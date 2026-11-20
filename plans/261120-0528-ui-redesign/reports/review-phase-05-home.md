## Code review: commit d3e0f2f (phase 5, steps 1 to 4: home, latest group, texts)

### Scope
- **What I read:** all 31 files (+1640/−124), using `git show d3e0f2f` and `git show d3e0f2f:<path>`. I also read the committed phase-05 plan, plan.md (decisions 3 to 6 and the planner defaults) and the boards Main, HomeEmpty and DarkHome.
- **Working tree moved during the review.** HEAD is now 2ad5700, which only changes plan files. `SvTile.kt` and `SvFields.kt` are being edited; the only change in `SvTile.kt` (IconInk made internal) does not affect anything below.
- **Not run:** Gradle, as instructed. Nothing was compiled, and ktlint, lint, unit tests and screenshots were not run. I did compare the recorded consent texts with `strings.xml` using a script in my scratchpad.

### Overall
`HomeSummary` is a pure function and its state table matches the plan. The home reads the request totals from the one activity-level holder; it never adds requests up again and never fetches. The consent texts are unchanged.

There is one real cross-account leak: the group tile can show the previous account's group after an account change. The home also names the person a group request is owed to from the name typed on the request, which the Requests list deliberately does not trust. One screenshot now depends on the current date.

---

### High

**1. The previous account's group tile comes back on Home after a sign-out, an account deletion or another account signing in.**

- **Where:** `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/home/HomeViewModel.kt:49-51`.
  - `group` is `stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)`.
  - `WhileSubscribed` keeps the last value forever by default (`replayExpirationMillis = Long.MAX_VALUE`), even after the Room observer has stopped.
- **Why the view model survives the account change:**
  - It is scoped to the Home entry (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt:225`, `:231-233`).
  - Home is never popped. `stackAfterAccountLeft` (`/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/TabNavigation.kt:33-36`) keeps Home and Settings, so the same HomeViewModel lives through every account change.
  - Signing out, deleting and signing in are only possible in Settings, so Home is always off screen when they happen.
- **Scenario:**
  1. Dana's home shows "Chiang Mai trip · You get back · THB 35.43".
  2. She taps the account button and deletes her account (confirmation dialog plus a call to the proxy), or signs out and Bao signs in through the Google picker. Either takes more than 5 s.
  3. After 5 s the tile's Room observer is cancelled, so `cache.clear()` (AccountRepository.kt:73, :83, :173) is never seen.
  4. Back on Home, `collectAsStateWithLifecycle()` starts from `group.value`, which is still Dana's tile. It is drawn during the fade back (or the predictive-back peek) until the restarted query emits null a few frames later, and then the list jumps up.
  5. Bao, or a signed-out phone, sees Dana's group name, avatar and balance. This breaks the requirement "khối chính, ô nhóm và số đếm … không còn gì của tài khoản cũ", and the commit message's "nothing of an account stays".
  - `month` is cached the same way. That is harmless (it is phone data); for example, the old currency shows for a few frames after changing it in Settings.
- **Why the tests pass:** `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/ui/home/HomeAfterSignOutTest.kt:123-126` keeps HomeScreen on screen and collecting with `collectAsState()` for the whole test, so the observer never stops. No test covers another account signing in.
- **Fix:**
  - Share `group` with `SharingStarted.Eagerly`. The view model lives as long as Home, which is the activity's lifetime, and the query returns one row. Alternatively use `WhileSubscribed(STOP_AFTER_MS, replayExpirationMillis = 0)`.
  - As a second safeguard, hide the tile when nobody is signed in, the way RequestOverviewViewModel does: `account.map { it.session }.distinctUntilChanged().flatMapLatest { if (it == null) flowOf(null) else groups.latestGroup }`.
  - That safeguard also covers the case where emptying the cache fails during a deletion (AccountRepository.kt:173-180, outcome ACCOUNT_DATA_NOT_DELETED). The session is gone there, but the group is still cached and would now sit on the first screen.
  - **Test to add:** take HomeScreen off screen, idle the main looper past 5 s, then sign out (and, separately, sign in a second account). Put HomeScreen back with `mainClock.autoAdvance = false` and assert that "Chiang Mai trip" is absent in the first frame.

### Medium

**2. The home names a group request's requester from the name typed on the request, while the Requests list uses the group's own name for them.**

- **Where:**
  - `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/requests/RequestTotals.kt:67` builds `youOweTo = owing.names { it.creditorName }`.
  - `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/home/HomeScreen.kt:246-249` shows it in "You owe … to …", and the YouOwe block shows it in its names row (`:153`).
- **The rule the Requests list follows:** `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/requests/RequestsViewModel.kt:40-45` deliberately uses "the requester's name as the group knows them, which only they can change, rather than the name they typed on the request". RequestsScreen.kt:221 uses it.
- **Scenario:** in a shared group, Minh sends the user a request and types "Dana" as the name the payer sees.
  - The Requests tab lists it under "Minh".
  - The home says "You owe ₫150,000 to Dana", with a "D" avatar.
  - So the home contradicts the tab it leads to, and brings back the impersonation the Requests list was built to prevent.
  - (The pay sheet at RequestsScreen.kt:271 also uses the typed name; that was already the case before this commit.)
- **Fix:**
  - Resolve names in the holder with the same rule, for example `RequestTotals.of(signedIn, requests, creditorNameOf = { it.creditorName })`.
  - Have RequestOverviewViewModel pass `groups.creditorNameOf(it) ?: it.creditorName`, moving `creditorNameOf` out of RequestsViewModel.kt:177-182 so both view models share it.
  - Add a RequestTotalsTest case with a group request whose typed name differs from the group's name.

**3. The `21-history` screenshot will change on 1 January 2027.**

- **Cause:**
  - Bill rows now write their date through `shortDateText(day, today)`, which adds the year when the date is outside today's year (HistoryScreen.kt:289-292, DateTexts.kt:20-21).
  - `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt:524` builds HistoryScreen without `today`, so it falls back to `LocalDate.now()` (HistoryScreen.kt:90).
- **Effect:**
  - The row shows the sample bill dated 2026-11-13: "Nov 13" today, "Nov 13, 2026" from 2027.
  - Once phase 8 records the reference images and the gate compares against them as CI does, `21-history` and `21-history-dark` will fail on the date alone.
  - Before this commit the row printed the raw "2026-11-13", which did not depend on the day.
- **Fix:** pass `today = HomeFixtures.today` there, and in NumbersInTextsTest.kt:187 for consistency (that test only checks digits, so it is not broken).

**4. None of the home's new buttons is tested.**

- **What is missing:**
  - No test taps the invitation (HomeScreen.kt:168), the "You owe" line (:150), the month tile or the group tile (:205-235).
  - The new AppStartupTest case taps the centre button, not the invitation, and every other test passes `{}`.
  - So AppNavHost.kt:242-247 could be wired to `{}`, or open the wrong group id, and the suite would stay green.
  - HomeScreen's own handling of bills not yet read is also untested: no "no bills" text and no "See all" while the list is null.
- **Fix:**
  - In AppStartupTest, tap `hasText("Split a bill") and hasClickAction()` and assert "New split" opens.
  - Add a small HomeScreen test that checks each callback fires, including the group callback with the tile's id.

### Low

**5. The tile can keep showing a group the user is no longer in.**
- The KDoc of `latestGroup` (GroupRepository.kt:34-40) says a group the user "can no longer read … goes with its state". In fact only `refresh(id)` on a 403/404 (`:52-61`) and `leave` (`:93-97`) remove it; `refreshGroups()` (`:45-49`) never removes states of groups that dropped out of the list.
- **Scenario:** the owner removes the user (`removeMember`) from another phone. The Groups list drops the group on its next refresh, but the home keeps it as the latest group, with its old balance, until the user taps it.
- **Fix:** have `refreshGroups()` remove `group:` states whose id is not in the new list (guarded by the same `since`), and correct the KDoc.

**6. The group's balances are worked out on the main thread every time the cache changes.**
- At HomeViewModel.kt:50, `.map(GroupTile::of)` comes after `watchLatest`'s `flowOn(Default)`, so it runs on Main.
- Room re-emits on every write to `proxy_cache` (requests, the groups list, request links). Each emission decodes a fresh GroupState and recomputes its `balances` over all bills and settlements.
- **Fix:** add `.flowOn(Dispatchers.Default)` after the map, and/or `distinctUntilChanged` on (key, savedAt) before decoding.

**7. The month name is formatted outside DateTexts.**
- HomeScreen.kt:270-272 builds its own `DateTimeFormatter.ofPattern("LLLL", TEXT_LOCALE)`. It is the only `ofPattern` outside `core/domain/.../DateTexts.kt`, which already has `monthYearText`.
- **Fix:** add a `monthText(month)` there.

**8. What a screen reader reads on the hero.**
- The hero is not merged, so TalkBack stops four times: "Friends owe you", "2 requests", "₫651,671", "Minh and Lan", then the button.
- **Optional fix:** merge the label, amount and people into one node, leaving the button separate.
- `spokenText = money.text` (HomeScreen.kt:241) gives the screen reader exactly the visible text. The `SvHeroAmount` contract and the catalog use a spoken form ("651,671 dong"). There is no helper for that yet, so this needs a decision rather than a fix.
- The tiles read fine: one button each, reading label, note, value and caption, with the action "Open".

**9. Differences from the boards, for the owner's screenshot review.**
- **"You owe …" line:** a pill with centred text and no chevron or bold amount. The board has a 48 dp bar with 16 dp corners, text aligned to the start, a bold amount and a chevron (Main.dc.html:47-50).
- **Invitation button:** `SvSmallAction` is 44 dp tall; the board's is 52 (HomeEmpty.dc.html:36).
- **Month tile icon:** a line icon instead of the board's filled bars (Main.dc.html:57).
- **Zero balance:** shows "settled up" in lower case (strings.xml:612) beside the capitalised "You get back" and "You owe". This follows the plan's wording.

**10. (Info) Three new strings are not used yet.**
- `split_paid_the_bill`, `split_what_was_understood` and `assistant_tell_the_ai` stay unused until steps 5 and 6, so lint will report UnusedResources warnings. With the current lint settings these are warnings, not errors. Step 4 asks for them to be added now.

---

### Checked and found correct
- **The three request states:** `HomeSummary.of` (HomeSummary.kt:38-52) treats signed out, not fetched yet, and fetched with nothing open the same way. Nothing ever shows a zero owed.
- **Bills not read yet versus no bills:** bills still being read give no main block (not the invitation), and a phone with no bills gets the invitation.
- **Planner defaults:** open requests win over the invitation; when only the user owes, "You owe" is the main figure. The month tile only appears beside a requests block, and only on a phone with bills. All cases are in HomeSummaryTest.
- **Currency choice:** the total in the user's own currency leads; otherwise the newest request's currency does. I verified the order: the proxy sorts `created_at DESC, id DESC` (proxy/src/requests.ts:256), `upsert` re-sorts newest first, and `groupBy` keeps the order. The other currencies show as tags and are never converted.
- **No second sum and no fetch:** the home only reads what `RequestTotals.of` builds once. RequestTotalsTest covers the new counts and names (trimmed, each once, blank names left out).
- **Overflow:** `sumOf` cannot overflow, because the proxy caps amounts at 10¹⁵ and the list at 200. The hero amount shrinks to 20sp and then wraps. An `ArithmeticException` in the month spending is caught.
- **Hero amount:** it uses the three parts of `formatParts`. The vi-VN test string contains the same no-break space (U+00A0) that `format()` writes.
- **Requests count and main block after an account change:** the holder is shared eagerly and keyed to the session. Sign-in empties the cache before saving the session, and late answers are dropped by `generation`.
- **The new DAO query:** the `substr` prefix comparison is exact and case-sensitive, with no LIKE wildcards. The keys "groups" and "request-link:" cannot match "group:", and ties are broken by key. The database schema does not change and nothing is kept outside ProxyCache. GroupRepositoryTest covers the order, the list key, leaving a group and `clear()`.
- **Group tile rules:** positive balance shows "You get back"; negative shows "You owe" with the sign dropped; zero shows "settled up"; no group or an unknown balance shows no tile.
- **Account button:** its description stays "Settings"; the letter is hidden from screen readers. The touch target is 48 dp around a 44 dp tile, and the letter stays the same size at 200 % text. It shows a person icon when no name is set, and deleting the account with phone data forgets the name.
- **Invitation button:** it has words only. The centre button is the only element with the description "Split a bill", and AppStartupTest asserts exactly one.
- **Texts:**
  - The diff touches exactly the eight strings of decision 6, and the two renamed keys have no stale references left in code, unit tests, device tests or the baseline profile.
  - The seven new labels are there, plus "You owe … to …", "N requests" and "and N others", which the board needs.
  - Every count goes through `plural(...).resolve()`.
  - The eight consent texts match their recorded copies exactly (checked by script), and ConsentTextsTest also fails if a consent text is added or removed.
  - After this commit "Claude" remains only in `ai_consent_where` and in `split_show_sent` / `split_hide_sent`, which step 7 removes.
- **Recent bills:** three rows, each with its category tile; the short date when the bill's date can be read, the printed text when not, and the day it was saved when there is no date. "See all" shows when there are bills or recurring bills.
- **Bills list in AppNavHost:** it now starts as null until read, so a phone with bills never flashes the invitation.
- **Wiring:** the dependency injection setup and AppModuleTest, the four call sites of `HomeScreen`, and the design-system rules (no direct Material buttons left in HomeScreen) are all in order.
- **Scope:** nothing beyond steps 1 to 4, apart from the components the board needs (`SvBrandTopBar`, `SvAccountButton`, the `SvSectionHeader` action), which also went into the catalog and a behaviour test.

### Plan follow-ups
- The other run has ticked the four Todo items of steps 1 to 4 in the working tree. The work is there, but "bài sau đăng xuất" (the after-sign-out test) is only really met once finding 1 is fixed and the navigation path is tested. I would also land the fixes for findings 2 and 3 before step 15's review.

### Unresolved questions
1. When the user only owes money, the main block offers no way to the requests to pay, unlike the "You owe" line under "Friends owe you". Is that intended?
2. With no bill counted this month (start of the month, only drafts, only older bills, or no exchange rate), the home shows "November · ₫0 · Your share of 0 bills". Is that wanted?
3. Should "settled up" be capitalised on the tile?
4. After a different Google account signs in, the account button still shows the first letter of the name saved on the phone. Is that intended?
5. While a sign-in has run out (no session, cache kept), the group tile still shows, as the Groups list does. Keep it that way?
6. If this month's spending cannot be added up, a phone with bills and no open request shows no main block at all. Is that an acceptable fallback?

Status: DONE_WITH_CONCERNS
Summary: Steps 1 to 4 are implemented as the plan describes, the consent texts are unchanged, and the home never re-adds or fetches requests. However, the Home view model keeps the previous account's group tile and shows it for a few frames after an account change made in Settings, and the after-sign-out test cannot catch this. The home also uses the untrusted typed name for group requests, and the 21-history screenshot will change in 2027.
Concerns/Blockers: Nothing was compiled or run (no Gradle). Fix finding 1 and add its test before treating the sign-out requirement as met.
