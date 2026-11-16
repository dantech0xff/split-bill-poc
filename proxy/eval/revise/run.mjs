// Runs the evaluation of edits by a sentence: every case of `cases.json`, sent to each candidate
// model through the proxy on this computer, scored, and reported in `plans/reports/`:
//
//   npm run eval:revise                        every candidate model
//   npm run eval:revise -- claude-haiku-5-5    only those named
//
// Every call goes to the real model and is paid for, with the Anthropic key in `proxy/.dev.vars`,
// as `npm run dev` reads it; without one it refuses to start. For each model it starts
// `wrangler dev` on 127.0.0.1:8788 with its own state in a temporary directory, so that the counts
// of a run never mix with those of `npm run dev` on 8787 or of an earlier run, and stops it, with
// everything it started, once that model is done or the run is interrupted. The proxy takes the
// model of each call from the header X-SplitVibe-Eval-Model, which only a proxy on a computer
// reads, and reports that call's tokens and cost. Calls go one after another, each from a new
// install, so that no call waits on another; one the proxy turns down for the per-minute limit of
// its address is sent again once its Retry-After has passed.
//
// Node 22.18 or later runs `score.ts` as it is, its types stripped.

import { execFileSync, spawn } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { CANDIDATES, casesOf, fixturesOf, reportOf, requestOf, scoreAnswer } from "./score.ts";

const PROXY = fileURLToPath(new URL("../../", import.meta.url));
const REPORTS = fileURLToPath(new URL("../../../plans/reports/", import.meta.url));
const PORT = 8788;
const ORIGIN = `http://127.0.0.1:${PORT}`;
/** How long `wrangler dev` may take to answer, and the proxy to end one call: its own deadline for the model is 120 seconds. */
const START_MS = 90_000;
const CALL_MS = 180_000;
/** How often a call turned down for the limit of its address is sent again. */
const RATE_RETRIES = 5;

/** The proxy this run started and has not stopped yet, so that an interrupted run stops it too. */
let running = null;

function refuse(message) {
  console.error(message);
  process.exit(1);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** The value of [name] in `proxy/.dev.vars`, which is never printed; "" when it is not there. */
async function devVar(name) {
  let text;
  try {
    text = await readFile(join(PROXY, ".dev.vars"), "utf8");
  } catch {
    return "";
  }
  for (const line of text.split(/\r?\n/)) {
    const match = /^\s*([A-Za-z0-9_]+)\s*=\s*(.*)$/.exec(line);
    if (match !== null && match[1] === name) return match[2].trim().replace(/^(["'])(.*)\1$/, "$2");
  }
  return "";
}

function portFree(port) {
  return new Promise((resolve) => {
    const server = createServer();
    server.once("error", () => resolve(false));
    server.listen(port, "127.0.0.1", () => server.close(() => resolve(true)));
  });
}

/** Whether the proxy on [ORIGIN] answers. */
async function answers() {
  try {
    const response = await fetch(`${ORIGIN}/v1/usage`, { headers: { "X-SplitVibe-Install": crypto.randomUUID() }, signal: AbortSignal.timeout(2_000) });
    await response.arrayBuffer();
    return response.status === 200;
  } catch {
    return false;
  }
}

/** Sends [signal] to the process group of [child]: npx, Wrangler and the workerd it runs. */
function signalGroup(child, signal) {
  try {
    process.kill(-child.pid, signal);
    return true;
  } catch {
    // The group has no process left.
    return false;
  }
}

/** Starts `wrangler dev` on [ORIGIN] with fresh state, and waits until it answers. */
async function startProxy() {
  const state = await mkdtemp(join(tmpdir(), "split-vibe-eval-"));
  const child = spawn("npx", ["wrangler", "dev", "--ip", "127.0.0.1", "--port", String(PORT), "--persist-to", state], {
    cwd: PROXY,
    detached: true,
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env, WRANGLER_SEND_METRICS: "false" },
  });
  running = { child, state };
  let said = "";
  const keep = (chunk) => {
    said = (said + chunk).slice(-4_000);
  };
  child.stdout.on("data", keep);
  child.stderr.on("data", keep);
  const deadline = Date.now() + START_MS;
  while (Date.now() < deadline && child.exitCode === null) {
    if (await answers()) return running;
    await sleep(500);
  }
  await stopProxy();
  refuse(`wrangler dev did not answer on ${ORIGIN}. What it said last:\n${said}`);
}

/** Stops the proxy this run started, everything it started with it, and deletes its state. */
async function stopProxy() {
  if (running === null) return;
  const { child, state } = running;
  running = null;
  if (signalGroup(child, "SIGTERM")) {
    const deadline = Date.now() + 10_000;
    while (Date.now() < deadline && signalGroup(child, 0)) await sleep(200);
    signalGroup(child, "SIGKILL");
  }
  await rm(state, { recursive: true, force: true });
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    stopProxy().finally(() => process.exit(130));
  });
}

/** One case sent to [model], as an outcome for the report. */
async function runCase(evalCase, model, fixtures) {
  const body = JSON.stringify(requestOf(evalCase, fixtures, crypto.randomUUID()));
  const outcome = (ms, fields) => ({ caseId: evalCase.id, model, ms, costMicro: null, answer: null, error: null, ...fields });
  const failed = (ms, error, costMicro = null) => outcome(ms, { error, costMicro, score: { pass: false, reason: `ended in ${error}` } });
  for (let attempt = 0; ; attempt++) {
    const started = performance.now();
    let response;
    let text;
    try {
      response = await fetch(`${ORIGIN}/v1/split-revision`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "X-SplitVibe-Install": crypto.randomUUID(),
          "X-SplitVibe-Version": "999999999",
          "X-SplitVibe-Eval-Model": model,
        },
        body,
        signal: AbortSignal.timeout(CALL_MS),
      });
      text = await response.text();
    } catch (error) {
      return failed(performance.now() - started, `a request that failed (${error instanceof Error ? error.name : "unknown"})`);
    }
    const ms = performance.now() - started;
    if (response.status === 429 && attempt < RATE_RETRIES && /"rate_limited"/.test(text)) {
      await sleep((Number(response.headers.get("retry-after")) || 60) * 1_000 + 500);
      continue;
    }
    if (response.status !== 200) return failed(ms, `${response.status} ${text.slice(0, 80)}`);
    let last;
    try {
      last = JSON.parse(text.trim().split("\n").pop() ?? "");
    } catch {
      return failed(ms, "an answer that is not NDJSON");
    }
    const cost = typeof last.eval?.costMicro === "number" ? last.eval.costMicro : null;
    if (last.type === "error") return failed(ms, `the error ${last.code}`, cost);
    if (last.type !== "result" || last.eval === undefined) return failed(ms, "an answer the proxy did not report");
    return outcome(ms, { costMicro: cost, answer: last.answer, score: scoreAnswer(evalCase, last.answer) });
  }
}

/** The commit the run measured, marked when the proxy's code differs from it. */
function commitOf() {
  try {
    const commit = execFileSync("git", ["rev-parse", "--short", "HEAD"], { cwd: PROXY, encoding: "utf8" }).trim();
    const changed = execFileSync("git", ["status", "--porcelain", "--", "src", "eval"], { cwd: PROXY, encoding: "utf8" }).trim() !== "";
    return changed ? `${commit}, with changes not committed` : commit;
  } catch {
    return "unknown";
  }
}

function stamp(date) {
  const two = (number) => String(number).padStart(2, "0");
  return `${String(date.getUTCFullYear()).slice(2)}${two(date.getUTCMonth() + 1)}${two(date.getUTCDate())}-${two(date.getUTCHours())}${two(date.getUTCMinutes())}`;
}

const named = process.argv.slice(2);
const models = named.length > 0 ? named : [...CANDIDATES];
const unknown = models.filter((model) => !CANDIDATES.includes(model));
if (unknown.length > 0) refuse(`Not a candidate model: ${unknown.join(", ")}. The candidates are ${CANDIDATES.join(", ")}.`);

const key = await devVar("ANTHROPIC_API_KEY");
if (key === "" || key.startsWith("<")) {
  refuse(
    "No Anthropic key, so nothing was run: the evaluation calls the real model, and each call is paid for. " +
      "Put the key into proxy/.dev.vars as ANTHROPIC_API_KEY=…, as docs/proxy.md says under “Run it on a computer”, then run this again.",
  );
}
if (new TextEncoder().encode(await devVar("HASH_SALT")).byteLength < 16) {
  refuse("proxy/.dev.vars needs HASH_SALT, of at least 16 characters, as docs/proxy.md says under “Run it on a computer”.");
}
if (!(await portFree(PORT))) refuse(`Port ${PORT} is in use. Stop what listens there (lsof -i :${PORT}), then run this again.`);

const fixtures = fixturesOf(JSON.parse(await readFile(new URL("./fixtures.json", import.meta.url), "utf8")));
const cases = casesOf(JSON.parse(await readFile(new URL("./cases.json", import.meta.url), "utf8")), fixtures);
const startedAt = new Date();
const commit = commitOf();
// The Worker imports the module of the web app, as `npm run dev` writes it first.
execFileSync(process.execPath, ["scripts/embed-web.mjs"], { cwd: PROXY, stdio: "inherit" });

const outcomes = [];
for (const model of models) {
  await startProxy();
  try {
    for (const evalCase of cases) {
      const outcome = await runCase(evalCase, model, fixtures);
      outcomes.push(outcome);
      console.log(`${model} ${evalCase.id}: ${outcome.score.pass ? "passed" : `failed, ${outcome.score.reason}`} in ${(outcome.ms / 1000).toFixed(1)} s`);
    }
  } finally {
    await stopProxy();
  }
}

await mkdir(REPORTS, { recursive: true });
const report = join(REPORTS, `eval-split-revision-${stamp(startedAt)}.md`);
await writeFile(report, reportOf({ startedAt, commit, models, cases, outcomes }));
console.log(`The report is in ${relative(process.cwd(), report)}.`);
