// Runs one command for the evaluation's runner, and stops everything in its process group once the
// runner is gone, however it went: stopped, crashed, killed with SIGKILL, or cut off with its
// terminal. `proxy-process.mjs` starts this guard at the head of a process group of its own, in
// which the command and all it starts run (npx, Wrangler and workerd), and holds the other end of
// the guard's input, which closes only when the runner closes it or exits. It is never run by hand:
//
//   node guard.mjs <command> [<argument>…]

import { spawn } from "node:child_process";
import { constants } from "node:os";

const [command, ...args] = process.argv.slice(2);
const child = spawn(command, args, { stdio: ["ignore", "inherit", "inherit"] });

/** Kills the whole group at once, this guard with it: the runner that would stop it is gone. */
function stopAll() {
  process.kill(-process.pid, "SIGKILL");
}

child.on("error", (error) => {
  console.error(`${command} could not start: ${error.message}`);
  process.exit(127);
});
// The guard ends with the command, so that the runner sees a proxy that stopped by itself. The
// SIGTERM the runner sends the group asks the command to stop; the guard waits for it to, and so
// collects it rather than leave it behind without a parent.
child.on("exit", (code, signal) => process.exit(code ?? 128 + (constants.signals[signal] ?? 0)));
process.on("SIGTERM", () => {});
process.stdin.on("end", stopAll);
process.stdin.on("error", stopAll);
process.stdin.resume();
