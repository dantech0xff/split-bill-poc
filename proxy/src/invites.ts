import { changeGroup, groupCountOf, isMember, keep, logStatement, outsider, readGroup, reply, stateAnswer, write } from "./group-state";
import { signedIn } from "./identity";
import type { Env } from "./index";
import { idOf, inputOf, textOf, uuidOf } from "./input";
import { bodyTokenOf, linkUrl, randomToken, tokenHash } from "./links";
import { POLICY } from "./policy";
import { notify } from "./push";
import { addressOf, admitHourly, hourlyRefusal } from "./rate-limit";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";

/**
 * `POST /v1/groups/{gid}/invites`: any member makes an invite link, which works for 14 days.
 * Answers 201 `{"token","url","expiresAt"}`; the token is shown only here, since only its hash is
 * kept. An account may make 20 invites an hour, over all its groups.
 */
export const createInviteRoute = signedIn(async ({ request, env, params, account }) => {
  const groupId = uuidOf(params[0]);
  const group = groupId === null ? null : await readGroup(env.DB, groupId, account);
  if (groupId === null || group === null) return errorResponse(404, "not_found");
  if (!isMember(group)) return outsider(group);
  const now = Date.now();
  const gate = await admitHourly(env.DB, account, "invite", POLICY.invitesPerHour, now);
  if (!gate.ok) return errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
  const token = randomToken();
  const expiresAt = now + POLICY.inviteMs;
  // Made only while the caller is still a member, should they have left since the read above.
  const made = await env.DB.prepare(
    `INSERT INTO invites (token_hash, group_id, created_at, expires_at)
     SELECT ?1, ?2, ?3, ?4 WHERE EXISTS (SELECT 1 FROM members WHERE group_id = ?2 AND account = ?5)`,
  )
    .bind(await tokenHash(token), groupId, now, expiresAt, account)
    .run();
  if (made.meta.changes !== 1) return errorResponse(403, "forbidden");
  return jsonResponse(201, { token, url: linkUrl(request, "g", token), expiresAt });
});

/** `DELETE /v1/groups/{gid}/invites`: the owner revokes every invite of the group at once. Answers 204. */
export const revokeInvitesRoute = signedIn(async ({ env, params, account }) => {
  const groupId = uuidOf(params[0]);
  const group = groupId === null ? null : await readGroup(env.DB, groupId, account);
  if (groupId === null || group === null) return errorResponse(404, "not_found");
  if (!isMember(group)) return outsider(group);
  if (!group.isOwner) return errorResponse(403, "forbidden");
  await revokeInvitesStatement(env.DB, groupId, Date.now()).run();
  return emptyResponse(204);
});

/**
 * Revokes every invite of [groupId] that still works, as of [now]: as the owner asks, and whenever
 * a member is taken out or leaves, so that no invite made before can bring them back. The members
 * who stay make a new one. Deleting an account does the same for every group it was in, in the
 * batch of `forgetSharedData`.
 */
export function revokeInvitesStatement(db: D1Database, groupId: string, now: number): D1PreparedStatement {
  return db.prepare("UPDATE invites SET revoked_at = ?2 WHERE group_id = ?1 AND revoked_at IS NULL").bind(groupId, now);
}

/**
 * The group an invite token leads to, or the answer: 404 for a token never made (or that no
 * invite could have), 410 for one expired or revoked.
 */
async function invitedGroup(db: D1Database, token: string | null): Promise<{ ok: true; groupId: string } | { ok: false; response: Response }> {
  const invite =
    token === null
      ? null
      : await db
          .prepare("SELECT group_id, expires_at, revoked_at FROM invites WHERE token_hash = ?1")
          .bind(await tokenHash(token))
          .first<{ group_id: string; expires_at: number; revoked_at: number | null }>();
  if (invite === null) return { ok: false, response: errorResponse(404, "not_found") };
  if (invite.revoked_at !== null || Date.now() >= invite.expires_at) return { ok: false, response: errorResponse(410, "gone") };
  return { ok: true, groupId: invite.group_id };
}

/**
 * `POST /v1/invites/open` with `{"token"}`: what an invite leads to, for someone deciding to join:
 * the group's name and currency, its placeholders, and whether the caller is already a member.
 * The token comes in the body so that no URL, and so no trace, ever holds it.
 */
export const openInviteRoute = signedIn(async ({ request, env, account }) => {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, bodyTokenOf);
  if (!input.ok) return input.response;
  const invited = await invitedGroup(env.DB, input.value.token);
  if (!invited.ok) return invited.response;
  const group = await readGroup(env.DB, invited.groupId, account);
  if (group === null) return errorResponse(404, "not_found");
  return jsonResponse(200, {
    group: { id: group.row.id, name: group.row.name, currency: { code: group.row.currency_code, fractionDigits: group.row.currency_digits } },
    placeholders: group.members.filter((member) => member.account === null).map((member) => ({ id: member.id, name: member.name })),
    alreadyMember: isMember(group),
  });
});

/**
 * `POST /v1/invites/preview` with `{"token"}`: the name and currency of the group an invite leads
 * to, for the invite's web page, which needs no session; nothing of its members. 404 and 410 as
 * for `open`; an invite that works counts as a read of the IP.
 */
export async function previewInviteRoute(request: Request, env: Env): Promise<Response> {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, bodyTokenOf);
  if (!input.ok) return input.response;
  const address = await addressOf(request, env);
  if (!address.ok) return address.response;
  const invited = await invitedGroup(env.DB, input.value.token);
  if (!invited.ok) return invited.response;
  const refused = await hourlyRefusal(env.DB, address.name, "web_read", POLICY.webReadsPerHour);
  if (refused !== null) return refused;
  const group = await env.DB.prepare("SELECT name, currency_code, currency_digits FROM groups WHERE id = ?1")
    .bind(invited.groupId)
    .first<{ name: string; currency_code: string; currency_digits: number }>();
  if (group === null) return errorResponse(404, "not_found");
  return jsonResponse(200, { group: { name: group.name, currency: { code: group.currency_code, fractionDigits: group.currency_digits } } });
}

type Joining = { token: string | null; kind: "join" | "claim"; memberId: string; name: string };

/**
 * `{"token","memberId","name"}` to join as a new member, or `{"token","claimMemberId","name"}` to
 * ask for a placeholder; never both.
 */
function joiningOf(body: Record<string, unknown>): Joining | null {
  const token = bodyTokenOf(body);
  const name = textOf(body.name);
  const joinAs = body.memberId ?? null;
  const claim = body.claimMemberId ?? null;
  if (token === null || name === null || (joinAs === null) === (claim === null)) return null;
  const memberId = idOf(joinAs ?? claim);
  return memberId === null ? null : { token: token.token, kind: joinAs !== null ? "join" : "claim", memberId, name };
}

const waiting = () => jsonResponse(202, { status: "pending" });

/**
 * `POST /v1/invites/join` with the invite's `token`. With `"memberId"` and `"name"` the caller
 * joins as a new member and gets the group's state. With `"claimMemberId"` and `"name"` they ask
 * to become that placeholder: the claim waits for the owner, who is notified, and the answer is 202
 * `{"status":"pending"}`. A member gets the group's state whatever they send, and so never
 * claims. A placeholder can have one claim waiting, and an account one claim per group; a claim
 * on a member with an account, or on a placeholder someone else claimed, is 409.
 */
export const joinGroupRoute = signedIn(async ({ request, env, ctx, account }) => {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, joiningOf);
  if (!input.ok) return input.response;
  const { token, kind, memberId, name } = input.value;
  const invited = await invitedGroup(env.DB, token);
  if (!invited.ok) return invited.response;
  const groupId = invited.groupId;
  const db = env.DB;
  return changeGroup(env, groupId, account, async (group) => {
    if (isMember(group)) return keep(stateAnswer());
    const target = group.members.find((member) => member.id === memberId);
    const now = Date.now();
    if (kind === "join") {
      if (target !== undefined) return reply(errorResponse(409, "conflict"));
      if (group.members.length >= POLICY.maxMembers) return reply(errorResponse(413, "too_large"));
      // A claim of the caller's in this group already counts the group.
      if (group.myClaim === null && (await groupCountOf(db, account)) >= POLICY.maxGroupsPerAccount) return reply(errorResponse(413, "too_large"));
      return write(
        [
          // A claim the caller left waiting is dropped: they joined as themselves instead.
          db.prepare("DELETE FROM claims WHERE group_id = ?1 AND account = ?2").bind(groupId, account),
          db.prepare("INSERT INTO members (group_id, id, name, account, joined_at) VALUES (?1, ?2, ?3, ?4, ?5)").bind(groupId, memberId, name, account, now),
          logStatement(db, groupId, now, "member_joined", memberId, memberId),
        ],
        stateAnswer(),
      );
    }
    if (target === undefined) return reply(errorResponse(404, "not_found"));
    if (target.account !== null) return reply(errorResponse(409, "conflict"));
    // The same claim again changes nothing; a claim on another placeholder waits for this one.
    if (group.myClaim !== null) return reply(group.myClaim.member_id === memberId ? waiting() : errorResponse(409, "conflict"));
    if (group.claims.some((claim) => claim.member_id === memberId)) return reply(errorResponse(409, "conflict"));
    if ((await groupCountOf(db, account)) >= POLICY.maxGroupsPerAccount) return reply(errorResponse(413, "too_large"));
    const owner = group.members.find((member) => member.id === group.row.owner_member_id);
    return write(
      [
        db
          .prepare("INSERT INTO claims (group_id, member_id, account, claimant_name, requested_at) VALUES (?1, ?2, ?3, ?4, ?5)")
          .bind(groupId, memberId, account, name, now),
        logStatement(db, groupId, now, "claim_requested", memberId, memberId),
      ],
      waiting,
      { after: () => ctx.waitUntil(notify(env, owner?.account ?? null, { type: "claim", groupId })) },
    );
  });
});

/**
 * `POST /v1/groups/{gid}/claims/{mid}/approve` and `/reject`: the owner decides a claim. Approving
 * gives the placeholder the claimant's account, and nothing else changes: its id, its name and
 * its amounts stay. Rejecting drops the claim. Both answer with the group's state.
 */
function claimDecisionRoute(decision: "approve" | "reject") {
  return signedIn(async ({ env, params, account }) => {
    const groupId = uuidOf(params[0]);
    const memberId = uuidOf(params[1]);
    if (groupId === null || memberId === null) return errorResponse(404, "not_found");
    const db = env.DB;
    return changeGroup(env, groupId, account, (group) => {
      if (!isMember(group)) return reply(outsider(group));
      if (!group.isOwner) return reply(errorResponse(403, "forbidden"));
      const claim = group.claims.find((each) => each.member_id === memberId);
      if (claim === undefined) return reply(errorResponse(404, "not_found"));
      const now = Date.now();
      const statements = [db.prepare("DELETE FROM claims WHERE group_id = ?1 AND member_id = ?2").bind(groupId, memberId)];
      if (decision === "approve") {
        statements.push(
          db.prepare("UPDATE members SET account = ?3 WHERE group_id = ?1 AND id = ?2 AND account IS NULL").bind(groupId, memberId, claim.account),
        );
      }
      statements.push(logStatement(db, groupId, now, decision === "approve" ? "claim_approved" : "claim_rejected", group.me.id, memberId));
      return write(statements, stateAnswer());
    });
  });
}

export const approveClaimRoute = claimDecisionRoute("approve");
export const rejectClaimRoute = claimDecisionRoute("reject");
