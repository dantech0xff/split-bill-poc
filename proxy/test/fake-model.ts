import { HttpResponse } from "msw";
import { POLICY } from "../src/policy";

export const MESSAGES_URL = "https://api.anthropic.com/v1/messages";

type Usage = { input_tokens: number; output_tokens: number; iterations?: unknown[] };

/** One answer of the fake model. */
export type Reply = {
  text: string;
  stopReason?: "end_turn" | "max_tokens" | "refusal";
  stopDetails?: { type: "refusal"; category: string | null; explanation: string | null };
  model?: string;
  usage?: Usage;
};

/** The model's answer as the API streams it. */
export function streamed(reply: Reply): HttpResponse<string> {
  return eventStream(serverSentEvents(reply));
}

export function eventStream(body: string): HttpResponse<string> {
  return new HttpResponse(body, { headers: { "content-type": "text/event-stream" } });
}

/**
 * The server-sent events of one answer: a thinking block (empty, as the API sends it by default),
 * then the answer as a text block.
 */
export function serverSentEvents(reply: Reply): string {
  const usage = reply.usage ?? { input_tokens: 5_000, output_tokens: 2_000 };
  const events = [
    {
      type: "message_start",
      message: {
        id: "msg_test",
        type: "message",
        role: "assistant",
        model: reply.model ?? POLICY.model,
        content: [],
        stop_reason: null,
        stop_sequence: null,
        usage: { input_tokens: usage.input_tokens, output_tokens: 1, cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
      },
    },
    { type: "content_block_start", index: 0, content_block: { type: "thinking", thinking: "", signature: "" } },
    { type: "content_block_delta", index: 0, delta: { type: "signature_delta", signature: "c2lnbmF0dXJl" } },
    { type: "content_block_stop", index: 0 },
    { type: "content_block_start", index: 1, content_block: { type: "text", text: "" } },
    { type: "content_block_delta", index: 1, delta: { type: "text_delta", text: reply.text } },
    { type: "content_block_stop", index: 1 },
    {
      type: "message_delta",
      delta: { stop_reason: reply.stopReason ?? "end_turn", stop_sequence: null, stop_details: reply.stopDetails ?? null },
      usage: { output_tokens: usage.output_tokens, iterations: usage.iterations ?? null },
    },
    { type: "message_stop" },
  ];
  return events.map((event) => `event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`).join("");
}

/** An error reply of the API, before any answer. */
export function apiError(status: number, type: string): HttpResponse<string> {
  return new HttpResponse(JSON.stringify({ type: "error", error: { type, message: "test" } }), {
    status,
    headers: { "content-type": "application/json" },
  });
}
