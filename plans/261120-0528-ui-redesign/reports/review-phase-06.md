## Review of 4b19a97: phase 6 (Groups, Requests, ways to get paid, bill links)

I found no blocking defect in the requested behaviour: dialogs, menus, settle up, members, join requests, bills, paying, confirming, request links, the QR code and the claim screen all behave as before. One new privacy problem needs a decision before release (finding 1). There are two medium defects, one of them a pre-existing bug that a new test claims to guard but doesn't. The rest is minor.

**How I reviewed:** I read the before and after versions of all 19 files with `git show`, the plan and phase file, and the nine boards. For the keyboard and autofill question I disassembled the Compose libraries the BOM (2026.09.00) actually resolves, foundation and ui 1.12.1. I did not run Gradle, so no test, lint or type-check results are claimed. I edited nothing.

**Your working tree changed while I was reviewing.** At the start only the phase-6 file was modified. Now 11 more files are modified: `HistoryScreen.kt`, `RecurringScreen.kt`, `RepeatBillSheet.kt` and design-system files including `SvFields.kt` (where `label` became nullable), `SvMeter.kt`, `SvTile.kt` and `SvButtons.kt`. Another session appears to be doing phase 7 in this same checkout. I re-checked that every design-system component I relied on matches the commit.

## Findings, most severe first

### High

**1. The password keyboard turns three payment fields into autofill password fields.**
- Where: `ui/payments/PaymentMethodSheet.kt:113`, `:135`, `:159`, `:307`; the test helper at `PaymentMethodSheetTest.kt:100-107`.
- Mechanism:
  - In foundation 1.12.1, the text field sets the autofill hint `ContentType.Password` whenever the keyboard type is `Password` (or `NumberPassword`). It does this from the keyboard type alone.
  - In ui 1.12.1, every field with an autofill hint is tracked. Once all of them leave the screen, Compose calls `AutofillManager.commit()` by itself, and the fields are reported with the password hint.
  - Nothing in the app or design system opts out of autofill.
  - Before this commit, the account number (`Number`) and IBAN (`Text`) had no hint; UPI (`Email`) was an email field.
  - TalkBack is unaffected: the screen-reader password flag is only set for `PasswordVisualTransformation`, so it reads exactly what it read before.
- Scenario: a user whose autofill service is Google Password Manager (the default on most phones) adds a VietQR method, types 257678859 and taps Save, or Cancel. The sheet closes and Compose commits, so the phone may ask "Save password for Split Vibe?" with the account number as the password. If they accept, the account number, IBAN or UPI ID sits in a cloud-synced password vault.
  - Next time, those three fields offer that saved "password", and could fill one method's account into another method's field.
  - A third-party password manager may also offer real passwords there. Once tapped, the password is visible and, if saved, goes out with every request.
  - The new test checks only the keyboard settings, so it cannot catch this.
- Fix options (this touches the planner's recorded decision from the phase-2 review, which did not consider autofill; it is new evidence, so the planner or owner decides):
  - (a) Recommended: keep the password keyboard and opt these fields out of autofill. Add an `SvTextField` parameter that sets `semantics { contentDataType = ContentDataType.None }` on the field's own modifier (ui 1.12.1 treats `None` as "not autofillable"). Assert in `PaymentMethodSheetTest` that the field node carries no `ContentType.Password`, and check on a device with Google Password Manager that Save shows no prompt.
  - (b) Return to non-password keyboard types and accept that keyboards may learn the values.
  - (c) Accept the risk and record it in the plan.

### Medium

**2. The details card can hide the actual payment detail.**
- Where: `ui/requests/RequestsScreen.kt:565-582` (`DetailRow`) and `:592-599` (`detailsOf`).
- Cause: each details line is split at its first ": ". The label `Text` has no width limit and the value has `weight(1f)`, so a label that needs more than one line takes the whole row and the value gets zero width.
  - The "Other" kind writes `"label: details"` (`PaymentRequestBuilder.kt:148`), with a label of up to 40 characters, then the user's free multi-line text (`PaymentMethod.kt:157-161`). So arbitrary user text gets parsed as label and value.
- Scenario: the label is "Vietcombank transfer (personal account)" and the details are 0123456789. A payer at 200 % text sees the label over two lines and no account number; only Copy still has it.
  - At 100 % text, a free line such as "Write this reference so I can match it: PHO24" leaves about 40 dp, so "PHO24" breaks mid-word.
  - The gallery test (`GroupsGalleryTest.kt:165-171`) uses only VietQR's short labels, so it would not show this.
- Fix: cap the label's share of the row, or put the value under the label when they don't fit side by side (the rule `TextsAndActions` already uses). Better still, have the builder return structured lines and keep `details` only for Copy. Add a test with an "Other" method at 2× text.

**3. Unknown group balances still read as zero, and the new test passes for the wrong reason.**
- Where: `ui/groups/GroupScreen.kt:347` (`balances?.get(member.id) ?: 0L`), `:364-368`, `:416`; `GroupHeaderTest.kt:74-86`, `:100-104`.
- Scenario: when the numbers are beyond adding up (the test's own fixture), the header block correctly disappears. But every member row, including "You", still says "settled up" with an empty bar, and Settle up says "Everyone is square."
  - This bug predates the commit and was carried into the rebuilt rows.
  - The test says it fails if the screen says the user stands "any way at all", but it matches "Settled up" exactly and case-sensitively. It therefore misses the rows' lowercase "settled up" and passes.
- Fix: when balances are null, show members without a standing or bar, and give Settle up a hint that the payments can't be worked out (wording for the owner). Make the test match case-insensitively by substring, and assert that "Everyone is square." is absent.

### Low to medium

**4. One requester-name lookup still decodes a group state on the main thread.**
- Where: `RequestsViewModel.kt:148-151`.
- `RequestLinkViewModel.load()` calls `creditorNameOf` → `ProxyCache.get` (`ProxyCache.kt:54-56`), which decodes the whole cached group state on the main dispatcher.
- So the commit message's "names of requesters are looked up off the main thread" is true for the list only.
- The same path runs on main at `AddToGroupViewModel.kt:162` (the conflict case).
- Pre-existing and in `data/`: after every group refresh or change, `GroupRepository.state()` (`GroupRepository.kt:218-222`) decodes the answer, decodes each bill's document to strip it, and re-encodes, all on the caller's dispatcher, which is main for every group view model.
- Fix: decode inside `ProxyCache.get` on `Dispatchers.Default`, which fixes every caller; or wrap the lookup in `withContext(Dispatchers.Default)`.

### Low

5. **Possible flaky assertion in the tiles test** (`RequestTilesTest.kt:148-151`). After waiting for the tiles, it asserts `lists.get() == 2`. But the tiles come from the already-filled cache, while the tab's second fetch travels through OkHttp on another thread, so the count can still be 1. Fix: `await { shown(YOU_OWE) && lists.get() == 2 }`.
   - Also, the comment at `:87` overclaims: the list and the totals both come from the cache the holder filled, so this test cannot tell "reads the totals" from "adds up the list". The unit test at `:160-176`, which builds the totals from a longer list than the one shown, does prove it. Fix the comment.
6. **The device journey's changed line is correct but now checks less** (`PaymentRequestJourneyTest.kt:121-122`). The change was needed, since the amount now appears in the tile too, and it is specific. But nothing on the device checks the new tile any more. Add `onNode(hasText("Friends owe you") and hasText(owed)).assertIsDisplayed()`.
7. **Bar colour differs from the boards** (`GroupScreen.kt:394-399`). The "gets back" bar uses the `moneyIn` colour (#0A6B4E light, #5BE3B0 dark). The Group and DarkGroup boards draw that bar #17B383 in both themes and use #0A6B4E/#5BE3B0 only for the amount text. No token for #17B383 exists.
8. **Pending group is still tappable** (`GroupsScreen.kt:149-171`). "Flat 3B" is still an "Open" button that leads to an error card; the Groups board draws it as a plain card, not a link. This predates the commit, and `GroupsGalleryTest.kt:86` now relies on it being clickable.
9. **String edits the phase doesn't list** (`strings.xml:632-633`, `:688-689`). "owner" became "Owner", "no app yet" became "No app yet", and the join sentence was split into a title and a hint. plan.md says strings.xml wins over the boards unless the phase lists the change. The words are the same and nothing else uses them; record them in the phase file.
10. **Two-way package dependency and a duplicated rule.** `ui/requests` now imports `Money` and `OpenRequests` from `ui/home` (`RequestsScreen.kt:83-84`), while `ui/home` already imports `RequestTotals` from `ui/requests`. Reusing them is right, since it is what keeps the tiles equal to the home, but they belong next to `RequestTotals`. Separately, `GroupStanding` (`GroupScreen.kt:272-301`) re-implements the home tile's wording rule (`HomeSummary.kt:105-111`).
11. **Unverified, 200 % text** (`GroupsScreen.kt:73-84`). The "New group" button sits beside a title that gets whatever width is left, so "Groups" may break mid-word at 2× text. Check the `22-groups-large-text` image.

## Checked and found correct
- **Material calls:** none of the nineteen calls remains, nor any direct Material Button, TextButton, OutlinedButton, TopAppBar, IconButton or DropdownMenu, in the four packages. The design-system module is untouched, and DesignSystemBoundaryTest's rules match nothing new.
- **Dialogs and menus:** "More for this group", "More for Minh", "Take out of the group", "Leave the group" and both dialog texts are unchanged, so GroupDialogsTest needs no edit. Take out and Leave are now red. The Received dialog keeps its settlement id and the confirm-the-request amount.
- **Settle up, members, join requests, bills:** same conditions and actions. Ask and Got it are still disabled while a call runs; remove is owner-only; delete is owner or adder.
- **Requests:** Pay only while not paid; Confirm only once the payer says they paid; the menu items are the same. The pay sheet now names the requester as the card does.
- **Request link:** same states and actions.
- **QR code:** keeps its 4-module white margin, 220 dp / 480 px size and "Payment code for …" label, and is still drawn off the main thread.
- **Payment sheet:** password keyboard on account, IBAN and UPI; auto-correct off on every field; 500-character cap; multi-line details; capitals kept. Nothing is masked, and TalkBack reads what it read before. There is no logging, and the saved-state fields are unchanged. VietQR accounts may contain letters, so losing the number pad is not a regression.
- **Other moved fields:** all keep their caps (60, 3 with capitals, rate) and keyboard options.
- **Requests tiles:** they read `RequestTotals` from the activity holder with the same `settings.localCurrency` as the home (`AppNavHost.kt:518-519` vs `:237`). They show nothing while signed out, while totals are Unknown, or while the list is null or empty. They use the home's own function, so they cannot disagree. Totals are re-read for each session and the cache is emptied on every sign-in, so no account sees another's tiles.
- **Lists above the bar:** Groups and Requests take the Scaffold's padding as `Modifier.padding`. The tests check both the list's frame and its last row against the bar. The claim screen's bar stays in the Scaffold slot: a plain full-width button while picking is open, the floating bar once closed, as the boards draw them.
- **Accessibility:** balances are always in words; bars and decorative avatars are hidden from the screen reader; a paid request carries a "Paid and confirmed" tag; merged cards keep their buttons separate; the group card is one button with a label; the join choices are a radio group; touch targets are 48 dp or more; at 200 % text the request card's actions move below its text.
- **Other:** "Open until" still goes through phase 4's date function. No screenshots were committed. The new list keys cannot collide with server-generated ids. No other test or device journey breaks on the new layout.

## Unresolved questions
1. Has this commit been through the plan's test gate (with `-Pscreenshots=compare`) and the API 34 run of `device-tests.yml`? The phase todo still has the gate unchecked, and I did not run Gradle.
2. Finding 1: opt the three fields out of autofill? This is a planner or owner decision, and it needs a device check with Google Password Manager.
3. Wording for groups whose balances can't be added up (finding 3), and whether a pending group should be tappable (finding 8).
4. Is the owner happy with two zero tiles when every request is paid? That is the planner's default, to be approved through screenshots.
5. Is another session meant to be editing this same checkout during phase 7?

Status: DONE_WITH_CONCERNS
Summary: The commit keeps all the required behaviour, caps, keyboard options, QR, tiles-equal-home and bar placement, but the password keyboard makes payment fields autofill password targets, the details card can hide a payment value, and unknown balances still read "settled up" while a new test claims otherwise.
Concerns/Blockers: Finding 1 needs a planner or owner decision and a device check; no Gradle, test or device run was done in this review, and the working tree is being edited by another session.
