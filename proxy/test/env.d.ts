import type { D1Migration } from "cloudflare:test";
import type { Env as ProxyEnv } from "../src/index";

// The bindings a test gets come from wrangler.jsonc, and the secrets from vitest.config.ts; this
// gives them the types the Worker declares, plus the migrations vitest.config.ts reads.
declare global {
  namespace Cloudflare {
    interface Env extends ProxyEnv {
      TEST_MIGRATIONS: D1Migration[];
    }
  }
}
