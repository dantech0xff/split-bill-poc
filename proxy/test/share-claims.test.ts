import { env } from "cloudflare:workers";
import { HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { hashed, ipKey } from "../src/ledger";
import { POLICY } from "../src/policy";
import type { ShareView } from "../src/share-state";
import { FAILING_SECRET, fakeTurnstile, PASSING_SECRET } from "./fake-turnstile";
import { billRequest, ipName, model, ANSWER, proxyRequest, send } from "./requests";
import { billProjection, changeClaim, type Claimed, fromPage, fromPageAt, newClaim, openShare, payPal, publish, windowOf } from "./share-fixtures";
import { type Answer, api, invite, linkToken, newGroup, person, rows, sha256, VND } from "./shared";

/** What the Worker logged while [run] ran, one entry per line. */
async function logsOf(run: () => Promise<unknown>): Promise<string[]> {
  const spied = vi.spyOn(console, "error").mockImplementation(() => undefined);
  try {
    await run();
    return spied.mock.calls.map((call) => call.map(String).join(" "));
  } finally {
    spied.mockRestore();
  }
}

type Viewed = { share: ShareView };

/** The same database, but [before] runs once, just before the first batch whose SQL [matches] says it is the one. */
function racing(matches: (sql: string[]) => boolean, before: () => Promise<void>): D1Database {
  let ran = false;
  return new Proxy(env.DB, {
    get(target, property) {
      if (property !== "batch") {
        const value: unknown = Reflect.get(target, property);
        return typeof value === "function" ? value.bind(target) : value;
      }
      return async (statements: D1PreparedStatement[]) => {
        // A prepared statement keeps its SQL as `statement`; the test reads it to find the batch to race.
        const sql = statements.map((statement) => (statement as unknown as { statement: string }).statement);
        if (!ran && matches(sql)) {
          ran = true;
          await before();
        }
        return target.batch(statements);
      };
    },
  });
}

/** A new claim on the link [token], with [turnstile] as its Turnstile token. */
function newClaimWith(token: string, turnstile: string) {
  return fromPage<Claimed>("/v1/shares/claim", { token, name: "Minh", itemIds: ["i1"], turnstile });
}

const isLock = (sql: string[]) => sql.some((text) => text.includes("status = 'locking'"));
const isNewClaim = (sql: string[]) => sql.some((text) => text.startsWith("INSERT INTO share_claims"));
const isClaimChange = (sql: string[]) => sql.some((text) => text.startsWith("UPDATE share_claims SET name"));

describe("a new claim", () => {
  it("needs a Turnstile token that Cloudflare passed for the action claim on this host, once", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const claim = (body: Record<string, unknown>) => fromPage<Claimed>("/v1/shares/claim", { token: link.token, name: "Minh", itemIds: ["i1"], ...body });
    const spent = turnstile.token();
    expect((await claim({ turnstile: spent })).status).toBe(200);
    const checksBefore = turnstile.checks();

    for (const body of [{}, { turnstile: "" }, { turnstile: 42 }, { turnstile: "x".repeat(2_049) }]) {
      const refused = await claim(body);
      expect(refused.status, JSON.stringify(body).slice(0, 60)).toBe(403);
      expect(refused.body).toEqual({ error: "turnstile" });
    }
    // Refused without asking Cloudflare.
    expect(turnstile.checks()).toBe(checksBefore);
    for (const token of ["not-from-the-widget", spent, turnstile.token({ action: "login" }), turnstile.token({ hostname: "evil.example" })]) {
      const refused = await claim({ turnstile: token });
      expect(refused.status).toBe(403);
      expect(refused.body).toEqual({ error: "turnstile" });
    }
    expect(turnstile.checks()).toBe(checksBefore + 4);
    expect(await rows("SELECT name FROM share_claims")).toEqual([{ name: "Minh" }]);
  });

  it("answers with the link and the claim's key, which the browser keeps and of which only the hash is kept", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();

    const made = await fromPage<Claimed>("/v1/shares/claim", { token: link.token, name: "  Minh  ", itemIds: ["i1", "i3"], turnstile: turnstile.token() });

    expect(made.status).toBe(200);
    expect(made.headers.get("cache-control")).toBe("no-store");
    expect(made.body.key).toMatch(/^[A-Za-z0-9_-]{22}$/);
    expect(made.body.you).toMatch(/^[0-9a-f-]{36}$/);
    expect(made.body.share.claims).toEqual([{ id: made.body.you, name: "Minh", itemIds: ["i1", "i3"] }]);
    expect(made.body.share.revision).toBe(link.share.revision + 1);
    const key = made.body.key ?? "";
    expect(await rows("SELECT key_hash FROM share_claims")).toEqual([{ key_hash: await sha256(key) }]);
    expect(JSON.stringify(await rows("SELECT * FROM share_claims"))).not.toContain(key);
    expect((await openShare(link.token, key)).body.you).toBe(made.body.you);
    expect((await openShare(link.token)).body.you).toBeNull();
  });

  it("is 503 without the secret TURNSTILE_SECRET, while a known key still changes its claim", async () => {
    const link = await publish(await person());
    const made = await newClaim(fakeTurnstile(), link.token, "Minh", ["i1"]);
    const off = { TURNSTILE_SECRET: undefined };
    const turnstile = fakeTurnstile();

    const refused = await fromPage("/v1/shares/claim", { token: link.token, name: "Hoa", itemIds: ["i2"], turnstile: turnstile.token() }, undefined, off);
    const changed = await fromPage<Claimed>("/v1/shares/claim", { token: link.token, key: made.body.key, name: "Minh", itemIds: ["i2"] }, undefined, off);

    expect(refused.status).toBe(503);
    expect(refused.body).toEqual({ error: "not_configured" });
    expect(changed.status).toBe(200);
    expect(turnstile.checks()).toBe(0);
  });

  it("takes Cloudflare's test secrets on a computer, where only their verdict counts", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const claim = (origin: string, secret: string) =>
      fromPageAt<Claimed>(origin, "/v1/shares/claim", { token: link.token, name: "Minh", itemIds: [], turnstile: "XXXX.DUMMY.TOKEN.XXXX" }, undefined, { TURNSTILE_SECRET: secret });

    for (const origin of ["http://localhost:8787", "http://127.0.0.1:8797", "http://[::1]:8787"]) {
      expect((await claim(origin, PASSING_SECRET)).status, origin).toBe(200);
      expect((await claim(origin, FAILING_SECRET)).body, origin).toEqual({ error: "turnstile" });
    }
    expect(turnstile.checks()).toBe(6);
  });

  it("is 503 with one of Cloudflare's test secrets on any other host, which is logged without the token", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const dummy = "XXXX.DUMMY.TOKEN.XXXX";
    const answers: Array<Answer<Claimed>> = [];

    const logs = await logsOf(async () => {
      for (const origin of ["https://proxy.test", "https://localhost.example", "https://127.0.0.1.example"]) {
        for (const secret of [PASSING_SECRET, FAILING_SECRET]) {
          answers.push(await fromPageAt<Claimed>(origin, "/v1/shares/claim", { token: link.token, name: "Minh", itemIds: [], turnstile: dummy }, undefined, { TURNSTILE_SECRET: secret }));
        }
      }
    });

    for (const answer of answers) {
      expect(answer.status).toBe(503);
      expect(answer.body).toEqual({ error: "not_configured" });
    }
    expect(turnstile.checks()).toBe(0);
    expect(logs).toEqual(Array.from({ length: answers.length }, () => JSON.stringify({ event: "turnstile_test_secret" })));
    expect(await rows("SELECT * FROM share_claims")).toEqual([]);
  });

  it("logs an action or a hostname that is not the claim's on this host, never the token", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const tokens = [turnstile.token({ action: "login" }), turnstile.token({ hostname: "evil.example" })];

    const logs = await logsOf(async () => {
      for (const token of tokens) expect((await newClaimWith(link.token, token)).body).toEqual({ error: "turnstile" });
    });

    expect(logs.map((line) => JSON.parse(line) as unknown)).toEqual([
      { event: "turnstile_mismatch", action: "login", hostname: "proxy.test", expectedHostname: "proxy.test" },
      { event: "turnstile_mismatch", action: "claim", hostname: "evil.example", expectedHostname: "proxy.test" },
    ]);
    for (const token of tokens) expect(logs.join("\n")).not.toContain(token);
  });

  it("sends Cloudflare the address the claim came from", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();

    await newClaim(turnstile, link.token, "Minh", ["i1"], "203.0.113.60");
    await newClaim(turnstile, link.token, "Hoa", ["i2"], "2001:db8::7");
    await newClaim(turnstile, link.token, "Tuan", ["i3"]);

    expect(turnstile.remoteIps()).toEqual(["203.0.113.60", "2001:db8::7", undefined]);
  });

  it("is refused when Cloudflare cannot say whether the token passed", async () => {
    const link = await publish(await person());
    for (const answer of [() => HttpResponse.json({ success: true }, { status: 500 }), () => new HttpResponse("not json"), () => HttpResponse.error()]) {
      const turnstile = fakeTurnstile(answer);
      const refused = await newClaim(turnstile, link.token, "Minh", ["i1"]);
      expect(refused.status).toBe(403);
      expect(refused.body).toEqual({ error: "turnstile" });
    }
    expect(await rows("SELECT * FROM share_claims")).toEqual([]);
  });
});

describe("a claim", () => {
  it("with a known key replaces its name and items without Turnstile, and sent again changes nothing", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const made = await newClaim(turnstile, link.token, "Minh", ["i1"]);
    const key = made.body.key ?? "";

    const changed = await changeClaim(link.token, key, "Minh N.", ["i2", "i3"]);
    const again = await changeClaim(link.token, key, "Minh N.", ["i2", "i3"]);
    const emptied = await changeClaim(link.token, key, "Minh N.", []);

    expect(changed.status).toBe(200);
    expect(changed.body).toEqual({ share: expect.any(Object), you: made.body.you });
    expect(changed.body.share.claims).toEqual([{ id: made.body.you, name: "Minh N.", itemIds: ["i2", "i3"] }]);
    expect(changed.body.share.revision).toBe(made.body.share.revision + 1);
    expect(again.body).toEqual(changed.body);
    expect(emptied.body.share.claims).toEqual([{ id: made.body.you, name: "Minh N.", itemIds: [] }]);
    expect(turnstile.checks()).toBe(1);
  });

  it("refuses a name over 40 characters, an item the bill does not have, and a body that does not follow the contract", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const claim = (body: Record<string, unknown>) => fromPage("/v1/shares/claim", { token: link.token, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token(), ...body });

    for (const body of [
      { name: "x".repeat(41) },
      { name: "" },
      { name: "   " },
      { name: "Minh\u0007" },
      { name: 42 },
      { itemIds: ["i9"] },
      { itemIds: ["i1", "i1"] },
      { itemIds: ["c1"] },
      { itemIds: [1] },
      { itemIds: "i1" },
      { itemIds: undefined },
      { token: undefined },
      { token: 42 },
    ]) {
      const refused = await claim(body);
      expect(refused.status, JSON.stringify(body)).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
    for (const token of ["AAAAAAAAAAAAAAAAAAAAAA", "too-short", "with+plus=equals______"]) {
      expect((await claim({ token })).body, token).toEqual({ error: "not_found" });
      expect((await openShare(token)).body, token).toEqual({ error: "not_found" });
    }
    const longest = await claim({ name: ` ${"x".repeat(40)} ` });
    expect(longest.status).toBe(200);
  });

  it("takes invisible formatting characters out of a name, such as bidi controls and zero-width spaces", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();

    const made = await newClaim(turnstile, link.token, "\u202eM\u200bi\u2066nh\u2069\ufeff", ["i1"]);
    const changed = await changeClaim(link.token, made.body.key ?? "", "\u200e Minh N. \u200f", ["i1"]);
    const nothingLeft = await newClaim(turnstile, link.token, "\u200b\u202e\u2060", ["i2"]);
    // Counted once they are out: 40 letters with format characters between them fit.
    const longest = await newClaim(turnstile, link.token, "x\u200b".repeat(40), ["i3"]);

    expect(made.body.share.claims.map((claim) => claim.name)).toEqual(["Minh"]);
    expect(changed.body.share.claims.map((claim) => claim.name)).toEqual(["Minh N."]);
    expect(nothingLeft.status).toBe(400);
    expect(nothingLeft.body).toEqual({ error: "input" });
    expect(longest.status).toBe(200);
    expect(await rows("SELECT name FROM share_claims ORDER BY created_at")).toEqual([{ name: "Minh N." }, { name: "x".repeat(40) }]);
  });

  it("keeps the zero-width joiner and non-joiner of a name, and refuses one with nothing else to show", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    // A family, three emoji joined by U+200D; and Alireza in Persian, written with U+200C.
    const family = "\u{1F468}‍\u{1F469}‍\u{1F467}";
    const alireza = "علی‌رضا";

    const persian = await newClaim(turnstile, link.token, alireza, ["i1"]);
    const emoji = await newClaim(turnstile, link.token, `‮Minh ${family}​`, ["i2"]);
    const joinersOnly = await newClaim(turnstile, link.token, "‍‌", ["i3"]);
    const withOthers = await newClaim(turnstile, link.token, " ‍ ​‮‌ ", ["i3"]);
    const renamed = await changeClaim(link.token, persian.body.key ?? "", "‌", ["i1"]);

    const names = new Map(emoji.body.share.claims.map((claim) => [claim.id, claim.name]));
    expect(names.get(persian.body.you)).toBe(alireza);
    expect(names.get(emoji.body.you)).toBe(`Minh ${family}`);
    for (const refused of [joinersOnly, withOthers, renamed]) {
      expect(refused.status).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
    expect((await rows<{ name: string }>("SELECT name FROM share_claims")).map((row) => row.name).sort()).toEqual([alireza, `Minh ${family}`].sort());
  });

  it("is one of 30 a link holds: the 31st is refused, and the 30 can still change", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    let last: Answer<Claimed> | null = null;
    for (let count = 0; count < POLICY.maxClaims; count++) {
      // A new IP whenever one has made as many new claims as it may in an hour.
      last = await newClaim(turnstile, link.token, `Guest ${count + 1}`, ["i1"], `198.51.100.${Math.floor(count / POLICY.newClaimsPerHour) + 1}`);
      expect(last.status, `claim ${count + 1}`).toBe(200);
    }

    const full = await newClaim(turnstile, link.token, "Guest 31", ["i1"], "198.51.100.99");

    expect(full.status).toBe(409);
    expect(full.body).toEqual({ error: "full" });
    expect(await rows("SELECT count(*) AS claims FROM share_claims")).toEqual([{ claims: POLICY.maxClaims }]);
    expect((await changeClaim(link.token, last?.body.key ?? "", "Guest 30", ["i2"])).status).toBe(200);
  });

  it("is refused once the link is locked, new or not, and taken again once it reopens", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();
    const made = await newClaim(turnstile, link.token, "Minh", ["i1"]);
    await api(owner, "POST", `/v1/shares/${link.id}/lock`);

    const changed = await changeClaim(link.token, made.body.key ?? "", "Minh", ["i2"]);
    const fresh = await newClaim(turnstile, link.token, "Hoa", ["i2"]);
    await api(owner, "POST", `/v1/shares/${link.id}/finalize`, {
      billRevision: 1,
      bill: billProjection(),
      amounts: { me: { amountMinor: 107_250, payment: null }, p1: { amountMinor: 107_250, payment: payPal(107_250) } },
    });
    const settled = await changeClaim(link.token, made.body.key ?? "", "Minh", ["i2"]);

    for (const refused of [changed, fresh, settled]) {
      expect(refused.status).toBe(409);
      expect(refused.body).toEqual({ error: "locked" });
    }
    await api(owner, "POST", `/v1/shares/${link.id}/reopen`);
    expect((await changeClaim(link.token, made.body.key ?? "", "Minh", ["i2"])).status).toBe(200);
  });
});

describe("a new claim sent again with the same attempt", () => {
  it("is the claim it made, with a new key, and needs neither Turnstile nor another place on the link", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const attempt = crypto.randomUUID();
    const ip = "203.0.113.80";

    const first = await newClaim(turnstile, link.token, "Minh", ["i1"], ip, attempt);
    const lost = first.body.key ?? "";
    const again = await fromPage<Claimed>("/v1/shares/claim", { token: link.token, attempt, name: "Minh", itemIds: ["i1", "i2"] }, ip);
    const withToken = await newClaim(turnstile, link.token, "Minh N.", ["i2"], ip, attempt.toUpperCase());

    expect(first.status).toBe(200);
    for (const answer of [again, withToken]) {
      expect(answer.status).toBe(200);
      expect(answer.body.you).toBe(first.body.you);
      expect(answer.body.key).toMatch(/^[A-Za-z0-9_-]{22}$/);
    }
    expect(new Set([lost, again.body.key, withToken.body.key]).size).toBe(3);
    expect(withToken.body.share.claims).toEqual([{ id: first.body.you, name: "Minh N.", itemIds: ["i2"] }]);
    expect(turnstile.checks()).toBe(1);
    expect((await windowOf(ip, "claim_new"))?.used).toBe(1);
    // Only the latest key opens the claim; only hashes are kept.
    expect((await openShare(link.token, lost)).body.you).toBeNull();
    expect((await openShare(link.token, again.body.key)).body.you).toBeNull();
    expect((await openShare(link.token, withToken.body.key)).body.you).toBe(first.body.you);
    const kept = await rows<{ key_hash: string; attempt_hash: string }>("SELECT key_hash, attempt_hash FROM share_claims");
    expect(kept).toEqual([{ key_hash: await sha256(withToken.body.key ?? ""), attempt_hash: await sha256(attempt) }]);
    expect(JSON.stringify(kept)).not.toContain(attempt);
  });

  it("finds its claim on a full link, and on that link only", async () => {
    const owner = await person();
    const link = await publish(owner);
    const other = await publish(owner);
    const turnstile = fakeTurnstile();
    const attempt = crypto.randomUUID();
    const first = await newClaim(turnstile, link.token, "Guest 0", ["i1"], "198.51.100.1", attempt);
    for (let count = 1; count < POLICY.maxClaims; count++) {
      expect((await newClaim(turnstile, link.token, `Guest ${count}`, ["i1"], `198.51.100.${Math.floor(count / POLICY.newClaimsPerHour) + 1}`)).status).toBe(200);
    }

    const again = await newClaim(turnstile, link.token, "Guest 0", ["i2"], "198.51.100.99", attempt);
    const elsewhere = await newClaim(turnstile, other.token, "Guest 0", ["i2"], "198.51.100.99", attempt);

    expect(again.status).toBe(200);
    expect(again.body.you).toBe(first.body.you);
    expect(again.body.share.claims).toHaveLength(POLICY.maxClaims);
    expect(elsewhere.status).toBe(200);
    expect(elsewhere.body.you).not.toBe(first.body.you);
    expect(elsewhere.body.share.claims).toHaveLength(1);
  });

  it("sent twice at once makes one claim", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const attempt = crypto.randomUUID();
    const raced: { second?: Answer<Claimed> } = {};
    const db = racing(isNewClaim, async () => {
      raced.second = await newClaim(turnstile, link.token, "Minh", ["i1"], undefined, attempt);
    });

    const first = await fromPage<Claimed>("/v1/shares/claim", { token: link.token, attempt, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token() }, undefined, { DB: db });

    expect(first.status).toBe(200);
    expect(raced.second?.status).toBe(200);
    expect(first.body.you).toBe(raced.second?.body.you);
    expect(await rows("SELECT count(*) AS claims FROM share_claims")).toEqual([{ claims: 1 }]);
    // The claim that landed second took the claim over with a key of its own.
    expect((await openShare(link.token, first.body.key)).body.you).toBe(first.body.you);
    expect((await openShare(link.token, raced.second?.body.key)).body.you).toBeNull();
  });

  it("is refused with an attempt that is not a UUID", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    for (const attempt of ["not-a-uuid", 42, "", {}]) {
      const refused = await fromPage("/v1/shares/claim", { token: link.token, attempt, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token() });
      expect(refused.status, JSON.stringify(attempt)).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
    expect((await fromPage("/v1/shares/claim", { token: link.token, attempt: null, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token() })).status).toBe(200);
    expect(turnstile.checks()).toBe(1);
  });
});

describe("a claim and a lock that come together", () => {
  it("land in order: a claim written just before the lock is in the lock's answer", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();
    const known = await newClaim(turnstile, link.token, "Hoa", ["i3"]);
    const results: Array<Answer<Claimed>> = [];
    const db = racing(isLock, async () => {
      results.push(await newClaim(turnstile, link.token, "Minh", ["i1"]));
      results.push(await changeClaim(link.token, known.body.key ?? "", "Hoa", ["i2", "i3"]));
    });

    const locked = await api<Viewed>(owner, "POST", `/v1/shares/${link.id}/lock`, undefined, { DB: db });

    expect(results.map((result) => result.status)).toEqual([200, 200]);
    expect(locked.status).toBe(200);
    expect(locked.body.share.status).toBe("locking");
    expect(locked.body.share.claims.map((claim) => [claim.id, claim.itemIds])).toEqual([
      [known.body.you, ["i2", "i3"]],
      [results[0].body.you, ["i1"]],
    ]);
  });

  it("land in order: a claim written just after the lock is refused, and nothing of it is kept", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();
    const known = await newClaim(turnstile, link.token, "Hoa", ["i3"]);
    for (const [matches, claimWith] of [
      [isNewClaim, (db: D1Database) => fromPage<Claimed>("/v1/shares/claim", { token: link.token, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token() }, undefined, { DB: db })],
      [isClaimChange, (db: D1Database) => fromPage<Claimed>("/v1/shares/claim", { token: link.token, key: known.body.key, name: "Hoa", itemIds: ["i1"] }, undefined, { DB: db })],
    ] as const) {
      await api(owner, "POST", `/v1/shares/${link.id}/reopen`);
      const raced: { locked?: Answer<Viewed> } = {};
      const db = racing(matches, async () => {
        raced.locked = await api<Viewed>(owner, "POST", `/v1/shares/${link.id}/lock`);
      });

      const claimed = await claimWith(db);

      expect(claimed.status).toBe(409);
      expect(claimed.body).toEqual({ error: "locked" });
      expect(raced.locked?.body.share.claims.map((claim) => [claim.id, claim.itemIds])).toEqual([[known.body.you, ["i3"]]]);
    }
    expect(await rows("SELECT name, item_ids FROM share_claims")).toEqual([{ name: "Hoa", item_ids: '["i3"]' }]);
  });

  it("sent at once, either land before the lock and are in its answer, or are refused and kept nowhere", async () => {
    const owner = await person();
    const turnstile = fakeTurnstile();
    for (let round = 0; round < 5; round++) {
      const link = await publish(owner);
      const [claimed, locked] = await Promise.all([newClaim(turnstile, link.token, "Minh", ["i1"]), api<Viewed>(owner, "POST", `/v1/shares/${link.id}/lock`)]);

      expect(locked.status).toBe(200);
      const inLock = locked.body.share.claims.map((claim) => claim.id);
      if (claimed.status === 200) {
        expect(inLock).toEqual([claimed.body.you]);
      } else {
        expect(claimed.body).toEqual({ error: "locked" });
        expect(inLock).toEqual([]);
        expect(await rows("SELECT id FROM share_claims WHERE share_id = ?1", link.id)).toEqual([]);
      }
    }
  });
});

describe("the limits of one IP", () => {
  async function useUp(ip: string, kind: string, used: number) {
    const name = `ip:${await hashed(ipKey(ip), env.HASH_SALT)}`;
    await env.DB.prepare("UPDATE rate_windows SET used = ?3 WHERE account = ?1 AND kind = ?2").bind(name, kind, used).run();
  }

  it("are as many new claims an hour as a link holds, counted before Cloudflare is asked", async () => {
    const owner = await person();
    const link = await publish(owner);
    const next = await publish(owner);
    const turnstile = fakeTurnstile();
    const ip = "203.0.113.20";
    let first: Answer<Claimed> | null = null;
    // A whole table on one Wi-Fi fills a link from one address.
    for (let count = 0; count < POLICY.newClaimsPerHour; count++) {
      const made = await newClaim(turnstile, link.token, `Guest ${count}`, ["i1"], ip);
      expect(made.status).toBe(200);
      first ??= made;
    }

    const refused = await newClaim(turnstile, next.token, "One too many", ["i1"], ip);

    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    expect(Number(refused.headers.get("retry-after"))).toBeGreaterThan(3_500);
    expect(turnstile.checks()).toBe(POLICY.newClaimsPerHour);
    expect((await changeClaim(link.token, first?.body.key ?? "", "Guest 0", ["i2"], ip)).status).toBe(200);
    expect((await newClaim(turnstile, next.token, "Another IP", ["i1"], "203.0.113.21")).status).toBe(200);
  });

  it("are 300 claim writes an hour, new or not, about ten for each claim a link holds", async () => {
    expect(POLICY.claimWritesPerHour).toBe(10 * POLICY.maxClaims);
    const link = await publish(await person());
    const ip = "203.0.113.30";
    const made = await newClaim(fakeTurnstile(), link.token, "Minh", ["i1"], ip);
    await useUp(ip, "claim_write", POLICY.claimWritesPerHour - 1);

    const last = await changeClaim(link.token, made.body.key ?? "", "Minh", ["i2"], ip);
    const refused = await changeClaim(link.token, made.body.key ?? "", "Minh", ["i3"], ip);

    expect(last.status).toBe(200);
    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    expect((await changeClaim(link.token, made.body.key ?? "", "Minh", ["i3"], "203.0.113.31")).status).toBe(200);
  });

  it("are 600 reads an hour over a link's page, its live connection, request links and invites", async () => {
    const owner = await person();
    const link = await publish(owner);
    const group = await newGroup(owner, "Flat 4B", VND);
    const inviteToken = await invite(group);
    const request = await api<{ url: string }>(owner, "POST", "/v1/requests", {
      id: crypto.randomUUID(),
      amountMinor: 50_000,
      currency: VND,
      payerName: "Minh",
      creditorName: "Lan",
      method: { kind: "PlainDetails", text: "Pay me" },
    });
    const ip = "203.0.113.40";
    expect((await openShare(link.token, undefined, ip)).status).toBe(200);
    await useUp(ip, "web_read", POLICY.webReadsPerHour - 2);
    expect((await fromPage("/v1/request-links/view", { token: linkToken(request.body.url) }, ip)).status).toBe(200);
    expect((await fromPage("/v1/invites/preview", { token: inviteToken }, ip)).status).toBe(200);

    const refused = [
      await openShare(link.token, undefined, ip),
      await fromPage("/v1/request-links/view", { token: linkToken(request.body.url) }, ip),
      await fromPage("/v1/invites/preview", { token: inviteToken }, ip),
    ];
    const live = await send(
      new Request("https://proxy.test/v1/shares/live", {
        headers: { upgrade: "websocket", "sec-websocket-protocol": `splitvibe.v1, ${link.token}`, "CF-Connecting-IP": ip },
      }),
    );

    for (const answer of refused) {
      expect(answer.status).toBe(429);
      expect(answer.body).toEqual({ error: "rate_limited" });
    }
    expect(live.status).toBe(429);
    expect((await openShare(link.token, undefined, "203.0.113.41")).status).toBe(200);
  });

  it("write nothing once they are reached: a refused request leaves its window as it was", async () => {
    const owner = await person();
    const link = await publish(owner);
    const ip = "203.0.113.70";
    const made = await newClaim(fakeTurnstile(), link.token, "Minh", ["i1"], ip);
    expect(made.status).toBe(200);
    for (const [kind, limit] of [
      ["web_read", POLICY.webReadsPerHour],
      ["claim_write", POLICY.claimWritesPerHour],
    ] as const) {
      await openShare(link.token, undefined, ip);
      await useUp(ip, kind, limit);
    }
    const before = { reads: await windowOf(ip, "web_read"), writes: await windowOf(ip, "claim_write"), news: await windowOf(ip, "claim_new") };

    const refused = [
      await openShare(link.token, made.body.key, ip),
      await send(new Request("https://proxy.test/v1/shares/live", { headers: { upgrade: "websocket", "sec-websocket-protocol": `splitvibe.v1, ${link.token}`, "CF-Connecting-IP": ip } })),
      await changeClaim(link.token, made.body.key ?? "", "Minh", ["i2"], ip),
      await newClaim(fakeTurnstile(), link.token, "Hoa", ["i2"], ip),
    ];

    expect(refused.map((answer) => answer.status)).toEqual([429, 429, 429, 429]);
    expect({ reads: await windowOf(ip, "web_read"), writes: await windowOf(ip, "claim_write"), news: await windowOf(ip, "claim_new") }).toEqual(before);
    expect(before.reads?.used).toBe(POLICY.webReadsPerHour);
    expect(before.news?.used).toBe(1);
  });

  it("write nothing for new claims past their limit, and do not ask Cloudflare", async () => {
    const link = await publish(await person());
    const turnstile = fakeTurnstile();
    const ip = "203.0.113.71";
    expect((await newClaim(turnstile, link.token, "Minh", ["i1"], ip)).status).toBe(200);
    await useUp(ip, "claim_new", POLICY.newClaimsPerHour);
    const before = await windowOf(ip, "claim_new");

    const refused = await newClaim(turnstile, link.token, "Hoa", ["i2"], ip);

    expect(refused.status).toBe(429);
    expect(Number(refused.headers.get("retry-after"))).toBeGreaterThan(3_500);
    expect(await windowOf(ip, "claim_new")).toEqual(before);
    expect(turnstile.checks()).toBe(1);
  });

  it("count nothing, and write nothing, for a token that leads to no link, request or invite that works", async () => {
    const owner = await person();
    const revoked = await publish(owner);
    const expired = await publish(owner);
    await api(owner, "DELETE", `/v1/shares/${revoked.id}`);
    await env.DB.prepare("UPDATE shares SET expires_at = ?2 WHERE id = ?1").bind(expired.id, Date.now() - 1).run();
    const group = await newGroup(owner, "Flat 4B", VND);
    const revokedInvite = await invite(group);
    await api(owner, "DELETE", `/v1/groups/${group.id}/invites`);
    const request = await api<{ request: { id: string }; url: string }>(owner, "POST", "/v1/requests", {
      id: crypto.randomUUID(),
      amountMinor: 50_000,
      currency: VND,
      payerName: "Minh",
      creditorName: "Lan",
      method: {},
    });
    await api(owner, "DELETE", `/v1/requests/${request.body.request.id}`);
    const ip = "203.0.113.72";
    const turnstile = fakeTurnstile();
    const live = (token: string) =>
      send(new Request("https://proxy.test/v1/shares/live", { headers: { upgrade: "websocket", "sec-websocket-protocol": `splitvibe.v1, ${token}`, "CF-Connecting-IP": ip } }));
    const unknown = "AAAAAAAAAAAAAAAAAAAAAA";

    const answers = [
      await openShare(unknown, undefined, ip),
      await openShare("not-a-token", undefined, ip),
      await newClaim(turnstile, unknown, "Minh", ["i1"], ip),
      await live(unknown),
      await fromPage("/v1/request-links/view", { token: unknown }, ip),
      await fromPage("/v1/invites/preview", { token: unknown }, ip),
      await openShare(revoked.token, undefined, ip),
      await openShare(expired.token, undefined, ip),
      await newClaim(turnstile, expired.token, "Minh", ["i1"], ip),
      await live(revoked.token),
      await fromPage("/v1/request-links/view", { token: linkToken(request.body.url) }, ip),
      await fromPage("/v1/invites/preview", { token: revokedInvite }, ip),
    ];

    expect(answers.map((answer) => answer.status)).toEqual([404, 404, 404, 404, 404, 404, 410, 410, 410, 410, 410, 410]);
    expect(await rows("SELECT kind FROM rate_windows WHERE account = ?1", await ipName(ip))).toEqual([]);
    expect(turnstile.checks()).toBe(0);
  });

  it("count a read only once its token led to a link that works", async () => {
    const link = await publish(await person());
    const ip = "203.0.113.73";

    await openShare("AAAAAAAAAAAAAAAAAAAAAA", undefined, ip);
    await openShare(link.token, undefined, ip);
    await openShare(link.token, undefined, ip);

    expect((await windowOf(ip, "web_read"))?.used).toBe(2);
  });
});

describe("what anyone with a link sees", () => {
  it("names no account and holds no hash, no IP and none of the link's own ids", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();
    const ip = "203.0.113.50";
    const made = await newClaim(turnstile, link.token, "Minh", ["i1"], ip);
    await api(owner, "POST", `/v1/shares/${link.id}/lock`);
    await api(owner, "POST", `/v1/shares/${link.id}/finalize`, {
      billRevision: 1,
      bill: billProjection(),
      amounts: { me: { amountMinor: 107_250, payment: null }, p1: { amountMinor: 107_250, payment: payPal(107_250) } },
    });
    const opened = await openShare(link.token, made.body.key, ip);
    const group = await newGroup(owner, "Flat 4B", VND);
    const preview = await fromPage("/v1/invites/preview", { token: await invite(group) }, ip);
    const request = await api<{ url: string }>(owner, "POST", "/v1/requests", {
      id: crypto.randomUUID(),
      amountMinor: 50_000,
      currency: VND,
      payerName: "Minh",
      creditorName: "Lan",
      method: { kind: "PayPalMe", handle: "lan" },
      payment: payPal(50_000),
    });
    const viewed = await fromPage("/v1/request-links/view", { token: linkToken(request.body.url) }, ip);

    expect(Object.keys(opened.body.share).sort()).toEqual(["amounts", "bill", "claims", "expiresAt", "paidBy", "revision", "status"]);
    expect(Object.keys(opened.body.share.claims[0]).sort()).toEqual(["id", "itemIds", "name"]);
    const hashes = [
      ...(await rows<{ token_hash: string }>("SELECT token_hash FROM shares")).map((row) => row.token_hash),
      ...(await rows<{ key_hash: string }>("SELECT key_hash FROM share_claims")).map((row) => row.key_hash),
      ...(await rows<{ link_hash: string }>("SELECT link_hash FROM requests")).map((row) => row.link_hash),
      ...(await rows<{ token_hash: string }>("SELECT token_hash FROM invites")).map((row) => row.token_hash),
      await hashed(ipKey(ip), env.HASH_SALT),
    ];
    for (const answer of [made, opened, preview, viewed]) {
      const text = JSON.stringify(answer.body);
      expect(answer.status).toBe(200);
      for (const secret of [owner.account, ip, link.id, link.billId, group.id, ...hashes]) expect(text).not.toContain(secret);
    }
  });

  it("never changes the bill: no route without a session makes, changes or deletes one", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();
    const before = (await api<{ share: ShareView }>(owner, "GET", `/v1/shares/${link.id}`)).body.share;
    const meddling = { bill: billProjection({ merchant: "Hacked", totalMinor: 1 }), billRevision: 99, status: "locked", amounts: { me: { amountMinor: 1 } } };

    await openShare(link.token);
    await fromPage("/v1/shares/open", { token: link.token, ...meddling });
    const made = await fromPage<Claimed>("/v1/shares/claim", { token: link.token, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token(), ...meddling });
    await fromPage("/v1/shares/claim", { token: link.token, key: made.body.key, name: "Minh", itemIds: ["i2"], ...meddling });
    for (const path of ["/v1/request-links/view", "/v1/invites/preview"]) await fromPage(path, { token: link.token, ...meddling });
    for (const [method, path] of [
      ["PUT", "/v1/shares/open"],
      ["DELETE", "/v1/shares/open"],
      ["PUT", "/v1/shares/claim"],
      ["DELETE", "/v1/shares/claim"],
      ["POST", "/v1/shares/live"],
      ["DELETE", "/v1/shares/live"],
    ] as const) {
      const sent = await send(new Request(`https://proxy.test${path}`, { method, headers: { "content-type": "application/json" }, body: JSON.stringify({ token: link.token, ...meddling }) }));
      expect(sent.status, `${method} ${path}`).toBe(405);
    }
    for (const [method, path] of [
      ["PUT", `/v1/shares/${link.id}`],
      ["POST", `/v1/shares/${link.id}/finalize`],
      ["DELETE", `/v1/shares/${link.id}`],
    ] as const) {
      expect((await api(null, method, path, { billId: link.billId, ...meddling })).status, `${method} ${path}`).toBe(401);
    }

    const after = (await api<{ share: ShareView }>(owner, "GET", `/v1/shares/${link.id}`)).body.share;
    const unchanged = (view: ShareView) => [view.id, view.billId, view.billRevision, view.status, view.createdAt, view.expiresAt, view.bill, view.amounts];
    expect(unchanged(after)).toEqual(unchanged(before));
    expect(after.claims.map((claim) => [claim.name, claim.itemIds])).toEqual([["Minh", ["i2"]]]);
  });

  it("whose claim key or Turnstile token opens nothing else, reading bills least of all", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();
    const turnstileToken = turnstile.token();
    const made = await fromPage<Claimed>("/v1/shares/claim", { token: link.token, name: "Minh", itemIds: ["i1"], turnstile: turnstileToken });
    const key = made.body.key ?? "";
    const seen = model(ANSWER);

    for (const secret of [key, turnstileToken, link.token]) {
      const asSession = await send(billRequest({ session: secret }));
      expect(asSession.status).toBe(401);
      expect(JSON.parse(asSession.body)).toEqual({ error: "session" });
      const asInstall = await send(billRequest({ install: secret }));
      expect(asInstall.status).toBe(401);
      expect(JSON.parse(asInstall.body)).toEqual({ error: "install" });
      for (const [method, path] of [
        ["GET", "/v1/usage"],
        ["GET", "/v1/groups"],
        ["GET", "/v1/requests"],
        ["GET", `/v1/shares/${link.id}`],
        ["DELETE", "/v1/account"],
      ] as const) {
        const sent = await send(proxyRequest(method, path, { session: secret }));
        expect(sent.status, `${method} ${path}`).toBe(401);
      }
    }
    expect(seen).toHaveLength(0);
    expect((await openShare(link.token, key)).body.you).toBe(made.body.you);
  });
});
