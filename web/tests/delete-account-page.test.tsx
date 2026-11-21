import { act } from "preact/test-utils";
import { describe, expect, it, vi } from "vitest";
import type { DeletionError } from "../src/api";
import type { GoogleButtonOptions, GoogleIdApi, GoogleIdConfiguration } from "../src/google-sign-in";
import { type DeletionServices, DeleteAccountPage, PLAY_SUBSCRIPTIONS_URL } from "../src/pages/delete-account-page";
import { all, mount, textOf } from "./render";

const CLIENT_ID = "123-abc.apps.googleusercontent.com";

/** An ID token for person@example.com, unsigned, as only its payload is read on the page. */
const ID_TOKEN = `eyJhbGciOiJSUzI1NiJ9.${btoa(JSON.stringify({ sub: "1", email: "person@example.com" })).replace(/=+$/, "")}.signature`;

type Deleted = { ok: true } | { ok: false; error: DeletionError };

/**
 * Google's library as a fake: `renderButton` draws a plain button that signs in with [ID_TOKEN],
 * so no test reaches Google; and the proxy's deletion answering what the test says.
 */
function services(answers: Array<Deleted | Promise<Deleted>> = [{ ok: true }], load?: () => Promise<GoogleIdApi>) {
  const configurations: GoogleIdConfiguration[] = [];
  const buttons: GoogleButtonOptions[] = [];
  const google: GoogleIdApi = {
    initialize: (configuration) => configurations.push(configuration),
    renderButton: (parent, options) => {
      buttons.push(options);
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = "Sign in with Google";
      button.addEventListener("click", () => configurations.at(-1)?.callback({ credential: ID_TOKEN }));
      parent.replaceChildren(button);
    },
  };
  const deleteAccount = vi.fn(async (_idToken: string) => {
    const answer = answers.shift();
    if (answer === undefined) throw new Error("No answer left");
    return answer;
  });
  const loadGoogle = vi.fn(load ?? (() => Promise.resolve(google)));
  const given: DeletionServices = { loadGoogle, deleteAccount };
  return { given, configurations, buttons, deleteAccount, loadGoogle };
}

function buttonNamed(page: HTMLElement, name: string): HTMLButtonElement {
  const button = all(page, "button").find((element) => textOf(element) === name);
  if (!(button instanceof HTMLButtonElement)) throw new Error(`No button named ${name}`);
  return button;
}

async function click(page: HTMLElement, name: string) {
  await act(async () => buttonNamed(page, name).click());
}

/** The page signed in with Google, at the step that asks before deleting. */
async function signedIn(answers?: Array<Deleted | Promise<Deleted>>) {
  const fake = services(answers);
  const page = mount(<DeleteAccountPage clientId={CLIENT_ID} services={fake.given} />);
  await vi.waitFor(() => expect(fake.buttons).toHaveLength(1));
  await click(page, "Sign in with Google");
  return { page, ...fake };
}

function heading(page: HTMLElement): string {
  return textOf(page.querySelector("#action-title"));
}

describe("the page to delete an account", () => {
  it("says what is deleted and what stays, and how to delete in the app", () => {
    const page = mount(<DeleteAccountPage clientId="" services={services().given} />);

    expect(textOf(page.querySelector("h1"))).toBe("Delete your Split Vibe account");
    expect(all(page, "h2").map(textOf)).toEqual(["What is deleted", "What stays", "Delete your account", "Delete it in the app"]);
    expect(textOf(page.querySelector(".hero p"))).toBe(
      "Deleting your account removes what Split Vibe's server keeps about you, apart from what is listed under What stays. It cannot be undone.",
    );
    const deleted = textOf(page.querySelector('[aria-labelledby="deleted-title"]'));
    expect(deleted).toContain(
      "Your sign-in on every phone. A phone still signed in to the account is signed out: Split Vibe's server no longer accepts its sign-in, so it cannot use the account until you sign in again.",
    );
    const stays = textOf(page.querySelector('[aria-labelledby="kept-title"]'));
    expect(stays).toContain("Bills, people and payment methods on your phone stay on your phone.");
    expect(stays).toContain("A Pro subscription goes on until you cancel it in Google Play: deleting your account does not cancel it.");
    expect(stays).toContain("How many AI calls your account used this month, until the month ends");
    expect(stays).toContain("When your account was deleted, for 30 days, so that phones still signed in to it are signed out.");
    const play = page.querySelector<HTMLAnchorElement>(`a[href="${PLAY_SUBSCRIPTIONS_URL}"]`);
    expect(textOf(play)).toBe("Manage your subscriptions on Google Play");
    expect(play?.rel).toBe("noopener noreferrer");
    expect(all(page, ".steps li").map(textOf)).toEqual(["Open Split Vibe on your phone.", "Go to Settings.", "Tap Delete account."]);
    // The stylesheet draws the lists' markers, so each list says it is one, for Safari.
    expect(all(page, "ul.bullets, ol.steps").map((list) => `${list.tagName} ${list.getAttribute("role")}`)).toEqual(["UL list", "UL list", "OL list"]);
    expect(all(page, "footer a").map((link) => [textOf(link), link.getAttribute("href"), link.getAttribute("aria-current")])).toEqual([
      ["Privacy policy", "/privacy", null],
      ["Terms of use", "/terms", null],
      ["Delete account", "/delete-account", "page"],
    ]);
  });

  it("without a client ID, says deleting on the web is not available yet, and loads nothing from Google", () => {
    const fake = services();
    const page = mount(<DeleteAccountPage clientId="" services={fake.given} />);

    expect(textOf(page.querySelector(".notice"))).toBe("Deleting your account on the web is not available yet. You can delete it in the app, as below.");
    expect(fake.loadGoogle).not.toHaveBeenCalled();
    expect(all(page, "button")).toEqual([]);
  });

  it("offers Sign in with Google for the page's client ID, as a popup, never signing in by itself", async () => {
    const fake = services();
    const page = mount(<DeleteAccountPage clientId={CLIENT_ID} services={fake.given} />);

    expect(textOf(page)).toContain("Loading Sign in with Google…");
    await vi.waitFor(() => expect(fake.buttons).toHaveLength(1));
    expect(fake.configurations).toEqual([{ client_id: CLIENT_ID, callback: expect.any(Function), auto_select: false, cancel_on_tap_outside: true, ux_mode: "popup" }]);
    expect(fake.buttons).toEqual([{ type: "standard", theme: "outline", size: "large", text: "signin_with", shape: "pill", logo_alignment: "left" }]);
    expect(page.querySelector(".google-button button")).not.toBeNull();
    expect(textOf(page)).not.toContain("Loading Sign in with Google…");
  });

  it("asks once more, naming the account, before it deletes anything", async () => {
    const { page, deleteAccount } = await signedIn();

    expect(heading(page)).toBe("Delete this account?");
    expect(textOf(page.querySelector('[aria-labelledby="action-title"] p'))).toBe(
      "You signed in as person@example.com. Deleting the account cannot be undone. It signs out every phone still signed in to it, and it does not cancel a Pro subscription: cancel that on Google Play.",
    );
    expect(document.activeElement).toBe(page.querySelector("#action-title"));
    expect(deleteAccount).not.toHaveBeenCalled();
    // Google's button, which Google draws into the page itself, goes with the step of signing in.
    expect(all(page, '[aria-labelledby="action-title"] button').map((button) => textOf(button))).toEqual(["Delete my account", "Cancel"]);
  });

  it("deletes the account once confirmed, and says so, and that Pro must be cancelled on Google Play", async () => {
    const { page, deleteAccount } = await signedIn([{ ok: true }]);

    await click(page, "Delete my account");

    await vi.waitFor(() => expect(heading(page)).toBe("Your account is deleted"));
    expect(deleteAccount.mock.calls).toEqual([[ID_TOKEN]]);
    const outcome = textOf(page.querySelector('[aria-labelledby="action-title"]'));
    expect(outcome).toContain("Split Vibe's server has deleted your account, apart from what this page lists under What stays.");
    expect(outcome).toContain("The bills, people and payment methods on your phone are still there");
    expect(outcome).toContain(
      "Phones that were still signed in to the account are now signed out: Split Vibe's server no longer accepts their sign-in, so they cannot use the account until you sign in again.",
    );
    expect(outcome).not.toContain("no longer keeps your account");
    expect(outcome).toContain("If you have Pro, cancel the subscription on Google Play: deleting your account does not cancel it.");
    expect(document.activeElement).toBe(page.querySelector("#action-title"));
  });

  it("waits while it deletes, with the buttons off", async () => {
    let answer: (deleted: Deleted) => void = () => undefined;
    const { page } = await signedIn([new Promise<Deleted>((resolve) => (answer = resolve))]);

    await click(page, "Delete my account");

    const busy = buttonNamed(page, "Deleting your account…");
    expect(busy.disabled).toBe(true);
    expect(buttonNamed(page, "Cancel").disabled).toBe(true);
    expect(page.querySelector('[aria-labelledby="action-title"]')?.getAttribute("aria-busy")).toBe("true");
    await act(async () => answer({ ok: true }));
    await vi.waitFor(() => expect(heading(page)).toBe("Your account is deleted"));
  });

  it("goes back to signing in on Cancel, deleting nothing", async () => {
    const { page, deleteAccount, buttons } = await signedIn();

    await click(page, "Cancel");

    expect(heading(page)).toBe("Delete your account");
    await vi.waitFor(() => expect(buttons).toHaveLength(2));
    expect(deleteAccount).not.toHaveBeenCalled();
  });

  it("asks to sign in again when Google's sign-in ran out first", async () => {
    const { page, buttons } = await signedIn([{ ok: false, error: { kind: "id_token" } }]);

    await click(page, "Delete my account");

    await vi.waitFor(() => expect(heading(page)).toBe("Delete your account"));
    expect(textOf(page.querySelector('[role="alert"]'))).toBe("Your sign-in with Google ran out before the account was deleted. Sign in again.");
    await vi.waitFor(() => expect(buttons).toHaveLength(2));
  });

  it("says why the account was not deleted, and tries again with the same sign-in", async () => {
    const failures: Array<[DeletionError, string]> = [
      [{ kind: "rate_limited", retryAfterSeconds: 1800 }, "This account was asked to be deleted too many times in the last hour. Try again in 30 minutes."],
      [{ kind: "subscription_unavailable" }, "The service that keeps Pro purchases for Split Vibe did not answer, so nothing was deleted. Try again in a few minutes."],
      [{ kind: "sign_in_off" }, "Deleting your account on the web is not available right now. You can delete it in the app, as below."],
      [{ kind: "network" }, "Split Vibe could not be reached, and nothing was deleted. Check your connection, then try again."],
      [{ kind: "unexpected" }, "Something went wrong, and nothing was deleted. Try again in a moment."],
    ];
    for (const [error, said] of failures) {
      const { page, deleteAccount } = await signedIn([{ ok: false, error }, { ok: true }]);

      await click(page, "Delete my account");
      await vi.waitFor(() => expect(heading(page)).toBe("Your account was not deleted"));
      expect(textOf(page.querySelector(".error-text")), error.kind).toBe(said);
      await click(page, "Try again");

      await vi.waitFor(() => expect(heading(page)).toBe("Your account is deleted"));
      expect(deleteAccount.mock.calls).toEqual([[ID_TOKEN], [ID_TOKEN]]);
    }
  });

  it("says when Google's sign-in cannot be loaded, and loads it again when asked", async () => {
    const fake = services();
    let loads = 0;
    const google: GoogleIdApi = { initialize: vi.fn(), renderButton: vi.fn() };
    fake.loadGoogle.mockImplementation(() => (loads++ === 0 ? Promise.reject(new Error("blocked")) : Promise.resolve(google)));
    const page = mount(<DeleteAccountPage clientId={CLIENT_ID} services={fake.given} />);

    await vi.waitFor(() => expect(textOf(page.querySelector('[role="alert"]'))).toContain("Sign in with Google could not be loaded."));
    await click(page, "Try again");

    await vi.waitFor(() => expect(google.renderButton).toHaveBeenCalledTimes(1));
    expect(page.querySelector('[role="alert"]')).toBeNull();
  });
});
