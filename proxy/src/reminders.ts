import type { Env } from "./index";
import { POLICY } from "./policy";
import { devicesOf, pushReady, sendTo } from "./push";

const DAY_MS = 24 * 60 * 60 * 1000;
/**
 * A reminder may go out this much before its days are quite up, so that a daily run that starts
 * a few seconds earlier than the one before still sends it.
 */
const EARLY_MS = 60 * 60 * 1000;

/**
 * The reminders of the daily cron. A request is due when it is neither confirmed nor reported
 * paid, has a debtor and a creditor, reminds every N days, and has had no reminder (nor been
 * made) in the last N days. Each due request's debtor is notified and the request records when.
 * The oldest due go first, within [POLICY.remindersPerRun] notifications; the rest go the next
 * day. Without push set up, nothing is sent and nothing recorded.
 */
export async function sendReminders(env: Env, now: number): Promise<void> {
  if (!pushReady(env)) return;
  const db = env.DB;
  const { results: due } = await db
    .prepare(
      `SELECT id, debtor FROM requests
       WHERE remind_every_days > 0 AND confirmed_paid_at IS NULL AND deleted_at IS NULL
         AND reported_paid_at IS NULL AND debtor IS NOT NULL AND creditor IS NOT NULL
         AND coalesce(last_reminded_at, created_at) <= ?1 - remind_every_days * ?2
       ORDER BY coalesce(last_reminded_at, created_at), id
       LIMIT ?3`,
    )
    .bind(now + EARLY_MS, DAY_MS, POLICY.remindersPerRun)
    .all<{ id: string; debtor: string }>();
  if (due.length === 0) return;
  const devices = await devicesOf(db, [...new Set(due.map((request) => request.debtor))]);
  const reminders: Array<{ id: string; devices: typeof devices }> = [];
  let sends = 0;
  for (const request of due) {
    const own = devices.filter((device) => device.account === request.debtor);
    if (sends + own.length > POLICY.remindersPerRun) break;
    sends += own.length;
    reminders.push({ id: request.id, devices: own });
  }
  if (reminders.length === 0) return;
  // Recorded before sending, so that a run started again does not remind twice.
  await db
    .prepare("UPDATE requests SET last_reminded_at = ?1 WHERE id IN (SELECT value FROM json_each(?2))")
    .bind(now, JSON.stringify(reminders.map((reminder) => reminder.id)))
    .run();
  for (const reminder of reminders) {
    await sendTo(env, reminder.devices, { type: "reminder", requestId: reminder.id });
  }
  console.log(JSON.stringify({ event: "reminders", due: due.length, reminded: reminders.length, notifications: sends }));
}

/**
 * Forgets what no longer works and no longer needs to answer: invites and deleted requests
 * [POLICY.forgetAfterMs] after they stopped working (their links then answer 404 rather than
 * 410), and the hourly windows that have ended.
 */
export async function forgetExpired(env: Env, now: number): Promise<void> {
  const db = env.DB;
  const before = now - POLICY.forgetAfterMs;
  await db.batch([
    db.prepare("DELETE FROM invites WHERE expires_at < ?1").bind(before),
    db.prepare("DELETE FROM requests WHERE deleted_at IS NOT NULL AND deleted_at < ?1").bind(before),
    db.prepare("DELETE FROM rate_windows WHERE started_at < ?1").bind(now - POLICY.hourMs),
  ]);
}
