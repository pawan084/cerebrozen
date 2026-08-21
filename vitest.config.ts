import { defineConfig } from "vitest/config";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Resolve "@/…" against the app the IMPORTER lives in.
 *
 * All four apps define `@` as their own root, so a single static alias is
 * wrong for three of them: a test rendering apps/web's CrisisLines would have
 * pulled apps/app's lib/crisis instead and passed for the wrong reason — the
 * exact failure mode these tests exist to catch. The importer's path is the
 * only thing that says which app is asking.
 */
const APP_OF = /apps[\/](app|web|admin|portal)[\/]/;
const EXTS = ["", ".ts", ".tsx", ".js", ".jsx", "/index.ts", "/index.tsx"];

function resolveAppAlias(source: string, importer: string | undefined) {
  const app = importer ? APP_OF.exec(importer)?.[1] : undefined;
  // A test file importing "@/…" directly has no app in its path; apps/app is
  // the only one whose tests do that, so it stays the default.
  const base = resolve(__dirname, "apps", app ?? "app");
  const rest = source.replace(/^@\//, "");
  for (const ext of EXTS) {
    const candidate = resolve(base, rest + ext);
    if (existsSync(candidate)) return candidate;
  }
  return null;
}

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
    alias: [
      { find: /^@\//, replacement: "@/", customResolver: resolveAppAlias },
      // Every app carries its own node_modules/react, so a component rendered
      // from apps/portal got a DIFFERENT React than @testing-library/react did
      // and every hook died on a null dispatcher ("Cannot read properties of
      // null (reading 'useContext')"). `dedupe` below is not enough on its own
      // because it does not cover the subpath the automatic JSX runtime
      // imports, so react and react-dom are pinned to the root copy by hand.
      { find: /^react$/, replacement: resolve(__dirname, "node_modules/react") },
      { find: /^react\/(.*)$/, replacement: resolve(__dirname, "node_modules/react") + "/$1" },
      { find: /^react-dom$/, replacement: resolve(__dirname, "node_modules/react-dom") },
      { find: /^react-dom\/(.*)$/, replacement: resolve(__dirname, "node_modules/react-dom") + "/$1" },
      // `next` is installed per app, never at the root, so vi.mock("next/link")
      // cannot resolve the id from a test file and registers NOTHING — without
      // an error. The component then loads the real module and dies on a router
      // context that does not exist. Aliasing is the only version that works.
      { find: /^next\/link$/, replacement: resolve(__dirname, "tests/stubs/next-link.tsx") },
      { find: /^next\/navigation$/, replacement: resolve(__dirname, "tests/stubs/next-navigation.ts") },
    ],
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
