import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TurnstileApi, TurnstileRenderOptions } from "../src/turnstile";

async function freshModule(): Promise<typeof import("../src/turnstile")> {
  vi.resetModules();
  return import("../src/turnstile");
}

/**
 * A document whose head keeps the scripts added to it without loading them, so no test reaches
 * Cloudflare, and a window the test gives Turnstile to.
 */
function page(): { doc: Document; view: Window; scripts: HTMLScriptElement[] } {
  const scripts: HTMLScriptElement[] = [];
  const doc = {
    createElement: (tag: string) => document.createElement(tag),
    head: {
      appendChild: (script: HTMLScriptElement) => {
        scripts.push(script);
        return script;
      },
    },
  } as unknown as Document;
  return { doc, view: {} as Window, scripts };
}

describe("loadTurnstile", () => {
  it("adds Turnstile's script once, only when asked, for explicit rendering", async () => {
    const { loadTurnstile, TURNSTILE_SCRIPT } = await freshModule();
    const { doc, view, scripts } = page();
    expect(scripts).toHaveLength(0);
    const first = loadTurnstile(doc, view);
    const second = loadTurnstile(doc, view);
    expect(second).toBe(first);
    expect(scripts.map((script) => script.src)).toEqual(["https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"]);
    expect(TURNSTILE_SCRIPT).toBe("https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit");
    const api = { render: vi.fn(), reset: vi.fn(), remove: vi.fn() } satisfies TurnstileApi;
    view.turnstile = api;
    scripts[0]?.dispatchEvent(new Event("load"));
    await expect(first).resolves.toBe(api);
    await expect(loadTurnstile(doc, view)).resolves.toBe(api);
    expect(scripts).toHaveLength(1);
  });

  it("can be tried again after the script failed to load", async () => {
    const { loadTurnstile } = await freshModule();
    const { doc, view, scripts } = page();
    const failed = loadTurnstile(doc, view);
    scripts[0]?.dispatchEvent(new Event("error"));
    await expect(failed).rejects.toThrow("could not be loaded");
    void loadTurnstile(doc, view).catch(() => undefined);
    expect(scripts).toHaveLength(2);
  });
});

describe("TurnstileCheck", () => {
  let options: TurnstileRenderOptions | undefined;
  let api: { [K in keyof TurnstileApi]: Mock<TurnstileApi[K]> };
  const container = document.createElement("div");

  beforeEach(() => {
    options = undefined;
    let widgets = 0;
    api = {
      render: vi.fn<TurnstileApi["render"]>((_element, given) => {
        options = given;
        widgets += 1;
        return `widget-${widgets}`;
      }),
      reset: vi.fn<TurnstileApi["reset"]>(),
      remove: vi.fn<TurnstileApi["remove"]>(),
    };
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders the widget with the site key and the claim action, and hands out its token", async () => {
    const { TurnstileCheck } = await freshModule();
    const check = new TurnstileCheck("1x00000000000000000000AA", () => container, async () => api);
    const token = check.token();
    await vi.waitFor(() => expect(api.render).toHaveBeenCalledOnce());
    expect(api.render.mock.calls[0]?.[0]).toBe(container);
    expect(options).toMatchObject({ sitekey: "1x00000000000000000000AA", action: "claim", appearance: "interaction-only" });
    options?.callback("token-1");
    await expect(token).resolves.toBe("token-1");
  });

  it("keeps a token that came before anyone asked, and renders afresh once one was used", async () => {
    const { TurnstileCheck } = await freshModule();
    const check = new TurnstileCheck("site-key", () => container, async () => api);
    check.prepare();
    await vi.waitFor(() => expect(api.render).toHaveBeenCalledOnce());
    options?.callback("early");
    await expect(check.token()).resolves.toBe("early");
    check.consumed();
    expect(api.remove).toHaveBeenCalledWith("widget-1");
    const next = check.token();
    await vi.waitFor(() => expect(api.render).toHaveBeenCalledTimes(2));
    options?.callback("token-2");
    await expect(next).resolves.toBe("token-2");
  });

  it("fails the token when Turnstile reports an error, and resets the widget for the next try", async () => {
    const { TurnstileCheck } = await freshModule();
    const check = new TurnstileCheck("site-key", () => container, async () => api);
    const token = check.token();
    await vi.waitFor(() => expect(api.render).toHaveBeenCalledOnce());
    options?.["error-callback"]("300030");
    await expect(token).rejects.toThrow();
    const retry = check.token();
    expect(api.reset).toHaveBeenCalledWith("widget-1");
    options?.callback("token-after-reset");
    await expect(retry).resolves.toBe("token-after-reset");
  });

  it("fails the token when the script cannot be loaded", async () => {
    const { TurnstileCheck } = await freshModule();
    const check = new TurnstileCheck("site-key", () => container, () => Promise.reject(new Error("offline")));
    await expect(check.token()).rejects.toThrow("offline");
  });
});
