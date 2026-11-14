import type { Env as ProxyEnv } from "../src/index";

// The bindings a test gets come from wrangler.jsonc, and the secrets from vitest.config.ts; this
// gives them the types the Worker declares.
declare global {
  namespace Cloudflare {
    interface Env extends ProxyEnv {}
  }
}
