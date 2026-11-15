import { importPKCS8, SignJWT } from "jose";
import type { Env } from "./index";
import { isRecord, readJson } from "./input";
import { errorName } from "./spend";

/**
 * A push notification. It names only what it is about, by id: the app reads the rest with the
 * person's own session once it opens.
 */
export type Push =
  | { type: "request" | "reported" | "confirmed" | "reminder" | "unpaid" | "unconfirmed"; requestId: string }
  | { type: "claim"; groupId: string };

/**
 * The fixed English text of each notification. None holds anything a person typed (no name,
 * merchant, note or link), so a notification shows nothing a lock screen should not.
 */
export const PUSH_TEXT: Record<Push["type"], { title: string; body: string }> = {
  request: { title: "New payment request", body: "Someone asked you to pay them back. Open Split Vibe to see the request." },
  reported: { title: "Payment reported", body: "A payment you asked for was reported as paid. Open Split Vibe to confirm it." },
  confirmed: { title: "Payment confirmed", body: "A payment you reported was confirmed. Open Split Vibe to see it." },
  reminder: { title: "Payment reminder", body: "A payment request is still waiting for you. Open Split Vibe to see it." },
  unpaid: { title: "Payment still open", body: "Someone has not paid you back yet. Open Split Vibe to send them a reminder." },
  unconfirmed: { title: "Payment to confirm", body: "A payment was reported to you. Open Split Vibe to confirm it once it arrived." },
  claim: { title: "Someone asked to join", body: "A person asked to take a place in one of your groups. Open Split Vibe to approve or reject it." },
};

/** An install of the app that can be notified. */
export type Device = { id: string; account: string; token: string };

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
/** An access token is fetched again this long before Google says it expires. */
const TOKEN_MARGIN_MS = 5 * 60 * 1000;
/** How long one call to Google may take. */
const TIMEOUT_MS = 10_000;
/** Far more than any answer of the token endpoint or of FCM needs. */
const MAX_ANSWER_BYTES = 64 * 1024;

type ServiceAccount = { projectId: string; clientEmail: string; privateKey: string };

/** The access token the isolate holds, for the service account named by [email]. */
let grant: { email: string; token: string; expiresAt: number } | null = null;
/** The fetch of an access token under way, which pushes that need one meanwhile share. */
let granting: { email: string; token: Promise<string | null> } | null = null;
/** The private key, imported once per isolate: importing an RSA key costs CPU time. */
let imported: { pem: string; key: Promise<CryptoKey> } | null = null;

/**
 * The service account in the secret `FCM_SERVICE_ACCOUNT`: the JSON key file Google hands out,
 * of which the project id, the client email and the private key are used. Null when the secret
 * is missing, which turns push off, or when it is not such a file, which also does and is logged.
 */
function serviceAccountOf(env: Env): ServiceAccount | null {
  const secret = env.FCM_SERVICE_ACCOUNT?.trim() ?? "";
  if (secret === "") return null;
  let parsed: unknown = null;
  try {
    parsed = JSON.parse(secret);
  } catch {
    // Reported below with every other fault of the secret.
  }
  const fields = isRecord(parsed) ? parsed : {};
  const { project_id: projectId, client_email: clientEmail, private_key: privateKey } = fields;
  if (typeof projectId !== "string" || projectId === "" || typeof clientEmail !== "string" || clientEmail === "" || typeof privateKey !== "string") {
    console.error(JSON.stringify({ event: "push_not_configured", reason: "service_account" }));
    return null;
  }
  return { projectId, clientEmail, privateKey };
}

/** Whether notifications can go out at all. */
export function pushReady(env: Env): boolean {
  return serviceAccountOf(env) !== null;
}

/** The devices of [accounts], the most recently registered first for each account. */
export async function devicesOf(db: D1Database, accounts: string[]): Promise<Device[]> {
  if (accounts.length === 0) return [];
  const { results } = await db
    .prepare("SELECT id, account, token FROM devices WHERE account IN (SELECT value FROM json_each(?1)) ORDER BY account, updated_at DESC, id")
    .bind(JSON.stringify(accounts))
    .all<Device>();
  return results;
}

/**
 * Sends [push] to every device of [account], when there is one and push is set up. It never
 * throws: a notification that cannot go out must not undo or fail what it tells of.
 */
export async function notify(env: Env, account: string | null, push: Push): Promise<void> {
  if (account === null || !pushReady(env)) return;
  try {
    const devices = await devicesOf(env.DB, [account]);
    if (devices.length === 0) return;
    await (await pushSender(env))?.send(devices, push);
  } catch (error) {
    console.error(JSON.stringify({ event: "push_failed", type: push.type, error: errorName(error) }));
  }
}

/** What sends notifications with one access token, as a whole run of the daily cron does. */
export type PushSender = {
  /** Sends [push] to [devices], and forgets each device whose token FCM says is no longer registered. It never throws. */
  send(devices: Device[], push: Push): Promise<void>;
};

/**
 * A sender with an access token for FCM, or null when push is not set up or Google gives no
 * token just now; that is logged, and nothing can be sent.
 */
export async function pushSender(env: Env): Promise<PushSender | null> {
  const account = serviceAccountOf(env);
  if (account === null) return null;
  const token = await accessToken(account, Date.now());
  if (token === null) return null;
  return {
    async send(devices, push) {
      if (devices.length === 0) return;
      try {
        const outcomes = await Promise.all(devices.map((device) => sendOne(env.DB, account, token, device, push)));
        const count = (outcome: Outcome) => outcomes.filter((each) => each === outcome).length;
        const outcome = { sent: count("sent"), removed: count("removed"), failed: count("failed") };
        console.log(JSON.stringify({ event: "push", type: push.type, devices: devices.length, ...outcome }));
      } catch (error) {
        console.error(JSON.stringify({ event: "push_failed", type: push.type, error: errorName(error) }));
      }
    },
  };
}

type Outcome = "sent" | "removed" | "failed";

async function sendOne(db: D1Database, account: ServiceAccount, token: string, device: Device, push: Push): Promise<Outcome> {
  try {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.projectId)}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ message: messageOf(device.token, push) }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (response.ok) {
      await response.body?.cancel();
      return "sent";
    }
    // The token was refused before its time; the next notification fetches another.
    if (response.status === 401) grant = null;
    if (!(await unregistered(response))) return "failed";
    // Only the row still holding that token: the app may have registered a new one meanwhile.
    await db.prepare("DELETE FROM devices WHERE id = ?1 AND token = ?2").bind(device.id, device.token).run();
    return "removed";
  } catch {
    return "failed";
  }
}

/**
 * The message FCM sends to one device: the fixed text of [push], and data that names only what it
 * is about. While the app is in the background Android shows it itself, in the app's own channel
 * for requests and groups (`Notices.CHANNEL` in the app).
 */
function messageOf(token: string, push: Push) {
  const data: Record<string, string> = push.type === "claim" ? { type: push.type, groupId: push.groupId } : { type: push.type, requestId: push.requestId };
  return { token, notification: PUSH_TEXT[push.type], data, android: { notification: { channel_id: ANDROID_CHANNEL } } };
}

/** The app's notification channel for requests and groups. */
const ANDROID_CHANNEL = "requests";

/**
 * Whether FCM refused a message because its device token is dead: 404 with the error code
 * `UNREGISTERED` (the app was uninstalled or the token rotated), or 400 `INVALID_ARGUMENT` about
 * the registration token. A 400 about anything else, or a 404 without that code (such as a wrong
 * project), says nothing about the device, which is kept.
 */
async function unregistered(response: Response): Promise<boolean> {
  const answer = await readJson(response, MAX_ANSWER_BYTES);
  const error = isRecord(answer) && isRecord(answer.error) ? answer.error : {};
  const details = Array.isArray(error.details) ? error.details.filter(isRecord) : [];
  const codes = details.map((detail) => detail.errorCode);
  if (response.status === 404) return codes.includes("UNREGISTERED");
  if (response.status !== 400 || !codes.includes("INVALID_ARGUMENT")) return false;
  const aboutToken = details
    .flatMap((detail) => (Array.isArray(detail.fieldViolations) ? detail.fieldViolations : []))
    .some((violation) => isRecord(violation) && violation.field === "message.token");
  return aboutToken || (typeof error.message === "string" && /registration token/i.test(error.message));
}

/**
 * An OAuth2 access token for FCM, held until shortly before it expires. Pushes that need one
 * while it is being fetched wait for that fetch rather than starting their own. Null when Google
 * would not give one; that is logged.
 */
function accessToken(account: ServiceAccount, now: number): Promise<string | null> {
  if (grant !== null && grant.email === account.clientEmail && now < grant.expiresAt) return Promise.resolve(grant.token);
  if (granting === null || granting.email !== account.clientEmail) {
    const token: Promise<string | null> = fetchAccessToken(account, now).finally(() => {
      if (granting?.token === token) granting = null;
    });
    granting = { email: account.clientEmail, token };
  }
  return granting.token;
}

/** Trades a JWT that the service account signs (RS256) for an access token, as Google's JWT bearer grant has it. */
async function fetchAccessToken(account: ServiceAccount, now: number): Promise<string | null> {
  try {
    const issuedAt = Math.floor(now / 1000);
    const assertion = await new SignJWT({ scope: SCOPE })
      .setProtectedHeader({ alg: "RS256", typ: "JWT" })
      .setIssuer(account.clientEmail)
      .setAudience(TOKEN_URL)
      .setIssuedAt(issuedAt)
      .setExpirationTime(issuedAt + 3600)
      .sign(await privateKey(account.privateKey));
    const response = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    const answer = await readJson(response, MAX_ANSWER_BYTES);
    const accessToken = isRecord(answer) ? answer.access_token : undefined;
    const expiresIn = isRecord(answer) ? answer.expires_in : undefined;
    if (!response.ok || typeof accessToken !== "string" || accessToken === "" || typeof expiresIn !== "number") {
      console.error(JSON.stringify({ event: "push_token_failed", status: response.status }));
      return null;
    }
    grant = { email: account.clientEmail, token: accessToken, expiresAt: now + expiresIn * 1000 - TOKEN_MARGIN_MS };
    return accessToken;
  } catch (error) {
    console.error(JSON.stringify({ event: "push_token_failed", error: errorName(error) }));
    return null;
  }
}

function privateKey(pem: string): Promise<CryptoKey> {
  if (imported === null || imported.pem !== pem) imported = { pem, key: importPKCS8(pem, "RS256") };
  return imported.key;
}
