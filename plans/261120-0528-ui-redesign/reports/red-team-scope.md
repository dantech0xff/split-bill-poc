# Red team: scope and contracts (UI redesign plan)

Reviewer lens: Scope & Complexity Critic, verification role Contract Verifier. Date 2026-11-20.
Scope reviewed: `plan.md`, `phase-01` to `phase-08`, `plans/reports/brainstorm-261120-0148-ui-redesign.md`, with the scout and research files in this folder. All file:line citations refer to the working tree at `/home/user/split-bill-poc`. The requested scope (37 boards, light and dark, new bottom-bar navigation, removing the two items, web pages following the shared tokens) is treated as a constraint. Doubts about requested items are raised as questions, not as cuts.

## Finding 1: "Font licences" screen, navigation key, Settings row and screenshot are unrequested UI
- **Severity:** High
- **Location:** Phase 6, sections "Overview", "Key Insights", "Requirements", "Files to Create / Modify", "Implementation Steps" step 6; Phase 7, "Requirements"
- **Flaw:** The plan adds a new screen (`FontLicencesScreen.kt`), a new `FontLicencesKey` and entry in `AppNavHost.kt`, a new Settings row, a new string "Font licences", and a new reference screenshot. None of these is on the 37 boards. The brainstorm's Settings board lists only "hàng hồ sơ và thanh đo số lượt gọi AI" as changes. The planner's own font research says a visible screen is not required.
- **Failure scenario:** The work adds a navigation key that the Phase 3 `Navigator` must route and that `NavigatorTest` and `BackNavigationTest` must cover, plus a screenshot. It also adds a Settings row that the owner never approved on the canvas. The owner then reviews a Settings screen that differs from the approved `Settings` board, and the phase-end "so với canvas" check fails or gets waived.
- **Evidence:** phase-06:14 "app có chỗ hiển thị giấy phép của hai phông chữ"; phase-06:29 "một màn hiển thị là cách dễ thấy nhất"; phase-06:37 "hàng \"Font licences\""; phase-06:45 "Create: `ui/settings/FontLicencesScreen.kt`; khóa `FontLicencesKey`"; phase-07:32 "màn giấy phép phông". Research `research/researcher-02-fonts.md:37-38`: "Ship the copyright notice and the licence text with the fonts. A file in the app or a licences screen is enough. Credit in the interface is not required." Brainstorm:102 "Cài đặt có hàng hồ sơ và thanh đo số lượt gọi AI." The app has no licences screen today (`researcher-02-fonts.md:20`). Phase 1 already ships both `OFL.txt` as raw resources (phase-01:72, :77), which meets the licence.
- **Suggested fix:** Keep the two raw `OFL.txt` resources from Phase 1 and drop the screen, key, row, string and screenshot. Ask the owner, as a question, whether a visible licences entry is wanted. If yes, record it as an owner decision in `plan.md` and get the board drawn.

## Finding 2: Phase 3 removes the back action of tab roots but misses three test callers, so its own success criterion cannot compile
- **Severity:** High
- **Location:** Phase 3, sections "Files to Create / Modify" and "Success Criteria"
- **Flaw:** Phase 3 removes the root-screen back button from `GroupsScreen`, `RequestsScreen` and `InsightsScreen`. Its test list covers only `BackNavigationTest`, `AppStartupTest`, `AppLinkTest`, `AppLaunchTest`, `PaymentRequestJourneyTest`, `JourneySteps` and `Journeys`. The gallery tests that pass `onBack` to these screens belong to Phases 5 and 6, which run after Phase 3.
- **Failure scenario:** Deleting `onBack` from `GroupsScreen`, `RequestActions` and `InsightsActions` breaks compilation of `:app:testProdDebugUnitTest`, which is Phase 3's success gate. The alternative is to keep the parameters and ignore them. That leaves dead API that the boundary and catalog docs then describe wrongly.
- **Evidence:** phase-03:57 "`ui/groups/GroupsScreen.kt`, `ui/requests/RequestsScreen.kt`, `ui/insights/InsightsScreen.kt` (bỏ nút lùi của màn gốc)"; phase-03:84 "`./gradlew :app:testProdDebugUnitTest` qua". Callers not listed: `app/src/test/java/com/dantech/splitvibe/ui/GroupsGalleryTest.kt:79` (`onBack = {}` on `GroupsScreen`), `GroupsGalleryTest.kt:194` (`onBack = {}` in `NO_REQUEST_ACTIONS`, used at :111, :116), `app/src/test/java/com/dantech/splitvibe/ui/SpendingGalleryTest.kt:118` (`onBack = {}` in `InsightsActions`). Production callers: `AppNavHost.kt:316, :417, :504`.
- **Suggested fix:** Add `GroupsGalleryTest.kt` and `SpendingGalleryTest.kt` to Phase 3's file list. Alternatively, state that the parameter is removed in Phase 3 and that the three test lines are changed in that phase.

## Finding 3: Phase 6 promises a notification route to "Recurring" that does not exist
- **Severity:** High
- **Location:** Phase 6, section "Requirements" (second bullet)
- **Flaw:** The plan says that opening from a notification "vẫn đưa tới thẻ Recurring". No notice opens `RecurringKey` today. The draft notice opens the draft bill itself through `AppLink.OpenBill`, which Phase 3 routes to `HomeKey, SplitKey`. To honour the requirement, an implementer has to change what a tapped notice does, which is a behaviour change outside "giữ mọi tính năng". The requirement also contradicts the Phase 3 deep-link table.
- **Failure scenario:** The implementer reroutes `KIND_DRAFT` to the Recurring tab to meet the requirement. The user taps "your draft is ready" and lands on a list instead of the draft. `AppLinkTest` and the Phase 3 `OpenBill` row then disagree. If the implementer follows Phase 3 instead, Phase 6's requirement fails review.
- **Evidence:** phase-06:35 "Mở từ thông báo hoặc từ hành động \"Repeat\" vẫn đưa tới thẻ \"Recurring\"." `app/src/main/java/com/dantech/splitvibe/ui/AppLink.kt:40-42` (`KIND_DRAFT` returns `OpenBill`); `AppNavHost.kt:178` (`is AppLink.OpenBill -> openBill(link.id)`); phase-03:46 "`OpenBill(id)` | Home | `HomeKey`, `SplitKey`". The complete list of `RecurringKey` callers is `AppNavHost.kt:129` (declaration), `:219` (Home place button, removed in Phase 3), `:294` (History "Repeat"), `:322`, `:332` (entry), and `docs/architecture.md:159`.
- **Suggested fix:** Change the requirement to cover only "Repeat" from the "All bills" tab (`AppNavHost.kt:294`). Replace "mọi chỗ mở `RecurringKey`" (phase-06:54) with the exact caller list above, including `docs/architecture.md:157-159`.

## Finding 4: The "Split a bill" label contract is undefined for the round centre button, and 12 test and profile callers depend on it
- **Severity:** High
- **Location:** Phase 2, "Thành phần mới" (`SvNavBar`) and step 3; Phase 3, step 9; Phase 4, "Key Insights" (labels kept)
- **Flaw:** Phase 4 promises that the text "Split a bill" is kept. The brainstorm draws the centre action as a round lime button ("nút tròn"), and Phase 2 says only "một nút giữa có nhãn". The plan never says whether that label is visible `Text` or only a `contentDescription`. `onNodeWithText` and UiAutomator `By.text` match only text. Phase 3 step 9 handles the "Requests" label clash but not this one.
- **Failure scenario:** If the round button exposes "Split a bill" only as a content description, every caller below finds 0 nodes. That breaks the JVM tests, all four e2e journeys and the baseline-profile journey, which then records nothing and fails the Phase 7 profile step. If the label is visible text and the HomeEmpty CTA on the same screen also reads "Split a bill", `onNodeWithText` throws "expected exactly 1 node". All these tests start on an empty install.
- **Evidence:** phase-02:53, :77; phase-03:49 "nút giữa có nhãn \"Split a bill\""; phase-03:71 (only "Requests" handled); phase-04:30; brainstorm:53. Callers (12): `app/src/test/java/com/dantech/splitvibe/BackNavigationTest.kt:83, :104`; `AppStartupTest.kt:33, :65`; `app/src/androidTest/java/com/dantech/splitvibe/SplitFlowOnDeviceTest.kt:87`; `AppLaunchTest.kt:45, :51`; `e2e/SampleBillJourneyTest.kt:40`; `e2e/DeclinedAiJourneyTest.kt:38`; `e2e/PaymentRequestJourneyTest.kt:75`; `e2e/ManualEntryJourneyTest.kt:32`; `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt:19` (`By.text`). String `home_split_a_bill` is at `strings.xml:882`.
- **Suggested fix:** In Phase 2, specify the semantics: either visible text, or `contentDescription` with all 12 callers switched to a role-plus-label matcher in Phase 3. Add step 9 of Phase 3 to these callers. Make the HomeEmpty CTA label unique, or have tests scope the lookup to the nav bar.

## Finding 5: The Home hero and the Requests badge read a request cache that only the Requests and Settings screens refresh
- **Severity:** High
- **Location:** Phase 3, "Requirements" (Requests badge); Phase 4, "Key Insights" and "Requirements" (Home hero)
- **Flaw:** `RequestRepository.requests` is a cache that is `null` until first fetched. It is refreshed only when `RequestsViewModel` is created and in `SettingsViewModel`. Phase 4's `HomeViewModel` reads it without refreshing, and the `HomeSummary` states have no "not fetched yet" case. Phase 3 also requires a pending-count badge, but the summing function (`HomeSummary`) is created only in Phase 4. Phase 3 has no data source for the badge.
- **Failure scenario:** A user signs in on a new phone and lands on Home. The hero shows "spending this month" and the Requests tab shows no badge. When the user opens the Requests tab, the hero flips to "Friends owe you" and the badge appears. Phase 5's criterion "Friends owe you trên Requests bằng tổng trên trang chủ" holds only after a visit to Requests. Adding a refresh on Home start is a new server call per launch, which plan.md:69 puts out of scope as a server-load change unless the owner agrees.
- **Evidence:** `app/src/main/java/com/dantech/splitvibe/data/requests/RequestRepository.kt:53-54` ("null before they were ever fetched"), `:56` (`refresh()`); `ui/requests/RequestsViewModel.kt:59-66` (refresh on init or sign-in); `ui/settings/SettingsViewModel.kt:60`. Plan: phase-03:37 "Mục Requests mang số yêu cầu đang chờ"; phase-04:24 "Số liệu mới cần ba nguồn: `RequestRepository.requests`…"; phase-04:36; phase-04:50; phase-05:73.
- **Suggested fix:** Move `HomeSummary` (or a small pending-count function) into Phase 3, or move the badge to Phase 4. Add a "never fetched" state to `HomeSummary`. Ask the owner whether Home may call `refresh()` when signed in, and record the answer.

## Finding 6: The `SvBottomBar` redesign breaks its nine callers' button colour, and the Insights tab root would stack two floating bars
- **Severity:** Medium
- **Location:** Phase 2, table "Thành phần hiện có" (`SvBottomBar`, `SvPrimaryButton`); Phase 6, "Requirements" (`Insights`)
- **Flaw:** Phase 2 makes `SvBottomBar` an ink (`bar`) floating pill "nút chính bên trong màu `accent`", while `SvPrimaryButton` stays `primary` (purple in light). Every caller puts an `SvPrimaryButton` inside, and the plan gives no mechanism, such as a local content colour or an `SvPrimaryButton` variant, for the colour switch. Separately, Insights becomes a tab root with `SvNavBar` (Phase 3) and keeps its "Export these bills" `SvBottomBar`. Phase 6 does not say where the export button goes.
- **Failure scenario:** On light theme, a purple button sits on an ink pill on Split, Result, Editor, Pro, Claim, AiConsent and RequestMoney. This violates the owner's colour rule (brainstorm:46). On Insights with bills, two floating bars overlap at the bottom, or the export bar covers `SvNavBar`.
- **Evidence:** phase-02:37-38; brainstorm:46 "trên nền tối hoặc nền tím, nút chính màu xanh chanh"; `core/designsystem/.../components/SvPrimaryButton.kt:29-38` (`Button` with default colours), `:46`. Callers (9): `ui/editor/BillEditorContent.kt:83`, `ui/split/NewSplitContent.kt:272`, `ui/split/ResultContent.kt:724`, `ui/split/AiConsent.kt:82`, `ui/split/RequestMoneyContent.kt:113`, `ui/paywall/ProScreen.kt:342`, `ui/claim/ClaimScreen.kt:406`, `ui/insights/InsightsScreen.kt:123`, `app/src/debug/.../catalog/CatalogScreen.kt:96`. Insights tab root: phase-03:32, phase-06:36.
- **Suggested fix:** In Phase 2, define how `SvPrimaryButton` picks `accent` inside `SvBottomBar` (for example a `CompositionLocal` set by the bar), and list the nine callers. In Phase 6, state where "Export these bills" sits relative to `SvNavBar` according to the `Insights` board.

## Finding 7: The web token export adds unused palette tokens and leaks weight-800 type into system-font web pages that are "not redesigned"
- **Severity:** Medium
- **Location:** Phase 1, step 6; Phase 8, "Requirements" and "Key Insights"
- **Flaw:** Phase 1 exports every `SvPalette` token to `tokens.css` in light and dark: 7 person colours, 9 category colours, bar, accent, moneyIn and moneyOut, notice and photoZone. Nothing on the web uses them, because `app.css` is changed only "nếu một vai màu đổi nghĩa" and the web layout is out of scope (plan.md:39, :70). Meanwhile the existing export carries `fontWeight` and size for every type style. After Phase 1, `titleLarge` and `headlineSmall` become Bricolage 800 at new sizes. Web headings that use `--sv-type-title-large-weight` and `--sv-type-headline-small-weight` then render system-ui at weight 800.
- **Failure scenario:** The web pages, which the owner chose not to redesign, get heavier and resized headings in system fonts, and no test catches it. Phase 8 still claims "Bố cục và chữ của trang không đổi". The unused palette variables become an exported contract that `docs/web.md` must document for no consumer.
- **Evidence:** phase-01:91 "thêm các token của `SvPalette` vào danh sách màu cho cả sáng và tối"; phase-01:63-64 (`titleLarge` uses `display` 800, 20/26); phase-08:32 "Bố cục và chữ của trang không đổi; phông vẫn là phông hệ thống"; phase-08:41. `core/designsystem/src/test/.../tokens/WebTokensTest.kt:45-50` (exports size, line height, weight and tracking per style), `:126-144` (style list). `web/src/app.css` uses `--sv-type-title-large-*`, `--sv-type-headline-small-*`, `--sv-type-body-large-*` and `--sv-type-label-large-*` (2 uses each), and no palette-only token.
- **Suggested fix:** Export only the tokens `app.css` reads. Defer `SvPalette` web export until a web page needs it. Decide explicitly whether the web keeps its weights: either clamp the exported web weight or accept the change and update the Phase 8 wording. Raise this with the owner, since it touches "web layout not redesigned".

## Finding 8: Phase 2 rewrites 32 Material call sites in packages that Phases 4 to 6 own and rebuild, and plans no dialog component for 12 dialogs
- **Severity:** Medium
- **Location:** Phase 2, step 6 and Todo "Màn hình không còn gọi thẳng ô nhập, công tắc, ô chọn của Material"; plan.md "Thứ tự và phụ thuộc"
- **Flaw:** Phase 2 converts every direct Material input, switch, checkbox, radio and `AlertDialog` in screens to `Sv…` components. These call sites sit in packages that plan.md assigns to Phases 4, 5 and 6, which rebuild the same screens right afterwards. There is no `SvDialog` among the new components, yet step 6 lists `AlertDialog` for conversion, and Phase 6 restyles only the delete-account dialog.
- **Failure scenario:** Each of these screens gets edited twice, and Phase 2 creates merge conflicts with the later parallel phases that the ownership rule was meant to prevent. The 11 other dialogs either stay default Material, now on an ink `secondaryContainer`, or each phase invents its own dialog styling inside screens. Ad hoc dialog styling is what `DesignSystemBoundaryTest` exists to forbid.
- **Evidence:** phase-02:58, :80, :88; plan.md:63 "mỗi giai đoạn sở hữu các gói `ui/` riêng". Direct calls under `app/src/main/java` outside `InputFields.kt`: 16 `OutlinedTextField`, 2 `Checkbox`, 1 `RadioButton`, 1 `Switch`, 12 `AlertDialog`, across 17 files. Dialogs: `ui/history/HistoryScreen.kt:155`, `ui/split/AssistantContent.kt:241`, `ui/split/SplitScreen.kt:204`, `ui/split/RequestMoneyContent.kt:195`, `ui/claim/ClaimScreen.kt:465`, `ui/settings/SettingsScreen.kt:290`, `ui/recurring/RecurringScreen.kt:110`, `ui/requests/RequestsScreen.kt:350`, `ui/groups/GroupScreen.kt:189, :205, :226, :478`. Phase 6 dialog: phase-06:38.
- **Suggested fix:** In Phase 2, build the components (including one dialog wrapper matching the `DeleteAccount` board) and the `InputFields.kt` swap only. Move each call-site conversion into the phase that owns the package, and list the files per phase.

## Finding 9: `NumbersInTextsTest` renders four redesigned screens and is missing from every phase, and the "THB 187.00" format change is silently dropped
- **Severity:** Medium
- **Location:** Phase 4 and Phase 6, "Modify tests"; plan.md "Nguồn thiết kế" (string precedence rule)
- **Flaw:** `NumbersInTextsTest` constructs `ProScreen`, `SettingsScreen`, `HomeScreen` and `HistoryScreen` with their current signatures. Phase 4 gives `HomeScreen` a view model and Phase 6 turns History into the two-tab `Bills` screen, yet no phase lists this test. Separately, the brainstorm records that the design writes "THB 187.00" (with a space) while the app prints "THB187.00". It lists this among changes the owner must confirm. The plan never mentions it. plan.md:26 says only that `strings.xml` wins on text, and currency formatting does not come from `strings.xml`.
- **Failure scenario:** Phase 4 or 6 breaks compilation of `NumbersInTextsTest` and the fix gets improvised late. The owner compares screenshots with the canvas, sees "THB187.00" everywhere, and reports a mismatch in Phase 7. Changing `BillCurrency.format` at that point touches about 70 `.format(` call sites, the PDF summary (out of scope) and the share card.
- **Evidence:** `app/src/test/java/com/dantech/splitvibe/text/NumbersInTextsTest.kt:136, :142, :154, :155` (screen constructors), `:112-113` (asserts "₫250,993" and "$12.50"); `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/BillCurrency.kt:60-63`; brainstorm:105 "Mã tiền tệ cách số một dấu cách, ví dụ \"THB 187.00\"; app hiện in \"THB187.00\"". Searching the plan folder for `THB` finds no mention of the format change, and searching for `NumbersInTextsTest` finds nothing.
- **Suggested fix:** Add `NumbersInTextsTest.kt` to the test lists of Phases 4 and 6. Add an explicit owner decision for the currency spacing (keep the current format, or change `BillCurrency.format`), with its callers listed if it changes.

## Finding 10: "Qua khi bỏ các bài so ảnh" has no defined mechanism, yet it gates Phases 1 to 6
- **Severity:** Medium
- **Location:** Success Criteria of Phases 1 to 6; plan.md "Mặc định do người lập plan đặt" (screenshots recorded once in Phase 7)
- **Flaw:** Every phase before 7 passes `:app:testProdDebugUnitTest` "khi bỏ các bài so ảnh", but the build has no switch that skips screenshot comparison. The plan names no Gradle filter, Roborazzi mode or test exclusion.
- **Failure scenario:** Each implementer improvises an exclusion: `--tests` filters, `-Pscreenshots=compare`, or temporarily recording and reverting. The phase-end evidence is then not comparable, and a real regression in a non-screenshot assertion inside a `…GalleryTest`, such as the `waitFor` label checks, gets excluded along with the images.
- **Evidence:** `app/build.gradle.kts:177-180` (only maps `-Pscreenshots=<mode>` to `roborazzi.test.<mode>`, default `verify`); `.github/workflows/ci.yml:44-46` (the unit task includes screenshot comparison). Plan: phase-01:109, phase-02:93, phase-03:84, phase-04:86, phase-05:71, phase-06:77; plan.md:38.
- **Suggested fix:** Define one command in plan.md, for example `-Pscreenshots=compare` (Roborazzi compare mode, which reports without failing) if verified on Roborazzi 1.76, or an explicit `--tests` exclusion pattern, and use it in every phase's criteria.

## Verification Results

| # | Interface / claim | Callers or evidence (count) | Result |
|---|---|---|---|
| 1 | Strings `split_show_sent`, `split_hide_sent` removed | 3: `ResultContent.kt:378`; literals in `ScreenGalleryTest.kt:269-273`; `strings.xml:346-347` | PASS (Phase 4 lists both) |
| 2 | `showOcrText` removed | 4 uses: `ResultContent.kt:140, :377, :378, :380` | PASS |
| 3 | `SavedBill.ocrDocument` kept as the "already read" flag | 2: `SplitBillViewModel.kt:606, :791` | PASS |
| 4 | "Photo stays on this phone" label | Only in `ai_consent_bill_read`, `strings.xml:117` (consent text, frozen by plan.md:33); no other app occurrence | PASS (consistent; nothing to remove in code) |
| 5 | Screenshot `09-sent-to-claude` and the 4 unwritten images | 6 files exist in `app/src/test/screenshots/` (07-pick-people, 08-item-sharers, 09-sent-to-claude, each light and dark); total 97 | PASS |
| 6 | `RecurringKey` removed | 6: `AppNavHost.kt:129, :219, :294, :322, :332`; `docs/architecture.md:159`. No notice route | FAILED (Finding 3: notification claim false; "mọi chỗ" not enumerated) |
| 7 | `HistoryKey` becomes a class with a tab | 5: `AppNavHost.kt:98, :214, :277, :289`; `docs/architecture.md:157` | PASS (docs via Phase 8) |
| 8 | `HomeScreen` signature (view model, removed place buttons) | 4: `AppNavHost.kt:207`; `ScreenGalleryTest.kt:386`; `PseudoLocaleGalleryTest.kt:87`; `NumbersInTextsTest.kt:154` | FAILED (misses `NumbersInTextsTest`) |
| 9 | Tab-root back removed (`GroupsScreen.onBack`, `RequestActions.onBack`, `InsightsActions.onBack`) | 6: `AppNavHost.kt:316, :417, :504`; `GroupsGalleryTest.kt:79, :194`; `SpendingGalleryTest.kt:118` | FAILED (Finding 2) |
| 10 | Label "Split a bill" kept | 12 callers (Finding 4) | FAILED (semantics undefined) |
| 11 | Home "Settings" content description (now an account tile) | 4: `AppStartupTest.kt:40, :49, :71`; `AppLaunchTest.kt:41` | PARTIAL (both files in Phase 3; plan does not say the tile keeps "Settings" as its description) |
| 12 | `SvBottomBar` new look | 9 call sites (Finding 6) | PARTIAL (colour mechanism and Insights collision missing) |
| 13 | `SvLargeTopBar` split into two | 2: `CatalogScreen.kt`, `HomeScreen.kt` (scout section 3) | PASS |
| 14 | Direct Material inputs and dialogs replaced | 32 sites in 17 files, 12 of them `AlertDialog` | PARTIAL (Finding 8: no dialog component; ownership clash) |
| 15 | `WebTokensTest` reads `Shapes()` and exports type weights | `WebTokensTest.kt:147` (`Shapes()`), `:45-50`; consumers in `web/src/app.css` (186 tokens in `tokens.css`) | PARTIAL (Finding 7) |
| 16 | `RequestRepository.requests` as a Home and badge source | Refreshers: `RequestsViewModel.kt:60-66`, `SettingsViewModel.kt:60`; null before first fetch (`RequestRepository.kt:53`) | FAILED (Finding 5) |
| 17 | `AppLink` variants in the Phase 3 table | 5 variants `AppLink.kt:11-21`; consumers `AppNavHost.kt:172-181`, `MainActivity.kt:47`, `AppLinkTest.kt` | PASS |
| 18 | `AppNavHost.kt:146` single stack, `open()` at :554 | Verified; `replace` is justified by `:464-465` (JoinGroup then Group) | PASS |
| 19 | `SPLIT_SURFACE` at `HomeScreen.kt:88`, `SplitScreen.kt:134` | Verified | PASS |
| 20 | `SvAvatar.kt:57-68` colours, `SvElevation.kt:66` tint | Verified via scout and source | PASS |
| 21 | navigation3 1.2.1, lifecycle 2.10.0, AGP 9.1.1 | `gradle/libs.versions.toml:2, :10, :11` | PASS |
| 22 | `ProScreenTest` 55 lookups, `SettingsScreenTest` 36 | 55 and 34 `onNode`/`onAllNodes` lines | PASS (34 vs 36 immaterial) |
| 23 | Stale balance comment at `GroupsScreen.kt:52` | Verified: "each with balances across its bills" | PASS |
| 24 | `GroupsGalleryTest` waits for "Payment code for VietQR" | `GroupsGalleryTest.kt:119` | PASS |
| 25 | `RecurringViewModel` scoped to the activity | `AppNavHost.kt:149-150` | PASS |
| 26 | Baseline-profile journey labels kept | `Journeys.kt:19-23` ("Split a bill", "Use the sample bill", "Bill photo", "Recent bills") | PARTIAL (see #10) |
| 27 | Seven font files vs research's six | Research recommends six (`researcher-02-fonts.md:7`) and leaves the 96pt cut to the designer (`:138`); plan.md:37 declares it as a default | PASS (disclosed default) |
| 28 | `BillCurrency.format` spacing change from the brainstorm | `BillCurrency.kt:60-63`; about 70 `.format(` sites in `app/src/main`; `NumbersInTextsTest.kt:112-113` | FAILED (unaddressed, Finding 9) |
| 29 | Exclusion of screenshot comparison | `app/build.gradle.kts:177-180` has no skip mode | FAILED (Finding 10) |

Counts: 29 interfaces or claims checked. PASS 15, PARTIAL 6, FAILED 8.

## Unresolved questions for the owner
1. Is a visible "Font licences" entry wanted, or are the bundled licence files enough?
2. May Home refresh payment requests from the server on each start when signed in?
3. Should the web pages keep their current heading weights, or follow the app's weight-800 display styles?
4. Currency spacing: keep "THB187.00" or change to "THB 187.00" as drawn?
