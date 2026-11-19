import { runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { decodeJwt, errors, exportJWK, generateKeyPair, SignJWT } from "jose";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import type { GroupState } from "../src/group-state";
import { ALLOWANCES } from "../src/identity";
import type { Env } from "../src/index";
import { hashed } from "../src/ledger";
import { POLICY } from "../src/policy";
import { nextMonthStart, periodOf, type QuotaBook } from "../src/quota-book";
import { forgetExpired } from "../src/reminders";
import type { RequestView } from "../src/requests";
import { issueSession, verdictOf } from "../src/session";
import { type FakeRevenueCat, fakeRevenueCat, playCustomer, TEST_KEY } from "./fake-revenuecat";
import { fakeTurnstile } from "./fake-turnstile";
import { network } from "./network";
import { ANSWER, billRequest, IP, last, model, proxyRequest, quotaOf, send, spendOf } from "./requests";
import { newClaim, openShare, publish } from "./share-fixtures";
import {
  addBill,
  addPlaceholder,
  api,
  invite,
  join,
  linkToken,
  logsOf,
  memberView,
  newGroup,
  type Person,
  person,
  rows,
  signedInAgain,
  stateOf,
  THB,
  VND,
} from "./shared";

const CLIENT_ID = "test-web-client.apps.googleusercontent.com";
const GOOGLE_SUB = "109876543210987654321";
const google = await generateKeyPair("RS256");
const stranger = await generateKeyPair("RS256");
const googleKeys = { keys: [{ ...(await exportJWK(google.publicKey)), kid: "google-test", alg: "RS256", use: "sig" }] };

/** RevenueCat, which every deletion of an account asks first. */
let revenueCat: FakeRevenueCat;

beforeEach(() => {
  network.use(http.get("https://www.googleapis.com/oauth2/v3/certs", () => HttpResponse.json(googleKeys)));
  revenueCat = fakeRevenueCat();
});

function bookOf(account: string) {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(`google:${account}`));
}

/** Everything the account's QuotaBook and spend ledger hold, by key. */
async function heldFor(account: string): Promise<{ book: Record<string, unknown>; ledger: Record<string, unknown> }> {
  const all = async (state: DurableObjectState) => Object.fromEntries(await state.storage.list());
  return {
    book: await runInDurableObject(bookOf(account), (_, state) => all(state)),
    ledger: await runInDurableObject(env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(`google:${account}`)), (_, state) => all(state)),
  };
}

type IdTokenChange = { key?: CryptoKey; issuer?: string; audience?: string; expires?: number | string; subject?: string };

/** An ID token as Google signs one for the app at sign-in, with [change] made to it. */
function idToken(change: IdTokenChange = {}): Promise<string> {
  return new SignJWT({ email: "person@example.com", email_verified: true })
    .setProtectedHeader({ alg: "RS256", kid: "google-test" })
    .setIssuer(change.issuer ?? "https://accounts.google.com")
    .setAudience(change.audience ?? CLIENT_ID)
    .setSubject(change.subject ?? GOOGLE_SUB)
    .setIssuedAt()
    .setExpirationTime(change.expires ?? "1h")
    .sign(change.key ?? google.privateKey);
}

function signIn(token: string, settings: Partial<typeof env> = {}) {
  return send(proxyRequest("POST", "/v1/session", {}, JSON.stringify({ idToken: token })), settings);
}

async function accountSession(now: number = Date.now(), settings: Partial<typeof env> = {}) {
  const account = await hashed(crypto.randomUUID(), env.HASH_SALT);
  const { session } = await issueSession(account, { ...env, ...settings }, now);
  return { account, session };
}

function monthly(used: number) {
  const now = Date.now();
  const period = periodOf({ kind: "monthly", limit: 10 }, now);
  return { limit: POLICY.monthlyCalls, used, remaining: POLICY.monthlyCalls - used, period, resetsAt: nextMonthStart(now), plan: "free" };
}

describe("signing in", () => {
  it("trades a Google ID token for a session that reads bills on the account's monthly calls", async () => {
    const signedIn = await signIn(await idToken());

    expect(signedIn.status).toBe(200);
    const { session, expiresAt, usage, purchaser } = JSON.parse(signedIn.body) as { session: string; expiresAt: number; usage: unknown; purchaser: string };
    expect(usage).toEqual(monthly(0));
    expect(expiresAt).toBeGreaterThan(Date.now() + POLICY.sessionMs - 60_000);
    // The purchaser is the account the session stands for, recorded as handed out.
    expect(purchaser).toBe(await hashed(GOOGLE_SUB, env.HASH_SALT));
    expect(decodeJwt(session).sub).toBe(purchaser);
    expect(await bookOf(purchaser).purchaserIssued()).toBe(true);

    model(ANSWER);
    const read = await send(billRequest({ session }));
    expect(last(read)).toMatchObject({ type: "result", usage: monthly(1) });
  });

  it("names the account by a salted hash of its Google id, never by the id itself", async () => {
    const { session } = JSON.parse((await signIn(await idToken())).body) as { session: string };

    const claims = decodeJwt(session);
    expect(claims.sub).toBe(await hashed(GOOGLE_SUB, env.HASH_SALT));
    expect(session).not.toContain(GOOGLE_SUB);
    expect(JSON.stringify(claims)).not.toContain("person@example.com");
  });

  it("refuses an ID token Google did not sign, that has run out, or that is meant for another app or comes from elsewhere", async () => {
    const tokens = [
      await idToken({ key: stranger.privateKey }),
      await idToken({ expires: Math.floor(Date.now() / 1000) - 60 }),
      await idToken({ audience: "another-app.apps.googleusercontent.com" }),
      await idToken({ issuer: "https://accounts.example.com" }),
      "not.a.token",
    ];

    for (const token of tokens) {
      const sent = await signIn(token);
      expect(sent.status).toBe(401);
      expect(JSON.parse(sent.body)).toEqual({ error: "id_token" });
    }
  });

  it("tells a bad token from Google's keys being out of reach", () => {
    for (const fault of [
      new errors.JWTExpired("expired", {}),
      new errors.JWTClaimValidationFailed("wrong audience", {}),
      new errors.JWSSignatureVerificationFailed(),
      new errors.JWSInvalid(),
      new errors.JWKSNoMatchingKey(),
    ]) {
      expect(verdictOf(fault)).toBe("invalid");
    }
    for (const outage of [new errors.JWKSTimeout(), new errors.JOSEError("Expected 200 OK from the JSON Web Key Set HTTP response"), new TypeError("fetch failed")]) {
      expect(verdictOf(outage)).toBe("unavailable");
    }
  });

  it("refuses a body without an ID token", async () => {
    for (const body of ["{}", '{"idToken": 42}', "not json"]) {
      const sent = await send(proxyRequest("POST", "/v1/session", {}, body));
      expect(sent.status).toBe(400);
    }
  });

  it("stays off until the owner sets the app's client ID and a long enough signing key", async () => {
    const token = await idToken();

    const offs: Array<Partial<typeof env>> = [{ GOOGLE_CLIENT_ID: "" }, { SESSION_SIGNING_KEY: "too short" }, { SESSION_SIGNING_KEY: undefined }];
    for (const settings of offs) {
      const sent = await signIn(token, settings);
      expect(sent.status).toBe(503);
      expect(JSON.parse(sent.body)).toEqual({ error: "sign_in_off" });
    }
  });
});

describe("a session", () => {
  it("is refused when it was changed, signed with another key, or has run out", async () => {
    const { session } = await accountSession();
    const [head, payload, signature] = session.split(".");
    const changed = `${head}.${payload.slice(0, -2)}${payload.endsWith("A") ? "B" : "A"}${payload.slice(-1)}.${signature}`;
    const otherKey = (await accountSession(Date.now(), { SESSION_SIGNING_KEY: "another-signing-key-that-is-long-enough" })).session;
    const expired = (await accountSession(Date.now() - POLICY.sessionMs - 60_000)).session;
    const seen = model(ANSWER);

    for (const bad of [changed, otherKey, expired, "not-a-session"]) {
      for (const request of [billRequest({ session: bad }), proxyRequest("GET", "/v1/usage", { session: bad })]) {
        const sent = await send(request);
        expect(sent.status).toBe(401);
        expect(JSON.parse(sent.body)).toEqual({ error: "session" });
      }
    }
    expect(seen).toHaveLength(0);
  });

  it("gives an account ten calls a month and refuses the eleventh until the next month begins", async () => {
    const { session } = await accountSession();
    model(ANSWER);
    for (let i = 0; i < POLICY.monthlyCalls; i++) {
      expect(last(await send(billRequest({ session, ip: `198.51.100.${i + 1}` })))).toMatchObject({ type: "result" });
    }

    const eleventh = await send(billRequest({ session, ip: "198.51.100.200" }));

    expect(eleventh.status).toBe(429);
    expect(JSON.parse(eleventh.body)).toEqual({ error: "quota_exhausted", usage: monthly(10) });
    const wait = Number(eleventh.headers.get("retry-after"));
    expect(Math.abs(wait - (nextMonthStart(Date.now()) - Date.now()) / 1000)).toBeLessThan(5);
  });
});

describe("the calls left", () => {
  it("are reported for an install and for an account, with whether AI is on, and the account's purchaser", async () => {
    const installId = crypto.randomUUID();
    const install = await send(proxyRequest("GET", "/v1/usage", { install: installId }));
    expect(install.status).toBe(200);
    expect(JSON.parse(install.body)).toEqual({
      usage: { limit: 3, used: 0, remaining: 3, period: "trial", resetsAt: null, plan: "trial" },
      aiEnabled: true,
    });
    expect(await env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(`install:${installId}`)).purchaserIssued()).toBe(false);

    const { account, session } = await accountSession();
    const signedIn = await send(proxyRequest("GET", "/v1/usage", { session }), { AI_ENABLED: "false" });
    expect(JSON.parse(signedIn.body)).toEqual({ usage: monthly(0), aiEnabled: false, purchaser: account });
    expect(await bookOf(account).purchaserIssued()).toBe(true);
  });

  it("need an install id or a session", async () => {
    const sent = await send(proxyRequest("GET", "/v1/usage", { install: null }));

    expect(sent.status).toBe(401);
    expect(JSON.parse(sent.body)).toEqual({ error: "install" });
  });
});

/**
 * The QuotaBooks as the Worker finds them while they still run the code from before Pro, for a few
 * minutes after the deploy that brought it: a call of `issuePurchaser`, which that code lacks, fails
 * as the runtime fails a call of a method an object does not have. Everything else is the real
 * object's.
 */
function booksFromBeforePro(): DurableObjectNamespace<QuotaBook> {
  return new Proxy(env.QUOTA_BOOK, {
    get(books, property) {
      if (property === "get") {
        return (id: DurableObjectId) =>
          new Proxy(books.get(id), {
            get(book, method) {
              if (method === "issuePurchaser") return () => Promise.reject(new TypeError('The RPC receiver does not implement the method "issuePurchaser".'));
              return Reflect.get(book, method);
            },
          });
      }
      const value: unknown = Reflect.get(books, property);
      return typeof value === "function" ? value.bind(books) : value;
    },
  });
}

describe("while an account's QuotaBook still runs the code from before Pro", () => {
  it("signs in, with the calls left and no purchaser, which is not recorded until a later call hands it out", async () => {
    const { result: signedIn, lines } = await logsOf(async () => signIn(await idToken(), { QUOTA_BOOK: booksFromBeforePro() }));

    expect(signedIn.status).toBe(200);
    const body = JSON.parse(signedIn.body) as { session: string; usage: unknown };
    expect(Object.keys(body).sort()).toEqual(["expiresAt", "session", "usage"]);
    expect(body.usage).toEqual(monthly(0));
    const account = await hashed(GOOGLE_SUB, env.HASH_SALT);
    expect(await bookOf(account).purchaserIssued()).toBe(false);
    expect(lines.map((line) => JSON.parse(line) as unknown)).toEqual([{ event: "purchaser_not_recorded" }]);

    // Once the object runs this code, the next call records the purchaser and hands it out.
    const read = await send(proxyRequest("GET", "/v1/usage", { session: body.session }));
    expect(JSON.parse(read.body)).toEqual({ usage: monthly(0), aiEnabled: true, purchaser: account });
    expect(await bookOf(account).purchaserIssued()).toBe(true);
  });

  it("reports the calls left without a purchaser, and records none", async () => {
    const { account, session } = await accountSession();
    model(ANSWER);
    expect(last(await send(billRequest({ session })))).toMatchObject({ type: "result" });

    const { result: read, lines } = await logsOf(() => send(proxyRequest("GET", "/v1/usage", { session }), { QUOTA_BOOK: booksFromBeforePro() }));

    expect(read.status).toBe(200);
    expect(JSON.parse(read.body)).toEqual({ usage: monthly(1), aiEnabled: true });
    expect(await bookOf(account).purchaserIssued()).toBe(false);
    expect(lines.map((line) => JSON.parse(line) as unknown)).toEqual([{ event: "purchaser_not_recorded" }]);
  });

  it("fails a refresh of the plan with 500, and asks RevenueCat nothing about the id it could not record", async () => {
    const who = await person();

    const { result: refreshed, lines } = await logsOf(() => api(who, "POST", "/v1/plan", {}, { QUOTA_BOOK: booksFromBeforePro() }));

    expect(refreshed.status).toBe(500);
    expect(refreshed.body).toEqual({ error: "internal" });
    expect(lines.map((line) => JSON.parse(line) as unknown)).toEqual([{ event: "request_failed", error: "TypeError" }]);
    expect(revenueCat.asked).toEqual([]);
    expect(await bookOf(who.account).purchaserIssued()).toBe(false);
  });
});

/** Someone signed in with Google: the `sub` Google names them by, the account the proxy names them by, and a session for it. */
type GooglePerson = Person & { sub: string };

async function googlePerson(): Promise<GooglePerson> {
  const sub = `1${crypto.getRandomValues(new Uint32Array(3)).join("")}`;
  const account = await hashed(sub, env.HASH_SALT);
  return { sub, account, session: (await issueSession(account, env, Date.now())).session };
}

/** A deletion as the page /delete-account asks for it: the ID token Google gave the page, without a session, an install id or a version. */
function deleteWithGoogle(token: string, settings: Partial<Env> = {}) {
  const headers = new Headers({ "content-type": "application/json", "CF-Connecting-IP": IP });
  return send(new Request("https://proxy.test/v1/account/delete-with-google", { method: "POST", headers, body: JSON.stringify({ idToken: token }) }), settings);
}

/** The two routes that delete an account: with the app's session, and with Google from the page. They delete alike. */
const DELETIONS = [
  {
    route: "DELETE /v1/account",
    remove: (who: GooglePerson, settings: Partial<Env> = {}) => send(proxyRequest("DELETE", "/v1/account", { session: who.session }), settings),
  },
  {
    route: "POST /v1/account/delete-with-google",
    remove: async (who: GooglePerson, settings: Partial<Env> = {}) => deleteWithGoogle(await idToken({ subject: who.sub }), settings),
  },
];

/** An account on Pro that read a bill and made a group. */
async function activeAccount() {
  const who = await googlePerson();
  revenueCat.hold(who.account, playCustomer({ expires: Date.now() + 20 * POLICY.dayMs }));
  expect((await api(who, "POST", "/v1/plan")).body).toMatchObject({ usage: { plan: "pro" } });
  model(ANSWER);
  expect(last(await send(billRequest({ session: who.session })))).toMatchObject({ type: "result" });
  return { who, group: await newGroup(who) };
}

/** The count an account's QuotaBook keeps of the month of [now], once [calls] bill reads were charged. */
function monthsCount(calls: number, now: number = Date.now()) {
  return { period: periodOf(ALLOWANCES.monthly, now), hundredths: calls * 100, used: calls };
}

/** What each member's balance is made of: their shares and payments of bills, and the settlements they paid and received. */
function sums(state: GroupState) {
  const sums: Record<string, { shares: number; paid: number; sent: number; received: number }> = {};
  const of = (id: string) => (sums[id] ??= { shares: 0, paid: 0, sent: 0, received: 0 });
  for (const member of state.members) of(member.id);
  for (const bill of state.bills) {
    for (const [id, amount] of Object.entries(bill.shares)) of(id).shares += amount;
    for (const [id, amount] of Object.entries(bill.paid)) of(id).paid += amount;
  }
  for (const settlement of state.settlements) {
    of(settlement.fromMemberId).sent += settlement.amountMinor;
    of(settlement.toMemberId).received += settlement.amountMinor;
  }
  return sums;
}

/** Each member's balance: what they paid and sent, less their shares and what they received. */
function balances(state: GroupState): Record<string, number> {
  return Object.fromEntries(Object.entries(sums(state)).map(([id, sum]) => [id, sum.paid + sum.sent - sum.shares - sum.received]));
}

/** A payment request [creditor] makes, with how to pay it for its web page. */
function newRequest(creditor: Person, extra: Record<string, unknown> = {}) {
  return api<{ request: RequestView; url: string }>(creditor, "POST", "/v1/requests", {
    id: crypto.randomUUID(),
    amountMinor: 70_000,
    currency: VND,
    payerName: "Minh",
    creditorName: "Lan",
    title: "Groceries",
    method: { kind: "VietQr", bankBin: "970436", accountNumber: "0123456789" },
    payment: { link: "https://paypal.me/lan/70000VND", qrPayload: null, details: "PayPal: paypal.me/lan", amountFilledIn: true },
    ...extra,
  });
}

// The account deletion matrix: one test for each row of what the proxy holds, through each of the two routes.
describe.each(DELETIONS)("deleting an account through $route", ({ remove }) => {
  it("devices that get notifications: are deleted", async () => {
    const who = await googlePerson();
    const other = await person();
    for (const [owner, token] of [
      [who, "token-of-the-account-1"],
      [who, "token-of-the-account-2"],
      [other, "token-of-someone-else"],
    ] as const) {
      expect((await api(owner, "PUT", `/v1/devices/${crypto.randomUUID()}`, { token })).status).toBe(204);
    }

    expect((await remove(who)).status).toBe(204);

    expect(await rows("SELECT account, token FROM devices")).toEqual([{ account: other.account, token: "token-of-someone-else" }]);
  });

  it("the calls used this month: stay in its QuotaBook until the month ends, alone, so that signing in again starts no new allowance", async () => {
    const { who } = await activeAccount();
    model(ANSWER);
    expect(last(await send(billRequest({ session: who.session, ip: "198.51.100.9" })))).toMatchObject({ type: "result" });
    expect(Object.keys((await heldFor(who.account)).book).filter((key) => !key.startsWith("request:"))).toEqual(["book", "issued", "plan"]);
    const now = Date.now();

    expect((await remove(who)).status).toBe(204);

    expect((await heldFor(who.account)).book).toEqual({ book: monthsCount(2, now) });
    expect(await runInDurableObject(bookOf(who.account), (_, state) => state.storage.getAlarm())).toBe(nextMonthStart(now));
    // Signing in again with the same Google account finds the month's calls used, on the free plan.
    const again = await signIn(await idToken({ subject: who.sub }));
    expect(again.status).toBe(200);
    expect(JSON.parse(again.body)).toMatchObject({ usage: monthly(2), purchaser: who.account });
  });

  it("the plan and the record of the purchaser: are deleted", async () => {
    const { who } = await activeAccount();
    expect(await bookOf(who.account).purchaserIssued()).toBe(true);

    expect((await remove(who)).status).toBe(204);

    expect(await bookOf(who.account).purchaserIssued()).toBe(false);
    expect(Object.keys((await heldFor(who.account)).book)).toEqual(["book"]);
    expect(await quotaOf(`google:${who.account}`)).toEqual(monthly(1));
  });

  it("the customer at RevenueCat: is deleted before anything else", async () => {
    const { who, group } = await activeAccount();
    const before = { held: await heldFor(who.account), group: await stateOf(group) };
    let atDeletion: typeof before | undefined;
    revenueCat.answer = async (asked) => {
      if (asked.method === "DELETE") atDeletion = { held: await heldFor(who.account), group: await stateOf(group) };
      return undefined;
    };

    expect((await remove(who)).status).toBe(204);

    expect(revenueCat.asked.filter((asked) => asked.method === "DELETE")).toEqual([
      { method: "DELETE", id: who.account, authorization: `Bearer ${TEST_KEY}`, platform: null },
    ]);
    expect(atDeletion).toEqual(before);
    expect(revenueCat.has(who.account)).toBe(false);
    expect(await rows("SELECT id FROM members WHERE account = ?1", who.account)).toEqual([]);
  });

  it("the customer at RevenueCat: when RevenueCat does not take the deletion, nothing is deleted, so that it can be asked again", async () => {
    const { who, group } = await activeAccount();
    const before = { held: await heldFor(who.account), group: await stateOf(group) };
    const failures = [
      () => HttpResponse.json({ code: 7110, message: "Internal server error." }, { status: 500 }),
      () => HttpResponse.error(),
      () => HttpResponse.json({ code: 7731, message: "Too many requests." }, { status: 429 }),
      () => HttpResponse.json({}, { status: 201 }),
    ];

    for (const failure of failures) {
      revenueCat.answer = (asked) => (asked.method === "DELETE" ? failure() : undefined);
      const sent = await remove(who);
      expect(sent.status).toBe(502);
      expect(JSON.parse(sent.body)).toEqual({ error: "subscription_unavailable" });
    }

    expect({ held: await heldFor(who.account), group: await stateOf(group) }).toEqual(before);
    expect(revenueCat.has(who.account)).toBe(true);
    revenueCat.answer = undefined;
    expect((await remove(who)).status).toBe(204);
    expect(await heldFor(who.account)).toEqual({ book: { book: monthsCount(1) }, ledger: {} });
  });

  it("the spend counter: is deleted", async () => {
    const who = await googlePerson();
    model(ANSWER);
    expect(last(await send(billRequest({ session: who.session })))).toMatchObject({ type: "result" });
    expect(await spendOf(`google:${who.account}`)).toMatchObject({ held: 0 });

    expect((await remove(who)).status).toBe(204);

    expect((await heldFor(who.account)).ledger).toEqual({});
  });

  it("the hourly counters: stay until the daily cleanup, under the account's salted hash alone", async () => {
    const who = await googlePerson();
    const group = await newGroup(who);
    await invite(group);
    expect((await newRequest(who)).status).toBe(201);

    expect((await remove(who)).status).toBe(204);

    const kept = ["account_delete", "group_write", "invite", "request"].map((kind) => ({ account: who.account, kind }));
    expect(await rows("SELECT account, kind FROM rate_windows ORDER BY kind")).toEqual(kept);
    // Once their hour is over, the daily cleanup forgets them.
    await env.DB.prepare("UPDATE rate_windows SET started_at = started_at - ?1").bind(POLICY.hourMs).run();
    await forgetExpired(env, Date.now());
    expect(await rows("SELECT * FROM rate_windows")).toEqual([]);
  });

  it("membership of groups and the groups' bills: become a member who left, every amount kept, so no other balance moves, and its groups and bills pass on", async () => {
    const lan = await googlePerson();
    const flat = await newGroup(lan, "Flat 4B", VND);
    const lanId = flat.ownerMemberId;
    const minh = await person();
    const minhId = await join(flat, minh, "Minh");
    const khoa = await person();
    const khoaId = await join(flat, khoa, "Khoa");
    const hoaId = await addPlaceholder(flat, "Hoa");
    const lansBill = await addBill(flat, lan, { [lanId]: 300, [minhId]: 300, [khoaId]: 200, [hoaId]: 200 }, { [lanId]: 1_000 });
    const minhsBill = await addBill(flat, minh, { [lanId]: 500, [minhId]: 500 }, { [minhId]: 1_000 });
    await api(lan, "POST", `/v1/groups/${flat.id}/settlements`, { id: crypto.randomUUID(), fromMemberId: minhId, amountMinor: 150 });
    await api(khoa, "POST", `/v1/groups/${flat.id}/settlements`, { id: crypto.randomUUID(), fromMemberId: lanId, amountMinor: 100 });
    // A group someone else owns, where Lan has a bill; a group nobody else with an account is in;
    // and a claim waiting in a third group.
    const trip = await newGroup(await person(), "Chiang Mai trip", THB);
    const lanInTrip = await join(trip, lan, "Lan");
    const lansTripBill = await addBill(trip, lan, { [trip.ownerMemberId]: 400, [lanInTrip]: 400 }, { [lanInTrip]: 800 });
    const alone = await newGroup(lan, "Just me", VND);
    const somebody = await addPlaceholder(alone, "Somebody");
    await api(lan, "POST", `/v1/groups/${alone.id}/settlements`, { id: crypto.randomUUID(), fromMemberId: somebody, amountMinor: 100 });
    const waiting = await newGroup(await person());
    const placeholder = await addPlaceholder(waiting, "Lan?");
    expect((await api(lan, "POST", "/v1/invites/join", { token: await invite(waiting), claimMemberId: placeholder, name: "Lan" })).status).toBe(202);
    const before = { flat: await stateOf(flat, minh), trip: await stateOf(trip) };

    expect((await remove(lan)).status).toBe(204);

    const after = await stateOf(flat, minh);
    expect(after.members.find((member) => member.id === lanId)).toEqual(memberView(lanId, "Former member", false));
    expect(after.log[0]).toEqual({ at: expect.any(Number), kind: "member_left", memberId: lanId, subjectId: lanId });
    expect(sums(after)).toEqual(sums(before.flat));
    expect(after.settlements).toEqual(before.flat.settlements);
    for (const staying of [minh, khoa]) expect(balances(await stateOf(flat, staying)), staying.account).toEqual(balances(before.flat));
    expect(after.group.ownerMemberId).toBe(minhId);
    expect(after.bills.map((bill) => [bill.id, bill.ownerMemberId])).toEqual([
      [lansBill, minhId],
      [minhsBill, minhId],
    ]);
    const tripAfter = await stateOf(trip);
    expect(balances(tripAfter)).toEqual(balances(before.trip));
    expect(tripAfter.bills.find((bill) => bill.id === lansTripBill)?.ownerMemberId).toBe(trip.ownerMemberId);
    expect(await rows("SELECT id FROM groups WHERE id = ?1", alone.id)).toEqual([]);
    expect(await rows("SELECT id FROM settlements WHERE group_id = ?1", alone.id)).toEqual([]);
    expect((await stateOf(waiting)).claims).toEqual([]);
    for (const table of ["groups", "members", "claims", "group_bills", "settlements", "group_log"]) {
      expect(JSON.stringify(await rows(`SELECT * FROM ${table}`)), table).not.toContain(lan.account);
    }
    const again = await signedInAgain(lan);
    expect((await api(again, "GET", "/v1/groups")).body).toEqual({ groups: [] });
    expect((await api(again, "GET", `/v1/groups/${flat.id}`)).status).toBe(403);
  });

  it("payment requests not confirmed: lose how the account is paid, and the other side keeps every request", async () => {
    const lan = await googlePerson();
    const owner = await person();
    const group = await newGroup(owner, "Flat 4B", VND);
    const lanId = await join(group, lan, "Lan");
    const minh = await person();
    const minhId = await join(group, minh, "Minh");
    const open = (await newRequest(lan, { groupId: group.id, toMemberId: minhId })).body;
    const paid = (await newRequest(lan)).body;
    await api(minh, "POST", "/v1/request-links/accept", { token: linkToken(paid.url) });
    await api(lan, "POST", `/v1/requests/${paid.request.id}/confirmed-paid`);
    const owed = (await newRequest(owner, { groupId: group.id, toMemberId: lanId, creditorName: "Owner" })).body.request;

    expect((await remove(lan)).status).toBe(204);

    const minhs = (await api<{ requests: RequestView[] }>(minh, "GET", "/v1/requests")).body.requests;
    expect(minhs.find((request) => request.id === open.request.id)).toEqual({ ...open.request, role: "debtor", method: {}, creditorName: "Former member" });
    expect(minhs.find((request) => request.id === paid.request.id)).toMatchObject({ method: paid.request.method, creditorName: "Former member" });
    expect((await api(owner, "GET", "/v1/requests")).body).toEqual({ requests: [{ ...owed, debtorBound: true }] });
    for (const made of [open, paid]) {
      const viewed = await api<{ request: Record<string, unknown> }>(null, "POST", "/v1/request-links/view", { token: linkToken(made.url) });
      expect(viewed.body.request).toMatchObject({ creditorName: "Former member", payment: null });
    }
    expect(JSON.stringify(await rows("SELECT * FROM requests"))).not.toContain(lan.account);
    expect((await api(await signedInAgain(lan), "GET", "/v1/requests")).body).toEqual({ requests: [] });
  });

  it("bill links and their claims: the links stop working, and their claims are deleted", async () => {
    const lan = await googlePerson();
    const turnstile = fakeTurnstile();
    const mine = await publish(lan);
    const someoneElses = await publish(await person());
    for (const link of [mine, someoneElses]) expect((await newClaim(turnstile, link.token, "Minh", ["i1"])).status).toBe(200);

    expect((await remove(lan)).status).toBe(204);

    expect((await openShare(mine.token)).status).toBe(404);
    expect(await rows("SELECT id FROM shares")).toEqual([{ id: someoneElses.id }]);
    expect(await rows("SELECT DISTINCT share_id FROM share_claims")).toEqual([{ share_id: someoneElses.id }]);
    expect((await openShare(someoneElses.token)).body.share.claims).toHaveLength(1);
    expect(JSON.stringify(await rows("SELECT * FROM shares"))).not.toContain(lan.account);
  });
});

describe("deleting an account", () => {
  it("with the app needs the account's session", async () => {
    const sent = await send(proxyRequest("DELETE", "/v1/account"));

    expect(sent.status).toBe(401);
    expect(JSON.parse(sent.body)).toEqual({ error: "session" });
    expect(revenueCat.asked).toEqual([]);
  });

  it("with Google takes an ID token as signing in does, and refuses the rest as signing in does, deleting nothing", async () => {
    const who = await googlePerson();
    const group = await newGroup(who);
    const before = await stateOf(group);
    const refused = (sent: Awaited<ReturnType<typeof send>>, status: number, code: string) => {
      expect(sent.status, code).toBe(status);
      expect(JSON.parse(sent.body)).toEqual({ error: code });
    };

    for (const body of ["{}", '{"idToken": 42}', '{"idToken": ""}', "not json"]) {
      const headers = new Headers({ "content-type": "application/json" });
      refused(await send(new Request("https://proxy.test/v1/account/delete-with-google", { method: "POST", headers, body })), 400, "input");
    }
    for (const change of [
      { key: stranger.privateKey },
      { expires: Math.floor(Date.now() / 1000) - 60 },
      { audience: "another-app.apps.googleusercontent.com" },
      { issuer: "https://accounts.example.com" },
    ]) {
      refused(await deleteWithGoogle(await idToken({ ...change, subject: who.sub })), 401, "id_token");
    }
    const token = await idToken({ subject: who.sub });
    for (const settings of [{ GOOGLE_CLIENT_ID: "" }, { SESSION_SIGNING_KEY: "too short" }]) refused(await deleteWithGoogle(token, settings), 503, "sign_in_off");

    expect(revenueCat.asked).toEqual([]);
    expect(await stateOf(group)).toEqual(before);
    expect(await rows("SELECT kind FROM rate_windows WHERE kind = 'account_delete'")).toEqual([]);
  });

  it("with Google issues no session", async () => {
    const who = await googlePerson();

    const sent = await deleteWithGoogle(await idToken({ subject: who.sub }));

    expect(sent.status).toBe(204);
    expect(sent.body).toBe("");
    expect([...sent.headers.keys()].sort()).toEqual(["cache-control"]);
  });

  it("is taken 5 times an hour for one account, by either route, and 429 with Retry-After past that, deleting nothing", async () => {
    const who = await googlePerson();
    for (let count = 0; count < POLICY.accountDeletionsPerHour; count++) {
      const deletion = DELETIONS[count % DELETIONS.length];
      expect((await deletion.remove(who)).status, `deletion ${count + 1} through ${deletion.route}`).toBe(204);
    }
    // A session outlives the deletion: the same one makes a group and has its purchaser recorded again.
    const group = await newGroup(who);
    expect((await api(who, "GET", "/v1/usage")).body).toMatchObject({ purchaser: who.account });
    const before = { held: await heldFor(who.account), group: await stateOf(group) };

    for (const deletion of DELETIONS) {
      const refused = await deletion.remove(who);
      expect(refused.status, deletion.route).toBe(429);
      expect(JSON.parse(refused.body)).toEqual({ error: "rate_limited" });
      const wait = Number(refused.headers.get("retry-after"));
      expect(wait).toBeGreaterThan(3_500);
      expect(wait).toBeLessThanOrEqual(3_600);
    }
    expect(revenueCat.asked.filter((asked) => asked.method === "DELETE")).toHaveLength(POLICY.accountDeletionsPerHour);
    expect({ held: await heldFor(who.account), group: await stateOf(group) }).toEqual(before);
    // Another account is counted apart.
    expect((await send(proxyRequest("DELETE", "/v1/account", { session: (await person()).session }))).status).toBe(204);
  });

  it("counts a customer RevenueCat does not have as deleted", async () => {
    const who = await person();
    expect((await api(who, "GET", "/v1/usage")).body).toMatchObject({ purchaser: who.account });

    const deleted = await send(proxyRequest("DELETE", "/v1/account", { session: who.session }));

    expect(deleted.status).toBe(204);
    expect(revenueCat.asked.map((asked) => asked.method)).toEqual(["DELETE"]);
    expect(await heldFor(who.account)).toEqual({ book: {}, ledger: {} });
  });

  it("deletes as before without RevenueCat's key, asking RevenueCat nothing", async () => {
    const { account, session } = await accountSession();
    model(ANSWER);
    await send(billRequest({ session }));

    const deleted = await send(proxyRequest("DELETE", "/v1/account", { session }), { REVENUECAT_SECRET_KEY: undefined });

    expect(deleted.status).toBe(204);
    expect(revenueCat.asked).toEqual([]);
    expect(await heldFor(account)).toEqual({ book: { book: monthsCount(1) }, ledger: {} });
  });
});
