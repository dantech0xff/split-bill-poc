import type { Env } from "./index";

/** The header that carries the app's version code. */
export const VERSION_HEADER = "X-SplitVibe-Version";

/**
 * Whether bills may be read with AI. The owner turns it off by setting `AI_ENABLED` to anything but
 * "true"; the app then offers entering the bill by hand.
 */
export function aiEnabled(env: Env): boolean {
  return env.AI_ENABLED.trim() === "true";
}

/** Whether the app that sent [request] is at least `MIN_APP_VERSION`. A request without a version is as old as can be. */
export function appVersionOk(request: Request, env: Env): boolean {
  return wholeNumber(request.headers.get(VERSION_HEADER)) >= wholeNumber(env.MIN_APP_VERSION);
}

function wholeNumber(value: string | null): number {
  const text = value?.trim() ?? "";
  return /^\d{1,9}$/.test(text) ? Number(text) : 0;
}

/** The hostnames of a proxy that runs on a computer, as `wrangler dev` serves it; `URL.hostname` writes IPv6 in brackets. */
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

/**
 * Whether the proxy was asked at [hostname] on a computer, as `wrangler dev` serves it. The
 * deployed Worker is only ever reached at its own hostname, so what this allows never happens there.
 */
export function servedLocally(hostname: string): boolean {
  return LOCAL_HOSTS.has(hostname);
}
