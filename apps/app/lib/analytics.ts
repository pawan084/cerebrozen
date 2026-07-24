// First-party anonymous product analytics — the web twin of iOS/Android
// `Analytics`. Allowlisted names only (backend routes/events.ALLOWED_EVENTS),
// a random install id, and NO auth header — rows can never join accounts.
//
// DPDP posture (owner decision 2026-07-13, cross-client): NO telemetry before
// consent. track() stays silent until the onboarding Consent step is passed or
// a session authenticates (an existing account has an established
// relationship); pre-consent funnel steps are deliberately uncounted. After
// unlock, the "Anonymous usage stats" opt-out governs.
import { API_URL } from "@/lib/api";

const ID_KEY = "cerebro_anon_id";
const UNLOCK_KEY = "analytics_unlocked";
const OPT_KEY = "usage_stats_on";

function store(): Storage | null {
  try { return typeof window === "undefined" ? null : window.localStorage; } catch { return null; }
}

export function analyticsUnlocked(): boolean {
  return store()?.getItem(UNLOCK_KEY) === "true";
}

/** Called when the Consent step is passed or a session authenticates. */
export function unlockAnalytics() {
  store()?.setItem(UNLOCK_KEY, "true");
}

export function analyticsEnabled(): boolean {
  return store()?.getItem(OPT_KEY) !== "false";
}

export function setAnalyticsEnabled(on: boolean) {
  store()?.setItem(OPT_KEY, String(on));
}

function anonId(): string {
  const s = store();
  const existing = s?.getItem(ID_KEY);
  if (existing) return existing;
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  const id = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  s?.setItem(ID_KEY, id);
  return id;
}

/** Fire-and-forget: never blocks a screen and never surfaces errors — these
 * are counts, not truth. */
export function track(name: string, step = "") {
  if (!analyticsUnlocked() || !analyticsEnabled()) return;
  try {
    void fetch(`${API_URL}/events`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      keepalive: true,
      body: JSON.stringify({ anon_id: anonId(), source: "app", events: [{ name, step }] }),
    }).catch(() => {});
  } catch {}
}
