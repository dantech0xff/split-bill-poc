# Code review: the app side of Pro (Phase 8)

Date: 2026-11-17 (UTC). Range `9e8f32e..42307f0`, paths `app`, `gradle`, `README.md`, `docs/architecture.md`
(app commits 01415a1, 8e292cc, f9d4ce7, 86fa51b, 77df2c2, 6e70943, 222783a, 298a0d5, 8fa8ccd, 42307f0).
For these paths the working tree equals 42307f0; only b41360a, the implementer's report, follows it.
This review changed no code, test or doc.

## Scope

- 70 files, +3,214 / -117. About 1,590 of the changed lines are main code: `billing/`, `ui/paywall/`,
  `data/account/`, `data/proxy/ProxyApi.kt`, `llm/ProxyModelCall.kt`, `ui/split/`, `ui/settings/`,
  `ui/AppNavHost.kt`, `di/AppModule.kt` and `MainActivity.kt`. The rest is tests, screenshots, build files and docs.
- Contract: the phase-08 sections "Hợp đồng chung" (App, and the proxy routes it calls), "Requirements" and
  "Success Criteria"; the researcher report, sections 1, 4 and 5; the implementer's report, whose decisions
  were judged rather than taken as given.
- Evidence beyond the diff:
  - the RevenueCat 11.3.0 AAR from the Gradle cache, read with `javap`;
  - the AAR manifests of `play-services-ads-identifier` 17.0.1, `play-services-location` 19.0.0 and
    `play-services-places-placereport` 17.0.0;
  - the merged prodRelease manifest and R8's `configuration.txt` in the app module's build outputs;
  - Google's reference for `ProductDetails.PricingPhase`;
  - the branch's CI runs.
- No Gradle invocation was run. Every finding below rests on the code, the SDK's bytecode or Google's
  reference, and the earlier attempt probably ran out of memory. No daemon was started, so none had to be stopped.

## Overall assessment

The identity rules hold in every path I traced. RevenueCat is configured only with the proxy's purchaser and
never anonymously, users are switched with `logIn`, and the SDK is closed on sign-out, on a sign-in that
runs out and on deletion. In 11.3.0 `close()` really resets the singleton, so the next start configures
afresh. The exception types match the SDK. The out-of-calls matrix, Settings, the deletion text and the 429
on deletion all match the contract.

Two High issues:
- buying is not blocked when the proxy says `not_configured`, against the owner's decision;
- a single-payment intro offer would be described as lasting "0 months", because the SDK passes Google's 0
  through unchanged.

Two Medium issues:
- the Pro screen lets RevenueCat's inactive entitlement override the proxy's Pro (renewal gap, account hold,
  stale cache), and then tells the subscriber the subscription "belongs to another Split Vibe account";
- a purchase the SDK has not yet sent to RevenueCat goes to whoever signs in next, while the code comments
  and the docs say the opposite.

## Critical

None found.

## High

### H1. Buying is not blocked when the proxy answers `not_configured`

Where:
- `ui/paywall/ProViewModel.kt:110-132`: when `POST /v1/plan` fails on opening, the failure is kept as
  `refreshFailure` and becomes a dismissable `message` (line 130). The plans load anyway.
- `ui/paywall/ProScreen.kt:271-276`: the main action is BUY whenever a plan is chosen, nothing is loading
  and `loadError == null`. The message plays no part.
- `billing/BillingRepository.kt:135-144`: after a purchase or switch, `completed()` turns every
  `ProxyException`, `not_configured` included, into `plan = null`.
- `ui/paywall/ProViewModel.kt:158` and `225-226`: the person is then told Pro counts "usually within a few minutes".
- `ProViewModelTest.kt:205-227` pins this behaviour: for 503 `not_configured` it asserts that the plans show.

Failure:
1. The Worker runs without `REVENUECAT_SECRET_KEY`, after a first deploy or a botched rotation.
2. The Pro screen shows "Split Vibe's server cannot check subscriptions yet." with "Subscribe for $3.99 a
   month" right under it.
3. The person pays. RevenueCat reports the entitlement active, so the screen switches to "You have Pro, 300
   AI calls a month", while the calls line and Settings still say 10 calls and "Free plan".
4. The webhook gets 503 as well, and RevenueCat stops after 5 retries (about 80 minutes).
5. The account stays at 10 calls until the secret is set and the Pro screen is opened again, or until a
   renewal event arrives.

The owner decided that buying must be blocked in this state. It is not, and neither is a member's switch.

Fix:
- Treat `not_configured` from the refresh on opening as a blocking state: no plan rows, no Subscribe, no
  Switch, and the existing "Pro cannot be bought right now" text. Restore can stay.
- Re-check in `BillingRepository.buy` and `switchTo`: call `refreshPlan()` first and refuse on
  `not_configured`, or keep the last answer in the repository, so that a screen opened before the proxy
  said so cannot buy.
- Change the test so that 503 `not_configured` asserts that no purchase is reachable. The other refusals
  (429, 502, 500) keep today's behaviour, which is not fatal.

### H2. A single-payment intro offer is described as lasting "0 months" (latent until such an offer exists)

Where:
- `billing/RevenueCatBilling.kt:101-107` maps `cycles = phase.billingCycleCount ?: 1`.
- `ui/paywall/ProTexts.kt:69-76` multiplies the period by `cycles`. When `cycles != 1` it writes
  "<price> every <period> for <total>".

Evidence:
- In RevenueCat 11.3.0, `google/PricingPhaseConversionsKt.toRevenueCatPricingPhase` builds
  `billingCycleCount` as `Integer.valueOf(ProductDetails.PricingPhase.getBillingCycleCount())`. It is never
  null, and `PricingPhase.getBillingCycleCount()` returns the field unchanged.
- `SubscriptionOptionConversionsKt.toSubscriptionOption` builds every option's phases with this converter.
- Google's reference for `getBillingCycleCount()` says: "Set to 0 for INFINITE_RECURRING and NON_RECURRING".
- RevenueCat's KDoc says the count is null for those modes, and the `?: 1` relies on that. The converter
  that ships does not do it.

Failure:
- The owner adds a "Single payment" intro price in Play Console, for example $5.99 for the first 3 months on
  the yearly plan. This is Play's third kind of offer, besides free trials and discounted recurring payments.
- The phase arrives as NON_RECURRING, P3M, count 0. The plan row and TalkBack both say "$5.99 every 3 months
  for 0 months, then $29.99 a year."
- That misstates the offer terms that Play's subscriptions policy requires. It also breaks the owner's 17/11
  decision that a later offer shows its terms correctly without a new release, and fixing it then would take one.
- Free trials and discounted recurring intro prices (FINITE_RECURRING, with a count of 1 or more) render correctly.

Test gap:
- `ProTextsTest.kt:44,48` ("paid up front") feeds `cycles = 1`, a value the SDK never produces for this kind of offer.
- `planOf` has no test, so the suite passes with the bug in place.

Fix:
- In `planOf`, write the cycles as
  `if (phase.recurrenceMode == RecurrenceMode.NON_RECURRING) 1 else phase.billingCycleCount?.takeIf { it > 0 } ?: 1`,
  and leave the plan out if an INFINITE_RECURRING phase comes before the last one.
- Move the phase mapping into a plain function over RevenueCat's public
  `PricingPhase(Period, RecurrenceMode, Int?, Price)`.
- Test it with the three shapes Play sends: a free trial (FINITE_RECURRING, 1), a discounted recurring
  price (FINITE_RECURRING, n) and a single payment (NON_RECURRING, 0).

## Medium

### M1. RevenueCat's inactive entitlement overrides the proxy's Pro, and Play's "already owned" reads as another account's subscription

Where:
- `ProViewModel.kt:63`: `member = membership?.active ?: (callsLeft?.plan == Plan.PRO)`. The proxy's plan
  counts only when RevenueCat returned no `pro` entitlement at all.
- `ProScreen.kt:107-111`, `152`, `273`: someone who is not a member gets the sales pitch and Subscribe, and
  the billing-issue notice exists only inside `MemberCard`.
- `ProViewModel.kt:261` maps `ProductAlreadyPurchasedError` (Play's ITEM_ALREADY_OWNED) to the
  another-account text.
- `RevenueCatBilling.kt:57` reads customer info with the default policy, CACHED_OR_FETCHED.

Failures, each on the account that owns the subscription:
1. Play's renewal gap. After a period ends, RevenueCat can report the entitlement as expired for minutes to
   hours while Play renews it (researcher, section 2.1). The proxy keeps Pro on purpose (the contract's
   rule: `play_store`, auto-renew on, 24 hours of grace).
   - Settings shows "Pro plan" and "Manage Pro", but the Pro screen shows "300 AI calls a month" and
     "Subscribe for $3.99 a month".
   - Tapping it returns ITEM_ALREADY_OWNED, and the screen says "The subscription on this Google Play account
     belongs to another Split Vibe account. Sign in with that account to use it." That is false.
2. Account hold. The entitlement is inactive with `billingIssueDetectedAt` set. The screen offers a new
   subscription and never says to update the payment method, although the contract asks for a member's
   billing issue to be shown.
3. Stale cache and failed posts.
   - On a second phone, the cached CustomerInfo can predate the purchase, and the screen offers to buy what
     the account already has.
   - The receipt post to RevenueCat can fail after Play has charged. In 11.3.0 the orchestrator then sends
     the post's error to the purchase, so the screen says "There is no connection. Check it and try again.",
     and trying again produces the same false another-account text.

Fix:
- Make `member = membership?.active == true || callsLeft?.plan == Plan.PRO`.
- Show a member state for an inactive Play entitlement that still `willRenew` or has `billingIssue`: either
  "renewal pending", or the billing-issue card with the manage link.
- On `ProductAlreadyPurchasedError`, run `restore()` and then `POST /v1/plan` before choosing the text. A
  restore that works means this account's Pro. `ReceiptAlreadyInUseError` means another account's. This
  keeps the contract's message for the case it describes.
- Consider `CacheFetchPolicy.FETCH_CURRENT` when the screen opens.

### M2. Purchases not yet sent to RevenueCat go to whoever signs in next, while the comments and docs say the opposite

Where:
- `billing/BillingRepository.kt:50-68` and `129-132`: `follow()` stops or switches the store without first
  trying to send what is pending.
- The opposite claim appears in `BillingRepository.kt:52-53` ("…then go as the account that made them"),
  `MainActivity.kt:38-39` and `docs/architecture.md:343-344`.

Evidence: in RevenueCat 11.3.0, `PostPendingTransactionsHelper` queries the purchases for
`IdentityManager.getCurrentAppUserID()` and posts the ones not yet sent as that user. The researcher's
section 1.3 says the same, from the SDK's sources.

Failure:
1. A buys and Play charges. Either the post to RevenueCat fails (no connection, or a RevenueCat 5xx), or the
   payment is pending (a slow payment method).
2. A signs out, or A's sign-in runs out, and B signs in on the same phone.
3. Once the SDK is configured as B, it posts A's token as B.
4. With "Keep with original App User ID", Pro then stays with B, and A's restore gets `ReceiptAlreadyInUseError`.

It is unknown whether RevenueCat checks the `obfuscatedAccountId` hash (marked unverified in the research).
The implementer accepted only the other half of this risk, the refund after 3 days.

Fix:
- Before `stop()`, or before switching to another purchaser, run a bounded `awaitSyncPurchases()` as the
  current purchaser when a purchase failed to post or is pending. The app knows both from the error it showed.
  Alternatively, warn at sign-out.
- Correct the three comment and doc lines.
- Record the remaining risk for pending payments.

## Low

### L1. `stop()` during a purchase or a restore leaves it hanging

- `BillingRepository.kt:102`, `111`, `119` and `122-127` run the store call outside the `identity` mutex, so
  `follow()` can `close()` the SDK in the middle of a purchase (`RevenueCatBilling.kt:46-50`).
- In 11.3.0, `PurchasesOrchestrator.close()` empties `purchaseCallbacksByProductId` and closes the backend
  and the billing client.
- Trigger: while the Play sheet is open, a background proxy call finds the session expired or gets a 401
  and ends it (`DeviceRegistrar.kt:25-32`, `GroupRepository.kt:199`, `RequestRepository.kt:166`).
- Result: "Waiting for Google Play…" stays until the person leaves the screen, and the purchase is not sent
  until the SDK is next configured, possibly as someone else (see M2).
- Fix: run `purchase` and `restore` inside `identity.withLock`, so that `stop()` waits for them.

### L2. The previous account's Pro is shown after another sign-in on the same Pro screen

- `ProViewModel.kt:109` keeps `membership` when a load starts, line 138 keeps it when the load fails, and
  line 207 hides it only while nobody is signed in.
- Scenario: A's sign-in runs out while the screen is open, and B signs in from it. While B's plans load, and
  for good if that load fails, B sees "You have Pro, Monthly plan, Renews on …" and A's billing issue.
- Fix: clear `membership` and `message` in `load()`, and whenever the session changes.

### L3. Some error texts point the wrong way

- `ProViewModel.kt:263` maps `PurchaseNotAllowedError`, which means this phone or account may not buy, to
  "Google Play has a problem right now. Try again later."
- Line 264 answers `ConfigurationError` and `ProductNotAvailableForPurchaseError` with "That did not work.
  Please try again." These come, for example, from products not yet active in Play or from the dev flavor.
  Retrying cannot help, and the existing "Pro cannot be bought right now" text fits.
- Line 250 shows raw exception text, such as "Something went wrong: RevenueCat is used only once a purchaser
  is signed in", or the SDK's "There is no singleton instance…".
- Fix: give each case its own text, and keep exception messages out of the UI.

### L4. A test proves less than its name

- `ProViewModelTest.kt:72-81` calls `buy()` while signed out. `buy()` returns at `state.value.chosen ?: return`
  because no plans are loaded, so the test would still pass if `BillingRepository.ready()` lost its sign-in
  check. (`BillingRepositoryTest.kt:94-121` does cover `ready()`.)
- Fix: give the signed-out view model plans, so that the sign-in check is what stops the purchase.

### L5. Guard against `AD_ID` (hardening; nothing to remove today)

- The merged prodRelease manifest has no `com.google.android.gms.permission.AD_ID`. None of
  `play-services-ads-identifier` 17.0.1, `play-services-location` 19.0.0 and
  `play-services-places-placereport` 17.0.0 declares a permission.
- RevenueCat reads the advertising ID only through `collectDeviceIdentifiers()` and the attribution-ID
  setters (`setAdjustID` and the like), and the app calls none of them.
- The app shows no ads and its Data safety answer will be "no advertising ID". So add `xmlns:tools` and
  `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />` to
  `app/src/main/AndroidManifest.xml`. Then a later dependency bump (ads-identifier 18 or later, Firebase
  Analytics) cannot add it silently.

### L6. The `dev` flavor gets the production key

- `app/build.gradle.kts:106` sets the key in `defaultConfig`, so every flavor has it.
- Dev installs configure the production RevenueCat project for a package Google Play does not sell.
  `GET /subscribers` then creates customers there, which are junk if the debug proxy derives purchasers
  differently, and the Pro screen shows the generic error from L3.
- Fix: set the key to empty for `dev` with a flavor-level `buildConfigField`. This is the implementer's open question 2.

## Checked and found sound

SDK lifecycle and identity:
- A start while signed out never configures the SDK.
- A start while signed in with a stored purchaser configures it with that purchaser (`MainActivity`, then `follow()`).
- An install upgraded from before Pro has no stored purchaser; it waits for `GET /v1/usage` or `POST /v1/plan` to bring one.
- Sign-in with and without a purchaser in the answer both behave as the contract says.
- Sign-out, a sign-in that runs out and deletion all close the SDK.
- In 11.3.0, `Purchases.close()` sets `backingFieldSharedInstance` to null and `PurchasesOrchestrator.close()`
  ends the Play Billing connection, so a later start configures afresh.
- A failed `logIn` closes the SDK rather than going on as the previous user.
- A rotation restarts `follow()`, and restarting it changes nothing. After process death, the SDK is
  configured again from the encrypted store.
- `logOut` is never called, and `configure` always gets an `appUserID`.
- The purchaser must be 64 lowercase hex characters, and an answer that arrives after the session changed is
  dropped by comparing sessions.
- All SDK calls run on the main thread, and identity changes happen one at a time under one mutex.
- The startup initializer that RevenueCat adds only registers activity callbacks.

Exceptions, read from the bytecode:
- `awaitLogIn`, `awaitRestore` and `awaitOfferings` throw `PurchasesException`.
- `awaitPurchase` throws its subclass `PurchasesTransactionException(error, userCancelled)`.
- All of them resume through a `CancellableContinuation`, so they can be cancelled.
- A cancelled purchase says nothing. A pending payment is a notice, not an error, and triggers no refresh.

Purchase and switch:
- Purchases go through `PurchaseParams.Builder(activity, package)`, and the product id is checked against the
  current offering before buying.
- A switch passes `oldProductId` as the subscription id without `:basePlan`, with
  `StoreReplacementMode.WITHOUT_PRORATION` in both directions.

`POST /v1/plan`:
- It is sent with `{}` and the session when the screen opens, and after each completed purchase, switch and restore.
- 429 (with `Retry-After`), 502 and 500 are not fatal, and a 401 turns into a sign-in to renew.

Out of calls:
- Trial, free and Pro, in a build that sells Pro and in one that does not, behave as the contract says, both
  in the bill read and in the assistant, from one `ProxyModelCall`.
- The proxy always sends `usage` with `quota_exhausted`, and a sign-in that ran out still leads to Settings.

Settings and deletion: the plan row and its Pro button, the deletion text, and a 429 on `DELETE /v1/account`,
which keeps the session and says when to try again.

Pro screen and Play's policy:
- Google Play's formatted prices, with the yearly plan priced per year.
- The renewal terms and how to cancel.
- The manage link `?sku=pro&package=${APPLICATION_ID}` for an active Play subscription.
- "Split Vibe works without a subscription", and signing in before buying.

Accessibility:
- Each plan is one radio button that reads its name, price and offer, inside a `selectableGroup`.
- Card titles are headings, messages and progress are polite live regions, and the back button is labelled.
- At the default font size, the whole Pro screen fits above the bottom bar on a 915 dp phone. The Pro images
  are drawn on a 1200 dp canvas, so they do not show this themselves.

Build:
- The key must match `goog_[A-Za-z0-9_-]+`. The error message does not print it, and the pattern cannot
  break out of the `buildConfigField` string.
- R8 picks up RevenueCat's and Billing's consumer rules (`configuration.txt`), and there is no `missing_rules.txt`.
- The SHA-256 of these cached artifacts matches `verification-metadata.xml`, which only gains entries:
  RevenueCat's AAR and module file, `billing` 9.1.0 and `play-services-ads-identifier` 17.0.1.
- The merged release manifest gains one permission, `com.android.vending.BILLING`.

Screenshots: 47, 48 and 49, and the re-recorded Settings images, show what the code draws for their inputs.
A trial is labelled "Free plan", as the code does.

## Recommended actions

1. H1: block buying and switching on `not_configured`, and change the test that pins today's behaviour.
2. H2: count a NON_RECURRING phase as one cycle, and test the mapping with the phase shapes Play actually sends.
3. M1: let the proxy's Pro count as membership, show the renewal-pending and billing-issue states, and
   restore before saying "another account".
4. M2: send pending purchases before stopping or switching the store, and correct the three claims.
5. L1 to L6 as listed.

## Metrics

- Gradle was not run by this review (see Scope).
- The implementer reports 405 unit tests passing, and lint and the release build passing on the final tree.
- CI on this branch: the device tests passed on 222783a, but there is no unit-test run for 298a0d5..42307f0.
- Type coverage and test coverage were not measured. `RevenueCatBilling` has no tests.
- Lint was not run.

## Unresolved questions

1. Should the dev flavor carry the RevenueCat key at all (L6)?
2. Once the restore-first check (M1) exists, should `ProductAlreadyPurchasedError` still say "another
   account" when that restore succeeds?
3. Does RevenueCat refuse a token whose `obfuscatedAccountId` hash names a different app user ID (M2)? This is unverified.
