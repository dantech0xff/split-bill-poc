---
title: "Phase 4: bills kept, edited, scanned and split every way"
date: 2026-11-15
summary: "Phase 4 built on the phone: Room storage with revisions, editor, scanner, five split modes, payers, exchange rates via the proxy, history; an independent review found 2 high and 8 medium issues, all fixed"
---

# Phase 4: bills kept, edited, scanned and split every way

## What happened

Phase 4 turned the one-shot split into a bill that lives on the phone:

- Domain: participants with bill-local ids, item ids that are never reused, five ways to split (equally, by item, by shares, by percent in hundredths, by exact amount) for the whole bill or one item, several payers, `BillEditor`, `ExchangeRate`. Property tests run 2,000 random bills per way of splitting.
- App: a Room database that writes each bill whole as the next revision and refuses a write from an older copy; drafts saved a moment after each change and restored after the system ends the app; the bill editor; Google's document scanner with up to five pages; photos shared from other apps; the system contact picker without READ_CONTACTS; the day's rate from the proxy, kept for offline use; history with search and delete.
- Proxy: `GET /v1/fx-rates` from Frankfurter v2 with a daily cron and a five-minute pause after a failed fetch, and a prompt that reads several pages. Deployed through the Cloudflare MCP with migration v2 and the cron schedule; the live route returned 161 currencies.

## What went wrong, and what it taught

- An independent review found two high issues. Two lines of the same name with the same problem gave two identical LazyColumn keys and crashed the result screen, permanently for a saved bill. Going back from a result and typing a bill by hand reused the bill's id and silently overwrote it.
- The revision check expected the newest revision the view model had seen rather than the one the edited copy came from, so after a conflict a queued change could still win.
- Saves ran in `viewModelScope`, which is cancelled before `onCleared`, and the photos of a bill waiting to be saved could be deleted as the flow moved on.
- Saving the day's exchange rate into the bill meant that merely opening a bill rewrote it.
- `ktlintFormat` produced awkward wraps (`if (a ==\n b)`); each had to be rewritten by hand.
- Running lint and a release build in one Gradle call raced on KSP's generated sources; run them in separate invocations, as CI does.

## Decisions

- A new reading of other photos, or a bill typed by hand, is always a new bill; only reading the same photos again replaces their bill.
- A finished bill that a change leaves unbalanced is a draft again, so stored finished bills always add up.
- The day's rate is shown, not saved; a rate the user types is saved with the bill.
- People are remembered only when a split is finished, and a suggestion can be removed.
- Sync between devices, server rules and re-applying a refused edit wait for Phases 5 and 6.

## Next steps

- Scan a two-page bill on a phone with Google Play services and record the two-page fixture.
- Try sharing from the gallery and the contact picker on a device.
- The owner sets the Anthropic key on the proxy, then a live read from a `prodRelease` build.
- Merge the branch into `main` once the owner agrees.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
