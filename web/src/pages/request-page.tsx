import { viewRequest } from "../api";
import type { RequestView } from "../contract";
import { pageProblemOf } from "../page-problem";
import { HeroAmount } from "../ui/hero-amount";
import { OpenInApp } from "../ui/open-in-app";
import { PaymentBlock } from "../ui/payment-block";
import { Loading, ProblemView } from "../ui/problem-view";
import type { AppLink } from "./app-link";
import { type Loaded, useLoad } from "./use-load";

function statusText(request: RequestView): string {
  switch (request.status) {
    case "open":
      return "Not paid yet";
    case "reported":
      return `${request.payerName} says it is paid. ${request.creditorName} has yet to confirm it.`;
    case "confirmed":
      return "Paid and confirmed";
  }
}

export function RequestDetails({ request, link, userAgent }: { request: RequestView; link: AppLink; userAgent: string }) {
  return (
    <>
      <section class="hero" aria-labelledby="request-title">
        <h1 id="request-title" class="type-title-medium hero__title hero__title--small">
          {`${request.creditorName} asks ${request.payerName} for`}
        </h1>
        <HeroAmount amountMinor={request.amountMinor} currency={request.currency} />
        {request.title !== null && request.title !== "" && <p class="type-body-large">{`For ${request.title}`}</p>}
        <p class="type-title-small hero__status">{statusText(request)}</p>
      </section>
      {request.status === "confirmed" ? (
        <p class="type-body-large supporting">Nothing is left to pay on this request.</p>
      ) : request.payment !== null ? (
        <section class="section" aria-labelledby="pay-title">
          <h2 id="pay-title" class="type-title-medium">
            How to pay
          </h2>
          <PaymentBlock payment={request.payment} amountMinor={request.amountMinor} currency={request.currency} />
        </section>
      ) : (
        <p class="type-body-large supporting">{`Ask ${request.creditorName} how to pay.`}</p>
      )}
      <section class="section" aria-labelledby="app-title">
        <h2 id="app-title" class="type-title-medium">
          Keep track in Split Vibe
        </h2>
        <p class="type-body-medium supporting">Split Vibe can tell {request.creditorName} that you paid, and remind you while it is open.</p>
        <OpenInApp link={link} userAgent={userAgent} main={false} />
      </section>
    </>
  );
}

export function RequestPageView({ loaded, link, userAgent, onRetry }: { loaded: Loaded<RequestView>; link: AppLink; userAgent: string; onRetry: () => void }) {
  if (loaded.state === "loading") return <Loading text="Opening the payment request…" />;
  const { result } = loaded;
  if (!result.ok) return <ProblemView problem={pageProblemOf(result.error)} kind="request" onRetry={onRetry} />;
  return <RequestDetails request={result.value} link={link} userAgent={userAgent} />;
}

/** `/r/#<token>`: a payment request, for someone without the app. */
export function RequestPage({ link, userAgent }: { link: AppLink; userAgent: string }) {
  const { loaded, retry } = useLoad(() => viewRequest(link.token));
  return <RequestPageView loaded={loaded} link={link} userAgent={userAgent} onRetry={retry} />;
}
