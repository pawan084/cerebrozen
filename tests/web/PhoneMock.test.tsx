import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { PhoneMock, type MockKind } from "../../apps/web/components/PhoneMock";

afterEach(cleanup);

const KINDS: MockKind[] = ["today", "sleep", "journal"];

function mock(kind: MockKind) {
  const { container } = render(<PhoneMock kind={kind} />);
  return container;
}

const activeTabIn = (container: HTMLElement) =>
  Array.from(container.querySelectorAll(".pm-tab.on")).map((t) => t.textContent);

describe("a mock is decoration, and says so", () => {
  // "A screen reader stepping through fake UI text would learn nothing true."
  // The caption beside it, written by the caller, is the accessible content —
  // these are drawings of a product, not the product.
  it.each(KINDS)("hides the %s mock from the accessibility tree", (kind) => {
    expect(mock(kind).firstElementChild!.getAttribute("aria-hidden")).toBe("true");
  });

  it.each(KINDS)("puts the %s mock's invented copy behind that one attribute", (kind) => {
    // Everything inside is fake product text, so nothing inside may carry its
    // own aria-label re-exposing it — hiding the wrapper is only true while the
    // contents stay silent.
    const container = mock(kind);
    expect(container.querySelectorAll("[aria-label]")).toHaveLength(0);
    expect(container.querySelectorAll('[aria-hidden="false"]')).toHaveLength(0);
  });
});

describe("the mocked tab bar is the app's actual tab bar", () => {
  // This component exists BECAUSE the baked renders in public/screens drifted:
  // they still carry the old tab set (Home · Sleep · …) from the indigo build.
  // A drawn mock that drifts the same way is the same problem with extra steps,
  // so the five tabs are compared against apps/app's own MOBILE array.
  const appTabs = (() => {
    const src = readFileSync(
      resolve(__dirname, "../../apps/app/app/(authed)/layout.tsx"),
      "utf8",
    );
    const block = src.match(/const MOBILE = \[([\s\S]*?)\n\];/)![1];
    return Array.from(block.matchAll(/label: "([^"]+)"/g)).map((m) => m[1]);
  })();

  it("reads the app's five tabs, in the app's order", () => {
    expect(appTabs).toEqual(["Today", "Explore", "Talk", "Journal", "You"]);
    const tabs = Array.from(mock("today").querySelectorAll(".pm-tab")).map((t) => t.textContent);
    expect(tabs).toEqual(appTabs);
  });

  it.each(KINDS)("shows the same five on the %s mock", (kind) => {
    const tabs = Array.from(mock(kind).querySelectorAll(".pm-tab")).map((t) => t.textContent);
    expect(tabs).toEqual(appTabs);
  });

  it("does not show Sleep as a destination of its own", () => {
    // Sleep left the tab bar for Explore — the ruling in REDESIGN_V2.md §6, and
    // the single most visible difference from the stale screenshots.
    expect(appTabs).not.toContain("Sleep");
    for (const kind of KINDS) {
      const tabs = Array.from(mock(kind).querySelectorAll(".pm-tab")).map((t) => t.textContent);
      expect(tabs).not.toContain("Sleep");
      cleanup();
    }
  });

  it.each([
    ["today" as const, "Today"],
    ["journal" as const, "Journal"],
    // The wind-down screen lives UNDER Explore, so that is the tab a phone
    // would be showing as active while it is open.
    ["sleep" as const, "Explore"],
  ])("highlights the tab the %s screen actually sits under", (kind, tab) => {
    expect(activeTabIn(mock(kind))).toEqual([tab]);
  });
});

describe("each kind draws its own screen", () => {
  it("shows the check-in on Today, and nothing from the other two", () => {
    const container = mock("today");
    expect(container.textContent).toContain("How are you, really?");
    expect(container.textContent).not.toContain("Rain over quiet hills");
    expect(container.textContent).not.toContain("Today’s prompt");
  });

  it("shows the sleep story and the mixer on the sleep mock", () => {
    const container = mock("sleep");
    expect(container.textContent).toContain("Rain over quiet hills");
    expect(container.querySelectorAll(".pm-meter > span")).toHaveLength(8);
    expect(container.firstElementChild!.className).toContain("pm-dark");
  });

  it("keeps the journal's privacy promise on the journal mock", () => {
    // The mock illustrates a real control — "you choose what the AI reads" is
    // the journal's actual private mode, not decoration invented for a poster.
    const container = mock("journal");
    expect(container.textContent).toContain("Private mode · you choose what the AI reads");
  });

  it("draws the light frame for everything but sleep", () => {
    expect(mock("today").firstElementChild!.className).not.toContain("pm-dark");
    cleanup();
    expect(mock("journal").firstElementChild!.className).not.toContain("pm-dark");
  });
});

describe("the mocks make no claim the product will not keep", () => {
  // The reason these replaced the baked renders: one of those shows
  // "3-day streak · beautifully done" — a milestone celebration this page and
  // the product spec both rule out, and `scripts/check-claims.mjs` polices the
  // surrounding vocabulary elsewhere. A drawn mock can reintroduce it just as
  // easily as a screenshot could.
  const BANNED = [/streak/i, /\bday[s]? in a row\b/i, /beautifully done/i, /well done/i, /badge/i];

  it.each(KINDS)("keeps the %s mock free of streak and milestone copy", (kind) => {
    const text = mock(kind).textContent ?? "";
    for (const pattern of BANNED) {
      expect(text).not.toMatch(pattern);
    }
  });

  it("suggests, rather than instructing, where it offers something", () => {
    // "Suggested because you said today felt wired" — the mock shows the app
    // giving its reason, which is the product's actual rule for interventions.
    expect(mock("today").textContent).toContain("Suggested because");
  });

  it("always leaves a way past the suggestion", () => {
    // An offer with no refusal is an instruction. The real Today screen carries
    // this row; a mock that dropped it would be selling a different product.
    expect(mock("today").textContent).toContain("Something else instead");
  });
});
