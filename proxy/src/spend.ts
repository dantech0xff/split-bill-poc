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

/** The dearest price in the table: what a hold assumes, and what a model missing from the table is charged. */
const DEAREST: Price = {
  input: Math.max(...Object.values(PRICES).map((price) => price.input)),
  output: Math.max(...Object.values(PRICES).map((price) => price.output)),
};

/** Tokens a request carries beyond its text: message framing and the format instructions of structured outputs. */
const OVERHEAD_TOKENS = 2_000;

/** Prompt cache tokens are not used today; if they appear, they are charged above the price of a cache write. */
const CACHE_FACTOR = 1.25;

/**
 * The most a call can cost, held against the spend caps before the model runs. Each token covers
 * at least one byte of UTF-8, so the byte length of the prompt bounds its tokens, and the answer
 * is bounded by `max_tokens`.
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
    const price = PRICES[model] ?? DEAREST;
    const cached = (attempt.cache_creation_input_tokens ?? 0) + (attempt.cache_read_input_tokens ?? 0);
    total += (attempt.input_tokens + cached * CACHE_FACTOR) * price.input + attempt.output_tokens * price.output;
  }
  return Math.ceil(total);
}

export function isPriced(model: string): boolean {
  return model in PRICES;
}

/** One spend cap a call is held against, and the error a caller sees when it is reached. */
export type SpendCap = { ledger: DurableObjectStub<AbuseLedger>; cap: number; code: "spend_limited" | "busy" };

/**
 * Holds [amount] against every cap at once. When one refuses, the holds the others took are given
 * back, and the first refusal in the order of [caps] is reported.
 */
export async function holdAll(
  caps: SpendCap[],
  now: number,
  amount: number,
): Promise<{ ok: true } | { ok: false; code: SpendCap["code"]; retryAfter: number }> {
  const gates: Gate[] = await Promise.all(caps.map((cap) => cap.ledger.holdSpend(now, amount, cap.cap)));
  const refused = gates.findIndex((gate) => !gate.ok);
  if (refused === -1) return { ok: true };
  await Promise.all(caps.filter((_, index) => gates[index].ok).map((cap) => cap.ledger.settleSpend(now, amount, 0)));
  const gate = gates[refused] as Extract<Gate, { ok: false }>;
  return { ok: false, code: caps[refused].code, retryAfter: gate.retryAfter };
}

/** Replaces the hold of [held] on every cap with what the call cost. */
export async function settleAll(caps: SpendCap[], held: number, actual: number): Promise<void> {
  const now = Date.now();
  await Promise.all(caps.map((cap) => cap.ledger.settleSpend(now, held, actual)));
}
