import { forgetSharedData } from "./account-data";
import { ALLOWANCES, accountName, callerOf, ledger, quotaBook, sessionAccountOf } from "./identity";
import type { Env } from "./index";
import { readJson } from "./input";
import { count } from "./metrics";
import { POLICY } from "./policy";
import type { Usage } from "./quota-book";
import { hourlyRefusal } from "./rate-limit";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";
import { deleteCustomer, refreshPlan, revenueCatKey } from "./revenuecat";
import { googleAccountOf, issueSession, signInReady } from "./session";
import { aiEnabled } from "./switches";

const MAX_ID_TOKEN_CHARS = 8 * 1024;

/** Why a Google ID token was not taken: the status and the error code to answer with. */
type IdTokenRefusal = { status: 400 | 401 | 503; code: "input" | "id_token" | "sign_in_off" };

/**
 * The account of the Google ID token in the body `{"idToken": …}` of [request], checked as signing
 * in checks it: 503 `sign_in_off` until the owner set the app's client ID and the signing key; 400
 * `input` for a body without a token; 401 `id_token` for a token Google did not sign for this
 * app's client ID, or that has run out; 503 `sign_in_off` again when Google's keys cannot be had.
 */
async function googleAccountIn(request: Request, env: Env): Promise<{ ok: true; account: string } | ({ ok: false } & IdTokenRefusal)> {
  if (!signInReady(env)) return { ok: false, status: 503, code: "sign_in_off" };
  const body = await readJson(request, 2 * MAX_ID_TOKEN_CHARS);
  const idToken = typeof body === "object" && body !== null ? (body as Record<string, unknown>).idToken : undefined;
  if (typeof idToken !== "string" || idToken === "" || idToken.length > MAX_ID_TOKEN_CHARS) return { ok: false, status: 400, code: "input" };
  const checked = await googleAccountOf(idToken, env);
  if (!checked.ok) return checked.reason === "invalid" ? { ok: false, status: 401, code: "id_token" } : { ok: false, status: 503, code: "sign_in_off" };
  return { ok: true, account: checked.account };
}

/**
 * `POST /v1/session` with `{"idToken": …}`: trades the ID token Google gave the app at sign-in for
 * a session the proxy signs, and says how many calls the account has left this month, with the
 * account's purchaser: the id the app gives RevenueCat for the account's purchases, left out while
 * it cannot be recorded, as [callsAndPurchaser] says. Each sign-in is counted as `sign_in`, with
 * `ok` or the error code.
 */
export async function sessionRoute(request: Request, env: Env): Promise<Response> {
  const checked = await googleAccountIn(request, env);
  if (!checked.ok) {
    count(env, "sign_in", checked.code);
    return errorResponse(checked.status, checked.code);
  }
  const now = Date.now();
  const { session, expiresAt } = await issueSession(checked.account, env, now);
  const answer = jsonResponse(200, { session, expiresAt, ...(await callsAndPurchaser(env, checked.account, now)) });
  count(env, "sign_in", "ok");
  return answer;
}

/**
 * `GET /v1/usage`: the calls the caller has left, and whether bills can be read with AI at all; for
 * a signed-in caller, also its purchaser, as `POST /v1/session` gives it.
 */
export async function usageRoute(request: Request, env: Env): Promise<Response> {
  const who = await callerOf(request, env);
  if (!who.ok) return errorResponse(401, who.code);
  const { caller } = who;
  const now = Date.now();
  if (caller.kind === "install") {
    return jsonResponse(200, { usage: await quotaBook(env, caller.name).usage(now, caller.allowance), aiEnabled: aiEnabled(env) });
  }
  return jsonResponse(200, { ...(await callsAndPurchaser(env, caller.account, now)), aiEnabled: aiEnabled(env) });
}

/**
 * The calls [account] has left, and the account as its purchaser once its QuotaBook has recorded
 * that the id went out, as [QuotaBook.issuePurchaser] does. For a few minutes after the deploy that
 * brought Pro, the QuotaBook may still run the code from before it, which has no such method: the
 * answer then has the calls left alone, from [QuotaBook.usage], and no purchaser, which the app gets
 * on a later call. So an id never goes out without its record. Only the event's name is logged.
 */
async function callsAndPurchaser(env: Env, account: string, now: number): Promise<{ usage: Usage; purchaser?: string }> {
  const book = quotaBook(env, accountName(account));
  try {
    return { usage: await book.issuePurchaser(now, ALLOWANCES.monthly), purchaser: account };
  } catch {
    console.error(JSON.stringify({ event: "purchaser_not_recorded" }));
    return { usage: await book.usage(now, ALLOWANCES.monthly) };
  }
}

/**
 * `POST /v1/plan`: asks RevenueCat for the subscription of the signed-in account, keeps the plan it
 * gives, and answers `{purchaser, usage, aiEnabled}`. The app calls it when it opens the Pro screen
 * and once a purchase, a restore or a change of plan is done, so as not to wait for RevenueCat's
 * webhook. Its body, empty or `{}`, is not read: the app has no say in its plan.
 *
 * It answers 503 `not_configured` without RevenueCat's key, 429 `rate_limited` past
 * [POLICY.planRefreshesPerHour] for the account, and 502 `subscription_unavailable` when RevenueCat
 * gives no plan that can be read, which leaves the plan kept as it was. Each refresh of a signed-in
 * account is counted as `plan_refresh`, with `ok` or the error code.
 */
export async function planRoute(request: Request, env: Env): Promise<Response> {
  const account = await sessionAccountOf(request, env);
  if (account === null) return errorResponse(401, "session");
  const refused = (status: number, code: string, response: Response = errorResponse(status, code)): Response => {
    count(env, "plan_refresh", code);
    return response;
  };
  const key = revenueCatKey(env);
  if (key === null) return refused(503, "not_configured");
  const limited = await hourlyRefusal(env.DB, account, "plan_refresh", POLICY.planRefreshesPerHour);
  if (limited !== null) return refused(429, "rate_limited", limited);
  const book = quotaBook(env, accountName(account));
  // The answer hands the id out, so it is recorded as handed out before RevenueCat is asked: the
  // read may create the customer, whose events the webhook then follows. A QuotaBook that cannot
  // record it fails the request, answered 500, so RevenueCat is never asked about an id without
  // its record.
  await book.issuePurchaser(Date.now(), ALLOWANCES.monthly);
  if (!(await refreshPlan(env, key, account))) return refused(502, "subscription_unavailable");
  const usage = await book.usage(Date.now(), ALLOWANCES.monthly);
  count(env, "plan_refresh", "ok");
  return jsonResponse(200, { purchaser: account, usage, aiEnabled: aiEnabled(env) });
}

/**
 * `DELETE /v1/account`: deletes the signed-in account, as [deleteAccount] does. The app then
 * forgets the session.
 */
export async function accountRoute(request: Request, env: Env): Promise<Response> {
  const account = await sessionAccountOf(request, env);
  if (account === null) return errorResponse(401, "session");
  return deleteAccount(env, account);
}

/**
 * `POST /v1/account/delete-with-google` with `{"idToken": …}`: deletes the account of a Google ID
 * token, as `DELETE /v1/account` deletes the account of a session, for the page `/delete-account`,
 * where a person who no longer has the app signs in with Google. The token is checked as
 * `POST /v1/session` checks it, with its errors, and no session is ever issued.
 */
export async function deleteWithGoogleRoute(request: Request, env: Env): Promise<Response> {
  const checked = await googleAccountIn(request, env);
  if (!checked.ok) return errorResponse(checked.status, checked.code);
  return deleteAccount(env, checked.account);
}

/**
 * Deletes what the proxy holds about [account], answering 204: first its customer at RevenueCat,
 * when RevenueCat is set up; then its part in the shared data, which `forgetSharedData` takes out
 * without changing anyone else's balances; then its QuotaBook, but for the calls it used this
 * calendar month, which [QuotaBook.forget] keeps until the month ends so that deleting the account
 * and signing in again starts no new allowance, and its spend counter. When RevenueCat does not
 * take the deletion, the answer is 502 `subscription_unavailable` and nothing else is deleted, so
 * that it can be asked again. A subscription on Google Play goes on: the app and the page say so
 * before they delete.
 *
 * An account may ask for its deletion [POLICY.accountDeletionsPerHour] times an hour, by either
 * route, counted before RevenueCat is asked; past that the answer is 429 `rate_limited` and nothing
 * is deleted. The count outlives the deletion, as every hourly count of the account does until the
 * daily run, because a session outlives it too.
 */
async function deleteAccount(env: Env, account: string): Promise<Response> {
  const refused = await hourlyRefusal(env.DB, account, "account_delete", POLICY.accountDeletionsPerHour);
  if (refused !== null) return refused;
  const key = revenueCatKey(env);
  if (key !== null && !(await deleteCustomer(key, account))) return errorResponse(502, "subscription_unavailable");
  const now = Date.now();
  await forgetSharedData(env.DB, account, now);
  const name = accountName(account);
  await Promise.all([quotaBook(env, name).forget(now), ledger(env, name).forget()]);
  return emptyResponse(204);
}
