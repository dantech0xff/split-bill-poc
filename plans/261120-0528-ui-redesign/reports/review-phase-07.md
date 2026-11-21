## Review of 565e8ec: phase 7 (Bills, Insights, Settings, Pro, design-system boundary)

Most of what the phase asks for is there and correct:
- The Bills tabs read as tabs.
- The eleven direct Material calls are gone, and the new rule is sound.
- The chart's bars add up to the total at the top.
- Settings keeps its four account states and its sign-out and delete conditions.
- The delete-account dialog is right.
- All the strings the tests look for are unchanged.

I found five medium problems and nine low ones. The two most important have the same cause: actions moved to the end of long pages, but their progress and result messages stayed at the top. So the result of an export, or of deleting the account, can appear where the user cannot see it. Second, the Pro plans no longer form one radio group for TalkBack.

**How I reviewed:**
- I read the before and after of all 28 files with `git show`, the phase file, plan.md, the ten boards and the phase 6 review.
- I checked four behaviours against the bytecode of the libraries the project resolves (taken from the Gradle cache with `javap`): ui 1.12.1, material3 1.5.0-rc01, foundation 1.12.1 and ui-test 1.12.1.
- I ran the new boundary regex over the committed `app/src/main/java` and `app/src/debug/java` with `git grep`. It matches nothing.
- I did not run Gradle. A hook blocked reading `app/build`, so I have no test results. I edited nothing.

## Findings, most severe first

### Medium

**1. The result of an export appears above the screen, where the user cannot see it.**
- Where: `ui/insights/InsightsScreen.kt:131-141` (the message is the item right after the period choice) and `:240-248` (the export button, now at the end of the list).
- Mechanism:
  - The export can now only be started from the end of the list. Its result (saved, save failed, share failed, no file picker, nothing to export) is still inserted as item 1.
  - A lazy list keeps its first visible item in place when an item is inserted above it. I checked this in foundation 1.12.1: `LazyListScrollPosition.updateScrollPositionIfTheFirstItemWasMoved` moves the index to wherever the old first key went. So the new card lands above the viewport.
  - `SvMessageCard` has no live region, and this call adds none, so TalkBack says nothing either.
- Scenario: 12 months of bills (the board's page is 2,440 px tall). The user scrolls to the end, taps Export these bills, then Save to files, and picks a folder on a full card. The write fails and the "could not save" card appears at the top, out of sight. The button simply becomes usable again, and the user believes the files were saved.
  - Before this commit the bar was visible at any scroll position, so most exports started from the top, where the card shows.
- Fix: show the message item directly above the export item when the period has bills, and give it `liveRegion = Polite`. Add a test that scrolls to the export, sets `ExportStatus(message = …)` and asserts the card `assertIsDisplayed()`.

**2. The progress and results of signing out and deleting the account stay at the top, while the buttons moved to the bottom.**
- Where: `ui/settings/SettingsScreen.kt:183-195` (Sign out and Delete account at the end of the page) and `:284-292`, `:311` (the "Working…" row and the result message, inside the calls card at the top).
- Scenario:
  - A signed-in user has agreed to the AI, and the build has the legal links. The page is then about 1,200 dp tall; `ProGalleryTest` needs a 1,500 dp window to draw it whole with the speech card. At the end of the page, the calls card is above the viewport.
  - The user taps Delete account, then Delete. Both buttons disappear (`!accountUi.working`), and "Working…" appears in the card at the top.
  - The deletion can end as "account data not deleted", "phone data not deleted", "not confirmed", or refused (`SettingsViewModel.kt:42-46`, `:117-121`). The buttons come back, and the explanation sits in the card at the top.
  - When the buttons disappear the page gets about 120 dp shorter and the scroll position clamps, so on a large phone part of the card may show at the top edge. With 130 % text or more, or on a 360 × 640 dp phone, it is entirely off-screen.
  - Sign out has the same shape.
- Before this commit the buttons sat inside the card, next to the progress row and the message.
- Fix: while these actions run or have just ended, show the working row and the message just above Sign out (or bring the card's status into view with a `BringIntoViewRequester`). Add a test at 412 × 915 dp that scrolls to Delete account, ends a deletion with a failure, and asserts the message is displayed.

**3. The two Pro plans no longer read as one radio group to a screen reader.**
- Where: `ui/paywall/ProScreen.kt:216` (the `Column(Modifier.selectableGroup())`) and `:239-245` (each plan is a `SvCard` wrapping a `Row(Modifier.selectable(role = RadioButton))`).
- Mechanism (checked in bytecode):
  - material3 1.5.0-rc01's plain `Surface`, which `SvCard` uses, adds `semantics { isContainer = true }`. So each card is a semantics node sitting between the group and its radio row.
  - In ui 1.12.1, `CollectionInfo_androidKt.setCollectionInfo` counts only direct children (`getReplacedChildren`) that carry `Selected`. `setCollectionItemInfo` works only when the node's direct `parent` has `SelectableGroup`. Neither is true any more.
  - So TalkBack reads two unrelated radio buttons, without the group or "1 of 2".
- Before this commit, the old `ListItem` applied `Modifier.semantics(true){}.then(modifier)` to its own `Surface`. Each selectable plan was therefore a direct child of the group.
- `ProScreenTest` still passes: it only counts `Role.RadioButton` nodes and checks which one is selected.
- Fix: put `clip(shape).selectable(…)` and the content description on the `SvCard` modifier, so they live on the Surface's own node, and keep the Row for layout only. Alternatively, set `collectionInfo` on the column and `collectionItemInfo` on each plan. Add a test that each radio node's `parent` has `SemanticsProperties.SelectableGroup`.

**4. One bill with a wrong date makes the All time chart enormous.**
- Where: `ui/insights/InsightsViewModel.kt:251-256` (`monthsOf`), `InsightsScreen.kt:283-302`, `SvBarChart.kt:57-90` and `:117-124`.
- Mechanism:
  - All time runs from the first counted month to max(this month, the latest counted month), with no limit.
  - The editor stores any typed text as the date (`BillEditor.kt:19`), `dayOf` accepts any ISO year, and the proxy's schema asks only for YYYY-MM-DD. A Thai receipt dated in the Buddhist era (2568) could plausibly be read that way too.
- Scenario: a typo of "2925-11-13" gives 10,801 months. The chart then:
  - composes two Boxes per month in one Row, on the main thread, again every time the item scrolls back into view;
  - measures 10,801 labels, against a text-measurer cache of 8;
  - draws bars of about 0.02 dp;
  - highlights the future month, although the code comments it as "the one that is still running" (`InsightsScreen.kt:299-300`);
  - says "… to November 2925. November 2925 is the highest".

  A year typed as 0202 gives about 21,900 months. All time had no chart before this commit.
- Fix: end the chart at this month and cap its start (for example the last 24 months, or grouped by year beyond that), and highlight this month only when it is in the chart. Test with a far-future and a far-past bill.

**5. Coming back to All bills hides the search field above the top of the list.** This predates the commit, but it is on the tab path you asked about.
- Where: `ui/history/HistoryScreen.kt:112-138`. The "search" item exists only on All bills and comes before the "tabs" item.
- Mechanism: the same scroll anchoring as in finding 1, confirmed in foundation 1.12.1. After visiting Recurring, the first visible key is "tabs". When search is inserted before it, the list keeps "tabs" first.
- Scenario: open Bills, tap Recurring, tap All bills. The list starts with the tabs and the field is scrolled away above. The same happens after Repeat switches to Recurring and the user goes back.
- The structure dates from phase 3, and the phase said to keep it, so this needs a decision.
- Fix: put the field and the tabs in one item keyed "tabs" (the tab nodes stay the same, so focus is kept), or `requestScrollToItem(0)` when switching to All bills while the list was at the top. Test that the field `assertIsDisplayed()` after two switches.

### Low

6. **The sign-in result may no longer be announced** (`SettingsScreen.kt:136`, `:211-225`, `:251-255`).
   - Before, the live-region text in the card changed in place to "Signed in with Google as …".
   - Now that text is removed, and the profile row appears as a new node.
   - ui 1.12.1's `sendSemanticsPropertyChangeEvents` skips nodes that have no previous counterpart (checked). So whether TalkBack reads the new row depends on it picking a live region out of a subtree change. This needs a TalkBack check on a device.

7. **Large text squeezes names inside words.**
   - Where: Recurring rows (`RecurringScreen.kt:119-123`) and the Insights rows for categories, people, groups and months (`InsightsScreen.kt:400-404`, `:306-317`). Each puts the name with `weight(1f)` beside an amount that keeps its full width, and now a 44 dp tile too.
   - By my estimate from the type sizes (not rendered), at 200 % text "Internet" or "Entertainment" breaks inside the word.
   - The rows on the All bills tab got the `SvFieldRow` treatment for exactly this case; these did not.
   - There is no large-text screenshot of the Recurring tab. The 200 % rule in plan.md does not name these screens.

8. **BackNavigationTest taps "Sign out" without scrolling** (`BackNavigationTest.kt:484`, `:526`).
   - In ui-test 1.12.1, `performClick` is `performTouchInput { click() }` (checked). A centre below the scroll viewport misses, and `awaitState` then times out after 60 s.
   - Sign out now sits near 900 dp down in a 915 dp window. One more line in the card pushes it out; for example an error card, since the mock proxy answers 404 to every call except the requests list.
   - It probably passes today, since the commit message implies the class was run. Add `performScrollTo()` before both taps.

9. **`clearTheCache` hides failures** (`BackNavigationTest.kt:554-556`). If `clear()` throws, the launched job completes exceptionally, `isCompleted` is true, and the test goes on with the cache not cleared. The old `runBlocking` rethrew. Use `async` and `await()` once it completes, or assert that the job was not cancelled.

10. **The rule only covers six exact names, and the commit message overclaims.**
    - The rule cannot see `BasicAlertDialog`, `DatePickerDialog`, `TriStateCheckbox` or `TextField`. That matches the phase, which names six controls.
    - But the message says no screen calls Material dialogs directly, and `RepeatBillSheet.kt:140-152` still calls `DatePickerDialog` with Material `TextButton`s.
    - The guard that the test scans files is a total (`DesignSystemBoundaryTest.kt:18-20`), so a missing `src/debug/java` would go unnoticed. A per-directory minimum fixes it.

11. **No chart for a one-month period is a decision nobody has approved.**
    - Where: `InsightsScreen.kt:284`, and `InsightsScreenTest.kt:72-78`, which asserts the chart is absent.
    - The phase asks for a chart of the months of the period with no exception. This month is the default period (`InsightsViewModel.kt:118-120`), so most visits show none.
    - plan.md says states without a board need the owner's approval through screenshots, and there is no screenshot of This month.

12. **The search field now has an 80-character cap** (`HistoryScreen.kt:114-128`, through `SV_TEXT_FIELD_MAX_LENGTH`). Before, it had none. The phase says to keep the length limits that exist. Harmless, but either record it or pass a cap of its own.

13. **A test name promises more than the test checks.** "The search field shows no label above it" (`HistoryScreenTest.kt:51-56`) would still pass with a label. The component test that checks the 52 dp height covers `SvTextField`, not the screen's call.

14. **Small items:**
    - The "No group" tile uses the card's quiet colour #F0EFEA; the board draws #D9D8D2.
    - Settings' `Links` (`SettingsScreen.kt:357-406`) re-implements `PageLinks` from `ui/common/Links.kt:52-72`: the "cannot open" state and its live-region notice.
    - The phase's risk note says the meter "rounds down by segment", but the boards draw part-filled segments, and `SvMeter` follows the boards. The note is stale, not the code.

## Checked and found correct

**Bills tabs**
- Role.Tab and Selected sit on each Surface's own node, as direct children of a `selectableGroup`, so TalkBack gets the group information.
- The tabs are one item with a fixed key, and the node id is kept (`HistoryScreenTest`).
- The open tab is saved with `rememberSaveable`, and Repeat switches tab on the same screen. The new `AppStartupTest` covers repeat, then back, then reopening the list.
- The tests' `assertIsOn` became `assertIsSelected`, and no stale toggle assertion remains.
- `assertNoNavigationBar` now ignores tabs that are not the bar's.

**Bills rows and sheets**
- Bills rows show the category tile, the phase 4 short date, the category in words, people, Draft and the menu. Their spoken label is unchanged, and the home's row is unchanged because it passes no menu.
- Recurring and delete dialogs use `SvDialog` with `irreversible` and the same texts.
- The repeat sheet keeps its `MAX_NAME` cap (`cutAt` never splits an emoji), its keyboard options, and the "Change the day of the first draft" and "Repeat" labels.

**Insights**
- The list frame ends above the navigation bar, and a test checks it.
- Both notices stay.
- The chart covers empty months for 12 months and All time.
- The bars add up to the top total for all three periods (`InsightsViewModelTest`).
- `months` is computed on `Dispatchers.Default`.
- Money goes through `format` and `formatParts`, months through `monthText` and `monthYearText`.
- The chart is described in words, and the highlighted month is also bold. The share bars are hidden from TalkBack, with the amount in text.
- There are no duplicate lazy keys.

**Settings**
- The four account states match the Settings and SettingsStates boards.
- Sign out and delete show under the same conditions as before; an expired sign-in nulls the session (`AccountStore.kt:210`).
- The delete dialog's tick box is off on every opening, its row is a 48 dp checkbox, and Delete is in the error colour (pixel test).
- The local currency field keeps capitals, auto-correct off and 3 characters (the test reads `EditorInfo`). The name field keeps word capitals and `MAX_NAME`.
- The consent block shows only once consent was given, with Take back.
- `settings_ai_consent_given` is byte-identical before and after. `strings.xml` only gains keys.

**Pro**
- The purchase bar is still in `bottomBar`, and every text `ProScreenTest` looks for is unchanged.
- The Yearly tap is on screen at 1,200 dp.
- `displayMedium` is Bricolage ExtraBold 38/42, as on the board.

**Boundary rule**
- `SvSwitch`, `SpeechServiceSwitch`, `Role.Switch` and imports pass; a bare `Switch(` and `material3.Switch (` fail.
- It scans `src/main/java` (111 files) and `src/debug/java` (2). No other source set holds Kotlin.

**Sign-out helper**
- It cannot deadlock: `awaitState` keeps the main looper running while `clear()` runs on Default.
- It cannot race with an older fetch: `put` and `removeUnder` compare `since` with `generation`, which `clear()` increments under the same mutex.

**Device tests and scope**
- No androidTest or `Journeys.kt` finder reaches the rebuilt parts.
- `AppLaunchTest`'s `onNodeWithText("Settings")` still matches only the title. `JourneySteps` finds tabs by content description, which the Bills tabs lack.
- The commit doesn't touch `ui/home`, `ui/split`, `ui/editor`, `ui/groups`, `ui/requests`, `ui/payments` or `ui/claim`. The design-system changes are additive, except `SvMeter`'s colour on the page and `SvBarChart`'s internals, which had no callers before.
- No screenshots were committed.

## Unresolved questions
1. Were the plan's test gate (`-Pscreenshots=compare`) and the API 34 `device-tests.yml` run on 565e8ec? The todo item is still unticked, and I could not read the build outputs.
2. Does the owner accept no chart for one-month periods, including the default This month (finding 11)?
3. What range should the All time chart cover (finding 4)?
4. Fix finding 5 now, although the phase froze the tab structure, or record it for later?
5. Findings 3 and 6 need a TalkBack check on a device. I could not test this here, and `AppLaunchTest`'s accessibility checks on the new Settings screen are also unverified.
