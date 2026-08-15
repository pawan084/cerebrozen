export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

// Neither token is readable by JavaScript any more.
//
// The ACCESS token lives in memory only — the same model as the user-facing web
// app (apps/app/lib/api.ts): XSS can't lift what storage doesn't hold. Until
// 2026-08-03 it sat in localStorage; an admin token is the worst one to leave
// lying around.
//
// The REFRESH token used to stay in localStorage so sessions survived a reload,
// which register E40 called out as the contradiction it was: the access token
// was moved out of storage *because* XSS can read storage, and the longer-lived,
// rotating credential — the one actually worth stealing — was left behind in it.
// It now lives in an httpOnly cookie set by `/auth/login` (backend
// `auth.REFRESH_COOKIE`, scoped to `/auth`), so a script on this origin cannot
// read it, and every auth call sends `credentials: "include"` to carry it.
//
// Consequence worth knowing: nothing in JS can now *look* at the session, so
// `hasSession()` asks the server instead of inspecting a key. That is strictly
// more truthful — the old check only proved a string existed, not that it worked.
let accessToken: string | null = null;

export function getToken(): string | null {
  return accessToken;
}
export function setToken(t: string) {
  accessToken = t;
}
/** Drop the in-memory access token. The refresh cookie is httpOnly, so only the
 * server can remove it — `logout()` is what actually ends a session. */
export function clearToken() {
  accessToken = null;
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

/** Whether a usable session exists — the login-screen gate.
 *
 * Now an async round-trip rather than a localStorage lookup, because the
 * refresh token is httpOnly and deliberately unreadable here. It attempts one
 * rotation: success means the cookie is present AND still valid, which is a
 * stronger claim than the old check could make (a revoked or expired token
 * still "existed" in storage, so the console would render the whole shell and
 * then throw the operator out on the first request).
 */
export async function hasSession(): Promise<boolean> {
  if (typeof window === "undefined") return false;
  return tryRefresh();
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
      // Required for the browser to STORE the httpOnly refresh cookie the
      // response sets — without it the Set-Cookie is silently dropped on a
      // cross-origin response and every reload would land back on sign-in.
      credentials: "include",
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
  // The response body still carries a refresh token, for the native clients
  // that need it. This one deliberately drops it on the floor: the cookie the
  // same response set is the copy this console will use, and it cannot read it.
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
    let res: Response;
    try {
      // No body: the token travels as the httpOnly cookie, which `credentials:
      // "include"` attaches. The backend reads body-first and falls back to the
      // cookie, so this is the same endpoint every other client uses.
      res = await fetch(`${API_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
        credentials: "include",
      });
    } catch {
      return false;
    }
    if (!res.ok) return false;
    const data = await res.json();
    setToken(data.access_token as string);
    // The rotated refresh token arrives as a fresh Set-Cookie; there is nothing
    // for us to store, which is the entire point.
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
      // Needed so the browser accepts the Set-Cookie that DELETES the refresh
      // cookie on `/auth/logout`. Safe to send on every call: the cookie is
      // scoped to `/auth`, so it is not actually attached to ordinary API
      // requests, and authorization still comes from the Bearer header.
      credentials: "include",
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
