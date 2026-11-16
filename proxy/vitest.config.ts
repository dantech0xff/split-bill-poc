import { fileURLToPath } from "node:url";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  // The same files `wrangler d1 migrations apply` runs; test/setup.ts applies them to the test database.
  const migrations = await readD1Migrations(fileURLToPath(new URL("./migrations", import.meta.url)));
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        miniflare: {
          bindings: {
            ANTHROPIC_API_KEY: "test-anthropic-key",
            HASH_SALT: "test-hash-salt-for-the-proxy-tests",
            SESSION_SIGNING_KEY: "test-session-signing-key-that-is-long-enough",
            GOOGLE_CLIENT_ID: "test-web-client.apps.googleusercontent.com",
            // The secret test/fake-turnstile.ts expects; Cloudflare's own test secrets are tried there too.
            TURNSTILE_SECRET: "test-turnstile-secret",
            TEST_MIGRATIONS: migrations,
          },
        },
      }),
    ],
    test: {
      // The Worker's tests; eval/revise/test/ holds those of the evaluation's runner, which Node runs.
      include: ["test/**/*.test.ts"],
      setupFiles: ["./test/setup.ts"],
    },
  };
});
