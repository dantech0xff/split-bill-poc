import {
  type ClaimAnswer,
  type InvitePreview,
  type OpenAnswer,
  type RequestView,
  parseClaimAnswer,
  parseInviteAnswer,
  parseOpenAnswer,
  parseRequestAnswer,
} from "./contract";

/** Why a call to the proxy did not work, as the page needs to tell it. */
export type ApiError =
  | { kind: "not_found" }
  | { kind: "gone" }
  | { kind: "rate_limited"; retryAfterSeconds: number | null }
  | { kind: "turnstile" }
  | { kind: "full" }
  | { kind: "locked" }
  | { kind: "not_configured" }
  | { kind: "input" }
  | { kind: "network" }
  | { kind: "unexpected" };

export type ApiResult<T> = { ok: true; value: T } | { ok: false; error: ApiError };

export type Fetch = (input: string, init: RequestInit) => Promise<Response>;

/** The seconds of a `Retry-After` header, or null when it holds none. */
export function retryAfterSeconds(header: string | null): number | null {
  if (header === null || !/^\s*\d+\s*$/.test(header)) return null;
  const seconds = Number.parseInt(header, 10);
  return Math.min(Math.max(seconds, 1), 24 * 60 * 60);
}

async function errorCodeOf(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "error" in body && typeof body.error === "string") return body.error;
  } catch {
    // A body that is not JSON names no code.
  }
  return null;
}

async function errorOf(response: Response): Promise<ApiError> {
  const code = await errorCodeOf(response);
  switch (response.status) {
    case 400:
      return { kind: "input" };
    case 403:
      return code === "turnstile" ? { kind: "turnstile" } : { kind: "unexpected" };
    case 404:
      return { kind: "not_found" };
    case 409:
      if (code === "full") return { kind: "full" };
      if (code === "locked") return { kind: "locked" };
      return { kind: "unexpected" };
    case 410:
      return { kind: "gone" };
    case 429:
      return { kind: "rate_limited", retryAfterSeconds: retryAfterSeconds(response.headers.get("retry-after")) };
    case 503:
      return code === "not_configured" ? { kind: "not_configured" } : { kind: "unexpected" };
    default:
      return { kind: "unexpected" };
  }
}

/**
 * POSTs [body] as JSON to [path] on the page's own origin, and parses a successful answer with
 * [parse]. Every failure comes back as an [ApiError], never as an exception.
 */
export async function postJson<T>(
  path: string,
  body: Record<string, unknown>,
  parse: (json: unknown) => T | null,
  fetchImpl: Fetch = (input, init) => fetch(input, init),
): Promise<ApiResult<T>> {
  let response: Response;
  try {
    response = await fetchImpl(path, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
      cache: "no-store",
      credentials: "same-origin",
      referrerPolicy: "no-referrer",
    });
  } catch {
    return { ok: false, error: { kind: "network" } };
  }
  if (!response.ok) return { ok: false, error: await errorOf(response) };
  let json: unknown;
  try {
    json = await response.json();
  } catch {
    return { ok: false, error: { kind: "unexpected" } };
  }
  const value = parse(json);
  return value === null ? { ok: false, error: { kind: "unexpected" } } : { ok: true, value };
}

export function openShare(token: string, key: string | null, fetchImpl?: Fetch): Promise<ApiResult<OpenAnswer>> {
  return postJson("/v1/shares/open", key === null ? { token } : { token, key }, parseOpenAnswer, fetchImpl);
}

export interface ClaimBody {
  token: string;
  key: string | null;
  name: string;
  itemIds: readonly string[];
  turnstile: string | null;
}

export function claimShare(claim: ClaimBody, fetchImpl?: Fetch): Promise<ApiResult<ClaimAnswer>> {
  const body: Record<string, unknown> = { token: claim.token, name: claim.name, itemIds: [...claim.itemIds] };
  if (claim.key !== null) body.key = claim.key;
  if (claim.turnstile !== null) body.turnstile = claim.turnstile;
  return postJson("/v1/shares/claim", body, parseClaimAnswer, fetchImpl);
}

export function viewRequest(token: string, fetchImpl?: Fetch): Promise<ApiResult<RequestView>> {
  return postJson("/v1/request-links/view", { token }, parseRequestAnswer, fetchImpl);
}

export function previewInvite(token: string, fetchImpl?: Fetch): Promise<ApiResult<InvitePreview>> {
  return postJson("/v1/invites/preview", { token }, parseInviteAnswer, fetchImpl);
}
