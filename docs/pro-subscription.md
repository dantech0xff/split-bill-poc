# Selling Pro: Play Console, RevenueCat and the Worker

Split Vibe Pro is one auto-renewing subscription sold through Google Play. Pro gives 300 AI calls a
month instead of 10; everything else in the app stays free. RevenueCat validates the purchases, and
the AI proxy decides who is Pro by asking RevenueCat itself, never by trusting the app or the
content of a webhook. How the proxy does that is in [proxy.md](proxy.md); how the app sells it is in
[architecture.md](architecture.md).

This page lists what the owner sets up outside the code, in order. The facts behind each step, with
their sources, are in
[the research of 17 November 2026](../plans/reports/researcher-261117-0442-revenuecat-play-billing-facts.md).

## Names the code expects

| Where | Name |
|---|---|
| Play Console subscription | product ID `pro`, with two auto-renewing base plans `monthly` and `annual` |
| RevenueCat products | `pro:monthly` and `pro:annual` |
| RevenueCat entitlement | `pro`, attached to both products |
| RevenueCat offering | the default offering, with the packages `$rc_monthly` and `$rc_annual` |
| RevenueCat app user ID | the `purchaser` the proxy hands the app; the app never makes up another one |
| Worker secrets | `REVENUECAT_SECRET_KEY` and `REVENUECAT_WEBHOOK_AUTH` |
| Webhook URL | `https://<your worker>/v1/subscription-events` |
| App key | `splitvibe.revenueCatApiKey` in `local.properties` (RevenueCat's public SDK key for Android) |

A product ID can never be used again in Play Console, even after it is deleted, so create `pro` once.

## Before you start

1. Finish the trademark search for the name "Split Vibe". Creating the app in Play Console fixes the
   package name `com.dantech.splitvibe` for good.
2. Create the app in Play Console and upload a signed release build to a testing track. Play
   creates subscriptions only for an app that has a build uploaded. The build must be signed with an
   upload key, not the debug key.

## Play Console

1. In Monetize with Play > Products > Subscriptions, create the subscription `pro` and add the two
   auto-renewing base plans `monthly` and `annual`. Set the prices; the plan's starting point is
   2.99 to 3.99 USD a month and 19.99 to 29.99 USD a year, and the app shows whatever prices Play
   returns for each region. Activate both base plans.
2. Create no free trial: Pro starts without one. If you add an offer later, the app shows its terms
   without a new release.
3. The app switches between the two plans with "charge at the next billing date"
   (`WITHOUT_PRORATION`): the switch takes effect at once and the new price applies from the next
   renewal. Google accepts only that mode or "charge immediately" between two base plans of one
   subscription.

## Google Cloud service account

RevenueCat reads purchases from Google with a service account. Its
[Cloud Shell script](https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials)
can do the first two steps.

1. Enable the Google Play Android Developer API, the Google Play Developer Reporting API and
   Pub/Sub, then create a service account with the roles Pub/Sub Editor and Monitoring Viewer, and
   a JSON key for it.
2. In Play Console > Users and permissions, invite the service account's email, add the app under
   App permissions, and grant: View app information and download bulk reports (read-only); View
   financial data, orders, and cancellation survey responses; Manage orders and subscriptions;
   Manage store presence.

## RevenueCat

1. Create a project and add a Play Store app with the package name `com.dantech.splitvibe`, then
   upload the service account's JSON key under Service Account Credentials. Validation can take up
   to 36 hours.
2. Real-time developer notifications: in the Play app's settings in RevenueCat, choose Connect to
   Google and copy the topic. In Play Console > Monetize with Play > Monetization setup, paste it as
   the topic of Real-time developer notifications, choose "Subscriptions, voided purchases, and all
   one-time products", save, and send a test notification. If it fails, give
   `google-play-developer-notifications@system.gserviceaccount.com` the Pub/Sub Publisher role on the
   topic. Leave "Track new purchases from server-to-server notifications" off.
3. Add the products `pro:monthly` and `pro:annual`, create the entitlement `pro` with both
   products, and create an offering with the packages `$rc_monthly` and `$rc_annual`. Make it the
   default offering.
4. In Project settings > General, set the restore behavior to **Keep with original App User ID**.
   A subscription then stays with the Split Vibe account that bought it: restoring it on another
   account fails, and the app tells the person to sign in with the account that bought it.
5. Webhooks need RevenueCat's Pro plan, which is free under 2,500 USD of monthly tracked revenue
   and costs 1% above it.

## The Worker

1. Make a long random value for the webhook, for example with `openssl rand -hex 32`, and keep it
   out of chat and out of the repository.
2. Set the two secrets from `proxy/`, pasting each value when Wrangler asks for it:

   ```bash
   npx wrangler secret put REVENUECAT_SECRET_KEY
   ```

   ```bash
   npx wrangler secret put REVENUECAT_WEBHOOK_AUTH
   ```

   `REVENUECAT_SECRET_KEY` is a secret API key (it starts with `sk_`) from the RevenueCat project's
   API keys. It can do anything in the project, so it lives only in the Worker's secrets; rotate it
   in RevenueCat if it ever leaks. `REVENUECAT_WEBHOOK_AUTH` is the random value of step 1.
3. Deploy the Worker as [proxy.md](proxy.md) describes. Without the two secrets nobody becomes Pro,
   and everything else works as before.

## The webhook

In RevenueCat > Integrations > Webhooks, add a configuration:

- URL: `https://<your worker>/v1/subscription-events`.
- Authorization header value: the random value of the Worker's `REVENUECAT_WEBHOOK_AUTH`, either as
  it is or after `Bearer `.
- Environment: both production and sandbox, so that license testers' purchases count.

Send a test event: the Worker answers 200. A 401 means the two values differ.

## The app

Put RevenueCat's public SDK key for the Play app in `local.properties` as
`splitvibe.revenueCatApiKey`, next to the other `splitvibe.*` keys. A build without it does not sell
Pro. Test purchases with the `prod` flavor: the `dev` flavor's package `com.dantech.splitvibe.dev`
is not the app on Play, so Play returns no products for it.

## Testing the whole cycle

Add license testers in Play Console's
[license testing](https://support.google.com/googleplay/android-developer/answer/6062777) settings
and to the testing track, open the opt-in link on the test phone, and use a phone signed in to only
one Google account. A tester's subscription renews within minutes instead of a month, and only a
few times; Google's [testing guide](https://developer.android.com/google/play/billing/test) lists
the times. A tester's purchase that is not acknowledged is refunded after 3 minutes.

Check, with a tester:

1. Signed in, buy the monthly plan: within 5 minutes the calls become 300 a month.
2. Let it renew a few times: the account stays Pro.
3. Switch to the yearly plan and back: the account stays Pro.
4. Cancel it in Google Play, then wait for it to end: the calls go back to 10 a month.
5. Buy again, then restore on a second phone signed in to the same Split Vibe account: it is Pro there too.
6. Restore on a different Split Vibe account: the subscription does not move, and the app says which
   account to sign in with.
7. Refund the order in Play Console and revoke the subscription: the account is no longer Pro.
8. As a free account, use the 11th call of a month: the app explains it and offers Pro, and manual
   entry keeps working.
9. Delete the account: the app first warns that deleting it does not cancel Pro.
