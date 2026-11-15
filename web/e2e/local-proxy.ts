import { createHash, createHmac, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { type APIRequestContext, type APIResponse, type Page, expect } from "@playwright/test";

const DEV_VARS = fileURLToPath(new URL("../../proxy/.dev.vars", import.meta.url));

/** The local secrets `wrangler dev` reads from proxy/.dev.vars; nothing here prints them. */
function devVars(): Record<string, string> {
  const vars: Record<string, string> = {};
  for (const line of readFileSync(DEV_VARS, "utf8").split("\n")) {
    const at = line.indexOf("=");
    if (at > 0 && !line.trimStart().startsWith("#")) vars[line.slice(0, at).trim()] = line.slice(at + 1).trim();
  }
  return vars;
}

function base64url(value: string): string {
  return Buffer.from(value).toString("base64url");
}

/**
 * A session for the account a Google sign-in with [googleSub] would get, signed the way
 * `proxy/src/session.ts` signs it: HS256 under `SESSION_SIGNING_KEY`, for the account named by
 * the SHA-256 of `HASH_SALT`, a NUL and the `sub`, as `hashed` in `proxy/src/ledger.ts` names it.
 */
export function sessionFor(googleSub: string): string {
  const vars = devVars();
  const salt = vars.HASH_SALT;
  const key = vars.SESSION_SIGNING_KEY;
  if (salt === undefined || key === undefined) throw new Error("proxy/.dev.vars lacks HASH_SALT or SESSION_SIGNING_KEY");
  const account = createHash("sha256").update(`${salt}\u0000${googleSub}`).digest("hex");
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "HS256" }));
  const payload = base64url(JSON.stringify({ sub: account, iss: "split-vibe-proxy", aud: "split-vibe-app", iat: now, exp: now + 3600 }));
  const signature = createHmac("sha256", key).update(`${header}.${payload}`).digest("base64url");
  return `${header}.${payload}.${signature}`;
}

/** The token in the fragment of a link the proxy handed out, such as `https://<host>/s/#<token>`. */
export function tokenOf(url: string): string {
  const token = new URL(url).hash.slice(1);
  expect(token).toMatch(/^[A-Za-z0-9_-]{22}$/);
  return token;
}

async function json(response: APIResponse, status: number): Promise<Record<string, unknown>> {
  expect(response.status(), await response.text()).toBe(status);
  return status === 204 ? {} : ((await response.json()) as Record<string, unknown>);
}

/** A signed-in account calling the proxy's routes as its app would. */
export class Account {
  private readonly headers: Record<string, string>;

  constructor(
    private readonly request: APIRequestContext,
    googleSub: string,
  ) {
    this.headers = { authorization: `Bearer ${sessionFor(googleSub)}` };
  }

  async makeShare(bill: unknown): Promise<{ id: string; token: string; billId: string }> {
    const id = randomUUID();
    const billId = randomUUID();
    const answer = await json(await this.request.put(`/v1/shares/${id}`, { headers: this.headers, data: { billId, billRevision: 1, bill } }), 201);
    return { id, billId, token: tokenOf(String(answer.url)) };
  }

  async lockShare(id: string): Promise<void> {
    const answer = await json(await this.request.post(`/v1/shares/${id}/lock`, { headers: this.headers }), 200);
    expect((answer.share as { status: string }).status).toBe("locking");
  }

  async finalizeShare(id: string, bill: unknown, amounts: unknown): Promise<void> {
    const answer = await json(await this.request.post(`/v1/shares/${id}/finalize`, { headers: this.headers, data: { billRevision: 1, bill, amounts } }), 200);
    expect((answer.share as { status: string }).status).toBe("locked");
  }

  async revokeShare(id: string): Promise<void> {
    await json(await this.request.delete(`/v1/shares/${id}`, { headers: this.headers }), 204);
  }

  async makeRequest(fields: Record<string, unknown>): Promise<{ id: string; token: string }> {
    const id = randomUUID();
    const answer = await json(await this.request.post("/v1/requests", { headers: this.headers, data: { id, ...fields } }), 201);
    return { id, token: tokenOf(String(answer.url)) };
  }

  async deleteRequest(id: string): Promise<void> {
    await json(await this.request.delete(`/v1/requests/${id}`, { headers: this.headers }), 204);
  }

  async makeGroup(name: string, currency: { code: string; fractionDigits: number }): Promise<string> {
    const id = randomUUID();
    await json(await this.request.post("/v1/groups", { headers: this.headers, data: { id, name, currency, memberId: randomUUID(), memberName: "Lan" } }), 201);
    return id;
  }

  async makeInvite(groupId: string): Promise<string> {
    const answer = await json(await this.request.post(`/v1/groups/${groupId}/invites`, { headers: this.headers }), 201);
    return tokenOf(String(answer.url));
  }

  async revokeInvites(groupId: string): Promise<void> {
    await json(await this.request.delete(`/v1/groups/${groupId}/invites`, { headers: this.headers }), 204);
  }
}

/** The headers the contract gives the page, for the proxy at [host]. */
export function expectPageHeaders(headers: Record<string, string>, host: string): void {
  expect(headers["content-type"]).toBe("text/html; charset=utf-8");
  expect(headers["content-security-policy"]).toBe(
    "default-src 'none'; script-src 'self' https://challenges.cloudflare.com; style-src 'self'; img-src 'self' data:; " +
      `connect-src 'self' wss://${host}; frame-src https://challenges.cloudflare.com; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`,
  );
  expect(headers["referrer-policy"]).toBe("no-referrer");
  expect(headers["x-robots-tag"]).toBe("noindex, nofollow");
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["cache-control"]).toBe("no-store");
}

/**
 * Collects every Content-Security-Policy violation on [page]'s own documents: the browser's own
 * console reports, and the `securitypolicyviolation` events each document fires.
 */
export async function watchForCspViolations(page: Page, origin: string): Promise<string[]> {
  const violations: string[] = [];
  await page.addInitScript((own) => {
    if (location.origin !== own) return;
    document.addEventListener("securitypolicyviolation", (event) => {
      console.error(`CSP violation: ${event.violatedDirective} blocked ${event.blockedURI || "inline"}`);
    });
  }, origin);
  page.on("console", (message) => {
    const text = message.text();
    const fromOwnPage = message.location().url === "" || message.location().url.startsWith(origin);
    if (fromOwnPage && /CSP violation|Content Security Policy|Refused to (load|execute|apply|connect|frame)/i.test(text)) violations.push(text);
  });
  return violations;
}
