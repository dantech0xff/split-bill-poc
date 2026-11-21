# Review of phase 8 of the Split Vibe UI redesign (381f1b9, e25a6a7, cd4db15, b14380a)

There are no critical or high findings. The record is exact: 136 images, each drawn by a test, and only the six named files deleted. The SvSteps background covers nothing a step draws. The main problem is that on Android 8.0 (API 26) both new end-to-end tests can skip without anything saying so.

## Scope
- **Commits:**
  - 381f1b9: SvSteps, +7/-1.
  - e25a6a7: `device-tests.yml`, +1.
  - cd4db15: six test files (about +360/-122) and 141 images (45 added, 6 deleted, 90 rewritten).
  - b14380a: plan ticks, +5/-5.
- **How I read them:** with `git show` and `git archive` (b14380a and f6d4fb7) into the scratchpad. I edited nothing in the checkout and ran no Gradle.
- **Checked against bytecode** in the Gradle cache with javap: AGP 9.1.1, UTP 32.1.1, androidx.core 1.18.0, Compose ui-test 1.12.1 and animation 1.12.1. Also against the source of the pinned android-emulator-runner v2.38.0 (a421e43).

## Findings, most severe first

### M1. On API 26, both new end-to-end tests can turn into a skip that nothing reports
**Where:**
- `e2e/KeyboardOnDeviceTest.kt`: line 75 (`assumeTrue`), lines 109-114 (`awaitKeyboard`), line 149 (`KEYBOARD_TIMEOUT_MS = 5_000L`).
- `e2e/ScreenshotsOnDeviceTest.kt`: line 46.
- `.github/workflows/device-tests.yml`: line 196 (the script), lines 207-208 (`if-no-files-found: ignore`).

**What goes wrong:**
- **Keyboard test.** On the API 26 `default` image the keyboard should come up. The runner sets `hw.keyboard=yes` only when `enable-hw-keyboard` is true (the default is false), and the AOSP image should ship its own keyboard. But the test gives the keyboard 5 s to appear, on a software-rendered emulator where the keyboard window is built the first time it shows. If it is slower, or the tap does not focus the field, line 75 reports the test as skipped.
- **Screenshot test.** Below API 29, AGP 9.1.1 finds the device folder through a MediaStore query (`content query --uri content://media/external/file --projection _data --where "_data LIKE '%/Android'"`, in `AdditionalTestOutputUtilsKt.findAdditionalTestOutputDirectoryOnDevice`). If MediaStore has not indexed `/storage/emulated/0/Android` yet:
  - AGP logs "additionalTestOutput is not supported on this device running API level 26 because the additional test output directory could not be found";
  - no `additionalTestOutputDir` argument is passed;
  - line 46 skips the test;
  - `if-no-files-found: ignore` keeps the artifact quiet.
- **Why it matters.** In both cases the job is green. Plan step 10 reads each job's conclusion, so a skip reads as a pass. That is exactly how the old keyboard test ended up proving nothing on every run.

**Suggested fix:**
- **Keyboard:** skip only when the phone has no keyboard app at all; otherwise fail, with a longer wait (about 20 s). Check on one ATD run that the list of enabled keyboards really is empty there.
- **Pictures:** pass the output folder explicitly in the api-levels script, or add a step for API 26 that warns when no `*-api-26.png` was kept; when reading the run, read the skipped count in the `e2e-api-26` artifact, not only the job's conclusion.

### L1. The light `15-sending.png` changes because of 381f1b9
**Where:** `core/designsystem/.../motion/SvSteps.kt:35`.

**What goes wrong:**
- `15-sending` is taken 200 ms into the change from the result step to the sent step (`ScreenGalleryTest.kt:484-487`). The old step has faded out (140 ms) and the new one is roughly halfway in, so about 45% of whatever lies under the steps shows through.
- Before this commit that was the gallery activity's window: `Theme.Material.Light.NoActionBar` from ui-test-manifest, #FAFAFA. Now it is `Ground`, #F6F6F4. The committed light picture reads (246,246,244) across the page; without the change those areas would be about (248,248,247).
- The same 2-3 levels of difference apply in the app, whose window is also `Theme.Material.Light`.
- No other capture is taken mid-fade. Every reference was recorded after the change, so no test flags it.

**Suggested fix:** accept it and say so in the phase report; it is the page colour the design wants. A strict "no light capture changes" is only possible by giving the window itself the page colour.

### L2. One Vietnamese check can never fail, and the comments claim more than the code checks
**Where:** `ScreenGalleryTest.kt:335`, `:318-320`, `:650`, `:842`.

**What goes wrong:**
- **Photo screen (`:335`).** `assertNoTotalWrittenTheAmericanWay()` before `03-photo-and-instruction-vi-vn` can never fail: that screen shows no amount or date the app formats, only the receipt bitmap. The inline comment admits this, but the test's KDoc (`:318-320`) and the commit message say every picture is checked for "902.664 ₫".
- **Result screen (`:842`, `:650`).** `AMERICAN_TOTAL` is the exact app form `₫902,664`. `05-result-top-vi-vn` also shows the AI's own sentence from the recorded answer, "the total is 902,664 VND", with US grouping, next to "902.664 ₫". The check lets this through by design, but the KDoc at `:650` says more than the check does.

**Suggested fix:** reword the KDoc to "each picture that shows the total", or drop the call at `:335`.

### L3. ScreenshotsOnDeviceTest also runs in the release gate, where its pictures are thrown away
**Where:** `.github/workflows/release.yml:89`, `device-tests.yml:79` and `:86-90`, `ScreenshotsOnDeviceTest.kt:50` and `:61`.

**What goes wrong:**
- The release calls the device tests with `release` true, so the android-14 job runs every package, including this test, before each release. On that emulator AGP provides a folder and UTP pulls it to `managed_device_android_test_additional_output/`, which no artifact keeps.
- So the release gate gains a sample-bill read, a folder check and a capture on an ATD image, for pictures nobody can see.
- Separately, the `01-home` picture shows whatever bills the earlier tests in the run left behind. It is not the empty first-run home of the `01-home` reference it is named after.

**Suggested fix:** run the test only where its output is kept, for example behind a runner argument that only the api-levels script passes. Rename the home picture, or say in the KDoc that its bills vary.

### L4. `docs/testing.md` no longer matches the device tests
**Where:** `docs/testing.md:9` (the AppLaunchTest row does not mention the tab round), `:14-15` (which classes run the accessibility checks; KeyboardOnDeviceTest now does too), `:26-31` (the journeys table lists neither KeyboardOnDeviceTest nor ScreenshotsOnDeviceTest), `:150` (the `e2e-api-<level>` artifact now also holds the pictures). The testing.md list in `phase-10-docs-store-finish.md:43` covers none of these.

**Suggested fix:** update these four places now, or add them to phase 10's list.

### L5. b14380a ticks the image review while the fixes it found are not recorded yet
**Where:** `phase-08-tests-screenshots-profile.md:83`.

**What goes wrong:** the ticked item says the dark, large-text, en-XA and vi-VN images were reviewed and the fixes gathered into one re-record, but the fixes in progress (plural forms on Vietnamese phones, the light window in the dark theme) are not in the record and will change committed references.

**Suggested fix (for the lead):** reopen the item, or note that the record is repeated once those fixes land.

### Informational
- **I1.** `46-pdf-summary-page-1.png` has new bytes but not one changed pixel; page 2 is untouched. Harmless churn.
- **I2.** `01-home-loading.png` and `01-home-signed-out.png` are byte-identical. This matches the plan (the two states share the main block, and the owner still has to review it), but these two references can never tell the states apart.
- **I3.** ScreenshotsOnDeviceTest and the workflow path are not in the phase's file list. The plan's fallback when there is no emulator is to hand the API 26 font check to the owner. The addition is reasonable and adds no permission or secret (the pictures hold only the sample bill), but the owner should accept it as the means for step 7, and someone still has to look at the pictures.

## What I checked and found correct

**The record (cd4db15)**
- The folder holds 136 PNG files, and the tests draw exactly 136 names, with nothing extra and nothing missing: every `shot` or `capture` with a literal name, the five `HomeState` names plus the owed state in dark, large text and vi-VN, the `-dark`, `-large-text` and `-vi-vn` walks, `46-pdf-summary-page-${index + 1}` over the summary's two pages, and `33-shared-picture`.
- No local test is ignored or filtered, and there is no other reference folder.
- Exactly the six named files were deleted. Every dark image that existed before still exists, and every state the Requirements list is present.

**The Vietnamese captures**
- For `05`, `10`, `01-home` and `01-home-owed`, the checks run before the shot. Each needs at least one node that shows or says "902.664 ₫" and none with "₫902,664". The owed-home check, which used to run after the capture, now runs before it.
- The no-break space matches the app's formatter, and neither the two amount forms nor "13 Nov · " / "Nov 13 · " share a substring.
- `01-home-vi-vn` shows the sample bill with its amount (902.664 ₫) and its day ("13 Nov · You, Minh, Lan"). The test adds up the month exactly as `HomeViewModel.month` does, and `homeMonth` stays null for every other capture.
- In `05-result-top-vi-vn` and `10-requests-sent-vi-vn`, all amounts read "x.xxx ₫" and the days "13 Nov 2026".

**AppLaunchTest**
- `tab(name)` matches only the four navigation tabs; each tab node keeps both its role and its name. The only other Tab-role component (`SvTabs`) is on the Bills screen.
- Each screen is checked after its tap (the titles are headings, and Home is found by "Recent bills"), and each tab is checked as selected.
- In ui-test 1.12.1, `performClick` runs the accessibility checks on the whole screen before it taps, so the four taps check Home, Groups, Requests and Insights, on API 34 and up.

**KeyboardOnDeviceTest**
- It keeps the old test's three checks: unclipped bounds, the field above the bar, and the bar above the keyboard.
- On API 26, androidx.core detects the keyboard by heuristics. With `adjustResize` and edge-to-edge the bottom inset grows by the keyboard, so both the detection and `keyboardTop()` are sound there, with no false positive.
- `show_ime_with_hard_keyboard` is set first and restored in `finally`, also after a skip.

**ScreenshotsOnDeviceTest and the workflow (e25a6a7)**
- An explicit folder argument wins; from API 29 the folder is `/sdcard/Android/media/<package>/additional_test_output`, below that `<Android folder>/data/<package>/files/test_data`; UTP creates the folder before the tests and pulls it back after them. Both folders belong to the app under test.
- The folder lands under `connected_android_test_additional_output/<variant>/connected/<device>/`, which the new workflow path matches, and the upload runs `if: always()`.

**JourneySteps**
- `readTheSampleBill` is SampleBillJourneyTest's sequence without the instruction. It relies on the AI consent not being given yet, which `FakeProxyRule` guarantees for both callers. No journey depends on how many bills earlier tests left.

**SvSteps**
- In animation 1.12.1 the caller's modifier, and so the new background, is drawn inside the layout's content, before its overlay, so shared elements in flight stay on top. The photo frame's shadow and the scan line are drawn inside the step, above the background. Every step already paints the same colour through its `Scaffold`, so the background only shows during fades.

**Other**
- No commit touches the baseline profiles. e25a6a7 changes no permission, trigger or secret.

## Unresolved questions
1. Has an api-levels run been made on these commits? Nothing recorded says whether SampleBillJourneyTest passes on API 26. Both new tests take the same path, so if it fails there, neither checks anything.
2. Should the AI's explanation follow the phone's region? On `05-result-top-vi-vn` it reads "902,664 VND" beside "902.664 ₫".
3. Does "every screen has a light image" cover sheets and dialogs? These have no capture: AddToGroupSheet, ExchangeRateSheet, PersonSheet, NewGroupSheet, PaySheet, NameDialog and DeleteRecurringBillDialog.
4. Is the owner content with CI pictures as the way to check the fonts on API 26 (step 7)?
5. The record and the two compare runs, the second forced with `--rerun`, could not be verified here, since Gradle was not to be run.

Sources: android-emulator-runner action.yml and src/emulator-manager.ts at a421e43; the Android Developers blog post on ATD images.

# Follow-up review: defc5cc (English plural rules) and 416330d (night window and SvScreenLayer)

The plural forms are now right on every phone and through every path. The dark window and the new screen layer are also correct, and no reference image needed recording. But defc5cc's English resources are built on the same AssetManager the phone's resources read from. On any non-English phone, the first plural therefore turns the phone's library and system strings English until the next configuration change. That needs fixing before the phase closes.

I read both commits with `git show` only: no edits, no commits, no Gradle. The framework claims are checked against the Android 14 framework jar Robolectric uses, Compose UI 1.12.1 and the API 36 platform resources.

## Findings, most severe first

### D1 (Medium). The English resources reconfigure the phone's AssetManager
**Where:** `app/src/main/java/com/dantech/splitvibe/text/UiText.kt:109-111` (defc5cc).

**Evidence:**
- The deprecated `Resources(AssetManager, DisplayMetrics, Configuration)` constructor creates a new `ResourcesImpl` that keeps the AssetManager it is given, not a copy.
- Its `updateConfiguration` then calls `mAssets.setConfiguration(…)` with the English locale and a left-to-right direction.
- `resources.assets` is the original `ResourcesImpl`'s own AssetManager.
- So after `EnglishResources.of(resources)`, every lookup through the original `resources` uses en-US. That lasts until their own `ResourcesImpl` gets the next configuration change, and the next plural flips them again.

**Scenario, on a Vietnamese phone:**
- The build has no locale filters, so the APK carries every library translation, such as Compose UI's Vietnamese "Đã chọn" for *selected*.
- Open Home with a bill. "Your share of N bills" is resolved through `LocalResources`, which flips the activity's AssetManager to English. From then on TalkBack says "Selected" instead of "Đã chọn" on the navigation tabs, Material 3's own strings are English, and the text toolbar that Compose opens shows "Copy", "Paste", "Select all" (the framework's `android.R.string.copy` and the rest, read through the activity's resources). After a rotation they are Vietnamese again, until the next plural.
- On Arabic or Persian phones, the shared AssetManager also loses its right-to-left direction, so `-ldrtl` resources resolve left-to-right.
- The application's AssetManager is flipped the same way, from background threads: PdfSummary resolves its plurals inside `withContext(Dispatchers.Default)` (`Exporter.kt:53-56`), and Notices does so from a worker (`Notices.kt:121`).
- So the commit message and the KDoc ("Only the choice of form changes") do not hold.

**Fix:** build the English resources through `createConfigurationContext`, which goes through `ResourcesManager` and gives the new `ResourcesImpl` its own AssetManager. Add a `vi-rVN` test that resolves a plural and then asserts that `context.getString(android.R.string.copy)` (or Compose's `R.string.selected`) still reads in Vietnamese.

### D2 (Low). The cache rebuilds often and outlives its activity
**Where:** `UiText.kt:100-113`. There is a single entry, keyed by the whole `Configuration`, so alternating callers with different configurations (a screen, then an export or a notice) rebuild it every time, each time repeating D1's reconfiguration under the lock that composition also takes. The process-wide entry keeps the AssetManager of the activity it was built from after that activity is destroyed (it holds no `Context`, so this is not an Activity leak). The KDoc's "made again only when the phone's configuration changes" is inaccurate. **Fix:** falls out of D1's fix.

### D3 (Low). The tests pin only the Vietnamese rule family
**Where:** `NumbersInTextsTest.kt:88-101`. The KDoc names French, but nothing checks a count of 0 on `fr` or `fa`, which both take "one" for 0 under their own rules. Nothing checks that `ar-XB` keeps its pseudo-localized plurals, or that other resources are unaffected (D1). **Fix:** add those cases.

### T1 (Low). The night parent theme changes more than the window colour
**Where:** `app/src/main/res/values-night/themes.xml:7-9` (416330d). Compared from the API 36 platform resources, `Theme.Material` against `Theme.Material.Light`:
- **Status bar.** The starting window on API 26-30 in dark mode now has a black status bar until `enableEdgeToEdge()` takes over; that is better.
- **Platform popups.** The platform text-selection toolbar and any other platform popup themed by the activity is dark in dark mode; it used to be light.
- **Task colour.** `colorBackground` is #303030, which the platform reads for the task description background (the placeholder in recents).
- **Unchanged.** The navigation bar colour, `windowDrawsSystemBarBackgrounds`, and the absence of `windowLight*`; `enableEdgeToEdge()` sets the bar colours and icon appearance at runtime from the night bit.

**Fix:** say so in the phase report, set `android:colorBackground` to `@color/window_dark`, and look at the text toolbar in dark mode on a device.

### T2 (Low). The light window is still not the page
**Where:** `values/themes.xml` (unchanged). The light window is the platform's #FAFAFA while the light page is #F6F6F4, so a light cold start and the API 31+ splash still show the platform grey before the page. **Optional fix:** a light window colour of #FFF6F6F4, with a light case in the test.

### Informational
416330d records no image, and correctly so: the only committed capture taken mid-transition through AppNavHost is `14-back-swipe-preview`, where Home is at full opacity and nothing under the navigation shows; the other galleries host their screens in ui-test-manifest's `ComponentActivity`, which has an explicit light theme.

## What I checked and found correct
- Every plural path goes through `UiText.Plural` (Compose, RequestMoneyContent, ResultContent, ShareEffect, Exporter, PdfSummary, Notices); there is no direct `getQuantityString` or `pluralStringResource` in app or core code, and there are no widgets.
- The chosen form is right on every phone: vi, fr and fa now read as English; ar and he give the same result as before. The pseudolocale check is right (en-XA by language, ar-XB by its XB region). Values are formatted as before.
- The cache is thread-safe, and a locale change while the app runs forces a rebuild.
- `01-home-vi-vn.png`: only a 13×16 px box changed (the "s" of "bills").
- SvScreenLayer is exactly SvSteps' former construction, so shared elements in flight stay above the page; AppNavHost keeps its shared transition scope; predictive back now reveals the page instead of the window. WindowBackgroundTest cannot pass vacuously.

## Questions
1. Should the light window also become the page colour (T2)?
2. Is the dark floating text toolbar in the dark theme wanted (T1)?
3. After the D1 fix, could someone long-press a text field on a Vietnamese phone and read the toolbar labels?
