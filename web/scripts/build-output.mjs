import { fileURLToPath } from "node:url";

/** Where `vite build` writes the page, which the Worker embeds. */
export const OUTPUT_DIRECTORY = fileURLToPath(new URL("../dist/", import.meta.url));
