import { POLICY } from "./policy";
import { bodyRefusal } from "./respond";

/** What the app sends to have one bill read: version 1 of the contract. */
export type BillRequest = {
  requestId: string;
  /**
   * The rows of the bill as the app's `OcrLayoutEncoder` writes them, inside `<bill_ocr>` tags:
   * `<bill_ocr rows="N">` for one page, or `<bill_ocr pages="N">` holding one `<page>` per photo.
   */
  ocrDocument: string;
  /** What the user typed; empty when they typed nothing. */
  instruction: string;
  /** ISO 4217 code of the currency the user's payment app works in, or empty when it has none. */
  localCurrency: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const CURRENCY = /^([A-Z]{3})?$/;

/** A UUID in its usual lower-case form, or null when [value] is not one. */
export function uuidOf(value: string | null | undefined): string | null {
  const id = value?.trim().toLowerCase() ?? "";
  return UUID.test(id) ? id : null;
}

/**
 * The body of a request, or of an answer the proxy fetched, as JSON, read up to [maxBytes]. A
 * body that is longer, or that is not JSON, reads as null. The length is counted while reading,
 * because a declared length can be missing or wrong.
 */
export async function readJson(message: Body, maxBytes: number): Promise<unknown> {
  const body = await jsonBody(message, maxBytes);
  return body.ok ? body.value : null;
}

/**
 * Like [readJson], but says why there is no value: `too_large` for a body longer than
 * [maxBytes], `input` for a missing body or one that is not JSON. The codes are the error codes
 * the routes answer with.
 */
export async function jsonBody(message: Body, maxBytes: number): Promise<{ ok: true; value: unknown } | { ok: false; code: "too_large" | "input" }> {
  if (message.body === null) return { ok: false, code: "input" };
  const reader = message.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) {
      await reader.cancel();
      return { ok: false, code: "too_large" };
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return { ok: true, value: JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as unknown };
  } catch {
    return { ok: false, code: "input" };
  }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;
/**
 * Unicode's invisible formatting characters (category Cf) but the two joiners: bidi controls such
 * as U+202E, which make text read backwards, zero-width spaces, U+FEFF and the like. In a name
 * they only serve to make it look like another, or to hide what it says.
 */
const FORMAT = /(?![‌‍])\p{Cf}/gu;
/**
 * The zero-width non-joiner and joiner, U+200C and U+200D, which stay: emoji sequences such as a
 * family are joined by U+200D, and Persian and some Indic names need them to be written right.
 */
const JOINERS = /[‌‍]/gu;

/**
 * [value] without invisible formatting characters, the joiners aside, and trimmed, when it is text
 * of [min] to [max] characters (counted as code points) with no control characters; otherwise
 * null. Text with no character to show, only joiners and spaces, counts as empty. Names and labels
 * go through it. Names are 1 to 60 characters.
 */
export function textOf(value: unknown, min = 1, max = 60): string | null {
  if (typeof value !== "string") return null;
  const kept = value.replace(FORMAT, "").trim();
  // A joiner joins the characters around it, and shows nothing of itself.
  const text = kept.replace(JOINERS, "").trim() === "" ? "" : kept;
  const length = [...text].length;
  return length >= min && length <= max && !CONTROL.test(text) ? text : null;
}

/** The most items, charges and participants a bill holds, on a bill link and in an edit of its split. */
export const MAX_BILL_ITEMS = 200;
export const MAX_BILL_CHARGES = 20;
export const MAX_BILL_PARTICIPANTS = 30;

/** One purchased line of a bill, one of its charges, and one of its participants, as the app sends them. */
export type BillItem = { id: string; name: string; quantity: string | null; amountMinor: number };
export type BillCharge = { id: string; label: string; amountMinor: number };
export type Participant = { id: string; name: string };

/** An id of a line or a person on a bill. */
const LINE_ID = /^[A-Za-z0-9_-]{1,64}$/;
/** A quantity as the app writes a decimal number: without exponent, leading zeros or trailing dot. */
const QUANTITY = /^-?(?:0|[1-9]\d{0,19})(?:\.\d{1,20})?$/;
/** The participant who is the app's user; their name may be empty, as the app stores none for them. */
const OWNER_PARTICIPANT = "me";

/** Whether [value] is the id of a line or a person on a bill: 1 to 64 of `A-Z a-z 0-9 _ -`. */
export function isLineId(value: unknown): value is string {
  return typeof value === "string" && LINE_ID.test(value);
}

/** Null for null or a missing value; what [read] makes of anything else, or undefined when it makes nothing of it. */
export function optional<T>(value: unknown, read: (value: unknown) => T | null): T | null | undefined {
  if (value === undefined || value === null) return null;
  return read(value) ?? undefined;
}

/** [value] as a list of [min] to [max] entries that [entryOf] reads, each with an id unique within it; null otherwise. */
export function linesOf<T>(value: unknown, min: number, max: number, entryOf: (entry: Record<string, unknown>, id: string) => T | null): T[] | null {
  if (!Array.isArray(value) || value.length < min || value.length > max) return null;
  const entries: T[] = [];
  const ids = new Set<string>();
  for (const entry of value) {
    if (!isRecord(entry) || !isLineId(entry.id) || ids.has(entry.id)) return null;
    const read = entryOf(entry, entry.id);
    if (read === null) return null;
    ids.add(entry.id);
    entries.push(read);
  }
  return entries;
}

/** How long a name or a label may be, in code points. */
export type NameLength = { min: number; max: number };
/** A name or a label on a bill link: 1 to 80 characters. */
const LINK_NAME: NameLength = { min: 1, max: 80 };

/** An item: a name of [length], a quantity that is null or a decimal written as text, and a line total of 0 or more. */
export function billItemOf(entry: Record<string, unknown>, id: string, length: NameLength = LINK_NAME): BillItem | null {
  const name = textOf(entry.name, length.min, length.max);
  const quantity = optional(entry.quantity, (text) => (typeof text === "string" && QUANTITY.test(text) ? text : null));
  const amountMinor = moneyOf(entry.amountMinor);
  return name === null || quantity === undefined || amountMinor === null ? null : { id, name, quantity, amountMinor };
}

/** A charge: a label of [length] and an amount that may take money off, as a discount does. */
export function billChargeOf(entry: Record<string, unknown>, id: string, length: NameLength = LINK_NAME): BillCharge | null {
  const label = textOf(entry.label, length.min, length.max);
  const amount = entry.amountMinor;
  const amountMinor = typeof amount === "number" && Number.isInteger(amount) && Math.abs(amount) <= POLICY.maxAmountMinor ? amount : null;
  return label === null || amountMinor === null ? null : { id, label, amountMinor };
}

/** A participant: a name of [length], on a bill link 1 to 60 characters, which may be empty for `me`. */
export function participantOf(
  entry: Record<string, unknown>,
  id: string,
  length: NameLength = { min: id === OWNER_PARTICIPANT ? 0 : 1, max: 60 },
): Participant | null {
  const name = textOf(entry.name, length.min, length.max);
  return name === null ? null : { id, name };
}

/** A currency as the shared data names it: an ISO 4217 code and the digits of its minor unit. */
export type Currency = { code: string; fractionDigits: number };

export function currencyOf(value: unknown): Currency | null {
  if (!isRecord(value)) return null;
  const { code, fractionDigits } = value;
  if (typeof code !== "string" || !/^[A-Z]{3}$/.test(code)) return null;
  if (typeof fractionDigits !== "number" || !Number.isInteger(fractionDigits) || fractionDigits < 0 || fractionDigits > 3) return null;
  return { code, fractionDigits };
}

/** An amount of money in minor units, from 0 up to [POLICY.maxAmountMinor], or null. */
export function moneyOf(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 && value <= POLICY.maxAmountMinor ? value : null;
}

/** A count, such as a revision, from 0 up, or null. */
export function countOf(value: unknown): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

/** [value] as a UUID in its usual lower-case form, or null when it is not one. */
export function idOf(value: unknown): string | null {
  return typeof value === "string" ? uuidOf(value) : null;
}

/** What a parser for [inputOf] gives for a value that is well formed but larger than the proxy keeps. */
export const TOO_LARGE: unique symbol = Symbol("too_large");

/**
 * The JSON object [request] carries, as [parse] reads it, or the refusal to answer with: 413
 * `too_large` past [maxBytes] or when [parse] gives [TOO_LARGE], 400 `input` when the body is not
 * a JSON object or [parse] gives null.
 */
export async function inputOf<T>(
  request: Request,
  maxBytes: number,
  parse: (body: Record<string, unknown>) => T | typeof TOO_LARGE | null,
): Promise<{ ok: true; value: T } | { ok: false; response: Response }> {
  const body = await jsonBody(request, maxBytes);
  if (!body.ok) return { ok: false, response: bodyRefusal(body.code) };
  const value = isRecord(body.value) ? parse(body.value) : null;
  if (value === null) return { ok: false, response: bodyRefusal("input") };
  if (value === TOO_LARGE) return { ok: false, response: bodyRefusal("too_large") };
  return { ok: true, value };
}

/** A UUID like [uuidOf], or null for null or a missing value; undefined when [value] is something else. */
export function optionalUuidOf(value: unknown): string | null | undefined {
  if (value === undefined || value === null) return null;
  return typeof value === "string" ? (uuidOf(value) ?? undefined) : undefined;
}

/** How many bytes [text] takes in UTF-8. */
export function byteLength(text: string): number {
  return new TextEncoder().encode(text).byteLength;
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** Whether [text] is a day that exists, written `YYYY-MM-DD`. */
export function isDay(text: string): boolean {
  const time = DAY.test(text) ? Date.parse(`${text}T00:00:00Z`) : Number.NaN;
  return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === text;
}

/**
 * How a payer pays one amount, as the creditor's app built it with its `PaymentRequestBuilder`,
 * for a web page that cannot build payment codes itself: a [link] to open, a [qrPayload] to
 * scan, the [details] to pay by hand, and whether the link or the code carries the amount.
 */
export type PaymentPayload = { link: string | null; qrPayload: string | null; details: string; amountFilledIn: boolean };

/** Every control character but the line feed and the carriage return. */
const CONTROL_BUT_LINE_BREAKS = /[\u0000-\u0009\u000b\u000c\u000e-\u001f\u007f-\u009f]/;
/** Whitespace and control characters, which no link a page opens may hold. */
const NOT_IN_LINKS = /[\s\u0000-\u001f\u007f-\u009f]/;

/**
 * A payment as the contract takes it; null for null or a missing value, undefined for anything
 * else. Its link is null, an `https://` URL with a host or a `upi://pay?` link, of at most
 * [POLICY.maxPaymentLinkChars] and without whitespace: the page opens no other scheme. Its code
 * to scan is null or at most [POLICY.maxPaymentCodeChars]; its details are 1 to
 * [POLICY.maxPaymentDetailsChars]. Both may break lines and hold no other control character.
 * Lengths are counted as code points. A link or a code that is missing counts as null.
 */
export function optionalPaymentOf(value: unknown): PaymentPayload | null | undefined {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) return undefined;
  const link = value.link === undefined || value.link === null ? null : paymentLinkOf(value.link);
  const qrPayload = value.qrPayload === undefined || value.qrPayload === null ? null : lineTextOf(value.qrPayload, 0, POLICY.maxPaymentCodeChars);
  const details = lineTextOf(value.details, 1, POLICY.maxPaymentDetailsChars);
  const { amountFilledIn } = value;
  if (link === undefined || qrPayload === undefined || details === undefined || typeof amountFilledIn !== "boolean") return undefined;
  return { link, qrPayload, details, amountFilledIn };
}

/** [value] when it is a link a page may open, as `optionalPaymentOf` says; undefined otherwise. */
function paymentLinkOf(value: unknown): string | undefined {
  if (typeof value !== "string" || [...value].length > POLICY.maxPaymentLinkChars || NOT_IN_LINKS.test(value)) return undefined;
  if (value.startsWith("upi://pay?")) return value;
  if (!value.startsWith("https://")) return undefined;
  try {
    return new URL(value).hostname !== "" ? value : undefined;
  } catch {
    // Not a URL at all.
    return undefined;
  }
}

/** [value] as it is, when it is text of [min] to [max] code points whose only control characters are line breaks. */
function lineTextOf(value: unknown, min: number, max: number): string | undefined {
  if (typeof value !== "string") return undefined;
  const length = [...value].length;
  return length >= min && length <= max && !CONTROL_BUT_LINE_BREAKS.test(value) ? value : undefined;
}

/** The request when [body] follows the contract, or null. Fields the contract does not name are ignored. */
export function billRequestOf(body: unknown): BillRequest | null {
  if (typeof body !== "object" || body === null || Array.isArray(body)) return null;
  const { schemaVersion, requestId, ocrDocument, instruction, localCurrency } = body as Record<string, unknown>;
  if (schemaVersion !== 1) return null;
  const id = typeof requestId === "string" ? uuidOf(requestId) : null;
  if (id === null) return null;
  if (typeof ocrDocument !== "string" || ocrDocument.length > POLICY.maxDocumentChars) return null;
  if (!ocrDocument.startsWith("<bill_ocr") || !ocrDocument.endsWith("</bill_ocr>")) return null;
  if (typeof instruction !== "string" || instruction.length > POLICY.maxInstructionChars) return null;
  if (typeof localCurrency !== "string" || !CURRENCY.test(localCurrency)) return null;
  return { requestId: id, ocrDocument, instruction, localCurrency };
}
