# Code review: Phase 2 design system branch

- Branch: `feat/phase-02-design-system` compared with `main`, at commit `39a4994`
- Reviewer: independent code reviewer (read-only; did not run Gradle; checked library behaviour by disassembling the cached jars)
- Date: 2026-11-14
- The reviewer returned this report as text; the controller session saved it here and added the
  "What was done" section at the end. The findings below are the reviewer's, shortened where they repeat.

## Verdict

Do not merge yet. The screens stay inside the design system, the tokens match the docs, and most of the
motion respects reduced motion. Two High problems need fixing first: process death loses saved state in
the split flow (including a photo taken with the camera), and the home button's shadow is blurred again on
every frame of the transition into the split screen. Two Medium and eleven Low findings; nothing Critical,
no security or public-contract issue.

## Findings

| # | Severity | Where | Finding |
|---|---|---|---|
| 1 | High | `ui/split/SplitScreen.kt` (`stepOf = { it::class }`), `motion/SvSteps.kt` | The step key is a `KClass`, whose hash is an identity hash that differs in every process. `AnimatedContent` builds it into the saved-state key of everything inside the step, so no `rememberSaveable` inside a step comes back after process death. The camera launcher's registry key is lost and a photo taken while the app was killed is dropped. |
| 2 | High (performance, not measured) | `ui/home/HomeScreen.kt`, `components/SvPrimaryFab.kt`, `motion/SharedSurface.kt` | With `resize = true` the floating button is measured again at the animated size every frame; its Level3 `dropShadow` sits inside the shared bounds and is rebuilt (a bitmap up to screen size, blurred on the CPU) at every size. |
| 3 | Medium | `motion/SvAmount.kt`, `motion/Bounce.kt` with `ResultContent.kt` | "When it first appears" is tied to the life of the composition, but these sit in lazy-list items that are dropped when scrolled away: the bill total counts up from zero again and added people's chips pop in again after scrolling back. With animations off the total shows zero for one frame. |
| 4 | Medium (performance) | `app/src/prodRelease/generated/baselineProfiles/` | The committed baseline profile predates the redesign: 14 rules for deleted classes, none for `designsystem`. |
| 5 | Low | `SplitScreen.kt` | `finish` resets the view model before leaving, so the confirmation fades into an empty "New split" form while the screen shrinks back into the button. |
| 6 | Low (accessibility) | `components/SvPersonChip.kt`, `components/SvListRow.kt` | The remove chip reads as a selected checkbox; tappable rows have no button role although the docs say they are buttons. |
| 7 | Low | `DesignSystemBoundaryTest.kt` | The pattern scan misses default Material shadows, `colorResource(`, `Color(r, g, b)`, default-spec animations, hand-written `delay(` and a screen wrapping itself in `SplitVibeTheme(`; it also passes when no file is found. |
| 8 | Low | `components/SvSplitButton.kt` | The `DropdownMenu` keeps Material's default shadow and container colour. |
| 9 | Low (performance) | `components/SvBillPhoto.kt` | The scan band builds a new gradient brush on every frame. |
| 10 | Low (performance) | `motion/SvAmount.kt`, `domain/BillCurrency.kt` | Every frame of a run creates a new currency formatter. |
| 11 | Low (performance, catalog only) | `components/SvFabMenu.kt` | A new shape every frame of the menu button's change rebuilds its shadow every frame. |
| 12 | Low | `ResultContent.kt` | Notice keys contain the position, so a notice that moves up fades out and in instead of sliding. |
| 13 | Low (test quality) | `ReducedMotionTest.kt` | Setting the animator scale in the test only flips `LocalReducedMotion`; Compose's own animations still run, so the animations-off path of Compose is never exercised. |
| 14 | Low (docs) | `README.md`, `docs/architecture.md` | Test counts and the description of `:core:designsystem` are out of date. |
| 15 | Low (hygiene) | screens and tests | 35 unused imports that ktlint does not report, because its unused-imports rule is off; `SplitFlowTest` keeps its own copy of the recorded answer that `Fixtures.claudeAnswer()` provides. |

Unresolved question raised by the reviewer: `SvPrimaryButton` always asks to keep its size during a
transition; inside a bottom sheet, which has no lookahead pass, that would crash if a shared transition
were running. No current flow does that.

## Checked and found sound

The mid-run behaviour of `SvAmount` and its test; the step container keeping each step's data and the
back handling outside it; the keys of `SvBillPhoto` and `SvSuccessMark`; unique lazy-list keys; reduced
motion in the new pieces apart from finding 3; the design-system boundary in `app/src/main`; draw-phase
reads of animated values; the Koin restore in `SplitFlowOnDeviceTest`; screenshot determinism; the colour
table and avatar contrast; the CI and checksum workflows.
