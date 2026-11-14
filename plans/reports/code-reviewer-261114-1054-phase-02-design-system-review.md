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

## What was done

All fifteen findings and the unresolved question were accepted after checking each against the code,
and fixed in five commits on the branch (`46ce187`, `c37f485`, `791bbba`, `dea953a`, `97d38b4`).

| # | Fix |
|---|---|
| 1 | Steps have fixed names; `SvSteps` takes only a name (`stepOf: (S) -> String`), and its comment says why. |
| 2 | `SvPrimaryFab` rests flat while its shared surface moves and fades its shadow back in through `strength`. The back-swipe reference image shows the difference: the shadow drawn at the corners of the moving surface is gone. |
| 3 | `SvAmount` and `popIn` save that they have run; with animations off an amount starts at its value. |
| 4 | The baseline profile was recorded again on the emulator (23,376 rules, 219 for the design system, none for removed classes). While taking it over, a workflow bug showed up: the test job uploaded the committed profile under the same artifact name as the recording job, so a download by name could take the stale copy. The upload now runs only in the recording job. |
| 5 | Leaving the confirmation no longer resets the split; the button that opens a split already does. |
| 6 | The remove chip reads as one button with its label; tappable rows have the button role. `ComponentBehaviourTest` checks both. |
| 7 | `DesignSystemBoundaryTest` covers the listed cases, scans the debug catalog, allows the theme only in the two activities, and fails when it finds fewer than 20 files. |
| 8 | The split button's menu has no Material shadow, a level surface and an outline. |
| 9 | The scan band's gradient is built once per size in `drawWithCache` and moved with `translate`. |
| 10 | `BillCurrency` keeps one formatter per thread for the last locale. |
| 11 | The floating menu's button uses one of two fixed shapes for its shadow. |
| 12 | Notices are keyed by their text, and repeated sentences are shown once. |
| 13 | `ReducedMotionTest` passes a `MotionDurationScale` of 0 to Compose when it switches animations off. |
| 14 | README and architecture page: 109 unit tests (108 pass, 1 skipped), 8 device tests, the module's contents, and that CI runs for pull requests and pushes to `main`, not every branch. |
| 15 | ktlint's unused-imports rule is on and the unused imports are gone; `SplitFlowTest` uses `Fixtures.claudeAnswer()`. |
| Question | A sheet provides no shared transition to its content, so nothing inside it can ask for a lookahead pass that a sheet does not have. |

Checks after the fixes: code style, 109 unit tests, Android Lint on both modules and every package
build passed locally.
