## Final review: branch `feat/ui-redesign` against `main`, excluding `web/` and `proxy/`

**Verdict:** I found no crash, data-loss or privacy defect on the release path. One finding should be fixed before merge: the AI's error message still says "Claude" on screen (finding 1). The rest are Low or Low–Medium. Several of the plan's own phase 10 gates are still open (see "Merge readiness").

### Scope and method
- **Range:** merge-base `2550279` to `54c69fa` (68 commits). I reviewed committed HEAD only. The worktree has one uncommitted edit, `plans/261120-0528-ui-redesign/phase-09-web-pages.md`, which belongs to the web review.
- **Size:** 450 files outside `web/` and `proxy/`. Production code is 112 files (+10,921 / −2,793) and tests are 69 files (+8,858 / −323). The rest is 157 reference images, fonts, docs and plan records.
- **Read:** `plan.md`, all ten phase files (the results sections in full), the findings of all nine phase reviews, and the diffs of `app/`, `core/`, `baseline-profile/`, the build files, `.github/workflows/`, `docs/` and `README.md`.
- **How I checked:** by reading the code, plus scripted checks:
  - string usage against `src/main`;
  - placeholder counts against the arguments at 790 call sites, with a checker I first ran on deliberately wrong calls;
  - use of every palette token;
  - links and anchors in the docs;
  - privacy scans of the diff and of the design boards;
  - sizes of the store images, read from their file headers.
- **Not done:** I did not run Gradle or emulators, edited nothing, and opened no secret files. Coverage and lint figures were therefore not measured.

### Findings, most severe first

**1. Medium: the bill-read error path still shows "Claude" to users, and a test locks the text in**
- **Where:**
  - `/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/llm/BillAnalysisParser.kt:50` throws "Claude's answer was not in the expected format. Please try again."
  - Line 55 of the same file throws "Claude returned an amount that is not a plausible price. Please try again."
  - The parser runs in the `read` step at `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/llm/GatewayBillAnalyzer.kt:51`. `ProxyModelCall.ask` calls that step outside its `try` (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/llm/ProxyModelCall.kt:121`).
  - With no `ModelCallFailure` cause, `BillAnalyzerException.text` falls back to `UiText.Raw(message)` (`ProxyModelCall.kt:64`).
  - `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:807-809` then shows that raw text.
- **Test:** `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/llm/GatewayBillAnalyzerTest.kt:146` asserts that the shown text contains "not in the expected format".
- **Scenario:** the model reads a 13-digit invoice or tax number on a receipt as the total. That is at or above `MAX_AMOUNT` = 1e12 (`BillAnalysisParser.kt:170`), so the New split screen says "Claude returned an amount that is not a plausible price."
- **Why it matters:** this breaks owner decision 6, and the plan's success criterion that no text outside the consent screen and the consent row in Settings names Claude. Phase 5 only checked `strings.xml`, and no phase owned the domain's messages.
- **Fix:** do what the assistant already does (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/assistant/SplitAssistant.kt:58`):
  - In `GatewayBillAnalyzer`'s `read`, catch `BillAnalyzerException` and throw `modelCallFailure(Kind.BAD_RESPONSE, ProxyModelCall.UNREADABLE_ANSWER, e)`. Users then see "The AI's answer could not be read. Please try again."
  - Change test line 146 to expect "could not be read".
  - Optionally add a test that no string resource outside the consent set contains "Claude".
  - Leave the `ai_consent_*` strings and `settings_ai_consent_given` untouched.

**2. Low–Medium: in Vietnam, a rate typed the way the app now writes it is read 1000 times too small**
- **Where:**
  - On a phone set to Vietnam the app now writes "1 USD = 25.350 VND" (`/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/domain/ExchangeRate.kt:50`, `:72`; shown at `ResultContent.kt:594-596`).
  - `positiveRate` (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/split/SplitSheets.kt:405-412`) reads both "." and "," only as a decimal mark. `AddToGroupViewModel.kt:114` uses the same parser.
  - `ExchangeRateSheet` (`SplitSheets.kt:355-391`) shows no converted total while typing.
- **History:** phase 4 recorded this as an unhandled risk. Phases 5 and 6 then rebuilt both rate fields without picking it up.
- **Scenario:** a phone set to Vietnam, a USD bill, local currency VND. The user types the card's rate from a bank SMS as Vietnamese write it, "25.412".
  - It is saved as 25.412 instead of 25,412.
  - The bill's "about ₫…" lines drop by 1000 times.
  - The saved rate also feeds Insights, Home's "this month" figure and the CSV and PDF exports (`InsightsViewModel.kt:332`), so their totals quietly undercount that bill.
  - Adding the bill to a group is safer: the live "Comes to …" line (`AddToGroupSheet.kt:154-158`) shows the wrong amount before saving.
- **Fix:** the smallest fix is to show the converted total live in `ExchangeRateSheet`, as `AddToGroupSheet` already does. A stronger option is to ask "Did you mean 25,412?" when the region groups thousands with the typed mark and the typed rate is 100 times or more off the day's rate. Either way this is the owner's call, since the plan deliberately kept the parsing as it was.

**3. Low: the docs overstate what the device tests do**
- `/home/user/split-bill-poc/docs/architecture.md:615` says the device tests split the sample bill "from the photo to the sent requests". `SplitFlowOnDeviceTest` actually stops at "Ask for the money" and taps Done (`/home/user/split-bill-poc/app/src/androidTest/java/com/dantech/splitvibe/SplitFlowOnDeviceTest.kt:95-101`). The README and `docs/testing.md` now say this correctly.
- The same row, and `/home/user/split-bill-poc/README.md:429-430`, say "the taps of three of these tests also run Android's accessibility checks". In CI only two ever do:
  - `KeyboardOnDeviceTest` turns the checks on only from API 34 (`KeyboardOnDeviceTest.kt:54`), and skips when the phone has no on-screen keyboard (`:69`).
  - Every API 34+ image CI uses is an ATD image without a keyboard.
  - The API 26 image, which has a keyboard, is below the API 34 gate.
  - So that test's checks run nowhere. `docs/testing.md:63-68` already describes this accurately.
- **Fix:** reword to "…to the step that asks for the money" and "two of them in CI, a third on a phone with an on-screen keyboard". Alternatively, give the Android 14 job a keyboard.

**4. Low: `SvPersonChip` no longer has a caller in the app**
- Its only caller (`ResultContent.kt`) went when phase 5 merged "Who is on the bill?" into "Each person pays". The string `split_who_is_on_the_bill` was removed at the same time; no phase lists that removal, but it is consistent with the merge.
- It survives only in the debug catalog (`/home/user/split-bill-poc/app/src/debug/java/com/dantech/splitvibe/catalog/CatalogScreen.kt:480-497`), and `/home/user/split-bill-poc/docs/design-system.md:320` still describes it as a live component.
- **Fix:** delete the component, its catalog entry and the doc row, or mark it as catalog-only.

**5. Low: requester names decode the same group's cached state once per request**
- `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/data/groups/GroupRepository.kt:56-64`: `creditorNamesOf` calls `creditorNameOf`, which does a database read and a full JSON decode of the group for every request.
- Since phase 3, `RequestOverviewViewModel.kt:50` runs this for the whole activity on every change to the request list, on top of `RequestsViewModel.kt:46`.
- **Scenario:** a trip group with 40 open requests means 80 reads and decodes of the same group state on every change (report paid, confirm, refresh). This is off the main thread, but it happens on every app open while signed in.
- **Fix:** decode each group once, for example `requests.mapNotNull { it.groupId }.distinct().associateWith { cached(it) }`, then look members up in that map.

**6. Low (unverified, and no phase owned it): restoring an old back stack that holds the removed `RecurringKey`**
- Phase 3 required one manual check: install `main`, open Recurring, install the branch over it, reopen, and report any crash. No later phase ran it or recorded it.
- If such saved state were ever restored, `NavKeySerializer`'s `Class.forName` would throw inside `rememberNavBackStack` (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt:150`), and the app would crash on launch.
- This is probably unreachable, because Android finishes a package's activities when it is updated, but nobody has proven it.
- **Fix:** run the check once before merge. Alternatively, keep `@Serializable data object RecurringKey : NavKey` for one release, with an entry that replaces itself with `HistoryKey`.

**7. Low: one hardcoded English string remains in the design system**
- `/home/user/split-bill-poc/core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/components/SvFields.kt:150` and `:474`: every `SvTextField` in an error state gets the description "Invalid input", which TalkBack reads aloud.
- It is not a string resource, so pseudolocales and any future translation miss it. Phase 2's review listed it, and no later phase took it on.
- **Fix:** take the description from the caller as a string resource, falling back to `supportingText`.

### Merge readiness: plan gates that are still open
- **Phase 10 has recorded nothing yet.** Missing:
  - Conclusions for `docs/privacy-policy.md`, `docs/compliance.md` and Play's "Data safety" section. My partial check found no promise to show users what was sent to the AI, and no statement about when requests are fetched, so no wording change looks needed.
  - The owner's approval of the seven store images.
  - The baseline profile. The committed one is `c37f485` from `main` (14 November) and covers none of the new code.
  - The local `ci.yml` run on the profile commit.
  - The journal entry.
- **Plan status:** every success criterion in `plan.md` is still unchecked, and phase 9 is still "In progress".
- **Before recording the profile:**
  - `baseline-profile/.../Journeys.kt:19-25` only starts a split and goes back. Switching to the Groups, Requests and Insights tabs, now the main way around the app, will not be in the profile; consider adding a round of the tabs.
  - Any fix to app code from findings 1–7 has to land before the recording (phase 10, step 6).

### Checked and found correct
- **Startup path:**
  - `UiText.start` runs first in `Application.onCreate`.
  - The content providers (FileProvider, androidx.startup with WorkManager, Firebase, ML Kit) read no app text.
  - `RecurringBillWorker` retries when Koin is not up yet.
  - The English plural resources are built through `createConfigurationContext`, so they have their own AssetManager, and access is synchronized.
  - The app ships no translations.
- **R8 and resource shrinking:**
  - The only new reflection is in a test (`ScreenGalleryTest`).
  - The serializable `GroupDialog` keeps `readResolve` under the default `proguard-android-optimize` rules.
  - Navigation keys are unchanged apart from the removal of `RecurringKey`.
  - Koin uses its DSL, not reflection.
  - The fonts are referenced from code, and `fonts_keep.xml` keeps the two licence files.
  - The night theme is reachable from the manifest.
- **Window colours:** both themes set `windowBackground` and `colorBackground` to the page colour, and `WindowBackgroundTest` cannot pass vacuously.
- **Navigation:**
  - The single back stack keeps its rules, and back never removes Home.
  - Links and notices are read only on a fresh activity (there is no `onNewIntent`), so a link always lands on `[Home, X]` and can never add a second `SplitKey`.
  - Every action callback is still wired to the UI; only the four tab screens lost their `onBack`, as planned.
  - All `rememberSaveable` values can be stored in a Bundle.
  - List keys are now prefixed per section, which also fixes possible collisions between `main`'s unprefixed member and bill keys.
- **Strings:**
  - All 794 resources are used from `src/main`.
  - Removals and wording changes match the owner's table and the changes the phases recorded.
  - The eight consent texts are pinned by `ConsentTextsTest`, and the consent screen still shows every paragraph and the policy link.
  - Argument counts match placeholders at all 790 call sites, and plural pairs are consistent.
  - There is one harmless duplicate (`editor_change`).
- **Region formats:** every amount, number and date goes through `BillCurrency`, `numberText` or `DateTexts`. Counts are written in English. The "Amount:" line appears only in the payment details text, never in a QR code or link. Exports keep English month names, and the CSV output is unchanged.
- **Design-system boundary:**
  - Screens set no colours, font families, font weights, sizes or transparency of their own.
  - The only remaining direct Material calls are `IconButton`, `TextButton` and `DatePickerDialog`; the date picker is recorded in phase 7.
  - Every palette token is used, and the component table in `docs/design-system.md` matches the code in both directions.
- **Accessibility:**
  - Tabs keep their tab role, selected state and click action, and large-text mode adds a mark that does not rely on colour.
  - Headings are set on top bars, section headers and dialogs.
  - The new small controls have 48 dp touch targets.
  - Direction-sensitive icons mirror for right-to-left layouts.
  - The new AI mark is decorative, and results are announced through live regions.
- **CI:**
  - Only two workflows changed. `checksums.yml` gained the `fonts` job, which has read-only permissions, fetches over HTTPS only and pins commits. `device-tests.yml` gained one runner argument and one artifact path.
  - Nothing was skipped or weakened.
  - The triggers of `ci.yml` and `security.yml` match what the docs say.
- **Docs:**
  - All links and anchors resolve, and every documented Gradle command and property exists.
  - The store image sizes and their dark versions check out.
  - The two store-listing rules are kept word for word, and the new rule is added.
  - `01-home-owed.png`, `05-result-top.png` and `10-requests-sent.png` show no QR code, email address, account number, or the sample bill's address or phone number.
  - The fonts' sources are documented without copying the hashes.
- **Privacy:**
  - No keys or tokens.
  - Only `example.com` email addresses.
  - Placeholder addresses only (`proxy.example`, `*.example.workers.dev`).
  - The private canvas is named but has no URL or ID in the repository.
  - The boards hold only relative content-hash blob paths, and load only Google Fonts and an uncommitted local `support.js`.
- **Tests:**
  - Phase 6's flaky `RequestTilesTest` assertion is fixed.
  - The plural tests cover Vietnamese, French, Persian, Arabic and the `ar-XB` pseudolocale.
  - `BillSafetyTest` covers a bill read by the AI and reopened from the list.
  - No new suppressions or TODOs were added to production code.

### Unresolved questions
1. **App icon:** the launcher icon, the Android 12+ splash screen and the Play listing still use the old green receipt (`ic_launcher_foreground.xml`, `launcher_background` #0B6E4F). The app's new mark, the violet and lime split circle, appears on Home and on every shared ticket. Should the icon be in scope?
2. **Sample bank account:** `ScreenGalleryTest.kt:907-908` uses Vietcombank's bank code with a 13-digit account number that looks real, visible in `10-requests-method-sheet.png`. Is it certain to be fictional? If not, use a number that cannot exist. (Answered after the review: the owner chose a number that cannot exist, and the sample is now all zeros; the number the review quoted is left out here for the same reason.)
3. **Owner decisions:** the phase files still list items needing the owner's approval. Examples: the "Amount:" line in the sender's region format, signing out over a split closing the split, the AI's explanation written in US format next to amounts in the phone's region, and phase 7's chart and wording choices. Were all of these approved in the 21 November screenshot review? No record ties each item to a decision.
4. **Keyboard test:** is `KeyboardOnDeviceTest`'s accessibility check meant to run anywhere?
