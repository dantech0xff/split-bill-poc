# Fixes of the review of the release workflow, CI checks and device journeys

Date: 19/11/2026. Branch `feat/phase-09-quality-compliance-release`. Review:
`plans/reports/code-reviewer-261119-0309-phase-9-release-ci-e2e-review.md`. The coordinator added two
items from `plans/reports/code-reviewer-261119-0309-security-review.md`: M3, the abuse address, and
L4, Gradle vulnerability scanning.

## Outcome

Every finding is fixed as decided, and both extra items are done. A release now signs nothing before
three things pass on the tagged commit: ci.yml's checks, the Android 14 device tests, and a check of
the Worker's settings. That last check runs before any approval and stops the run when
`GOOGLE_CLIENT_ID`, `ANDROID_CERT_SHA256` or `ABUSE_EMAIL` is empty in `proxy/wrangler.jsonc`. All
three are empty today, so no release can go ahead until the owner commits them, which is intended.

The release checks gained four things: the 16 KB ELF check, attribute lookup in any namespace, more
forms of keys, and nested zips. The secret scan no longer takes its configuration from the commits it
scans. Gradle's shipped libraries now reach the dependency graph from main. The Gradle distribution's
checksum is pinned.

Every workflow passes actionlint 1.7.12. The Security and Device tests runs on the pushed commit are
listed under [CI runs](#ci-runs).

## Commits

| Commit | What |
|---|---|
| `4bb97d8` | release checks: 16 KB ELF alignment, attributes by resource ID and local name, more key forms (M2, L1, L2) |
| `4f52a77` | the payment request journey checks the proxy's list; FakeProxyRule undoes a failed setup (M3, L10) |
| `0ec1344` | `distributionSha256Sum` for Gradle 9.4.1 (L7) |
| `25badc5` | `dependency-graph.yml` and the plugin's two verification entries (security L4) |
| `078a8cf` | secret scan with main's rules, `--ignore-gitleaks-allow`; dependency review in every scope (L6, L3) |
| `018db01` | release.yml calls the device tests and checks the Worker's settings before signing (H1, H2, security M3) |
| `89172c3` | in-process Kotlin, `--ignore-scripts`, immediate Durable Object update, 30-day bundle (L5, L4, L8, L9, L11 comment) |
| `064dad1` | testing.md: the release gate and the debug build (H1, M3, M4) |
| `7831d6a` | release.md and runbook.md (H2, M1, L9, L11, L12, release checks, scans) |

All were pushed to origin; the branch head is `7831d6a`. The commits stage explicit paths only.

## Findings

### H1. The release waits for the API 34 journeys

- **`device-tests.yml`:**
  - It has a `workflow_call` trigger with a boolean input, `release`, defaulting to true, which only a
    called run has.
  - When called, it runs only the Android 14 job. The baseline profile recording is now a job of its
    own, `profile`, skipped for a release, which ships the committed profile. The API 26/30/36 matrix
    stays manual only.
  - The concurrency group is
    `device-tests-<ref>[-api-levels][-release]`, with `cancel-in-progress: ${{ !inputs.release }}`. A
    called run's group therefore differs from a tag-triggered or branch run of the workflow itself,
    and a called run is never cancelled.
  - The push paths gain `app/src/main/**`, `core/**` and `app/build.gradle.kts`.
  - The comment "A release waits for these to pass" sits on the Android 14 job, and is now true.
- **`release.yml`:** a job `device-tests` (`needs: version`, `contents: read`,
  `uses: ./.github/workflows/device-tests.yml`), and `bundle` needs it.
- **Tested:**
  - actionlint: 0 findings on the final files, and on the intermediate state of `018db01`, linted with
    its callees.
  - The push run on `7831d6a` started exactly the two non-measuring jobs and skipped the matrix.
  - The called path needs a tag, which I may not create, so it has not run on GitHub.

### H2, and security M3: the Worker's settings

- **`scripts/worker-settings.py` (new):**
  - It reads `proxy/wrangler.jsonc` as Wrangler does, as JSON with comments and trailing commas, and
    prints every `vars` value.
  - With `--summary FILE`, it appends them as a table. Each value is shown as JSON, so an empty one
    reads `""`.
  - It exits 1 naming each of `GOOGLE_CLIENT_ID`, `ANDROID_CERT_SHA256` and `ABUSE_EMAIL` that is
    empty or blank, with what breaks without it. It exits 2 when the file cannot be read.
  - Its tests are in `scripts/worker-settings.test.py`, which ci.yml now runs.
- **`release.yml`:** a job, *The Worker's settings*, with `contents: read` and no environment or
  secret. It runs the script with `--summary "$GITHUB_STEP_SUMMARY"`, and `bundle` needs it, so the
  run stops before the first approval.
- **Docs:**
  - `docs/release.md` has a new section, "The Worker's settings". It says that the repository's file
    is the production configuration, and that a release overwrites any value changed in the
    dashboard; Wrangler's own help is quoted. An emergency switch must be committed, or no release
    made until it is undone. The section also has the table of the three required values.
  - `docs/runbook.md` says the same in "Switching AI off", for switching back, and in "Forcing an
    update".
- **Tested:**
  - 5 tests pass, and 5 deliberate breakages of the script each make them fail: trailing commas
    kept, strings not skipped, blank counted as set, the abuse address not required, pipes not
    escaped.
  - On the repository's file the script exits 1 and names all three values. It lists
    `ANDROID_DEV_CERT_SHA256`, which the proxy's commit `5e937db` added and keeps empty on purpose,
    without requiring it.
  - `wrangler deploy --dry-run` lists the same variables the script prints.

### M1. Tag on main

The tag trigger stays. `docs/release.md` has a new section, "Only the owner pushes release tags". It
gives the reason: a tag runs its own commit's `release.yml`, whose check of main it can leave out.
It then gives the settings:

- a tag ruleset on `v*` with *Restrict creations*, *Restrict updates* and *Restrict deletions*, whose
  bypass list holds only the *Repository admin* role, the owner;
- the `production` environment limited to `v*` tags, with the owner as required reviewer.

The menu path and rule names are from GitHub's ruleset docs. I could not see or create the settings.

### M2. 16 KB ELF alignment

- **Change:**
  - A new release check, "16 KB page alignment". Every `.so` in the APKs is read as ELF. In a 64-bit
    library, every PT_LOAD segment must have `p_align >= 16384`, in either byte order. 32-bit
    libraries are skipped.
  - A `.so` that is not readable ELF fails the check, and so does a 64-bit library with no PT_LOAD.
  - zipalign stays.
- **Tests:**
  - A package with 64-bit libraries aligned to 16384 and 65536 and a 32-bit one at 4096 passes.
  - A 64-bit library with a 4096 segment fails and is named, and a non-ELF `.so` fails.
  - The release-like test package now carries aligned libraries, and the bad one a 4096 library.
- **Real packages:** an unsigned prod release APK and AAB that an earlier session built on 14/11
  from an older commit, which I found in its scratchpad:
  - The APK passes. Its 4 64-bit libraries are all `0x4000`; readelf agrees, and shows the 32-bit
    ML Kit library at `0x1000`.
  - bundletool 1.18.3 (checksum checked) made 258 split APKs from the AAB. They pass all five checks
    in 17 s: 12 64-bit libraries and 1038 files searched.

### M3. The payment-request journey asserts the proxy's list

- `FakeProxy.payerTakesOn(id)` stands for the payer accepting the link in their app, as
  `POST /v1/request-links/accept` does on the proxy. From then on the list shows the request with
  `debtorBound: true`.
- The journey calls it after the POST and before opening Requests. It then waits for
  "Not paid yet", which only the list can show, since the cached POST answer says
  "Not opened in the app yet". It checks that the latter is gone, and that no "OK" button shows: the
  error card has the only one on that screen.
- **Tested:**
  - `:app:compileProdDebugAndroidTestKotlin` passes, and ktlintCheck passes.
  - On the device, the Android 14 job of run 44460220611 passed 12 of 12 tests, this journey among
    them.

### M4. The debug build

`docs/testing.md` now says that the journeys, like every device test, run `prodDebug`, and what that
leaves out: R8, resource shrinking, the release network config. It also says that no job runs the
minified release on a device.

The only release-like build on a device is the baseline profile job's `prodNonMinifiedRelease`,
release with R8 and resource shrinking off. Three sources confirm it:

- a `--dry-run` of `:app:generateProdReleaseBaselineProfile`: 85 `ProdNonMinifiedRelease` tasks,
  `:baseline-profile:pixel6Api34ProdNonMinifiedReleaseAndroidTest`, no R8 task;
- the plugin's bytecode;
- Android's docs, which list the overridden `isMinifyEnabled = false` and `isShrinkResources = false`.

No build was changed.

### L1. Namespaced attributes

- **Change:** the parser keeps each attribute's namespace, local name and resource ID.
  - The manifest's `debuggable`, `usesCleartextTraffic`, `networkSecurityConfig` and
    `targetSdkVersion` are looked up by resource ID: `0x0101000f`, `0x010104ec`, `0x01010527` and
    `0x01010270`. I confirmed these with an `aapt2 dump`.
  - The network config's `cleartextTrafficPermitted` is looked up by local name in any namespace.
  - `package` and `split` are read without a namespace, as Android reads them.
  - The name is split at the last colon after which the line still reads as an attribute. A
    namespace URI with `=` or `:` therefore cannot hide one, and a crafted string value can at worst
    cause a failure.
- **Tests:** `p:debuggable` and `p:usesCleartextTraffic` with the private namespace, and
  `x:cleartextTrafficPermitted` with `xmlns:x="http://example.com/x"`. Each fails, and is named.

### L2. More forms of keys

- **Change:**
  - The private key pattern allows `Name: value` header lines, such as `Proc-Type`, `DEK-Info` and
    `Comment`, between the BEGIN line and the body.
  - Zip entries inside the APKs are opened and searched, 3 levels deep. A nested entry that cannot be
    read, being encrypted or unusually compressed, is a finding.
  - JKS files are found by `FEEDFEED`. PKCS#12 files are found by a DER sequence of version 3 and
    then PKCS#7 data or signed data, which certificates and the PKCS#7 signatures of signed APKs do
    not match.
  - Every base64 run of 32 or more characters, standard or URL-safe, is decoded from each of the 4
    alignments and searched for every kind of key. Such a finding says "in base64".
- **Tests:** an encrypted PEM key with headers, a PGP block with `Comment:`, a service-account JSON
  in `assets/bundle.zip`, a JKS file, a PKCS#12 file, and an `sk_` key in base64. Each is found and
  named, and none is printed. The release-like package also carries a zip and a base64 public key,
  and passes.
- **No false findings** on the real APK or the 258 splits.
- The test inputs are still assembled at run time, and gitleaks finds nothing in the file under the
  default rules.

### L3. Dependency review scopes

`fail-on-scopes: runtime, development, unknown`, and the comment now names development packages such
as Wrangler. Dependency review runs on pull requests only, so it did not run on these pushes.

### L4. `npm ci --ignore-scripts`

- **Change:** both installs in the worker job. The comment now says that no install script runs,
  but that the web build still runs package code that could change the Wrangler run later with the
  token.
- **Checked first,** on a scratch copy of the committed `proxy/` and `web/`:
  - `npm ci --ignore-scripts` installed 137 and 122 packages.
  - `REQUIRE_WEB=1 npm run build:web` built the pages.
  - `npx wrangler deploy --dry-run --durable-objects-code-update-mode immediate` (Wrangler 4.162.0)
    bundled the Worker, 1471 KiB, and listed its bindings.
  - The lock files' only install scripts are esbuild, workerd and fsevents. The first two find their
    binaries through optional dependencies; fsevents is macOS only.

### L5. Kotlin in-process

- **Change:** the signed build passes `-Pkotlin.compiler.execution.strategy=in-process` beside
  `--no-daemon`. The comment says what is true: the passwords reach only this build's processes,
  and none outlives it.
- **Checked:** `:app:compileProdDebugAndroidTestKotlin` with those flags:
  - The log says "Finished executing kotlin compiler using IN_PROCESS strategy" and "Daemon will be
    stopped at the end of the build".
  - No Kotlin daemon, Gradle daemon or aapt2 process was left afterwards.
- **A clean build fits too.** CI's signed build is clean, so I also built `:app:bundleProdRelease`
  unsigned, from a clean export of `7831d6a`, with the same flags. It succeeded in 4 min 20 s
  (91 tasks, all executed). The whole app compiled in-process within Gradle's 3 GB heap, R8 ran
  without a full GC, and no daemon or aapt2 process was left afterwards.

### L6. gitleaks configured by the workflow

- **Change:**
  - The scan reads `.github/gitleaks.toml` and `.github/gitleaksignore` with `git show` from the
    pull request's base commit, or from `origin/main` for a push. The scanned commits cannot change
    them, except by changing the workflow itself.
  - Where the base has no rules, gitleaks' default rules apply with nothing ignored, which is how
    this branch scans until it is merged.
  - It passes `--config`, `--gitleaks-ignore-path` and `--ignore-gitleaks-allow`.
  - It deletes the working tree's `.gitleaksignore`. gitleaks 8.30.1's source (`cmd/root.go`) shows
    it always loads `{source}/.gitleaksignore` as well as the given path. Only history is scanned, so
    deleting the file loses nothing.
  - The rules extend the defaults with one AND allowlist. In `scripts/release-checks.test.py`, a
    finding is let through only on a line that uses `FAKE_BODY` or `Fa1keKeyBody`.
- **Tested:** the step, extracted from the workflow, in a scratch repository:
  - A branch exempting its own fake GitHub token three ways (a `gitleaks:allow` comment, a
    `.gitleaksignore` with its fingerprint, and a `.gitleaks.toml` allowing every path) passed the
    old step: no leaks.
  - The new step found the token, both as for a pull request and as for a push.
  - The allowlist let through only the `FAKE_BODY` line in the test file. The same marker in another
    file, and an unmarked line in the test file, were still found.
  - With no rules on the base, all were found.
  - On this repository's history locally: no leaks, with either the default rules or the new ones.
    On GitHub: 402 commits, no leaks. My changed files have 0 findings under the default rules.

### L7. Gradle distribution checksum

- **Change:** `distributionSha256Sum=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb`,
  Gradle's published sum for `gradle-9.4.1-bin.zip`. Both its `.sha256` file and
  gradle.org/release-checksums give it.
- **Checked:**
  - With an empty Gradle home, `./gradlew --version` downloaded the distribution and accepted it.
  - A copy of the wrapper with one altered digit refused it: "Verification of Gradle distribution
    failed!".

### L8. Durable Objects deploy mode

`npx wrangler deploy --durable-objects-code-update-mode immediate`. Wrangler 4.162.0's help lists the
flag, and the dry run above accepted it. release.md mentions it in the publishing step.

### L9. Bundle retention

`retention-days: 30`. release.md says that once the bundle is gone, the whole run is run again (*Re-run
all jobs*). That is safe, as a failed upload's edit is deleted without a commit, so Google Play never
took its version code.

### L10. FakeProxyRule

`before()` now runs its setup in a `try`. On a failure it calls `undo()` and rethrows, with any
cleanup failure suppressed. `undo()` signs out, reloads the app's own modules and shuts the server
down, each step whatever the previous one did, and `after()` uses it too. Compiled and ktlint-clean.

### L11 and L12

- The single `release` group and the side-by-side Worker and Play jobs stay.
- release.md says that a run for a newer tag replaces one still waiting, before any of its jobs run.
  It says to push one tag at a time and to reject or cancel a run you will not approve.
- A new step 8 says to check that both publishing jobs succeeded. A new app with the old Worker gets
  404 `path`, so run the failed job again or release the next PATCH.

### Security L4. Gradle dependency graph

The coordinator approved the metadata change.

- **`.github/workflows/dependency-graph.yml` (new):** it runs on pushes to main only, with
  `permissions: {}` at the top.
  - `generate` (`contents: read`) runs `gradle/actions/dependency-submission` v6.4.1, pinned to
    `8da46b539cd8fe6ab27ca75128919a18d757604c`, the peeled commit of the tag. It uses
    `dependency-graph: generate-and-upload` and reports only `:app`'s `prodReleaseRuntimeClasspath`.
    Those dependencies are marked runtime through `DEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS`.
    It also passes `validate-wrappers: true` and `additional-arguments: --dependency-verification
    strict`.
  - `submit` (`contents: write`) only runs `download-and-submit` with the cache off: no checkout, no
    Gradle. No secret is read anywhere.
- **Verification is back on.** The action hard-codes `-Dorg.gradle.dependency.verification=off`
  (`sources/src/actions/dependency-submission/main.ts`). In a scratch probe, a later
  `--dependency-verification strict` turned verification back on.
- **The plugin needed metadata entries.** Gradle also verifies the init script's classpath: a probe
  init script dependency failed with "Dependency verification failed for configuration
  'classpath'". That classpath is `org.gradle:github-dependency-graph-gradle-plugin:1.5.1`, a shadow
  jar with no dependencies. `gradle/verification-metadata.xml` gains its two files, with the SHA-256
  Gradle wrote, and nothing else changes:
  - jar: `4fb3ab160c677c16bd14b59a4d584f5e821eb0d1f839c049e2b764ef39605713`;
  - `.module`: `94befdc518574b3127f5b0ee4345662bbfb8dec511a889f37b50052cafaa814e`.
- **Cross-check.** The portal publishes no SHA-256, but the files match the SHA-1 and MD5 it
  publishes:
  - jar: `e80aeaeb238a2af4cbe5ac653f2a7312124b2350` and `ed502231d0a93431e7ab62befffebb76`;
  - module: `810fe5b023f391f07cc79724f3d54300f3189815` and `131b06499cfdad631a6073b6e0e197f8`.
- **Tested on the project,** with the action's init scripts from the pinned commit and its exact
  Gradle arguments, offline:
  - With the new entries, the run succeeded. The graph has 259 packages, 258 runtime plus Gradle
    itself as development. Its direct dependencies include okhttp 4.12.0, firebase-messaging 26.0.1,
    purchases 11.3.0, room-runtime 2.8.5, koin-android 4.2.2 and text-recognition 16.0.1.
  - On an export of the commit before (without the entries), it failed on the plugin's jar and
    `.module`.
- **Docs:** `docs/release.md`, in the list of scans, says that a pull request's dependency review
  does not see Gradle changes, and why:
  - only main gets a graph;
  - submitting needs write access, which fork and Dependabot pull requests lack;
  - the workflow_run workaround would accept a graph the pull request wrote.

  The comment in security.yml says the same.

## Commands run

| Command | Result |
|---|---|
| `ANDROID_HOME=/opt/android-sdk python3 -B scripts/release-checks.test.py` | 10 tests OK, about 11 s (7 before) |
| the same, with 9 deliberate breakages of the checker, one at a time | each breakage fails a test |
| `python3 -B scripts/worker-settings.test.py` | 5 tests OK; 5 breakages each fail a test |
| actionlint 1.7.12, `-shellcheck=` (my scratch copy; archive SHA-256 `8aca8db9…49a3d8` matches the release checksum file, binary identical to the archive's) | 0 findings on all 6 workflows, and on the intermediate `release.yml` of `018db01` |
| `./gradlew --init-script …/central-mirror.init.gradle --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:compileProdDebugAndroidTestKotlin` (after checking that no other Gradle build ran) | BUILD SUCCESSFUL in 20 s; only the existing deprecation warning of `createAndroidComposeRule` |
| `./gradlew … --offline --no-daemon ktlintCheck` | BUILD SUCCESSFUL |
| `./gradlew … --offline --no-daemon :app:bundleProdRelease -Pkotlin.compiler.execution.strategy=in-process` on a clean export of `7831d6a`, with `ANDROID_HOME` set and no `local.properties`, as on a runner | BUILD SUCCESSFUL in 4 min 20 s, 91 tasks executed |
| `./gradlew … --dry-run :app:generateProdReleaseBaselineProfile` | prodNonMinifiedRelease tasks only, no R8 |
| dependency graph generation, as the action runs it, with and without the new entries | success with the entries, failure on the plugin without them |
| gitleaks 8.30.1 (checksum-verified copy) on the history, and the extracted scan step in a scratch repository | as in L6 |
| npm and Wrangler on a scratch copy | as in L4 |
| the wrapper with an empty Gradle home, and with a wrong sum | as in L7 |

No Gradle build of mine overlapped another, and none was left running. I never ran `--stop`.

## CI runs

| Run | Commit | Result |
|---|---|---|
| [Security 44460220626](https://github.com/dantech0xff/split-bill-poc/actions/runs/44460220626) | `7831d6a` | success. The *Secret scan* job reported that `refs/remotes/origin/main` has no `.github/gitleaks.toml` and that it was scanning with gitleaks' default rules: 402 commits, no leaks. *Dependency review* was skipped, as for any push |
| [Device tests 44460220611](https://github.com/dantech0xff/split-bill-poc/actions/runs/44460220611) | `7831d6a` | success. [Tests on an Android 14 emulator](https://github.com/dantech0xff/split-bill-poc/actions/runs/44460220611/job/135345009629): "Starting 12 tests on pixel6Api34", "Finished 12 tests", BUILD SUCCESSFUL, which includes the changed payment request journey. [Record the baseline profile](https://github.com/dantech0xff/split-bill-poc/actions/runs/44460220611/job/135345009407): recorded and kept. The API matrix was skipped |
| [Dependency checksums 44460086481](https://github.com/dantech0xff/split-bill-poc/actions/runs/44460086481) | `5e937db`, a proxy commit pushed with my first four | still running at 04:51 UTC; I did not wait for this hour-long run. Its cache is off, so the wrapper had to download the distribution and check the pinned sum. Its Gradle step has been running since 04:42, so the wrapper accepted the distribution: a wrong sum stops the wrapper before Gradle starts |
| Device tests 44460086406 | `5e937db` | cancelled by the newer push, as its concurrency group intends |

I did not start the manual API matrix.

## Left, with the reason

- **Not run on GitHub.** `release.yml` needs a tag, which I may not create. So the called device
  tests, the settings job, the in-process signed build, the install without scripts and the
  immediate deploy were checked locally or statically. `dependency-graph.yml` runs only on main; its
  submission step (`download-and-submit`) has not run anywhere.
- **The owner's part:**
  - the tag ruleset and the environment (M1), which I may neither create nor see;
  - the three Worker values, without which every release now stops by design.
- **Pull requests' Gradle changes** are not dependency-reviewed. This is the trade-off documented in
  release.md, as the coordinator decided.
- **Outside the decided scope,** noted for later:
  - The release checks match elements by their full name. Android's manifest parser matches local
    names, so a namespaced `<x:application>` is unexamined; not verified on Android.
  - The review's L10 also noted that each reload of the Koin modules drops the Room database and
    OkHttp objects without closing them; unchanged.
- **The first runs after merge.** Pull requests into main use gitleaks' default rules until
  `.github/gitleaks.toml` is on main. The default rules are stricter, with no allowlist, and find
  nothing in the repository today.

## Unresolved questions

None.
