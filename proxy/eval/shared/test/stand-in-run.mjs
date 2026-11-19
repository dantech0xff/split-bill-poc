// A stand-in for an evaluation's runner in the tests of `proxy-process.mjs`: it starts
// `stand-in-proxy.mjs` through `startProxy`, as a runner starts Wrangler, and prints "ready" once
// it is up. Then `wait` waits for a signal; the others wait for a line on their input and end as
// their name says: `stop` stops the proxy and returns, `exit` calls `process.exit()`, `throw`
// throws where nothing catches it, and `reject` fails at the top of the module. When the start
// fails, it prints why and exits with 1.
//
//   node stand-in-run.mjs <stop | exit | throw | reject | wait> <file> [stubborn | broken]

import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { startProxy, stopProxy } from "../proxy-process.mjs";

const PROXY = fileURLToPath(new URL("./stand-in-proxy.mjs", import.meta.url));
const [end, file, how = ""] = process.argv.slice(2);

try {
  await startProxy({ command: process.execPath, args: (state) => [PROXY, state, file, how], ready: async () => existsSync(file), startMs: 10_000 });
} catch (error) {
  console.log(`failed: ${(error instanceof Error ? error.message : String(error)).replace(/\s+/g, " ")}`);
  process.exit(1);
}
console.log("ready");
if (end !== "wait") {
  await new Promise((resolve) => process.stdin.once("data", resolve));
  process.stdin.destroy();
}

if (end === "stop") {
  await stopProxy();
} else if (end === "exit") {
  process.exit(3);
} else if (end === "throw") {
  setTimeout(() => {
    throw new Error("A mistake of the run");
  }, 0);
} else if (end === "reject") {
  await Promise.reject(new Error("A step of the run that failed"));
} else {
  setInterval(() => {}, 1_000);
}
