"use client";

import { useState } from "react";

// Today (TOD-01) — redesigned against ref/mobile.html + ref/web.html.
//
// The whole point of this screen in the spec is that it is ONE decision. Our
// shipped Home is a dashboard: check-in rail, continue card, tonight, this week,
// quick tiles, all competing at equal weight. Here the recommendation is the
// only thing at full volume, and everything else is either a quiet second line
// or folded into a <details> the user opens deliberately.
//
// Mock data only — no fetch, no session. Every value below is a constant.

const MOODS = [
  { key: "clear", label: "Clear", note: "Steady" },
  { key: "anxious", label: "Anxious", note: "Loud thoughts" },
  { key: "low", label: "Low", note: "Heavy" },
  { key: "tired", label: "Tired", note: "Need rest" },
] as const;

// The day plan is presence-framed: it records what happened, never what was
// missed. There is no "2 of 4 done", no ring to complete, no streak.
const DAY = [
  { when: "Morning", what: "Morning check-in", state: "done" },
  { when: "Midday", what: "Two-minute reset", state: "open" },
  { when: "Evening", what: "Untangle a thought", state: "open" },
  { when: "Night", what: "Wind-down ritual", state: "open" },
] as const;

const WEEK = [
  { n: "5", label: "days you showed up" },
  { n: "12", label: "practices opened" },
  { n: "6h 48m", label: "average night" },
];

export default function DesignToday() {
  const [mood, setMood] = useState<string | null>("anxious");

  return (
    <div className="today-wrap">
      <p className="eyebrow">Wednesday · 6 August</p>
      <h1 className="today-greeting">
        Good morning,
        <br />
        take it gently.
      </h1>
      <p className="sub today-lede">
        Choose one useful next step without browsing the whole product.
      </p>

      {/* The one thing at full volume. */}
      <section className="today-hero" aria-labelledby="next-step">
        <p className="eyebrow">Your next helpful step</p>
        <h2 id="next-step" className="today-hero-title">
          Make a little room around loud thoughts.
        </h2>
        <ul className="today-chips" aria-label="What this involves">
          <li>3 minutes</li>
          <li>Works offline</li>
          <li>Nothing to score</li>
        </ul>
        {/* Credibility: the recommendation explains itself, including what it
            did NOT read. The spec makes this a product surface, not a footnote.

            DO NOT hardcode "it did not use your journal" when this is wired. That
            sentence is only true for the RULE generator. The AI planner sends
            recent journal *titles* (never bodies, and only under journal_memory
            consent) — backend/app/services/agentic.py::_recent_signals — so a flat
            claim would be a false privacy statement half the time. `plan.source`
            ("ai" | "rule") is already on the wire; branch on it, as the Android
            twin does via heroWhyRes(source). */}
        <p className="today-why">
          You said you were <b>anxious</b> at medium intensity this morning, so this is a
          short, low-effort practice rather than a long one. It read your check-in and your
          sleep entries, and nothing you have written.
        </p>
        <div className="today-actions">
          <button type="button" className="btn btn-primary today-cta">
            Begin grounding
          </button>
          <button type="button" className="text-btn">
            Choose something else
          </button>
        </div>
      </section>

      {/* Second decision, deliberately quieter. */}
      <section className="today-checkin" aria-labelledby="checkin-h">
        <h2 id="checkin-h" className="serif-h">
          What is here right now?
        </h2>
        <p className="sub">This does not create a diagnosis or a score.</p>
        <div className="mood-row" role="group" aria-label="How you feel now">
          {MOODS.map((m) => (
            <button
              key={m.key}
              type="button"
              className={mood === m.key ? "mood-tile selected" : "mood-tile"}
              aria-pressed={mood === m.key}
              onClick={() => setMood(mood === m.key ? null : m.key)}
            >
              <strong>{m.label}</strong>
              <small>{m.note}</small>
            </button>
          ))}
        </div>
        <button type="button" className="text-btn">
          Add intensity or a private note →
        </button>
      </section>

      {/* Everything else folds away, so the first screenful stays one decision. */}
      <details className="today-fold">
        <summary>
          <span>Your day</span>
          <small>One thing done, three still open</small>
        </summary>
        <ul className="day-list">
          {DAY.map((d) => (
            <li key={d.when} className={d.state === "done" ? "day-row done" : "day-row"}>
              <span className="day-when">{d.when}</span>
              <span className="day-what">{d.what}</span>
              <span className="day-state">{d.state === "done" ? "Done" : "Open"}</span>
            </li>
          ))}
        </ul>
        <p className="tiny">
          Flexible, not a streak. Do what helps and skip what does not — blank slots are
          simply blank.
        </p>
      </details>

      <details className="today-fold">
        <summary>
          <span>Tonight</span>
          <small>Wind-down starts around 10:30 pm</small>
        </summary>
        <p className="sub">
          A four-step ritual: dim the room, a slow breath, one page of journal, then rain
          over quiet hills with a fade-out timer.
        </p>
        <button type="button" className="text-btn">
          Open tonight&rsquo;s plan →
        </button>
      </details>

      <details className="today-fold">
        <summary>
          <span>This week</span>
          <small>What showing up looked like</small>
        </summary>
        <div className="week-row">
          {WEEK.map((w) => (
            <div key={w.label} className="week-cell">
              <strong>{w.n}</strong>
              <small>{w.label}</small>
            </div>
          ))}
        </div>
        <p className="tiny">
          These count the days you were here. Nothing here counts the days you were not.
        </p>
      </details>
    </div>
  );
}
