// The landing site's crisis directory — the fourth hand-copy of a contract that
// already lives in `backend/app/services/crisis.py`, `apps/ios CrisisResources.swift`,
// `apps/android Extras.kt` and `apps/app/lib/crisis.ts`.
//
// It exists because the footer links /support as "Crisis support" and that page
// carried no number at all: it told a person in crisis to go and find a hotline
// themselves, while the homepage promised region-correct lines "always a tap
// away". A marketing page is where someone who has not signed up lands, which
// makes it the *first* surface, not an afterthought.
//
// Two hard rules, same as every other copy (design system §1, §9):
//   1. Tele-MANAS 14416 leads every crisis surface.
//   2. A crisis surface never depends on the network — this list is static, so
//      it renders with a dead API, no session, and no connection.
//
// Kept byte-honest with the backend's IN region by `scripts/check-crisis-lines.mjs`,
// which gates this file in CI. Dial targets are contracts and stay literal.

export type CrisisLine = { name: string; number: string };

/** India-first, offline-safe — the same list and order the member app ships.
 * Other regions are addressed in words rather than by guessing a location from
 * a static page that has no permission to ask. */
export const CRISIS_LINES: CrisisLine[] = [
  { name: "Tele-MANAS — real people, 24/7", number: "14416" },
  { name: "Emergency services", number: "112" },
  { name: "KIRAN mental-health helpline", number: "1800-599-0019" },
  { name: "Find a helpline", number: "https://findahelpline.com" },
];

export function isWebLink(target: string): boolean {
  return /^https?:/i.test(target);
}

/** `tel:` (digits only) or the URL itself — the displayed text keeps its spacing. */
export function crisisHref(target: string): string {
  return isWebLink(target) ? target : `tel:${target.replace(/[^\d+]/g, "")}`;
}

/** Every surface that shows these leads with Tele-MANAS when it is in the list. */
export function teleManasFirst(lines: CrisisLine[]): CrisisLine[] {
  const i = lines.findIndex((l) => /tele-?manas|14416/i.test(`${l.name} ${l.number}`));
  return i > 0 ? [lines[i], ...lines.slice(0, i), ...lines.slice(i + 1)] : lines;
}
