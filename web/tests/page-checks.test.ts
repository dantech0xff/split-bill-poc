import { describe, expect, it } from "vitest";
import { PAGE_FILES, assetType, pageProblems, stylesheetProblems, stylesheetUrls } from "../scripts/page-checks.mjs";

/** A page as `vite build` writes it, with [head] and [body] added to its head and body. */
function page(head = "", body = ""): string {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="robots" content="noindex, nofollow">
    <meta name="referrer" content="no-referrer">
    <meta name="sv-turnstile-site-key" content="">
    <meta name="sv-abuse-email" content="">
    <link rel="icon" href="data:,">
    <script type="module" crossorigin src="/assets/index-AbC123.js"></script>
    <link rel="stylesheet" crossorigin href="/assets/index-DeF456.css">
    ${head}
  </head>
  <body>
    <div id="app"></div>
    ${body}
  </body>
</html>
`;
}

describe("the checks of the built page, which the proxy's embed script makes too", () => {
  it("take a page that loads only its own /assets/ files, with the tags the Worker fills", () => {
    expect(pageProblems(page())).toEqual([]);
  });

  it.each([
    ["an inline script", '<script type="module">import("/assets/x.js")</script>'],
    ["a script from another origin", '<script src="https://cdn.example.com/x.js"></script>'],
    ["a script from another origin without a scheme", '<script src="//cdn.example.com/x.js"></script>'],
    ["an inline style", "<style>body{color:red}</style>"],
    ["a stylesheet from another origin", '<link rel="stylesheet" href="https://cdn.example.com/x.css">'],
    ["a preload of another origin", '<link rel="preconnect" href="https://cdn.example.com">'],
    ["an icon as an image of data", '<link rel="icon" href="data:image/png;base64,AAAA">'],
  ])("refuse %s in the head", (_label, head) => {
    expect(pageProblems(page(head))).not.toEqual([]);
  });

  it.each([
    ["a style attribute", '<p style="color:red">Hi</p>'],
    ["an inline event handler", '<img src="/assets/x.png" onerror="alert(1)">'],
    ["a link with a scheme", '<a href="javascript:alert(1)">Pay</a>'],
    ["an image from another origin", "<img src='https://tracker.example/p.gif'>"],
  ])("refuse %s in the body", (_label, body) => {
    expect(pageProblems(page("", body))).not.toEqual([]);
  });

  it("refuse a page without a tag the Worker fills or the headers' meta tags", () => {
    for (const tag of ['<meta name="sv-abuse-email" content="">', '<meta name="robots" content="noindex, nofollow">', '<html lang="en">']) {
      const problems = pageProblems(page().replace(tag, ""));
      expect(problems, tag).toHaveLength(1);
      expect(problems[0]).toMatch(/^no /);
    }
  });
});

/** The page /delete-account as scripts/static-pages.mjs writes it, with [body] added to its body. */
function deletePage(body = ""): string {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="strict-origin-when-cross-origin">
<meta name="sv-google-client-id" content="">
<link rel="icon" href="data:,">
<script type="module" crossorigin src="/assets/index-AbC123.js"></script>
<link rel="stylesheet" crossorigin href="/assets/index-DeF456.css">
</head>
<body><div id="app"></div>${body}</body>
</html>
`;
}

/** A legal page as scripts/static-pages.mjs writes it, with [content] in its main. */
function legalPage(content = "<h1>Privacy policy</h1>"): string {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="no-referrer">
<link rel="icon" href="data:,">
<link rel="stylesheet" crossorigin href="/assets/index-DeF456.css">
</head>
<body><main>${content}</main></body>
</html>
`;
}

/** The home page's picture of a screenshot, light and dark, as scripts/static-pages.mjs writes it. */
const PICTURE = `<picture class="home-phone">
<source srcset="/assets/landing-result-dark-9876543210.webp" media="(prefers-color-scheme: dark)" width="600" height="1333">
<img src="/assets/landing-result-0123456789.webp" alt="A bill read" width="600" height="1333" loading="lazy" decoding="async">
</picture>`;

/** The home page as scripts/static-pages.mjs writes it, with [content] in its main. */
function homePage(content = `<h1>Scan a bill.</h1>${PICTURE}<a href="/privacy">Privacy policy</a>`): string {
  return legalPage(content);
}

describe("the checks of the other pages", () => {
  it("know each page the build writes, and the kind of checks it passes", () => {
    expect(PAGE_FILES).toEqual([
      { file: "index.html", kind: "link" },
      { file: "delete-account.html", kind: "delete-account" },
      { file: "privacy.html", kind: "legal" },
      { file: "terms.html", kind: "legal" },
      { file: "home.html", kind: "home" },
    ]);
  });

  it("take the deletion page, which tells Google its origin and has the client ID's tag, and refuse it without either", () => {
    expect(pageProblems(deletePage(), "delete-account")).toEqual([]);
    for (const tag of ['<meta name="sv-google-client-id" content="">', '<meta name="referrer" content="strict-origin-when-cross-origin">']) {
      expect(pageProblems(deletePage().replace(tag, ""), "delete-account"), tag).toHaveLength(1);
    }
    expect(pageProblems(deletePage('<script src="https://accounts.google.com/gsi/client"></script>'), "delete-account")).not.toEqual([]);
  });

  it("take a legal page with links to the web, to addresses, to sections and to the Worker's pages", () => {
    const links = '<a href="https://www.anthropic.com/legal/privacy">a</a> <a href="mailto:privacy@example.com">b</a> <a href="#retention">c</a> <a href="/terms">d</a>';
    expect(pageProblems(legalPage(`<p>${links}</p>`), "legal")).toEqual([]);
  });

  it.each([
    ["a script of its own", '<script type="module" src="/assets/index-AbC123.js"></script>'],
    ["an image of its own", '<img src="/assets/logo.png" alt="">'],
    ["a picture of its own", PICTURE],
    ["an image from another origin", '<img src="https://tracker.example/p.gif" alt="">'],
    ["a link that runs script", '<a href="javascript:alert(1)">x</a>'],
    ["a link to another origin without a scheme", '<a href="//evil.example">x</a>'],
    ["a link with a data scheme", '<a href="data:text/html,hi">x</a>'],
    ["a style attribute", '<p style="color:red">x</p>'],
    ["an inline event handler", '<p onclick="x()">x</p>'],
  ])("refuse a legal page with %s", (_label, content) => {
    expect(pageProblems(legalPage(content), "legal")).not.toEqual([]);
  });

  it("take the home page with its screenshots, light and dark, and links to the web, to sections and to the Worker's pages", () => {
    const links = '<a href="https://play.google.com/store">a</a> <a href="#more">b</a> <a href="/terms">c</a>';
    expect(pageProblems(homePage(), "home")).toEqual([]);
    expect(pageProblems(homePage(`${PICTURE}${PICTURE}<p>${links}</p>`), "home")).toEqual([]);
  });

  it.each([
    ["a script of its own", '<script type="module" src="/assets/index-AbC123.js"></script>'],
    ["an image from another origin", '<img src="https://tracker.example/p.webp" alt="">'],
    ["an image that is not WebP", '<img src="/assets/logo-AbC123.png" alt="">'],
    ["an image not under /assets/", '<img src="/landing-result.webp" alt="">'],
    ["an image in a folder of /assets/", '<img src="/assets/x/landing-result.webp" alt="">'],
    ["an image as data", '<img src="data:image/webp;base64,UklGRg==" alt="">'],
    ["two images in one srcset", '<picture><source srcset="/assets/a.webp 1x, /assets/b.webp 2x"><img src="/assets/a.webp" alt=""></picture>'],
    ["a srcset on an img", '<img src="/assets/a.webp" srcset="/assets/b.webp" alt="">'],
    ["an image in single quotes", "<img src='/assets/a.webp' alt=''>"],
    ["an image without quotes", '<img src=/assets/a.webp alt="">'],
    ["a frame", '<iframe src="/assets/a.webp"></iframe>'],
    ["a link that runs script", '<a href="javascript:alert(1)">x</a>'],
    ["a style attribute", '<p style="color:red">x</p>'],
  ])("refuse a home page with %s", (_label, content) => {
    expect(pageProblems(homePage(`<h1>Scan a bill.</h1>${content}`), "home")).not.toEqual([]);
  });

  it("refuse a home page without the tags every page carries", () => {
    for (const tag of ['<meta name="robots" content="noindex, nofollow">', '<meta name="referrer" content="no-referrer">', '<html lang="en">']) {
      expect(pageProblems(homePage().replace(tag, ""), "home"), tag).toHaveLength(1);
    }
  });

  it("refuse a kind of page they do not know", () => {
    expect(pageProblems(legalPage(), "other" as never)).toEqual(["an unknown kind of page: other"]);
  });
});

describe("the files under assets/, which the build and the proxy's embed script both check", () => {
  it("are the pages' scripts, their stylesheet and its fonts, each with the content type the Worker serves it with", () => {
    expect(assetType("index-AbC123.js")).toBe("text/javascript; charset=utf-8");
    expect(assetType("index-DeF456.css")).toBe("text/css; charset=utf-8");
    expect(assetType("bricolage-grotesque-800-Gh7_8-i.woff2")).toBe("font/woff2");
    expect(assetType("landing-result-dark-0123456789.webp")).toBe("image/webp");
  });

  it.each([
    ["an image", "logo-AbC123.png"],
    ["an older font format", "be-vietnam-pro-400.woff"],
    ["a font file of the app", "be_vietnam_pro_regular.ttf"],
    ["a source map", "index-AbC123.js.map"],
    ["a page", "index.html"],
    ["a file in a folder", "fonts/x.woff2"],
    ["a way out of the folder", "../index.js"],
    ["a name with a space", "index AbC.js"],
    ["an ending in capitals", "index-AbC123.JS"],
    ["an ending it does not serve", "font.woff2x"],
    ["a name that is only an ending", ".woff2"],
    ["a name that starts with a dot", "..woff2"],
    ["a name that starts with a dash", "-.css"],
    ["a name that starts with an underscore", "_index.js"],
  ])("refuse %s", (_label, name) => {
    expect(assetType(name)).toBeNull();
  });
});

describe("the checks of the built stylesheet", () => {
  const assets = new Set(["index-DeF456.css", "bricolage-grotesque-800-Gh78.woff2", "be-vietnam-pro-400-Ij90.woff2"]);
  const fonts =
    '@font-face{font-family:"Bricolage Grotesque";src:url(/assets/bricolage-grotesque-800-Gh78.woff2)format("woff2")}' +
    "@font-face{font-family:Be Vietnam Pro;src:url('/assets/be-vietnam-pro-400-Ij90.woff2') format(\"woff2\")}";

  it("find the files a stylesheet loads, quoted or not, each address whole", () => {
    expect(stylesheetUrls(fonts)).toEqual(["/assets/bricolage-grotesque-800-Gh78.woff2", "/assets/be-vietnam-pro-400-Ij90.woff2"]);
    expect(stylesheetUrls(`a{b:url("/x)y.png")}c{d:url('/z w.png')}`)).toEqual(["/x)y.png", "/z w.png"]);
  });

  it("take a stylesheet that loads only fonts of its own that are under assets/", () => {
    expect(stylesheetProblems(`${fonts}body{margin:0}`, assets)).toEqual([]);
  });

  it.each([
    ["a font that is not under assets/", "@font-face{src:url(/assets/missing-Kl12.woff2)}"],
    ["a font from another origin", "@font-face{src:url(https://fonts.gstatic.com/s/x.woff2)}"],
    ["a font of another kind", "@font-face{src:url(/assets/x.ttf)}"],
    ["an image", ".hero{background:url(/assets/x.png)}"],
    ["an image inline as data", ".hero{background:url(data:image/png;base64,AAAA)}"],
    ["another stylesheet", '@import "/assets/other.css";'],
    ["an image from another origin through image-set()", '.hero{background:image-set("https://evil.example/x.png" 1x)}'],
    ["an image through -webkit-image-set()", '.hero{background:-webkit-image-set("/assets/x.png" 1x)}'],
    ["a url() its name hides behind an escape", ".hero{background:u\\72l(https://evil.example/x.png)}"],
    ["a quoted url() with a parenthesis in it", '.hero{background:url("https://evil.example/a)b.png")}'],
  ])("refuse a stylesheet that loads %s", (_label, css) => {
    expect(stylesheetProblems(`${fonts}${css}`, assets)).toHaveLength(1);
  });
});
