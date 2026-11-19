# Retention, request link expiry, invites on deletion, the bill route's evaluation header, and the docs

## Phase Implementation Report

### Executed phase
- Phase: quality, compliance and release, the proxy and web part: the owner's decisions of 19 November on retention and request links, the coordinator's decisions on invites at deletion and on the bill route's evaluation header, and the `docs/proxy.md` changes the previous fix left.
- Plan: `plans/261114-0220-split-vibe-production/phase-09-quality-compliance-release.md`, not edited.
- Status: completed. All work is committed and pushed to `origin/feat/phase-09-quality-compliance-release` (head `9f11898` before this report's commit). `docs/runbook.md` is untouched; its lines are in the last section below.

### Commits
| Commit | Item | What |
|---|---|---|
| `4e2fb25` docs | 1 | The 14 changes to `docs/proxy.md`, against the file as it was, the bill-read evaluation section kept |
| `5e937db` chore(proxy) | 1 | `"ANDROID_DEV_CERT_SHA256": ""` in `wrangler.jsonc` `vars`, with its comment |
| `9289a5a` feat(proxy) | 2 | An install's QuotaBook deletes itself 12 months after its last held call; the daily run empties the payment method of requests confirmed more than 12 months ago |
| `ee8a804` feat(proxy) | 3 | `POST /v1/request-links/view` answers 410 `gone` 90 days after the request was made |
| `cbf8722` feat(web) | 3 | The `/r/` page's text for a link that no longer works |
| `52520da` fix(proxy) | 4 | Deleting an account revokes the live invites of every group it was in, in the same batch |
| `db5047f` fix(proxy) | 2 | Type fix for item 2's storage writes: `tsc` had failed since `9289a5a` (see Issues) |
| `7a51a6b` refactor(proxy) | 5 | The bill route reads the evaluation's header through the edit route's `modelOf`; `eval/read/worker.ts` and its drift tests are gone |
| `9f11898` docs | 6 | `docs/privacy-policy.md`, `docs/proxy.md`, `docs/web.md` |

## Each item

### 1. `docs/proxy.md` and `wrangler.jsonc`
**Done.** I applied all 14 changes from the previous report's section, at their places in the current file. I rewrapped long lines and made no change to the meaning.
- I checked the new claims against the code: the `spend_cap_near` and `bad_cert_fingerprint` log lines, the outcomes `session` and `internal`, and the HEAD routes.
- `wrangler.jsonc` now has `"ANDROID_DEV_CERT_SHA256": ""` next to `ANDROID_CERT_SHA256`. The vars comment says:
  - the release variable holds the upload key's and the Play app signing key's fingerprints;
  - the debug one is set only on a Worker that debug builds use, and stays empty here, so that no debug-signed app opens production links.
- `npm run bundle` lists `env.ANDROID_DEV_CERT_SHA256 ("")`.

### 2. Retention of 12 months
**Done.**

**An install's QuotaBook** (`proxy/src/quota-book.ts`):
- **The time.** Every held call of an install writes the new key `lastHeldAt` = `now`, in the same write as the hold. This covers a new call and a run again of the same request. An account's object never gets the key.
- **The alarm.** `arm()` adds `lastHeldAt + POLICY.installRetentionMs` to the times it wakes for. When it fires, `alarm()` runs `forgetEnded()`, which calls `deleteAll()` once that time has passed.
  - With the compatibility date `2026-11-01`, `deleteAll()` also deletes the alarm (Cloudflare changelog, 24 February 2026), so nothing is left.
  - No object is ever listed.
- **A missed alarm.** `begin()` runs before every method, so a call after that time first deletes the object. The call then runs as a new install's, and is counted as a first call.
- **The alarm set again.** `begin()` sets the alarm again when a time is kept but no alarm is set. That covers both a deleted account's time and an install's.
- **Objects from before.** An install's object written by older code holds a trial count or the first-call mark but no time. It takes `now` as its time when this code first calls or wakes it.
- **Which calls count.** Only held calls move the time. Commits, releases, `/v1/usage` and calls refused for the quota do not.

**Paid requests** (`forgetExpired` in `proxy/src/reminders.ts`, in the same batch as the other cleanups):
- It runs `UPDATE requests SET method = '{}', payment = NULL WHERE confirmed_paid_at < now - POLICY.paidMethodRetentionMs AND (method <> '{}' OR payment IS NOT NULL)`, so each request is written once.
- The amount, the names, the debtor and creditor, `reportedPaidAt`, `confirmedPaidAt` and the group's settlement stay.
- I emptied `payment` too: it holds the same payment details for the web page.

**`POLICY`.** `installRetentionMs` and `paidMethodRetentionMs`, each `365 * 24 * 60 * 60 * 1000`, with comments. The comments of the cron in `index.ts` and `wrangler.jsonc` mention the new cleanup.

**Tests**
- `proxy/test/quota-book.test.ts`, new group "an install that is not signed in" (4 tests):
  - "keeps the time of the last call it held, which every call held moves on and nothing else does, and an account keeps none".
  - "keeps its count for 12 months after its last call, a call before then moving the time on, and is then gone, the install starting afresh". A call a day before the 12 months keeps the count and moves the time, and the count survives past the old end. At 12 months after the new call, the storage is `{}`, there is no alarm, and the next hold is `first: true`.
  - "deletes itself by its own alarm 12 months after the last call it held".
  - "written before the time of its last call was kept, starts its 12 months when it is first called or woken". It covers a call, a release and the alarm.
- Two existing QuotaBook tests changed their expectation: an install's trial was "never over". One of them now also checks that the alarm is set again.
- `proxy/test/payment-requests.test.ts`: "loses its payment method at the daily run once it was confirmed more than 12 months ago, and keeps its amount, its people and its paid state, so no balance moves". It uses four requests:
  - a group request confirmed 12 months and a day ago;
  - a request outside a group confirmed then;
  - one confirmed a day short of 12 months ago;
  - an open one made two years ago.
  
  It checks that the group's whole state is unchanged, the settlement included, and that both people's lists differ only in `method: {}` on the two old ones. It also checks the stored rows.

### 3. Payment request links on the web end after 90 days
**Done**, for the route the `/r/` page uses.
- **Proxy.** `viewRequestLinkRoute` answers 410 `gone`, as an expired invite or bill link does, once `Date.now() >= created_at + POLICY.requestLinkMs` (90 days).
  - The check comes before the hourly count, so an expired link counts no read of the address.
  - `/v1/requests…`, `/v1/request-links/open` and `/accept` are unchanged; see the first question below.
- **Web.** The page's text for 410 on a request now reads: "The person who asked withdrew this request, or the link's 90 days are over. If you kept it in your requests in Split Vibe, it is still there." "Keep it in my requests for later" is the app's own button. The heading stays "This link no longer works".
- **Tests**
  - `proxy/test/payment-requests.test.ts`: "works for 90 days after the request was made, then answers as an expired link, counting no read, while the request stays in both people's apps". It checks:
    - at 89 days the page shows the request;
    - at 91 days it answers 410 `gone` and adds no web read;
    - both people's lists, `open` for the debtor, report paid and confirm still work.
  - `web/tests/request-invite-pages.test.tsx`: the new text, and no "Try again" button.
  - `web/e2e/request-and-invite.spec.ts`: after a withdrawal, the page shows the new sentence.

### 4. Deleting an account revokes its groups' invites
**Done.** `forgetSharedData` (`proxy/src/account-data.ts`) has one more statement in its batch:
- `UPDATE invites SET revoked_at = ?2 WHERE revoked_at IS NULL AND group_id IN (SELECT group_id FROM members WHERE account = ?1)`.
- It runs before the members lose their account, so it covers every group the account was a member of, owned or not.
- A claim waiting in a group is not membership and revokes nothing, as with leaving.

Test in `proxy/test/account.test.ts`, a matrix row run through both deletion routes: "invites of the groups it was in: are revoked in the same batch, so that none made before brings it back once it signs in again, and a new one works".
- It covers the account's own invite, the owner's invite, and an invite made by the heir of a group the account owned: each answers 410 to join and to open.
- An invite of a group the account was not in still works, and new invites work.
- With the statement replaced by a no-op, the test fails through both routes.

### 5. The bill route reads the evaluation's model header
**Done.**
- **Shared code.** `EVAL_MODEL_HEADER` and `modelOf(request, own)` moved from `split-revision.ts` to `model-call.ts`. The rule is unchanged: the header counts only at `localhost`, `127.0.0.1` or `[::1]`, and only for a model with a price in `spend.ts`; any other model is 400 `input`. The answer then reports `eval` with the model, the tokens and the cost.
- **Routes.** Both routes now use it: `BILL_READ` takes `modelOf(request, POLICY.model)`, and the edit route `modelOf(request, POLICY.revisionModel)`.
- **Runner.** `eval/read/run.mjs` starts `wrangler dev` on the normal Worker. `startLocalProxy()` in `eval/shared/local-proxy.mjs` lost its unused entry argument. `npm run eval:read` itself was already `node eval/read/run.mjs`, so `package.json` is unchanged.
- **Removed.** `proxy/eval/read/worker.ts`, and from `test/eval-read.test.ts` the group "a bill read that names its model on a computer" (5 tests). That group held the drift test and the tests of that Worker's dispatch.
- **Kept.** "each pass through the proxy on a computer when the model answers what they expect" now sends all 35 bills through the normal Worker.
- **New tests** in `proxy/test/bill-analysis.test.ts`, group "the model the evaluation names", mirroring the edit route's tests (4 tests):
  - the named model is asked with the route's prompt, schema and settings, the refusal fallback included, and the last line reports `eval`;
  - an answer the app cannot use is charged and reports `eval`;
  - "is ignored by the deployed proxy, which always asks its own model and reports nothing, whatever the header names": a priced and an unpriced name at the deployed host both get `POLICY.model` and no `eval`;
  - an unpriced model at a local host is 400 `input`, and the model is not called.
- **Docs.** The bill-read evaluation section of `docs/proxy.md` is updated in the same commit.

### 6. Docs
**Done.**
- **`docs/privacy-policy.md`**
  - The install's count and the time of its last AI call are kept until 12 months after that call, and each call moves the time on.
  - A confirmed request loses its way to get paid and its web payment details 12 months after confirmation, keeping its amount, names and paid state.
  - A request's web page works for 90 days. This is stated in "Payment requests", "People who open a link without the app", the retention list, and the deletion section's confirmed requests.
  - The file is plain Markdown with only its leading comment. The build's renderer, `renderLegalMarkdown`, renders it with every new passage and no comment, and `npm run build` in `web/` writes `privacy.html`.
- **`docs/proxy.md`**: calls and limits, what it keeps (Durable Objects and D1), the `install_first_call` row, groups and members, payment requests, the web routes, the daily run, deleting an account, tests, and a deploy note. It names `installRetentionMs`, `paidMethodRetentionMs` and `requestLinkMs`.
- **`docs/web.md`**: the `/r/` bullet.

## Tests Status
| Command | Result |
|---|---|
| `proxy`: `npm run typecheck` | exit 0 |
| `proxy`: `npm test` | exit 0. Vitest 24 files, 507 tests pass: 500 before, +4 QuotaBook, +2 payment requests, +2 account (one row, two routes), +4 bill route, −5 evaluation-Worker tests. Node 27 of 27 pass |
| `proxy`: `npm run bundle -- <scratch dir>` | exit 0. Total Upload 1475.49 KiB, gzip 295.51 KiB; `env.METRICS (split_vibe_events)` and `env.ANDROID_DEV_CERT_SHA256 ("")` listed |
| `web`: `npm test` | exit 0. 20 files, 235 tests pass |
| `web`: `npm run build` | pass: "The build fits the pages' CSPs: 4 pages and 2 files under /assets/" |
| `web`: `npm run e2e` | exit 0. 8 of 8 pass. Its `wrangler dev` stopped; ports 8797 and 9339 are free, and no wrangler or workerd process is left |

No linter is configured in `proxy/` or `web/`.

## Issues Encountered
- **Intermediate commits did not type-check.** After item 1 I ran Vitest only, which strips types, so `9289a5a`, `ee8a804`, `cbf8722` and `52520da` fail `tsc`. The two spread writes in `QuotaBook.hold` were mistyped. `db5047f` fixes them, and every check passes from there on.
- **Request-link scope.** I read "the request-link routes it uses" literally: the `/r/` page calls only `/v1/request-links/view`. As a result, after 90 days:
  - a signed-in account that has the link can still `open` it and see the payment method in the app;
  - such an account can still `accept` it if nobody has, as before.
  
  The docs say so. See the first question.
- **12 months are counted as 365 days.** Across a 29 February, this ends one day before 12 calendar months, so nothing is kept longer than the policy says.
- **The daily cleanup reads the whole `requests` table once a day.** No index covers `confirmed_paid_at`. A partial index would need a new migration, which is not among my files.
- **Old install objects.** An install's QuotaBook from before this version, with no time, is deleted only if something calls or wakes it after the deploy. No production proxy with real installs exists yet, as far as the plan says. The deploy note covers this.
- **The QuotaBook now keeps a time for installs.** The earlier change kept no time for the first-call mark. This time is the one the deletion needs, and the privacy policy states it.
- **A wrong claim I left as it was.** `docs/proxy.md`'s "Objects are named by salted hashes" does not hold for installs: their QuotaBook is named `install:<install id>`. It was there before and is outside these items.

## Next Steps
- The coordinator puts the runbook lines below into `docs/runbook.md`.
- The owner can now run `npm run eval:read` against the normal Worker, as before, with the key in `proxy/.dev.vars`.

## Runbook lines for the coordinator
For `docs/runbook.md`, in its own format. Paths are from the repository root.

**Alert: `spend_cap_near` (a spend cap reached 80%)**
- **Where it shows.**
  - Worker logs: `{"event":"spend_cap_near","cap":"<cap>"}`.
  - Analytics Engine: event `spend_cap_near`, with the cap as its outcome. `npm run metrics` in `proxy/` prints it per day.
- **Meaning.** It fires on the first hold in a cap's 24-hour window that takes the cap to 80% (`POLICY.spendWarnPercent`), or that the cap refuses. It fires once per cap and window. The cap is one of:
  - `caller`: $3 for one account or install;
  - `address`: $6 for one hashed IP;
  - `install`: the $5 pool that installs not signed in share, inside the $20;
  - `global`: $20 for the whole proxy.
- **`global`**
  - Compare today's `bill_analysis` and `split_revision` cost in `npm run metrics` with the days before.
  - For real growth, such as Pro accounts: raise `globalCapMicro` in `proxy/src/policy.ts`, deploy, and check the monthly limit in the Anthropic console.
  - For a spike that sign-ins do not explain: treat it as abuse. If spend must stop now, set `AI_ENABLED` to anything but `"true"`; the app falls back to entering bills by hand.
- **`install`**
  - Compare `install_first_call` with the days before; a jump means a script making new installs.
  - Nothing is needed to protect accounts. Once the pool is spent, installs get 429 `busy` and accounts keep $15.
  - Lower `installCapMicro` if it repeats. Raise it, below `globalCapMicro`, only for real use.
- **`address` or `caller`**: the cap holds by itself. Act only when the same cap fires every day.

**Symptom: AI is "busy" for people who are not signed in, while signed-in people read bills**
- **Cause.** The installs' $5 pool of the current window is spent. Their calls get 429 `busy` until the window ends, at most 24 hours after it started.
- **Signs.** A `spend_cap_near` with cap `install` came earlier in the window. The `bill_analysis` and `split_revision` lines show outcome `busy` with `"caller":"install"`.
- **What to do.**
  - Tell people that signing in with Google gives them AI again at once, 10 calls a month.
  - If the use is real, raise `installCapMicro` and deploy; a deploy does not reset the window.
  - If it is a script, leave it: the pool is what keeps accounts working.

**Retention runs by itself**
- An install that is not signed in is forgotten 12 months (365 days) after its last AI call, by its own QuotaBook's alarm. Someone who comes back after that gets 3 free calls again; this is expected.
- The daily run empties the payment method, and the web page's payment details, of each request confirmed more than 365 days ago.
  - When `daily_run` is `failed`, this waits for the next run that works: see which part failed among the cron's events in the Cloudflare dashboard.
  - The next good run catches up, as each run empties everything past the 12 months.

**Support: "my payment request link says it no longer works"**
- The request was withdrawn, or it was made more than 90 days ago (`requestLinkMs`): the web page stops showing it then.
- The request is still in the app for the creditor, and for the payer if they kept it.
- For a new web link, the creditor makes a new request.

**Support: "an old group invite no longer works"**
- Removing a member, leaving and deleting an account each revoke every working invite of the group, or of every group the account was in.
- A member who stays makes a new invite.

**Deploy note for this version**
- Deploy with `--durable-objects-code-update-mode immediate`, as for the earlier versions.
- An install's QuotaBook written by older code starts its 12 months the first time this version calls or wakes it.

## Unresolved Questions
- After 90 days, should `/v1/request-links/open` and `/accept` also refuse an account that is not yet the request's debtor? This would cover the payment method a signed-in stranger with the link can still see in the app. The creditor and a bound debtor would keep the request in the app either way.
- Is 365 days acceptable as "12 months" in both rules?
- Should a migration add a partial index on `requests (confirmed_paid_at)` for rows that still carry a method, so that the daily cleanup does not read the whole table?

Status: DONE_WITH_CONCERNS
Summary: All six items are implemented, tested, documented, committed and pushed, and every proxy and web check, the bundle and the e2e suite pass on the final tree. The runbook lines are above for the coordinator.
Concerns/Blockers: Four intermediate commits (`9289a5a` to `52520da`) fail `tsc` until `db5047f` fixes it. The 90-day expiry covers the web page's route only, not the app's `open` and `accept`.

## Follow-up

The coordinator settled three of the questions above: 365 days stands for 12 months, no index migration, and the intermediate commits that fail `tsc` stay as they are. It then asked for the four changes below. I ran `npm run typecheck` in `proxy/` before each of these commits.

### Commits
| Commit | What |
|---|---|
| `60bc958` feat(proxy) | The request link also ends in the app after 90 days, for anyone but its creditor and its bound debtor |
| `bc0822b` docs | `docs/proxy.md` says what each Durable Object is named by |
| `d6856f4` docs | The 90-day rule for the two people, in `docs/proxy.md`, `docs/privacy-policy.md` and `docs/web.md` |
| `5abaac8` docs | The runbook lines, in `docs/runbook.md` |

### What changed
- **The app's link routes** (`proxy/src/requests.ts`). Once `POLICY.requestLinkMs` has passed since the request was made:
  - `POST /v1/request-links/open` and `/accept` answer 410 `gone` to any account that is neither the creditor nor the bound debtor (`debtor_bound_at` set and `debtor` the caller). This holds whether or not someone took the request on.
  - On that path nothing is bound and nothing is counted. `accept` binds at the same `now` it checked the time with, so no debtor is bound after the link ran out.
  - The creditor and the bound debtor keep their answers. `open` gives each their view. `accept` gives the creditor 403 `forbidden` and the bound debtor their view.
  - `view` and both app routes share one helper, `linkRanOut`. The comments on the three routes and on `requestLinkMs` state the rule.
- **Object names.** `docs/proxy.md`'s wrong sentence is replaced by a list of each Durable Object's name:
  - `install:<install id>` or `google:<salted hash>` for a caller's `QuotaBook` and spend counter;
  - `ip:<salted hash>` for an address;
  - `global` for the whole proxy's spend, and `rates` for the `FxBook`;
  - the link's id and creation time for a `ShareRoom`.
- **The rule in the docs.** The link stops working after 90 days for anyone but the person who asked and the payer who already took it on, and the request stays in those two people's apps.
  - `docs/proxy.md`: the payment request table and bullets, the web routes, and the tests.
  - `docs/privacy-policy.md`, in three places. It keeps only its leading comment; the build's renderer and `npm run build` both render it.
  - `docs/web.md`: the `/r/` bullet. The page's own text was already right.
- **The runbook.** Each line went into the runbook's own sections:
  - "Where to look": the `spend_cap_near` log line.
  - "Spend caps": an `installCapMicro` row, what installs see when their pool is spent, and a new part, "When a cap comes near its end", with a table of what each cap means and what to do.
  - "The daily run failing": the method cleanup, and its statement added to the manual clean-up command.
  - "Rotating secrets": `ANDROID_DEV_CERT_SHA256`, which stays empty on production.
  - "Reading the counters": the new event, and a daily check of it.
  - "Support": four cases. The request link case follows the new rule, and its messages are the page's and the app's own.
  - **Beyond the lines I was given.** "Account deletion requests" said that another phone's session keeps working for 30 days after a deletion. That stopped being true when deleting an account began to end every session, so the runbook now says such phones are signed out.

### Tests
- New, in `proxy/test/payment-requests.test.ts`: "stops opening and accepting 90 days after the request was made for anyone but its creditor and its bound debtor, and binds nobody then".
  - At 91 days, a stranger gets 410 `gone` from both routes, and nothing is bound.
  - At 89 days, the same account opens the request and takes it on, and another stranger gets 409 from both routes, as before.
  - At 91 days again, the creditor's `open` gives their view and `accept` 403, the bound debtor's `open` and `accept` give their view, and the other stranger gets 410 from both routes. The request is still in the debtor's list.

| Command | Result |
|---|---|
| `cd proxy && npm run typecheck` | exit 0 |
| `cd proxy && npm test` | exit 0; Vitest 24 files, 508 tests pass (one more than before); Node 27 of 27 pass |
| `cd web && npm test` | exit 0; 20 files, 235 tests pass |
| `cd web && npm run build` | exit 0; "The build fits the pages' CSPs: 4 pages and 2 files under /assets/" |

Status: DONE
Summary: The 90-day link expiry now covers the app's `open` and `accept` for anyone but the request's two people. The object names, the 90-day rule in the docs and the runbook lines are done, and every acceptance check passes.
