import { createExecutionContext, createScheduledController, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { exportJWK, generateKeyPair, SignJWT } from "jose";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { WEIGHTS } from "../src/identity";
import worker, { type Env } from "../src/index";
import { type BillRequest, billRequestOf } from "../src/input";
import { hashed } from "../src/ledger";
import type { MetricEvent } from "../src/metrics";
import { callModel, type ModelTask, TIMING } from "../src/model-call";
import { POLICY } from "../src/policy";
import { BILL_SCHEMA } from "../src/schema";
import { fakeRevenueCat, playCustomer, TEST_WEBHOOK_AUTH, webhookEvent } from "./fake-revenuecat";
import { fakeTurnstile } from "./fake-turnstile";
import { network } from "./network";
import { ANSWER, billAnswer, billRequest, IP, model, proxyRequest, type Sent, send } from "./requests";
import { modelRevision, revisionRequest, SAMPLE_REVISION } from "./revisions";
import { fromPage, publish } from "./share-fixtures";
import { api, linkToken, logsOf, type Person, person, VND } from "./shared";

/** A dataset that keeps every data point written to it, as the binding `METRICS` would send it to Analytics Engine. */
function dataset(write: (point: AnalyticsEngineDataPoint) => void = () => undefined) {
  const points: AnalyticsEngineDataPoint[] = [];
  const binding: AnalyticsEngineDataset = {
    writeDataPoint(point) {
      if (point !== undefined) points.push(point);
      write(point ?? {});
    },
  };
  return { points, settings: { METRICS: binding } satisfies Partial<Env> };
}

/** The QuotaBooks with a `hold` that fails, as the call to an object fails while it is reset by a deploy or overloaded. */
function booksThatCannotHold(): Env["QUOTA_BOOK"] {
  return new Proxy(env.QUOTA_BOOK, {
    get(books, property) {
      if (property === "get") {
        return (id: DurableObjectId) =>
          new Proxy(books.get(id), {
            get: (book, method) => (method === "hold" ? () => Promise.reject(new Error("Durable Object reset because its code was updated.")) : Reflect.get(book, method)),
          });
      }
      const value: unknown = Reflect.get(books, property);
      return typeof value === "function" ? value.bind(books) : value;
    },
  });
}

/** The events and outcomes of [points], in order. */
function counted(points: AnalyticsEngineDataPoint[]): Array<[unknown, unknown]> {
  return points.map((point) => [point.blobs?.[0], point.blobs?.[1]]);
}

const google = await generateKeyPair("RS256");
const googleKeys = { keys: [{ ...(await exportJWK(google.publicKey)), kid: "google-test", alg: "RS256", use: "sig" }] };

beforeEach(() => {
  network.use(http.get("https://www.googleapis.com/oauth2/v3/certs", () => HttpResponse.json(googleKeys)));
});

function signIn(sub: string, settings: Partial<Env>) {
  const token = new SignJWT({})
    .setProtectedHeader({ alg: "RS256", kid: "google-test" })
    .setIssuer("https://accounts.google.com")
    .setAudience("test-web-client.apps.googleusercontent.com")
    .setSubject(sub)
    .setIssuedAt()
    .setExpirationTime("1h")
    .sign(google.privateKey);
  return token.then((idToken) => send(proxyRequest("POST", "/v1/session", {}, JSON.stringify({ idToken })), settings));
}

describe("the counters", () => {
  it("count a bill read with its outcome, cost, tokens and time, and an install's first call once", async () => {
    const { points, settings } = dataset();
    model(ANSWER);
    const install = crypto.randomUUID();

    await send(billRequest({ install }), settings);
    await send(billRequest({ install }), settings);

    expect(counted(points)).toEqual([
      ["bill_analysis", "ok"],
      ["install_first_call", "ok"],
      ["bill_analysis", "ok"],
    ]);
    const [read] = points;
    expect(read?.indexes).toEqual(["bill_analysis"]);
    const [cost, inputTokens, outputTokens, ms] = read?.doubles ?? [];
    expect(cost).toBeGreaterThan(0);
    expect(inputTokens).toBeGreaterThan(0);
    expect(outputTokens).toBeGreaterThan(0);
    expect(ms).toBeGreaterThanOrEqual(0);
    expect(points[1]?.doubles).toEqual([0, 0, 0, 0]);
  });

  it("count an edit of a split by a sentence, and the outcome of a call refused before the model", async () => {
    const { points, settings } = dataset();
    model(modelRevision(SAMPLE_REVISION));
    const { session } = await person();

    await send(revisionRequest({ session }), settings);
    await send(billRequest({ session }), { ...settings, AI_ENABLED: "false" });
    await send(billRequest({ install: null }), settings);

    expect(counted(points)).toEqual([
      ["split_revision", "ok"],
      ["bill_analysis", "ai_off"],
      ["bill_analysis", "install"],
    ]);
    expect(points[0]?.doubles?.[0]).toBeGreaterThan(0);
    expect(points[1]?.doubles?.slice(0, 3)).toEqual([0, 0, 0]);
  });

  it("count the first call of an install with what came of it, even when it got no answer", async () => {
    const { points, settings } = dataset();
    model(() => HttpResponse.json({ type: "error", error: { type: "api_error", message: "down" } }, { status: 500 }));
    const install = crypto.randomUUID();

    await logsOf(() => send(billRequest({ install }), settings));
    model(ANSWER);
    await send(billRequest({ install }), settings);

    expect(counted(points)).toEqual([
      ["bill_analysis", "upstream"],
      ["install_first_call", "upstream"],
      ["bill_analysis", "ok"],
    ]);
  });

  it("count a bill read or an edit that an error cuts short as internal: one the Worker answers 500, before the stream starts", async () => {
    const { points, settings } = dataset();
    model(ANSWER);
    const failing = { ...settings, QUOTA_BOOK: booksThatCannotHold() };

    const { result } = await logsOf(async () => [
      await send(billRequest({ install: crypto.randomUUID() }), failing),
      await send(revisionRequest({ install: crypto.randomUUID() }), failing),
    ]);

    for (const sent of result) {
      expect(sent.status).toBe(500);
      expect(JSON.parse(sent.body)).toEqual({ error: "internal" });
    }
    expect(counted(points)).toEqual([
      ["bill_analysis", "internal"],
      ["split_revision", "internal"],
    ]);
  });

  it("count a call that an error cuts short as internal: one whose stream then ends with the error line internal", async () => {
    const { points, settings } = dataset();
    model(ANSWER);
    const broken: ModelTask<BillRequest> = {
      event: "bill_analysis",
      weight: WEIGHTS.billAnalysis,
      maxBodyBytes: POLICY.maxBodyBytes,
      inputOf: billRequestOf,
      system: "Read the bill.",
      schema: BILL_SCHEMA,
      userMessage: (input) => input.ocrDocument,
      settings: () => ({ model: POLICY.model, maxTokens: 1_000, effort: "low", fallback: false, report: false }),
      answerOf: () => {
        throw new TypeError("The answer could not be handled");
      },
      chargeBadAnswer: true,
    };
    const ctx = createExecutionContext();

    const { result: lines } = await logsOf(async () => {
      const response = await callModel(billRequest({ install: crypto.randomUUID() }), { ...env, ...settings }, ctx, broken, TIMING);
      const body = await response.text();
      await waitOnExecutionContext(ctx);
      return body.trim().split("\n").map((line) => JSON.parse(line) as unknown);
    });

    expect(lines[lines.length - 1]).toEqual({ type: "error", code: "internal" });
    expect(counted(points)).toEqual([
      ["bill_analysis", "internal"],
      ["install_first_call", "internal"],
    ]);
  });

  it("log and count a spend cap that a call takes to 80% of it, once in the cap's window, naming the cap and nothing else", async () => {
    const { points, settings } = dataset();
    model(ANSWER);
    const global = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName("global"));
    const mark = (cap: number) => (cap * POLICY.spendWarnPercent) / 100;
    // Just short of the warning marks of the installs' pool and of the proxy's cap.
    await global.holdSpend(Date.now(), mark(POLICY.installCapMicro) - 1, POLICY.globalCapMicro, POLICY.installCapMicro);
    await global.holdSpend(Date.now(), mark(POLICY.globalCapMicro) - mark(POLICY.installCapMicro), POLICY.globalCapMicro);

    const { lines } = await logsOf(async () => {
      await send(billRequest({ install: crypto.randomUUID() }), settings);
      await send(billRequest({ session: (await person()).session }), settings);
    });

    const near = points.filter((point) => point.blobs?.[0] === "spend_cap_near");
    expect(counted(near)).toEqual([
      ["spend_cap_near", "global"],
      ["spend_cap_near", "install"],
    ]);
    for (const point of near) expect(point).toEqual({ indexes: ["spend_cap_near"], blobs: point.blobs, doubles: [0, 0, 0, 0] });
    const logged = lines.map((line) => JSON.parse(line) as Record<string, unknown>).filter((line) => line.event === "spend_cap_near");
    expect(logged).toEqual([
      { event: "spend_cap_near", cap: "global" },
      { event: "spend_cap_near", cap: "install" },
    ]);
  });

  it("count sign-ins, with their outcome", async () => {
    const { points, settings } = dataset();

    await signIn("109876543210987654321", settings);
    await send(proxyRequest("POST", "/v1/session", {}, JSON.stringify({ idToken: "not.a.token" })), settings);
    await send(proxyRequest("POST", "/v1/session", {}, "{}"), settings);
    await signIn("109876543210987654321", { ...settings, GOOGLE_CLIENT_ID: "" });

    expect(counted(points)).toEqual([
      ["sign_in", "ok"],
      ["sign_in", "id_token"],
      ["sign_in", "input"],
      ["sign_in", "sign_in_off"],
    ]);
  });

  it("count bill links made, and the claims that land on them", async () => {
    const { points, settings } = dataset();
    const lan = await person();
    const link = await publish(lan);
    const turnstile = fakeTurnstile();

    await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: link.share.bill }, settings);
    const claimed = await fromPage<{ key: string }>("/v1/shares/claim", { token: link.token, name: "Minh", itemIds: ["i1"], turnstile: turnstile.token() }, undefined, settings);
    await fromPage("/v1/shares/claim", { token: link.token, key: claimed.body.key, name: "Minh", itemIds: ["i1", "i2"] }, undefined, settings);
    // The same claim again changes nothing, and is not counted; neither is one that does not land.
    await fromPage("/v1/shares/claim", { token: link.token, key: claimed.body.key, name: "Minh", itemIds: ["i1", "i2"] }, undefined, settings);
    await fromPage("/v1/shares/claim", { token: link.token, name: "Hoa", itemIds: ["i9"], turnstile: turnstile.token() }, undefined, settings);

    expect(counted(points)).toEqual([
      ["share_created", "ok"],
      ["share_claim", "new"],
      ["share_claim", "changed"],
    ]);
  });

  it("count payment requests made, reported paid and confirmed paid, each once", async () => {
    const { points, settings } = dataset();
    const lan = await person();
    const minh = await person();
    const body = { id: crypto.randomUUID(), amountMinor: 70_000, currency: VND, payerName: "Minh", creditorName: "Lan", method: {} };

    const made = await api<{ request: { id: string }; url: string }>(lan, "POST", "/v1/requests", body, settings);
    await api(lan, "POST", "/v1/requests", body, settings);
    await api(minh, "POST", "/v1/request-links/accept", { token: linkToken(made.body.url) }, settings);
    for (let time = 0; time < 2; time++) {
      await api(minh, "POST", `/v1/requests/${made.body.request.id}/reported-paid`, undefined, settings);
      await api(lan, "POST", `/v1/requests/${made.body.request.id}/confirmed-paid`, undefined, settings);
    }

    expect(counted(points)).toEqual([
      ["request_created", "ok"],
      ["request_reported_paid", "ok"],
      ["request_confirmed_paid", "ok"],
    ]);
  });

  it("count refreshes of a plan and the events of RevenueCat's webhook, with their outcome", async () => {
    const { points, settings } = dataset();
    const revenueCat = fakeRevenueCat();
    const who: Person = await person();
    revenueCat.hold(who.account, playCustomer({ expires: Date.now() + 20 * 24 * 60 * 60 * 1000 }));
    const deliver = (event: Record<string, unknown>) =>
      send(
        new Request("https://proxy.test/v1/subscription-events", {
          method: "POST",
          headers: { authorization: TEST_WEBHOOK_AUTH, "content-type": "application/json" },
          body: JSON.stringify(event),
        }),
        settings,
      );

    await api(who, "POST", "/v1/plan", undefined, settings);
    await api(who, "POST", "/v1/plan", undefined, { ...settings, REVENUECAT_SECRET_KEY: undefined });
    await deliver(webhookEvent("RENEWAL", who.account));
    await deliver(webhookEvent("TEST", who.account));

    expect(counted(points)).toEqual([
      ["plan_refresh", "ok"],
      ["plan_refresh", "not_configured"],
      ["subscription_event", "ok"],
      ["subscription_event", "test"],
    ]);
  });

  it("count the daily run, done or failed, with how long it took", async () => {
    const { points, settings } = dataset();
    network.use(
      http.get("https://api.frankfurter.dev/v2/rates", () => HttpResponse.json([{ date: "2026-11-14", base: "EUR", quote: "USD", rate: 1.1 }])),
      http.get("https://api.frankfurter.dev/v2/currencies", () => HttpResponse.json([{ iso_code: "USD", type: "currency" }])),
    );
    await worker.scheduled(createScheduledController({ cron: "30 16 * * *" }), { ...env, ...settings });
    network.use(http.get("https://api.frankfurter.dev/v2/rates", () => HttpResponse.error()));
    await logsOf(() => expect(worker.scheduled(createScheduledController({ cron: "30 16 * * *" }), { ...env, ...settings })).rejects.toThrow());

    expect(counted(points)).toEqual([
      ["daily_run", "ok"],
      ["daily_run", "failed"],
    ]);
    expect(points[0]?.doubles?.[3]).toBeGreaterThanOrEqual(0);
  });

  it("never write who: no account, install id, IP, token or anything of a bill", async () => {
    const { points, settings } = dataset();
    model(ANSWER);
    const install = crypto.randomUUID();
    const sub = "109876543210987654321";
    const lan = await person();
    const link = await publish(lan);

    await send(billRequest({ install, ip: IP }), settings);
    await signIn(sub, settings);
    await api(lan, "PUT", `/v1/shares/${crypto.randomUUID()}`, { billId: crypto.randomUUID(), billRevision: 1, bill: link.share.bill }, settings);

    const written = JSON.stringify(points);
    for (const secret of [install, sub, await hashed(sub, env.HASH_SALT), lan.account, IP, link.token, billAnswer.merchant ?? "", "Pho bo"]) {
      if (secret !== "") expect(written, secret).not.toContain(secret);
    }
    const events: MetricEvent[] = ["bill_analysis", "install_first_call", "sign_in", "share_created"];
    for (const point of points) {
      expect(Object.keys(point).sort()).toEqual(["blobs", "doubles", "indexes"]);
      expect(point.indexes).toEqual([point.blobs?.[0]]);
      expect(point.blobs).toHaveLength(2);
      expect(events).toContain(point.blobs?.[0]);
      expect(point.blobs?.[1]).toMatch(/^[a-z_]+$/);
      expect(point.doubles?.every((number) => Number.isFinite(number))).toBe(true);
    }
  });

  it("change nothing when the binding is missing, or refuses a point", async () => {
    model(ANSWER);
    const refusing = dataset(() => {
      throw new RangeError("Too many data points");
    });
    const answers: Sent[] = [];
    for (const settings of [{ METRICS: undefined }, refusing.settings]) {
      const { result, lines } = await logsOf(() => send(billRequest({ install: crypto.randomUUID() }), settings));
      answers.push(result);
      if (settings === refusing.settings) {
        expect(lines.filter((line) => line.includes("metric_not_written"))).toEqual([
          JSON.stringify({ event: "metric_not_written", error: "RangeError" }),
          JSON.stringify({ event: "metric_not_written", error: "RangeError" }),
        ]);
      }
    }

    for (const answer of answers) {
      expect(answer.status).toBe(200);
      expect(answer.lines.at(-1)).toMatchObject({ type: "result", answer: billAnswer });
    }
  });
});
