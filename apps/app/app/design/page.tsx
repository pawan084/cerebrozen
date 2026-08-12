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
//   SLP-01 Tonight → /sleep (2026-08-12). What graduated is the ORDER: tonight
//   leads (the wind-down ritual), and the rhythm, the sounds and last night's
//   check-in fold below it. Two of the mock's features did not: the reorderable
//   wind-down (the mock states it "does not persist anywhere" — a reorder that
//   forgets on reload is a fake save) and the "10:30 pm" target bedtime (no
//   such field exists in backend/app/models/sleep.py, so it would be invented).
//   TOD-02 Check in → /checkin (2026-08-12). The mock's first state was
//   "Clear"; the wire vocabulary is Good · Anxious · Low · Tired · Overwhelmed ·
//   Not sure, and "Clear" is the same drift that was removed from Android's
//   check-in the same week. Its flat "Does not use your journal" did NOT
//   graduate: that is only true while journal_memory consent is off, so the
//   sentence would have been right for most people and wrong for exactly those
//   who had changed it — the list now reads /users/me/consent. Nor did "Save
//   and see the step", which promised a per-feeling destination that does not
//   exist. Light/Medium/Strong map to 2/3/4 rather than 1/3/5: intensity feeds
//   the stability average in services/insights.py, and a three-way choice
//   should not be recorded at the extremes of a five-point scale.
// Empty on purpose: every screen that landed here has graduated. The surface
// stays so the next redesign has somewhere to put a screen before it is wired,
// and so the notes above keep their home.
const DONE: [href: string, id: string, title: string, note: string][] = [];

export default function DesignIndex() {
  return (
    <div className="today-wrap">
      <p className="eyebrow">Design surface</p>
      <h1 className="today-greeting">Screens, before the wiring.</h1>
      <p className="sub today-lede">
        Redesigned against ref/, rendered with mock data and no backend. These are for review
        — the shipped screens are unchanged until each one is signed off.
      </p>
      {DONE.length === 0 ? (
        <section className="ds-card">
          <p className="sub">
            Nothing is waiting here. Every screen that came through this surface has graduated
            into its real route — the comments in this file record what each one dropped on the
            way, and why.
          </p>
        </section>
      ) : (
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
      )}
    </div>
  );
}
