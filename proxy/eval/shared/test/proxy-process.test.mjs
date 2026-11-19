// Checks, with stand-ins for an evaluation's runner and for Wrangler, that the proxy a run starts
// never outlives the run, however the run ends. It needs no key and calls no model. Node's
// own runner runs it, as `npm test` does after the Worker's tests; POSIX only, as the runner is.
//
//   node --test eval/shared/test/proxy-process.test.mjs

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, afterEach, describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const RUN = fileURLToPath(new URL("./stand-in-run.mjs", import.meta.url));
const scratch = mkdtempSync(join(tmpdir(), "split-vibe-proxy-process-test-"));
/** The processes and directories a test started, which it must not leave behind even when it fails. */
const startedPids = [];
const startedStates = [];
let files = 0;

afterEach(() => {
  for (const pid of startedPids.splice(0)) {
    try {
      process.kill(pid, "SIGKILL");
    } catch {
      // Already gone, as it should be.
    }
  }
  for (const state of startedStates.splice(0)) rmSync(state, { recursive: true, force: true });
});

after(() => rmSync(scratch, { recursive: true, force: true }));

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Whether [pid] still runs. A process that ended but was never collected is only waiting to be: on Linux, /proc says so. */
function runs(pid) {
  try {
    process.kill(pid, 0);
  } catch {
    return false;
  }
  try {
    const stat = readFileSync(`/proc/${pid}/stat`, "utf8");
    return stat.charAt(stat.lastIndexOf(")") + 2) !== "Z";
  } catch {
    // No /proc, as on macOS, where an ended process is collected at once.
    return true;
  }
}

async function until(test, ms, what) {
  const deadline = Date.now() + ms;
  while (!test()) {
    if (Date.now() > deadline) assert.fail(`${what} within ${ms} ms`);
    await sleep(50);
  }
}

/**
 * Starts the stand-in run, which ends as [end] says once it is told to, with its proxy [how], and
 * waits until the proxy is up or the start failed: the run, its exit, what it printed, and the
 * proxy's processes and state, or null when it did not start.
 */
async function startRun(end, how = "") {
  const file = join(scratch, `proxy-${(files += 1)}.json`);
  const run = spawn(process.execPath, [RUN, end, file, how], { stdio: ["pipe", "pipe", "pipe"] });
  startedPids.push(run.pid);
  let printed = "";
  run.stdout.on("data", (chunk) => {
    printed += chunk;
  });
  run.stderr.resume();
  const exited = new Promise((resolve) => run.on("exit", (code, signal) => resolve({ code, signal })));
  await until(() => printed.includes("ready\n") || printed.includes("failed:"), 10_000, "The stand-in run started its proxy, or failed to");
  if (!printed.includes("ready\n")) return { run, exited, printed: () => printed, proxy: null };
  const proxy = JSON.parse(readFileSync(file, "utf8"));
  startedPids.push(...proxy.pids);
  startedStates.push(proxy.state);
  assert.ok(proxy.pids.every(runs), "The stand-in proxy and the process it started run");
  return { run, exited, printed: () => printed, proxy };
}

/** Waits until the proxy and what it started have stopped, and checks that its state is deleted unless [stateKept]. */
async function stopped(proxy, { stateKept = false } = {}) {
  await until(() => !proxy.pids.some(runs), 5_000, "The proxy and the process it started stopped");
  if (!stateKept) assert.equal(existsSync(proxy.state), false, "The proxy's state is deleted");
}

describe("the proxy a run of the evaluation starts", () => {
  it("stops with all it started, its state deleted, when the run stops it", async () => {
    const { run, exited, proxy } = await startRun("stop");

    run.stdin.end("now\n");

    assert.deepEqual(await exited, { code: 0, signal: null });
    await stopped(proxy);
  });

  for (const [end, code, what] of [
    ["exit", 3, "calls process.exit()"],
    ["throw", 1, "throws where nothing catches it"],
    ["reject", 1, "fails at the top of its module"],
  ]) {
    it(`stops when the run ${what} while it runs`, async () => {
      const { run, exited, proxy } = await startRun(end);

      run.stdin.end("now\n");

      assert.deepEqual(await exited, { code, signal: null });
      await stopped(proxy);
    });
  }

  for (const [signal, code] of [
    ["SIGINT", 130],
    ["SIGTERM", 143],
    ["SIGHUP", 129],
  ]) {
    it(`stops on ${signal}, as when the terminal is closed or Ctrl-C is pressed, and then the run exits`, async () => {
      const { run, exited, proxy } = await startRun("wait");

      run.kill(signal);

      assert.deepEqual(await exited, { code, signal: null });
      await stopped(proxy);
    });
  }

  it("is killed at once by a second signal while it is slow to stop", async () => {
    const { run, exited, proxy } = await startRun("wait", "stubborn");
    const asked = Date.now();

    run.kill("SIGINT");
    await sleep(300);
    run.kill("SIGINT");

    assert.deepEqual(await exited, { code: 130, signal: null });
    // The proxy ignores SIGTERM, so without the second signal the run would wait ten seconds for it.
    assert.ok(Date.now() - asked < 5_000, "The run exited at the second signal");
    await stopped(proxy);
  });

  it("is killed by its guard when the run itself is killed with SIGKILL, which no handler sees", async () => {
    const { run, exited, proxy } = await startRun("wait");

    run.kill("SIGKILL");

    assert.deepEqual(await exited, { code: null, signal: "SIGKILL" });
    // Its state stays behind in the temporary directory, holding no secret; the test deletes it.
    await stopped(proxy, { stateKept: true });
  });

  it("fails to start at once, saying what the proxy said, when the proxy ends before it is ready", async () => {
    const asked = Date.now();
    const { exited, printed, proxy } = await startRun("stop", "broken");

    assert.equal(proxy, null);
    assert.deepEqual(await exited, { code: 1, signal: null });
    assert.match(printed(), /failed: .* did not start: it ended\. What it said last: The stand-in proxy cannot start\./);
    assert.ok(Date.now() - asked < 5_000, "The start failed before its deadline of ten seconds");
  });
});
