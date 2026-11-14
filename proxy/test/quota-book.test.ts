import { runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { POLICY } from "../src/policy";
import { nextMonthStart, type Allowance } from "../src/quota-book";

// Times are taken from the real clock: the object's alarm runs on it, and a time in the past would
// let the alarm clear a hold in the middle of a test.
const TRIAL: Allowance = { kind: "trial", limit: POLICY.trialCalls };
const MONTHLY: Allowance = { kind: "monthly", limit: POLICY.monthlyCalls };
/** The digest of what a request asks; tests that do not look at it use the same one throughout. */
const BILL = "digest-of-a-bill";

function book() {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(crypto.randomUUID()));
}

type Book = ReturnType<typeof book>;

/** One call that the model answered: held, then charged. */
async function call(stub: Book, now: number, allowance: Allowance, requestId: string = crypto.randomUUID()) {
  const hold = await stub.hold(now, requestId, allowance, BILL);
  if (hold.ok) await stub.commit(now, requestId, allowance, BILL);
  return hold;
}

describe("trial calls", () => {
  it("allows three, then refuses for good", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      expect((await call(stub, now, TRIAL)).ok).toBe(true);
    }

    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL)).toMatchObject({ ok: false, code: "quota_exhausted", retryAfter: null });
    expect(await stub.hold(nextMonthStart(now), crypto.randomUUID(), TRIAL, BILL)).toMatchObject({ ok: false, code: "quota_exhausted" });
    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 3, remaining: 0, period: "trial", resetsAt: null });
  });

  it("lets exactly three of four requests racing for the calls have one", async () => {
    const stub = book();
    const now = Date.now();

    const holds = await Promise.all([1, 2, 3, 4].map(() => stub.hold(now, crypto.randomUUID(), TRIAL, BILL)));

    expect(holds.filter((hold) => hold.ok)).toHaveLength(POLICY.trialCalls);
  });
});

describe("monthly calls", () => {
  it("allows ten in a month and refuses the eleventh until the next month begins", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.monthlyCalls; i++) {
      expect((await call(stub, now, MONTHLY)).ok).toBe(true);
    }

    const eleventh = await stub.hold(now, crypto.randomUUID(), MONTHLY, BILL);
    expect(eleventh).toMatchObject({ ok: false, code: "quota_exhausted", retryAfter: Math.ceil((nextMonthStart(now) - now) / 1000) });

    const nextMonth = await stub.hold(nextMonthStart(now), crypto.randomUUID(), MONTHLY, BILL);
    expect(nextMonth).toMatchObject({ ok: true, usage: { used: 0, remaining: 9 } });
  });
});

describe("a request id", () => {
  it("is charged once however often it runs, and runs at most three times", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    for (let run = 0; run < POLICY.runsPerRequest; run++) {
      expect((await call(stub, now, TRIAL, id)).ok).toBe(true);
    }

    expect(await stub.hold(now, id, TRIAL, BILL)).toMatchObject({ ok: false, code: "retry_limit" });
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("running again while its call is still held takes no second call", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL);
    await stub.hold(now, id, TRIAL, BILL);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 2 });
  });

  it("is refused when it comes back asking about another bill", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await call(stub, now, TRIAL, id);

    expect(await stub.hold(now, id, TRIAL, "digest-of-another-bill")).toMatchObject({ ok: false, code: "request_mismatch" });
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 1, remaining: 2 });
  });
});

describe("a held call", () => {
  it("counts against the allowance while the model runs", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      expect((await stub.hold(now, crypto.randomUUID(), TRIAL, BILL)).ok).toBe(true);
    }

    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL)).toMatchObject({ ok: false, code: "quota_exhausted" });
  });

  it("is given back when the model failed before it could charge", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL);
    await stub.release(now, id);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 3 });
  });

  it("stays held while another run of the same request still waits for the model, whichever run fails", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL);
    await stub.hold(now, id, TRIAL, BILL);

    await stub.release(now, id);
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 2 });

    await stub.release(now, id);
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 3 });
  });

  it("stays charged when a later run of the same request fails", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await call(stub, now, TRIAL, id);
    await stub.hold(now, id, TRIAL, BILL);
    await stub.release(now, id);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("is given back after five minutes when nothing more is heard of it", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      await stub.hold(now, crypto.randomUUID(), TRIAL, BILL);
    }

    expect(await stub.usage(now + POLICY.holdMs - 1, TRIAL)).toMatchObject({ remaining: 0 });
    expect(await stub.usage(now + POLICY.holdMs, TRIAL)).toMatchObject({ remaining: 3 });
  });
});

it("forgets old request ids, so its storage does not grow", async () => {
  const stub = book();
  // Held long enough ago that both the hold and the memory of the request have run out.
  const longAgo = Date.now() - POLICY.requestMemoryMs - POLICY.holdMs;
  await stub.hold(longAgo, crypto.randomUUID(), TRIAL, BILL);
  await call(stub, longAgo, TRIAL);

  await runDurableObjectAlarm(stub);

  const left = await runInDurableObject(stub, async (_, state) => (await state.storage.list({ prefix: "request:" })).size);
  expect(left).toBe(0);
});

it("deletes everything it holds for a deleted account", async () => {
  const stub = book();
  const now = Date.now();
  await call(stub, now, MONTHLY);
  await call(stub, now, MONTHLY);

  await stub.forget();

  expect(await stub.usage(now, MONTHLY)).toMatchObject({ used: 0, remaining: 10 });
  const left = await runInDurableObject(stub, async (_, state) => (await state.storage.list()).size);
  expect(left).toBe(0);
});
