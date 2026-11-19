import { describe, expect, it } from "vitest";
import type { Env } from "../src/index";
import { send } from "./requests";
import { api, logsOf, newGroup, person, VND } from "./shared";

const FINGERPRINT = "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5";
const OTHER = "a1:b2:c3:d4:e5:f6:07:18:29:3a:4b:5c:6d:7e:8f:90:01:12:23:34:45:56:67:78:89:9a:ab:bc:cd:de:ef:f0";
/** The fingerprint of a debug key, which signs the debug build alone. */
const DEBUG = "0F:1E:2D:3C:4B:5A:69:78:87:96:A5:B4:C3:D2:E1:F0:0F:1E:2D:3C:4B:5A:69:78:87:96:A5:B4:C3:D2:E1:F0";

/** A plain GET, as Android's verifier or a browser sends it: no session, no install id, no version. */
function get(path: string, settings: Partial<Env> = {}) {
  return send(new Request(`https://proxy.test${path}`), settings);
}

/** What assetlinks.json says of [packageName]: the app signed by a key of [fingerprints] opens the proxy's links. */
function statement(packageName: string, fingerprints: string[]) {
  return {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: { namespace: "android_app", package_name: packageName, sha256_cert_fingerprints: fingerprints },
  };
}

describe("App Links", () => {
  it("list each package with the fingerprints of its own variable alone: the release build's, and the debug build's", async () => {
    const { result: sent, lines } = await logsOf(() =>
      get("/.well-known/assetlinks.json", { ANDROID_CERT_SHA256: ` ${FINGERPRINT} ,${OTHER},, not-a-fingerprint `, ANDROID_DEV_CERT_SHA256: DEBUG.toLowerCase() }),
    );

    expect(sent.status).toBe(200);
    expect(sent.headers.get("content-type")).toBe("application/json");
    expect(JSON.parse(sent.body)).toEqual([
      statement("com.dantech.splitvibe", [FINGERPRINT, OTHER.toUpperCase()]),
      statement("com.dantech.splitvibe.dev", [DEBUG]),
    ]);
    expect(lines).toEqual([JSON.stringify({ event: "bad_cert_fingerprint", name: "ANDROID_CERT_SHA256", count: 1 })]);
  });

  it("list only the package whose variable has fingerprints, when one is set", async () => {
    const release = await get("/.well-known/assetlinks.json", { ANDROID_CERT_SHA256: FINGERPRINT, ANDROID_DEV_CERT_SHA256: undefined });
    const debug = await get("/.well-known/assetlinks.json", { ANDROID_CERT_SHA256: "", ANDROID_DEV_CERT_SHA256: DEBUG });

    expect(JSON.parse(release.body)).toEqual([statement("com.dantech.splitvibe", [FINGERPRINT])]);
    expect(JSON.parse(debug.body)).toEqual([statement("com.dantech.splitvibe.dev", [DEBUG])]);
  });

  it("are an empty list without any fingerprint", async () => {
    for (const value of ["", "  ", "not-a-fingerprint"]) {
      const { result: sent } = await logsOf(() => get("/.well-known/assetlinks.json", { ANDROID_CERT_SHA256: value, ANDROID_DEV_CERT_SHA256: value }));
      expect(sent.status).toBe(200);
      expect(JSON.parse(sent.body)).toEqual([]);
    }
  });
});

describe("a link opened in a browser", () => {
  it("carries its token in the fragment, and opens the same page for every invite, request and bill", async () => {
    const owner = await person();
    const group = await newGroup(owner, "Zqxv garden trip", VND);
    const invited = await api<{ token: string; url: string }>(owner, "POST", `/v1/groups/${group.id}/invites`);
    const made = await api<{ url: string }>(owner, "POST", "/v1/requests", {
      id: crypto.randomUUID(),
      amountMinor: 987_654,
      currency: VND,
      payerName: "Minh Zqxv",
      creditorName: "Lan Zqxv",
      title: "Zqxv dinner",
      method: { kind: "PlainDetails", text: "Account 0123456789" },
    });
    const shared = await api<{ url: string }>(owner, "PUT", `/v1/shares/${crypto.randomUUID()}`, {
      billId: crypto.randomUUID(),
      billRevision: 1,
      bill: {
        merchant: "Zqxv noodles",
        date: null,
        currency: VND,
        items: [{ id: "i1", name: "Zqxv soup", quantity: null, amountMinor: 987_654 }],
        charges: [],
        totalMinor: 987_654,
        participants: [{ id: "me", name: "Lan Zqxv" }],
      },
    });
    const links = [new URL(invited.body.url), new URL(made.body.url), new URL(shared.body.url)];
    expect(links.map((link) => [link.origin, link.pathname])).toEqual([
      ["https://proxy.test", "/g/"],
      ["https://proxy.test", "/r/"],
      ["https://proxy.test", "/s/"],
    ]);
    expect(links[0].hash).toBe(`#${invited.body.token}`);
    for (const link of links) expect(link.hash).toMatch(/^#[A-Za-z0-9_-]{22}$/);

    // What a browser asks for: the path, never the fragment.
    const pages = await Promise.all(links.map((link) => get(link.pathname)));

    for (const page of pages) {
      expect(page.status).toBe(200);
      expect(page.headers.get("content-type")).toBe("text/html; charset=utf-8");
      expect(page.headers.get("cache-control")).toBe("no-store");
      expect(page.headers.get("referrer-policy")).toBe("no-referrer");
      expect(page.body).toBe(pages[0].body);
    }
    expect(pages[0].body).not.toMatch(/Zqxv|987654|0123456789/);
  });
});
