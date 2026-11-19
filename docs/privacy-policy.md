# Split Vibe Privacy Policy

Last updated: [effective date]

This policy explains what Split Vibe does with information about you: in the Split Vibe app for Android, in the web pages that its links open, and on the server that both of them use. It describes the service as it works today.

## In short

- The photos of your bills never leave your phone. The phone reads the text of a bill itself.
- When you ask the AI to read a bill, the text your phone read and the instruction you typed go through our server to Anthropic, whose AI model works out the items. When you change a split by a sentence, the bill, the names on it and your sentence go the same way.
- Your bills, the people you split with and your ways to get paid are kept on your phone. Our server keeps only what you share with other people: bills you add to a group, payment requests you send, and bills you open for picking with a link.
- We keep no email address, name or photo of your Google account. Our server knows an account only by a code worked out from the account's Google ID, from which the ID cannot be read back.
- The app shows no ads and contains no analytics or advertising SDK. We count how the service is used only as totals, with no account, install or network address attached.
- You can delete your account in the app or on our website.
- We do not sell your personal data.

## Who is responsible

Split Vibe is provided by [owner name], [postal address] ("we", "us"). We decide why and how the personal data described here is processed, and we are responsible for it.

You can reach us at [contact email].

If you are in the European Union, you can also contact our representative there: [EU representative name and address].

If you are in the United Kingdom, you can also contact our representative there: [UK representative name and address].

## What stays on your phone

The app keeps these in its own storage on your phone, and nowhere else:

- your bills, with their drafts, earlier versions and recurring bills;
- the photos of each bill's pages;
- the people you split with before, including names you picked from your contacts;
- your ways to get paid: bank accounts for VietQR or SEPA transfers, PromptPay, UPI, PayPal.Me, payment links and any other details you add;
- the name you use in groups and on requests, your local currency and your other settings;
- the email address of the Google account you signed in with, which Settings shows, and your sign-in session, both encrypted with a key held by Android's Keystore;
- a copy of the groups and requests our server last sent, so that you can read them without a connection. The app empties it when you sign in, sign out or delete your account.

The insights you export as CSV files or a PDF go only where you choose to save or share them.

The app turns Android's backup to Google Drive off, so this data is not copied to your Google account. On some phones, a direct transfer from an old phone to a new one can still copy it. Clearing the app's data or uninstalling the app deletes it from the phone, but not the copies you shared, which are described below.

The app asks Android for three permissions: the internet; notifications, which only show notices; and the microphone, which it asks for only when you tap the microphone to say a change. It does not ask for the camera, your files or your contacts. Photos come from Google's document scanner, the camera app or the photo picker, and a person from the system's contact picker, which hands the app only the contact you pick.

## What leaves your phone, and why

### Reading a bill with AI

When you ask the app to read a bill, the phone first reads the text of the photos itself, with Google's ML Kit. It then sends our server the text it read, with the position of each piece of text on the page, the instruction you typed (such as "Split with Minh and Lan. Minh had the beers.") and your local currency. That text is whatever is printed on the bill, which can include a shop's name and address, a staff member's name or part of a card number.

Our server sends these to Anthropic's AI model, which answers with the bill's items, charges, total and currency, and with what your instruction says about people. Our server passes the answer to your phone and keeps nothing of the bill, its text or your instruction. Your phone then works out everyone's share itself.

We do this to read the bill you asked us to read.

### Changing a split by a sentence

When you type or say a change, such as "Lan didn't have beer", the phone sends our server the bill's items, charges and total, the shop's name, the names of the people on the split (yours as you entered it, or "Me"), how the bill is split and who paid, and your sentence. Our server sends these to Anthropic's AI model, which proposes changes. Nothing changes until you apply them. Our server keeps none of it.

To let you say a change, the app uses your phone's speech recognizer. On Android 12 and later, when your phone can recognize speech by itself, the sound stays on the phone. Otherwise the app asks you before it uses your phone's speech service, which may send the sound over the internet to the company that provides that service, on many phones Google. You can take that permission back in Settings. Split Vibe never receives the sound, only the words, which you check before anything is sent.

### Your free calls and the app's version

The app makes a random ID for itself. The ID is not tied to your phone's hardware or to you. The app sends it, with the app's version number, with most requests to our server. Our server uses the ID to count the 3 free AI calls of an install that is not signed in, and uses the version to refuse AI requests from an app too old to work with it.

Our server also limits how much each network address can ask of the AI, and caps what it spends in a day, so that nobody can run up its costs. For that, it counts requests and spending under a salted hash of your network address (an IPv6 address by its first half), never the address itself.

### Signing in with Google

Signing in is optional. It gives you 10 AI calls a month, groups, tracked payment requests and bill links. When you sign in, Google shows its account picker and hands the app a sign-in token for the account you pick. That token, which Google makes, names the account and includes its email address and, usually, its name and a link to its profile photo. The app sends the token to our server, which checks with Google's public keys that it is real and reads only the account's Google ID from it. From that ID it works out your account's code, a salted hash, and keeps nothing else of the token. It then gives the app a session that lasts 30 days.

The app keeps the account's email address on your phone to show you which account you are signed in with. It is not sent to our server again.

### Groups

A group, and everything in it, is shared with its members through our server. Our server keeps:

- the group's name and currency;
- its members: the name each member typed for themselves, the names members typed for people kept a place for, and which member is which account;
- the bills added to the group: what was bought, the charges, the total, who was on the bill and how it was split, who paid, and the rate used to convert it into the group's currency. A bill in a group carries no photo, no text read from the photo and no instruction;
- the payments back that members recorded;
- a log of what changed, naming members and bills by their IDs;
- requests to join a group, with the name the person typed, and invites, of which only a hash of the link is kept.

Every member of a group sees all of this, except the requests to join, which only the group's owner sees. We keep it to run the group for its members.

### Payment requests

When you ask someone for money while signed in, our server keeps the request so that the payer can say they paid and you can confirm it: the amount and currency, the payer's name as you typed it, your name as you want the payer to see it, what it is for, and the one way to get paid you chose for it, such as a bank account number and the name on it, a PromptPay ID, a UPI ID or a PayPal.Me name. It can also keep how to pay as your app built it for the request's web page: a link, a code to scan and the details. The person you ask sees this in the app, and anyone with the request's link can see it on its web page. The other ways to get paid on your phone stay there.

Without signing in, a request is only a message that you send from your phone through the chat app you choose. It does not go to our server.

### Bills you open for picking

When you open a bill for everyone at the table to pick their items, our server keeps what the page shows: the shop's name and the date, the items, charges and total, the names of the people on the bill, the names people type on the page with the items they pick, and, once you settle the split, what each person owes, how to pay, who paid and which person on the bill each pick is. Anyone with the link sees this. A link works for 30 days, and you can close the picking or take the link back at any time.

### Push notifications

Once you sign in, the app asks Firebase Cloud Messaging, a Google service, for a push token for this install, and sends it to our server with the install's ID. Our server uses it to tell you about payment requests and about requests to join your groups. The app asks our server to forget it when you sign out on that phone. To deliver notifications, Firebase's SDK on the phone also uses an ID for the install and basic details of the app and the phone. A notification carries fixed words of the app's own and the ID of the request or group it is about: never a name, an amount or a link.

### Pro

Pro is a subscription sold through Google Play. Google Play takes the payment, and we never see your payment details. While you are signed in, the app uses RevenueCat to sell and check Pro, under a purchaser ID that our server gives the app for your account and that names no one. RevenueCat receives, under that ID, the purchases you make in Split Vibe through Google Play, and details such as your phone's locale and currency. Our server asks RevenueCat whether your account is on Pro, and keeps until when, so that it can count your calls. When nobody is signed in, the app does not contact RevenueCat.

### Exchange rates

The app fetches the day's exchange rates from our server without saying who asks. Our server gets them from Frankfurter, a public source of central bank rates, which learns nothing about you.

### Reading text and scanning on the phone

Google's ML Kit reads the text of your photos, and Google's document scanner finds and straightens a bill's pages. Both run on your phone. According to Google, ML Kit sends Google information about how it works so that Google can diagnose it and measure its use: your phone's maker, model and Android version, the app's name and version, IDs for the install and, for the features that come with Google Play services, for the phone, timings, settings, the size of what it read, and error codes. Google does not list the photos or the text among what it collects. Google describes this on its [ML Kit data disclosure page](https://developers.google.com/ml-kit/android-data-disclosure).

### Crash reports

If you allowed your phone to share usage and diagnostic data with Google, Google Play gives us reports of the app's crashes and freezes (Android vitals). Google shows them to us only in aggregate, so that they do not identify you. The app itself contains no crash reporting tool.

## People who open a link without the app

Split Vibe's links open web pages served by our server, which work without the app or an account.

- The part of a link after the "#" is its key. Your browser does not send that part to our server: the page reads it and sends it in the body of its requests, so it does not appear in any log.
- On a bill link, you can type a name and pick the items you had. Our server keeps them with the link, and everyone with the link sees them. Your browser keeps your pick's secret key, its ID and the name you typed in its local storage, so that you can change your picks later; clearing the site's data in your browser removes them.
- Before your first pick on a bill, Cloudflare Turnstile checks that you are a person and not a program. It receives your network address, details of your browser and its connection, and the site's address.
- A payment request page shows the request to anyone with its link. An invite page shows the group's name and currency.
- Our server counts the requests each page makes in an hour under a salted hash of your network address, to stop abuse.
- The account deletion page loads Google's sign-in from Google, so Google learns of your visit to that page.
- Split Vibe's pages set no cookies of their own.

## Who processes your data for us

We use these companies to run Split Vibe. Each receives only what the service needs from it, under its own terms and privacy policy.

- Anthropic, PBC (United States) runs the AI model. It receives the text of a bill with your instruction and local currency, or a bill with the names on its split and your sentence, as described above. It does not receive your photos, your account, your install's ID or your network address: requests come from our server. Anthropic says it deletes these inputs and the model's answers within 30 days, and keeps them for up to 2 years if its systems flag a request as breaking its usage policy. Under its commercial terms, Anthropic may not train its models on them. See [how long Anthropic keeps API data](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data) and [Anthropic's privacy policy](https://www.anthropic.com/legal/privacy).
- Cloudflare, Inc. (United States) runs our server, its database, its counters, its logs and its aggregate counts, and Turnstile. It receives everything the app and the pages send to our server, and the network address of every connection. See [Cloudflare's privacy policy](https://www.cloudflare.com/privacypolicy/) and [Turnstile's privacy terms](https://www.cloudflare.com/turnstile-privacy-policy/).
- Google runs Sign in with Google, Firebase Cloud Messaging, Google Play and ML Kit, as described above. Google Play also distributes the app and handles subscriptions and refunds. See [Google's privacy policy](https://policies.google.com/privacy).
- RevenueCat, Inc. (United States) checks and keeps Pro purchases: your account's purchaser ID and the purchases made in Split Vibe through Google Play. See [RevenueCat's privacy policy](https://www.revenuecat.com/privacy).

Other services you choose are not ours. Your phone's speech service, the chat app you send a request with, and the bank or payment app a payment link or code opens handle what you give them under their own terms.

## Where your data is stored

- Our database, which holds groups, payment requests, bill links, push tokens and hourly counters, is in Cloudflare's eastern North America region.
- The counters of AI calls, limits and spending live in the Cloudflare data center near where each was first used, which may be in your country or in another one.
- Each request to our server is handled in a Cloudflare data center, usually one near you.
- Anthropic, RevenueCat and Google process data in the United States and in the other countries where they and their service providers operate.

## Transfers to other countries

If you use Split Vibe from outside the United States, your data is sent to and kept in other countries, including the United States, where data protection laws may differ from yours.

For people in the European Economic Area, the United Kingdom and Switzerland, the data processing terms of Cloudflare, RevenueCat and Google's Firebase include the European Commission's standard contractual clauses. [Confirm the transfer safeguard in Anthropic's data processing addendum.]

For people in Vietnam, this is a transfer of personal data abroad under Vietnam's Law on Personal Data Protection.

## How long we keep it

On your phone, everything stays until you delete it, clear the app's data, uninstall the app or delete your account with "Also delete the bills, people and payment methods on this phone" ticked.

On our server:

- The count of free calls of an install that is not signed in is kept, under the install's random ID, for as long as Split Vibe runs, since an install gets its 3 free calls only once.
- For a signed-in account, only the count of calls of the latest calendar month, counted in UTC, in which it used the AI is kept. Its Pro status, as RevenueCat last reported it, and the time our server first gave out the account's purchaser ID, are kept until you delete the account.
- The ID of each AI request, with a salted hash of what it asked, is kept for one day, so that a retry is not charged twice.
- Counters per install, per account and per network address for the limits and the daily spending caps delete themselves once their periods end, at most a day after they began.
- Hourly counters of what an account creates, and of what the web pages ask for per network address, are deleted by our daily clean-up once their hour is over.
- A session is not stored on our server. It lasts 30 days on your phone.
- Groups, with their members, bills, payments back and log, are kept until the group is deleted, which happens when its last member with an account leaves it or deletes their account. A bill deleted from a group is emptied at once.
- A payment request stays until the person who asked deletes it or confirms it was paid. One deleted before it is confirmed is emptied at once and removed 30 days later. One confirmed as paid is kept as a record for both people for as long as Split Vibe runs, also after either of them deletes their account, without that account on it.
- An invite is removed 30 days after it expires; invites work for 14 days.
- A bill link, with the picks made on it, is deleted by our daily clean-up once it has expired, 30 days after it was made, or once its owner took it back.
- A push token is deleted when the app signs out on that phone and tells our server, when you delete your account, when it is passed to another account, when Firebase says it no longer works, or when the account signs in on an eleventh phone, which replaces the one used longest ago.
- Our server's logs record each AI request's outcome, model, number of tokens, cost and time taken, and events such as notifications sent, without any ID, network address, text or name. They are kept for up to 7 days. Cloudflare's traces record how long each request took, its outcome, its address on our server (which holds IDs only), the country and city it came from, the network it came through and the browser or app that made it, without its network address, its contents or its headers. They are kept for 7 days.
- The aggregate counts described below are kept for 3 months.
- Our database keeps a history of itself for up to 30 days, so that it can be restored after a fault. What is deleted from the database stays in that history until it ages out.

Anthropic, RevenueCat and Google keep what they receive as their own policies say, and as described above.

## Counting how Split Vibe is used

To know whether Split Vibe works and what it costs, our server counts events as totals: bills read and splits changed by a sentence, with their outcome and cost; the first AI call of an install; sign-ins; bill links made and items picked; payment requests made, reported paid and confirmed; checks of Pro and RevenueCat's notices; and the daily runs. Each count holds only the kind of event, its outcome and a number such as a cost. None holds an account, an install's ID or a network address. Cloudflare keeps these counts for 3 months.

The app itself sends no usage statistics and contains no analytics or advertising tool. It does not read your phone's advertising ID.

## Deleting your account

You can delete your account in the app, in Settings, under Delete account. You can also [delete it on our website](/delete-account), by signing in with Google there, without the app. Deleting your account does not cancel a Pro subscription: cancel it in Google Play first. The app warns you about this before it deletes the account.

When you delete your account, our server:

- first has RevenueCat delete your account's customer record and purchase history. Google Play keeps its own record of your purchases;
- deletes your push tokens, your bill links with the picks made on them, your requests to join groups, and the rest of your account's counters and Pro status;
- keeps the count of calls you used this calendar month, under your account's code, until the month ends, so that deleting an account and signing in again does not give new calls. It also keeps the hourly counters, which hold nothing but your account's code, until the daily clean-up after their hour;
- in groups, turns you into a member called "Former member", keeping the amounts you paid and owe so that the other members' balances do not change. The bills you added stay in the group and pass to its owner. A group in which nobody else has an account is deleted;
- on the payment requests you made, deletes the way to get paid of each request not yet confirmed and the payment details of every request's web page, and shows your name as "Former member". The other person keeps the request, and on a request already confirmed as paid they keep the way to get paid it carried, as their record;
- takes your account off every request, so that nobody can act as you on it.

Your bills, people and ways to get paid are on your phone, not on our server. When you delete your account in the app, you can tick "Also delete the bills, people and payment methods on this phone" to delete your bills, drafts, recurring bills, page photos, people, ways to get paid, the copy of your groups and requests, and your name from the phone. If you leave it unticked, they stay. The app deletes them only after our server has deleted your account, so that a failure loses nothing. Your other settings, such as your currency, stay. Deleting your account on the website does not touch your phone.

Deleting your account signs you out on the phone you delete it from. If you are signed in to the same account on another phone, sign out there too: a phone that is still signed in can keep using the account for up to 30 days after it signed in, and what it does is kept again under the same account.

What is deleted stays in our database's history for up to 30 days, as described above.

## Your rights

Depending on where you live, you may have the right to know what personal data we hold about you and get a copy of it, to have it corrected or deleted, to restrict or object to how we use it, to take it elsewhere, to withdraw a permission you gave, and to complain to a data protection authority, such as the one of your country in the European Union or the United Kingdom, or the Ministry of Public Security in Vietnam.

You can see most of what our server keeps about you in the app: your groups, your requests and your bill links. You can delete your account and its data yourself, in the app or on our website. For anything else, write to [contact email]. Because we keep no email address or name with your account, an email alone does not tell us which account is yours, so we may need you to show us that an account is yours before we act on a request about it.

## Why we may use your data

Where the law asks us to name a legal basis:

- we use your data to provide the features you ask for: reading bills, changing splits, signing in, groups, payment requests, bill links, notifications and Pro;
- we have a legitimate interest in keeping Split Vibe safe and affordable, so we limit requests per install, account and network address, cap spending, check first picks with Turnstile, keep short logs and traces, and count use in totals;
- where we ask for your permission, such as for notifications or for your phone's online speech service, you can change your mind in the app's Settings or in your phone's settings.

The AI reads bills and proposes changes; you decide what to apply and what to ask of anyone. Split Vibe makes no decision about you.

## Children

Split Vibe is meant for adults aged 18 and over, and is not directed at children. We do not knowingly collect personal data from children. If you believe a child has used Split Vibe, the account can be deleted in the app or on our website, and you can write to us at [contact email].

## Security

Everything the app and the pages send to our server travels encrypted over HTTPS. Your session on the phone is encrypted. Our server keeps links, keys and network addresses only as hashes, and accounts only as salted hashes. No system is perfectly secure, so we keep only what the service needs, for as long as it needs it.

## Changes to this policy

When we change this policy, we publish the new version on this page before it takes effect, with its date at the top and a note of what changed.

## Contact

[owner name], [postal address], [contact email].

Support: [support channel].

See also our [Terms of Use](/terms).
