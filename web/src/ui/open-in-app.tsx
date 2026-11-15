import { isAndroid, openInAppHref } from "../links";

interface OpenInAppProps {
  link: { host: string; path: string; token: string };
  userAgent: string;
  /** Whether getting the app is the page's main action, drawn as a filled button. */
  main: boolean;
}

/** "Open in Split Vibe" on Android, where the app can take the link; Google Play elsewhere. */
export function OpenInApp({ link, userAgent, main }: OpenInAppProps) {
  const android = isAndroid(userAgent);
  const href = openInAppHref(link, android);
  const look = main ? "button button--filled" : "button button--tonal";
  if (android) {
    return (
      <a class={look} href={href}>
        Open in Split Vibe
      </a>
    );
  }
  return (
    <>
      <a class={look} href={href} target="_blank" rel="noopener noreferrer">
        Get Split Vibe on Google Play
      </a>
      <p class="type-body-small supporting">Split Vibe is an app for Android phones.</p>
    </>
  );
}
