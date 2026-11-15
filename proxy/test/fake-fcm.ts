import { exportPKCS8, generateKeyPair, jwtVerify } from "jose";
import { http, HttpResponse } from "msw";
import { network } from "./network";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SEND_URL = /^https:\/\/fcm\.googleapis\.com\/v1\/projects\/([^/]+)\/messages:send$/;
const PROJECT = "split-vibe-test";

/** A message as FCM got it. */
export type FcmMessage = {
  token: string;
  notification: { title: string; body: string };
  data: Record<string, string>;
  android: { notification: { channel_id: string } };
};

export type FakeFcm = {
  /** The value of `FCM_SERVICE_ACCOUNT` for this fake: a key file with a service account of its own. */
  serviceAccount: string;
  /** What FCM was asked to send, in order. */
  messages: FcmMessage[];
  /** How many access tokens the token endpoint handed out. */
  tokensIssued: () => number;
};

type FakeOptions = {
  /** FCM's answer for a device token; undefined sends the message. */
  answer?: (deviceToken: string) => Response | undefined;
  /** How long the access tokens last, as the token endpoint says, in seconds. */
  expiresIn?: number;
  /** The token endpoint's answer instead of a token. */
  refuseToken?: () => Response;
};

/**
 * Google's token endpoint and FCM HTTP v1, as fakes. The token endpoint checks the JWT bearer
 * grant: an RS256 assertion signed by the service account's key, issued by its client email, for
 * the token endpoint, with the FCM scope. FCM takes only the tokens it handed out, for its project.
 */
export async function fakeFcm(options: FakeOptions = {}): Promise<FakeFcm> {
  const keys = await generateKeyPair("RS256", { extractable: true });
  const clientEmail = `push-${crypto.randomUUID()}@${PROJECT}.iam.gserviceaccount.com`;
  const serviceAccount = JSON.stringify({
    type: "service_account",
    project_id: PROJECT,
    private_key_id: "test-key",
    private_key: await exportPKCS8(keys.privateKey),
    client_email: clientEmail,
  });
  const issued: string[] = [];
  const messages: FcmMessage[] = [];
  network.use(
    http.post(TOKEN_URL, async ({ request }) => {
      if (options.refuseToken) return options.refuseToken();
      const form = await request.formData();
      if (form.get("grant_type") !== "urn:ietf:params:oauth:grant-type:jwt-bearer") {
        return HttpResponse.json({ error: "unsupported_grant_type" }, { status: 400 });
      }
      try {
        const assertion = form.get("assertion");
        const { payload } = await jwtVerify(typeof assertion === "string" ? assertion : "", keys.publicKey, {
          issuer: clientEmail,
          audience: TOKEN_URL,
          algorithms: ["RS256"],
        });
        if (payload.scope !== "https://www.googleapis.com/auth/firebase.messaging") throw new Error("Wrong scope");
      } catch {
        return HttpResponse.json({ error: "invalid_grant" }, { status: 400 });
      }
      const token = `access-${crypto.randomUUID()}`;
      issued.push(token);
      return HttpResponse.json({ access_token: token, expires_in: options.expiresIn ?? 3599, token_type: "Bearer" });
    }),
    http.post(SEND_URL, async ({ request }) => {
      const project = SEND_URL.exec(request.url)?.[1];
      const bearer = request.headers.get("authorization")?.replace(/^Bearer /, "") ?? "";
      if (project !== PROJECT || !issued.includes(bearer)) return HttpResponse.json({ error: { code: 401, status: "UNAUTHENTICATED" } }, { status: 401 });
      const { message } = (await request.json()) as { message: FcmMessage };
      messages.push(message);
      return options.answer?.(message.token) ?? HttpResponse.json({ name: `projects/${PROJECT}/messages/${messages.length}` });
    }),
  );
  return { serviceAccount, messages, tokensIssued: () => issued.length };
}

/** FCM's answer for a token that is no longer registered. */
export function unregistered(): Response {
  return HttpResponse.json(
    {
      error: {
        code: 404,
        message: "Requested entity was not found.",
        status: "NOT_FOUND",
        details: [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "UNREGISTERED" }],
      },
    },
    { status: 404 },
  );
}

/** FCM's answer for a token that is not a registration token at all, with or without the field it names. */
export function invalidRegistration(withField: boolean): Response {
  const details: unknown[] = [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "INVALID_ARGUMENT" }];
  if (withField) {
    const violation = { field: "message.token", description: "Invalid registration token" };
    details.push({ "@type": "type.googleapis.com/google.rpc.BadRequest", fieldViolations: [violation] });
  }
  const message = withField ? "Request contains an invalid argument." : "The registration token is not a valid FCM registration token";
  return HttpResponse.json({ error: { code: 400, message, status: "INVALID_ARGUMENT", details } }, { status: 400 });
}
