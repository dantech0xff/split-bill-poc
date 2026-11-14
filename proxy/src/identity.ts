import { uuidOf } from "./input";
import { POLICY } from "./policy";
import type { Allowance } from "./quota-book";

/** The header that carries the random id an install of the app made for itself. */
export const INSTALL_HEADER = "X-SplitVibe-Install";

/**
 * Who is calling, as far as allowances are concerned. [name] names the caller's QuotaBook and
 * spend ledger; it never holds personal data.
 */
export type Caller = { kind: "install"; name: string; allowance: Allowance };

/** An install that is not signed in, or null when the request carries no valid install id. */
export function installCaller(request: Request): Caller | null {
  const id = uuidOf(request.headers.get(INSTALL_HEADER));
  if (id === null) return null;
  return { kind: "install", name: `install:${id}`, allowance: { kind: "trial", limit: POLICY.trialCalls } };
}
