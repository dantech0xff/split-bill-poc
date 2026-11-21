# Play Console: draft answers

Draft answers for the declarations Play Console asks for before Split Vibe can be published, written
from what the app, its SDKs and the proxy do after the Phase 9 release work. The owner enters them;
the legal adviser reviews the Data safety answers together with the
[privacy policy](privacy-policy.md) (see [compliance.md](compliance.md)).

Play's rules were checked on 19 November 2026 in the Play Console Help pages listed
[at the end](#pages-checked). The forms themselves live in Play Console, which was not opened: where
the exact wording of a question is not on a help page, it is marked **unverified**, and the answer
follows what the app does. In Play Console, the declarations are on the App content page
(Policy and programs > App content).

Addresses below use `<the proxy's host>`, the host of the Worker that serves the app's links and
pages, as [proxy.md](proxy.md) does.

## Privacy policy

- URL: `https://<the proxy's host>/privacy`, rendered from [privacy-policy.md](privacy-policy.md).
- Play asks that it be on an active, public URL with a valid certificate, reachable everywhere
  without a sign-in or geofencing, on a normal web page rather than a PDF, linked from Play Console
  and from inside the app. The app links it from Settings and from the Pro screen.
- Play's User Data policy asks that the policy name the developer and a privacy contact, list the
  data accessed, collected, used and shared and with whom, describe secure handling and the
  retention and deletion policy, and be labelled a privacy policy. The draft does all of these once
  its placeholders are filled.

## Data safety

### Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | Yes | The types below |
| Is all of the user data collected by your app encrypted in transit? | Yes | A release build reaches the proxy only over https, and the release checks refuse plain HTTP. Firebase, ML Kit and RevenueCat each state that they encrypt in transit. |
| Do you provide a way for users to request that their data is deleted? | Yes | Settings > Delete account in the app, and `https://<the proxy's host>/delete-account` on the web |
| Account creation and the delete account URL (wording **unverified**) | Accounts are made with Sign in with Google. Delete account URL: `https://<the proxy's host>/delete-account` | Play's account deletion page asks for the web link "within your Data safety form" |

### Data types

"Collected" means sent off the phone by the app or an SDK in it. "Shared" is No throughout: the
providers that receive data (Anthropic, Cloudflare, Google's Firebase, RevenueCat) process it on the
developer's behalf, which Play does not count as sharing, and what users show other users (a group,
a payment request, a bill link) goes there by their own action, which Play also exempts. See the
[notes](#notes-on-the-data-safety-answers) for the calls a reviewer could make differently.

"Optional" means a person can use Split Vibe without it: typing bills in by hand, not signing in,
not sharing, not buying Pro.

| Category | Data type | Collected | Ephemeral | Required or optional | Purposes | What it is |
|---|---|---|---|---|---|---|
| Location | Approximate location | Yes | No | Required | Analytics | Cloudflare's traces keep, for 7 days, the country and city of each request to the proxy, derived from its IP address. The app never reads the phone's location. |
| Location | Precise location | No | | | | |
| Personal info | Name | Yes | No | Optional | App functionality | The user's name in groups and on requests, and the names of people on a split, when the split goes to a group, a bill link, a payment request or the AI. The Google sign-in token also carries the account's name, which the proxy neither reads nor keeps. |
| Personal info | Email address | Yes | No | Optional | App functionality, Account management | The Google sign-in token carries the account's email address; the proxy reads only the account ID and keeps none of it. A way to get paid can also be an email address, such as a Zelle address in plain details, which a payment request keeps. |
| Personal info | User IDs | Yes | No | Optional | App functionality, Account management, Fraud prevention, security, and compliance | The account's code, a salted hash of the Google account ID, which names its counters and shared data, and the purchaser ID RevenueCat knows it by. |
| Personal info | Phone number | Yes | No | Optional | App functionality | A PromptPay ID, or other payment details, can be a phone number, kept with a payment request. |
| Personal info | Address; Race and ethnicity; Political or religious beliefs; Sexual orientation; Other info | No | | | | The app asks for none of them. A bill's text can show a shop's address, which is declared as bill content below. |
| Financial info | User payment info | Yes | No | Optional | App functionality | The one way to get paid a payment request carries: a bank account number and name (VietQR), an IBAN (SEPA), a PromptPay ID, a UPI ID, a PayPal.Me name, a payment link or other details. The list of ways to get paid stays on the phone. |
| Financial info | Purchase history | Yes | No | Optional | App functionality, Analytics | Pro purchases, which RevenueCat's SDK sends to RevenueCat, and the Pro status the proxy keeps; RevenueCat's guidance names these two purposes. Bills are records of purchases too: their text goes to the AI, and bills added to a group or a bill link are kept on the proxy. |
| Financial info | Other financial info | Yes | No | Optional | App functionality | Who owes whom: a group's bills, shares and payments back, payment requests, and the amounts on a bill link. |
| Financial info | Credit score | No | | | | |
| Health and fitness | Health info; Fitness info | No | | | | |
| Messages | Emails; SMS or MMS; Other in-app messages | No | | | | A payment request is sent through the chat app the user picks. Notifications carry fixed words of the app. |
| Photos and videos | Photos; Videos | No | | | | Photos are read on the phone by ML Kit and never leave it. |
| Audio files | Voice or sound recordings; Music files; Other audio files | No | | | | The phone's speech recognizer, a system service, turns speech into text; the app receives only text, and asks before it uses a speech service that may send sound over the network. |
| Files and docs | Files and docs | No | | | | Exports go only where the user saves or shares them through Android. |
| Calendar | Calendar events | No | | | | |
| Contacts | Contacts | Yes | No | Optional | App functionality | The app has no contacts permission; Android's contact picker gives it the name of the one contact picked. That name leaves the phone only when the person is on a split that goes to a group, a bill link, a payment request or the AI. |
| App activity | App interactions | Yes | No | Required | Analytics | The proxy counts events (bill reads, edits, sign-ins, bill links, picks, payment requests, plan checks) as totals in Workers Analytics Engine, with no account, install ID or IP. |
| App activity | In-app search history | No | | | | Searching bills happens on the phone. |
| App activity | Installed apps | No | | | | |
| App activity | Other user-generated content | Yes | No | Optional | App functionality | The text read from a bill and the instruction, sent to Anthropic; the sentences that change a split; group names; request titles; bills shared in groups and on bill links. |
| App activity | Other actions | No | | | | |
| Web browsing | Web browsing history | No | | | | |
| App info and performance | Crash logs | No | | | | The app has no crash reporter. Android vitals comes from Android and Google Play, not from the app. |
| App info and performance | Diagnostics | Yes | No | Required | Analytics | ML Kit's performance and error data, which Google's SDK sends; the proxy's traces (times, outcomes, CPU time) and logs (tokens, cost and time of each AI call), which Cloudflare keeps up to 7 days. |
| App info and performance | Other app performance data | No | | | | |
| Device or other IDs | Device or other IDs | Yes | No | Required | App functionality, Analytics, Fraud prevention, security, and compliance | The random install ID on most requests (free calls, limits, the device for push); the FCM token and the Firebase installation ID once someone signs in; ML Kit's installation identifiers; salted hashes of the network address for the limits. |

### Notes on the Data safety answers

- **Not ephemeral.** The proxy keeps nothing of a bill read or an edit, but Anthropic keeps API
  inputs and outputs for up to 30 days, so they are not "only stored in memory", as Play defines
  ephemeral processing. Everything else in the table is stored.
- **Sending bill text to the AI is collection**, as the plan records, even though the proxy only
  passes it on: it leaves the phone.
- **ML Kit.** Google lists what ML Kit collects as "Used for diagnostics and usage analytics" and
  says it does not pass it to third parties. If the owner reads ML Kit's terms as Google using this
  data for its own purposes rather than on the developer's behalf, declare Diagnostics and Device or
  other IDs as shared with Google as well.
- **Approximate location** comes only from Cloudflare's traces. If traces are switched off in
  `proxy/wrangler.jsonc`, the answer becomes No.
- **Contacts.** Declared because a contact's name can leave the phone. A reviewer could read the same
  name as Name only; declaring both is the cautious answer.
- **The web pages** (`/s/`, `/r/`, `/g/`, `/delete-account`) open in a browser, not in a WebView of
  the app, so what people type there, and Cloudflare Turnstile's check, is outside the app's form.
  The privacy policy covers them.
- **No analytics or advertising SDK** is in the app, so there is no consent mode to configure.

## Content rating

Done in the IARC questionnaire under App content > Content ratings. Play's help pages describe the
steps but not the questions, so the category list and the wording below are **unverified**.

- Email address for IARC: `<contact email>`.
- Category: the general one for apps that are not games (likely "All Other App Types").
- Violence, sexuality, language, fear, crude humour, gambling (real or simulated): No.
- Controlled substances: the app neither shows nor promotes drugs, alcohol or tobacco, but its
  bundled sample bill lists beer ("Bia Saigon Special") and the example instruction says "Minh had
  the beers". If the questionnaire asks about references to alcohol, answer Yes, or replace the
  sample bill and the example first (an app change).
- Users interact or exchange content: Yes. Members of a group see the names and bills others add,
  everyone with a bill link sees the names typed on it, and payment requests carry names and a title.
  There is no chat, feed or public profile.
- Shares the user's location: No.
- Digital purchases: Yes, the Pro subscription through Google Play.
- Unrestricted internet access: No. The app opens payment links in other apps and its own links in
  a browser; it has no browser of its own.
- If asked whether the app shares personal information with third parties: payment details go to
  the payer the user picks, and service providers process data on the developer's behalf.

## Ads

"No, my app does not contain ads." The app has no ad SDK and shows no house ads.

## Advertising ID

The app does not use the advertising ID. The manifest removes
`com.google.android.gms.permission.AD_ID` (`tools:node="remove"`), so no library can add it to the
merged manifest. The release checklist checks the final bundle.

## App access

Answer "All or some functionality is restricted", and give the instructions below. Play asks that
sign-in details work at all times, be reusable, work from any location, be in English, and skip any
two-step verification.

- **What needs a sign-in:** groups, tracked payment requests, bill links and the 10 monthly AI
  calls. Pro needs a purchase, and only raises the AI calls to 300 a month; every feature can be
  reached without it.
- **Credentials:** a Google account made for the review, without 2-Step Verification:
  `<review Google account>` and `<its password>`. Enter them only in Play Console, never in the
  repository. Play asks for the full account details when an app uses Sign in with Google.
- **Steps for the reviewer:** open the app; tap the round **+** button in the middle of the bar at
  the bottom ("Split a bill") to start a new split, and **Use the sample bill** there to try a bill
  without a photo, then **Read and split the bill**, and **Agree** on the screen that asks before
  Split Vibe first sends a bill's text to the AI; open Settings with the round button at the top
  right of the home screen and tap **Sign in with Google**, choosing the review account (add it to
  the device first if the picker does not offer it).
- **AI calls:** an install has 3 free calls, and the review account 10 a month, shared by every
  reviewer who uses it. So that a review does not run out, grant the review account Pro from
  RevenueCat's dashboard once it has signed in once: the proxy counts such a grant as Pro until it
  ends ([proxy.md](proxy.md#pro-subscriptions)).
- **Deleting the account:** in Settings, or on the web at `https://<the proxy's host>/delete-account`.
  Deleting the review account ends its access; sign in again to make a new one.

## Target audience and content

- Target age: **18 and over** only. Split Vibe asks people for money and handles payment details,
  and an adult audience keeps it out of the Families policy. This is the owner's decision; the
  privacy policy and the terms say 18 too ([compliance.md](compliance.md#what-the-drafts-assume)).
- Restrict Minor Access: optional for this app; when on, Play keeps users it determines are under
  18 from finding, downloading or buying it. The owner decides.
- The store listing must not appeal to children: no cartoon characters or childish art.

## Financial features

"My app doesn't provide any financial features." Split Vibe never holds, receives or moves money
and is not a wallet, a bank or a payment service. A payment request carries a link, a QR code or
details that the payer opens in their own banking or payment app, where the payment happens. Of
Play's options, "Mobile payments and digital wallets" and "Money transfer and wire services" do not
fit, as the app initiates no payment. The owner and the adviser should confirm this answer, since a
reviewer sees payment QR codes in the app. The declaration is required of every app, also of one
without financial features.

## Health apps

"My app doesn't provide any health features." Required of every app.

## News and government apps

Split Vibe is not a news app and not a government app; answer No where asked.

## Pro subscription

Set up as [pro-subscription.md](pro-subscription.md) describes: the subscription `pro` with the
auto-renewing base plans `monthly` and `annual`, and RevenueCat's products, entitlement and
offering.

How the app meets Play's Subscriptions policy:

| Play asks | The app |
|---|---|
| The price and how often it is charged | Google Play's price for each plan's period: "$3.99 a month", "$29.99 a year". The yearly plan shows its yearly price, never a monthly figure. |
| The renewal terms | "A plan renews automatically at the end of each month or year, at its price shown here, until you cancel it. You can cancel any time in Google Play, and Pro then lasts until the end of the period you paid for." |
| Whether a subscription is needed | "Everything else stays free: typing bills in by hand, groups, insights, exports and recurring bills. Split Vibe works without a subscription." |
| An offer's terms before enrolment | The offer's phases as Google Play states them, such as "Free for 7 days, then $3.99 a month." Pro launches without an offer. |
| An easy way to cancel | "Manage subscriptions in Google Play", which opens Google Play's subscriptions page, at the Pro subscription when the app knows it |
| Privacy policy and terms | Linked on the Pro screen next to the renewal terms |

The prices in the quotes are examples from the reference screenshots; the screen shows what Google
Play returns. In the subscription's listing in Play Console, use a plain name such as "Split Vibe
Pro" and a benefit such as "300 AI calls a month instead of 10"; Play's policy forbids names such as
"Free Trial" for a subscription that charges.

## Account deletion

Play requires an in-app path and a web resource where people can ask for deletion without
reinstalling the app, which names the app or the developer as the store listing does. Where extra
steps come before deletion, such as cancelling a subscription, they must be stated.

- In the app: Settings > Delete account. The dialog warns that deleting does not cancel Pro, and
  offers "Also delete the bills, people and payment methods on this phone", off by default.
- On the web: `https://<the proxy's host>/delete-account` deletes with Sign in with Google, says
  what is deleted and what stays (the bills on the phone, and a Pro subscription to cancel in Google
  Play), and shows the result.
- What is deleted and what is kept, and for how long, is in the privacy policy's section "Deleting
  your account".
- Data safety: answer Yes to deletion requests and give the web address.

## Pages checked

All Play Console Help, checked on 19 November 2026:

- Provide information for Google Play's Data safety section: https://support.google.com/googleplay/android-developer/answer/10787469
- Understanding Google Play's app account deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111
- User Data: https://support.google.com/googleplay/android-developer/answer/10144311
- Prepare your app for review: https://support.google.com/googleplay/android-developer/answer/9859455
- Requirements for providing sign in details for review: https://support.google.com/googleplay/android-developer/answer/15748846
- Content Ratings: https://support.google.com/googleplay/android-developer/answer/9898843
- Content rating requirements for apps, games, and the ads served on both: https://support.google.com/googleplay/android-developer/answer/188189
- Manage target audience and app content settings: https://support.google.com/googleplay/android-developer/answer/9867159
- Provide information for the Financial features declaration: https://support.google.com/googleplay/android-developer/answer/13849271
- Provide information for the Health apps declaration form: https://support.google.com/googleplay/android-developer/answer/14738291
- Advertising ID: https://support.google.com/googleplay/android-developer/answer/6048248
- Subscriptions: https://support.google.com/googleplay/android-developer/answer/9900533

Also used: RevenueCat's "Google Play's Data Safety"
(https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety),
Firebase's "Prepare for Google Play's data disclosure requirements"
(https://firebase.google.com/docs/android/play-data-disclosure) and Google's ML Kit data disclosure
(https://developers.google.com/ml-kit/android-data-disclosure).
