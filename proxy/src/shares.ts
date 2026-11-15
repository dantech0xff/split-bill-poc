import { type Call, signedIn } from "./identity";
import { countOf, idOf, inputOf, TOO_LARGE, uuidOf } from "./input";
import { linkUrl, shareToken, tokenHash } from "./links";
import { POLICY } from "./policy";
import { admitHourly } from "./rate-limit";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";
import {
  amountsOf,
  billProjectionOf,
  guardStatement,
  isGone,
  keepItemsStatement,
  ownerView,
  type ProjectedBill,
  publicView,
  readShare,
  type Share,
  SHARE_WRITE_ATTEMPTS,
  shareFrom,
  shareStatements,
  tell,
  writeShare,
} from "./share-state";

/** What a change of a link decides once it has seen the link as it is. */
type Decision =
  /** Answer without writing anything, such as a refusal. */
  | { kind: "reply"; response: Response }
  /** Nothing needs to change, as when a request is sent again: answer with the link as it is. */
  | { kind: "keep" }
  /** Write [statements], the first of them the update that guards the change (see [guardStatement]). */
  | { kind: "write"; statements: D1PreparedStatement[] };

const KEEP: Decision = { kind: "keep" };

function reply(response: Response): Decision {
  return { kind: "reply", response };
}

function write(...statements: D1PreparedStatement[]): Decision {
  return { kind: "write", statements };
}

/** How [changeShare] answers: with the link's address or not, and what it says to an account that does not own the link. */
type Answering = { withUrl: boolean; notOwned: () => Response };

const NO_URL: Answering = { withUrl: false, notOwned: () => errorResponse(404, "not_found") };

/** The answer to a link's owner: `{"share": ShareView}`, and the link's `url` for the routes that hand it out. */
async function ownerAnswer(call: Call, share: Share, status: number, withUrl: boolean): Promise<Response> {
  const body: Record<string, unknown> = { share: ownerView(share) };
  if (withUrl) body.url = linkUrl(call.request, "s", await shareToken(call.env, share.row.owner, share.row.id, share.row.created_at));
  return jsonResponse(status, body);
}

/** Counts one change of the caller's links against [POLICY.shareWritesPerHour]; the 429 to answer with once past it, else null. */
async function shareWriteRefusal(call: Call): Promise<Response | null> {
  const gate = await admitHourly(call.env.DB, call.account, "share_write", POLICY.shareWritesPerHour, Date.now());
  return gate.ok ? null : errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
}

/**
 * Runs one change of the caller's link [shareId]. [decide] sees the link as it is now; what it
 * writes lands only while the link still allows it, or the link is read again and [decide] runs
 * anew. Every page that has the link open hears of a change that landed. 404 for a link that does
 * not exist, or that the caller does not own (unless [answering] says otherwise); 410 for one
 * revoked or expired; 409 when the link keeps changing under the change [SHARE_WRITE_ATTEMPTS]
 * times.
 */
async function changeShare(
  call: Call,
  shareId: string,
  decide: (share: Share, now: number) => Decision,
  answering: Answering = NO_URL,
): Promise<Response> {
  const { env, ctx, account } = call;
  for (let attempt = 1; attempt <= SHARE_WRITE_ATTEMPTS; attempt++) {
    const share = await readShare(env.DB, "id", shareId);
    if (share === null) return errorResponse(404, "not_found");
    if (share.row.owner !== account) return answering.notOwned();
    const now = Date.now();
    if (isGone(share.row, now)) return errorResponse(410, "gone");
    const decided = decide(share, now);
    if (decided.kind === "reply") return decided.response;
    if (decided.kind === "keep") return ownerAnswer(call, share, 200, answering.withUrl);
    const written = await writeShare(env.DB, shareId, decided.statements);
    if (written === null) continue;
    tell(env, ctx, shareId, { type: "share", share: publicView(written) });
    return ownerAnswer(call, written, 200, answering.withUrl);
  }
  console.error(JSON.stringify({ event: "share_change_contended" }));
  return errorResponse(409, "conflict");
}

type ShareInput = { billId: string; billRevision: number; projected: ProjectedBill };

function shareInputOf(body: Record<string, unknown>): ShareInput | typeof TOO_LARGE | null {
  const billId = idOf(body.billId);
  const billRevision = countOf(body.billRevision);
  const projected = billProjectionOf(body.bill);
  if (projected === TOO_LARGE) return TOO_LARGE;
  return billId === null || billRevision === null || projected === null ? null : { billId, billRevision, projected };
}

/** Makes link [shareId] for the caller, open and without claims; null when a link with that id already exists. */
async function makeShare({ env, account }: Call, shareId: string, wanted: ShareInput): Promise<Share | null> {
  const db = env.DB;
  const now = Date.now();
  const hash = await tokenHash(await shareToken(env, account, shareId, now));
  const results = await db.batch([
    db
      .prepare(
        `INSERT INTO shares (id, owner, bill_id, token_hash, status, revision, bill_revision, bill, amounts, created_at, expires_at)
         VALUES (?1, ?2, ?3, ?4, 'open', 1, ?5, ?6, NULL, ?7, ?8)
         ON CONFLICT DO NOTHING`,
      )
      .bind(shareId, account, wanted.billId, hash, wanted.billRevision, wanted.projected.json, now, now + POLICY.shareMs),
    ...shareStatements(db, "id", shareId),
  ]);
  return results[0].meta.changes === 1 ? shareFrom(results.slice(1)) : null;
}

/**
 * `PUT /v1/shares/{id}` with `{"billId","billRevision","bill"}`: makes a bill link, answering 201,
 * or shows a changed bill on it, answering 200; both with `{"share","url"}`. The link stays with
 * its bill (another `billId` is 400) and never shows an older copy of it (a lower `billRevision` is
 * 409); the same copy sent again changes nothing. Claims keep only the items the bill still has,
 * and a `locked` link goes back to `locking` without its amounts. Another account's id is 409; a
 * revoked or expired link is 410. An account makes [POLICY.sharesPerHour] links an hour, and
 * every other call counts against [POLICY.shareWritesPerHour].
 */
export const putShareRoute = signedIn(async (call) => {
  const { request, env, params, account } = call;
  const shareId = uuidOf(params[0]);
  if (shareId === null) return errorResponse(400, "input");
  const input = await inputOf(request, POLICY.maxShareBodyBytes, shareInputOf);
  if (!input.ok) return input.response;
  const wanted = input.value;
  const db = env.DB;
  const existing = await readShare(db, "id", shareId);
  if (existing === null) {
    const gate = await admitHourly(db, account, "share", POLICY.sharesPerHour, Date.now());
    if (!gate.ok) return errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
    const made = await makeShare(call, shareId, wanted);
    if (made !== null) return ownerAnswer(call, made, 201, true);
    // A request that came together with this one made the link first: this one changes it.
  } else if (existing.row.owner !== account) {
    return errorResponse(409, "conflict");
  } else {
    const refused = await shareWriteRefusal(call);
    if (refused !== null) return refused;
  }
  const { billId, billRevision, projected } = wanted;
  const answering: Answering = { withUrl: true, notOwned: () => errorResponse(409, "conflict") };
  return changeShare(
    call,
    shareId,
    (share, now) => {
      if (share.row.bill_id !== billId) return reply(errorResponse(400, "input"));
      if (billRevision < share.row.bill_revision) return reply(errorResponse(409, "conflict"));
      if (billRevision === share.row.bill_revision && projected.json === share.row.bill) return KEEP;
      return write(
        guardStatement(
          db,
          shareId,
          now,
          "owner = ?3 AND bill_revision <= ?4",
          "bill_revision = ?4, bill = ?5, amounts = NULL, status = CASE status WHEN 'locked' THEN 'locking' ELSE status END",
          [account, billRevision, projected.json],
        ),
        keepItemsStatement(db, shareId, projected.itemIds),
      );
    },
    answering,
  );
});

/** `GET /v1/shares/{id}`: the caller's link, with its claims, and its `url`. */
export const readShareRoute = signedIn(async (call) => {
  const shareId = uuidOf(call.params[0]);
  const share = shareId === null ? null : await readShare(call.env.DB, "id", shareId);
  if (share === null || share.row.owner !== call.account) return errorResponse(404, "not_found");
  if (isGone(share.row, Date.now())) return errorResponse(410, "gone");
  return ownerAnswer(call, share, 200, true);
});

/** Counts one change against [POLICY.shareWritesPerHour], then runs it as [changeShare] does. */
async function countedChange(call: Call, shareId: string, decide: (share: Share, now: number) => Decision): Promise<Response> {
  const refused = await shareWriteRefusal(call);
  if (refused !== null) return refused;
  return changeShare(call, shareId, decide);
}

/**
 * `POST /v1/shares/{id}/lock`: the link stops taking claims, `open` becoming `locking`, and the
 * answer holds every claim written before: the change and the read of the claims are one batch,
 * and a claim lands only while the link is open. A link already `locking` or `locked` is answered
 * as it is.
 */
export const lockShareRoute = signedIn(async (call) => {
  const shareId = uuidOf(call.params[0]);
  if (shareId === null) return errorResponse(404, "not_found");
  return countedChange(call, shareId, (share, now) =>
    share.row.status !== "open"
      ? KEEP
      : write(guardStatement(call.env.DB, shareId, now, "owner = ?3 AND status = 'open'", "status = 'locking'", [call.account])),
  );
});

/** `POST /v1/shares/{id}/reopen`: a `locking` or `locked` link takes claims again, without its amounts; an open one is answered as it is. */
export const reopenShareRoute = signedIn(async (call) => {
  const shareId = uuidOf(call.params[0]);
  if (shareId === null) return errorResponse(404, "not_found");
  return countedChange(call, shareId, (share, now) =>
    share.row.status === "open"
      ? KEEP
      : write(guardStatement(call.env.DB, shareId, now, "owner = ?3 AND status IN ('locking', 'locked')", "status = 'open', amounts = NULL", [call.account])),
  );
});

type Settled = { billRevision: number; projected: ProjectedBill; amounts: string };

function settledOf(body: Record<string, unknown>): Settled | typeof TOO_LARGE | null {
  const billRevision = countOf(body.billRevision);
  const projected = billProjectionOf(body.bill);
  if (projected === TOO_LARGE) return TOO_LARGE;
  const amounts = projected === null ? null : amountsOf(body.amounts, projected.bill);
  return billRevision === null || projected === null || amounts === null ? null : { billRevision, projected, amounts: JSON.stringify(amounts) };
}

/**
 * `POST /v1/shares/{id}/finalize` with `{"billRevision","bill","amounts"}`: the owner settles the
 * split of a `locking` or `locked` link (409 while it is open), which becomes `locked` with what
 * each participant owes and how to pay it. `amounts` names every participant of `bill` and nobody
 * else, and comes to its total (else 400); `bill` is shown as with `PUT`, and `billRevision`
 * follows the same rule. The same settlement sent again changes nothing.
 */
export const finalizeShareRoute = signedIn(async (call) => {
  const shareId = uuidOf(call.params[0]);
  if (shareId === null) return errorResponse(404, "not_found");
  const input = await inputOf(call.request, POLICY.maxShareBodyBytes, settledOf);
  if (!input.ok) return input.response;
  const { billRevision, projected, amounts } = input.value;
  const db = call.env.DB;
  return countedChange(call, shareId, (share, now) => {
    if (share.row.status === "open") return reply(errorResponse(409, "conflict"));
    if (billRevision < share.row.bill_revision) return reply(errorResponse(409, "conflict"));
    const same = share.row.bill_revision === billRevision && share.row.bill === projected.json && share.row.amounts === amounts;
    if (share.row.status === "locked" && same) return KEEP;
    return write(
      guardStatement(
        db,
        shareId,
        now,
        "owner = ?3 AND status IN ('locking', 'locked') AND bill_revision <= ?4",
        "status = 'locked', bill_revision = ?4, bill = ?5, amounts = ?6",
        [call.account, billRevision, projected.json, amounts],
      ),
      keepItemsStatement(db, shareId, projected.itemIds),
    );
  });
});

/**
 * `DELETE /v1/shares/{id}/claims/{claimId}`: the owner takes a claim off a link that is `open` or
 * `locking` (409 `locked` once it is locked). The browser that made it then has no claim, and its
 * key opens none. A claim the link does not have, as when this is sent again, changes nothing.
 */
export const removeClaimRoute = signedIn(async (call) => {
  const shareId = uuidOf(call.params[0]);
  const claimId = uuidOf(call.params[1]);
  if (shareId === null || claimId === null) return errorResponse(404, "not_found");
  const db = call.env.DB;
  return countedChange(call, shareId, (share, now) => {
    if (share.row.status === "locked") return reply(errorResponse(409, "locked"));
    if (!share.claims.some((claim) => claim.id === claimId)) return KEEP;
    return write(
      guardStatement(
        db,
        shareId,
        now,
        "owner = ?3 AND status IN ('open', 'locking') AND EXISTS (SELECT 1 FROM share_claims WHERE share_id = ?1 AND id = ?4)",
        "",
        [call.account, claimId],
      ),
      db.prepare("DELETE FROM share_claims WHERE share_id = ?1 AND id = ?2").bind(shareId, claimId),
    );
  });
});

/**
 * `DELETE /v1/shares/{id}`: the owner revokes the link. Its claims are deleted and its bill and
 * amounts emptied at once, and every page that has it open hears that it is gone; its token then
 * answers 410 until the daily run deletes it. Revoking again answers 204 as well; an expired link
 * can be revoked too. It counts against no limit, since it is how an owner stops abuse.
 */
export const revokeShareRoute = signedIn(async ({ env, ctx, params, account }) => {
  const shareId = uuidOf(params[0]);
  const row = shareId === null ? null : await env.DB.prepare("SELECT owner FROM shares WHERE id = ?1").bind(shareId).first<{ owner: string }>();
  if (shareId === null || row === null || row.owner !== account) return errorResponse(404, "not_found");
  const db = env.DB;
  await db.batch([
    db.prepare("UPDATE shares SET revoked_at = coalesce(revoked_at, ?3), bill = NULL, amounts = NULL WHERE id = ?1 AND owner = ?2").bind(shareId, account, Date.now()),
    db.prepare("DELETE FROM share_claims WHERE share_id = ?1 AND EXISTS (SELECT 1 FROM shares WHERE id = ?1 AND owner = ?2)").bind(shareId, account),
  ]);
  tell(env, ctx, shareId, { type: "gone" });
  return emptyResponse(204);
});
