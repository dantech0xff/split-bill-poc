# Proxy side of Pro: report

Date: 2026-11-17 (UTC). Branch: `feat/phase-08-pro-subscription`. Scope: `proxy/` and `docs/proxy.md`.

## Outcome

The proxy now decides who is on Pro by reading RevenueCat itself, and gives Pro accounts 300 calls a
month on the same monthly count as the free 10. All of the shared contract's proxy items are built:
the purchaser, the plan storage, the RevenueCat read, the effective plan, `usage.plan`,
`POST /v1/plan`, the webhook, RevenueCat-first account deletion, the two optional secrets, and the
policy values. `npm run typecheck` and `npm test` are both clean. Without the two secrets nobody
becomes Pro, and everything else behaves as before.

## Commits

| Commit | What |
|---|---|
| `48d0f77` | feat(proxy): give Pro accounts 300 calls a month from the plan their QuotaBook keeps |
| `8cf98b4` | feat(proxy): read each account's plan from RevenueCat |
| `3b0438a` | feat(proxy): follow RevenueCat's webhook by reading the plan again |
| `7eac7d8` | docs(proxy): describe Pro subscriptions and what RevenueCat is asked |

## Routes as built

| Route | Answer |
|---|---|
| `POST /v1/session` | `{session, expiresAt, usage, purchaser}`; records the purchaser as issued |
| `GET /v1/usage` | install: `{usage, aiEnabled}`; session: `{usage, aiEnabled, purchaser}`, which also records the purchaser as issued |
| `POST /v1/plan` | session required, and the body (empty or `{}`) is not read. Returns 200 `{purchaser, usage, aiEnabled}`. Errors: 401 `session`, 503 `not_configured` without `REVENUECAT_SECRET_KEY`, 429 `rate_limited` with `Retry-After` past 30 an hour (`HourlyKind` `plan_refresh`), 502 `subscription_unavailable` with the plan kept as it was |
| `POST /v1/subscription-events` | 503 `not_configured` without `REVENUECAT_WEBHOOK_AUTH`; 401 `webhook_auth` unless `Authorization` equals the secret, or `Bearer ` followed by it (SHA-256 digests compared with `crypto.subtle.timingSafeEqual`); 413/400 for a body over 64 KB or without an `event` object; 200 `{"ok":true}` for TEST, for no issued id, and after every refresh succeeds; 503 `subscription_unavailable` if any read fails |
| `DELETE /v1/account` | with the key, RevenueCat `DELETE /v1/subscribers/{account}` goes first, and only 200 or 404 count as done. Anything else gives 502 `subscription_unavailable` and deletes nothing else. After that come the shared data, then the QuotaBook and the ledger |

Every `usage` (in `/v1/usage`, `/v1/session`, `/v1/plan`, and the stream lines and error bodies of
bill-analysis and split-revision) ends with `plan`: `"trial"`, `"free"` or `"pro"`.

## Storage as built (QuotaBook)

- `plan`: `{proUntil: number | null, checkedAt: number}`. A lifetime entitlement is stored as
  `proUntil = Infinity`; structured clone keeps that through RPC and storage. A plan whose
  `checkedAt` is older than the stored one is ignored. One with an equal `checkedAt` is written, as
  the English contract says ("older is ignored").
- `issued`: the time, in ms, that the id was first handed out. `storePlan` writes nothing unless
  `issued` is present, so a read still in flight when the account is deleted cannot bring the
  plan back.
- The QuotaBook chooses the allowance itself in `hold`, `commit` and `usage`: Pro (`POLICY.proCalls`)
  while `now < proUntil + POLICY.proGraceMs`, otherwise the free allowance, on the same month's
  count. The RPC signatures are unchanged (it still takes the caller's allowance without Pro), so a
  Worker and a QuotaBook running different versions during a deploy still work together.
- New RPC methods: `issuePurchaser(now, allowance)`, `purchaserIssued()` and `storePlan(plan)`.
  `forget()` deletes the plan and the issued mark along with everything else. A malformed stored
  plan reads as no Pro.
- New files: `src/revenuecat.ts` (the REST client and the answer reader) and
  `src/subscription-webhook.ts`. The hourly kind `plan_refresh` needs no migration, because
  `rate_windows.kind` has no CHECK constraint.

## Tests

| File | New tests | Covers |
|---|---|---|
| `test/quota-book.test.ts` | 9 | 300 calls then refusal; Pro until `proUntil` + 24 h, free after; `used` kept across plan changes mid-month; lifetime; trial unaffected; plan kept only when issued and forgotten on delete; out-of-order writes; malformed plans |
| `test/plan.test.ts` (new) | 19 | `/v1/plan`: shape, empty body, issued mark, 401, the hourly limit, 503, 502 for 15 kinds of failure with the plan unchanged. Reading the plan: active, grace, renewal pending (Pro within 24 h, free after, and the `pro:monthly` lookup), cancelled, refunded, billing issue without grace, lifetime, no entitlement, sandbox. Allowance: an 11th bill read works on Pro and is refused on free; `plan` in edit lines and in usage for trial, free and pro |
| `test/subscription-webhook.test.ts` (new) | 16 | All the webhook cases in the contract, plus a bad body (400/413), no REST key, the 20-id cap with duplicates, and logs that carry no ids or secrets |
| `test/account.test.ts` | 4 | RevenueCat DELETE comes first (the state is captured at the moment of the DELETE); 404 is fine; 500, network error, 429 and 201 give 502 and leave the books and group untouched, and a retry then succeeds; without the key it works as before. Existing tests now also check `purchaser` in session and usage, and that installs get none |

The fake is `test/fake-revenuecat.ts`, built on MSW. It accepts only the test key and creates a
customer on a GET (201), as RevenueCat does. It can also replace or hold back an answer.
Two existing tests that delete accounts (`payment-requests.test.ts` and `shares.test.ts`) now
install the fake, and the exact `usage` assertions in other files gained `plan`.

Totals: before, 20 files and 347 tests; after, 22 files and 395 tests (48 new), all passing. The
300-call test takes about 8 seconds.

As a further check, I applied 12 deliberate faults one at a time, and the tests caught every one:
ignoring the issued mark, answering 200 on a failed read, dropping the `Bearer` form, not treating
TEST specially, ignoring `checkedAt` order, storing without the issued mark, putting RevenueCat
after the shared data in deletion, dropping the renewal gap, reading a missing `expires_date` as
lifetime, ignoring `unsubscribe_detected_at`, dropping the `:` fallback, and dropping the 24-hour
grace.

## Commands run

| Command (in `proxy/`) | Result |
|---|---|
| `npm test` before any change | exit 0: 20 files / 347 tests; Node 10/10 |
| `npm run typecheck` before any change | exit 0 |
| `npm run typecheck` at the end | exit 0 |
| `npm test` at the end | exit 0: 22 files / 395 tests; Node 10/10 |

## Decisions and differences from the contract

1. **The body of `POST /v1/plan` is not read at all.** An empty body and `{}` are both accepted,
   and nothing is ever taken from a body. This matches the proxy's other bodiless POST routes.
2. **Order of checks in the webhook:** the secret, then auth, then the body, then TEST, then the
   issued ids, then `REVENUECAT_SECRET_KEY`. TEST events and events with no issued id answer 200
   even without the REST key, as the contract says. An event about an issued account while the key
   is missing answers 503 `not_configured`, so RevenueCat retries it.
3. **Errors the contract left open:** a malformed webhook body gives 400 `input`, and one over
   64 KB gives 413 `too_large`. The 200 body is `{"ok":true}`.
4. **Order of candidate ids:** `app_user_id`, `original_app_user_id`, `transferred_from`,
   `transferred_to`, then `aliases`. Putting the transfer sides before the aliases means a long
   alias list cannot push them past the 20-id cap.
5. **Strict reading of RevenueCat's answer.** Only status 200 or 201 is read. Any other status (4xx
   included), and any answer missing `request_date_ms`, `entitlements` or `subscriptions`, or with
   a `pro` that lacks `expires_date` or carries a value that is not a time, counts as unreadable:
   502 or 503, with the stored plan kept. A partial answer therefore never downgrades a payer, and a
   missing `expires_date` is never read as lifetime. A missing `grace_period_expires_date` means no
   grace. The renewal gap requires the three flags to be explicitly `null`.
6. **Only 200 and 404 count as done for DELETE.** Any other status, 201 and 204 included, gives
   502.
7. **Not updated:** the phase file, and `docs/pro-subscription.md`, which the app agent added. The
   task limited my files to `proxy/**`, `docs/proxy.md` and this report. `docs/proxy.md` links to
   `docs/pro-subscription.md` for the owner's setup.

## Concerns

- **A short window during deletion.** Between RevenueCat's DELETE and `QuotaBook.forget()`, a
  webhook event, or a `POST /v1/plan` from a second device, could still read the customer and so
  re-create it, empty. Closing this would need a "deleting" state in the book. The contract fixes
  the order (RevenueCat first, nothing else deleted on failure), so I documented the window in
  `docs/proxy.md` instead.
- **Old sessions after deletion.** Sessions are not revoked, so a session issued before the
  deletion can mark the purchaser issued again and have RevenueCat create the customer anew. The
  docs say so.
- **Spend caps are unchanged.** The $3 a day caller cap still limits a Pro account to about 50
  bill reads a day at roughly 6 cents each. The docs and `policy.ts` say the $20 global cap must
  rise as accounts move to Pro.

## Unresolved questions

- Should a RevenueCat 401 (a wrong key) on `POST /v1/plan` answer 503 `not_configured` rather than
  502 `subscription_unavailable`? It is 502 now, and the log line carries the status.

Status: DONE
