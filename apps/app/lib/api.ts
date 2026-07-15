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

export type Me = { id: string; email: string; name: string; role: string; org_id: string | null };

export async function me(): Promise<Me | null> {
  const t = getTokens();
  if (!t) return null;
  let r = await fetch(`${BASE}/users/me`, { headers: { Authorization: `Bearer ${t.access_token}` } });
  if (r.status === 401 && (await refresh())) {
    r = await fetch(`${BASE}/users/me`, { headers: { Authorization: `Bearer ${getTokens()!.access_token}` } });
  }
  return r.ok ? ((await r.json()) as Me) : null;
}
