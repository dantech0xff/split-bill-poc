import type { Env } from "./index";
import { errorResponse } from "./respond";
import { signInReady } from "./session";
import { DELETE_ACCOUNT_PAGE, LEGAL_PAGES, WEB_ASSETS, WEB_PAGE } from "./web-assets.generated";

/** A Turnstile site key: letters, digits, `_` and `-`. */
const SITE_KEY = /^[A-Za-z0-9_-]{1,128}$/;
/** A client ID of Google's OAuth: letters, digits, `.`, `_` and `-`, ending in `.apps.googleusercontent.com`. */
const CLIENT_ID = /^[A-Za-z0-9._-]{1,200}\.apps\.googleusercontent\.com$/;
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

function isClientId(value: string): boolean {
  return CLIENT_ID.test(value);
}

/**
 * The value of the variable [name] to put in a meta tag of the page: trimmed, and empty when it
 * is not what [valid] takes, which is logged.
 */
function setting(env: Env, name: "TURNSTILE_SITE_KEY" | "ABUSE_EMAIL" | "GOOGLE_CLIENT_ID", valid: (value: string) => boolean): string {
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
 * The Content-Security-Policy of the link pages: their own scripts and styles and Cloudflare's
 * Turnstile only, images of their own or inline as data, and connections to the proxy itself, the
 * live connection's `wss://` named outright for browsers that do not count it as `'self'`.
 */
function linkPagePolicy(host: string): string {
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

/** The policy of the privacy policy and the terms, which run no script: their own styles, and images of their own or inline as data. */
const LEGAL_PAGE_POLICY = ["default-src 'none'", "style-src 'self'", "img-src 'self' data:", "base-uri 'none'", "form-action 'none'", "frame-ancestors 'none'"].join(
  "; ",
);

/**
 * The policy of `/delete-account`, the one page that loads Google Identity Services: the page's own
 * scripts and styles, and exactly the origins Google's guide asks for its library, its stylesheet,
 * the iframe of its button and its endpoints, all under `https://accounts.google.com/gsi/`.
 */
const DELETE_ACCOUNT_PAGE_POLICY = [
  "default-src 'none'",
  "script-src 'self' https://accounts.google.com/gsi/client",
  "style-src 'self' https://accounts.google.com/gsi/style",
  "img-src 'self' data:",
  "connect-src 'self' https://accounts.google.com/gsi/",
  "frame-src https://accounts.google.com/gsi/",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
].join("; ");

/**
 * [page] as the Worker serves its pages: never cached, kept out of search engines, never framed,
 * under [policy]. [referrer] is `no-referrer` but on `/delete-account`, which must tell Google its
 * origin (see [deleteAccountPageRoute]).
 */
function pageResponse(page: string, policy: string, referrer: "no-referrer" | "strict-origin-when-cross-origin" = "no-referrer"): Response {
  return new Response(page, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "content-security-policy": policy,
      "referrer-policy": referrer,
      "x-robots-tag": "noindex, nofollow",
      "x-content-type-options": "nosniff",
      "cache-control": "no-store",
    },
  });
}

/**
 * `GET /s/`, `GET /r/` and `GET /g/`: the web app's page, the same for every link. A link's token
 * is in its fragment, which never reaches the proxy; the page's script reads it and calls the
 * routes the page needs. The Worker fills the page's meta tags `sv-turnstile-site-key` and
 * `sv-abuse-email` from the variables `TURNSTILE_SITE_KEY` and `ABUSE_EMAIL`. The page passes no
 * address on to where its links lead.
 */
export async function webPageRoute(request: Request, env: Env): Promise<Response> {
  let page = filled(WEB_PAGE, "sv-turnstile-site-key", setting(env, "TURNSTILE_SITE_KEY", isSiteKey));
  page = filled(page, "sv-abuse-email", setting(env, "ABUSE_EMAIL", isAbuseEmail));
  return pageResponse(page, linkPagePolicy(new URL(request.url).host));
}

/** `GET /privacy`: the privacy policy, rendered from `docs/privacy-policy.md` when the web app was built. */
export async function privacyPageRoute(): Promise<Response> {
  return pageResponse(LEGAL_PAGES.privacy, LEGAL_PAGE_POLICY);
}

/** `GET /terms`: the terms of use, rendered from `docs/terms.md` when the web app was built. */
export async function termsPageRoute(): Promise<Response> {
  return pageResponse(LEGAL_PAGES.terms, LEGAL_PAGE_POLICY);
}

/**
 * `GET /delete-account`: the page where someone deletes their account by signing in with Google,
 * drawn by the web app's script. The Worker fills its meta tag `sv-google-client-id` with
 * `GOOGLE_CLIENT_ID` while sign-in works, as `POST /v1/account/delete-with-google` needs it; left
 * empty, the page says deleting on the web is not available yet and how to delete in the app. Its
 * referrer policy is `strict-origin-when-cross-origin`, as Google's guide recommends: Google tells
 * the page's origin from the referrer, and refuses the button without it. Cross-origin requests
 * get the origin alone, and the page's address holds nothing else.
 */
export async function deleteAccountPageRoute(_request: Request, env: Env): Promise<Response> {
  const clientId = signInReady(env) ? setting(env, "GOOGLE_CLIENT_ID", isClientId) : "";
  return pageResponse(filled(DELETE_ACCOUNT_PAGE, "sv-google-client-id", clientId), DELETE_ACCOUNT_PAGE_POLICY, "strict-origin-when-cross-origin");
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
