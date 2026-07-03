// Session-aware API client for the CereBro web app.
//
// Token model (docs/WEB_APP_PLAN.md §3): the ACCESS token lives in memory only
// (never persisted — XSS can't lift it from storage), the REFRESH token in
// localStorage; every 401 triggers one rotation via POST /auth/refresh before
// giving up. No cookies → no CSRF surface.

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

const REFRESH_KEY = "cerebro_app_refresh";

let accessToken: string | null = null;

function readRefresh(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

function storeSession(tokens: { access_token: string; refresh_token: string }) {
  accessToken = tokens.access_token;
  window.localStorage.setItem(REFRESH_KEY, tokens.refresh_token);
}

export function clearSession() {
  accessToken = null;
  if (typeof window !== "undefined") window.localStorage.removeItem(REFRESH_KEY);
}

/** Whether a (possibly stale) session exists — the routing guard's signal. */
export function hasSession(): boolean {
  return readRefresh() !== null;
}

/** Rotate the token pair; false means the session is truly over. */
async function refreshSession(): Promise<boolean> {
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
}

export async function api<T = any>(
  path: string,
  init: RequestInit = {},
  allowRetry = true,
): Promise<T> {
  // Fresh page load: no in-memory access token yet, but a refresh token exists.
  if (!accessToken && hasSession()) await refreshSession();

  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(init.headers || {}),
    },
  });
  if (res.status === 401 || res.status === 403) {
    if (allowRetry && (await refreshSession())) return api<T>(path, init, false);
    clearSession();
    throw new Error("unauthorized");
  }
  if (!res.ok) {
    let detail = `Request failed: ${res.status}`;
    try {
      detail = (await res.json()).detail ?? detail;
    } catch {}
    throw new Error(detail);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export async function signIn(email: string, password: string): Promise<void> {
  const body = new URLSearchParams({ username: email, password });
  const res = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) throw new Error("Invalid email or password.");
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

export async function signOut(): Promise<void> {
  try {
    await api("/auth/logout", { method: "POST" }, false);
  } catch {
    // Best-effort server-side revocation; the local session clears regardless.
  }
  clearSession();
}
