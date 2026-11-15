import { forgetSharedData } from "./account-data";
import { ALLOWANCES, callerOf, ledger, quotaBook, sessionAccountOf } from "./identity";
import type { Env } from "./index";
import { readJson } from "./input";
import { emptyResponse, errorResponse, jsonResponse } from "./respond";
import { googleAccountOf, issueSession, signInReady } from "./session";
import { aiEnabled } from "./switches";

const MAX_ID_TOKEN_CHARS = 8 * 1024;

/**
 * `POST /v1/session` with `{"idToken": …}`: trades the ID token Google gave the app at sign-in for
 * a session the proxy signs, and says how many calls the account has left this month.
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
  const usage = await quotaBook(env, `google:${checked.account}`).usage(now, ALLOWANCES.monthly);
  return jsonResponse(200, { session, expiresAt, usage });
}

/** `GET /v1/usage`: the calls the caller has left, and whether bills can be read with AI at all. */
export async function usageRoute(request: Request, env: Env): Promise<Response> {
  const who = await callerOf(request, env);
  if (!who.ok) return errorResponse(401, who.code);
  const usage = await quotaBook(env, who.caller.name).usage(Date.now(), who.caller.allowance);
  return jsonResponse(200, { usage, aiEnabled: aiEnabled(env) });
}

/**
 * `DELETE /v1/account`: deletes everything the proxy holds about the signed-in account: its call
 * count, its spend counter, and its part in the shared data, which `forgetSharedData` takes out
 * without changing anyone else's balances. The app then forgets the session.
 */
export async function accountRoute(request: Request, env: Env): Promise<Response> {
  const account = await sessionAccountOf(request, env);
  if (account === null) return errorResponse(401, "session");
  await forgetSharedData(env.DB, account, Date.now());
  const name = `google:${account}`;
  await Promise.all([quotaBook(env, name).forget(), ledger(env, name).forget()]);
  return emptyResponse(204);
}
