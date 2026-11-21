# Review: the launcher icon and the sample account numbers

Independent review of the branch `feat/app-icon` against `main` (`git diff e7f15bb..1506b08`: commits `8e49b78`, `b7af338`, `1506b08`), 21 November 2026. The fixes and the owner's answers are recorded in [`phase-10-docs-store-finish.md`](../261120-0528-ui-redesign/phase-10-docs-store-finish.md). The old Vietcombank sample number is left out of this report on purpose.

## Scope

- 42 files, +181 / −59, 13 PNGs (12 re-recorded, 1 new).
- Searched across app, debug, web, proxy, docs and plans. The PNGs were viewed and measured, each re-recorded image was compared with `main`, and Play's and Android's current icon pages were checked.
- Not run: Gradle. CI had not run on the branch either.

## Overall assessment

- The icon work is correct: path data, sweep flags, safe zone, notice icon and Play picture all hold, checked by calculation and by measuring pixels.
- Where the number replacement was applied it is clean, but it missed a second Vietcombank sample in the debug catalog, which the re-recorded catalog images still show.
- One older test weakness in `ClaimViewModelTest` bears directly on the replacement.

## Findings, most severe first

**H1. The debug catalog and its three re-recorded images still show a Vietcombank account number.**
- `app/src/debug/java/com/dantech/splitvibe/catalog/CatalogScreen.kt:888` sets `mutableStateOf("Vietcombank\nAccount 0123 4567 89")`, which the "Details" field at `CatalogScreen.kt:975-981` draws.
- It shows in `catalog-light.png` and `catalog-dark.png` (rows ~7030-7070) and in `catalog-large-text.png` (rows ~9130-9170), all three re-recorded by `b7af338`, whose message says the debug catalog was done.
- 0123456789 has ten digits, a length Vietnamese banks use, so it does not meet the owner's rule of a number no bank gives.
- Fix: `"Vietcombank\nAccount 0000 0000 00"`, re-record the three images and check that only that row changes.

**M1. The proxy tests use the same Vietcombank number (bank code 970436, account 0123456789).** Outside the scope the owner named, so it is the owner's call.
- `proxy/test/payment-requests.test.ts:15,19,20,125`, `proxy/test/account.test.ts:491`, `proxy/test/push.test.ts:82`, `proxy/test/links.test.ts:68`. Source only, not images, but the repository is public.
- Some of these values are markers in "must not leak" checks (`push.test.ts:104`, `links.test.ts:102`, `payment-requests.test.ts:362`). Runs of zeros also occur in fixture ids and timestamps, so all-zero values could make a "does not contain" check fail for no reason and a "contains" check pass on an id.
- Suggested fix: something distinctive for the leak checks; the same file already uses `"LAN-ACCOUNT"` and `"MALLORY-ACCOUNT"` (`payment-requests.test.ts:253,261`).

**M2 (older than this branch). `ClaimViewModelTest`'s "a payment method changed since the amounts went is noticed" can pass without comparing the methods.**
- `ClaimViewModel.kt:108-111` computes `link.method != chosen?.let { fingerprintOf(it.method) }`, and `method` (`ClaimViewModel.kt:103-105`) is null until Room's query returns its first result. So the first value of `methodChanged` is already true for a settled link where the user is paid, whatever the methods are.
- `ClaimViewModelTest.kt:290-307` only waits for `methodChanged` to be true, and no test covers an unchanged method.
- With this branch's numbers, nine and ten zeros still give different fingerprints, so the test still describes the right case, but it does not prove it.
- Production effect: on opening a settled link, `ClaimScreen.kt:474` can show "Send the new details" until the methods load.
- Fix: treat "methods not loaded yet" as unchanged in the view model; in the test, wait for `viewModel.method.value != null`; add a test with the same fingerprint that expects false.

**M3. Other payment ids that could belong to real people are still in reference images.** Whether the rule covers these is the owner's call.
- `27-payment-methods.png` and its large-text twin: PromptPay `081-234-5678` (`GroupsGalleryTest.kt:376`), which has the form of a Thai mobile number, and the IBAN `DE89 3704 0044 0532 0130 00` (`GroupsGalleryTest.kt:377`), a well-known example IBAN with a valid check digit.
- `25-pay-open-link.png`: `https://revolut.me/dana` (`RequestFixtures.kt:25`). `catalog-*.png`: `paypal.me/minhtran` (`CatalogScreen.kt:881`).
- In source only: `GroupViewModelTest.kt:77`, `PaymentMethodRepositoryTest.kt:26`, `ShareRepositoryTest.kt:162-175`, `RegionFormatsTest.kt:307,309`, and `paypal.me/lan` in the web and proxy fixtures.
- PromptPay accepts `000-000-0000`. A handle can never be proven unowned, so handles need a decision rather than a pattern.

**L1. The monochrome test only checks that the layer exists.** `LauncherIconTest.kt:51-54` passes if `<monochrome>` points at an empty or wrong drawable. Fix: draw the layer into the same square and check it is opaque inside both halves and clear in the ground and in the gap at (54, 54).

**L2. No test covers `ic_notification.xml`.** A flipped sweep flag there would pass CI. Fix: draw it at about 240 px and check opacity inside each half, in the gap and outside.

**L3. Two checks accept longer runs of zeros.** `RequestsViewModelTest.kt:62` and `web/tests/share-page.test.tsx:145` would also accept "Account: 0000000000". Neither gives a false pass today, as each fixture has one account. Fix: match the whole line, for example `/Account: 0{9}(?!0)/`.

**L4. A docs claim the code no longer backs.** `docs/design-system.md:61-64` says every colour is declared once and leaves no stale copy, but `app/src/main/res/values/colors.xml:5-6` now copies Violet and Lime. Fix: say, as for the window colours, that a change to `brand` or `accent` has to be repeated there and that `LauncherIconTest` fails until it is.

**L5. A comment says too much.** `LauncherIconTest.kt:63` and `:86` say every launcher shows the middle 72 units. A round mask shows less; only the middle 66 is always shown. Fix: "the most any launcher shows".

## Process and information

- CI runs only for pull requests and pushes to `main`; on the branch only Security and Device tests ran, and both passed. Not yet run there: ktlint, the Robolectric suite with `LauncherIconTest` and the verification of the re-recorded images, Android lint and packaging, and the proxy and web tests.
- The old numbers stay in public history: the Vietcombank sample in `main`'s history (`8e4d13c`, `0d244b7`) and on the pushed `fix/fictional-sample-account` branch (`39d1866`, an earlier copy of `8e49b78`), and 257678859 in many older commits. That branch can be deleted after the merge.
- Seven design boards in `plans/261120-0528-ui-redesign/boards/` (for example `RequestLink.dc.html:77`) and several reports still show 257678859. `review-branch.md` question 1, the app icon, is answered but not marked, unlike question 2.

## Checked and found correct

- Launcher icon geometry: each arc joins two opposite points of its circle; sweep 0 goes down the left side (white) and sweep 1 down the right (lime), at centres (51, 51.5) and (57, 56.5), the orientation of `SvTopBar.kt:182-183`. The farthest point of either half is 26.9 units from the centre, inside the 33-unit radius of the 66 safe zone; the mark is 52 × 51 units, within Android's 48-66 dp logo size. Measured in the rendered picture: white x 72-233 / y 75-400, lime x 278-439 / y 111-436.
- `ic_notification.xml`: the launcher mark scaled by 20/52 (radius 8.846), spanning x 2.000-22.000 and y 2.192-21.808, inside 22 of 24, with the same sweeps, in one colour.
- Monochrome layer: using the foreground as the monochrome layer is the example in Android's adaptive-icon guide; launchers keep only its shape, and the 6-unit gap keeps the split visible. Android 8 to 12 ignore the tag.
- Splash screen: no splash attributes are set and `MainActivity` has no icon of its own, so Android 12 and later show the whole launcher icon on the page.
- Old icon and colour: #0B6E4F and the receipt are gone from every tracked file except the historical review note. The manifest, the FCM notice icon and the debug catalog entry use the new icons; no notice or channel colour is set; the web and proxy pages use an empty favicon.
- `LauncherIconTest`: the colour test fails on a wrong ground, white or lime colour, on swapped halves and on flipped sweeps; the Play picture is compared on every run, since the build defaults to `roborazzi.test.verify`; Robolectric runs SDK 34, so `getMonochrome()` exists.
- Play picture: 512 × 512, 8-bit RGBA, fully opaque, 9,296 bytes; full square, no rounded corners or shadow, exact palette colours; the mark's 383 px extent fits Play's 384 px keyline and is not clipped by Play's corner mask.
- Number replacements: no test passes for the wrong reason; the matchers are exact or carry the "Account: " prefix that zero-filled ids lack. `RequestMoneyViewModelTest` reads the account field itself and still tests what its name says, since the request id is a hash of the method (`RequestIds.kt:15-21`). The validation accepts nine and ten zeros. The vietnam-qr-pay vectors kept in `PaymentRequestBuilderTest` and `EmvQrTest` fit the owner's decision. In all twelve re-recorded images the only changed pixels are the account rows and the QR codes.
- Docs: the two new sections match the code, apart from L4.

## Unresolved questions

1. Should the rule extend to the proxy tests (M1), and if so, what value should the leak checks use?
2. Should it extend to PromptPay numbers, payment handles and the DE89 example IBAN shown in images (M3)?
3. Is it acceptable that the old numbers stay in public history and on `fix/fictional-sample-account`?
4. Should the plan boards that show 257678859 stay as they are?

Status: DONE_WITH_CONCERNS
