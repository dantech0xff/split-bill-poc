import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LiveConnection, type LiveHandlers, MAX_LIVE_FAILURES, PING_INTERVAL_MS, liveUrl, parseLiveMessage, retryDelay } from "../src/live";
import { FakeLiveEnvironment } from "./fakes";
import shareOpen from "./fixtures/share-open.json";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";

function handlers(): { [K in keyof LiveHandlers]: Mock<LiveHandlers[K]> } {
  return {
    onConnected: vi.fn<LiveHandlers["onConnected"]>(),
    onDisconnected: vi.fn<LiveHandlers["onDisconnected"]>(),
    onShare: vi.fn<LiveHandlers["onShare"]>(),
    onGone: vi.fn<LiveHandlers["onGone"]>(),
  };
}

describe("liveUrl", () => {
  it("connects to the page's own host, with no token in the address", () => {
    expect(liveUrl({ protocol: "https:", host: "proxy.example.workers.dev" })).toBe("wss://proxy.example.workers.dev/v1/shares/live");
    expect(liveUrl({ protocol: "http:", host: "127.0.0.1:8797" })).toBe("ws://127.0.0.1:8797/v1/shares/live");
  });
});

describe("parseLiveMessage", () => {
  it("reads a new view of the link and the end of the link", () => {
    expect(parseLiveMessage(JSON.stringify({ type: "share", share: shareOpen.share }))?.type).toBe("share");
    expect(parseLiveMessage(JSON.stringify({ type: "gone" }))).toEqual({ type: "gone" });
  });

  it("ignores pong and anything else", () => {
    expect(parseLiveMessage("pong")).toBeNull();
    expect(parseLiveMessage(JSON.stringify({ type: "share", share: { status: "open" } }))).toBeNull();
    expect(parseLiveMessage(JSON.stringify({ type: "hello" }))).toBeNull();
    expect(parseLiveMessage(new ArrayBuffer(4))).toBeNull();
  });
});

describe("retryDelay", () => {
  it("doubles from one second up to thirty, with jitter below that", () => {
    expect([1, 2, 3, 4, 5, 6, 7].map((failures) => retryDelay(failures, () => 1))).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000]);
    expect(retryDelay(1, () => 0)).toBe(500);
  });
});

describe("LiveConnection", () => {
  let env: FakeLiveEnvironment;

  beforeEach(() => {
    vi.useFakeTimers();
    env = new FakeLiveEnvironment();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function connection(on: LiveHandlers): LiveConnection {
    return new LiveConnection(TOKEN, "wss://proxy.example.workers.dev/v1/shares/live", on, env);
  }

  it("sends the token as the second protocol, and says when it is open", () => {
    const on = handlers();
    connection(on).start();
    expect(env.last.url).toBe("wss://proxy.example.workers.dev/v1/shares/live");
    expect(env.last.protocols).toEqual(["splitvibe.v1", TOKEN]);
    expect(on.onConnected).not.toHaveBeenCalled();
    env.last.open();
    expect(on.onConnected).toHaveBeenCalledOnce();
  });

  it("passes on new views and stops for good once the link is gone", () => {
    const on = handlers();
    connection(on).start();
    env.last.open();
    env.last.receive({ type: "share", share: shareOpen.share });
    expect(on.onShare).toHaveBeenCalledWith(expect.objectContaining({ revision: 7 }));
    env.last.receive({ type: "gone" });
    expect(on.onGone).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(120_000);
    expect(env.sockets).toHaveLength(1);
  });

  it("pings every 30 seconds while open", () => {
    connection(handlers()).start();
    env.last.open();
    vi.advanceTimersByTime(PING_INTERVAL_MS - 1);
    expect(env.last.sent).toEqual([]);
    vi.advanceTimersByTime(1);
    expect(env.last.sent).toEqual(["ping"]);
    vi.advanceTimersByTime(PING_INTERVAL_MS);
    expect(env.last.sent).toEqual(["ping", "ping"]);
  });

  it("reconnects with backoff after drops, and counts the failures", () => {
    const on = handlers();
    connection(on).start();
    env.last.open();
    env.last.drop();
    expect(on.onDisconnected).toHaveBeenLastCalledWith(1);
    vi.advanceTimersByTime(999);
    expect(env.sockets).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(env.sockets).toHaveLength(2);
    env.last.drop();
    expect(on.onDisconnected).toHaveBeenLastCalledWith(2);
    vi.advanceTimersByTime(1999);
    expect(env.sockets).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(env.sockets).toHaveLength(3);
    env.last.open();
    expect(on.onConnected).toHaveBeenCalledTimes(2);
    env.last.drop();
    expect(on.onDisconnected).toHaveBeenLastCalledWith(1);
  });

  it("stops after five failures in a row, and tries once more only when asked, or afresh once shown again", () => {
    const on = handlers();
    const live = connection(on);
    live.start();
    for (let failure = 1; failure <= MAX_LIVE_FAILURES; failure++) {
      expect(env.sockets).toHaveLength(failure);
      env.last.drop();
      expect(on.onDisconnected).toHaveBeenLastCalledWith(failure);
      vi.advanceTimersByTime(30_000);
    }
    expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES);
    expect(live.stalled).toBe(true);
    vi.advanceTimersByTime(60 * 60_000);
    expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES);

    // A read that worked asks for one more attempt; its failure stops it again.
    live.tryAgain();
    expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES + 1);
    env.last.drop();
    expect(on.onDisconnected).toHaveBeenLastCalledWith(MAX_LIVE_FAILURES + 1);
    vi.advanceTimersByTime(60 * 60_000);
    expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES + 1);

    // Shown again, it starts the count afresh, and so it does once the browser is back online.
    env.setVisible(false);
    env.setVisible(true);
    expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES + 2);
    env.last.drop();
    expect(on.onDisconnected).toHaveBeenLastCalledWith(1);
    expect(live.stalled).toBe(false);
  });

  it("tries once more only while no attempt is due or open", () => {
    const live = connection(handlers());
    live.start();
    live.tryAgain();
    expect(env.sockets).toHaveLength(1);
    env.last.drop();
    live.tryAgain();
    expect(env.sockets).toHaveLength(1);
    vi.advanceTimersByTime(1_000);
    expect(env.sockets).toHaveLength(2);
  });

  it("connects no sooner than the proxy asked, whatever asks it to", () => {
    const live = connection(handlers());
    live.start();
    live.waitUntil(Date.now() + 120_000);
    env.last.drop();
    vi.advanceTimersByTime(60_000);
    env.goOnline();
    env.setVisible(false);
    env.setVisible(true);
    live.tryAgain();
    vi.advanceTimersByTime(59_999);
    expect(env.sockets).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(env.sockets).toHaveLength(2);
  });

  it("closes while the page is hidden and connects again once it is shown", () => {
    const on = handlers();
    connection(on).start();
    env.last.open();
    env.setVisible(false);
    expect(env.last.closedWith).toBe(1000);
    expect(on.onDisconnected).not.toHaveBeenCalled();
    vi.advanceTimersByTime(120_000);
    expect(env.sockets).toHaveLength(1);
    expect(env.sockets[0]?.sent).toEqual([]);
    env.setVisible(true);
    expect(env.sockets).toHaveLength(2);
    env.last.open();
    expect(on.onConnected).toHaveBeenCalledTimes(2);
  });

  it("does not connect while hidden, and does not retry until shown", () => {
    env.visible = false;
    const on = handlers();
    connection(on).start();
    expect(env.sockets).toHaveLength(0);
    env.setVisible(true);
    env.last.drop();
    env.setVisible(false);
    vi.advanceTimersByTime(60_000);
    expect(env.sockets).toHaveLength(1);
  });

  it("tries again at once when the browser comes back online", () => {
    connection(handlers()).start();
    env.last.drop();
    env.goOnline();
    expect(env.sockets).toHaveLength(2);
  });

  it("stops everything when stopped", () => {
    const on = handlers();
    const live = connection(on);
    live.start();
    env.last.open();
    live.stop();
    expect(env.last.closedWith).toBe(1000);
    env.setVisible(false);
    env.setVisible(true);
    vi.advanceTimersByTime(120_000);
    expect(env.sockets).toHaveLength(1);
    expect(on.onDisconnected).not.toHaveBeenCalled();
  });
});
