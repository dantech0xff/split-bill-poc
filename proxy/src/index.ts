import { accountRoute, sessionRoute, usageRoute } from "./account";
import { billAnalysis } from "./bill-analysis";
import { deleteBillRoute, putBillRoute, recordSettlementRoute } from "./bills";
import { FxBook } from "./fx-book";
import { fxRatesRoute, refreshFxRates } from "./fx-rates";
import { addMemberRoute, createGroupRoute, leaveGroupRoute, listGroupsRoute, readGroupRoute, renameGroupRoute, renameMemberRoute } from "./groups";
import { approveClaimRoute, createInviteRoute, joinGroupRoute, readInviteRoute, rejectClaimRoute, revokeInvitesRoute } from "./invites";
import { AbuseLedger } from "./ledger";
import { QuotaBook } from "./quota-book";
import { errorResponse } from "./respond";

export { AbuseLedger, FxBook, QuotaBook };

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
  /** Groups, their members, bills and settlements; the schema is in `migrations/`. */
  DB: D1Database;
  QUOTA_BOOK: DurableObjectNamespace<QuotaBook>;
  ABUSE_LEDGER: DurableObjectNamespace<AbuseLedger>;
  FX_BOOK: DurableObjectNamespace<FxBook>;
}

/** A route gets the parameters of its path, the `:name` segments of its pattern, in order. */
type Route = (request: Request, env: Env, ctx: ExecutionContext, params: string[]) => Promise<Response>;

/** The salt keeps the names of IPs and accounts from being worked back; without one, nothing may run. */
const MIN_SALT_BYTES = 16;

const ROUTES: ReadonlyArray<readonly [pattern: string, methods: Record<string, Route>]> = [
  ["/v1/bill-analysis", { POST: (request, env, ctx) => billAnalysis(request, env, ctx) }],
  ["/v1/session", { POST: sessionRoute }],
  ["/v1/usage", { GET: usageRoute }],
  ["/v1/account", { DELETE: accountRoute }],
  ["/v1/fx-rates", { GET: fxRatesRoute }],
  ["/v1/groups", { GET: listGroupsRoute, POST: createGroupRoute }],
  ["/v1/groups/:group", { GET: readGroupRoute, PATCH: renameGroupRoute }],
  ["/v1/groups/:group/members", { POST: addMemberRoute }],
  ["/v1/groups/:group/members/:member", { PATCH: renameMemberRoute }],
  ["/v1/groups/:group/leave", { POST: leaveGroupRoute }],
  ["/v1/groups/:group/invites", { POST: createInviteRoute, DELETE: revokeInvitesRoute }],
  ["/v1/groups/:group/claims/:member/approve", { POST: approveClaimRoute }],
  ["/v1/groups/:group/claims/:member/reject", { POST: rejectClaimRoute }],
  ["/v1/groups/:group/bills/:bill", { PUT: putBillRoute, DELETE: deleteBillRoute }],
  ["/v1/groups/:group/settlements", { POST: recordSettlementRoute }],
  ["/v1/invites/:token", { GET: readInviteRoute }],
  ["/v1/invites/:token/join", { POST: joinGroupRoute }],
];

/** The methods of the route whose pattern [pathname] matches, with the path's parameters; null when none does. */
function routeOf(pathname: string): { methods: Record<string, Route>; params: string[] } | null {
  const segments = pathname.split("/");
  for (const [pattern, methods] of ROUTES) {
    const parts = pattern.split("/");
    if (parts.length !== segments.length) continue;
    const params: string[] = [];
    const matches = parts.every((part, index) => {
      if (!part.startsWith(":")) return part === segments[index];
      params.push(segments[index]);
      return segments[index] !== "";
    });
    if (matches) return { methods, params };
  }
  return null;
}

export default {
  async fetch(request, env, ctx): Promise<Response> {
    try {
      const matched = routeOf(new URL(request.url).pathname);
      if (matched === null) return errorResponse(404, "path");
      const route = matched.methods[request.method];
      if (route === undefined) return errorResponse(405, "method");
      if (new TextEncoder().encode(env.HASH_SALT ?? "").byteLength < MIN_SALT_BYTES) {
        console.error(JSON.stringify({ event: "not_configured", missing: "HASH_SALT" }));
        return errorResponse(503, "not_configured");
      }
      return await route(request, env, ctx, matched.params);
    } catch (error) {
      console.error(JSON.stringify({ event: "request_failed", error: error instanceof Error ? error.name : "unknown" }));
      return errorResponse(500, "internal");
    }
  },

  /** The daily cron of `wrangler.jsonc`, which fetches the exchange rates. */
  async scheduled(_controller, env): Promise<void> {
    await refreshFxRates(env);
  },
} satisfies ExportedHandler<Env>;
