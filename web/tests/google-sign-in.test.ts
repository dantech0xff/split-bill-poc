import { describe, expect, it, vi } from "vitest";
import type { GoogleIdApi } from "../src/google-sign-in";

async function freshModule(): Promise<typeof import("../src/google-sign-in")> {
  vi.resetModules();
  return import("../src/google-sign-in");
}

/** A document whose head keeps the scripts added to it without loading them, so no test reaches Google, and a window the test gives Google's library to. */
function page(): { doc: Document; view: Window; scripts: HTMLScriptElement[] } {
  const scripts: HTMLScriptElement[] = [];
  const doc = {
    createElement: (tag: string) => document.createElement(tag),
    head: {
      appendChild: (script: HTMLScriptElement) => {
        scripts.push(script);
        return script;
      },
    },
  } as unknown as Document;
  return { doc, view: {} as Window, scripts };
}

/** An ID token whose payload holds [claims], unsigned, as only its payload is read here. */
function tokenWith(claims: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(claims));
  const payload = btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `eyJhbGciOiJSUzI1NiJ9.${payload}.signature`;
}

describe("loadGoogleSignIn", () => {
  it("adds Google's library once, only when asked, and gives its google.accounts.id", async () => {
    const { loadGoogleSignIn, GOOGLE_SIGN_IN_SCRIPT } = await freshModule();
    const { doc, view, scripts } = page();
    expect(scripts).toHaveLength(0);
    const first = loadGoogleSignIn(doc, view);
    const second = loadGoogleSignIn(doc, view);
    expect(second).toBe(first);
    expect(GOOGLE_SIGN_IN_SCRIPT).toBe("https://accounts.google.com/gsi/client");
    expect(scripts.map((script) => script.src)).toEqual(["https://accounts.google.com/gsi/client"]);
    const api: GoogleIdApi = { initialize: vi.fn(), renderButton: vi.fn() };
    view.google = { accounts: { id: api } };
    scripts[0]?.dispatchEvent(new Event("load"));
    await expect(first).resolves.toBe(api);
    await expect(loadGoogleSignIn(doc, view)).resolves.toBe(api);
    expect(scripts).toHaveLength(1);
  });

  it("fails when the library did not start or could not be loaded, and can be tried again", async () => {
    const { loadGoogleSignIn } = await freshModule();
    const { doc, view, scripts } = page();
    const silent = loadGoogleSignIn(doc, view);
    scripts[0]?.dispatchEvent(new Event("load"));
    await expect(silent).rejects.toThrow("did not start");
    const failed = loadGoogleSignIn(doc, view);
    scripts[1]?.dispatchEvent(new Event("error"));
    await expect(failed).rejects.toThrow("could not be loaded");
    void loadGoogleSignIn(doc, view).catch(() => undefined);
    expect(scripts).toHaveLength(3);
  });
});

describe("emailOf", () => {
  it("reads the address an ID token names, written in any script", async () => {
    const { emailOf } = await freshModule();
    expect(emailOf(tokenWith({ sub: "1", email: "person@example.com" }))).toBe("person@example.com");
    expect(emailOf(tokenWith({ sub: "1", email: "nguyễn.văn.a@example.vn" }))).toBe("nguyễn.văn.a@example.vn");
  });

  it("is null for a token that names no address, or is no token", async () => {
    const { emailOf } = await freshModule();
    for (const token of [tokenWith({ sub: "1" }), tokenWith({ email: 42 }), tokenWith({ email: "" }), tokenWith([1]), "not-a-token", "a.%%%.b", "", "a..b"]) {
      expect(emailOf(token), token).toBeNull();
    }
  });
});
