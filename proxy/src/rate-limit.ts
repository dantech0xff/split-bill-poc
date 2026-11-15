import { POLICY } from "./policy";

export type HourlyKind = "invite";

/**
 * Counts one more [kind] made by [account] in a fixed window of an hour, and says whether it is
 * within [limit]. The window starts with the first one counted and starts over an hour later. A
 * refused attempt is counted as well, which does not make the window last longer. One statement
 * reads and counts, so requests that come together cannot both take the last place.
 */
export async function admitHourly(
  db: D1Database,
  account: string,
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
    .bind(account, kind, now, POLICY.hourMs)
    .first<{ started_at: number; used: number }>();
  if (window === null) throw new Error("The hourly window was not counted");
  if (window.used <= limit) return { ok: true };
  return { ok: false, retryAfter: Math.max(1, Math.ceil((window.started_at + POLICY.hourMs - now) / 1000)) };
}
