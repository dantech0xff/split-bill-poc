import { ALLOWANCES, callerOf, ledger, quotaBook } from "./identity";
import type { Env } from "./index";
import { readJson } from "./input";
import { errorResponse, jsonResponse } from "./respond";
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
 * `DELETE /v1/account`: deletes everything the proxy holds about the signed-in account, which is
 * its call count and its spend counter. The app then forgets the session.
 */
export async function accountRoute(request: Request, env: Env): Promise<Response> {
  const who = await callerOf(request, env);
  if (!who.ok || who.caller.kind !== "google") return errorResponse(401, "session");
  await Promise.all([quotaBook(env, who.caller.name).forget(), ledger(env, who.caller.name).forget()]);
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}
