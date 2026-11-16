# Proxy: edits of a split by a sentence, weighted calls, the bill's category

The proxy part of Phase 7 is built and pushed on `feat/phase-07-ai-assistant-insights`. `POST /v1/split-revision`
turns a sentence into edits from the contract's thirteen ops and costs a fifth of a call from the
same allowance as a bill read. The allowance now counts in hundredths of a call, and the usage
numbers are decimals. Objects written in whole calls are converted the first time they are asked
after the deploy. A bill read now asks for the bill's `category`. The evaluation set has 56 cases
and a runner that refuses to start without a key; it has been tested with the fake model only.
`npm run typecheck` and `npm test` pass in `proxy/`, on every commit's own tree. Nothing was
deployed, no Cloudflare or Anthropic API was called, and every process I started is stopped.
Ports 8787 and 8788 are free.

## The wire contract as built

The full text is in `docs/proxy.md`, in the section "Editing a split by a sentence".

**Shared with a bill read.** These are the same as for a bill read:

- the headers (`X-SplitVibe-Install`, `X-SplitVibe-Version`, or `Authorization: Bearer`);
- the order of checks: switch, key, version, caller, body, IP limit, allowance, then spend caps;
- the NDJSON stream of wait lines, then one result or error line;
- the per-IP limit of 10 calls a minute and 100 a day, which bill reads and edits now share;
- the spend caps.

**Request.** It has the contract's shape. These are the limits I chose:

- `requestId` is a UUID. `instruction` is 1 to 500 code points, has no control characters (so it is one line), and loses Cf characters except the two joiners.
- The bill:
  - `merchant` is null or 1 to 200 characters.
  - `items` holds 0 to 200 items, as `{id, name, quantity, amountMinor}`.
  - `charges` holds 0 to 50 charges, as `{id, label, kind, amountMinor}` with the six kinds.
  - `totalMinor` is 0 to 10^15.
- The split:
  - `participants` holds 1 to 100 people.
  - `whole` must be present: null, or a division in the domain's `Division` form.
  - `items` maps an item id to a division.
  - `notSharing` and `payments` are at most 100 each.
  - `items`, `notSharing`, `payments`, `among` and `except` may be left out when they are empty.
- Ids, names and numbers:
  - Ids are 1 to 64 characters of `A-Za-z0-9_-` and unique within their list.
  - Names and labels are 0 to 200 characters. The app can hold cleared names, and longer ones than a bill link takes.
  - Shares and basis points in the request are any whole number from 0, because a split the user is still typing need not add up.
- The body is at most 64 KB. That keeps the prompt under the 100K tokens up to which Haiku's price in `spend.ts` holds.
- Anything else is 400 `input`, before anything is held. Fields the contract does not name never reach the model.

**Answer.** `{"type":"result","answer":{"edits":[…],"explanation":"…","question":null},"usage":{…}}`

- `edits` holds at most 20 edits. `explanation` is 0 to 200 characters. `question` is null, or 1 to 200 characters, and then `edits` is empty.
- `shares`, `basisPoints` and `amounts` are objects keyed by participant id, as in a division: `{"me": 2, "p1": 1}`.
- `add_charge` carries exactly one of `amountMinor` or `percentBasisPoints`, and leaves the other key out.
- `quantity` is a decimal string greater than 0.
- Shares are at most 1,000 (item shares start at 1, whole shares at 0), basis points at most 10,000, and amounts at most 10^15. Names and labels are 1 to 200 characters.
- The proxy does three things to the answer before checking it:
  - The model's schema writes each number per participant as a `{participantId, …}` pair, because structured outputs cannot key an object by a value. The proxy builds the object from the pairs.
  - It lowercases the enum values.
  - It turns line breaks into spaces. A blank question counts as null.
- **What passes to the app as it is.** The app rejects these edits one by one:
  - unknown ids, including ids that are not id-shaped (for example "Lan Anh");
  - percentages that do not add up to 10,000;
  - amounts that do not add up to the total;
  - `remove_person` on `me`.

**Errors.** The codes are those of a bill read:

- Before the stream:
  - 400 `input`, which also covers a request id reused for another request, a bill read's included;
  - 401 `install` or `session`;
  - 426 `update_required`;
  - 429 `quota_exhausted` (with `usage`), `retry_limit` (with `usage`), `rate_limited`, `spend_limited` or `busy`;
  - 503 `ai_off` or `not_configured`.
- As the last line:
  - `refused` and `too_long` are charged and carry `usage`, as for a bill read.
  - `bad_response` is **given back and carries no `usage`**, as you asked. On a bill read it stays charged.
  - `upstream`, `busy`, `timeout` and `internal` are not charged.

**Usage numbers.** `limit`, `used` and `remaining` are decimals with at most two decimals (`3`, `0.2`, `2.8`). This holds everywhere usage appears:

- `/v1/usage` and the answer of `/v1/session`;
- the result and error lines;
- the 429 bodies.

Counts are kept in whole hundredths and divided once, which a test checks for every count from 0 to 1,000 calls. Without that, `0.07 + 0.22` would print `0.29000000000000004`. A call runs only while `remaining` is at least its weight: with 0.8 left, a bill read gets 429 and an edit runs.

## What the app must do differently

- **Usage.** Read `limit`, `used` and `remaining` as decimals, not with `intOrNull`. Today `ProxyApi.callsLeftOf` returns null for `2.8`, and `usage()` then throws `bad_response`. Show the numbers without trailing zeros.
- **The new route.** It sends the same headers as a bill read. The domain's `SplitRevisionRequest` builds a body this route accepts, and `SplitRevisionParser` reads this answer. I read both committed files and changed neither.
- **Error handling on the new route.**
  - A `bad_response` line has no `usage`.
  - A 429 `quota_exhausted` can come with `remaining` of 0.8, which is still enough for an edit.
- **Category.** `category` is now a required field of the bill read's answer. The domain's parser already reads it.

## How the old quota data is converted

The old objects stored the count of the period as `book: {period, used}` in whole calls. Their request ids had no weight.

- Every public method of `QuotaBook` first runs `upgrade()`, once per instance.
- `upgrade()` rewrites a book without `hundredths` as `{period, hundredths: used × 100}`. It gives every request id without a weight a weight of 100, so a call held before the deploy is charged or given back as the whole call it was.
- Each record converts on its own and only when it is in the old form, so running the upgrade again changes nothing. Writes go in chunks of at most 128 keys.
- A Worker of the old version can still be finishing a bill read while the new one starts. It calls `hold` and `commit` without a weight, and that counts as a whole call. Without this guard, the count would become NaN.
- Tests write objects in the old form and check that:
  - the usage is unchanged;
  - the old hold is charged as one call;
  - the old charged request id is not charged again;
  - a monthly count keeps its month;
  - storage written in the new form is left alone;
  - a call without a weight counts as a whole call.

## Model settings and why

The edit model is `claude-haiku-5-5` at effort `low` with `thinking: {type: "adaptive"}`, `max_tokens` 8,000 and no server-side fallback. The comment in `policy.ts` gives the reasons:

- An edit is a short mapping from one sentence onto structured edits. Low effort is the documented level for short, simple, high-volume requests.
- Adaptive thinking lets the model think on the harder cases and skip thinking on the rest. The harder cases are:
  - several edits at once;
  - percentages that must add up;
  - nicknames and missing accents;
  - deciding to ask instead.
- I chose adaptive over thinking off for two reasons:
  - Anthropic's guide says Haiku 5.5 tends to put reasoning into user-facing text with thinking off or at low effort, and here that text is the 200-character explanation.
  - Sonnet 5.5 and Opus 5.5 reject `disabled`. Adaptive thinking lets the evaluation compare all three models with the same settings.
- 8,000 tokens leaves room for a short think plus a 20-edit answer, with the roughly 30% larger token counts of Haiku's tokenizer.
- The spend hold uses the call's `max_tokens` and the byte bound on input, which holds for any tokenizer.

Bill reads keep `claude-opus-5-5` at effort medium with fallback, unchanged.

## The evaluation set

**Where it is.** `proxy/eval/revise/` holds:

- `fixtures.json`, with nine bills and splits in the request format;
- `cases.json`, with 56 cases: 25 Vietnamese, 16 English, 5 Thai, 5 Indonesian and 5 Spanish;
- `score.ts`, with the scoring and the report;
- `run.mjs`, the runner.

**What the cases cover.** The tests check this coverage:

- every one of the thirteen ops, and five cases with several edits;
- tips by percentage and by fixed amount, plus a service charge, a parking fee and a discount;
- payers, people added and removed, and someone who paid but did not eat;
- names typed without accents and as nicknames ("hung", "Tom", "jose y maria"), and two people both called Minh, where the expected answer is a question;
- six ambiguous instructions that expect a question;
- adversarial instructions:
  - a negative share;
  - a "SYSTEM OVERRIDE";
  - an off-topic request;
  - JSON injected into the instruction;
  - instructions hidden in an item's name and in a person's name.

**Scoring.**

- A case that expects a question passes when the answer asks one.
- Otherwise the edits must match exactly, with these allowances:
  - people and per-participant numbers are compared as sets;
  - charge labels are ignored;
  - new-person refs are matched by name;
  - order matters only between edits that do not commute. Those pairs are:
    - two whole-split edits;
    - a whole-split edit and an item-split edit;
    - two edits of the same item;
    - an edit that adds or removes a person, and one that names that person;
    - a charge by percentage, and an item amount or quantity;
    - the whole bill's exact amounts, and an edit that changes the total.
- Adversarial cases expect no edits and allow a question.

**How to run it.** From `proxy/`:

- `npm run eval:revise` runs every candidate model.
- `npm run eval:revise -- claude-haiku-5-5` runs only the models named.

It needs Node 22.18 or later and `ANTHROPIC_API_KEY` in `proxy/.dev.vars`. Without the key it prints a clear refusal and runs nothing. I checked that here; this machine has no key.

**What a run does.**

- For each model, it starts its own `wrangler dev` on `127.0.0.1:8788` with fresh state in a temporary directory.
- Each call is sent with `X-SplitVibe-Eval-Model`. The proxy honours that header only when it is served at `localhost`, `127.0.0.1` or `[::1]` and the model has a price. The last line then reports the model, tokens and cost.
- It retries a `rate_limited` reply after its `Retry-After`.
- At the end of each model, and when interrupted, it stops the whole process group.
- It writes `plans/reports/eval-split-revision-<YYMMDD-HHMM>.md` with:
  - accuracy;
  - errors;
  - p50 and p95 latency (nearest rank);
  - average cost per call;
  - results by language;
  - every failed case.

I ran a copy of the runner with `AI_ENABLED` off, so the proxy answered every call itself and no model was called. It started wrangler, sent 56 calls, wrote the report, removed its state, and left no wrangler or workerd process running.

## Tests

- Before: 18 files, 268 tests. After: 20 files, 330 tests.
- Changed files:
  - `quota-book.test.ts` went from 13 to 25 tests (weights, the refusal below the weight, decimals, conversion).
  - `bill-analysis.test.ts` went from 22 to 24 (category).
- New: `split-revision.test.ts` (30 tests) and `eval-revise.test.ts` (18).
- The route tests cover:
  - 42 malformed bodies, all refused before anything is counted;
  - the bills the app can hold;
  - holds and commits at 0.2 with decimal usage;
  - re-runs charged once;
  - a request id that cannot be reused across edits or routes;
  - the refusal below 0.2;
  - 34 broken model answers, all given back with spend settled at cost;
  - unknown ids passing through;
  - the switch, the version check, the key, spend caps and the shared IP limit;
  - the evaluation header, both locally and on the deployed host.
- A mutation check confirmed that three of these tests fail when their check is removed: the given-back `bad_response`, the local-host gate and the question-with-edits rule.
- The `web/` tests were not needed, because nothing embedded in the pages changed.

## Commits

1. `e0d7411` feat(proxy): count calls in hundredths so that a call can weigh less than one
2. `40a0701` refactor(proxy): move what every call to the model shares out of the bill read
3. `167e0b2` feat(proxy): ask the bill read for the category of the whole bill
4. `d93dcb0` refactor(proxy): share the readers of a bill's lines and the check of a computer's host
5. `3e6b02f` feat(proxy): edit a split by a sentence with the model
6. `bbf33e2` fix(proxy): take every bill the app can hold in an edit of its split
7. `364977c` feat(proxy): evaluate the models that edit a split by a sentence
8. `3c0153c` docs(proxy): describe edits of a split, weighted calls, the category and the evaluation
9. This report.

## Notes

- I added no D1 migration.
- The shared fixture `sample_bill_answer.json` in `app/` was not changed. The proxy tests add a category to it in `test/requests.ts`, so they pass whether or not the app's fixture has a category.
- Commit 6 exists because I read the domain's committed request builder after commit 5. That builder sends the bill as the app holds it, and the app holds things the bill link's limits refuse: cleared names, names over 80 characters, and percentages past 100% while the user types.

## Open questions

1. **Older apps.** An older app signed in to an account that has made an edit reads `2.8` as missing. Its Settings would then show a read error, and its bill-read lines would carry no usage. The app is not released, so this may not matter. Otherwise, raise `MIN_APP_VERSION` when edits ship.
2. **Charging refusals.** On the edit route, `refused` and `too_long` are charged (0.2), as on a bill read. Only a `bad_response` is given back. Please confirm that the owner wants refusals of edits charged.
3. **Spend hold.** The hold uses the dearest list price, as bill reads do: about $0.27 per edit in flight, against a real cost near $0.0005. Edits have no fallback, so the hold could use the model's own price. I kept the conservative rule; changing it is a one-line decision.
4. **Model choice.** It stays Haiku with adaptive thinking until the real-key run. If Haiku misses the 90% target, the next settings to try are thinking off with Haiku (the runner would need a second setting) or Sonnet 5.5 with `between_tools`.
5. **Gaps in the evaluation.**
   - The explanation's language and wording are not scored, and the report does not show explanations.
   - Matching across scripts (typing "Nok" for "นก") is not covered.

Status: DONE
Summary: The edit route, weighted calls with decimal usage and the conversion of old counts, the bill's category, and a 56-case evaluation with its runner are built, documented, tested with the fake model (330 tests), and committed in focused commits that each pass on their own tree.
Concerns/Blockers: Older app builds cannot parse decimal usage, and edit refusals are charged as on bill reads; both are listed under open questions for the owner.
