---
title: "Phase 5: groups, balances and asking for money without holding it"
date: 2026-11-15
summary: "Phase 5 built groups on D1, balances with the fewest transfers, payment methods with prefilled links and QR codes, a share card, payment requests with push and App Links; two independent reviews found 1 critical and 7 high issues, all fixed; D1 and the Worker are deployed"
---

# Phase 5: groups, balances and asking for money without holding it

## What happened

Phase 5 turned a split into money people can ask for and track, without the app ever holding it:

- Domain: `Balances`, `GroupConversion` (the total is converted once, then shared by largest remainder, separately for shares and for what was paid), `DebtSimplifier` (greedy, at most one transfer fewer than the members, overflow-checked), `EmvQr` with CRC-16/CCITT-FALSE, seven kinds of `PaymentMethod`, and `PaymentRequestBuilder` for PayPal.Me, UPI (payer-initiated only), PromptPay, VietQR and the EPC069-12 SEPA code. Property tests run 2,000 random groups in many currencies; a four-person trip in baht matches a table worked out by hand; the QR codes match promptpay-qr, vietnam-qr-pay and the EPC guidelines' example character for character.
- App: payment methods kept on the phone (Room version 2); one request message per person with its QR code and a share card captured from Compose; groups with invites, joining, claiming a kept place with the owner's approval, removing members, balances, suggested transfers and "Got it"; a requests inbox; App Links whose token rides in the URL fragment; FCM started by hand once someone signs in; a cache of the proxy's answers emptied at every sign-in, sign-out and account deletion.
- Proxy: a D1 schema of 10 tables and 3 triggers; routes for groups, invites, requests, request links and devices; FCM HTTP v1; a daily reminder run; link pages and `assetlinks.json`; account deletion over the shared data. 185 tests.
- Deploy: a D1 Time Travel bookmark first, then the migration through the Cloudflare API, recorded in `d1_migrations` the way wrangler does it; the Worker updated with the `DB` binding, keeping its variables, secrets and cron. The live Worker answered the smoke tests as expected.

## What went wrong, and what it taught

- The review's one critical issue: a request's id did not depend on the payment method, so after the requester corrected their account, sending again kept the old account in the request the payer opened. When data routes money, any id that removes duplicates has to include the routing data. Request ids are now a SHA-256 of the content, and the open request they replace is withdrawn.
- "Got it" made a new settlement id on every tap, and the group "Ask" made a new request on every tap, so a retry could record or ask for the same debt twice. Ids are now made once per dialog, and getting money that was asked for confirms that request instead of recording a second settlement.
- `Char.isDigit` takes Thai and full-width digits. A PromptPay id passed the check and then crashed the QR builder, also on the payer's phone. Digits are now 0 to 9 only, and building a request never throws on a method from another phone.
- A whole group kept as one Room row stops opening past about 2 MB and was decoded on the main thread. The phone now keeps a bill's title and amounts only, and reads away from the main thread.
- An answer that arrived after an account change wrote the previous account's data into the cache. A generation counter, checked under a lock, now drops it.
- On the proxy: a request link made from the id alone could later open another account's request under a reused id (now an HMAC of creditor and id); link tokens in URL paths reached Cloudflare's traces (now in fragments and POST bodies); a group's state had no bound (now capped).
- `ktlintFormat` again left two `if (a ==` line breaks; they were rewritten by hand before the phase closed. Searching for lines that end in a comparison operator catches them.
- The container has no wrangler login, so the deploy went through a temporary KV namespace and an upload Worker pinned to the files' SHA-256 hashes, both deleted afterwards. D1's query endpoint runs a migration with trigger bodies as one batch.

## Decisions

- Shared data lives in D1 behind the existing proxy; Firebase only sends push.
- Payment methods stay on the phone; each request carries a snapshot of one.
- A bill in a group is updated in the group rather than asked for person by person.
- Editing a method makes a new request and withdraws the open one.
- The proxy checks only the shape and size of a method snapshot; both phones check its content.
- Kept as they are: a former member may still confirm a group request, the device takeover risk is accepted, and request links cannot be rotated.
- Caps: 40 notifications per cron run, to stay within the free plan's 50 subrequests; per group 100 members, 500 bills, 1 MB of documents and 2,000 settlements; per account 100 groups and 200 group changes an hour.

## Next steps

- Scan and open every payment code and link with real payment apps, including two Vietnamese banking apps for VietQR.
- The owner creates the Firebase project, sets `FCM_SERVICE_ACCOUNT` and the four Firebase values in `local.properties`, and sets `ANDROID_CERT_SHA256`, `GOOGLE_CLIENT_ID` and the Anthropic key.
- Owner decisions: a debtor declining a request, binding a kept place when a request link is accepted, narrowing the pasted-link allowlist, whether "Former member" places can be claimed, the Cloudflare plan, and the reminder time of 16:30 UTC, which is 23:30 in Vietnam and Thailand.
- Merge the branch into `main` once the owner agrees.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
