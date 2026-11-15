import { describe, expect, it } from "vitest";
import { claimShare, openShare, previewInvite, retryAfterSeconds, viewRequest } from "../src/api";
import { FakeFetch } from "./fakes";
import invite from "./fixtures/invite.json";
import requestOpen from "./fixtures/request-open.json";
import shareOpen from "./fixtures/share-open.json";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";

describe("the proxy's routes", () => {
  it("carry the token in the JSON body only", async () => {
    const fetch = new FakeFetch()
      .answer("/v1/shares/open", { status: 200, body: shareOpen })
      .answer("/v1/request-links/view", { status: 200, body: requestOpen })
      .answer("/v1/invites/preview", { status: 200, body: invite });
    expect((await openShare(TOKEN, null, fetch.fetch)).ok).toBe(true);
    expect((await viewRequest(TOKEN, fetch.fetch)).ok).toBe(true);
    expect((await previewInvite(TOKEN, fetch.fetch)).ok).toBe(true);
    expect(fetch.calls).toEqual([
      { path: "/v1/shares/open", body: { token: TOKEN } },
      { path: "/v1/request-links/view", body: { token: TOKEN } },
      { path: "/v1/invites/preview", body: { token: TOKEN } },
    ]);
  });

  it("post JSON without credentials of their own or a referrer", async () => {
    let seen: RequestInit | undefined;
    await openShare(TOKEN, "KkKkKkKkKkKkKkKkKkKkKk", async (path, init) => {
      seen = init;
      expect(path).toBe("/v1/shares/open");
      return new Response(JSON.stringify(shareOpen), { status: 200 });
    });
    expect(seen?.method).toBe("POST");
    expect(seen?.headers).toEqual({ "content-type": "application/json" });
    expect(seen?.referrerPolicy).toBe("no-referrer");
    expect(seen?.cache).toBe("no-store");
    expect(JSON.parse(String(seen?.body))).toEqual({ token: TOKEN, key: "KkKkKkKkKkKkKkKkKkKkKk" });
  });

  it("send a claim's Turnstile token and attempt only when there are some", async () => {
    const fetch = new FakeFetch().answer("/v1/shares/claim", { status: 409, body: { error: "full" } }, { status: 409, body: { error: "locked" } });
    const attempt = "0b5c6c1e-58f2-4d39-9a5e-4cf1d2a7e9b1";
    const first = await claimShare({ token: TOKEN, key: null, attempt, name: "Bao", itemIds: ["i1"], turnstile: "t" }, fetch.fetch);
    const later = await claimShare({ token: TOKEN, key: "KkKkKkKkKkKkKkKkKkKkKk", attempt: null, name: "Bao", itemIds: [], turnstile: null }, fetch.fetch);
    expect(first).toEqual({ ok: false, error: { kind: "full" } });
    expect(later).toEqual({ ok: false, error: { kind: "locked" } });
    expect(fetch.calls.map((call) => call.body)).toEqual([
      { token: TOKEN, attempt, name: "Bao", itemIds: ["i1"], turnstile: "t" },
      { token: TOKEN, key: "KkKkKkKkKkKkKkKkKkKkKk", name: "Bao", itemIds: [] },
    ]);
  });

  it("tell a missing link from one that no longer works", async () => {
    const fetch = new FakeFetch()
      .answer("/v1/invites/preview", { status: 404, body: { error: "not_found" } }, { status: 410, body: { error: "gone" } })
      .answer("/v1/invites/preview", { status: 200, body: { group: { name: 5 } } });
    expect(await previewInvite(TOKEN, fetch.fetch)).toEqual({ ok: false, error: { kind: "not_found" } });
    expect(await previewInvite(TOKEN, fetch.fetch)).toEqual({ ok: false, error: { kind: "gone" } });
    expect(await previewInvite(TOKEN, fetch.fetch)).toEqual({ ok: false, error: { kind: "unexpected" } });
  });

  it("read Retry-After as whole seconds", () => {
    expect(retryAfterSeconds("30")).toBe(30);
    expect(retryAfterSeconds(" 5 ")).toBe(5);
    expect(retryAfterSeconds("0")).toBe(1);
    expect(retryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT")).toBeNull();
    expect(retryAfterSeconds(null)).toBeNull();
  });
});
