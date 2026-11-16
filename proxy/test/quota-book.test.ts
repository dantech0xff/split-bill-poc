import { runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { WEIGHTS } from "../src/identity";
import { POLICY } from "../src/policy";
import { nextMonthStart, type Allowance } from "../src/quota-book";

// Times are taken from the real clock: the object's alarm runs on it, and a time in the past would
// let the alarm clear a hold in the middle of a test.
const TRIAL: Allowance = { kind: "trial", limit: POLICY.trialCalls };
const MONTHLY: Allowance = { kind: "monthly", limit: POLICY.monthlyCalls };
/** The digest of what a request asks; tests that do not look at it use the same one throughout. */
const BILL = "digest-of-a-bill";
/** A bill read takes a whole call, an edit of a split a fifth of one. */
const READ = WEIGHTS.billAnalysis;
const EDIT = WEIGHTS.splitRevision;

function book() {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(crypto.randomUUID()));
}

type Book = ReturnType<typeof book>;

/** One call that the model answered: held, then charged. */
async function call(stub: Book, now: number, allowance: Allowance, requestId: string = crypto.randomUUID(), weight: number = READ) {
  const hold = await stub.hold(now, requestId, allowance, BILL, weight);
  if (hold.ok) await stub.commit(now, requestId, allowance, BILL, weight);
  return hold;
}

describe("trial calls", () => {
  it("allows three, then refuses for good", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      expect((await call(stub, now, TRIAL)).ok).toBe(true);
    }

    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ)).toMatchObject({ ok: false, code: "quota_exhausted", retryAfter: null });
    expect(await stub.hold(nextMonthStart(now), crypto.randomUUID(), TRIAL, BILL, READ)).toMatchObject({ ok: false, code: "quota_exhausted" });
    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 3, remaining: 0, period: "trial", resetsAt: null });
  });

  it("lets exactly three of four requests racing for the calls have one", async () => {
    const stub = book();
    const now = Date.now();

    const holds = await Promise.all([1, 2, 3, 4].map(() => stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ)));

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

    const eleventh = await stub.hold(now, crypto.randomUUID(), MONTHLY, BILL, READ);
    expect(eleventh).toMatchObject({ ok: false, code: "quota_exhausted", retryAfter: Math.ceil((nextMonthStart(now) - now) / 1000) });

    const nextMonth = await stub.hold(nextMonthStart(now), crypto.randomUUID(), MONTHLY, BILL, READ);
    expect(nextMonth).toMatchObject({ ok: true, usage: { used: 0, remaining: 9 } });
  });
});

describe("calls that weigh less than one", () => {
  it("are held and charged at their weight, and the usage says so in decimals", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();

    expect(await stub.hold(now, id, TRIAL, BILL, EDIT)).toMatchObject({ ok: true, usage: { used: 0, remaining: 2.8 } });
    expect(await stub.commit(now, id, TRIAL, BILL, EDIT)).toEqual({ limit: 3, used: 0.2, remaining: 2.8, period: "trial", resetsAt: null });
    await call(stub, now, TRIAL, crypto.randomUUID(), EDIT);
    await call(stub, now, TRIAL);

    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 1.4, remaining: 1.6, period: "trial", resetsAt: null });
  });

  it("still run when too little is left for a bill read, and stop when less than their weight is left", async () => {
    const stub = book();
    const now = Date.now();
    await call(stub, now, TRIAL);
    await call(stub, now, TRIAL);
    await call(stub, now, TRIAL, crypto.randomUUID(), EDIT);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 2.2, remaining: 0.8 });
    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ)).toMatchObject({ ok: false, code: "quota_exhausted", usage: { remaining: 0.8 } });
    for (let edit = 0; edit < 4; edit++) {
      expect((await call(stub, now, TRIAL, crypto.randomUUID(), EDIT)).ok).toBe(true);
    }
    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, EDIT)).toMatchObject({
      ok: false,
      code: "quota_exhausted",
      usage: { used: 3, remaining: 0 },
    });
  });

  it("are refused when what is left is above zero but below their weight", async () => {
    const stub = book();
    const now = Date.now();
    // 2.9 calls, which no route weighs, leave a tenth of a call.
    await call(stub, now, TRIAL, crypto.randomUUID(), 290);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 2.9, remaining: 0.1 });
    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, EDIT)).toMatchObject({ ok: false, code: "quota_exhausted", usage: { remaining: 0.1 } });
    expect((await call(stub, now, TRIAL, crypto.randomUUID(), 10)).ok).toBe(true);
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 3, remaining: 0 });
  });

  it("count their holds against what is left until they are charged or given back", async () => {
    const stub = book();
    const now = Date.now();
    const held = crypto.randomUUID();
    await stub.hold(now, held, TRIAL, BILL, EDIT);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 2.8 });
    await stub.release(now, held);
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 3 });
  });

  it("are charged once however often their request runs", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    for (let run = 0; run < POLICY.runsPerRequest; run++) {
      expect((await call(stub, now, MONTHLY, id, EDIT)).ok).toBe(true);
    }

    expect(await stub.hold(now, id, MONTHLY, BILL, EDIT)).toMatchObject({ ok: false, code: "retry_limit" });
    expect(await stub.usage(now, MONTHLY)).toMatchObject({ used: 0.2, remaining: 9.8 });
  });

  it("print every count of hundredths with at most two decimals", async () => {
    const stub = book();
    const now = Date.now();
    // Added up as decimals, 0.07 and 0.22 make 0.29000000000000004, and three edits taken from 3
    // leave 2.3999999999999995. The book adds whole hundredths and divides once, when it answers.
    for (const weight of [7, 22]) await call(stub, now, MONTHLY, crypto.randomUUID(), weight);

    const usage = await stub.usage(now, MONTHLY);
    expect(JSON.stringify(usage)).toContain('"limit":10,"used":0.29,"remaining":9.71,');
    const misprinted: number[] = [];
    for (let hundredths = 0; hundredths <= 100_000; hundredths++) {
      const written = String(hundredths / 100);
      if (!/^\d+(\.\d{1,2})?$/.test(written) || Math.round(Number(written) * 100) !== hundredths) misprinted.push(hundredths);
    }
    expect(misprinted).toEqual([]);
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

    expect(await stub.hold(now, id, TRIAL, BILL, READ)).toMatchObject({ ok: false, code: "retry_limit" });
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("running again while its call is still held takes no second call", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL, READ);
    await stub.hold(now, id, TRIAL, BILL, READ);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 2 });
  });

  it("is refused when it comes back asking about another bill", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await call(stub, now, TRIAL, id);

    expect(await stub.hold(now, id, TRIAL, "digest-of-another-bill", READ)).toMatchObject({ ok: false, code: "request_mismatch" });
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 1, remaining: 2 });
  });
});

describe("a held call", () => {
  it("counts against the allowance while the model runs", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      expect((await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ)).ok).toBe(true);
    }

    expect(await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ)).toMatchObject({ ok: false, code: "quota_exhausted" });
  });

  it("is given back when the model failed before it could charge", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL, READ);
    await stub.release(now, id);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0, remaining: 3 });
  });

  it("stays held while another run of the same request still waits for the model, whichever run fails", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL, READ);
    await stub.hold(now, id, TRIAL, BILL, READ);

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
    await stub.hold(now, id, TRIAL, BILL, READ);
    await stub.release(now, id);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("is given back after five minutes when nothing more is heard of it", async () => {
    const stub = book();
    const now = Date.now();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ);
    }

    expect(await stub.usage(now + POLICY.holdMs - 1, TRIAL)).toMatchObject({ remaining: 0 });
    expect(await stub.usage(now + POLICY.holdMs, TRIAL)).toMatchObject({ remaining: 3 });
  });

  it("is charged at the weight the call names once its hold was given back for running too long", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    await stub.hold(now, id, TRIAL, BILL, EDIT);
    const late = now + POLICY.holdMs;
    expect(await stub.usage(late, TRIAL)).toMatchObject({ used: 0, remaining: 3 });

    expect(await stub.commit(late, id, TRIAL, BILL, EDIT)).toMatchObject({ used: 0.2, remaining: 2.8 });
  });
});

describe("storage written before calls had weights", () => {
  /** Writes [entries] into a fresh object as the proxy wrote them while it counted whole calls. */
  async function oldBook(entries: Record<string, unknown>): Promise<Book> {
    const stub = book();
    await runInDurableObject(stub, async (_, state) => {
      await state.storage.put(entries);
    });
    return stub;
  }

  it("is turned into hundredths at the first call, so that nobody gains or loses anything", async () => {
    const now = Date.now();
    const charged = crypto.randomUUID();
    const held = crypto.randomUUID();
    const stub = await oldBook({
      book: { period: "trial", used: 1 },
      [`request:${charged}`]: { heldAt: now, runs: 1, active: 0, committedAt: now, digest: BILL },
      [`request:${held}`]: { heldAt: now, runs: 1, active: 1, committedAt: null, digest: BILL },
    });

    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 1, remaining: 1, period: "trial", resetsAt: null });
    const stored = await runInDurableObject(stub, async (_, state) => Object.fromEntries(await state.storage.list()));
    expect(stored).toMatchObject({
      book: { period: "trial", hundredths: 100 },
      [`request:${charged}`]: { committedAt: now, weight: 100 },
      [`request:${held}`]: { committedAt: null, weight: 100 },
    });
    expect(stored.book).not.toHaveProperty("used");

    // The call held before the deploy is charged as the whole call it held; the charged one stays charged once.
    expect(await stub.commit(now, held, TRIAL, BILL, EDIT)).toMatchObject({ used: 2, remaining: 1 });
    expect(await stub.hold(now, charged, TRIAL, BILL, READ)).toMatchObject({ ok: true, usage: { used: 2, remaining: 1 } });
    await stub.commit(now, charged, TRIAL, BILL, READ);
    await call(stub, now, TRIAL, crypto.randomUUID(), EDIT);
    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 2.2, remaining: 0.8 });
  });

  it("gives back a call held before the deploy when its run fails after it", async () => {
    const now = Date.now();
    const held = crypto.randomUUID();
    const stub = await oldBook({
      book: { period: "trial", used: 2 },
      [`request:${held}`]: { heldAt: now, runs: 1, active: 1, committedAt: null, digest: BILL },
    });

    await stub.release(now, held);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 2, remaining: 1 });
  });

  it("keeps an account's count of the month it was written in", async () => {
    const now = Date.now();
    const stub = await oldBook({ book: { period: `${new Date(now).toISOString().slice(0, 7)}`, used: 7 } });

    expect(await stub.usage(now, MONTHLY)).toMatchObject({ used: 7, remaining: 3 });
    expect(await stub.usage(nextMonthStart(now), MONTHLY)).toMatchObject({ used: 0, remaining: 10 });
  });

  it("is left as it is once it was written in hundredths", async () => {
    const now = Date.now();
    const stub = await oldBook({
      book: { period: "trial", hundredths: 20 },
      [`request:${crypto.randomUUID()}`]: { heldAt: now, runs: 1, active: 1, committedAt: null, digest: BILL, weight: 20 },
    });

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 0.2, remaining: 2.6 });
  });

  it("takes a call that comes without a weight, from a Worker deployed before weights, as a whole call", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();
    // That Worker calls hold and commit with four arguments.
    const noWeight = undefined as unknown as number;

    await stub.hold(now, id, TRIAL, BILL, noWeight);

    expect(await stub.commit(now, id, TRIAL, BILL, noWeight)).toMatchObject({ used: 1, remaining: 2 });
  });
});

it("forgets old request ids, so its storage does not grow", async () => {
  const stub = book();
  // Held long enough ago that both the hold and the memory of the request have run out.
  const longAgo = Date.now() - POLICY.requestMemoryMs - POLICY.holdMs;
  await stub.hold(longAgo, crypto.randomUUID(), TRIAL, BILL, READ);
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
