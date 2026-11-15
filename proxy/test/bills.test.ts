import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import type { GroupState } from "../src/group-state";
import { POLICY } from "../src/policy";
import { addBill, addPlaceholder, api, billBody, join, newGroup, type Person, person, rows, stateOf, type TestGroup } from "./shared";

/** A group of four: its owner, two members with accounts, and a placeholder. */
async function household() {
  const owner = await person();
  const group = await newGroup(owner);
  const lan = await person();
  const lanId = await join(group, lan, "Lan");
  const minh = await person();
  const minhId = await join(group, minh, "Minh");
  const hoaId = await addPlaceholder(group, "Hoa");
  return { owner, group, lan, lanId, minh, minhId, hoaId, ownerId: group.ownerMemberId };
}

function putBill(group: TestGroup, who: Person, billId: string, body: unknown) {
  return api<GroupState>(who, "PUT", `/v1/groups/${group.id}/bills/${billId}`, body);
}

describe("a bill", () => {
  it("is added by a member, belongs to their member, and is read back as it was sent", async () => {
    const { group, lan, lanId, minhId, hoaId, ownerId } = await household();
    const billId = crypto.randomUUID();
    const document = { title: "Dinner", lines: [{ name: "Pad thai", amountMinor: 35_000, participants: [lanId, minhId] }], note: "Ünïcödé ✓" };
    const conversion = { rate: "25350", date: "2026-11-14", source: "Frankfurter" };
    const shares = { [ownerId]: 300_888, [lanId]: 300_888, [hoaId]: 300_888 };
    const paid = { [lanId]: 902_664 };

    const put = await putBill(group, lan, billId, { expectedRevision: 0, document, conversion, totalMinor: 902_664, shares, paid });

    expect(put.status).toBe(200);
    expect(put.body.bills).toEqual([
      { id: billId, ownerMemberId: lanId, revision: 1, updatedAt: expect.any(Number), document, conversion, totalMinor: 902_664, shares, paid },
    ]);
    expect(put.body.log[0]).toEqual({ at: expect.any(Number), kind: "bill_added", memberId: lanId, subjectId: billId });
    expect((await stateOf(group)).bills).toEqual(put.body.bills);
  });

  it("changes at the revision it was read at, which then moves on by one, and a stale one is refused", async () => {
    const { group, lan, lanId, ownerId } = await household();
    const billId = await addBill(group, lan, { [lanId]: 500, [ownerId]: 500 }, { [lanId]: 1_000 });

    const changed = await putBill(group, lan, billId, billBody({ [lanId]: 400, [ownerId]: 600 }, { [lanId]: 1_000 }, 1));
    const stale = await putBill(group, lan, billId, billBody({ [lanId]: 1_000 }, { [lanId]: 1_000 }, 1));
    const asNew = await putBill(group, lan, billId, billBody({ [lanId]: 1_000 }, { [lanId]: 1_000 }, 0));
    const ahead = await putBill(group, lan, crypto.randomUUID(), billBody({ [lanId]: 1_000 }, { [lanId]: 1_000 }, 3));

    expect(changed.status).toBe(200);
    expect(changed.body.bills[0]).toMatchObject({ id: billId, revision: 2, shares: { [lanId]: 400, [ownerId]: 600 } });
    expect(changed.body.log[0]).toEqual({ at: expect.any(Number), kind: "bill_changed", memberId: lanId, subjectId: billId });
    for (const refused of [stale, asNew, ahead]) {
      expect(refused.status).toBe(409);
      expect(refused.body).toEqual({ error: "conflict" });
    }
    expect((await stateOf(group)).bills[0].revision).toBe(2);
  });

  it("sent again unchanged, after a lost answer, changes nothing", async () => {
    const { group, lan, lanId } = await household();
    const billId = crypto.randomUUID();
    const body = billBody({ [lanId]: 1_000 }, { [lanId]: 1_000 });
    const first = await putBill(group, lan, billId, body);

    const again = await putBill(group, lan, billId, body);

    expect(again.status).toBe(200);
    expect(again.body).toEqual(first.body);
    expect(again.body.log.filter((entry) => entry.kind === "bill_added")).toHaveLength(1);
  });

  it("is changed or deleted only by the member it belongs to or the group's owner", async () => {
    const { owner, group, lan, lanId, minh, minhId } = await household();
    const billId = await addBill(group, lan, { [lanId]: 500, [minhId]: 500 }, { [lanId]: 1_000 });
    const change = (revision: number) => billBody({ [lanId]: 200, [minhId]: 800 }, { [lanId]: 1_000 }, revision);

    const byMinh = await putBill(group, minh, billId, change(1));
    const deletedByMinh = await api(minh, "DELETE", `/v1/groups/${group.id}/bills/${billId}`);
    const byOwner = await putBill(group, owner, billId, change(1));

    expect(byMinh.status).toBe(403);
    expect(byMinh.body).toEqual({ error: "forbidden" });
    expect(deletedByMinh.status).toBe(403);
    expect(byOwner.status).toBe(200);
    expect(byOwner.body.bills[0]).toMatchObject({ ownerMemberId: lanId, revision: 2 });
    expect((await api(owner, "DELETE", `/v1/groups/${group.id}/bills/${billId}`)).status).toBe(200);
  });

  it("whose shares or payments do not add up to its total, or that names a non-member, is refused", async () => {
    const { group, lan, lanId, minhId } = await household();
    const good = billBody({ [lanId]: 500, [minhId]: 500 }, { [lanId]: 1_000 });
    const bad: Array<Record<string, unknown>> = [
      { ...good, shares: { [lanId]: 500, [minhId]: 499 } },
      { ...good, paid: { [lanId]: 1_001 } },
      { ...good, totalMinor: 999 },
      { ...good, shares: { [lanId]: 500, [crypto.randomUUID()]: 500 } },
      { ...good, paid: { [crypto.randomUUID()]: 1_000 } },
      { ...good, shares: { [lanId]: 500, [minhId.toUpperCase()]: 500 } },
      { ...good, shares: { [lanId]: 1_500, [minhId]: -500 } },
      { ...good, shares: { [lanId]: 500.5, [minhId]: 499.5 } },
      { ...good, shares: { [lanId]: "500", [minhId]: 500 } },
      // As JSON.parse reads it, "__proto__" is a key like any other.
      { ...good, shares: JSON.parse(`{"__proto__": 0, "${lanId}": 500, "${minhId}": 500}`) as Record<string, number> },
      { ...good, shares: [500, 500] },
      { ...good, totalMinor: -1, shares: {}, paid: {} },
      { ...good, document: "a bill" },
      { ...good, document: null },
      { ...good, expectedRevision: undefined },
      { ...good, expectedRevision: -1 },
      { ...good, conversion: { rate: "0", date: "2026-11-14", source: "Frankfurter" } },
      { ...good, conversion: { rate: "-1", date: "2026-11-14", source: "Frankfurter" } },
      { ...good, conversion: { rate: 25350, date: "2026-11-14", source: "Frankfurter" } },
      { ...good, conversion: { rate: "25350", date: "2026-02-30", source: "Frankfurter" } },
      { ...good, conversion: { rate: "25350", date: "2026-11-14" } },
    ];

    for (const body of bad) {
      const sent = await putBill(group, lan, crypto.randomUUID(), JSON.parse(JSON.stringify(body)));
      expect(sent.status, JSON.stringify(body)).toBe(400);
      expect(sent.body).toEqual({ error: "input" });
    }
    expect((await putBill(group, lan, "not-a-uuid", good)).status).toBe(400);
    const tooLarge = await putBill(group, lan, crypto.randomUUID(), { ...good, document: { text: "x".repeat(POLICY.maxBillDocumentBytes) } });
    expect(tooLarge.status).toBe(413);
    expect(tooLarge.body).toEqual({ error: "too_large" });
    expect((await stateOf(group)).bills).toEqual([]);
  });

  it("deleted, leaves the state, keeps its id from coming back, and deleting it again changes nothing", async () => {
    const { owner, group, lan, lanId } = await household();
    const billId = await addBill(group, lan, { [lanId]: 1_000 }, { [lanId]: 1_000 });

    const deleted = await api<GroupState>(lan, "DELETE", `/v1/groups/${group.id}/bills/${billId}`);
    const again = await api<GroupState>(owner, "DELETE", `/v1/groups/${group.id}/bills/${billId}`);

    expect(deleted.status).toBe(200);
    expect(deleted.body.bills).toEqual([]);
    expect(deleted.body.log[0]).toEqual({ at: expect.any(Number), kind: "bill_deleted", memberId: lanId, subjectId: billId });
    expect(again.status).toBe(200);
    expect(again.body).toEqual(await stateOf(group));
    expect(again.body.group.revision).toBe(deleted.body.group.revision);
    for (const revision of [0, 1, 2]) {
      expect((await putBill(group, lan, billId, billBody({ [lanId]: 1_000 }, { [lanId]: 1_000 }, revision))).status).toBe(409);
    }
    expect(await rows("SELECT document, shares, paid, total_minor FROM group_bills WHERE id = ?1", billId)).toEqual([
      { document: null, shares: null, paid: null, total_minor: null },
    ]);
    expect((await api(lan, "DELETE", `/v1/groups/${group.id}/bills/${crypto.randomUUID()}`)).status).toBe(404);
  });
});

describe("a settlement", () => {
  it("is recorded by the member who received the money, from another member", async () => {
    const { group, lan, lanId, hoaId } = await household();
    const id = crypto.randomUUID();

    const recorded = await api<GroupState>(lan, "POST", `/v1/groups/${group.id}/settlements`, { id, fromMemberId: hoaId, amountMinor: 1_000 });

    expect(recorded.status).toBe(200);
    expect(recorded.body.settlements).toEqual([
      { id, fromMemberId: hoaId, toMemberId: lanId, amountMinor: 1_000, recordedByMemberId: lanId, requestId: null, createdAt: expect.any(Number) },
    ]);
    expect(recorded.body.log[0]).toEqual({ at: expect.any(Number), kind: "settlement_recorded", memberId: lanId, subjectId: id });
  });

  it("names another member as the payer and an amount above zero", async () => {
    const { group, lan, lanId, minhId } = await household();
    const record = (fromMemberId: string, amountMinor: unknown) =>
      api(lan, "POST", `/v1/groups/${group.id}/settlements`, { id: crypto.randomUUID(), fromMemberId, amountMinor });

    for (const [from, amount] of [
      [lanId, 100],
      [crypto.randomUUID(), 100],
      [minhId, 0],
      [minhId, -100],
      [minhId, 10.5],
      [minhId, "100"],
    ] as const) {
      expect((await record(from, amount)).status, `${from} ${amount}`).toBe(400);
    }
    expect((await stateOf(group)).settlements).toEqual([]);
  });

  it("cannot be changed or deleted: no route does it, the same id again changes nothing, and the database refuses", async () => {
    const { group, lan, minh, minhId } = await household();
    const id = crypto.randomUUID();
    const path = `/v1/groups/${group.id}/settlements`;
    await api(lan, "POST", path, { id, fromMemberId: minhId, amountMinor: 1_000 });
    const before = await stateOf(group, lan);

    const again = await api<GroupState>(lan, "POST", path, { id, fromMemberId: minhId, amountMinor: 999_999 });
    const byAnother = await api(minh, "POST", path, { id, fromMemberId: minhId, amountMinor: 1 });

    expect(again.status).toBe(200);
    expect(again.body).toEqual(before);
    expect(byAnother.status).toBe(409);
    for (const method of ["PUT", "PATCH", "DELETE"]) {
      expect((await api(lan, method, `${path}/${id}`, { amountMinor: 1 })).body).toEqual({ error: "path" });
      expect((await api(lan, method, path, { id, amountMinor: 1 })).body).toEqual({ error: "method" });
    }
    await expect(env.DB.prepare("UPDATE settlements SET amount_minor = 1 WHERE id = ?1").bind(id).run()).rejects.toThrow(/append_only/);
    expect(await stateOf(group, lan)).toEqual(before);
  });
});

describe("amounts", () => {
  it("are taken up to 10^15 minor units, the app's largest, and refused above", async () => {
    const { group, lan, lanId, minhId } = await household();
    const max = POLICY.maxAmountMinor;
    expect(max).toBe(1_000_000_000_000_000);
    const settle = (amountMinor: number) =>
      api(lan, "POST", `/v1/groups/${group.id}/settlements`, { id: crypto.randomUUID(), fromMemberId: minhId, amountMinor });

    const largest = await putBill(group, lan, crypto.randomUUID(), billBody({ [lanId]: max / 2, [minhId]: max / 2 }, { [lanId]: max }));
    expect(largest.status).toBe(200);
    expect(largest.body.bills[0].totalMinor).toBe(max);
    expect((await settle(max)).status).toBe(200);

    const over = [
      { ...billBody({ [lanId]: max + 1 }, { [lanId]: max + 1 }) },
      { ...billBody({ [lanId]: max }, { [lanId]: max }), totalMinor: max + 1, shares: { [lanId]: max, [minhId]: 1 }, paid: { [lanId]: max, [minhId]: 1 } },
      { ...billBody({ [lanId]: max }, { [lanId]: max }), shares: { [lanId]: max + 1, [minhId]: -1 } },
    ];
    for (const body of over) expect((await putBill(group, lan, crypto.randomUUID(), body)).status).toBe(400);
    expect((await settle(max + 1)).status).toBe(400);
  });
});

describe("changes that come together", () => {
  it("to one bill at one revision: one lands and the other is refused", async () => {
    const { group, lan, lanId, minhId, owner } = await household();
    const billId = await addBill(group, lan, { [lanId]: 1_000 }, { [lanId]: 1_000 });

    const [first, second] = await Promise.all([
      putBill(group, lan, billId, billBody({ [lanId]: 400, [minhId]: 600 }, { [lanId]: 1_000 }, 1)),
      putBill(group, owner, billId, billBody({ [lanId]: 700, [minhId]: 300 }, { [lanId]: 1_000 }, 1)),
    ]);

    expect([first.status, second.status].sort()).toEqual([200, 409]);
    const state = await stateOf(group);
    expect(state.bills[0].revision).toBe(2);
    expect(state.log.filter((entry) => entry.kind === "bill_changed")).toHaveLength(1);
  });

  it("to one group all land, each moving its revision on by one", async () => {
    const { group, lan } = await household();
    const before = (await stateOf(group)).group.revision;

    const added = await Promise.all(
      Array.from({ length: 8 }, (_, index) => api(lan, "POST", `/v1/groups/${group.id}/members`, { id: crypto.randomUUID(), name: `Guest ${index}` })),
    );

    expect(added.map((answer) => answer.status)).toEqual(Array<number>(8).fill(200));
    const state = await stateOf(group);
    expect(state.group.revision).toBe(before + 8);
    expect(state.members.filter((member) => member.name.startsWith("Guest"))).toHaveLength(8);
    expect(state.log.filter((entry) => entry.kind === "member_added")).toHaveLength(9);
  });
});
