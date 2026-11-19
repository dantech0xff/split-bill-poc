import { type Page, expect, test } from "@playwright/test";
import { expectHeadersOfPage, watchForCspViolations } from "./local-proxy";

const ORIGIN = "http://127.0.0.1:8797";

const LEGAL_POLICY = "default-src 'none'; style-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
const DELETION_POLICY =
  "default-src 'none'; script-src 'self' https://accounts.google.com/gsi/client; style-src 'self' https://accounts.google.com/gsi/style; " +
  "img-src 'self' data:; connect-src 'self' https://accounts.google.com/gsi/; frame-src https://accounts.google.com/gsi/; " +
  "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

/** An ID token for person@example.com, unsigned: the page only reads its payload, and the proxy's route is faked below. */
const ID_TOKEN = `eyJhbGciOiJSUzI1NiJ9.${Buffer.from(JSON.stringify({ sub: "1", email: "person@example.com" })).toString("base64url")}.signature`;

/**
 * Google's library as a fake, served where the page loads the real one: `renderButton` draws a
 * plain button that signs in with [ID_TOKEN]. Nothing reaches Google.
 */
const FAKE_GOOGLE = `
window.google = { accounts: { id: {
  initialize(configuration) { window.googleConfiguration = configuration; },
  renderButton(parent) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Sign in with Google";
    button.addEventListener("click", () => window.googleConfiguration.callback({ credential: ${JSON.stringify(ID_TOKEN)} }));
    parent.replaceChildren(button);
  },
} } };
`;

/** Answers every request of [page] to Google with the fake library, or refuses it, and keeps the Referer each one carried. */
async function fakeGoogle(page: Page): Promise<string[]> {
  const referers: string[] = [];
  await page.route("https://accounts.google.com/**", async (route) => {
    const request = route.request();
    referers.push((await request.allHeaders()).referer ?? "");
    if (new URL(request.url()).pathname === "/gsi/client") await route.fulfill({ contentType: "text/javascript; charset=utf-8", body: FAKE_GOOGLE });
    else await route.abort();
  });
  return referers;
}

test("the privacy policy and the terms are static pages that run no script and link each other", async ({ page }) => {
  await fakeGoogle(page);
  const violations = await watchForCspViolations(page, ORIGIN);
  const requests: string[] = [];
  page.on("request", (request) => requests.push(new URL(request.url()).pathname));
  const footerLink = (name: string) => page.getByRole("contentinfo").getByRole("link", { name, exact: true });

  const privacy = await page.goto("/privacy");
  expectHeadersOfPage(privacy?.headers() ?? {}, LEGAL_POLICY);
  await expect(page).toHaveTitle("Privacy policy · Split Vibe");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/privacy policy/i);
  await expect(footerLink("Privacy policy")).toHaveAttribute("aria-current", "page");
  // The link pages' stylesheet lays it out, and nothing else loads.
  await expect(page.locator("main.page")).toHaveCSS("display", "flex");
  expect(requests).toEqual(["/privacy", expect.stringMatching(/^\/assets\/[^/]+\.css$/)]);
  expect(await page.locator("script").count()).toBe(0);

  await footerLink("Terms of use").click();
  await expect(page).toHaveURL(`${ORIGIN}/terms`);
  const terms = await page.request.get("/terms");
  expectHeadersOfPage(terms.headers(), LEGAL_POLICY);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/terms of use/i);
  await expect(footerLink("Terms of use")).toHaveAttribute("aria-current", "page");
  await footerLink("Delete account").click();
  await expect(page).toHaveURL(`${ORIGIN}/delete-account`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Delete your Split Vibe account");
  expect(violations).toEqual([]);
});

test("the deletion page signs in with Google, asks once more, and deletes the account", async ({ page }) => {
  const referers = await fakeGoogle(page);
  const deletions: unknown[] = [];
  const answers = [
    { status: 401, contentType: "application/json", body: JSON.stringify({ error: "id_token" }) },
    { status: 204, body: "" },
  ];
  await page.route("**/v1/account/delete-with-google", async (route) => {
    deletions.push(route.request().postDataJSON());
    await route.fulfill(answers.shift() ?? { status: 500 });
  });
  const violations = await watchForCspViolations(page, ORIGIN);

  const answer = await page.goto("/delete-account");
  expectHeadersOfPage(answer?.headers() ?? {}, DELETION_POLICY, "strict-origin-when-cross-origin");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Delete your Split Vibe account");
  await expect(page.getByText("A Pro subscription goes on until you cancel it in Google Play")).toBeVisible();
  const clientId = await page.locator('meta[name="sv-google-client-id"]').getAttribute("content");
  expect(clientId).toMatch(/\.apps\.googleusercontent\.com$/);

  await page.getByRole("button", { name: "Sign in with Google" }).click();
  // Google was told the page's origin and nothing more of its address.
  expect(referers).toEqual([`${ORIGIN}/`]);
  expect(await page.evaluate(() => (window as unknown as { googleConfiguration: { client_id: string; ux_mode: string } }).googleConfiguration)).toMatchObject({
    client_id: clientId,
    ux_mode: "popup",
  });
  await expect(page.getByRole("heading", { name: "Delete this account?" })).toBeFocused();
  await expect(page.getByText("You signed in as person@example.com.")).toBeVisible();

  // The sign-in ran out before the deletion: the page asks for it again.
  await page.getByRole("button", { name: "Delete my account" }).click();
  await expect(page.getByRole("alert")).toHaveText("Your sign-in with Google ran out before the account was deleted. Sign in again.");
  await page.getByRole("button", { name: "Sign in with Google" }).click();
  await page.getByRole("button", { name: "Delete my account" }).click();

  await expect(page.getByRole("heading", { name: "Your account is deleted" })).toBeFocused();
  await expect(page.getByRole("link", { name: "Manage your subscriptions on Google Play" }).first()).toHaveAttribute(
    "href",
    "https://play.google.com/store/account/subscriptions",
  );
  expect(deletions).toEqual([{ idToken: ID_TOKEN }, { idToken: ID_TOKEN }]);
  expect(violations).toEqual([]);
});

test("the deletion page loads nothing from Google but its library, and the link pages cannot load it at all", async ({ page }) => {
  const referers = await fakeGoogle(page);
  await page.goto("/delete-account");
  await expect(page.getByRole("button", { name: "Sign in with Google" })).toBeVisible();
  expect(referers).toHaveLength(1);

  // A link page's policy refuses Google's library outright.
  const blocked = await watchForCspViolations(page, ORIGIN);
  await page.goto("/g/#AAAAAAAAAAAAAAAAAAAAAA");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("This link does not exist");
  await page.evaluate(
    (source) =>
      new Promise<void>((resolve) => {
        const script = document.createElement("script");
        script.src = source;
        script.addEventListener("error", () => resolve());
        script.addEventListener("load", () => resolve());
        document.head.appendChild(script);
      }),
    "https://accounts.google.com/gsi/client",
  );
  expect(referers).toHaveLength(1);
  expect(blocked.some((line) => line.includes("accounts.google.com"))).toBe(true);
});
