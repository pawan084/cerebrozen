#!/usr/bin/env node
/*
 * Fails (exit 1) if any user-facing surface makes an unbacked superlative or
 * medical claim. The automated half of docs/CLAIMS_MAP.md.
 *
 * Adapted 2026-07-30 from the version on origin/main-sibling-legacy, which
 * scanned apps/web/src only. That would have missed every over-claim actually
 * found in this repo: "unlimited voice" was an Android string, "Downloaded
 * soundscape" was Swift. Copy lives in four places here, so all four are read.
 *
 * Deliberately PHRASE-based, not word-based, so the product's careful
 * disclaimers pass — "not a substitute for therapy", "never diagnoses,
 * prescribes, or replaces professional care". What is banned is the
 * affirmative overclaim those disclaimers are the opposite of.
 *
 * To retire a false positive: fix the copy, or add the claim to CLAIMS_MAP.md
 * with the mechanism that makes it true and delete the phrase here. Never add a
 * silent allowlist — that defeats the gate.
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

// Every surface a user can read. Extensions are per-directory because a Swift
// file and a strings.xml carry copy just as much as a .tsx does.
const SURFACES = [
  { dir: "apps/web/app", ext: /\.(tsx?|mdx?)$/ },
  { dir: "apps/web/components", ext: /\.(tsx?)$/ },
  { dir: "apps/app/app", ext: /\.(tsx?|mdx?)$/ },
  { dir: "apps/app/components", ext: /\.(tsx?)$/ },
  { dir: "apps/admin/app", ext: /\.(tsx?)$/ },
  { dir: "apps/ios/CereBro", ext: /\.swift$/ },
  { dir: "apps/android/app/src/main/res", ext: /strings\.xml$/ },
];

// 1. Medical / efficacy claims this product cannot make. It is explicitly not
//    a clinical service, so these are false by construction, not merely unproven.
const MEDICAL = [
  "clinically proven",
  "clinically validated",
  "medically proven",
  "scientifically proven",
  "fda approved",
  "fda-approved",
  "fda cleared",
  "fda-cleared",
  "cure for",
  "cures anxiety",
  "cures depression",
  "cures stress",
  "miracle cure",
  "proven to cure",
  "treats depression",
  "treats anxiety",
  "diagnoses your",
];

// 2. Outcome guarantees. No wellness product can promise these.
const GUARANTEES = [
  "guaranteed results",
  "results guaranteed",
  "guaranteed to",
  "100% effective",
  "100 percent effective",
  "risk-free",
  "risk free",
];

// 3. Capability claims that were false in this repo at some point. Each one is
//    here because it shipped: "unlimited voice" implied a voice meter that does
//    not exist, "downloaded soundscape" implied downloads no client implements.
//    They stay banned until the capability is real AND in CLAIMS_MAP.md.
const CAPABILITY = [
  "unlimited voice",
  "downloaded soundscape",
  "offline playback",
  "download for offline",
  "available offline",
];

const BANNED = [...MEDICAL, ...GUARANTEES, ...CAPABILITY];

function walk(dir, ext) {
  const out = [];
  for (const name of readdirSync(dir)) {
    if (name === "node_modules" || name === ".next" || name === "build") continue;
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p, ext));
    else if (ext.test(name)) out.push(p);
  }
  return out;
}

const violations = [];
let scanned = 0;
for (const { dir, ext } of SURFACES) {
  const abs = resolve(root, dir);
  if (!existsSync(abs)) continue;
  for (const file of walk(abs, ext)) {
    scanned += 1;
    const text = readFileSync(file, "utf8");
    const lower = text.toLowerCase();
    for (const phrase of BANNED) {
      let idx = lower.indexOf(phrase);
      while (idx !== -1) {
        const line = text.slice(0, idx).split("\n").length;
        const source = text.slice(0, idx).split("\n").pop() ?? "";
        // A comment explaining why a phrase was REMOVED is not a claim. This is
        // the one exemption, and it is structural rather than a phrase allowlist.
        const isComment = /^\s*(\/\/|\/\*|\*|#|<!--)/.test(source);
        if (!isComment) {
          violations.push({ file: file.replace(`${root}/`, ""), line, phrase });
        }
        idx = lower.indexOf(phrase, idx + 1);
      }
    }
  }
}

if (violations.length) {
  console.error("✗ Unbacked claims (docs/CLAIMS_MAP.md):");
  for (const v of violations) console.error(`  ${v.file}:${v.line}  "${v.phrase}"`);
  console.error(
    `\n${violations.length} claim(s) must map to a mechanism + test in docs/CLAIMS_MAP.md, or be removed.`,
  );
  process.exit(1);
}
console.log(`✓ No unbacked claims across ${scanned} user-facing files.`);
