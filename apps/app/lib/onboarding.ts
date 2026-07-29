// Data + persistence for the web onboarding funnel — a faithful, web-adapted
// port of the iOS OnboardingFlow (value-first: a felt benefit before the account
// ask). The step names are a cross-stack contract with the iOS funnel and
// backend metrics.ONBOARDING_STEPS — keep the list and order in sync.

import { api } from "@/lib/api";

// 8-step funnel (Android precedent): the 18+ attest merged into "disclosure"
// and the fake "first_plan" preview was killed. The canonical backend
// vocabulary (metrics.ONBOARDING_STEPS) still lists age_gate/first_plan —
// they simply never fire from web.
export const STEP_NAMES = [
  "welcome", "disclosure", "language", "state_check",
  "first_reset", "signup", "consent", "notifications",
] as const;

// One feeling tap is the whole "assessment" — each maps into the shared
// motivation/goal taxonomy so server personalization keeps working. Mirrors
// iOS StateCheckScreen.states.
export const FEELINGS: {
  label: string;
  emoji: string;
  motivation: string;
  goal: string;
}[] = [
  { label: "Stressed and tense", emoji: "🌬️", motivation: "Calm", goal: "Reduce stress" },
  { label: "Can't switch off at night", emoji: "🌙", motivation: "Calm", goal: "Sleep better" },
  { label: "Overthinking everything", emoji: "🔁", motivation: "Focus", goal: "Stop overthinking" },
  { label: "Doubting myself", emoji: "🫥", motivation: "Confidence", goal: "Build confidence" },
  { label: "Feeling distant from people", emoji: "👥", motivation: "Connection", goal: "Feel less alone" },
  { label: "Can't stay consistent", emoji: "🏁", motivation: "Discipline", goal: "Strengthen willpower" },
];

export const LANGUAGES = ["English", "Hindi", "Hinglish", "Punjabi", "Tamil"];
export const REMINDER_TIMES = ["Morning 9 AM", "Evening 7 PM", "No reminders"];

export type Consent = {
  mood_history: boolean;
  ai_memory: boolean;
  voice_storage: boolean;
  journal_memory: boolean;
  sleep_history: boolean;
  model_training: boolean;
};

export type Draft = {
  feeling: string | null;
  motivations: string[];
  goals: string[];
  languages: string[];
  consent: Consent;
  reminder: string;
};

export function freshDraft(): Draft {
  return {
    feeling: null,
    motivations: [],
    goals: [],
    languages: ["English", "Hinglish"],
    // Private by default — nothing pre-ticked (consent must be an action).
    consent: {
      mood_history: false,
      ai_memory: false,
      voice_storage: false,
      journal_memory: false,
      sleep_history: false,
      model_training: false,
    },
    reminder: "Evening 7 PM",
  };
}

const DRAFT_KEY = "cerebro_app_onboarding_draft";

export function loadDraft(): Draft {
  if (typeof window === "undefined") return freshDraft();
  try {
    const raw = window.localStorage.getItem(DRAFT_KEY);
    if (!raw) return freshDraft();
    const parsed = JSON.parse(raw);
    // Deep-merge consent so drafts saved before a category existed (e.g.
    // model_training) still carry every key, private-by-default.
    const fresh = freshDraft();
    return { ...fresh, ...parsed, consent: { ...fresh.consent, ...(parsed.consent ?? {}) } };
  } catch {
    return freshDraft();
  }
}

export function saveDraft(d: Draft) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(DRAFT_KEY, JSON.stringify(d));
  }
}

export function clearDraft() {
  if (typeof window !== "undefined") window.localStorage.removeItem(DRAFT_KEY);
}

/** After the account exists, carry the locally-collected onboarding choices to
 * the server. Best-effort per call — a personalization write failing must never
 * block entry into the app (the funnel already delivered its value). */
export async function applyOnboarding(draft: Draft): Promise<void> {
  // Age + AI-disclosure attestation (both were gated in the funnel).
  try {
    await api("/users/me/attest", { method: "POST", body: JSON.stringify({}) });
  } catch {}
  // Consent (private-by-default; only what the user switched on).
  try {
    await api("/users/me/consent", {
      method: "PATCH",
      body: JSON.stringify(draft.consent),
    });
  } catch {}
  // Profile: language + the feeling-derived motivation/goal, and — since the web
  // has no local notifications — a reminder choice maps to the email nudge opt-in.
  try {
    await api("/users/me", {
      method: "PATCH",
      body: JSON.stringify({
        language: draft.languages.join(", ") || "English",
        motivations: draft.motivations,
        goals: draft.goals,
        email_nudges: draft.reminder !== "No reminders",
      }),
    });
  } catch {}
}
