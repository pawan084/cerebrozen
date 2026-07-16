#!/usr/bin/env node
/*
 * Propagates design/tokens.css values into consumer stylesheets, or with
 * --check verifies consumers match (CI gate; exits 1 on drift).
 * A consumer keeps its own file structure; only declarations whose custom
 * property NAME exists in tokens.css get their VALUE synced.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const TOKENS = resolve(root, "design/tokens.css");
const CONSUMERS = [
  resolve(root, "apps/web/src/app/globals.css"),
  resolve(root, "apps/admin/app/globals.css"),
  resolve(root, "apps/app/app/globals.css"),
  // Phase 3+: Android Color.kt via its own generator
];

const check = process.argv.includes("--check");

function parseTokens(css) {
  const map = new Map();
  for (const m of css.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    map.set(m[1], m[2].trim());
  }
  return map;
}

const tokens = parseTokens(readFileSync(TOKENS, "utf8"));
if (tokens.size === 0) {
  console.error(`No tokens found in ${TOKENS}`);
  process.exit(1);
}

let drift = 0;
for (const file of CONSUMERS) {
  let src;
  try {
    src = readFileSync(file, "utf8");
  } catch {
    console.error(`consumer missing: ${file}`);
    drift++;
    continue;
  }
  let out = src;
  for (const [name, value] of tokens) {
    const re = new RegExp(`(${name}\\s*:\\s*)([^;]+)(;)`, "g");
    out = out.replace(re, (whole, pre, cur, post) => {
      if (cur.trim() !== value) {
        drift++;
        if (check) {
          console.error(`${file}: ${name} is "${cur.trim()}", tokens.css says "${value}"`);
        }
        return `${pre}${value}${post}`;
      }
      return whole;
    });
  }
  if (!check && out !== src) {
    writeFileSync(file, out);
    console.log(`synced: ${file}`);
  }
}

if (check) {
  if (drift) {
    console.error(`\n${drift} drifted declaration(s). Run: node scripts/sync-tokens.mjs`);
    process.exit(1);
  }
  console.log(`tokens in sync (${tokens.size} tokens, ${CONSUMERS.length} consumer(s))`);
} else if (!drift) {
  console.log("nothing to sync");
}
