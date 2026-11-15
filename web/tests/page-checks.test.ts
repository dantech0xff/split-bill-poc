import { describe, expect, it } from "vitest";
import { pageProblems } from "../scripts/page-checks.mjs";

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
