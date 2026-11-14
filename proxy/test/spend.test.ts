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

  expect(holdMicro(promptBytes)).toBeGreaterThanOrEqual(fullAnswer);
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
