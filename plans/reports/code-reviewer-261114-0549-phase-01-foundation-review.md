# Code review: Phase 1 foundation branch

- Branch: `feat/phase-01-foundation` compared with `main`, at commit `22b965f`
- Reviewer: independent code reviewer (read-only, did not run Gradle)
- Date: 2026-11-14
- The reviewer returned this report as text; the controller session saved it here and added the
  "What was done" section. The findings below are the reviewer's, shortened only where they repeat.

## Verdict

No Critical defect. The app code holds up against `main`:

- **Navigation:** the Navigation 3 port keeps every callback equivalent, with one exception: a
  double tap on Back or Save in Settings now closes one screen, where `main` closed two.
- **Dependency wiring:** the Koin wiring matches the old container one for one. The view model is
  still scoped to the activity and the analyzer cache is kept.
- **Moved code:** all eleven files moved to `:core:domain` differ from `main` only in formatting,
  plus `BillAnalysisParser` becoming public as the plan asks. The formatting hunks in
  `ClaudeBillAnalyzer.kt` do not change behaviour.
- **Release signing:** a `release` build can never be signed with the debug key, and no secret
  value is written to a log or a built file.

The weak spots were in CI and the docs.

## Findings

| # | Severity | Where | Finding |
|---|---|---|---|
| 1 | High | `.github/workflows/ci.yml` | Both jobs were skipped for a pull request from a branch of this repository, so only the push run on the branch tip counted. That run tests the branch head, not the merge with `main`. A job skipped by `if:` may also satisfy a required status check (depends on branch protection, not verifiable from the repository). |
| 2 | Medium | `.github/workflows/ci.yml` | The secret scan passes no `--config`, so gitleaks honours a `.gitleaks.toml`, a `.gitleaksignore` and inline `gitleaks:allow` comments from the pull request it scans. |
| 3 | Medium | `app/build.gradle.kts` | With one to three of the four signing variables set, the release came out unsigned without a message. An empty string counted as set. |
| 4 | Medium | `ci.yml`, `docs/architecture.md`, `.gitignore` | Roborazzi writes the comparison images to `outputs/roborazzi` in the output folder, not next to the reference image, so the workflow never uploaded them and the docs named the wrong place. |
| 5 | Medium | `phase-01-start.md` | Plan items missing or different without being recorded: checksums instead of signature checking, no baseline profile file, no baseline numbers, no back-swipe recording on API 36, no "intentional change turns CI red" run, `push` used beside `pull_request`. |
| 6 | Medium | `ci.yml` | The checks compiled neither the device tests, the profile module nor the `dev` flavor. |
| 7 | Low | `baseline-profile` benchmarks | Two benchmarks use `BaselineProfileMode.Require` and fail until a profile is committed. |
| 8 | Low | test comments, `docs/architecture.md`, `README.md` | Two comments still named `:app:testDebugUnitTest`; the architecture page said a fork pull request can run both workflows; the "7 tests pass in GitHub Actions" claim needed a run to back it. |
| 9 | Low | `ui/AppNavHost.kt`, manifest | A double tap on Back or Save in Settings now closes one screen instead of two, and the app opts in to the back callback on Android 13 to 15. Both are improvements but differ from "no behaviour change". |
| 10 | Low | tests | `AppModuleTest` checks a hand-written list of types; `koin.close()` is not in a `finally`. |
| 11 | Low | `device-tests.yml`, `.editorconfig` | A push to `main` that touches the device paths cancels a running nightly job. `.editorconfig` uses the Android Studio style while `gradle.properties` says `kotlin.code.style=official`. |

Suspicions the reviewer could not verify by reading:

- **S1.** The "working" screenshot depends on a 4 second response delay outlasting the capture.
- **S2.** Reference images compared exactly may differ on another operating system.
- **S3.** `robolectric.properties` pins SDK 34, and its comment claimed that is the newest platform Robolectric ships.
- **S4.** Restoring the back stack after process death has never run on a minified release.
- **S5.** `NavDisplay`'s default back handling was not read by the reviewer.

Checked and found correct: every navigation callback against `main`; the Koin module against the
deleted container; test isolation through `stopKoin`; the renames; the eleven moved files; the
formatting-only hunks; every action pinned to a full commit that matches its tag; `permissions:
contents: read`, `persist-credentials: false`, no `pull_request_target`, no `secrets.*`; the
gitleaks archive verified with `sha256sum --check` against the published checksum; no secret in the
repository.

## What was done

| # | Decision | Detail |
|---|---|---|
| 1 | Fixed | The checks now run on `pull_request` for every pull request and on `push` to `main` only, as the plan says, and the `if:` conditions are gone. Requiring the two checks in branch protection is a repository setting for the owner. |
| 2 | Accepted, documented | The scan is a net for accidents. Hiding a secret from it takes a deliberate change to an ignore file or an inline marker in the same pull request, which shows in the diff. Forcing the default rules would also remove the only way to silence a false positive in history. The architecture page says such a change must be reviewed like code. |
| 3 | Fixed | A blank value counts as not set, and one to three variables stop the build with the names of the missing ones. Verified with none, some and four blank variables. |
| 4 | Fixed | The workflow uploads `outputs/roborazzi`, the docs name the right folder, and the two ignore patterns that rested on the wrong belief are removed. Verified by the run that fails on purpose (see the phase file). |
| 5 | Recorded | The phase file now lists each difference and each open item. |
| 6 | Fixed | The checks compile the `dev` flavor, the device tests and the profile module. |
| 7 | See phase file | Depends on whether the nightly job has recorded a profile that could be committed. |
| 8 | Fixed | Comments and the architecture page corrected. The test count is backed by workflow run `43561370404`. |
| 9 | Recorded | Both are kept as intended and listed in the phase file. |
| 10 | Left as is | The startup test starts the real application, which resolves the view model and everything it needs. The module test's Koin instance is local, so a failed assertion leaks nothing. |
| 11 | Left as is | A newer run on the same branch replaces the nightly one, which serves the same purpose. The IDE reads `.editorconfig`, which is the stricter of the two. |
| S1 | Left as is | Passed in every run so far, locally and on the GitHub runner; noted as a possible source of a flaky run. |
| S2 | Checked | The images were recorded in a Linux container and matched on the `ubuntu-24.04` runner. |
| S3 | Fixed | The comment now gives the real reason: the reference screenshots were recorded on Android 14. Existing tests already pinned SDK 34. |
| S4 | Checked in part | The release package keeps `INSTANCE` and `serializer()` on the three key objects (inspected with apkanalyzer). Running a minified build on a device is still open. |
| S5 | Checked | The source of `navigation3-ui` 1.2.1 was read: the default removes the last entry, and it is enabled only when a previous entry exists. |

## Unresolved questions

1. Which checks are required in branch protection, and are workflows from fork pull requests held for approval? Only the repository owner can see and set this.
2. Is checking SHA-256 checksums instead of signatures acceptable to the product owner? The plan's wording was "signature".
