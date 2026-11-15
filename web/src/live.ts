import { type PublicShare, isObject, parseShare } from "./contract";

export type LiveMessage = { type: "share"; share: PublicShare } | { type: "gone" };

/** A message of the live connection, or null for anything else, such as the `pong` that answers a `ping`. */
export function parseLiveMessage(data: unknown): LiveMessage | null {
  if (typeof data !== "string") return null;
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    return null;
  }
  if (!isObject(value)) return null;
  if (value.type === "gone") return { type: "gone" };
  if (value.type !== "share") return null;
  const share = parseShare(value.share);
  return share === null ? null : { type: "share", share };
}

/** The part of a WebSocket the connection uses, so tests can stand in for one. */
export interface SocketLike {
  readonly readyState: number;
  onopen: ((event: Event) => void) | null;
  onmessage: ((event: MessageEvent) => void) | null;
  onclose: ((event: CloseEvent) => void) | null;
  onerror: ((event: Event) => void) | null;
  send(data: string): void;
  close(code?: number, reason?: string): void;
}

export interface LiveEnvironment {
  createSocket(url: string, protocols: string[]): SocketLike;
  /** The time now, in milliseconds since the epoch. */
  now(): number;
  isVisible(): boolean;
  /** Calls [listener] whenever the page is shown or hidden; returns what stops it. */
  onVisibilityChange(listener: () => void): () => void;
  /** Calls [listener] whenever the browser comes back online; returns what stops it. */
  onOnline(listener: () => void): () => void;
  random(): number;
}

export interface LiveHandlers {
  /** The connection is open: read the link now, so that nothing from before is missed. */
  onConnected(): void;
  /**
   * An attempt failed or the connection dropped; [failures] counts them since the last open. From
   * [MAX_LIVE_FAILURES] on, the connection tries again only when asked (see [LiveConnection.tryAgain])
   * or when the page is shown again.
   */
  onDisconnected(failures: number): void;
  onShare(share: PublicShare): void;
  onGone(): void;
}

export const LIVE_PROTOCOL = "splitvibe.v1";
export const PING_INTERVAL_MS = 30_000;
/** The failures in a row after which the connection stops trying by itself. */
export const MAX_LIVE_FAILURES = 5;
const FIRST_RETRY_MS = 1_000;
const MAX_RETRY_MS = 30_000;
const OPEN = 1;

/**
 * The live connection's address on the page's own host. The link's token never goes in it: it
 * travels as the second WebSocket protocol, a header that traces do not keep. Plain `ws:` is
 * used only when the page itself came over plain HTTP, as from `wrangler dev`.
 */
export function liveUrl(location: { protocol: string; host: string }): string {
  return `${location.protocol === "http:" ? "ws:" : "wss:"}//${location.host}/v1/shares/live`;
}

/** How long to wait before the next attempt after [failures] in a row: doubling from 1 s up to 30 s, with jitter. */
export function retryDelay(failures: number, random: () => number): number {
  const base = Math.min(MAX_RETRY_MS, FIRST_RETRY_MS * 2 ** Math.max(0, failures - 1));
  return Math.round(base * (0.5 + random() / 2));
}

export function browserLiveEnvironment(): LiveEnvironment {
  return {
    createSocket: (url, protocols) => new WebSocket(url, protocols),
    now: () => Date.now(),
    isVisible: () => document.visibilityState !== "hidden",
    onVisibilityChange(listener) {
      document.addEventListener("visibilitychange", listener);
      return () => document.removeEventListener("visibilitychange", listener);
    },
    onOnline(listener) {
      window.addEventListener("online", listener);
      return () => window.removeEventListener("online", listener);
    },
    random: Math.random,
  };
}

/**
 * The page's live connection to one link. It connects while the page is visible, closes while it
 * is hidden, reconnects with backoff after a drop, pings every 30 seconds, and stops for good
 * once the link is gone. After [MAX_LIVE_FAILURES] failures in a row it stops trying, until the
 * page is shown again or the browser comes back online, which start the count afresh, or until
 * [tryAgain] asks for one more attempt. It never connects before the time [waitUntil] was given.
 */
export class LiveConnection {
  private socket: SocketLike | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private failures = 0;
  /** No attempt before this time: the proxy asked the page to wait. */
  private notBefore = Number.NEGATIVE_INFINITY;
  private running = false;
  private gone = false;
  private stops: Array<() => void> = [];

  constructor(
    private readonly token: string,
    private readonly url: string,
    private readonly handlers: LiveHandlers,
    private readonly env: LiveEnvironment,
  ) {}

  get connected(): boolean {
    return this.socket !== null && this.socket.readyState === OPEN;
  }

  /** Whether it stopped trying after [MAX_LIVE_FAILURES] failures in a row, and waits to be asked. */
  get stalled(): boolean {
    return this.failures >= MAX_LIVE_FAILURES && this.socket === null && this.retryTimer === null;
  }

  start(): void {
    if (this.running || this.gone) return;
    this.running = true;
    this.stops.push(this.env.onVisibilityChange(() => this.visibilityChanged()));
    this.stops.push(this.env.onOnline(() => this.reconnectNow()));
    if (this.env.isVisible()) this.connect();
  }

  stop(): void {
    this.running = false;
    for (const stop of this.stops) stop();
    this.stops = [];
    this.clearRetry();
    this.closeSocket();
  }

  /**
   * One more attempt when the connection is down and none is due, as after a read of the link
   * that worked, which says the proxy can be reached. The failures in a row still count, so that
   * one more failure stops it again.
   */
  tryAgain(): void {
    if (!this.running || this.gone || this.socket !== null || this.retryTimer !== null || !this.env.isVisible()) return;
    this.connectWhenAllowed(0);
  }

  /** Makes no attempt before [time], as the proxy asked; an attempt already due waits until then. */
  waitUntil(time: number): void {
    this.notBefore = Math.max(this.notBefore, time);
    if (this.retryTimer === null) return;
    this.clearRetry();
    this.connectWhenAllowed(0);
  }

  private visibilityChanged(): void {
    if (!this.running) return;
    if (this.env.isVisible()) {
      this.reconnectNow();
    } else {
      this.clearRetry();
      this.closeSocket();
    }
  }

  private reconnectNow(): void {
    if (!this.running || this.gone || this.socket !== null || !this.env.isVisible()) return;
    this.failures = 0;
    this.clearRetry();
    this.connectWhenAllowed(0);
  }

  /** Connects after [delay], and not before the time the proxy asked to wait for. */
  private connectWhenAllowed(delay: number): void {
    const wait = Math.max(delay, this.notBefore - this.env.now());
    if (wait <= 0) {
      this.connect();
      return;
    }
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.connect();
    }, wait);
  }

  private connect(): void {
    if (!this.running || this.gone || this.socket !== null) return;
    this.clearRetry();
    let socket: SocketLike;
    try {
      socket = this.env.createSocket(this.url, [LIVE_PROTOCOL, this.token]);
    } catch {
      this.failed();
      return;
    }
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.failures = 0;
      this.startPing(socket);
      this.handlers.onConnected();
    };
    socket.onmessage = (event) => {
      if (this.socket === socket) this.received(event.data);
    };
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.stopPing();
      if (this.running && !this.gone) this.failed();
    };
    socket.onerror = () => {
      // A close event always follows; it decides what happens next.
    };
  }

  private received(data: unknown): void {
    const message = parseLiveMessage(data);
    if (message === null) return;
    if (message.type === "share") {
      this.handlers.onShare(message.share);
      return;
    }
    this.gone = true;
    this.stop();
    this.handlers.onGone();
  }

  private failed(): void {
    this.failures += 1;
    this.handlers.onDisconnected(this.failures);
    if (!this.running || this.gone || !this.env.isVisible() || this.failures >= MAX_LIVE_FAILURES) return;
    this.connectWhenAllowed(retryDelay(this.failures, this.env.random));
  }

  private startPing(socket: SocketLike): void {
    this.stopPing();
    this.pingTimer = setInterval(() => {
      if (socket.readyState === OPEN) socket.send("ping");
    }, PING_INTERVAL_MS);
  }

  private stopPing(): void {
    if (this.pingTimer !== null) clearInterval(this.pingTimer);
    this.pingTimer = null;
  }

  private clearRetry(): void {
    if (this.retryTimer !== null) clearTimeout(this.retryTimer);
    this.retryTimer = null;
  }

  private closeSocket(): void {
    const socket = this.socket;
    this.socket = null;
    this.stopPing();
    if (socket === null) return;
    try {
      socket.close(1000, "");
    } catch {
      // Closing a socket that never opened can throw in some browsers; it is gone either way.
    }
  }
}
