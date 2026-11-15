import type { Env } from "./index";
import { inputOf, textOf } from "./input";
import { bodyTokenOf, randomToken, tokenHash, tokenOf } from "./links";
import { POLICY } from "./policy";
import { addressOf, hourlyRefusal } from "./rate-limit";
import { errorResponse, jsonResponse } from "./respond";
import { LIVE_PROTOCOL } from "./share-room";
import {
  billOf,
  type ClaimRow,
  guardStatement,
  itemsInBill,
  linkedShare,
  MAX_ITEMS,
  publicView,
  type Share,
  SHARE_WRITE_ATTEMPTS,
  shareRoom,
  tell,
  writeShare,
} from "./share-state";
import { turnstilePassed, turnstileReady, turnstileTokenOf } from "./turnstile";

// The routes a bill link's web page calls. They need no session: the link's token, in the body or
// in the live connection's subprotocols, is all they take, and they never change the bill. Each
// counts against the hourly limits of the caller's IP only once the token led to a link that
// works, so that a token that leads nowhere, and a request past a limit, writes nothing.

/** The claim of the browser that holds the key whose hash is [keyHash], if the link has one. */
function claimOf(share: Share, keyHash: string | null): ClaimRow | undefined {
  return keyHash === null ? undefined : share.claims.find((claim) => claim.key_hash === keyHash);
}

/**
 * `POST /v1/shares/open` with `{"token","key"?}`: the link as anyone with its token sees it, and
 * `you`, the id of the claim whose key the browser sent, or null. Counts as a read of the IP.
 */
export async function openShareRoute(request: Request, env: Env): Promise<Response> {
  const input = await inputOf(request, POLICY.maxSharedBodyBytes, (body) => {
    const token = bodyTokenOf(body);
    // A key no claim could have is one the link does not know.
    return token === null ? null : { token: token.token, key: tokenOf(body.key) };
  });
  if (!input.ok) return input.response;
  const address = await addressOf(request, env);
  if (!address.ok) return address.response;
  const found = await linkedShare(env.DB, input.value.token, Date.now());
  if (!found.ok) return found.response;
  const refused = await hourlyRefusal(env.DB, address.name, "web_read", POLICY.webReadsPerHour);
  if (refused !== null) return refused;
  const key = input.value.key;
  const mine = claimOf(found.share, key === null ? null : await tokenHash(key));
  return jsonResponse(200, { share: publicView(found.share), you: mine?.id ?? null });
}

type Claiming = { token: string | null; key: string | null; name: string; itemIds: string[]; turnstile: string | null };

/** A claim's body: `name` of 1 to [POLICY.maxClaimNameChars] characters, and `itemIds` with no id twice. */
function claimingOf(body: Record<string, unknown>): Claiming | null {
  const token = bodyTokenOf(body);
  const name = textOf(body.name, 1, POLICY.maxClaimNameChars);
  const itemIds = body.itemIds;
  if (token === null || name === null || !Array.isArray(itemIds) || itemIds.length > MAX_ITEMS) return null;
  if (!itemIds.every((id): id is string => typeof id === "string") || new Set(itemIds).size !== itemIds.length) return null;
  return { token: token.token, key: tokenOf(body.key), name, itemIds, turnstile: turnstileTokenOf(body.turnstile) };
}

/** The answer to a claim: the link as it is now, the claim's id, and its key when the claim is new. */
function claimed(share: Share, claimId: string, key?: string): Response {
  return jsonResponse(200, { share: publicView(share), you: claimId, ...(key === undefined ? {} : { key }) });
}

/**
 * Whether a new claim may be made: the answer to refuse it with, or null. Without a secret
 * `TURNSTILE_SECRET` that can be used on this host, 503 `not_configured`; without a Turnstile
 * token, or with one Cloudflare refuses, 403 `turnstile`. The IP's new claims are counted before
 * Cloudflare is asked, so that nobody can make the proxy ask more than [POLICY.newClaimsPerHour]
 * times an hour (429 past it).
 */
async function newClaimRefusal(request: Request, env: Env, address: string, turnstile: string | null): Promise<Response | null> {
  const hostname = new URL(request.url).hostname;
  if (!turnstileReady(env, hostname)) return errorResponse(503, "not_configured");
  if (turnstile === null) return errorResponse(403, "turnstile");
  const refused = await hourlyRefusal(env.DB, address, "claim_new", POLICY.newClaimsPerHour);
  if (refused !== null) return refused;
  const passed = await turnstilePassed(env, turnstile, hostname, request.headers.get("CF-Connecting-IP"));
  return passed ? null : errorResponse(403, "turnstile");
}

/**
 * `POST /v1/shares/claim` with `{"token","key"?,"name","itemIds","turnstile"?}`: the items one
 * browser takes on an open link, under the name its user typed.
 *
 * With the key of one of the link's claims, that claim's name and items are replaced. Otherwise
 * it is a new claim, which needs a Turnstile token (see [newClaimRefusal]); its answer carries the
 * key the browser keeps from then on, of which only the hash is stored. `itemIds` must be items
 * of the bill (400), a link holds [POLICY.maxClaims] claims (409 `full`), and takes them only while
 * open (409 `locked`). A claim lands only while all of that still holds, in the same statement, so
 * one written before a lock is in the lock's answer and one after it is refused. Every claim on a
 * link that works counts against the IP's [POLICY.claimWritesPerHour].
 */
export async function claimRoute(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const input = await inputOf(request, POLICY.maxClaimBodyBytes, claimingOf);
  if (!input.ok) return input.response;
  const wanted = input.value;
  const address = await addressOf(request, env);
  if (!address.ok) return address.response;
  const db = env.DB;
  const keyHash = wanted.key === null ? null : await tokenHash(wanted.key);
  const itemIds = JSON.stringify(wanted.itemIds);
  let counted = false;
  // A Turnstile token is good for one check: once it passed, a claim that has to be decided again does not ask again.
  let passed = false;
  for (let round = 1; round <= SHARE_WRITE_ATTEMPTS; round++) {
    const now = Date.now();
    const found = await linkedShare(db, wanted.token, now);
    if (!found.ok) return found.response;
    if (!counted) {
      const refused = await hourlyRefusal(db, address.name, "claim_write", POLICY.claimWritesPerHour);
      if (refused !== null) return refused;
      counted = true;
    }
    const share = found.share;
    if (share.row.status !== "open") return errorResponse(409, "locked");
    const items = new Set(billOf(share.row).items.map((item) => item.id));
    if (!wanted.itemIds.every((id) => items.has(id))) return errorResponse(400, "input");
    const shareId = share.row.id;
    const mine = claimOf(share, keyHash);
    if (mine !== undefined) {
      if (mine.name === wanted.name && mine.item_ids === itemIds) return claimed(share, mine.id);
      const written = await writeShare(db, shareId, [
        guardStatement(db, shareId, now, `status = 'open' AND EXISTS (SELECT 1 FROM share_claims WHERE share_id = ?1 AND key_hash = ?3) AND ${itemsInBill("?4")}`, "", [
          keyHash,
          itemIds,
        ]),
        db.prepare("UPDATE share_claims SET name = ?3, item_ids = ?4, updated_at = ?5 WHERE share_id = ?1 AND key_hash = ?2").bind(shareId, keyHash, wanted.name, itemIds, now),
      ]);
      if (written === null) continue;
      tell(env, ctx, written.row, { type: "share", share: publicView(written) });
      return claimed(written, mine.id);
    }
    if (share.claims.length >= POLICY.maxClaims) return errorResponse(409, "full");
    if (!passed) {
      const notNow = await newClaimRefusal(request, env, address.name, wanted.turnstile);
      if (notNow !== null) return notNow;
      passed = true;
    }
    const key = randomToken();
    const claimId = crypto.randomUUID();
    const written = await writeShare(db, shareId, [
      guardStatement(db, shareId, now, `status = 'open' AND (SELECT count(*) FROM share_claims WHERE share_id = ?1) < ?3 AND ${itemsInBill("?4")}`, "", [
        POLICY.maxClaims,
        itemIds,
      ]),
      db
        .prepare("INSERT INTO share_claims (share_id, id, key_hash, name, item_ids, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6)")
        .bind(shareId, claimId, await tokenHash(key), wanted.name, itemIds, now),
    ]);
    if (written === null) continue;
    tell(env, ctx, written.row, { type: "share", share: publicView(written) });
    return claimed(written, claimId, key);
  }
  console.error(JSON.stringify({ event: "share_claim_contended" }));
  return errorResponse(409, "conflict");
}

/**
 * `GET /v1/shares/live`, a WebSocket offering the subprotocols `splitvibe.v1` and the link's
 * token: the token travels in that header because traces keep URLs and not header values. The
 * connection is answered with `splitvibe.v1` alone, and hears `{"type":"share","share"}` after
 * every change of the link and `{"type":"gone"}` once it is revoked; see [ShareRoom]. Without an
 * upgrade to a WebSocket, or without those two protocols, 400; 404 and 410 as for `open`; 429 past
 * the IP's reads, or when the link has [POLICY.maxLiveConnections] open.
 */
export async function liveShareRoute(request: Request, env: Env): Promise<Response> {
  if (request.headers.get("upgrade")?.toLowerCase() !== "websocket") return errorResponse(400, "input");
  const offered = (request.headers.get("sec-websocket-protocol") ?? "")
    .split(",")
    .map((protocol) => protocol.trim())
    .filter((protocol) => protocol !== "");
  const others = offered.filter((protocol) => protocol !== LIVE_PROTOCOL);
  if (others.length !== 1 || offered.length !== 2) return errorResponse(400, "input");
  const address = await addressOf(request, env);
  if (!address.ok) return address.response;
  const found = await linkedShare(env.DB, tokenOf(others[0]), Date.now());
  if (!found.ok) return found.response;
  const refused = await hourlyRefusal(env.DB, address.name, "web_read", POLICY.webReadsPerHour);
  if (refused !== null) return refused;
  // The object gets nothing of the request, the token least of all: only the upgrade.
  return shareRoom(env, found.share.row).fetch(new Request("https://share-room/connect", { headers: { upgrade: "websocket" } }));
}
