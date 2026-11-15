// Checks that the built page is what the Worker can serve under its CSP: one index.html that
// loads only /assets/ files, with no inline script, inline style, style attribute or inline event
// handler, no reference to another origin, and with the meta tags the Worker fills. The page's
// checks are those of scripts/page-checks.mjs, which the proxy's embed script makes too. Exits
// with 1 and says what is wrong otherwise.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";
import { pageProblems } from "./page-checks.mjs";

const problems = [];

function filesUnder(directory, prefix = "") {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? filesUnder(path, `${prefix}${name}/`) : [`${prefix}${name}`];
  });
}

const files = filesUnder(OUTPUT_DIRECTORY);
for (const file of files) {
  if (file !== "index.html" && !/^assets\/[A-Za-z0-9_.-]+\.(js|css)$/.test(file)) problems.push(`unexpected file in the build: ${file}`);
}

const html = readFileSync(join(OUTPUT_DIRECTORY, "index.html"), "utf8");
problems.push(...pageProblems(html));

if (problems.length > 0) {
  console.error(`The build does not fit the page's CSP:\n- ${problems.join("\n- ")}`);
  process.exit(1);
}
console.log(`The build fits the page's CSP: index.html and ${files.length - 1} files under /assets/.`);
if (process.argv.includes("--show")) console.log(html);
