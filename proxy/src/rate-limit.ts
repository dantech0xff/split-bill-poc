import type { Env } from "./index";
import { clientIp, hashed, ipKey } from "./ledger";
import { POLICY } from "./policy";
import { errorResponse } from "./respond";

/**
 * What an hourly limit counts. An account counts the invites, payment requests, group changes,
 * bill links and changes to them it makes, and the times it has its plan read from RevenueCat; a
 * hashed IP counts what the web pages do without a session: new claims, claim writes and reads.
 */
export type HourlyKind =
  | "invite"
  | "request"
  | "group_write"
  | "share"
  | "share_write"
  | "plan_refresh"
  | "claim_new"
  | "claim_write"
  | "web_read";

/**
 * Counts one more [kind] made by [name] in a fixed window of an hour while that stays within
 * [limit], and says whether it did. [name] is an account, or what [addressOf] makes of an IP. The
 * window starts with the first one counted and starts over an hour later. One statement reads and
 * counts, so requests that come together cannot both take the last place. A refused attempt writes
 * nothing: once the window is full, the statement leaves its row as it is until the hour is over,
 * so that requests past a limit cost no writes.
 */
export async function admitHourly(
  db: D1Database,
  name: string,
  kind: HourlyKind,
  limit: number,
  now: number,
): Promise<{ ok: true } | { ok: false; retryAfter: number }> {
  const counted = await db
    .prepare(
      `INSERT INTO rate_windows (account, kind, started_at, used) VALUES (?1, ?2, ?3, 1)
       ON CONFLICT (account, kind) DO UPDATE SET
         started_at = CASE WHEN ?3 - started_at >= ?4 THEN ?3 ELSE started_at END,
         used = CASE WHEN ?3 - started_at >= ?4 THEN 1 ELSE used + 1 END
       WHERE ?3 - rate_windows.started_at >= ?4 OR rate_windows.used < ?5
       RETURNING used`,
    )
    .bind(name, kind, now, POLICY.hourMs, limit)
    .first<{ used: number }>();
  if (counted !== null) return { ok: true };
  // The window is full and was left as it is; when it started says how long until it starts over.
  const full = await db
    .prepare("SELECT started_at FROM rate_windows WHERE account = ?1 AND kind = ?2")
    .bind(name, kind)
    .first<{ started_at: number }>();
  const startedAt = full?.started_at ?? now;
  return { ok: false, retryAfter: Math.max(1, Math.ceil((startedAt + POLICY.hourMs - now) / 1000)) };
}

/**
 * The name the IP of [request] counts under: `ip:` and a salted hash of it, as for the IP limits
 * of bill reads, so that the address itself is never stored. An IPv6 address counts by its /64. A
 * request that reached the edge without an address gets the answer 400 `input` instead. Naming
 * an address writes nothing; [hourlyRefusal] counts under the name.
 */
export async function addressOf(request: Request, env: Env): Promise<{ ok: true; name: string } | { ok: false; response: Response }> {
  const ip = clientIp(request);
  if (!ip.ok) return { ok: false, response: errorResponse(400, "input") };
  return { ok: true, name: `ip:${await hashed(ipKey(ip.ip), env.HASH_SALT)}` };
}

/**
 * Counts one more [kind] of [name] against [limit] an hour, as [admitHourly] does: the answer 429
 * `rate_limited` with `Retry-After` past the limit, or null when it may go on. The routes of the web
 * pages call it only once a token led to a link, a request or an invite that works, so that a token
 * that leads nowhere is answered without a write.
 */
export async function hourlyRefusal(db: D1Database, name: string, kind: HourlyKind, limit: number): Promise<Response | null> {
  const gate = await admitHourly(db, name, kind, limit, Date.now());
  return gate.ok ? null : errorResponse(429, "rate_limited", { retryAfter: gate.retryAfter });
}
