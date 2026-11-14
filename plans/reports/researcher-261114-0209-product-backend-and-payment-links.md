# Research: competitors, payment links, FX rates, Firebase, privacy, monetisation

Date: 2026-11-14. Author: researcher. Not legal advice.
Confidence labels: **[O]** official source (vendor docs, store listing by the developer, regulator), **[S]** secondary source (law firm, open-source library, press, aggregator), **[U]** unverified (see the Unverified section).

## Executive summary

- The market splits into two groups. Ledger apps (Splitwise, Tricount, Settle Up, Splid, Spliit) track balances over time. Single-bill scanners (Tab and a wave of 2026 "AI receipt splitter" apps) split one restaurant bill. Few apps combine on-device OCR, a natural-language instruction ("Anna didn't drink, split wine among the rest") and a persistent group ledger, and none I found does this on Android.
- Splitwise is the incumbent. It gates receipt scanning, itemisation and currency conversion behind Pro and caps free expense entry per day. Most user complaints are about that cap and the ads.
- No payment rail lets a third party prefill a payment everywhere. PayPal.Me (amount and currency), UPI, PromptPay, VietQR and EPC "BCD" codes are documented formats that the app can generate. Venmo and Cash App rely on community-documented URL formats. Revolut and Wise links are user-generated and cannot be prefilled reliably. Zelle has no public link format.
- Backend: use a Cloud Functions 2nd gen **callable** function with `enforceAppCheck`, a secret bound through `defineSecret`, streaming via `sendChunk`, and a server-side per-user quota document. The Blaze plan is mandatory.
- Two compliance items affect the build directly. Google Play's new Contact Picker policy (mandatory 27 Jan 2027 for apps targeting API 37+) means a split-bill app should **not** request READ_CONTACTS. Vietnam's PDP Law 91/2025 (in force 1 Jan 2026) treats storage on foreign cloud as a cross-border transfer that requires an impact-assessment filing.

---

## 1. Competitor landscape (late 2026)

### 1.1 Per-app facts

**Splitwise** (iOS, Android, web)
- Pro features listed officially: unlimited daily expenses, receipt scanning with itemisation, currency conversion, charts, search, default splits, card transaction import (US only) and no ads. [O] https://www.splitwise.com/pro
- The Pro page shows no price. US App Store IAPs range from $2.99 to $39.99, and "pricing varies by plan and country". The rating is 4.0 from about 30K ratings. [O] https://apps.apple.com/us/app/splitwise/id458023433
- Free daily cap: a developer reply on the App Store mentions 4 expenses per day. Third-party sources say 3 to 5 per day with a 10 s cooldown and date the cap to late 2023. [S]/[U] https://as001.pages.dev/learn/splitwise-free-limits , https://www.itvoice.in/splitwise-has-introduced-restrictions-on-the-number-of-free-expenses-users-can-add
- Commonly quoted price is about $4.99 per month or $39.99 per year (US). [S] https://getfinny.app/blog/splitwise-pricing-2026

**Tricount** (by bunq; iOS, Android)
- Free with "No ads. No limits". Features: balances, payment requests, currency conversion, offline use, Splitwise import, and an optional bunq card that adds expenses automatically. [O] https://www.tricount.com/
- Receipt photos can be attached, but the site mentions no OCR. Several sources report the old Premium tier was removed. [S] https://app-partly.web.app/en/blog/splitwise-vs-tricount-vs-partly-2026/ (competitor page)

**Settle Up** (Step Up Labs; iOS, Android, web)
- US IAPs: Premium $3.99 per month or $19.99 per year. Group Premium options: $5.99 for 1 week, $9.99 for 2 weeks, $14.99 for 1 month, $39.99 for 1 year, $149.99 lifetime. [O] https://apps.apple.com/us/app/settle-up-group-expenses/id737534985
- Premium removes ads and adds receipt photos, recurring expenses, custom categories, Excel export and statistics. The app contains ads. The listing mentions no OCR and no AI. The rating is 4.8 from 1.8K ratings. [O] same listing
- Platforms are listed on the website. [O] https://settleup.io/

**Splid** (iOS, Android)
- No sign-up, works offline with optional sync, 150+ currencies, settlement minimisation and PDF export. [O] https://apps.apple.com/us/app/splid-split-group-bills/id991473495
- IAPs: "Splid Plus" $3.99 and "2 Groups" $2.99 (one-time, not a subscription). The free tier is reported to allow a limited number of groups and has no ads. The rating is 4.9 from 4.2K ratings. [O] listing. The group-limit detail is [U].

**Tab - The simple bill splitter** (bring10 LLC; iOS only)
- Free with no IAP listed. You photograph an itemised receipt and each friend taps their items, synced in real time. Tax and tip are split proportionally. The rating is 4.2 from 1.2K ratings. No Android version was found. [O] https://apps.apple.com/us/app/id595068606
- A reviewer reports that subtotal and total lines get picked up as items, which is an OCR accuracy weakness. [O] listing review

**Spliit** (open source; web, plus an iPhone app)
- MIT licence, about 3.1K GitHub stars. Free with no ads and no account. Funded by donations through Open Collective. [O] https://spliit.app/ , https://github.com/spliit-app/spliit
- "AI Scan (Beta)" receipt extraction uses an OpenAI-compatible vision model (default `gpt-5-nano`) with structured JSON output. [O] GitHub README
- No Android app is mentioned. [O] spliit.app

**2025-2026 AI receipt-splitting wave** (mostly iOS indie apps)
- Examples: Groobil (launched 31 Jan 2026), Qota (launched 19 Feb 2026), Splitsy, SaySplit (group chat), SplitNSettle (voice input plus receipt scan "powered by OpenAI"), Tabify (itemises receipts and integrates with Splitwise), Kira (deletes receipt images within 30 days), SplitSnap (processes images and discards them), and Splitceipt (Android). [S] https://www.producthunt.com/products/groobil/launches , https://apps.apple.com/us/app/splitnsettle/id6757589699 , https://apps.apple.com/us/app/tabify-ai-receipt-splitter/id6740590324 , https://play.google.com/store/apps/details?id=com.splitceipt
- No major company has launched an LLM bill-splitting product. The segment is crowded with single-bill tools, and most have no persistent group ledger. [S]

### 1.2 Feature matrix

| | Splitwise | Tricount | Settle Up | Splid | Tab | Spliit | 2026 AI indie apps |
|---|---|---|---|---|---|---|---|
| Platforms | iOS/And/web | iOS/And | iOS/And/web | iOS/And | iOS | web/iOS | mostly iOS |
| Persistent groups/balances | Yes | Yes | Yes | Yes | No (per bill) | Yes | Mostly no |
| Receipt OCR + itemise | Pro only | No (photo only) | No (photo is Premium) | No | Yes, free | Beta (cloud LLM) | Yes |
| Natural-language instruction | No | No | No | No | No | No | A few (voice/chat) |
| Multi-currency conversion | Pro only | Yes | Yes | 150+ | n/a | Default currency only [U] | Varies |
| Offline | Partial [U] | Yes | Yes [U] | Yes | No [U] | No | Varies |
| Account required | Yes | Yes [U] | Yes [U] | No | No [U] | No | Varies |
| Ads | Free tier | No | Free tier | No | No | No | Varies |
| Paid model | Subscription | Free (bank-funded) | Sub + group passes + lifetime | One-time IAP | Free | Donations | Sub/IAP |
| Daily entry cap | Yes (3-5) | No | No [U] | No | No | No | Scan caps common [U] |

### 1.3 Recurring complaint themes
- Splitwise's daily cap, entry cooldown, unskippable full-screen ads and features moving from free to paid. These dominate negative reviews. [S] https://kimola.com/reports/splitwise-app-feedback-report-uncover-user-insights-google-play-en-144452 , https://au.trustpilot.com/review/splitwise.com
- Sync glitches after updates and difficulty deleting an account (Splitwise). [S] same sources
- OCR picks up subtotal, total or tax lines as items, and tips get mis-assigned (Tab reviews). [O] Tab listing
- Tricount users lost CSV/PDF export. [S] https://app-partly.web.app/en/blog/splitwise-vs-tricount-vs-partly-2026/

### 1.4 Gaps a new app could fill
1. An Android-first itemised receipt split with a free tier that does not cap ledger entries. Charge for the cost driver (LLM calls), not for data entry.
2. A natural-language split instruction with a reviewable, editable result. No ledger app offers this.
3. The single-bill scan flow and the persistent group ledger in one app. Today users combine Tab or Tabify with Splitwise.
4. Settle-up handoff to local rails: UPI, PromptPay, VietQR, EPC QR and PayPal.Me. Incumbents are US/EU-centric.
5. Privacy-forward OCR: text is extracted on the device, images are never uploaded by default, and retention is stated clearly. This addresses the trust concern that Kira and SplitSnap market against.
6. No account needed to start (anonymous sign-in, upgraded later), the way Splid and Spliit work.

---

## 2. Requesting payment without holding money

| Rail | Official doc | Format | Amount / currency / note prefill | Third-party restrictions | Conf. |
|---|---|---|---|---|---|
| PayPal.Me | https://www.paypal.com/us/cshelp/article/what-is-paypalme-help432 | `https://paypal.me/{handle}/{amount}{ISO}` e.g. `paypal.me/DiaRusso/25AUD` | Amount yes; currency yes (optional suffix); note: not documented | Handle up to 20 alphanumeric characters, one per account; some regions restrict currencies; business accounts receive only Goods & Services payments | [O] |
| Venmo (US only) | https://venmo.com/paymentlinks (page fetched; its current content shows no link parameters) | `https://venmo.com/{username}?txn=pay\|charge&amount=10&note=for+tix`; app scheme `venmo://paycharge?txn=pay&recipients=..&amount=..&note=..` | Amount yes, note yes, USD only | No published third-party terms found; the format is community-documented | [S]/[U] |
| Cash App (US/UK) | Help pages describe `$Cashtag` only: https://cash.app/help/1105 | `https://cash.app/${cashtag}`; `.../{amount}` suffix is common but undocumented | Amount [U]; note no | Not documented | [S] base / [U] amount |
| Revolut | https://help.revolut.com/help/adding-money/with-money-from-friends-or-relatives/requesting-money/ ; terms https://revolut.com/legal/payment-terms-paylinks | `https://revolut.me/{revtag}`, or an in-app "Request money" link with a fixed amount (to change the amount, cancel and recreate the link) | Fixed amount only inside the Revolut app; URL parameters are not documented | Non-Revolut payers pay by card, capped (one region quotes £250 per week); requests expire after 10 days; not available in the US for non-Revolut payers | [S] (help pages via search; direct fetch returned 403) |
| Wise | https://wise.com/help/articles/6DtiR7Ugdp7hfoKJHfRfvJ/how-to-use-wisetag ; https://wise.com/help/articles/2WvlZST6DiDMUBhyl1N4zM/how-to-request-money | Personal: Wisetag link or QR (URL structure not documented). Payment request links are created in the app. Business Quick Pay: `wise.com/pay/business/{name}?currency=GBP&amount=100` | Personal: amount is set in the app, not by URL. Business Quick Pay: amount and currency in the URL | Wisetag is for receiving from other Wise customers; an active balance in the requested currency is required | [O] for behaviour; [U] for the `wise.com/pay/me/{tag}` format |
| UPI (India) | NPCI UPI Linking Specs v1.6 (mirror): https://www.labnol.org/files/linking.pdf ; Google Pay India: https://developers.google.com/pay/india/api/android/in-app-payments | `upi://pay?pa={vpa}&pn={name}&am={amount}&cu=INR&tn={note}`; the same string inside a QR | `pa` and `pn` mandatory; `am` optional (editable when absent); `cu` INR; `tn` note optional; `tr` mandatory only for merchant/dynamic | **P2P collect requests banned from 1 Oct 2025**, so generate a payer-initiated intent or QR only. Google Pay docs require merchant fields for in-app *merchant* payments | [O] spec (2017 version) / [S] ban |
| PromptPay (Thailand) | No public BoT spec found; EMVCo MPM v1.1 plus Thai QR supplement | EMVCo TLV: `00 02 01`, `01 02 11\|12`, tag `29` {`00` AID `A000000677010111`, `01` mobile `0066`+9 digits (13 chars), or `02` national ID (13), or `03` e-wallet (15)}, `58 TH`, `53 764`, `54 amount`, `63` CRC-16/CCITT-FALSE | Amount yes (tag 54; tag 01 becomes 12 = dynamic); currency fixed THB; note no standard field for P2P | None published for static personal QR | [S] https://thai-qr-payment.js.org/reference/spec/ , https://github.com/dtinth/promptpay-qr |
| VietQR (Vietnam, NAPAS) | NAPAS QR Switching spec v1.5.2 (not public; vietqr.net returned 503) | EMVCo TLV: tag `38` {`00` GUID `A000000727`, `01` {`00` bank BIN, `01` account}, `02` service `QRIBFTTA` (to account) or `QRIBFTTC` (to card)}, `53 704`, `54 amount`, `58 VN`, `62`/`08` purpose (note), `63` CRC | Amount yes; currency VND; note yes (VietQR.io limits it to 50 characters without special characters) | Spec terms unknown. VietQR.io (operated by Casso, not NAPAS) offers an image URL `img.vietqr.io/image/{BIN}-{acct}-{template}.png?amount=&addInfo=&accountName=`, rate-limited to 10 QR per 10 s per IP | [S] https://www.vietqr.io/danh-sach-api/link-tao-ma-nhanh/ , https://beta.pkg.go.dev/giautm.dev/emvqr |
| SEPA EPC QR ("BCD", Girocode) | EPC069-12 v3.0: https://europeanpaymentscouncil.eu/sites/default/files/kb/file/2022-09/EPC069-12%20v3.0%20Quick%20Response%20Code%20-%20Guidelines%20to%20Enable%20the%20Data%20Capture%20for%20the%20Initiation%20of%20an%20SCT_0.pdf | Newline-separated lines: `BCD` / `002` / `1` (UTF-8) / `SCT` / BIC (optional in v002) / name / IBAN / `EUR12.50` / purpose (4 chars) / structured reference **or** / free text / info | Amount yes; currency **EUR only**; note yes (remittance text) | Free to use; bank app support varies by country. Febelfin cites v3.1 effective 1 Feb 2026 | [O] v3.0; [S] v3.1 https://en.wikipedia.org/wiki/EPC_QR_code |
| Zelle (US) | https://www.zelle.com/faq/how-to-use | **No public link or QR format.** QR codes are generated by the user's bank app ("My Code") and tied to the enrolled account | n/a | Cannot be generated by third parties; show the payee's email or phone number with a copy button | [O]/[S] |

Design note: all of these are payer-initiated handoffs. The app opens a URL or intent, or shows a QR code, and never sees the money. For rails without a link format, fall back to copying an IBAN, account number or phone number.

---

## 3. Exchange rates

| Source | Coverage | Frequency | Licence / price | Conf. |
|---|---|---|---|---|
| ECB euro reference rates | About 30 currencies, EUR base (no VND; RUB suspended since 2022) | Working days around 16:00 CET | Free; cite the ECB as source; "for information purposes only", not intended for transactions | [O] https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html , https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html |
| Frankfurter (open source) | 219 currencies blended from 111 central banks and official sources; history back to 1948 | Several updates per day as sources publish | Free, no key, no quota (abuse rate limiting); commercial use of blended rates allowed; self-host with Docker | [O] https://frankfurter.dev/ (project's own site) |
| Open Exchange Rates | 200+ currencies | Hourly (Free/Developer), 30 min, 5 min | Free: 1,000 requests/month, USD base only. Developer $12/mo (10K requests), Enterprise $47, Unlimited $97. Free-plan commercial terms not shown on the page | [O] https://openexchangerates.org/signup |
| ExchangeRate-API | 161-165 currencies (needs at least 3 sources per currency) | Free: daily; Pro: hourly | Free 1.5K requests/month; Pro $10/mo; Business $30/mo | [O] https://www.exchangerate-api.com/ |

**Recommendation (ranked):**
1. A **scheduled function once per day** fetches all rates from Frankfurter in one call and writes `fxRates/{yyyy-mm-dd}` (USD or EUR base plus a source field) to Firestore. Clients read that single document (one read per day per client, cached offline). Show "rate as of {date}, source: ..." for attribution.
2. Fallback: Open Exchange Rates Developer ($12/mo) if Frankfurter is down or misses a currency. Avoid the OXR free plan for a commercial app until its terms are confirmed.
3. Product rules: **snapshot the rate on each expense** so historical balances never drift, let users override the rate (for example, to match the rate their card actually charged), and label rates as reference mid-market rates, not transaction quotes.

---

## 4. Firebase production architecture

### 4.1 LLM endpoint: Cloud Functions 2nd gen vs Cloud Run
- 2nd gen functions *are* Cloud Run services (built on Cloud Run and Eventarc). HTTP and callable functions can run up to 60 minutes, handle up to 1000 concurrent requests per instance (default 80; concurrency needs at least 1 full CPU), and use up to 16 GiB and 4 vCPU. [O] https://firebase.google.com/docs/functions/version-comparison , https://firebase.google.com/docs/functions/manage-functions
- `minInstances` is billed at idle rates, about $8 per month for 256 MiB with 1 CPU and concurrency. Requests over `maxInstances` wait up to 30 s, then get a 429. [O] manage-functions
- **Callable streaming**: the server calls `response.sendChunk()` when `request.acceptsStreaming` is true, and the Kotlin client iterates `.stream()`. The client SDK's default timeout is 70 s; raise it on both sides for long LLM calls. [O] https://firebase.google.com/docs/functions/callable
- Plain Cloud Run has a default timeout of 300 s (max 3600 s) and concurrency up to 1000. You would have to write App Check and Auth token verification yourself. [O] https://docs.cloud.google.com/run/docs/configuring/request-timeout , https://docs.cloud.google.com/run/docs/about-concurrency
- **Secrets**: use `defineSecret('LLM_API_KEY')`, bind it through the `secrets` option and read `.value()` at runtime, never at global scope. Free allowance: 6 active versions and 10K accesses per month. `functions.config()` is deprecated, and new deploys that use it fail after March 2027. [O] https://firebase.google.com/docs/functions/config-env
- **Choice: a callable function (2nd gen), not raw Cloud Run.** It verifies Auth and App Check automatically, supports streaming, and the operational surface is smaller for a single developer. Move to Cloud Run only if a non-Node runtime or custom container becomes necessary.

### 4.2 App Check (Play Integrity)
- Can be enforced on Firestore, Realtime Database, Cloud Storage, **callable** functions only, Auth (preview), AI Logic and custom backends. It reduces abuse but does not eliminate it, and it complements Auth rather than replacing it. [O] https://firebase.google.com/docs/app-check
- Play Integrity Standard tier allows 10,000 calls per day. Token TTL defaults to 1 h (configurable from 30 min to 7 days). You must link the Play Console Cloud project and register the SHA-256 fingerprint, and use the debug provider for emulators and CI. [O] https://firebase.google.com/docs/app-check , https://firebase.google.com/docs/app-check/android/play-integrity-provider
- `enforceAppCheck: true` on `onCall`. Optional replay protection (beta): `consumeAppCheckToken: true` on the server plus `limitedUseAppCheckTokens = true` in Kotlin. It adds one extra round trip, so use it only for the LLM endpoint. [O] https://firebase.google.com/docs/app-check/cloud-functions

### 4.3 Per-user rate limiting and quota
- There is no built-in per-user rate limiter. Security rules can only throttle writes, using `request.time` against a timestamp stored per user and checked with `getAfter`. Rules `get()` calls are billed even when the request is denied. [S] https://fireship.io/lessons/how-to-rate-limit-writes-firestore , [O] https://firebase.google.com/docs/firestore/security/rules-conditions
- Recommended pattern: inside the callable, run a Firestore transaction on `usage/{uid}_{yyyymmdd}` that checks and increments the count **before** calling the LLM, and return `resource-exhausted` when the user is over the limit. Rules deny client writes to `usage/`. Add a global daily budget document and `maxInstances` as a cost ceiling. [S] pattern; the mechanisms are [O]
- Anonymous and email sign-ups are IP-rate-limited by Firebase. [O] https://firebase.google.com/docs/auth/android/anonymous-auth

### 4.4 Authentication on Android
- Google sign-in through **Credential Manager**: `androidx.credentials:credentials` and `credentials-play-services-auth` 1.3.0, plus `googleid` 1.1.1. Use `GetGoogleIdOption` with the **web** client ID, then `GoogleAuthProvider.getCredential(idToken)` and `signInWithCredential`. On sign-out, call `clearCredentialState`. [O] https://firebase.google.com/docs/auth/android/google-signin
- Anonymous sign-in, upgraded later with `currentUser.linkWithCredential(credential)`, which keeps the uid and its data. Handle `FirebaseAuthUserCollisionException` (credential already in use) by signing in to the existing account and merging data on the server [S]. With Identity Platform, an optional auto-delete removes unlinked anonymous accounts after 30 days, and those accounts then do not count toward quota. [O] https://firebase.google.com/docs/auth/android/anonymous-auth

### 4.5 Firestore modelling
- Suggested shape: `groups/{gid}` (name, currency, memberUids[]), `groups/{gid}/members/{mid}` (display name, optional uid, payment handles), `groups/{gid}/expenses/{eid}` (amountMinor as an integer, currency, fxRate snapshot, payer, shares map, createdBy, receipt text reference), and `groups/{gid}/settlements/{sid}`. Compute balances on the client from expenses, or keep a denormalised `balances` doc updated by a function. [S] design inference
- Transactions **fail offline**, while batched writes queue offline. All reads must happen before writes, total time is capped at 270 s, and use `FieldValue.increment` for simple counters. Implication: client writes for expenses should be plain or batched writes (offline-safe); recompute balances server-side or derive them on the client. [O] https://firebase.google.com/docs/firestore/manage-data/transactions
- Offline persistence is **on by default on Android** with a 100 MB cache (configurable through `PersistentCacheSettings`). [O] https://firebase.google.com/docs/firestore/manage-data/enable-offline
- Rules: check membership with `exists(.../groups/$(gid)/members/$(request.auth.uid))` or with `request.auth.uid in get(group).data.memberUids`. A request can make at most 10 document-access calls (20 for batches and transactions). Rules are not filters, so queries must constrain on membership. [O] https://firebase.google.com/docs/firestore/security/rules-conditions

### 4.6 Location
- The location **cannot be changed** after the database is created. Multi-region options: `nam5`, `nam7`, `eur3` (99.999% SLA). Regional locations are cheaper and have lower write latency (99.99%). Since 30 Oct 2024, new `*.firebasestorage.app` buckets no longer tie to the Firestore location. [O] https://firebase.google.com/docs/firestore/locations
- Put the functions region next to the database. The location choice matters under GDPR and Vietnam's cross-border rules. Either location is a cross-border transfer for Vietnamese users, because Google has no Vietnam region [U].

### 4.7 What needs Blaze (pay-as-you-go)
- Cloud Functions, Secret Manager, Cloud Run, phone-auth SMS, and Cloud Storage (new default buckets since Sep 2024; existing buckets since **3 Feb 2026**). [O] https://firebase.google.com/pricing , https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024
- Blaze still includes the free quotas. Functions: 2M invocations, 400K GB-s and 200K CPU-s per month. Firestore Standard: 50K reads, 20K writes and 20K deletes per day, 1 GiB stored. Auth: 50K MAU. [O] https://firebase.google.com/pricing
- Set a budget alert. Budgets notify but do not cap spending, so the quota document and `maxInstances` are the real ceilings [S].

### 4.8 Remote Config, Crashlytics, Analytics, EU consent
- Remote Config, Crashlytics and Analytics are no-cost products on both Spark and Blaze. [O] https://firebase.google.com/pricing
- Google's **EU user consent policy** (EEA, UK, Switzerland) requires legally valid consent for local storage where the law requires it and for ad personalisation, records of consent, instructions for withdrawal, and disclosure of each party that receives the data. [O] https://www.google.com/about/company/user-consent-policy/
- Android consent mode v2: set default `<meta-data>` keys `google_analytics_default_allow_analytics_storage`, `..._ad_storage`, `..._ad_user_data` and `..._ad_personalization` in the manifest, then call `Firebase.analytics.setConsent{}` at runtime. If the app shows no ads, set the ad keys to false permanently. [O] https://developers.google.com/tag-platform/security/guides/app-consent (exact key names via [S] vendor guides such as https://support.cookieinformation.com/articles/mobile-consent-solution/set-up-google-consent-mode-v2-for-android-apps/)

---

## 5. Privacy and compliance checklist (not legal advice)

**GDPR** [O] https://eur-lex.europa.eu/eli/reg/2016/679/oj
- Applies to a non-EU developer offering the service to people in the EU (Art. 3(2)). An **EU representative is required (Art. 27)** unless processing is "occasional" and low-risk; a consumer app that processes data continuously is unlikely to qualify for that exception. [O] https://gdpr-info.eu/art-27-gdpr/
- Legal basis: contract for core features, consent for analytics and other non-essential storage. Provide a privacy notice (Art. 13), data-subject rights to access, delete and export (Art. 15-20), processor agreements (Art. 28: Google Cloud/Firebase, the LLM vendor), and a lawful transfer mechanism (Ch. V: EU-US DPF or SCCs). [O]
- Receipt contents and other people's names are personal data of **non-users** too. Minimise them: no images uploaded by default, retention limits, and text sent to the LLM only for processing. Check the LLM vendor's data-retention and training terms. [S]

**CCPA/CPRA (California)**
- Applies to a for-profit business that meets one of these: annual gross revenue above **$26,625,000** (2025 adjustment), 100K or more California consumers or households, or at least 50% of revenue from selling or sharing personal information. A small indie app likely falls below these until it scales, but building the deletion and access flows now costs little. [O] https://cppa.ca.gov/announcements/2024/20241217.html , https://www.cppa.ca.gov/regulations/cpi_adjustment.html
- New regulations on risk assessments, cybersecurity audits and ADMT were adopted in September 2025. [O] https://cppa.ca.gov/regulations/

**Vietnam**
- The **Law on Personal Data Protection 91/2025/QH15** was passed on 26 Jun 2025 and took effect on **1 Jan 2026**. **Decree 356/2025/ND-CP**, effective 31 Dec 2025, replaces Decree 13/2023. [S] https://www.dlapiperdataprotection.com/?c=VN&t=law , https://www.tilleke.com/insights/new-decree-provides-guidance-for-vietnams-personal-data-protection-law , https://fpf.org/blog/fpf-releases-updated-issue-brief-on-vietnams-law-on-protection-of-personal-data-and-the-law-on-data/
- Storing data collected in Vietnam on foreign servers or foreign cloud counts as a **cross-border transfer**. It requires a transfer impact assessment filed with the Ministry of Public Security (reported deadline: within 60 days of starting the transfer) and a DPIA, using mandatory templates. Fines for cross-border violations reportedly reach up to 5% of prior-year revenue. Small enterprises and startups get some exemptions, such as from appointing a DPO. [S] https://www.china-briefing.com/china-outbound-news/vietnam-personal-data-protection-regulation-decree-356 , https://www.vietnam-briefing.com/doing-business-guide/vietnam/company-establishment/vietnam-personal-data-privacy-law
- Consent must be explicit, specific and recorded. Check whether the exemptions apply to an individual (non-enterprise) developer. [U]

**Google Play**
- **Data safety form**: declare everything the app and its SDKs collect or share, including Firebase Analytics, Crashlytics and the LLM call. Data processed only on the device (on-device OCR) is not "collected". Data sent off the device for ephemeral processing must still be declared, though it is not shown on the listing. Transfers to service providers are not "sharing". A privacy policy is mandatory. [O] https://support.google.com/googleplay/android-developer/answer/10787469
- **Account deletion**: apps with accounts must offer an in-app deletion path **and** a web page where users can request deletion without reinstalling. Retained data must be disclosed. Applies to anonymous accounts upgraded to real ones. [O] https://support.google.com/googleplay/android-developer/answer/13327111
- **Contacts**: for apps targeting Android 17 (API 37+), READ_CONTACTS is allowed only when the system Contact Picker (`Intent.ACTION_PICK_CONTACTS`) is insufficient. "Choosing a contact to transact with" or inviting someone is explicitly listed as a use that should *not* request it. Play Console declaration prompts started in September 2026, compliance is mandatory from **27 Jan 2027**, and a 30-day extension is available. [O] https://support.google.com/googleplay/android-developer/answer/16935362

---

## 6. Monetisation benchmarks

- Competitor pricing (US):
  - Splitwise Pro: about $4.99 per month or $39.99 per year [S]; IAPs from $2.99 to $39.99 [O].
  - Settle Up: $3.99 per month or $19.99 per year, group passes from $5.99 per week to $39.99 per year, $149.99 lifetime [O].
  - Splid: one-time $3.99 [O].
  - Tricount, Tab and Spliit: free [O].
  - The market anchor is roughly **$20-40 per year**, with **one-time or per-group passes** as a proven alternative for travellers.
- **Google Play service fee** [O] https://support.google.com/googleplay/android-developer/answer/112622
  - Auto-renewing subscriptions: **15%** in markets still on the old fee structure.
  - In rollout regions (EEA, UK and US from 30 Jun 2026; Australia and Japan from 30 Sep 2026), the fee is "10% + 5% billing fee", which is still 15% in total.
  - One-time products in rollout regions: 20% + 5% for new installs.
  - Elsewhere, 15% applies to the first $1M of annual revenue (enrolment required) and 30% above that.
- **RevenueCat**: free up to **$2,500 monthly tracked revenue**, then 1% of MTR. The Pro plan includes all features: paywalls, experiments, charts and integrations. Growth Tools cost 1% of MTR on conversions they drive. [O] https://www.revenuecat.com/pricing/
- **Free-tier limits on per-use paid features**: competitors gate AI receipt scanning entirely (Splitwise) or make receipt photos Premium (Settle Up). Splitwise caps expense entry. Exact AI scan quotas for indie apps (such as "N free scans per month") could not be verified [U]. Cost-driven framing: give free users a small monthly LLM quota, enforced by the server quota document in 4.3, and keep manual entry unlimited, which addresses the main Splitwise complaint.

---

## Unverified

- Splitwise's exact current free daily cap (sources give 3, 4 or 5, with a 10 s cooldown) and its official Pro price; the Splitwise help page could not be fetched.
- Venmo URL parameters `txn`, `amount` and `note`, and the `venmo://paycharge` scheme. The official payment-links page no longer documents them. Also unverified: Venmo's terms for third-party deep links.
- Cash App's `cash.app/$tag/{amount}` suffix.
- Wise's personal Wisetag URL structure (`wise.com/pay/me/{tag}`) and whether a personal link can carry an amount.
- Revolut: whether `revolut.me` URLs accept an amount parameter, and per-country card caps. The help pages returned 403 to direct fetch, so these facts come from search snippets.
- PromptPay and VietQR field details come from open-source implementations, because the official BoT and NAPAS specifications are not public. The current NPCI linking spec version is newer than v1.6 (2017). The VietQR.io October 2026 API-change notice content is unknown.
- EPC069-12 v3.1 content and its 1 Feb 2026 effective date (reported by Febelfin only).
- Whether the Open Exchange Rates free plan permits commercial use.
- Splid's free-tier group limit; Tricount account requirement; whether Settle Up has offline mode; Spliit's multi-currency support.
- The Vietnam TIA 60-day filing deadline, the 5% fine cap, and whether exemptions apply to a solo developer.
- Exact Android consent-mode manifest key names (taken from vendor guides; Google's Android page could not be fetched).
- That Google Cloud has no region in Vietnam.

## Implications for the plan

1. Position the app as an Android-first combination of on-device OCR, a natural-language instruction and a persistent group ledger. Keep manual entry unlimited and free, and meter only LLM calls.
2. Build one callable Cloud Function (2nd gen, Node) for the LLM. Enable `enforceAppCheck` and `consumeAppCheckToken`, bind the key with `defineSecret`, stream with `sendChunk`, set `maxInstances`, and give the client a timeout above 70 s.
3. Enforce a per-user daily LLM quota in a Firestore transaction before every LLM call, plus a global daily budget document. Use a budget alert for notification only.
4. Start users with anonymous sign-in and upgrade them through Credential Manager Google sign-in using `linkWithCredential`. Handle the credential-collision merge case on the server.
5. Write expenses with plain or batched writes so they work offline. Never require a transaction on the client. Store integer minor units, the currency and the FX rate snapshot on every expense.
6. Choose the Firestore location once, deliberately (for example `eur3` or a regional EU location), and record the decision. It cannot be changed later.
7. Upgrade to Blaze from day one (Functions and Secret Manager require it). Avoid Cloud Storage unless receipt images are actually needed.
8. Run a daily scheduled FX job using Frankfurter and write one `fxRates/{date}` document, with Open Exchange Rates Developer as the paid fallback. Show the rate date and source, and let users override the rate.
9. Generate payment handoffs for the documented formats only: PayPal.Me, UPI intent or QR, PromptPay QR, VietQR (own EMVCo encoder), and EPC QR (EUR only). Treat Venmo and Cash App as best-effort links, offer Revolut, Wise and Zelle as "paste your link" or copy-details fields, and use no collect requests.
10. Use the system Contact Picker and do not request READ_CONTACTS, so the app is ready for the 27 Jan 2027 Play policy.
11. Before launch: privacy policy, Data safety form (Analytics, Crashlytics and the LLM call declared), in-app and web account deletion, consent-mode defaults set to denied for the EEA/UK, an EU representative, a DPA with the LLM vendor, and a decision on filing a Vietnam TIA and DPIA.
12. Pricing: launch at about $2.99-3.99 per month or $19.99-29.99 per year, plus a one-time trip pass, sold through Play Billing and RevenueCat (free under $2,500 MTR). Budget for a 15% store fee.
