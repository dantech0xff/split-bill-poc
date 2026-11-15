import type { PageKind } from "./links";

/** A link's token: 128 bits in base64url, without padding. */
const TOKEN = /^[A-Za-z0-9_-]{22}$/;

const PATHS: Record<string, PageKind> = { "/s/": "share", "/r/": "request", "/g/": "invite" };

export type Route =
  | { kind: PageKind; path: string; token: string | null }
  | { kind: "unknown" };

/**
 * Which page to show for [pathname], and the token in [hash], the link's fragment. The token is
 * only ever read from the fragment, which the browser never sends; null when the fragment holds
 * nothing a link could carry.
 */
export function routeOf(pathname: string, hash: string): Route {
  const kind = PATHS[pathname];
  if (kind === undefined) return { kind: "unknown" };
  const fragment = hash.startsWith("#") ? hash.slice(1) : hash;
  return { kind, path: pathname, token: TOKEN.test(fragment) ? fragment : null };
}
