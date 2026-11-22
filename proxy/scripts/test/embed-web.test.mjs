// The checks that keep the Worker from serving anything but the web app's scripts, stylesheets,
// fonts and the home page's WebP images, run on builds made in a temporary folder: the embed
// script's reading of `assets/` and of a page, and the web app's own check of its build. A build
// that holds a stray file, a stylesheet that loads from another origin or a page that shows an
// image it does not hold is refused by both.
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, describe, it } from "node:test";
import { buildProblems } from "../../../web/scripts/verify-build.mjs";
import { EmbedError, assetsIn, checkPage } from "../embed-web.mjs";

const SCRIPT = "index-AbC123.js";
const STYLESHEET = "index-DeF456.css";
const FONT = "bricolage-grotesque-800-Gh78.woff2";
const FONT_FACE = `@font-face{font-family:"Bricolage Grotesque";src:url(/assets/${FONT})format("woff2")}`;
const IMAGE = "landing-result-0123456789.webp";
const DARK_IMAGE = "landing-result-dark-9876543210.webp";
/** The home page's picture of [IMAGE], with [DARK_IMAGE] in a dark theme. */
const PICTURE = `<picture><source srcset="/assets/${DARK_IMAGE}" media="(prefers-color-scheme: dark)"><img src="/assets/${IMAGE}" alt="A bill read"></picture>`;

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
  pages["home.html"] = pages["privacy.html"].replace("<h1>Privacy policy</h1>", `<h1>Split Vibe</h1>${PICTURE}`);
  for (const [name, html] of Object.entries(pages)) writeFileSync(join(folder, name), html);
  for (const [name, body] of Object.entries(assets)) writeFileSync(join(folder, "assets", name), body);
  return folder;
}

const SOUND = {
  [SCRIPT]: 'document.title="Split Vibe";',
  [STYLESHEET]: `${FONT_FACE}h1{font-family:"Bricolage Grotesque"}`,
  [FONT]: "wOF2",
  [IMAGE]: "RIFF\u0000\u0000\u0000\u0000WEBP",
  [DARK_IMAGE]: "RIFF\u0000\u0000\u0000\u0000WEBP",
};

describe("the checks of the web app's build", () => {
  it("take a build of a script, a stylesheet, the font it loads and the home page's images", async () => {
    const folder = build(SOUND);

    assert.deepEqual(buildProblems(folder).problems, []);
    const assets = await assetsIn(join(folder, "assets"));
    assert.deepEqual([...assets.keys()].sort(), [FONT, SCRIPT, STYLESHEET, IMAGE, DARK_IMAGE].sort());
    // A font and an image are held as bytes, a script and a style as text.
    assert.equal(Buffer.isBuffer(assets.get(IMAGE).body), true);
    assert.equal(assets.get(IMAGE).type, "image/webp");
    assert.equal(Buffer.isBuffer(assets.get(FONT).body), true);
    assert.equal(typeof assets.get(SCRIPT).body, "string");
  });

  it("refuse an image under assets/ that is not WebP", async () => {
    const folder = build({ ...SOUND, "logo.png": "not an image" });

    assert.deepEqual(buildProblems(folder).problems, ["unexpected file in the build: assets/logo.png"]);
    await assert.rejects(
      assetsIn(join(folder, "assets")),
      (error) => error instanceof EmbedError && /logo\.png is not a script, a style, a font or a WebP image/.test(error.message),
    );
  });

  it("refuse a home page that shows an image the build does not hold, and an image no page shows", () => {
    const { [DARK_IMAGE]: _dark, ...withoutDark } = SOUND;
    assert.deepEqual(buildProblems(build(withoutDark)).problems, [`home.html: refers to /assets/${DARK_IMAGE}, which the build does not hold`]);
    assert.deepEqual(buildProblems(build({ ...SOUND, "landing-stray-0000000000.webp": SOUND[IMAGE] })).problems, [
      "assets/landing-stray-0000000000.webp: an image no page shows",
    ]);
  });

  it("refuse in the embedding a home page that shows an image the build does not hold", async () => {
    const folder = build(SOUND);
    const assets = await assetsIn(join(folder, "assets"));
    const page = `<!doctype html>\n<html lang="en">\n<head>\n<meta name="robots" content="noindex, nofollow">\n<meta name="referrer" content="no-referrer">\n</head>\n<body>${PICTURE}</body>\n</html>\n`;

    assert.doesNotThrow(() => checkPage(page, assets, "home", "home.html"));
    assets.delete(DARK_IMAGE);
    assert.throws(() => checkPage(page, assets, "home", "home.html"), (error) => error instanceof EmbedError && error.message.includes(DARK_IMAGE));
  });

  it("refuse a stylesheet that loads a file from another origin", async () => {
    const folder = build({ ...SOUND, [STYLESHEET]: `${FONT_FACE}.hero{background:url(https://evil.example/x.png)}` });

    assert.deepEqual(buildProblems(folder).problems, [`assets/${STYLESHEET}: a url() to something other than an /assets/ font: https://evil.example/x.png`]);
    await assert.rejects(assetsIn(join(folder, "assets")), (error) => error instanceof EmbedError && /evil\.example\/x\.png/.test(error.message));
  });
});
