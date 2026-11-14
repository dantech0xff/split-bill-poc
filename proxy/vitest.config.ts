import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      miniflare: {
        bindings: {
          ANTHROPIC_API_KEY: "test-anthropic-key",
          HASH_SALT: "test-hash-salt",
          SESSION_SIGNING_KEY: "test-session-signing-key-that-is-long-enough",
        },
      },
    }),
  ],
  test: {
    setupFiles: ["./test/setup.ts"],
  },
});
