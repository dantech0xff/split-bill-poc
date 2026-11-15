import type { Env } from "./index";
import { isRecord, readJson } from "./input";
import { errorName } from "./spend";

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
 * so with one of them only its verdict can be checked. None belongs on the deployed Worker.
 */
const TEST_SECRETS = new Set(["1x0000000000000000000000000000000AA", "2x0000000000000000000000000000000AA", "3x0000000000000000000000000000000AA"]);

/** Whether new claims can be checked at all: the secret `TURNSTILE_SECRET` is set. */
export function turnstileReady(env: Env): boolean {
  return (env.TURNSTILE_SECRET ?? "").trim() !== "";
}

/** [value] when it can be a Turnstile token: text of 1 to 2,048 characters. Anything else is no token. */
export function turnstileTokenOf(value: unknown): string | null {
  return typeof value === "string" && value !== "" && value.length <= MAX_TOKEN_CHARS ? value : null;
}

/**
 * Whether Cloudflare's Siteverify says [token] passed the Turnstile widget whose secret is
 * `TURNSTILE_SECRET`, with the action `claim`, on a page served at [hostname], and was not used
 * before: a token is good for one check only. Anything else is false: a refusal, an answer of
 * another shape, or no answer within [TIMEOUT_MS]. Failures that are not a verdict on the token
 * are logged, with Cloudflare's error codes and never the token. Call it only once
 * [turnstileReady].
 */
export async function turnstilePassed(env: Env, token: string, hostname: string): Promise<boolean> {
  const secret = (env.TURNSTILE_SECRET ?? "").trim();
  const answer = await siteverify(secret, token);
  if (answer === null) return false;
  if (answer.success !== true) {
    const listed = answer["error-codes"];
    const codes = Array.isArray(listed) ? listed.filter((code): code is string => typeof code === "string").slice(0, 5) : [];
    if (codes.some((code) => !TOKEN_FAULTS.has(code))) console.error(JSON.stringify({ event: "turnstile_failed", codes }));
    return false;
  }
  if (TEST_SECRETS.has(secret)) return true;
  return answer.action === CLAIM_ACTION && answer.hostname === hostname;
}

/** Siteverify's answer about [token], or null when it gave none that can be read; that is logged. */
async function siteverify(secret: string, token: string): Promise<Record<string, unknown> | null> {
  try {
    const response = await fetch(SITEVERIFY_URL, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ secret, response: token }),
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
