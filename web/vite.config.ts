import preact from "@preact/preset-vite";
import { defineConfig } from "vitest/config";

// One page, one script and one stylesheet, all under /assets/: the Worker embeds web/dist and
// serves nothing else. Nothing is inlined into index.html, because the page's CSP allows no inline
// script or style.
export default defineConfig({
  plugins: [preact()],
  base: "/",
  build: {
    outDir: "dist",
    assetsDir: "assets",
    assetsInlineLimit: 0,
    modulePreload: { polyfill: false },
    sourcemap: false,
  },
  test: {
    environment: "happy-dom",
    include: ["tests/**/*.test.{ts,tsx}"],
    restoreMocks: true,
    unstubGlobals: true,
  },
});
