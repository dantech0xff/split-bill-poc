import { AbuseLedger } from "./ledger";
import { QuotaBook } from "./quota-book";

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
  async fetch(): Promise<Response> {
    return new Response(JSON.stringify({ error: "path" }), {
      status: 404,
      headers: { "content-type": "application/json", "cache-control": "no-store" },
    });
  },
} satisfies ExportedHandler<Env>;
