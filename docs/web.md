# Web pages

The pages in `web/` are what people open from a link without having the app, in one bundle that
picks the page from the path:

- `/s/#<token>`: a bill link. Everyone at the table opens it, types their name and taps the items
  they had; everyone sees the picks live. Once the person who made the link settles the split
  in the app, the page shows what each person pays and how. When the app matched the people who
  picked to the bill's participants, the page also lists who picked and says who each of them is
  on the bill ("On the bill as Minh", or "Not on the bill" for a claim matched to nobody), and it
  marks "You" on the browser's own claim and on the
  amount of the participant that claim was matched to, so the person finds what they owe at a
  glance.
- `/r/#<token>`: a payment request, with how to pay and "Open in Split Vibe". It works for 90 days
  after the request was made (`requestLinkMs` in the proxy's
  [`policy.ts`](../proxy/src/policy.ts)). After that, as once the request is withdrawn, the page
  says that the link no longer works, and that a request kept in Split Vibe is still there: the
  request stays in both people's apps.
- `/g/#<token>`: a group invite, with how to get the app to join.
- `/delete-account`: deleting an account by signing in with Google, [below](#deleting-an-account-on-the-web).

How to pay, on a payment request and on each amount of a settled bill link, is what the person who
sent the link wrote: a link, a code to scan and details. So above it the page says "The person who
sent this link wrote these payment details. Split Vibe does not check them.", followed by the
"Report abuse" link while the Worker has an `ABUSE_EMAIL`.

The same Worker serves two static pages beside them, `/privacy` and `/terms`,
[below](#the-privacy-policy-and-the-terms). The app's Settings and its Pro screen link to both.

The routes they call, and the headers the Worker serves them with, are in
[proxy.md](proxy.md#bill-links-and-the-web-pages). The token is read from the
link's fragment only and travels in request bodies and in the WebSocket protocol header, never
in a URL, in storage or in a log.

Preact and TypeScript, built with Vite. Colours, type, shapes, spacing and elevation come from
`web/src/tokens.css`, which `WebTokensTest` in `core/designsystem` writes from the app's design
system: do not edit it by hand. Every page, the static ones too, uses the one stylesheet built from
it and from `web/src/app.css`.

## Deleting an account on the web

Play asks that an app with accounts lets people delete their account without the app, and the
proxy keeps no email address that a request by email could name an account by. So
`/delete-account` deletes the account of whoever signs in there with Google:

1. It says what deleting the account deletes and what stays: bills, people and payment methods
   on the phone stay on the phone, a Pro subscription goes on until it is cancelled in Google
   Play, and the calls of the month, the time of the deletion and the hourly counters stay for as
   long as [proxy.md](proxy.md#deleting-an-account) says. It says too that a phone still signed in
   to the account is signed out, since the proxy refuses every session issued before the
   deletion. It also says how to delete in the app: Settings, then Delete account.
2. It loads Google Identity Services, `https://accounts.google.com/gsi/client`, and shows its
   "Sign in with Google" button, for the client ID the Worker puts in
   `<meta name="sv-google-client-id">`, as a popup and never signing in by itself.
3. Once Google gives the page an ID token, it names the account ("You signed in as …", read from
   the token, which only the proxy checks), says once more that deleting it signs out every
   phone still signed in to it and does not cancel Pro, and asks: "Delete my account" or
   "Cancel".
4. It sends `{"idToken": …}` to `POST /v1/account/delete-with-google` and says what came of it:
   the account is deleted, apart from what stays, and phones that were still signed in are now
   signed out, with a link to cancel Pro on Google Play; or it was not, and why, with "Try
   again". When the sign-in ran out first (401 `id_token`), it asks to sign in again.

Each step takes the focus as it is shown, so that a screen reader says where the deletion stands.
While the Worker has no `GOOGLE_CLIENT_ID`, or sign-in is off, the tag is empty: the page then says
that deleting on the web is not available yet, and loads nothing from Google. The page is the web
app's script at that path, so it needs JavaScript; without it, the page says how to delete in the
app.

It is the one page that may load Google Identity Services, and its headers say so:

- `content-security-policy: default-src 'none'; script-src 'self' https://accounts.google.com/gsi/client; style-src 'self' https://accounts.google.com/gsi/style; img-src 'self' data:; connect-src 'self' https://accounts.google.com/gsi/; frame-src https://accounts.google.com/gsi/; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`,
  exactly what [Google's guide](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid)
  asks for its library, its stylesheet, the iframe of its button and its endpoints. The link
  pages' policy does not allow it, so no other page can load it.
- `referrer-policy: strict-origin-when-cross-origin`, with the same in the page's meta tag, as
  Google recommends: Google tells the page's origin from the referrer and refuses its button
  without it. A request to another origin carries the origin alone, and the page's address holds
  nothing else. The other pages send no referrer at all.

**The owner's step.** Add the page's origin, `https://<the proxy's host>`, the scheme and the host
without a path, to the *Authorized JavaScript origins* of the OAuth client of type *Web
application* whose client ID is `GOOGLE_CLIENT_ID` (Google Cloud, Google Auth Platform, Clients).
Until then Google refuses the button, and the browser's console says that the origin is not
allowed for the client ID. Keep that one origin on the production client.

**Trying the page on a computer.** Use a separate OAuth client of type *Web application*, best in
a Google Cloud project kept for testing, with `http://localhost` and `http://localhost:8787` as
its Authorized JavaScript origins. Put its client ID into `GOOGLE_CLIENT_ID` in
`proxy/.dev.vars`, which `npm run dev` in `proxy/` reads over `wrangler.jsonc`, and open
`http://localhost:8787/delete-account`. Do not add `localhost` origins to the production client:
any page served on a person's own computer could then get ID tokens for it after one click in
Google's popup, and such a token signs in to that person's account or deletes it.

## The privacy policy and the terms

`/privacy` and `/terms` are [`docs/privacy-policy.md`](privacy-policy.md) and
[`docs/terms.md`](terms.md), which `npm run build` renders into `privacy.html` and `terms.html`
with [`scripts/static-pages.mjs`](../web/scripts/static-pages.mjs), so the Worker parses no
Markdown. To change a page, change its Markdown, then build and deploy. The pages run no script and
load nothing but the stylesheet; the Worker serves them with a policy that allows nothing else.

- The Markdown is GitHub's. Every heading gets the id GitHub gives it, so a link to `#a-section`
  works on the page as on GitHub.
- A link may go to a web page, to a `mailto:` address, to a `#section`, to a path of the Worker
  such as `/delete-account`, or to the other page's Markdown: `terms.md` and `privacy-policy.md`
  become `/terms` and `/privacy`.
- A source may begin with one HTML comment, a note for whoever edits it, alone on its lines; the
  page leaves it out whole. Any other HTML comment stops the build, saying on which line: one
  later in the file, one with text after its `-->`, one in code, or one a browser would end
  elsewhere than at its first `-->` (`<!-->`, `--!>`). So a page never shows a note, nor drops
  text with one, by mistake. Any other HTML in the Markdown is shown as text.
- The build stops, saying where, at an image or at a link to a file no page serves; and when either
  Markdown file is missing, saying which, so that `npm run bundle` in `proxy/` never bundles a
  deploy without them.

## Commands

Node 22 and npm, from `web/`:

```bash
npm ci
```

```bash
npm test          # unit tests, Vitest with happy-dom
npm run build     # typecheck, build into dist/, write the other pages, and check them against their CSPs
npm run size      # gzipped size of the initial download, against its 150 KB budget
```

`npm run build` writes `web/dist/index.html` and hashed scripts and styles under `web/dist/assets/`,
then `delete-account.html`, `privacy.html` and `terms.html` beside `index.html`, which the proxy's
`npm run build:web` embeds into the Worker. Nothing else is served.

## End-to-end tests

`npm run e2e` runs Playwright in a phone viewport against `wrangler dev`, which it starts from
`proxy/` on port 8797 (devtools on 9339) and stops at the end. Before that it builds the web app,
embeds it with the proxy's `npm run build:web`, and gives a fresh local D1 database under
`web/.e2e/` the proxy's migrations: every local request counts as one address, so the hourly
limits of an earlier run would otherwise carry over. Turnstile runs for real with Cloudflare's
always-pass test keys, so the browser and the Worker both need to reach
`challenges.cloudflare.com`; behind an HTTPS proxy, the browser uses `HTTPS_PROXY`. The tests of
`/privacy`, `/terms` and `/delete-account` reach nothing outside the computer: the browser gets a
fake of Google's library where it loads the real one, and the deletion route answers from the
test, since the Worker could only check a token Google signed. They check the pages' headers, that
nothing breaks their policies, that Google is told the page's origin and nothing more, and that a
link page cannot load Google's library at all.

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
