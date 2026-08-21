import { defineConfig } from "vitest/config";
import { resolve } from "node:path";

// One runner for all four Next apps.
//
// The alias is apps/app's, because it is the only app whose lib/ uses "@/" —
// the other three import relatively. Tests live under tests/ rather than beside
// the sources so that `next build` and the production images never see them.
export default defineConfig({
  // Components are .tsx; esbuild needs to be told which JSX runtime to use or
  // every render throws "React is not defined".
  esbuild: { jsx: "automatic" },
  resolve: {
    alias: { "@": resolve(__dirname, "apps/app") },
    // Component tests render apps/app components with @testing-library/react.
    // Without dedupe the component resolves `react` from apps/app/node_modules
    // while the test library resolves it from the root, and two React copies
    // means a null hook dispatcher: "Cannot read properties of null (reading
    // 'useState')" on every render.
    dedupe: ["react", "react-dom"],
  },
  test: {
    // jsdom, not node: most of these modules read localStorage or window at
    // call time, and the queue's whole contract is about surviving a closed tab.
    environment: "jsdom",
    include: ["tests/**/*.test.ts", "tests/**/*.test.tsx"],
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
      include: ["apps/*/lib/**/*.ts", "apps/*/components/**/*.tsx"],
      // mock.ts is 500 lines of fixture data for screens whose models do not
      // exist yet; measuring it would flatter the number without testing code.
      exclude: ["apps/portal/lib/mock.ts"],
      reporter: ["text-summary", "text"],
    },
  },
});
