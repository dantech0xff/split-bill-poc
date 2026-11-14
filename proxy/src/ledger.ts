import { DurableObject } from "cloudflare:workers";
import { POLICY, type RateLimit } from "./policy";

export type Gate = { ok: true } | { ok: false; retryAfter: number };

type RateWindow = { minuteStart: number; minuteCount: number; dayStart: number; dayCount: number };
type SpendWindow = { start: number; spent: number; held: number };

const RATE = "rate";
const SPEND = "spend";

/**
 * Counters against abuse for one name: a hashed IP, one caller, or the single name `global`. It
 * counts requests per minute and per day, and money spent over a fixed 24 hour window. Storage
 * holds counters only; the IP itself is never written. Once every window has passed, the object
 * deletes itself, so a hashed IP is not kept after its window.
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

  /** Holds [amount] against [cap] before the model runs; the most the call could cost. */
  async holdSpend(now: number, amount: number, cap: number): Promise<Gate> {
    const spend = rolledSpend(await this.ctx.storage.get<SpendWindow>(SPEND), now);
    if (spend.spent + spend.held + amount > cap) {
      return { ok: false, retryAfter: secondsLeft(now, spend.start, POLICY.dayMs) };
    }
    spend.held += amount;
    await this.ctx.storage.put(SPEND, spend);
    await this.arm(now);
    return { ok: true };
  }

  /** Replaces a hold of [held] with what the call really cost. Zero gives the whole hold back. */
  async settleSpend(now: number, held: number, actual: number): Promise<void> {
    const stored = await this.ctx.storage.get<SpendWindow>(SPEND);
    const spend = rolledSpend(stored, now);
    if (stored === spend) {
      spend.held = Math.max(0, spend.held - held);
    }
    spend.spent += actual;
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
