# Web pages

The pages in `web/` are what people open from a link without having the app, in one bundle that
picks the page from the path:

- `/s/#<token>`: a bill link. Everyone at the table opens it, types their name and taps the items
  they had; everyone sees the picks live. Once the person who made the link settles the split
  in the app, the page shows what each person pays and how. When the app matched the people who
  picked to the bill's participants, the page also lists who picked and says who each of them is
  on the bill ("On the bill as Minh"), and it marks "You" on the browser's own claim and on the
  amount of the participant that claim was matched to, so the person finds what they owe at a
  glance.
- `/r/#<token>`: a payment request, with how to pay and "Open in Split Vibe".
- `/g/#<token>`: a group invite, with how to get the app to join.

The routes they call, and the headers the Worker serves them with, are in
[proxy.md](proxy.md#bill-links-and-the-web-pages). The token is read from the
link's fragment only and travels in request bodies and in the WebSocket protocol header, never
in a URL, in storage or in a log.

Preact and TypeScript, built with Vite. Colours, type, shapes, spacing and elevation come from
`web/src/tokens.css`, which `WebTokensTest` in `core/designsystem` writes from the app's design
system: do not edit it by hand.

## Commands

Node 22 and npm, from `web/`:

```bash
npm ci
```

```bash
npm test          # unit tests, Vitest with happy-dom
npm run build     # typecheck, build into dist/, and check the build against the page's CSP
npm run size      # gzipped size of the initial download, against its 150 KB budget
```

`npm run build` writes `web/dist/index.html` and hashed scripts and styles under `web/dist/assets/`, which
the proxy's `npm run build:web` embeds into the Worker. Nothing else is served.

## End-to-end tests

`npm run e2e` runs Playwright in a phone viewport against `wrangler dev`, which it starts from
`proxy/` on port 8797 (devtools on 9339) and stops at the end. Before that it builds the web app,
embeds it with the proxy's `npm run build:web`, and gives a fresh local D1 database under
`web/.e2e/` the proxy's migrations: every local request counts as one address, so the hourly
limits of an earlier run would otherwise carry over. Turnstile runs for real with Cloudflare's
always-pass test keys, so the browser and the Worker both need to reach
`challenges.cloudflare.com`; behind an HTTPS proxy, the browser uses `HTTPS_PROXY`.

It needs `proxy/.dev.vars` (git ignores it), with `HASH_SALT` and `SESSION_SIGNING_KEY` as
[proxy.md](proxy.md#run-it-on-a-computer) describes them, and these local values:

```properties
GOOGLE_CLIENT_ID=local-e2e.apps.googleusercontent.com
TURNSTILE_SITE_KEY=1x00000000000000000000AA
TURNSTILE_SECRET=1x0000000000000000000000000000000AA
ABUSE_EMAIL=abuse@example.com
```

The tests sign the owner in with a session they make from the same `HASH_SALT` and
`SESSION_SIGNING_KEY`, as the proxy would. They use the Chromium at `/opt/pw-browsers/chromium`
when there is one, or the one `npx playwright install chromium` puts in place; set
`PW_CHROMIUM_PATH` to use another.
