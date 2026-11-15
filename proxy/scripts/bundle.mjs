// Bundles the Worker as a deploy through the Cloudflare API uploads it, without deploying anything:
// builds the web app in `web/` and embeds it with the requirement on, so that the bundle can never
// hold the placeholder page, then has Wrangler write the bundle into the directory given as the
// first argument, or in BUNDLE_DIR, relative to `proxy/`:
//
//   npm run bundle -- <dir>
//
// The web app's packages must be installed first: `npm ci --prefix ../web`.

import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const PROXY = fileURLToPath(new URL("../", import.meta.url));
const outdir = (process.argv[2] ?? process.env.BUNDLE_DIR ?? "").trim();
if (outdir === "") {
  console.error("Say where the bundle goes: `npm run bundle -- <dir>`, or BUNDLE_DIR=<dir> npm run bundle.");
  process.exit(2);
}

/** Runs [command] in `proxy/` with [args], the web app required throughout; throws when it fails. */
function run(command, args) {
  execFileSync(command, args, { cwd: PROXY, stdio: "inherit", env: { ...process.env, REQUIRE_WEB: "1" } });
}

try {
  run("npm", ["run", "build:web"]);
  run("npx", ["wrangler", "deploy", "--dry-run", "--outdir", outdir]);
} catch {
  // The step that failed has said why.
  process.exit(1);
}
