# Store listing

The text and images of Split Vibe's page on Google Play, in English, the only language of 1.0. The
owner enters them in Play Console under Grow users > Store presence > Main store listing
(**unverified** menu path). Play's limits were checked on 19 November 2026 in "Create and set up
your app", "Add preview assets to showcase your app" and "Metadata" (Play Console Help).

## App name

```text
Split Vibe
```

10 characters; Play allows 30. Play's Metadata policy forbids emoji, words of ranking or price such
as "#1" or "free", and all capitals in a name unless they are the brand's. The name waits on the
trademark search ([release-checklist.md](release-checklist.md#before-the-closed-test)).

## Short description

```text
Scan a bill, say how to split it, and ask everyone for their share.
```

67 characters; Play allows 80.

## Full description

```text
Split Vibe splits a bill for you. Scan or photograph the bill, say how it should be split in your own words, and Split Vibe works out who owes what. Then it gives each person a message to pay you, with a link or a QR code for the way you get paid.

Read the bill, your way
• Scan it with Google's document scanner, up to five pages; take a photo; pick one from your gallery; share it from another app; or type the bill in by hand.
• Say how to split it: "Split with Minh and Lan. Minh had the beers." or "We are 3, I didn't drink."
• Your phone reads the text of the bill, and AI works out the items, taxes, fees and total. The photos never leave your phone.
• Check everything, and fix anything misread, before you split.

Split it fairly
• Split equally, by item, by shares, by percent or by exact amounts, and divide any item its own way.
• Taxes, fees, tips and discounts follow each person's items.
• One person paid, or several did: say how much each.
• The shares always add up to the bill's total.
• A bill in another currency is also shown in yours, at the day's rate or at a rate you type in.

Change it with a sentence
Type or say "Lan didn't have beer" or "add a 10% tip". Split Vibe shows each person's share before and after, and nothing changes until you apply it. Undo puts it back.

Everyone picks their items
Send a link to the table. Everyone opens it in a browser, without the app or an account, types their name and taps what they had. You see the picks as they happen.

Ask for the money
Each person gets their own message to send in any chat app, with the amount and how to pay you: VietQR, PromptPay, UPI, SEPA, PayPal.Me, a link made in another payment app, or details to type in. Signed in, you can see who has paid, confirm it and send reminders.

Groups for trips and shared flats
Keep the bills of a trip or a flat together in one currency. See each member's balance across bills and currencies, and who pays whom to settle up. Invite people with a link.

Insights and exports
See what your bills came to by month, category, person and group, with your own share. Export a period as CSV files or a PDF summary.

Recurring bills
Rent or a weekly dinner: a finished bill can repeat every week or month, as a draft for you to check.

Private by design
• Your bills, the people you split with and your ways to get paid stay on your phone.
• No ads, and no analytics or advertising SDK.
• Split Vibe never holds or moves money. Payment links and codes open your own bank or payment app.

Free, with Pro for more AI
Typing bills in, splitting, groups, payment requests, insights, exports and recurring bills are free. Reading a bill with AI takes one AI call, and changing a split by a sentence a fifth of one. You get 3 calls to try, 10 a month when you sign in with Google, or 300 a month with Pro: a monthly or yearly subscription through Google Play, which you can cancel at any time in Google Play.

Good to know
• Split Vibe is in English, and reads bills printed in Latin script, such as English, Vietnamese and most European languages.
• Groups, tracked payment requests and bill links need a Google sign-in.
• AI can misread a bill: check the amounts before you ask anyone to pay.
• Needs Android 8.0 or later.
```

3,242 characters, counting each line break as one; Play allows 4,000. Every statement in it
is something the app does today, as the [README](../README.md) and [proxy.md](proxy.md) describe.
Keep it that way when the text changes: Play's Metadata policy forbids misleading descriptions,
repeated keywords and unattributed testimonials. Recount after any change, for example with
`python3 -c "import sys; print(len(sys.stdin.read().rstrip()))" < description.txt`.

## Screenshots

From the reference images in [`app/src/test/screenshots/`](../app/src/test/screenshots), which the
screenshot tests draw from the real screens with the sample bill and other sample data, in this
order:

| # | File | Size (px) | Shows |
|---|---|---|---|
| 1 | `01-home-owed.png` | 824 × 1830 | The home: what friends owe you and what you owe, this month's spending, a group's balance, the latest bills (the sample bill, "Phở Thìn" and "Grab") and the navigation bar |
| 2 | `02-new-split.png` | 824 × 1830 | A new split: scan the bill, take a photo, pick one, use the sample bill or type it in by hand, and say how to split it |
| 3 | `05-result-top.png` | 824 × 1830 | The sample bill read: its total, what Split Vibe understood, and what each of the three people pays |
| 4 | `31-assistant-proposal.png` | 824 × 1830 | A change by a sentence, with each share before and after |
| 5 | `10-requests-sent.png` | 824 × 1830 | Asking for the money: the split as a ticket to share as a picture, and a notice to add a way to get paid |
| 6 | `23-group.png` | 824 × 3200, crop to the top 824 × 1830 | A group: what you get back, each member's balance, and the payments that settle it |
| 7 | `40-insights.png` | 824 × 5200, crop to the top 824 × 1830 | What the bills of the last 12 months came to, and by month. Its top also shows two notices, about bills without a rate or without a date that can be used. |

The light theme is shown; each has a dark twin (`-dark.png`), cropped the same way, if the owner
prefers it.

How they meet Play's rules:

- Play takes 2 to 8 phone screenshots, PNG or JPEG up to 8 MB each, each side between 320 and
  3,840 px, the long side at most 2.3 times the short one. The 824 × 1830 images, about 70 to 150
  KB each, meet all of this. The taller whole-page images do not, which is why 6 and 7 are cropped
  to the same height.
- For the app to be shown in Play's recommendations, Play asks for at least 4 screenshots of at
  least 1080 px, 1080 × 1920 or more in portrait. These are 824 px wide, so they qualify for the
  listing but not for that. Rendering store copies at 1080 × 1920 is a change to the screenshot
  tests' device size; the owner decides whether it is worth it.
- The images have no status bar and no tagline, as Play recommends.
- Before publishing, check that the sample data is fictional: the sample bill names a restaurant,
  an address and a phone number in Ho Chi Minh City.
- Left out on purpose: `28-bill-link.png` and `29-bill-link-settling.png` show the test address
  `https://proxy.example/s/#AAAA…`, a closing date of 1 January 2100 and an example account number,
  which do not belong on a store page without new recordings.
- No image with a QR code, an email address, an account number, or the sample bill's address or
  phone number.

## Other assets

Play also asks for an app icon (512 × 512 px, 32-bit PNG with alpha, up to 1,024 KB) and a feature
graphic (1,024 × 500 px, JPEG or 24-bit PNG without alpha). Neither is in the repository yet. Play
recommends that the feature graphic carry no words of ranking or price such as "Best", "#1" or
"Free".

## Category and contact

- Category: **Finance**. Play describes it as "Banking, payment, ATM finders, financial news,
  insurance, taxes, portfolio/trading, tip calculators". Splitting bills and asking for payments
  fits it best of Play's categories; Productivity is the alternative. Tags are chosen in Play
  Console.
- Contact email: `<contact email>`, which Play requires. A website (`https://<the proxy's host>`) and
  a phone number are optional.
- Privacy policy: `https://<the proxy's host>/privacy` ([play-console.md](play-console.md#privacy-policy)).
