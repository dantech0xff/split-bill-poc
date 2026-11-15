---
title: "Phase 6 follow-up: who each claim is on the bill, and joiners kept"
date: 2026-11-15
summary: "Owner decisions built and deployed: ZWJ/ZWNJ kept, claim matches shown on settled links, CI builds the deploy bundle; D1 0003 applied"
---

# Phase 6 follow-up: who each claim is on the bill, and joiners kept

## What happened

The owner answered the four open questions of Phase 6. All four are built and deployed:
- The zero-width joiner and non-joiner stay in names, on the proxy and in the app's copy of a bill.
  Every other invisible formatting character is still taken out. A name of nothing but joiners and
  spaces counts as empty, so it is refused where a name is needed, and the app falls back to
  "Item", "Charge" or "Someone".
- The page does not strip anything from the name a person types (unchanged, by decision).
- CI now builds the bundle a deploy uploads, with the web pages in it, in place of the plain web
  build.
- A settled link says who each claim is on the bill. The app sends `matches` with `finalize`: the
  participant each claim was matched to, or the one added for it at lock. The page lists who
  picked, says "On the bill as Minh" or "Not on the bill", and marks "You" on the browser's own
  claim and its amount.

## Decisions

- Matches go with `finalize`, when they no longer change, rather than through a new route while
  picking is open.
- Joiners at the edges of a name are kept. Names on a link are not unique anyway; the owner tells
  two "Minh" claims apart by their items.

## What went wrong, and what it taught

- The proxy agent assumed the app still stripped joiners, because it was told not to read `app/`.
  The app had already changed in parallel. Briefs for parallel agents should say what the other
  side is changing at the same time.

## Deploy

D1 Time Travel bookmark first, then `0003_share_matches.sql` in one batch with its `d1_migrations`
row. The Worker was uploaded from the bundle the CI command builds, through a hash-pinned
temporary staging Worker and KV namespace, both deleted afterwards. Smoke test: the new page, the
headers, 404 for unknown tokens and 401 without a session.

## Next steps

- The owner sets `GOOGLE_CLIENT_ID` and `ABUSE_EMAIL`, then two browsers and an Android phone try
  one link against the deployed proxy, including the "Who picked" list after settling.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
