// The proxy on this computer that the evaluations send their calls through, and what a runner
// checks before it starts one: shared by the evaluation of edits (`../revise/run.mjs`) and that of
// bill reads (`../read/run.mjs`).
//
// Every call goes to the real model and is paid for, with the Anthropic key in `proxy/.dev.vars`,
// as `npm run dev` reads it; without one a runner refuses to start. For each run of each model a
// runner starts `wrangler dev` on 127.0.0.1:8788 with its own state in a temporary directory, so
// that the counts of a run never mix with those of `npm run dev` on 8787 or of an earlier run, and
// stops it, with everything it started, once that run is done, and however the runner ends, as
// `proxy-process.mjs` says. It runs on Linux and macOS, not on Windows. The proxy takes the model
// of each call from the header X-SplitVibe-Eval-Model, which only a proxy on a computer reads, and
// reports that call's tokens and cost. Calls go one after another, each from a new install, so
// that no call waits on another; one the proxy turns down for the per-minute limit of its address
// is sent again once its Retry-After has passed.

import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { createServer } from "node:net";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { startProxy as startGuarded, stopProxy } from "./proxy-process.mjs";

export { stopProxy };

/** The proxy's directory, and the directory the reports of the evaluations go to. */
export const PROXY = fileURLToPath(new URL("../../", import.meta.url));
export const REPORTS = fileURLToPath(new URL("../../../plans/reports/", import.meta.url));
const PORT = 8788;
const ORIGIN = `http://127.0.0.1:${PORT}`;
/** How long `wrangler dev` may take to answer, and the proxy to end one call: its own deadline for the model is 120 seconds. */
const START_MS = 90_000;
const CALL_MS = 180_000;
/** How often a call turned down for the limit of its address is sent again. */
const RATE_RETRIES = 5;

/** Says why the run stops, and stops it. */
export function refuse(message) {
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

/**
 * The models a run asks: those named on its command line, or every one of [candidates]. First it
 * refuses to run, saying why, on Windows, for a name that is not one of [candidates], without the
 * Anthropic key or the salt in `proxy/.dev.vars`, and while something listens on its port: so
 * when it refuses, nothing has started and nothing is paid for.
 */
export async function modelsToRun(candidates) {
  if (process.platform === "win32") {
    refuse("The evaluation runs on Linux and macOS: it stops Wrangler and all it started by their process group, which Windows does not have.");
  }
  const named = process.argv.slice(2);
  const models = named.length > 0 ? named : [...candidates];
  const unknown = models.filter((model) => !candidates.includes(model));
  if (unknown.length > 0) refuse(`Not a candidate model: ${unknown.join(", ")}. The candidates are ${candidates.join(", ")}.`);

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
  return models;
}

/** Writes the module of the web app, which the Worker imports, as `npm run dev` writes it first. */
export function embedWeb() {
  execFileSync(process.execPath, ["scripts/embed-web.mjs"], { cwd: PROXY, stdio: "inherit" });
}

/**
 * Starts `wrangler dev` on [ORIGIN] with fresh state, serving the Worker of `wrangler.jsonc`, and
 * waits until it answers.
 */
export async function startLocalProxy() {
  try {
    await startGuarded({
      command: "npx",
      args: (state) => ["wrangler", "dev", "--ip", "127.0.0.1", "--port", String(PORT), "--persist-to", state],
      cwd: PROXY,
      env: { ...process.env, WRANGLER_SEND_METRICS: "false" },
      ready: answers,
      startMs: START_MS,
    });
  } catch (error) {
    refuse(`wrangler dev did not answer on ${ORIGIN}. ${error instanceof Error ? error.message : String(error)}`);
  }
}

/**
 * Sends [body] to [path] of the proxy as the app would, from a new install, asking [model]. What
 * the call came to: how long it took from sending to its last line, what the proxy reported it
 * cost in microdollars (null when it reported nothing), and either the answer or, in [error],
 * what ended the call without one: a request that failed, a status other than 200, an answer that
 * is not NDJSON, an error line, or a result the proxy did not report.
 */
export async function callProxy(path, body, model) {
  const sent = JSON.stringify(body);
  const failed = (ms, error, costMicro = null) => ({ ms, costMicro, answer: null, error });
  for (let attempt = 0; ; attempt++) {
    const started = performance.now();
    let response;
    let text;
    try {
      response = await fetch(`${ORIGIN}${path}`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "X-SplitVibe-Install": crypto.randomUUID(),
          "X-SplitVibe-Version": "999999999",
          "X-SplitVibe-Eval-Model": model,
        },
        body: sent,
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
    return { ms, costMicro: cost, answer: last.answer, error: null };
  }
}

/** The commit the run measured, marked when the proxy's code differs from it. */
export function commitOf() {
  try {
    const commit = execFileSync("git", ["rev-parse", "--short", "HEAD"], { cwd: PROXY, encoding: "utf8" }).trim();
    const changed = execFileSync("git", ["status", "--porcelain", "--", "src", "eval"], { cwd: PROXY, encoding: "utf8" }).trim() !== "";
    return changed ? `${commit}, with changes not committed` : commit;
  } catch {
    return "unknown";
  }
}

/** [date] in UTC as a report's name writes it: YYMMDD-HHMM. */
export function stamp(date) {
  const two = (number) => String(number).padStart(2, "0");
  return `${String(date.getUTCFullYear()).slice(2)}${two(date.getUTCMonth() + 1)}${two(date.getUTCDate())}-${two(date.getUTCHours())}${two(date.getUTCMinutes())}`;
}
