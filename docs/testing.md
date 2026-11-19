# Testing on a device

The checks that run on the build machine (unit tests, screenshots, lint, the proxy's tests) are in
the [README](../README.md) and in [architecture.md](architecture.md#checks). This page is about the
tests in `app/src/androidTest`, which need an Android runtime: an emulator or a phone.

| Tests | What they prove |
|---|---|
| `AppLaunchTest` | The app starts as the launcher starts it, opens Settings and a new split, and keeps the open screen when the activity is recreated. |
| `SplitFlowOnDeviceTest` | The sample bill is split from its photo to the sent requests, with the real recognizer and a recorded answer in place of the AI. |
| `KeystoreCipherTest`, `GatewayBillAnalyzerOnDeviceTest`, `MlKitBillTextRecognizerTest`, `PdfSummaryOnDeviceTest` | On the Android runtime: the Keystore encryption, the proxy client reading a streamed answer, ML Kit on the sample bill, and the PDF summary written as a real PDF file. |
| `e2e/` | The end-to-end journeys below: the real app, from its home screen, against a fake proxy. |

`AppLaunchTest` and `SplitFlowOnDeviceTest` also run Android's accessibility checks on every
screen they reach, on Android 14 and later.

## The end-to-end journeys

Each journey starts the app as the launcher does and goes through it the way a person would,
finding every element by the English text on it, or by what it is, such as a text field or a list.
No part of the app is replaced but its address of the proxy, so the screens, view models,
repositories, the database and the proxy client are the ones that ship.

| Test | Journey | What it checks |
|---|---|---|
| `SampleBillJourneyTest` | Split a bill, use the sample bill, type "Split with Minh and Lan. Minh had the beers.", read and split it | The real recognizer reads the photo on the phone, and one bill read goes to the proxy with that text and the instruction and nothing else, as the install, signed out. The split shows the restaurant and what each person pays: you ₫250,993, Minh ₫400,680, Lan ₫250,991. |
| `ManualEntryJourneyTest` | Split a bill, type it in by hand (one item of 90), add Minh and Lan | Each of the three pays 30 in the currency the app works in, the user is offered to ask the other two for 60, and the proxy hears of no bill read and no change of a split. |
| `PaymentRequestJourneyTest` | Signed in: type a bill in by hand (90 at Pho 24), add Minh, request his 45, send it, open Requests | The request goes to the proxy with the session, Minh's share, the user's name and the way to get paid; the share sheet is asked for; Requests lists it under "You asked for" as not opened in the app yet. |

The amounts of the manual journeys are typed as whole numbers, so they read the same in any
currency, and the expected text is formatted the way the app formats it, for the phone's language.
Once something was typed, a journey taps through the element's click action rather than a touch at
its place on the screen: on a phone with a soft keyboard, the keyboard may still be moving the
screen, and a touch would land on whatever slid under it.

### The fake proxy

`FakeProxy` answers on a MockWebServer that the test starts on the phone's loopback address, which
debug builds may reach over plain HTTP. It answers each route the journeys use the way the Worker
does, with the shapes and error codes of [proxy.md](proxy.md):

| Route | Answer |
|---|---|
| `POST /v1/bill-analysis` | With a valid body, as an install that names itself or with the fake's session: 200 `application/x-ndjson`, a `{"type":"wait"}` line and then `{"type":"result","answer":…,"usage":…}`. The answer is always the one Claude gave for the sample bill when it was recorded, [`sample_bill_answer.json`](../app/src/sharedTest/resources/fixtures/sample_bill_answer.json). |
| `GET /v1/usage` | `{usage, aiEnabled: true}`, with `purchaser` when signed in: an install's 3 trial calls, or an account's 10 for the month in UTC, less the bill reads it answered. |
| `POST /v1/session` | For any `idToken`, the fake's session for 30 days, with its `usage` and `purchaser`. No journey calls it, since Google's sign-in cannot run in a test. |
| `GET /v1/fx-rates` | `{base: "EUR", source: "Frankfurter", fetchedAt, rates}` for EUR, USD, GBP, JPY, INR, THB and VND. |
| `POST /v1/requests` | With the fake's session: 201 `{request, url}`, the request as its creditor sees it and a link `https://<host>/r/#<token>`. The same request again answers the same way. A request in a group is 404, as the fake keeps no groups. |
| `GET /v1/requests` | With the fake's session: `{"requests": […]}`, those made through it, newest first. |

A session other than the fake's is 401 `session`, a call to the model without a session or an
install id is 401 `install`, a body the proxy would not take is 400 `input`, and any other path
is 404 `path`. The fake keeps every request it got, which the journeys check.

`FakeProxyRule` points the app at it. The app's own Koin modules, `appModule` and
`spendingModule`, are loaded again with one more module after them, which overrides `ProxyApi`
with one at the fake's address; every object the modules made before, in an earlier test of the
same process, is let go first, so that each is made again with the fake. After the test the
modules are loaded again as the app loads them. Nothing in `app/src/main` knows about the tests.

The rule also starts each test signed out or signed in. Signing in keeps the fake's session in the
app's own `AccountStore`, where a Google sign-in leaves its session, and empties the proxy cache,
as a sign-in does. Every test ends signed out.

## Running them on a computer

With an emulator that Gradle downloads, starts and stops itself (Android 14, as in CI). It needs
hardware virtualisation: KVM on Linux, or a Mac or Windows machine the Android emulator runs on.

```bash
./gradlew :app:pixel6Api34ProdDebugAndroidTest
```

On a phone or an emulator that is already connected (`adb devices` lists it):

```bash
./gradlew :app:connectedProdDebugAndroidTest
```

Only the journeys, or only one of them:

```bash
./gradlew :app:connectedProdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.dantech.splitvibe.e2e
./gradlew :app:connectedProdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dantech.splitvibe.e2e.ManualEntryJourneyTest
```

The results are in `app/build/reports/androidTests/`. The tests need no network and no proxy:
whatever address `local.properties` gives, the journeys talk to the fake.

They run in the data of the debug app on that phone, and change it as a person using it would:
they sign it out, add bills and the people on them, keep "Lan" as the name on requests, and save a
PayPal.Me way to get paid when none is saved, which they remove again. On Android 13 and later they
allow the app's notices, which a build with push would otherwise ask for when someone signs in.
Use an emulator, or a phone whose debug app holds nothing you need.

## In CI

[`device-tests.yml`](../.github/workflows/device-tests.yml) runs every night, on request, and on
every push that changes the device tests, the data they share with the local tests
(`app/src/sharedTest`), the profile module, the dependencies or the workflow.

- **Tests on an Android 14 emulator** runs every test in `app/src/androidTest`, the journeys among
  them, on the emulator Gradle manages (`pixel6Api34`, an Android 14 test image). A failure fails
  the run. The journeys passing here is one of the quality gates of a release.
- **Record the baseline profile** records the startup profile and keeps it as an artifact.
- On a failure, the test reports are kept as an artifact for 7 days.

### The Android versions matrix

To measure the journeys on other versions of Android, start the workflow by hand with
**api-levels** ticked (Actions, Device tests, Run workflow), or:

```bash
gh workflow run device-tests.yml --ref <branch> -f api-levels=true
```

That run does only this, so it neither cancels a run of a push nor is cancelled by one:

| API | Android | System image |
|---|---|---|
| 26 | 8.0, the oldest the app runs on | `default`, as no test image exists for it |
| 30 | 11 | `aosp_atd` |
| 36 | 16, the version the app targets | `aosp_atd` |

Each starts an x86_64 emulator with the Pixel 6 profile through
[android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner), as Gradle's
managed emulator is defined for Android 14 only, and runs the `e2e` package with
`connectedProdDebugAndroidTest`. Its jobs may fail without failing the run: each job's outcome is
the measurement, and its reports are kept as the artifact `e2e-api-<level>` for 14 days, to be
written into the release checklist. Nothing waits for this run.
