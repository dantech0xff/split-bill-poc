// Prints the size of the page's initial download, index.html and the scripts and styles it loads,
// gzipped, against its 150 KB budget, and the size of the fonts its stylesheet loads against their
// own cap of 120 KB; exits with 1 when either is over. WOFF2 is compressed already, so a font
// counts as its size. KB here is 1000 bytes.
import { readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { gzipSync } from "node:zlib";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";
import { stylesheetUrls } from "./page-checks.mjs";

/** The most the page's HTML, script and styles may weigh gzipped; reaching it fails. */
export const BUDGET_BYTES = 150 * 1000;
/** The most the fonts may weigh together, apart from [BUDGET_BYTES]. */
export const FONT_CAP_BYTES = 120 * 1000;
/** The most font files the pages may load. */
export const MAX_FONTS = 3;

/**
 * The initial download of the page built into [directory]: index.html and each `/assets/` file it
 * refers to, with its size and its size gzipped, and each font a stylesheet among them loads, with
 * its size.
 */
export function downloadOf(directory = OUTPUT_DIRECTORY) {
  const html = readFileSync(join(directory, "index.html"));
  const referenced = [...html.toString("utf8").matchAll(/\b(?:src|href)="\/(assets\/[^"]+)"/g)].map((match) => match[1]);
  const files = [{ name: "index.html", bytes: html }, ...referenced.map((path) => ({ name: path, bytes: readFileSync(join(directory, path)) }))];
  const fonts = new Set(
    files
      .filter((file) => file.name.endsWith(".css"))
      .flatMap((file) => stylesheetUrls(file.bytes.toString("utf8")))
      .filter((url) => url.startsWith("/assets/"))
      .map((url) => url.slice(1)),
  );
  return {
    page: files.map(({ name, bytes }) => ({ name, raw: bytes.length, gzip: gzipSync(bytes, { level: 9 }).length })),
    fonts: [...fonts].map((name) => ({ name, bytes: statSync(join(directory, name)).size })),
  };
}

const kb = (bytes) => `${(bytes / 1000).toFixed(1)} KB`;
const sum = (values) => values.reduce((total, value) => total + value, 0);

/** What in [download], as [downloadOf] gives it, is over its limit, one sentence for each; empty when nothing is. */
export function sizeProblems({ page, fonts }) {
  const problems = [];
  const gzip = sum(page.map((file) => file.gzip));
  if (gzip >= BUDGET_BYTES) problems.push(`The initial download is ${kb(gzip)} gzipped, which reaches its budget of ${kb(BUDGET_BYTES)}.`);
  if (fonts.length > MAX_FONTS) problems.push(`The stylesheet loads ${fonts.length} fonts, and the pages may load at most ${MAX_FONTS}.`);
  const fontBytes = sum(fonts.map((font) => font.bytes));
  if (fontBytes > FONT_CAP_BYTES) problems.push(`The fonts are ${kb(fontBytes)}, over their cap of ${kb(FONT_CAP_BYTES)}.`);
  return problems;
}

/** The lines `npm run size` prints for [download]. */
export function sizeReport({ page, fonts }) {
  const lines = page.map((file) => `${file.name.padEnd(44)} ${kb(file.raw).padStart(9)} ${kb(file.gzip).padStart(9)} gzipped`);
  lines.push(
    `${"initial download".padEnd(44)} ${kb(sum(page.map((file) => file.raw))).padStart(9)} ${kb(sum(page.map((file) => file.gzip))).padStart(9)} gzipped, budget ${kb(BUDGET_BYTES)}`,
  );
  lines.push(...fonts.map((font) => `${font.name.padEnd(44)} ${kb(font.bytes).padStart(9)}`));
  lines.push(`${`fonts (${fonts.length} of at most ${MAX_FONTS})`.padEnd(44)} ${kb(sum(fonts.map((font) => font.bytes))).padStart(9)}, cap ${kb(FONT_CAP_BYTES)}`);
  return lines;
}

// Run as a script; imported, it only lends its functions.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const download = downloadOf();
  for (const line of sizeReport(download)) console.log(line);
  const problems = sizeProblems(download);
  if (problems.length > 0) {
    for (const problem of problems) console.error(problem);
    process.exit(1);
  }
}
