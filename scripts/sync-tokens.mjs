#!/usr/bin/env node
// Propagate design/tokens.css into the marker-delimited block of each app's
// globals.css (per-app Docker build contexts can't share a file at build
// time, so the copies are checked in and this script + the CI --check keep
// them from drifting). Run from the repo root:
//   node scripts/sync-tokens.mjs          # rewrite the synced blocks
//   node scripts/sync-tokens.mjs --check  # exit 1 if any copy drifted (CI)
import { readFileSync, writeFileSync } from "node:fs";

const SOURCE = "design/tokens.css";
const TARGETS = [
  "apps/web/app/globals.css",
  "apps/admin/app/globals.css",
  "apps/app/app/globals.css",
];
const START = "/* @cerebro-tokens:start */";
const END = "/* @cerebro-tokens:end */";

// The token rule is everything after the file's leading comment.
const tokens = readFileSync(SOURCE, "utf8").replace(/^\/\*[\s\S]*?\*\/\s*/, "").trim();
const block = `${START}\n/* Synced from design/tokens.css — edit THERE, then run \`node scripts/sync-tokens.mjs\`. */\n${tokens}\n${END}`;

const check = process.argv.includes("--check");
let drift = false;

for (const file of TARGETS) {
  const src = readFileSync(file, "utf8");
  const start = src.indexOf(START);
  const end = src.indexOf(END);
  if (start === -1 || end === -1) {
    console.error(`${file}: token markers missing — re-add the @cerebro-tokens block`);
    process.exit(1);
  }
  const next = src.slice(0, start) + block + src.slice(end + END.length);
  if (next !== src) {
    drift = true;
    if (check) {
      console.error(`${file}: drifted from ${SOURCE}`);
    } else {
      writeFileSync(file, next);
      console.log(`${file}: synced`);
    }
  }
}

if (check && drift) process.exit(1);
console.log(check ? "tokens in sync" : "done");
