import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiResult } from "../src/api";
import { type InvitePreview, type RequestView, parseInviteAnswer, parseRequestAnswer } from "../src/contract";
import { InvitePageView } from "../src/pages/invite-page";
import { RequestPageView } from "../src/pages/request-page";
import invite from "./fixtures/invite.json";
import requestConfirmed from "./fixtures/request-confirmed.json";
import requestOpen from "./fixtures/request-open.json";
import requestReported from "./fixtures/request-reported.json";
import { mount, stubCanvas, textOf } from "./render";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";

beforeEach(() => {
  stubCanvas();
});

const ANDROID = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/142.0 Mobile Safari/537.36";
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";

function request(answer: unknown): ApiResult<RequestView> {
  const value = parseRequestAnswer(answer);
  if (value === null) throw new Error("The fixture does not parse");
  return { ok: true, value };
}

function group(answer: unknown): ApiResult<InvitePreview> {
  const value = parseInviteAnswer(answer);
  if (value === null) throw new Error("The fixture does not parse");
  return { ok: true, value };
}

const requestLink = { host: "proxy.example.workers.dev", path: "/r/", token: TOKEN };
const inviteLink = { host: "proxy.example.workers.dev", path: "/g/", token: TOKEN };

describe("the payment request's page", () => {
  it("shows who asks whom, how much, what for, and how to pay", () => {
    const page = mount(<RequestPageView loaded={{ state: "done", result: request(requestOpen) }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(page.querySelector(".hero"))).toBe("Lan asks Minh for ₫150,000 For Hot pot Not paid yet");
    const pay = page.querySelector<HTMLAnchorElement>(".payment a");
    expect(textOf(pay)).toBe("Pay with a UPI app");
    expect(pay?.getAttribute("href")).toBe("upi://pay?pa=lan@okbank&pn=Lan&am=1500.00&cu=INR");
    expect(pay?.rel).toBe("noopener noreferrer");
    expect(page.querySelector('canvas[role="img"]')?.getAttribute("aria-label")).toBe("QR code to pay ₫150,000");
    expect(textOf(page.querySelector(".details-card__text"))).toBe("UPI: lan@okbank Amount: ₹1,500.00");
    expect(textOf(page)).toContain("The amount, ₫150,000, is filled in for you.");
  });

  it("sends people without Android to Google Play, and opens the app on Android", () => {
    const elsewhere = mount(<RequestPageView loaded={{ state: "done", result: request(requestOpen) }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    const play = Array.from(elsewhere.querySelectorAll("a")).find((link) => textOf(link) === "Get Split Vibe on Google Play");
    expect(play?.getAttribute("href")).toBe("https://play.google.com/store/apps/details?id=com.dantech.splitvibe");
    const android = mount(<RequestPageView loaded={{ state: "done", result: request(requestOpen) }} link={requestLink} userAgent={ANDROID} onRetry={vi.fn()} />);
    const open = Array.from(android.querySelectorAll("a")).find((link) => textOf(link) === "Open in Split Vibe");
    expect(open?.getAttribute("href")).toBe(
      `intent://proxy.example.workers.dev/r/#${TOKEN}#Intent;scheme=https;package=com.dantech.splitvibe;` +
        "S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dcom.dantech.splitvibe;end",
    );
  });

  it("says when the payer reported it paid, and how to pay without details", () => {
    const page = mount(<RequestPageView loaded={{ state: "done", result: request(requestReported) }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(page.querySelector(".hero"))).toBe("Lan asks Minh for $42.50 Minh says it is paid. Lan has yet to confirm it.");
    expect(textOf(page)).toContain("Ask Lan how to pay.");
    expect(page.querySelector(".payment")).toBeNull();
  });

  it("offers no way to pay once the payment is confirmed", () => {
    const page = mount(<RequestPageView loaded={{ state: "done", result: request(requestConfirmed) }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(page.querySelector(".hero__status"))).toBe("Paid and confirmed");
    expect(page.querySelector(".payment")).toBeNull();
    expect(textOf(page)).toContain("Nothing is left to pay on this request.");
  });

  it("shows no payment link the page may not open", () => {
    const answer = structuredClone(requestOpen);
    answer.request.payment.link = "javascript:alert(document.domain)";
    const page = mount(<RequestPageView loaded={{ state: "done", result: request(answer) }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(page.querySelector(".payment a")).toBeNull();
    expect(Array.from(page.querySelectorAll("a")).some((link) => (link.getAttribute("href") ?? "").startsWith("javascript"))).toBe(false);
    expect(textOf(page.querySelector(".details-card__text"))).toContain("UPI: lan@okbank");
  });

  it("says a missing link does not exist, a withdrawn one no longer works, and waits while loading", () => {
    const missing = mount(<RequestPageView loaded={{ state: "done", result: { ok: false, error: { kind: "not_found" } } }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(missing.querySelector("h1"))).toBe("This link does not exist");
    const gone = mount(<RequestPageView loaded={{ state: "done", result: { ok: false, error: { kind: "gone" } } }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(gone.querySelector("h1"))).toBe("This link no longer works");
    expect(textOf(gone)).toContain("This payment request was withdrawn.");
    const loading = mount(<RequestPageView loaded={{ state: "loading" }} link={requestLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(loading.querySelector("h1"))).toBe("Opening the payment request…");
  });

  it("offers to try again after too many requests", () => {
    const retry = vi.fn();
    const page = mount(
      <RequestPageView loaded={{ state: "done", result: { ok: false, error: { kind: "rate_limited", retryAfterSeconds: 120 } } }} link={requestLink} userAgent={IPHONE} onRetry={retry} />,
    );
    expect(textOf(page)).toContain("Try again in 2 minutes.");
    page.querySelector("button")?.click();
    expect(retry).toHaveBeenCalled();
  });
});

describe("the group invite's page", () => {
  it("shows the group's name and currency, and how to get the app to join", () => {
    const page = mount(<InvitePageView loaded={{ state: "done", result: group(invite) }} link={inviteLink} userAgent={ANDROID} onRetry={vi.fn()} />);
    expect(textOf(page.querySelector("h1"))).toBe("Chiang Mai trip");
    expect(textOf(page.querySelector(".hero"))).toBe("You are invited to join Chiang Mai trip Balances in THB");
    expect(textOf(page.querySelector(".steps"))).toContain("Get Split Vibe from Google Play on your Android phone.");
    const open = Array.from(page.querySelectorAll("a")).find((link) => textOf(link) === "Open in Split Vibe");
    expect(open?.getAttribute("href")).toMatch(new RegExp(`^intent://proxy\\.example\\.workers\\.dev/g/#${TOKEN}#Intent;`));
  });

  it("says an invite that was turned off no longer works", () => {
    const page = mount(<InvitePageView loaded={{ state: "done", result: { ok: false, error: { kind: "gone" } } }} link={inviteLink} userAgent={IPHONE} onRetry={vi.fn()} />);
    expect(textOf(page.querySelector("h1"))).toBe("This link no longer works");
    expect(textOf(page)).toContain("Ask the group for a new one.");
  });
});
