# Code review: Phase 7 domain rules and the result screen's AI edits

Date: 2026-11-16 (UTC). Branch `feat/phase-07-ai-assistant-insights`, reviewed at `f0ff557`. The six
commits the other agent added since (`47f9ed8` to `13ca3ed`: insights screen, export, recurring
bills) change no file in scope.

Scope:
- Domain commits `c2a7e2c`, `a2244d6`, `1885399`, `570c209`, `5af9889`, `5e808cc` and `9dbc20a`.
- App commits `f478f46`, `c26ebde`, `1ccce69`, `c054baa` and `284ef29`.

Read against:
- `phase-07-ai-assistant-insights.md`: Đã chốt, Hợp đồng chung, Requirements, Success Criteria and Security.
- The two builder reports.
- `docs/architecture.md` and `docs/design-system.md`.
- The proxy's `split-revision.ts`, `revise-prompt.ts` and `input.ts`, where the app's assumptions meet them.

## Verdict

Not ready to land as it is. Most of the money core is sound:
- I found no order of edits that leaves the shares short of the total without a reported problem.
- No share goes below zero when the total is zero or more.
- A rejected edit changes nothing, and unknown items, people and refs are rejected.
- Payers follow the total.
- The insights table is right, and so are the recurrence rules and the draft ids.
- Decimal calls are read and shown correctly.
- Voice input meets the privacy rules.

Two High findings break the phase's central promise, "the AI proposes, the calculator computes":
- **H1:** a model answer the proxy accepts crashes the app.
- **H2:** an item edit on a whole-bill split quietly brings back item assignments the user had left
  behind. The split that is applied then differs from the sentence shown, and from what the
  proxy's prompt tells the model.

Three Medium findings concern Undo, the instruction field and the request builder.

Counts: Critical 0, High 2, Medium 3, Low 9.

## How I checked

- I read every file in scope at HEAD and each commit's diff, and checked each claim in the two builder reports.
- I ran `:core:domain:test` on a copy of the committed tree in the scratchpad, never in the shared checkout:
  - The copy was made with `git archive HEAD`, with the settings cut down to `:core:domain`.
  - Gradle ran with `--offline --no-daemon`, and Kotlin compiled in-process.
  - The checkout's build directory and the other agent's daemon were not touched.
- All 185 existing tests pass, as the domain report says.
- I added 14 scratch probes to that copy only, never to the repository. Their output is quoted below as "probe Pn". The probe file is
  `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/review-p7/tree/core/domain/src/test/kotlin/com/dantech/splitvibe/domain/ReviewProbeTest.kt`.
- I ran no app test, as instructed. App findings come from reading the code unless stated.
- I recomputed by hand every figure of the 20-bill insights table.

## Findings

### Critical

None.

### High

**H1. A model answer the proxy accepts crashes the app.** The domain exception is verified; the app crash follows from the code path.

Where:
- `core/domain/.../BillEditor.kt:134-138`: `evenUnitPrice` ends in `longValueExact()` with no range check. It is reached from `SplitEdits.kt:173-178` (`itemAmount`).
- `SplitEdits.walk` (`SplitEdits.kt:64-79`) catches only its own `Rejection`.
- `SplitAssistantViewModel.kt:140-141` and `:341-348` run `SplitEdits.apply` and `describe` inside `combine(...).stateIn(viewModelScope)` with no guard.

What is wrong:
- The unit price is `amount / quantity`, worked out in `DECIMAL64`.
- When the quotient is a whole number above `Long.MAX_VALUE`, `longValueExact()` throws `ArithmeticException`.
- Nothing between the domain and the view model's state flow catches it. The exception ends the `stateIn` coroutine, and Android kills the app.

Failing scenario:
1. On the result screen, the user asks for "beer quantity 0.000001 and its amount 10,000,000,000,000". An item name read from a photo, acting as a prompt injection, can ask the same.
2. The model answers `[{"op":"item_quantity","itemId":"i1","quantity":"0.000001"},{"op":"item_amount","itemId":"i1","amountMinor":10000000000000}]`.
3. Both edits pass the proxy's own checks: the quantity regex at `split-revision.ts:355` allows six decimals, and `moneyOf` allows amounts up to 10^15.
4. `show()` stores the proposal. `uiOf` calls `proposalUi`, which calls `SplitEdits.apply`, and that throws `ArithmeticException: Overflow`.
5. The app crashes, and the 0.2 call is charged.

On a bill that already holds a small quantity, such as 0.0001 kg of saffron, one `item_amount` edit is enough.

Evidence:
- Probe P1: the first edit alone is applied. With both edits, `apply` and `describe` throw `java.lang.ArithmeticException: Overflow`.
- Probe P1b: quantity 0.0001 on the bill and one `ItemAmount(i1, 10^15)` throw the same.
- The property test cannot find this. Its quantities are 0.5 or more (`SplitEditPropertyTest.kt:227`), and the parser fuzz uses the quantities "3" and "4".

Breaks:
- Success Criterion: an instruction with odd directives must not create a state that breaks the invariants.
- Security: every edit goes through `applyEdits`.

Fix:
- In `evenUnitPrice`, return null when the quotient is outside `0..MAX_AMOUNT_MINOR`, checked before `longValueExact()`. An exact division with a remainder check also removes the `DECIMAL64` rounding noted in L9.
- As a backstop, turn an `ArithmeticException` in `SplitEdits.walk` into a rejection: "That is more than a bill can hold."
- Let a failure in `proposalUi` become an error message, not a crash.
- Add "0.000001" to the property test's quantities, and amounts near `MAX_AMOUNT_MINOR` to its amounts.

**H2. An item edit on a whole-bill split brings back hidden item assignments, so what is applied is not what the sentence says.** Verified.

Where:
- `SplitEdits.kt:204` (`byItem()`, which is `plan.withMode(BY_ITEM, bill)`) and `SplitEdits.kt:84` (`split_mode`) both use `SplitPlan.kt:104-115`. That sets `whole = null` and keeps `setup.items`.
- `SplitRevisionRequest.kt:86-88` sends those items whatever `whole` is.
- The proxy prompt (`proxy/src/revise-prompt.ts:27`) tells the model that "every item while whole is set is shared equally by everyone sharing the bill".
- Prompt line 34 and the contract's `split_mode` row say a mode starts "from equal parts" (bắt đầu từ chia đều).

What is wrong:
- Switching the split mode on the result screen keeps each item's division: `withMode` never clears `setup.items`.
- When an AI item edit later switches the bill back to by item, those divisions come back.
- The model does not know this, because of its prompt. The user does not know either, because the screen shows one sentence.

Failing scenario:
1. A bill of beer 90,000 (i1), pho 200,000 (i2) and a 14,500 service charge, with a printed total of 304,500. Participants: the user, Minh and Lan.
2. The user once split it by item with the pho for Minh only, then chose Equal. Everyone pays 101,500.
3. The user says "Lan didn't have beer", and the model answers `item_sharers i1 [me, p1]`.
4. The proposal lists one change, "Beer: shared by you and Minh". The after column shows you 47,250, Minh 257,250 and Lan 0, because the pho is Minh's alone again.
5. The sentence and the prompt mean about 117,250, 117,250 and 70,000.

Apply saves the wrong split. When the hidden item is small, the difference can pass unnoticed.

Evidence:
- Probe P13 printed the following lines:
  ```text
  items after={i2=Equally(among=[p1]), i1=Equally(among=[me, p1])}
  sentences=[Beer: shared by you and Minh]
  before=[101500, 101500, 101500] after=[47250, 257250, 0]
  ```
- Probe P12 shows the request sending `"whole":{"type":"equal",...}` together with `"items":{"i1":{"type":"equal","among":["me"]...}}`.
- The property test does build such plans: `randomPlan` draws `whole` and `items` independently. It checks only sums, so it passes.

Fix:
- In `SplitEdits`, when an item edit or `split_mode by_item` leaves a whole-bill split, start from an even split by item: `plan.copy(setup = plan.setup.copy(whole = null, items = emptyMap()))`. That matches the contract ("starting from an equal split") and the prompt.
- Leave the screen's own mode switch as it is.
- In `SplitRevisionRequest.body`, send `items` only when `whole` is null.
- Add a test with a hidden item division.
- If the owner wants the remembered assignments to come back instead, that is a contract change (question 1). The prompt's line 27 must then say so, and `describe` must name the assignments that return.

### Medium

**M1. Undo does not bring back a finished bill: it stays a draft.** Verified by code path.

Where:
- `SplitAssistantViewModel.kt:208-228`: `Undo` keeps only the bill and the plan.
- `SplitBillViewModel.kt:506` and `:529-536`: `change()` applies `settledStatus()`.
- `data/bills/SavedBill.kt:71`: `settledStatus()` only ever turns FINAL into DRAFT.

What is wrong:
- Applying a proposal that leaves a problem turns a finished bill into a draft, which is right.
- Undo puts back the old bill and plan, but not the status, because `settledStatus()` never goes back to FINAL.

Failing scenario:
1. The user opens a finished bill from the history; its requests were already sent.
2. They ask "add a 10% tip". The bill has two payers, so the proposal shows "After these changes: Payments add up to …".
3. They tap Apply, see the problem, and tap Undo.
4. The shares are back, but the bill is saved as a draft:
   - History shows it as a draft.
   - The new insights and exports leave it out, because they count finished bills only (`BillRepository.finished()`, used at `InsightsViewModel.kt:99`).
   - "Repeat" is no longer offered (`HistoryScreen.kt:121`).

Evidence:
- The code path above.
- `SplitAssistantFlowTest` checks only `bill` and `plan` after Undo (lines 117-118 and 146-147) and never starts from a finished bill. So "Undo brings back the previous bill and plan exactly" is true of the bill and plan, but not of the saved bill.

Fix:
- Keep the status in `Undo`, and give `SplitOnScreen` a way to restore it with the split. For example, `replaceSplit(bill, plan, status)`, where the split view model allows FINAL back only when the restored plan has no problems.
- Add a flow test that starts from a finished bill.

**M2. A line break or tab in the instruction makes every request fail, with a dead-end message.** Verified by code path and by the proxy's own test.

Where:
- `AssistantContent.kt:99-134`: the field is multi-line (`maxLines = 4`, not single-line).
- `SplitAssistantViewModel.kt:158-169`: `onTextChanged` and `send()` only cut and trim.
- `SplitRevisionRequest.kt:40` also only trims.
- On the proxy, `split-revision.ts:172` reads the instruction with `textOf`, which refuses any control character (`input.ts:74` and `:99`).
- `proxy/test/split-revision.test.ts:175` expects a 400 for `"Lan\nkhông uống bia"`.

Failing scenario:
1. The user pastes "Lan didn't have beer" and "add a 10% tip" on two lines. A line break can also come from a hardware keyboard, or from the keyboard's own dictation ("new line").
2. The proxy answers 400 `input`.
3. The app shows "This bill cannot be changed this way. Change it by hand instead."

That message is wrong: the bill is fine, and the same request without the line break would work. How a line break gets into the field is plausible rather than tested; the refusal and the message are certain.

Fix:
- In `SplitRevisionRequest.body`, replace each run of whitespace and control characters in the instruction with one space, so the builder only makes bodies the proxy accepts.
- Filter line breaks in `onTextChanged`.
- Add a test.

**M3. The request sends bill text as it is stored, and the proxy refuses some of what the app can store.** Plausible: it depends on the data.

Where:
- `SplitRevisionRequest.kt:48-57`, `:67` and `:80` send the merchant, item names, charge labels and participant names as stored, and the currency code as it is.
- The proxy answers 400 for any of the following (`split-revision.ts:43-44`, `input.ts:93-100` and `:179-185`):
  - control characters in any name;
  - names over 200 code points;
  - a merchant that is empty once formatting characters are removed;
  - any currency code that is not three letters A to Z.
- The proxy's own test lists these cases at `split-revision.test.ts:176-183`.

How the app comes to hold such text:
- `BillAnalysisParser.kt:84`, `:95` and `:113` only trim what the model read.
- The proxy passes the bill read's answer through unchecked (`bill-analysis.ts:23`). So an item name the model wrote over two lines keeps its line break.
- An unknown currency with no local currency set resolves to the code "" (`BillCurrency.resolve`), and a code like "VNĐ" stays as written.

Failing scenario: a bill read from a receipt line printed over two lines, "Bia Saigon" and "Lon 330ml". Every request on that bill gets "This bill cannot be changed this way. Change it by hand instead.", and nothing on screen says why.

Evidence:
- The code path above.
- The bill link already cleans the same fields for the same proxy (`data/share/ShareModels.kt:77-85`, `clean()`), so this route is the odd one out.
- The domain report's open question 1 asks exactly this. The answer is yes: the proxy refuses them.

Fix:
- Clean names and labels in `SplitRevisionRequest.body` the way `ShareModels.clean` does. That needs one shared function in `:core:domain`, which also removes the third copy of the same logic (L8).
- For a currency code the proxy cannot take, say so in the assistant before sending ("Choose the bill's currency first"), not after the proxy refuses it.

### Low

- **L1. `split_mode` to the mode the plan already has changes nothing.** Verified, probe P3.
  - `SplitEdits.kt:84` uses `SplitPlan.withMode`, which returns early when the mode is unchanged (`SplitPlan.kt:105`). The contract and the prompt say a mode starts "from equal parts".
  - Example: with shares of 2:1:1, "split it by shares evenly again" gives `split_mode by_shares`. The edit is applied but changes nothing, and the card says "None of the changes that are kept can be applied", although nothing was rejected.
  - Fix: in `SplitEdits`, always reset to the mode's even start, or reject the edit with a reason ("The bill is already split in shares.").
- **L2. A quantity written with trailing zeros counts as a change.** Verified, probe P2.
  - `ItemQuantity(i1, "3.000000")` on a quantity of 3 changes the `Bill`, because `BigDecimal` equality depends on the scale. So Apply is offered for a no-op (`SplitAssistantViewModel.kt:366`).
  - The bill is then stored and sent with `"quantity":"3.000000"`.
  - Fix: store `quantity.stripTrailingZeros()` with a scale of at least 0 in `SplitEdits.itemQuantity`, or compare quantities with `compareTo`.
- **L3. Insights round half to even; the bill's own conversion rounds half up.** Verified, probe P5.
  - `Insights.kt:119` passes `HALF_EVEN`, while `ExchangeRate.convert` defaults to `HALF_UP` (`ExchangeRate.kt:40`).
  - $5.00 at 25,350.5 is 126,752 in the statistics and 126,753 everywhere else the app converts it.
  - Fix: one rounding rule for shown conversions, or a line in the contract saying the statistics differ.
- **L4. The parser can still throw, on an answer only a broken proxy would send.** Verified, probes P7 and P8.
  - `Json.parseToJsonElement` throws `StackOverflowError` on 100,000 nested arrays. `SplitRevisionParser.kt:51-55` catches only `IllegalArgumentException`, and `ProxyApi.parse` meets the same text first.
  - The proxy validates every answer (`revisionAnswerOf`), so this only matters as defence in depth. But the KDoc and the domain report both say parsing never throws.
  - Separately, invisible characters that are not format characters, such as U+3164, pass as a name ("Add ㅤ"). The proxy accepts them too.
  - Fix: correct the claim, or catch `StackOverflowError` around the parse. Consider refusing names that show nothing.
- **L5. `item_amount` and `item_quantity` do not switch a whole-bill split to by item.** Verified, probe P4.
  - This deliberately departs from the contract's rule ("Một thao tác trên món … chuyển sang chia theo món trước") and from the prompt ("An edit of an item switches …", `revise-prompt.ts:49`).
  - The domain's choice is the better one, because it keeps a split by shares or by percent. The contract and the prompt should say so, so that the three parts agree.
- **L6. The assistant's state is lost on process death.** Verified by code.
  - `Draft` holds the typed text, the question, the proposal and the Undo, and it lives in memory only. So does the request id in `ProxyModelCall.unfinished`.
  - The state is lost cleanly, and nothing is half applied. But the user has to type again, and asking again is a new request that is charged again.
  - `docs/architecture.md:121-122` says that what was on screen comes back from `SavedStateHandle`, and `SplitEdit` was made serializable for this.
  - Fix: keep `text`, `question` and `proposal` in `SavedStateHandle`, or record the limitation.
- **L7. Voice details.** Verified by code.
  - `onDeviceUnusable` (`VoiceInput.kt:63`) lasts only as long as one screen's `VoiceInput`, a Koin `factory`. The app report says "for the rest of the run", but the next split screen tries the phone's own recognizer again.
  - When the speech service itself reports `LanguageUnavailable`, the app still says "This phone cannot turn speech in its language into text by itself. Tap the mic to try its speech service" (`SplitAssistantViewModel.kt:445-446`).
  - The "Use it" consent to the speech service is kept for good, with no way to take it back (`VoiceInput.kt:76-78`).
  - Listening does not stop when the app goes to the background.
  - Fix: word the message by `onDevice`, offer the consent in Settings, and stop listening on `ON_STOP`.
- **L8. Three copies of the same text cleaning.** Verified.
  - `SplitRevisionParser.oneLine` (domain), `ShareModels.clean` (app) and the proxy's `textOf` all strip format characters but keep the joiners.
  - Their rules differ slightly: names are cut at 80 code points in the app and 200 on the proxy, and the question at 500 in the app and 200 on the proxy.
  - One domain function would serve both app uses (see M3).
- **L9. Smaller items.** Verified.
  - `evenUnitPrice` divides in `DECIMAL64`, so a quotient above 16 digits can be rounded into a whole number and stored as a wrong unit price, which its doc says never happens. It needs amounts near 10^15, and the H1 fix covers it.
  - `Insights.byGroup` is keyed by the group's name, and the new `InsightsViewModel.kt:218` passes `saved.group?.groupName`. Two groups with the same name are therefore added up as one.
  - The domain report still shows `draftId` as `"{templateId}_2026-02-28"`, although `9dbc20a` made it a UUID.
  - `README.md:121-123` and `:308`, and `docs/architecture.md:123-124`, still speak of reads. The docs do not mention the assistant, `SplitEdit`, `Insights` or `Recurrence`. Both builders know this.
  - `CallsLeft.REVISION` repeats the server's 0.2; also known.

## Plausible but not verified

- M3 depends on real bills holding such text. The proxy's refusal is certain, but I did not find a stored bill that triggers it.
- In M2, no test shows how a line break reaches the field: paste, a hardware Enter, or the keyboard's dictation.
- In H1, the domain exception is proven. The app crash comes from reading the code: `combine` and `stateIn` in `viewModelScope`, with no exception handler. It was not run on a device.

## Checked and found correct

- **`SplitEdits.apply` follows the contract's rules:**
  - Edits are applied in order, each one whole or rejected with a reason.
  - A rejected edit changes nothing. The state is immutable, and the property test compares each prefix of the edit list.
  - Unknown items, people and refs are rejected, and a ref is freed when its person is removed.
  - `me` cannot be removed.
  - `item_sharers` and `item_shares` switch the bill to by item.
  - `add_charge` moves a printed total by the signed charge. A percentage is taken of the items' total after earlier edits, rounded half to even.
  - A discount subtracts. A total below zero or above `MAX_AMOUNT_MINOR` is rejected.
  - `item_amount` and `item_quantity` keep a printed total, as `BillEditor` does.
  - A single payer follows the total, and several payers give a reported problem.
- **Invariants.**
  - I found no order of edits that leaves the shares short of the total without a reported problem.
  - None gives a negative share on a total of zero or more, loses a participant, or leaves a payment by someone not on the bill.
  - Problems an edit leaves behind are reported as they are on screen, such as amounts after a new charge or percentages after a removal.
- **Overflow guards.**
  - Each part is bounded before the calculator adds them up.
  - Shares of `Long.MAX_VALUE` are divided with `BigInteger` (probe P9).
  - A percentage charge divides by 10,000, which always terminates.
- **Reused ids.** An id freed by a removal and reused by an addition is described by the new person's name ("Zoe paid the whole bill", probe P10). The before and after list matches people by both id and name.
- **The answer parser.**
  - It reads the 13 ops with their exact names and fields. Unknown ops and wrong types are counted, never thrown.
  - It keeps at most 20 edits and an explanation of at most 200 code points.
  - Control characters, line breaks and format characters other than the joiners are removed, and text is cut on code-point boundaries.
  - A question that comes with edits keeps both, and the app shows only the question. The proxy refuses that combination anyway.
- **The request builder.**
  - The body equals the contract's example.
  - Divisions use the domain's `Division` shape.
  - Divisions of items no longer on the bill are left out.
  - A blank user name goes as "Me".
  - Quantities go as plain strings.
- **Insights.**
  - Recomputed by hand for all 17 counted bills: the overall total of 29,053,156 and the user's 9,509,523, every month, group, category and person, and the one-dong drift of bill 10 all match.
  - Undated bills are in every sum except the months. Bills without a usable rate are only counted in `withoutRate`.
  - Ordering:
    - months, earliest first;
    - groups, largest total first, then by name, with no group last;
    - categories, largest total first, then in enum order;
    - people, largest own part first, then by name (probe P11).
  - The test does check real sums, with whole-map assertions. It has no ties, though, so only my probe checks the order of ties.
- **Recurrence.**
  - A schedule on the 31st falls on the last day of shorter months and comes back to the 31st.
  - Leap years and a start on 29 February work.
  - `next` is strictly after the given day.
  - The period key is the ISO date.
  - `draftId` is a version 3 name-based UUID in lower case (probe P14), which the proxy's regex (`input.ts:18`) accepts whatever the version.
  - The test that sweeps three years of start days is strong.
- **Categories.**
  - The nine values have the contract's wire names and labels.
  - A missing or unknown value reads as `OTHER`, and so does a stored bill without the field.
- **Decimal calls.**
  - Counts are read from the JSON number's text, never through a `Double`, and bounded before anything else is done with them.
  - At most two decimals are taken. Trailing zeros are dropped and the scale is kept at 0 or more, so "10.00" equals `BigDecimal(10)`.
  - Whole numbers from an older proxy read the same.
  - Counts are shown without trailing zeros, and the quota messages read right for whole calls and for parts of one.
- **Request id.**
  - The id is kept after a failure, a cut-off answer, an unusable answer and a cancel. It is renewed after `retry_limit` and after a 400.
  - The proxy runs a known id again without charging twice (`quota-book.ts:68-88`).
  - A question hides the edits, and the error kinds match those of a bill read.
- **The assistant flow.**
  - Nothing is applied without a tap.
  - Apply goes through `SplitBillViewModel.change` as one change: the split view model is still the only writer, the bill is saved once, and its link is updated.
  - Dropping an edit works everything out again on the bill on screen, and each reason lines up with its sentence.
  - Undo is offered only while the bill on screen is what Apply made it.
  - Another bill on screen resets the assistant.
- **Voice.**
  - The microphone is asked for at the tap. A refusal leaves typing working, with no error.
  - On Android 12 and later, the phone's own recognizer is used first.
  - The phone's speech service is used only after the user is told, and with `EXTRA_PREFER_OFFLINE`.
  - What is heard waits in the field and is never sent by itself. The field is locked while listening.
  - The recognizer is destroyed on cancel, on send and in `onCleared`. Late callbacks are ignored by the owner check, and `VoiceInput` holds the application context.
  - The manifest marks the microphone as not required and declares the `RecognitionService` query.
- **Design system.**
  - The new UI uses `SvCard`, `SvMessageCard`, `SvWorkingRow` and `SvAvatar`.
  - Colours come from `MaterialTheme`, distances from `SvSpacing` and depth from `SvElevation`.
  - It sets no theme, colour, shadow or animation of its own.
  - `AlertDialog`, `OutlinedTextField` and an inline Material icon path are used as other screens already use them.

## Tests: what they prove and what they miss

- The domain's example tests and the two property tests check real behaviour: sums after every prefix of the edit list, that a rejected edit changes nothing, and that unknown names are rejected. They miss H1, because they use no small quantities and no amounts near the maximum. They miss H2, because they check only sums.
- The parser fuzz never nests deeper than three levels, so its "never throws" does not cover L4.
- `SplitAssistantFlowTest` covers:
  - the success criterion, with a faked proxy;
  - dropping an edit;
  - hostile answers;
  - questions;
  - quota;
  - a refused microphone.

  Its check that Undo restores "exactly" compares only the bill and plan (M1). It has no case with a hidden item division, a finished bill or a line break.

## Plan follow-ups (the plan is not edited)

- "`SplitEdit` and `applyEdits` with invariant tests" is built. It needs H1 and H2 fixed before it meets the criterion about odd instructions.
- "Proposals, accept, drop and undo" is built; M1 is open.
- Voice input is built.
- The category and `Insights` are done in the domain. The screens, the export and the recurring bills are the other agent's work.
- The assistant's screenshots are recorded.
- The evaluator's run with the real key is deferred, as decided on 16/11.

## Unresolved questions

1. H2: when an AI edit switches a whole-bill split to by item, should it start from an even split, as the contract's `split_mode` row and the prompt say? Or should it bring back the item assignments the screen remembers? The second needs a new prompt and a change to `describe`.
2. M3: should the app clean bill text for this route as it does for links, or should the proxy accept what the app can store? What should happen to a bill whose currency code is empty or not an ISO code?
3. M1: should Undo restore a finished bill as finished, or should Apply warn before it turns a finished bill into a draft?
4. L1 and L5: should the contract record the domain's choices, or should the domain follow the contract's wording? The choices are that `split_mode` to the current mode changes nothing, and that `item_amount` and `item_quantity` do not switch to by item.
5. L3: should one rounding rule apply to converted amounts across the app, or half to even for the statistics only?

```text
Status: DONE_WITH_CONCERNS
Summary: The review is complete, and the domain and app mostly meet the contract. Two High and three Medium findings should be fixed before the phase lands:
- High: a crash from a model answer the proxy accepts; hidden item divisions returning on an AI item edit.
- Medium: Undo loses the finished status; a line break in the instruction leads to a dead end; bill text the proxy refuses gets a 400.
Concerns/Blockers: The concerns are about the code under review, not the review. No app test was run, as instructed, so the app findings come from reading the code. The domain findings were confirmed by probes in a scratch copy of the tree.
```
