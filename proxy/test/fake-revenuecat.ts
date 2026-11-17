import { http, HttpResponse } from "msw";
import { network } from "./network";

const SUBSCRIBER_URL = "https://api.revenuecat.com/v1/subscribers/:id";
const DAY = 24 * 60 * 60 * 1000;

/** The `REVENUECAT_SECRET_KEY` the tests run with, from vitest.config.ts. */
export const TEST_KEY = "test-revenuecat-secret-key";
/** The `REVENUECAT_WEBHOOK_AUTH` the tests run with, from vitest.config.ts. */
export const TEST_WEBHOOK_AUTH = "test-revenuecat-webhook-auth";

/** What RevenueCat's v1 answer about a customer shows of its purchases. */
export type Customer = { entitlements: Record<string, unknown>; subscriptions: Record<string, unknown> };

/** One request RevenueCat got: its method, the customer it named, and the headers that matter. */
export type Asked = { method: string; id: string; authorization: string | null; platform: string | null };

export type FakeRevenueCat = {
  /** Gives the customer [id] what [customer] holds from now on, as a purchase or a renewal does. */
  hold(id: string, customer: Customer): void;
  /** Whether RevenueCat has the customer [id]: one it was given, or one a GET created. */
  has(id: string): boolean;
  /** Every request RevenueCat got, in order. */
  asked: Asked[];
  /** How many GETs RevenueCat got, of the customer [id] when one is named. */
  gets(id?: string): number;
  /**
   * RevenueCat's answer instead of the fake's own, whenever this returns one: an outage, say, or an
   * answer held back until another has overtaken it.
   */
  answer?: (asked: Asked) => Response | undefined | Promise<Response | undefined>;
  /** RevenueCat's clock, which `request_date_ms` reads: the real one unless a test sets another. */
  clock: () => number;
};

/**
 * RevenueCat's REST API v1 for customers, as a fake. It takes only [TEST_KEY], as a Bearer token. A
 * GET answers what the customer holds, with `request_date_ms` from [FakeRevenueCat.clock], and as
 * RevenueCat does, creates an empty customer with 201 when there was none. A DELETE deletes the
 * customer, or answers 404 when there is none.
 */
export function fakeRevenueCat(): FakeRevenueCat {
  const customers = new Map<string, Customer>();
  const fake: FakeRevenueCat = {
    hold(id, customer) {
      customers.set(id, customer);
    },
    has: (id) => customers.has(id),
    asked: [],
    gets: (id) => fake.asked.filter((asked) => asked.method === "GET" && (id === undefined || asked.id === id)).length,
    clock: () => Date.now(),
  };
  const asked = async (request: { method: string; headers: { get(name: string): string | null } }, id: string) => {
    const seen: Asked = { method: request.method, id, authorization: request.headers.get("authorization"), platform: request.headers.get("x-platform") };
    fake.asked.push(seen);
    return { asked: seen, answer: await fake.answer?.(seen) };
  };
  network.use(
    http.get(SUBSCRIBER_URL, async ({ request, params }) => {
      const { asked: seen, answer } = await asked(request, String(params.id));
      if (answer !== undefined) return answer;
      if (seen.authorization !== `Bearer ${TEST_KEY}`) return unauthorized();
      const known = customers.get(seen.id);
      const customer = known ?? { entitlements: {}, subscriptions: {} };
      if (known === undefined) customers.set(seen.id, customer);
      return HttpResponse.json(subscriberAnswer(seen.id, customer, fake.clock()), { status: known === undefined ? 201 : 200 });
    }),
    http.delete(SUBSCRIBER_URL, async ({ request, params }) => {
      const { asked: seen, answer } = await asked(request, String(params.id));
      if (answer !== undefined) return answer;
      if (seen.authorization !== `Bearer ${TEST_KEY}`) return unauthorized();
      if (!customers.delete(seen.id)) return HttpResponse.json({ code: 7259, message: "Subscriber not found." }, { status: 404 });
      return HttpResponse.json({ app_user_id: seen.id });
    }),
  );
  return fake;
}

function unauthorized(): Response {
  return HttpResponse.json({ code: 7225, message: "Invalid API Key." }, { status: 401 });
}

/** RevenueCat's v1 answer about the customer [id], holding [customer], made at [at]. */
export function subscriberAnswer(id: string, customer: Customer, at: number): Record<string, unknown> {
  return {
    request_date: new Date(at).toISOString(),
    request_date_ms: at,
    subscriber: {
      entitlements: customer.entitlements,
      first_seen: "2026-11-01T08:00:00Z",
      last_seen: "2026-11-01T08:00:00Z",
      management_url: null,
      non_subscriptions: {},
      original_app_user_id: id,
      original_application_version: null,
      original_purchase_date: null,
      other_purchases: {},
      subscriber_attributes: {},
      subscriptions: customer.subscriptions,
    },
  };
}

/** A purchase of the Play subscription `pro`: when its period ends, and what happened to it since, each at a time. */
export type PlayPurchase = {
  expires: number;
  /** The end of the grace period Play gives after a failed payment. */
  grace?: number;
  /** When renewal was turned off. */
  unsubscribed?: number;
  billingIssue?: number;
  refunded?: number;
  /** Bought by one of Play's license testers, which RevenueCat marks as sandbox. */
  sandbox?: boolean;
  /** The product the entitlement names: `pro` unless given; the subscription is listed under `pro`. */
  product?: string;
};

/** A customer whose Play subscription `pro`, on its monthly base plan, grants the entitlement `pro`. */
export function playCustomer(purchase: PlayPurchase): Customer {
  const started = iso(purchase.expires - 30 * DAY);
  const expires = iso(purchase.expires);
  const grace = iso(purchase.grace);
  return {
    entitlements: {
      pro: {
        expires_date: expires,
        grace_period_expires_date: grace,
        product_identifier: purchase.product ?? "pro",
        product_plan_identifier: "monthly",
        purchase_date: started,
      },
    },
    subscriptions: {
      pro: {
        auto_resume_date: null,
        billing_issues_detected_at: iso(purchase.billingIssue),
        expires_date: expires,
        grace_period_expires_date: grace,
        is_sandbox: purchase.sandbox ?? false,
        original_purchase_date: started,
        ownership_type: "PURCHASED",
        period_type: "normal",
        product_plan_identifier: "monthly",
        purchase_date: started,
        refunded_at: iso(purchase.refunded),
        store: "play_store",
        store_transaction_id: "GPA.3300-0000-0000-00000",
        unsubscribe_detected_at: iso(purchase.unsubscribed),
      },
    },
  };
}

/**
 * A customer given the entitlement `pro` from RevenueCat's dashboard until [expires], or for life
 * for null. The grant is listed among the subscriptions, of store `promotional`, with nothing that
 * says it stopped renewing, though it never renews.
 */
export function promotionalCustomer(expires: number | null): Customer {
  const granted = iso(Date.now() - DAY);
  const ends = iso(expires ?? undefined);
  const product = expires === null ? "rc_promo_pro_lifetime" : "rc_promo_pro_custom";
  return {
    entitlements: {
      pro: { expires_date: ends, grace_period_expires_date: null, product_identifier: product, purchase_date: granted },
    },
    subscriptions: {
      [product]: {
        billing_issues_detected_at: null,
        expires_date: ends,
        grace_period_expires_date: null,
        is_sandbox: false,
        original_purchase_date: granted,
        period_type: "normal",
        purchase_date: granted,
        refunded_at: null,
        store: "promotional",
        unsubscribe_detected_at: null,
      },
    },
  };
}

function iso(time: number | undefined): string | null {
  return time === undefined ? null : new Date(time).toISOString();
}

/** A webhook event of [type] about the customer [id], as RevenueCat sends one, with [fields] added or replaced. */
export function webhookEvent(type: string, id: unknown, fields: Record<string, unknown> = {}): Record<string, unknown> {
  const now = Date.now();
  return {
    api_version: "1.0",
    event: {
      id: crypto.randomUUID().toUpperCase(),
      type,
      event_timestamp_ms: now,
      app_id: "app0123456789",
      app_user_id: id,
      original_app_user_id: id,
      aliases: [id],
      environment: "PRODUCTION",
      store: "PLAY_STORE",
      product_id: "pro:monthly",
      entitlement_ids: ["pro"],
      period_type: "NORMAL",
      purchased_at_ms: now,
      expiration_at_ms: now + 30 * DAY,
      ...fields,
    },
  };
}

/** A `TRANSFER` event, which names the customers a purchase moved from and to, and has no `app_user_id`. */
export function transferEvent(from: string[], to: string[]): Record<string, unknown> {
  return {
    api_version: "1.0",
    event: {
      id: crypto.randomUUID().toUpperCase(),
      type: "TRANSFER",
      event_timestamp_ms: Date.now(),
      app_id: "app0123456789",
      environment: "PRODUCTION",
      store: "PLAY_STORE",
      transferred_from: from,
      transferred_to: to,
    },
  };
}
