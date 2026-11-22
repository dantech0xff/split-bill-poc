// Puts the web app that `web/` builds into the Worker's bundle, as the module
// `src/web-assets.generated.ts`, which git ignores. The Worker serves its pages at /s/, /r/ and
// /g/ (`index.html`), at /delete-account (`delete-account.html`), at /privacy and /terms
// (`privacy.html` and `terms.html`, which `npm run build` in `web/` renders from
// docs/privacy-policy.md and docs/terms.md), and at / (`home.html`, the home page), and their
// scripts, styles, fonts and the home page's screenshots at /assets/<name>; Workers Static Assets
// are not used, because the Worker is deployed through the Cloudflare API, which cannot upload
// them the same way.
//
// It reads `web/dist`, where `npm run build` in `web/` writes those pages and, in `assets/`, their
// hashed `.js` and `.css` files, the `.woff2` fonts the stylesheet loads and the home page's
// `.webp` screenshots, which the module holds as base64 and the Worker serves as bytes. Until a
// page is there, it writes a small
// placeholder page in its place, so that the proxy's typecheck, tests and `wrangler dev` work
// without the web app. With
// `--require`, or `REQUIRE_WEB=1`, it stops instead, as a bundle for a deploy must hold the web
// app: `npm run bundle` asks for that.
//
// The meta tags that the Worker fills, `sv-turnstile-site-key` and `sv-abuse-email` on the link
// pages and `sv-google-client-id` on /delete-account, are written as `<meta name="…" content="">`,
// and added before `</head>` when a page lacks them. A page the Worker could not serve whole stops
// the build: anything the checks of `web/scripts/page-checks.mjs` find, the same that
// `npm run build` in `web/` makes (a script or style not under `/assets/`, a reference with a
// scheme or to another origin but the links of a legal page or the home page, an image the home
// page may not show, an inline script or style, a `style` attribute or an inline event handler,
// which the page's Content-Security-Policy would block, or a missing meta tag); a script, style or
// image it refers to that is not in `assets/`; a stylesheet with a `url()` to anything but a WOFF2
// font that is in `assets/`, an `@import`, an `image-set()` or a backslash; or any other kind of
// file in `assets/`.

import { existsSync } from "node:fs";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { ASSET_TYPES, assetType, pageProblems, stylesheetProblems } from "../../web/scripts/page-checks.mjs";

const DIST = fileURLToPath(new URL("../../web/dist/", import.meta.url));
const OUT = fileURLToPath(new URL("../src/web-assets.generated.ts", import.meta.url));

/** The pages the Worker serves, by their names in the module: the file, its kind of checks, and the meta tags the Worker fills in it. */
const PAGES = {
  link: { file: "index.html", kind: "link", filled: ["sv-turnstile-site-key", "sv-abuse-email"] },
  deleteAccount: { file: "delete-account.html", kind: "delete-account", filled: ["sv-google-client-id"] },
  privacy: { file: "privacy.html", kind: "legal", filled: [] },
  terms: { file: "terms.html", kind: "legal", filled: [] },
  home: { file: "home.html", kind: "home", filled: [] },
};

/** The kinds of file under assets/ that the module holds as bytes rather than text. */
const BINARY_ASSETS = new Set([".woff2", ".webp"]);

/** A placeholder page with [head] in its head, in the place of a page that was not built. */
function placeholder(head) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
${head}
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
}

/** The placeholder of each kind of page, with the tags that kind needs. */
const PLACEHOLDERS = {
  link: placeholder('<meta name="referrer" content="no-referrer">\n<meta name="sv-turnstile-site-key" content="">\n<meta name="sv-abuse-email" content="">'),
  "delete-account": placeholder('<meta name="referrer" content="strict-origin-when-cross-origin">\n<meta name="sv-google-client-id" content="">'),
  legal: placeholder('<meta name="referrer" content="no-referrer">'),
  home: placeholder('<meta name="referrer" content="no-referrer">'),
};
const PLACEHOLDER_STYLE = "body{font-family:system-ui,sans-serif;line-height:1.5;max-width:32rem;margin:4rem auto;padding:0 1.5rem}\n";

export class EmbedError extends Error {}

/** [page], the file [file], with each meta tag of [names] that the Worker fills written as it expects, exactly once. */
export function withFilledTags(page, names = PAGES.link.filled, file = PAGES.link.file) {
  let html = page;
  for (const name of names) {
    const tag = `<meta name="${name}" content="">`;
    const found = html.match(new RegExp(`<meta\\b[^>]*\\bname\\s*=\\s*(["']?)${name}\\1(?=[\\s/>])[^>]*>`, "gi")) ?? [];
    if (found.length > 1) throw new EmbedError(`${file} has ${found.length} meta tags named ${name}; it needs one.`);
    if (found.length === 1) {
      html = html.replace(found[0], () => tag);
      continue;
    }
    const head = html.search(/<\/head\s*>/i);
    if (head < 0) throw new EmbedError(`${file} has neither a meta tag named ${name} nor a </head> to add one before.`);
    html = `${html.slice(0, head)}${tag}\n${html.slice(head)}`;
  }
  return html;
}

/**
 * Stops on what the Content-Security-Policy of [page], the file [file] checked as a page of
 * [kind], would block, by the checks `npm run build` in `web/` makes, and on scripts, styles and
 * images the Worker does not serve.
 */
export function checkPage(page, assets, kind = PAGES.link.kind, file = PAGES.link.file) {
  const problems = pageProblems(page, kind);
  if (problems.length > 0) throw new EmbedError(`${file} does not fit its page's Content-Security-Policy:\n- ${problems.join("\n- ")}`);
  for (const [, , reference] of page.matchAll(/\b(?:src|srcset|href)\s*=\s*(["'])(.*?)\1/gi)) {
    const path = reference.split(/[?#]/)[0];
    if (reference === "data:," || !/\.(?:js|css|webp)$/i.test(path)) continue;
    const name = /^\/assets\/([^/]+)$/.exec(path)?.[1];
    if (name === undefined || !assets.has(name)) {
      throw new EmbedError(`${file} refers to ${reference}, which the Worker does not serve: scripts, styles and images must be in /assets/ (Vite's base must be "/").`);
    }
  }
}

/**
 * The pages' scripts, styles, fonts and images in [directory], by name: a script or a style as its
 * text, a font or an image as its bytes. Stops on any other kind of file, and on a stylesheet with a
 * `url()` to anything but a font among them, an `@import`, an `image-set()` or a backslash.
 */
export async function assetsIn(directory) {
  const assets = new Map();
  if (!existsSync(directory)) return assets;
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const type = entry.isFile() ? assetType(entry.name) : null;
    if (type === null) {
      throw new EmbedError(
        `web/dist/assets/${entry.name} is not a script, a style, a font or a WebP image; the Worker serves only .js, .css, .woff2 and .webp files there.`,
      );
    }
    const bytes = await readFile(join(directory, entry.name));
    assets.set(entry.name, { type, body: BINARY_ASSETS.has(extname(entry.name)) ? bytes : bytes.toString("utf8") });
  }
  const names = new Set(assets.keys());
  for (const [name, asset] of assets) {
    if (asset.type !== ASSET_TYPES[".css"]) continue;
    const problems = stylesheetProblems(asset.body, names);
    if (problems.length > 0) throw new EmbedError(`web/dist/assets/${name} loads what the Worker does not serve:\n- ${problems.join("\n- ")}`);
  }
  return assets;
}

/** [body] as the module writes it: text as a string, the bytes of a font or an image as base64 that the module decodes. */
function bodyOf(body) {
  return typeof body === "string" ? JSON.stringify(body) : `bytesOf(${JSON.stringify(body.toString("base64"))})`;
}

/** The TypeScript module that holds [pages] and [assets]. */
function moduleOf(source, pages, assets) {
  const entries = [...assets.entries()]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([name, asset]) => `  [${JSON.stringify(name)}, { type: ${JSON.stringify(asset.type)}, body: ${bodyOf(asset.body)} }],`);
  return `// Generated by scripts/embed-web.mjs from ${source}. Git ignores it; do not edit it: run \`npm run build:web\` in proxy/.

/** The page every bill, request and invite link opens in a browser, with the meta tags the Worker fills left empty. */
export const WEB_PAGE = ${JSON.stringify(pages.link)};

/** The page /delete-account, with the meta tag the Worker fills left empty. */
export const DELETE_ACCOUNT_PAGE = ${JSON.stringify(pages.deleteAccount)};

/** The pages /privacy and /terms, as they were rendered from docs/ when the web app was built. */
export const LEGAL_PAGES: { readonly privacy: string; readonly terms: string } = {
  privacy: ${JSON.stringify(pages.privacy)},
  terms: ${JSON.stringify(pages.terms)},
};

/** The home page, /, as it was written when the web app was built. */
export const HOME_PAGE = ${JSON.stringify(pages.home)};

/** [base64] as the bytes it stands for: the module holds each font and image as base64 text. */
function bytesOf(base64: string): Uint8Array {
  const text = atob(base64);
  const bytes = new Uint8Array(text.length);
  for (let index = 0; index < text.length; index += 1) bytes[index] = text.charCodeAt(index);
  return bytes;
}

/** The pages' scripts and styles, as text, and the fonts their stylesheet loads and the home page's images, as bytes, by their names under /assets/. */
export const WEB_ASSETS: ReadonlyMap<string, { type: string; body: string | Uint8Array }> = new Map<string, { type: string; body: string | Uint8Array }>([
${entries.join("\n")}
]);
`;
}

/** Whether this run must embed the web app, as for a deploy, and stop rather than write a placeholder. */
function webRequired() {
  return process.argv.includes("--require") || process.env.REQUIRE_WEB === "1";
}

async function main() {
  const missing = Object.values(PAGES)
    .map((page) => page.file)
    .filter((file) => !existsSync(join(DIST, file)));
  if (missing.length > 0 && webRequired()) {
    throw new EmbedError(`web/dist lacks ${missing.join(", ")}, and this bundle must hold the web app: run \`npm ci --prefix ../web\`, then \`npm run build:web\`.`);
  }
  const assets = await assetsIn(join(DIST, "assets"));
  if (missing.length > 0) assets.set("placeholder.css", { type: ASSET_TYPES[".css"], body: PLACEHOLDER_STYLE });
  const pages = {};
  for (const [name, page] of Object.entries(PAGES)) {
    const html = missing.includes(page.file) ? PLACEHOLDERS[page.kind] : await readFile(join(DIST, page.file), "utf8");
    const filled = withFilledTags(html, page.filled, page.file);
    checkPage(filled, assets, page.kind, page.file);
    pages[name] = filled;
  }
  const source = missing.length === 0 ? "web/dist" : `web/dist, with a placeholder for ${missing.join(", ")}, which it lacks`;
  const module = moduleOf(source, pages, assets);
  const current = existsSync(OUT) ? await readFile(OUT, "utf8") : null;
  // Written only when it changes, so that `wrangler dev` does not reload for nothing.
  if (current !== module) await writeFile(OUT, module);
  if (missing.length > 0) {
    console.warn(`web/dist lacks ${missing.join(", ")}: the Worker serves a placeholder page for each. Run \`npm run build:web\` to embed the web app.`);
  }
}

// Run as a script; imported, it only lends its checks.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error instanceof EmbedError ? `The web app cannot be embedded: ${error.message}` : error);
    process.exit(1);
  });
}
