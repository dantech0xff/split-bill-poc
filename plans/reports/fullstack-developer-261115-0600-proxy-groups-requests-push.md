# Proxy: groups, payment requests and push

- Branch: `feat/phase-05-groups-balances-settle-up`, not pushed
- Commits: `17e0b47` (groups, bills, settlements), `d5e7df1` (payment requests, devices, push, reminders), `1b94db8` (App Links and link pages), `c7d0223` (account deletion), `0ae49b5` (docs), plus the commit that adds this report
- Date: 2026-11-15
- Scope: `proxy/` and `docs/proxy.md` only. Nothing was deployed, no Cloudflare or Google resource was created or called, and no secret was read.

## What was built

All 25 routes and rules of the contract, plus the two additions sent during the work: the 10^15 cap on every
amount, and `creditorName` and `title` on payment requests.

- `migrations/0001_groups.sql`: the whole schema (groups, members, claims, invites, bills, settlements,
  group log, requests, devices, hourly windows), with foreign keys, a few `CHECK`s and two triggers. One
  trigger refuses any change to a settlement. The other guards group revisions (see "Concurrency").
- `group-state.ts`: reading a group's state, and `changeGroup`, which every change to a group goes through.
- `groups.ts`, `invites.ts`, `bills.ts`, `requests.ts`, `devices.ts`: the routes. `push.ts`: FCM HTTP v1.
  `reminders.ts`: the cron's reminders and clean-up. `account-data.ts`: account deletion. `links.ts`: tokens,
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

Joining and claims
5. `memberId` or `claimMemberId` set to `null` counts as absent; both or neither is 400.
   - A member who posts join with either body gets 200 and their state, and no claim is made.
   - A claim on an unknown member is 404.
   - An account has one waiting claim per group: the same claim again is 202 and nobody is notified; a claim
     on a different placeholder is 409.
   - Joining as a new member drops one's own waiting claim.
   - Approving does not rename the placeholder.

Members and the log
6. `joinedAt` is when the member row was made, and it never changes, also when a placeholder is claimed. The
   new owner is the member with an account whose `joinedAt` is earliest, with ties broken by id.
7. The log's `memberId` is the actor and `subjectId` the thing acted on (table in `docs/proxy.md`).
   `claim_requested` has the placeholder in both fields. Renaming the group adds no entry, because the
   contract has no kind for it. Account deletion logs `member_left`.

Bills and settlements
8. `document` must be a JSON object, else 400. It is stored after `JSON.parse`/`JSON.stringify`, so key order
   is kept but numbers are written as JavaScript writes them.
   - `conversion` may be missing or null. Otherwise `rate` is a positive decimal string (at most 18 digits
     each side of the point), `date` a real `YYYY-MM-DD` day, and `source` 1 to 60 characters. Other fields
     are dropped.
   - Keys of `shares` and `paid` must be exact lower-case member ids.
   - A deleted bill gives 409 for any `expectedRevision`.
   - Checks run in this order: 400 or 413 on the body, 404 or 403 on the group, 400 for a non-member key, 409
     for a deleted bill, 403 on bill ownership, the replay check, 409 on the revision, then the 413 caps.
9. A settlement amount is 1 to 10^15, `fromMemberId` must be another member, and the same id gets the rules
   in item 2.

Payment requests
10. `amountMinor` is at least 1. `method` is a JSON object of at most 4096 bytes in UTF-8, else 413.
    `toMemberId` without `groupId` is 400, and so is `groupId` without `toMemberId`. `billId` is any UUID and
    is not checked against the group's bills. The currency must match the group's code and `fractionDigits`.
11. `GET /v1/request-links/{token}`:
    - Anyone but the creditor gets `role: "debtor"` with `debtorBound: true` ("as after accepting").
    - The creditor gets the creditor view, with the real `debtorBound`.
    - Once an account is bound, anyone but that account and the creditor gets 409 `conflict` and sees none of
      the request. That is the same answer accept gives.
12. Accept by the creditor is 403. Reported-paid does nothing once the request is confirmed.
13. A creditor can confirm a group request even after leaving the group. The settlement is credited to the
    creditor's member recorded when the request was made.
14. When a group is deleted, its requests stay, with `groupId`, `toMemberId` and `billId` set to null.
    Confirming one then records no settlement.
15. Request links never expire. Deleting a request empties it at once (method `{}`, names, title, debtor,
    group); its link then answers 410 for 30 days, then 404.
16. The link token is the first 128 bits of HMAC-SHA-256 of the request id under `HASH_SALT`, and only its
    SHA-256 is stored. That is what lets a repeated create return the same `url`. Invite tokens are 128
    random bits.

Push and reminders
17. The push texts are fixed (`PUSH_TEXT` in `push.ts`):
    - request: "New payment request"
    - reported: "Payment reported"
    - confirmed: "Payment confirmed"
    - reminder: "Payment reminder"
    - claim: "Someone asked to join"

    Each has a fixed body. `data` is exactly `{type, requestId}`, or `{type, groupId}` for a claim. Accept,
    approve and reject send no push. Without a working service account, flows succeed and nothing is sent.
18. Reminders:
    - Requests already reported paid are skipped: the debtor already said they paid.
    - The clock starts at `lastRemindedAt`, or at `createdAt` if there was none.
    - A reminder may go out up to 1 hour early, so a daily run that starts a few seconds early still sends it.
    - At most 40 notifications per run (`POLICY.remindersPerRun`); the rest go the next day.
    - A debtor without devices is still recorded as reminded.
    - Without push set up, nothing is sent and nothing is recorded.
19. Devices:
    - A token belongs to one account: registering it elsewhere deletes the other row.
    - `PUT` with a device id another account owns moves that device to the caller.
    - An account keeps its 10 newest devices.
    - A device is deleted on FCM 404 with `UNREGISTERED`, or on 400 `INVALID_ARGUMENT` about the
      registration token. Other errors keep it.

Account deletion and App Links
20. Account deletion goes further than the contract's list:
    - On every request the account made, `creditorName` becomes "Former member".
    - The account is removed as creditor and as debtor on every request (a bound debtor stays "bound"). So
      signing in again with the same Google account finds nothing, and no reminder goes out for it.
    - Groups where nobody else has an account are deleted, and its hourly windows too.
21. assetlinks: with `ANDROID_CERT_SHA256` empty, or with no valid fingerprint, the body is `[]`. Otherwise
    both packages get every valid fingerprint, upper-cased.

    `/g/{token}` and `/r/{token}`:
    - Every well-formed token gets the same HTML page, and it is never looked up.
    - A malformed token is 404.
    - The page is sent with `no-store`, `no-referrer`, a strict CSP and `noindex`.

Limits I added (not in the contract; all 413 `too_large`)
22. 100 members per group (placeholders included), 1,000 live bills per group, 100 groups per account (memberships
    plus waiting claims), and request bodies of 16 KB (256 KB for bills).

Concurrency
23. Every group change runs as one D1 batch. The batch starts with `UPDATE groups SET revision = read + 1`, and
    a trigger aborts it (`stale_group`) when another change came first. The change is then re-read and decided
    again, up to 10 times with a random backoff, before it answers 409 `conflict`. Tests cover 8 concurrent
    adds and two racing bill edits.

## Deploy

1. Apply the schema before deploying: `npx wrangler d1 migrations apply split-vibe --remote`.
2. Optional push: `npx wrangler secret put FCM_SERVICE_ACCOUNT < service-account.json`, using a Firebase service
   account allowed to send FCM messages. Delete the key file afterwards.
3. App Links: put the SHA-256 fingerprints of the debug key, the upload key and the Play app signing key into
   `ANDROID_CERT_SHA256` in `wrangler.jsonc`, comma-separated.
4. `npx wrangler deploy`. `HASH_SALT` must never change: accounts and every request link depend on it.

## Checks

- `npm run typecheck`: clean.
- `npm test`: 172 passed in 14 files. 96 tests were there before; 76 are new.
- Each of the four code commits builds and passes on its own: 135, 166, 170 and 172 tests.
- Negative controls: I broke nine rules one at a time, and the tests caught each one:
  - the owner check on renaming a group
  - the revision guard trigger
  - the check that a bill's amounts add up to its total
  - the check that a bill names only members
  - the rule that only the debtor reports a request paid
  - the rule that only the creditor confirms it
  - the account a push goes to
  - one claim per placeholder
  - account deletion taking the account off its members

## Unresolved questions

1. The cron runs at 16:30 UTC, which is 23:30 in Vietnam and Thailand, so reminders arrive near midnight. As the
   contract asked, I used the existing cron. Should reminders get a second cron, for example 02:00 UTC?
2. Is the Worker on the free plan? The reminder budget of 40 per run fits its 50 subrequests; on the paid plan,
   raise `POLICY.remindersPerRun`.
3. Keep or change the caps I added (100 members, 1,000 bills, 100 groups per account, 10 devices)?
4. The plan also mentions reminding the creditor when a request was reported paid but not confirmed. That is not
   built, because it is not in the contract.
5. Group writes have no rate limit, and the log is never pruned (the state shows only the newest 100). Is that
   acceptable for now, given D1's row and storage billing?
6. The plan's architecture said accepting a request link could bind a placeholder member to the account; the
   contract does not, so it does not. Please confirm.
7. A group's state always includes every live bill's document. Large groups will mean large answers; there is
   no pagination.
