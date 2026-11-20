# Scout: tests, CI, web and docs that check or document the UI

Scope: read-only inventory for the UI redesign plan (new colours, fonts, shapes, restyled components,
bottom navigation bar, restructured screens, renamed or removed labels, web pages sharing tokens).
Nothing was built, run or edited, except this report. Line numbers are as of the working tree on
2026-11-20. Secrets files (local.properties, proxy/.dev.vars) were not read.

## 1. Screenshot tests (Roborazzi, JVM)

### Mechanism
- Library: Roborazzi 1.76.0 (`gradle/libs.versions.toml:29`), test dependency `app/build.gradle.kts:295`.
- Mode switch: `-Pscreenshots=record` sets `roborazzi.test.record`; the default is `verify`
  (`app/build.gradle.kts:180-181`). Also `robolectric.pixelCopyRenderMode=hardware` (`app/build.gradle.kts:183`).
  Test code does not branch on the mode; it always calls `captureScreenRoboImage` / `captureRoboImage`.
- Record command (documented in `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt:89`):
  `./gradlew :app:testProdDebugUnitTest --tests '*ScreenGalleryTest*' -Pscreenshots=record`.
- CI runs verify: `.github/workflows/ci.yml:44-46`. On failure the side-by-side images are kept from
  `**/outputs/roborazzi/` (`ci.yml:99-109`).
- Reference folder: `app/src/test/screenshots/` (relative path written by each test).
- Determinism measures: ripples off via `LocalRippleConfiguration provides null`
  (`ScreenGalleryTest.kt:383`); working-state clock stopped and advanced 500 ms
  (`ScreenGalleryTest.kt:235-240`, constant line 497); halfway-to-sent clock 200 ms (`:298-302`, constant line 498);
  fixed data from recorded ML Kit output and Claude answer (`ScreenGalleryTest.kt:122`, `Fixtures.kt`; the resources are
  `app/src/sharedTest/resources/fixtures/sample_bill_ocr.json`, `sample_bill_answer.json`, `sample_bill_request.json`,
  wired at `app/build.gradle.kts:199-201`).

### Test classes that write images
| Class (file) | Config and qualifier | Images written (file:line of the call) |
|---|---|---|
| `ScreenGalleryTest` (`app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt`) | class `@Config(sdk=[34], qualifiers=PHONE)` at :94; PHONE = `w412dp-h915dp-xhdpi` (:477, 824x1830 px); dark `PHONE_AT_NIGHT` (:478); TALL `w412dp-h1900dp-xhdpi` (:481) and TALL_AT_NIGHT (:482); `GraphicsMode.NATIVE` at :93 | see the list below |
| `BackNavigationTest` (`app/src/test/java/com/dantech/splitvibe/BackNavigationTest.kt`) | `@Config(qualifiers="w412dp-h915dp-xhdpi")` :39 | `14-back-swipe-preview.png` (:90); no dark twin |
| `GroupsGalleryTest` (`.../ui/GroupsGalleryTest.kt`) | class PHONE-like `w412dp-h1600dp-xhdpi` :48; dark `...-night-xhdpi` :87, :114 | `22-groups` (:65), `23-group` (:84), `23-group-dark` (:88), `24-join-group` (:91), `25-requests` (:111), `25-requests-dark` (:116), `26-request-link` (:119, waits for label "Payment code for VietQR"), `27-payment-methods` (:132); helper `shot` at :147-161 |
| `ClaimGalleryTest` (`.../ui/ClaimGalleryTest.kt`) | `w412dp-h1600dp-xhdpi` :39; dark :70 | `28-bill-link` (:65), `28-bill-link-dark` (:71), `29-bill-link-settling` (:76) |
| `ProGalleryTest` (`.../ui/ProGalleryTest.kt`) | TALL `w412dp-h1200dp-xhdpi` :131, class :43; PHONE `h915dp` :133 | `47-pro` (:50), `47-pro-dark` (:54), `48-pro-member` (:57), `48-pro-member-dark` (:61), `49-settings-pro` (:65), `49-settings-pro-dark` (:69) |
| `SpendingGalleryTest` (`.../ui/SpendingGalleryTest.kt`) | class TALL :54; WHOLE_PAGE `h2000dp` :201 and night :202 | `40-insights` (:65, WHOLE_PAGE), `40-insights-dark` (:69), `41-export` (:72, opens "Export these bills"), `41-export-dark` (:76), `42-recurring` (:79), `42-recurring-dark` (:83), `43-repeat-bill` (:86, menu RENT_MENU, opens REPEAT), `43-repeat-bill-dark` (:90), `44-edit-bill-category` (:93), `44-edit-bill-category-dark` (:97), `45-history-categories` (:100), `45-history-categories-dark` (:104) |
| `AiConsentGalleryTest` (`.../ui/AiConsentGalleryTest.kt`) | PHONE :32; dark :42; large text :45-48; en-XA `PSEUDO_PHONE` :52 (constant :81) | `55-ai-consent` (:39), `55-ai-consent-dark` (:43), `55-ai-consent-large-text` (:48), `55-ai-consent-en-xa` (:53) |
| `PseudoLocaleGalleryTest` (`.../ui/PseudoLocaleGalleryTest.kt`) | class `PSEUDO_PHONE` = `en-rXA-w412dp-h915dp-xhdpi` :64, :236; `PSEUDO_TALL` = `en-rXA-w412dp-h1600dp-xhdpi` :239 | `50-home-en-xa` (:86), `51-result-en-xa` (:103), `54-edit-bill-en-xa` (:131), `52-settings-en-xa` (:146), `53-pro-en-xa` (:169); :82 only asserts a string resource is read |
| `ComponentCatalogTest` (`.../ui/ComponentCatalogTest.kt`) | `w412dp-h3000dp-xhdpi` :24 | `catalog-light` (:31), `catalog-dark` (:34), written at :42 |
| `PdfSummaryTest` (`app/src/test/java/com/dantech/splitvibe/export/PdfSummaryTest.kt`) | `@Config(sdk=[34])` :44 | `46-pdf-summary-page-1` and `-2` (:170), inside "each page looks like its reference image" (:162) |

### Screens and states in ScreenGalleryTest
- `every screen of the split flow` (:141-142) and its dark twin (:145-149, suffix "-dark" at :147): one walk, written in order:
  `01-home` (:210), `11-settings` (:213), `16-settings-signed-in` (:216), `17-settings-sign-in-expired` (:219),
  `12-new-split-without-ai` (:222), `02-new-split` (:225), `03-photo-and-instruction` (:231), `04-working` (:239),
  `05-result-top` (:244), `06-result-shares` (:252), `07-add-person` (:258), `08-item-split` (:266),
  `09-sent-to-claude` (:272), `18-split-by-percent` (:279), `19-who-paid` (:285), `20-edit-bill` (:291),
  `15-sending` (:301), `10-requests-sent` (:303), `21-history` (:308).
- `whole result page` (:151-153, dark :155-160): `13-result-whole-page` (:354), TALL.
- `the assistant on the result screen` (:163-171): `30-assistant` (:322), `31-assistant-proposal` (:336).
- `settings once the phone's speech service was allowed` (:174-182): `32-settings-speech-service` (:342).
- Large text, `RuntimeEnvironment.setFontScale(2f)` (:187), suffix `-large-text` (:188): `01-home` (:190), `03-photo-and-instruction` (:195), `05-result-top` (:200), `10-requests-sent` (:203).
- Screens rendered: HomeScreen (:386), HistoryScreen (:388), SplitScreen with AI and without AI (:391, :396), SettingsScreen in five states (:401-446).
- Count in this class: 19 light names in the walk + 1 + 2 + 1 = 23 light; the same 23 in dark; 4 large-text.

### Reference images
- Total: 97 PNG files in `app/src/test/screenshots/` (`ls` count). Breakdown: 38 dark screen images plus `catalog-dark`
  (39 dark-suffixed); 5 `-large-text`; 6 `-en-xa` (50-54 and 55); `catalog-light`; 46 light screen images, of which 2 are PDF pages.
- No test writes these 4 files any more (grep of every name in `app/src/test` and `app/src/androidTest` found no writer):
  `07-pick-people.png`, `07-pick-people-dark.png`, `08-item-sharers.png`, `08-item-sharers-dark.png`.
  They are stale candidates; keep, delete, or re-record only after the plan decides.
- Images with no store or README use: the whole folder is listed here; store use is in section 7.

## 2. JVM UI tests that find nodes by text, description or semantics

Production code has no test tags: `grep testTag(` in `app/src/main` and `core/designsystem/src/main` returns 0 hits.
Only `AmountAnimationTest.kt:79` sets a tag (`"amount"`, constant at :96), in the test itself. Every other finder
uses visible text, content description, semantics role (toggle, heading, click action, text input, scroll index),
or a dialog ancestor. A label change breaks these tests; a layout change breaks the scroll constants in section 1.

| File (path under `app/src/test/java/com/dantech/splitvibe/`) | Finders and labels (line) |
|---|---|
| `AppStartupTest.kt` | "Split Vibe" (:30), "Recent bills" (:31, :38, :44, :75), "Split a bill" (:33, :65), "New split" (:34, :67), CD "Back" (:37, :53, :74), CD "Settings" (:40, :49, :71), "Local currency" (:41, :54, :73), "Get Pro" with click (:50), "Split Vibe Pro" (:51). 24 finder calls. |
| `BackNavigationTest.kt` | "Split a bill" (:83, :104), "New split" (:84, :88, :93, :99, :112), "Recent bills" (:89, :94, :98, :115), "Use the sample bill" (:105). |
| `ui/split/SplitFlowTest.kt` | "Use the sample bill" (:93, :159), `R.string.split_default_instruction` (:96), "Read and split the bill" (:97, :161, :167), formatted VND amounts (:112, :118), "Nước suối" (:124), CD "Lan" as toggle (:125), "Done" (:126), "Request … from 2 people" (:133), "Ask for the money" (:134), "Send" with `hasScrollAction` (:139), CD "Back" (:165), CD "Bill photo" (:168), "Bill total" must not exist (:169). |
| `ui/split/BillLifecycleTest.kt` | "Type it in by hand" (:93), "Where" as text field (:94), "Add an item" (:95), "Item" and "Amount" as text fields (:98-99), "Done" (:102), "Add someone" (:105), "Name" field and "Add" (:106-107), "Request … from 1 person" (:108), "Ask for the money" (:109), CD "Back" (:312). |
| `ui/split/BillSafetyTest.kt` | "Paid by" as scroll target (:130). |
| `ui/split/SplitErrorsTest.kt` | "Get Pro" with click (:123, :126). |
| `ui/split/AiConsentFlowTest.kt`, `AssistantListeningTest.kt`, `SplitAssistantFlowTest.kt`, `PercentTextTest.kt` | No finder found by grep (they do not drive node text). Not enumerated further. |
| `ui/ScreenGalleryTest.kt` | Labels used for navigation and as anchors: "Use the sample bill" (:193, :227, :314, :348), "Read and split the bill" (:197, :236, :317, :351), "Request" substring + click (:202, :294, :299), "Adds up to the bill total" (:248), "Add someone" (:255-256), "Name" field (:257), "Add" (:259), `BEERS` = "Bia Saigon Special" (:263, :265, constant :485), "Done" (:267, :288, :292), "Show what was sent to Claude" / "Hide what was sent to Claude" (:269-273), "By percent" (:276-277), "100% of 100%" (:278), "You paid" substring (:282-283), "Several people" / "One person" (:284, :287), CD "Edit bill" (:290), "Change the split" field (:321), CD "Ask Claude" (:325), CD "Minh paid the whole bill" toggle (:329), "Proposed changes" (:333-334), "Hide"/"Show" as above, `INSTRUCTION` "Split with Minh and Lan. Minh had the beers." (:483), `ASSISTANT_INSTRUCTION` (:484). Scroll-position constants that depend on bottom bar and layout: `PAGE_PX` 1150, `HALF_PAGE_PX` 540, `SHARES_TOTAL_BOTTOM_PX` 1500, `PROPOSAL_TOP_PX` 220 (:488-495); comments at :262, :281, :331 say the bottom bar overlaps the list. |
| `ui/GroupsGalleryTest.kt` | Waits for "Payment code for VietQR" (:119). |
| `ui/paywall/ProScreenTest.kt` | 55 finder calls (:68-229): "Sign in with Google", "Subscribe", "Subscribe for $29.99 a year", CD "Monthly, $3.99 a month" and "Yearly, $29.99 a year", "renews automatically" (substring), "Manage subscriptions in Google Play", "Restore purchases", "Free for a month, then $3.99 a month.", heading "You have Pro", "Monthly plan, $3.99 a month", "Renews on 17 December 2026.", "Switch to yearly", heading "Your Pro subscription", "Open Google Play", "Ends on", "Pro cannot be bought right now", "Loading the plans…", "Try again", "OK", "Privacy policy", "Terms of use", "<n> AI calls a month". |
| `ui/settings/SettingsScreenTest.kt` | 36 finder calls (:63-189): AI consent heading constant `AI_CONSENT` (:63, :70), "Take back" (:72, :77), "Taken back. Split Vibe asks you again before it next uses the AI." (:75), switch by `isToggleable` with constant `SWITCH` (:84-98), "Free plan", "7 of 10 calls left this month", "Get Pro", "Pro plan", "Manage Pro", "Delete account", "Deleting the account does not cancel Pro", "cancel it in Google Play first", toggle `WITH_PHONE_DATA` (:144), "Delete", "Cancel", text field "Minh" (:166-170), "Privacy policy" and "Terms of use" (:178-189). |
| `ui/groups/GroupDialogsTest.kt` | CD "More for Minh" (:38-39), "Take out of the group" (:40), CD "More for this group" (:52), "Leave the group" (:53). |
| `ui/recurring/RepeatBillSheetTest.kt` | CD "Change the day of the first draft" as click action (:42), "Repeat" (:43). |
| `ui/ComponentBehaviourTest.kt` | 24 finder calls: "By item" (:58-61), "By percent" (:88-91), CD "Lan" and "Minh" (:112-125), "One by one" (:150-156), CD "More ways to request" (:152), "Request all" (:157), CD "Start a split" (:176), "Take photo" (:182), CD "Remove Lê Thị Lan" (:195), CD "Bia Saigon Special, 132,000, Minh" (:216). |
| `ui/ReducedMotionTest.kt` | "Use the sample bill" (:113, :125), "Read and split the bill" (:115, :127), "Request" substring + click (:119), "Ask for the money" (:120). |
| `ui/SpendingGalleryTest.kt` | Opens a row by text and click (:183), labels "Export these bills", REPEAT, RENT_MENU (passed as arguments, :72-90). |
| `ui/AmountAnimationTest.kt` | Tag "amount" (:72, :79, :96). |
| `ui/common/LegalLinksRowTest.kt` | 5 finder calls, not enumerated. |
| `ui/PseudoLocaleGalleryTest.kt`, `ui/ClaimGalleryTest.kt`, `ui/AppLinkTest.kt`, `ui/DesignSystemBoundaryTest.kt` | No text finders beyond the above (the boundary test is source-text based, see section 4). |

Labels that appear in more than one place and would collide if a bottom bar reuses them: "Settings" (AppLaunchTest:42
as text, CD at :41), "Recent bills" (home marker), "Split a bill" (home), "Requests" (PaymentRequestJourneyTest:113,
section 3).

## 3. Device tests (androidTest, Espresso-free Compose rules, on an emulator or phone)

Common helpers: `app/src/androidTest/java/com/dantech/splitvibe/e2e/JourneySteps.kt`.
- `TIMEOUT_MS` = 60 s (:33); `awaitNode` / `awaitNoNode` (:36, :41); `field(label)` = `hasSetTextAction() and hasText(label)` (:46).
- `tap()` uses `SemanticsActions.OnClick`, not a touch (:53, reason in :48-52).
- `list()` = `hasScrollToIndexAction` (:56). `typeBillByHand` (:62-65), `answerTheAiConsent(button)` on a dialog (:68-73),
  `fillInTheBill` (:79-89: "Edit bill", "Where", "Add an item", "Item", "Amount", "Done", "Bill total"),
  `addPerson` (:92-102: "Add someone", "Name", IME action), `assertShare(name, amount)` (:105-109: amount text and
  sibling name), `appCurrency()` (:112-115).

| Class (file) | Lines | Labels and semantics | Accessibility checks |
|---|---|---|---|
| `AppLaunchTest` (`app/src/androidTest/java/com/dantech/splitvibe/AppLaunchTest.kt`) | rule :25; `@Before` :31-34; tests :36-59 | "Split Vibe" (:38), "Recent bills" (:39, :58), CD "Settings" (:41), text "Settings" (:42), CD "Back" (:43), "Split a bill" (:45), "New split" (:46, :52, :56) | `enableAccessibilityChecks()` in `@Before`, only when `SDK_INT >= 34` (:33) |
| `SplitFlowOnDeviceTest` (`app/src/androidTest/java/com/dantech/splitvibe/SplitFlowOnDeviceTest.kt`) | class :40; accessibility :82; test :85; currency "VND" :49 | "Split a bill" (:87), "Use the sample bill" (:88), "Read and split the bill" (:89-90), "Agree" (:91-92), "Request" substring (:94-95), "Ask for the money" (:97), "Done" (:99), "Recent bills" (:100) | same `SDK_INT >= 34` gate (:82) |
| `e2e/SampleBillJourneyTest` (`.../e2e/SampleBillJourneyTest.kt`) | test :38; steps :40-70 | "Split a bill" (:40), "Use the sample bill" (:41), "Read and split the bill" (:42, :44), field "How should it be split?" (:43), dialog "Use the AI for your bills?" (:47), "Agree" (:49), "Bill total" (:52), merchant text "QUÁN ỐC BA MIỂN" (:53), shares "You", "Minh", "Lan" with VND amounts (:68-70), instruction "Split with Minh and Lan. Minh had the beers." (:75), POST body check (:57-64) | none in this class |
| `e2e/DeclinedAiJourneyTest` (`.../e2e/DeclinedAiJourneyTest.kt`) | :34-52 | "Split a bill" (:38), "Use the sample bill" (:39), "Read and split the bill" (:40-41), dialog button "Type it in by hand" (:42), "Corner Cafe" (:45), shares "You" (:50) | none |
| `e2e/ManualEntryJourneyTest` (`.../e2e/ManualEntryJourneyTest.kt`) | :28-46 | "Split a bill" (:32), "Corner Cafe" (:34), shares "You", "Minh", "Lan" (:39-41), "Request <amount>" (:43) | none |
| `e2e/PaymentRequestJourneyTest` (`.../e2e/PaymentRequestJourneyTest.kt`) | :70-121 | "Split a bill" (:75), "Request <owed> from 1 person" (:78), "Ask for the money" (:79), field "Your name on the requests" (:81), "Send" (:86), "Again" (:89), "Done" (:112), **"Requests" (:113, a navigation label)**, "Not paid yet" (:115), "You asked for" (:116), "Minh · Pho 24" (:117), "Not opened in the app yet" (:119), "OK" (:121) | none |

- Other androidTest files (`export/PdfSummaryOnDeviceTest.kt`, `ocr/MlKitBillTextRecognizerTest.kt`,
  `llm/GatewayBillAnalyzerOnDeviceTest.kt`, `data/settings/KeystoreCipherTest.kt`) have no finder by grep.
- `e2e/FakeProxyRule.kt:97` grants POST_NOTIFICATIONS on API 33+ so the system prompt does not cover the screen.
- Only two classes enable accessibility checks (touch target size, contrast, labels on tappable items). The journeys do not.
  The doc at `AppLaunchTest.kt:27-30` says "every tap", but that applies to the classes above only.

### Which runs and on which API
- Managed device: `pixel6Api34` = Pixel 6, API 34, `aosp-atd` (`app/build.gradle.kts:187-195`); run by
  `:app:pixel6Api34ProdDebugAndroidTest`. Journeys run on the `prodDebug` build (`docs/testing.md:118-125`).
- `.github/workflows/device-tests.yml`:
  - `android-14` job (:51-90): all of `app/src/androidTest`, release-gating (`release.yml` calls it; `device-tests.yml:7`).
  - push trigger paths (:26-36) include `app/src/main/**` and `app/src/androidTest/**`; nightly cron (:13, 18:23 UTC).
  - `profile` job (:95-142): records the baseline profile; skipped on release (:97).
  - `api-levels` matrix (:146-209): API 26 (target default), 30 and 36 (`aosp_atd`); runs only the `e2e` package; `continue-on-error: true` (:152); only on manual dispatch with the input (:148).

## 4. CI, lint and style rules, startup benchmark and baseline profile

### Jobs and steps a redesign touches (`.github/workflows/ci.yml`)
- Job `checks` (:21-109):
  - `ktlintCheck` (:41-42).
  - Unit tests and screenshots: `:core:domain:test :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest` (:44-46).
  - Android Lint: `:app:lintProdDebug :core:designsystem:lintDebug` (:48-49).
  - Packages (:52-53), dev flavor and device-test compile (:56-57).
  - "No Anthropic code in the packages" (:60-77) - unrelated to UI but a package check.
  - "Release checks" for 16 KB page alignment (:96-97).
  - Artifact on failure: reports and `outputs/roborazzi` (:99-109).
- Job `proxy` (:111-165): `npm audit` for the web (:137-140), web build into the Worker (:145-154), `npm test --prefix ../web` (vitest, :156), `npm run typecheck` (:160), `npm test` in proxy (:165).
- `release.yml`: reuses ci.yml and device-tests.yml (:74-89); web build into the Worker (:305-317); Worker deploy (:354-358).
- `security.yml` and `dependency-graph.yml` do not check UI.

### Style and lint rules
- `.editorconfig` (root): `max_line_length = 140`; `ktlint_code_style = android_studio`; trailing commas on declaration and call sites;
  `ktlint_function_naming_ignore_when_annotated_with = Composable` (Composables may start with a capital); `ktlint_standard_no-unused-imports = enabled`.
- ktlint runs as a plain Java program: root `build.gradle.kts:19-43`.
- Android Lint: `app/build.gradle.kts:204-210` (`checkDependencies = true`, so `:core:designsystem` lint is in the app report). No `lint.xml` and no lint baseline in the root, `app/` or `core/designsystem/` (checked by `ls`).
- `DesignSystemBoundaryTest` (`app/src/test/java/com/dantech/splitvibe/ui/DesignSystemBoundaryTest.kt`) is a source-text lint:
  - the rule (:15-28) scans `src/main/java` and `src/debug/java` (:115) for screens;
  - forbidden in screens (`FORBIDDEN` at :84 onward): `MaterialTheme(` / `MaterialExpressiveTheme {` (own theme), `SplitVibeTheme {` except in `MainActivity.kt` and `CatalogActivity.kt`, `Color(…)`, `colorResource(`, `Color.White` and other named colours, `.shadow(`, `dropShadow`, `tonalElevation`, `shadowElevation`, `FloatingActionButton(`, `Elevated*(`, `Defaults.*Elevation(`, `DropdownMenu(`, `tween(`, `spring(`, `keyframes`, `AnimatedVisibility(`, `AnimatedContent`, `Crossfade(`, `Animatable(`, `animate*AsState(`, `animateContentSize(`, `delay(` (allowed only in `ShareRepository.kt`).
  - Allowed samples at :59-68 (e.g. `SvCard(elevation = SvElevation.Level2)`, `SvPrimaryFab(…)`, `SvMotion.screenChange`).
  - A bottom navigation bar or a Material `NavigationBar`, `FloatingActionButton` or `DropdownMenu` written in a screen file would fail here. Their wrappers belong in `core/designsystem`, which this rule does not scan.

### Startup benchmark and baseline profile
- Module `baseline-profile` (`baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/`): `BaselineProfileGenerator.kt`, `StartupBenchmark.kt`, `SplitFlowBenchmark.kt`, `Journeys.kt`.
- `Journeys.kt:18-21` (`startASplit`) selects by UiAutomator text and description: "Split a bill", "Use the sample bill", `By.desc("Bill photo")`, "Recent bills". A relabel or a removed "Bill photo" description breaks the benchmark.
- Committed profiles, shipped in release: `app/src/prodRelease/generated/baselineProfiles/startup-prof.txt` and `baseline-prof.txt` (`docs/testing.md:114-115` says a release ships the committed profile).
- Re-recording: `device-tests.yml` profile job nightly (:95-142; artifact `baseline-profile`, 14 days). Benchmarks are not run in CI; the budget (`frameDurationCpuMs` 6 ms P50, 12 ms P95; cold start under 1 s) is a manual release check (`docs/release-checklist.md:26-31`).

## 5. Web: tokens, pages, tests, budgets, deploy

### Token file: produced and consumed
- Producer: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/WebTokensTest.kt`.
  - Test `the web pages use the app's tokens` (:26-38): in check mode it compares generated CSS with `web/src/tokens.css`; in write mode (`sv.webTokens == "write"`, :29) it writes the file.
  - Output path `TOKENS_FILE = "../../web/src/tokens.css"` (:185).
  - Generator covers: colours from `LightColors`/`DarkColors` (`colors()` at :73), type scale `SvTypography` (`typeScale()`), shapes (Material `Shapes()` plus `SvShapes.groupOuterCorner/groupInnerCorner`), spacing `SvSpacing` (`spacing()` at :159, a hard-coded list of six), elevation `SvElevation.entries` (4 levels, `SvElevation.kt:23-34`), dark overrides `@media (prefers-color-scheme: dark)`.
  - Write command: `./gradlew :core:designsystem:testDebugUnitTest -PwebTokens=write` (documented at `WebTokensTest.kt:18-22`; wired at `core/designsystem/build.gradle.kts:25-26`).
  - CI runs the check mode (`ci.yml:46`): a token change without rewriting the file fails CI.
- Output: `web/src/tokens.css` (header line 1: "Do not edit by hand"; `:root` light tokens :2-134; dark :136-195).
- Consumers: `web/src/main.tsx:2-3` imports `tokens.css` then `app.css`. `web/src/app.css` has 204 lines using `var(--sv-…)` (e.g. `.page` :180, `.footer` :188, `.footer__link` :198, `.hero` :213, `.banner` :244, `.person…` :540-586, `.payment` :600, `.details-card` :630, `.steps` :645, `.claimer` :654, `.footer__links` :706, reduced motion :825).
- Per-token-to-per-page map: not enumerated. Unknown without a line-by-line read of `app.css`.

### Pages and which tokens they use
- All pages share one stylesheet (`docs/web.md:36-39`):
  - `index.html` (link pages `/s/`, `/r/`, `/g/`) built from `web/index.html`; a `<meta name="sv-turnstile-site-key">` and `sv-abuse-email`.
  - `delete-account.html` (`/delete-account`): `web/src/pages/delete-account-page.tsx`, written by `web/scripts/static-pages.mjs:190-194` with the script and stylesheet tags of index.html.
  - `privacy.html`, `terms.html` (`/privacy`, `/terms`): rendered from `docs/privacy-policy.md` and `docs/terms.md` by `web/scripts/static-pages.mjs:28-31` (LEGAL_PAGES), with the stylesheet of index.html (`static-pages.mjs:168-174`). The page-footer links (`SITE_LINKS`, :34-38) use class `footer__link`.
- Other page components: `web/src/pages/share-page.tsx`, `invite-page.tsx`, `request-page.tsx`, `share-sections.tsx`, `app-link.ts`, `use-load.ts`; UI pieces in `web/src/ui/` (`footer.tsx`, `site-footer.tsx`, `payment-block.tsx`, `problem-view.tsx`, `open-in-app.tsx`, `qr-code.tsx`, `icons.tsx`).
- Hard rule: no inline `style` attribute and no inline script or style (CSP). Enforced by `web/tests/source-safety.test.ts` ("never sets a style attribute", no `console.`, no `innerHTML`, `localStorage` only in `claim-store.ts`) and by the page checks in `web/scripts/page-checks.mjs` and `web/scripts/verify-build.mjs:1-6`.
- The web's colour use is entirely through CSS variables; no colour literal is in the TSX (not fully verified; grep for `#` literals in `web/src/*.tsx` was not run).

### Web tests and budgets
- Vitest (`web/package.json` `test`: `vitest run`; `web/vite.config.ts:17-20`, happy-dom, `tests/**/*.test.{ts,tsx}`). Tests pin text and markup:
  - `web/tests/static-pages.test.ts:24` (stylesheet hash in fixtures), `:118` (exact `<a class="footer__link" href="/terms" aria-current="page">Terms of use</a>`).
  - `web/tests/page-checks.test.ts:16`, `:36-45` (inline style and foreign stylesheet refusals).
  - `web/tests/share-page.test.tsx:55-60`, `:94`, `:132-136`, `:293-294`; `web/tests/delete-account-page.test.tsx:80-83`, `:135-141`, `:207`; `web/tests/request-invite-pages.test.tsx:47`, `:85`, `:93`, `:102` (class `.details-card__text`), `:110-111`, `:122`, `:133` (class `.steps`), `:141`; `web/tests/payment-block.test.tsx:55-56`.
- Playwright end-to-end (`web/e2e/*.spec.ts`, config `web/playwright.config.ts`): Pixel 7 device profile (`playwright.config.ts:22`), `wrangler dev` on port 8797 (`playwright.config.ts:5`, webServer block below). Selectors pin copy and classes:
  - `legal-and-deletion.spec.ts:49-127`: role and name selectors, `main.page` (:57), "Delete this account?" (:100), "Delete my account" (:104), "Your account is deleted" (:109).
  - `request-and-invite.spec.ts:29-95`: ".hero" (:30), "Pay on paypal.me" (:31), "QR code to pay ₫150,000" (:34), "Copy the details" (:36), "Open in Split Vibe" (:38), ".payment > p:first-child" (:45), "This link no longer works" (:59), "How to join" (:74).
  - `share-link.spec.ts:62` (item button regex), `:110` (`.hero__amount`), `:120` and `:136` (`getByLabel("Your name")`), `:154-156` (`.banner`, `button[aria-pressed]`), `:181-184` (`.person__row`, `.person--you .person__label`, `.claimer`).
- Playwright is not in CI: ci.yml runs only vitest (:156). It runs locally via `npm run e2e` (`web/package.json`; `README.md:437`, `docs/web.md:138`).
- No `toHaveScreenshot` or any pixel test for the web (grep found none).
- Size budget: `web/scripts/size.mjs:8` (150 KB gzipped, initial download, exits 1 over budget). Run only by hand (`npm run size`); not in ci.yml. Release target only: `docs/release-checklist.md:60`.
- Lighthouse: no config or script in the repo. It is a manual, non-blocking release target (`docs/release-checklist.md:58-59`).

### How the pages reach production
- `web/package.json` build: `tsc` typecheck, `vite build` (`web/vite.config.ts:8-12`: one script, one stylesheet under `/assets/`), `scripts/static-pages.mjs`, `scripts/verify-build.mjs`.
- `proxy/scripts/embed-web.mjs` (header :1-10) copies `web/dist` into the generated module `proxy/src/web-assets.generated.ts` (git-ignored; imported by `proxy/src/web.ts:4`). The Worker serves `/s/`, `/r/`, `/g/`, `/delete-account`, `/privacy`, `/terms` from it (embed-web.mjs:2-5). Workers Static Assets are not used (:5-7).
- `proxy/package.json`: `build:web` (:7), `test` (:9), `typecheck` (:10). Proxy tests do not reference the web pages (no hit in `proxy/test`).
- Deploy: `release.yml:305-317` builds the web app into the Worker; `:349` applies D1 migrations; `:354-358` runs `npx wrangler deploy`.
- Consequence: a token change reaches users only through a Worker deploy, which happens only in the release workflow. The Android app ships on its own track.

## 6. Documentation that describes the look or carries screenshots

| File | Section or lines | What it says |
|---|---|---|
| `docs/design-system.md` | :1-12 (intro, screens take no look of their own), :35-58 (colour, tones, contrast), :76-78 (shape), :94-95 (elevation), :128 (keepSizeDuringTransition), :148-164 (component table), :170-182 (catalog and "Adding a component"; `catalog-light/dark.png` at :173), :186-190 (accessibility) | the look itself; the step "record the reference images" (:181) |
| `docs/architecture.md` | :13-14 (diagram), :25 (`tokens.css` edge), :32-33 (module table), :43-53 (theme and token rules), :102 (`PseudoLocaleGalleryTest`), :333-336 (web tokens), :566 (screenshot test row), :571 (device tests row), :582-603 (screenshot section), :626 (verification command), :651-659 (baseline profile) | architecture and test layout |
| `docs/web.md` | :36-39 (tokens from `tokens.css`, written by `WebTokensTest`, do not edit) | web look |
| `docs/testing.md` | :3-12 (what runs where), :109-125 (device test jobs, profile) | no screenshot section (grep for "screenshot" in this file: no hit) |
| `docs/release-checklist.md` | :17-21 (CI green, screenshots), :39-43 (accessibility: 200% text, contrast), :58-60 (Lighthouse and size targets), :91 (screenshots recorded only for intended changes), :137 ("the screenshots") | release gates |
| `docs/release.md` | :49 (ci.yml includes screenshots) | |
| `docs/play-console.md` | :201 ("reference screenshots" for prices) | |
| `docs/store-listing.md` | :83-115 | store screenshots, see section 7 |
| `README.md` | :30 (image `docs/screenshots/new-split.png`), :309 (designsystem description), :337-341 (test and profile folders), :380 (design system), :394-397 (ScreenGalleryTest and record command), :407, :416 (device test commands), :437 (`npm run e2e`) | overview |

## 7. Store listing assets and README images from reference screenshots

- `docs/store-listing.md:85-98`: seven screenshots taken from `app/src/test/screenshots/`:
  - `03-photo-and-instruction.png`, `05-result-top.png`, `06-result-shares.png`, `31-assistant-proposal.png`, `10-requests-sent.png` (824 x 1830, no crop);
  - `23-group.png` (824 x 3200, cropped to 824 x 1830);
  - `40-insights.png` (824 x 4000, cropped to 824 x 1830; its top shows two notices about bills without a rate or date).
  - Dark twins mentioned at :98. Excluded on purpose: `28-bill-link.png` and `29-bill-link-settling.png` (test address and example account data, :113-115).
  - Size figures follow from the qualifiers: PHONE = 412x915 dp at xhdpi = 824 x 1830 (ScreenGalleryTest :477); group 1600 dp = 3200 px; insights WHOLE_PAGE 2000 dp = 4000 px (SpendingGalleryTest :201).
  - The crop is manual: unknown whether the store copies are cropped by a script (none found in repo).
- Play rules (`docs/store-listing.md:102-109`): 824-px images qualify for the listing but not for the recommendations (1080 px wide); a change to 1080 x 1920 would change the screenshot test device size (owner decision).
- README: `README.md:30` uses `docs/screenshots/new-split.png`. Its MD5 differs from `app/src/test/screenshots/02-new-split.png`, so it is not a current copy. Provenance: unknown.
- Play Console uploads: unknown whether the uploaded images match the repo; not checkable from the repo.

## 8. Risks and unknowns

1. Every screen image breaks on any visual change: 93 reference files written by tests plus `catalog-*` are compared. The plan must budget a record run and a review of about 97 images (`-Pscreenshots=record`), plus any new `ComponentCatalogTest` coverage.
2. Bottom navigation bar: `ScreenGalleryTest` scroll constants (:246-265, :281-283, :331-335, constants :488-495) assume the current bar and top bar. A taller or shorter bar, or a bar that overlays content differently, changes where "Adds up to the bill total", "Show what was sent to Claude", "Proposed changes" and BEERS land, and needs re-tuning.
3. Label collisions: a bottom bar with "Settings", "Requests" or "Split" labels would create duplicate matches for `onNodeWithText("Settings")` (AppLaunchTest:42), `onNodeWithText("Requests")` (PaymentRequestJourneyTest:113) and `hasText("Split a bill")`. Duplicates make `onNodeWithText` fail. Check every label listed in section 2 and 3.
4. Renamed labels that tests pin (in addition to section 2 and 3): "Split a bill", "Use the sample bill", "Read and split the bill", "New split", "Recent bills", "Ask for the money", "Done", "Type it in by hand", "Bill photo" (content description; also `Journeys.kt:17`), "Back" and "Settings" content descriptions, "Ask Claude", "Change the split", "Minh paid the whole bill", "Payment code for VietQR", plus the Pro/Settings copy in section 2. The Android device tests and the web Playwright specs (section 5) both pin copy.
5. `DesignSystemBoundaryTest`: new screen-level Material wrappers (NavigationBar wrappers, FAB, DropdownMenu, elevation, animations) fail the build unless they live in `core/designsystem`.
6. `ColorTokensTest` (`core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/ColorTokensTest.kt`): text at 4.5:1 and outlines at 3:1 on every paired background, both palettes (`docs/design-system.md:53-54`). A new palette must pass or the test fails.
7. `WebTokensTest` limits:
   - the elevation levels are read from `SvElevation.entries`, but the dark `darkSurfaces` list has exactly 4 entries (`WebTokensTest.kt:64`); a fifth level would throw `IndexOutOfBounds` in the test;
   - colours, type and spacing are enumerated by hand (`colors()`, `typeScale()`, `spacing()`), so a new token is silently not written to CSS unless the list is extended;
   - the font family is hard-coded as a system stack (:42-43). A bundled font in Android does not reach the web; this is a design decision, not a test failure.
8. Web CSP and source rules: no `style=` attributes and no inline styles in Preact components (source-safety test). A redesign must use classes in `app.css`.
9. Web size budget (150 KB gzipped) is not enforced in CI; a larger font or CSS can grow the initial download without a failing check.
10. Playwright selectors use roles and copy (section 5); a copy change breaks them, but they do not run in CI. Their pass state is unknown on any given commit.
11. Baseline profile: committed `startup-prof.txt` and `baseline-prof.txt` describe the current code; a restructure makes them stale until re-recorded by the nightly job, and a release ships the committed files. Re-record before the release gate.
12. Accessibility checks run only in two device classes (section 3) and only on API 34+. The journeys on API 26, 30 and 36 are measured only, with `continue-on-error`.
13. Screenshot verify mode when a reference is missing: the behaviour of Roborazzi 1.76 for a new file name is not checked in this scout; read the library docs before relying on it.
14. The four unwritten reference files (section 1) may be stale. Whether the redesign still needs them is a plan decision.
15. Per-token-to-per-page map of `app.css` (section 5) is not enumerated.
16. Whether `release.yml` is triggered by a tag and what it approves: not read in full (only lines 6-11, 74-89, 305-358 were checked).
17. Local Chromium path and proxy settings in `web/playwright.config.ts` (:4-6, :13-14) are machine-specific; not relevant to the plan.

## Relevant Files
- `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt` - main screenshot walk, labels, scroll constants.
- `app/src/test/java/com/dantech/splitvibe/ui/{AiConsent,Claim,Groups,Pro,Spending,PseudoLocale,ComponentCatalog}GalleryTest.kt`, `BackNavigationTest.kt`, `export/PdfSummaryTest.kt` - other image writers.
- `app/src/test/java/com/dantech/splitvibe/ui/DesignSystemBoundaryTest.kt` - source-text lint for screens.
- `app/src/test/screenshots/` - 97 reference PNGs.
- `app/src/androidTest/java/com/dantech/splitvibe/AppLaunchTest.kt`, `SplitFlowOnDeviceTest.kt`, `e2e/*.kt` - device tests.
- `.github/workflows/ci.yml`, `device-tests.yml`, `release.yml` - gates.
- `app/build.gradle.kts` (:178-183 screenshot mode, :187-195 managed device, :204-210 lint), `build.gradle.kts` (:19-43 ktlint), `.editorconfig`.
- `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/WebTokensTest.kt`, `ColorTokensTest.kt` - token-to-web and palette tests.
- `core/designsystem/build.gradle.kts` (:25-26) - web token mode.
- `web/src/tokens.css`, `web/src/app.css`, `web/src/main.tsx`, `web/package.json`, `web/vite.config.ts`, `web/playwright.config.ts`, `web/scripts/static-pages.mjs`, `web/scripts/size.mjs`, `web/scripts/verify-build.mjs`, `web/scripts/page-checks.mjs`, `web/tests/*.test.ts(x)`, `web/e2e/*.spec.ts`.
- `proxy/scripts/embed-web.mjs`, `proxy/src/web.ts`, `proxy/package.json`.
- `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt` and the other benchmark files; `app/src/prodRelease/generated/baselineProfiles/*.txt`.
- `docs/design-system.md`, `docs/architecture.md`, `docs/web.md`, `docs/testing.md`, `docs/release-checklist.md`, `docs/release.md`, `docs/store-listing.md`, `docs/play-console.md`, `README.md`, `docs/screenshots/new-split.png`.

## Patterns
- One path renders the real screens twice per run: once in Robolectric for images and state, once on a device for behaviour. Both use text and descriptions as selectors.
- Dark theme and large text are produced by Robolectric qualifiers and a suffix, not by separate test code.
- Tokens flow one way: Kotlin `SvColors`/`SvTypography`/`SvShapes`/`SvSpacing`/`SvElevation` to `WebTokensTest` to `web/src/tokens.css` to `app.css` to the Worker bundle (`embed-web.mjs`) to a release deploy.
- Web pages reject inline style and script by test and by CSP checks, so visual changes go through CSS classes only.

## Risks
See section 8 above.

## Unresolved Questions
- Roborazzi 1.76 behaviour for a missing reference image in verify mode: unknown without its docs.
- Per-page token usage in `app.css`: not enumerated.
- Whether the store listing images in Play match the repo: unknown.
- Whether `docs/screenshots/new-split.png` is generated or hand-made: unknown.
- Exact trigger of `release.yml` (tag or manual): not verified.
