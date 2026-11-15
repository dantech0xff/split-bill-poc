export const APP_PACKAGE = "com.dantech.splitvibe";
export const PLAY_STORE_URL = `https://play.google.com/store/apps/details?id=${APP_PACKAGE}`;

const MAX_PAYMENT_LINK = 2048;
const SPACE_OR_CONTROL = /[\s\u0000-\u001f\u007f-\u009f]/;

export type SafePaymentLink = { kind: "web"; href: string; host: string } | { kind: "upi"; href: string };

/**
 * The payment link to offer, or null for anything but a link that starts `https://` and has a
 * host, or one that starts `upi://pay?`, without whitespace or control characters. The proxy
 * refuses others too; the page checks again because it is the page that hands the link to the
 * browser. A web link is passed on as the browser parsed it, so the address it opens is the one
 * checked here.
 */
export function safePaymentLink(link: string | null | undefined): SafePaymentLink | null {
  if (typeof link !== "string" || link.length > MAX_PAYMENT_LINK || SPACE_OR_CONTROL.test(link)) return null;
  if (link.startsWith("upi://pay?")) return { kind: "upi", href: link };
  if (!link.startsWith("https://")) return null;
  let url: URL;
  try {
    url = new URL(link);
  } catch {
    return null;
  }
  if (url.protocol !== "https:" || url.hostname === "") return null;
  return { kind: "web", href: url.href, host: url.hostname };
}

/** Whether this browser runs on Android, where an `intent:` link can open the app. */
export function isAndroid(userAgent: string): boolean {
  return /\bAndroid\b/i.test(userAgent);
}

/**
 * The link of the "Open in Split Vibe" button: on Android an `intent:` link to the same link,
 * `https://<host><path>#<token>`, that opens the app, or Google Play when the app is missing;
 * elsewhere Google Play, which never gets the token. [token] has been checked to be a link's
 * token, so it cannot end the fragment or add to the intent.
 */
export function openInAppHref(link: { host: string; path: string; token: string }, android: boolean): string {
  if (!android) return PLAY_STORE_URL;
  const fallback = encodeURIComponent(PLAY_STORE_URL);
  return `intent://${link.host}${link.path}#${link.token}#Intent;scheme=https;package=${APP_PACKAGE};S.browser_fallback_url=${fallback};end`;
}

/**
 * A plain address, without anything that would change what a `mailto:` link does: at most 254
 * characters; letters, digits and `._%+-`, an `@`, then two or more labels of letters, digits and
 * `-` joined by dots. The Worker takes `ABUSE_EMAIL` by the same rule (proxy/src/web.ts, and
 * docs/proxy.md), so that an address it puts in the page is never one the page leaves out.
 */
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$/;
const MAX_EMAIL_LENGTH = 254;

export type PageKind = "share" | "request" | "invite";

const PAGE_NAMES: Record<PageKind, string> = {
  share: "bill link",
  request: "payment request link",
  invite: "group invite link",
};

/**
 * The "Report abuse" link to [address], or null when there is no usable address. The subject
 * names the kind of page only, never the link or anything on it.
 */
export function abuseMailto(address: string | null | undefined, kind: PageKind): string | null {
  const trimmed = address?.trim() ?? "";
  if (trimmed.length > MAX_EMAIL_LENGTH || !EMAIL.test(trimmed)) return null;
  return `mailto:${trimmed}?subject=${encodeURIComponent(`Report abuse: Split Vibe ${PAGE_NAMES[kind]}`)}`;
}
