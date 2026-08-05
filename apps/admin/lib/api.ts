export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

const REFRESH_KEY = "cerebro_admin_refresh";

// The ACCESS token lives in memory only — the same model as the user-facing
// web app (apps/app/lib/api.ts): XSS can't lift what storage doesn't hold.
// Until 2026-08-03 it sat in localStorage; an admin token is the worst one to
// leave lying around. The refresh token stays in localStorage so sessions
// survive a reload — a fresh load starts token-less and rotates on first use.
let accessToken: string | null = null;

export function getToken(): string | null {
  return accessToken;
}
export function setToken(t: string) {
  accessToken = t;
}
export function clearToken() {
  accessToken = null;
  if (typeof window !== "undefined") window.localStorage.removeItem(REFRESH_KEY);
}

/** Sign out for real: revoke server-side, then clear locally.
 *
 * Register E37: the shell only ever called clearToken(), so a previously
 * exfiltrated refresh token kept working after the operator believed they had
 * signed out. POST /auth/logout bumps the user's token_version, which
 * invalidates every outstanding access AND refresh token — it authenticates
 * with the access token, so it goes through the normal authed path (which
 * refreshes first if this tab has no access token yet). The local clear runs
 * either way: a failed revoke must never strand someone signed in.
 */
export async function logout(): Promise<void> {
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {
    // Offline, already-expired session, or server down — clear locally anyway.
  } finally {
    clearToken();
  }
}

/** Whether a (possibly stale) session exists — the login-screen gate. The
 * access token being memory-only means a reload always starts without one;
 * what decides "signed in" is having a refresh token to rotate. */
export function hasSession(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(REFRESH_KEY) !== null;
}

/**
 * Why a failure happened, so the UI can say the true thing:
 * `offline` — the request never reached a server (down API, no network, CORS).
 * `unauthorized` — the server answered, and said no.
 * `server` — the server answered 5xx: our fault, not the operator's.
 * `request` — any other non-OK response.
 */
export type ApiErrorKind = "offline" | "unauthorized" | "server" | "request";

export class ApiError extends Error {
  kind: ApiErrorKind;
  status?: number;
  constructor(kind: ApiErrorKind, message: string, status?: number) {
    super(message);
    this.name = "ApiError";
    this.kind = kind;
    this.status = status;
  }
}

const OFFLINE_MSG = "We couldn't reach the CereBro API.";

export async function login(email: string, password: string): Promise<string> {
  const body = new URLSearchParams({ username: email, password });
  let res: Response;
  try {
    res = await fetch(`${API_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
  } catch {
    // A dead backend must never be reported as bad credentials — an operator
    // retyping a correct password is the worst possible dead end.
    throw new ApiError("offline", OFFLINE_MSG);
  }
  if (res.status === 400 || res.status === 401 || res.status === 403) {
    throw new ApiError("unauthorized", "That email and password don't match an admin account.");
  }
  if (!res.ok) {
    throw new ApiError(
      res.status >= 500 ? "server" : "request",
      `Sign-in failed on the server side (${res.status}).`,
      res.status,
    );
  }
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
    let res: Response;
    try {
      res = await fetch(`${API_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refresh }),
      });
    } catch {
      return false;
    }
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
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init.headers || {}),
      },
    });
  } catch {
    // Never clears the token: an unreachable API is not an expired session.
    throw new ApiError("offline", OFFLINE_MSG);
  }
  if (res.status === 401 || res.status === 403) {
    if (allowRetry && (await tryRefresh())) return api<T>(path, init, false);
    clearToken();
    throw new ApiError("unauthorized", "unauthorized");
  }
  // Message shape kept ("Request failed: 503") — callers match on the status.
  if (!res.ok) {
    throw new ApiError(res.status >= 500 ? "server" : "request", `Request failed: ${res.status}`, res.status);
  }
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
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    });
  } catch {
    // Register E66: an offline upload threw a raw "TypeError: Failed to
    // fetch" which the Media tab surfaced verbatim — every JSON call already
    // maps this to the friendly offline copy.
    throw new ApiError("offline", OFFLINE_MSG);
  }
  if (res.status === 401 || res.status === 403) {
    if (allowRetry && (await tryRefresh())) return upload<T>(path, file, false);
    clearToken();
    throw new Error("unauthorized");
  }
  if (!res.ok) throw new Error(await errorDetail(res));
  return res.json();
}
