import { runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Env } from "../src/index";
import { ALLOWANCES } from "../src/identity";
import { hashed } from "../src/ledger";
import { POLICY } from "../src/policy";
import {
  type FakeRevenueCat,
  fakeRevenueCat,
  playCustomer,
  subscriberAnswer,
  TEST_KEY,
  TEST_WEBHOOK_AUTH,
  transferEvent,
  webhookEvent,
} from "./fake-revenuecat";
import { proxyRequest, send } from "./requests";
import { api, logsOf, type Person, person } from "./shared";

const HOUR = 60 * 60 * 1000;

let revenueCat: FakeRevenueCat;

beforeEach(() => {
  revenueCat = fakeRevenueCat();
});

/** Delivers [body] to the webhook as RevenueCat does, with [authorization] as its Authorization header; none for null. */
function deliver(body: unknown, authorization: string | null = TEST_WEBHOOK_AUTH, settings: Partial<Env> = {}) {
  const headers = new Headers({ "content-type": "application/json; charset=utf-8" });
  if (authorization !== null) headers.set("authorization", authorization);
  const sent = typeof body === "string" ? body : JSON.stringify(body);
  return send(new Request("https://proxy.test/v1/subscription-events", { method: "POST", headers, body: sent }), settings);
}

/** Someone signed in whose app got its purchaser from the proxy. */
async function purchaser(): Promise<Person> {
  const who = await person();
  expect((await api(who, "GET", "/v1/usage")).body).toMatchObject({ purchaser: who.account });
  return who;
}

function bookOf(account: string) {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(`google:${account}`));
}

/** The plan the account's QuotaBook keeps, as storage holds it. */
function storedPlan(who: Person): Promise<unknown> {
  return runInDurableObject(bookOf(who.account), (_, state) => state.storage.get("plan"));
}

/** The plan the account's calls left say it is on. */
async function planNow(who: Person): Promise<unknown> {
  return (await api<{ usage: { plan: string } }>(who, "GET", "/v1/usage")).body.usage.plan;
}

/** A Play subscription in force for twenty days more. */
function active() {
  return playCustomer({ expires: Date.now() + 20 * POLICY.dayMs });
}

describe("the webhook's authorization", () => {
  it("refuses an event without the value set in RevenueCat, whatever its length, and asks and writes nothing", async () => {
    const who = await purchaser();
    revenueCat.hold(who.account, active());
    const wrong = [
      null,
      "",
      "wrong",
      `Bearer wrong`,
      TEST_WEBHOOK_AUTH.slice(0, -1),
      `${TEST_WEBHOOK_AUTH}x`,
      `bearer ${TEST_WEBHOOK_AUTH}`,
      `Basic ${TEST_WEBHOOK_AUTH}`,
      `Bearer Bearer ${TEST_WEBHOOK_AUTH}`,
      "x".repeat(4_096),
    ];

    for (const authorization of wrong) {
      const sent = await deliver(webhookEvent("INITIAL_PURCHASE", who.account), authorization);
      expect(sent.status, String(authorization)).toBe(401);
      expect(JSON.parse(sent.body)).toEqual({ error: "webhook_auth" });
    }
    expect(revenueCat.asked).toEqual([]);
    expect(await storedPlan(who)).toBeUndefined();
  });

  it("refuses a wrong value before it reads the body, however long the body is", async () => {
    const who = await purchaser();
    const long = JSON.stringify(webhookEvent("RENEWAL", who.account, { aliases: Array(2_000).fill(who.account) }));
    expect(long.length).toBeGreaterThan(64 * 1024);

    const refused = await deliver(long, "wrong");

    expect(refused.status).toBe(401);
    expect(JSON.parse(refused.body)).toEqual({ error: "webhook_auth" });
    // The same body with the right value is read, and is too long.
    expect((await deliver(long)).status).toBe(413);
    expect(revenueCat.asked).toEqual([]);
  });

  it("takes the value as it is, or after Bearer", async () => {
    const who = await purchaser();
    revenueCat.hold(who.account, active());

    for (const authorization of [TEST_WEBHOOK_AUTH, `Bearer ${TEST_WEBHOOK_AUTH}`]) {
      const sent = await deliver(webhookEvent("RENEWAL", who.account), authorization);
      expect(sent.status, authorization).toBe(200);
    }
    expect(revenueCat.gets(who.account)).toBe(2);
  });

  it("is 503 not_configured without the secret, whatever the request carries, and asks nothing", async () => {
    const who = await purchaser();

    for (const secret of [undefined, "", "  "]) {
      for (const authorization of [null, "", TEST_WEBHOOK_AUTH, "Bearer "]) {
        const sent = await deliver(webhookEvent("RENEWAL", who.account), authorization, { REVENUECAT_WEBHOOK_AUTH: secret });
        expect(sent.status).toBe(503);
        expect(JSON.parse(sent.body)).toEqual({ error: "not_configured" });
      }
    }
    expect(revenueCat.asked).toEqual([]);
  });
});

describe("an event", () => {
  it("has the proxy read the plan of the account it is about from RevenueCat, and keep it", async () => {
    const who = await purchaser();
    const expires = Date.now() + 20 * POLICY.dayMs;
    revenueCat.hold(who.account, playCustomer({ expires }));
    const at = Date.now();
    revenueCat.clock = () => at;

    const sent = await deliver(webhookEvent("INITIAL_PURCHASE", who.account));

    expect(sent.status).toBe(200);
    expect(sent.headers.get("content-type")).toBe("application/json");
    expect(JSON.parse(sent.body)).toEqual({ ok: true });
    expect(revenueCat.asked).toEqual([{ method: "GET", id: who.account, authorization: `Bearer ${TEST_KEY}`, platform: null }]);
    expect(await storedPlan(who)).toEqual({ proUntil: expires, checkedAt: at });
    expect(await planNow(who)).toBe("pro");
  });

  it("of type TEST is answered 200 without asking RevenueCat", async () => {
    const who = await purchaser();

    const sent = await deliver(webhookEvent("TEST", who.account));

    expect(sent.status).toBe(200);
    expect(JSON.parse(sent.body)).toEqual({ ok: true });
    expect(revenueCat.asked).toEqual([]);
  });

  it("about ids that are not accounts, or that the proxy never handed out, is answered 200 without asking RevenueCat", async () => {
    const who = await purchaser();
    const stranger = await hashed(crypto.randomUUID(), env.HASH_SALT);

    for (const id of ["$RCAnonymousID:8e1b2c3d4e5f", who.account.toUpperCase(), who.account.slice(1), `${who.account}0`, ` ${who.account}`, 42, null, stranger]) {
      const sent = await deliver(webhookEvent("INITIAL_PURCHASE", id));
      expect(sent.status, String(id)).toBe(200);
      expect(JSON.parse(sent.body)).toEqual({ ok: true });
    }
    expect(revenueCat.asked).toEqual([]);
    expect(await storedPlan(who)).toBeUndefined();
  });

  it("is refused when its body is not an event, before RevenueCat is asked", async () => {
    const who = await purchaser();
    const bodies: Array<[string, number]> = [
      ["not json", 400],
      ["{}", 400],
      ['{"api_version": "1.0", "event": "RENEWAL"}', 400],
      [JSON.stringify(webhookEvent("RENEWAL", who.account, { aliases: Array(2_000).fill(who.account) })), 413],
    ];

    for (const [body, status] of bodies) {
      const sent = await deliver(body);
      expect(sent.status, body.slice(0, 40)).toBe(status);
      expect(JSON.parse(sent.body)).toEqual({ error: status === 413 ? "too_large" : "input" });
    }
    expect(revenueCat.asked).toEqual([]);
  });

  it("that comes again leaves the plan as it was kept", async () => {
    const who = await purchaser();
    const expires = Date.now() + 20 * POLICY.dayMs;
    revenueCat.hold(who.account, playCustomer({ expires }));
    const event = webhookEvent("RENEWAL", who.account);

    expect((await deliver(event)).status).toBe(200);
    const first = (await storedPlan(who)) as { proUntil: number; checkedAt: number };
    expect((await deliver(event)).status).toBe(200);
    const second = (await storedPlan(who)) as { proUntil: number; checkedAt: number };

    expect(first.proUntil).toBe(expires);
    expect(second.proUntil).toBe(expires);
    expect(second.checkedAt).toBeGreaterThanOrEqual(first.checkedAt);
    expect(await planNow(who)).toBe("pro");
  });

  it("of an old expiry, delivered after a renewal, leaves the account on Pro, as RevenueCat says it is", async () => {
    const who = await purchaser();
    const now = Date.now();
    const renewed = now + 30 * POLICY.dayMs;
    revenueCat.hold(who.account, playCustomer({ expires: renewed }));

    expect((await deliver(webhookEvent("RENEWAL", who.account, { event_timestamp_ms: now, expiration_at_ms: renewed }))).status).toBe(200);
    const late = webhookEvent("EXPIRATION", who.account, { event_timestamp_ms: now - 2 * HOUR, expiration_at_ms: now - HOUR, expiration_reason: "BILLING_ERROR" });
    expect((await deliver(late)).status).toBe(200);

    expect(await storedPlan(who)).toMatchObject({ proUntil: renewed });
    expect(await planNow(who)).toBe("pro");
  });

  it("whose answers from RevenueCat come out of order leaves the plan of the later answer", async () => {
    const who = await purchaser();
    const earlier = Date.now();
    const later = earlier + 1_000;
    const expires = earlier + 20 * POLICY.dayMs;
    let release!: () => void;
    const heldBack = new Promise<void>((resolve) => {
      release = resolve;
    });
    let gets = 0;
    revenueCat.answer = async (asked) => {
      if (asked.method !== "GET") return undefined;
      gets += 1;
      // The first read is answered last, with what RevenueCat held before the second read.
      if (gets === 1) {
        await heldBack;
        return HttpResponse.json(subscriberAnswer(who.account, { entitlements: {}, subscriptions: {} }, earlier));
      }
      return HttpResponse.json(subscriberAnswer(who.account, playCustomer({ expires }), later));
    };

    const first = deliver(webhookEvent("CANCELLATION", who.account));
    await vi.waitFor(() => expect(gets).toBe(1));
    expect((await deliver(webhookEvent("UNCANCELLATION", who.account))).status).toBe(200);
    release();
    expect((await first).status).toBe(200);

    expect(await storedPlan(who)).toEqual({ proUntil: expires, checkedAt: later });
    expect(await planNow(who)).toBe("pro");
  });

  it("of a transfer refreshes the account the purchase left and the one it went to", async () => {
    const from = await purchaser();
    const to = await purchaser();
    revenueCat.hold(from.account, active());
    expect((await deliver(webhookEvent("INITIAL_PURCHASE", from.account))).status).toBe(200);
    expect(await planNow(from)).toBe("pro");
    revenueCat.hold(from.account, { entitlements: {}, subscriptions: {} });
    revenueCat.hold(to.account, active());

    const sent = await deliver(transferEvent([from.account], [to.account]));

    expect(sent.status).toBe(200);
    expect(await planNow(from)).toBe("free");
    expect(await planNow(to)).toBe("pro");
    expect(revenueCat.gets(from.account)).toBe(2);
    expect(revenueCat.gets(to.account)).toBe(1);
  });

  it("is answered 503 when RevenueCat cannot be read, changing nothing it could not read, and is taken when it comes again", async () => {
    const from = await purchaser();
    const to = await purchaser();
    revenueCat.hold(from.account, active());
    expect((await deliver(webhookEvent("INITIAL_PURCHASE", from.account))).status).toBe(200);
    const kept = await storedPlan(from);
    // The purchase moved: RevenueCat now says so, but cannot be read for one side.
    revenueCat.hold(from.account, { entitlements: {}, subscriptions: {} });
    revenueCat.hold(to.account, active());
    revenueCat.answer = (asked) => (asked.id === from.account ? HttpResponse.json({ code: 7110, message: "Internal server error." }, { status: 500 }) : undefined);
    const transfer = transferEvent([from.account], [to.account]);

    const failed = await deliver(transfer);

    expect(failed.status).toBe(503);
    expect(JSON.parse(failed.body)).toEqual({ error: "subscription_unavailable" });
    expect(await storedPlan(from)).toEqual(kept);
    expect(await planNow(from)).toBe("pro");
    // What could be read is kept: reading it again gives the same.
    expect(await planNow(to)).toBe("pro");

    revenueCat.answer = undefined;
    const again = await deliver(transfer);

    expect(again.status).toBe(200);
    expect(await planNow(from)).toBe("free");
    expect(await planNow(to)).toBe("pro");
  });

  it("is answered 503 not_configured without RevenueCat's key once it is about an account, and asks nothing", async () => {
    const who = await purchaser();

    const sent = await deliver(webhookEvent("RENEWAL", who.account), TEST_WEBHOOK_AUTH, { REVENUECAT_SECRET_KEY: undefined });
    const test = await deliver(webhookEvent("TEST", who.account), TEST_WEBHOOK_AUTH, { REVENUECAT_SECRET_KEY: undefined });

    expect(sent.status).toBe(503);
    expect(JSON.parse(sent.body)).toEqual({ error: "not_configured" });
    expect(test.status).toBe(200);
    expect(revenueCat.asked).toEqual([]);
  });

  it("about a deleted account is answered 200 without asking RevenueCat, which creates no customer again", async () => {
    const who = await purchaser();
    revenueCat.hold(who.account, active());
    expect((await deliver(webhookEvent("INITIAL_PURCHASE", who.account))).status).toBe(200);
    expect((await send(proxyRequest("DELETE", "/v1/account", { session: who.session }))).status).toBe(204);
    const asked = revenueCat.asked.length;

    const sent = await deliver(webhookEvent("EXPIRATION", who.account));

    expect(sent.status).toBe(200);
    expect(revenueCat.asked).toHaveLength(asked);
    expect(revenueCat.has(who.account)).toBe(false);
    // The account's QuotaBook keeps nothing but the time of the deletion.
    expect(await runInDurableObject(bookOf(who.account), async (_, state) => [...(await state.storage.list()).keys()])).toEqual(["deletedAt"]);
  });

  it("refreshes at most 20 accounts, each once", async () => {
    const now = Date.now();
    const ids = await Promise.all(Array.from({ length: 25 }, () => hashed(crypto.randomUUID(), env.HASH_SALT)));
    for (const id of ids) await bookOf(id).issuePurchaser(now, ALLOWANCES.monthly);

    const sent = await deliver(webhookEvent("RENEWAL", ids[0], { aliases: [...ids, ...ids] }));

    expect(sent.status).toBe(200);
    expect(revenueCat.gets()).toBe(20);
    expect(new Set(revenueCat.asked.map((asked) => asked.id))).toEqual(new Set(ids.slice(0, 20)));
  });

  it("is logged by its type and outcome, never with an id, a body or a secret", async () => {
    const who = await purchaser();
    revenueCat.hold(who.account, active());
    const renewal = webhookEvent("RENEWAL", who.account);
    const { lines } = await logsOf(async () => {
      await deliver(renewal);
      await deliver(renewal, "wrong");
      revenueCat.answer = () => HttpResponse.json({ code: 7110 }, { status: 500 });
      await deliver(renewal);
    });

    const logged = lines.join("\n");
    expect(lines.map((line) => JSON.parse(line) as Record<string, unknown>)).toEqual([
      { event: "subscription_event", type: "RENEWAL", accounts: 1, outcome: "ok", ms: expect.any(Number) },
      { event: "subscription_event", outcome: "webhook_auth", ms: expect.any(Number) },
      { event: "revenuecat_unavailable", status: 500 },
      { event: "subscription_event", type: "RENEWAL", accounts: 1, failed: 1, outcome: "subscription_unavailable", ms: expect.any(Number) },
    ]);
    for (const secret of [who.account, TEST_WEBHOOK_AUTH, TEST_KEY, String((renewal.event as { id: string }).id)]) {
      expect(logged).not.toContain(secret);
    }
  });
});
