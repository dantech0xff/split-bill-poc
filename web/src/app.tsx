import { useEffect, useState } from "preact/hooks";
import { type PageKind, abuseMailto } from "./links";
import { InvitePage } from "./pages/invite-page";
import { RequestPage } from "./pages/request-page";
import { SharePage } from "./pages/share-page";
import { routeOf } from "./route";
import { Footer } from "./ui/footer";
import { ReportAbuse } from "./ui/payment-block";
import { ProblemView } from "./ui/problem-view";

const TITLES: Record<PageKind, string> = {
  share: "Shared bill · Split Vibe",
  request: "Payment request · Split Vibe",
  invite: "Group invite · Split Vibe",
};

export interface AppProps {
  pathname: string;
  host: string;
  hash: string;
  siteKey: string;
  abuseEmail: string;
  userAgent: string;
}

/**
 * The three pages of one bundle, chosen by the path: `/s/` a bill link, `/r/` a payment request,
 * `/g/` a group invite. The token comes from the fragment; a page is drawn afresh for a new one.
 */
export function App({ pathname, host, hash: initialHash, siteKey, abuseEmail, userAgent }: AppProps) {
  const [hash, setHash] = useState(initialHash);
  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash);
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const route = routeOf(pathname, hash);
  const kind: PageKind = route.kind === "unknown" ? "share" : route.kind;
  useEffect(() => {
    document.title = route.kind === "unknown" ? "Split Vibe" : TITLES[route.kind];
  }, [route.kind]);

  let page;
  if (route.kind === "unknown" || route.token === null) {
    page = <ProblemView problem={{ kind: "not_found" }} kind={kind} />;
  } else {
    const link = { host, path: route.path, token: route.token };
    switch (route.kind) {
      case "share":
        page = <SharePage key={route.token} token={route.token} siteKey={siteKey} />;
        break;
      case "request":
        page = <RequestPage key={route.token} link={link} userAgent={userAgent} />;
        break;
      case "invite":
        page = <InvitePage key={route.token} link={link} userAgent={userAgent} />;
        break;
    }
  }

  return (
    <>
      <header class="app-bar">
        <p class="app-bar__brand type-title-medium">Split Vibe</p>
      </header>
      <main class="page">
        <ReportAbuse.Provider value={abuseMailto(abuseEmail, kind)}>{page}</ReportAbuse.Provider>
      </main>
      <Footer kind={kind} abuseEmail={abuseEmail} />
    </>
  );
}
