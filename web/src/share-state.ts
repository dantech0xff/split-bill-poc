import { type BillProjection, OWNER_PARTICIPANT_ID, type PublicClaim, type PublicShare, type ShareStatus } from "./contract";

/**
 * Whether [next] should replace [current]. Views come from `open`, from claims and from the live
 * connection, in any order; the one with the higher revision is the later state of the link.
 */
export function isNewer(current: PublicShare | null, next: PublicShare): boolean {
  return current === null || next.revision > current.revision;
}

export function claimOf(share: PublicShare, claimId: string | null): PublicClaim | null {
  if (claimId === null) return null;
  return share.claims.find((claim) => claim.id === claimId) ?? null;
}

export function claimersOf(share: PublicShare, itemId: string): PublicClaim[] {
  return share.claims.filter((claim) => claim.itemIds.includes(itemId));
}

/** The name the bill's owner gave themself, or null when they left it empty. */
export function ownerNameOf(bill: BillProjection): string | null {
  const name = bill.participants.find((participant) => participant.id === OWNER_PARTICIPANT_ID)?.name.trim() ?? "";
  return name === "" ? null : name;
}

/** A participant's name as the page shows it; the owner's empty name reads as the bill's owner. */
export function participantLabel(participant: { id: string; name: string }): string {
  const name = participant.name.trim();
  if (name !== "") return name;
  return participant.id === OWNER_PARTICIPANT_ID ? "The bill's owner" : "Someone";
}

export function statusSentence(status: ShareStatus, bill: BillProjection): string {
  switch (status) {
    case "open":
      return "Picks are open again.";
    case "locking":
      return `Picks are closed: ${ownerNameOf(bill) ?? "the payer"} is settling the split.`;
    case "locked":
      return "The split is settled. Each person's amount is below.";
  }
}

/** Whether the owner changed what the bill shows: items, charges, total or people. */
export function billChanged(before: BillProjection, after: BillProjection): boolean {
  return JSON.stringify(before) !== JSON.stringify(after);
}

const listFormat = typeof Intl.ListFormat === "function" ? new Intl.ListFormat("en", { type: "conjunction" }) : null;

/** "A", "A and B", "A, B, and C". */
export function listOf(names: readonly string[]): string {
  return listFormat ? listFormat.format(names) : names.join(", ");
}

const MAX_SENTENCES = 4;

/**
 * Sentences for the live region about what changed between [before] and [after] that this
 * browser did not do itself: picks by others, a new status, a changed bill.
 */
export function describeChanges(before: PublicShare, after: PublicShare, you: string | null): string[] {
  const sentences: string[] = [];
  if (before.status !== after.status) sentences.push(statusSentence(after.status, after.bill));
  if (billChanged(before.bill, after.bill)) sentences.push("The bill was updated.");
  const stillOnBill = new Set(after.bill.items.map((item) => item.id));
  const itemName = (id: string): string =>
    after.bill.items.find((item) => item.id === id)?.name ?? before.bill.items.find((item) => item.id === id)?.name ?? "an item";
  const earlier = new Map(before.claims.map((claim) => [claim.id, claim]));
  for (const claim of after.claims) {
    if (claim.id === you) continue;
    const old = earlier.get(claim.id);
    const added = claim.itemIds.filter((id) => !old?.itemIds.includes(id));
    const removed = (old?.itemIds ?? []).filter((id) => !claim.itemIds.includes(id) && stillOnBill.has(id));
    if (old && old.name !== claim.name) sentences.push(`${old.name} is now ${claim.name}.`);
    if (!old && added.length === 0) sentences.push(`${claim.name} joined.`);
    if (added.length > 0) sentences.push(`${claim.name} picked ${listOf(added.map(itemName))}.`);
    if (removed.length > 0) sentences.push(`${claim.name} put back ${listOf(removed.map(itemName))}.`);
  }
  const now = new Set(after.claims.map((claim) => claim.id));
  for (const old of before.claims) {
    if (old.id !== you && !now.has(old.id)) sentences.push(`${old.name} is no longer on the bill.`);
  }
  if (sentences.length <= MAX_SENTENCES) return sentences;
  return [...sentences.slice(0, MAX_SENTENCES), "There are more changes."];
}
