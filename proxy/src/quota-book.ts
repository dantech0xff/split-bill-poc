import { DurableObject } from "cloudflare:workers";
import { POLICY } from "./policy";

/**
 * What a caller may use. A trial is a number of calls for good; monthly is a number of calls in
 * each calendar month, counted in UTC.
 */
export type Allowance = { kind: "trial" | "monthly"; limit: number };

/** How much of its allowance a caller has used. A trial never starts over, so it has no reset time. */
export type Usage = { limit: number; used: number; remaining: number; period: string; resetsAt: number | null };

export type HoldResult =
  | { ok: true; usage: Usage }
  | { ok: false; code: "quota_exhausted" | "retry_limit"; usage: Usage; retryAfter: number | null };

/** One request id: when it was first held, how often it ran, and when it was charged, if it was. */
type RequestEntry = { heldAt: number; runs: number; committedAt: number | null };
type Book = { period: string; used: number };

const BOOK = "book";
const REQUEST = "request:";

/**
 * The calls of one caller: an install that is not signed in, or a Google account. Every request of
 * a caller runs on this one object, one after another, so two requests can never both take the
 * last call. Storage holds counts and request ids only.
 *
 * A call is held before the model runs, committed once the model has answered, and released when
 * the model failed before it could charge. A hold that is neither committed nor released within
 * [POLICY.holdMs] is given back, so a request cut off half way does not cost the person a call.
 */
export class QuotaBook extends DurableObject {
  async hold(now: number, requestId: string, allowance: Allowance): Promise<HoldResult> {
    const book = await this.currentBook(now, allowance);
    const requests = await this.liveRequests(now);
    const known = requests.get(requestId);
    if (known) {
      // The same request again, after a dropped connection for instance: it shares the call it
      // already holds or was charged, but it may only run a few times.
      if (known.runs >= POLICY.runsPerRequest) {
        return { ok: false, code: "retry_limit", usage: usageOf(book, requests, allowance, now), retryAfter: null };
      }
      known.runs += 1;
      await this.ctx.storage.put(REQUEST + requestId, known);
      return { ok: true, usage: usageOf(book, requests, allowance, now) };
    }
    const usage = usageOf(book, requests, allowance, now);
    if (usage.remaining <= 0) {
      const retryAfter = usage.resetsAt === null ? null : secondsUntil(now, usage.resetsAt);
      return { ok: false, code: "quota_exhausted", usage, retryAfter };
    }
    const entry: RequestEntry = { heldAt: now, runs: 1, committedAt: null };
    requests.set(requestId, entry);
    await this.ctx.storage.put(REQUEST + requestId, entry);
    await this.arm(now);
    return { ok: true, usage: usageOf(book, requests, allowance, now) };
  }

  /** Charges the call of [requestId] once the model has answered. Charging the same request twice does nothing. */
  async commit(now: number, requestId: string, allowance: Allowance): Promise<Usage> {
    const book = await this.currentBook(now, allowance);
    const entry = (await this.ctx.storage.get<RequestEntry>(REQUEST + requestId)) ?? { heldAt: now, runs: 1, committedAt: null };
    if (entry.committedAt === null) {
      entry.committedAt = now;
      book.used += 1;
      await this.ctx.storage.put({ [BOOK]: book, [REQUEST + requestId]: entry });
      await this.arm(now);
    }
    return usageOf(book, await this.liveRequests(now), allowance, now);
  }

  /** Gives back the call of [requestId] when the model failed before it could charge. A charged call stays charged. */
  async release(now: number, requestId: string): Promise<void> {
    const entry = await this.ctx.storage.get<RequestEntry>(REQUEST + requestId);
    if (entry && entry.committedAt === null) {
      await this.ctx.storage.delete(REQUEST + requestId);
    }
  }

  async usage(now: number, allowance: Allowance): Promise<Usage> {
    return usageOf(await this.currentBook(now, allowance), await this.liveRequests(now), allowance, now);
  }

  /** Deletes everything this object holds, for a deleted account. */
  async forget(): Promise<void> {
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
  }

  async alarm(): Promise<void> {
    const now = Date.now();
    await this.liveRequests(now);
    await this.arm(now);
  }

  /** The count of the current period. A new calendar month starts a monthly count from zero. */
  private async currentBook(now: number, allowance: Allowance): Promise<Book> {
    const period = periodOf(allowance, now);
    const stored = await this.ctx.storage.get<Book>(BOOK);
    return stored && stored.period === period ? stored : { period, used: 0 };
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

function forgetAt(entry: RequestEntry): number {
  return entry.committedAt === null ? entry.heldAt + POLICY.holdMs : entry.committedAt + POLICY.requestMemoryMs;
}

function usageOf(book: Book, requests: Map<string, RequestEntry>, allowance: Allowance, now: number): Usage {
  const holding = [...requests.values()].filter((entry) => entry.committedAt === null).length;
  return {
    limit: allowance.limit,
    used: book.used,
    remaining: Math.max(0, allowance.limit - book.used - holding),
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
