import type { Env } from "./index";
import type { Currency } from "./input";
import { POLICY } from "./policy";
import { admitHourly } from "./rate-limit";
import { errorResponse, jsonResponse } from "./respond";

/** What a group's log records. Each entry names the member who acted and what it acted on. */
export type LogKind =
  | "group_created"
  | "member_added"
  | "member_joined"
  | "member_left"
  | "member_removed"
  | "member_renamed"
  | "claim_requested"
  | "claim_approved"
  | "claim_rejected"
  | "bill_added"
  | "bill_changed"
  | "bill_deleted"
  | "settlement_recorded";

/** The rate a bill was converted into the group's currency at, as the app sent it. */
export type Conversion = { rate: string; date: string; source: string };

/** Everything a member sees of a group. Accounts never appear in it; `hasAccount` is all it says of them. */
export type GroupState = {
  group: { id: string; name: string; currency: Currency; ownerMemberId: string; revision: number; createdAt: number };
  myMemberId: string | null;
  members: Array<{ id: string; name: string; hasAccount: boolean; isOwner: boolean; joinedAt: number }>;
  /** Claims that wait for the owner; empty for everyone else. */
  claims: Array<{ memberId: string; claimantName: string; requestedAt: number }>;
  bills: Array<{
    id: string;
    ownerMemberId: string;
    revision: number;
    updatedAt: number;
    document: unknown;
    conversion: Conversion | null;
    totalMinor: number;
    shares: Record<string, number>;
    paid: Record<string, number>;
  }>;
  settlements: Array<{
    id: string;
    fromMemberId: string;
    toMemberId: string;
    amountMinor: number;
    recordedByMemberId: string;
    requestId: string | null;
    createdAt: number;
  }>;
  /** The newest entries first. */
  log: Array<{ at: number; kind: LogKind; memberId: string; subjectId: string }>;
};

export type GroupRow = {
  id: string;
  name: string;
  currency_code: string;
  currency_digits: number;
  owner_member_id: string;
  revision: number;
  created_at: number;
};
export type MemberRow = { id: string; name: string; account: string | null; joined_at: number };
export type ClaimRow = { member_id: string; account: string; claimant_name: string; requested_at: number };
type BillRow = {
  id: string;
  owner_member_id: string;
  revision: number;
  updated_at: number;
  document: string;
  conversion: string | null;
  total_minor: number;
  shares: string;
  paid: string;
};
type SettlementRow = {
  id: string;
  from_member_id: string;
  to_member_id: string;
  amount_minor: number;
  recorded_by_member_id: string;
  request_id: string | null;
  created_at: number;
};
type LogRow = { at: number; kind: LogKind; member_id: string; subject_id: string };

/** A group as a change sees it before it decides: its row, its members and claims, and where the caller stands. */
export type Group = {
  row: GroupRow;
  members: MemberRow[];
  claims: ClaimRow[];
  /** The caller's member, when the caller is a member. */
  me: MemberRow | null;
  /** The caller's claim, while it waits. */
  myClaim: ClaimRow | null;
  isOwner: boolean;
};

/**
 * How many times a change reads the group again after another change came first, before it
 * answers 409. Each round lets one of the changes that came together land, so this covers bursts,
 * such as an app sending several bills at once.
 */
const WRITE_ATTEMPTS = 10;
/** The most a change waits before reading the group again, times the attempts made; the wait is random, to spread the changes out. */
const RETRY_STEP_MS = 20;

/** Statements whose results [groupFrom] reads: the group, its members and its claims. */
function groupStatements(db: D1Database, groupId: string): D1PreparedStatement[] {
  return [
    db.prepare("SELECT id, name, currency_code, currency_digits, owner_member_id, revision, created_at FROM groups WHERE id = ?1").bind(groupId),
    db.prepare("SELECT id, name, account, joined_at FROM members WHERE group_id = ?1 ORDER BY joined_at, id").bind(groupId),
    db.prepare("SELECT member_id, account, claimant_name, requested_at FROM claims WHERE group_id = ?1 ORDER BY requested_at, member_id").bind(groupId),
  ];
}

/** Statements whose results [stateFrom] reads: those of [groupStatements], then the live bills, the settlements and the log. */
export function stateStatements(db: D1Database, groupId: string): D1PreparedStatement[] {
  return [
    ...groupStatements(db, groupId),
    db
      .prepare(
        `SELECT id, owner_member_id, revision, updated_at, document, conversion, total_minor, shares, paid
         FROM group_bills WHERE group_id = ?1 AND deleted_at IS NULL ORDER BY created_at, id`,
      )
      .bind(groupId),
    db
      .prepare(
        `SELECT id, from_member_id, to_member_id, amount_minor, recorded_by_member_id, request_id, created_at
         FROM settlements WHERE group_id = ?1 ORDER BY created_at, id`,
      )
      .bind(groupId),
    db.prepare("SELECT at, kind, member_id, subject_id FROM group_log WHERE group_id = ?1 ORDER BY seq DESC LIMIT ?2").bind(groupId, POLICY.logEntries),
  ];
}

/** The rows of a result. They come from this Worker's own tables, whose shape `migrations/` fixes, so they are typed as such. */
function rowsOf<T>(result: D1Result | undefined): T[] {
  return (result?.results ?? []) as T[];
}

/** The group from the first three results of [groupStatements] or [stateStatements], or null when it does not exist. */
function groupFrom(results: D1Result[], account: string): Group | null {
  const row = rowsOf<GroupRow>(results[0])[0];
  if (row === undefined) return null;
  const members = rowsOf<MemberRow>(results[1]);
  const claims = rowsOf<ClaimRow>(results[2]);
  const me = members.find((member) => member.account === account) ?? null;
  const myClaim = claims.find((claim) => claim.account === account) ?? null;
  return { row, members, claims, me, myClaim, isOwner: me !== null && me.id === row.owner_member_id };
}

/** The group and the state [account] sees of it, from the results of [stateStatements]; null when it does not exist. */
export function stateFrom(results: D1Result[], account: string): { group: Group; state: GroupState } | null {
  const group = groupFrom(results, account);
  if (group === null) return null;
  const { row } = group;
  const state: GroupState = {
    group: {
      id: row.id,
      name: row.name,
      currency: { code: row.currency_code, fractionDigits: row.currency_digits },
      ownerMemberId: row.owner_member_id,
      revision: row.revision,
      createdAt: row.created_at,
    },
    myMemberId: group.me?.id ?? null,
    members: group.members.map((member) => ({
      id: member.id,
      name: member.name,
      hasAccount: member.account !== null,
      isOwner: member.id === row.owner_member_id,
      joinedAt: member.joined_at,
    })),
    claims: group.isOwner
      ? group.claims.map((claim) => ({ memberId: claim.member_id, claimantName: claim.claimant_name, requestedAt: claim.requested_at }))
      : [],
    bills: rowsOf<BillRow>(results[3]).map((bill) => ({
      id: bill.id,
      ownerMemberId: bill.owner_member_id,
      revision: bill.revision,
      updatedAt: bill.updated_at,
      document: JSON.parse(bill.document) as unknown,
      conversion: bill.conversion === null ? null : (JSON.parse(bill.conversion) as Conversion),
      totalMinor: bill.total_minor,
      shares: JSON.parse(bill.shares) as Record<string, number>,
      paid: JSON.parse(bill.paid) as Record<string, number>,
    })),
    settlements: rowsOf<SettlementRow>(results[4]).map((settlement) => ({
      id: settlement.id,
      fromMemberId: settlement.from_member_id,
      toMemberId: settlement.to_member_id,
      amountMinor: settlement.amount_minor,
      recordedByMemberId: settlement.recorded_by_member_id,
      requestId: settlement.request_id,
      createdAt: settlement.created_at,
    })),
    log: rowsOf<LogRow>(results[5]).map((entry) => ({ at: entry.at, kind: entry.kind, memberId: entry.member_id, subjectId: entry.subject_id })),
  };
  return { group, state };
}

/** The group named [groupId] as [account] stands in it, or null when there is no such group. */
export async function readGroup(db: D1Database, groupId: string, account: string): Promise<Group | null> {
  return groupFrom(await db.batch(groupStatements(db, groupId)), account);
}

/** The group and its whole state, read together, or null when there is no such group. */
export async function readState(db: D1Database, groupId: string, account: string): Promise<{ group: Group; state: GroupState } | null> {
  return stateFrom(await db.batch(stateStatements(db, groupId)), account);
}

/** Whether the caller is a member of [group], which is what reading or changing it takes. */
export function isMember(group: Group): group is Group & { me: MemberRow } {
  return group.me !== null;
}

/** The answer to a caller who is not a member: 403 `pending` while their claim waits, else 403 `forbidden`. */
export function outsider(group: Group): Response {
  return errorResponse(403, group.myClaim !== null ? "pending" : "forbidden");
}

/** What a change decides, once it has seen the group as it is. */
export type Plan =
  /** Answer without writing anything, such as a refusal. */
  | { kind: "reply"; response: Response }
  /** Nothing needs to change, as when a request is sent again: answer from the state as it is. */
  | { kind: "keep"; answer: (state: GroupState) => Response }
  /**
   * Write [statements], and answer from the state they leave, which is null when they deleted
   * the group or [readState] is false. [after] runs once they have landed, to send notifications.
   */
  | {
      kind: "write";
      statements: D1PreparedStatement[];
      answer: (state: GroupState | null) => Response | Promise<Response>;
      after?: () => void;
      readState: boolean;
    };

/** How a write goes on once its statements have landed. */
export type WriteOptions = {
  /** Runs once the statements have landed, to send notifications. */
  after?: () => void;
  /** False for a change whose answer does not need the group's state, so the batch does not read it. */
  readState?: boolean;
};

export function reply(response: Response): Plan {
  return { kind: "reply", response };
}

export function keep(answer: (state: GroupState) => Response): Plan {
  return { kind: "keep", answer };
}

export function write(
  statements: D1PreparedStatement[],
  answer: (state: GroupState | null) => Response | Promise<Response>,
  options: WriteOptions = {},
): Plan {
  return { kind: "write", statements, answer, after: options.after, readState: options.readState ?? true };
}

/** Answers with the group's state, or 404 when the change deleted the group. */
export function stateAnswer(status = 200): (state: GroupState | null) => Response {
  return (state) => (state === null ? errorResponse(404, "not_found") : jsonResponse(status, state));
}

/**
 * Runs one change to a group. [plan] decides from the group as it is now, and its statements go
 * in one batch behind an update of the group's revision to the one read plus one. A trigger
 * refuses that update when another change came first, which rolls the whole batch back; the
 * group is then read again and [plan] decides anew. So every change is decided on the state it
 * changes, permissions included, and moves the revision on by exactly one. The state the batch
 * leaves is read in the same batch, unless the change does not need it.
 *
 * Every change counts once against the caller's hourly limit of group writes, whatever it then
 * decides: 429 past it. 404 `not_found` when the group does not exist; 409 `conflict` when other
 * changes keep coming first [WRITE_ATTEMPTS] times, which only a group changed many times a second
 * would see.
 */
export async function changeGroup(env: Env, groupId: string, account: string, plan: (group: Group) => Plan | Promise<Plan>): Promise<Response> {
  const db = env.DB;
  const refused = await groupWriteRefusal(db, account);
  if (refused !== null) return refused;
  for (let attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
    if (attempt > 1) await new Promise((resolve) => setTimeout(resolve, Math.random() * RETRY_STEP_MS * attempt));
    const group = await readGroup(db, groupId, account);
    if (group === null) return errorResponse(404, "not_found");
    const decided = await plan(group);
    if (decided.kind === "reply") return decided.response;
    if (decided.kind === "keep") {
      const current = await readState(db, groupId, account);
      return current === null ? errorResponse(404, "not_found") : decided.answer(current.state);
    }
    const reads = decided.readState ? stateStatements(db, groupId) : [];
    let results: D1Result[];
    try {
      results = await db.batch([
        db.prepare("UPDATE groups SET revision = ?2 WHERE id = ?1").bind(groupId, group.row.revision + 1),
        ...decided.statements,
        ...reads,
      ]);
    } catch (error) {
      if (lostRace(error)) continue;
      throw error;
    }
    // No row to update: the group was deleted after it was read, and the next read says so.
    if (results[0].meta.changes !== 1) continue;
    decided.after?.();
    const state = reads.length === 0 ? null : (stateFrom(results.slice(-reads.length), account)?.state ?? null);
    return await decided.answer(state);
  }
  console.error(JSON.stringify({ event: "group_change_contended" }));
  return errorResponse(409, "conflict");
}

/**
 * Counts one change to a group by [account] against [POLICY.groupWritesPerHour], over all its
 * groups; the 429 to answer with once past it, else null.
 */
export async function groupWriteRefusal(db: D1Database, account: string): Promise<Response | null> {
  const gate = await admitHourly(db, account, "group_write", POLICY.groupWritesPerHour, Date.now());
  return gate.ok ? null : errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
}

/**
 * Whether a batch failed because the group changed after it was read: its revision moved on, or
 * it was deleted, so that a row added under it no longer has a group.
 */
function lostRace(error: unknown): boolean {
  return error instanceof Error && (error.message.includes("stale_group") || error.message.includes("FOREIGN KEY constraint failed"));
}

/** A statement that adds an entry to a group's log. */
export function logStatement(db: D1Database, groupId: string, at: number, kind: LogKind, memberId: string, subjectId: string): D1PreparedStatement {
  return db
    .prepare("INSERT INTO group_log (group_id, at, kind, member_id, subject_id) VALUES (?1, ?2, ?3, ?4, ?5)")
    .bind(groupId, at, kind, memberId, subjectId);
}

/** Groups [account] belongs to or waits to join, which [POLICY.maxGroupsPerAccount] limits. */
export async function groupCountOf(db: D1Database, account: string): Promise<number> {
  const row = await db
    .prepare("SELECT (SELECT count(*) FROM members WHERE account = ?1) + (SELECT count(*) FROM claims WHERE account = ?1) AS groups")
    .bind(account)
    .first<{ groups: number }>();
  return row?.groups ?? 0;
}
