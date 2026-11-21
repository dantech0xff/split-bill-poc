import { readFileSync } from "node:fs";
import { type Browser, type BrowserContext, type Page, type WebSocket, devices, expect, test } from "@playwright/test";
import { Account, expectPageHeaders, watchForCspViolations } from "./local-proxy";

const ORIGIN = "http://127.0.0.1:8797";
const HOST = "127.0.0.1:8797";
const LIVE_BUDGET_MS = 2_000;
/** The page's "Report abuse" link, with the address the local Worker is given. */
const REPORT_BILL_LINK = "mailto:abuse@example.com?subject=Report%20abuse%3A%20Split%20Vibe%20bill%20link";
/** What the page says beside every way to pay: the person who sent the link wrote it. */
const USER_WRITTEN = "The person who sent this link wrote these payment details. Split Vibe does not check them. Report abuse";
/** The random id the page sends with every try of its first claim. */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

/** The bill of the unit tests' fixture: Pho 24, three items, a service charge, three people. */
const BILL: unknown = JSON.parse(readFileSync(new URL("../tests/fixtures/share-open.json", import.meta.url), "utf8")).share.bill;

const AMOUNTS = {
  me: { amountMinor: 77000, payment: null },
  p1: {
    amountMinor: 86500,
    payment: {
      link: "https://paypal.me/splitvibedemo/86500VND",
      qrPayload: "https://paypal.me/splitvibedemo/86500VND",
      details: "PayPal: paypal.me/splitvibedemo\nAmount: ₫86,500",
      amountFilledIn: true,
    },
  },
  p2: {
    amountMinor: 67500,
    payment: {
      link: null,
      qrPayload: "00020101021238540010A00000072701240006970416011000000000002080208QRIBFTTA53037045405675005802VN62070803Pho6304B1C2",
      details: "Bank: ACB (970416)\nAccount: 000000000\nName: LAN NGUYEN\nAmount: ₫67,500",
      amountFilledIn: false,
    },
  },
};

interface Phone {
  context: BrowserContext;
  page: Page;
  violations: string[];
  requests: string[];
  sockets: WebSocket[];
}

/** A phone at the table: its own browser, which records what it sends and what its CSP blocks. */
async function phone(browser: Browser): Promise<Phone> {
  const context = await browser.newContext({ ...devices["Pixel 7"], baseURL: ORIGIN });
  const page = await context.newPage();
  const violations = await watchForCspViolations(page, ORIGIN);
  const requests: string[] = [];
  const sockets: WebSocket[] = [];
  page.on("request", (request) => requests.push(request.url()));
  page.on("websocket", (socket) => sockets.push(socket));
  return { context, page, violations, requests, sockets };
}

/** The toggle of the item whose name is [item], for a browser that has picks open. */
function itemButton(page: Page, item: string) {
  return page.getByRole("button", { name: new RegExp(`^(\\d+ × )?${item} `) });
}

/**
 * Waits, checking at every frame, until [page] shows [item] as picked by [name], and answers
 * how long that took from [since].
 */
async function msUntilPicked(page: Page, item: string, name: string, since: number): Promise<number> {
  await page.waitForFunction(
    ({ itemName, claimer }) =>
      Array.from(document.querySelectorAll(".item")).some(
        (row) => row.querySelector(".item__name")?.textContent?.includes(itemName) && row.textContent?.includes(`picked by ${claimer}`),
      ),
    { itemName: item, claimer: name },
    { polling: "raf", timeout: LIVE_BUDGET_MS * 2 },
  );
  return Date.now() - since;
}

/**
 * Taps [item] on a page whose browser has no claim yet, which passes Turnstile first, and answers
 * when the proxy's answer came and how long it took from the tap.
 */
async function firstClaim(page: Page, token: string, item: string): Promise<{ answeredAt: number; tookMs: number }> {
  const tappedAt = Date.now();
  const answer = page.waitForResponse((response) => response.url().endsWith("/v1/shares/claim"));
  await itemButton(page, item).click();
  const claim = await answer;
  const answeredAt = Date.now();
  expect(claim.status()).toBe(200);
  const body: unknown = claim.request().postDataJSON();
  expect(body).toMatchObject({ token, name: expect.any(String), itemIds: expect.any(Array), turnstile: expect.any(String), attempt: expect.stringMatching(UUID) });
  expect(body).not.toHaveProperty("key");
  return { answeredAt, tookMs: answeredAt - tappedAt };
}

test("everyone at the table picks live, then sees what to pay and how, until the link is revoked", async ({ browser, request }) => {
  const lan = new Account(request, "e2e-bill-owner");
  const link = await lan.makeShare(BILL);
  const minh = await phone(browser);
  const an = await phone(browser);

  const answer = await minh.page.goto(`/s/#${link.token}`);
  expect(answer).not.toBeNull();
  expectPageHeaders(answer?.headers() ?? {}, HOST);
  // The amounts below are written as a browser set to the United States writes them.
  expect(await minh.page.evaluate(() => navigator.languages)).toEqual(["en-US"]);
  await an.page.goto(`/s/#${link.token}`);
  for (const { page } of [minh, an]) {
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Pho 24");
    await expect(page.locator(".hero__amount")).toHaveText("₫231,000");
    await expect(page.getByRole("contentinfo").getByRole("link", { name: "Report abuse" })).toHaveAttribute("href", REPORT_BILL_LINK);
  }
  for (const { sockets } of [minh, an]) {
    expect(sockets).toHaveLength(1);
    expect(sockets[0]?.url()).toBe(`ws://${HOST}/v1/shares/live`);
    expect(sockets[0]?.isClosed()).toBe(false);
  }

  // Minh's first claim passes Turnstile; An sees it live once the proxy holds it.
  await minh.page.getByLabel("Your name").fill("Minh");
  const minhFirst = await firstClaim(minh.page, link.token, "Spring rolls");
  const firstDelay = await msUntilPicked(an.page, "Spring rolls", "Minh", minhFirst.answeredAt);
  await expect(itemButton(minh.page, "Spring rolls")).toHaveAttribute("aria-pressed", "true");

  // A later pick needs only the key; An sees it within the budget from the tap itself.
  const tappedAt = Date.now();
  const keyed = minh.page.waitForRequest((sent) => sent.url().endsWith("/v1/shares/claim"));
  await itemButton(minh.page, "Iced coffee").click();
  const keyedBody: unknown = (await keyed).postDataJSON();
  expect(keyedBody).toMatchObject({ token: link.token, key: expect.stringMatching(/^[A-Za-z0-9_-]{22}$/), name: "Minh", itemIds: ["i2", "i3"] });
  expect(keyedBody).not.toHaveProperty("turnstile");
  expect(keyedBody).not.toHaveProperty("attempt");
  const secondDelay = await msUntilPicked(an.page, "Iced coffee", "Minh", tappedAt);

  // An picks too, and Minh sees it.
  await an.page.getByLabel("Your name").fill("An");
  const anFirst = await firstClaim(an.page, link.token, "Pho bo");
  const thirdDelay = await msUntilPicked(minh.page, "Pho bo", "An", anFirst.answeredAt);

  const report = `Minh's first claim ${firstDelay} ms after its answer, Minh's next tap ${secondDelay} ms after the tap, An's first claim ${thirdDelay} ms after its answer; the first claims took ${minhFirst.tookMs} ms and ${anFirst.tookMs} ms from the tap, Turnstile included`;
  test.info().annotations.push({ type: "live updates", description: report });
  console.log(`Live updates: ${report}.`);
  for (const delay of [firstDelay, secondDelay, thirdDelay]) expect(delay).toBeLessThan(LIVE_BUDGET_MS);

  // The key is kept under a hash of the token, never the token itself.
  const stored = await minh.page.evaluate(() => Object.entries(localStorage));
  expect(stored).toHaveLength(1);
  expect(stored[0]?.[0]).toMatch(/^sv\.claim\.[0-9a-f]{64}$/);
  expect(JSON.stringify(stored)).not.toContain(link.token);

  // The owner locks: picks freeze on both pages.
  const claims = await lan.lockShare(link.id);
  for (const { page } of [minh, an]) {
    await expect(page.locator(".banner")).toHaveText("Picks are closed: Lan is settling the split.");
    await expect(page.locator("button[aria-pressed]")).toHaveCount(0);
    await expect(page.getByLabel("Your name")).toHaveCount(0);
  }

  // The owner, who paid the bill, settles the split and matches each claim to the person it is on
  // the bill: both pages show each amount, the QR codes and the link, say who each claim is, and
  // mark the amount of their own.
  const claimIdOf = (name: string): string => claims.find((claim) => claim.name === name)?.id ?? "";
  await lan.finalizeShare(link.id, BILL, AMOUNTS, "me", { [claimIdOf("Minh")]: "p1", [claimIdOf("An")]: "p2" });
  const seen = [
    {
      phoneAtTable: minh,
      own: /^Minh\s*You$/,
      amounts: [/Lan\s*Paid the bill\s*₫77,000/, /Minh\s*You\s*₫86,500/, /An\s*₫67,500/],
      claimers: [/^Minh\s*You\s*On the bill as Minh$/, /^An\s*On the bill as An$/],
    },
    {
      phoneAtTable: an,
      own: /^An\s*You$/,
      amounts: [/Lan\s*Paid the bill\s*₫77,000/, /Minh\s*₫86,500/, /An\s*You\s*₫67,500/],
      claimers: [/^Minh\s*On the bill as Minh$/, /^An\s*You\s*On the bill as An$/],
    },
  ];
  for (const { phoneAtTable, own, amounts, claimers } of seen) {
    const { page } = phoneAtTable;
    await expect(page.getByRole("heading", { name: "What each person pays" })).toBeVisible();
    await expect(page.locator(".person__row")).toHaveText(amounts);
    await expect(page.locator(".person--you .person__label")).toHaveText(own);
    await expect(page.getByRole("heading", { name: "Who picked" })).toBeVisible();
    await expect(page.locator(".claimer")).toHaveText(claimers);
    await page.locator("summary", { hasText: "Minh" }).click();
    await page.locator("summary", { hasText: "An" }).click();
    const pay = page.getByRole("link", { name: "Pay on paypal.me" });
    await expect(pay).toHaveAttribute("href", "https://paypal.me/splitvibedemo/86500VND");
    await expect(pay).toHaveAttribute("target", "_blank");
    await expect(pay).toHaveAttribute("rel", "noopener noreferrer");
    for (const amount of ["₫86,500", "₫67,500"]) {
      const qr = page.getByRole("img", { name: `QR code to pay ${amount}` });
      await expect(qr).toBeVisible();
      const drawn = await qr.evaluate((canvas: HTMLCanvasElement) => {
        const pixels = canvas.getContext("2d")?.getImageData(0, 0, canvas.width, canvas.height).data ?? new Uint8ClampedArray();
        let dark = 0;
        for (let index = 0; index < pixels.length; index += 4) if ((pixels[index] ?? 255) < 128) dark += 1;
        return { modules: canvas.width - 8, dark };
      });
      expect(drawn.modules).toBeGreaterThanOrEqual(21);
      expect(drawn.dark).toBeGreaterThan(0);
    }
    await expect(page.getByText("Account: 000000000")).toBeVisible();
    await expect(page.getByText("Type in the amount yourself: ₫67,500.")).toBeVisible();
    // Each way to pay says who wrote it, that Split Vibe does not check it, and where to report abuse.
    const notices = page.locator(".payment > p:first-child");
    await expect(notices).toHaveText([USER_WRITTEN, USER_WRITTEN]);
    for (const notice of await notices.all()) await expect(notice.getByRole("link", { name: "Report abuse" })).toHaveAttribute("href", REPORT_BILL_LINK);
  }

  // The owner revokes the link: both pages say it no longer works.
  await lan.revokeShare(link.id);
  for (const { page } of [minh, an]) {
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("This link no longer works");
  }

  for (const phoneAtTable of [minh, an]) {
    expect(phoneAtTable.violations).toEqual([]);
    for (const url of phoneAtTable.requests) {
      const { pathname, search } = new URL(url);
      expect(`${pathname}${search}`).not.toContain(link.token);
    }
    await phoneAtTable.context.close();
  }
});

test("a bill link that was never made does not exist", async ({ page }) => {
  const violations = await watchForCspViolations(page, ORIGIN);
  await page.goto("/s/#AAAAAAAAAAAAAAAAAAAAAA");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("This link does not exist");
  expect(violations).toEqual([]);
});
