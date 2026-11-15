import { afterEach, describe, expect, it, vi } from "vitest";
import { act } from "preact/test-utils";
import { App, type AppProps } from "../src/app";
import { FakeFetch, FakeLiveEnvironment } from "./fakes";
import invite from "./fixtures/invite.json";
import shareOpen from "./fixtures/share-open.json";
import { mount, textOf } from "./render";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";

function props(patch: Partial<AppProps>): AppProps {
  return { pathname: "/s/", host: "proxy.example.workers.dev", hash: `#${TOKEN}`, siteKey: "", abuseEmail: "", userAgent: IPHONE, ...patch };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("App", () => {
  it("knows only the three pages", () => {
    const page = mount(<App {...props({ pathname: "/x/" })} />);
    expect(textOf(page.querySelector("h1"))).toBe("This link does not exist");
  });

  it("says a link without a whole token does not exist, without asking the proxy", () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    const page = mount(<App {...props({ hash: "#AbCd" })} />);
    expect(textOf(page.querySelector("h1"))).toBe("This link does not exist");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("says Split Vibe never holds money, and links to abuse reports only with an address", () => {
    const without = mount(<App {...props({ pathname: "/x/" })} />);
    expect(textOf(without.querySelector("footer"))).toBe("Split Vibe never holds or moves money.");
    const withAddress = mount(<App {...props({ pathname: "/x/", abuseEmail: "abuse@example.com" })} />);
    const report = withAddress.querySelector<HTMLAnchorElement>("footer a");
    expect(textOf(report)).toBe("Report abuse");
    expect(report?.getAttribute("href")).toBe("mailto:abuse@example.com?subject=Report%20abuse%3A%20Split%20Vibe%20bill%20link");
  });

  it("opens an invite from the token in the fragment", async () => {
    const fetch = new FakeFetch().answer("/v1/invites/preview", { status: 200, body: invite });
    vi.stubGlobal("fetch", fetch.fetch);
    const page = mount(<App {...props({ pathname: "/g/" })} />);
    await vi.waitFor(() => expect(textOf(page.querySelector("h1"))).toBe("Chiang Mai trip"));
    expect(fetch.calls).toEqual([{ path: "/v1/invites/preview", body: { token: TOKEN } }]);
    expect(document.title).toBe("Group invite · Split Vibe");
    expect(document.title).not.toContain(TOKEN);
  });

  it("opens a bill link live: connects, then reads it", async () => {
    const env = new FakeLiveEnvironment();
    const fetch = new FakeFetch().answer("/v1/shares/open", { status: 200, body: shareOpen });
    vi.stubGlobal("fetch", fetch.fetch);
    vi.stubGlobal(
      "WebSocket",
      class {
        constructor(url: string, protocols: string[]) {
          return env.createSocket(url, protocols);
        }
      },
    );
    const page = mount(<App {...props({})} />);
    await vi.waitFor(() => expect(env.sockets).toHaveLength(1));
    expect(env.last.protocols).toEqual(["splitvibe.v1", TOKEN]);
    // The test page is served over plain HTTP, so the connection is plain too; over HTTPS it is wss:.
    expect(env.last.url).toBe(`ws://${window.location.host}/v1/shares/live`);
    await act(async () => env.last.open());
    await vi.waitFor(() => expect(textOf(page.querySelector("h1"))).toBe("Pho 24"));
    expect(document.title).toBe("Shared bill · Split Vibe");
  });
});
