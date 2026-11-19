# Phase 9, proxy and web part: production-readiness review

## Code Review Summary

### Scope
- Range `4c2eb82..57a739a`, paths `proxy/`, `web/`, `docs/proxy.md`, `docs/web.md`: commits 789dc68, c7048c0, 6614039, 4e3c4ec, c72ccab and the report 57a739a. Other commits in the range were not reviewed.
- 46 files, +3459 / -433 lines.
- Focus: the new route `POST /v1/account/delete-with-google` and the page `/delete-account`, the legal pages, the month count a deleted account keeps in its QuotaBook, the Analytics Engine counters, `npm run metrics`, and whether the docs are accurate.
- Contract: `plans/261114-0220-split-vibe-production/phase-09-quality-compliance-release.md` ("Hợp đồng chung" and the deletion matrix). I checked the implementer's report against the code instead of taking it as given.
- Checks run:
  - `proxy`: `npm test` passes (Vitest 23 files, 444 tests; Node 17 tests); `npm run typecheck` passes; `npm run bundle -- <scratch dir>` passes (Total Upload 1460.01 KiB, gzip 291.25 KiB, binding `env.METRICS (split_vibe_events)` listed).
  - `web`: `npm test` passes (20 files, 231 tests). `npm run build` runs inside the bundle step and reports "4 pages and 2 files under /assets/".
  - The real legal renderer against about 25 hostile Markdown inputs, and against the real `docs/privacy-policy.md` and `docs/terms.md`.
  - Google's GIS setup guide and Cloudflare's Analytics Engine pricing and limits pages, against the claims in the code and docs.
  - Playwright e2e was not run again.

### Overall Assessment
- No Critical or High defect.
- Both deletion routes go through one token check (`googleAccountIn`, also used by `/v1/session`) and one deletion (`deleteAccount`). Their token validation, the shared limit of 5 deletions an hour, RevenueCat going first and the matrix therefore cannot drift apart.
- The matrix tests run every row through both routes, and each test fails on the old behaviour of its row.
- One Medium: deleting on the web does not end the session of a phone still signed in to the account. That phone puts deleted data back, while the page tells the person the server no longer keeps the account.
- The Lows are: docs and monitoring accuracy, data minimisation, a config edge, HEAD answering 405, a Markdown comment edge case, a rollback edge case, and OAuth origin hygiene.

### Critical Issues
None.

### High Priority
None.

### Medium Priority

**M1. Deleting on the web leaves every signed-in phone working, and those phones put deleted data back, while the page says the server no longer keeps the account**

Where:
- `proxy/src/account.ts:137-141`: `deleteWithGoogleRoute` deletes the account but leaves its sessions valid.
- `proxy/src/session.ts:77-90`: `accountOfSession` accepts any session that has not expired. Sessions last 30 days (`proxy/src/policy.ts:48`), and nothing can revoke one.
- `web/src/pages/delete-account-page.tsx:245-258`: the result says "Split Vibe's server no longer keeps your account."
- `web/src/pages/delete-account-page.tsx:81-97`: "What stays" never mentions phones that are still signed in.

Failure scenario: a person with Split Vibe still installed and signed in deletes the account on `/delete-account` and gets 204. The next time the app starts:
- **The device comes back**: `app/src/main/java/com/dantech/splitvibe/SplitVibeApp.kt:33-40` calls `DeviceRegistrar.register()`, which sends `PUT /v1/devices/<install id>` with the still-valid session. The `devices` row (push token and install id) is back, though the matrix row says "Xóa hết".
- **The RevenueCat customer comes back**: `app/src/main/java/com/dantech/splitvibe/billing/BillingRepository.kt:96` starts RevenueCat with the stored purchaser whenever a session exists. `Purchases.configure(appUserID(purchaser))` in `app/src/main/java/com/dantech/splitvibe/billing/RevenueCatBilling.kt:40-47` reads the customer, and that read re-creates it at RevenueCat. The deletion deleted that customer first.
- **The purchaser record comes back**: `GET /v1/usage` runs `callsAndPurchaser` (`proxy/src/account.ts:77-85`), whose `issuePurchaser` writes `issued` again. Webhook events and `POST /v1/plan` then ask RevenueCat about the account again.

The in-app route does not have this problem on its own phone, because the app forgets the session there. The web route is built for people who no longer have the app, but nothing stops someone who still has it from using the page.

`docs/privacy-policy.md:198,200` does disclose this ("Deleting your account on the website does not touch your phone"; "a phone that is still signed in can keep using the account for up to 30 days ... and what it does is kept again"). The page whose URL goes to Play as the deletion page says neither.

Fix:
- **Now, page only**: before "Delete my account" and in the result, say that a phone still signed in keeps using the account and that what it sends is kept again. Ask the person to sign out in Settings on those phones first, or to uninstall the app. Change "no longer keeps your account" to match.
- **Proper fix, for the owner to decide**: keep a tombstone per account hash for `POLICY.sessionMs`, for example a D1 row `(account, deleted_at)` that the daily run forgets. Have `accountOfSession` refuse a session whose `iat` is not later than the tombstone. The app already treats 401 `session` as "sign-in ran out" (`BillingRepository.kt:119`, `ui/common/ProxyErrors.kt:18`), so the phone would stop on its next call. The cost is a D1 read on each signed-in request, unless it is cached.

### Low Priority

**L1. A bill read or split edit that fails with 500 writes no data point, though `docs/proxy.md:409` says each one is counted "however it ends"**
- `proxy/src/model-call.ts:111-133` awaits `callerOf`, `ipLedger.admit` and `quota.hold`, and `:148-153` rethrows a failed `holdAll`. Any of these can throw: an overloaded Durable Object, one reset by a deploy, or a D1 fault. The fetch handler then answers 500 `internal` (`proxy/src/index.ts:201-204`), and `ended()` never runs.
- Impact: phase 9 plans an alert on the AI gateway's error rate, fed by these counters. That alert would see an infrastructure outage as fewer reads, not as more errors.
- Fix: have a thrown error in the part of `callModel` before the stream call `ended("internal", {})` before it rethrows, or narrow the claim in the doc.

**L2. `firstCall` keeps a timestamp forever, under the raw install id, where a flag would do**
- `proxy/src/quota-book.ts:144-148` stores `[FIRST_CALL]: now`. An install's QuotaBook is named `install:<install id>` (`proxy/src/identity.ts:62`) and is never deleted.
- The code only checks whether the key exists (`:145`), so the date of an install's first AI call is kept for no use.
- The privacy policy's retention list for installs names only the count of free calls (`docs/privacy-policy.md:162`).
- Fix: store `true` or `1`. The behaviour stays the same and no date is kept.

**L3. `GOOGLE_CLIENT_ID` is trimmed for the page but not for the token check**
- `proxy/src/web.ts:35-40` and `:143-146` put the trimmed value into the meta tag, which `proxy/test/web.test.ts:173` asserts, and `signInReady` trims too (`proxy/src/session.ts:17`). But `jwtVerify` compares `aud` exactly against the untrimmed `env.GOOGLE_CLIENT_ID` (`proxy/src/session.ts:34`).
- Scenario: with a stray space in the value, the page shows the button and Google signs a token for the trimmed ID. Every deletion then answers 401 `id_token`, and the page keeps saying "Your sign-in with Google ran out before the account was deleted. Sign in again." The app's sign-in fails the same way.
- Fix: trim the value once for every use, for example `audience: env.GOOGLE_CLIENT_ID.trim()`.

**L4. `HEAD /privacy`, `/terms` and `/delete-account` answer 405**
- `proxy/src/index.ts:167-169` register GET only, and `:194-195` answer 405 for any other method.
- These URLs go to Play and the app. Link checkers and uptime monitors that probe with HEAD will report them as broken.
- Fix: answer HEAD with the GET handler for these three pages (the runtime drops the body).

**L5. The Markdown renderer drops HTML comments in fewer cases than `docs/web.md:89` says**
- `web/scripts/static-pages.mjs:111-113` drops an HTML token only when the whole token is one comment.
- If text follows the closing `-->` on its line, the whole block is published as escaped text, comment included. A future "Draft for legal review" note written that way would appear on `/privacy`.
- `<!-- a --><b>x</b><!-- b -->` is dropped entirely instead of being shown as text.
- This is not an XSS: whatever is not dropped is escaped, and the legal pages' policy runs no script. The real docs render correctly: the draft comment is gone and no escaped tags are left.
- Fix: remove each comment from the token's text, then escape what is left. Or have the build stop on an HTML token that holds a comment and anything else.

**L6. Old QuotaBook code consumes the month-end alarm of a kept count**
- Before this change, `alarm()` only re-armed for request ids.
- This happens if a deleted account's month-end alarm runs on old code: a rollback that spans the 1st of a month, or a deploy window at 00:00 UTC on the 1st. Nothing then re-arms the alarm, and `{period, hundredths, used}` stays indefinitely under the account's hash. The privacy policy says the count is kept "until the month ends".
- The quota is not affected, because a past month counts as zero.
- Fix: say so in the rollback notes of `docs/proxy.md`, or have the new code re-arm when it reads a lone book from a past month.

**L7. The docs ask to add `http://localhost` origins to the production web client**
- `docs/web.md:72-74` and `docs/proxy.md:1156-1158` ask to add them to the client whose ID is `GOOGLE_CLIENT_ID`.
- Any page served on a person's machine from `http://localhost` or `http://localhost:8787` could then get ID tokens for the production client after one click in Google's popup. Such a token signs in through `POST /v1/session` or deletes the account.
- Fix: test locally with a separate web client, or a separate project. Keep only `https://<the proxy's host>` on the production client.

### Edge cases scouted and found sound
- **Forged, foreign or replayed ID tokens**:
  - `googleAccountOf` (`proxy/src/session.ts:26-44`) accepts only RS256, with Google's keys, either Google issuer, `aud` equal to `GOOGLE_CLIENT_ID`, and `sub` and `exp` present.
  - A wrong key, an expired token, another audience or another issuer gets 401 through the new route, and nothing is written. The test checks that no `account_delete` window appears.
  - A token for another client cannot be obtained without being an authorized origin or a registered Android client.
  - A replay within the token's hour needs the token itself, and `/v1/session` would accept that token anyway, so the route adds no new capability.
- **Cross-site requests**: no cookie and no CORS header, so a cross-site POST cannot delete anything without a valid token.
- **Rate limit**: both routes count against one `account_delete` window per account, checked after the token and before RevenueCat. This is tested by alternating the two routes.
- **What is logged**: nothing of the token, the email or the account. `bad_setting` names only the variable. Invocation logs are off, and no path holds a token.
- **The policy and referrer of `/delete-account`**:
  - The CSP is exactly what Google's GIS guide lists (checked on developers.google.com).
  - `strict-origin-when-cross-origin` is Google's own recommendation.
  - The page's URL holds nothing secret, the Play link has `noreferrer`, and the deletion fetch sends no referrer. The e2e test shows Google gets only the origin.
- **No other page can load Google's script**: the link pages' `script-src` does not allow it, the legal pages have no `script-src`, and the e2e test shows a link page blocking it.
- **XSS through the rendered Markdown**: every probe was escaped or stopped the build:
  - raw HTML, as a block or inline;
  - `javascript:`, `vbscript:` and `data:` links, entity-encoded links, reference-style links and autolinks;
  - quotes in an href or a title, a code fence's info string, HTML inside headings, table cells and link text.
  - On top of that, `pageProblems` flags escaped handler text such as `onerror=`, and the legal pages' CSP runs no script.
- **Headers of `/privacy` and `/terms`**: no script, `no-referrer`, `nosniff`, `no-store`, `noindex`, and served to GET at their exact paths only.
- **The kept month count**:
  - It holds only `{period, hundredths, used}`, in `google:<salted hash>`. Plan, `issued` and request ids are deleted in one coalesced write. The alarm is set for the start of next month.
  - An object holding only a past month deletes itself when it wakes. An install's trial book is never touched.
  - A new sign-in in the same month sees the count on the free plan. A call in flight during the deletion is charged to it.
  - Mixed versions during a deploy: an old Worker with a new object keeps the count (because `now` defaults); a new Worker with an old object wipes it, as the docs say.
- **The matrix tests**: each row's test would fail on the old behaviour: the hourly-counter test fails under the old two-window rule; there is a snapshot taken while RevenueCat deletes; and the test of the members who stay compares their balances before and after.
- **The counters**:
  - Every outcome is a code constant, and the test checks that outcomes are lower-case words and that no point holds an id.
  - At most two points per request, against the limit of 250 per invocation.
  - A missing binding does nothing; a binding that throws gives `metric_not_written` and changes no answer (tested).
- **`npm run metrics`**:
  - The token goes only into `Authorization` and is never printed.
  - The account ID must be 32 hex digits before it goes into the URL, and the days are checked.
  - The SQL holds no outside input.
  - The pricing claim is right (free plan: 100,000 points a day, 10,000 queries a day, not billed yet), and so is the three-month retention.
- **A missing legal source stops the build**: `static-pages.mjs` exits with 1, so `npm run build` and `npm run bundle` stop. Embedding with `REQUIRE_WEB=1` refuses placeholders.
- **Bundle size**: 1460 KiB, 291 KiB gzipped, against Workers' 3 MB (free) and 10 MB (paid) compressed limits. The generated web module is 140 KB.

### The implementer's differences from the contract
1. **`strict-origin-when-cross-origin` on `/delete-account`**: accept. Google recommends it, the URL holds nothing, and other origins get only the origin.
2. **A stricter policy on the legal pages**: accept.
3. **Bill links deleted instead of revoked**: accept. The matrix's effect holds: links stop working at once and their claims are gone. Less data is kept. The only visible change is that people see 404 "does not exist" instead of 410 "turned off", which is the owner's UX call.
4. **The first AI call of an install counted at its first held call**: accept, with L2.
5. **The footer and the default for `forget(now)`**: fine. The default is what keeps an old Worker paired with a new object safe.

### Positive observations (for calibrating risk)
- The page never stores the ID token anywhere but component state, and sends it only to `/v1/account/delete-with-google`. The source-safety test confirms that `accounts.google.com` appears only in `google-sign-in.ts`.

### Recommended Actions
1. M1: change the page text now; leave the session tombstone to the owner as a design decision.
2. L1: count `internal`, or correct `docs/proxy.md:409`.
3. L3, L4, L2: small code changes.
4. L5, L6, L7: fixes to the docs, or small code changes.

### Metrics
- Type coverage: `npm run typecheck` passes in `proxy/`, and the web typecheck passes inside `npm run build`.
- Tests: proxy 444 Vitest and 17 Node tests pass; web 231 tests pass. Coverage percentage was not measured, because no coverage tooling is configured.
- Linting: no JS/TS linter is configured in `proxy/` or `web/`.

### Unresolved Questions
- **Release workflow (another part)**: `.github/workflows/release.yml` (344bb73, outside this range) deploys with plain `npx wrangler deploy`. `docs/proxy.md:1366-1370` relies on `--durable-objects-code-update-mode immediate` for the first deploy of the kept count; the flag exists in wrangler 4.162.0. Without the flag, deletions in the first minutes on old QuotaBook code lose their month count, and installs' first calls go uncounted. The lead should align the two.
- **Indexing**: the legal pages send `x-robots-tag: noindex`. Whether they should be indexable is the owner's call; Play only needs them reachable.
- **Not verified here**: real GIS sign-in against the owner's client and the SQL API against a real account were only exercised against fakes.
