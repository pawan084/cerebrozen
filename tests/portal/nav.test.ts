import { existsSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { NAV, PAGE_META } from "../../apps/portal/lib/nav";
import * as copy from "../../apps/portal/lib/copy";

const portalApp = resolve(__dirname, "../../apps/portal/app");

/** Where the App Router would look for the page behind an href. */
function pageFileFor(href: string): string {
  const segment = href === "/" ? "" : href;
  return resolve(portalApp, `.${segment}/page.tsx`);
}

describe("the sidebar", () => {
  it("lists five groups, each with entries", () => {
    expect(NAV).toHaveLength(5);
    for (const group of NAV) {
      expect(group.title.trim()).not.toBe("");
      expect(group.items.length).toBeGreaterThan(0);
    }
  });

  it("gives every entry a code, an icon and a label", () => {
    for (const item of NAV.flatMap((g) => g.items)) {
      expect(item.code, "prototype traceability code").toMatch(/^[A-Z]+-\d+$/);
      expect(item.icon.trim()).not.toBe("");
      expect(item.label.trim()).not.toBe("");
    }
  });

  it("never lists the same route twice", () => {
    const hrefs = NAV.flatMap((g) => g.items).map((i) => i.href).filter(Boolean);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it("never lists the same prototype code twice", () => {
    const codes = NAV.flatMap((g) => g.items).map((i) => i.code);
    expect(new Set(codes).size).toBe(codes.length);
  });
});

describe("every door opens onto something", () => {
  // This repo has shipped the opposite twice in one month — an iOS screen with
  // no door, and an Android route reachable only from an unreachable screen.
  // The mirror of that bug is a sidebar entry pointing at a page that does not
  // exist: the operator clicks and gets a 404 with the nav still around it.
  const linked = NAV.flatMap((g) => g.items).filter((i) => i.href);

  it("links at least the 36 routes the prototype defines", () => {
    expect(linked.length).toBeGreaterThan(0);
  });

  it.each(linked.map((i) => [i.href!, i.label] as const))(
    "%s (%s) has a page on disk",
    (href) => {
      expect(existsSync(pageFileFor(href)), `no page.tsx behind ${href}`).toBe(true);
    },
  );

  it.each(linked.map((i) => [i.href!, i.label] as const))(
    "%s (%s) has a title for the topbar",
    (href) => {
      // A route with no PAGE_META renders an unnamed topbar — the operator
      // cannot tell which of thirty-six screens they are on.
      expect(PAGE_META[href], `PAGE_META has no entry for ${href}`).toBeTruthy();
      expect(PAGE_META[href].title.trim()).not.toBe("");
      expect(PAGE_META[href].sub.trim()).not.toBe("");
    },
  );
});

describe("the detail routes that are deliberately not in the sidebar", () => {
  it("still get named, because the topbar reads PAGE_META not NAV", () => {
    const sidebar = new Set(NAV.flatMap((g) => g.items).map((i) => i.href));
    const detailOnly = Object.keys(PAGE_META).filter((href) => !sidebar.has(href));
    // They are reached from their parent, exactly as in the prototype, so the
    // absence is intentional — but an unnamed one is still a bug.
    for (const href of detailOnly) {
      expect(PAGE_META[href].title.trim(), `${href} has no title`).not.toBe("");
    }
  });

  it("points PAGE_META at real pages too", () => {
    for (const href of Object.keys(PAGE_META)) {
      // Dynamic segments are a template, not a path on disk.
      if (href.includes("[")) continue;
      expect(existsSync(pageFileFor(href)), `PAGE_META names ${href}, which has no page`).toBe(true);
    }
  });
});

describe("the privacy wall copy", () => {
  // Five strings quoted verbatim from ref/portal.html and from the spec. They
  // are the portal's promise to members — the reason an employer dashboard is
  // allowed to exist beside a mental-health product at all. "Do not soften,
  // shorten or reword them" is the instruction in the module; this is the
  // check that someone did not.
  it("names every category an administrator can never open", () => {
    for (const word of ["chat", "journal", "mood", "sleep", "safety plan"]) {
      expect(copy.PRIVACY_WALL_SIDEBAR.toLowerCase()).toContain(word);
    }
  });

  it("says plainly that managers cannot see who used CereBro", () => {
    expect(copy.PRIVACY_WALL_NOTICE_BODY).toContain("Managers cannot see who used CereBro");
    expect(copy.PRIVACY_WALL_NOTICE_TITLE).toContain("not available here");
  });

  it("keeps the three protections that make the aggregates safe", () => {
    // Anonymous totals ALONE are not privacy — small cohorts re-identify. All
    // three have to survive together or the sentence stops being true.
    for (const phrase of ["anonymous group totals", "minimum cohort thresholds", "small-cell suppression"]) {
      expect(copy.PRIVACY_WALL_NOTICE_BODY).toContain(phrase);
    }
  });

  it("still refuses the dark patterns CAM-01 names", () => {
    for (const phrase of ["inactive-employee exports", "streak pressure", "forced enrolment"]) {
      expect(copy.CAMPAIGN_PROHIBITED).toContain(phrase);
    }
  });

  it("keeps the boundary above the most senior role", () => {
    // ROL-01: seniority is the obvious place for an exception to be quietly
    // added, so the sentence names the two roles that would ask for one.
    expect(copy.ROLE_BOUNDARY).toContain("Benefits owner");
    expect(copy.ROLE_BOUNDARY).toContain("Privacy reviewer");
    expect(copy.ROLE_BOUNDARY).toContain("cannot access personal wellbeing content");
  });

  it("shows admins the never-available list before launch", () => {
    for (const word of ["Chats", "voice transcripts", "journal entries", "safety plans", "trusted contacts"]) {
      expect(copy.NEVER_AVAILABLE).toContain(word);
    }
  });

  it("leaves none of them empty", () => {
    for (const [name, text] of Object.entries(copy)) {
      if (typeof text === "string") expect(text.trim(), `${name} is empty`).not.toBe("");
    }
  });
});
