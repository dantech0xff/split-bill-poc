// Prints the gzipped size of the page's initial download, index.html and the files it loads,
// and exits with 1 when it reaches the 150 KB budget.
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { gzipSync } from "node:zlib";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";

const BUDGET_BYTES = 150 * 1000;
const html = readFileSync(join(OUTPUT_DIRECTORY, "index.html"));
const referenced = [...html.toString("utf8").matchAll(/\b(?:src|href)="\/(assets\/[^"]+)"/g)].map((match) => match[1]);

const rows = [
  { name: "index.html", bytes: html },
  ...referenced.map((path) => ({ name: path, bytes: readFileSync(join(OUTPUT_DIRECTORY, path)) })),
].map(({ name, bytes }) => ({ name, raw: bytes.length, gzip: gzipSync(bytes, { level: 9 }).length }));
const total = rows.reduce((sum, row) => ({ raw: sum.raw + row.raw, gzip: sum.gzip + row.gzip }), { raw: 0, gzip: 0 });

const kb = (bytes) => `${(bytes / 1000).toFixed(1)} KB`;
for (const row of rows) console.log(`${row.name.padEnd(36)} ${kb(row.raw).padStart(9)} ${kb(row.gzip).padStart(9)} gzipped`);
console.log(`${"initial download".padEnd(36)} ${kb(total.raw).padStart(9)} ${kb(total.gzip).padStart(9)} gzipped, budget ${kb(BUDGET_BYTES)}`);
if (total.gzip >= BUDGET_BYTES) {
  console.error("The initial download is over its budget.");
  process.exit(1);
}
