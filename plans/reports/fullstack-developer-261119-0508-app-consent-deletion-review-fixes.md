# App: review fixes, consent before the first AI call, RevenueCat start, dependency lint, group dialogs

Date: 2026-11-19 (UTC). Branch `feat/phase-09-quality-compliance-release`, pushed. Input: the app
review `code-reviewer-261119-0431-phase-9-app-review.md` and the coordinator's task.

## Outcome

Items A (M1, L1 to L9), B, C and E are done, each with tests of the changed behavior (L7 is docs
only). D is done as asked, but its goal is not reached: lint's NewApi check cannot reach
`:core:domain` with AGP 9.1. The trial call to an API 31 method passed CI's lint task, and the
evidence is below. Every acceptance build passes on the final tree.

## What changed, by item

### A. Review findings

**M1, deletion outside Settings and after the app ends.**
- `AccountRepository.deleteAccount(withPhoneData)` runs in the repository's own scope (a
  `SupervisorJob` on `Dispatchers.IO`), not the view model's, and returns its `Job`. Back, the top
  bar's arrow or Save no longer cancel the request or what follows it.
- `DeletionStore` (new, preferences `account_deletion`, written with `commit()`) keeps three facts:
  - the person's choice, before the request goes out;
  - the proxy's 204, before the phone's part starts;
  - the outcome, until Settings has shown it.
- The next start finishes a deletion that was cut off. `SplitVibeApp.onCreate` calls
  `finishInterruptedDeletion()` when one is pending, and Settings opening does too, for a start
  that was offline.
  - After a 204, it clears the session at once, then deletes what is left on the phone, the phone's
    data included when the box was ticked, without asking the proxy again.
  - Before a 204, it sends `DELETE /v1/account` again.
  - If the proxy then answers 401 `session` (which is also how it answers once the earlier request
    deleted the account), nothing on the phone is deleted, and Settings says the deletion could not
    be confirmed: "sign in again and delete the account".
  - Without a connection it waits for the next start.
- Settings follows `AccountRepository.deletion`. It shows "Working…" when opened during a deletion,
  and the outcome the next time it shows, once. Outcomes: deleted; deleted with the phone's data;
  groups and requests not all deleted; phone's data not all deleted; not confirmed. A refusal (429,
  502, 500, offline) shows its existing message. `refresh()` asks nothing while a deletion runs.
- `AccountStore.clearSession()` and `SettingsStore.forgetName()` now write with `commit()`, so a
  kill right after a deletion cannot bring the session or the name back.
- The comment in `AccountRepository` and `docs/architecture.md` now describe this.

**L1, checked file deletions.**
- `BillPages.deleteAll()` and the cache deletion in `PhoneData.delete()` throw `IOException` when a
  file is still there.
- After the table transaction, a file failure stops nothing: the other files, the notices and the
  name still go, then the failure is reported (outcome "phone's data not all deleted").
- `cache.clear()` is inside the error handling. A failure there is reported as "groups and requests
  not all deleted", not as a failed deletion, and Google's credential state is still cleared.
- An interrupted local deletion is finished at the next start (M1).

**L2, scanner pages and notices.** `PhoneData.delete()` deletes the whole `cacheDir` (the camera,
the document scanner, shared cards, exports) and calls `NotificationManagerCompat.cancelAll()`.

**L3, one number rule.**
- The rule: numbers, money and dates are written as English writes them, whatever the phone's
  locale: digits 0 to 9, "." before decimals, English month names. It is the rule the calls left
  already followed, and the counts did before they became `%d` arguments.
- `TEXT_LOCALE` (`Locale.US`) lives in `:core:domain`.
- `UiText.resolve` fills placeholders with `String.format(TEXT_LOCALE, …)` in place of
  `Resources.getString(id, args)`, which used the phone's locale.
- Every plural and every text with `%d` is now read through `UiText`: 11 Compose call sites, the
  drafts notice and the share parts were converted.
- `BillCurrency.format`, `ExchangeRate.describe`/`rateText`, `SplitEdits.describe` and
  `PaymentRequestBuilder.build` default to `TEXT_LOCALE`, so money, rates, payment details and the
  domain's own English sentences follow. So do the dates of History, Recurring, the group's log,
  the notices, the PDF and the claim screen's "open until" (most through the new `dateText`).
- The CSV files keep the phone's convention, for the spreadsheet app that opens them.
- User-visible: on phones set to another region, money and dates change, for example "250.993 ₫"
  becomes "₫250,993" on a Vietnamese phone, and "12,50 €" becomes "€12.50" on a German one.
  Nothing changes on en-US, and every existing screenshot matched.

**L4, totals by person.** The insights keep no word for the user or someone without a name
(`InsightShare` name empty). The screen and the PDF name them through the resources
(`personName`). `InsightsViewModel` no longer takes `ContentWords`. One visible change: among equal
shares, the user and people without a name now come first, as the tie-break by name
(`Insights.kt:164`) no longer sees the words for them.

**L5, field labels.** In `ui/common/InputFields.kt`, labels and placeholders end in "…" when
longer than their field, and TalkBack reads them whole. The recorded
images show the English app had the same fault: "Percent for Bao" and "Amount for Bao" cut off "Bao
Tran". I chose an ellipsis over wrapping, as a floating label of two lines overlaps the field's
border.

**L6, texts into resources.**
- `PdfSummary(content, resources)` reads its words from the resources: 16 new `pdf_*` strings and
  one plural, plus the insights' section names, `insights_no_group`, `common_bill` and the category
  labels. The device test passes the instrumentation's resources, as the PDF now needs real words.
- The ordinal ending is one ICU message, `{0,selectordinal,one{#st} two{#nd} few{#rd} other{#th}}`,
  read through the new `UiText.Message` with English rules (`android.icu.text.MessageFormat`).
  `recurring_day_*` are gone. The message also reads in en-XA.
- Text that stays in code, with its reason:
  - the sentences `:core:domain` writes (a split's problems, proposed edits, payment method
    problems), which have no resources;
  - the CSV headings and words, a spreadsheet format meant to be the same in every language;
  - log and exception messages, for developers;
  - "—" for a missing amount in the assistant card, a symbol;
  - `TEXT_LOCALE` itself, which a translation must change.

**L7.** The six sentences of `docs/architecture.md` are corrected:
- `:107`: the deletion's lifetime;
- `:96-98`: a 401 ends the sign-in;
- `:104`: what the screen says;
- `:82`: dates are now English everywhere, so that sentence was rewritten under the number rule;
- `:64`: what a new language needs;
- `:75-79`: `ContentWords` is no longer shown on a screen.

**L8.** "No app on this phone opens web pages." is a polite live region (`ui/common/Links.kt`), and
so is the Pro screen's "Google Play could not be opened…".

**L9.** `ProxyApi.deleteAccount()` counts only a 204, through a client that follows no redirect.

### B. Consent before the first AI call

- **When it shows.** `SplitBillViewModel.analyze` and `SplitAssistantViewModel.send` check
  `AppSettings.aiAllowed` before any work. Without it they show `AiConsentDialog` (full screen,
  `ui/split/AiConsent.kt`) and send nothing. Both AI callers go through these two view models.
- **What it says.** What each call sends, through Split Vibe's server, to Anthropic in the United
  States:
  - for a bill read, the text read from the photo, the instruction and the currency;
  - for an edit, what was typed or said, with the shop, the items, charges and amounts, the names
    (the user's too), the division and who paid.
  - These match `GatewayBillAnalyzer` and `SplitRevisionRequest.body`.
  - It links the privacy policy through a new `PrivacyPolicyLink`, which shares its code with
    `LegalLinksRow`.
- **Answers.**
  - Agree: the consent is stored and the call the person asked for goes ahead.
  - "Type it in by hand": the existing manual entry, which keeps the photos.
  - "Keep the split as it is" for an edit.
  - Back closes the screen without an answer.
- **Storage.** `SettingsStore.allowAi`, in the same preferences as the speech consent, written
  with `commit()`. No request carries it and nothing syncs it. Deleting the phone's data keeps it.
- **Withdrawal.** Settings shows a row once the consent is given, with "Take back". After that,
  the row says the app asks again, and the next AI action shows the consent screen. Agreeing again
  happens only on that screen.
- **Device tests.**
  - `FakeProxyRule` starts and ends each journey without the consent.
  - `SampleBillJourneyTest` checks that nothing reached the proxy before it taps Agree.
  - `SplitFlowOnDeviceTest`, which runs with accessibility checks on, taps Agree on its way.
  - New `DeclinedAiJourneyTest` taps "Type it in by hand" on the consent screen and types the bill.
- **Docs.** `docs/architecture.md`, `docs/testing.md`, `docs/compliance.md`, and the reviewer steps
  in `docs/play-console.md`.

### C. RevenueCat after the proxy accepts the session

- `Account.sessionAccepted` (in memory) is true after a sign-in, or once `GET /v1/usage` or
  `POST /v1/plan` succeeds with the session (`AccountStore.onSessionAccepted`). It resets with the
  session.
- `BillingRepository.follow()` configures the SDK only for an accepted session. When there is a
  stored, unaccepted one, it calls the new `checkSession` parameter once (wired to
  `AccountRepository.refresh`).
  - A 401 `session` ends the sign-in through the existing handling, and the SDK never starts.
  - Offline, it starts with the next call that succeeds.
- Purchase, switch, restore and offer go through `asPurchaser`. It now calls `refreshPlan()` first
  for an unaccepted session, and throws, without starting the SDK, when the proxy refuses or cannot
  be reached.
- Before this, restore and offer started the SDK without asking the proxy. A purchase did so after
  an offline plan check too, as `requireOnSale` swallows that error.

### D. Lint for all modules

`checkDependencies = true` is in the app's `lint {}` block, and `:core:domain` applies
`com.android.lint`, which comes with AGP and needs no new artifact. The app's lint now runs the
domain's `lintAnalyzeJvmMain` and lists the design system's issues. The only new entry is the design
system's `ModifierFactoryExtensionFunction` warning in `RowMotion.kt`, which `:core:designsystem:lintDebug`
already reported. There are no errors, so nothing needed fixing. The comments and the doc say what
the setting does and does not do (see the proof).

### E. Group dialogs

- `groups_take_out_text` now ends: "The group's current invite links stop working, and a new
  invite gives their place back."
- `groups_leave_text` adds: "The group's invite links stop working, so coming back needs a new
  invite from someone in the group."
- The account-deletion dialog says nothing about groups, so it stays true.
- No en-XA or ar-XB image shows these dialogs, so none was re-recorded.

## Tests added

| Test | What it proves |
|---|---|
| `ProxyApiTest`: only a 204 counts, no redirect | 200 and 302 are refusals; the redirect is not followed |
| `AccountRepositoryTest` (8 new) | Choice on disk before the request, and the outcome kept until said; resume before the answer with the phone's data; resume after the 204 without a request; 401 at resume keeps the phone's data (not confirmed); offline resume waits; an undeletable page is reported while the rest goes; a broken cache is "groups and requests not all deleted"; scanner pages and notices go |
| `SettingsViewModelTest` (3 new) | Leaving Settings does not stop the deletion and the next Settings says the outcome once; Settings opened mid-deletion shows it working and asks for no count; a cut-off deletion the proxy no longer confirms is said |
| `BillCurrencyTest` (1 new) | Money, a rate and a domain sentence in English under fa-IR and ar-EG |
| `NumbersInTextsTest` (5) | Under `fa` and `ar`: texts with counts, money and dates in English, and the Pro, Settings, home and history screens show no foreign digit; plus a guard that no plural or `%d` text is read outside `UiText` |
| `PdfSummaryTest` (1 new), `RecurringViewModelTest` (1 new) | The PDF's words and the ordinal come from the resources (checked in en-XA) |
| `InsightsViewModelTest` (1 new) | The user and an unnamed person carry no word, and are named in the screen's language |
| `PseudoLocaleGalleryTest` (1 new) | The bill editor in en-XA |
| `LegalLinksRowTest` (2) | Without a browser the message appears as a polite live region; with one, the page opens |
| `SettingsStoreTest` (2) | Consent off by default, kept on disk, taken back at once |
| `AiConsentFlowTest` (5) | No proxy call before agreeing, and the read goes ahead after; manual entry keeps the photo and calls nothing; closing changes nothing; after withdrawal no call from either AI action; an edit asks, declining keeps the split, agreeing sends |
| `AiConsentGalleryTest` (4) | The consent screen in light, dark, 200% text and en-XA |
| `SettingsScreenTest` (2 new) | No consent row before agreeing; "Take back" withdraws and the row says the app asks again |
| `BillingRepositoryTest` (4 new) | A stored session the proxy refuses never starts the store; an accepted one starts it after the proxy's answer; offline it waits for the next call that works; restore and buy check the session first and never start the store on 401 or offline |
| `GroupDialogsTest` (2) | Both group dialogs say the new sentences |
| `DeclinedAiJourneyTest` (device) | Declining and typing by hand calls no model |

One flaky failure showed up once: `UncaughtExceptionsBeforeTest` in `SplitErrorsTest`. A split
view model from an earlier class queried its closed in-memory database. `TestStorage` now cancels
the view models made by `testSplitViewModel` before it closes; this is its own commit.

## Commands run

CI's steps (`.github/workflows/ci.yml`), one Gradle build at a time, each with `--init-script
…/central-mirror.init.gradle --offline`. They ran in full at `29545e0`. The two commits after it
change docs and one KDoc line, so on the final tree (`70f1107`) all five ran again: Gradle
recompiled the Kotlin and found the classes unchanged, so the tests, R8 and the device-test compile
were up to date.

| Command | Result |
|---|---|
| `./gradlew :core:domain:test :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest` | BUILD SUCCESSFUL. App 493 tests in 71 classes, domain 200, design system 3, no failure, every screenshot matched |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL |
| `./gradlew :app:lintProdDebug :core:designsystem:lintDebug` | BUILD SUCCESSFUL. App report 0 errors and 15 warnings, none from this work: newer versions (9), the target API, `UnusedAttribute`, `ObsoleteSdkInt`, `DataExtractionRules`, the debug catalog's `LocalContextResourcesRead`, and the design system's warning above |
| `./gradlew :app:assembleProdDebug :app:assembleProdRelease :app:bundleProdRelease` | BUILD SUCCESSFUL, R8 ran (a separate invocation from lint) |
| `./gradlew :app:assembleDevDebug :app:assembleProdDebugAndroidTest :baseline-profile:assembleProdNonMinifiedRelease` | BUILD SUCCESSFUL; `compileProdDebugAndroidTestKotlin` ran |

The device tests were not run (no emulator here).

## Lint NewApi proof

- **The trial.** I appended `fun lintNewApiProbe(value: java.math.BigInteger): Long =
  value.longValueExact()` to `core/domain/…/BillCurrency.kt` and ran `./gradlew
  :app:lintProdDebug :core:designsystem:lintDebug`.
  - Result: exit 0, BUILD SUCCESSFUL, no NewApi.
  - `:core:domain:lintAnalyzeJvmMain` ran, and its `lint-partial.xml` holds no API entry.
  - The probe was removed (`git checkout`) and never committed.
- **Why.** I read lint 32.1.1's bytecode, which AGP 9.1.1 uses:
  - `ApiDetector.createUastHandler` does `val project = if (context.isGlobalAnalysis())
    context.mainProject else context.project`, and returns an `ApiVisitor` only when
    `project.isAndroidProject`.
  - `LintModelModuleProject.isAndroidProject()` is `type != JAVA_LIBRARY`.
  - AGP 9 always analyses each module on its own (its `USE_LINT_PARTIAL_ANALYSIS` option sits with
    the removed ones). So in the domain's own analysis the project is a `JAVA_LIBRARY`, and the API
    check never runs there, `checkDependencies` or not.
- **A JDK release flag cannot help.** `longValueExact()` is in Java 8, so `-Xjdk-release=1.8` on
  the domain's compile accepted the probe (tried, then reverted). Android API levels are not JDK
  versions.
- **What would work, each a choice for the coordinator.**
  - A lint-only Android library module that compiles the domain's sources, wired into
    `:app:lintProdDebug`.
  - Or a JVM test that checks the domain's bytecode against the SDK's `api-versions.xml`.
  - Or making `:core:domain` an Android library, which gives up its no-Android guarantee.

## Screenshots recorded

- New:
  - `54-edit-bill-en-xa.png`;
  - `55-ai-consent.png`, `55-ai-consent-dark.png`, `55-ai-consent-large-text.png`,
    `55-ai-consent-en-xa.png`.
- Recorded again, intended: `18-split-by-percent.png`, `18-split-by-percent-dark.png`,
  `19-who-paid.png`, `19-who-paid-dark.png`. "Bao Tran" now ends in "…" instead of being cut to
  "Bao".
- No other image changed.

## Privacy policy sentences for the coordinator

Proposed paragraph for `docs/privacy-policy.md`, after the sections on reading a bill with AI and
changing a split by a sentence:

> Before the app first sends anything to the AI, it asks for your consent on a screen of its own,
> which says what reading a bill and changing a split send, and to whom, and links this policy. Until
> you agree, nothing goes to the AI: you can type a bill in by hand instead, or keep a split as it is,
> and everything else in the app works the same. The app keeps your answer on your phone, with its
> other settings, and does not send it to our server. You can take your consent back at any time in
> the app's Settings; the app then asks again before it next uses the AI.

It fits "What stays on your phone" (`docs/privacy-policy.md:28-43`), whose "your other settings"
already covers the answer, with what that section says about backups and transfers.

Optional, for the legal bases, if the adviser wants consent named there:

> Where the law asks for consent before data is collected, as Vietnam's does, sending the text of a
> bill, or a change of a split with the bill it is about, to Anthropic rests on the consent you give
> on that screen.

Possible bullet for "In short": "The app asks before it first uses the AI, and you can take that back
in Settings."

## Left, with reasons

1. **NewApi does not cover `:core:domain`** (D). It needs one of the choices above.
2. **Plural forms follow the phone's language.** Android picks the plural form by the
   configuration's locale. On a Persian phone a count of 0 takes the "one" form ("0 bill"). L3
   covers digits and decimal marks only.
3. **Two narrow cases of the deletion.**
   - A kill between the 204 arriving and its being written leaves only "not confirmed".
   - An account the earlier request did delete is also "not confirmed" at resume, as the proxy cannot
     tell it apart from a run-out session. In both cases the person is told to sign in and delete
     again.
4. **A recurring-bill notice can follow a deletion.** A draft the worker made just before the
   deletion's transaction is still announced after `cancelAll()`; tapping it shows "bill gone".
5. **The README** (not mine to edit) describes the split steps without the consent screen.
6. **The deletion dialog and the calls count.** While checking E: `settings_delete_text` says the
   server deletes "the count of your calls", but `docs/proxy.md` says the month's count is kept
   until the month ends. This predates this work and was left unchanged.

## Unresolved questions

1. **D.** Which guard, if any, should cover the domain's API level, given that lint cannot?
2. **L3.** Is English money and date formatting on every phone what the owner wants, as Vietnamese
   phones change from "250.993 ₫" to "₫250,993"? The other rule (the phone's locale everywhere)
   would also need the calls left, ordinals, quantities and input fields localized.
3. **B.** The consent wording awaits the legal adviser, as the owner said.

Status: DONE_WITH_CONCERNS
Summary: All of A to E are implemented, tested, committed and pushed, and every acceptance build passes. Lint's NewApi check cannot reach the Kotlin JVM domain module with AGP 9.1, so D's proof failed, with evidence.
Concerns/Blockers: D needs a decision on another guard; L3 changes how money and dates look on phones set to other regions; the device tests were not run here.
