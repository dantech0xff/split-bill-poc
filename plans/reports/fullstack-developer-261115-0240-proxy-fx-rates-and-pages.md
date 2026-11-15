# Proxy: daily exchange rates and bills in several pages

- Branch: `worktree-agent-afdd603af163fa079`, based on `feat/phase-04-capture-editing-split-modes` at `b94c1c9`
- Commits: `38bbda6` (exchange rates), `9d73663` (pages), `1a14532` (docs), plus the commit that adds this report
- Date: 2026-11-15
- Scope: `proxy/` and `docs/proxy.md` only. Nothing was deployed and the Cloudflare account was not touched.

## What changed

**`GET /v1/fx-rates`.** `proxy/src/fx-book.ts` adds the SQLite-backed Durable Object `FxBook` (binding
`FX_BOOK`, migration `v2`, one object named `rates`). It keeps one snapshot: base `EUR`, source
`Frankfurter`, `fetchedAt`, and for each fiat currency its rate and date, with EUR at 1 on the
newest date. It fetches `https://api.frankfurter.dev/v2/rates?base=EUR` and `/v2/currencies` together
under one 10 second timeout, sending `user-agent: split-vibe-proxy`. Only codes whose `type` is
`currency` are kept. Requests that need a refresh while one is running share it. `proxy/src/fx-rates.ts`
holds the route (200 with `cache-control: public, max-age=3600`, or 503 `fx_unavailable`) and the cron
refresh. `index.ts` exports the class, adds the route behind the existing `HASH_SALT` check and adds a
`scheduled` handler. `wrangler.jsonc` adds the binding, the migration and the cron `30 16 * * *`.
`readJson` in `input.ts` now takes any `Body`, so the source's replies are read with the same byte cap
and strict UTF-8 as requests.

**Bills in several pages.** One paragraph was added to the OCR section of `SYSTEM_PROMPT`, right after
the single-page description, which is unchanged. It describes `<bill_ocr pages="N">` with
`<page n="…" rows="…">`, rows starting from 1 again on each page, and coordinates that apply within
a page only. It also says the pages are consecutive parts of one bill, that every page must be read before
answering, that an item can run across a page break, that repeated headers are neither items nor a
new bill, and that a subtotal or "carried forward" line is not the total. The input check was already
correct and is unchanged; the 20,000-character limit stays.

**Docs.** `docs/proxy.md` has the new route, an exchange-rates section, the multi-page format, the
`FxBook` migration and the cron in the deploy step, and small fixes where the new route made a sentence
untrue: which requests carry the install header, what the objects keep, and what keeps working for old
apps.

## Checks

- `npm run typecheck`: clean.
- `npm test`: 93 passed in 8 files (81 before; 11 new in `test/fx-rates.test.ts`, 1 new in
  `test/bill-analysis.test.ts`). Three more full runs were also green.
- Negative control: with the single-flight guard removed, the concurrency test fails.
- `wrangler deploy --dry-run` bundles with the `FX_BOOK` binding.
- A live local run (`wrangler dev` on port 8791 with a throwaway salt, stopped afterwards) against the
  real API returned all 161 fiat currencies and no crypto or metal. EUR, USD, VND, INR and THB were
  present and matched the raw feed. The second request was answered from the snapshot in 7 ms, the
  cron handler refreshed, and POST answered 405.

## Decisions and open points

- "Malformed" means: either reply is not a JSON list; any fiat rate is against another base, is not a
  positive finite number or has no `YYYY-MM-DD` date; or no fiat rate is left. A single broken fiat
  entry rejects the whole fetch, so a fault never silently drops a currency from a good snapshot.
  Entries for crypto and metals are ignored without being checked.
- The cron time, 16:30 UTC, is my choice: it falls after the ECB publishes around 16:00 CET. Frankfurter
  only says its rates "update throughout the day".
- A failed cron run throws, so it shows as failed in Cron Events. Cloudflare's docs do not say whether
  failed cron runs are retried.
- There is no pause after a failed on-demand refresh. While the snapshot is over a day old and the
  source keeps failing, every request asks the source again. Concurrent requests share one attempt,
  and the wait is capped at 10 seconds. If that latency matters, a pause of a few minutes after a
  failure would be the follow-up.
- The prompt does not ask the model to merge lines that repeat at a page boundary, because the pages
  are described as consecutive parts. If the app can send overlapping photos of one long receipt, the
  prompt needs a rule for that, and the app should say whether pages can overlap.
- `docs/proxy.md` still points to the Phase 3 plan for "every error code". That plan does not list
  `fx_unavailable` or the new route, and it is outside this task's files.
