import { createScheduledController } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { POLICY } from "../src/policy";
import type { ShareView } from "../src/share-state";
import { fakeRevenueCat } from "./fake-revenuecat";
import { fakeTurnstile } from "./fake-turnstile";
import { network } from "./network";
import { proxyRequest, send } from "./requests";
import {
  billProjection,
  changeClaim,
  newClaim,
  openShare,
  type Owned,
  payPal,
  publish,
  racing,
  type TestShare,
} from "./share-fixtures";
import { type Answer, api, linkToken, person, rows, sha256, VND } from "./shared";

type Viewed = { share: ShareView };

const DAY = 24 * 60 * 60 * 1000;

/** What Lan and Minh owe of the sample bill once the split is settled. */
function amounts(lan = 107_250, minh = 107_250) {
  return { me: { amountMinor: lan, payment: null }, p1: { amountMinor: minh, payment: payPal(minh) } };
}

/** Two claims on [link]: Minh took the first two items, Hoa the last two. */
async function twoClaims(link: TestShare) {
  const turnstile = fakeTurnstile();
  const minh = await newClaim(turnstile, link.token, "Minh", ["i1", "i2"]);
  const hoa = await newClaim(turnstile, link.token, "Hoa", ["i2", "i3"]);
  if (minh.status !== 200 || hoa.status !== 200) throw new Error(`The claims were not made: ${minh.status}, ${hoa.status}`);
  return { minh: minh.body, hoa: hoa.body };
}

function expire(link: TestShare, at = Date.now() - 1) {
  return env.DB.prepare("UPDATE shares SET expires_at = ?2 WHERE id = ?1").bind(link.id, at).run();
}

describe("a bill link", () => {
  it("is made by its owner, who gets the link to share; the token is kept only as a hash", async () => {
    const lan = await person();
    const id = crypto.randomUUID();
    const billId = crypto.randomUUID();
    const before = Date.now();

    const made = await api<Owned>(lan, "PUT", `/v1/shares/${id}`, { billId, billRevision: 3, bill: billProjection() });

    expect(made.status).toBe(201);
    expect(made.headers.get("cache-control")).toBe("no-store");
    expect(made.body.url).toMatch(/^https:\/\/proxy\.test\/s\/#[A-Za-z0-9_-]{22}$/);
    expect(made.body.share).toEqual({
      id,
      billId,
      billRevision: 3,
      status: "open",
      revision: 1,
      createdAt: expect.any(Number),
      expiresAt: made.body.share.createdAt + POLICY.shareMs,
      bill: billProjection(),
      claims: [],
      amounts: null,
      paidBy: null,
      matches: null,
    });
    expect(made.body.share.createdAt).toBeGreaterThanOrEqual(before);
    const token = linkToken(made.body.url);
    expect(await rows("SELECT token_hash FROM shares")).toEqual([{ token_hash: await sha256(token) }]);
    expect(JSON.stringify(await rows("SELECT * FROM shares"))).not.toContain(token);
  });

  it("is handed to its owner with the same address every time, and the same bill sent again changes nothing", async () => {
    const lan = await person();
    const link = await publish(lan);

    const again = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 1, bill: billProjection() });
    const read = await api<Owned>(lan, "GET", `/v1/shares/${link.id}`);

    expect(again.status).toBe(200);
    expect(again.body).toEqual({ share: link.share, url: link.url });
    expect(read.status).toBe(200);
    expect(read.body).toEqual({ share: link.share, url: link.url });
  });

  it("keeps only what the contract names of a bill", async () => {
    const lan = await person();
    const bill = billProjection();
    const sent = {
      ...bill,
      ocrDocument: "<bill_ocr rows=\"1\">secret</bill_ocr>",
      items: bill.items.map((item) => ({ ...item, unitPriceMinor: 65_000, photo: "page-1.jpg" })),
      participants: bill.participants.map((participant) => ({ ...participant, account: "someone@example.com" })),
    };

    const made = await api<Owned>(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: sent });

    expect(made.status).toBe(201);
    expect(made.body.share.bill).toEqual(bill);
    expect(JSON.stringify(await rows("SELECT * FROM shares"))).not.toMatch(/secret|page-1|someone@example\.com|unitPrice/);
  });

  it("takes invisible formatting characters out of the bill's names and labels", async () => {
    const lan = await person();
    const bill = billProjection();
    const sent = billProjection({
      merchant: "\u202ePho 24\u202c",
      items: bill.items.map((item) => ({ ...item, name: `\u200b${item.name}\u2060` })),
      charges: [{ id: "c1", label: "Service\u00ad charge\ufeff", amountMinor: 19_500 }],
      participants: [
        { id: "me", name: "\u200b" },
        { id: "p1", name: "Mi\u2060nh" },
      ],
    });

    const made = await api<Owned>(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: sent });
    const empty = await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, {
      billId: crypto.randomUUID(),
      billRevision: 1,
      bill: billProjection({ participants: [{ id: "p1", name: "\u2066\u2069" }] }),
    });

    expect(made.status).toBe(201);
    expect(made.body.share.bill).toEqual({ ...bill, participants: [{ id: "me", name: "" }, { id: "p1", name: "Minh" }] });
    expect(empty.status).toBe(400);
  });

  it("keeps the zero-width joiner and non-joiner, which emoji sequences and some scripts need, in the bill's names and labels", async () => {
    const lan = await person();
    const bill = billProjection();
    // A family, three emoji joined by U+200D; and Alireza in Persian, written with U+200C.
    const family = "\u{1F468}\u200d\u{1F469}\u200d\u{1F467}";
    const alireza = "\u0639\u0644\u06cc\u200c\u0631\u0636\u0627";
    const sent = billProjection({
      merchant: `Pho 24 ${family}`,
      items: [{ ...bill.items[0], name: `\u202e${family}\u2069 menu` }, ...bill.items.slice(1)],
      participants: [
        { id: "me", name: "\u200d\u200c" },
        { id: "p1", name: `\u200b${alireza}` },
      ],
    });

    const made = await api<Owned>(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: sent });

    expect(made.status).toBe(201);
    // Only the other formatting characters go; the owner's name of joiners alone shows nothing, so it is empty.
    expect(made.body.share.bill).toEqual({
      ...bill,
      merchant: `Pho 24 ${family}`,
      items: [{ ...bill.items[0], name: `${family} menu` }, ...bill.items.slice(1)],
      participants: [
        { id: "me", name: "" },
        { id: "p1", name: alireza },
      ],
    });
    for (const change of [
      { merchant: "\u200d" },
      { items: [{ ...bill.items[0], name: " \u200c\u200b\u200d " }] },
      { charges: [{ id: "c1", label: "\u200d\u202e\u200c", amountMinor: 19_500 }] },
      { participants: [{ id: "p1", name: "\u200d \u200c" }] },
    ]) {
      const refused = await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: billProjection(change) });
      expect(refused.status, JSON.stringify(change)).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
  });

  it("takes a bill that follows the contract to its edges", async () => {
    const lan = await person();
    const edges = billProjection({
      merchant: null,
      date: null,
      items: [
        { id: "A-z_0-9", name: "x".repeat(80), quantity: "-1", amountMinor: 0 },
        { id: "x".repeat(64), name: "Tea", quantity: "0", amountMinor: POLICY.maxAmountMinor },
        { id: "i3", name: "Rice", quantity: "12345.678", amountMinor: 1 },
      ],
      charges: [
        { id: "discount", label: "Discount", amountMinor: -POLICY.maxAmountMinor },
        { id: "tip", label: "Tip", amountMinor: POLICY.maxAmountMinor },
      ],
      totalMinor: 0,
      participants: [
        { id: "me", name: "" },
        { id: "p1", name: "y".repeat(60) },
      ],
    });

    const made = await api<Owned>(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 0, bill: edges });

    expect(made.status).toBe(201);
    expect(made.body.share.bill).toEqual(edges);
  });

  it("refuses a bill that does not follow the contract", async () => {
    const lan = await person();
    const item = billProjection().items[0];
    const bad: Array<Record<string, unknown>> = [
      { merchant: "" },
      { merchant: "   " },
      { merchant: "x".repeat(81) },
      { merchant: 24 },
      { date: "2026-02-30" },
      { date: "14/11/2026" },
      { currency: { code: "VND", fractionDigits: 4 } },
      { currency: undefined },
      { items: [] },
      { items: Array.from({ length: 201 }, (_, index) => ({ ...item, id: `i${index}` })) },
      { items: [{ ...item, id: "has space" }] },
      { items: [{ ...item, id: "x".repeat(65) }] },
      { items: [item, item] },
      { items: [{ ...item, name: "" }] },
      { items: [{ ...item, name: "x".repeat(81) }] },
      { items: [{ ...item, quantity: 2 }] },
      { items: [{ ...item, quantity: "1e3" }] },
      { items: [{ ...item, quantity: "2." }] },
      { items: [{ ...item, quantity: "01" }] },
      { items: [{ ...item, amountMinor: -1 }] },
      { items: [{ ...item, amountMinor: 1.5 }] },
      { items: [{ ...item, amountMinor: POLICY.maxAmountMinor + 1 }] },
      { items: "i1" },
      { charges: undefined },
      { charges: Array.from({ length: 21 }, (_, index) => ({ id: `c${index}`, label: "Tax", amountMinor: 1 })) },
      { charges: [{ id: "c1", label: "", amountMinor: 1 }] },
      { charges: [{ id: "c1", label: "Discount", amountMinor: -POLICY.maxAmountMinor - 1 }] },
      { totalMinor: -1 },
      { totalMinor: "214500" },
      { participants: [] },
      { participants: Array.from({ length: 31 }, (_, index) => ({ id: `p${index}`, name: "Someone" })) },
      { participants: [{ id: "p1", name: "" }] },
      { participants: [{ id: "p1", name: "x".repeat(61) }] },
      { participants: [{ id: "p1", name: "Minh" }, { id: "p1", name: "Hoa" }] },
    ];
    for (const change of bad) {
      const bill = { ...billProjection(), ...change };
      const sent = await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill });
      expect(sent.status, JSON.stringify(change).slice(0, 120)).toBe(400);
      expect(sent.body).toEqual({ error: "input" });
    }
    for (const body of [
      { billRevision: 1, bill: billProjection() },
      { billId: "not-a-uuid", billRevision: 1, bill: billProjection() },
      { billId: crypto.randomUUID(), billRevision: -1, bill: billProjection() },
      { billId: crypto.randomUUID(), billRevision: 1.5, bill: billProjection() },
      { billId: crypto.randomUUID(), billRevision: 1 },
    ]) {
      expect((await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, body)).status, JSON.stringify(body)).toBe(400);
    }
    expect((await api(lan, "PUT", "/v1/shares/not-a-uuid", { billId: crypto.randomUUID(), billRevision: 1, bill: billProjection() })).status).toBe(400);
    // Every byte counts: 200 items whose names take four bytes a letter.
    const heavy = billProjection({ items: Array.from({ length: 200 }, (_, index) => ({ id: `${index}`.padStart(64, "i"), name: "🍜".repeat(80), quantity: null, amountMinor: 1 })) });
    const tooLarge = await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: heavy });
    expect(tooLarge.status).toBe(413);
    expect(tooLarge.body).toEqual({ error: "too_large" });
    expect(await rows("SELECT id FROM shares")).toEqual([]);
  });

  it("shows a changed bill, never an older copy, and stays with its bill", async () => {
    const lan = await person();
    const link = await publish(lan, billProjection(), 2);
    const changed = billProjection({ merchant: "Pho 24 Hai Ba Trung", totalMinor: 220_000 });

    const shown = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 3, bill: changed });
    const older = await api(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection() });
    const otherBill = await api(lan, "PUT", `/v1/shares/${link.id}`, { billId: crypto.randomUUID(), billRevision: 4, bill: changed });
    const sameRevision = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 3, bill: billProjection({ merchant: "Pho 24 Q1" }) });

    expect(shown.status).toBe(200);
    expect(shown.body.url).toBe(link.url);
    expect(shown.body.share).toMatchObject({ billRevision: 3, revision: 2, status: "open", bill: changed });
    expect(older.status).toBe(409);
    expect(older.body).toEqual({ error: "conflict" });
    expect(otherBill.status).toBe(400);
    expect(sameRevision.body.share).toMatchObject({ billRevision: 3, revision: 3, bill: { merchant: "Pho 24 Q1" } });
  });

  it("drops an item the bill no longer has from every claim, and leaves the other items' claims as they were", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    const tuan = await newClaim(fakeTurnstile(), link.token, "Tuan", ["i3", "i1", "i2"]);
    const untouched = await rows<{ updated_at: number }>("SELECT updated_at FROM share_claims ORDER BY created_at, id");
    const bill = billProjection();
    const withoutTea = billProjection({ items: bill.items.filter((item) => item.id !== "i2"), totalMinor: 194_500 });

    const shown = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: withoutTea });

    expect(shown.status).toBe(200);
    expect(shown.body.share.claims.map((claim) => [claim.id, claim.name, claim.itemIds])).toEqual([
      [minh.you, "Minh", ["i1"]],
      [hoa.you, "Hoa", ["i3"]],
      [tuan.body.you, "Tuan", ["i3", "i1"]],
    ]);
    expect(shown.body.share.revision).toBe(tuan.body.share.revision + 1);
    // When a claim was last written stays its browser's.
    expect(shown.body.share.claims.map((claim) => ({ updated_at: claim.updatedAt }))).toEqual(untouched);
    const opened = await openShare(link.token);
    expect(opened.body.share.claims).toEqual([
      { id: minh.you, name: "Minh", itemIds: ["i1"] },
      { id: hoa.you, name: "Hoa", itemIds: ["i3"] },
      { id: tuan.body.you, name: "Tuan", itemIds: ["i3", "i1"] },
    ]);
    // Tea is no longer on the bill, so nobody can claim it.
    expect((await changeClaim(link.token, hoa.key ?? "", "Hoa", ["i2", "i3"])).status).toBe(400);
  });

  it("is read, changed and revoked by its owner only, and no route lists links", async () => {
    const lan = await person();
    const link = await publish(lan);
    const mallory = await person();
    const claimId = crypto.randomUUID();

    for (const [method, path, body] of [
      ["GET", `/v1/shares/${link.id}`],
      ["POST", `/v1/shares/${link.id}/lock`],
      ["POST", `/v1/shares/${link.id}/reopen`],
      ["POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() }],
      ["DELETE", `/v1/shares/${link.id}/claims/${claimId}`],
      ["DELETE", `/v1/shares/${link.id}`],
    ] as const) {
      const sent = await api(mallory, method, path, body);
      expect(sent.status, `${method} ${path}`).toBe(404);
      expect(sent.body).toEqual({ error: "not_found" });
    }
    const taken = await api(mallory, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 9, bill: billProjection({ merchant: "Mine now" }) });
    expect(taken.status).toBe(409);
    expect(taken.body).toEqual({ error: "conflict" });
    for (const path of ["/v1/shares", "/v1/shares/"]) {
      expect((await api(lan, "GET", path)).status, path).toBe(404);
    }
    for (const id of ["not-a-uuid", crypto.randomUUID()]) {
      expect((await api(lan, "GET", `/v1/shares/${id}`)).status, id).toBe(404);
    }
    expect((await api<Owned>(lan, "GET", `/v1/shares/${link.id}`)).body.share).toEqual(link.share);
  });

  it("needs its owner's session on every route of the owner", async () => {
    const link = await publish(await person());
    for (const [method, path] of [
      ["PUT", `/v1/shares/${link.id}`],
      ["GET", `/v1/shares/${link.id}`],
      ["POST", `/v1/shares/${link.id}/lock`],
      ["POST", `/v1/shares/${link.id}/reopen`],
      ["POST", `/v1/shares/${link.id}/finalize`],
      ["DELETE", `/v1/shares/${link.id}/claims/${crypto.randomUUID()}`],
      ["DELETE", `/v1/shares/${link.id}`],
    ] as const) {
      for (const authorization of [null, "Bearer not-a-session", `Bearer ${link.token}`]) {
        const headers = new Headers({ "content-type": "application/json" });
        if (authorization !== null) headers.set("authorization", authorization);
        const sent = await send(new Request(`https://proxy.test${path}`, { method, headers, body: method === "GET" ? undefined : "{}" }));
        expect(sent.status, `${method} ${path} with ${authorization}`).toBe(401);
        expect(JSON.parse(sent.body)).toEqual({ error: "session" });
      }
    }
  });

  it("lasts 30 days, and once expired is not read or changed, even before the daily run deletes it", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh } = await twoClaims(link);
    await expire(link);

    for (const [method, path, body] of [
      ["GET", `/v1/shares/${link.id}`],
      ["PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection() }],
      ["POST", `/v1/shares/${link.id}/lock`],
      ["POST", `/v1/shares/${link.id}/reopen`],
      ["DELETE", `/v1/shares/${link.id}/claims/${minh.you}`],
    ] as const) {
      const sent = await api(lan, method, path, body);
      expect(sent.status, `${method} ${path}`).toBe(410);
      expect(sent.body).toEqual({ error: "gone" });
    }
    expect((await openShare(link.token)).body).toEqual({ error: "gone" });
    expect((await changeClaim(link.token, minh.key ?? "", "Minh", ["i1"])).status).toBe(410);
    expect(await rows("SELECT id FROM shares")).toEqual([{ id: link.id }]);
    // A link made again under the same id, once the daily run deleted it, gets another token.
    await env.DB.prepare("DELETE FROM shares WHERE id = ?1").bind(link.id).run();
    const again = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection() });
    expect(again.status).toBe(201);
    expect(linkToken(again.body.url)).not.toBe(link.token);
    expect((await openShare(link.token)).status).toBe(404);
  });

  it("revoked, loses its claims, its bill and its amounts at once, and answers 410 to everyone until it is deleted", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh } = await twoClaims(link);

    const revoked = await api(lan, "DELETE", `/v1/shares/${link.id}`);
    const again = await api(lan, "DELETE", `/v1/shares/${link.id}`);

    expect(revoked.status).toBe(204);
    expect(again.status).toBe(204);
    expect(await rows("SELECT * FROM share_claims")).toEqual([]);
    expect(await rows("SELECT bill, amounts, revoked_at IS NOT NULL AS revoked FROM shares")).toEqual([{ bill: null, amounts: null, revoked: 1 }]);
    for (const [method, path, body] of [
      ["GET", `/v1/shares/${link.id}`],
      ["PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection() }],
      ["POST", `/v1/shares/${link.id}/lock`],
      ["POST", `/v1/shares/${link.id}/finalize`, { billRevision: 2, bill: billProjection(), amounts: amounts() }],
    ] as const) {
      expect((await api(lan, method, path, body)).status, `${method} ${path}`).toBe(410);
    }
    expect((await openShare(link.token)).body).toEqual({ error: "gone" });
    expect((await changeClaim(link.token, minh.key ?? "", "Minh", ["i1"])).body).toEqual({ error: "gone" });
    expect((await api(await person(), "DELETE", `/v1/shares/${link.id}`)).status).toBe(404);
  });

  it("revoked again, answers at once, writing nothing and waking no page", async () => {
    const lan = await person();
    const link = await publish(lan);
    await api(lan, "DELETE", `/v1/shares/${link.id}`);
    const before = await rows("SELECT * FROM shares");
    const touched: string[] = [];
    // The database and the namespace of the links' rooms, recording what the route asks of them.
    const db = new Proxy(env.DB, {
      get(target, property) {
        const value: unknown = Reflect.get(target, property);
        if (property === "batch" || property === "exec") touched.push(`DB.${property}`);
        return typeof value === "function" ? value.bind(target) : value;
      },
    });
    const rooms = new Proxy(env.SHARE_ROOM, {
      get(target, property) {
        touched.push(`SHARE_ROOM.${String(property)}`);
        const value: unknown = Reflect.get(target, property);
        return typeof value === "function" ? value.bind(target) : value;
      },
    });

    const again = await api(lan, "DELETE", `/v1/shares/${link.id}`, undefined, { DB: db, SHARE_ROOM: rooms });

    expect(again.status).toBe(204);
    expect(touched).toEqual([]);
    expect(await rows("SELECT * FROM shares")).toEqual(before);
  });

  it("is made 30 times an hour by one account, and changed 300 times an hour over all its links", async () => {
    const lan = await person();
    const links: TestShare[] = [];
    for (let count = 0; count < POLICY.sharesPerHour; count++) links.push(await publish(lan));

    const refused = await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: billProjection() });

    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    expect(Number(refused.headers.get("retry-after"))).toBeGreaterThan(3_500);
    expect((await publish(await person())).share.status).toBe("open");
    // Changes have their own window: the next to last, then the last, then one too many.
    expect((await api(lan, "POST", `/v1/shares/${links[0].id}/lock`)).status).toBe(200);
    await env.DB.prepare("UPDATE rate_windows SET used = ?2 WHERE account = ?1 AND kind = 'share_write'").bind(lan.account, POLICY.shareWritesPerHour - 1).run();
    expect((await api(lan, "POST", `/v1/shares/${links[1].id}/lock`)).status).toBe(200);
    const tooMany = await api(lan, "POST", `/v1/shares/${links[2].id}/lock`);
    expect(tooMany.status).toBe(429);
    expect((await api(lan, "GET", `/v1/shares/${links[2].id}`)).status).toBe(200);
    // Revoking is how an owner stops abuse, so no limit holds it back.
    expect((await api(lan, "DELETE", `/v1/shares/${links[2].id}`)).status).toBe(204);
  });
});

describe("settling a split", () => {
  it("locks a link, which then takes no claims, and answers every claim made before", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);

    const locked = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/lock`);
    const again = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/lock`);

    expect(locked.status).toBe(200);
    expect(locked.body.share).toMatchObject({ status: "locking", revision: hoa.share.revision + 1, amounts: null });
    expect(locked.body.share.claims).toEqual([
      { id: minh.you, name: "Minh", itemIds: ["i1", "i2"], updatedAt: expect.any(Number) },
      { id: hoa.you, name: "Hoa", itemIds: ["i2", "i3"], updatedAt: expect.any(Number) },
    ]);
    expect(again.body).toEqual(locked.body);
    const late = await changeClaim(link.token, minh.key ?? "", "Minh", ["i1"]);
    expect(late.status).toBe(409);
    expect(late.body).toEqual({ error: "locked" });
  });

  it("settles a locked link with what each participant owes and how to pay it, which the page then shows", async () => {
    const lan = await person();
    const link = await publish(lan);
    await twoClaims(link);
    const open = await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);

    const settled = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(100_000, 114_500) });
    const again = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(100_000, 114_500) });
    const corrected = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 2, bill: billProjection(), amounts: amounts(90_000, 124_500) });
    const stale = await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });

    expect(open.status).toBe(409);
    expect(settled.status).toBe(200);
    expect(settled.body.share).toMatchObject({ status: "locked", amounts: amounts(100_000, 114_500) });
    expect(again.body.share.revision).toBe(settled.body.share.revision);
    expect(corrected.body.share).toMatchObject({ status: "locked", billRevision: 2, revision: settled.body.share.revision + 1, amounts: amounts(90_000, 124_500) });
    expect(stale.status).toBe(409);
    const opened = await openShare(link.token);
    expect(opened.body.share).toMatchObject({ status: "locked", amounts: amounts(90_000, 124_500), paidBy: null });
    expect(Object.keys(opened.body.share.amounts ?? {})).toEqual(["me", "p1"]);
  });

  it("keeps who paid the whole bill with the amounts, shows it to everyone, and drops it with them", async () => {
    const lan = await person();
    const link = await publish(lan);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const settle = (paidBy: unknown) => api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(), paidBy });

    const byMinh = await settle("p1");
    const seen = await openShare(link.token);
    const again = await settle("p1");
    const byLan = await settle("me");
    const nobody = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });

    expect(byMinh.status).toBe(200);
    expect(byMinh.body.share).toMatchObject({ status: "locked", amounts: amounts(), paidBy: "p1" });
    expect(seen.body.share).toMatchObject({ amounts: amounts(), paidBy: "p1" });
    expect(again.body.share.revision).toBe(byMinh.body.share.revision);
    expect(byLan.body.share).toMatchObject({ paidBy: "me", revision: byMinh.body.share.revision + 1 });
    expect(nobody.body.share).toMatchObject({ paidBy: null, revision: byLan.body.share.revision + 1 });

    // Whatever drops the amounts drops who paid: a changed bill, reopening and revoking.
    await settle("p1");
    const changed = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection({ merchant: "Pho 25" }) });
    expect(changed.body.share).toMatchObject({ status: "locking", amounts: null, paidBy: null });
    await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 2, bill: billProjection(), amounts: amounts(), paidBy: "p1" });
    const reopened = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/reopen`);
    expect(reopened.body.share).toMatchObject({ status: "open", amounts: null, paidBy: null });
    expect((await openShare(link.token)).body.share).toMatchObject({ amounts: null, paidBy: null });
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 2, bill: billProjection(), amounts: amounts(), paidBy: "me" });
    expect((await api(lan, "DELETE", `/v1/shares/${link.id}`)).status).toBe(204);
    expect(await rows("SELECT amounts, paid_by FROM shares")).toEqual([{ amounts: null, paid_by: null }]);
  });

  it("refuses a payer who is not one of the bill's participants", async () => {
    const lan = await person();
    const link = await publish(lan);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    for (const paidBy of ["p9", "", "ME", 1, {}, ["me"]]) {
      const refused = await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(), paidBy });
      expect(refused.status, JSON.stringify(paidBy)).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
    // Another bill names other people: who paid must be one of the bill that is sent.
    const others = billProjection({ participants: [{ id: "me", name: "Lan" }, { id: "p2", name: "An" }] });
    const settled = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, {
      billRevision: 1,
      bill: others,
      amounts: { me: { amountMinor: 107_250, payment: null }, p2: { amountMinor: 107_250, payment: null } },
      paidBy: "p2",
    });
    expect(settled.body.share).toMatchObject({ status: "locked", paidBy: "p2" });
  });

  it("says which participant each claim is, to the owner and to everyone with the link, once the split is settled", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    const locked = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/lock`);
    const matches = { [minh.you]: "p1", [hoa.you]: "me" };

    const settled = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(), matches });
    const read = await api<Owned>(lan, "GET", `/v1/shares/${link.id}`);
    const opened = await openShare(link.token, minh.key);

    expect(link.share.matches).toBeNull();
    expect(minh.share.matches).toBeNull();
    expect(locked.body.share.matches).toBeNull();
    expect(settled.status).toBe(200);
    for (const share of [settled.body.share, read.body.share, opened.body.share]) {
      expect(share).toMatchObject({ status: "locked", amounts: amounts() });
      expect(share.matches).toEqual(matches);
    }
    // Kept with its keys sorted, as every settlement writes them.
    const sorted = [minh.you, hoa.you].sort();
    expect(Object.keys(opened.body.share.matches ?? {})).toEqual(sorted);
    expect(await rows("SELECT matches FROM shares")).toEqual([{ matches: JSON.stringify(Object.fromEntries(sorted.map((id) => [id, matches[id]]))) }]);
  });

  it("matches several claims to one participant, and leaves the claims it does not name unmatched", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const settle = (matches: unknown) => api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(), matches });

    const both = await settle({ [minh.you]: "p1", [hoa.you]: "p1" });
    const one = await settle({ [hoa.you]: "me" });
    const none = await settle({});
    const without = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });

    expect(both.status).toBe(200);
    expect(both.body.share.matches).toEqual({ [minh.you]: "p1", [hoa.you]: "p1" });
    expect(one.body.share.matches).toEqual({ [hoa.you]: "me" });
    // Matching no claim is the same as sending no matches.
    expect(none.body.share).toMatchObject({ status: "locked", amounts: amounts(), matches: null });
    expect(without.body.share).toMatchObject({ status: "locked", matches: null, revision: none.body.share.revision });
  });

  it("counts the same matches sent again, in any order, as the same settlement, and other matches as a change", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const settle = (matches: unknown) => api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts(), matches });

    const first = await settle({ [minh.you]: "p1", [hoa.you]: "me" });
    const reordered = await settle({ [hoa.you]: "me", [minh.you]: "p1" });
    const changed = await settle({ [minh.you]: "p1", [hoa.you]: "p1" });
    const fewer = await settle({ [minh.you]: "p1" });
    const none = await settle(null);
    const empty = await settle({});

    expect(reordered.body).toEqual(first.body);
    expect(changed.body.share.revision).toBe(first.body.share.revision + 1);
    expect(fewer.body.share).toMatchObject({ revision: changed.body.share.revision + 1, matches: { [minh.you]: "p1" } });
    expect(none.body.share).toMatchObject({ revision: fewer.body.share.revision + 1, matches: null });
    expect(empty.body).toEqual(none.body);
  });

  it("refuses matches of a claim the link does not have, of someone who is not on the bill, or of another shape", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    const elsewhere = await newClaim(fakeTurnstile(), (await publish(lan)).token, "Tuan", ["i1"]);
    await api(lan, "DELETE", `/v1/shares/${link.id}/claims/${hoa.you}`);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const before = await rows("SELECT * FROM shares WHERE id = ?1", link.id);
    const settle = (matches: unknown, bill = billProjection(), shares: unknown = amounts()) =>
      api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill, amounts: shares, matches });

    for (const matches of [
      { [crypto.randomUUID()]: "p1" },
      // A claim its owner took off, and one of another link.
      { [hoa.you]: "me" },
      { [minh.you]: "p1", [elsewhere.body.you]: "p1" },
      { [minh.you]: "p9" },
      { [minh.you]: "ME" },
      { [minh.you]: "" },
      { [minh.you]: 1 },
      { [minh.you]: null },
      { [minh.you]: { id: "p1" } },
      { [minh.you]: ["p1"] },
      [minh.you],
      [[minh.you, "p1"]],
      "p1",
      true,
    ]) {
      const refused = await settle(matches);
      expect(refused.status, JSON.stringify(matches)).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
    expect(await rows("SELECT * FROM shares WHERE id = ?1", link.id)).toEqual(before);
    // Who a claim is must be one of the bill that is sent.
    const others = billProjection({ participants: [{ id: "me", name: "Lan" }, { id: "p2", name: "An" }] });
    const theirs = { me: { amountMinor: 107_250, payment: null }, p2: { amountMinor: 107_250, payment: null } };
    expect((await settle({ [minh.you]: "p1" }, others, theirs)).status).toBe(400);
    const settled = await settle({ [minh.you]: "p2" }, others, theirs);
    expect(settled.status).toBe(200);
    expect(settled.body.share.matches).toEqual({ [minh.you]: "p2" });
  });

  it("lands matches only while every claim they name is on the link: one taken off just before is refused, and nothing is kept", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const raced: { removed?: Answer<Viewed> } = {};
    const db = racing(
      (sql) => sql.some((text) => text.includes("status = 'locked'")),
      async () => {
        raced.removed = await api<Viewed>(lan, "DELETE", `/v1/shares/${link.id}/claims/${hoa.you}`);
      },
    );

    const settled = await api(
      lan,
      "POST",
      `/v1/shares/${link.id}/finalize`,
      { billRevision: 1, bill: billProjection(), amounts: amounts(), matches: { [minh.you]: "p1", [hoa.you]: "me" } },
      { DB: db },
    );

    expect(raced.removed?.status).toBe(200);
    expect(settled.status).toBe(400);
    expect(settled.body).toEqual({ error: "input" });
    expect(await rows("SELECT status, amounts, matches FROM shares")).toEqual([{ status: "locking", amounts: null, matches: null }]);
  });

  it("drops the matches with the amounts: on a changed bill, on reopening and on revoking", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    const matches = { [minh.you]: "p1", [hoa.you]: "me" };
    const settle = (billRevision: number) => api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision, bill: billProjection(), amounts: amounts(), matches });
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    expect((await settle(1)).body.share.matches).toEqual(matches);

    const changed = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection({ merchant: "Pho 25" }) });
    expect(changed.body.share).toMatchObject({ status: "locking", amounts: null, paidBy: null, matches: null });
    expect((await settle(2)).body.share.matches).toEqual(matches);
    const reopened = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/reopen`);
    expect(reopened.body.share).toMatchObject({ status: "open", amounts: null, matches: null });
    expect((await openShare(link.token)).body.share.matches).toBeNull();
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    expect((await settle(2)).body.share.matches).toEqual(matches);
    expect((await api(lan, "DELETE", `/v1/shares/${link.id}`)).status).toBe(204);
    expect(await rows("SELECT amounts, paid_by, matches FROM shares")).toEqual([{ amounts: null, paid_by: null, matches: null }]);
    // The schema keeps matches only with the amounts.
    await expect(env.DB.prepare("UPDATE shares SET matches = '{}' WHERE id = ?1").bind(link.id).run()).rejects.toThrow(/CHECK constraint failed/);
  });

  it("refuses amounts that do not add up to the total, or that leave out or add a participant", async () => {
    const lan = await person();
    const link = await publish(lan);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const bad: unknown[] = [
      amounts(107_250, 107_249),
      amounts(107_250, 107_251),
      { me: { amountMinor: 214_500, payment: null } },
      { ...amounts(107_250, 107_250), p2: { amountMinor: 0, payment: null } },
      { me: { amountMinor: 214_500, payment: null }, p2: { amountMinor: 0, payment: null } },
      { me: { amountMinor: -1, payment: null }, p1: { amountMinor: 214_501, payment: null } },
      { me: { amountMinor: 107_250.5, payment: null }, p1: { amountMinor: 107_249.5, payment: null } },
      { me: 107_250, p1: 107_250 },
      [107_250, 107_250],
      null,
    ];
    for (const sent of bad) {
      const finalized = await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: sent });
      expect(finalized.status, JSON.stringify(sent)).toBe(400);
      expect(finalized.body).toEqual({ error: "input" });
    }
    const big = billProjection({ totalMinor: POLICY.maxAmountMinor, participants: [{ id: "me", name: "Lan" }, { id: "p1", name: "Minh" }] });
    const half = POLICY.maxAmountMinor / 2;
    const exact = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, {
      billRevision: 1,
      bill: big,
      amounts: { me: { amountMinor: half, payment: null }, p1: { amountMinor: half } },
    });
    expect(exact.status).toBe(200);
    expect(exact.body.share.amounts).toEqual({ me: { amountMinor: half, payment: null }, p1: { amountMinor: half, payment: null } });
  });

  it("takes payment links that are https with a host or a UPI payment, and nothing else a page could open", async () => {
    const lan = await person();
    const link = await publish(lan);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const finalize = (payment: unknown) =>
      api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, {
        billRevision: 1,
        bill: billProjection(),
        amounts: { me: { amountMinor: 107_250, payment: null }, p1: { amountMinor: 107_250, payment } },
      });
    const good = [
      payPal(107_250),
      { link: "upi://pay?pa=lan@okbank&pn=Lan%20Nguyen&am=10.00&cu=INR", qrPayload: "upi://pay?pa=lan@okbank", details: "UPI ID: lan@okbank", amountFilledIn: true },
      { link: null, qrPayload: "BCD\n002\n1\nSCT\n\nLan Nguyen\nDE89370400440532013000\nEUR10.00", details: "IBAN: DE89 3704\nAmount: €10.00", amountFilledIn: true },
      { link: `https://example.com/${"x".repeat(POLICY.maxPaymentLinkChars - 20)}`, qrPayload: "q".repeat(POLICY.maxPaymentCodeChars), details: "d".repeat(POLICY.maxPaymentDetailsChars), amountFilledIn: false },
      { details: "Pay by hand", amountFilledIn: false },
    ];
    for (const payment of good) {
      const finalized = await finalize(payment);
      expect(finalized.status, JSON.stringify(payment).slice(0, 100)).toBe(200);
      expect(finalized.body.share.amounts?.p1.payment).toEqual({ link: null, qrPayload: null, ...payment });
    }
    const pay = payPal(107_250);
    const bad = [
      { ...pay, link: "http://paypal.me/lan" },
      { ...pay, link: "javascript:alert(1)" },
      { ...pay, link: "data:text/html,<script>alert(1)</script>" },
      { ...pay, link: "upi://mandate?pa=lan@okbank" },
      { ...pay, link: "intent://pay#Intent;scheme=upi;end" },
      { ...pay, link: "HTTPS://paypal.me/lan" },
      { ...pay, link: "https://" },
      { ...pay, link: " https://paypal.me/lan" },
      { ...pay, link: "https://paypal.me/lan nguyen" },
      { ...pay, link: "https://paypal.me/\nlan" },
      { ...pay, link: `https://example.com/${"x".repeat(POLICY.maxPaymentLinkChars)}` },
      { ...pay, link: 42 },
      { ...pay, qrPayload: "q".repeat(POLICY.maxPaymentCodeChars + 1) },
      { ...pay, qrPayload: "bell\u0007" },
      { ...pay, details: "" },
      { ...pay, details: "d".repeat(POLICY.maxPaymentDetailsChars + 1) },
      { ...pay, details: "nul\u0000" },
      { ...pay, details: undefined },
      { ...pay, amountFilledIn: "yes" },
      { ...pay, amountFilledIn: undefined },
      "https://paypal.me/lan",
    ];
    for (const payment of bad) {
      const finalized = await finalize(payment);
      expect(finalized.status, JSON.stringify(payment).slice(0, 100)).toBe(400);
    }
  });

  it("reopened, takes claims again and shows no amounts; a bill changed after the split is settled again", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh } = await twoClaims(link);
    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    const settled = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });

    const changed = await api<Owned>(lan, "PUT", `/v1/shares/${link.id}`, { billId: link.billId, billRevision: 2, bill: billProjection({ merchant: "Pho 25" }) });
    const reopened = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/reopen`);
    const again = await api<Viewed>(lan, "POST", `/v1/shares/${link.id}/reopen`);

    expect(changed.body.share).toMatchObject({ status: "locking", amounts: null, revision: settled.body.share.revision + 1 });
    expect(reopened.body.share).toMatchObject({ status: "open", amounts: null, revision: changed.body.share.revision + 1 });
    expect(again.body).toEqual(reopened.body);
    expect((await changeClaim(link.token, minh.key ?? "", "Minh N.", ["i1"])).status).toBe(200);
  });

  it("takes a claim off while the link is open or locking; its browser then has none", async () => {
    const lan = await person();
    const link = await publish(lan);
    const { minh, hoa } = await twoClaims(link);
    const turnstile = fakeTurnstile();

    const removed = await api<Viewed>(lan, "DELETE", `/v1/shares/${link.id}/claims/${minh.you}`);
    const again = await api<Viewed>(lan, "DELETE", `/v1/shares/${link.id}/claims/${minh.you}`);

    expect(removed.status).toBe(200);
    expect(removed.body.share.claims.map((claim) => claim.id)).toEqual([hoa.you]);
    expect(removed.body.share.revision).toBe(hoa.share.revision + 1);
    expect(again.body).toEqual(removed.body);
    expect((await openShare(link.token, minh.key)).body.you).toBeNull();
    // Its key is one the link no longer knows, so a claim with it is a new one, which needs Turnstile.
    expect((await changeClaim(link.token, minh.key ?? "", "Minh", ["i1"])).body).toEqual({ error: "turnstile" });
    const back = await newClaim(turnstile, link.token, "Minh", ["i1"]);
    expect(back.status).toBe(200);
    expect(back.body.you).not.toBe(minh.you);

    await api(lan, "POST", `/v1/shares/${link.id}/lock`);
    expect((await api(lan, "DELETE", `/v1/shares/${link.id}/claims/${back.body.you}`)).status).toBe(200);
    await api(lan, "POST", `/v1/shares/${link.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });
    const whenLocked = await api(lan, "DELETE", `/v1/shares/${link.id}/claims/${hoa.you}`);
    expect(whenLocked.status).toBe(409);
    expect(whenLocked.body).toEqual({ error: "locked" });
    expect((await api(lan, "DELETE", `/v1/shares/${link.id}/claims/not-a-uuid`)).status).toBe(404);
  });
});

describe("cleaning up", () => {
  function dailyRun(): Promise<void> {
    network.use(
      http.get("https://api.frankfurter.dev/v2/rates", () => HttpResponse.json([{ date: "2026-11-14", base: "EUR", quote: "USD", rate: 1.1 }])),
      http.get("https://api.frankfurter.dev/v2/currencies", () => HttpResponse.json([{ iso_code: "USD", type: "currency" }])),
    );
    return worker.scheduled(createScheduledController({ cron: "30 16 * * *" }), env);
  }

  it("the daily run deletes links that expired or were revoked, with their claims, and keeps the others", async () => {
    const lan = await person();
    const expired = await publish(lan);
    const revoked = await publish(lan);
    const live = await publish(lan);
    for (const link of [expired, revoked, live]) await twoClaims(link);
    await expire(expired, Date.now() - DAY);
    await api(lan, "DELETE", `/v1/shares/${revoked.id}`);

    await dailyRun();

    expect(await rows("SELECT id FROM shares")).toEqual([{ id: live.id }]);
    expect(await rows("SELECT DISTINCT share_id FROM share_claims")).toEqual([{ share_id: live.id }]);
    expect((await openShare(expired.token)).status).toBe(404);
    expect((await openShare(revoked.token)).status).toBe(404);
    expect((await openShare(live.token)).body.share.claims).toHaveLength(2);
  });

  it("deleting an account deletes every link it made, with their claims, and empties how its requests are paid", async () => {
    const lan = await person();
    const mine = await publish(lan);
    const settled = await publish(lan);
    await twoClaims(mine);
    await api(lan, "POST", `/v1/shares/${settled.id}/lock`);
    await api(lan, "POST", `/v1/shares/${settled.id}/finalize`, { billRevision: 1, bill: billProjection(), amounts: amounts() });
    const minhs = await publish(await person());
    await twoClaims(minhs);
    const request = await api<{ url: string }>(lan, "POST", "/v1/requests", {
      id: crypto.randomUUID(),
      amountMinor: 107_250,
      currency: VND,
      payerName: "Minh",
      creditorName: "Lan",
      method: { kind: "PayPalMe", handle: "lan" },
      payment: payPal(107_250),
    });
    // Deleting an account deletes its customer at RevenueCat first.
    fakeRevenueCat();

    expect((await send(proxyRequest("DELETE", "/v1/account", { session: lan.session }))).status).toBe(204);

    expect(await rows("SELECT id FROM shares")).toEqual([{ id: minhs.id }]);
    expect(await rows("SELECT DISTINCT share_id FROM share_claims")).toEqual([{ share_id: minhs.id }]);
    expect(JSON.stringify(await rows("SELECT * FROM shares"))).not.toContain(lan.account);
    for (const link of [mine, settled]) expect((await openShare(link.token)).status).toBe(404);
    expect(await rows("SELECT method, payment FROM requests")).toEqual([{ method: "{}", payment: null }]);
    const viewed = await api<{ request: Record<string, unknown> }>(null, "POST", "/v1/request-links/view", { token: linkToken(request.body.url) });
    expect(viewed.body.request).toMatchObject({ creditorName: "Former member", payment: null });
  });
});
