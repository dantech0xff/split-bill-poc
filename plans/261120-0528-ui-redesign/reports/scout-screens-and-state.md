# Scout: screens, state and data of Split Vibe (input for the UI redesign plan)

Scope: read-only scouting of `app/src/main/java/com/dantech/splitvibe/ui/**`, the view models and repositories they use, `core/domain`, `core/designsystem` tokens, `app/src/main/res/values/strings.xml`. Line numbers are as read on 2026-11-20. No build was run. Paths are relative to `/home/user/split-bill-poc`.

## 0. Navigation skeleton (AppNavHost)

- `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt:88-133` declares the `@Serializable` NavKeys: HomeKey, SplitKey, SettingsKey, HistoryKey, PaymentMethodsKey, GroupsKey, GroupKey(id), JoinGroupKey(token), RequestsKey, ClaimKey, RequestLinkKey(token), InsightsKey, RecurringKey, ProKey. `SPLIT_SURFACE = "split"` is at line 136.
- `AppNavHost` (line 145) uses Navigation 3 (`NavDisplay`, `rememberNavBackStack(HomeKey)`, line 146). Entries are at lines 205-538. Each entry wraps its content in `Screen {}` (line 546) to provide the shared-transition scope.
- Shared view models: `SplitBillViewModel` (activity-scoped, line 148), `RecurringViewModel` (line 150). `billRepository.listings()` feeds Home and is collected at line 159.
- Back stack helper `open()` (line 554) and `backStack.remove(key)` is used for back everywhere.
- No bottom navigation exists today. Every screen owns its own `Scaffold`, top bar with back arrow, and sometimes a `bottomBar` (ResultContent FinishBar, RequestMoneyContent Done, BillEditorContent Done, Pro BottomPart). Adding a global bottom bar must account for these.
- Entries per screen (AppNavHost.kt): Home 205-222; Split 223-239; Claim 240-276; History 277-298; Insights 299-321; Recurring 322-337; Settings 338-368; Pro 369-393; Groups 394-420; Group 421-455; JoinGroup 456-479; Requests 480-509; RequestLink 510-525; PaymentMethods 526-538.

## 1. Per-screen inventory

Line counts from `wc -l`. "VM" = view model. Sheets/dialogs are listed with their line numbers.

### 1.1 Home
- Files: `ui/home/HomeScreen.kt` (178 lines). No view model. Params: `bills: List<BillListing>`, `onSplitBill`, `onOpenBill`, `onSeeAllBills`, `onOpenSettings`, `onOpenGroups`, `onOpenRequests`, `onOpenInsights`, `onOpenRecurring` (lines 57-68).
- Sections, in order: `SvLargeTopBar` with app name and settings icon (72-82); `IntroCard` "Split a bill with AI" (103-105, composable at 155); a FlowRow of four `PlaceButton`s: Groups, Requests, Insights, Recurring (106-117, composable at 146); "Recent bills" header with "See all" (118-130); empty state or the first `RECENT_BILLS = 3` rows via `BillListingRow` (131-139, const at 178); FAB "Split a bill" (83-90, shared surface `SPLIT_SURFACE`).
- Sheets/dialogs: none.
- Navigation callbacks: as in params; AppNavHost wires them at lines 207-220. `onSplitBill` calls `splitViewModel.startNewSplit()` then opens SplitKey (209-212).

### 1.2 Split (the split flow, one screen with steps)
- `ui/split/SplitScreen.kt` (235). Top-level `SplitScreen(viewModel, aiStatus, newCameraTarget, scanIntent, onExit, onOpenSettings, onOpenPro, onOpenPaymentMethods, onPickTogether, legalLinks, assistant)` at lines 39-56.
- Step selection (66-71): `SplitStep.Sent` when result and sentRequests; `Edit` when result and editing; `Result` when result; otherwise `Input`. `SplitStep` sealed interface at 216-235.
- BackHandler per step (79-88). Result sheets/dialogs at 184-209: `AiConsentDialog` for the bill read (184-192) and for the assistant (193-201); notice `AlertDialog` (203-209).
- Steps are rendered by `SvSteps` (135-179): `Sent` -> `RequestMoneyContent` (keyed VM `requests-{id}`, 141); `Edit` -> `BillEditorContent` (146); `Result` -> `ResultContent` (152); `Input` -> `NewSplitContent` (160).
- The surface `Modifier.sharedSurface(SPLIT_SURFACE)` (134) and `SEND_SURFACE = "send"` (31) are shared-element keys with Home's FAB and the Result/Sent buttons. Renaming these breaks the grow animation.
- Sub-files: `NewSplitContent.kt` (288; `NewSplitContent` 65, `PhotoSection` 146, `AnalyzeBar` 271), `ResultContent.kt` (799), `RequestMoneyContent.kt` (335), `SplitSheets.kt` (424), `DivisionInputs.kt` (169), `AssistantContent.kt` (371), `AiConsent.kt` (90), `SplitBillViewModel.kt` (848), `SplitAssistantViewModel.kt` (582).

Input step (`NewSplitContent.kt`): title "New split" (88); photo section (108, 146-268: take photo, choose photos, use sample, type by hand, lines 196-261); instruction field (122-123); error text with action (134-135); `AnalyzeBar` bottom bar with progress text and "Read and split the bill" (271-290, button at 285).

### 1.3 Result (ResultContent) - see section 2 for detail
- `ui/split/ResultContent.kt:128` `ResultContent(result, people, actions, assistant, assistantActions)`. `ResultActions` class at 98-119.
- Top bar: back and edit icon (156-173). Bottom bar `FinishBar` (174-182, composable 703-756).
- Sheets: AddPersonSheet (401-421), PersonSheet (423-441), ItemSplitSheet (443-460), PaymentsSheet (462-474), AddToGroupSheet (476-487), ExchangeRateSheet (489-504), plus `AssistantVoicePrompts` (397).

### 1.4 Bill editor
- `ui/editor/BillEditorContent.kt` (369). `BillEditorContent(bill, onEdit, onDone)` at line 69. No VM of its own; state is the `SplitBillViewModel`. Details in section 6.

### 1.5 Bills list (History)
- `ui/history/HistoryScreen.kt` (219), `ui/history/HistoryViewModel.kt` (43). Details in section 4.

### 1.6 Recurring bills
- `ui/recurring/RecurringScreen.kt` (183), `RecurringViewModel.kt` (70), `RepeatBillSheet.kt` (160). Details in section 4.

### 1.7 Insights
- `ui/insights/InsightsScreen.kt` (326), `InsightsViewModel.kt` (306), `ExportSheet.kt` (58).
- `InsightsScreen(state, export, actions)` at line 91. Sections in order (in `insights()`, 192-239): period chips (`item(key="period")`, 134), message (143), overview card (195, composable 244), notes (196-200), "by month" (205-212), "by category" (213-220), "by person" (221-229), "by group" (230-238). Overall line "spent on" at 259.
- Sheets: `ExportSheet` (`ExportSheet.kt:30`), opened from the top bar action at about line 124.
- VM `InsightsViewModel` at line 100: `state` (109-118, `WhileSubscribed`), `export` (121), `choose` (123), `share` (128), `saveToFolder` (134), `saveTo` (140), `shared` (146), `pickerMissing` (149), `dismissMessage` (151). `insightsOf()` at line 206.
- Nav callbacks: `InsightsActions` (71): onChoosePeriod, onShare, onSaveToFolder, onSaveTo, onShared, onPickerMissing, onDismissMessage, onBack.

### 1.8 Groups list
- `ui/groups/GroupsScreen.kt` (196). `GroupsScreen(groups, signedIn, status, myName, localCurrency, onCreate, onOpen, onOpenSettings, onDismissMessage, onBack)` at lines 55-66.
- Sections: sign-in card when signed out (84-94); status message (95-105); loading (106); empty state (107-114); one `SvListRow` per group with name and currency code (115-131). FAB "New group" when signed in (79-81). Sheet `NewGroupSheet` (150).
- The file header comment at line 52 says "with balances across its bills", but the rows show no balance (118-130). Unknown whether this is intended.
- VM `GroupsViewModel.kt` (91): `groups` (33), `signedIn` (36), `status` (43), `myName` (45), `localCurrency` (48), `created` (52), `refresh` (59), `create` (64).

### 1.9 Group (one group)
- `ui/groups/GroupScreen.kt` (517). `GroupScreen(state, status, openRequests, actions)` at line 93. Dialog state `GroupDialog` at 499.
- Sections in order (lines 146-160): balances (146, function 249: header with currency hint 251, cards per member 253-275, "settle up" transfers 280-316 with "Ask" and "Got it" buttons); members (152, function 318: owner/no-app badges, claims card 348-370, invite and keep-place buttons 379-380); bills (157 call, function 385, delete option 411-414); recent changes/activity (158, function 420).
- Dialogs (166-245): rename, keep place, take out of group, leave, "I got it" (received).
- VM `GroupViewModel.kt` (198): key `group-{id}` in AppNavHost (423). `group` flow (54), `status` (57), `share` (61), `left` (65), `refresh()` (71) called at init (68). Actions: invite, revokeInvites, rename, addPlaceholder, approve, removeMember, reject, deleteBill, markReceived (114), askFor (128), leave (174), refresh (`GroupActions` 70-90 in GroupScreen).
- `JoinGroupScreen.kt` (227): `JoinState` 59, `JoinGroupViewModel` 70, `JoinGroupScreen` 133.
- `AddToGroupSheet.kt` (189) and `AddToGroupViewModel.kt` (217): used from the Result step.

### 1.10 Requests
- `ui/requests/RequestsScreen.kt` (385), `RequestsViewModel.kt` (182, contains both `RequestsViewModel` and `RequestLinkViewModel`), `RequestLinkScreen.kt` (106), `RequestMessages.kt` (82), `RequestMoneyViewModel.kt` (142).
- `RequestsScreen(requests, signedIn, status, actions, creditorNames)` at lines 96-103.
- Sections in order: sign-in card (126-136), status message (137-147), loading (148), empty state when signed in and nothing (149-156), section "To pay" = requests where `!isMine` (158), section "You asked for" = `isMine` (165). `section()` helper at 212; `RequestRow` at 223; `statusText` at 240; `PaySheet` at 270; `PayPanel` at 292; `OpenLinkButton` with dialog at 334-369; `DetailsCard` at 371.
- Sheets/dialogs: PaySheet (270), host-link dialog (350).
- Nav callbacks: `RequestActions` (81-91): onReportPaid, onConfirmPaid, onRemind, onSetReminders, onDelete, onRefresh, onOpenSettings, onDismissMessage, onBack.
- VM `RequestsViewModel` (34): `requests` (37), `creditorNames` (43), `signedIn` (47), `status` (54), `share` (57), refresh (64), reportPaid (68), confirmPaid (70), setReminders (72), delete (74), remind (77), shared (90).
- `RequestLinkScreen(state, signedIn, onAccept, onReportPaid, onOpenSettings, onRetry, onBack)` at `RequestLinkScreen.kt:36`. VM `RequestLinkViewModel` (122-175), state `RequestLinkState` (112).

### 1.11 Payment methods ("Ways to get paid")
- `ui/payments/PaymentMethodsScreen.kt` (170): `PaymentMethodsScreen(methods, onSave, onMakeMain, onRemove, onBack)` at line 69; VM `PaymentMethodsViewModel` at line 49. FAB "Add" (90). Sections: intro (96), empty text (106), list of `MethodRow` (110, row at 137) with overflow menu "use for requests" / "remove" (160-161).
- `ui/payments/PaymentMethodSheet.kt` (288): `PaymentMethodSheet(editing, onSave, onDismiss)` at line 66.

### 1.12 Claim ("Let everyone pick their items")
- `ui/claim/ClaimScreen.kt` (508): `ClaimActions` (72), `ClaimPayment` (89), `ClaimScreen(state, saved, payment, actions, locale)` (107), `LinkCard` (248), `PaymentCard` (293).
- `ui/claim/ClaimViewModel.kt` (447): class at line 81, constructor takes `BillOnScreen` (host, 82) and `ShareRepository` (83).
- `ui/claim/BillOnScreen.kt` (33): interface the split VM implements.
- Opened from Result via `onPickTogether` (AppNavHost 235). Lifecycle: `resume()`/`pause()` at 251-254.

### 1.13 Settings
- `ui/settings/SettingsScreen.kt` (420), `SettingsViewModel.kt` (149). Details in section 5.

### 1.14 Pro (paywall)
- `ui/paywall/ProScreen.kt` (384), `ProViewModel.kt` (343), `ProTexts.kt` (125). Details in section 5.

### 1.15 Common
- `ui/common/InputFields.kt` (107): `InputField` (27), `NumberField` (65).
- `ui/common/Links.kt` (80): `LegalLinks` data class (29), `LegalLinks.of(proxyUrl)` (32), `LegalLinksRow` (42), `PrivacyPolicyLink` (47), `openLink` (75).
- `ui/common/ProxyErrors.kt` (35): `ProxyWork` (11), `ProxyErrors` object (14).
- `ui/common/ShareEffect.kt` (37): `ShareEffect(share, onShared)` (17).
- `ui/AppLink.kt` (69): sealed `AppLink` for deep links.

## 2. The result screen in detail (`ui/split/ResultContent.kt` unless noted)

Layout is a single `LazyColumn` inside `Scaffold` (186, with `Modifier.imePadding()` 158). The rows in order:

1. Total card `TotalCard` (187-195; composable 508-539): "Bill total" label (`split_bill_total`, 516), amount (517), date, summary text from `SavedBill.summary` (519-522), and for foreign currency the converted amount and rate with "Add rate" / "Change rate" (523-536).
2. Notices (`plan.openNotices()` and `bill.warnings`, 199-205) and the difference message with "Edit bill" action (206-216).
3. Assistant card `AssistantCard` (218-224, `AssistantContent.kt:87`), applied-change card with undo (225-235), proposal card `ProposalCard` (236-244, `AssistantContent.kt:178`).
4. **People chips** (246-285): `SvSectionHeader` "Who is on the bill?" (`split_who_is_on_the_bill`, 248-251). Chip for "You" that toggles sharing (`onToggleMe`, 258-265). Chip per non-me participant (266-277) that opens `PersonSheet`. `AssistChip` "Add someone" (278-282). Source of the chips: `plan.participants` (SplitPlan), names via `result.nameOf(id)` (`SplitResultState.nameOf`, SplitBillViewModel.kt:125).
5. **Split-mode choice** (287-297): `SvChoiceChips` with `MODES` list (793-799: Equally, By item, By shares, By percent, By amount). Selection calls `actions.onSetMode` -> `SplitBillViewModel.setMode` (SplitBillViewModel.kt:494).
6. **Items list** (299-317): header with hint depending on mode (301-305). Each `ItemRow` (553-592) is tappable only in "By item" mode (313) and opens `ItemSplitSheet` (443-460). The sharers line is `describe()` (760-784).
7. **Charges** (319-331): header (321-327); `ChargeRow` (595-601) for each charge plus the computed difference line `differenceCharge()` (787-791).
8. **"Each person pays"** (333-342): header `split_each_person_pays` (333), then one `ShareRow` per `plan.participants` (608-656), showing amount, items and charges breakdown, converted amount, and paid amount. In share, percent or amount modes it has an inline `DivisionValueInput` (645-651). Then the "given" total for those modes (343-355, `DivisionTotalRow`) and "Adds up to the bill total" `SharesTotalRow` (356-358, 660-675). Problems are error cards (360-366).
9. **"Paid by"** (368-371): header `split_paid_by` (368) and `PaidRow` (678-700), tap opens `PaymentsSheet` (462-474). Text from `split_choose_who_paid`, `split_one_paid`, `split_person_part`.
10. **"Sent to Claude" section** (373-393, to be removed): a `TextButton` toggling `showOcrText` with strings `split_show_sent` / `split_hide_sent` (377-379), then the monospace `result.ocrDocument` text (381-388). The state variable is `showOcrText` (140). The data is `SplitResultState.ocrDocument` (SplitBillViewModel.kt:115) from `SavedBill.ocrDocument` (`data/bills/SavedBill.kt:52`), written at SplitBillViewModel.kt:391.

Sheets are at 401-504 (AddPersonSheet, PersonSheet, ItemSplitSheet, PaymentsSheet, AddToGroupSheet, ExchangeRateSheet). Their composables live in `SplitSheets.kt`: `AddPersonSheet` 64, `PersonSheet` 131, `ItemSplitSheet` 167, `PaymentsSheet` 266, `ExchangeRateSheet` 338.

Bottom bar `FinishBar` (703-756), in order of precedence:
- problems present: disabled "Finish" (726).
- bill is in a group: "Update in group <name>" with menu (729-734).
- user paid everything and others owe something: "Request <amount> from N people" (`split_request_from` plural, 736-746), which calls `onSendRequests`. Menu: add to group, let everyone pick, finish without asking (742). Carries `SEND_SURFACE` shared element (744).
- otherwise "Finish" with menu: add to group, pick together (748-753).

"Ask for the money" steps:
1. Result step, FinishBar request button -> `ResultActions.onSendRequests` -> `SplitScreen` line 106 -> `SplitBillViewModel.sendRequests()` (`ui/split/SplitBillViewModel.kt:527-537`). It builds `PersonRequest`s for each share except the user and amount > 0, then calls `finish()` (marks FINAL) and sets `sentRequests`. Step changes to `SplitStep.Sent` (SplitScreen.kt:67).
2. Sent step `RequestMoneyContent.kt:79` (`RequestMoneyContent(result, requests, viewModel, onOpenPaymentMethods, onDone)`):
   - Success mark with `SEND_SURFACE` (123), heading "Ask for the money" (`requests_ask_for_money`, 125), hint (128).
   - "Share the split as a picture" button (137-144) renders `SummaryCard` (205-233) through `Modifier.shareCard` and sends the image plus a text summary (`shareSummary`, 310-325).
   - Method part (147-154, `MethodPart` 236-262): "Paid back by <method>" with "Change" or "Add a way to get paid" (`MethodSheet` 291-308 when several methods).
   - Your-name field when signed in (155-169).
   - One `PersonRow` per requester (170-178, row 265-288): name, amount, converted amount, "Send" / "Again" button calling `share(person)` (98-111).
   - `share()` calls `RequestMoneyViewModel.prepare` (`ui/requests/RequestMoneyViewModel.kt:78-100`). A link is created only when signed in, a method is chosen and the name is not blank (line 84 `val link = if (signedIn && chosen != null && name.isNotBlank()) track(...)`). `track()` (104-128) calls `RequestRepository.create` (`data/requests/RequestRepository.kt:66`), then `withdrawReplaced` (101). Without a link the text and QR still go out. The QR image is made via `QrCodes.bitmap` and `ShareCardRenderer.save`.
   - Done button (113) calls `finish` (AppNavHost via `onFinish`/`onDone`).
3. Each send calls `viewModel.markShared` (109) so the button reads "Again".

## 3. Home: current data and what exists app-wide

### 3.1 What Home receives today
- Only `bills: List<BillListing>` (HomeScreen.kt:59), fed by `billRepository.listings()` (AppNavHost.kt:159 -> `data/bills/BillRepository.kt:27`). Home shows the first three (`RECENT_BILLS`, HomeScreen.kt:131, 178). No view model. No account, request or group data.
- `BillListing` (`data/bills/SplitVibeDatabase.kt:49-61`): id, status, updatedAt, merchant, date, currency, fractionDigits, totalMinor, people, category. It has no per-person share, so a user's share needs the saved document.
- Listing SQL (`SplitVibeDatabase.kt:70-74`): all bills, newest first, filtered by `searchText LIKE`.
- `finishedDocuments()` (`SplitVibeDatabase.kt:80-81`): documents of FINAL bills; exposed as `BillRepository.finished()` (`BillRepository.kt:35`), returns `List<SavedBill>`.

### 3.2 (a) Sum of open payment requests, per currency, owed and owing
- Source: `RequestRepository.requests: Flow<List<RequestView>?>` (`data/requests/RequestRepository.kt:54`), from `cache.watch(REQUESTS)`. Refreshed by `refresh()` (56, `GET v1/requests`).
- Model: `RequestView` (`data/requests/RequestModels.kt:12-56`): `amountMinor`, `currency: CurrencyDto` (`.bill` at line 38 gives a `BillCurrency`), `role` ("creditor" = owed to the user, "debtor" = user owes; `isMine` line 37, constants 52-53), `status` (line 45-49). `RequestStatus` enum at 58-67: WAITING, REPORTED_PAID, PAID.
- "Open" = status != PAID, i.e. WAITING or REPORTED_PAID (the payer said they paid, but the requester has not confirmed; `RequestStatus.REPORTED_PAID` comment at line 62). Nothing computes this sum today. `RequestsScreen` only filters by `isMine` into two sections (RequestsScreen.kt:158, 165). Other uses of `isMine` and status are in `RequestRepository.kt:103` and `AddToGroupViewModel.kt:91`, `GroupViewModel.kt:104`.
- Signed out: `ProxyApi.signedIn` throws `ProxyException(401, SESSION_REFUSED)` when there is no session (`data/proxy/ProxyApi.kt:141-142`). Sign-in, sign-out and deletion all call `cache.clear()` (`data/account/AccountRepository.kt:73, 83, 173`), so the request cache is empty when signed out. The `watch` flow emits null (`ProxyCache.kt:32-36`). Result: no requests, so the sum is nothing/zero, and the UI must show "sign in" rather than zero.
- Offline and signed in: the cache returns the last answer (`ProxyCache.watch`, `ProxyCache.kt:32`). Refresh fails with an IOException (see SettingsViewModel.kt:63-64 for how the app treats offline). Works from cache.
- Per currency: group the list by `currency.code`. No conversion exists for requests.

### 3.3 (b) This month's spending and the user's share
- Domain: `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/Insights.kt`. `InsightBill` (line 8: day, category, group, currency, totalMinor, shares, `myShareMinor` line 19, rate). `Spending(bills, totalMinor, myShareMinor)` (34). `Insights` (61) with `overall: Spending` (63), `byMonth: Map<YearMonth, Spending>` (65), `byPerson` (67), `byGroup` (73), `byCategory` (75), `undated` (76), `withoutRate` (78). `Insights.of(bills, localCurrency)` (89).
- App side: `insightsOf(finished, localCurrency, period, clock)` (`ui/insights/InsightsViewModel.kt:206-237`) converts each bill with `insightBillOf` (250) and selects the period via `InsightsPeriod.THIS_MONTH` (`InsightsViewModel.kt:51-62`). `overall.totalMinor` and `overall.myShareMinor` for the month are the "this month" numbers; `byMonth[YearMonth.now]` also works.
- Flow: `bills.finished()` + `settings.localCurrency` (`InsightsViewModel.kt:109-116`, `flowOn(Default)`, `WhileSubscribed`). A new Home would need its own flow with the same inputs.
- Works signed out and offline: yes. Reads only the local database. Only FINAL bills count (SplitVibeDatabase.kt:80). Bills in other currencies count only if they have a rate; otherwise they are listed in `withoutRate` (Insights.kt:78).
- Clock: `LocalDate.now(clock)` with zone from the clock (`InsightsViewModel.kt:208`, `insightBillOf` takes `zone`).

### 3.4 (c) User's balance in a group
- Domain: `GroupState.balances: Map<String, Long>?` (`data/groups/GroupModels.kt:135-138`): positive = owed to the member, negative = owes. Computed by `Balances.of` (`core/domain/src/main/kotlin/com/dantech/splitvibe/domain/Balances.kt:16`) over `countedBills` (132) and settlements. Null when numbers do not add up (runCatching). The user's own id is `GroupState.myMemberId` (line 108), and `GroupState.currency` (115). `suggestedTransfers` (141).
- Source: `GroupRepository.group(id)` flow (`data/groups/GroupRepository.kt:32`) and `cached(id)` suspend (35). Refreshed by `refresh(id)` (44, `GET v1/groups/{id}`), which is called when a `GroupViewModel` starts (`ui/groups/GroupViewModel.kt:68`).
- Group list: `GroupSummary` (`GroupModels.kt:31-41`) has name, currency, status, `myMemberId`, but no balance. `refreshGroups()` (`GroupRepository.kt:37`) fetches only the summaries.
- Cache: a group's full state is kept only after it has been fetched or changed (`GroupRepository.state()` 183-187 `cache.put` with documents stripped). A group the user never opened on this phone has no cached state, so its balance is unknown offline and needs a network call per group. A 403/404 removes the cached state (`GroupRepository.kt:47-51`).
- Signed out: groups list is cleared (see 3.2); `GroupRepository.call` throws 401 (`ProxyApi.kt:142`); no balance.
- Working with multiple groups in a Home total: balances are per group in the group's currency, so totals must be grouped by currency. Nothing app-wide sums balances across groups today.

## 4. Bills list (History) and recurring bills

### 4.1 Bills list
- `HistoryScreen(listings, query, onQueryChanged, onOpen, onDelete, onBack, onRepeat, today)` at `ui/history/HistoryScreen.kt:63-72`. Top bar title "Bills" (`groups_bills`, 81). Back arrow (82-86).
- Sections: search `OutlinedTextField` with placeholder `history_search` (91-106, `typed` state kept at 76). Then one of: loading (`listings == null`, 108), empty state ("No bills yet" or "No bill matches ...", 110-120), or rows (122-132). Row composable `BillListingRow` at 175 (title, detail line, total, draft badge, overflow menu with "Open", "Delete", "Repeat this bill" if FINAL, lines 175-216).
- Sheets/dialogs: `RepeatBillSheet` for a repeat (137-151), and delete `AlertDialog` (153-167).
- VM `HistoryViewModel` (`ui/history/HistoryViewModel.kt:20-43`): `query` from `SavedStateHandle` (22), `listings` = `query.flatMapLatest { bills.listings(it) }` (25-27, `WhileSubscribed(5000)`), `onQueryChanged` (29, capped at 80 chars), `delete` (34).
- Search: `BillRepository.listings(query)` normalizes with `NameMatcher.normalize` (`BillRepository.kt:114`), SQL LIKE on `searchText` (`SplitVibeDatabase.kt:70-74`). Search text covers merchant, date, items and people.
- Nav: AppNavHost 277-298 (`HistoryKey`). `onRepeat` calls `recurringViewModel.repeat(...)`, asks for notices, opens RecurringKey (290-295).
- Tabs: this screen has no tabs. Merging with recurring needs a tab row and two state sets, and each tab has its own back, search (History only), and empty state.

### 4.2 Recurring bills
- `RecurringScreen(rows, actions)` at `ui/recurring/RecurringScreen.kt:65`. Top bar title "Recurring bills" (`recurring_title`, 70). Sections: loading (81), empty state `recurring_none` (83-89), intro text (92-99), rows `RecurringBillRow` (100-102, row composable 126-152: name, schedule description `describe()` 159, detail `detailOf()` 173, amount, overflow with pause/resume and delete). Delete `AlertDialog` (108-122).
- `RecurringActions` (51-56): onPause, onResume, onDelete, onBack.
- VM `RecurringViewModel` (`ui/recurring/RecurringViewModel.kt:24-70`): `rows` (32-37, `RecurringRow(bill, next, due)`), `repeat` (43-49, creates from a saved bill then `runDrafts()`), `pause` (51), `resume` (55), `delete` (63). Activity-scoped instance (AppNavHost:150).
- Data: `data/recurring/RecurringBill.kt` (110) and `RecurringBillRepository.kt` (121, `all` flow used at RecurringViewModel 32).
- Sheet: `RepeatBillSheet(name, date, today, onRepeat, onDismiss)` (`ui/recurring/RepeatBillSheet.kt:57`) with `suggestedSchedule` (150) and `firstDraftDay` (157).

### 4.3 Tabs merge: what must be shared
- Both lists are `LazyColumn`s with their own top bar. History has a search field and a `WhileSubscribed` flow. Recurring has an intro and no search. Each has a separate VM and separate Koin scope (History is entry-scoped, Recurring is activity-scoped; AppNavHost 150 and 280).
- Both use `SvListRow`/`SvRowPosition` and `SvOverflowMenu`.

## 5. Settings and the Pro screens

### 5.1 Settings (`ui/settings/SettingsScreen.kt`)
- Signature `SettingsScreen(settings, account, accountUi, canSignIn, actions, onSave, onOpenPaymentMethods, onBack, legalLinks, onAllowSpeechService, onWithdrawAiConsent)` at lines 89-103. Top bar "Settings" (119). Body `Column` scrolls (128-135).
- Sections in order:
  1. `AccountCard` (136; composable 191-281): "AI calls" title (197), plan row with "Get Pro"/"Manage Pro" (`PlanRow` 199, 327-342), calls-left text (200, `callsLeftText` 406), cost text (`CALL_COSTS` 419), sign-in status text (205-224), sign out, "Delete account" text button (253-261), working indicator (236-243), sign-in button (247-248, 263-265), message card (268).
  2. Local currency field with validation (138-150).
  3. Your name field (152-160).
  4. Speech service switch, only if the user already agreed (162; `SpeechServiceSwitch` 350-368).
  5. AI consent block, only if the user already agreed (164; `AiConsent` 375-386, withdraw button at 384, which calls `onWithdrawAiConsent` = `aiConsentWithdrawal()` 390-393 -> `SettingsStore.allowAi(false)`).
  6. "Ways to get paid" row (166-181), to `PaymentMethodsKey`.
  7. Save button (183).
  8. Legal links row `LegalLinksRow` (185; `ui/common/Links.kt:42`; strings `legal_privacy_policy`, `legal_terms_of_use`, `legal_cannot_open` at strings.xml 281-283).
- Delete-account dialog: `DeleteAccountDialog(onDelete(withPhoneData), onDismiss)` at lines 288-320 (title `settings_delete_title`, text `settings_delete_text`, checkbox `settings_delete_with_phone_data`, confirm `common_delete` in error colour). Opened from the AccountCard (192, 255-258, dialog trigger 272-280).
- AI consent wording lives in `ui/split/AiConsent.kt` (`AiConsentDialog` 35, `AiConsentScreen` 50), shared by the split screen (SplitScreen.kt:184-201). Strings `ai_consent_*` (strings.xml 115-121) and `settings_ai_consent*` (109-112).
- VM `SettingsViewModel` (`ui/settings/SettingsViewModel.kt`): `AccountUiState` (26), class at 29, `ui` (32), `canSignIn` (34), `signIn` (71), `signOut` (73), `deleteAccount(withPhoneData)` (79-84), `act()` (87), `said()` (104), `textOf` (116), `messageOf` (130).
- Settings state object `AppSettings` and `SettingsStore` (`data/settings/SettingsStore.kt`): `speechServiceAllowed` (22), `aiAllowed` (28), `allowAi` (83), `allowSpeechService` (74), `MAX_NAME`.
- Settings' own "Save" writes `localCurrency` and `myName` (AppNavHost 358-362).

### 5.2 Pro (`ui/paywall/`)
- `ProScreen(state, actions, legalLinks)` at `ProScreen.kt:89`. Top bar "Split Vibe Pro" (`pro_title`, 102). Body (`ProScreen.kt:118-129`): one of "not sold" message (121), member card when subscribed (`MemberCard` 155, with billing-issue message 179-181), or benefit card (`BenefitCard` 136). Then calls-left text (124), then, when sold, `Offer` (197, plan rows `PlanRow` 257, switch card `SwitchCard` 280) and `Terms` (303-329: renewal terms, manage button, restore, legal links). Bottom bar `BottomPart` (331). Cards use `HeroCard` (189).
- `ProActions` (69): onSignIn, onSelect, onBuy, onSwitch, onRestore, onRetry, onDismissMessage, onBack.
- `ProViewModel.kt`: `ProMessage` (48), `ProUiState` (51-75, fields `sold`, `signedIn`, `signInExpired`, `callsLeft`, `loading`, `loadError`, `plans`, `selected`, Pro membership and `working`), class at 107. Billing via RevenueCat/Google Play (`billing/`).
- `ProTexts.kt` (125): strings helpers. Not read in full.

## 6. Bill editor (`ui/editor/BillEditorContent.kt`)

- `BillEditorContent(bill, onEdit, onDone)` at 69. `onEdit: ((Bill) -> Bill) -> Unit` is the only mutation path. `SplitBillViewModel.editBill(edit)` (`SplitBillViewModel.kt:451-461`) applies it and re-plans (`plan.withBill`), saves a draft, and reloads the day's FX rate when the currency changes.
- Structure, in `LazyColumn` (line 86 onward), inside `Scaffold` with top bar "Edit bill" (75) and bottom "Done" (83):
  - "bill" block (86-130): merchant text (`BillEditor.withMerchant`, 93), date text (102-105, only if applicable), currency field (111-117).
  - "category" (131-141): `SvChoiceChips` for `BillCategory` with `editor_category` header.
  - "items header" (143), then `ItemEditor` per item (144-146, composable 193-248): name field (206), remove icon (210), quantity (220), price each (227-233), amount (236-239). Every item is always an open editor. There is no collapsed or single-open-row state today.
  - "add item" button (147-156; `BillEditor.withItemAdded`).
  - "charges header" (158-164), then `ChargeEditor` per charge (165-167, composable 250-291: label field 262, amount 270-274, remove 280). The default label depends on `kind` (266).
  - "add charge" (168-181): assist chips for TAX, SERVICE_CHARGE, TIP, DISCOUNT, ROUNDING, OTHER (`ADDABLE_CHARGES`, `addLabelOf` 358).
  - "total header" (183), `TotalEditor` (184-186, composable 295-339): printed total (299-307), difference note and two actions "Use the lines as the total" / "Keep the total and add the difference as a line" (318-334).
- Domain editing: `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/BillEditor.kt:12` (`object BillEditor`), functions `withItemName`, `withItemQuantity`, `withItemUnitPrice`, `withItemAmount`, `withoutItem`, `withCharge`, `withChargeAdded`, `withoutCharge`, `withPrintedTotal`, `withLinesAsTotal`, `withDifferenceAsCharge` (names as used in the screen).
- Model: `Bill` (`core/domain/.../domain/Bill.kt:45`), `BillItem` (21: id, name, quantity, unitPriceMinor, amountMinor), `BillCharge` (34: id, label, kind, amountMinor), `ChargeKind` (13: TAX, SERVICE_CHARGE, TIP, DISCOUNT, ROUNDING, OTHER).
- Quantities: `positiveQuantity` (344).

## 7. Working and progress statuses while a bill is read

- Enum `Progress { IDLE, LOADING_PHOTO, READING_TEXT, ASKING_CLAUDE }` at `ui/split/SplitBillViewModel.kt:76`. It lives in `SplitUiState.progress` (136) and `isBusy` is `progress != IDLE` (155).
- Set by `launchWork(progress)` (802-824): LOADING_PHOTO for photo picks and sample (313, 320); READING_TEXT for on-device OCR (368); ASKING_CLAUDE after OCR, before `analyzer.analyze` (375). Reset to IDLE by `cancelWork` (428) and when the latest work finishes (824).
- Strings (values/strings.xml): `split_opening_photo` = "Opening the photo…" (308); `split_reading_on_phone` = "Reading the bill on this phone…" (309); `split_asking_claude` = "Claude is working out the bill…" (310). Mapping in `NewSplitContent.kt:275-279` (`IDLE -> ""`). Shown in `AnalyzeBar` (271-290) through `SvBottomBar` with a cancel button (`common_cancel`).
- Other work-related strings: `split_no_text_found` (VM 371), `split_bill_gone` (VM 289), `split_read_and_split` (311), `split_default_instruction` = "Split this bill" (325, the instruction field's default), `split_scanner_failed` (254), `split_no_camera` (261).
- Note: "Claude" appears in user-facing strings (`split_asking_claude`, `ai_consent_where`). A redesign that removes the "sent to Claude" section does not remove this progress text unless it is changed too.
- Bill status (separate enum): `BillStatus` in `data/bills/SavedBill.kt:16` with `DRAFT` (line 19) and FINAL. FINAL is set by `finish()` (`SplitBillViewModel.kt:581-587`). Status drives history "Draft" label (`history_draft`, strings 900) and the Repeat action (HistoryScreen 129).

## 8. Shared picture of a split and PDF summary: colours and type

- Design-system colours: `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvColors.kt` defines `LightColors` (line 12) and `DarkColors` (58) as Material 3 `lightColorScheme` / `darkColorScheme` with hard-coded purple palette (primary `#554ACC`, primaryContainer `#E7DEFF`). Theme: `core/designsystem/.../theme/SplitVibeTheme.kt:23` (`SplitVibeTheme(darkTheme, content)`), uses `typography = SvTypography`. No dynamic colour in that function.
- Typography: `core/designsystem/.../tokens/SvTypography.kt:7` is `Typography()` (Material 3 defaults), with `amountLarge` (headlineMediumEmphasized), `amountMedium` (titleMediumEmphasized), `amountSmall` (bodyLarge), all with tabular digits (lines 13-21). Elevations in `tokens/SvElevation.kt` (Level1-3).
- Shared picture: `RequestMoneyContent.kt:205-233` `SummaryCard` is a normal `SvCard` with the app's components (`SvAvatar`, `amountLarge`, `amountMedium`), and `Modifier.shareCard(state)` (`app/src/main/java/com/dantech/splitvibe/share/ShareCardRenderer.kt:33`) records it with a `GraphicsLayer`. `rememberShareCardState` (27) and `ShareCardRenderer.save/shareIntent` (49, 60) write a PNG under the `shared` folder. So the picture takes its colours and type from the Material theme (above). A redesign of the card changes the picture.
- Also the QR code image via `share/QrCodes.kt` (29 lines; `QUIET_ZONE_MODULES = 4`, line 28).
- PDF summary: `app/src/main/java/com/dantech/splitvibe/export/PdfSummary.kt` (360 lines), class at line 32. It does NOT use the design system. Colours: none set; text is black with alpha (`QUIET_ALPHA = 160` line 338, `RULE_ALPHA = 96` line 339). Typefaces: `Typeface.DEFAULT` and `Typeface.DEFAULT_BOLD` (lines 35-39). Sizes: title 20, heading 13, body 9.5, small 8.5 (lines 323-326). Page A4 595x842 (315-316), margin 48 (319). Strings come from `Resources` (constructor at 32). Called from `Exporter.kt:57`. Exports for Insights (`ExportContent` built by `contentOf` in `InsightsViewModel.kt:193`).

## 9. Strings

- File: `app/src/main/res/values/strings.xml` (1010 lines). 726 `<string>` elements, 45 `<plurals>`, no `translatable="false"`. Only one locale folder exists: `app/src/main/res/values/` with `strings.xml`, `colors.xml`, `themes.xml`. No translations (no `values-xx`).
- Naming scheme: `<area>_<element>` in snake_case, one prefix per screen or component. Examples: `split_*` (112 strings), `requests_*`, `groups_*`, `settings_*`, `pro_*`, `editor_*`, `history_*`, `recurring_*`, `insights_*`, `home_*`, `payments_*`, `claim_*`, `ai_consent_*`, `sign_in_*`, `legal_*`, `common_*` (shared: common_back, common_done, common_delete, common_cancel, common_ok, common_you, common_bill, common_the_bill...), `content_*` (default words used in messages, e.g. `content_payer`). Plural keys are used for counts (e.g. `split_request_from` 377-380, `calls_left_*`, `call_costs`, `pro_price_*`, `insights_bill_count`).
- Strings behind the sections the redesign renames or removes:
  - Home: `home_intro_title` (887), `home_intro_text` (888), `home_split_a_bill` (882), `home_insights` (883), `home_recent_bills` (884), `home_see_all` (885), `home_no_bills` (886), `groups_title`, `requests_title` (710), `recurring_title` (891), `app_name` (3).
  - Result, people chips: `split_who_is_on_the_bill` (331), `split_who_hint` (332), `split_add_someone` (336), `split_you_share` (333), `common_you`.
  - Result, mode: `split_how_is_it_split` (337), `split_mode_equal` (392), `split_mode_by_item` (393), `split_mode_by_shares` (394), `split_mode_by_percent` (395), `split_mode_by_amount` (396).
  - Result, items/charges: `split_items` (338), `split_items_hint_by_item` (339), `split_items_hint` (340), `split_charges` (341), `split_charges_hint_by_item` (342), `split_charges_hint` (343).
  - "Each person pays": `split_each_person_pays` (344), `split_pays`, `split_items_and_charges`, `split_adds_up` (366), `split_about` (351), `split_about_in_sentence` (364), `split_paid_in_sentence`.
  - "Paid by": `split_paid_by` (345), `split_choose_who_paid` (367), `split_one_paid` (368), `split_person_part` (369), `split_change_who_paid` (370).
  - "Sent to Claude": `split_show_sent` (347), `split_hide_sent` (346).
  - Split totals: `split_bill_total` (350), `split_difference` (389).
  - Requests screen: `requests_title` (710), `requests_to_pay` (714), `requests_you_asked_for` (716), `requests_need_sign_in`, `requests_empty` (713), `requests_ask_for_money` (480), `requests_ask_hint` (481), `requests_share_picture` (482), `requests_your_name` (483), `requests_add_method` (488), `requests_paid_back_by` (489), `requests_send` (493), `requests_send_again` (492), `requests_split_with` (486).
  - Editor: `split_edit_bill` (used as top bar title and the difference action), `editor_where` (516), `editor_date` (518), `editor_currency` (521), `editor_category` (522), `editor_add_item` (524), `editor_charges_hint` (525), `editor_total` (526), `editor_this_item` (527), `editor_remove` (528), `editor_quantity` (529), `editor_price_each` (530), `editor_amount` (531), `editor_printed_total` (532).
  - Bill new split: `split_new_title` (286), `split_add_the_bill` (298), `split_read_and_split` (311), `split_default_instruction` (325), `split_add_the_bill_hint`.
  - Settings: `settings_title`, `settings_ai_calls` (92), `settings_local_currency*` (85-87), `settings_your_name*` (88-89), `settings_payment_methods*` (90-91), `settings_delete_*` (100-102, 274-278), `settings_ai_consent*` (109-112), `settings_speech_service*` (106-107), `settings_pro_plan`, `settings_free_plan`, `settings_manage_pro` (103-105).
  - Pro: `pro_*` (70 strings, lines 143-263, plus plurals).
  - Legal: `legal_privacy_policy` (281), `legal_terms_of_use` (282), `legal_cannot_open` (283).
- Strings with "Claude" or "Anthropic" in user text: `split_asking_claude` (310), `split_show_sent`, `ai_consent_where` (116), `settings_ai_consent_given` (110).
- Pseudolocales: `app/build.gradle.kts` lines 136-138 set `isPseudoLocalesEnabled = true` in the `debug` build type only; comment says en-XA (longer, accented) and ar-XB (right to left). Release leaves them out (line 140-141 `release` block has no such flag). Docs: `docs/architecture.md:100-102` and `:518`, `:587`. Test `app/src/test/java/com/dantech/splitvibe/ui/PseudoLocaleGalleryTest.kt` draws screens in en-XA (per `plans/reports/fullstack-developer-261119-0147-app-release.md`).

## 10. Risks and unknowns

Risks (verify before changing code):
1. Shared-element keys: `SPLIT_SURFACE` (AppNavHost.kt:136, used at HomeScreen.kt:88 and SplitScreen.kt:134) and `SEND_SURFACE` (SplitScreen.kt:31, used at ResultContent.kt:744 and RequestMoneyContent.kt:123). A new bottom-bar "Split a bill" button must carry `SPLIT_SURFACE` or the grow animation breaks.
2. Bottom bar conflicts: Result (FinishBar), Sent (Done), Editor (Done), Input (AnalyzeBar), Pro (BottomPart) already own `bottomBar`. A global bottom nav must not appear over these steps, or they need restructuring.
3. `ocrDocument` is more than the "sent" section. It is the storage of the read text and is used as the "bill was read" flag: `SplitBillViewModel.kt:606` and `:791` (`billWasRead = saved.ocrDocument.isNotEmpty()`). Removing the UI section must keep the field and its writes (`SplitBillViewModel.kt:391`, `GatewayBillAnalyzer.kt:46` which sends it to the proxy). Removing the UI does not change what is sent.
4. Home total needs signed-in state. Requests are cleared on sign-in, sign-out and deletion (`AccountRepository.kt:73, 83, 173`). Show "sign in" rather than zero when signed out.
5. Group balances need one full `GroupState` per group. The list (`GroupSummary`) has no balance. Unopened groups have no cached balance, and refreshing all groups makes one call per group. Offline, unopened groups show nothing. Balances are per group currency; a total across groups must group by currency. No FX is applied to requests or group balances.
6. Monthly spending is FINAL bills only, with FX from saved rates and a day-rate fallback. A bill in another currency without a rate is excluded from totals and counted in `withoutRate` (Insights.kt:78). A Home tile must say so.
7. Editor: today every item is an open editor (BillEditorContent.kt:144-146). "One row open at a time" needs new UI state (the editor has none; `SplitBillViewModel.editBill` is the only write path). Local `rememberSaveable` state is enough if the open row is UI-only; check `ItemEditor` focus behaviour.
8. Result people chips and "Each person pays" use different sources: chips come from `plan.participants` (ResultContent.kt:266) and the share rows from `plan.participants` with `result.split.shares` (ShareRow 608-656). Merging them must keep inline `DivisionValueInput` for share, percent and amount modes (645-651) and the "given" total (343-355) and `SharesTotalRow` (356-358).
9. History and Recurring are separate Navigation 3 entries (AppNavHost 277, 322) with separate VMs (History entry-scoped, Recurring activity-scoped at AppNavHost 150). Merging into one tabbed screen changes the lifecycle of `RecurringViewModel` (it is activity-scoped so repeat survives leaving the screen, comment at AppNavHost 149). Keep that.
10. Requests "two summary tiles": they need the same open-sum logic as Home (section 3.2). RequestsScreen currently has no totals.
11. `GroupsScreen` header comment says balances are shown; the row code does not. Confirm the intended behaviour before the plan.
12. Strings: 726 elements, no translations. Removing or renaming strings in the redesign affects the pseudolocale gallery test and Roborazzi screenshot baselines (see plans/reports fullstack-developer-261119-0147-app-release.md). Not verified in this scout.

Unknowns (not read or not verified):
- `ProTexts.kt`, `ClaimScreen.kt` internals, `JoinGroupScreen.kt` internals, `RequestLinkScreen.kt` internals, `AddToGroupSheet.kt` internals, `PaymentMethodSheet.kt` internals, `InsightsScreen.kt` row internals, `ExportSheet.kt` internals: only signatures were read.
- `SplitAssistantViewModel.kt` and `AssistantContent.kt`: only signatures and lines 63-87 region read.
- Whether `ProxyApi` throws `IOException` offline: inferred from `SettingsViewModel.kt:63-64` and `messageOf` (146), not read in `ProxyApi.kt`.
- `RequestMoneyViewModel.track()` failure path (lines 128-135) not read in full.
- `AccountRepository`/`AccountStore` details beyond the `cache.clear()` calls.
- Koin wiring in `di/AppModule.kt` (for InsightsViewModel, ClaimViewModel, RecurringViewModel scopes) not read.
- The exact `SvLargeTopBar`, `SvSplitButton`, `SvBottomBar` component code in `core/designsystem` not read.
- Whether ShareCardRenderer's picture includes the QR code (only `SummaryCard` was read).
- Existence and content of Roborazzi screenshot tests for each screen (only the PseudoLocaleGalleryTest name and its docs were seen).

## Relevant Files (summary)
- `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt` - navigation keys and entries (88-538)
- `app/src/main/java/com/dantech/splitvibe/ui/home/HomeScreen.kt` - Home (57-142)
- `app/src/main/java/com/dantech/splitvibe/ui/split/SplitScreen.kt` - split steps and surfaces (39-235)
- `app/src/main/java/com/dantech/splitvibe/ui/split/ResultContent.kt` - result screen (128-505, rows 187-393, FinishBar 703-756)
- `app/src/main/java/com/dantech/splitvibe/ui/split/RequestMoneyContent.kt` - "Ask for the money" (79-201, SummaryCard 205)
- `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt` - state, progress, sendRequests (76-156, 347-399, 527-537, 581-587, 802-824)
- `app/src/main/java/com/dantech/splitvibe/ui/requests/RequestMoneyViewModel.kt` - prepare and track (78-128)
- `app/src/main/java/com/dantech/splitvibe/ui/editor/BillEditorContent.kt` - editor (69-291)
- `app/src/main/java/com/dantech/splitvibe/ui/history/HistoryScreen.kt`, `HistoryViewModel.kt` - bills list
- `app/src/main/java/com/dantech/splitvibe/ui/recurring/RecurringScreen.kt`, `RecurringViewModel.kt` - recurring
- `app/src/main/java/com/dantech/splitvibe/ui/insights/InsightsScreen.kt`, `InsightsViewModel.kt` - insights and monthly totals (206-237)
- `app/src/main/java/com/dantech/splitvibe/ui/requests/RequestsScreen.kt`, `RequestsViewModel.kt` - requests (96-103, 158-165)
- `app/src/main/java/com/dantech/splitvibe/data/requests/RequestRepository.kt`, `RequestModels.kt` - requests data and status (RequestModels 12-67)
- `app/src/main/java/com/dantech/splitvibe/data/groups/GroupRepository.kt`, `GroupModels.kt` - group cache and balances (GroupModels 106-141)
- `app/src/main/java/com/dantech/splitvibe/data/proxy/ProxyCache.kt` - cache, clear on account change (22-52)
- `app/src/main/java/com/dantech/splitvibe/data/account/AccountRepository.kt` - cache.clear at 73, 83, 173
- `app/src/main/java/com/dantech/splitvibe/data/bills/SplitVibeDatabase.kt` - BillListing, finished documents (49-81)
- `app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt` - settings (89-420)
- `app/src/main/java/com/dantech/splitvibe/ui/paywall/ProScreen.kt`, `ProViewModel.kt` - Pro (89-384, 51-107)
- `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/Insights.kt`, `Balances.kt`, `BillEditor.kt` - domain
- `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvColors.kt`, `SvTypography.kt` - theme tokens
- `app/src/main/java/com/dantech/splitvibe/share/ShareCardRenderer.kt` - shared picture (21-76)
- `app/src/main/java/com/dantech/splitvibe/export/PdfSummary.kt` - PDF summary (32-40, 315-339)
- `app/src/main/res/values/strings.xml` - 726 strings, 45 plurals
- `app/build.gradle.kts` - pseudolocales in debug (136-138)

## Patterns
- Each screen is `XxxScreen(stateValues..., actions)` with an `XxxActions` class or loose lambdas, plus an `XxxViewModel` that exposes `StateFlow`s collected with `collectAsStateWithLifecycle` in AppNavHost. Screens are stateless apart from `rememberSaveable` dialog/sheet flags.
- Lists use `LazyColumn` with `SvListRow` and `SvRowPosition.of(index, size)` for rounded group styling, `rowMotion()` for animation, and `SvSectionHeader` for section titles.
- Sheets use `SvBottomSheet`; dialogs use `AlertDialog`; primary actions use `SvPrimaryButton`/`SvSplitButton`.
- State held in `SavedStateHandle` survives process death (SplitBillViewModel, HistoryViewModel, InsightsViewModel).
- Proxy-backed data is cached on the phone through `ProxyCache` and cleared on any account change; local bill data comes from Room (`SplitVibeDatabase`).

## Unresolved Questions
- Should Home sum include REPORTED_PAID requests (payer said paid, not yet confirmed)? The plan must choose; the code treats both as open (status != PAID).
- Should balances for groups never opened be fetched for the Home tile, or should the tile show only cached groups?
- Is the "balances" line in the Groups list header a regression or an intended change (GroupsScreen.kt:52 vs 117-130)?
- Does the redesign keep the Split flow's FinishBar request button as the only route to "Ask for the money"?
