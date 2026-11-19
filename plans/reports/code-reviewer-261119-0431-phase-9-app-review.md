# Phase 9 app review: deletion with the phone's data, legal links, texts in resources, version, domain fix

Date: 2026-11-19 (UTC). Reviewer: code-reviewer. Read-only review; no code, test or doc changed.

## Code Review Summary

### Scope
- Commits: `905249c`, `a4d69c5`, `a2ab66c`, `40aace7`, `078cb98`, `b60d981`, `5712bdc`, `01943f3`, `5dec25d` (app part only), `242ff9f`, `1414ada`, and `d03f0a6` in `core/domain`.
- Files: 67 main files under `app/src/main/java`, `strings.xml` (701 strings, 44 plurals), `app/build.gradle.kts`, 40 test files and 4 new images, `docs/architecture.md`, `SplitCalculator.kt` and its test.
- Contract: phase-09 "Hợp đồng chung" (App part) and the deletion matrix; the implementer's report `fullstack-developer-261119-0147-app-release.md`, whose choices were judged, not assumed.
- How it was checked:
  - every diff, and the code around it;
  - scripted audits, kept in the session scratchpad:
    - all 892 `R.string`/`R.plurals` call sites in main code against their placeholders and argument types;
    - every English literal removed in the scoped commits, matched against the resources, and the reverse direction;
    - every method added after API 26, from the SDK's `api-versions.xml` (android-36), searched for in `core/domain` and `app/src/main`;
  - Roborazzi 1.76.0's comparison defaults, read from its AAR;
  - `Resources.getString(int, Object...)`, read from the Robolectric android-all 14 bytecode;
  - one JShell check of how `%d` renders under several locales;
  - the lint report and unit-test results the last run left on disk.
  No Gradle run.

### Overall Assessment
The string refactor holds up:
- every argument count and `%d` type matches;
- no `UiText` reaches the screen unresolved;
- the English is unchanged apart from the one sentence the report names.

The deletion asks the proxy first and runs as one Room transaction for the tables. It is safe against the recurring-bill worker and the proxy cache. `toLongExact` is exactly equivalent to `longValueExact`.

What does not hold:
- The deletion is tied to the Settings screen's lifetime, and nothing finishes a deletion that was interrupted (M1).
- Some smaller gaps in what is deleted and what is reported.
- A change in how counts are formatted under locales with their own digits.
- A pseudolocale check that skipped the screens most likely to clip.
- Docs sentences that claim more than the code does.

There are no Critical or High findings.

### Critical Issues
None.

### High Priority
None.

### Medium Priority

**M1. Leaving Settings, or the app dying, while `DELETE /v1/account` is in flight loses the deletion. The code comment and the docs say the opposite.**

Where:
- `SettingsViewModel.kt:58-60, 63-77` runs `repository.deleteAccount` in `viewModelScope`.
- That view model belongs to the Settings entry (`AppNavHost.kt:202, 339-340`). It is cleared whenever Settings is popped: by Back, by the top-bar arrow (`SettingsScreen.kt:116`, `AppNavHost.kt:363`), or by Save, which stays enabled while "Working…" shows (`SettingsScreen.kt:176`, `AppNavHost.kt:357-361`).
- Cancelling the coroutine cancels the OkHttp call (`ProxyApi.kt:335-336`).
- Only the part after the proxy's answer runs in `NonCancellable` (`AccountRepository.kt:71`). Yet the comment at `AccountRepository.kt:69-70`, the report and `docs/architecture.md:107` ("also when the person leaves Settings meanwhile") promise more.

Failure scenarios:
1. **Answer lost after the proxy deleted.** The person ticks the box, taps Delete, and presses Back while the 204 is on its way. The answer is lost: `suspendCancellableCoroutine` never resumes a cancelled caller with a value. So `api.deleteAccount()` throws `CancellationException`, and on the phone nothing happens:
   - the stored session, the groups/requests cache, every bill, person, payment method and page photo, and the name all stay;
   - no message is shown.
2. **What the person sees next.** The next time Settings opens, `refresh()` gets 401 (the proxy ended every session of the deleted account). Settings then shows "Your sign-in as … has run out". The box is only offered with a live session, so the phone's data can now only be deleted by:
   - signing in again, which makes a new account, and deleting again;
   - or clearing the app's storage.
3. **App killed.** The same happens when the app is swiped away or killed during the request.
4. **Server cut part-way.** Earlier in the request, cancelling closes the connection. Cloudflare ends a Worker's invocation when the client cancels (see Sources), so the proxy's deletion, RevenueCat first, can stop part-way. Making that resumable is the proxy's side, but the app should not cause it.

Fix:
- Run the whole deletion, request included, outside the screen's lifetime: either `withContext(NonCancellable)` around the call in `act`, or an application-scoped coroutine in `AccountRepository` that publishes the outcome so a reopened Settings can show it.
- Do not let Back or Save silently abandon it, or say that it continues.
- For process death, persist the choice before sending (for example a `commit()`ed "delete with phone data" marker) and reconcile on the next start. At least tell the person when it could not finish.
- Correct `AccountRepository.kt:69-70` and `docs/architecture.md:107`.

### Low Priority

**L1. A failure on the phone is not always reported, and an interrupted local deletion is never finished.**
- **File deletions are not checked.**
  - `BillPages.deleteAll()` ignores every `File.delete()` result (`BillPages.kt:39-41`), and `PhoneData` ignores `deleteRecursively()` (`PhoneData.kt:41`).
  - A page photo that cannot be deleted still ends in "…and so were the bills, people and payment methods on this phone." (`SettingsViewModel.kt:59`).
  - The report's "When the phone's data cannot all be deleted … `PhoneDataException` is thrown" holds for the database only.
- **A cache failure reads as a failed deletion.**
  - `cache.clear()` sits outside the `try` (`AccountRepository.kt:72`). A database error there escapes after the account is gone and the session is cleared.
  - The person then reads "Something went wrong" (`SettingsViewModel.kt:106`) for a deletion that worked.
  - Google's credential state is not cleared, and the ticked box is ignored.
- **A kill mid-deletion leaves leftovers.**
  - A kill between the transaction (`PhoneData.kt:31-37`) and the last step (`PhoneData.kt:44`) leaves page photos in `filesDir/bill_pages` that no bill refers to and nothing ever sweeps, plus the name.
  - `forgetName()` and `clearSession()` use `apply()`.

Fix:
- Throw `PhoneDataException` when a file that should go still exists.
- Move `cache.clear()` inside the `try`.
- Rerun the idempotent `PhoneData.delete()` at start while the M1 marker is set.

**L2. Two copies of deleted data stay on the phone.**
- **Document-scanner pages.** They sit in the app cache outside the three folders `PhoneData` clears (`PhoneData.kt:40`). `BillImageLoader.discard` (`BillImageLoader.kt:47-66`) removes them only after a successful load (`SplitBillViewModel.kt:307-309`). A failed or interrupted load therefore leaves bill photos behind a "delete everything".
- **Recurring-draft notices.** A posted notice keeps the deleted recurring bill's name as its title (`Notices.kt:129-131`) and opens a deleted draft when tapped. A draft the worker made just before the transaction is announced after it (`RecurringDrafts.kt:39-46`).

Fix: delete the whole contents of `cacheDir` (it is a cache, and "Clear cache" does the same), and cancel the app's notices (`NotificationManagerCompat.cancelAll()`, or the drafts channel's) in `PhoneData.delete()`.

**L3. Counts now take the phone's digits; the numbers beside them do not.**
- **What changed.** Counts used to be Kotlin templates, so always ASCII. They are now `%d` arguments, and `Resources.getString(int, Object...)` formats with `getConfiguration().getLocales().get(0)` (read from the framework bytecode). On a phone set to Persian, Arabic, Bengali, Marathi and others, the English UI therefore shows that language's digits: `String.format(fa-IR, "%d", 300)` gives U+06F3 U+06F0 U+06F0.
- **Pro screen.** "Pro gives you ۳۰۰ AI calls a month instead of ۱۰" (`ProScreen.kt:138, 143, 165, 205`) sits just below "7 of 10 calls left this month". Those numbers pass through `%s` as `toPlainString()` (`SettingsScreen.kt:374-384`, `AccountStore.kt:81`), so they stay ASCII.
- **Recurring bills.** "Every month on the ۳۱st" (`RecurringScreen.kt:164-171`, `strings.xml:898-901`).
- **Wait hints.** "Try again in about ۴۰ minutes." (`ProxyModelCall.kt:228-229`).
- **Not caught.** The en-US screenshots cannot catch this. Money was already formatted with `Locale.getDefault()`.

Fix: pick one rule. For an English-only v1.0 the smallest is to pass counts as `%1$s` with `count.toString()`, which is the old output. Otherwise, localize every number, `CallsLeft.text` included, and form ordinals with ICU.

**L4. `ContentWords`, read once in the language the process starts in, is shown on screen.**
- The totals by person in Insights use `words.you` and `words.someone` (`InsightsViewModel.kt:269, 272`; `AppModule.kt:59`), and the screen shows them (`InsightsScreen.kt:226`).
- After the phone's language changes while the process lives, that row stays in the old language while the rest of the screen follows the new one.
- `ContentWords.kt:6-11` and `docs/architecture.md:75-79` say these words are not shown on screens.
- There is no effect while v1.0 has one language.

Fix: keep "the user" and "no name" as flags in the screen model, resolve them at composition, and resolve through `Exporter.text` for the files.

**L5. The en-XA images skip the places where clipping is likeliest, so "nothing to fix" is not established.**
- `InputFields.kt:47-48` makes every field label and placeholder one line, with the default clip and no ellipsis.
- These fields include the editor's "Qty" (80 dp, `BillEditorContent.kt:368`), "Price each" and "Amount", side by side (`BillEditorContent.kt:218-243`), and `NumberField` in the split sheets.
- "[Ǫţý one]" or "[Þŕîçé éåçĥ one two]" in those widths would be cut silently.
- None of the four en-XA screens (home, result, Settings, Pro) has such a field. The report's "nothing cut off … because every text wraps" (report lines 104-105) is true of those four screens only.
- Not rendered: based on width arithmetic, so it needs one image to confirm.

Fix: add an en-XA image of the bill editor and of a sheet with `NumberField`, and let these labels ellipsize or wrap.

**L6. Two exceptions to "every text in resources" do not hold up as stated.**
- **The PDF summary's text.** Its headings, footer, page numbers and summary sentence stay in code (`PdfSummary.kt:56, 58, 78-133, 182`; `Exporter.kt:141, 144`).
  - The stated reason is that the device test builds `PdfSummary(content, Locale.US)` (`PdfSummaryOnDeviceTest.kt:67`).
  - But a third constructor parameter with a default keeps that call compiling, while `Exporter` passes words from the resources.
  - As it stands, a second language needs code changes, which the plan's language requirement is meant to avoid.
- **English grammar in code.** The ordinal suffix (st/nd/rd/th) and the weekday in `Locale.ENGLISH` (`RecurringScreen.kt:162-171`) are decided in code. The KDoc says so, but the report's exception list does not.

Fix: apply the default-parameter route, or record both as accepted v1.0 debt in the plan.

**L7. `docs/architecture.md` (new in `1414ada`) says more than the code does.**
- `:107`: see M1.
- `:96-98` says that when the proxy refuses, nothing on the phone changes and the account stays signed in. In fact a 401 expires the session on the phone (`AccountRepository.kt:65`, `AccountStore.kt:176-188`).
- `:104` says the screen "then says what is left". It only says that not all could be deleted, and file failures are not reported (L1).
- `:82` says dates are written with English month and day names. Many follow the phone's locale instead (`HistoryScreen.kt:221`, `RecurringScreen.kt:187`, `GroupScreen.kt:429`, `Notices.kt:117`, `ClaimScreen.kt:496`, `PdfSummary.kt:184`).
- `:64` says a new language needs "no change to the code". The list at `:80-85` and L6 contradict it.
- `:75-79`: see L4.

**L8. The legal links fail silently for TalkBack, and that path has no test.**
- "No app on this phone opens web pages." appears without a live region (`Links.kt:52-58`). A screen-reader user who taps a link on a phone without a browser therefore hears nothing.
- The Pro screen's existing "Google Play could not be opened" message has the same gap (`ProScreen.kt:318-324`). Both could use the `announced` modifier the screen already has (`ProScreen.kt:383`).
- No test covers the no-browser path; Robolectric's `checkActivities(true)` would make it testable.

**L9. Hardening: a deletion counts as done on any 2xx, even after a redirect.**
- `ProxyApi.kt:123-125` accepts any `isSuccessful` response, and the default client follows redirects (`ProxyApi.kt:250-257`). OkHttp turns a 301 or 302 for a DELETE into a GET.
- So a redirect rule on the proxy's zone that lands on a 200 page would make the app clear the session and, with the box ticked, the phone's data, while the account still exists.

Fix: the contract's answer is 204. Require `response.code == 204 && response.priorResponse == null`, or turn off redirects for this client.

### Checked and found sound
- **Deletion order.** The proxy is asked first. Any `ProxyException` (500, 429 with `Retry-After`, 502, 401) or `IOException` is rethrown before anything on the phone changes (`AccountRepository.kt:61-67`). `AccountRepositoryTest` runs 500, 429 and 502 in turn and checks every count, the files, the page, the name and the session. A 401 only expires the session, as before.
- **Transaction.** The five tables go in one Room transaction. The `DROP TABLE recurring_bills` test proves the rollback: "no table is half deleted".
- **Races.** None let a writer outlive the deletion:
  - a recurring run re-checks its row inside its own transaction (`RecurringBillRepository.makeDraft`), so it cannot make a draft after the deletion;
  - `BillDao.write` refuses a stale revision, so the activity-scoped split view model shows "deleted elsewhere" instead of bringing back a saved bill;
  - `ProxyCache`'s generation refuses late answers;
  - push messages write nothing locally.
- **What stays.** Only what the contract lists goes. The currency, the speech consent, the install id, the exchange rates and the notice-permission flag stay. `android:allowBackup="false"`, so no cloud copy outlives the deletion.
- **Dialog.** The box is unticked each time the dialog opens (`rememberSaveable` inside the dialog) and survives rotation. The row is one checkbox for TalkBack, and the text scrolls with large fonts. `SettingsScreenTest` proves `[false, true]` and the reset. The name field resets through `rememberSaveable(settings.myName)`, and a later Save of a blank name is ignored.
- **Legal links.**
  - **How they are built.** `HttpUrl.newBuilder().addPathSegment(...)`, on the same address and with the same validity rule as `ProxyApi.isSetUp` (`ProxyApi.kt:74-77, 164`). So the links show exactly when the proxy is set up, a trailing slash is handled, and none show without an address.
  - **Without a browser.** `ActivityNotFoundException` is caught.
  - **On the Pro screen.** The links sit beside the renewal terms, which show only while Pro is on sale, as the contract says.
  - **Tests.** They open `/privacy` and `/terms`, and show nothing without an address.
- **Strings.**
  - **Call sites.** Placeholder counts match at all 892 call sites; the two hits are a local `section(titleRes, list)` helper. Every `%d` gets an `Int` or a `Long`, and the three `%%` strings are always formatted. No `UiText` reaches `stringResource` unresolved or a string template.
  - **English unchanged.** Every English literal removed in the scoped commits is in the resources. The reverse check finds only intended differences:
    - the removed dialog sentence;
    - singular forms for fixed limits, which never show;
    - the three new deletion and legal texts;
    - category labels, now resources with the domain's exact words.
  - **Argument order and plurals.** Multi-argument strings keep the old argument order. Plural counts reproduce the old singular/plural conditions (`pages > 1`, `others.size == 1`, `drafts.size == 1`). `ProTexts` gives the same English for every period length.
  - **Context.** No view model reads resources. A `Context` reaches view models only as a per-call parameter for Credential Manager, which predates this phase.
  - **Purity.** `UiText` and `ModelCallFailure` live in `:app`. `:core:domain` imports nothing from Android or `:app`.
  - **Lint.** Lint has no hard-coded-text check for Compose, so these audits are the only guard; lint itself reports nothing string-related (0 errors).
- **Screenshots.** Roborazzi's defaults are strict (`ThresholdValidator(0f)`, per-pixel distance 0.007), with no override in the project. Only the four en-XA images were added. Texts that no screenshot draws are pinned by tests that compare whole English sentences (`TestText.inEnglish`). The last run (03:38 UTC, after the last app commit) has no failure or error in any result file, and `PseudoLocaleGalleryTest` passed 5 of 5. `5dec25d`'s `LocalResources` fix is right.
- **Pseudolocales.** They are enabled in debug only, and no locale filter strips them. The four images show pseudolocalized text and nothing cut off. The tall canvases follow the English galleries' convention, and those screens scroll.
- **Version.** The property names match `release.yml:203-204`. The defaults are 1 and "0.1.0", a blank value counts as unset, and a code outside 1..2100000000 stops the build.
- **`d03f0a6`.**
  - **Equivalence.** For every `BigInteger`, `bitLength() <= 63` holds exactly when the value fits a Long (`Long.MIN_VALUE` has bitLength 63; minus one gives 64). OpenJDK's `longValueExact` tests `mag.length <= 2 && bitLength() <= 63`, and the first part follows from the second. The exception type and message are the same.
  - **API level.** `api-versions.xml` confirms `BigInteger.longValueExact` is API 31.
  - **No other post-26 API in `core/domain`.** Every candidate is safe at API 26:
    - `BigDecimal.longValueExact` is API 1 (`BillCurrency.kt:22`, `BillEditor.kt:129, 142`, `SplitEdits.kt:250`);
    - `Math.addExact` and `Math.negateExact` are API 24;
    - `CharSequence.codePoints()` with `IntStream.forEach` is API 24 (`SplitRevisionParser.kt:102`);
    - `URLEncoder.encode(String, String)` is API 1;
    - `java.time` calls are Java 8 methods (API 26);
    - there are no `SequencedCollection` calls: both `entries.reversed()` are on a `Set`, so Kotlin's extension applies.
  - **`app/src/main`.** Lint's NewApi check is clean. `Math.floorDiv` at `RepeatBillSheet.kt:138` is the `(long, long)` form, API 24.

### Out of scope, or predating this phase
- **RevenueCat's own cache.** The RevenueCat SDK keeps its cached purchaser id and customer info on the phone after a deletion; the app closes the SDK but never logs out (`RevenueCatBilling.kt:31-33, 51-54`). This bears on the matrix row "Session và mã người mua đã lưu". The phase-8 reviews already raised it.
- **Device run still due.** The API 26 and 30 measuring run after `d03f0a6` is still to be done, as the release-CI review notes.
- **No guard on the domain's API level.** `:core:domain` compiles against JDK 21 (no toolchain), and lint does not look at it. Nothing binds to a newer member today, but nothing guards it either. Apply `com.android.lint` to `:core:domain` with `checkDependencies = true` in `:app`, so that NewApi covers the domain.

### Recommended Actions
1. M1: run the deletion outside the screen's lifetime, persist the choice and reconcile at start, then fix the comment and the docs.
2. L1: report file failures, move `cache.clear()` into the `try`, and rerun `PhoneData.delete()` while it is unfinished.
3. L2: clear the whole cache and cancel the app's notices on deletion.
4. L3: decide the digit rule for v1.0. ASCII counts restore the old output.
5. L5: add an en-XA image of the bill editor, and let field labels ellipsize or wrap.
6. L7: correct the six docs sentences.
7. As time allows: L4, L6, L8, L9.

### Metrics
- Type coverage: not applicable (Kotlin).
- Test coverage: not measured.
  - 18 new tests, all in `app/src/test`.
  - They test what their names say for the paths they cover.
  - Missing:
    - leaving Settings during the request (M1);
    - a file that cannot be deleted (L1);
    - the no-browser path (L8).
- Linting: 0 errors and 14 warnings in the last lint report, none from this work.

### Unresolved Questions
1. For M1: should a deletion interrupted after the proxy answered be finished quietly at the next start, or should the app ask first? This is a product call.
2. For L3: in an English-only UI on phones with other digit systems, should numbers be ASCII everywhere or localized everywhere?
3. For L6: is the PDF summary inside "every string a user sees is in resources" for v1.0, given that a defaulted parameter avoids touching the device test?

### Sources
- [Cloudflare changelog: Handle incoming request cancellation in Workers with Request.signal](https://developers.cloudflare.com/changelog/post/2025-05-22-handle-request-cancellation/)

Status: DONE_WITH_CONCERNS
Summary: No critical or high defects. The string refactor, the legal links, the version properties, the deletion's ordering and the domain fix check out. One medium gap: leaving Settings or the app dying during the deletion request loses the person's choice, against what the code comment and the docs claim. There are nine low items.
Concerns/Blockers: Fix M1 before release. L5 is based on width arithmetic and needs one rendered image to confirm.
