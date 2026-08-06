"use client";

import { useMemo, useState } from "react";

// Explore (EXP-01) — redesigned against ref/mobile.html `screens['EXP-01']`
// and ref/web.html `explore()`.
//
// The shipped /explore under (authed) is a content catalogue: a wall of cards
// you read through. The spec's Explore is the opposite — you arrive knowing
// what you need and leave with one tool, so the top level is six *needs*, not
// six content types. Search is present but secondary: it is for the person who
// already knows the name of the thing, not the default way in.
//
// The six families are the spec's, and Sleep leads them because Sleep lost its
// tab in the IA ruling (REDESIGN_V2 §6.1) — this is the entry point that has to
// carry it.
//
// Nothing here is ordered by how often you use it and nothing counts sessions.
// "Recently used" is a memory aid, not a record of attendance.
//
// Mock data only — no fetch, no session. Every value below is a constant.

type Family = {
  key: string;
  mark: string;
  title: string;
  note: string;
  lead: string;
  terms: string[];
};

const FAMILIES: Family[] = [
  {
    key: "sleep",
    mark: "☾",
    title: "Sleep",
    note: "Tonight’s plan, wind-down and the CBT-I foundations",
    lead: "Tonight’s wind-down · four steps · about 20 minutes",
    terms: ["sleep", "night", "wind-down", "bedtime", "insomnia", "cbt-i", "rest"],
  },
  {
    key: "calm",
    mark: "○",
    title: "Calm now",
    note: "Breathing, grounding and body resets for a loud moment",
    lead: "Three-minute grounding · works offline",
    terms: ["calm", "ground", "breath", "breathe", "panic", "reset", "body"],
  },
  {
    key: "sounds",
    mark: "♫",
    title: "Sounds",
    note: "Ambient audio, sleep stories and a mix you build yourself",
    lead: "Rain over quiet hills · fade-out timer",
    terms: ["sound", "audio", "rain", "ocean", "story", "mixer", "noise"],
  },
  {
    key: "thoughts",
    mark: "⌁",
    title: "Thought work",
    note: "Untangle a thought, separate it from the facts, write it down",
    lead: "Untangle a thought · about 6 minutes",
    terms: ["thought", "thoughts", "reframe", "worry", "rumination", "journal", "write"],
  },
  {
    key: "mindful",
    mark: "✦",
    title: "Mindful activities",
    note: "Slow sensory activities with a clear ending and nothing to win",
    lead: "Sand garden · ends when you close it",
    terms: ["mindful", "activity", "activities", "sensory", "play", "slow"],
  },
  {
    key: "programmes",
    mark: "▦",
    title: "Programmes",
    note: "Structured journeys you move through at whatever pace suits you",
    lead: "Seven-night wind-down · pick up anywhere",
    terms: ["programme", "programmes", "course", "journey", "module", "structured"],
  },
];

// Presence framing: what you opened and roughly when. No counts, no "you have
// not opened this in 6 days", no completion percentage.
const RECENT = [
  { title: "Three-minute grounding", kind: "Practice", when: "Yesterday evening", mark: "○", tone: "" },
  { title: "Rain over quiet hills", kind: "Sound", when: "Yesterday night", mark: "♫", tone: "info" },
  { title: "Untangle a thought", kind: "Practice", when: "Monday", mark: "⌁", tone: "warm" },
  { title: "Seven-night wind-down", kind: "Programme · day 3", when: "Last week", mark: "▦", tone: "ok" },
];

export default function DesignExplore() {
  const [query, setQuery] = useState("");

  const q = query.trim().toLowerCase();
  const families = useMemo(
    () =>
      q
        ? FAMILIES.filter(
            (f) =>
              f.title.toLowerCase().includes(q) ||
              f.note.toLowerCase().includes(q) ||
              f.terms.some((t) => t.includes(q)),
          )
        : FAMILIES,
    [q],
  );
  const recent = useMemo(
    () =>
      q
        ? RECENT.filter(
            (r) => r.title.toLowerCase().includes(q) || r.kind.toLowerCase().includes(q),
          )
        : RECENT,
    [q],
  );

  return (
    <div className="today-wrap">
      <p className="eyebrow">Explore</p>
      <h1 className="today-greeting">Find what fits this moment.</h1>
      <p className="sub today-lede">
        Start from what you need rather than reading a catalogue. Each family opens with one
        suggestion, and you can ignore it.
      </p>

      <div className="ds-search">
        <span className="ds-search-icon" aria-hidden="true">
          ⌕
        </span>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search a need, a length or a name…"
          aria-label="Search practices, sounds and programmes"
        />
      </div>

      <section className="ds-section" aria-labelledby="need-h">
        <div className="ds-head">
          <h2 id="need-h" className="serif-h">
            Start by need
          </h2>
          <span className="ds-badge">{families.length} of 6</span>
        </div>
        {families.length ? (
          <div className="ds-grid">
            {families.map((f) => (
              <button key={f.key} type="button" className="ds-tile">
                <span className="ds-mark-inline" aria-hidden="true">
                  {f.mark}
                </span>
                <strong>{f.title}</strong>
                <small>{f.note}</small>
                <small>{f.lead}</small>
              </button>
            ))}
          </div>
        ) : (
          <div className="ds-card">
            <p className="sub">
              Nothing matches &ldquo;{query}&rdquo; yet. Try a need (&ldquo;loud
              thoughts&rdquo;), a length (&ldquo;three minutes&rdquo;) or a format
              (&ldquo;sound&rdquo;).
            </p>
          </div>
        )}
      </section>

      <section className="ds-section" aria-labelledby="recent-h">
        <div className="ds-head">
          <h2 id="recent-h" className="serif-h">
            Recently used
          </h2>
          <button type="button" className="text-btn">
            Open your library →
          </button>
        </div>
        {recent.length ? (
          <div className="ds-list">
            {recent.map((r) => (
              <button key={r.title} type="button" className="ds-row">
                <span className={r.tone ? `ds-mark ${r.tone}` : "ds-mark"} aria-hidden="true">
                  {r.mark}
                </span>
                <span className="ds-row-copy">
                  <strong>{r.title}</strong>
                  <small>
                    {r.kind} · {r.when}
                  </small>
                </span>
                <span className="ds-chevron" aria-hidden="true">
                  ›
                </span>
              </button>
            ))}
          </div>
        ) : (
          <div className="ds-card">
            <p className="sub">Nothing recent matches that search.</p>
          </div>
        )}
        <p className="tiny">
          This is here so you can pick something up again without searching for it. It is not a
          record of how often you came, and nothing is removed for going unused.
        </p>
      </section>

      <details className="today-fold">
        <summary>
          <span>Saved and on this device</span>
          <small>Eight saved · four kept for a quiet connection</small>
        </summary>
        <div className="ds-list">
          <button type="button" className="ds-row">
            <span className="ds-mark warm" aria-hidden="true">
              ♡
            </span>
            <span className="ds-row-copy">
              <strong>Saved</strong>
              <small>Things you kept, in the order you kept them</small>
            </span>
            <span className="ds-chevron" aria-hidden="true">
              ›
            </span>
          </button>
          <button type="button" className="ds-row">
            <span className="ds-mark info" aria-hidden="true">
              ↓
            </span>
            <span className="ds-row-copy">
              <strong>Kept on this device</strong>
              <small>Audio and practices that open without a connection</small>
            </span>
            <span className="ds-chevron" aria-hidden="true">
              ›
            </span>
          </button>
        </div>
      </details>

      <details className="today-fold">
        <summary>
          <span>Why there is no feed</span>
          <small>Every list here ends</small>
        </summary>
        <p className="sub">
          Nothing in Explore scrolls forever, autoplays into the next thing, or ranks content by
          how long it holds you. Each family is a short list with an end, and each item tells you
          how long it takes before you start it.
        </p>
      </details>
    </div>
  );
}
