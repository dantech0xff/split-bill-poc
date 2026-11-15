import { useCallback, useEffect, useState } from "preact/hooks";
import type { ApiResult } from "../api";

export type Loaded<T> = { state: "loading" } | { state: "done"; result: ApiResult<T> };

/**
 * Runs [load] once for the page, and again on retry. An answer that comes after the page went
 * away, or after a newer attempt, is dropped.
 */
export function useLoad<T>(load: () => Promise<ApiResult<T>>): { loaded: Loaded<T>; retry: () => void } {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<Loaded<T>>({ state: "loading" });

  useEffect(() => {
    let current = true;
    setLoaded({ state: "loading" });
    load().then(
      (result) => {
        if (current) setLoaded({ state: "done", result });
      },
      () => {
        if (current) setLoaded({ state: "done", result: { ok: false, error: { kind: "unexpected" } } });
      },
    );
    return () => {
      current = false;
    };
    // [load] belongs to the page's token, which keys the page: a new token is a new page.
  }, [attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  return { loaded, retry };
}
