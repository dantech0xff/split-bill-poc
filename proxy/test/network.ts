import { setupNetwork } from "@msw/cloudflare";

/** Every outgoing request in a test goes through here; one with no handler fails the test. */
export const network = setupNetwork();
