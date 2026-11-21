# Testing

The checks that run on the build machine, with their commands, are in
[architecture.md](architecture.md#checks), and the [README](../README.md#tests) says what the unit
tests cover. This page is about running them before a merge, about the screenshot tests and their
reference images, and about the tests in `app/src/androidTest`, which need an Android runtime: an
emulator or a phone.

## Before a merge

`ci.yml` runs for pull requests and for `main` only, so a branch pushed without a pull request gets
no run of it. Before asking for a merge, run the steps of its `checks` job on the computer, in the
order the job has them: the Gradle commands of the [checks](architecture.md#checks), the step that
looks for Anthropic code in the packages, and the three Python checks. The release checks and their
tests read packages with the Android SDK's build tools, which they find through
`ANDROID_BUILD_TOOLS`, or the newest under `ANDROID_HOME`; `ci.yml` installs version 36.0.0. A
branch that changes `web/` or `proxy/` also runs the steps of the `proxy` job. The unit tests run
in the default mode there, as in CI, which fails on any screenshot that differs from its reference
image.

A push to the branch still starts `security.yml`, and `device-tests.yml` when it changes the app;
[In CI](#in-ci) says how to read those runs.

## Screenshot tests

The screenshot tests are part of the app's unit tests;
[architecture.md](architecture.md#screenshot-tests) says what they draw. The Gradle property
`screenshots` chooses what they do with the reference images in `app/src/test/screenshots`
(`app/build.gradle.kts`):

| Mode | Command | What happens |
|---|---|---|
| `verify`, the default | `./gradlew :app:testProdDebugUnitTest` | Each picture is compared with its reference image, and the test fails when they differ. CI runs this mode. |
| `record` | `./gradlew :app:testProdDebugUnitTest -Pscreenshots=record` | Each picture is written as its reference image, over the one before. |
| `compare` | `./gradlew :app:testProdDebugUnitTest -Pscreenshots=compare` | Each picture is compared, but no test fails for a difference and the reference images stay as they are. For each picture that changed, the new one is written as `<name>_actual.png` and the two side by side as `<name>_compare.png`, in `app/build/outputs/roborazzi/`. |

Use `compare` while a change is meant to alter pictures that are not to be recorded yet, such as a
redesign made in several steps: the tests still run and pass, the new pictures can be looked at and
shown to whoever approves them, and the reference images keep the look that was approved last. A
reference image that is missing does not fail the test in this mode either. Record once the new look
is accepted, look at every changed image, dark, large text and pseudolocale included, and review them
in the pull request like code.

- Gradle does not run a test task again when nothing it reads has changed, so a second run on the
  same code compares nothing unless it is forced: add `--rerun`, as in
  `./gradlew :app:testProdDebugUnitTest --rerun`.
- To run the pictures of one test only, add `--tests` with its class, such as
  `--tests 'com.dantech.splitvibe.ui.ComponentCatalogTest'`.
- Recording writes the pictures the tests draw and leaves every other file in the folder alone, so
  a reference image that no test draws any more stays behind, and no mode reports it. To find one,
  delete every `*.png` in `app/src/test/screenshots/`, record, and read `git status`: an image that
  was deleted and not written again belongs to no test.

## Tests on a device

| Tests | What they prove |
|---|---|
| `AppLaunchTest` | The app starts as the launcher starts it, opens Settings and a new split, goes round the four tabs of the navigation bar from Home back to Home, and keeps the open screen when the activity is recreated. |
| `SplitFlowOnDeviceTest` | The sample bill is split from its photo to the step that asks for the money, through the consent to the AI, with the real recognizer and a recorded answer in place of the AI. |
| `KeystoreCipherTest`, `GatewayBillAnalyzerOnDeviceTest`, `MlKitBillTextRecognizerTest`, `PdfSummaryOnDeviceTest` | On the Android runtime: the Keystore encryption, the proxy client reading a streamed answer, ML Kit on the sample bill, and the PDF summary written as a real PDF file. |
| `e2e/` | The end-to-end journeys below, and `DateOrderOnDeviceTest`, which checks the order of a date and how an amount is written against the phone's own data about regions. Android takes that data from ICU, which differs from the Java the local tests run on, and from one version of Android to the next. |

Three of them also run Android's accessibility checks, on Android 14 and later, on every screen
they tap on: a label on everything that can be tapped, touch targets of at least 48dp, and text
contrast. `AppLaunchTest` checks the four main screens on its round of the tabs, and Settings;
`SplitFlowOnDeviceTest` the new split, the consent to the AI, the result and the step that asks for
the money; and `KeyboardOnDeviceTest` the result with the keyboard open, on a phone that has an
on-screen keyboard, which the Android 14 emulator of CI does not have.

## The end-to-end journeys

Each journey starts the app as the launcher does and goes through it the way a person would,
finding every element by the English text on it, by what a screen reader says for a button that
shows no text, such as "Split a bill" in the middle of the navigation bar, or by what it is, such as
a tab, a text field or a list. No part of the app is replaced but its address of the proxy, so the
screens, view models, repositories, the database and the proxy client are the app's own. They are
those of the debug build, though, like every test in `app/src/androidTest`: see [In CI](#in-ci) for
what that leaves out.

| Test | Journey | What it checks |
|---|---|---|
| `SampleBillJourneyTest` | Split a bill, use the sample bill, type "Split with Minh and Lan. Minh had the beers.", read and split it, agree on the consent to the AI | Nothing goes to the proxy before the consent. Then the real recognizer reads the photo on the phone, and one bill read goes to the proxy, as the install, signed out, with that text, the instruction and the local currency and nothing else: never the photo. The split shows the restaurant and what each person pays: on a phone set to the United States, you ₫250,993, Minh ₫400,680, Lan ₫250,991. |
| `DeclinedAiJourneyTest` | Split a bill, use the sample bill, read and split it, choose "Type it in by hand" on the consent to the AI, type the bill (one item of 90), add Minh and Lan | The editor of a bill typed in by hand opens, the user pays 30, the proxy hears of no bill read and no change of a split, and the consent stays not given. |
| `ManualEntryJourneyTest` | Split a bill, type it in by hand (one item of 90), add Minh and Lan, take the user off the bill and back | Each of the three pays 30 in the currency the app works in, and the user is offered to ask the other two for 60. Off the bill, Minh and Lan pay 45 each and the request is for 90. The proxy hears of no bill read and no change of a split. |
| `PaymentRequestJourneyTest` | Signed in: type a bill in by hand (90 at Pho 24), add Minh, request his 45, send it; Minh takes it on in his app; open the Requests tab | The request goes to the proxy with the session, Minh's share, the user's name and the way to get paid; the share sheet is asked for. The Requests tab fetches the list again and shows what only the proxy's list can say: the request under "You asked for" as not paid yet, since Minh took it on, where the answer that made it said "Not opened in the app yet". The tile of what friends owe shows his 45. No error message shows. |
| `KeyboardOnDeviceTest` | Read and split the sample bill as above, choose "By percent", and type Lan's percent with the on-screen keyboard | The field being typed into stays above the bar at the bottom, and the bar above the keyboard. A phone with no on-screen keyboard turned on, such as the test images of Android 11, 14 and 16 in CI, skips the test, as it would prove nothing there; on a phone with one, a keyboard that does not show within 20 seconds of the tap fails it. |
| `ScreenshotsOnDeviceTest` | Open the app, then read and split the sample bill as above | Saves the home and the top of the split as this phone draws them, as `01-home-api-<level>.png` and `05-result-top-api-<level>.png`, for a person to look at the two fonts, the marks of Vietnamese and the dong sign: a font that does not load gives way to the system's without an error. It runs only when the run asks for the pictures with the runner argument `savePictures=true`, as the jobs of the [Android versions matrix](#the-android-versions-matrix) do, and is skipped in every other run. A run that asks but gives no folder for them fails it. The home shows whatever bills the journeys before it kept. |

The amounts of the manual journeys are typed as whole numbers, so they read the same in any
currency, and the expected text is written by the app's own `BillCurrency.format`, so it follows
the phone's region as the app does.
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
| `GET /v1/requests` | With the fake's session: `{"requests": […]}`, those made through it, newest first. One that a test had its payer take on (`FakeProxy.payerTakesOn`, as accepting its link in the payer's app does) shows as bound to its debtor, `debtorBound: true`. |

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
as a sign-in does. Every test ends signed out. Each test also starts and ends without the consent
to the AI, as after Settings took it back, so a journey that reads a bill first answers the consent
screen.

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

`ScreenshotsOnDeviceTest` runs only when the command also has
`-Pandroid.testInstrumentationRunnerArguments.savePictures=true`. On a connected phone or emulator,
the Android Gradle plugin then copies its pictures back under
`app/build/outputs/connected_android_test_additional_output/`.

The results are in `app/build/reports/androidTests/`. The tests need no network and no proxy:
whatever address `local.properties` gives, the journeys talk to the fake.

They run in the data of the debug app on that phone, and change it as a person using it would:
they sign it out, add bills and the people on them, keep "Lan" as the name on requests, and save a
PayPal.Me way to get paid when none is saved, which they remove again. On Android 13 and later they
allow the app's notices, which a build with push would otherwise ask for when someone signs in.
`KeyboardOnDeviceTest` sets the phone to show its on-screen keyboard while a hardware keyboard is
attached, and puts the setting back afterwards. Use an emulator, or a phone whose debug app holds
nothing you need.

## In CI

[`device-tests.yml`](../.github/workflows/device-tests.yml) runs every night, on request, and on
every push that changes the app's code (`app/src/main`) or its build file, the core modules, the
device tests, the data they share with the local tests (`app/src/sharedTest`), the profile module,
the dependencies or the workflow.

- **Tests on an Android 14 emulator** runs every test in `app/src/androidTest`, the journeys among
  them, on the emulator Gradle manages (`pixel6Api34`, an Android 14 test image). A failure fails
  the run. The test image has no on-screen keyboard and the run asks for no pictures, so there
  `KeyboardOnDeviceTest` and `ScreenshotsOnDeviceTest` are skipped. This job is a gate of every
  release: [`release.yml`](../.github/workflows/release.yml) runs it on the tagged commit and signs
  nothing until it passes ([release.md](release.md)). That run has this job only, in a concurrency
  group of its own, so no push cancels it.
- **Record the baseline profile** records the startup profile and keeps it as an artifact. A
  release does not run it, as it ships the profile committed in `app/src/prodRelease`.
- On a failure, the test reports are kept as an artifact for 7 days.

**The journeys run the debug build.** `:app:pixel6Api34ProdDebugAndroidTest` tests `prodDebug`:
R8 neither shrinks nor renames its code, its resources are not shrunk, the pseudolocales are in,
and its network config lets it reach the fake over plain HTTP on the loopback address. So the
journeys do not catch a fault that only R8 or resource shrinking brings, such as the removal of a
class that Koin or a serializer needs. No job runs the minified release on a device. The one release-like build
that runs on one is in **Record the baseline profile**: it installs `prodNonMinifiedRelease`, which
the Baseline Profile plugin makes from the release build type with R8 and resource shrinking off,
starts it and begins a split. The minified release itself is only checked as a package: by the
release checks of [release.md](release.md#what-the-release-checks-look-at), in `ci.yml` and
`release.yml`.

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
`connectedProdDebugAndroidTest`, asking for the pictures of `ScreenshotsOnDeviceTest`. The plain
image of Android 8.0 has an on-screen keyboard, so `KeyboardOnDeviceTest` types with one there; the
test images of Android 11 and 16 have none, and skip it. Its jobs may fail without failing the run:
each job's outcome is the measurement. Each job keeps its reports and the two pictures, under
`connected_android_test_additional_output/`, as the artifact `e2e-api-<level>` for 14 days, to be
written into the release checklist. Nothing waits for this run.

### Reading the two runs

A default run and a run with **api-levels** test different things, so a check of every version of
Android on one commit takes both, started by hand on that commit:

```bash
gh workflow run device-tests.yml --ref <branch>
gh workflow run device-tests.yml --ref <branch> -f api-levels=true
```

- A push to the same branch cancels a default run that is still going, as a newer push replaces it.
  Push nothing until both runs are done; a run that was cancelled says nothing about the commit.
- A run's own result is not the measurement: the jobs of the API levels may fail while the run
  passes. Read the commit a run tested and the conclusion of each of its jobs:

  ```bash
  gh run view <run id> --json headSha,jobs
  ```

- A job that passed can still have skipped a test. For the API levels, read the skipped count in
  the reports of the `e2e-api-<level>` artifact, and look at its two pictures.
