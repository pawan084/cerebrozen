// Session-aware API client for the CereBro web app.
//
// Token model (docs/WEB_APP_PLAN.md §3): the ACCESS token lives in memory only
// (never persisted — XSS can't lift it from storage), the REFRESH token in
// localStorage; every 401 triggers one rotation via POST /auth/refresh before
// giving up. No cookies → no CSRF surface.

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

const REFRESH_KEY = "cerebro_app_refresh";
const ONBOARDED_KEY = "cerebro_app_onboarded";

let accessToken: string | null = null;

function readRefresh(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

function storeSession(tokens: { access_token: string; refresh_token: string }) {
  accessToken = tokens.access_token;
  window.localStorage.setItem(REFRESH_KEY, tokens.refresh_token);
}

/** Everything this app keeps in localStorage that belongs to the PERSON
 * rather than the browser. Register D24: clearSession only dropped the
 * refresh token, so the cached safety plan, the journal draft and the
 * onboarding answers (feelings, consent) stayed readable by whoever used the
 * browser next — and account deletion left them too. Theme preference is
 * deliberately absent: it belongs to the device, not the account. */
const PERSONAL_KEYS = [
  REFRESH_KEY,
  // The offline write queue. It holds entries ONE person authored, so it must
  // not survive a sign-out: on a shared browser the next person's first drain
  // would post the previous person's check-ins into their account. Losing a
  // queued write when a session ends is the lesser of the two failures.
  "cerebro_app_outbox",
  "cbz-safety-plan",
  "cerebro_app_journal_draft",
  "cerebro_app_onboarding_draft",
  "cerebro_app_ritual",
  ONBOARDED_KEY,
];

export function clearSession() {
  accessToken = null;
  if (typeof window === "undefined") return;
  for (const key of PERSONAL_KEYS) {
    try {
      window.localStorage.removeItem(key);
    } catch {
      // A full or blocked store must not stop the rest from clearing.
    }
  }
}

/** Whether a (possibly stale) session exists — the routing guard's signal. */
export function hasSession(): boolean {
  return readRefresh() !== null;
}

/** Whether the value-first onboarding funnel has been completed on this device.
 * Mirrors iOS `AppState.hasOnboarded`: the funnel shows once, then the guard
 * routes returning visitors past it. A plain sign-out leaves it set (the device
 * has already been introduced); only `resetOnboarding()` re-arms the funnel. */
export function hasOnboarded(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(ONBOARDED_KEY) === "1";
}

export function setOnboarded() {
  if (typeof window !== "undefined") window.localStorage.setItem(ONBOARDED_KEY, "1");
}

export function resetOnboarding() {
  if (typeof window !== "undefined") window.localStorage.removeItem(ONBOARDED_KEY);
}

// Refresh tokens are single-use (the backend rotates + revokes on each refresh),
// so concurrent callers MUST share one in-flight refresh — otherwise the losing
// racers POST an already-revoked token, fail, and clearSession() wipes a session
// that was actually fine. A fresh page load fires several authed fetches at once
// (Home alone hits /auth/me + streak + moods + journal), so this race is real.
let refreshInFlight: Promise<boolean> | null = null;

/** Rotate the token pair; false means the session is truly over. Deduped so
 * simultaneous callers await a single rotation. */
function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const refresh = readRefresh();
    if (!refresh) return false;
    const res = await fetch(`${API_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: refresh }),
    });
    if (!res.ok) {
      clearSession();
      return false;
    }
    storeSession(await res.json());
    return true;
  })().finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

/** Authenticated fetch returning the raw Response — the base for JSON calls,
 * SSE streams (Oracle), and blob downloads (export). Handles the fresh-load
 * refresh and one rotation retry per 401. */
export async function authedFetch(
  path: string,
  init: RequestInit = {},
  allowRetry = true,
): Promise<Response> {
  // Fresh page load: no in-memory access token yet, but a refresh token exists.
  if (!accessToken && hasSession()) await refreshSession();

  // FormData must NOT carry a hand-set Content-Type: the browser adds the
  // multipart boundary itself, and stamping "application/json" over a file
  // upload produces a body the server cannot parse (this is how /voice/stt
  // would have failed). Everything else still defaults to JSON.
  const isForm = typeof FormData !== "undefined" && init.body instanceof FormData;
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      ...(isForm ? {} : { "Content-Type": "application/json" }),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(init.headers || {}),
    },
  });
  // 401 means "this token is no longer good" — refresh once, then give up and
  // sign out. 403 does NOT: the backend answers 403 for consent-gated routes
  // (`_memory_allowed`), so treating it as a dead session silently destroyed
  // the session of anyone visiting /patterns with AI memory switched off, and
  // buried the page's own friendly explanation behind "unauthorized"
  // (register D1). A 403 is now returned to the caller to interpret.
  if (res.status === 401) {
    if (allowRetry && (await refreshSession())) return authedFetch(path, init, false);
    clearSession();
    throw Object.assign(new Error("unauthorized"), { status: 401 });
  }
  return res;
}

/**
 * The free-tier daily cap.
 *
 * Its own error type because the IP rate limiter ALSO returns 429 and means
 * something entirely different — offering an upgrade to someone who merely
 * typed too fast would be wrong and manipulative. Only `detail.code` is
 * trusted, never the status alone.
 */
export class FreeLimitError extends Error {
  readonly limit: number;
  readonly used: number;
  /** ISO instant. The quota window is UTC, so this is rendered in the user's
   *  own timezone rather than described as "midnight". */
  readonly resetsAt: string;

  constructor(detail: { message?: string; limit?: number; used?: number; resets_at?: string }) {
    super(detail.message ?? "You've used today's free messages.");
    this.name = "FreeLimitError";
    this.limit = detail.limit ?? 0;
    this.used = detail.used ?? 0;
    this.resetsAt = detail.resets_at ?? "";
  }

  /** "5:30 AM" in the browser's timezone. */
  get resetText(): string {
    if (!this.resetsAt) return "midnight UTC";
    const d = new Date(this.resetsAt);
    return Number.isNaN(d.getTime())
      ? "midnight UTC"
      : d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
}

export async function api<T = any>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await authedFetch(path, init);
  if (!res.ok) {
    let detail = `Request failed: ${res.status}`;
    try {
      const body = await res.json();
      // `detail` may be an OBJECT (the cap) or a string. Reading it blindly
      // would put "[object Object]" in front of the user.
      if (res.status === 429 && body?.detail?.code === "free_daily_limit") {
        throw new FreeLimitError(body.detail);
      }
      // `error` is slowapi's key for the IP rate limiter — without it a
      // throttled user only sees "Request failed: 429".
      detail =
        (typeof body?.detail === "string" ? body.detail : body?.detail?.message) ??
        body?.error ??
        detail;
    } catch (e) {
      if (e instanceof FreeLimitError) throw e;
    }
    // The status rides ALONG with the message. The offline queue has to tell
    // "the server refused this" (never retry) from "the network never
    // answered" (queue it), and by this point `detail` is the server's own
    // prose with the number parsed out of it — a message is not a status.
    throw Object.assign(new Error(detail), { status: res.status });
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

/** Register D22: every non-OK response mapped to "Invalid email or
 * password.", so a 500 or a rate-limit told the user their credentials were
 * wrong and invited a pointless password reset. Only 400/401 mean that. */
function signInMessage(status: number, detail?: string): string {
  if (status === 400 || status === 401) return "Invalid email or password.";
  if (status === 429) return "Too many attempts just now — wait a minute and try again.";
  if (status >= 500) return "We couldn't reach the sign-in service. Nothing is wrong with your account — try again shortly.";
  return detail || "Couldn't sign in just now — try again.";
}

export async function signIn(email: string, password: string): Promise<void> {
  const body = new URLSearchParams({ username: email, password });
  const res = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) {
    let detail: string | undefined;
    try {
      const body = await res.json();
      detail = typeof body?.detail === "string" ? body.detail : undefined;
    } catch {
      // Non-JSON error body — the status alone decides the message.
    }
    throw new Error(signInMessage(res.status, detail));
  }
  storeSession(await res.json());
}

export async function signUp(email: string, password: string, name: string): Promise<void> {
  const res = await fetch(`${API_URL}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, name }),
  });
  if (!res.ok) {
    let detail = "Could not create your account.";
    try {
      detail = (await res.json()).detail ?? detail;
    } catch {}
    throw new Error(detail);
  }
  storeSession(await res.json());
}

/** Exchange a Sign in with Apple identity token for a session (find-or-create
 * by the stable Apple id / verified email — see backend /auth/apple). */
export async function signInApple(identityToken: string, name = ""): Promise<void> {
  const res = await fetch(`${API_URL}/auth/apple`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ identity_token: identityToken, name }),
  });
  if (!res.ok) throw new Error("Apple sign-in failed. Try email instead.");
  storeSession(await res.json());
}

/** Exchange a Google ID token (credential) for a session. */
export async function signInGoogle(idToken: string, name = ""): Promise<void> {
  const res = await fetch(`${API_URL}/auth/google`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id_token: idToken, name }),
  });
  if (!res.ok) throw new Error("Google sign-in failed. Try email instead.");
  storeSession(await res.json());
}

/** Email a one-time sign-in code. Always resolves — the account is only
 * created at verify, so there is nothing to enumerate. */
export async function requestOtp(email: string): Promise<void> {
  const res = await fetch(`${API_URL}/auth/otp/request`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  if (!res.ok) throw new Error("Couldn't send a code. Try again in a minute.");
}

/** Exchange an emailed one-time code for a session (signs up new addresses). */
export async function verifyOtp(email: string, code: string): Promise<void> {
  const res = await fetch(`${API_URL}/auth/otp/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, code }),
  });
  if (!res.ok) throw new Error("Invalid or expired code.");
  storeSession(await res.json());
}

export async function signOut(): Promise<void> {
  try {
    await authedFetch("/auth/logout", { method: "POST" }, false);
  } catch {
    // Best-effort server-side revocation; the local session clears regardless.
  }
  clearSession();
}
