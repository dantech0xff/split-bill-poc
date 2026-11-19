# Releasing Split Vibe

A release starts when a version tag is pushed. The workflow
[`release.yml`](../.github/workflows/release.yml) then checks the tagged commit again, runs the
device tests on it, checks the Worker's settings, builds the app bundle and signs it with the
upload key, puts it on Google Play's internal testing track, records a D1 bookmark, applies the D1
migrations and deploys the Worker. Promoting a build to the closed, open and production tracks
stays a step in Play Console.

A pushed tag `v*` starts the workflow, which goes on only when `main` holds the tagged commit; a
pull request never starts it. That check is part of the tagged commit's own `release.yml`, so it
binds only with the repository settings in [Only the owner pushes release
tags](#only-the-owner-pushes-release-tags).
Every job that touches a secret runs in the GitHub environment `production`, and that environment's
reviewers approve each such job before it starts. Until then the job cannot read a secret, so no
key, token or account is reached without an approval.

## Making a release

1. Merge everything the release needs into `main`, and wait until CI passes on it.
2. Pick the version. The tag is `vMAJOR.MINOR.PATCH`, with MINOR and PATCH below 100 and no
   leading zeros. It gives the app's version name `MAJOR.MINOR.PATCH` and its version code
   `MAJOR * 10000 + MINOR * 100 + PATCH`, so a later version always has a higher code, which Google
   Play requires. A version code that Google Play has seen once can never be uploaded again, so a
   fixed build gets the next PATCH.

   | Tag | Version name | Version code |
   |---|---|---|
   | `v0.9.0` | 0.9.0 | 900 |
   | `v1.0.0` | 1.0.0 | 10000 |
   | `v1.2.3` | 1.2.3 | 10203 |

   The version code must also be higher than that of any build uploaded by hand before.
3. Tag the newest commit of `main` and push the tag:

   ```bash
   git fetch origin main
   ```

   ```bash
   git tag -a v1.0.0 -m "Split Vibe 1.0.0" origin/main
   ```

   ```bash
   git push origin v1.0.0
   ```

4. The run first checks that the tag is on `main` and works out the version, then runs every check
   of `ci.yml` on the commit: code style, unit tests and screenshots, lint, the packages, the
   release checks, `npm audit`, and the tests of the proxy and the web app. Beside them it runs
   the device tests on an Android 14 emulator, the end-to-end journeys among them
   ([testing.md](testing.md#in-ci)), and lists the Worker's settings in the run's summary,
   stopping when one it needs is empty ([below](#the-workers-settings)). Nothing needs approval
   yet, and nothing is signed unless all of these pass.
5. **First approval: the signed bundle.** In the run on the Actions tab, choose *Review deployments*,
   tick `production` and approve. The job writes `local.properties` from the environment's
   variables, builds `:app:bundleProdRelease` signed with the upload key, deletes the key file, makes
   the split APKs with bundletool and checks their 16 KB alignment, and runs the release checks on
   them. A variable that is not set shows as a warning that names the feature the build has off.
6. Before the second approval, read the run's summary: the version, the SHA-256 fingerprint of the
   certificate that signed the bundle, the Worker's settings that the deploy will set, and any
   warnings. The fingerprint must be the upload key's, which Play Console shows under *Test and
   release > App integrity*.
7. **Second approval: publishing.** Approve `production` again. Two jobs start together:
   - *Google Play internal testing* uploads the bundle through the Android Publisher API's edits flow
     and gives the internal track a release of it, which reaches the internal testers once Google
     Play has processed it.
   - *Worker and its database* records the D1 bookmark in the run's summary, applies the migrations
     of `proxy/migrations` that the database does not have, and deploys the Worker, moving every
     Durable Object to the new code at once (`--durable-objects-code-update-mode immediate`, as
     [proxy.md](proxy.md) asks of deploys that change what the objects keep).
8. **Check that both publishing jobs succeeded.** They do not wait for each other, so one can fail
   while the other goes through. The Worker keeps serving every app version that is out, as it
   always has ([proxy.md](proxy.md)), so a new Worker with the old app is fine. A new app with the
   old Worker is not: it gets 404 `path` from any route it needs that the old Worker lacks. Run the
   failed job again, or fix it and release the next PATCH.

A job that waits for approval and is rejected fails, and nothing after it runs. A failed job can be
run again from the run's page; it asks for approval again.

One release runs at a time. GitHub keeps one more run waiting behind it, and a run for a newer tag
takes the place of one still waiting, which is cancelled before any of its jobs runs. So push one
tag at a time, and reject or cancel a run that you will not approve: a run left waiting for
approval holds back every later release.

## The Worker's settings

`proxy/wrangler.jsonc` of the tagged commit is the Worker's production configuration. The deploy
sets every variable in its `vars` to the file's value, and so overwrites any value changed in the
Cloudflare dashboard since. Only the secrets set with `wrangler secret put` stay as they are.
Wrangler's own help says so: without `--keep-vars`, a deploy "will delete all vars before setting
those found in the Wrangler configuration".

An emergency switch made in the dashboard must therefore be committed too. This covers `AI_ENABLED`
set to `false` and a raised `MIN_APP_VERSION` ([runbook.md](runbook.md#switching-ai-off)). Commit
the same value to `proxy/wrangler.jsonc` on `main` before the next release, or make no release
until the switch is undone in the dashboard. A release made in between undoes the switch.

Before any approval, the job *The Worker's settings* writes every variable that the deploy will
set into the run's summary. It stops the run when one of these required values is empty in the
file:

| Variable | Without it |
|---|---|
| `GOOGLE_CLIENT_ID` | The Worker turns away every session and every sign-in |
| `ANDROID_CERT_SHA256` | The Worker leaves the app out of its App Links file, so invite and payment links no longer open in the app |
| `ABUSE_EMAIL` | The public pages, which show what people wrote on their payment requests and bill links, have no "Report abuse" link |

All three are public ([proxy.md](proxy.md), "Deploy it"). To fix an empty one, commit its value to
`main`, then tag that commit. The stopped run used no version code, so the same tag can be deleted
([as below](#rehearsing-on-a-test-tag)) and pushed again on the new commit. To run the same check
on a computer:

```bash
python3 scripts/worker-settings.py proxy/wrangler.jsonc
```

## What the release checks look at

[`scripts/release-checks.py`](../scripts/release-checks.py) reads the APKs with `aapt2` and fails
with the name of each check that fails:

- **Not debuggable:** no APK sets `android:debuggable`.
- **No plain HTTP:** `android:usesCleartextTraffic` is not true, the network security config, if
  there is one, permits cleartext nowhere, and the target SDK is at least 28, below which plain
  HTTP is allowed by default.
- **Proxy address is https:** the address given with `--proxy-url` is https and is in the app's
  code, and the code holds no plain-http address of that host. `ci.yml` builds the release package
  without an address and skips this check; `release.yml` gives the address the build was made with.
- **No secret keys:** no file in any APK holds a private key, encrypted or not, a PGP private key
  block, an Anthropic API key, a secret API key that starts with `sk_`, or a Google OAuth client
  secret, as text, as UTF-16 or in base64, and no file is a Java (JKS) or PKCS#12 key store, by the
  bytes such a file begins with. Zip files inside the APKs, such as a zipped asset, are searched
  too. The public keys that every copy of the app carries, such as RevenueCat's `goog_` key and
  Firebase's API key, are fine. A finding names the file and the kind of key, never the key.
- **16 KB page alignment:** every loadable (PT_LOAD) segment of every 64-bit native library is
  aligned to 16 KB or more, which phones with 16 KB memory pages need to load it. 32-bit libraries
  only ever run with 4 KB pages. `release.yml` also checks with `zipalign` that each library starts
  on a 16 KB boundary inside the split APKs.

Android reads the manifest's attributes by their resource ID and those of the network security
config by their name, whatever namespace prefix they are written with, so the checks read them the
same way.

`ci.yml` runs it on the release APK for every pull request and every push to `main`, together with
its tests, which build one APK made like a release and several deliberately bad ones. To run them on
a computer, with the Android SDK's build tools and a JDK:

```bash
ANDROID_HOME=<the Android SDK> python3 scripts/release-checks.test.py
```

```bash
ANDROID_HOME=<the Android SDK> python3 scripts/release-checks.py app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk
```

The other scans live outside `release.yml`:

- `security.yml` runs gitleaks over the whole history for every push, to any branch or tag, and for
  every pull request. Its rules and the findings it ignores are
  [`.github/gitleaks.toml`](../.github/gitleaks.toml) and
  [`.github/gitleaksignore`](../.github/gitleaksignore) as they are on `main`, or for a pull request
  on the branch it merges into, so a change to them applies once it is merged. `gitleaks:allow`
  comments count for nothing. It also reviews the dependencies that a pull request adds or
  updates, and fails on a known vulnerability of high or critical severity in any of them, whether
  it ships or is only for development.
- `ci.yml` runs `npm audit --audit-level=high` for `proxy/` and `web/`.
- [`dependency-graph.yml`](../.github/workflows/dependency-graph.yml) sends the libraries the app
  ships, the `prodReleaseRuntimeClasspath` of `:app`, to GitHub's dependency graph on every push to
  `main`, so Dependabot raises an alert for a known vulnerability in one of them. A pull request's
  dependency review does not see its Gradle changes. GitHub reviews the Gradle dependencies only of
  a commit that has a submitted graph, and only `main` gets one. Submitting a graph needs write
  access to the repository, which a run for a pull request from a fork or from Dependabot does not
  get. The usual way around that is a second workflow that submits the graph a pull request's run
  made, and it would take whatever graph that pull request's code wrote. So a Gradle change shows
  up in Dependabot's alerts once it is merged, not in the review before.
- Dependabot proposes updates of the Gradle dependencies, the npm packages of both and the actions.

## One-time setup

The owner does this once, in GitHub, Google Cloud, Play Console and Cloudflare. No value below ever
goes into the repository, a workflow file or a chat.

### Only the owner pushes release tags

For a pushed tag, GitHub runs the `release.yml` of the commit that the tag points at, not the one
on `main`. The check that `main` holds the tagged commit is part of that file. A tag on a commit
whose `release.yml` leaves the check out, or prints the secrets once approved, would therefore ask
for the same `production` approval as a real release. Two repository settings make the check
binding:

- **A tag ruleset**, under *Settings > Rules > Rulesets > New ruleset > New tag ruleset*. Name it
  `release tags` and set *Enforcement status* to *Active*. Under *Target tags*, add the pattern
  `v*`, then tick *Restrict creations*, *Restrict updates* and *Restrict deletions*. In *Bypass
  list*, add only the role *Repository admin*, which in this repository, owned by a personal
  account, is the owner alone. No collaborator, deploy key, app or bot can then create, move or
  delete a `v*` tag.
- **The `production` environment**, set up as below: only `v*` tags may use it, and the owner is its
  required reviewer.

So every run that can reach a secret starts from a tag that the owner pushed, and each of its jobs
waits for the owner's approval.

### The `production` environment

In the repository's *Settings > Environments*, create the environment `production` and set:

- **Required reviewers:** the owner. *Prevent self-reviews* stays off while the owner is the only
  reviewer, because the person who pushes the tag must be able to approve it.
- **Allow administrators to bypass configured protection rules:** off. Otherwise an administrator
  can run the jobs without an approval.
- **Deployment branches and tags:** *Selected branches and tags*, with one rule of type *Tag* and the
  pattern `v*`. No branch can then use the environment at all.

Then add the secrets and variables below to the environment, not to the repository: only jobs that
run in `production` see them, and only after an approval.

| Name | Kind | What goes in |
|---|---|---|
| `UPLOAD_KEYSTORE_BASE64` | secret | The upload keystore file, in base64 on one line |
| `UPLOAD_KEYSTORE_PASSWORD` | secret | The keystore's password |
| `UPLOAD_KEY_ALIAS` | secret | The alias of the upload key in it |
| `UPLOAD_KEY_PASSWORD` | secret | The upload key's password |
| `CLOUDFLARE_API_TOKEN` | secret | The Cloudflare API token described below |
| `CLOUDFLARE_ACCOUNT_ID` | variable | The Cloudflare account's ID |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | variable | The provider's full name, `projects/<project number>/locations/global/workloadIdentityPools/github/providers/split-vibe` |
| `GCP_SERVICE_ACCOUNT` | variable | The service account's email, `play-publisher@<project id>.iam.gserviceaccount.com` |
| `AI_PROXY_URL` | variable | The Worker's https address, such as `https://split-vibe-proxy.<subdomain>.workers.dev`; required |
| `GOOGLE_WEB_CLIENT_ID` | variable | The app's web client ID at Google; without it signing in is off |
| `REVENUECAT_API_KEY` | variable | RevenueCat's public SDK key for the Play app, `goog_…`; without it Pro is not sold |
| `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` | variables | The Firebase settings of the Android app, all four or none; without them push is off |

The variables are the same public values that `local.properties` holds for a build on a computer
(see [proxy.md](proxy.md) and [pro-subscription.md](pro-subscription.md)); they ship inside the app.
With the GitHub CLI, a secret is read from standard input and a variable given on the command line:

```bash
gh secret set UPLOAD_KEYSTORE_PASSWORD --env production
```

```bash
gh variable set AI_PROXY_URL --env production --body "https://split-vibe-proxy.<subdomain>.workers.dev"
```

### The upload key

Google Play signs the app that phones get with its own app signing key; the upload key only proves
that a bundle comes from the owner. If there is none yet, make one and keep the file and both
passwords offline, as Play Console needs the same key for every upload:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Put it into the secret in base64 (on macOS, `base64 -i upload-keystore.jks`):

```bash
base64 -w 0 upload-keystore.jks | gh secret set UPLOAD_KEYSTORE_BASE64 --env production
```

The workflow decodes it into a file in the runner's temporary directory that only its user can
read, and deletes it after the build. If the key is ever lost or exposed, ask for an upload key
reset in Play Console under *Test and release > App integrity*.

### Google Play through Workload Identity Federation

The upload to Google Play uses no key file. The job exchanges its GitHub OIDC token, through a
Workload Identity pool, for a one-hour access token of a service account that Play Console allows
to release to testing tracks, and nothing else. The service account that RevenueCat uses
([pro-subscription.md](pro-subscription.md)) is a different one, with other permissions and a key;
do not reuse it here.

1. In a Google Cloud project, enable the APIs the exchange and the upload need:

   ```bash
   gcloud services enable iam.googleapis.com cloudresourcemanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com androidpublisher.googleapis.com --project=<project id>
   ```

2. Create the service account, and no key for it:

   ```bash
   gcloud iam service-accounts create play-publisher --project=<project id> --display-name="Play publisher for release.yml"
   ```

3. Create the pool:

   ```bash
   gcloud iam workload-identity-pools create github --project=<project id> --location=global --display-name="GitHub Actions"
   ```

4. Create the provider. Its condition admits only tokens of jobs in the environment `production`
   of this repository, named by GitHub's numeric IDs, which unlike names are never reused. The IDs
   come from `gh api repos/dantech0xff/split-bill-poc --jq '.id, .owner.id'`.

   ```bash
   gcloud iam workload-identity-pools providers create-oidc split-vibe --project=<project id> --location=global --workload-identity-pool=github --display-name="split-bill-poc releases" --issuer-uri="https://token.actions.githubusercontent.com" --attribute-mapping="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment" --attribute-condition="assertion.repository_id == '<repository id>' && assertion.repository_owner_id == '<owner id>' && assertion.environment == 'production'"
   ```

5. Let exactly that subject act as the service account. A job in the environment `production` has
   the subject `repo:dantech0xff/split-bill-poc:environment:production`.

   ```bash
   gcloud iam service-accounts add-iam-policy-binding play-publisher@<project id>.iam.gserviceaccount.com --project=<project id> --role=roles/iam.workloadIdentityUser --member="principal://iam.googleapis.com/projects/<project number>/locations/global/workloadIdentityPools/github/subject/repo:dantech0xff/split-bill-poc:environment:production"
   ```

6. Put the provider's full name into `GCP_WORKLOAD_IDENTITY_PROVIDER` and the service account's
   email into `GCP_SERVICE_ACCOUNT`. This prints the name:

   ```bash
   gcloud iam workload-identity-pools providers describe split-vibe --project=<project id> --location=global --workload-identity-pool=github --format="value(name)"
   ```

7. In Play Console, under *Users and permissions*, invite the service account's email. Give it no
   account permission; under *App permissions*, add Split Vibe and tick only **Release apps to
   testing tracks**, which lets it upload bundles and roll out testing-track releases but never
   publish to production.

Before the first run, the app must already have had a release rolled out by hand on a testing
track, as the setup for Pro does ([pro-subscription.md](pro-subscription.md), "Before you start").
Until then the app is a draft, for which Google Play accepts only draft releases through the API,
and the upload fails with "Only releases with status draft may be created on draft app". The
internal track also needs its list of testers in Play Console.

The commit of an edit is made with `changesInReviewBehavior=ERROR_IF_IN_REVIEW`: while other
changes of the app are in review, such as a production release, the upload fails and says so,
instead of cancelling that review as the API's default would. Run the job again once the review is
over. The run keeps the signed bundle for 30 days. After that the job can no longer find it: run
the whole run again (*Re-run all jobs*), which checks, builds and signs the same version again and
asks for both approvals. That is safe, as the failed upload's edit was deleted without being
committed, so Google Play never took its version code.

### The Cloudflare token

The Worker job needs to read and write D1 and to deploy the Worker, nothing more. In the Cloudflare
dashboard, under *Manage account > Account API tokens*, create a custom token with:

- **Account > Workers Scripts > Edit**, which may deploy an existing Worker but not create or
  delete one; where the dashboard offers it, only for the Worker `split-vibe-proxy`;
- **Account > D1 > Edit**, for the bookmark and the migrations;
- the one account as its only resource, and an expiry date, with a reminder to make a new token
  before it.

No zone permission is needed while the Worker has no routes or custom domains, only its
`workers.dev` address; adding one later needs *Zone > Workers Routes > Edit* for that zone. Wrangler
takes the account from `CLOUDFLARE_ACCOUNT_ID`, so it never lists accounts or reads user details.
If an account token does not work for a step, a user token under *My Profile > API Tokens* with the
same two permissions, for the one account, does the same.

The token deploys only. The Worker's secrets stay as the owner set them with `wrangler secret put`
([proxy.md](proxy.md), "Deploy it"); its variables are those of `proxy/wrangler.jsonc`
([above](#the-workers-settings)). The first deploy of the Worker is made by hand, as the token
cannot create one.

### The dependency graph

`dependency-graph.yml` can send the app's libraries to GitHub only while the repository's dependency
graph is on. Turn it on in the repository's *Settings*, under *Security and quality > Advanced
Security*, with *Enable* next to *Dependency graph*, and check that Dependabot alerts are on, since
they are what reports a vulnerable library. Until the graph is on, every push to `main` fails that
workflow at "Send them to GitHub", and no alert covers the Android libraries.

## Rehearsing on a test tag

The two approvals make a rehearsal that changes nothing outside GitHub:

1. Once the setup above is done and the Worker's required settings are committed
   ([above](#the-workers-settings)), push a test tag on `main`, such as `v0.9.0`, with a version
   code below that of the first real release.
2. Approve the signed bundle, then read its job: the build settings, the signature, the 16 KB check
   and the release checks.
3. Reject the second approval. Nothing reaches Google Play or Cloudflare, and the version code is
   not used.
4. Delete the test tag:

   ```bash
   git push --delete origin v0.9.0
   ```

   ```bash
   git tag --delete v0.9.0
   ```

To rehearse the publishing as well, approve the second time instead: the bundle goes to the
internal testers with that version code, which can then never be uploaded again, and the Worker is
deployed from the tagged commit of `main`. Rehearse a D1 restore only on a database made for the
purpose, as a restore overwrites the database it is given.

## Rolling back

### The app

Google Play never installs a lower version code over a higher one, so a bad build is replaced
rather than undone: revert the change on `main` and release it with the next PATCH. While a staged
rollout to production is under way, *Halt rollout* on the release in Play Console stops it from
reaching more people.

### The Worker

Roll back to the version before, or to a version ID from the list of deployments:

```bash
npx wrangler rollback --message "<why>"
```

```bash
npx wrangler deployments list
```

Run them from `proxy/`, signed in with `npx wrangler login`. A rollback serves an earlier version of
the Worker, its code and its settings; D1 keeps its data and its schema. It cannot go back across a
change of the Durable Object classes, and it reaches only the last 100 versions.
[proxy.md](proxy.md) ("Deploy it") says what happens on a rollback across the first deploys of
weighted calls and of Pro.

### The database

The migrations so far only add tables and columns, so the Worker from before them still works with
the database after them. Restore D1 only when a migration damaged data: every release run has the
bookmark from before its migrations in its summary, and this puts the whole database back to that
point, losing everything written since:

```bash
npx wrangler d1 time-travel restore split-vibe --bookmark=<bookmark>
```

The restore prints a bookmark of its own, which undoes it. Time Travel keeps 7 days on the Workers
Free plan and 30 days on the Paid plan, so a bookmark older than that can no longer be restored.
