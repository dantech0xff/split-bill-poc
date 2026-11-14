import Anthropic from "@anthropic-ai/sdk";
import { callerOf, ledger, quotaBook } from "./identity";
import type { Env } from "./index";
import { billRequestOf, readJson } from "./input";
import { clientIp, hashed, ipKey } from "./ledger";
import { POLICY } from "./policy";
import { SYSTEM_PROMPT, userMessage } from "./prompt";
import { errorResponse, lineStream } from "./respond";
import { BILL_SCHEMA } from "./schema";
import { costMicro, errorName, holdAll, holdMicro, settleAll, type SpendCap } from "./spend";
import { aiEnabled, appVersionOk } from "./switches";

type Message = Anthropic.Beta.BetaMessage;

/** How long things may take. Tests shorten them; the Worker uses [TIMING]. */
export type Timing = { waitLineMs: number; afterHangUpMs: number; modelDeadlineMs: number };

export const TIMING: Timing = {
  waitLineMs: POLICY.waitLineMs,
  afterHangUpMs: POLICY.afterHangUpMs,
  modelDeadlineMs: POLICY.modelDeadlineMs,
};

const SCHEMA_BYTES = byteLength(JSON.stringify(BILL_SCHEMA));
const SYSTEM_BYTES = byteLength(SYSTEM_PROMPT);

/**
 * `POST /v1/bill-analysis`: reads one bill with the model.
 *
 * Before the model runs, the request passes, in order: the AI switch, the app's version, the
 * caller, the contract check, the rate limit of its IP, the caller's allowance (a call is held
 * against it), and the spend caps of the caller, the IP and the whole proxy (the most the call can
 * cost is held against each). A refusal at any step is an error reply, and what earlier steps held
 * is given back.
 *
 * Then the reply streams as NDJSON while the model works. The call is charged once the model has
 * answered, whatever the answer says and also when the app has hung up, as long as the answer
 * comes within [Timing.afterHangUpMs] of the hang-up; otherwise the model is stopped. A call that
 * got no answer is given back. Spend is settled at what the call cost, exactly once.
 */
export async function billAnalysis(request: Request, env: Env, ctx: ExecutionContext, timing: Timing = TIMING): Promise<Response> {
  const started = Date.now();
  if (!aiEnabled(env)) return refused(started, "ai_off", errorResponse(503, "ai_off"));
  if (env.ANTHROPIC_API_KEY.trim() === "") return refused(started, "not_configured", errorResponse(503, "not_configured"));
  if (!appVersionOk(request, env)) return refused(started, "update_required", errorResponse(426, "update_required"));
  const who = await callerOf(request, env);
  if (!who.ok) return refused(started, who.code, errorResponse(401, who.code));
  const caller = who.caller;
  const input = billRequestOf(await readJson(request, POLICY.maxBodyBytes));
  if (input === null) return refused(started, "input", errorResponse(400, "input"));
  const ip = clientIp(request);
  if (!ip.ok) return refused(started, "input", errorResponse(400, "input"));

  const now = Date.now();
  const ipLedger = ledger(env, `ip:${await hashed(ipKey(ip.ip), env.HASH_SALT)}`);
  const rate = await ipLedger.admit(now, POLICY.ip);
  if (!rate.ok) return refused(started, "rate_limited", errorResponse(429, "rate_limited", { retryAfter: rate.retryAfter }));

  const prompt = userMessage(input.ocrDocument, input.instruction, input.localCurrency);
  // Ties the request id to this bill, so that a run again under the same id is the same request.
  const digest = await hashed(prompt, env.HASH_SALT);
  const quota = quotaBook(env, caller.name);
  const hold = await quota.hold(now, input.requestId, caller.allowance, digest);
  if (!hold.ok) {
    if (hold.code === "request_mismatch") return refused(started, hold.code, errorResponse(400, "input"));
    return refused(started, hold.code, errorResponse(429, hold.code, { retryAfter: hold.retryAfter, extra: { usage: hold.usage } }));
  }

  const held = holdMicro(SYSTEM_BYTES + byteLength(prompt) + SCHEMA_BYTES);
  const caps: SpendCap[] = [
    { ledger: ledger(env, caller.name), cap: POLICY.callerCapMicro, code: "spend_limited" },
    { ledger: ipLedger, cap: POLICY.ipCapMicro, code: "spend_limited" },
    { ledger: ledger(env, "global"), cap: POLICY.globalCapMicro, code: "busy" },
  ];
  let spend: Awaited<ReturnType<typeof holdAll>>;
  try {
    spend = await holdAll(caps, now, held);
  } catch (error) {
    await quota.release(now, input.requestId).catch(() => undefined);
    throw error;
  }
  if (!spend.ok) {
    await quota.release(now, input.requestId);
    return refused(started, spend.code, errorResponse(429, spend.code, { retryAfter: spend.retryAfter }));
  }

  return lineStream(ctx, { waitMs: timing.waitLineMs, afterHangUpMs: timing.afterHangUpMs }, async (send, hungUp) => {
    // Whatever happens below, the hold is given back or charged, and the spend settled, once.
    let answered = false;
    let reachedModel = false;
    let cost = 0;
    try {
      const reply = await askModel(env, prompt, AbortSignal.any([AbortSignal.timeout(timing.modelDeadlineMs), hungUp]));
      reachedModel = reply.reachedModel;
      if (!reply.ok) {
        const code = hungUp.aborted ? "client_gone" : failureCode(reply.error);
        send({ type: "error", code });
        log({ outcome: code, caller: caller.kind, model: POLICY.model, ms: Date.now() - started });
        return;
      }
      const message = reply.message;
      answered = true;
      cost = costMicro(message);
      // The answer is paid for, so it goes out even if the count cannot be updated just now.
      const usage = await quota.commit(Date.now(), input.requestId, caller.allowance, digest).catch((error: unknown) => {
        console.error(JSON.stringify({ event: "commit_failed", error: errorName(error) }));
        return undefined;
      });
      const answer = answerOf(message);
      send(answer.ok ? { type: "result", answer: answer.value, usage } : { type: "error", code: answer.code, usage });
      log({
        outcome: answer.ok ? "ok" : answer.code,
        caller: caller.kind,
        model: message.model,
        inputTokens: message.usage.input_tokens,
        outputTokens: message.usage.output_tokens,
        costMicro: cost,
        refusal: message.stop_reason === "refusal" ? (message.stop_details?.category ?? null) : undefined,
        clientGone: hungUp.aborted || undefined,
        ms: Date.now() - started,
      });
    } finally {
      // When the model was reached but did not answer, what it was billed for cannot be known, so
      // the whole hold counts as spent.
      await settleAll(caps, held, answered ? cost : reachedModel ? held : 0);
      if (!answered) {
        await quota.release(Date.now(), input.requestId).catch((error: unknown) => {
          console.error(JSON.stringify({ event: "release_failed", error: errorName(error) }));
        });
      }
    }
  });
}

type ModelReply = { ok: true; message: Message; reachedModel: true } | { ok: false; error: unknown; reachedModel: boolean };

async function askModel(env: Env, prompt: string, signal: AbortSignal): Promise<ModelReply> {
  const client = new Anthropic({
    apiKey: env.ANTHROPIC_API_KEY,
    maxRetries: POLICY.modelRetries,
    fetch: shortRetriesOnly,
    // The SDK may print a piece of a broken answer when it logs; nothing of a bill goes to the logs.
    logLevel: "off",
  });
  let reachedModel = false;
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
    { signal },
  );
  // From the moment the API accepted the request, the model may be billing for it.
  stream.on("connect", () => {
    reachedModel = true;
  });
  try {
    return { ok: true, message: await stream.finalMessage(), reachedModel: true };
  } catch (error) {
    return { ok: false, error, reachedModel };
  }
}

/**
 * The SDK waits as long as the API's `retry-after` says before it retries. A wait longer than the
 * app can afford is not waited for: the call fails at once as busy instead.
 */
async function shortRetriesOnly(input: string | URL | Request, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, init);
  if ((response.status === 429 || response.status === 529) && retryAfterMs(response.headers) > POLICY.maxRetryWaitMs) {
    const headers = new Headers(response.headers);
    headers.set("x-should-retry", "false");
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
  }
  return response;
}

function retryAfterMs(headers: Headers): number {
  const millis = Number.parseFloat(headers.get("retry-after-ms") ?? "");
  if (Number.isFinite(millis)) return millis;
  const value = headers.get("retry-after") ?? "";
  const seconds = Number.parseFloat(value);
  if (Number.isFinite(seconds)) return seconds * 1000;
  const date = Date.parse(value);
  return Number.isFinite(date) ? date - Date.now() : 0;
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
  // An overload reported in the middle of the answer comes as an error event, without a status.
  if (error instanceof Anthropic.APIError && (error.type === "overloaded_error" || error.type === "rate_limit_error")) return "busy";
  return "upstream";
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
