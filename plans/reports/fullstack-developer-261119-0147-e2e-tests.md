# End-to-end device tests against a fake proxy

Status: done, with one app bug found on Android 8 to 11 that is outside this work's files.

## Outcome

- **Android 14:** the three journeys run the real app from its home screen against a fake proxy
  on the phone, and pass. The Android 14 job of `device-tests.yml` runs them with the other device
  tests (12 tests) and fails the run on any failure, as before. Two runs on this branch, 12 of 12
  each.
- **The measuring run** is a dispatch input, `api-levels`. It runs only the journeys on API 26, 30
  and 36, in a concurrency group of its own, without failing the run. I started it once:
  - API 36: 3 of 3 pass.
  - API 30: 2 of 3 pass.
  - API 26: 1 of 3 pass.
- **The sample bill fails on API 26 and 30 because of an app bug:**
  `SplitCalculator.divideProportionally` calls `BigInteger.longValueExact()`, which Android only
  has from API 31. The API 26 run also showed a weakness in the test itself, fixed since; see
  below.
- **No main-code hook was needed.** Nothing in `app/src/main` changed.

## Journeys as built

All are in `app/src/androidTest/java/com/dantech/splitvibe/e2e/`.

| Test | Steps | Checks |
|---|---|---|
| `SampleBillJourneyTest` | Home, "Split a bill", "Use the sample bill", the instruction "Split with Minh and Lan. Minh had the beers.", "Read and split the bill" | The real ML Kit reads the photo. Exactly one `POST /v1/bill-analysis` goes out as the install, with no `Authorization`. It carries exactly the contract's five fields, the typed instruction, and an `ocrDocument` that contains "902.664". The result shows the merchant and each share: You ₫250,993, Minh ₫400,680, Lan ₫250,991. |
| `ManualEntryJourneyTest` | Home, "Split a bill", "Type it in by hand", "Corner Cafe", one item "Lunch" of 90, Done, add Minh and Lan | Each row of "Each person pays" shows 30 in the app's currency, and the bar offers "Request 60 from 2 people". The fake got no `/v1/bill-analysis` and no `/v1/split-revision`. |
| `PaymentRequestJourneyTest` | Signed in. Home, a bill typed in by hand (Pho 24, "Pho bo" 90), add Minh, "Request 45 from 1 person", name "Lan", "Send", Done, home, "Requests" | One `POST /v1/requests` goes out with the fake's bearer session. It names Minh as payer and Lan as creditor, has the title "Pho 24", Minh's 45 in minor units, the app's currency and a payment method. The share sheet is asked for once, then answered without opening. `GET /v1/requests` is called, and Requests lists "Minh · Pho 24", the amount and "Not opened in the app yet" under "You asked for". |

How the tests find and use elements:

- **Shared steps.** Typing a bill in by hand, adding a person through the sheet, and checking one
  person's share (the amount next to their name) are in `JourneySteps.kt`.
- **Finding elements.** Every element is found by the English text the user sees (labels, field
  labels, titles), or by what it is (a text field, the screen's lazy list).
- **Currency.** The amounts of the two manual journeys are whole numbers. They are typed in the
  app's currency (USD on the CI emulators), and the expected text is formatted with the app's own
  `BillCurrency.format`.
- **Tapping.** Once something has been typed, a journey taps through the element's click action
  rather than by touching its position on the screen.

## How the fake proxy is wired

- **The fake (`FakeProxy`).** An OkHttp `Dispatcher` on a `MockWebServer` started inside the test,
  on the phone's loopback address, where debug builds allow plain HTTP. It answers with the shapes
  and error codes of `docs/proxy.md`:
  - `POST /v1/bill-analysis`: NDJSON, a wait line, then a result line with the recorded
    `sample_bill_answer.json` and `usage`.
  - `GET /v1/usage` and `POST /v1/session` (no journey calls the session route).
  - `GET /v1/fx-rates`.
  - `POST /v1/requests`: 201 `{request, url}`, and the same answer when the same id comes again.
  - `GET /v1/requests`.

  Refusals are the proxy's: 401 `session` or `install`, 400 `input`, and 404 `path` for any other
  route. Every request is recorded for the assertions.
- **Pointing the app at it (`FakeProxyRule`).** It is the outer rule, so it runs before the
  activity starts.
  - Before the test, it unloads the app's `appModule` and `spendingModule`. This lets go of every
    object they made, which an earlier test in the same process may have made with the build's
    address. It then loads them again, with one module after them that overrides `ProxyApi` with
    the fake's address (Koin's default override).
  - After the test, it does the same without the override.
- **Account state.** The rule starts each test signed out. For the request journey it starts
  signed in instead: it empties `ProxyCache`, as a sign-in does, then calls
  `AccountStore.saveSession(<fake session>)`. Every test ends signed out with the cache emptied.
  When it signs in on Android 13 and later, it also grants POST_NOTIFICATIONS. Otherwise, a local
  build with push would show the system's permission question over the app.
- **Way to get paid.** The request journey saves a PayPal.Me method through
  `PaymentMethodRepository` only when the phone has none, and removes it afterwards. The app keeps
  a request on the proxy only when it has a payment method.
- **Share sheet.** An `Instrumentation.ActivityMonitor` on `ACTION_CHOOSER` blocks the system
  share sheet, so nothing covers the app.

## CI runs

| Run | What | Result |
|---|---|---|
| [44445548965](https://github.com/dantech0xff/split-bill-poc/actions/runs/44445548965) (push `2bf92e0`) | Android 14 job ([job](https://github.com/dantech0xff/split-bill-poc/actions/runs/44445548965/job/135296676733)), profile job | success: 12 of 12 tests, the 3 journeys among them; profile recorded |
| [44446284788](https://github.com/dantech0xff/split-bill-poc/actions/runs/44446284788) (dispatch, `api-levels=true`, at `f736fa4`; the one measuring run) | API 36 ([job](https://github.com/dantech0xff/split-bill-poc/actions/runs/44446284788/job/135299113801)) | success: 3 of 3 |
| | API 30 ([job](https://github.com/dantech0xff/split-bill-poc/actions/runs/44446284788/job/135299114179)) | failure: manual entry and payment request pass; the sample bill fails because of the app bug below |
| | API 26 ([job](https://github.com/dantech0xff/split-bill-poc/actions/runs/44446284788/job/135299114114)) | failure: manual entry passes; the sample bill fails because of the app bug below; the payment request failed because of the test's tap, fixed in `2f3258a` |
| [44447406618](https://github.com/dantech0xff/split-bill-poc/actions/runs/44447406618) (push `bfed21d`, with the tap fix) | Android 14 job ([job](https://github.com/dantech0xff/split-bill-poc/actions/runs/44447406618/job/135302798410)), profile job | success: 12 of 12 |

The measuring run reads as success overall, because its jobs have `continue-on-error`. Each job's
own result is the measurement, and each job keeps its reports as the artifact
`e2e-api-<level>` for 14 days.

Local checks:

- `:app:compileProdDebugAndroidTestKotlin` passes.
- `ktlintCheck` passes over the whole project at the last run.

Both used the mirror init script and `--offline`, one build at a time.

## Found: the split crashes on Android 8 to 11 (needs a fix in `core/domain`)

- **The cause.** `core/domain/.../SplitCalculator.kt` calls `BigInteger.longValueExact()` at
  lines 221 and 225, in `divideProportionally`. That method exists on Android only from API 31,
  and the app's `minSdk` is 26.
- **What it breaks.** `divideProportionally` handles the charges of a split by item, splits by
  shares or by percent, and `GroupConversion`. On Android 8 to 11 each of these throws
  `NoSuchMethodError`.
- **Where it shows.** The API 30 logcat shows the throw in `SplitBillViewModel.showSaved`, after
  the bill read (`SplitResultState.<init>` → `SplitPlan.result` → `SplitCalculator.byItem` →
  `proportionalAmong` → `divideProportionally`), so the result never shows.
- **Why nothing caught it.** The unit tests run on a JDK, and Android Lint does not check the
  plain JVM `core/domain` module for API levels.
- **A fix for the owner of `core/domain`.** Check `bitLength() < 64` and use `toLong()` in place of
  `longValueExact()` on `BigInteger`. The `BigDecimal.longValueExact()` calls are fine. After the
  fix, a second measuring run should show the sample bill passing on API 26 and 30.

## Different from the contract, or left

- **The tap fix is unmeasured on API 26.** On the API 26 image, which has a soft keyboard, the
  keyboard was still opening when the request journey tapped "Add an item", so the touch landed on
  a charge chip. The editor list then had 10 rows but no item field. The test images used on
  API 30, 34 and 36 have no keyboard. Since `2f3258a`, a tap that comes after typing goes through
  the element's click action instead. That passed on Android 14. The instruction was to start the
  measuring run only once, so it has not been measured on API 26 again.
- **The matrix uses an emulator runner, not Gradle's managed devices.** The managed device is
  defined only for API 34, and `app/build.gradle.kts` was out of scope. So the matrix starts its
  emulators with `reactivecircus/android-emulator-runner`, pinned to the commit of v2.38.0
  (`a421e43…`). It uses x86_64, the Pixel 6 profile, `aosp_atd` on API 30 and 36, and `default` on
  API 26, where no test image exists. If managed devices for API 26, 30 and 36 are added to
  `app/build.gradle.kts` later, the matrix can call them in place of the runner.
- **The payment method is seeded, not added through the screens.** Only the session had to be
  seeded, but the request journey also saves its way to get paid through the app's repository,
  and only when none is saved. Adding it through the screens would make the journey depend on
  what the phone already holds.
- **The fake serves two unused routes.** `POST /v1/session` and `GET /v1/usage` answer as on the
  proxy, but no journey calls them.
- **No accessibility checks in the journeys.** The existing launch and split tests keep running
  those checks; accessibility is a gate of its own in the plan.
- **The new tests use the deprecated Compose rule factory.** `createAndroidComposeRule` is
  deprecated in favour of the `junit4.v2` one, which runs coroutines on a `StandardTestDispatcher`.
  I kept the factory the existing device tests use, which pass on CI. Moving all device tests to
  `v2` is a separate change.
- **New push trigger.** The workflow now also starts on pushes that change `app/src/sharedTest/**`,
  since the fake answers with a fixture from there.
- **Cosmetic job names.** On a run where a job is skipped, GitHub shows its name template
  unexpanded: "End-to-end tests on API ${{ matrix.api-level }}" on pushes, "matrix.name" on the
  measuring run.
- **Docs.** `README.md` and `docs/architecture.md` (not in this work's files) still describe the
  device tests without the journeys and do not link `docs/testing.md`.
  `docs/release-checklist.md`, written by another agent, already links it.

## Files

- `app/src/androidTest/java/com/dantech/splitvibe/e2e/`: `FakeProxy.kt`, `FakeProxyRule.kt`,
  `JourneySteps.kt`, `SampleBillJourneyTest.kt`, `ManualEntryJourneyTest.kt`,
  `PaymentRequestJourneyTest.kt`
- `.github/workflows/device-tests.yml`
- `docs/testing.md`
- Commits: `42e98b0`, `f163b8c`, `2bf92e0`, `7953fd9`, `2f3258a`, `bfed21d`, and this report.

## Unresolved

- The `core/domain` fix above, and then one more measuring run to record API 26 and 30 in the
  release checklist.
