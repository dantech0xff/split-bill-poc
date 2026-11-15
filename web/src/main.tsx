import { render } from "preact";
import "./tokens.css";
import "./app.css";
import { App } from "./app";
import { ABUSE_EMAIL_META, TURNSTILE_SITE_KEY_META, metaContent } from "./page-meta";

const root = document.getElementById("app");
if (root !== null) {
  render(
    <App
      pathname={window.location.pathname}
      host={window.location.host}
      hash={window.location.hash}
      siteKey={metaContent(TURNSTILE_SITE_KEY_META)}
      abuseEmail={metaContent(ABUSE_EMAIL_META)}
      userAgent={navigator.userAgent}
    />,
    root,
  );
}
