import { test, expect, Page } from "@playwright/test";

/**
 * Layout that survives the width people actually hold (WEB gap, 2026-08-23).
 *
 * Nothing in this suite checked a narrow viewport, and the failures that would
 * have shown up there are exactly the ones this project keeps meeting: text
 * clipped mid-word, a card wider than the screen, a page you can drag sideways.
 * They never look like errors — the build is green, the page renders, and the
 * report arrives later as "it looks wrong".
 *
 * So these assert three mechanical things at four widths, and nothing about
 * taste:
 *
 *   1. the page does not scroll sideways
 *   2. no element sticks out past the right edge
 *   3. no text is cut off by its own box
 *
 * **Legitimately scrollable content is excluded, not special-cased away.** The
 * house rule is that wide things (tables, code, diagrams) live in their own
 * `overflow-x: auto` container, so anything inside such an ancestor is doing
 * the right thing and is skipped. An element that overflows with no scrollable
 * parent is the bug this file is for.
 */

const WIDTHS = [
  { name: "small phone", width: 360, height: 740 },
  { name: "phone", width: 390, height: 844 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "desktop", width: 1280, height: 800 },
];

const WEB = process.env.WEB_URL || "http://web:3000";

/** Every page the marketing site serves, which is every page a stranger sees. */
const PAGES = [
  "/", "/security", "/privacy", "/terms", "/refunds", "/subprocessors",
  "/safety", "/accessibility", "/support", "/organizations", "/delete-account",
];

type Offender = { tag: string; cls: string; text: string; detail: string };

/** Runs in the page: find anything overflowing that has no business doing so. */
async function findLayoutBreaks(page: Page) {
  return page.evaluate(() => {
    const scrollableAncestor = (el: Element): boolean => {
      let n: Element | null = el.parentElement;
      while (n && n !== document.body) {
        const o = getComputedStyle(n).overflowX;
        if (o === "auto" || o === "scroll") return true;
        n = n.parentElement;
      }
      return false;
    };
    const describe = (el: Element, detail: string) => ({
      tag: el.tagName.toLowerCase(),
      cls: (el.className || "").toString().split(" ").filter(Boolean)[0] ?? "",
      text: (el as HTMLElement).innerText?.trim().replace(/\s+/g, " ").slice(0, 50) ?? "",
      detail,
    });

    const vw = document.documentElement.clientWidth;
    const past: ReturnType<typeof describe>[] = [];
    const clipped: ReturnType<typeof describe>[] = [];

    for (const el of Array.from(document.body.querySelectorAll("*"))) {
      const he = el as HTMLElement;
      if (he.offsetParent === null && getComputedStyle(he).position !== "fixed") continue;
      if (scrollableAncestor(el)) continue;

      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;

      // Decorative art is allowed to bleed past the edge, and says so about
      // itself: `aria-hidden`, no text, nothing to click. The landing's
      // `.orb-art` blobs sit at `right: -48px` with `pointer-events: none` on
      // purpose. What is NOT allowed is a readable or clickable thing off the
      // edge — that is the case this file exists for, and the nav link it
      // caught had both text and an href.
      const interactive = el.matches("a, button, input, select, textarea, [role=button], [tabindex]");
      const readable = ((el as HTMLElement).innerText ?? "").trim().length > 0;
      const declaredDecorative =
        el.closest("[aria-hidden='true']") !== null ||
        getComputedStyle(he).pointerEvents === "none";
      const matters = interactive || (readable && !declaredDecorative);

      // 1px of tolerance: sub-pixel rounding is not a layout bug.
      if (matters && r.right > vw + 1) {
        past.push(describe(el, `right=${Math.round(r.right)} > viewport=${vw}`));
      }
      // Text cut off by its own box. `overflow: hidden` with an ellipsis is a
      // deliberate choice and reads the same to this check, so only flag it
      // when nothing is doing the ellipsis — otherwise every truncated label
      // in the product is a false positive.
      const cs = getComputedStyle(he);
      const deliberate =
        cs.textOverflow === "ellipsis" || cs.webkitLineClamp !== "none";
      if (!deliberate && he.scrollWidth > he.clientWidth + 1 && he.clientWidth > 0) {
        const leaf = !Array.from(el.children).some(
          (c) => (c as HTMLElement).innerText?.trim(),
        );
        if (leaf) clipped.push(describe(el, `content=${he.scrollWidth} > box=${he.clientWidth}`));
      }
    }

    return {
      sideways: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      past: past.slice(0, 6),
      clipped: clipped.slice(0, 6),
    };
  });
}

function report(where: string, kind: string, items: Offender[]): string {
  return [
    `${where}: ${items.length} element(s) ${kind}`,
    ...items.map((o) => `  <${o.tag}${o.cls ? "." + o.cls : ""}> ${o.detail}  "${o.text}"`),
  ].join("\n");
}

for (const vp of WIDTHS) {
  test.describe(`${vp.name} (${vp.width}px)`, () => {
    for (const path of PAGES) {
      test(`${path} lays out inside the viewport`, async ({ page }) => {
        await page.setViewportSize({ width: vp.width, height: vp.height });
        await page.goto(`${WEB}${path}`, { waitUntil: "domcontentloaded" });
        // Webfonts change metrics, and a check that runs before they land
        // measures a layout the user never sees.
        await page.evaluate(() => document.fonts?.ready);

        const r = await findLayoutBreaks(page);

        expect(
          r.sideways,
          `${path} scrolls sideways at ${vp.width}px (${r.scrollWidth} > ${r.clientWidth}) — ` +
            `something is wider than the screen`,
        ).toBe(false);
        expect(r.past.length, report(path, "past the right edge", r.past)).toBe(0);
        expect(r.clipped.length, report(path, "with text cut off", r.clipped)).toBe(0);
      });
    }
  });
}
