---
title: "Phase 9: make Split Vibe ready for Google Play"
date: 2026-11-19
summary: "Deletion choice, consent before the first AI call, strings in resources, legal pages, counters, 12-month retention, release CI and device tests; three High findings fixed; Worker deployed twice; owner steps listed"
---

# Phase 9: make Split Vibe ready for Google Play

## What happened

Phase 9 prepared Split Vibe 1.0 for Google Play. The owner decided:
- the account deletion dialog asks whether to delete the phone's bills, people and payment methods too, unticked by default, and only warns about Pro;
- usage is counted on the server with Workers Analytics Engine and no SDK;
- data is kept for 12 months;
- payment request links stop working for strangers after 90 days;
- a simple consent screen comes before the first AI call;
- 1.0 is released in every country at once, with 5, 20, 50 and 100% steps from the first update.

Agents built four parts in parallel, each followed by an independent review and a round of fixes:
- **proxy and web:** account deletion on the web with Google, the privacy and terms pages built from `docs/`, the counters with `npm run metrics`, 12-month retention, request link expiry, an install pool inside the global spend cap, and a bill-read evaluation of 35 bills;
- **app:** the deletion choice, a deletion that finishes after Settings is left or the app is killed, every string in resources with plurals and two pseudolocales, the consent screen, and RevenueCat started only for a session the proxy accepted;
- **release CI:** `release.yml` in two approved stages with Workload Identity Federation, release checks, a Worker settings check, secret scanning on every push, and dependency and vulnerability scanning;
- **device tests:** journeys against a fake proxy, blocking on API 34 and measured on API 26, 30 and 36.

The reviews found 3 High findings, all fixed:
- an anonymous install could fill the global spend cap and stop AI for everyone for a day;
- the release did not wait for the API 34 journeys;
- every release would have reset the Worker's production settings.

The Worker was deployed twice, both times with a D1 Time Travel bookmark taken first:
- the first deploy added the METRICS binding and the immediate Durable Object update;
- the second deploy serves the privacy page with the consent section.

## Problems found and their causes

- **API 26 to 30 crash.** The device tests found `BigInteger.longValueExact`, an Android API 31 call, in `:core:domain`. That module is plain Kotlin, so lint's NewApi check never reaches it, and `checkDependencies` does not change that under AGP 9.1. Only the device journeys on API 26 and 30 guard it.
- **The new declined-AI journey failed on its first emulator run.** The agent had no emulator. Alone on the bill, the user's share row and the "Paid by" row both read "You $90.00". Splitting the bill three ways fixed it.
- **CI builds failed with "Dependency verification failed".** The failure was on the Kotlin serialization plugin marker POM. Maven Central answers 429 to GitHub runners, and Gradle then turns to the Gradle Plugin Portal, which publishes its own marker for the Kotlin plugins with different bytes. The plugin's jar and module there are the same files as on Central. Compose's marker already had the portal's checksum as `also-trust`, and serialization's now has it too. A long rate limit still fails a run with a plain 429, and a rerun passes.
- **The deletion dialog's text was wrong.** It said the server deletes the count of calls, but the month's count stays until the month ends. The dialog now says what stays and how groups and requests change.

## Decision

- After 90 days a payment request link opens only for its creditor and the debtor who took it on.
- 12 months is 365 days.
- Every number in the English texts uses English formatting, money and dates included. On a Vietnamese phone, "250.993 ₫" becomes "₫250,993". The owner should confirm this.

## Next steps

These are the owner's:
- the trademark search;
- Play Console and the closed test with 12 testers for 14 days;
- the production values of the proxy and its secrets;
- legal review of the drafts;
- Workers Paid;
- the reference phone for the performance budget;
- an Anthropic key for the two evaluations;
- the AI budget per active user;
- material3 1.5.0 stable;
- the drills;
- the staged rollout.

The order is in `docs/release-checklist.md`.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
