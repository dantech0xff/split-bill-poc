# Android UI (M3 Expressive, motion, shadows) and Google Play release: fact sheet as of 2026-11-14

Author: researcher agent. The agent could not write files, so it delivered this report in its final message and the controller saved it here unchanged except for this note and the "Checked again by the controller" section.

Confidence labels: **[O]** official source or direct inspection of official artifacts, **[S]** secondary source, **[U]** unverified (also listed in the Unverified section).

## Checked again by the controller (2026-11-14)

- Google Maven metadata: latest `compose-bom` is 2026.09.00 and its POM pins material3 1.4.0 with ui, foundation and animation 1.12.1; latest material3 is 1.5.0-rc01; navigation-compose 2.10.2; benchmark-macro-junit4 1.5.0; billing 9.1.0; graphics-shapes 1.1.0.
- Local Gradle cache of this project (BOM 2026.03.01): material3 1.4.0 has no `MaterialExpressiveTheme`, `ButtonGroup`, `FloatingToolbar`, `LoadingIndicator`, wavy indicators or `MaterialShapes`; ui 1.10.6 contains `dropShadow` and `innerShadow`; animation 1.10.6 contains `SharedTransitionLayout` without the experimental opt-in.
- `zipalign -c -P 16 -v 4` on the debug APK of this project: all 8 native libraries pass the 16 KB check.

## Headline

- **No Expressive APIs yet:** the current BOM 2026.03.01 gives no Material 3 Expressive APIs. It pins material3 1.4.0, and 1.4.0's public API contains none of them.
- **Latest BOM doesn't help:** the newest BOM, 2026.09.00, also pins material3 1.4.0.
- **What Expressive needs:** `androidx.compose.material3:material3:1.5.0-rc01` (Nov 4, 2026) pinned explicitly. In that version almost everything is promoted to stable. Only `LoadingIndicator`/`ContainedLoadingIndicator`, `MaterialShapes` and a few PullToRefresh colours still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

How it was verified: Google Maven metadata and the BOM POMs, inspection of the AARs (javap), AndroidX API signature files (`api/*.txt`) and source on android.googlesource.com.

---

## 1. Material 3 Expressive in Compose

### Versions

| Item | Fact | Conf. | Source |
|---|---|---|---|
| material3 latest stable | 1.4.0 | O | https://developer.android.com/jetpack/androidx/releases/compose-material3 |
| material3 pre-release | 1.5.0-rc01 (Nov 4, 2026), 1.5.0-beta02 (Oct 21), 1.5.0-alpha29 (Sep 23) | O | same, plus `dl.google.com/.../material3/maven-metadata.xml` |
| BOM 2026.03.01 maps to | material3 **1.4.0**; ui/foundation/animation/runtime **1.10.6** | O | `dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.03.01/compose-bom-2026.03.01.pom` |
| Latest BOM | **2026.09.00**, which maps to material3 **1.4.0** and ui/foundation/animation/runtime **1.12.1**. No 2026.10/11 BOM exists. | O | BOM maven-metadata.xml and the 2026.09.00 POM; https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Expressive in 1.4.0? | **No public Expressive API.** The 1.4.0 API file has 0 hits for MaterialExpressiveTheme, MotionScheme, ButtonGroup, FloatingToolbar, LoadingIndicator, Wavy, Flexible app bars and MaterialShapes. | O | `android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/material3/material3/api/1.4.0-beta03.txt` plus AAR inspection |
| Expressive in 1.5.0 | Present. The API file has 27 lines still tagged experimental (identical count in 1.5.0-beta01.txt and current.txt). | O | `.../material3/api/1.5.0-beta01.txt`, `current.txt` |
| 1.5.0-rc01 transitive deps | foundation/ui/animation-core **1.12.0** and graphics-shapes **1.0.1**. These are newer than BOM 2026.03.01's 1.10.6, so Gradle will upgrade the Compose UI libraries anyway. | O | `material3-android-1.5.0-rc01.pom` |

The opt-in annotation is `androidx.compose.material3.ExperimentalMaterial3ExpressiveApi` (a `@RequiresOptIn` annotation). [O]

### API-by-API status in 1.5.0 (from current.txt / 1.5.0-beta01.txt) [O]

| API | Exists? | Stability in 1.5.0 |
|---|---|---|
| `MaterialExpressiveTheme(colorScheme?, motionScheme?, shapes?, typography?, content)` | Yes | Stable, no opt-in. `expressiveLightColorScheme()` is also stable. |
| `MotionScheme.expressive()` / `MotionScheme.standard()`; `MaterialTheme(motionScheme = …)`; `MaterialTheme.motionScheme` | Yes | Stable |
| `MaterialShapes` (about 35 `RoundedPolygon` presets) plus `RoundedPolygon.toShape()` / `toPath()` | Yes | **Experimental**: `@ExperimentalMaterial3ExpressiveApi` |
| Shape morphing | Use `androidx.graphics.shapes.Morph(start, end)` from graphics-shapes. MaterialShapes are `RoundedPolygon`s. | graphics-shapes 1.1.0 is stable; material3 1.5 pulls 1.0.1 |
| Emphasized typography (`displayLargeEmphasized` … `labelSmallEmphasized`, 15 styles on `Typography`) | Yes | Stable |
| `ButtonGroup` | Yes | Stable |
| Split button | The public composable is `SplitButtonLayout(leadingButton, trailingButton, …)` with `SplitButtonDefaults.LeadingButton/TrailingButton` (plus Tonal/Elevated/Outlined variants). There is no `SplitButton(` composable in current.txt. | Stable. See Unverified. |
| `FloatingActionButtonMenu` (+ `ToggleFloatingActionButton`) | Yes | Stable |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | Yes | Stable |
| `LoadingIndicator` / `ContainedLoadingIndicator` | Yes | **Experimental**: `@ExperimentalMaterial3ExpressiveApi` |
| `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | Yes | Stable |
| Flexible app bars: `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `FlexibleBottomAppBar`, `TwoRowsTopAppBar` | Yes | Stable |
| Toggle buttons: `ToggleButton`, `ElevatedToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton` | Yes | Stable. `TonalToggleButton` was renamed `FilledTonalToggleButton`. |
| Other items still experimental | `PullToRefreshDefaults.loadingIndicatorColor/ContainerColor` (Expressive opt-in); `AppBarWithSearch` is under `@ExperimentalMaterial3Api` | |

### Migration gotchas from plain `MaterialTheme` [O, from the MaterialTheme.kt source unless noted]

1. **Light-only default colours:** with no `colorScheme`, `MaterialExpressiveTheme` uses `expressiveLightColorScheme()`. That is light only and has no dynamic colour, so the app must pass its own dark and dynamic schemes.
2. **Typography is unchanged:** the default is plain `Typography()`. Emphasized styles only appear where they are used explicitly.
3. **Plain `MaterialTheme` still uses standard motion:** it defaults to `MotionScheme.standard()`. Expressive springs come only through `MaterialExpressiveTheme` or `MaterialTheme(motionScheme = MotionScheme.expressive())`.
4. **Nesting:** a nested `MaterialExpressiveTheme` inherits from its parent. A `MaterialTheme` inside it overrides only the parameters passed.
5. **Version alignment:** pinning material3 1.5.0-rc01 on BOM 2026.03.01 silently lifts Compose UI to 1.12.0. Move the BOM to 2026.09.00 (1.12.1) at the same time so the libraries stay consistent.
6. **Stale examples:** tutorials written against 1.4.0-alphas or early 1.5.0-alphas use renamed or removed APIs.
7. **Release-candidate risk:** an RC means the API is frozen but not final. A 1.5.0 stable date could not be confirmed. [U]

---

## 2. Motion

- **Consuming MotionScheme tokens** [O, MotionScheme.kt KDoc and API]:
  - Methods: `MaterialTheme.motionScheme.{default,fast,slow}SpatialSpec<T>()` and `{default,fast,slow}EffectsSpec<T>()`. Each returns `FiniteAnimationSpec<T>`, so it can go straight into `animate*AsState(animationSpec = …)`, `updateTransition`, `fadeIn/slideIn(animationSpec)` and `Animatable.animateTo`.
  - Spatial specs are for animations that change the shape or bounds of a component; effects specs are for colour and alpha.
  - The implementations are spring specs (damping and stiffness tokens), not fixed-duration tweens.
- **Shared elements** [O]: `SharedTransitionLayout`, `Modifier.sharedElement` and `Modifier.sharedBounds` need no opt-in from animation 1.10.0, which the current BOM's 1.10.6 already includes.
- **`Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)`** on lazy lists, grids and staggered grids: stable, no opt-in. It needs stable item keys. [O]
- **Predictive back** [O]:
  - Apps targeting API 36 on Android 16 get back-to-home, cross-activity and cross-task animations by default. On those devices `onBackPressed` is not called and `KeyEvent.KEYCODE_BACK` is not dispatched. Source: https://developer.android.com/about/versions/16/behavior-changes-16
  - Navigation Compose 2.8.0+ supports it (latest **2.10.2**; the project uses 2.9.8). Material 3 1.3.0+ supports predictive back in `SearchBar`, `ModalBottomSheet` and the drawers. Manual handling uses `PredictiveBackHandler`. Source: https://developer.android.com/develop/ui/compose/system/predictive-back-setup
- **Navigation 3** [O]:
  - `androidx.navigation3:navigation3-runtime/-ui` **1.2.1 stable (Nov 4, 2026)**; 1.3.0-alpha04. 1.0.0 went stable on Nov 19, 2025.
  - 1.2 adds a result API, a deep-link API and `NavigationBackHandler` for predictive back. It depends on Compose 1.10 and compileSdk 36.
  - Google's position: the stable announcement says it is ready for production and that Nav2 users should consider migrating. The Navigation overview page still recommends Navigation Compose for Compose apps. There is **no explicit official statement that new apps should use Nav3**, and Nav2 is not deprecated.
  - Sources: https://developer.android.com/jetpack/androidx/releases/navigation3, https://developer.android.com/blog/posts/jetpack-navigation-3-is-stable, https://developer.android.com/guide/navigation
- **`androidx.graphics:graphics-shapes`**: latest stable **1.1.0** (`RoundedPolygon`, `Morph`). [O]
- **Haptics** [O]: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.X)`. Compose UI 1.8.0 added Confirm, Reject, ToggleOn, ToggleOff, SegmentTick, SegmentFrequentTick, GestureThresholdActivate, GestureEnd, ContextClick and VirtualKey.
- **Animation performance** [O, https://developer.android.com/develop/ui/compose/performance/bestpractices]: defer state reads with lambda modifiers such as `Modifier.offset { }`, `graphicsLayer { }` and `drawBehind { }`; use `derivedStateOf`; avoid backwards writes; use lazy keys and `contentType`.
- **Respecting "remove animations"** [O, source]: Compose on Android reads `Settings.Global.ANIMATOR_DURATION_SCALE` and feeds it into `MotionDurationScale`, so stock Compose animations collapse when the scale is 0. Custom infinite or decorative effects should read the scale and render a static state. Whether the accessibility toggle maps to scale 0, and how infinite transitions behave at 0, is unconfirmed. [U]

---

## 3. Shadows

| | `Modifier.shadow` | `Modifier.dropShadow` / `Modifier.innerShadow` |
|---|---|---|
| Signature | `shadow(elevation: Dp, shape, clip, ambientColor, spotColor)` | `dropShadow(shape: Shape, shadow: Shadow)` and `dropShadow(shape: Shape, block: DropShadowScope.() -> Unit)`; `innerShadow` has the same pair. `Shadow(radius: Dp, color: Color = Black, spread: Dp = 0.dp, offset: DpOffset = Zero, alpha: Float, blendMode)` plus a `Brush` overload for gradient shadows. |
| Introduced | Long-standing | **Compose UI 1.9.0**. Stable, no opt-in, available on the current BOM (1.10.6). [O] |
| Rendering | Platform elevation shadow from the light-source model. Only elevation is adjustable. | Rasterises the shape outline into an Alpha8 bitmap using `BlurMaskFilter`. Renderers are cached per shape, size, layout direction, density and shadow minus offset. [O, source] |
| API-level limits | `ambientColor`/`spotColor` work on Android 9 (API 28) and above only; on API 26-27 the shadow stays black. [O] | No API-level guard in the source; it should work at minSdk 26 (inferred from source). |
| Performance notes | Cheap, done by the platform. | Because the cache excludes offset, **animating offset is cheap**. Animating radius, spread, size or shape re-blurs every frame. `innerShadow` draws on top, so it goes after `background`. [O] |

Shadow docs: https://developer.android.com/develop/ui/compose/graphics/draw/shadows [O]. Material 3's own guidance is that elevation is mainly tonal, with shadows secondary. [O]

---

## 4. Measuring smoothness

- **Macrobenchmark and Baseline Profiles** [O, https://developer.android.com/jetpack/androidx/releases/benchmark]: `androidx.benchmark:benchmark-macro-junit4` **1.5.0 stable (Sep 9, 2026)**; the `androidx.baselineprofile` Gradle plugin is also 1.5.0. As of 1.5.0-alpha01 the plugin no longer requires `newDsl=false` in AGP 9.0.
- **FrameTimingMetric** [O]: `frameDurationCpuMs`, `frameOverrunMs` (preferred where available) and `frameCount`.
- **Baseline Profile setup** [O, https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile]: a `com.android.test` module with `androidx.baselineprofile`, `baselineProfile(project(":baseline-profile"))` in the app, then `:app:generateBaselineProfile`. Generation works without root on API 33+.
- **Compose compiler** [O]: strong skipping is on by default since Kotlin 2.0.20. The `composeCompiler {}` block has `reportsDestination`, `metricsDestination` and `stabilityConfigurationFiles`.
- **R8 under AGP 9** [O, https://developer.android.com/build/releases/agp-9-0-0-release-notes]: new defaults are optimized resource shrinking, strict full mode for keep rules (`-keep class A` no longer keeps the constructor) and failing on missing ProGuard files. Latest AGP is **9.5.1**; the project uses 9.1.1.

---

## 5. Google Play, new app from a personal account, late 2026

| Requirement | Fact | Conf. | Source |
|---|---|---|---|
| Target API | Since **Aug 31, 2026**, new apps and updates must target **API 36**. targetSdk 36 is compliant. | O | https://support.google.com/googleplay/android-developer/answer/11926878 |
| 16 KB pages | Apps targeting API 35 and higher must support 16 KB page sizes on 64-bit devices. From **February 1, 2027**, updates that do not support it cannot be released. Check with `zipalign -c -P 16 -v 4` or `bundletool dump config`. | O | https://developer.android.com/guide/practices/page-sizes |
| ML Kit text-recognition 16.0.1 | Listed as 16 KB-supported in the release notes of Aug 7, 2024. The only native library, `libmlkit_google_ocr_pipeline.so`, has every LOAD segment aligned to 0x4000 on arm64-v8a and x86_64. | O | https://developers.google.com/ml-kit/release-notes plus readelf on the Maven AAR |
| Closed testing | Personal accounts created after Nov 13, 2023 need at least **12 testers** opted in continuously for at least **14 days** before applying for production. | O | https://support.google.com/googleplay/android-developer/answer/14151465 |
| Data safety | Mandatory for every published app. Collection means transmitting data off the device, including by SDKs; ephemerally processed data must still be declared. A privacy policy is required. Sending receipt text to a cloud LLM API counts as collection. | O | https://support.google.com/googleplay/android-developer/answer/10787469 |
| Account deletion | If users can create an account in the app, an in-app deletion path **and** a web link are required. | O | https://support.google.com/googleplay/android-developer/answer/13327111 |
| Play Billing Library | The **minimum for new apps and updates is now v8 (8.0.0)**. v8 is supported until Aug 31, 2027; v9 until Aug 31, 2028. Latest is **9.1.0**. | O | https://developer.android.com/google/play/billing/deprecation-faq plus Maven |
| Photo/video permissions | Apps targeting API 33+ may request `READ_MEDIA_IMAGES` only if the system photo picker is not sufficient for core functionality, and must file a declaration. | O (policy) | https://support.google.com/googleplay/android-developer/answer/9888170 |
| Edge-to-edge | Enforced since targeting API 35. Targeting API 36 disables the opt-out on Android 16 devices, so insets must be handled. | O | https://developer.android.com/about/versions/16/behavior-changes-16 |
| Large screens on API 36 | On sw ≥ 600dp, orientation, resizability and aspect-ratio locks are ignored. | O | same |

---

## 6. Screenshot testing

| Tool | State (Nov 2026) | Conf. |
|---|---|---|
| **Roborazzi** (`io.github.takahirom.roborazzi`) | Latest **1.79.0** (12 Nov). Runs on Robolectric (≥ 4.10, native graphics mode). Tasks: `recordRoborazziDebug`, `verifyRoborazziDebug`, `compareRoborazziDebug`. | O (maintainer repo) https://github.com/takahirom/roborazzi |
| **Paparazzi** (`app.cash.paparazzi`) | Stable 1.3.5. **2.0.0-alpha05.1 (2026-09-28)** is the first release to list AGP 9.0.0. Uses layoutlib, not Robolectric. | O (maintainer changelog) |
| **Compose Preview Screenshot Testing** | The standalone `com.android.compose.screenshot` plugin is no longer maintained. Its replacement, AGP test suites, is experimental and needs AGP ≥ 9.5.0-alpha03 plus experimental flags. | O https://developer.android.com/studio/preview/compose-screenshot-testing |

**Recommendation for a single developer already on Robolectric 4.17: Roborazzi.** It reuses the existing Robolectric and `ui-test-junit4` setup and renders through the real Android graphics stack. Robolectric 4.18 is out; the project uses 4.17. [O]

---

## Unverified

- `SplitButton` naming: the release notes say `SplitButtonLayout` was deprecated for `SplitButton`, but the main API file shows only `SplitButtonLayout` with no deprecation. Check against the 1.5.0-rc01 KDoc or the IDE before coding.
- Whether 1.5.0-rc01 exactly matches androidx-main current.txt (inferred from the API freeze at beta).
- Release date of material3 1.5.0 stable, and whether a BOM containing it ships soon.
- Whether the accessibility "Remove animations" toggle sets `ANIMATOR_DURATION_SCALE` to 0, and how `rememberInfiniteTransition`, LoadingIndicator and wavy indicators behave at scale 0.
- Whether Expressive components trigger haptics on their own.
- Whether Robolectric native graphics renders `dropShadow` (BlurMaskFilter) and elevation shadows faithfully. Roborazzi's AGP 9.1 compatibility is not stated in its README.
- Minimum and maximum AGP versions for benchmark and baselineprofile 1.5.0.
- Whether the Feb 1, 2027 16 KB enforcement date also covers first-time new apps.
- m3.material.io pages render client-side and returned no content, so design-level motion and shape guidance comes from the Compose source KDoc.
- Not checked: AAB and Play App Signing specifics. The release build is currently signed with the debug key per `app/build.gradle.kts`, which Play will not accept as an upload key.

## Implications for the plan

1. Expressive is impossible on BOM 2026.03.01. Upgrade to BOM **2026.09.00** and pin `material3:1.5.0-rc01` explicitly, accepting RC risk, or wait for 1.5.0 stable.
2. Theme root: `MaterialExpressiveTheme`, passing the app's own light, dark and (optionally) dynamic `ColorScheme`. Use emphasized type styles explicitly.
3. Keep `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` confined to the few call sites that use `LoadingIndicator`, `ContainedLoadingIndicator` and `MaterialShapes`.
4. Drive every custom animation from `MaterialTheme.motionScheme`: spatial specs for bounds and shape, effects specs for colour and alpha. Use lambda modifiers and `Modifier.animateItem` with stable keys.
5. Shared element transitions are stable on the current BOM. Use `SharedTransitionLayout` plus `sharedBounds` for list-to-detail transitions.
6. Navigation: either stay on navigation-compose (bump to 2.10.2) or migrate to Nav3 1.2.1. Decide once, before the screens multiply.
7. Shadows: use `dropShadow`/`innerShadow` for brand shadows and animate only offset and alpha. Treat coloured `Modifier.shadow` as API 28+ only.
8. Reduced motion: stock Compose animations follow the system scale. Gate infinite or decorative effects behind an explicit check and test them with animations disabled.
9. Add a `:baseline-profile` module (benchmark and baselineprofile 1.5.0) and macrobenchmark journeys with `FrameTimingMetric`. Turn on Compose compiler reports for a stability pass, and re-check keep rules under AGP 9's strict R8 defaults.
10. Play release: targetSdk 36 is fine. ML Kit 16.0.1 is 16 KB-ready. Budget **14+ days** for a 12-tester closed test. Write a privacy policy and a Data safety form that declares the off-device receipt processing. Replace debug signing with a real upload key.
11. With accounts or subscriptions: provide in-app plus web account deletion, and use Play Billing **≥ 8.0.0**, preferably 9.1.0. Get images through the photo picker or camera; never request `READ_MEDIA_IMAGES`.
12. Screenshot tests: adopt Roborazzi on the existing Robolectric setup. Do not adopt the deprecated `com.android.compose.screenshot` plugin.
