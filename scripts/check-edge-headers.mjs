#!/usr/bin/env node
// Security headers set at the EDGE, in deploy/Caddyfile (WC-87).
//
//   node scripts/check-edge-headers.mjs   # exit 1 if a site block loses them
//
// This gate exists because of a gap, not a preference. The e2e suite runs the
// four Next apps and the API directly — there is no Caddy in the stack — so
// every header in the Caddyfile is invisible to every test in this repo. A
// dropped `import security_headers` would pass CI, pass review (it is one
// deleted line in a file nobody reads on a feature branch), and ship. The only
// evidence would be an HSTS header quietly missing in production.
//
// Its companion `check-csp-sync.mjs` pins the CSP the four Next middlewares
// emit; `e2e/tests/security-headers.spec.ts` proves at runtime that those
// headers are actually served and that the nonce really is per-request. This
// file covers the third tier, the one nothing else can reach.
import { readFileSync } from "node:fs";

const CADDYFILE = "deploy/Caddyfile";
const SNIPPET = "security_headers";

// Header → a test for its value. Each is here because losing it is silent.
const REQUIRED_IN_SNIPPET = [
  [
    "Strict-Transport-Security",
    (v) => {
      const maxAge = Number(v.match(/max-age=(\d+)/)?.[1] ?? 0);
      // A year. Below that, preload lists reject the domain and the header is
      // mostly decorative.
      return maxAge >= 31536000;
    },
    "must set max-age of at least a year (31536000)",
  ],
  ["X-Content-Type-Options", (v) => v.includes("nosniff"), 'must be "nosniff"'],
  ["X-Frame-Options", (v) => /DENY|SAMEORIGIN/.test(v), "must be DENY or SAMEORIGIN"],
  [
    "Referrer-Policy",
    (v) => /no-referrer|strict-origin/.test(v),
    "must not leak full URLs cross-origin",
  ],
  [
    "Permissions-Policy",
    (v) => /camera=\(\)/.test(v) && /microphone=\(\)/.test(v),
    "must deny camera and microphone by default",
  ],
];

const src = readFileSync(CADDYFILE, "utf8");
// Comments are not configuration. The portal block is commented out on purpose
// (no auth in front of it yet) and must not be read as a live site missing its
// headers — nor as cover for one.
const lines = src.split("\n").filter((l) => !/^\s*#/.test(l));

const problems = [];

// ── 1. The snippet still defines everything ──────────────────────────────
const snippetStart = lines.findIndex((l) => l.includes(`(${SNIPPET})`));
if (snippetStart === -1) {
  problems.push(`${CADDYFILE}: the (${SNIPPET}) snippet is gone entirely.`);
} else {
  let depth = 0;
  let end = snippetStart;
  for (let i = snippetStart; i < lines.length; i++) {
    depth += (lines[i].match(/{/g) ?? []).length;
    depth -= (lines[i].match(/}/g) ?? []).length;
    if (depth === 0 && i > snippetStart) {
      end = i;
      break;
    }
  }
  const body = lines.slice(snippetStart, end + 1).join("\n");
  for (const [header, valid, why] of REQUIRED_IN_SNIPPET) {
    const line = body.split("\n").find((l) => l.trim().startsWith(header));
    if (!line) {
      problems.push(`(${SNIPPET}) no longer sets ${header}.`);
    } else if (!valid(line)) {
      problems.push(`(${SNIPPET}) ${header}: ${why} — got: ${line.trim()}`);
    }
  }
}

// ── 2. Every live site block imports it ──────────────────────────────────
// A site block is a top-level `address... {`. Skipped: the global options block
// (`{` with no address) and the snippet itself (`(name) {`).
const sites = [];
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (!/{\s*$/.test(line) || /^\s/.test(line)) continue;
  const address = line.replace(/{\s*$/, "").trim();
  if (!address || address.startsWith("(")) continue;

  let depth = 0;
  const body = [];
  for (let j = i; j < lines.length; j++) {
    depth += (lines[j].match(/{/g) ?? []).length;
    depth -= (lines[j].match(/}/g) ?? []).length;
    body.push(lines[j]);
    if (depth === 0 && j > i) break;
  }
  sites.push({ address, body: body.join("\n") });
}

if (sites.length === 0) {
  problems.push(`${CADDYFILE}: parsed no site blocks at all — has the format changed?`);
}

for (const site of sites) {
  // A block that only redirects serves no content of its own. Exempted
  // deliberately and named here rather than skipped silently, so the choice is
  // visible: if one of these ever grows a body, it stops being exempt.
  const redirectOnly =
    /\bredir\b/.test(site.body) && !/reverse_proxy|file_server|respond/.test(site.body);
  if (redirectOnly) continue;

  if (!site.body.includes(`import ${SNIPPET}`)) {
    problems.push(
      `${site.address}: serves content without \`import ${SNIPPET}\` — ` +
        "no HSTS, no nosniff, no framing or referrer policy on any response.",
    );
  }
}

// ── 3. The API keeps its own CSP ─────────────────────────────────────────
// FastAPI renders no documents, so this is defence in depth against
// content-type confusion; it is also the only CSP Caddy still sets, the Next
// apps having moved theirs into middleware.
const api = sites.find((s) => s.address.startsWith("api."));
if (!api) {
  problems.push(`${CADDYFILE}: no api. site block found.`);
} else if (!/Content-Security-Policy\s+"default-src 'none'/.test(api.body)) {
  problems.push(`${api.address}: lost its locked-down Content-Security-Policy.`);
}

if (problems.length > 0) {
  console.error("Edge security headers are wrong in deploy/Caddyfile:\n");
  for (const problem of problems) console.error(`  · ${problem}`);
  console.error(
    "\n  Nothing else in this repo can catch this. The e2e stack runs the apps\n" +
      "  directly with no Caddy in front, so these headers exist in production\n" +
      "  and nowhere a test can see them.",
  );
  process.exit(1);
}

const checked = sites.map((s) => s.address).join(", ");
console.log(`Edge headers hold across ${sites.length} site block(s): ${checked}`);
