# Proxy: bill links, live claims and the web pages

Branch `feat/phase-06-web-companion-realtime-claim`, pushed (5 commits on top of `08c5d27`).
Status: done, with the concerns listed at the end.

## What was built

| Area | Files |
|---|---|
| Schema | `proxy/migrations/0002_shares.sql`: `shares`, `share_claims` (cascade-deleted with their link), indexes for owner, expiry and revoked links, the trigger `shares_change_refused`, `ALTER TABLE requests ADD COLUMN payment TEXT`. `0001` is untouched. |
| Owner routes | `proxy/src/shares.ts`: `PUT/GET/DELETE /v1/shares/{id}`, `POST …/lock`, `…/reopen`, `…/finalize`, `DELETE …/claims/{claimId}`, all behind `signedIn`. |
| Public routes | `proxy/src/share-claims.ts`: `POST /v1/shares/open`, `POST /v1/shares/claim`, `GET /v1/shares/live`. `proxy/src/requests.ts`: `POST /v1/request-links/view` and the optional `payment` of `POST /v1/requests`. `proxy/src/invites.ts`: `POST /v1/invites/preview`. |
| Shared state | `proxy/src/share-state.ts`: rows, ShareView and PublicShare, the BillProjection and amounts parsers, the guarded write, the claim trimming statement, broadcasting. |
| Live | `proxy/src/share-room.ts`: Durable Object `ShareRoom` (binding `SHARE_ROOM`, migration tag `v3`, SQLite class). It uses the Hibernation API with a `ping`/`pong` auto-response, accepts at most 50 connections, exposes a `broadcast` RPC, and closes connections with `gone` on revocation. It stores nothing. |
| Turnstile | `proxy/src/turnstile.ts`: Siteverify with `TURNSTILE_SECRET` (optional), checking `success`, action `claim` and the request's hostname. |
| Pages | `proxy/src/web.ts`: `GET /s/`, `/r/`, `/g/` and `GET /assets/<name>`, with the contract's headers and meta tags. `linkPageRoute` is removed; `assetLinksRoute` stays. |
| Embedding | `proxy/scripts/embed-web.mjs` writes `proxy/src/web-assets.generated.ts`, which `proxy/.gitignore` ignores. |
| Limits | `proxy/src/policy.ts` holds the new limits. `proxy/src/rate-limit.ts` has new `HourlyKind`s and `addressName`/`addressRefusal`, which give IP-keyed `admitHourly` windows under `ip:<salted hash>`. |
| Cleanup | `proxy/src/reminders.ts` `forgetExpired` deletes expired and revoked links in the same batch, with no Durable Object call. `proxy/src/account-data.ts` deletes the account's links and empties `payment` together with `method`. |
| Wiring | `proxy/src/index.ts` (routes, `Env`, exports `ShareRoom`); `proxy/wrangler.jsonc` (binding, `v3`, the vars `TURNSTILE_SITE_KEY` and `ABUSE_EMAIL`); `proxy/package.json` scripts; `proxy/vitest.config.ts` (a test `TURNSTILE_SECRET`). |
| Refactors | `isDay` moved from `bills.ts` to `input.ts`. `requestToken` now goes through a shared `macToken` with no change in behaviour. |
| CI | `.github/workflows/ci.yml`, proxy job: the npm cache covers both lock files, and a "Web app" step runs `npm ci --prefix ../web` and `npm run build:web`. When `web/package-lock.json` is absent, the step prints a note and exits 0. |
| Docs | `docs/proxy.md`: the new section, "What it does", "What it keeps", logs, the conventions, "The daily run", "App Links", "Deleting an account", "Run it on a computer", "Tests" and "Deploy it" (D1 migration `0002` with a Time Travel bookmark, the Turnstile widget and `TURNSTILE_SECRET`, `TURNSTILE_SITE_KEY` and `ABUSE_EMAIL`, building the web app before deploying, `ShareRoom`/`v3`). Hosts are written `<the proxy's host>`. |

## Commits

1. `dd87607` feat(proxy): show payment requests and invites to browsers
2. `2c2328a` feat(proxy): let everyone at the table claim a shared bill's items live
3. `ff8ef19` feat(proxy): serve the web app for bill, request and invite links
4. `7d46b9d` ci: build the web app before checking the proxy
5. `4d009e3` docs(proxy): describe bill links and the web pages as they are built

`npm run typecheck` and `npm test` passed on each commit's own tree.

## Tests

- Before: 14 files, 185 tests. After: 18 files, 240 tests (191 after commit 1, 234 after commit 2).
- New: `test/shares.test.ts`, `share-claims.test.ts`, `share-live.test.ts`, `web.test.ts`, `fake-turnstile.ts` and `share-fixtures.ts`. `payment-requests.test.ts`, `invites.test.ts` and `links.test.ts` were extended.
- The full suite passes against the real web build and against the placeholder.
- A mutation check confirmed the race tests catch the guard. Dropping `status = 'open'` from the claim guard makes the race tests fail.
- The acceptance criteria all have tests:
  - No route lists links. The right token reads a live link. Expired and revoked links are 410 while their rows still exist. Public routes never change the bill.
  - Claims are refused for a 41-character name, an unknown item, the 31st claim, and any claim once locked. A new claim needs a token Turnstile passed, and a known key updates without one.
  - The lock race is tested both ways round, by landing one batch just before the other, and also with both requests sent at once.
  - A `PUT` that drops an item trims it from every claim and keeps the other items in their order.
  - `finalize` refuses wrong sums, a missing or extra participant, and bad payment links.
  - Public answers hold no account, hash, IP or link ids, and the key and the Turnstile token open nothing else (`/v1/bill-analysis` included).
  - Live connections get `share` and `gone` messages, the 51st is refused, and `ping` gets `pong`.
  - The pages carry their headers and the CSP, and `/s/<token>` is 404.
  - The daily run and account deletion remove links.
  - `view` and `preview` answer as the contract says, with 404 and 410.

## Where I read the contract differently (each is now what `docs/proxy.md` says)

1. **Token input.** It is `share-link:<owner>:<id>:<createdAt>`, not `share-link:<owner>:<id>`. Without the creation time, a link deleted by the daily run and made again under the same id would bring the old URL back to life. Clients never compute tokens, so neither client is affected.
2. **"410 however long ago."** The cleanup rule deletes the rows and there are no tombstones. So a link is 410 from the moment it expires or is revoked until the daily run deletes it, and 404 after that.
3. **Expired links.** Every owner route answers 410 on an expired link, not only on a revoked one. Revoking still works on an expired link (204).
4. **Other accounts.** On every owner route, an account that does not own the link gets 404, as for an unknown id. `PUT` stays 409, as the contract says.
5. **What counts as a change.** Every call of `PUT` on an existing link, `lock`, `reopen`, `finalize` and claim removal counts against the 300 changes an hour, whatever it then decides. `GET` and revoking never count, so an owner can always stop abuse.
6. **Removing a claim.** On a `locked` link it is 409 `locked`; the contract gave no code. Removing a claim the link no longer has answers 200 unchanged, so a retry is safe. A non-UUID claim id is 404.
7. **Lock, reopen, finalize.** `finalize` on an open link is 409 `conflict`. The same settlement sent again to a locked link changes nothing. `reopen` on an open link answers the link as it is.
8. **PUT revisions.** An equal `billRevision` with a different bill is accepted as a change; only a lower one is refused. The same revision with the same bill changes nothing.
9. **BillProjection.**
   - Only the named fields are stored, and the 64 KB limit applies to that stored JSON.
   - `quantity` may carry a minus sign: the app's parser passes the model's quantity through, and that can be negative. It has no exponent and no leading zero, with at most 20 digits on each side of the point.
   - Names and labels are trimmed and refuse control characters.
10. **PaymentPayload.**
    - `link` must literally start with `https://` (lowercase) and have a host, or start with `upi://pay?`. It may not contain whitespace or control characters.
    - `qrPayload` may be empty (0 to 512).
    - A `link`, `qrPayload` or `payment` that is left out counts as null. `amountFilledIn` must be a boolean. Lengths are counted in code points.
    - All of this matches what the app's `PaymentPayload.of` sends.
11. **Turnstile.**
    - With Cloudflare's test secrets, only `success` is checked, because their answers have no action and say `example.com`. This is what lets the web agent's end-to-end run with the always-pass keys work.
    - When Siteverify does not answer within 5 seconds, or answers with something unreadable, the claim gets 403 `turnstile`.
    - A token must be 1 to 2,048 characters; anything else counts as missing.
12. **Order of checks for a new claim.** 409 `locked`, then 400 for an item, 409 `full`, 503, 403 for a missing token, 429, and only then Siteverify. The 10 new claims an hour are counted before Cloudflare is asked.
13. **Keys.** A `key` that is not 22 base64url characters counts as a key the link does not know, so the request is treated as a new claim.
14. **Live connection.**
    - 400 `input` without an upgrade or without exactly the two protocols; either order of the two is accepted.
    - A full link answers 429 `rate_limited` with `Retry-After: 60`.
    - On revocation the server closes with code 1000 and reason `gone`.
    - Any message other than `ping` closes the connection with code 1008.
15. **Body limits.** Owner routes take up to 512 KB, a claim up to 32 KB, and open, view and preview up to 16 KB.
16. **Pages and assets.** The meta values must look like a site key or an email address; otherwise the tag stays empty and the value is logged. Values are HTML-escaped. Assets also send `x-content-type-options: nosniff`.
17. **Requests.**
    - RequestView does not carry `payment`.
    - Deleting a request empties `payment`.
    - Account deletion empties `payment` only where it empties `method`, that is on unconfirmed requests.
18. **Missing address.** A request that reaches the edge without CF-Connecting-IP gets 400 on the IP-limited public routes, as bill reads already do.

## The generated module and `npm run build:web`

- `node scripts/embed-web.mjs` reads `web/dist/index.html` and `web/dist/assets/*`. It writes `src/web-assets.generated.ts`, which exports `WEB_PAGE` (a string) and `WEB_ASSETS` (a `ReadonlyMap` from name to `{type, body}`).
- It rewrites the two meta tags to `<meta name="…" content="">`, inserting them before `</head>` if they are missing. The Worker then replaces those exact tags with the escaped values.
- The script stops with an error on:
  - an inline `<script>` or `<style>`;
  - a script or style the page refers to that is not served from `/assets/`;
  - a file in `assets/` that is neither `.js` nor `.css`;
  - a duplicated tag.
- It writes the file only when its content changes. Without `web/dist`, it writes a placeholder page with one CSS file.
- Scripts:
  - `build:web` is `npm --prefix ../web run build && node scripts/embed-web.mjs`.
  - `typecheck`, `test` and `dev` run the embed script first.
- The web build that exists today embeds cleanly: two assets, and a page that already has both tags.
- The deploy must run `npm run build:web` before bundling. If the module was never written, bundling fails; if only the placeholder was written, the placeholder is what gets served.

## Open questions and concerns

1. **Shared Wi-Fi and the claim limit.** The contract allows 10 new claims an hour per address. A table on one restaurant Wi-Fi, or behind carrier NAT, shares one address, so the 11th person cannot make a first claim for an hour. I kept the contract's number; whether to raise it is a product decision.
2. **Revocation race on live connections.** A connection that passes the token check in the instant before a revocation is accepted after the `gone` broadcast, so it never hears `gone`. The page learns it from `open`, which it calls right after connecting and which then answers 410. No data leaks, since a revoked link gets no further broadcasts.
3. **D1 write budget.** Every public read writes one hourly-window row in D1, against the free plan's 100,000 rows written a day.
4. **App manifest.** The app must not declare `/s/` in its App Links intent filter (owned by app/). `assetlinks.json` covers the whole host.
5. **Report not committed.** This report file is not committed; it is left for the orchestrator.

```text
Status: DONE_WITH_CONCERNS
Summary: All of the contract is built, with 240 tests passing against both the real web build and the placeholder, and 5 commits pushed. The places where I read the contract differently are listed above and written into docs/proxy.md.
Concerns/Blockers: The 10 new claims an hour per address may be too few for a table on one Wi-Fi. A live connection that opens in the instant of a revocation does not hear `gone`, though the page's next `open` answers 410.
```
