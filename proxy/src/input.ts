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
 * [value] trimmed, when it is text of [min] to [max] characters (counted as code points) with no
 * control characters; otherwise null. Names are 1 to 60 characters.
 */
export function textOf(value: unknown, min = 1, max = 60): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  const length = [...text].length;
  return length >= min && length <= max && !CONTROL.test(text) ? text : null;
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

/** How many bytes [text] takes in UTF-8. */
export function byteLength(text: string): number {
  return new TextEncoder().encode(text).byteLength;
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
