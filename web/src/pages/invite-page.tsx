import { previewInvite } from "../api";
import { pageProblemOf } from "../page-problem";
import type { InvitePreview } from "../contract";
import { OpenInApp } from "../ui/open-in-app";
import { Loading, ProblemView } from "../ui/problem-view";
import type { AppLink } from "./app-link";
import { type Loaded, useLoad } from "./use-load";

export function InviteDetails({ group, link, userAgent }: { group: InvitePreview; link: AppLink; userAgent: string }) {
  return (
    <>
      <section class="hero" aria-labelledby="invite-title">
        <p class="type-title-small hero__label">You are invited to join</p>
        <h1 id="invite-title" class="type-headline-small hero__title hero__title--large">
          {group.name}
        </h1>
        <p class="type-body-medium">{`Balances in ${group.currency.code}`}</p>
      </section>
      <section class="section" aria-labelledby="join-title">
        <h2 id="join-title" class="type-title-medium">
          How to join
        </h2>
        <ol class="steps type-body-large">
          <li>Get Split Vibe from Google Play on your Android phone.</li>
          <li>Open this invite link again on that phone, or tap Open in Split Vibe there.</li>
          <li>Choose the name the group knows you by, and join.</li>
        </ol>
        <OpenInApp link={link} userAgent={userAgent} main />
      </section>
    </>
  );
}

export function InvitePageView({ loaded, link, userAgent, onRetry }: { loaded: Loaded<InvitePreview>; link: AppLink; userAgent: string; onRetry: () => void }) {
  if (loaded.state === "loading") return <Loading text="Opening the invite…" />;
  const { result } = loaded;
  if (!result.ok) return <ProblemView problem={pageProblemOf(result.error)} kind="invite" onRetry={onRetry} />;
  return <InviteDetails group={result.value} link={link} userAgent={userAgent} />;
}

/** `/g/#<token>`: an invite to a group, for someone without the app. */
export function InvitePage({ link, userAgent }: { link: AppLink; userAgent: string }) {
  const { loaded, retry } = useLoad(() => previewInvite(link.token));
  return <InvitePageView loaded={loaded} link={link} userAgent={userAgent} onRetry={retry} />;
}
