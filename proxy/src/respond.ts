/** A JSON reply that is never cached. [retryAfter] becomes the Retry-After header, in seconds. */
export function jsonResponse(status: number, body: Record<string, unknown>, retryAfter: number | null = null): Response {
  const headers = new Headers({ "content-type": "application/json", "cache-control": "no-store" });
  if (retryAfter !== null) headers.set("retry-after", String(retryAfter));
  return new Response(JSON.stringify(body), { status, headers });
}

/** A refusal before any answer starts: `{"error": code}`, plus whatever else helps the app explain it. */
export function errorResponse(
  status: number,
  code: string,
  options: { retryAfter?: number | null; extra?: Record<string, unknown> } = {},
): Response {
  return jsonResponse(status, { error: code, ...options.extra }, options.retryAfter ?? null);
}

export type Line =
  | { type: "wait" }
  | { type: "result"; [field: string]: unknown }
  | { type: "error"; code: string; [field: string]: unknown };

/**
 * A 200 reply whose body is NDJSON: a wait line at once and every [waitMs], then the lines [run]
 * sends, and the end of the body when [run] is done.
 *
 * [run] goes on when the caller hangs up, so a call that reached the model is still charged and
 * its spend settled; lines sent after that are dropped. The runtime keeps it alive through
 * `waitUntil`.
 */
export function lineStream(
  ctx: ExecutionContext,
  waitMs: number,
  run: (send: (line: Line) => void) => Promise<void>,
): Response {
  const encoder = new TextEncoder();
  let open = true;
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({
    start(started) {
      controller = started;
    },
    cancel() {
      open = false;
    },
  });
  const send = (line: Line) => {
    if (!open) return;
    try {
      controller.enqueue(encoder.encode(`${JSON.stringify(line)}\n`));
    } catch {
      open = false;
    }
  };
  send({ type: "wait" });
  const timer = setInterval(() => send({ type: "wait" }), waitMs);
  const work = run(send)
    .catch((error: unknown) => {
      console.error(JSON.stringify({ event: "line_stream_failed", error: error instanceof Error ? error.name : "unknown" }));
      send({ type: "error", code: "internal" });
    })
    .finally(() => {
      clearInterval(timer);
      if (!open) return;
      open = false;
      controller.close();
    });
  ctx.waitUntil(work);
  return new Response(body, {
    headers: {
      "content-type": "application/x-ndjson; charset=utf-8",
      // Compression would hold the wait lines back. Cloudflare does not compress this type, and
      // no-transform asks every hop on the way to leave the body as it is.
      "cache-control": "no-store, no-transform",
    },
  });
}
