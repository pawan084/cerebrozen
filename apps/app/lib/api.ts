/* Platform auth client for the employee app: login, token storage, and a coalesced
   single-use refresh (a second refresh with the same token trips the platform's reuse
   detection and revokes every session — so we share one call).

   ═══ Where the tokens live, and why it is not symmetric ═══

   ACCESS  → memory only, never persisted.
   REFRESH → localStorage.

   The reference states the reason as "XSS can't lift it from storage", which on its own
   is a weak argument: an XSS that reads localStorage still gets the refresh token, and a
   refresh token mints access tokens. The real reason is what our platform does with each:

     * A stolen ACCESS token is undetectable. It is a signed bearer credential the engine
       validates on its own without calling anybody, so it works silently until it expires
       and nothing anywhere notices.
     * A stolen REFRESH token is single-use. The moment an attacker spends it, the real
       client's next refresh presents a rotated token, the platform's reuse detection fires
       and revokes EVERY session for that user (routers/auth.py: "Reuse of a rotated token
       = a stolen token being replayed. Kill everything.").

   So keeping access out of storage means a storage-reading XSS can only steal the
   credential whose use is *detectable and self-revoking*. That is a real improvement, and
   it only exists because the rotation contract is already there — the two halves are one
   mechanism, and we had shipped only one of them (SECURITY.md calls the whole thing "the
   Zen pattern" and names it as our commitment).

   The cost is one refresh round-trip per page load, which the coalescing above already
   bounds to a single call. No cookies anywhere → no CSRF surface. */

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8100";
const REFRESH_KEY = "cbz_app_refresh";

export type Tokens = { access_token: string; refresh_token: string };
let refreshing: Promise<boolean> | null = null;

/** The access token. Deliberately module state: it dies with the tab, and nothing can read
 *  it out of storage because it was never put there. */
let access: string | null = null;

/** The key this app used when it stored BOTH tokens together. Read once, to migrate. */
const LEGACY_KEY = "cbz_app_tokens";

/**
 * Move an existing session onto the new key, and take the access token out of storage.
 *
 * Without this, switching keys would (a) sign every existing user out for no reason, and
 * (b) leave their old blob — access token included — sitting in localStorage forever,
 * untouched, which is precisely the thing this change exists to prevent. The migration is
 * the cleanup, not a courtesy.
 */
function migrateLegacy(): void {
  if (typeof window === "undefined") return;
  const raw = localStorage.getItem(LEGACY_KEY);
  if (raw === null) return;
  // Remove it FIRST: a malformed blob must not leave the access token behind on the way out.
  localStorage.removeItem(LEGACY_KEY);
  try {
    const old = JSON.parse(raw) as Partial<Tokens>;
    if (old?.refresh_token && !localStorage.getItem(REFRESH_KEY)) {
      localStorage.setItem(REFRESH_KEY, old.refresh_token);
    }
  } catch {
    /* unreadable — nothing to carry over, and it is already gone from storage */
  }
}

function readRefresh(): string | null {
  if (typeof window === "undefined") return null;
  migrateLegacy();
  return localStorage.getItem(REFRESH_KEY);
}

function setTokens(t: Tokens | null) {
  access = t?.access_token ?? null;
  if (typeof window === "undefined") return;
  if (t) localStorage.setItem(REFRESH_KEY, t.refresh_token);
  else localStorage.removeItem(REFRESH_KEY);
}

/** Is there a session to resume? True when a refresh token exists — the access token is
 *  gone on every reload by design, so its absence says nothing about being signed in. */
export function hasSession(): boolean {
  return readRefresh() !== null;
}

/** Drop the local copy without calling /auth/logout. For account deletion: the platform
 *  has already revoked every refresh token server-side, and the account no longer exists
 *  to log out of — this file owns the storage key, so nobody else has to. */
export function clearTokens(): void {
  setTokens(null);
}

export async function login(email: string, password: string): Promise<void> {
  // The platform's /auth/login takes OAuth2 form fields (username, password).
  const body = new URLSearchParams({ username: email, password });
  const r = await fetch(`${BASE}/auth/login`, { method: "POST", body });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? "Sign in failed");
  setTokens(await r.json());
}

export async function logout(): Promise<void> {
  const refreshToken = readRefresh();
  if (refreshToken) {
    await fetch(`${BASE}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: refreshToken }),
    }).catch(() => null);
  }
  setTokens(null);
}

async function refresh(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = readRefresh();
      if (!refreshToken) return false;
      const r = await fetch(`${BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refreshToken }),
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

/**
 * A valid access token, refreshing when we don't hold one or the caller forces it.
 * Returns null when the user must sign in again.
 *
 * The `!access` case is the ordinary one now, not an edge: every page load starts with an
 * empty memory and a refresh token in storage, so the first call here spends one round
 * trip to mint an access token. Concurrent callers share it (see `refreshing`) — two
 * parallel refreshes would present the same rotated token and trip reuse detection,
 * signing the user out of everything for the crime of loading a page.
 */
export async function accessToken(force = false): Promise<string | null> {
  if (!readRefresh()) return null;
  if (force || !access) return (await refresh()) ? access : null;
  return access;
}

export type Me = {
  id: string; email: string; name: string; role: string; org_id: string | null;
  /** The coach's persona/style — one of the companion keys the platform stores on the profile. */
  companion?: string;
  /** The platform's RESOLVED crisis region: the person's own choice, else their org's
   *  default, else "" = unknown (which the engine answers with an international
   *  directory rather than a guess). The crisis panel asks the engine for this region's
   *  helplines — the client never holds a country's numbers. */
  crisis_region?: string;
};

export async function me(): Promise<Me | null> {
  const token = await accessToken();
  if (!token) return null;
  let r = await fetch(`${BASE}/users/me`, { headers: { Authorization: `Bearer ${token}` } });
  if (r.status === 401 && (await refresh())) {
    r = await fetch(`${BASE}/users/me`, { headers: { Authorization: `Bearer ${access}` } });
  }
  return r.ok ? ((await r.json()) as Me) : null;
}

/* ── DPDP consent ─────────────────────────────────────────────────────────── */

export type Consent = {
  mood_history: boolean; ai_memory: boolean; journal_memory: boolean;
  sleep_history: boolean; voice_storage: boolean; model_training: boolean;
};

export async function getConsent(): Promise<Consent | null> {
  const token = await accessToken();
  if (!token) return null;
  let r = await fetch(`${BASE}/users/me/consent`, { headers: { Authorization: `Bearer ${token}` } });
  if (r.status === 401 && (await refresh())) {
    r = await fetch(`${BASE}/users/me/consent`, { headers: { Authorization: `Bearer ${access}` } });
  }
  return r.ok ? ((await r.json()) as Consent) : null;
}

/** Change one consent. The platform ROTATES the session — every old refresh token is
 *  revoked and a fresh pair comes back carrying the new claim, so a withdrawal bites on
 *  the very next request instead of 15 minutes later. We MUST adopt those tokens (or the
 *  user would be silently signed out for having touched a switch). */
export async function updateConsent(patch: Partial<Consent>): Promise<Consent> {
  const token = await accessToken();
  if (!token) throw new Error("not signed in");
  const r = await fetch(`${BASE}/users/me/consent`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(patch),
  });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `HTTP ${r.status}`);
  const body = (await r.json()) as Consent & Partial<Tokens>;
  if (body.access_token && body.refresh_token) {
    setTokens({ access_token: body.access_token, refresh_token: body.refresh_token });
  }
  return body as Consent;
}

/** Patch profile fields (name, companion/persona, language, region, goals…). */
export async function updateProfile(patch: Record<string, unknown>): Promise<Me> {
  const token = await accessToken();
  if (!token) throw new Error("not signed in");
  const r = await fetch(`${BASE}/users/me`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(patch),
  });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `HTTP ${r.status}`);
  return (await r.json()) as Me;
}
