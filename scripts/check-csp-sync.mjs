#!/usr/bin/env node
// The four Next apps each carry a hand-copied middleware.ts CSP (per-app
// Docker build contexts — see any of the three files' header comment). They
// are allowed to differ where their needs differ (apps/app adds media-src for
// narration audio), but the security floor must hold in all of them. This
// gate pins that floor, so a future edit to one copy can't quietly weaken it.
//
//   node scripts/check-csp-sync.mjs   # exit 1 if any app's CSP loses a floor directive
import { readFileSync } from "node:fs";

const FILES = [
  "apps/web/middleware.ts",
  "apps/admin/middleware.ts",
  "apps/app/middleware.ts",
  "apps/portal/middleware.ts",
];

// Each entry: [human name, regex the middleware source must match].
const FLOOR = [
  ["default-src 'self'", /default-src 'self'/],
  ["nonce-based script-src in production", /'nonce-\$\{nonce\}'/],
  ["worker-src 'self'", /worker-src 'self'/],
  ["object-src 'none'", /object-src 'none'/],
  ["base-uri 'self'", /base-uri 'self'/],
  ["form-action 'self'", /form-action 'self'/],
  ["frame-ancestors", /frame-ancestors/],
  ["x-nonce forwarded to the framework", /requestHeaders\.set\("x-nonce", nonce\)/],
  ["CSP set on the response", /response\.headers\.set\("Content-Security-Policy", csp\)/],
];

let failed = false;
for (const file of FILES) {
  const src = readFileSync(file, "utf8");
  for (const [name, re] of FLOOR) {
    if (!re.test(src)) {
      console.error(`${file}: missing CSP floor — ${name}`);
      failed = true;
    }
  }
}

if (failed) process.exit(1);
console.log(`CSP floor holds across ${FILES.length} middlewares.`);
