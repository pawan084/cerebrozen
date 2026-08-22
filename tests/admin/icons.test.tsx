import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { BrandMark, Icon } from "../../apps/admin/components/icons";

afterEach(cleanup);

/** The dashboard's tab keys, read from the page that renders them. */
const TAB_KEYS = (() => {
  const src = readFileSync(resolve(__dirname, "../../apps/admin/app/page.tsx"), "utf8");
  const block = src.match(/const TABS: \{ key: Tab; label: string \}\[\] = \[([\s\S]*?)\n\];/)![1];
  return Array.from(block.matchAll(/key: "([^"]+)"/g)).map((m) => m[1]);
})();

describe("every tab has a glyph", () => {
  // Not a style rule — an outage. The tab bar renders `Icon[t.key]` directly,
  // so a key with no entry evaluates to undefined, React throws #130, and the
  // WHOLE dashboard dies on first paint. The "media" tab shipped without one
  // and took down every admin screen; nothing but a comment stopped it
  // happening again.
  it("finds a tab list to check", () => {
    expect(TAB_KEYS.length).toBeGreaterThan(5);
    expect(TAB_KEYS).toContain("media");
  });

  it.each(TAB_KEYS)("has a glyph for the %s tab", (key) => {
    expect(typeof Icon[key]).toBe("function");
  });

  it.each(TAB_KEYS)("renders the %s glyph as an svg rather than throwing", (key) => {
    const Glyph = Icon[key];
    const { container } = render(<Glyph />);
    const svg = container.querySelector("svg")!;
    expect(svg).toBeTruthy();
    // The failure mode being guarded is a component that returns undefined, so
    // "it rendered something" is the assertion that matters.
    expect(svg.querySelector("path, rect, circle, ellipse")).toBeTruthy();
  });
});

describe("the icons are decoration", () => {
  // Each one sits beside its own text label in the tab bar. Announcing the
  // glyph too would read every destination twice.
  it.each(Object.keys(Icon))("hides the %s glyph from the accessibility tree", (key) => {
    const Glyph = Icon[key];
    const { container } = render(<Glyph />);
    expect(container.querySelector("svg")!.getAttribute("aria-hidden")).toBe("true");
  });

  it("hides the brand mark too", () => {
    const { container } = render(<BrandMark />);
    expect(container.querySelector("svg")!.getAttribute("aria-hidden")).toBe("true");
  });
});

describe("the glyphs take their colour from the text around them", () => {
  // `stroke="currentColor"` and no fill: an icon set with baked-in hex would
  // stay indigo through a palette change, and raw hex in a component is the
  // thing the design-token rule exists to prevent.
  it.each(Object.keys(Icon))("draws the %s glyph with currentColor", (key) => {
    const Glyph = Icon[key];
    const { container } = render(<Glyph />);
    const svg = container.querySelector("svg")!;
    expect(svg.getAttribute("stroke")).toBe("currentColor");
    expect(svg.getAttribute("fill")).toBe("none");
  });

  it("carries no raw hex in any nav glyph", () => {
    for (const key of Object.keys(Icon)) {
      const Glyph = Icon[key];
      const { container } = render(<Glyph />);
      expect(container.innerHTML).not.toMatch(/#[0-9a-f]{3,8}\b/i);
      cleanup();
    }
  });
});

describe("the brand mark", () => {
  it("scales from one number", () => {
    const { container } = render(<BrandMark size={64} />);
    const svg = container.querySelector("svg")!;
    expect(svg.getAttribute("width")).toBe("64");
    expect(svg.getAttribute("height")).toBe("64");
  });

  it("keeps its viewBox when it scales, so it never distorts", () => {
    const { container: small } = render(<BrandMark size={16} />);
    const { container: large } = render(<BrandMark size={200} />);
    expect(small.querySelector("svg")!.getAttribute("viewBox")).toBe(
      large.querySelector("svg")!.getAttribute("viewBox"),
    );
  });

  it("is drawn inline rather than fetched", () => {
    // CSP-clean and same-origin: no icon font, no remote sprite. An <img> here
    // would be a request from the admin dashboard to somewhere else.
    const { container } = render(<BrandMark />);
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("use")).toBeNull();
    expect(container.querySelector("svg > circle")).toBeTruthy();
  });
});
