import type { Env } from "./index";
import { isRecord, readJson } from "./input";
import { errorName } from "./spend";
import { servedLocally } from "./switches";

const SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
/** The action the web page gives its Turnstile widget before a new claim. */
const CLAIM_ACTION = "claim";
/** The longest token Turnstile makes. */
const MAX_TOKEN_CHARS = 2_048;
/** How long Cloudflare may take to check a token; Siteverify usually answers within a fraction of a second. */
const TIMEOUT_MS = 5_000;
/** Far more than an answer of Siteverify needs. */
const MAX_ANSWER_BYTES = 16 * 1024;
/** What Siteverify says of a token that is bad, expired or spent: a verdict on the token rather than on the proxy. */
const TOKEN_FAULTS = new Set(["invalid-input-response", "timeout-or-duplicate"]);
/**
 * Cloudflare's secrets for testing, which pass every token, refuse every token, or find every
 * token spent. Siteverify answers them without an action and with `example.com` as the hostname,
 * so with one of them only its verdict can be checked. They are taken only on a computer (see
 * [servedLocally]); anywhere else a new claim is 503, so that one set on the deployed Worker by
 * mistake never lets every token pass.
 */
const TEST_SECRETS = new Set(["1x0000000000000000000000000000000AA", "2x0000000000000000000000000000000AA", "3x0000000000000000000000000000000AA"]);
/** The most of a value Siteverify answered that a log line repeats. */
const LOGGED_CHARS = 64;

function secretOf(env: Env): string {
  return (env.TURNSTILE_SECRET ?? "").trim();
}

/**
 * Whether new claims can be checked at all on a page served at [hostname]: the secret
 * `TURNSTILE_SECRET` is set, and is not one of Cloudflare's test secrets unless the proxy runs on
 * a computer. A test secret anywhere else is logged, never with a token.
 */
export function turnstileReady(env: Env, hostname: string): boolean {
  const secret = secretOf(env);
  if (secret === "") return false;
  if (!TEST_SECRETS.has(secret) || servedLocally(hostname)) return true;
  console.error(JSON.stringify({ event: "turnstile_test_secret" }));
  return false;
}

/** [value] when it can be a Turnstile token: text of 1 to 2,048 characters. Anything else is no token. */
export function turnstileTokenOf(value: unknown): string | null {
  return typeof value === "string" && value !== "" && value.length <= MAX_TOKEN_CHARS ? value : null;
}

/** What Siteverify said of a field, for a log line: its text, cut short, or the kind of value it was. */
function logged(value: unknown): string {
  return typeof value === "string" ? value.slice(0, LOGGED_CHARS) : value === undefined ? "missing" : typeof value;
}

/**
 * Whether Cloudflare's Siteverify says [token] passed the Turnstile widget whose secret is
 * `TURNSTILE_SECRET`, with the action `claim`, on a page served at [hostname], and was not used
 * before: a token is good for one check only. [remoteIp], the caller's CF-Connecting-IP, goes to
 * Siteverify too when there is one. Anything else is false: a refusal, an answer of another shape,
 * an action or a hostname that does not match, or no answer within [TIMEOUT_MS]. Failures that are
 * not a verdict on the token are logged, with Cloudflare's error codes and never the token, and so
 * are an action or a hostname that does not match. Call it only once [turnstileReady].
 */
export async function turnstilePassed(env: Env, token: string, hostname: string, remoteIp: string | null): Promise<boolean> {
  const secret = secretOf(env);
  const answer = await siteverify(secret, token, remoteIp);
  if (answer === null) return false;
  if (answer.success !== true) {
    const listed = answer["error-codes"];
    const codes = Array.isArray(listed) ? listed.filter((code): code is string => typeof code === "string").slice(0, 5) : [];
    if (codes.some((code) => !TOKEN_FAULTS.has(code))) console.error(JSON.stringify({ event: "turnstile_failed", codes }));
    return false;
  }
  // [turnstileReady] took a test secret only on a computer, where only the verdict can be checked.
  if (TEST_SECRETS.has(secret)) return true;
  if (answer.action === CLAIM_ACTION && answer.hostname === hostname) return true;
  console.error(JSON.stringify({ event: "turnstile_mismatch", action: logged(answer.action), hostname: logged(answer.hostname), expectedHostname: hostname }));
  return false;
}

/** Siteverify's answer about [token], or null when it gave none that can be read; that is logged. */
async function siteverify(secret: string, token: string, remoteIp: string | null): Promise<Record<string, unknown> | null> {
  const remote = remoteIp?.trim() ?? "";
  try {
    const response = await fetch(SITEVERIFY_URL, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ secret, response: token, ...(remote === "" ? {} : { remoteip: remote }) }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    const answer = await readJson(response, MAX_ANSWER_BYTES);
    if (response.ok && isRecord(answer)) return answer;
    console.error(JSON.stringify({ event: "turnstile_unavailable", status: response.status }));
    return null;
  } catch (error) {
    console.error(JSON.stringify({ event: "turnstile_unavailable", error: errorName(error) }));
    return null;
  }
}
