# App side of the release: deletion choice, legal links, texts in resources, version from Gradle

Date: 2026-11-19 (UTC). Branch: `feat/phase-09-quality-compliance-release`.
Scope: the "App" part of the shared contract in
`plans/261114-0220-split-vibe-production/phase-09-quality-compliance-release.md`, including the app rows
of the account deletion matrix, with the English restatement of the task as the authority.

## Outcome

All five parts are built, committed and pushed. Every check the task lists passes on the final tree.
No existing reference image changed: git shows only the four new `en-XA` images added. Every
screenshot the unit tests take verifies against its reference.

One file ownership problem needs the orchestrator. My commit `5dec25d` also carries five pure renames
under `proxy/eval/` that another agent had staged in the shared index. See "File ownership" below.

| Commit | What it does |
|---|---|
| `905249c` | build(app): version from Gradle properties; pseudolocales in debug builds |
| `a4d69c5` | refactor(app): texts of Settings, the account, Pro and the AI calls in resources |
| `a2ab66c` | feat(app): the choice to delete the phone's data with the account |
| `40aace7` | feat(app): privacy policy and terms of use links in Settings and on the Pro screen |
| `078cb98` | style(app): wraps one long line from `40aace7` that ktlint flagged after the push |
| `b60d981` | refactor(app): texts of the split flow and the bill editor in resources |
| `5712bdc` | refactor(app): texts of groups, requests, bill links and payment methods in resources |
| `01943f3` | refactor(app): texts of home, bills, insights, recurring bills and notices in resources |
| `5dec25d` | fix(app): the share sheet reads texts through `LocalResources` (lint `LocalContextGetResourceValueCall`); two strings marked as meant for lint. It also carries five foreign renames, see "File ownership". |
| `242ff9f` | test(app): home, a split's result, Settings and Pro drawn in `en-XA` |
| `1414ada` | docs: how texts are kept and resolved, and what deleting the account deletes |

`42e98b0` and `2f3258a` on the same branch are device journeys under `app/src/androidTest`. Another
agent wrote them, not me.

## What was built

### 1. Deletion choice

- The account deletion dialog in Settings has a checkbox, "Also delete the bills, people and payment
  methods on this phone". It is unticked each time the dialog opens.
  `AccountActions.onDeleteAccount` now takes `withPhoneData: Boolean`.
- `AccountRepository.deleteAccount(context, withPhoneData)` calls the proxy first. When the proxy
  fails (any `ProxyException`, including 429 `rate_limited` and 502 `subscription_unavailable`), the
  exception is rethrown and nothing on the phone changes. The session stays signed in. Only a refused
  session (401) marks the session expired, as before, so signing in again can retry.
- After the proxy has deleted the account, the phone does the following, as it always did: it forgets
  the session and the purchaser, clears the cached groups and requests, and forgets the Google account.
  Then, when the box was ticked, `PhoneData.delete()` (new, `data/account/PhoneData.kt`) deletes the
  following:
  - in one Room transaction: bills and drafts, recurring bills, people, payment methods, and the proxy
    cache;
  - every stored bill page image;
  - the cache folders that hold copies of the same data (camera photos, shared cards, exports);
  - the user's name (`SettingsStore.forgetName()`).

  The currency, the consent to the speech service and the other settings stay. This step runs in
  `NonCancellable`, so leaving Settings midway does not leave it half done.
- When the phone's data cannot all be deleted after the account was deleted, `PhoneDataException` is
  thrown. Settings then says so as an error: "Your account was deleted, but the bills, people and
  payment methods on this phone could not all be deleted. Clearing Split Vibe's storage in the phone's
  settings deletes them."
- When the data is deleted, Settings says "Your account was deleted, and so were the bills, people and
  payment methods on this phone." The name field follows the stored name, so it empties.
- With the box unticked, everything stays as it is today.

### 2. Legal links

- `ui/common/Links.kt` is new. `LegalLinks.of(proxyUrl)` builds `/privacy` and `/terms` on the build's
  `AI_PROXY_URL` with OkHttp's `HttpUrl`. It returns `null` when the build has no valid proxy address.
  `LegalLinksRow` shows "Privacy policy" and "Terms of use" as buttons that open the browser. When no
  app can open them it shows "No app on this phone opens web pages."
- `AppNavHost` builds the links once from `BuildConfig.AI_PROXY_URL`, so release and debug each use
  their own address. It passes them to Settings and to the Pro screen. On the Pro screen they sit in
  the terms block next to the renewal terms.
- A build without a proxy address hides both links.

### 3. Strings

- `app/src/main/res/values/strings.xml` went from 1 string to 701 strings and 44 plurals. Plurals are
  used wherever a count is shown, and numbered formatted arguments wherever a sentence is built.
- View models, repositories and `llm/` return a small text type, `text/UiText.kt`. It has these cases:
  - `Res`, `Plural`: a resource with its arguments;
  - `Raw`: text that is not the app's own, such as typed names, model output or `:core:domain`
    sentences;
  - `Lines`, `Joined`: texts put together.

  Screens resolve it with `@Composable resolve()`, which reads `LocalResources`. Code that has a
  `Context` (notices, the share sheet, the exporter) uses `resolve(resources)`. No view model gets a
  `Context` for strings.
- A failed model call carries its text as `ModelCallFailure`, which is the cause of the domain's
  `BillAnalyzerException`. The domain class is final and holds only a message. `e.text` reads it.
- `text/ContentWords.kt` covers words the app writes into stored or sent content rather than shows:
  "You" in a bill's stored people list and in the totals by person, the name of a recurring bill
  without one, unnamed lines and people on a bill's page, and the greeting of a group request. They are
  read from resources once, through Koin. `BillRepository`, `RecurringBillRepository`,
  `ShareRepository` and `BillProjection.of` take them.
- `text/DomainLabels.kt` maps the domain enums `BillCategory` and `ChargeKind` to resource ids.
- Debug builds carry the pseudolocales `en-XA` and `ar-XB` (`isPseudoLocalesEnabled = true` in
  `debug` only). `PseudoLocaleGalleryTest` adds these images:
  - `50-home-en-xa.png`
  - `51-result-en-xa.png`: the sample bill as the recorded proxy answer splits it
  - `52-settings-en-xa.png`: signed in, with the legal links
  - `53-pro-en-xa.png`: with the legal links

  The images showed nothing cut off and nothing overlapping, because every text wraps, so there was
  nothing to fix. Three things there are by design and not bugs:
  - names, merchants and model output are not pseudolocalized;
  - dates keep their English month names;
  - an avatar takes the first character of the pseudolocalized "You", which is a bracket.
- An audit script checked that the fixed English of every literal removed from the main code is in the
  resources.

### 4. Version

- `app/build.gradle.kts` takes `versionCode` from `splitvibe.versionCode` and `versionName` from
  `splitvibe.versionName`. The defaults are 1 and "0.1.0", and a blank value counts as unset.
- A code that is not a whole number from 1 to 2100000000 stops the build with a clear message.
- Nothing else in the release config changed.

### 5. Docs

`docs/architecture.md` has two new sections, "Texts and languages" and "Deleting the account". It
also has notes in the module table, the build variants (pseudolocales, version properties) and the
screenshot tests (`en-XA` images).

## Files

- Build: `app/build.gradle.kts`. `app/proguard-rules.pro` needed no change.
- Resources: `app/src/main/res/values/strings.xml`.
- New main code:
  - `text/UiText.kt`, `text/ContentWords.kt`, `text/DomainLabels.kt`
  - `data/account/PhoneData.kt`
  - `ui/common/Links.kt`

  All are under `app/src/main/java/com/dantech/splitvibe/`.
- Changed main code: 62 files, 67 with the new ones, +2177/−1207 lines, by area:
  - Deletion: `AccountRepository`, `AccountStore`; the `deleteAll` and `forgetName` additions in
    `SplitVibeDatabase`, `RecurringBill`, `BillPages`, `SettingsStore`; folder constants in
    `BillImageLoader`, `ShareCardRenderer`, `Exporter`.
  - Settings and Pro: `SettingsScreen`, `SettingsViewModel`, `ProScreen`, `ProTexts`, `ProViewModel`.
  - AI calls: `llm/ProxyModelCall`, `llm/GatewayBillAnalyzer`, `assistant/SplitAssistant`.
  - Wiring: `di/AppModule`, `di/SpendingModule`, `ui/AppNavHost`.
  - Strings in every screen and view model: split, editor, groups, requests, claim, payments, home,
    history, insights, recurring, notices, export, share.
- Tests:
  - new: `TestText.kt` (`inEnglish()`, `english()`, `contentWords()`), `ui/common/LegalLinksTest.kt`,
    `ui/PseudoLocaleGalleryTest.kt`;
  - 37 changed test files, for the new constructor arguments and because texts are now `UiText`;
  - four new images in `app/src/test/screenshots/`. No existing image changed.
- Docs: `docs/architecture.md`.

## Tests

There are 18 new tests, all in `app/src/test`. The unit test count went from 433 to 451.

- `AccountRepositoryTest`:
  - With the box ticked, everything the phone alone keeps is deleted, but only after the proxy deleted
    the account.
  - With the box unticked, all of it stays, but the account's cached groups and requests go.
  - A refused deletion deletes nothing even when the person asked for it. The test runs 500
    `internal`, 429 `rate_limited` (with `Retry-After`) and 502 `subscription_unavailable` in turn.
    For each it checks the counts of bills, recurring bills, people and methods, the groups, the
    cached files, the name, a stored page, and that the session is still there.
- `SettingsScreenTest`:
  - the box decides whether the phone's data is deleted, and it starts unticked each time;
  - the name field follows the stored name after deletion;
  - the links open `…/privacy` and `…/terms` on the proxy address;
  - no links without the address.
- `SettingsViewModelTest`: the message when the data went too, and the error message when the account
  was deleted but the data could not all be deleted.
- `ProScreenTest`: the two links next to the renewal terms on the proxy address, and no links without
  the address.
- `LegalLinksTest`: the page addresses, and `null` for a build without an address.
- `PseudoLocaleGalleryTest`: the resources resolve in `en-XA`, plus the four images.

## Commands and results

Every Gradle command ran with `--init-script …/central-mirror.init.gradle --offline`. Each one waited
for any other Gradle client (`pgrep -f GradleWrapperMain`), and they ran one at a time. I never used
`--stop` while another build ran, or `--dependency-verification=lenient`.

| Command | Result |
|---|---|
| `./gradlew ktlintCheck` (root) | Passes (exit 0) |
| `./gradlew :app:testProdDebugUnitTest --rerun` | Passes. 451 tests in 65 classes: 0 failures, 0 errors, 0 skipped. Every screenshot verifies. |
| `./gradlew :app:testDevDebugUnitTest --rerun` | Passes. 451 tests in 65 classes: 0 failures, 0 errors, 0 skipped. Every screenshot verifies. |
| `./gradlew :core:domain:test` | Passes. 199 tests in 24 classes, 0 failures. |
| `./gradlew :core:designsystem:testDebugUnitTest` | Passes. 3 tests in 2 classes. |
| `./gradlew :app:lintProdDebug`, run alone | 0 errors, 14 warnings (see below) |
| `./gradlew :app:assembleProdRelease --rerun`, run alone | Passes |
| `./gradlew :app:compileProdDebugAndroidTestKotlin --rerun` | Passes |
| `./gradlew :app:processProdReleaseManifest -Psplitvibe.versionCode=42 -Psplitvibe.versionName=1.2.3-test` | Merged manifest has `versionCode="42"` and `versionName="1.2.3-test"` |
| `./gradlew :app:processProdReleaseManifest` (no properties) | Merged manifest has `versionCode="1"` and `versionName="0.1.0"` |
| `./gradlew :app:help -Psplitvibe.versionCode=abc` | Fails as intended: "splitvibe.versionCode must be a whole number from 1 to 2100000000, but it is abc". A blank value builds with the default. |

None of the 14 lint warnings comes from this work:

- `OldTargetApi`: `targetSdk = 36` is the owner's choice;
- `UnusedAttribute`, `DataExtractionRules`, `ObsoleteSdkInt`: in files I did not change;
- `AndroidGradlePluginVersion` and 8 × `NewerVersionAvailable`: library and tool versions;
- `LocalContextResourcesRead`: in `app/src/debug/.../catalog/CatalogScreen.kt:193`, which is outside my
  files.

## Strings that stayed in code, and why

- **Sentences that `:core:domain` writes.** That module is off limits for this task, so the app shows
  these sentences as `UiText.Raw`:
  - reasons a split does not add up (`SplitCalculator`);
  - proposed edits and their reasons (`SplitEdits`);
  - a payment method's problem, title and summary (`PaymentMethod`);
  - a bill's warnings and its difference note;
  - parser messages (`BillAnalysisParser`);
  - the default label the editor stores for a new line (`BillEditor.defaultLabel`);
  - payment request details (`PaymentRequestBuilder`).

  Moving them needs resource ids or a text type in the domain.
- **Dates.** They are written with English patterns and day and month names (`Locale.ENGLISH`), as
  before. A new language would change these formats, not the resources.
- **CSV headings and words** ("Bill ID", "Your share", "You", the kinds). A CSV file is a format for
  spreadsheet apps and should stay the same in every language.
- **PDF summary headings and lines**, and `Exporter.groupName` / `monthName` used by the PDF. The
  device test in `app/src/androidTest`, which is off limits, builds `PdfSummary(content, Locale.US)`
  without words. Moving the headings means that test has to pass them.
- **`PaymentPayload`'s fallback "Pay by hand."** It is content sent with a request. The domain never
  builds blank details, so it never shows.
- **Separators and fixed parts**: `", "`, `" · "`, line breaks, `"—"`, the `paypal.me/` prefix, file
  names and keys.
- **Log lines and exception messages.** They are for developers. An unexpected exception (for example
  from ML Kit) still shows its own message inside the resource sentence "Something went wrong: %1$s",
  as before.
- **`app/src/debug` component catalog.** It is outside my files and only in debug builds.
- **Test-only strings.**

## Different from the contract, or left

- **One sentence removed.** The deletion dialog no longer says "Bills on this phone stay as they are."
  It would be false with the box ticked. Every other English text is unchanged word for word. No
  existing screenshot shows the dialog.
- **Cache copies also deleted.** Ticking the box also deletes the copies of the same data that wait in
  the app's cache (camera photos, shared cards, exports). Otherwise bill photos and exports would
  outlive a deletion the person asked for.
- **Three new texts that the contract does not word:** the two results of deleting with the phone's
  data, and "No app on this phone opens web pages."
- **Existing screenshots have no legal links.** The existing Settings and Pro screenshot tests draw the
  screens without the links, because the parameter defaults to `null`, so those images stay as they
  were. The app always passes the links when the build has an address. The screen tests check the
  links and their absence, and the `en-XA` images show them.
- **Singular plural forms that never show.** For counts that are fixed limits (3, 10, 300), the `one`
  form of the plural is grammatical singular. It never shows, so the English output does not change.
- **`ar-XB` not screenshot-tested.** It is enabled in debug, but only `en-XA` images were asked for.
- **Plan file left as is.** Plan files are off limits to me, so the phase's todo item for strings and
  pseudolocales is not ticked.
- **One commit fails ktlint alone.** `40aace7` was pushed with one line over 140 characters. I fixed it
  in `078cb98` instead of rewriting pushed history, so `40aace7` builds and passes its tests but would
  fail `ktlintCheck` on its own.

## File ownership

My pushed commit `5dec25d` contains five renames I do not own. All have 100% similarity, so no
content changed:

- `proxy/eval/revise/guard.mjs` → `proxy/eval/shared/guard.mjs`
- `proxy/eval/revise/proxy-process.mjs` → `proxy/eval/shared/proxy-process.mjs`
- `proxy/eval/revise/test/proxy-process.test.mjs` → `proxy/eval/shared/test/proxy-process.test.mjs`
- `proxy/eval/revise/test/stand-in-proxy.mjs` → `proxy/eval/shared/test/stand-in-proxy.mjs`
- `proxy/eval/revise/test/stand-in-run.mjs` → `proxy/eval/shared/test/stand-in-run.mjs`

**How it happened.** Another agent had staged them (`git mv`) in the shared index. I staged only my own
paths, but a plain `git commit` takes the whole index.

**Why I left it.** I did not revert it, because that agent's uncommitted edits are on the new paths,
and on `proxy/eval/revise/run.mjs`, which likely updates the imports. A revert would break that
agent's work.

**What is needed.** The `proxy/eval` owner should know the renames are already on origin, so it commits
only its content changes. Until it commits `run.mjs`, the pushed tree may not match what that script
imports.

I checked every commit of mine against my file list. Only `5dec25d` is affected, and no foreign
commit touches my files. This report is committed with `git commit -- <path>`, which commits only that
path whatever else is staged.

## Unresolved questions

- Should the PDF summary's headings move into resources? That needs the owner of
  `app/src/androidTest` to pass them to `PdfSummary` in the device test.
- Should the domain's sentences move into resources? That needs a change to `:core:domain`, outside
  this task.
- Does the `proxy/eval` owner know about the renames in `5dec25d`? See "File ownership".

Status: DONE_WITH_CONCERNS
Summary: The deletion choice, legal links, strings in resources with plurals and pseudolocales,
version properties and docs are built and pushed. ktlint, both unit suites (451 tests each, every
screenshot verifying), the core tests, lint, the release build and the device-test compile all pass.
Concerns/Blockers: My commit `5dec25d` swept in five pure renames under `proxy/eval/` that another
agent had staged, and the orchestrator should tell that agent. The domain's sentences and the PDF
headings stay in English in code, because the modules that own them were off limits. The deletion
dialog lost one sentence that the new choice makes false.
