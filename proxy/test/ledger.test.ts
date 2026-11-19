import { runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { clientIp, hashed, ipKey } from "../src/ledger";
import { POLICY } from "../src/policy";

// Times are taken from the real clock, which the object's alarm runs on.
function ledger() {
  return env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(crypto.randomUUID()));
}

describe("request rate", () => {
  it("refuses a request over the minute limit until that minute has passed", async () => {
    const stub = ledger();
    const now = Date.now();
    const limit = { perMinute: 2, perDay: 10 };
    expect(await stub.admit(now, limit)).toEqual({ ok: true });
    expect(await stub.admit(now, limit)).toEqual({ ok: true });

    expect(await stub.admit(now + 1_000, limit)).toEqual({ ok: false, retryAfter: 59 });
    expect(await stub.admit(now + POLICY.minuteMs, limit)).toEqual({ ok: true });
  });

  it("refuses a request over the day limit until that day has passed", async () => {
    const stub = ledger();
    const now = Date.now();
    const limit = { perMinute: 10, perDay: 2 };
    await stub.admit(now, limit);
    await stub.admit(now, limit);

    expect(await stub.admit(now + POLICY.minuteMs, limit)).toEqual({ ok: false, retryAfter: (POLICY.dayMs - POLICY.minuteMs) / 1_000 });
    expect(await stub.admit(now + POLICY.dayMs, limit)).toEqual({ ok: true });
  });
});

describe("spend", () => {
  it("holds up to the cap and refuses what would go over it", async () => {
    const stub = ledger();
    const now = Date.now();
    expect(await stub.holdSpend(now, 600, 1_000)).toEqual({ ok: true });

    // A hold refused by the cap would take it past its warning mark too, and is the first to.
    expect(await stub.holdSpend(now, 500, 1_000)).toEqual({ ok: false, retryAfter: POLICY.dayMs / 1_000, nearCap: true });
  });

  it("replaces a hold with the real cost, and gives it all back when the call cost nothing", async () => {
    const stub = ledger();
    const now = Date.now();
    await stub.holdSpend(now, 600, 1_000);
    await stub.settleSpend(now, 600, 100);
    expect(await stub.holdSpend(now, 900, 1_000)).toEqual({ ok: true, nearCap: true });

    await stub.settleSpend(now, 900, 0);
    expect(await stub.holdSpend(now, 900, 1_000)).toEqual({ ok: true });
    expect(await stub.holdSpend(now, 1, 1_000)).toMatchObject({ ok: false });
  });

  it("starts a new window a day after the last one began", async () => {
    const stub = ledger();
    const now = Date.now();
    await stub.holdSpend(now, 1_000, 1_000);
    await stub.settleSpend(now, 1_000, 1_000);

    expect(await stub.holdSpend(now + POLICY.dayMs, 1_000, 1_000)).toEqual({ ok: true, nearCap: true });
  });

  it("keeps the calls of a pool within it, in the cap's own window, so that the other calls always keep the rest of the cap", async () => {
    const stub = ledger();
    const now = Date.now();
    const million = 1_000_000;
    const { globalCapMicro: cap, installCapMicro: pool } = POLICY;
    for (let held = 0; held < pool; held += million) expect(await stub.holdSpend(now, million, cap, pool), `pooled ${held}`).toMatchObject({ ok: true });

    // The pool is full, with the cap far from it: the calls of the pool are refused, the others not.
    expect(await stub.holdSpend(now, 1, cap, pool)).toMatchObject({ ok: false, retryAfter: POLICY.dayMs / 1_000 });
    for (let held = pool; held < cap; held += million) expect(await stub.holdSpend(now, million, cap), `other ${held}`).toMatchObject({ ok: true });
    expect(await stub.holdSpend(now, 1, cap)).toMatchObject({ ok: false });
    expect(cap - pool).toBe(15_000_000);

    // A call of the pool that cost less gives the pool back what it did not spend, in the same window.
    await stub.settleSpend(now, million, 0, true);
    expect(await stub.holdSpend(now, million, cap, pool)).toMatchObject({ ok: true });
    // A new window starts the pool over with the cap.
    expect(await stub.holdSpend(now + POLICY.dayMs, pool, cap, pool)).toMatchObject({ ok: true });
  });

  it("tells of the first hold of a window to take the cap, and the pool, to the warning mark, once each", async () => {
    const stub = ledger();
    const now = Date.now();
    const cap = 1_000;
    const pool = 500;

    expect(await stub.holdSpend(now, 300, cap, pool)).toEqual({ ok: true });
    expect(await stub.holdSpend(now, 100, cap, pool)).toEqual({ ok: true, nearPool: true });
    expect(await stub.holdSpend(now, 50, cap, pool)).toEqual({ ok: true });
    expect(await stub.holdSpend(now, 350, cap)).toEqual({ ok: true, nearCap: true });
    expect(await stub.holdSpend(now, 100, cap)).toEqual({ ok: true });
    expect(await stub.holdSpend(now, 500, cap)).toEqual({ ok: false, retryAfter: POLICY.dayMs / 1_000 });

    // The next window tells of them again.
    const tomorrow = now + POLICY.dayMs;
    expect(await stub.holdSpend(tomorrow, 900, cap, pool)).toEqual({ ok: false, retryAfter: POLICY.dayMs / 1_000, nearCap: true, nearPool: true });
    expect(await stub.holdSpend(tomorrow, 400, cap, pool)).toEqual({ ok: true });
  });
});

it("deletes itself once every window has passed, so a hashed IP is not kept", async () => {
  const stub = ledger();
  const twoDaysAgo = Date.now() - 2 * POLICY.dayMs;
  await stub.admit(twoDaysAgo, POLICY.ip);
  await stub.holdSpend(twoDaysAgo, 10, 100);

  await runDurableObjectAlarm(stub);

  const left = await runInDurableObject(stub, async (_, state) => (await state.storage.list()).size);
  expect(left).toBe(0);
});

describe("the caller's address", () => {
  it("is named by a salted hash, never by itself", async () => {
    const one = await hashed("203.0.113.7", "salt");

    expect(one).toMatch(/^[0-9a-f]{64}$/);
    expect(one).not.toContain("203.0.113.7");
    expect(await hashed("203.0.113.7", "salt")).toBe(one);
    expect(await hashed("203.0.113.7", "another salt")).not.toBe(one);
  });

  it("comes from CF-Connecting-IP; an edge request without it is refused, and a local run is named local", () => {
    expect(clientIp(new Request("https://proxy.test", { headers: { "CF-Connecting-IP": " 203.0.113.7 " } }))).toEqual({ ok: true, ip: "203.0.113.7" });
    expect(clientIp(new Request("https://proxy.test", { cf: { country: "VN" } } as RequestInit))).toEqual({ ok: false });
    expect(clientIp(new Request("https://proxy.test"))).toEqual({ ok: true, ip: "local" });
  });
});

describe("an IPv6 address", () => {
  it("counts by its /64, which one subscriber holds", () => {
    expect(ipKey("2001:db8::1")).toBe("2001:db8:0:0::/64");
    expect(ipKey("2001:0DB8:0000:0000:ffff:0:0:2")).toBe(ipKey("2001:db8::1"));
    expect(ipKey("2001:db8:0:1::1")).not.toBe(ipKey("2001:db8:0:2::1"));
    expect(ipKey("::1")).toBe("0:0:0:0::/64");
    expect(ipKey("64:ff9b::203.0.113.7")).toBe("64:ff9b:0:0::/64");
  });

  it("that carries an IPv4 address counts as that address, and an IPv4 address counts by itself", () => {
    expect(ipKey("::ffff:203.0.113.7")).toBe("203.0.113.7");
    expect(ipKey(" 203.0.113.7 ")).toBe("203.0.113.7");
  });

  it("that cannot be read is used as it is", () => {
    for (const odd of ["2001:db8:::1", "1:2:3:4:5:6:7", "1::2::3", "2001:db8::xyz", "local"]) {
      expect(ipKey(odd)).toBe(odd);
    }
  });
});
