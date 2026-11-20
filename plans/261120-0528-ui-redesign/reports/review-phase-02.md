# Code review: Phase 2 shared components (d6dc06d..4a8bb44)

I found no Critical issues. There is one High (bottom bar over list content on four callers), seven Medium and eight Low. Text contrast holds on every new surface in both themes, and the components match the boards closely.

### Scope
- **Commits:** ed723d4 and 4a8bb44; 32 files, +3,698 / −258.
- **Read in full:**
  - phase-02 and plan.md
  - every changed component
  - `InputFields.kt` before and after
  - the nine `SvBottomBar` callers, `AiConsent.kt`, and the field in `PaymentMethodSheet.kt`
  - both test files
  - the boards Main, HomeEmpty, DeleteAccount, Result, Settings, JoinGroup and Insights
- **Framework checks:** I read the bytecode of the pinned libraries with javap, read only: ui-android 1.12.1, foundation 1.12.1 and the Material3 1.5.0-rc01 Scaffold.
- **HEAD moved during the review.** A new commit, `dbc1584` ("end the lists above the floating bottom bar", 11:54), landed after 4a8bb44 and changes four bar callers. I reviewed the range and treat `dbc1584` as a follow-up.
- **The screen captures are older than the code.** The p2a screens were taken at 10:02, before both 4a8bb44 and `dbc1584`. The p2b catalog captures (11:33) match 4a8bb44.

### Overall
The weak points are three:
- the contract with callers about content under the floating bars;
- tests that look stronger than they are;
- colour and large-text cases that the ground mechanism (`LocalSvGround`) does not cover.

---

### Findings, most severe first

**1. High: the floating bottom bar covered list content on 4 of the 9 callers, and its guard test could not see it**
- **Where:** `ui/editor/BillEditorContent.kt:85`, `ui/insights/InsightsScreen.kt:133`, `ui/split/RequestMoneyContent.kt:114`, `ui/split/ResultContent.kt:186` (line numbers at 4a8bb44). Test: `ComponentBehaviourTest.kt:277-301`.
- **Problem:**
  - These four lists used `LazyColumn(Modifier.fillMaxSize(), contentPadding = padding)`. The list runs under the bar's slot, and only its end is padded.
  - The old bar was an opaque full-width strip, so this never showed. The new pill has transparent 20 dp side margins and a 16 dp gap below, so rows slide under it and stay visible around it.
  - So the phase's premise ("in all nine places content ends above the bar") was false, and ed723d4's line "None of its nine callers changed" did not hold.
- **Failure scenario:**
  - In `p2a/final/10-requests-sent.png`, a "Send" button is half-covered by the pill.
  - In `05-result-top.png`, item rows are drawn in the 16 dp strip under the pill.
  - According to `dbc1584`'s commit message, the device accessibility check rejected the uncovered 26 dp strip as a touch target.
  - I ruled out a capture artefact: Material3's Scaffold does pass the bar height as bottom padding, `LazyColumn` clips (via `clipScrollableContainer`), and the catalog's scrolling chip row is clipped exactly at its padded edge.
- **Why the test missed it:**
  - The test builds its own Scaffold with the same `contentPadding` pattern.
  - It only checks the last row after `performScrollToNode`, and `contentPadding` always passes that check.
  - It passed on 4a8bb44 and would pass again if a caller regressed.
- **Smallest fix:**
  - The lists themselves are fixed in `dbc1584`.
  - Still to do: assert the scroll container's own bounds, e.g. `compose.onNode(hasScrollAction()).getBoundsInRoot().bottom <= bar.top`. That fails for `contentPadding` and passes for `padding`.
  - Run that assertion on at least one real caller rendered inside `SvSteps`, such as `RequestMoneyContent`.
  - Recapture the screens.

**2. Medium: every touch-target assertion in the new tests is tautological**
- **Where:** `ComponentBehaviourTest.kt:320-321`, `364-366`, `521-522`, `571-572`.
- **Problem:** `touchBoundsInRoot` pads any node that has an OnClick action up to the 48 dp minimum. I confirmed this in ui-android 1.12.1: `SemanticsNode.getTouchBoundsInRoot` → `getUseMinimumTouchTarget` (true when OnClick is present) → `NodeCoordinator.touchBoundsInRoot()`, which adds `calculateMinimumTouchTargetPadding`.
- **Failure scenario:** a 10 × 10 dp clickable passes all four checks. The 26 dp device failure in finding 1 is exactly what these checks claim to guard against.
- **Smallest fix:** assert the reserved layout size instead, e.g. `fetchSemanticsNode().layoutInfo.width/height >= 48.dp`. That size includes Material's `minimumInteractiveComponentSize`, so `SvSmallAction` (44 dp drawn, 48 dp reserved) still passes.

**3. Medium: the text field's docs and test promise more keyboard privacy than Compose delivers**
- **Where:** `SvFields.kt:55-56` says a field "can turn off the keyboard's corrections and suggestions"; the test at `ComponentBehaviourTest.kt:638` only checks the auto-correct flag.
- **Problem:**
  - In foundation 1.12.1, `EditorInfo_androidKt` sets `TYPE_TEXT_FLAG_AUTO_CORRECT` (32768) only when auto-correct is on.
  - It never sets `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (524288: zero occurrences in the class) or the no-personalised-learning flag.
- **Failure scenario:** an account-number field built as the docs describe still shows keyboard suggestions, and keyboards such as Gboard may learn the number. That is exactly the plan's stated threat ("số tài khoản đi vào từ điển của bàn phím"). This already applies to `PaymentMethodSheet` today; phase 6 will move it onto this component.
- **Smallest fix:**
  - Correct the KDoc.
  - For account numbers and IBANs, choose a password keyboard type, which keyboards treat as sensitive. The text stays visible because `SvTextField` applies no visual transformation.
  - Make the test assert that choice.

**4. Medium: `SvMessageCard` ignores the ground, so inside sheets and cards it is white on white and errors lose their container**
- **Where:** `SvMessageCard.kt:41-47`. Callers: `groups/AddToGroupSheet.kt:83, 91, 98` (all errors, inside an `SvBottomSheet`) and `split/AssistantContent.kt:92, 95, 152` (152 is an error, inside an `SvCard`).
- **Problem:** the card always uses `SvElevation.Level1.surface`, which is the same colour as the sheet and card around it (white in light, `#1C1C21` in dark). Before this phase, errors sat on a tinted `errorContainer` box.
- **Failure scenario:** a "bill was deleted" error in the add-to-group sheet now has no visible card; only the 36 dp red tile marks it.
- **Smallest fix:** when `LocalSvGround.current == SvGround.Card`, draw the card in `SvGround.Card.quiet` (`#F0EFEA` light, `#2A2A31` dark; 16.4:1 and 12.9:1 with its text). Keep white on the page and on the bar.

**5. Medium: at 200% text an amount is cut, so the phase's own success criterion fails**
- **Where:** `CatalogScreen.kt:649-659` (Date and Total side by side) and `SvFields.kt:150-163` (the value box next to the suffix).
- **Problem:** `catalog-large-text` shows the Total field as "9026" plus half a "6" for 902664, next to the "VND" suffix. Phase-02 requires "catalog-large-text_actual.png không có chữ nào bị cắt". A single-line field scrolls rather than wraps (Material behaved the same), but nothing tells the user the value is cut.
- **Failure scenario:** a low-vision user at 200% reads "2256" in the 148 dp amount fields of the split sheet (`DivisionInputs.kt:82, 91`, which have a suffix), or a partial price in the 80 dp and weighted fields of the bill editor.
- **Smallest fix:**
  - In this phase, either record the exception or make the catalog's Total field full width, as `TotalEditor` uses it.
  - In phases 5 and 6, lay narrow number fields out in a `FlowRow`, or move the currency code into the label, at large text sizes.
- Evidence: `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/review-crops/total-200.png`

**6. Medium: in icons-only mode the selected tab is shown by colour alone**
- **Where:** `SvNavBar.kt:146` (colour) and `:174-183` (label hidden).
- **Problem:** at 200% the labels drop, so the bold label is gone too. What remains is lime `#C9F73A` against `#C6C5CE`: 1.37:1 apart in light, and 1.61:1 against `#B7B6BF` in dark. That fails WCAG 1.4.1, for exactly the users who get this mode.
- **Smallest fix:** in icons-only mode, add a non-colour cue, such as a 4 dp lime dot or pill under the selected icon. The normal-size look stays as on the board.
- Evidence: `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/review-crops/lt-navbar.png`

**7. Medium: no capture shows the migrated input fields, and the editor's fields misalign at 200%**
- **Where:** 13 call sites: `BillEditorContent.kt:91, 100, 107, 204, 219, 226, 235, 260, 269, 298`, `DivisionInputs.kt:76, 85`, `SplitSheets.kt:361`. The misaligned row is at `BillEditorContent.kt:218`.
- **Problem:**
  - `20-edit-bill.png` still shows Material outlined fields with floating labels, so the switch to `SvTextField` (labels above, about 20 dp taller, labels that wrap) was never captured, at either text size.
  - In the item row (Qty 80 dp, then two weighted fields of about 122 dp), "Price each" wraps at 200% and "Amount" does not. The row is top-aligned, so the Price box sits one label line lower than its neighbours.
  - The delete icon on the row above is centred on label plus box, not on the box.
- **Smallest fix:**
  - Recapture `20-edit-bill` (normal and large text), the percent and amount sheets, and screens 05 and 10 after `dbc1584`, before sending images to the owner.
  - Use `Alignment.Bottom` on the three-field row.

**8. Medium (needs a decision): the nav bar's documented usage is the pattern that failed in finding 1**
- **Where:** `SvNavBar.kt:72-75`. The phase also prescribes it ("màn gốc đặt contentPadding đáy cho danh sách").
- **Problem:** lists scroll under the floating nav bar, and only their end is padded. That is the setup that produced the uncovered 26 dp strip rejected on the device. The test at `ComponentBehaviourTest.kt:399-430` likewise checks only the end of the list.
- **Failure scenario:** in phase 3, the device accessibility check flags a row half under the nav bar on a root screen.
- **Options:**
  - (a) Root lists end above the nav bar, which gives up content scrolling under it.
  - (b) Keep the planned design and scope the device check to positions where no control is partly covered.
  - Either way, add a mid-list assertion. This is a plan decision for the lead or owner, not one to make silently here.

**9. Low: catalog captures at mdpi in 9000/15000 dp windows**
- **Where:** `ComponentCatalogTest.kt:24-27`, `:39-44`.
- **Assessment:**
  - **The memory reason is real.** Gradle test workers default to a 512 MB heap and no `maxHeapSize` is set. An xhdpi 9000 dp capture is 824 × 18000 px, about 59 MB per buffer, and compare mode holds several.
  - **What the fix costs:** half the linear resolution, exactly where this phase checks 1–2 dp marks.
  - **What it breaks:** the catalog now uses a different density from every other gallery (xhdpi), and phase 8 will freeze that into reference images.
  - **What it risks:** silent truncation. Light and dark content ends only about 230 dp above the bar zone, and nothing asserts that the content fits the window.
  - **What it keeps:** pixel comparison still catches 1 dp changes.
- **Sounder fix:** `testOptions.unitTests.all { it.maxHeapSize = "2g" }` (the Gradle daemon already runs with `-Xmx3g`), and/or capture the catalog in sections at xhdpi, plus a check that the content fits the window.

**10. Low: grounds that would give wrong colours, though no current caller hits them**
- `SvCard.kt:40-42`: a card with a custom colour still provides the plain card ground. On the five primaryContainer and errorContainer cards (`ResultContent.kt:511`, `HomeScreen.kt:149`, `InsightsScreen.kt:248`, `ProScreen.kt:190`, `BillEditorContent.kt:320`), avatar rings come out white and quiet controls `#F0EFEA`.
- `SvCard.kt:88`: in dark theme a Level3 card is the same colour as a quiet control on it (1.00:1); Level2 gives 1.10:1. `AssistantContent.kt:179` and `RequestMoneyContent.kt:208` use Level2 cards.
- `SvFields.kt:125, 174-179`: the field's label and supporting text ignore the ground. On the light bar that is 1.0:1; on violet, 2.9:1.
- Raw Material dialogs and popups declared inside a bar, hero or tile inherit that ground.

**11. Low: smaller issues in the nav bar and avatar stack**
- `SvNavBar.kt:91`: `BoxWithConstraints` is a subcompose layout and crashes under intrinsic measurement (e.g. inside a `Row(Modifier.height(IntrinsicSize.Min))`).
- `SvNavBar.kt:193`: the count badge's text is fixed at 11 dp and does not grow with the text size.
- `SvAvatar.kt:39, 46`: two-letter initials in a 32 dp tile are partly hidden by the next tile's 8 dp overlap ("TM" in the hero). The boards use one letter and a white 2 px ring; here the ring defaults to the ground colour, so it is invisible on violet.

**12. Low: typography and large text**
- `SvHero.kt:133`: the hero amount keeps the display cut of Bricolage at every size, while `SvTypography.kt:23-29` says that cut is used "only from 56sp".
- `SvTile.kt:101`: at 200% the label breaks mid-word ("Novemb / er").

**13. Low: semantics**
- `SvSplitButton.kt:79-88`: the arrow is a checkable Surface with no role, so TalkBack reads it as a toggle ("not checked"). This was the same before. Role.Button with an expanded/collapsed state description (as `SvFabMenu` has) would be clearer.
- `SvFields.kt:286`: the error description is hard-coded English ("Invalid input"); Material used a localised string.

**14. Low: one-row chips**
- `SvChoiceGroup.kt:89-95`: a one-row `SvChoiceChips` does not scroll the selected chip into view, so a selected last option can start off screen.

**15. Low: caps, coverage and docs**
- **Length caps:** the caps of 3 (currency) and 20 (numbers) passed from `InputField` to `SvTextField` are no longer tested anywhere.
- **Default length:** `SV_TEXT_FIELD_MAX_LENGTH = 80` also applies to multi-line fields. That is a trap for the `PaymentMethodSheet` migration, whose details field allows 500.
- **Dialog not captured:** `SvDialog` never appears in any image (`CatalogScreen.kt:814-859` only has buttons that open it).
- **Stale docs:** `docs/design-system.md:147` still says the large top bar shrinks on scroll.

---

### Test quality
- **Checks that can fail and do guard behaviour:**
  - choice group and chips: role and state
  - person rows
  - split-button menu, on the page and on the bar
  - nav bar: tabs, selection and count. Labels counted 1 versus 0 in the unmerged tree; together the two tests make icons-only meaningful.
  - hero amount: lines, size order, minimum size
  - meter, stat bar and chart: descriptions, with the "N" label cleared
  - tick box, radio and switch state changes
  - a row as one tick box
  - dialog: heading, both buttons, side by side, stacked at 2×
  - keyboard options and `maxLength`
  - tile as one button
  - category icons and colours distinct
  - tappable row
- **Checks that cannot fail:** all four touch-target checks (finding 2).
- **Weak guards:** both bar-covers tests (findings 1 and 8). `hasVisualOverflow` in the hero test only trips if someone adds `maxLines`. The category test checks that icons and colours are distinct, not that each category gets its own.

### Checked and found correct
- Every row of both requirement tables is implemented with the specified tokens (corners, heights, roles).
- `SvLargeTopBar` is split in two, and both callers are updated.
- **Bottom bar:**
  - The bar stays in the callers' Scaffold slot.
  - The main and split buttons turn lime inside it; the quiet button, working row, loading indicator and text take the bar's colours.
  - The consent screen's second answer went from 2.9:1 to 14.1:1 (light) and 9.8:1 (dark).
- **Nav bar:** follows the Main board's geometry and spec: Tab role ×4, `selectableGroup`, count merged into the name ("Requests, 2"), labelled centre button. Icons-only is decided by measuring every label in bold at the real width. The reported height includes insets and margins.
- **Hero amount:** prefix and suffix at 0.54 em, spoken text as content description, shrinks 56 → 20 sp by binary search, then wraps; no `maxLines`.
- **Text field:**
  - It takes every listed parameter, and its defaults equal the old `InputField`'s (80 characters, Sentences, Next).
  - Line height is 26 sp at 16 sp, and "Ẳ QUÁN ỐC BA MIỂN" is intact at 100% and 200%.
  - The label is merged into the field node, so lookups by label still work.
  - Error semantics are set, and the cut never splits an emoji.
  - `InputField`'s typed state, comparison of values, error rule, keyboards and IME Next are all preserved.
- **PaymentMethodSheet:** unchanged. Its own field keeps `autoCorrectEnabled = false` and `take(500)`, and the components it uses kept their semantics.
- **Dialog:** matches the DeleteAccount board's geometry. The buttons switch between side by side and stacked by measurement and mirror in right-to-left layouts. Confirm text reads at 6.40:1 (white on red, light) and 8.19:1 (ink on coral, dark).
- **Meters, chart and ticket:** fill from the start in both directions, are safe with NaN and zero, and the ticket's notch path is correct.
- **CategoryStyle:** the icon `when` covers every category, and the palette order matches the enum.
- **Contrast:** text on every new surface is at least 4.5:1 in both themes. The lowest pairs are lime on violet at 5.18, white over the hero circle at 5.41, and red on the page at 5.91.
- **Motion and boundary rules:** new animations are finite and follow the system's animation scale, durations come from `motionScheme`, and `DesignSystemBoundaryTest`'s rules are respected.
- **Right-to-left:** paddings use start and end, offsets are direction-aware, the hero circle is mirrored, and the back arrow is auto-mirrored.
- **Callers of changed components (app main, call sites):**

  | Component | Calls |
  |---|---|
  | `SvMessageCard` | 38 |
  | `SvCard` | 20 |
  | `SvListRow` | 20 |
  | `SvPrimaryButton` | 20 |
  | `SvSectionHeader` | 19 |
  | `SvLoadingIndicator` | 13 |
  | `SvBottomSheet` | 12 |
  | `SvAvatar` | 10 |
  | `SvBottomBar` | 8, plus the catalog |
  | `NumberField` | 8 |
  | `SvEmptyState` | 8 (all on the page) |
  | `SvSplitButton` | 6 (`ResultContent.kt:730/739/749`, `ClaimScreen.kt:417/427/434`) |
  | `InputField` | 5, plus 1 internal |
  | `SvChoiceChips` | 5 |
  | `SvPersonChoiceRow` | 5 |
  | `SvChoiceGroup` | 4 |
  | `SvPrimaryFab` | 3 |
  | `SvWorkingRow` | 2 (`NewSplitContent.kt:274`, `AssistantContent.kt:144`) |
  | `SvPersonPickRow` | 1 (`SplitSheets.kt:230`) |
  | `SvBillPhoto` | 1 (`NewSplitContent.kt:183`) |
  | `SvSuccessMark` | 1 (`RequestMoneyContent.kt:124`) |
  | `SvLargeTopBar` | 1 (`HomeScreen.kt:67`) |

### Unresolved questions
- Has `device-tests.yml` been run and read on `dbc1584`, the phase's final commit now? The phase requires it.
- Should the root screens' lists run under `SvNavBar` (finding 8)?
