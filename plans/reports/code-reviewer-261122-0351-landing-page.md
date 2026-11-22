# Review: the home page at `/`

Independent review of commit `71d0136` on `feat/landing-page` (parent `49fe098`), 22 November 2026 (UTC). Read only: nothing in the repository was changed, committed, pushed or deployed.

## Scope

- 34 files, +1,102 / −88 lines, and 14 new WebP files (560 KB together).
- Read: the diff and every touched file, plus what the change affects: the router in `proxy/src/index.ts`, `web/scripts/verify-build.mjs`, `docs/privacy-policy.md`, `docs/security-threat-model.md`, `docs/release-checklist.md`, the app's manifest, `proxy/src/policy.ts`, and the app sources behind each claim on the page.
- Ran: `npm test` in `web/` and in `proxy/`; `tsc --noEmit` in both (read only); `git diff --check`.
- Probed, in a scratch folder outside the repository:
  - `pageProblems`, the embed's `checkPage` and the build's `buildProblems`, on pages written to break them;
  - the embedded `HOME_PAGE`, against a fresh `homePage()` render;
  - a fresh run of `make-images.py`'s steps, against the committed WebPs.
- Rendered the embedded page in headless Chromium: light and dark themes, 320 to 1,920 px wide. Every request was answered from `proxy/src/web-assets.generated.ts`, with the page's CSP attached. No server was started.
- Viewed all 14 screenshots.
- Not run: Gradle, the Playwright end-to-end suite, `npm run build` (it writes `web/dist`), Wrangler.

## Overall assessment

- The Worker side is correct:
  - `/` matches only `/`;
  - other methods get 405, and HEAD mirrors GET;
  - the policy is the legal pages' policy;
  - the WebP files are embedded and served byte for byte as `image/webp`.
- The page and images match their sources. The embedded page equals a fresh render of the committed source. The WebPs are byte-identical to a fresh run of `make-images.py` on today's reference images.
- In a browser the page:
  - triggers no CSP violation;
  - picks the dark files in a dark theme;
  - loads every lazy image on scroll;
  - never overflows sideways at any width tried.
- The weak spot is the page checks. They read HTML with regular expressions, and ordinary HTML syntax gets past them. The CSP blocks everything that matters, so this is a second line of defence that does not hold as documented, not an open hole.
- The copy follows the store listing closely, but two statements are not accurate: the privacy claim and the AI allowance.
- Several docs were not updated, and one updated claim is wrong.

## Findings, most severe first

No Critical or High findings.

**M1. The page checks accept pages that load, embed or run what they claim to refuse.**

Verified by running `pageProblems(html, "home")` and `checkPage(...)` on test pages.

Where the checks are blind:

- `web/scripts/page-checks.mjs:156-169` (`imageProblems`) only sees `src` or `srcset` that comes after whitespace (`/\ssrc(?:set)?\s*=/`, `/\s(src|srcset)="…"/`).
  - HTML also ends an attribute name at `/` and right after a quoted value.
  - So `<img/src=…>` and `alt="x"src=…` are invisible to it.
  - The same blind spot is at `:122` (`style`), `:123` (event handlers) and `:130` (legal pages). Those three predate this commit.
- `:140` reads a quoted value that starts with whitespace as empty, but browsers strip that whitespace. It also looks only at `src` and `href`. So `poster`, `data`, `imagesrcset`, `http-equiv`, and a `<base>` with a padded URL all get through.
- `:112` only counts a script that has a closing `</script>`. So `<svg><script href="…"/></svg>` is not counted as a script.
- `proxy/scripts/embed-web.mjs:111-118` only checks references that end in `.js`, `.css` or `.webp`.

Each line below passed both the build check and the embed (the self-closing script passes the embed when its file is in `assets/`). The right column is what the page's CSP then does:

| Markup | Under `STATIC_PAGE_POLICY` |
|---|---|
| `<img/src="/v1/usage">`, `<img alt="x"src="/assets/logo.png">` | loads: same origin |
| `<img alt="x"src=" data:image/svg+xml,…">` | shows: `img-src … data:` |
| `alt="x"srcset="https://tracker.example/p.png 2x"`, `<video poster="https://…">`, `<link rel="preload" as="image" href="/assets/….css" imagesrcset="https://…">`, `<object data="https://…">` | blocked |
| `<iframe/src="/privacy">`, `<embed/src="/assets/a.webp">` | blocked: `default-src 'none'` |
| `<img/src="/assets/a.webp"/onerror="…">`, `alt="x"onload="…"`, `<svg><script href="/assets/x.js"/></svg>`, `<area href=" javascript:…">` | blocked: no `script-src` |
| `<p/style="…">` | blocked: `style-src 'self'` |
| `<base href=" https://evil.example/">` | blocked: `base-uri 'none'` |
| `<meta http-equiv="refresh" content="0;url=https://evil.example/">` | navigates away: no CSP directive covers it |

- **Answer to the brief.**
  - Under the CSP, nothing the checks accept can run script or load from another origin. `proxy/test/web.test.ts:158-160` asserts the CSP header exactly.
  - But the checks do accept three things the docs say they refuse: same-origin images that are not the screenshots, `data:` images, and a meta refresh to any site.
  - The guarantee written in `docs/web.md:176-179`, `docs/proxy.md:1171-1178` and the threat model is stronger than the code.
- **Failure scenario.** A later edit of `homePage()`, by hand or by an agent, writes one of these: `alt="${alt}"src=…`, a `<video poster>`, a tracking pixel, or a meta refresh. `npm run build` and the embed both pass it.
- **Fix.** For `legal` and `home` pages, replace the regex scan with a strict allow-list:
  - Every start tag must match `^<([a-z][a-z0-9-]*)((?:\s+[a-z][a-z0-9:-]*(?:="[^"<>]*")?)*)\s*/?>$`.
  - Its element must be one the page kind uses. For the home page: `html, head, meta, link, title, body, div, header, main, section, footer, nav, p, h1, h2, ul, li, dl, dt, dd, span, a, picture, source, img, svg, path`. The legal pages need what the Markdown renderer writes.
  - Each attribute must be one the pages use, for example `class, id, lang, charset, name, content, rel, href, crossorigin, src, srcset, media, width, height, alt, loading, decoding, role, aria-*, focusable, viewbox, d`.
  - URL values may contain no whitespace.
  - Add the table's rows as refusal tests.
  - Optional runtime belt: build the home page's policy per host, as `linkPagePolicy` does, with `img-src https://<host>/assets/ data:`.

**M2. "Private by design" claims more than the app does.**

Verified against the privacy policy.

- `web/scripts/static-pages.mjs:293` says: "Your bills, the people you split with and your ways to get paid stay on your phone." It is copied from `docs/store-listing.md:63`.
- The privacy policy (`docs/privacy-policy.md:14`, and its sections at `:80`, `:93` and `:99`) says the server keeps:
  - bills added to a group;
  - payment requests, each with the way to get paid chosen for it, such as a bank account number;
  - bills opened for picking with a link.
- The page itself offers bill links (`:291`) and groups a few lines earlier.
- **Failure scenario.** A visitor trusts the statement, then adds bills to a group or sends a tracked request with a bank account. That data is on the server. This is a privacy claim on a public page.
- **Fix.** Use the policy's own short form, for example: "Your bills, the people you split with and your ways to get paid are kept on your phone. Our server keeps only what you share: bills you add to a group, payment requests you send, and bills you open for picking." The listing has the same sentence, and its text is the owner's to change.

**M3. The AI allowance leaves out what an edit costs.**

Verified.

- `static-pages.mjs:294` says: "Reading a bill with AI takes one call: you get 3 to try, 10 a month…"
- The listing, `docs/store-listing.md:68`, adds "and changing a split by a sentence a fifth of one". The proxy charges 0.2 of a call per edit (`proxy/src/policy.ts:47`).
- The page reads as 3, 10 or 300 bill reads, and never says that "Change it with a sentence" uses calls.
- `docs/web.md:144-146` says the page uses the listing's text and that the two are kept together. The only test that ties them (`web/tests/static-pages.test.ts:204`) compares the meta description alone.
- **Failure scenario.** Someone who edits splits by sentence runs out of calls sooner than the page suggests.
- **Fix.** Put the clause back, for example: "Reading a bill with AI takes one call, and changing a split by a sentence a fifth of one: you get 3 calls to try, …". Add a test that the page's allowance sentence has the same numbers as the listing.

**L1. The build does not stop where `docs/web.md:176-179` says it does.**

Verified with `buildProblems` on a scratch build; the embed behaviour is from reading the code.

- The docs say both the build and the embedding stop "on an image the home page shows that is not one of those WebP files". `verify-build.mjs` only checks the shape of the path.
- A home page showing `/assets/not-in-the-build.webp` passes the build. Only the embed checks that the file exists.
- A stray `.webp` that no page shows passes both. The Worker then serves it.
- Two comments no longer hold:
  - `proxy/scripts/test/embed-web.test.mjs:1-4` says "both refuse it", which is not true of its new test at `:78`.
  - `web/scripts/verify-build.mjs:1-8` still says `assets/` holds only scripts, stylesheets and fonts.
- **Failure scenario.** A renamed screenshot leaves the page pointing at a missing file. `npm run build` stays green, and only the embed (or `npm run bundle`) fails.
- **Fix.** Either have `buildProblems` check that each image on a page names a file in `assets/` and that every `.webp` there is shown, or change the docs to say that only the embedding checks this.

**L2. `docs/proxy.md` was only partly updated.**

Verified by reading.

- `:1157-1159` still says `/assets/` serves "scripts, stylesheets and WOFF2 fonts". It now also serves WebP files, as `image/webp`.
- `:1160-1164` still lists the module's inputs without `home.html` and `.webp`.
- `:1171-1178` still allows links to other origins only "for the links of a legal page", and gives the image rule for legal pages only. Both rules now apply to the home page too.
- **Failure scenario.** A maintainer trusts `proxy.md` on what the Worker serves and checks.
- **Fix.** Name the WebP images, `home.html`, and the home page's link and image rules.

**L3. Other docs and comments are now wrong.**

Verified by reading.

- `docs/web.md`:
  - `:3-22` lists `/` among the pages of "one bundle that picks the page from the path". It is a static page with no script (`web/src/route.ts`, `web/src/main.tsx`).
  - `:29-30` still says "two static pages".
- `docs/security-threat-model.md:274-279` lists the pages served without `/`, and says the legal pages are the ones that run no script. Its line references into `web.ts` were already out of date before this commit.
- `docs/release-checklist.md:116-117` checks that `/privacy`, `/terms` and `/delete-account` answer. `docs/store-listing.md:136-138` now suggests `/` as the Play website, so the checklist should check `/` too.
- `web/src/app.css:3-4` still says the content keeps to one column of at most 600px. The home page is 1,120px wide with two columns.
- Two comments are now too narrow, as both also cover the home page:
  - `page-checks.mjs:96` (`LEGAL_LINK`, "a link of a legal page");
  - `static-pages.mjs:38` (`SITE_LINKS`, "the legal pages and of /delete-account").
- **Failure scenario.** The release check passes while `/`, the website given on Play, is down.
- **Fix.** Update each sentence named above.

**L4. Some tests claim more than they prove.**

Verified by reading, and by checking each refusal case's message.

- `static-pages.test.ts:204` is named "describes the app as its Google Play listing does", but only compares the meta description. That is how M3 got through.
- `static-pages.test.ts:227-241` says each file name carries "a hash of their bytes", but accepts any 10 hex digits. Compare with `sha256(bytes).slice(0, 10)` instead.
- `page-checks.test.ts:134` does not prove the new `srcset` rule for legal pages. `PICTURE` also has an `<img src>`, which the old rule already refused.
  - The case that needs the new rule is a lone `<picture><source srcset="/assets/a.webp"></picture>`.
  - I checked that it is refused, but no test covers it.
- `proxy/test/web.test.ts:154` is named "shows only images of its own" but checks no image. The test at `:316` does, once the web app is built.
- Tests that are missing:
  - refusals for the markup in M1;
  - a check that the WebPs are 600 × 1333, which `LANDING_IMAGE_SIZE` (`static-pages.mjs:68`) hard-codes;
  - an end-to-end test of `/` like the one for `/privacy` (`web/e2e/legal-and-deletion.spec.ts:44-76`): headers, CSP violations, requests made, no script. My Chromium run of these found no problems, but CI does not run it.
- All 13 home-page refusal cases (`page-checks.test.ts:152-165`) fail for the reason they name.
- **Failure scenario.** The copy, a file name or the checks drift while the tests stay green.

**L5. Two small departures from the stylesheet's conventions.**

Verified by reading and rendering. Neither theme is broken.

- `app.css:1357-1363` sets `--home-bezel` on `.home`, with its own dark-theme media query. The file's convention is a `--web-*` role on `:root` (`app.css:36-56`). It also reuses the floating bar's colour (`--sv-bar`) and an outline role (`--sv-outline-variant`) as the fill of the phone frame.
- `app.css:1598`: `.home-chip` takes title-large's weight and family with title-medium's size and line height. That style is not in the type scale (there is one precedent at `:1274`), and it drops title-large's tracking.
- **Failure scenario.** Retuning the bar or an outline role recolours the phone frames. A change to the type scale misses the chips.
- **Fix.** Add a `--web-*` role on `:root`, for example `--web-frame`, and use a whole type style for the chips.
- Checked and fine:
  - no hard-coded colour;
  - all 44 `--sv-*` tokens the section uses exist in `tokens.css`;
  - every font weight used has a font file;
  - the screenshots' shadow, `--sv-elevation-4`, is `none` in the dark theme.

**L6. The "Coming soon" badge looks like a button, and the headline is outside `main`.**

Seen in the render. The owner decides.

- `app.css:1439-1449` draws "Coming soon to Google Play" exactly like the primary pill button: lime on violet, 48px high, rounded ends. Tapping it does nothing.
- The comment at `:1438` says it is a statement and not a button, but a visitor cannot tell.
- The page's only `<h1>` is in the hero's `<header>` (`static-pages.mjs:352-366`). So "skip to main content" and the `main` landmark both miss the headline.
- **Fix.**
  - Give the badge a quieter tag style.
  - Either wrap the hero inside `<main>`, or make the hero a `<section>` inside it.

**L7 (operations, for information). The images cost something on every start and every first visit.**

Measured. This is fine at today's scale, but worth knowing.

- The 14 images add 747 KB of base64 to the Worker module. The module is now 1.07 MB, or 719 KB gzipped.
- `WEB_ASSETS` decodes every asset when the module loads (`embed-web.mjs:175-186`). Every cold start pays for that, including cold starts of API routes: 2.3 to 4.2 ms in Node here.
- A first visit to `/` costs 12 Worker requests: the page, the stylesheet, 3 fonts and 7 images. The Worker answers each one itself; nothing is cached at Cloudflare's edge. All of them count against the API's request allowance.
- **Fix, if it matters later.** Decode each asset on its first request, or move the static files to Workers Static Assets.

## Edge cases explored and found correct

- **Routing** (`proxy/src/index.ts:191`, `:198-212`):
  - Segments must match exactly, so only `/` reaches the page, with any query string.
  - `//`, `/index.html`, `/home` and `/home.html` are 404 (tested). POST, PUT and DELETE are 405.
  - HEAD gets GET's status and headers with no body.
  - The `HASH_SALT` gate applies, as it does for `/privacy`.
- **Nothing depends on `/` being 404:**
  - the Playwright web server waits on `/s/`;
  - the eval proxy waits on `/v1/usage`;
  - the app never requests `/`;
  - App Links cover only `/g/` and `/r/` (`app/src/main/AndroidManifest.xml:61-68`), so `/` opens in the browser.
- **CSP** (`proxy/src/web.ts:77-85`): tested as an exact string. In Chromium there was no violation. The 3 fonts and 7 images loaded, and no other request was made.
- **Embed:**
  - `.webp` files are held and served as bytes (`embed-web.mjs:50`, `:137`).
  - The served bytes equal the embedded ones. Each file starts with `RIFF…WEBP` and is served with `image/webp`, `immutable` and `nosniff`.
  - `--require` stops when `home.html` is missing.
  - The placeholder home page passes its checks (by reading).
  - Vite empties `dist/` on each build, so no WebP from an older build is left behind.
- **Images:**
  - The embedded `HOME_PAGE` equals `homePage()` rendered from the committed source.
  - The 14 WebPs are byte-identical to a fresh run of `make-images.py`'s steps on the current reference images (Pillow 12.3.0).
  - All are 600 × 1333 lossy VP8, 24.2 to 47.9 KB each. One theme's seven come to 279 to 281 KB, as `docs/web.md:184-185` says.
  - None shows a QR code, an account number, an email address, a street address or a phone number.
- **Copy against the code:**

  | Page says | Code |
  |---|---|
  | Android 8.0 and later | `minSdk = 26` |
  | Up to five pages | `DocumentScanner.MAX_PAGES = 5` |
  | 3 / 10 / 300 calls | `proxy/src/policy.ts` |
  | VietQR, PromptPay, UPI, SEPA, PayPal.Me, pasted links, plain details | `PaymentMethod.kt` |
  | Repeats weekly or monthly | `Recurrence.kt` |

  - Also confirmed: insights by month, category, person and group; CSV and PDF exports; Undo; reminders.
  - Pro adds only AI calls.
  - The meta description equals the listing's short description, and nothing links to Google Play yet.
- **Layout and themes:**
  - no horizontal overflow at 320, 360, 600, 720, 959, 960, 1,440 or 1,920 px, in either theme;
  - the dark images are chosen in the dark theme;
  - the headline scales from 40 to 72px, as `app.css:1341-1347` says;
  - all colours come from tokens that follow the theme.
- **Scope:** the commit changes nothing unrelated.
  - The `head()` title change leaves the legal and deletion page titles exactly as they were.
  - Renaming `LEGAL_PAGE_POLICY` to `STATIC_PAGE_POLICY` stays within `web.ts`.
  - The font test now filters by type, which it has to now that images are also stored as bytes.

## Metrics

- `web/`:
  - `npm test`: 24 files, 317 tests passed.
  - `tsc --noEmit -p tsconfig.json`: clean.
- `proxy/`:
  - `npm test`: Vitest, 24 files and 512 tests passed, none skipped (the real web build was embedded); `node --test`, 31 passed.
  - `tsc --noEmit`: clean.
- `git diff --check`: clean.
- No linter is set up for `web/` or `proxy/`. Coverage was not measured.

## Recommended actions

1. M1: an allow-list check for the static pages, with the probe cases as tests.
2. M2 and M3: fix the two sentences, and decide whether the listing changes too.
3. L1: make the build check that each shown image exists, or correct the docs.
4. L2 and L3: update `docs/proxy.md`, `docs/web.md`, the threat model, the release checklist and the outdated comments.
5. L4: tighten the tests named there, and add an end-to-end test of `/`.
6. L5 to L7: as the owner prefers.

## Unresolved questions

1. Should the store listing's "stay on your phone" and its allowance sentence change along with the page (M2, M3)? That text is the owner's.
2. Was the home page's design approved? `docs/web.md:41-43` says the stylesheet draws the pages "as the approved web designs do", but `plans/261120-0528-ui-redesign/boards/web/` has no board for a home page.
3. Deploying this commit publishes the store screenshots before the listing goes live. `docs/store-listing.md:113-114` asks for a check that the sample data is fictional "before publishing", and "Quán Ốc Ba Miền" appears in both hero screenshots. Has that name been checked?
4. Once the app is out, a "Get it on Google Play" badge would be an image, but the home page checks take only `/assets/*.webp`. Will a text link do, or should the checks allow the official badge?

Status: DONE_WITH_CONCERNS
