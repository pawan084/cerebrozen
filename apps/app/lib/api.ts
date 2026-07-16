/* Platform auth client for the employee app: login, token storage, and a
   coalesced single-use refresh (a second refresh with the same token trips the
   platform's reuse detection and revokes every session — so we share one call). */

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8100";

export type Tokens = { access_token: string; refresh_token: string };
let refreshing: Promise<boolean> | null = null;

export function getTokens(): Tokens | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem("cbz_app_tokens");
  return raw ? (JSON.parse(raw) as Tokens) : null;
}

function setTokens(t: Tokens | null) {
  if (t) localStorage.setItem("cbz_app_tokens", JSON.stringify(t));
  else localStorage.removeItem("cbz_app_tokens");
}

export async function login(email: string, password: string): Promise<void> {
  // The platform's /auth/login takes OAuth2 form fields (username, password).
  const body = new URLSearchParams({ username: email, password });
  const r = await fetch(`${BASE}/auth/login`, { method: "POST", body });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? "Sign in failed");
  setTokens(await r.json());
}

export async function logout(): Promise<void> {
  const t = getTokens();
  if (t) {
    await fetch(`${BASE}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: t.refresh_token }),
    }).catch(() => null);
  }
  setTokens(null);
}

async function refresh(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const t = getTokens();
      if (!t) return false;
      const r = await fetch(`${BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: t.refresh_token }),
      });
      if (!r.ok) {
        setTokens(null);
        return false;
      }
      setTokens(await r.json());
      return true;
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/** A valid access token, refreshing once if the current one is missing/stale.
 *  Returns null when the user must sign in again. */
export async function accessToken(force = false): Promise<string | null> {
  const t = getTokens();
  if (!t) return null;
  if (force) return (await refresh()) ? getTokens()!.access_token : null;
  return t.access_token;
}

export type Me = {
  id: string; email: string; name: string; role: string; org_id: string | null;
  /** The platform's RESOLVED crisis region: the person's own choice, else their org's
   *  default, else "" = unknown (which the engine answers with an international
   *  directory rather than a guess). The crisis panel asks the engine for this region's
   *  helplines — the client never holds a country's numbers. */
  crisis_region?: string;
};

export async function me(): Promise<Me | null> {
  const t = getTokens();
  if (!t) return null;
  let r = await fetch(`${BASE}/users/me`, { headers: { Authorization: `Bearer ${t.access_token}` } });
  if (r.status === 401 && (await refresh())) {
    r = await fetch(`${BASE}/users/me`, { headers: { Authorization: `Bearer ${getTokens()!.access_token}` } });
  }
  return r.ok ? ((await r.json()) as Me) : null;
}

/* ── DPDP consent ─────────────────────────────────────────────────────────── */

export type Consent = {
  mood_history: boolean; ai_memory: boolean; journal_memory: boolean;
  sleep_history: boolean; voice_storage: boolean; model_training: boolean;
};

export async function getConsent(): Promise<Consent | null> {
  const t = getTokens();
  if (!t) return null;
  let r = await fetch(`${BASE}/users/me/consent`, { headers: { Authorization: `Bearer ${t.access_token}` } });
  if (r.status === 401 && (await refresh())) {
    r = await fetch(`${BASE}/users/me/consent`, { headers: { Authorization: `Bearer ${getTokens()!.access_token}` } });
  }
  return r.ok ? ((await r.json()) as Consent) : null;
}

/** Change one consent. The platform ROTATES the session — every old refresh token is
 *  revoked and a fresh pair comes back carrying the new claim, so a withdrawal bites on
 *  the very next request instead of 15 minutes later. We MUST adopt those tokens (or the
 *  user would be silently signed out for having touched a switch). */
export async function updateConsent(patch: Partial<Consent>): Promise<Consent> {
  const t = getTokens();
  if (!t) throw new Error("not signed in");
  const r = await fetch(`${BASE}/users/me/consent`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${t.access_token}` },
    body: JSON.stringify(patch),
  });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `HTTP ${r.status}`);
  const body = (await r.json()) as Consent & Partial<Tokens>;
  if (body.access_token && body.refresh_token) {
    setTokens({ access_token: body.access_token, refresh_token: body.refresh_token });
  }
  return body as Consent;
}
