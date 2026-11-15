import { env } from "cloudflare:workers";
import { expect } from "vitest";
import type { GroupState } from "../src/group-state";
import type { Env } from "../src/index";
import { hashed } from "../src/ledger";
import { issueSession } from "../src/session";
import { send } from "./requests";

/** Someone signed in with Google: the account the proxy names them by, and a session for it. */
export type Person = { account: string; session: string };

export async function person(): Promise<Person> {
  const account = await hashed(crypto.randomUUID(), env.HASH_SALT);
  return { account, session: (await issueSession(account, env, Date.now())).session };
}

/** A new session for the same account, as after signing in again. */
export async function signedInAgain(who: Person): Promise<Person> {
  return { account: who.account, session: (await issueSession(who.account, env, Date.now())).session };
}

export type Answer<T> = { status: number; headers: Headers; body: T };

/**
 * Sends [method] [path] as [who] (nobody for null), with [body] as JSON, and reads the answer as
 * the contract shapes it. Only the session is sent, never an install id or an app version.
 */
export async function api<T = Record<string, unknown>>(
  who: Person | null,
  method: string,
  path: string,
  body?: unknown,
  settings: Partial<Env> = {},
): Promise<Answer<T>> {
  const headers = new Headers({ "content-type": "application/json" });
  if (who !== null) headers.set("authorization", `Bearer ${who.session}`);
  const init: RequestInit = { method, headers, body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body) };
  const sent = await send(new Request(`https://proxy.test${path}`, init), settings);
  // The tests read answers in the shapes the contract gives them.
  return { status: sent.status, headers: sent.headers, body: (sent.body === "" ? null : JSON.parse(sent.body)) as T };
}

export const THB = { code: "THB", fractionDigits: 2 };
export const VND = { code: "VND", fractionDigits: 0 };

/** A group [owner] made, with its id and the owner's member. */
export type TestGroup = { id: string; owner: Person; ownerMemberId: string };

export async function newGroup(owner: Person, name = "Chiang Mai trip", currency = THB): Promise<TestGroup> {
  const id = crypto.randomUUID();
  const ownerMemberId = crypto.randomUUID();
  const made = await api<GroupState>(owner, "POST", "/v1/groups", { id, name, currency, memberId: ownerMemberId, memberName: "Owner" });
  if (made.status !== 201) throw new Error(`The group was not made: ${made.status}`);
  return { id, owner, ownerMemberId };
}

/** The token of a shared link, which travels in its fragment: `https://<host>/g/#<token>`. */
export function linkToken(url: string): string {
  return new URL(url).hash.slice(1);
}

/** An invite to [group] that [by] makes, and its token. */
export async function invite(group: TestGroup, by: Person = group.owner): Promise<string> {
  const made = await api<{ token: string }>(by, "POST", `/v1/groups/${group.id}/invites`);
  if (made.status !== 201) throw new Error(`The invite was not made: ${made.status}`);
  return made.body.token;
}

/** [joiner] joins [group] through an invite as a new member named [name]; the member's id. */
export async function join(group: TestGroup, joiner: Person, name = "Joiner"): Promise<string> {
  const memberId = crypto.randomUUID();
  const joined = await api(joiner, "POST", "/v1/invites/join", { token: await invite(group), memberId, name });
  if (joined.status !== 200) throw new Error(`The member did not join: ${joined.status}`);
  return memberId;
}

/** A placeholder named [name] that [by] adds to [group]; its id. */
export async function addPlaceholder(group: TestGroup, name: string, by: Person = group.owner): Promise<string> {
  const id = crypto.randomUUID();
  const added = await api(by, "POST", `/v1/groups/${group.id}/members`, { id, name });
  if (added.status !== 200) throw new Error(`The placeholder was not added: ${added.status}`);
  return id;
}

export async function stateOf(group: TestGroup, who: Person = group.owner): Promise<GroupState> {
  const read = await api<GroupState>(who, "GET", `/v1/groups/${group.id}`);
  if (read.status !== 200) throw new Error(`The group could not be read: ${read.status}`);
  return read.body;
}

/** A member as a group's state shows them, whenever they joined. */
export function memberView(id: string, name: string, hasAccount: boolean, isOwner = false) {
  return { id, name, hasAccount, isOwner, joinedAt: expect.any(Number) };
}

/** A bill as the app sends it: [totalMinor] shared and paid as [shares] and [paid] say. */
export function billBody(shares: Record<string, number>, paid: Record<string, number>, expectedRevision = 0, extra: Record<string, unknown> = {}) {
  const totalMinor = Object.values(shares).reduce((sum, amount) => sum + amount, 0);
  return {
    expectedRevision,
    document: { title: "Khao soi and drinks", lines: [{ name: "Khao soi", amountMinor: totalMinor }] },
    conversion: null,
    totalMinor,
    shares,
    paid,
    ...extra,
  };
}

/** [who] adds a new bill to [group]; its id. */
export async function addBill(group: TestGroup, who: Person, shares: Record<string, number>, paid: Record<string, number>): Promise<string> {
  const id = crypto.randomUUID();
  const put = await api(who, "PUT", `/v1/groups/${group.id}/bills/${id}`, billBody(shares, paid));
  if (put.status !== 200) throw new Error(`The bill was not added: ${put.status}`);
  return id;
}

/** Rows of a table, straight from the database. */
export async function rows<T = Record<string, unknown>>(sql: string, ...values: unknown[]): Promise<T[]> {
  return (await env.DB.prepare(sql).bind(...values).all<T>()).results;
}

/** The SHA-256 of [text] in hex, as the proxy keeps tokens. */
export async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
