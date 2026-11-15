import type { RefObject } from "preact";
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { browserLiveEnvironment, liveUrl } from "../live";
import { ShareController, type ShareState } from "../share-controller";
import { claimOf } from "../share-state";
import { TurnstileCheck } from "../turnstile";
import { Loading, ProblemView } from "../ui/problem-view";
import {
  AmountsSection,
  BillHeader,
  ClaimersSection,
  ClaimForm,
  ItemsSection,
  LiveRegion,
  type ShareActions,
  StatusBanner,
  TotalsSection,
} from "./share-sections";

interface ShareViewProps {
  state: ShareState;
  actions: ShareActions;
  humanCheckSlot?: RefObject<HTMLDivElement | null>;
}

/** The bill link's page for one state; everything it shows comes from [state]. */
export function ShareView({ state, actions, humanCheckSlot }: ShareViewProps) {
  const { share } = state;
  let body;
  if (state.phase === "failed" && state.problem !== null) {
    body = <ProblemView problem={state.problem} kind="share" onRetry={() => actions.retry()} />;
  } else if (share === null) {
    body = <Loading text="Opening the bill…" />;
  } else {
    const picks = new Set(state.picks ?? claimOf(share, state.you)?.itemIds ?? []);
    body = (
      <>
        <BillHeader bill={share.bill} />
        <StatusBanner share={share} />
        {state.billUpdated && (
          <div class="notice notice--action">
            <p class="type-body-medium">The bill was updated. Check that your picks are still right.</p>
            <button type="button" class="button button--text" onClick={() => actions.dismissBillUpdate()}>
              OK
            </button>
          </div>
        )}
        {share.status === "locked" && (
          <>
            <AmountsSection share={share} you={state.you} />
            <ClaimersSection share={share} you={state.you} />
          </>
        )}
        {share.status === "open" && <ClaimForm state={state} actions={actions} humanCheckSlot={humanCheckSlot ?? null} />}
        <ItemsSection share={share} picks={picks} you={state.you} onToggle={(itemId) => actions.toggle(itemId)} />
        <TotalsSection bill={share.bill} />
      </>
    );
  }
  return (
    <>
      {body}
      <LiveRegion announcement={state.announcement} />
    </>
  );
}

/** `/s/#<token>`: a bill everyone at the table can open, pick their items on, and pay from. */
export function SharePage({ token, siteKey }: { token: string; siteKey: string }) {
  const slot = useRef<HTMLDivElement>(null);
  const controller = useMemo(
    () =>
      new ShareController({
        token,
        human: siteKey === "" ? null : new TurnstileCheck(siteKey, () => slot.current),
        live: typeof WebSocket === "function" ? { url: liveUrl(window.location), env: browserLiveEnvironment() } : null,
      }),
    [token, siteKey],
  );
  const [state, setState] = useState(controller.state);
  useEffect(() => {
    const unsubscribe = controller.subscribe(setState);
    void controller.start();
    return () => {
      unsubscribe();
      controller.stop();
    };
  }, [controller]);
  return <ShareView state={state} actions={controller} humanCheckSlot={slot} />;
}
