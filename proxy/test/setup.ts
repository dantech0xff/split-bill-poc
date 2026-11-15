import { applyD1Migrations, reset } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest";
import { network } from "./network";

beforeAll(() => {
  network.enable();
});

// reset() empties every Durable Object, the one behind the local D1 database included, so each
// test starts from an empty database with the schema of migrations/.
beforeEach(async () => {
  await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
});

afterEach(async () => {
  network.resetHandlers();
  await reset();
});

afterAll(() => {
  network.disable();
});
