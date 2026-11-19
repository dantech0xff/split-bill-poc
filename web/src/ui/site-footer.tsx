/** The pages every page but the link pages links to; scripts/static-pages.mjs gives the legal pages the same. */
export const SITE_LINKS = [
  { path: "/privacy", label: "Privacy policy" },
  { path: "/terms", label: "Terms of use" },
  { path: "/delete-account", label: "Delete account" },
] as const;

/** The footer of /delete-account, as the legal pages have it: the links of [SITE_LINKS], [current] marked as the page shown. */
export function SiteFooter({ current }: { current: string }) {
  return (
    <footer class="footer type-body-small">
      <nav aria-label="Split Vibe">
        <ul class="footer__links">
          {SITE_LINKS.map(({ path, label }) => (
            <li key={path}>
              <a class="footer__link" href={path} aria-current={path === current ? "page" : undefined}>
                {label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
    </footer>
  );
}
