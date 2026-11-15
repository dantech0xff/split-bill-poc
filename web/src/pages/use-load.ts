import { useCallback, useEffect, useRef, useState } from "preact/hooks";
import { type ApiResult, retryTimeOf } from "../api";

export type Loaded<T> = { state: "loading" } | { state: "done"; result: ApiResult<T> };

/**
 * Runs [load] once for the page, and again on retry. A retry after a 429 waits until the time its
 * `Retry-After` asked for, showing the page as loading meanwhile, so that it never asks again
 * sooner than the proxy said. An answer that comes after the page went away, or after a newer
 * attempt, is dropped.
 */
export function useLoad<T>(load: () => Promise<ApiResult<T>>): { loaded: Loaded<T>; retry: () => void } {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<Loaded<T>>({ state: "loading" });
  const notBefore = useRef(Number.NEGATIVE_INFINITY);

  useEffect(() => {
    let current = true;
    setLoaded({ state: "loading" });
    const settle = (result: ApiResult<T>) => {
      if (!current) return;
      if (!result.ok && result.error.kind === "rate_limited") {
        notBefore.current = retryTimeOf(result.error.retryAfterSeconds, Date.now());
      }
      setLoaded({ state: "done", result });
    };
    const run = () => {
      load().then(settle, () => settle({ ok: false, error: { kind: "unexpected" } }));
    };
    const wait = notBefore.current - Date.now();
    const timer = wait > 0 ? setTimeout(run, wait) : null;
    if (timer === null) run();
    return () => {
      current = false;
      if (timer !== null) clearTimeout(timer);
    };
    // [load] belongs to the page's token, which keys the page: a new token is a new page.
  }, [attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  return { loaded, retry };
}
