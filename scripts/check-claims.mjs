#!/usr/bin/env node
/*
 * Fails (exit 1) if the marketing site makes an unbacked superlative or medical claim.
 * This is the automated half of CLAUDE.md rule 6 ("marketing claims must map to
 * mechanisms") and docs/CLAIMS_MAP.md: a claim with no mechanism has no place in apps/web.
 *
 * Deliberately PHRASE-based, not word-based, so the product's careful disclaimers pass —
 * "not a substitute for therapy", "does not diagnose, treat, or cure", "never a substitute
 * for emergency services". We ban the affirmative overclaims those disclaimers are the
 * opposite of. To retire a false positive, remove the phrase or fix the copy — never add a
 * silent allowlist, which would defeat the gate.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const SCAN_DIR = resolve(root, "apps/web/src");

// Affirmative overclaims that never appear in a legitimate disclaimer.
const BANNED = [
  "clinically proven",
  "clinically validated",
  "medically proven",
  "scientifically proven",
  "fda approved",
  "fda-approved",
  "fda cleared",
  "fda-cleared",
  "guaranteed results",
  "results guaranteed",
  "guaranteed to",
  "100% effective",
  "100 percent effective",
  "risk-free",
  "risk free",
  "cure for",
  "cures anxiety",
  "cures depression",
  "cures stress",
  "miracle cure",
  "proven to cure",
];

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else if (/\.(tsx?|mdx?)$/.test(name)) out.push(p);
  }
  return out;
}

const violations = [];
for (const file of walk(SCAN_DIR)) {
  const text = readFileSync(file, "utf8");
  const lower = text.toLowerCase();
  for (const phrase of BANNED) {
    let idx = lower.indexOf(phrase);
    while (idx !== -1) {
      const line = text.slice(0, idx).split("\n").length;
      violations.push({ file: file.replace(`${root}/`, ""), line, phrase });
      idx = lower.indexOf(phrase, idx + 1);
    }
  }
}

if (violations.length) {
  console.error("✗ Unbacked marketing claims (CLAUDE.md rule 6 / docs/CLAIMS_MAP.md):");
  for (const v of violations) console.error(`  ${v.file}:${v.line}  "${v.phrase}"`);
  console.error(
    `\n${violations.length} claim(s) must map to a mechanism + test in docs/CLAIMS_MAP.md, or be removed.`,
  );
  process.exit(1);
}
console.log("✓ No unbacked marketing claims in apps/web/src.");
