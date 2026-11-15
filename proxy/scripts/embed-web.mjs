// Puts the web app that `web/` builds into the Worker's bundle, as the module
// `src/web-assets.generated.ts`, which git ignores. The Worker serves its page at /s/, /r/ and /g/
// and its scripts and styles at /assets/<name>; Workers Static Assets are not used, because the
// Worker is deployed through the Cloudflare API, which cannot upload them the same way.
//
// It reads `web/dist`, where `npm run build` in `web/` writes `index.html` and, in `assets/`, the
// page's hashed `.js` and `.css` files. Until `web/dist` exists, it writes a small placeholder
// page instead, so that the proxy's typecheck, tests and `wrangler dev` work without the web app.
// With `--require`, or `REQUIRE_WEB=1`, it stops instead, as a bundle for a deploy must hold the
// web app: `npm run bundle` asks for that.
//
// The page's two meta tags that the Worker fills, `sv-turnstile-site-key` and `sv-abuse-email`,
// are written as `<meta name="…" content="">`, and added before `</head>` when the page lacks
// them. A page the Worker could not serve whole stops the build: anything the checks of
// `web/scripts/page-checks.mjs` find, the same that `npm run build` in `web/` makes (a script or
// style not under `/assets/`, a reference with a scheme or to another origin, an inline script or
// style, a `style` attribute or an inline event handler, which the page's Content-Security-Policy
// would block, or a missing meta tag); a script or style it refers to that is not in `assets/`; or
// any other kind of file in `assets/`.

import { existsSync } from "node:fs";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { pageProblems } from "../../web/scripts/page-checks.mjs";

const DIST = fileURLToPath(new URL("../../web/dist/", import.meta.url));
const OUT = fileURLToPath(new URL("../src/web-assets.generated.ts", import.meta.url));
const CONTENT_TYPES = { ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8" };
const FILLED = ["sv-turnstile-site-key", "sv-abuse-email"];
const ASSET_NAME = /^[A-Za-z0-9._-]+$/;

const PLACEHOLDER_PAGE = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="no-referrer">
<meta name="sv-turnstile-site-key" content="">
<meta name="sv-abuse-email" content="">
<title>Split Vibe</title>
<link rel="stylesheet" href="/assets/placeholder.css">
</head>
<body>
<main>
<h1>Split Vibe</h1>
<p>This page is not available right now.</p>
</main>
</body>
</html>
`;
const PLACEHOLDER_STYLE = "body{font-family:system-ui,sans-serif;line-height:1.5;max-width:32rem;margin:4rem auto;padding:0 1.5rem}\n";

export class EmbedError extends Error {}

/** The page with each meta tag the Worker fills written as it expects, exactly once. */
export function withFilledTags(page) {
  let html = page;
  for (const name of FILLED) {
    const tag = `<meta name="${name}" content="">`;
    const found = html.match(new RegExp(`<meta\\b[^>]*\\bname\\s*=\\s*(["']?)${name}\\1(?=[\\s/>])[^>]*>`, "gi")) ?? [];
    if (found.length > 1) throw new EmbedError(`index.html has ${found.length} meta tags named ${name}; it needs one.`);
    if (found.length === 1) {
      html = html.replace(found[0], () => tag);
      continue;
    }
    const head = html.search(/<\/head\s*>/i);
    if (head < 0) throw new EmbedError(`index.html has neither a meta tag named ${name} nor a </head> to add one before.`);
    html = `${html.slice(0, head)}${tag}\n${html.slice(head)}`;
  }
  return html;
}

/**
 * Stops on what the page's Content-Security-Policy would block, by the checks `npm run build` in
 * `web/` makes, and on scripts and styles the Worker does not serve.
 */
export function checkPage(page, assets) {
  const problems = pageProblems(page);
  if (problems.length > 0) throw new EmbedError(`index.html does not fit the page's Content-Security-Policy:\n- ${problems.join("\n- ")}`);
  for (const [, , reference] of page.matchAll(/\b(?:src|href)\s*=\s*(["'])(.*?)\1/gi)) {
    const path = reference.split(/[?#]/)[0];
    if (reference === "data:," || !/\.(?:js|css)$/i.test(path)) continue;
    const name = /^\/assets\/([^/]+)$/.exec(path)?.[1];
    if (name === undefined || !assets.has(name)) {
      throw new EmbedError(`index.html refers to ${reference}, which the Worker does not serve: scripts and styles must be in /assets/ (Vite's base must be "/").`);
    }
  }
}

/** The page's scripts and styles, by name. */
async function assetsIn(directory) {
  const assets = new Map();
  if (!existsSync(directory)) return assets;
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const type = CONTENT_TYPES[extname(entry.name).toLowerCase()];
    if (!entry.isFile() || type === undefined || !ASSET_NAME.test(entry.name)) {
      throw new EmbedError(`web/dist/assets/${entry.name} is not a script or a style; the Worker serves only .js and .css files there.`);
    }
    assets.set(entry.name, { type, body: await readFile(join(directory, entry.name), "utf8") });
  }
  return assets;
}

/** The TypeScript module that holds [page] and [assets]. */
function moduleOf(source, page, assets) {
  const entries = [...assets.entries()]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([name, asset]) => `  [${JSON.stringify(name)}, { type: ${JSON.stringify(asset.type)}, body: ${JSON.stringify(asset.body)} }],`);
  return `// Generated by scripts/embed-web.mjs from ${source}. Git ignores it; do not edit it: run \`npm run build:web\` in proxy/.

/** The page every bill, request and invite link opens in a browser, with the meta tags the Worker fills left empty. */
export const WEB_PAGE = ${JSON.stringify(page)};

/** The page's scripts and styles, by their names under /assets/. */
export const WEB_ASSETS: ReadonlyMap<string, { type: string; body: string }> = new Map<string, { type: string; body: string }>([
${entries.join("\n")}
]);
`;
}

/** Whether this run must embed the web app, as for a deploy, and stop rather than write the placeholder. */
function webRequired() {
  return process.argv.includes("--require") || process.env.REQUIRE_WEB === "1";
}

async function main() {
  const built = existsSync(join(DIST, "index.html"));
  if (!built && webRequired()) {
    throw new EmbedError("web/dist was not built, and this bundle must hold the web app: run `npm ci --prefix ../web`, then `npm run build:web`.");
  }
  const assets = built ? await assetsIn(join(DIST, "assets")) : new Map([["placeholder.css", { type: CONTENT_TYPES[".css"], body: PLACEHOLDER_STYLE }]]);
  const page = withFilledTags(built ? await readFile(join(DIST, "index.html"), "utf8") : PLACEHOLDER_PAGE);
  checkPage(page, assets);
  const module = moduleOf(built ? "web/dist" : "a placeholder, as web/dist was not built", page, assets);
  const current = existsSync(OUT) ? await readFile(OUT, "utf8") : null;
  // Written only when it changes, so that `wrangler dev` does not reload for nothing.
  if (current !== module) await writeFile(OUT, module);
  if (!built) console.warn("web/dist was not built: the Worker serves a placeholder page. Run `npm run build:web` to embed the web app.");
}

// Run as a script; imported, it only lends its checks.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error instanceof EmbedError ? `The web app cannot be embedded: ${error.message}` : error);
    process.exit(1);
  });
}
