import { existsSync } from "node:fs";
import { defineConfig, devices } from "@playwright/test";

/** The port the local proxy listens on for these tests, apart from the 8787 of `npm run dev`. */
const PORT = 8797;
const INSPECTOR_PORT = 9339;
/** Where this machine keeps its Chromium; elsewhere, the one `npx playwright install chromium` puts in place. */
const LOCAL_CHROMIUM = "/opt/pw-browsers/chromium";
const executablePath = process.env.PW_CHROMIUM_PATH ?? (existsSync(LOCAL_CHROMIUM) ? LOCAL_CHROMIUM : undefined);
/** Turnstile's script comes from Cloudflare; behind an HTTPS proxy, the browser goes through it too. */
const outboundProxy = process.env.HTTPS_PROXY ?? process.env.https_proxy;

export default defineConfig({
  testDir: "e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [["list"]],
  use: {
    ...devices["Pixel 7"],
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: "retain-on-failure",
    launchOptions: {
      ...(executablePath === undefined ? {} : { executablePath }),
      ...(outboundProxy === undefined ? {} : { proxy: { server: outboundProxy, bypass: "127.0.0.1,localhost" } }),
    },
  },
  webServer: {
    cwd: "../proxy",
    command:
      `node ../web/e2e/prepare-proxy.mjs && npx wrangler dev --ip 127.0.0.1 --port ${PORT} --inspector-port ${INSPECTOR_PORT} ` +
      "--persist-to ../web/.e2e/state --show-interactive-dev-session=false",
    url: `http://127.0.0.1:${PORT}/s/`,
    reuseExistingServer: false,
    timeout: 180_000,
    stdout: "ignore",
    stderr: "pipe",
  },
});
