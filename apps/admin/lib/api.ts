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

// Refresh tokens are single-use (backend rotates + revokes), so concurrent
// callers must share one rotation — otherwise the losing racers POST a revoked
// token and spuriously fail. A fresh dashboard load fires several 401→refresh
// paths at once, so dedupe them.
let refreshInFlight: Promise<boolean> | null = null;

/** Rotate the token pair once; false ends the session for real. Deduped. */
function tryRefresh(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
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
  })().finally(() => { refreshInFlight = null; });
  return refreshInFlight;
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
  if (!res.ok) throw new Error(await errorDetail(res));
  if (res.status === 204) return undefined as T;
  return res.json();
}

/** FastAPI puts the actionable message in `detail` — surface it instead of a bare
 * status code, so "File exceeds 25 MB" reaches the admin rather than "413". */
async function errorDetail(res: Response): Promise<string> {
  try {
    const body = await res.json();
    if (typeof body?.detail === "string" && body.detail) return body.detail;
  } catch {
    /* not JSON — fall through to the status */
  }
  return `Request failed: ${res.status}`;
}

/**
 * Multipart upload — a separate path from [api] because that one hardcodes
 * `Content-Type: application/json`. A multipart body must NOT carry a
 * caller-set Content-Type: the browser has to write it itself so it can append
 * the boundary token, and overriding it makes the server fail to parse the body.
 */
export async function upload<T = any>(
  path: string,
  file: File,
  allowRetry = true,
): Promise<T> {
  const token = getToken();
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  if (res.status === 401 || res.status === 403) {
    if (allowRetry && (await tryRefresh())) return upload<T>(path, file, false);
    clearToken();
    throw new Error("unauthorized");
  }
  if (!res.ok) throw new Error(await errorDetail(res));
  return res.json();
}
