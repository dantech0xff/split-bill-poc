# Proxy: groups, payment requests and push

- Branch: `feat/phase-05-groups-balances-settle-up`, not pushed
- First commits: `17e0b47` (groups, bills, settlements), `d5e7df1` (payment requests, devices, push, reminders), `1b94db8` (App Links and link pages), `c7d0223` (account deletion), `0ae49b5` (docs), `d93d057` (this report)
- After the review: `849e5f6`, `005e444`, `49a0ab9`, `ff2c90d`, `a0380c1`, `8364553`, `81366ba`, `daf8337`, plus the commit that updates this report (see "Fixes after review")
- Date: 2026-11-15
- Scope: `proxy/` and `docs/proxy.md` only. Nothing was deployed, no Cloudflare or Google resource was created or called, and no secret was read.

## What was built

All 25 routes and rules of the contract, plus the two additions sent during the work: the 10^15 cap on every
amount, and `creditorName` and `title` on payment requests.

- `migrations/0001_groups.sql`: the whole schema (groups, members, claims, invites, bills, settlements,
  group log, requests, devices, hourly windows), with foreign keys, a few `CHECK`s and three triggers:
  - two refuse changing or deleting a settlement (deleting its whole group aside);
  - one guards group revisions (see "Concurrency").
- `group-state.ts`: reading a group's state, and `changeGroup`, which every change to a group goes through.
- `groups.ts`, `invites.ts`, `bills.ts`, `requests.ts`, `devices.ts`: the routes. `push.ts`: FCM HTTP v1.
  `reminders.ts`: the cron's notices and clean-up. `account-data.ts`: account deletion. `links.ts`: tokens,
  App Links and the link page. `rate-limit.ts`: hourly windows, counted in D1.
- `index.ts`: a pattern router (`/v1/groups/:group/...`). Its 404 `path` and 405 `method` behave as before,
  and every route still needs `HASH_SALT`. `identity.ts`: `sessionAccountOf`, and `signedIn`, the wrapper
  that answers 401 `session`. `input.ts`: shared validators, and `jsonBody`, which tells 413 from 400.
- Tests: `vitest.config.ts` reads the migrations, and `test/setup.ts` applies them before each test.
  `reset()` empties the local D1 between tests, which I checked. `test/fake-fcm.ts` fakes Google's token
  endpoint, which verifies the RS256 assertion with the public key, and FCM.

## Contract details I decided (the app depends on these)

Status codes and repeats
1. Every "→ group state" route answers 200. The only exceptions are `POST /v1/groups` (201) and a claim (202).
2. Sending the same thing again:
   - `POST /v1/groups` with an existing id: 201 with the current state if the caller is a member of that
     group, else 409.
   - `POST .../members` with an id the group already has: 200 with the current state, nothing changed.
   - `PUT bill`: if the stored revision is `expectedRevision + 1` and the content is identical, it is a
     replay: 200, nothing changed. Anything else follows the revision rule.
   - Settlement id sent again by the member who recorded it: 200, nothing changed. By another member: 409.
   - `POST /v1/requests` with an existing id from the same creditor: 201 with the current view and the same
     `url`. The hourly limit does not count it. Another creditor's id, or a deleted request's id: 409.
   - Accepting a link again: 200. Reported-paid or confirmed-paid again: 200, nothing changed. Deleting a
     deleted bill: 200. Deleting a deleted request: 204. Deleting an unknown device: 204.

Who gets 403 and who gets 404
3. A group that does not exist is 404; a non-member gets 403 `forbidden`, a pending claimant 403 `pending`.
   For a request, someone who is neither creditor nor debtor gets 404, and the other party gets 403.
4. An id in the path that is not a UUID is 404 `not_found`, except on `PUT` (bills, devices), where it is 400.
   Uppercase UUIDs are accepted and lower-cased.

Link tokens
5. No URL path or query holds a token.
   - Shared links are `https://<host>/g/#<token>` and `/r/#<token>`.
   - The app sends the token as `"token"` in the bodies of `POST /v1/invites/open`, `/v1/invites/join`,
     `/v1/request-links/open` and `/v1/request-links/accept`.
   - A body without a `token` string is 400 `input`.
   - A string no link could hold (not 22 base64url characters), or that no link has, is 404 `not_found`.
   - An expired or revoked invite, or a deleted request, is 410 `gone`.
   - `GET /g/` and `GET /r/` serve the page. `/g/<token>` is 404.

Joining and claims
6. In a join body, `memberId` or `claimMemberId` set to `null` counts as absent; both or neither is 400.
   - A member who posts join with either body gets 200 and their state, and no claim is made.
   - A claim on an unknown member is 404.
   - An account has one waiting claim per group: the same claim again is 202 and nobody is notified; a claim
     on a different placeholder is 409.
   - Joining as a new member drops one's own waiting claim.
   - Approving does not rename the placeholder.

Members and the log
7. `joinedAt` is when the member row was made, and it never changes, also when a placeholder is claimed. The
   new owner is the member with an account whose `joinedAt` is earliest, with ties broken by id.
8. The log's `memberId` is the actor and `subjectId` the thing acted on (table in `docs/proxy.md`).
   - `claim_requested` has the placeholder in both fields.
   - `member_removed` has the owner as `memberId` and the removed member as `subjectId`.
   - Renaming the group adds no entry, because the contract has no kind for it.
   - Account deletion logs `member_left`.
9. `POST /v1/groups/{gid}/members/{mid}/remove`:
   - Non-owners get 403. An unknown member is 404.
   - Removing the owner's own member, or a placeholder, is 409 `conflict`.
   - The removed account can come back through any live invite, so the owner should revoke invites if needed.

Bills and settlements
10. `document` must be a JSON object, else 400. It is stored after `JSON.parse`/`JSON.stringify`, so key order
    is kept but numbers are written as JavaScript writes them.
    - `conversion` may be missing or null. Otherwise `rate` is a positive decimal string (at most 18 digits
      each side of the point), `date` a real `YYYY-MM-DD` day, and `source` 1 to 60 characters. Other fields
      are dropped.
    - Keys of `shares` and `paid` must be exact lower-case member ids.
    - A deleted bill gives 409 for any `expectedRevision`.
    - Checks run in this order: 400 or 413 on the body, 404 or 403 on the group, 400 for a non-member key, 409
      for a deleted bill, 403 on bill ownership, the replay check, 409 on the revision, then the 413 caps
      (the group's document bytes, then its live bills).
11. A settlement amount is 1 to 10^15, `fromMemberId` must be another member, and the same id gets the rules
    in item 2.

Payment requests
12. `amountMinor` is at least 1. `method` is a JSON object of at most 4096 bytes in UTF-8, else 413.
    `toMemberId` without `groupId` is 400, and so is `groupId` without `toMemberId`. `billId` is any UUID and
    is not checked against the group's bills. The currency must match the group's code and `fractionDigits`.
    Every RequestView carries `creditorMemberId`: the creditor's member for a group request, else null. It
    becomes null too when the group is deleted.
13. `POST /v1/request-links/open`:
    - Anyone but the creditor gets `role: "debtor"` with `debtorBound: true` ("as after accepting").
    - The creditor gets the creditor view, with the real `debtorBound`.
    - Once an account is bound, anyone but that account and the creditor gets 409 `conflict` and sees none of
      the request. That is the same answer accept gives.
14. Accept by the creditor is 403. Reported-paid does nothing once the request is confirmed.
15. A creditor can confirm a group request even after leaving the group. The settlement is credited to the
    creditor's member recorded when the request was made.
16. When a confirmation and a withdrawal come together, whichever lands first wins:
    - If the withdrawal lands first, the confirmation answers 404 and records nothing.
    - If the confirmation lands first, the withdrawal answers 409.
17. When a group is deleted, its requests stay, with `groupId`, `toMemberId`, `creditorMemberId` and `billId`
    set to null. Confirming one then records no settlement.
18. Request links never expire. Deleting a request empties it at once (method `{}`, names, title, debtor,
    group); its link then answers 410 for 30 days, then 404.
19. Tokens:
    - A request link token is the first 128 bits of HMAC-SHA-256 under `HASH_SALT` of
      `request-link:<creditor's account>:<request id>`, and only its SHA-256 is stored. This lets a repeated
      create return the same `url`, while another account reusing a forgotten id gets a different link.
    - Invite tokens are 128 random bits.

Push and notices
20. The push texts are fixed (`PUSH_TEXT` in `push.ts`), with the titles below and a fixed body each:
    - request: "New payment request"
    - reported: "Payment reported"
    - confirmed: "Payment confirmed"
    - reminder: "Payment reminder"
    - unpaid: "Payment still open"
    - unconfirmed: "Payment to confirm"
    - claim: "Someone asked to join"

    The `unpaid` and `unconfirmed` texts are exactly as you gave them.

    `data` is exactly `{type, requestId}`, or `{type, groupId}` for a claim. The `android.notification.channel_id`
    "requests" is kept. Accept, approve, reject and removal send no push. Without a working service account,
    flows succeed and nothing is sent.
21. The daily run:
    - A request due a reminder whose debtor is bound and has a device notifies the debtor (`reminder`).
      Otherwise its creditor, if they have a device, gets `unpaid`. Both record `lastRemindedAt`, so both follow
      `remindEveryDays`.
    - A request reported paid two days ago and not confirmed tells its creditor (`unconfirmed`), whatever its
      `remindEveryDays`, at most once every two days. This uses a new column, `confirm_reminded_at`.
    - Due means: not confirmed, not reported paid, not deleted, with a creditor, and no reminder (nor creation)
      in the last N days.
    - Every cadence may fire up to 1 hour early, so a daily run that starts a few seconds early still sends it.
    - Only accounts with a device are chosen. At most 40 notifications go out per run, counted per device, oldest
      first; the rest go the next day.
    - The access token is fetched once per run. Without push set up, or if the token fetch fails, nothing is sent
      and nothing is recorded.
22. Devices:
    - A token belongs to one account: registering it elsewhere deletes the other row.
    - `PUT` with a device id another account owns moves that device to the caller.
    - An account keeps its 10 newest devices.
    - A device is deleted on FCM 404 with `UNREGISTERED`, or on 400 `INVALID_ARGUMENT` about the
      registration token. Other errors keep it.

Account deletion and App Links
23. Account deletion goes further than the contract's list:
    - On every request the account made, `creditorName` becomes "Former member".
    - The account is removed as creditor and as debtor on every request (a bound debtor stays "bound"). So
      signing in again finds none of it, and no notice goes to it or on its behalf. A creditor whose debtor
      was deleted gets `unpaid` notices instead.
    - Groups where nobody else has an account are deleted, and its hourly windows too.
    - Sessions are not revoked. One issued before the deletion keeps working until it expires, which
      `docs/proxy.md` now says.
24. assetlinks: with `ANDROID_CERT_SHA256` empty, or with no valid fingerprint, the body is `[]`. Otherwise
    both packages get every valid fingerprint, upper-cased.

    The page at `/g/` and `/r/` is the same for every link, never sees a token, and is sent with `no-store`,
    `no-referrer`, a strict CSP and `noindex`.

Limits (beyond the contract)
25. Size limits, each 413 `too_large`:
    - 100 members per group, placeholders included.
    - 500 live bills per group.
    - 1 MB of live bill documents per group in total, counted as serialized, with a changed bill counted as it
      would be.
    - 2,000 settlements per group. Confirming a group request whose settlement would pass this is also 413.
    - 100 groups per account (memberships plus waiting claims).
    - Request bodies of 16 KB (256 KB for bills).
26. Rate limit: 200 group changes per account per hour over all groups, 429 with `Retry-After`.
    - Every route that changes a group counts once, whatever it then decides: making a group, renaming it,
      adding, renaming and removing members, leaving, joining and claiming, approving and rejecting, bills,
      settlements, and confirming a group request.
    - Reads, invites and payment requests do not count.

Concurrency
27. Every group change runs as one D1 batch. The batch starts with `UPDATE groups SET revision = read + 1`, and
    a trigger aborts it (`stale_group`) when another change came first. The change is then re-read and decided
    again, up to 10 times with a random backoff, before it answers 409 `conflict`. Tests cover 8 concurrent
    adds and two racing bill edits.

    A confirmation that lost to a withdrawal still moves the group's revision on by one, with no change in the
    state.

## Fixes after review

The review is `plans/reports/code-reviewer-261115-0800-phase-5-proxy-review.md`. Each fix is its own commit,
and each commit passed `npm run typecheck` and `npx vitest run` before it was made.

| Finding | Commit | Fix |
|---|---|---|
| H1 | `849e5f6` | The request link token is derived from the creditor's account and the id, so a forgotten id reused by another account never opens an old link. A test plays the review's attack through; it fails with the old derivation. |
| M1 | `005e444` | The confirmation's batch updates only a request that is still open, in the group and not withdrawn, and inserts the settlement and log entry only when that update landed. The answer re-reads the request, and nobody is notified unless the confirmation landed. A test wraps D1 so that the withdrawal lands just before the confirmation's batch; it fails with the old code. `changeGroup` writes may now skip the state reads, and answer asynchronously. |
| L4, L2 | `49a0ab9` | A trigger refuses deleting a settlement while its group exists. A group's cascade still works, which tests check for leaving and for account deletion. `docs/proxy.md` now says that sessions are not revoked, and what an old session can still do. |
| M2 | `ff2c90d` | Tokens moved out of URLs, as in item 5. The old routes with a token in the path are removed, and the docs and the `wrangler.jsonc` comment now say traces hold URLs that contain only ids. |
| M4 | `a0380c1` | Added `creditorMemberId`, and owner removal of a member with the new log kind `member_removed` (items 8, 9 and 12). Requests already made stay as they are. |
| H2 | `8364553` | The new limits of items 25 and 26. Bills store `document_bytes`, and the partial index `group_bills_live` serves both the state read and the `count(*)`/`sum` of live bills. `leave` no longer reads the state. |
| M3, plan | `81366ba` | The reminder run of item 21. The new types `unpaid` and `unconfirmed` use your texts, the budget is spent only on accounts with a device, and the access token is fetched once per run. Negative controls showed that removing the device filter brings the starvation back, and recording without a token is caught. |
| docs | `daf8337` | What `remindEveryDays` and account deletion now mean for notices. |

Not changed:
- L1: a former member may still confirm, as decided before.
- L3: the device takeover risk is accepted.
- L5: links cannot be rotated.
- The review's other suggestions: answering writes without the whole state (the app depends on it), a debtor's
  decline, and owner approval of new members.

Tests went from 172 to 185, all passing. The new ones:
- the H1 attack;
- two confirm and withdraw races;
- member removal, twice;
- the document bytes cap;
- the settlement cap;
- the write limit, twice;
- the creditor notices, twice;
- the reminder budget;
- the token fetched once.

## Deploy

1. Apply the schema before deploying: `npx wrangler d1 migrations apply split-vibe --remote`. Nothing is deployed
   yet, so the one migration was edited in place.
2. Optional push: `npx wrangler secret put FCM_SERVICE_ACCOUNT < service-account.json`, using a Firebase service
   account allowed to send FCM messages. Delete the key file afterwards.
3. App Links: put the SHA-256 fingerprints of the debug key, the upload key and the Play app signing key into
   `ANDROID_CERT_SHA256` in `wrangler.jsonc`, comma-separated.
4. `npx wrangler deploy`. `HASH_SALT` must never change: accounts and every request link depend on it.

## Checks

- `npm run typecheck`: clean.
- `npx vitest run`: 185 passed in 14 files (96 from before the phase).
- The negative controls of the first round still hold. I broke nine rules one at a time, and the tests caught
  each one:
  - the owner check on renaming a group
  - the revision guard trigger
  - the check that a bill's amounts add up to its total
  - the check that a bill names only members
  - the rule that only the debtor reports a request paid
  - the rule that only the creditor confirms it
  - the account a push goes to
  - one claim per placeholder
  - account deletion taking the account off its members
- The fixes add four negative controls, all caught: the H1 derivation, the M1 batch, the reminder device
  filter, and the run's single token fetch.

## Unresolved questions

1. The cron runs at 16:30 UTC, which is 23:30 in Vietnam and Thailand, so notices arrive near midnight. As the
   contract asked, I used the existing cron. Should reminders get a second cron, for example 02:00 UTC?
2. Is the Worker on the free plan? The 40 notices per run fit its 50 subrequests; on the paid plan, raise
   `POLICY.remindersPerRun`.
3. Keep or change the limits: 100 members, 500 bills, 1 MB of documents, 2,000 settlements, 100 groups per
   account, 10 devices, and 200 group changes an hour?
4. The group log is never pruned (the state shows only the newest 100), and deleted bills keep an emptied row.
   Both now grow only within the hourly write limit.
5. The plan's architecture said accepting a request link could bind a placeholder member to the account; the
   contract does not, so it does not. Please confirm.
6. A confirmation that loses to a withdrawal still moves the group's revision on by one, with no change in the
   state, so the app may fetch once for nothing.
