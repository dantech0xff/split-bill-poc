# Phase 5 review: Android app and Kotlin domain (groups, balances, payment requests, push, App Links)

- Date: 2026-11-15 (UTC)
- Branch: `feat/phase-05-groups-balances-settle-up`, range `0acc20d..080127e`. HEAD moved from `0ae49b5` to `080127e` while I was reviewing. I also read the four new commits (`447a5b1`, `d93d057`, `d4ab8c7`, `080127e`); only `Notices.kt`, `AppLink.kt`, `RequestsViewModel.kt` and three tests changed in scope.
- Paths reviewed: `core/domain/**`, `app/**`, `core/designsystem/**`, `gradle/libs.versions.toml`. I read `proxy/**` only to confirm the contract the app relies on. `gradle/verification-metadata.xml` was not reviewed.
- Method: read-only. Gradle was not run, as instructed, so tests were read but not executed. I rechecked the QR test vectors with my own CRC script and compared them with the upstream sources. I also redid the hand-worked balance table myself.

## Scope

- Files: 116 changed (+7,417 / −148). Main code is +5,272 / −134, tests +1,880 / −7, plus 33 screenshots and the Room schema `2.json`.
- Focus: money routing and trust boundaries first, then money arithmetic, then Android lifecycle, then tests.
- Edge cases I looked for: a payment method edited after a request was sent; a server-supplied method that passes `problem()` but cannot be encoded; answers lost after a write was committed; the same money recorded through two paths; group data growing large; switching accounts while a call is in flight; links opened before signing in; screen rotation in the middle of a flow; ids the proxy never lets come back after deletion; intent extras forged by other apps.

## Overall assessment

The domain core is sound. The balance math, the largest-remainder conversion, the debt simplifier, the EMV/EPC encoders and the PastedLink allowlist are correct and well tested. I confirmed the vectors against promptpay-qr and vietnam-qr-pay, and the hand-worked table against my own arithmetic.

The defects are at the seams:
- ids that are reused when they should change, or regenerated when they should be reused;
- two flows that can ask for, or record, the same money twice;
- a cache design that stops working at scale.

There is one critical finding: a payer can be routed to an account the requester already corrected. With five high findings, I would not merge this as it stands.

## Critical

### C1. After a payment method is edited, sending the request again keeps the old account in the request the payer opens

- `app/src/main/java/com/dantech/splitvibe/ui/requests/RequestMoneyViewModel.kt:103-105`:
  ```kotlin
  val key = "${saved.id}/${person.participant.id}/${person.amountMinor}/${chosen.id}"
  id = UUID.nameUUIDFromBytes(key.toByteArray()).toString(),
  ```
- `app/src/main/java/com/dantech/splitvibe/data/payments/PaymentMethodRepository.kt:39-43`: `replace()` keeps the row id, so `chosen.id` does not change when the account does.
- `app/src/main/java/com/dantech/splitvibe/data/requests/RequestRepository.kt:55`: `create()` returns the cached request and its link without calling the proxy. Even without the cache, the proxy answers an existing id with the stored row and ignores the new `method` (`proxy/src/requests.ts:169-170`).
- `app/src/main/java/com/dantech/splitvibe/ui/requests/RequestsViewModel.kt:60-71`: "Send a reminder" rebuilds the details and QR from the stored, old snapshot.

**Scenario:**
1. Dana sends Minh a request with a VietQR account that has a typo.
2. On the request step she notices it, goes to "Ways to get paid", corrects the account (same method id), comes back and taps "Again".
3. The id is the same, so `create()` returns the old request and the old link.
4. The chat message shows the corrected account and QR, because they are built locally from the new method (`RequestMoneyViewModel.kt:79`). The link in the same message opens a request whose pay panel builds the QR and details from the old snapshot (`RequestsScreen.kt:260-262`).
5. Minh follows "Open it in Split Vibe…" and scans the in-app code, so he pays the wrong account. A later reminder repeats the old account.

For PayPal.Me, Revolut/Wise links and plain details (such as a Zelle email) there is no name check by the bank to catch this.

**Fix:**
- Make the id cover what the payer pays to: bill, participant, amount, currency, and a SHA-256 of `Json.encodeToString(PaymentMethod.serializer(), method)`. The same content still gives the same id; a changed account gives a new request.
- When a new request replaces an open one for the same bill and participant, withdraw the old one (`DELETE /v1/requests/{old}`, with a local map from bill and participant to request id). Alternatively, treat an edit as a new method and offer to withdraw open requests that carry the old snapshot.
- Add a test: edit the method, send again, and assert a POST with the new method and the old request withdrawn. Today `RequestRepositoryTest.kt:78-80` asserts the very shortcut that causes this.

## High

### H1. A PromptPay id typed with non-ASCII digits passes validation, then crashes the QR builder, including remotely on the payer's phone

- `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/payments/PaymentMethod.kt:68,73,77` use `Char::isDigit`. That accepts any Unicode decimal digit: Thai ๐-๙, full-width ０-９, Arabic-Indic. I checked that these are category `Nd`.
- `PaymentRequestBuilder.kt:134-145` passes those digits to `EmvQr.field`, which requires printable ASCII (`EmvQr.kt:17`).

**Scenario:** `PromptPay("๑๒๓๔๕๖๗๘๙๐๑๒๓")` (13 Thai digits) gets `problem() == null`, because the 13-digit branch only checks `!international`. Then `build()` throws `IllegalArgumentException`. An ASCII "0" followed by nine Thai digits gets through the 10-digit branch the same way.
- **Payer's phone:** the throw happens inside `remember {}` during composition (`RequestsScreen.kt:261-262`, used by the request link screen and the Pay sheet). The app crashes every time that request is opened. The proxy stores any JSON object as the method (`proxy/src/requests.ts:130-135`), so any signed-in account can make such a request and send the link.
- **Requester's phone:** the same build runs in `rememberCoroutineScope().launch` (`RequestMoneyContent.kt:90-91`) and crashes the app.
- **Group "Ask":** `GroupViewModel.askFor` creates the request on the proxy (line 97) before it builds (line 112). The payer is notified of a request whose Pay sheet crashes their app.

**Fix:**
- Test for ASCII digits only (`it in '0'..'9'`) in `digits` and in `problem()`.
- `PaymentRequestBuilder.build` should never throw for a method it receives: catch the `IllegalArgumentException` and return the "cannot be used" result.
- Add tests with Thai and full-width digits.
- Ask the proxy side to validate snapshots with the same rules.

### H2. "Got it" uses a new settlement id on every attempt, so a retry or double tap records the payment twice, permanently

- `app/src/main/java/com/dantech/splitvibe/data/groups/GroupRepository.kt:152-159`: `put("id", UUID.randomUUID().toString())` on every call.
- `GroupViewModel.kt:85`; `GroupScreen.kt:180-196, 254-259`: the buttons stay enabled during the call, and with the group on screen nothing shows that a call is running (`GroupScreen.kt:124-127`).
- The proxy is idempotent on the settlement id (`proxy/src/bills.ts:211-233`), but the app never sends the same id twice.

**Scenarios:**
- Dana confirms "I got it". The POST commits, but the answer is lost. She sees "There is no connection…", the transfer is still listed because the cache did not change, and she taps again. A second settlement is recorded under a new id.
- On a slow network, with nothing on screen, she taps "Got it" twice.

Settlements can never be edited or deleted. Lan now shows as owed the amount, and settle-up tells Dana to pay Lan back, so real money moves the wrong way.

**Fix:**
- Create the settlement id once per intent, when the dialog opens, and keep it with the dialog's saved state; pass it to `recordPaidBack`.
- Disable "Got it" and "Ask" while `status.working`.
- Add a test that a retry after an `IOException` resends the same id.

### H3. Group "Ask" makes a new request on every tap, and is never matched against "Got it": the same debt can be asked for, and recorded, twice

- `GroupViewModel.kt:91-126`: a random request id on each tap (line 100).
- `RequestsScreen.kt:142-151`: confirming a request.
- The proxy records a settlement for every confirmed group request (`proxy/src/requests.ts:335-355`).

**Scenario A:** Dana taps "Ask" for Lan's 1,262.50 THB. Nothing visible happens while the request is created, so she taps again. Lan gets two "New payment request" notices and two "Pay" rows for one debt; paying both is plausible.

**Scenario B:** Lan pays. Dana taps "Got it" in the group (first settlement). Lan then taps "I have paid", and Dana confirms in Requests: `confirmInGroup` records a second settlement, and Lan now shows as owed the amount.

**Fix:**
- Reuse the open group request to the same member from the cached list, or derive the id from group, both members, amount and the group's latest settlement id.
- Disable the button while the call runs.
- When "Got it" is tapped for a member with an open group request, offer to confirm that request instead. Alternatively, the proxy closes such requests when a manual settlement covers them.
- Warn before confirming a group request when that member no longer owes anything.

### H4. A bill that is in a group can also be requested person by person, and confirming those requests never reduces the group balance

- `app/src/main/java/com/dantech/splitvibe/ui/split/ResultContent.kt:637-664`: after adding a bill to a group, the main button is still "Request … from N people", next to "Update in …".
- `RequestMoneyViewModel.kt:100-113`: no `groupId` or `toMemberId` is sent, even when `saved.group` is set.
- The proxy confirms per-bill requests without touching the group (`proxy/src/requests.ts:310-320`).

**Scenario:** Dana adds the dinner to "Chiang Mai trip", then taps the main "Request … from 2 people". Minh pays his request and Dana confirms it. The group still shows Minh owing his share, settle-up tells him to pay Dana, and "Ask" asks him again. The same happens if she sends the requests first and adds the bill afterwards.

**Fix (needs a product decision):** One option: when `saved.group` is set, run the request step as group requests (`groupId`, `toMemberId = memberOf[participant.id]`, amount from the converted bill, in the group's currency). The other: disable per-person requests for group bills and explain why. In either case, when a bill with confirmed per-bill requests is added to a group, record those payments as settlements or warn.

### H5. A group's whole state is cached as one Room row: past about 2 MB the group can no longer be opened (crash), and every update is decoded on the main thread

- `GroupRepository.kt:32, 162-166`: the `group:{id}` row holds every bill's full document. `SplitVibeDatabase.kt:152-157` defines the table.
- `ProxyCache.kt:18`: no `flowOn` and no `catch` on the read.
- `GroupViewModel.kt:44`: `stateIn(viewModelScope)` collects on the main thread.
- The proxy returns every live bill's document (`proxy/src/group-state.ts:124,184`), up to `POLICY.maxBills = 1000`. The proxy developer's report lists this as open question 7.

**Scenario:**
- Android reads a database row through a 2 MB buffer (CursorWindow). A larger `document` throws `SQLiteBlobTooBigException` out of the Room Flow. Nothing catches it inside `stateIn` in `viewModelScope`, so the app crashes each time the group is opened, and each refresh writes the oversized row again.
- I estimate a 20-line receipt at about 5 KB in the cached shape, so roughly 400 bills reach the limit; long receipts reach it sooner.
- Well before that, every change to the cache decodes megabytes of JSON on the main thread, which janks the UI.

**Fix:**
- Do not cache full documents: keep total, shares, paid and title per bill, and load a document when it is needed. Or keep bills in their own rows or table.
- Add `.flowOn(Dispatchers.Default)` and `.catch { emit(null) }` to `ProxyCache.watch`.
- Add a test with a state larger than 2 MB.

## Medium

### M1. An answer that arrives after sign-out or sign-in writes the previous account's groups or requests into the cache

`GroupRepository.kt:34-38, 162-166`; `RequestRepository.kt:45-48, 70-71, 104-112`; `AccountRepository.kt:29-45`. `cache.clear()` runs once, but nothing stops a call already sent with the old session from writing afterwards. View models further back in navigation keep running while the user is in Settings, for example the Groups screen's first load.

**Scenario:** On a slow network (read timeout 30 s, call timeout 150 s), the Groups screen's `GET /v1/groups` is still pending. A signs out and B signs in. A's answer lands after B's `cache.clear()`, so B sees A's groups, or A's requests with payer names, amounts and A's payment method, until something refreshes; while offline, indefinitely.

**Fix:**
- Give the cache an account epoch that increments on every clear, or remember the session token a call used, and drop the write if it no longer matches. Another option is to cancel in-flight calls on sign-out (`client.dispatcher.cancelAll()`).
- Make `cache` and `devices` required constructor parameters (`AccountRepository.kt:20-22`).
- Add tests. The requirement is untested today: `AccountRepositoryTest.kt:37` builds the repository without a cache.

### M2. Invite, request-link and Groups screens opened before signing in never load after signing in

- `JoinGroupScreen.kt:74-86`: `load()` runs only from `init`, and returns when signed out.
- `RequestsViewModel.kt:111-113`: the request link view model only loads in `init`.
- `GroupsViewModel.kt:47-54`: same, and `GroupsScreen.kt:66-73` has no reload action.

**Scenario:** An invitee taps the invite link while signed out, sees "Sign in in Settings", signs in and goes back. The screen is blank: signed in, no invitation, nothing loading, no message, no way to retry. This is the first-run path for every new member. The request link and the Groups list behave the same way.

**Fix:** Watch the session in these view models (`account.map { it.session != null }.distinctUntilChanged().filter { it }.onEach { load() }`) and add a retry action.

### M3. A bill deleted from its group, or an answer lost before the next change, can leave the bill unable to reach the group again

- `AddToGroupViewModel.kt:101-112`: the expected revision comes only from the bill's own link to the group.
- `AddToGroupSheet.kt:49`: that link is saved only while the sheet is showing.
- `ProxyErrors.kt:19`: one generic message for every 409.
- Proxy rules (`proxy/src/bills.ts:139-142`): a deleted bill id is 409 forever, and a replay is accepted only with identical content.

**Scenario (a):** The group owner deletes Minh's bill. Minh edits it and taps "Update in …". He gets 409 forever with "Someone changed this at the same moment… try again". Retrying never works, and the bill can never be added again under its id.

**Scenario (b):** The PUT commits, but the answer is lost, or the sheet is closed before it arrives, so the link is never saved. The next day `fx.rate()` gives a new rate, so the content differs from the stored copy, and every attempt is 409 even though the bill is in the group.

**Fix:**
- On 409, refresh the group and reconcile. If the stored bill is deleted, clear `saved.group`, say so, and offer to add it again under a new id. If it is the user's bill, adopt its revision and ask before overwriting.
- Save the group link from the view model, not only from the sheet's `LaunchedEffect`.
- Give 409 its own message.

### M4. Rotating the phone while adding a bill to a group drops the chosen group, the manual member matches and the typed rate

`AddToGroupSheet.kt:46` (`LaunchedEffect(saved.id) { viewModel.start(saved) }` runs again after recreation) and `AddToGroupViewModel.kt:58-62` (`start()` resets the state).

**Scenario:** Dana corrects an automatic match ("An" had been matched to "Anh") and types her card's rate of 36, then rotates the phone. `start()` resets everything: name matching picks "Anh" again and the day's rate of 35.5 comes back. If she doesn't recheck, the bill charges the wrong member at the wrong rate.

**Fix:** Skip `start()` when the view model already holds this bill (keep a `startedFor = saved.id`), or start from the view model with `SavedStateHandle`.

### M5. Replaced per-person requests stay open, and a deleted request can never be tracked again

`RequestMoneyViewModel.kt:103-105`; `RequestRepository.kt:95-100`; the proxy answers 409 for a deleted request's id (`proxy/src/requests.ts:170`).

- **(a)** After the bill's amount changes, or the user picks another payment method, "Again" makes a new request, while the earlier one stays open for a payer who already accepted it: two "Pay" rows for one share.
- **(b)** The requester deletes a request and sends it again unchanged. The id is the same, the proxy answers 409, and the app says "could not be kept… You can still ask again later". It never can.

**Fix:** Keep track of the current request for each bill and participant, withdraw the previous open one when a new one replaces it, and add something to the id after a deletion (for example a counter per bill).

## Low

- **L1, notice extras from other apps are trusted:** `MainActivity` is exported, and `AppLink.of` (`AppLink.kt:34-38`) trusts `EXTRA_KIND` and `EXTRA_GROUP_ID` (and, since `d4ab8c7`, `type` and `groupId` whenever any `google.message_id` is present). The group id is not validated before it becomes part of a URL path, and OkHttp's `addPathSegments` (`ProxyApi.kt:126`) resolves `..`. Another app can therefore make the app issue a signed-in GET to another `/v1` route. The impact is only a broken screen (no actions appear), but ids should be checked as UUIDs, which is all the proxy accepts.
- **L2, shared images reuse file names:** every reminder and group "Ask" saves `pay-code` (`ShareEffect.kt:21`), and participant p1 of every bill saves `pay-p1` (`RequestMoneyContent.kt:94`). An app that reads the image late, or still holds read access to that file, gets a later request's code with a different amount. Use a unique name per share.
- **L3, sharing can crash:** an `IOException` from `ShareCardRenderer.save` (`ShareCardRenderer.kt:54`, for example a full disk) or a throw from `QrCodes.bitmap` escapes `LaunchedEffect` (`ShareEffect.kt:19-22`) and `rememberCoroutineScope` (`RequestMoneyContent.kt:90-99, 281-290`). Catch it and share text only.
- **L4, sign-out can hang:** `devices.unregister()` runs before the session is cleared (`AccountRepository.kt:41`, `DeviceRegistrar.kt:28-31`), with OkHttp's 150 s call timeout. Bound it (`withTimeoutOrNull`), or clear the session locally first and send the DELETE in the background with the saved token.
- **L5, a push token refreshed while the app is running is lost:** `PushService.onNewToken` launches into a scope that `onDestroy` cancels almost immediately (`PushService.kt:16-29`), because the service stops itself once the callback returns. A cold start re-registers (`SplitVibeApp.kt:34-40`), but a token that changes while the app is running is only registered at the next start; meanwhile the proxy drops the old token when FCM reports it gone. Register synchronously in `onNewToken` (blocking is allowed there) or from the application scope.
- **L6, the notification small icon is the adaptive launcher icon:** `Notices.kt:73` uses it, the only launcher icon is `mipmap-anydpi-v26/ic_launcher.xml`, and the manifest has no `com.google.firebase.messaging.default_notification_icon`, so notices the system shows use it too. It renders as a blob, and on Android 8.0 (minSdk is 26) adaptive small icons are reported to crash the system UI. Add a monochrome vector icon and the meta-data entry.
- **L7, the group bill document still carries model and instruction output:** `sharedCopyOf` (`AddToGroupViewModel.kt:146-154`) keeps `bill.warnings` (written by the model) and `plan.expectedHeadCount` (read from the instruction). Clear both.
- **L8, payment builder edge cases:**
  - The SEPA amount has no two-decimal cap (`PaymentRequestBuilder.kt:107`): a crafted EUR currency with three decimals gives `EUR12.345`, which EPC069-12 forbids.
  - `paymentHostOf` accepts `HTTPS://…` (`PaymentMethod.kt:190`), but Android matches link schemes case-sensitively, so "Open" silently does nothing (`RequestsScreen.kt:298`). Lower-case the scheme.
  - The allowlist accepts any path on any subdomain of paypal.com, wise.com, revolut.com, venmo.com and cash.app. An open redirect on one of those sites stays a phishing path, behind a dialog that shows only the trusted host.
- **L9, main-thread and duplicated work:** `QrCodes.bitmap` runs during composition (`RequestsScreen.kt:272`). The rate field (`AddToGroupSheet.kt:137`) re-implements rate parsing and accepts exponents and signs, instead of reusing `positiveRate` (`SplitSheets.kt:358-365`).
- **L10, lost update in the requests cache:** `RequestRepository.keep` (`:108-112`) reads the list, changes it and writes it back without a lock. Two quick "Send" taps can drop one request from the cached list until the next refresh.
- **L11, a navigation callback runs after rotation:** `GroupsViewModel.create` (`:56-63`) calls back into the screen after the network call. After a rotation that callback points at the old navigation stack, so the new group does not open.
- **L12, Firebase starts for everyone:** once configured, Firebase Messaging fetches a token and contacts Google on every app start, also for users who never sign in. If that matters for the store's privacy disclosure, turn off auto-init (`firebase_messaging_auto_init_enabled`) and enable it after sign-in.

## What looked correct

- **Balances, conversion and settle-up:**
  - I redid the hand-worked table in `GroupBalancesTest` by hand and it is right (A +3,543, B +150,628, C −126,250, D −27,921; transfers C→B 126,250, D→B 24,378, D→A 3,543), and so are the differences after editing a bill or a rate.
  - The largest-remainder split never gives a unit to a zero weight; overflow-checked arithmetic is used throughout; a bill whose amounts don't add up is left out whole.
  - The property test covers 2,000 random groups in six currencies (0 to 3 decimals) with random rates and payments back, the n−1 transfer limit, and zero balances after the transfers.
- **QR codes and links:**
  - CRC-16/CCITT-FALSE is correct; I recomputed all ten EMV vectors myself.
  - The PromptPay vectors match promptpay-qr's `index.test.js` exactly; the VietQR vectors match the vietnam-qr-pay README.
  - The SEPA code's line layout, trailing-line trimming and 331-byte cap follow EPC069-12.
  - Amounts are never rounded: when a network cannot carry the exact amount, the code carries none and the details tell the payer to type it.
  - UPI uses the payer-initiated `upi://pay` with name and note encoded; notes are filtered per network.
- **PastedLink:**
  - Only https on port 443, no user info, ASCII host, exact or subdomain match.
  - `java.net.URI` rejected every parser trick I tried (`\@`, `%2e`, `#@`, `;`, brackets, whitespace), and there is a look-alike-letter test.
  - The payer confirms the host before a pasted link opens (`RequestsScreen.kt:303-323`).
- **Server-supplied payment methods:** every place that turns one into a link or QR goes through `PaymentRequestBuilder.build`, which runs `problem()` first; an unknown kind reads as "no way to pay". H1 is the gap in this.
- **App Links:** the token pattern blocks `/`, `.` and `%`; scheme and host must match exactly; the link is taken only on first creation; the host comes from the build per build type. `d4ab8c7` routes notices shown by the system from the data Firebase passes in, and its test shows that plain extras open nothing.
- **Notices:** fixed texts only (now the same as the proxy's); unknown kinds show nothing; the permission is checked on Android 13 and later; the tap opens the app through an explicit, immutable intent; permission is asked once after sign-in; saying no breaks no flow. The proxy now names the app's `requests` channel, so the docs' claim holds.
- **Files shared with other apps:** only `cache/shared/` was added to the FileProvider paths; read access goes only to the app the user picks; files older than a day are deleted.
- **The group bill document:** OCR text, photos, instruction, summary, the user's own rate and person ids are removed, with a test.
- **A build without Firebase settings:** every entry point checks `Push.isConfigured`, and the build requires all four settings or none.
- **Koin, Room and design system:** the new view models are wired with the right parameters and keys, with no cycles. Room version 2 only adds tables, both schemas are committed, and the main-method updates are atomic. The `SvOverflowMenu` refactor is behaviour-preserving.
- **Code hygiene:** no `@Suppress`, no `!!`, and no logging in the new main code.

## Test gaps against the plan's success criteria

- Cache emptied on sign-in, sign-out and account deletion: no test (`AccountRepositoryTest.kt:37` has no cache).
- A server-supplied method that passes `problem()` but cannot be encoded (H1): none.
- A retry reusing its id, for settlements, group asks and per-person requests: none.
- A method edited and the request sent again (C1): none; the current test locks in the cause.
- "Từ chối quyền thông báo không làm hỏng luồng nào": no test of `Notices.show` with the permission denied.
- Adding to a group: no tests for 409, a deleted bill, a bill moved between groups, or rotation. No test with a large group state.
- Covered: 2,000 random groups; 4 members, 5 bills, 3 currencies (the two creators appear only in comments, which is fine because creators don't change balances); editing after payments back; the QR vectors; PastedLink rejections (`javascript:`, `intent:`, `http:`, other domains, look-alike letters); details always shown for Venmo, Cash App, Revolut, Wise and Zelle; App Links; `SplitFlowTest` now reaches the message a person receives (`447a5b1`); screenshots of the new screens.
- Step 8 (manual test matrix with real payment apps): there is no report in `plans/reports/`.

## Plan follow-ups (no plan files edited)

- Domain items (`Balances`, `GroupConversion`, `DebtSimplifier`; `EmvQr` and the builders): complete except H1.
- Payment methods profile and PastedLink check: complete except how edits behave (C1).
- Per-person requests through the share sheet: present; C1, H4 and M5 are open.
- Share card image: done (L2 and L3 remain).
- Report paid, confirm, append-only settlements, app side: needs H2 and H3.
- Push, app side: `d4ab8c7` fixed the channel and the taps from the background; L5 and L6 remain.
- Account deletion, app side: the cache is cleared (M1 race remains).
- Manual matrix: not started.

## Recommended actions, in order

1. C1: id from the method's content; withdraw replaced requests.
2. H1: ASCII-only digits; `build` never throws for a received method.
3. H2 and H3: reuse settlement and request ids on retry; disable buttons during calls; connect "Got it" with open group requests.
4. H4: decide, then route group bills through group requests or block per-person requests for them.
5. H5: smaller cached group state, decode off the main thread, catch read errors.
6. M1 to M5, then the low items.

## Metrics

- Type coverage: not applicable (Kotlin is statically typed; no `Any` widening or suppressions added).
- Tests: not run (Gradle not available here). 12 new and 6 changed test files read.
- Lint and ktlint: not run.

## Unresolved questions

1. H4: should a bill that is in a group still offer per-person requests? If so, should those be group requests, in the group's currency?
2. C1: should editing a method withdraw or re-send the open requests that carry the old snapshot, or should an edit always make a new method?
3. For the proxy reviewer: should `POST /v1/requests` validate the method snapshot with the same rules as `PaymentMethod.problem()`?
4. Should the PastedLink allowlist be narrowed to known link shapes per service (`paypal.me/<name>`, `revolut.me/<name>`, `wise.com/pay/...`) to close the open-redirect path?
5. Group state size (also the proxy's open question 7): paginate, or slim the documents the app caches?
6. Should Firebase auto-init be off until someone signs in?

## Note on the working tree

When I finished, another session had uncommitted edits in `AppLink.kt`, `AppLinkTest.kt`, `GroupRepository.kt`, `RequestRepository.kt` and `RequestModels.kt`:
- the link token moves into the URL fragment (`/g/#{token}`);
- invite and request-link tokens move into POST bodies (`v1/invites/open`, `v1/invites/join`, `v1/request-links/open`, `v1/request-links/accept`);
- a new `removeMember`;
- a new `creditorMemberId` on `RequestView`.

This review covers HEAD `080127e` and not those edits. None of them touches the code behind C1, H1-H5 or M1-M5. Once committed, the new link parsing and the routes need a short re-review against the proxy contract.
