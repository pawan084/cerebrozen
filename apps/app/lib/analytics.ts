/**
 * Anonymous, first-party product analytics for the browser client.
 *
 * The mobile apps have posted to `/events` since 2026-07-04; the web clients
 * never did, so the `source` values `web`/`app` were unused and the browser
 * funnel was invisible in admin. This is the missing half — deliberately a port
 * of the same contract rather than a new one, because the admin funnel counts
 * iOS, Android and web together and a different vocabulary would silently
 * corrupt it.
 *
 * The rules it inherits, all of which matter more than the measurement:
 *
 *  - **Zero third-party SDKs.** Counts go to our own backend, nowhere else.
 *  - **Never account-linked.** A random per-install id, and `/events` takes no
 *    auth on purpose — a bearer token on the request is ignored, so events
 *    cannot be joined to a user even by accident.
 *  - **Allowlisted names only**, no free-form payload. Unknown names are
 *    dropped server-side.
 *  - **Nothing fires before consent** (`unlock()`), matching the 2026-07-13
 *    decision on Android: funnel steps reached before the Consent screen are
 *    intentionally uncounted rather than retroactively sent.
 *  - **Opt-out** honoured, and failures are swallowed — analytics must never
 *    affect the product.
 */
import { API_URL } from "@/lib/api";

const ID_KEY = "cerebro_anon_id";
const OPT_KEY = "cerebro_usage_stats";
/** Set once the user passes the Consent step (or signs in as a returning user). */
const UNLOCK_KEY = "cerebro_analytics_unlocked";

/** The complete vocabulary, mirroring `routes/events.ALLOWED_EVENTS`. */
export type EventName =
  | "onboarding_step"
  | "onboarding_done"
  | "paywall_view"
  | "paywall_cta";

/**
 * Onboarding step names, in order, mirroring `services/metrics.ONBOARDING_STEPS`.
 * The admin funnel joins on these strings — a rename here silently drops a bar
 * rather than erroring, so this array is a cross-stack contract.
 */
export const ONBOARDING_STEPS = [
  "welcome",
  "age_gate",
  "disclosure",
  "language",
  "state_check",
  "first_reset",
  "first_plan",
  "signup",
  "consent",
  "notifications",
] as const;

function safeGet(key: string): string | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* private mode / full store — analytics is never worth an exception */
  }
}

export function analyticsEnabled(): boolean {
  return safeGet(OPT_KEY) !== "false";
}

export function setAnalyticsEnabled(on: boolean) {
  safeSet(OPT_KEY, String(on));
}

/** Called once the user has passed Consent. Idempotent. */
export function unlockAnalytics() {
  safeSet(UNLOCK_KEY, "1");
}

function unlocked(): boolean {
  return safeGet(UNLOCK_KEY) === "1";
}

function anonId(): string {
  const existing = safeGet(ID_KEY);
  if (existing) return existing;
  const id =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID().replace(/-/g, "")
      : Math.random().toString(36).slice(2).padEnd(16, "0");
  safeSet(ID_KEY, id);
  return id;
}

/**
 * Fire-and-forget. Returns nothing and never throws.
 *
 * `keepalive` so an event sent as the user navigates away still leaves — the
 * last step of a funnel is the one most likely to be lost otherwise.
 */
export function track(name: EventName, step = "") {
  if (typeof window === "undefined") return;
  if (!unlocked() || !analyticsEnabled()) return;
  try {
    void fetch(`${API_URL}/events`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // No credentials, no Authorization: the endpoint ignores auth by design
      // and sending one would only create the appearance of linkage.
      body: JSON.stringify({
        anon_id: anonId(),
        source: "app",
        events: [{ name, step }],
      }),
      keepalive: true,
    }).catch(() => {});
  } catch {
    /* never let a count break a screen */
  }
}
