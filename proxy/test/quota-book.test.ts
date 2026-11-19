import { runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { WEIGHTS, weightInHundredths } from "../src/identity";
import { POLICY } from "../src/policy";
import { nextMonthStart, periodOf, type Allowance, type PlanState } from "../src/quota-book";

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

/** A fresh object whose storage holds [entries], as another version of the code may have written them. */
async function bookWith(entries: Record<string, unknown>): Promise<Book> {
  const stub = book();
  await runInDurableObject(stub, async (_, state) => {
    await state.storage.put(entries);
  });
  return stub;
}

/** Everything the object's storage holds, by key. */
function storageOf(stub: Book): Promise<Record<string, unknown>> {
  return runInDurableObject(stub, async (_, state) => Object.fromEntries(await state.storage.list()));
}

/** One call that the model answered: held, then charged. */
async function call(stub: Book, now: number, allowance: Allowance, requestId: string = crypto.randomUUID(), weight: number = READ) {
  const hold = await stub.hold(now, requestId, allowance, BILL, weight);
  if (hold.ok) await stub.commit(now, requestId, allowance, BILL, weight);
  return hold;
}

/** The book of a Google account whose id the proxy handed out as its purchaser, keeping [plan] when there is one. */
async function accountBook(plan?: PlanState): Promise<Book> {
  const stub = book();
  await stub.issuePurchaser(Date.now(), MONTHLY);
  if (plan !== undefined) await stub.storePlan(plan);
  return stub;
}

/** The usage of a Google account in the month of [now]. */
function month(now: number, plan: "free" | "pro", used: number, remaining: number) {
  const limit = plan === "pro" ? POLICY.proCalls : POLICY.monthlyCalls;
  return { limit, used, remaining, period: periodOf(MONTHLY, now), resetsAt: nextMonthStart(now), plan };
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
    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 3, remaining: 0, period: "trial", resetsAt: null, plan: "trial" });
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

describe("a Google account on Pro", () => {
  it("may make 300 calls in a month and is refused the next", async () => {
    const now = Date.now();
    const stub = await accountBook({ proUntil: now + 30 * POLICY.dayMs, checkedAt: now });
    for (let i = 0; i < POLICY.proCalls; i++) {
      expect((await call(stub, now, MONTHLY)).ok, `call ${i + 1}`).toBe(true);
    }

    const next = await stub.hold(now, crypto.randomUUID(), MONTHLY, BILL, EDIT);

    expect(next).toEqual({
      ok: false,
      code: "quota_exhausted",
      usage: month(now, "pro", 300, 0),
      retryAfter: Math.ceil((nextMonthStart(now) - now) / 1000),
    });
  }, 60_000);

  it("is Pro until a day after the time RevenueCat gave, and has the free calls after", async () => {
    const now = Date.now();
    const proUntil = now - POLICY.proGraceMs + 1;
    const stub = await accountBook({ proUntil, checkedAt: proUntil - POLICY.dayMs });
    for (let i = 0; i < POLICY.monthlyCalls; i++) await call(stub, now, MONTHLY);

    expect(await stub.hold(now, crypto.randomUUID(), MONTHLY, BILL, READ)).toMatchObject({ ok: true, usage: { limit: 300, plan: "pro" } });
    const after = proUntil + POLICY.proGraceMs;
    expect(await stub.usage(after, MONTHLY)).toEqual(month(after, "free", 10, 0));
    expect(await stub.hold(after, crypto.randomUUID(), MONTHLY, BILL, READ)).toMatchObject({
      ok: false,
      code: "quota_exhausted",
      usage: { limit: 10, plan: "free" },
    });
  });

  it("keeps what the month used when the plan changes during the month", async () => {
    const now = Date.now();
    const stub = await accountBook();
    for (let i = 0; i < 4; i++) await call(stub, now, MONTHLY);
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "free", 4, 6));

    await stub.storePlan({ proUntil: now + 30 * POLICY.dayMs, checkedAt: now });
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "pro", 4, 296));
    for (let i = 0; i < 8; i++) await call(stub, now, MONTHLY);
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "pro", 12, 288));

    await stub.storePlan({ proUntil: null, checkedAt: now + 1 });
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "free", 12, 0));
    expect(await stub.hold(now, crypto.randomUUID(), MONTHLY, BILL, EDIT)).toMatchObject({ ok: false, code: "quota_exhausted" });
  });

  it("never ends with an entitlement that has no end", async () => {
    const now = Date.now();
    const stub = await accountBook({ proUntil: Number.POSITIVE_INFINITY, checkedAt: now });
    const later = now + 10 * 365 * POLICY.dayMs;

    expect(await stub.usage(later, MONTHLY)).toEqual(month(later, "pro", 0, 300));
    expect((await storageOf(stub)).plan).toEqual({ proUntil: Number.POSITIVE_INFINITY, checkedAt: now });
  });

  it("leaves the trial of an install as it is", async () => {
    const now = Date.now();
    const stub = await accountBook({ proUntil: now + POLICY.dayMs, checkedAt: now });

    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 0, remaining: 3, period: "trial", resetsAt: null, plan: "trial" });
  });
});

describe("a plan", () => {
  it("is kept only for an account whose id went out as a purchaser, and forgotten with the account", async () => {
    const now = Date.now();
    const plan = { proUntil: now + POLICY.dayMs, checkedAt: now };
    const stub = book();

    await stub.storePlan(plan);
    expect(await stub.purchaserIssued()).toBe(false);
    expect(await storageOf(stub)).toEqual({});
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "free", 0, 10));

    expect(await stub.issuePurchaser(now, MONTHLY)).toEqual(month(now, "free", 0, 10));
    await stub.issuePurchaser(now + 1, MONTHLY);
    await stub.storePlan(plan);
    expect(await stub.purchaserIssued()).toBe(true);
    expect(await storageOf(stub)).toEqual({ issued: now, plan });
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "pro", 0, 300));

    await stub.forget();
    await stub.storePlan({ ...plan, checkedAt: now + 1 });
    expect(await stub.purchaserIssued()).toBe(false);
    expect(await storageOf(stub)).toEqual({});
  });

  it("keeps the one RevenueCat gave last, whatever order its answers come in", async () => {
    const now = Date.now();
    const later = { proUntil: null, checkedAt: now };
    const stub = await accountBook(later);

    await stub.storePlan({ proUntil: now + POLICY.dayMs, checkedAt: now - 1 });
    expect((await storageOf(stub)).plan).toEqual(later);
    expect(await stub.usage(now, MONTHLY)).toMatchObject({ plan: "free" });

    const latest = { proUntil: now + POLICY.dayMs, checkedAt: now + 1 };
    await stub.storePlan(latest);
    expect((await storageOf(stub)).plan).toEqual(latest);
    expect(await stub.usage(now, MONTHLY)).toMatchObject({ plan: "pro" });
  });

  it("reads as no Pro when it is not of the shape it is kept in, and is kept again in its shape", async () => {
    const now = Date.now();
    for (const plan of [
      { proUntil: "2099-01-01T00:00:00Z", checkedAt: now },
      { proUntil: Number.NaN, checkedAt: now },
      { proUntil: now + POLICY.dayMs },
      { proUntil: now + POLICY.dayMs, checkedAt: Number.POSITIVE_INFINITY },
      "pro",
    ]) {
      const stub = await bookWith({ issued: now, plan });
      expect(await stub.usage(now, MONTHLY), JSON.stringify(plan)).toEqual(month(now, "free", 0, 10));

      await stub.storePlan({ proUntil: now + POLICY.dayMs, checkedAt: now - POLICY.dayMs });
      expect(await stub.usage(now, MONTHLY), JSON.stringify(plan)).toEqual(month(now, "pro", 0, 300));
    }
  });

  it("is not kept when it is not of its shape", async () => {
    const now = Date.now();
    const stub = await accountBook();
    const plans: unknown[] = [
      { proUntil: now + POLICY.dayMs, checkedAt: Number.NaN },
      { proUntil: Number.NaN, checkedAt: now },
      { proUntil: "soon", checkedAt: now },
    ];
    for (const plan of plans) {
      // As a Worker of another version could send it: the object checks what it is given.
      await stub.storePlan(plan as PlanState);
    }

    expect(await storageOf(stub)).toEqual({ issued: expect.any(Number) });
  });
});

describe("the weight of a route", () => {
  it("is a whole number of hundredths from one hundredth to a whole call", () => {
    expect(WEIGHTS).toEqual({ billAnalysis: 100, splitRevision: 20 });
    for (const [calls, hundredths] of [
      [0.2, 20],
      [0.01, 1],
      [0.07, 7],
      [0.29, 29],
      [0.57, 57],
      [1, 100],
    ]) {
      expect(weightInHundredths(calls), String(calls)).toBe(hundredths);
    }
  });

  it("stops the Worker from loading when it is anything else, rather than be rounded without a word", () => {
    for (const calls of [0.004, 0.125, 0.0101, 0, -0.2, 1.01, 2, Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(() => weightInHundredths(calls), String(calls)).toThrow(/whole number of hundredths of a call from 0.01 to 1/);
    }
  });
});

describe("calls that weigh less than one", () => {
  it("are held and charged at their weight, and the usage says so in decimals", async () => {
    const stub = book();
    const now = Date.now();
    const id = crypto.randomUUID();

    expect(await stub.hold(now, id, TRIAL, BILL, EDIT)).toMatchObject({ ok: true, usage: { used: 0, remaining: 2.8 } });
    expect(await stub.commit(now, id, TRIAL, BILL, EDIT)).toEqual({ limit: 3, used: 0.2, remaining: 2.8, period: "trial", resetsAt: null, plan: "trial" });
    await call(stub, now, TRIAL, crypto.randomUUID(), EDIT);
    await call(stub, now, TRIAL);

    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 1.4, remaining: 1.6, period: "trial", resetsAt: null, plan: "trial" });
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
  it("is turned into hundredths at the first call, so that nobody gains or loses anything", async () => {
    const now = Date.now();
    const charged = crypto.randomUUID();
    const held = crypto.randomUUID();
    const stub = await bookWith({
      book: { period: "trial", used: 1 },
      [`request:${charged}`]: { heldAt: now, runs: 1, active: 0, committedAt: now, digest: BILL },
      [`request:${held}`]: { heldAt: now, runs: 1, active: 1, committedAt: null, digest: BILL },
    });

    expect(await stub.usage(now, TRIAL)).toEqual({ limit: 3, used: 1, remaining: 1, period: "trial", resetsAt: null, plan: "trial" });
    expect(await storageOf(stub)).toMatchObject({
      book: { period: "trial", hundredths: 100, used: 1 },
      [`request:${charged}`]: { committedAt: now, weight: 100 },
      [`request:${held}`]: { committedAt: null, weight: 100 },
    });

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
    const stub = await bookWith({
      book: { period: "trial", used: 2 },
      [`request:${held}`]: { heldAt: now, runs: 1, active: 1, committedAt: null, digest: BILL },
    });

    await stub.release(now, held);

    expect(await stub.usage(now, TRIAL)).toMatchObject({ used: 2, remaining: 1 });
  });

  it("keeps an account's count of the month it was written in", async () => {
    const now = Date.now();
    const stub = await bookWith({ book: { period: `${new Date(now).toISOString().slice(0, 7)}`, used: 7 } });

    expect(await stub.usage(now, MONTHLY)).toMatchObject({ used: 7, remaining: 3 });
    expect(await stub.usage(nextMonthStart(now), MONTHLY)).toMatchObject({ used: 0, remaining: 10 });
  });

  it("keeps a count already written in hundredths as it is", async () => {
    const now = Date.now();
    const stub = await bookWith({
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

describe("a rollback to the code deployed before calls had weights", () => {
  type OldBook = { period: string; used: number };

  /**
   * What that code does with the storage it finds, copied from it: it reads the count as `used`, in
   * whole calls, counts each request id not yet charged as one call held, refuses a call only when
   * nothing is left, and charges each call it makes as one more, writing the book back with
   * whatever else it held. [storage] is changed in place, as that code would change it.
   */
  function codeBeforeWeights(storage: Record<string, unknown>, allowance: Allowance, now: number) {
    const period = periodOf(allowance, now);
    const currentBook = (): OldBook => {
      const stored = storage.book as OldBook | undefined;
      return stored && stored.period === period ? stored : { period, used: 0 };
    };
    const usage = () => {
      const holding = Object.entries(storage).filter(([key, entry]) => key.startsWith("request:") && (entry as { committedAt: number | null }).committedAt === null).length;
      const { used } = currentBook();
      return { limit: allowance.limit, used, remaining: Math.max(0, allowance.limit - used - holding) };
    };
    /** A bill read the model answered, held and then charged; false when that code refuses it. */
    const call = (requestId: string): boolean => {
      if (usage().remaining <= 0) return false;
      const book = currentBook();
      book.used += 1;
      storage.book = book;
      storage[`request:${requestId}`] = { heldAt: now, runs: 1, active: 0, committedAt: now, digest: BILL };
      return true;
    };
    return { usage, call };
  }

  it("reads counts in whole calls, rounded up, so that it never hands out a call that is not left", async () => {
    const stub = book();
    const now = Date.now();
    await call(stub, now, TRIAL);
    await call(stub, now, TRIAL, crypto.randomUUID(), EDIT);
    const held = crypto.randomUUID();
    await stub.hold(now, held, TRIAL, BILL, EDIT);

    // 1.2 calls are charged and 0.2 held, which leaves 1.6. That code reads two calls charged, and
    // the hold as a whole call: it may hand out less than is left, but never more.
    const storage = await storageOf(stub);
    expect(storage.book).toEqual({ period: "trial", hundredths: 120, used: 2 });
    const before = codeBeforeWeights(storage, TRIAL, now);
    expect(JSON.stringify(before.usage())).toBe('{"limit":3,"used":2,"remaining":0}');
    expect(before.call(crypto.randomUUID())).toBe(false);

    await stub.release(now, held);
    const afterRelease = codeBeforeWeights(await storageOf(stub), TRIAL, now);
    expect(afterRelease.usage()).toEqual({ limit: 3, used: 2, remaining: 1 });
    expect(afterRelease.call(crypto.randomUUID())).toBe(true);
    expect(afterRelease.call(crypto.randomUUID())).toBe(false);
  });

  it("is taken back once this code runs again: what that code charged counts, and nobody gains a call", async () => {
    const stub = book();
    const now = Date.now();
    for (let edit = 0; edit < 3; edit++) await call(stub, now, TRIAL, crypto.randomUUID(), EDIT);
    const storage = await storageOf(stub);
    const before = codeBeforeWeights(storage, TRIAL, now);
    expect(before.usage()).toEqual({ limit: 3, used: 1, remaining: 2 });
    const read = crypto.randomUUID();
    expect(before.call(read)).toBe(true);
    expect(before.call(crypto.randomUUID())).toBe(true);
    expect(before.call(crypto.randomUUID())).toBe(false);

    // The object starts again on this code, with what that code left.
    const restarted = await bookWith(storage);

    expect(await restarted.usage(now, TRIAL)).toEqual({ limit: 3, used: 2.6, remaining: 0.4, period: "trial", resetsAt: null, plan: "trial" });
    expect(await storageOf(restarted)).toMatchObject({ book: { period: "trial", hundredths: 260, used: 3 }, [`request:${read}`]: { weight: 100 } });
    // A read that code charged is not charged again when it runs once more.
    expect(await call(restarted, now, TRIAL, read)).toMatchObject({ ok: true });
    expect(await restarted.usage(now, TRIAL)).toMatchObject({ used: 2.6, remaining: 0.4 });
    // A second rollback finds the count it left, and hands out nothing more.
    expect(codeBeforeWeights(await storageOf(restarted), TRIAL, now).usage()).toEqual({ limit: 3, used: 3, remaining: 0 });
  });

  it("starts the count of a new month from what that code wrote in it", async () => {
    const now = Date.now();
    const lastMonth = periodOf(MONTHLY, now - 32 * POLICY.dayMs);
    const storage: Record<string, unknown> = { book: { period: lastMonth, hundredths: 940, used: 10 } };
    // The month turned while that code ran, so it started the count again in whole calls.
    codeBeforeWeights(storage, MONTHLY, now).call(crypto.randomUUID());
    expect(storage.book).toEqual({ period: periodOf(MONTHLY, now), used: 1 });

    expect(await (await bookWith(storage)).usage(now, MONTHLY)).toMatchObject({ used: 1, remaining: 9 });
  });

  it("counts what is not a whole number, as a write cut off may leave, as nothing, so that no count becomes NaN", async () => {
    const now = Date.now();
    const notANumber = await bookWith({
      book: { period: "trial", hundredths: "120", used: 2 },
      [`request:${crypto.randomUUID()}`]: { heldAt: now, runs: 1, active: 1, committedAt: null, digest: BILL, weight: Number.NaN },
    });
    const neither = await bookWith({ book: { period: "trial", hundredths: Number.NaN, used: null } });
    const noWhole = await bookWith({ book: { period: "trial", hundredths: 280, used: Number.NaN } });

    // The whole calls stand in for the hundredths, and a hold with no weight holds a whole call.
    expect(await notANumber.usage(now, TRIAL)).toEqual({ limit: 3, used: 2, remaining: 0, period: "trial", resetsAt: null, plan: "trial" });
    expect(await neither.usage(now, TRIAL)).toEqual({ limit: 3, used: 0, remaining: 3, period: "trial", resetsAt: null, plan: "trial" });
    expect(await noWhole.usage(now, TRIAL)).toEqual({ limit: 3, used: 2.8, remaining: 0.2, period: "trial", resetsAt: null, plan: "trial" });
    expect((await storageOf(noWhole)).book).toEqual({ period: "trial", hundredths: 280, used: 3 });
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

describe("a deleted account", () => {
  it("keeps the count of the month alone, and wakes when the month ends", async () => {
    const now = Date.now();
    const stub = await accountBook({ proUntil: now + POLICY.dayMs, checkedAt: now });
    await call(stub, now, MONTHLY);
    await call(stub, now, MONTHLY, crypto.randomUUID(), EDIT);
    expect(await stub.hold(now, crypto.randomUUID(), MONTHLY, BILL, READ)).toMatchObject({ ok: true });

    await stub.forget(now);

    expect(await storageOf(stub)).toEqual({ book: { period: periodOf(MONTHLY, now), hundredths: 120, used: 2 } });
    expect(await stub.purchaserIssued()).toBe(false);
    expect(await stub.usage(now, MONTHLY)).toEqual(month(now, "free", 1.2, 8.8));
    expect(await runInDurableObject(stub, (_, state) => state.storage.getAlarm())).toBe(nextMonthStart(now));
  });

  it("charges to that count a call that was held when it was deleted, once the model answers", async () => {
    const now = Date.now();
    const stub = book();
    await call(stub, now, MONTHLY);
    const held = crypto.randomUUID();
    expect(await stub.hold(now, held, MONTHLY, BILL, READ)).toMatchObject({ ok: true });

    await stub.forget(now);

    expect(await stub.commit(now, held, MONTHLY, BILL, READ)).toEqual(month(now, "free", 2, 8));
  });

  it("keeps no count of a month that is over, nor anything else", async () => {
    const now = Date.now();
    const lastMonth = nextMonthStart(now) - 40 * POLICY.dayMs;
    const stub = await accountBook();
    await call(stub, lastMonth, MONTHLY);

    await stub.forget(now);

    expect(await storageOf(stub)).toEqual({});
  });

  it("deletes the count once its month is over", async () => {
    const now = Date.now();
    const lastMonth = nextMonthStart(now) - 40 * POLICY.dayMs;
    const stub = await accountBook();
    await call(stub, lastMonth, MONTHLY);
    // Deleted last month: the alarm for the month's end is due now.
    await stub.forget(lastMonth);
    await runDurableObjectAlarm(stub);

    expect(await storageOf(stub)).toEqual({});
  });

  it("wakes again when the month ends, when it was woken before that", async () => {
    const now = Date.now();
    const stub = book();
    await call(stub, now, MONTHLY);
    await stub.forget(now);
    await runInDurableObject(stub, (_, state) => state.storage.setAlarm(now));

    expect(await runDurableObjectAlarm(stub)).toBe(true);

    expect(await storageOf(stub)).toEqual({ book: { period: periodOf(MONTHLY, now), hundredths: 100, used: 1 } });
    expect(await runInDurableObject(stub, (_, state) => state.storage.getAlarm())).toBe(nextMonthStart(now));
  });

  it("leaves the trial of an install alone, whenever it wakes", async () => {
    const now = Date.now();
    const stub = book();
    await call(stub, now, TRIAL);
    await runInDurableObject(stub, (_, state) => state.storage.setAlarm(now));

    await runDurableObjectAlarm(stub);

    expect((await storageOf(stub)).book).toEqual({ period: "trial", hundredths: 100, used: 1 });
  });
});

describe("the first call of an install", () => {
  it("is told apart once, when it is held, whatever comes of it", async () => {
    const now = Date.now();
    const stub = book();
    const first = crypto.randomUUID();

    const held = await stub.hold(now, first, TRIAL, BILL, READ);
    const again = await stub.hold(now, first, TRIAL, BILL, READ);
    await stub.release(now, first);
    await stub.release(now, first);
    const next = await stub.hold(now, crypto.randomUUID(), TRIAL, BILL, READ);

    expect(held).toMatchObject({ ok: true, first: true });
    expect(again).not.toHaveProperty("first");
    expect(next).toMatchObject({ ok: true });
    expect(next).not.toHaveProperty("first");
    // The mark says that it happened, never when.
    expect((await storageOf(stub)).firstCall).toBe(true);
  });

  it("is never an account's call, nor one of an install that had calls charged before the mark existed, or whose mark holds a time", async () => {
    const now = Date.now();
    const account = await accountBook();
    const older = await bookWith({ book: { period: "trial", hundredths: 100, used: 1 } });
    // As the version before kept the mark: the time of the first call.
    const timed = await bookWith({ firstCall: now - POLICY.dayMs });

    for (const stub of [account, older, timed]) {
      const held = await stub.hold(now, crypto.randomUUID(), stub === account ? MONTHLY : TRIAL, BILL, READ);
      expect(held).toMatchObject({ ok: true });
      expect(held).not.toHaveProperty("first");
    }
  });
});
