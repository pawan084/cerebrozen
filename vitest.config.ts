import { defineConfig } from "vitest/config";
import { resolve } from "node:path";

// One runner for all four Next apps.
//
// The alias is apps/app's, because it is the only app whose lib/ uses "@/" —
// the other three import relatively. Tests live under tests/ rather than beside
// the sources so that `next build` and the production images never see them.
export default defineConfig({
  resolve: {
    alias: { "@": resolve(__dirname, "apps/app") },
  },
  test: {
    // jsdom, not node: most of these modules read localStorage or window at
    // call time, and the queue's whole contract is about surviving a closed tab.
    environment: "jsdom",
    include: ["tests/**/*.test.ts"],
    // The API clients hold their tokens in module state on purpose — the web
    // app's access token never touches storage — so those tests take a fresh
    // copy of the module per case via resetModules() + dynamic import. Each
    // re-import re-transforms the module, and with every file running in
    // parallel the first one in a worker can exceed the 5s default while doing
    // nothing but compiling. The tests themselves run in tens of milliseconds;
    // this budget is for the transform, not for any awaited work.
    testTimeout: 20_000,
    coverage: {
      provider: "v8",
      include: ["apps/*/lib/**/*.ts"],
      // mock.ts is 500 lines of fixture data for screens whose models do not
      // exist yet; measuring it would flatter the number without testing code.
      exclude: ["apps/portal/lib/mock.ts"],
      reporter: ["text-summary", "text"],
    },
  },
});
