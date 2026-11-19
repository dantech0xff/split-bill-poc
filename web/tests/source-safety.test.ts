import { describe, expect, it } from "vitest";

/** Every source file of the page, as text. */
const sources = import.meta.glob<string>("../src/**/*.{ts,tsx,css}", { query: "?raw", import: "default", eager: true });

function filesMatching(pattern: RegExp): string[] {
  return Object.entries(sources)
    .filter(([, text]) => pattern.test(text))
    .map(([path]) => path);
}

describe("the page's source", () => {
  it("is all there", () => {
    expect(Object.keys(sources).length).toBeGreaterThan(10);
  });

  it("never builds HTML from text", () => {
    expect(filesMatching(/dangerouslySetInnerHTML|innerHTML|outerHTML|insertAdjacentHTML|document\.write|createContextualFragment/)).toEqual([]);
  });

  it("never runs text as code", () => {
    expect(filesMatching(/\beval\s*\(|new\s+Function\s*\(|setTimeout\(\s*["'`]/)).toEqual([]);
  });

  it("never sets a style attribute, which the CSP refuses", () => {
    expect(filesMatching(/\bstyle\s*=\s*["'{]|setAttribute\(\s*["']style["']/)).toEqual([]);
  });

  it("logs nothing, so no token can end up in a log", () => {
    expect(filesMatching(/\bconsole\./)).toEqual([]);
  });

  it("keeps localStorage to the claim store", () => {
    expect(filesMatching(/localStorage/)).toEqual(["../src/claim-store.ts"]);
  });

  it("loads nothing from another origin but Turnstile and Google's sign-in, and links to Google Play", () => {
    const origins = new Set(
      Object.values(sources).flatMap((text) => [...text.matchAll(/https:\/\/([a-z0-9.-]+)/gi)].map((match) => match[1]?.toLowerCase())),
    );
    expect([...origins].sort()).toEqual(["accounts.google.com", "challenges.cloudflare.com", "play.google.com"]);
    expect(filesMatching(/accounts\.google\.com/)).toEqual(["../src/google-sign-in.ts"]);
  });
});
