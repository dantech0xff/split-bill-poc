# Security review for the release: threat model and findings

## Code Review Summary

### Scope
- Repository at `1414ada` (19 November 2026). The proxy, web, workflow and scripts files did not change between the start of the review (`e3610f1`) and that commit; the app files that did were re-checked where cited.
- Read in full: all 39 files of `proxy/src/` (7,129 lines), `proxy/migrations/`, `proxy/wrangler.jsonc`, the five workflows, `scripts/release-checks.py` (the parts that decide), `.gitignore` files, `.github/dependabot.yml`.
- Read where they touch security: `web/src/` (API client, route, links, payment block, request page, claim store, Google sign-in, deletion page, footer), `app/src/main/AndroidManifest.xml`, the debug network security config, `AccountStore`, `KeystoreCipher`, `GoogleAccounts`, `MainActivity`, `AppLink`, `PushService`, `Notices`, `DeviceRegistrar`, `SplitVibeApp`, `BillingRepository`, `RevenueCatBilling`, `ProxyApi`, `RequestIds`, `RequestRepository`, the group screens and view models, `OcrLayoutEncoder` and `Balances` in `core/domain`.
- Docs: architecture, proxy, web, release, pro-subscription, privacy-policy, runbook, release-checklist; the phase 9 plan's security requirements; the proxy and web review of 19 November (`plans/reports/code-reviewer-261119-0255-phase-9-proxy-web-review.md`), so as not to report its findings twice.
- Checks run: `npm test` in `proxy/` (23 files, 444 tests in the workers runtime, and the Node tests: all pass); a scan of all 361 commits for key-shaped strings, printing file names only (none found); the arithmetic of a bill read's spend hold, with the proxy's own prompt, schema and sample request. Gradle was not run (another agent uses it), so the merged manifest and the Android tests were not checked here.
- Third-party behaviour checked against current docs: Cloudflare's trace attributes, Workers Free limits, and Version URLs.
- Scouting was done by reading and searching the code directly; no scout skill was available in this session.
- Output: `docs/security-threat-model.md` (502 lines), and this report.

### Overall Assessment
- No Critical defect. I found no way to read or change another person's data, make anyone Pro, forge or usefully replay a webhook event or a session, run script on the pages, phish through push texts, leak a secret through logs, traces, errors or the repository, or reach production from a pull request. Permissions are checked on the state the change applies to, every SQL value is bound, and link tokens never travel in a URL.
- One High is about availability, not money. The spend caps hold, but the one global cap can be filled by an anonymous client in minutes, and AI then stops for every user, Pro included, for up to a day. The release checklist (`docs/release-checklist.md:48-51`) blocks release on a High.
- Four Mediums: group removal, sessions after deletion (first reported in the proxy and web review, still open), phishing through user-written pages on the service's address, and the Workers Free plan's daily request limit.
- Five Lows, about App Links, traces, the repository and CI hardening.
- The release checks the plan asks for are all in place: no key, not debuggable, no plain HTTP, https proxy, secret scan, dependency scan. The one gap is Gradle vulnerability scanning (L4).

### Critical Issues
None.

### High Priority

**H1. An anonymous client can stop AI for every user for up to 24 hours, for a few dollars of the owner's money and none of its own calls**

Where:
- `proxy/src/identity.ts:60-62`: an install is any UUID sent in `X-SplitVibe-Install`, and nothing ties it to a phone. A script therefore gets a fresh caller, and a fresh $3 caller cap, for every call.
- `proxy/src/model-call.ts:142-146`: every call also holds against one ledger, `global`, capped at $20 per fixed 24-hour window (`proxy/src/policy.ts:63-65`; `proxy/src/ledger.ts:35-44`, `:159-162`). Trial installs, free accounts and Pro accounts all share it.
- `proxy/src/bill-analysis.ts:22` with `proxy/src/spend.ts:52-55`: a bill read can fall back to another model, so it holds at the dearest price. That is 471,685 µ$ (47 cents) for the sample bill (`app/src/sharedTest/resources/fixtures/sample_bill_request.json`) and 777,340 µ$ for the longest document, computed with the proxy's own prompt and schema.
- `proxy/src/respond.ts:63-66` and `proxy/src/model-call.ts:202-211`: suppose the client hangs up once the model has started, and the model does not answer within `afterHangUpMs` (25 s, `proxy/src/policy.ts:100`). The call is aborted and counted as spent at its whole hold on all three caps, and the caller's call is given back. The same happens to a call that runs past `modelDeadlineMs` (120 s). `proxy/test/bill-analysis-edges.test.ts:78-86` pins this: after the hang-up the install still has `used: 0, remaining: 3`, and its spend is `SAMPLE_HOLD`.

Scenario:
- A script sends bill reads, each with a new random install id and a long `<bill_ocr>` document, so the model runs past 25 s, and closes the connection after the first `wait` line.
- One address is refused once its $6 is held or spent, which takes 12 sample-size calls (7 of the longest). At 10 calls a minute per address that is about two minutes.
- Four addresses fill the $20: four IPv4 addresses, or four /64s of one IPv6 allocation, since `ipKey` counts IPv6 by /64 (`proxy/src/ledger.ts:115-122`).
- After that, every bill read from every install and account is refused with 429 `busy` until the window started by the first hold ends, up to 24 hours. Edits survive only on the few cents left.
- Anthropic bills only the tokens it processed, a few cents per call, so the owner pays a few dollars. The script spends none of its calls, since each is given back.
- It works again in the next window. The plan's answer to a cost spike, lowering the cap or switching AI off (`docs/runbook.md:78-81`), only deepens the outage.

Impact: the paid feature (300 calls a month on Pro) is off for everyone whenever someone wants it off. Spend stays within the caps, so this is availability, not cost, and not Critical.

Fix. (a), (b) and (d) together close it; (c) and (e) are stronger.
- (a) Give anonymous installs their own daily pool. For `caller.kind === "install"`, hold against a second ledger, `global:trial`, capped well under the global one (say $3 to $5). Trial abuse then cannot use up what accounts rely on. A separate pool for Pro is the same idea.
- (b) Do not give the call back when the caller hung up after the model was reached (`hungUp.aborted && reachedModel` in the `finally` of `callModel`); charge it like an answered call. An app that retries a dropped read reuses its request id, which is charged once (`QuotaBook.hold`), so honest retries do not pay twice. This changes the documented rule "A call that got no answer is given back" (`docs/proxy.md:90-98`) for hang-ups, which is the owner's decision.
- (c) Settle an unanswered call at what the stream reported: the input tokens of `message_start`, and output bounded by the time it ran. Keep the whole 16,000-token hold only when nothing was reported. Thinking tokens are billed beyond what streams, so this needs a conservative output bound.
- (d) Alert at 80% of the global cap, as the plan's operations list asks, and add a runbook entry for `busy` at the cap.
- (e) Play Integrity on trial calls, or a limit on new installs per address per day.

### Medium Priority

**M1. Taking a member out of a group does not keep them out**

Where:
- `proxy/src/groups.ts:227-248`: removal takes the account off its member and drops its claims. Invites are left as they are.
- `proxy/src/invites.ts:16-35`: any member makes invites, 20 an hour, and an invite does not record who made it.
- `proxy/src/invites.ts:139-157`: joining as a new member needs only an invite that works. It does not check whether the account was removed.
- `app/src/main/java/com/dantech/splitvibe/ui/groups/GroupScreen.kt:188-199`: the dialog calls only `onRemoveMember`.
- `app/src/main/res/values/strings.xml:578`: the dialog tells the owner "They will no longer see the group."
- No test covers re-joining after removal (`proxy/test/groups.test.ts:278-321`). `docs/proxy.md:539-540` says a removed account can join again through an invite, "which the owner can revoke".

Scenario:
- Mallory, a member, makes an invite and keeps its token.
- The owner takes her out, and the app says she will no longer see the group.
- She calls `POST /v1/invites/join` with that token and a new member id, and is a member again at once. She sees every member, bill, settlement and log entry.
- She can add bills that put shares on anyone, and ask any member with an account for money, with daily reminders.
- An invite left in the group's chat does the same. The owner learns of it from the log, if they look.

Fix:
- Revoke the group's live invites in the removal's batch (`UPDATE invites SET revoked_at = ?2 WHERE group_id = ?1 AND revoked_at IS NULL`).
- Better, keep the removed account in a per-group list that `joinGroupRoute` refuses with 403 until the owner lets them back, for instance by approving a claim.
- Say in the dialog what happens to invites, and test the re-join.

**M2. Sessions outlive sign-out and account deletion, and a phone still signed in brings back what the deletion removed**

First reported as M1 of `plans/reports/code-reviewer-261119-0255-phase-9-proxy-web-review.md` for the web route; still open at `1414ada`, and listed here because the security review must rate it.

Where:
- `proxy/src/session.ts:77-90`: a session is checked by signature and expiry only, for 30 days (`proxy/src/policy.ts:48`). Neither `proxy/src/account.ts:158-168` (deletion) nor signing out revokes anything.
- `app/src/main/java/com/dantech/splitvibe/SplitVibeApp.kt:33-41`: every start of the app's process with a session registers the push token again. That process start can be a daily recurring-bill run.
- `app/src/main/java/com/dantech/splitvibe/MainActivity.kt:43` with `billing/BillingRepository.kt:94-106` and `billing/RevenueCatBilling.kt:40-47`: RevenueCat's SDK is started as the stored purchaser, and reading the customer has RevenueCat create it again.
- `proxy/src/account.ts:59-85`: `GET /v1/usage` records the purchaser as handed out again, so webhook events and `POST /v1/plan` ask RevenueCat about it again.
- `web/src/pages/delete-account-page.tsx:245-258`: the page says "Split Vibe's server no longer keeps your account", and nothing on it asks the person to sign out on their phones. The privacy policy does ask (`docs/privacy-policy.md:200`).

Scenario:
- A person deletes their account at `/delete-account` while their phone is signed in.
- Within a day the phone's device row (push token, install id, account id) is back in D1.
- The next time they open the app, RevenueCat's customer is back under the same id.
- A session copied off a phone works for up to 30 days after sign-out or deletion. The only way to end it is rotating `SESSION_SIGNING_KEY`, which signs everyone out.

Fix:
- Now: on the web page, before deleting and in the result, ask people to sign out of Split Vibe on their phones, or to uninstall it, and correct "no longer keeps your account".
- Proper fix, the owner's decision: keep a mark per account, `sessionsBefore`, written by `deleteAccount` and by a new sign-out route, and kept for `POLICY.sessionMs`. Refuse a session whose `iat` is earlier.
  - The mark could live in a D1 row the daily run forgets, or in the `QuotaBook`, which already outlives a deletion.
  - Checking it on every signed-in request costs a read; checking it on writes, `/v1/usage`, `/v1/plan` and `/v1/devices` is enough to stop the resurrection.
  - The app already turns 401 `session` into "sign in again" and stops RevenueCat (`AccountStore.expireSession`, `BillingRepository.follow`).
  - Add the mark to the privacy policy's retention list.

**M3. Request and bill-link pages publish any signed-in account's text, link and QR code under the service's own address, without saying it is user-written**

Where:
- `proxy/src/input.ts:284-313`: `link` may be any `https://` URL with a host, `qrPayload` any text of up to 512 characters, `details` up to 1,000.
- `proxy/src/requests.ts:138-172`: `creditorName`, `payerName` and `title` are free text.
- `proxy/src/requests.ts:298-323`: the request is served without a session. A request link never expires; only withdrawal, confirmation or the creditor's account deletion ends what it shows.
- `web/src/pages/request-page.tsx:27-45` and `web/src/ui/payment-block.tsx:50-65`: the page shows "<creditorName> asks <payerName> for", "For <title>", a "Pay on <host>" button, a QR code captioned "Scan it in your banking app.", and the details.
- `web/src/ui/footer.tsx:7-14`: the only notice is "Split Vibe never holds or moves money", plus a "Report abuse" link that `proxy/wrangler.jsonc:32` (`ABUSE_EMAIL: ""`) leaves out.
- Bill links' `amounts[*].payment` are the same (`proxy/src/share-state.ts:318-337`).

Scenario:
- Any Google account signs in and makes a request with creditorName "Split Vibe", title "Account check" and details "Your Split Vibe account will be closed. Confirm your bank login: scan the code."
- Its `link` is `https://splitvibe-check.example/login`, and its QR payload is the same address. Thirty such links an hour per account (`proxy/src/policy.ts:123`), working until withdrawn.
- On the service's own address, the page shows "Split Vibe asks Minh for ₫1,000", a "Pay on splitvibe-check.example" button, and a code for the banking app.

Fix:
- (a) On `/r/` and `/s/`, put a plain notice above how to pay: the names, text, link and code were written by the person who sent the link, and Split Vibe has not checked them.
- (b) Refuse a `qrPayload` that is not one of the formats `PaymentRequestBuilder` makes, so a code cannot carry a web address: EMV `000201…` (VietQR, PromptPay), EPC `BCD\n…` (SEPA), `upi://pay?…`.
- (c) Set `ABUSE_EMAIL` before release (already on the checklist), and consider failing the release workflow while it is empty.
- (d) Optionally, let request links expire, as bill links do after 30 days.
- Restricting `link` to known hosts would break the pasted-link payment method, so keep the host on the button.

**M4. On the Workers Free plan, one client can stop the whole service until midnight UTC**

Where:
- `docs/proxy.md:1381-1382`: the deploy assumes the Free plan, moving to Paid only on CPU errors.
- `docs/release.md:245-247` and `proxy/wrangler.jsonc`: no route and no custom domain, only the `workers.dev` address, so no WAF rule can limit requests in front of the Worker.
- `proxy/src/index.ts:189-205`: every path, a 404 too, is a Worker invocation.
- Cloudflare's Free plan allows 100,000 requests a day per account, then answers Error 1027 until 00:00 UTC (developers.cloudflare.com/workers/platform/limits, checked 19 November 2026).
- Separately, reading a group has no rate limit (`proxy/src/groups.ts:106-112`), and a full group's state is about 2,700 rows of D1.

Scenario: a loop of `curl` at about 100 requests a second to any path uses the day's requests in about 17 minutes. From then until midnight UTC, the app, sign-in, the web pages and RevenueCat's webhook all fail for everyone. Organic growth reaches the same wall.

Fix:
- Run production on Workers Paid before the closed test, where requests past the allowance are billed rather than refused, and add it to `docs/release-checklist.md`.
- For a fence in front of the Worker, serve it from a custom domain on a Cloudflare zone with a rate-limiting rule (the Cloudflare token then needs Zone > Workers Routes, per `docs/release.md:245-247`).
- Consider an hourly read limit per account for group reads.

### Low Priority

**L1. The production App Links file trusts the debug key, for the release package too**
- Where:
  - `proxy/src/links.ts:97-103`: every fingerprint in `ANDROID_CERT_SHA256` is paired with both `com.dantech.splitvibe` and `com.dantech.splitvibe.dev`.
  - `docs/proxy.md:1250-1253`: the owner is told to put "the debug key, the upload key and the Play app signing key" in it.
- Scenario: someone with a copy of the owner's debug keystore signs a build of the package `com.dantech.splitvibe` with it. Debug keystores usually sit on the developer's computer under a well-known password. On a phone without the Play app, that build is a verified handler of `https://<proxy host>/g/` and `/r/`, and receives invite and request tokens. It needs the keystore and a sideloaded app.
- Fix: pair packages with their own keys (the release package with the Play app signing key and the upload key, `.dev` with the debug key), and keep the debug key out of the production Worker's variable.

**L2. Traces at full sampling tie account and install ids to city, network and device for 7 days**
- Where:
  - `proxy/wrangler.jsonc:12-17`: traces are on for every request.
  - Outbound spans record `url.full`, which for RevenueCat is `https://api.revenuecat.com/v1/subscribers/<account id>` (`proxy/src/revenuecat.ts:46`, `:71`).
  - The device route's URL holds the install id: `app/src/main/java/com/dantech/splitvibe/notifications/DeviceRegistrar.kt:36`, `/v1/devices/<install id>`.
  - Durable Object spans record the object's id, which `idFromName("google:<account>")` determines.
  - Root spans record the city, the ASN and the user agent (Cloudflare's trace attributes, checked 19 November 2026).
  - `docs/privacy-policy.md:173` says traces hold "its address on our server (which holds IDs only)", without saying they link to an account.
- Scenario: anyone with the Cloudflare account's observability access can list, request by request, the city, the network and the phone model a given account or install called from.
- Fix: sample traces (`observability.traces.head_sampling_rate`, say 0.05), and say in the policy that traces can be tied to an account's code. Optionally, give RevenueCat a purchaser id derived separately from the account id, which also keeps RevenueCat from holding the key of every D1 row.

**L3. The FCM key file is not git-ignored, and the secret scan runs only after the push**
- Where:
  - `docs/proxy.md:1246-1250`: the owner runs `npx wrangler secret put FCM_SERVICE_ACCOUNT < service-account.json` from `proxy/`, then deletes the file.
  - `.gitignore` and `proxy/.gitignore`: no entry for it.
  - `.github/workflows/security.yml:6-8`, `:31-43`: gitleaks runs on push and on pull requests.
- Scenario: the file stays in `proxy/`, and a later `git add .` takes it. The push to the public `main` shows the service account's private key, and gitleaks fails that run after the key is public.
- Fix: ignore `*service-account*.json`, and turn on GitHub's secret scanning push protection for the repository, which blocks a push holding a known key before it lands.

**L4. The Gradle dependencies are not scanned for known vulnerabilities**
- Where: `.github/workflows/security.yml:55-61`. Dependency review sees npm and the actions only; its comment says Gradle needs dependency submission, which is not set up. Dependabot's version updates (`.github/dependabot.yml`) raise no vulnerability alerts without the dependency graph.
- Scenario: an advisory is published for an Android library the app ships, such as OkHttp, Firebase, RevenueCat or Room. No alert is raised and no check fails. The plan asks for dependency scanning in CI.
- Fix: add a job on pushes to `main` that runs `gradle/actions/dependency-submission`, pinned by commit. It feeds the dependency graph, Dependabot alerts and dependency review.

**L5. Install scripts run on the runner that then holds the Cloudflare token**
- Where: `.github/workflows/release.yml:271-274` runs `npm ci` twice with scripts on; `:277-280` builds the web app; `:283-318` runs `wrangler` with `CLOUDFLARE_API_TOKEN`.
- Scenario: a malicious version of a package in either lock file, merged through a Dependabot pull request for instance, runs its install script in this job. The token is not in that step's environment, but the script can rewrite `node_modules/wrangler`, which the next steps run with the token. Code deployed with the token runs with every Worker secret.
- Fix: `npm ci --ignore-scripts` where the build allows it. Or build the bundle in a job without the token, pass it on as an artifact, and deploy it from a job that installs only `wrangler`.

### Also open from the proxy and web review of 19 November, not repeated above
- Its L7: the docs ask to add `http://localhost` origins to the production Google web client (`docs/web.md:72-74`, `docs/proxy.md:1156-1158`). A page served on a person's own computer could then get ID tokens for it.
- Its L3: `GOOGLE_CLIENT_ID` is trimmed for the page but not for the token's audience (`proxy/src/session.ts:34`).
- Its L2: a trial install's `firstCall` keeps a timestamp where a flag would do (`proxy/src/quota-book.ts:144-148`).
- Its open question: `.github/workflows/release.yml:318` deploys without `--durable-objects-code-update-mode immediate`, which `docs/proxy.md:1366-1370` asks for on some first deploys.

### Edge Cases Found by Scout
Checked and found sound:
- **Object references:** every group, bill, settlement, request, link and claim route checks the caller against the state it changes (`proxy/src/group-state.ts:146-154`, `:216-224`; `proxy/src/requests.ts:110-121`; `proxy/src/shares.ts:76-99`). Ids of other accounts answer 404 or 403 with nothing of the object.
- **Races:**
  - Group changes go behind a revision trigger (`proxy/migrations/0001_groups.sql:17-25`), link changes behind SQL guards (`proxy/src/share-state.ts:211-234`).
  - Confirming a request writes its settlement in the same batch, with one settlement per request by a unique index.
  - Debtor binding is one conditional update. Counts and spend are serialised in Durable Objects.
- **Request id squatting:** group request ids are name-based and computable by a member (`app/src/main/java/com/dantech/splitvibe/data/requests/RequestIds.kt:15-17`). The app retries a 409 under a random id (`app/src/main/java/com/dantech/splitvibe/data/requests/RequestRepository.kt:83-88`), so squatting blocks nothing.
- **Webhook:**
  - The content is never trusted; it only triggers reads.
  - The auth value is compared by digest in constant time.
  - Replays change nothing RevenueCat does not say now.
  - Ids are capped at 20 and must have been handed out.
- **Making someone Pro:** no route takes a plan from the client. Refunds, cancellations and billing failures past the period end Pro at once (`proxy/src/revenuecat.ts:102-149`).
- **Script injection:**
  - No HTML sink in `web/src`. Meta values are escaped (`proxy/src/web.ts:42-50`).
  - Each page's CSP allows its own scripts only (plus Turnstile's, or Google Identity Services' paths on `/delete-account`).
  - `frame-ancestors`, `base-uri` and `form-action` are `'none'`. No cookies and no CORS headers.
- **Tokens in URLs:**
  - Links carry tokens in the fragment (`web/src/route.ts:17-22`; `proxy/src/links.ts:71-73`).
  - The live connection carries its token in the subprotocol, and the room gets nothing of the request (`proxy/src/share-claims.ts:208-224`).
  - Traces record no header values (Cloudflare's trace attributes).
  - D1 trace text holds no values: every SQL interpolation in `proxy/src` is a code constant.
- **Logs:** every `console.*` in `proxy/src` prints an event, codes, counts or an error's name; the SDK's logging is off; invocation logs are off.
- **Push:** fixed texts, and the data holds the type and one id (`proxy/src/push.ts:18-26`, `:162-165`). The app validates extras as UUIDs, and its pending intents are immutable.
- **Android:**
  - Backups:
    - `allowBackup="false"`.
    - The session, the email and the purchaser are AES-GCM under a non-exportable Keystore key, and a copy restored elsewhere fails to decrypt and reads as an expired sign-in (`app/src/main/java/com/dantech/splitvibe/data/account/AccountStore.kt:219-235`, `app/src/main/java/com/dantech/splitvibe/data/settings/KeystoreCipher.kt:33-42`). A backup or transfer cannot restore a session or the purchaser on another phone.
  - Network:
    - No network security config in release, and `targetSdk` 36.
    - The debug build allows loopback only.
  - Components:
    - Only `MainActivity` is exported. It takes content URIs of other apps only (`app/src/main/java/com/dantech/splitvibe/MainActivity.kt:80-91`), and links on the proxy's host with a token-shaped fragment (`app/src/main/java/com/dantech/splitvibe/ui/AppLink.kt:54-67`).
    - Joining a group and accepting a request each need a tap.
  - Code:
    - No WebView, no `Log` calls, no HTTP logging interceptor.
    - Printed text cannot close the OCR tags: `core/domain/src/main/kotlin/com/dantech/splitvibe/ocr/OcrLayoutEncoder.kt:161-167` turns `<` and `>` into `‹` and `›`.
- **CI:**
  - Pull requests run with `contents: read` and no secret.
  - The release needs a `v*` tag on a commit of `main`, then approvals of the `production` environment.
  - Actions are pinned to commits; bundletool and gitleaks are checked by SHA-256.
  - Play uses Workload Identity Federation bound to the repository's and owner's ids and the environment.
  - The upload key is decoded with umask 077 and deleted after the build.
- **Version URLs:** none for this Worker, since Cloudflare gives none to a Worker that implements Durable Objects. Old versions are not reachable with production secrets.

### Positive Observations
- An event from the webhook is treated as a signal and never as data, and the plan is always read back from RevenueCat. That is why a leaked or replayed webhook cannot make anyone Pro.
- Link tokens are 128-bit and stored only as hashes, and they never appear in a URL. Combined with traces that record URLs but no headers, this keeps every token out of Cloudflare's records.

### Recommended Actions
1. H1: before release, add a separate trial pool (a), charge hang-ups (b; the owner decides the rule change) and alert at 80% (d).
2. M4: move production to Workers Paid, and add it to the release checklist.
3. M1: revoke invites on removal, or refuse removed accounts at join; fix the dialog's text.
4. M2: change the web page's text now; decide on session revocation.
5. M3: add the user-content notice, restrict `qrPayload` to payment formats, and set `ABUSE_EMAIL`.
6. L3, L4, L5: small CI and repository changes.
7. L1, L2: correct the App Links pairing and the deploy instructions; sample traces and update the policy.

### Plan follow-ups
- In the phase 9 plan, "Rà soát bảo mật và sửa phát hiện mức cao" is half done. The threat model is written; H1 is the open High. I left the plan unedited.
- The plan's "Quét phụ thuộc và quét secret trong CI" is met for npm, the actions and secrets; L4 is the gap for Gradle.
- The plan's release checks (no key, not debuggable, no plain HTTP, https proxy) are met by `scripts/release-checks.py` in `.github/workflows/ci.yml` and `.github/workflows/release.yml`.

### Metrics
- Tests: proxy 444 Vitest tests and the Node tests pass. The web tests and the Gradle builds and tests were not run in this review.
- Type coverage, test coverage: not measured; no coverage tooling is configured.
- Linting: not run (no JS/TS linter is configured in `proxy/` or `web/`; ktlint needs Gradle).
- Secret scan of history: 0 matches over 361 commits for the seven key patterns.

### Unresolved Questions
- H1 (b) changes the documented rule that a call without an answer is given back. Should a hang-up count as a call? That is the owner's decision.
- Is the Workers Free plan really the launch plan (M4), or is Paid already intended? The docs only mention Paid for CPU time.
- `docs/release.md:239-240` says the token's "Workers Scripts > Edit" "may deploy an existing Worker but not create or delete one", and that it can be scoped to one Worker. I could not confirm either from Cloudflare's docs in this review.
- RevenueCat's SDK may keep its own copy of the app user id on the phone, outside `AccountStore`'s encryption. Not checked: it would need the SDK's sources or a device.
- The merged manifest's library components were not inspected (no Gradle build).
