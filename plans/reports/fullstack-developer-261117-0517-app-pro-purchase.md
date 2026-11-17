# App: the Pro subscription through RevenueCat

Date: 2026-11-17 (UTC). Branch `feat/phase-08-pro-subscription`. Everything below is pushed. The
proxy side was built at the same time by another agent; this side was written against the shared
contract in the phase file, not against the proxy's code.

## Outcome

The app side of the contract is built, tested and pushed:

- RevenueCat 11.3.0 (with Google Play Billing Library 9.1.0), its public key from
  `splitvibe.revenueCatApiKey` as `BuildConfig.REVENUECAT_API_KEY`;
- the plan (`usage.plan`) and the purchaser read from the proxy, `POST /v1/plan`, and the purchaser
  kept with the session;
- the store behind an interface, configured only as the signed-in purchaser, never anonymously;
- `BillingRepository`: plans, the `pro` entitlement, purchase, switch, restore, each followed by
  `POST /v1/plan`;
- the Pro screen (`ui/paywall/`, `ProKey`), opened from Settings and from calls used up;
- the plan row in Settings, the deletion dialog's sentence about Pro, screenshots and docs;
- the two additions the coordinator sent from the proxy's review: an answer without `purchaser`, and
  a deletion the proxy limits with 429 `rate_limited`.

Every required check passes on the final tree, and each code commit passed the style check, the
device test compile and all app unit tests on its own tree, exported from git into the scratchpad
(the small fix `222783a` in the working tree, which then held nothing else of mine).

| Check | Result |
|---|---|
| `ktlintCheck` (root task) | Passes on the whole repository |
| `:app:testProdDebugUnitTest` | 405 tests pass, 63 of them new (342 before) |
| `:core:domain:test`, `:core:designsystem:testDebugUnitTest` | 198 and 3 pass |
| `:app:lintProdDebug`, in its own invocation | Passes; no warning in my files |
| `:app:assembleProdRelease`, in its own invocation | Builds. R8 takes RevenueCat's own rules from its AAR and reports no missing class, so the app needs no rules of its own |
| Screenshots | Recorded with `-Pscreenshots=record`, then verified by a normal run |
| `:app:compileProdDebugAndroidTestKotlin` | Compiles |
| Clean-machine checksums | `checksums.yml` run 44026139926 passed; its artifact equals the committed file byte for byte and only adds entries (below) |
| Device tests (`device-tests.yml`) | Run 44032739631 failed on an accessibility check, which `222783a` fixes (below); run 44034034141 on `222783a` passed both jobs |

## What the user sees

- **Settings** shows the plan beside the calls left, "Free plan" or "Pro plan", with a button
  "Get Pro" or "Manage Pro" that opens the Pro screen. The plan line appears once the proxy has
  counted the calls; the button is there whenever the build has the AI proxy. The account deletion
  dialog now says: "Split Vibe's server deletes what it keeps about your Google account: the count
  of your calls and your plan. Bills on this phone stay as they are. Deleting the account does not
  cancel Pro, so if you have it, cancel it in Google Play first."
- **The Pro screen, without Pro** (`47-pro.png`):
  - an accent card "300 AI calls a month": "Pro gives you 300 AI calls a month instead of 10, …"
    and "Everything else stays free: typing bills in by hand, groups, insights, exports and
    recurring bills. Split Vibe works without a subscription.";
  - the calls left and the day they come back;
  - the two plans as radio rows with Google Play's formatted price for their own period ("$3.99 a
    month", "$29.99 a year"; the yearly plan never shows a monthly figure), and the terms of an offer
    when there is one ("Free for 7 days, then $3.99 a month.", "$0.99 for the first month, then
    $3.99 a month.", "$1.99 a month for 3 months, then …", "$5.99 for the first 3 months, then …");
  - the renewal terms ("A plan renews automatically at the end of each month or year, at its price
    shown here, until you cancel it. You can cancel any time in Google Play, …"), "Manage
    subscriptions in Google Play" and "Restore purchases";
  - the main button "Subscribe for $3.99 a month", for the plan picked.
- **The Pro screen of a Pro member** (`48-pro-member.png`): "You have Pro", the plan and its price
  ("Monthly plan, $3.99 a month"), "Renews on 17 December 2026." or "Ends on …, as it is set not to
  renew.", a notice when RevenueCat reports a billing issue, a card to switch to the other plan with
  its price and "The new plan starts now, and Google Play charges its price from your next renewal.",
  and the manage link and restore.
- **Signed out**, the main action is "Sign in with Google" (`AccountRepository.signIn`), with "Sign in
  with Google to buy Pro, so that it stays with your account. Signing in alone gives you 10 AI calls a
  month, free." No plan is loaded and nothing goes to RevenueCat until someone signs in. A sign-in
  that ran out says so and offers the same.
- **A build that does not sell Pro** (no RevenueCat key, or no sign-in) shows "Pro is not available in
  this build of the app. Everything else works as it does." and the calls left.
- **Progress and errors**: "Loading the plans…", a load error with "Try again", "Pro cannot be bought
  right now" for an empty offering, and a status row in the bottom bar while signing in, waiting for
  Google Play or restoring. What an action came to shows in the bottom bar with "OK".
- **TalkBack**: each plan row is one radio button that reads its name, price and offer; the card
  titles are headings; messages and progress are polite live regions.
- **Calls used up** (`quota_exhausted`), on the first step of a split and in the assistant:

  | | Build that sells Pro | Build that does not |
  |---|---|---|
  | Trial (not signed in) | "You have used your 3 free calls. Sign in with Google for 10 calls a month, or get Pro for 300." + **Get Pro** | Today's text + **Open settings** |
  | Free (signed in) | "You have used this month's 10 calls. They come back on 1 December. Get Pro for 300 calls a month." + **Get Pro** | Today's text, no action |
  | Pro | "You have used this month's 300 calls. They come back on 1 December.", no action | (cannot happen) |

  The part-left variants keep their first sentence ("Reading a bill takes a whole call, and 0.4 of
  this month's 300 calls are left. …"). A sign-in that ran out keeps leading to Settings.

## How it works

- **Proxy client** (`ProxyApi`): `CallsLeft.plan` is `TRIAL`, `FREE` or `PRO`; without the field (or
  with a value this version does not know) it is `TRIAL` for `period: "trial"` and `FREE` otherwise.
  `purchaser` is read from `POST /v1/session`, `GET /v1/usage` and `POST /v1/plan`, and only when it
  is 64 lowercase hex characters. `refreshPlan()` posts `{}` to `/v1/plan` and is refused locally
  while nobody is signed in.
- **Purchaser** (`AccountStore`): saved with the session and encrypted the same way; replaced at the
  next sign-in (a sign-in without one leaves none); removed on sign-out, deletion and when the sign-in
  runs out (`expireSession`); read back at start only with a session that still holds.
  `onPurchaser(purchaser, session)` keeps a later one only while that session is still signed in,
  so an answer that arrives after signing out is dropped. `refresh()` and `refreshPlan()` use it.
- **Store** (`billing/`): `Billing` is the interface; `RevenueCatBilling` implements it; the tests
  use `FakeBilling`. `start(purchaser)` configures the SDK with that app user ID the first time and
  calls `awaitLogIn` when the user differs; when the log-in fails it closes the SDK, so nothing goes
  on as the person before. `stop()` calls `Purchases.sharedInstance.close()`, which exists in 11.3.0
  (checked with `javap` on the AAR that Gradle verified, and in the sources jar). `logOut` is never
  called. Plans come from `offerings.current.monthly/annual` with `product.price.formatted`,
  `product.period` and the phases of `product.defaultOption` before its last one; a plan whose period
  cannot be read is left out. The entitlement maps `isActive`, `willRenew`, `expirationDate`,
  `productIdentifier`, `productPlanIdentifier`, `billingIssueDetectedAt`, `store` and
  `managementURL`.
- **When the SDK runs**: `MainActivity.onCreate` launches `BillingRepository.follow()`, which maps the
  account to "purchaser while signed in" and starts or stops the store. So an install signed in with a
  known purchaser configures RevenueCat as soon as the app opens, and unacknowledged purchases are
  posted as that account. It is not in `Application.onCreate`, which every Robolectric test runs. A
  build that does not sell Pro never touches the SDK.
- **BillingRepository**: `sellsPro` is "has the key and can sign in". `offer()`, `buy()`,
  `switchTo()` and `restore()` first make sure the store runs as the current purchaser (one change of
  user at a time, under a mutex) and throw `SignInNeededException` without one. A switch passes
  `oldProductId` = the subscription id without `:basePlan`, and
  `StoreReplacementMode.WITHOUT_PRORATION` both ways. After a completed purchase, switch or restore it
  calls `POST /v1/plan` and returns the proxy's plan, or null when the proxy could not be asked, which
  leaves the purchase bought.
- **Pro screen** (`ProViewModel`): on opening, `POST /v1/plan` (a failure is shown and not fatal),
  then the offerings and the entitlement. A Pro member is one whose RevenueCat entitlement is active,
  or, before RevenueCat has answered, one the proxy counts as Pro. Errors: `PurchaseCancelledError`
  says nothing; `PaymentPendingError` "The payment is pending. Pro starts once Google Play confirms
  it." (as a notice, not an error); `ReceiptAlreadyInUseError` and `ProductAlreadyPurchasedError` "The
  subscription on this Google Play account belongs to another Split Vibe account. Sign in with that
  account to use it."; `NetworkError` "There is no connection. Check it and try again.";
  `StoreProblemError` and `PurchaseNotAllowedError` "Google Play has a problem right now. Try again
  later."; the proxy's `rate_limited`, `not_configured` and `subscription_unavailable` each a sentence
  of their own. `purchase` failures are caught as `PurchasesTransactionException` (with
  `userCancelled`), the others as `PurchasesException`.
- **Manage link**: `https://play.google.com/store/account/subscriptions?sku=<subscription>&package=${BuildConfig.APPLICATION_ID}`
  while the person has an active Pro subscription from Google Play (the subscription id comes from
  the entitlement, `pro` by the contract); otherwise RevenueCat's `managementURL` when it is https,
  or the general subscriptions page.
- **Answers without `purchaser`**: for a few minutes after a deploy the proxy can answer
  `POST /v1/session` and `GET /v1/usage` without it, and `POST /v1/plan` with a 500. A missing
  purchaser reads as missing, never as a bad response: the sign-in works, the next answer that has
  it brings it, and an answer without it keeps the one stored for the session. A failed check of the
  plan stays a message on the Pro screen, which still shows the plans.
- **A limited deletion**: `DELETE /v1/account` can answer 429 `rate_limited` with `Retry-After` (five
  deletions an hour), having deleted nothing. The session stays, and Settings says "That was tried
  too many times just now, so nothing was changed. Try again in about 40 minutes.", with the wait
  worded by `waitHint`, which the AI calls' limits and the Pro screen's `rate_limited` now share.
- **Out of calls**: `ProxyModelCall` takes `sellsPro` and writes the text; `proHelps(sellsPro)` (calls
  used up and the refusal's plan is not `PRO`) puts Get Pro on the error, and `signInHelps` no longer
  sends calls used up to Settings in a build that sells Pro. Both view models carry `errorOpensPro`
  beside `errorOpensSettings`.

## Files

New:

- `app/src/main/java/com/dantech/splitvibe/billing/Billing.kt`, `RevenueCatBilling.kt`, `BillingRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/paywall/ProScreen.kt`, `ProViewModel.kt`, `ProTexts.kt`
- Tests: `billing/FakeBilling.kt`, `billing/BillingRepositoryTest.kt`, `ui/paywall/ProViewModelTest.kt`,
  `ui/paywall/ProScreenTest.kt`, `ui/paywall/ProTextsTest.kt`, `ui/ProGalleryTest.kt`
- Screenshots: `47-pro`, `48-pro-member`, `49-settings-pro`, each with `-dark`

Changed:

- Build: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `gradle/verification-metadata.xml`
- Data: `data/account/AccountStore.kt` (`Plan`, `CallsLeft.plan`, the purchaser),
  `data/account/AccountRepository.kt`, `data/proxy/ProxyApi.kt`
- Wiring: `di/AppModule.kt`, `MainActivity.kt`, `ui/AppNavHost.kt` (`ProKey`)
- Out of calls: `llm/ProxyModelCall.kt`, `llm/GatewayBillAnalyzer.kt`, `assistant/SplitAssistant.kt`,
  `ui/split/SplitBillViewModel.kt`, `SplitAssistantViewModel.kt`, `NewSplitContent.kt`,
  `AssistantContent.kt`, `SplitScreen.kt`
- Settings: `ui/settings/SettingsScreen.kt` (plan row, `AccountActions.onOpenPro`, deletion text)
- Tests brought to the new signatures, and new cases in `AccountStoreTest`, `AccountRepositoryTest`,
  `ProxyApiTest`, `GatewayBillAnalyzerTest`, `SplitErrorsTest`, `SplitAssistantFlowTest`,
  `SettingsScreenTest`, `AppStartupTest`, `AppModuleTest`; two device tests (signatures only)
- Screenshots re-recorded because Settings gained the plan row: `11-settings`, `16-settings-signed-in`,
  `17-settings-sign-in-expired`, `32-settings-speech-service`, each with `-dark`. No other image changed.
- Docs: `README.md`, `docs/architecture.md`

`core/domain` needed no change: the plan reaches `proHelps` through the `ProxyException` that
`ProxyModelCall` already sets as the cause of `BillAnalyzerException`.

## Tests

63 new tests, all passing:

| Class | New | What they show |
|---|---|---|
| `BillingRepositoryTest` | 10 | The store runs only as the current purchaser and stops without one (checked inside the stand-in on every start); nothing is bought, switched or restored signed out or without a purchaser; a purchase posts `{}` to `/v1/plan` with the session and keeps 300 calls on Pro; a purchase stays bought when the proxy cannot be asked; a switch uses `WITHOUT_PRORATION` and `pro` both ways, also from `pro:annual`; restore, and a restore refused for another account asks the proxy nothing; a refused session expires the sign-in and drops the purchaser; a check answered without the purchaser keeps the stored one; a late answer after signing out is dropped; no key or no sign-in sells no Pro |
| `ProViewModelTest` | 12 | Signed out: nothing to RevenueCat, buying impossible, then signing in loads the plans; a purchase leads to `POST /v1/plan` and 300 calls on Pro; cancelling changes nothing and says nothing; pending; receipt in use and already purchased; restore and restore on another account; nothing to restore; a member's switch; the three proxy refusals and a 500 are not fatal and keep the last calls, the 429 saying when to try again; no purchaser and no proxy; a build without billing; the store's error texts |
| `ProScreenTest` | 7 | Sign-in as the main action and the 10-calls sentence; plans as radio buttons read with their prices; offer terms read with the plan; the member's plan, renewal, billing issue and switch; loading and try again; the message and OK; not sold |
| `ProTextsTest` | 7 | Prices per period, the yearly one a year's; no offer; free trials; intro prices (one period, several, paid up front); a trial then an intro price; renewal and end dates; the manage link and its fallbacks |
| `ProGalleryTest` | 6 | `47-pro`, `48-pro-member`, `49-settings-pro`, light and dark |
| `AccountStoreTest` | +3 | The purchaser is kept, read back, replaced at another sign-in; gone on sign-out, deletion and a sign-in that runs out; kept only for the session it answered |
| `AccountRepositoryTest` | +4 | Sign-in keeps it, sign-out and deletion forget it; `GET /v1/usage` brings it for a session, never for an install; a sign-in answered without it works and the next count brings it, and a count without it keeps it; a deletion limited with 429 deletes nothing and keeps the sign-in |
| `ProxyApiTest` | +4 | `plan` parsing including an answer without it and unknown values; the purchaser's shape; `POST /v1/plan` request and answer; it needs a sign-in and keeps 429, 503 and 502 as they are |
| `GatewayBillAnalyzerTest` | +2 | The quota texts and actions for trial, free and Pro with billing, and without |
| `SplitErrorsTest` | +2 | Where the error leads for trial, free and Pro with billing; Get Pro on the first step and in the assistant |
| `SplitAssistantFlowTest` | +1 | The assistant in a build that sells Pro, for the trial and for a Pro member |
| `SettingsScreenTest` | +3 | The plan row and its button; no plan line and no empty text before the count, no row without AI; the deletion text |
| `SettingsViewModelTest` | +1 | A deletion limited with 429 keeps the account signed in and says when to try again |
| `AppStartupTest` | +1 | Settings opens the Pro screen, and back returns |

`AppModuleTest` also resolves `BillingRepository` and `ProViewModel`, and `AccountMessagesTest` has
two more cases for `rate_limited`, with and without `Retry-After`.

## Commands run

All with `--init-script …/scratchpad/central-mirror.init.gradle`, and `--offline` except the one
fetch.

- Fetch, once, not offline: `--write-verification-metadata sha256 :app:compileProdDebugUnitTestKotlin
  :app:compileProdDebugAndroidTestKotlin :app:assembleProdDebug :app:assembleProdRelease`. It passed
  and added 103 lines to `gradle/verification-metadata.xml`, removing none.
- `ktlintCheck`: passes (also on each exported commit).
- `:app:testProdDebugUnitTest`: 405 pass on the final tree. The first run in verify mode failed only
  the 10 screenshot tests expected (the six new images, and the Settings images now showing the plan
  row); then `-Pscreenshots=record`; then a normal run passed with every image.
- `:core:domain:test :core:designsystem:testDebugUnitTest`: pass.
- `:app:lintProdDebug` alone: passes; its warnings are all older ones (versions, target API, backup
  rules). Run again on the final tree.
- `:app:assembleProdRelease` alone: builds; `configuration.txt` holds the rules of
  `purchases-11.3.0/proguard.txt`, and there is no `missing_rules.txt`. Run again on the final tree.
- `:app:compileProdDebugAndroidTestKotlin`: compiles, on the final tree too.
- Each code commit after the build commit, but `222783a`: `git archive HEAD` into the scratchpad
  with that commit's files laid over it, `ANDROID_HOME=/opt/android-sdk`, then `ktlintCheck
  :app:compileProdDebugAndroidTestKotlin :app:testProdDebugUnitTest`: all passed before I committed.
  `222783a` passed `ktlintCheck :app:testProdDebugUnitTest` in the working tree.
- `./gradlew --stop` at the end.

### Checksums from the clean machine

Pushing `01415a1` started `checksums.yml` (run 44026139926), which passed. I downloaded its
`verification-metadata` artifact with `gh run download` into an empty directory. It is byte for byte
the file committed in `01415a1` (`cmp` finds no difference; SHA-256
`13ad088dc072222fc339bb7299f9b45383b590f6176aa813d895689471ed7463` for both). Against the file before
that commit it adds 103 lines and removes none, for 14 components:

- `com.revenuecat.purchases:purchases` 11.3.0 and `com.android.billingclient:billing` 9.1.0;
- what they bring: `kotlin-parcelize-runtime` 2.2.21, `poko-annotations` and `poko-annotations-jvm`
  0.20.2, `tink-android` 1.10.0, `play-services-ads-identifier` 17.0.1, `play-services-location`
  19.0.0, `play-services-places-placereport` 17.0.0, `transport-api` 3.0.0, `transport-backend-cct`
  and `transport-runtime` 3.1.8;
- the metadata of `lifecycle-process` 2.5.0 and 2.6.2 (version alignment).

There is no separate checksum commit, because the clean machine's list equals the committed one.

## Commits

- `01415a1` build(app): add RevenueCat's SDK and the setting for its public key
- `8e292cc` feat(app): keep the plan and the purchaser the proxy reports
- `f9d4ce7` feat(app): sell Pro through RevenueCat, only ever as the signed-in purchaser
- `86fa51b` feat(app): add the Pro screen, opened from the plan row in Settings
- `77df2c2` feat(app): invite to Pro when the calls run out, in a build that sells it
- `6e70943` docs: describe Pro, its RevenueCat key, and how the plan reaches the app
- `222783a` fix(app): leave no empty text in the plan row before the calls are counted
- `298a0d5` test(app): check that answers without the purchaser keep the one stored
- `8fa8ccd` feat(app): say when to try again after the proxy limits a deletion
- `42307f0` docs: say that an answer without the purchaser keeps the one stored
- this report

`f9d4ce7` and `86fa51b` hold earlier versions of `AppModule.kt`, `AppModuleTest.kt`, `AppNavHost.kt`
and `ScreenGalleryTest.kt`, with only the wiring their step needs, so each commit stands on its own;
`77df2c2` brings them to their final form. `298a0d5` holds the tests of `ProViewModelTest` and
`AccountRepositoryTest` without what `8fa8ccd` adds. The other agent's proxy commits interleave with
mine.

### The device tests

The push of `6e70943` started `device-tests.yml` (run 44032739631), which failed in
`AppLaunchTest`: the accessibility checks found an item "that may not have a label readable by
screen readers" at the place of the plan row in Settings. With no proxy on the emulator the calls
are never counted, and the row drew an empty text beside Get Pro. `222783a` draws a space there
instead, and `SettingsScreenTest` now fails on any empty text in that state (it failed on the code
before the fix, and passes after). The Robolectric tests do not run these checks, which is why only
the emulator saw it. As that push changed no path the workflow follows, I started it by hand on the
branch (run 44034034141, on `222783a`), and both jobs passed: the emulator tests and the baseline
profile.

## Where this differs from the contract, or adds to it

- **The key is checked.** A `splitvibe.revenueCatApiKey` that is not RevenueCat's public Google key
  (`goog_` and letters, digits, `_` or `-`) stops the build, like the other settings, and the message
  does not print the value. This keeps the secret `sk_` key out of the app.
- **Settings shows Get Pro in a build without the key too**, as that is the only way to the Pro
  screen's "not available" state the contract asks for. Without the AI proxy there is no plan row.
- **The deletion sentence shows in every build**, as deleting never cancels a Google Play
  subscription, whichever build deletes.
- **A failed log-in closes the SDK** rather than leaving it as the account before. The next start
  configures it afresh with the new purchaser.
- **`ProductAlreadyPurchasedError`** says the subscription belongs to another account, as the
  contract asks. It could also come for this same account if RevenueCat's customer info failed to
  load and the person tapped Subscribe again.
- **A deletion refused with 502 `subscription_unavailable`** says "Split Vibe's server had a problem.
  Try again in a moment.", as other server failures of account actions do.
- **Unacknowledged purchases** are retried only while someone is signed in, as the research says
  (section 1.3): if nobody signs in within three days, Google refunds such a purchase. That follows
  from never using an anonymous id.
- **`not_configured` from `POST /v1/plan`** shows "Split Vibe's server cannot check subscriptions yet."
  and buying stays possible, as the contract makes the refresh not fatal. Such a purchase counts once
  the proxy has its RevenueCat secret and the screen asks again.
- **Plans** whose period cannot be read are left out, as their terms could not be shown.

## Not verified

- **A real purchase.** There is no Play Console subscription or RevenueCat project yet, so buying,
  switching and restoring have only run against the stand-in. `RevenueCatBilling`, the thin layer
  over the SDK, has no test of its own: the SDK talks to RevenueCat's servers and Google Play, which
  local tests do not have. The README lists this under what has not been verified.
- **The dev flavor** with a key would configure RevenueCat for `com.dantech.splitvibe.dev`, for which
  Google Play sells nothing, so its Pro screen would say "Pro cannot be bought right now". The README
  tells testers to use `prod`.
- **The release build at runtime**, as before.

## Notes

- The merged manifest gains one permission, `com.android.vending.BILLING`. RevenueCat also declares
  `INTERNET` and `ACCESS_NETWORK_STATE`, which the app already had through WorkManager and Firebase;
  `play-services-ads-identifier` 17.0.1 and `play-services-location` 19.0.0 declare none, so there is
  no `AD_ID` or location permission. RevenueCat adds two activities and an `androidx.startup`
  initializer.
- One test failed once, in the run that recorded the images: `SplitErrorsTest` got
  `UncaughtExceptionsBeforeTest` from a Room query (`PersonDao.recent`) that ran after an earlier
  test closed its database, the same kind of leak an earlier report saw. Most likely it came from my
  new test in `SplitAssistantFlowTest`, which ran just before and made a second split view model; it
  now reuses the one it has, and the six full runs since all passed.
- I did not update the phase file, which is not among my files.

## Open questions

1. Should the Pro screen stop offering a purchase while the proxy answers `not_configured`, so nobody
   pays before the server can count Pro? It now only says so.
2. Should a `dev` build leave out the RevenueCat key, so it never calls RevenueCat for a package
   Google Play does not sell?
3. Phase 9: the Data safety form has to list RevenueCat. By its own KDoc the SDK reads the
   advertising ID only once an attribution network's id is set, which the app never does.
