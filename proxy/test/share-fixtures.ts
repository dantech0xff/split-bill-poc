import type { Env } from "../src/index";
import type { BillProjection, PublicShare, ShareView } from "../src/share-state";
import type { FakeTurnstile } from "./fake-turnstile";
import { send } from "./requests";
import { type Answer, api, linkToken, type Person, VND } from "./shared";

/** A bill as the owner's app projects it for a link: three items, a charge and two people. */
export function billProjection(overrides: Partial<BillProjection> = {}): BillProjection {
  return {
    merchant: "Pho 24",
    date: "2026-11-14",
    currency: VND,
    items: [
      { id: "i1", name: "Pho bo", quantity: "2", amountMinor: 130_000 },
      { id: "i2", name: "Iced tea", quantity: null, amountMinor: 20_000 },
      { id: "i3", name: "Spring rolls", quantity: "0.5", amountMinor: 45_000 },
    ],
    charges: [{ id: "c1", label: "Service charge", amountMinor: 19_500 }],
    totalMinor: 214_500,
    participants: [
      { id: "me", name: "Lan" },
      { id: "p1", name: "Minh" },
    ],
    ...overrides,
  };
}

export type Owned = { share: ShareView; url: string };
export type Opened = { share: PublicShare; you: string | null };
export type Claimed = { share: PublicShare; you: string; key?: string };

/** A link [owner] made, with its token, which travels in the fragment of its address. */
export type TestShare = { id: string; billId: string; token: string; url: string; share: ShareView };

/** [owner] makes a link for [bill]; it throws unless the proxy makes it. */
export async function publish(owner: Person, bill: BillProjection = billProjection(), billRevision = 1): Promise<TestShare> {
  const id = crypto.randomUUID();
  const billId = crypto.randomUUID();
  const put = await api<Owned>(owner, "PUT", `/v1/shares/${id}`, { billId, billRevision, bill });
  if (put.status !== 201) throw new Error(`The link was not made: ${put.status}`);
  return { id, billId, token: linkToken(put.body.url), url: put.body.url, share: put.body.share };
}

/**
 * [body] sent to [path] as a web page sends it: JSON, without a session or an install id, from
 * [ip] when one is given (as Cloudflare names it in CF-Connecting-IP).
 */
export async function fromPage<T = Record<string, unknown>>(path: string, body: unknown, ip?: string, settings: Partial<Env> = {}): Promise<Answer<T>> {
  const headers = new Headers({ "content-type": "application/json" });
  if (ip !== undefined) headers.set("CF-Connecting-IP", ip);
  const sent = await send(new Request(`https://proxy.test${path}`, { method: "POST", headers, body: JSON.stringify(body) }), settings);
  // The tests read answers in the shapes the contract gives them.
  return { status: sent.status, headers: sent.headers, body: (sent.body === "" ? null : JSON.parse(sent.body)) as T };
}

/** A new claim on the link [token], with a token of [turnstile]'s widget. */
export function newClaim(turnstile: FakeTurnstile, token: string, name: string, itemIds: string[], ip?: string) {
  return fromPage<Claimed>("/v1/shares/claim", { token, name, itemIds, turnstile: turnstile.token() }, ip);
}

/** The claim of the browser holding [key], with its name and items replaced. */
export function changeClaim(token: string, key: string, name: string, itemIds: string[], ip?: string) {
  return fromPage<Claimed>("/v1/shares/claim", { token, key, name, itemIds }, ip);
}

export function openShare(token: string, key?: string, ip?: string) {
  return fromPage<Opened>("/v1/shares/open", key === undefined ? { token } : { token, key }, ip);
}

/** What a payer of [amountMinor] gets to pay Lan with PayPal. */
export function payPal(amountMinor: number) {
  return { link: `https://paypal.me/lan/${amountMinor}VND`, qrPayload: null, details: `PayPal: paypal.me/lan\nAmount: ₫${amountMinor}`, amountFilledIn: true };
}
