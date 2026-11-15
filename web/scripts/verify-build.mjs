// Checks that the built page is what the Worker can serve under its CSP: one index.html that
// loads only /assets/ files, with no inline script, inline style or style attribute, and with the
// meta tags the Worker fills. Exits with 1 and says what is wrong otherwise.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { OUTPUT_DIRECTORY } from "./build-output.mjs";

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

for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
  const [, attributes, body] = match;
  if (!/\bsrc="\/assets\/[^"]+\.js"/.test(attributes)) problems.push(`a script that does not load an /assets/ file: ${match[0].slice(0, 80)}`);
  if (body.trim() !== "") problems.push("an inline script");
}
if (/<style\b/i.test(html)) problems.push("an inline <style> element");
if (/\sstyle\s*=/i.test(html)) problems.push("a style attribute");
if (/\son[a-z]+\s*=/i.test(html)) problems.push("an inline event handler");
for (const match of html.matchAll(/<link\b([^>]*)>/gi)) {
  const href = /\bhref="([^"]*)"/.exec(match[1])?.[1] ?? "";
  if (/\brel="icon"/.test(match[1]) && href === "data:,") continue;
  if (!/^\/assets\/[^"]+\.css$/.test(href)) problems.push(`a link to something other than an /assets/ stylesheet: ${href}`);
}
for (const match of html.matchAll(/\b(?:src|href)="([^"]*)"/gi)) {
  if (/^(?:[a-z][a-z0-9+.-]*:)?\/\//i.test(match[1])) problems.push(`a reference to another origin: ${match[1]}`);
}

const required = [
  /<html lang="en">/,
  /<meta name="robots" content="noindex, nofollow">/,
  /<meta name="referrer" content="no-referrer">/,
  /<meta name="sv-turnstile-site-key" content="">/,
  /<meta name="sv-abuse-email" content="">/,
];
for (const pattern of required) {
  if (!pattern.test(html)) problems.push(`index.html lacks ${pattern.source}`);
}

if (problems.length > 0) {
  console.error(`The build does not fit the page's CSP:\n- ${problems.join("\n- ")}`);
  process.exit(1);
}
console.log(`The build fits the page's CSP: index.html and ${files.length - 1} files under /assets/.`);
if (process.argv.includes("--show")) console.log(html);
