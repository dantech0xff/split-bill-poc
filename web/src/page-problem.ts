import type { ApiError } from "./api";

/** Why a page cannot show its link at all. */
export type PageProblem =
  | { kind: "not_found" }
  | { kind: "gone" }
  | { kind: "rate_limited"; retryAfterSeconds: number | null }
  | { kind: "network" }
  | { kind: "unexpected" };

/** What a failed read of a link means for its page. */
export function pageProblemOf(error: ApiError): PageProblem {
  switch (error.kind) {
    case "not_found":
    case "gone":
    case "network":
      return { kind: error.kind };
    case "rate_limited":
      return error;
    default:
      return { kind: "unexpected" };
  }
}
