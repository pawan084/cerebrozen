#!/usr/bin/env node
// Fail CI when a text role in design/tokens.css stops being readable on a ground
// it is allowed to land on. Android (ContrastTest.kt) and iOS (ContrastTest.swift)
// have had this gate for a while; the web side did not, which is how --muted-2
// shipped at 3.9:1 for a month. Run from the repo root:
//   node scripts/check-contrast.mjs
import { readFileSync } from "node:fs";

const SOURCE = "design/tokens.css";
const AA_TEXT = 4.5; // WCAG AA, normal-size text
const AA_FILL = 3.0; // non-text UI boundaries

// A tonal role only has to be readable on the neutral grounds and on its OWN
// wash — amber text never lands on a danger wash, and gating that pairing would
// force the brand colours darker for no real-world benefit.
const NEUTRAL = ["surface", "surface-raised", "surface-field"];
const PAIRINGS = {
  text: [...NEUTRAL, "accent-soft", "ok-soft", "warm-soft", "danger-soft", "amber-soft", "info-soft"],
  "text-secondary": [...NEUTRAL, "accent-soft", "ok-soft", "warm-soft", "danger-soft", "amber-soft", "info-soft"],
  "text-faint": [...NEUTRAL, "accent-soft"],
  accent: [...NEUTRAL, "accent-soft"],
  "accent-2": [...NEUTRAL, "accent-soft"],
  ok: [...NEUTRAL, "ok-soft"],
  warm: [...NEUTRAL, "warm-soft"],
  danger: [...NEUTRAL, "danger-soft"],
  amber: [...NEUTRAL, "amber-soft"],
  info: [...NEUTRAL, "info-soft"],
};
// Label colour that sits on a filled control of that role.
const ON_FILL = { "on-accent": "accent" };

const hexOf = (h) => {
  let s = h.replace("#", "").trim();
  if (s.length === 3) s = s.split("").map((c) => c + c).join("");
  return [0, 2, 4].map((i) => parseInt(s.slice(i, i + 2), 16));
};
const channel = (c) => {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
};
const luminance = (h) => {
  const [r, g, b] = hexOf(h);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
};
const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// Pull each theme block out of the token file and resolve var() indirection.
function themes(css) {
  const out = {};
  const blocks = [
    ["dawn", /:root\s*\{([\s\S]*?)\n\}/],
    ["night", /:root\[data-theme="night"\]\s*\{([\s\S]*?)\n\}/],
  ];
  for (const [name, re] of blocks) {
    const body = css.match(re);
    if (!body) {
      console.error(`${SOURCE}: could not find the ${name} theme block`);
      process.exit(1);
    }
    const vars = {};
    for (const [, k, v] of body[1].matchAll(/--([\w-]+)\s*:\s*([^;]+);/g)) {
      vars[k] = v.trim();
    }
    out[name] = vars;
  }
  // Night inherits anything it does not restate.
  out.night = { ...out.dawn, ...out.night };
  for (const vars of Object.values(out)) {
    for (const k of Object.keys(vars)) {
      let seen = 0;
      while (vars[k]?.startsWith("var(--") && seen++ < 10) {
        vars[k] = vars[vars[k].slice(6, vars[k].indexOf(")"))] ?? vars[k];
      }
    }
  }
  return out;
}

const css = readFileSync(SOURCE, "utf8");
const resolved = themes(css);
const failures = [];
let checked = 0;

for (const [themeName, vars] of Object.entries(resolved)) {
  for (const [role, grounds] of Object.entries(PAIRINGS)) {
    const fg = vars[role];
    if (!fg?.startsWith("#")) continue;
    for (const ground of grounds) {
      const bg = vars[ground];
      if (!bg?.startsWith("#")) continue;
      checked++;
      const r = contrast(fg, bg);
      if (r < AA_TEXT) {
        failures.push(`${themeName}: --${role} (${fg}) on --${ground} (${bg}) = ${r.toFixed(2)}, needs ${AA_TEXT}`);
      }
    }
  }
  for (const [label, fill] of Object.entries(ON_FILL)) {
    const fg = vars[label];
    const bg = vars[fill];
    if (!fg?.startsWith("#") || !bg?.startsWith("#")) continue;
    checked++;
    const r = contrast(fg, bg);
    if (r < AA_TEXT) {
      failures.push(`${themeName}: --${label} (${fg}) on filled --${fill} (${bg}) = ${r.toFixed(2)}, needs ${AA_TEXT}`);
    }
  }
  // The hairline has to be visible against the grounds it separates.
  for (const ground of NEUTRAL) {
    const l = vars.line;
    const bg = vars[ground];
    if (!l?.startsWith("#") || !bg?.startsWith("#")) continue;
    checked++;
    const r = contrast(l, bg);
    if (r < 1.2) {
      failures.push(`${themeName}: --line (${l}) on --${ground} (${bg}) = ${r.toFixed(2)}, too faint to read as a boundary`);
    }
  }
}

if (failures.length) {
  console.error(`contrast: ${failures.length} of ${checked} pairings fail\n`);
  for (const f of failures) console.error(`  ${f}`);
  process.exit(1);
}
console.log(`contrast: ${checked} pairings pass (AA ${AA_TEXT}:1 text, ${AA_FILL}:1 fills)`);
