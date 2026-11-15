import type { Env } from "./index";
import { clientIp, hashed, ipKey } from "./ledger";
import { POLICY } from "./policy";
import { errorResponse } from "./respond";

/**
 * What an hourly limit counts. An account counts the invites, payment requests and group changes
 * it makes; a hashed IP counts what the web pages read without a session.
 */
export type HourlyKind = "invite" | "request" | "group_write" | "web_read";

/**
 * Counts one more [kind] made by [name] in a fixed window of an hour, and says whether it is
 * within [limit]. [name] is an account, or what [addressName] makes of an IP. The window starts
 * with the first one counted and starts over an hour later. A refused attempt is counted as well,
 * which does not make the window last longer. One statement reads and counts, so requests that
 * come together cannot both take the last place.
 */
export async function admitHourly(
  db: D1Database,
  name: string,
  kind: HourlyKind,
  limit: number,
  now: number,
): Promise<{ ok: true } | { ok: false; retryAfter: number }> {
  const window = await db
    .prepare(
      `INSERT INTO rate_windows (account, kind, started_at, used) VALUES (?1, ?2, ?3, 1)
       ON CONFLICT (account, kind) DO UPDATE SET
         started_at = CASE WHEN ?3 - started_at >= ?4 THEN ?3 ELSE started_at END,
         used = CASE WHEN ?3 - started_at >= ?4 THEN 1 ELSE used + 1 END
       RETURNING started_at, used`,
    )
    .bind(name, kind, now, POLICY.hourMs)
    .first<{ started_at: number; used: number }>();
  if (window === null) throw new Error("The hourly window was not counted");
  if (window.used <= limit) return { ok: true };
  return { ok: false, retryAfter: Math.max(1, Math.ceil((window.started_at + POLICY.hourMs - now) / 1000)) };
}

/**
 * The name an IP counts under: `ip:` and a salted hash of it, as for the IP limits of bill reads,
 * so that the address itself is never stored. An IPv6 address counts by its /64. Null for a
 * request that reached the edge without an address.
 */
export async function addressName(request: Request, env: Env): Promise<string | null> {
  const ip = clientIp(request);
  return ip.ok ? `ip:${await hashed(ipKey(ip.ip), env.HASH_SALT)}` : null;
}

/**
 * Counts one more [kind] from the IP [request] came from against [limit] an hour, for the routes
 * the web pages call without a session. The answer to refuse it with: 429 `rate_limited` past the
 * limit, or 400 `input` for a request that carries no address; null when it may go on.
 */
export async function addressRefusal(request: Request, env: Env, kind: HourlyKind, limit: number): Promise<Response | null> {
  const name = await addressName(request, env);
  if (name === null) return errorResponse(400, "input");
  const gate = await admitHourly(env.DB, name, kind, limit, Date.now());
  return gate.ok ? null : errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
}
