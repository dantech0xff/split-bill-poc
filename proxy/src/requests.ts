import { changeGroup, type Group, isMember, outsider, type Plan, readGroup, reply, write } from "./group-state";
import { type Call, signedIn } from "./identity";
import type { Env } from "./index";
import {
  byteLength,
  type Currency,
  currencyOf,
  idOf,
  inputOf,
  isRecord,
  moneyOf,
  optionalPaymentOf,
  optionalUuidOf,
  type PaymentPayload,
  textOf,
  TOO_LARGE,
  uuidOf,
} from "./input";
import { bodyTokenOf, linkUrl, requestToken, tokenHash } from "./links";
import { POLICY } from "./policy";
import { notify } from "./push";
import { addressOf, admitHourly, hourlyRefusal } from "./rate-limit";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";

/** How often a debtor may be reminded, in days; 0 is never. */
const REMIND_DAYS: readonly number[] = [0, 1, 3, 7];

const COLUMNS = `id, creditor, debtor, debtor_bound_at, amount_minor, currency_code, currency_digits, payer_name, creditor_name, title,
  method, payment, group_id, to_member_id, creditor_member_id, bill_id, created_at, reported_paid_at, confirmed_paid_at, remind_every_days, deleted_at`;

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
  /** The PaymentPayload for the request's web page, as JSON. */
  payment: string | null;
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
  /** The creditor's member in the group, so the app can show who in the group asks, whatever name they typed. */
  creditorMemberId: string | null;
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
    creditorMemberId: row.creditor_member_id,
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
  payment: string | null;
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
  const payment = optionalPaymentOf(body.payment);
  if (id === null || amountMinor === null || amountMinor === 0 || currency === null) return null;
  if (payerName === null || creditorName === null || title === null || payment === undefined) return null;
  if (groupId === undefined || toMemberId === undefined || billId === undefined || !isRecord(body.method)) return null;
  // A member is named exactly when a group is.
  if ((groupId === null) !== (toMemberId === null)) return null;
  if (typeof remindEveryDays !== "number" || !REMIND_DAYS.includes(remindEveryDays)) return null;
  const method = JSON.stringify(body.method);
  if (byteLength(method) > POLICY.maxMethodBytes) return TOO_LARGE;
  return {
    id,
    amountMinor,
    currency,
    payerName,
    creditorName,
    title: title === "" ? null : title,
    method,
    payment: payment === null ? null : JSON.stringify(payment),
    groupId,
    toMemberId,
    billId,
    remindEveryDays,
  };
}

/** The answer to creating a request, whose creditor is the caller: 201 with the request as they see it, and the link to share. */
async function created(call: Call, row: RequestRow): Promise<Response> {
  return jsonResponse(201, { request: viewOf(row, "creditor"), url: linkUrl(call.request, "r", await requestToken(call.env, call.account, row.id)) });
}

/**
 * `POST /v1/requests`: the caller asks someone to pay them, with the one payment method they
 * chose, and optionally the `payment` their app built from it for the request's web page. In a
 * group, the payer is a member other than the caller, the currency is the group's,
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
           method, payment, group_id, to_member_id, creditor_member_id, bill_id, link_hash, created_at, remind_every_days)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19)
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
        wanted.payment,
        wanted.groupId,
        wanted.toMemberId,
        creditorMemberId,
        wanted.billId,
        await tokenHash(await requestToken(env, account, wanted.id)),
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

/**
 * The request a link's token leads to, or the answer: 404 for a link never made (or that no link
 * could have), 410 for one whose request was deleted.
 */
async function linkedRequest(db: D1Database, token: string | null): Promise<{ ok: true; row: RequestRow } | { ok: false; response: Response }> {
  const row = token === null ? null : await db.prepare(`SELECT ${COLUMNS} FROM requests WHERE link_hash = ?1`).bind(await tokenHash(token)).first<RequestRow>();
  if (row === null) return { ok: false, response: errorResponse(404, "not_found") };
  if (row.deleted_at !== null) return { ok: false, response: errorResponse(410, "gone") };
  return { ok: true, row };
}

/**
 * `POST /v1/request-links/open` with `{"token"}`: the request a link carries, as the caller will
 * see it once they accept it: as its debtor, bound. Its creditor sees it as theirs. When another
 * account already accepted it, the answer is 409, and nothing of the request is shown. The token
 * comes in the body so that no URL, and so no trace, ever holds it.
 */
export const openRequestLinkRoute = signedIn(async ({ request, env, account }) => {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, bodyTokenOf);
  if (!input.ok) return input.response;
  const linked = await linkedRequest(env.DB, input.value.token);
  if (!linked.ok) return linked.response;
  const row = linked.row;
  if (row.creditor === account) return viewResponse(row, "creditor");
  if (row.debtor_bound_at !== null && row.debtor !== account) return errorResponse(409, "conflict");
  return jsonResponse(200, { ...viewOf(row, "debtor"), debtorBound: true });
});

/**
 * `POST /v1/request-links/view` with `{"token"}`: the request a link carries, for its web page,
 * which needs no session: who asks whom for how much and for what, whether it is `open`,
 * `reported` or `confirmed`, and how to pay it, as the creditor's app built it (null when it sent
 * none). Nothing names an account. 404 and 410 as for `open`; a link that works counts as a read
 * of the IP.
 */
export async function viewRequestLinkRoute(request: Request, env: Env): Promise<Response> {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, bodyTokenOf);
  if (!input.ok) return input.response;
  const address = await addressOf(request, env);
  if (!address.ok) return address.response;
  const linked = await linkedRequest(env.DB, input.value.token);
  if (!linked.ok) return linked.response;
  const refused = await hourlyRefusal(env.DB, address.name, "web_read", POLICY.webReadsPerHour);
  if (refused !== null) return refused;
  const row = linked.row;
  const status = row.confirmed_paid_at !== null ? "confirmed" : row.reported_paid_at !== null ? "reported" : "open";
  return jsonResponse(200, {
    request: {
      creditorName: row.creditor_name,
      payerName: row.payer_name,
      amountMinor: row.amount_minor,
      currency: { code: row.currency_code, fractionDigits: row.currency_digits },
      title: row.title,
      status,
      // The Worker's own JSON, checked when the request was made.
      payment: row.payment === null ? null : (JSON.parse(row.payment) as PaymentPayload),
      createdAt: row.created_at,
    },
  });
}

/**
 * `POST /v1/request-links/accept` with `{"token"}`: the caller becomes the request's debtor, when
 * nobody is yet; accepting again changes nothing. 409 when another account is the debtor, 403 for
 * the creditor, who cannot owe themself.
 */
export const acceptRequestLinkRoute = signedIn(async ({ request, env, account }) => {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, bodyTokenOf);
  if (!input.ok) return input.response;
  for (let attempt = 1; attempt <= 2; attempt++) {
    const linked = await linkedRequest(env.DB, input.value.token);
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
 * from the payer's member to the creditor's, so the group's balances change exactly once; in a
 * group that holds [POLICY.maxSettlements] already, it is 413.
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
 *
 * Withdrawing a request does not go through the group, so it can land between that read and the
 * batch. Each statement of the batch therefore acts on the request as the batch finds it: the
 * confirmation lands only on a request still open, in the group and not withdrawn, and the
 * settlement and its log entry only when the confirmation did. The answer reads the request
 * again to tell which happened; the debtor is notified only of a confirmation that landed.
 */
async function confirmInGroup(env: Env, group: Group, id: string, account: string, tell: (row: RequestRow) => void): Promise<Plan> {
  const db = env.DB;
  const [requestResult, countResult] = await db.batch([
    db.prepare(`SELECT ${COLUMNS} FROM requests WHERE id = ?1`).bind(id),
    db.prepare("SELECT count(*) AS settlements FROM settlements WHERE group_id = ?1").bind(group.row.id),
  ]);
  // Rows of this Worker's own tables, shaped by migrations/.
  const fresh = (requestResult.results[0] as RequestRow | undefined) ?? null;
  const settlements = (countResult.results[0] as { settlements: number } | undefined)?.settlements ?? 0;
  if (fresh === null || fresh.deleted_at !== null || fresh.creditor !== account || fresh.group_id !== group.row.id) {
    return reply(errorResponse(404, "not_found"));
  }
  if (fresh.confirmed_paid_at !== null) return reply(viewResponse(fresh, "creditor"));
  if (fresh.to_member_id === null || fresh.creditor_member_id === null) throw new Error("A group request without its members");
  // The settlement it records would take the group past its limit.
  if (settlements >= POLICY.maxSettlements) return reply(errorResponse(413, "too_large"));
  const now = Date.now();
  const settlementId = crypto.randomUUID();
  // The request as this confirmation leaves it, and as nothing else does: open until now, then confirmed at [now].
  const confirmedHere = "FROM requests WHERE id = ?1 AND confirmed_paid_at = ?2 AND deleted_at IS NULL AND group_id = ?3";
  return write(
    [
      db
        .prepare("UPDATE requests SET confirmed_paid_at = ?2 WHERE id = ?1 AND confirmed_paid_at IS NULL AND deleted_at IS NULL AND group_id = ?3")
        .bind(id, now, group.row.id),
      db
        .prepare(
          `INSERT INTO settlements (group_id, id, from_member_id, to_member_id, amount_minor, recorded_by_member_id, request_id, created_at)
           SELECT group_id, ?4, to_member_id, creditor_member_id, amount_minor, creditor_member_id, id, confirmed_paid_at ${confirmedHere}`,
        )
        .bind(id, now, group.row.id, settlementId),
      db
        .prepare(
          `INSERT INTO group_log (group_id, at, kind, member_id, subject_id)
           SELECT group_id, confirmed_paid_at, 'settlement_recorded', creditor_member_id, ?4 ${confirmedHere}`,
        )
        .bind(id, now, group.row.id, settlementId),
    ],
    async () => {
      const landed = await requestRow(db, id);
      if (landed === null || landed.deleted_at !== null || landed.confirmed_paid_at === null) return errorResponse(404, "not_found");
      if (landed.confirmed_paid_at === now) tell(landed);
      return viewResponse(landed, "creditor");
    },
    { readState: false },
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
 * is. What it held, its payment for the web page included, is emptied at once; the row itself
 * stays for a while, so that its link answers 410. Deleting it again answers 204 as well.
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
      `UPDATE requests SET deleted_at = ?3, debtor = NULL, payer_name = '', creditor_name = '', title = NULL, method = '{}', payment = NULL,
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
