# Web pages for bill links, payment requests and invites

Status: done. The web app is built, unit-tested, end-to-end tested against `wrangler dev` with the
proxy's committed routes, measured with Lighthouse, committed and pushed on
`feat/phase-06-web-companion-realtime-claim`.

## What was built

`web/`: one Preact 11 + TypeScript bundle built with Vite 8, which picks the page from
`location.pathname` and reads the token from `location.hash` only.

- `/s/#<token>`: the bill (merchant, date, items with quantity and amount, charges, total) with each
  item's claimers as chips, live. People type their name and tap items (buttons with
  `aria-pressed`); an item can have several claimers. `locking` freezes the picks and says
  "Picks are closed: <owner> is settling the split." `locked` lists every participant with their
  amount; each row with a payment opens into the payment block (open at once when only one person
  has one). 404 says the link does not exist; 410 or a `gone` message says it no longer works.
- `/r/#<token>`: who asks whom, how much, what for, the status (`open`, `reported`, `confirmed`),
  the payment block (hidden once confirmed), and "Open in Split Vibe".
- `/g/#<token>`: the group's name and currency, three steps to join, and "Open in Split Vibe".
- Live connection (`src/live.ts`, `src/share-controller.ts`): connects first and calls `open` once
  the socket is open (or after 3 s if it never opens), keeps the view with the higher revision from
  any source, handles `share` and `gone`, closes while the tab is hidden and reconnects and reads
  again when shown, reconnects with backoff (1 s doubling to 30 s, jittered; reads `open` on the first
  failure and then at most once a minute), and pings every 30 s.
- Claims: one claim in flight at a time, the latest picks winning; optimistic picks put back on
  failure. Turnstile's script loads only when someone starts a first claim (types a name or taps
  an item without a key), renders explicitly with the meta site key and action `claim`, shows
  itself only when interaction is needed, and is removed once its token is used. `{key, claimId,
  name}` is kept in localStorage under `sv.claim.<SHA-256 hex>`; storage that throws, is full, or a
  context without `crypto.subtle` falls back to memory, and the page says picks last only while it
  stays open. A key the proxy no longer knows (claim removed) is dropped and the claim is made
  afresh with Turnstile. 403 `turnstile`, 409 `full`, 409 `locked` (then re-reads), 410, 429
  (`Retry-After`), 503 `not_configured`, 400, network errors each get a plain message.
- Payment block: a button for `link` only when it starts `https://` with a host or starts
  `upi://pay?` (no whitespace or control characters), opened with `target="_blank"
  rel="noopener noreferrer"`; a QR code from `qrPayload` drawn on a canvas with lean-qr 2.7.4 (MIT),
  black on white with its quiet zone; `details` as text with a Copy button (clipboard, else the text
  is selected); and whether the amount is filled in.
- "Open in Split Vibe": on Android
  `intent://<host><path>#<token>#Intent;scheme=https;package=com.dantech.splitvibe;S.browser_fallback_url=<Play, encoded>;end`;
  elsewhere a link to the Play Store page.
- Money: minor units turned into an exact decimal with whole-number arithmetic, formatted with
  `Intl.NumberFormat("en", {style: "currency", …})` at the currency's `fractionDigits`, and
  `"<code> <number>"` for a code the browser does not list in `Intl.supportedValuesOf("currency")`.
- Footer: "Split Vibe never holds or moves money." and "Report abuse" (`mailto:` with a subject naming
  the kind of page only) when the meta tag holds a plain address.
- Design: `src/app.css` uses only `tokens.css` variables (checked: 160 uses, all defined), grouped
  rows, chips, tonal and filled buttons, a hero card, light and dark. Accessibility: `lang="en"`,
  landmarks, one `h1` per page, a labelled name field that takes focus on a name problem, a polite
  live region announcing others' picks and status changes, visible focus, 48 px targets.
- Security: no `innerHTML`, `dangerouslySetInnerHTML`, `insertAdjacentHTML`, `document.write`,
  style attributes, `eval` or `console` in `src/` (a unit test reads the sources to keep it so).
  The build has no inline script or style and loads only `/assets/` files
  (`scripts/verify-build.mjs`, run by `npm run build`).

Files: `web/` only, plus `proxy/.dev.vars` (local, gitignored, verified with `git check-ignore`;
random `HASH_SALT` and `SESSION_SIGNING_KEY`, Turnstile's test keys, a local `GOOGLE_CLIENT_ID` and
`ABUSE_EMAIL`; left in place for later end-to-end runs). `web/src/tokens.css` is untouched.

Dependencies, all exact: preact 11.2.0, lean-qr 2.7.4; dev: vite 8.3.8, @preact/preset-vite 2.10.6,
typescript 5.9.3 and vitest 5.1.0 (as in `proxy/`), happy-dom 21.0.4, @playwright/test 1.63.0 (the
version of the pre-installed Chromium), @types/node 22.20.5. `web/package-lock.json` is committed.

## Commits

- `a8f742b` feat(web): show bill links, payment requests and invites in the browser
- `08c5d27` test(web): run the pages end to end against the local proxy
- `1fb1aa4` fix(web): show the person check again after picks close and reopen
- `5217766` fix(web): take the person to the name field when a pick needs a name
- `6ad389d` test(web): check the page's assets by kind, whatever their hashes
- `e368f06` fix(web): offer only payment links that start https:// or upi://pay?

## Tests

- Unit (Vitest + happy-dom, `npm test`): 142 passed in 15 files. They cover every state of the three
  pages from contract fixtures (`web/tests/fixtures/`), the link filter (`javascript:`, `http:`,
  `intent:`, `data:`, `https:` without a host or slashes, capitals, spaces, control characters),
  revision ordering from `open`, live messages and claim answers, money (VND 0, USD 2, KWD 3,
  unknown codes, negatives, 10^15), the claim flow's exact request bodies and every error with a
  fake `fetch`, the live connection's protocols, ping, backoff, visibility and online handling,
  the Turnstile loader and widget, and the storage fallback.
- Typecheck (`npm run typecheck`, both tsconfigs) and `npm run build` (with the CSP check) pass.
- End to end (`npm run e2e`, Playwright, Pixel 7 viewport, Chromium at `/opt/pw-browsers/chromium`):
  5 passed, last against the proxy's committed routes. Playwright starts `wrangler dev` on 8797
  (inspector 9339) after `npm run build:web` and migrations on a fresh local D1 under `web/.e2e/`,
  and stops it at the end. The owner's session is signed as `proxy/src/session.ts` does, from the
  local `HASH_SALT` and `SESSION_SIGNING_KEY`. Turnstile runs for real with the always-pass keys.
  - Bill link: two phones open it; picks reach the other phone in 28 to 43 ms after the first
    claim's answer, 62 to 83 ms after a later tap (key only), and 14 to 18 ms after the second
    phone's first claim, each timed per animation frame and asserted under 2 s over four runs.
    First claims took 2.3 to 2.6 s from the tap, almost all of it Turnstile (the test taps right
    after typing). Then lock (picks freeze), finalize (amounts, both QR codes drawn, the PayPal
    link with `noopener noreferrer`, the bank details), and revoke (both pages: "This link no longer
    works"). The claim bodies carry the token, a Turnstile token and no key first, then the key and
    no Turnstile token; localStorage holds one hashed key and no token; no request path or query
    holds the token.
  - `/r/`: details, payment block, the exact `intent:` link on Android and the Play Store link on
    an iPhone, 410 after the request is deleted. `/g/`: name, currency, steps, `intent:` link,
    410 after invites are turned off. `/assets/` files are served `immutable`.
  - Every page's headers match the contract (CSP for `127.0.0.1:8797`, `no-referrer`,
    `noindex, nofollow`, `nosniff`, `no-store`), and no CSP violation was reported on any page,
    Turnstile's script and frame included.

## Lighthouse and size

Lighthouse 13.6.0 run ad hoc, mobile form factor with simulated throttling, Chromium headless,
against the local proxy with a bill link:

| Page | Performance | Accessibility | FCP | LCP | TBT | CLS |
|---|---|---|---|---|---|---|
| `/s/` open, with a bill | 100 | 100 | 0.8 s | 1.2 s | 0 ms | 0.031 |
| `/s/` settled, with amounts and QR codes | 99 | 100 | 0.8 s | 1.2 s | 0 ms | 0.061 |

Targets were 90 or more for both: met. Initial download (`npm run size`): index.html 0.4 KB +
JS 21.9 KB + CSS 3.5 KB = 25.8 KB gzipped, against the 150 KB budget; Lighthouse counts 27 KiB
transferred.

## Contract points I found unclear, and what I did

- The live URL: the task gives `"wss://" + location.host`; the page uses `ws:` only when it was
  itself served over plain HTTP (`wrangler dev`), so the local tests can connect. Over HTTPS it is
  always `wss:`.
- `ping`/`pong` are taken as the plain texts `ping` and `pong`; anything that is not a JSON
  `share` or `gone` message is ignored.
- PublicShare does not say which participant a browser's claim became, so the settled page cannot
  tell the visitor "you pay X"; it lists everyone, each row opening into its own payment block.
- A confirmed request hides its payment block ("Nothing is left to pay on this request") so nobody
  pays twice; open and reported requests show it.
- The claim key's storage name hashes the token with a label (`split-vibe claim key`, NUL, token),
  so it never equals the token hash the proxy keeps.
- The contract was refined while I worked (payment links must literally start `https://`; claims
  and amounts are ordered; `quantity` may be negative). The page follows the refined text; a
  negative quantity shows as `-1 × <name>`.

## Notes for others

- CI (owned by the proxy side) runs `npm ci` and `build:web` for `web/`, so the typecheck and the
  build's CSP check run there, but not the web unit tests. Adding `npm test --prefix ../web` would
  cost about 3 s. The end-to-end tests need outbound access to Cloudflare for Turnstile and a
  generated `proxy/.dev.vars`; they could run in CI with random local secrets, as the Turnstile test
  keys are public.
- The `intent:` link names `com.dantech.splitvibe` only, so it does not open a debug build
  (`com.dantech.splitvibe.dev`).
- All processes I started (wrangler dev, workerd, Chromium, Lighthouse) are stopped; ports 8797 and
  9339 are free. Local test state and traces were deleted.

## Open questions

1. Should PublicShare map each claim to the participant it became, so the settled page can show the
   visitor their own amount first?
2. Should a confirmed request still show how it was paid, or is hiding the payment block right?
3. Should "Open in Split Vibe" also reach the debug package for testing on a debug build?
4. Should CI run the web unit tests, and the end-to-end tests with generated local secrets?
