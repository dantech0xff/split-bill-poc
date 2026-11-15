/** The parts of Cloudflare Turnstile's explicit API the page uses. */
export interface TurnstileRenderOptions {
  sitekey: string;
  action: string;
  theme: "auto";
  size: "flexible";
  appearance: "interaction-only";
  "response-field": false;
  "refresh-expired": "auto";
  callback: (token: string) => void;
  "error-callback": (code: string) => void;
  "expired-callback": () => void;
  "timeout-callback": () => void;
}

export interface TurnstileApi {
  render(container: HTMLElement, options: TurnstileRenderOptions): string | null | undefined;
  reset(widgetId: string): void;
  remove(widgetId: string): void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

export const TURNSTILE_SCRIPT = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

let loading: Promise<TurnstileApi> | null = null;

/**
 * Loads Turnstile's script once, when the first claim starts, and never before: a page that only
 * shows a bill talks to nobody but the proxy. A failed load can be tried again.
 */
export function loadTurnstile(doc: Document = document, view: Window = window): Promise<TurnstileApi> {
  if (view.turnstile) return Promise.resolve(view.turnstile);
  if (loading) return loading;
  const attempt = new Promise<TurnstileApi>((resolve, reject) => {
    const script = doc.createElement("script");
    script.src = TURNSTILE_SCRIPT;
    script.async = true;
    script.addEventListener("load", () => {
      if (view.turnstile) resolve(view.turnstile);
      else reject(new Error("Turnstile did not start"));
    });
    script.addEventListener("error", () => {
      script.remove();
      reject(new Error("Turnstile could not be loaded"));
    });
    doc.head.appendChild(script);
  });
  loading = attempt;
  attempt.catch(() => {
    if (loading === attempt) loading = null;
  });
  return attempt;
}

/** Proof that a person, not a bot, makes this browser's first claim on a link. */
export interface HumanCheck {
  /** Starts the check ahead of the first claim, so its token is ready by the first tap. */
  prepare(): void;
  /** A token for one claim; rejects when the check cannot be done. */
  token(): Promise<string>;
  /** The token handed out was sent: get a fresh one should another be needed. */
  consumed(): void;
  dispose(): void;
}

interface Waiter {
  resolve(token: string): void;
  reject(error: Error): void;
}

/**
 * A Turnstile widget with the action `claim`, rendered on demand into the element [container]
 * gives. It shows itself only when Turnstile needs the person to interact.
 */
export class TurnstileCheck implements HumanCheck {
  private api: TurnstileApi | null = null;
  private widgetId: string | null = null;
  private starting: Promise<void> | null = null;
  private ready: string | null = null;
  private waiters: Waiter[] = [];
  private failed = false;
  private disposed = false;

  constructor(
    private readonly siteKey: string,
    private readonly container: () => HTMLElement | null,
    private readonly load: () => Promise<TurnstileApi> = () => loadTurnstile(),
  ) {}

  prepare(): void {
    this.start().catch(() => {
      // token() reports the failure when a claim needs it.
    });
  }

  token(): Promise<string> {
    if (this.ready !== null) {
      const token = this.ready;
      this.ready = null;
      return Promise.resolve(token);
    }
    return new Promise<string>((resolve, reject) => {
      this.waiters.push({ resolve, reject });
      if (this.failed && this.api && this.widgetId !== null) {
        // A widget that failed stays failed until it is reset.
        this.failed = false;
        this.api.reset(this.widgetId);
      }
      this.start().catch((error: unknown) => this.rejectAll(error instanceof Error ? error : new Error("Turnstile failed")));
    });
  }

  /**
   * Takes the widget away: a token is good for one claim, and once the proxy has handed out a key
   * no more are needed. Should one be needed after all, the next call renders a new widget.
   */
  consumed(): void {
    this.ready = null;
    this.failed = false;
    if (this.api && this.widgetId !== null) this.api.remove(this.widgetId);
    this.widgetId = null;
  }

  dispose(): void {
    this.disposed = true;
    this.rejectAll(new Error("The check was closed"));
    if (this.api && this.widgetId !== null) this.api.remove(this.widgetId);
    this.widgetId = null;
  }

  private start(): Promise<void> {
    if (this.disposed) return Promise.reject(new Error("The check was closed"));
    if (this.widgetId !== null) return Promise.resolve();
    if (!this.starting) {
      this.starting = this.render().finally(() => {
        this.starting = null;
      });
    }
    return this.starting;
  }

  private async render(): Promise<void> {
    const api = await this.load();
    const container = this.container();
    if (this.disposed || container === null) throw new Error("Nowhere to show the check");
    this.api = api;
    const widgetId = api.render(container, {
      sitekey: this.siteKey,
      action: "claim",
      theme: "auto",
      size: "flexible",
      appearance: "interaction-only",
      "response-field": false,
      "refresh-expired": "auto",
      callback: (token) => this.received(token),
      "error-callback": () => {
        this.failed = true;
        this.rejectAll(new Error("Turnstile could not check this browser"));
      },
      "expired-callback": () => {
        this.ready = null;
      },
      "timeout-callback": () => {
        this.ready = null;
        if (this.widgetId !== null) api.reset(this.widgetId);
      },
    });
    if (typeof widgetId !== "string") throw new Error("Turnstile did not render");
    this.widgetId = widgetId;
  }

  private received(token: string): void {
    const waiter = this.waiters.shift();
    if (waiter) waiter.resolve(token);
    else this.ready = token;
  }

  private rejectAll(error: Error): void {
    const waiters = this.waiters;
    this.waiters = [];
    for (const waiter of waiters) waiter.reject(error);
  }
}
