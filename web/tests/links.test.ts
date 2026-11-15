import { describe, expect, it } from "vitest";
import { PLAY_STORE_URL, abuseMailto, isAndroid, openInAppHref, safePaymentLink } from "../src/links";

describe("safePaymentLink", () => {
  it("takes an https link with a host, as the browser parses it", () => {
    expect(safePaymentLink("https://paypal.me/lan/71500VND")).toEqual({
      kind: "web",
      href: "https://paypal.me/lan/71500VND",
      host: "paypal.me",
    });
    expect(safePaymentLink("HTTPS://Example.COM/pay")).toEqual({ kind: "web", href: "https://example.com/pay", host: "example.com" });
  });

  it("takes a UPI payment link", () => {
    const link = "upi://pay?pa=lan@okbank&pn=Lan&am=1500.00&cu=INR";
    expect(safePaymentLink(link)).toEqual({ kind: "upi", href: link });
  });

  it.each([
    ["javascript:", "javascript:alert(document.domain)"],
    ["javascript: in capitals", "JavaScript:alert(1)"],
    ["javascript: after a space", " javascript:alert(1)"],
    ["http:", "http://paypal.me/lan"],
    ["intent:", "intent://pay#Intent;scheme=upi;end"],
    ["data:", "data:text/html,<script>alert(1)</script>"],
    ["https: without a host", "https://"],
    ["https: without slashes or host", "https:"],
    ["https: with an empty authority", "https:///"],
    ["a UPI link that is not a payment", "upi://mandate?pa=lan@okbank"],
    ["a UPI link in capitals", "UPI://pay?pa=lan@okbank"],
    ["a relative path", "/v1/shares/open"],
    ["a control character", "https://paypal.me/\nlan"],
    ["an empty text", ""],
    ["no link", null],
  ])("refuses %s", (_label, link) => {
    expect(safePaymentLink(link)).toBeNull();
  });

  it("refuses a link longer than the proxy allows", () => {
    expect(safePaymentLink(`https://paypal.me/${"a".repeat(2048)}`)).toBeNull();
  });
});

describe("openInAppHref", () => {
  const link = { host: "proxy.example.workers.dev", path: "/r/", token: "AbCdEfGhIjKlMnOpQrStUv" };

  it("opens the same link in the app on Android, with Google Play as the fallback", () => {
    expect(openInAppHref(link, true)).toBe(
      "intent://proxy.example.workers.dev/r/#AbCdEfGhIjKlMnOpQrStUv#Intent;scheme=https;package=com.dantech.splitvibe;" +
        "S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dcom.dantech.splitvibe;end",
    );
  });

  it("sends anyone else to Google Play, without the token", () => {
    expect(openInAppHref(link, false)).toBe(PLAY_STORE_URL);
    expect(PLAY_STORE_URL).toBe("https://play.google.com/store/apps/details?id=com.dantech.splitvibe");
  });
});

describe("isAndroid", () => {
  it("tells Android from other systems", () => {
    expect(isAndroid("Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/142.0 Mobile Safari/537.36")).toBe(true);
    expect(isAndroid("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")).toBe(false);
    expect(isAndroid("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/142.0 Safari/537.36")).toBe(false);
  });
});

describe("abuseMailto", () => {
  it("names the kind of page only", () => {
    expect(abuseMailto("abuse@example.com", "share")).toBe("mailto:abuse@example.com?subject=Report%20abuse%3A%20Split%20Vibe%20bill%20link");
    expect(abuseMailto("abuse@example.com", "request")).toContain("payment%20request%20link");
    expect(abuseMailto("abuse@example.com", "invite")).toContain("group%20invite%20link");
  });

  it("leaves the link out without a plain address", () => {
    expect(abuseMailto("", "share")).toBeNull();
    expect(abuseMailto("   ", "share")).toBeNull();
    expect(abuseMailto("not an address", "share")).toBeNull();
    expect(abuseMailto("abuse@example.com?cc=someone@example.com", "share")).toBeNull();
    expect(abuseMailto("abuse@example.com&body=x", "share")).toBeNull();
  });
});
