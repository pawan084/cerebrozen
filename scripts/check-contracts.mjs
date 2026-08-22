#!/usr/bin/env node
/*
 * Fails (exit 1) when a hand-duplicated cross-stack contract has drifted.
 *
 * ARCHITECTURE.md carries a table of values that exist in three or four places
 * on purpose — there is no shared schema between a FastAPI service, a Kotlin
 * object, a Swift file and a `.storekit` declaration, so they are kept in step
 * by hand and by the rule in CLAUDE.md ("change backend + iOS in the same
 * commit"). WC-279 asks for that rule to fail CI instead of fail review, which
 * is what this does.
 *
 * It follows the pattern already proven here by `check-prices.mjs`: prices were
 * hand-written in four places and the Android paywall drifted 25% under every
 * other surface with nothing able to notice. These are the contracts where the
 * same drift costs the most:
 *
 *   1. STORE PRODUCT IDS — the one that takes money. If Android sells
 *      `com.cerebrozen.premium.anual` (or anything the backend does not know),
 *      Play charges the card and `_PRODUCT_TIERS.get(...)` returns `free`. The
 *      user has paid and been given nothing, and every layer believes it did
 *      its job. Prices are already gated; the IDS were not, and an id mismatch
 *      is worse than a price mismatch.
 *
 *   2. CRISIS NUMBERS — the one where a bug is measured in human harm. A
 *      helpline that drifts on one client is a person dialling a number that
 *      does not answer. This has a history here: a UK helpline once reached
 *      Indian users.
 *
 * Deliberately NOT a generic "parse everything" tool. Each contract names its
 * files and its extraction, so a failure says which two surfaces disagree and
 * about what, rather than reporting that a regex stopped matching.
 *
 * To add a contract: write an extractor per surface, compare, and add the row
 * to ARCHITECTURE.md's table. To retire one: delete it here AND there, in the
 * same commit — a gate nobody can see is worse than no gate.
 */
import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const problems = [];
const checked = [];

function read(relative) {
  const path = resolve(root, relative);
  if (!existsSync(path)) {
    problems.push(`${relative} is missing — a contract cannot be checked against a file that is not there.`);
    return null;
  }
  return readFileSync(path, "utf8");
}

/** Compare named sets, reporting what each side has that the others do not. */
function agree(contract, sources) {
  const names = Object.keys(sources);
  const entries = names.map((n) => [n, new Set(sources[n])]);
  const [, first] = entries[0];
  let ok = true;
  for (const [name, set] of entries.slice(1)) {
    const missing = [...first].filter((v) => !set.has(v));
    const extra = [...set].filter((v) => !first.has(v));
    if (missing.length || extra.length) {
      ok = false;
      problems.push(
        `${contract}: ${names[0]} and ${name} disagree.` +
          (missing.length ? `\n    ${name} is missing: ${missing.join(", ")}` : "") +
          (extra.length ? `\n    ${name} has extra:   ${extra.join(", ")}` : ""),
      );
    }
  }
  if (ok) checked.push(`${contract} — ${first.size} value(s) agree across ${names.length} surfaces`);
  return ok;
}

/* ── 1. Store product ids ──────────────────────────────────────────────── */
{
  const py = (relative) => {
    const src = read(relative);
    if (!src) return [];
    const block = /_PRODUCT_TIERS\s*=\s*\{([\s\S]*?)\}/.exec(src);
    if (!block) {
      problems.push(`${relative}: no _PRODUCT_TIERS map found.`);
      return [];
    }
    return [...block[1].matchAll(/"([^"]+)"\s*:/g)].map((m) => m[1]);
  };

  const appstore = py("backend/app/services/appstore.py");
  const playstore = py("backend/app/services/playstore.py");

  const kotlinSrc = read("apps/android/app/src/main/java/com/cerebrozen/app/net/Billing.kt");
  const kotlinBlock = kotlinSrc && /val PRODUCTS\s*=\s*listOf\(([\s\S]*?)\)/.exec(kotlinSrc);
  const android = kotlinBlock ? [...kotlinBlock[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]) : [];
  if (kotlinSrc && !kotlinBlock) problems.push("Billing.kt: no PRODUCTS list found.");

  const storekitSrc = read("apps/ios/CereBro/Products.storekit");
  const ios = storekitSrc
    ? [...storekitSrc.matchAll(/"productID"\s*:\s*"([^"]+)"/g)].map((m) => m[1])
    : [];

  // A vacuous pass is the failure mode these gates exist to avoid: an empty
  // set agrees with an empty set.
  if (!appstore.length) problems.push("appstore.py declared no products — refusing to pass vacuously.");

  agree("Store product ids", {
    "backend/appstore.py": appstore,
    "backend/playstore.py": playstore,
    "android/Billing.kt": android,
    "ios/Products.storekit": ios,
  });
}

/* ── 2. Crisis numbers ─────────────────────────────────────────────────── */
{
  // Only the numbers that must be identical everywhere. Region-specific lines
  // legitimately differ per client region and are checked by their own suites;
  // these two are the India defaults every surface shows.
  const REQUIRED = ["14416", "112"];

  const surfaces = {
    "web/lib/crisis.ts": "apps/app/lib/crisis.ts",
    "android/CrisisDirectory.kt":
      "apps/android/app/src/main/java/com/cerebrozen/app/ui/screens/CrisisDirectory.kt",
    "landing/lib/crisis.ts": "apps/web/lib/crisis.ts",
  };

  for (const [name, relative] of Object.entries(surfaces)) {
    const src = read(relative);
    if (!src) continue;
    const missing = REQUIRED.filter((n) => !src.includes(n));
    if (missing.length) {
      problems.push(
        `Crisis numbers: ${name} does not carry ${missing.join(", ")} — ` +
          "a helpline that drifts on one client is a person dialling a number that does not answer.",
      );
    }
  }
  if (!problems.some((p) => p.startsWith("Crisis numbers"))) {
    checked.push(`Crisis numbers — ${REQUIRED.join(", ")} present on every client directory`);
  }
}

/* ── Report ────────────────────────────────────────────────────────────── */
if (problems.length) {
  console.error("✗ Cross-stack contract drift:\n");
  for (const p of problems) console.error("  " + p + "\n");
  console.error(
    "These values are duplicated by hand because nothing shares a schema across\n" +
      "FastAPI, Kotlin, Swift and .storekit. Fix every surface in ONE commit —\n" +
      "see the cross-stack contract table in docs/ARCHITECTURE.md.",
  );
  process.exit(1);
}

console.log("✓ Cross-stack contracts agree.");
for (const line of checked) console.log("  · " + line);
