/* The member's own wellness data, served by the ENGINE (never the platform — the
   "counts, never content" firewall keeps journals/moods off the org database).
   Every write is consent-gated: the engine answers 403 when the person hasn't
   consented, and 409 when the deployment has self-report wellness turned off.
   Both are surfaced honestly rather than swallowed. */

import { accessToken } from "./api";

const ENGINE = process.env.NEXT_PUBLIC_ENGINE_URL || "http://localhost:8000";

export type JournalEntry = { id?: string; entry_id?: string; body?: string; title?: string; created_at?: string; at?: string };
export type MoodEntry = { id?: string; entry_id?: string; mood?: string; symbol?: string; intensity?: number; note?: string; created_at?: string; at?: string };

/** Why a wellness surface is unavailable — so the UI can say which, not "error". */
export class Unavailable extends Error {
  constructor(readonly reason: "consent" | "disabled" | "offline", msg: string) { super(msg); }
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await accessToken();
  if (!token) throw new Unavailable("offline", "not signed in");
  const r = await fetch(`${ENGINE}${path}`, {
    ...init,
    headers: { ...(init?.body ? { "Content-Type": "application/json" } : {}), Authorization: `Bearer ${token}` },
  });
  if (r.status === 403) throw new Unavailable("consent", "You haven't turned this on in Settings.");
  if (r.status === 409) throw new Unavailable("disabled", "Self-report wellness isn't enabled for your workspace.");
  if (!r.ok) throw new Unavailable("offline", `HTTP ${r.status}`);
  return (r.status === 204 ? null : await r.json()) as T;
}

export const listJournal = () => call<JournalEntry[]>("/v1/wellness/journal?limit=50");
export const addJournal = (body: string, title = "") =>
  call<JournalEntry>("/v1/wellness/journal", { method: "POST", body: JSON.stringify({ body, title }) });
export const listMoods = () => call<MoodEntry[]>("/v1/wellness/moods?limit=30");
export const addMood = (mood: string, symbol = "", intensity = 0) =>
  call<MoodEntry>("/v1/wellness/moods", { method: "POST", body: JSON.stringify({ mood, symbol, intensity }) });
export const weeklyInsights = () => call<Record<string, unknown>>("/v1/wellness/insights/weekly");
export const deleteEntry = (kind: "journal" | "moods" | "sleep", id: string) =>
  call<{ deleted?: boolean }>(`/v1/wellness/${kind}/${encodeURIComponent(id)}`, { method: "DELETE" });
