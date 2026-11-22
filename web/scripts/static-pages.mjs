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
//   referrer;
// - home.html, the home page /, which says what Split Vibe is and how to get it, in the words of
//   the Google Play listing in docs/store-listing.md, with the app's screenshots from src/landing/.
//   It loads the stylesheet of index.html and runs no script; its screenshots are copied into
//   assets/ under names that carry a hash of their bytes.
//
// A source that is missing stops the build, and so `npm run bundle`, saying which; so does
// Markdown the pages cannot show: an image, a link to a file no page serves, or an HTML comment
// other than the note a source may begin with.

import { createHash } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
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

/** The links in the footer of the home page, the legal pages and /delete-account, which `src/ui/site-footer.tsx` draws the same. */
export const SITE_LINKS = [
  { path: "/privacy", label: "Privacy policy" },
  { path: "/terms", label: "Terms of use" },
  { path: "/delete-account", label: "Delete account" },
];

export const DELETE_ACCOUNT_FILE = "delete-account.html";

/** The home page, /. */
export const HOME_FILE = "home.html";

/** Where the home page's screenshots are kept: WebP copies of the app's reference images, which `make-images.py` there makes. */
export const LANDING_DIRECTORY = fileURLToPath(new URL("../src/landing/", import.meta.url));

/**
 * The screenshots of the home page, by their names in src/landing/, where each is a light file and
 * a `-dark` one, with what each shows for whoever cannot see it.
 */
export const LANDING_IMAGES = [
  { name: "home-owed", alt: "Split Vibe's home: what friends owe you and what you owe, this month's spending and the latest bills" },
  { name: "new-split", alt: "A new split: scan the bill, take a photo, pick one or type it in, and say how to split it" },
  { name: "result", alt: "A bill read: its total, what Split Vibe understood, and what each of three people pays" },
  { name: "assistant", alt: "A change by a sentence, with each person's share before and after" },
  { name: "requests", alt: "Asking for the money: the split as a ticket to share as a picture" },
  { name: "group", alt: "A group: what you get back, each member's balance, and the payments that settle it" },
  { name: "insights", alt: "What the bills of the last 12 months came to, and by month" },
];

/** The size of every screenshot in pixels, as `make-images.py` writes them: the store's 824 x 1830, scaled to 600 wide. */
export const LANDING_IMAGE_SIZE = { width: 600, height: 1333 };

/** What the home page says about getting the app while it is not on Google Play yet. */
export const GET_THE_APP = "Coming soon to Google Play";

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
 * The note a source may begin with for whoever edits it: one HTML comment, alone on its lines,
 * which the page leaves out whole. A comment that a browser could end elsewhere than at its first
 * `-->`, at once as `<!-->` or `<!--->`, or at `--!>`, is not taken for one.
 */
const LEADING_NOTE = /^\s*<!--(?!-?>)(?:(?!--!?>)[\s\S])*-->[ \t]*(?:\r?\n|$)/;

/**
 * [markdown], the content of docs/[source], as HTML: GitHub's Markdown, headings with ids, and
 * links as [hrefOf] makes them. The note the source may begin with, [LEADING_NOTE], is left out;
 * any other HTML comment stops the build, saying on which line, so that the page never shows a
 * note, nor drops text with one, by mistake. Any other HTML in the Markdown is shown as text, so
 * that nothing in it can run, style the page or load anything. An image, or a link to a file no
 * page serves, stops the build too, saying where.
 */
export function renderLegalMarkdown(markdown, source) {
  const seen = new Map();
  const problems = [];
  const body = markdown.replace(LEADING_NOTE, "");
  const comment = body.indexOf("<!--");
  if (comment >= 0) {
    const line = markdown.slice(0, markdown.length - body.length + comment).split("\n").length;
    problems.push(`has an HTML comment on line ${line}: a source holds one only as the note it begins with, which the page leaves out`);
  }
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
      // A list's markers are drawn by the stylesheet, and Safari reads a list without markers of
      // its own as a list only when it says it is one.
      list(token) {
        const type = token.ordered ? "ol" : "ul";
        const start = token.ordered && token.start !== 1 ? ` start="${token.start}"` : "";
        const items = token.items.map((item) => this.listitem(item)).join("");
        return `<${type} role="list"${start}>\n${items}</${type}>\n`;
      },
      html({ text }) {
        return escapeHtml(text);
      },
    },
  });
  const html = marked.parse(body);
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

/** The head the pages share, up to their [title], whole, with the referrer policy [referrer]. */
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
<title>${escapeHtml(title)}</title>`;
}

/**
 * The legal [page] with [content], its rendered Markdown, under the link pages' bar and with
 * [stylesheet], the stylesheet tag of index.html. Content without a heading of the first level gets
 * the page's title as one, so that every page has its heading.
 */
export function legalPage(page, content, stylesheet) {
  const heading = /<h1\b/i.test(content) ? "" : `<h1 id="${escapeHtml(slugOf(page.title, new Map()))}">${escapeHtml(page.title)}</h1>\n`;
  return `${head(`${page.title} · Split Vibe`, "no-referrer")}
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
  return `${head("Delete your account · Split Vibe", "strict-origin-when-cross-origin", '<meta name="sv-google-client-id" content="">\n')}
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

/** The app's mark, the two halves of its launcher icon, which the stylesheet colours: [className] names its size. */
function mark(className) {
  return `<svg class="home-mark ${className}" viewBox="28 28.5 52 51" aria-hidden="true" focusable="false"><path class="home-mark__first" d="M51,28.5A23,23 0,0 0,51 74.5Z"/><path class="home-mark__second" d="M57,33.5A23,23 0,0 1,57 79.5Z"/></svg>`;
}

/**
 * The screenshot [name] of [LANDING_IMAGES] in a phone's frame, from its files in [shots], the
 * dark one in a dark theme; loaded when it comes near the screen unless [eager].
 */
function shot(shots, name, className, eager = false) {
  const image = LANDING_IMAGES.find((entry) => entry.name === name);
  const files = shots[name];
  if (image === undefined || files === undefined) throw new StaticPageError(`the home page has no screenshot named ${name}`);
  const { width, height } = LANDING_IMAGE_SIZE;
  const loading = eager ? "eager" : "lazy";
  return `<picture class="home-phone ${className}">
<source srcset="${files.dark}" media="(prefers-color-scheme: dark)" width="${width}" height="${height}">
<img src="${files.light}" alt="${escapeHtml(image.alt)}" width="${width}" height="${height}" loading="${loading}" decoding="async">
</picture>`;
}

/**
 * A section of the home page: its [title], its [body], already HTML, and its [visual] of the kind
 * [kind], a phone's screenshot or the ways to split, beside the text on wide screens, on its left
 * when [flip].
 */
function feature(title, body, visual, { flip = false, kind = "shot" } = {}) {
  return `<section class="home-feature${flip ? " home-feature--flip" : ""}">
<div class="home-feature__text">
<h2 class="home-title">${title}</h2>
${body}
</div>
<div class="home-feature__visual home-feature__visual--${kind}">
${visual}
</div>
</section>`;
}

/**
 * The home page, /: what Split Vibe does, in the words of its Google Play listing, with the app's
 * screenshots, whose files under /assets/ [shots] gives by name, and [stylesheet], the stylesheet
 * tag of index.html. It runs no script. Until the app is on Google Play, it says it is coming
 * there, [GET_THE_APP], and links nowhere for it.
 */
export function homePage(stylesheet, shots) {
  const description = "Scan a bill, say how to split it, and ask everyone for their share.";
  const modes = ["Equally", "By item", "By shares", "By percent", "Exact amounts"].map((mode) => `<li class="home-chip">${mode}</li>`).join("\n");
  const more = [
    ["Everyone picks their items", "Send a link to the table. Everyone opens it in a browser, without the app or an account, types their name and taps what they had. You see the picks as they happen."],
    ["Recurring bills", "Rent or a weekly dinner: a finished bill can repeat every week or month, as a draft for you to check."],
    [
      "Private by design",
      "Your bills, the people you split with and your ways to get paid are kept on your phone. Our server keeps only what you share with other people: bills you add to a group, payment requests you send, and bills you open for picking with a link. No ads, and no analytics or advertising SDK. Split Vibe never holds or moves money: payment links and codes open your own bank or payment app.",
    ],
    [
      "Free, with Pro for more AI",
      "Typing bills in, splitting, groups, payment requests, insights, exports and recurring bills are free. Reading a bill with AI takes one call, and changing a split by a sentence a fifth of one: you get 3 calls to try, 10 a month when you sign in with Google, or 300 a month with Pro, a monthly or yearly subscription through Google Play that you can cancel at any time.",
    ],
  ]
    .map(([term, detail]) => `<div class="home-more__item">\n<dt class="home-more__term">${term}</dt>\n<dd class="home-more__detail">${detail}</dd>\n</div>`)
    .join("\n");
  const notes = [
    "Split Vibe is in English, and reads bills printed in Latin script, such as English, Vietnamese and most European languages.",
    "Groups, tracked payment requests and bill links need a Google sign-in.",
    "AI can misread a bill: check the amounts before you ask anyone to pay.",
  ]
    .map((note) => `<li>${note}</li>`)
    .join("\n");
  const sections = [
    feature(
      "Read the bill, your way",
      `<p class="home-body">Scan up to five pages, take a photo, pick one from your gallery or type the bill in by hand. Then say how to split it.</p>
<p class="home-say">&ldquo;Split with Minh and Lan. Minh had the beers.&rdquo;</p>
<p class="home-aside">Your phone reads the text of the bill, and AI works out the items, taxes, fees and total. The photos never leave your phone.</p>`,
      shot(shots, "new-split", "home-phone--feature"),
    ),
    feature(
      "Split it fairly",
      `<p class="home-body">Taxes, fees, tips and discounts follow each person's items, and the shares always add up to the bill's total.</p>
<p class="home-aside">One person paid, or several did. A bill in another currency is shown in yours too, at the day's rate or at one you type in.</p>`,
      `<ul class="home-modes" aria-label="Ways to split">\n${modes}\n</ul>`,
      { flip: true, kind: "modes" },
    ),
    feature(
      "Change it with a sentence",
      `<p class="home-body">Type or say what changed. You see each person's share before and after, and nothing changes until you apply it. Undo puts it back.</p>
<p class="home-say">&ldquo;Lan didn't have beer&rdquo;</p>
<p class="home-say">&ldquo;Add a 10% tip&rdquo;</p>`,
      shot(shots, "assistant", "home-phone--feature"),
    ),
    feature(
      "Ask for the money",
      `<p class="home-body">Everyone gets their own message for any chat app, with the amount and how to pay you: VietQR, PromptPay, UPI, SEPA, PayPal.Me, a link from another payment app, or details to type in.</p>
<p class="home-aside">Signed in, you can see who has paid, confirm it and send reminders.</p>`,
      shot(shots, "requests", "home-phone--feature"),
      { flip: true },
    ),
    feature(
      "Groups for trips and shared flats",
      `<p class="home-body">Keep the bills of a trip or a flat together in one currency. See each member's balance across bills and currencies, and who pays whom to settle up.</p>
<p class="home-aside">Invite people with a link.</p>`,
      shot(shots, "group", "home-phone--feature"),
    ),
    feature(
      "Insights and exports",
      `<p class="home-body">See what your bills came to by month, category, person and group, with your own share. Export a period as CSV files or a PDF summary.</p>`,
      shot(shots, "insights", "home-phone--feature"),
      { flip: true },
    ),
  ];
  return `${head("Split Vibe: scan a bill, say how to split it", "no-referrer", `<meta name="description" content="${escapeHtml(description)}">\n`)}
${stylesheet}
</head>
<body>
<div id="app" class="home">
<main class="home-main">
<section class="home-hero">
<div class="home-hero__inner">
<div class="home-hero__copy">
<p class="home-brand">${mark("home-mark--brand")}Split Vibe</p>
<h1 class="home-hero__title">Scan a bill. Say how to split it.</h1>
<p class="home-hero__lede">Split Vibe works out who owes what and gives everyone a message to pay you, with a link or a QR code.</p>
<p class="home-get"><span class="home-status">${GET_THE_APP}</span><span class="home-get__note">For Android 8.0 and later</span></p>
</div>
<div class="home-hero__visual">
${mark("home-mark--signature")}
${shot(shots, "home-owed", "home-phone--back", true)}
${shot(shots, "result", "home-phone--front", true)}
</div>
</div>
</section>
${sections.join("\n")}
<section class="home-band" aria-labelledby="more">
<h2 class="home-title" id="more">And there is more</h2>
<dl class="home-more">
${more}
</dl>
</section>
<section class="home-band home-band--notes" aria-labelledby="good-to-know">
<h2 class="home-subtitle" id="good-to-know">Good to know</h2>
<ul class="home-notes" role="list">
${notes}
</ul>
</section>
<section class="home-end" aria-labelledby="get-the-app">
<div class="home-end__inner">
${mark("home-mark--end")}
<h2 class="home-title" id="get-the-app">Split the next bill with Split Vibe</h2>
<p class="home-get"><span class="home-status">${GET_THE_APP}</span><span class="home-get__note">For Android 8.0 and later</span></p>
</div>
</section>
</main>
${footer("/")}
</div>
</body>
</html>
`;
}

/**
 * Copies the screenshots of [LANDING_IMAGES] from [source] into [output]/assets/, each under a
 * name that carries a hash of its bytes, so that it may be cached for a year; gives the paths of
 * each one's light and dark file by its name. A file that is missing stops the build, saying which.
 */
export async function copyLandingImages(source = LANDING_DIRECTORY, output = OUTPUT_DIRECTORY) {
  await mkdir(join(output, "assets"), { recursive: true });
  const shots = {};
  for (const { name } of LANDING_IMAGES) {
    const files = {};
    for (const [theme, file] of [["light", `${name}.webp`], ["dark", `${name}-dark.webp`]]) {
      if (!existsSync(join(source, file))) {
        throw new StaticPageError(`the home page shows web/src/landing/${file}, which is missing: run \`python3 web/src/landing/make-images.py\`.`);
      }
      const bytes = await readFile(join(source, file));
      const hashed = `landing-${file.replace(/\.webp$/, "")}-${createHash("sha256").update(bytes).digest("hex").slice(0, 10)}.webp`;
      await writeFile(join(output, "assets", hashed), bytes);
      files[theme] = `/assets/${hashed}`;
    }
    shots[name] = files;
  }
  return shots;
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

/**
 * Writes the legal pages from the Markdown in [docs], the page /delete-account, and the home page
 * with its screenshots from [landing], next to the index.html in [output].
 */
export async function writeStaticPages(docs = DOCS_DIRECTORY, output = OUTPUT_DIRECTORY, landing = LANDING_DIRECTORY) {
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
  await writeFile(join(output, HOME_FILE), homePage(stylesheet, await copyLandingImages(landing, output)));
}

// Run as a script; imported, it only lends its functions.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  writeStaticPages().then(
    () => console.log(`Wrote ${[...LEGAL_PAGES.map((page) => page.file), DELETE_ACCOUNT_FILE, HOME_FILE].join(", ")} and the home page's screenshots.`),
    (error) => {
      console.error(error instanceof StaticPageError ? `The pages cannot be built: ${error.message}` : error);
      process.exit(1);
    },
  );
}
