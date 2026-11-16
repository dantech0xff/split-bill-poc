import { expect, it } from "vitest";
import { POLICY } from "../src/policy";
import { costMicro, holdMicro, isPriced } from "../src/spend";

it("knows the price of the model that reads bills", () => {
  expect(isPriced(POLICY.model)).toBe(true);
});

it("holds at least what the dearest model would charge for the whole prompt and a full answer", () => {
  const promptBytes = 30_000;
  const fullAnswer = costMicro({
    model: "claude-opus-4-8",
    usage: { input_tokens: promptBytes, output_tokens: POLICY.maxTokens, iterations: null },
  } as Parameters<typeof costMicro>[0]);

  expect(holdMicro(promptBytes, POLICY.maxTokens)).toBeGreaterThanOrEqual(fullAnswer);
});

it("holds a call without a fallback at the price of its own model, and the dearest price in the table without one", () => {
  // 10,000 bytes plus 2,000 tokens of framing in, and 8,000 tokens out, at $0.10 and $0.50 per million tokens.
  expect(holdMicro(10_000, 8_000, "claude-haiku-5-5")).toBe(5_200);
  // At $10 and $50, the highest list price of any model, as a model missing from the table is charged.
  expect(holdMicro(10_000, 8_000, "claude-unknown")).toBe(520_000);
  // At $5 and $25, the dearest in the table, as a bill read that may fall back to another model is held.
  expect(holdMicro(10_000, 8_000)).toBe(260_000);
});

it("charges a model missing from the price list at the highest list price of any model", () => {
  const usage = { input_tokens: 1_000, output_tokens: 1_000, iterations: null };

  // 1,000 tokens in at $10 and 1,000 out at $50 per million tokens.
  expect(costMicro({ model: "claude-unknown", usage } as Parameters<typeof costMicro>[0])).toBe(60_000);
});

it("charges inference kept inside one region above list price", () => {
  const usage = { input_tokens: 1_000, output_tokens: 1_000, iterations: null };
  const cost = (inference_geo: string | null) => costMicro({ model: POLICY.model, usage: { ...usage, inference_geo } } as Parameters<typeof costMicro>[0]);

  expect(cost("global")).toBe(24_000);
  expect(cost(null)).toBe(24_000);
  expect(cost("us")).toBe(26_400);
});
