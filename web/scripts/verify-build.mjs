// Checks that the built pages are what the Worker can serve under their CSPs: index.html, the
// page of the links, and the pages scripts/static-pages.mjs writes beside it, each loading only
// /assets/ files, with no inline script, inline style, style attribute or inline event handler, no
// reference to another origin but a legal page's links, and with the meta tags the Worker fills.
// The pages' checks are those of scripts/page-checks.mjs, which the proxy's embed script makes
// too. Exits with 1 and says what is wrong otherwise.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";
import { PAGE_FILES, pageProblems } from "./page-checks.mjs";

const problems = [];

function filesUnder(directory, prefix = "") {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? filesUnder(path, `${prefix}${name}/`) : [`${prefix}${name}`];
  });
}

const pages = new Set(PAGE_FILES.map((page) => page.file));
const files = filesUnder(OUTPUT_DIRECTORY);
for (const file of files) {
  if (!pages.has(file) && !/^assets\/[A-Za-z0-9_.-]+\.(js|css)$/.test(file)) problems.push(`unexpected file in the build: ${file}`);
}

for (const { file, kind } of PAGE_FILES) {
  const path = join(OUTPUT_DIRECTORY, file);
  if (!existsSync(path)) {
    problems.push(`no ${file}`);
    continue;
  }
  problems.push(...pageProblems(readFileSync(path, "utf8"), kind).map((problem) => `${file}: ${problem}`));
}

if (problems.length > 0) {
  console.error(`The build does not fit the pages' CSPs:\n- ${problems.join("\n- ")}`);
  process.exit(1);
}
console.log(`The build fits the pages' CSPs: ${PAGE_FILES.length} pages and ${files.length - PAGE_FILES.length} files under /assets/.`);
if (process.argv.includes("--show")) console.log(readFileSync(join(OUTPUT_DIRECTORY, "index.html"), "utf8"));
