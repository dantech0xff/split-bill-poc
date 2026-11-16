import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { POLICY } from "../src/policy";
import { SYSTEM_PROMPT, userMessage } from "../src/prompt";
import { BILL_SCHEMA } from "../src/schema";
import { holdMicro } from "../src/spend";
import { apiError, eventStream, type Reply, serverSentEvents, streamed } from "./fake-model";
import { network } from "./network";
import {
  ANSWER,
  billRequest,
  fetchWorker,
  IP,
  ipName,
  last,
  model,
  quotaOf,
  sampleAnswer,
  sampleRequest,
  send,
  spendOf,
} from "./requests";

describe("a bill read", () => {
  it("streams wait lines, then the model's answer with the calls left", async () => {
    model(ANSWER);

    const sent = await send(billRequest());

    expect(sent.status).toBe(200);
    expect(sent.headers.get("content-type")).toBe("application/x-ndjson; charset=utf-8");
    expect(sent.lines[0]).toEqual({ type: "wait" });
    expect(last(sent)).toEqual({
      type: "result",
      answer: sampleAnswer,
      usage: { limit: 3, used: 1, remaining: 2, period: "trial", resetsAt: null },
    });
  });

  it("asks the model with the proxy's prompt, schema and settings", async () => {
    const seen = model(ANSWER);

    await send(billRequest());

    expect(seen).toHaveLength(1);
    expect(seen[0].body).toEqual({
      model: POLICY.model,
      max_tokens: POLICY.maxTokens,
      system: SYSTEM_PROMPT,
      messages: [{ role: "user", content: userMessage(sampleRequest.ocrDocument, sampleRequest.instruction, sampleRequest.localCurrency) }],
      output_config: { effort: "medium", format: { type: "json_schema", schema: BILL_SCHEMA } },
      fallbacks: "default",
      stream: true,
    });
    expect(seen[0].headers["anthropic-beta"]).toContain("server-side-fallback-2026-07-01");
    expect(seen[0].headers["x-api-key"] ?? seen[0].headers.authorization).toMatch(/^(Bearer )?test-anthropic-key$/);
  });
});

describe("the contract", () => {
  it("refuses a request without a valid install id", async () => {
    const seen = model(ANSWER);

    for (const install of [null, "not-a-uuid"]) {
      const sent = await send(billRequest({ install }));
      expect(sent.status).toBe(401);
      expect(JSON.parse(sent.body)).toEqual({ error: "install" });
    }
    expect(seen).toHaveLength(0);
  });

  it("refuses a body that breaks the contract, before anything is counted", async () => {
    const seen = model(ANSWER);
    const valid = { ...sampleRequest, requestId: crypto.randomUUID() };
    const document = (rows: string) => `<bill_ocr rows="1">\n${rows}\n</bill_ocr>`;
    const broken: Array<Record<string, unknown> | string> = [
      "{ not json",
      { ...valid, schemaVersion: 2 },
      { ...valid, requestId: "42" },
      { ...valid, ocrDocument: document("x".repeat(POLICY.maxDocumentChars)) },
      { ...valid, ocrDocument: "1 | [0,0,10,10] \"Coffee\"" },
      { ...valid, instruction: "x".repeat(POLICY.maxInstructionChars + 1) },
      { ...valid, localCurrency: "vnd" },
      { ...valid, ocrDocument: undefined },
      // Follows the contract, but is longer than any request needs to be.
      JSON.stringify({ ...valid, padding: "x".repeat(POLICY.maxBodyBytes) }),
    ];

    for (const body of broken) {
      const sent = await send(billRequest(typeof body === "string" ? { raw: body } : { body }));
      expect(sent.status, JSON.stringify(body).slice(0, 80)).toBe(400);
      expect(JSON.parse(sent.body)).toEqual({ error: "input" });
    }
    expect(seen).toHaveLength(0);
    expect(await spendOf(await ipName(IP))).toBeUndefined();
  });

  it("accepts a bill photographed in two pages and hands its document to the model as it came", async () => {
    const seen = model(ANSWER);
    const ocrDocument = [
      '<bill_ocr pages="2">',
      '<page n="1" rows="2">',
      '1 | [40,10,420,40] "PHO 24"',
      '2 | [40,410,420,440] "Pho bo" | [800,410,960,440] "65.000"',
      "</page>",
      '<page n="2" rows="1">',
      '1 | [40,100,420,130] "TONG CONG" | [800,100,960,130] "65.000"',
      "</page>",
      "</bill_ocr>",
    ].join("\n");

    const sent = await send(billRequest({ body: { ...sampleRequest, requestId: crypto.randomUUID(), ocrDocument } }));

    expect(last(sent)).toMatchObject({ type: "result" });
    const content = userMessage(ocrDocument, sampleRequest.instruction, sampleRequest.localCurrency);
    expect(content.startsWith(`${ocrDocument}\n\n`)).toBe(true);
    expect(seen[0].body.messages).toEqual([{ role: "user", content }]);
  });

  it("accepts an empty instruction and no local currency", async () => {
    const seen = model(ANSWER);

    const sent = await send(billRequest({ body: { ...sampleRequest, requestId: crypto.randomUUID(), instruction: "", localCurrency: "" } }));

    expect(last(sent)).toMatchObject({ type: "result" });
    expect(seen[0].body.messages).toEqual([{ role: "user", content: userMessage(sampleRequest.ocrDocument, "", "") }]);
  });

  it("refuses other methods and paths", async () => {
    const get = await send(new Request("https://proxy.test/v1/bill-analysis"));
    expect(get.status).toBe(405);
    expect(JSON.parse(get.body)).toEqual({ error: "method" });

    const other = await send(new Request("https://proxy.test/v1/other", { method: "POST" }));
    expect(other.status).toBe(404);
    expect(JSON.parse(other.body)).toEqual({ error: "path" });
  });

  it("is turned off by the AI switch, set to anything but true, before anything is counted", async () => {
    const seen = model(ANSWER);
    const install = crypto.randomUUID();

    for (const value of ["false", "", "off"]) {
      const sent = await send(billRequest({ install }), { AI_ENABLED: value });
      expect(sent.status).toBe(503);
      expect(JSON.parse(sent.body)).toEqual({ error: "ai_off" });
    }
    expect(seen).toHaveLength(0);
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
  });

  it("asks an app older than the minimum version to update", async () => {
    const seen = model(ANSWER);

    for (const version of [null, "4", "not a number"]) {
      const sent = await send(billRequest({ version }), { MIN_APP_VERSION: "5" });
      expect(sent.status).toBe(426);
      expect(JSON.parse(sent.body)).toEqual({ error: "update_required" });
    }
    expect(seen).toHaveLength(0);
    expect((await send(billRequest({ version: "5" }), { MIN_APP_VERSION: "5" })).status).toBe(200);
  });

  it("refuses an edge request that does not say where it came from", async () => {
    const seen = model(ANSWER);

    const sent = await send(billRequest({ ip: "", cf: { country: "VN" } }));

    expect(sent.status).toBe(400);
    expect(seen).toHaveLength(0);
  });
});

describe("the allowance", () => {
  it("lets an install that is not signed in read three bills, then refuses the fourth", async () => {
    const seen = model(ANSWER);
    const install = crypto.randomUUID();
    for (let i = 0; i < POLICY.trialCalls; i++) {
      expect(last(await send(billRequest({ install })))).toMatchObject({ type: "result" });
    }

    const fourth = await send(billRequest({ install }));

    expect(fourth.status).toBe(429);
    expect(fourth.headers.get("retry-after")).toBeNull();
    expect(JSON.parse(fourth.body)).toEqual({
      error: "quota_exhausted",
      usage: { limit: 3, used: 3, remaining: 0, period: "trial", resetsAt: null },
    });
    expect(seen).toHaveLength(POLICY.trialCalls);
  });

  it("charges a request once however often it runs", async () => {
    const seen = model(ANSWER);
    const install = crypto.randomUUID();
    const body = { ...sampleRequest, requestId: crypto.randomUUID() };

    await send(billRequest({ install, body }));
    const again = await send(billRequest({ install, body }));

    expect(last(again)).toMatchObject({ type: "result", usage: { used: 1, remaining: 2 } });
    expect(seen).toHaveLength(2);
  });

  it("charges a call the model answered even when the app hung up before the answer", async () => {
    let answer!: () => void;
    const answered = new Promise<void>((resolve) => {
      answer = resolve;
    });
    model(async () => {
      await answered;
      return streamed(ANSWER);
    });
    const install = crypto.randomUUID();
    const ctx = createExecutionContext();

    const response = await fetchWorker(billRequest({ install }), ctx);
    const reader = response.body!.getReader();
    await reader.read();
    await reader.cancel();
    answer();
    await waitOnExecutionContext(ctx);

    expect(await quotaOf(install)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("charges an answer the model refused, cut off, or wrote wrongly, and says why", async () => {
    const cases: Array<[Reply, string]> = [
      [{ ...ANSWER, stopReason: "refusal", stopDetails: { type: "refusal", category: null, explanation: null } }, "refused"],
      [{ ...ANSWER, stopReason: "max_tokens" }, "too_long"],
      [{ text: "[1, 2, 3]" }, "bad_response"],
    ];
    for (const [reply, code] of cases) {
      model(reply);
      const install = crypto.randomUUID();

      const sent = await send(billRequest({ install }));

      expect(last(sent)).toEqual({ type: "error", code, usage: { limit: 3, used: 1, remaining: 2, period: "trial", resetsAt: null } });
      network.resetHandlers();
    }
  });

  it("keeps the hold of a run still going when another run of the same request fails", async () => {
    let reached!: () => void;
    const firstReached = new Promise<void>((resolve) => {
      reached = resolve;
    });
    let answer!: () => void;
    const answered = new Promise<void>((resolve) => {
      answer = resolve;
    });
    let calls = 0;
    model(async () => {
      calls += 1;
      if (calls > 1) return apiError(500, "api_error");
      reached();
      await answered;
      return streamed(ANSWER);
    });
    const install = crypto.randomUUID();
    const body = { ...sampleRequest, requestId: crypto.randomUUID() };
    const ctx = createExecutionContext();
    const first = await fetchWorker(billRequest({ install, body }), ctx);
    await firstReached;

    const second = await send(billRequest({ install, body }));

    expect(last(second)).toEqual({ type: "error", code: "upstream" });
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 2 });
    answer();
    await first.arrayBuffer();
    await waitOnExecutionContext(ctx);
    expect(await quotaOf(install)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("gives the call back when the model fails before it answers", async () => {
    const cases: Array<[() => Response, string]> = [
      [() => apiError(500, "api_error"), "upstream"],
      [() => apiError(529, "overloaded_error"), "busy"],
      [() => apiError(429, "rate_limit_error"), "busy"],
      [() => HttpResponse.error(), "upstream"],
    ];
    for (const [reply, code] of cases) {
      const seen = model(reply);
      const install = crypto.randomUUID();

      const sent = await send(billRequest({ install }));

      expect(last(sent)).toEqual({ type: "error", code });
      expect(seen).toHaveLength(1 + POLICY.modelRetries);
      expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
      expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 0, held: 0 });
      network.resetHandlers();
    }
  });
});

describe("spend", () => {
  it("settles the caller, the IP and the whole proxy at what the call cost", async () => {
    model({ ...ANSWER, usage: { input_tokens: 5_000, output_tokens: 2_000 } });
    const install = crypto.randomUUID();

    await send(billRequest({ install }));

    // 5,000 tokens in at $4 and 2,000 out at $20 per million tokens.
    const cost = { spent: 60_000, held: 0 };
    expect(await spendOf(`install:${install}`)).toMatchObject(cost);
    expect(await spendOf(await ipName(IP))).toMatchObject(cost);
    expect(await spendOf("global")).toMatchObject(cost);
  });

  it("charges each attempt of a refusal fallback at the price of its own model", async () => {
    const attempt = { input_tokens: 5_000, cache_creation_input_tokens: 0, cache_read_input_tokens: 0, cache_creation: null };
    model({
      ...ANSWER,
      model: "claude-opus-4-8",
      usage: {
        input_tokens: 5_000,
        output_tokens: 2_000,
        iterations: [
          { ...attempt, type: "message", model: POLICY.model, output_tokens: 0 },
          { ...attempt, type: "fallback_message", model: "claude-opus-4-8", output_tokens: 2_000 },
        ],
      },
    });
    const install = crypto.randomUUID();

    await send(billRequest({ install }));

    // The declined attempt: 5,000 in at $4. The fallback: 5,000 in at $5 and 2,000 out at $25.
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 95_000, held: 0 });
  });

  it("keeps the whole hold as spent when the model is overloaded after it started", async () => {
    const events = serverSentEvents(ANSWER);
    const started = events.slice(0, events.indexOf("event: content_block_start"));
    model(() => eventStream(`${started}event: error\ndata: {"type":"error","error":{"type":"overloaded_error","message":"test"}}\n\n`));
    const install = crypto.randomUUID();

    const sent = await send(billRequest({ install }));

    expect(last(sent)).toEqual({ type: "error", code: "busy" });
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
    const prompt = userMessage(sampleRequest.ocrDocument, sampleRequest.instruction, sampleRequest.localCurrency);
    const bytes = (text: string) => new TextEncoder().encode(text).byteLength;
    const held = holdMicro(bytes(SYSTEM_PROMPT) + bytes(prompt) + bytes(JSON.stringify(BILL_SCHEMA)), POLICY.maxTokens);
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: held, held: 0 });
  });

  it("refuses a call over the caller's daily spend cap without holding anything", async () => {
    const seen = model(ANSWER);
    const install = crypto.randomUUID();
    const caller = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(`install:${install}`));
    await caller.holdSpend(Date.now(), POLICY.callerCapMicro, POLICY.callerCapMicro);

    const sent = await send(billRequest({ install }));

    expect(sent.status).toBe(429);
    expect(JSON.parse(sent.body)).toEqual({ error: "spend_limited" });
    expect(Number(sent.headers.get("retry-after"))).toBeGreaterThan(0);
    expect(seen).toHaveLength(0);
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
    expect(await spendOf(await ipName(IP))).toMatchObject({ spent: 0, held: 0 });
    expect(await spendOf("global")).toMatchObject({ spent: 0, held: 0 });
  });

  it("says the AI is busy when the proxy's own daily cap is reached", async () => {
    const seen = model(ANSWER);
    const global = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName("global"));
    await global.holdSpend(Date.now(), POLICY.globalCapMicro, POLICY.globalCapMicro);

    const sent = await send(billRequest());

    expect(sent.status).toBe(429);
    expect(JSON.parse(sent.body)).toEqual({ error: "busy" });
    expect(Number(sent.headers.get("retry-after"))).toBeGreaterThan(0);
    expect(seen).toHaveLength(0);
  });
});

it("limits the requests from one IP, across installs", async () => {
  const seen = model(ANSWER);
  for (let i = 0; i < POLICY.ip.perMinute; i++) {
    expect((await send(billRequest())).status).toBe(200);
  }

  const over = await send(billRequest());

  expect(over.status).toBe(429);
  expect(JSON.parse(over.body)).toEqual({ error: "rate_limited" });
  expect(Number(over.headers.get("retry-after"))).toBeGreaterThan(0);
  expect(seen).toHaveLength(POLICY.ip.perMinute);
  expect((await send(billRequest({ ip: "198.51.100.1" }))).status).toBe(200);
});
