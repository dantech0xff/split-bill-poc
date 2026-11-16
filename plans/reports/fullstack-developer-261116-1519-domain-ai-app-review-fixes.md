# Fixes of the review of the phase's domain rules and the result screen's AI edits

Date: 2026-11-16 (UTC). Branch `feat/phase-07-ai-assistant-insights`. Review fixed:
`code-reviewer-261116-1448-phase-7-domain-ai-app-review.md`, with the decisions the lead made on it,
and, as asked later, finding H2 of `code-reviewer-261116-1448-phase-7-insights-export-recurring-review.md`.

## Outcome

Every finding assigned to me is fixed and committed in 18 commits, and each change of behaviour
comes with a test that fails without it; the two comment fixes need none. One commit, `a2049d0`,
holds only part of a change that `9668a60` completes (see Concerns). An AI answer the proxy
accepts can no longer end the app: the numbers are range-checked and divided exactly in the domain,
an arithmetic failure in an edit becomes a rejection, and the assistant shows a proposal it cannot
work out instead of crashing. An AI item edit on a bill split as a whole now starts from an even
split by item, as the contract says, so what is applied is what the sentence says. Undo brings back a
finished bill as finished. The request builder now only makes bodies the proxy takes: the
instruction goes on one line, and names go the way bill links send them, with the shared text rules
now kept once in `:core:domain`. The voice input stops when the screen stops, words its messages by
the recognizer that failed, and its consent can be taken back in Settings. Finishing a split now
keeps the day's exchange rate the screen showed.

On HEAD (`d8f169b`), `ktlintCheck`, `:core:domain:test`, `:core:designsystem:testDebugUnitTest` and
`:app:testProdDebugUnitTest` all pass with every task rerun. `:app:lintProdDebug` fails, but only on
two `MissingPermission` errors in `notifications/Notices.kt`, which `89c5cd4` (the recurring bills'
fixer) introduced; none of the lint report's issues is in my files. I told the coordinator.

## Findings, changes and commits

| Finding | What changed | Commit |
|---|---|---|
| H1, domain | `BillEditor.evenUnitPrice` divides exactly (`divideAndRemainder`) and checks the price against `MAX_AMOUNT_MINOR` before `longValueExact()`. `SplitEdits.walk` turns any `ArithmeticException` into the rejection "That is more than a bill can hold." The property test now draws quantities from 0.000001 to 999999 on bills and in edits, and amounts near the most a bill holds; against the old code both property tests throw `ArithmeticException: Overflow`. | `f12edaa` |
| H1, app | `SplitAssistantViewModel` works a proposal out inside a guard. One that throws shows "These changes could not be worked out on this bill. Change the split by hand instead." with no amounts and no Apply, and `apply()` applies nothing of it. Committed before the domain fix, so its test failed without it; since the domain fix, no known input reaches the guard, and the same test checks the rejection. | `d87a3b0` |
| H2 | An `item_sharers` or `item_shares` edit, or `split_mode` `by_item`, on a whole-bill split clears the remembered item divisions; the screen's own mode switch still remembers them. The sentence says so: "Beer: shared by Dana and Minh, and every other item by everyone", "Split the bill item by item, every item shared by everyone". The request sends `split.items` only while `whole` is null. Tests: the reviewer's scenario (117,251 / 117,250 / 69,999), the request, a flow test, and a property-test check that no other item's division comes back. | `30a228e` |
| M1 | `SplitOnScreen.replaceSplit` takes the status. Apply passes the bill's own; Undo keeps and restores the one from before Apply. The split screen's `change()` finishes a bill again only when the restored split has no problem. | `ae0f1c8` |
| M2 | `SplitRevisionRequest.instructionOf` puts the instruction on one line: control characters become spaces, runs of spaces one, and the ends are trimmed; it also takes out the formatting characters the proxy takes out. `body` uses it, and the assistant decides what can be sent by the same rule, so text of only such characters is never sent. The field stays multi-line. | `ff499b3` |
| M3 | The merchant, item names, charge labels and participant names go through the shared `oneLine` rule, cut to 200 code points. A name that shows nothing goes empty; such a merchant goes as `null`. The currency code goes as it is. | `1f61167` |
| L8 | `codePointLength`, `takeCodePoints`, `withoutFormatting`, `showsNothing` and `oneLine` (the bill links' `clean`) now live in `core/domain/.../domain/ProxyText.kt`. Bill links and payment requests use them with their own limits and fallbacks unchanged. The revision parser uses `oneLine` after its own step that turns every space a screen shows into a plain one; a name or sentence of only joiners now reads as empty. | `a2049d0`, `9668a60` |
| L1 and L5 | The domain's choices stay. A proposal now has an outcome: kept edits that apply but change nothing say "The changes that are kept leave the split as it is.", which is no error; "None of the changes that are kept can be applied" is kept for when none applies. | `18d7741` |
| L2 | An `item_quantity` equal in value to the item's changes nothing; any other is stored without trailing zeros and with a scale of at least 0. | `1e1ebdf` |
| L4 | `JsonNesting.isWithin` refuses text that opens more than 64 arrays and objects inside one another, strings aside. `SplitRevisionParser.parse` returns null for it. The app's `ProxyApi` reads every line before the parser does, as the review noted, and now reads such a line as a bad response rather than throwing `StackOverflowError`. | `24971e7`, `7bfc924` |
| L6 | A comment where the assistant's state lives says it is lost when the system ends the app, with the unanswered request's id, and that this is accepted. | `d8f169b` |
| L7, stop | `SplitScreen` calls `onScreenStopped()` on `ON_STOP`: listening ends at once, the recognizer is let go, and the words heard so far stay in the field. | `ed77296` |
| L7, message | `VoiceEnd.LanguageUnavailable` carries `speechServiceNext`. It is true only when the phone's own recognizer gave up and a speech service exists; otherwise the note says "This phone cannot turn speech in its language into text. Type the change instead." | `28d8566` |
| L7, consent | The consent is `AppSettings.speechServiceAllowed`, kept by `SettingsStore.allowSpeechService`. `VoiceInput` reads and writes it there. Settings shows "Use the phone's online speech service" once consent was given; turned off, the mic asks again, and the switch stays for the visit. New images `32-settings-speech-service` (light and dark), looked at. | `4fdd86d` |
| L7, fallback | The comment now says the speech service stands in for as long as the screen lasts; the next screen tries the phone's own recognizer first again, so the sound stays on the phone once it can. | `28d8566` |
| L9, unit price | The `DECIMAL64` rounding is gone with the H1 fix: 999,999,999,999,999 for 1.000001 units no longer gets the unit price 999,999,000,000,999. | `f12edaa` |
| L3, rest of L9 | Not mine: the insights, the domain report and the docs. | none |
| Flaky claim test | `ClaimViewModelTest` keeps its requests in a `CopyOnWriteArrayList`; the synchronized list guarded adding but not `any` and `first`. | `2df1ea1` |
| Insights review H2 | `SplitBillViewModel.finish()` keeps the rate the bill is shown at when it has none of its own. A typed rate stays, a bill finished without a known day rate keeps none, and opening or showing a bill still writes nothing. Tests on the real view model. | `1828206` |

H1 also asked for range checks wherever an AI edit's numbers reach exact arithmetic. Besides
`evenUnitPrice`, I checked `BillEditor.lineAmount` (already checked), `SplitEdits.chargeAmount` (at
most the items' total) and the calculator's `divideProportionally` (in `BigInteger`, each part at
most the amount). None can overflow.

## Tests

| | Before (`dab1d9d`) | After (`d8f169b`) |
|---|---|---|
| `:core:domain:test` | 185 | 198 |
| `:core:designsystem:testDebugUnitTest` | 3 | 3 |
| `:app:testProdDebugUnitTest` | 306, one failing (the claim test's flake) | 340, none failing |

I added 28 tests: 12 in the domain and 16 in the app. The rest of the growth is the other agents'.
- Domain: `ProxyTextTest` 4, `SplitRevisionParserTest` 1, `SplitRevisionRequestTest` 3,
  `SplitEditsTest` 3, `BillEditorTest` 1. I also extended both property tests.
- App: `SplitAssistantFlowTest` 6, `ProxyApiTest` 1, `VoiceInputTest` 2, `AssistantListeningTest` 1,
  `SettingsScreenTest` 2, `BillLifecycleTest` 2 and `ScreenGalleryTest` 2.

No existing screenshot was re-recorded. For the L7 stop and H1 fixes, I ran the new tests against the
old code in a scratch copy, and they fail there.

## Concerns

- **One commit does not build.** `a2049d0` holds only the removal of the app's `ProxyText.kt`. Its
  `git add` failed on that already-staged path, so the rest did not go in. The proxy's fixer had
  committed `b0b019f` on top before I saw it, so I did not rewrite history; `9668a60` completes the
  change. The app does not compile at `a2049d0` and `b0b019f`. Every other commit of mine passed the
  checks on its tree.
- **Where the checks ran.** The other fixers' uncommitted work made the shared checkout fail to
  compile at times. So from the H2 commit on, I ran every check on an exported copy of HEAD plus my
  files, in the scratchpad. Their commits interleave with mine, so each run covered HEAD of the
  moment.
- **Flakes outside my files, passing on rerun.**
  - Once, `BillLifecycleTest` got `UncaughtExceptionsBeforeTest`: a `PaymentMethodDao.all` query ran
    after its test closed the database. It most likely leaked from `RequestMoneyViewModelTest`, which
    ran just before.
  - Once, `GroupsGalleryTest` "a request link with its payment code" threw
    `CalledFromWrongThreadException`.
- **Consent from before.** The speech service's consent moved from the `voice` preferences file to
  the settings file. A phone that said Use it on an earlier build of this unreleased phase is asked
  again.

## Open questions

1. **Currency codes.** A currency code other than "" that is not three letters A to Z, such as
   "VNĐ" from a reading, still makes the proxy refuse a revision. Should the app send "" for it, or
   should the proxy take it too?
2. **Using the day's rate again.** On a finished bill, `useDayRate` takes a typed rate away and keeps
   none until the split is finished again. Should it keep the day's rate there instead?
3. **Names that show nothing.** A name of characters that are not format characters yet show
   nothing, such as U+3164, still passes (part of L4). Should both the domain and the proxy refuse
   it?
4. **The docs.** `docs/architecture.md` says what was on screen comes back from `SavedStateHandle`.
   It should say that the assistant's proposal does not (L6). The docs are not mine.

```text
Status: DONE_WITH_CONCERNS
Summary: Every assigned finding and the insights review's rate fix are committed with tests, and ktlint and all three test suites pass on HEAD.
Concerns/Blockers:
- Lint fails on HEAD only on two MissingPermission errors in notifications/Notices.kt, from 89c5cd4, not mine.
- Commit a2049d0 holds only a deletion, which 9668a60 completes, so the app does not compile at a2049d0 and b0b019f.
```
