import { createRemoteJWKSet, errors, jwtVerify, SignJWT } from "jose";
import type { Env } from "./index";
import { hashed } from "./ledger";
import { POLICY } from "./policy";

/** Google's public keys for its ID tokens. The set is cached in the isolate and fetched again when Google rotates a key. */
const GOOGLE_KEYS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
const GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"];

const ISSUER = "split-vibe-proxy";
const AUDIENCE = "split-vibe-app";
const ACCOUNT = /^[0-9a-f]{64}$/;
const MIN_KEY_BYTES = 32;

/** Sign-in works once the owner has set the app's web client ID and a signing key long enough for HS256. */
export function signInReady(env: Env): boolean {
  return env.GOOGLE_CLIENT_ID.trim() !== "" && signingKey(env).byteLength >= MIN_KEY_BYTES;
}

/**
 * The account a Google ID token stands for, named by a salted hash of its `sub` so that the `sub`
 * itself is never kept. The token must be signed by Google, issued by Google, meant for this app's
 * web client ID, and unexpired; otherwise it is "invalid". When Google's keys cannot be had just
 * now, the token cannot be judged and the answer is "unavailable".
 */
export async function googleAccountOf(
  idToken: string,
  env: Env,
): Promise<{ ok: true; account: string } | { ok: false; reason: "invalid" | "unavailable" }> {
  try {
    const { payload } = await jwtVerify(idToken, GOOGLE_KEYS, {
      algorithms: ["RS256"],
      issuer: GOOGLE_ISSUERS,
      audience: env.GOOGLE_CLIENT_ID,
      requiredClaims: ["sub", "exp"],
    });
    if (typeof payload.sub !== "string" || payload.sub === "") return { ok: false, reason: "invalid" };
    return { ok: true, account: await hashed(payload.sub, env.HASH_SALT) };
  } catch (error) {
    const reason = verdictOf(error);
    if (reason === "unavailable") console.error(JSON.stringify({ event: "google_keys_unavailable", error: error instanceof Error ? error.name : "unknown" }));
    return { ok: false, reason };
  }
}

/** Whether a failure to check an ID token says the token is bad, or only that Google's keys could not be fetched. */
export function verdictOf(error: unknown): "invalid" | "unavailable" {
  const tokenFaults = [
    errors.JWTExpired,
    errors.JWTClaimValidationFailed,
    errors.JWTInvalid,
    errors.JWSInvalid,
    errors.JWSSignatureVerificationFailed,
    errors.JWKSNoMatchingKey,
    errors.JWKSMultipleMatchingKeys,
    errors.JOSEAlgNotAllowed,
    errors.JOSENotSupported,
  ];
  return tokenFaults.some((fault) => error instanceof fault) ? "invalid" : "unavailable";
}

/** A session for [account], signed by the proxy, that lasts [POLICY.sessionMs]. */
export async function issueSession(account: string, env: Env, now: number): Promise<{ session: string; expiresAt: number }> {
  const expiresAt = now + POLICY.sessionMs;
  const session = await new SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(account)
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setIssuedAt(Math.floor(now / 1000))
    .setExpirationTime(Math.floor(expiresAt / 1000))
    .sign(signingKey(env));
  return { session, expiresAt };
}

/** The account a session stands for, or null when this proxy did not sign it, it was changed, or it has run out. */
export async function accountOfSession(session: string, env: Env): Promise<string | null> {
  if (!signInReady(env)) return null;
  try {
    const { payload } = await jwtVerify(session, signingKey(env), {
      algorithms: ["HS256"],
      issuer: ISSUER,
      audience: AUDIENCE,
      requiredClaims: ["sub", "exp"],
    });
    return typeof payload.sub === "string" && ACCOUNT.test(payload.sub) ? payload.sub : null;
  } catch {
    return null;
  }
}

function signingKey(env: Env): Uint8Array {
  return new TextEncoder().encode(env.SESSION_SIGNING_KEY ?? "");
}
