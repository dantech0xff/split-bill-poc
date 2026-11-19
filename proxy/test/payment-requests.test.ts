import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { POLICY } from "../src/policy";
import { forgetExpired } from "../src/reminders";
import type { RequestView } from "../src/requests";
import { fakeFcm } from "./fake-fcm";
import { fakeRevenueCat } from "./fake-revenuecat";
import { proxyRequest, send } from "./requests";
import { addPlaceholder, api, join, linkToken, newGroup, type Person, person, rows, sha256, stateOf, THB, VND } from "./shared";

type Created = { request: RequestView; url: string };

const DAY = 24 * 60 * 60 * 1000;

const METHOD = { kind: "VietQr", bankBin: "970436", accountNumber: "0123456789", accountName: "NGUYEN LAN" };
/** How to pay, as the creditor's app builds it for the request's web page. */
const PAYMENT = {
  link: null,
  qrPayload: "00020101021238540010A00000072701240006970436011001234567890208QRIBFTTA53037045406150000",
  details: "Bank: Vietcombank (970436)\nAccount: 0123456789\nName: NGUYEN LAN\nAmount: ₫150,000",
  amountFilledIn: true,
};

function requestBody(overrides: Record<string, unknown> = {}) {
  return {
    id: crypto.randomUUID(),
    amountMinor: 150_000,
    currency: VND,
    payerName: "Minh",
    creditorName: "Lan",
    title: "Hot pot at Manwah",
    method: METHOD,
    ...overrides,
  };
}

async function ask(creditor: Person, overrides: Record<string, unknown> = {}) {
  return api<Created>(creditor, "POST", "/v1/requests", requestBody(overrides));
}

function tokenOf(url: string): string {
  return linkToken(url);
}

/** A group in VND with its owner, a member with an account and a placeholder. */
async function vndGroup() {
  const owner = await person();
  const group = await newGroup(owner, "Flat 4B", VND);
  const minh = await person();
  const minhId = await join(group, minh, "Minh");
  const hoaId = await addPlaceholder(group, "Hoa");
  return { owner, group, minh, minhId, hoaId };
}

describe("a payment request", () => {
  it("is made by its creditor, who gets the link to share; the link is kept only as a hash", async () => {
    const lan = await person();
    const body = requestBody({ creditorName: "  Lan  ", payerName: " Minh ", remindEveryDays: 3 });
    const before = Date.now();

    const made = await api<Created>(lan, "POST", "/v1/requests", body);

    expect(made.status).toBe(201);
    expect(made.headers.get("cache-control")).toBe("no-store");
    expect(made.body.request).toEqual({
      id: body.id,
      role: "creditor",
      amountMinor: 150_000,
      currency: VND,
      payerName: "Minh",
      creditorName: "Lan",
      title: "Hot pot at Manwah",
      method: METHOD,
      groupId: null,
      toMemberId: null,
      creditorMemberId: null,
      billId: null,
      createdAt: expect.any(Number),
      reportedPaidAt: null,
      confirmedPaidAt: null,
      remindEveryDays: 3,
      debtorBound: false,
    });
    expect(made.body.request.createdAt).toBeGreaterThanOrEqual(before);
    expect(made.body.url).toMatch(/^https:\/\/proxy\.test\/r\/#[A-Za-z0-9_-]{22}$/);
    const token = tokenOf(made.body.url);
    expect(await rows("SELECT link_hash FROM requests")).toEqual([{ link_hash: await sha256(token) }]);
    expect(JSON.stringify(await rows("SELECT * FROM requests"))).not.toContain(token);
  });

  it("sent again answers with the same link, and an id another creditor used is refused", async () => {
    const lan = await person();
    const body = requestBody();
    const first = await api<Created>(lan, "POST", "/v1/requests", body);

    const again = await api<Created>(lan, "POST", "/v1/requests", { ...body, amountMinor: 1 });
    const stolen = await api(await person(), "POST", "/v1/requests", body);

    expect(again.status).toBe(201);
    expect(again.body).toEqual(first.body);
    expect(stolen.status).toBe(409);
    expect(await rows("SELECT id FROM requests")).toHaveLength(1);
  });

  it("keeps an empty or missing title as none, and refuses a body that does not follow the contract", async () => {
    const lan = await person();
    expect((await ask(lan, { title: "   " })).body.request.title).toBeNull();
    expect((await ask(lan, { title: undefined })).body.request.title).toBeNull();
    expect((await ask(lan, { title: "x".repeat(80) })).body.request.title).toHaveLength(80);

    const bad: Array<Record<string, unknown>> = [
      { id: "not-a-uuid" },
      { amountMinor: 0 },
      { amountMinor: -5 },
      { amountMinor: 1.5 },
      { amountMinor: "150000" },
      { currency: { code: "VND", fractionDigits: 5 } },
      { payerName: "" },
      { payerName: "x".repeat(61) },
      { creditorName: undefined },
      { creditorName: "  " },
      { creditorName: "x".repeat(61) },
      { title: "x".repeat(81) },
      { title: 42 },
      { method: "VietQr 970436 0123456789" },
      { method: [METHOD] },
      { method: undefined },
      { remindEveryDays: 2 },
      { remindEveryDays: "7" },
      { groupId: crypto.randomUUID() },
      { toMemberId: crypto.randomUUID() },
      { billId: "not-a-uuid" },
    ];
    for (const overrides of bad) {
      const sent = await ask(lan, overrides);
      expect(sent.status, JSON.stringify(overrides)).toBe(400);
      expect(sent.body).toEqual({ error: "input" });
    }
    const huge = await ask(lan, { method: { kind: "PlainDetails", text: "x".repeat(POLICY.maxMethodBytes) } });
    expect(huge.status).toBe(413);
    expect(huge.body).toEqual({ error: "too_large" });
  });

  it("in a group, names another member and the group's currency, and needs the creditor to be a member", async () => {
    const { owner, group, minhId } = await vndGroup();
    const inGroup = (overrides: Record<string, unknown>) => ask(owner, { groupId: group.id, toMemberId: minhId, ...overrides });

    expect((await inGroup({ toMemberId: group.ownerMemberId })).status).toBe(400);
    expect((await inGroup({ toMemberId: crypto.randomUUID() })).status).toBe(400);
    expect((await inGroup({ currency: THB })).status).toBe(400);
    expect((await inGroup({ currency: { code: "VND", fractionDigits: 2 } })).status).toBe(400);
    expect((await inGroup({ groupId: crypto.randomUUID() })).status).toBe(404);
    expect((await ask(await person(), { groupId: group.id, toMemberId: minhId })).body).toEqual({ error: "forbidden" });
    expect(await rows("SELECT id FROM requests")).toEqual([]);
  });

  it("in a group, binds a payer who has an account at once, and waits for anyone else to accept the link", async () => {
    const { owner, group, minh, minhId, hoaId } = await vndGroup();
    const billId = crypto.randomUUID();

    const toMinh = await ask(owner, { groupId: group.id, toMemberId: minhId, billId });
    const toHoa = await ask(owner, { groupId: group.id, toMemberId: hoaId });

    expect(toMinh.status).toBe(201);
    expect(toMinh.body.request).toMatchObject({
      groupId: group.id,
      toMemberId: minhId,
      creditorMemberId: group.ownerMemberId,
      billId,
      debtorBound: true,
      role: "creditor",
    });
    expect(toHoa.body.request).toMatchObject({ toMemberId: hoaId, debtorBound: false });
    const minhsList = await api<{ requests: RequestView[] }>(minh, "GET", "/v1/requests");
    expect(minhsList.body.requests).toEqual([{ ...toMinh.body.request, role: "debtor" }]);
  });

  it("is made at most 30 times an hour by one creditor", async () => {
    const lan = await person();
    const made: string[] = [];
    for (let count = 0; count < POLICY.requestsPerHour; count++) {
      const sent = await ask(lan);
      expect(sent.status).toBe(201);
      made.push(sent.body.request.id);
    }

    const refused = await ask(lan);
    const sentAgain = await api(lan, "POST", "/v1/requests", requestBody({ id: made[0] }));

    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    expect(Number(refused.headers.get("retry-after"))).toBeGreaterThan(3_500);
    expect(sentAgain.status).toBe(201);
    expect((await ask(await person())).status).toBe(201);
    expect(await rows("SELECT id FROM requests WHERE creditor = ?1", lan.account)).toHaveLength(POLICY.requestsPerHour);
  });

  it("is listed for its creditor and its bound debtor only, newest first", async () => {
    const lan = await person();
    const minh = await person();
    const first = await ask(lan);
    const second = await ask(lan);
    await api(minh, "POST", "/v1/request-links/accept", { token: tokenOf(second.body.url) });

    const lans = await api<{ requests: RequestView[] }>(lan, "GET", "/v1/requests");
    const minhs = await api<{ requests: RequestView[] }>(minh, "GET", "/v1/requests");
    const strangers = await api<{ requests: RequestView[] }>(await person(), "GET", "/v1/requests");

    expect(lans.body.requests.map((request) => request.id)).toEqual([second.body.request.id, first.body.request.id]);
    expect(lans.body.requests.every((request) => request.role === "creditor")).toBe(true);
    expect(minhs.body.requests.map((request) => [request.id, request.role, request.debtorBound])).toEqual([[second.body.request.id, "debtor", true]]);
    expect(strangers.body.requests).toEqual([]);
  });
});

describe("a request link", () => {
  it("shows the request as its debtor will see it, and accepting it makes the caller the debtor", async () => {
    const lan = await person();
    const made = await ask(lan);
    const token = tokenOf(made.body.url);
    const minh = await person();

    const seen = await api<RequestView>(minh, "POST", "/v1/request-links/open", { token });
    const accepted = await api<RequestView>(minh, "POST", "/v1/request-links/accept", { token });
    const again = await api<RequestView>(minh, "POST", "/v1/request-links/accept", { token });

    const asDebtor = { ...made.body.request, role: "debtor", debtorBound: true };
    expect(seen.status).toBe(200);
    expect(seen.body).toEqual(asDebtor);
    expect(accepted.body).toEqual(asDebtor);
    expect(again.body).toEqual(asDebtor);
    expect((await api<{ requests: RequestView[] }>(lan, "GET", "/v1/requests")).body.requests[0].debtorBound).toBe(true);
    expect(await rows("SELECT debtor FROM requests")).toEqual([{ debtor: minh.account }]);
  });

  it("is accepted by one account only, and never by its creditor", async () => {
    const lan = await person();
    const made = await ask(lan);
    const token = tokenOf(made.body.url);
    await api(await person(), "POST", "/v1/request-links/accept", { token });
    const late = await person();

    expect((await api(late, "POST", "/v1/request-links/accept", { token })).body).toEqual({ error: "conflict" });
    expect((await api(late, "POST", "/v1/request-links/open", { token })).body).toEqual({ error: "conflict" });
    expect((await api(lan, "POST", "/v1/request-links/accept", { token })).body).toEqual({ error: "forbidden" });
    expect((await api<RequestView>(lan, "POST", "/v1/request-links/open", { token })).body).toEqual({ ...made.body.request, debtorBound: true });
  });

  it("made for one creditor never opens a request another account makes later under the same id", async () => {
    const lan = await person();
    const mallory = await person();
    const minh = await person();
    const body = requestBody({ method: { kind: "VietQr", accountNumber: "LAN-ACCOUNT" } });
    const token = tokenOf((await ask(lan, body)).body.url);
    // Whoever opened the link saw the request's id.
    expect((await api<RequestView>(mallory, "POST", "/v1/request-links/open", { token })).body.id).toBe(body.id);
    expect((await api(lan, "DELETE", `/v1/requests/${body.id}`)).status).toBe(204);
    await forgetExpired(env, Date.now() + POLICY.forgetAfterMs + DAY);
    expect(await rows("SELECT id FROM requests")).toEqual([]);

    const reused = await ask(mallory, { ...body, method: { kind: "VietQr", accountNumber: "MALLORY-ACCOUNT" } });

    expect(reused.status).toBe(201);
    expect(tokenOf(reused.body.url)).not.toBe(token);
    expect((await api(minh, "POST", "/v1/request-links/open", { token })).body).toEqual({ error: "not_found" });
    expect((await api(minh, "POST", "/v1/request-links/accept", { token })).body).toEqual({ error: "not_found" });
    expect((await api<RequestView>(minh, "POST", "/v1/request-links/open", { token: tokenOf(reused.body.url) })).body.method).toEqual({
      kind: "VietQr",
      accountNumber: "MALLORY-ACCOUNT",
    });
  });

  it("answers 404 for a link never made, and 410 once its request was deleted", async () => {
    const lan = await person();
    const made = await ask(lan);
    const token = tokenOf(made.body.url);
    const minh = await person();

    for (const token of ["AAAAAAAAAAAAAAAAAAAAAA", "too-short", "with+plus=equals______"]) {
      expect((await api(minh, "POST", "/v1/request-links/open", { token })).body).toEqual({ error: "not_found" });
      expect((await api(minh, "POST", "/v1/request-links/accept", { token })).status).toBe(404);
    }
    for (const body of [{}, { token: 42 }, { token: null }]) {
      expect((await api(minh, "POST", "/v1/request-links/open", body)).body).toEqual({ error: "input" });
      expect((await api(minh, "POST", "/v1/request-links/accept", body)).status).toBe(400);
    }
    expect((await api(lan, "DELETE", `/v1/requests/${made.body.request.id}`)).status).toBe(204);
    expect((await api(minh, "POST", "/v1/request-links/open", { token })).body).toEqual({ error: "gone" });
    expect((await api(minh, "POST", "/v1/request-links/accept", { token })).status).toBe(410);
  });
});

describe("a request link's web page", () => {
  type WebView = { request: Record<string, unknown> };

  function view(token: string) {
    return api<WebView>(null, "POST", "/v1/request-links/view", { token });
  }

  it("shows, without a session, who asks whom for how much and how to pay, as the creditor's app built it", async () => {
    const lan = await person();
    const made = await ask(lan, { payment: PAYMENT });
    const token = tokenOf(made.body.url);

    const seen = await view(token);

    expect(made.status).toBe(201);
    expect(made.body.request).not.toHaveProperty("payment");
    expect(seen.status).toBe(200);
    expect(seen.headers.get("cache-control")).toBe("no-store");
    expect(seen.body).toEqual({
      request: {
        creditorName: "Lan",
        payerName: "Minh",
        amountMinor: 150_000,
        currency: VND,
        title: "Hot pot at Manwah",
        status: "open",
        payment: PAYMENT,
        createdAt: made.body.request.createdAt,
      },
    });
    expect(JSON.stringify(seen.body)).not.toMatch(new RegExp(`${lan.account}|${made.body.request.id}|970436","accountNumber`));
    expect((await view(tokenOf((await ask(lan)).body.url))).body.request.payment).toBeNull();
  });

  it("says whether the request is open, reported paid or confirmed, and how to pay it until it is confirmed", async () => {
    const lan = await person();
    const minh = await person();
    const made = await ask(lan, { payment: PAYMENT });
    const token = tokenOf(made.body.url);
    const seen = async () => (await view(token)).body.request;

    expect(await seen()).toMatchObject({ status: "open", payment: PAYMENT });
    await api(minh, "POST", "/v1/request-links/accept", { token });
    await api(minh, "POST", `/v1/requests/${made.body.request.id}/reported-paid`);
    expect(await seen()).toMatchObject({ status: "reported", payment: PAYMENT });
    await api(lan, "POST", `/v1/requests/${made.body.request.id}/confirmed-paid`);
    expect(await seen()).toMatchObject({ status: "confirmed", payment: null });
  });

  it("shows nothing of how its creditor was paid once the creditor deleted their account, confirmed or not", async () => {
    const lan = await person();
    const minh = await person();
    const paid = await ask(lan, { payment: PAYMENT });
    const open = await ask(lan, { payment: PAYMENT });
    const token = tokenOf(paid.body.url);
    await api(minh, "POST", "/v1/request-links/accept", { token });
    await api(minh, "POST", `/v1/requests/${paid.body.request.id}/reported-paid`);
    await api(lan, "POST", `/v1/requests/${paid.body.request.id}/confirmed-paid`);
    // Deleting an account deletes its customer at RevenueCat first.
    fakeRevenueCat();

    expect((await send(proxyRequest("DELETE", "/v1/account", { session: lan.session }))).status).toBe(204);

    for (const made of [paid, open]) {
      const seen = await view(tokenOf(made.body.url));
      expect(seen.status).toBe(200);
      expect(seen.body.request).toMatchObject({ creditorName: "Former member", payment: null });
    }
    expect((await view(token)).body.request.status).toBe("confirmed");
    expect(await rows("SELECT payment FROM requests")).toEqual([{ payment: null }, { payment: null }]);
  });

  it("answers 404 for a link never made, and 410 once its request was deleted, which empties how it is paid", async () => {
    const lan = await person();
    const made = await ask(lan, { payment: PAYMENT });
    const token = tokenOf(made.body.url);

    for (const unknown of ["AAAAAAAAAAAAAAAAAAAAAA", "too-short"]) expect((await view(unknown)).body).toEqual({ error: "not_found" });
    expect((await api(null, "POST", "/v1/request-links/view", {})).body).toEqual({ error: "input" });
    await api(lan, "DELETE", `/v1/requests/${made.body.request.id}`);

    expect((await view(token)).status).toBe(410);
    expect((await view(token)).body).toEqual({ error: "gone" });
    expect(await rows("SELECT payment FROM requests")).toEqual([{ payment: null }]);
  });

  it("works for 90 days after the request was made, then answers as an expired link, counting no read, while the request stays in both people's apps", async () => {
    const lan = await person();
    const minh = await person();
    const made = await ask(lan, { payment: PAYMENT });
    const id = made.body.request.id;
    const token = tokenOf(made.body.url);
    await api(minh, "POST", "/v1/request-links/accept", { token });
    const madeDaysAgo = (days: number) => env.DB.prepare("UPDATE requests SET created_at = ?2 WHERE id = ?1").bind(id, Date.now() - days * DAY).run();
    const reads = () => rows("SELECT used FROM rate_windows WHERE kind = 'web_read'");

    await madeDaysAgo(89);
    const seen = await view(token);
    expect(seen.status).toBe(200);
    expect(seen.body.request).toMatchObject({ status: "open", payment: PAYMENT });

    await madeDaysAgo(91);
    const counted = await reads();
    const expired = await view(token);
    expect(expired.status).toBe(410);
    expect(expired.body).toEqual({ error: "gone" });
    expect(await reads()).toEqual(counted);

    // In the app, the request and its routes are as they were, for both people.
    const listed = async (who: Person) => (await api<{ requests: RequestView[] }>(who, "GET", "/v1/requests")).body.requests.map((request) => request.id);
    expect(await listed(lan)).toEqual([id]);
    expect(await listed(minh)).toEqual([id]);
    expect((await api<RequestView>(minh, "POST", "/v1/request-links/open", { token })).body).toMatchObject({ id, method: METHOD });
    expect((await api<RequestView>(minh, "POST", `/v1/requests/${id}/reported-paid`)).status).toBe(200);
    expect((await api<RequestView>(lan, "POST", `/v1/requests/${id}/confirmed-paid`)).body).toMatchObject({ confirmedPaidAt: expect.any(Number) });
  });

  it("takes only a payment whose link is https with a host or a UPI payment, and whose fields follow the contract", async () => {
    const lan = await person();
    for (const payment of [
      { ...PAYMENT, link: "http://pay.example/lan" },
      { ...PAYMENT, link: "javascript:alert(1)" },
      { ...PAYMENT, details: "" },
      { ...PAYMENT, qrPayload: "q".repeat(POLICY.maxPaymentCodeChars + 1) },
      { ...PAYMENT, amountFilledIn: undefined },
      "upi://pay?pa=lan@okbank",
    ]) {
      const refused = await ask(lan, { payment });
      expect(refused.status, JSON.stringify(payment).slice(0, 80)).toBe(400);
      expect(refused.body).toEqual({ error: "input" });
    }
    const upi = { link: "upi://pay?pa=lan@okbank&pn=Lan&am=150.00&cu=INR", qrPayload: null, details: "UPI ID: lan@okbank", amountFilledIn: true };
    const made = await ask(lan, { currency: { code: "INR", fractionDigits: 2 }, payment: upi });
    expect(made.status).toBe(201);
    expect((await view(tokenOf(made.body.url))).body.request.payment).toEqual(upi);
    expect((await ask(lan, { payment: null })).status).toBe(201);
  });
});

describe("paying", () => {
  async function accepted() {
    const lan = await person();
    const made = await ask(lan);
    const minh = await person();
    await api(minh, "POST", "/v1/request-links/accept", { token: tokenOf(made.body.url) });
    return { lan, minh, id: made.body.request.id };
  }

  it("is reported once by the debtor and confirmed once by the creditor", async () => {
    const { lan, minh, id } = await accepted();

    const reported = await api<RequestView>(minh, "POST", `/v1/requests/${id}/reported-paid`);
    const reportedAgain = await api<RequestView>(minh, "POST", `/v1/requests/${id}/reported-paid`);
    const confirmed = await api<RequestView>(lan, "POST", `/v1/requests/${id}/confirmed-paid`);
    const confirmedAgain = await api<RequestView>(lan, "POST", `/v1/requests/${id}/confirmed-paid`);

    expect(reported.status).toBe(200);
    expect(reported.body).toMatchObject({ role: "debtor", reportedPaidAt: expect.any(Number), confirmedPaidAt: null });
    expect(reportedAgain.body.reportedPaidAt).toBe(reported.body.reportedPaidAt);
    expect(confirmed.body).toMatchObject({ role: "creditor", reportedPaidAt: reported.body.reportedPaidAt, confirmedPaidAt: expect.any(Number) });
    expect(confirmedAgain.body.confirmedPaidAt).toBe(confirmed.body.confirmedPaidAt);
  });

  it("cannot be reported by the creditor or confirmed by the debtor, and a stranger finds no request", async () => {
    const { lan, minh, id } = await accepted();
    const stranger = await person();

    expect((await api(lan, "POST", `/v1/requests/${id}/reported-paid`)).body).toEqual({ error: "forbidden" });
    expect((await api(minh, "POST", `/v1/requests/${id}/confirmed-paid`)).body).toEqual({ error: "forbidden" });
    expect((await api(minh, "PATCH", `/v1/requests/${id}`, { remindEveryDays: 7 })).body).toEqual({ error: "forbidden" });
    expect((await api(minh, "DELETE", `/v1/requests/${id}`)).body).toEqual({ error: "forbidden" });
    for (const [method, path, body] of [
      ["POST", `/v1/requests/${id}/reported-paid`],
      ["POST", `/v1/requests/${id}/confirmed-paid`],
      ["PATCH", `/v1/requests/${id}`, { remindEveryDays: 7 }],
      ["DELETE", `/v1/requests/${id}`],
    ] as const) {
      expect((await api(stranger, method, path, body)).body, `${method} ${path}`).toEqual({ error: "not_found" });
    }
    expect(await rows("SELECT reported_paid_at, confirmed_paid_at, remind_every_days FROM requests")).toEqual([
      { reported_paid_at: null, confirmed_paid_at: null, remind_every_days: 0 },
    ]);
  });

  it("confirmed in a group records exactly one settlement, from the payer's member to the creditor's", async () => {
    const { owner, group, minh, minhId } = await vndGroup();
    const made = await ask(owner, { groupId: group.id, toMemberId: minhId, amountMinor: 250_000 });
    const id = made.body.request.id;
    await api(minh, "POST", `/v1/requests/${id}/reported-paid`);
    const before = await stateOf(group);

    const [first, second] = await Promise.all([
      api<RequestView>(owner, "POST", `/v1/requests/${id}/confirmed-paid`),
      api<RequestView>(owner, "POST", `/v1/requests/${id}/confirmed-paid`),
    ]);
    const third = await api<RequestView>(owner, "POST", `/v1/requests/${id}/confirmed-paid`);

    expect([first.status, second.status, third.status]).toEqual([200, 200, 200]);
    expect(new Set([first.body.confirmedPaidAt, second.body.confirmedPaidAt, third.body.confirmedPaidAt]).size).toBe(1);
    const after = await stateOf(group);
    expect(after.settlements).toEqual([
      {
        id: expect.any(String),
        fromMemberId: minhId,
        toMemberId: group.ownerMemberId,
        amountMinor: 250_000,
        recordedByMemberId: group.ownerMemberId,
        requestId: id,
        createdAt: first.body.confirmedPaidAt,
      },
    ]);
    expect(after.log[0]).toEqual({
      at: first.body.confirmedPaidAt,
      kind: "settlement_recorded",
      memberId: group.ownerMemberId,
      subjectId: after.settlements[0].id,
    });
    expect(after.group.revision).toBe(before.group.revision + 1);
  });

  it("confirmed while its creditor withdraws it records nothing, whichever lands first", async () => {
    const { owner, group, minhId } = await vndGroup();
    const fcm = await fakeFcm();
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const made = await ask(owner, { groupId: group.id, toMemberId: minhId });
    const id = made.body.request.id;
    const before = await stateOf(group);
    // The withdrawal lands just before the confirmation's own batch, after the confirmation read the request.
    let withdrawn: number | null = null;
    const racing = new Proxy(env.DB, {
      get(target, property) {
        if (property !== "batch") {
          const value: unknown = Reflect.get(target, property);
          return typeof value === "function" ? value.bind(target) : value;
        }
        return async (statements: D1PreparedStatement[]) => {
          // A prepared statement keeps its SQL as `statement`; the test reads it to find the confirmation's batch.
          const sql = statements.map((statement) => (statement as unknown as { statement: string }).statement);
          if (withdrawn === null && sql.some((text) => text.startsWith("UPDATE requests SET confirmed_paid_at"))) {
            withdrawn = (await api(owner, "DELETE", `/v1/requests/${id}`)).status;
          }
          return target.batch(statements);
        };
      },
    });

    const confirmed = await api(owner, "POST", `/v1/requests/${id}/confirmed-paid`, undefined, { ...settings, DB: racing });

    expect(withdrawn).toBe(204);
    expect(confirmed.status).toBe(404);
    expect(await rows("SELECT * FROM settlements")).toEqual([]);
    expect(await rows("SELECT confirmed_paid_at, deleted_at IS NOT NULL AS withdrawn FROM requests")).toEqual([{ confirmed_paid_at: null, withdrawn: 1 }]);
    const after = await stateOf(group);
    expect(after.settlements).toEqual([]);
    expect(after.log.filter((entry) => entry.kind === "settlement_recorded")).toEqual([]);
    expect(after.members).toEqual(before.members);
    expect(fcm.messages).toEqual([]);
  });

  it("withdrawn after it was confirmed stays confirmed, with its one settlement", async () => {
    const { owner, group, minhId } = await vndGroup();
    const id = (await ask(owner, { groupId: group.id, toMemberId: minhId })).body.request.id;

    const [confirmed, withdrawn] = await Promise.all([
      api<RequestView>(owner, "POST", `/v1/requests/${id}/confirmed-paid`),
      new Promise((resolve) => setTimeout(resolve, 50)).then(() => api(owner, "DELETE", `/v1/requests/${id}`)),
    ]);

    expect(confirmed.status).toBe(200);
    expect(withdrawn.status).toBe(409);
    expect((await stateOf(group)).settlements.map((settlement) => settlement.requestId)).toEqual([id]);
  });

  it("confirmed outside a group records no settlement", async () => {
    const { owner, group } = await vndGroup();
    const { lan, id } = await accepted();

    expect((await api(lan, "POST", `/v1/requests/${id}/confirmed-paid`)).status).toBe(200);
    expect((await stateOf(group, owner)).settlements).toEqual([]);
    expect(await rows("SELECT * FROM settlements")).toEqual([]);
  });

  it("is reminded as often as the creditor chooses", async () => {
    const { lan, id } = await accepted();

    const changed = await api<RequestView>(lan, "PATCH", `/v1/requests/${id}`, { remindEveryDays: 7 });

    expect(changed.status).toBe(200);
    expect(changed.body.remindEveryDays).toBe(7);
    expect((await api(lan, "PATCH", `/v1/requests/${id}`, { remindEveryDays: 5 })).status).toBe(400);
    expect((await api(lan, "PATCH", `/v1/requests/${crypto.randomUUID()}`, { remindEveryDays: 1 })).status).toBe(404);
  });

  it("is withdrawn by its creditor while it is open, and kept once confirmed", async () => {
    const { lan, minh, id } = await accepted();
    const confirmed = await accepted();
    await api(confirmed.lan, "POST", `/v1/requests/${confirmed.id}/confirmed-paid`);

    expect((await api(confirmed.lan, "DELETE", `/v1/requests/${confirmed.id}`)).body).toEqual({ error: "conflict" });
    const deleted = await api(lan, "DELETE", `/v1/requests/${id}`);
    expect(deleted.status).toBe(204);
    expect((await api(lan, "DELETE", `/v1/requests/${id}`)).status).toBe(204);
    expect((await api<{ requests: unknown[] }>(lan, "GET", "/v1/requests")).body.requests).toEqual([]);
    expect((await api<{ requests: unknown[] }>(minh, "GET", "/v1/requests")).body.requests).toEqual([]);
    expect((await api(minh, "POST", `/v1/requests/${id}/reported-paid`)).status).toBe(404);
    const [kept] = await rows<Record<string, unknown>>("SELECT * FROM requests WHERE id = ?1", id);
    expect(kept).toMatchObject({ method: "{}", payer_name: "", creditor_name: "", title: null, debtor: null, deleted_at: expect.any(Number) });
  });
});

describe("a group that goes", () => {
  it("leaves its requests between their two people, without the group", async () => {
    const { owner, group, minh, minhId } = await vndGroup();
    const made = await ask(owner, { groupId: group.id, toMemberId: minhId, billId: crypto.randomUUID() });
    await api(minh, "POST", `/v1/groups/${group.id}/leave`);

    expect((await api(owner, "POST", `/v1/groups/${group.id}/leave`)).status).toBe(204);

    const left = await api<{ requests: RequestView[] }>(minh, "GET", "/v1/requests");
    expect(left.body.requests).toEqual([{ ...made.body.request, role: "debtor", groupId: null, toMemberId: null, creditorMemberId: null, billId: null }]);
    const confirmed = await api<RequestView>(owner, "POST", `/v1/requests/${made.body.request.id}/confirmed-paid`);
    expect(confirmed.status).toBe(200);
    expect(await rows("SELECT * FROM settlements")).toEqual([]);
    expect(await rows("SELECT * FROM groups")).toEqual([]);
    expect(await env.DB.prepare("SELECT count(*) AS n FROM requests").first("n")).toBe(1);
  });
});

describe("a request confirmed as paid", () => {
  /** Sets when [id] was confirmed paid to [at], as if it had been confirmed then. */
  function confirmedAt(id: string, at: number) {
    return env.DB.prepare("UPDATE requests SET confirmed_paid_at = ?2 WHERE id = ?1").bind(id, at).run();
  }

  it("loses its payment method at the daily run once it was confirmed more than 12 months ago, and keeps its amount, its people and its paid state, so no balance moves", async () => {
    const { owner, group, minh, minhId } = await vndGroup();
    const inGroup = (await ask(owner, { groupId: group.id, toMemberId: minhId, amountMinor: 250_000, payment: PAYMENT })).body.request.id;
    const lan = await person();
    const alone = await ask(lan, { payment: PAYMENT });
    const lately = await ask(lan, { payment: PAYMENT });
    const unpaid = await ask(lan, { payment: PAYMENT });
    for (const made of [alone, lately, unpaid]) await api(minh, "POST", "/v1/request-links/accept", { token: tokenOf(made.body.url) });
    await api(minh, "POST", `/v1/requests/${inGroup}/reported-paid`);
    for (const [creditor, id] of [
      [owner, inGroup],
      [lan, alone.body.request.id],
      [lan, lately.body.request.id],
    ] as const) {
      expect((await api(creditor, "POST", `/v1/requests/${id}/confirmed-paid`)).status).toBe(200);
    }
    const now = Date.now();
    // Two were confirmed 12 months and a day ago, one a day short of 12 months ago; one is still open, made long before.
    await confirmedAt(inGroup, now - POLICY.paidMethodRetentionMs - DAY);
    await confirmedAt(alone.body.request.id, now - POLICY.paidMethodRetentionMs - DAY);
    await confirmedAt(lately.body.request.id, now - POLICY.paidMethodRetentionMs + DAY);
    await env.DB.prepare("UPDATE requests SET created_at = ?2 WHERE id = ?1").bind(unpaid.body.request.id, now - 2 * POLICY.paidMethodRetentionMs).run();
    const listed = async (who: Person) => (await api<{ requests: RequestView[] }>(who, "GET", "/v1/requests")).body.requests;
    const before = { group: await stateOf(group), owners: await listed(owner), lans: await listed(lan), minhs: await listed(minh) };

    await forgetExpired(env, now);

    // Both people keep every request: those paid long ago without the method they carried, and nothing else changed.
    const forgotten = new Set([inGroup, alone.body.request.id]);
    const emptied = (requests: RequestView[]) => requests.map((request) => (forgotten.has(request.id) ? { ...request, method: {} } : request));
    expect(await listed(owner)).toEqual(emptied(before.owners));
    expect(await listed(lan)).toEqual(emptied(before.lans));
    expect(await listed(minh)).toEqual(emptied(before.minhs));
    expect((await listed(minh)).filter((request) => request.confirmedPaidAt !== null)).toHaveLength(3);
    // The group's settlement, and so every balance, stays as it was.
    const after = await stateOf(group);
    expect(after).toEqual(before.group);
    expect(after.settlements).toEqual([expect.objectContaining({ fromMemberId: minhId, toMemberId: group.ownerMemberId, amountMinor: 250_000, requestId: inGroup })]);
    expect(await rows("SELECT id, method, payment FROM requests ORDER BY created_at, id")).toEqual(
      expect.arrayContaining([
        { id: inGroup, method: "{}", payment: null },
        { id: alone.body.request.id, method: "{}", payment: null },
        { id: lately.body.request.id, method: JSON.stringify(METHOD), payment: JSON.stringify(PAYMENT) },
        { id: unpaid.body.request.id, method: JSON.stringify(METHOD), payment: JSON.stringify(PAYMENT) },
      ]),
    );
  });
});
