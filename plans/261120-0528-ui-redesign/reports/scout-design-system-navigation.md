# Scout report: design system and navigation (Split Vibe)

Read-only scouting. No build or Gradle run. Line numbers refer to the working tree at branch `feat/ui-redesign`. Paths are relative to `/home/user/split-bill-poc`.

## 1. Module tree of core/designsystem

Module: `core/designsystem/build.gradle.kts` (41 lines). Namespace `com.dantech.splitvibe.designsystem`. Exposes Compose BOM, `compose.ui` and `compose.material3` as `api`. Test system property `sv.webTokens` at lines 23-28.

Base path: `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/`

| File | Purpose | Lines |
|---|---|---|
| `theme/SplitVibeTheme.kt` | `LocalDarkTheme`, `SplitVibeTheme()` wrapper | 29 |
| `tokens/SvColors.kt` | `LightColors`, `DarkColors` (`lightColorScheme`/`darkColorScheme`) | 98 |
| `tokens/SvTypography.kt` | `SvTypography`, `amountLarge/Medium/Small` | 14 |
| `tokens/SvSpacing.kt` | `SvSpacing` xs..xxl | 11 |
| `tokens/SvShapes.kt` | `SvShapes.groupOuterCorner` / `groupInnerCorner` | 12 |
| `tokens/SvElevation.kt` | `SvElevation` enum L1-L4, `surface`, `Modifier.elevation` | 69 |
| `motion/SvMotion.kt` | `screenChange`, `stepFadeOut`, `stepFadeIn` | 33 |
| `motion/SharedSurface.kt` | `SvSharedTransition`, `sharedSurface`, `keepSizeDuringTransition` | 52 |
| `motion/SvSteps.kt` | `SvSteps` (step-by-step flow host) | 40 |
| `motion/ReducedMotion.kt` | `LocalReducedMotion`, `rememberReducedMotion` | 36 |
| `motion/RowMotion.kt` | `LazyItemScope.rowMotion()` | 16 |
| `motion/Bounce.kt` | `bounceOnChange`, `popIn` | 57 |
| `motion/StaggeredEntry.kt` | `SvEntryState`, `rememberSvEntryState`, `staggeredEntry` | 56 |
| `motion/SvAmount.kt` | `SvAmount` count-up / run-to-value amount | 61 |
| `components/SvAvatar.kt` | `SvAvatar`, `initials`, six person colours | 63 |
| `components/SvBillPhoto.kt` | `SvBillPhoto` | 112 |
| `components/SvBottomSheet.kt` | `SvBottomSheet`, `SvPersonPickRow`, `SvPersonChoiceRow` | 129 |
| `components/SvCard.kt` | `SvCard` | 30 |
| `components/SvChoiceGroup.kt` | `SvChoiceGroup`, `SvChoiceChips` | 65 |
| `components/SvEmptyState.kt` | `SvEmptyState` | 50 |
| `components/SvFabMenu.kt` | `SvFabMenu`, `SvFabMenuItem` | 112 |
| `components/SvFloatingToolbar.kt` | `SvFloatingToolbar` | 30 |
| `components/SvListRow.kt` | `SvListRow` (x2), `SvRowPosition` | 87 |
| `components/SvLoadingIndicator.kt` | `SvLoadingIndicator` | 19 |
| `components/SvMessageCard.kt` | `SvMessageCard` | 33 |
| `components/SvPersonChip.kt` | `SvPersonChip` (3 overloads) | 77 |
| `components/SvPrimaryButton.kt` | `SvPrimaryButton`, `SvBottomBar` | 57 |
| `components/SvPrimaryFab.kt` | `SvPrimaryFab` | 51 |
| `components/SvSectionHeader.kt` | `SvSectionHeader` | 18 |
| `components/SvSplitButton.kt` | `SvSplitButton`, `SvMenuOption`, `SvOverflowMenu` | 96 |
| `components/SvSuccessMark.kt` | `SvSuccessMark` | 66 |
| `components/SvTopBar.kt` | `SvLargeTopBar` | 18 |
| `components/SvWorkingRow.kt` | `SvWorkingRow` | 39 |

Source total: 33 main files. Tests: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/ColorTokensTest.kt` (65), `.../tokens/WebTokensTest.kt` (175).

Note: `SvBottomBar` lives in `SvPrimaryButton.kt:46`, `SvPersonPickRow` in `SvBottomSheet.kt:96`, `SvPersonChoiceRow` in `SvBottomSheet.kt:122`, `SvChoiceChips` in `SvChoiceGroup.kt:54`, `SvOverflowMenu` in `SvSplitButton.kt:70`. There is no `SvBottomBar.kt` or `SvPersonPickRow.kt`.

## 2. Tokens

### Colour
- Light scheme: `tokens/SvColors.kt:12-56` (`internal val LightColors`). Primary `#554ACC` at line 13.
- Dark scheme: `tokens/SvColors.kt:58-102` (`internal val DarkColors`). Primary `#CEBDFF` at line 59.
- Roles map directly onto Material's `ColorScheme` slots: every slot is set, including `surfaceContainerLowest`..`surfaceContainerHighest` (light 38-42, dark 84-88) and the fixed-colour family (light 44-55, dark 90-101). No custom role names exist; screens read `MaterialTheme.colorScheme.*`.
- Elevation surfaces: `tokens/SvElevation.kt:40-52` (`SvElevation.surface`). Light: always `surfaceContainerLowest`. Dark: L1 `surfaceContainerLow`, L2 `surfaceContainer`, L3 `surfaceContainerHigh`, L4 `surfaceContainerHighest`.
- Person colours (not tokens): six `PersonColor(light, dark, onDark)` in `components/SvAvatar.kt:57-68`, selected by `name.hashCode()` (line 26). `Color.White` is hard-coded at `SvAvatar.kt:41`.
- Shadow tint: `onPrimaryFixed` (`SvElevation.kt:66`, `#000F5D` light).

### Typography
- Base: `internal val SvTypography = Typography()` at `tokens/SvTypography.kt:7`. Material's default scale, no custom font, no custom sizes.
- Amount styles (extension properties on `Typography`): `amountLarge` = `headlineMediumEmphasized` (line 13), `amountMedium` = `titleMediumEmphasized` (line 16), `amountSmall` = `bodyLarge` (line 19). All go through `withEqualWidthDigits()` (line 21) which sets `fontFeatureSettings = "tnum"`.
- Font family: none. No `.ttf`, `.otf`, `.woff` files under `app/src` or `core/designsystem/src`; no `res/font` directory (`app/src/main/res` has only drawable, drawable-nodpi, mipmap-anydpi-v26, values, xml). The only font reference in app code is `ui/split/ResultContent.kt:387` (`FontFamily.Monospace`), set by a screen, not by a token.
- Web type uses `--sv-font-family` hard-coded at `WebTokensTest.kt:43` (system-ui stack, with Roboto).

### Spacing
- `tokens/SvSpacing.kt:7-12`: `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32 dp.

### Shapes
- Material shapes are used as they are. `MaterialExpressiveTheme` is not given a `shapes` argument (`theme/SplitVibeTheme.kt:25-30`), so Material defaults apply.
- App-own corners: `tokens/SvShapes.kt:11` (`groupOuterCorner` 20 dp), `:14` (`groupInnerCorner` 4 dp). Used by `components/SvListRow.kt:76-84`.
- Component shape defaults: `SvCard.kt:25` (`MaterialTheme.shapes.extraLarge`), `SvMessageCard.kt:24` (`large`), `SvBillPhoto.kt:56-57` (`extraLarge`/`medium`), `SvFabMenu.kt:114-115` (fixed 16/28 dp rounded squares), `SvFloatingToolbar.kt:26` (`CircleShape`), `SvSuccessMark.kt:52` (`CircleShape`).

### Elevation
- `tokens/SvElevation.kt:23-35`: enum `Level1..Level4`, each with an ambient and key `ShadowLayer` (radius, offsetY, alpha). Level1 6/0/0.06 and 2/1/0.10 dp; Level2 12/0/0.07 and 4/2/0.12; Level3 20/0/0.08 and 8/4/0.14; Level4 32/0/0.10 and 12/8/0.16.
- `Modifier.elevation(level, shape, strength)` at `SvElevation.kt:63-70`. It returns `this` in dark theme (line 64, reads `LocalDarkTheme`). `shadowLayer` at lines 72-78 draws with `dropShadow`.
- `strength` animates only opacity and offset (doc at lines 57-60).

### Motion
- `tokens/` has no motion tokens. Motion lives in `motion/SvMotion.kt`:
  - `screenChange()` tween 400 ms, `FastOutSlowInEasing` (lines 20, 37).
  - `stepFadeOut()` 140 ms `FastOutLinearInEasing` (lines 28, 38).
  - `stepFadeIn()` duration 260 ms with 140 ms delay (lines 31-35).
- Material motion scheme: `MotionScheme.expressive()` at `theme/SplitVibeTheme.kt:27`. Component specs read `MaterialTheme.motionScheme.*` (e.g. `SvPrimaryFab.kt:34`, `SvWorkingRow.kt:27`, `StaggeredEntry.kt:45`, `RowMotion.kt:14-18`).
- Other constants: `StaggeredEntry.kt:58-61` (RISE 24 dp, STEP 40 ms, 10 rows, entrance window 700 ms); `Bounce.kt:61-62` (pressed 0.85, arriving 0.6); `SvBillPhoto.kt:112-117`; `SvSuccessMark.kt:69-71`; `SvFabMenu.kt:111-117`.

## 3. Sv components

Call-site counts: `Sv<Name>(` or `Sv<Name> {` occurrences under `app/src/main/java/com/dantech/splitvibe/ui` and `app/src/debug/java/com/dantech/splitvibe/catalog`. Counts are textual and include the debug catalog. Declarations are not counted. Format: total (per package).

| Component | File:line | Parameters (brief) | Material wrapped | Call sites |
|---|---|---|---|---|
| `SvLargeTopBar` | `components/SvTopBar.kt:14` | title, scrollBehavior, actions | `LargeFlexibleTopAppBar` | 2 (catalog 1, home 1) |
| `SvCard` | `components/SvCard.kt:22` | modifier, elevation=L1, shape, color, content | `Surface` | 21 (catalog 1, claim 2, editor 1, groups 3, home 1, insights 1, paywall 3, requests 3, settings 1, split 5) |
| `SvSectionHeader` | `components/SvSectionHeader.kt:13` | title, hint | `Text` (no Material container) | 32 (catalog 13, claim 2, editor 4, groups 5, insights 1, requests 1, split 6) |
| `SvListRow` | `components/SvListRow.kt:46`, `:58` | position, [label, onClickLabel, onClick], content | `Surface` + `Row` | 22 (catalog 2, claim 2, editor 2, groups 4, history 1, insights 1, payments 1, recurring 1, requests 1, settings 1, split 6) |
| `SvAvatar` | `components/SvAvatar.kt:25` | name, size=40 dp | `Text` in `Box` | 11 (catalog 1, groups 3, payments 1, split 6) |
| `SvMessageCard` | `components/SvMessageCard.kt:20` | text, isError, action, onAction | `Surface` + `TextButton` | 40 (catalog 2, claim 4, groups 10, insights 2, paywall 7, requests 4, settings 1, split 10) |
| `SvLoadingIndicator` | `components/SvLoadingIndicator.kt:15` | modifier | `LoadingIndicator` (expressive, opt-in at line 13) | 14 (catalog 1, claim 1, groups 4, history 1, insights 1, paywall 2, recurring 1, requests 2, settings 1) |
| `SvWorkingRow` | `components/SvWorkingRow.kt:26` | status, cancelLabel, onCancel | `AnimatedContent`, `TextButton` | 3 (catalog 1, split 2) |
| `SvSuccessMark` | `components/SvSuccessMark.kt:33` | icon | none (Box + Icon) | 2 (catalog 1, split 1) |
| `SvBillPhoto` | `components/SvBillPhoto.kt:45` | photo, contentDescription, scanning | `Image` | 2 (catalog 1, split 1) |
| `SvPrimaryButton` | `components/SvPrimaryButton.kt:29` | text, onClick, enabled | `Button` | 23 (catalog 3, claim 2, editor 1, groups 1, insights 1, paywall 2, requests 1, settings 3, split 9) |
| `SvBottomBar` | `components/SvPrimaryButton.kt:46` | content (ColumnScope) | `Surface` (RectangleShape, Level3) | 9 (catalog 1, claim 1, editor 1, insights 1, paywall 1, split 4) |
| `SvPrimaryFab` | `components/SvPrimaryFab.kt:27` | text, icon, onClick | `ExtendedFloatingActionButton` (elevation zeroed) | 4 (catalog 1, groups 1, home 1, payments 1) |
| `SvFabMenu` | `components/SvFabMenu.kt:46` | expanded, onExpandedChange, label, icon, items | `FloatingActionButtonMenu` + own button | 1 (catalog 1) |
| `SvFabMenuItem` | `components/SvFabMenu.kt:35` | label, icon, onClick (class) | n/a | 2 (catalog 2) |
| `SvFloatingToolbar` | `components/SvFloatingToolbar.kt:20` | content (RowScope) | `HorizontalFloatingToolbar` | 1 (catalog 1) |
| `SvSplitButton` | `components/SvSplitButton.kt:39` | text, onClick, moreLabel, options | `SplitButtonLayout` | 7 (catalog 1, claim 3, split 3) |
| `SvMenuOption` | `components/SvSplitButton.kt:31` | label, onClick (class) | n/a | 35 (catalog 3, claim 10, groups 6, history 2, payments 2, recurring 3, requests 5, split 4) |
| `SvOverflowMenu` | `components/SvSplitButton.kt:70` | label, options | `IconButton` + `DropdownMenu` | 12 (catalog 1, claim 3, groups 3, history 1, payments 1, recurring 1, requests 2) |
| `SvChoiceGroup` | `components/SvChoiceGroup.kt:24` | options, selected, onSelect | `ToggleButton` (connected) | 5 (catalog 1, insights 2, recurring 1, split 1) |
| `SvChoiceChips` | `components/SvChoiceGroup.kt:54` | options, selected, onSelect | `FilterChip` in `FlowRow` | 6 (catalog 1, editor 1, groups 1, payments 1, split 2) |
| `SvPersonChip` | `components/SvPersonChip.kt:25`, `:41`, `:65` | name + (selected, onClick) / (removeLabel, onRemove) / (onClick, onClickLabel) | `FilterChip`, `InputChip`, `AssistChip` | 6 (catalog 4, split 2) |
| `SvPersonPickRow` | `components/SvBottomSheet.kt:96` | name, checked, onCheckedChange, detail | `ListItem` + `Checkbox` | 3 (catalog 2, split 1) |
| `SvPersonChoiceRow` | `components/SvBottomSheet.kt:122` | name, selected, onSelect, detail | `ListItem` + `RadioButton` | 6 (catalog 1, groups 2, payments 1, split 2) |
| `SvBottomSheet` | `components/SvBottomSheet.kt:41` | title, onDismiss, actions, supportingText, content | `ModalBottomSheet` | 13 (catalog 1, groups 2, insights 1, payments 1, recurring 1, requests 1, split 6) |
| `SvEmptyState` | `components/SvEmptyState.kt:28` | icon, text | `Column` + `Icon` | 9 (catalog 1, groups 1, history 1, home 1, insights 2, payments 1, recurring 1, requests 1) |

Not in the table of `docs/design-system.md` (reported separately): `SvSteps` (`motion/SvSteps.kt:26`, call site split 1, `ui/split/SplitScreen.kt:135`), `SvAmount` (`motion/SvAmount.kt:31`, catalog 4, insights 1, split 3), `SvSharedTransition` (class, `motion/SharedSurface.kt:17`, used only at `ui/AppNavHost.kt:549`).

Docs table at `docs/design-system.md:145-167` does not list `SvChoiceChips`, `SvPersonChoiceRow`, `SvOverflowMenu`, `SvFabMenuItem`/`SvMenuOption`, `SvSteps`, `SvAmount`. Check before a catalog update.

## 4. Theme wiring

- `theme/SplitVibeTheme.kt:23`: `fun SplitVibeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)`.
- Line 24: `CompositionLocalProvider(LocalDarkTheme provides darkTheme, LocalReducedMotion provides rememberReducedMotion())`.
- Lines 25-30: `MaterialExpressiveTheme(colorScheme = if (darkTheme) DarkColors else LightColors, motionScheme = MotionScheme.expressive(), typography = SvTypography, content)`. No `shapes` argument.
- `LocalDarkTheme` defined at line 16 (`staticCompositionLocalOf { false }`), read by `SvElevation.kt:45,64`, `SvAvatar.kt:27`.
- Callers: `MainActivity.kt:49` (`SplitVibeTheme { AppNavHost(...) }`), `debug/.../catalog/CatalogActivity.kt:15`, and tests. `ReducedMotionTest.kt:146` and `ComponentCatalogTest.kt:34` (catalog dark, `darkTheme = true`).
- Dark theme: follows the system unless the parameter is passed. No per-app setting exists in the theme.
- Dynamic colour: none. No `dynamicLightColorScheme`/`dynamicDarkColorScheme` anywhere in `app/src` or `core/designsystem/src`.
- Activity enables edge-to-edge at `MainActivity.kt:34`.

## 5. Guard tests

### ColorTokensTest
File: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/ColorTokensTest.kt`.
- Tests: light (line 16) and dark (line 19) palettes.
- Surfaces checked (lines 22-29): surface, surfaceContainerLowest, Low, Container, High, Highest.
- Text pairs (lines 30-53), threshold 4.5:1 (`TEXT_CONTRAST`, line 72): onPrimary/primary, onPrimaryContainer/primaryContainer, onSecondary/secondary, onSecondaryContainer/secondaryContainer, onTertiary/tertiary, onTertiaryContainer/tertiaryContainer, onError/error, onErrorContainer/errorContainer, onBackground/background, inverseOnSurface/inverseSurface, inversePrimary/inverseSurface, onPrimaryFixed/primaryFixed, onPrimaryFixedVariant/primaryFixedDim, onSecondaryFixedVariant/secondaryFixedDim, onTertiaryFixedVariant/tertiaryFixedDim; and per surface onSurface, onSurfaceVariant, primary, error.
- Outlines (lines 59-62): `outline` on each surface, threshold 3:1 (`OUTLINE_CONTRAST`, line 73).
- Contrast formula: lines 66-69 (WCAG luminance).
- Not checked: `outlineVariant`, `SvAvatar` person colours (comment claims >6:1 at `SvAvatar.kt:59-60`), `tertiaryContainer` as text-on-surface, shadows, web CSS.

### WebTokensTest
File: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/WebTokensTest.kt`.
- Target file: `TOKENS_FILE = "../../web/src/tokens.css"` (line 185), relative to module working dir.
- Test `the web pages use the app's tokens` (lines 25-36): when system property `sv.webTokens` == `write` it writes the file (lines 29-33); otherwise it asserts equality (line 35).
- Generated content (`webTokensCss`, lines 38-71): `color-scheme`, `--sv-font-family` (line 43, hard-coded), light colours via `colors(LightColors)` (line 44, list at 73-124), type scale via `typeScale()` for `SvTypography` (lines 45-50, list 126-144), shapes (line 51, `shapes()` lines 147-157), spacing (line 52, lines 159-166), elevation (lines 53-57, shadow strings 171-175), dark media query (lines 61-70, dark colours line 63, dark elevation `none`).
- Risk: `shapes()` uses `Shapes()` default (line 147), not the theme's shapes. If the redesign sets shapes in `SplitVibeTheme`, the web file will keep the default values and the test will still pass.
- Risk: the dark block writes only colours and elevation; type and shape are light-only.
- `hex()` line 177, `px()` 179, `shadow()` 171 (tint `LightColors.onPrimaryFixed`, line 53).

### DesignSystemBoundaryTest
File: `app/src/test/java/com/dantech/splitvibe/ui/DesignSystemBoundaryTest.kt`.
- Test 1 (lines 15-27): scans `src/main/java` and `src/debug/java` under the app module (line 115). Requires at least 20 `.kt` files (`MIN_SOURCE_FILES`, line 116, check at line 18). Reports `path:line sets <what>`.
- Test 2 (lines 30-74): self-test of the regex rules with forbidden and allowed sample lines.
- Forbidden (`FORBIDDEN` list, lines 84-112):
  - "its own theme": `MaterialTheme(`/`MaterialExpressiveTheme(` (line 86), `SplitVibeTheme(` allowed only in `MainActivity.kt` and `CatalogActivity.kt` (line 87).
  - Experimental expressive opt-in `ExperimentalMaterial3ExpressiveApi` (line 88).
  - "a colour of its own": `Color(` with a numeric or `red =` first argument, `colorResource(`, and `Color.White|Black|Red|...` (lines 89-94).
  - "a shadow of its own": `dropShadow(`, `innerShadow(`, `.shadow(`, `shadowElevation|tonalElevation`, `*FloatingActionButton(`, `Elevated*(`, `Defaults.*Elevation(`, `DropdownMenu(` (lines 96-101).
  - "an animation timing of its own": `tween(`, `spring(`, `keyframes(`, `repeatable(`, `infiniteRepeatable(` (line 102).
  - "an animation of its own": `AnimatedVisibility(`, `AnimatedContent(`, `Crossfade(`, `Animatable(`, `updateTransition(`, `rememberInfiniteTransition(`, `animate*AsState(`, `animateContentSize(` (lines 103-109).
  - "a delay of its own": `delay(` allowed only in `ShareRepository.kt` (line 111).
- Allowed samples (lines 59-70) include `SvCard(elevation = SvElevation.Level2)`, `Modifier.elevation(SvElevation.Level3, shape)`, `SvPrimaryFab(...)`, `fadeIn(fade) togetherWith fadeOut(fade)`, `SvMotion.screenChange<Float>()`.
- Not forbidden: `FontFamily`/`fontFamily` (see ResultContent.kt:387), `Typography`/`TextStyle` copies, `Modifier.background(MaterialTheme...)`, `MaterialTheme.shapes.*`, `Surface(` without elevation args, `ListItemDefaults.colors(... Color.Transparent)` (allowed in the sample).
- Note: the rule is not applied to `core/designsystem` itself.

### Other guard tests
- `app/src/test/java/com/dantech/splitvibe/ui/ReducedMotionTest.kt`: animations off → working state stands still (lines 91-97); on → moves (100-105); bill can be split with animations off (107-121). Uses `SplitVibeTheme` at line 146.
- `app/src/test/java/com/dantech/splitvibe/ui/ComponentBehaviourTest.kt`: radio semantics, person chip, split button, floating menu, tappable row labels (lines 52-203).
- `app/src/test/java/com/dantech/splitvibe/ui/ComponentCatalogTest.kt`: screenshot capture `catalog-light` (line 31) and `catalog-dark` (line 34) of `CatalogScreen`. References in `app/src/test/screenshots/catalog-light.png` and `catalog-dark.png` (both present).
- `app/build.gradle.kts:178-181`: Roborazzi `-Pscreenshots=record|verify` (default verify).
- Device tests: `app/src/androidTest/java/com/dantech/splitvibe/AppLaunchTest.kt:33` and `SplitFlowOnDeviceTest.kt:82` call `enableAccessibilityChecks()`.
- Screenshot reference images for screens: `app/src/test/screenshots/` (97 files, e.g. `01-home.png`, `11-settings.png`).

## 6. Navigation

Files: `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt` (597 lines), `ui/AppLink.kt` (69 lines), `MainActivity.kt` (91 lines).

### NavKeys (all `@Serializable`, `AppNavHost.kt`)
| Key | Line | Shape |
|---|---|---|
| `HomeKey` | 89 | data object |
| `SplitKey` | 92 | data object |
| `SettingsKey` | 95 | data object |
| `HistoryKey` | 98 | data object |
| `PaymentMethodsKey` | 101 | data object |
| `GroupsKey` | 104 | data object |
| `GroupKey(id)` | 107 | data class |
| `JoinGroupKey(token)` | 111 | data class |
| `RequestsKey` | 114 | data object |
| `ClaimKey` | 118 | data object |
| `RequestLinkKey(token)` | 122 | data class |
| `InsightsKey` | 126 | data object |
| `RecurringKey` | 129 | data object |
| `ProKey` | 133 | data object |

Shared key constant: `SPLIT_SURFACE = "split"` (line 136). `SEND_SURFACE = "send"` is in `ui/split/SplitScreen.kt:31`.

### Entries and back stack
- Back stack owner: `AppNavHost` itself, `rememberNavBackStack(HomeKey)` (line 146). Saveable across process death (keys are serializable).
- Entries are declared in `entryProvider { entry<...> { Screen { ... } } }` at lines 204-539. Entry lines: Home 205, Split 223, Claim 240, History 277, Insights 299, Recurring 322, Settings 338, Pro 369, Groups 394, Group 421, JoinGroup 456, Requests 480, RequestLink 510, PaymentMethods 526.
- Navigation actions: `NavBackStack.open(key)` (lines 554-556) appends unless the key is already on top. Everything else pops with `backStack.remove(key)` (e.g. lines 231, 249, 289, 332, 361, 364, 387, 429, 450, 464, 476, 504, 522, 535).
- Tabs are pushed, not replaced: Home's `PlaceButton`s push GroupsKey, RequestsKey, InsightsKey, RecurringKey (`ui/home/HomeScreen.kt:112-115`). No replace/root-switch helper exists.
- Settings is pushed from Home's top-bar icon (`ui/home/HomeScreen.kt:76-80`, callback at `AppNavHost.kt:215`) and from 6 other places (`AppNavHost.kt:232, 270, 415, 474, 502, 520`; removal on back at `:361, :364`).
- Home's FAB action: `HomeScreen.kt:84-89` → `AppNavHost.kt:209-212` (`splitViewModel.startNewSplit()` then `backStack.open(SplitKey)`).

### Transitions and shared surface
- `SharedTransitionLayout` wraps `NavDisplay` at `AppNavHost.kt:190-191`, `sharedTransitionScope = this` (line 193).
- `transitionSpec` and `popTransitionSpec`: `fadeIn(fade) togetherWith fadeOut(fade)` (lines 194-195), `fade = SvMotion.screenChange<Float>()` (line 187, 400 ms).
- Predictive back: `predictivePopTransitionSpec` adds `scaleOut(fade, targetScale = 0.85f)` (line 198).
- `entryDecorators`: `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()` (lines 199-203).
- Each entry is wrapped in `private fun SharedTransitionScope.Screen` (lines 546-551) which provides `LocalSvSharedTransition` (`motion/SharedSurface.kt:19`). Anything with `sharedSurface` must be inside a `Screen` block.
- Home side: `HomeScreen.kt:84-89` `SvPrimaryFab(..., modifier = Modifier.sharedSurface(SPLIT_SURFACE, resize = true))`.
- Split side: `ui/split/SplitScreen.kt:134` `Box(modifier = Modifier.sharedSurface(SPLIT_SURFACE))` wraps `SvSteps` (line 135). Steps are `SplitStep` (sealed, line 216): `Input`, `Edit`, `Result`, `Sent` with fixed names `"input"`, `"edit"`, `"result"`, `"sent"`.
- Send button: `ui/split/ResultContent.kt:744` and `RequestMoneyContent.kt:123` use `SEND_SURFACE`.
- `sharedSurface` mechanics: `motion/SharedSurface.kt:34-46`. `keepSizeDuringTransition` at line 54.
- The FAB rests flat while moving: `SvPrimaryFab.kt:32-40`.
- Back on the split screen: `ui/split/SplitScreen.kt:79-88` (`BackHandler` per step; Input is enabled only while busy, line 87). `onExit` at `AppNavHost.kt:231` removes `SplitKey`.
- Global back: `AndroidManifest.xml:34` `android:enableOnBackInvokedCallback="true"`.
- Bottom sheets: `SvBottomSheet` clears `LocalSvSharedTransition` (`SvBottomSheet.kt:57`).

### Deep links and intents
- Manifest: `app/src/main/AndroidManifest.xml`. `MainActivity` is `exported="true"` (line 41). Launcher filter (lines 43-47). `SEND`/`SEND_MULTIPLE` image/* filters (lines 49-58). App Link filter `autoVerify="true"` for `https`, host `${linkHost}`, `pathPrefix /g/` and `/r/` (lines 61-69).
- `linkHost` placeholder from `app/build.gradle.kts:84`, `:118`, `:135` (`linkHostOf(proxy URL)`).
- `AppLink` sealed interface (`ui/AppLink.kt:9-18`): `JoinGroup(token)`, `OpenRequest(token)`, `Group(id)`, `Requests`, `OpenBill(id)`.
- `AppLink.of(intent, proxyUrl)` (lines 34-52): handles notice extras (`Notices.EXTRA_KIND`, `EXTRA_BILL_ID`, `EXTRA_GROUP_ID`), then `ACTION_VIEW` data (line 51).
- `AppLink.of(uri, proxyUrl)` (lines 58-67): requires `https`, exact host and no userinfo; token in the fragment matched by `TOKEN` regex (line 24); path `/g/` → JoinGroup, `/r/` → OpenRequest.
- Consumed by `MainActivity.kt:47` (`AppLink.of(intent, BuildConfig.AI_PROXY_URL)`, taken once when `savedInstanceState == null`) and `AppNavHost.kt:172-181` (`LaunchedEffect(link)`).
- Shared images: `MainActivity.kt:46, 80-91`; in `AppNavHost.kt:164-169` they start a split and open `SplitKey`.
- Tests: `app/src/test/java/com/dantech/splitvibe/ui/AppLinkTest.kt` (lines 21-71).

### Where a bottom bar could be hosted
Options, based on the current code (no build verified):
1. Inside each tab entry, as the existing per-screen `bottomBar` pattern does (`ui/editor/BillEditorContent.kt:83`, `ui/insights/InsightsScreen.kt:123`). The bar is inside `Screen {}` so `sharedSurface` works. Cost: four copies, and the bar fades with each entry.
2. Around `NavDisplay` in `AppNavHost.kt:190-203`, using `Scaffold(bottomBar=...)` or a `Box` with the bar visible when `backStack.last()` is one of the four tab keys. The bar is then outside `Screen {}`, so `LocalSvSharedTransition` is not provided there. The split button would need its own `SharedTransitionScope`, which is unknown.
3. A new "tab root" model: current `open()` only pushes (`AppNavHost.kt:554-556`), so tab switching would need a replace or clear-and-push helper, and back behaviour would change.

## 7. Web token export
- Mechanism: `WebTokensTest` in `core/designsystem` writes `web/src/tokens.css` from the tokens, or fails when it differs. Triggered by Gradle test task `:core:designsystem:testDebugUnitTest` with `-PwebTokens=write` to regenerate (`core/designsystem/build.gradle.kts:23-28`, `WebTokensTest.kt:20-22`).
- Output file: `web/src/tokens.css` (195 lines; header line 1 says "Written from the app's design system by WebTokensTest ... Do not edit by hand").
- Consumers: `web/src/main.tsx:2` (`import "./tokens.css"`), `web/src/app.css` (header comment at line 2 says it uses only tokens.css variables).
- Docs: `docs/web.md:37`, `docs/architecture.md:25` (diagram `design -. tokens.css .-> web`), `docs/architecture.md:335-336`.
- No npm script or other build step writes `tokens.css`: searches of `web/package.json`, `web/scripts`, and root Gradle files found no reference beyond the above.
- Only light colours are exported in full. Dark colours are written in a `prefers-color-scheme: dark` block (`WebTokensTest.kt:61-70`).

## 8. Risks and unknowns

1. No bundled fonts exist today (no `.ttf`/`.otf` and no `res/font`). `SvTypography = Typography()` uses Material's default family (`tokens/SvTypography.kt:7`). The redesign's "two bundled fonts" need a new font resource, a `FontFamily` in `SvTypography`, and a matching change in `WebTokensTest.kt:43` (hard-coded system stack) plus web `@font-face` handling, which is not in the tokens test today. Unknown: whether any web font file exists under `web/` (not checked).
2. Shapes: `SplitVibeTheme` passes no `shapes`. A new shape set would need `MaterialExpressiveTheme(shapes = ...)` (`SplitVibeTheme.kt:25-30`). `WebTokensTest` reads `Shapes()` defaults at `WebTokensTest.kt:147`, so the web tokens would silently drift from the app. Unknown: whether the pinned `material3 = 1.5.0-rc01` (`gradle/libs.versions.toml:7`) `MaterialExpressiveTheme` accepts `shapes` in the same form (not checked against the library source).
3. `ColorTokensTest` covers text pairs and `outline` on surfaces, not `outlineVariant`, not the avatar person colours (`SvAvatar.kt:61-68`), and not shadows. A new palette could pass while the avatar colours fail contrast.
4. `SvBottomBar` is documented as floating (`docs/design-system.md:158`, `SvPrimaryButton.kt:42-43`) but the code is a full-width `RectangleShape` Level3 surface (`SvPrimaryButton.kt:47-49`). No floating pill-shaped nav bar exists. `SvFloatingToolbar` (`SvFloatingToolbar.kt:20-31`) is a circular floating toolbar with equal-weight actions, used only in the debug catalog. It is a candidate base, not a nav bar.
5. Navigation tabs are pushed onto one back stack (`AppNavHost.kt:554-556`), and each tab's Back returns through the stack. A true bottom navigation needs a root-switch model; `BackNavigationTest.kt` (lines 82, 103) covers only split-screen back, not tab switching.
6. The shared-surface key `SPLIT_SURFACE` pairs `HomeScreen.kt:88` with `SplitScreen.kt:134`. If Home's FAB becomes a bar button, the bar must sit inside the same `Screen {}` scope (`AppNavHost.kt:546-551`), or the animation silently does nothing (`SharedSurface.kt:35` returns `this` when no transition is provided).
7. Settings entry: Home's top-bar `IconButton` at `HomeScreen.kt:76-80` is the only Home-side entry. The account button replaces it; the other 6 `backStack.open(SettingsKey)` callers (`AppNavHost.kt` lines listed in section 6) are unaffected.
8. Boundary test scope: `DesignSystemBoundaryTest` does not forbid `FontFamily` on screens (`ui/split/ResultContent.kt:387` already sets `FontFamily.Monospace`), nor `Typography` overrides, nor `Color` opacity on text. A redesign that moves type into screens would pass the test.
9. `SvAvatar` hard-codes `Color.White` (`SvAvatar.kt:41`) and uses its own six person colours (`SvAvatar.kt:61-68`), outside tokens and outside the web export.
10. Dark theme elevation: `SvElevation.surface` (lines 40-52) and `Modifier.elevation` (line 64) depend on `LocalDarkTheme` (`SplitVibeTheme.kt:16`). A new dark palette must keep the four surface-container steps in the same order or the levels lose meaning.
11. Shadow tint is `onPrimaryFixed` (`SvElevation.kt:66`). Changing `onPrimaryFixed` also changes every shadow and the web `--sv-elevation-*` values.
12. Screenshot references (`app/src/test/screenshots/*.png`, 97 files) must be re-recorded with `-Pscreenshots=record` (`app/build.gradle.kts:178-181`) after any visual change. Recording was not run here.
13. Docs drift: `docs/design-system.md:145-167` omits several components listed above (`SvChoiceChips`, `SvPersonChoiceRow`, `SvOverflowMenu`, `SvMenuOption`, `SvSteps`, `SvAmount`). It also points `SvBottomSheet` to a file name that exists.
14. Unknown: whether `NavDisplay` (navigation3-ui 1.2.1, `gradle/libs.versions.toml:11`) can host a persistent bottom bar that participates in `SharedTransitionLayout` transitions. Not verified; no build was run.
15. Unknown: whether the device-test accessibility checks (`AppLaunchTest.kt:33`) cover the tab screens once a bar is added.
