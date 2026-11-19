import { render } from "preact";
import "./tokens.css";
import "./app.css";
import { App } from "./app";
import { ABUSE_EMAIL_META, GOOGLE_CLIENT_ID_META, TURNSTILE_SITE_KEY_META, metaContent } from "./page-meta";
import { DELETE_ACCOUNT_PATH, DeleteAccountPage } from "./pages/delete-account-page";

const root = document.getElementById("app");
if (root !== null) {
  render(
    window.location.pathname === DELETE_ACCOUNT_PATH ? (
      <DeleteAccountPage clientId={metaContent(GOOGLE_CLIENT_ID_META)} />
    ) : (
      <App
        pathname={window.location.pathname}
        host={window.location.host}
        hash={window.location.hash}
        siteKey={metaContent(TURNSTILE_SITE_KEY_META)}
        abuseEmail={metaContent(ABUSE_EMAIL_META)}
        userAgent={navigator.userAgent}
      />
    ),
    root,
  );
}
