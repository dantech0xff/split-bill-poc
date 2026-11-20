## Code review: phase 3 (one back stack with the floating navigation bar), uncommitted changes on feat/ui-redesign

I found one crash path, one missing test for a known Navigation 3 1.2.1 blank-screen bug, two account-change defects, and several smaller issues. The missing test matters most for this phase's test gate.

**Scope:** 25 modified files and 6 new ones, about +870/−320 lines in tracked files. HEAD b279b01 is a docs-only commit.

**How I checked:** I did not run Gradle and did not build or run any tests. To check library behaviour, I unpacked the Gradle-cache artefacts into the scratchpad and read their bytecode with `javap`: navigation3 1.2.1, navigationevent 1.1.1, activity 1.12.4 and compose runtime 1.12.1. I also confirmed the Navigation 3 release-note wording on developer.android.com.

---

### Findings, most severe first

**1. High (test evidence): the "tab during a back swipe" test cannot reach the known 1.2.1 bug**
- Where: `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/BackNavigationTest.kt:322-340`.
- What Navigation 3 1.2.2 fixed (I6a482, b/573660083): "shared NavEntry not rendering on the incoming scene when a navigation interrupts a pop". The 1.3.0-alpha notes describe the same change as "not rendered on screen when it is popped then pushed again before the pop completes".
- What the test does: it holds a swipe, taps a *different* tab, then *cancels*. No pop is committed and no popped entry is pushed again, so the test passes whether the bug is present or not.
- Why it matters: the plan's Risk Assessment makes these tests the evidence, with a report to the owner if they fail.
- Scenario left uncovered:
  - On Groups, press back. During the 400 ms fade, tap Groups on the incoming Home bar. `[Home, Groups]` is pushed again before the pop ends, and on 1.2.1 Groups can stay unrendered (blank, with no bar to tap).
  - The same applies to back from Split followed by the centre button during the fade.
- Fix: add tests with `compose.mainClock.autoAdvance = false`:
  - Choose Groups, call `onBackPressed()`, advance about 5 frames, send OnClick to `tab("Groups")`, restore auto-advance, then `assertDrawn("Groups")` and check that `tab("Groups")` is selected.
  - The same with the centre button after back from Split.
  - A held swipe, then a tab tap, then a commit (see finding 2).
  - If any of these fail, record it and tell the owner, as the plan says.

**2. Medium (crash, rarely reachable on a phone): back swipe on a tab, Home tab tapped, swipe released → empty back stack**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt:211` (NavDisplay uses its default `onBack`) and `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/TabNavigation.kt:33` (`choose(HomeKey)` shrinks the stack to `[Home]` while the gesture is still running).
- Verified in bytecode:
  - The nav3 completion handler runs `repeat(entries.size - currentScene.previousEntries.size) { onBack() }`, which is 1 for a `[Home]` scene.
  - The default `onBack` is `removeLastOrNull()`, with no guard.
  - navigationevent 1.1.1 `dispatchOnCompleted` delivers to `inProgressHandler` even after that handler was disabled. The handler and its `NavigationEventState` are remembered across scene changes, and `OnBackPressedDispatcher.onBackPressed()` routes into exactly that path.
  - NavDisplay then throws `IllegalArgumentException("NavDisplay backstack cannot be empty")`.
- Repro in the existing harness: on `[Home, Groups]` call `swipeBackHalfway()`, send OnClick to `tab("Home")`, then `onBackPressed()`. That is the existing test with "Home" instead of "Requests" and "commit" instead of "cancel".
- Reach: on phones the system normally steals other touches during a held back gesture. It can still happen with a trackpad back gesture plus touch, under automation, or with a tap and a back key in the same frame.
- Fix: pass `onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }` to NavDisplay, and add that test. This keeps Navigation 3 at 1.2.1 and adds no navigation state.

**3. Medium: after the account leaves, the rule keeps whatever screen is on top, not Settings**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/TabNavigation.kt:30` and `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt:572-579`.
- Why the top is not always Settings: deletion runs in `AccountRepository`'s own scope and "goes on after Settings is left" (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsViewModel.kt:37`, `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/data/account/AccountRepository.kt:93`). Settings does not block back.
- Scenario:
  - Settings → Delete account → back → Groups tab → open a group gives `[Home, Groups, GroupKey(g1)]`.
  - When the deletion ends, `clearSession()` turns the stack into `[Home, GroupKey(g1)]`.
  - The deleted account's group screen stays on top, the Groups root under it is gone, and so is Settings, the screen that reports how the deletion ended.
- Same at a cold start with an interrupted deletion: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/SplitVibeApp.kt:36` → `finishInterruptedDeletion()` → `clearSession()`, with a restored stack and `wasSignedIn = true`.
- Smaller case: sign-out waits up to 5 s for the device unregistration. Opening "Ways to get paid" or "Get Pro" in that time ends with `[Home, PaymentMethods]` or `[Home, Pro]`.
- Test gap: `TabNavigationTest` only uses stacks whose top is Settings or Home.
- Fix (product choice; the plan says "Home and Settings"): when Settings is in the stack, keep `[HomeKey] + stack.drop(stack.lastIndexOf(SettingsKey))`, which is Settings plus Pro or PaymentMethods opened from it. Otherwise keep `[HomeKey]`, or drop only the account-bound keys (`GroupKey`, `JoinGroupKey`, `RequestLinkKey`, `ClaimKey`). Add pure tests where the top is not Settings.

**4. Medium (cross-account): the request totals can show the previous account's requests just after a sign-in**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/requests/RequestOverviewViewModel.kt:31-33`, together with `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/data/account/AccountRepository.kt:73-74` and `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/data/proxy/ProxyCache.kt:32-36,49-52`.
- Precondition: account A's session ran out or the proxy refused it. `AccountStore.expireSession` (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/data/account/AccountStore.kt:195`) leaves the cache alone, so A's request list stays.
- What happens when B signs in:
  - `cache.clear()` returns once the delete commits, but the Room watch flow re-emits later, asynchronously.
  - `saveSession(B)` sets `signedIn` immediately.
  - `combine` therefore publishes `Loaded` with A's totals and count until Room emits null.
- Not visible in this phase, because sign-in happens on Settings or Pro, which have no bar. But `totals.value` is wrong for that window, and phases 5 and 6 read the same holder.
- Test gap: `RequestTotalsTest`'s sign-in case starts from an empty cache.
- Fix: tie the list to the session, for example `account.map { it.session }.distinctUntilChanged().flatMapLatest { s -> if (s == null) flowOf(SignedOut) else repository.requests.map { RequestTotals.of(true, it) } }`. A fresh Room collection after `clear()` runs a new query, so the stale list is never combined with B's session. Add a test: cache holds A's list, then `expireSession`, `clear`, `saveSession(B)`; assert the holder never reports `Loaded` before B's fetch completes.

**5. Low: fetches queue behind a stalled one**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/requests/RequestOverviewViewModel.kt:37`. `onEach { fetch() }` runs one fetch at a time.
- Effect: a hung fetch for the old session (call timeout 150 s, `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/data/proxy/ProxyApi.kt:268`) delays the "after every sign-in" fetch. The tab shows no count meanwhile.
- Fix: use `collectLatest { s -> if (s != null) fetch() }`. This folds into the fix for finding 4.

**6. Low: the bar's shared box on Insights differs from the other three roots**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsScreen.kt:113`, `/home/user/split-bill-poc/core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/components/SvNavBar.kt:90-94`, `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/AppNavBar.kt:44`.
- The system navigation-bar inset is added only once on Insights. The outer `navigationBarsPadding()` consumes it, and the two inner ones add 0.
- But the "bottom-bar" shared surface wraps SvNavBar's own inset padding. On Home, Groups and Requests its box includes the inset; on Insights it does not.
- Result: with `scaleToBounds()` (FillWidth, Center), the pill moves vertically by about half the inset during the 400 ms fade and while a back swipe scrubs. That is roughly 12 dp with gesture navigation and 24 dp with three buttons.
- Fix: on Insights, consume the inset only around the export bar: `Column { if (...) SvBottomBar(Modifier.consumeWindowInsets(WindowInsets.navigationBars)) {...}; bottomBar() }`. Check by hand on the emulator.

**7. Low (accessibility): the Bills tabs are rebuilt on every switch**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/history/HistoryScreen.kt:105-156`. Each tab has its own LazyColumn, and the All bills / Recurring choice is an item in each (`:123`, `:155`).
- Effect: choosing a tab destroys the focused radio button and creates a new one in another list, so TalkBack and keyboard focus are lost every time.
- Fix: one LazyColumn, with the search item shown only on All bills and a stable `item("tabs")` key. This still matches the boards (search above the tabs on Bills, no search on Recurring).

**8. Low: the snapshot wrapper in `showOnly` and the "single change" tests that pass for the wrong reason**
- Where: `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/TabNavigation.kt:41-51` and `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/ui/TabNavigationTest.kt:24-29, 64-71, 84-95`.
- In the app: writes made in one click handler on the main thread are already applied and observed as a single change, so `withMutableSnapshot` changes nothing.
- In the test: plain writes would fail for an unrelated reason. The stack is created in the same global snapshot, and compose's `writableRecord` returns the existing record without `recordModified` (checked in runtime 1.12.1), so no apply notification is sent at all.
- So the tests do not prove the "a step back then forward" claim in their comments.
- Fix: either drop the wrapper and assert only the final stacks, or call `Snapshot.sendApplyNotifications()` after building the stack in `changesOf` so the counter measures what it claims.

**9. Low: other test gaps and weak assertions**
- (a) Nothing checks that `AppNavHost` passes the holder's totals to the bar. Hard-coding `RequestTotals.SignedOut` would still pass every test, because BackNavigationTest's fake proxy returns `[]`. Suggest: signed in with one waiting request, assert the Requests tab's content description is "Requests, 1"; after sign-out, no count.
- (b) `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/ui/requests/RequestTotalsTest.kt:122-124`: `requestCount == 0` after one looper idle proves little. The request would leave on OkHttp's thread, and `ProxyApi.signedIn` already throws locally without a session (`ProxyApi.kt:142`).
- (c) `RequestTotalsTest.kt:136`: the name says "until the requests come", but no later fetch is made.
- (d) `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/AppStartupTest.kt:80-82`: choosing Insights again only re-checks the title, which would also pass if the screen were rebuilt. `TabNavigationTest` covers the no-op.
- (e) Untested: account deletion through the UI; the account rule across `recreate()`, which the comment at `AppNavHost.kt:566-571` claims; the open Bills tab across `recreate()`.

**10. Info: changes outside the phase's list**
- `/home/user/split-bill-poc/app/src/main/res/values/strings.xml` deletes `recurring_title` ("Recurring bills"). It is unused after this change and has no translations, but the phase only lists three new labels. Mention it in the phase report.
- `RecurringScreen.kt` now holds `RecurringBills`, so the file name no longer matches (naming nit).
- The page height of SpendingGalleryTest's whole-page shots went from 2000 dp to 2300 dp. That is test-only and justified by the second bar.

---

### Checked and found correct
- **Tab selection:**
  - Home gives `[Home]`.
  - Another tab sets or appends index 1 in one change.
  - Choosing the open tab is a no-op.
  - From a deeper stack the result is `[Home, tab]`.
  - The selected item comes from the entry's key; there is no separate tab state.
  - Only the four main screens are accepted.
- **Back:** from a root, back pops to Home. On Home, NavDisplay's handler is disabled, so the system exits; the tests check `hasEnabledCallbacks()`.
- **Centre button:**
  - It calls `startNewSplit()` (which saves the current draft first) and opens Split above the current root; back returns to that root.
  - `SPLIT_SURFACE` moved from Home's old floating button to the centre button.
  - The surface name is set only inside `Screen {}`, and the test counts 1 → 2 → 1 during the transition.
- **App links:**
  - `LaunchedEffect(link)` is unchanged.
  - A link is read only on a fresh create, and notices use NEW_TASK|CLEAR_TASK, so each link opens above `[Home]`.
  - `AppLinkTest` is untouched, and each link kind has a back test whose title finders are unique on their screens.
- **`recreate()`:** the stack and the Requests tab come back (AppStartupTest). Activity-scoped view models survive. The Bills tab is kept with `rememberSaveable`.
- **Account rule:** it fires only on `clearSession()` (sign-out, deletion, and sign-out after an expired sign-in), not on expiry. Settings keeps its view model when it moves to index 1.
- **`RequestTotals`:**
  - The formulas match the plan and agree with how RequestsScreen splits "mine" and "not mine".
  - Sums are per `BillCurrency(code, fractionDigits)`.
  - There are three states, and null is distinct from an empty list.
  - The count is hidden when signed out, unknown, or 0.
  - Errors are swallowed while cancellation is rethrown.
  - Late answers for an account that has left are dropped by the cache generation.
  - I found no path to double counting: refresh replaces the list, and `keep()` de-duplicates by id.
- **Layout:** all four root lists use `Modifier.padding(padding)`. The Requests frame/last-row check would fail with `contentPadding`, so it is a real test. Insights adds the system inset once.
- **Device tests and `Journeys.kt`:**
  - All twelve "Split a bill" finders now use the content description.
  - No text finder for the tab labels or "Recurring bills" remains.
  - `JourneySteps.tab()` matches role Tab plus a substring, so "Requests, N" also matches.
  - After "Done" the journey is back on Home with a single bar.
  - Nothing on Home looks likely to trip the on-device accessibility checks: tabs are at least 56 dp tall and about 55–73 dp wide, the centre button is 56 dp, the tab labels are not exposed as text, and no clickable text repeats.
  - `By.desc` works on the merged icon button.
- **Call sites and constraints:**
  - Every call site of the changed screen and action signatures is updated.
  - DesignSystemBoundaryTest's forbidden patterns do not occur in the new files.
  - No Gradle or verification-metadata file changed, and Navigation 3 stays at 1.2.1.
- **Plan follow-ups (I made no edits):** Todo items 1–9 look implemented. Item 10 (the test gate, the `device-tests.yml` run, screenshots to the owner) cannot be verified here.

### Unresolved questions
1. If a sign-in runs out and a different account then signs in, `AppNavHost.kt:189` treats the expired sign-in as still signed in, so the rule never runs and the old account's screens (for example `GroupKey`) stay under Settings. The cache is cleared, so no data leaks, but the screens linger. Is that intended?
2. Signing out from Settings opened over Split removes Split, as the plan says. `startNewSplit()` saves a draft, but photos not yet read are lost. Does the owner accept that?
3. `NavKeySerializer` calls `Class.forName` with no catch, so restoring an old stack containing `RecurringKey` would throw. Whether that is reachable after an update still needs the manual emulator check the plan lists.
4. The Bills tabs are announced as radio buttons ("checked"), while the bar's items are announced as tabs. Is that left for phase 7?

Status: DONE_WITH_CONCERNS
Summary: The phase is broadly implemented as planned, but finger-on-edge back plus a Home tap can crash with an empty back stack, the test meant to prove the known 1.2.1 blank-screen bug cannot reach it, and the account rule and the totals holder each have an account-change defect. I did not run Gradle; library behaviour was checked from bytecode in the Gradle cache.
Concerns/Blockers: Findings 1–4 should be resolved, or accepted by the owner, before this phase's test gate is treated as passed.
