// What the built pages and the files under assets/ must be for the Worker to serve them under the
// pages' Content-Security-Policy. Both `npm run build` (scripts/verify-build.mjs) and the proxy's
// scripts/embed-web.mjs, which puts the pages into the Worker, check them with these, so that
// neither lets through what the other refuses.

/**
 * The kinds of file under assets/, by their ending, with the content type the Worker serves each
 * with: the pages' scripts, their stylesheet, and the fonts that stylesheet loads.
 */
export const ASSET_TYPES = {
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".woff2": "font/woff2",
};

/** A file the Worker serves under /assets/: a name of letters, digits, `.`, `_` and `-`, of a kind in [ASSET_TYPES]. */
const ASSET_FILE = /^[A-Za-z0-9_.-]+(\.js|\.css|\.woff2)$/;

/** The content type of [name], a file under assets/, or null when the Worker serves no such file. */
export function assetType(name) {
  const ending = ASSET_FILE.exec(name)?.[1];
  return ending === undefined ? null : ASSET_TYPES[ending];
}

/** The addresses [css] loads files from, those of its `url(...)`s, in order. */
export function stylesheetUrls(css) {
  return [...css.matchAll(/\burl\(\s*(["']?)([^"')]*)\1\s*\)/gi)].map((match) => match[2].trim());
}

/**
 * What is wrong with [css], a stylesheet under assets/, given [assets], the names of the files
 * there; empty when nothing is. It may load fonts of its own, `/assets/<name>.woff2` files that are
 * there, which `font-src 'self'` lets it, and nothing else: no image, no other origin, no `@import`.
 */
export function stylesheetProblems(css, assets) {
  const problems = [];
  if (/@import\b/i.test(css)) problems.push("an @import");
  for (const url of stylesheetUrls(css)) {
    const name = /^\/assets\/([^/?#]+\.woff2)$/.exec(url)?.[1];
    if (name === undefined) problems.push(`a url() to something other than an /assets/ font: ${url.slice(0, 80)}`);
    else if (!assets.has(name)) problems.push(`a font that is not under assets/: ${url}`);
  }
  return problems;
}

/**
 * The pages `npm run build` writes, each with the kind of checks it passes: `link` for the page of
 * /s/, /r/ and /g/, `delete-account` for /delete-account, and `legal` for /privacy and /terms.
 */
export const PAGE_FILES = [
  { file: "index.html", kind: "link" },
  { file: "delete-account.html", kind: "delete-account" },
  { file: "privacy.html", kind: "legal" },
  { file: "terms.html", kind: "legal" },
];

/** The tags each kind of page must carry, the meta tags the Worker fills among them. */
const REQUIRED = {
  link: [
    /<html lang="en">/,
    /<meta name="robots" content="noindex, nofollow">/,
    /<meta name="referrer" content="no-referrer">/,
    /<meta name="sv-turnstile-site-key" content="">/,
    /<meta name="sv-abuse-email" content="">/,
  ],
  "delete-account": [
    /<html lang="en">/,
    /<meta name="robots" content="noindex, nofollow">/,
    // Google Identity Services tells the page's origin from the referrer.
    /<meta name="referrer" content="strict-origin-when-cross-origin">/,
    /<meta name="sv-google-client-id" content="">/,
  ],
  legal: [/<html lang="en">/, /<meta name="robots" content="noindex, nofollow">/, /<meta name="referrer" content="no-referrer">/],
};

/** Where a link of a legal page may go: a web page, an address to write to, a section of the page, or a path of the Worker. */
const LEGAL_LINK = /^(?:https?:\/\/[^\s"'<>]+|mailto:[^\s"'<>]+|#[^\s"'<>]*|\/(?!\/)[^\s"'<>]*)$/i;

/**
 * What is wrong with [html] as a page of [kind] that the Worker serves; empty when nothing is.
 * Scripts and styles must be files under `/assets/`; no inline script or style, `style` attribute
 * or inline event handler, which the policy blocks; no reference with a scheme or to another
 * origin, but the empty `data:,` icon, which loads nothing; and the tags of [REQUIRED]. A legal
 * page runs no script and embeds nothing, and its links may go to the web, to an address or to the
 * Worker's own pages.
 */
export function pageProblems(html, kind = "link") {
  const problems = [];
  const required = REQUIRED[kind];
  if (required === undefined) return [`an unknown kind of page: ${kind}`];
  for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    if (kind === "legal") {
      problems.push("a script, which a legal page does not run");
      continue;
    }
    const [, attributes, body] = match;
    if (!/\bsrc="\/assets\/[^"]+\.js"/.test(attributes)) problems.push(`a script that does not load an /assets/ file: ${match[0].slice(0, 80)}`);
    if (body.trim() !== "") problems.push("an inline script");
  }
  if (/<style\b/i.test(html)) problems.push("an inline <style> element");
  if (/\sstyle\s*=/i.test(html)) problems.push("a style attribute");
  if (/\son[a-z]+\s*=/i.test(html)) problems.push("an inline event handler");
  for (const match of html.matchAll(/<link\b([^>]*)>/gi)) {
    const href = /\bhref="([^"]*)"/.exec(match[1])?.[1] ?? "";
    if (/\brel="icon"/.test(match[1]) && href === "data:,") continue;
    if (!/^\/assets\/[^"]+\.css$/.test(href)) problems.push(`a link to something other than an /assets/ stylesheet: ${href}`);
  }
  let references = html;
  if (kind === "legal") {
    if (/\ssrc\s*=/i.test(html)) problems.push("an image or other embedded resource, which a legal page does not show");
    // Its links are judged here, and left out of the check of references below.
    references = html.replace(/<a\b[^>]*>/gi, (tag) => {
      const href = /\bhref="([^"]*)"/.exec(tag)?.[1];
      if (href === undefined || !LEGAL_LINK.test(href)) problems.push(`a link a legal page may not have: ${tag.slice(0, 80)}`);
      return "";
    });
  }
  for (const match of references.matchAll(/\b(?:src|href)\s*=\s*(["']?)([^"'\s>]*)\1/gi)) {
    const reference = match[2];
    if (reference === "data:,") continue;
    if (/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(reference)) problems.push(`a reference with a scheme or to another origin: ${reference}`);
  }
  for (const pattern of required) {
    if (!pattern.test(html)) problems.push(`no ${pattern.source}`);
  }
  return problems;
}
