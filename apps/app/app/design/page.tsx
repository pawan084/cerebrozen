import Link from "next/link";

// Index of the design surface. Screens land here as they are redesigned; each
// one graduates into its real route once signed off.
//
// GRADUATED — no longer on this surface:
//   TOD-01 Today → /home (2026-08-12). The hero reads /plans/active, and its
//   provenance sentence branches on plan.source rather than the mock's
//   hardcoded line. lib/todayHero.ts holds the contract.
//   SAF-01 Urgent support → /crisis (2026-08-12). The mock's useState region
//   selector did NOT graduate: it would have made which emergency number you
//   see depend on JS. Every region is in the markup behind a native <details>,
//   and an e2e test loads the page with JavaScript disabled to keep it that way.
//   EXP-01 Explore → /explore (2026-08-12). The six needs were already shipped
//   in Wave 1; what graduated is the secondary search field, which filters the
//   cards ON THIS PAGE rather than querying the catalogue — a box that searched
//   a different corpus than the cards beneath it would be worse than no box.
//   "Recently used" did NOT graduate: there is no recents store on web, and
//   inventing one silently is not a memory aid, it is a fabrication.
const DONE: [href: string, id: string, title: string, note: string][] = [
  [
    "/design/checkin",
    "TOD-02",
    "Check in",
    "Six states, optional intensity and note; ends on what happens next, never a score.",
  ],
  [
    "/design/sleep",
    "SLP-01",
    "Tonight",
    "Target bedtime, a reorderable four-step wind-down, quick tools and an association-only insight.",
  ],
];

export default function DesignIndex() {
  return (
    <div className="today-wrap">
      <p className="eyebrow">Design surface</p>
      <h1 className="today-greeting">Screens, before the wiring.</h1>
      <p className="sub today-lede">
        Redesigned against ref/, rendered with mock data and no backend. These are for review
        — the shipped screens are unchanged until each one is signed off.
      </p>
      <ul className="day-list">
        {DONE.map(([href, id, title, note]) => (
          <li key={href} className="day-row">
            <span className="day-when">{id}</span>
            <span className="day-what">
              <Link href={href}>{title}</Link> — <span className="sub">{note}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
