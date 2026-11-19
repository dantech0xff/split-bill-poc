import { DurableObject } from "cloudflare:workers";
import { POLICY, type RateLimit } from "./policy";

export type Gate = { ok: true } | { ok: false; retryAfter: number };

/**
 * What a hold of spend comes to: [Gate], and whether it is the first hold of the window to take
 * what is spent and held to [POLICY.spendWarnPercent] of the cap or more, refused or not: [nearCap]
 * for the cap, [nearPool] for the pool of the hold, when it has one. A refused hold always would.
 */
export type SpendGate = Gate & { nearCap?: true; nearPool?: true };

type RateWindow = { minuteStart: number; minuteCount: number; dayStart: number; dayCount: number };
/**
 * Money over one window: what was spent, what is held, and whether a hold has taken them to the
 * warning mark yet. [pool] is the part of it that calls with a pool spent and hold, the calls of
 * installs that are not signed in on the global ledger, with its own mark.
 */
type SpendWindow = { start: number; spent: number; held: number; warned?: true; pool?: PoolSpend };
type PoolSpend = { spent: number; held: number; warned?: true };

const RATE = "rate";
const SPEND = "spend";

/**
 * Counters against abuse for one name: a hashed IP, one caller, or the single name `global`. It
 * counts requests per minute and per day, and money spent over a fixed 24 hour window; `global`
 * also counts, inside that window, what the calls of installs that are not signed in spent, which
 * have a pool of their own. Storage holds counters only; the IP itself is never written. Once every
 * window has passed, the object deletes itself, so a hashed IP is not kept after its window.
 */
export class AbuseLedger extends DurableObject {
  async admit(now: number, limit: RateLimit): Promise<Gate> {
    const rate = rolledRate(await this.ctx.storage.get<RateWindow>(RATE), now);
    if (rate.minuteCount >= limit.perMinute) {
      return { ok: false, retryAfter: secondsLeft(now, rate.minuteStart, POLICY.minuteMs) };
    }
    if (rate.dayCount >= limit.perDay) {
      return { ok: false, retryAfter: secondsLeft(now, rate.dayStart, POLICY.dayMs) };
    }
    rate.minuteCount += 1;
    rate.dayCount += 1;
    await this.ctx.storage.put(RATE, rate);
    await this.arm(now);
    return { ok: true };
  }

  /**
   * Holds [amount] against [cap] before the model runs; the most the call could cost. With
   * [poolCap], the hold also counts against a pool inside [cap], which has a cap of its own, in the
   * same window: so the calls with a pool never spend more of a window than [poolCap], and the
   * others always keep the rest of [cap]. A Worker deployed before pools sends none.
   */
  async holdSpend(now: number, amount: number, cap: number, poolCap?: number): Promise<SpendGate> {
    const spend = rolledSpend(await this.ctx.storage.get<SpendWindow>(SPEND), now);
    const pool = poolCap === undefined ? undefined : (spend.pool ?? { spent: 0, held: 0 });
    const total = spend.spent + spend.held + amount;
    const pooled = pool === undefined ? 0 : pool.spent + pool.held + amount;
    const nearCap = spend.warned === undefined && total >= warnMark(cap);
    const nearPool = pool !== undefined && poolCap !== undefined && pool.warned === undefined && pooled >= warnMark(poolCap);
    if (nearCap) spend.warned = true;
    if (pool !== undefined && nearPool) pool.warned = true;
    const ok = total <= cap && (poolCap === undefined || pooled <= poolCap);
    if (ok) {
      spend.held += amount;
      if (pool !== undefined) pool.held += amount;
    }
    if (pool !== undefined) spend.pool = pool;
    if (ok || nearCap || nearPool) {
      await this.ctx.storage.put(SPEND, spend);
      await this.arm(now);
    }
    const marks = { ...(nearCap ? { nearCap: true as const } : {}), ...(nearPool ? { nearPool: true as const } : {}) };
    return ok ? { ok: true, ...marks } : { ok: false, retryAfter: secondsLeft(now, spend.start, POLICY.dayMs), ...marks };
  }

  /**
   * Replaces a hold of [held] with what the call really cost. Zero gives the whole hold back.
   * [pooled] says the hold counted against the pool too, as [holdSpend] took it with a pool.
   */
  async settleSpend(now: number, held: number, actual: number, pooled = false): Promise<void> {
    const stored = await this.ctx.storage.get<SpendWindow>(SPEND);
    const spend = rolledSpend(stored, now);
    const sameWindow = stored === spend;
    if (sameWindow) {
      spend.held = Math.max(0, spend.held - held);
    }
    spend.spent += actual;
    if (pooled) {
      const pool = spend.pool ?? { spent: 0, held: 0 };
      if (sameWindow) pool.held = Math.max(0, pool.held - held);
      pool.spent += actual;
      spend.pool = pool;
    }
    await this.ctx.storage.put(SPEND, spend);
    await this.arm(now);
  }

  /** Deletes everything this object holds, for a deleted account. */
  async forget(): Promise<void> {
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
  }

  async alarm(): Promise<void> {
    const now = Date.now();
    const rate = await this.ctx.storage.get<RateWindow>(RATE);
    const spend = await this.ctx.storage.get<SpendWindow>(SPEND);
    const rateOver = !rate || (now >= rate.dayStart + POLICY.dayMs && now >= rate.minuteStart + POLICY.minuteMs);
    const spendOver = !spend || now >= spend.start + POLICY.dayMs;
    if (rateOver && spendOver) {
      await this.ctx.storage.deleteAll();
      return;
    }
    await this.arm(now);
  }

  /** Wakes the object when its next window ends, to delete it once nothing is left to count. */
  private async arm(now: number) {
    const rate = await this.ctx.storage.get<RateWindow>(RATE);
    const spend = await this.ctx.storage.get<SpendWindow>(SPEND);
    const times = [
      rate ? rate.minuteStart + POLICY.minuteMs : undefined,
      rate ? rate.dayStart + POLICY.dayMs : undefined,
      spend ? spend.start + POLICY.dayMs : undefined,
    ].filter((time): time is number => time !== undefined && time > now);
    if (times.length > 0) {
      await this.ctx.storage.setAlarm(Math.min(...times));
    }
  }
}

/** A salted SHA-256 of [value], used as the name of an object so that the value itself is not stored. */
export async function hashed(value: string, salt: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${salt}\u0000${value}`));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * The address of the caller. Cloudflare sets CF-Connecting-IP on every request that reaches the
 * edge, so a request there without it is refused. `wrangler dev` runs without the edge, and its
 * requests share one name so that the proxy can be tried on a computer.
 */
export function clientIp(request: Request): { ok: true; ip: string } | { ok: false } {
  const header = request.headers.get("CF-Connecting-IP")?.trim() ?? "";
  if (header !== "") return { ok: true, ip: header };
  if (request.cf != null) return { ok: false };
  return { ok: true, ip: "local" };
}

/**
 * What the IP limits count by. An IPv6 subscriber holds a whole /64 and phones move between
 * addresses inside it, so an IPv6 address counts by its /64. An IPv4 address counts by itself,
 * also when it is written inside IPv6. Anything else is used as it is.
 */
export function ipKey(ip: string): string {
  const address = ip.trim().toLowerCase();
  if (!address.includes(":")) return address;
  const mapped = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/.exec(address);
  if (mapped) return mapped[1];
  const groups = ipv6Groups(address);
  return groups === null ? address : `${groups.slice(0, 4).join(":")}::/64`;
}

/** The eight groups of an IPv6 address, without leading zeros, or null when it is not one. */
function ipv6Groups(address: string): string[] | null {
  const halves = address.split("::");
  if (halves.length > 2) return null;
  const parts = halves.map((half) => (half === "" ? [] : half.split(":")));
  // An address may end in IPv4 notation, which stands for its last two groups.
  const last = parts[parts.length - 1];
  const dotted = last.length > 0 ? /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(last[last.length - 1]) : null;
  if (dotted) {
    const [a, b, c, d] = dotted.slice(1).map(Number);
    last.splice(last.length - 1, 1, ((a << 8) | b).toString(16), ((c << 8) | d).toString(16));
  }
  const written = parts.reduce((count, part) => count + part.length, 0);
  // "::" stands for one group of zeros or more.
  const zeros = parts.length === 2 ? 8 - written : 0;
  if ((parts.length === 2 && zeros < 1) || (parts.length === 1 && written !== 8)) return null;
  const groups = [...parts[0], ...Array<string>(zeros).fill("0"), ...(parts[1] ?? [])];
  if (!groups.every((group) => /^[0-9a-f]{1,4}$/.test(group))) return null;
  return groups.map((group) => group.replace(/^0+(?=.)/, ""));
}

function rolledRate(stored: RateWindow | undefined, now: number): RateWindow {
  const rate = stored ? { ...stored } : { minuteStart: now, minuteCount: 0, dayStart: now, dayCount: 0 };
  if (now - rate.minuteStart >= POLICY.minuteMs) {
    rate.minuteStart = now;
    rate.minuteCount = 0;
  }
  if (now - rate.dayStart >= POLICY.dayMs) {
    rate.dayStart = now;
    rate.dayCount = 0;
  }
  return rate;
}

/** The stored window while it lasts, the same object; a fresh one once it has ended. */
function rolledSpend(stored: SpendWindow | undefined, now: number): SpendWindow {
  if (stored && now - stored.start < POLICY.dayMs) return stored;
  return { start: now, spent: 0, held: 0 };
}

function secondsLeft(now: number, start: number, windowMs: number): number {
  return Math.max(1, Math.ceil((start + windowMs - now) / 1000));
}

/** What spent and held come to at the warning mark of [cap]. */
function warnMark(cap: number): number {
  return (cap * POLICY.spendWarnPercent) / 100;
}
