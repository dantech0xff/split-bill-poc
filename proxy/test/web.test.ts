import { describe, expect, it, vi } from "vitest";
import type { Env } from "../src/index";
import { DELETE_ACCOUNT_PAGE, LEGAL_PAGES, WEB_ASSETS } from "../src/web-assets.generated";
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

/** The headers every page has but its policy and referrer policy. */
function expectPageHeaders(page: Awaited<ReturnType<typeof get>>) {
  expect(page.status).toBe(200);
  expect(page.headers.get("content-type")).toBe("text/html; charset=utf-8");
  expect(page.headers.get("x-robots-tag")).toBe("noindex, nofollow");
  expect(page.headers.get("x-content-type-options")).toBe("nosniff");
  expect(page.headers.get("cache-control")).toBe("no-store");
}

describe("the privacy policy and the terms", () => {
  it("are the pages rendered when the web app was built, which run no script and load nothing from elsewhere", async () => {
    for (const [path, body] of [
      ["/privacy", LEGAL_PAGES.privacy],
      ["/terms", LEGAL_PAGES.terms],
    ] as const) {
      const page = await get(path);

      expectPageHeaders(page);
      expect(page.headers.get("content-security-policy"), path).toBe(
        "default-src 'none'; style-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
      );
      expect(page.headers.get("referrer-policy")).toBe("no-referrer");
      expect(page.body).toBe(body);
      expect(page.body).not.toMatch(/<script\b/i);
    }
    expect(LEGAL_PAGES.privacy).not.toBe("");
    expect(LEGAL_PAGES.terms).not.toBe("");
  });

  it("are served at their paths only, to GET and HEAD", async () => {
    for (const path of ["/privacy/", "/terms/", "/privacy.html", "/terms.html", "/privacy/x"]) {
      expect((await get(path)).status, path).toBe(404);
    }
    for (const method of ["POST", "PUT", "DELETE"]) {
      expect((await send(new Request("https://proxy.test/privacy", { method }))).status, method).toBe(405);
    }
  });
});

describe("the page to delete an account", () => {
  it("loads Google's sign-in only from the origins its guide names, and tells Google its origin alone", async () => {
    const page = await get("/delete-account", {}, "https://links.example.org");

    expectPageHeaders(page);
    expect(page.headers.get("content-security-policy")).toBe(
      [
        "default-src 'none'",
        "script-src 'self' https://accounts.google.com/gsi/client",
        "style-src 'self' https://accounts.google.com/gsi/style",
        "img-src 'self' data:",
        "connect-src 'self' https://accounts.google.com/gsi/",
        "frame-src https://accounts.google.com/gsi/",
        "base-uri 'none'",
        "form-action 'none'",
        "frame-ancestors 'none'",
      ].join("; "),
    );
    expect(page.headers.get("referrer-policy")).toBe("strict-origin-when-cross-origin");
  });

  it("carries the Worker's Google client ID while sign-in works, and nothing otherwise", async () => {
    const filled = await get("/delete-account", { GOOGLE_CLIENT_ID: " test-web-client.apps.googleusercontent.com " });
    const unset = await get("/delete-account", { GOOGLE_CLIENT_ID: "" });
    const signInOff = await get("/delete-account", { SESSION_SIGNING_KEY: "too short" });

    expect(filled.body).toBe(DELETE_ACCOUNT_PAGE.replace('<meta name="sv-google-client-id" content="">', '<meta name="sv-google-client-id" content="test-web-client.apps.googleusercontent.com">'));
    for (const page of [unset, signInOff]) {
      expect(page.status).toBe(200);
      expect(page.body).toBe(DELETE_ACCOUNT_PAGE);
    }
    expect(DELETE_ACCOUNT_PAGE.match(/<meta name="sv-google-client-id" content="">/g)).toHaveLength(1);
  });

  it("leaves the client ID out when it is not of the shape Google gives, and logs it", async () => {
    const logs: unknown[] = [];
    const spied = vi.spyOn(console, "error").mockImplementation((line: unknown) => {
      logs.push(line);
    });
    const pages = [];
    for (const clientId of ['"><script>alert(1)</script>.apps.googleusercontent.com', "client-id-without-its-domain", "a b.apps.googleusercontent.com"]) {
      pages.push(await get("/delete-account", { GOOGLE_CLIENT_ID: clientId }));
    }
    spied.mockRestore();

    for (const page of pages) {
      expect(page.body).toBe(DELETE_ACCOUNT_PAGE);
      expect(page.body).not.toContain("alert(1)");
    }
    expect(logs).toEqual(pages.map(() => JSON.stringify({ event: "bad_setting", name: "GOOGLE_CLIENT_ID" })));
  });

  it("is served at its path only, to GET and HEAD", async () => {
    for (const path of ["/delete-account/", "/delete-account.html", "/delete"]) {
      expect((await get(path)).status, path).toBe(404);
    }
    expect((await send(new Request("https://proxy.test/delete-account", { method: "POST" }))).status).toBe(405);
  });
});

describe("every page", () => {
  it("answers HEAD as it answers GET, with the same status and headers but no body, as link checkers and uptime monitors ask", async () => {
    for (const path of ["/s/", "/r/", "/g/", "/privacy", "/terms", "/delete-account"]) {
      const got = await get(path);
      const head = await send(new Request(`https://proxy.test${path}`, { method: "HEAD" }));

      expect(head.status, path).toBe(200);
      expect([...head.headers], path).toEqual([...got.headers]);
      expect(head.body, path).toBe("");
      expect(got.body, path).not.toBe("");
    }
    for (const path of ["/privacy/", "/s/AAAAAAAAAAAAAAAAAAAAAA"]) {
      expect((await send(new Request(`https://proxy.test${path}`, { method: "HEAD" }))).status, path).toBe(404);
    }
  });
});

describe("the page's scripts and styles", () => {
  it("are served under /assets/ to be cached for a year, every one the pages refer to", async () => {
    const referred: string[] = [];
    for (const path of ["/s/", "/delete-account", "/privacy", "/terms"]) {
      const page = await get(path);
      referred.push(...[...page.body.matchAll(/(?:src|href)="\/assets\/([^"]+)"/g)].map((match) => match[1]));
    }

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
