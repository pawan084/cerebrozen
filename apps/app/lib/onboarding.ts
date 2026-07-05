// Data + persistence for the web onboarding funnel — a faithful, web-adapted
// port of the iOS OnboardingFlow (value-first: a felt benefit before the account
// ask). The step names are a cross-stack contract with the iOS funnel and
// backend metrics.ONBOARDING_STEPS — keep the list and order in sync.

import { api } from "@/lib/api";

export const STEP_NAMES = [
  "welcome", "age_gate", "disclosure", "language", "state_check",
  "first_reset", "first_plan", "signup", "consent", "notifications",
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

// Headline the first plan around the chosen goal (mirrors iOS FirstPlanScreen).
export function planTitle(goal: string | undefined): string {
  switch (goal) {
    case "Sleep better": return "Sleep deeper";
    case "Reduce stress": return "Ease today's stress";
    case "Stop overthinking": return "Quiet the noise";
    case "Build confidence": return "Steady confidence";
    case "Feel less alone": return "Feel more connected";
    case "Strengthen willpower": return "Small promises, kept";
    default: return "A calmer day";
  }
}

export const PLAN_STEPS = [
  { title: "Breathing reset", detail: "3 min · recommended now", emoji: "🌬️" },
  { title: "Night journal", detail: "5 min reflection", emoji: "📖" },
  { title: "Reminder timing", detail: "Evening private nudge", emoji: "🔔" },
];

export type Consent = {
  mood_history: boolean;
  ai_memory: boolean;
  voice_storage: boolean;
  journal_memory: boolean;
  sleep_history: boolean;
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
    },
    reminder: "Evening 7 PM",
  };
}

const DRAFT_KEY = "cerebro_app_onboarding_draft";

export function loadDraft(): Draft {
  if (typeof window === "undefined") return freshDraft();
  try {
    const raw = window.localStorage.getItem(DRAFT_KEY);
    return raw ? { ...freshDraft(), ...JSON.parse(raw) } : freshDraft();
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
