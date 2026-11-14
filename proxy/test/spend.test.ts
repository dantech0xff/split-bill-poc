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

it("charges a model missing from the price list at the dearest price", () => {
  const usage = { input_tokens: 1_000, output_tokens: 1_000, iterations: null };
  const unknown = costMicro({ model: "claude-unknown", usage } as Parameters<typeof costMicro>[0]);
  const dearest = costMicro({ model: "claude-opus-4-8", usage } as Parameters<typeof costMicro>[0]);

  expect(unknown).toBe(dearest);
});
