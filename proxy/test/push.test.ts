import { createScheduledController } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import worker, { type Env } from "../src/index";
import { POLICY } from "../src/policy";
import { PUSH_TEXT, type Push } from "../src/push";
import type { RequestView } from "../src/requests";
import { fakeFcm, invalidRegistration, unregistered } from "./fake-fcm";
import { network } from "./network";
import { addPlaceholder, api, invite, join, newGroup, type Person, person, rows, sha256, VND } from "./shared";

const DAY = 24 * 60 * 60 * 1000;

async function device(who: Person, token: string, id: string = crypto.randomUUID()): Promise<string> {
  const put = await api(who, "PUT", `/v1/devices/${id}`, { token });
  if (put.status !== 204) throw new Error(`The device was not registered: ${put.status}`);
  return id;
}

async function tokensOf(who: Person): Promise<string[]> {
  return (await rows<{ token: string }>("SELECT token FROM devices WHERE account = ?1 ORDER BY token", who.account)).map((row) => row.token);
}

/** A request from [creditor] that [debtor] accepted; its id. */
async function acceptedRequest(creditor: Person, debtor: Person, settings: Partial<Env> = {}): Promise<string> {
  const made = await api<{ request: RequestView; url: string }>(
    creditor,
    "POST",
    "/v1/requests",
    { id: crypto.randomUUID(), amountMinor: 50_000, currency: VND, payerName: "Minh", creditorName: "Lan", method: { kind: "PlainDetails", text: "Pay me" } },
    settings,
  );
  await api(debtor, "POST", `/v1/request-links/${made.body.url.split("/").pop()}/accept`, undefined, settings);
  return made.body.request.id;
}

/** Frankfurter, as far as the daily run needs it. */
function frankfurter() {
  network.use(
    http.get("https://api.frankfurter.dev/v2/rates", () => HttpResponse.json([{ date: "2026-11-14", base: "EUR", quote: "USD", rate: 1.1 }])),
    http.get("https://api.frankfurter.dev/v2/currencies", () => HttpResponse.json([{ iso_code: "USD", type: "currency" }])),
  );
}

function dailyRun(settings: Partial<Env> = {}): Promise<void> {
  return worker.scheduled(createScheduledController({ cron: "30 16 * * *" }), { ...env, ...settings });
}

describe("push notifications", () => {
  it("go only to a request's debtor or creditor, or to a group's owner for a claim, with fixed text and ids only", async () => {
    const fcm = await fakeFcm();
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const owner = await person();
    const group = await newGroup(owner, "Secret garden trip", VND);
    const minh = await person();
    const minhId = await join(group, minh, "Minh Secretname");
    const lan = await person();
    await join(group, lan, "Lan Secretname");
    const hoaId = await addPlaceholder(group, "Hoa Secretname");
    const stranger = await person();
    for (const [who, token] of [
      [owner, "token-owner"],
      [minh, "token-minh"],
      [lan, "token-lan"],
      [stranger, "token-stranger"],
    ] as const) {
      await device(who, token);
    }

    const made = await api<{ request: RequestView }>(
      owner,
      "POST",
      "/v1/requests",
      {
        id: crypto.randomUUID(),
        amountMinor: 120_000,
        currency: VND,
        payerName: "Minh Secretname",
        creditorName: "Owner Secretname",
        title: "Secret hot pot",
        method: { kind: "VietQr", bankBin: "970436", accountNumber: "0123456789", accountName: "SECRET NAME" },
        groupId: group.id,
        toMemberId: minhId,
      },
      settings,
    );
    const id = made.body.request.id;
    await api(minh, "POST", `/v1/requests/${id}/reported-paid`, undefined, settings);
    await api(owner, "POST", `/v1/requests/${id}/confirmed-paid`, undefined, settings);
    await api(stranger, "POST", `/v1/invites/${await invite(group)}/join`, { claimMemberId: hoaId, name: "Stranger Secretname" }, settings);
    await api(owner, "POST", `/v1/groups/${group.id}/settlements`, { id: crypto.randomUUID(), fromMemberId: minhId, amountMinor: 1 }, settings);

    expect(fcm.messages.map((message) => [message.token, message.data])).toEqual([
      ["token-minh", { type: "request", requestId: id }],
      ["token-owner", { type: "reported", requestId: id }],
      ["token-minh", { type: "confirmed", requestId: id }],
      ["token-owner", { type: "claim", groupId: group.id }],
    ]);
    for (const message of fcm.messages) {
      expect(Object.keys(message).sort()).toEqual(["android", "data", "notification", "token"]);
      expect(message.android).toEqual({ notification: { channel_id: "requests" } });
      expect(message.notification).toEqual(PUSH_TEXT[message.data.type as Push["type"]]);
      expect(JSON.stringify(message)).not.toMatch(/Secret|SECRET|970436|0123456789|garden|hot pot|https?:/i);
    }
    expect(fcm.tokensIssued()).toBe(1);
  });

  it("are not sent without a service account, or with one that is not a key file, and every flow still works", async () => {
    const fcm = await fakeFcm();
    for (const settings of [{}, { FCM_SERVICE_ACCOUNT: "not json" }, { FCM_SERVICE_ACCOUNT: JSON.stringify({ project_id: "split-vibe-test" }) }]) {
      const lan = await person();
      const minh = await person();
      await device(lan, `token-${crypto.randomUUID()}`);
      await device(minh, `token-${crypto.randomUUID()}`);
      const id = await acceptedRequest(lan, minh, settings);

      expect((await api(minh, "POST", `/v1/requests/${id}/reported-paid`, undefined, settings)).status).toBe(200);
      expect((await api(lan, "POST", `/v1/requests/${id}/confirmed-paid`, undefined, settings)).status).toBe(200);
    }
    expect(fcm.messages).toEqual([]);
    expect(fcm.tokensIssued()).toBe(0);
  });

  it("are not sent when Google refuses the access token, and the flow still works", async () => {
    const fcm = await fakeFcm({ refuseToken: () => HttpResponse.json({ error: "invalid_grant" }, { status: 400 }) });
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const lan = await person();
    const minh = await person();
    await device(lan, "token-lan");
    const id = await acceptedRequest(lan, minh, settings);

    expect((await api(minh, "POST", `/v1/requests/${id}/reported-paid`, undefined, settings)).status).toBe(200);
    expect(fcm.messages).toEqual([]);
  });

  it("share one access token until shortly before it expires", async () => {
    const fcm = await fakeFcm();
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const lan = await person();
    const minh = await person();
    await device(lan, "token-lan");
    await device(minh, "token-minh");
    const id = await acceptedRequest(lan, minh, settings);

    await api(minh, "POST", `/v1/requests/${id}/reported-paid`, undefined, settings);
    await api(lan, "POST", `/v1/requests/${id}/confirmed-paid`, undefined, settings);

    expect(fcm.messages).toHaveLength(2);
    expect(fcm.tokensIssued()).toBe(1);
  });

  it("fetch a new access token when the one held is about to expire", async () => {
    // Under the five minutes before expiry at which a token is no longer used.
    const fcm = await fakeFcm({ expiresIn: 200 });
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const lan = await person();
    const minh = await person();
    await device(lan, "token-lan");
    await device(minh, "token-minh");
    const id = await acceptedRequest(lan, minh, settings);

    await api(minh, "POST", `/v1/requests/${id}/reported-paid`, undefined, settings);
    await api(lan, "POST", `/v1/requests/${id}/confirmed-paid`, undefined, settings);

    expect(fcm.messages).toHaveLength(2);
    expect(fcm.tokensIssued()).toBe(2);
  });

  it("forget a device whose token FCM says is unregistered or no registration token, and keep the others", async () => {
    const answers: Record<string, () => Response> = {
      "token-gone": unregistered,
      "token-invalid": () => invalidRegistration(false),
      "token-invalid-field": () => invalidRegistration(true),
      "token-busy": () => HttpResponse.json({ error: { code: 503, status: "UNAVAILABLE" } }, { status: 503 }),
      "token-wrong-project": () =>
        HttpResponse.json({ error: { code: 404, message: "Requested entity was not found.", status: "NOT_FOUND" } }, { status: 404 }),
      "token-bad-payload": () =>
        HttpResponse.json(
          {
            error: {
              code: 400,
              message: "Invalid value at 'message.data'",
              status: "INVALID_ARGUMENT",
              details: [{ "@type": "type.googleapis.com/google.rpc.BadRequest", fieldViolations: [{ field: "message.data", description: "Invalid value" }] }],
            },
          },
          { status: 400 },
        ),
    };
    const fcm = await fakeFcm({ answer: (token) => answers[token]?.() });
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const lan = await person();
    const minh = await person();
    for (const token of [...Object.keys(answers), "token-fine"]) await device(minh, token);
    const id = await acceptedRequest(lan, minh, settings);

    expect((await api(lan, "POST", `/v1/requests/${id}/confirmed-paid`, undefined, settings)).status).toBe(200);

    expect(fcm.messages).toHaveLength(7);
    expect(await tokensOf(minh)).toEqual(["token-bad-payload", "token-busy", "token-fine", "token-wrong-project"]);
  });
});

describe("a device", () => {
  it("is registered for the caller, replaced under the same id, and removed by its own account only", async () => {
    const lan = await person();
    const id = await device(lan, "token-first");

    await device(lan, "token-second", id);
    expect(await rows("SELECT id, account, token FROM devices")).toEqual([{ id, account: lan.account, token: "token-second" }]);
    expect((await api(await person(), "DELETE", `/v1/devices/${id}`)).status).toBe(204);
    expect(await tokensOf(lan)).toEqual(["token-second"]);

    const removed = await api(lan, "DELETE", `/v1/devices/${id}`);
    expect(removed.status).toBe(204);
    expect(await tokensOf(lan)).toEqual([]);
    expect((await api(lan, "DELETE", `/v1/devices/${id}`)).status).toBe(204);
  });

  it("keeps a token for one account only, so a phone that changes accounts stops getting the old one's notifications", async () => {
    const lan = await person();
    const minh = await person();
    const phone = await device(lan, "token-shared-phone");

    await device(minh, "token-shared-phone");
    expect(await tokensOf(lan)).toEqual([]);
    expect(await tokensOf(minh)).toEqual(["token-shared-phone"]);

    await device(lan, "token-lan", phone);
    await device(minh, "token-minh", phone);
    expect(await rows("SELECT account, token FROM devices WHERE id = ?1", phone)).toEqual([{ account: minh.account, token: "token-minh" }]);
  });

  it("is one of the ten newest of its account at most", async () => {
    const lan = await person();
    const first = await device(lan, "token-0");
    await env.DB.prepare("UPDATE devices SET updated_at = 1 WHERE id = ?1").bind(first).run();
    for (let count = 1; count <= POLICY.maxDevices; count++) await device(lan, `token-${count}`);

    const kept = await tokensOf(lan);
    expect(kept).toHaveLength(POLICY.maxDevices);
    expect(kept).not.toContain("token-0");
  });

  it("needs a token of 1 to 4096 characters and a device id", async () => {
    const lan = await person();
    for (const body of [{}, { token: "" }, { token: "x".repeat(4097) }, { token: 42 }]) {
      expect((await api(lan, "PUT", `/v1/devices/${crypto.randomUUID()}`, body)).status, JSON.stringify(body)).toBe(400);
    }
    expect((await api(lan, "PUT", "/v1/devices/not-a-uuid", { token: "token" })).status).toBe(400);
    expect((await api(lan, "PUT", `/v1/devices/${crypto.randomUUID()}`, { token: "x".repeat(4096) })).status).toBe(204);
  });
});

describe("the daily run", () => {
  /** Requests from Lan to Minh, accepted, then moved to the state each case needs. */
  async function requests() {
    const lan = await person();
    const minh = await person();
    await device(minh, "token-minh");
    await device(lan, "token-lan");
    const now = Date.now();
    const cases: Record<string, { set: string; values: unknown[] }> = {
      dueDaily: { set: "remind_every_days = 1, created_at = ?2", values: [now - 2 * DAY] },
      dueWithinTheHour: { set: "remind_every_days = 1, created_at = ?2", values: [now - DAY + 30 * 60 * 1000] },
      dueWeekly: { set: "remind_every_days = 7, created_at = ?2, last_reminded_at = ?3", values: [now - 30 * DAY, now - 8 * DAY] },
      remindedThisWeek: { set: "remind_every_days = 7, created_at = ?2, last_reminded_at = ?3", values: [now - 30 * DAY, now - 6 * DAY] },
      notYet: { set: "remind_every_days = 3, created_at = ?2", values: [now - 2 * DAY] },
      never: { set: "remind_every_days = 0, created_at = ?2", values: [now - 30 * DAY] },
      reported: { set: "remind_every_days = 1, created_at = ?2, reported_paid_at = ?2", values: [now - 2 * DAY] },
      confirmed: { set: "remind_every_days = 1, created_at = ?2, confirmed_paid_at = ?2", values: [now - 2 * DAY] },
      unbound: { set: "remind_every_days = 1, created_at = ?2, debtor = NULL, debtor_bound_at = NULL", values: [now - 2 * DAY] },
    };
    const ids: Record<string, string> = {};
    for (const [name, change] of Object.entries(cases)) {
      ids[name] = await acceptedRequest(lan, minh);
      await env.DB.prepare(`UPDATE requests SET ${change.set} WHERE id = ?1`)
        .bind(ids[name], ...change.values)
        .run();
    }
    return { lan, minh, ids };
  }

  it("reminds the debtor of each request that is due, once every N days", async () => {
    const fcm = await fakeFcm();
    frankfurter();
    const { ids } = await requests();
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };
    const before = Date.now();

    await dailyRun(settings);

    const due = [ids.dueDaily, ids.dueWithinTheHour, ids.dueWeekly];
    expect(fcm.messages.map((message) => message.token)).toEqual(["token-minh", "token-minh", "token-minh"]);
    expect(fcm.messages.map((message) => message.data.requestId).sort()).toEqual([...due].sort());
    expect(fcm.messages.every((message) => message.data.type === "reminder" && message.notification.title === PUSH_TEXT.reminder.title)).toBe(true);
    const reminded = await rows<{ id: string; last_reminded_at: number }>("SELECT id, last_reminded_at FROM requests WHERE last_reminded_at >= ?1", before);
    expect(reminded.map((row) => row.id).sort()).toEqual([...due].sort());

    await dailyRun(settings);
    expect(fcm.messages).toHaveLength(3);
  });

  it("sends at most its share of notifications, and the reminders left over go the next day", async () => {
    const fcm = await fakeFcm();
    frankfurter();
    const lan = await person();
    const minh = await person();
    await device(minh, "token-minh");
    await env.DB.prepare(
      `WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?1)
       INSERT INTO requests (id, creditor, debtor, debtor_bound_at, amount_minor, currency_code, currency_digits,
         payer_name, creditor_name, method, link_hash, created_at, remind_every_days)
       SELECT lower(hex(randomblob(16))), ?2, ?3, 1, 100, 'VND', 0, 'Minh', 'Lan', '{}', lower(hex(randomblob(32))), ?4 - i, 1 FROM n`,
    )
      .bind(POLICY.remindersPerRun + 1, lan.account, minh.account, Date.now() - 2 * DAY)
      .run();
    const settings = { FCM_SERVICE_ACCOUNT: fcm.serviceAccount };

    await dailyRun(settings);
    expect(fcm.messages).toHaveLength(POLICY.remindersPerRun);

    await dailyRun(settings);
    expect(fcm.messages).toHaveLength(POLICY.remindersPerRun + 1);
  });

  it("reminds nobody and records nothing without push set up", async () => {
    frankfurter();
    await requests();

    await dailyRun();

    expect(await rows("SELECT id FROM requests WHERE last_reminded_at IS NOT NULL AND last_reminded_at > ?1", Date.now() - DAY)).toEqual([]);
  });

  it("forgets invites and deleted requests a month after they stopped working, and hourly windows that ended", async () => {
    frankfurter();
    const owner = await person();
    const group = await newGroup(owner);
    const longGone = await invite(group);
    const recentlyGone = await invite(group);
    const now = Date.now();
    const expire = async (token: string, at: number) =>
      env.DB.prepare("UPDATE invites SET expires_at = ?2 WHERE token_hash = ?1").bind(await sha256(token), at).run();
    await expire(longGone, now - POLICY.forgetAfterMs - DAY);
    await expire(recentlyGone, now - POLICY.forgetAfterMs + DAY);
    const oldRequest = await acceptedRequest(owner, await person());
    const newRequest = await acceptedRequest(owner, await person());
    for (const id of [oldRequest, newRequest]) await api(owner, "DELETE", `/v1/requests/${id}`);
    await env.DB.prepare("UPDATE requests SET deleted_at = ?2 WHERE id = ?1").bind(oldRequest, now - POLICY.forgetAfterMs - DAY).run();
    await env.DB.prepare("UPDATE rate_windows SET started_at = ?2 WHERE account = ?1").bind(owner.account, now - 2 * POLICY.hourMs).run();
    const other = await person();
    await acceptedRequest(other, await person());

    await dailyRun();

    expect((await api(owner, "GET", `/v1/invites/${longGone}`)).status).toBe(404);
    expect((await api(owner, "GET", `/v1/invites/${recentlyGone}`)).status).toBe(410);
    expect((await rows<{ id: string }>("SELECT id FROM requests WHERE deleted_at IS NOT NULL")).map((row) => row.id)).toEqual([newRequest]);
    expect(await rows("SELECT account FROM rate_windows")).toEqual([{ account: other.account }]);
  });
});
