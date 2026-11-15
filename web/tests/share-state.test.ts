import { describe, expect, it } from "vitest";
import { type PublicShare, parseShare } from "../src/contract";
import { describeChanges, isNewer, matchOf, participantLabel, statusSentence } from "../src/share-state";
import shareOpen from "./fixtures/share-open.json";

function share(patch: Partial<PublicShare> = {}): PublicShare {
  const parsed = parseShare(structuredClone(shareOpen.share));
  if (parsed === null) throw new Error("The fixture does not parse");
  return { ...parsed, ...patch };
}

describe("matchOf", () => {
  it("finds the participant a claim is on the bill as, and nobody for a claim the owner matched to nobody", () => {
    const settled = share({ status: "locked", matches: { "c-minh": "p1" } });
    expect(matchOf(settled, "c-minh")).toEqual({ id: "p1", name: "Minh" });
    expect(matchOf(settled, "c-an")).toBeNull();
    expect(matchOf(settled, null)).toBeNull();
    expect(matchOf(share(), "c-minh")).toBeNull();
    // Only a participant of the bill: an id the bill does not have names nobody.
    expect(matchOf(share({ matches: { "c-minh": "p9" } }), "c-minh")).toBeNull();
  });

  it("finds claims whose ids are __proto__ and constructor like any other", () => {
    const parsed = parseShare({ ...shareOpen.share, matches: JSON.parse('{"__proto__": "p2"}') as unknown });
    if (parsed === null) throw new Error("The share does not parse");
    expect(matchOf(parsed, "__proto__")).toEqual({ id: "p2", name: "An" });
    expect(matchOf(parsed, "constructor")).toBeNull();
  });
});

describe("isNewer", () => {
  it("keeps whichever view has the higher revision", () => {
    expect(isNewer(null, share({ revision: 1 }))).toBe(true);
    expect(isNewer(share({ revision: 7 }), share({ revision: 8 }))).toBe(true);
    expect(isNewer(share({ revision: 8 }), share({ revision: 7 }))).toBe(false);
    expect(isNewer(share({ revision: 8 }), share({ revision: 8 }))).toBe(false);
  });
});

describe("describeChanges", () => {
  const before = share();

  it("tells who picked or put back what", () => {
    const after = share({
      revision: 8,
      claims: [
        { id: "c-minh", name: "Minh", itemIds: ["i1", "i3"] },
        { id: "c-an", name: "An", itemIds: ["i1"] },
      ],
    });
    expect(describeChanges(before, after, null)).toEqual(["Minh picked Iced coffee.", "An put back Spring rolls."]);
  });

  it("tells of someone new and of someone taken off", () => {
    const after = share({
      revision: 8,
      claims: [
        { id: "c-an", name: "An", itemIds: ["i1", "i2"] },
        { id: "c-bao", name: "Bao", itemIds: [] },
      ],
    });
    expect(describeChanges(before, after, null)).toEqual(["Bao joined.", "Minh is no longer on the bill."]);
  });

  it("leaves out what this browser did itself", () => {
    const after = share({ revision: 8, claims: [{ id: "c-minh", name: "Minh", itemIds: [] }, before.claims[1]!] });
    expect(describeChanges(before, after, "c-minh")).toEqual([]);
  });

  it("tells of a new status and of a changed bill", () => {
    const after = share({ revision: 9, status: "locking", bill: { ...before.bill, totalMinor: 240000 } });
    expect(describeChanges(before, after, null)).toEqual(["Picks are closed: Lan is settling the split.", "The bill was updated."]);
  });
});

describe("participantLabel and statusSentence", () => {
  it("call the owner's empty name the bill's owner", () => {
    expect(participantLabel({ id: "me", name: "" })).toBe("The bill's owner");
    expect(participantLabel({ id: "me", name: "Lan" })).toBe("Lan");
  });

  it("name the owner, or the person who made this link when the owner left their name empty", () => {
    const bill = share().bill;
    expect(statusSentence("locking", bill)).toBe("Picks are closed: Lan is settling the split.");
    expect(statusSentence("locking", { ...bill, participants: [{ id: "me", name: "" }] })).toBe(
      "Picks are closed: the person who made this link is settling the split.",
    );
  });
});
