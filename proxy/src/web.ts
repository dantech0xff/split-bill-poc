import type { Env } from "./index";
import { errorResponse } from "./respond";
import { WEB_ASSETS, WEB_PAGE } from "./web-assets.generated";

/** A Turnstile site key: letters, digits, `_` and `-`. */
const SITE_KEY = /^[A-Za-z0-9_-]{1,128}$/;
/**
 * An address for the "Report abuse" link, by the rule the page applies too (`abuseMailto` in
 * web/src/links.ts, and docs/proxy.md): at most 254 characters; letters, digits and `._%+-`, an
 * `@`, then two or more labels of letters, digits and `-` joined by dots. Nothing in such an
 * address can change what a `mailto:` link does.
 */
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$/;
const MAX_EMAIL_CHARS = 254;

function isSiteKey(value: string): boolean {
  return SITE_KEY.test(value);
}

function isAbuseEmail(value: string): boolean {
  return value.length <= MAX_EMAIL_CHARS && EMAIL.test(value);
}

/**
 * The value of the variable [name] to put in a meta tag of the page: trimmed, and empty when it
 * is not what [valid] takes, which is logged.
 */
function setting(env: Env, name: "TURNSTILE_SITE_KEY" | "ABUSE_EMAIL", valid: (value: string) => boolean): string {
  const value = (env[name] ?? "").trim();
  if (value === "" || valid(value)) return value;
  console.error(JSON.stringify({ event: "bad_setting", name }));
  return "";
}

/** [text] as it may stand in an HTML attribute between double quotes. */
function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/'/g, "&#39;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** The page with the meta tag [name], which `scripts/embed-web.mjs` leaves empty, filled with [value]. */
function filled(page: string, name: string, value: string): string {
  return page.replace(`<meta name="${name}" content="">`, () => `<meta name="${name}" content="${escapeHtml(value)}">`);
}

/**
 * The page's Content-Security-Policy: its own scripts and styles and Cloudflare's Turnstile only,
 * images of its own or inline as data, and connections to the proxy itself, the live connection's
 * `wss://` named outright for browsers that do not count it as `'self'`.
 */
function contentSecurityPolicy(host: string): string {
  return [
    "default-src 'none'",
    "script-src 'self' https://challenges.cloudflare.com",
    "style-src 'self'",
    "img-src 'self' data:",
    `connect-src 'self' wss://${host}`,
    "frame-src https://challenges.cloudflare.com",
    "base-uri 'none'",
    "form-action 'none'",
    "frame-ancestors 'none'",
  ].join("; ");
}

/**
 * `GET /s/`, `GET /r/` and `GET /g/`: the web app's page, the same for every link. A link's token
 * is in its fragment, which never reaches the proxy; the page's script reads it and calls the
 * routes the page needs. The Worker fills the page's meta tags `sv-turnstile-site-key` and
 * `sv-abuse-email` from the variables `TURNSTILE_SITE_KEY` and `ABUSE_EMAIL`. The page is never
 * cached, kept out of search engines, never framed, and passes no address on to where its links
 * lead.
 */
export async function webPageRoute(request: Request, env: Env): Promise<Response> {
  let page = filled(WEB_PAGE, "sv-turnstile-site-key", setting(env, "TURNSTILE_SITE_KEY", isSiteKey));
  page = filled(page, "sv-abuse-email", setting(env, "ABUSE_EMAIL", isAbuseEmail));
  return new Response(page, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "content-security-policy": contentSecurityPolicy(new URL(request.url).host),
      "referrer-policy": "no-referrer",
      "x-robots-tag": "noindex, nofollow",
      "x-content-type-options": "nosniff",
      "cache-control": "no-store",
    },
  });
}

/**
 * `GET /assets/<name>`: one of the page's scripts or styles. Their names carry a hash of what they
 * hold, so they may be cached for a year; any other name is 404.
 */
export async function webAssetRoute(_request: Request, _env: Env, _ctx: ExecutionContext, params: string[]): Promise<Response> {
  const asset = WEB_ASSETS.get(params[0]);
  if (asset === undefined) return errorResponse(404, "not_found");
  return new Response(asset.body, {
    headers: { "content-type": asset.type, "cache-control": "public, max-age=31536000, immutable", "x-content-type-options": "nosniff" },
  });
}
