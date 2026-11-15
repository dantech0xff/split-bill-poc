import { type PageKind, abuseMailto } from "../links";

export function Footer({ kind, abuseEmail }: { kind: PageKind; abuseEmail: string }) {
  const mailto = abuseMailto(abuseEmail, kind);
  return (
    <footer class="footer type-body-small">
      <p>Split Vibe never holds or moves money.</p>
      {mailto !== null && (
        <p>
          <a class="footer__link" href={mailto}>
            Report abuse
          </a>
        </p>
      )}
    </footer>
  );
}
