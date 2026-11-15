import { changeGroup, type Group, isMember, logStatement, outsider, type Plan, readGroup, reply, write } from "./group-state";
import { type Call, signedIn } from "./identity";
import type { Env } from "./index";
import { byteLength, type Currency, currencyOf, idOf, inputOf, isRecord, moneyOf, optionalUuidOf, textOf, TOO_LARGE, uuidOf } from "./input";
import { linkUrl, requestToken, tokenHash, tokenOf } from "./links";
import { POLICY } from "./policy";
import { notify } from "./push";
import { admitHourly } from "./rate-limit";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";

/** How often a debtor may be reminded, in days; 0 is never. */
const REMIND_DAYS: readonly number[] = [0, 1, 3, 7];

const COLUMNS = `id, creditor, debtor, debtor_bound_at, amount_minor, currency_code, currency_digits, payer_name, creditor_name, title,
  method, group_id, to_member_id, creditor_member_id, bill_id, created_at, reported_paid_at, confirmed_paid_at, remind_every_days, deleted_at`;

type RequestRow = {
  id: string;
  creditor: string | null;
  debtor: string | null;
  debtor_bound_at: number | null;
  amount_minor: number;
  currency_code: string;
  currency_digits: number;
  payer_name: string;
  creditor_name: string;
  title: string | null;
  method: string;
  group_id: string | null;
  to_member_id: string | null;
  creditor_member_id: string | null;
  bill_id: string | null;
  created_at: number;
  reported_paid_at: number | null;
  confirmed_paid_at: number | null;
  remind_every_days: number;
  deleted_at: number | null;
};

/** A payment request as one of its two people sees it. Accounts never appear in it. */
export type RequestView = {
  id: string;
  role: "creditor" | "debtor";
  amountMinor: number;
  currency: Currency;
  payerName: string;
  creditorName: string;
  title: string | null;
  method: unknown;
  groupId: string | null;
  toMemberId: string | null;
  billId: string | null;
  createdAt: number;
  reportedPaidAt: number | null;
  confirmedPaidAt: number | null;
  remindEveryDays: number;
  debtorBound: boolean;
};

function viewOf(row: RequestRow, role: RequestView["role"]): RequestView {
  return {
    id: row.id,
    role,
    amountMinor: row.amount_minor,
    currency: { code: row.currency_code, fractionDigits: row.currency_digits },
    payerName: row.payer_name,
    creditorName: row.creditor_name,
    title: row.title,
    method: JSON.parse(row.method) as unknown,
    groupId: row.group_id,
    toMemberId: row.to_member_id,
    billId: row.bill_id,
    createdAt: row.created_at,
    reportedPaidAt: row.reported_paid_at,
    confirmedPaidAt: row.confirmed_paid_at,
    remindEveryDays: row.remind_every_days,
    debtorBound: row.debtor_bound_at !== null,
  };
}

function viewResponse(row: RequestRow, role: RequestView["role"]): Response {
  return jsonResponse(200, viewOf(row, role));
}

async function requestRow(db: D1Database, id: string): Promise<RequestRow | null> {
  return db.prepare(`SELECT ${COLUMNS} FROM requests WHERE id = ?1`).bind(id).first<RequestRow>();
}

/** The request the path names, when the caller is its creditor or its bound debtor and it is not deleted; 404 otherwise. */
async function ownRequest(
  db: D1Database,
  segment: string | undefined,
  account: string,
): Promise<{ ok: true; row: RequestRow } | { ok: false; response: Response }> {
  const id = uuidOf(segment);
  const row = id === null ? null : await requestRow(db, id);
  if (row === null || row.deleted_at !== null || (row.creditor !== account && row.debtor !== account)) {
    return { ok: false, response: errorResponse(404, "not_found") };
  }
  return { ok: true, row };
}

type NewRequest = {
  id: string;
  amountMinor: number;
  currency: Currency;
  payerName: string;
  creditorName: string;
  title: string | null;
  method: string;
  groupId: string | null;
  toMemberId: string | null;
  billId: string | null;
  remindEveryDays: number;
};

function newRequestOf(body: Record<string, unknown>): NewRequest | typeof TOO_LARGE | null {
  const id = idOf(body.id);
  const amountMinor = moneyOf(body.amountMinor);
  const currency = currencyOf(body.currency);
  const payerName = textOf(body.payerName);
  const creditorName = textOf(body.creditorName);
  const title = body.title === undefined || body.title === null ? "" : textOf(body.title, 0, 80);
  const groupId = optionalUuidOf(body.groupId);
  const toMemberId = optionalUuidOf(body.toMemberId);
  const billId = optionalUuidOf(body.billId);
  const remindEveryDays = body.remindEveryDays ?? 0;
  if (id === null || amountMinor === null || amountMinor === 0 || currency === null) return null;
  if (payerName === null || creditorName === null || title === null) return null;
  if (groupId === undefined || toMemberId === undefined || billId === undefined || !isRecord(body.method)) return null;
  // A member is named exactly when a group is.
  if ((groupId === null) !== (toMemberId === null)) return null;
  if (typeof remindEveryDays !== "number" || !REMIND_DAYS.includes(remindEveryDays)) return null;
  const method = JSON.stringify(body.method);
  if (byteLength(method) > POLICY.maxMethodBytes) return TOO_LARGE;
  return { id, amountMinor, currency, payerName, creditorName, title: title === "" ? null : title, method, groupId, toMemberId, billId, remindEveryDays };
}

/** The answer to creating a request: 201 with the request as its creditor sees it, and the link to share. */
async function created(call: Call, row: RequestRow): Promise<Response> {
  return jsonResponse(201, { request: viewOf(row, "creditor"), url: linkUrl(call.request, "r", await requestToken(call.env, row.id)) });
}

/**
 * `POST /v1/requests`: the caller asks someone to pay them, with the one payment method they
 * chose. In a group, the payer is a member other than the caller, the currency is the group's,
 * and a payer with an account becomes the debtor at once and is notified; anyone else becomes
 * the debtor by accepting the link. The same request sent again answers as the first time did. A
 * creditor may make 30 requests an hour.
 */
export const createRequestRoute = signedIn(async (call) => {
  const { request, env, ctx, account } = call;
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, newRequestOf);
  if (!input.ok) return input.response;
  const wanted = input.value;
  const db = env.DB;
  let debtor: string | null = null;
  let creditorMemberId: string | null = null;
  if (wanted.groupId !== null) {
    const group = await readGroup(db, wanted.groupId, account);
    if (group === null) return errorResponse(404, "not_found");
    if (!isMember(group)) return outsider(group);
    const payer = group.members.find((member) => member.id === wanted.toMemberId);
    const sameCurrency = group.row.currency_code === wanted.currency.code && group.row.currency_digits === wanted.currency.fractionDigits;
    if (payer === undefined || payer.id === group.me.id || !sameCurrency) return errorResponse(400, "input");
    debtor = payer.account;
    creditorMemberId = group.me.id;
  }
  const existing = await requestRow(db, wanted.id);
  if (existing !== null) return existing.creditor === account && existing.deleted_at === null ? created(call, existing) : errorResponse(409, "conflict");
  const now = Date.now();
  const gate = await admitHourly(db, account, "request", POLICY.requestsPerHour, now);
  if (!gate.ok) return errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
  let row: RequestRow | null;
  try {
    row = await db
      .prepare(
        `INSERT INTO requests (id, creditor, debtor, debtor_bound_at, amount_minor, currency_code, currency_digits, payer_name, creditor_name, title,
           method, group_id, to_member_id, creditor_member_id, bill_id, link_hash, created_at, remind_every_days)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18)
         RETURNING ${COLUMNS}`,
      )
      .bind(
        wanted.id,
        account,
        debtor,
        debtor === null ? null : now,
        wanted.amountMinor,
        wanted.currency.code,
        wanted.currency.fractionDigits,
        wanted.payerName,
        wanted.creditorName,
        wanted.title,
        wanted.method,
        wanted.groupId,
        wanted.toMemberId,
        creditorMemberId,
        wanted.billId,
        await tokenHash(await requestToken(env, wanted.id)),
        now,
        wanted.remindEveryDays,
      )
      .first<RequestRow>();
  } catch (error) {
    // The same request came in twice at once; the one that landed answers both.
    if (!(error instanceof Error && error.message.includes("UNIQUE constraint failed"))) throw error;
    const landed = await requestRow(db, wanted.id);
    return landed !== null && landed.creditor === account && landed.deleted_at === null ? created(call, landed) : errorResponse(409, "conflict");
  }
  if (row === null) throw new Error("The request was not stored");
  ctx.waitUntil(notify(env, row.debtor, { type: "request", requestId: row.id }));
  return created(call, row);
});

/** `GET /v1/requests`: the requests the caller made or is the debtor of, newest first, at most 200. */
export const listRequestsRoute = signedIn(async ({ env, account }) => {
  const { results } = await env.DB.prepare(
    `SELECT ${COLUMNS} FROM requests WHERE (creditor = ?1 OR debtor = ?1) AND deleted_at IS NULL ORDER BY created_at DESC, id DESC LIMIT ?2`,
  )
    .bind(account, POLICY.listedRequests)
    .all<RequestRow>();
  return jsonResponse(200, { requests: results.map((row) => viewOf(row, row.creditor === account ? "creditor" : "debtor")) });
});

/** The request a link's token leads to, or the answer: 404 for a link never made, 410 for one whose request was deleted. */
async function linkedRequest(db: D1Database, segment: string | undefined): Promise<{ ok: true; row: RequestRow } | { ok: false; response: Response }> {
  const token = tokenOf(segment);
  const row = token === null ? null : await db.prepare(`SELECT ${COLUMNS} FROM requests WHERE link_hash = ?1`).bind(await tokenHash(token)).first<RequestRow>();
  if (row === null) return { ok: false, response: errorResponse(404, "not_found") };
  if (row.deleted_at !== null) return { ok: false, response: errorResponse(410, "gone") };
  return { ok: true, row };
}

/**
 * `GET /v1/request-links/{token}`: the request a link carries, as the caller will see it once
 * they accept it: as its debtor, bound. Its creditor sees it as theirs. When another account
 * already accepted it, the answer is 409, and nothing of the request is shown.
 */
export const readRequestLinkRoute = signedIn(async ({ env, params, account }) => {
  const linked = await linkedRequest(env.DB, params[0]);
  if (!linked.ok) return linked.response;
  const row = linked.row;
  if (row.creditor === account) return viewResponse(row, "creditor");
  if (row.debtor_bound_at !== null && row.debtor !== account) return errorResponse(409, "conflict");
  return jsonResponse(200, { ...viewOf(row, "debtor"), debtorBound: true });
});

/**
 * `POST /v1/request-links/{token}/accept`: the caller becomes the request's debtor, when nobody
 * is yet; accepting again changes nothing. 409 when another account is the debtor, 403 for the
 * creditor, who cannot owe themself.
 */
export const acceptRequestLinkRoute = signedIn(async ({ env, params, account }) => {
  for (let attempt = 1; attempt <= 2; attempt++) {
    const linked = await linkedRequest(env.DB, params[0]);
    if (!linked.ok) return linked.response;
    const row = linked.row;
    if (row.creditor === account) return errorResponse(403, "forbidden");
    if (row.debtor_bound_at !== null) return row.debtor === account ? viewResponse(row, "debtor") : errorResponse(409, "conflict");
    const bound = await env.DB.prepare(
      `UPDATE requests SET debtor = ?2, debtor_bound_at = ?3 WHERE id = ?1 AND debtor_bound_at IS NULL AND deleted_at IS NULL RETURNING ${COLUMNS}`,
    )
      .bind(row.id, account, Date.now())
      .first<RequestRow>();
    if (bound !== null) return viewResponse(bound, "debtor");
    // Someone accepted it, or its creditor deleted it, just now: the next read says which.
  }
  return errorResponse(409, "conflict");
});

/**
 * `POST /v1/requests/{id}/reported-paid`: the debtor says they paid. It is set once, and the
 * creditor is notified; after that, or once the creditor confirmed, it changes nothing.
 */
export const reportPaidRoute = signedIn(async ({ env, ctx, params, account }) => {
  const own = await ownRequest(env.DB, params[0], account);
  if (!own.ok) return own.response;
  const row = own.row;
  if (row.debtor !== account) return errorResponse(403, "forbidden");
  if (row.reported_paid_at !== null || row.confirmed_paid_at !== null) return viewResponse(row, "debtor");
  const reported = await env.DB.prepare(
    `UPDATE requests SET reported_paid_at = ?3
     WHERE id = ?1 AND debtor = ?2 AND reported_paid_at IS NULL AND confirmed_paid_at IS NULL AND deleted_at IS NULL
     RETURNING ${COLUMNS}`,
  )
    .bind(row.id, account, Date.now())
    .first<RequestRow>();
  if (reported === null) {
    // It changed just now; answer with what it is.
    const again = await ownRequest(env.DB, params[0], account);
    return again.ok ? viewResponse(again.row, "debtor") : again.response;
  }
  ctx.waitUntil(notify(env, reported.creditor, { type: "reported", requestId: reported.id }));
  return viewResponse(reported, "debtor");
});

/**
 * `POST /v1/requests/{id}/confirmed-paid`: the creditor confirms they were paid. It is set once,
 * and the debtor is notified. For a request in a group, the same batch records the settlement
 * from the payer's member to the creditor's, so the group's balances change exactly once.
 */
export const confirmPaidRoute = signedIn(async ({ env, ctx, params, account }) => {
  for (let attempt = 1; attempt <= 3; attempt++) {
    const own = await ownRequest(env.DB, params[0], account);
    if (!own.ok) return own.response;
    const row = own.row;
    if (row.creditor !== account) return errorResponse(403, "forbidden");
    if (row.confirmed_paid_at !== null) return viewResponse(row, "creditor");
    const tell = (confirmed: RequestRow) => ctx.waitUntil(notify(env, confirmed.debtor, { type: "confirmed", requestId: confirmed.id }));
    if (row.group_id === null) {
      const confirmed = await env.DB.prepare(
        `UPDATE requests SET confirmed_paid_at = ?3
         WHERE id = ?1 AND creditor = ?2 AND confirmed_paid_at IS NULL AND group_id IS NULL AND deleted_at IS NULL
         RETURNING ${COLUMNS}`,
      )
        .bind(row.id, account, Date.now())
        .first<RequestRow>();
      if (confirmed === null) continue;
      tell(confirmed);
      return viewResponse(confirmed, "creditor");
    }
    const response = await changeGroup(env, row.group_id, account, (group) => confirmInGroup(env, group, row.id, account, tell));
    // The group was deleted after the request was read, so the request no longer belongs to it.
    if (response.status === 404) continue;
    return response;
  }
  return errorResponse(409, "conflict");
});

/**
 * The confirmation of a group request, decided on the group as it is: the request is read again
 * within the change, so that a confirmation sent twice at once records one settlement. Anything
 * that moved the request out of the group answers 404, after which the request is read anew.
 */
async function confirmInGroup(env: Env, group: Group, id: string, account: string, tell: (row: RequestRow) => void): Promise<Plan> {
  const db = env.DB;
  const fresh = await requestRow(db, id);
  if (fresh === null || fresh.deleted_at !== null || fresh.creditor !== account || fresh.group_id !== group.row.id) {
    return reply(errorResponse(404, "not_found"));
  }
  if (fresh.confirmed_paid_at !== null) return reply(viewResponse(fresh, "creditor"));
  if (fresh.to_member_id === null || fresh.creditor_member_id === null) throw new Error("A group request without its members");
  const now = Date.now();
  const settlementId = crypto.randomUUID();
  const confirmed: RequestRow = { ...fresh, confirmed_paid_at: now };
  return write(
    [
      db.prepare("UPDATE requests SET confirmed_paid_at = ?2 WHERE id = ?1 AND confirmed_paid_at IS NULL").bind(id, now),
      db
        .prepare(
          `INSERT INTO settlements (group_id, id, from_member_id, to_member_id, amount_minor, recorded_by_member_id, request_id, created_at)
           VALUES (?1, ?2, ?3, ?4, ?5, ?4, ?6, ?7)`,
        )
        .bind(group.row.id, settlementId, fresh.to_member_id, fresh.creditor_member_id, fresh.amount_minor, id, now),
      logStatement(db, group.row.id, now, "settlement_recorded", fresh.creditor_member_id, settlementId),
    ],
    () => viewResponse(confirmed, "creditor"),
    () => tell(confirmed),
  );
}

/** `PATCH /v1/requests/{id}` with `{"remindEveryDays"}`: the creditor changes how often the debtor is reminded. */
export const changeRequestRoute = signedIn(async ({ request, env, params, account }) => {
  if (uuidOf(params[0]) === null) return errorResponse(404, "not_found");
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const days = body.remindEveryDays;
    return typeof days === "number" && REMIND_DAYS.includes(days) ? { days } : null;
  });
  if (!input.ok) return input.response;
  const own = await ownRequest(env.DB, params[0], account);
  if (!own.ok) return own.response;
  if (own.row.creditor !== account) return errorResponse(403, "forbidden");
  const changed = await env.DB.prepare(`UPDATE requests SET remind_every_days = ?3 WHERE id = ?1 AND creditor = ?2 AND deleted_at IS NULL RETURNING ${COLUMNS}`)
    .bind(own.row.id, account, input.value.days)
    .first<RequestRow>();
  return changed === null ? errorResponse(404, "not_found") : viewResponse(changed, "creditor");
});

/**
 * `DELETE /v1/requests/{id}`: the creditor withdraws a request that is not confirmed; 409 once it
 * is. What it held is emptied at once; the row itself stays for a while, so that its link
 * answers 410. Deleting it again answers 204 as well.
 */
export const deleteRequestRoute = signedIn(async ({ env, params, account }) => {
  const id = uuidOf(params[0]);
  for (let attempt = 1; id !== null && attempt <= 2; attempt++) {
    const row = await requestRow(env.DB, id);
    if (row === null || (row.creditor !== account && row.debtor !== account)) break;
    if (row.creditor !== account) return errorResponse(403, "forbidden");
    if (row.deleted_at !== null) return emptyResponse(204);
    if (row.confirmed_paid_at !== null) return errorResponse(409, "conflict");
    const deleted = await env.DB.prepare(
      `UPDATE requests SET deleted_at = ?3, debtor = NULL, payer_name = '', creditor_name = '', title = NULL, method = '{}',
         group_id = NULL, to_member_id = NULL, creditor_member_id = NULL, bill_id = NULL
       WHERE id = ?1 AND creditor = ?2 AND confirmed_paid_at IS NULL AND deleted_at IS NULL`,
    )
      .bind(id, account, Date.now())
      .run();
    if (deleted.meta.changes === 1) return emptyResponse(204);
    // Confirmed or deleted just now: the next read says which.
  }
  return errorResponse(404, "not_found");
});
