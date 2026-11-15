import { describe, expect, it, vi } from "vitest";
import type { Env } from "../src/index";
import { WEB_ASSETS } from "../src/web-assets.generated";
import { send } from "./requests";

// These hold for the web app built from web/ and for the placeholder the proxy embeds without it,
// so they read nothing of what the page says.

/** A plain GET, as a browser sends it: no session, no install id, no version. */
function get(path: string, settings: Partial<Env> = {}, origin = "https://proxy.test") {
  return send(new Request(`${origin}${path}`), settings);
}

function policyFor(host: string): string {
  return [
    "default-src 'none'",
    "script-src 'self' https://challenges.cloudflare.com",
    "style-src 'self'",
    "img-src 'self' data:",
    `connect-src 'self' wss://${host}`,
    "frame-src https://challenges.cloudflare.com",
    "base-uri 'none'",
    "form-action 'none'",
    "frame-ancestors 'none'",
  ].join("; ");
}

describe("the web page", () => {
  it("is the same for /s/, /r/ and /g/, and is never cached, indexed, framed or referred from", async () => {
    const pages = await Promise.all(["/s/", "/r/", "/g/"].map((path) => get(path)));

    for (const page of pages) {
      expect(page.status).toBe(200);
      expect(page.headers.get("content-type")).toBe("text/html; charset=utf-8");
      expect(page.headers.get("content-security-policy")).toBe(policyFor("proxy.test"));
      expect(page.headers.get("referrer-policy")).toBe("no-referrer");
      expect(page.headers.get("x-robots-tag")).toBe("noindex, nofollow");
      expect(page.headers.get("x-content-type-options")).toBe("nosniff");
      expect(page.headers.get("cache-control")).toBe("no-store");
      expect(page.body).toBe(pages[0].body);
    }
  });

  it("lets the page connect live to the host it was served from", async () => {
    const page = await get("/s/", {}, "https://links.example.org");

    expect(page.headers.get("content-security-policy")).toBe(policyFor("links.example.org"));
  });

  it("carries the Turnstile site key and the abuse address of the Worker's variables", async () => {
    const filled = await get("/s/", { TURNSTILE_SITE_KEY: " 0x4AAAAAAAB1c2D3e4F5g6H7 ", ABUSE_EMAIL: "abuse@split-vibe.example" });
    const empty = await get("/r/", { TURNSTILE_SITE_KEY: "", ABUSE_EMAIL: "" });

    expect(filled.body).toContain('<meta name="sv-turnstile-site-key" content="0x4AAAAAAAB1c2D3e4F5g6H7">');
    expect(filled.body).toContain('<meta name="sv-abuse-email" content="abuse@split-vibe.example">');
    expect(empty.body).toContain('<meta name="sv-turnstile-site-key" content="">');
    expect(empty.body).toContain('<meta name="sv-abuse-email" content="">');
    for (const body of [filled.body, empty.body]) {
      expect(body.match(/<meta name="sv-turnstile-site-key"/g)).toHaveLength(1);
      expect(body.match(/<meta name="sv-abuse-email"/g)).toHaveLength(1);
    }
  });

  it("leaves a meta tag empty when its variable is not of its shape, so that nothing gets into the page", async () => {
    const page = await get("/g/", { TURNSTILE_SITE_KEY: '"><script>alert(1)</script>', ABUSE_EMAIL: "<img src=x onerror=alert(1)>@x" });

    expect(page.status).toBe(200);
    expect(page.body).toContain('<meta name="sv-turnstile-site-key" content="">');
    expect(page.body).toContain('<meta name="sv-abuse-email" content="">');
    expect(page.body).not.toContain("alert(1)");
  });

  it("takes an abuse address by the rule the page applies, and logs one it leaves out", async () => {
    const taken = ["abuse@example.com", "a.b_c%d+e-f@mail.example.co.uk", `${"a".repeat(242)}@example.com`];
    const refused = [
      `o'brien&"co"@example.com`,
      "abuse@localhost",
      "abuse@example.com?cc=someone@example.com",
      "abuse@example.com&body=x",
      "abuse @example.com",
      "abuse@@example.com",
      "abuse@example..com",
      `${"a".repeat(243)}@example.com`,
    ];
    const logs: unknown[] = [];
    const spied = vi.spyOn(console, "error").mockImplementation((line: unknown) => {
      logs.push(line);
    });

    for (const address of taken) {
      expect((await get("/r/", { ABUSE_EMAIL: address })).body, address).toContain(`<meta name="sv-abuse-email" content="${address}">`);
    }
    expect(logs).toEqual([]);
    for (const address of refused) {
      expect((await get("/r/", { ABUSE_EMAIL: address })).body, address).toContain('<meta name="sv-abuse-email" content="">');
    }
    spied.mockRestore();

    expect(logs).toEqual(refused.map(() => JSON.stringify({ event: "bad_setting", name: "ABUSE_EMAIL" })));
  });

  it("is served at /s/, /r/ and /g/ only, never with a token in the path", async () => {
    for (const path of ["/s", "/r", "/g", "/s/AAAAAAAAAAAAAAAAAAAAAA", "/r/AAAAAAAAAAAAAAAAAAAAAA", "/g/AAAAAAAAAAAAAAAAAAAAAA", "/s/x/y", "/index.html"]) {
      expect((await get(path)).status, path).toBe(404);
    }
    for (const method of ["POST", "PUT", "DELETE"]) {
      expect((await send(new Request("https://proxy.test/s/", { method }))).status, method).toBe(405);
    }
  });
});

describe("the page's scripts and styles", () => {
  it("are served under /assets/ to be cached for a year, every one the page refers to", async () => {
    const page = await get("/s/");
    const referred = [...page.body.matchAll(/(?:src|href)="\/assets\/([^"]+)"/g)].map((match) => match[1]);

    expect(WEB_ASSETS.size).toBeGreaterThan(0);
    expect(referred.length).toBeGreaterThan(0);
    for (const name of referred) expect(WEB_ASSETS.has(name), name).toBe(true);
    for (const [name, asset] of WEB_ASSETS) {
      const served = await get(`/assets/${name}`);
      expect(served.status, name).toBe(200);
      expect(served.headers.get("content-type")).toBe(asset.type);
      expect(served.headers.get("content-type")).toMatch(/^text\/(?:javascript|css); charset=utf-8$/);
      expect(served.headers.get("cache-control")).toBe("public, max-age=31536000, immutable");
      expect(served.headers.get("x-content-type-options")).toBe("nosniff");
      expect(served.body).toBe(asset.body);
    }
  });

  it("are all there is under /assets/: any other name is 404", async () => {
    for (const path of ["/assets/", "/assets/nothing-here.js", "/assets/index.ts", "/assets/a/b.js", "/assets/..%2Fsrc%2Findex.ts", "/assets/constructor"]) {
      const sent = await get(path);
      expect(sent.status, path).toBe(404);
    }
  });
});
