import { createExecutionContext, runInDurableObject, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { afterEach, describe, expect, it } from "vitest";
import { POLICY } from "../src/policy";
import type { PublicShare } from "../src/share-state";
import { fakeTurnstile } from "./fake-turnstile";
import { fetchWorker } from "./requests";
import { billProjection, changeClaim, newClaim, openShare, type Owned, publish, type TestShare } from "./share-fixtures";
import { api, linkToken, person } from "./shared";

/** What a page heard on its connection, and how the connection closed. */
type Page = { socket: WebSocket; heard: string[]; closed: { code: number; reason: string } | null };

const pages: Page[] = [];

// Every connection a test opened is closed, and the close answered, before the next test empties
// the Durable Objects, so that none is cut off halfway.
afterEach(async () => {
  const open = pages.splice(0).filter((page) => page.closed === null);
  for (const page of open) page.socket.close(1000, "done");
  await until(() => open.every((page) => page.closed !== null));
});

/** The handshake of a page's live connection to the link [token], as a browser sends it. */
async function connect(token: string, protocols: string | null = `splitvibe.v1, ${token}`, upgrade = "websocket"): Promise<Response> {
  const headers = new Headers();
  if (upgrade !== "") headers.set("upgrade", upgrade);
  if (protocols !== null) headers.set("sec-websocket-protocol", protocols);
  const ctx = createExecutionContext();
  const response = await fetchWorker(new Request("https://proxy.test/v1/shares/live", { headers }), ctx);
  await waitOnExecutionContext(ctx);
  return response;
}

/** The page on the other end of an accepted connection. */
function listen(response: Response): Page {
  const socket = response.webSocket;
  if (response.status !== 101 || socket === null) throw new Error(`The connection was refused: ${response.status}`);
  const page: Page = { socket, heard: [], closed: null };
  socket.addEventListener("message", (event) => {
    page.heard.push(typeof event.data === "string" ? event.data : "(binary)");
  });
  socket.addEventListener("close", (event) => {
    page.closed = { code: event.code, reason: event.reason };
  });
  socket.accept();
  pages.push(page);
  return page;
}

async function opened(link: TestShare): Promise<Page> {
  return listen(await connect(link.token));
}

/** Waits for [done], which a message or a close on a connection makes true. */
async function until(done: () => boolean): Promise<void> {
  for (let tick = 0; tick < 300 && !done(); tick++) await new Promise((resolve) => setTimeout(resolve, 10));
  if (!done()) throw new Error("Nothing came");
}

/** The updates a page heard, read as JSON. */
function updates(page: Page): Array<{ type: string; share?: PublicShare }> {
  return page.heard.map((text) => JSON.parse(text) as { type: string; share?: PublicShare });
}

/** The object that holds [link]'s connections, named by its id and when it was made. */
function room(link: TestShare) {
  return env.SHARE_ROOM.get(env.SHARE_ROOM.idFromName(`${link.id}:${link.share.createdAt}`));
}

describe("a link's live connection", () => {
  it("is answered with the subprotocol splitvibe.v1 alone, and hears the link after every change", async () => {
    const owner = await person();
    const link = await publish(owner);
    const turnstile = fakeTurnstile();

    const response = await connect(link.token);
    const page = listen(response);
    const claimed = await newClaim(turnstile, link.token, "Minh", ["i1"]);
    await until(() => page.heard.length === 1);
    await changeClaim(link.token, claimed.body.key ?? "", "Minh", ["i1", "i2"]);
    await until(() => page.heard.length === 2);
    await api(owner, "POST", `/v1/shares/${link.id}/lock`);
    await until(() => page.heard.length === 3);
    await api(owner, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection({ merchant: "Pho 25" }) });
    await until(() => page.heard.length === 4);

    expect(response.status).toBe(101);
    expect(response.headers.get("sec-websocket-protocol")).toBe("splitvibe.v1");
    const heard = updates(page);
    expect(heard[0]).toEqual({ type: "share", share: claimed.body.share });
    expect(heard.map((update) => [update.type, update.share?.status, update.share?.revision])).toEqual([
      ["share", "open", link.share.revision + 1],
      ["share", "open", link.share.revision + 2],
      ["share", "locking", link.share.revision + 3],
      ["share", "locking", link.share.revision + 4],
    ]);
    expect(heard[3].share).toEqual((await openShare(link.token)).body.share);
    // The object holds connections only: D1 holds the link.
    expect(await runInDurableObject(room(link), async (_, state) => (await state.storage.list()).size)).toBe(0);
  });

  it("hears that the link is gone once it is revoked, and is closed", async () => {
    const owner = await person();
    const link = await publish(owner);
    const first = await opened(link);
    const second = await opened(link);

    expect((await api(owner, "DELETE", `/v1/shares/${link.id}`)).status).toBe(204);
    await until(() => first.closed !== null && second.closed !== null);

    for (const page of [first, second]) {
      expect(updates(page)).toEqual([{ type: "gone" }]);
      expect(page.closed).toEqual({ code: 1000, reason: "gone" });
    }
    expect((await connect(link.token)).status).toBe(410);
  });

  it("answers ping with pong, and ends a connection that sends anything else", async () => {
    const link = await publish(await person());
    const quiet = await opened(link);
    const chatty = await opened(link);

    quiet.socket.send("ping");
    await until(() => quiet.heard.length === 1);
    chatty.socket.send("hello");
    await until(() => chatty.closed !== null);

    expect(quiet.heard).toEqual(["pong"]);
    expect(quiet.closed).toBeNull();
    expect(chatty.closed?.code).toBe(1008);
  });

  it("is one of 50 a link holds at once: the 51st is refused", async () => {
    const link = await publish(await person());
    for (let count = 0; count < POLICY.maxLiveConnections; count++) await opened(link);

    const refused = await connect(link.token);

    expect(refused.status).toBe(429);
    expect(refused.webSocket).toBeNull();
    expect(await refused.json()).toEqual({ error: "rate_limited" });
    expect(Number(refused.headers.get("retry-after"))).toBeGreaterThan(0);
    expect(await runInDurableObject(room(link), (_, state) => state.getWebSockets().length)).toBe(POLICY.maxLiveConnections);
    // Another link has room of its own.
    expect((await connect((await publish(await person())).token)).status).toBe(101);
  });

  it("when full, first closes the connections that stayed quiet for two minutes, and takes the new one", async () => {
    const link = await publish(await person());
    const early: Page[] = [];
    for (let count = 1; count < POLICY.maxLiveConnections; count++) early.push(await opened(link));
    const [pinging, ...quiet] = early;
    pinging.socket.send("ping");
    await until(() => pinging.heard.length === 1);
    // These were accepted three minutes ago, and one of them pinged just now.
    await runInDurableObject(room(link), (_, state) => {
      for (const socket of state.getWebSockets()) socket.serializeAttachment({ acceptedAt: Date.now() - 3 * 60 * 1000 });
    });
    // The last place goes to a page that has not pinged yet, as none does in its first 30 seconds.
    const fresh = await opened(link);

    const joined = await connect(link.token);

    expect(joined.status).toBe(101);
    listen(joined);
    await until(() => quiet.every((page) => page.closed !== null));
    for (const page of quiet) expect(page.closed).toEqual({ code: 1000, reason: "quiet" });
    expect(pinging.closed).toBeNull();
    expect(fresh.closed).toBeNull();
    expect(await runInDurableObject(room(link), (_, state) => state.getWebSockets().filter((socket) => socket.readyState === WebSocket.OPEN).length)).toBe(3);
  });

  it("of a link made again under the same id hears nothing of the new link", async () => {
    const owner = await person();
    const link = await publish(owner);
    const old = await opened(link);
    // The daily run deleted the link; its owner makes it again under the same id, later.
    await env.DB.prepare("DELETE FROM shares WHERE id = ?1").bind(link.id).run();
    await new Promise((resolve) => setTimeout(resolve, 5));
    const again = await api<Owned>(owner, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 1, bill: billProjection() });
    expect(again.status).toBe(201);
    expect(again.body.share.createdAt).not.toBe(link.share.createdAt);
    const token = linkToken(again.body.url);
    const current = listen(await connect(token));

    await newClaim(fakeTurnstile(), token, "Minh", ["i1"]);
    await until(() => current.heard.length === 1);
    await api(owner, "DELETE", `/v1/shares/${link.id}`);
    await until(() => current.closed !== null);

    expect(updates(current).map((update) => update.type)).toEqual(["share", "gone"]);
    expect(old.heard).toEqual([]);
    expect(old.closed).toBeNull();
  });

  it("needs an upgrade, the two subprotocols and a token of a link that works", async () => {
    const owner = await person();
    const link = await publish(owner);
    const expired = await publish(owner);
    await env.DB.prepare("UPDATE shares SET expires_at = ?2 WHERE id = ?1").bind(expired.id, Date.now() - 1).run();

    for (const [protocols, upgrade] of [
      [`splitvibe.v1, ${link.token}`, ""],
      [null, "websocket"],
      ["splitvibe.v1", "websocket"],
      [link.token, "websocket"],
      [`splitvibe.v2, ${link.token}`, "websocket"],
      [`splitvibe.v1, ${link.token}, ${link.token}`, "websocket"],
    ] as const) {
      const refused = await connect(link.token, protocols, upgrade);
      expect(refused.status, `${protocols} ${upgrade}`).toBe(400);
      expect(await refused.json()).toEqual({ error: "input" });
    }
    for (const token of ["AAAAAAAAAAAAAAAAAAAAAA", "not-a-token"]) {
      expect((await connect(token)).status, token).toBe(404);
    }
    expect((await connect(expired.token)).status).toBe(410);
    // Either order of the two protocols opens it.
    expect((await connect(link.token, `${link.token}, splitvibe.v1`)).status).toBe(101);
  });
});
