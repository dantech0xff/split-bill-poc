import type { Env } from "./index";
import { errorName } from "./spend";

/**
 * What the proxy counts in Workers Analytics Engine, the dataset `split_vibe_events` bound as
 * `METRICS`. A data point names its event and its outcome, and carries numbers; nothing else, so
 * never an account, an install id, an IP, any id or anything of a bill.
 */
export type MetricEvent =
  /** A bill read; its outcome is the line or the refusal that ended it. */
  | "bill_analysis"
  /** An edit of a split by a sentence, counted as a bill read is. */
  | "split_revision"
  /** The first call an install that is not signed in has held, once per install, with what came of it. */
  | "install_first_call"
  | "sign_in"
  | "share_created"
  /** A claim on a bill link that landed: `new`, or `changed` for one that was there already. */
  | "share_claim"
  | "request_created"
  | "request_reported_paid"
  | "request_confirmed_paid"
  | "plan_refresh"
  | "subscription_event"
  | "daily_run";

/**
 * The numbers of a data point, each in a double of its own: `double1` the cost in microdollars of
 * a call the model answered, `double2` and `double3` its input and output tokens, `double4` how
 * long it took in milliseconds. One a point does not carry is 0.
 */
export type MetricNumbers = { costMicro?: number | null; inputTokens?: number; outputTokens?: number; ms?: number };

/**
 * Writes one data point: the event as its index and first blob, the outcome as its second blob, and
 * [numbers] as its doubles. The event is the index because Analytics Engine samples by index, so a
 * busy event never crowds out a rare one. Without the binding nothing is written; a point the
 * binding refuses is logged by the error's name and never fails the request it counts.
 */
export function count(env: Env, event: MetricEvent, outcome: string, numbers: MetricNumbers = {}): void {
  const metrics = env.METRICS;
  if (metrics === undefined) return;
  try {
    metrics.writeDataPoint({
      indexes: [event],
      blobs: [event, outcome],
      doubles: [numbers.costMicro, numbers.inputTokens, numbers.outputTokens, numbers.ms].map(finiteOrZero),
    });
  } catch (error) {
    console.error(JSON.stringify({ event: "metric_not_written", error: errorName(error) }));
  }
}

function finiteOrZero(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
