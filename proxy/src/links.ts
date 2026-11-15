/** A link's token: 128 bits in base64url, without padding. */
const TOKEN = /^[A-Za-z0-9_-]{22}$/;

/** The token of an invite: 128 random bits. Only its hash is kept, so it is shown once, to whoever made it. */
export function randomToken(): string {
  return base64url(crypto.getRandomValues(new Uint8Array(16)));
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
