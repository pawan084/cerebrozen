import { defineConfig, devices } from "@playwright/test";

/* The suite runs against a REAL composed stack — never mocks. That is the whole point:
   ENGINEERING.md's cross-stack protocol requires "an e2e run against the composed stack"
   before any SSE-vocabulary or JWT-claims change, and a mock cannot discharge that. The
   contracts these specs pin (who serves what, what a token carries, what an HR admin can
   reach) only exist *between* the services.

   Default URLs are the ports docker-compose.yml publishes, which are also the ports the
   local dev servers use — so `npm test` works against either a `docker compose up` stack
   or hand-started dev servers, and CI uses compose (`npm run e2e:compose`). Override any
   of them per environment. */
export const urls = {
  web: process.env.E2E_WEB_URL || "http://localhost:3000",
  admin: process.env.E2E_ADMIN_URL || "http://localhost:3001",
  app: process.env.E2E_APP_URL || "http://localhost:3002",
  platform: process.env.E2E_PLATFORM_URL || "http://localhost:8100",
  engine: process.env.E2E_ENGINE_URL || "http://localhost:8000",
};

export default defineConfig({
  testDir: "./tests",
  // Contract specs run against ONE shared stack with a shared database. Parallel workers
  // would race on org/seat state (an invite spec's seat count vs an analytics spec's
  // floor), so the file order is the execution order and each file owns its own data.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    // Cold containers are slow on the first hit; the assertions are about contracts,
    // not latency, so be patient rather than flaky.
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
