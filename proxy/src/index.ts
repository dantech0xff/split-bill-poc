import { billAnalysis } from "./bill-analysis";
import { AbuseLedger } from "./ledger";
import { QuotaBook } from "./quota-book";
import { errorResponse } from "./respond";

export { AbuseLedger, QuotaBook };

export interface Env {
  /** The Anthropic key. A secret of the Worker; on a computer it sits in `.dev.vars`, which git ignores. */
  ANTHROPIC_API_KEY: string;
  /** Salt for the hashes that name objects after an IP or an account, so neither is stored as it is. */
  HASH_SALT: string;
  /** Key that signs the sessions the proxy hands out after a Google sign-in. */
  SESSION_SIGNING_KEY: string;
  QUOTA_BOOK: DurableObjectNamespace<QuotaBook>;
  ABUSE_LEDGER: DurableObjectNamespace<AbuseLedger>;
}

export default {
  async fetch(request, env, ctx): Promise<Response> {
    try {
      const { pathname } = new URL(request.url);
      if (pathname === "/v1/bill-analysis") {
        return request.method === "POST" ? await billAnalysis(request, env, ctx) : errorResponse(405, "method");
      }
      return errorResponse(404, "path");
    } catch (error) {
      console.error(JSON.stringify({ event: "request_failed", error: error instanceof Error ? error.name : "unknown" }));
      return errorResponse(500, "internal");
    }
  },
} satisfies ExportedHandler<Env>;
