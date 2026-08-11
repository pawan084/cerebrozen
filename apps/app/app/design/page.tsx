import Link from "next/link";

// Index of the design surface. Screens land here as they are redesigned; each
// one graduates into its real route once signed off.
const DONE: [href: string, id: string, title: string, note: string][] = [
  ["/design/today", "TOD-01", "Today", "One decision at full volume; everything else folded away."],
  [
    "/design/checkin",
    "TOD-02",
    "Check in",
    "Six states, optional intensity and note; ends on what happens next, never a score.",
  ],
  [
    "/design/explore",
    "EXP-01",
    "Explore",
    "Six practice families by need, a search field and recently used. Not the shipped /explore.",
  ],
  [
    "/design/sleep",
    "SLP-01",
    "Tonight",
    "Target bedtime, a reorderable four-step wind-down, quick tools and an association-only insight.",
  ],
  [
    "/design/urgent",
    "SAF-01",
    "Urgent support",
    "Tele-MANAS 14416 first, then 112, each with its source and check date. India alone is verified.",
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
