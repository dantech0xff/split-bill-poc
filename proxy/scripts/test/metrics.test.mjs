// The tests of `npm run metrics`, with a stand-in for Analytics Engine's SQL API: they need no
// token and no network.
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { daysOf, MAX_DAYS, MetricsError, main, queryOf, rowsOf, startOf, tableOf } from "../metrics.mjs";

const ACCOUNT = "0123456789abcdef0123456789abcdef";
const TOKEN = "metrics-test-token-never-printed";
const NOW = Date.UTC(2026, 10, 19, 15, 30);

/** A stand-in for the SQL API that answers [status] with [body], and keeps what it was asked. */
function sqlApi(status, body) {
  const asked = [];
  const fetchImpl = async (url, init) => {
    asked.push({ url, init });
    return new Response(typeof body === "string" ? body : JSON.stringify(body), { status });
  };
  return { asked, fetchImpl };
}

/** Runs the command with [argv] and [environment], and gives what it printed, or the error it stopped with. */
async function run(argv, environment, api) {
  const printed = [];
  try {
    await main(argv, environment, api.fetchImpl, (text) => printed.push(text), NOW);
    return { printed, error: null };
  } catch (error) {
    return { printed, error };
  }
}

describe("npm run metrics", () => {
  it("shows 7 days unless it is given a number of days from 1 to 90", () => {
    assert.equal(daysOf(undefined), 7);
    assert.equal(daysOf("1"), 1);
    assert.equal(daysOf(String(MAX_DAYS)), 90);
    for (const wrong of ["0", "91", "-1", "7.5", "seven", "", "1e2"]) assert.throws(() => daysOf(wrong), MetricsError, wrong);
  });

  it("counts from midnight UTC of the first day, by day, event and outcome, weighing each point by its sample", () => {
    assert.equal(startOf(7, NOW), Date.UTC(2026, 10, 13));
    assert.equal(startOf(1, NOW), Date.UTC(2026, 10, 19));
    const query = queryOf(7, NOW);
    assert.match(query, /FROM split_vibe_events /);
    assert.match(query, /WHERE timestamp >= toDateTime\('2026-11-13 00:00:00'\)/);
    assert.match(query, /SUM\(_sample_interval\) AS count, SUM\(_sample_interval \* double1\) AS cost_micro/);
    assert.match(query, /GROUP BY day, event, outcome ORDER BY day, event, outcome FORMAT JSON$/);
  });

  it("asks the SQL API of the account with the token, and prints a table", async () => {
    const api = sqlApi(200, {
      meta: [],
      data: [
        { day: "2026-11-18 00:00:00", event: "bill_analysis", outcome: "ok", count: "12", cost_micro: 734210 },
        { day: "2026-11-18 00:00:00", event: "sign_in", outcome: "ok", count: "3", cost_micro: 0 },
        { day: "2026-11-19 00:00:00", event: "daily_run", outcome: "ok", count: 1, cost_micro: 0 },
      ],
      rows: 3,
    });

    const { printed, error } = await run(["7"], { CLOUDFLARE_ACCOUNT_ID: ACCOUNT, CLOUDFLARE_API_TOKEN: TOKEN }, api);

    assert.equal(error, null);
    assert.equal(api.asked.length, 1);
    assert.equal(api.asked[0].url, `https://api.cloudflare.com/client/v4/accounts/${ACCOUNT}/analytics_engine/sql`);
    assert.equal(api.asked[0].init.method, "POST");
    assert.equal(api.asked[0].init.headers.authorization, `Bearer ${TOKEN}`);
    assert.equal(api.asked[0].init.body, queryOf(7, NOW));
    assert.deepEqual(printed[0].split("\n"), [
      "Counts of the last 7 days, in UTC",
      "",
      "Day         Event          Outcome  Count     Cost",
      "2026-11-18  bill_analysis  ok          12  $0.7342",
      "2026-11-18  sign_in        ok           3",
      "2026-11-19  daily_run      ok           1",
    ]);
    assert.ok(!printed.join("\n").includes(TOKEN));
  });

  it("says when there is nothing to count", () => {
    assert.equal(tableOf([], 1), "Counts of the last 1 day, in UTC: none.");
  });

  it("stops without the token or the account id, naming what is missing and printing neither", async () => {
    const api = sqlApi(200, { data: [] });
    for (const [environment, missing] of [
      [{}, "CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN"],
      [{ CLOUDFLARE_API_TOKEN: TOKEN }, "CLOUDFLARE_ACCOUNT_ID"],
      [{ CLOUDFLARE_ACCOUNT_ID: ACCOUNT, CLOUDFLARE_API_TOKEN: "  " }, "CLOUDFLARE_API_TOKEN"],
    ]) {
      const { error } = await run([], environment, api);
      assert.ok(error instanceof MetricsError);
      assert.match(error.message, new RegExp(`^Set ${missing}:`));
      assert.ok(!error.message.includes(TOKEN));
      assert.ok(!error.message.includes(ACCOUNT));
    }
    const { error } = await run([], { CLOUDFLARE_ACCOUNT_ID: "not-an-account", CLOUDFLARE_API_TOKEN: TOKEN }, api);
    assert.match(error.message, /is not an account id/);
    assert.equal(api.asked.length, 0);
  });

  it("stops on an answer that is not rows of data, saying why and never with the token", async () => {
    for (const [status, body, said] of [
      [403, '{"errors":[{"code":10000,"message":"Authentication error"}]}', /^Analytics Engine answered 403: \{"errors"/],
      [422, "ERROR: Unknown table split_vibe_events", /^Analytics Engine answered 422: ERROR: Unknown table/],
      [200, "not json", /other than JSON/],
      [200, { data: [{ day: "2026-11-19 00:00:00", event: "sign_in" }] }, /a row that is not/],
      [200, { rows: 0 }, /other than rows of data/],
    ]) {
      const { error } = await run([], { CLOUDFLARE_ACCOUNT_ID: ACCOUNT, CLOUDFLARE_API_TOKEN: TOKEN }, sqlApi(status, body));
      assert.ok(error instanceof MetricsError, String(status));
      assert.match(error.message, said);
      assert.ok(!error.message.includes(TOKEN));
    }
    const unreachable = { fetchImpl: async () => Promise.reject(new TypeError("fetch failed")) };
    const { error } = await run([], { CLOUDFLARE_ACCOUNT_ID: ACCOUNT, CLOUDFLARE_API_TOKEN: TOKEN }, unreachable);
    assert.equal(error.message, "Analytics Engine could not be reached (TypeError).");
  });

  it("reads a count the SQL API writes as text, as it writes whole numbers of 64 bits", () => {
    assert.deepEqual(rowsOf({ data: [{ day: "2026-11-19 00:00:00", event: "sign_in", outcome: "ok", count: "5", cost_micro: 0 }] }), [
      { day: "2026-11-19", event: "sign_in", outcome: "ok", count: 5, costMicro: 0 },
    ]);
  });
});
