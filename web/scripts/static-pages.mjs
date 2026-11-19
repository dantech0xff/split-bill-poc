// Writes the pages the Worker serves besides the link pages into web/dist, once `vite build` has
// written index.html and its assets:
//
// - privacy.html and terms.html, the pages /privacy and /terms, rendered here from
//   docs/privacy-policy.md and docs/terms.md into static HTML that loads the stylesheet of
//   index.html and runs no script, so that the Worker never parses Markdown;
// - delete-account.html, the page /delete-account, which loads the script and the stylesheet of
//   index.html: that script draws the deletion page at that path. It has the meta tag
//   sv-google-client-id, which the Worker fills, and the referrer policy
//   strict-origin-when-cross-origin, as Google Identity Services tells the page's origin from the
//   referrer.
//
// A source that is missing stops the build, and so `npm run bundle`, saying which; so does
// Markdown the pages cannot show: an image, or a link to a file no page serves.

import { existsSync } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { Marked } from "marked";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";

/** Where the sources of the legal pages are kept: the repository's docs/. */
export const DOCS_DIRECTORY = fileURLToPath(new URL("../../docs/", import.meta.url));

/** The legal pages: the file written, its source in docs/, its path on the Worker, and its title. */
export const LEGAL_PAGES = [
  { file: "privacy.html", source: "privacy-policy.md", path: "/privacy", title: "Privacy policy" },
  { file: "terms.html", source: "terms.md", path: "/terms", title: "Terms of use" },
];

/** The links in the footer of the legal pages and of /delete-account, which `src/ui/site-footer.tsx` draws the same. */
export const SITE_LINKS = [
  { path: "/privacy", label: "Privacy policy" },
  { path: "/terms", label: "Terms of use" },
  { path: "/delete-account", label: "Delete account" },
];

export const DELETE_ACCOUNT_FILE = "delete-account.html";

export class StaticPageError extends Error {}

const ENTITIES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
const DECODED = { amp: "&", lt: "<", gt: ">", quot: '"', "#39": "'" };

/** [text] as it may stand in HTML, between tags or in an attribute between double quotes. */
function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (character) => ENTITIES[character]);
}

/**
 * Where a link goes on the Worker: a web page or an address as it is, a section of the page, or a
 * path of the Worker; a link to the other legal page's Markdown goes to that page, so that the
 * sources link to each other as they do on GitHub. Null for anything else.
 */
function hrefOf(href) {
  if (/^(?:https?:\/\/|mailto:)/i.test(href) || href.startsWith("#") || /^\/(?!\/)/.test(href)) return href;
  const hash = href.indexOf("#");
  const file = (hash < 0 ? href : href.slice(0, hash)).replace(/^\.\//, "");
  const page = LEGAL_PAGES.find((legal) => legal.source === file);
  return page === undefined ? null : `${page.path}${hash < 0 ? "" : href.slice(hash)}`;
}

/**
 * The id of a heading whose HTML is [html], as GitHub makes one, so that a link to a section works
 * here as it does there: the text in lower case, without punctuation, spaces made hyphens, and a
 * number added to a heading met again.
 */
function slugOf(html, seen) {
  const text = html.replace(/<[^>]*>/g, "").replace(/&(amp|lt|gt|quot|#39);/g, (_, entity) => DECODED[entity]);
  const base = text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}\p{Pc}\- ]/gu, "")
    .replace(/ /g, "-");
  const times = seen.get(base) ?? 0;
  seen.set(base, times + 1);
  return times === 0 ? base : `${base}-${times}`;
}

/**
 * [markdown], the content of docs/[source], as HTML: GitHub's Markdown, headings with ids, and
 * links as [hrefOf] makes them. HTML comments are left out; any other HTML in the Markdown is
 * shown as text, so that nothing in it can run, style the page or load anything. An image, or a
 * link to a file no page serves, stops the build, saying where.
 */
export function renderLegalMarkdown(markdown, source) {
  const seen = new Map();
  const problems = [];
  const marked = new Marked({ gfm: true, async: false });
  marked.use({
    renderer: {
      heading({ tokens, depth }) {
        const inner = this.parser.parseInline(tokens);
        return `<h${depth} id="${escapeHtml(slugOf(inner, seen))}">${inner}</h${depth}>\n`;
      },
      link({ href, title, tokens }) {
        const inner = this.parser.parseInline(tokens);
        const target = hrefOf(href);
        if (target === null) {
          problems.push(`links to ${href}, which no page of the Worker serves: link to a web page, a mailto: address, a #section, a path of the Worker, privacy-policy.md or terms.md`);
          return inner;
        }
        const titled = typeof title === "string" && title !== "" ? ` title="${escapeHtml(title)}"` : "";
        return `<a href="${escapeHtml(target)}"${titled}>${inner}</a>`;
      },
      image({ href }) {
        problems.push(`shows an image (${href}); the legal pages show none`);
        return "";
      },
      html({ text }) {
        return /^\s*<!--[\s\S]*-->\s*$/.test(text) ? "" : escapeHtml(text);
      },
    },
  });
  const html = marked.parse(markdown);
  if (problems.length > 0) throw new StaticPageError(`docs/${source} ${problems.join("; it ")}.`);
  return html;
}

/** The links of [SITE_LINKS] as a footer, the one to the page at [current] marked as the page shown. */
function footer(current) {
  const links = SITE_LINKS.map(({ path, label }) => {
    const here = path === current ? ' aria-current="page"' : "";
    return `<li><a class="footer__link" href="${path}"${here}>${label}</a></li>`;
  });
  return `<footer class="footer type-body-small">
<nav aria-label="Split Vibe">
<ul class="footer__links">
${links.join("\n")}
</ul>
</nav>
</footer>`;
}

/** The head both kinds of page share, up to their title, with the referrer policy [referrer]. */
function head(title, referrer, extra = "") {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="${referrer}">
<meta name="color-scheme" content="light dark">
${extra}<link rel="icon" href="data:,">
<title>${escapeHtml(title)} · Split Vibe</title>`;
}

/**
 * The legal [page] with [content], its rendered Markdown, under the link pages' bar and with
 * [stylesheet], the stylesheet tag of index.html. Content without a heading of the first level gets
 * the page's title as one, so that every page has its heading.
 */
export function legalPage(page, content, stylesheet) {
  const heading = /<h1\b/i.test(content) ? "" : `<h1 id="${escapeHtml(slugOf(page.title, new Map()))}">${escapeHtml(page.title)}</h1>\n`;
  return `${head(page.title, "no-referrer")}
${stylesheet}
</head>
<body>
<div id="app">
<header class="app-bar">
<p class="app-bar__brand type-title-medium">Split Vibe</p>
</header>
<main class="page prose">
${heading}${content}</main>
${footer(page.path)}
</div>
</body>
</html>
`;
}

/** The page /delete-account: the web app's [script] and [stylesheet], the tags of index.html, which draw it. */
export function deleteAccountPage(script, stylesheet) {
  return `${head("Delete your account", "strict-origin-when-cross-origin", '<meta name="sv-google-client-id" content="">\n')}
${script}
${stylesheet}
</head>
<body>
<div id="app"></div>
<noscript>Deleting your account on this page needs JavaScript. You can also delete it in the app: open Split Vibe, go to Settings and tap Delete account.</noscript>
</body>
</html>
`;
}

/** The tags of index.html that load the web app's one script and one stylesheet, as `vite build` wrote them. */
export function assetTagsOf(index) {
  const scripts = index.match(/<script\b[^>]*\bsrc="\/assets\/[^"]+\.js"[^>]*><\/script>/g) ?? [];
  const stylesheets = index.match(/<link\b[^>]*\brel="stylesheet"[^>]*\bhref="\/assets\/[^"]+\.css"[^>]*>/g) ?? [];
  if (scripts.length !== 1 || stylesheets.length !== 1) {
    throw new StaticPageError(`index.html loads ${scripts.length} scripts and ${stylesheets.length} stylesheets from /assets/; the other pages take one of each.`);
  }
  return { script: scripts[0], stylesheet: stylesheets[0] };
}

/** The sources of the legal pages that [docs] lacks, as `docs/<name>`. */
export function missingSources(docs) {
  return LEGAL_PAGES.filter((page) => !existsSync(join(docs, page.source))).map((page) => `docs/${page.source}`);
}

/** Writes the legal pages from the Markdown in [docs], and the page /delete-account, next to the index.html in [output]. */
export async function writeStaticPages(docs = DOCS_DIRECTORY, output = OUTPUT_DIRECTORY) {
  const missing = missingSources(docs);
  if (missing.length > 0) {
    throw new StaticPageError(
      `/privacy and /terms are built from docs/privacy-policy.md and docs/terms.md, and ${missing.join(" and ")} ${missing.length === 1 ? "is" : "are"} missing.`,
    );
  }
  const { script, stylesheet } = assetTagsOf(await readFile(join(output, "index.html"), "utf8"));
  for (const page of LEGAL_PAGES) {
    const content = renderLegalMarkdown(await readFile(join(docs, page.source), "utf8"), page.source);
    await writeFile(join(output, page.file), legalPage(page, content, stylesheet));
  }
  await writeFile(join(output, DELETE_ACCOUNT_FILE), deleteAccountPage(script, stylesheet));
}

// Run as a script; imported, it only lends its functions.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  writeStaticPages().then(
    () => console.log(`Wrote ${[...LEGAL_PAGES.map((page) => page.file), DELETE_ACCOUNT_FILE].join(", ")}.`),
    (error) => {
      console.error(error instanceof StaticPageError ? `The pages cannot be built: ${error.message}` : error);
      process.exit(1);
    },
  );
}
