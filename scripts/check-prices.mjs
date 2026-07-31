#!/usr/bin/env node
/*
 * Fails (exit 1) if any client quotes a subscription price that does not match
 * apps/ios/CereBro/Products.storekit — the declaration of what a user is
 * actually charged.
 *
 * Written 2026-07-31 after the Premium audit: the Android paywall advertised
 * ₹399/month and ₹2,999/year against a real ₹499 and ₹3,999, and computed
 * "Save 37%" from the wrong pair. Every other surface — StoreKit, iOS, the
 * landing page — agreed with each other; only Android had drifted, and nothing
 * could notice, because prices are hand-written strings in four places.
 *
 * Deliberately narrow. It does NOT try to parse marketing copy: it collects the
 * price-shaped tokens (₹N or ₹N,NNN) that appear in each surface's pricing copy
 * and asserts every one of them is a price the catalogue actually contains.
 * Quoting a real price in the wrong place is a judgement call; quoting a number
 * that is not in the catalogue at all is always a bug.
 */
import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const storekitPath = resolve(root, "apps/ios/CereBro/Products.storekit");

if (!existsSync(storekitPath)) {
  console.error(`✗ ${storekitPath} is missing — prices cannot be checked.`);
  process.exit(1);
}

const storekit = JSON.parse(readFileSync(storekitPath, "utf8"));

/** Every monthly price, and every annual price, the store actually charges. */
const catalogue = new Set();
for (const group of storekit.subscriptionGroups ?? []) {
  for (const sub of group.subscriptions ?? []) {
    const price = Number(sub.displayPrice);
    if (!Number.isFinite(price)) continue;
    catalogue.add(Math.round(price));
  }
}
if (catalogue.size === 0) {
  console.error("✗ Products.storekit declared no prices — refusing to pass vacuously.");
  process.exit(1);
}

// Surfaces that quote a price to a user, and the lines worth reading in each.
const SURFACES = [
  { file: "apps/android/app/src/main/res/values/strings.xml", match: /premium_(annual|monthly)_price/ },
  { file: "apps/android/app/src/main/res/values-hi/strings.xml", match: /premium_(annual|monthly)_price/ },
  { file: "apps/web/app/page.tsx", match: /amount:/ },
];

const violations = [];
for (const { file, match } of SURFACES) {
  const abs = resolve(root, file);
  if (!existsSync(abs)) continue;
  readFileSync(abs, "utf8")
    .split("\n")
    .forEach((line, i) => {
      if (!match.test(line)) return;
      for (const raw of line.match(/₹\s?[\d,]+/g) ?? []) {
        const value = Number(raw.replace(/[₹,\s]/g, ""));
        // ₹0 is the free tier — a real thing to advertise, and never a store product.
        if (value !== 0 && !catalogue.has(value)) {
          violations.push({ file, line: i + 1, raw: raw.trim() });
        }
      }
    });
}

if (violations.length) {
  console.error("✗ Prices that are not in Products.storekit:");
  for (const v of violations) console.error(`  ${v.file}:${v.line}  ${v.raw}`);
  console.error(
    `\nThe catalogue charges: ${[...catalogue].sort((a, b) => a - b).map((n) => `₹${n}`).join(", ")}.`,
  );
  console.error("Fix the copy, or update Products.storekit if the price really changed.");
  process.exit(1);
}
console.log(`✓ Every quoted price matches Products.storekit (${[...catalogue].sort((a, b) => a - b).map((n) => `₹${n}`).join(", ")}).`);
