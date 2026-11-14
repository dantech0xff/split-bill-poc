import type Anthropic from "@anthropic-ai/sdk";
import type { AbuseLedger, Gate } from "./ledger";
import { POLICY } from "./policy";

type Price = { input: number; output: number };

/**
 * List prices in microdollars per token, which is the same number as dollars per million tokens.
 * Every model that can serve a request is here: the model in POLICY, the other candidates the
 * model choice is measured against, and the models a refused request falls back to.
 */
const PRICES: Record<string, Price> = {
  "claude-opus-5-5": { input: 4, output: 20 },
  "claude-opus-5": { input: 5, output: 25 },
  "claude-opus-4-8": { input: 5, output: 25 },
  "claude-sonnet-5-5": { input: 2, output: 10 },
  "claude-sonnet-5": { input: 2, output: 10 },
  "claude-haiku-5-5": { input: 0.1, output: 0.5 },
};

/** The dearest price in the table, which a hold assumes. */
const DEAREST: Price = {
  input: Math.max(...Object.values(PRICES).map((price) => price.input)),
  output: Math.max(...Object.values(PRICES).map((price) => price.output)),
};

/**
 * What a model missing from the table is charged, should a refusal fall back to one: the highest
 * list price of any current Claude model, so that spend is never counted low.
 */
const UNLISTED: Price = { input: 10, output: 50 };

/** Inference kept inside one region is billed at 110% of list price; anything but "global" counts as that. */
const REGIONAL_PERCENT = 110;

/** Tokens a request carries beyond its text: message framing and the format instructions of structured outputs. */
const OVERHEAD_TOKENS = 2_000;

/** Prompt cache tokens are not used today; if they appear, they are charged above the price of a cache write. */
const CACHE_FACTOR = 1.25;

/**
 * The most a call can cost, held against the spend caps before the model runs. Each token covers
 * at least one byte of UTF-8, so the byte length of the prompt bounds its tokens, and the answer
 * is bounded by `max_tokens`. A refusal fallback runs a second attempt, which the hold does not
 * cover: the settle then counts both, so a cap can be passed by that second attempt of a call in
 * flight.
 */
export function holdMicro(promptBytes: number): number {
  return Math.ceil((promptBytes + OVERHEAD_TOKENS) * DEAREST.input + POLICY.maxTokens * DEAREST.output);
}

/**
 * What a finished call cost. When a refused request fell back to another model, the usage lists
 * each attempt with its own model, and each is charged at that model's price.
 */
export function costMicro(message: Anthropic.Beta.BetaMessage): number {
  const iterations = message.usage.iterations ?? [];
  const attempts = iterations.length > 0 ? iterations : [message.usage];
  let total = 0;
  for (const attempt of attempts) {
    const model = "model" in attempt && attempt.model ? attempt.model : message.model;
    const price = PRICES[model];
    if (price === undefined) console.warn(JSON.stringify({ event: "unpriced_model", model }));
    const { input, output } = price ?? UNLISTED;
    const cached = (attempt.cache_creation_input_tokens ?? 0) + (attempt.cache_read_input_tokens ?? 0);
    total += (attempt.input_tokens + cached * CACHE_FACTOR) * input + attempt.output_tokens * output;
  }
  const region = message.usage.inference_geo;
  return Math.ceil(region && region !== "global" ? (total * REGIONAL_PERCENT) / 100 : total);
}

export function isPriced(model: string): boolean {
  return model in PRICES;
}

/** One spend cap a call is held against, and the error a caller sees when it is reached. */
export type SpendCap = { ledger: DurableObjectStub<AbuseLedger>; cap: number; code: "spend_limited" | "busy" };

/**
 * Holds [amount] against every cap at once. When one refuses or cannot be reached, the holds the
 * others took are given back; then the first refusal in the order of [caps] is reported, or the
 * failure thrown.
 */
export async function holdAll(
  caps: SpendCap[],
  now: number,
  amount: number,
): Promise<{ ok: true } | { ok: false; code: SpendCap["code"]; retryAfter: number }> {
  const results = await Promise.allSettled(caps.map((cap) => cap.ledger.holdSpend(now, amount, cap.cap)));
  const took = (result: PromiseSettledResult<Gate>) => result.status === "fulfilled" && result.value.ok;
  const failed = results.findIndex((result) => !took(result));
  if (failed === -1) return { ok: true };
  await settleAll(caps.filter((_, index) => took(results[index])), amount, 0);
  const result = results[failed];
  if (result.status === "rejected") throw result.reason;
  return { ok: false, code: caps[failed].code, retryAfter: (result.value as Extract<Gate, { ok: false }>).retryAfter };
}

/**
 * Replaces the hold of [held] on every cap with what the call cost. It never throws: a cap that
 * cannot be reached keeps the hold until its window ends, which counts the spend high, never low.
 */
export async function settleAll(caps: SpendCap[], held: number, actual: number): Promise<void> {
  const now = Date.now();
  const results = await Promise.allSettled(caps.map((cap) => cap.ledger.settleSpend(now, held, actual)));
  for (const result of results) {
    if (result.status === "rejected") console.error(JSON.stringify({ event: "settle_failed", error: errorName(result.reason) }));
  }
}

export function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "unknown";
}
