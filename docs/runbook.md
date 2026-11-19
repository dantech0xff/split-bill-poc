# Runbook

How to run Split Vibe once it is live: the switches, the limits, what to do when a provider fails,
backups and the restore drill, secrets, the counters, and requests from users. It is written for the
owner, who holds the accounts at Cloudflare, Anthropic, Google, RevenueCat and Play Console.

Commands run from `proxy/`, with Wrangler signed in to the owner's Cloudflare account
(`npx wrangler login`). The Worker is `split-vibe-proxy` and its D1 database `split-vibe`, as named
in `proxy/wrangler.jsonc`. A release, with the Worker's deploy, goes through the release workflow
that [release.md](release.md) describes; how each part of the proxy behaves is in
[proxy.md](proxy.md).

## Where to look

| What | Where |
|---|---|
| The Worker's logs and traces | Cloudflare dashboard > Workers & Pages > `split-vibe-proxy` > Observability. Logs are kept 3 days on the Workers Free plan and 7 on Workers Paid; traces 7 days. |
| The daily run | The same Worker > Settings > Trigger Events > View events: the last 100 runs of the cron |
| Use and AI cost | `npm run metrics`, [below](#reading-the-counters) |
| Crashes and freezes | Play Console > Android vitals |
| Subscriptions | RevenueCat's dashboard |
| AI spend | The Anthropic console: usage, and the monthly spend limit |
| The database | `npx wrangler d1 time-travel info split-vibe`, and the D1 page of the Cloudflare dashboard |

The Worker's own log lines are JSON with an `event` field, never holding an ID, a token, a name or
a text: `bill_analysis` and `split_revision` per AI call (outcome, caller kind, model, tokens, cost,
time), `reminders` per daily run, `subscription_event` per RevenueCat event, and errors such as
`request_failed`, `revenuecat_unavailable`, `revenuecat_delete_failed`, `google_keys_unavailable`
and `not_configured`. [proxy.md](proxy.md#what-it-keeps) lists them.

## Switching AI off

`AI_ENABLED` in `proxy/wrangler.jsonc` turns bill reading and edits by a sentence on only while it is
exactly `"true"`. Use it when the AI costs run away, when the model misbehaves, or for the drill.

1. In the Cloudflare dashboard, go to Workers & Pages > `split-vibe-proxy` > Settings > Variables and
   Secrets, edit `AI_ENABLED` to `false`, and select Deploy. This takes effect at once, without a
   release.
2. Commit the same value to `proxy/wrangler.jsonc` on `main` in the same hour. Every release
   deploys the repository's values and overwrites any change made in the dashboard
   ([release.md](release.md#the-workers-settings)), so a release made before that commit turns AI
   back on. Until the commit is on `main`, make no release.
3. Check: a bill read from the app answers 503 `ai_off`.

What people see: "Reading bills with AI is switched off for now. Please try again later." Typing a
bill in by hand, splitting it, groups, requests and everything else keep working, and nothing is
charged. `GET /v1/usage` reports `aiEnabled: false`, so the app knows before the next read.

To switch it back, set `"true"` the same way, in the dashboard and in the file on `main`: while the
file still says `"false"`, the next release switches AI off again. The plan's drill: switch it off,
check that the very next read in the app falls back to typing in by hand, switch it on, and note
the result in `plans/reports/`.

## Forcing an update

`MIN_APP_VERSION` is the lowest version code that may read bills and edit splits. An older app gets
426 `update_required` and says "Update Split Vibe to keep reading bills." or "Update Split Vibe to
keep changing splits with AI." Signing in, the calls left, the plan, the exchange rates, the shared
data and deleting the account keep working for old apps, so nobody is locked out of their data.

Raise it the way `AI_ENABLED` is changed, to the version code of the release that must be the
minimum: in the dashboard, and committed to `proxy/wrangler.jsonc` on `main`. A release deploys the
file's value and overwrites the dashboard's, so a value raised only in the dashboard drops back at
the next release; until the commit is on `main`, make no release. The release workflow sets the
version code from the tag ([release.md](release.md)). Raise it only once that release is at 100% on
Google Play and has had a day to reach phones, or the people still waiting for the update lose the
AI.

## Spend caps

The caps are in [`proxy/src/policy.ts`](../proxy/src/policy.ts), counted over fixed 24-hour windows
in millionths of a dollar:

| Name | Now | What it caps |
|---|---|---|
| `callerCapMicro` | 3,000,000 ($3) | One install or one account |
| `ipCapMicro` | 6,000,000 ($6) | One network address (an IPv6 /64) |
| `globalCapMicro` | 20,000,000 ($20) | The whole proxy |

Each AI call holds the most it could cost while it runs and settles at what it cost. Besides the
caps, `ip.perMinute` (10) and `ip.perDay` (100) limit the calls per address, and `trialCalls`,
`monthlyCalls` and `proCalls` (3, 10, 300) the calls per caller.

To change a cap, edit `policy.ts`, run `npm run typecheck` and `npm test`, and release the Worker
through the release workflow. Lower the global cap at once when costs run away; switch AI off if
that is not fast enough. Raise it as accounts move to Pro: at about 6 cents a bill read, one Pro
account that uses its 300 calls costs some $18 a month ([proxy.md](proxy.md#calls-and-limits)).

Keep a monthly spend limit in the Anthropic console as well, above what the global cap allows in a
month, so that a fault in the proxy's counting cannot run up an unbounded bill.

What people see when a cap holds:

- the global cap: 429 `busy`, "The AI is busy right now. Try again in about N minutes.";
- a caller's or an address's cap, or an address's limit: 429 `spend_limited` or `rate_limited`,
  "Too many bills were read just now." with when to try again;
- the calls used up: 429 `quota_exhausted`, with when they come back and, in a build that sells it,
  an offer of Pro.

Typing bills in by hand never counts against anything.

## When a provider fails

| Provider | What the proxy does | What people see | What keeps working |
|---|---|---|---|
| Anthropic overloaded or rate limiting | `busy`; a call that got no answer is not charged | "The AI is busy right now." with a wait | Everything but the AI |
| Anthropic slow | `timeout` after 120 seconds | "Reading the bill took too long. Please try again." | Everything but the AI |
| Anthropic failing otherwise, or the key revoked | `upstream`; not charged | "Split Vibe's server had a problem. Try again in a moment." | Everything but the AI |
| RevenueCat | `POST /v1/plan` 502 `subscription_unavailable`; the webhook 503, which RevenueCat sends again; account deletion 502, deleting nothing | On the Pro screen: "Split Vibe's server could not check your subscription just now. Try again in a moment." A deletion fails with "Split Vibe's server had a problem. Try again in a moment." | Pro members keep Pro until the end of what RevenueCat last reported plus 24 hours (`proGraceMs`); everything else |
| Google Play | Nothing on the proxy | The Pro screen cannot show plans or take a purchase: "Google Play has a problem right now. Try again later." or "Pro cannot be bought right now. Please try again later." | Existing Pro, as the proxy decides it from RevenueCat |
| Google sign-in (its keys cannot be fetched) | `POST /v1/session` 503 `sign_in_off` | "Signing in is not available right now. Please try again later." | Sessions already issued, for up to 30 days |
| Firebase Cloud Messaging | Notifications are not sent; nothing fails | No push notices | Everything; the requests and groups screens still show the news |
| Frankfurter | The last exchange rates are kept and handed out; `fetchedAt` shows their age | Rates may be a day or more old | Everything |

For Anthropic, check the logs: `bill_analysis` lines with `upstream`, `busy` or `timeout`, and their
share of all reads. When the failure lasts, switching AI off shows people a clear message instead of
an error at each try. For a revoked or wrong key, set a new one ([below](#rotating-secrets)).

For RevenueCat, the logs show `revenuecat_unavailable` with the status or the error's name. A 401
there means the secret key is wrong or revoked.

## RevenueCat's webhook failing

RevenueCat counts any answer but 200 as a failure, and sends a failed event again five times over
about 80 minutes. An event is only a sign that something changed: whenever someone opens the Pro
screen, buys or restores, the app has the proxy read the plan itself with `POST /v1/plan`, so a lost
event delays a change of plan; it does not lose it.

| The webhook answers | Meaning | Fix |
|---|---|---|
| 401 `webhook_auth` | The Authorization value RevenueCat sends is not `REVENUECAT_WEBHOOK_AUTH` | Set the same value in both places ([below](#rotating-secrets)) |
| 503 `not_configured` | A secret is missing on the Worker | Set `REVENUECAT_WEBHOOK_AUTH` and `REVENUECAT_SECRET_KEY` |
| 503 `subscription_unavailable` | RevenueCat's API could not be read for an account | Wait; RevenueCat sends the event again |
| 500 `internal` | An account's `QuotaBook` could not be asked | Check the logs for `request_failed`; RevenueCat sends the event again |
| 413 `too_large`, 400 `input` | The body is over 64 KB or has no `event` | Look at the event in RevenueCat's dashboard |

Each event the Worker handles prints a `subscription_event` line with its type, how many accounts it
was about and what came of it. In RevenueCat's dashboard, a failed delivery can be sent again by
hand once the cause is fixed; events that happen while the webhook is disabled are never sent.
RevenueCat's test event answers 200 when the setup is right.

## The daily run failing

The cron runs every day at 16:30 UTC. It fetches the exchange rates, sends the payment reminders that
are due (40 at most per run), and deletes expired invites, deleted requests older than 30 days,
expired or revoked bill links with their picks, and the hourly counters whose hour is over. Each part
runs whatever the others do, and the run is marked failed when one of them failed. Its outcomes are
under Trigger Events > View events; the logs hold its `reminders` line and any error.

When it fails:

- Exchange rates: the first request that finds them a day old fetches them, so they stay fresh.
- Reminders: those due go out at the next run that works, 40 at a time.
- Clean-up: nothing is deleted that day. The privacy policy promises that expired and revoked bill
  links, and hourly counters, are deleted by the daily clean-up, so fix a failure within a day or
  two. An expired link still answers 410 meanwhile; nobody can read it.

A deployed cron cannot be started by hand. Fix the cause and let the next run catch up, or run the
clean-up's own statements once, after taking a Time Travel bookmark (`npx wrangler d1 time-travel
info split-vibe`). Times are milliseconds since the epoch: replace `<now>` with the current time,
for example `$(date +%s)000`.

```bash
npx wrangler d1 execute split-vibe --remote --command "DELETE FROM invites WHERE expires_at < <now> - 2592000000; DELETE FROM requests WHERE deleted_at IS NOT NULL AND deleted_at < <now> - 2592000000; DELETE FROM shares WHERE expires_at <= <now>; DELETE FROM shares WHERE revoked_at IS NOT NULL; DELETE FROM rate_windows WHERE started_at < <now> - 3600000;"
```

These are the statements of `forgetExpired` in `proxy/src/reminders.ts`; check them there before
running them, in case the code has changed. A bill link's picks go with it through the schema's
cascade.

## Backups and restoring D1

D1's Time Travel is always on: it keeps the database's history for 30 days on the Workers Paid plan
and 7 days on Workers Free, and can restore it to a point in that time. The release workflow
records a bookmark before it applies migrations ([release.md](release.md)). See where the database
stands now:

```bash
npx wrangler d1 time-travel info split-vibe
```

Restoring overwrites the database in place, cancels the queries running at that moment, and returns
a bookmark that undoes it:

```bash
npx wrangler d1 time-travel restore split-vibe --bookmark=<bookmark>
```

Before restoring production, know what a restore does not do:

- It brings back everything deleted after the restore point, including the shared data of every
  account deleted since then. The proxy keeps no record of which accounts were deleted, so they
  cannot be deleted again afterwards. Restore to the latest point that undoes the fault, and tell
  the legal adviser when a restore went back past account deletions.
- It restores D1 only. The calls, limits and plans in the Durable Objects stay as they are now.
- Anything written after the restore point is lost, and the apps' copies of groups and requests
  follow the server at their next refresh.

### The restore drill

The plan asks for one drill before release, and then one now and again: export production to SQL,
load it into a temporary database, compare, and delete everything. The export holds personal data:
do it on the owner's own computer, with its disk encrypted, and delete the file at the end.

1. Pick a quiet hour: an export blocks other requests to the database while it runs.
2. Export:

   ```bash
   npx wrangler d1 export split-vibe --remote --output=drill.sql
   ```

3. Make a temporary database in the same region:

   ```bash
   npx wrangler d1 create split-vibe-drill --location=enam
   ```

4. Load the export into it. If foreign keys stop it, the file needs `PRAGMA defer_foreign_keys =
   true;` at its top.

   ```bash
   npx wrangler d1 execute split-vibe-drill --remote --file=drill.sql
   ```

5. Count the rows of every table in both databases, and compare:

   ```bash
   npx wrangler d1 execute split-vibe --remote --json --command "SELECT 'groups', count(*) FROM groups UNION ALL SELECT 'members', count(*) FROM members UNION ALL SELECT 'claims', count(*) FROM claims UNION ALL SELECT 'invites', count(*) FROM invites UNION ALL SELECT 'group_bills', count(*) FROM group_bills UNION ALL SELECT 'settlements', count(*) FROM settlements UNION ALL SELECT 'group_log', count(*) FROM group_log UNION ALL SELECT 'requests', count(*) FROM requests UNION ALL SELECT 'devices', count(*) FROM devices UNION ALL SELECT 'rate_windows', count(*) FROM rate_windows UNION ALL SELECT 'shares', count(*) FROM shares UNION ALL SELECT 'share_claims', count(*) FROM share_claims"
   ```

   Run the same command against `split-vibe-drill`. Counts of `rate_windows`, and of anything written
   during the drill, may differ by what happened meanwhile; the others should match. Add any table a
   later migration creates.

6. Delete the temporary database and the export:

   ```bash
   npx wrangler d1 delete split-vibe-drill
   ```

   ```bash
   rm drill.sql
   ```

7. Record the date, the counts and the time each step took in `plans/reports/`, without any of the
   data.

## Rotating secrets

The Worker's secrets are set with `npx wrangler secret put <NAME>`, which asks for the value without
echoing it. Setting one deploys a new version of the Worker with it. Never paste a value into chat,
a file in the repository, a workflow or a log.

| Secret | Rotate | Effect on users |
|---|---|---|
| `ANTHROPIC_API_KEY` | Make a new key in the Anthropic console, set it, then revoke the old one | None; a read in flight at that moment may fail |
| `SESSION_SIGNING_KEY` (32 characters or more) | Set a new random value, such as from `openssl rand -base64 32` | Every session stops working: everyone signed in is asked to sign in again. Their data stays, as accounts do not depend on this key. |
| `HASH_SALT` | Do not rotate | Every account and the counts kept for it are named by it, and every payment request link and bill link is made with it: a new salt gives every Google account a new code, so nobody would find their groups, requests or links again. If it leaks, treat it as an incident and decide with the adviser. |
| `FCM_SERVICE_ACCOUNT` | Make a new JSON key for the service account in Google Cloud, set it with `npx wrangler secret put FCM_SERVICE_ACCOUNT < key.json`, delete the old key in Google Cloud, and delete the file | None |
| `TURNSTILE_SECRET` | Rotate the widget's secret key under Turnstile in the Cloudflare dashboard, then set it | A first pick on a bill link may be refused while the two differ |
| `REVENUECAT_SECRET_KEY` | Make a new secret key in RevenueCat's project settings, set it, then revoke the old one | None; with a wrong key, plan checks fail with 502 and the logs show `"status":401` |
| `REVENUECAT_WEBHOOK_AUTH` | Make a new value (`openssl rand -hex 32`), set it on the Worker and in RevenueCat's webhook settings straight after | Events in between get 401 and are sent again for about 80 minutes; send any left over again from RevenueCat's dashboard |

The release workflow's own credentials (the upload key, its access to Play through Workload Identity
Federation, and the Cloudflare API token) live in its protected `production` environment
([release.md](release.md)). If the upload key leaks, ask for an upload key reset in Play Console. The owner's own Cloudflare API token
for the counters is rolled in the Cloudflare dashboard under API Tokens.

`GOOGLE_CLIENT_ID`, `ANDROID_CERT_SHA256`, `TURNSTILE_SITE_KEY` and `ABUSE_EMAIL` are public
variables in `wrangler.jsonc`, not secrets.

## Reading the counters

The proxy writes one data point per event to Workers Analytics Engine (binding `METRICS`, dataset
`split_vibe_events`), each with only the event's name, its outcome and a number, such as a cost in
millionths of a dollar: bill reads and edits by a sentence, the first AI call of an install,
sign-ins, bill links made and picks, payment requests made, reported paid and confirmed, plan
checks and webhook events, and the daily runs. No data point holds an account, an install ID or an
IP. Cloudflare keeps them for three months.

`npm run metrics`, from `proxy/`, reads them through Cloudflare's SQL API with an API token of the
owner's that has the Account Analytics Read permission. At high volumes Analytics Engine samples the
data, so a count is the sum of `_sample_interval`, not the number of rows. Without the binding the
proxy runs as before and writes nothing.

During the rollout, check every day:

- the user-perceived crash rate and ANR rate in Android vitals (the release checklist gives the
  limits for each step);
- the AI's outcomes and its cost per day and per active user, against the Phase 3 budget;
- that the daily run worked;
- new installs calling the AI for the first time, a sign that the free calls are being farmed when
  it jumps;
- the funnel: bills read, links made, picks, requests made, reported paid and confirmed. Scanning
  happens only on the phone, so it is not counted.

Crashes come from Android vitals in Play Console; the app has no crash reporting SDK. Add one only
if vitals proves not to be enough, and update the privacy policy and the Data safety form first.

## Account deletion requests

People delete their account themselves, in the app (Settings > Delete account) or on the web at
`https://<the proxy's host>/delete-account` with Sign in with Google. The server keeps no email
address, so an email asking for deletion does not say which account it is about: answer with those
two ways, and mention that:

- a Pro subscription is not cancelled by the deletion and must be cancelled in Google Play;
- the bills on the phone stay unless they tick the box in the app's dialog, or uninstall the app;
- another phone still signed in to the same account should sign out, as its session keeps working
  for up to 30 days.

A deletion that fails with 502 `subscription_unavailable` failed at RevenueCat and deleted nothing:
it works once RevenueCat answers again. Each account may ask for its deletion 5 times an hour.

Someone who can no longer sign in to their Google account cannot be found on the server. Explain
that the server holds no name or email, that their data on others' phones and in groups is what the
other members keep, and refer the case to the adviser if they insist.

## Abuse reports

The web pages show a "Report abuse" link to the address in `ABUSE_EMAIL` in `wrangler.jsonc`; set it
before release, or the pages show none. A report should carry the link it is about. The part after
the `#` is the link's token, and the database keeps only its SHA-256 in hex:

```bash
printf '%s' '<token>' | sha256sum
```

Take a bookmark first (`npx wrangler d1 time-travel info split-vibe`), then, depending on the link:

- A bill link (`/s/#…`): look at it with
  `npx wrangler d1 execute split-vibe --remote --command "SELECT id, status, created_at, expires_at FROM shares WHERE token_hash = '<hash>'"`,
  and take it down by deleting it, as the daily run deletes expired links; its picks go with it:
  `npx wrangler d1 execute split-vibe --remote --command "DELETE FROM shares WHERE token_hash = '<hash>'"`.
  Open pages learn it at their next request.
- A payment request (`/r/#…`): empty it as its creditor's deletion would, which keeps nothing to pay:
  `npx wrangler d1 execute split-vibe --remote --command "UPDATE requests SET deleted_at = <now>, debtor = NULL, payer_name = '', creditor_name = '', title = NULL, method = '{}', payment = NULL, group_id = NULL, to_member_id = NULL, creditor_member_id = NULL, bill_id = NULL WHERE link_hash = '<hash>' AND confirmed_paid_at IS NULL AND deleted_at IS NULL"`.
  These are the columns the `DELETE /v1/requests/{id}` route empties in `proxy/src/requests.ts`;
  check them there first.
- A group invite (`/g/#…`): stop it with
  `npx wrangler d1 execute split-vibe --remote --command "UPDATE invites SET revoked_at = <now> WHERE token_hash = '<hash>'"`.

There is no way to block an account or an address: the limits per address and Turnstile slow abuse
down, and the links' owners can remove picks, lock or revoke their links. Keep a note of each report
and what was done, without the reported data. A request from an authority goes to the adviser.

## Incidents

A security incident, such as a leaked secret, data reaching someone it should not, or the server
being misused:

1. Contain it: rotate the secret ([above](#rotating-secrets)), switch AI off, or take the content
   down.
2. Write down what happened and when, from the logs, traces and dashboards, within the first hours.
3. Tell the adviser at once. The GDPR asks that a personal data breach be reported to the supervisory
   authority within 72 hours of becoming aware of it, unless it is unlikely to put people's rights
   and freedoms at risk, and
   Vietnam's law asks for a report to its personal data protection agency within 72 hours
   ([compliance.md](compliance.md#vietnam-cross-border-transfer-and-impact-assessments)).
4. Afterwards, add what was learned to this runbook.

## Support

Support channel: `<support channel>`. The Play listing needs a contact email
(`<contact email>`), which the privacy policy and the terms name too.

What usually comes in, and where the answer is:

- "Reading the bill failed": the messages in [When a provider fails](#when-a-provider-fails) and
  [Spend caps](#spend-caps); typing the bill in by hand always works.
- "My calls are used up": 3 per install without signing in, 10 a month signed in, 300 with Pro; the
  month is counted in UTC.
- "Pro does not show": the purchase is checked by the proxy within minutes; opening the Pro screen
  checks it again. A subscription bought on another Split Vibe account stays with that account.
- "Cancel or refund Pro": in Google Play; Google Play handles refunds.
- "Delete my account or my data": [Account deletion requests](#account-deletion-requests).
- "Someone sent me a request or a link I do not trust": [Abuse reports](#abuse-reports).
