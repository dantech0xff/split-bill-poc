import { http, HttpResponse } from "msw";
import { network } from "./network";

const SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

/** The `TURNSTILE_SECRET` the tests run with, from vitest.config.ts: the secret of the fake's own widget. */
export const TEST_SECRET = "test-turnstile-secret";
/** Cloudflare's secrets for testing, which pass and refuse every token. */
export const PASSING_SECRET = "1x0000000000000000000000000000000AA";
export const FAILING_SECRET = "2x0000000000000000000000000000000AA";

export type FakeTurnstile = {
  /** A token of the fake's widget, solved for [action] on a page at [hostname]: by default `claim` on the tests' host. */
  token(solved?: { action?: string; hostname?: string }): string;
  /** How many times the proxy asked Siteverify. */
  checks(): number;
};

/**
 * Cloudflare's Siteverify, as a fake. For [TEST_SECRET] it takes only the tokens it handed out,
 * each once, and answers with the action and the hostname each was solved for. Cloudflare's test
 * secrets pass or refuse anything, and answer as Cloudflare does: without an action, and with
 * `example.com` as the hostname. [answer], when given, answers instead.
 */
export function fakeTurnstile(answer?: () => Response): FakeTurnstile {
  const issued = new Map<string, { action: string; hostname: string }>();
  const spent = new Set<string>();
  let checks = 0;
  network.use(
    http.post(SITEVERIFY_URL, async ({ request }) => {
      checks += 1;
      if (answer !== undefined) return answer();
      const { secret, response } = (await request.json()) as { secret?: unknown; response?: unknown };
      const now = new Date().toISOString();
      if (secret === PASSING_SECRET) return HttpResponse.json({ success: true, challenge_ts: now, hostname: "example.com", "error-codes": [] });
      if (secret === FAILING_SECRET) return HttpResponse.json({ success: false, "error-codes": ["invalid-input-response"] });
      if (secret !== TEST_SECRET) return HttpResponse.json({ success: false, "error-codes": ["invalid-input-secret"] });
      const token = typeof response === "string" ? response : "";
      const solved = issued.get(token);
      if (solved === undefined) return HttpResponse.json({ success: false, "error-codes": ["invalid-input-response"] });
      if (spent.has(token)) return HttpResponse.json({ success: false, "error-codes": ["timeout-or-duplicate"] });
      spent.add(token);
      return HttpResponse.json({ success: true, challenge_ts: now, hostname: solved.hostname, "error-codes": [], action: solved.action, cdata: "" });
    }),
  );
  return {
    token(solved = {}) {
      const token = `turnstile.${crypto.randomUUID()}`;
      issued.set(token, { action: solved.action ?? "claim", hostname: solved.hostname ?? "proxy.test" });
      return token;
    },
    checks: () => checks,
  };
}
