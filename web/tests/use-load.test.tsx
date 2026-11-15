import { act } from "preact/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiResult } from "../src/api";
import { useLoad } from "../src/pages/use-load";
import { mount, textOf } from "./render";

/** A page that loads with [load] and shows what it got, with a button that tries again. */
function Probe({ load }: { load: () => Promise<ApiResult<string>> }) {
  const { loaded, retry } = useLoad(load);
  const shown = loaded.state === "loading" ? "loading" : loaded.result.ok ? loaded.result.value : loaded.result.error.kind;
  return (
    <>
      <p>{shown}</p>
      <button type="button" onClick={retry}>
        Try again
      </button>
    </>
  );
}

const limited = (retryAfterSeconds: number | null): ApiResult<string> => ({ ok: false, error: { kind: "rate_limited", retryAfterSeconds } });

describe("a page's load", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function settle(): Promise<void> {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
  }

  it("tried again after a 429 waits as long as its Retry-After asks", async () => {
    const load = vi.fn<() => Promise<ApiResult<string>>>().mockResolvedValueOnce(limited(30)).mockResolvedValue({ ok: true, value: "the request" });
    const page = mount(<Probe load={load} />);
    await settle();
    expect(textOf(page.querySelector("p"))).toBe("rate_limited");

    act(() => page.querySelector("button")?.click());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(29_000);
    });
    expect(load).toHaveBeenCalledOnce();
    expect(textOf(page.querySelector("p"))).toBe("loading");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(load).toHaveBeenCalledTimes(2);
    expect(textOf(page.querySelector("p"))).toBe("the request");
  });

  it("waits a minute after a 429 that does not say how long, and not at all after other failures", async () => {
    const load = vi
      .fn<() => Promise<ApiResult<string>>>()
      .mockResolvedValueOnce({ ok: false, error: { kind: "network" } })
      .mockResolvedValueOnce(limited(null))
      .mockResolvedValue({ ok: true, value: "the invite" });
    const page = mount(<Probe load={load} />);
    await settle();
    act(() => page.querySelector("button")?.click());
    await settle();
    expect(load).toHaveBeenCalledTimes(2);
    expect(textOf(page.querySelector("p"))).toBe("rate_limited");

    act(() => page.querySelector("button")?.click());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(59_000);
    });
    expect(load).toHaveBeenCalledTimes(2);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(load).toHaveBeenCalledTimes(3);
    expect(textOf(page.querySelector("p"))).toBe("the invite");
  });
});
