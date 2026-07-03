export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

const TOKEN_KEY = "cerebro_admin_token";
const REFRESH_KEY = "cerebro_admin_refresh";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}
export function setToken(t: string) {
  window.localStorage.setItem(TOKEN_KEY, t);
}
export function clearToken() {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
}

export async function login(email: string, password: string): Promise<string> {
  const body = new URLSearchParams({ username: email, password });
  const res = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) throw new Error("Invalid email or password");
  const data = await res.json();
  // Keep the refresh token so sessions outlive the 30-minute access token.
  window.localStorage.setItem(REFRESH_KEY, data.refresh_token as string);
  return data.access_token as string;
}

/** Rotate the token pair once; false ends the session for real. */
async function tryRefresh(): Promise<boolean> {
  const refresh =
    typeof window === "undefined" ? null : window.localStorage.getItem(REFRESH_KEY);
  if (!refresh) return false;
  const res = await fetch(`${API_URL}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refresh_token: refresh }),
  });
  if (!res.ok) return false;
  const data = await res.json();
  setToken(data.access_token as string);
  window.localStorage.setItem(REFRESH_KEY, data.refresh_token as string);
  return true;
}

export async function api<T = any>(
  path: string,
  init: RequestInit = {},
  allowRetry = true,
): Promise<T> {
  const token = getToken();
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {}),
    },
  });
  if (res.status === 401 || res.status === 403) {
    if (allowRetry && (await tryRefresh())) return api<T>(path, init, false);
    clearToken();
    throw new Error("unauthorized");
  }
  if (!res.ok) throw new Error(`Request failed: ${res.status}`);
  if (res.status === 204) return undefined as T;
  return res.json();
}
