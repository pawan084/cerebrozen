import Link from "next/link";

// The design surface: redesigned screens from ref/, rendered with mock data and
// no backend, auth or network — the same thing the ref/ prototypes are.
//
// It lives OUTSIDE the (authed) group on purpose. These screens are reviewed
// before they are wired, and putting them behind the session guard would mean
// nobody can look at them without a running Postgres. Each screen graduates into
// its real route once the design is signed off; this surface is scaffolding and
// is expected to shrink to nothing.
//
// Nothing here reads or writes user data, so there is no privacy surface to get
// wrong — but for the same reason, nothing here proves the real screen works.
export const metadata = {
  title: "CereBro — design surface",
  robots: { index: false, follow: false },
};

// TOD-01 is gone from this list because it GRADUATED (2026-08-12) — the hero,
// its provenance sentence and the folds now render on the real /home from
// /plans/active. This surface is scaffolding and is meant to shrink to nothing.
const SCREENS: [href: string, id: string, label: string][] = [
  ["/design/checkin", "TOD-02", "Check in"],
];

export default function DesignLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="design-root">
      <header className="design-bar">
        <Link href="/design" className="design-brand">
          CereBro <span>design surface</span>
        </Link>
        <nav className="design-nav" aria-label="Screens">
          {SCREENS.map(([href, id, label]) => (
            <Link key={href} href={href} className="design-chip">
              <b>{id}</b> {label}
            </Link>
          ))}
        </nav>
        <p className="design-note">Mock data · no backend · not the shipped screen</p>
      </header>
      <main id="main">{children}</main>
    </div>
  );
}
