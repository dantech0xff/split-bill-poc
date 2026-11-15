import { changeGroup, type Conversion, isMember, keep, logStatement, outsider, reply, stateAnswer, write } from "./group-state";
import { signedIn } from "./identity";
import { byteLength, countOf, idOf, inputOf, isDay, isRecord, moneyOf, textOf, TOO_LARGE, uuidOf } from "./input";
import { POLICY } from "./policy";
import { errorResponse } from "./respond";

/** A bill as the app sends it, with its JSON parts serialized the way they are stored. */
type BillInput = {
  expectedRevision: number;
  document: string;
  /** The document's size in UTF-8, which the group's total of [POLICY.maxGroupDocumentBytes] counts. */
  documentBytes: number;
  conversion: string | null;
  totalMinor: number;
  shares: string;
  paid: string;
  /** Every member the shares and the payments name. */
  memberIds: string[];
};

type StoredBill = {
  owner_member_id: string;
  revision: number;
  document: string | null;
  document_bytes: number | null;
  conversion: string | null;
  total_minor: number | null;
  shares: string | null;
  paid: string | null;
  deleted_at: number | null;
};

/** A rate as a decimal number, which the app keeps as text so that no digit is lost. */
const RATE = /^(?:0|[1-9]\d{0,17})(?:\.\d{1,18})?$/;

/**
 * The bill [body] holds, when it follows the contract: amounts are whole minor units from 0 to
 * [POLICY.maxAmountMinor], the shares and the payments each add up to the total exactly, and
 * they are keyed by member ids. Whether those members are in the group is checked against the
 * group itself. A document over [POLICY.maxBillDocumentBytes] is [TOO_LARGE].
 */
function billOf(body: Record<string, unknown>): BillInput | typeof TOO_LARGE | null {
  const expectedRevision = countOf(body.expectedRevision);
  const totalMinor = moneyOf(body.totalMinor);
  const shares = amountsOf(body.shares);
  const paid = amountsOf(body.paid);
  const conversion = body.conversion === undefined || body.conversion === null ? null : conversionOf(body.conversion);
  if (expectedRevision === null || totalMinor === null || shares === null || paid === null || conversion === undefined) return null;
  if (!isRecord(body.document)) return null;
  // Summed as big integers: amounts up to 10^15 for many members go past what a double holds exactly.
  const total = BigInt(totalMinor);
  if (sum(shares) !== total || sum(paid) !== total) return null;
  const document = JSON.stringify(body.document);
  const documentBytes = byteLength(document);
  if (documentBytes > POLICY.maxBillDocumentBytes) return TOO_LARGE;
  return {
    expectedRevision,
    document,
    documentBytes,
    conversion: conversion === null ? null : JSON.stringify(conversion),
    totalMinor,
    shares: JSON.stringify(shares),
    paid: JSON.stringify(paid),
    memberIds: [...new Set([...Object.keys(shares), ...Object.keys(paid)])],
  };
}

/** A map from member id to an amount, or null when a key is not a member id or a value not an amount. */
function amountsOf(value: unknown): Record<string, number> | null {
  if (!isRecord(value)) return null;
  const amounts: Record<string, number> = {};
  for (const [memberId, amount] of Object.entries(value)) {
    const minor = moneyOf(amount);
    // Ids are compared as stored, so only the lower-case form a member id has is one.
    if (minor === null || idOf(memberId) !== memberId) return null;
    amounts[memberId] = minor;
  }
  return amounts;
}

function sum(amounts: Record<string, number>): bigint {
  return Object.values(amounts).reduce((total, amount) => total + BigInt(amount), 0n);
}

/** The conversion into the group's currency: a positive rate as text, its day, and where it came from; undefined when it is not one. */
function conversionOf(value: unknown): Conversion | undefined {
  if (!isRecord(value)) return undefined;
  const { rate, date } = value;
  const source = textOf(value.source);
  if (typeof rate !== "string" || !RATE.test(rate) || !/[1-9]/.test(rate)) return undefined;
  if (typeof date !== "string" || !isDay(date) || source === null) return undefined;
  return { rate, date, source };
}

function sameBill(stored: StoredBill, bill: BillInput): boolean {
  return (
    stored.document === bill.document &&
    stored.conversion === bill.conversion &&
    stored.total_minor === bill.totalMinor &&
    stored.shares === bill.shares &&
    stored.paid === bill.paid
  );
}

/**
 * `PUT /v1/groups/{gid}/bills/{billId}`: adds a bill to the group or changes one. A new bill, sent
 * with `expectedRevision` 0, belongs to the caller's member; an existing one may be changed by
 * the member it belongs to or by the group's owner. `expectedRevision` must be the revision
 * stored, else 409, and the bill's revision becomes one more. The same change sent again, which
 * finds the bill already at that revision with the same content, changes nothing. A deleted bill
 * keeps its id, so a bill with that id cannot come back (409).
 */
export const putBillRoute = signedIn(async ({ request, env, params, account }) => {
  const groupId = uuidOf(params[0]);
  if (groupId === null) return errorResponse(404, "not_found");
  const billId = uuidOf(params[1]);
  if (billId === null) return errorResponse(400, "input");
  const input = await inputOf(request, POLICY.maxBodyBytes, billOf);
  if (!input.ok) return input.response;
  const bill = input.value;
  const db = env.DB;
  return changeGroup(env, groupId, account, async (group) => {
    if (!isMember(group)) return reply(outsider(group));
    const members = new Set(group.members.map((member) => member.id));
    if (!bill.memberIds.every((memberId) => members.has(memberId))) return reply(errorResponse(400, "input"));
    const [storedResult, liveResult] = await db.batch([
      db
        .prepare(
          `SELECT owner_member_id, revision, document, document_bytes, conversion, total_minor, shares, paid, deleted_at
           FROM group_bills WHERE group_id = ?1 AND id = ?2`,
        )
        .bind(groupId, billId),
      // Counted on the index of live bills, so deleted ones are never read.
      db
        .prepare("SELECT count(*) AS live, coalesce(sum(document_bytes), 0) AS bytes FROM group_bills WHERE group_id = ?1 AND deleted_at IS NULL")
        .bind(groupId),
    ]);
    // Rows of this Worker's own table, shaped by migrations/.
    const stored = (storedResult.results[0] as StoredBill | undefined) ?? null;
    const { live, bytes } = (liveResult.results[0] as { live: number; bytes: number } | undefined) ?? { live: 0, bytes: 0 };
    if (stored !== null && stored.deleted_at !== null) return reply(errorResponse(409, "conflict"));
    if (stored !== null && stored.owner_member_id !== group.me.id && !group.isOwner) return reply(errorResponse(403, "forbidden"));
    if (stored !== null && stored.revision === bill.expectedRevision + 1 && sameBill(stored, bill)) return keep(stateAnswer());
    if ((stored?.revision ?? 0) !== bill.expectedRevision) return reply(errorResponse(409, "conflict"));
    // The documents of the group's other bills, and this one as it would be.
    if (bytes - (stored?.document_bytes ?? 0) + bill.documentBytes > POLICY.maxGroupDocumentBytes) return reply(errorResponse(413, "too_large"));
    const now = Date.now();
    if (stored === null) {
      if (live >= POLICY.maxBills) return reply(errorResponse(413, "too_large"));
      return write(
        [
          db
            .prepare(
              `INSERT INTO group_bills (group_id, id, owner_member_id, revision, created_at, updated_at, document, document_bytes, conversion,
                 total_minor, shares, paid)
               VALUES (?1, ?2, ?3, 1, ?4, ?4, ?5, ?6, ?7, ?8, ?9, ?10)`,
            )
            .bind(groupId, billId, group.me.id, now, bill.document, bill.documentBytes, bill.conversion, bill.totalMinor, bill.shares, bill.paid),
          logStatement(db, groupId, now, "bill_added", group.me.id, billId),
        ],
        stateAnswer(),
      );
    }
    return write(
      [
        db
          .prepare(
            `UPDATE group_bills SET revision = ?3 + 1, updated_at = ?4, document = ?5, document_bytes = ?6, conversion = ?7, total_minor = ?8,
               shares = ?9, paid = ?10
             WHERE group_id = ?1 AND id = ?2 AND revision = ?3 AND deleted_at IS NULL`,
          )
          .bind(groupId, billId, bill.expectedRevision, now, bill.document, bill.documentBytes, bill.conversion, bill.totalMinor, bill.shares, bill.paid),
        logStatement(db, groupId, now, "bill_changed", group.me.id, billId),
      ],
      stateAnswer(),
    );
  });
});

/**
 * `DELETE /v1/groups/{gid}/bills/{billId}`: the member the bill belongs to, or the group's owner,
 * deletes it. Its row stays, emptied, so that its id is never used again; it leaves the state.
 * Deleting it again changes nothing.
 */
export const deleteBillRoute = signedIn(async ({ env, params, account }) => {
  const groupId = uuidOf(params[0]);
  const billId = uuidOf(params[1]);
  if (groupId === null || billId === null) return errorResponse(404, "not_found");
  const db = env.DB;
  return changeGroup(env, groupId, account, async (group) => {
    if (!isMember(group)) return reply(outsider(group));
    const stored = await db
      .prepare("SELECT owner_member_id, deleted_at FROM group_bills WHERE group_id = ?1 AND id = ?2")
      .bind(groupId, billId)
      .first<{ owner_member_id: string; deleted_at: number | null }>();
    if (stored === null) return reply(errorResponse(404, "not_found"));
    if (stored.owner_member_id !== group.me.id && !group.isOwner) return reply(errorResponse(403, "forbidden"));
    if (stored.deleted_at !== null) return keep(stateAnswer());
    const now = Date.now();
    return write(
      [
        db
          .prepare(
            `UPDATE group_bills SET revision = revision + 1, updated_at = ?3, deleted_at = ?3,
               document = NULL, document_bytes = NULL, conversion = NULL, total_minor = NULL, shares = NULL, paid = NULL
             WHERE group_id = ?1 AND id = ?2`,
          )
          .bind(groupId, billId, now),
        logStatement(db, groupId, now, "bill_deleted", group.me.id, billId),
      ],
      stateAnswer(),
    );
  });
});

/**
 * `POST /v1/groups/{gid}/settlements` with `{"id","fromMemberId","amountMinor"}`: the member who
 * received money records that another member paid it. Settlements are only ever added: no route
 * changes or deletes one, and the same id sent again leaves the settlement as it was.
 */
export const recordSettlementRoute = signedIn(async ({ request, env, params, account }) => {
  const groupId = uuidOf(params[0]);
  if (groupId === null) return errorResponse(404, "not_found");
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const id = idOf(body.id);
    const fromMemberId = idOf(body.fromMemberId);
    const amountMinor = moneyOf(body.amountMinor);
    return id === null || fromMemberId === null || amountMinor === null || amountMinor === 0 ? null : { id, fromMemberId, amountMinor };
  });
  if (!input.ok) return input.response;
  const { id, fromMemberId, amountMinor } = input.value;
  const db = env.DB;
  return changeGroup(env, groupId, account, async (group) => {
    if (!isMember(group)) return reply(outsider(group));
    const found = await db
      .prepare(
        `SELECT (SELECT recorded_by_member_id FROM settlements WHERE group_id = ?1 AND id = ?2) AS recorded_by,
                (SELECT count(*) FROM settlements WHERE group_id = ?1) AS settlements`,
      )
      .bind(groupId, id)
      .first<{ recorded_by: string | null; settlements: number }>();
    const recordedBy = found?.recorded_by ?? null;
    if (recordedBy !== null) return recordedBy === group.me.id ? keep(stateAnswer()) : reply(errorResponse(409, "conflict"));
    if (fromMemberId === group.me.id || !group.members.some((member) => member.id === fromMemberId)) return reply(errorResponse(400, "input"));
    if ((found?.settlements ?? 0) >= POLICY.maxSettlements) return reply(errorResponse(413, "too_large"));
    const now = Date.now();
    return write(
      [
        db
          .prepare(
            `INSERT INTO settlements (group_id, id, from_member_id, to_member_id, amount_minor, recorded_by_member_id, request_id, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?4, NULL, ?6)`,
          )
          .bind(groupId, id, fromMemberId, group.me.id, amountMinor, now),
        logStatement(db, groupId, now, "settlement_recorded", group.me.id, id),
      ],
      stateAnswer(),
    );
  });
});
