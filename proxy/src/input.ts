import { POLICY } from "./policy";

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
  if (message.body === null) return null;
  const reader = message.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) {
      await reader.cancel();
      return null;
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
    return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes));
  } catch {
    return null;
  }
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
