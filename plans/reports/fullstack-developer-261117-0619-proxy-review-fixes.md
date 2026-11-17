# Fixes from the review of the proxy side of Pro: report

Date: 2026-11-17 (UTC). Branch: `feat/phase-08-pro-subscription`. Scope: `proxy/src`, `proxy/test`
and `docs/proxy.md`, following the decisions on
[the review](code-reviewer-261117-0558-phase-8-proxy-review.md). Nothing was deployed.

## Outcome

Every decision is applied. `npm run typecheck` and `npm test` are both clean: 22 files and 404
tests (395 before, 9 new), and the Node suite passes 10 of 10. Eight deliberate faults, applied one
at a time, were each caught by the new tests (listed below).

## Commits (pushed to origin, `6e70943..9ca677e`)

| Commit | What |
|---|---|
| `6f29764` | fix(proxy): sign in and report usage while a QuotaBook runs the code from before Pro |
| `c376dc9` | fix(proxy): limit account deletions, and keep the counts that bound RevenueCat through them |
| `0aacfde` | fix(proxy): keep Pro past an ended period only for a Google Play subscription that renews |
| `171003a` | test(proxy): check RevenueCat's answer size, a refused key's log, and the webhook's check before its body |
| `9ca677e` | docs(proxy): describe the first deploy of Pro, the limit on deletions, and the webhook's edges |

Before each commit, I checked its tree on its own with `npx tsc` and the Worker suite
(`npx vitest run`). All passed: 398, 400, 401 and 404 tests for the first four commits.

## Findings

### M1: new QuotaBook methods during a deploy

- **Done.** `sessionRoute` and `usageRoute` now go through `callsAndPurchaser` (`proxy/src/account.ts`).
  - When `issuePurchaser` throws, the route falls back to `usage()` and leaves `purchaser` out of
    that answer.
  - It logs only `{"event":"purchaser_not_recorded"}`.
  - So an id never goes out without its record.
- **`POST /v1/plan` is unchanged.** A failing `issuePurchaser` there still ends as 500 `internal`,
  before RevenueCat is asked. A comment at the call says why.
- **Docs.** `docs/proxy.md` has a new "The first deploy of Pro" note under "Deploy it", after the
  weighted-calls notes. It says:
  - a `QuotaBook` may keep the code from before Pro for up to about five minutes;
  - in that window, session and usage answer without `purchaser`, and with a `usage` without `plan`;
  - `POST /v1/plan` and the webhook may answer 500, which RevenueCat retries, and the app treats a
    failed refresh as non-fatal;
  - deploy it as one plain deploy, not a gradual one.

  The purchaser bullet, the line after the Pro section, and the log list now say the same.
- **The flag.** The note names `--durable-objects-code-update-mode immediate`, by reference to the
  command already given above it.
  - Cloudflare's page confirms the flag for `wrangler deploy`, with the values `immediate` or
    `deferred <duration>`.
  - The project's Wrangler 4.162.0 lists it in `wrangler deploy --help`.
- **Tests** (`proxy/test/account.test.ts`, describe "while an account's QuotaBook still runs the
  code from before Pro"):
  - sign-in answers `{session, expiresAt, usage}` with no `purchaser`, records nothing and logs only
    the event name; the next `GET /v1/usage` on the current code then records the purchaser and
    hands it out;
  - `GET /v1/usage` answers the real calls left, `monthly(1)` after one bill read, without a
    purchaser;
  - `POST /v1/plan` answers 500 and asks RevenueCat nothing.
- **How the tests fake an old object.** `booksFromBeforePro()` wraps the real `QUOTA_BOOK` binding
  in a JS `Proxy`. Its stubs reject `issuePurchaser` with the `TypeError` that workerd raises for a
  method an object does not have. The binding reaches the routes through the `settings` argument
  that `send()` already merges over `env`. The production code has no hook for this.
- **Correction to the implementer's report.** fullstack-developer-261117-0517-proxy-pro-plan.md
  (lines 47-48) says "a Worker and a QuotaBook running different versions during a deploy still
  work together". That was true only for `hold`, `commit`, `release`, `usage` and `forget`.
  - A QuotaBook still on the code from before Pro has no `issuePurchaser`, `purchaserIssued` or
    `storePlan`. So until this fix, sign-in and usage failed with 500 in that window.
  - Now sign-in and usage work in the window, without `purchaser`. `POST /v1/plan` and the webhook
    may still answer 500 there.
  - An old Worker with a new QuotaBook works.

### L1: unlimited RevenueCat traffic through repeated deletion

- **The limit.** `DELETE /v1/account` now counts the new hourly kind `account_delete` against
  `POLICY.accountDeletionsPerHour` (5, with a comment).
  - It counts after the session check and before the RevenueCat call.
  - Past the limit it answers 429 `rate_limited` with `Retry-After`, and nothing is deleted.
- **The kept windows.** `forgetSharedData` now deletes every hourly window of the account but
  `plan_refresh` and `account_delete`.
  - The kinds are the constant `KEPT_WINDOWS`, passed through `json_each`.
  - The daily run's existing delete forgets these windows once their hour is over.
- **Docs.** `docs/proxy.md` gives the limit, and says that these two counters outlive the deletion
  until the daily run and hold nothing of the account but its salted hash. "The daily run" and the
  routes table are adjusted to match.
- **Tests:**
  - `account.test.ts`, "is taken 5 times an hour for one account, and 429 with Retry-After past
    that, deleting nothing": five deletions with one session, then the sixth is 429. RevenueCat got
    exactly 5 DELETEs, and the QuotaBook and the group made afterwards are unchanged.
  - `plan.test.ts`, "keeps its hourly count through the account's deletion, which cannot start it
    over, until the daily run after the hour": 30 refreshes, a deletion, then 429. Both windows are
    still there, and `forgetExpired` removes them after the hour.
  - The existing shared-data deletion test now expects only `account_delete` to remain, and checks
    that a `request` window existed before the deletion.

### L2: the deletion window

Docs only. A new paragraph follows the note that sessions outlive deletion. It names both paths
that can re-create an empty customer:
- a second phone's `POST /v1/plan`;
- a webhook event whose issued check ran before `forget()`.

The narrower sentence it replaces ("Only an event that arrives in the moment…") is gone. The code
has no new state.

### L3: docs wording

The docs now say:
- the proxy takes the first 20 ids of the form of an account, and only then keeps those it handed
  out, so ids of that form that it never handed out count towards the 20;
- a QuotaBook error answers 500 `internal`, logged as `request_failed`, which RevenueCat also
  retries.

The code is unchanged. Only the comments in `subscription-webhook.ts` were made to say the same.

### L4: missing tests

- **Oversized answer.** `plan.test.ts`, "reads an answer of up to 256 KB, and is 502 for a longer
  one, keeping the plan as it was". An answer one byte over 256 KB gives 502, and the plan stays
  Pro as kept. The same answer at exactly 256 KB is read and stores the free plan, which shows that
  the 502 comes from the size alone.
- **Auth before body.** `subscription-webhook.test.ts`, "refuses a wrong value before it reads the
  body, however long the body is". A body over 64 KB with a wrong `Authorization` gives 401
  `webhook_auth`. The same body with the right value gives 413.
- **The 10-second timeout is not tested**, because the code cannot take a shorter timeout without a
  test-only hack.
  - `TIMEOUT_MS` is a module constant passed to `AbortSignal.timeout`, and shortening it for tests
    would need a test-only parameter or setting.
  - vitest cannot fake workerd's timers, and a real-time test would wait more than 10 seconds.
  - So whether that signal also bounds the body read in workerd is still not shown by a test.

### I2: promotional grants

- **Done.** The renewal-gap rule now needs the granting subscription's `store` to be `play_store`
  (`renewsOnPlay` in `revenuecat.ts`). An entitlement in force counts whatever its store.
- **Test.** `plan.test.ts`, "is Pro while a grant from RevenueCat's dashboard lasts, and free as
  soon as it has ended, as a grant never renews".
- **Fake.** In `fake-revenuecat.ts`, `promotionalCustomer(expires)` replaces `lifetimeCustomer()`,
  which becomes `promotionalCustomer(null)`.
- **Docs.** The plan rules in `docs/proxy.md` now say the same.

### I1: `>=` on `checkedAt`

Left as it is, as decided.

### RevenueCat's 401 on `POST /v1/plan`

- **Kept as 502.** The 401 stays 502 `subscription_unavailable`, and the log line already carried
  the status.
- **Test.** `plan.test.ts`, "is 502 with a key RevenueCat refuses, as is a deletion, and both log
  RevenueCat's 401, so that a wrong key shows". It logs exactly
  `{"event":"revenuecat_unavailable","status":401}` and
  `{"event":"revenuecat_delete_failed","status":401}`, without the key. The deletion goes no
  further.
- **Docs.** "Deploy it", step 5, now says what a wrong key causes and which log lines show it.

## Fault check

Each fault was applied alone and then reverted; the files were confirmed restored afterwards.

| Fault | Caught by |
|---|---|
| No fallback after a failing `issuePurchaser` | the sign-in and usage fallback tests |
| `forgetSharedData` deletes every window | the kept-window plan test, the deletion-limit test, the shared-data deletion test |
| No limit on deletion | the deletion-limit test |
| Answers read up to 512 KB | the size test |
| Body read before the auth check | the auth-before-body test |
| Renewal gap for any store | the promotional test |
| `revenuecat_unavailable` without its status | the wrong-key test, the webhook log test |
| `POST /v1/plan` going on when the purchaser cannot be recorded | the plan-500 test |

## Commands run (in `proxy/`)

| Command | Result |
|---|---|
| `npm run typecheck`, before any change | exit 0 |
| `npm test`, before any change | exit 0: 22 files and 395 tests; Node 10 of 10 |
| `npx tsc` and `npx vitest run`, on each commit's tree | exit 0 each: 398, 400, 401 and 404 tests |
| `npm run typecheck`, at the end | exit 0 |
| `npm test`, at the end | exit 0: 22 files and 404 tests; Node 10 of 10 |

## Concerns

- **The app, which I did not read** (another agent owns `app/`):
  - During the deploy window, `purchaser` can be missing from the answers of `POST /v1/session` and
    `GET /v1/usage`, so the app must treat it as optional.
  - `DELETE /v1/account` can now answer 429 `rate_limited` with `Retry-After`.
- **Contracts in plan files**, which were not mine to change: the shared contract in the phase-08
  file and the Phase 3 contract of the account routes do not yet have the 429 on deletion or the
  `play_store` condition of the renewal gap.
- **Counts after signing in again:** an account deleted and then signed into again within the hour
  inherits its plan-read and deletion counts. This is by design, and both limits are generous.

## Unresolved questions

- Should the 10-second deadline be shown to bound the body read too? Giving the read its own
  deadline, which the review offered as the other fix, would be a code change that the decisions
  did not include.

Status: DONE
