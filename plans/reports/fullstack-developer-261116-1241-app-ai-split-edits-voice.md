# Report: the result screen's AI assistant, weighted calls and voice (app)

## Outcome

The result screen now changes a split by a sentence the user types or says. The AI proposes edits
through `POST /v1/split-revision`; the app works them out on the split on screen with
`SplitEdits.apply` and the calculator, shows each one in words with what everyone pays before and
after, and applies nothing until the user taps Apply. Apply goes through `SplitBillViewModel` as one
change, so the bill is saved and its link kept in step as after any edit, and Undo brings back the
previous bill and plan exactly. AI calls are decimals now (a change takes 0.2 of a call): they are
read from the JSON number's text into `BigDecimal` and shown without trailing zeros. Voice input
uses the phone's own recognizer when there is one, and asks before using a speech service that may
send the sound away.

The checks pass on the branch's HEAD plus these commits, without the other agents' uncommitted
work: `ktlintCheck`, `:core:domain:test`, `:core:designsystem:testDebugUnitTest`,
`:app:testProdDebugUnitTest` and, in a separate invocation, `:app:lintProdDebug` (no new warning).
Each of the five code commits was also checked on its own tree: ktlint, every source set compiling,
and the app's unit tests.

## What the user sees, step by step

1. Under the bill total, a card with a field labelled "Change the split", a mic button and a send
   button. The hint gives examples ("Lan didn't have beer, or add a 10% tip"), says the changes are
   checked before they apply, and that asking takes 0.2 of a call.
2. The user types, or taps the mic and speaks. What is heard goes into the field and waits there.
3. Sending (the button or the keyboard's send key) shows "Claude is working out the changes…" with
   Cancel, and the field is locked meanwhile. A request that was cancelled or failed keeps its
   request id when sent again, so the proxy does not charge it twice.
4. The answer arrives as a "Proposed changes" card with:
   - what was asked, and the AI's explanation;
   - each edit in words (`SplitEdits.describe`, the list form, on the same bill and plan as
     `apply`), each with a tick that drops it or keeps it again;
   - under a kept edit that cannot be applied, its reason; how many edits could not be read;
   - what each person pays before → after. Changed amounts are in the accent colour, and someone
     added or taken off shows a dash on one side;
   - any problem the split would have afterwards.
   Dropping or keeping an edit works everything out again at once.
5. Apply is enabled only when the kept edits change the split. After Apply, the card gives way to
   the AI's explanation with an Undo button. Undo is offered while the split on screen is still what
   the change made it; any other change of the bill or plan ends the offer. A pending proposal and
   Undo survive rotation, the editor, Settings and the bill's link screen; leaving the split screen
   ends them.
6. A question from the AI replaces the proposal, even when edits came with it. It shows above the
   field, the field is labelled "Your answer", and the answer goes with what was asked and the
   question: "<instruction> (<question> <answer>)", shortened to fit 500 code points. "Ask something
   else" drops the question.
7. Errors show under the field, in the bill read's kinds, worded for a change:
   - no calls left, with "Open settings" when signing in would help (the bill read's rule, now
     shared);
   - sign-in run out, app too old, busy, too many requests, refused, too long, timeout, no
     connection;
   - AI switched off, said once as a notice above the field; sending still goes out, as for bill
     reads, because the proxy may have turned AI back on;
   - a 400 from the route: "This bill cannot be changed this way. Change it by hand instead."
   When the build has no proxy address, the assistant says so and cannot send.
8. An answer with no edits and no question shows the AI's explanation, or "Claude found nothing to
   change."
9. Settings shows "AI calls", the count with decimals ("1.8 of 3 free calls left on this phone.",
   "9 of 10 calls left this month. They come back on 1 December.") and "Reading a bill takes 1 call,
   and changing a split by a sentence takes 0.2."

## Decimal calls: how they are parsed and shown

- `CallsLeft.limit`, `used` and `remaining` are `BigDecimal`. `ProxyApi` reads each from the JSON
  primitive's content, which keeps the number as it was written, and never through a `Double`.
- A string, a boolean, a negative number, a number above 1,000,000 or one with more than two
  decimals reads as no count, as any broken field did before. The bound is checked first, so a
  number such as `1e999999999` costs nothing.
- Values are kept without trailing zeros and with a scale of at least 0, so `9.80`, `9.8` and
  `0.098e2` are the same count, and `10.00` equals `BigDecimal(10)`.
- A whole number, as a proxy from before this change sends it, reads the same as before. No count
  is stored on the phone (`AccountStore` keeps calls left in memory only, as it did), so there was
  no stored integer to migrate.
- `CallsLeft.text` writes a count without trailing zeros: "2.8", "3", "0.2", "10". `CallsLeft.READ`
  (1) and `CallsLeft.REVISION` (0.2, mirroring `revisionWeight` in `proxy/src/policy.ts`) are only
  what the messages say each use takes; the proxy decides.
- Messages say calls where they said reads. A refusal with part of a call left says so: "Reading a
  bill takes a whole call, and 0.8 of your 3 free calls are left."

## Voice

- On Android 12 and later, when `SpeechRecognizer.isOnDeviceRecognitionAvailable` says so, the app
  listens with `createOnDeviceSpeechRecognizer`, and the sound stays on the phone.
- When the phone has only a speech service (`isRecognitionAvailable`), the first tap on the mic
  asks "Use the phone's speech service?" and says it may send what is said over the internet.
  "Type instead" changes nothing. "Use it" is remembered, and the request asks the service to stay
  offline when it can (`EXTRA_PREFER_OFFLINE`).
- If the phone's own recognizer reports that it cannot take its language, the app says so and uses
  the speech service, asking first as above, for the rest of the run.
- `RECORD_AUDIO` is asked for only when the mic is tapped, after the speech-service question when
  there is one. Refused, the field shows a plain note ("To speak a change, allow the microphone for
  Split Vibe in the phone's settings. You can type it instead."); typing and sending work as before,
  and no error is shown.
- A phone without any recognizer gets no mic button.
- Partial results appear in the field as they are heard, after anything typed before. The final
  text stays in the field and is never sent by itself. Nothing heard, a failure or a cancel leaves
  the field as it was, with a short note.
- The manifest declares `RECORD_AUDIO`, the microphone as a feature that is not required (so phones
  without one can still install the app), and the `<queries>` entry for
  `android.speech.RecognitionService`.

## Tests and screenshots added

- `assistant/SplitAssistantTest` (MockWebServer):
  - the body is the contract's example plus a UUID `requestId`, sent to `/v1/split-revision` with
    the install and version headers; wait, blank and unknown lines are skipped;
  - every refusal and stream error maps to its kind and message, also a 400 under any code and an
    answer whose edits are all unreadable;
  - decimal usage updates the account, also from a refusal;
  - the request id is kept across a failure, a cut-off, an unusable answer and a cancel, and
    renewed after `retry_limit` and `input`;
  - a refused session ends the sign-in; AI off and on again; an empty or too long instruction and a
    build without the proxy's address send nothing.
- `assistant/VoiceInputTest` (Robolectric): no recognizer, a speech service, on-device first;
  partial and final text; how failures are told apart; a cancelled recognizer says nothing more; a
  language the phone cannot recognize by itself switches to the speech service; the microphone and
  the speech service are the user's to allow.
- `ui/split/SplitAssistantFlowTest`, on the real split and assistant view models:
  - the success criterion: "Lan không uống bia" on a bill of beer and pho shared by the user, Minh
    and Lan, with the proxy faked as one `item_sharers` edit. The proposal shows 84,000 → 99,750,
    99,750 and 52,500; Apply gives exactly what `SplitCalculator` gives and is saved; Undo brings
    back the old shares, bill and plan;
  - propose, drop one, apply, then undo restores the bill and plan exactly; Undo ends with the next
    change;
  - edits that cannot be applied are shown with their reasons, an unreadable one is counted, and
    only the others are applied;
  - an adversarial answer (a negative amount for someone, an unknown person, a negative item, an
    unknown item, a discount larger than the bill): nothing applies, Apply stays off, the reasons
    are shown;
  - a question is shown alone, answered in the same field and sent with what it asked;
  - no calls left before and after signing in; AI off said once; a build without AI sends nothing;
  - voice: a refused microphone leaves typing and sending working with no error; a phone with only
    a speech service asks first, and what it hears waits in the field, unsent.
- Updated for decimals and the new wording: `ProxyApiTest` (decimal counts, counts of the wrong
  shape), `GatewayBillAnalyzerTest` (part of a call left, decimals in the usage), `AccountStoreTest`,
  `AccountRepositoryTest`, `SettingsViewModelTest`, `AccountMessagesTest` (the calls text),
  `AppModuleTest` (the new bindings) and the device test `GatewayBillAnalyzerOnDeviceTest`.
- Screenshots in `ScreenGalleryTest`, recorded with `-Pscreenshots=record` on HEAD plus my files
  only, so no other screen's image was touched (home, history and the new split came out
  identical). I looked at every one:
  - new: `30-assistant` (the field with an instruction typed) and `31-assistant-proposal` (three
    edits, one dropped, before → after), light and dark;
  - re-recorded because the result screen changed: `05-result-top` (light, dark, large text),
    `06-result-shares`, `07-add-person`, `08-item-split`, `13-result-whole-page`,
    `18-split-by-percent`, `19-who-paid`, light and dark;
  - re-recorded because Settings changed: `11-settings`, `16-settings-signed-in`,
    `17-settings-sign-in-expired`, light and dark.
  The whole-page shot's tall size grew from 1700 to 1900 dp so the page still shows whole, and the
  shares shot scrolls its total clear of the bottom bar, as it showed before.

## Commits

- `f478f46` feat(app): count AI calls with decimals, as a change of a split takes part of one
- `c26ebde` refactor(app): share what every call to the model does between the AI routes
- `1ccce69` feat(app): ask the proxy for the edits a sentence makes to a split
- `c054baa` feat(app): turn what the user says into text, on the phone when it can
- `284ef29` feat(app): change the split on the result screen by a sentence, typed or said

All pushed to `feat/phase-07-ai-assistant-insights`; this report follows them.

## What I needed from the domain or the proxy

Nothing was missing to build to the contract. Notes for their owners:

- The assistant's failures are `BillAnalyzerException` with the bill read's kinds, as asked. The
  name belongs to the bill reader; a neutral name in `:core:domain`, such as `AiCallException`,
  would read better now that two routes use it.
- `CallsLeft.REVISION` (0.2) mirrors `revisionWeight` for the messages only. If the weight changes
  on the server, the hint, Settings and the "part of a call" message keep saying 0.2 until the app
  changes; `/v1/usage` could carry the weights if that matters.
- Limits the app relies on, for reconciling the request builder with the proxy's final validation:
  - the instruction is trimmed and at most 500 code points: the field holds no more, an answer to a
    question is shortened to fit, and a blank one is never sent;
  - the user's name for "me" is their Settings name, at most 60 characters, or "Me";
  - the bill and split go as they are. The app caps neither the number of items or participants
    nor the length of item names and charge labels for this route: names typed in the editor are
    at most 80 characters, but names read from a photo can be longer, and a bill can hold any
    number of items. A 400 from the route is shown as "This bill cannot be changed this way. Change
    it by hand instead."
- `SplitPlan` gives the id of someone taken off to the next person added, so the before/after list
  matches people by id and name.

## Docs impact (not mine to change)

`README.md` ("3 free reads", "10 reads a month") and `docs/architecture.md` ("free reads used up",
the split flow) still describe reads rather than weighted calls, and do not mention the assistant
or voice.

## Notes for the other agents' work

- `ScreenGalleryTest`'s `21-history` can flake: the test waits for the finished status in the
  database, but the list on screen comes from Room's flow and can still say "Draft" a moment later.
  It failed once out of six runs here, on a tree without any assistant change, and passed on rerun.
- While I worked, the other agent's uncommitted `SplitVibeApp` scheduled WorkManager at start-up,
  which Robolectric had not initialised, so every Robolectric test failed in the shared checkout.
  That is why I ran my checks on exported trees of HEAD plus my commits.

## Open questions

1. Should the assistant warn before sending when the last known count is below 0.2? It lets the
   proxy decide now, as bill reads do, because a count can be stale (the month turns).
2. Screenshot numbers: I used 30 and 31; the other agent's PDF images use 36. Nothing collides, but
   the numbering may want a look once the insights screens have theirs.

```text
Status: DONE_WITH_CONCERNS
Summary: The assistant, decimal calls and voice are built, tested and pushed in five focused commits, each checked on its own tree.
Concerns/Blockers: An existing flake in the history screenshot; while I worked, the other agent's uncommitted start-up change broke Robolectric tests in the shared checkout.
```
