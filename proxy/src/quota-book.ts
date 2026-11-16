import { DurableObject } from "cloudflare:workers";
import { POLICY } from "./policy";

/**
 * What a caller may use. A trial is a number of calls for good; monthly is a number of calls in
 * each calendar month, counted in UTC.
 */
export type Allowance = { kind: "trial" | "monthly"; limit: number };

/**
 * How much of its allowance a caller has used, in calls. A call can weigh less than one, so the
 * numbers have up to two decimals, such as 2.8. A trial never starts over, so it has no reset time.
 */
export type Usage = { limit: number; used: number; remaining: number; period: string; resetsAt: number | null };

export type HoldResult =
  | { ok: true; usage: Usage }
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

const BOOK = "book";
const REQUEST = "request:";
/** The most keys one write of storage takes. */
const MAX_PUT_KEYS = 128;

/**
 * The calls of one caller: an install that is not signed in, or a Google account. Every request of
 * a caller runs on this one object, one after another, so two requests can never both take the
 * last call. Storage holds counts and request ids only.
 *
 * A call is held before the model runs, committed once the model has answered, and released when
 * no answer came. Each run of a request holds a share of the request's one call and gives its own
 * share back; the call is given back once no run holds it. A hold that is neither committed nor
 * released within [POLICY.holdMs] is given back, so a request cut off half way costs nothing.
 *
 * A call has a weight, in hundredths of a call: a bill read takes a whole call, an edit of a split
 * less. It runs only while what is left of the allowance is at least its weight, and its request id
 * keeps the weight it was first held with.
 */
export class QuotaBook extends DurableObject {
  /** Whether this object has looked for counts in whole calls since it started; see [upgrade]. */
  private upgraded = false;

  async hold(now: number, requestId: string, allowance: Allowance, digest: string, weight: number): Promise<HoldResult> {
    await this.upgrade();
    const book = await this.currentBook(now, allowance);
    const requests = await this.liveRequests(now);
    const known = requests.get(requestId);
    if (known) {
      if (known.digest !== digest) {
        return { ok: false, code: "request_mismatch", usage: usageOf(book, requests, allowance, now), retryAfter: null };
      }
      // The same request again, after a dropped connection for instance: it shares the call it
      // already holds or was charged, but it may only run a few times.
      if (known.runs >= POLICY.runsPerRequest) {
        return { ok: false, code: "retry_limit", usage: usageOf(book, requests, allowance, now), retryAfter: null };
      }
      known.runs += 1;
      known.active += 1;
      known.heldAt = now;
      await this.ctx.storage.put(REQUEST + requestId, known);
      await this.arm(now);
      return { ok: true, usage: usageOf(book, requests, allowance, now) };
    }
    const cost = weightOf(weight);
    if (countOf(book, requests, allowance).remaining < cost) {
      const usage = usageOf(book, requests, allowance, now);
      const retryAfter = usage.resetsAt === null ? null : secondsUntil(now, usage.resetsAt);
      return { ok: false, code: "quota_exhausted", usage, retryAfter };
    }
    const entry: RequestEntry = { heldAt: now, runs: 1, active: 1, committedAt: null, digest, weight: cost };
    requests.set(requestId, entry);
    await this.ctx.storage.put(REQUEST + requestId, entry);
    await this.arm(now);
    return { ok: true, usage: usageOf(book, requests, allowance, now) };
  }

  /**
   * Charges the call of [requestId] once the model has answered, at the weight it was held with.
   * Charging the same request twice does nothing. [weight] counts only for a hold already given
   * back, as one that outlived [POLICY.holdMs].
   */
  async commit(now: number, requestId: string, allowance: Allowance, digest: string, weight: number): Promise<Usage> {
    await this.upgrade();
    const book = await this.currentBook(now, allowance);
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
    return usageOf(book, await this.liveRequests(now), allowance, now);
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
    return usageOf(await this.currentBook(now, allowance), await this.liveRequests(now), allowance, now);
  }

  /** Deletes everything this object holds, for a deleted account. */
  async forget(): Promise<void> {
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
  }

  async alarm(): Promise<void> {
    await this.upgrade();
    const now = Date.now();
    await this.liveRequests(now);
    await this.arm(now);
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
function usageOf(book: Book, requests: Map<string, RequestEntry>, allowance: Allowance, now: number): Usage {
  const count = countOf(book, requests, allowance);
  return {
    limit: count.limit / PER_CALL,
    used: count.used / PER_CALL,
    remaining: count.remaining / PER_CALL,
    period: book.period,
    resetsAt: allowance.kind === "monthly" ? nextMonthStart(now) : null,
  };
}

/** "trial" for a trial, and the calendar month in UTC, such as "2026-11", for a monthly allowance. */
export function periodOf(allowance: Allowance, now: number): string {
  if (allowance.kind === "trial") return "trial";
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
