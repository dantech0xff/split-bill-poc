// What the built pages and the files under assets/ must be for the Worker to serve them under the
// pages' Content-Security-Policy. Both `npm run build` (scripts/verify-build.mjs) and the proxy's
// scripts/embed-web.mjs, which puts the pages into the Worker, check them with these, so that
// neither lets through what the other refuses.

/**
 * The kinds of file under assets/, by their ending, with the content type the Worker serves each
 * with: the pages' scripts, their stylesheet, the fonts that stylesheet loads, and the home page's
 * screenshots.
 */
export const ASSET_TYPES = {
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".woff2": "font/woff2",
  ".webp": "image/webp",
};

/**
 * A file the Worker serves under /assets/: a name that starts with a letter or a digit, then
 * letters, digits, `.`, `_` and `-`, of a kind in [ASSET_TYPES].
 */
const ASSET_FILE = /^[A-Za-z0-9][A-Za-z0-9_.-]*(\.js|\.css|\.woff2|\.webp)$/;

/** An image a page may show: a WebP file under /assets/, by its name alone. */
const PAGE_IMAGE = /^\/assets\/[A-Za-z0-9][A-Za-z0-9_.-]*\.webp$/;

/** The content type of [name], a file under assets/, or null when the Worker serves no such file. */
export function assetType(name) {
  const ending = ASSET_FILE.exec(name)?.[1];
  return ending === undefined ? null : ASSET_TYPES[ending];
}

/** A `url(...)` of a stylesheet, with its address between double quotes, single quotes or none. */
const URL_FUNCTION = /\burl\(\s*(?:"([^"]*)"|'([^']*)'|([^)\s"']*))\s*\)/gi;

/** The addresses [css] loads files from, those of its `url(...)`s, each read whole, in order. */
export function stylesheetUrls(css) {
  return [...css.matchAll(URL_FUNCTION)].map((match) => (match[1] ?? match[2] ?? match[3] ?? "").trim());
}

/**
 * What is wrong with [css], a stylesheet under assets/, given [assets], the names of the files
 * there; empty when nothing is. Its `url(...)`s may load only fonts of its own, `/assets/<name>.woff2`
 * files that are there, which `font-src 'self'` lets it. It may hold no `@import`, no `image-set()`,
 * which loads files without a `url(...)`, and no backslash, with which an escape could hide one.
 */
export function stylesheetProblems(css, assets) {
  const problems = [];
  if (/@import\b/i.test(css)) problems.push("an @import");
  if (/image-set\(/i.test(css)) problems.push("an image-set(), which loads files of its own");
  if (css.includes("\\")) problems.push("a backslash, with which an escape could hide what the stylesheet loads");
  for (const url of stylesheetUrls(css)) {
    const name = /^\/assets\/([^/?#]+\.woff2)$/.exec(url)?.[1];
    if (name === undefined) problems.push(`a url() to something other than an /assets/ font: ${url.slice(0, 80)}`);
    else if (!assets.has(name)) problems.push(`a font that is not under assets/: ${url}`);
  }
  return problems;
}

/**
 * The pages `npm run build` writes, each with the kind of checks it passes: `link` for the page of
 * /s/, /r/ and /g/, `delete-account` for /delete-account, `legal` for /privacy and /terms, and
 * `home` for the home page, /.
 */
export const PAGE_FILES = [
  { file: "index.html", kind: "link" },
  { file: "delete-account.html", kind: "delete-account" },
  { file: "privacy.html", kind: "legal" },
  { file: "terms.html", kind: "legal" },
  { file: "home.html", kind: "home" },
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
  home: [/<html lang="en">/, /<meta name="robots" content="noindex, nofollow">/, /<meta name="referrer" content="no-referrer">/],
};

/** The kinds of page that run no script, and whose links may go to the web, to an address or to the Worker's own pages. */
const STATIC_KINDS = new Set(["legal", "home"]);

/** Where a link of a legal page or of the home page may go: a web page, an address to write to, a section of the page, or a path of the Worker. */
const LEGAL_LINK = /^(?:https?:\/\/[^\s"'<>]+|mailto:[^\s"'<>]+|#[^\s"'<>]*|\/(?!\/)[^\s"'<>]*)$/i;

/**
 * Where a browser may start reading an attribute's name: after whitespace, after the `/` that HTML
 * lets stand between attributes, or right after a quoted value. A check that looked for whitespace
 * alone would miss `<p/style=…>` and `alt="x"src=…`.
 */
const ATTRIBUTE_START = String.raw`(?:^|[\s/"'])`;

/** Whether [html] has an attribute whose name [name] matches, by [ATTRIBUTE_START]. */
function hasAttribute(html, name) {
  return new RegExp(`${ATTRIBUTE_START}(?:${name})\\s*=`, "i").test(html);
}

/**
 * The attributes, besides `href`, `src` and `srcset`, with which an element loads or sends to an
 * address, or with which a meta tag acts on the page; a page that runs no script has none of them.
 */
const OTHER_LOADING_ATTRIBUTES = "http-equiv|poster|data|imagesrcset|imagesizes|background|formaction|action|ping|xlink:href|cite|longdesc|usemap|manifest|codebase|archive|lowsrc|dynsrc|profile";

/** The elements the home page is made of; it may have no other. */
const HOME_ELEMENTS = new Set([
  "html", "head", "meta", "link", "title", "body", "div", "main", "section", "footer", "nav", "p", "h1", "h2",
  "ul", "li", "dl", "dt", "dd", "span", "a", "picture", "source", "img", "svg", "path",
]);

/** The attributes the home page's elements may carry, besides the `aria-` ones. */
const HOME_ATTRIBUTES = new Set([
  "class", "id", "lang", "charset", "name", "content", "rel", "href", "crossorigin", "src", "srcset", "media",
  "width", "height", "alt", "loading", "decoding", "role", "focusable", "viewbox", "d",
]);

/**
 * A start tag as the home page writes it: a name, then attributes that each follow whitespace, each
 * with no value or with one in double quotes that holds no `<`, `>` or `"`.
 */
const START_TAG = /^<([a-z][a-z0-9-]*)((?:\s+[a-z][a-z0-9:-]*(?:="[^"<>]*")?)*)\s*\/?>$/i;

/**
 * What is wrong with [html] as a page of [kind] that the Worker serves; empty when nothing is.
 * Scripts and styles must be files under `/assets/`; no inline script or style, `style` attribute
 * or inline event handler, which the policy blocks; no reference with a scheme or to another
 * origin, but the empty `data:,` icon, which loads nothing; and the tags of [REQUIRED]. A legal
 * page and the home page run no script and have no attribute that loads or sends to an address
 * but their links, which may go to the web, to an address or to the Worker's own pages. A legal
 * page embeds nothing. The home page is made only of the elements and attributes it uses, and
 * shows WebP images under /assets/, each an `<img src>` or the `<source srcset>` of a picture.
 *
 * These checks stop a build whose pages would not work under their Content-Security-Policy, or
 * would do more than they are meant to; the policy that the Worker sends is what keeps a page from
 * running script or loading from elsewhere.
 */
export function pageProblems(html, kind = "link") {
  const problems = [];
  const required = REQUIRED[kind];
  if (required === undefined) return [`an unknown kind of page: ${kind}`];
  if (STATIC_KINDS.has(kind)) {
    // Any script element, closed or not, as `<svg><script href="…"/></svg>` would be.
    if (/<script\b/i.test(html)) problems.push(`a script, which a ${kind} page does not run`);
    if (hasAttribute(html, OTHER_LOADING_ATTRIBUTES)) problems.push(`an attribute that loads or acts on its own, which a ${kind} page does not have`);
  } else {
    for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
      const [, attributes, body] = match;
      if (!/\bsrc="\/assets\/[^"]+\.js"/.test(attributes)) problems.push(`a script that does not load an /assets/ file: ${match[0].slice(0, 80)}`);
      if (body.trim() !== "") problems.push("an inline script");
    }
  }
  if (/<style\b/i.test(html)) problems.push("an inline <style> element");
  if (hasAttribute(html, "style")) problems.push("a style attribute");
  if (hasAttribute(html, "on[a-z]+")) problems.push("an inline event handler");
  for (const match of html.matchAll(/<link\b([^>]*)>/gi)) {
    const href = /\bhref="([^"]*)"/.exec(match[1])?.[1] ?? "";
    if (/\brel="icon"/.test(match[1]) && href === "data:,") continue;
    if (!/^\/assets\/[^"]+\.css$/.test(href)) problems.push(`a link to something other than an /assets/ stylesheet: ${href}`);
  }
  let references = html;
  if (kind === "legal" && hasAttribute(html, "src(?:set)?")) problems.push("an image or other embedded resource, which a legal page does not show");
  if (kind === "home") problems.push(...homeProblems(html));
  if (STATIC_KINDS.has(kind)) {
    // Its links are judged here, and left out of the check of references below.
    references = html.replace(/<a\b[^>]*>/gi, (tag) => {
      const href = /\bhref="([^"]*)"/.exec(tag)?.[1];
      if (href === undefined || !LEGAL_LINK.test(href)) problems.push(`a link a ${kind} page may not have: ${tag.slice(0, 80)}`);
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

/**
 * What in the home page [html] is not made of what the page uses: every start tag must be one of
 * [START_TAG]'s shape, of an element of [HOME_ELEMENTS], with attributes of [HOME_ATTRIBUTES] or
 * `aria-` ones; a `src` only on an `<img>` and a `srcset` only on a `<source>`, each one WebP file
 * under /assets/ as [PAGE_IMAGE] says; an `href` only on a link or a stylesheet.
 */
function homeProblems(html) {
  const problems = [];
  for (const [tag] of html.matchAll(/<[a-z][^>]*>/gi)) {
    const match = START_TAG.exec(tag);
    if (match === null) {
      problems.push(`a tag the home page may not have: ${tag.slice(0, 80)}`);
      continue;
    }
    const element = match[1].toLowerCase();
    if (!HOME_ELEMENTS.has(element)) problems.push(`an element the home page may not have: <${element}>`);
    for (const [, name, value = ""] of match[2].matchAll(/\s+([a-z][a-z0-9:-]*)(?:="([^"]*)")?/gi)) {
      const attribute = name.toLowerCase();
      if (!HOME_ATTRIBUTES.has(attribute) && !attribute.startsWith("aria-")) {
        problems.push(`an attribute the home page may not have: ${attribute} on <${element}>`);
      } else if ((attribute === "src" && element !== "img") || (attribute === "srcset" && element !== "source")) {
        problems.push(`an image the home page may not show: ${tag.slice(0, 80)}`);
      } else if ((attribute === "src" || attribute === "srcset") && !PAGE_IMAGE.test(value)) {
        problems.push(`an image the home page may not show: ${tag.slice(0, 80)}`);
      } else if (attribute === "href" && element !== "a" && element !== "link") {
        problems.push(`an href the home page may not have: ${tag.slice(0, 80)}`);
      }
    }
  }
  return problems;
}
