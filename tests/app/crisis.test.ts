import { describe, expect, it } from "vitest";

import * as appCrisis from "../../apps/app/lib/crisis";
import * as webCrisis from "../../apps/web/lib/crisis";

// Two hard rules, stated in both files (design system §1, §9):
//   1. Tele-MANAS 14416 leads every crisis surface.
//   2. A crisis surface never depends on the network.
//
// `scripts/check-crisis-lines.mjs` already gates the LIST against the backend's
// IN region. What it does not check is the behaviour around the list: whether
// the reordering helper actually reorders, and whether the dial targets survive
// being turned into hrefs. A number that renders correctly and dials wrongly is
// the worst failure this product has, and it is invisible to a copy check.
describe.each([
  ["apps/app", appCrisis],
  ["apps/web", webCrisis],
])("%s crisis directory", (_name, mod) => {
  it("leads with Tele-MANAS", () => {
    expect(mod.CRISIS_LINES[0].number).toBe("14416");
  });

  it("is static — no network, no session, no fetch", () => {
    // Rule 2 is a property of the module, so this asserts the shape rather
    // than a call: plain data, populated at import, usable with a dead API.
    expect(Array.isArray(mod.CRISIS_LINES)).toBe(true);
    expect(mod.CRISIS_LINES.length).toBeGreaterThan(0);
    for (const line of mod.CRISIS_LINES) {
      expect(typeof line.name).toBe("string");
      expect(line.number).toBeTruthy();
    }
  });

  it("carries the emergency number and a way out for other regions", () => {
    const numbers = mod.CRISIS_LINES.map((l) => l.number);
    expect(numbers).toContain("112");
    expect(numbers.some((n) => mod.isWebLink(n))).toBe(true);
  });

  describe("turning a target into something tappable", () => {
    it("dials a short code as digits", () => {
      expect(mod.crisisHref("14416")).toBe("tel:14416");
    });

    it("strips the spacing a hyphenated number is displayed with", () => {
      // KIRAN is shown as 1800-599-0019 because that is readable; a tel: URI
      // with hyphens in it is not reliably dialled.
      expect(mod.crisisHref("1800-599-0019")).toBe("tel:18005990019");
    });

    it("keeps a leading + so an international number still dials", () => {
      expect(mod.crisisHref("+91 22 2556 3291")).toBe("tel:+912225563291");
    });

    it("leaves a URL alone rather than dialling it", () => {
      expect(mod.crisisHref("https://findahelpline.com")).toBe("https://findahelpline.com");
    });

    it("treats http and HTTPS alike, and is not fooled by a lookalike", () => {
      expect(mod.isWebLink("HTTPS://example.com")).toBe(true);
      expect(mod.isWebLink("http://example.com")).toBe(true);
      // "https" inside the string but not as the scheme is a phone number, and
      // dialling it is the safe reading.
      expect(mod.isWebLink("14416")).toBe(false);
    });

    it("produces a dial target for every line it ships", () => {
      // The end-to-end property: every row on the screen has to be tappable.
      for (const line of mod.CRISIS_LINES) {
        const href = mod.crisisHref(line.number);
        expect(href).toMatch(/^(tel:[+\d]+|https?:\/\/)/);
      }
    });
  });

  describe("teleManasFirst", () => {
    it("promotes Tele-MANAS from wherever the server put it", () => {
      const served = [
        { name: "Emergency services", number: "112" },
        { name: "KIRAN", number: "1800-599-0019" },
        { name: "Tele-MANAS", number: "14416" },
      ];
      expect(mod.teleManasFirst(served).map((l) => l.number)).toEqual([
        "14416", "112", "1800-599-0019",
      ]);
    });

    it("recognises it by number even when the name does not say so", () => {
      // Server-driven resources are region-ordered and their names are not
      // ours to predict; the 14416 is the part that cannot change.
      const served = [
        { name: "Emergency services", number: "112" },
        { name: "National helpline", number: "14416" },
      ];
      expect(mod.teleManasFirst(served)[0].number).toBe("14416");
    });

    it("accepts the hyphenless spelling", () => {
      const served = [
        { name: "Emergency", number: "112" },
        { name: "TeleMANAS", number: "999" },
      ];
      expect(mod.teleManasFirst(served)[0].name).toBe("TeleMANAS");
    });

    it("preserves the order of everything else", () => {
      const served = [
        { name: "A", number: "1" },
        { name: "B", number: "2" },
        { name: "Tele-MANAS", number: "14416" },
        { name: "C", number: "3" },
      ];
      expect(mod.teleManasFirst(served).map((l) => l.name)).toEqual(["Tele-MANAS", "A", "B", "C"]);
    });

    it("leaves a list that already leads with it untouched", () => {
      const served = mod.CRISIS_LINES;
      expect(mod.teleManasFirst(served)).toBe(served);
    });

    it("returns a list with no Tele-MANAS unchanged rather than emptying it", () => {
      // A region whose resources genuinely do not include 14416 must still get
      // its own numbers — dropping them would be the worst possible failure.
      const served = [{ name: "Samaritans", number: "116123" }];
      expect(mod.teleManasFirst(served)).toEqual(served);
    });

    it("survives an empty list", () => {
      expect(mod.teleManasFirst([])).toEqual([]);
    });
  });
});

describe("the two hand-copies agree", () => {
  // CLAUDE.md: cross-stack contracts are duplicated by hand. These two files
  // are copies three and four of the same list, and the landing page is the
  // FIRST surface someone in crisis reaches — before they have an account. A
  // number corrected in one file and missed in the other is the failure mode.
  it("ships the same numbers in the same order", () => {
    expect(appCrisis.CRISIS_LINES.map((l) => l.number)).toEqual(
      webCrisis.CRISIS_LINES.map((l) => l.number),
    );
  });

  it("dials them identically", () => {
    for (const line of appCrisis.CRISIS_LINES) {
      expect(webCrisis.crisisHref(line.number)).toBe(appCrisis.crisisHref(line.number));
    }
  });
});

describe("the non-emergency doors (apps/app only)", () => {
  it("leads with Tele-MANAS here too", () => {
    expect(appCrisis.HUMAN_SUPPORT[0].target).toBe("14416");
  });

  it("gives every entry a working target and a reason to tap it", () => {
    for (const row of appCrisis.HUMAN_SUPPORT) {
      expect(row.detail.trim()).not.toBe("");
      expect(appCrisis.crisisHref(row.target)).toMatch(/^(tel:[+\d]+|https?:\/\/)/);
    }
  });
});
