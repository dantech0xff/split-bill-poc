import {
  changeGroup,
  groupCountOf,
  groupWriteRefusal,
  isMember,
  keep,
  logStatement,
  outsider,
  readState,
  reply,
  stateAnswer,
  stateFrom,
  stateStatements,
  write,
} from "./group-state";
import { signedIn } from "./identity";
import { type Currency, currencyOf, idOf, inputOf, textOf, uuidOf } from "./input";
import { POLICY } from "./policy";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";

type GroupListRow = {
  id: string;
  name: string;
  currency_code: string;
  currency_digits: number;
  revision: number;
  member_id: string | null;
  created_at: number;
};

/**
 * `GET /v1/groups`: the groups the caller is a member of, and those where their claim waits
 * (status "pending", no member yet), oldest first.
 */
export const listGroupsRoute = signedIn(async ({ env, account }) => {
  const { results } = await env.DB.prepare(
    `SELECT g.id, g.name, g.currency_code, g.currency_digits, g.revision, m.id AS member_id, g.created_at
       FROM members m JOIN groups g ON g.id = m.group_id WHERE m.account = ?1
     UNION ALL
     SELECT g.id, g.name, g.currency_code, g.currency_digits, g.revision, NULL AS member_id, g.created_at
       FROM claims c JOIN groups g ON g.id = c.group_id WHERE c.account = ?1
     ORDER BY created_at, id`,
  )
    .bind(account)
    .all<GroupListRow>();
  const groups = results.map((row) => ({
    id: row.id,
    name: row.name,
    currency: { code: row.currency_code, fractionDigits: row.currency_digits },
    revision: row.revision,
    myMemberId: row.member_id,
    status: row.member_id === null ? "pending" : "member",
  }));
  return jsonResponse(200, { groups });
});

type NewGroup = { id: string; name: string; currency: Currency; memberId: string; memberName: string };

function newGroupOf(body: Record<string, unknown>): NewGroup | null {
  const id = idOf(body.id);
  const name = textOf(body.name);
  const currency = currencyOf(body.currency);
  const memberId = idOf(body.memberId);
  const memberName = textOf(body.memberName);
  if (id === null || name === null || currency === null || memberId === null || memberName === null) return null;
  return { id, name, currency, memberId, memberName };
}

/**
 * `POST /v1/groups`: makes a group whose owner and first member is the caller. The same group
 * sent again by a member of it answers as the first time did, with the group as it is now; a
 * group id someone else already uses is 409.
 */
export const createGroupRoute = signedIn(async ({ request, env, account }) => {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, newGroupOf);
  if (!input.ok) return input.response;
  const { id, name, currency, memberId, memberName } = input.value;
  const db = env.DB;
  const refused = await groupWriteRefusal(db, account);
  if (refused !== null) return refused;
  for (let attempt = 1; attempt <= 2; attempt++) {
    const existing = await readState(db, id, account);
    if (existing !== null) return isMember(existing.group) ? jsonResponse(201, existing.state) : errorResponse(409, "conflict");
    if ((await groupCountOf(db, account)) >= POLICY.maxGroupsPerAccount) return errorResponse(413, "too_large");
    const now = Date.now();
    try {
      const results = await db.batch([
        db
          .prepare("INSERT INTO groups (id, name, currency_code, currency_digits, owner_member_id, revision, created_at) VALUES (?1, ?2, ?3, ?4, ?5, 1, ?6)")
          .bind(id, name, currency.code, currency.fractionDigits, memberId, now),
        db.prepare("INSERT INTO members (group_id, id, name, account, joined_at) VALUES (?1, ?2, ?3, ?4, ?5)").bind(id, memberId, memberName, account, now),
        logStatement(db, id, now, "group_created", memberId, id),
        ...stateStatements(db, id),
      ]);
      const created = stateFrom(results.slice(3), account);
      if (created !== null) return jsonResponse(201, created.state);
    } catch (error) {
      // A request with the same id made the group first; the next read says whose it is.
      if (!(error instanceof Error && error.message.includes("UNIQUE constraint failed"))) throw error;
    }
  }
  return errorResponse(409, "conflict");
});

/** `GET /v1/groups/{gid}`: the group's state, for its members only. */
export const readGroupRoute = signedIn(async ({ env, params, account }) => {
  const groupId = uuidOf(params[0]);
  const current = groupId === null ? null : await readState(env.DB, groupId, account);
  if (current === null) return errorResponse(404, "not_found");
  if (!isMember(current.group)) return outsider(current.group);
  return jsonResponse(200, current.state);
});

/** `PATCH /v1/groups/{gid}` with `{"name"}`: renames the group; only its owner may. */
export const renameGroupRoute = signedIn(async ({ request, env, params, account }) => {
  const groupId = uuidOf(params[0]);
  if (groupId === null) return errorResponse(404, "not_found");
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const name = textOf(body.name);
    return name === null ? null : { name };
  });
  if (!input.ok) return input.response;
  const { name } = input.value;
  return changeGroup(env, groupId, account, (group) => {
    if (!isMember(group)) return reply(outsider(group));
    if (!group.isOwner) return reply(errorResponse(403, "forbidden"));
    if (group.row.name === name) return keep(stateAnswer());
    return write([env.DB.prepare("UPDATE groups SET name = ?2 WHERE id = ?1").bind(groupId, name)], stateAnswer());
  });
});

/**
 * `POST /v1/groups/{gid}/members` with `{"id","name"}`: any member adds a placeholder, a member
 * without an account, for someone who has not joined. A member id the group already has changes
 * nothing, so that the app can send the same member again after a lost answer.
 */
export const addMemberRoute = signedIn(async ({ request, env, params, account }) => {
  const groupId = uuidOf(params[0]);
  if (groupId === null) return errorResponse(404, "not_found");
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const id = idOf(body.id);
    const name = textOf(body.name);
    return id === null || name === null ? null : { id, name };
  });
  if (!input.ok) return input.response;
  const { id, name } = input.value;
  const db = env.DB;
  return changeGroup(env, groupId, account, (group) => {
    if (!isMember(group)) return reply(outsider(group));
    if (group.members.some((member) => member.id === id)) return keep(stateAnswer());
    if (group.members.length >= POLICY.maxMembers) return reply(errorResponse(413, "too_large"));
    const now = Date.now();
    return write(
      [
        db.prepare("INSERT INTO members (group_id, id, name, account, joined_at) VALUES (?1, ?2, ?3, NULL, ?4)").bind(groupId, id, name, now),
        logStatement(db, groupId, now, "member_added", group.me.id, id),
      ],
      stateAnswer(),
    );
  });
});

/** `PATCH /v1/groups/{gid}/members/{mid}` with `{"name"}`: renames a member; that member themself or the owner may. */
export const renameMemberRoute = signedIn(async ({ request, env, params, account }) => {
  const groupId = uuidOf(params[0]);
  const memberId = uuidOf(params[1]);
  if (groupId === null || memberId === null) return errorResponse(404, "not_found");
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const name = textOf(body.name);
    return name === null ? null : { name };
  });
  if (!input.ok) return input.response;
  const { name } = input.value;
  const db = env.DB;
  return changeGroup(env, groupId, account, (group) => {
    if (!isMember(group)) return reply(outsider(group));
    const member = group.members.find((each) => each.id === memberId);
    if (member === undefined) return reply(errorResponse(404, "not_found"));
    if (member.id !== group.me.id && !group.isOwner) return reply(errorResponse(403, "forbidden"));
    if (member.name === name) return keep(stateAnswer());
    const now = Date.now();
    return write(
      [
        db.prepare("UPDATE members SET name = ?3 WHERE group_id = ?1 AND id = ?2").bind(groupId, memberId, name),
        logStatement(db, groupId, now, "member_renamed", group.me.id, memberId),
      ],
      stateAnswer(),
    );
  });
});

/**
 * `POST /v1/groups/{gid}/leave`: the caller's member stays as a placeholder, with its name and
 * every amount, but without the caller's account. An owner who leaves hands the group to the
 * member with an account who joined first; when nobody with an account is left, the group and
 * everything in it is deleted. Answers 204.
 */
export const leaveGroupRoute = signedIn(async ({ env, params, account }) => {
  const groupId = uuidOf(params[0]);
  if (groupId === null) return errorResponse(404, "not_found");
  const db = env.DB;
  const left = () => emptyResponse(204);
  return changeGroup(env, groupId, account, (group) => {
    if (!isMember(group)) return reply(outsider(group));
    const me = group.me;
    const now = Date.now();
    const leaving = [
      db.prepare("UPDATE members SET account = NULL WHERE group_id = ?1 AND id = ?2").bind(groupId, me.id),
      logStatement(db, groupId, now, "member_left", me.id, me.id),
    ];
    // The answer is 204, so the batch need not read the group's state.
    if (!group.isOwner) return write(leaving, left, { readState: false });
    // Members are in the order they joined.
    const heir = group.members.find((member) => member.account !== null && member.id !== me.id);
    if (heir === undefined) return write(deleteGroupStatements(db, groupId), left, { readState: false });
    return write([db.prepare("UPDATE groups SET owner_member_id = ?2 WHERE id = ?1").bind(groupId, heir.id), ...leaving], left, { readState: false });
  });
});

/**
 * `POST /v1/groups/{gid}/members/{mid}/remove`: the owner takes a member's account off the group.
 * The member stays as a placeholder, exactly as when they leave: its name and every amount stay,
 * and its account and any claim of that account in the group go. Payment requests already made
 * stay as they are. Only a member with an account, and not the owner's own member, can be
 * removed; anything else is 409. Answers with the group's state.
 */
export const removeMemberRoute = signedIn(async ({ env, params, account }) => {
  const groupId = uuidOf(params[0]);
  const memberId = uuidOf(params[1]);
  if (groupId === null || memberId === null) return errorResponse(404, "not_found");
  const db = env.DB;
  return changeGroup(env, groupId, account, (group) => {
    if (!isMember(group)) return reply(outsider(group));
    if (!group.isOwner) return reply(errorResponse(403, "forbidden"));
    const member = group.members.find((each) => each.id === memberId);
    if (member === undefined) return reply(errorResponse(404, "not_found"));
    if (member.id === group.me.id || member.account === null) return reply(errorResponse(409, "conflict"));
    const now = Date.now();
    return write(
      [
        db.prepare("UPDATE members SET account = NULL WHERE group_id = ?1 AND id = ?2").bind(groupId, memberId),
        db.prepare("DELETE FROM claims WHERE group_id = ?1 AND account = ?2").bind(groupId, member.account),
        logStatement(db, groupId, now, "member_removed", group.me.id, memberId),
      ],
      stateAnswer(),
    );
  });
});

/**
 * Deletes a group and, through the schema's cascades, its members, claims, invites, bills,
 * settlements and log. Payment requests made in it stay between their two people, without the
 * group, its members or its bill.
 */
export function deleteGroupStatements(db: D1Database, groupId: string): D1PreparedStatement[] {
  return [
    db.prepare("UPDATE requests SET group_id = NULL, to_member_id = NULL, creditor_member_id = NULL, bill_id = NULL WHERE group_id = ?1").bind(groupId),
    db.prepare("DELETE FROM groups WHERE id = ?1").bind(groupId),
  ];
}
