import { runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { clientIp, hashed } from "../src/ledger";
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

    expect(await stub.holdSpend(now, 500, 1_000)).toEqual({ ok: false, retryAfter: POLICY.dayMs / 1_000 });
  });

  it("replaces a hold with the real cost, and gives it all back when the call cost nothing", async () => {
    const stub = ledger();
    const now = Date.now();
    await stub.holdSpend(now, 600, 1_000);
    await stub.settleSpend(now, 600, 100);
    expect(await stub.holdSpend(now, 900, 1_000)).toEqual({ ok: true });

    await stub.settleSpend(now, 900, 0);
    expect(await stub.holdSpend(now, 900, 1_000)).toEqual({ ok: true });
    expect(await stub.holdSpend(now, 1, 1_000)).toMatchObject({ ok: false });
  });

  it("starts a new window a day after the last one began", async () => {
    const stub = ledger();
    const now = Date.now();
    await stub.holdSpend(now, 1_000, 1_000);
    await stub.settleSpend(now, 1_000, 1_000);

    expect(await stub.holdSpend(now + POLICY.dayMs, 1_000, 1_000)).toEqual({ ok: true });
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
