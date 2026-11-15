import { describe, expect, it } from "vitest";
import type { Env } from "../src/index";
import { send } from "./requests";
import { api, newGroup, person, VND } from "./shared";

const FINGERPRINT = "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5";
const OTHER = "a1:b2:c3:d4:e5:f6:07:18:29:3a:4b:5c:6d:7e:8f:90:01:12:23:34:45:56:67:78:89:9a:ab:bc:cd:de:ef:f0";

/** A plain GET, as Android's verifier or a browser sends it: no session, no install id, no version. */
function get(path: string, settings: Partial<Env> = {}) {
  return send(new Request(`https://proxy.test${path}`), settings);
}

describe("App Links", () => {
  it("list both packages with every fingerprint of ANDROID_CERT_SHA256", async () => {
    const sent = await get("/.well-known/assetlinks.json", { ANDROID_CERT_SHA256: ` ${FINGERPRINT} ,${OTHER},, not-a-fingerprint ` });

    expect(sent.status).toBe(200);
    expect(sent.headers.get("content-type")).toBe("application/json");
    const fingerprints = [FINGERPRINT, OTHER.toUpperCase()];
    expect(JSON.parse(sent.body)).toEqual(
      ["com.dantech.splitvibe", "com.dantech.splitvibe.dev"].map((packageName) => ({
        relation: ["delegate_permission/common.handle_all_urls"],
        target: { namespace: "android_app", package_name: packageName, sha256_cert_fingerprints: fingerprints },
      })),
    );
  });

  it("are an empty list without any fingerprint", async () => {
    for (const value of ["", "  ", "not-a-fingerprint"]) {
      const sent = await get("/.well-known/assetlinks.json", { ANDROID_CERT_SHA256: value });
      expect(sent.status).toBe(200);
      expect(JSON.parse(sent.body)).toEqual([]);
    }
  });
});

describe("a link opened in a browser", () => {
  it("carries its token in the fragment, and shows one short page for every invite and request", async () => {
    const owner = await person();
    const group = await newGroup(owner, "Secret garden trip", VND);
    const invited = await api<{ token: string; url: string }>(owner, "POST", `/v1/groups/${group.id}/invites`);
    const made = await api<{ url: string }>(owner, "POST", "/v1/requests", {
      id: crypto.randomUUID(),
      amountMinor: 987_654,
      currency: VND,
      payerName: "Minh Secretname",
      creditorName: "Lan Secretname",
      title: "Secret dinner",
      method: { kind: "PlainDetails", text: "Account 0123456789" },
    });
    const links = [new URL(invited.body.url), new URL(made.body.url)];
    expect(links.map((link) => [link.origin, link.pathname])).toEqual([
      ["https://proxy.test", "/g/"],
      ["https://proxy.test", "/r/"],
    ]);
    expect(links[0].hash).toBe(`#${invited.body.token}`);
    expect(links[1].hash).toMatch(/^#[A-Za-z0-9_-]{22}$/);

    // What a browser asks for: the path, never the fragment.
    const pages = await Promise.all(links.map((link) => get(link.pathname)));

    for (const page of pages) {
      expect(page.status).toBe(200);
      expect(page.headers.get("content-type")).toBe("text/html; charset=utf-8");
      expect(page.headers.get("cache-control")).toBe("no-store");
      expect(page.headers.get("referrer-policy")).toBe("no-referrer");
      expect(page.body).toBe(pages[0].body);
    }
    expect(pages[0].body).toContain("Split Vibe");
    expect(pages[0].body).not.toMatch(/Secret|987|0123456789|Minh|Lan|garden/);
  });

  it("is served at /g/ and /r/ only, never with a token in the path", async () => {
    for (const path of ["/g", "/r", "/g/AAAAAAAAAAAAAAAAAAAAAA", "/r/AAAAAAAAAAAAAAAAAAAAAA", "/g/x/y"]) {
      expect((await get(path)).status, path).toBe(404);
    }
    expect((await send(new Request("https://proxy.test/g/", { method: "POST" }))).status).toBe(405);
  });
});
