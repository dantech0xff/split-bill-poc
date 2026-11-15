# Phase 6 app and domain review: bill links, live claims, settle

Date: 2026-11-15 (UTC). Branch `feat/phase-06-web-companion-realtime-claim`, diff `18c2842..HEAD -- app core`.
Contract: `docs/proxy.md`, "Bill links and the web pages".
Plan: `plans/261114-0220-split-vibe-production/phase-06-web-companion-realtime-claim.md`.

## Code Review Summary

### Scope
- **Files:** the 35 files of `git diff 18c2842..HEAD -- app core`.
  - Production: `ClaimSplit.kt`, `data/share/*`, `PaymentPayload.kt`, `ProxyApi.kt`, `SavedBill.kt`, `RequestRepository.kt`, `RequestMoneyViewModel.kt`, `GroupViewModel.kt`, `ui/claim/*`, `SplitBillViewModel.kt`, `ResultContent.kt`, `SplitScreen.kt`, `AppNavHost.kt`, `AppModule.kt`.
  - Tests: the ones the brief lists.
- **Also read:** `proxy/src/shares.ts`, `proxy/src/input.ts`, `proxy/src/requests.ts`, `proxy/src/share-state.ts`, `web/src/pages/share-sections.tsx`, `AddToGroupViewModel.kt`, `PaymentRequestBuilder.kt`, `BillRepository.kt`, `SplitPlan.kt`, `SplitCalculator.kt`.
- **Size:** about 1,130 production lines and 1,131 test lines added.
- **Focus:** the Phase 6 diff. The proxy's contract and code are the reference for what the app sends.

### Overall assessment

The building blocks are sound and match the contract:
- `ClaimSplit`;
- `BillProjection`;
- the single write lock in `ShareRepository` together with its `sent` map;
- the live flow that connects first and then calls `open`.

The end-to-end settle does not hold as wired:
- The first "Send what everyone owes" on a draft bill is undone about half a second later by the app's own save (C1).
- The amounts can carry a payment method that sends money to the wrong person, or to an account the user has since corrected (H3, H5).
- A merchant name with a stray control character blocks finalize (H2).
- After the system ends the process, the claim screen comes back blank (H1).
- Taking a stranger off the link after locking leaves them in the split (H4).

The test meant to prove "page = phone = `SplitCalculator`" runs on a fake host and compares a function with itself, so the suite stays green over C1. **Not ready to ship.**

### Critical issues

**C1. Finalize is undone by the save that marks the bill finished.**

How it happens, step by step:
1. `ClaimViewModel.kt:133-134` sends `shares.finalize(stored, …)` with `stored.revision` = N, then calls `host.finish()`.
2. On a draft bill, `finish()` changes the status to FINAL and queues a save (`SplitBillViewModel.kt:510-514`, `:520-527`).
3. About 400 ms later, `persist` writes revision N+1. Because `written.share != null`, it calls `showOnLink` (`SplitBillViewModel.kt:618-625`).
4. In `project`, `sent[shareId]` is N, which is below N+1 (`ShareRepository.kt:54-58`). So it sends `PUT /v1/shares/{id}` with `billRevision` N+1.
5. The proxy leaves the link alone only when both the revision and the bill are unchanged (`proxy/src/shares.ts:160-169`). Otherwise it writes `amounts = NULL, status = CASE status WHEN 'locked' THEN 'locking'`.

**Scenario:** a new bill, then "Let everyone pick their items", "Stop picking and split", "Send what everyone owes".
- The page shows the amounts, then goes back to "picking closed".
- On the phone, the live update flips the bar from "Done" back to "Send what everyone owes".
- If the user already tapped Done, the page stays without amounts.
- A second send sticks, because `finish()` then changes nothing.

**Impact:** the phase's main outcome, a page that shows what each person owes after finalizing, fails on the default path. Success criterion 5 fails in practice.

The tests miss it because `ClaimViewModelTest.FakeHost` (`ClaimViewModelTest.kt:216-242`) neither saves nor re-projects on `finish()`.

**Fix:**
- Mark the bill finished before reading the stored copy, so finalize carries the last revision:
  ```kotlin
  fun send() = run {
      host.finish()                                        // queue FINAL first (no-op if final or with problems)
      val stored = checkNotNull(host.storedBill()) { … }   // flushes that save: revision N+1
      …
      keep(shares.finalize(stored, settings.value.myName, amounts))
  }
  ```
- Also see M1: `project` should not send a copy that changes nothing on the page.
- Add a test with the real `SplitBillViewModel` host, like `BillLinkSyncTest`:
  1. lock, then send;
  2. wait longer than the save delay;
  3. check that no `PUT` comes after `/finalize`, or that the last `billRevision` is no higher than the finalized one.

### High priority

**H1. After the system ends the process, the claim screen comes back blank with no way forward.**

Why:
- `ClaimKey` comes back with the back stack (`AppNavHost.kt:73-104`).
- The activity-scoped split view model reloads its bill asynchronously (`SplitBillViewModel.kt:679-693`). `billOnScreen` is a `stateIn` copy of its state (`:186`).
- `ClaimViewModel` calls `start()` in `init`. `start()` returns at once when `host.billOnScreen.value` is null (`ClaimViewModel.kt:83`), and nothing calls it again.
- `ClaimScreen.kt:118-133` then shows nothing: no message, no spinner, no retry.

**Scenario:** the user taps "Share the link" and goes to a chat app. Android ends the process. Coming back shows an empty "Everyone picks their items" screen.

**Fix:**
- In `init`, wait for the bill before starting: `viewModelScope.launch { host.billOnScreen.filterNotNull().first(); start() }`.
  - Add a timeout that shows "This bill is not open any more" with Back.
- Keep the bill's id in the state.
  - Check `host.billOnScreen.value?.id == billId` before `lock`, `send` and `applyPicks`.

**H2. A control character in the merchant name makes finalize fail with 400, and request links fall back to untracked.**

How the character gets through:
- `ClaimViewModel.kt:130` and `RequestMoneyViewModel.kt:80` pass the raw `bill.merchant` as the payment note.
- `PaymentRequestBuilder.kt:62` copies it unfiltered into `details` ("Note: …").
- `PaymentPayload.of` (`PaymentPayload.kt:24-33`) only shortens `details` to 1,000 characters.

What the proxy does with it:
- It refuses `details` or `qrPayload` that hold any control character other than CR or LF (`proxy/src/input.ts:160-163`, `:197-202`).
- One bad payment makes the whole `amounts` invalid (`share-state.ts:321-322`), and the whole `POST /v1/requests` too (`requests.ts:148-150`).

Such characters do occur: `BillProjectionTest` uses `merchant = "Pho\t24"`, and commit `1d3fb9b` says they come from photos. Only the projection was cleaned.

**Impact:**
- The user sees "Something in this did not check out" and cannot fix it, because the tab is invisible.
- Phase 5 request links for such bills now take the "could not be kept" path. That is a regression.

**Fix:**
- Clean the note the same way `BillProjection.clean` does, and share that helper.
- Make `PaymentPayload.of` apply exactly the rules of `input.ts`:
  - in `details`, drop control characters other than `\n` and `\r` (or turn them into spaces), and keep at least one character;
  - drop a `qrPayload` that holds such characters;
  - drop a `link` that holds whitespace or control characters, or is longer than 2,048 code points.
- Add tests with the inputs the proxy refuses: a tab, `\u0007`, `\u0085`, a space in a UPI link, a link of 2,049 characters.

**H3. Every person's payment points to the user, even when the user is not the one who is owed.**

Why:
- `ClaimViewModel.kt:126-131` attaches the user's main method to every participant other than `me` whose amount is above 0. It does not look at `plan.payments` or `saved.group`.
- The page shows that as how to pay, and says "settle it with {owner}" (`web/src/pages/share-sections.tsx:251-277`).
- The finish bar offers the link even when someone else paid (`ResultContent.kt:680-685`, the `else` branch, not `iPaidItAll`), and for bills in a group (`:661-666`).
- Phase 5 deliberately asks people for money only when `iPaidItAll`. It keeps group bills out of per-person requests: "In a group, people settle up across all its bills".

**Scenarios:**
- Minh paid the whole bill. The page tells Lan, and Minh himself, to pay Dana with Dana's QR.
- A bill in a group. People pay Dana on the page while the group still counts them as owing, so they pay twice unless someone records a settlement.

**Fix:**
- Attach `payment` only when `stored.plan.payments.singleOrNull()?.participantId == ME_ID && stored.group == null`.
- Otherwise send the amounts with `payment = null`, and say on the claim screen that the page shows shares only.
- Whether the page should show net debts instead is a product question (see below).

**H4. Taking someone off the link after locking leaves them in the split.**

Why:
- While the link is `locking`, each row offers "Take {name} off the link" (`ClaimScreen.kt:223-234`).
- `remove()` (`ClaimViewModel.kt:106`) only updates the view.
- By then, `applyPicks` has already added the stranger as a participant and given them their items (`:196-207`).

**Scenario:** the user locks, notices "Stranger", takes them off, then sends.
- The amounts include "Stranger" owing for the bun cha, with the user's QR.
- The people who really ate it pay less.
- The page lists a person whose claim is gone.

The same happens after reopening: a participant added for a removed claim stays on the bill and shares the unpicked items.

**Fix (either):**
- While locking, split again from the claims in the answer, and remove the participant that `applyPicks` added for that claim (keep a map from claim id to the participant it added).
- Or, once locked, offer only "Let people pick again".

**H5. The page can keep showing an old or corrected account.**

Why:
- The payment payload is fixed at finalize (`ClaimViewModel.kt:126-133`). Nothing sends it again or withdraws it when a method is edited, replaced, removed or made main.
- Phase 5 had the same problem on request pages and fixed it (finding C1 of the Phase 5 app review):
  - `RequestIds.kt:15-19` derives the request id from the method;
  - `RequestRepository.kt:101-113` withdraws requests that a new one replaces.
- The claim flow also picks the main method silently, with `paymentMethods.all.first().firstOrNull()`. There is no choice and no preview, unlike `RequestMoneyViewModel.method`.

**Impact:** a payer scans a QR for an account the user has since corrected.

**Fix:**
- Store a fingerprint of the method used in `ShareLink`, for example the SHA-256 of the method's JSON, as `requestIdFor` does.
- When a method changes, reopen or re-finalize the linked bills whose fingerprint differs. At the least, flag them on the result and claim screens.
- Before sending, show "The page will ask for: {method}", with a way to pick another and a warning when no method is set.

### Medium priority

**M1. Every save re-projects the bill, even when the page would not change, and wipes a settled link.**
- `showOnLink` runs after every save of a linked bill (`SplitBillViewModel.kt:625`). The proxy treats any higher `billRevision` as a change.
- After finalize, any of these saves resets `locked` to `locking` and drops the amounts:
  - a group link;
  - an exchange rate the user typed;
  - a change of who paid;
  - any edit of the split.
- Nobody is told, because `showOnLink` throws the answer away.
- Each such `PUT` also counts against the 300 changes an hour that `lock`, `finalize` and `removeClaim` need.
- Making a link costs two `PUT`s: one from `publish`, and one from the save that `linkToShare` triggers.

**Fix:**
- For each link, remember a fingerprint (the projection's JSON plus `plan.result(bill).shares`) instead of the revision alone, and skip `project` when it is unchanged.
- When a `PUT` answers `locking` for a link last seen `locked`, show a notice on the split screen: "The link no longer shows what everyone owes. Open it to send again."

**M2. Re-projecting brings deleted links back, and switching accounts orphans links.**
- A `PUT` to an id that no longer exists makes a new link and answers 201 (`proxy/src/shares.ts:140-146`).
  - `project()` ignores that answer, and `SavedBill.share` has no `expiresAt`.
  - So after the daily run deletes an expired link, the next edit silently publishes the bill again for 30 days. The new token is unknown to anyone, and the link counts against the quota.
- After an account switch, `existing()` takes the 404 to mean "gone" and makes a new link (`ClaimViewModel.kt:147-153`). The old link stays live with its claims.

**Fix:**
- Keep `expiresAt` and the owner's account id in `ShareLink`, and skip and forget a link that has expired.
- Treat a 201 from re-projecting as a link brought back: delete it and forget it.
- When the account differs, tell the user instead of making a new link silently.

**M3. The bill link's token leaks into group bill documents.**
- `AddToGroupViewModel.sharedCopyOf` (`:203-212`) removes the pages, the OCR text, the rate and the group link, but not the new `share`.
- So `share.url` (`…/s/#<token>`) is stored in D1, inside the group's state:
  - every member can read it, and so can anyone who joins later;
  - yet the proxy deliberately keeps only hashes of tokens.

**Fix:** set `share = null` in `sharedCopyOf`, with a test.

**M4. The live connection ignores whether the screen is visible, and cannot tell when the socket has died.**
- `listen()` collects in `viewModelScope` (`ClaimViewModel.kt:162-176`). The socket, its 30 s pings and the reconnect loop keep running in the background as long as `ClaimKey` is on the back stack. That costs battery and data, and holds one of the link's 50 connection slots.
- `ProxyApi.webSocket` uses the default client, which has no `pingInterval`, and nothing checks for a `pong`. A half-open TCP connection, for example after switching from Wi-Fi to mobile data, can freeze the live view without any error until TCP gives up. That works against the 2-second success criterion.
- Nothing on screen says "reconnecting".

**Fix:**
- Collect only while the screen is started: `repeatOnLifecycle(STARTED)` in the screen, or `stateIn(WhileSubscribed(5_000))`.
- Give the socket `client.newBuilder().pingInterval(30, SECONDS)`, after checking that the Durable Object answers protocol pings. Otherwise, add a watchdog that reconnects when no `pong` arrives.
- Show the connection state on the screen.

**M5. An unexpected exception in the live flow crashes the app.**
- The coroutine launched in `onOpen` catches only `ProxyException` and `IOException` (`ShareRepository.kt:118-129`).
- Any other exception from `open()` is not caught: a decoding error, or a failed `checkNotNull` in `view()`.
  - It cancels the `callbackFlow`, and `live()` throws it again.
  - `listen()` has no `catch`, so the exception reaches `viewModelScope` unhandled and the process dies.

**Fix:** catch `Exception` in `onOpen` (rethrowing `CancellationException`) and close the connection. Add `.catch { }` with a retry in `listen()`.

**M6. The "Sign in" card never goes away.**
- `needsSignIn` is decided once, in `start()` (`ClaimViewModel.kt:84-86`), and the card's only action opens Settings.
- After signing in and coming back, the card is still there; the user has to leave the screen and open it again.

**Fix:** watch `account` and call `start()` when a session appears.

**M7. Destructive actions have no confirmation.**
- One tap on "Stop sharing the link" (`ClaimScreen.kt:84-87`) deletes every claim, and it cannot be undone.
- "Take {name} off the link" has no confirmation either.
- Phase 5 asks before Remove and Leave (`GroupScreen.kt:176`, `:197`).

**Fix:** confirm both with an `AlertDialog`.

**M8. Matching people by hand is lost on the round trip the screen itself suggests.**
- `matches` lives only in the view model of the claim screen's entry.
- "Check the split first" closes the screen (`ClaimScreen.kt:248-256`). Coming back suggests matches by name again.
- **Example:** a claim typed "Bob" that the user matched to Lan becomes "someone new" again. After "Let people pick again" and another lock, Lan's items move to a duplicate participant "Bob".

**Fix:** persist which participant each claim is, for example in `ShareLink` or the `SavedStateHandle`.

**M9. `applyPicks` can make the bill unshareable, and empty claims become payers.**
- It adds a participant for each new claim. With up to 30 claims on top of the existing participants, the plan can pass 30 people.
  - The re-projection then answers 400, which is swallowed.
  - `finalize` answers 400, shown as "did not check out".
- A claim with no items still adds a participant, who then shares every item nobody picked.

**Fix:**
- Check `MAX_PEOPLE` before `applyPlan`, and explain when the limit is reached.
- When adding new people, skip claims with no items, or ask first.

**M10. Tests that pass for the wrong reason, and gaps.**
- "the amounts sent to the page are the calculator's and the saved bill's" (`ClaimViewModelTest.kt:166-192`) proves less than its name:
  - It runs on `FakeHost`, whose `storedBill()` returns the copy on screen and whose `finish()` saves nothing.
  - It compares `plan.result()` with `SplitCalculator.split()` and `withSharesWorkedOut()`, which are the same computation.
  - So it cannot catch C1.
- No proxy or web test reconciles the amounts either (searched `proxy/` and `web/`). The automated reconciliation that success criterion 5 asks for does not exist.
- Missing tests:
  - the real host: send, finish, save, `PUT`;
  - the claim screen restored after process death (H1);
  - taking a claim off after locking (H4);
  - a live `Gone` closing the screen;
  - a 404 or 410 on `GET` leading to a new link;
  - control characters in payment details (H2);
  - a bill paid by someone else, and a bill in a group (H3);
  - signing in after seeing the card (M6);
  - removing an item while the link is open (criterion 6, app side);
  - payload rules mirrored from `input.ts`.

### Low priority
- **L1. No busy state.** While `working`, the split button looks enabled but ignores taps, because `SvSplitButton` has no `enabled` parameter (`ClaimScreen.kt:248-256`). Once a link exists, no progress shows, and "Let people pick again" stays active during finalize.
- **L2. A second lock discards edits.** Locking again after reopening replaces the split with the picks, silently dropping edits made after "Check the split first". Warn before replacing.
- **L3. `forgetLink` clears the wrong link.** It clears whatever link the bill holds, not the one that failed (`SplitBillViewModel.kt:659-661`). Compare the `shareId`.
- **L4. Wrong wording.** For 401 and 503, `ProxyErrors` says "groups and requests" on this screen. The text for a `locked` link says "how to pay you" even when no payment was attached (`ClaimScreen.kt:278`).
- **L5. Payments go unchecked.** `send()` checks only that the split is complete, not who paid. Finalize can succeed while `finish()` refuses, leaving a draft bill with amounts on the page. Check `plan.problems(bill)` instead.
- **L6. The page can lag after offline edits.** It keeps the old projection until the next save. `start()` could re-project when `share.billRevision < stored.revision`.
- **L7. Strangers' names become suggestions.** The names they type become participants. On the save that marks the bill final, those names go into the `PeopleRepository` suggestions (`BillRepository.kt:35-37`).
- **L8. Unrelated reformatting.** `GatewayBillAnalyzer.kt` and `RequestsScreen.kt` were reformatted. Harmless, but outside the phase.
- **L9. The link filter is looser than the proxy's.** `PaymentPayload.opensOnTheWeb` does not check the length of `upi://` links or whitespace in them. The current builder never produces such a link, but the filter should match the proxy's (part of the H2 fix).

### Edge cases found by scout
- Process death with `ClaimKey` on top: the claim view model starts before the split view model has reloaded its bill (H1).
- After finalize, a save that only changes the status resets the link (C1); so do saves of other details that change nothing on the page (M1).
- The daily run deletes a link and a later `PUT` brings it back; an account switch makes the `GET` answer 404, and a new link orphans the old one (M2).
- A tab in the merchant name passes the projection but fails the payment details (H2).
- Strangers taken off after locking, claims with no items, and more than 30 participants after `applyPicks` (H4, M9).
- A half-open socket, and a live socket kept open in the background (M4).
- The group's copy of the bill carries the link's token (M3).
- A late 404 from re-projecting while a new link is made: safe today, because `writes` runs calls in order; L3 hardens it.

### Checked and found correct
- **The split screen stays the only writer of the bill.**
  - The claim view model never touches Room; it changes the bill through `BillOnScreen` and `change()`.
  - `storedBill()` saves any pending change under `saveLock` before reading.
  - `ClaimViewModel` lives with its screen's entry (`rememberViewModelStoreNavEntryDecorator`), so closing the screen closes the socket.
  - Its host lives with the activity, so nothing leaks.
- **`ShareRepository` keeps copies in order.**
  - All of the owner's calls run one at a time under one `Mutex`.
  - The `sent` map stops an older copy from going out after a newer one while the app runs.
  - After process death, the proxy's `billRevision` rule answers 409, which is swallowed.
  - A `project` and a `finalize` that race end correctly.
- **The finalize amounts are right.**
  - They come from the stored copy through `SplitCalculator`.
  - They name exactly the projection's participants and add up to `bill.totalMinor`, as `amountsOf` requires.
  - `me`, and anyone owing nothing, get `payment: null`.
- **`ClaimSplit` does what its comments say.** People who picked leave `notSharing`, unknown item ids are ignored, and an item nobody picked is shared by everyone. Its four tests are sound.
- **`BillProjection` fits the proxy's rules.**
  - Control characters are cleaned.
  - Cutting by UTF-16 units stays within the limits, which count code points.
  - The ids `i<n>` and `c<n>` fit the id rule, and `"other"` cannot collide with them.
  - More than 20 charges are added up into one line without changing the total.
- **The live flow follows the contract.**
  - The token travels only in `Sec-WebSocket-Protocol`.
  - Public calls carry no session and no install id (tested).
  - It connects first, then calls `open`, and `transformWhile` drops a view read just before `Gone`.
  - `awaitClose` stops the pings and closes the socket, and the wait between attempts stops growing at 30 s.
  - OkHttp lifts its call and read timeouts once the connection is upgraded.
  - The callbacks use only `trySend` and `launch`, which are safe from any thread.
- **The test changes are sound.**
  - The `DesignSystemBoundaryTest` exemption is needed, because the rule scans all of `src/main/java`, and it is narrow.
  - `WebTokensTest` only checks the file unless asked to write it.

### Recommended actions
1. C1: mark the bill finished before reading the stored copy, and add a test with the real host.
2. H1: wait for the bill, and keep its id.
3. H2: clean the note, and make `PaymentPayload.of` follow `input.ts`, with tests.
4. H3: attach a payment only when the user paid the whole bill and the bill is not in a group.
5. H4: when someone is taken off while locking, split again and remove the participant added for them, or do not offer it.
6. H5: keep the method's fingerprint on the link, reset or resend when methods change, and show the method before sending.
7. M1, M2: re-project only when the page would change, keep `expiresAt`, never bring a deleted link back, and tell the user when a settled link was reset.
8. M3: `sharedCopyOf` clears `share`.
9. M4, M5: tie the connection to the screen's lifecycle, detect missing pongs, and catch errors in the live flow.
10. M6 to M10, then the Low items.

### Metrics
- **Type safety:** the new production code has no `@Suppress`, no `Any` widening and no `!!`.
- **The requested command** (`--offline`): BUILD SUCCESSFUL.
  - `:core:domain:test` was up to date: 134 tests, 0 failures, including `ClaimSplitTest` (4).
  - 24 app tests passed:

    | Test class | Tests |
    |---|---|
    | ClaimGalleryTest | 3 |
    | ClaimViewModelTest | 6 |
    | ShareRepositoryTest | 6 |
    | BillLinkSyncTest | 2 |
    | SharedImagesTest | 3 |
    | PaymentPayloadTest | 4 |

- **An extra run**, for tests the filters missed, also passed: `BillProjectionTest` 6, `RequestRepositoryTest` 5, `DesignSystemBoundaryTest` 2, and `WebTokensTest` 1 in `:core:designsystem`.
- **Coverage:** not measured.
- **Lint:** not run.
- **Working tree:** clean after the runs.
- **Gradle daemons:** I did not start one.
  - Both runs reused an idle Gradle 9.4.1 daemon (PID 2320) and its Kotlin daemon (PID 2485), which an earlier session had started.
  - I did not run `./gradlew --stop`, because it stops every daemon of that version, including ones I do not own.

### Plan follow-ups (plan not edited)
- **Todo "App: make the link, follow it live, match people, settle in two steps, write the split into the bill":** built, but not done (C1, H1 to H5).
- **Todo "Show the bill again when it is edited":** built; M1 and M2 remain.
- **Success criterion 5** (amounts equal on the page, on the phone and in `SplitCalculator`, checked automatically): not met (C1, M10).
- **Criterion 4** (two browsers and an Android phone see a pick within 2 s): no evidence from the app yet. Check by hand or with an instrumented test after M4.
- **Criterion 6** (removing an item while the link is open): the app's re-projection of it has no test.

### Unresolved questions
1. When the user did not pay the whole bill, or the bill is in a group, should the page show shares without a way to pay, or what each person owes the person who actually paid? This is a product decision.
2. Should a claim with no items add a participant?
3. When an edit resets a settled link, should the app send the amounts again by itself, or ask the user?
4. If Gradle daemon PID 2320 belongs to the lead session, it should be stopped there when the work is done.
