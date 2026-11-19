import type { PageKind } from "../links";
import type { PageProblem } from "../page-problem";

const GONE: Record<PageKind, string> = {
  share: "The person who shared the bill turned the link off, or its 30 days are over.",
  // A request's link works on the web for 90 days; the request stays in the app of whoever kept it.
  request: "The person who asked withdrew this request, or the link's 90 days are over. If you kept it in your requests in Split Vibe, it is still there.",
  invite: "The invite was turned off or has run out. Ask the group for a new one.",
};

/** How long to wait before trying again, from a `Retry-After` in seconds. */
export function waitText(seconds: number | null): string {
  if (seconds === null) return "Try again in a few minutes.";
  if (seconds < 90) return `Try again in ${seconds} ${seconds === 1 ? "second" : "seconds"}.`;
  return `Try again in ${Math.ceil(seconds / 60)} minutes.`;
}

/** The title and text for a page that cannot show its link. */
export function problemCopy(problem: PageProblem, kind: PageKind): { title: string; text: string; retry: boolean } {
  switch (problem.kind) {
    case "not_found":
      return { title: "This link does not exist", text: "Check that you have the whole link, exactly as it was sent to you.", retry: false };
    case "gone":
      return { title: "This link no longer works", text: GONE[kind], retry: false };
    case "rate_limited":
      return { title: "Too many requests just now", text: waitText(problem.retryAfterSeconds), retry: true };
    case "network":
      return { title: "Split Vibe could not be reached", text: "Check your connection, then try again.", retry: true };
    case "unexpected":
      return { title: "Something went wrong", text: "Try again in a moment.", retry: true };
  }
}

interface ProblemViewProps {
  problem: PageProblem;
  kind: PageKind;
  onRetry?: () => void;
}

export function ProblemView({ problem, kind, onRetry }: ProblemViewProps) {
  const copy = problemCopy(problem, kind);
  return (
    <section class="problem" aria-labelledby="problem-title">
      <h1 id="problem-title" class="type-headline-small">
        {copy.title}
      </h1>
      <p class="type-body-large supporting">{copy.text}</p>
      {copy.retry && onRetry !== undefined && (
        <button type="button" class="button button--tonal" onClick={onRetry}>
          Try again
        </button>
      )}
    </section>
  );
}

export function Loading({ text }: { text: string }) {
  return (
    <section class="loading" aria-busy="true">
      <h1 class="type-title-large">{text}</h1>
      <div class="loading__bar" aria-hidden="true" />
    </section>
  );
}
