---
title: "Phase 7: change a split by a sentence, see what was spent, repeat bills"
date: 2026-11-17
summary: "AI split edits on Haiku 5.5 with weighted calls, voice input, categories, insights with CSV/PDF export, recurring bills; three reviews fixed; proxy deployed"
---

# Phase 7: change a split by a sentence, see what was spent, repeat bills

## What happened

Phase 7 ran as six background agents over two days: domain, proxy and two app parts in parallel,
then three independent reviews and three fix agents. A shared contract written into the phase file
before any code (categories, the `/v1/split-revision` wire format, the 13 edit ops and their rules,
weighted calls, the recurring schedule) let the parts build at once.

Built:
- a sentence, typed or said, becomes structured edits on the result screen; the domain applies or
  rejects each one, the screen shows each person's share before and after, and Undo restores;
- calls counted in hundredths on the proxy (an edit costs 0.2), decimals in the app;
- a category on every bill, an insights screen in the local currency, CSV and PDF export;
- recurring bills with WorkManager, drafts made once per period, catch-up when the app opens.

The reviews found 5 High, 9 Medium and 28 Low issues. All High and Medium ones were fixed:
- an AI answer the proxy accepted could crash the app on overflow;
- an AI item edit brought back item divisions the user had left behind;
- a rollback after deploying weighted calls would have given every caller unlimited calls;
- the proxy refused line breaks and odd names the app sends;
- the repeat sheet moved a rent on the 31st to the 30th for good;
- insights could convert only hand-typed rates, because the day's rate was never saved.

## Decisions

- The owner chose 0.2 of a call per edit, Haiku 5.5 for edits, nine categories, and running the
  real-key evaluation later.
- Draft ids became name-based UUIDs of `{templateId}_{date}`: still the same on every run, and a
  bill id the proxy takes for groups, requests and links.
- Finishing a split now saves the day's rate it shows. That is a write the user causes, so the
  Phase 4 rule that opening a bill never rewrites it still holds.
- QuotaBook writes the old whole-call field next to the new hundredths, so a rollback is safe.

## What went wrong, and what it taught

- Agents sharing one checkout broke each other's builds mid-edit. They ended up checking on exported
  trees, and one commit was left that does not compile on its own. Next time, use one worktree per
  agent.
- The contract fixed a draft id format that the proxy refuses; one agent caught it while building.
  The contract should be checked against the existing id rules before it is shared.
- I ran a test and forgot ktlint, and pushed a 141-character line.

## Next steps

- The owner sets the Anthropic key; then run `npm run eval:revise` (about 540 calls) before edits
  reach users.
- The owner sets `GOOGLE_CLIENT_ID` to try the features end to end on the deployed proxy.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
