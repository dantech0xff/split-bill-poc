# Code review: the proxy side of Pro (RevenueCat), phase 8

Date: 2026-11-17 (UTC). Scope: `git diff 9e8f32e..834537c -- proxy docs/proxy.md` (commits 48d0f77, 8cf98b4, 3b0438a and 7eac7d8, plus the report in 834537c). I did not review the app commits in that range. I changed no code, tests or docs.

## Summary

### Scope
- **Source:** `proxy/src/` revenuecat.ts, subscription-webhook.ts, quota-book.ts, account.ts, identity.ts, index.ts, policy.ts and rate-limit.ts.
- **Tests:** `proxy/test/` fake-revenuecat.ts, plan.test.ts, subscription-webhook.test.ts, quota-book.test.ts, account.test.ts, and the six existing tests that were adjusted.
- **Config and docs:** vitest.config.ts, wrangler.jsonc, docs/proxy.md.
- **Size:** 1763 lines added and 83 removed, across 21 files.
- **Compared against:** the contract ("Hợp đồng chung" in phase-08-pro-subscription.md), sections 2 and 3 of researcher-261117-0442, and the implementer's report. I also read the code around the diff: model-call.ts, account-data.ts, session.ts, input.ts, index.ts and reminders.ts.
- **Commands run (in `proxy/`):**
  - `npm test`: 22 files and 395 tests pass; the Node suite passes 10 of 10.
  - `npm run typecheck`: exit 0.
  - `git status` afterwards shows nothing changed under proxy/ or docs/.

### Overall assessment
The code follows the contract closely, and its security core holds:
- Nothing a client or an event says ever becomes a plan.
- RevenueCat is read only for ids the QuotaBook has marked as issued.
- The webhook compares its secret in constant time before it reads the body.
- Log lines carry no ids, bodies or secrets.
- The plan rules match the contract line by line.

The tests check behaviour, and none of them would pass with the behaviour broken.

The one material defect shows up only during a deploy. The Worker calls three new QuotaBook methods that an object still running the previous code does not have, so sign-in and usage fail while the deploy settles. The implementer's report says the opposite. The other findings are Low:
- account deletion can drive unlimited RevenueCat traffic;
- the deletion window is wider than the docs say;
- a few docs statements and test gaps.

## Critical
None.

## High
None.

## Medium

### M1. During a deploy, a QuotaBook still on the previous code makes sign-in, usage and `/v1/plan` answer 500

**Where.** The Worker calls methods that are new in quota-book.ts:191-212:

| Call site | Method |
|---|---|
| account.ts:29, `POST /v1/session` | `issuePurchaser` |
| account.ts:44, `GET /v1/usage` when signed in | `issuePurchaser` |
| account.ts:68, `POST /v1/plan` | `issuePurchaser` |
| subscription-webhook.ts:98 | `purchaserIssued` |
| revenuecat.ts:34 | `storePlan` |

**Failure.** During a deploy, Cloudflare can run the Worker and its Durable Objects on different code:
- **Plain `wrangler deploy`:** "an active object can keep running its current code until it hibernates or reaches this delay". The delay defaults to 300 s.
- **Gradual deployment:** each object is assigned one version, so objects keep the old code for the whole rollout.

Cloudflare's advice is to keep the Worker and its objects "forward and backward compatible for as long as any version might still be running".

When the new Worker calls `issuePurchaser` on a QuotaBook still running the old code, the call throws `TypeError: The RPC receiver does not implement the method "issuePurchaser"`. index.ts:189-191 turns that into 500 `internal`. The effects:
- With a plain deploy, users who were active at deploy time cannot sign in or read their usage for up to five minutes.
- With a gradual rollout, about (new version's share) × (old version's share) of sign-ins fail for as long as the rollout lasts. At 10 % new code that is about 9 %.

This happens whether or not the RevenueCat secrets are set. So two statements are wrong during that window:
- docs/proxy.md:170 says that without the two secrets "everything else works as before".
- The implementer's report (fullstack-developer-261117-0517-proxy-pro-plan.md:47-48) says "a Worker and a QuotaBook running different versions during a deploy still work together". That holds only for `hold`, `commit` and `usage`.

Two cases are fine:
- Webhook deliveries in the window get 500, and RevenueCat retries them.
- The other direction, an old Worker with a new QuotaBook, works.

The repo already handles this kind of version mismatch: docs/proxy.md:1186-1194 has a deploy note for the weighted-calls change. This change adds no such note.

**Fix.** Do both:
- (a) Make the routes tolerate an old object. In `sessionRoute` and `usageRoute`, when `issuePurchaser` throws, fall back to `usage()` and leave `purchaser` out of that answer; the app gets it on its next call. This is the only fix that also covers gradual deployments. `/v1/plan` already marks the id as issued before it reads RevenueCat, so nothing can be read for an unmarked id.
- (b) Add a deploy note: deploy this version with `npx wrangler deploy --durable-objects-code-update-mode immediate`, and not as a gradual deployment.

Also correct the claim in the implementer's report.

## Low

### L1. One session can drive unlimited RevenueCat calls by deleting its account in a loop, and each deletion resets the hourly limit on `/v1/plan`

**Where.**
- `DELETE /v1/account` (account.ts:83-92) has no rate limit. With the key set, it sends RevenueCat a DELETE on every call (account.ts:87).
- `forgetSharedData` deletes the account's `rate_windows` rows (account-data.ts:60). That includes the `plan_refresh` window that account.ts:63 counts.
- Sessions survive the deletion (docs/proxy.md:692).

**Failure.** Someone with one Google account repeats this loop:
1. 30 calls of `POST /v1/plan`, which make 30 GETs; the first creates the customer.
2. One `DELETE /v1/account`, which makes one DELETE and resets the window.

Looping `DELETE /v1/account` alone is enough. RevenueCat's API limits apply to the whole project, so this can make everyone's `/v1/plan` answer 502 and the webhook's reads answer 503. RevenueCat retries a webhook event 5 times over about 80 minutes, then drops it. Purchases, cancellations and refunds then go unrecorded until each user's app next succeeds at `/v1/plan`.

**Fix.**
- Keep `plan_refresh` windows out of the account-deletion delete at account-data.ts:60. The daily run at reminders.ts:105 already removes windows older than an hour, so the account id still goes within a day.
- Give `DELETE /v1/account` its own hourly limit, counted before the RevenueCat call.

### L2. The deletion window is wider than docs/proxy.md:672-674 says, and it can be closed

**What the docs say:** "Only an event that arrives in the moment between RevenueCat's deletion and the proxy's still has the customer read."

**What the code does.** The window runs from RevenueCat's DELETE (account.ts:87), through `forgetSharedData`, which is a D1 batch (account.ts:88), to `forget()` (account.ts:90). Two things can happen inside it:
- A `POST /v1/plan` from a second phone signed in to the same account reads RevenueCat and re-creates the customer. The issued mark is still there at account.ts:68-69.
- A webhook whose `purchaserIssued` check (subscription-webhook.ts:98) ran before `forget()` reads RevenueCat after the DELETE.

Either way, a deleted account is left with an empty RevenueCat customer under its pseudonymous id. The implementer's report lists this as a concern.

**Fix.** Before RevenueCat's DELETE, write a "deleting" mark in the QuotaBook:
- While the mark is set, `purchaserIssued()` answers false, and `issuePurchaser` and `storePlan` do nothing.
- If the DELETE fails, clear the mark.

On failure this still deletes nothing, as the contract asks. At a minimum, make the docs name both paths.

### L3. docs/proxy.md says slightly more than the code does

- **The 20-id cap (docs/proxy.md:157).** The docs say "each once and at most 20, that it handed out". The code takes the first 20 well-formed ids and only then filters for issued ones (subscription-webhook.ts:97-99). So ids of the right form that were never issued count against the 20 and can push an issued id out, and the event is still answered 200, so it is not retried. Real events carry 1 to 3 ids, so this is wording. Either filter first, in bounded chunks, or say "of the first 20 ids of that form".
- **Webhook logs and answers (docs/proxy.md:361 and 159-165).** The docs promise "one line per event (`subscription_event`)", and the answers list stops at 503. A throw from a QuotaBook call (subscription-webhook.ts:98 or revenuecat.ts:34) escapes the route. It is answered 500 `internal` with a `request_failed` line instead (index.ts:189-191). RevenueCat retries a 500 too, so the behaviour is fine; the docs should say so.
- **docs/proxy.md:170, "everything else works as before":** not true during the deploy window (M1).

### L4. Claims that no test checks

- **Timeout and size bound.** No test exercises the 10-second timeout or the 256 KB limit on RevenueCat's answer (revenuecat.ts:15, 17, 48 and 55), although docs/proxy.md:119-120, 146 and 665 state both. Nothing in the suite shows that the `AbortSignal.timeout` passed to `fetch` also limits reading the body in workerd. Add a test whose answer sends headers and then stalls, or give the body read its own deadline.
- **Auth before body.** No test proves that a wrong `Authorization` is refused before the body is read: a 100 KB body with a wrong header should get 401, not 413. The code checks in the right order (subscription-webhook.ts:46-48), and a test would keep it that way.

## Informational
Each of these follows the contract. Change them only if the owner wants to.

- **I1. Equal `checkedAt`.** `storePlan` also writes a plan whose `checkedAt` equals the stored one (quota-book.ts:211 uses `>=`). The Vietnamese contract says only a newer one ("mới hơn") is written. That differs only for two reads in the same millisecond, which makes no practical difference. Align the contract's wording or the operator.
- **I2. Promotional grants.** A promotional grant (store `promotional`) never renews. But if its entry under `subscriptions` carries null `unsubscribe_detected_at`, `billing_issues_detected_at` and `refunded_at`, as the fake's lifetime customer does, the renewal-gap branch (revenuecat.ts:116-117 and 136-137) reads an expired or revoked grant as still renewing. It then stays Pro for 24 h after it ends. This is what the contract's definition in parentheses says, and it is bounded. If the owner expects a revoked grant to end at once, require `store === "play_store"` for the renewal gap. I have not checked this against a live RevenueCat answer.

## Edge cases checked, and what I found

| Edge case | Finding |
|---|---|
| Worker and QuotaBook on different code during a deploy | M1 for the new methods; `hold`, `commit` and `usage` are compatible both ways |
| Old sessions after deletion mark the id issued again | as documented at docs/proxy.md:692-697; consistent with the contract |
| Webhook or `/v1/plan` reads during deletion | L2 |
| Deletion resets the hourly windows | L1 |
| RevenueCat date formats | V8 parses `…Z`, `.123Z`, `.123456Z`, `+00:00`, strings without an offset (UTC in workerd) and plain dates. Empty or non-string dates make the answer unreadable (502 or 503), never lifetime. Checked with a scratch script outside the repo |
| Null or missing `expires_date` | only an explicit null means lifetime; missing means unreadable (tested) |
| Product keyed with or without its base plan | `pro` and `pro:monthly` are both found (tested). The v1 entitlement's `product_identifier` is the subscription id without the base plan, which matches the `subscriptions` keys |
| Infinity through RPC and storage | survives both (quota-book test "never ends …"). `planStateOf` accepts ±Infinity and rejects NaN |
| The 24 h grace | boundary tested at `proUntil + proGraceMs` and one ms before |
| Answers arriving out of order | the later `checkedAt` wins (webhook test holds the first answer back) |
| Partial failure across several ids | 503; the retry re-reads every id and gives the same result |
| An event with no `app_user_id` (TRANSFER) | both sides refreshed (tested) |
| More than 20 ids | capped; see L3 for the order |
| Subrequest budget | 20 external fetches plus 40 Durable Object calls fit the Free plan's limits of 50 external and 1,000 internal subrequests |
| Six simultaneous connections | fetches beyond six wait in a queue. All their timeouts start together, so a slow RevenueCat could time out the last ones; this needs more than six ids, so it is theoretical |

## Checked and found sound

- **Webhook authorization:**
  - Both header forms are accepted.
  - The comparison hashes the header and the secret with SHA-256 and compares the digests with `crypto.subtle.timingSafeEqual`, running both comparisons every time.
  - A missing secret answers 503, and the secret is trimmed.
  - A wrong header is answered 401 before the body is read.
  - Tests cover lengths, prefixes, `bearer` in lower case and `Basic`.
- **No way to Pro except RevenueCat's answer:** `planRoute` never reads the body (tested with a forged `plan` in it). Event content is ignored; only RevenueCat's answer is stored.
- **RevenueCat customers are created only for issued ids:**
  - The webhook filters on issued ids (subscription-webhook.ts:98-99).
  - `/v1/plan` marks the id before it reads (account.ts:68).
  - `storePlan` checks the mark again (quota-book.ts:209).
  - Events about deleted accounts are not read (tested).
- **Logs:**
  - The `subscription_event` line holds only the type (checked against `[A-Z_]{1,64}`), the outcome, counts and the time taken.
  - The RevenueCat lines hold only the status or the error's name.
  - A test checks that no account id, secret, key or event id appears.
- **Limits on outbound calls:**
  - Every RevenueCat call has a 10 s timeout.
  - Answers are read up to 256 KB, with the length counted while reading.
  - The DELETE body is cancelled.
  - The webhook body is limited to 64 KB, and the URL path holds the id URL-encoded.
- **Plan rules:**
  - The active, renewal-gap and otherwise-none rules match the contract.
  - Parsing is strict, uses `Object.hasOwn`, and checks `request_date_ms` is a positive safe integer.
  - Each rule has a test that fails if the rule is removed: grace period, unsubscribed, refunded, billing issue, lifetime, missing entitlement and sandbox.
- **Atomicity in the QuotaBook:**
  - `termsAt` and `currentBook` run in the same Durable Object event, so the plan and the count are read together.
  - `storePlan` reads, compares and writes with nothing in between, since it awaits only storage behind the input gate.
  - `forget()` before `storePlan` stores nothing (tested).
  - The month's count is shared, and a change of plan keeps what was used (tested).
- **`usage.plan` everywhere:** every `usage` comes from the QuotaBook (grep: model-call.ts:120 and 175, account.ts).
- **`/v1/plan`:**
  - It needs a session.
  - It answers 503 before the hourly count is touched (tested).
  - It allows 30 calls an hour, then 429 with `Retry-After`.
  - On a failed read it answers 502 and keeps the stored plan (15 failure shapes tested).
- **Webhook answers:**
  - Success is 200 `{"ok":true}`, never 204.
  - TEST events and events with no issued id answer 200, even without the REST key.
  - Any failed read answers 503.
  - The time taken is bounded well under RevenueCat's 60 s.
- **Deletion:**
  - RevenueCat goes first, and only 200 or 404 count as done.
  - 500, a network error, 429 and 201 answer 502 and leave everything as it was (the test captures the state at the moment of the DELETE).
  - Without the key, deletion behaves as before (tested).
- **Without the two secrets:**
  - `/v1/plan` and the webhook answer 503.
  - Deletion is unchanged.
  - `/v1/session` and `/v1/usage` gain only extra fields. The app at the base commit reads its JSON field by field (ProxyApi.kt), so extra fields are ignored.
- **Schema:** `plan_refresh` needs no migration, because `rate_windows.kind` has no CHECK constraint (0001_groups.sql:188-194).
- **Tests:** I found no test that would still pass with its behaviour broken. The tests use the real Durable Objects, D1 and RPC, and an MSW fake that fails on any request it does not expect.

## The implementer's decisions

| Decision | My view |
|---|---|
| The body of `POST /v1/plan` is not read | Agree |
| Webhook check order: secret, auth, body, TEST, issued ids, REST key | Agree. TEST and events with no issued id answer 200 even without the REST key, as the contract says |
| 400 or 413 for a bad webhook body | Agree. RevenueCat retries these harmlessly |
| Order of candidate ids, transfer sides before aliases | Agree, but see L3: the cap comes before the issued filter |
| Strict reading: only 200 or 201, every field required | Agree. Payers are never downgraded by a partial answer; the cost is that a malformed answer also delays a refund, which is bounded by `proUntil` + 24 h |
| Only 200 or 404 count as done for DELETE | Agree |
| An equal `checkedAt` is written | Harmless; see I1 |
| "A Worker and a QuotaBook on different versions still work together" | Disagree; see M1 |
| Open question: should RevenueCat's 401 answer 503 rather than 502? | Keep 502 for the app. A wrong or rotated key also turns every account deletion into 502, so set an alert on `revenuecat_unavailable` and `revenuecat_delete_failed` with status 401 |

## Recommended actions
1. M1: catch a failing `issuePurchaser` in `sessionRoute` and `usageRoute`, and add the immediate-mode deploy note. Correct the report's compatibility claim.
2. L1: keep `plan_refresh` windows through account deletion, and add an hourly limit to `DELETE /v1/account`.
3. L2: add a "deleting" mark, or correct docs/proxy.md:672-674.
4. L3 and L4: correct the docs wording, and add the timeout, size-limit and auth-before-body tests.

## Metrics
- **Type safety:** `strict: true`. The changed files have no `any`, no `@ts-ignore` and no lint suppressions.
- **Test coverage:** not measured, since no coverage tool is configured. There are 48 new tests, and they check behaviour.
- **Linting:** no linter is configured for proxy/. The typecheck is clean.

## Open questions
- Does the owner deploy with gradual deployments? M1 is Medium with a plain `wrangler deploy`, and High with a gradual rollout.
- Does the RevenueCat project hold any app besides the Play app, such as RevenueCat's Test Store? Sandbox purchases count, and the reader does not check `store`, so a sandbox purchase from any store in the project would grant Pro.
- On the app side, which this review did not cover: the app never calls `logOut`. If its SDK stays configured with a deleted account's purchaser, the SDK's own customer-info read re-creates the customer at RevenueCat. Check this in the app review.

Sources:
- [Durable Objects code updates](https://developers.cloudflare.com/durable-objects/deployments/durable-objects-code-updates/)
- [Gradual deployments with Durable Objects](https://developers.cloudflare.com/workers/versions-and-deployments/gradual-deployments/with-durable-objects/)
- ["The RPC receiver does not implement the method"](https://answeroverflow.com/m/1300956539229704202)
- [Workers resource limits](https://developers.cloudflare.com/workers/writing-workers/resource-limits/)
- [Workers platform limits](https://developers.cloudflare.com/workers/platform/limits/)
- [RevenueCat community: base plan id in product id](https://community.revenuecat.com/third-party-integrations-53/android-subscription-adding-base-plan-id-to-product-id-2710)
