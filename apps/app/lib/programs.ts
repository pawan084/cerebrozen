"use client";

/* Multi-day programs (journeys) — enrolment is a preference, not content, and lives
   on the PLATFORM: {active_program_id, started_at}. The current day is derived from
   the start date, so it can't drift. It never holds a word the person wrote. */

import { accessToken } from "./api";

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8100";

export type ProgramGuide = { title?: string; body?: string };
export type ActiveProgram = {
  id: string; title: string; subtitle?: string;
  day: number; days: number; completed?: boolean; today_guide?: ProgramGuide;
};
export type CatalogProgram = { id: string; title: string; subtitle?: string };

async function pf<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await accessToken();
  if (!token) throw new Error("not signed in");
  const r = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { ...(init?.body ? { "Content-Type": "application/json" } : {}), Authorization: `Bearer ${token}` },
  });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return (r.status === 204 ? null : await r.json()) as T;
}

export const activeProgram = () => pf<{ program: ActiveProgram | null }>("/programs/active");
export const listPrograms = () => pf<CatalogProgram[]>("/content?kind=program");
export const enrollProgram = (id: string) =>
  pf<{ program?: ActiveProgram }>("/programs/enroll", { method: "POST", body: JSON.stringify({ content_id: id }) });
export const leaveProgram = () => pf<unknown>("/programs/active", { method: "DELETE" });
