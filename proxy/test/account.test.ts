import { runInDurableObject } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { decodeJwt, errors, exportJWK, generateKeyPair, SignJWT } from "jose";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { hashed } from "../src/ledger";
import { POLICY } from "../src/policy";
import { nextMonthStart, periodOf } from "../src/quota-book";
import { issueSession, verdictOf } from "../src/session";
import { network } from "./network";
import { ANSWER, billRequest, last, model, proxyRequest, send } from "./requests";

const CLIENT_ID = "test-web-client.apps.googleusercontent.com";
const GOOGLE_SUB = "109876543210987654321";
const google = await generateKeyPair("RS256");
const stranger = await generateKeyPair("RS256");
const googleKeys = { keys: [{ ...(await exportJWK(google.publicKey)), kid: "google-test", alg: "RS256", use: "sig" }] };

beforeEach(() => {
  network.use(http.get("https://www.googleapis.com/oauth2/v3/certs", () => HttpResponse.json(googleKeys)));
});

type IdTokenChange = { key?: CryptoKey; issuer?: string; audience?: string; expires?: number | string };

/** An ID token as Google signs one for the app at sign-in, with [change] made to it. */
function idToken(change: IdTokenChange = {}): Promise<string> {
  return new SignJWT({ email: "person@example.com", email_verified: true })
    .setProtectedHeader({ alg: "RS256", kid: "google-test" })
    .setIssuer(change.issuer ?? "https://accounts.google.com")
    .setAudience(change.audience ?? CLIENT_ID)
    .setSubject(GOOGLE_SUB)
    .setIssuedAt()
    .setExpirationTime(change.expires ?? "1h")
    .sign(change.key ?? google.privateKey);
}

function signIn(token: string, settings: Partial<typeof env> = {}) {
  return send(proxyRequest("POST", "/v1/session", {}, JSON.stringify({ idToken: token })), settings);
}

async function accountSession(now: number = Date.now(), settings: Partial<typeof env> = {}) {
  const account = await hashed(crypto.randomUUID(), env.HASH_SALT);
  const { session } = await issueSession(account, { ...env, ...settings }, now);
  return { account, session };
}

function monthly(used: number) {
  const now = Date.now();
  return { limit: POLICY.monthlyCalls, used, remaining: POLICY.monthlyCalls - used, period: periodOf({ kind: "monthly", limit: 10 }, now), resetsAt: nextMonthStart(now) };
}

describe("signing in", () => {
  it("trades a Google ID token for a session that reads bills on the account's monthly calls", async () => {
    const signedIn = await signIn(await idToken());

    expect(signedIn.status).toBe(200);
    const { session, expiresAt, usage } = JSON.parse(signedIn.body) as { session: string; expiresAt: number; usage: unknown };
    expect(usage).toEqual(monthly(0));
    expect(expiresAt).toBeGreaterThan(Date.now() + POLICY.sessionMs - 60_000);

    model(ANSWER);
    const read = await send(billRequest({ session }));
    expect(last(read)).toMatchObject({ type: "result", usage: monthly(1) });
  });

  it("names the account by a salted hash of its Google id, never by the id itself", async () => {
    const { session } = JSON.parse((await signIn(await idToken())).body) as { session: string };

    const claims = decodeJwt(session);
    expect(claims.sub).toBe(await hashed(GOOGLE_SUB, env.HASH_SALT));
    expect(session).not.toContain(GOOGLE_SUB);
    expect(JSON.stringify(claims)).not.toContain("person@example.com");
  });

  it("refuses an ID token Google did not sign, that has run out, or that is meant for another app or comes from elsewhere", async () => {
    const tokens = [
      await idToken({ key: stranger.privateKey }),
      await idToken({ expires: Math.floor(Date.now() / 1000) - 60 }),
      await idToken({ audience: "another-app.apps.googleusercontent.com" }),
      await idToken({ issuer: "https://accounts.example.com" }),
      "not.a.token",
    ];

    for (const token of tokens) {
      const sent = await signIn(token);
      expect(sent.status).toBe(401);
      expect(JSON.parse(sent.body)).toEqual({ error: "id_token" });
    }
  });

  it("tells a bad token from Google's keys being out of reach", () => {
    for (const fault of [
      new errors.JWTExpired("expired", {}),
      new errors.JWTClaimValidationFailed("wrong audience", {}),
      new errors.JWSSignatureVerificationFailed(),
      new errors.JWSInvalid(),
      new errors.JWKSNoMatchingKey(),
    ]) {
      expect(verdictOf(fault)).toBe("invalid");
    }
    for (const outage of [new errors.JWKSTimeout(), new errors.JOSEError("Expected 200 OK from the JSON Web Key Set HTTP response"), new TypeError("fetch failed")]) {
      expect(verdictOf(outage)).toBe("unavailable");
    }
  });

  it("refuses a body without an ID token", async () => {
    for (const body of ["{}", '{"idToken": 42}', "not json"]) {
      const sent = await send(proxyRequest("POST", "/v1/session", {}, body));
      expect(sent.status).toBe(400);
    }
  });

  it("stays off until the owner sets the app's client ID and a long enough signing key", async () => {
    const token = await idToken();

    const offs: Array<Partial<typeof env>> = [{ GOOGLE_CLIENT_ID: "" }, { SESSION_SIGNING_KEY: "too short" }];
    for (const settings of offs) {
      const sent = await signIn(token, settings);
      expect(sent.status).toBe(503);
      expect(JSON.parse(sent.body)).toEqual({ error: "sign_in_off" });
    }
  });
});

describe("a session", () => {
  it("is refused when it was changed, signed with another key, or has run out", async () => {
    const { session } = await accountSession();
    const [head, payload, signature] = session.split(".");
    const changed = `${head}.${payload.slice(0, -2)}${payload.endsWith("A") ? "B" : "A"}${payload.slice(-1)}.${signature}`;
    const otherKey = (await accountSession(Date.now(), { SESSION_SIGNING_KEY: "another-signing-key-that-is-long-enough" })).session;
    const expired = (await accountSession(Date.now() - POLICY.sessionMs - 60_000)).session;
    const seen = model(ANSWER);

    for (const bad of [changed, otherKey, expired, "not-a-session"]) {
      for (const request of [billRequest({ session: bad }), proxyRequest("GET", "/v1/usage", { session: bad })]) {
        const sent = await send(request);
        expect(sent.status).toBe(401);
        expect(JSON.parse(sent.body)).toEqual({ error: "session" });
      }
    }
    expect(seen).toHaveLength(0);
  });

  it("gives an account ten calls a month and refuses the eleventh until the next month begins", async () => {
    const { session } = await accountSession();
    model(ANSWER);
    for (let i = 0; i < POLICY.monthlyCalls; i++) {
      expect(last(await send(billRequest({ session, ip: `198.51.100.${i + 1}` })))).toMatchObject({ type: "result" });
    }

    const eleventh = await send(billRequest({ session, ip: "198.51.100.200" }));

    expect(eleventh.status).toBe(429);
    expect(JSON.parse(eleventh.body)).toEqual({ error: "quota_exhausted", usage: monthly(10) });
    const wait = Number(eleventh.headers.get("retry-after"));
    expect(Math.abs(wait - (nextMonthStart(Date.now()) - Date.now()) / 1000)).toBeLessThan(5);
  });
});

describe("the calls left", () => {
  it("are reported for an install and for an account, with whether AI is on", async () => {
    const install = await send(proxyRequest("GET", "/v1/usage"));
    expect(install.status).toBe(200);
    expect(JSON.parse(install.body)).toEqual({
      usage: { limit: 3, used: 0, remaining: 3, period: "trial", resetsAt: null },
      aiEnabled: true,
    });

    const { session } = await accountSession();
    const account = await send(proxyRequest("GET", "/v1/usage", { session }), { AI_ENABLED: "false" });
    expect(JSON.parse(account.body)).toEqual({ usage: monthly(0), aiEnabled: false });
  });

  it("need an install id or a session", async () => {
    const sent = await send(proxyRequest("GET", "/v1/usage", { install: null }));

    expect(sent.status).toBe(401);
    expect(JSON.parse(sent.body)).toEqual({ error: "install" });
  });
});

describe("deleting an account", () => {
  it("deletes everything the proxy holds about the account", async () => {
    const { account, session } = await accountSession();
    model(ANSWER);
    await send(billRequest({ session }));

    const deleted = await send(proxyRequest("DELETE", "/v1/account", { session }));

    expect(deleted.status).toBe(204);
    for (const stub of [
      env.QUOTA_BOOK.get(env.QUOTA_BOOK.idFromName(`google:${account}`)),
      env.ABUSE_LEDGER.get(env.ABUSE_LEDGER.idFromName(`google:${account}`)),
    ]) {
      const left = await runInDurableObject(stub, async (_, state) => (await state.storage.list()).size);
      expect(left).toBe(0);
    }
  });

  it("needs the account's session", async () => {
    const sent = await send(proxyRequest("DELETE", "/v1/account"));

    expect(sent.status).toBe(401);
    expect(JSON.parse(sent.body)).toEqual({ error: "session" });
  });
});
