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
 * signed in, once per install, so that the proxy can count new installs. `session` refuses a call
 * whose session was issued before the account was deleted; see [QuotaBook.sessionEnded].
 */
export type HoldResult =
  | { ok: true; usage: Usage; first?: true }
  | { ok: false; code: "quota_exhausted" | "retry_limit" | "request_mismatch"; usage: Usage; retryAfter: number | null }
  | { ok: false; code: "session" };

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
/**
 * When an install that is not signed in last had a call held, in ms since the epoch. Its object
 * deletes itself, its count with it, [POLICY.installRetentionMs] after that time.
 */
const LAST_HELD = "lastHeldAt";
/**
 * When a Google account was last deleted, in ms since the epoch. Every session issued before it is
 * refused, so it is kept for as long as such a session could last, [POLICY.sessionMs].
 */
const DELETED = "deletedAt";
/** The most keys one write of storage takes. */
const MAX_PUT_KEYS = 128;

/**
 * The calls of one caller: an install that is not signed in, or a Google account. Every request of
 * a caller runs on this one object, one after another, so two requests can never both take the
 * last call. Storage holds counts and request ids; for a Google account its plan and when its id
 * was first handed out as a purchaser; and for an install when it last had a call held.
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
 * A deleted account's object keeps the count of the calendar month it was deleted in until that
 * month ends, and the time it was deleted for [POLICY.sessionMs], so that every session issued
 * before the deletion is refused; an object left with nothing else then deletes itself. See
 * [forget].
 *
 * An install's object keeps the time of the last call it held, and deletes itself with its count
 * [POLICY.installRetentionMs] after it, by its own alarm; every call held before then moves the
 * time on. An install that calls again later starts the trial afresh, as a new one does.
 *
 * Every method first forgets what has outlived its time, as the alarm would have; see [begin].
 */
export class QuotaBook extends DurableObject {
  /** Whether this object has looked for counts in whole calls since it started; see [upgrade]. */
  private upgraded = false;

  /**
   * Holds a call of [weight] for [requestId] while what is left of the allowance that holds at [now]
   * is at least that. [allowance] is the caller's without Pro; see [termsAt]. [issuedAt] is when
   * the session of a signed-in caller was issued: a session issued before the account was deleted
   * is refused as `session`, and nothing is held. A Worker deployed before deletions ended sessions
   * sends none, and nothing is checked.
   */
  async hold(now: number, requestId: string, allowance: Allowance, digest: string, weight: number, issuedAt?: number): Promise<HoldResult> {
    if (endsSession(await this.begin(now), issuedAt)) return { ok: false, code: "session" };
    const terms = await this.termsAt(now, allowance);
    const book = await this.currentBook(now, terms.allowance);
    const requests = await this.liveRequests(now);
    // A call held for an install moves on the time its object is kept until.
    const held = allowance.kind === "trial" ? { [LAST_HELD]: now } : {};
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
      await this.ctx.storage.put({ [REQUEST + requestId]: known, ...held });
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
    await this.ctx.storage.put({ [REQUEST + requestId]: entry, ...held, ...(first ? { [FIRST_CALL]: true } : {}) });
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
    await this.begin(now);
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
    await this.begin(now);
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
    await this.begin(now);
    return this.usageAt(now, allowance);
  }

  /**
   * [usage], for a Google account whose id the proxy is handing to the app as its purchaser, the id
   * the app gives RevenueCat. The first time, the object records that the id went out: the proxy
   * asks RevenueCat only about ids it handed out, because RevenueCat's read creates a customer that
   * does not exist. The record goes with everything else when the account is deleted.
   *
   * [issuedAt] is when the session the id goes out to was issued. Null for a session issued before
   * the account was deleted, and nothing is recorded; a Worker deployed before deletions ended
   * sessions sends none, and nothing is checked.
   */
  async issuePurchaser(now: number, allowance: Allowance, issuedAt?: number): Promise<Usage | null> {
    if (endsSession(await this.begin(now), issuedAt)) return null;
    if ((await this.ctx.storage.get(ISSUED)) === undefined) await this.ctx.storage.put(ISSUED, now);
    return this.usageAt(now, allowance);
  }

  /** Whether the proxy handed this account's id out as a purchaser and has not forgotten it since. */
  async purchaserIssued(): Promise<boolean> {
    await this.begin(Date.now());
    return this.issued();
  }

  /**
   * Keeps [plan], read from RevenueCat, as the account's plan, unless the plan already kept was read
   * later: answers that arrive out of order leave the latest, so an event repeated or late changes
   * nothing. Nothing is kept for an id the proxy never handed out or has forgotten, such as a deleted
   * account's, nor for a plan that is not of the shape [PlanState] gives.
   */
  async storePlan(plan: PlanState): Promise<void> {
    await this.begin(Date.now());
    const next = planStateOf(plan);
    if (next === null || !(await this.issued())) return;
    const stored = planStateOf(await this.ctx.storage.get(PLAN));
    if (stored === null || next.checkedAt >= stored.checkedAt) await this.ctx.storage.put(PLAN, next);
  }

  /**
   * Whether a session issued at [issuedAt] was issued before the account was last deleted, for a
   * route that refuses such a session and has no other call to make to this object. Nothing about
   * the session is recorded.
   */
  async sessionEnded(now: number, issuedAt: number): Promise<boolean> {
    return endsSession(await this.begin(now), issuedAt);
  }

  /**
   * Forgets a deleted account as of [now]: its plan, the record of its purchaser, the request ids
   * it remembers and everything else go, but two things, which hold nothing but times and counts,
   * under the account's salted hash:
   *
   * - the count of the calendar month of [now], until the month ends, so that deleting the account
   *   and signing in again starts no new allowance. A call still held runs on, and is charged to
   *   that count once the model answers;
   * - [now] itself, as the time the account was deleted, for [POLICY.sessionMs], as long as a
   *   session lasts: every session issued before it is refused, so that a phone still signed in
   *   brings nothing back. A sign-in after it gets a session that works.
   *
   * The object wakes when each is due and deletes it, so that once the later of the two has passed,
   * nothing is left; see [alarm]. [now] defaults to the time of the call, as a Worker deployed
   * before the count was kept sends none.
   */
  async forget(now: number = Date.now()): Promise<void> {
    const kept = await this.ctx.storage.get([BOOK, DELETED]);
    const book = kept.get(BOOK);
    const keepBook = isRecord(book) && book.period === monthOf(now);
    // A deletion recorded later, by a Worker whose clock runs ahead, stays the one that counts.
    const earlier = kept.get(DELETED);
    const deletedAt = typeof earlier === "number" ? Math.max(earlier, now) : now;
    const others = [...(await this.ctx.storage.list()).keys()].filter((key) => key !== DELETED && !(keepBook && key === BOOK));
    const writes: Array<Promise<unknown>> = [this.ctx.storage.put(DELETED, deletedAt)];
    for (let start = 0; start < others.length; start += MAX_PUT_KEYS) {
      writes.push(this.ctx.storage.delete(others.slice(start, start + MAX_PUT_KEYS)));
    }
    // Issued with no wait between them, the writes land as one.
    await Promise.all(writes);
    await this.arm(now);
  }

  /**
   * Forgets the request ids whose time is up, what a deleted account kept once its time is up, and
   * an install that held no call for [POLICY.installRetentionMs], then sleeps until the next of
   * them is due. An object left with nothing deletes itself.
   */
  async alarm(): Promise<void> {
    await this.upgrade();
    const now = Date.now();
    await this.liveRequests(now);
    await this.forgetEnded(now);
    if ((await this.ctx.storage.list({ limit: 1 })).size === 0) {
      await this.ctx.storage.deleteAll();
      return;
    }
    await this.arm(now);
  }

  /**
   * What every method does first, but [forget] and [alarm]: [upgrade], then [forgetEnded], as at a
   * call after the alarm was missed. When that changed something, or a time the alarm wakes for is
   * kept but no alarm is set, as when older code ran the alarm after a rollback, the alarm is set
   * again for what is left. Answers the account's deletion time while it is kept, or null.
   */
  private async begin(now: number): Promise<number | null> {
    await this.upgrade();
    const { deletedAt, timed, changed } = await this.forgetEnded(now);
    if (changed || (timed && (await this.ctx.storage.getAlarm()) === null)) await this.arm(now);
    return deletedAt;
  }

  /**
   * Forgets what has outlived its time: a monthly count once its month has ended, as it counts for
   * nothing then; the account's deletion time once every session issued before it has run out; and
   * everything an install's object holds once [POLICY.installRetentionMs] have passed since the
   * last call it held. An install's object written before that time was kept, which holds a trial
   * count or the first-call mark and no time, takes [now] as its time, so that its months start
   * when this code first sees it. Answers the deletion time while it is kept, or null; whether a
   * time the alarm wakes for is kept; and whether anything changed.
   */
  private async forgetEnded(now: number): Promise<{ deletedAt: number | null; timed: boolean; changed: boolean }> {
    const kept = await this.ctx.storage.get([BOOK, DELETED, FIRST_CALL, LAST_HELD]);
    const lastHeld = kept.get(LAST_HELD);
    if (typeof lastHeld === "number" && now >= lastHeld + POLICY.installRetentionMs) {
      // The alarm goes with the rest.
      await this.ctx.storage.deleteAll();
      return { deletedAt: null, timed: false, changed: true };
    }
    const ended: string[] = [];
    const book = kept.get(BOOK);
    if (isMonthlyBook(book) && book.period < monthOf(now)) ended.push(BOOK);
    const stored = kept.get(DELETED);
    const deletedAt = typeof stored === "number" && now < stored + POLICY.sessionMs ? stored : null;
    if (stored !== undefined && deletedAt === null) ended.push(DELETED);
    if (ended.length > 0) await this.ctx.storage.delete(ended);
    const untimed = typeof lastHeld !== "number" && (kept.has(FIRST_CALL) || isTrialBook(book));
    if (untimed) await this.ctx.storage.put(LAST_HELD, now);
    return { deletedAt, timed: deletedAt !== null || typeof lastHeld === "number" || untimed, changed: ended.length > 0 || untimed };
  }

  /** The usage at [now], once [begin] has run. */
  private async usageAt(now: number, allowance: Allowance): Promise<Usage> {
    const terms = await this.termsAt(now, allowance);
    return usageOf(await this.currentBook(now, terms.allowance), await this.liveRequests(now), terms, now);
  }

  private async issued(): Promise<boolean> {
    return (await this.ctx.storage.get(ISSUED)) !== undefined;
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

  /**
   * Wakes the object when the next thing it keeps is due to be forgotten, so that storage does not
   * grow: a request id; a deleted account's deletion time, once sessions issued before it have run
   * out; a deleted account's count, when its month ends; and an install's object, once it has held
   * no call for [POLICY.installRetentionMs]. That count is the one kept beside the deletion time, or
   * one kept alone, as the version before deletion times kept it.
   */
  private async arm(now: number) {
    const requests = await this.ctx.storage.list<RequestEntry>({ prefix: REQUEST });
    const kept = await this.ctx.storage.get([BOOK, DELETED, ISSUED, PLAN, LAST_HELD]);
    const times = [...requests.values()].map(forgetAt);
    const deletedAt = kept.get(DELETED);
    if (typeof deletedAt === "number") times.push(deletedAt + POLICY.sessionMs);
    const lastHeld = kept.get(LAST_HELD);
    if (typeof lastHeld === "number") times.push(lastHeld + POLICY.installRetentionMs);
    const alone = requests.size === 0 && !kept.has(ISSUED) && !kept.has(PLAN);
    if (isMonthlyBook(kept.get(BOOK)) && (typeof deletedAt === "number" || alone)) times.push(nextMonthStart(now));
    const due = times.filter((time) => time > now);
    if (due.length > 0) {
      await this.ctx.storage.setAlarm(Math.min(...due));
    }
  }
}

/**
 * Whether a session issued at [issuedAt] was issued before [deletedAt], the time the account was
 * last deleted, which ends it. A call that names no session, as an install's or as one from a
 * Worker deployed before deletions ended sessions, ends none.
 */
function endsSession(deletedAt: number | null, issuedAt: unknown): boolean {
  return deletedAt !== null && typeof issuedAt === "number" && issuedAt < deletedAt;
}

/** Whether [value] is the book of a monthly allowance, which counts for nothing once its month has ended. */
function isMonthlyBook(value: unknown): value is StoredBook {
  return isRecord(value) && typeof value.period === "string" && value.period !== "trial";
}

/** Whether [value] is the book of an install's trial, which only an install's object holds. */
function isTrialBook(value: unknown): boolean {
  return isRecord(value) && value.period === "trial";
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
