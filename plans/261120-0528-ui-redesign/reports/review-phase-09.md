# Phase 9 review: commits 934c051, 8984d57, 54c69fa

None of the findings is critical or high. Two are medium and need fixing before the phase closes: Safari no longer announces several lists as lists, and docs/proxy.md still quotes the old policies. The rest are low-severity polish and test gaps.

**What I ran**
- Web unit tests: 23 files, 279 tests, all pass.
- Web typecheck: passes.
- Proxy tests: vitest 24 files, 509 tests, plus 27 `node --test` tests, all pass. They ran against the real embedded build (3 fonts, 1 script, 1 stylesheet), not the placeholder. I checked that this build contains the final CSS rules.
- The real `money.ts`, `dates.ts` and `region.ts` under Node 22.23 / ICU 78.3 for 27 regions.
- A rebuild of the three fonts with fontTools 4.60.1 and Brotli 1.1.0, into the scratchpad.
- WCAG contrast for every colour pair the new CSS sets, in both themes.
- WebKit's source for how it decides whether a list is a list.

**What I did not run:** Gradle (forbidden), `npm run build` and `npm run size` (a local hook blocks access to the build folder), Playwright. No process is left running, and the repo is as I found it.

**Two things in the brief are off:**
- The working tree is not clean. `/home/user/split-bill-poc/plans/261120-0528-ui-redesign/phase-09-web-pages.md` has uncommitted todo ticks, including "tests, build, size, Playwright" marked done.
- DateTexts.kt and DateOrder.kt are in `/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/domain/`, not in `app/.../text/`.

## Findings, most severe first

### Medium 1: Several lists stop being announced as lists in Safari, and the step numbers are silent everywhere

**Where:**
- `/home/user/split-bill-poc/web/src/app.css:1089-1123` (`.steps`: `list-style: none`, and the number as `content: counter(step) / ""` at :1109).
- `/home/user/split-bill-poc/web/src/app.css:1142-1165` (`.bullets`, which now falls under the global `ul { list-style: none }` at :101-104).
- `/home/user/split-bill-poc/web/src/app.css:1221-1269` (`.prose ul/ol`, with `/ ""` at :1255).
- The markup: `/home/user/split-bill-poc/web/src/pages/invite-page.tsx:23` and `/home/user/split-bill-poc/web/src/pages/delete-account-page.tsx:76`, `:92` and `:116`, plus every list on the legal pages.

**What went wrong:** before this change, these lists showed visible markers. `.steps` used the browser's default numbers (old app.css:645), `.bullets` had `disc` (old :670), and the legal pages had `disc` and `decimal` (old :759 and :763). WebKit treats a `<ul>` or `<ol>` with no ARIA role as a list only when its items show a visible marker; its source says "<ul>, <ol> lists need to have visible markers". A `::before` that is empty, or whose alternative text is `""`, does not count, so outside a `nav` the list becomes a plain group.

**Concrete case:** an iPhone user with VoiceOver opens a group invite. The three "How to join" steps are no longer announced as "list, 3 items", and their numbers are not spoken. On /delete-account, "What is deleted" and "What stays" (six items each) lose list navigation, and so does every list on /privacy and /terms. In every browser, `/ ""` now hides the step numbers that used to be read out. This breaks the phase rule that no role changes. Playwright runs Chromium only, so it cannot catch this.

**Fix, either of:**
- Keep native markers (`list-style: disc` / `decimal`) and style `li::marker` for colour and size.
- Add `role="list"` to `ol.steps` and `ul.bullets`, and have `static-pages.mjs` emit it on prose lists. This restores what Safari exposed before and changes nothing in Chrome or Firefox.

Either way, drop the `/ ""` so the step numbers are spoken, then check with VoiceOver on iOS.

### Medium 2: docs/proxy.md still quotes the old policies and the old asset rules

**Where:** `/home/user/split-bill-poc/docs/proxy.md`:
- :1125 (link-page policy), :1132-1133 (legal-page policy) and :1141 (/delete-account policy): all three still lack `font-src 'self'`.
- :1151-1152 says `/assets/` serves only "scripts and styles".
- :1157 says the module holds "the `.js` and `.css` files".

**Concrete case:** anyone checking a deployed Worker's headers against this document, which is the Worker's contract document, finds a mismatch, and reads that fonts are not served.

Under the current plan nothing will fix this. Phase 10 says it does not touch or review the web, and it does not list proxy.md.

**Fix:** add `font-src 'self'` after `style-src` in all three quotes. Say that `/assets/` serves scripts, stylesheets and WOFF2 fonts (`font/woff2`), and that the module holds the `.js`, `.css` and `.woff2` files.

### Low 3: Some regions get a stray no-break space next to the currency sign

**Where:** `/home/user/split-bill-poc/web/src/money.ts:53-62` (`withSymbol`) and `:95`.

**What went wrong:** the page swaps the region's own currency symbol for the English one. It keeps the U+00A0 space that ICU inserted only because the region's own symbol had a letter touching the digits.

**Concrete case:** a browser set to es-MX opens a bill in EUR. Intl writes "EUR 1,234.50", and the page writes "€ 1,234.50" with a no-break space. The app on a phone set to Mexico writes "€1,234.50": ICU, which the phone uses, spaces only a symbol that ends in a letter, and the JVM never does.

The same happens for VND, GBP, INR, KRW, CNY, AUD, CAD and HKD across es-419, for USD in ms-MY and af-ZA, for CNY in ja-JP ("CN¥ 1,234.50"), and for INR in id-ID. The number is never wrong, but the "space beside the symbol" rule the docs promise is broken.

**Fix:** take the parts from `Intl.NumberFormat` with `currencyDisplay: "narrowSymbol"` (falling back to the default display if the option throws), then swap and space as now. I ran this variant:
- The cases above become "€1,234.50", "$1,234.50", "CN¥1,234.50" and "₹1.234,50".
- The en-US, vi-VN, de-DE, th-TH, hi-IN, fa-IR, ar-EG and KWD outputs stay byte-identical.

Add tests for es-MX with EUR and ms-MY with USD.

### Low 4: The status pill's text drops to 4.04:1 where it overlaps the decorative circle

**Where:** `/home/user/split-bill-poc/web/src/app.css:342-351`, together with the circle at `:296-307`.

**What went wrong:** the pill's background is 16% white over the violet hero. Where it lies over the circle (10% white), its text is 4.04:1, below 4.5:1, in both themes (the violet is the same in both).

**Concrete case:** a request page with an amount of 14 or more characters, such as "$12,345,678.90" or "₫1,500,000,000". The amount drops to its smallest size, about 26 px. With no "For …" line and the reported status ("Minh says it is paid. Lan has yet to confirm it."), the pill sits about 94-140 px from the hero's top at 390 px wide. The circle reaches down to 120 px, so part of the pill's first line sits on it.

The "Bill total" label got opacity 0.9 for exactly this reason (4.68:1 over the circle); the pill did not get the same treatment.

**Fix:** make the pill opaque: `background: color-mix(in srgb, var(--sv-on-brand) 16%, var(--sv-brand))`. That gives 4.79:1 everywhere.

### Low 5: The build's stylesheet check misses some ways CSS can load files

**Where:** `/home/user/split-bill-poc/web/scripts/page-checks.mjs:26-44`.

**What went wrong:** I probed the check directly, and all three of these pass with no problems reported:
- `image-set("https://evil.example/x.png" 1x)`
- the CSS-escaped `u\72l(https://…)`
- `url("https://evil.example/a)b.png")`

The CSP still blocks other origins when the page runs, so the repo's own CSS can't exploit this. But `/home/user/split-bill-poc/docs/web.md:153-154` and `/home/user/split-bill-poc/proxy/scripts/embed-web.mjs:24-25` claim the build refuses "a stylesheet that loads anything but a font".

**Fix:** refuse `image-set(` and `-webkit-image-set(`, refuse any backslash escape, and parse quoted URLs whole, for example `url\(\s*(?:"([^"]*)"|'([^']*)'|([^)\s"']*))\s*\)`. Add these three cases to the tests.

### Low 6: Test gaps

- **(a) A test case passes for the wrong reason.** `/home/user/split-bill-poc/web/tests/page-checks.test.ts:154` is labelled "a name that is only an ending", but uses `".woff2x"`, which is refused only because it doesn't end in `.woff2`. `.woff2` itself is not tested. The name rule (`page-checks.mjs:17`) accepts `..woff2`, `..js` and `-.css`. Fix: test `.woff2`, and require the name to start with a letter or digit.
- **(b) Nothing tests the two scripts that refuse stray files.** The phase asked for a test that an unknown file in `assets/` is still refused. Only the shared `assetType()` is unit-tested. If `embed-web.mjs`'s `assetsIn()` or `verify-build.mjs` stopped calling it, or stopped calling `stylesheetProblems`, no test would fail. Fix: run them on a temporary folder holding a `.png` and a stylesheet with a remote `url()`.
- **(c) The phase's success strings are not pinned on the page.** No component test renders the bill page with `navigator.languages = ["vi-VN"]` to check "231.000 ₫" and "14 Nov 2026 · 3 items". They are checked only in the unit tests and on the hero amount alone. Fix: one `ShareView` test, using the same spy as `hero-amount.test.tsx`.
- **(d) A test can pass without checking anything.** The font test in `/home/user/split-bill-poc/proxy/test/web.test.ts` asserts nothing when the module holds the placeholder (no web build). That is fine in CI, which builds the web app first; it only matters for local runs.

### Low 7: Weights 500 and 700 have no font file, so the browser picks the nearest one

**Where:** `/home/user/split-bill-poc/web/src/tokens.css:97` and `:154`, plus the boards, which draw Be Vietnam Pro at 500 and 700.

Only Be Vietnam Pro 400 and 600 and Bricolage 800 ship, so browsers draw:
- filled and danger buttons ("Pay on paypal.me", "Delete my account") at 600, where the board draws 700;
- item amounts, row amounts and step numbers at 800, where the token says 700;
- item names at 400, where the board draws 500.

No fake bold is synthesized, because a 600 face exists. This follows from the three-file decision. Record it, or map the CSS to the shipped weights explicitly, so the owner's screenshot review compares like with like.

### Note 8: Differences from the boards to check in the owner's screenshot review

No text changed. These are size and colour differences only:
- Item and charge amounts are 15 px (board: 16 px), and the total is 19 px (board: 20 px).
- The bill title is 26/32 px (board: 28/34 px).
- The hero number is 56 px (board WebBill: 52 px).
- The "Bill total" label has opacity 0.9 (board: 0.8). This one is deliberate, to reach 4.5:1.
- Name chips follow the app's colour hash, so "Lan" is pink (person-0) where the board shows blue.

### Note 9: Two edge cases in old browsers and pseudo-currencies

- Browsers without `Intl.supportedValuesOf` (Safari < 15.4, Chrome < 99, Firefox < 93) treat any well-formed code as a currency, so an unknown "ZZZ" is written "ZZZ 1,234.56" instead of "1,234.56 ZZZ".
- Current browsers list XDR and XSU as currencies, while Java (which reports −1 decimals for them), and so the app, treats them as unknown.

## Checked and found correct

**Policies and the asset route**
- The three policies differ from before only by `font-src 'self'`; the legal policy was also reflowed onto several lines. The proxy tests and the e2e specs compare them word for word.
- `webAssetRoute` looks the name up exactly in a `Map`, using the raw path segment. Nested paths, `..%2F…`, `constructor`, `.woff` and `.png` all get 404.
- Content types come from a fixed table (`font/woff2` for fonts), with `nosniff` and an immutable one-year cache.
- Each font is served twice in the proxy test, which rules out a buffer emptied by the first response.

**Build checks and sources**
- `verify-build.mjs` and `embed-web.mjs` both refuse any file in `assets/` other than `.js`, `.css` and `.woff2`, as well as folders. The embed script also refuses symlinks.
- A stylesheet may only load existing `/assets/*.woff2` files, and `@import` is refused (apart from the gaps in Low 5).
- No `style` attribute and no new remote origin anywhere; the source-safety test now also reads the CSS.

**Fonts**
- 36,328 + 32,368 + 34,376 = 103,072 bytes, under the 120 KB cap.
- My rebuild matches `font-checksums.sha256` byte for byte. The source font files match the app's pinned hashes, and the 800 weight is cut from the 14pt file.
- All 144 Vietnamese vowel forms (12 vowels × 6 tones, both cases) and đ/Đ are present in all three files.
- The copyright and licence records (name IDs 0, 13 and 14) are identical to the source files.
- Neither family declares a Reserved Font Name, so the subsets may keep their names. The OFL files are byte-identical to the app's.
- `@font-face` uses `font-display: swap`.
- The size check really counts the fonts: it counts the files the stylesheet loads, against a cap of 3 files and 120 KB. The proxy test checks that the embedded fonts are exactly the ones the stylesheet loads.

**Money and dates**
- en-US, vi-VN and de-DE produce the app's RegionFormatsTest strings exactly.
- The order of day, month and year matches the app for all ten regions it tests (fa-IR day first, ja-JP year first, en-CA month first, sv-SE day first).
- hi-IN writes "₫9,02,664", as phase 4 recorded on a real phone.
- Digits are always 0-9, including for ar-EG, fa-IR, `-u-nu-arab` and `-u-nu-thai`.
- Currencies with 0 and 3 decimals work, and the bill's own number of decimals is kept.
- Negative amounts match the app ("-$12.50", "-12,50 $").
- Unknown codes go after the number with a space, as in the app.
- An empty, malformed or unknown language list falls back to en-US.
- Lists of names are still joined in English.
- The contract only lets `^[A-Z]{3}$` codes through, so the case of an empty code (which would leave a trailing space) cannot happen.

**Markup and accessibility**
- No text, route, title, heading level, role or ARIA attribute changed; the only additions are decorative icons with `aria-hidden`.
- Every class the phase lists as pinned by tests is still in the markup. Every class the markup uses has a CSS rule, and every CSS variable used is defined.
- The QR code is black on white, square and unrounded, with a 4-module quiet zone, plus a white tile in the dark theme. In forced-colours mode the canvas pixels stay as drawn.
- Touch targets are at least 48 px.
- The focus ring is 3 px with a 2 px offset: at least 5.6:1 in the light theme and 11.4:1 in the dark one. The name field now gets it too.
- All other text pairs reach 4.5:1 in both themes.
- With reduced motion, the loading bar and the spinner stand still.

**The deletion page fix**
- The slot for Google's button is now keyed. Without the key, Preact 11 reuses the unkeyed slot `div` for the confirm step's buttons, so Google's injected button stays on screen.
- The test's fake Google library injects a real `<button>`, so the new assertion would fail without the key.
- Cancel draws Google's button again, so going back to the sign-in step works.

**Other checks**
- The Playwright changes are meaningful: they check that the fonts loaded, that there are no CSP violations, and that the browser's languages are `["en-US"]`.
- `tokens.css` matches the Kotlin sizes, weights, letter spacing, shapes, spacing and palette.
- With the shipped font's real widths, the hero amount never overflows enough to be clipped.

## Unresolved questions
1. Was the Gradle gate (`WebTokensTest` in compare mode) run on 54c69fa? plan.md:98 requires it, and nothing in the commits says so.
2. Were Playwright and `npm run size` run on the final commit? They are ticked only in the uncommitted phase file.
3. Should the docs/proxy.md fix land in phase 9, given that phase 10 excludes the web?
4. Does the owner accept the weight substitutions and the chip colours that differ from the boards?
5. I did not measure how much text jumps on a desktop's first load, when "Arial Black" stands in until Bricolage arrives.

Sources (WebKit's list detection):
- [WebKit AccessibilityList.cpp (wpewebkit 2.38.6, Debian sources)](https://sources.debian.org/src/wpewebkit/2.38.6-1/Source/WebCore/accessibility/AccessibilityList.cpp/)
- [WebKit AccessibilityList.cpp (upstream)](https://webkit.googlesource.com/WebKit/+/master/Source/WebCore/accessibility/AccessibilityList.cpp)
