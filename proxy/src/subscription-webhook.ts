import { accountName, quotaBook } from "./identity";
import type { Env } from "./index";
import { isRecord, jsonBody } from "./input";
import { count } from "./metrics";
import { bodyRefusal, errorResponse, jsonResponse } from "./respond";
import { refreshPlan, revenueCatKey } from "./revenuecat";

/** The largest body of an event; RevenueCat's events take a few kilobytes. */
const MAX_EVENT_BYTES = 64 * 1024;
/** The most accounts one event leads the proxy to ask RevenueCat about. */
const MAX_EVENT_ACCOUNTS = 20;
/** An account id as the proxy hands it out as a purchaser: 64 hex digits in lower case. */
const ACCOUNT = /^[0-9a-f]{64}$/;
/** What a log line repeats of an event's type: RevenueCat writes its types in capitals and underscores. */
const EVENT_TYPE = /^[A-Z_]{1,64}$/;

/**
 * `POST /v1/subscription-events`: the webhook RevenueCat calls when a subscription changes.
 *
 * Its `Authorization` header must be the value set in RevenueCat's webhook, the secret
 * `REVENUECAT_WEBHOOK_AUTH`, as it is or after `Bearer `; otherwise the answer is 401 `webhook_auth`,
 * and nothing is read, asked or written. Without the secret the answer is 503 `not_configured`.
 *
 * What an event says is only a sign that something changed. The proxy takes the ids the event
 * names, keeps the accounts among them whose ids it handed out as purchasers and has not forgotten,
 * and asks RevenueCat for the plan of each, which it keeps as `POST /v1/plan` does. So an event that
 * comes twice, late or out of order leaves the plan RevenueCat gives now, and one about an id the
 * proxy never handed out or has forgotten, such as a deleted account's, asks RevenueCat nothing:
 * asking would create the customer.
 *
 * RevenueCat counts every answer but 200 as a failure, 204 among them, and sends a failed event
 * again, five times over about 80 minutes. So the answer is 200 with a small body once every account
 * the event is about was refreshed, for a `TEST` event, and for an event about no such account; it
 * is 503 when RevenueCat could not be read for one of them, so that the event comes again, which is
 * safe: what was kept meanwhile stays right. When the QuotaBook of an account it names cannot be
 * asked, the request fails as any route's does, answered 500 `internal`, which RevenueCat sends
 * again too.
 *
 * One log line per event says its type and what came of it, never an id, a body or a secret; an
 * event that fails as above is logged as any failed request is instead. Each answered event is
 * counted as `subscription_event`, with what came of it but not its type.
 */
export async function subscriptionEventsRoute(request: Request, env: Env): Promise<Response> {
  const started = Date.now();
  const answered = (outcome: string, response: Response, fields: Record<string, unknown> = {}): Response => {
    const ms = Date.now() - started;
    console.log(JSON.stringify({ event: "subscription_event", ...fields, outcome, ms }));
    count(env, "subscription_event", outcome, { ms });
    return response;
  };
  const secret = env.REVENUECAT_WEBHOOK_AUTH?.trim() ?? "";
  if (secret === "") return answered("not_configured", errorResponse(503, "not_configured"));
  if (!(await authorized(request.headers.get("authorization"), secret))) return answered("webhook_auth", errorResponse(401, "webhook_auth"));

  const body = await jsonBody(request, MAX_EVENT_BYTES);
  if (!body.ok) return answered(body.code, bodyRefusal(body.code));
  const event = isRecord(body.value) && isRecord(body.value.event) ? body.value.event : null;
  if (event === null) return answered("input", bodyRefusal("input"));
  const type = typeof event.type === "string" && EVENT_TYPE.test(event.type) ? event.type : "other";
  if (type === "TEST") return answered("test", jsonResponse(200, { ok: true }), { type });

  const accounts = await accountsOf(env, event);
  if (accounts.length === 0) return answered("no_account", jsonResponse(200, { ok: true }), { type, accounts: 0 });
  const key = revenueCatKey(env);
  if (key === null) return answered("not_configured", errorResponse(503, "not_configured"), { type, accounts: accounts.length });
  const refreshed = await Promise.all(accounts.map((account) => refreshPlan(env, key, account)));
  const failed = refreshed.filter((ok) => !ok).length;
  if (failed > 0) {
    return answered("subscription_unavailable", errorResponse(503, "subscription_unavailable"), { type, accounts: accounts.length, failed });
  }
  return answered("ok", jsonResponse(200, { ok: true }), { type, accounts: accounts.length });
}

/**
 * Whether [header] is [secret], as it is or after `Bearer `. The values are compared by their
 * SHA-256 digests, in constant time, so that neither the time an answer takes nor the length of a
 * guess tells how much of the secret the guess got right.
 */
async function authorized(header: string | null, secret: string): Promise<boolean> {
  const [given, bare, bearer] = await Promise.all([header ?? "", secret, `Bearer ${secret}`].map(sha256));
  // Both comparisons run, whichever of them matches.
  const matches = [crypto.subtle.timingSafeEqual(given, bare), crypto.subtle.timingSafeEqual(given, bearer)];
  return matches.includes(true);
}

function sha256(text: string): Promise<ArrayBuffer> {
  return crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
}

/**
 * The accounts [event] is about: of the first [MAX_EVENT_ACCOUNTS] ids it names that have the form
 * of an account, each once, those the proxy handed out as purchasers and has not forgotten. The cap
 * comes first, so ids of that form the proxy never handed out count towards it. A `TRANSFER` names
 * its two sides in `transferred_from` and `transferred_to`, without `app_user_id`; both lists come
 * before `aliases`, so that a long list of aliases cannot crowd them out.
 */
async function accountsOf(env: Env, event: Record<string, unknown>): Promise<string[]> {
  const named = [
    event.app_user_id,
    event.original_app_user_id,
    ...listOf(event.transferred_from),
    ...listOf(event.transferred_to),
    ...listOf(event.aliases),
  ];
  const ids = [...new Set(named.filter((id): id is string => typeof id === "string" && ACCOUNT.test(id)))].slice(0, MAX_EVENT_ACCOUNTS);
  const issued = await Promise.all(ids.map((id) => quotaBook(env, accountName(id)).purchaserIssued()));
  return ids.filter((_, index) => issued[index]);
}

function listOf(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}
