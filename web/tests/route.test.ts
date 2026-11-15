import { describe, expect, it } from "vitest";
import { routeOf } from "../src/route";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";

describe("routeOf", () => {
  it("chooses the page from the path and reads the token from the fragment", () => {
    expect(routeOf("/s/", `#${TOKEN}`)).toEqual({ kind: "share", path: "/s/", token: TOKEN });
    expect(routeOf("/r/", `#${TOKEN}`)).toEqual({ kind: "request", path: "/r/", token: TOKEN });
    expect(routeOf("/g/", `#${TOKEN}`)).toEqual({ kind: "invite", path: "/g/", token: TOKEN });
  });

  it("has no token for a fragment no link could hold", () => {
    expect(routeOf("/s/", "")).toEqual({ kind: "share", path: "/s/", token: null });
    expect(routeOf("/s/", "#short")).toEqual({ kind: "share", path: "/s/", token: null });
    expect(routeOf("/s/", `#${TOKEN}x`)).toEqual({ kind: "share", path: "/s/", token: null });
    expect(routeOf("/s/", `#${TOKEN.slice(1)}=`)).toEqual({ kind: "share", path: "/s/", token: null });
  });

  it("knows no other path", () => {
    expect(routeOf("/", `#${TOKEN}`)).toEqual({ kind: "unknown" });
    expect(routeOf(`/s/${TOKEN}`, "")).toEqual({ kind: "unknown" });
  });
});
