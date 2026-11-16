// A stand-in for `wrangler dev` in the tests of `proxy-process.mjs`: it starts one more process, as
// Wrangler starts workerd, writes both of their ids and its directory of state to the file it is
// given, and runs until it is stopped. With `stubborn`, both ignore SIGTERM, as a proxy that hangs
// does; with `broken`, it ends at once, as Wrangler does when it cannot start.
//
//   node stand-in-proxy.mjs <state> <file> [stubborn | broken]

import { spawn } from "node:child_process";
import { renameSync, writeFileSync } from "node:fs";

const [state, file, how = ""] = process.argv.slice(2);

if (how === "broken") {
  console.error("The stand-in proxy cannot start.");
  process.exitCode = 2;
} else {
  const ignoreTerm = how === "stubborn" ? 'process.on("SIGTERM", () => {});' : "";
  if (ignoreTerm !== "") process.on("SIGTERM", () => {});
  const worker = spawn(process.execPath, ["-e", `${ignoreTerm} setInterval(() => {}, 1_000);`], { stdio: "ignore" });
  // Written whole, then moved into place, so that no reader finds half of it.
  writeFileSync(`${file}.part`, JSON.stringify({ pids: [process.pid, worker.pid], state }));
  renameSync(`${file}.part`, file);
  setInterval(() => {}, 1_000);
}
