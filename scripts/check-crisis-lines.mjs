/**
 * The crisis directory is duplicated by hand across three stacks. This makes
 * that duplication checkable.
 *
 * `CLAUDE.md` lists crisis regions among the cross-stack contracts kept in sync
 * by hand, alongside the assessment taxonomy, widget kinds and product ids. Of
 * those, this is the one where drift is measured in human harm: a member in
 * crisis is shown their region's lines by the client, and told them again by the
 * server when `crisis.reply_suffix` appends resources to a chat reply. If the
 * backend thinks a US member should dial 112 while iOS shows 911, one of those
 * two surfaces is handing a person in their worst hour a number that does not
 * answer.
 *
 * The repo already gates its other hand-copied contracts — `sync-tokens`,
 * `check-csp-sync`, `check-prices`, `check-claims`. The numbers happen to agree
 * today (verified 2026-08-13); this is what keeps them agreeing when nobody is
 * looking.
 *
 * Three things are asserted, in order of how badly they fail:
 *   1. the three stacks cover the SAME set of region codes;
 *   2. per region, the ordered list of dialable targets is identical — order is
 *      semantic, because "Tele-MANAS leads on every crisis surface" is a stated
 *      design rule (REDESIGN §2.3), not a preference;
 *   3. the fallback list for unknown regions is identical too, since that is
 *      what every member outside the seven supported regions actually sees.
 */
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const SOURCES = {
  backend: "backend/app/services/crisis.py",
  android: "apps/android/app/src/main/java/com/cerebrozen/app/ui/screens/CrisisDirectory.kt",
  ios: "apps/ios/CereBro/Features/Safety/CrisisResources.swift",
};

/**
 * Compare what is dialled, not how it is written.
 *
 * The stacks legitimately spell the helpline finder differently — the backend
 * stores a full URL because it serializes to JSON clients render as a link,
 * Android stores the bare host because it builds the intent itself. Normalizing
 * the scheme keeps that from reading as drift. Nothing else is touched: spacing
 * inside a number ("13 11 14") is preserved, because a stack that silently
 * reformatted it may also have mistyped it.
 */
const normalize = (target) =>
  target.trim().replace(/^https?:\/\//, "").replace(/\/$/, "").toLowerCase();

/** `"IN": [ {..."number": "14416"}, ... ]` plus the `_DEFAULT` list. */
function parseBackend(src) {
  const regions = {};
  const block = /"([A-Z]{2})":\s*\[([\s\S]*?)\]/g;
  for (const [, code, body] of src.matchAll(block)) {
    regions[code] = [...body.matchAll(/"number":\s*"([^"]+)"/g)].map((m) => normalize(m[1]));
  }
  const fallback = /_DEFAULT[^=]*=\s*\[([\s\S]*?)\]/.exec(src);
  return {
    regions,
    fallback: fallback
      ? [...fallback[1].matchAll(/"number":\s*"([^"]+)"/g)].map((m) => normalize(m[1]))
      : [],
  };
}

/** `"IN" -> listOf( CrisisLine(R.string.x, "14416", ...), ... )` + the `else ->`. */
function parseAndroid(src) {
  const regions = {};
  const block = /"([A-Z]{2})"\s*->\s*listOf\(([\s\S]*?)\n\s*\)/g;
  for (const [, code, body] of src.matchAll(block)) {
    regions[code] = [...body.matchAll(/CrisisLine\([^,]+,\s*"([^"]+)"/g)].map((m) => normalize(m[1]));
  }
  const fallback = /else\s*->\s*listOf\(([\s\S]*?)\n\s*\)/.exec(src);
  return {
    regions,
    fallback: fallback
      ? [...fallback[1].matchAll(/CrisisLine\([^,]+,\s*"([^"]+)"/g)].map((m) => normalize(m[1]))
      : [],
  };
}

/** `case "IN": return [ CrisisResource(title: "...", phone: "14416", ...) ]` + `default:`. */
function parseIos(src) {
  const regions = {};
  // Each `case "XX":` runs until the next `case`/`default` at the same level.
  const block = /case\s+"([A-Z]{2})":([\s\S]*?)(?=\n\s*(?:case\s|default:))/g;
  for (const [, code, body] of src.matchAll(block)) {
    const phones = [...body.matchAll(/phone:\s*"([^"]+)"/g)].map((m) => normalize(m[1]));
    const urls = [...body.matchAll(/url:\s*"([^"]+)"/g)].map((m) => normalize(m[1]));
    if (phones.length || urls.length) regions[code] = [...phones, ...urls];
  }
  // Bound the default branch by its own `return [ ... ]` rather than by the
  // brace that closes the switch: that brace is indented deeper than a naive
  // lookahead expects, and getting it wrong parsed an empty fallback and
  // reported a drift that did not exist.
  const tail = /default:\s*return\s*\[([\s\S]*?)\n\s*\]/.exec(src);
  const fallback = tail
    ? [
        ...[...tail[1].matchAll(/phone:\s*"([^"]+)"/g)].map((m) => normalize(m[1])),
        ...[...tail[1].matchAll(/url:\s*"([^"]+)"/g)].map((m) => normalize(m[1])),
      ]
    : [];
  return { regions, fallback };
}

const parsed = {
  backend: parseBackend(readFileSync(join(root, SOURCES.backend), "utf8")),
  android: parseAndroid(readFileSync(join(root, SOURCES.android), "utf8")),
  ios: parseIos(readFileSync(join(root, SOURCES.ios), "utf8")),
};

const problems = [];

// A parser that silently matches nothing would turn this gate into a no-op that
// reports success — the worst possible failure for a safety check. Refuse to
// pass without evidence that each source was actually read.
// Distinguishing "I could not read this" from "these disagree" is not
// pedantry: the first version of this script mis-parsed iOS's default branch
// and confidently reported a fallback drift that did not exist. A gate that
// cries wolf about crisis numbers gets muted, and a muted gate is worse than
// no gate. Empty means broken parser — every stack has regions and a fallback.
for (const [stack, { regions, fallback }] of Object.entries(parsed)) {
  if (Object.keys(regions).length === 0) {
    problems.push(`${stack}: parsed 0 regions from ${SOURCES[stack]} — the format changed and this gate stopped checking anything`);
  }
  if (fallback.length === 0) {
    problems.push(`${stack}: parsed no unknown-region fallback from ${SOURCES[stack]} — that is a broken parser, not a product state`);
  }
}

if (problems.length === 0) {
  const sets = Object.entries(parsed).map(([stack, { regions }]) => [stack, Object.keys(regions).sort()]);
  const [, reference] = sets[0];
  for (const [stack, codes] of sets.slice(1)) {
    if (codes.join(",") !== reference.join(",")) {
      problems.push(
        `region coverage differs — ${sets[0][0]} has [${reference.join(", ")}], ${stack} has [${codes.join(", ")}]. ` +
          `A member in a region one stack knows and another does not gets the generic fallback from the second.`,
      );
    }
  }

  for (const code of reference) {
    const rows = Object.entries(parsed).map(([stack, { regions }]) => [stack, regions[code] ?? []]);
    const [, expected] = rows[0];
    for (const [stack, actual] of rows.slice(1)) {
      if (actual.join(" | ") !== expected.join(" | ")) {
        problems.push(
          `${code}: ${rows[0][0]} dials [${expected.join(", ")}] but ${stack} dials [${actual.join(", ")}]`,
        );
      }
    }
  }

  const fallbacks = Object.entries(parsed).map(([stack, { fallback }]) => [stack, fallback]);
  const [, expectedFallback] = fallbacks[0];
  for (const [stack, actual] of fallbacks.slice(1)) {
    if (actual.join(" | ") !== expectedFallback.join(" | ")) {
      problems.push(
        `unknown-region fallback differs — ${fallbacks[0][0]} offers [${expectedFallback.join(", ")}], ` +
          `${stack} offers [${actual.join(", ")}]. This is what every member outside the supported regions sees.`,
      );
    }
  }
}

if (problems.length) {
  console.error("✗ Crisis directory has drifted across stacks:\n");
  for (const p of problems) console.error(`  ${p}`);
  console.error(
    "\nThese numbers are dialled by people in crisis. Fix the mismatch in all three sources:\n" +
      Object.values(SOURCES).map((s) => `  ${s}`).join("\n"),
  );
  process.exit(1);
}

const regionCount = Object.keys(parsed.backend.regions).length;
console.log(
  `✓ Crisis directory agrees across backend, iOS and Android ` +
    `(${regionCount} regions + fallback, order included).`,
);
