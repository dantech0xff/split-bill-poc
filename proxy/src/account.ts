import { forgetSharedData } from "./account-data";
import { ALLOWANCES, accountName, callerOf, ledger, quotaBook, sessionAccountOf } from "./identity";
import type { Env } from "./index";
import { readJson } from "./input";
import { POLICY } from "./policy";
import { hourlyRefusal } from "./rate-limit";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";
import { deleteCustomer, refreshPlan, revenueCatKey } from "./revenuecat";
import { googleAccountOf, issueSession, signInReady } from "./session";
import { aiEnabled } from "./switches";

const MAX_ID_TOKEN_CHARS = 8 * 1024;

/**
 * `POST /v1/session` with `{"idToken": …}`: trades the ID token Google gave the app at sign-in for
 * a session the proxy signs, and says how many calls the account has left this month, with the
 * account's purchaser: the id the app gives RevenueCat for the account's purchases.
 */
export async function sessionRoute(request: Request, env: Env): Promise<Response> {
  if (!signInReady(env)) return errorResponse(503, "sign_in_off");
  const body = await readJson(request, 2 * MAX_ID_TOKEN_CHARS);
  const idToken = typeof body === "object" && body !== null ? (body as Record<string, unknown>).idToken : undefined;
  if (typeof idToken !== "string" || idToken === "" || idToken.length > MAX_ID_TOKEN_CHARS) return errorResponse(400, "input");

  const checked = await googleAccountOf(idToken, env);
  if (!checked.ok) return checked.reason === "invalid" ? errorResponse(401, "id_token") : errorResponse(503, "sign_in_off");
  const now = Date.now();
  const { session, expiresAt } = await issueSession(checked.account, env, now);
  const usage = await quotaBook(env, accountName(checked.account)).issuePurchaser(now, ALLOWANCES.monthly);
  return jsonResponse(200, { session, expiresAt, usage, purchaser: checked.account });
}

/**
 * `GET /v1/usage`: the calls the caller has left, and whether bills can be read with AI at all; for
 * a signed-in caller, also its purchaser, as `POST /v1/session` gives it.
 */
export async function usageRoute(request: Request, env: Env): Promise<Response> {
  const who = await callerOf(request, env);
  if (!who.ok) return errorResponse(401, who.code);
  const { caller } = who;
  const book = quotaBook(env, caller.name);
  const now = Date.now();
  if (caller.kind === "install") return jsonResponse(200, { usage: await book.usage(now, caller.allowance), aiEnabled: aiEnabled(env) });
  const usage = await book.issuePurchaser(now, caller.allowance);
  return jsonResponse(200, { usage, aiEnabled: aiEnabled(env), purchaser: caller.account });
}

/**
 * `POST /v1/plan`: asks RevenueCat for the subscription of the signed-in account, keeps the plan it
 * gives, and answers `{purchaser, usage, aiEnabled}`. The app calls it when it opens the Pro screen
 * and once a purchase, a restore or a change of plan is done, so as not to wait for RevenueCat's
 * webhook. Its body, empty or `{}`, is not read: the app has no say in its plan.
 *
 * It answers 503 `not_configured` without RevenueCat's key, 429 `rate_limited` past
 * [POLICY.planRefreshesPerHour] for the account, and 502 `subscription_unavailable` when RevenueCat
 * gives no plan that can be read, which leaves the plan kept as it was.
 */
export async function planRoute(request: Request, env: Env): Promise<Response> {
  const account = await sessionAccountOf(request, env);
  if (account === null) return errorResponse(401, "session");
  const key = revenueCatKey(env);
  if (key === null) return errorResponse(503, "not_configured");
  const refused = await hourlyRefusal(env.DB, account, "plan_refresh", POLICY.planRefreshesPerHour);
  if (refused !== null) return refused;
  const book = quotaBook(env, accountName(account));
  // The answer hands the id out, so it is recorded as handed out before RevenueCat is asked: the
  // read may create the customer, whose events the webhook then follows.
  await book.issuePurchaser(Date.now(), ALLOWANCES.monthly);
  if (!(await refreshPlan(env, key, account))) return errorResponse(502, "subscription_unavailable");
  const usage = await book.usage(Date.now(), ALLOWANCES.monthly);
  return jsonResponse(200, { purchaser: account, usage, aiEnabled: aiEnabled(env) });
}

/**
 * `DELETE /v1/account`: deletes everything the proxy holds about the signed-in account: first its
 * customer at RevenueCat, when RevenueCat is set up; then its part in the shared data, which
 * `forgetSharedData` takes out without changing anyone else's balances; then its call count, with
 * its plan and the record of its purchaser, and its spend counter. When RevenueCat does not take the
 * deletion, the answer is 502 `subscription_unavailable` and nothing else is deleted, so that the app
 * can try again. A subscription on Google Play goes on: the app says so before it deletes. The app
 * then forgets the session.
 */
export async function accountRoute(request: Request, env: Env): Promise<Response> {
  const account = await sessionAccountOf(request, env);
  if (account === null) return errorResponse(401, "session");
  const key = revenueCatKey(env);
  if (key !== null && !(await deleteCustomer(key, account))) return errorResponse(502, "subscription_unavailable");
  await forgetSharedData(env.DB, account, Date.now());
  const name = accountName(account);
  await Promise.all([quotaBook(env, name).forget(), ledger(env, name).forget()]);
  return emptyResponse(204);
}
