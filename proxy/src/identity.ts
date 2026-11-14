import type { Env } from "./index";
import { uuidOf } from "./input";
import type { AbuseLedger } from "./ledger";
import { POLICY } from "./policy";
import type { Allowance, QuotaBook } from "./quota-book";
import { accountOfSession } from "./session";

/** The header that carries the random id an install of the app made for itself. */
export const INSTALL_HEADER = "X-SplitVibe-Install";

export const ALLOWANCES = {
  trial: { kind: "trial", limit: POLICY.trialCalls },
  monthly: { kind: "monthly", limit: POLICY.monthlyCalls },
} as const satisfies Record<string, Allowance>;

/**
 * Who is calling, as far as allowances are concerned: an install that is not signed in, or a
 * Google account. [name] names the caller's QuotaBook and spend ledger; it holds no personal data.
 */
export type Caller = { kind: "install" | "google"; name: string; allowance: Allowance };

/**
 * The caller of [request]. A session, when the request carries one, decides: one that does not
 * check out is refused rather than read as the install, so that a signed-in person is never moved
 * onto the trial without knowing.
 */
export async function callerOf(request: Request, env: Env): Promise<{ ok: true; caller: Caller } | { ok: false; code: "install" | "session" }> {
  const authorization = request.headers.get("authorization");
  if (authorization !== null) {
    const bearer = /^Bearer (\S+)$/.exec(authorization.trim());
    const account = bearer === null ? null : await accountOfSession(bearer[1], env);
    if (account === null) return { ok: false, code: "session" };
    return { ok: true, caller: { kind: "google", name: `google:${account}`, allowance: ALLOWANCES.monthly } };
  }
  const install = uuidOf(request.headers.get(INSTALL_HEADER));
  if (install === null) return { ok: false, code: "install" };
  return { ok: true, caller: { kind: "install", name: `install:${install}`, allowance: ALLOWANCES.trial } };
}

export function quotaBook(env: Env, name: string): DurableObjectStub<QuotaBook> {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(name));
}

export function ledger(env: Env, name: string): DurableObjectStub<AbuseLedger> {
  return env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(name));
}
