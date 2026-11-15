// Readies the local proxy for the end-to-end tests: builds the web app and embeds it in the
// Worker with the proxy's `npm run build:web`, and gives a fresh local D1 database, under
// web/.e2e/state, the proxy's migrations. Every run starts from nothing, so that the hourly limits
// of earlier runs, which count every local request as one address, do not carry over.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const proxy = fileURLToPath(new URL("../../proxy/", import.meta.url));
const state = fileURLToPath(new URL("../.e2e/state/", import.meta.url));
const NEEDED = ["HASH_SALT", "SESSION_SIGNING_KEY", "GOOGLE_CLIENT_ID", "TURNSTILE_SITE_KEY", "TURNSTILE_SECRET", "ABUSE_EMAIL"];

const devVars = join(proxy, ".dev.vars");
const present = existsSync(devVars)
  ? readFileSync(devVars, "utf8")
      .split("\n")
      .map((line) => line.split("=")[0]?.trim())
  : [];
const missing = NEEDED.filter((name) => !present.includes(name));
if (missing.length > 0) {
  console.error(`proxy/.dev.vars lacks ${missing.join(", ")}: see "End-to-end tests" in web/README.md.`);
  process.exit(1);
}

function run(command, args, env = {}) {
  execFileSync(command, args, { cwd: proxy, stdio: "inherit", env: { ...process.env, ...env } });
}

run("npm", ["run", "build:web"]);
rmSync(state, { recursive: true, force: true });
run("npx", ["wrangler", "d1", "migrations", "apply", "split-vibe", "--local", "--persist-to", state], { CI: "1" });
