# Claim matches and joiners in names

Date: 2026-11-15 (UTC). Branch `feat/phase-06-web-companion-realtime-claim`: three commits on top of `80032be`, then this report. All pushed.

## Outcome

Both of the product owner's decisions are built as briefed, and every check passes.

**Joiners.** Names and labels now keep the zero-width joiner (U+200D) and the zero-width non-joiner (U+200C).
- Every other Cf character is still stripped.
- A name with nothing left but joiners and spaces counts as empty. So it is still refused wherever a name is needed.

**Matches.** `finalize` takes an optional `matches`.
- The proxy keeps it in a new column, `shares.matches`, added by `proxy/migrations/0003_share_matches.sql`.
- ShareView and PublicShare return it.

**The page.** Once the link is settled:
- It lists who picked, and says who each matched claim is on the bill ("On the bill as Minh").
- It marks "You" on the browser's own claim, and on the amount of the participant that claim was matched to.

**Checks.**

| Package | Check | Result |
|---|---|---|
| `proxy/` | `npm run typecheck` | Passes |
| `proxy/` | `npm test` | 268 tests pass (260 before) |
| `web/` | `npm test` | 192 tests pass (182 before) |
| `web/` | `npm run build` | Passes, CSP check included |
| `web/` | `npm run e2e` | 5 of 5 pass |

**Clean-up.** Afterwards no wrangler, workerd, Chromium or vite process was left running. Ports 8787, 8797, 9339, 9229 and 5173 were free.

## The contract for `matches`

### The request

The route is `POST /v1/shares/{id}/finalize`, with the body `{"billRevision","bill","amounts","paidBy"?,"matches"?}`.

`matches` is null, missing, or an object `{"<claim id>": "<participant id>"}`:
- Every key must be exactly the id of one of this link's claims, as ShareView lists it.
- Every value must be the id of a participant of the `bill` sent in the same request.
- Several claims may be the same participant.
- A claim left out has no match.
- Null, missing and `{}` all mean "no matches". All three are stored as null and answered as null.

### What is refused

Anything else is 400 `{"error":"input"}`, as for a bad `paidBy`. The tests cover these cases:
- an array, a string or a boolean in place of the object;
- a value that is not a participant of the bill: null, a number, an object, an array, `""`, `"ME"`, `"p9"`, or a participant of another bill;
- an unknown claim id;
- the id of a claim the owner removed;
- the id of a claim of another link.

### When each check runs

1. **With the body.** The shape and the participants are checked as the body is read, before the call counts as a change.
2. **Against the link.** The claims are checked after the existing checks for an `open` link (409) and a stale `billRevision` (409). So this 400 counts as one of the owner's 300 changes an hour, as every `finalize` call already does.
3. **In the write.** The claims are checked again inside the write batch, by a new `claimsOnLink` condition in the guard update. If a claim is removed between the read and the write, the batch rolls back, the link is read again, and the answer is 400.

### How it is stored and returned

- **Column.** `shares.matches` is TEXT holding JSON with its keys sorted. It has the constraint `CHECK (matches IS NULL OR amounts IS NOT NULL)`, as `paid_by` does.
- **Same settlement again.** A `locked` link that gets the same revision, bill, amounts, `paidBy` and matches changes nothing, whatever the order of the keys.
- **Different matches.** These count as a change: the revision moves on and every open page hears it.
- **Dropped with the amounts.** Like `paidBy`, `matches` is dropped by a `PUT` that changes the bill, by `reopen` and by revoking. Deleting the account and the daily run delete the whole row.
- **Views.** ShareView and PublicShare carry `matches: Record<string, string> | null`, with its keys sorted. It is null whenever `amounts` is null, and also when no claim was matched.

## Joiners

`textOf` in `proxy/src/input.ts` now works like this:
1. It strips every Cf character except the two joiners, with the regex `/(?![‌‍])\p{Cf}/gu`.
2. It trims the result.
3. If nothing but joiners and spaces is left, the text counts as empty.

What follows from this:
- Where a name needs at least one character, a name of only joiners and spaces is refused.
- The owner's participant `me` may have an empty name. A name of only joiners becomes empty, and the page then calls them the bill's owner.
- Every name and label the proxy accepts goes through `textOf`.
- The page still strips nothing from what a person types, as the owner decided.

The tests cover these cases:
- A family emoji joined by ZWJ, and "Alireza" written in Persian with a ZWNJ, come through unchanged. This holds in a claim name and in the bill's merchant, items and participants.
- Other Cf characters are still stripped: U+202E, U+200B, U+2060, U+2066, U+2069, U+FEFF and U+00AD.
- Names of only joiners, or of joiners mixed with spaces and other Cf characters, are refused. This holds for claims, the merchant, items, charges and participants.
- The owner's name of only joiners becomes empty.

One existing test used U+200D as its example of a character that gets stripped. It now uses U+2060, the word joiner, which is still stripped.

## The page

**Where the list lives.** The page had no list of claims; claims only appeared as chips on the items. So I added a "Who picked" section after "What each person pays".
- It shows only when the link is settled and at least one claim is matched to a participant of the bill.
- It lists every claim, in the order the claims were made.

**What each claim says.**
- A matched claim says "On the bill as <name>".
- If the claim is matched to the owner `me` and the owner left their name empty, it says "the person who made this link". This follows the page's existing rule for sentences (`personInSentence`).
- An unmatched claim shows only its name.

**The "You" mark.**
- The browser's own claim (`you`) carries a "You" chip, the same chip the items already use.
- The amount row of the participant that claim was matched to gets:
  - the chip next to the name;
  - a highlight in `--sv-secondary-container`.
- The chip says "You" in words, so the mark does not rely on colour alone.

**Colours.** I computed contrast from `tokens.css`, which I did not edit. Every text on the highlighted row has at least 5.5:1 in both themes; the lowest is the note text in dark mode. No other colour override was needed.

**Parsing.**
- `parseShare` reads `matches` into an object without a prototype, as `parseAmounts` does.
- A missing `matches` reads as null.
- A value that is not a string fails the whole answer.

**Checked by eye.** I took screenshots of the settled page in light and dark mode, on a Pixel 7 viewport, with a throwaway spec that I then deleted. Both look right.

**Size.** The initial download is 26.9 KB gzipped (26.6 KB before), against a budget of 150 KB.

## Tests

| Package | Before | After |
|---|---|---|
| `proxy/` | 260 tests, 18 files | 268 tests, 18 files |
| `web/` | 182 tests, 17 files | 192 tests, 17 files |

### Proxy: 8 new tests

- **Joiners** (2 tests): kept on bills and on claims.
- **Matches** (6 tests), which check that:
  - `matches` is returned in ShareView and PublicShare, and is null before settling;
  - several claims can be matched to one participant;
  - `{}` and a missing `matches` behave the same as null;
  - the same matches in any key order are not a change, and different matches are;
  - every refusal case is 400 and nothing is written;
  - a claim removed just before the settlement's batch makes the settlement 400;
  - `matches` is dropped by a changed bill, by `reopen` and by revoking, and the schema's CHECK refuses it without amounts.

I also verified the race test. With `claimsOnLink` removed from the guard, exactly that test fails, and only that one.

The `racing` helper moved from `share-claims.test.ts` to `share-fixtures.ts`, so that both test files can use it.

### Web: 10 new tests

- **`parseShare`** (3 tests):
  - it keeps `matches`, which is null before settling or when the field is missing;
  - it refuses a wrong shape;
  - it keeps `__proto__` and `constructor` as plain keys.
- **`matchOf`** (2 tests).
- **The settled page** (5 tests), which cover:
  - a match for the browser's own claim, and no match for it;
  - no list when no claim resolves to a participant, and no list before settling;
  - an owner with an empty name;
  - claim ids `__proto__` and `constructor`.

The fixtures now carry `matches`. The settled fixture has real matches.

## End to end

`npm run e2e` passed 5 of 5 in 20.5 s.
- It ran against `wrangler dev` on 127.0.0.1:8797, with a fresh local D1 and migrations 0001 to 0003 applied.
- The owner takes the claim ids from the `lock` answer, then settles with `paidBy: "me"` and matches for Minh's and An's claims.

On both phones, the run checks:
- the "Who picked" section, with "On the bill as Minh" and "On the bill as An";
- the "You" chip on that phone's own claim and on its own amount, and on no other row.

Live updates arrived in 12 to 65 ms, and no CSP violation was reported.

## Migration

The migration is `ALTER TABLE shares ADD COLUMN matches TEXT CHECK (matches IS NULL OR amounts IS NOT NULL)`. `0001` and `0002` are untouched.

I checked it in the scratchpad with local databases only; no Cloudflare API was called.
- **Fresh database.** `wrangler d1 migrations apply --local` applies 0001, 0002 and 0003 in order.
- **Upgrade.** I took a database that had only 0001 and 0002 and held a settled link, then applied 0003. Existing rows got `matches` null and kept their `paid_by`.
- **Constraint.** Inserting `matches` without amounts fails the CHECK.

Step 3 of "Deploy it" in `docs/proxy.md` now names `0003`, and says it only adds a column.

## Commits

1. `b4f49f8` fix(proxy): keep the zero-width joiner and non-joiner in names and labels
2. `6e8547e` feat(proxy): say on a settled bill link which participant each claim is
3. `8bed2b3` feat(web): say who each claim is on a settled bill and mark the browser's own amount
4. This report.

How each commit was tested:
- I tested each commit's tree before starting the next change.
- Commits 1 and 2 passed `npm run typecheck` and `npm test` in `proxy/`.
- Commit 3 passed `npm test`, `npm run build` and `npm run e2e` in `web/`. After it, the proxy's typecheck and tests passed again.

Each commit carries the contract text for its own change. Files were staged by explicit path. Nothing was touched outside `proxy/`, `web/`, `docs/proxy.md`, `docs/web.md` and this report.

The orchestrator's commits `c7ab71b` and `80032be` landed in between. They touch only `app/`, `.github/` and `docs/architecture.md`.

## What the app must do

**Send `matches` with `finalize` as planned.**
- Use claim ids exactly as ShareView gives them.
- Name only participants of the bill sent in the same request.
- Expect `null` back after sending `{}`.

**Retry a 400 from `finalize` with fresh data.** Read the link again and rebuild `matches` before sending it again. The owner may have removed a claim from another device in the meantime.

**Read the new field.** ShareView now carries `matches`. The app should read it, or at least accept it, in case its decoder refuses keys it does not know.

**Keep the joiners in the app's own copy of a bill.** I did not read `app/`. But according to its commit message, `c917cfc` makes the app strip formatting characters from the bill's names and labels before it sends them, by the proxy's former rule.
- If the app strips all Cf characters, the bill's names on the link still lose their joiners. The owner's decision would then hold only for names typed on the page.
- The app should keep U+200C and U+200D.
- It should treat a name of only joiners and spaces as empty. That means its "Item", "Charge" or "Someone" fallback, or an empty name for the owner.

## Open questions

1. **Joiners at the edges of a name.** They are kept, because the rule keeps joiners everywhere. A joiner at the start or end of a name joins nothing, and it can make "Minh" and "Minh" followed by U+200D look the same. Should edge joiners be trimmed along with the spaces?
2. **Own claim matched to nobody.** "Who picked" then shows the claim with "You" and no note, and no amount is marked. Should the page say something, such as "You are not on the bill"? Nothing was asked for, so the page says nothing.

Status: DONE_WITH_CONCERNS
Summary: Joiners are kept in names, and `matches` is built end to end: `finalize`, migration `0003`, both views, and the settled page's "Who picked" list with the "You" mark. The proxy has 268 tests and the web pages 192, all passing, and the end-to-end suite passes 5 of 5.
Concerns/Blockers: The app likely still strips joiners from its own copy of a bill (`c917cfc`), so bill names on the link would lose them until the app follows the new rule.
