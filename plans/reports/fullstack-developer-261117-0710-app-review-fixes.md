# App: fixes for the review of the Pro code

Date: 2026-11-17 (UTC). Branch `feat/phase-08-pro-subscription`. The review is
[code-reviewer-261117-0705-phase-8-app-review.md](code-reviewer-261117-0705-phase-8-app-review.md);
every finding is fixed as the coordinator decided. Only `app/**`, `README.md`, `docs/architecture.md`
and this report changed.

## Outcome

All ten findings are fixed and tested, and every required check passes on the final tree:

| Check | Result |
|---|---|
| `ktlintCheck` (root task) | Passes |
| `:app:testProdDebugUnitTest` | 433 tests in 63 classes pass, 28 of them new (405 before); every screenshot verifies |
| `:app:testDevDebugUnitTest` | 433 tests pass, screenshots included |
| `:core:domain:test`, `:core:designsystem:testDebugUnitTest` | 198 and 3 pass, run again with `--rerun` |
| `:app:lintProdDebug`, in its own invocation | Passes: 0 errors and 14 warnings, all of kinds from before (target API, newer versions, `enableOnBackInvokedCallback`, backup rules, an unneeded `v26` folder); none points at code changed here |
| `:app:assembleProdRelease`, in its own invocation | Builds; no `missing_rules.txt`, and the merged release manifest has no `AD_ID` |
| `:app:compileProdDebugAndroidTestKotlin` | Compiles |
| Screenshots | None changed, so none was re-recorded; all of them verify in both flavors |

No dependency was added, so `gradle/verification-metadata.xml` is unchanged.

## Findings

### H1. Buying is blocked while the proxy answers `not_configured`

- `BillingRepository.onSale` (a `StateFlow`) keeps the proxy's last answer to `POST /v1/plan`. It turns
  false on 503 `not_configured` and true again on any answered check. 429, 502 and 500 leave it as it is.
- The Pro screen combines it into `ProUiState.onSale`. While it is false:
  - there are no plan rows, no Subscribe and no switch card;
  - the existing "Pro cannot be bought right now. Please try again later." text shows in their place;
  - Restore and "Manage subscriptions in Google Play" stay;
  - `ProViewModel.buy` and `switchTo` return without doing anything.
- The bottom message on opening still says "Split Vibe's server cannot check subscriptions yet.", as it
  did, so the person sees the reason as well.
- `BillingRepository.buy` and `switchTo` check in this order:
  1. the sign-in;
  2. the plan, by asking the proxy with `POST /v1/plan` first;
  3. they refuse with the new `NotOnSaleException` when that check says `not_configured`, or when the
     proxy cannot be reached and its last answer said so.
  - A screen opened earlier therefore cannot buy.
  - A refused session ends the sign-in and stops the purchase.
  - A limit, a 502 or a 500 does not stop it.
- Cost: each purchase or switch sends one more `POST /v1/plan`. The proxy allows 30 an hour per account,
  and a refused check never stops a purchase.
- Tests:
  - `ProViewModelTest`: "while Split Vibe's server cannot check subscriptions, no purchase and no
    switch can be made, and a restore can". It replaces the `not_configured` case of the test that
    pinned the old behaviour. That test now covers 429, 502 and 500 only, and also asserts that Pro
    stays on sale.
  - `ProViewModelTest`: "a screen opened while Pro was on sale cannot buy once the server says it
    cannot check subscriptions".
  - `BillingRepositoryTest`: "while the proxy cannot check subscriptions nothing is bought or switched,
    until it checks again", which also restores in that state.
  - `BillingRepositoryTest`: "a check before a purchase refused for a limit or a server problem does
    not stop the purchase".
  - `BillingRepositoryTest`: "without the proxy just then, its last answer decides whether Pro is on
    sale".
  - `ProScreenTest`: "while Pro is not on sale there is no plan to buy and no switch, but restoring
    and managing stay", for a member too.

### H2. A single-payment offer states one period

- The phase mapping is now a plain function, `offerOf(List<PricingPhase>)`, in `RevenueCatBilling.kt`,
  with `periodOf(Period)` beside it. It works on RevenueCat's public `PricingPhase`.
- How it reads a phase:
  - a `NON_RECURRING` phase counts as one cycle;
  - a count of 0 or none counts as one;
  - an `INFINITE_RECURRING` phase before the last phase leaves the plan out (null), and so does a
    period whose unit cannot be read.
- Test: `RevenueCatOfferTest` (6) builds real `PricingPhase` objects and checks the text each one
  produces:
  - a free trial (`FINITE_RECURRING`, 1, P7D): "Free for 7 days, then $3.99 a month.";
  - a discounted recurring price (`FINITE_RECURRING`, 3, P1M): "$1.99 a month for 3 months, then
    $3.99 a month.";
  - a single payment (`NON_RECURRING`, 0, P3M) on the yearly plan: "$5.99 for the first 3 months, then
    $29.99 a year.";
  - a count of 0 or null; an endless phase and an unknown period, which leave the plan out; and the
    full price alone, which is no offer.

### M1. The proxy's Pro, a renewal Google Play has not made yet, and "already owned"

- Membership:
  - `member = membership?.active == true || callsLeft?.plan == Plan.PRO`, exactly as decided.
  - `ProMembership.awaitingRenewal` means an entitlement that is inactive, from Google Play, and still
    `willRenew` or has a `billingIssue`.
  - `ProUiState.subscribed` (member or awaiting renewal) decides whether the screen shows the member
    card or the pitch. In that state there is no pitch, no plan rows and no Subscribe.
  - The switch card appears only while RevenueCat reports the entitlement active.
- What the member card says:
  - the heading is "You have Pro" for a member, and "Your Pro subscription" for someone the proxy does
    not count as Pro;
  - a renewal Google Play has not made yet reads "Renewal pending: Google Play is renewing your
    subscription, which can take a few hours.";
  - a billing issue shows the existing notice, which now has an "Open Google Play" button. The button
    opens the manage link, and `manageUrlOf` now gives the subscription's own Play link
    (`?sku=pro&package=…`) for a subscription awaiting renewal too.
- `ProductAlreadyPurchasedError`, on a purchase or on a switch:
  1. The screen shows "Restoring your purchases…" and runs `BillingRepository.restore()`, which ends
     with `POST /v1/plan`.
  2. A restore that works shows the account's Pro with a non-error message, such as "Your Pro
     subscription is back on this account.".
  3. `ReceiptAlreadyInUseError` from the restore gives today's "belongs to another Split Vibe account"
     text.
  - `ProductAlreadyPurchasedError` no longer maps to that text directly.
  - The restore message also covers a subscription awaiting renewal: "Your Pro subscription was found.
    Pro comes back once Google Play has renewed it.".
- `CacheFetchPolicy.FETCH_CURRENT`:
  - `Billing.membership` now takes RevenueCat's `CacheFetchPolicy`;
  - `BillingRepository.offer()`, which only the Pro screen's load calls, passes `FETCH_CURRENT`;
  - `RevenueCatBilling` hands it to `awaitCustomerInfo(policy)`.
- Tests:
  - `ProViewModelTest`:
    - "the proxy's Pro counts as membership although RevenueCat reports the subscription ended";
    - "a subscription Google Play is still renewing or waits for a payment for shows as the
      person's, not as one to buy";
    - "when Google Play says its account has the subscription already, a restore finds this
      account's Pro and shows it", which also checks the plan requests before the purchase and after
      the restore;
    - "a subscription of this Google Play account that another Split Vibe account bought says to
      sign in with that one", now both for a purchase RevenueCat refuses and for "already owned"
      followed by a refused restore;
    - "opening the screen asks RevenueCat's server for the person's Pro, not the copy its SDK keeps".
  - `ProScreenTest`:
    - "a subscription Google Play is still renewing shows as the person's, never as one to buy";
    - "a payment Google Play could not take shows the notice, which opens the subscription in Google
      Play", which checks the started intent's URL;
    - "the proxy's Pro shows as Pro although RevenueCat reports the subscription ended".
  - `ProTextsTest`: "a subscription Google Play is still renewing says so, and one waiting for a
    payment leaves it to the payment's notice", and the manage-link test now covers subscriptions
    awaiting renewal.

### M2. A purchase left unsent is synced as its buyer before the store stops or switches

- `Billing.syncPurchases()` calls `awaitSyncPurchases()`. In 11.3.0 it resumes through a
  `CancellableContinuation`, so a timeout really ends the wait.
- `BillingRepository` keeps track of two things: who the store runs as (`runningAs`), and the purchaser
  whose purchase this run of the app may have left unsent (`unsentBy`).
- Before the store stops, or starts as another purchaser, it runs the sync as the current purchaser
  under `withTimeoutOrNull(5_000)` when `unsentBy` is that purchaser. A failure or a timeout is
  ignored, and the stop or switch goes ahead.
- What marks a purchase or switch as unsent:
  - any `PurchasesException` except those that leave nothing to send: cancelled, already owned,
    receipt in use, not allowed, not available, configuration. This covers `PaymentPendingError`,
    `NetworkError`, `StoreProblemError` and the backend's codes;
  - a success whose Pro the SDK worked out on the phone (`ProMembership.offline`). The SDK sets this
    after a 5xx when it computes offline entitlements, and marks that customer info
    `VerificationResult.VERIFIED_ON_DEVICE`, read from the 11.3.0 sources;
  - a purchase coroutine that was cancelled while Google Play was open.
- Why the rule is broad: a failed receipt post reaches the purchase with whatever code the backend
  sends, and a network failure shares `NetworkError` with Google Play's own. "May be unsent" is the
  safe reading, and an unneeded sync costs at most five seconds at sign-out.
- The three claims are corrected so that they say what the SDK does: the KDoc of
  `BillingRepository.follow`, the comment in `MainActivity.onCreate`, and `docs/architecture.md`. The
  SDK sends such a purchase as whoever it runs as when it next tries.
- `docs/architecture.md` now records what remains:
  - a payment still pending, or a purchase the sync could not send, is later sent as whoever is signed
    in then, who keeps the subscription under "Keep with original App User ID";
  - a purchase left by an earlier run is not known to this one;
  - the sync also runs after an account deletion.
- Tests, in `BillingRepositoryTest`:
  - "a purchase left unsent is sent as its buyer before the store stops", for a failed send, a pending
    payment and Pro worked out on the phone;
  - "a purchase left unsent is sent as its buyer before another account's purchaser takes the store";
  - "nothing is sent before the store stops when no purchase was left unsent", for a confirmed
    purchase, a cancelled one, one RevenueCat keeps with another account, and no purchase at all;
  - "the store stops all the same when a purchase left unsent cannot be sent within a few seconds".
    It uses Robolectric's clock: still running after 4 seconds, stopped at 5; a failing sync stops
    the store at once.

### L1. A sign-out waits for an open purchase or restore

- `purchase`, `restore` and reading the offer run inside `identity.withLock` (`asPurchaser`), the same
  lock as `follow()`, so `stop()` waits for them.
- The offer is under the lock too. A `close()` in the middle of reading offerings has the same problem,
  and one helper keeps all store calls alike.
- Test: `BillingRepositoryTest` "a sign-out while Google Play's purchase or a restore is open waits
  until it is done". It holds a purchase, then a restore, open on a gate, signs out, and checks that
  the store still runs as the buyer. Once the gate opens, the sign-out stops the store.

### L2. The previous account's Pro is forgotten

- `ProViewModel.load()` clears `membership` and `message`.
- A collector on the account's session clears both whenever the session changes.
- Test: `ProViewModelTest` "another sign-in on the open screen shows nothing of the account before,
  also when its plans cannot load".

### L3. Error texts

- `PurchaseNotAllowedError`: "This phone or this Google Play account cannot buy subscriptions."
- `ConfigurationError` and `ProductNotAvailableForPurchaseError`: "Pro cannot be bought right now.
  Please try again later." The text now lives in `ProTexts.kt` as `NOTHING_ON_SALE`.
- Anything unexpected: "That did not work. Please try again." No exception message reaches the UI.
- The Pro screen's sign-in uses Settings' `messageOf`, whose last branch showed the exception's
  message. That branch now says "Something went wrong. Please try again." in both screens.
- Tests:
  - `ProViewModelTest` "each failure of the store or the proxy has a plain message", extended;
  - `ProViewModelTest` "a failure of the app's own says only that it did not work, never what the
    exception says";
  - `AccountMessagesTest` "an unexpected failure says only that something went wrong, never what the
    exception says".

### L4. The signed-out test now reaches the sign-in check

- `ProViewModelTest` "signed out, nothing goes to RevenueCat, and the plans left on screen buy nothing
  until the person signs in again":
  1. it checks that nothing reaches RevenueCat or the proxy before signing in;
  2. it signs in and loads the plans;
  3. it lets the sign-in run out with the plans still on screen, then buys.
- The repository's sign-in check stops the purchase with "Sign in with Google to buy Pro.". No purchase
  starts and the proxy is not asked.

### L5. `AD_ID`

- `app/src/main/AndroidManifest.xml` declares `xmlns:tools` and
  `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />`,
  with a comment that says why.
- The merged `devDebug` and `prodRelease` manifests hold `com.android.vending.BILLING` and no `AD_ID`.

### L6. No RevenueCat key in `dev`

- The `dev` flavor sets `buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")`. Its generated
  `BuildConfig` has `REVENUECAT_API_KEY = ""`. `prod` keeps the value from `defaultConfig` and was not
  printed.
- A `dev` build therefore sells no Pro, never calls RevenueCat, and its Pro screen says Pro is not
  available in this build.
- `README.md` says so where the key is described, and the flavor row of the build variants table in
  `docs/architecture.md` says it too.

## Commits

- `f2e3223` fix(app): state a single-payment offer as one period, not 0 cycles
- `400b0a5` fix(app): send a purchase left unsent as its buyer before the store changes user
- `209c070` fix(app): sell no Pro while the proxy cannot check subscriptions
- `7767c6a` fix(app): show a subscriber's Pro while Google Play renews it, never the offer
- `f61a3e5` fix(app): forget the last account's Pro on the Pro screen, and say plainly what failed
- `f2ee020` build(app): keep the advertising ID out, and RevenueCat's key out of dev builds
- `98c6528` docs: say whom RevenueCat's SDK sends an unsent purchase as, and what is left of that risk
- `769279e` docs(app): name each kind of purchase the store syncs before it changes user (comment
  only; `ktlintCheck` passes on it)
- this report

I built the fixes one commit at a time, so when each code commit was checked the working tree held only
that commit's changes:

- The first commit passed `ktlintCheck`, `RevenueCatOfferTest` and `ProTextsTest`.
- The next four passed `ktlintCheck` and all of `billing.*` and `ui.paywall.*`, with `AppModuleTest`,
  `ProGalleryTest` or `ui.settings.*` where they were touched.
- The build commit was checked by generating the dev `BuildConfig` and merging the manifests.
- The full checks ran on the final tree, whose code equals that build commit.

L2, L3 and L4 share one commit because they change the same two files.

## Commands run

Every command used `--init-script …/scratchpad/central-mirror.init.gradle --offline`, ran one at a
time, and none used `--dependency-verification=lenient`.

- After each step: `ktlintCheck`, and `:app:testProdDebugUnitTest` with `--tests` for `billing.*`,
  `ui.paywall.*`, plus `di.AppModuleTest`, `ui.ProGalleryTest` and `ui.settings.*` where they were
  touched. All passed.
- `:app:generateDevDebugBuildConfig :app:processDevDebugMainManifest :app:processProdReleaseMainManifest`:
  this checked the dev key and the merged manifests (above).
- Final tree:
  - `ktlintCheck`: passes;
  - `:app:testProdDebugUnitTest`: 433 pass;
  - `:app:testDevDebugUnitTest`: 433 pass;
  - `:core:domain:test --rerun :core:designsystem:testDebugUnitTest --rerun`: 198 and 3 pass;
  - `:app:lintProdDebug`, alone: passes with 0 errors;
  - `:app:assembleProdRelease`, alone: builds;
  - `:app:compileProdDebugAndroidTestKotlin`: compiles.
- `./gradlew --stop` at the end.

## Not verified

- **The real SDK.** `RevenueCatBilling` still has no test of its own, because it needs RevenueCat's
  servers and Google Play. This covers its one-line uses of `awaitCustomerInfo(policy)` and
  `awaitSyncPurchases()`, and the `VERIFIED_ON_DEVICE` reading. They were checked against the 11.3.0
  bytecode and sources.
- **How a sync treats a payment that is still pending.** `syncPurchases` posts tokens with an
  unspecified purchase state. I could not tell whether RevenueCat then marks such a token as sent on
  the phone. The docs describe that risk only in general terms.
- **A real purchase**, as before.

## Unresolved questions

1. Should the sync before a stop also run after an account deletion? It does now, as decided. Such a
   purchase then reaches RevenueCat under the purchaser the proxy has just forgotten, rather than
   under the next account to sign in. The doc records it.
2. A recurrence mode RevenueCat reports as `UNKNOWN`, which Google does not send today, is read like a
   recurring phase (count, 0 or none as one) rather than leaving the plan out. Say if such a plan
   should be left out instead.
