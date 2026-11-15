import type { Env } from "./index";
import { POLICY } from "./policy";
import { type Device, devicesOf, pushReady, pushSender } from "./push";

const DAY_MS = 24 * 60 * 60 * 1000;
/** A payment reported to a creditor waits this long before they are told to confirm it, and as long again between notices. */
const CONFIRM_AFTER_MS = 2 * DAY_MS;
/**
 * A notice may go out this much before its days are quite up, so that a daily run that starts a
 * few seconds earlier than the one before still sends it.
 */
const EARLY_MS = 60 * 60 * 1000;

type Notice = "reminder" | "unpaid" | "unconfirmed";
/** A request that waits, the account to tell, and what to tell them. */
type Due = { id: string; recipient: string; type: Notice };

/**
 * The requests that wait, oldest first, each with the one account to tell and only when that
 * account has a device, so that nobody who cannot get a notice takes a place in the run:
 *
 * - due a reminder (neither confirmed nor reported paid, a creditor, reminding every N days, and no
 *   reminder, nor its making, in the last N days): to the debtor, when bound and with a device; or
 *   else, as `unpaid`, to the creditor, who can then nudge the payer themself;
 * - reported paid two days ago and not confirmed, and not told so in the last two days: to the
 *   creditor, as `unconfirmed`.
 */
const DUE = `
  SELECT id, recipient, type FROM (
    SELECT r.id AS id,
      CASE WHEN EXISTS (SELECT 1 FROM devices d WHERE d.account = r.debtor) THEN r.debtor ELSE r.creditor END AS recipient,
      CASE WHEN EXISTS (SELECT 1 FROM devices d WHERE d.account = r.debtor) THEN 'reminder' ELSE 'unpaid' END AS type,
      coalesce(r.last_reminded_at, r.created_at) + r.remind_every_days * ?2 AS due_at
    FROM requests r
    WHERE r.remind_every_days > 0 AND r.confirmed_paid_at IS NULL AND r.deleted_at IS NULL
      AND r.reported_paid_at IS NULL AND r.creditor IS NOT NULL
      AND coalesce(r.last_reminded_at, r.created_at) <= ?1 - r.remind_every_days * ?2
      AND (EXISTS (SELECT 1 FROM devices d WHERE d.account = r.debtor) OR EXISTS (SELECT 1 FROM devices d WHERE d.account = r.creditor))
    UNION ALL
    SELECT r.id, r.creditor, 'unconfirmed', coalesce(r.confirm_reminded_at, r.reported_paid_at) + ?3
    FROM requests r
    WHERE r.reported_paid_at IS NOT NULL AND r.confirmed_paid_at IS NULL AND r.deleted_at IS NULL AND r.creditor IS NOT NULL
      AND coalesce(r.confirm_reminded_at, r.reported_paid_at) <= ?1 - ?3
      AND EXISTS (SELECT 1 FROM devices d WHERE d.account = r.creditor)
  )
  ORDER BY due_at, id
  LIMIT ?4`;

/**
 * The notices of the daily cron about requests that wait (see [DUE]). The oldest go first,
 * within [POLICY.remindersPerRun] notifications, counted by the devices they go to; the rest go
 * the next day. A reminder or an `unpaid` notice records `last_reminded_at`, so both follow the
 * request's own days; an `unconfirmed` one records `confirm_reminded_at`. The access token is
 * fetched once for the run: without push set up, or without a token, nothing is sent and nothing
 * recorded.
 */
export async function sendReminders(env: Env, now: number): Promise<void> {
  if (!pushReady(env)) return;
  const db = env.DB;
  // The query names the notice type itself, from its CASE and its constants.
  const { results: due } = await db.prepare(DUE).bind(now + EARLY_MS, DAY_MS, CONFIRM_AFTER_MS, POLICY.remindersPerRun).all<Due>();
  if (due.length === 0) return;
  const sender = await pushSender(env);
  if (sender === null) return;
  const devices = await devicesOf(db, [...new Set(due.map((notice) => notice.recipient))]);
  const chosen: Array<Due & { devices: Device[] }> = [];
  let sends = 0;
  for (const notice of due) {
    const own = devices.filter((device) => device.account === notice.recipient);
    // Its last device went after the query.
    if (own.length === 0) continue;
    if (sends + own.length > POLICY.remindersPerRun) break;
    sends += own.length;
    chosen.push({ ...notice, devices: own });
  }
  if (chosen.length === 0) return;
  const idsOf = (types: Notice[]) => JSON.stringify(chosen.filter((notice) => types.includes(notice.type)).map((notice) => notice.id));
  // Recorded before sending, so that a run started again does not send twice.
  await db.batch([
    db.prepare("UPDATE requests SET last_reminded_at = ?1 WHERE id IN (SELECT value FROM json_each(?2))").bind(now, idsOf(["reminder", "unpaid"])),
    db.prepare("UPDATE requests SET confirm_reminded_at = ?1 WHERE id IN (SELECT value FROM json_each(?2))").bind(now, idsOf(["unconfirmed"])),
  ]);
  for (const notice of chosen) {
    await sender.send(notice.devices, { type: notice.type, requestId: notice.id });
  }
  console.log(JSON.stringify({ event: "reminders", due: due.length, sent: chosen.length, notifications: sends }));
}

/**
 * Forgets what no longer works and no longer needs to answer: invites and deleted requests
 * [POLICY.forgetAfterMs] after they stopped working (their links then answer 404 rather than
 * 410), bill links once they expired or were revoked, with their claims (their links then answer
 * 404 too), and the hourly windows that have ended. One batch, and no Durable Object: the pages
 * that still have a deleted link open hear nothing more, and learn it at their next request.
 */
export async function forgetExpired(env: Env, now: number): Promise<void> {
  const db = env.DB;
  const before = now - POLICY.forgetAfterMs;
  await db.batch([
    db.prepare("DELETE FROM invites WHERE expires_at < ?1").bind(before),
    db.prepare("DELETE FROM requests WHERE deleted_at IS NOT NULL AND deleted_at < ?1").bind(before),
    // Their claims go with them, through the schema's cascade.
    db.prepare("DELETE FROM shares WHERE expires_at <= ?1").bind(now),
    db.prepare("DELETE FROM shares WHERE revoked_at IS NOT NULL"),
    db.prepare("DELETE FROM rate_windows WHERE started_at < ?1").bind(now - POLICY.hourMs),
  ]);
}
