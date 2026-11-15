# Code review: Phase 4 app branch

- Branch: `feat/phase-04-capture-editing-split-modes`. Range: `git diff main...HEAD`, 10 commits from 94b01b8 to ea2d4ed.
- Date: 2026-11-15 (UTC). Review only: no source file was edited, nothing was committed.
- Scope: 132 files changed. 88 are code or config (+6,318/−943), and 33 of those are main sources (+3,013/−329). The rest are screenshots.
- How it was reviewed: I read every changed main source file in full and traced the save, restore, conflict and exchange-rate paths by hand. I ran the checks listed below. I also probed the domain with a throwaway JUnit test in a clone placed in the session scratchpad, not in this repository.

## Checks run

| Check | Result |
|---|---|
| `./gradlew ktlintCheck` | Passed |
| `:core:domain:test`, `:core:designsystem:testDebugUnitTest` | Up to date. They had already passed on exactly these inputs. |
| `npm test` in `proxy/` | 96 tests passed in 8 files |
| `:app:testProdDebugUnitTest`, `:app:lintProdDebug`, `:app:assembleProdRelease` (R8) | **Could not run here.** Gradle cannot resolve `:app:kspProdDebugKotlinProcessorClasspath`: "Could not find guava-33.2.1-jre.jar, auto-value-annotations-1.6.3.jar, kotlinpoet-jvm-2.0.0.jar…", although the jars are in `~/.gradle`. Maven Central answers HTTP 429 from this sandbox. This is a problem with the environment, not the code. The session scratchpad holds an init script that puts a Google mirror of Maven Central first (`central-mirror.init.gradle`), but the permission system denied this review running Gradle with it. The app's unit tests, screenshots, Android lint and R8 are therefore **not verified** by this review. |
| `gradle/verification-metadata.xml` | Spot check: Room 2.8.5 (runtime, ktx, compiler, sqlite), KSP 2.3.12 and `play-services-mlkit-document-scanner` 16.0.0 all have entries |
| Design-system boundary | I ran the forbidden-pattern regexes of `DesignSystemBoundaryTest` over `app/src/main/java` and `app/src/debug/java`: no matches |

What the domain probe showed:
- Two items named "Beer" are split by percent. After one person is removed, the split reports two identical problem lines. Two blank-named items produce two identical "An item: …" lines.
- Payments are You 60,000 and Minh 60,000 on a 120,000 bill. After Minh is removed, the payments are `[You 60,000]` and the problem reads "₫60,000 short".
- `BillEditor.withItemQuantity(…, "99999999999999999999")` on an item with a unit price throws `java.lang.ArithmeticException: Overflow`.
- Item ids are i1, i2, i3. After deleting i3 and adding an item, the ids are `[i1, i2, i3]`.
- `BillCurrency("", 2).parse("33.333")` returns 3,333,300.
- `BillCurrency.resolve("IDR", null, [])` and `resolve("HUF", null, [])` both give 2 fraction digits.

## Overall assessment

The money core is solid. The shares add up to the total on every path I traced, and the property test exercises every mode. The data layer is small and careful: the revision check is transactional, page file names are validated, and the contact picker reads only the display name.

The risk sits in the view model's lifecycle and in the UI built on top of it:
- One crash on the result screen can become permanent for a saved bill.
- One path silently overwrites a saved bill.
- The conflict detection relies on a revision map that does not track what each edit was based on.
- Saves are tied to `viewModelScope`.
- Late exchange-rate lookups overwrite what the user typed, and merely opening a bill can rewrite it.
- The README and architecture docs still describe the app before this phase.

I did not find a critical issue: no path produces shares or payments that silently fail to add up.

## High

### H1. Two items with the same name crash the result screen (duplicate LazyColumn keys)
- **Where:** `app/src/main/java/com/dantech/splitvibe/ui/split/ResultContent.kt:288`, `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/SplitCalculator.kt:84`.
- **What:** When splitting by item, each invalid item division produces the problem `"<item name>: <problem>"`, or `"An item: …"` for a blank name. Two lines with the same name and the same kind of problem therefore produce identical strings. The result list keys every problem as `"problem $text"`. LazyColumn then throws `IllegalArgumentException: Key "…" was already used` as soon as both are composed.
- **Scenario:**
  1. A receipt has two "Bia" lines (common), or two items were added in the editor and left without names.
  2. Choose By item, and split each "Bia" by percent: Minh 50, Lan 50.
  3. Remove Lan, or tap "You" off when You were among the percentages.
  4. The problems are now two copies of "Bia: Percentages add up to 50%. They need to add up to 100%." (confirmed by the probe).
  5. Scroll to "Each person pays": the app crashes.

  The people chips are near the top, so the problems are usually off screen when the person is removed. The change is then autosaved 0.4 s later, and the saved bill crashes the screen every time it is reopened and scrolled.
- **Fix:** Key the problems by position (`itemsIndexed(result.problems, key = { i, _ -> "problem $i" })`), or return structured problems that carry the item id. Add a test with two same-named items.

### H2. Typing a bill by hand, or reading another photo, after going back from a result overwrites that saved bill
- **Where:** `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:245` (`id = billId ?: …`), `:282`, `:310-313` (`backToInput` keeps `billId`), and `:501` (the blank-bill guard applies only while the stored revision is 0).
- **What:** `backToInput()` keeps `billId`, and `startManualEntry()` builds an empty `SavedBill` with that id. The guard "an empty bill is not worth keeping" is skipped because the id already has a stored revision, so the first keystroke replaces the saved bill.
- **Scenario:**
  1. Use the sample bill, then Read and split: bill X is saved with 9 items.
  2. Tap the back arrow, then "Type it in by hand".
  3. Type "C" in Where.
  4. 0.4 s later, X is rewritten as `{merchant "C", no items}`. The reading, its OCR text, the plan, the instruction and the summary are gone from History, and nothing asked first.

  "Choose photos" followed by Read and split on a *different* receipt does the same thing: the first receipt disappears. The `analyze()` comment says replacing is intended for reading the same bill again, but nothing checks that it is the same bill.
- **Fix:** Decide what is intended (question 1).
  - If the user is starting a second bill, give manual entry and a reading of new pages a fresh id.
  - If replacing is meant, confirm before the first write, and keep the blank guard until the replacement has content.

## Medium

### M1. The revision check can overwrite another writer, and reports conflicts that are not there
- **Where:** `SplitBillViewModel.kt:425` (`storedRevisions.putIfAbsent`) and `:501-519`. Also `MainActivity`: launch mode `standard` and no `onNewIntent`.
- **What:** Every save expects `storedRevisions[id]`, which is the newest revision this view model has seen, not the revision the edited copy was derived from. This causes two problems:
  - (a) After a conflict, the handler moves `storedRevisions` to the other writer's revision but leaves `pendingSave` untouched. A change queued from the copy that was refused is then written against the new revision, and it wins.
  - (b) `open()` and `restore()` keep any older value because of `putIfAbsent`. The first edit after reopening a bill that another writer has changed is then refused and dropped.
- **Scenario:** A second writer exists today: sharing a photo from the gallery opens a second `MainActivity`, which has its own activity-scoped `SplitBillViewModel` with its own `storedRevisions` and `saveLock`.
  - For (b): instance 1 saved X at revision 2. In instance 2 the user opens X and edits it (revision 3). Back in instance 1 the user opens X from History and sees revision 3, then edits. The save expects revision 2, the notice "changed elsewhere" appears, and the edit is discarded.
  - For (a): instance 1 still shows X at revision 2 and the user edits. While that refused save is being handled, the user types again. The queued copy (the revision 2 content plus the edits) is saved over revision 3. The screen now shows revision 3 while the database holds the other copy.
- **Fix:**
  - Carry the base revision in the copy itself: after a successful write, set the revision of the `SavedBill` on screen to the written one if it is still the same bill, and pass that revision to `save`.
  - On a conflict, clear any pending save for that bill.
  - In `open()` and `restore()`, assign the loaded revision instead of using `putIfAbsent`.

  The plan's risk table also says a refused edit should be kept and re-applicable; today it is thrown away. If only one instance is intended, consider `singleTask` with `onNewIntent` for shares.

### M2. A save already running when the activity finishes is cancelled, and `onCleared` only rescues the queued one
- **Where:** `SplitBillViewModel.kt:178-191` and `:493-523`.
- **What:** `ViewModel.clear()` cancels `viewModelScope` before it calls `onCleared()`. The debounced collector removes the change from `pendingSave` (with compare-and-set) before it calls `persist`. So a write that is in flight is cancelled, and `saveNow(CoroutineScope(Dispatchers.IO))` finds nothing left to save.

  If the cancellation lands after Room committed but before `storedRevisions` was updated, a newer pending change saved from the IO scope expects the old revision. It hits `BillConflictException` and is dropped, and the handler updates a screen that no longer exists.
- **Scenario:** Edit, pause about 0.4 s so the write starts, then press back out of the app during that write. The last change is missing next time. The window is narrow, but it is exactly the "close right after typing" case this code is meant to cover.
- **Fix:** Run every persist in an application-scoped scope, for example a Koin `single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }`, or wrap the write and the `storedRevisions` update in `withContext(NonCancellable)`.

  A related ordering issue: `startNewSplit()` and `open()` call `forgetUnsavedPages()` right after `saveNow()`. If the first save of a bill that was just read is still pending, this deletes its photos, because `unsavedPages` is only trimmed after the write.

### M3. The day's rate, arriving late, replaces a rate the user typed
- **Where:** `SplitBillViewModel.kt:461-467` and `SplitSheets.kt:303-325`.
- **What:** After the lookup, `ensureExchangeRate` checks only the bill id and the currency, then runs `change { exchangeRate = found }`. A rate set through `setExchangeRate` in the meantime is overwritten.

  There is a related problem in the sheet. `ExchangeRateSheet` remembers `typed` once. If the fetched rate arrives while the sheet is open, the field resyncs to the fetched rate, but Save still sends the value typed earlier, so what is saved differs from what is shown.
- **Scenario:**
  1. The network is slow (roaming) and the cached rates are more than an hour old.
  2. Open a USD bill with VND as the local currency.
  3. Tap "Add a rate", type 26000, Save. The bill shows "a rate you entered".
  4. A few seconds later the lookup completes, and the bill silently switches to Frankfurter's 25,350 and is autosaved.
- **Fix:** Keep the lookup `Job` and cancel it in `setExchangeRate`, or skip the result when `!replace && current.exchangeRate?.converts(current.bill, to) == true`. In the sheet, key its state on `rate`, or do not resync a field that has focus.

### M4. Opening a bill after a change of local currency destroys its rate and re-saves it; a bill that is already open ignores the change
- **Where:** `SplitBillViewModel.kt:442-457` and `:468`. `settings` is only ever read through `.value`.
- **What:** When a bill is shown, `ensureExchangeRate` does one of three things:
  - it drops the stored rate when the bill no longer needs one;
  - it replaces the stored rate when a rate for the new pair is found;
  - it drops the stored rate when the lookup fails.

  Each of these is a change, so the bill is autosaved with the next revision and `updatedAt = now`. Merely viewing a bill rewrites it, a FINAL one included.

  In the other direction, `SplitResultState` captures `localCurrency` once. If the local currency is changed in Settings while a split is open (the view model survives a visit to Settings), the old conversion stays on screen until the next edit. After that edit the conversion disappears and no new rate is looked up.
- **Scenario:**
  1. A USD bill has the card rate 26,000 VND typed in.
  2. In Settings, change the local currency to USD.
  3. Open the bill from History: the rate is removed and the bill jumps to the top of History.
  4. Change the local currency back to VND: the card rate is gone for good, and only today's rate can be fetched.
- **Fix:** On open, treat the rate as display data: filter it, but do not delete or replace the stored one unless the user changes something. Alternatively, keep one rate per target currency. Collect `settings` in the view model and run `ensureExchangeRate` again when the local currency changes.

### M5. A finished bill can be stored as FINAL with no shares, or with payments that do not add up
- **Where:** `SplitBillViewModel.kt:404-420`, `app/src/main/java/com/dantech/splitvibe/data/bills/BillRepository.kt:31-36`, and `SavedBill.withSharesWorkedOut()`.
- **What:** `finish()` checks for problems once. Later edits keep the status FINAL. `save()` writes `shares = []` when the split is incomplete, and it never checks the payments.

  The plan requires the money invariants to hold on the stored document, not only in memory. Phase 5 balances will read the saved shares of FINAL bills.
- **Scenario:** Finish a split, open it from History, switch to By amount and type a single amount. The autosave stores status FINAL with `shares []`, and History shows the bill without the "Draft" label. The same happens after removing one of two payers (L3).
- **Fix:** When a FINAL bill gets problems, store it as DRAFT, or refuse to persist FINAL with problems (see question 2). Add a repository test.

### M6. Restore after process death drops photos picked after going back, and leaves their files behind
- **Where:** `SplitBillViewModel.kt:310-313`, `:477-484` and `:526-545`.
- **What:** `backToInput()` keeps `KEY_BILL`. `restore()` prefers the stored bill over `KEY_PAGES` and `KEY_INSTRUCTION`, and it never adds the `KEY_PAGES` files to `unsavedPages`.
- **Scenario:**
  1. Read bill X, go back, and choose a different photo. It is stored in `filesDir/bill_pages`.
  2. The system kills the process in the background, and the user returns.
  3. The result of X is shown again. The new photo is not shown, and its JPEG is never deleted (a privacy and storage leak). The screen also comes back on the result step, which the user had left.
- **Fix:** Save which step was on screen.
  - If it was the input step, restore `KEY_PAGES` and the instruction, and keep `KEY_BILL` only as the id to reuse.
  - Otherwise, add the names that are not in `saved.pages` to `unsavedPages`.

### M7. Large numbers typed in the editor crash the app
- **Where:** `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/BillEditor.kt:120-121` (`longValueExact()`), `app/src/main/java/com/dantech/splitvibe/ui/editor/BillEditorContent.kt:191-206` and `:311-312`, and `SplitSheets.kt:340-341`.
- **What:** `lineAmount` throws `ArithmeticException` when quantity × unit price does not fit in a Long. `editBill` runs it on the main thread, and nothing catches it. `positiveQuantity` and `positiveRate` accept any number the 20-character field can hold, including exponent notation such as "1e30". The probe confirmed the throw.
- **Scenario:** An item has a price of 25,000. In the Qty field, type 20 nines or paste "1e30": the app crashes. Pasting "1e999999999" as a rate makes the BigDecimal scaling and formatting try to allocate gigabytes.
- **Fix:** Bound the quantity (for example ≤ 10,000) and prices and amounts (the parser's limit of 1e12), reject exponents, and make `lineAmount` return null instead of throwing.

### M8. The README and the architecture doc still describe the app as it was before this phase
- **Where:** `README.md:9, 27, 31, 141, 151-154, 159, 171, 177, 198, 284` and `docs/architecture.md:25, 32`.
- **What:** They still describe:
  - seeded in-app contacts (`SeededContactRepository`, `Contact`);
  - matching names in the instruction to contacts and pre-selecting them;
  - "nothing is converted";
  - "129 tests".

  They say nothing about Room, History, drafts, the editor, the scanner, the split modes or exchange rates. The plan lists `README.md:219` among the files this phase changes.
- **Fix:** Update the README sections "What the app does", "How it works" (currency, matching), the project layout, the tests and the mock-data note, and the module table in `docs/architecture.md`.

## Low

### L1. The check against the app's own FileProvider can be bypassed with a `userId@` prefix
- **Where:** `app/src/main/java/com/dantech/splitvibe/MainActivity.kt:41`, and `SharedImagesTest` has no test for it.
- **What:** `uri.authority` includes the user info. `content://0@com.dantech.splitvibe.fileprovider/bill_photos/<file>.jpg` passes the `startsWith(packageName)` check, and `ContentResolver` then strips the `0@` user prefix and opens the app's own non-exported provider.
- **Impact:** Small. Only `cache/bill_photos` is exposed, and nothing goes back to the sender. Still, the KDoc promises "never a file of this app's own", and that does not hold.
- **Fix:** Reject `uri.userInfo != null` and compare `uri.host`, or reject when `packageManager.resolveContentProvider(uri.host, 0)?.packageName == packageName`. Add this case to the test.

### L2. `ContactNames` trusts whatever the contact picker returns
- **Where:** `app/src/main/java/com/dantech/splitvibe/data/people/ContactNames.kt:16-26`, and `SplitBillViewModel.kt:344-353`.
- **What:**
  - There is no check that the URI belongs to `ContactsContract.AUTHORITY`.
  - The name is not length-capped, although every text field caps at 80 characters.
  - Only `SecurityException` and `IllegalArgumentException` are caught.
- **Scenario:** Any app can answer `ACTION_PICK` for contacts. If it returns, for example, `content://com.dantech.splitvibe.fileprovider/bill_photos/x`, FileProvider returns a cursor without the `display_name` column. `getString(0)` then throws `CursorIndexOutOfBoundsException` inside `viewModelScope.launch`, and the app crashes.
- **Fix:** Check the authority, use `getColumnIndex`, catch `RuntimeException`, and apply `take(MAX_TEXT_CHARS)`.

### L3. Removing one of several payers leaves a partial single payment
- **Where:** `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/Payment.kt:26-29`, and `SplitPlan.kt:83-92` and `:141-146`.
- **What:** The probe showed the result: `[You 60,000]` on a 120,000 bill. `SplitPlan`'s KDoc says "a single payer always paid the whole bill". The next unrelated edit to the bill (`followTotal`) silently turns this into You paying 120,000.
- **Fix:** Pick one rule. Either the one payer left pays the total, or the problem stays shown and is not fixed automatically on the next edit.

### L4. Changing the currency leaves payments and exact amounts behind, and hand-entered IDR and HUF bills get cents
- **Where:** `BillEditor.kt:22-31`, `SplitBillViewModel.kt:242` and `:328-336`, and `BillEditorContent.kt:101-107`.
- **What:**
  - `withCurrency` keeps the bill's amounts as written, but the amounts of several payers and the By amount values stay in the old minor units: $30.00 is stored as 3000, which becomes ₫3,000. A problem is shown, so no wrong split is saved, but the values become meaningless.
  - `resolve(code, null, …)` gives IDR and HUF two decimals. For bills typed by hand, this contradicts the README's statement that a rupiah or forint bill is split in whole units.
- **Fix:** In `editBill`, convert the plan's amounts when the currency changes, or reset them to an even split. Give hand-entered bills in such currencies 0 decimals.

### L5. Typing "33.333" in a percent field means 33,333%
- **Where:** `app/src/main/java/com/dantech/splitvibe/ui/split/DivisionInputs.kt:28` and `:62`, and `BillCurrency.parse`.
- **What:** With three digits after it, the separator is read as a thousands separator (the probe gave 3,333,300 basis points). The total row shows an error, so no wrong split is saved, but a natural way to type thirds fails in a confusing way.
- **Fix:** Use a percent parser that always treats the last separator as the decimal point, and rounds or rejects more than two decimals.

### L6. Ids are reused after the last item or person is deleted
- **Where:** `BillEditor.kt:126-130` and `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/Participant.kt:22-25`.
- **What:** The KDoc says an id is never handed out twice on one bill, but the probe shows that deleting i3 and adding an item yields i3 again. This is harmless today because `withBill` drops the old division, but the plan wants stable item ids for Phase 6 claims.
- **Fix:** Keep a per-bill counter in `Bill`.

### L7. Every name is remembered at the next autosave, typos included, and `forget()` has no caller
- **Where:** `app/src/main/java/com/dantech/splitvibe/data/people/PeopleRepository.kt:25-36` and `BillRepository.kt:33`.
- **What:**
  - Add "Mihn" and correct it 2 s later: "Mihn" stays in the suggestions, and only clearing the app's data removes it.
  - Every autosave also bumps `lastUsedAt` for everyone on the bill.
- **Fix:** Remember people when the split is finished, or debounce longer, and offer a way to remove a suggestion.

### L8. The camera's originals outlive the bill
- **Where:** `app/src/main/java/com/dantech/splitvibe/ocr/BillImageLoader.kt:48-52` and `BillPages.kt:13-16`.
- **What:** The full-resolution capture stays in `cache/bill_photos` until the next capture. Deleting a bill only deletes its `BillPages` copy, although the confirmation dialog says "its photos are deleted from this phone".
- **Fix:** Delete the capture once it has been copied into `BillPages`. The document scanner's own output may need the same treatment (question 5).

### L9. The baseline-profile journey waits for text that no longer exists
- **Where:** `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt:23`.
- **What:** `Until.hasObject(By.text("Contacts"))` now always times out after 10 s, and the result is ignored. Every iteration idles for 10 s and no longer checks that it got back home.
- **Fix:** Wait for "Recent bills" instead.

### L10. Amount fields in the editor: a typed leading "0" vanishes, and retyping a line amount drops its unit price on the first key
- **Where:** `BillEditorContent.kt:207-216` and `:308`, and `BillEditor.kt:65-69`.
- **What:**
  - `amountText` shows 0 as "". Typing "0" over "5.00" stores 0, and the field resyncs to "".
  - Retyping 50,000 for an item of 2 × 25,000 stores 5 on the first keystroke. That amount disagrees with the unit price, so the price is dropped and never comes back.
- **Fix:** Commit these fields when they lose focus or on the IME action, or keep the price when the final amount agrees with it.

### L11. The History search field and its query disagree after process death
- **Where:** `app/src/main/java/com/dantech/splitvibe/ui/history/HistoryScreen.kt:62` and `HistoryViewModel.kt:20`.
- **What:** `typed` is `rememberSaveable`, but the view model's query is not saved. After the process is recreated, the field shows "pho" while the list shows every bill.
- **Fix:** Keep the query in `SavedStateHandle`, or call `onQueryChanged(typed)` when the screen starts.

### L12. Accessibility: blank item names and the "You" chip
- **Where:** `ResultContent.kt:189-194` and `:490-491`.
- **What:**
  - A blank-named item row is read as ", 1 × …, ₫10,000, Everyone" and its action as "Choose who had ", while the screen draws the name "Item".
  - The "You" chip only shows visually whether you share the bill. The hint above it says "Tap someone to rename them or take them off", which does not apply to You.
- **Fix:** Use the same fallback name in the labels, and give the You chip a state description such as "You share the bill".

### L13. Smaller items
- `app/src/main/java/com/dantech/splitvibe/data/proxy/ProxyApi.kt:96` and `:109-114`: the fx-rates GET still sends the install id and app version, although `docs/proxy.md:29-30` says this route needs neither.
- `app/src/main/java/com/dantech/splitvibe/llm/GatewayBillAnalyzer.kt:144`: the TOO_LONG message still says "Try photographing it in two parts". Two parts are now sent as one document and fail with TOO_MANY_PAGES.
- `app/src/main/java/com/dantech/splitvibe/scan/DocumentScanner.kt`: `startIntent` turns every exception into null. The screen then says the phone has no Google Play services and hides "Scan" for as long as the screen lives, even after a transient failure.
- `SplitBillViewModel.kt:232-235`: there is no cap of `MAX_PAGES`. On older phones the photo picker falls back to `ACTION_OPEN_DOCUMENT`, which takes any number of photos.
- `SplitBillViewModel.kt:520-521`: raw exception messages are shown to the user. If saving keeps failing, the dialog comes back on every keystroke.
- Dead or misleading code: `BillReconciler.DIFFERENCE_LABEL` is unused, and its KDoc is wrong (the screen says "Difference to the printed total"). `PeopleRepository.forget` is unused.
- Test gaps:
  - Every view model test uses `saveDelayMs = 0`.
  - Nothing covers `saveNow`, `onCleared`, or a conflict with a change still queued.
  - The multi-page format is checked only against the encoder's own output (`GatewayBillAnalyzerTest:289`).
  - There is no recorded two-page fixture, which a success criterion of the plan asks for.

## Edge cases found while scouting
- Two lines with the same name or a blank name, combined with item divisions that become invalid (H1).
- Going back from a result keeps the bill's id for whatever comes next on the photo step (H2, M6).
- Sharing a photo opens a second activity and a second view model, so there are two writers in one process (M1).
- The activity finishing while a save is in flight (M2).
- An exchange-rate lookup that is slower than the user (M3), and a change of local currency between saving and opening a bill (M4).
- Editing a finished bill (M5).
- A pasted exponent or a 20-digit number (M7).
- A contact picker that returns a non-contacts URI (L2), and a `userId@` authority (L1).

## Verified and fine (for calibration)
- **Split arithmetic:** in `SplitCalculator`, the shares add up to the total in whole minor units on every path (whole-bill modes, item pools, item divisions, charges shared in proportion). Proportional parts use BigInteger, and `SplitPropertyTest` runs 2,000 bills per mode.
- **Revision write:** `BillDao.write` checks the revision and upserts inside one `@Transaction`.
- **Photo files:** `BillPages` accepts only UUID file names before it touches any file.
- **Contacts and permissions:** the contact picker queries only `DISPLAY_NAME`. The manifest has neither `READ_CONTACTS` nor `READ_MEDIA_IMAGES`.
- **What leaves the phone:**
  - A bill reading sends only the OCR text, the instruction and the local currency to the proxy.
  - Photos stay in `filesDir`.
  - The fx GET has no body.
- **Exchange-rate parsing:** rates written as strings, negative rates and rates without a date are dropped. Numbers are read from their literal text. The rates file is written to `.partial` and then renamed. IO and proxy failures fall back to the kept copy, with a 5-minute pause before trying again.
- **Search:** the Room search normalizes the query like `searchText` and escapes it for LIKE with `ESCAPE '\'`. The escaping is in fact redundant, because `normalize` already removes `%`, `_` and `\`.
- **Typed text in fields:** `InputField` and `NumberField` keep what was typed and resync through `sameValue` correctly on the paths I traced (a trailing space in a name, "12." becoming 12.00, an empty total). The `Main.immediate` collection keeps the instruction field in step with what was typed.
- **Proxy input:** `OcrLayoutEncoder` replaces `<` and `>` inside fragments. The proxy still checks the `<bill_ocr …></bill_ocr>` envelope, and the prompt treats page text as data.
- **Proxy `FxBook`:** refreshes are single-flight. The pause after a failed refresh applies only to refreshes triggered by requests, and the cron bypasses it. The tests cover all three.

## Plan follow-ups (`phase-04-bill-capture-editing-split-modes.md` Todo)
- **Look complete:**
  - property tests for the 5 ways of splitting and for several payers;
  - `Person`, `Participant` and `itemId`, with `TestPeople`;
  - `BillEditor`, the editor screen and manual entry;
  - the scanner, the picker fallback and receiving shared photos;
  - multi-page `analyze` and `encode`, with the prompt updated;
  - `PeopleRepository` and the system contact picker, with the seeded contacts removed;
  - the exchange-rate cron, `FxRepository`, the converted amounts and typing a rate by hand;
  - Room storage with revisions, History, search, delete and autosaved drafts;
  - new screenshots and an extended flow test.
- **Still open:**
  - a recorded two-page fixture and the device check;
  - the conflict mitigation in the risk table (keep a refused edit and let the user re-apply it);
  - the docs (M8).
- **Out of scope by the owner's decision:** sync between devices, server rules, and deleting people and drafts together with the account.

## Recommended actions, in order
1. H1: fix the problem keys and add a test with two items of the same name.
2. H2: decide how the photo step should treat the existing bill, then fix it (fresh id or confirmation).
3. M5: stop FINAL bills from being stored with problems.
4. M1: base the expected revision on the copy that was edited, clear a pending save on conflict, and assign the loaded revision on open and restore.
5. M2: move saves to an application-scoped or `NonCancellable` scope, and fix the order of `saveNow()` and `forgetUnsavedPages()`.
6. M3 and M4: cancel or skip late rate lookups, stop rewriting bills on open, and observe `settings`.
7. M6: restore the step that was on screen and its photos.
8. M7: bound numeric input and remove the throw from `lineAmount`.
9. M8: update the README and the architecture doc.
10. The Low items.
11. Run the app's unit tests, Android lint and the R8 release build on CI, or on a machine whose Gradle cache resolves KSP. This review could not run them.

## Metrics
- Type coverage: Kotlin and TypeScript throughout. The diff adds no `!!`, and only two broad `catch (e: Exception)`, in `persist` and `DocumentScanner`.
- Test coverage: not measured.
- Lint: ktlint reports 0 issues. Android lint was not run (see Checks run).

## Unresolved questions
1. Should "Type it in by hand", or a reading of different photos after going back, replace the bill that was read (as it does now, silently) or start a new one?
2. When a FINAL bill is edited, should it go back to DRAFT, block changes that leave problems, or keep FINAL and refresh its shares only when it is complete?
3. When the local currency changes, should a rate the user typed for the old currency be kept, for example one rate per target currency?
4. Is `standard` launch mode intended for `MainActivity`? With it, each share opens a second instance with its own split view model, which is the second writer in M1.
5. Where does the Google document scanner write its output (the app's cache?), and should the app delete those files? Does `getStartScanIntent` fail while the scanner module downloads on first use? If it does, the app hides scanning for the session with a message that says the phone has no Google Play services.
6. Gradle in this sandbox reports "Could not find" for KSP processor jars that are in the cache. I did not run `--refresh-dependencies`, to avoid touching the shared cache. Running with the scratchpad's `central-mirror.init.gradle` was denied by the permission system. Someone with permission needs to run `:app:testProdDebugUnitTest`, `:app:lintProdDebug` and `:app:assembleProdRelease` (with the mirror init script, or on CI) before merging.
