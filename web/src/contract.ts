/**
 * What the proxy answers, as docs/proxy.md ("Bill links and the web pages") describes it, and
 * parsers that check each answer before the page uses it. An answer of the wrong shape parses
 * to null, and the page treats it as a failed request rather than drawing half of it.
 */

export interface Currency {
  code: string;
  fractionDigits: number;
}

export interface BillItem {
  id: string;
  name: string;
  quantity: string | null;
  amountMinor: number;
}

export interface BillCharge {
  id: string;
  label: string;
  amountMinor: number;
}

export interface Participant {
  id: string;
  name: string;
}

export interface BillProjection {
  merchant: string | null;
  date: string | null;
  currency: Currency;
  items: BillItem[];
  charges: BillCharge[];
  totalMinor: number;
  participants: Participant[];
}

export interface PaymentPayload {
  link: string | null;
  qrPayload: string | null;
  details: string;
  amountFilledIn: boolean;
}

export interface ShareAmount {
  amountMinor: number;
  payment: PaymentPayload | null;
}

export interface PublicClaim {
  id: string;
  name: string;
  itemIds: string[];
}

export type ShareStatus = "open" | "locking" | "locked";

export interface PublicShare {
  status: ShareStatus;
  revision: number;
  expiresAt: number;
  bill: BillProjection;
  claims: PublicClaim[];
  amounts: Record<string, ShareAmount> | null;
}

export type RequestStatus = "open" | "reported" | "confirmed";

export interface RequestView {
  creditorName: string;
  payerName: string;
  amountMinor: number;
  currency: Currency;
  title: string | null;
  status: RequestStatus;
  payment: PaymentPayload | null;
  createdAt: number;
}

export interface InvitePreview {
  name: string;
  currency: Currency;
}

/** The participant id the app gives the bill's owner. */
export const OWNER_PARTICIPANT_ID = "me";

/** The largest amount of money the proxy takes, in minor units (`BillCurrency.MAX_AMOUNT_MINOR`). */
export const MAX_AMOUNT_MINOR = 1_000_000_000_000_000;

type Json = Record<string, unknown>;

export function isObject(value: unknown): value is Json {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === "string";
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function isAmount(value: unknown, allowNegative = false): value is number {
  return (
    typeof value === "number" &&
    Number.isSafeInteger(value) &&
    value <= MAX_AMOUNT_MINOR &&
    value >= (allowNegative ? -MAX_AMOUNT_MINOR : 0)
  );
}

function isTime(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

/** Parses each element with [parse]; null when [value] is not an array or any element fails. */
function arrayOf<T>(value: unknown, parse: (element: unknown) => T | null): T[] | null {
  if (!Array.isArray(value)) return null;
  const parsed: T[] = [];
  for (const element of value) {
    const one = parse(element);
    if (one === null) return null;
    parsed.push(one);
  }
  return parsed;
}

export function parseCurrency(value: unknown): Currency | null {
  if (!isObject(value)) return null;
  const { code, fractionDigits } = value;
  if (!isString(code) || !/^[A-Z]{3}$/.test(code)) return null;
  if (typeof fractionDigits !== "number" || !Number.isInteger(fractionDigits) || fractionDigits < 0 || fractionDigits > 3) {
    return null;
  }
  return { code, fractionDigits };
}

function parseItem(value: unknown): BillItem | null {
  if (!isObject(value)) return null;
  const { id, name, amountMinor } = value;
  const quantity = value.quantity ?? null;
  if (!isString(id) || !isString(name) || !isNullableString(quantity) || !isAmount(amountMinor)) return null;
  return { id, name, quantity, amountMinor };
}

function parseCharge(value: unknown): BillCharge | null {
  if (!isObject(value)) return null;
  const { id, label, amountMinor } = value;
  if (!isString(id) || !isString(label) || !isAmount(amountMinor, true)) return null;
  return { id, label, amountMinor };
}

function parseParticipant(value: unknown): Participant | null {
  if (!isObject(value)) return null;
  const { id, name } = value;
  if (!isString(id) || !isString(name)) return null;
  return { id, name };
}

export function parseBill(value: unknown): BillProjection | null {
  if (!isObject(value)) return null;
  const merchant = value.merchant ?? null;
  const date = value.date ?? null;
  if (!isNullableString(merchant) || !isNullableString(date)) return null;
  const currency = parseCurrency(value.currency);
  const items = arrayOf(value.items, parseItem);
  const charges = arrayOf(value.charges ?? [], parseCharge);
  const participants = arrayOf(value.participants, parseParticipant);
  if (currency === null || items === null || charges === null || participants === null) return null;
  if (!isAmount(value.totalMinor)) return null;
  return { merchant, date, currency, items, charges, totalMinor: value.totalMinor, participants };
}

export function parsePayment(value: unknown): PaymentPayload | null {
  if (!isObject(value)) return null;
  const link = value.link ?? null;
  const qrPayload = value.qrPayload ?? null;
  const { details, amountFilledIn } = value;
  if (!isNullableString(link) || !isNullableString(qrPayload) || !isString(details) || typeof amountFilledIn !== "boolean") {
    return null;
  }
  return { link, qrPayload, details, amountFilledIn };
}

/** A payment that is null stays null; one that is there must parse, or the whole answer fails. */
function parseOptionalPayment(value: unknown): { ok: true; payment: PaymentPayload | null } | { ok: false } {
  if (value === null || value === undefined) return { ok: true, payment: null };
  const payment = parsePayment(value);
  return payment === null ? { ok: false } : { ok: true, payment };
}

function parseClaim(value: unknown): PublicClaim | null {
  if (!isObject(value)) return null;
  const { id, name } = value;
  const itemIds = arrayOf(value.itemIds, (element) => (isString(element) ? element : null));
  if (!isString(id) || !isString(name) || itemIds === null) return null;
  return { id, name, itemIds };
}

function parseAmounts(value: unknown): Record<string, ShareAmount> | null | undefined {
  if (value === null || value === undefined) return null;
  if (!isObject(value)) return undefined;
  const amounts: Record<string, ShareAmount> = {};
  for (const [participantId, entry] of Object.entries(value)) {
    if (!isObject(entry) || !isAmount(entry.amountMinor)) return undefined;
    const payment = parseOptionalPayment(entry.payment);
    if (!payment.ok) return undefined;
    amounts[participantId] = { amountMinor: entry.amountMinor, payment: payment.payment };
  }
  return amounts;
}

function isShareStatus(value: unknown): value is ShareStatus {
  return value === "open" || value === "locking" || value === "locked";
}

function isRequestStatus(value: unknown): value is RequestStatus {
  return value === "open" || value === "reported" || value === "confirmed";
}

export function parseShare(value: unknown): PublicShare | null {
  if (!isObject(value)) return null;
  const { status, revision, expiresAt } = value;
  if (!isShareStatus(status)) return null;
  if (typeof revision !== "number" || !Number.isSafeInteger(revision) || revision < 0 || !isTime(expiresAt)) return null;
  const bill = parseBill(value.bill);
  const claims = arrayOf(value.claims, parseClaim);
  const amounts = parseAmounts(value.amounts);
  if (bill === null || claims === null || amounts === undefined) return null;
  return { status, revision, expiresAt, bill, claims, amounts };
}

export interface OpenAnswer {
  share: PublicShare;
  you: string | null;
}

export interface ClaimAnswer {
  share: PublicShare;
  you: string;
  key: string | null;
}

export function parseOpenAnswer(value: unknown): OpenAnswer | null {
  if (!isObject(value)) return null;
  const share = parseShare(value.share);
  const you = value.you ?? null;
  if (share === null || !isNullableString(you)) return null;
  return { share, you };
}

/** A claim key: 128 bits in base64url, as the proxy hands them out. */
export const CLAIM_KEY = /^[A-Za-z0-9_-]{22}$/;

export function parseClaimAnswer(value: unknown): ClaimAnswer | null {
  if (!isObject(value)) return null;
  const share = parseShare(value.share);
  const { you } = value;
  const key = value.key ?? null;
  if (share === null || !isString(you) || !(key === null || (isString(key) && CLAIM_KEY.test(key)))) return null;
  return { share, you, key };
}

export function parseRequestAnswer(value: unknown): RequestView | null {
  if (!isObject(value) || !isObject(value.request)) return null;
  const request = value.request;
  const { creditorName, payerName, amountMinor, status, createdAt } = request;
  const title = request.title ?? null;
  const currency = parseCurrency(request.currency);
  const payment = parseOptionalPayment(request.payment);
  if (!isString(creditorName) || !isString(payerName) || !isAmount(amountMinor) || !isNullableString(title)) return null;
  if (!isRequestStatus(status)) return null;
  if (currency === null || !payment.ok || !isTime(createdAt)) return null;
  return {
    creditorName,
    payerName,
    amountMinor,
    currency,
    title,
    status,
    payment: payment.payment,
    createdAt,
  };
}

export function parseInviteAnswer(value: unknown): InvitePreview | null {
  if (!isObject(value) || !isObject(value.group)) return null;
  const { name } = value.group;
  const currency = parseCurrency(value.group.currency);
  if (!isString(name) || currency === null) return null;
  return { name, currency };
}
