// Checks that the built pages are what the Worker can serve under their CSPs: index.html, the
// page of the links, and the pages scripts/static-pages.mjs writes beside it, each loading only
// /assets/ files that the build holds, with no inline script, inline style, style attribute or
// inline event handler, no reference to another origin but the links of a legal page or the home
// page, and with the meta tags the Worker fills; and that assets/ holds only scripts, stylesheets,
// fonts and the home page's WebP screenshots, every screenshot shown by a page, a stylesheet's
// `url()`s leading only to those fonts, with no `@import`, `image-set()` or backslash. The checks of
// each page are those of scripts/page-checks.mjs, which the proxy's embed script makes too. Exits
// with 1 and says what is wrong otherwise.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";
import { PAGE_FILES, assetType, pageProblems, stylesheetProblems } from "./page-checks.mjs";

function filesUnder(directory, prefix = "") {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? filesUnder(path, `${prefix}${name}/`) : [`${prefix}${name}`];
  });
}

/**
 * What is wrong with the build in [directory], web/dist unless given, one sentence for each, and
 * the names of the files under its assets/; the problems are empty when nothing is wrong.
 */
export function buildProblems(directory = OUTPUT_DIRECTORY) {
  const problems = [];
  const pages = new Set(PAGE_FILES.map((page) => page.file));
  const assets = new Set();
  for (const file of filesUnder(directory)) {
    if (pages.has(file)) continue;
    const name = /^assets\/([^/]+)$/.exec(file)?.[1];
    if (name === undefined || assetType(name) === null) problems.push(`unexpected file in the build: ${file}`);
    else assets.add(name);
  }
  for (const name of assets) {
    if (!name.endsWith(".css")) continue;
    const css = readFileSync(join(directory, "assets", name), "utf8");
    problems.push(...stylesheetProblems(css, assets).map((problem) => `assets/${name}: ${problem}`));
  }
  const shown = new Set();
  for (const { file, kind } of PAGE_FILES) {
    const path = join(directory, file);
    if (!existsSync(path)) {
      problems.push(`no ${file}`);
      continue;
    }
    const html = readFileSync(path, "utf8");
    problems.push(...pageProblems(html, kind).map((problem) => `${file}: ${problem}`));
    for (const [, name] of html.matchAll(/\b(?:src|srcset|href)="\/assets\/([^"?#]+)"/g)) {
      shown.add(name);
      if (!assets.has(name)) problems.push(`${file}: refers to /assets/${name}, which the build does not hold`);
    }
  }
  for (const name of assets) {
    if (name.endsWith(".webp") && !shown.has(name)) problems.push(`assets/${name}: an image no page shows`);
  }
  return { problems, assets };
}

// Run as a script; imported, it only lends its check.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { problems, assets } = buildProblems();
  if (problems.length > 0) {
    console.error(`The build does not fit the pages' CSPs:\n- ${problems.join("\n- ")}`);
    process.exit(1);
  }
  console.log(`The build fits the pages' CSPs: ${PAGE_FILES.length} pages and ${assets.size} files under /assets/.`);
  if (process.argv.includes("--show")) console.log(readFileSync(join(OUTPUT_DIRECTORY, "index.html"), "utf8"));
}
