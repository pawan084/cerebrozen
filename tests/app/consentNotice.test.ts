import { describe, expect, it } from "vitest";

import {
  CONSENT_NOTICE,
  NOTICE_LANGS,
  defaultNoticeLang,
} from "../../apps/app/lib/consentNotice";

// DPDP s.5(3): the consent notice must be accessible in English or an
// Eighth-Schedule language. This is a legal surface, and its failure mode is
// silent — a half-translated language ships a notice with English category
// labels under an Assamese heading, and nobody notices because nobody on the
// team reads all thirteen. Structure is what a test can hold; the words
// themselves are an owner item (professional legal review before 13 May 2027).
const CATEGORIES = [
  "mood_history",
  "ai_memory",
  "journal_memory",
  "sleep_history",
  "voice_storage",
  "model_training",
] as const;

describe("the shipped languages", () => {
  it("includes English and the twelve Eighth-Schedule languages", () => {
    expect(NOTICE_LANGS).toHaveLength(13);
    expect(NOTICE_LANGS[0]).toBe("en");
  });

  it("keys them by ISO code, not by name", () => {
    for (const code of NOTICE_LANGS) expect(code).toMatch(/^[a-z]{2}$/);
  });
});

describe.each(NOTICE_LANGS)("the %s notice is complete", (lang) => {
  const notice = CONSENT_NOTICE[lang];

  it("names itself in its own script", () => {
    // The picker lists nativeName. An empty or English-only one leaves a
    // reader unable to find their own language in the list.
    expect(notice.nativeName.trim()).not.toBe("");
  });

  it.each(["title", "caption", "recommendOn", "recommendOnSub", "recommendOff", "recommendOffSub"] as const)(
    "has a non-empty %s",
    (field) => {
      expect(notice[field].trim()).not.toBe("");
    },
  );

  it("covers all six consent categories", () => {
    expect(Object.keys(notice.categories).sort()).toEqual([...CATEGORIES].sort());
  });

  it.each(CATEGORIES)("gives %s both a label and a hint", (key) => {
    expect(notice.categories[key].label.trim()).not.toBe("");
    expect(notice.categories[key].hint.trim()).not.toBe("");
  });
});

describe("the twelve translations are actually translated", () => {
  // The failure this catches is a copy-paste that left English behind: the
  // structure would be perfect and the notice would be useless to the person
  // it was added for.
  const english = CONSENT_NOTICE.en;
  const translated = NOTICE_LANGS.filter((l) => l !== "en");

  it.each(translated)("%s does not simply repeat the English title", (lang) => {
    expect(CONSENT_NOTICE[lang].title).not.toBe(english.title);
  });

  it.each(translated)("%s translates every category label", (lang) => {
    const same = CATEGORIES.filter(
      (k) => CONSENT_NOTICE[lang].categories[k].label === english.categories[k].label,
    );
    expect(same, `${lang} still shows English for: ${same.join(", ")}`).toEqual([]);
  });
});

describe("picking a default from the app-language step", () => {
  it.each([
    ["Hindi", "hi"],
    ["Punjabi", "pa"],
    ["Tamil", "ta"],
  ])("maps %s to %s", (name, code) => {
    expect(defaultNoticeLang(name)).toBe(code);
  });

  it("accepts the comma string the onboarding step actually produces", () => {
    expect(defaultNoticeLang("Hindi, Punjabi")).toBe("hi");
    expect(defaultNoticeLang(" Punjabi , Tamil ")).toBe("pa");
  });

  it("accepts an array just as happily", () => {
    expect(defaultNoticeLang(["Tamil", "Hindi"])).toBe("ta");
  });

  it("takes the first language it recognises, not the first listed", () => {
    expect(defaultNoticeLang(["Marathi", "Tamil"])).toBe("ta");
  });

  it("maps Hinglish to English, because it reads in Latin script", () => {
    expect(defaultNoticeLang("Hinglish")).toBe("en");
  });

  it("falls back to English rather than to nothing", () => {
    for (const input of ["", [], "Klingon", ["  "]]) {
      expect(defaultNoticeLang(input)).toBe("en");
    }
  });

  it("only ever returns a language that actually ships", () => {
    // A default naming a missing notice would render an empty consent screen —
    // the one screen that must never be blank.
    for (const input of ["Hindi", "Punjabi", "Tamil", "Bengali", "", "anything"]) {
      expect(NOTICE_LANGS).toContain(defaultNoticeLang(input));
    }
  });
});
