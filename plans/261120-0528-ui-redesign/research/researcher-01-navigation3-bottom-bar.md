# Research: bottom navigation bar on Navigation 3

Date: 2026-11-20 (UTC). Scope: four tabs (Home, Groups, Requests, Insights), a centre "new split" action, and flow screens that hide the bar, added to the existing `AppNavHost`.
Labels: **[V]** means I verified it in the cited source. **[I]** means it is my inference and should be checked in a test.

## Bottom line

Use the official **multiple back stacks** recipe as published at the `1.2.0` tag of android/nav3-recipes. That gives one saved `NavBackStack` per tab, a saved current tab, per-stack entry decorators, "exit through Home", and a flattened `[Home stack + current tab stack]` passed to `NavDisplay(entries = ...)`.
Draw the bar inside the tab-root entries rather than in a `Scaffold` around `NavDisplay`. That way the bar hides on flow screens without extra logic, it moves with predictive back, and the centre button keeps the `SPLIT_SURFACE` grow animation.
Bump navigation3 from 1.2.1 to 1.2.2, which is a bug fix only. Stay off 1.3.0-alpha.

## Repo facts (read in the repo)

- `gradle/libs.versions.toml`: navigation3 `1.2.1`, lifecycle `2.10.0` (including `lifecycle-viewmodel-navigation3`), AGP `9.1.1`, compileSdk 37.
- `AppNavHost.kt` uses one `rememberNavBackStack(HomeKey)` inside `SharedTransitionLayout`, passed to `NavDisplay(backStack = ..., sharedTransitionScope = this, transitionSpec/popTransitionSpec/predictivePopTransitionSpec = fades)`. It has `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`, and every key is `@Serializable`.
- In the same file, the screens navigate with `backStack.open(key)` (which skips a duplicate on top) and `backStack.remove(key)`. Tab roots also call `remove(GroupsKey)`, `remove(RequestsKey)` and `remove(InsightsKey)` as their "back".
- `SplitBillViewModel` and `RecurringViewModel` are activity-scoped.
- `Screen {}` supplies `LocalSvSharedTransition` from `LocalNavAnimatedContentScope`. `SPLIT_SURFACE` is set on the Home button (`HomeScreen.kt:88`) and on the split screen (`SplitScreen.kt:134`).
- `AppLink` has five cases. `MainActivity` applies the link only when `savedInstanceState == null`, and `AppNavHost` pushes it onto the stack in a `LaunchedEffect`. There is no `onNewIntent` handling, and launchMode is the default.
- `BackNavigationTest` (Robolectric) drives the back gesture with `dispatchOnBackStarted/Progressed/Cancelled` and asserts that both screens are drawn during the preview. It starts from the "Split a bill" text.

## 1. Official guidance: per-tab stacks vs one shared stack

- [V] The migration guide assumes "one or several top-level routes (usually displayed in a bottom navigation bar), and each has its own back stack". It also assumes that the state of each stack is kept when you switch, and that you "always exit the app through the Home screen". Source: https://developer.android.com/guide/navigation/navigation-3/migration-guide (fetched 2026-11-20).
- [V] Recipe `multiplestacks` (https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks, plus repo https://github.com/android/nav3-recipes/tree/1.2.0/app/src/main/java/com/example/nav3recipes/multiplestacks):
  - `rememberNavigationState(startRoute, topLevelRoutes)` creates one `rememberNavBackStack(key)` per top-level route.
  - The current route is saved with `rememberSerializable(..., serializer = MutableStateSerializer(NavKeySerializer())) { mutableStateOf(startRoute) }`.
  - `toDecoratedEntries()` calls `rememberDecoratedNavEntries` once per stack, each with its own `rememberSaveableStateHolderNavEntryDecorator` and `rememberViewModelStoreNavEntryDecorator`. It returns only Home's entries, plus the current tab's entries when that tab is not Home.
  - The recipe says that "even if a top level route is not in use its state is still retained".
- [V] The recipe `main` branch builds against `navigation3 = 1.3.0-SNAPSHOT` and moved the tab-state helper to `rememberNavKey(stateSerializer = ...)`. The `1.2.0` tag (the latest recipes release, which targets Nav3 1.2.0 and lifecycle-viewmodel 2.11.0) uses the `MutableStateSerializer` form. Copy from the tag, not from `main`. Source: https://github.com/android/nav3-recipes/releases.
- [V] The older "top-level back stack" recipe, `commonui` (`TopLevelBackStack`, https://developer.android.com/guide/navigation/navigation-3/recipes/common-ui), keeps the stacks in plain `mutableStateListOf`, so they are not saved after process death. It also clears a tab's history when you go back out of it, and lets Home move above other tabs, so "back from Home doesn't necessarily leave the app". That does not meet this app's requirements. Reject it.
- [I] Option ranking:
  1. Per-tab stacks with the `multiplestacks` model. This keeps each tab's history and is the official persistent model.
  2. One shared stack with tab "replace" semantics. This is the smallest diff, but each tab loses its history and the state of a tab that is not shown is gone unless you keep its keys.
  3. `commonui`, rejected for the reasons above.

## 2. Hiding the bar on flow screens, and a centre action that is not a tab

- [V] Navigation 3 gives three places for the bar:
  - (a) Outside `NavDisplay`, as in the `multiplestacks` recipe, which uses `Scaffold(bottomBar = NavigationBar ...)`.
  - (b) A `SceneDecoratorStrategy` (stable since 1.1.0), as in recipe `navscenedecorator`. It decorates every scene and keeps one bar instance across scenes with `movableContentOf` plus `sharedElement` plus a `cacheSize` placeholder. Source: https://github.com/android/nav3-recipes/tree/1.2.0/app/src/main/java/com/example/nav3recipes/navscenedecorator.
  - (c) Inside the entry content.
- [V] `Scene.metadata` defaults to the last entry's metadata (`Scene.kt` on androidx-main). Per-entry metadata can therefore mark which screens show the bar.
- [I] (a) is the simplest, but the bar sits outside every `AnimatedContent` scope. It would hide or show on its own animation, not in step with a back swipe being scrubbed. The centre button would also have no nav `AnimatedVisibilityScope`, so `sharedSurface(SPLIT_SURFACE)` would do nothing and the grow animation is lost.
- [I] (b) needs custom "who calls the movable content" logic once some scenes are left undecorated. In the recipe, only the entering scene calls the bar. On a tab-root-to-Split transition the entering scene has no bar, so the bar would vanish at once. This is the best choice if a navigation rail on large screens is planned, which it is not today.
- [I] (c), recommended: a `TabRoot {}` wrapper, used only for the four tab-root entries, draws the content plus the floating bar. Give the bar container a `sharedElement(rememberSharedContentState("bottom-bar"), LocalNavAnimatedContentScope.current)` so it stays still during tab switches and during back previews between two tab roots. Flow screens never call the wrapper, so they hide the bar without any flag.
- [I] Centre action: the button in the bar calls `splitViewModel.startNewSplit()` and then `navigator.navigate(SplitKey)`. `SplitKey` is not a top-level key, so the recipe's `navigate()` pushes it onto the **current tab's** stack, and back returns to that tab.
- [I] Move `sharedSurface(SPLIT_SURFACE, resize = true)` from Home's button to the bar's centre button, inside `Screen {}`, so that only one surface carries the key in any one scene.

## 3. Back between tabs, and predictive back with several stacks

- [V] Recipe `Navigator.goBack()`: if the top of the current stack is the tab root, it sets `topLevelRoute = startRoute` (Home); otherwise it pops the current stack. Pass it as `NavDisplay(onBack = navigator::goBack)`.
- [V] `NavigationBackHandler` is enabled only when `currentScene.previousEntries.isNotEmpty()`. `SinglePaneScene.previousEntries = entries.dropLast(1)`. Sources: `scene/NavigationEventHandler.kt` and `scene/SinglePaneScene.kt` on androidx-main (https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/navigation3/navigation3-ui/).
  - On Home's root the flattened list is `[HomeKey]`, so back is disabled and the system exits. That gives back from a non-Home tab, then Home, then exit.
- [I] Predictive back from the Groups root previews the scene for `[Home stack]`, which is the top of Home's stack, such as Settings if it was left open there. It previews this with `predictivePopTransitionSpec`, because the flattened list makes the preview "just another pop". This is a real advantage of flattening over swapping stacks.
- [V] `isPop(old, new)` (`ui/NavDisplay.kt`, androidx-main) is true only when `new` is a strict prefix of `old`. The existing fade specs fit all three cases:
  - Home to Groups (`[H]` to `[H,G]`) is a forward transition.
  - Groups to Requests (`[H,G]` to `[H,R]`) is forward, because the lists diverge.
  - Groups to Home is a pop.

  [I] I assume 1.2.x has the same logic.
- [V] Predictive-back fixes are already in 1.2.x: a back gesture during a forward transition (1.2.0-beta01, 1.1.7) and duplicate-key `SaveableStateProvider` crashes during rapid pops (1.2.0-beta01). Source: https://developer.android.com/jetpack/androidx/releases/navigation3.

## 4. State restoration with several stacks

- [V] `rememberNavBackStack` needs every key to implement `NavKey` and be `@Serializable`. Source: https://developer.android.com/guide/navigation/navigation-3/save-state. The repo already does this.
- [V] Each stack must have its **own** `SaveableStateHolderNavEntryDecorator` (multiple-backstacks recipe page).
- [V] Lifecycle 2.11.0-beta02 (May 2026) added a `rememberViewModelStoreNavEntryDecorator(ViewModelStoreProvider)` overload "enabling use cases such as supporting multiple back stacks where the same entry exists in both but requires separate state". Lifecycle 2.11.0 (2026-06-17) needs AGP 9.2.0 or later. 2.11.1 is the latest stable (2026-11-18). Source: https://developer.android.com/jetpack/androidx/releases/lifecycle.
- [I] Pitfalls:
  - (1) The same key can sit in two stacks at once, for example `SettingsKey`, `ProKey` and `PaymentMethodsKey`, which Groups, Requests, Split and Claim screens all open. With per-stack decorators on lifecycle 2.10.0 this probably works, but it is unproven. Test it, or stay at one copy per key: if the key already exists in another stack that is in use, switch to that tab rather than push a second copy.
  - (2) Iterate the tabs in a fixed `listOf(...)` order. `rememberNavBackStack` calls inside a loop are positional.
  - (3) Never let a tab stack become empty. Recipe `goBack()` calls `currentStack.last()`, and `NavDisplay` `require`s non-empty entries. Today's `remove(GroupsKey)`, `remove(RequestsKey)` and `remove(InsightsKey)` must become `goBack()`.
  - (4) Activity-scoped view models (split, recurring) are unaffected.
  - (5) Robolectric `recreate()` exercises the saved-instance-state path for both the stacks and the current tab.

## 5. Deep links into a tab

- [V] The official pattern is a synthetic back stack: `UriDeepLinkMatcher(...).withBackStack { listOf(HomeKey, it.key) }`, then `rememberNavBackStack(*syntheticBackStack)`. Up and Back are the same inside the app's own task. Sources: https://developer.android.com/guide/navigation/navigation-3/deep-links and https://github.com/android/nav3-recipes/blob/main/docs/deeplink-guide.md.
- [V] `UriDeepLinkMatcher` does not extract fragment values as arguments (1.2.0-alpha06 note). This app's tokens ride in the URI fragment (`/g/#{token}`), so keep the hand-written `AppLink.of`. The Nav3 deep-link API would add nothing here.
- [I] Map each `AppLink` to a tab plus that tab's stack with one `navigator.openIn(tab, vararg keys)`, leaving Home's stack as `[HomeKey]`:

  | AppLink | Tab | Stack |
  |---|---|---|
  | `Group(id)` | Groups | `[GroupsKey, GroupKey(id)]` |
  | `JoinGroup` | Groups | `[GroupsKey, JoinGroupKey]` |
  | `Requests` | Requests | `[RequestsKey]` |
  | `OpenRequest` | Requests | `[RequestsKey, RequestLinkKey]` |
  | `OpenBill` | Home | `[HomeKey, SplitKey]`, via the existing `openBill` |

  Back then walks down the tab's stack, then to Home, then exits. Keep the current "apply once when `savedInstanceState == null`" rule.
- [I] Out of scope, but worth noting: the guide's rule that "Back returns to the calling app when started in another app's task" is not implemented today, and this design does not change that.

## 6. Transitions and shared elements when the displayed stack switches

- [V] Passing `sharedTransitionScope` to `NavDisplay` is supported (since 1.1.0) and keeps scene changes smooth. Source: https://developer.android.com/guide/navigation/navigation-3/animate-destinations. The `entries =` overload accepts the same `sharedTransitionScope` and transition specs (NavDisplay reference, https://developer.android.com/reference/kotlin/androidx/navigation3/ui/NavDisplay.composable).
- [V] 1.2.2 (2026-11-18) fixes a "shared NavEntry not rendering on the incoming scene when a navigation interrupts a pop". This can happen when someone taps a tab during a back animation.
- [V] Known shared-element issues are listed **only for 1.3.0-alpha01 and later**, which uses `DeferredAnimatedContent`:
  - shared elements are rendered in the outgoing scene (b/582354988);
  - shared elements don't seek during the gesture phase.

  [I] Moving to 1.3.0-alpha would risk the scrubbed back preview that `BackNavigationTest` covers.
- [I] Because switching a tab changes the `entries` list, not the `NavDisplay`, transitions, `LocalNavAnimatedContentScope` and `SharedTransitionLayout` behave the same as for a push or pop today. Do not render a separate `NavDisplay` per tab: that would break shared elements and predictive back between tabs.

## 7. API changes between 1.2.1 and the latest

- [V] Latest stable is 1.2.2 (2026-11-18), with the bug fix above. 1.2.1 (2026-11-04) only lowered compileSdk to 36. Latest alpha is 1.3.0-alpha05 (2026-11-18). The 1.3 line adds dynamic features, `SceneInfo` title and url, public `EntryProviderScope.build()`, `DeferredAnimatedContent`, and deprecates `permitTransformDuringDeferredTransition`.
- [V] Nothing this design uses is deprecated in 1.2.x: `rememberNavBackStack`, `rememberDecoratedNavEntries`, `NavDisplay(entries)`, `NavKeySerializer`, decorators.
- [V] On the lifecycle side, `removeViewModelStoreOnPop` is deprecated in 2.11. The repo does not use it.

## 8. Recommended minimal design

1. Bump navigation3 to 1.2.2. Keep lifecycle at 2.10.0 unless the shared-key test in step 6 fails. If it does, move to 2.11.1 together with AGP 9.2.0 or later, and use the provider overload.
2. Add `NavigationState` and `Navigator`, copied from the recipe at the `1.2.0` tag, into `ui/` next to `AppNavHost.kt`.
   - Tabs: `listOf(HomeKey, GroupsKey, RequestsKey, InsightsKey)`.
   - Add to `Navigator`:
     - `open(key)`, which keeps today's guard against a duplicate on top.
     - `remove(key)`, which searches the stacks and never removes a tab root: for a root it calls `goBack()`.
     - `replace(old, new)`, for JoinGroup.
     - `openIn(tab, keys)`, for links.
3. In `AppNavHost`, replace `rememberNavBackStack(HomeKey)` with `rememberNavigationState`. Use `NavDisplay(entries = state.toDecoratedEntries(entryProvider), onBack = navigator::goBack, ...)` and keep the same `SharedTransitionLayout`, fade specs and `Screen {}`. Swap every `backStack.open/remove` for the `navigator.*` call of the same name.
4. Add a `TabRoot {}` wrapper used by the four tab-root entries.
   - It draws the floating bar: four items plus the centre button, which does `startNewSplit` and then `navigate(SplitKey)`.
   - The bar has a "bottom-bar" shared element, and the centre button carries `SPLIT_SURFACE`.
   - Settings stays a push from Home's account button.
5. Main trade-off: drawing the bar per entry (option c) trades the cheapest setup (a `Scaffold` bar) for correct hiding, predictive-back scrubbing and the grow animation. It costs a bar recomposed per tab root, and any indicator animation inside the bar restarts on every tab switch. The scene decorator (b) is the upgrade path if that becomes visible.
6. Tests: extend `BackNavigationTest`, and add a navigator unit test.
   - Back goes from a tab, to Home, then the activity finishes.
   - Predictive back from the Groups root previews Home, cancel restores Groups, and commit lands on Home.
   - Per-tab history survives a switch: Groups, then a group, then Requests, then Groups again shows the group.
   - The stacks and the selected tab survive `recreate()`.
   - The bar is shown on the four roots and absent on Split, Claim, Settings, Pro and PaymentMethods.
   - The centre button opens Split from any tab, and back returns to that tab. Update the screenshot of the grow animation.
   - Each `AppLink` builds the stack from the table in section 5, with the right tab selected.
   - `SettingsKey` opened from Home and from Groups at the same time: no crash, and each copy has its own state.
   - Rapidly tapping a tab during a back animation does not crash or leave a blank screen.

## Limitations

- I did not compile or run anything (Gradle was not allowed). The navigation3 source I read is androidx-main (1.3 alpha); I assume the 1.2.x behaviour matches. I did not search the issue tracker beyond the release notes.
- `gh` access to android/nav3-recipes was blocked, so I read the recipe files raw from GitHub at `main` and at tag `1.2.0`.

## Unresolved questions

1. Do detail screens inside a tab (Group, History, Recurring, RequestLink, JoinGroup) show the bar, or only the four roots?
2. Should Settings, Pro and PaymentMethods opened from inside another tab switch to Home's stack, or be pushed onto the current tab? This decides the duplicate-key risk.
3. Should reselecting the current tab pop it to its root? The recipe only emits a reselect event.
4. Should an App Link opened from another app's task return to that app on Back, as the official guide recommends? This is not covered today.
