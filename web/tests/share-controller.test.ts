import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { type ClaimRecord, type ClaimStore, MemoryClaimStore } from "../src/claim-store";
import { type PublicShare, parseShare } from "../src/contract";
import { MAX_LIVE_FAILURES } from "../src/live";
import { OPEN_FALLBACK_MS, READ_WHILE_DISCONNECTED_MS, ShareController } from "../src/share-controller";
import type { HumanCheck } from "../src/turnstile";
import { FakeFetch, FakeLiveEnvironment, held } from "./fakes";
import shareOpen from "./fixtures/share-open.json";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";
const KEY = "KkKkKkKkKkKkKkKkKkKkKk";
const OPEN = "/v1/shares/open";
const CLAIM = "/v1/shares/claim";

function share(revision: number, patch: Partial<PublicShare> = {}): PublicShare {
  const parsed = parseShare(structuredClone(shareOpen.share));
  if (parsed === null) throw new Error("The fixture does not parse");
  return { ...parsed, revision, ...patch };
}

function withMyClaim(revision: number, itemIds: string[], name = "Bao"): PublicShare {
  const base = share(revision);
  return { ...base, claims: [...base.claims, { id: "c-me", name, itemIds }] };
}

class FakeHumanCheck implements HumanCheck {
  prepared = 0;
  consumed = vi.fn();
  disposed = false;
  readonly outcomes: Array<string | Error> = [];
  private handedOut = 0;

  prepare(): void {
    this.prepared += 1;
  }

  token(): Promise<string> {
    const next = this.outcomes.shift();
    if (next instanceof Error) return Promise.reject(next);
    this.handedOut += 1;
    return Promise.resolve(next ?? `turnstile-${this.handedOut}`);
  }

  dispose(): void {
    this.disposed = true;
  }
}

/** A store whose record the test can see and set. */
function memoryStore(record: ClaimRecord | null = null): ClaimStore {
  return new MemoryClaimStore(record);
}

describe("ShareController", () => {
  let fetch: FakeFetch;
  let env: FakeLiveEnvironment;
  let human: FakeHumanCheck;
  let store: ClaimStore;
  let controller: ShareController;

  function make(options: { human?: HumanCheck | null; store?: ClaimStore; now?: () => number } = {}): ShareController {
    store = options.store ?? memoryStore();
    controller = new ShareController({
      token: TOKEN,
      human: options.human === undefined ? human : options.human,
      live: { url: "wss://proxy.example.workers.dev/v1/shares/live", env },
      fetch: fetch.fetch,
      store: Promise.resolve(store),
      ...(options.now === undefined ? {} : { now: options.now }),
    });
    return controller;
  }

  /** [count] answers of `open` with the link at revision 7. */
  function opens(count: number) {
    return Array.from({ length: count }, () => ({ status: 200, body: { share: share(7), you: null } }));
  }

  /** Everything the page sent: live connections and reads of the link. */
  function sent(): number {
    return env.sockets.length + fetch.callsTo(OPEN).length;
  }

  /** Starts the page, opens its live connection, and waits for the first view. */
  async function startWith(view: PublicShare, you: string | null = null): Promise<void> {
    fetch.answer(OPEN, { status: 200, body: { share: view, you } });
    await controller.start();
    env.last.open();
    await vi.waitFor(() => expect(controller.state.phase).toBe("ready"));
  }

  beforeEach(() => {
    fetch = new FakeFetch();
    env = new FakeLiveEnvironment();
    human = new FakeHumanCheck();
  });

  afterEach(() => {
    controller.stop();
  });

  it("connects first, then reads the link with the token in the body only", async () => {
    make();
    fetch.answer(OPEN, { status: 200, body: { share: share(7), you: null } });
    await controller.start();
    expect(fetch.calls).toHaveLength(0);
    expect(env.last.url).not.toContain(TOKEN);
    env.last.open();
    await vi.waitFor(() => expect(controller.state.share?.revision).toBe(7));
    expect(fetch.calls).toEqual([{ path: OPEN, body: { token: TOKEN } }]);
  });

  it("keeps whichever view has the higher revision, from any source", async () => {
    make();
    await startWith(share(7));
    env.last.receive({ type: "share", share: share(6, { status: "locking" }) });
    expect(controller.state.share?.revision).toBe(7);
    env.last.receive({ type: "share", share: share(9) });
    expect(controller.state.share?.revision).toBe(9);
    fetch.answer(OPEN, { status: 200, body: { share: share(8, { status: "locking" }), you: null } });
    await controller.refresh();
    expect(controller.state.share?.revision).toBe(9);
    expect(controller.state.share?.status).toBe("open");
  });

  it("makes the first claim with Turnstile, keeps the key, and sends only the key afterwards", async () => {
    make();
    await startWith(share(7));
    controller.setName("  Bao ");
    expect(human.prepared).toBeGreaterThan(0);
    fetch.answer(CLAIM, { status: 200, body: { share: withMyClaim(8, ["i2"]), you: "c-me", key: KEY } });
    controller.toggle("i2");
    expect(controller.state.picks).toEqual(["i2"]);
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(fetch.callsTo(CLAIM)[0]?.body).toEqual({ token: TOKEN, name: "Bao", itemIds: ["i2"], turnstile: "turnstile-1" });
    expect(human.consumed).toHaveBeenCalledOnce();
    expect(store.load()).toEqual({ key: KEY, claimId: "c-me", name: "Bao" });
    expect(controller.state.you).toBe("c-me");
    expect(controller.currentPicks()).toEqual(["i2"]);

    fetch.answer(CLAIM, { status: 200, body: { share: withMyClaim(9, ["i2", "i3"]), you: "c-me" } });
    controller.toggle("i3");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(fetch.callsTo(CLAIM)[1]?.body).toEqual({ token: TOKEN, key: KEY, name: "Bao", itemIds: ["i2", "i3"] });
    expect(store.load()?.key).toBe(KEY);
  });

  it("sends one claim at a time, the latest picks last", async () => {
    make({ store: memoryStore({ key: KEY, claimId: "c-me", name: "Bao" }) });
    await startWith(withMyClaim(7, []), "c-me");
    const first = held();
    fetch.answer(CLAIM, first.answer, { status: 200, body: { share: withMyClaim(9, ["i1", "i2", "i3"]), you: "c-me" } });
    controller.toggle("i1");
    controller.toggle("i2");
    controller.toggle("i3");
    expect(controller.state.picks).toEqual(["i1", "i2", "i3"]);
    expect(fetch.callsTo(CLAIM)).toHaveLength(1);
    first.release({ status: 200, body: { share: withMyClaim(8, ["i1"]), you: "c-me" } });
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(fetch.callsTo(CLAIM).map((call) => call.body.itemIds)).toEqual([["i1"], ["i1", "i2", "i3"]]);
    expect(controller.currentPicks()).toEqual(["i1", "i2", "i3"]);
  });

  it("asks for a name before the first pick, and sends nothing", async () => {
    make();
    await startWith(share(7));
    controller.toggle("i1");
    expect(controller.state.claimProblem).toEqual({ kind: "name_missing" });
    controller.setName("x".repeat(41));
    controller.toggle("i1");
    expect(controller.state.claimProblem).toEqual({ kind: "name_too_long" });
    expect(fetch.callsTo(CLAIM)).toHaveLength(0);
  });

  it.each([
    ["403 turnstile", { status: 403, body: { error: "turnstile" } }, { kind: "turnstile" }],
    ["409 full", { status: 409, body: { error: "full" } }, { kind: "full" }],
    ["429 with Retry-After", { status: 429, body: { error: "rate_limited" }, headers: { "retry-after": "30" } }, { kind: "rate_limited", retryAfterSeconds: 30 }],
    ["503 not_configured", { status: 503, body: { error: "not_configured" } }, { kind: "not_configured" }],
    ["400 input", { status: 400, body: { error: "input" } }, { kind: "input" }],
    ["a network failure", new TypeError("Failed to fetch"), { kind: "network" }],
    ["500", { status: 500, body: { error: "internal" } }, { kind: "unexpected" }],
  ])("says why a claim failed on %s, and puts the picks back", async (_label, answer, problem) => {
    make();
    await startWith(share(7));
    controller.setName("Bao");
    fetch.answer(CLAIM, answer);
    controller.toggle("i2");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(controller.state.claimProblem).toEqual(problem);
    expect(controller.state.picks).toBeNull();
    expect(controller.currentPicks()).toEqual([]);
    expect(store.load()).toBeNull();
    expect(human.consumed).toHaveBeenCalledOnce();
  });

  it("reads the link again when picks closed in the meantime", async () => {
    make();
    await startWith(share(7));
    controller.setName("Bao");
    fetch.answer(CLAIM, { status: 409, body: { error: "locked" } });
    fetch.answer(OPEN, { status: 200, body: { share: share(8, { status: "locking" }), you: null } });
    controller.toggle("i2");
    await vi.waitFor(() => expect(controller.state.share?.status).toBe("locking"));
    expect(controller.state.claimProblem).toEqual({ kind: "locked" });
  });

  it("ends the page when a claim finds the link gone", async () => {
    make();
    await startWith(share(7));
    controller.setName("Bao");
    fetch.answer(CLAIM, { status: 410, body: { error: "gone" } });
    controller.toggle("i2");
    await vi.waitFor(() => expect(controller.state.phase).toBe("failed"));
    expect(controller.state.problem).toEqual({ kind: "gone" });
    expect(env.last.closedWith).toBe(1000);
    expect(human.disposed).toBe(true);
  });

  it("claims afresh with Turnstile when the proxy no longer knows the key", async () => {
    make({ store: memoryStore({ key: KEY, claimId: "c-old", name: "Bao" }) });
    await startWith(share(7), null);
    expect(store.load()).toBeNull();
    expect(controller.state.name).toBe("Bao");

    store.save({ key: KEY, claimId: "c-old", name: "Bao" });
    fetch.answer(
      CLAIM,
      { status: 403, body: { error: "turnstile" } },
      { status: 200, body: { share: withMyClaim(8, ["i1"]), you: "c-me", key: "NnNnNnNnNnNnNnNnNnNnNn" } },
    );
    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    const bodies = fetch.callsTo(CLAIM).map((call) => call.body);
    expect(bodies[0]).toEqual({ token: TOKEN, key: KEY, name: "Bao", itemIds: ["i1"] });
    expect(bodies[1]).toEqual({ token: TOKEN, name: "Bao", itemIds: ["i1"], turnstile: "turnstile-1" });
    expect(store.load()).toEqual({ key: "NnNnNnNnNnNnNnNnNnNnNn", claimId: "c-me", name: "Bao" });
    expect(controller.state.claimProblem).toBeNull();
  });

  it("says so when Turnstile cannot check the browser, without claiming", async () => {
    make();
    await startWith(share(7));
    controller.setName("Bao");
    human.outcomes.push(new Error("Turnstile could not be loaded"));
    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.claimProblem).toEqual({ kind: "human_check_failed" }));
    expect(fetch.callsTo(CLAIM)).toHaveLength(0);
  });

  it("says picking is not available without a site key", async () => {
    make({ human: null });
    await startWith(share(7));
    controller.setName("Bao");
    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.claimProblem).toEqual({ kind: "not_configured" }));
    expect(fetch.callsTo(CLAIM)).toHaveLength(0);
  });

  it("takes this browser's picks away when the owner removes its claim", async () => {
    make({ store: memoryStore({ key: KEY, claimId: "c-me", name: "Bao" }) });
    await startWith(withMyClaim(7, ["i1"]), "c-me");
    expect(controller.currentPicks()).toEqual(["i1"]);
    env.last.receive({ type: "share", share: share(8) });
    expect(controller.state.you).toBeNull();
    expect(controller.state.removedByOwner).toBe(true);
    expect(controller.currentPicks()).toEqual([]);
    expect(store.load()).toBeNull();
  });

  it("ends the page once the live connection says the link is gone", async () => {
    make();
    await startWith(share(7));
    env.last.receive({ type: "gone" });
    expect(controller.state.phase).toBe("failed");
    expect(controller.state.problem).toEqual({ kind: "gone" });
    expect(controller.state.announcement?.text).toBe("This link no longer works.");
  });

  it("says what others changed in the live region", async () => {
    make();
    await startWith(share(7));
    const after = share(8);
    after.claims = [{ id: "c-minh", name: "Minh", itemIds: ["i1", "i3"] }, after.claims[1]!];
    env.last.receive({ type: "share", share: after });
    expect(controller.state.announcement?.text).toBe("Minh picked Iced coffee.");
  });

  it("reads the link when the live connection fails before it opens", async () => {
    make();
    fetch.answer(OPEN, { status: 404, body: { error: "not_found" } });
    await controller.start();
    env.last.drop();
    await vi.waitFor(() => expect(controller.state.problem).toEqual({ kind: "not_found" }));
    expect(fetch.callsTo(OPEN)).toHaveLength(1);
  });

  it("shows a retry when the first read fails, and reads again on retry", async () => {
    make();
    fetch.answer(OPEN, new TypeError("Failed to fetch"), { status: 200, body: { share: share(7), you: null } });
    await controller.start();
    env.last.open();
    await vi.waitFor(() => expect(controller.state.problem).toEqual({ kind: "network" }));
    controller.retry();
    await vi.waitFor(() => expect(controller.state.phase).toBe("ready"));
  });

  it("reads the link anyway when the live connection does not open in time", async () => {
    vi.useFakeTimers();
    try {
      make();
      fetch.answer(OPEN, { status: 200, body: { share: share(7), you: null } });
      await controller.start();
      expect(fetch.calls).toHaveLength(0);
      await vi.advanceTimersByTimeAsync(OPEN_FALLBACK_MS);
      expect(fetch.callsTo(OPEN)).toHaveLength(1);
    } finally {
      vi.useRealTimers();
    }
  });

  describe("while the live connection keeps failing", () => {
    beforeEach(() => {
      vi.useFakeTimers();
      env.failing = true;
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it("reads the link every three minutes once the connection stops trying, and tries it once after each read", async () => {
      make();
      fetch.answer(OPEN, ...opens(10));
      await controller.start();
      // Five attempts, 1, 2, 4 and 8 seconds apart: the fifth fails 15 seconds in.
      await vi.advanceTimersByTimeAsync(16_000);
      expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES);
      expect(fetch.callsTo(OPEN)).toHaveLength(1);
      expect(controller.state.share?.revision).toBe(7);

      for (let poll = 1; poll <= 3; poll++) {
        await vi.advanceTimersByTimeAsync(READ_WHILE_DISCONNECTED_MS - 2_000);
        expect(fetch.callsTo(OPEN), `poll ${poll}`).toHaveLength(poll);
        await vi.advanceTimersByTimeAsync(2_000);
        expect(fetch.callsTo(OPEN), `poll ${poll}`).toHaveLength(poll + 1);
        expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES + poll);
      }
    });

    it("sends at most a read and a connection every three minutes, about 45 requests in its first hour", async () => {
      make();
      fetch.answer(OPEN, ...opens(60));
      await controller.start();
      await vi.advanceTimersByTimeAsync(60 * 60_000);
      const firstHour = sent();
      await vi.advanceTimersByTimeAsync(60 * 60_000);

      expect(firstHour).toBeLessThanOrEqual(45);
      expect(sent() - firstHour).toBeLessThanOrEqual((2 * 60) / 3);
    });

    it("reads nothing while hidden, and starts the connection afresh once shown, reading no more once it opens", async () => {
      make();
      fetch.answer(OPEN, ...opens(10));
      await controller.start();
      await vi.advanceTimersByTimeAsync(60_000);
      env.setVisible(false);
      await vi.advanceTimersByTimeAsync(30 * 60_000);
      expect(fetch.callsTo(OPEN)).toHaveLength(1);
      expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES);

      env.failing = false;
      env.setVisible(true);
      expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES + 1);
      env.last.open();
      await vi.advanceTimersByTimeAsync(1_000);
      expect(fetch.callsTo(OPEN)).toHaveLength(2);
      await vi.advanceTimersByTimeAsync(30 * 60_000);
      expect(fetch.callsTo(OPEN)).toHaveLength(2);
      expect(env.sockets).toHaveLength(MAX_LIVE_FAILURES + 1);
    });
  });

  it("neither reads nor connects before the time a 429 on a read asked for, whatever asks it to", async () => {
    vi.useFakeTimers();
    try {
      make();
      fetch.answer(OPEN, { status: 429, body: { error: "rate_limited" }, headers: { "retry-after": "120" } }, ...opens(2));
      await controller.start();
      env.last.drop();
      await vi.advanceTimersByTimeAsync(10);
      expect(controller.state.problem).toEqual({ kind: "rate_limited", retryAfterSeconds: 120 });

      controller.retry();
      env.setVisible(false);
      env.setVisible(true);
      env.goOnline();
      await vi.advanceTimersByTimeAsync(119_000);
      expect(fetch.callsTo(OPEN)).toHaveLength(1);
      expect(env.sockets).toHaveLength(1);
      expect(controller.state.phase).toBe("loading");

      await vi.advanceTimersByTimeAsync(1_000);
      expect(fetch.callsTo(OPEN)).toHaveLength(2);
      expect(env.sockets).toHaveLength(2);
      expect(controller.state.phase).toBe("ready");
    } finally {
      vi.useRealTimers();
    }
  });

  it("sends no claim before the time a 429 asked for, and asks for no person check meanwhile", async () => {
    let clock = 1_000_000;
    make({ now: () => clock });
    await startWith(share(7));
    controller.setName("Bao");
    fetch.answer(
      CLAIM,
      { status: 429, body: { error: "rate_limited" }, headers: { "retry-after": "30" } },
      { status: 200, body: { share: withMyClaim(8, ["i1"]), you: "c-me", key: KEY } },
    );

    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(controller.state.claimProblem).toEqual({ kind: "rate_limited", retryAfterSeconds: 30 });
    clock += 10_000;
    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(controller.state.claimProblem).toEqual({ kind: "rate_limited", retryAfterSeconds: 20 });
    expect(fetch.callsTo(CLAIM)).toHaveLength(1);
    expect(human.consumed).toHaveBeenCalledOnce();

    clock += 20_000;
    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.you).toBe("c-me"));
    expect(fetch.callsTo(CLAIM)).toHaveLength(2);
    expect(controller.state.claimProblem).toBeNull();
  });

  it("waits a minute after a 429 that does not say how long", async () => {
    let clock = 1_000_000;
    make({ now: () => clock });
    await startWith(share(7));
    controller.setName("Bao");
    fetch.answer(CLAIM, { status: 429, body: { error: "rate_limited" } });

    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    clock += 15_000;
    controller.toggle("i1");
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));

    expect(controller.state.claimProblem).toEqual({ kind: "rate_limited", retryAfterSeconds: 45 });
    expect(fetch.callsTo(CLAIM)).toHaveLength(1);
  });

  it("saves a changed name on an existing claim when the field is left", async () => {
    make({ store: memoryStore({ key: KEY, claimId: "c-me", name: "Bao" }) });
    await startWith(withMyClaim(7, ["i1"]), "c-me");
    expect(controller.state.name).toBe("Bao");
    controller.commitName();
    expect(fetch.callsTo(CLAIM)).toHaveLength(0);
    fetch.answer(CLAIM, { status: 200, body: { share: withMyClaim(8, ["i1"], "Bao Tran"), you: "c-me" } });
    controller.setName("Bao Tran");
    controller.commitName();
    await vi.waitFor(() => expect(controller.state.saving).toBe(false));
    expect(fetch.callsTo(CLAIM)[0]?.body).toEqual({ token: TOKEN, key: KEY, name: "Bao Tran", itemIds: ["i1"] });
    expect(store.load()?.name).toBe("Bao Tran");
  });
});
