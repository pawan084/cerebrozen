#!/usr/bin/env node
// Every test named in docs/CLAIMS_MAP.md must actually exist.
//
// That file's own rule is "a row without a test is an intention, not a claim" —
// but nothing checked that the named test was real. On 2026-08-12 six citations
// were fiction: DisclosureCopyTest and ConsentDefaultsTest had never been
// written, and four backend files had been renamed (test_usage.py →
// test_usage_limit.py, test_consent.py → test_consent_enforced.py,
// test_safety.py → test_safety_reach.py, test_insights.py →
// test_insights_no_guesses.py) without the doc following. A claims map that
// cites a guarantee nobody wrote is worse than no claims map: it reads like
// evidence.
//
//   node scripts/check-claims-tests.mjs   # exit 1 if any citation does not resolve
import { readFileSync, existsSync, readdirSync, statSync } from "node:fs";
import { join, extname } from "node:path";

const DOC = "docs/CLAIMS_MAP.md";
const doc = readFileSync(DOC, "utf8");

function walk(dir, exts, out = []) {
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry === "build" || entry === ".next") continue;
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) walk(p, exts, out);
    else if (exts.includes(extname(p))) out.push(p);
  }
  return out;
}

const read = (files) => files.map((f) => readFileSync(f, "utf8")).join("\n");

// `tests/test_foo.py` → backend/tests/test_foo.py must exist.
const files = [...new Set([...doc.matchAll(/tests\/(test_[a-z_0-9]+\.py)/g)].map((m) => m[1]))];
// `::test_foo` → some backend test must define it.
const names = [...new Set([...doc.matchAll(/::(test_[a-z_0-9]+)/g)].map((m) => m[1]))];
// `` `FooTest` `` → some Kotlin/Swift test must declare the class.
const classes = [...new Set([...doc.matchAll(/`([A-Z][A-Za-z]*Test)`/g)].map((m) => m[1]))];

const pySrc = read(walk("backend/tests", [".py"]));
const clientSrc = read([
  ...walk("apps/android/app/src/test", [".kt"]),
  ...walk("apps/android/app/src/androidTest", [".kt"]),
  ...walk("apps/ios", [".swift"]),
]);

const problems = [];
for (const f of files) {
  if (!existsSync(join("backend/tests", f))) problems.push(`backend/tests/${f} does not exist`);
}
for (const n of names) {
  if (!pySrc.includes(`def ${n}(`)) problems.push(`no backend test defines ${n}`);
}
for (const c of classes) {
  if (!clientSrc.includes(`class ${c}`)) problems.push(`no client test declares class ${c}`);
}

if (problems.length) {
  console.error(`✗ ${DOC} cites tests that do not exist:\n`);
  for (const p of problems) console.error(`  - ${p}`);
  console.error(
    "\nEither write the test, or point the row at the one that really covers the claim.\n" +
      "Do not delete the citation and leave the claim: that is the state this gate exists to prevent.",
  );
  process.exit(1);
}

console.log(
  `✓ every test cited in ${DOC} exists ` +
    `(${files.length} files, ${names.length} named tests, ${classes.length} client classes).`,
);
