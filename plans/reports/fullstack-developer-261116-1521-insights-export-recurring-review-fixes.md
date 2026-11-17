# Fixes of the review of insights, export and recurring bills

Date: 2026-11-16 (UTC). Branch `feat/phase-07-ai-assistant-insights`. Review: `code-reviewer-261116-1448-phase-7-insights-export-recurring-review.md`, with L3 and L9 of `code-reviewer-261116-1448-phase-7-domain-ai-app-review.md`.

## Outcome

Every finding I was given is fixed or left with a reason, as decided:

- both High defects;
- the four Medium issues that are mine (M5, the docs, is the orchestrator's);
- the decisions on notices, undated bills, rounding and groups;
- the Low findings that are real defects.

Each fix has a test that fails without it. I checked that by taking each fix out and running its test again.

Two things on the branch are not green:

- **Lint, in some of my pushed commits.** `11a5a83` moved the check of leave to post notices into a helper that lint cannot follow. From that commit to `799a37b`, `:app:lintProdDebug` reports two MissingPermission errors. `f4566ce` fixes it, and lint passes on the final tree. I did not rewrite those commits, as the branch is shared and pushed.
- **ktlint, in another agent's commit.** `ktlintCheck` fails on the final tree only at `app/src/test/java/com/dantech/splitvibe/ui/split/BillLifecycleTest.kt:265`. That line came in `19e6ac4`, which is not mine. None of my files has a ktlint finding.

## What changed, by finding

| Finding | What changed | Commit |
|---|---|---|
| H1 | The schedule starts on the bill's own day, or on the day the user picks, and the sheet describes that schedule. The first draft goes to `RecurringBillRepository.create` on its own and is reached through `madeThrough`, never by moving the start. Tests: a rent dated 31 October, repeated as the sheet suggests and run daily, makes drafts on 30 November, 31 December, 31 January, 28 February and 31 March. A bill dated today makes no draft of itself. The sheet's own Repeat button hands on the 31st. | `34f1644`, `36dd6fd` |
| H2 | The note for bills without a rate now ends "To count one, open it and type a rate." The fixture saves the rates the app saves, now that finishing a bill keeps the day's rate (`99dbc25`). That is the day's rate, worked out with `ExchangeRate.between` from Frankfurter's rates against the euro as `FxRepository` does, a rate typed by hand, or none. | `0ebc0de` |
| M1 | `MainActivity` catches up in `repeatOnLifecycle(Lifecycle.State.STARTED)`, without the `savedInstanceState` condition. Tests launch the activity, bring a live one back, and recreate one, with a draft due each time. | `00203c2` |
| M2 | The participants in `saved.share?.added` are taken off the template with `withParticipantRemoved`. Test: once a draft is finished, the people remembered are only those the user split with. | `5e6bc72` |
| M3 | An export waits for the state with `state.filterNotNull().first()`. A period without bills says "Nothing was exported, as this period has no finished bills." Tests use a view model nothing watches yet, and a period without bills. | `27ffd72` |
| M4 | Only names are cut short. Columns of amounts, counts, dates and categories take their share of the page, or what their widest cell needs when that is more, and the name takes the rest. When that would leave a name less than a quarter of the page, those cells are drawn smaller. The reference text is the text drawn. Tests read every `drawText` through a canvas: IDR1,234,567,890.12 with a long place and a long name, and the largest amount in a currency with a 20-letter code. The reference pages did not change. | `6532f5b` |
| L1 | The daily run starts a day after it is scheduled. Each run keeps it while a recurring bill is active and cancels it otherwise. | `b50ddc3` |
| L2 | A run before Koin is started, or before its modules are loaded, returns `Result.retry()`. | `b50ddc3` |
| L3 | The catch-up runs on `Dispatchers.Default` and catches `Exception`, but rethrows `CancellationException`. A notice that throws no longer stops a run. A run is `NonCancellable`, so leaving the app half way through never leaves a draft without its notice. | `00203c2`, `11a5a83` |
| L4 | The next draft counts from the mark, and the list says "Draft due Nov 17, 2026" while that draft is due and not made yet. | `19c3453` |
| L5 | `create` clears the warnings of the first reading. | `5e6bc72` |
| L6 | Everyone but the user is told apart by their normalized name. Groups are told apart by id. "All time counts them" went with the rule for undated bills. Two notes are left, see below. | `b95a29c`, `8a5963a`, `0ebc0de` |
| L8 | The screen takes the PDF's name from `Exporter.pdfName`. Two points are left, see below. | `799a37b` |
| L9 | The migration test reads the bill of an unknown category back, as `other`. The fixture has a remainder, converted parts that differ from the converted total, and the zone `Asia/Ho_Chi_Minh`. The CSV guard is tried for each character in each column of text. The PDF test checks the text drawn. The repository test is named for what it checks. | `4a2842e`, `0ebc0de`, `e240101`, `6532f5b` |
| L10 | One `dayOf`, one `YOU`, `SavedBill.myShareMinor`, and one `storedJson` for both repositories. One function posts a notice, with the permission check beside the call. Each notice's tap carries its draft's address. The button now says "Change the day of the first draft". | `799a37b`, `4a2842e`, `f4566ce`, `11a5a83`, `36dd6fd` |
| L11 | Each draft is written in one transaction with the mark, and with the check that its recurring bill is still there, not paused and not made up to that day. Tests pause and delete recurring bills, and delete a draft, while a run is under way. | `fb457d9` |
| Domain L3 | Insights convert half up, as `ExchangeRate.convert` does by default. The hand-worked table moved by one dong in each sum that holds the user's $5.00 at 25,350.5. | `8a5963a` |
| Domain L9 | Groups are keyed by id and named as their newest bill names them. Test: two groups of one name, and a group renamed between two bills. | `8a5963a` |
| Notices | One notice per recurring bill per run. Its title is the recurring bill's name. Its text is "Its draft for Nov 17, 2026 is ready to check and finish." for one draft, and "2 drafts are ready to check and finish, the newest for Dec 1, 2026." for more. It opens the newest draft. It is `VISIBILITY_PRIVATE`, with a public version titled only "A recurring bill is due". | `11a5a83` |
| Undated bills | A bill without a readable date counts on the day it was saved, in the phone's zone, in every period. A note says how many bills were placed that way. The domain's `InsightBill` now takes a `LocalDate` day, which the app works out. | `0ebc0de` |
| Lint | See the outcome. | `f4566ce` |

Recurring bills still cannot be edited, only deleted and made again. The CSV export is still three files. The first draft is never before today, and a deleted draft is not made again.

## Left as they are

- **L6, a note for bills dated after the period.** That is not a defect. Such a bill belongs to a later period, as an older bill belongs to an earlier one. All time counts it, and its month heads the list there, so a mistyped year shows.
- **L6, a note for documents this version cannot read.** Nothing writes such a document:
  - this version reads every document it writes;
  - Room refuses a database from a newer version;
  - Android does not downgrade an app without wiping its data;
  - a category this version does not know now reads as `other` (`4a2842e`).
- **L7, reading every finished bill again.** This is about speed only. The statistics need each bill's shares, plan and rate, which live only in the JSON document. The period cannot be filtered in SQL, because an undated bill counts on its save day in the phone's zone. The decoding runs on `Dispatchers.Default`, and only while the screen is open.
- **L8, the quadratic pagination.** 3,000 finished bills, eight a day for a year, cost about 4.5 million list copies, which takes milliseconds. Only a timing test could fail without a fix.
- **L8, the cache.** The files sit in the app's private cache. Only the app, and the app picked for that one share through its grant, can read them. `allowBackup` is false, and the system clears the cache when space runs low.
- **L10, the shopping-cart icon.** `material-icons-core` has no chart icon, and the extended set is a dependency this task may not add.
- **L1, `ExistingPeriodicWorkPolicy.UPDATE`.** `KEEP` keeps the daily run's timing, and no installed app has this work yet. `UPDATE` on every foreground would rewrite the work's request each time. It is the policy to use once the request changes.
- **H2, a test through `SplitBillViewModel`.** Saving the day's rate on finishing belongs to the split screen (`99dbc25`). This fixture builds the day's rate exactly as that path gets it.

## Tests

| Module | Before (15:58 UTC) | After |
|---|---|---|
| `:core:domain:test` | 191 tests, 24 suites | 198 tests, 24 suites |
| `:core:designsystem:testDebugUnitTest` | 3 tests, 2 suites | 3 tests, 2 suites |
| `:app:testProdDebugUnitTest` | 307 tests, 53 suites | 342 tests, 57 suites |

All pass. My suites went from 74 tests to 95: one more in the domain, and twenty more in the app, with two new suites, `RecurringCatchUpTest` and `RepeatBillSheetTest`. The other agents added the rest.

| Check | Result |
|---|---|
| `ktlintCheck` | No finding in my files; fails only at `BillLifecycleTest.kt:265`, from `19e6ac4` |
| The three unit test tasks | Pass |
| `:app:lintProdDebug`, in its own invocation | Passes, with no issue in `app/src/main/java` |
| `:app:assembleProdRelease` | Builds. R8 keeps `RecurringBillWorker` under its own name |
| `:app:compileProdDebugAndroidTestKotlin` | Compiles, with `PdfSummaryOnDeviceTest` changed for the new `InsightBill` |

Each commit's tests passed on the working tree before it was committed, and each commit holds only my files. I did not rebuild every commit in a worktree of its own.

**Images recorded again.** I recorded only with `-Pscreenshots=record` and the gallery and PDF test classes, and looked at each image. These changed, as the fixture did:

- `40-insights` and `41-export`;
- `43-repeat-bill` and `45-history-categories`;
- each of those in the dark theme too;
- `46-pdf-summary-page-1` and `-2`, and `summary.txt`.

The images of `42-recurring` and `44-edit-bill-category` re-rendered byte for byte. Running the classes again checked every image.

## For the docs (M5, the orchestrator's)

`docs/architecture.md` should also say:

- **The catch-up.** It runs every time the app comes to the foreground.
- **The daily work.** It starts a day after it is scheduled, and stops itself once no recurring bill is active.
- **Drafts.** Each is made in one transaction with the check of its recurring bill and with the mark.
- **Notices.** There is one per recurring bill per run, private, with a public version.
- **Insights:**
  - a bill counts on its date, or on its save day in the phone's zone;
  - amounts convert half up;
  - groups are told apart by id, and people by name.
- **Stored JSON.** One setup reads bills and recurring bills, and coerces unknown values to their defaults.

The manifest needs no change.

## Open questions

1. **Notices of later runs.** Each run's notice is tagged with its newest draft, so a later run never replaces a notice the user has not seen. Tagging by recurring bill instead would keep one notice per bill in the shade, but the replaced notice's count would be lost. Which do you want?
2. **The notice's words.** For one draft, the title is now the recurring bill's name, and the text is today's text. For more, the text is "3 drafts are ready to check and finish, the newest for Dec 15, 2026." Is that the wording you want?
3. **Undated bills in the files.** The CSV and the PDF still show such a bill's date as written ("No date", "13/11/2026"). They list it on the day it counts on. Is that right?
4. **The other agent's line.** Should the orchestrator ask the split screen's agent to wrap `BillLifecycleTest.kt:265`, so that `ktlintCheck` passes on the branch?

```text
Status: DONE_WITH_CONCERNS
Summary: Every finding I was given is fixed with a test that fails without it, or left with a reason, and the unit tests, lint and the release build pass on the final tree.
Concerns/Blockers: My commits 11a5a83 to 799a37b fail :app:lintProdDebug, which f4566ce fixes. ktlintCheck fails only at the other agent's BillLifecycleTest.kt:265, from 19e6ac4.
```
