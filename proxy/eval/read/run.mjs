// Runs the evaluation of bill reads: every bill of `bills.ts`, sent to each candidate model in
// [RUNS] runs through the proxy on this computer, scored as the app reads the answer, and reported
// in `plans/reports/` with the cheapest model that reaches the bar of `score.ts`:
//
//   npm run eval:read                        every candidate model
//   npm run eval:read -- claude-haiku-5-5    only those named
//
// Every call goes to the real model and is paid for, with the Anthropic key in `proxy/.dev.vars`,
// as `npm run dev` reads it; without one it says so and runs nothing. For each run of each model
// it starts `wrangler dev` on 127.0.0.1:8788 with its own state in a temporary directory, serving
// `worker.ts`, which asks each bill read of the model it names and reports its tokens and cost,
// and stops it, with everything it started, once that run is done, and however the evaluation
// ends, as `../shared/local-proxy.mjs` says. It runs on Linux and macOS, not on Windows. Calls go
// one after another, each from a new install; one the proxy turns down for the per-minute limit
// of its address is sent again once its Retry-After has passed.
//
// Node 22.18 or later runs the TypeScript files as they are, their types stripped.

import { mkdir, writeFile } from "node:fs/promises";
import { join, relative } from "node:path";
import { callProxy, commitOf, embedWeb, modelsToRun, REPORTS, stamp, startLocalProxy, stopProxy } from "../shared/local-proxy.mjs";
import { BILLS } from "./bills.ts";
import { CANDIDATES, errorScore, reportOf, requestOf, scoreAnswer } from "./score.ts";

/**
 * How many times each bill is sent to each model. A model does not read alike every time, so a
 * bill counts at its lowest run. Each run has a proxy of its own, as the limit of its address,
 * 100 calls a day, would stop a second run.
 */
const RUNS = 3;
/** The Worker the runs serve, from `proxy/`. */
const ENTRY = "eval/read/worker.ts";

/** One bill sent to [model] in [run], as an outcome for the report. */
async function readBill(bill, model, run) {
  const called = await callProxy("/v1/bill-analysis", requestOf(bill, crypto.randomUUID()), model);
  const score = called.error === null ? scoreAnswer(bill, called.answer) : errorScore(bill, called.error);
  return { billId: bill.id, model, run, ms: called.ms, costMicro: called.costMicro, answer: called.answer, error: called.error, score };
}

const models = await modelsToRun(CANDIDATES);
const startedAt = new Date();
const commit = commitOf();
embedWeb();

const outcomes = [];
for (const model of models) {
  for (let run = 1; run <= RUNS; run++) {
    await startLocalProxy(ENTRY);
    try {
      for (const bill of BILLS) {
        const outcome = await readBill(bill, model, run);
        outcomes.push(outcome);
        console.log(`${model} run ${run} ${bill.id}: ${outcome.score.pass ? "passed" : `failed, ${outcome.score.reason}`} in ${(outcome.ms / 1000).toFixed(1)} s`);
      }
    } finally {
      await stopProxy();
    }
  }
}

await mkdir(REPORTS, { recursive: true });
const report = join(REPORTS, `eval-bill-read-${stamp(startedAt)}.md`);
await writeFile(report, reportOf({ startedAt, commit, models, runs: RUNS, bills: BILLS, outcomes }));
console.log(`The report is in ${relative(process.cwd(), report)}.`);
