# Fixes for the proxy and web review, and for four items of the security review

## Phase implementation report

### Executed phase
- Phase: quality, compliance and release, proxy and web part; the fixes the coordinator decided for `plans/reports/code-reviewer-261119-0255-phase-9-proxy-web-review.md` (M1, L1 to L7) and for H1, M1, M3 and L1 of `plans/reports/code-reviewer-261119-0309-security-review.md`.
- Plan: `plans/261114-0220-split-vibe-production/phase-09-quality-compliance-release.md`, not edited.
- Status: completed. Everything is committed and pushed to `origin/feat/phase-09-quality-compliance-release` (`ed1e9cc..2089d2e`, plus the commit of this report).

### Commits
| Commit | What |
|---|---|
| `255d9d2` fix(proxy) | An install's first-call mark holds `true`, not a time (L2) |
| `1d9fd43` fix(proxy) | The token's audience is the trimmed `GOOGLE_CLIENT_ID` (L3) |
| `957d84c` feat(proxy) | HEAD on the six web pages (L4) |
| `d73a4e4` fix(proxy) | A bill read or edit cut short by an error is counted as `internal` (L1) |
| `6ed898b` fix(proxy) | A deletion ends every session issued before it; kept state goes at its time, also after a missed alarm (M1, L6) |
| `818132b` fix(proxy) | Installs' $5 pool inside the global cap; `spend_cap_near` at 80% (security H1) |
| `3eee733` fix(proxy) | Removing a member, or leaving, revokes the group's live invites (security M1) |
| `1d56e48` fix(proxy) | assetlinks.json pairs each package with its own keys (security L1) |
| `c1b39e3` fix(web) | `/delete-account` says phones still signed in are signed out (M1) |
| `2c4c3ac` fix(web) | A legal source may hold one leading comment; any other stops the build (L5) |
| `ed246d2` feat(web) | Notice beside payment details on `/r/` and `/s/` (security M3) |
| `2089d2e` docs | `docs/privacy-policy.md` and `docs/web.md` (decisions 8 and 9, and the docs of the items above) |

Some files carried more than one fix. For those commits I staged intermediate versions as blobs with `git update-index`, so the shared working tree was never rewritten. I checked by diff that each intermediate version holds only its own fix. I could not type-check each intermediate tree on its own: a hook here blocks linking the dependencies into a scratch export. The final tree passes every check below.

## Each finding

### M1: sessions outlived a deletion
**Done.**
- **Deletion time.** `QuotaBook.forget(now)` (`proxy/src/quota-book.ts`) now also stores `deletedAt`, the time of the deletion, beside the month's count, under `google:<salted hash>` only. Both routes use it, through `deleteAccount`. A later deletion keeps the later time.
- **Refusal.** A session whose `iat` is before `deletedAt` is answered 401 `session` everywhere a session is taken:
  - AI routes: folded into `hold(…, issuedAt)`, which returns the new code `session`.
  - `/v1/usage`: folded into `issuePurchaser(now, allowance, issuedAt)`, which returns `null`.
  - `/v1/plan`: folded into the same `issuePurchaser` call. I moved that call ahead of the hourly count, so a refused session counts nothing and asks RevenueCat nothing.
  - Shared data, devices and `DELETE /v1/account`: one extra call, `QuotaBook.sessionEnded(now, issuedAt)`, made by `signedInAccountOf` in `proxy/src/identity.ts`. `signedIn` uses it, so groups, bills, invites, requests, shares and devices all do.
  - `POST /v1/session` checks nothing, since it has no session yet.
- **Millisecond `iat`.** Sessions now carry `iat` to the millisecond, in fractional seconds (`proxy/src/session.ts`). Without that, a sign-in in the same second as the deletion would be refused. Sessions issued earlier, in whole seconds, still count. A session without `iat` is refused; every session the proxy ever issued has one.
- **Sign-in after deletion.** It gets a new session that works, and the account starts afresh apart from the month's count.
- **How long the record lasts.** The alarm wakes for whichever is due first, then again for the other:
  - the month's count goes when the month ends, which is what the privacy policy says;
  - the deletion time goes after `POLICY.sessionMs`.
  After the later of the two, nothing is left and the object deletes itself. The decision asked for one cleanup "at the later of the month's end and that time". I kept the count only until the month ends, so that it is never kept longer than the policy says. The end state is the same.
- **Mixed versions during a deploy.**
  - New Worker, old QuotaBook: the old object ignores the extra arguments of `hold` and `issuePurchaser`. It lacks `sessionEnded`, and the runtime refuses that call with a `TypeError` starting "The RPC receiver does not implement". The Worker reads that as "no deletion recorded", which is true of old code; any other error answers 500. A deletion made in that window records no time, so it ends no session. The deploy note below covers this.
  - Old Worker, new QuotaBook: the old Worker sends no issue time, so nothing is checked. `forget()` without a time still records one.
- **Page.** See the web part below.
- Tests:
  - Both deletion routes, in `proxy/test/account.test.ts`:
    - "the time of the deletion: stays in its QuotaBook … for as long as a session lasts".
    - "sessions issued before it: are refused wherever a session is taken, and bring nothing of the account back". It covers 14 kinds of session route plus a bill read and an edit, for the deleting phone and a second phone. It checks that no `devices` row, issued mark, RevenueCat call, model call, member or hourly count comes back.
    - "a sign-in after it: gets a session that works everywhere, on the calls the month has left".
  - Session format: "carries the time it was issued to the millisecond, still counts as one in whole seconds …, and is refused without one".
  - Mixed versions: "while an account's QuotaBook still runs the code from before deletions ended sessions" (2 tests).
  - `proxy/test/quota-book.test.ts`, "a deleted account" (11 tests): the alarms in both orders; "is left with nothing once both are over"; the session checks at the object; calls that name no session, as an older Worker sends them; two deletions.
  - `plan.test.ts`, `subscription-webhook.test.ts` and the rate-limit test of `account.test.ts` now sign in again after a deletion, since the old session no longer works.

### L1: a 500 wrote no data point
**Done.** `callModel` (`proxy/src/model-call.ts`) wraps its steps. An error thrown before `ended()` now ends the call as `internal`, with its log line and data point, and the install's first call is counted with it. That covers both the Worker's 500 and a stream that ends with the error line `internal`.
- Tests in `proxy/test/metrics.test.ts`: "count a bill read or an edit that an error cuts short as internal: one the Worker answers 500" (a QuotaBook whose `hold` fails); and "… one whose stream then ends with the error line internal".

### L2: the first-call mark kept a date
**Done.** The mark now stores `true`. A mark written as a time by the earlier version still counts.
- Tests in `proxy/test/quota-book.test.ts`, "the first call of an install": the existing behaviour test, now expecting `true`, and a case for a mark that holds a time.

### L3: `GOOGLE_CLIENT_ID` was not trimmed for the token check
**Done.** One helper, `clientIdOf`, now gives the trimmed value to the sign-in switch and to the audience check.
- Test in `proxy/test/account.test.ts`: "takes the app's client ID set with a stray space or line break, as the page's tag does". It covers sign-in and the deletion with Google.

### L4: HEAD answered 405
**Done.** `/s/`, `/r/`, `/g/`, `/privacy`, `/terms` and `/delete-account` answer HEAD with GET's status and headers and no body (`page()` in `proxy/src/index.ts`).
- Test in `proxy/test/web.test.ts`: "answers HEAD as it answers GET, with the same status and headers but no body". The 405 tests for other methods still pass.

### L5: HTML comments in the legal renderer
**Done, by refusing**, the simpler of the two options; `docs/web.md` says so.
- A source may begin with one comment, alone on its lines; it is dropped whole.
- Any other `<!--` stops the build, naming the line: a later comment, one with text after `-->`, HTML between two comments, an inline or unclosed comment, one in code, or the forms browsers end early, `<!-->` and `--!>`.
- The real docs have only leading notes and render as before. The fixture's comment moved to the top.
- Tests in `web/tests/static-pages.test.ts`: "leave out the note a source begins with …" and "stop the build at any other HTML comment, saying on which line …" (11 sources, including both cases the review lists).

### L6: kept state could stay past its time
**Done.** Every QuotaBook method first runs `begin()`, except `forget` and `alarm`.
- It drops a monthly count whose month has ended, which counts for nothing then, and a deletion time older than 30 days.
- It sets the alarm again whenever it dropped something, or when a deletion time is kept but no alarm is set. That is the state older code leaves after a rollback.
- The drop of an ended month's count applies to any account's object, not only a deleted one. It is harmless, since a past month counts as zero.
- Test in `proxy/test/quota-book.test.ts`: "forgets what it kept past its time when it is reached before its alarm ran, … and sets the alarm again for the rest". It covers both over, only the month over, nothing over with no alarm, and a trial count left alone.

### L7: localhost origins on the production client
**Done in `docs/web.md`**: test on a computer with a separate OAuth client, best in a test project, with its ID in `proxy/.dev.vars`, and never add `localhost` to the production client. The matching change to `docs/proxy.md` is below.

### Decision 9: `docs/privacy-policy.md`
**Done.**
- What deletion keeps now lists the deletion time, kept under the account's code for 30 days so that phones still signed in are signed out. The retention list says the same.
- "Deleting your account on the website does not touch your phone" now reads "deletes nothing on your phone".
- The passage that said a signed-in phone keeps using the account now says that a deletion signs out every phone, and that signing in again starts afresh apart from the month's calls.
- The counts list gains "the spending caps that come near their limit".
- The file is still plain Markdown. `npm run build` renders it, and rendering the real source with the build's own function confirms the new passages are present and no comment is left.

### Security H1: anonymous installs could fill the shared daily spend cap
**Done.**
- **Pool.** A call from an install that is not signed in also holds against `POLICY.installCapMicro`, $5. The pool sits inside the global ledger, in the same 24-hour window as the $20, so signed-in and Pro callers always keep $15. Two separate ledgers would start their windows at different times and let installs take up to $10 of one global window. When the pool is full, installs get 429 `busy`.
- **Unchanged rule.** A call the caller hung up on is still given back, as instructed.
- **80% event.** The first hold of a window that takes a cap to `POLICY.spendWarnPercent` (80%) logs `{"event":"spend_cap_near","cap":…}` and writes one data point `spend_cap_near` with the cap (`caller`, `address`, `install` or `global`). No id is included. A hold the cap refuses also counts, since it would pass 100%. The mark is kept per ledger and window, so it fires once per cap and window. An old ledger ignores the pool argument, and an old Worker ignores the marks.
- Tests:
  - `proxy/test/ledger.test.ts`: "keeps the calls of a pool within it, in the cap's own window, so that the other calls always keep the rest of the cap" (installs cannot take signed-in callers below $15), and "tells of the first hold of a window to take the cap, and the pool, to the warning mark, once each" (once per window, again in the next).
  - `proxy/test/bill-analysis.test.ts`: "keeps signed-in accounts their share …": with the pool full, an install's read is refused 429 `busy` and an account's read runs.
  - `proxy/test/metrics.test.ts`: "log and count a spend cap that a call takes to 80% of it, once in the cap's window, naming the cap and nothing else".

### Security M1: a removed member came back through an old invite
**Done.** `removeMemberRoute` and `leaveGroupRoute` (`proxy/src/groups.ts`) revoke every invite of the group that still works, in their batch. The SQL statement is shared with the owner's revoke route (`revokeInvitesStatement` in `proxy/src/invites.ts`). A deleted group's invites go with it, as before.
- Test in `proxy/test/groups.test.ts`: "taken out by the owner, or leaving, cannot come back through an invite made before …". The removed member's own invite and the owner's older one both answer 410 `gone`, and a new invite works. Leaving behaves the same.

### Security M3: user-written payment details on the service's pages
**Done.** `PaymentBlock` (`web/src/ui/payment-block.tsx`) is shown on `/r/` and for every payable amount on `/s/`. It now starts with "The person who sent this link wrote these payment details. Split Vibe does not check them.", followed by a "Report abuse" link while `ABUSE_EMAIL` is set. `App` provides the link through a context, with the page kind's subject.
- Tests:
  - Unit: `web/tests/request-invite-pages.test.tsx` (the notice, its link, and that it comes first), `web/tests/share-page.test.tsx` (both payable amounts) and `web/tests/app.test.tsx` (through `App`, with the request page's mailto).
  - e2e: both specs now check the notice and its link, and select the footer's "Report abuse" link inside the footer, since a page with payment details now has two.

### Security L1: App Links paired the debug key with the release package
**Done.** `com.dantech.splitvibe` takes only the fingerprints of `ANDROID_CERT_SHA256` (the upload key and the Play app signing key). `com.dantech.splitvibe.dev` takes only those of the new optional `ANDROID_DEV_CERT_SHA256` (the debug key). A package whose variable is empty is left out, so either setting works alone, and with neither the file is `[]`. A bad value is logged with its variable's name.
- **How the owner sets them.** Put the upload key's and the Play app signing key's SHA-256 fingerprints, comma-separated, in `ANDROID_CERT_SHA256`. Put the debug key's in `ANDROID_DEV_CERT_SHA256` only on a Worker that debug builds use, and keep it empty on production. The `docs/proxy.md` text is below. `proxy/wrangler.jsonc` is not among my files, so the coordinator should add `"ANDROID_DEV_CERT_SHA256": ""` to its `vars`; without it the Worker runs as if the variable were empty.
- Tests in `proxy/test/links.test.ts`: both variables, each alone, and none.

### Web part of M1: `/delete-account`
**Done** (`web/src/pages/delete-account-page.tsx`).
- **Before deleting.** The "What is deleted" list gains "Your sign-in on every phone", and says that a phone still signed in can no longer use the account until you sign in again. The confirmation says that deleting "signs out every phone still signed in to it". "What stays" gains the deletion time, kept 30 days.
- **Result.** It says those phones are now signed out.
- **Wording.** I replaced the two overclaims, "no longer keeps your account" and "removes what Split Vibe's server keeps about you", with wording that points at "What stays". The page states what the server guarantees (it refuses those sessions), not when the app notices.
- Tests in `web/tests/delete-account-page.test.tsx`: updated text checks, including that "no longer keeps your account" is gone.

## Tests status
| Command | Result |
|---|---|
| `proxy`: `npm run typecheck` | pass |
| `proxy`: `npm test` | Vitest 24 files, 500 tests, pass; Node 27 tests, pass. These totals include `test/eval-read.test.ts` and the eval Node tests of the agent working on `proxy/eval/`. |
| `proxy`: `npm run bundle -- <scratch dir>` | pass; Total Upload 1471.01 KiB, gzip 294.31 KiB; `env.METRICS (split_vibe_events)` listed |
| `web`: `npm test` | 20 files, 235 tests, pass |
| `web`: `npm run build` | pass; "4 pages and 2 files under /assets/" |
| `web`: `npm run e2e` | 8 of 8 pass. Its `wrangler dev` stopped at the end; ports 8797 and 9339 are free. |

No linter is configured in `proxy/` or `web/`.

## Changes `docs/proxy.md` needs (for the coordinator)
I quote the current text (HEAD `2089d2e`) and give the replacement.

1. **Routes table, row `DELETE /v1/account`** (around line 43). In its last cell, replace "which stay until the month ends, [below](#deleting-an-account)" with:
   > which stay until the month ends; every session issued before then is refused from then on, [below](#deleting-an-account)

2. **The paragraph after the table**, after the sentence "A signed-in app adds `Authorization: Bearer <session>`, which is all the shared data needs.", add:
   > A session issued before its account was deleted is refused everywhere as 401 `session`, as one that has run out is.

3. **Calls and limits, the spend bullet** (around line 101). Replace "- Spend is capped over each 24 hours: $3 per caller, $6 per address and $20 for the whole proxy." with:
   > - Spend is capped over each 24 hours: $3 per caller, $6 per address and $20 for the whole proxy. Of the $20, installs that are not signed in may spend $5 together (`installCapMicro`), counted in the same window as the $20, so that signed-in accounts, Pro among them, always keep $15; once the installs' $5 is spent, their calls are refused with 429 `busy` until the window ends. The first hold of a window that takes a cap to 80% of it (`spendWarnPercent`), or that the cap refuses, logs `spend_cap_near` with which cap it is and counts it ([Counters](#counters)), once per cap and window.

   Keep the rest of the bullet.

4. **What it keeps, first paragraph** (around lines 330-342).
   - Replace "requests and spend per address." with:
     > requests and spend per address, and for the whole proxy what installs that are not signed in spent, apart.
   - Replace "but the calls of the current month, which stay until the month ends, as [Deleting an account](#deleting-an-account) says. An install's `QuotaBook` also keeps when it first had a call held" with:
     > but the calls of the current month, which stay until the month ends, and the time of the deletion, which stays for 30 days, as [Deleting an account](#deleting-an-account) says. An install's `QuotaBook` also keeps that it had a call held, never when,

5. **What it keeps, log paragraph** (around line 383). After "it prints `purchaser_not_recorded` and nothing more.", add:
   > When a call's hold takes a spend cap to 80% of it, it prints `spend_cap_near` with the cap, `caller`, `address`, `install` or `global`, once per cap and window, and nothing more. A fingerprint for App Links that is not one prints `bad_cert_fingerprint` with its variable's name and how many there were.

6. **Counters table** (around lines 409-418).
   - Row `bill_analysis`, `split_revision`, in the outcome cell, replace "such as `quota_exhausted`, `ai_off` or `upstream`" with:
     > such as `quota_exhausted`, `ai_off` or `upstream`; `session` for a session issued before its account was deleted; `internal` for a call an error cut short, which the Worker answered 500 or ended with the error line `internal`
   - Row `plan_refresh`, in the outcome cell, replace "`ok`, `not_configured`, `rate_limited` or `subscription_unavailable`" with:
     > `ok`, `not_configured`, `session`, `rate_limited` or `subscription_unavailable`
   - Add a row before `daily_run`:
     > | `spend_cap_near` | a call's hold takes a spend cap to 80% of it, or the cap refuses it, the first time in the cap's window | the cap: `caller`, `address`, `install` (the pool of installs that are not signed in) or `global` | none |

7. **Groups and members** (around lines 536-540). Replace "A removed account can join again only through an invite, which the owner can revoke." with:
   > Removing a member, and leaving, revoke every invite of the group that still works, in the same batch, so that no invite made before, one the member kept among them, brings anyone back: the members who stay make a new one, and a removed account can join again only through such a new invite, which the owner can revoke too.

8. **App Links** (around lines 719-723). Replace "It lists `com.dantech.splitvibe` and `com.dantech.splitvibe.dev`, each with every fingerprint in the variable `ANDROID_CERT_SHA256` (comma-separated SHA-256 fingerprints, `AB:CD:…`, upper-cased by the Worker; one that is not one is left out and logged). Without any, it is `[]`." with:
   > It lists each package with the fingerprints of its own variable alone, comma-separated SHA-256 fingerprints (`AB:CD:…`, upper-cased by the Worker; one that is not one is left out and logged): `com.dantech.splitvibe` with `ANDROID_CERT_SHA256`, the upload key's and the Play app signing key's, and `com.dantech.splitvibe.dev` with `ANDROID_DEV_CERT_SHA256`, the debug key's. A package whose variable lists none is left out, and without any the file is `[]`. So no key is trusted for a package it does not sign. Leave `ANDROID_DEV_CERT_SHA256` empty on production, so that no app signed with a debug key opens its links.

9. **Deleting an account.**
   - Replace the first bullet, "- The `QuotaBook` keeps the count of the calendar month … once the model answers.", with:
     > - The `QuotaBook` keeps two things and nothing else: the count of the calendar month the account was deleted in, and the time of the deletion. Its plan, the record of its purchaser and the request ids it remembers go, so a later webhook event about the account asks RevenueCat nothing. The count holds the month and the calls used, under the account's salted hash, so that deleting the account and signing in again starts no new allowance: a sign-in in the same month sees the calls used, on the free plan. A call still held when the account is deleted runs on, and is charged to that count once the model answers. The time of the deletion stays for 30 days (`sessionMs`), the longest a session lasts, so that every session issued before it is refused, as below. The object wakes when the month ends and deletes the count, and when the 30 days are over and deletes the time, so that nothing is left after the later of the two. Every call to the object first forgets what has outlived its time and sets the alarm again for what is left, so that nothing stays past its time when older code ran the alarm after a rollback.
   - In the hourly counters bullet, replace "So neither deleting the account again and again nor a session that outlives the deletion starts a limit over, and RevenueCat cannot be asked without end." with:
     > So deleting the account again and again, signing in again each time, starts no limit over, and RevenueCat cannot be asked without end.
   - Replace the paragraph "Sessions are not revoked: … could still be used." with:
     > Every session issued before the deletion is refused from then on, as 401 `session`, wherever a session is taken: the shared data, devices, the calls left, the plan, bill reads and edits, and `DELETE /v1/account`. So a phone still signed in to the account, as when it was deleted on the web, brings nothing back: no device, no record of its purchaser, no question to RevenueCat, no group, request or count. The app takes 401 `session` as a sign-in that ran out, forgets the session and asks to sign in again. A session carries its issue time, `iat`, to the millisecond, so a sign-in after the deletion, even in the same second, gets a session that works; sessions issued in whole seconds, before sessions had milliseconds, still count. The check rides on the call each route makes to the account's `QuotaBook` anyway: the hold of a bill read or an edit, and the purchaser of `/v1/usage` and `/v1/plan`. The routes on shared data, devices and `DELETE /v1/account` make one more call to it. One thing the proxy cannot stop: when the app starts, it starts RevenueCat's SDK as the purchaser it kept, before any call tells it that its session ended, and RevenueCat's read creates the customer again, empty, under the account's id. The proxy asks RevenueCat nothing about it, since the record of the purchaser is gone.
   - The paragraph "While an account is being deleted, …" stays as it is.

10. **The pages** (around lines 1032-1068).
    - In the first bullet, replace "`GET /s/`, `GET /r/` and `GET /g/` answer the web app's page," with:
      > `GET /s/`, `GET /r/` and `GET /g/`, and HEAD as below, answer the web app's page,
    - After that bullet, add:
      > - On a payment request and on each amount of a settled bill link, the page says above how to pay that the person who sent the link wrote it and that Split Vibe does not check it, with the "Report abuse" link while `ABUSE_EMAIL` is set ([web.md](web.md)).
    - Replace "- These three answer GET at their paths as written only: `/privacy/` or `/terms.html` is 404." with:
      > - These three, like the link pages, answer GET and HEAD at their paths as written only. HEAD gets the status and headers GET gets, without the body, for link checkers and uptime monitors. `/privacy/` or `/terms.html` is 404, and any other method 405.

11. **Run it on a computer** (around lines 1154-1157). Replace "The page `http://localhost:8787/delete-account` signs in with Google only once `http://localhost` and `http://localhost:8787` are both among the web client's Authorized JavaScript origins, as Google's guide asks for testing on a computer." with:
    > To try the page `http://localhost:8787/delete-account`, use a separate OAuth client of type *Web application*, best in a Google Cloud project kept for testing, with `http://localhost` and `http://localhost:8787` as its Authorized JavaScript origins, and its client ID in `GOOGLE_CLIENT_ID` in `proxy/.dev.vars` ([web.md](web.md#deleting-an-account-on-the-web)). Never add `localhost` origins to the production client: any page served on a person's own computer could then get ID tokens for it, and such a token signs in to that person's account or deletes it.

12. **Tests.** In the sentence on the deletion matrix, after "the calls of the month, which a new sign-in sees;", add:
    > the time of the deletion, kept for 30 days; the sessions issued before it, refused on every kind of route without bringing anything back, and a sign-in after it, which works;

13. **Deploy it, step 3, App Links** (around lines 1250-1253). Replace "For App Links, put the SHA-256 fingerprints of the keys that sign the app (the debug key, the upload key and the Play app signing key) into `ANDROID_CERT_SHA256` in `wrangler.jsonc`, separated by commas; they are public. `keytool -list -v` prints them, and Play Console shows the app signing key's." with:
    > For App Links, put the SHA-256 fingerprints of the keys that sign the release app, the upload key and the Play app signing key, into `ANDROID_CERT_SHA256` in `wrangler.jsonc`, separated by commas; they are public. `keytool -list -v` prints them, and Play Console shows the app signing key's. The debug key's fingerprint goes into `ANDROID_DEV_CERT_SHA256`, and only on a Worker that debug builds use. On production it stays empty, so that no app signed with a debug key opens the production links.

14. **Deploy it, step 7.** After the paragraph "**The first deploy of the kept count and the counters**: …", add:
    > **The first deploy that ends sessions at a deletion and keeps installs within their pool**: for up to about five minutes, a `QuotaBook` still on the code from before keeps no time of a deletion, so an account deleted in that window keeps its sessions until they expire; the Worker takes the runtime's refusal of the method that code lacks as "no deletion", so no request fails. An `AbuseLedger` still on the code from before holds no pool, so in that window installs may spend more than $5 of the proxy's cap. Deploy with `--durable-objects-code-update-mode immediate`, as above, to leave no such window.
    >
    > **Rolling back** past it: the older code neither keeps nor checks the time of a deletion, so sessions issued before a deletion work again until they expire, and it may run a deleted account's alarm without setting it again. Once this version is deployed again, each such `QuotaBook` forgets what has outlived its time at its first call and sets its alarm again; one that nothing calls keeps its count and its deletion time, which hold nothing but the account's salted hash, until something does.

### Outside `docs/proxy.md` (not my files)
- `proxy/wrangler.jsonc`: add `"ANDROID_DEV_CERT_SHA256": ""` to `vars`, next to `ANDROID_CERT_SHA256`.
- `docs/release-checklist.md:102` names `ANDROID_CERT_SHA256` with the Play app signing key. It could add that `ANDROID_DEV_CERT_SHA256` stays empty on production.
- `docs/runbook.md` could gain an entry for `spend_cap_near` and for installs refused `busy` once their pool is spent.

## Issues and deviations
- **The month's count goes at the month's end, the deletion time after 30 days.** The decision described one cleanup at the later of the two. The end state is the same, nothing after the later, and the count is never kept longer than the privacy policy says.
- **`POST /v1/plan` now checks the session before its hourly count.** As a result, a refused session counts nothing. The only other change is that a live session's 429 answer also records the purchaser mark, which sign-in has already recorded.
- **Every shared-data request now makes one QuotaBook call.** This is the extra call the decision allowed.
- **Old-version detection reads the runtime's error message.** The prefix is "The RPC receiver does not implement"; local workerd says `The RPC receiver does not implement "name".`. If production words it differently, shared-data routes on an old object would answer 500 during the deploy window rather than accept the session. That is safe for data, and is avoided by the immediate code-update mode.
- **Concern, app side, not fixable in my files.** At start-up, `BillingRepository.follow` starts RevenueCat as the stored purchaser before any proxy call reports the ended session. On a phone that was signed in at deletion time, RevenueCat may therefore create an empty customer again. The proxy ignores it, since the purchaser record is gone. Neither the page nor the policy claims otherwise, and `docs/proxy.md` change 9 says it.
- **Deleting an account does not revoke the invites of its groups.** Only removal and leaving do, as asked. A deleted account that signs in again can still join through an old invite.

## Next steps
- The coordinator applies the `docs/proxy.md` changes above and adds the `wrangler.jsonc` variable.
- Deploy notes: the first deploy of this version should use `--durable-objects-code-update-mode immediate`; `.github/workflows/release.yml` still deploys without it, as the earlier review noted.

## Unresolved questions
- Should deleting an account also revoke the live invites of every group it was in, as leaving does?
- Should the app hold back RevenueCat's SDK until the proxy has accepted the stored session, so that a deletion leaves no empty customer behind?
- `POST /v1/session` does not check a deletion that races it, so a session issued in the same moment as a deletion is handed out and refused on its first use. Is that acceptable, or should sign-in answer an error then?

Status: DONE_WITH_CONCERNS
Summary: All eight review items and the four security items are fixed, tested, committed and pushed; every proxy and web check, the bundle and the e2e suite pass. The `docs/proxy.md` text and a new `wrangler.jsonc` variable are listed above for the coordinator.
Concerns/Blockers: On a phone signed in at deletion time, the app can re-create an empty RevenueCat customer at start-up (app-side change needed). Intermediate commits were verified by diff, not type-checked one by one. During the first deploy, a deletion on an old QuotaBook ends no session unless the immediate code-update mode is used.
