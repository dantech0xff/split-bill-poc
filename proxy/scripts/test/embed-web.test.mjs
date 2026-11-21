// The checks that keep the Worker from serving anything but the web app's scripts, stylesheets and
// fonts, run on builds made in a temporary folder: the embed script's reading of `assets/` and the
// web app's own check of its build. Each build holds a stray image or a stylesheet that loads a
// file from another origin, and both refuse it.
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, describe, it } from "node:test";
import { buildProblems } from "../../../web/scripts/verify-build.mjs";
import { EmbedError, assetsIn } from "../embed-web.mjs";

const SCRIPT = "index-AbC123.js";
const STYLESHEET = "index-DeF456.css";
const FONT = "bricolage-grotesque-800-Gh78.woff2";
const FONT_FACE = `@font-face{font-family:"Bricolage Grotesque";src:url(/assets/${FONT})format("woff2")}`;

const folders = [];
after(() => {
  for (const folder of folders) rmSync(folder, { recursive: true, force: true });
});

/** A build in a folder of its own with [assets], by name, beside its pages, which load the script and the stylesheet. */
function build(assets) {
  const folder = mkdtempSync(join(tmpdir(), "split-vibe-build-"));
  folders.push(folder);
  mkdirSync(join(folder, "assets"));
  const tags = `<script type="module" crossorigin src="/assets/${SCRIPT}"></script>\n<link rel="stylesheet" crossorigin href="/assets/${STYLESHEET}">`;
  const head = '<meta charset="utf-8">\n<meta name="robots" content="noindex, nofollow">\n<link rel="icon" href="data:,">';
  const pages = {
    "index.html": `<!doctype html>\n<html lang="en">\n<head>\n${head}\n<meta name="referrer" content="no-referrer">\n<meta name="sv-turnstile-site-key" content="">\n<meta name="sv-abuse-email" content="">\n${tags}\n</head>\n<body><div id="app"></div></body>\n</html>\n`,
    "delete-account.html": `<!doctype html>\n<html lang="en">\n<head>\n${head}\n<meta name="referrer" content="strict-origin-when-cross-origin">\n<meta name="sv-google-client-id" content="">\n${tags}\n</head>\n<body><div id="app"></div></body>\n</html>\n`,
    "privacy.html": `<!doctype html>\n<html lang="en">\n<head>\n${head}\n<meta name="referrer" content="no-referrer">\n<link rel="stylesheet" crossorigin href="/assets/${STYLESHEET}">\n</head>\n<body><main><h1>Privacy policy</h1></main></body>\n</html>\n`,
  };
  pages["terms.html"] = pages["privacy.html"].replace("Privacy policy", "Terms of use");
  for (const [name, html] of Object.entries(pages)) writeFileSync(join(folder, name), html);
  for (const [name, body] of Object.entries(assets)) writeFileSync(join(folder, "assets", name), body);
  return folder;
}

const SOUND = { [SCRIPT]: 'document.title="Split Vibe";', [STYLESHEET]: `${FONT_FACE}h1{font-family:"Bricolage Grotesque"}`, [FONT]: "wOF2" };

describe("the checks of the web app's build", () => {
  it("take a build of a script, a stylesheet and the font it loads", async () => {
    const folder = build(SOUND);

    assert.deepEqual(buildProblems(folder).problems, []);
    assert.deepEqual([...(await assetsIn(join(folder, "assets"))).keys()].sort(), [FONT, SCRIPT, STYLESHEET]);
  });

  it("refuse an image under assets/", async () => {
    const folder = build({ ...SOUND, "logo.png": "not an image" });

    assert.deepEqual(buildProblems(folder).problems, ["unexpected file in the build: assets/logo.png"]);
    await assert.rejects(assetsIn(join(folder, "assets")), (error) => error instanceof EmbedError && /logo\.png is not a script, a style or a font/.test(error.message));
  });

  it("refuse a stylesheet that loads a file from another origin", async () => {
    const folder = build({ ...SOUND, [STYLESHEET]: `${FONT_FACE}.hero{background:url(https://evil.example/x.png)}` });

    assert.deepEqual(buildProblems(folder).problems, [`assets/${STYLESHEET}: a url() to something other than an /assets/ font: https://evil.example/x.png`]);
    await assert.rejects(assetsIn(join(folder, "assets")), (error) => error instanceof EmbedError && /evil\.example\/x\.png/.test(error.message));
  });
});
