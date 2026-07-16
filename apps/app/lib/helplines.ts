/* Crisis helplines — served by the ENGINE, never hardcoded here.
 *
 * ARCHITECTURE.md §Cross-stack contracts: "never hardcoded in clients". The Zen
 * reference breaks this in exactly this file's place — its web chat ships India's KIRAN
 * number as a client-side fallback (`chat/page.tsx:154`) — and our own Android app shipped
 * the same bug until 2026-07-16: one country's numbers shown to everyone, next to a region
 * picker whose answer nothing read. Do not reintroduce it here.
 *
 * NEUTRAL is the only list in this file, it names NO country, and it is byte-identical to
 * what the engine returns for an unknown region (`app/safety/helplines.py::_INTERNATIONAL`).
 * It exists so the panel renders something dialable even if the fetch fails — a crisis
 * surface with nothing on it is the worst outcome available. Mirrors
 * `apps/android/.../data/Helplines.kt`; the two are deliberately the same shape.
 */

import { accessToken } from "./api";

const ENGINE = process.env.NEXT_PUBLIC_ENGINE_URL || "http://localhost:8000";

export type Helpline = {
  name: string;
  detail: string;
  /** A phone number to dial or a URL to open — `kind` says which, so we never sniff it. */
  target: string;
  kind: "tel" | "url";
};

/** The floor. Region-neutral by construction — see the file header. */
export const NEUTRAL: Helpline[] = [
  {
    name: "Find a helpline in your country",
    detail: "International directory · routes to your region",
    target: "https://findahelpline.com",
    kind: "url",
  },
];

/** Drop malformed rows rather than render a row that does nothing when tapped. */
export function parse(rows: unknown): Helpline[] {
  if (!Array.isArray(rows)) return [];
  return rows.flatMap((r) => {
    const o = r as Partial<Helpline> | null;
    if (!o || typeof o.target !== "string" || !o.target.trim()) return [];
    if (o.kind !== "tel" && o.kind !== "url") return [];
    return [{ name: String(o.name ?? ""), detail: String(o.detail ?? ""), target: o.target, kind: o.kind }];
  });
}

/**
 * The helplines to show. Never throws and never returns an empty list: every failure
 * path lands on NEUTRAL.
 *
 * `region` is the platform's resolved `crisis_region` from /users/me (the person's own
 * choice, else their org's default, else "" — which the engine answers with the
 * international directory rather than a guess).
 */
export async function loadHelplines(region: string): Promise<Helpline[]> {
  try {
    const token = await accessToken();
    // Unauthenticated is not a reason to show nothing — fall through to NEUTRAL.
    if (!token) return NEUTRAL;
    const r = await fetch(`${ENGINE}/v1/safety/helplines?region=${encodeURIComponent(region)}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!r.ok) return NEUTRAL;
    const parsed = parse((await r.json())?.helplines);
    return parsed.length ? parsed : NEUTRAL;
  } catch {
    return NEUTRAL;
  }
}

/** What an <a> should point at. `tel:` for a number, the URL itself for a link. */
export const hrefFor = (h: Helpline) => (h.kind === "tel" ? `tel:${h.target.replace(/[^0-9+]/g, "")}` : h.target);
