/** What Google gives the page once someone signed in: the ID token, a JWT for the page's client ID. */
export interface GoogleCredential {
  credential: string;
}

/** The parts of Google Identity Services' `google.accounts.id` the deletion page uses. */
export interface GoogleIdConfiguration {
  client_id: string;
  callback: (response: GoogleCredential) => void;
  auto_select: false;
  cancel_on_tap_outside: true;
  ux_mode: "popup";
}

export interface GoogleButtonOptions {
  type: "standard";
  theme: "outline";
  size: "large";
  text: "signin_with";
  shape: "pill";
  logo_alignment: "left";
}

export interface GoogleIdApi {
  initialize(configuration: GoogleIdConfiguration): void;
  renderButton(parent: HTMLElement, options: GoogleButtonOptions): void;
}

declare global {
  interface Window {
    google?: { accounts?: { id?: GoogleIdApi } };
  }
}

/** Google Identity Services' library, which only /delete-account loads, and only its policy allows. */
export const GOOGLE_SIGN_IN_SCRIPT = "https://accounts.google.com/gsi/client";

let loading: Promise<GoogleIdApi> | null = null;

/** `google.accounts.id` once the library is there, or undefined. */
function idApiOf(view: Window): GoogleIdApi | undefined {
  return view.google?.accounts?.id;
}

/** Loads Google Identity Services' library once, and gives its `google.accounts.id`. A failed load can be tried again. */
export function loadGoogleSignIn(doc: Document = document, view: Window = window): Promise<GoogleIdApi> {
  const ready = idApiOf(view);
  if (ready !== undefined) return Promise.resolve(ready);
  if (loading) return loading;
  const attempt = new Promise<GoogleIdApi>((resolve, reject) => {
    const script = doc.createElement("script");
    script.src = GOOGLE_SIGN_IN_SCRIPT;
    script.async = true;
    script.addEventListener("load", () => {
      const api = idApiOf(view);
      if (api !== undefined) resolve(api);
      else reject(new Error("Google Identity Services did not start"));
    });
    script.addEventListener("error", () => {
      script.remove();
      reject(new Error("Google Identity Services could not be loaded"));
    });
    doc.head.appendChild(script);
  });
  loading = attempt;
  attempt.catch(() => {
    if (loading === attempt) loading = null;
  });
  return attempt;
}

/**
 * The address an ID token names, to show whose account is about to be deleted; null when it names
 * none. The token is only read here: the proxy checks it.
 */
export function emailOf(idToken: string): string | null {
  const payload = idToken.split(".")[1];
  if (payload === undefined || payload === "") return null;
  try {
    const binary = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const claims: unknown = JSON.parse(new TextDecoder().decode(Uint8Array.from(binary, (character) => character.charCodeAt(0))));
    if (typeof claims !== "object" || claims === null || !("email" in claims)) return null;
    const { email } = claims;
    return typeof email === "string" && email !== "" && email.length <= 320 ? email : null;
  } catch {
    return null;
  }
}
