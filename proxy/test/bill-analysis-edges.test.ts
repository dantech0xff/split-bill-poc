import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { billAnalysis } from "../src/bill-analysis";
import { TIMING, type Timing } from "../src/model-call";
import type { Env } from "../src/index";
import { POLICY } from "../src/policy";
import { SYSTEM_PROMPT, userMessage } from "../src/prompt";
import { BILL_SCHEMA } from "../src/schema";
import { holdMicro } from "../src/spend";
import { apiError, serverSentEvents, sseOf, stalled, streamed } from "./fake-model";
import {
  ANSWER,
  billAnswer,
  billRequest,
  fetchWorker,
  IP,
  ipName,
  last,
  model,
  proxyRequest,
  quotaOf,
  sampleRequest,
  send,
  type Sent,
  spendOf,
} from "./requests";

/** The hold of a read of the sample bill: what is counted as spent when the model was reached but did not answer. */
const SAMPLE_HOLD = (() => {
  const bytes = (text: string) => new TextEncoder().encode(text).byteLength;
  const prompt = userMessage(sampleRequest.ocrDocument, sampleRequest.instruction, sampleRequest.localCurrency);
  return holdMicro(bytes(SYSTEM_PROMPT) + bytes(prompt) + bytes(JSON.stringify(BILL_SCHEMA)), POLICY.maxTokens);
})();

/** The start of the sample answer, up to and including `message_start`. */
const MESSAGE_START = (() => {
  const events = serverSentEvents(ANSWER);
  return events.slice(0, events.indexOf("event: content_block_start"));
})();

/** Sends [request] straight to the route with [timing], and reads the whole reply once the work is done. */
async function sendTimed(request: Request, timing: Partial<Timing>): Promise<Sent> {
  const ctx = createExecutionContext();
  const response = await billAnalysis(request, env, ctx, { ...TIMING, ...timing });
  const body = new TextDecoder().decode(await response.arrayBuffer());
  await waitOnExecutionContext(ctx);
  const lines = body.trim() === "" ? [] : body.trim().split("\n").map((line) => JSON.parse(line) as Record<string, unknown>);
  return { status: response.status, headers: response.headers, body, lines };
}

/** Starts a read straight on the route with [timing], takes its first line, and hangs up. */
async function hangUpOn(request: Request, timing: Partial<Timing>) {
  const ctx = createExecutionContext();
  const response = await billAnalysis(request, env, ctx, { ...TIMING, ...timing });
  const reader = response.body!.getReader();
  await reader.read();
  await reader.cancel();
  await waitOnExecutionContext(ctx);
}

function never(): Promise<Response> {
  return new Promise<Response>(() => undefined);
}

describe("a read the app hung up on", () => {
  it("is stopped when the model does not answer in time, given back, and costs nothing when the model was never reached", async () => {
    model(never);
    const install = crypto.randomUUID();

    await hangUpOn(billRequest({ install }), { afterHangUpMs: 50 });

    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 0, held: 0 });
  });

  it("keeps the whole hold as spent when the model had started", async () => {
    model((request) => stalled(MESSAGE_START, request.signal));
    const install = crypto.randomUUID();

    await hangUpOn(billRequest({ install }), { afterHangUpMs: 50 });

    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: SAMPLE_HOLD, held: 0 });
  });
});

describe("a model that takes too long", () => {
  it("ends as a timeout, given back, and costs nothing when it never answered", async () => {
    model(never);
    const install = crypto.randomUUID();

    const sent = await sendTimed(billRequest({ install }), { modelDeadlineMs: 100 });

    expect(last(sent)).toEqual({ type: "error", code: "timeout" });
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 0, held: 0 });
  });

  it("keeps the whole hold as spent when it had started", async () => {
    model((request) => stalled(MESSAGE_START, request.signal));
    const install = crypto.randomUUID();

    const sent = await sendTimed(billRequest({ install }), { modelDeadlineMs: 100 });

    expect(last(sent)).toEqual({ type: "error", code: "timeout" });
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: SAMPLE_HOLD, held: 0 });
  });
});

it("keeps the whole hold as spent when the answer stops before its last event", async () => {
  const events = serverSentEvents(ANSWER);
  model(() => new HttpResponse(events.slice(0, events.indexOf("event: message_delta")), { headers: { "content-type": "text/event-stream" } }));
  const install = crypto.randomUUID();

  const sent = await send(billRequest({ install }));

  expect(last(sent)).toEqual({ type: "error", code: "upstream" });
  expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
  expect(await spendOf(`install:${install}`)).toMatchObject({ spent: SAMPLE_HOLD, held: 0 });
});

it("does not wait for the model's retry when the API asks for a long wait, and calls it busy", async () => {
  const tooLong = model(() => {
    const reply = apiError(429, "rate_limit_error");
    reply.headers.set("retry-after", String(POLICY.maxRetryWaitMs / 1000 + 90));
    return reply;
  });
  const install = crypto.randomUUID();
  const startedAt = Date.now();

  const sent = await send(billRequest({ install }));

  expect(last(sent)).toEqual({ type: "error", code: "busy" });
  expect(tooLong).toHaveLength(1);
  expect(Date.now() - startedAt).toBeLessThan(POLICY.maxRetryWaitMs);
  expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
});

describe("a request id", () => {
  it("cannot be used again for another bill", async () => {
    const seen = model(ANSWER);
    const install = crypto.randomUUID();
    const requestId = crypto.randomUUID();
    await send(billRequest({ install, body: { ...sampleRequest, requestId } }));

    const other = await send(billRequest({ install, body: { ...sampleRequest, requestId, instruction: "Split it four ways" } }));

    expect(other.status).toBe(400);
    expect(JSON.parse(other.body)).toEqual({ error: "input" });
    expect(seen).toHaveLength(1);
    expect(await quotaOf(install)).toMatchObject({ used: 1, remaining: 2 });
  });

  it("is refused on its fourth run, with the calls left and no time to wait", async () => {
    model(ANSWER);
    const install = crypto.randomUUID();
    const body = { ...sampleRequest, requestId: crypto.randomUUID() };
    for (let run = 0; run < POLICY.runsPerRequest; run++) await send(billRequest({ install, body }));

    const fourth = await send(billRequest({ install, body }));

    expect(fourth.status).toBe(429);
    expect(fourth.headers.get("retry-after")).toBeNull();
    expect(JSON.parse(fourth.body)).toMatchObject({ error: "retry_limit", usage: { used: 1, remaining: 2 } });
  });

  it("keeps the hold of a run again while the first run fails", async () => {
    const reached: Array<() => void> = [];
    const answers: Array<(reply: Response) => void> = [];
    model(
      () =>
        new Promise<Response>((resolve) => {
          answers.push(resolve);
          reached.shift()?.();
        }),
    );
    const until = (count: number) => new Promise<void>((resolve) => (answers.length >= count ? resolve() : reached.push(resolve)));
    const install = crypto.randomUUID();
    const body = { ...sampleRequest, requestId: crypto.randomUUID() };
    const first = createExecutionContext();
    const again = createExecutionContext();
    const firstReply = await fetchWorker(billRequest({ install, body }), first);
    await until(1);
    const againReply = await fetchWorker(billRequest({ install, body }), again);
    await until(2);

    // A request the API turns down is not retried, so the first run ends here.
    answers[0](apiError(400, "invalid_request_error"));
    await firstReply.arrayBuffer();
    await waitOnExecutionContext(first);
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 2 });

    answers[1](streamed(ANSWER));
    await againReply.arrayBuffer();
    await waitOnExecutionContext(again);
    expect(await quotaOf(install)).toMatchObject({ used: 1, remaining: 2 });
  });
});

it("reads the answer across the switch to a fallback model", async () => {
  const text = JSON.stringify(billAnswer);
  const half = Math.floor(text.length / 2);
  const usage = { input_tokens: 5_000, output_tokens: 1, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 };
  model(() =>
    new HttpResponse(
      sseOf([
        { type: "message_start", message: { id: "msg_test", type: "message", role: "assistant", model: "claude-opus-4-8", content: [], stop_reason: null, stop_sequence: null, usage } },
        { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } },
        { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: text.slice(0, half) } },
        { type: "content_block_stop", index: 0 },
        { type: "content_block_start", index: 1, content_block: { type: "fallback", from: { model: POLICY.model }, to: { model: "claude-opus-4-8" } } },
        { type: "content_block_stop", index: 1 },
        { type: "content_block_start", index: 2, content_block: { type: "text", text: "" } },
        { type: "content_block_delta", index: 2, delta: { type: "text_delta", text: text.slice(half) } },
        { type: "content_block_stop", index: 2 },
        { type: "message_delta", delta: { stop_reason: "end_turn", stop_sequence: null, stop_details: null }, usage: { output_tokens: 2_000, iterations: null } },
        { type: "message_stop" },
      ]),
      { headers: { "content-type": "text/event-stream" } },
    ),
  );

  const sent = await send(billRequest());

  expect(last(sent)).toMatchObject({ type: "result", answer: billAnswer });
});

it("refuses a call over its IP's daily spend cap, and gives back what the other caps held", async () => {
  const seen = model(ANSWER);
  const install = crypto.randomUUID();
  const ipLedger = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(await ipName(IP)));
  await ipLedger.holdSpend(Date.now(), POLICY.ipCapMicro, POLICY.ipCapMicro);

  const sent = await send(billRequest({ install }));

  expect(sent.status).toBe(429);
  expect(JSON.parse(sent.body)).toEqual({ error: "spend_limited" });
  expect(Number(sent.headers.get("retry-after"))).toBeGreaterThan(0);
  expect(seen).toHaveLength(0);
  expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
  expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 0, held: 0 });
  expect(await spendOf("global")).toMatchObject({ spent: 0, held: 0 });
});

it("counts IPv6 callers by their /64", async () => {
  model(ANSWER);
  for (let i = 1; i <= POLICY.ip.perMinute; i++) {
    expect((await send(billRequest({ ip: `2001:db8::${i.toString(16)}` }))).status).toBe(200);
  }

  expect((await send(billRequest({ ip: "2001:db8::ff:1" }))).status).toBe(429);
  expect((await send(billRequest({ ip: "2001:db8:0:1::1" }))).status).toBe(200);
});

/** The worker's settings with one spend ledger that fails at [method] when the object is named [name]. */
function failingLedger(name: string, method: "holdSpend" | "settleSpend"): Partial<Env> {
  const real = env.ABUSE_LEDGER;
  const names = new WeakMap<object, string>();
  const namespace = {
    idFromName(objectName: string) {
      const id = real.idFromName(objectName);
      names.set(id, objectName);
      return id;
    },
    get(id: DurableObjectId) {
      const stub = real.get(id);
      if (names.get(id) !== name) return stub;
      const fail = () => Promise.reject(new Error(`${method} failed`));
      return {
        admit: (now: number, limit: typeof POLICY.ip) => stub.admit(now, limit),
        holdSpend: method === "holdSpend" ? fail : (now: number, amount: number, cap: number) => stub.holdSpend(now, amount, cap),
        settleSpend: method === "settleSpend" ? fail : (now: number, held: number, actual: number) => stub.settleSpend(now, held, actual),
        forget: () => stub.forget(),
      };
    },
  };
  return { ABUSE_LEDGER: namespace as unknown as Env["ABUSE_LEDGER"] };
}

describe("a counter that cannot be reached", () => {
  it("while the caps are held gives back the holds the other caps and the allowance took", async () => {
    const seen = model(ANSWER);
    const install = crypto.randomUUID();

    const sent = await send(billRequest({ install }), failingLedger("global", "holdSpend"));

    expect(sent.status).toBe(500);
    expect(JSON.parse(sent.body)).toEqual({ error: "internal" });
    expect(seen).toHaveLength(0);
    expect(await quotaOf(install)).toMatchObject({ used: 0, remaining: 3 });
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 0, held: 0 });
    expect(await spendOf(await ipName(IP))).toMatchObject({ spent: 0, held: 0 });
  });

  it("after the answer went out adds no second line, and the other caps are still settled", async () => {
    model(ANSWER);
    const install = crypto.randomUUID();

    const sent = await send(billRequest({ install }), failingLedger("global", "settleSpend"));

    expect(sent.lines.filter((line) => line.type !== "wait")).toEqual([
      { type: "result", answer: billAnswer, usage: { limit: 3, used: 1, remaining: 2, period: "trial", resetsAt: null, plan: "trial" } },
    ]);
    expect(await spendOf(`install:${install}`)).toMatchObject({ spent: 60_000, held: 0 });
  });
});

describe("a proxy that is missing a setting", () => {
  it("refuses everything without a long enough salt for its hashes", async () => {
    // A secret that was never set is not there at all, which is not the same as empty.
    for (const salt of ["short", undefined]) {
      for (const request of [billRequest(), proxyRequest("GET", "/v1/usage"), proxyRequest("POST", "/v1/session", {}, "{}")]) {
        const sent = await send(request, { HASH_SALT: salt });
        expect(sent.status).toBe(503);
        expect(JSON.parse(sent.body)).toEqual({ error: "not_configured" });
      }
    }
  });

  it("refuses to read bills without the Anthropic key, also before the key was ever set", async () => {
    const seen = model(ANSWER);

    for (const key of ["", undefined]) {
      const sent = await send(billRequest(), { ANTHROPIC_API_KEY: key });

      expect(sent.status).toBe(503);
      expect(JSON.parse(sent.body)).toEqual({ error: "not_configured" });
    }
    expect(seen).toHaveLength(0);
  });
});
