import { createExecutionContext, createScheduledController, runInDurableObject, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { fetchFxSnapshot, FX_MAX_AGE_MS, type FxSnapshot } from "../src/fx-book";
import { fxBook } from "../src/fx-rates";
import worker from "../src/index";
import { network } from "./network";
import { fetchWorker, send } from "./requests";

const RATES_URL = "https://api.frankfurter.dev/v2/rates";
const CURRENCIES_URL = "https://api.frankfurter.dev/v2/currencies";

/** The source's rates, as Frankfurter writes them: fiat currencies, crypto assets and a metal, each with its own day. */
const RATES = [
  { date: "2026-11-14", base: "EUR", quote: "AAVE", rate: 0.011127 },
  { date: "2026-11-13", base: "EUR", quote: "AOA", rate: 1022 },
  { date: "2026-11-14", base: "EUR", quote: "BTC", rate: 0.000016003 },
  { date: "2026-11-14", base: "EUR", quote: "EUR", rate: 1 },
  { date: "2026-11-14", base: "EUR", quote: "INR", rate: 104.22 },
  { date: "2026-11-14", base: "EUR", quote: "THB", rate: 37.507 },
  { date: "2026-11-14", base: "EUR", quote: "USD", rate: 1.1082 },
  { date: "2026-11-14", base: "EUR", quote: "VND", rate: 28529 },
  { date: "2026-11-13", base: "EUR", quote: "XAU", rate: 0.00028097 },
];

function currency(code: string, name: string, type: "currency" | "crypto" | "metal") {
  return { iso_code: code, iso_numeric: null, name, symbol: code, type, start_date: "1999-01-04", end_date: "2026-11-14" };
}

/** The source's list of currencies, which says what each code is. */
const CURRENCIES = [
  currency("AAVE", "Aave", "crypto"),
  currency("AOA", "Angolan Kwanza", "currency"),
  currency("BTC", "Bitcoin", "crypto"),
  currency("EUR", "Euro", "currency"),
  currency("INR", "Indian Rupee", "currency"),
  currency("THB", "Thai Baht", "currency"),
  currency("USD", "United States Dollar", "currency"),
  currency("VND", "Vietnamese Dong", "currency"),
  currency("XAU", "Gold", "metal"),
];

/** What the proxy keeps of [RATES]: the fiat currencies only, and the euro at 1 on the newest day. */
const FIAT = {
  EUR: { rate: 1, date: "2026-11-14" },
  AOA: { rate: 1022, date: "2026-11-13" },
  INR: { rate: 104.22, date: "2026-11-14" },
  THB: { rate: 37.507, date: "2026-11-14" },
  USD: { rate: 1.1082, date: "2026-11-14" },
  VND: { rate: 28529, date: "2026-11-14" },
};

/** Rates fetched before, which a test puts in the object with the age it needs. */
function heldSnapshot(ageMs: number): FxSnapshot {
  return {
    base: "EUR",
    source: "Frankfurter",
    fetchedAt: Date.now() - ageMs,
    rates: { EUR: { rate: 1, date: "2026-11-12" }, USD: { rate: 1.0951, date: "2026-11-12" }, VND: { rate: 28_100, date: "2026-11-12" } },
  };
}

async function hold(snapshot: FxSnapshot): Promise<void> {
  await runInDurableObject(fxBook(env), (_, state) => state.storage.put("snapshot", snapshot));
}

function held(): Promise<FxSnapshot | undefined> {
  return runInDurableObject(fxBook(env), (_, state) => state.storage.get<FxSnapshot>("snapshot"));
}

type Answer = () => Response | Promise<Response>;
type Asked = { url: string; userAgent: string | null };

/** The source answers with [rates] and [currencies]; the returned list grows by one for each request it gets. */
function source(rates: Answer = () => HttpResponse.json(RATES), currencies: Answer = () => HttpResponse.json(CURRENCIES)): Asked[] {
  const asked: Asked[] = [];
  const answering = (answer: Answer) => ({ request }: { request: { url: string; headers: Headers } }) => {
    asked.push({ url: request.url, userAgent: request.headers.get("user-agent") });
    return answer();
  };
  network.use(http.get(RATES_URL, answering(rates)), http.get(CURRENCIES_URL, answering(currencies)));
  return asked;
}

/** A request for the rates as the app sends it: a plain GET, with no install id, version or session. */
function ratesRequest(init: RequestInit = {}): Request {
  return new Request("https://proxy.test/v1/fx-rates", init);
}

describe("the exchange rates", () => {
  it("are fetched when the proxy has none, keeping each fiat currency with its own day and the euro at 1", async () => {
    const asked = source();
    const before = Date.now();

    const sent = await send(ratesRequest());

    expect(sent.status).toBe(200);
    const body = JSON.parse(sent.body) as FxSnapshot;
    expect(body).toEqual({ base: "EUR", source: "Frankfurter", fetchedAt: expect.any(Number), rates: FIAT });
    expect(body.fetchedAt).toBeGreaterThanOrEqual(before);
    for (const dropped of ["AAVE", "BTC", "XAU"]) expect(body.rates).not.toHaveProperty(dropped);
    expect(await held()).toEqual(body);
    expect(asked.map((request) => request.url).sort()).toEqual([CURRENCIES_URL, `${RATES_URL}?base=EUR`]);
    expect(asked.every((request) => request.userAgent === "split-vibe-proxy")).toBe(true);
  });

  it("fetched less than a day ago are handed out without asking the source", async () => {
    const snapshot = heldSnapshot(FX_MAX_AGE_MS - 60_000);
    await hold(snapshot);
    const asked = source();

    const sent = await send(ratesRequest());

    expect(sent.status).toBe(200);
    expect(JSON.parse(sent.body)).toEqual(snapshot);
    expect(asked).toHaveLength(0);
  });

  it("a day old are fetched again before they are handed out", async () => {
    await hold(heldSnapshot(FX_MAX_AGE_MS));
    const asked = source();
    const before = Date.now();

    const sent = await send(ratesRequest());

    const body = JSON.parse(sent.body) as FxSnapshot;
    expect(body.rates).toEqual(FIAT);
    expect(body.fetchedAt).toBeGreaterThanOrEqual(before);
    expect(asked).toHaveLength(2);
    expect(await held()).toEqual(body);
  });

  it("held are handed out as they are when the source fails or answers with something else", async () => {
    const rates: Answer = () => HttpResponse.json(RATES);
    const currencies: Answer = () => HttpResponse.json(CURRENCIES);
    const changed = (change: (rate: (typeof RATES)[number]) => Record<string, unknown>): Answer => () =>
      HttpResponse.json(RATES.map((rate) => (rate.quote === "USD" ? change(rate) : rate)));
    const faults: Array<[string, Answer, Answer]> = [
      ["an error", () => new HttpResponse("busy", { status: 503 }), currencies],
      ["an error for the list of currencies", rates, () => HttpResponse.json({ status: 404, message: "not found" }, { status: 404 })],
      ["no connection", () => HttpResponse.error(), currencies],
      ["a page that is not JSON", () => new HttpResponse("<html>Down for maintenance</html>", { headers: { "content-type": "text/html" } }), currencies],
      ["JSON that is not a list", () => HttpResponse.json({ rates: { USD: 1.1 } }), currencies],
      ["an empty list", () => HttpResponse.json([]), currencies],
      ["an answer far longer than any list of rates", () => new HttpResponse(`${" ".repeat(1024 * 1024)}[]`), currencies],
      ["a rate written as text", changed((rate) => ({ ...rate, rate: "1.1082" })), currencies],
      ["a rate of zero", changed((rate) => ({ ...rate, rate: 0 })), currencies],
      ["a rate without its day", changed(({ date: _, ...rate }) => rate), currencies],
      ["rates against another currency", () => HttpResponse.json(RATES.map((rate) => ({ ...rate, base: "USD" }))), currencies],
      ["a list of currencies that names no currency", rates, () => HttpResponse.json(CURRENCIES.map((entry) => ({ ...entry, type: "fiat" })))],
    ];

    for (const [fault, ratesAnswer, currenciesAnswer] of faults) {
      const snapshot = heldSnapshot(2 * FX_MAX_AGE_MS);
      await hold(snapshot);
      const asked = source(ratesAnswer, currenciesAnswer);

      const sent = await send(ratesRequest());

      expect(sent.status, fault).toBe(200);
      expect(JSON.parse(sent.body), fault).toEqual(snapshot);
      expect(await held(), fault).toEqual(snapshot);
      expect(asked.length, fault).toBeGreaterThan(0);
      network.resetHandlers();
    }
  });

  it("are unavailable when the proxy has none and the source fails", async () => {
    source(() => new HttpResponse(null, { status: 500 }));

    const sent = await send(ratesRequest());

    expect(sent.status).toBe(503);
    expect(JSON.parse(sent.body)).toEqual({ error: "fx_unavailable" });
    expect(sent.headers.get("cache-control")).toBe("no-store");
    expect(await held()).toBeUndefined();
  });

  it("are fetched once for requests that come together", async () => {
    let answer!: () => void;
    const answered = new Promise<void>((resolve) => {
      answer = resolve;
    });
    const asked = source(async () => {
      await answered;
      return HttpResponse.json(RATES);
    });
    const contexts = [1, 2, 3].map(() => createExecutionContext());

    const replies = Promise.all(contexts.map((ctx) => fetchWorker(ratesRequest(), ctx)));
    // Long enough for every request to reach the object while the first fetch still waits.
    await new Promise((resolve) => setTimeout(resolve, 100));
    answer();
    const responses = await replies;
    const bodies = await Promise.all(responses.map((response) => response.json<FxSnapshot>()));
    await Promise.all(contexts.map((ctx) => waitOnExecutionContext(ctx)));

    expect(responses.map((response) => response.status)).toEqual([200, 200, 200]);
    expect(bodies.every((body) => JSON.stringify(body) === JSON.stringify(bodies[0]))).toBe(true);
    expect(asked).toHaveLength(2);
  });

  it("are given up on when the source does not answer in time", async () => {
    source(() => new Promise<Response>(() => undefined));

    const fetched = await fetchFxSnapshot(Date.now(), 50);

    expect(fetched).toEqual({ ok: false, reason: "TimeoutError" });
  });
});

describe("the answer", () => {
  it("holds the base, the source, when the rates were fetched and every rate with its day, and may be cached for an hour", async () => {
    source();

    const sent = await send(ratesRequest());

    expect(sent.headers.get("content-type")).toBe("application/json");
    expect(sent.headers.get("cache-control")).toBe("public, max-age=3600");
    const body = JSON.parse(sent.body) as FxSnapshot;
    expect(Object.keys(body)).toEqual(["base", "source", "fetchedAt", "rates"]);
    expect(body).toMatchObject({ base: "EUR", source: "Frankfurter" });
    for (const code of ["EUR", "USD", "VND", "INR", "THB"]) expect(body.rates).toHaveProperty(code);
    for (const rate of Object.values(body.rates)) {
      expect(Object.keys(rate)).toEqual(["rate", "date"]);
      expect(rate.rate).toBeGreaterThan(0);
      expect(rate.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    }
  });

  it("needs no install id or session, but still the proxy's salt, and only answers GET", async () => {
    const asked = source();

    const unsalted = await send(ratesRequest(), { HASH_SALT: "short" });
    expect(unsalted.status).toBe(503);
    expect(JSON.parse(unsalted.body)).toEqual({ error: "not_configured" });

    for (const method of ["POST", "PUT", "DELETE"]) {
      const sent = await send(ratesRequest({ method }));
      expect(sent.status).toBe(405);
      expect(JSON.parse(sent.body)).toEqual({ error: "method" });
    }
    expect(asked).toHaveLength(0);

    expect((await send(ratesRequest())).status).toBe(200);
  });
});

describe("the daily cron", () => {
  it("fetches the rates again, also when those held are fresh", async () => {
    await hold(heldSnapshot(60_000));
    const asked = source();

    await worker.scheduled(createScheduledController({ cron: "30 16 * * *" }), env);

    expect(asked).toHaveLength(2);
    expect((await held())?.rates).toEqual(FIAT);
  });

  it("fails when the source does, and the rates held stay", async () => {
    const snapshot = heldSnapshot(60_000);
    await hold(snapshot);
    source(() => HttpResponse.error());

    await expect(worker.scheduled(createScheduledController({ cron: "30 16 * * *" }), env)).rejects.toThrow();

    expect(await held()).toEqual(snapshot);
  });
});
