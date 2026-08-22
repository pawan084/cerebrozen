import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { BrandMark, Icon } from "../../apps/app/components/icons";

afterEach(cleanup);

const NAMES = Object.keys(Icon) as (keyof typeof Icon)[];

/** Every `Icon.x` the app's shell and screens actually reach for. */
const REFERENCED = (() => {
  const files = [
    "apps/app/app/(authed)/layout.tsx",
    "apps/app/components/AppHeader.tsx",
    "apps/app/components/RitualSteps.tsx",
    "apps/app/components/JourneyPath.tsx",
    "apps/app/components/InterventionCard.tsx",
  ];
  const found = new Set<string>();
  for (const file of files) {
    let src: string;
    try {
      src = readFileSync(resolve(__dirname, "../..", file), "utf8");
    } catch {
      continue; // a component that moved is not this test's business
    }
    for (const m of src.matchAll(/\bIcon\.([A-Za-z]\w*)/g)) found.add(m[1]);
    for (const m of src.matchAll(/\bIcon\[["'](\w+)["']\]/g)) found.add(m[1]);
  }
  return [...found].sort();
})();

describe("every glyph the shell asks for exists", () => {
  // The nav renders `item.icon` resolved from this set. A name that does not
  // exist is `undefined` in JSX, which is React #130 — not a missing icon, a
  // blank screen. The admin dashboard has already been taken down that way
  // once (see tests/admin/icons.test.tsx).
  it("found the references to check", () => {
    expect(REFERENCED.length).toBeGreaterThan(5);
  });

  it.each(REFERENCED)("has %s", (name) => {
    expect(typeof (Icon as Record<string, unknown>)[name]).toBe("function");
  });

  it("covers the five phone tabs by name", () => {
    // Today · Explore · Talk · Journal · You, in the app's own words.
    for (const name of ["home", "search", "talk", "journal", "account"]) {
      expect(typeof (Icon as Record<string, unknown>)[name]).toBe("function");
    }
  });
});

describe("every glyph draws, and draws quietly", () => {
  it.each(NAMES)("renders %s as an svg with real geometry", (name) => {
    const Glyph = Icon[name];
    const { container } = render(<Glyph />);
    const svg = container.querySelector("svg")!;
    expect(svg).toBeTruthy();
    expect(svg.querySelector("path, rect, circle, ellipse")).toBeTruthy();
  });

  it.each(NAMES)("keeps %s out of the accessibility tree", (name) => {
    // Every icon in this app sits beside its own label. Announcing the glyph
    // would read each destination twice, and the support door — a held heart,
    // deliberately not an alarm — would read as an icon name mid-sentence.
    const Glyph = Icon[name];
    const { container } = render(<Glyph />);
    expect(container.querySelector("svg")!.getAttribute("aria-hidden")).toBe("true");
  });

  it.each(NAMES)("draws %s in the surrounding text's colour", (name) => {
    const Glyph = Icon[name];
    const { container } = render(<Glyph />);
    expect(container.querySelector("svg")!.getAttribute("stroke")).toBe("currentColor");
  });
});

describe("size and class pass through", () => {
  it("defaults to 20 and takes an override", () => {
    const { container: dflt } = render(<Icon.home />);
    expect(dflt.querySelector("svg")!.getAttribute("width")).toBe("20");
    const { container: big } = render(<Icon.home size={44} />);
    const svg = big.querySelector("svg")!;
    expect(svg.getAttribute("width")).toBe("44");
    expect(svg.getAttribute("height")).toBe("44");
  });

  it("never distorts, whatever the size", () => {
    const { container } = render(<Icon.journal size={64} />);
    expect(container.querySelector("svg")!.getAttribute("viewBox")).toBe("0 0 24 24");
  });

  it("lets the caller style it, which is how the active tab is coloured", () => {
    const { container } = render(<Icon.talk className="nav-icon on" />);
    expect(container.querySelector("svg")!.getAttribute("class")).toBe("nav-icon on");
  });
});

describe("the two glyphs that are allowed their own colour", () => {
  it("fills the play triangle rather than outlining it", () => {
    // A stroked triangle at 20px reads as a hollow arrow. This one is filled
    // with currentColor and has its stroke removed — still no hex.
    const { container } = render(<Icon.play />);
    const path = container.querySelector("path")!;
    expect(path.getAttribute("fill")).toBe("currentColor");
    expect(path.getAttribute("stroke")).toBe("none");
  });

  it("paints the notification dot from a token, never a raw hex", () => {
    // The one place a colour is named in this file, and it names a CSS
    // variable: iOS reads design tokens only, and web mirrors that rule.
    const { container } = render(<Icon.bellDot />);
    const dot = container.querySelector("circle")!;
    expect(dot.getAttribute("fill")).toBe("var(--warm)");
    expect(container.innerHTML).not.toMatch(/fill="#[0-9a-f]{3,8}"/i);
  });

  it("gives the plain bell no dot at all", () => {
    // bell and bellDot differ by exactly the thing their names promise; a
    // permanent dot would claim unread notifications that do not exist.
    const { container } = render(<Icon.bell />);
    expect(container.querySelector("circle")).toBeNull();
  });
});

describe("the brand mark", () => {
  it("scales from one number without distorting", () => {
    const { container } = render(<BrandMark size={80} />);
    const svg = container.querySelector("svg")!;
    expect(svg.getAttribute("width")).toBe("80");
    expect(svg.getAttribute("height")).toBe("80");
    expect(svg.getAttribute("viewBox")).toBe("4 32 296 296");
  });

  it("defaults to the size the header uses", () => {
    const { container } = render(<BrandMark />);
    expect(container.querySelector("svg")!.getAttribute("width")).toBe("26");
  });

  it("is inline SVG, not a fetched asset", () => {
    const { container } = render(<BrandMark />);
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("use")).toBeNull();
  });

  it("stays out of the reading — the wordmark beside it carries the name", () => {
    const { container } = render(<BrandMark />);
    expect(container.querySelector("svg")!.getAttribute("aria-hidden")).toBe("true");
  });
});
