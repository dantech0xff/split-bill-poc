---
title: "Phase 6: bill links people claim from live, in the browser"
date: 2026-11-15
summary: "Bill links, live claims on the web, two-step settle with who paid; two reviews fixed; D1 0002, ShareRoom and Turnstile deployed"
---

# Phase 6: bill links people claim from live, in the browser

## What happened

Phase 6 built bill links end to end. The proxy keeps links and claims in D1, with a `ShareRoom`
Durable Object that holds only the WebSockets. The pages at `/s/`, `/r/` and `/g/` are a Preact
app embedded into the Worker. In the app, the claim screen makes the link, follows it live, matches
claims to people, locks, and settles with how to pay and who paid.

Two independent reviews found 1 critical, 6 high, 14 medium and 17 low issues across the app, the
proxy and the pages. All were fixed. The critical one: sending the amounts saved the bill again,
the save sent a fresh copy of the bill, and that copy took the amounts off the page. The fix made
the split screen the only writer of a bill. It sends a copy only when what the page shows changes
(a fingerprint of the page and the shares), and never an older revision than one already sent.

## Decisions

- New claims per IP stay at 30 an hour, so a table on one Wi-Fi can claim. Turnstile, removing a
  claim, locking and revoking stop one address from filling a link.
- The page asks for money only when the link's maker paid the whole bill outside a group;
  otherwise `paidBy` says who to settle up with.
- Turnstile test secrets work only on localhost, so a test key in production fails loudly (503
  and a log line) instead of letting every claim through.
- The proxy strips Unicode Cf characters from names. The app's projection now does the same, so a
  name made only of them falls back to "Someone" instead of breaking every copy of the bill.

## What went wrong, and what it taught

- `withTimeoutOrNull` on the main dispatcher never fired in Robolectric until the test moved the
  main looper's clock (`idleFor`).
- A test assumed the bill's stored revision equals the page's. The bill is saved once more to
  record what the page shows, so the right check is the revision the change was saved at.
- `ktlintFormat` still produces wraps like `if (x ==\n 1\n)`. They are fixed by hand after every
  format.

## Deploy

Turnstile widget created through the API, with its secret passed straight into the Worker and never
printed. D1 Time Travel bookmark taken before `0002_shares.sql` ran in one batch with its
`d1_migrations` row. The Worker was uploaded from `npm run bundle` through a hash-pinned temporary
staging Worker and KV namespace, then both were deleted. Smoke test: headers, assets, 404 for
unknown tokens with no D1 write, and the real widget rendering under the page's CSP.

## Next steps

- The owner sets `GOOGLE_CLIENT_ID`, then two browsers and an Android phone try one link against
  the deployed proxy.
- The owner sets `ABUSE_EMAIL`, and decides on ZWJ/ZWNJ, stripping Cf on the page, and bundling in CI.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
