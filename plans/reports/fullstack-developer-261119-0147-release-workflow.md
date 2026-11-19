# Release workflow and CI checks of the release phase

Date: 19/11/2026. Branch `feat/phase-09-quality-compliance-release`.

## Outcome

`release.yml` releases a version tag on `main` in two approved stages: the signed bundle, then the
upload to Play's internal track and the Worker deploy. The release checks are a tested script that
CI and the release both run, the secret scan now covers every push, pull requests get dependency
review, CI audits both npm lock files, and Dependabot covers the web app. `docs/release.md` holds
the run and the owner's one-time setup. Every workflow passes actionlint 1.7.12.

## Files

| File | Lines | Change |
|---|---|---|
| `.github/workflows/release.yml` | 398 | new |
| `.github/workflows/security.yml` | 61 | new |
| `.github/workflows/ci.yml` | 160 | callable, release checks, npm audit, secret scan moved out |
| `.github/dependabot.yml` | 31 | npm entry for `/web` |
| `scripts/release-checks.py` | 321 | new |
| `scripts/release-checks.test.py` | 246 | new |
| `docs/release.md` | 319 | new |

Commits: `0e1ed99` (secret scan on every push, dependency review), `6b302a6` (npm audit,
Dependabot for web), `5d5ec35` (release checks and their tests in CI), `344bb73` (release workflow
and docs), and this report.

## What was built

### `.github/workflows/release.yml`

Triggered only by `push` of tags `v*`; `permissions: {}` at the top, one concurrency group that is
never cancelled.

| Job | Environment | Permissions | Does |
|---|---|---|---|
| `version` | none | `contents: read` | `git merge-base --is-ancestor HEAD refs/remotes/origin/main`; parses `vMAJOR.MINOR.PATCH` (MAJOR < 100000, MINOR and PATCH < 100, no leading zeros) into version name and code `MAJOR*10000 + MINOR*100 + PATCH` (`v1.0.0` is 10000) |
| `checks` | none | `contents: read` | calls `ci.yml` (now also `on: workflow_call`): style, unit tests and screenshots, lint, packages, release checks, npm audit, proxy and web tests |
| `bundle` | `production` | `contents: read` | clean Gradle build (cache off); `local.properties` from environment variables (proxy address required, the others warn naming the feature that is off; values with whitespace or a backslash refused); keystore decoded from `UPLOAD_KEYSTORE_BASE64` into `$RUNNER_TEMP` with umask 077, deleted by an `if: always()` step; `./gradlew --no-daemon :app:bundleProdRelease -Psplitvibe.versionCode=… -Psplitvibe.versionName=…`; signer's SHA-256 fingerprint into the run summary; bundletool 1.18.3 (SHA-256 checked) `build-apks`, then `zipalign -c -P 16 -v 4` on every split APK; release checks with `--proxy-url`; bundle kept as an artifact for 7 days |
| `worker` | `production` | `contents: read` | `npm ci` for proxy and web, `REQUIRE_WEB=1 npm run build:web`, then with the token only in these steps' env: `wrangler d1 time-travel info split-vibe --json` (bookmark into the log and the run summary with the restore command), `wrangler d1 migrations apply split-vibe --remote`, `wrangler deploy` |
| `play` | `production` | `id-token: write` only | downloads the bundle; `google-github-actions/auth` with WIF, `service_account`, `token_format: access_token`, scope `androidpublisher`, no credentials file, no exported variables; edits flow with curl: insert edit, `bundles.upload` (checks the version code Play read), `tracks.update` internal with status `completed`, `edits:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW`; an edit not committed is deleted by an EXIT trap; the token reaches curl through `--config -` on standard input, never argv |

`bundle` needs `version` and `checks`; `worker` and `play` both need `bundle`, so they wait for the
same, second approval and start together.

### `.github/workflows/security.yml` (new)

- `Secret scan`: the gitleaks 8.30.1 job moved out of `ci.yml` unchanged in substance (whole
  history, SHA-256-checked archive, now unpacked into `$RUNNER_TEMP`), triggered by every `push`
  (any branch or tag) and every `pull_request`. The job name stays `Secret scan`, so a required
  status check of that name still matches.
- `Dependency review`: pull requests only, `actions/dependency-review-action` v5.0.1 with
  `fail-on-severity: high`.

### `.github/workflows/ci.yml`

- `on: workflow_call` added; header says release.yml calls it and where the scans went.
- `checks` job: build tools 36.0.0 through sdkmanager, `ANDROID_BUILD_TOOLS` exported, the tests of
  the release checks, then the release checks on `app-prod-release-unsigned.apk`.
- `proxy` job: `npm audit --audit-level=high` for `proxy/` and `--prefix ../web`.
- The `secrets` job is gone (now in `security.yml`).

### `.github/dependabot.yml`

The Gradle entry already existed. Added an npm entry for `/web` beside the one for `/proxy`.

### `scripts/release-checks.py` and `scripts/release-checks.test.py` (new)

Python 3 standard library plus `aapt2` from the build tools (`ANDROID_BUILD_TOOLS`, else the newest
under `ANDROID_HOME`/`ANDROID_SDK_ROOT`). Takes one whole APK, or every APK that bundletool makes
(several base APKs, one per Android version range, and their splits; all must be one app). Checks:
not debuggable (every APK); no plain HTTP on every base APK (`usesCleartextTraffic`, every file of
the network security config resolved through `aapt2 dump resources`, target SDK at least 28); proxy
address https and present in the dex with no `http://<same host>` (skipped without `--proxy-url`);
no secret in any entry, as text or UTF-16: private key blocks (also JSON-escaped), `sk-ant-`,
`sk_` (24+ characters after an optional `live_`/`test_`), `GOCSPX-`. Findings name the file and
the kind, never the value. Exit 0 pass, 1 a check failed, 2 unreadable input; failures also as
`::error` annotations in Actions.

The tests build real APKs with aapt2, javac and d8 and run the checker as a subprocess: a package
made like a release passes (with a `goog_` key, a Firebase-style key and a certificate in it); a
deliberately bad one fails all four checks, naming each file, and never prints a secret; plus a
target below 28, a non-https address, no address (skip), bases and splits of one bundle together,
two apps or splits alone refused, and files that are not APKs. Fake keys are assembled at run time
so the repository holds nothing gitleaks would flag.

### `docs/release.md` (new)

Making a release (tag format and version codes, the two approvals and what to read before each),
what the release checks look at and how to run them locally, the one-time setup (the `production`
environment and its rules, a table of every secret and variable with what goes in, the upload key,
Workload Identity Federation with gcloud commands and the Play Console permission, the Cloudflare
token's scopes), rehearsing on a test tag, and rolling back the app, the Worker and D1.

## How each part was validated

- **actionlint 1.7.12**, downloaded into its own scratchpad directory, SHA-256
  `8aca8db9…49a3d8` checked against the release's checksum file, run from the repository: 0 errors
  in all 5 workflows, and in each of the four intermediate states of `ci.yml` committed. Its
  shellcheck rule was off, see below.
- **Shell steps:** all 31 `run:` scripts of release.yml, ci.yml and security.yml extracted with
  PyYAML and parsed with `bash -n`; none interpolates `${{ }}` (every value goes through `env`).
- **Version step:** 13 tags; `v1.0.0` 10000, `v0.9.0` 900, `v2.13.7` 21307, `v99999.99.99`
  999999999, `v0.0.1` 1; refused: `v0.0.0`, `v1.0`, `v1.0.0-rc1`, `v01.0.0`, `v1.100.0`, `v1.2.3x`,
  `1.2.3`, `v100000.0.0`.
- **Build settings step:** all set, only the proxy (warnings name each feature off), no proxy
  (error), a line break and a backslash in a value (refused).
- **Play upload step** against a local fake of the edits API: success (insert, upload as
  octet-stream with all bytes, track body `{"track":"internal","releases":[{"name":"1.0.0",
  "versionCodes":["10000"],"status":"completed"}]}`, commit with `ERROR_IF_IN_REVIEW`, bearer
  header arrived from standard input); commit refused (Google's message as `::error`, edit
  deleted, exit 1); wrong version code read back (error, edit deleted).
- **Signature step:** `keytool -printcert -jarfile` prints the SHA-256 fingerprint of a jar signed
  with a throwaway key; on an unsigned file it prints "Not a signed jar file" with exit 0, so the
  step's own error fires.
- **Secret scan step:** run as extracted on this repository: 348 commits, no leaks. gitleaks on
  the new scripts, docs and `.github`: no leaks.
- **npm audit:** `npm audit --audit-level=high` in proxy and web today: 0 vulnerabilities.
- **Release checks tests:** 7 pass in about 13 s. Nine deliberate breakages of the checker (no
  UTF-16 scan, debuggable ignored, network config ignored, plain http ignored, target ignored,
  secret printed, only the first base checked, two apps accepted, splits not combined) each make
  them fail.
- **Real artifacts**, built from an export of the committed HEAD in the scratchpad without
  `local.properties` (as CI builds), because the shared working tree did not compile during the
  app agent's string migration:
  - prod debug APK (a natural bad input): fails Not debuggable, No plain HTTP (the debug network
    config permits 127.0.0.1) and, given `http://127.0.0.1:8787`, the proxy check; given
    `https://127.0.0.1:8787` it reports the missing https address and the plain-http one; no
    false secret in 618 files.
  - unsigned prod release APK (what `ci.yml` checks): passes, 197 files, 4 s.
  - the release AAB through the extracted 16 KB step: bundletool made 258 split APKs, all pass
    `zipalign -c -P 16`; the release checks on all 258 pass in 14 s with 1107 files searched, and
    fail as they should with an address the build does not hold.
  - a rehearsal of the whole bundle job on the export, its steps run as extracted from
    `release.yml`: Build settings wrote `local.properties` from well-formed test values, which
    Gradle accepted; Upload key decoded a throwaway PKCS12 key into a file of mode 0600; Gradle
    signed `:app:bundleProdRelease` through the `upload` signing config with
    `-Psplitvibe.versionCode=900 -Psplitvibe.versionName=0.9.0`, and the bundle's manifest says
    900 and 0.9.0; the delete step left the temporary directory empty; Signature printed the
    throwaway certificate's SHA-256, the same as `keytool -list` shows; 16 KB passed on all 258
    splits; and the release checks with `--proxy-url https://split-vibe-proxy.example.workers.dev`
    passed all four. So R8 keeps the configured address whole in the code, and the test `goog_`
    and Firebase-style values are not taken for secrets.
  - Not rehearsed, as they need the owner's accounts: the Workload Identity exchange, the real Play
    upload, and Wrangler against Cloudflare. The Play step's logic ran against the fake API above.

## Facts checked, with sources

- Action pins, resolved with `git ls-remote --tags` and the new ones cloned at their tag (all three
  run on node24): actions/checkout v7.0.1 `3d3c42e5…`, actions/setup-java v6.3.0 `d1374f8a…`,
  gradle/actions v6.4.1 commit `8da46b53…` (annotated tag `7296c772…`), actions/upload-artifact
  v7.0.2 `cf430e03…`, actions/setup-node v7.1.0 `949feb24…` (all as already pinned and still the
  latest), and new: actions/download-artifact v8.0.2 `9000827c…`, google-github-actions/auth
  v3.0.0 `7c6bc770…`, actions/dependency-review-action v5.0.1 `bbc44d14…`.
- gitleaks 8.30.1 is the latest tag; the asset digest on
  [its release](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1) is the pinned
  `551f6fc8…f2470eb`. bundletool 1.18.3 is the latest tag; GitHub's digest for
  `bundletool-all-1.18.3.jar` on [its release](https://github.com/google/bundletool/releases/tag/1.18.3)
  is `a099cfa1…1028e29`, and the download matched. actionlint
  [v1.7.12](https://github.com/rhysd/actionlint/releases/tag/v1.7.12) is the latest release.
- [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes): `zipalign -c -P 16
  -v 4 APK_NAME.apk`, build tools 35.0.0 or higher; updates that do not support 16 KB cannot be
  released from 1 February 2027. bundletool's own help: without `--ks` it signs with the debug
  keystore if one exists, otherwise the APKs stay unsigned, which zipalign and aapt2 accept.
- Android Publisher API: [the edits workflow](https://developers.google.com/android-publisher/edits)
  (one open edit per user; a commit invalidates other edits; Console changes win),
  [bundles.upload](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles/upload)
  (upload URI, scope `androidpublisher`, long timeout advised),
  [tracks](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks) (release
  fields, status `completed`), [commit](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit)
  (`changesInReviewBehavior` defaults to cancelling a review in progress; `ERROR_IF_IN_REVIEW` fails
  instead and keeps the edit valid). [Getting started](https://developers.google.com/android-publisher/getting_started)
  for inviting the service account. Play Console's
  [permissions](https://support.google.com/googleplay/android-developer/answer/9844686): "Release
  apps to testing tracks" is app-level and cannot publish to production.
- Draft apps accept only draft releases through the API: reported by third parties, not found in
  Google's docs, e.g. [Codemagic](https://docs.codemagic.io/troubleshooting/common-google-play-errors/).
- Workload Identity Federation:
  [google-github-actions/auth v3.0.0 README](https://github.com/google-github-actions/auth/blob/v3.0.0/README.md)
  and its security considerations (attribute condition always; numeric `repository_id` and
  `repository_owner_id`; `roles/iam.workloadIdentityUser` on the service account for
  `token_format: access_token`; the action calls the IAM Credentials API's `generateAccessToken`);
  [Google Cloud's guide](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
  (enable IAM, Resource Manager, Service Account Credentials and Security Token Service; `principal://…/subject/SUBJECT`);
  [GitHub's OIDC reference](https://docs.github.com/en/actions/reference/security/oidc) (subject
  `repo:ORG/REPO:environment:NAME` for a job with an environment; `id-token: write` needed).
- GitHub [environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
  (up to six reviewers, optional self-review prevention, required reviewers on public repositories
  on every plan, administrators may bypass by default and that can be switched off, selected
  branches and tags, environment secrets unreadable until approval, `vars` context) and
  [reviewing deployments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/review-deployments)
  (approval is per waiting job). The
  [dependency graph's ecosystems](https://docs.github.com/en/code-security/reference/supply-chain-security/dependency-graph-supported-package-ecosystems)
  read `package-lock.json` and workflows, and Gradle only through dependency submission.
  download-artifact's README: a token is needed only for another run's artifacts.
- D1 Time Travel: [Cloudflare's reference](https://developers.cloudflare.com/d1/reference/time-travel/)
  (30 days on Workers Paid, 7 on Free; restore overwrites in place and returns a bookmark that
  undoes it), and the installed Wrangler 4.162.0: `d1 time-travel info <db> --json` prints only the
  API's `{bookmark}` (no banner), and in a non-interactive run `d1 migrations apply` takes its
  confirmation's fallback, yes.
- Cloudflare tokens: [permissions](https://developers.cloudflare.com/fundamentals/api/reference/permissions/),
  [Workers authorization](https://developers.cloudflare.com/workers/authorization/workers/)
  (Workers Scripts Edit maps to Editor, which deploys an existing Worker but cannot create one; a
  D1 binding needs no D1 permission to deploy, querying D1 does),
  [authorization overview](https://developers.cloudflare.com/workers/authorization/) (granular
  Wrangler permissions need an account-owned token),
  [account-owned tokens](https://developers.cloudflare.com/fundamentals/api/get-started/account-owned-tokens/)
  (Manage account > Account API tokens, optional expiry). Wrangler's source: with
  `CLOUDFLARE_ACCOUNT_ID` set it never lists accounts.
- [Worker rollbacks](https://developers.cloudflare.com/workers/configuration/versions-and-deployments/rollbacks/)
  (last 100 versions; blocked across Durable Object class changes; resources unchanged) and
  `wrangler rollback --help`.

## Differences from the contract, and why

- **Two approvals, not one.** Approval is per job, and the secrets are split across three jobs so
  that the Gradle build (the most third-party code) never shares a machine with the Cloudflare
  token or the Play token, and the Play token never shares one with `npm ci`. The second gate also
  lets the reviewer compare the signer's fingerprint first, and makes a rehearsal that publishes
  nothing (approve the build, reject the publishing).
- **The Worker deploy and the Play upload run side by side** after the second approval, not one
  after the other: ordering them would cost a third approval. The Worker stays compatible with
  every app that is out, so either order is safe for the internal track.
- **Release checks in `ci.yml` run for pull requests as well as pushes to `main`.** They read no
  secret and take seconds, and a bad change is caught before it merges. Without a proxy address in
  the CI build, the address check is skipped there and runs only in `release.yml`.
- **Optional build settings only warn.** Only `AI_PROXY_URL` is required; a missing web client ID,
  RevenueCat key or Firebase setting shows as a warning naming the feature that is off, which the
  reviewer sees before the second approval. Gradle still checks every value's form.
- **The checker is Python**, a language the repository did not use: its standard library reads
  APKs (zip) without dependencies, and Python 3 is on GitHub's Ubuntu runners.
- **shellcheck was not run.** Downloading a shellcheck binary was refused by this session's
  permission checks, so actionlint ran without its shellcheck rule; the shell steps were checked
  with `bash -n` and run as described above instead.
- **sdkmanager** now prints that it is deprecated in favour of `android sdk`; it still works
  (exit 0). The steps that call it are a safety net, as AGP 9.1.1 installs build tools 36.0.0 itself.

## For other phases and the owner

- **Device tests and the release.** `device-tests.yml` (owned by the device-test agent) now says
  "A release waits for these to pass", but it has no `workflow_call` trigger, so `release.yml`
  cannot run it. If the release must wait for them, that file needs `workflow_call:` and
  `release.yml` a job `uses: ./.github/workflows/device-tests.yml` that `bundle` needs.
- **Docs owned by others are now out of date:** `docs/architecture.md` ("Release signing" says the
  release workflow does not exist yet; "Checks" says two workflows run the checks and lists the
  secret scan under `ci.yml`), and README's list of checks. The required status checks keep their
  names ("Style, tests, lint and packages", "AI proxy", "Secret scan").
- **CI on this branch:** `ci.yml` runs only for pull requests and pushes to `main`, and no pull
  request is open for this branch, so it did not run on these pushes; its new steps were run here
  as described above, and it will first run on GitHub with a pull request or on `main`.
  `security.yml` ran on every push of this branch and its Secret scan passed each time, among them
  run 44447980436 on `344bb73`, the release workflow's commit; Dependency review was skipped, as a
  push is not a pull request. `release.yml` did not run, as no tag was pushed.
- The owner's setup in `docs/release.md` is not done yet; nothing was created on GitHub, Google
  Cloud, Play Console or Cloudflare.

## Unresolved questions

1. Should `release.yml` wait for the device tests (see above)?
2. Is one approval for the whole release preferred over two, at the cost of one machine holding the
   upload key, the Cloudflare token and the Play token together?
