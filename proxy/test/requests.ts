import { createExecutionContext, runInDurableObject, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { http } from "msw";
import sampleAnswer from "../../app/src/sharedTest/resources/fixtures/sample_bill_answer.json";
import sampleRequest from "../../app/src/sharedTest/resources/fixtures/sample_bill_request.json";
import { ALLOWANCES } from "../src/identity";
import worker, { type Env } from "../src/index";
import { hashed, ipKey } from "../src/ledger";
import { MESSAGES_URL, type Reply, streamed } from "./fake-model";
import { network } from "./network";

export { sampleAnswer, sampleRequest };

export const IP = "203.0.113.7";
export const ANSWER: Reply = { text: JSON.stringify(sampleAnswer) };

export type Sent = { status: number; headers: Headers; body: string; lines: Array<Record<string, unknown>> };
/** A request the model got: its JSON body and its headers. */
export type Seen = { body: Record<string, unknown>; headers: Record<string, string> };

/** The worker as the runtime calls it. A test request has no edge properties, which the handler does not need. */
export function fetchWorker(request: Request, ctx: ExecutionContext, settings: Partial<Env> = {}): Promise<Response> {
  return worker.fetch(request as Parameters<typeof worker.fetch>[0], { ...env, ...settings }, ctx);
}

/** Sends [request] and reads the whole reply, after the work it left running has finished. */
export async function send(request: Request, settings: Partial<Env> = {}): Promise<Sent> {
  const ctx = createExecutionContext();
  const response = await fetchWorker(request, ctx, settings);
  const body = new TextDecoder().decode(await response.arrayBuffer());
  await waitOnExecutionContext(ctx);
  const lines = response.headers.get("content-type")?.startsWith("application/x-ndjson")
    ? body.trim().split("\n").map((line) => JSON.parse(line) as Record<string, unknown>)
    : [];
  return { status: response.status, headers: response.headers, body, lines };
}

type Caller = { install?: string | null; session?: string; ip?: string; version?: string | null; cf?: object };

/** A request to the proxy, from an app at version 1 and a phone with its own install id unless [caller] says otherwise. */
export function proxyRequest(method: string, path: string, caller: Caller = {}, body?: string): Request {
  const headers = new Headers({ "content-type": "application/json" });
  if (caller.ip !== "") headers.set("CF-Connecting-IP", caller.ip ?? IP);
  if (caller.install !== null) headers.set("X-SplitVibe-Install", caller.install ?? crypto.randomUUID());
  if (caller.version !== null) headers.set("X-SplitVibe-Version", caller.version ?? "1");
  if (caller.session !== undefined) headers.set("Authorization", `Bearer ${caller.session}`);
  return new Request(`https://proxy.test${path}`, { method, headers, body, cf: caller.cf } as RequestInit);
}

/** A bill read of the sample bill with a fresh request id, unless [options] gives another body. */
export function billRequest(options: Caller & { body?: unknown; raw?: string } = {}): Request {
  const body = options.raw ?? JSON.stringify(options.body ?? { ...sampleRequest, requestId: crypto.randomUUID() });
  return proxyRequest("POST", "/v1/bill-analysis", options, body);
}

/** The model answers every request with [reply]; the returned list grows by one for each request it gets. */
export function model(reply: Reply | ((request: Request) => Response | Promise<Response>)): Seen[] {
  const seen: Seen[] = [];
  network.use(
    http.post(MESSAGES_URL, async ({ request }) => {
      seen.push({ body: (await request.clone().json()) as Record<string, unknown>, headers: Object.fromEntries(request.headers) });
      return typeof reply === "function" ? reply(request as unknown as Request) : streamed(reply);
    }),
  );
  return seen;
}

export function last(sent: Sent): Record<string, unknown> {
  return sent.lines[sent.lines.length - 1];
}

/** The calls left of an install, or of an account when [name] starts with `google:`. */
export function quotaOf(name: string) {
  const caller = name.includes(":") ? name : `install:${name}`;
  const allowance = caller.startsWith("google:") ? ALLOWANCES.monthly : ALLOWANCES.trial;
  return env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(caller)).usage(Date.now(), allowance);
}

export async function spendOf(name: string): Promise<{ spent: number; held: number } | undefined> {
  const stub = env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(name));
  return runInDurableObject(stub, (_, state) => state.storage.get<{ spent: number; held: number }>("spend"));
}

export async function ipName(ip: string): Promise<string> {
  return `ip:${await hashed(ipKey(ip), env.HASH_SALT)}`;
}
