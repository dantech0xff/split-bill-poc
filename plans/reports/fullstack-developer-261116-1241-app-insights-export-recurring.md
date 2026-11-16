# App: categories, spending insights, CSV and PDF export, recurring bills

Date: 2026-11-16 (UTC). Branch `feat/phase-07-ai-assistant-insights`, on top of `b0c0a90`. Everything below is pushed.

## Outcome

All four app features of phase 7 that were mine are built, tested and pushed:

- every bill has one of the nine categories;
- an insights screen adds up the finished bills by month, category, person and group;
- the bills of a period export as three CSV files or a PDF summary;
- recurring bills make a draft and a notice on each of their days, also while the app is closed.

Every required check passes on the final tree, and each code commit passed the app's unit tests on its own tree in a separate git worktree.

| Check | Result |
|---|---|
| `ktlintCheck` | Passes on the whole repository |
| `:core:domain:test`, `:core:designsystem:testDebugUnitTest` | Pass |
| `:app:testProdDebugUnitTest` | 306 tests pass (258 before my commits) |
| `:app:lintProdDebug`, in its own invocation | Passes; no warning in my files |
| `:app:assembleProdRelease` | Builds. R8 keeps `RecurringBillWorker` under its own name with its `(Context, WorkerParameters)` constructor |
| `:app:compileProdDebugAndroidTestKotlin` | The new device test compiles |

One test that is not mine failed once in nine full runs, and passed when run again: `ClaimViewModelTest` (see open questions).

## What the user sees

- **Home** has two more buttons beside Groups and Requests: Insights and Recurring bills. They wrap onto a second line, and at twice the text size nothing is cut off.
- **The editor** has a Category section under the date and currency: nine chips (Food & drink to Other), one chosen, set through `onEdit { it.copy(category = …) }`. A bill read by the model comes with the parser's category.
- **The list of bills** shows each bill's category in its line ("2026-11-13 · Other · You, Minh, Lan"). The delete button became a ⋮ menu (`SvOverflowMenu`) with "Repeat this bill", for finished bills only, and "Delete". Home's recent bills show the category too, without the menu.
- **Insights**:
  - Periods are This month, 12 months (the current month and the 11 before) and All time.
  - A card shows the period, its total in the local currency and "Spent on N bills · Your share X".
  - Notes say what the totals leave out, such as "2 bills are left out of the totals, as no rate to VND is saved with them: 1 in EUR and 1 in THB." In a limited period, bills without a readable date are left out ("All time counts them"). In All time they count everywhere but in the months.
  - Then come the breakdowns by month (newest first), category, person ("What each person's part of the bills came to") and group ("No group" for bills in none). Each is a row of `SvListRow` with the amount and a bar as long as its share of the largest line. The bars are drawn in the theme's `primary` on `surfaceContainerHighest`, with no chart library.
  - There is an empty state for no finished bills at all, and another for a period without any.
- **Export**: "Export these bills" opens a sheet (`SvBottomSheet`) to choose CSV files or PDF summary.
  - "Save to files" uses the Storage Access Framework. The three CSV files go into a folder the user picks; the PDF goes into a file the user names.
  - "Share" hands the files to the share sheet through the `FileProvider`. Several files go at once, through `ACTION_SEND_MULTIPLE` with `ClipData` and read grants.
  - Files only go where the user chooses. A failure says so in a message card, and a phone without a file picker is told to share instead.
- **Recurring bills** lists each with its schedule, such as "Every month on the 31st, or the last day of a shorter month", and "Next draft Dec 31, 2026" or "Paused". Each row has a menu to pause or resume and to delete. Deleting asks first and says that the drafts made stay.
- **Repeat this bill** opens a sheet:
  - a name, the merchant by default;
  - Weekly or Monthly (`SvChoiceGroup`);
  - "First draft Dec 1, 2026", with Change opening a date picker that allows today or later. Until the user picks a day, the first draft comes one period after the bill's own date, today at the earliest.
  - Confirming opens Recurring bills. On Android 13 and later it asks for `POST_NOTIFICATIONS` when that has not been given.
- **The notice** of a new draft is on its own channel, "Recurring bills". It says "A recurring bill is due" and "Its draft for Dec 1, 2026 is ready to check and finish." Tapping it opens that draft on the split screen, through `AppLink.OpenBill`.

## The export formats

The three files are UTF-8 with a byte order mark, quoted as RFC 4180 says, with CRLF line ends.

- **Numbers.** Amounts are in major units with the currency's own number of decimals, with no grouping and no symbol, and the currency code is in a column of its own.
- **Separators.** `CsvConvention.of(locale)` picks commas and `.` when the locale's decimal mark is a point. It picks semicolons and `,` when it is a comma, as for Vietnamese and German.
- **Formulas.** Text a spreadsheet would take for a formula (`= + - @`, tab, CR at the start) gets an apostrophe in front. A name typed into a bill's link reaches `shares.csv`, so this matters.
- **Files.** `bills.csv` has one row per bill, `items.csv` one per item and charge, and `shares.csv` one per person on a bill. Each row carries the bill's id, so the files join.

The samples below are the reference files (`app/src/test/exports/decimal-point/`). `<CRLF>` stands for the line end.

```
bills.csv
Bill ID,Date,Place,Category,Group,Currency,Total,Your share<CRLF>
a1,2026-11-12,Quán Ốc Ba Miển,Food & drink,Flat 3B,VND,243000,81000<CRLF>
b1,2026-10-20,"Bowling ""Strike"", Inc.",Entertainment,,USD,30.00,10.00<CRLF>
c1,,Tops Market,Groceries,,THB,412.50,412.50<CRLF>

items.csv
Bill ID,Date,Place,Line,Kind,Quantity,Unit price,Amount,Currency<CRLF>
a1,2026-11-12,Quán Ốc Ba Miển,Ốc hương rang muối,Item,1,165000,165000,VND<CRLF>
a1,2026-11-12,Quán Ốc Ba Miển,VAT 8%,Tax,,,18000,VND<CRLF>
b1,2026-10-20,"Bowling ""Strike"", Inc.","Lane, 2 games",Item,2,12.50,25.00,USD<CRLF>
b1,2026-10-20,"Bowling ""Strike"", Inc.",Shoes,Item,1.5,4.00,6.00,USD<CRLF>
b1,2026-10-20,"Bowling ""Strike"", Inc.",Member discount,Discount,,,-3.50,USD<CRLF>

shares.csv
Bill ID,Date,Place,Person,Share,Paid,Currency<CRLF>
a1,2026-11-12,Quán Ốc Ba Miển,You,81000,243000,VND<CRLF>
b1,2026-10-20,"Bowling ""Strike"", Inc.",Lan,10.00,30.00,USD<CRLF>
b1,2026-10-20,"Bowling ""Strike"", Inc.",'=1+2,10.00,0.00,USD<CRLF>
```

The same rows with a decimal comma (`decimal-comma/`):

```
b1;2026-10-20;"Bowling ""Strike"", Inc.";Entertainment;;USD;30,00;10,00<CRLF>
b1;2026-10-20;"Bowling ""Strike"", Inc.";Lane, 2 games;Item;2;12,50;25,00;USD<CRLF>
```

The PDF summary (`spending-2026-11.pdf`, say) is A4 pages written with `android.graphics.pdf.PdfDocument`, in black and grey on white so it prints well. It holds the title and period, one sentence of totals, the notes, then tables by month, category, person and group in the local currency, and every bill in its own currency. A table that goes over a page break repeats its header, and each page has a footer with the period and its number. Its text, as the test compares it (`app/src/test/exports/summary.txt`), begins:

```
Spending summary
All time. Finished bills, in VND.
9 bills came to ₫24,562,000. Your share was ₫7,931,000.
2 bills are left out of the totals, as no rate to VND is saved with them: 1 in EUR and 1 in THB.
By month
Month | Bills | Total | Your share
November 2026 | 3 | ₫9,900,000 | ₫3,100,000
…
Split Vibe · All time | Page 1 of 2
```

## How drafts are made once, and caught up

- **The id.** A draft's id is `Recurrence.draftId(templateId, occurrence)`. I raised the conflict between the contract's `{templateId}_{date}` and the proxy, which takes only UUID bill ids for groups, requests and links. The coordinator then changed the domain's draft id to the name-based UUID of that string, so every device still works out the same id.
- **Made once.** `BillRepository.createIfAbsent` saves the draft as revision 0. `BillDao.write` checks that no bill has the id and writes it in one `@Transaction`. Of two runs that make the same draft, exactly one does and gets the bill back. Only that run posts the notice, so there is one notice per draft.
- **Caught up.** Each recurring bill keeps `madeThrough`, the last occurrence dealt with. A run makes every occurrence after it up to today, earliest first, then moves the mark on with `UPDATE … WHERE madeThrough < :day`, so the mark never goes back.
  - Days missed while the phone was off are made at the next run.
  - A draft the user deleted is not made again.
  - The mark starts at yesterday when a recurring bill is made, so a start date in the past only sets the day it comes back on.
  - Resuming moves the mark to yesterday, so the occurrences that passed while it was paused are not made.
  - A paused bill is not read by the run at all.
- **What a draft holds.** It is the template's bill with the occurrence's day as its date, and its plan, as an unfinished bill (`DRAFT`) with its shares worked out. It has no photos, read text, instruction, link, group, summary or rate.
- **When it runs.** `RecurringBillWorker` is a `CoroutineWorker` that takes `RecurringDrafts` from Koin through `KoinComponent`, so WorkManager's default factory creates it. Two unique works exist:
  - `recurring-bills` runs daily (`KEEP`);
  - `recurring-bills-now` runs once (`APPEND_OR_REPLACE`) after a recurring bill is made or resumed.

  When the activity opens fresh, `MainActivity` makes what is due directly, without waiting for the daily run. It also keeps the daily run while there is an active recurring bill. A database error makes the work retry.
- **Why not at application start.** WorkManager is not touched in `SplitVibeApp.onCreate`. Robolectric creates the application for every local test and does not run the manifest's startup providers, so `WorkManager.getInstance` failed in every test. Making the application a `Configuration.Provider` instead needs `WorkManagerInitializer` removed from the manifest, or WorkManager's lint check `RemoveWorkManagerInitializer`, which is FATAL, stops the build.

## The new dependencies

| Library | Version | Use |
|---|---|---|
| `androidx.work:work-runtime-ktx` | 2.12.0 | The daily work |
| `androidx.work:work-testing` | 2.12.0 | Tests only |

- **Versions.** 2.12.0 is the newest stable release, published on 23 September 2026, so more than two weeks old (`Last-Modified` of its POMs). androidx is not on Maven Central, and the mirror answers 404 for it. I read `maven-metadata.xml` and the POM dates from Google's Maven, which is where Gradle resolves them from. 2.12.0 needs minSdk 24, and the app has 26.
- **Checksums.** I wrote them once with a run that was not offline: `--write-verification-metadata sha256`, with the mirror init script, over the debug and release classpaths and the unit test compile. Pushing the build commit started `checksums.yml` on the branch (run 43891123888), because the version catalog changed. I did not dispatch it again: its concurrency group would have cancelled that run and started the same one over.
- **The clean-machine list.** The run passed. Its artifact `verification-metadata`, downloaded with `gh run download`, is byte for byte the file committed in `959b262`. Compared with the file before my commits, it removes nothing and adds only WorkManager's ten components:
  - `work-runtime`, `work-runtime-ktx` and `work-testing` 2.12.0;
  - `tracing-ktx` 1.2.0 and `concurrent-futures-ktx` 1.1.0;
  - `lifecycle-livedata`, `lifecycle-livedata-core` and `lifecycle-viewmodel-savedstate` 2.6.2;
  - `lifecycle-service` 2.6.2 and 2.10.0.

  So no further commit was needed. The later commits change no dependency, so the list holds for the final tree, which builds offline against it.

## The migration

- **What changes.** The database moves to version 3 with `AutoMigration(from = 2, to = 3, spec = CategoriesFromDocuments)`.
  - Room adds `bills.category TEXT NOT NULL DEFAULT 'other'` and creates `recurring_bills`.
  - `onPostMigrate` then reads each bill's `bill.category` from its JSON document and writes it to the column. A missing category, an unknown one or an unreadable document stays `other`.
- **The schema.** `app/schemas/.../3.json` is exported and committed, and each worktree build regenerated it unchanged.
- **The test.** `DatabaseMigrationTest` follows no earlier pattern, as there was none, and needs no new dependency.
  - It builds a version 2 database from the exported `2.json`: its tables, indices and identity.
  - It inserts bills saved with a category, without one and with one this version does not know.
  - It opens the database with Room as the app does. Room runs the migration and refuses to open unless the migrated schema is exactly version 3's.
  - It then checks the categories in the list and that the bills read back as saved, and writes and reads a recurring bill.

## Tests and screenshots

New and changed tests, all passing:

| File | Tests | What |
|---|---|---|
| `ui/insights/InsightsViewModelTest` | 7 | The mapping from `SavedBill` to `InsightBill` and the screen state, over 11 finished bills and a draft. The three periods are checked against tables worked out by hand: totals, months, categories, groups, people with their keys and names, the notes. CSV files to the share sheet through the file provider, and CSV files saved into a folder through a fake documents provider. A provider that is gone gives an error message |
| `export/CsvExportTest` | 6 | Byte-for-byte reference files in both conventions. Each file read back as a spreadsheet app in `en-US` and `vi-VN` reads numbers (`NumberFormat` must take the whole cell). The convention per locale. Byte order mark, CRLF, the formula guard |
| `export/PdfSummaryTest` | 3 | Robolectric has no native `PdfDocument` (it reports "document is closed"), so a byte comparison is impossible there. The pages' text is compared with `summary.txt`, the pagination with its repeated header is checked, and the pages, drawn at twice their size on a canvas as the PDF's pages are, are compared with screenshot images |
| `androidTest/export/PdfSummaryOnDeviceTest` | 1 | On a device, the real PDF file: `%PDF-`, `%%EOF`, one `/Page` and one A4 `/MediaBox` per page laid out. It compiles; it runs with the device tests, not here |
| `recurring/RecurringDraftsTest` | 8 | With a fake clock: a monthly bill on the 31st run daily from January 2027 to April 2028 makes exactly one draft per period, through months of 28, 29, 30 and 31 days, each on its day. Two runs at once, 20 rounds of 10 due drafts each, make each draft once with one notice. Missed months are caught up and made only once. A paused bill makes nothing and skips what passed meanwhile. Deleting a recurring bill keeps its drafts. A deleted draft is not made again. A start in the past. The draft's content |
| `recurring/RecurringBillWorkerTest` | 3 | `work-testing`: `TestListenableWorkerBuilder` runs the worker; one real notice per draft on its channel, whose tap opens `AppLink.OpenBill`; no notices without permission but the drafts still made; `schedule` with WorkManager's test runner (the one-time run, the daily run every 24 hours, `setPeriodDelayMet`, `KEEP`) |
| `data/bills/DatabaseMigrationTest` | 1 | See above |
| `data/bills/BillRepositoryTest` | +2 | `createIfAbsent` once only, the category column, `finished()` |
| `ui/AppLinkTest` | +1 | A draft's notice opens that bill; anything but a UUID opens nothing |
| `ui/recurring/RecurringViewModelTest` | 4 | Making, listing, pausing, resuming and deleting; the suggested first draft; the schedule's words |
| `di/SpendingModuleTest` | 1 | Koin creates every object of the new module |
| `ui/SpendingGalleryTest` | 12 | Screenshots, below |

- **New reference images.** Each is light and dark, except the PDF pages:
  - `40-insights` (a tall page with every breakdown)
  - `41-export`
  - `42-recurring`
  - `43-repeat-bill`
  - `44-edit-bill-category`
  - `45-history-categories`
  - `46-pdf-summary-page-1` and `-2`
- **Numbering.** I started at 40 because the assistant's images took 30 and 31 in the meantime.
- **Re-recorded.** These are the images of my screens in the shared galleries: `01-home` (also dark and large text), `14-back-swipe-preview` (home behind the split screen), `20-edit-bill` and `21-history` (also dark).
- **How I recorded.** I filtered `--tests` to the gallery methods that draw them. I backed up every image first and compared afterwards: only these changed, and every other image re-rendered byte for byte.
- **Review and stability.** I looked at every image recorded. Three verify runs in a row of the split flow galleries and of mine passed, so `21-history` is stable with the menu.
- **Recording the references again.** The CSV and PDF reference files are recorded with the screenshots, by `-Pscreenshots=record`, which sets `roborazzi.test.record`. A `.gitattributes` in `app/src/test/exports` keeps git from changing the CSV files' line ends.

## Commits

- `959b262` build: add WorkManager to make the drafts of recurring bills
- `0572bfa` feat(app): keep each bill's category in the list of bills, and recurring bills on the phone
- `44b2c4b` feat(app): add up the finished bills of a period and export them as CSV and PDF
- `eaccef8` feat(app): make a draft of each recurring bill on its days, with a notice that opens it
- `17720fa` feat(app): show the insights and the recurring bills, and pick each bill's category
- `62b30f4` test(app): check the recurring bills' view model and what the repeat sheet suggests
- this report

There is no separate commit for the checksums, because the clean machine's list equals the one in `959b262`.

`eaccef8` holds earlier versions of `AppNavHost.kt`, `SpendingModule.kt` and `SpendingModuleTest.kt`: only the line that opens a draft's notice, and the module without the recurring screen's view model. That is so the commit stands on its own; `17720fa` brings them to their final form.

## From the domain, and the manifest

- **The domain.** Nothing is missing. One interpretation: `InsightShare.personKey` does not say what to use for someone without a `personId`. I use `me` for the user, the `personId` when there is one, and otherwise `name:` with the name as `NameMatcher.normalize` leaves it, which is how the app tells people apart. A participant without a name falls back to a key of that bill alone. The domain's Insights test uses the same "me" key.
- **The manifest.** No change is needed. WorkManager merges its own provider, services and receivers, `RECEIVE_BOOT_COMPLETED` among them. `POST_NOTIFICATIONS` is already declared. The `FileProvider` gets the new `exports/` cache path in `res/xml/file_paths.xml`. Removing `WorkManagerInitializer` would only matter if the app ever wants its own WorkManager configuration.

## Open questions

1. **The notice's words.** The notice keeps `Notices`' rule that nothing anyone typed appears in a notice, so it names the day but not the recurring bill's name. Should it say "Rent", the name the user gave it, on the lock screen?
2. **Dates.** A recurring bill's first draft can be today at the earliest. Drafts are never made for days before it was made, and a deleted draft is not made again. Is that the intended behaviour?
3. **Insights periods.** In This month and 12 months, bills without a readable date are left out, with a note; they count in All time. Should they fall back to the day they were saved instead?
4. **A flaky test.** `ClaimViewModelTest` can fail with a `ConcurrentModificationException`: the test reads its `received` list while MockWebServer's thread appends to it. A synchronized or copy-on-write list fixes it. That file is not mine.
5. **Docs.** `docs/architecture.md` will need, once the phase lands, the navigation keys `InsightsKey` and `RecurringKey`, database version 3, WorkManager and `RecurringBillWorker`, the export, and `SpendingModule` next to `AppModule`. That is outside my files.

```text
Status: DONE
Summary: Categories in the editor and the list of bills, the insights screen, CSV and PDF export, and recurring bills with idempotent drafts, catch-up, a daily WorkManager run and one notice per draft are built and tested; ktlint, the unit tests, lint and the release build pass, and every commit is pushed.
Concerns/Blockers: none; the open questions are product choices and a flaky test outside my files.
```
