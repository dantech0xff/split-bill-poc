// The text of a file of web/ as it is on disk. Vitest hands a test an empty string for a stylesheet
// it imports, even with `?raw`, so the tests that read the stylesheets read them through this.
import { readFileSync } from "node:fs";

/** The text of the file at [path], relative to web/, such as "src/app.css". */
export function sourceText(path) {
  return readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
}
