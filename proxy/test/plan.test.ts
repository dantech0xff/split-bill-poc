import { runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { ALLOWANCES, WEIGHTS } from "../src/identity";
import { POLICY } from "../src/policy";
import { nextMonthStart, periodOf } from "../src/quota-book";
import { forgetExpired } from "../src/reminders";
import { type Customer, type FakeRevenueCat, fakeRevenueCat, playCustomer, promotionalCustomer, subscriberAnswer, TEST_KEY } from "./fake-revenuecat";
import { ANSWER, billAnswer, billRequest, last, model, proxyRequest, send } from "./requests";
import { modelRevision, revisionRequest, SAMPLE_REVISION } from "./revisions";
import { api, logsOf, type Person, person, rows, signedInAgain } from "./shared";

const HOUR = 60 * 60 * 1000;

let revenueCat: FakeRevenueCat;

beforeEach(() => {
  revenueCat = fakeRevenueCat();
});

function bookOf(who: Person) {
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(`google:${who.account}`));
}

/** The plan the account's QuotaBook keeps, as storage holds it. */
function storedPlan(who: Person): Promise<unknown> {
  return runInDurableObject(bookOf(who), (_, state) => state.storage.get("plan"));
}

/** What `usage` says of an account that used [used] calls this month, on [plan]. */
function month(plan: "free" | "pro", used: number) {
  const now = Date.now();
  const limit = plan === "pro" ? POLICY.proCalls : POLICY.monthlyCalls;
  return { limit, used, remaining: Math.max(0, limit - used), period: periodOf(ALLOWANCES.monthly, now), resetsAt: nextMonthStart(now), plan };
}

/** `POST /v1/plan` as [who], with [body]: `{}` unless given, and nothing at all for "". */
function refresh(who: Person | null, body: unknown = {}, settings: Parameters<typeof api>[4] = {}) {
  return api<Record<string, unknown>>(who, "POST", "/v1/plan", body, settings);
}

/** An account whose app asked for its plan while RevenueCat held [customer] for it. */
async function customerWith(customer: Customer): Promise<Person> {
  const who = await person();
  revenueCat.hold(who.account, customer);
  expect((await refresh(who)).status).toBe(200);
  return who;
}

/** The plan the calls left say once RevenueCat holds [customer], and the `proUntil` the account's QuotaBook keeps. */
async function planOf(customer: Customer): Promise<{ plan: unknown; proUntil: unknown }> {
  const who = await customerWith(customer);
  const read = await api<{ usage: { plan: string } }>(who, "GET", "/v1/usage");
  return { plan: read.body.usage.plan, proUntil: ((await storedPlan(who)) as { proUntil: unknown }).proUntil };
}

describe("asking for the plan", () => {
  it("reads the account's subscription from RevenueCat, keeps its plan, and answers with the purchaser and the calls left", async () => {
    const who = await person();
    const expires = Date.now() + 20 * POLICY.dayMs;
    revenueCat.hold(who.account, playCustomer({ expires }));
    const at = Date.now();
    revenueCat.clock = () => at;

    const answer = await refresh(who);

    expect(answer.status).toBe(200);
    expect(answer.body).toEqual({ purchaser: who.account, usage: month("pro", 0), aiEnabled: true });
    expect(revenueCat.asked).toEqual([{ method: "GET", id: who.account, authorization: `Bearer ${TEST_KEY}`, platform: null }]);
    expect(await storedPlan(who)).toEqual({ proUntil: expires, checkedAt: at });
  });

  it("takes an empty body as well as {}, and reads no plan from what the app sends", async () => {
    const who = await person();

    expect((await refresh(who, "")).body).toEqual({ purchaser: who.account, usage: month("free", 0), aiEnabled: true });
    const claimed = await refresh(who, { plan: "pro", proUntil: Date.now() + 30 * POLICY.dayMs, usage: { plan: "pro" } });

    expect(claimed.status).toBe(200);
    expect(claimed.body).toMatchObject({ usage: { limit: POLICY.monthlyCalls, plan: "free" } });
  });

  it("records the purchaser as handed out, so that RevenueCat's events about it are followed", async () => {
    const who = await person();
    expect(await bookOf(who).purchaserIssued()).toBe(false);

    await refresh(who);

    expect(await bookOf(who).purchaserIssued()).toBe(true);
  });

  it("needs a session, and asks RevenueCat nothing without one", async () => {
    const nobody = await refresh(null);
    const stranger = await send(proxyRequest("POST", "/v1/plan", { session: "not-a-session" }, "{}"));

    expect(nobody.status).toBe(401);
    expect(nobody.body).toEqual({ error: "session" });
    expect(stranger.status).toBe(401);
    expect(JSON.parse(stranger.body)).toEqual({ error: "session" });
    expect(revenueCat.asked).toEqual([]);
  });

  it("is answered 30 times an hour for one account, and 429 with Retry-After past that", async () => {
    const who = await person();
    for (let count = 0; count < POLICY.planRefreshesPerHour; count++) {
      expect((await refresh(who)).status, `refresh ${count + 1}`).toBe(200);
    }

    const refused = await refresh(who);

    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    const wait = Number(refused.headers.get("retry-after"));
    expect(wait).toBeGreaterThan(3_500);
    expect(wait).toBeLessThanOrEqual(3_600);
    expect(revenueCat.gets(who.account)).toBe(POLICY.planRefreshesPerHour);
    // Another account is counted apart, and the window starts over an hour later.
    expect((await refresh(await person())).status).toBe(200);
    await env.DB.prepare("UPDATE rate_windows SET started_at = started_at - ?2 WHERE account = ?1").bind(who.account, POLICY.hourMs).run();
    expect((await refresh(who)).status).toBe(200);
  }, 60_000);

  it("keeps its hourly count through the account's deletion, which cannot start it over, until the daily run after the hour", async () => {
    const who = await person();
    for (let count = 0; count < POLICY.planRefreshesPerHour; count++) {
      expect((await refresh(who)).status, `refresh ${count + 1}`).toBe(200);
    }
    expect((await send(proxyRequest("DELETE", "/v1/account", { session: who.session }))).status).toBe(204);

    // The deletion ended the session, which counts nothing more; signing in again finds the count.
    expect((await refresh(who)).body).toEqual({ error: "session" });
    const refused = await refresh(await signedInAgain(who));

    expect(refused.status).toBe(429);
    expect(refused.body).toEqual({ error: "rate_limited" });
    expect(revenueCat.gets(who.account)).toBe(POLICY.planRefreshesPerHour);
    expect(await rows("SELECT account, kind, used FROM rate_windows ORDER BY kind")).toEqual([
      { account: who.account, kind: "account_delete", used: 1 },
      { account: who.account, kind: "plan_refresh", used: POLICY.planRefreshesPerHour },
    ]);
    await env.DB.prepare("UPDATE rate_windows SET started_at = started_at - ?2 WHERE account = ?1").bind(who.account, POLICY.hourMs + 1).run();
    await forgetExpired(env, Date.now());
    expect(await rows("SELECT * FROM rate_windows")).toEqual([]);
  }, 60_000);

  it("reads an answer of up to 256 KB, and is 502 for a longer one, keeping the plan as it was", async () => {
    const who = await customerWith(playCustomer({ expires: Date.now() + 20 * POLICY.dayMs }));
    const kept = await storedPlan(who);
    const now = Date.now();
    /** RevenueCat's answer that the account has no Pro, made [bytes] long by a field the proxy does not read. */
    const answerOf = (bytes: number) => {
      const answer = { ...subscriberAnswer(who.account, { entitlements: {}, subscriptions: {} }, now), padding: "" };
      answer.padding = "x".repeat(bytes - JSON.stringify(answer).length);
      return () => new HttpResponse(JSON.stringify(answer), { headers: { "content-type": "application/json" } });
    };

    revenueCat.answer = answerOf(256 * 1024 + 1);
    const refused = await refresh(who);

    expect(refused.status).toBe(502);
    expect(refused.body).toEqual({ error: "subscription_unavailable" });
    expect(await storedPlan(who)).toEqual(kept);

    revenueCat.answer = answerOf(256 * 1024);
    const read = await refresh(who);

    expect(read.status).toBe(200);
    expect(read.body).toMatchObject({ usage: { plan: "free" } });
    expect(await storedPlan(who)).toEqual({ proUntil: null, checkedAt: now });
  });

  it("is 502 with a key RevenueCat refuses, as is a deletion, and both log RevenueCat's 401, so that a wrong key shows", async () => {
    const who = await person();
    const wrongKey = "sk_not_the_projects_key";

    const { result, lines } = await logsOf(async () => ({
      plan: await refresh(who, {}, { REVENUECAT_SECRET_KEY: wrongKey }),
      deletion: await send(proxyRequest("DELETE", "/v1/account", { session: who.session }), { REVENUECAT_SECRET_KEY: wrongKey }),
    }));

    expect(result.plan.status).toBe(502);
    expect(result.plan.body).toEqual({ error: "subscription_unavailable" });
    expect(result.deletion.status).toBe(502);
    expect(JSON.parse(result.deletion.body)).toEqual({ error: "subscription_unavailable" });
    expect(lines.map((line) => JSON.parse(line) as unknown)).toEqual([
      { event: "revenuecat_unavailable", status: 401 },
      { event: "revenuecat_delete_failed", status: 401 },
    ]);
    expect(lines.join("\n")).not.toContain(wrongKey);
    expect(revenueCat.asked.map(({ method, authorization }) => [method, authorization])).toEqual([
      ["GET", `Bearer ${wrongKey}`],
      ["DELETE", `Bearer ${wrongKey}`],
    ]);
    // The deletion did not go through: the purchaser's record is still there.
    expect(await bookOf(who).purchaserIssued()).toBe(true);
  });

  it("is 503 not_configured without RevenueCat's key, before RevenueCat or the hourly count is asked", async () => {
    const who = await person();

    for (const key of [undefined, "", "  "]) {
      const sent = await refresh(who, {}, { REVENUECAT_SECRET_KEY: key });
      expect(sent.status).toBe(503);
      expect(sent.body).toEqual({ error: "not_configured" });
    }
    expect(revenueCat.asked).toEqual([]);
    expect(await rows("SELECT * FROM rate_windows")).toEqual([]);
  });

  it("is 502 subscription_unavailable when RevenueCat gives no plan that can be read, and keeps the plan as it was", async () => {
    const who = await customerWith(playCustomer({ expires: Date.now() + 20 * POLICY.dayMs }));
    const kept = await storedPlan(who);
    // What RevenueCat would answer now, had it answered.
    revenueCat.hold(who.account, { entitlements: {}, subscriptions: {} });
    const now = Date.now();
    const answering = (customer: Customer) => HttpResponse.json(subscriberAnswer(who.account, customer, now));
    const failures: Array<[string, () => Response]> = [
      ["unreachable", () => HttpResponse.error()],
      ["500", () => HttpResponse.json({ code: 7110, message: "Internal server error." }, { status: 500 })],
      ["503 in HTML", () => new HttpResponse("<html>Service Unavailable</html>", { status: 503, headers: { "content-type": "text/html" } })],
      ["401", () => HttpResponse.json({ code: 7225, message: "Invalid API Key." }, { status: 401 })],
      ["429", () => HttpResponse.json({ code: 7731, message: "Too many requests." }, { status: 429 })],
      ["not JSON", () => new HttpResponse("{", { status: 200, headers: { "content-type": "application/json" } })],
      ["no subscriber", () => HttpResponse.json({ request_date_ms: now })],
      ["no time", () => HttpResponse.json({ ...subscriberAnswer(who.account, { entitlements: {}, subscriptions: {} }, now), request_date_ms: undefined })],
      ["a time as text", () => HttpResponse.json({ ...subscriberAnswer(who.account, { entitlements: {}, subscriptions: {} }, now), request_date_ms: String(now) })],
      ["no entitlements", () => HttpResponse.json({ request_date_ms: now, subscriber: { subscriptions: {} } })],
      ["entitlements as a list", () => HttpResponse.json({ request_date_ms: now, subscriber: { entitlements: [], subscriptions: {} } })],
      ["pro as text", () => answering({ entitlements: { pro: "active" }, subscriptions: {} })],
      ["pro without an end", () => answering({ entitlements: { pro: { product_identifier: "pro" } }, subscriptions: {} })],
      ["an end that is no time", () => answering({ entitlements: { pro: { expires_date: "soon", product_identifier: "pro" } }, subscriptions: {} })],
      ["a grace period that is no time", () => answering({ entitlements: { pro: { expires_date: null, grace_period_expires_date: 7 } }, subscriptions: {} })],
    ];

    for (const [name, failure] of failures) {
      revenueCat.answer = () => failure();
      const sent = await refresh(who);
      expect(sent.status, name).toBe(502);
      expect(sent.body, name).toEqual({ error: "subscription_unavailable" });
      expect(await storedPlan(who), name).toEqual(kept);
    }
    expect((await api(who, "GET", "/v1/usage")).body).toMatchObject({ usage: { plan: "pro" } });

    revenueCat.answer = undefined;
    expect((await refresh(who)).body).toMatchObject({ usage: { plan: "free" } });
  });
});

describe("the plan RevenueCat gives", () => {
  it("is Pro while the entitlement is in force, until its period ends", async () => {
    const expires = Date.now() + 20 * POLICY.dayMs;

    expect(await planOf(playCustomer({ expires }))).toEqual({ plan: "pro", proUntil: expires });
  });

  it("is Pro in the grace period Play gives after a failed payment, until that ends", async () => {
    const now = Date.now();
    const grace = now + 3 * POLICY.dayMs;

    expect(await planOf(playCustomer({ expires: now - HOUR, grace, billingIssue: now - HOUR }))).toEqual({ plan: "pro", proUntil: grace });
  });

  it("is Pro for a day after a period that ended while the subscription still renews, as Play may renew it late, and free after", async () => {
    const now = Date.now();
    const pending = now - HOUR;
    const lapsed = now - POLICY.proGraceMs - HOUR;

    expect(await planOf(playCustomer({ expires: pending }))).toEqual({ plan: "pro", proUntil: pending });
    // A product named with its base plan is found under its subscription.
    expect(await planOf(playCustomer({ expires: pending, product: "pro:monthly" }))).toEqual({ plan: "pro", proUntil: pending });
    expect(await planOf(playCustomer({ expires: lapsed }))).toEqual({ plan: "free", proUntil: lapsed });
  });

  it("stays Pro to the end of the period once renewal is turned off, and ends at once when the period is over", async () => {
    const now = Date.now();
    const expires = now + 5 * POLICY.dayMs;

    expect(await planOf(playCustomer({ expires, unsubscribed: now - POLICY.dayMs }))).toEqual({ plan: "pro", proUntil: expires });
    expect(await planOf(playCustomer({ expires: now - HOUR, unsubscribed: now - POLICY.dayMs }))).toEqual({ plan: "free", proUntil: null });
  });

  it("ends at once when the purchase was refunded", async () => {
    const now = Date.now();

    // Play revokes a subscription it refunds: RevenueCat ends its period when it was refunded.
    expect(await planOf(playCustomer({ expires: now - HOUR, refunded: now - HOUR }))).toEqual({ plan: "free", proUntil: null });
  });

  it("ends at once when a payment failed and Play gives no grace period", async () => {
    const now = Date.now();

    expect(await planOf(playCustomer({ expires: now - HOUR, billingIssue: now - 2 * HOUR }))).toEqual({ plan: "free", proUntil: null });
  });

  it("never ends for an entitlement granted for life", async () => {
    expect(await planOf(promotionalCustomer(null))).toEqual({ plan: "pro", proUntil: Number.POSITIVE_INFINITY });
  });

  it("is Pro while a grant from RevenueCat's dashboard lasts, and free as soon as it has ended, as a grant never renews", async () => {
    const now = Date.now();
    const lasts = now + 7 * POLICY.dayMs;

    expect(await planOf(promotionalCustomer(lasts))).toEqual({ plan: "pro", proUntil: lasts });
    expect(await planOf(promotionalCustomer(now - HOUR))).toEqual({ plan: "free", proUntil: null });
  });

  it("is free without the entitlement, as for a customer RevenueCat has just created", async () => {
    const who = await person();

    expect((await refresh(who)).body).toMatchObject({ usage: month("free", 0) });
    expect(revenueCat.has(who.account)).toBe(true);
    expect(await storedPlan(who)).toEqual({ proUntil: null, checkedAt: expect.any(Number) });
  });

  it("counts a license tester's purchase, which RevenueCat marks as sandbox, as any other", async () => {
    const expires = Date.now() + 5 * 60_000;

    expect(await planOf(playCustomer({ expires, sandbox: true }))).toEqual({ plan: "pro", proUntil: expires });
  });
});

describe("Pro calls", () => {
  /** An account that has used ten calls this month, on Pro when [pro] says so. */
  async function tenCallsUsed(pro: boolean): Promise<Person> {
    const who = await customerWith(pro ? playCustomer({ expires: Date.now() + 20 * POLICY.dayMs }) : { entitlements: {}, subscriptions: {} });
    const book = bookOf(who);
    const now = Date.now();
    for (let call = 0; call < POLICY.monthlyCalls; call++) {
      const id = crypto.randomUUID();
      await book.hold(now, id, ALLOWANCES.monthly, "digest-of-a-bill", WEIGHTS.billAnalysis);
      await book.commit(now, id, ALLOWANCES.monthly, "digest-of-a-bill", WEIGHTS.billAnalysis);
    }
    return who;
  }

  it("let a Pro account read an eleventh bill in a month, which a free account is refused", async () => {
    const seen = model(ANSWER);
    const pro = await tenCallsUsed(true);
    const free = await tenCallsUsed(false);

    const read = await send(billRequest({ session: pro.session }));
    const refused = await send(billRequest({ session: free.session }));

    expect(last(read)).toEqual({ type: "result", answer: billAnswer, usage: month("pro", 11) });
    expect(refused.status).toBe(429);
    expect(JSON.parse(refused.body)).toEqual({ error: "quota_exhausted", usage: month("free", 10) });
    expect(seen).toHaveLength(1);
  });

  it("say the plan in the answer of an edit of a split", async () => {
    model(modelRevision(SAMPLE_REVISION));
    const who = await customerWith(playCustomer({ expires: Date.now() + 20 * POLICY.dayMs }));

    const edit = await send(revisionRequest({ session: who.session }));

    expect(last(edit)).toEqual({ type: "result", answer: SAMPLE_REVISION, usage: { ...month("pro", 0.2), remaining: 299.8 } });
  });

  it("say the plan in the calls left: trial for an install, free or pro for an account", async () => {
    const install = await send(proxyRequest("GET", "/v1/usage"));
    const free = await person();
    const pro = await customerWith(playCustomer({ expires: Date.now() + 20 * POLICY.dayMs }));

    expect(JSON.parse(install.body)).toEqual({ usage: { limit: 3, used: 0, remaining: 3, period: "trial", resetsAt: null, plan: "trial" }, aiEnabled: true });
    expect((await api(free, "GET", "/v1/usage")).body).toEqual({ usage: month("free", 0), aiEnabled: true, purchaser: free.account });
    expect((await api(pro, "GET", "/v1/usage")).body).toEqual({ usage: month("pro", 0), aiEnabled: true, purchaser: pro.account });
  });
});
