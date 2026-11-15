# Code review: Phase 6, proxy (bill links, live claims, web routes) and web app

Date: 2026-11-15 (UTC). Branch `feat/phase-06-web-companion-realtime-claim` at `28b5f70`, compared with `main` at `18c2842` (`git diff 18c2842..HEAD -- proxy web docs/proxy.md .github`). Security first. The implementers' reports were read for context only; every claim below was checked against the code, and the main ones against running probes.

## Code Review Summary

### Scope
- Proxy: `src/{shares,share-claims,share-state,share-room,turnstile,web,rate-limit,policy,requests,invites,reminders,account-data,index,links,input}.ts`, `migrations/0002_shares.sql`, `scripts/embed-web.mjs`, `wrangler.jsonc`, `vitest.config.ts`, and `test/` (shares, share-claims, share-live, web, fake-turnstile, fixtures).
- Web: `web/src/`, `web/tests/`, `web/scripts/`, `web/e2e/prepare-proxy.mjs`.
- Contract and plan: `docs/proxy.md` ("Bill links and the web pages", plus "Conventions"), `.github/workflows/ci.yml`, and the "Success Criteria" of `plans/261114-0220-split-vibe-production/phase-06-web-companion-realtime-claim.md`.
- Lines changed:

  | Area | Added | Removed |
  |---|---|---|
  | Proxy src, migrations and scripts | 1,544 | 81 |
  | Proxy tests | 1,634 | 16 |
  | Web src and scripts | 3,559 | 0 |
  | Web tests and e2e | 2,391 | 0 |

  `web/package-lock.json` is not counted.
- Focus: the first surface where people without an account write data.

### Overall Assessment
The core holds.
- The revision guard (trigger plus D1 batch) puts claims, lock, finalize, reopen and revoke in a correct order.
- Owner checks are uniform, and PublicShare is built field by field.
- Tokens never appear in paths, and expiry is judged at read time.
- The page has no HTML sinks and a strict CSP. Payment links are filtered on both the proxy and the page.

I found no authorization bypass and no XSS. The defects are in abuse economics and data retention:
- The public rate limiter writes to D1 without any bound (High).
- One address can fill any link.
- The per-IP limits other than new claims do not fit the "whole table on one Wi-Fi" decision, and the page's retry loop uses them up.
- The Turnstile test-secret shortcut has no guard in production.
- Confirmed requests keep showing the creditor's payment details publicly after the creditor deletes the account.
- A latent defect: the Durable Objects that hold the live connections are named by link id, so a link made again under an old id broadcasts to the old link's open pages.

### Critical Issues
None found.

### High Priority

#### H1. The public rate limiter writes to D1 on every request, before any token check and also past the limit, so the per-IP limits put no ceiling on D1 writes

Evidence:
- The counter is an upsert that always adds one: `proxy/src/rate-limit.ts:27-36` (`used = CASE ... ELSE used + 1 END`). The comment at `:16` says "A refused attempt is counted as well".
- Every public route calls it first, before the token is looked up:
  - `share-claims.ts:43` (`open`)
  - `share-claims.ts:99` (`claim`)
  - `share-claims.ts:170` (`live`)
  - `requests.ts:298` (`request-links/view`)
  - `invites.ts:92` (`invites/preview`)
- `rate_windows` has two indexes (`0001_groups.sql:188-195`). D1 counts an extra row written per index, so starting a window writes more than one row.

Scenario:
- A script sends `POST /v1/shares/open {"token":"x"}`; no valid token is needed.
- Each request writes one upsert and gets 404.
- Past 600 an hour it gets 429, and still writes.
- Scratch probe (not committed): with `used` set to 600, five more reads all got 429 and `used` became 605.

What goes wrong:
- On the free plan D1 allows 100,000 rows written a day.
- Cloudflare's D1 pricing page says that once an account reaches its daily limit, "you will not be able to run queries against D1" until the reset at 00:00 UTC. That includes reads.
- So about 100k junk requests, fewer once index rows count, stop every D1 feature for everyone for the rest of the day: groups, requests, bill links, devices, reminders and account deletion.
- Rotating IPv6 /64s multiplies any per-IP cap.

Caveats:
- The free Workers plan's own limit of 100,000 requests a day is of the same order. So this lowers the bar for an outage rather than creating one.
- On the paid plan it becomes cost amplification instead, since the D1 writes dominate what each request costs.

Normal use also pays:
- Every page view costs two counter writes (`live` and `open`).
- A page whose live connection keeps failing costs about 180 to 300 writes an hour (see M2).

Fix, best payoff first:
1. Look the token up first and count after. Junk and unknown tokens get 404 and write nothing. Tokens are 128-bit, so counting misses protects nothing against guessing.
2. Write nothing once over the limit. Use `ON CONFLICT (account, kind) DO UPDATE SET ... WHERE ?3 - rate_windows.started_at >= ?4 OR rate_windows.used < ?5`. When `RETURNING` gives no row, read `started_at` to compute `Retry-After`. Apply this to `admitHourly` for every kind.
3. Put a filter that writes nothing in front of the public routes, keyed by the hashed IP. Either:
   - the Workers Rate Limiting binding (`ratelimits`): its counters are per Cloudflare location, its period is 10 or 60 s, and it uses no storage, but its page does not say which plans have it, so check the account's plan;
   - or a Map in each isolate.
4. Optionally, a daily budget for public writes, so that signed-in features keep working when it runs out.

Tests to add: a refused request leaves `used` unchanged, and a junk token writes nothing.

### Medium Priority

#### M1. One address can fill any link: `newClaimsPerHour` (30) equals `maxClaims` (30)

Evidence:
- `policy.ts:121,131`
- `share-claims.ts:75-81,129`
- `share-claims.test.ts:290-312`, whose own words are "A whole table on one Wi-Fi fills a link from one address".

Scenario:
- Someone with the link (it was forwarded to a chat, or a prankster at the table) solves 30 Turnstile checks from one network. They post 30 claims with junk names.
- The first real diner who tries to claim then gets 409 `full`.
- The owner's options:
  - Remove claims one by one, each counting against the 300 changes an hour, while the attacker refills at 30 an hour.
  - Lock the link, which stops everyone.
  - Revoke it and share a new one.

Why it matters: the plan's risk table lists "per-IP rate limit" as a defence against claim spam. At this setting it no longer limits sabotage of a single link; only Turnstile does.

The decision: 30 an hour was chosen on purpose, so that a whole table on one Wi-Fi can claim. I am not proposing to undo it silently. Options:
- (a) Keep it. Document that Turnstile, lock and revoke are the defences.
- (b) Add a limit per hashed IP and per link below `maxClaims` (for example 20), alongside the 30 an hour per IP.
- (c) Store the salted IP name with each claim (deleted with it), and cap claims per link and per IP.

Trade-off: with (b) or (c), the 21st person on the same Wi-Fi has to switch to mobile data.

#### M2. The other per-IP limits do not fit the same "whole table on one Wi-Fi" decision, and the page's retry loop uses them up

Evidence:
- The limits are 120 claim writes and 600 reads an hour per IP (`policy.ts:132-133`).
- Every tap is one claim write, the first claim included (`share-controller.ts:215-229, 354-377`).
- Every page view, and every return to a visible tab, costs two reads, `live` then `open` (`live.ts:129-143`, `share-controller.ts:166`).
- While the live connection keeps failing:
  - backoff stops growing at 30 s (`live.ts:53-54, 188-196`);
  - the page also reads `open` once a minute (`share-controller.ts:92, 255-258`);
  - that comes to about 120 to 240 connection attempts plus about 60 reads an hour per page.

Scenarios:
1. Thirty people on one Wi-Fi pick 3 or 4 items each. That is 90 to 150 claim writes, so they hit the cap of 120. Everyone after that sees "Too many picks from this network" for the rest of the hour.
2. Pages get stuck reconnecting:
   - Cause: the network blocks WebSockets, the room is full (50 connections), or the IP's read limit itself has run out.
   - Two to four stuck pages use up 600 reads within the hour.
   - After that, `open`, `live`, the `/r/` view and the `/g/` preview answer 429 to everyone on that IP.
   - The share page silently keeps a stale view (`share-controller.ts:288` reports a problem only when no share is on screen).
   - When the window starts over, the stuck pages use up the new 600 within minutes, so the IP locks itself out.
   - Every attempt is also a D1 write (H1).

Fix:
- Proxy: scale `claimWritesPerHour` with the decision, at about 10 per possible claim (300). Raise reads once they no longer cost writes (after H1).
- Page:
  - After about 5 failures in a row, stop live retries and read `open` every 2 to 5 minutes instead.
  - Respect `Retry-After` on a 429 from `open`.
  - Say so on screen when reads are refused while a share is shown.
- Tests: add one that caps the requests per hour a page sends in this state.

#### M3. With Turnstile's always-pass test secret set in production, the check is bypassed, and nothing logs it

Evidence:
- With any of Cloudflare's test secrets, `success` alone decides; action and hostname go unchecked (`turnstile.ts:21,51`).
- `share-claims.test.ts:99-108` shows the dummy token `XXXX.DUMMY.TOKEN.XXXX` passing with the `1x...AA` secret.
- Nothing logs that a test secret is in use.
- With the always-fail `2x...AA` secret, every claim fails as a "token fault" (`turnstile.ts:15,48`), also without a log.

Scenario:
- The documented local pair (`docs/proxy.md`, "Run it on a computer", kept in the git-ignored local secrets file of `proxy/`) ends up as the Worker's secret.
- That can happen while trying the pages before the widget exists, or by uploading that file with `wrangler secret bulk`.
- Every new claim then passes with a dummy token. The remaining defences are 30 claims an hour per IP and 30 per link.

Fix:
- Accept the test-secret shortcut only when the request's host is local (`localhost`, `127.0.0.1`, `[::1]`); the e2e already runs on 127.0.0.1.
- On any other host, log `{"event":"turnstile_test_secret"}` and answer 503 `not_configured`.
- Change the vitest case to use a local host.
- While there:
  - pass `remoteip` (CF-Connecting-IP) to Siteverify, as Cloudflare recommends;
  - log action and hostname mismatches, never the token.

#### M4. Confirmed requests keep showing the creditor's payment details without a session, with no end date, also after the creditor deleted their account

Evidence:
- Deleting an account empties `payment` only where `confirmed_paid_at IS NULL` (`account-data.ts:60`).
- `request-links/view` returns `payment` whatever the status (`requests.ts:303-313`).
- Confirmed requests are never deleted:
  - the daily run deletes only rows with `deleted_at` set (`reminders.ts:101`);
  - withdrawing a confirmed request is 409 (`requests.ts:492`).
- The page itself hides the payment block once a request is confirmed ("Nothing is left to pay on this request", `web/src/pages/request-page.tsx:35`). So the field serves nothing on screen.
- Scratch probe (not committed), in order:
  1. create a request with `payment`;
  2. accept it, report it paid, confirm it;
  3. `DELETE /v1/account`;
  4. `POST /v1/request-links/view` still answers 200, with `creditorName: "Former member"` and the full `payment` (the PayPal handle is in both `link` and `details`).

What goes wrong:
- Anyone with the link can still read the deleted person's IBAN, UPI ID or handle, whatever `details`, `link` and `qrPayload` carry.
- That identifies the "Former member" again.
- The contract says exactly this ("Deleting an account empties payment along with method on the requests it made that are not confirmed"). So this is a gap in the contract, not code drifting from it.

Fix:
- In `viewRequestLinkRoute`, return `payment: null` once `confirmed_paid_at` is set.
- In `forgetSharedData`, empty `payment` on all of the account's requests. Keep the `method` rule if the debtor's app needs it.
- Optionally, empty `payment` when a request is confirmed.
- Update the contract and add the probe above as a test.

### Low Priority

#### L1. The Durable Objects that hold the live connections are named by link id, so a link made again under the same id broadcasts to the old link's open connections
- Evidence:
  - The object is `idFromName(shareId)` (`share-state.ts:213-215`), used by `tell` and by `share-claims.ts:175`.
  - Expiry, the daily deletion and account deletion close no connections, as the contract says.
  - A connection accepted just after a revoke's `gone` broadcast stays open; this was the implementer's own concern.
  - The contract explicitly allows making a link again under the same id once the old one is deleted.
- Scratch probe:
  1. Open a connection on link A.
  2. Delete A's row, as the daily run does.
  3. `PUT` the same id, which gives a new token.
  4. Claim on the new link.

  The old connection received the new link's bill and claims without ever holding its token.
- Today this is latent: the app always makes a fresh UUID (`ClaimViewModel.kt:66,155-160`).
- Fix: name the object `${id}:${created_at}` (both call sites have the row), or name it by the token's hash.

#### L2. When a link already has 50 connections, the object refuses new ones instead of closing dead or hoarded ones
- Evidence: `share-room.ts:39-44` uses no tags and closes nothing.
- Scenario: one person with the token opens 50 connections from one IP and keeps them alive; that costs 50 of the IP's 600 reads. Or dropped mobile connections linger. Later pages fall back to reading the link, and into M2's loop.
- Fix:
  - When full, close connections whose `ctx.getWebSocketAutoResponseTimestamp(ws)` is older than about two ping intervals; the page pings every 30 s.
  - Tag each connection with the hashed IP (`acceptWebSocket(server, [tag])`, `getWebSockets(tag)`) and cap each tag.

#### L3. Revoking has no limit, and always writes and wakes the link's object, even when the link is already revoked
- Evidence: `shares.ts:297-308`.
- Fix: when `revoked_at` is already set, answer 204 without the batch and without the broadcast. Keep the first revoke unlimited.

#### L4. A new claim is not idempotent
- Evidence: the proxy makes the claim's id and key (`share-claims.ts:135-148`).
- If the answer is lost, the browser has no key for the claim it made. That claim:
  - takes one of the 30 places;
  - makes the browser pass a second Turnstile check;
  - shows a duplicate name until the owner removes it.
- Fix option: let the browser send a fresh key with its first claim, and keep that key's hash. A retry with the same key then follows the known-key path.

#### L5. Names allow Unicode format characters
- Evidence: `input.ts:74` refuses C0 and C1 control characters only. So claim names (`share-claims.ts:57`) can carry:
  - bidi overrides (U+202A to U+202E, U+2066 to U+2069);
  - zero-width characters (U+200B to U+200F);
  - U+FEFF.
- Look-alike names ("Minh" against "Minh" plus U+200B) and reversed-looking text reach every page and the owner's matching screen. It is text only, so no XSS.
- Fix: refuse `\p{Cf}` in claim names on both sides, or at least the bidi controls. Render names inside `<bdi>` or with `unicode-bidi: isolate`.

#### L6. `parseAmounts` writes participant ids into a plain object
- Evidence: `web/src/contract.ts:207-213`.
- The id pattern (`share-state.ts:234`) allows `__proto__`. Such an id sets the object's prototype instead of a key.
- Only the owner controls these ids, so it affects only that owner's page. The proxy already builds this map with `Object.fromEntries` (`share-state.ts:326-327`).
- Fix: use `Object.fromEntries`, `Object.create(null)` or a `Map`.

#### L7. Some misconfigurations fail without a trace
- Siteverify's action or hostname does not match: the check returns false, with no log (`turnstile.ts:52`).
- `ABUSE_EMAIL` is accepted by the Worker (`web.ts:8`, for example an address without a dot) but refused by the page (`web/src/links.ts:48`). The result is no link and no log.
- `embed-web.mjs:114-124` writes the placeholder page whenever `web/dist` is missing.
  - `npm test`, `typecheck` and `dev` all run it.
  - A bundle made after them on a machine without a web build serves "This page is not available right now" at every `/s/`, `/r/` and `/g/`.
  - Once a deploy workflow exists, make it refuse the placeholder, for example with `embed-web.mjs --require-built`.

#### L8. `embed-web.mjs` checks less than its comment says
- `checkPage` (`embed-web.mjs:68-81`) skips any reference that has a scheme (`https://...` scripts and styles), and does not look at `style=` or `on*=` attributes.
- `web/scripts/verify-build.mjs` does check them, but only when `npm run build` ran it.
- The CSP blocks all of these at runtime, so the failure is a broken page, not an injection.
- Fix: reuse verify-build's checks in embed-web.

### Edge Cases Found by Scouting
- **Claim against lock:** both orders and simultaneous sends are covered by tests. The trigger plus the batch make them happen in a strict order. Correct.
- **PUT trimming against a claim in flight:** correct, though untested.
  - A claim decided on the old bill re-checks `itemsInBill` in its guard. It is refused, reads the link again and gets 400.
  - A claim that landed first is trimmed in the PUT's batch.
- **Finalize against reopen, revoke or a newer PUT:** the guard conditions force a fresh read, which then answers 409 or 410. Correct.
- **A claim against the daily deletion or an account deletion:** correct.
  - The guard updates no row, and the claim's insert fails on the foreign key.
  - That counts as refused, the link is read again, and the answer is 404.
- **Broadcasts arriving out of order:** the page keeps the view with the higher revision. Correct.
- **A connection racing a revoke:** a connection accepted after `gone` hears nothing more. The page's `open` right after connecting answers 410, and the page closes it. Fine, but see L1.
- **Objects named by link id, with no close on expiry or deletion:** L1.
- **A live connection that keeps failing:** a request loop, see M2 and H1.
- **Local test runs load the local secrets file of `proxy/`:** the log says "Using secrets defined in" it.
  - `vitest.config.ts` pins five bindings, so other variables (`TURNSTILE_SITE_KEY`, `ABUSE_EMAIL`, and so on) differ between a developer's machine and CI.
  - No test depends on them today. Pin them to keep it that way.

### Checked and found correct
- **Authorization**
  - Every owner route is `signedIn` and checks the owner. Other accounts get 404, and 409 for `PUT`. The guard SQL repeats `owner = ?3`.
  - No route lists links.
  - Public routes never change the bill, the status or the amounts (tested).
  - Claim keys and Turnstile tokens open no other route, `/v1/bill-analysis` included (tested).
- **Data exposure**
  - PublicShare is built field by field (`share-state.ts:136-145`). Answers hold no account, hash, IP, link id or bill id (tested by scanning them).
  - `view` and `preview` return only the fields the contract names.
  - Errors and logs carry codes and error names only.
- **Tokens and keys**
  - Link tokens are an HMAC that includes `createdAt`. Only their SHA-256 is kept, and they never appear in a path or query.
  - The live connection's token travels in `Sec-WebSocket-Protocol`. The link's object receives only the upgrade, never the token.
  - `/s/<token>` is 404.
  - Claim keys are 128 random bits, stored as a hash, and scoped by `(share_id, key_hash)`.
- **Expiry and cleanup**
  - Expiry is judged at read time in `linkedShare`, `changeShare` and `readShareRoute`, and in the guard SQL (`expires_at > ?2`).
  - Revoking empties the bill and the amounts and deletes the claims in one batch.
  - The daily run and account deletion delete claims through the foreign key's `ON DELETE CASCADE`.
- **Turnstile, with a real secret**
  - The action `claim` and the request's hostname are checked, and Siteverify lets a token pass only once.
  - New claims are counted before Siteverify is asked.
  - The 5 s timeout refuses the claim, and Siteverify's answer is capped at 16 KB.
  - No token is logged.
- **The page**
  - Sources have no HTML sinks (tested). The one `innerHTML` in the bundle is Preact's `dangerouslySetInnerHTML` path, which nothing uses.
  - The CSP matches what the built page loads:
    - one module script and one stylesheet, both under `/assets/`;
    - a `data:` favicon;
    - Turnstile's script and frame;
    - same-origin fetches and `wss://<host>`.
  - Meta values are checked for shape and HTML-escaped. The replacement is a function, so `$&` cannot surprise.
  - Payment links:
    - filtered the same way on both sides: `https://` with a host, through `URL.href`, or `upi://pay?`;
    - opened with `noopener noreferrer`.
  - `Referrer-Policy: no-referrer` is set as a header, as a meta tag and on every fetch.
  - The mailto subject is fixed and encoded.
  - The `intent:` link is built only from `location.host`, a fixed path and a token whose shape was checked. Its Play Store fallback carries no token.
- **Bodies and amounts**
  - Body limits are as documented: 512 KB for the owner's routes, 32 KB for a claim, 16 KB for `open`, `view` and `preview`, and 64 KB for the stored bill.
  - Amounts are summed as BigInt.
  - On the proxy, a participant id of `__proto__` stays a plain key.

### Positive Observations
The `racing` D1 proxy in `share-claims.test.ts` really lands one batch just before the other, so the lock race tests exercise the guard rather than timing luck.

### Recommended Actions
1. H1: count only after the token resolves, write nothing past the limit, and put a filter that writes nothing in front.
2. M3: refuse test secrets on hosts that are not local, and log it.
3. M4: stop returning `payment` once a request is confirmed, empty it when the account is deleted, and update the contract.
4. M2: raise the claim-write and read limits to fit a full table, and slow the page's retries.
5. M1: the product owner decides between keeping 30 and adding a limit per link.
6. L1: name the objects `${id}:${created_at}`.
7. L2 to L8 when convenient.

### Plan follow-ups (Todo of phase-06; no plan file was edited)

| Todo | Status |
|---|---|
| Contract, migration, permission tests | Done |
| Link lifecycle routes | Done |
| Public claims with Turnstile and limits | Done, with M1 to M3 open |
| Live updates through Durable Objects | Done, with L1 and L2 open |
| Pages `/s/`, `/r/`, `/g/` with tokens and headers | Done |
| Projecting the bill again when it is edited (proxy side) | Done; trimming is tested |
| Daily cleanup, and the page of a link that no longer works | Done |
| Account deletion removes links and claims | Done; M4 is about requests |
| E2E, Lighthouse, bundle size | Reported by the web implementer; not rerun, as instructed |
| App items | Outside this review |

Whether to keep the plan's review step open until H1, M3 and M4 are fixed is for the lead to decide.

### Success criteria

| Criterion | Result |
|---|---|
| No route lists links | Met, tested |
| The right token reads a link while it is valid | Met, tested |
| Expired and revoked links cannot be read, before deletion too | Met, tested |
| No public route changes the projection | Met, tested |
| `claim` refuses a name that is too long, an unknown item, the 31st claim, and anything after lock; a new claimant without a valid Turnstile token is refused | Met, tested; except with a test secret (M3) |
| A claim just before `lock` is in its answer; one just after is refused, with both sent together | Met; tested both ways and sent at once |
| Two browsers and an Android phone see a pick within 2 s | Browsers: the implementer's e2e reports 14 to 83 ms (not rerun). Android: outside this review. |
| The amounts on the page, in the app and from `SplitCalculator` are equal (automated) | Not checkable in proxy/ or web/; the proxy only checks that the amounts add up |
| Deleting an item while the link is open trims the claims that had it and keeps the others | Met, tested |
| The projection has no account, hash or IP | Met, tested |
| The claim key and Turnstile token cannot call bill reads or session routes | Met, tested |
| Headers: noindex, no-referrer and CSP | Met, tested in proxy and web |
| Payment links: new tab, no referrer, https or upi only | Met, tested in proxy and web |
| After the daily run, expired links have no claims | Met, tested |
| Lighthouse scores and bundle size are reported | In the web implementer's report (25.8 KB gzipped, scores 99 to 100); not rerun |

### Metrics
- **Types:** strict TypeScript in both packages. `npm run typecheck` in `proxy/` passes.
- **Tests:**
  - `proxy/`: `npm test` passes, 240 tests in 18 files.
  - `web/`: `npm test` passes, 142 tests in 15 files.
  - The three scratch probes behind H1, M4 and L1 pass. They ran from the scratchpad; nothing was committed and the working tree is clean.
  - Coverage is not measured; neither package configures it.
- **Linting:** no JS/TS linter is configured for `proxy/` or `web/`.

### Unresolved Questions
1. M1: keep 30 new claims an hour per IP with no limit per link?
2. `/v1/request-links/view` keeps showing a request, with its payment, to anyone holding the link after another account accepted it. The signed-in `open` hides it in that case (409, "nothing of the request is shown"). Is that intended?
3. Has the production Turnstile widget (managed mode, interaction-only, real site key) been checked against the CSP? The e2e used the always-pass test site key.
4. Does the account's plan include the Workers Rate Limiting binding? Cloudflare's page does not say which plans have it.
5. Will deploys run through a workflow that guarantees `build:web` ran (L7)?

Sources: Cloudflare D1 pricing and limits (https://developers.cloudflare.com/d1/platform/pricing/); Workers Rate Limiting binding (https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/).
