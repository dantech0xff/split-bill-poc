import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import type { GroupState } from "../src/group-state";
import { POLICY } from "../src/policy";
import { addBill, addPlaceholder, api, invite, join, memberView, newGroup, person, rows, sha256, stateOf, THB } from "./shared";

type Invite = { token: string; url: string; expiresAt: number };
type InviteView = { group: { id: string; name: string; currency: unknown }; placeholders: Array<{ id: string; name: string }>; alreadyMember: boolean };

describe("an invite's web page", () => {
  it("shows, without a session, the name and currency of the group, and nothing of its members", async () => {
    const owner = await person();
    const group = await newGroup(owner, "Flat 4B");
    await addPlaceholder(group, "Minh Placeholder");
    const token = await invite(group);

    const seen = await api(null, "POST", "/v1/invites/preview", { token });

    expect(seen.status).toBe(200);
    expect(seen.headers.get("cache-control")).toBe("no-store");
    expect(seen.body).toEqual({ group: { name: "Flat 4B", currency: THB } });
    expect(JSON.stringify(seen.body)).not.toMatch(new RegExp(`${group.id}|${owner.account}|Placeholder|Owner`));
  });

  it("answers 404 for a token never made, and 410 once the invite has expired or was revoked", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const preview = (token: unknown) => api(null, "POST", "/v1/invites/preview", { token });

    for (const token of ["AAAAAAAAAAAAAAAAAAAAAA", "too-short"]) expect((await preview(token)).body).toEqual({ error: "not_found" });
    expect((await preview(42)).body).toEqual({ error: "input" });
    const expiring = await invite(group);
    await env.DB.prepare("UPDATE invites SET expires_at = ?2 WHERE token_hash = ?1").bind(await sha256(expiring), Date.now() - 1).run();
    expect((await preview(expiring)).body).toEqual({ error: "gone" });
    const revoked = await invite(group);
    await api(owner, "DELETE", `/v1/groups/${group.id}/invites`);
    expect((await preview(revoked)).status).toBe(410);
  });
});

describe("an invite", () => {
  it("is made by any member, lasts 14 days, and is kept only as a hash", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    await join(group, member);
    const before = Date.now();

    const made = await api<Invite>(member, "POST", `/v1/groups/${group.id}/invites`);

    expect(made.status).toBe(201);
    expect(made.body.token).toMatch(/^[A-Za-z0-9_-]{22}$/);
    expect(made.body.url).toBe(`https://proxy.test/g/#${made.body.token}`);
    expect(made.body.expiresAt).toBeGreaterThanOrEqual(before + POLICY.inviteMs);
    expect(made.body.expiresAt).toBeLessThanOrEqual(Date.now() + POLICY.inviteMs);
    const kept = await rows<{ token_hash: string }>("SELECT token_hash FROM invites WHERE group_id = ?1 ORDER BY created_at DESC LIMIT 1", group.id);
    expect(kept).toEqual([{ token_hash: await sha256(made.body.token) }]);
    expect(JSON.stringify(await rows("SELECT * FROM invites"))).not.toContain(made.body.token);
  });

  it("shows the group and its placeholders, and lets a new member join", async () => {
    const owner = await person();
    const group = await newGroup(owner, "Flat 4B");
    const placeholder = await addPlaceholder(group, "Minh");
    const token = await invite(group);
    const joiner = await person();
    const memberId = crypto.randomUUID();

    const seen = await api<InviteView>(joiner, "POST", "/v1/invites/open", { token });
    const joined = await api<GroupState>(joiner, "POST", "/v1/invites/join", { token, memberId, name: " Lan " });

    expect(seen.status).toBe(200);
    expect(seen.body).toEqual({
      group: { id: group.id, name: "Flat 4B", currency: THB },
      placeholders: [{ id: placeholder, name: "Minh" }],
      alreadyMember: false,
    });
    expect(joined.status).toBe(200);
    expect(joined.body.myMemberId).toBe(memberId);
    expect(joined.body.members.find((member) => member.id === memberId)).toEqual(memberView(memberId, "Lan", true));
    expect(joined.body.log[0]).toEqual({ at: expect.any(Number), kind: "member_joined", memberId, subjectId: memberId });
    expect((await api<InviteView>(joiner, "POST", "/v1/invites/open", { token })).body.alreadyMember).toBe(true);
  });

  it("taken again by a member gives the group as it is, whatever they ask for", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const placeholder = await addPlaceholder(group, "Minh");
    const member = await person();
    await join(group, member);
    const token = await invite(group);
    const before = await stateOf(group, member);

    const asNew = await api<GroupState>(member, "POST", "/v1/invites/join", { token, memberId: crypto.randomUUID(), name: "Again" });
    const asClaim = await api<GroupState>(member, "POST", "/v1/invites/join", { token, claimMemberId: placeholder, name: "Minh" });

    expect(asNew.status).toBe(200);
    expect(asNew.body).toEqual(before);
    expect(asClaim.status).toBe(200);
    expect(asClaim.body).toEqual(before);
    expect(await rows("SELECT * FROM claims")).toEqual([]);
  });

  it("refuses a new member under an id the group already has, and a body that names both or neither", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const placeholder = await addPlaceholder(group, "Minh");
    const token = await invite(group);
    const joiner = await person();

    const taken = await api(joiner, "POST", "/v1/invites/join", { token, memberId: placeholder, name: "Minh" });

    expect(taken.status).toBe(409);
    for (const body of [
      { memberId: crypto.randomUUID(), claimMemberId: placeholder, name: "Both" },
      { name: "Neither" },
      { memberId: crypto.randomUUID() },
      { memberId: "not-a-uuid", name: "Bad" },
    ]) {
      expect((await api(joiner, "POST", "/v1/invites/join", { token, ...body })).status, JSON.stringify(body)).toBe(400);
    }
  });

  it("answers 404 for a token never made, and 410 once it has expired or was revoked", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const joiner = await person();
    const join = (token: string) => api(joiner, "POST", "/v1/invites/join", { token, memberId: crypto.randomUUID(), name: "Late" });

    for (const token of ["AAAAAAAAAAAAAAAAAAAAAA", "too-short", "with+plus=equals______"]) {
      expect((await api(joiner, "POST", "/v1/invites/open", { token })).body).toEqual({ error: "not_found" });
      expect((await join(token)).body).toEqual({ error: "not_found" });
    }
    for (const body of [{}, { token: 42 }]) {
      expect((await api(joiner, "POST", "/v1/invites/open", body)).body).toEqual({ error: "input" });
      expect((await api(joiner, "POST", "/v1/invites/join", { ...body, memberId: crypto.randomUUID(), name: "Late" })).status).toBe(400);
    }

    const expiring = await invite(group);
    await env.DB.prepare("UPDATE invites SET expires_at = ?2 WHERE token_hash = ?1").bind(await sha256(expiring), Date.now() - 1).run();
    expect((await api(joiner, "POST", "/v1/invites/open", { token: expiring })).body).toEqual({ error: "gone" });
    expect((await join(expiring)).status).toBe(410);

    const first = await invite(group);
    const second = await invite(group);
    const revoked = await api(owner, "DELETE", `/v1/groups/${group.id}/invites`);
    expect(revoked.status).toBe(204);
    for (const token of [first, second]) {
      expect((await api(joiner, "POST", "/v1/invites/open", { token })).status).toBe(410);
      expect((await join(token)).status).toBe(410);
    }

    expect((await join(await invite(group))).status).toBe(200);
  });

  it("is revoked by the owner only", async () => {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    await join(group, member);
    const token = await invite(group);

    expect((await api(member, "DELETE", `/v1/groups/${group.id}/invites`)).body).toEqual({ error: "forbidden" });
    expect((await api(await person(), "DELETE", `/v1/groups/${group.id}/invites`)).status).toBe(403);
    expect((await api(await person(), "POST", "/v1/invites/open", { token })).status).toBe(200);
  });

  it("is made at most 20 times an hour by one account, over all its groups", async () => {
    const owner = await person();
    const first = await newGroup(owner);
    const second = await newGroup(owner);
    for (let made = 0; made < POLICY.invitesPerHour; made++) {
      expect((await api(owner, "POST", `/v1/groups/${(made % 2 === 0 ? first : second).id}/invites`)).status).toBe(201);
    }

    const refused = await api(owner, "POST", `/v1/groups/${second.id}/invites`);

    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    const wait = Number(refused.headers.get("retry-after"));
    expect(wait).toBeGreaterThan(3_500);
    expect(wait).toBeLessThanOrEqual(3_600);
    expect(await rows("SELECT * FROM invites")).toHaveLength(POLICY.invitesPerHour);

    // Another member is counted apart, and the owner's window starts over an hour later.
    const other = await person();
    await env.DB.prepare("INSERT INTO members (group_id, id, name, account, joined_at) VALUES (?1, ?2, 'Other', ?3, 1)")
      .bind(first.id, crypto.randomUUID(), other.account)
      .run();
    expect((await api(other, "POST", `/v1/groups/${first.id}/invites`)).status).toBe(201);
    await env.DB.prepare("UPDATE rate_windows SET started_at = started_at - ?2 WHERE account = ?1").bind(owner.account, POLICY.hourMs).run();
    expect((await api(owner, "POST", `/v1/groups/${first.id}/invites`)).status).toBe(201);
  });
});

describe("a claim", () => {
  async function claimed() {
    const owner = await person();
    const group = await newGroup(owner);
    const member = await person();
    const memberId = await join(group, member, "Lan");
    const placeholder = await addPlaceholder(group, "Minh");
    await addBill(group, owner, { [placeholder]: 700, [group.ownerMemberId]: 300 }, { [group.ownerMemberId]: 1000 });
    const token = await invite(group);
    const claimant = await person();
    const asked = await api(claimant, "POST", "/v1/invites/join", { token, claimMemberId: placeholder, name: "Minh N." });
    return { owner, group, member, memberId, placeholder, token, claimant, asked };
  }

  it("waits for the owner, who alone sees it, while the claimant is told it is pending", async () => {
    const { owner, group, member, placeholder, claimant, asked } = await claimed();

    expect(asked.status).toBe(202);
    expect(asked.body).toEqual({ status: "pending" });
    const ownerView = await stateOf(group, owner);
    expect(ownerView.claims).toEqual([{ memberId: placeholder, claimantName: "Minh N.", requestedAt: expect.any(Number) }]);
    expect(ownerView.log[0]).toEqual({ at: expect.any(Number), kind: "claim_requested", memberId: placeholder, subjectId: placeholder });
    expect((await stateOf(group, member)).claims).toEqual([]);
    expect((await api(claimant, "GET", `/v1/groups/${group.id}`)).body).toEqual({ error: "pending" });
    expect(JSON.stringify(ownerView)).not.toContain(claimant.account);
  });

  it("approved, gives the placeholder the claimant's account and changes nothing else", async () => {
    const { owner, group, placeholder, claimant } = await claimed();
    const before = await stateOf(group, owner);

    const approved = await api<GroupState>(owner, "POST", `/v1/groups/${group.id}/claims/${placeholder}/approve`);

    expect(approved.status).toBe(200);
    expect(approved.body.claims).toEqual([]);
    expect(approved.body.members.find((member) => member.id === placeholder)).toEqual(memberView(placeholder, "Minh", true));
    expect(approved.body.bills).toEqual(before.bills);
    expect(approved.body.log[0]).toEqual({ at: expect.any(Number), kind: "claim_approved", memberId: group.ownerMemberId, subjectId: placeholder });
    const claimantView = await stateOf(group, claimant);
    expect(claimantView.myMemberId).toBe(placeholder);
    expect((await api<{ groups: Array<{ status: string }> }>(claimant, "GET", "/v1/groups")).body.groups.map((listed) => listed.status)).toEqual(["member"]);
  });

  it("rejected, is dropped, and the placeholder stays as it was", async () => {
    const { owner, group, placeholder, claimant, token } = await claimed();

    const rejected = await api<GroupState>(owner, "POST", `/v1/groups/${group.id}/claims/${placeholder}/reject`);

    expect(rejected.status).toBe(200);
    expect(rejected.body.claims).toEqual([]);
    expect(rejected.body.members.find((member) => member.id === placeholder)?.hasAccount).toBe(false);
    expect(rejected.body.log[0]).toEqual({ at: expect.any(Number), kind: "claim_rejected", memberId: group.ownerMemberId, subjectId: placeholder });
    expect((await api(claimant, "GET", `/v1/groups/${group.id}`)).body).toEqual({ error: "forbidden" });
    expect((await api<{ groups: unknown[] }>(claimant, "GET", "/v1/groups")).body.groups).toEqual([]);
    expect((await api(claimant, "POST", "/v1/invites/join", { token, claimMemberId: placeholder, name: "Minh" })).status).toBe(202);
  });

  it("is decided by the owner only", async () => {
    const { group, member, placeholder, claimant } = await claimed();
    const path = (decision: string) => `/v1/groups/${group.id}/claims/${placeholder}/${decision}`;

    for (const decision of ["approve", "reject"]) {
      expect((await api(member, "POST", path(decision))).body).toEqual({ error: "forbidden" });
      expect((await api(claimant, "POST", path(decision))).body).toEqual({ error: "pending" });
      expect((await api(await person(), "POST", path(decision))).body).toEqual({ error: "forbidden" });
    }
    expect((await api(group.owner, "POST", `/v1/groups/${group.id}/claims/${crypto.randomUUID()}/approve`)).status).toBe(404);
    expect((await stateOf(group)).claims).toHaveLength(1);
  });

  it("waits one at a time on a placeholder, one per account in a group, and never on a member with an account", async () => {
    const { group, memberId, placeholder, token, claimant } = await claimed();
    const second = await addPlaceholder(group, "Hoa");
    const ask = (who: typeof claimant, claimMemberId: string) => api(who, "POST", "/v1/invites/join", { token, claimMemberId, name: "Someone" });
    const other = await person();

    expect((await ask(other, placeholder)).status).toBe(409);
    expect((await ask(other, memberId)).status).toBe(409);
    expect((await ask(other, group.ownerMemberId)).status).toBe(409);
    expect((await ask(other, crypto.randomUUID())).status).toBe(404);
    expect((await ask(claimant, placeholder)).status).toBe(202);
    expect((await ask(claimant, second)).status).toBe(409);
    expect((await ask(other, second)).status).toBe(202);

    const claims = (await stateOf(group)).claims;
    expect(claims.map((claim) => claim.memberId).sort()).toEqual([placeholder, second].sort());
    expect((await stateOf(group)).log.filter((entry) => entry.kind === "claim_requested")).toHaveLength(2);
  });

  it("is dropped when its claimant joins as a new member instead", async () => {
    const { group, token, claimant } = await claimed();
    const memberId = crypto.randomUUID();

    const joined = await api<GroupState>(claimant, "POST", "/v1/invites/join", { token, memberId, name: "Minh" });

    expect(joined.status).toBe(200);
    expect(joined.body.myMemberId).toBe(memberId);
    expect((await stateOf(group)).claims).toEqual([]);
  });
});
