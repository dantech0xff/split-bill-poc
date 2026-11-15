import type { Env } from "./index";
import { byteLength, type Currency, currencyOf, isDay, isRecord, moneyOf, optionalPaymentOf, type PaymentPayload, textOf, TOO_LARGE } from "./input";
import { tokenHash } from "./links";
import { POLICY } from "./policy";
import { errorResponse } from "./respond";
import type { LiveUpdate, ShareRoom } from "./share-room";

/** Where a link stands: people claim items while it is `open`; the owner settles the split while it is `locking`; `locked` shows the amounts. */
export type ShareStatus = "open" | "locking" | "locked";

/** What the page shows of a bill, as the owner's app projected it; nothing else of the bill is kept. */
export type BillProjection = {
  merchant: string | null;
  date: string | null;
  currency: Currency;
  items: Array<{ id: string; name: string; quantity: string | null; amountMinor: number }>;
  charges: Array<{ id: string; label: string; amountMinor: number }>;
  totalMinor: number;
  participants: Array<{ id: string; name: string }>;
};

/** What each participant of a bill owes once its owner settled the split, and how to pay it, keyed by participant id. */
export type ShareAmounts = Record<string, { amountMinor: number; payment: PaymentPayload | null }>;

/** Which participant of the bill each claim is, as the owner matched them in settling the split: participant ids keyed by claim id. */
export type ShareMatches = Record<string, string>;

/**
 * A bill link as its owner sees it. Accounts, hashes and IPs never appear in it. `paidBy` is the
 * participant who paid the whole bill, when the owner named one in settling the split, and
 * `matches` says which participant each claim is, when the owner matched any; both are null
 * whenever `amounts` is.
 */
export type ShareView = {
  id: string;
  billId: string;
  billRevision: number;
  status: ShareStatus;
  revision: number;
  createdAt: number;
  expiresAt: number;
  bill: BillProjection;
  claims: Array<{ id: string; name: string; itemIds: string[]; updatedAt: number }>;
  amounts: ShareAmounts | null;
  paidBy: string | null;
  matches: ShareMatches | null;
};

/** A bill link as anyone with its token sees it: the owner's view without the link's ids, the bill's revision, and when things were made. */
export type PublicShare = {
  status: ShareStatus;
  revision: number;
  expiresAt: number;
  bill: BillProjection;
  claims: Array<{ id: string; name: string; itemIds: string[] }>;
  amounts: ShareAmounts | null;
  paidBy: string | null;
  matches: ShareMatches | null;
};

export type ShareRow = {
  id: string;
  owner: string;
  bill_id: string;
  status: ShareStatus;
  revision: number;
  bill_revision: number;
  bill: string | null;
  amounts: string | null;
  paid_by: string | null;
  matches: string | null;
  created_at: number;
  expires_at: number;
  revoked_at: number | null;
};
export type ClaimRow = { id: string; key_hash: string; attempt_hash: string | null; name: string; item_ids: string; updated_at: number };

/** A link with its claims, in the order they were made. */
export type Share = { row: ShareRow; claims: ClaimRow[] };

/** A bill as a route took it: the projection, its JSON as it is stored, and its item ids. */
export type ProjectedBill = { bill: BillProjection; json: string; itemIds: string[] };

/** How many times a change reads the link again when it changed in a way the change depends on, before it answers 409. */
export const SHARE_WRITE_ATTEMPTS = 3;

const SHARE_COLUMNS = "id, owner, bill_id, status, revision, bill_revision, bill, amounts, paid_by, matches, created_at, expires_at, revoked_at";

/** Statements whose results [shareFrom] reads: the link whose [by] is [value], and its claims. */
export function shareStatements(db: D1Database, by: "id" | "token_hash", value: string): D1PreparedStatement[] {
  return [
    db.prepare(`SELECT ${SHARE_COLUMNS} FROM shares WHERE ${by} = ?1`).bind(value),
    db
      .prepare(
        `SELECT id, key_hash, attempt_hash, name, item_ids, updated_at FROM share_claims
         WHERE share_id = (SELECT id FROM shares WHERE ${by} = ?1) ORDER BY created_at, id`,
      )
      .bind(value),
  ];
}

/** The link in the two results of [shareStatements], or null when there is none. Rows of this Worker's own tables, shaped by `migrations/`. */
export function shareFrom(results: D1Result[]): Share | null {
  const row = (results[0]?.results[0] as ShareRow | undefined) ?? null;
  return row === null ? null : { row, claims: (results[1]?.results ?? []) as ClaimRow[] };
}

export async function readShare(db: D1Database, by: "id" | "token_hash", value: string): Promise<Share | null> {
  return shareFrom(await db.batch(shareStatements(db, by, value)));
}

/**
 * The link a token leads to, or the answer: 404 for a token never made (or that no link could
 * have), 410 for a link that was revoked or has expired. Expiry is judged now, so an expired link
 * is gone before the daily run deletes it.
 */
export async function linkedShare(db: D1Database, token: string | null, now: number): Promise<{ ok: true; share: Share } | { ok: false; response: Response }> {
  const share = token === null ? null : await readShare(db, "token_hash", await tokenHash(token));
  if (share === null) return { ok: false, response: errorResponse(404, "not_found") };
  if (isGone(share.row, now)) return { ok: false, response: errorResponse(410, "gone") };
  return { ok: true, share };
}

/** Whether a link no longer works: revoked, or [POLICY.shareMs] after it was made. */
export function isGone(row: ShareRow, now: number): boolean {
  return row.revoked_at !== null || now >= row.expires_at;
}

// The JSON a link's row holds is the Worker's own, written from a BillProjection, ShareAmounts,
// ShareMatches and arrays of item ids that were checked when they came in; it is read back as such.

/** The bill a working link shows. Only a revoked link has none, and nothing reads a revoked link's bill. */
export function billOf(row: ShareRow): BillProjection {
  if (row.bill === null) throw new Error("A link without its bill");
  return JSON.parse(row.bill) as BillProjection;
}

export function ownerView({ row, claims }: Share): ShareView {
  return {
    id: row.id,
    billId: row.bill_id,
    billRevision: row.bill_revision,
    status: row.status,
    revision: row.revision,
    createdAt: row.created_at,
    expiresAt: row.expires_at,
    bill: billOf(row),
    claims: claims.map((claim) => ({ id: claim.id, name: claim.name, itemIds: JSON.parse(claim.item_ids) as string[], updatedAt: claim.updated_at })),
    amounts: row.amounts === null ? null : (JSON.parse(row.amounts) as ShareAmounts),
    paidBy: row.paid_by,
    matches: row.matches === null ? null : (JSON.parse(row.matches) as ShareMatches),
  };
}

export function publicView({ row, claims }: Share): PublicShare {
  return {
    status: row.status,
    revision: row.revision,
    expiresAt: row.expires_at,
    bill: billOf(row),
    claims: claims.map((claim) => ({ id: claim.id, name: claim.name, itemIds: JSON.parse(claim.item_ids) as string[] })),
    amounts: row.amounts === null ? null : (JSON.parse(row.amounts) as ShareAmounts),
    paidBy: row.paid_by,
    matches: row.matches === null ? null : (JSON.parse(row.matches) as ShareMatches),
  };
}

/**
 * Writes one change of a link: [statements] go in one batch, the first of them an update of the
 * link's revision that says what the change needs of the link as the batch finds it (see the
 * trigger `shares_change_refused`), and the batch reads the link back. Null when the link no
 * longer allowed the change, or was deleted, after it was read: nothing was written, and the
 * caller reads it again and decides anew.
 */
export async function writeShare(db: D1Database, shareId: string, statements: D1PreparedStatement[]): Promise<Share | null> {
  let results: D1Result[];
  try {
    results = await db.batch([...statements, ...shareStatements(db, "id", shareId)]);
  } catch (error) {
    if (refused(error)) return null;
    throw error;
  }
  // No row to update: the link was deleted after it was read.
  if (results[0].meta.changes !== 1) return null;
  return shareFrom(results.slice(-2));
}

/**
 * The update that guards one change of link [shareId] in a batch of [writeShare]: it moves the
 * link's revision on by one while the link works at [now] and [condition] holds of it as the
 * batch finds it, and is refused otherwise, which rolls the batch back. [set] changes other
 * columns of the link in the same statement, from their values before it. In both, ?1 is the
 * link's id, ?2 is [now], and [values] follow from ?3.
 */
export function guardStatement(db: D1Database, shareId: string, now: number, condition: string, set: string, values: unknown[]): D1PreparedStatement {
  const others = set === "" ? "" : `, ${set}`;
  return db
    .prepare(`UPDATE shares SET revision = CASE WHEN revoked_at IS NULL AND expires_at > ?2 AND ${condition} THEN revision + 1 ELSE 0 END${others} WHERE id = ?1`)
    .bind(shareId, now, ...values);
}

/** SQL that holds while every id of the JSON array bound at [parameter] is an item of the bill of the link that `shares` names. */
export function itemsInBill(parameter: string): string {
  return `NOT EXISTS (SELECT 1 FROM json_each(${parameter}) AS wanted
    WHERE wanted.value NOT IN (SELECT json_extract(item.value, '$.id') FROM json_each(shares.bill, '$.items') AS item))`;
}

/** SQL that holds while every key of the JSON object bound at [parameter], when it is not null, is a claim of the link whose id is ?1. */
export function claimsOnLink(parameter: string): string {
  return `NOT EXISTS (SELECT 1 FROM json_each(${parameter}) AS matched
    WHERE matched.key NOT IN (SELECT id FROM share_claims WHERE share_id = ?1))`;
}

/**
 * Whether a batch was refused because the link changed after it was read: the update of its
 * revision found it no longer allowed the change; or it was deleted, so that a claim made under it
 * no longer has a link; or a claim sent twice at once with the same attempt landed first.
 */
function refused(error: unknown): boolean {
  return (
    error instanceof Error &&
    ["share_refused", "FOREIGN KEY constraint failed", "UNIQUE constraint failed"].some((reason) => error.message.includes(reason))
  );
}

/**
 * A statement that drops from every claim of [shareId] the item ids that are not in [itemIds],
 * the items of the bill the link now shows, keeping the others in their order. Claims that hold
 * none of those are left as they are.
 */
export function keepItemsStatement(db: D1Database, shareId: string, itemIds: string[]): D1PreparedStatement {
  return db
    .prepare(
      `UPDATE share_claims SET item_ids = (
         SELECT json_group_array(kept.value) FROM json_each(share_claims.item_ids) AS kept WHERE kept.value IN (SELECT value FROM json_each(?2)))
       WHERE share_id = ?1 AND EXISTS (
         SELECT 1 FROM json_each(share_claims.item_ids) AS kept WHERE kept.value NOT IN (SELECT value FROM json_each(?2)))`,
    )
    .bind(shareId, JSON.stringify(itemIds));
}

/** What names a link's [ShareRoom]: its id and when it was made, the two together naming one link only. */
export type RoomName = Pick<ShareRow, "id" | "created_at">;

/**
 * The Durable Object that holds the live connections of the link [row]. It is named by the link's
 * id and when the link was made, so that a link made again under the same id, once the first was
 * deleted, never reaches the connections of the first.
 */
export function shareRoom(env: Env, row: RoomName): DurableObjectStub<ShareRoom> {
  return env.SHARE_ROOM.get(env.SHARE_ROOM.idFromName(`${row.id}:${row.created_at}`));
}

/**
 * Passes [update] on to every page that has the link [row] open, once the answer is on its way.
 * It never fails what it tells of: a page that misses it reads the link again when it reconnects.
 */
export function tell(env: Env, ctx: ExecutionContext, row: RoomName, update: LiveUpdate): void {
  ctx.waitUntil(
    shareRoom(env, row)
      .broadcast(update)
      .catch((error: unknown) => {
        console.error(JSON.stringify({ event: "share_broadcast_failed", type: update.type, error: error instanceof Error ? error.name : "unknown" }));
      }),
  );
}

/** The most items a bill link's bill holds, and so the most a claim can take. */
export const MAX_ITEMS = 200;
/** An id of a line or a person on a bill. */
const LINE_ID = /^[A-Za-z0-9_-]{1,64}$/;
/** A quantity as the app writes a decimal number: without exponent, leading zeros or trailing dot. */
const QUANTITY = /^-?(?:0|[1-9]\d{0,19})(?:\.\d{1,20})?$/;
/** The participant who is the bill's owner; their name may be empty, and the page then calls them the bill's owner. */
const OWNER_PARTICIPANT = "me";

/**
 * The bill [value] holds, when it follows the contract's BillProjection; [TOO_LARGE] past
 * [POLICY.maxBillDocumentBytes] as stored; null otherwise. Only the fields the contract names are
 * kept, so that the page never shows anything else the app might have sent.
 */
export function billProjectionOf(value: unknown): ProjectedBill | typeof TOO_LARGE | null {
  if (!isRecord(value)) return null;
  const merchant = optional(value.merchant, (text) => textOf(text, 1, 80));
  const date = optional(value.date, (text) => (typeof text === "string" && isDay(text) ? text : null));
  const currency = currencyOf(value.currency);
  const items = listOf(value.items, 1, MAX_ITEMS, itemOf);
  const charges = listOf(value.charges, 0, 20, chargeOf);
  const participants = listOf(value.participants, 1, 30, participantOf);
  const totalMinor = moneyOf(value.totalMinor);
  if (merchant === undefined || date === undefined || currency === null || totalMinor === null) return null;
  if (items === null || charges === null || participants === null) return null;
  const bill: BillProjection = { merchant, date, currency, items, charges, totalMinor, participants };
  const json = JSON.stringify(bill);
  if (byteLength(json) > POLICY.maxBillDocumentBytes) return TOO_LARGE;
  return { bill, json, itemIds: items.map((item) => item.id) };
}

/** Null for null or a missing value; what [read] makes of anything else, or undefined when it makes nothing of it. */
function optional<T>(value: unknown, read: (value: unknown) => T | null): T | null | undefined {
  if (value === undefined || value === null) return null;
  return read(value) ?? undefined;
}

/** [value] as a list of [min] to [max] entries that [entryOf] reads, each with an id unique within it; null otherwise. */
function listOf<T>(value: unknown, min: number, max: number, entryOf: (entry: Record<string, unknown>, id: string) => T | null): T[] | null {
  if (!Array.isArray(value) || value.length < min || value.length > max) return null;
  const entries: T[] = [];
  const ids = new Set<string>();
  for (const entry of value) {
    if (!isRecord(entry) || typeof entry.id !== "string" || !LINE_ID.test(entry.id) || ids.has(entry.id)) return null;
    const read = entryOf(entry, entry.id);
    if (read === null) return null;
    ids.add(entry.id);
    entries.push(read);
  }
  return entries;
}

function itemOf(entry: Record<string, unknown>, id: string): BillProjection["items"][number] | null {
  const name = textOf(entry.name, 1, 80);
  const quantity = optional(entry.quantity, (text) => (typeof text === "string" && QUANTITY.test(text) ? text : null));
  const amountMinor = moneyOf(entry.amountMinor);
  return name === null || quantity === undefined || amountMinor === null ? null : { id, name, quantity, amountMinor };
}

function chargeOf(entry: Record<string, unknown>, id: string): BillProjection["charges"][number] | null {
  const label = textOf(entry.label, 1, 80);
  const amount = entry.amountMinor;
  // A charge may take money off, as a discount does.
  const amountMinor = typeof amount === "number" && Number.isInteger(amount) && Math.abs(amount) <= POLICY.maxAmountMinor ? amount : null;
  return label === null || amountMinor === null ? null : { id, label, amountMinor };
}

function participantOf(entry: Record<string, unknown>, id: string): BillProjection["participants"][number] | null {
  const name = textOf(entry.name, id === OWNER_PARTICIPANT ? 0 : 1, 60);
  return name === null ? null : { id, name };
}

/**
 * What each participant of [bill] owes, when [value] names every one of them and nobody else,
 * each with an amount of 0 to [POLICY.maxAmountMinor] and a payment that is null or one the
 * contract takes, and the amounts together come to the bill's total; null otherwise. The
 * participants keep the bill's order.
 */
export function amountsOf(value: unknown, bill: BillProjection): ShareAmounts | null {
  if (!isRecord(value)) return null;
  const participants = new Set(bill.participants.map((participant) => participant.id));
  const named = Object.keys(value);
  if (named.length !== participants.size || !named.every((id) => participants.has(id))) return null;
  const entries: Array<[string, ShareAmounts[string]]> = [];
  // Summed as big integers: 30 amounts of up to 10^15 go past what a double holds exactly.
  let total = 0n;
  for (const { id } of bill.participants) {
    const entry = value[id];
    if (!isRecord(entry)) return null;
    const amountMinor = moneyOf(entry.amountMinor);
    const payment = optionalPaymentOf(entry.payment);
    if (amountMinor === null || payment === undefined) return null;
    total += BigInt(amountMinor);
    entries.push([id, { amountMinor, payment }]);
  }
  // Built from entries, so that an id such as `__proto__` stays an id.
  return total === BigInt(bill.totalMinor) ? Object.fromEntries(entries) : null;
}

/**
 * Who paid the whole bill, as a settlement names them: null for null or a missing value, the id
 * when [value] is the id of one participant of [bill], undefined for anything else.
 */
export function paidByOf(value: unknown, bill: BillProjection): string | null | undefined {
  if (value === undefined || value === null) return null;
  return typeof value === "string" && bill.participants.some((participant) => participant.id === value) ? value : undefined;
}

/**
 * Which participant of [bill] each claim is, as a settlement says: undefined unless [value] is
 * null, missing, or an object whose every value is the id of a participant of [bill]; null when it
 * matches no claim. Several claims may be the same participant. Whether each key is one of the
 * link's claims is checked against the link itself. The keys are sorted, so that the same matches
 * are always written the same way.
 */
export function matchesOf(value: unknown, bill: BillProjection): ShareMatches | null | undefined {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) return undefined;
  const participants = new Set(bill.participants.map((participant) => participant.id));
  const entries: Array<[string, string]> = [];
  for (const [claimId, participantId] of Object.entries(value)) {
    if (typeof participantId !== "string" || !participants.has(participantId)) return undefined;
    entries.push([claimId, participantId]);
  }
  if (entries.length === 0) return null;
  entries.sort(([one], [other]) => (one < other ? -1 : one > other ? 1 : 0));
  // Built from entries, so that a key such as `__proto__` stays a key.
  return Object.fromEntries(entries);
}
