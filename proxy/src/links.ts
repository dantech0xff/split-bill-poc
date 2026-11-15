import type { Env } from "./index";
import { errorResponse } from "./respond";

/** A link's token: 128 bits in base64url, without padding. */
const TOKEN = /^[A-Za-z0-9_-]{22}$/;
/** A fingerprint as Digital Asset Links writes it: 32 bytes in upper-case hex, separated by colons. */
const FINGERPRINT = /^[0-9A-F]{2}(?::[0-9A-F]{2}){31}$/;
const PACKAGES = ["com.dantech.splitvibe", "com.dantech.splitvibe.dev"];

/** The token of an invite: 128 random bits. Only its hash is kept, so it is shown once, to whoever made it. */
export function randomToken(): string {
  return base64url(crypto.getRandomValues(new Uint8Array(16)));
}

/**
 * The token of the link of request [requestId]: the first 128 bits of an HMAC-SHA-256 of the id
 * under `HASH_SALT`. Only its hash is kept, like an invite's, yet the proxy can hand the same link
 * to its creditor again when the app sends the same request twice. Without the salt, which never
 * leaves the Worker, no one can work out a link from an id.
 */
export async function requestToken(env: Env, requestId: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.HASH_SALT), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`request-link\u0000${requestId}`));
  return base64url(new Uint8Array(mac).slice(0, 16));
}

/** The SHA-256 of a link's token, in hex: what the database keeps in its place. */
export async function tokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** [segment] when it has the shape of a token, else null. */
export function tokenOf(segment: string | undefined): string | null {
  return segment !== undefined && TOKEN.test(segment) ? segment : null;
}

/** The link to share: `https://<the host [request] came to>/g/<token>` for an invite, `/r/<token>` for a payment request. */
export function linkUrl(request: Request, kind: "g" | "r", token: string): string {
  return `https://${new URL(request.url).host}/${kind}/${token}`;
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

/**
 * `GET /.well-known/assetlinks.json`: lets Android open the proxy's `/g/` and `/r/` links in the
 * app. Both packages, the release and the debug build, are listed with every fingerprint in
 * `ANDROID_CERT_SHA256`; one that is not a SHA-256 fingerprint is left out and logged. Without
 * any, the file is an empty list, which links nothing.
 */
export async function assetLinksRoute(_request: Request, env: Env): Promise<Response> {
  const listed = (env.ANDROID_CERT_SHA256 ?? "")
    .split(",")
    .map((fingerprint) => fingerprint.trim().toUpperCase())
    .filter((fingerprint) => fingerprint !== "");
  const fingerprints = listed.filter((fingerprint) => FINGERPRINT.test(fingerprint));
  if (fingerprints.length < listed.length) {
    console.error(JSON.stringify({ event: "bad_cert_fingerprint", count: listed.length - fingerprints.length }));
  }
  const statements =
    fingerprints.length === 0
      ? []
      : PACKAGES.map((packageName) => ({
          relation: ["delegate_permission/common.handle_all_urls"],
          target: { namespace: "android_app", package_name: packageName, sha256_cert_fingerprints: fingerprints },
        }));
  return new Response(JSON.stringify(statements), {
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}

/** What a browser shows for a link: the same words for every link, so the page tells nothing of the group or the request. */
const LINK_PAGE = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Split Vibe</title>
<style>body{font-family:system-ui,sans-serif;line-height:1.5;max-width:32rem;margin:4rem auto;padding:0 1.5rem;color:#1d1b20}</style>
</head>
<body>
<h1>Open this link in Split Vibe</h1>
<p>This link works in the Split Vibe app. Open it on an Android phone that has Split Vibe installed.</p>
</body>
</html>
`;

/**
 * `GET /g/{token}` and `GET /r/{token}`: the page a link opens where the app does not. It never
 * looks the token up, so it says nothing about whether a link is real, and it neither caches nor
 * passes the address on.
 */
export async function linkPageRoute(_request: Request, _env: Env, _ctx: ExecutionContext, params: string[]): Promise<Response> {
  if (tokenOf(params[0]) === null) return errorResponse(404, "path");
  return new Response(LINK_PAGE, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'",
      "referrer-policy": "no-referrer",
      "x-robots-tag": "noindex",
    },
  });
}
