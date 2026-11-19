# Release documents of Split Vibe 1.0: report

Date: 19 November 2026 (UTC). Branch: `feat/phase-09-quality-compliance-release`.
Plan: [phase 9](../261114-0220-split-vibe-production/phase-09-quality-compliance-release.md).

## Outcome

All seven documents are written, committed one by one and pushed. The privacy policy and the terms
render with the web build's renderer (`web/scripts/static-pages.mjs`, committed by the web agent in
`789dc68`): I ran it on both files, and the draft note was dropped, the cross-links became `/privacy`
and `/terms`, the placeholders showed, and no HTML was escaped. Checking the facts turned up eight
points the orchestrator or the owner must act on, listed under [Findings](#findings).

## Files and commits

| File | Lines | Commits |
|---|---|---|
| `docs/privacy-policy.md` | 238 | `55a3c58`, `b603b11`, `0eb0e42` |
| `docs/terms.md` | 139 | `55a3c58`, `b603b11` |
| `docs/compliance.md` | 264 | `13350f3`, `b603b11`, `0db6d0e` |
| `docs/play-console.md` | 240 | `6c0a0eb` |
| `docs/runbook.md` | 371 | `eeaabbe`, `1005820` |
| `docs/release-checklist.md` | 185 | `f736fa4` |
| `docs/store-listing.md` | 132 | `244b78c` |
| `README.md` | 496 | `1005820` |

No code, workflow, plan file or other agent's document was changed. Each commit staged only its own
paths.

## What each document covers

- **privacy-policy.md:** who is responsible; what stays on the phone, including that Android's cloud
  backup is off; what leaves it, feature by feature (bill reads, edits, the install ID, Google
  sign-in, groups, payment requests, bill links, push, Pro, exchange rates, ML Kit, Android
  vitals); people who use the web pages; each processor and what it receives (Anthropic,
  Cloudflare with Turnstile, Google, RevenueCat); where data is stored; transfers abroad; how long
  each kind is kept; the aggregate counters; account deletion in the app and on the web, with what
  it keeps; rights; legal bases; children; security; changes; contact. It uses plain Markdown only,
  with no tables, so that the page renderer shows it as written.
- **terms.md:** the service and its AI calls; that AI results may be wrong and amounts must be
  checked (which Anthropic's Commercial Terms, section D.3, require); that the app holds and moves
  no money and that payment links open other apps; accounts; Pro through Google Play, with renewal,
  cancelling and refunds; acceptable use, including Anthropic's usage policy; content; availability;
  ending use; liability; other services; governing law as a placeholder; changes.
- **compliance.md:** the draft note for both files; the placeholders; the assumptions the drafts
  make, which the owner or adviser must confirm; a table of the drafts' statements and their
  sources; a review checklist; GDPR Article 27, with UK and Switzerland marked unverified;
  Vietnam's Law 91/2025/QH15 read in the Official Gazette, with Decree 356 from three law firms;
  the data processing terms of Anthropic, Cloudflare, Google and RevenueCat; sources.
- **play-console.md:** the privacy policy URL; the Data safety answers for every data type
  (collected, ephemeral, required or optional, purposes) with notes on the calls a reviewer could
  make differently; the content rating; ads; the advertising ID; app access for reviewers; target
  audience; financial features; health apps; the subscription against Play's Subscriptions policy;
  account deletion; the Play pages checked, named.
- **runbook.md:** where to look; switching AI off; forcing an update; the spend caps; what people see
  when Anthropic, RevenueCat, Google Play, Google sign-in, FCM or Frankfurter fail; webhook failures;
  the daily run failing, with its clean-up statements; D1 Time Travel, what a restore cannot undo,
  and the restore drill; rotating each secret; reading the counters; deletion requests; abuse
  reports, with take-down statements; incidents; support.
- **release-checklist.md:** the blocking gates and the targets, each with how to check it; the checks
  on the final bundle (16 KB, target SDK 36, stable material3, no advertising ID, Billing Library);
  setup before the closed test; the owner's steps, from creating the app to 7 days at 100%, with the
  conditions for each rollout step; the drills. Links [release.md](../../docs/release.md).
- **store-listing.md:** the app name (10 characters), a short description (67 of 80), a full
  description (3,242 of 4,000, counting line breaks), counted from the written file; seven
  screenshots from `app/src/test/screenshots/`, with what Play's size rules mean for them; the
  missing icon and feature graphic; the Finance category; contacts.
- **README.md:** rewritten for the product. It keeps the setup sections other docs rely on (the
  `local.properties` keys, Firebase, the RevenueCat key and `pro-subscription.md`), "How it works",
  the tests, the known limits, the toolchain and the license. It adds the web pages, the legal pages,
  account deletion, a "Privacy in short" section and an index of `docs/`. It drops the proof of
  concept's verification table and the stale test counts: there are now some 647 `@Test` methods in
  the unit tests, not 290, and some 404 proxy tests, not 96. Of the open points, it keeps only those
  the plan still records on 19 November.

## Facts checked, with sources

From the repository, at `HEAD` (committed code, to avoid other agents' work in progress):

- **Limits, retention and switches:** `proxy/src/policy.ts` and `proxy/wrangler.jsonc` (logs off,
  traces on, the cron, the variables).
- **Proxy behaviour:**
  - `session.ts`: only `sub` is read from the Google ID token, salted and hashed.
  - `identity.ts`: install callers are named by their random ID.
  - `quota-book.ts`: one book per period, request IDs kept for a day.
  - `ledger.ts`: IPs and IPv6 /64s are hashed, and the object deletes itself.
  - `model-call.ts`: no user identifier is sent to Anthropic, and how errors are mapped.
  - `reminders.ts`: the clean-up statements.
  - `links.ts`: hex SHA-256 of tokens.
  - `requests.ts`: the columns a deletion empties.
  - `index.ts`: the cron fails when one part fails.
  - The migrations `0001` and `0002`.
- **App:**
  - `AndroidManifest.xml`: `allowBackup=false`, `AD_ID` removed, three declared permissions,
    Firebase auto-init off.
  - `GoogleAccounts.kt` and `AccountStore.kt`: the email is kept on the phone, encrypted.
  - `ProxyApi.kt`: which requests carry the install ID.
  - `Push.kt` and `DeviceRegistrar.kt`.
  - `VoiceInput.kt`: on-device first, and consent before the network service.
  - `RevenueCatBilling.kt`: `appUserID(purchaser)` only, no attributes.
  - The user-facing messages in `ProxyModelCall.kt`, `GatewayBillAnalyzer.kt`, `ProViewModel.kt`,
    `ProTexts.kt`, `ProScreen.kt` and `SettingsViewModel.kt`.
- **Web:** `web/src/claim-store.ts` (the claim key, ID and name in localStorage); no cookie anywhere
  in `proxy/src` or `web/src`.
- **Screenshots:** the sizes of every file in `app/src/test/screenshots/` (824 px wide; 1830, 2400,
  3200 or 4000 px high).

External sources, checked on 19 November 2026:

- **Play Console Help:**
  - Data safety (10787469), account deletion (13327111), User Data (10144311), Prepare your app for
    review (9859455) and sign-in details for review (15748846).
  - Content Ratings (9898843 and 188189), target audience (9867159), Financial features (13849271),
    Health apps (14738291) and Advertising ID (6048248).
  - Subscriptions (9900533), Create and set up your app (9859152), preview assets (9866151), Metadata
    (9898842) and categories (9859673).
  - Testing requirements for new personal accounts (14151465), staged rollouts (6346149) and target
    API level (11926878).
- **Google Play Help:** cancelling a subscription (7018481) and refunds (2479637).
- **developer.android.com:**
  - Android vitals: thresholds of 1.09% for crashes and 0.47% for ANRs, 8% per phone model, 28-day
    averages.
  - 16 KB page sizes: required from 1 February 2027, with `zipalign -c -P 16 -v 4` and `bundletool`.
  - Auto Backup: on Android 12 and later, `allowBackup=false` turns cloud backup off, but on some
    devices not device-to-device transfer.
  - The AndroidX material3 release notes.
- **Google Maven:** the `material3` metadata, and `1.5.0` returning 404.
- **Cloudflare:**
  - D1: Time Travel (30 days on Paid, 7 on Free, a restore can be undone), import and export (an
    export blocks the database), and the Wrangler D1 commands (`--location=enam`).
  - Workers: Logs (3 days on Free, 7 on Paid), traces (7 days, billed from 1 December 2026), Cron
    Triggers (last 100 events, no manual trigger for a deployed cron) and secrets (`secret put`
    deploys at once; dashboard edits need Deploy).
  - Analytics Engine: limits (data kept 3 months) and the SQL API (Account Analytics Read,
    `_sample_interval`).
  - The Customer DPA (v6.4, effective 3 April 2026), the Self-Serve Subscription Agreement (§6.1) and
    the Turnstile privacy addendum.
- **Anthropic:**
  - Commercial Terms (effective 17 June 2025): the DPA is incorporated, there is no training on
    customer content, and D.3 requires the notice to users.
  - Privacy center (updated 1 July 2026): inputs are deleted within 30 days, and kept up to 2 years
    if flagged.
  - The usage policy and the privacy policy URLs.
- **Google services:**
  - Firebase's Play data disclosure (FCM, Installations) and its Data Processing and Security Terms.
  - ML Kit's Android data disclosure, and the document scanner, which runs on the device.
- **RevenueCat:** its Google Play Data Safety guidance, and its DPA (effective August 2026).
- **GDPR:** Articles 13 and 27 on gdpr-info.eu.
- **Vietnam:**
  - Law 91/2025/QH15, read in the Official Gazette PDF (Công báo No. 971 + 972, 24 July 2025):
    Articles 1, 2, 8, 9, 11, 19 to 23, 36 and 38.
  - Decree 356/2025/ND-CP, from law firms only: Frasers (January 2026), Tilleke & Gibbins
    (5 January 2026) and EY Vietnam (3 March 2026).

## Unverified

- **Play Console forms:** the exact wording of the questions (account creation, the IARC category
  and questions, the Advertising ID form) and the menu path of the store listing. Play Console was
  not opened.
- **Staged rollouts:** whether Play treats the first production release after closed and open
  testing as an update that can be staged.
- **material3:** whether 1.5.0 is truly out. The release notes say it is; Google Maven, as reached
  from here, does not serve it.
- **Anthropic:** its DPA itself (transfer mechanism, sub-processors), and where it processes data.
- **Google:** its role (processor or controller) for Play, Sign in with Google and ML Kit, whose
  terms were not read, and where the OAuth consent screen's privacy URL is set.
- **Cloudflare:** where the Analytics Engine data and the logs are stored, and the name of the
  Turnstile secret rotation in the dashboard.
- **UK and Switzerland:** the UK representative rule is known from law firms' summaries only, the
  ICO's page was not read, and Switzerland was not checked.
- **Vietnam:**
  - Decree 356's official text, its forms and procedure, and whether A05 must approve a dossier
    before transfers start.
  - Whether its exemptions cover the cross-border transfer assessment (the sources disagree, and
    Article 38 of the law names only Articles 21, 22 and 33(2)).
  - How an individual developer is classified, and the sanctions decree that EY mentions.
- **`npm run metrics`:** its options and output. The web agent's script was not committed when the
  runbook was written, so the runbook describes only what the shared contract says.
- **`docs/release.md`:** it was untracked and owned by the CI agent, so I linked it without reading
  it. Statements about `release.yml` (version code from the tag, the 16 KB check, the bookmark
  before migrations, the protected environment) come from the plan's shared contract.
- **App messages:** the runbook quotes the app's messages as they were at `HEAD` when it was written.
  The app agent is moving strings to resources, word for word, and changed Settings in this phase.

## Placeholders the owner must fill

In the legal drafts, written in square brackets because GitHub hides `<…>` in Markdown prose:

- `[effective date]`, `[owner name]`, `[postal address]`, `[contact email]` and
  `[support channel]`, in both drafts;
- `[EU representative name and address]` and `[UK representative name and address]`, in the privacy
  policy; delete each line if none is appointed;
- `[Confirm the transfer safeguard in Anthropic's data processing addendum.]`, in the privacy
  policy;
- `[governing law]`, `[courts]` and `[Decide whether we refund the unused part of a paid period in
  that case, and how.]`, in the terms.

In the other documents, in code spans:

- the owner's facts: `<the proxy's host>`, `<contact email>` and `<support channel>`;
- `<review Google account>` and `<its password>`, to be entered in Play Console only;
- values filled at the moment a command is run: `<token>`, `<hash>`, `<now>` and `<bookmark>`.

Decisions the drafts leave to the owner or adviser, listed in compliance.md:

- the age of 18, the same in the policy, the terms and Play;
- the legal bases, and whether Vietnam needs a consent screen;
- the retention without end of installs' trial counts and of confirmed requests;
- how to answer access and portability requests;
- the liability cap and governing law;
- the refund on termination;
- Restrict Minor Access;
- the Financial features answer;
- the content rating's alcohol question;
- how to stage 1.0;
- the per-user AI budget and the bill-read evaluation threshold.

## Findings

1. **Play cannot stage a first publication.** Its help says staged rollouts "can only be used for app
   updates, not when publishing an app for the first time". The plan's 5, 20, 50 and 100% may not
   be possible for 1.0. The checklist gives the owner two options: fewer countries first, or
   staging from the first update.
2. **material3 1.5.0 may not be downloadable yet.** The AndroidX release notes list it on
   18 November 2026, but Google Maven still served `1.5.0-rc01` as the latest on 19 November. The
   bump is a code change, with new checksums, for the app's owner of `gradle/libs.versions.toml`.
3. **Two gates cannot pass as written.** Phase 3's bill-read evaluation of at least 30 sample bills
   is not in the repository (only `proxy/eval/revise/` exists), and no numeric threshold was ever
   set. Phase 3 also sets spend caps, but no budget of AI cost per active user, which the rollout
   conditions use.
4. **A D1 restore would bring back deleted accounts' shared data.** The proxy keeps no record of
   deleted accounts, so they could not be deleted again. The runbook warns about this; the design
   question is for the owner and adviser.
5. **Some data is kept without an end, and access requests have no tool.** Installs' trial counts and
   confirmed payment requests, including the payment method a confirmed request carried after its
   creditor deleted their account, are kept for as long as the service runs. The policy says so
   plainly. There is also no way to answer an access or portability request about server data.
6. **Data safety must declare more than the plan's list.** There is still no analytics SDK, but:
   - ML Kit sends Google diagnostics with installation and device identifiers;
   - Cloudflare's traces keep each request's country and city for 7 days, so approximate location
     is declared too;
   - a contact's name leaves the phone when that person is on a shared split.

   All three are declared in play-console.md, with the alternatives.
7. **Vietnam is likely not exempt.** The law treats storing and processing on foreign platforms as a
   cross-border transfer; the dossier is due 60 days after the first transfer, which may fall in the
   closed test. EY reports that bank account data is sensitive under Decree 356, which would rule
   out the size exemptions. The law asks for consent before collection unless an exception applies,
   and the app has no consent screen.
8. **The store assets need work.**
   - The screenshots fit Play's 2.3:1 limit, but are 824 px wide, under the 1,080 px Play asks for
     recommendations.
   - The bill link screenshots show a test address, a closing date of 2100 and an account number,
     so they are left out.
   - The sample bill and its example mention beer, which the content rating may ask about.
   - The icon and the feature graphic do not exist yet.

The README keeps its existing copyright line, "Copyright © 2026 dantech0xff". It is the repository
owner's GitHub handle and the only copyright notice in the repository, since `LICENSE` names no
holder. Dropping it would change a legal notice the owner wrote, so I kept it; say if it should go.

On the draft notes: the instruction allowed a note at the top of each legal file only if it stays
off the page, and forbade HTML. The web build drops HTML comments, and GitHub does not show them,
so each file starts with a one-line comment. It renders nothing, and compliance.md carries the
same note.

## Unresolved questions

1. Who builds and runs the bill-read evaluation of Phase 3, and against what threshold?
2. Which per-user AI cost budget gates the rollout steps?
3. How should 1.0 be staged if Play offers no rollout percentage for it?
4. Should confirmed payment requests and installs' trial counts get a retention limit? That would be
   a change to the proxy.
5. Should the README keep the copyright handle?

Status: DONE_WITH_CONCERNS
Summary: All seven release documents are written, committed and pushed, and the two legal drafts
render correctly with the web build's renderer. Checking the facts turned up eight findings that
need the owner's or the orchestrator's decision: chiefly Play cannot stage a first release,
material3 1.5.0 is not yet on Google Maven, and two release gates have no threshold or set.
Concerns/Blockers: none blocking this work; the findings above are for the owner and adviser.
