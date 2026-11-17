# RevenueCat + Google Play subscription: verified facts

Researched 2026-11-17 (UTC) for one auto-renewing subscription: entitlement `pro`, a monthly and a yearly base plan, a Kotlin/Compose app, and a Cloudflare Worker that decides entitlements itself.

Labels: **[O]** official source read, **[S]** secondary source, **[U]** could not verify.

Method: I read RevenueCat's docs in their Markdown form (append `.md` to a docs URL). GitHub API access was not enabled for this session (HTTP 403), so the SDK facts come from the 11.3.0 artifacts on Maven Central: POM, AAR (checked with `javap`), and `-sources.jar`. The CHANGELOG came from raw.githubusercontent.com.

## Summary

- **SDK.** Use `com.revenuecat.purchases:purchases:11.3.0`. It pulls in Play Billing Library 9.1.0, which meets Google's "Billing Library 8 or later" rule; PBL 9 remains accepted until 2028-08-31. The repo already meets the SDK's build requirements: Kotlin 2.3.21, compileSdk 37 and minSdk 26.
- **Server reads.** After every webhook, call `GET https://api.revenuecat.com/v1/subscribers/{app_user_id}` and read `subscriber.entitlements.pro`. This endpoint is current and not deprecated, and it is what RevenueCat's webhook guide prescribes. Its one hazard is that it creates the customer when none exists.
- **Webhooks.**
  - Body shape: `{api_version, event}`.
  - Authentication: the configured value arrives in `Authorization`. HMAC signing is optional and uses `X-RevenueCat-Webhook-Signature`.
  - Success and retries: only HTTP 200 counts as success. RevenueCat retries 5 times over about 80 minutes, and disconnects after 60 s.
  - Delivery: at least once, and order is not guaranteed.
- **Restore setting.** Choose **Keep with original App User ID**. When a different app user ID restores or syncs the purchase, the SDK returns the error `ReceiptAlreadyInUseError`, not a CustomerInfo without the entitlement.
- **Plan switch.** Monthly and yearly are two base plans of one subscription, so Google accepts only `CHARGE_FULL_PRICE` or `WITHOUT_PRORATION`. Pass `oldProductId` as the subscription ID only.

## 1. Android SDK

### 1.1 Versions and requirements

| Fact | Label | Source |
|---|---|---|
| The latest stable `com.revenuecat.purchases:purchases` is **11.3.0** (`<release>11.3.0</release>`, metadata updated 2026-11-13). | [O] | [maven-metadata.xml](https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases/maven-metadata.xml) |
| Its POM depends on `com.android.billingclient:billing` **9.1.0** (compile scope) and `kotlin-stdlib` 2.2.21. | [O] | [POM](https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases/11.3.0/purchases-11.3.0.pom) |
| 9.1.0 is the newest `billing` version on Google Maven. | [O] | [group-index.xml](https://dl.google.com/android/maven2/com/android/billingclient/group-index.xml) |
| "By Aug 31, 2026, all new apps and updates to existing apps must use Billing Library version 8 or later", with an extension possible until Nov 1, 2026. PBL 9's deadline is Aug 31, 2028. | [O] | [deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq) (updated 2026-11-13) |
| The AAR manifest has `minSdkVersion="23"`. The docs require "Kotlin 2.1.0 or newer, a `compileSdk` of 35 or higher, and a `minSdk` of 23 or higher". | [O] | [AAR](https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases/11.3.0/purchases-11.3.0.aar), [install](https://www.revenuecat.com/docs/getting-started/installation/android) |
| v11 exposes PBL 9.1.0 as an `api` dependency. If the app also declares `billing` itself, that version must be 9.0.0 or later. | [O] | [v11 migration](https://www.revenuecat.com/docs/sdk-guides/android-native-10x-to-11x-migration) |
| The Activity that starts purchases must use `launchMode` `standard` or `singleTop`. | [O] | [install](https://www.revenuecat.com/docs/getting-started/installation/android) |

### 1.2 Exact API names in 11.3.0

All names are in package `com.revenuecat.purchases` unless noted. They were read with `javap` from the AAR and checked in the [sources jar](https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases/11.3.0/purchases-11.3.0-sources.jar) **[O]**.

| Need | API |
|---|---|
| Configure with an ID | `Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).appUserID(id).build())`; also `Purchases.isConfigured` and `Purchases.sharedInstance` |
| Log in | `logIn(appUserID, LogInCallback?)`, whose callback is `onReceived(customerInfo, created)`; or `awaitLogIn(appUserID): LogInResult` (class `com.revenuecat.purchases.data.LogInResult`, fields `customerInfo`, `created`) |
| Log out | `logOut(ReceiveCustomerInfoCallback?)`; `awaitLogOut(): CustomerInfo` |
| Coroutine helpers | `awaitOfferings(): Offerings`; `awaitPurchase(PurchaseParams): PurchaseResult` (`storeTransaction`, `customerInfo`); `awaitRestore(): CustomerInfo`; `awaitCustomerInfo(fetchPolicy = CacheFetchPolicy.CACHED_OR_FETCHED)`; `awaitSyncPurchases(): CustomerInfo`. Variants returning `Result<T>`: `awaitOfferingsResult`, `awaitPurchaseResult`, `awaitRestoreResult` |
| Buy | `PurchaseParams.Builder(activity, pkg).build()`. The builder also accepts a `StoreProduct` or a `SubscriptionOption`. |
| Change plan | `.oldProductId(subscriptionId)` plus `.replacementMode(models.StoreReplacementMode.X)`, where X is `WITHOUT_PRORATION` (the default), `WITH_TIME_PRORATION`, `CHARGE_FULL_PRICE`, `CHARGE_PRORATED_PRICE` or `DEFERRED` |
| Price and period | `pkg.product.price` (`formatted`, `amountMicros`, `currencyCode`); `pkg.product.period` (`value`, `unit` = DAY/WEEK/MONTH/YEAR/UNKNOWN, `iso8601`); `offering.monthly`, `offering.annual`; `PackageType.MONTHLY` = `$rc_monthly`, `PackageType.ANNUAL` = `$rc_annual` |
| Trial and intro terms | `product.defaultOption`; `product.subscriptionOptions` (`basePlan`, `freeTrial`, `introOffer`, `defaultOffer`, `withTag(tag)`); `SubscriptionOption.pricingPhases`, `freePhase`, `introPhase`, `fullPricePhase`, `billingPeriod`, `isBasePlan`; `PricingPhase.billingPeriod`, `price`, `billingCycleCount`, `recurrenceMode`, `offerPaymentMode` (`FREE_TRIAL`, `SINGLE_PAYMENT`, `DISCOUNTED_RECURRING_PAYMENT`) |
| Manage subscription | `CustomerInfo.managementURL: Uri?`; `Purchases.showManageSubscriptions(context)` |
| Entitlement | `customerInfo.entitlements["pro"]` or `.entitlements.active`. `EntitlementInfo` has `isActive`, `willRenew`, `expirationDate: Date?`, `periodType` (`NORMAL`, `INTRO`, `TRIAL`, `PREPAID`), `billingIssueDetectedAt: Date?`, `unsubscribeDetectedAt: Date?`, and also `productIdentifier`, `productPlanIdentifier`, `isSandbox`, `store` |

How these APIs behave:

- **`logOut` on an anonymous user.** It does not throw synchronously. It completes with `PurchasesErrorCode.LogOutWithAnonymousUserError` (code 22, "Called logOut but the current user is anonymous.") [O] (sources, `identity/IdentityManager.kt`).
- **Avoiding anonymous IDs.** `logOut` itself creates a new anonymous ID. For apps that only use their own IDs, RevenueCat says to configure the SDK with the ID, never call `logOut`, and switch accounts with `logIn` [O] ([identifying customers](https://www.revenuecat.com/docs/customers/identifying-customers)).
- **Exception types.** `awaitPurchase` throws `PurchasesTransactionException`, which carries `code` and `userCancelled`. `awaitLogIn`, `awaitLogOut` and `awaitRestore` declare `@Throws(PurchasesTransactionException::class)`, but they actually throw its parent class `PurchasesException`. Catch `PurchasesException` [O] (sources, `coroutinesExtensions.kt` and `CoroutinesExtensionsCommon.kt`).
- **Replacement-mode deprecation.** `googleReplacementMode(GoogleReplacementMode)` is marked `@Deprecated("Use .replacementMode() instead")`, and the class `GoogleReplacementMode` is marked `@Deprecated("Use StoreReplacementMode instead")`. `StoreReplacementMode` was added in 10.3.0 [O] (AAR; [CHANGELOG](https://raw.githubusercontent.com/RevenueCat/purchases-android/main/CHANGELOG.md)).
- **`oldProductId` format.** The KDoc says "We expect the subscriptionId only. If a string including `:` is passed in … anything after the `:` will be ignored", and the SDK logs a warning. RevenueCat's docs example passes `"oldProductId:oldBasePlanId"`, which works but contradicts the KDoc [O] (sources, `PurchaseParams.kt`; [managing subscriptions](https://www.revenuecat.com/docs/subscription-guidance/managing-subscriptions)).
- **Google's rule for switching base plans of one subscription.** "When switching plans within the same subscription to an auto-renewing plan … valid proration modes are CHARGE_FULL_PRICE and WITHOUT_PRORATION"; any other mode makes the purchase fail. The Play Console default for each subscription is "Charge immediately" (equivalent to CHARGE_FULL_PRICE) or "Charge at the next billing date" (equivalent to WITHOUT_PRORATION) [O] ([subscriptions](https://developer.android.com/google/play/billing/subscriptions), updated 2026-10-23).
- **`DEFERRED` needs RTDN.** RevenueCat says `DEFERRED` requires Google server notifications [O] ([managing subscriptions](https://www.revenuecat.com/docs/subscription-guidance/managing-subscriptions)).
- **What `StoreProduct.price` means.** For a Google subscription it is the base-plan price. Offer pricing is in the pricing phases [O] (sources KDoc).
- **`freePhase` and `introPhase`.** Both skip the last, full-price phase, so a plain base plan returns null for both [O] (sources, `SubscriptionOption.kt`).
- **Which offer a purchase uses.** Buying a `Package` or `StoreProduct` applies `defaultOption`: the longest eligible free trial, otherwise the cheapest intro offer, otherwise the base plan. Tagging an offer `rc-ignore-offer` excludes it [O] ([offers](https://www.revenuecat.com/docs/subscription-guidance/subscription-offers)).
- **Offer eligibility.** Offers whose eligibility Google decides appear in `subscriptionOptions` only for eligible users. Offers marked "developer determined" always appear [O] ([Google Play offers](https://www.revenuecat.com/docs/subscription-guidance/subscription-offers/google-play-offers)).
- **`managementURL`.** It points to Play when the customer has an active Play subscription, and is null when there is no active subscription [O] ([customer info](https://www.revenuecat.com/docs/customers/customer-info)). `showManageSubscriptions` falls back to `https://play.google.com/store/account/subscriptions` [O] (sources, `common/Constants.kt`).
- **Error codes.** Sources: [O] sources (`PurchasesErrorCode.kt`, `google/errors.kt`, `PostReceiptHelper.kt`) and the [errors page](https://www.revenuecat.com/docs/test-and-launch/errors).

  | Case | `PurchasesErrorCode` | Code | Notes |
  |---|---|---|---|
  | User cancelled | `PurchaseCancelledError` | 1 | From Play's `USER_CANCELED` |
  | Payment pending | `PaymentPendingError` | 20 | A Play purchase in `PENDING` state; the SDK does not acknowledge it |
  | Already purchased | `ProductAlreadyPurchasedError` | 6 | From Play's `ITEM_ALREADY_OWNED`, which is the name the docs list |
  | Receipt used by another subscriber | `ReceiptAlreadyInUseError` | 7 | |
  | Network | `NetworkError` | 10 | |
  | Store problem | `StoreProblemError` | 2 | From Play's `ERROR`, `SERVICE_UNAVAILABLE`, `SERVICE_DISCONNECTED`, `SERVICE_TIMEOUT` |

- **Blocked Play Store.** Since v11 it reports `PurchaseNotAllowedError` [O] ([v11 migration](https://www.revenuecat.com/docs/sdk-guides/android-native-10x-to-11x-migration)).

### 1.3 Where to configure, and what happens to unacknowledged purchases

- **Configure once, early.** RevenueCat: "You should only configure *Purchases* once, usually early in your application lifecycle". Its Android sample configures in `Application.onCreate` [O] ([configuring](https://www.revenuecat.com/docs/getting-started/configuring-sdk)).
- **Know the ID first.** Apps that never want anonymous IDs must know the ID before they configure the SDK [O] ([identifying customers](https://www.revenuecat.com/docs/customers/identifying-customers)).
- **No offerings fetch at startup.** "Don't call `getOfferings` in your Android app's `Application.onCreate`" [O] ([displaying products](https://www.revenuecat.com/docs/getting-started/displaying-products)).
- **A second `configure` replaces the first.** Calling `configure` again with a different configuration replaces the singleton, and only an info log records it [O] (sources, `Purchases.kt`).
- **Google's refund rule.** Google refunds and revokes a purchase that is not acknowledged "within three days" [O] ([integrate](https://developer.android.com/google/play/billing/integrate)). For license-tester purchases the window is 3 minutes [O] ([test](https://developer.android.com/google/play/billing/test)).
- **When the SDK acknowledges.** The SDK acknowledges purchases automatically [O] ([making purchases](https://www.revenuecat.com/docs/getting-started/making-purchases)), but only once RevenueCat has accepted the token or rejected it with a non-5xx error [O] (sources, `PostReceiptHelper.kt`, `common/Backend.kt`).
- **When it retries.** After a 5xx error or a network failure, the purchase stays unacknowledged. The SDK retries when billing connects, when the app comes to the foreground, and when it fetches CustomerInfo. Each retry posts the purchase under the *current* app user ID [O] (sources, `PostPendingTransactionsHelper.kt`, `PurchasesOrchestrator.kt`).
- **What that means for this app (my inference from the points above).** If the SDK is configured only while someone is signed in, a failed post is retried only during signed-in sessions.
  - If nobody signs in within 3 days, Google refunds the purchase.
  - If a different user signs in first, the purchase is posted under that user.
- **Hashed account ID.** The SDK sets Play's `obfuscatedAccountId` to the SHA-256 hash of the app user ID [O] (sources, `google/BillingWrapper.kt`; [RTDN page](https://www.revenuecat.com/docs/platform-resources/server-notifications/google-server-notifications)). I found nothing saying RevenueCat uses this hash to reject a post from a different user [U].

## 2. REST API

### 2.1 v1 `GET /v1/subscribers/{app_user_id}`

- **Status: current.** The reference page shows no deprecation marker on this endpoint; only `POST …/attribution` is marked deprecated [O] ([v1 customers](https://www.revenuecat.com/docs/api-v1/customers)). A search of the full docs dump finds no deprecation or sunset notice for v1 [O] ([llms-full.txt](https://www.revenuecat.com/docs/llms-full.txt)). A RevenueCat staff member wrote on 2024-05-31 that v1 "will continue to be available" and "we have no plans to discontinue or deprecate it at this time!" [S] ([community](https://community.revenuecat.com/sdks-51/v1-vs-v2-customer-subscriptions-4614)).
- **Auth header.** `Authorization: Bearer <key>` [O] ([v1](https://www.revenuecat.com/docs/api-v1)). v1 also accepts the bare key without `Bearer` [O] ([v2](https://www.revenuecat.com/docs/api-v2)). Secret keys start with `sk_`, are project-wide, and "can be used to make any API request", including granting entitlements and deleting subscribers [O] ([API keys](https://www.revenuecat.com/docs/projects/authentication)).
- **It creates missing customers.** The endpoint "Gets the latest Customer Info … or creates a new customer if it doesn't exist": HTTP 200 means found, 201 means created. Do not send `X-Platform` when reading for information or with a secret key [O] ([v1 customers](https://www.revenuecat.com/docs/api-v1/customers)).
- **Fields** [O] ([schema](https://www.revenuecat.com/docs/redocusaurus/openapi-v1-customer-info-model.yaml)):
  - `request_date_ms`: the request time in milliseconds since epoch.
  - `subscriber.entitlements.<identifier>`: includes expired entitlements. If several purchases grant the entitlement, the one with the latest expiry is shown. Its fields:
    - `expires_date`: ISO 8601, may be in the past; `null` means lifetime.
    - `grace_period_expires_date`: `null` if the customer was never in a grace period.
    - `product_identifier`: the product that grants the entitlement; it "might temporarily be unavailable" when store validation has problems.
    - `product_plan_identifier`: the Google Play base plan ID.
    - `purchase_date`: the last purchase or renewal.
  - `subscriber.subscriptions.<product_identifier>`: keyed by the Play subscription ID *without* the base plan, which is returned separately in `product_plan_identifier` ([event fields](https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields)). Its fields:
    - `expires_date`, `grace_period_expires_date`.
    - `purchase_date`: when the current period started.
    - `unsubscribe_detected_at`: when auto-renew was turned off; the subscription "may still be active".
    - `billing_issues_detected_at`: reset to `null` when the billing issue is resolved.
    - `refunded_at`, `is_sandbox`.
    - `period_type`: enum `normal`, `trial`, `intro`.
    - `store`: for example `play_store` or `promotional`.
    - Also `store_transaction_id`, `auto_resume_date` (Play only), `ownership_type`, `price`.
- **Period-type spellings differ.** The webhook `period_type` adds `PROMOTIONAL` and `PREPAID` in upper case, and the SDK's `PeriodType` has `PREPAID`. Parse case-insensitively and accept unknown values [O] (sources above).
- **Grace periods.** To detect one, "watch for the value of `grace_period_expires_date` … and compare it to the current date" [O] ([grace periods](https://www.revenuecat.com/docs/subscription-guidance/how-grace-periods-work)).
- **Play renewal gap.** Entitlements can be empty for minutes or hours after the period ends, before Google applies grace or an extension. RevenueCat suggests optionally treating the customer as "renewal pending" when the period ended recently (for example, less than six hours ago) and auto-renew is still on [O] ([Play renewal extensions](https://www.revenuecat.com/docs/customers/google-play-renewal-extensions)).

### 2.2 v1 `DELETE /v1/subscribers/{app_user_id}`

- **What it deletes.** "Permanently deletes a customer. Deletion is queued asynchronously." This removes all of the customer's data, including sandbox and production purchase history, and is "sufficient for GDPR erasure requests" [O] ([v1 customers](https://www.revenuecat.com/docs/api-v1/customers), [customer profile](https://www.revenuecat.com/docs/dashboard-and-metrics/customer-profile#delete-customer)).
- **How to confirm it.** Call DELETE again and treat 404 as confirmation. Do not use GET to confirm, because GET re-creates the customer [O] ([customer profile](https://www.revenuecat.com/docs/dashboard-and-metrics/customer-profile#delete-customer)).
- **The Play subscription survives.** Deleting a customer "won't cancel mobile … subscriptions", and the user "can still trigger the Restore Purchases method to re-sync" [O] ([customer profile](https://www.revenuecat.com/docs/dashboard-and-metrics/customer-profile#delete-customer), [managing subscriptions](https://www.revenuecat.com/docs/subscription-guidance/managing-subscriptions)).
- **Stopping billing before deletion.** Two v1 endpoints can do this first [O] ([v1 transactions](https://www.revenuecat.com/docs/api-v1/transactions)):
  - `POST /v1/subscribers/{id}/subscriptions/{store_transaction_identifier}/cancel`: the subscription "remains valid until its expiration time, but it will not renew".
  - `POST /v1/subscribers/{id}/subscriptions/{product_identifier}/revoke`: refunds the last purchase and revokes access immediately.

### 2.3 API v2

Base URL `https://api.revenuecat.com/v2`. The header `Authorization: Bearer sk_…` is required. "API v1 keys will not work with REST API v2", and v2 keys are created with explicit permissions. The Customer Information domain allows 480 requests per minute [O] ([v2](https://www.revenuecat.com/docs/api-v2)).

| Endpoint | Permission | Notes | Source |
|---|---|---|---|
| `GET /v2/projects/{project_id}/customers/{customer_id}` | `customer_information:customers:read` | Returns 404 when the customer is missing, so it creates nothing. The response embeds `active_entitlements`. | [O] [customer](https://www.revenuecat.com/docs/api-v2/customer) |
| `GET …/customers/{customer_id}/active_entitlements` | `customer_information:customers:read` | Items look like `{object:"customer.active_entitlement", entitlement_id:"entla1b2c3d4e5", expires_at}`, so they are keyed by the **internal entitlement ID, not the lookup key `pro`**. | [O] [customer resources](https://www.revenuecat.com/docs/api-v2/customer/resources) |
| `GET /v2/projects/{project_id}/entitlements` | `project_configuration:entitlements:read` (60 requests per minute) | Maps `id` to `lookup_key`. | [O] [entitlement](https://www.revenuecat.com/docs/api-v2/entitlement) |
| `GET …/customers/{customer_id}/subscriptions` | `customer_information:subscriptions:read` | Returns `gives_access`, `status`, `auto_renewal_status`, `current_period_ends_at`. | [O] [customer resources](https://www.revenuecat.com/docs/api-v2/customer/resources) |
| `DELETE /v2/projects/{project_id}/customers/{customer_id}` | `customer_information:customers:read_write` | Asynchronous; confirm with GET returning 404. | [O] [customer](https://www.revenuecat.com/docs/api-v2/customer) |

**Which version RevenueCat recommends in 2026: no explicit statement found [U].** The signals in the docs point both ways:

- The API-keys page and the customer-info page link "the REST API" to v2 [O].
- The webhook guide says to call v1 `GET /subscribers` "after receiving any webhook" [O] ([webhooks](https://www.revenuecat.com/docs/integrations/webhooks)).

### 2.4 Recommendation for this project

Use v1 `GET /v1/subscribers/{app_user_id}` for every entitlement decision. Use v1 DELETE for account deletion with the same key.

| Dimension | v1 `GET /subscribers` (ranked 1) | v2 customer + active entitlements (ranked 2) |
|---|---|---|
| Finding `pro` | By identifier: `subscriber.entitlements.pro` | By internal `entitlement_id`, which must be mapped once |
| Grace period | Explicit `grace_period_expires_date` | Not documented whether `expires_at` includes grace [U] |
| Flags for the gap rule (unsubscribe, billing issue, refund, sandbox) | In the same response | Needs a second call and a second permission |
| Unknown customer | Created (201) | 404 |
| Damage if the key leaks | The key can do anything, including refunds and grants | Can be limited to read-only |
| RevenueCat's webhook-sync guidance | Named explicitly | Not mentioned |

Reasons for v1:

1. It is the call RevenueCat's own webhook guide prescribes.
2. One response contains `pro` by name, with expiry, grace and the flags needed for the renewal-gap rule.
3. There is no project ID or internal-ID mapping to maintain.

The cost is the unscoped key and the create-on-GET behaviour. Section "Implications for the build" covers how to contain both. Pick v2 instead only if limiting the damage of a leaked key outweighs the extra calls and the mapping.

## 3. Webhooks

- **Body.** `{"api_version": "1.0", "event": {…}}` [O] ([event types and fields](https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields)).
- **Fields** [O] (same page):
  - `id`: unique per event; retries reuse it.
  - `type`.
  - `app_user_id`: the subscriber's last seen app user ID.
  - `original_app_user_id`: the first app user ID used.
  - `aliases`: every app user ID ever used. RevenueCat says to search both `original_app_user_id` and `aliases` when looking up users.
  - `transferred_from`, `transferred_to`: string arrays, sent only on `TRANSFER`.
  - `event_timestamp_ms`: when the event was generated, which "doesn't necessarily coincide with" the action; retries reuse it.
  - `environment`: `SANDBOX` or `PRODUCTION`.
  - `entitlement_ids`: an array, or null when the product maps to no entitlement. `entitlement_id` is deprecated.
  - `expiration_at_ms`: null for non-subscription purchases and lifetime products.
  - `store`: for example `PLAY_STORE`; included only sometimes.
  - `period_type`: `TRIAL`, `INTRO`, `NORMAL`, `PROMOTIONAL` or `PREPAID`.
  - `product_id`: for Play products set up after February 2023, the format is `<subscription_id>:<base_plan_id>`.
- **Events without `app_user_id`.** `TRANSFER` carries only the common fields plus the transfer fields. `TEMPORARY_ENTITLEMENT_GRANT` adds only `app_user_id` and `store` to the common fields [O] (same page; [sample events](https://www.revenuecat.com/docs/integrations/webhooks/sample-events)).
- **Event types in 2026** [O] (same page):
  - `TEST`: a dashboard test with a "purchase-like sample payload".
  - Purchases and lifecycle: `INITIAL_PURCHASE`, `RENEWAL`, `CANCELLATION`, `UNCANCELLATION`, `NON_RENEWING_PURCHASE`, `SUBSCRIPTION_PAUSED` (Play only), `EXPIRATION`, `BILLING_ISSUE`, `PRODUCT_CHANGE`, `SUBSCRIPTION_EXTENDED`.
  - `REFUND_REVERSED`: App Store only.
  - `INVOICE_ISSUANCE`: RevenueCat Billing only.
  - Other types: `TRANSFER`, `TEMPORARY_ENTITLEMENT_GRANT`, `VIRTUAL_CURRENCY_TRANSACTION`, `EXPERIMENT_ENROLLMENT`.
  - `PURCHASE_REDEEMED`: web purchases only.
  - `SUBSCRIBER_ALIAS`: deprecated; new projects don't receive it.
  - `PRICE_INCREASE_CONSENT_REQUIRED`, `PRICE_INCREASE_CONSENT_APPROVED`.
  - New types may be added without a version change.
- **Authorization header.** The dashboard has an optional field to "Set authorization header that will be sent with each POST request", and "Your server should verify the validity of the authorization header for every notification" [O] ([webhooks](https://www.revenuecat.com/docs/integrations/webhooks)). The header is `Authorization` and the configured value is sent as entered, with no `Bearer` added [S] ([Hookdeck guide](https://hookdeck.com/webhooks/platforms/guide-to-revenuecat-webhooks-features-and-best-practices): "Sent verbatim on every request"). This is consistent with the official v2 example `"authorization_header": "Bearer 123456"` [O] ([v2 integration](https://www.revenuecat.com/docs/api-v2/integration)).
- **HMAC signing (optional, per integration)** [O] ([webhooks](https://www.revenuecat.com/docs/integrations/webhooks)):
  - Header format: `X-RevenueCat-Webhook-Signature: t=<unix_timestamp>,v1=<hmac_sha256_hex>`.
  - The HMAC-SHA256 is computed over `"<t>.<raw body bytes>"`, before any JSON parsing.
  - `t` and `v1` are recomputed on every delivery attempt.
  - The secret is shown only once. Rotating it invalidates the old one immediately.
- **Success and retries.** "Your server should return a **200 status code**. Any other status code will be considered a failure" (so 204 counts as a failure). RevenueCat retries up to 5 times with backoff of about 2.5, 5, 10, 20 and 40 minutes, roughly 80 minutes in total, then stops. It disconnects after 60 s [O] (same page).
- **Manual retry and disabling.** A failed event can be retried by hand from the dashboard. Events that occur while a webhook is disabled are never sent [O] (same page).
- **Delivery delay.** Usually 5–60 s; cancellations usually arrive within 2 hours [O] (same page).
- **Duplicates.** Delivery is best-effort "at least one delivery" (RevenueCat's wording), and "in some *rare* situations" the same event arrives more than once. Make processing idempotent; the event `id` works for this [O] (same page).
- **Order.** `BILLING_ISSUE`, `CANCELLATION` and `EXPIRATION` "are dispatched in order at the same time, so it is unlikely but possible to receive these events in a different order". After a billing issue, a `RENEWAL` may appear before it in the customer history [O] ([event flows](https://www.revenuecat.com/docs/integrations/webhooks/event-flows)).
- **Plan requirement.** Webhooks need RevenueCat's Pro plan [O] ([webhooks](https://www.revenuecat.com/docs/integrations/webhooks)). Pro is free below $2.5K monthly tracked revenue and costs 1% above it [O] ([pricing](https://www.revenuecat.com/pricing)).

## 4. Restore and transfer setting

- **Where it is.** Project settings > General. It applies to every app in the project, and sandbox purchases can optionally use a different behaviour [O] ([restore behavior](https://www.revenuecat.com/docs/projects/restore-behavior)).
- **Options** [O] (same page):
  - **Transfer to new App User ID** (the default).
  - **Transfer if there are no active subscriptions**.
  - **Keep with original App User ID**.
  - **Share between App User IDs (legacy)**, available only to legacy projects.
- **The one that keeps a purchase with its buyer: "Keep with original App User ID".** It "Returns an error if the App User ID attempting to restore purchase or make a new purchase is different from the original App User ID that made the purchase". It is meant for apps where every customer creates an account before purchasing [O] (same page).
- **Scope.** The setting applies to new purchases as well as restores. It only affects purchases owned by identified app user IDs; anonymous owners are merged (aliased) instead [O] (same page).
- **What the SDK returns: an error, not a CustomerInfo without the entitlement.**
  - `restorePurchases` and `awaitRestore` fail with `ReceiptAlreadyInUseError` (code 7). The backend error `BackendCannotTransferPurchase` maps to that code [O] ([errors](https://www.revenuecat.com/docs/test-and-launch/errors), sources `common/errors.kt`).
  - `syncPurchases` "simulates restoring a purchase" and returns the first error it hits while posting [O] ([restoring purchases](https://www.revenuecat.com/docs/getting-started/restoring-purchases), sources `SyncPurchasesHelper.kt`).
- **The rejected purchase is still acknowledged.** On a non-5xx rejection the SDK marks the purchase token synced and acknowledges it, so Google does not refund it [S] (inferred from sources `common/Backend.kt` and `PostReceiptHelper.kt`).
- **Server-notification setting.** With this restore behaviour, RevenueCat "highly recommend[s]" keeping **Track new purchases from server-to-server notifications** off, unless its app-user-ID detection method is set to "Use anonymous App User IDs" [O] ([RTDN](https://www.revenuecat.com/docs/platform-resources/server-notifications/google-server-notifications)).

## 5. Google Play disclosure and deep link

[Subscriptions policy](https://support.google.com/googleplay/android-developer/answer/9900533) [O]:

- **What to disclose.** "clearly and explicitly disclosing your offer terms, the cost of your subscription, the frequency of your billing cycle, the automatic renewal terms, whether a subscription is required to use the app". Users "should not have to perform any additional action to review the information".
- **Listed violations include:**
  - monthly subscriptions that don't say they renew and charge every month;
  - "Annual subscriptions that most prominently display their pricing in terms of monthly cost";
  - pricing and terms that are only partly localized;
  - not showing that the app is usable without a subscription;
  - SKU names like "Free Trial" for a subscription that charges automatically.
- **Trials and intro prices.** Before enrolment, state the duration, pricing and content, "how and when a free trial will convert to a paid subscription, how much the paid subscription will cost, and how a user can cancel".
- **Management.** The app must disclose how to manage or cancel, and give access to an easy online way to cancel, for example a link to Google Play's Subscription Center in account settings.
- **Coming change (effective 2027-01-27).** Eligibility-based discounts, such as student plans, must disclose their criteria. This does not apply here.

[Deep links](https://developer.android.com/google/play/billing/subscriptions#deep-link) [O] (page updated 2026-10-23, so current):

- **One subscription.** `https://play.google.com/store/account/subscriptions?sku=your-sub-product-id&package=your-app-package` is marked "(recommended)". It is for non-expired subscriptions, and `sku` is the subscription `productId`, not the base plan.
- **All subscriptions.** `https://play.google.com/store/account/subscriptions` lists every subscription. Google also suggests it for helping a user restore a canceled subscription.

## 6. Owner setup, 2026

1. **Play Console app and test build.** Create the app with the final package `com.dantech.splitvibe`, and upload a signed build to a closed testing track. Subscriptions can only be created after an APK is uploaded [O] ([Play product setup](https://www.revenuecat.com/docs/getting-started/entitlements/android-products), [sandbox](https://www.revenuecat.com/docs/test-and-launch/sandbox/google-play-store)).
2. **Subscription and base plans.** In Play Console, go to Monetize with Play > Products > Subscriptions. Create one subscription (a product ID can never be reused, even after deletion), add two auto-renewing base plans (monthly and yearly), and activate them [O] ([Play product setup](https://www.revenuecat.com/docs/getting-started/entitlements/android-products)). Optionally set the subscription's default replacement mode [O] ([subscriptions](https://developer.android.com/google/play/billing/subscriptions)).
3. **RevenueCat project and app.** Create the project and add a Play Store app with its app name, package name and service credentials [O] ([connect a store](https://www.revenuecat.com/docs/projects/connect-a-store)). The app gets the public SDK key; the Worker gets a secret key [O] ([API keys](https://www.revenuecat.com/docs/projects/authentication)).
4. **Google Cloud service account.** Enable the Google Play Android Developer API, the Play Developer Reporting API and Pub/Sub. Create a service account with the roles **Pub/Sub Editor** and **Monitoring Viewer**, then create a JSON key. RevenueCat's Cloud Shell script can do all of this [O] ([Play service credentials](https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials)).
5. **Play Console permissions for the service account.** In Users and permissions, invite the service account's email and add the app under App permissions. Grant these four Account permissions [O] (same page):
   - **View app information and download bulk reports (read-only)**
   - **View financial data, orders, and cancellation survey responses**
   - **Manage orders and subscriptions**
   - **Manage store presence**
6. **Upload the key.** In RevenueCat, go to Apps > the Play app > Service Account Credentials JSON and upload the key. Validation can take up to 36 hours [O] (same page).
7. **Real-time developer notifications** [O] ([RTDN](https://www.revenuecat.com/docs/platform-resources/server-notifications/google-server-notifications)):
   - In the RevenueCat Play app settings, click **Connect to Google** and copy the topic ID.
   - In Play Console, go to Monetize with Play > Monetization setup > Real-time developer notifications. Paste the topic as the Topic name, set Notification content to "Subscriptions, voided purchases, and all one-time products", and save.
   - Click **Send test notification**. If it fails, give `google-play-developer-notifications@system.gserviceaccount.com` the Pub/Sub Publisher role on the topic.
   - Leave "Track new purchases from server-to-server notifications" off.
8. **Products in RevenueCat.** Add both products. Their identifiers take the form `<subscription_id>:<base_plan_id>` [O] ([Play product setup](https://www.revenuecat.com/docs/getting-started/entitlements/android-products)).
9. **Entitlement.** In Product catalog > Entitlements, click **+ New entitlement**, use the identifier `pro`, and attach both products [O] ([entitlements](https://www.revenuecat.com/docs/getting-started/entitlements)).
10. **Offering.** Create an offering and make it the Default Offering, which the SDK returns as `current`. Add the packages `$rc_monthly` and `$rc_annual` [O] ([offerings](https://www.revenuecat.com/docs/offerings/overview); sources `Package.kt`).
11. **Webhook.** In Integrations > Webhooks, choose Add new configuration. Enter the HTTPS URL and the authorization header value, choose the environment (production, sandbox or both), the app and any event filter, and optionally enable HMAC signing [O] ([webhooks](https://www.revenuecat.com/docs/integrations/webhooks)).
12. **Restore behaviour.** In Project settings > General, choose **Keep with original App User ID** [O] ([restore behavior](https://www.revenuecat.com/docs/projects/restore-behavior)).
13. **License testers.**
    - In Play Console, open Manage account > Play apps settings > Monetization settings > License testing, then add and select an email list [O] ([Google help](https://support.google.com/googleplay/android-developer/answer/6062777)).
    - Also add the testers to the closed-track list and open the opt-in URL. Use a device with a PIN and only one Google account signed in [O] ([sandbox](https://www.revenuecat.com/docs/test-and-launch/sandbox/google-play-store)).
    - The tested package name must match the app on Play [O] ([test](https://developer.android.com/google/play/billing/test)).

## Unverified

- **Whether the authorization value is sent verbatim** (only [S]). RevenueCat's own pages say only that the header "will be sent with each POST request". I searched the webhooks page, the v2 Integration reference, the full docs dump and community threads. The only direct statement is Hookdeck's, plus RevenueCat's `"Bearer 123456"` example. Mitigation: compare the whole header value, and also use HMAC.
- **The exact `TEST` payload.** The docs say only "purchase-like sample payload", and it is not among the sample events, so I don't know which `app_user_id` it carries.
- **Whether v2 `active_entitlements[].expires_at` includes the grace period.** The schema doesn't say.
- **Whether v2 `customer_id` equals the App User ID.** The v2 reference only says "ID of the customer". I searched the v2 pages and the full docs dump.
- **An explicit v1-vs-v2 recommendation for new server integrations.** None found in the v1 or v2 overviews, the API keys, customer-info or webhooks pages, or a web search. A search-engine summary attributed a "won't deprecate v1, if ever" quote to staff, but that quote was not in the threads I opened.
- **The status code for `ReceiptAlreadyInUseError`.** That this error arrives as a non-5xx response, so the SDK acknowledges the token, is inferred from SDK source only.
- **The exact `managementURL` string for Play.** RevenueCat's backend may return the general subscriptions page or a link to the one subscription; this is not documented. The SDK's fallback constant is the general URL.
- **Whether RevenueCat checks `obfuscatedAccountId`.** I found nothing saying RevenueCat uses it to refuse a post made under a different app user ID.
- **GitHub sources.** `api.github.com` and `github.com/…/releases.atom` returned 403, so I read the SDK from the Maven Central sources jar and the CHANGELOG from raw.githubusercontent.com.
- **Not researched:** Cloudflare Worker limits such as `waitUntil`, the Play Data safety form, tax, and live Play Console screens. UI paths are as written in RevenueCat's and Google's documentation.

## Implications for the build

App (`gradle/libs.versions.toml` and `app/build.gradle.kts` were read, not changed):

1. **Dependency.** Add `com.revenuecat.purchases:purchases:11.3.0`. Nothing else needs to change: Kotlin 2.3.21, compileSdk 37 and minSdk 26 already exceed the SDK's minimums, PBL 9.1.0 comes in transitively, and `MainActivity` uses the default `standard` launch mode.
2. **Configuration.** Configure once in `SplitVibeApp.onCreate` when a signed-in app user ID is stored; otherwise configure at first sign-in. Switch users with `logIn`, and never call `logOut`.
   - Accept the consequence from §1.3: failed posts are retried only while someone is signed in.
   - Show a "purchase pending" or "pending sync" state rather than relying on automatic recovery.
3. **Errors.**
   - Catch `PurchasesException` for login, logout and restore; `awaitPurchase` throws `PurchasesTransactionException`.
   - Treat `PurchaseCancelledError` as a silent cancel.
   - `PaymentPendingError` means the payment is pending.
   - Treat `ReceiptAlreadyInUseError` and `ProductAlreadyPurchasedError` as "this Google account's subscription belongs to another Split Vibe account".
4. **Plan switch.** Use `PurchaseParams.Builder(activity, targetPackage).oldProductId("<subscription id>").replacementMode(StoreReplacementMode.WITHOUT_PRORATION)` or `CHARGE_FULL_PRICE`. Never use `DEFERRED`, `WITH_TIME_PRORATION` or `CHARGE_PRORATED_PRICE` between base plans of one subscription.
5. **Paywall.** Build it from `pkg.product.price.formatted`, `pkg.product.period` and the `defaultOption` pricing phases; trial and intro lines appear automatically once an offer exists.
   - State that the plan renews automatically and how to cancel.
   - Make the yearly price, not a per-month figure, the main number for the yearly plan.
6. **Manage link.** Build `https://play.google.com/store/account/subscriptions?sku=<subscription id>&package=<applicationId>` from `EntitlementInfo.productIdentifier`, stripping any `:basePlan` suffix to be safe. Fall back to `managementURL` or the general subscriptions URL.
7. **Testing.** The `dev` flavor's package `com.dantech.splitvibe.dev` won't load Play products unless it has its own Play app, so test purchases with the `prod` flavor. Debug builds work for license testers, and their unacknowledged purchases are refunded after 3 minutes, which makes the failure path testable.

Server (`proxy/`):

8. **Webhook route.**
   - Compare the `Authorization` header in constant time against a Worker secret. If HMAC is enabled, verify it over the raw bytes before parsing JSON, because the existing `readJson` parses straight away.
   - Answer with **200**, not 204: `emptyResponse(204)` would count as a failure.
   - Return a 5xx when the RevenueCat fetch fails, so RevenueCat retries (5 times over about 80 minutes).
9. **Which users to refresh.** Use `app_user_id`, or `transferred_from` plus `transferred_to` for `TRANSFER`. Skip IDs the server never issued or has deleted, because GET creates customers; this also neutralises `TEST` events and late retries after account deletion.
10. **Deciding `pro`.** `pro` is active when `expires_date` is null, or when the later of `expires_date` and `grace_period_expires_date` is after `request_date_ms`. Store `request_date_ms` and only overwrite stored state with a newer one; this makes duplicates and out-of-order events harmless, so no event-order logic is needed. Optionally add the six-hour renewal-gap rule (§2.1), using `unsubscribe_detected_at == null` as "auto-renew still on".
11. **Sandbox purchases.** License-tester purchases are sandbox (`is_sandbox`, `environment: SANDBOX`), and one customer can hold both sandbox and production purchases. The production server needs an explicit rule for them.
12. **Account deletion.** If deletion should stop billing, cancel or refund and revoke the Play subscription first. Then `DELETE /v1/subscribers/{id}`, confirm with a second DELETE returning 404, and never confirm with GET.

Open decisions for the owner:

- Which replacement mode to use for each direction (monthly to yearly, yearly to monthly).
- Whether sandbox purchases grant Pro on production.
- Whether account deletion cancels, or refunds and revokes, the subscription.
- Whether a v1 key that can do anything is acceptable, or least-privilege v2 keys are worth the extra calls.
