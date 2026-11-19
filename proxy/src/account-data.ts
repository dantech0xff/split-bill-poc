/** The name a member gets once its account is deleted. */
export const FORMER_MEMBER = "Former member";

/**
 * Takes [account] out of the shared data, in one batch, so that it is gone everywhere or nowhere.
 * Every amount and settlement stays as it is, so nobody else's balance changes:
 *
 * - its members become placeholders named [FORMER_MEMBER], keeping their ids, and each group logs
 *   that they left;
 * - a group where nobody else has an account is deleted, as when its last account leaves;
 * - a group it owns passes to the member with an account who joined first, and its bills pass to
 *   the group's owner;
 * - its claims and devices are deleted. Its hourly windows stay, holding nothing of the account but
 *   its salted hash, until the daily run forgets them once their hour is over, as it forgets every
 *   window, so that neither deleting the account again and again nor a session that outlives the
 *   deletion starts a limit over;
 * - the requests it made keep their amounts for the debtor, but the payment method of each one
 *   not yet confirmed becomes `{}`, the payment for the web page of every one, confirmed or not,
 *   is emptied, so that its link no longer shows how the account was paid, and the creditor's
 *   name on every one becomes [FORMER_MEMBER]; the account is taken off every request, as
 *   creditor and as debtor, so none can be read or changed through it again, and no reminder goes
 *   out for a creditor who is gone;
 * - the bill links it made are deleted, with their claims. Their pages hear nothing of it, and
 *   learn it at their next request.
 *
 * Every group it was in, or waited to join, moves on a revision, so that a change decided on the
 * group as it was is refused and decided again.
 */
export async function forgetSharedData(db: D1Database, account: string, now: number): Promise<void> {
  // The groups where nobody but [account] has an account.
  const orphaned = `SELECT m.group_id FROM members m WHERE m.account = ?1 AND NOT EXISTS (
      SELECT 1 FROM members o WHERE o.group_id = m.group_id AND o.account IS NOT NULL AND o.account <> ?1)`;
  await db.batch([
    db
      .prepare(
        `UPDATE groups SET revision = revision + 1
         WHERE id IN (SELECT group_id FROM members WHERE account = ?1 UNION SELECT group_id FROM claims WHERE account = ?1)`,
      )
      .bind(account),
    db
      .prepare("INSERT INTO group_log (group_id, at, kind, member_id, subject_id) SELECT group_id, ?2, 'member_left', id, id FROM members WHERE account = ?1")
      .bind(account, now),
    db
      .prepare(`UPDATE requests SET group_id = NULL, to_member_id = NULL, creditor_member_id = NULL, bill_id = NULL WHERE group_id IN (${orphaned})`)
      .bind(account),
    db.prepare(`DELETE FROM groups WHERE id IN (${orphaned})`).bind(account),
    db
      .prepare(
        `UPDATE groups SET owner_member_id = (
           SELECT o.id FROM members o WHERE o.group_id = groups.id AND o.account IS NOT NULL AND o.account <> ?1 ORDER BY o.joined_at, o.id LIMIT 1)
         WHERE owner_member_id IN (SELECT m.id FROM members m WHERE m.group_id = groups.id AND m.account = ?1)`,
      )
      .bind(account),
    db
      .prepare(
        `UPDATE group_bills SET owner_member_id = (SELECT g.owner_member_id FROM groups g WHERE g.id = group_bills.group_id)
         WHERE (group_id, owner_member_id) IN (SELECT group_id, id FROM members WHERE account = ?1)`,
      )
      .bind(account),
    db.prepare("UPDATE members SET account = NULL, name = ?2 WHERE account = ?1").bind(account, FORMER_MEMBER),
    db.prepare("DELETE FROM claims WHERE account = ?1").bind(account),
    db.prepare("DELETE FROM devices WHERE account = ?1").bind(account),
    db
      .prepare("UPDATE requests SET payment = NULL, method = CASE WHEN confirmed_paid_at IS NULL THEN '{}' ELSE method END WHERE creditor = ?1")
      .bind(account),
    db.prepare("UPDATE requests SET creditor = NULL, creditor_name = ?2 WHERE creditor = ?1").bind(account, FORMER_MEMBER),
    db.prepare("UPDATE requests SET debtor = NULL WHERE debtor = ?1").bind(account),
    // Their claims go with them, through the schema's cascade.
    db.prepare("DELETE FROM shares WHERE owner = ?1").bind(account),
  ]);
}
