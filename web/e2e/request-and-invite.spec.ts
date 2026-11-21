import { devices, expect, test } from "@playwright/test";
import { Account, expectPageHeaders, watchForCspViolations } from "./local-proxy";

const ORIGIN = "http://127.0.0.1:8797";
const HOST = "127.0.0.1:8797";
const PLAY = "https://play.google.com/store/apps/details?id=com.dantech.splitvibe";
const FALLBACK = `S.browser_fallback_url=${encodeURIComponent(PLAY)};end`;

test("a payment request shows who asks whom for what, how to pay, and opens in the app", async ({ page, request, browser }) => {
  const lan = new Account(request, "e2e-creditor");
  const asked = await lan.makeRequest({
    amountMinor: 150000,
    currency: { code: "VND", fractionDigits: 0 },
    payerName: "Minh",
    creditorName: "Lan",
    title: "Hot pot",
    method: {},
    payment: {
      link: "https://paypal.me/lan/150000VND",
      qrPayload: "https://paypal.me/lan/150000VND",
      details: "PayPal: paypal.me/lan\nAmount: ₫150,000",
      amountFilledIn: true,
    },
  });
  const violations = await watchForCspViolations(page, ORIGIN);
  const answer = await page.goto(`/r/#${asked.token}`);
  expectPageHeaders(answer?.headers() ?? {}, HOST);

  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Lan asks Minh for");
  await expect(page.locator(".hero")).toHaveText(/₫150,000\s*For Hot pot\s*Not paid yet/);
  const pay = page.getByRole("link", { name: "Pay on paypal.me" });
  await expect(pay).toHaveAttribute("href", "https://paypal.me/lan/150000VND");
  await expect(pay).toHaveAttribute("rel", "noopener noreferrer");
  await expect(page.getByRole("img", { name: "QR code to pay ₫150,000" })).toBeVisible();
  await expect(page.getByText("PayPal: paypal.me/lan")).toBeVisible();
  await expect(page.getByRole("button", { name: "Copy the details" })).toBeVisible();
  await expect(page.getByText("The amount, ₫150,000, is filled in for you.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Open in Split Vibe" })).toHaveAttribute(
    "href",
    `intent://${HOST}/r/#${asked.token}#Intent;scheme=https;package=com.dantech.splitvibe;${FALLBACK}`,
  );
  const report = /^mailto:abuse@example\.com\?subject=Report%20abuse%3A%20Split%20Vibe%20payment%20request%20link$/;
  await expect(page.getByRole("contentinfo").getByRole("link", { name: "Report abuse" })).toHaveAttribute("href", report);
  // How to pay says first who wrote it, that Split Vibe does not check it, and where to report abuse.
  const notice = page.locator(".payment > p:first-child");
  await expect(notice).toHaveText("The person who sent this link wrote these payment details. Split Vibe does not check them. Report abuse");
  await expect(notice.getByRole("link", { name: "Report abuse" })).toHaveAttribute("href", report);

  // Away from Android, the button goes to Google Play, without the token.
  const iphone = await browser.newContext({ ...devices["iPhone 15"], baseURL: ORIGIN });
  const elsewhere = await iphone.newPage();
  await elsewhere.goto(`/r/#${asked.token}`);
  await expect(elsewhere.getByRole("link", { name: "Get Split Vibe on Google Play" })).toHaveAttribute("href", PLAY);
  await iphone.close();

  // Once the creditor withdraws it, the link no longer works.
  await lan.deleteRequest(asked.id);
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("This link no longer works");
  await expect(page.getByText("If you kept it in your requests in Split Vibe, it is still there.")).toBeVisible();
  expect(violations).toEqual([]);
});

test("a group invite shows the group and how to get the app to join", async ({ page, request }) => {
  const lan = new Account(request, "e2e-group-owner");
  const group = await lan.makeGroup("Chiang Mai trip", { code: "THB", fractionDigits: 2 });
  const token = await lan.makeInvite(group);
  const violations = await watchForCspViolations(page, ORIGIN);
  const answer = await page.goto(`/g/#${token}`);
  expectPageHeaders(answer?.headers() ?? {}, HOST);

  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Chiang Mai trip");
  await expect(page.getByText("Balances in THB")).toBeVisible();
  await expect(page.getByRole("heading", { name: "How to join" })).toBeVisible();
  await expect(page.getByRole("listitem").first()).toHaveText("Get Split Vibe from Google Play on your Android phone.");
  await expect(page.getByRole("link", { name: "Open in Split Vibe" })).toHaveAttribute(
    "href",
    `intent://${HOST}/g/#${token}#Intent;scheme=https;package=com.dantech.splitvibe;${FALLBACK}`,
  );

  // Once the owner turns invites off, the link no longer works.
  await lan.revokeInvites(group);
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("This link no longer works");
  await expect(page.getByText("Ask the group for a new one.")).toBeVisible();
  expect(violations).toEqual([]);
});

test("the page's scripts, styles and fonts are served from /assets/ to be cached for good", async ({ page }) => {
  const assets: Array<{ url: string; cache: string | undefined; type: string | undefined }> = [];
  page.on("response", (response) => {
    const headers = response.headers();
    if (new URL(response.url()).pathname.startsWith("/assets/")) assets.push({ url: response.url(), cache: headers["cache-control"], type: headers["content-type"] });
  });
  const violations = await watchForCspViolations(page, ORIGIN);
  await page.goto("/g/#AAAAAAAAAAAAAAAAAAAAAA");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("This link does not exist");
  // The faces the page shows have loaded from the Worker, under the page's policy.
  const loaded = await page.evaluate(async () => {
    await document.fonts.ready;
    return [...document.fonts].filter((face) => face.status === "loaded").map((face) => `${face.family.replace(/"/g, "")} ${face.weight}`);
  });
  expect(loaded).toEqual(expect.arrayContaining(["Bricolage Grotesque 800", "Be Vietnam Pro 400"]));
  const paths = assets.map((asset) => new URL(asset.url).pathname);
  expect(paths.filter((path) => /^\/assets\/[^/]+\.js$/.test(path))).toHaveLength(1);
  expect(paths.filter((path) => /^\/assets\/[^/]+\.css$/.test(path))).toHaveLength(1);
  const fonts = assets.filter((asset) => /^\/assets\/[^/]+\.woff2$/.test(new URL(asset.url).pathname));
  expect(fonts.length).toBeGreaterThanOrEqual(2);
  expect(fonts.length).toBeLessThanOrEqual(3);
  expect(paths).toHaveLength(2 + fonts.length);
  for (const font of fonts) expect(font.type).toBe("font/woff2");
  for (const asset of assets) expect(asset.cache).toBe("public, max-age=31536000, immutable");
  expect(violations).toEqual([]);
});
