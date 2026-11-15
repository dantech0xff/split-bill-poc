import type { Env } from "./index";

/** A link's token: 128 bits in base64url, without padding. */
const TOKEN = /^[A-Za-z0-9_-]{22}$/;
/** A fingerprint as Digital Asset Links writes it: 32 bytes in upper-case hex, separated by colons. */
const FINGERPRINT = /^[0-9A-F]{2}(?::[0-9A-F]{2}){31}$/;
const PACKAGES = ["com.dantech.splitvibe", "com.dantech.splitvibe.dev"];

/**
 * A random token of 128 bits: an invite's, or the key of a claim on a bill link. Only its hash is
 * kept, so it is shown once, to whoever it was made for.
 */
export function randomToken(): string {
  return base64url(crypto.getRandomValues(new Uint8Array(16)));
}

/**
 * The token of the link of request [requestId] made by [creditor]: the first 128 bits of an
 * HMAC-SHA-256 of both under `HASH_SALT`. Only its hash is kept, like an invite's, yet the proxy
 * can hand the same link to its creditor again when the app sends the same request twice. Without
 * the salt, which never leaves the Worker, no one can work out a link from an id. Naming the
 * creditor keeps a link to its own request: an id forgotten after a deletion and used again by
 * another account gives that request another link, and the old one stays dead.
 */
export function requestToken(env: Env, creditor: string, requestId: string): Promise<string> {
  return macToken(env, `request-link:${creditor}:${requestId}`);
}

/**
 * The token of bill link [shareId], made by [owner] at [createdAt]: the first 128 bits of an
 * HMAC-SHA-256 of the three under `HASH_SALT`. Only its hash is kept, yet the owner gets the same
 * link every time they ask. The time it was made is part of it, so a link made again under the
 * same id, once the daily run deleted the first, gets another token, and the old one stays dead.
 */
export function shareToken(env: Env, owner: string, shareId: string, createdAt: number): Promise<string> {
  return macToken(env, `share-link:${owner}:${shareId}:${createdAt}`);
}

/** The first 128 bits of an HMAC-SHA-256 of [text] under `HASH_SALT`, in base64url: a token no one can work out without the salt. */
async function macToken(env: Env, text: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.HASH_SALT), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text));
  return base64url(new Uint8Array(mac).slice(0, 16));
}

/** The SHA-256 of a link's token, in hex: what the database keeps in its place. */
export async function tokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** [value] when it has the shape of a link's token, else null. */
export function tokenOf(value: unknown): string | null {
  return typeof value === "string" && TOKEN.test(value) ? value : null;
}

/**
 * The `token` of a body: null when the body has no text there, which is bad input; otherwise the
 * token, or a null token for text that no link could hold, which leads nowhere.
 */
export function bodyTokenOf(body: Record<string, unknown>): { token: string | null } | null {
  return typeof body.token === "string" ? { token: tokenOf(body.token) } : null;
}

/**
 * The link to share: `https://<the host [request] came to>/g/#<token>` for an invite, `/r/#<token>`
 * for a payment request, `/s/#<token>` for a bill link. The token is in the fragment, which a
 * browser never sends: no request to the proxy, nor its traces, ever holds it. The app, or the
 * web page, reads it from the link and sends it in a body.
 */
export function linkUrl(request: Request, kind: "g" | "r" | "s", token: string): string {
  return `https://${new URL(request.url).host}/${kind}/#${token}`;
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
