// The proxy that a run of the evaluation starts on this computer, which never outlives the run.
// It runs under `guard.mjs`, at the head of a process group of its own, so that one signal reaches
// the proxy and everything it started: npx, Wrangler and the workerd that holds the key. The run
// stops it when it is done with it; on SIGINT, SIGTERM and SIGHUP, after which it exits, at once on
// a second signal; and at every exit, that of an error or of `process.exit()` included. When the
// run cannot do even that, killed with SIGKILL for instance, its end of the guard's input closes,
// and the guard kills the group itself. POSIX only: Windows has no process groups to signal.
//
// `local-proxy.mjs` starts Wrangler through here for the runner of each evaluation, and
// `test/proxy-process.test.mjs` checks every one of those ends with a stand-in for it.

import { spawn } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { constants, tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const GUARD = fileURLToPath(new URL("./guard.mjs", import.meta.url));
/** How long the proxy may take to stop once asked, before its group is killed. */
const STOP_MS = 10_000;
/** How much of what the proxy said is kept, to tell why it did not start. */
const SAID_CHARS = 4_000;

/** The proxy started and not stopped yet: the guard that leads its group, its state, and its stop once asked. */
let running = null;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Starts [command] with the arguments that [args] gives for a fresh directory of state, in a
 * process group of its own, and waits until [ready] resolves true. When it ends first, or is not
 * ready within [startMs], it is stopped and this throws, with what it said last.
 */
export async function startProxy({ command, args, cwd, env, ready, startMs }) {
  if (running !== null) throw new Error("A proxy is running already");
  stopProxyOnExit();
  const state = mkdtempSync(join(tmpdir(), "split-vibe-eval-"));
  const child = spawn(process.execPath, [GUARD, command, ...args(state)], { cwd, env, detached: true, stdio: ["pipe", "pipe", "pipe"] });
  running = { child, state, stopped: null };
  let said = "";
  let failure = null;
  const keep = (chunk) => {
    said = (said + chunk).slice(-SAID_CHARS);
  };
  child.stdout.on("data", keep);
  child.stderr.on("data", keep);
  child.on("error", (error) => {
    failure = error;
  });
  const deadline = Date.now() + startMs;
  while (Date.now() < deadline && failure === null && child.exitCode === null && child.signalCode === null) {
    if (await ready()) return;
    await sleep(200);
  }
  await stopProxy();
  const why = failure !== null ? `it could not start (${failure.message})` : child.exitCode !== null || child.signalCode !== null ? "it ended" : `it was not ready within ${startMs / 1000} s`;
  throw new Error(`${command} did not start: ${why}. What it said last:\n${said}`);
}

/** Stops the proxy and everything it started, and deletes its state. A second call waits for the same stop. */
export function stopProxy() {
  // [stop] may forget the proxy before it first waits, when its group has already ended.
  const proxy = running;
  if (proxy === null) return Promise.resolve();
  proxy.stopped ??= stop(proxy);
  return proxy.stopped;
}

/** Asks the group to stop, kills what is left of it after [STOP_MS], and forgets it. */
async function stop(proxy) {
  if (signalGroup(proxy.child, "SIGTERM")) {
    const deadline = Date.now() + STOP_MS;
    while (Date.now() < deadline && signalGroup(proxy.child, 0)) await sleep(100);
    signalGroup(proxy.child, "SIGKILL");
  }
  forget(proxy);
}

/** Kills the group at once and forgets it, as the run exits: an exit's handlers may not wait. */
function stopNow() {
  if (running === null) return;
  signalGroup(running.child, "SIGKILL");
  forget(running);
}

function forget(proxy) {
  proxy.child.stdin.destroy();
  rmSync(proxy.state, { recursive: true, force: true, maxRetries: 5 });
  if (running === proxy) running = null;
}

/** Sends [signal] to the group the guard leads; false once no process is left in it. */
function signalGroup(child, signal) {
  try {
    process.kill(-child.pid, signal);
    return true;
  } catch {
    return false;
  }
}

let handling = false;

/**
 * Makes the run stop the proxy however it ends, from its first start on. SIGINT, SIGTERM and
 * SIGHUP stop it, then exit with the signal's code; a second signal while it stops exits at once.
 * Every exit kills what is left of it.
 */
function stopProxyOnExit() {
  if (handling) return;
  handling = true;
  process.on("exit", stopNow);
  let signalled = false;
  for (const signal of ["SIGINT", "SIGTERM", "SIGHUP"]) {
    process.on(signal, () => {
      const code = 128 + constants.signals[signal];
      if (signalled) process.exit(code);
      signalled = true;
      stopProxy().finally(() => process.exit(code));
    });
  }
}
