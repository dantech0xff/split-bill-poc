# Proxy and web part of the release: implementation report

## Phase implementation report

### Executed phase
- Phase: quality, compliance and release; the proxy and web part of its shared contract ("Hợp đồng chung") and the account deletion matrix.
- Plan: `plans/261114-0220-split-vibe-production/phase-09-quality-compliance-release.md`, not edited, as the task asked.
- Status: completed. Everything is committed and pushed to `origin/feat/phase-09-quality-compliance-release`.

### Commits
| Commit | What |
|---|---|
| `789dc68` feat(web) | `/privacy` and `/terms` built from `docs/*.md` at build time; the `/delete-account` page; a check for each kind of page |
| `c7048c0` feat(proxy) | Workers Analytics Engine counters (`METRICS`), and `npm run metrics` |
| `6614039` feat(proxy) | `POST /v1/account/delete-with-google`; a deleted account keeps only the month's calls until the month ends; hourly counters stay until the daily cleanup |
| `4e3c4ec` feat(proxy) | The Worker serves the three pages, with their headers |
| `c72ccab` docs | `docs/proxy.md` and `docs/web.md`, plus a code comment in `account-data.ts` |

Each commit holds only `web/`, `proxy/` or my two docs (checked with `git show --stat`). Some files carried more than one feature, so I staged an intermediate version of them for each proxy commit and typechecked that state before committing it.

## What was built

### Routes
- `POST /v1/account/delete-with-google` with `{"idToken": …}`:
  - It checks the token through the same function as `POST /v1/session` (`googleAccountIn` in `proxy/src/account.ts`).
  - It then deletes through the same function as `DELETE /v1/account` (`deleteAccount`), so neither route has its own copy.
  - Answers: 204 when done; 400 `input`, 401 `id_token`, 429 `rate_limited` with `Retry-After`, 502 `subscription_unavailable`, 503 `sign_in_off`.
  - It never issues a session: a 204 has no body and only `cache-control`.
  - The 5-an-hour limit counts both routes together, before RevenueCat is asked, and RevenueCat is still asked first.
- `GET /privacy` and `GET /terms` serve static pages embedded at build time. Their policy is `default-src 'none'; style-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`, with the link pages' other headers.
- `GET /delete-account`:
  - Policy: `script-src 'self' https://accounts.google.com/gsi/client; style-src 'self' https://accounts.google.com/gsi/style; connect-src 'self' https://accounts.google.com/gsi/; frame-src https://accounts.google.com/gsi/`. These are the origins Google's guide lists; the remaining directives are as on the link pages.
  - The Worker fills `<meta name="sv-google-client-id">` with `GOOGLE_CLIENT_ID` only while sign-in works and the value looks like a client ID; otherwise the tag stays empty, and a badly shaped value is logged.

### What deleting an account does now (the matrix)
- Devices are deleted, and so is the spend ledger.
- RevenueCat's customer is deleted first; if RevenueCat fails, nothing else is deleted.
- **QuotaBook (changed):** `forget(now)` keeps only the current month's count `{period, hundredths, used}` under the hashed account name.
  - The plan, the purchaser mark and the remembered request ids are deleted.
  - An alarm at the start of the next month deletes the count. An object holding nothing but an ended month's count deletes itself whenever it wakes.
  - A new sign-in in the same month sees the calls used, on the free plan.
  - A call still in flight is charged to the kept count when it finishes.
- **`rate_windows` (changed):** every hourly window of the account now stays until the daily cleanup. Before, all but two were deleted.
- Groups are unchanged: the account's members become "Former member", every amount stays, its bills pass to the group's owner, a group it owns passes on, and a group with no other account is deleted.
- Payment requests not yet confirmed lose their payment-method snapshot, and every request loses its web `payment`.
- Bill links are deleted with their claims, so they stop working at once.

### Pages (`web/`)
- **Legal pages:** `web/scripts/static-pages.mjs` runs after `vite build`.
  - It renders `docs/privacy-policy.md` and `docs/terms.md` with marked (an exact dev dependency, `18.2.1`) into `privacy.html` and `terms.html`. These use the link pages' stylesheet, built from tokens.css, and run no script.
  - Headings get GitHub-style ids, and links between the two Markdown files become `/privacy` and `/terms`.
  - HTML comments are dropped, and any other raw HTML shows as text.
  - An image, or a link to a file no page serves, stops the build. So does a missing source, naming the file, and with it `npm run bundle`.
- **Deletion page:** `delete-account.html` loads the same one script and stylesheet as the link pages; `main.tsx` draws the deletion page at that path.
  - It says what is deleted and what stays, including the phone's bills, the Pro warning with a Play subscriptions link, the month's count and the hourly counters.
  - Then: "Sign in with Google" (popup, `auto_select: false`), a final confirmation naming the signed-in email, then the outcome.
  - If the sign-in has expired it asks to sign in again; other failures say why and offer "Try again". Without a client ID it says web deletion is not available yet.
  - The in-app steps (Settings, Delete account) are always shown.
  - Each step moves the focus to its heading.
- `web/scripts/page-checks.mjs` now has three kinds of page: `link`, `delete-account` and `legal`. Both the web build and the proxy's embed script use it.

### Counters
- `proxy/src/metrics.ts` writes `count(env, event, outcome, numbers)` as `indexes: [event]`, `blobs: [event, outcome]`, `doubles: [cost µ$, input tokens, output tokens, ms]`. Without the binding it does nothing; if the binding throws, it logs `metric_not_written` and the request carries on.
- Events counted:
  - `bill_analysis` and `split_revision`, for every outcome, refusals included;
  - `install_first_call`;
  - `sign_in`;
  - `share_created`;
  - `share_claim` (`new` or `changed`);
  - `request_created`, `request_reported_paid`, `request_confirmed_paid`, each only when it actually happens;
  - `plan_refresh`;
  - `subscription_event` (the outcome only, never RevenueCat's event type);
  - `daily_run` (`ok` or `failed`, with ms).
- `wrangler.jsonc` adds `"analytics_engine_datasets": [{ "binding": "METRICS", "dataset": "split_vibe_events" }]`.
- `npm run metrics -- [days]` (1 to 90, default 7) reads the counts through the SQL API.
  - It takes `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN` from the environment and never prints either.
  - It prints a table of day, event, outcome, count (summed from `_sample_interval`) and cost.

## Tests and commands
| Command | Result |
|---|---|
| `proxy`: `npm run typecheck` | pass |
| `proxy`: `npm test` | Vitest 23 files, 444 tests pass (was 22 and 404); Node 17 tests pass (10 existing, 7 new for `npm run metrics`) |
| `proxy`: `npm run bundle -- <scratch dir>` | pass, and lists `env.METRICS (split_vibe_events)` |
| `web`: `npm run typecheck`, `npm run build` | pass; the build reports "4 pages and 2 files under /assets/" |
| `web`: `npm test` | 20 files, 231 tests pass (was 17 and 192) |
| `web`: `npm run e2e` | 8 of 8 pass (3 new) |
| `web`: `npm run size` | 29.8 KB gzipped, against a 150 KB budget |
| `npm audit --audit-level=high`, both packages | 0 vulnerabilities |

New tests:
- **`proxy/test/account.test.ts`:** the matrix, one row per test, run through both routes (20 tests), plus 6 more on the route's errors, the shared hourly limit, no session, and RevenueCat edge cases. The group row checks that the balances of the members who stay do not change. The QuotaBook row checks that a new sign-in sees the month's count.
- **`quota-book.test.ts`:** 8 tests: the kept month count, its deletion at month end, waking again before the month ends, an install's trial left alone, and the first-call mark.
- **`metrics.test.ts`:** 10 tests, including that no data point holds any id, and that a missing or failing binding changes no answer.
- **`web.test.ts`:** 6 tests on the new pages' headers and policies, the client-ID meta tag, and their paths.
- **Web unit tests:** the static pages (11 tests, with Markdown fixtures in `web/tests/fixtures/legal/`), the deletion page with a fake Google library (10), the Google loader and `emailOf` (4), the deletion API call (2), the page checks for each kind, and source safety (the `accounts.google.com` origin appears only in `google-sign-in.ts`).
- **Playwright `e2e/legal-and-deletion.spec.ts`:**
  - Headers, no CSP violations, no scripts and footer navigation on the legal pages.
  - The deletion flow with Google's library faked through `page.route`. The deletion route is faked too: the Worker could only check a token Google signed, which needs the network.
  - Google receives only the page's origin as Referer.
  - A link page's CSP blocks Google's library.

## Differences from the contract, and why
1. **`/delete-account` sends `referrer-policy: strict-origin-when-cross-origin`, not `no-referrer`.** Google Identity Services identifies the page's origin from the referrer, refuses the button without it, and Google recommends this policy. Cross-origin requests carry the origin only, and the page's URL holds nothing else. All other security headers are unchanged.
2. **The legal pages' CSP is stricter than the link pages'.** They allow no script, frame or connection at all.
3. **Bill links are deleted outright rather than marked revoked.** This was already the behaviour, and the effect matches the matrix: the links stop working at once (404 instead of 410) and their claims are gone.
4. **"First AI call of an install" is counted at the install's first held call.** The install's QuotaBook marks it, so it is counted once per install, together with that call's outcome. An install that had calls charged before this deploy is never counted as new.
5. **Small additions.** A footer linking the three new pages to each other. And `forget(now)` defaults to the current time, so an older Worker still running during the deploy works. The deploy notes say to use `--durable-objects-code-update-mode immediate`: otherwise, for up to five minutes, old QuotaBook code would still wipe a deleted account's month count.

## Not verified here
- Real Google sign-in. It needs the owner's OAuth client, with `https://<the proxy's host>` added to its Authorized JavaScript origins (documented in `docs/web.md` and `docs/proxy.md`).
- The SQL API against a real account. The query follows Cloudflare's documented syntax (aliases in `GROUP BY` as in their example, `toStartOfInterval`, `toDateTime`, `FORMAT JSON`) and was tested only against a stand-in.

## Unresolved questions
- The legal pages keep `x-robots-tag: noindex, nofollow`, like every other page, so search engines won't list them. Play only needs them reachable. Whether they should be indexable is the owner's call.
- Should a deleted account's bill links be kept as revoked (410, "turned off") until the daily cleanup, instead of being deleted (404)? I left the current deletion in place.

Status: DONE
Summary: The deletion route, the deletion matrix behaviour, the three pages, the counters with `npm run metrics`, and the docs are built, tested and pushed. Every test, typecheck, bundle, e2e and audit passes.
Concerns/Blockers: Google sign-in and the Analytics Engine SQL API were only tested against fakes. `/delete-account` uses `strict-origin-when-cross-origin` as its referrer policy, which Google's sign-in needs.
