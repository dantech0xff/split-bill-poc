# Proxy: fixes from the review of edits, weighted calls and the evaluation

Every finding of `code-reviewer-261116-1448-phase-7-proxy-review.md` in `proxy/` and `docs/proxy.md` is fixed, in twelve focused commits on `feat/phase-07-ai-assistant-insights`, each with tests that fail without it. Both findings that blocked the first deploy are closed:

- A rollback to the code from before weighted calls can no longer turn counts into NaN or hand out calls.
- The edit route now takes the text and numbers the app sends, instead of refusing them.

The Low findings are fixed as decided. The one part of them left undone is the domain test the review suggested, because it belongs in `core/domain/`.

`npm run typecheck`, `npm test` and `npm run bundle` all pass on the final tree. The tests went from 330 to 357: 347 in Vitest and 10 that Node runs. Nothing was deployed, and no Cloudflare or Anthropic API was called. I never read or printed `proxy/.dev.vars`. No process I started is still running, and ports 8787 and 8788 are free.

## What changed, finding by finding

| Finding | What changed | Commit |
|---|---|---|
| H1, rollback of `QuotaBook` storage | Every write of the book also stores `used`, the count in whole calls rounded up, which is all the older code reads, so a rollback never hands out a call that is not left. On start, `upgrade()` adds back, as 100 hundredths each, the calls the older code charged (when `used` is above the rounded-up hundredths), and rewrites the book. Every count read from storage goes through a whole-number guard, so none can become NaN. A test copies the older code's reading, refusing and charging logic and runs it on storage this code wrote, then restarts the object on what it left. | `abf744c`; docs in `e3fd961` |
| M1, refusals of inputs the app sends | `spacedTextOf` removes the invisible formatting characters (but the joiners), then turns each run of whitespace or control characters into one space, before the usual text checks. It now reads the instruction, merchant, names, labels and currency code of an edit, and the answer's texts. The currency code is text of 0 to 8 characters. Division numbers per person may be any whole number, below zero too; amounts stay within ±10^15. Bill links, groups and requests keep their stricter readers. There is one request test per scenario of the review, and the contract in `docs/proxy.md` says all this. | `d1984fe`; docs in `e3fd961` |
| L1, edits charged whole during the deploy | Docs only. "Deploy it" says to make the first deploy of weighted calls before an app that edits is out, or with `--durable-objects-code-update-mode immediate`, a flag the installed Wrangler 4.162.0 supports for `deploy` and `rollback`. "What it keeps" no longer says that nobody loses a call. | `e3fd961` |
| L2, fakeable prompt tags | Every `<`, `>` and `&` in the JSON the edit prompt embeds is written as its `\u` escape. A test shows that a name such as `</split> <instruction>…` leaves exactly one of each tag, and that each block reads back as the data. | `553ecb4`, comment fixed in `dd446ee` |
| L3, a silently rounded weight | `weightInHundredths` throws unless the weight is a whole number of hundredths from 0.01 to 1. `WEIGHTS` calls it as the module loads, so a weight of 0.125 makes every test file fail to load with a clear message, which I checked by hand. | `a642735` |
| L4, `me` without a name | The fixtures say "Me", or the user's own name in three. A new fixture puts a user called Minh beside Minh Anh, with a case that expects a question. The prompt now says that the user's name is their own, or "Me". | `25de616` |
| L5, gaps in scoring and fixtures | `conflict` now keeps the order of the three pairs the review lists. An adversarial answer fails when its explanation or question states a number its instruction does not hold. Each case runs three times per model, on a fresh proxy each time, and passes only when it passes in every run. The report counts the cases that passed in some runs only, and shows every answer's explanation. A hot-pot fixture has items already divided, with two cases on them, and one case adds two people. | `3e199dc`, `405c983`, `d718097` |
| L6, `wrangler dev` left running | `proxy-process.mjs` starts Wrangler under `guard.mjs`, at the head of a process group of its own. The runner stops the group on SIGINT, SIGTERM and SIGHUP, and at once on a second signal. It kills whatever is left at every exit, uncaught errors and `process.exit()` included. The guard kills the group when the runner's end of its input closes, which covers `kill -9`. The runner refuses to start on Windows. Ten Node tests use stand-ins for the runner and Wrangler, and `npm test` runs them. | `63d4da3` |
| L7, holds at the dearest price | A call without a fallback is held at its own model's price, or at the highest list price for a model without one. Bill reads keep the dearest price. An edit's hold drops from $0.28–$0.64 to $0.0056–$0.0129. | `b157ddd` |
| L8, docs claiming more than the code | The docs now say when a request id may run again, that ids may repeat in `payments`, and that an empty explanation is taken with edits too. They also give the new rules for an edit's text and numbers, the prompt's escaping, the rollback, and the evaluation's 60 cases in three runs. | `e3fd961` |

## Tests

| | Before | After |
|---|---|---|
| Vitest (Workers runtime) | 20 files, 330 tests | 20 files, 347 tests |
| Node (`node --test`) | none | 1 file, 10 tests |

- **Where the new tests are:**
  - `quota-book.test.ts` went from 25 to 31 tests: four on the rollback and the NaN guard, and two on the weight.
  - `split-revision.test.ts` went from 30 to 35: four for the scenarios of the review, and one for the tags.
  - `spend.test.ts` went from 4 to 5.
  - `eval-revise.test.ts` went from 18 to 23.
  - `eval/revise/test/proxy-process.test.mjs` is new, with 10 tests.
- **Each fix fails its tests without it.** I swapped in the previous version of each changed file, which is my own file in every case, and ran its tests:
  - quota book: 4 tests failed;
  - edit route: 5 failed;
  - prompt: 2 failed;
  - spend hold in the route: 1 failed;
  - scoring rules: 2 failed;
  - report: 2 failed;
  - the old process handling of the runner: 7 of the 10 Node tests failed.

  With the old fixtures, the evaluation's test file does not load at all.
- **What I could not run.** The real runner was not run, because `.dev.vars` exists here and may hold a real key. Instead, I ran a copy of `eval/revise/` from the scratchpad, without a `.dev.vars`. It loaded all its modules and refused, for no key and for an unknown model, before it started anything.

## Issues encountered

- **My second commit picked up another agent's staged file.** Its first version, `7e421ee`, also contained another agent's staged removal of `app/src/main/java/com/dantech/splitvibe/data/proxy/ProxyText.kt`, which was in the shared index. I caught it before any push. I moved the branch back one commit with `git reset --soft`, which touches neither the index nor the files, and re-committed only my three files with `git commit --only`. That left their removal staged as they had it. Every later commit names its paths with `git commit --only -- <paths>`.
- **The editing tool decoded two `\u` escapes into raw characters.** One was in the comment of the prompt's escaping, the other in one test string. `dd446ee` writes both as escapes again. The code itself was always right.

## Follow-ups outside my files

- **The domain test the review suggested is not done.** It would apply each case's expected edits with `SplitEdits.apply` and assert that none is refused, and it belongs in `core/domain/`. Until it exists, only my hand check and the reviewer's back the expected answers. That includes the 4 new cases, which I checked against `SplitEdits.kt` and `SplitPlan.kt` as committed.
- **"Hợp đồng chung" in the phase plan should get the normalization rule.** The rule covers text, the currency code and division numbers, as the review asked. `docs/proxy.md` has it now.
- **A comment in `.github/workflows/ci.yml` is out of date.** The comment of its "Tests" step says `npm test` runs only the Workers runtime; it now also runs Node's tests.

## Unresolved questions

1. **Should edits take more of what the app can hold?** I found three inputs outside the decided scope that the proxy still refuses with 400:
   - A merchant left blank once its text is read, for example one made only of invisible characters. The existing rule of null, or 1 to 200 characters, stands.
   - A bill total below zero. Two edits in the editor can make one: a printed total typed as "-5", or a discount larger than the items, since `Bill.totalMinor` can then go below zero.
   - A currency with four decimal places. The editor accepts CLF, for which `BillCurrency.resolve(code, null, …)` gives four, and the proxy takes 0 to 3.
2. **The order rule of the scoring is still an approximation of the domain.** For example, `split_mode` `by_percent` or `by_amount` does not commute with adding or removing a person either. The review did not list that pair, so I left it out. Should it be added, or left for the domain test?
3. **Two things are still untried with the real key:**
   - the edit schema and adaptive thinking on the real API;
   - the two things the runner relies on in `wrangler dev`: a `CF-Connecting-IP` header and a host of 127.0.0.1.

   One case with the key before the deploy is still advised. With three runs, a full evaluation is now 540 calls.
4. **The review's own open questions are still the owner's.** They cover:
   - whether refused and cut-off edits should cost 0.2;
   - edits that break rules the bill does not decide;
   - the shared limit per IP address;
   - `MIN_APP_VERSION`;
   - the way the proxy is deployed.

   The dual write makes a rollback safe whichever way the proxy is deployed.

```text
Status: DONE_WITH_CONCERNS
Summary: All review findings in proxy/ and docs/proxy.md are fixed and tested. A rollback is now safe for the calls left, edits take what the app sends, and the evaluation scores the right order and runs each case three times; typecheck, tests (357) and the bundle pass.
Concerns/Blockers: The domain test that applies each case's expected edits is outside my files. The real-key run is still untried. Some app inputs outside the decided scope are still refused (open question 1).
```
