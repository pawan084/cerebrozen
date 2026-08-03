// The web crisis directory — mirror of iOS `CrisisResources.swift`, Android
// `Extras.kt: CrisisScreen` and backend `services/crisis.py`.
//
// Two hard rules live here (design system §1, §9):
//   1. Tele-MANAS 14416 leads every crisis surface.
//   2. A crisis surface must never depend on the network — this list is static,
//      so it renders with a dead API, a dead session, or no connection at all.
// Dial/URL targets are contracts and stay literal.

export type CrisisLine = { name: string; number: string };

/** India-first, offline-safe. Emergency numbers differ elsewhere, which the
 * support page says in plain words rather than guessing a region. */
export const CRISIS_LINES: CrisisLine[] = [
  { name: "Tele-MANAS — real people, 24/7", number: "14416" },
  { name: "Emergency services", number: "112" },
  { name: "KIRAN mental-health helpline", number: "1800-599-0019" },
  { name: "Find a helpline", number: "https://findahelpline.com" },
];

/** Doors to a real human when it isn't an emergency (Android HumanSupportScreen). */
export const HUMAN_SUPPORT: { name: string; detail: string; target: string }[] = [
  { name: "Tele-MANAS — call 14416", detail: "Free government mental-health line · 24/7", target: "14416" },
  { name: "iCall — talk to a counsellor", detail: "Trained counsellors by phone · 9152987821", target: "9152987821" },
  { name: "Find a therapist", detail: "Directories of professional help near you", target: "https://www.findahelpline.com/in" },
];

export function isWebLink(target: string): boolean {
  return /^https?:/i.test(target);
}

/** `tel:` (digits only) or the URL itself — the displayed text keeps its spacing. */
export function crisisHref(target: string): string {
  return isWebLink(target) ? target : `tel:${target.replace(/[^\d+]/g, "")}`;
}

/** Server-driven resources arrive region-ordered, but every surface that shows
 * them still leads with Tele-MANAS when it's in the list. */
export function teleManasFirst(lines: CrisisLine[]): CrisisLine[] {
  const i = lines.findIndex((l) => /tele-?manas|14416/i.test(`${l.name} ${l.number}`));
  return i > 0 ? [lines[i], ...lines.slice(0, i), ...lines.slice(i + 1)] : lines;
}
