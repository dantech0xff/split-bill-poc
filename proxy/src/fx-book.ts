import { DurableObject } from "cloudflare:workers";
import { isRecord, readJson } from "./input";
import { errorName } from "./spend";

/** How many units of a currency one euro buys, and the day the source gives that rate for. */
export type FxRate = { rate: number; date: string };

/**
 * The exchange rates the proxy hands out: for each fiat currency the source has, how many units
 * of it one euro buys. The rates of one snapshot need not share a day, because some central banks
 * publish a day or two after others, so each rate keeps its own. [fetchedAt] is when the proxy
 * fetched them, in epoch milliseconds.
 */
export type FxSnapshot = { base: "EUR"; source: "Frankfurter"; fetchedAt: number; rates: Record<string, FxRate> };

/** One fetch from the source: a whole snapshot, or why there is none. */
export type FxFetch = { ok: true; snapshot: FxSnapshot } | SourceFailure;
type SourceFailure = { ok: false; reason: string };

/**
 * A snapshot this old is fetched again when a request finds it. The daily cron normally fetches
 * the rates before then; this covers a cron that failed or did not run.
 */
export const FX_MAX_AGE_MS = 24 * 60 * 60 * 1000;

/**
 * After a refresh fails, requests hand out the rates held, or none, for this long before the
 * source is asked again, so an outage of the source does not make every request wait for it.
 */
export const FX_RETRY_PAUSE_MS = 5 * 60 * 1000;

/** How long one refresh waits for the source, both of its requests together. */
const FX_SOURCE_TIMEOUT_MS = 10_000;

// Version 2 of the API. Version 1 has only some 30 currencies, and the Vietnamese dong is not
// among them.
const RATES_URL = "https://api.frankfurter.dev/v2/rates?base=EUR";
const CURRENCIES_URL = "https://api.frankfurter.dev/v2/currencies";
const SOURCE_HEADERS = { accept: "application/json", "user-agent": "split-vibe-proxy" };
/** Far more than the two answers need: together they are under 50 KB. */
const MAX_SOURCE_BYTES = 1024 * 1024;

const SNAPSHOT = "snapshot";
/** When the last refresh failed; gone once one works again. */
const FAILED_AT = "refreshFailedAt";
const CURRENCY_CODE = /^[A-Z]{3}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;

/**
 * The latest exchange rates, in the one object that every request and the daily cron share.
 * Storage holds a single snapshot, and a refresh only ever replaces it with a whole new one: when
 * the source fails, or answers with something it is not known to send, the snapshot held stays.
 */
export class FxBook extends DurableObject {
  /** The refresh under way. Whoever needs one while it runs waits for it rather than asking the source again. */
  private refreshing: Promise<boolean> | null = null;

  /**
   * The rates to hand out at [now]: the snapshot held, fetched again first when there is none or
   * it is [FX_MAX_AGE_MS] old, unless a refresh failed less than [FX_RETRY_PAUSE_MS] ago. When
   * that fetch fails, the snapshot held is handed out as it is; null means there has never been one.
   */
  async rates(now: number): Promise<FxSnapshot | null> {
    const held = await this.ctx.storage.get<FxSnapshot>(SNAPSHOT);
    if (held !== undefined && now - held.fetchedAt < FX_MAX_AGE_MS) return held;
    const failedAt = await this.ctx.storage.get<number>(FAILED_AT);
    if (failedAt !== undefined && now - failedAt < FX_RETRY_PAUSE_MS) return held ?? null;
    await this.refresh(now);
    return (await this.ctx.storage.get<FxSnapshot>(SNAPSHOT)) ?? null;
  }

  /** Fetches the rates whatever the age of those held or the last failure, and says whether new ones were kept. */
  refresh(now: number): Promise<boolean> {
    this.refreshing ??= this.fetchAndKeep(now).finally(() => {
      this.refreshing = null;
    });
    return this.refreshing;
  }

  private async fetchAndKeep(now: number): Promise<boolean> {
    const fetched = await fetchFxSnapshot(now, FX_SOURCE_TIMEOUT_MS);
    if (!fetched.ok) {
      console.error(JSON.stringify({ event: "fx_refresh_failed", reason: fetched.reason }));
      await this.ctx.storage.put(FAILED_AT, now);
      return false;
    }
    await this.ctx.storage.put(SNAPSHOT, fetched.snapshot);
    await this.ctx.storage.delete(FAILED_AT);
    console.log(JSON.stringify({ event: "fx_refreshed", currencies: Object.keys(fetched.snapshot.rates).length }));
    return true;
  }
}

/**
 * The rates of every fiat currency the source has, against the euro, as it gives them now. The
 * source's list of currencies says which of its codes are currencies; crypto assets and metals
 * are left out. Tests shorten [timeoutMs]; the object uses [FX_SOURCE_TIMEOUT_MS].
 */
export async function fetchFxSnapshot(now: number, timeoutMs: number): Promise<FxFetch> {
  const signal = AbortSignal.timeout(timeoutMs);
  const [rates, currencies] = await Promise.all([sourceJson(RATES_URL, signal), sourceJson(CURRENCIES_URL, signal)]);
  if (!rates.ok) return rates;
  if (!currencies.ok) return currencies;
  const snapshot = snapshotOf(rates.body, currencies.body, now);
  return snapshot === null ? { ok: false, reason: "malformed" } : { ok: true, snapshot };
}

/**
 * The answer of the source at [url], read as JSON; its body reads as null when it is not JSON or
 * is far too long. An error status, a lost connection or the end of the time is a failure.
 */
async function sourceJson(url: string, signal: AbortSignal): Promise<{ ok: true; body: unknown } | SourceFailure> {
  try {
    const response = await fetch(url, { headers: SOURCE_HEADERS, signal });
    if (!response.ok) {
      await response.body?.cancel();
      return { ok: false, reason: `status_${response.status}` };
    }
    return { ok: true, body: await readJson(response, MAX_SOURCE_BYTES) };
  } catch (error) {
    // No connection, or the time ran out while waiting for the answer or reading it.
    return { ok: false, reason: errorName(error) };
  }
}

/**
 * The snapshot the source's two answers make, or null when they are not what the source is known
 * to send. Every rate kept must be against the euro, a positive number and dated; one that is
 * not fails the whole answer, so that a fault never takes a currency out of the rates handed
 * out. Entries for anything but a fiat currency are dropped unread, and an answer without a
 * single fiat rate is not a snapshot.
 */
function snapshotOf(rates: unknown, currencies: unknown, now: number): FxSnapshot | null {
  if (!Array.isArray(rates) || !Array.isArray(currencies)) return null;
  const fiat = new Set<string>();
  for (const currency of currencies) {
    const code = isRecord(currency) && currency.type === "currency" ? currency.iso_code : undefined;
    if (typeof code === "string" && CURRENCY_CODE.test(code)) fiat.add(code);
  }
  const kept: Record<string, FxRate> = {};
  for (const entry of rates) {
    // The euro is the base, set below.
    if (!isRecord(entry) || typeof entry.quote !== "string" || entry.quote === "EUR" || !fiat.has(entry.quote)) continue;
    const { base, rate, date } = entry;
    if (base !== "EUR" || typeof rate !== "number" || !Number.isFinite(rate) || rate <= 0) return null;
    if (typeof date !== "string" || !DAY.test(date)) return null;
    kept[entry.quote] = { rate, date };
  }
  const days = Object.values(kept).map((fiatRate) => fiatRate.date);
  if (days.length === 0) return null;
  // One euro is one euro, on the newest day of any rate.
  const newest = days.reduce((latest, day) => (day > latest ? day : latest));
  return { base: "EUR", source: "Frankfurter", fetchedAt: now, rates: { EUR: { rate: 1, date: newest }, ...kept } };
}
