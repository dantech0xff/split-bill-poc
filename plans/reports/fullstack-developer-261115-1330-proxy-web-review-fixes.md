# Proxy and web pages: fixes from the Phase 6 review

Date: 2026-11-15 (UTC). Branch `feat/phase-06-web-companion-realtime-claim`, 17 commits on top of `bf599b4`, pushed. Review: `code-reviewer-261115-1200-phase-6-proxy-web-review.md`.

Status: done. Every finding is fixed as the brief asked. All checks pass:
- `proxy/`: `npm run typecheck`, and `npm test` with 260 tests.
- `web/`: `npm test` with 182 tests, and `npm run build`, CSP check included.
- End to end: 5 of 5 against the local proxy.

## What was done, finding by finding

| Finding | What changed | Commit |
|---|---|---|
| H1 | The public routes count against the IP's limits only once the token leads somewhere, and a refused request writes nothing. | `a8962e6` |
| M1 | The limit stays at 30 new claims an hour per IP. The contract says what stops one address from filling a link. | `a8962e6` |
| M2, proxy | Claim writes go from 120 to 300 an hour per IP. | `a8962e6` |
| M2, page | Live retries stop after 5 failures; then the page reads every 3 minutes. Every 429's `Retry-After` is respected. | `01e6cc0` |
| M3 | Test secrets work only on a local host. `remoteip` is sent. Mismatches are logged. | `7feb099` |
| M4 | No `payment` once a request is confirmed. Account deletion empties `payment` on all of the account's requests. | `d85428b` |
| `paidBy`, proxy | New optional field on `finalize`, kept and dropped with the amounts. | `ecee550` |
| `paidBy`, page | The page's wording uses it. | `9317d71` |
| L1 | Rooms are named by the link's id and `created_at`. | `b00d450` |
| L2 | A full room first closes connections that have been quiet for 2 minutes. | `2cc0877` |
| L3 | Revoking a link a second time answers 204 at once. | `b00d450` |
| L4, proxy | The claim `attempt`. | `cbefb72` |
| L4, page | The page sends the `attempt`. | `22d015f` |
| L5 | Cf characters are stripped from names and labels. | `b618214` |
| L6 | The page builds `amounts` without a prototype. | `7bab6f0` |
| L7 | Turnstile mismatches are logged; `ABUSE_EMAIL` follows the page's rule; `REQUIRE_WEB` and `npm run bundle` | `7feb099`, `55e292e`, `aadb710` |
| L8 | The embed script runs the web build's checks. | `3c22186` |

### H1: the public routes write to D1 only for a token that leads somewhere
- Order of `open`, `claim`, `live`, `request-links/view` and `invites/preview`:
  1. Name the address. This writes nothing; a missing address is still 400.
  2. Resolve the token. An unknown token is answered 404 and a revoked or expired one 410, with no write.
  3. Count against the hourly window.
- `admitHourly`, which serves every kind, accounts included, is still one statement that reads and counts. Its upsert has `DO UPDATE ... WHERE <window over> OR used < limit`. Once the window is full, the row is left untouched and `RETURNING` gives nothing. A second read of `started_at` then gives `Retry-After`; that read writes nothing.
- Tests:
  - At each limit (reads, claim writes, new claims), `used` and `started_at` are unchanged after a refused request.
  - Twelve requests with unknown, revoked or expired tokens, over all five routes, leave no `rate_windows` row for the IP.
  - A read is counted only for a token that resolves.
  - Putting the old statement back fails exactly the two write-nothing tests.
- I did not add the in-memory guard per isolate.
  - After this fix, a junk or refused request costs about two rows read and no write. Even the free plan's 100,000 Worker requests a day are then far below D1's 5 million rows read.
  - The guard would also share state across a test file's tests, and its effect in production depends on which isolate gets a request.
  - So it adds little, and complicates testing.

### M1
I kept the decision. The contract now says that one address can fill a link under these limits, and that Turnstile, the owner's removal of a claim, locking and revoking are what stop it. `policy.ts` says the same.

### M2
- **Proxy:** `claimWritesPerHour` is 300, which a test ties to ten per possible claim. Reads stay at 600 an hour, now counted only for tokens that resolve.
- **Live connection** (`web/src/live.ts`):
  - After 5 failures in a row it stops retrying. It tries again from the start when the tab is shown again or the browser comes back online.
  - `tryAgain()` makes one attempt. The failure count is kept, so one more failure stops it again.
  - `waitUntil(time)` holds every attempt until `time`, including attempts set off by showing the tab or coming back online.
- **Share page** (`web/src/share-controller.ts`):
  - Once live has stopped, it reads `open` every 3 minutes while the tab is visible, and calls `tryAgain()` after each read that works.
  - A 429 on `open` blocks reads and live attempts until `Retry-After`, or for 60 s when the header is missing.
  - A 429 on a claim blocks claims, and the Turnstile check, for the same time. The page shows how many seconds are left.
- **Request and invite pages:** "Try again" waits out a 429 (`useLoad`).
- **Effect:** a page whose live connection can never open now sends about 44 requests in its first hour and 40 an hour after that. Before, the review measured hundreds. A unit test sets these caps.
- **Limit:** a browser cannot see the status or `Retry-After` of a refused WebSocket handshake, such as the room's 429 with `Retry-After: 60`. So that one wait cannot be respected; the page backs off and stops after five failures instead.

### M3
- `turnstileReady(env, hostname)` accepts Cloudflare's test secrets only when the request's hostname is `localhost`, `127.0.0.1` or `[::1]`.
- On any other host it logs `{"event":"turnstile_test_secret"}`, with no token, and the claim is 503 `not_configured`. Siteverify is not called.
- Siteverify now gets `remoteip`, the CF-Connecting-IP, when there is one.
- An action or hostname mismatch is logged as `turnstile_mismatch`, with the action and hostname Cloudflare gave (cut to 64 characters) and the expected host, never the token.
- The test that locked in the bypass is replaced by tests for both sides:
  - local hosts accept the test secrets;
  - three other hosts are refused with 503, no call to Siteverify, and the log line.
- Further tests cover the mismatch logs (the token is never in them) and `remoteip` (IPv4, IPv6 and none).

### M4
- `request-links/view` returns `payment: null` once a request is confirmed.
- `forgetSharedData` empties `payment` on every request of the account as creditor, confirmed ones included. It still sets `method` to `{}` only on unconfirmed requests, which the debtor's app still shows.
- The review's probe is now a test: create with `payment`, accept, report, confirm, delete the account, then view gives `payment: null` and "Former member". A second, unconfirmed request is checked the same way.

### paidBy (new contract field)
- **Proxy:**
  - `finalize` takes an optional `paidBy`: null, or the id of a participant of `bill`. Any other value is 400.
  - It is stored in a new `shares.paid_by` column, added in `0002_shares.sql`, which is not deployed. The column has `CHECK (paid_by IS NULL OR amounts IS NOT NULL)`, so the schema enforces "dropped with the amounts".
  - PUT that changes the bill, `reopen` and revoke all set it to NULL with the amounts.
  - Sending the same settlement again with a different `paidBy` counts as a change.
  - ShareView and PublicShare carry `paidBy`, which is null before settling.
- **Page:** for a row without a payment:
  - the payer's own row: "Paid the bill";
  - the owner's row, when someone else paid: "Shared this bill";
  - an amount of 0: "Nothing to pay";
  - anyone else, with a payer named: "No way to pay was added. Minh paid the bill: settle up with them directly.";
  - anyone else, with no payer named: "No way to pay was added: settle up directly with whoever paid the bill."
- **Owner without a name:** the owner's entry `me` may have an empty name. Sentences then say "the person who made this link". That includes the claim errors and the "Picks are closed" banner, which used to fall back to "the payer"; that word is now wrong when someone else paid.
- The end-to-end run now settles with `paidBy: "me"` and checks "Lan Paid the bill".

### Low findings
- **L1:** the room is `idFromName("<id>:<created_at>")`. `tell` and `shareRoom` now take the link's row. A test opens a connection, deletes the link, makes it again under the same id, then claims and revokes; the old connection hears nothing.
- **L2:**
  - Each connection is accepted with an attachment `{acceptedAt}`.
  - When 50 connections are open, the room first closes, with code 1000 and reason `quiet`, those whose last ping (`getWebSocketAutoResponseTimestamp`) and acceptance are both more than 2 minutes old. It refuses the new one only if still full.
  - A test backdates 49 connections, one of which pinged, and adds a fresh 50th. The 51st is accepted, the 48 quiet connections are closed, and the pinging and fresh ones stay.
- **L3:** revoking an already revoked link answers 204 straight after its `SELECT`. A test wraps `DB` and `SHARE_ROOM` and shows that the second revoke calls neither a batch nor the room.
- **L4:**
  - A new claim may carry `attempt`, a UUID. Its SHA-256 is stored in `share_claims.attempt_hash`, with a partial unique index per link.
  - The same attempt on the same link, without a known key, replaces that claim's name and items and returns a new key; the old key stops working.
  - That path asks for no Turnstile check, is not counted as a new claim, and passes the "full" check.
  - Two tries landing at once: the unique index refuses the second insert, the link is read again, and the second try finds the claim. Tested by racing the batches.
  - The page makes the attempt with `crypto.randomUUID()` (none where that is missing), sends it with every try of its first claim, and clears it once a key is stored.
- **L5:**
  - `textOf` strips `\p{Cf}` before trimming and counting. Every name and label the proxy accepts goes through it: claims, the bill's merchant, items, charges and participants, groups, members, requests, and the source name of a conversion.
  - A name made only of such characters is refused.
- **L6:** `parseAmounts` builds `Object.create(null)`. A test with ids `__proto__` and `constructor` fails with the old `{}`. No other object on the page is keyed by ids: `route.ts` maps fixed paths, all starting with `/`.
- **L7:**
  - Mismatch logs: see M3.
  - `ABUSE_EMAIL`: the Worker now uses the page's rule, at most 254 characters, `[A-Za-z0-9._%+-]+@label(.label)+`. The rule is written in the contract, and both test suites check the same addresses.
  - `embed-web.mjs` stops rather than write the placeholder when `REQUIRE_WEB=1` is set or `--require` is given.
  - New `npm run bundle -- <dir>` (or `BUNDLE_DIR`), in `proxy/scripts/bundle.mjs`, runs `build:web` with the requirement on, then `wrangler deploy --dry-run --outdir <dir>`.
  - Checked by hand: the bundle was written to the scratchpad and ended with "--dry-run: exiting now", so nothing was deployed. A copy of the script with no web build exits 1 with `--require` and with `REQUIRE_WEB=1`, and writes the placeholder without them.
- **L8:**
  - The page checks moved into `web/scripts/page-checks.mjs`, with a `.d.mts` for TypeScript. Both `verify-build.mjs` and `embed-web.mjs` use them.
  - Embedding therefore also refuses references with a scheme or protocol-relative ones (except the `data:,` icon), `style=`, `on*=` and missing meta tags.
  - The placeholder page gained the robots and referrer meta tags so that it passes.
  - `web/tests/page-checks.test.ts` covers the checks.

## Commits (in order)
1. `a8962e6` fix(proxy): count web page requests only for links that work, never past a limit
2. `7feb099` fix(proxy): take Turnstile's test secrets only on a computer
3. `d85428b` fix(proxy): stop showing a creditor's payment details once they are not needed
4. `ecee550` feat(proxy): say on a bill link who paid the whole bill
5. `b00d450` fix(proxy): keep a bill link's live connections to that link alone
6. `2cc0877` fix(proxy): make room on a full bill link by closing its quiet connections
7. `cbefb72` fix(proxy): find the claim a browser made when it sends its first claim again
8. `b618214` fix(proxy): take invisible formatting characters out of names and labels
9. `55e292e` fix(proxy): take the abuse address by the rule the page applies
10. `3c22186` fix(proxy): check the embedded page as strictly as the web build does
11. `aadb710` fix(proxy): refuse to bundle a deploy without the web app
12. `01e6cc0` fix(web): stop live retries after five failures and wait as long as the proxy asks
13. `9317d71` fix(web): say whom to settle up with by who paid the bill, not by who shared it
14. `22d015f` fix(web): send the same attempt with every try of a first claim
15. `7bab6f0` fix(web): keep participant ids as plain keys of a settled link's amounts
16. `07f87bb` docs(proxy): say what a bill link keeps of who paid and of first claims
17. This report.

Each commit's tree passed `npm run typecheck` and `npm test` in `proxy/` on its own. Commits that touch `web/` also passed `npm test` and `npm run build` there. Each commit carries the contract text for its change. Files were staged by explicit path, and nothing outside `proxy/`, `web/` and `docs/proxy.md` was touched.

## Tests

| Package | Before | After |
|---|---|---|
| `proxy/` | 240 tests, 18 files | 260 tests, 18 files |
| `web/` | 142 tests, 15 files | 182 tests, 17 files (new: `page-checks.test.ts`, `use-load.test.tsx`) |

Typecheck passes in both packages. The web build passes its CSP check. Initial download: 26.6 KB gzipped, against a budget of 150 KB.

## End to end
`npm run e2e` in `web/`: 5 passed in 17.4 s.
- Playwright started `wrangler dev` on 127.0.0.1:8797, with a fresh local D1 and the edited `0002`, and stopped it at the end.
- Turnstile ran for real with the always-pass test keys, which M3 now allows on 127.0.0.1.
- Live updates arrived in 18 to 69 ms. The first claims took about 2.4 to 2.5 s from the tap, Turnstile included.
- The run also checked:
  - the `attempt` in first-claim bodies, and its absence from claims sent with a key;
  - "Lan Paid the bill" after settling with `paidBy: "me"`.
- No CSP violation was reported.
- Afterwards no wrangler, workerd or Chromium process was left, and ports 8797 and 9339 were free.

## Contract changes (`docs/proxy.md`)
- **What it keeps:**
  - logs now cover Turnstile mismatches and refused test secrets, never a token;
  - a link keeps who paid;
  - attempts are kept as hashes;
  - the web pages' hourly counters count only tokens that resolve.
- **Conventions:** names lose Unicode Cf characters before they are trimmed and counted.
- **Deleting an account:** `payment` is emptied on every request the account made, confirmed ones included; `method` only on unconfirmed ones.
- **Bill links:**
  - routes table: `finalize` takes `"paidBy"?` and returns the amounts and `paidBy`; `reopen` drops both;
  - PUT, `reopen` and revoking drop `paidBy` along with the amounts;
  - the `finalize` bullet describes `paidBy` (400 for a non-participant);
  - revoking an already revoked link answers 204 at once and wakes nothing;
  - ShareView gains `paidBy`, with a bullet on what the page says.
- **BillProjection:** names and labels, the merchant included, lose Cf characters.
- **Claiming items:**
  - `attempt` in the table and its own bullet;
  - Siteverify gets `remoteip`;
  - the test-secret rule for local hosts, with 503 and a log elsewhere;
  - claim names lose Cf characters;
  - the order of checks now includes the key or attempt;
  - limits: 300 claim writes an hour;
  - counting happens only for tokens that resolve, and nothing past a limit is written;
  - what stops one address from filling a link.
- **The live connection:**
  - a full room first closes connections that have been quiet for 2 minutes (code 1000, reason `quiet`);
  - pages ping every 30 s;
  - the page's behaviour after 5 failures and after a 429;
  - the room is named by the link's id and `created_at`.
- **Payment requests and invites on the web:** a read is counted only for a request or invite that works; `payment` is null once a request is confirmed.
- **The pages:**
  - the `ABUSE_EMAIL` rule, shared with the page;
  - `REQUIRE_WEB` and `--require`;
  - the embed checks are those of `web/scripts/page-checks.mjs`.
- **Run it on a computer:** the Turnstile test pair works only at `localhost`, `127.0.0.1` or `[::1]`.
- **Deploy it:**
  - step 4: a test secret on the deployed Worker means no new claims;
  - step 5: a deploy through the API uploads what `npm run bundle -- <directory>` writes.
- The deployed Worker's address appears nowhere; hosts are written `<the proxy's host>`.

## Notes
- I edited `0002_shares.sql` rather than adding `0003`, as the brief preferred. Wrangler does not apply an edited migration again, so any local D1 that already ran the earlier `0002` lacks `shares.paid_by` and `share_claims.attempt_hash`. It has to be recreated before `npm run dev`. On this machine the dev database under `proxy/.wrangler/` never applied any migration, so nothing was stale.
- The app has to send `paidBy` with `finalize`, and may read it back in ShareView. Every other change on the proxy keeps the app's requests valid.
- CI is unchanged. The new embed checks import `web/scripts/page-checks.mjs`, which is in every checkout.

## Open questions
1. **ZWJ and ZWNJ.** L5 strips all of Unicode's category Cf, as asked. That includes the zero-width joiner and non-joiner, which emoji sequences such as family emoji use, and which Persian and some Indic names need to render correctly. Should those two be kept? The cost is that they can be used for look-alike names.
2. **Stripping on the page.** The page does not strip Cf characters from the name a person types. The proxy stores the stripped name, so a typed name that contains such characters is sent again, unchanged, each time the field loses focus. Each send counts as one claim write. Should the page apply the same rule?
3. **A deploy workflow.** Should CI run `npm run bundle` to prove a deploy bundle builds with the web app? Today nothing deploys from CI, so I left it out.

Status: DONE
Summary: Every finding of the proxy and web review is fixed as specified. `paidBy` and the claim `attempt` are added to the contract and built on both sides. The proxy has 260 tests and the web pages 182, all passing, and the end-to-end suite passes 5 of 5. There are 17 commits, pushed.
Concerns/Blockers: The open questions above (ZWJ/ZWNJ under the Cf rule, stripping on the page, bundling in CI) need a product decision. The app must send `paidBy` with `finalize`.
