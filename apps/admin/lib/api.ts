/* Platform API client: bearer auth + single-use refresh rotation.
   Rotation is COALESCED — concurrent 401s share one refresh call, because a
   second refresh with the same token trips the platform's reuse detection
   and revokes every session (that is a feature; don't fight it). */

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8100";

let refreshing: Promise<boolean> | null = null;

export function getTokens() {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem("cbz_tokens");
  return raw ? (JSON.parse(raw) as { access_token: string; refresh_token: string }) : null;
}

function setTokens(t: { access_token: string; refresh_token: string } | null) {
  if (t) localStorage.setItem("cbz_tokens", JSON.stringify(t));
  else localStorage.removeItem("cbz_tokens");
}

export async function login(email: string, password: string): Promise<void> {
  const body = new URLSearchParams({ username: email, password });
  const r = await fetch(`${BASE}/auth/login`, { method: "POST", body });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? "login failed");
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

export async function acceptInvitation(token: string, name: string, password: string): Promise<void> {
  const r = await fetch(`${BASE}/auth/accept-invitation`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, name, password }),
  });
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? "invitation failed");
  setTokens(await r.json());
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

export async function api(path: string, init: RequestInit = {}, retried = false): Promise<Response> {
  const t = getTokens();
  const headers = new Headers(init.headers);
  if (t) headers.set("Authorization", `Bearer ${t.access_token}`);
  if (init.body && typeof init.body === "string") headers.set("Content-Type", "application/json");
  const r = await fetch(`${BASE}${path}`, { ...init, headers });
  if (r.status === 401 && !retried && (await refresh())) return api(path, init, true);
  return r;
}

export async function apiJson<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  const r = await api(path, init);
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `HTTP ${r.status}`);
  return (await r.json()) as T;
}
