import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import type { GroupState } from "../src/group-state";
import { POLICY } from "../src/policy";
import { send } from "./requests";
import { addBill, addPlaceholder, api, invite, join, memberView, newGroup, person, rows, stateOf, THB, VND } from "./shared";

describe("a group", () => {
  it("is made with the caller as its owner and first member, and read by its members only", async () => {
    const owner = await person();
    const id = crypto.randomUUID();
    const memberId = crypto.randomUUID();
    const before = Date.now();

    const made = await api<GroupState>(owner, "POST", "/v1/groups", { id, name: "  Flat 4B  ", currency: VND, memberId, memberName: " Lan " });

    expect(made.status).toBe(201);
    expect(made.headers.get("cache-control")).toBe("no-store");
    expect(made.body).toEqual({
      group: { id, name: "Flat 4B", currency: VND, ownerMemberId: memberId, revision: 1, createdAt: expect.any(Number) },
      myMemberId: memberId,
      members: [{ id: memberId, name: "Lan", hasAccount: true, isOwner: true, joinedAt: made.body.group.createdAt }],
      claims: [],
      bills: [],
      settlements: [],
      log: [{ at: made.body.group.createdAt, kind: "group_created", memberId, subjectId: id }],
    });
    expect(made.body.group.createdAt).toBeGreaterThanOrEqual(before);
    expect(await stateOf({ id, owner, ownerMemberId: memberId })).toEqual(made.body);

    const outsider = await api(await person(), "GET", `/v1/groups/${id}`);
    expect(outsider.status).toBe(403);
    expect(outsider.body).toEqual({ error: "forbidden" });
    expect((await api(owner, "GET", `/v1/groups/${crypto.randomUUID()}`)).body).toEqual({ error: "not_found" });
    expect((await api(owner, "GET", "/v1/groups/not-a-uuid")).status).toBe(404);
  });

  it("made again with the same id answers as the first time, and an id someone else uses is refused", async () => {
    const owner = await person();
    const body = { id: crypto.randomUUID(), name: "Trip", currency: THB, memberId: crypto.randomUUID(), memberName: "Owner" };
    const first = await api<GroupState>(owner, "POST", "/v1/groups", body);

    const again = await api<GroupState>(owner, "POST", "/v1/groups", { ...body, name: "Another name" });
    const someoneElse = await api(await person(), "POST", "/v1/groups", { ...body, memberId: crypto.randomUUID() });

    expect(again.status).toBe(201);
    expect(again.body).toEqual(first.body);
    expect(someoneElse.status).toBe(409);
    expect(someoneElse.body).toEqual({ error: "conflict" });
    expect(await rows("SELECT id FROM members")).toHaveLength(1);
  });

  it("refuses a body that does not follow the contract", async () => {
    const owner = await person();
    const good = { id: crypto.randomUUID(), name: "Trip", currency: THB, memberId: crypto.randomUUID(), memberName: "Owner" };
    const bad: Array<Record<string, unknown>> = [
      { ...good, id: "not-a-uuid" },
      { ...good, name: "   " },
      { ...good, name: "x".repeat(61) },
      { ...good, name: "Line\nbreak" },
      { ...good, name: 42 },
      { ...good, currency: { code: "thb", fractionDigits: 2 } },
      { ...good, currency: { code: "THB", fractionDigits: 4 } },
      { ...good, currency: "THB" },
      { ...good, memberId: undefined },
      { ...good, memberName: "" },
    ];

    for (const body of bad) {
      const sent = await api(owner, "POST", "/v1/groups", body);
      expect(sent.status, JSON.stringify(body)).toBe(400);
      expect(sent.body).toEqual({ error: "input" });
    }
    expect((await api(owner, "POST", "/v1/groups", "not json")).status).toBe(400);
    expect((await api(owner, "POST", "/v1/groups", { ...good, name: "x".repeat(20_000) })).status).toBe(413);
    // Sixty characters, counted as characters rather than UTF-16 units.
    expect((await api(owner, "POST", "/v1/groups", { ...good, name: "🍜".repeat(60) })).status).toBe(201);
  });

  it("is listed for its members, and for someone whose claim waits as pending", async () => {
    const owner = await person();
    const group = await newGroup(owner, "Flat 4B", VND);
    const placeholder = await addPlaceholder(group, "Minh");
    const claimant = await person();
    await api(claimant, "POST", `/v1/invites/${await invite(group)}/join`, { claimMemberId: placeholder, name: "Minh N." });

    const mine = await api<{ groups: unknown[] }>(owner, "GET", "/v1/groups");
    const waiting = await api<{ groups: unknown[] }>(claimant, "GET", "/v1/groups");
    const stranger = await api<{ groups: unknown[] }>(await person(), "GET", "/v1/groups");

    const listed = { id: group.id, name: "Flat 4B", currency: VND, revision: 3 };
    expect(mine.body).toEqual({ groups: [{ ...listed, myMemberId: group.ownerMemberId, status: "member" }] });
    expect(waiting.body).toEqual({ groups: [{ ...listed, myMemberId: null, status: "pending" }] });
    expect(stranger.body).toEqual({ groups: [] });
  });

  it("is renamed by its owner only, and every change moves its revision on by one", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    await join(group, member);

    const byMember = await api(member, "PATCH", `/v1/groups/${group.id}`, { name: "Mine now" });
    const byOwner = await api<GroupState>(owner, "PATCH", `/v1/groups/${group.id}`, { name: "Chiang Mai, again" });

    expect(byMember.status).toBe(403);
    expect(byMember.body).toEqual({ error: "forbidden" });
    expect(byOwner.status).toBe(200);
    expect(byOwner.body.group).toMatchObject({ name: "Chiang Mai, again", revision: 3 });
    expect((await stateOf(group, member)).group.name).toBe("Chiang Mai, again");
  });

  it("refuses an outsider everything, and someone whose claim waits is told so", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const placeholder = await addPlaceholder(group, "Minh");
    const billId = await addBill(group, owner, { [group.ownerMemberId]: 100 }, { [group.ownerMemberId]: 100 });
    const claimant = await person();
    await api(claimant, "POST", `/v1/invites/${await invite(group)}/join`, { claimMemberId: placeholder, name: "Minh" });
    const before = await stateOf(group);
    const path = `/v1/groups/${group.id}`;
    const attempts: Array<[string, string, unknown?]> = [
      ["GET", path],
      ["PATCH", path, { name: "Taken" }],
      ["POST", `${path}/members`, { id: crypto.randomUUID(), name: "Extra" }],
      ["PATCH", `${path}/members/${placeholder}`, { name: "Renamed" }],
      ["POST", `${path}/leave`],
      ["POST", `${path}/invites`],
      ["DELETE", `${path}/invites`],
      ["POST", `${path}/claims/${placeholder}/approve`],
      ["POST", `${path}/claims/${placeholder}/reject`],
      ["PUT", `${path}/bills/${billId}`, { expectedRevision: 1, document: {}, conversion: null, totalMinor: 0, shares: {}, paid: {} }],
      ["DELETE", `${path}/bills/${billId}`],
      ["POST", `${path}/settlements`, { id: crypto.randomUUID(), fromMemberId: group.ownerMemberId, amountMinor: 100 }],
    ];

    for (const [who, caller, code] of [
      ["outsider", await person(), "forbidden"],
      ["claimant", claimant, "pending"],
    ] as const) {
      for (const [method, route, body] of attempts) {
        const sent = await api(caller, method, route, body);
        expect(sent.status, `${who} ${method} ${route}`).toBe(403);
        expect(sent.body).toEqual({ error: code });
      }
    }
    expect(await stateOf(group)).toEqual(before);
  });

  it("is open on every route to a session only", async () => {
    const group = await newGroup(await person());
    const id = crypto.randomUUID();
    const routes: Array<[string, string]> = [
      ["GET", "/v1/groups"],
      ["POST", "/v1/groups"],
      ["GET", `/v1/groups/${group.id}`],
      ["PATCH", `/v1/groups/${group.id}`],
      ["POST", `/v1/groups/${group.id}/members`],
      ["PATCH", `/v1/groups/${group.id}/members/${id}`],
      ["POST", `/v1/groups/${group.id}/leave`],
      ["POST", `/v1/groups/${group.id}/invites`],
      ["DELETE", `/v1/groups/${group.id}/invites`],
      ["POST", `/v1/groups/${group.id}/claims/${id}/approve`],
      ["POST", `/v1/groups/${group.id}/claims/${id}/reject`],
      ["PUT", `/v1/groups/${group.id}/bills/${id}`],
      ["DELETE", `/v1/groups/${group.id}/bills/${id}`],
      ["POST", `/v1/groups/${group.id}/settlements`],
      ["GET", "/v1/invites/AAAAAAAAAAAAAAAAAAAAAA"],
      ["POST", "/v1/invites/AAAAAAAAAAAAAAAAAAAAAA/join"],
      ["GET", "/v1/requests"],
      ["POST", "/v1/requests"],
      ["PATCH", `/v1/requests/${id}`],
      ["DELETE", `/v1/requests/${id}`],
      ["POST", `/v1/requests/${id}/reported-paid`],
      ["POST", `/v1/requests/${id}/confirmed-paid`],
      ["GET", "/v1/request-links/AAAAAAAAAAAAAAAAAAAAAA"],
      ["POST", "/v1/request-links/AAAAAAAAAAAAAAAAAAAAAA/accept"],
      ["PUT", `/v1/devices/${id}`],
      ["DELETE", `/v1/devices/${id}`],
    ];

    for (const [method, path] of routes) {
      for (const authorization of [null, "Bearer not-a-session", "Basic dXNlcjpwYXNz"]) {
        const headers = new Headers({ "content-type": "application/json", "X-SplitVibe-Install": crypto.randomUUID(), "X-SplitVibe-Version": "1" });
        if (authorization !== null) headers.set("authorization", authorization);
        const response = await send(new Request(`https://proxy.test${path}`, { method, headers, body: method === "GET" ? undefined : "{}" }));
        expect(response.status, `${method} ${path} with ${authorization}`).toBe(401);
        expect(JSON.parse(response.body)).toEqual({ error: "session" });
      }
    }
  });
});

describe("a member", () => {
  it("is added as a placeholder by any member, and the same id again changes nothing", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    const memberId = await join(group, member);
    const id = crypto.randomUUID();

    const added = await api<GroupState>(member, "POST", `/v1/groups/${group.id}/members`, { id, name: "Minh" });
    const again = await api<GroupState>(member, "POST", `/v1/groups/${group.id}/members`, { id, name: "Minh" });

    expect(added.status).toBe(200);
    expect(added.body.members.find((each) => each.id === id)).toEqual(memberView(id, "Minh", false));
    expect(added.body.log[0]).toEqual({ at: expect.any(Number), kind: "member_added", memberId, subjectId: id });
    expect(again.status).toBe(200);
    expect(again.body).toEqual(added.body);
  });

  it("is renamed by themself or by the owner, and by nobody else", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    const memberId = await join(group, member, "Lan");
    const placeholder = await addPlaceholder(group, "Minh");
    const path = (id: string) => `/v1/groups/${group.id}/members/${id}`;

    const self = await api<GroupState>(member, "PATCH", path(memberId), { name: "Lan Anh" });
    const other = await api(member, "PATCH", path(placeholder), { name: "Minh Tran" });
    const ownerRenames = await api<GroupState>(owner, "PATCH", path(placeholder), { name: "Minh Tran" });
    const ownerRenamesMember = await api<GroupState>(owner, "PATCH", path(memberId), { name: "Lan A." });
    const unknown = await api(owner, "PATCH", path(crypto.randomUUID()), { name: "Nobody" });

    expect(self.status).toBe(200);
    expect(self.body.members.find((each) => each.id === memberId)?.name).toBe("Lan Anh");
    expect(self.body.log[0]).toEqual({ at: expect.any(Number), kind: "member_renamed", memberId, subjectId: memberId });
    expect(other.status).toBe(403);
    expect(ownerRenames.body.members.find((each) => each.id === placeholder)?.name).toBe("Minh Tran");
    expect(ownerRenamesMember.body.members.find((each) => each.id === memberId)?.name).toBe("Lan A.");
    expect(unknown.status).toBe(404);
    expect((await api(owner, "PATCH", path(memberId), { name: "" })).status).toBe(400);
  });

  it("who leaves stays as a placeholder with their name and amounts, and reads the group no more", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    const memberId = await join(group, member, "Lan");
    await addBill(group, member, { [memberId]: 600, [group.ownerMemberId]: 400 }, { [memberId]: 1000 });
    const before = await stateOf(group);

    const left = await api(member, "POST", `/v1/groups/${group.id}/leave`);

    expect(left.status).toBe(204);
    expect(left.body).toBeNull();
    expect(left.headers.get("cache-control")).toBe("no-store");
    const after = await stateOf(group);
    expect(after.members.find((each) => each.id === memberId)).toEqual(memberView(memberId, "Lan", false));
    expect(after.bills).toEqual(before.bills);
    expect(after.log[0]).toEqual({ at: expect.any(Number), kind: "member_left", memberId, subjectId: memberId });
    expect(after.group.revision).toBe(before.group.revision + 1);
    expect((await api(member, "GET", `/v1/groups/${group.id}`)).status).toBe(403);
    expect((await api<{ groups: unknown[] }>(member, "GET", "/v1/groups")).body.groups).toEqual([]);
    expect((await api(member, "POST", `/v1/groups/${group.id}/leave`)).status).toBe(403);
  });

  it("who owns the group and leaves hands it to the member with an account who joined first", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    await addPlaceholder(group, "Minh");
    const first = await person();
    const firstId = await join(group, first, "First");
    await join(group, await person(), "Second");

    expect((await api(owner, "POST", `/v1/groups/${group.id}/leave`)).status).toBe(204);

    const state = await stateOf(group, first);
    expect(state.group.ownerMemberId).toBe(firstId);
    expect(state.members.filter((member) => member.isOwner).map((member) => member.id)).toEqual([firstId]);
    expect((await api(first, "PATCH", `/v1/groups/${group.id}`, { name: "Ours" })).status).toBe(200);
  });

  it("who is the last with an account and leaves deletes the group and everything in it", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const placeholder = await addPlaceholder(group, "Minh");
    await addBill(group, owner, { [placeholder]: 500 }, { [group.ownerMemberId]: 500 });
    await api(owner, "POST", `/v1/groups/${group.id}/settlements`, { id: crypto.randomUUID(), fromMemberId: placeholder, amountMinor: 500 });
    const token = await invite(group);
    await api(await person(), "POST", `/v1/invites/${token}/join`, { claimMemberId: placeholder, name: "Minh" });

    expect((await api(owner, "POST", `/v1/groups/${group.id}/leave`)).status).toBe(204);

    expect((await api(owner, "GET", `/v1/groups/${group.id}`)).status).toBe(404);
    for (const table of ["groups", "members", "claims", "invites", "group_bills", "settlements", "group_log"]) {
      expect(await rows(`SELECT * FROM ${table}`), table).toEqual([]);
    }
    expect((await api(await person(), "GET", `/v1/invites/${token}`)).status).toBe(404);
  });
});

describe("the size of a group", () => {
  it("stops at 100 members, placeholders included", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    await env.DB.prepare(
      `WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?2)
       INSERT INTO members (group_id, id, name, account, joined_at) SELECT ?1, lower(hex(randomblob(16))), 'Filler', NULL, 1 FROM n`,
    )
      .bind(group.id, POLICY.maxMembers - 1)
      .run();

    const added = await api(owner, "POST", `/v1/groups/${group.id}/members`, { id: crypto.randomUUID(), name: "One too many" });
    const joined = await api(await person(), "POST", `/v1/invites/${await invite(group)}/join`, { memberId: crypto.randomUUID(), name: "Late" });

    expect(added.status).toBe(413);
    expect(added.body).toEqual({ error: "too_large" });
    expect(joined.status).toBe(413);
  });

  it("stops at 1,000 bills, and an account at 100 groups", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    await env.DB.prepare(
      `WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?3)
       INSERT INTO group_bills (group_id, id, owner_member_id, revision, created_at, updated_at, document, conversion, total_minor, shares, paid)
       SELECT ?1, lower(hex(randomblob(16))), ?2, 1, 1, 1, '{}', NULL, 0, '{}', '{}' FROM n`,
    )
      .bind(group.id, group.ownerMemberId, POLICY.maxBills)
      .run();
    const bill = { expectedRevision: 0, document: {}, conversion: null, totalMinor: 0, shares: {}, paid: {} };

    expect((await api(owner, "PUT", `/v1/groups/${group.id}/bills/${crypto.randomUUID()}`, bill)).status).toBe(413);

    // The owner is in one group already; these make it a hundred.
    await env.DB.batch([
      env.DB.prepare(
        `WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?1)
         INSERT INTO groups (id, name, currency_code, currency_digits, owner_member_id, revision, created_at)
         SELECT 'g' || i, 'Filler', 'THB', 2, 'm' || i, 1, 1 FROM n`,
      ).bind(POLICY.maxGroupsPerAccount - 1),
      env.DB.prepare(
        `WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?1)
         INSERT INTO members (group_id, id, name, account, joined_at) SELECT 'g' || i, 'm' || i, 'Owner', ?2, 1 FROM n`,
      ).bind(POLICY.maxGroupsPerAccount - 1, owner.account),
    ]);
    const oneMore = await api(owner, "POST", "/v1/groups", {
      id: crypto.randomUUID(),
      name: "Too many",
      currency: THB,
      memberId: crypto.randomUUID(),
      memberName: "Owner",
    });
    expect(oneMore.status).toBe(413);
  });
});
