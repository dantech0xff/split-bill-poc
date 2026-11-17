import type { Env } from "./index";
import { uuidOf } from "./input";
import type { AbuseLedger } from "./ledger";
import { POLICY } from "./policy";
import { type Allowance, PER_CALL, type QuotaBook } from "./quota-book";
import { errorResponse } from "./respond";
import { accountOfSession } from "./session";

/** The header that carries the random id an install of the app made for itself. */
export const INSTALL_HEADER = "X-SplitVibe-Install";

/**
 * The allowance of each kind of caller without Pro. A Google account's `QuotaBook` gives it Pro's
 * calls instead while its plan is Pro.
 */
export const ALLOWANCES = {
  trial: { kind: "trial", limit: POLICY.trialCalls },
  monthly: { kind: "monthly", limit: POLICY.monthlyCalls },
} as const satisfies Record<string, Allowance>;

/** What one call takes of the caller's allowance, in hundredths of a call: a bill read takes a whole call. */
export const WEIGHTS = {
  billAnalysis: PER_CALL,
  splitRevision: weightInHundredths(POLICY.revisionWeight),
} as const;

/**
 * [calls], the weight of a route in calls, in hundredths of a call. It must be a whole number of
 * hundredths from one hundredth to a whole call: anything else would be rounded without a word,
 * 0.125 to 0.13 and 0.004 to a whole call, so it throws instead, which stops the Worker from
 * loading and every test from running.
 */
export function weightInHundredths(calls: number): number {
  const hundredths = Math.round(calls * PER_CALL);
  // A weight of two decimals, times 100, lands a rounding error away from its whole number.
  if (hundredths >= 1 && hundredths <= PER_CALL && Math.abs(calls * PER_CALL - hundredths) < 1e-9) return hundredths;
  throw new Error(`A weight must be a whole number of hundredths of a call from 0.01 to 1, not ${calls}`);
}

/**
 * Who is calling, as far as allowances are concerned: an install that is not signed in, or a
 * Google account, with its [account], which is also its purchaser at RevenueCat. [name] names the
 * caller's QuotaBook and spend ledger; it holds no personal data.
 */
export type Caller =
  | { kind: "install"; name: string; allowance: Allowance }
  | { kind: "google"; account: string; name: string; allowance: Allowance };

/**
 * The caller of [request]. A session, when the request carries one, decides: one that does not
 * check out is refused rather than read as the install, so that a signed-in person is never moved
 * onto the trial without knowing.
 */
export async function callerOf(request: Request, env: Env): Promise<{ ok: true; caller: Caller } | { ok: false; code: "install" | "session" }> {
  if (request.headers.has("authorization")) {
    const account = await sessionAccountOf(request, env);
    if (account === null) return { ok: false, code: "session" };
    return { ok: true, caller: { kind: "google", account, name: accountName(account), allowance: ALLOWANCES.monthly } };
  }
  const install = uuidOf(request.headers.get(INSTALL_HEADER));
  if (install === null) return { ok: false, code: "install" };
  return { ok: true, caller: { kind: "install", name: `install:${install}`, allowance: ALLOWANCES.trial } };
}

/** The account the session of [request] stands for, or null when it carries none that checks out. */
export async function sessionAccountOf(request: Request, env: Env): Promise<string | null> {
  const bearer = /^Bearer (\S+)$/.exec(request.headers.get("authorization")?.trim() ?? "");
  return bearer === null ? null : await accountOfSession(bearer[1], env);
}

/** A request on shared data: who sent it, and the parameters of its path in order. */
export type Call = { request: Request; env: Env; ctx: ExecutionContext; params: string[]; account: string };

/**
 * A route on shared data. Shared data belongs to Google accounts, so the route needs a session
 * and no install id; without a session that checks out it answers 401 `session`.
 */
export function signedIn(route: (call: Call) => Promise<Response>) {
  return async (request: Request, env: Env, ctx: ExecutionContext, params: string[]): Promise<Response> => {
    const account = await sessionAccountOf(request, env);
    if (account === null) return errorResponse(401, "session");
    return route({ request, env, ctx, params, account });
  };
}

/** The name of a Google account's QuotaBook and spend ledger. */
export function accountName(account: string): string {
  return `google:${account}`;
}

export function quotaBook(env: Env, name: string): DurableObjectStub<QuotaBook> {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(name));
}

export function ledger(env: Env, name: string): DurableObjectStub<AbuseLedger> {
  return env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(name));
}
