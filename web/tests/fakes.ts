import type { LiveEnvironment, SocketLike } from "../src/live";

/** A WebSocket the test opens, feeds and closes by hand. */
export class FakeSocket implements SocketLike {
  readyState = 0;
  onopen: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  readonly sent: string[] = [];
  closedWith: number | null = null;

  constructor(
    readonly url: string,
    readonly protocols: string[],
  ) {}

  open(): void {
    this.readyState = 1;
    this.onopen?.(new Event("open"));
  }

  receive(data: unknown): void {
    this.onmessage?.(new MessageEvent("message", { data: typeof data === "string" ? data : JSON.stringify(data) }));
  }

  /** The other end hangs up, or the connection fails before it opens. */
  drop(): void {
    this.readyState = 3;
    this.onerror?.(new Event("error"));
    this.onclose?.(new CloseEvent("close", { code: 1006 }));
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(code?: number): void {
    this.readyState = 3;
    this.closedWith = code ?? 1005;
    this.onclose?.(new CloseEvent("close", { code: code ?? 1005 }));
  }
}

/** A page whose visibility and connection the test sets, and whose sockets it keeps. */
export class FakeLiveEnvironment implements LiveEnvironment {
  readonly sockets: FakeSocket[] = [];
  visible = true;
  private readonly visibilityListeners = new Set<() => void>();
  private readonly onlineListeners = new Set<() => void>();

  createSocket(url: string, protocols: string[]): FakeSocket {
    const socket = new FakeSocket(url, protocols);
    this.sockets.push(socket);
    return socket;
  }

  get last(): FakeSocket {
    const socket = this.sockets.at(-1);
    if (socket === undefined) throw new Error("No socket was made");
    return socket;
  }

  isVisible(): boolean {
    return this.visible;
  }

  onVisibilityChange(listener: () => void): () => void {
    this.visibilityListeners.add(listener);
    return () => this.visibilityListeners.delete(listener);
  }

  onOnline(listener: () => void): () => void {
    this.onlineListeners.add(listener);
    return () => this.onlineListeners.delete(listener);
  }

  random(): number {
    return 1;
  }

  setVisible(visible: boolean): void {
    this.visible = visible;
    for (const listener of this.visibilityListeners) listener();
  }

  goOnline(): void {
    for (const listener of this.onlineListeners) listener();
  }
}

export interface RecordedCall {
  path: string;
  body: Record<string, unknown>;
}

export type Answer = { status: number; body?: unknown; headers?: Record<string, string> } | Error;

/** An answer the test hands over later, to hold a request on its way. */
export function held(): { answer: Promise<Answer>; release: (answer: Answer) => void } {
  let release: (answer: Answer) => void = () => undefined;
  const answer = new Promise<Answer>((resolve) => {
    release = resolve;
  });
  return { answer, release };
}

/**
 * A fetch that answers each path from a queue the test fills, and records every call. A path
 * with no answer left fails the test.
 */
export class FakeFetch {
  readonly calls: RecordedCall[] = [];
  private readonly answers = new Map<string, Array<Answer | Promise<Answer>>>();

  answer(path: string, ...answers: Array<Answer | Promise<Answer>>): this {
    this.answers.set(path, [...(this.answers.get(path) ?? []), ...answers]);
    return this;
  }

  callsTo(path: string): RecordedCall[] {
    return this.calls.filter((call) => call.path === path);
  }

  readonly fetch = async (path: string, init: RequestInit): Promise<Response> => {
    const body: unknown = typeof init.body === "string" ? JSON.parse(init.body) : null;
    this.calls.push({ path, body: body as Record<string, unknown> });
    const queued = this.answers.get(path)?.shift();
    if (queued === undefined) throw new Error(`No answer left for ${path}`);
    const next = await queued;
    if (next instanceof Error) throw next;
    return new Response(next.body === undefined ? null : JSON.stringify(next.body), {
      status: next.status,
      headers: { "content-type": "application/json", ...next.headers },
    });
  };
}
