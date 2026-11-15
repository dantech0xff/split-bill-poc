import type { FxBook } from "./fx-book";
import type { Env } from "./index";
import { errorResponse, jsonResponse } from "./respond";

/** The rates change once a day, so the app, and any cache on the way, may keep an answer for an hour. */
const CACHE_CONTROL = "public, max-age=3600";

/** The one FxBook. Every request and the daily cron share its snapshot, so it has a single name. */
export function fxBook(env: Env): DurableObjectStub<FxBook> {
  return env.FX_BOOK.get(env.FX_BOOK.idFromName("rates"));
}

/**
 * `GET /v1/fx-rates`: the latest exchange rates against the euro. They are public and the same for
 * everyone, so the route needs no caller. 503 `fx_unavailable` means the proxy has never had rates
 * and could not fetch them just now.
 */
export async function fxRatesRoute(_request: Request, env: Env): Promise<Response> {
  const snapshot = await fxBook(env).rates(Date.now());
  if (snapshot === null) return errorResponse(503, "fx_unavailable");
  const { base, source, fetchedAt, rates } = snapshot;
  const response = jsonResponse(200, { base, source, fetchedAt, rates });
  response.headers.set("cache-control", CACHE_CONTROL);
  return response;
}

/**
 * The daily cron: fetches the rates whatever the age of those held. It fails when the source did,
 * so that the run shows as failed among the Worker's cron events; the rates held stay, and the
 * first request that finds them a day old tries again.
 */
export async function refreshFxRates(env: Env): Promise<void> {
  if (!(await fxBook(env).refresh(Date.now()))) throw new Error("The exchange rates could not be fetched");
}
