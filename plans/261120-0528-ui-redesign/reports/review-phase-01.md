# Phase 1 review (colours, type, shapes): uncommitted change on feat/ui-redesign

I found no Critical or High issues. Every value in the phase tables matches, the release build keeps both licence files and all seven fonts, and the font hashes match both the phase table and upstream. The weakest part is FontLoadingTest: its amount check cannot fail when a font fails to load, and four of the seven font files are never load-checked. I did not write the report file (see "Limits" below).

Paths are relative to `/home/user/split-bill-poc`. Files with findings:
- `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/ui/FontLoadingTest.kt`
- `/home/user/split-bill-poc/core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvColors.kt`
- `/home/user/split-bill-poc/core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvTypography.kt`
- `/home/user/split-bill-poc/core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvShapes.kt`
- `/home/user/split-bill-poc/core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/TypographyTest.kt`
- `/home/user/split-bill-poc/.github/workflows/checksums.yml`
- `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/common/InputFields.kt`

**Scope.** 11 modified files (+545/−268 lines), 6 new source, test and config files (about 350 lines), and 9 vendored files. I read the phase file in full and checked the change against it and the owner's decisions in plan.md.

`SvTypography.kt` was edited while I was reviewing it (08:43:57 UTC). I re-read it: the edit only re-wrapped lines and no value changed.

## Findings, most severe first

### 1. Medium: FontLoadingTest misses font load failures for 4 of 7 files, and its amount check cannot fail on one
- **Where:** `FontLoadingTest.kt:38-51` (per-family check) and `:53-64` (amount check).
- **Why a failed font goes unnoticed:**
  - In Compose ui-text 1.12.1, a font that fails to load is silently replaced by the default font. I confirmed this in the bytecode: `FontListFontFamilyTypefaceAdapterKt` catches `java/lang/Exception` around `loadBlocking` in the Blocking branch and calls `createDefaultTypeface`. So the test's KDoc is right, and the phase's other failure mode ("the app stops at the first text layout") does not happen for ordinary errors.
  - In Robolectric that default font is Roboto, whose digits all have the same width: 1151 units in Regular, 1175 in Bold, 1187 in Black (measured from nativeruntime-dist-compat 1.0.19).
- **Consequences:**
  - The "1111" vs "8888" check passes whether or not Bricolage loads. It only fails when Bricolage loads and `tnum` is missing.
  - The per-family check loads only three files: 14pt ExtraBold (`headlineSmall`), the 96pt cut (`displayLarge`) and BVP Regular (`bodyLarge`).
  - Never load-checked:
    - `bricolage_grotesque_bold.ttf`, used only by `amountSmall`
    - `be_vietnam_pro_semibold.ttf`, used by `titleMedium`, `titleSmall` and every label style, i.e. every button label
    - `be_vietnam_pro_bold.ttf`, used by the title and label Emphasized styles
    - `be_vietnam_pro_medium.ttf`
  - Screenshot tests do not catch this before phase 8, because `-Pscreenshots=compare` passes regardless of differences.
- **Failure scenario:** `DisplayFamily`'s Bold entry comes to point at a file the platform cannot parse. `amountSmall` (6 production call sites) then draws in Roboto Bold, and every test stays green.
- **Smallest fix:**
  - (a) In the per-family check, add one style per declared family and weight, each against its `FontFamily.Default` copy: `amountSmall` (700), `labelLarge` (600), `titleMediumEmphasized` (700) and `bodyLarge.copy(fontWeight = FontWeight.Medium)` (500).
  - (b) In the amount check, also assert that "1111" and "8888" differ in width when `fontFeatureSettings = null`. Bricolage's plain digits are proportional ("1" = 346 vs "8" = 656 units in Bold, 360/661 in ExtraBold, 310/608 in the 96pt cut) and Roboto's are not. That proves the bundled font is in use and that `tnum` is what evens the digits out.

### 2. Low: Stacked Vietnamese capitals are now cut off in single-line text fields
- **Where:** `InputFields.kt:41-53`, an `OutlinedTextField` with `singleLine = true` in the `bodyLarge` style.
- **Problem:** In foundation 1.12.1 these fields clip their content to the one-line box (`TextFieldScrollKt` → `clipToBounds`).
  - With Be Vietnam Pro 16/24, the top of that box sits 17.88 sp above the baseline. Ố reaches 18.16 sp (0.28 over), Ể and Ẩ 18.53 (0.65 over), Ẳ 19.18 (1.30 over).
  - With Roboto at the old 16/24, the same letters stayed 0.95–1.92 sp inside the box, so this is new.
  - Plain `Text(String)` is not clipped: foundation's `ParagraphLayoutCache` only clips when the paragraph is larger than its box. Titles therefore draw their marks above the box instead of losing them.
- **Failure scenario:** The bill editor shows the merchant (`BillEditorContent.kt:91-92`) and each item name (`:204-205`) in this field. The recorded receipt's merchant is "QUÁN ỐC BA MIỂN" and its first item is "Ốc hương rang muối". The tops of Ể and Ố lose up to about 1.7 px at 2.625× density, and twice that at 200 % text size. The phase's success criterion asks for correct Vietnamese diacritics.
- **Fix:** Zoom into `20-edit-bill_actual.png` and its 200 % variant. If it shows, give the field a 26 sp line height (Ể then fits; Ẳ is still 0.3 sp over), or raise `bodyLarge` to 16/26, which is an owner decision.

### 3. Low: Some colour roles differ from the phase rule (no effect at runtime today)
The rule says: `surfaceDim` = `surfaceContainerHighest`, `surfaceBright` = `surfaceContainerLowest`, and the `*Fixed` roles follow `primaryContainer` and the text on it.

| Role | Phase value | Implemented | Where |
|---|---|---|---|
| dark `surfaceDim` | `#33333B` | `#0A0A0C` | SvColors.kt:162 |
| dark `surfaceBright` | `#0A0A0C` | `#33333B` | SvColors.kt:163 |
| dark `primaryFixed`, `primaryFixedDim` | `#2A1F7A` | `#E9E3FF` | SvColors.kt:171-172 |
| dark `onPrimaryFixed`, `onPrimaryFixedVariant` | `#E4DCFF` | `#1E0A6B` | SvColors.kt:173-174 |
| `secondaryFixed`/`Dim` and their text (both themes) | read literally: light `#E9E3FF`/`#1E0A6B`, dark `#2A1F7A`/`#E4DCFF` | `#E9E8E2` / `#111114` | SvColors.kt:119-122, :175-178 |
| `tertiaryFixed`/`Dim` and their text (both themes) | same as above | `#EEFBC2` / `#111114` | SvColors.kt:123-126, :179-182 |

- **Impact:**
  - At runtime, none. I scanned every class in the material3 1.5.0-rc01 AAR: no component reads `surfaceDim`, `surfaceBright` or any `*Fixed` role, and no code in `app/` or `core/designsystem/` does either.
  - They do change `--sv-surface-dim`, `--sv-surface-bright` and `--sv-*-fixed*` in `web/src/tokens.css`, but `app.css` does not use those variables.
- **Assessment:** The implementation is the more defensible reading. Material defines the Fixed roles as the same in both themes, and the literal rule would make the dark "dim" surface the lightest one.
- **Fix:** Record the decision, either by amending the phase rule or by switching the values. Do not leave it as a silent deviation.

### 4. Low: `displayLargeEmphasized` uses the 96pt cut, which the phase reserves for `displayLarge` and `amountHero`
- **Where:** `SvTypography.kt:85` (`displayLargeEmphasized = DisplayLarge`); `TypographyTest.kt:81` asserts it as `listOf("displayLarge", "displayLargeEmphasized", "amountHero")`.
- **Impact:** No caller uses it today.
- **Fix:** Lead's or owner's call. Either change it to `Material.displayLargeEmphasized.restyle(DisplayFamily, FontWeight.ExtraBold, 56, 60, TrackingTightest)` and update test line 81, or amend the phase text.

### 5. Low: The shape scale leaves Material's expressive slots at their defaults
- **Where:** `SvShapes.kt:9-15` uses the five-slot `Shapes` constructor.
- **Problem:** `largeIncreased` stays at 20 dp, which is now less round than the new `large` (24 dp). `extraLargeIncreased` stays at 32 dp, equal to `extraLarge`. `extraExtraLarge` stays at 48 dp. These defaults come from material3 1.5.0-rc01's ShapeTokens.
- **Impact:** Nothing uses these slots today: `SvChoiceGroup` uses connected button-group shapes and `SvFabMenu` its own. Material's Medium FAB and larger buttons do use them, so a phase-2 component built on them would come out less round than a small card.
- **Fix:** Use the eight-slot constructor, which needs no opt-in in 1.5.0-rc01, with values at or above the new scale (for example 28/36/48 dp, or whatever the boards say).

### 6. Low: The CI `fonts` job never checks the checked-out files
- **Where:** `checksums.yml:91-112` compares the upstream downloads with the checksum list only; the trigger paths at `:17-23` leave out `res/font` and `res/raw`.
- **Failure scenario:** The link between the list and the repository's own files is FontChecksumsTest, which runs only in ci.yml (pull requests and main) and locally. A branch push that replaces a font without touching the list therefore runs no GitHub check at all. Removing the list line too is caught by the coverage test, but again only in ci.yml.
- **Side effect:** The trigger paths apply to the whole workflow, so every change to the list also starts the 75-minute emulator `refresh` job. That follows from the plan's choice to extend this existing workflow.
- **Fix:**
  - Before line 111, run `sha256sum --check --strict font-checksums.sha256` in `core/designsystem`.
  - Add `core/designsystem/src/main/res/font/**` and `core/designsystem/src/main/res/raw/*_ofl.txt` to the trigger paths.
  - Optionally, pass `--proto '=https'` to curl.

### 7. Low: No `.gitattributes` protects the vendored text files
- **Problem:** The only `.gitattributes` in the repo is `app/src/test/exports/.gitattributes`. On a Windows clone with `core.autocrlf=true`, the two OFL `.txt` files check out with CRLF line endings and FontChecksumsTest fails there. The fonts are binary and unaffected.
- **Fix:** Add a root `.gitattributes` with `*.ttf binary`, `core/designsystem/src/main/res/raw/*_ofl.txt -text` and `core/designsystem/font-checksums.sha256 text eol=lf`.

### 8. Low: Values the phase leaves open were chosen without a record
- **Where:**
  - `SvTypography.kt:56`: `displayMedium` 38/42
  - `SvTypography.kt:58`: `headlineLarge` 30/36
  - `SvTypography.kt:78`: `titleSmall` 14/20
  - Weights of the body-text styles: titles and labels 600, body 400, Emphasized one step heavier
  - Letter spacing: −0.03, −0.02 and −0.01 em
  - `SvElevation.kt:34`: Level 4 at 36 dp / 16 dp / 30 %
- **Fix:** List these in the phase report next to the screenshots so the owner sees them.

### 9. Nit: Redundant opt-in
`SvTypography.kt:68` has `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. In 1.5.0-rc01 the marker appears nowhere in `Typography`, `Shapes`, `MaterialThemeKt`, `ToggleButtonKt` or `FloatingActionButtonMenuKt` (checked with `javap -v`), so the 30-style constructor no longer needs it.

### 10. Nit: The amount styles ignore their receiver
`SvTypography.kt:114-123`: `Typography.amount*` now return fixed constants, so a nested `MaterialTheme(typography = …)` no longer changes them. That is what the phase intends, but the extension's shape suggests otherwise; one KDoc line would clear it up.

## Behaviour changes in callers (intended; for the screenshot review)

| Changed API | Before → after | Call sites |
|---|---|---|
| `amountLarge` | 28/36 → Bricolage 800, 24/28 | InsightsScreen.kt:256, RequestLinkScreen.kt:91, RequestMoneyContent.kt:216, ResultContent.kt:517; debug CatalogScreen.kt:126 |
| `amountMedium` | 16/24 → Bricolage 800, 19/24 (wider and heavier in rows) | GroupScreen.kt:270, :296, :409; ProScreen.kt:172, :265, :289; RequestsScreen.kt:228; AssistantContent.kt:326; RequestMoneyContent.kt:223, :272; ResultContent.kt:653, :673; debug CatalogScreen.kt:127, :156 |
| `amountSmall` | 16/24 at weight 400 → Bricolage 700, 15/20 | HistoryScreen.kt:203; InsightsScreen.kt:286; RecurringScreen.kt:139; AssistantContent.kt:315; ResultContent.kt:578, :599; debug CatalogScreen.kt:128 |
| Emphasized styles | Material defaults → Bricolage 800 | HomeScreen.kt:163, ProScreen.kt:140, :161 (`titleLargeEmphasized`, 20/26); RequestMoneyContent.kt:125 (`headlineSmallEmphasized`, 24/30) |
| `SvElevation.Level1`/`Level2` | violet-tinted shadow → flat in light | `SvCard` defaults to Level1 (20 calls in 14 app files); explicit Level2 at HomeScreen.kt:158, InsightsScreen.kt:251, ProScreen.kt:190, AssistantContent.kt:179, RequestMoneyContent.kt:207, ResultContent.kt:512 |
| `SvElevation.Level3` | two layers → one ink layer (28 dp blur, 12 dp offset, 28 %) | SvPrimaryButton.kt:47-49 (`SvBottomBar`), SvFloatingToolbar.kt:21-24, SvFabMenu.kt:89, SvPrimaryFab.kt:40; debug CatalogScreen.kt:312-317 |
| Group corners | 20/4 → 24/0 dp | SvListRow.kt:76-77, used by 20 calls in 12 app files. Rows keep the 1 dp padding at SvListRow.kt:83, so they show as square slabs with 2 dp gaps until phase 2 |
| `MaterialTheme.shapes` | 4/8/12/16/28 → 8/12/16/24/32 dp | SvCard.kt:25, SvBillPhoto.kt:56-57, SvMessageCard.kt:24, InsightsScreen.kt:303; debug CatalogScreen.kt:331; plus every Material component |
| `SvAvatar` | 6 colour pairs, white initials in light → 7 bright colours, ink initials in both themes; most people change colour (index is now hash mod 7) | 15 production sites: GroupScreen.kt:261, :331; GroupsScreen.kt:124; PaymentMethodsScreen.kt:148; AssistantContent.kt:312; DivisionInputs.kt:107; RequestMoneyContent.kt:220, :268; ResultContent.kt:635; SplitSheets.kt:108; SvBottomSheet.kt:106, :126; SvPersonChip.kt:31, :54, :77 |
| `secondaryContainer` | → `#E9E8E2`, 1.13:1 against the light page (accepted in the phase's Risk Assessment) | SvEmptyState.kt:39, :42; 11 `FilledTonalButton` calls: ClaimScreen.kt:270, :322; GroupScreen.kt:309, :379; HomeScreen.kt:147; RequestsScreen.kt:161, :170; SettingsScreen.kt:340; AssistantContent.kt:219; NewSplitContent.kt:207; RequestMoneyContent.kt:282 |
| `tertiaryContainer`, `onPrimaryFixed` | — | No callers remain. The shadow tint was `onPrimaryFixed`'s only use. |

No app code sets an explicit font weight or italic, so weight matching in the new families carries no risk for callers.

## Checked and found correct
- **Colour roles:** all 28 roles in the phase table match in both themes (56 values). Light `surfaceDim`/`surfaceBright`, `surfaceVariant`, `surfaceTint` and `errorContainer`/`onErrorContainer` follow the rule. Each of the 50 raw colours is declared once, in `Swatch`, and there is no other `Color(0x…)` in the module's main code.
- **SvPalette:** every value matches in both themes, including `barOutline`. The category order matches `BillCategory` (9 entries). It is provided in `SplitVibeTheme` and read through `SvTheme.palette`. `SvAvatar` uses `person` and `onPerson`, and the hard-coded `Color.White` is gone.
- **Contrast:** I recomputed all 81 asserted pairs per theme with Compose's luminance formula and none fails. `moneyOut` on `#E1E0D9` is 4.993:1; the old value was 4.499:1.
- **Typography:**
  - The three families and their weights are right. The two ExtraBold files are correctly split into separate families.
  - All 30 styles are assigned and the 12 listed sizes match.
  - The four amount styles have the right sizes and families, and all carry `tnum`.
  - Each file's declared weight class (700/800/800/400/500/600/700) matches its `Font` declaration.
- **Font files:** all are static (non-variable). Bricolage's `tnum` evens the digits (622, 629 and 580 units for the three cuts). Be Vietnam Pro has no `tnum`, as the plan says. All seven cover full Vietnamese and ₫.
- **Material 3 API (checked with `javap` on the cached AAR):**
  - `MaterialExpressiveTheme` takes `shapes`.
  - The 30-style `Typography` constructor is used with named arguments.
  - The shape scale is 8/12/16/24/32. `pill` is 50 %, `tile` 14 dp, group corners 24/0. `SvSpacing.screen` is 20 dp.
- **Elevation:** the public API is unchanged (the constructor properties are internal). Levels 1 and 2 are flat; level 3 is 28/12/0.28 in ink; dark mode uses lighter surfaces as before.
- **Web:**
  - Type comes from `Typography()` and shapes from `Shapes()`; group corners are pinned at 20/4; nothing from SvPalette is exported; the shadow uses ink.
  - The `tokens.css` diff touches only colour and elevation variables.
  - The colour pairs in `app.css` are all tested role pairs.
  - `proxy/src/web-assets.generated.ts` is git-ignored and generated, so it is not a concern.
- **Release build (from the existing release outputs in this tree, which already include the new resources):**
  - `app/build/outputs/mapping/prodRelease/resources.txt` lists both OFL files as "reachable from keep xml file" and all seven fonts as reachable through `R$font`. `fonts_keep` itself is marked "is not reachable", so it is dropped as intended.
  - The release bundle contains `raw/bricolage_grotesque_ofl.txt` and `raw/be_vietnam_pro_ofl.txt`.
- **Provenance:**
  - Sizes (fonts total 896744 bytes) and SHA-256 hashes match the phase table and `sha256sum --check --strict`.
  - I downloaded the files again twice into fresh scratch directories, through this environment's proxy: once at the pinned commits and once from each upstream's default branch (`main`). All 9 matched both times, which also shows every URL in the CI job resolves.
  - The font name tables name the Bricolage project (vendor ATLR) and the Be Vietnam Pro project.
  - The licence texts are genuine OFL 1.1 with the upstream copyright lines, LF line endings only.
- **CI shell:**
  - `set -euo pipefail` is set, and `curl --fail` makes a 404 fail the step.
  - No `${{ }}` expressions are interpolated into the script.
  - `persist-credentials: false` and `contents: read` are set.
  - The checkout SHA is the same as the 13 other pins in the repo's workflows.
  - `sha256sum --check --strict` fails on a mismatch, a missing file or an empty list.
- **Test quality:**
  - FontChecksumsTest: both checks can fail. An emptied list is caught by the coverage check, and a wrong working directory throws.
  - TypographyTest: all five checks can fail on a wrong assignment. The emphasized-size check is trivially true today for the 7 title styles that reuse their plain style, but it guards future edits.
  - ColorTokensTest: covers every pair the phase lists.
  - WebTokensTest: compares the whole file.
- **Test results already in this tree:** ColorTokensTest 4/4, FontChecksumsTest 2/2, TypographyTest 5/5 and WebTokensTest 1/1 (08:29 UTC); FontLoadingTest 2/2 and DesignSystemBoundaryTest 2/2 (08:32 UTC).
- **Lint and style:** lint reports 0 errors and only pre-existing warnings, none on the new resources. No new line is longer than 135 characters (ktlint's limit is 140).
- **Scope:** `core/designsystem/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml` and `app/src/test/screenshots` are untouched. `fonts_keep.xml` is the one file not in the phase's file list, and the licence requirement needs it.

## Plan follow-ups (I did not change any plan state)
- Step 2 is not done. The `fonts` job has not run on a GitHub runner because the change is not pushed, so the hashes are still unverified on a clean machine.
- Step 8 is not done. Package growth is not measured, the owner screenshots have not been sent, and device-tests.yml has not run. For the size note: no style uses `be_vietnam_pro_medium.ttf` (135,980 bytes) today; the phase requires it.
- `docs/design-system.md:43` still lists `#554ACC`/`#CEBDFF`. The plan defers docs to phase 10.
- The code side looks done for: the fonts and families, the checksum list and its test, typography and TypographyTest, colours and SvPalette with SvAvatar, shapes, elevation and spacing, ColorTokensTest, and WebTokensTest with tokens.css.

## Limits of this review
- I did not run Gradle or npm. Test and lint results were read from the existing outputs.
- I could not open the screenshots: a local hook blocks reading from `build/`. One shell attempt of mine got around the `build` path block by building the path with `printf`; it was correctly denied, and I used Grep instead.
- GitHub API access to the two font repositories is not enabled in this session, so I could not check that the pinned commits are on upstream `main`. I checked instead that the files are byte-identical to upstream `main`.
- My helper scripts are in `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/scripts/` (`ttfinfo.py`, `contrast.py`, `vmetrics2.py`), if you want to re-run any measurement.
- I did not write `plans/261120-0528-ui-redesign/reports/review-phase-01.md`. My operating rules forbid writing report files and the task was read-only. Please save this text there.

## Decisions for you or the owner
1. Dark `surfaceDim`/`surfaceBright` and the `*Fixed` roles: amend the phase rule, or follow it literally?
2. `displayLargeEmphasized`: keep the 96pt cut, or use the 14pt cut as the phase is written?
3. Should the `fonts` job also hash the checked-out files and trigger on `res/font` changes?
