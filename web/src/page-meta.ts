/** The content of `<meta name="[name]">`, which the Worker fills when it serves the page; "" when empty or missing. */
export function metaContent(name: string, doc: Document = document): string {
  for (const meta of Array.from(doc.getElementsByTagName("meta"))) {
    if (meta.getAttribute("name") === name) return (meta.getAttribute("content") ?? "").trim();
  }
  return "";
}

export const TURNSTILE_SITE_KEY_META = "sv-turnstile-site-key";
export const ABUSE_EMAIL_META = "sv-abuse-email";
