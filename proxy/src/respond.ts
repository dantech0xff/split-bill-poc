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

export type StreamTiming = {
  /** How often a wait line goes out. */
  waitMs: number;
  /** How long the work may go on after the caller hung up, before [run]'s signal asks it to stop. */
  afterHangUpMs: number;
};

/**
 * A 200 reply whose body is NDJSON: a wait line at once and every [StreamTiming.waitMs], then the
 * lines [run] sends, and the end of the body when [run] is done. One result or error line ends the
 * answer; anything [run] sends after it is dropped.
 *
 * The runtime keeps [run] going through `waitUntil` when the caller hangs up, but only for 30
 * seconds. So the signal it gets aborts [StreamTiming.afterHangUpMs] after a hang-up, early enough
 * for [run] to stop its work and settle what it held before the runtime cuts it off.
 */
export function lineStream(
  ctx: ExecutionContext,
  timing: StreamTiming,
  run: (send: (line: Line) => void, hungUp: AbortSignal) => Promise<void>,
): Response {
  const encoder = new TextEncoder();
  const hangUp = new AbortController();
  let hangUpTimer: ReturnType<typeof setTimeout> | undefined;
  let open = true;
  let ended = false;
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({
    start(started) {
      controller = started;
    },
    cancel() {
      open = false;
      hangUpTimer = setTimeout(() => hangUp.abort(), timing.afterHangUpMs);
    },
  });
  const send = (line: Line) => {
    if (!open || ended) return;
    if (line.type !== "wait") ended = true;
    try {
      controller.enqueue(encoder.encode(`${JSON.stringify(line)}\n`));
    } catch {
      open = false;
    }
  };
  send({ type: "wait" });
  const waitTimer = setInterval(() => send({ type: "wait" }), timing.waitMs);
  const work = run(send, hangUp.signal)
    .catch((error: unknown) => {
      console.error(JSON.stringify({ event: "line_stream_failed", error: error instanceof Error ? error.name : "unknown" }));
      send({ type: "error", code: "internal" });
    })
    .finally(() => {
      clearInterval(waitTimer);
      clearTimeout(hangUpTimer);
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
