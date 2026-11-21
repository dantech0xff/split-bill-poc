// @vitest-environment node
// The build writes files, so these run in Node rather than in a page.
import { describe, expect, it } from "vitest";
import { pageProblems } from "../scripts/page-checks.mjs";
import {
  LEGAL_PAGES,
  SITE_LINKS,
  StaticPageError,
  assetTagsOf,
  deleteAccountPage,
  legalPage,
  missingSources,
  renderLegalMarkdown,
  writeStaticPages,
} from "../scripts/static-pages.mjs";
import { SITE_LINKS as FOOTER_LINKS } from "../src/ui/site-footer";
import privacyFixture from "./fixtures/legal/privacy-policy.md?raw";
import termsFixture from "./fixtures/legal/terms.md?raw";

/** The fixtures' folder, which holds a privacy-policy.md and a terms.md as docs/ does. */
const FIXTURES = decodeURIComponent(new URL("./fixtures/legal/", import.meta.url).pathname);

const SCRIPT = '<script type="module" crossorigin src="/assets/index-AbC123.js"></script>';
const STYLESHEET = '<link rel="stylesheet" crossorigin href="/assets/index-DeF456.css">';
const INDEX = `<!doctype html>\n<html lang="en">\n<head>\n${SCRIPT}\n${STYLESHEET}\n</head>\n<body><div id="app"></div></body>\n</html>\n`;

const privacy = LEGAL_PAGES.find((page) => page.path === "/privacy");
const terms = LEGAL_PAGES.find((page) => page.path === "/terms");
if (privacy === undefined || terms === undefined) throw new Error("The legal pages are /privacy and /terms");

describe("the legal pages", () => {
  it("are /privacy and /terms, from docs/privacy-policy.md and docs/terms.md", () => {
    expect(LEGAL_PAGES.map(({ file, source, path, title }) => [file, source, path, title])).toEqual([
      ["privacy.html", "privacy-policy.md", "/privacy", "Privacy policy"],
      ["terms.html", "terms.md", "/terms", "Terms of use"],
    ]);
  });

  it("render GitHub's Markdown, with an id on every heading as GitHub makes them", () => {
    const html = renderLegalMarkdown(privacyFixture, "privacy-policy.md");

    expect(html).toContain('<h1 id="privacy-policy">Privacy policy</h1>');
    expect(html).toContain('<h2 id="what-we-keep">What we keep</h2>');
    expect(html).toContain('<h2 id="what-we-keep-1">What we keep</h2>');
    expect(html).toContain('<h2 id="deleting-your-account">Deleting your account</h2>');
    expect(html).toContain("<th>For how long</th>");
    expect(html).toContain("<td>Until the month ends</td>");
    expect(html).toMatch(/<li>Bills stay on your phone\.<ul role="list">\s*<li>Photos never leave it\.<\/li>/);
  });

  it("say each list is a list, as the stylesheet draws their markers and Safari reads one without them as a list only then", () => {
    const lists = [privacyFixture, termsFixture].flatMap((markdown) => [...renderLegalMarkdown(markdown, "terms.md").matchAll(/<(ul|ol)\b[^>]*>/g)].map((match) => match[0]));

    expect(lists).toEqual(['<ul role="list">', '<ul role="list">', '<ol role="list">']);
    expect(renderLegalMarkdown("3. Three\n4. Four\n", "terms.md")).toContain('<ol role="list" start="3">');
  });

  it("link the other legal page, sections, the Worker's own pages, the web and addresses", () => {
    const html = renderLegalMarkdown(privacyFixture, "privacy-policy.md");
    const links = [...html.matchAll(/<a href="([^"]*)"/g)].map((match) => match[1]);

    expect(links).toEqual(["/terms", "#deleting-your-account", "mailto:privacy@example.com", "https://www.anthropic.com/legal/privacy", "/delete-account"]);
    expect(html).toContain('title="Anthropic"');
    expect(renderLegalMarkdown(termsFixture, "terms.md")).toContain('<a href="/privacy#what-we-keep">privacy policy</a>');
  });

  it("leave out the note a source begins with, and show any other HTML in the Markdown as text", () => {
    const html = renderLegalMarkdown(privacyFixture, "privacy-policy.md");

    expect(html).not.toContain("A comment for the people");
    expect(html).not.toContain("&lt;!--");
    expect(html).toContain('<h1 id="privacy-policy">Privacy policy</h1>');
    expect(html).toContain("Some &lt;b&gt;raw&lt;/b&gt; HTML shows as text.");
    expect(renderLegalMarkdown('<script>alert(1)</script>\n\n<p style="color:red" onclick="x()">Hi</p>\n', "terms.md")).not.toMatch(/<script|<p style|onclick="/);
    // A note over several lines, after a blank line or two, with the text right after it.
    expect(renderLegalMarkdown("\n\n<!-- Draft.\nFor legal review. -->\nThe terms.\n", "terms.md")).toBe("<p>The terms.</p>\n");
  });

  it("stop the build at any other HTML comment, saying on which line, rather than show a note or drop text with one", () => {
    for (const [markdown, line] of [
      // Text after the comment on its line: the note would be published, or the text lost with it.
      ["<!-- Draft for legal review --> Published text.\n", 1],
      ["# Terms\n\n<!-- Draft for legal review --> Published text.\n", 3],
      // HTML between two comments: kept or dropped with them by mistake.
      ["<!-- a --><b>x</b><!-- b -->\n", 1],
      ["# Terms\n\nText <!-- a --><b>x</b><!-- b --> more.\n", 3],
      // A comment past the start, in a paragraph, one never closed, and two notes.
      ["# Terms\n\n<!-- Note -->\n\nText.\n", 3],
      ["Text <!-- note --> more.\n", 1],
      ["# Terms\n\nText <!-- never closed\n", 3],
      ["<!-- first -->\n<!-- second -->\n", 2],
      // Comments that a browser ends elsewhere than at the first -->.
      ["<!-->\nText.\n<!-- -->\n", 1],
      ["<!-- a --!> visible -->\n", 1],
      // Even in code: a source holds no comment but its note.
      ["Write `<!-- -->` to hide text.\n", 1],
    ] as const) {
      expect(() => renderLegalMarkdown(markdown, "terms.md"), markdown).toThrow(
        new StaticPageError(`docs/terms.md has an HTML comment on line ${line}: a source holds one only as the note it begins with, which the page leaves out.`),
      );
    }
  });

  it("stop the build at an image, or a link to a file no page serves, saying where", () => {
    expect(() => renderLegalMarkdown("![Logo](logo.png)\n", "terms.md")).toThrow(new StaticPageError("docs/terms.md shows an image (logo.png); the legal pages show none."));
    for (const link of ["proxy.md", "../README.md", "javascript:alert(1)", "screenshots/x.png"]) {
      expect(() => renderLegalMarkdown(`[here](${link})\n`, "privacy-policy.md"), link).toThrow(StaticPageError);
    }
    expect(() => renderLegalMarkdown("[here](proxy.md)\n", "privacy-policy.md")).toThrow(/^docs\/privacy-policy\.md links to proxy\.md, which no page of the Worker serves/);
  });

  it("are whole pages that load the link pages' stylesheet and no script, and pass the checks of a legal page", () => {
    const pages = [
      legalPage(privacy, renderLegalMarkdown(privacyFixture, privacy.source), STYLESHEET),
      legalPage(terms, renderLegalMarkdown(termsFixture, terms.source), STYLESHEET),
    ];

    for (const page of pages) {
      expect(pageProblems(page, "legal")).toEqual([]);
      expect(page).toContain(STYLESHEET);
      expect(page).not.toContain("<script");
      expect(page.match(/<h1\b/g)).toHaveLength(1);
    }
    expect(pages[0]).toContain("<title>Privacy policy · Split Vibe</title>");
    expect(pages[1]).toContain('<a class="footer__link" href="/terms" aria-current="page">Terms of use</a>');
  });

  it("give a page whose Markdown has no heading its title as one", () => {
    const page = legalPage(terms, "<p>Only a paragraph.</p>\n", STYLESHEET);

    expect(page).toContain('<h1 id="terms-of-use">Terms of use</h1>');
  });

  it("link the same pages in their footer as the deletion page does", () => {
    expect(SITE_LINKS).toEqual(FOOTER_LINKS);
  });
});

describe("the page /delete-account", () => {
  it("loads the web app's script and stylesheet, has the meta tag the Worker fills, and tells Google its origin", () => {
    const page = deleteAccountPage(SCRIPT, STYLESHEET);

    expect(pageProblems(page, "delete-account")).toEqual([]);
    expect(page).toContain(SCRIPT);
    expect(page).toContain(STYLESHEET);
    expect(page).toContain('<meta name="referrer" content="strict-origin-when-cross-origin">');
    expect(page).toContain("<title>Delete your account · Split Vibe</title>");
    expect(page).toContain("open Split Vibe, go to Settings and tap Delete account");
    // It is not a link page.
    expect(pageProblems(page, "link")).not.toEqual([]);
  });

  it("takes the tags of index.html, which must load one script and one stylesheet", () => {
    expect(assetTagsOf(INDEX)).toEqual({ script: SCRIPT, stylesheet: STYLESHEET });
    expect(() => assetTagsOf(INDEX.replace(SCRIPT, ""))).toThrow(StaticPageError);
    expect(() => assetTagsOf(INDEX.replace(STYLESHEET, `${STYLESHEET}\n${STYLESHEET}`))).toThrow(StaticPageError);
  });
});

describe("the build of the pages", () => {
  it("stops when a source is missing, saying which", async () => {
    expect(missingSources(FIXTURES)).toEqual([]);
    expect(missingSources("/no/such/folder/")).toEqual(["docs/privacy-policy.md", "docs/terms.md"]);

    await expect(writeStaticPages("/no/such/folder/", "/no/such/output/")).rejects.toThrow(
      new StaticPageError("/privacy and /terms are built from docs/privacy-policy.md and docs/terms.md, and docs/privacy-policy.md and docs/terms.md are missing."),
    );
  });
});
