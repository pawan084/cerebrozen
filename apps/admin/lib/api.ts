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
