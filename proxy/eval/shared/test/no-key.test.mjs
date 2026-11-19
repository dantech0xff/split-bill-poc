// Checks that the runner of each evaluation, without what a run needs, says why and runs nothing:
// no Wrangler, no model and no report. Each runner runs from a copy of `eval/` under a stand-in
// for `proxy/` in a temporary directory, which holds the `.dev.vars` the test writes, so the
// proxy's own `.dev.vars` is never read and a key there can never be used; the stand-in holds no
// Wrangler and no web app either. Node's own runner runs it, as `npm test` does after the Worker's
// tests:
//
//   node --test eval/shared/test/no-key.test.mjs

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { cpSync, existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const EVAL = fileURLToPath(new URL("../../", import.meta.url));
const scratch = mkdtempSync(join(tmpdir(), "split-vibe-no-key-test-"));
let checkouts = 0;

after(() => rmSync(scratch, { recursive: true, force: true }));

/** A stand-in checkout: `proxy/eval/` copied, with [devVars] as `proxy/.dev.vars`, or none when null. */
function checkout(devVars) {
  const root = join(scratch, `checkout-${(checkouts += 1)}`);
  cpSync(EVAL, join(root, "proxy", "eval"), { recursive: true });
  if (devVars !== null) writeFileSync(join(root, "proxy", ".dev.vars"), devVars);
  return root;
}

/** Runs the runner of [evaluation] in [root] with [args]: how it ended and what it printed. */
function run(root, evaluation, args = []) {
  return new Promise((resolve) => {
    const proxy = join(root, "proxy");
    const runner = spawn(process.execPath, [join(proxy, "eval", evaluation, "run.mjs"), ...args], { cwd: proxy, stdio: ["ignore", "pipe", "pipe"] });
    let printed = "";
    runner.stdout.on("data", (chunk) => {
      printed += chunk;
    });
    runner.stderr.on("data", (chunk) => {
      printed += chunk;
    });
    const timer = setTimeout(() => runner.kill("SIGKILL"), 20_000);
    runner.on("exit", (code, signal) => {
      clearTimeout(timer);
      resolve({ code, signal, printed });
    });
  });
}

const NO_KEY = /^No Anthropic key, so nothing was run: the evaluation calls the real model, and each call is paid for\. Put the key into proxy\/\.dev\.vars as ANTHROPIC_API_KEY=…/;

for (const evaluation of ["read", "revise"]) {
  describe(`the runner of the evaluation of ${evaluation === "read" ? "bill reads" : "edits"}`, () => {
    for (const [what, devVars] of [
      ["without proxy/.dev.vars", null],
      ["when proxy/.dev.vars holds no key", "HASH_SALT=a-salt-of-sixteen-or-more\nSESSION_SIGNING_KEY=a-signing-key-of-at-least-thirty-two-bytes\n"],
      ["when the key is still the placeholder of the docs", "ANTHROPIC_API_KEY=<your Anthropic key>\nHASH_SALT=a-salt-of-sixteen-or-more\n"],
    ]) {
      it(`says there is no key and runs nothing ${what}`, async () => {
        const root = checkout(devVars);

        const { code, signal, printed } = await run(root, evaluation);

        assert.deepEqual({ code, signal }, { code: 1, signal: null });
        assert.match(printed, NO_KEY);
        assert.equal(existsSync(join(root, "plans")), false, "No report is written");
      });
    }

    it("refuses a model that is not a candidate before it looks for a key", async () => {
      const root = checkout(null);

      const { code, printed } = await run(root, evaluation, ["claude-haiku-5-5", "not-a-model"]);

      assert.equal(code, 1);
      assert.match(printed, /^Not a candidate model: not-a-model\. The candidates are claude-haiku-5-5, claude-sonnet-5-5, claude-opus-5-5\.\n$/);
    });

    it("refuses to run without the salt, even with a key", async () => {
      const root = checkout("ANTHROPIC_API_KEY=sk-ant-a-stand-in-that-is-no-key\n");

      const { code, printed } = await run(root, evaluation);

      assert.equal(code, 1);
      assert.match(printed, /^proxy\/\.dev\.vars needs HASH_SALT, of at least 16 characters/);
      assert.equal(existsSync(join(root, "plans")), false, "No report is written");
    });
  });
}
