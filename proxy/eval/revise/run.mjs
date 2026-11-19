// Runs the evaluation of edits by a sentence: every case of `cases.json`, sent to each candidate
// model in [RUNS] runs through the proxy on this computer, scored, and reported in `plans/reports/`:
//
//   npm run eval:revise                        every candidate model
//   npm run eval:revise -- claude-haiku-5-5    only those named
//
// Every call goes to the real model and is paid for, with the Anthropic key in `proxy/.dev.vars`,
// as `npm run dev` reads it; without one it refuses to start. For each run of each model it starts
// `wrangler dev` on 127.0.0.1:8788 with its own state in a temporary directory, and stops it, with
// everything it started, once that run is done, and however the evaluation ends, as
// `../shared/local-proxy.mjs` says. It runs on Linux and macOS, not on Windows. The proxy takes
// the model of each call from the header X-SplitVibe-Eval-Model, which only a proxy on a computer
// reads, and reports that call's tokens and cost. Calls go one after another, each from a new
// install; one the proxy turns down for the per-minute limit of its address is sent again once
// its Retry-After has passed.
//
// Node 22.18 or later runs `score.ts` as it is, its types stripped.

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join, relative } from "node:path";
import { callProxy, commitOf, embedWeb, modelsToRun, REPORTS, stamp, startLocalProxy, stopProxy } from "../shared/local-proxy.mjs";
import { CANDIDATES, casesOf, fixturesOf, reportOf, requestOf, scoreAnswer } from "./score.ts";

/**
 * How many times each case is sent to each model. A model does not answer alike every time, and
 * one case is nearly two points of the score, so a case counts at its lowest score. Each run has a
 * proxy of its own, as the limit of its address, 100 calls a day, would stop a second run.
 */
const RUNS = 3;

/** One case sent to [model] in [run], as an outcome for the report. */
async function runCase(evalCase, model, run, fixtures) {
  const called = await callProxy("/v1/split-revision", requestOf(evalCase, fixtures, crypto.randomUUID()), model);
  const score = called.error === null ? scoreAnswer(evalCase, called.answer) : { pass: false, reason: `ended in ${called.error}` };
  return { caseId: evalCase.id, model, run, ms: called.ms, costMicro: called.costMicro, answer: called.answer, error: called.error, score };
}

const models = await modelsToRun(CANDIDATES);
const fixtures = fixturesOf(JSON.parse(await readFile(new URL("./fixtures.json", import.meta.url), "utf8")));
const cases = casesOf(JSON.parse(await readFile(new URL("./cases.json", import.meta.url), "utf8")), fixtures);
const startedAt = new Date();
const commit = commitOf();
embedWeb();

const outcomes = [];
for (const model of models) {
  for (let run = 1; run <= RUNS; run++) {
    await startLocalProxy();
    try {
      for (const evalCase of cases) {
        const outcome = await runCase(evalCase, model, run, fixtures);
        outcomes.push(outcome);
        console.log(`${model} run ${run} ${evalCase.id}: ${outcome.score.pass ? "passed" : `failed, ${outcome.score.reason}`} in ${(outcome.ms / 1000).toFixed(1)} s`);
      }
    } finally {
      await stopProxy();
    }
  }
}

await mkdir(REPORTS, { recursive: true });
const report = join(REPORTS, `eval-split-revision-${stamp(startedAt)}.md`);
await writeFile(report, reportOf({ startedAt, commit, models, runs: RUNS, cases, outcomes }));
console.log(`The report is in ${relative(process.cwd(), report)}.`);
