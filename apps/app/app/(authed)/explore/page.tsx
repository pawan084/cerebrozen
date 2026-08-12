"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";

// Explore (EXP-01) — the second tab in the spec's five, and the parent of Sleep.
//
// The point of this screen is the opposite of a content catalogue: you arrive
// knowing what you NEED, not what you want to browse. So the top level is the
// spec's six practice families (M6), each landing on a real destination — never
// a filter that shows the same page six times.
//
// Sleep lives here rather than in the tab bar (ref/ ruling, REDESIGN_V2.md §6),
// which is why it leads the list: it is the heaviest thing behind this door.
const FAMILIES = [
  {
    href: "/sleep",
    label: "Sleep",
    blurb: "Tonight's wind-down, your diary and what the last weeks looked like.",
    icon: Icon.sleep,
  },
  {
    href: "/games#breathe",
    label: "Calm now",
    blurb: "Paced breathing and sensory grounding, for when it needs to stop rising.",
    icon: Icon.wind,
  },
  {
    href: "/library?kind=sounds",
    label: "Sounds",
    blurb: "Soundscapes and sleep stories, with a timer so they end on their own.",
    icon: Icon.play,
  },
  {
    href: "/games#reframe",
    label: "Thought work",
    blurb: "Name the thinking trap, then take the thought apart one piece at a time.",
    icon: Icon.spark,
  },
  {
    href: "/games#settle",
    label: "Mindful activities",
    blurb: "Unscored attention exercises. No leaderboard and nothing to finish.",
    icon: Icon.games,
  },
  {
    href: "/programs",
    label: "Programmes",
    blurb: "Guided journeys over days, with the evidence and the limits stated up front.",
    icon: Icon.library,
  },
];

// The three shortcuts the spec puts under the families. Kept to what this client
// can actually do today — there is no favourites or downloads store on web, and
// a door that opens onto nothing is worse than no door.
const SHORTCUTS = [
  { href: "/library", label: "Full catalogue", icon: Icon.library },
  { href: "/insights", label: "Insights", icon: Icon.insights },
  { href: "/plan", label: "Your plan", icon: Icon.plan },
];

export default function ExplorePage() {
  // EXP-01 graduation: search is present but SECONDARY — it is for the person
  // who already knows the name of the thing, not the default way in. So it sits
  // under the lede and the six needs stay the primary surface.
  //
  // It filters what is already on this page rather than querying the catalogue:
  // a box that silently searched a different corpus than the cards beneath it
  // would be a worse lie than no box.
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const families = useMemo(
    () => (q ? FAMILIES.filter((f) => `${f.label} ${f.blurb}`.toLowerCase().includes(q)) : FAMILIES),
    [q],
  );

  return (
    <>
      <AppHeader eyebrow="Explore" title="Explore by need" />
      <div className="page-body">
        <p className="sub" style={{ maxWidth: "46ch", marginBottom: 14 }}>
          Find a tool by what would help right now, without browsing a whole catalogue.
        </p>

        <input
          type="search"
          className="explore-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search a need or a name…"
          aria-label="Search practices, sounds and programmes"
          style={{ marginBottom: 22 }}
        />

        <nav className="explore-grid" aria-label="Practice families">
          {families.map(({ href, label, blurb, icon: I }, i) => (
            <Link
              key={href}
              href={href}
              className={`card explore-card cz-in${i < 3 ? ` cz-d${i}` : ""}`}
            >
              <span className="explore-icon" aria-hidden="true">
                <I size={20} />
              </span>
              <strong>{label}</strong>
              <span className="sub">{blurb}</span>
            </Link>
          ))}
        </nav>

        {q && families.length === 0 && (
          <p className="sub">
            Nothing here matches &ldquo;{query}&rdquo;. The full catalogue searches everything
            — <Link href="/library" className="link">open it</Link>.
          </p>
        )}

        <div className="sec-head">
          <h2 className="serif-h">Everything else</h2>
        </div>
        <nav className="explore-shortcuts" aria-label="Other destinations">
          {SHORTCUTS.map(({ href, label, icon: I }) => (
            <Link key={href} href={href} className="ui-chip">
              <I size={16} /> {label}
            </Link>
          ))}
        </nav>
      </div>
    </>
  );
}
