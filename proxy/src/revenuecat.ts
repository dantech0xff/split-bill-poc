import { accountName, quotaBook } from "./identity";
import type { Env } from "./index";
import { isRecord, readJson } from "./input";
import type { PlanState } from "./quota-book";
import { errorName } from "./spend";

/**
 * A customer of RevenueCat's REST API v1, named by its app user ID: the account id the proxy hands
 * the app as its purchaser.
 */
const SUBSCRIBERS_URL = "https://api.revenuecat.com/v1/subscribers/";
/** The entitlement that makes an account Pro, as RevenueCat and the app name it. */
const ENTITLEMENT = "pro";
/** How long one call to RevenueCat may take. */
const TIMEOUT_MS = 10_000;
/** Far more than RevenueCat's answer about one customer needs. */
const MAX_ANSWER_BYTES = 256 * 1024;

/** The secret key of RevenueCat's REST API, or null while it is not set: then nobody becomes Pro. */
export function revenueCatKey(env: Env): string | null {
  const key = env.REVENUECAT_SECRET_KEY?.trim() ?? "";
  return key === "" ? null : key;
}

/**
 * Asks RevenueCat for the plan of [account] and keeps it in the account's QuotaBook; false when
 * RevenueCat gave none, which leaves the plan kept as it was. RevenueCat creates a customer it does
 * not have when it is asked, so call it only for an account whose id the proxy handed out as a
 * purchaser; the QuotaBook keeps nothing for any other.
 */
export async function refreshPlan(env: Env, key: string, account: string): Promise<boolean> {
  const plan = await readPlan(key, account);
  if (plan === null) return false;
  await quotaBook(env, accountName(account)).storePlan(plan);
  return true;
}

/**
 * The plan RevenueCat gives the customer [purchaser] now, or null when it gave none that can be read:
 * no answer within [TIMEOUT_MS], a status other than 200 or 201 (a customer it had to create), or an
 * answer of another shape. A failure is logged with its status or the error's name, never with the
 * id. No `X-Platform` header goes with it, as RevenueCat asks of a read with a secret key.
 */
async function readPlan(key: string, purchaser: string): Promise<PlanState | null> {
  try {
    const response = await fetch(SUBSCRIBERS_URL + encodeURIComponent(purchaser), {
      headers: { authorization: `Bearer ${key}`, accept: "application/json" },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (response.status !== 200 && response.status !== 201) {
      await response.body?.cancel();
      console.error(JSON.stringify({ event: "revenuecat_unavailable", status: response.status }));
      return null;
    }
    const plan = planOf(await readJson(response, MAX_ANSWER_BYTES));
    if (plan === null) console.error(JSON.stringify({ event: "revenuecat_unreadable", status: response.status }));
    return plan;
  } catch (error) {
    console.error(JSON.stringify({ event: "revenuecat_unavailable", error: errorName(error) }));
    return null;
  }
}

/**
 * Deletes the customer [account] at RevenueCat, with every purchase it holds; true once RevenueCat
 * took the deletion or had no such customer. A subscription on Google Play goes on: deleting the
 * customer does not cancel it. A failure is logged as [readPlan] logs one.
 */
export async function deleteCustomer(key: string, account: string): Promise<boolean> {
  try {
    const response = await fetch(SUBSCRIBERS_URL + encodeURIComponent(account), {
      method: "DELETE",
      headers: { authorization: `Bearer ${key}` },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    await response.body?.cancel();
    if (response.status === 200 || response.status === 404) return true;
    console.error(JSON.stringify({ event: "revenuecat_delete_failed", status: response.status }));
    return false;
  } catch (error) {
    console.error(JSON.stringify({ event: "revenuecat_delete_failed", error: errorName(error) }));
    return false;
  }
}

/**
 * The plan in RevenueCat's answer about one customer, as of the answer's `request_date_ms`; null when
 * the answer is not of the shape RevenueCat gives.
 *
 * - The entitlement `pro` is in force while the later of its `expires_date` and of the end of the
 *   grace period Play gives after a failed payment is after the answer's time: Pro until then. One
 *   whose `expires_date` is null, as one granted for life from RevenueCat's dashboard, never ends.
 * - Once that time has passed, Pro lasts until its `expires_date`, which [POLICY.proGraceMs] then
 *   extends, while the subscription that granted it still renews by itself, with no billing issue
 *   and no refund: Play may renew a subscription hours after its period ended.
 * - Anything else, the entitlement missing among them, is no Pro.
 *
 * Purchases in RevenueCat's sandbox, which Play's license testers make, count like any other: only
 * the testers the owner lists in Play Console can make them.
 */
function planOf(answer: unknown): PlanState | null {
  if (!isRecord(answer) || !isRecord(answer.subscriber)) return null;
  const checkedAt = answer.request_date_ms;
  if (typeof checkedAt !== "number" || !Number.isSafeInteger(checkedAt) || checkedAt <= 0) return null;
  const { entitlements, subscriptions } = answer.subscriber;
  if (!isRecord(entitlements) || !isRecord(subscriptions)) return null;
  if (!Object.hasOwn(entitlements, ENTITLEMENT)) return { proUntil: null, checkedAt };
  const pro = entitlements[ENTITLEMENT];
  if (!isRecord(pro)) return null;
  // Only an `expires_date` of null never ends: one that is missing cannot be read.
  const expires = timeOf(pro.expires_date);
  const grace = timeOf(pro.grace_period_expires_date ?? null);
  if (expires === undefined || grace === undefined) return null;
  if (expires === null) return { proUntil: Number.POSITIVE_INFINITY, checkedAt };
  const until = grace === null ? expires : Math.max(expires, grace);
  if (until > checkedAt) return { proUntil: until, checkedAt };
  const granting = subscriptionOf(subscriptions, pro.product_identifier);
  return { proUntil: granting !== null && stillRenews(granting) ? expires : null, checkedAt };
}

/**
 * The subscription that granted the entitlement, listed under the product the entitlement names; a
 * Play product named with its base plan, `<subscription>:<base plan>`, is also looked for under the
 * subscription alone.
 */
function subscriptionOf(subscriptions: Record<string, unknown>, product: unknown): Record<string, unknown> | null {
  if (typeof product !== "string" || product === "") return null;
  for (const id of [product, product.split(":")[0]]) {
    const subscription = Object.hasOwn(subscriptions, id) ? subscriptions[id] : undefined;
    if (isRecord(subscription)) return subscription;
  }
  return null;
}

/** Whether [subscription] renews by itself: nobody turned renewal off, no payment failed and nothing was refunded. */
function stillRenews(subscription: Record<string, unknown>): boolean {
  return subscription.unsubscribe_detected_at === null && subscription.billing_issues_detected_at === null && subscription.refunded_at === null;
}

/** A time RevenueCat wrote in ISO 8601, in ms since the epoch: null for null, undefined for anything that is not a time. */
function timeOf(value: unknown): number | null | undefined {
  if (value === null) return null;
  const time = typeof value === "string" ? Date.parse(value) : Number.NaN;
  return Number.isFinite(time) ? time : undefined;
}
