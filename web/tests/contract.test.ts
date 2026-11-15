import { describe, expect, it } from "vitest";
import { parseClaimAnswer, parseInviteAnswer, parseOpenAnswer, parseRequestAnswer, parseShare } from "../src/contract";
import invite from "./fixtures/invite.json";
import requestConfirmed from "./fixtures/request-confirmed.json";
import requestOpen from "./fixtures/request-open.json";
import requestReported from "./fixtures/request-reported.json";
import shareLocked from "./fixtures/share-locked.json";
import shareLocking from "./fixtures/share-locking.json";
import shareOpen from "./fixtures/share-open.json";

function clone<T>(value: T): T {
  return structuredClone(value);
}

describe("the parsers of the proxy's answers", () => {
  it("take every fixture as the contract describes it", () => {
    for (const answer of [shareOpen, shareLocking, shareLocked]) expect(parseOpenAnswer(answer)).not.toBeNull();
    for (const answer of [requestOpen, requestReported, requestConfirmed]) expect(parseRequestAnswer(answer)).not.toBeNull();
    expect(parseInviteAnswer(invite)).toEqual({ name: "Chiang Mai trip", currency: { code: "THB", fractionDigits: 2 } });
  });

  it("keep amounts and payments of a settled link", () => {
    const share = parseShare(shareLocked.share);
    expect(share?.amounts?.p1?.payment?.link).toBe("https://paypal.me/lan/86500VND");
    expect(share?.amounts?.me).toEqual({ amountMinor: 77000, payment: null });
  });

  it("keep who paid the whole bill, null when nobody was named or the proxy says nothing of it", () => {
    expect(parseShare({ ...shareLocked.share, paidBy: "p1" })?.paidBy).toBe("p1");
    expect(parseShare(shareLocked.share)?.paidBy).toBeNull();
    const { paidBy: _left, ...without } = shareLocked.share;
    expect(parseShare(without)?.paidBy).toBeNull();
    expect(parseShare({ ...shareLocked.share, paidBy: 5 })).toBeNull();
  });

  it("keep which participant each claim is, null before the split is settled or when the proxy says nothing of it", () => {
    expect(parseShare(shareLocked.share)?.matches).toEqual({ "c-minh": "p1", "c-an": "p2" });
    expect(parseShare(shareOpen.share)?.matches).toBeNull();
    expect(parseShare(shareLocking.share)?.matches).toBeNull();
    const { matches: _left, ...without } = shareLocked.share;
    expect(parseShare(without)?.matches).toBeNull();
  });

  it("refuse matches of the wrong shape", () => {
    for (const matches of [5, "p1", ["p1"], { "c-minh": 1 }, { "c-minh": null }, { "c-minh": { id: "p1" } }, { "c-minh": ["p1"] }]) {
      expect(parseShare({ ...shareLocked.share, matches }), JSON.stringify(matches)).toBeNull();
    }
  });

  it("keep claim ids such as __proto__ and constructor as plain keys of the matches", () => {
    const matches = JSON.parse('{"__proto__": "p1", "constructor": "p2"}') as unknown;
    const share = parseShare({ ...shareLocked.share, matches });

    expect(share).not.toBeNull();
    expect(Object.keys(share?.matches ?? {})).toEqual(["__proto__", "constructor"]);
    expect(share?.matches?.["__proto__"]).toBe("p1");
    expect(share?.matches?.["constructor"]).toBe("p2");
    expect(share?.matches?.["toString"]).toBeUndefined();
    expect(parseShare({ ...shareLocked.share, matches: JSON.parse('{"__proto__": 5}') as unknown })).toBeNull();
  });

  it("keep participant ids such as __proto__ and constructor as plain keys of the amounts", () => {
    const bill = { ...shareLocked.share.bill, participants: [{ id: "__proto__", name: "Proto" }, { id: "constructor", name: "Con" }], totalMinor: 3000 };
    const amounts = JSON.parse('{"__proto__": {"amountMinor": 1000, "payment": null}, "constructor": {"amountMinor": 2000, "payment": null}}') as unknown;
    const share = parseShare({ ...shareLocked.share, bill, amounts });

    expect(share).not.toBeNull();
    expect(Object.keys(share?.amounts ?? {})).toEqual(["__proto__", "constructor"]);
    expect(share?.amounts?.["__proto__"]).toEqual({ amountMinor: 1000, payment: null });
    expect(share?.amounts?.["constructor"]).toEqual({ amountMinor: 2000, payment: null });
    expect(share?.amounts?.["toString"]).toBeUndefined();
  });

  it("refuse a share of the wrong shape", () => {
    const badStatus = clone(shareOpen.share) as Record<string, unknown>;
    badStatus.status = "closed";
    expect(parseShare(badStatus)).toBeNull();

    const fractionalAmount = clone(shareOpen.share);
    (fractionalAmount.bill.items[0] as { amountMinor: number }).amountMinor = 1.5;
    expect(parseShare(fractionalAmount)).toBeNull();

    const badCurrency = clone(shareOpen.share);
    badCurrency.bill.currency = { code: "vnd", fractionDigits: 0 };
    expect(parseShare(badCurrency)).toBeNull();

    const badClaim = clone(shareOpen.share) as { claims: unknown[] };
    badClaim.claims = [{ id: "c", name: "Minh", itemIds: [1] }];
    expect(parseShare(badClaim)).toBeNull();

    const badPayment = clone(shareLocked.share) as { amounts: Record<string, unknown> };
    badPayment.amounts.p1 = { amountMinor: 86500, payment: { link: 5, details: "x", amountFilledIn: true } };
    expect(parseShare(badPayment)).toBeNull();
  });

  it("take a claim answer only with a key of the right shape", () => {
    expect(parseClaimAnswer({ ...shareOpen, you: "c-new", key: "AAAAAAAAAAAAAAAAAAAAAA" })?.key).toBe("AAAAAAAAAAAAAAAAAAAAAA");
    expect(parseClaimAnswer({ ...shareOpen, you: "c-new" })?.key).toBeNull();
    expect(parseClaimAnswer({ ...shareOpen, you: "c-new", key: "short" })).toBeNull();
    expect(parseClaimAnswer({ ...shareOpen, you: null })).toBeNull();
  });

  it("refuse a request with a status the contract does not name", () => {
    expect(parseRequestAnswer({ request: { ...requestOpen.request, status: "paid" } })).toBeNull();
    expect(parseRequestAnswer({ request: { ...requestOpen.request, amountMinor: -1 } })).toBeNull();
  });
});
