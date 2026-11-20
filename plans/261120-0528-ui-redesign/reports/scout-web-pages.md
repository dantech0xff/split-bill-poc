# Web link pages: inventory of what exists today

Scope: read-only inventory of `web/` (the Preact app) and the Worker pieces that serve it (`proxy/src/web.ts`, `proxy/src/index.ts`, `proxy/scripts/embed-web.mjs`). Line references are `path:line`. The Worker host is written as `<worker host>`. No file was created or changed, and no build, npm or test command was run.

## 1. Relevant files and how routes resolve

| Route a visitor sees | Served by (Worker) | Page component | Resolution |
|---|---|---|---|
| `/s/#<token>` bill link | `proxy/src/index.ts:187` `["/s/", page(webPageRoute)]`, exact `/s/` only | `web/src/pages/share-page.tsx` `SharePage` (line 69), `ShareView` (line 27) | `web/src/main.tsx:11-22` picks `App`; `web/src/app.tsx:38` calls `routeOf` |
| `/r/#<token>` payment request | `index.ts:188` | `web/src/pages/request-page.tsx` `RequestPage` (65), `RequestPageView` (57), `RequestDetails` (22) | `web/src/route.ts:6` `PATHS` map |
| `/g/#<token>` group invite | `index.ts:189` | `web/src/pages/invite-page.tsx` `InvitePage` (42), `InvitePageView` (34), `InviteDetails` (9) | `route.ts:6` |
| `/delete-account` | `index.ts:193` `page(deleteAccountPageRoute)` | `web/src/pages/delete-account-page.tsx` `DeleteAccountPage` (280) | `main.tsx:11` compares the path to `DELETE_ACCOUNT_PATH` (`delete-account-page.tsx:8`) |
| `/privacy`, `/terms` | `index.ts:191-192` | Static HTML from `web/scripts/static-pages.mjs` | Worker returns the embedded string (`proxy/src/web.ts:124-132`) |
| Asset `/assets/<name>` | `index.ts:190` | Hashed `.js` / `.css` | `web.ts:152-158` |
| Index / not-found | No `/` route. Any other path gets `errorResponse(404,"path")` (`index.ts:217`), a JSON body `{"error":"path"}` (`proxy/src/respond.ts:14-19`) | Not a page | |
| Unknown fragment | `/s/`, `/r/`, `/g/` with no 22-character token in `#` | `ProblemView` kind `not_found` (`web/src/app.tsx:45-46`) | `route.ts:4` `TOKEN = /^[A-Za-z0-9_-]{22}$/` |
| App-level unknown path | Unreachable through the Worker; `App` falls back to `not_found` (`app.tsx:39,45`) | | |

Supporting files:
- `web/index.html` (the shell): `<title>Split Vibe</title>`, meta tags `sv-turnstile-site-key`, `sv-abuse-email` (empty), `<noscript>Split Vibe needs JavaScript to show this link.</noscript>`, and `<div id="app">`.
- `web/src/app.tsx`: header, `<main class="page">`, footer (`app.tsx:62-72`).
- `web/src/route.ts`, `web/src/links.ts`, `web/src/page-problem.ts`, `web/src/page-meta.ts`, `web/src/share-controller.ts`, `web/src/share-state.ts`, `web/src/turnstile.ts`, `web/src/google-sign-in.ts`, `web/src/live.ts`, `web/src/claim-store.ts`, `web/src/api.ts`, `web/src/contract.ts`, `web/src/money.ts`, `web/src/dates.ts`.
- `web/src/pages/share-sections.tsx`, `web/src/pages/use-load.ts`, `web/src/pages/app-link.ts`.
- `web/src/ui/`: `problem-view.tsx`, `payment-block.tsx`, `qr-code.tsx` (uses the `lean-qr` package), `open-in-app.tsx`, `footer.tsx`, `site-footer.tsx`, `icons.tsx`.
- Document titles, set in `app.tsx:11-15,41`: `Shared bill · Split Vibe`, `Payment request · Split Vibe`, `Group invite · Split Vibe`, `Split Vibe`. The title follows `route.kind`, so `/s/` with no token shows the title `Shared bill · Split Vibe` on the not-found view.

Share-controller behaviour that changes what the page shows (`web/src/share-controller.ts`):
- Opens with the live WebSocket and falls back to `open` after 3 s (`OPEN_FALLBACK_MS`, line 92).
- Polls every 3 min (`READ_WHILE_DISCONNECTED_MS`, line 94).
- Gives up for good on `gone` or `not_found` (`finish`, line 422).
- Turnstile is loaded only on the first claim (`turnstile.ts:33-35`).

## 2. States and exact copy

Conventions: `·` is a middle dot, `…` an ellipsis, `₫` the dong sign. Text inside `[brackets]` is data. Quoted strings are exact. "Owner" means `ownerInSentence` (`share-state.ts:30`): the owner's name, or `the person who made this link` when the owner left it empty (`share-state.ts:27`).

### 2.1 Bill link `/s/#<token>` (`share-page.tsx`, `share-sections.tsx`)

**S-A. Loading** (`share-page.tsx:33`)
- `<section class="loading" aria-busy="true">` with `h1.type-title-large`: `Opening the bill…`
- Then `div.loading__bar` (animated gradient, `app.css:282-297`).
- Title `Shared bill · Split Vibe`.

**S-B. Failed (not found, gone, network, rate limited, unexpected)** (`share-page.tsx:30-31`): the `ProblemView`, see section 2.4.
- A `gone` or `not_found` read also appears here with `share === null`.
- Also `claimOf` (`share-state.ts:11`) gives the claim.

**S-C. Open (`share.status === "open"`)** (`share-page.tsx:36-57`), top to bottom:
1. `section.hero[aria-labelledby=bill-title]`
   - `h1#bill-title.type-title-large.hero__title`: merchant, or `Shared bill` if none.
   - `p.type-body-medium`: `<Mon D, YYYY> · <n> items` or just `<n> items`. Singular: `1 item` (`share-sections.tsx:32-33`). Example: `Nov 14, 2026 · 3 items`.
   - `p.type-title-small.hero__label`: `Bill total`
   - `p.type-display-small.hero__amount`: total, e.g. `₫231,000`.
   - There is no status banner while open (`StatusBanner` returns null, `share-sections.tsx:47`).
2. If the bill changed since the last view: `div.notice.notice--action` with `p.type-body-medium` `The bill was updated. Check that your picks are still right.` and `button.button.button--text` `OK` (dismisses the notice).
3. `section.section[aria-labelledby=claim-title]` (`ClaimForm`, `share-sections.tsx:87-144`):
   - `h2#claim-title.type-title-medium`: `Who are you?`
   - `label[for=claim-name].type-body-small.text-field__label`: `Your name`
   - `input#claim-name.text-field__input.type-body-large`: type text, `maxLength=40`, `autocomplete=nickname`, `autocapitalize=words`, `enterKeyHint=done`, no placeholder. Typing calls `setName`, `change` calls `commitName`, Enter blurs.
   - `p#claim-help.type-body-medium.supporting`: `Type your name, then tap what you had. Everyone with this link sees your picks.`
   - If the picks are not kept beyond this page (`persistent` false): `p.notice.type-body-medium`: `Your picks are kept only while this page stays open. Keep it open until the bill is settled.`
   - If the owner removed this browser's picks (`removedByOwner`): `p.notice.type-body-medium`: `[Owner, capitalised] took your picks off the bill. Tap what you had to pick again.` Example: `Lan took your picks off the bill. Tap what you had to pick again.`
   - `div.human-check` (Turnstile widget; hidden via `:empty`, `app.css:386-388`). Turnstile's own UI draws inside it and is not our copy.
   - `p.type-body-medium.supporting.claim-progress` (hidden when empty): `Checking that you are a person…` while checking, `Saving your picks…` while saving.
   - If there is a claim problem: `p#claim-problem.error-text.type-body-medium[role=alert]` with the text in section 2.1a.
4. `section.section[aria-labelledby=items-title]` (`ItemsSection`, `share-sections.tsx:195-229`):
   - `h2#items-title.type-title-medium`: `Items`
   - `p.type-body-medium.supporting`: `Tap what you had. Several people can share an item.`
   - `ul.group` of `li.group__item`, each with `button.item.item--toggle[aria-pressed=true|false]`:
     - `span.item__check` (`CheckIcon` when picked)
     - `span.item__name.type-body-large`: `<qty> × <name>`, where the prefix is omitted when quantity is null or `1`. Example: `2 × Pho bo`, `Spring rolls`.
     - `span.item__amount.type-title-medium`: `₫130,000`
     - `span.item__claimers` (only when someone picked it): a visually hidden `, picked by <list>`, then `span.chips[aria-hidden]` with `span.chip.chip--you` `You` (when mine) and one `span.chip.type-label-medium` per other person's name.
5. `section.section[aria-labelledby=totals-title]` (`TotalsSection`, `share-sections.tsx:231-251`):
   - `h2#totals-title.visually-hidden`: `Charges and total`
   - `ul.group`: one `li.group__item.row` per charge, with label and amount, e.g. `Service charge` / `₫21,000`.
   - `li.group__item.row.row--total`: `Total` / `₫231,000`.

**S-D. Locking (`share.status === "locking"`)**
- `p.banner.type-body-large`: `Picks are closed: [Owner] is settling the split.` Example: `Picks are closed: Lan is settling the split.`
- Items are shown without buttons (`div.item`, read-only, with claimer chips). No claim form, no amounts, no claimers section.
- Then the totals section as in S-C.5.

**S-E. Locked (`status === "locked"`)**
- `p.banner.type-body-large`: `The split is settled. Each person's amount is below.`
- `AmountsSection` (`share-sections.tsx:276-335`):
  - `h2#amounts-title.type-title-medium`: `What each person pays`
  - `p.type-body-medium.supporting`: `Tap your name to see how to pay.` (only when more than one person has a payment)
  - `ul.group` of `li.group__item.person` (plus `.person--you` on this browser's own row, which gets a `secondary-container` background, `app.css:568-571`):
    - With a way to pay: `details.person__details` (open by default when there is exactly one such person) with `summary.person__row`: `span.person__name` (label plus optional You chip), `span.person__amount` (`₫86,500`), and `span.person__expand` with a chevron. Then `PaymentBlock` (see 2.4).
    - Without a way to pay: `div.person__row` with the name, then `p.person__note.type-body-medium.supporting`, then the amount. Notes, exact:
      - `Paid the bill` (the person who paid the whole bill)
      - `Shared this bill` (owner row)
      - `Nothing to pay` (amount 0)
      - `No way to pay was added: settle up directly with whoever paid the bill.` (nobody named as payer)
      - `No way to pay was added. [Payer] paid the bill: settle up with them directly.` Example: `No way to pay was added. Minh paid the bill: settle up with them directly.`
    - Name label (`participantLabel`, `share-state.ts:60-64`): the name, or `The bill's owner` for the owner with an empty name, or `Someone`.
- `ClaimersSection` (`share-sections.tsx:342-365`), only when at least one claim is matched:
  - `h2#claimers-title.type-title-medium`: `Who picked`
  - `li.group__item.claimer` per claim: `span.claimer__name` (name plus `You` chip for this browser), `span.type-body-medium.supporting`: `On the bill as [name]` or `Not on the bill`. Example: `On the bill as Minh`.
- Then the items (read-only) and totals.

**S-F. Live region** (`LiveRegion`, `share-sections.tsx:367-373`): `div.visually-hidden[aria-live=polite][aria-atomic=true]`. It announces changes by others (`describeChanges`, `share-state.ts:95-119`). Sentence templates:
- `[Name] picked [items].` Example: `Minh picked Iced coffee.`
- `[Name] put back [items].`
- `[Name] joined.`
- `[Old] is now [New].`
- `[Name] is no longer on the bill.`
- `The bill was updated.`
- Status sentences (`share-state.ts:66-75`): `Picks are open again.` / `Picks are closed: [Owner] is settling the split.` / `The split is settled. Each person's amount is below.`
- More than 4 sentences: the first 4 plus `There are more changes.`

**2.1a. Claim problem texts** (`claimProblemText`, `share-sections.tsx:52-79`), shown in `p#claim-problem[role=alert]`:
- `name_missing`: `Type your name first, then tap what you had.` (also moves focus to `#claim-name` and sets `aria-invalid=true`)
- `name_too_long`: `Use a name of at most 40 characters.`
- `name_invalid`: `Your name has characters that cannot be used. Type it again.`
- `human_check_failed`: `The check that you are a person could not finish. Tap your item again to retry.`
- `turnstile`: `The check that you are a person did not go through. Tap your item again to retry.`
- `full`: `This bill already has 30 people picking items. Ask [Owner] to add you.`
- `locked`: `Picks are closed: [Owner] is settling the split.`
- `rate_limited`: `Too many picks from this network. [wait]` where wait is `Try again in N seconds.` (under 90 s), `Try again in N minutes.` (rounded up), or `Try again in a few minutes.` (no Retry-After) (`problem-view.tsx:12-16`)
- `not_configured`: `Picking items does not work right now. You can still see the bill.` (no site key)
- `input`: `That pick could not be saved. Check your name, then try again.`
- `network`: `Your pick was not saved: check your connection, then try again.`
- `unexpected`: `Your pick was not saved. Try again in a moment.`

**Signed-out and signed-in:** there is no account on the link pages. "You" always means this browser's own claim, marked by the `You` chip. The `sv.claim.<sha256>` record is in localStorage (`claim-store.ts:18`), or in memory when storage is refused.

**Footer on link pages** (`footer.tsx`, `app.tsx:70`): `footer.footer.type-body-small`:
- `p`: `Split Vibe never holds or moves money.`
- If the abuse address is valid: `p` > `a.footer__link` `Report abuse`, with `href` `mailto:<address>?subject=Report%20abuse%3A%20Split%20Vibe%20<bill link | payment request link | group invite link>`. The address must match `links.ts:53`. Example from tests: `mailto:abuse@example.com?subject=Report%20abuse%3A%20Split%20Vibe%20bill%20link`.

### 2.2 Payment request `/r/#<token>` (`request-page.tsx`)

**R-A. Loading**: `section.loading` with `h1.type-title-large`: `Opening the payment request…`, plus the loading bar.

**R-B. Failed**: `ProblemView` kind `request` (section 2.4).

**R-C. Details** (`RequestDetails`, lines 22-55), top to bottom:
1. `section.hero[aria-labelledby=request-title]`:
   - `h1#request-title.type-title-medium.hero__title`: `[Creditor] asks [Payer] for`. Example: `Lan asks Minh for`.
   - `p.type-display-small.hero__amount`: amount, e.g. `₫150,000`.
   - `p.type-body-large` (only when the title is not empty): `For [title]`. Example: `For Hot pot`.
   - `p.type-title-small.hero__status`: one of:
     - open: `Not paid yet`
     - reported: `[Payer] says it is paid. [Creditor] has yet to confirm it.` Example: `Minh says it is paid. Lan has yet to confirm it.`
     - confirmed: `Paid and confirmed`
2. Body:
   - If confirmed: `p.type-body-large.supporting`: `Nothing is left to pay on this request.`
   - Else if the payment is present (this includes reported): `section[aria-labelledby=pay-title]` with `h2#pay-title`: `How to pay`, then `PaymentBlock` (section 2.4).
   - Else: `p.type-body-large.supporting`: `Ask [Creditor] how to pay.`
3. `section[aria-labelledby=app-title]`: `h2#app-title`: `Keep track in Split Vibe`. `p.type-body-medium.supporting`: `Split Vibe can tell [Creditor] that you paid, and remind you while it is open.` Then `OpenInApp main=false` (section 2.5).

No live region, no abuse footer change.

**R-D. Sample values**: `tests/fixtures/request-open.json`: creditor `Lan`, payer `Minh`, amount `₫150,000`, title `Hot pot`, currency VND, with a UPI link and details `UPI: lan@okbank\nAmount: ₹1,500.00`. The e2e spec uses a PayPal link, `Pay on paypal.me`, and details `PayPal: paypal.me/lan\nAmount: ₫150,000`.

**R-E. Problem copy for the request** (`app`-level `GONE.request`, `problem-view.tsx:7`): `The person who asked withdrew this request, or the link's 90 days are over. If you kept it in your requests in Split Vibe, it is still there.`

### 2.3 Group invite `/g/#<token>` (`invite-page.tsx`)

**I-A. Loading**: `Opening the invite…` in `h1.type-title-large`.

**I-B. Failed**: `ProblemView` kind `invite`. Gone text: `The invite was turned off or has run out. Ask the group for a new one.`

**I-C. Details** (`InviteDetails`, lines 9-32):
1. `section.hero[aria-labelledby=invite-title]`:
   - `p.type-title-small.hero__label`: `You are invited to join`
   - `h1#invite-title.type-headline-small.hero__title`: the group name, e.g. `Chiang Mai trip`
   - `p.type-body-medium`: `Balances in [currency code]`, e.g. `Balances in THB`
2. `section[aria-labelledby=join-title]`: `h2#join-title.type-title-medium`: `How to join`. `ol.steps.type-body-large` with 3 items:
   1. `Get Split Vibe from Google Play on your Android phone.`
   2. `Open this invite link again on that phone, or tap Open in Split Vibe there.`
   3. `Choose the name the group knows you by, and join.`
3. `OpenInApp main=true` (`button--filled`).

No live region, no claim, no Turnstile.

### 2.4 Shared components

**ProblemView** (`ui/problem-view.tsx:40-55`): `section.problem[aria-labelledby=problem-title]`: `h1#problem-title.type-headline-small` (title), `p.type-body-large.supporting` (text), and `button.button.button--tonal` `Try again` (only when retry is offered).

| Kind | Title | Text | Retry button |
|---|---|---|---|
| not_found | `This link does not exist` | `Check that you have the whole link, exactly as it was sent to you.` | no |
| gone | `This link no longer works` | Per page kind: share `The person who shared the bill turned the link off, or its 30 days are over.` / request (2.2) / invite (2.3) | no |
| rate_limited | `Too many requests just now` | waitText (see 2.1a) | yes |
| network | `Split Vibe could not be reached` | `Check your connection, then try again.` | yes |
| unexpected | `Something went wrong` | `Try again in a moment.` | yes |

Mapping (`page-problem.ts:12-23`): `not_found`, `gone` and `network` pass through. `rate_limited` carries its Retry-After seconds. Other errors become `unexpected`.

**PaymentBlock** (`ui/payment-block.tsx:45-94`): `div.payment`:
1. `p.type-body-medium.supporting`: `The person who sent this link wrote these payment details. Split Vibe does not check them.` When an abuse address is set: a space and `a` `Report abuse` (same mailto as the footer).
2. If the payment link is safe (`safePaymentLink`, `links.ts:16-28`: `https://` with a host and no spaces or control characters, or `upi://pay?`): `a.button.button--filled[target=_blank][rel="noopener noreferrer"]` with an external-link icon and text `Pay on [hostname]` (example `Pay on paypal.me`) or `Pay with a UPI app` (UPI).
3. If there is a QR payload: `figure.qr-card`: `canvas.qr[role=img][aria-label="QR code to pay [amount]"]` (black on white, one pixel per module, 4-module quiet zone, scaled to `min(100%, 240px)`, `image-rendering: pixelated`). `figcaption.type-body-medium`: `Scan it in your banking app.` If the QR cannot be drawn: `p.type-body-medium.supporting`: `The QR code could not be drawn here. Use the details below.`
4. `div.details-card`: `p.details-card__text` (the details as written, `white-space: pre-line`), `button.button.button--text` with a copy icon and `Copy the details`, then `p.type-body-small.supporting[role=status]` with one of:
   - `Copied.`
   - `The details are selected: copy them from your browser's menu.`
   - `Copy the details by hand.`
5. `p.type-body-medium.supporting`: `The amount, [amount], is filled in for you.` or `Type in the amount yourself: [amount].`

Sample (tests): details `Bank: ACB (970416)\nAccount: 257678859\nName: LAN NGUYEN\nAmount: ₫67,500`, which is not filled in, so it reads `Type in the amount yourself: ₫67,500.`

**OpenInApp** (`ui/open-in-app.tsx:11-30`):
- Android (`/\bAndroid\b/i` on the user agent): `a.button` `Open in Split Vibe`, with the `intent:` href (section 7).
- Elsewhere: `a.button[target=_blank][rel="noopener noreferrer"]` `Get Split Vibe on Google Play`, then `p.type-body-small.supporting`: `Split Vibe is an app for Android phones.`
- Class: `button--filled` when `main`, else `button--tonal`.

**Footer on link pages**: see 2.1.

### 2.5 /delete-account (`delete-account-page.tsx`)

Page frame: `header.app-bar` > `p.app-bar__brand` `Split Vibe`, then `main.page` with the sections below, then `SiteFooter` (section 2.7). Document title `Delete your account · Split Vibe` (from the HTML head, `static-pages.mjs:192`; for the SPA it is the `index.html` title `Split Vibe` set in `main.tsx`).

**Top section (always shown)**: `section.hero[aria-labelledby=delete-title]`:
- `h1#delete-title.type-headline-small.hero__title`: `Delete your Split Vibe account`
- `p.type-body-large`: `Deleting your account removes what Split Vibe's server keeps about you, apart from what is listed under What stays. It cannot be undone.`

`section[aria-labelledby=deleted-title]`, `h2#deleted-title` `What is deleted`, `ul.bullets.type-body-large` (6 items, exact):
1. `Your account, your Pro plan as Split Vibe knows it, and your purchase history at RevenueCat, which handles Pro purchases for Split Vibe.`
2. `Your place in groups. You show as "Former member", and every amount stays as it was, so nobody else's balance changes. A group you own passes to another member.`
3. `How to pay you, on the payment requests you made that are not confirmed yet.`
4. `The bill links you shared, which stop working, and what people picked on them.`
5. `The phones that get Split Vibe's notifications for you.`
6. `Your sign-in on every phone. A phone still signed in to the account is signed out: Split Vibe's server no longer accepts its sign-in, so it cannot use the account until you sign in again.`

`section[aria-labelledby=kept-title]`, `h2#kept-title` `What stays`, `ul.bullets` (6 items, exact):
1. `Bills, people and payment methods on your phone stay on your phone. To delete them too, delete your account in the app and tick "Also delete the bills, people and payment methods on this phone", or uninstall Split Vibe.`
2. `A Pro subscription goes on until you cancel it in Google Play: deleting your account does not cancel it. ` followed by the link `Manage your subscriptions on Google Play` (`a` to `https://play.google.com/store/account/subscriptions`, `rel="noopener noreferrer"`, `target=_blank`).
3. `How many AI calls your account used this month, until the month ends, so that deleting the account and signing in again does not start them over.`
4. `When your account was deleted, for 30 days, so that phones still signed in to it are signed out.`
5. `Counts of what your account did in the last hour, until the next daily clean-up.`
6. `The bills and settlements of your groups, so that their balances stay right, and the payment requests between you and others, for the other person.`

**Deletion section** (`Deletion`, lines 125-272), one of these by step:

Without a client ID (`clientId === ""`, `delete-account-page.tsx:288-294`): `section` with `h2#action-title`: `Delete your account`, then `p.notice.type-body-large`: `Deleting your account on the web is not available yet. You can delete it in the app, as below.`

With a client ID, step `sign_in` (`expired` false):
- `h2#action-title.type-title-medium` (focusable, `tabIndex=-1`): `Delete your account`
- `p.type-body-large`: `Sign in with the Google account you use in Split Vibe. You can still change your mind after that.`
- While Google loads: `p.type-body-medium.supporting`: `Loading Sign in with Google…`
- If Google fails to load: `p.error-text[role=alert]`: `Sign in with Google could not be loaded. Check your connection, then try again, or delete your account in the app.` and `button.button.button--tonal` `Try again`.
- `div.google-button` (min-height 44px): Google's own button (`renderButton`: standard, outline, large, `signin_with`, pill, left logo). Its label comes from Google, not from our copy.
- With the sign-in expired after a deletion attempt: `p.error-text[role=alert]`: `Your sign-in with Google ran out before the account was deleted. Sign in again.`

Step `confirm`:
- `h2#action-title`: `Delete this account?`
- `p.type-body-large`: `You signed in as [email].` or `You signed in with Google.`, then ` Deleting the account cannot be undone. It signs out every phone still signed in to it, and it does not cancel a Pro subscription: cancel that on Google Play.`
- `div.actions`: `button.button.button--danger` `Delete my account` and `button.button.button--text` `Cancel`.

Step `deleting`:
- Same heading. `section[aria-busy=true]`.
- Button `Deleting your account…` (disabled), and `Cancel` (disabled).

Step `failed`:
- `h2#action-title`: `Your account was not deleted`
- `p.error-text`: per error (below). Then `button.button.button--tonal` `Try again` and `button.button.button--text` `Cancel`.
- Failure texts (`delete-account-page.tsx:36-49`):
  - rate_limited: `This account was asked to be deleted too many times in the last hour. [wait]`
  - subscription_unavailable: `The service that keeps Pro purchases for Split Vibe did not answer, so nothing was deleted. Try again in a few minutes.`
  - sign_in_off: `Deleting your account on the web is not available right now. You can delete it in the app, as below.`
  - network: `Split Vibe could not be reached, and nothing was deleted. Check your connection, then try again.`
  - unexpected: `Something went wrong, and nothing was deleted. Try again in a moment.`

Step `deleted`:
- `h2#action-title`: `Your account is deleted`
- `p.type-body-large`: `Split Vibe's server has deleted your account, apart from what this page lists under What stays. The bills, people and payment methods on your phone are still there until you delete them in the app or uninstall it.`
- `p.type-body-large`: `Phones that were still signed in to the account are now signed out: Split Vibe's server no longer accepts their sign-in, so they cannot use the account until you sign in again.`
- `p.type-body-large`: `If you have Pro, cancel the subscription on Google Play: deleting your account does not cancel it. ` plus the `Manage your subscriptions on Google Play` link.
- The heading takes focus on each step change (`delete-account-page.tsx:167-170`).

Then `InTheApp`: `section[aria-labelledby=in-app-title]`, `h2#in-app-title.type-title-medium`: `Delete it in the app`, and `ol.steps.type-body-large`:
1. `Open Split Vibe on your phone.`
2. `Go to Settings.`
3. `Tap Delete account.`

Sign-in state: signed out (`sign_in`) and signed in (`confirm`) are the only two account states on the web. The Google token is sent to `POST /v1/account/delete-with-google` as `{ idToken }`, with no referrer (`api.ts`, tests in `api.test.ts:72`).

### 2.6 Privacy and terms (`/privacy`, `/terms`) (section 3)

Their frame: `header.app-bar` (brand `Split Vibe`), `main.page.prose`, then the static footer (section 2.7). No Report abuse link on these pages.

### 2.7 Footers

- Link pages: section 2.1.
- `/delete-account` and the legal pages use `SiteFooter` (`ui/site-footer.tsx:9-25`, and the same markup in `static-pages.mjs:138-150`): `footer.footer.type-body-small` > `nav[aria-label="Split Vibe"]` > `ul.footer__links` with three links, `a.footer__link`:
  - `Privacy policy` `/privacy`
  - `Terms of use` `/terms`
  - `Delete account` `/delete-account`
  - The current page gets `aria-current="page"`.
- The "never holds or moves money" sentence and the abuse link appear only on link pages.

## 3. Legal pages

Sources: `docs/privacy-policy.md` (247 lines, 5,156 words) and `docs/terms.md` (139 lines, 2,199 words). Both are in the repository's `docs/` folder, outside `web/`.

Build: `web/scripts/static-pages.mjs`.
- `LEGAL_PAGES` (lines 28-31) maps `privacy.html` / `privacy-policy.md` / `/privacy` / `Privacy policy`, and `terms.html` / `terms.md` / `/terms` / `Terms of use`.
- Markdown is rendered with `marked` (`Marked({gfm:true})`) and custom renderers (lines 97-135).
  - Headings get GitHub-style ids (`slugOf`, lines 70-80).
  - `.md` links are rewritten to `/privacy` or `/terms` (`hrefOf`, lines 57-63).
  - Any other HTML is escaped to text.
  - Images, unknown links and any HTML comment other than a leading note stop the build.
- `legalPage` (lines 171-188) builds the document: `<title>[Title] · Split Vibe</title>`, the `index.html` stylesheet tag, `header.app-bar`, `main.page.prose` with the rendered content, and the footer. If the Markdown has no `h1`, the title is inserted as one.
- Output has no script. The `page-checks` `legal` kind rejects scripts, `src` attributes and any link other than web, mailto, `#section` or Worker path (`page-checks.mjs:52-77`).
- The source's first `<h1>` is used as shown. Privacy's `h1` is `Split Vibe Privacy Policy`; terms' is `Split Vibe Terms of Use`.

Structure (headings only, quoted from `docs/`):

Privacy: 1 `h1` and 17 `h2`, of which `Who is responsible` appears under the summary, plus 13 `h3` under `What leaves your phone, and why`. The `h2` headings in order: `In short`, `Who is responsible`, `What stays on your phone`, `What leaves your phone, and why` (its `h3`s: `Your consent before the AI is used`, `Reading a bill with AI`, `Changing a split by a sentence`, `Your free calls and the app's version`, `Signing in with Google`, `Groups`, `Payment requests`, `Bills you open for picking`, `Push notifications`, `Pro`, `Exchange rates`, `Reading text and scanning on the phone`, `Crash reports`), `People who open a link without the app`, `Who processes your data for us`, `Where your data is stored`, `Transfers to other countries`, `How long we keep it`, `Counting how Split Vibe is used`, `Deleting your account`, `Your rights`, `Why we may use your data`, `Children`, `Security`, `Changes to this policy`, `Contact`.

Terms: 1 `h1` and 16 `h2`, no `h3`: `Who can use Split Vibe`, `What Split Vibe does`, `AI results may be wrong`, `Split Vibe holds and moves no money`, `Your account`, `Pro`, `Acceptable use`, `Your content`, `Availability and changes`, `Ending your use`, `Our liability`, `Other services`, `Governing law and disputes`, `Changes to these terms`, `General`, `Contact`.

Output shape:
- `h1` with an id, then `h2` with ids, `h3` with ids (privacy only).
- Paragraphs, unordered and ordered lists, and nested lists.
- Blockquotes, inline code and fenced code (`pre code`).
- Links: to `/terms`, `/privacy`, `/delete-account` (privacy `:194`, terms `:50`), external web links (Cloudflare, Google, RevenueCat, Anthropic), `mailto:` and `#` section links. Links are shown with their own text.
- No tables, lists of images, or `<script>`. The `prose` styles include tables, but the real sources have none at the start of a line (`^\|`). Table styles apply only to the test fixture.

Tests of the legal pages: `web/tests/static-pages.test.ts` binds to the heading ids and to fixture strings (see section 6).

Sample output of `legalPage`: `<title>Privacy policy · Split Vibe</title>` and `<h1 id="privacy-policy">Privacy policy</h1>` (fixture; the real `h1` comes from the source).

## 4. Styles

Files: `web/src/app.css` (832 lines, 1,517 words) and `web/src/tokens.css` (195 lines, generated by `WebTokensTest` from the app's design system and marked not to be edited by hand, line 1).

**Layout**
- `body`: `margin: 0; min-width: 320px;` background `--sv-surface`, text `--sv-on-surface`, body type scale (app.css:17-28).
- `#app`: flex column, `min-height: 100vh; min-height: 100dvh` (`app.css:30-35`).
- Frame: `.app-bar, .page, .footer` (`app.css:163-170`): `width: 100%; max-width: 600px; margin-inline: auto; padding-inline: var(--sv-space-lg)`. Centred by `margin-inline: auto` in a 600 px column.
- `.page`: flex column with `gap: var(--sv-space-xl)` (24 px). Padding `16px` top and `32px` bottom (`app.css:180-186`).
- `.footer` centred column, text centred, padding 16 px top and 32 px bottom.
- No media queries except `prefers-reduced-motion` (`app.css:825-832`). Mobile-first; no breakpoints. Wide screens keep one 600 px column.

**Dark mode**: by `@media (prefers-color-scheme: dark)` only (`tokens.css:136-195`), with no manual toggle. `color-scheme: light dark` is set in `:root` (`tokens.css:3`) and in `index.html:8`. Dark mode also sets all elevations to `none` (`tokens.css:186-193`), so elevation is shown only in light mode.

**Component and utility classes** (purpose in one line):
- `*` reset: `box-sizing: border-box` (`app.css:6-10`).
- `.type-display-small`, `.type-headline-small`, `.type-title-large`, `.type-title-medium`, `.type-title-small`, `.type-body-large`, `.type-body-medium`, `.type-body-small`, `.type-label-large`, `.type-label-medium`: type scale (size, line-height, weight, tracking).
- `.supporting`: secondary text colour.
- `.visually-hidden`: off-screen text for screen readers, clip-path and 1 px.
- `.icon`: 24 px SVG.
- `.app-bar`, `.app-bar__brand`: top bar and brand line in primary colour.
- `.page`, `.footer`, `.footer__link` (48 px tap target), `.footer__links` (wrapping centred list), `.footer__link[aria-current="page"]` (muted, no underline).
- `.section`: flex column with `gap: var(--sv-space-md)`.
- `.hero`, `.hero__label`, `.hero__title`, `.hero__status`: primary-container card with `28px` radius and elevation 1.
- `.banner`: secondary-container message block (used for the picks-closed and settled status).
- `.notice`: tertiary-container message block.
- `.notice--action`: a `notice` with a flex row for text plus button.
- `.error-text`: error-container block with `role=alert` text.
- `.problem`, `.loading`: flex column for the problem and loading views.
- `.loading__bar`: 4 px animated gradient bar (1.2 s, `@keyframes loading`).
- `.button`: base button (48 px min height, 28 px radius, label-large type).
- `.button--filled`, `.button--tonal`, `.button--text`, `.button--danger`: variants (`--text` is primary-coloured, aligned start).
- `.button:disabled`: 60% opacity.
- `.text-field`, `.text-field__label`, `.text-field__input`: name field (56 px high, 1 px outline, 4 px radius); `[aria-invalid="true"]` uses the error colour.
- `.claim-progress:empty`, `.human-check:empty`: hidden when empty.
- `.group`, `.group__item`, `.row`, `.row--total`: grouped rows with 4 px inner and 20 px outer radius on first and last child (`app.css:392-430`).
- `.item`, `.item--toggle`, `.item--toggle[aria-pressed="true"]`: item rows (grid with name, amount and claimers). Pressed uses secondary-container.
- `.item__check`, `[aria-pressed="true"] > .item__check`: 28 px checkbox square.
- `.item__name`, `.item__amount`, `.item__claimers`: grid areas.
- `.chips`, `.chip`, `.chip--you`: name chips. `.chip--you` is primary-filled.
- `.person__details > summary`, `.person__row`, `.person__name`, `.person__amount`, `.person__expand`: summary rows for people, with a rotating chevron when open.
- `.person--you`: secondary-container row for this browser's own amount.
- `.person__label`, `.claimer__name`: name plus chip, wrapping.
- `.payment`, `.payment > .button--filled` (stretched).
- `.qr-card`, `.qr`: QR figure card (240 px max, pixelated).
- `.details-card`, `.details-card__text`: details card (pre-line, selectable).
- `.steps` (`ol`, 24 px start padding), `.bullets` (disc list).
- `.claimer`: row for "Who picked".
- `.actions`: wrapping button row.
- `.google-button`: 44 px minimum height so Google's button does not jump.
- `h2[tabindex="-1"]:focus`: no outline on focused step headings.
- `.prose` (and `.prose h1` to `h6`, `ul`, `ol`, `li > ul`, `blockquote`, `table`, `th`, `td`, `code`, `pre`, `pre code`, `hr`): legal page content styles.
- Classes used in JSX with no CSS rule: `hero__amount` (`request-page.tsx:30`, `share-sections.tsx:41`), `person` (the bare class, only `.person--you` is styled), and `person__note` (`share-sections.tsx:325`). They inherit their type classes only.

**`--sv-*` variables read by `app.css`** (grouped):
- Colour: `--sv-surface`, `--sv-on-surface`, `--sv-on-surface-variant`, `--sv-primary`, `--sv-on-primary`, `--sv-primary-container`, `--sv-on-primary-container`, `--sv-secondary-container`, `--sv-on-secondary-container`, `--sv-tertiary-container`, `--sv-on-tertiary-container`, `--sv-error`, `--sv-on-error`, `--sv-error-container`, `--sv-on-error-container`, `--sv-outline`, `--sv-outline-variant`, `--sv-surface-container`, `--sv-surface-container-high`, `--sv-elevation-1-surface`.
- Type: `--sv-font-family`; per style `--sv-type-<style>-size`, `-line-height`, `-weight`, `-tracking` for `display-small`, `headline-small`, `title-large`, `title-medium`, `title-small`, `body-large`, `body-medium`, `body-small`, `label-large`, `label-medium`.
- Shape: `--sv-shape-extra-small` (4 px), `--sv-shape-small` (8 px), `--sv-shape-medium` (12 px), `--sv-shape-large` (16 px), `--sv-shape-extra-large` (28 px), `--sv-shape-group-outer` (20 px), `--sv-shape-group-inner` (4 px).
- Elevation: `--sv-elevation-1` (light: `0 0 6px rgb(0 15 93 / 0.06), 0 1px 2px rgb(0 15 93 / 0.1)`, none in dark).
- Spacing: `--sv-space-xs` (4 px), `--sv-space-sm` (8), `--sv-space-md` (12), `--sv-space-lg` (16), `--sv-space-xl` (24), `--sv-space-xxl` (32).
- Unused by `app.css` but defined: `--sv-elevation-2`, `-3`, `-4`, `--sv-elevation-N-surface`, `--sv-type-*` for display-large, display-medium, headline-large, headline-medium, title styles not used, label-small, and the `--sv-*` colour roles `inverse-*`, `surface-tint`, `scrim`, and the `*-fixed` roles.

**Font-family stacks in use**
- `--sv-font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;` (`tokens.css:4`), used on `body` (`app.css:22`).
- `font-family: ui-monospace, monospace;` only on `.prose code` (`app.css:801`).
- The placeholder page uses `system-ui,sans-serif` (`proxy/scripts/embed-web.mjs:72`).
- No web font file, `@font-face` or font URL exists anywhere in `web/src`, `proxy/src` or the Worker.

## 5. Delivery constraints

**Worker responses** (`proxy/src/web.ts`):
- HTML pages (`pageResponse`, lines 98-109): `content-type: text/html; charset=utf-8`, `content-security-policy` (per page), `referrer-policy` (`no-referrer`, or `strict-origin-when-cross-origin` for `/delete-account`), `x-robots-tag: noindex, nofollow`, `x-content-type-options: nosniff`, `cache-control: no-store`.
- Assets (`webAssetRoute`, lines 152-158): `content-type` `text/javascript; charset=utf-8` or `text/css; charset=utf-8`, `cache-control: public, max-age=31536000, immutable`, `x-content-type-options: nosniff`. Unknown names return 404 JSON.
- HEAD is answered as GET with the same headers and no body (`index.ts:127-140`).
- `GET /.well-known/assetlinks.json` is served by another route (`index.ts:186`), not by the web code.

**Content-Security-Policy, exact** (`web.ts:57-68` and following):
- Link pages (`/s/`, `/r/`, `/g/`, `linkPagePolicy`):
  `default-src 'none'; script-src 'self' https://challenges.cloudflare.com; style-src 'self'; img-src 'self' data:; connect-src 'self' wss://<worker host>; frame-src https://challenges.cloudflare.com; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`
- Legal pages (`LEGAL_PAGE_POLICY`, `web.ts:72-74`):
  `default-src 'none'; style-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`
- `/delete-account` (`DELETE_ACCOUNT_PAGE_POLICY`, `web.ts:81-91`):
  `default-src 'none'; script-src 'self' https://accounts.google.com/gsi/client; style-src 'self' https://accounts.google.com/gsi/style; img-src 'self' data:; connect-src 'self' https://accounts.google.com/gsi/; frame-src https://accounts.google.com/gsi/; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`

Consequences for styles and fonts:
- `default-src 'none'` covers fonts. No `font-src` is set in any of the three policies, so a font would be blocked everywhere.
- Styles must be same-origin files. No inline `<style>`, no `style` attribute, and `style-src` has no `'unsafe-inline'`.
- Images: same-origin or `data:` only. The QR is drawn to a canvas.
- Connections: `self` and `wss://<worker host>` for link pages only.

**Build** (`web/vite.config.ts`, `web/package.json`):
- `npm run build` = `npm run typecheck && vite build && node scripts/static-pages.mjs && node scripts/verify-build.mjs`.
- Vite config: `base: "/"`, `build.outDir: "dist"`, `assetsDir: "assets"`, `assetsInlineLimit: 0` (no inlining, so every asset is a file), `modulePreload.polyfill: false`, `sourcemap: false`. The config comment says the Worker embeds `web/dist` and serves nothing else.
- Single entry `web/src/main.tsx`, which imports `tokens.css` and `app.css`. Hashed names come from Vite's defaults (the test fixture shows `index-<hash>.js` / `.css`); this inventory did not run a build to confirm.
- `static-pages.mjs` writes `privacy.html`, `terms.html` and `delete-account.html` into `web/dist`, reusing the `index.html` script and stylesheet tags (`assetTagsOf`, lines 205-212).
- `verify-build.mjs` rejects any file in `dist` other than the four pages and `assets/*.js|css` (regex at line 24), then runs `pageProblems` on each page.
- `proxy/scripts/embed-web.mjs` writes `proxy/src/web-assets.generated.ts` (git-ignored), with `WEB_PAGE`, `DELETE_ACCOUNT_PAGE`, `LEGAL_PAGES` and `WEB_ASSETS`. Workers Static Assets are not used (comment, lines 6-7). It inserts the Worker meta tags before `</head>` when missing (`withFilledTags`, lines 77-92) and refuses any `assets/` file that is not `.js` or `.css` (`assetsIn`, lines 113-124).

**Font check.** Could a font placed in the web build be served from the same origin under the current policy? No. Four things block it:
1. The policy has no `font-src`, so `default-src 'none'` blocks the font in all three policies (`web.ts:57-91`).
2. `embed-web.mjs` accepts only `.js` and `.css` in `assets/` (`CONTENT_TYPES` line 33, `ASSET_NAME` line 34, the throw at line 119). `verify-build.mjs:24` does the same.
3. `page-checks.mjs:66` allows a `<link>` only for an `/assets/` stylesheet or the empty icon, so a preload link would fail.
4. `webAssetRoute` has no font content type. The Worker serves only what `WEB_ASSETS` holds.

A font would need: `font-src 'self'` added to the three policies; a font content type and an asset-name rule in `embed-web.mjs` and `verify-build.mjs`; and an `@font-face` in `app.css` referencing `/assets/...`. Any `@font-face` in CSS is fetched under `font-src`, not `style-src`.

**`npm run size`** (`web/scripts/size.mjs`):
- Measures the initial download: `web/dist/index.html` plus each `/assets/` file it references through `src=` or `href=`. It prints raw and gzipped size per file at level 9, with KB shown as bytes/1000.
- Budget `BUDGET_BYTES = 150 * 1000` (line 8), i.e. 150 KB decimal. The process exits with 1 when the total gzipped size is at or over the budget (line 21).

**`web/scripts/page-checks.mjs`** checks each built page for the Worker's CSP:
- Only `/assets/*.js` scripts; no inline script (`pageProblems`, lines 51-59).
- No inline `<style>`, no `style` attribute, no inline event handler (lines 60-62).
- `<link>` only for an `/assets/*.css` stylesheet, or the empty icon `data:,` (lines 63-67).
- No reference with a scheme or another origin, except `data:,`.
- Legal pages: no script, no `src` at all, and links only to web, mailto, `#` or `/` paths (lines 69-77).
- Required tags for each kind (lines 18-34): `<html lang="en">`, `robots` noindex, `referrer` (`no-referrer`, or `strict-origin-when-cross-origin` for the deletion page), and the empty meta tags the Worker fills (`sv-turnstile-site-key`, `sv-abuse-email` on link pages; `sv-google-client-id` on the deletion page).
- Used by `verify-build.mjs`, `embed-web.mjs` and `tests/page-checks.test.ts`.

**Runtime and third-party origins**: `source-safety.test.ts:37-41` asserts that the only `https://` hosts in `web/src` are `accounts.google.com`, `challenges.cloudflare.com` and `play.google.com`. Turnstile loads on the first claim (`turnstile.ts:28`). Google Identity Services loads only on `/delete-account` (`google-sign-in.ts:36`).

## 6. Tests that pin the look or the copy

Counts are `it` blocks, and `it.each` counts as one. `describe` blocks are not counted.

**Component tests (JSDOM/happy-dom, `web/tests/`)**
- `app.test.tsx`: 6 blocks. Selects: `h1`, `footer`, `footer a`, `.payment > p`, `.payment > p a`, `document.title`. Text: `This link does not exist`, `Split Vibe never holds or moves money.`, `Report abuse`, `Chiang Mai trip`, `Pho 24`, `Group invite · Split Vibe`, `Shared bill · Split Vibe`, `The person who sent this link wrote these payment details. Split Vibe does not check them. Report abuse`. Also the WebSocket URL `ws://<host>/v1/shares/live` and its protocols.
- `request-invite-pages.test.tsx`: 10 blocks. Selects: `.hero`, `.hero__status`, `.payment a`, `.payment > p`, `.payment > p a`, `canvas[role="img"]`, `.details-card__text`, `.steps`, `h1`, `button`, and `a` by text. Text: `Lan asks Minh for ₫150,000 For Hot pot Not paid yet`, `Pay with a UPI app`, `QR code to pay ₫150,000`, `UPI: lan@okbank Amount: ₹1,500.00`, `The amount, ₫150,000, is filled in for you.`, `Get Split Vibe on Google Play`, `Open in Split Vibe`, `Lan asks Minh for $42.50 ...` (reported fixture), `Minh says it is paid. Lan has yet to confirm it.`, `Ask Lan how to pay.`, `Paid and confirmed`, `Nothing is left to pay on this request.`, `This link no longer works`, `The person who asked withdrew this request, or the link's 90 days are over.`, `If you kept it in your requests in Split Vibe, it is still there.`, `Opening the payment request…`, `Try again in 2 minutes.`, `Chiang Mai trip`, `You are invited to join Chiang Mai trip Balances in THB`, `Get Split Vibe from Google Play on your Android phone.`, `Ask the group for a new one.`
- `delete-account-page.test.tsx`: 10 blocks. Selects: `h1`, `h2` (all), `.hero p`, `[aria-labelledby="deleted-title"|"kept-title"|"action-title"] p`, `#action-title`, `.steps li`, `footer a` (text, href, `aria-current`), `.notice`, `.error-text`, `[role="alert"]`, `.google-button button`, `[aria-busy]`, `button` by text, `a[href=Play subscriptions URL]`. Text: all the deletion strings in section 2.5, `Loading Sign in with Google…`, `Delete this account?`, `You signed in as person@example.com.`, `Delete my account`, `Deleting your account…`, `Cancel`, `Your account was not deleted`, the five failure texts, `Try again in 30 minutes.`, `Your account is deleted`, `Delete it in the app`, `Manage your subscriptions on Google Play`.
- `payment-block.test.tsx`: 4 blocks. Selects: `.details-card button`, `[role="status"]`, `canvas`. Text: `Copied.`, `The details are selected: copy them from your browser's menu.`, `The QR code could not be drawn here.`.
- `share-page.test.tsx`: 23 blocks. Selects: `h1`, `.hero`, `.hero__amount`, `button[aria-pressed]`, `input#claim-name`, `label[for="claim-name"]`, `[aria-labelledby="totals-title"]`, `[role="alert"]`, `.banner`, `.item`, `.person`, `.person__row`, `.person--you`, `.person--you .person__row`, `.person .chip--you`, `.person__name .chip--you`, `.claimer`, `#claimers-title`, `details`, `summary`, `canvas`, `input`, `[aria-live="polite"]`, `button` by text (`OK`), `[aria-labelledby]`. Text: `Pho 24`, `Nov 14, 2026 · 3 items`, `₫231,000`, `2 × Pho bo ₫130,000 , picked by Minh and An Minh An`, `Charges and total Service charge ₫21,000 Total ₫231,000`, `Your name`, `Your picks are kept only while this page stays open.`, `This bill already has 30 people picking items. Ask Lan to add you.`, `Too many picks from this network. Try again in 30 seconds.`, `Type your name first, then tap what you had.`, `Picks are closed: Lan is settling the split.`, `The split is settled. Each person's amount is below.`, `What each person pays`, `Pay on paypal.me`, `The amount, ₫86,500, is filled in for you.`, `QR code to pay ₫67,500`, `Account: 257678859`, `Type in the amount yourself: ₫67,500.`, `No way to pay was added. Minh paid the bill: settle up with them directly.`, `Who picked`, `On the bill as Minh`, `Not on the bill`, `On the bill as the person who made this link`, `Opening the bill…`, `This link does not exist`, `This link no longer works`, `Split Vibe could not be reached`, `Minh picked Iced coffee.`, `The bill was updated.`, `Lan took your picks off the bill.`.
- `use-load.test.tsx`: 2 blocks. Selects: `p` and `button`, with a probe page (not product copy). Binds to retry timing: 429 with Retry-After, and 60 s default.

**Unit tests with no DOM selectors or product copy** (logic, strings only where listed):
- `links.test.ts`: 11 blocks (`it.each` x3). Binds to `safePaymentLink`, `openInAppHref` (the `intent://` format and the Play fallback), `isAndroid`, and `abuseMailto` (subject `Report abuse: Split Vibe bill link` etc.).
- `money.test.ts`: 7 `it` blocks. Binds to `formatMoney` output: `₫` dong without decimals, dollars with two decimals, dinars with three decimals, code before the number for unknown currencies, negative values, and exactness at the largest proxy amount.
- `share-state.test.ts`: 9 blocks. Binds to `describeChanges` sentences (see 2.1 S-F) and `participantLabel` / `statusSentence` strings: `The bill's owner`, `the person who made this link`.
- `share-controller.test.ts`: 28 blocks. Not read in detail; controller state transitions, not copy.
- `live.test.ts`: 15 blocks. Binds to the WebSocket protocol (`splitvibe.v1`), ping 30 s, backoff 1 s to 30 s with jitter, five failures.
- `turnstile.test.ts`: 7 blocks. Binds to the script URL, site key, action `claim`, `interaction-only` appearance.
- `claim-store.test.ts`: 7 blocks. Binds to `sv.claim.` + SHA-256 key, and to memory fallback.
- `api.test.ts`: 7 blocks. Binds to request bodies and to the `Retry-After` parse.
- `contract.test.ts`: 11 blocks. Parsing of fixtures.
- `google-sign-in.test.ts`: 4 blocks. Binds to `google.accounts.id`, the email from the token payload.
- `route.test.ts`: 3 blocks. Binds to `routeOf`.
- `page-checks.test.ts`: 6 blocks. Binds to `pageProblems` (see section 5).
- `static-pages.test.ts`: 12 blocks. Binds to legal page rendering and the errors for comments, images and links. Heading ids: `privacy-policy`, `what-we-keep`, `what-we-keep-1`, `deleting-your-account`, `terms-of-use`. Strings: `Privacy policy · Split Vibe`, `Terms of use`, and footer `Privacy policy` / `Terms of use` / `Delete account`. Fixtures in `web/tests/fixtures/legal/` (not the real docs).
- `source-safety.test.ts`: 7 blocks. Binds to the source text: no `innerHTML`, no `eval`, no `style=`, no `console.`, `localStorage` only in `claim-store.ts`, and the origin list in section 5.

**E2E specs** (Playwright, `web/e2e/*.spec.ts`; `web/playwright.config.ts` starts `wrangler dev` on port 8797, `workers: 1`):
- `share-link.spec.ts`: 2 tests.
  - Test 1 "everyone at the table picks live, then sees what to pay and how, until the link is revoked": `h1` `Pho 24`; `.hero__amount` `₫231,000`; contentinfo `Report abuse`; `button[aria-pressed]` (count 0 when locked); `getByLabel("Your name")`; `.banner` `Picks are closed: Lan is settling the split.`; `.item`, `.item__name`; `.person__row` texts `Lan Paid the bill ₫77,000`, `Minh ₫86,500`, `An ₫67,500`; `.person--you .person__label` `Minh You`; `h2` `What each person pays` and `Who picked`; `.claimer` texts `Minh You On the bill as Minh`; `summary` with `Minh` / `An`; link `Pay on paypal.me`; `canvas` role `img` `QR code to pay ₫86,500` and `₫67,500`; text `Account: 257678859`; `Type in the amount yourself: ₫67,500.`; `.payment > p:first-child` with the user-written notice; after revoke `h1` `This link no longer works`.
  - Test 2: `h1` `This link does not exist` for `/s/#AAAAAAAAAAAAAAAAAAAAAA`.
- `request-and-invite.spec.ts`: 3 tests.
  - Payment request: `h1` `Lan asks Minh for`; `.hero` `₫150,000 For Hot pot Not paid yet`; link `Pay on paypal.me` (`rel="noopener noreferrer"`); `canvas` `QR code to pay ₫150,000`; text `PayPal: paypal.me/lan`; button `Copy the details`; `The amount, ₫150,000, is filled in for you.`; `Open in Split Vibe` (intent href); `.payment > p:first-child` exact notice; `Get Split Vibe on Google Play` on iPhone; after delete, `This link no longer works` and `If you kept it in your requests in Split Vibe, it is still there.`
  - Invite: `h1` `Chiang Mai trip`; `Balances in THB`; `h2` `How to join`; first `listitem` `Get Split Vibe from Google Play on your Android phone.`; link `Open in Split Vibe`; after revoke `Ask the group for a new one.`
  - Assets: `/assets/` responses have `cache-control: public, max-age=31536000, immutable`; exactly one `.js` and one `.css`.
- `legal-and-deletion.spec.ts`: 3 tests.
  - Legal: title `Privacy policy · Split Vibe`; `h1` matches `/privacy policy/i`; `footer` links by name with `aria-current="page"`; `main.page` `display: flex`; no script on `/privacy`; `h1` `/terms of use/i`; `Delete account` link leads to `Delete your Split Vibe account`.
  - Deletion: `Sign in with Google` (faked), `A Pro subscription goes on until you cancel it in Google Play`, `meta[name="sv-google-client-id"]` ending in `.apps.googleusercontent.com`, `Delete this account?` (focused), `You signed in as person@example.com.`, `Delete my account`, alert `Your sign-in with Google ran out before the account was deleted. Sign in again.`, `Your account is deleted` (focused), `Manage your subscriptions on Google Play` link.
  - Third test: no request to Google's library except the first load, and `/g/` refuses it (CSP).

CSS class names selected by tests (unit and e2e, union): `.hero`, `.hero__amount`, `.hero__status`, `.payment`, `.payment > p`, `.payment > p:first-child`, `.payment a`, `.details-card`, `.details-card button`, `.details-card__text`, `.steps`, `.steps li`, `.bullets`? (not selected), `.notice`, `.banner`, `.error-text`, `.item`, `.item__name`, `.item__claimers` (not selected directly), `.person`, `.person__row`, `.person__label`, `.person__name` (`.person__name .chip--you`), `.person--you`, `.person .chip--you`, `.person__details`? (selected via `details`), `.claimer`, `.claimer__name`? (not selected), `.google-button`, `.loading`? (not selected), `.chip--you` (via `.person__label`), `.qr-card`? (not selected), `.qr`? (not selected), `.claim-progress` (not selected), `main.page`, `footer.footer` (via `contentinfo`), `#action-title`, `#bill-title`, `#claim-name`, `#claim-help` (not selected), `#claimers-title`, `#totals-title`, `#problem-title` (via `h1`), `[aria-labelledby=...]`, `[aria-pressed]`, `[aria-live]`, `[aria-busy]`, `[role=alert]`, `[role=status]`, `[role=img]`.

Exact texts selected by tests, consolidated: all strings quoted in sections 2.1-2.7 that appear above under "Text".

## 7. Links back to the app

- Constants: `web/src/links.ts:1-2`: `APP_PACKAGE = "com.dantech.splitvibe"`, `PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.dantech.splitvibe"`.
- `openInAppHref(link, android)` (`links.ts:41-45`):
  - Non-Android: the Play URL, without the token. Used by `OpenInApp`.
  - Android: `intent://<worker host><path>#<token>#Intent;scheme=https;package=com.dantech.splitvibe;S.browser_fallback_url=<encodeURIComponent(PLAY_STORE_URL)>;end`. The `path` is `/s/`, `/r/` or `/g/`. The fallback is the Play Store page.
- `OpenInApp` (`ui/open-in-app.tsx`): Android gets `Open in Split Vibe` (intent link). Other systems get `Get Split Vibe on Google Play` (Play link, new tab) and `Split Vibe is an app for Android phones.` The invite page's first step text tells the user to get the app on Android. The request page's text is `Split Vibe can tell [Creditor] that you paid, and remind you while it is open.`
- A visitor without the app on a non-Android phone sees only the Play button and the line `Split Vibe is an app for Android phones.`. On Android they see `Open in Split Vibe`, which falls back to Play if the app is not installed. The bill link page has no app button.
- Deletion page: `https://play.google.com/store/account/subscriptions` (`delete-account-page.tsx:11`), labelled `Manage your subscriptions on Google Play`.
- App link: `proxy/src/index.ts:186` serves `/.well-known/assetlinks.json` (not read; it is the Android app-link file).
- Abuse links: `mailto:` only (section 2.1 and 2.4).

## 8. Formatting (amounts, dates, lists, locale)

- Locale: English (`"en"`) everywhere: `Intl.NumberFormat("en")` in `web/src/money.ts:24`, `Intl.DateTimeFormat("en")` in `web/src/dates.ts:2`, `Intl.ListFormat("en", conjunction)` in `web/src/share-state.ts:82`. The UI text is English only.
- Amounts (`money.ts`): `formatMoney(amountMinor, currency)` uses the currency's `fractionDigits` and `Intl.NumberFormat` with `style: currency`. Examples: `₫150,000` (VND, 0 digits), `$42.50` (USD, 2 digits), `KWD 1.234` (3 digits, per the doc comment, not a test). Negative values keep a minus. Unknown codes (not in `Intl.supportedValuesOf("currency")`) are written `<code> <number>`. Amounts are built with whole-number arithmetic (`decimalOf`, lines 41-50) to stay exact up to 10^15.
- Dates (`dates.ts`): `formatBillDate("YYYY-MM-DD")` with `dateStyle: "medium"` in UTC, e.g. `Nov 14, 2026`. Text that is not a real date is shown as given.
- Lists (`share-state.ts:85-87`): `listOf` uses `Intl.ListFormat`: `A`, `A and B`, `A, B, and C`. Used in `, picked by Minh and An` and in the live sentences.
- Names: `capitalized` (`share-state.ts:42`) capitalises the first letter of a sentence-start name.
- Quantities: `<n> × <item>`, omitted when the quantity is null or `1` (`share-sections.tsx:146-148`). Sample: `2 × Pho bo`.
- Counts: `1 item` / `N items` (`share-sections.tsx:32`). Retry wait texts: `Try again in N seconds.` / `Try again in N minutes.` (rounded up) / `Try again in a few minutes.` (no value).
- The currency code on the invite: `Balances in THB` (raw code, not formatted).
- Payment details are shown exactly as the creditor wrote them, with `white-space: pre-line`.

## Risks and things to verify before design work

1. Only exact `/s/`, `/r/` and `/g/` (trailing slash) are served (`index.ts:187-189` with `routeOf`, `index.ts:146-151`). `/s` or `/s/<anything>` returns the Worker's JSON 404 `{"error":"path"}`. The App's `not_found` view is reached only by `/s/`, `/r/`, `/g/` with a missing or malformed fragment. `/` is also a JSON 404.
2. `hero__amount`, `person__note` and the bare `person` class have no CSS rule. Any redesign that depends on them needs new rules.
3. The `document.title` is set from the route kind, so `/s/` with no token shows `Shared bill · Split Vibe` on the not-found page (`app.tsx:39-41`).
4. Dark mode is OS-driven only (`prefers-color-scheme`). Elevation is removed in dark mode, so shadows exist only in light.
5. Footer differences: `Split Vibe never holds or moves money.` and `Report abuse` are on link pages only (`footer.tsx`), not on `/delete-account` or the legal pages.
6. Two `Report abuse` links appear on a bill or request page: one in the footer, one beside each payment block (`payment-block.tsx:58-63`). The e2e specs check both.
7. The CSP has no `font-src` and no `img-src` beyond `'self' data:`. Any new font or image host needs policy, embed and check changes together (section 5).
8. The share page's owner name uses `UNNAMED_OWNER` in sentences (`share-state.ts:27`), so copy with the owner's name must allow for the fallback.
9. The e2e and tests need `proxy/.dev.vars` (`web/e2e/local-proxy.ts:6-16`). This inventory did not open that file.
10. The e2e specs use PayPal and UPI test data and the `Lan`/`Minh`/`An` names; the copy in e2e strings is what is pinned.

## Unresolved questions
- The real built file names (hash format) were not confirmed by a build. Only the Vite defaults and the test fixture pattern were read.
- `web/src/share-controller.ts` tests (28 blocks) and `live.test.ts` were counted, not read line by line. They do not select on the DOM.
- The e2e helper `proxy/scripts/*` and the `proxy/package.json` `build:web` script were not read; the embed script's own comment was used for the build path.
