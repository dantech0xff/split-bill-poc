# Code review: Phase 5 proxy (groups, bills, settlements, payment requests, push, reminders, App Links, account deletion)

- Branch `feat/phase-05-groups-balances-settle-up`. Six commits touch `proxy/` (`17e0b47` to `d4ab8c7`). Scope: `git diff 0acc20d..HEAD -- proxy docs/proxy.md`, 34 files, +4,761 / -88.
- Date 2026-11-15 (UTC). The Android app is out of scope (another reviewer covers it).
- Method:
  - Read every changed file against `docs/proxy.md` ("Shared data"), the implementer's report and the phase-05 plan.
  - Grepped every SQL write statement.
  - `npm run typecheck`: clean (`tsc` exit 0, strict, no `any` or suppression). `npx vitest run`: 172/172 pass.
  - Ran four probes in the Workers test runtime. The probes live in the scratchpad, not the repo: `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/proxy-probe/*.probe.test.ts`. To re-run, from `proxy/`: `npx vitest run --dir <that folder> --globals --silent=false`. The scratchpad may be cleared.
  - Checked Cloudflare's current docs for the D1 and Workers limits and for trace span attributes.
- Nothing was edited, committed or deployed. No secret was read.

## Verdict

Every route checks authorization correctly. No critical finding.

Two High findings block production:
- **H1:** a forgotten request id can be reused, and a payment link someone shared earlier then shows another person's payment method.
- **H2:** a group's state has no bound. One member can break the group for everyone and use up the account's daily D1 free quota.

There are also four Medium and five Low findings.

## Critical

None found.

## High

### H1. An old request link can be re-pointed to another creditor's payment method

Where:
- `proxy/src/links.ts:21-25`: the link token is a function of the request id alone: `HMAC(HASH_SALT, "request-link\0" + id)`.
- `proxy/src/requests.ts:169-170,199`: any caller may create a request under an id that no row holds, and gets that same token.
- `proxy/src/reminders.ts:65`: deleted requests are forgotten 30 days after deletion, which frees their id.
- `proxy/src/requests.ts:226-232`: a link resolves by its token's hash only.

Scenario:
1. Lan posts her request link in a group chat.
2. Mallory opens it. `GET /v1/request-links/{token}` answers with the request's `id`. A bound debtor also sees the id in `GET /v1/requests`.
3. Lan withdraws the request.
4. Thirty days later the daily run deletes the row.
5. Mallory sends `POST /v1/requests` with the same `id`, her own VietQR, `creditorName` "Lan", and the same title and amount.
6. The proxy issues the same token. Lan's old link in the chat now opens Mallory's request. It is unbound, so anyone who opens it sees "Lan, Hot pot, 150,000 VND" with Mallory's account, and can accept and pay.

This lets someone other than the requester change what a payer sees.

Evidence (probe, reproduced):
- After `forgetExpired`, Mallory's create answered 201 with the same token.
- Opening the old link answered 200 with `{"creditorName":"Lan","method":{"kind":"VietQr","accountNumber":"MALLORY-ACCOUNT"}}`.

Fix (pick one; the first is the smallest):
- (a) Never free a request id. Keep the emptied row, or at least its `id` and `link_hash` in a tombstone table, and answer 410 forever. The rows are tiny.
- (b) Derive the token from the creditor too: `HMAC(salt, "request-link\0" + creditor + "\0" + id)`. Reuse by another account then gives a different token, and the old link stays dead.

Either way, add a test that forgets a request, re-creates its id as another account, and opens the old link.

### H2. A group's state is unbounded, and every GET and every write reads all of it

Where:
- `proxy/src/group-state.ts:119-136`: the state carries every live bill with its full document, and every settlement.
- `proxy/src/group-state.ts:276-283`: every write appends those reads to its batch. This includes `leave`, which answers 204 and never uses them.
- `proxy/src/bills.ts:134`: each bill PUT runs `count(*)`, which scans every bill row of the group, deleted ones included.
- `proxy/src/bills.ts:198-200`: a deleted bill keeps its row forever, uncapped.
- `proxy/src/bills.ts:215-249`: settlements have no cap and no rate limit.
- `proxy/src/policy.ts:75,79`: the caps allow 1,000 live bills of 64 KB each.
- No group route has a rate limit.

Failure scenarios:
- **(a) The group breaks for everyone.** Any member, including one who joined through a forwarded invite, can loop `POST /settlements` for 1 minor unit from a placeholder. That is allowed, and it only costs their own balance. They can also fill the bill caps.
  - Every GET, and every write's answer, then exceeds the Free plan's 10 ms CPU per request. At the caps it also exceeds memory.
  - Settlements cannot be removed by anyone, so the group stays broken. Writes may still commit in D1 while the Worker fails to read the answer.
- **(b) All shared data goes offline daily.** D1 Free quotas are per account: 5 M rows read and 100 k rows written per day. Once one is hit, every D1 query fails until 00:00 UTC.
  - With 20 k settlements, one state read costs about 40 k rows read. About 125 reads a day then take every group, request and device offline for all users.
  - The app's normal fetching of that one group is enough. The attacker needs about a day of writes, then nothing more.

Evidence:
- Probe: 60 concurrent settlement posts all answered 200 (no 429).
- Probe: with 20,060 settlements, `GET` answered 5.2 MB, and D1 reported `rows_read` 40,189 for one state read. One more settlement also answered 5.2 MB.
- V8 cost of JSON parse and stringify alone, measured in Node:

| Group content | State size | CPU |
|---|---|---|
| 200 bills of 4 KB | 0.7 MB | about 7 ms |
| 1,000 bills of 8 KB | 6.4 MB | about 77 ms |
| 1,000 bills of 64 KB (the caps) | 47 MB | about 440 ms |
| 20,000 settlements | 5.4 MB | about 11 ms |

Fix:
- Cap what one group holds:
  - settlements per group, and per member per day;
  - the total document bytes per group (for example 1 to 2 MB);
  - deleted bills, by counting them toward a cap or moving them to a compact tombstone table without documents.
- Add an hourly write limit per account for group writes. `admitHourly` already exists.
- Answer writes with `{revision, the changed entity}` instead of the whole state. Keep the full state for `GET`, ideally with a "since revision" delta.
- Drop the state reads from `leave`.
- Add `CREATE INDEX ... ON group_bills (group_id, created_at, id) WHERE deleted_at IS NULL`, so that reads and the count stop scanning deleted rows.

## Medium

### M1. A confirmation that races the creditor's own deletion leaves a withdrawn request confirmed, with a settlement that cannot be undone

Where:
- `proxy/src/requests.ts:337-354`: `confirmInGroup` reads the request in its plan. But the batch's `UPDATE requests SET confirmed_paid_at ... WHERE id = ?1 AND confirmed_paid_at IS NULL` has no `deleted_at IS NULL` or `group_id` guard, and the settlement INSERT is unconditional.
- `proxy/src/requests.ts:384-403`: `deleteRequestRoute` changes the row outside the group's revision guard. So the guard does not order the two actions.

Scenario:
1. The creditor confirms on one phone and withdraws on another, or the app sends both actions in quick succession.
2. The delete lands between `confirmInGroup`'s read and its batch.
3. Delete answers 204 and empties the request (`group_id` NULL, method `{}`). Confirm answers 200, records the settlement, and notifies the debtor.
4. The request disappears from both lists while the group's balance has moved. Nobody can undo it: settlements are append-only, and only the receiver records them.
5. Thirty days later the row is forgotten. The id becomes free again (H1), and any group request that later reuses it can never be confirmed: its INSERT hits `settlements_one_per_request`, which `lostRace` does not catch, so the answer is 500.

Evidence: reproduced in 4 of 32 rounds, with the delete started two D1 round trips later. Each time: confirm 200, delete 204, one settlement, and a row with both `deleted_at` and `confirmed_paid_at` set.

Fix:
- Decide in the batch on the row as it is: `UPDATE requests SET confirmed_paid_at = ?2 WHERE id = ?1 AND confirmed_paid_at IS NULL AND deleted_at IS NULL AND group_id = ?3`.
- Insert the settlement only when that update landed: `INSERT INTO settlements (...) SELECT ... FROM requests WHERE id = ?1 AND confirmed_paid_at = ?2 AND deleted_at IS NULL`.
- Answer from the re-read, and send no push when nothing landed.
- Alternatively, run deletion of a group request through `changeGroup`.
- Add a race test.

### M2. Invite and request-link tokens are written to Cloudflare traces in clear

Where:
- `proxy/wrangler.jsonc:7-15`: traces are on, with `head_sampling_rate: 1`.
- Six routes carry a bearer token in the URL path (`proxy/src/index.ts:84-85,90-91,94-95`): `/v1/invites/{token}`, `.../join`, `/v1/request-links/{token}`, `.../accept`, `/g/{token}` and `/r/{token}`.
- Cloudflare's "Spans and attributes" page lists `url.full` and `url.path` on every fetch-handler root span.
- `docs/proxy.md:101-105` and the wrangler comment both say traces hold no secret.

Scenario: anyone who can read the account's observability data, or any trace export destination, can recover:
- live invite tokens, which let them join a group for 14 days, read its whole financial state and claim placeholders;
- request-link tokens, which let them read the payment method and become the debtor before the intended payer.

This defeats "only its SHA-256 is kept".

Fix:
- Take the tokens out of URLs:
  - For the API, put the token in a POST body: `POST /v1/invites/lookup {"token"}`, `POST /v1/invites/join {"token", ...}`, and the same for request links.
  - For shared links, put the token in a fragment (`https://host/g#<token>`). Browsers never send a fragment, and Android passes it to the app.
- Or exclude these paths from traces or sample them out.
- Correct the docs and the wrangler comment either way.

### M3. Reminders: device-less debtors take the run's places, and a broken service account records reminders it never sends

Where:
- `proxy/src/reminders.ts:22-43`: `LIMIT 40` counts requests, including those whose debtor has no device. Those are recorded and use up the run.
- `proxy/src/reminders.ts:45-51`: a reminder is recorded before it is sent.
- `proxy/src/push.ts:104-105`: an access token is fetched per `sendTo`, and a failed fetch returns silently.

Scenario:
- **Starvation.** Forty older due requests whose debtors have no registered device fill a run. That is common: debtors who never registered a device or turned notifications off, and attacker accounts at 30 requests an hour per creditor. The run records 40 reminders and sends none, while a debtor with a device waits.
  - The whole service gets one 40-place run a day. An attacker who keeps a few hundred device-less due requests delays every reminder by weeks.
- **Broken key.** If the key is revoked or rotated, each run fetches a token up to 40 times, sends nothing, and records all 40 as reminded. The docs only promise "without push set up, nothing is sent or recorded".

Evidence (probe): 40 device-less due requests plus one reachable debtor gave `{"event":"reminders","due":40,"reminded":40,"notifications":0}` and no messages.

Fix:
- Choose the run's notifications among due requests whose debtor has a device (join `devices`). Record or skip the device-less ones in a separate UPDATE without a limit.
- Fetch the access token once at the start of the run, and return without recording anything when it is null.
- Consider one reminder per debtor per run, and a cap on open reminding requests per creditor.

### M4. In a group, a payment request names its creditor only by typed text, and nobody can be removed or decline a request

Where:
- `proxy/src/requests.ts:60-79`: `RequestView` leaves out `creditor_member_id`.
- `proxy/src/requests.ts:159-168`: any member binds any account-holding member as debtor at once, and pushes them.
- `proxy/src/invites.ts:112-126`: joining as a new member needs no approval.
- `proxy/src/index.ts:74-92`: no route removes a member or lets a debtor decline.

Scenario:
1. An invite link is forwarded beyond the group. Any member can make one, and it works for 14 days.
2. A stranger joins with it.
3. The stranger sends every account-holding member a request with `creditorName` "Lan" (the organizer), the title "Trip deposit" and their own VietQR.
4. Each victim becomes the debtor at once, gets "New payment request", then daily reminders.
5. The app cannot show which member asked; it has only the typed name.
6. The owner can revoke invites but cannot remove the stranger. The victims cannot decline; they can only report paid to stop the reminders.

This is a social-engineering route in a product that routes real money.

Fix:
- Return `creditorMemberId` for group requests. Have the app show that member's group name, and flag it when it differs from `creditorName`.
- Add owner-only member removal, which makes the member a placeholder as leaving does.
- Add a debtor "decline" that stops reminders.
- Consider owner approval for new members.

## Low

- **L1. A former member still writes to the group's ledger.**
  - Where: `proxy/src/requests.ts:335-342`. `confirmInGroup` checks only the creditor account. It does not check membership, or that `creditor_member_id` is still held by the caller.
  - Scenario: the creditor leaves, and the owner approves someone else onto that placeholder. The former member's later confirmation records a settlement as received by, and recorded by, the new holder's member.
  - This follows the implementer's documented decision (report item 13). Suggested fix: when that member now belongs to another account, confirm the request without recording a settlement, or refuse it.
- **L2. Account deletion is not final for sessions, and leaves data reachable through links.**
  - Sessions: `proxy/src/account.ts:42-49` does not revoke the stateless 30-day session. A background job, a second phone or a stolen token can recreate groups, requests or devices under the same account hash. Signing in again then finds them, against `docs/proxy.md:356-357`.
  - Links: `proxy/src/account-data.ts:57-59` keeps the deleted creditor's unbound requests. Their payer name and title stay readable, and the requests stay acceptable, through their links, and are never forgotten.
  - Fix: keep a tombstone of deleted accounts with the deletion time, and refuse sessions issued before it. Mark the creditor's unbound open requests as deleted.
- **L3. The first account to register a device id or FCM token takes it.**
  - Where: `proxy/src/devices.ts:24-35`.
  - Any account that learns another account's device id or FCM token can move it to itself. The victim's notifications stop, and the attacker's go to the victim's phone. Payloads are fixed text and ids, so only notifications are lost; no data leaks.
  - Fix: refuse to move a device id that another account holds, keeping only the token de-duplication. Or accept and document the risk.
- **L4. Latent hazards in `changeGroup` and the schema.**
  - `proxy/src/group-state.ts:289` retries after a batch that committed, when the revision update matched no row. Today every write plan whose statements could still land also inserts a log row, which fails the foreign key. A future plan without one would commit side effects and then answer 404.
  - `proxy/src/group-state.ts:301-303` treats any foreign-key failure as a lost race and retries ten times, which would hide a validation bug behind a 409.
  - `proxy/migrations/0001_groups.sql:103-107` refuses only UPDATE on settlements. A guard such as `BEFORE DELETE ON settlements WHEN EXISTS (SELECT 1 FROM groups WHERE id = OLD.group_id)` would make "cannot be deleted" true in the database too, while still letting a deleted group cascade. Verify the cascade in a test.
- **L5. Request links cannot be rotated.**
  - Where: `proxy/src/requests.ts:253-269`.
  - The first account to accept a link becomes the debtor. A link posted in a chat can be taken by the wrong person, which locks the intended payer out (409) until the creditor deletes and re-sends.
  - The creditor cannot see who accepted. This follows the contract; noted for product awareness.

## Test gaps against the success criteria

- Missing:
  - re-creating a forgotten request id (H1);
  - any bound on group state or on group writes (H2);
  - the confirm and delete race (M1);
  - device-less debtors in the reminder limit, and a failing token fetch in the cron (M3);
  - account deletion's revision bump racing a group write;
  - a former member's confirmation after a re-claim (L1);
  - a session used after account deletion (L2).
- The plan asks for frequency limits ("giới hạn tần suất"). They exist only for requests (30 an hour) and invites (20 an hour); group writes have none.
- Settlements are protected against UPDATE in the database. Against DELETE they are protected only by having no route.
- Covered well:
  - the 401 on all 26 shared routes;
  - the 403 `forbidden` and `pending` matrix for outsiders and claimants on every group route;
  - the creditor and debtor permission matrix and the stranger 404s;
  - the settlement UPDATE trigger;
  - a concurrent double confirm recording one settlement;
  - account deletion keeping every member's sums;
  - push recipients and fixed text;
  - FCM token cleanup;
  - reminder due and not-due cases;
  - the size caps.

## What looked correct

- **Authentication.** Every shared route goes through `signedIn`, and the session is checked by jose (HS256, issuer, audience, `exp`, and a `sub` of 64 hex characters).
- **Group permissions.**
  - Every plan checks membership; owner-only actions check the owner.
  - A waiting claimant gets `pending`. Claims are shown to the owner only.
  - No answer ever contains an account hash.
  - An account becomes a member only by joining with an invite, or by the owner approving a claim (`AND account IS NULL`, one member per account per group).
- **Bills.**
  - Sums use BigInt, so large amounts are exact.
  - Member keys must be exact lower-case ids of the group.
  - Amounts are capped at 10^15. The conversion and the day are validated.
  - Replays are idempotent, the revision rule holds, and a deleted bill's id can never come back.
- **Concurrency.** The revision trigger and retry loop work.
  - A refused revision update rolls the whole batch back.
  - Every write to a group's state goes through `changeGroup`, or bumps the revision (account deletion), or creates the group.
  - Reads a plan makes after the group read are protected, because any change in between bumps the revision.
- **Settlements.**
  - Only the receiver records one, from another member, for more than 0.
  - The trigger refuses UPDATE. A repeated id is idempotent.
  - Confirming twice at once records exactly one, guarded by both the revision and the `settlements_one_per_request` unique index.
- **Payment methods and payment-request fields.**
  - `method` is written only at creation, and emptied on delete or account deletion. A repeated create never changes it.
  - Amount, names and title are also written only at creation.
  - Only the creditor, the bound debtor, or the holder of a link that no one has accepted yet can read a method.
  - A stranger gets 404 by id. Once a link is accepted, everyone else gets 409.
  - Accepting, reporting and confirming are atomic conditional updates.
- **Account deletion.**
  - It runs as one atomic batch and bumps the revision of every affected group.
  - Groups where nobody else has an account are deleted first, so a new owner always exists.
  - Bills pass to the new owner. Requests are detached. No other member's balance changes (tested).
- **Push.**
  - Titles and bodies are fixed text, and `data` holds only `type` plus a UUID.
  - Recipients match the contract: the debtor, the creditor, or the group's owner.
  - The access token is cached per service-account email, and concurrent fetches share one.
  - A failed notification never fails the flow, and nothing is sent without the secret.
  - A device is deleted only on `UNREGISTERED`, or on an invalid argument about the token, and only the row that still holds that token.
- **Cron budget.**
  - At most 41 external fetches: one token and 40 sends. The exchange-rate fetches run inside the `FxBook` Durable Object, so they do not count against the Worker.
  - At most 44 D1 queries: 3 for reminders, 1 for clean-up, and up to 40 device deletions.
  - Both are within the Free plan's 50. Each `db.batch` counts as one D1 query.
- **SQL.**
  - Every value is a bound parameter. Only constant fragments (`COLUMNS`, the orphaned-groups subquery) are interpolated.
  - Indexes cover every lookup the routes make.
  - The migration is consistent: foreign keys cascade from a group, and the triggers and partial indexes are sound.
- **Tokens and link pages.**
  - Invite tokens are 128 random bits. Request tokens are an HMAC of the id truncated to 128 bits. Both are kept only as SHA-256 and looked up by hash, so there is no timing oracle.
  - The link pages never look a token up, and send no-store, no-referrer, a strict CSP and noindex.
  - `assetlinks.json` validates the fingerprints.
- **Errors.** Internal errors answer 500 `internal` and log only the error name. Whether a group or request exists leaks only for random UUIDs, as the contract says.

## Unresolved questions

1. **H1 fix:** keep request ids forever (links answer 410 for good), or derive the token from the creditor and the id? The first also closes the M1 tail.
2. **Plan:** is the Free plan the production target? If so, the caps must be worked out from 10 ms of CPU and the account-wide D1 daily quotas. The paid plan raises those numbers but does not remove the risk that settlements break a group.
3. **Members and debtors:** should owners be able to remove members, and debtors be able to decline a request? Should group requests carry `creditorMemberId`? (M4)
4. **Placeholders:** should the "Former member" placeholders that account deletion leaves be claimable? A claimant would inherit a deleted person's balance.
5. **Former members:** keep the decision that a former member may still confirm group requests (L1), or stop recording a settlement once the placeholder belongs to someone else?
6. **Reminder time:** the reminder cron runs at 16:30 UTC, which is 23:30 in Vietnam and Thailand (the implementer's question 1).

## Metrics

- Type coverage: strict TypeScript; no `any`, `@ts-ignore` or lint suppression in `proxy/src`. `tsc` is clean.
- Tests: 172/172 pass, 76 of them new. No line-coverage tool is configured.
- Linting: no linter is configured for `proxy/`.
