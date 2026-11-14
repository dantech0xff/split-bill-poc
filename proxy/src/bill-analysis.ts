import Anthropic from "@anthropic-ai/sdk";
import { installCaller } from "./identity";
import type { Env } from "./index";
import { billRequestOf, readJson } from "./input";
import { type AbuseLedger, clientIp, hashed } from "./ledger";
import { POLICY } from "./policy";
import { SYSTEM_PROMPT, userMessage } from "./prompt";
import { errorResponse, lineStream } from "./respond";
import { BILL_SCHEMA } from "./schema";
import { costMicro, holdAll, holdMicro, settleAll, type SpendCap } from "./spend";

type Message = Anthropic.Beta.BetaMessage;

const SCHEMA_BYTES = byteLength(JSON.stringify(BILL_SCHEMA));
const SYSTEM_BYTES = byteLength(SYSTEM_PROMPT);

/**
 * `POST /v1/bill-analysis`: reads one bill with the model.
 *
 * Before the model runs, the request passes, in order: the contract check, the rate limit of its
 * IP, the caller's allowance (a call is held against it), and the spend caps of the caller, the IP
 * and the whole proxy (the most the call can cost is held against each). A refusal at any step is
 * an error reply, and what earlier steps held is given back.
 *
 * Then the reply streams as NDJSON while the model works. The call is charged once the model has
 * answered, whatever the answer says and whether or not the app is still listening; it is given
 * back when the model did not answer. Spend is settled at what the call cost.
 */
export async function billAnalysis(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const started = Date.now();
  const caller = installCaller(request);
  if (caller === null) return refused(started, "install", errorResponse(401, "install"));
  const input = billRequestOf(await readJson(request, POLICY.maxBodyBytes));
  if (input === null) return refused(started, "input", errorResponse(400, "input"));
  const ip = clientIp(request);
  if (!ip.ok) return refused(started, "input", errorResponse(400, "input"));

  const now = Date.now();
  const ipLedger = ledger(env, `ip:${await hashed(ip.ip, env.HASH_SALT)}`);
  const rate = await ipLedger.admit(now, POLICY.ip);
  if (!rate.ok) return refused(started, "rate_limited", errorResponse(429, "rate_limited", { retryAfter: rate.retryAfter }));

  const quota = env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(caller.name));
  const hold = await quota.hold(now, input.requestId, caller.allowance);
  if (!hold.ok) {
    return refused(started, hold.code, errorResponse(429, hold.code, { retryAfter: hold.retryAfter, extra: { usage: hold.usage } }));
  }

  const prompt = userMessage(input.ocrDocument, input.instruction, input.localCurrency);
  const held = holdMicro(SYSTEM_BYTES + byteLength(prompt) + SCHEMA_BYTES);
  const caps: SpendCap[] = [
    { ledger: ledger(env, caller.name), cap: POLICY.callerCapMicro, code: "spend_limited" },
    { ledger: ipLedger, cap: POLICY.ipCapMicro, code: "spend_limited" },
    { ledger: ledger(env, "global"), cap: POLICY.globalCapMicro, code: "busy" },
  ];
  const spend = await holdAll(caps, now, held);
  if (!spend.ok) {
    if (hold.newHold) await quota.release(now, input.requestId);
    return refused(started, spend.code, errorResponse(429, spend.code, { retryAfter: spend.retryAfter }));
  }

  return lineStream(ctx, POLICY.waitLineMs, async (send) => {
    const reply = await askModel(env, prompt);
    if (!reply.ok) {
      // No answer came back, so the call is not charged. Spend is settled at the whole hold when
      // the model had started, since what it was billed for cannot be known.
      if (hold.newHold) await quota.release(Date.now(), input.requestId);
      await settleAll(caps, held, reply.started ? held : 0);
      const code = failureCode(reply.error);
      send({ type: "error", code });
      log({ outcome: code, caller: caller.kind, model: POLICY.model, ms: Date.now() - started });
      return;
    }
    const message = reply.message;
    const usage = await quota.commit(Date.now(), input.requestId, caller.allowance);
    const answer = answerOf(message);
    send(answer.ok ? { type: "result", answer: answer.value, usage } : { type: "error", code: answer.code, usage });
    const cost = costMicro(message);
    await settleAll(caps, held, cost);
    log({
      outcome: answer.ok ? "ok" : answer.code,
      caller: caller.kind,
      model: message.model,
      inputTokens: message.usage.input_tokens,
      outputTokens: message.usage.output_tokens,
      costMicro: cost,
      refusal: message.stop_reason === "refusal" ? (message.stop_details?.category ?? null) : undefined,
      ms: Date.now() - started,
    });
  });
}

type ModelReply = { ok: true; message: Message } | { ok: false; error: unknown; started: boolean };

async function askModel(env: Env, prompt: string): Promise<ModelReply> {
  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: POLICY.modelRetries });
  const stream = client.beta.messages.stream(
    {
      model: POLICY.model,
      max_tokens: POLICY.maxTokens,
      system: SYSTEM_PROMPT,
      messages: [{ role: "user", content: prompt }],
      output_config: { effort: POLICY.effort, format: { type: "json_schema", schema: BILL_SCHEMA } },
      // When a safety classifier declines the request, the API runs it again on the model it
      // recommends for that case instead of returning the refusal.
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
    },
    { signal: AbortSignal.timeout(POLICY.modelDeadlineMs) },
  );
  try {
    return { ok: true, message: await stream.finalMessage() };
  } catch (error) {
    return { ok: false, error, started: stream.currentMessage !== undefined };
  }
}

/** The bill the model wrote, or why there is none. Structured outputs keep a finished answer to the schema. */
function answerOf(message: Message): { ok: true; value: Record<string, unknown> } | { ok: false; code: string } {
  if (message.stop_reason === "refusal") return { ok: false, code: "refused" };
  if (message.stop_reason === "max_tokens") return { ok: false, code: "too_long" };
  if (message.stop_reason !== "end_turn") return { ok: false, code: "bad_response" };
  // Text blocks only: thinking blocks, and the marker a fallback leaves, hold no part of the answer.
  const text = message.content.flatMap((block) => (block.type === "text" ? [block.text] : [])).join("");
  try {
    const value: unknown = JSON.parse(text);
    if (typeof value === "object" && value !== null && !Array.isArray(value)) {
      return { ok: true, value: value as Record<string, unknown> };
    }
  } catch {
    // Reported below.
  }
  return { ok: false, code: "bad_response" };
}

function failureCode(error: unknown): string {
  if (error instanceof Anthropic.APIUserAbortError || error instanceof Anthropic.APIConnectionTimeoutError) return "timeout";
  if (error instanceof Anthropic.RateLimitError || error instanceof Anthropic.OverloadedError) return "busy";
  return "upstream";
}

function ledger(env: Env, name: string): DurableObjectStub<AbuseLedger> {
  return env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(name));
}

function byteLength(text: string): number {
  return new TextEncoder().encode(text).byteLength;
}

function refused(started: number, outcome: string, response: Response): Response {
  log({ outcome, ms: Date.now() - started });
  return response;
}

/** One line per request, for cost and health. It never holds the bill, the instruction, a name, an install id or an IP. */
function log(fields: Record<string, unknown>) {
  console.log(JSON.stringify({ event: "bill_analysis", ...fields }));
}
