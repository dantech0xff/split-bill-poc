import { accountRoute, sessionRoute, usageRoute } from "./account";
import { billAnalysis } from "./bill-analysis";
import { AbuseLedger } from "./ledger";
import { QuotaBook } from "./quota-book";
import { errorResponse } from "./respond";

export { AbuseLedger, QuotaBook };

// Secrets are set apart from a deploy, so a Worker can run before they exist: those that may be
// missing are optional, and the code that reads them says what happens without them.
export interface Env {
  /**
   * The Anthropic key. A secret of the Worker; on a computer it sits in `.dev.vars`, which git
   * ignores. Until it is set, bill reads answer 503 `not_configured`.
   */
  ANTHROPIC_API_KEY?: string;
  /**
   * Salt for the hashes that name objects after an IP or an account, so neither is stored as it is.
   * Every route refuses to run without one, before it is read anywhere else.
   */
  HASH_SALT: string;
  /** Key that signs the sessions the proxy hands out after a Google sign-in; at least 32 bytes. Sign-in is off without it. */
  SESSION_SIGNING_KEY?: string;
  /** The app's web client ID at Google, which Google's ID tokens for the app are meant for. Public. */
  GOOGLE_CLIENT_ID: string;
  /** "true" while bills may be read with AI; anything else turns AI off without a release. */
  AI_ENABLED: string;
  /** The oldest version code of the app that may still read bills. */
  MIN_APP_VERSION: string;
  QUOTA_BOOK: DurableObjectNamespace<QuotaBook>;
  ABUSE_LEDGER: DurableObjectNamespace<AbuseLedger>;
}

type Route = (request: Request, env: Env, ctx: ExecutionContext) => Promise<Response>;

/** The salt keeps the names of IPs and accounts from being worked back; without one, nothing may run. */
const MIN_SALT_BYTES = 16;

const ROUTES: Record<string, Record<string, Route>> = {
  "/v1/bill-analysis": { POST: billAnalysis },
  "/v1/session": { POST: sessionRoute },
  "/v1/usage": { GET: usageRoute },
  "/v1/account": { DELETE: accountRoute },
};

export default {
  async fetch(request, env, ctx): Promise<Response> {
    try {
      const methods = ROUTES[new URL(request.url).pathname];
      if (methods === undefined) return errorResponse(404, "path");
      const route = methods[request.method];
      if (route === undefined) return errorResponse(405, "method");
      if (new TextEncoder().encode(env.HASH_SALT ?? "").byteLength < MIN_SALT_BYTES) {
        console.error(JSON.stringify({ event: "not_configured", missing: "HASH_SALT" }));
        return errorResponse(503, "not_configured");
      }
      return await route(request, env, ctx);
    } catch (error) {
      console.error(JSON.stringify({ event: "request_failed", error: error instanceof Error ? error.name : "unknown" }));
      return errorResponse(500, "internal");
    }
  },
} satisfies ExportedHandler<Env>;
