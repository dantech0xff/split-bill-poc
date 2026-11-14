import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { expect, it } from "vitest";
import { lineStream } from "../src/respond";

function lines(text: string): unknown[] {
  return text.trim().split("\n").map((line) => JSON.parse(line));
}

it("sends a wait line at once and again while the work runs, then the work's lines, then ends", async () => {
  const ctx = createExecutionContext();

  const response = lineStream(ctx, { waitMs: 20, afterHangUpMs: 1_000 }, async (send) => {
    await new Promise((resolve) => setTimeout(resolve, 90));
    send({ type: "result", answer: 1 });
  });
  const text = new TextDecoder().decode(await response.arrayBuffer());
  await waitOnExecutionContext(ctx);

  const sent = lines(text);
  expect(sent.length).toBeGreaterThanOrEqual(4);
  expect(sent.slice(0, -1).every((line) => JSON.stringify(line) === '{"type":"wait"}')).toBe(true);
  expect(sent[sent.length - 1]).toEqual({ type: "result", answer: 1 });
  expect(response.headers.get("cache-control")).toBe("no-store, no-transform");
});

it("ends with an error line when the work breaks", async () => {
  const ctx = createExecutionContext();

  const response = lineStream(ctx, { waitMs: 1_000, afterHangUpMs: 1_000 }, async () => {
    throw new Error("broken");
  });
  const text = new TextDecoder().decode(await response.arrayBuffer());
  await waitOnExecutionContext(ctx);

  expect(lines(text)).toEqual([{ type: "wait" }, { type: "error", code: "internal" }]);
});

it("sends nothing after the line that ends the answer, also when the work breaks later", async () => {
  const ctx = createExecutionContext();

  const response = lineStream(ctx, { waitMs: 10, afterHangUpMs: 1_000 }, async (send) => {
    send({ type: "result", answer: 1 });
    await new Promise((resolve) => setTimeout(resolve, 50));
    send({ type: "error", code: "late" });
    throw new Error("broken after the answer");
  });
  const text = new TextDecoder().decode(await response.arrayBuffer());
  await waitOnExecutionContext(ctx);

  expect(lines(text)).toEqual([{ type: "wait" }, { type: "result", answer: 1 }]);
});

it("asks the work to stop a while after the caller hangs up, and not before", async () => {
  const ctx = createExecutionContext();
  let stoppedAfter = -1;
  let hungUpAt = 0;

  const response = lineStream(ctx, { waitMs: 1_000, afterHangUpMs: 60 }, async (_, hungUp) => {
    await new Promise<void>((resolve) => hungUp.addEventListener("abort", () => resolve()));
    stoppedAfter = Date.now() - hungUpAt;
  });
  const reader = response.body!.getReader();
  await reader.read();
  hungUpAt = Date.now();
  await reader.cancel();
  await waitOnExecutionContext(ctx);

  expect(stoppedAfter).toBeGreaterThanOrEqual(50);
});
