import { DurableObject } from "cloudflare:workers";
import type { Env } from "./index";
import { POLICY } from "./policy";
import { errorResponse } from "./respond";
import type { PublicShare } from "./share-state";

/** The subprotocol of a link's live connection. The page offers it with the link's token; the proxy answers with it alone. */
export const LIVE_PROTOCOL = "splitvibe.v1";

/** What every page that has a link open hears after a change: the link as it is now, or that it was revoked. */
export type LiveUpdate = { type: "share"; share: PublicShare } | { type: "gone" };

/** How long a page that found a link's connections full waits before it tries again, in seconds. */
const FULL_RETRY_SECONDS = 60;

/**
 * The live connections of one bill link, named by the link's id and when the link was made (see
 * `shareRoom`). It holds no data of its own: D1 holds the link, and the Worker hands this object
 * each change once it is written, which it passes on to every connection. A lost connection loses
 * nothing, since the page reads the link again when it reconnects.
 *
 * Connections use the WebSocket Hibernation API, so the object can sleep while they are open and
 * quiet. A page's `ping` is answered `pong` without waking it. Pages send nothing else; a
 * connection that does is closed.
 */
export class ShareRoom extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  /**
   * Takes one more connection, once the Worker has checked the link's token: 101 with the
   * subprotocol [LIVE_PROTOCOL], or 429 `rate_limited` while the link already has
   * [POLICY.maxLiveConnections] open.
   */
  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("upgrade")?.toLowerCase() !== "websocket") return errorResponse(400, "input");
    if (this.ctx.getWebSockets().length >= POLICY.maxLiveConnections) {
      return errorResponse(429, "rate_limited", { retryAfter: FULL_RETRY_SECONDS });
    }
    const { 0: client, 1: server } = new WebSocketPair();
    this.ctx.acceptWebSocket(server);
    return new Response(null, { status: 101, webSocket: client, headers: { "sec-websocket-protocol": LIVE_PROTOCOL } });
  }

  /**
   * Sends [update] to every connection. After `gone` the connections are closed, with the
   * reason `gone`, as nothing more will come.
   */
  async broadcast(update: LiveUpdate): Promise<void> {
    const message = JSON.stringify(update);
    for (const socket of this.ctx.getWebSockets()) {
      try {
        socket.send(message);
        if (update.type === "gone") socket.close(1000, "gone");
      } catch {
        // The connection closed meanwhile; the others still get the update.
      }
    }
  }

  async webSocketMessage(socket: WebSocket): Promise<void> {
    socket.close(1008, "unexpected message");
  }

  /** Completes the closing handshake that a page started. */
  async webSocketClose(socket: WebSocket, code: number): Promise<void> {
    try {
      // 1005 and 1006 say that no code came; they cannot be sent back.
      socket.close(code === 1005 || code === 1006 ? 1000 : code, "closed");
    } catch {
      // Already closed on this side.
    }
  }
}
