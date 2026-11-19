// Prints the proxy's daily counts, as the binding METRICS writes them to the Workers Analytics
// Engine dataset split_vibe_events: for each day in UTC of the last N days, today included, each
// event and outcome, how many there were and what the calls of the model among them cost.
//
//   npm run metrics -- [days]
//
// Days are 7 unless a number from 1 to 90 is given: Analytics Engine keeps data for three months.
// It reads the counts through Analytics Engine's SQL API with an API token that may read the
// account's analytics (the permission Account Analytics: Read) and the account's id, from the
// environment: CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID. Neither is ever printed. A count is
// what Analytics Engine estimates from its sample, the sum of each data point's sample interval.

import { pathToFileURL } from "node:url";

export const DATASET = "split_vibe_events";
export const DEFAULT_DAYS = 7;
export const MAX_DAYS = 90;
/** The longest the SQL API may take to answer. */
const TIMEOUT_MS = 30_000;
/** A Cloudflare account id: 32 hex digits, as the dashboard shows it. */
const ACCOUNT_ID = /^[0-9a-f]{32}$/i;

export class MetricsError extends Error {}

/** The number of days [argument] asks for, [DEFAULT_DAYS] without one. */
export function daysOf(argument) {
  if (argument === undefined) return DEFAULT_DAYS;
  const days = /^\d{1,3}$/.test(argument) ? Number(argument) : Number.NaN;
  if (!(days >= 1 && days <= MAX_DAYS)) throw new MetricsError(`Say how many days to show, from 1 to ${MAX_DAYS}, such as \`npm run metrics -- 30\`; not ${argument}.`);
  return days;
}

/** Midnight UTC of the first day counted: [days] days back from the day of [now], that day included. */
export function startOf(days, now) {
  const date = new Date(now);
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() - (days - 1));
}

/** The SQL that counts the data points of the last [days] days, by day, event and outcome. */
export function queryOf(days, now) {
  const start = new Date(startOf(days, now)).toISOString().slice(0, 19).replace("T", " ");
  return [
    "SELECT toStartOfInterval(timestamp, INTERVAL '1' DAY) AS day, blob1 AS event, blob2 AS outcome,",
    "SUM(_sample_interval) AS count, SUM(_sample_interval * double1) AS cost_micro",
    `FROM ${DATASET}`,
    `WHERE timestamp >= toDateTime('${start}')`,
    "GROUP BY day, event, outcome",
    "ORDER BY day, event, outcome",
    "FORMAT JSON",
  ].join(" ");
}

/** The rows of the SQL API's answer, as `{day, event, outcome, count, costMicro}`; it stops on an answer of another shape. */
export function rowsOf(answer) {
  if (typeof answer !== "object" || answer === null || !Array.isArray(answer.data)) throw new MetricsError("Analytics Engine answered something other than rows of data.");
  return answer.data.map((row) => {
    const count = Number(row?.count);
    const costMicro = Number(row?.cost_micro ?? 0);
    if (typeof row?.day !== "string" || typeof row.event !== "string" || typeof row.outcome !== "string" || !Number.isFinite(count) || !Number.isFinite(costMicro)) {
      throw new MetricsError("Analytics Engine answered a row that is not a day, an event, an outcome, a count and a cost.");
    }
    return { day: row.day.slice(0, 10), event: row.event, outcome: row.outcome, count, costMicro };
  });
}

/** [rows] as a table, one line each, for the last [days] days; the cost is left out where there is none. */
export function tableOf(rows, days) {
  const title = `Counts of the last ${days} ${days === 1 ? "day" : "days"}, in UTC`;
  if (rows.length === 0) return `${title}: none.`;
  const lines = [["Day", "Event", "Outcome", "Count", "Cost"]];
  for (const row of rows) {
    lines.push([row.day, row.event, row.outcome, String(Math.round(row.count)), row.costMicro > 0 ? `$${(row.costMicro / 1_000_000).toFixed(4)}` : ""]);
  }
  const widths = lines[0].map((_, column) => Math.max(...lines.map((line) => line[column].length)));
  const numeric = new Set([3, 4]);
  const formatted = lines.map((line) => line.map((cell, column) => (numeric.has(column) ? cell.padStart(widths[column]) : cell.padEnd(widths[column]))).join("  ").trimEnd());
  return [title, "", ...formatted].join("\n");
}

/** The first line of [text], at most 200 characters, to say why the SQL API refused. */
function firstLine(text) {
  return text.trim().split("\n")[0].slice(0, 200);
}

/**
 * Reads the counts of the last days that [argv] asks for, with the token and the account id of
 * [environment], through [fetchImpl], and prints them with [print].
 */
export async function main(argv = process.argv.slice(2), environment = process.env, fetchImpl = fetch, print = console.log, now = Date.now()) {
  const days = daysOf(argv[0]);
  const token = (environment.CLOUDFLARE_API_TOKEN ?? "").trim();
  const account = (environment.CLOUDFLARE_ACCOUNT_ID ?? "").trim();
  const lacking = [account === "" ? "CLOUDFLARE_ACCOUNT_ID" : null, token === "" ? "CLOUDFLARE_API_TOKEN" : null].filter((name) => name !== null);
  if (lacking.length > 0) {
    throw new MetricsError(`Set ${lacking.join(" and ")}: the id of the Cloudflare account and an API token that may read its analytics (Account Analytics: Read).`);
  }
  if (!ACCOUNT_ID.test(account)) throw new MetricsError("CLOUDFLARE_ACCOUNT_ID is not an account id: 32 hex digits, as the Cloudflare dashboard shows it.");
  let response;
  try {
    response = await fetchImpl(`https://api.cloudflare.com/client/v4/accounts/${account}/analytics_engine/sql`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "text/plain" },
      body: queryOf(days, now),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (error) {
    throw new MetricsError(`Analytics Engine could not be reached (${error instanceof Error ? error.name : "unknown"}).`);
  }
  const text = await response.text();
  if (!response.ok) throw new MetricsError(`Analytics Engine answered ${response.status}: ${firstLine(text)}`);
  let answer;
  try {
    answer = JSON.parse(text);
  } catch {
    throw new MetricsError("Analytics Engine answered something other than JSON.");
  }
  print(tableOf(rowsOf(answer), days));
}

// Run as a script; imported, it only lends its functions.
if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error instanceof MetricsError ? error.message : error);
    process.exit(1);
  });
}
