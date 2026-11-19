import { DurableObject } from "cloudflare:workers";
import { isRecord } from "./input";
import { POLICY } from "./policy";

/**
 * What a caller may use. A trial is a number of calls for good; monthly is a number of calls in
 * each calendar month, counted in UTC.
 */
export type Allowance = { kind: "trial" | "monthly"; limit: number };

/** The plan a caller is on: the trial of an install, or the free plan or Pro of a Google account. */
export type Plan = "trial" | "free" | "pro";

/**
 * How much of its allowance a caller has used, in calls, and the plan the allowance comes from. A
 * call can weigh less than one, so the numbers have up to two decimals, such as 2.8. A trial never
 * starts over, so it has no reset time.
 */
export type Usage = { limit: number; used: number; remaining: number; period: string; resetsAt: number | null; plan: Plan };

/**
 * A Google account's subscription as RevenueCat last told of it: Pro until [proUntil], in ms since
 * the epoch, which is infinite for an entitlement that never ends, or no Pro for null; and
 * [checkedAt], when RevenueCat answered, by RevenueCat's clock.
 */
export type PlanState = { proUntil: number | null; checkedAt: number };

/**
 * What a hold comes to. [first] is set on the first call ever held for an install that is not
 * signed in, once per install, so that the proxy can count new installs.
 */
export type HoldResult =
  | { ok: true; usage: Usage; first?: true }
  | { ok: false; code: "quota_exhausted" | "retry_limit" | "request_mismatch"; usage: Usage; retryAfter: number | null };

/** Allowances are counted in hundredths of a call, so that a call of any weight is a whole number. */
export const PER_CALL = 100;

/** [calls], which has at most two decimals, in hundredths of a call. */
export function hundredthsOf(calls: number): number {
  return Math.round(calls * PER_CALL);
}

/**
 * One request id: when it was last held, how often it ran, how many of its runs are still waiting
 * for the model, when it was charged if it was, a salted digest of what it asked, so that the id
 * cannot be used again for another bill, and its weight: the hundredths of a call it holds and is
 * charged.
 */
type RequestEntry = { heldAt: number; runs: number; active: number; committedAt: number | null; digest: string; weight: number };
/** The count of one period, in hundredths of a call. */
type Book = { period: string; hundredths: number };

/**
 * A [Book] as this code writes it, with `used`: the same count in whole calls, rounded up. The code
 * deployed before calls had weights reads `used` alone, as after a rollback, so that code never
 * hands out a call that is not left.
 */
type WrittenBook = Book & { used: number };

/**
 * What storage may hold as the book. Before calls had weights it held `used` alone, in whole calls,
 * and request ids had no weight, each holding one call. That code still runs on this storage after
 * a rollback: it reads `used`, charges each call it makes as one more, and writes the book back with
 * whatever else it held.
 */
type StoredBook = { period: string; hundredths?: unknown; used?: unknown };
type StoredEntry = Omit<RequestEntry, "weight"> & { weight?: unknown };

/** The allowance that holds for a caller at one time, and the plan it comes from. */
type Terms = { allowance: Allowance; plan: Plan };

const BOOK = "book";
const REQUEST = "request:";
/** A Google account's plan, as [PlanState]. */
const PLAN = "plan";
/** When the proxy first handed a Google account's id to the app as its purchaser. */
const ISSUED = "issued";
/** That an install has had a call held, so that its first call is told apart once; never when. */
const FIRST_CALL = "firstCall";
/** The most keys one write of storage takes. */
const MAX_PUT_KEYS = 128;

/**
 * The calls of one caller: an install that is not signed in, or a Google account. Every request of
 * a caller runs on this one object, one after another, so two requests can never both take the
 * last call. Storage holds counts and request ids, and for a Google account its plan and when its
 * id was first handed out as a purchaser.
 *
 * A Google account's monthly allowance is Pro's, [POLICY.proCalls], at every time its plan says it
 * is Pro, and the free one otherwise. The object decides that each time it holds, charges or reports
 * a call, so that the plan and the count are read together. Both plans share the month's count.
 *
 * A call is held before the model runs, committed once the model has answered, and released when
 * no answer came. Each run of a request holds a share of the request's one call and gives its own
 * share back; the call is given back once no run holds it. A hold that is neither committed nor
 * released within [POLICY.holdMs] is given back, so a request cut off half way costs nothing.
 *
 * A call has a weight, in hundredths of a call: a bill read takes a whole call, an edit of a split
 * less. It runs only while what is left of the allowance is at least its weight, and its request id
 * keeps the weight it was first held with.
 *
 * A deleted account's object keeps the count of the calendar month it was deleted in, and nothing
 * else, until that month ends, then deletes itself; see [forget].
 */
export class QuotaBook extends DurableObject {
  /** Whether this object has looked for counts in whole calls since it started; see [upgrade]. */
  private upgraded = false;

  /**
   * Holds a call of [weight] for [requestId] while what is left of the allowance that holds at [now]
   * is at least that. [allowance] is the caller's without Pro; see [termsAt].
   */
  async hold(now: number, requestId: string, allowance: Allowance, digest: string, weight: number): Promise<HoldResult> {
    await this.upgrade();
    const terms = await this.termsAt(now, allowance);
    const book = await this.currentBook(now, terms.allowance);
    const requests = await this.liveRequests(now);
    const known = requests.get(requestId);
    if (known) {
      if (known.digest !== digest) {
        return { ok: false, code: "request_mismatch", usage: usageOf(book, requests, terms, now), retryAfter: null };
      }
      // The same request again, after a dropped connection for instance: it shares the call it
      // already holds or was charged, but it may only run a few times.
      if (known.runs >= POLICY.runsPerRequest) {
        return { ok: false, code: "retry_limit", usage: usageOf(book, requests, terms, now), retryAfter: null };
      }
      known.runs += 1;
      known.active += 1;
      known.heldAt = now;
      await this.ctx.storage.put(REQUEST + requestId, known);
      await this.arm(now);
      return { ok: true, usage: usageOf(book, requests, terms, now) };
    }
    const cost = weightOf(weight);
    if (countOf(book, requests, terms.allowance).remaining < cost) {
      const usage = usageOf(book, requests, terms, now);
      const retryAfter = usage.resetsAt === null ? null : secondsUntil(now, usage.resetsAt);
      return { ok: false, code: "quota_exhausted", usage, retryAfter };
    }
    // An install's first call: nothing charged, nothing held, and no first call marked. An install
    // that had calls charged before the mark existed has a count, so it is never taken for new.
    const first =
      allowance.kind === "trial" && book.hundredths === 0 && requests.size === 0 && (await this.ctx.storage.get(FIRST_CALL)) === undefined;
    const entry: RequestEntry = { heldAt: now, runs: 1, active: 1, committedAt: null, digest, weight: cost };
    requests.set(requestId, entry);
    await this.ctx.storage.put(first ? { [REQUEST + requestId]: entry, [FIRST_CALL]: true } : { [REQUEST + requestId]: entry });
    await this.arm(now);
    const usage = usageOf(book, requests, terms, now);
    return first ? { ok: true, usage, first } : { ok: true, usage };
  }

  /**
   * Charges the call of [requestId] once the model has answered, at the weight it was held with.
   * Charging the same request twice does nothing. [weight] counts only for a hold already given
   * back, as one that outlived [POLICY.holdMs].
   */
  async commit(now: number, requestId: string, allowance: Allowance, digest: string, weight: number): Promise<Usage> {
    await this.upgrade();
    const terms = await this.termsAt(now, allowance);
    const book = await this.currentBook(now, terms.allowance);
    const stored = await this.ctx.storage.get<RequestEntry>(REQUEST + requestId);
    const entry = stored ?? { heldAt: now, runs: 1, active: 1, committedAt: null, digest, weight: weightOf(weight) };
    entry.active = Math.max(0, entry.active - 1);
    if (entry.committedAt === null) {
      entry.committedAt = now;
      book.hundredths += weightOf(entry.weight);
      await this.ctx.storage.put({ [BOOK]: written(book), [REQUEST + requestId]: entry });
    } else {
      await this.ctx.storage.put(REQUEST + requestId, entry);
    }
    await this.arm(now);
    return usageOf(book, await this.liveRequests(now), terms, now);
  }

  /**
   * Gives back this run's share of the call of [requestId] when it got no answer. The call itself
   * is given back once no run holds it any more; a charged call stays charged.
   */
  async release(now: number, requestId: string): Promise<void> {
    await this.upgrade();
    const entry = await this.ctx.storage.get<RequestEntry>(REQUEST + requestId);
    if (!entry) return;
    entry.active = Math.max(0, entry.active - 1);
    if (entry.committedAt === null && entry.active === 0) {
      await this.ctx.storage.delete(REQUEST + requestId);
    } else {
      await this.ctx.storage.put(REQUEST + requestId, entry);
    }
  }

  async usage(now: number, allowance: Allowance): Promise<Usage> {
    await this.upgrade();
    const terms = await this.termsAt(now, allowance);
    return usageOf(await this.currentBook(now, terms.allowance), await this.liveRequests(now), terms, now);
  }

  /**
   * [usage], for a Google account whose id the proxy is handing to the app as its purchaser, the id
   * the app gives RevenueCat. The first time, the object records that the id went out: the proxy
   * asks RevenueCat only about ids it handed out, because RevenueCat's read creates a customer that
   * does not exist. The record goes with everything else when the account is deleted.
   */
  async issuePurchaser(now: number, allowance: Allowance): Promise<Usage> {
    if ((await this.ctx.storage.get(ISSUED)) === undefined) await this.ctx.storage.put(ISSUED, now);
    return this.usage(now, allowance);
  }

  /** Whether the proxy handed this account's id out as a purchaser and has not forgotten it since. */
  async purchaserIssued(): Promise<boolean> {
    return (await this.ctx.storage.get(ISSUED)) !== undefined;
  }

  /**
   * Keeps [plan], read from RevenueCat, as the account's plan, unless the plan already kept was read
   * later: answers that arrive out of order leave the latest, so an event repeated or late changes
   * nothing. Nothing is kept for an id the proxy never handed out or has forgotten, such as a deleted
   * account's, nor for a plan that is not of the shape [PlanState] gives.
   */
  async storePlan(plan: PlanState): Promise<void> {
    const next = planStateOf(plan);
    if (next === null || !(await this.purchaserIssued())) return;
    const stored = planStateOf(await this.ctx.storage.get(PLAN));
    if (stored === null || next.checkedAt >= stored.checkedAt) await this.ctx.storage.put(PLAN, next);
  }

  /**
   * Forgets a deleted account as of [now]: its plan, the record of its purchaser, the request ids
   * it remembers and everything else go, but the count of the calendar month of [now]. That count
   * stays until the month ends, so that deleting the account and signing in again starts no new
   * allowance; it holds nothing but the month and the calls used, under the account's salted
   * hash. The object wakes when the month ends and deletes it; see [alarm]. A call still held
   * runs on, and is charged to that count once the model answers. [now] defaults to the time of
   * the call, as a Worker deployed before the count was kept sends none.
   */
  async forget(now: number = Date.now()): Promise<void> {
    const book = await this.ctx.storage.get<StoredBook>(BOOK);
    if (book === undefined || book.period !== monthOf(now)) {
      await this.ctx.storage.deleteAlarm();
      await this.ctx.storage.deleteAll();
      return;
    }
    const others = [...(await this.ctx.storage.list()).keys()].filter((key) => key !== BOOK);
    const deletions: Array<Promise<number>> = [];
    for (let start = 0; start < others.length; start += MAX_PUT_KEYS) {
      deletions.push(this.ctx.storage.delete(others.slice(start, start + MAX_PUT_KEYS)));
    }
    // Issued with no wait between them, the deletions land in one write.
    await Promise.all(deletions);
    await this.ctx.storage.setAlarm(nextMonthStart(now));
  }

  /**
   * Forgets the request ids whose time is up, then sleeps until the next one is due. An object that
   * holds nothing else but the count of one calendar month, as a deleted account's does, deletes
   * itself once that month has ended, and otherwise wakes again when it ends.
   */
  async alarm(): Promise<void> {
    await this.upgrade();
    const now = Date.now();
    const requests = await this.liveRequests(now);
    const month = requests.size === 0 ? await this.loneMonth() : null;
    if (month === null) {
      await this.arm(now);
    } else if (month === monthOf(now)) {
      await this.ctx.storage.setAlarm(nextMonthStart(now));
    } else {
      await this.ctx.storage.deleteAll();
    }
  }

  /**
   * Puts what the code deployed before calls had weights wrote into this code's form, the first
   * time anything asks after this code starts: the book as [bookOf] counts it, with the calls that
   * code charged, and each request id without a weight as one that holds a whole call. That code
   * wrote such records before weights were first deployed, and writes them again after any rollback
   * to it. Records already in this form are left as they are, so this can run again safely, and the
   * object looks only once while it runs. Each record is written on its own, so a write cut off half
   * way loses nothing.
   */
  private async upgrade(): Promise<void> {
    if (this.upgraded) return;
    const changes: Array<[string, WrittenBook | RequestEntry]> = [];
    const stored = await this.ctx.storage.get<StoredBook>(BOOK);
    if (stored !== undefined) {
      const book = written(bookOf(stored));
      if (book.hundredths !== stored.hundredths || book.used !== stored.used) changes.push([BOOK, book]);
    }
    for (const [key, entry] of await this.ctx.storage.list<StoredEntry>({ prefix: REQUEST })) {
      const weight = weightOf(entry.weight);
      if (entry.weight !== weight) changes.push([key, { ...entry, weight }]);
    }
    for (let start = 0; start < changes.length; start += MAX_PUT_KEYS) {
      await this.ctx.storage.put(Object.fromEntries(changes.slice(start, start + MAX_PUT_KEYS)));
    }
    this.upgraded = true;
  }

  /**
   * The allowance that holds at [now] for a caller whose allowance without Pro is [allowance]: an
   * install keeps its trial, and a Google account has Pro's calls while its plan is Pro.
   */
  private async termsAt(now: number, allowance: Allowance): Promise<Terms> {
    if (allowance.kind === "trial") return { allowance, plan: "trial" };
    if (!isProAt(planStateOf(await this.ctx.storage.get(PLAN)), now)) return { allowance, plan: "free" };
    return { allowance: { kind: "monthly", limit: POLICY.proCalls }, plan: "pro" };
  }

  /** The count of the current period. A new calendar month starts a monthly count from zero. */
  private async currentBook(now: number, allowance: Allowance): Promise<Book> {
    const period = periodOf(allowance, now);
    const stored = await this.ctx.storage.get<StoredBook>(BOOK);
    return stored && stored.period === period ? bookOf(stored) : { period, hundredths: 0 };
  }

  /** Request ids that still matter: uncharged holds not yet given back, and charged ones still remembered. */
  private async liveRequests(now: number): Promise<Map<string, RequestEntry>> {
    const stored = await this.ctx.storage.list<RequestEntry>({ prefix: REQUEST });
    const live = new Map<string, RequestEntry>();
    const expired: string[] = [];
    for (const [key, entry] of stored) {
      if (now < forgetAt(entry)) {
        live.set(key.slice(REQUEST.length), entry);
      } else {
        expired.push(key);
      }
    }
    if (expired.length > 0) {
      await this.ctx.storage.delete(expired);
    }
    return live;
  }

  /** The month of the count, when storage holds a monthly count and nothing else. */
  private async loneMonth(): Promise<string | null> {
    const stored = await this.ctx.storage.list<StoredBook>();
    const book = stored.get(BOOK);
    return stored.size === 1 && book !== undefined && book.period !== "trial" ? book.period : null;
  }

  /** Wakes the object when the next request id is due to be forgotten, so storage does not grow. */
  private async arm(now: number) {
    const stored = await this.ctx.storage.list<RequestEntry>({ prefix: REQUEST });
    const times = [...stored.values()].map(forgetAt).filter((time) => time > now);
    if (times.length > 0) {
      await this.ctx.storage.setAlarm(Math.min(...times));
    }
  }
}

/**
 * [weight] when it is a whole number of hundredths above zero, as every route sends. A Worker
 * deployed before calls had weights, still finishing a bill read while the new one starts, sends
 * none, and the request ids it wrote have none: their call is a whole one, as is that of anything
 * else, so that no count can become NaN.
 */
function weightOf(weight: unknown): number {
  return typeof weight === "number" && Number.isSafeInteger(weight) && weight > 0 ? weight : PER_CALL;
}

/** [value] when it is a whole number of zero or more, as every count storage holds should be; null otherwise, NaN among them. */
function wholeCountOf(value: unknown): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

/**
 * The count [stored] holds, in hundredths of a call. A book written before calls had weights has
 * whole calls only. A book this code wrote has both; when its whole calls are more than its
 * hundredths come to, the code before weights ran on it since, after a rollback, and charged each
 * call it made as a whole one, so those calls are counted too, and nobody gains one by a rollback.
 * A count that is not a whole number of zero or more counts as none, so that none becomes NaN.
 */
function bookOf(stored: StoredBook): Book {
  const calls = wholeCountOf(stored.used);
  const hundredths = wholeCountOf(stored.hundredths);
  if (hundredths === null) return { period: stored.period, hundredths: (calls ?? 0) * PER_CALL };
  const chargedSince = Math.max(0, (calls ?? 0) - wholeCallsOf(hundredths));
  return { period: stored.period, hundredths: hundredths + chargedSince * PER_CALL };
}

/** [book] as storage keeps it: with its count in whole calls too, for the code before weights. */
function written(book: Book): WrittenBook {
  return { ...book, used: wholeCallsOf(book.hundredths) };
}

/** [hundredths] in whole calls, rounded up, so that a part of a call counts as a whole one. */
function wholeCallsOf(hundredths: number): number {
  return Math.ceil(hundredths / PER_CALL);
}

function forgetAt(entry: RequestEntry): number {
  return entry.committedAt === null ? entry.heldAt + POLICY.holdMs : entry.committedAt + POLICY.requestMemoryMs;
}

/** The allowance, what was charged and what is left, in hundredths of a call. A call still held counts as used. */
function countOf(book: Book, requests: Map<string, RequestEntry>, allowance: Allowance): { limit: number; used: number; remaining: number } {
  const holding = [...requests.values()].reduce((sum, entry) => (entry.committedAt === null ? sum + weightOf(entry.weight) : sum), 0);
  const limit = hundredthsOf(allowance.limit);
  return { limit, used: book.hundredths, remaining: Math.max(0, limit - book.hundredths - holding) };
}

/** [countOf] in calls, as the app reads it: a number of hundredths over 100 prints with at most two decimals. */
function usageOf(book: Book, requests: Map<string, RequestEntry>, terms: Terms, now: number): Usage {
  const count = countOf(book, requests, terms.allowance);
  return {
    limit: count.limit / PER_CALL,
    used: count.used / PER_CALL,
    remaining: count.remaining / PER_CALL,
    period: book.period,
    resetsAt: terms.allowance.kind === "monthly" ? nextMonthStart(now) : null,
    plan: terms.plan,
  };
}

/**
 * [value] when it is a plan as [QuotaBook.storePlan] keeps one: [PlanState.checkedAt] a finite time,
 * and [PlanState.proUntil] null or a time, infinite for an entitlement that never ends. Null for
 * anything else, as for an account that never had a plan, so that a record cut off half way reads
 * as no Pro rather than as NaN.
 */
function planStateOf(value: unknown): PlanState | null {
  if (!isRecord(value) || typeof value.checkedAt !== "number" || !Number.isFinite(value.checkedAt)) return null;
  const { proUntil, checkedAt } = value;
  if (proUntil === null) return { proUntil, checkedAt };
  return typeof proUntil === "number" && !Number.isNaN(proUntil) ? { proUntil, checkedAt } : null;
}

/** Whether [plan] makes its account Pro at [now]: until [POLICY.proGraceMs] after the time RevenueCat gave. */
function isProAt(plan: PlanState | null, now: number): boolean {
  return plan !== null && plan.proUntil !== null && now < plan.proUntil + POLICY.proGraceMs;
}

/** "trial" for a trial, and the calendar month in UTC, such as "2026-11", for a monthly allowance. */
export function periodOf(allowance: Allowance, now: number): string {
  return allowance.kind === "trial" ? "trial" : monthOf(now);
}

/** The calendar month of [now] in UTC, such as "2026-11". */
function monthOf(now: number): string {
  const date = new Date(now);
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

export function nextMonthStart(now: number): number {
  const date = new Date(now);
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 1);
}

function secondsUntil(now: number, time: number): number {
  return Math.max(1, Math.ceil((time - now) / 1000));
}
