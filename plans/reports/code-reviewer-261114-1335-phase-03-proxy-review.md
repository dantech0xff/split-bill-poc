# Code review: Phase 3 AI proxy

- Branch: `feat/phase-03-cloudflare-ai-proxy`, proxy commits `93d01fe`, `b014f1b` and `830136e`
- Reviewer: independent code reviewer (read-only; ran `npm test` and `npm run typecheck`; read the installed `@anthropic-ai/sdk` 1.8.0 and jose 6.2.17 sources and the Cloudflare and Anthropic docs)
- Date: 2026-11-14
- The reviewer returned this report as text; the controller session saved it here, shortened where it repeats, and added "What was done" at the end.

## Verdict

The core is sound. Each quota and ledger decision runs atomically inside its Durable Object, the result line goes out only after the commit, a refused cap rolls the other caps back, and both token checks pin the algorithm and verify issuer, audience and expiry. It should not be deployed as it was, for two reasons the test suite could not see: per-IP limits were keyed by the full IPv6 address, and the runtime cancels the work 30 seconds after the client disconnects, so a long read after a hang-up was neither charged nor settled nor logged. Two Medium and six Low findings followed.

## Findings

| # | Severity | Where | Finding |
|---|---|---|---|
| 1 | High | `ledger.ts` `clientIp`, `bill-analysis.ts` | The IP ledger was named after the full `CF-Connecting-IP`. An IPv6 client holds a whole /64, so a script rotating addresses inside it never fills an IP ledger, and only the global cap stays, which it can exhaust for everyone each day. This removes the IP bound that the accepted trial trade-off relies on. |
| 2 | High | `respond.ts`, `bill-analysis.ts` | `waitUntil` keeps work for at most 30 seconds after the client disconnects, then cancels it. A read still running then is not charged, its hold lapses, its spend hold stays held for the whole window, and no log line is written. The hang-up test released the fake answer at once, so it passed either way. |
| 3 | Medium | `quota-book.ts` `hold`, `bill-analysis.ts` | A known request id was shared without checking that it is the same request: one charged call could carry three different bills within 24 hours. |
| 4 | Medium | `spend.ts`, `bill-analysis.ts`, `respond.ts` | Nothing between the holds and the settle ran in a `finally`. A Durable Object call that throws leaves the other holds held for up to a day, a failed commit after the answer loses the paid answer, and a failed settle after the result line made the stream send an `internal` error line after the result. |
| 5 | Low | `quota-book.ts`, `bill-analysis.ts` | "Only the run that took the hold may release it" protected the first run from a failing retry, but not a retry from a failing first run: the release deleted the hold while the retry was still running, so the last call could be taken twice. |
| 6 | Low | `spend.ts` | The hold covers one attempt, while a refusal fallback bills each attempt; a model missing from the price list was charged at $5/$25, below the Fable and Mythos list prices. |
| 7 | Low | `bill-analysis.ts` `askModel` | "Started" was read from `stream.currentMessage`, which the SDK clears when the body ends, so a stream that ended without `message_stop` refunded the whole hold although output was billed. |
| 8 | Low | `bill-analysis.ts` `failureCode` | A mid-stream `overloaded_error` mapped to `upstream` instead of `busy`, and the SDK obeys a `retry-after` of any length, so a long wait ran into the 120 second deadline and came back as `timeout`. |
| 9 | Low | `session.ts` | A failed fetch of Google's keys was answered as 401 `id_token`, the same as a forged token, and was not logged. |
| 10 | Low | `ledger.ts`, `session.ts` | Nothing checked that `HASH_SALT` is set; without it every hash was salted with "undefined". |

Test gaps the reviewer listed: a hang-up that outlasts the budget, the IP spend cap, the `timeout` code, a mid-stream overload, a request id reused for another bill, a first run failing while a re-run is in flight, a fallback block between text blocks, throwing Durable Object calls, deleting an account during a read, several holds racing for the last call, `retry_limit` on the route, and IPv6.

Checked and fine: quota atomicity, the month boundary, hold expiry and clean-up, the spend cap mechanics, cost per attempt from `usage.iterations`, the charge rule, the HS256 session, the Google ID token check, browser abuse (the custom headers force a CORS preflight the Worker never answers), input validation, privacy of the logs, SDK usage, parity with the Kotlin analyzer, the streaming body, and error responses that carry codes only.

Questions the reviewer left open: whether "charged even if the app hung up" must hold for reads that end more than about 25 seconds after a hang-up; whether the Anthropic workspace infers in the US (prices are then 1.1 times the list); which Workers plan runs the proxy; and whether a salted digest of the prompt, kept for 24 hours, fits "never stores bill text".

## What was done

All ten findings were fixed in the proxy, each with a test that a deliberately reintroduced bug made fail (ten mutation checks, all caught). `npm test` passes 81 tests and `npm run typecheck` is clean.

1. IPv6 addresses count by their /64; IPv4, also written inside IPv6, counts by itself (`ledger.ts` `ipKey`).
2. The stream's hang-up signal stops the model 25 seconds after the app hangs up, so the run gives back, settles and logs `client_gone` before the runtime's 30 second cut. The plan's charge rule now says "within 25 seconds of a hang-up". Moving the model call off the request was not done; see the open question below.
3. A request id is tied to a salted digest of the prompt, kept with the id for 24 hours; a run under the same id for another bill is refused with 400 `input`.
4. Release and settle run once in a `finally`; `holdAll` uses `allSettled` and rolls back every hold it took when one cap refuses or throws; `settleAll` never throws; the stream sends nothing after its result or error line; a failed commit still delivers the paid answer.
5. Each run holds a share of its request's call; a release takes back only that share, and the call goes back when no run holds it.
6. Unlisted models are charged at $10/$50 and logged; regional inference is charged at 110%. The hold still covers one attempt, which the plan now states.
7. "The model was reached" comes from the stream's `connect` event.
8. A mid-stream overload maps to `busy`, and a `retry-after` above 10 seconds is not slept on: the call fails at once as `busy`.
9. A failure to fetch Google's keys answers 503 `sign_in_off` and is logged; only token faults answer 401 `id_token`.
10. Every route answers 503 `not_configured` without a salt of at least 16 bytes, and bill reading does without the Anthropic key. The SDK's own logging is off.

Every test gap listed was covered, except deleting an account during a read, which the plan's risk table now accepts.

Open for the owner: whether reads that end more than 25 seconds after a hang-up must still be charged (that needs the model call moved to a Queue or a Durable Object), which Workers plan the proxy runs on, and whether the 24 hour salted digest of a request is acceptable (it is kept like the salted IP hashes).
