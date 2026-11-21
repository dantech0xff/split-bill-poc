import { useEffect, useRef, useState } from "preact/hooks";
import { type DeletionError, deleteAccountWithGoogle } from "../api";
import { type GoogleButtonOptions, type GoogleCredential, type GoogleIdApi, emailOf, loadGoogleSignIn } from "../google-sign-in";
import { AlertIcon, NoticeIcon } from "../ui/icons";
import { waitText } from "../ui/problem-view";
import { SiteFooter } from "../ui/site-footer";

/** The path of the page, which the web app draws instead of a link page. */
export const DELETE_ACCOUNT_PATH = "/delete-account";

/** Where someone manages, and cancels, their subscriptions on Google Play. */
export const PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions";

const BUTTON: GoogleButtonOptions = { type: "standard", theme: "outline", size: "large", text: "signin_with", shape: "pill", logo_alignment: "left" };

/** What the page asks of the world: Google's sign-in, and the proxy's deletion. Tests give their own. */
export interface DeletionServices {
  loadGoogle(): Promise<GoogleIdApi>;
  deleteAccount(idToken: string): Promise<{ ok: true } | { ok: false; error: DeletionError }>;
}

const BROWSER: DeletionServices = {
  loadGoogle: () => loadGoogleSignIn(),
  deleteAccount: (idToken) => deleteAccountWithGoogle(idToken),
};

/** Where the deletion stands: signing in, the last word before deleting, deleting, and what came of it. */
type Step =
  | { kind: "sign_in"; expired: boolean }
  | { kind: "confirm"; idToken: string; email: string | null }
  | { kind: "deleting"; idToken: string; email: string | null }
  | { kind: "failed"; idToken: string; email: string | null; error: Exclude<DeletionError, { kind: "id_token" }> }
  | { kind: "deleted" };

type Google = { state: "loading" } | { state: "ready"; api: GoogleIdApi } | { state: "failed" };

function failureText(error: Exclude<DeletionError, { kind: "id_token" }>): string {
  switch (error.kind) {
    case "rate_limited":
      return `This account was asked to be deleted too many times in the last hour. ${waitText(error.retryAfterSeconds)}`;
    case "subscription_unavailable":
      return "The service that keeps Pro purchases for Split Vibe did not answer, so nothing was deleted. Try again in a few minutes.";
    case "sign_in_off":
      return "Deleting your account on the web is not available right now. You can delete it in the app, as below.";
    case "network":
      return "Split Vibe could not be reached, and nothing was deleted. Check your connection, then try again.";
    case "unexpected":
      return "Something went wrong, and nothing was deleted. Try again in a moment.";
  }
}

function PlaySubscriptionsLink() {
  return (
    <a href={PLAY_SUBSCRIPTIONS_URL} target="_blank" rel="noopener noreferrer">
      Manage your subscriptions on Google Play
    </a>
  );
}

/** What deleting an account deletes and what it leaves, as the proxy and the app do it. */
function WhatHappens() {
  return (
    <>
      <section class="hero" aria-labelledby="delete-title">
        <h1 id="delete-title" class="type-headline-small hero__title">
          Delete your Split Vibe account
        </h1>
        <p class="type-body-large">
          Deleting your account removes what Split Vibe's server keeps about you, apart from what is listed under What stays. It cannot be undone.
        </p>
      </section>
      <section class="section" aria-labelledby="deleted-title">
        <h2 id="deleted-title" class="type-title-medium">
          What is deleted
        </h2>
        <ul class="bullets type-body-large" role="list">
          <li>Your account, your Pro plan as Split Vibe knows it, and your purchase history at RevenueCat, which handles Pro purchases for Split Vibe.</li>
          <li>Your place in groups. You show as "Former member", and every amount stays as it was, so nobody else's balance changes. A group you own passes to another member.</li>
          <li>How to pay you, on the payment requests you made that are not confirmed yet.</li>
          <li>The bill links you shared, which stop working, and what people picked on them.</li>
          <li>The phones that get Split Vibe's notifications for you.</li>
          <li>
            Your sign-in on every phone. A phone still signed in to the account is signed out: Split Vibe's server no longer accepts its sign-in, so it cannot use the
            account until you sign in again.
          </li>
        </ul>
      </section>
      <section class="section" aria-labelledby="kept-title">
        <h2 id="kept-title" class="type-title-medium">
          What stays
        </h2>
        <ul class="bullets type-body-large" role="list">
          <li>
            Bills, people and payment methods on your phone stay on your phone. To delete them too, delete your account in the app and tick "Also delete the bills,
            people and payment methods on this phone", or uninstall Split Vibe.
          </li>
          <li>
            A Pro subscription goes on until you cancel it in Google Play: deleting your account does not cancel it. <PlaySubscriptionsLink />.
          </li>
          <li>How many AI calls your account used this month, until the month ends, so that deleting the account and signing in again does not start them over.</li>
          <li>When your account was deleted, for 30 days, so that phones still signed in to it are signed out.</li>
          <li>Counts of what your account did in the last hour, until the next daily clean-up.</li>
          <li>The bills and settlements of your groups, so that their balances stay right, and the payment requests between you and others, for the other person.</li>
        </ul>
      </section>
    </>
  );
}

function InTheApp() {
  return (
    <section class="section" aria-labelledby="in-app-title">
      <h2 id="in-app-title" class="type-title-medium">
        Delete it in the app
      </h2>
      <ol class="steps steps--compact type-body-large" role="list">
        <li>Open Split Vibe on your phone.</li>
        <li>Go to Settings.</li>
        <li>Tap Delete account.</li>
      </ol>
    </section>
  );
}

/** The steps of deleting with Google: sign in, confirm, and what came of it. */
function Deletion({ clientId, services }: { clientId: string; services: DeletionServices }) {
  const [google, setGoogle] = useState<Google>({ state: "loading" });
  const [loads, setLoads] = useState(0);
  const [step, setStep] = useState<Step>({ kind: "sign_in", expired: false });
  const slot = useRef<HTMLDivElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const shown = useRef(false);

  useEffect(() => {
    let current = true;
    setGoogle({ state: "loading" });
    services.loadGoogle().then(
      (api) => {
        if (!current) return;
        api.initialize({
          client_id: clientId,
          callback: (response: GoogleCredential) => {
            const idToken = typeof response?.credential === "string" ? response.credential : "";
            if (idToken !== "") setStep({ kind: "confirm", idToken, email: emailOf(idToken) });
          },
          auto_select: false,
          cancel_on_tap_outside: true,
          ux_mode: "popup",
        });
        setGoogle({ state: "ready", api });
      },
      () => {
        if (current) setGoogle({ state: "failed" });
      },
    );
    return () => {
      current = false;
    };
    // The client ID and the services belong to the page; [loads] counts tries after a failure.
  }, [loads]);

  // Google draws its button into the slot each time the step of signing in is shown.
  useEffect(() => {
    if (google.state === "ready" && step.kind === "sign_in" && slot.current !== null) google.api.renderButton(slot.current, BUTTON);
  }, [google, step]);

  // Each new step takes the focus, so that a screen reader says where the deletion stands.
  useEffect(() => {
    if (shown.current) heading.current?.focus();
    shown.current = true;
  }, [step.kind]);

  const remove = (idToken: string, email: string | null) => {
    setStep({ kind: "deleting", idToken, email });
    services.deleteAccount(idToken).then(
      (result) => {
        if (result.ok) setStep({ kind: "deleted" });
        else if (result.error.kind === "id_token") setStep({ kind: "sign_in", expired: true });
        else setStep({ kind: "failed", idToken, email, error: result.error });
      },
      () => setStep({ kind: "failed", idToken, email, error: { kind: "unexpected" } }),
    );
  };
  const cancel = () => setStep({ kind: "sign_in", expired: false });

  switch (step.kind) {
    case "sign_in":
      return (
        <section class="section section--card" aria-labelledby="action-title">
          <h2 id="action-title" class="type-title-medium" ref={heading} tabIndex={-1}>
            Delete your account
          </h2>
          {step.expired && (
            <p class="error-text type-body-medium" role="alert">
              <AlertIcon />
              Your sign-in with Google ran out before the account was deleted. Sign in again.
            </p>
          )}
          <p class="type-body-large">Sign in with the Google account you use in Split Vibe. You can still change your mind after that.</p>
          {google.state === "loading" && <p class="type-body-medium supporting">Loading Sign in with Google…</p>}
          {google.state === "failed" && (
            <>
              <p class="error-text type-body-medium" role="alert">
                <AlertIcon />
                Sign in with Google could not be loaded. Check your connection, then try again, or delete your account in the app.
              </p>
              <button type="button" class="button button--tonal" onClick={() => setLoads((count) => count + 1)}>
                Try again
              </button>
            </>
          )}
          {/* Keyed, so that the next step never takes it over with the button Google drew into it. */}
          <div key="google-button" class="google-button" ref={slot} />
        </section>
      );
    case "confirm":
    case "deleting": {
      const deleting = step.kind === "deleting";
      return (
        <section class="section section--card" aria-labelledby="action-title" aria-busy={deleting}>
          <h2 id="action-title" class="type-title-medium" ref={heading} tabIndex={-1}>
            Delete this account?
          </h2>
          <p class="type-body-large">
            {step.email === null ? "You signed in with Google." : `You signed in as ${step.email}.`} Deleting the account cannot be undone. It signs out every phone
            still signed in to it, and it does not cancel a Pro subscription: cancel that on Google Play.
          </p>
          <div class="actions">
            <button type="button" class="button button--danger" disabled={deleting} onClick={() => remove(step.idToken, step.email)}>
              {deleting ? "Deleting your account…" : "Delete my account"}
            </button>
            <button type="button" class="button button--text" disabled={deleting} onClick={cancel}>
              Cancel
            </button>
          </div>
        </section>
      );
    }
    case "failed":
      return (
        <section class="section section--card" aria-labelledby="action-title">
          <h2 id="action-title" class="type-title-medium" ref={heading} tabIndex={-1}>
            Your account was not deleted
          </h2>
          <p class="error-text type-body-medium">
            <AlertIcon />
            {failureText(step.error)}
          </p>
          <div class="actions">
            <button type="button" class="button button--tonal" onClick={() => remove(step.idToken, step.email)}>
              Try again
            </button>
            <button type="button" class="button button--text" onClick={cancel}>
              Cancel
            </button>
          </div>
        </section>
      );
    case "deleted":
      return (
        <section class="section section--card" aria-labelledby="action-title">
          <h2 id="action-title" class="type-title-medium" ref={heading} tabIndex={-1}>
            Your account is deleted
          </h2>
          <p class="type-body-large">
            Split Vibe's server has deleted your account, apart from what this page lists under What stays. The bills, people and payment methods on your phone are
            still there until you delete them in the app or uninstall it.
          </p>
          <p class="type-body-large">
            Phones that were still signed in to the account are now signed out: Split Vibe's server no longer accepts their sign-in, so they cannot use the account
            until you sign in again.
          </p>
          <p class="type-body-large">
            If you have Pro, cancel the subscription on Google Play: deleting your account does not cancel it. <PlaySubscriptionsLink />.
          </p>
        </section>
      );
  }
}

/**
 * `/delete-account`: says what deleting an account deletes and what it leaves, and deletes the
 * account of whoever signs in with Google there, once they confirm. Without [clientId], which the
 * Worker leaves empty until sign-in with Google works, it says deleting on the web is not available
 * yet. Either way it says how to delete in the app.
 */
export function DeleteAccountPage({ clientId, services = BROWSER }: { clientId: string; services?: DeletionServices }) {
  return (
    <>
      <header class="app-bar">
        <p class="app-bar__brand type-title-medium">Split Vibe</p>
      </header>
      <main class="page">
        <WhatHappens />
        {clientId === "" ? (
          <section class="section section--card" aria-labelledby="action-title">
            <h2 id="action-title" class="type-title-medium">
              Delete your account
            </h2>
            <p class="notice type-body-large">
              <NoticeIcon />
              Deleting your account on the web is not available yet. You can delete it in the app, as below.
            </p>
          </section>
        ) : (
          <Deletion clientId={clientId} services={services} />
        )}
        <InTheApp />
      </main>
      <SiteFooter current={DELETE_ACCOUNT_PATH} />
    </>
  );
}
