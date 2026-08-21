import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { heroKindFor, heroWhy, heroWorksOffline } from "../../apps/app/lib/todayHero";

// A CROSS-STACK CONTRACT, hand-duplicated per CLAUDE.md, whose authority is
// apps/android/.../TodayScreen.kt. The module's own header said "there is no
// unit-test runner in apps/app, so unlike the Android twin these functions are
// only covered by e2e" — that sentence is what this file exists to retire.
describe("which hero to show", () => {
  it("reads a slow fetch as loading, not as 'you have no plan'", () => {
    // The branch ORDER is the contract. Flip the first two and someone opening
    // Home on a bad connection is told they have no plan, then watches it
    // appear — the app calling itself a liar in the first second.
    expect(heroKindFor(false, false, false)).toBe("loading");
  });

  it("shows the fallback once the fetch has actually landed empty", () => {
    expect(heroKindFor(true, false, false)).toBe("fallback");
  });

  it("shows the next step when there is one", () => {
    expect(heroKindFor(true, true, true)).toBe("plan-step");
  });

  it("shows done when the plan is finished", () => {
    expect(heroKindFor(true, true, false)).toBe("plan-done");
  });

  it("trusts a cached plan even before the fetch resolves", () => {
    // hasPlan with planLoaded still false is the offline/cached case: there is
    // something real to show, so showing a spinner over it would be a
    // regression from the person's point of view.
    expect(heroKindFor(false, true, true)).toBe("plan-step");
    expect(heroKindFor(false, true, false)).toBe("plan-done");
  });

  it("never returns anything the renderer does not handle", () => {
    const kinds = new Set(["loading", "fallback", "plan-step", "plan-done"]);
    for (const planLoaded of [true, false])
      for (const hasPlan of [true, false])
        for (const hasNextStep of [true, false])
          expect(kinds.has(heroKindFor(planLoaded, hasPlan, hasNextStep))).toBe(true);
  });
});

describe("what may be badged 'Works offline'", () => {
  it.each(["/games", "/games/imagery", "/games/ritual", "/safety-plan"])(
    "%s genuinely runs with no network",
    (route) => {
      expect(heroWorksOffline(route)).toBe(true);
    },
  );

  it.each([
    ["/sounds", "a soundscape streams unless it was downloaded first"],
    ["/talk", "needs a model on the other end"],
    ["/sleep", "reads the diary from the server"],
    ["/insights/trends", "aggregates the server computes"],
  ])("%s is not badged — %s", (route) => {
    // "A chip that promises offline and then fails on the Mumbai local is worse
    // than no chip." The absences are the point of this set, so they are worth
    // more test lines than the members.
    expect(heroWorksOffline(route)).toBe(false);
  });

  it("does not match on a prefix", () => {
    // Set membership, not startsWith: /games-of-chance is not /games, and a
    // future /safety-plan/edit has not been checked for offline behaviour.
    expect(heroWorksOffline("/games-of-chance")).toBe(false);
    expect(heroWorksOffline("/safety-plan/edit")).toBe(false);
    expect(heroWorksOffline("/games/")).toBe(false);
  });

  it("says no to an unknown route rather than guessing", () => {
    expect(heroWorksOffline("")).toBe(false);
    expect(heroWorksOffline("/whatever-ships-next")).toBe(false);
  });
});

describe("the provenance sentence", () => {
  // "It did not read your journal" is a PRIVACY STATEMENT, and it is only true
  // for the rule generator. The AI planner does see recent journal titles
  // (never bodies, and only under journal_memory consent). A flat claim would
  // be false half the time — the exact class CLAIMS_MAP exists to stop.
  it("promises less when the AI planner wrote the plan", () => {
    const ai = heroWhy("ai");
    expect(ai).toContain("journal titles");
    expect(ai).toContain("did not read the entries themselves");
    expect(ai).not.toContain("did not read your journal");
  });

  it("says the journal was untouched only for the rule generator", () => {
    expect(heroWhy("rule")).toContain("did not read your journal");
  });

  it("falls back to the SAFER sentence when the source is unknown", () => {
    // Unknown source must not accidentally claim more access than was used;
    // but more importantly it must not claim LESS and be wrong. The rule
    // wording is the conservative default the Android twin also takes.
    for (const source of [undefined, null, "", "unknown", "fallback"]) {
      expect(heroWhy(source)).toBe(heroWhy("rule"));
    }
  });

  it("is case-insensitive about the source", () => {
    // The server has sent "ai" and "AI" at different times; a case slip here
    // would silently downgrade a true statement into a false one.
    expect(heroWhy("AI")).toBe(heroWhy("ai"));
    expect(heroWhy("Ai")).toBe(heroWhy("ai"));
  });

  it("matches the Android strings word for word", () => {
    // The hand-duplication CLAUDE.md warns about, checked rather than trusted.
    // Both clients show this sentence under the same hero; if one is edited
    // alone, two users comparing phones see different privacy promises.
    const xml = readFileSync(
      resolve(__dirname, "../../apps/android/app/src/main/res/values/strings.xml"),
      "utf8",
    );
    const stringNamed = (name: string) => {
      const m = xml.match(new RegExp(`<string name="${name}">([\\s\\S]*?)</string>`));
      if (!m) throw new Error(`strings.xml has no ${name} — the contract moved`);
      return m[1];
    };
    expect(heroWhy("rule")).toBe(stringNamed("today_hero_why_rule"));
    expect(heroWhy("ai")).toBe(stringNamed("today_hero_why_ai"));
  });
});
