// What the built page must be for the Worker to serve it under its Content-Security-Policy. Both
// `npm run build` (scripts/verify-build.mjs) and the proxy's scripts/embed-web.mjs, which puts the
// page into the Worker, check it with these, so that neither lets through what the other refuses.

/** The meta tags and attributes the page must carry, the two the Worker fills among them. */
const REQUIRED = [
  /<html lang="en">/,
  /<meta name="robots" content="noindex, nofollow">/,
  /<meta name="referrer" content="no-referrer">/,
  /<meta name="sv-turnstile-site-key" content="">/,
  /<meta name="sv-abuse-email" content="">/,
];

/**
 * What is wrong with [html] as the page the Worker serves; empty when nothing is. Scripts and
 * styles must be files under `/assets/`; no inline script or style, `style` attribute or inline
 * event handler, which the policy blocks; no reference with a scheme or to another origin, but the
 * empty `data:,` icon, which loads nothing; and the tags of [REQUIRED].
 */
export function pageProblems(html) {
  const problems = [];
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
  for (const match of html.matchAll(/\b(?:src|href)\s*=\s*(["']?)([^"'\s>]*)\1/gi)) {
    const reference = match[2];
    if (reference === "data:,") continue;
    if (/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(reference)) problems.push(`a reference with a scheme or to another origin: ${reference}`);
  }
  for (const pattern of REQUIRED) {
    if (!pattern.test(html)) problems.push(`no ${pattern.source}`);
  }
  return problems;
}
