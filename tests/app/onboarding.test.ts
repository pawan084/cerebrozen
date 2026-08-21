import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { beforeEach, describe, expect, it, vi } from "vitest";

const apiMock = vi.fn();
vi.mock("@/lib/api", () => ({ api: (...args: unknown[]) => apiMock(...args) }));

import {
  FEELINGS,
  LANGUAGES,
  REMINDER_TIMES,
  STEP_NAMES,
  applyOnboarding,
  clearDraft,
  freshDraft,
  loadDraft,
  saveDraft,
} from "../../apps/app/lib/onboarding";

const DRAFT_KEY = "cerebro_app_onboarding_draft";

beforeEach(() => {
  window.localStorage.clear();
  apiMock.mockReset();
  apiMock.mockResolvedValue({});
});

describe("private by default", () => {
  it("pre-ticks nothing — consent has to be an action", () => {
    // DPDP "specific and informed": a category shown already switched on is
    // not consent, it is a default someone failed to notice.
    const consent = freshDraft().consent;
    expect(Object.values(consent).every((v) => v === false)).toBe(true);
  });

  it("covers all six categories, the same set the account page carries", () => {
    expect(Object.keys(freshDraft().consent).sort()).toEqual([
      "ai_memory", "journal_memory", "mood_history",
      "model_training", "sleep_history", "voice_storage",
    ].sort());
  });
});

describe("the draft survives a reload", () => {
  it("round-trips what was chosen", () => {
    const d = freshDraft();
    d.feeling = "Stressed and tense";
    d.consent.mood_history = true;
    saveDraft(d);
    const back = loadDraft();
    expect(back.feeling).toBe("Stressed and tense");
    expect(back.consent.mood_history).toBe(true);
  });

  it("gives a brand-new visitor a fresh draft rather than nothing", () => {
    expect(loadDraft()).toEqual(freshDraft());
  });

  it("clears completely", () => {
    saveDraft({ ...freshDraft(), feeling: "Doubting myself" });
    clearDraft();
    expect(loadDraft().feeling).toBeNull();
  });

  it("deep-merges consent so a NEW category defaults to off, not undefined", () => {
    // A draft saved before model_training existed must still yield a boolean
    // for it — private by default. An undefined would also make the toggle
    // uncontrolled, so this is a React bug and a privacy bug at once.
    window.localStorage.setItem(
      DRAFT_KEY,
      JSON.stringify({ feeling: "x", consent: { mood_history: true } }),
    );
    const back = loadDraft();
    expect(back.consent.model_training).toBe(false);
    expect(back.consent.mood_history).toBe(true);
    expect(Object.keys(back.consent)).toHaveLength(6);
  });

  it("never resurrects a consent the saved draft did not grant", () => {
    window.localStorage.setItem(DRAFT_KEY, JSON.stringify({ consent: {} }));
    expect(Object.values(loadDraft().consent).every((v) => v === false)).toBe(true);
  });

  it("falls back to a fresh draft when the stored one is corrupt", () => {
    window.localStorage.setItem(DRAFT_KEY, "{not json");
    expect(loadDraft()).toEqual(freshDraft());
  });
});

describe("carrying the choices to the server", () => {
  it("attests, sets consent, and writes the profile", async () => {
    await applyOnboarding(freshDraft());
    const paths = apiMock.mock.calls.map(([p]) => p);
    expect(paths).toEqual(["/users/me/attest", "/users/me/consent", "/users/me"]);
  });

  it("keeps going when one of the three fails", async () => {
    // Best-effort per call: the funnel has already delivered its value, so a
    // personalization write failing must never block entry into the app.
    apiMock.mockRejectedValueOnce(new Error("attest exploded"));
    await expect(applyOnboarding(freshDraft())).resolves.toBeUndefined();
    expect(apiMock).toHaveBeenCalledTimes(3);
  });

  it("survives all three failing", async () => {
    apiMock.mockRejectedValue(new Error("server down"));
    await expect(applyOnboarding(freshDraft())).resolves.toBeUndefined();
  });

  it("sends exactly the consent the person set, nothing added", async () => {
    const d = freshDraft();
    d.consent.ai_memory = true;
    await applyOnboarding(d);
    const consentCall = apiMock.mock.calls.find(([p]) => p === "/users/me/consent")!;
    expect(JSON.parse(consentCall[1].body)).toEqual(d.consent);
  });

  it("maps a reminder choice onto the email opt-in", async () => {
    // The web has no local notifications, so "Gentle nudges" has to mean
    // something real on the server or it is another choice that changes
    // nothing — the exact fake the timed chips were removed for.
    const on = freshDraft();
    await applyOnboarding(on);
    expect(JSON.parse(apiMock.mock.calls[2][1].body).email_nudges).toBe(true);

    apiMock.mockReset();
    apiMock.mockResolvedValue({});
    await applyOnboarding({ ...freshDraft(), reminder: "No reminders" });
    expect(JSON.parse(apiMock.mock.calls[2][1].body).email_nudges).toBe(false);
  });

  it("never sends an empty language", async () => {
    await applyOnboarding({ ...freshDraft(), languages: [] });
    expect(JSON.parse(apiMock.mock.calls[2][1].body).language).toBe("English");
  });

  it("joins several languages the way the server stores them", async () => {
    await applyOnboarding({ ...freshDraft(), languages: ["Hindi", "Tamil"] });
    expect(JSON.parse(apiMock.mock.calls[2][1].body).language).toBe("Hindi, Tamil");
  });
});

describe("the feeling tap is the whole assessment", () => {
  it("offers six feelings, each with a label and an emoji", () => {
    expect(FEELINGS).toHaveLength(6);
    for (const f of FEELINGS) {
      expect(f.label.trim()).not.toBe("");
      expect(f.emoji.trim()).not.toBe("");
    }
  });

  it("maps every feeling into the shared motivation/goal taxonomy", () => {
    // Server personalization joins on these strings; a feeling with no mapping
    // would produce a plan built from nothing while looking like it worked.
    for (const f of FEELINGS) {
      expect(f.motivation.trim(), `${f.label} has no motivation`).not.toBe("");
      expect(f.goal.trim(), `${f.label} has no goal`).not.toBe("");
    }
  });

  it("does not give two feelings the same goal", () => {
    const goals = FEELINGS.map((f) => f.goal);
    expect(new Set(goals).size).toBe(goals.length);
  });

  it("offers a reminder choice that is on/off only", () => {
    expect(REMINDER_TIMES).toEqual(["Gentle nudges", "No reminders"]);
    expect(LANGUAGES).toContain("English");
  });

  it("defaults the draft language to something the picker actually offers", () => {
    for (const l of freshDraft().languages) expect(LANGUAGES).toContain(l);
  });
});

describe("the step names are the backend's vocabulary", () => {
  const backend = (() => {
    const src = readFileSync(
      resolve(__dirname, "../../backend/app/services/metrics.py"),
      "utf8",
    );
    const block = src.match(/ONBOARDING_STEPS = \[([\s\S]*?)\]/)?.[1] ?? "";
    return [...block.matchAll(/"([a-z_]+)"/g)].map((m) => m[1]);
  })();

  it("could read the canonical list", () => {
    expect(backend.length).toBeGreaterThan(0);
  });

  it("uses only names the funnel chart knows", () => {
    // The admin funnel JOINS on these strings, so a renamed step is not an
    // error — it is a chart quietly missing a bar.
    for (const step of STEP_NAMES) {
      expect(backend, `the backend funnel has no step "${step}"`).toContain(step);
    }
  });

  it("keeps them in the backend's order", () => {
    // The chart orders by the canonical list, so web's eight have to be a
    // subsequence of the ten — reordering here would draw the funnel wrong.
    const positions = STEP_NAMES.map((s) => backend.indexOf(s));
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
  });

  it("is the eight-step funnel, not the backend's ten", () => {
    // age_gate folded into disclosure and the invented first_plan preview is
    // gone. Indexing the ten-name list by the UI's step number is what once
    // labelled step 4 "state_check" when it was "first_reset".
    expect(STEP_NAMES).toHaveLength(8);
    expect(STEP_NAMES).not.toContain("age_gate");
    expect(STEP_NAMES).not.toContain("first_plan");
  });
});
