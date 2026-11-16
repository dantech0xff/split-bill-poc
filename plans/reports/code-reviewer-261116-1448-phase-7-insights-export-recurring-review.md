# Review: Phase 7 app work on categories, insights, export and recurring bills

Date: 2026-11-16 (UTC). Branch `feat/phase-07-ai-assistant-insights`. Commits `959b262`, `0572bfa`, `44b2c4b`, `eaccef8`, `17720fa` and `62b30f4`; the files were read at `HEAD`, where none has changed since `62b30f4`. The review was read-only: nothing changed but this report.

## Verdict

**Not ready to close the recurring bills and insights items.**

What holds up:

- the database move from version 2 to 3, `createIfAbsent`, the `madeThrough` mark and the one notice per draft;
- the CSV export, which is careful;
- the design-system rules, which the screens keep;
- every touched test suite, which passes.

Two High defects pass CI and would show in real use:

1. **The repeat sheet re-anchors month-end schedules.** It stores the clamped first draft as the schedule's start, so a rent dated the 29th to the 31st drifts for good to the 30th or the 28th. That breaks the shared contract.
2. **Insights can convert only hand-typed rates.** The app stopped saving the day's rate on 15 November, so only a rate the user typed is saved with a bill. The test fixture saves a day rate the app never writes, so the test passes on data that cannot occur.

Five Medium issues follow:

- the catch-up when the app opens misses warm starts and restored activities;
- people who joined from a bill's link become remembered people through drafts;
- a pick in the file picker after process death is dropped without a word;
- the PDF can cut amounts short;
- `docs/architecture.md` no longer matches the app.

No Critical issue was found.

## Scope and checks run

- **Scope.** 52 files; +2,293 and −28 lines in `app/src/main`, and +1,902 lines of tests (images not counted). The six commits were read together with their callers: `SplitBillViewModel`, `PeopleRepository`, `AppLink`, and the domain's `Insights` and `Recurrence`.
- **Tests.** `:app:testProdDebugUnitTest` for `*Recurring*`, `InsightsViewModelTest`, `CsvExportTest`, `PdfSummaryTest`, `DatabaseMigrationTest`, `BillRepositoryTest`, `AppLinkTest`, `SpendingModuleTest` and `DesignSystemBoundaryTest`: 11 suites, 49 tests, 0 failures, results stamped 15:30 UTC. `ktlintCheck` passes.
- **R8.** In `app/build/outputs/mapping/prodRelease/`, `mapping.txt` keeps `com.dantech.splitvibe.recurring.RecurringBillWorker` under its own name. `seeds.txt` keeps its `(Context, WorkerParameters)` constructor, as WorkManager 2.12.0's own rules ask (`-keepnames class * extends ListenableWorker`, plus its public constructors).
- **Checksums.** Workflow run 43891123888 ("Dependency checksums", on `959b262`) succeeded. Its `verification-metadata` artifact is byte-identical to the committed file.
- **Insights tables.** All three tables worked out by hand in `InsightsViewModelTest` were recomputed independently. Every total, share, order and note is right.
- **Schedules.** For H1, `Recurrence.occurrence`, `Recurrence.next` and `firstDraftDay` were replayed line for line in java.time, in a scratch program outside the repository.

## Findings

| # | Severity | Kind | Title |
|---|---|---|---|
| H1 | High | Verified | The repeat sheet re-anchors month-end schedules |
| H2 | High | Verified | Insights convert only hand-typed rates; the fixture saves a rate the app never writes |
| M1 | Medium | Verified (code path) | The catch-up runs only on a fresh `onCreate` |
| M2 | Medium | Verified (code path) | People who joined from a link become remembered through drafts |
| M3 | Medium | Plausible | A picker result after process death is dropped without a word |
| M4 | Medium | Plausible | The PDF cuts amounts short with "…" |
| M5 | Medium | Verified | `docs/architecture.md` no longer matches the app |
| L1 to L11 | Low | Mixed, tagged on each | Below |

### Critical

None found.

### High

#### H1. The repeat sheet re-anchors month-end schedules, so the 31st never comes back (Verified)

- **Where:**
  - `app/src/main/java/com/dantech/splitvibe/ui/recurring/RepeatBillSheet.kt:56-57` and `:125-136`;
  - stored as it is by `app/src/main/java/com/dantech/splitvibe/data/recurring/RecurringBillRepository.kt:67`;
  - every occurrence is counted from `start` in `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/Recurrence.kt:44-47`.
- **What is wrong:** Until the user picks a day, the first draft is the bill's next occurrence, which the domain clamps to the end of a shorter month. The sheet then builds `RecurrenceSchedule(frequency, start)` with that clamped day as the start. The contract ("Lịch định kỳ") counts every period from the start precisely so that the 31st comes back. With the start on the 30th or the 28th, it never does.
- **Failing scenario:** A bill "Rent" dated 2026-10-31. Today is 2026-11-16, and the user taps Repeat with the defaults (Monthly).
  - The sheet says "First draft Nov 30, 2026" and "Every month on the 30th, or the last day of a shorter month".
  - The drafts fall on 2026-11-30, 12-30, 2027-01-30, 02-28, 03-30 and 04-30.
  - The contract gives 11-30, 12-31, 01-31, 02-28, 03-31 and 04-30.
  - A bill dated 2027-01-31 and repeated on 2027-02-10 makes a draft on the 28th of every month.
  - A recurring bill cannot be edited; it can only be deleted and made again.
- **Evidence:**
  - `app/src/test/java/com/dantech/splitvibe/ui/recurring/RecurringViewModelTest.kt:108-109` asserts `firstDraftDay("2026-10-31", MONTHLY, 2026-11-16) == 2026-11-30`, under the comment "comes back on the last day of a shorter month". The test passes.
  - The month-end test, `app/src/test/java/com/dantech/splitvibe/recurring/RecurringDraftsTest.kt:91-110`, calls `recurring.create(…, RecurrenceSchedule(MONTHLY, 2027-01-31))` directly and never goes through the sheet.
  - The java.time replay prints the sequences above.
- **Fix:** Keep the schedule on the bill's own day, and move only the first draft.
  - Pass `RecurrenceSchedule(frequency, billDay)` and the first draft's day to `RecurringBillRepository.create`.
  - There, set `madeThrough = maxOf(today − 1 day, firstDraft − 1 day)`. That also keeps the bill's own occurrence from becoming a draft.
  - Describe that schedule in the sheet.
  - Add a test from `firstDraftDay` through `create` and `makeDue`, from November to March.

#### H2. Insights convert only hand-typed rates, and the fixture saves a day rate the app never writes (Verified)

- **Where:**
  - `app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsViewModel.kt:234`;
  - in `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt`:
    - `:89` says "shown, never saved";
    - `:564-590` is `lookUpDayRate`, which keeps the rate in memory only;
    - `:458-462` is `setExchangeRate`, the only code that saves a non-null rate;
  - the fixture, `app/src/test/java/com/dantech/splitvibe/SpendingFixtures.kt:86`.
- **What is wrong:**
  - Since `7f55d92` (15 November: "The day's rate is shown, not saved, so opening a bill never rewrites it"), `SavedBill.exchangeRate` is set only when the user types a rate.
  - Insights use only that saved rate, so every other bill in another currency ends up in `withoutRate`.
  - The fixture's `usdToVnd` saves a rate with `source = "Frankfurter"` and a date: a state the app no longer produces. The checks of "each bill's saved rate" therefore pass on data that cannot occur.
- **Failing scenario:** The local currency is VND. A trip to Bangkok gives 12 finished bills in baht, and each showed the day's rate on the result screen.
  - Insights for the month say "12 bills in THB are left out of the totals, as no rate to VND is saved with them."
  - The total, and the lines by person, group and category, all leave the trip out.
  - The CSV files and the PDF show those bills only in baht.
- **Evidence:**
  - In `app/src/main`, `exchangeRate =` is assigned only in `setExchangeRate` (a rate typed by hand) and in `app/src/main/java/com/dantech/splitvibe/ui/groups/AddToGroupViewModel.kt:208` (null).
  - `FxRepository` keeps only the latest day's rates, so it has no rate for a given date.
- **Fix:** This needs the lead's decision, because it touches the choice made on 15 November. The options:
  - **(a)** Save the day's rate with the bill when the user finishes the split. That is a write the user causes, so "opening a bill never rewrites it" still holds.
  - **(b)** Let insights use `FxRepository`'s latest rates for a bill without a saved rate, with a note that says today's rate was used.
  - **(c)** Keep the behaviour, but make the fixture realistic (rates typed by hand only), make the note say how to add a rate, and record the limitation in the plan.

  Whichever is chosen, add a test whose bills are saved through `SplitBillViewModel`'s real path.

### Medium

#### M1. The catch-up when the app opens runs only on a fresh start without saved state (Verified, code path)

- **Where:** `app/src/main/java/com/dantech/splitvibe/MainActivity.kt:29` and `:46-55`.
- **What is wrong:** The plan's answer to a delayed WorkManager is to catch up when the app opens ("Khi mở app, kiểm tra và tạo bù các kỳ đã lỡ"). But `catchUpOnRecurringBills()` runs only in `onCreate` with `savedInstanceState == null`, and two common cases never get there:
  - coming back to a live activity, which calls no `onCreate`;
  - an activity restored after the system ended the process, which has saved state.
- **Failing scenario:** A monthly rent due on 1 December, and the phone's battery manager holds the daily work back.
  - On 1 December the user opens Split Vibe from Recents. The process had been ended in the background, so the activity comes back with its saved state.
  - No draft is made and no notice is shown, and the Recurring screen says "Next draft Jan 1, 2027" (see L4).
  - The draft appears only when WorkManager runs, or after a fresh start from the launcher.
- **Evidence:** The code path. No test drives the activity's catch-up: only `RecurringDraftsTest` and `RecurringBillWorkerTest` call `makeDue`.
- **Fix:** Run `makeDue()` every time the app comes back to the foreground, which is safe because it is idempotent by design.
  - Use `repeatOnLifecycle(Lifecycle.State.STARTED)` or `ProcessLifecycleOwner`.
  - Drop the `savedInstanceState` condition for it.
  - Test both a recreated activity and a resumed one.

#### M2. People who joined a bill from its link become remembered people once a draft is finished (Verified, code path)

- **Where:**
  - `app/src/main/java/com/dantech/splitvibe/data/recurring/RecurringBillRepository.kt:42-43`: the template takes `saved.plan` whole;
  - `app/src/main/java/com/dantech/splitvibe/data/recurring/RecurringBill.kt:46-52`: a draft has no `share`;
  - `app/src/main/java/com/dantech/splitvibe/data/bills/BillRepository.kt:55-58`: strangers are only read from `worked.share?.added`.
- **What is wrong:** The rule that someone who joined from a link "is never suggested for later bills" (`docs/architecture.md:211-212`, and the comment at `BillRepository.kt:56`) is enforced only through `share.added`. The template copies those participants but not the link, so finishing a draft remembers them. Names typed into the web page, which come from strangers, become lasting suggestions.
- **Failing scenario:** A finished dinner where "An" joined from the link is repeated weekly.
  - When next week's draft is finished, "An" is written to the `people` table and is suggested on every new bill.
  - Insights then key An by `personId` on the draft and by `name:an` on the original bill, so "An" shows twice.
- **Fix:** When making the template, drop the participants the link added (`saved.share?.added?.values`) with `plan.withParticipantRemoved(id, bill)`. Or keep their ids in `RecurringBillContent` and leave them out when remembering. Add a test.

#### M3. A folder or file picked after the system ended the app is ignored without a word (Plausible)

- **Where:**
  - `app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsViewModel.kt:143-145`: `val shown = state.value ?: return`;
  - the pickers' launchers, at `app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsScreen.kt:87-92`.
- **What is wrong:** `state` starts as `null` and fills only after the database has been read. If the process dies while the system's file picker is open, the restored screen gets the picked address before the first state, and `exportAs` returns without saying anything.
- **Failing scenario:** A phone with little memory, or with "Don't keep activities" on.
  - The user taps Export, then Save to files, and the system ends Split Vibe behind the picker.
  - The user picks `Documents/Split`, and the app comes back.
  - Nothing is written and no message appears.
- **Fix:** In the coroutine, wait for the state with `state.filterNotNull().first()`. When the period has no bills, say so instead of returning silently.

#### M4. The PDF cuts amounts short with "…" (Plausible)

- **Where:** in `app/src/main/java/com/dantech/splitvibe/export/PdfSummary.kt`:
  - `:227`: every cell is ellipsized;
  - `:266-272`: `BILL_COLUMNS` leaves about 82 points for an amount at 9.5 points;
  - `:220`: `Row.text` is the full text, not what is drawn.
- **What is wrong:** A shortened name is fine, but a shortened amount reads as another number. The reference text cannot catch it, because the test compares `Row.text` rather than the text drawn on the page.
- **Failing scenario:** An estimate from Roboto's character widths, not a measurement.
  - A villa bill of "IDR15,000,000.00" is 16 characters, about 82 to 85 points, and is drawn as "IDR15,000,00…".
  - Amounts in LAK, UZS or IRR in the tens of millions are cut the same way.
- **Fix:** Never ellipsize the amount columns (`alignEnd`).
  - Size them from the widest amount in the table, or make the text of that one cell smaller.
  - Add a test with a long name and a ten-digit amount with decimals, and check the string that is drawn.

#### M5. `docs/architecture.md` no longer matches the app (Verified, docs gap)

- **Where:** `docs/architecture.md:27`, `:58` ("one Koin module that lists every long-lived object"), `:68-70` (the navigation keys) and `:274`.
- **What is missing:**
  - `spendingModule`;
  - the keys `InsightsKey` and `RecurringKey`;
  - database version 3 and its migration;
  - WorkManager and `RecurringBillWorker`;
  - the export, with its `exports/` path in the FileProvider.

  The builder already lists this as open question 5.
- **Fix:** Update those sections, and add two short subsections: "Insights and export" and "Recurring bills".

### Low

#### L1. The daily work is never cancelled, and its first run doubles the immediate run (Verified)

- **Where:** `app/src/main/java/com/dantech/splitvibe/recurring/RecurringBillWorker.kt:42-55` and `app/src/main/java/com/dantech/splitvibe/MainActivity.kt:50`.
- **What is wrong:**
  - Nothing calls `cancelUniqueWork`, so the job still wakes every day after the last recurring bill is deleted or paused. The docs say it runs "while there is a recurring bill".
  - A new periodic request runs its first period at once, so `schedule()` starts two workers. That is harmless only thanks to `createIfAbsent`.
  - `KEEP` means a later change to the request, such as its interval or constraints, never reaches phones that already have the app.
- **Fix:**
  - Cancel `DAILY` when `anyActive()` is false.
  - Give the periodic request an initial delay.
  - Consider `ExistingPeriodicWorkPolicy.UPDATE`.

#### L2. The worker may run before Koin is started (Plausible)

- **Where:** `app/src/main/java/com/dantech/splitvibe/recurring/RecurringBillWorker.kt:27`.
- **What is wrong:**
  - WorkManager's initializer runs in a content provider, before `SplitVibeApp.onCreate`.
  - After a forced stop or a crash, `ForceStopRunnable` can start pending work on a background thread before `startKoin` returns.
  - That run then fails with "KoinApplication has not been started". It is not a crash, and the next period runs as usual.
- **Fix:** Either of:
  - start WorkManager on demand, with a `KoinWorkerFactory`;
  - return `Result.retry()` when `GlobalContext.getOrNull()` is null.

#### L3. The catch-up's error handling (Verified, code path)

- **Where:** `app/src/main/java/com/dantech/splitvibe/MainActivity.kt:47-54` and `app/src/main/java/com/dantech/splitvibe/recurring/RecurringDrafts.kt:32-34`.
- **What is wrong:**
  - Only `SQLException` is caught in `lifecycleScope`. Any other exception crashes the app as it starts, for example a `RuntimeException` that `NotificationManager.notify` throws on some manufacturers' builds.
  - The CPU part of `save()`, working out the shares and the JSON, runs on the main thread.
  - If `notify` throws after a draft was written, that draft never gets a notice: the next run finds it already made.
- **Fix:**
  - Run `makeDue` on `Dispatchers.Default`.
  - Catch `Exception`, rethrowing `CancellationException`.
  - Guard `showDraft` so that a failure there doesn't stop the run.

#### L4. The Recurring screen hides an overdue draft (Verified)

- **Where:** `app/src/main/java/com/dantech/splitvibe/data/recurring/RecurringBill.kt:43`.
- **What is wrong:** `next(today)` starts after the later of `madeThrough` and yesterday. A draft that is due but not yet made therefore shows as the period after it.
- **Fix:** Show "Draft due <day>" while `due(today)` is not empty.

#### L5. Drafts copy the first occasion's warnings (Verified)

- **Where:** `app/src/main/java/com/dantech/splitvibe/data/recurring/RecurringBill.kt:50`.
- **What is wrong:** `bill.warnings` from the first reading, such as "The bill total could not be read…", lands on every draft. The class comment says nothing of one occasion is kept.
- **Fix:** Clear `warnings` in `create`.

#### L6. Insights leave some bills out without a note, or count one person twice (Verified, code path)

- **Where:**
  - `app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsViewModel.kt:185-186`, `:226-230` and `:260-263`;
  - `app/src/main/java/com/dantech/splitvibe/data/bills/BillRepository.kt:33-43`.
- **What is wrong:**
  - Bills dated in the future, and documents this version cannot read, are left out without a note.
  - The note "All time counts them" is wrong for an undated bill that has no rate.
  - Forgetting a suggested person gives the same name a new `personId`, so "Lan" shows twice.
  - Someone who joined from a link and a remembered person with the same name are two lines with the same label.
  - A group renamed between two bills shows as two groups.
- **Fix:**
  - Key everyone but the user by their name as normalized; the people table is already unique on that.
  - Add notes for bills dated in the future and for bills that cannot be read.

#### L7. Insights read every finished bill again on each change (Verified, code path)

- **Where:** `app/src/main/java/com/dantech/splitvibe/data/bills/BillRepository.kt:33-43` and `app/src/main/java/com/dantech/splitvibe/data/bills/SplitVibeDatabase.kt:80-81`.
- **What is wrong:** Every write to `bills` reads and decodes all the finished documents again, read text included, and the period is filtered in memory. That is fine for hundreds of bills but slow for thousands.
- **Fix:** Select only the columns needed, or filter by `date` in SQL.

#### L8. Smaller points in the export (Verified, code path)

- **What is wrong:**
  - Laying out the pages takes O(n²) time because of `blocks.drop(index)` (`app/src/main/java/com/dantech/splitvibe/export/PdfSummary.kt:150`), which matters for an all-time export of thousands of bills.
  - The shared files are cleaned out of the cache only on the next share, so they stay there until then (`app/src/main/java/com/dantech/splitvibe/export/Exporter.kt:62-64`).
  - `app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsScreen.kt:166` repeats `Exporter.pdfName`.
- **Fix:**
  - Find the blocks that stay together by index instead of copying the list.
  - Clean the cache when the files are written.
  - Call `Exporter.pdfName` from the screen.

#### L9. Some tests claim more than they check (Verified)

- **The migration test.** `app/src/test/java/com/dantech/splitvibe/data/bills/DatabaseMigrationTest.kt:85-102` inserts a bill with the category "pets" but never reads it back.
  - This version cannot decode it: `BillRepository.kt:118-121` has no `coerceInputValues`, so opening that bill would throw.
  - So "a category this version does not know is other" holds for the list's column only.
  - The case cannot happen today, since Room refuses to open a database from a newer version.
- **The insights fixture** divides evenly everywhere. It has:
  - no remainder;
  - no bill whose converted parts differ from its converted total;
  - no time zone but UTC (`TestClock`).
- **The CSV test.** `app/src/test/java/com/dantech/splitvibe/export/CsvExportTest.kt:55` checks the formula guard only for "=", and only in the Person column.
- **The PDF test.** `app/src/test/java/com/dantech/splitvibe/export/PdfSummaryTest.kt:41-49` compares the full text, not the text drawn on the page (see M4).
- **The repository test.** The "two writers" test in `BillRepositoryTest` writes one after the other. The test that really runs two at once is in `RecurringDraftsTest`.
- **Fix:**
  - Read the third bill back in the migration test, or add `coerceInputValues`.
  - Add uneven amounts and a time zone other than UTC to the fixture.
  - Test each guarded character in each free-text column.
  - Check the text that is drawn.

#### L10. Repeated code and small points (Verified)

- **Repeated code:**
  - "read yyyy-mm-dd, or nothing" is written five times: `InsightsViewModel.kt:276`, `RepeatBillSheet.kt:126`, `Notices.kt:124`, `PdfSummary.kt:169`, and once in the domain;
  - the word "You" is spelled out in five files;
  - the lookup of the user's own share appears three times;
  - two repositories set up the same `Json`;
  - `showDraft` repeats `show`'s permission check.
- **Small points:**
  - the "Change" button tells a screen reader nothing about what it changes;
  - the Insights button uses a shopping-cart icon;
  - each notice's tap uses `id.hashCode()` as its request code, so two drafts whose ids had the same hash would share one tap. A unique `data` address per draft rules that out.
- **Fix:** One helper for reading a day, one constant for "You", and labels with context.

#### L11. Changes during a run (Plausible)

- **Where:** `app/src/main/java/com/dantech/splitvibe/recurring/RecurringDrafts.kt:29-36`, which reads the recurring bills once per run.
- **What is wrong:**
  - A recurring bill paused or deleted while a run is under way still gets its drafts.
  - A run that started before the user deleted a draft can make that draft again.
  - The window for both is a few milliseconds.
- **Fix:** Check that the recurring bill is still there and not paused in the same transaction as the write.

## Checked and found correct

- **The move from version 2 to 3.**
  - The new column, `category TEXT NOT NULL DEFAULT 'other'`, matches both the entity and `3.json`.
  - `onPostMigrate` runs inside Room's migration transaction, and `document` is never null.
  - `parseToJsonElement` throws only `SerializationException`, which is caught. A category that is missing, unknown or not a string becomes `other`, as does a document that is not an object.
  - No domain class checks anything on creation, so decoding throws nothing else.
  - The moves from 1 to 2 to 3 run in a row.
- **Bills written by other code.** Every bill is written through `BillRepository.save`, whose `toEntity` sets `category`. The parser sets the category after `BillReconciler`, the only call to `Bill(...)`. No writer leaves the column out of date.
- **`createIfAbsent`.** The check for revision 0 and the write are one `@Transaction`, and an existing bill is never overwritten. Two runs at once, 20 rounds of 10 drafts each, make each draft once, with one notice each.
- **The `madeThrough` mark.** It never moves back (`WHERE madeThrough < :day`, and `MAX` on resume). Days are stored as ISO strings, which sort like the days themselves.
- **Recurring behaviour:**
  - pausing and resuming skip the days in between;
  - a deleted draft is not made again;
  - deleting a recurring bill keeps its drafts;
  - nothing is made for days before the recurring bill;
  - drafts have no photos, read text, instruction, link, group, summary or rate.
- **Clock and time zone.**
  - `PhoneClock` reads `ZoneId.systemDefault()` on every call, so it follows a change of zone.
  - The date picker's arithmetic in UTC milliseconds is right.
  - The sheet's "today" uses the same zone.
- **Notices:**
  - there is one per draft, tagged with its id, posted only by the run that made the draft;
  - permission is checked on Android 13 and later, and the drafts are still made without it;
  - a tap opens `AppLink.OpenBill`, which accepts only ids in UUID form;
  - a draft deleted meanwhile says "This bill is no longer on this phone."
- **WorkManager:**
  - `KEEP` and `APPEND_OR_REPLACE` are the right policies;
  - R8 keeps the worker under its name, with its constructor;
  - Koin provides `RecurringDrafts`;
  - with `allowBackup="false"`, a recurring bill never reaches a second phone.
- **Insights:**
  - only `FINAL` bills count, and `converts` checks both currencies;
  - the periods are right: this month, the 12 calendar months from 1 December, and all time;
  - the three hand-worked tables are right.
- **CSV:**
  - each file has a byte order mark, and CRLF ends every line, the last one too;
  - fields are quoted as RFC 4180 says;
  - numbers use the locale's decimal mark without grouping, and negative discounts stay numbers;
  - every free-text column is guarded against `= + - @`, tab and CR, which is OWASP's list;
  - the reference files are compared byte for byte, and `.gitattributes` marks them `-text`.
- **PDF:**
  - a heading stays with its header and first row, and the header is repeated on a new page;
  - the footer says "Page x of y";
  - paragraphs wrap;
  - the `PdfDocument` is closed in `finally`.
- **Where the files go:**
  - sharing uses a chooser, with `ClipData` and a read grant;
  - the FileProvider is not exported, its new path is `cache-path exports/`, and each share gets a folder of its own;
  - errors from another app's provider become an `IOException` and a message;
  - a phone without a file picker is told to share instead.
- **The screens:**
  - `DesignSystemBoundaryTest` passes, and the screens use only the theme's tokens and `Sv` components;
  - icon buttons and menus have labels;
  - there are empty states for no finished bills, a period without bills, and no recurring bills;
  - back works through `backStack.remove(…Key)` and `NavDisplay`;
  - the home buttons wrap at twice the text size;
  - group names are 1 to 60 characters long, so the keys of the list cannot collide.
- **The build.** `work-runtime-ktx` and `work-testing` are at 2.12.0, and the ten new components are the ones the builder's report lists.

## The builder's claims, checked

| Claim | Result |
|---|---|
| ktlint and the unit tests pass | Confirmed for ktlint and for the 11 touched suites (49 tests); the full 306 tests were not run again |
| R8 keeps `RecurringBillWorker` | Confirmed, in `mapping.txt` and `seeds.txt` |
| The checksums from a clean machine equal `959b262` | Confirmed: byte-identical |
| A monthly bill on the 31st makes one draft in each period | True of the repository; false of the sheet's default (H1) |
| Two runs at once make each draft once | Confirmed |
| The activity makes what is due as the app opens | Only on a fresh start (M1) |
| The daily run is kept while there is a recurring bill | Scheduled, but never cancelled (L1) |
| Each participant counts under the same key on every bill | Mostly (M2, L6) |
| A bill's saved rate counts only when it converts | True, but the only saved rates are typed by hand (H2) |
| The PDF's pages are compared with reference text | The text compared is not what is drawn (M4) |
| A failure says so in a message card | Except after process death (M3) |
| Files go only where the user chooses | Confirmed |

## Plan follow-ups (for the lead; the plan was not edited)

- **"Trường `category`, `Insights`, màn hình thống kê" and "Xuất CSV và PDF":** built. Tick them once H2 is decided and M4 is fixed.
- **"Hóa đơn định kỳ với WorkManager":** built. Tick it once H1 and M1 are fixed.
- **"Ảnh chuẩn cho màn hình mới":** done for these screens.

## Metrics

- Type safety: no `Any`, unchecked casts or suppressions were added.
- Test coverage: not measured.
- Lint: ktlint has no findings. Android Lint was not run again.

## Unresolved questions

1. **H2.** Should the day's rate be saved when a split is finished, or should insights use today's rate? Or is leaving foreign bills without a typed rate out of the totals the intended behaviour? Any change touches the decision of 15 November.
2. **H1.** Should the default schedule stay on the bill's own day, as the contract reads? And should a recurring bill be editable?
3. **M2.** Should a recurring bill keep the people who joined the original bill from its link at all?
4. **Saving the CSV files.** They need a folder, and from Android 11 the picker refuses the top of Download. Would one ZIP file, saved through the file picker, be better?
5. **Notices after a long gap.** A weekly bill posts one notice per draft, so 13 after three months away. Would one summary notice be better?
6. **L1.** Should the daily job stop when no recurring bill is active?

```text
Status: DONE_WITH_CONCERNS
Summary: The migration, the idempotent drafts, the CSV export and the UI rules hold up, and the 49 tests of the touched suites pass. Two High defects need a fix or a product decision first: the repeat sheet re-anchors month-end schedules, and insights convert only hand-typed rates while the fixture saves a day rate the app never writes. Five Medium issues follow.
Concerns/Blockers: H2 needs the lead's decision on saving the day's rate.
```
