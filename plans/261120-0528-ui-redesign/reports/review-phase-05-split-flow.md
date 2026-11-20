## Code review: commit 8593533 (phase 5, steps 5 to 14)

### Scope
- 38 files, +2629 / −954 lines:
  - the rebuilt screens in `ui/split` and `ui/editor`
  - 13 design-system files
  - 11 unit-test files and 3 device-test files
  - `strings.xml`
- I reviewed the commit with `git show`, not the working tree. Nothing was edited, staged or committed, and I ran no Gradle task.
- Device tests and journeys were only read.
- Paths below are relative to `/home/user/split-bill-poc`, with line numbers as of 8593533.

### Overall assessment
Nothing in this commit changes what a bill splits into or what is sent.
- Every callback on the result screen, the three sheets and the editor calls the same view-model and `BillEditor` functions with the same arguments.
- `SplitFlowTest` still checks the Result board's shares: ₫250,993, ₫400,680 and ₫250,991, adding up to ₫902,664.
- `ocrDocument` is still written and still sets `billWasRead`, and a new test now reopens the bill from storage.

The findings are about layout, accessibility, differences from the boards and how strong the tests are. None of them is a data bug.

### Findings, most severe first

**M1 (Medium). A long label, not the number, pushes a part field off its row at normal text size.**
- Where:
  - The side-by-side test measures the whole field's widest one-line width: `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/components/SvFields.kt:254-258`.
  - That width includes the label, which is drawn on one line inside the field's decoration box: `SvFields.kt:144-147`.
  - It is used by the result rows (`SvListRow.kt:168`, called from `app/src/main/java/com/dantech/splitvibe/ui/split/ResultContent.kt:700` and `:711`) and by the sheet rows (`app/src/main/java/com/dantech/splitvibe/ui/split/DivisionInputs.kt:126`, `:143`).
- Scenario: a split by percent or by amount, or "Several people" in Who paid, with someone added from contacts. I measured with the app's own font file (Be Vietnam Pro Bold, 13sp), script in `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/scripts/measure.py`:

  | Text | Width at 100% | Fits 148dp? |
  |---|---|---|
  | "Percent for Christopher" | 155dp | no |
  | "Percent for Trần Thị Mai" | 157dp | no |
  | "Percent for Nguyễn Văn An" | 174dp | no |
  | "Percent for Bao Tran" (the board's own name) | 136dp | yes, but 156–176dp at 115–130% text |
  | the number itself, "33.33 %" | 98dp | yes |

  So at 100% text those rows drop their field below the name across the full width, while the You, Minh and Lan rows keep theirs beside the name. The list ends up ragged.
- Why it matters:
  - The old code gave the field a fixed 148dp and let the label wrap.
  - It contradicts the docs of `SvFieldRow` and `SvListRow` ("while what it holds fits").
  - It differs from the SplitByPercent board (124px field beside the name, 11px label) and the SheetWhoPaid board (no visible label at all, only an aria-label).
  - Every test uses short names, so nothing catches it.
- Fix:
  - Keep the label out of the fit test. For example, give the label `Text` a layout modifier whose maxIntrinsicWidth returns its minIntrinsicWidth: the label then wraps inside the slot, and `SvFieldRow`'s bottom alignment still lines the boxes up.
  - Or have `SvFieldRow` measure only the box content.
  - Add a `ComponentBehaviourTest` case at 100% text with a long label that asserts side by side.

**M2 (Medium, accessibility). Opening a row in the editor drops TalkBack's focus.**
- Where: `app/src/main/java/com/dantech/splitvibe/ui/editor/BillEditorContent.kt:175-179` and `:204-208` (a closed row is replaced by an open one); `ClosedRow` at `:384-420`.
- Scenario:
  - A TalkBack user double-taps "Nghêu hấp sả, 1 × ₫95,000, ₫95,000" with the action "Change Nghêu hấp sả".
  - The focused node disappears and different nodes replace it, so focus is lost and the user has to search for the fields.
  - The same happens with the row that opens itself after "Add an item".
  - Before this commit, every field was always on screen.
- Fix: when a row opens from a tap or an add (not when state is restored), request focus on its first field with a `FocusRequester` and a `LaunchedEffect`.

**M3 (Medium, needs your decision). The proposal card sits somewhere other than on the ResultProposal board.**
- Where: `ResultContent.kt:283-315`.
- The board (390×1330) puts the "Proposed changes" card right after the total, above "Each person pays".
- The code puts it after the people, the tally line and the "Change the split" field. The comment gives a reason ("in view of the field"), which is a defensible UX choice, but it departs from an approved board in the main flow, and the 31-assistant-proposal screenshot will not match the board.

**L1. Request screen: the "Your name on the requests" field can end up under the keyboard.**
- Where: `RequestMoneyContent.kt:116` has no `Modifier.imePadding()`. The app is edge-to-edge (`MainActivity.kt:34`, `AndroidManifest.xml:43` adjustResize), and the new split, result and editor screens all pad for the keyboard.
- When signed in, the field (`:165`) now sits below a taller ticket, about a third of the way up from the bottom on a Pixel 6, which is where the keyboard opens. The list does not shrink, so the field is not lifted above it.
- This was already true before the commit; the taller ticket makes it more likely. Fix: `Scaffold(modifier = Modifier.imePadding(), …)`.

**L2. Some assertions cannot fail, or pass without testing what they claim.**
- `BillSafetyTest.kt:281` (`last.bottom <= bar.top`) follows from `:278`. `getBoundsInRoot` is clipped to the list, so a row can never report a bottom below the list's frame.
- `SplitFlowOnDeviceTest.kt:146` has the same clipping problem. It holds whenever the list frame does, even if the field is mostly hidden.
- `SplitFlowOnDeviceTest.kt:148` and `:157-163`: when no soft keyboard appears (a hardware-keyboard device, or an image without a keyboard app), `keyboardTop()` returns the bottom of the screen and the check passes as a pass.
- Fix: use unclipped bounds (`fetchSemanticsNode()` position plus size), and add `Assume.assumeTrue` that the keyboard is visible, so a run without one is reported as skipped.

**L3. The new 200% gallery test asserts nothing.**
- Where: `ScreenGalleryTest.kt:257-290` only takes the 18, 19 and 20 large-text pictures.
- With no reference images before phase 8, compare mode cannot fail. Only `SvFieldRow` on its own is tested at 200% (in `ComponentBehaviourTest`), not how the screens use it.
- Fix: assert that the "Percent for You" field and the Who paid amount fields span the row at 200%.

**L4. The total block lacks the board's people count.** `ResultContent.kt:499-522`. The Result, ResultProposal and sheet boards show the date and "3 people" side by side; only the date is drawn.

**L5. The request screen has no "Back to the split" button.** The AskForMoney board has a 44px round back link at the top. This was already missing before the commit, and the system Back still works.

**L6. A suggested person's button says only "Add".** `SplitSheets.kt:124`. The SheetAddPerson board labels it "Add Bao Tran" for screen readers. With several suggestions plus the sheet's own Add button, TalkBack lists several identical "Add" buttons. Fix: a content description "Add %1$s" (one new string).

**L7. The three tiles for adding a bill can differ in height.** `NewSplitContent.kt:208-258` does not equalise them. On a 360dp phone, "Choose photos" (about 85dp of text in about 84dp of room) wraps, so that tile grows; the board shows three equal 72px tiles. Fix: `Row(Modifier.height(IntrinsicSize.Max))` with `fillMaxHeight()` on each tile.

**L8. Long shop names now wrap in the top bar.** `ResultContent.kt:154` uses `SvTopBar` (26sp, wraps by design). The old bar showed one line with an ellipsis, and so does the Result board (20px, one line). A long name read from a receipt takes two or three lines. This is a design-system decision for you; I am only noting that the behaviour changed.

**L9. A latent crash in `DivisionValueRow`.** `SvFields.kt:244` requires one child per width, but `DivisionInputs.kt:115` draws nothing for the Equally and By item modes, so `DivisionValueRow` (`:125`) would crash if called in those modes. No caller does today. Fix: a guard, or document the precondition.

**Nits**
- `ScreenGalleryTest.kt:526-529` clears FileProvider's private `sCache` field by reflection. It works today, but every test in that class would fail loudly if androidx.core renamed the field.
- The large-text catalog picture is now drawn at ldpi (0.75x), which shows less detail for judging 200% text by eye. The catalog tests also lack the "nothing left to scroll" check that 13-result-whole-page now has.
- The instruction field now capitalises the first letter of each sentence, because it takes `SvTextField`'s multi-line default (the old field had none). Harmless.
- The new Send icon (`SvActionIcons.kt:63`) does not flip in right-to-left layouts. Not a regression: that button used the Share icon before.

### Checked and found correct
- **What a bill splits into and what is sent:**
  - The calls are unchanged: the "You" switch, setting each person's part, splitting an item, who paid, and every editor function for items, charges and the printed total.
  - The logic of the two sheets is unchanged.
  - The request texts are unchanged. The shared picture is now the ticket on the page colour, and the gallery test reads the actual PNG.
- **Length caps and keyboard options kept:**
  - Add someone and rename: 80 characters, capitalised words, Done adds.
  - "Your name on the requests": 60 characters, capitalised words.
  - Instruction: 1,000 characters, the same cap as the view model.
  - Change-the-split field: twice the view model's limit, so the view model's own limit still applies first; Send on the keyboard is kept.
- **`ocrDocument`:** only the screen's getter was removed. Reading a bill still writes it, and opening or restoring a bill still sets `billWasRead`. The new `BillSafetyTest` would fail if the flag were lost.
- **The "You" switch:** a screen reader reads the row as a switch with its state in words, and the part field sits outside the switch. `SplitFlowTest`, `ManualEntryJourneyTest` and `ComponentBehaviourTest` all prove it.
- **The editor's open row** is kept by id. Ids only ever count up, so a removed id is never reused and adding always opens the new row. The new `BillLifecycleTest` would fail if the open row were kept by position. Names save on every keystroke. Only half-typed numbers (such as "12,") are dropped when a row closes, as when leaving the editor before.
- **Bars:** the result screen and the editor use the Scaffold's bottom bar with keyboard padding, and their lists end above the bar.
- **The thirteen Material calls are gone** from `ui/home`, `ui/split` and `ui/editor`. `SvDialog` handles Back, a tap outside and the dismiss button the same way the old dialogs did.
- **`DesignSystemBoundaryTest`:** the new font-family rule finds nothing, and the rebuilt screens set no colour, shadow, animation, theme, font size or font weight of their own.
- **Other callers of the changed components see no change:**
  - `SvListRow`'s refactored card wrapper is identical to the old one.
  - `SvSmallAction` keeps its colours.
  - `SvChoiceChips` computes the same span when no padding is given.
  - `SvTextField`'s new defaults behave as before.
- **Strings:**
  - The removed strings have no references left, and no string is left unused (three were unused before this commit).
  - "Claude" remains only in `ai_consent_where`.
  - The consent text is unchanged, and its answers sit below the scrolling text (now asserted by `AiConsentGalleryTest`).
  - Every label the tests and journeys rely on is kept, and so are `SPLIT_SURFACE` and `SEND_SURFACE`.
- **Finders:**
  - The sideways row of ways to split adds a second scrollable element to the result screen, and no test on that screen still searches for "any scrollable" (`hasScrollAction()`).
  - `fillInTheBill` waits for the row that opens itself.
  - `assertShare` works on the merged row because the amount exposes its final text to tests.
  - `myRow` matches exactly one node.
  - The SampleBill, DeclinedAi and PaymentRequest journeys still find their elements, as far as reading them shows.
  - `ReducedMotionTest`'s 412×915 screen shows "Use the sample bill" without scrolling.
- **Gallery test setup:** the screens are now torn down before the database and server close, and clearing the FileProvider cache matches Robolectric's new cache folder for each test. The shared-picture check sees only its own share sheet. The whole-result-page picture now fails if the page outgrows the taller window.

### Plan follow-ups
- Steps 5 to 13 look implemented.
- Two things cannot be confirmed from the commit: that the 200% pictures were looked at (step 14), and the device run on API 34.
- Suggested order: fix M1 with a long-label test, fix M2, decide M3, then L1 to L3.

### Unresolved questions
1. Should the proposal card follow the ResultProposal board (M3)?
2. Are the missing "3 people" tag and back button (L4, L5) deliberate?
3. For someone who paid the whole bill, the Result board shows a "Paid the bill" tag, while the SplitByPercent board shows "Pays ₫225,666 · paid ₫902,664". The code always uses the tag. Which board is right?
4. The SheetWhoPaid board has no visible labels on the amount fields; the app shows "Amount for <name>" above each one. Is that intended?
5. Does the emulator in `device-tests.yml` (pixel6Api34) show a soft keyboard? If not, half of the new device test proves nothing (L2).
6. Will the accessibility checks on the device flag the ways-to-split buttons that the sideways row cuts in half? Only the device run can tell.

Status: DONE_WITH_CONCERNS
Summary: Nothing in this commit changes what a bill splits into or what is sent. The main concerns are part fields that leave their rows at normal text size because of their labels, TalkBack losing its place when an editor row opens, the proposal card's position against its board, and a few device-test assertions that cannot fail or pass without a keyboard.
Concerns/Blockers: No Gradle or device run was made, as instructed.
