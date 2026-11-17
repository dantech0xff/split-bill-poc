---
title: "Phase 8: sell Pro through RevenueCat, with the proxy deciding who is Pro"
date: 2026-11-17
summary: "Pro at 300 calls a month through RevenueCat: plan kept in QuotaBook from RevenueCat's own answer, webhook as a signal, Pro screen and invites in the app; two reviews fixed; proxy deployed"
---

# Phase 8: sell Pro through RevenueCat, with the proxy deciding who is Pro

## What happened

Phase 8 added the Pro subscription. The owner kept RevenueCat, left the prices to set later in Play
Console, and chose no free trial. A researcher first checked the current facts: RevenueCat's SDK
11.3.0 brings Play Billing 9.1.0; its REST v1 `GET /subscribers` creates customers that do not exist;
its webhooks count only HTTP 200 as delivered; the restore setting "Keep with original App User ID"
keeps a purchase with its buyer; and Play's policy says what a paywall must disclose. The contract in
the phase file was settled from those facts before any code, and two agents built the proxy and the
app at the same time.

Built:
- the proxy keeps each account's plan in its QuotaBook, from RevenueCat's own answer, never from the
  app or from the content of a webhook; Pro gets 300 calls a month on the same counter as the 10 free
  ones; `usage.plan`, `purchaser`, `POST /v1/plan` and the webhook; account deletion removes the
  RevenueCat customer first;
- the app configures RevenueCat only as the signed-in purchaser, and has a Pro screen with Play's
  prices and renewal terms, a plan row in Settings, a Get Pro invite when the calls run out, and a
  warning that deleting the account does not cancel Pro.

Two independent reviews found 2 High, 3 Medium and 10 Low issues, all fixed. The main ones:
- Pro could be bought while the proxy could not yet check subscriptions;
- a single-payment intro offer would have read "for 0 months", because the SDK passes Google's 0 on;
- during Play's renewal gap the Pro screen trusted RevenueCat's lapsed entitlement over the proxy,
  offered the subscriber a new purchase and then blamed another account;
- a purchase not yet sent to RevenueCat would have been posted as whoever signs in next;
- in the minutes after a deploy, Durable Objects still on the old code lacked the new methods, so
  sign-in and usage would have answered 500;
- repeating account deletion could make unlimited calls to RevenueCat.

The proxy is deployed. Without RevenueCat's two secrets nobody can be Pro yet, and everything else
works as before. The container restarted three times; once it ran out of memory while a review ran
beside a bundle build. Everything on disk survived, and agents now run one Gradle job at a time.

## Decision

- An account is Pro while `proUntil` plus 24 hours lies ahead. `proUntil` is RevenueCat's expiry
  with Play's grace, or the lapsed expiry while a Play subscription still renews without a billing
  issue or refund. Every event triggers a fresh read, and an older `request_date_ms` never overwrites
  a newer one, so duplicate and out-of-order events need no ordering logic.
- Only purchaser ids the proxy handed out are read, because the read creates customers.
- Defaults the owner can change: plan switches without proration, sandbox purchases count, the v1
  key lives only in the Worker's secrets, deleting an account does not cancel the subscription,
  dev builds carry no RevenueCat key, and the advertising-ID permission is removed.

## Next steps

- Owner: the trademark search, the Play Console app and the `pro` subscription, the RevenueCat
  project, the Worker's two secrets, the webhook, the app's key, and the license-tester run through
  the whole cycle, all in docs/pro-subscription.md.
- Open: whether deleting an account should also stop the Play subscription from renewing.
- Still waiting from before: the Anthropic key and the evaluations, GOOGLE_CLIENT_ID, ABUSE_EMAIL and
  the Phase 5 items.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
