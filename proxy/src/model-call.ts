import Anthropic from "@anthropic-ai/sdk";
import { callerOf, ledger, quotaBook } from "./identity";
import type { Env } from "./index";
import { byteLength, readJson } from "./input";
import { clientIp, hashed, ipKey } from "./ledger";
import { count, type MetricEvent, type MetricNumbers } from "./metrics";
import { POLICY } from "./policy";
import type { Usage } from "./quota-book";
import { errorResponse, lineStream } from "./respond";
import { costMicro, errorName, holdAll, holdMicro, isPriced, settleAll, type SpendCap } from "./spend";
import { aiEnabled, appVersionOk, servedLocally } from "./switches";

type Message = Anthropic.Beta.BetaMessage;

/** How long things may take. Tests shorten them; the Worker uses [TIMING]. */
export type Timing = { waitLineMs: number; afterHangUpMs: number; modelDeadlineMs: number };

export const TIMING: Timing = {
  waitLineMs: POLICY.waitLineMs,
  afterHangUpMs: POLICY.afterHangUpMs,
  modelDeadlineMs: POLICY.modelDeadlineMs,
};

/** How the model is asked, for one call. */
export type ModelSettings = {
  /** The model, which must have a price in `spend.ts`. */
  model: string;
  /** Room for the model's thinking and its answer; the spend hold assumes all of it is used. */
  maxTokens: number;
  effort: "low" | "medium" | "high";
  /** Sent as `thinking` when set; without it, the model's default applies. */
  thinking?: { type: "adaptive" };
  /**
   * Whether a request a safety classifier declines runs again on the model the API recommends
   * for that case, instead of ending as a refusal.
   */
  fallback: boolean;
  /**
   * Whether the line that ends the answer also says which model answered, with its tokens and the
   * cost of the call, as the evaluation on a computer needs; the app never asks for it.
   */
  report: boolean;
};

/** One kind of call to the model: what a bill read and an edit of a split do differently. */
export type ModelTask<Input extends { requestId: string }> = {
  /** The event of its log lines and its data points. */
  event: Extract<MetricEvent, "bill_analysis" | "split_revision">;
  /** What one call takes of the caller's allowance, in hundredths of a call. */
  weight: number;
  /** The largest body read, in bytes; a longer one is refused as input. */
  maxBodyBytes: number;
  /** The request when [body] follows the contract, or null. [request] carries the headers. */
  inputOf(body: unknown, request: Request): Input | null;
  /** The system prompt and the schema of the answer, the same for every call. */
  system: string;
  schema: Record<string, unknown>;
  /** The one user message of a call. Its digest also ties the request id to what it asks. */
  userMessage(input: Input): string;
  settings(input: Input): ModelSettings;
  /**
   * The answer the app gets, from the JSON object the model wrote, or null when it breaks the
   * contract. Such an answer ends as the error `bad_response`: charged as an answer when
   * [chargeBadAnswer] is true, given back as a failure of the model otherwise.
   */
  answerOf(value: Record<string, unknown>): Record<string, unknown> | null;
  chargeBadAnswer: boolean;
};

/** The header with which an evaluation on a computer names the model of one call; see [modelOf]. */
export const EVAL_MODEL_HEADER = "X-SplitVibe-Eval-Model";

/** The model a call asks, and whether the line that ends its answer reports the model, its tokens and the cost. */
export type ModelChoice = { model: string; report: boolean };

/**
 * The model of a call: [own], the route's model in `POLICY`, unless the proxy runs on a computer
 * and the request names in [EVAL_MODEL_HEADER] another model the proxy has a price for, as the
 * evaluations of bill reads and of edits do; the line that ends its answer then reports the model,
 * its tokens and the cost. The deployed proxy is never asked at a computer's hostname, so there the
 * header changes nothing. Null for a model without a price, which a computer refuses as input.
 */
export function modelOf(request: Request, own: string): ModelChoice | null {
  const named = request.headers.get(EVAL_MODEL_HEADER)?.trim() ?? "";
  if (named === "" || !servedLocally(new URL(request.url).hostname)) return { model: own, report: false };
  return isPriced(named) ? { model: named, report: true } : null;
}

/**
 * One call to the model, from a route of the app.
 *
 * Before the model runs, the request passes, in order: the AI switch, the app's version, the
 * caller, the contract check, the rate limit of its IP, the caller's allowance (the call's weight
 * is held against it), and the spend caps of the caller, the IP and the whole proxy (the most the
 * call can cost is held against each, and for an install that is not signed in against the
 * installs' pool inside the proxy's cap too). A refusal at any step is an error reply, and what
 * earlier steps held is given back.
 *
 * Then the reply streams as NDJSON while the model works. The call is charged once the model has
 * answered, also when the app has hung up, as long as the answer comes within
 * [Timing.afterHangUpMs] of the hang-up; otherwise the model is stopped. A call that got no
 * answer, or an answer [ModelTask.answerOf] refuses where the task does not charge for it, is
 * given back. Spend is settled at what the call cost, exactly once.
 *
 * Every call ends in one log line and one data point of [ModelTask.event], with its outcome, its
 * time and, once the model answered, its tokens and cost. A call cut short by an error thrown on
 * the way, which the Worker answers 500 `internal`, or the stream ends with the error line
 * `internal`, ends as `internal`. The first call an install holds is also counted as
 * `install_first_call`, with the same outcome.
 */
export async function callModel<Input extends { requestId: string }>(
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  task: ModelTask<Input>,
  timing: Timing,
): Promise<Response> {
  const started = Date.now();
  let first = false;
  let counted = false;
  const ended = (outcome: string, fields: Record<string, unknown>, numbers: MetricNumbers = {}) => {
    counted = true;
    const ms = Date.now() - started;
    log(task.event, { outcome, ...fields, ms });
    count(env, task.event, outcome, { ...numbers, ms });
    if (first) count(env, "install_first_call", outcome);
  };
  const refused = (outcome: string, response: Response): Response => {
    ended(outcome, {});
    return response;
  };
  try {
    if (!aiEnabled(env)) return refused("ai_off", errorResponse(503, "ai_off"));
    if ((env.ANTHROPIC_API_KEY ?? "").trim() === "") return refused("not_configured", errorResponse(503, "not_configured"));
    if (!appVersionOk(request, env)) return refused("update_required", errorResponse(426, "update_required"));
    const who = await callerOf(request, env);
    if (!who.ok) return refused(who.code, errorResponse(401, who.code));
    const caller = who.caller;
    const input = task.inputOf(await readJson(request, task.maxBodyBytes), request);
    if (input === null) return refused("input", errorResponse(400, "input"));
    const ip = clientIp(request);
    if (!ip.ok) return refused("input", errorResponse(400, "input"));

    const now = Date.now();
    const ipLedger = ledger(env, `ip:${await hashed(ipKey(ip.ip), env.HASH_SALT)}`);
    const rate = await ipLedger.admit(now, POLICY.ip);
    if (!rate.ok) return refused("rate_limited", errorResponse(429, "rate_limited", { retryAfter: rate.retryAfter }));

    const prompt = task.userMessage(input);
    const settings = task.settings(input);
    // Ties the request id to what it asks, so that a run again under the same id is the same request.
    const digest = await hashed(prompt, env.HASH_SALT);
    const quota = quotaBook(env, caller.name);
    // A session issued before its account was deleted is refused there, and nothing is held.
    const hold = await quota.hold(now, input.requestId, caller.allowance, digest, task.weight, caller.kind === "google" ? caller.issuedAt : undefined);
    if (!hold.ok) {
      if (hold.code === "session") return refused(hold.code, errorResponse(401, "session"));
      if (hold.code === "request_mismatch") return refused(hold.code, errorResponse(400, "input"));
      return refused(hold.code, errorResponse(429, hold.code, { retryAfter: hold.retryAfter, extra: { usage: hold.usage } }));
    }
    first = hold.first === true;

    // A call without a fallback is answered by its own model only, and held at that model's price.
    const held = holdMicro(
      byteLength(task.system) + byteLength(prompt) + byteLength(JSON.stringify(task.schema)),
      settings.maxTokens,
      settings.fallback ? undefined : settings.model,
    );
    // An install's call also counts against the installs' pool inside the global cap, so that
    // installs together never take what signed-in accounts keep of it.
    const pool = caller.kind === "install" ? { pool: POLICY.installCapMicro } : {};
    const caps: SpendCap[] = [
      { ledger: ledger(env, caller.name), cap: POLICY.callerCapMicro, code: "spend_limited", kind: "caller" },
      { ledger: ipLedger, cap: POLICY.ipCapMicro, code: "spend_limited", kind: "address" },
      { ledger: ledger(env, "global"), cap: POLICY.globalCapMicro, code: "busy", kind: "global", ...pool },
    ];
    let spend: Awaited<ReturnType<typeof holdAll>>;
    try {
      spend = await holdAll(caps, now, held);
    } catch (error) {
      await quota.release(now, input.requestId).catch(() => undefined);
      throw error;
    }
    // Once per cap and window, when a cap comes near its end: which cap, never whose.
    for (const cap of spend.near) {
      log("spend_cap_near", { cap });
      count(env, "spend_cap_near", cap);
    }
    if (!spend.ok) {
      await quota.release(now, input.requestId);
      return refused(spend.code, errorResponse(429, spend.code, { retryAfter: spend.retryAfter }));
    }

    return lineStream(ctx, { waitMs: timing.waitLineMs, afterHangUpMs: timing.afterHangUpMs }, async (send, hungUp) => {
      // Whatever happens below, the hold is given back or charged, and the spend settled, once.
      let reachedModel = false;
      let cost: number | null = null;
      let charged = false;
      try {
        const reply = await askModel(env, task, prompt, settings, AbortSignal.any([AbortSignal.timeout(timing.modelDeadlineMs), hungUp]));
        reachedModel = reply.reachedModel;
        if (!reply.ok) {
          const code = hungUp.aborted ? "client_gone" : failureCode(reply.error);
          send({ type: "error", code });
          ended(code, { caller: caller.kind, model: settings.model });
          return;
        }
        const message = reply.message;
        cost = costMicro(message);
        const answer = outcomeOf(message, task);
        let usage: Usage | undefined;
        if (answer.charged) {
          charged = true;
          // The answer is paid for, so it goes out even if the count cannot be updated just now.
          usage = await quota.commit(Date.now(), input.requestId, caller.allowance, digest, task.weight).catch((error: unknown) => {
            console.error(JSON.stringify({ event: "commit_failed", error: errorName(error) }));
            return undefined;
          });
        }
        const report = settings.report
          ? { eval: { model: message.model, inputTokens: message.usage.input_tokens, outputTokens: message.usage.output_tokens, costMicro: cost } }
          : {};
        send(answer.ok ? { type: "result", answer: answer.value, usage, ...report } : { type: "error", code: answer.code, usage, ...report });
        const tokens = { inputTokens: message.usage.input_tokens, outputTokens: message.usage.output_tokens };
        ended(
          answer.ok ? "ok" : answer.code,
          {
            caller: caller.kind,
            model: message.model,
            ...tokens,
            costMicro: cost,
            refusal: message.stop_reason === "refusal" ? (message.stop_details?.category ?? null) : undefined,
            clientGone: hungUp.aborted || undefined,
          },
          { ...tokens, costMicro: cost },
        );
      } catch (error) {
        // The stream then ends with the error line `internal`.
        if (!counted) ended("internal", { caller: caller.kind, model: settings.model });
        throw error;
      } finally {
        // When the model was reached but did not answer, what it was billed for cannot be known, so
        // the whole hold counts as spent.
        await settleAll(caps, held, cost ?? (reachedModel ? held : 0));
        if (!charged) {
          await quota.release(Date.now(), input.requestId).catch((error: unknown) => {
            console.error(JSON.stringify({ event: "release_failed", error: errorName(error) }));
          });
        }
      }
    });
  } catch (error) {
    // The fetch handler answers it 500 `internal`.
    if (!counted) ended("internal", {});
    throw error;
  }
}

type ModelReply = { ok: true; message: Message; reachedModel: true } | { ok: false; error: unknown; reachedModel: boolean };

async function askModel<Input extends { requestId: string }>(
  env: Env,
  task: ModelTask<Input>,
  prompt: string,
  settings: ModelSettings,
  signal: AbortSignal,
): Promise<ModelReply> {
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
      model: settings.model,
      max_tokens: settings.maxTokens,
      system: task.system,
      messages: [{ role: "user", content: prompt }],
      ...(settings.thinking === undefined ? {} : { thinking: settings.thinking }),
      output_config: { effort: settings.effort, format: { type: "json_schema", schema: task.schema } },
      // When a safety classifier declines the request, the API runs it again on the model it
      // recommends for that case instead of returning the refusal.
      ...(settings.fallback ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" as const } : {}),
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

/**
 * What the model's answer comes to: the answer the app gets, or the error code, and whether the
 * call is charged. Structured outputs keep a finished answer to the schema; a refusal or an answer
 * cut off at `max_tokens` is charged, as the model did answer.
 */
function outcomeOf<Input extends { requestId: string }>(
  message: Message,
  task: ModelTask<Input>,
): { ok: true; value: Record<string, unknown>; charged: true } | { ok: false; code: string; charged: boolean } {
  if (message.stop_reason === "refusal") return { ok: false, code: "refused", charged: true };
  if (message.stop_reason === "max_tokens") return { ok: false, code: "too_long", charged: true };
  const value = message.stop_reason === "end_turn" ? jsonObjectOf(message) : null;
  const answer = value === null ? null : task.answerOf(value);
  return answer === null ? { ok: false, code: "bad_response", charged: task.chargeBadAnswer } : { ok: true, value: answer, charged: true };
}

/** The JSON object the model wrote, or null. Text blocks only: thinking blocks, and the marker a fallback leaves, hold no part of it. */
function jsonObjectOf(message: Message): Record<string, unknown> | null {
  const text = message.content.flatMap((block) => (block.type === "text" ? [block.text] : [])).join("");
  try {
    const value: unknown = JSON.parse(text);
    if (typeof value === "object" && value !== null && !Array.isArray(value)) return value as Record<string, unknown>;
  } catch {
    // Not JSON; reported as null.
  }
  return null;
}

function failureCode(error: unknown): string {
  if (error instanceof Anthropic.APIUserAbortError || error instanceof Anthropic.APIConnectionTimeoutError) return "timeout";
  if (error instanceof Anthropic.RateLimitError || error instanceof Anthropic.OverloadedError) return "busy";
  // An overload reported in the middle of the answer comes as an error event, without a status.
  if (error instanceof Anthropic.APIError && (error.type === "overloaded_error" || error.type === "rate_limit_error")) return "busy";
  return "upstream";
}

/** One line per call, for cost and health. It never holds the bill, the instruction, a name, an install id or an IP. */
function log(event: string, fields: Record<string, unknown>) {
  console.log(JSON.stringify({ event, ...fields }));
}
