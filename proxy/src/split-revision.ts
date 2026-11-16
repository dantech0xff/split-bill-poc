import { WEIGHTS } from "./identity";
import type { Env } from "./index";
import {
  type BillCharge,
  billChargeOf,
  type BillItem,
  billItemOf,
  countOf,
  type Currency,
  currencyOf,
  idOf,
  isLineId,
  isRecord,
  linesOf,
  MAX_BILL_CHARGES,
  MAX_BILL_ITEMS,
  MAX_BILL_PARTICIPANTS,
  moneyOf,
  optional,
  type Participant,
  participantOf,
  textOf,
} from "./input";
import { callModel, type ModelTask, TIMING, type Timing } from "./model-call";
import { POLICY } from "./policy";
import { REVISE_SYSTEM_PROMPT, revisionMessage } from "./revise-prompt";
import { CHARGE_KINDS, PERSON_REFS, REVISION_SCHEMA, SPLIT_MODES } from "./schema";
import { isPriced } from "./spend";
import { servedLocally } from "./switches";

/** The header with which the evaluation on a computer names the model of one call; see [modelOf]. */
export const EVAL_MODEL_HEADER = "X-SplitVibe-Eval-Model";

/** The longest instruction, in code points, as the contract says. */
const MAX_INSTRUCTION_CHARS = 500;
/** The most edits an answer holds, and the longest explanation and question, in code points. */
const MAX_EDITS = 20;
const MAX_EXPLANATION_CHARS = 200;
const MAX_QUESTION_CHARS = 200;
/** The most people one edit names: everyone a bill holds, and the nine an answer may add. */
const MAX_PEOPLE_IN_EDIT = MAX_BILL_PARTICIPANTS + PERSON_REFS.length;
/** The largest share an answer gives, so that the app never gets a number past what it counts with. */
const MAX_SHARES = 1_000;
/** 100%, in basis points. */
const FULL = 10_000;
/** A quantity an answer sets: a decimal written as text, more than 0, without sign, exponent or leading zeros. */
const QUANTITY = /^(?:0|[1-9]\d{0,19})(?:\.\d{1,20})?$/;
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

export type ChargeKind = (typeof CHARGE_KINDS)[number];
export type SplitMode = (typeof SPLIT_MODES)[number];

/** How one amount, the whole bill or one item, is divided, as the domain writes its `Division`. */
export type Division =
  | { type: "equal"; among: string[]; except: string[] }
  | { type: "shares"; shares: Record<string, number> }
  | { type: "percent"; basisPoints: Record<string, number> }
  | { type: "amount"; amounts: Record<string, number> };

export type RevisionBill = {
  merchant: string | null;
  currency: Currency;
  items: BillItem[];
  charges: Array<BillCharge & { kind: ChargeKind }>;
  totalMinor: number;
};

export type RevisionSplit = {
  participants: Participant[];
  /** Null when each item is divided on its own. */
  whole: Division | null;
  /** By item id; an item without one is shared equally by everyone sharing the bill. */
  items: Record<string, Division>;
  notSharing: string[];
  payments: Array<{ participantId: string; amountMinor: number }>;
};

/** What the app sends to have a split edited by a sentence. */
export type RevisionRequest = { requestId: string; instruction: string; bill: RevisionBill; split: RevisionSplit };

/** One edit of a split, as the app gets it; the app checks it against the bill and applies it, or rejects it. */
export type Edit =
  | { op: "item_sharers"; itemId: string; participantIds: string[] }
  | { op: "item_shares"; itemId: string; shares: Record<string, number> }
  | { op: "split_mode"; mode: SplitMode }
  | { op: "whole_shares"; shares: Record<string, number> }
  | { op: "whole_percent"; basisPoints: Record<string, number> }
  | { op: "whole_amounts"; amounts: Record<string, number> }
  | { op: "add_person"; ref: string; name: string }
  | { op: "remove_person"; participantId: string }
  | { op: "set_sharing"; participantId: string; sharing: boolean }
  | { op: "add_charge"; kind: ChargeKind; label: string; amountMinor: number }
  | { op: "add_charge"; kind: ChargeKind; label: string; percentBasisPoints: number }
  | { op: "set_payer"; participantId: string }
  | { op: "item_amount"; itemId: string; amountMinor: number }
  | { op: "item_quantity"; itemId: string; quantity: string };

/** The answer the app gets: the edits, a sentence that says what they change, or a question instead. */
export type Revision = { edits: Edit[]; explanation: string; question: string | null };

/** A request and how its model is asked: the policy's model, or the one the evaluation names. */
type RevisionCall = RevisionRequest & { model: string; report: boolean };

/**
 * An edit of a split by a sentence. The model turns the instruction into edits of the split, as
 * [REVISION_SCHEMA] shapes them, and the proxy checks the answer again and writes it as the
 * contract does before the app gets it. An answer that fails the check is given back, as a failure
 * of the model would be: the user can ask again without paying for it twice.
 */
const REVISION: ModelTask<RevisionCall> = {
  event: "split_revision",
  weight: WEIGHTS.splitRevision,
  maxBodyBytes: POLICY.maxRevisionBodyBytes,
  inputOf(body, request) {
    const revision = revisionRequestOf(body);
    const model = modelOf(request);
    return revision === null || model === null ? null : { ...revision, ...model };
  },
  system: REVISE_SYSTEM_PROMPT,
  schema: REVISION_SCHEMA,
  userMessage: revisionMessage,
  settings: (call) => ({
    model: call.model,
    maxTokens: POLICY.revisionMaxTokens,
    effort: POLICY.revisionEffort,
    thinking: { type: "adaptive" },
    // The model has no server-side fallback: a refusal ends the call.
    fallback: false,
    report: call.report,
  }),
  answerOf: revisionAnswerOf,
  chargeBadAnswer: false,
};

/** `POST /v1/split-revision`: edits a split by a sentence with the model, as [callModel] does every call. */
export function splitRevision(request: Request, env: Env, ctx: ExecutionContext, timing: Timing = TIMING): Promise<Response> {
  return callModel(request, env, ctx, REVISION, timing);
}

/**
 * The model of a call: [POLICY.revisionModel], unless the proxy runs on a computer and the request
 * names in [EVAL_MODEL_HEADER] another model the proxy has a price for, as the evaluation does;
 * the line that ends its answer then reports the model, its tokens and the cost. The deployed
 * proxy is never asked at a computer's hostname, so there the header changes nothing. Null for a
 * model without a price, which a computer refuses as input.
 */
function modelOf(request: Request): { model: string; report: boolean } | null {
  const named = request.headers.get(EVAL_MODEL_HEADER)?.trim() ?? "";
  if (named === "" || !servedLocally(new URL(request.url).hostname)) return { model: POLICY.revisionModel, report: false };
  return isPriced(named) ? { model: named, report: true } : null;
}

/**
 * The request when [body] follows the contract, or null. Fields the contract does not name are
 * ignored, and the lists and maps of the split that are empty may be left out; `whole` may not,
 * as null there means splitting by item. Names and labels are read as on a bill link: without
 * invisible formatting characters but the joiners, trimmed, and without control characters.
 */
export function revisionRequestOf(body: unknown): RevisionRequest | null {
  if (!isRecord(body)) return null;
  const requestId = idOf(body.requestId);
  const instruction = textOf(body.instruction, 1, MAX_INSTRUCTION_CHARS);
  const bill = billOf(body.bill);
  const split = splitOf(body.split);
  if (requestId === null || instruction === null || bill === null || split === null) return null;
  return { requestId, instruction, bill, split };
}

function billOf(value: unknown): RevisionBill | null {
  if (!isRecord(value)) return null;
  const merchant = optional(value.merchant, (text) => textOf(text, 1, 80));
  const currency = currencyOf(value.currency);
  // A bill entered by hand may have no items, only its total.
  const items = linesOf(value.items, 0, MAX_BILL_ITEMS, billItemOf);
  const charges = linesOf(value.charges, 0, MAX_BILL_CHARGES, chargeOf);
  const totalMinor = moneyOf(value.totalMinor);
  if (merchant === undefined || currency === null || items === null || charges === null || totalMinor === null) return null;
  return { merchant, currency, items, charges, totalMinor };
}

function chargeOf(entry: Record<string, unknown>, id: string): RevisionBill["charges"][number] | null {
  const charge = billChargeOf(entry, id);
  const kind = CHARGE_KINDS.find((one) => one === entry.kind) ?? null;
  return charge === null || kind === null ? null : { id, label: charge.label, kind, amountMinor: charge.amountMinor };
}

function splitOf(value: unknown): RevisionSplit | null {
  if (!isRecord(value) || value.whole === undefined) return null;
  const participants = linesOf(value.participants, 1, MAX_BILL_PARTICIPANTS, participantOf);
  const whole = optional(value.whole, divisionOf);
  const items = value.items === undefined ? {} : itemDivisionsOf(value.items);
  const notSharing = value.notSharing === undefined ? [] : idListOf(value.notSharing);
  const payments = value.payments === undefined ? [] : paymentsOf(value.payments);
  if (participants === null || whole === undefined || items === null || notSharing === null || payments === null) return null;
  return { participants, whole, items, notSharing, payments };
}

function divisionOf(value: unknown): Division | null {
  if (!isRecord(value)) return null;
  switch (value.type) {
    case "equal": {
      const among = value.among === undefined ? [] : idListOf(value.among);
      const except = value.except === undefined ? [] : idListOf(value.except);
      return among === null || except === null ? null : { type: "equal", among, except };
    }
    case "shares": {
      const shares = perPersonOf(value.shares, countOf);
      return shares === null ? null : { type: "shares", shares };
    }
    case "percent": {
      const basisPoints = perPersonOf(value.basisPoints, (number) => wholeNumberOf(number, 0, FULL));
      return basisPoints === null ? null : { type: "percent", basisPoints };
    }
    case "amount": {
      const amounts = perPersonOf(value.amounts, moneyOf);
      return amounts === null ? null : { type: "amount", amounts };
    }
    default:
      return null;
  }
}

/** [value] as numbers [read] takes, keyed by the ids of at most [MAX_BILL_PARTICIPANTS] people; null otherwise. */
function perPersonOf(value: unknown, read: (number: unknown) => number | null): Record<string, number> | null {
  if (!isRecord(value)) return null;
  const entries: Array<[string, number]> = [];
  for (const [id, written] of Object.entries(value)) {
    const number = read(written);
    if (!isLineId(id) || number === null) return null;
    entries.push([id, number]);
  }
  // Built from entries, so that an id such as `__proto__` stays an id.
  return entries.length <= MAX_BILL_PARTICIPANTS ? Object.fromEntries(entries) : null;
}

function itemDivisionsOf(value: unknown): Record<string, Division> | null {
  if (!isRecord(value)) return null;
  const entries: Array<[string, Division]> = [];
  for (const [id, division] of Object.entries(value)) {
    const read = divisionOf(division);
    if (!isLineId(id) || read === null) return null;
    entries.push([id, read]);
  }
  return entries.length <= MAX_BILL_ITEMS ? Object.fromEntries(entries) : null;
}

/** [value] as the ids of at most [MAX_BILL_PARTICIPANTS] people, each once; null otherwise. */
function idListOf(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length > MAX_BILL_PARTICIPANTS) return null;
  if (!value.every(isLineId) || new Set(value).size !== value.length) return null;
  return value;
}

function paymentsOf(value: unknown): RevisionSplit["payments"] | null {
  if (!Array.isArray(value) || value.length > MAX_BILL_PARTICIPANTS) return null;
  const payments: RevisionSplit["payments"] = [];
  for (const entry of value) {
    const amountMinor = isRecord(entry) ? moneyOf(entry.amountMinor) : null;
    if (!isRecord(entry) || !isLineId(entry.participantId) || amountMinor === null) return null;
    payments.push({ participantId: entry.participantId, amountMinor });
  }
  return payments;
}

/**
 * The answer the app gets, from what the model wrote, or null when it breaks the contract: more
 * than [MAX_EDITS] edits, an edit that is not one of the thirteen or whose fields are of the wrong
 * shape or out of range, an explanation or a question that is too long, or a question with edits.
 * Each per-participant number comes as a pair in the model's answer and goes to the app keyed by
 * the participant, as in a division. What depends on the bill is the app's to check: ids that it
 * does not have, percentages that do not add up to 10000, and amounts that do not add up to its
 * total pass, and the app rejects those edits one by one.
 */
export function revisionAnswerOf(value: Record<string, unknown>): Revision | null {
  const { edits, explanation, question } = value;
  if (!Array.isArray(edits) || edits.length > MAX_EDITS) return null;
  const said = answerTextOf(explanation, 0, MAX_EXPLANATION_CHARS);
  const asked = question === null || question === undefined ? "" : answerTextOf(question, 0, MAX_QUESTION_CHARS);
  if (said === null || asked === null || (asked !== "" && edits.length > 0)) return null;
  const checked: Edit[] = [];
  for (const edit of edits) {
    const read = editOf(edit);
    if (read === null) return null;
    checked.push(read);
  }
  // A blank question is no question.
  return { edits: checked, explanation: said, question: asked === "" ? null : asked };
}

function editOf(value: unknown): Edit | null {
  if (!isRecord(value) || typeof value.op !== "string") return null;
  // Structured outputs may write an enum's value in other capitals.
  const op = value.op.toLowerCase();
  switch (op) {
    case "item_sharers": {
      const itemId = answerIdOf(value.itemId);
      const participantIds = answerIdsOf(value.participantIds);
      return itemId === null || participantIds === null ? null : { op, itemId, participantIds };
    }
    case "item_shares": {
      const itemId = answerIdOf(value.itemId);
      const shares = pairsOf(value.shares, "shares", (number) => wholeNumberOf(number, 1, MAX_SHARES));
      return itemId === null || shares === null ? null : { op, itemId, shares };
    }
    case "split_mode": {
      const mode = oneOf(value.mode, SPLIT_MODES);
      return mode === null ? null : { op, mode };
    }
    case "whole_shares": {
      const shares = pairsOf(value.shares, "shares", (number) => wholeNumberOf(number, 0, MAX_SHARES));
      return shares === null ? null : { op, shares };
    }
    case "whole_percent": {
      const basisPoints = pairsOf(value.basisPoints, "basisPoints", (number) => wholeNumberOf(number, 0, FULL));
      return basisPoints === null ? null : { op, basisPoints };
    }
    case "whole_amounts": {
      const amounts = pairsOf(value.amounts, "amountMinor", moneyOf);
      return amounts === null ? null : { op, amounts };
    }
    case "add_person": {
      const ref = oneOf(value.ref, PERSON_REFS);
      const name = answerTextOf(value.name, 1, 60);
      return ref === null || name === null ? null : { op, ref, name };
    }
    case "remove_person":
    case "set_payer": {
      const participantId = answerIdOf(value.participantId);
      return participantId === null ? null : { op, participantId };
    }
    case "set_sharing": {
      const participantId = answerIdOf(value.participantId);
      return participantId === null || typeof value.sharing !== "boolean" ? null : { op, participantId, sharing: value.sharing };
    }
    case "add_charge":
      return chargeEditOf(value);
    case "item_amount": {
      const itemId = answerIdOf(value.itemId);
      const amountMinor = moneyOf(value.amountMinor);
      return itemId === null || amountMinor === null ? null : { op, itemId, amountMinor };
    }
    case "item_quantity": {
      const itemId = answerIdOf(value.itemId);
      const quantity = typeof value.quantity === "string" && QUANTITY.test(value.quantity) && /[1-9]/.test(value.quantity) ? value.quantity : null;
      return itemId === null || quantity === null ? null : { op, itemId, quantity };
    }
    default:
      return null;
  }
}

/** A new charge, with exactly one of an amount of 1 or more and a percentage of 1 to 10000 basis points. */
function chargeEditOf(value: Record<string, unknown>): Edit | null {
  const kind = oneOf(value.kind, CHARGE_KINDS);
  const label = answerTextOf(value.label, 1, 80);
  const byAmount = value.amountMinor !== undefined && value.amountMinor !== null;
  const byPercent = value.percentBasisPoints !== undefined && value.percentBasisPoints !== null;
  if (kind === null || label === null || byAmount === byPercent) return null;
  if (byAmount) {
    const amountMinor = wholeNumberOf(value.amountMinor, 1, POLICY.maxAmountMinor);
    return amountMinor === null ? null : { op: "add_charge", kind, label, amountMinor };
  }
  const percentBasisPoints = wholeNumberOf(value.percentBasisPoints, 1, FULL);
  return percentBasisPoints === null ? null : { op: "add_charge", kind, label, percentBasisPoints };
}

/**
 * An id the model wrote: text of 1 to 64 characters without control characters, as it is. Whether
 * the bill has it is the app's to say.
 */
function answerIdOf(value: unknown): string | null {
  return typeof value === "string" && value !== "" && [...value].length <= 64 && !CONTROL.test(value) ? value : null;
}

/** At least one id and at most [MAX_PEOPLE_IN_EDIT], each once. */
function answerIdsOf(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > MAX_PEOPLE_IN_EDIT) return null;
  const ids = value.map(answerIdOf);
  return ids.every((id): id is string => id !== null) && new Set(ids).size === ids.length ? ids : null;
}

/** The model's pairs of a participant and a number [read] takes, as an object keyed by participant, each once. */
function pairsOf(value: unknown, field: string, read: (number: unknown) => number | null): Record<string, number> | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > MAX_PEOPLE_IN_EDIT) return null;
  const entries: Array<[string, number]> = [];
  for (const pair of value) {
    const id = isRecord(pair) ? answerIdOf(pair.participantId) : null;
    const number = isRecord(pair) ? read(pair[field]) : null;
    if (id === null || number === null || entries.some(([known]) => known === id)) return null;
    entries.push([id, number]);
  }
  // Built from entries, so that an id such as `__proto__` stays an id.
  return Object.fromEntries(entries);
}

/** Text the model wrote for the user, with each run of whitespace, line breaks among them, as one space, read as [textOf] reads names. */
function answerTextOf(value: unknown, min: number, max: number): string | null {
  return typeof value === "string" ? textOf(value.replace(/\s+/gu, " "), min, max) : null;
}

function wholeNumberOf(value: unknown, min: number, max: number): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= min && value <= max ? value : null;
}

/** [value] as one of [allowed], whatever its capitals; null otherwise. */
function oneOf<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  return typeof value === "string" ? (allowed.find((one) => one === value.toLowerCase()) ?? null) : null;
}
