import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 30_000,
  expect: { timeout: 10_000 },
  // Kept at 1 deliberately, and NOT as a rescue: the run is invoked with
  // --fail-on-flaky-tests, so a test that fails and then passes still turns the
  // build red. The retry exists to tell us WHICH kind of red it is —
  // "always broken" and "intermittent" need completely different investigations,
  // and with retries at 0 they look identical.
  //
  // A flake is a defect here, not weather. The Android suite's twelve
  // "AppNotIdleException flakes" (2026-08-15) were one real bug: a Compose
  // click left an underdamped spring running and poisoned every later test in
  // the JVM. Retrying past that would have hidden it indefinitely.
  //
  // To park a genuinely broken test, use `test.fixme` with a reason. That is
  // visible in the report and in the diff; a silent rerun is neither.
  retries: 1,
  workers: 1,
  reporter: [["list"]],
  use: {
    headless: true,
    screenshot: "only-on-failure",
    actionTimeout: 10_000,
  },
});
