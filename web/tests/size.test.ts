// @vitest-environment node
// The check reads a build's files, so these run in Node rather than in a page.
import { describe, expect, it } from "vitest";
import { BUDGET_BYTES, FONT_CAP_BYTES, MAX_FONTS, type Download, downloadOf, sizeProblems } from "../scripts/size.mjs";

/** A build as `vite build` writes one, with a script, a stylesheet and the two fonts it loads. */
const FIXTURE = decodeURIComponent(new URL("./fixtures/size/", import.meta.url).pathname);

/** A download whose HTML, script and styles weigh [pageGzip] bytes gzipped, with fonts of [fonts] bytes each. */
function download(fonts: number[], pageGzip = 40_000): Download {
  return {
    page: [
      { name: "index.html", raw: 1_000, gzip: 500 },
      { name: "assets/index-AbC123.js", raw: pageGzip * 3, gzip: pageGzip - 500 },
    ],
    fonts: fonts.map((bytes, index) => ({ name: `assets/font-${index}.woff2`, bytes })),
  };
}

describe("the size check of npm run size", () => {
  it("counts the fonts the stylesheet loads apart from the page's HTML, script and styles", () => {
    const measured = downloadOf(FIXTURE);

    expect(measured.page.map((file) => file.name)).toEqual(["index.html", "assets/index-AbC123.js", "assets/index-DeF456.css"]);
    expect(measured.fonts).toEqual([
      { name: "assets/display-Gh78.woff2", bytes: 9 },
      { name: "assets/text-Ij90.woff2", bytes: 7 },
    ]);
  });

  it("takes three fonts of 120 KB together, beside a page under its budget", () => {
    expect(FONT_CAP_BYTES).toBe(120_000);
    expect(MAX_FONTS).toBe(3);
    expect(sizeProblems(download([40_000, 40_000, 40_000]))).toEqual([]);
  });

  it("refuses fonts of more than 120 KB together, however light the page is", () => {
    expect(sizeProblems(download([40_000, 40_000, 41_000], 10_000))).toEqual(["The fonts are 121.0 KB, over their cap of 120.0 KB."]);
  });

  it("refuses a fourth font, however small", () => {
    expect(sizeProblems(download([20_000, 20_000, 20_000, 1_000]))).toEqual(["The stylesheet loads 4 fonts, and the pages may load at most 3."]);
  });

  it("refuses a page that reaches its budget of 150 KB, and keeps the fonts out of it", () => {
    expect(BUDGET_BYTES).toBe(150_000);
    expect(sizeProblems(download([], BUDGET_BYTES))).toEqual(["The initial download is 150.0 KB gzipped, which reaches its budget of 150.0 KB."]);
    expect(sizeProblems(download([100_000], BUDGET_BYTES - 1))).toEqual([]);
  });
});
