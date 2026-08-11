"use client";

import { useState } from "react";

// Check in (TOD-02) — redesigned against ref/mobile.html `screens['TOD-02']`
// and ref/web.html `checkin()`.
//
// The one thing this screen must not become is a measurement. Our shipped
// check-in ends on a saved value; the prototype ends on a *consequence* — you
// tell it what is here, and it tells you what it will do with that, including
// what it will deliberately not read. So the panel below the picker is
// "what happens next", never a score, a rating, a level or a trend.
//
// Six states, not four: "Overwhelmed" and "Not sure" are both in the spec, and
// "Not sure" is load-bearing — it is the answer that keeps someone who cannot
// name a feeling from being pushed into naming one wrongly.
//
// Mock data only — no fetch, no session. Every value below is a constant.

const FEELINGS = [
  { key: "clear", label: "Clear", note: "Steady", mark: "☀" },
  { key: "anxious", label: "Anxious", note: "Thoughts feel loud", mark: "≈" },
  { key: "low", label: "Low", note: "Everything feels heavy", mark: "↓" },
  { key: "tired", label: "Tired", note: "I need rest", mark: "☾" },
  { key: "overwhelmed", label: "Overwhelmed", note: "Too much at once", mark: "!" },
  { key: "unsure", label: "Not sure", note: "Closest fit right now", mark: "…" },
] as const;

const INTENSITIES = ["Light", "Medium", "Strong"] as const;

// What the check-in leads to, per feeling. Deliberately a *next step*, phrased
// as an offer — never "your score is", never "you are".
const NEXT_STEP: Record<string, { title: string; why: string }> = {
  clear: {
    title: "Nothing needs fixing right now.",
    why: "You said things feel steady, so the next screen offers one short reflection and then gets out of the way.",
  },
  anxious: {
    title: "A short way to make room around loud thoughts.",
    why: "Loud thoughts respond better to something brief than to something long, so the next screen opens a three-minute grounding practice rather than a programme.",
  },
  low: {
    title: "Something small, with a low cost to start.",
    why: "When things feel heavy, effort is the barrier, so the next screen offers the shortest thing that tends to help rather than the most thorough one.",
  },
  tired: {
    title: "Rest first, everything else after.",
    why: "You said you need rest, so the next screen opens tonight’s wind-down instead of a practice that asks for attention you do not have.",
  },
  overwhelmed: {
    title: "One step, and only one.",
    why: "When there is too much at once, a list makes it worse, so the next screen shows a single step with nothing else on it.",
  },
  unsure: {
    title: "Two gentle options, so nothing has to be named.",
    why: "You said you are not sure, so nothing is inferred from that. The next screen offers a choice between settling and writing, and you can pick neither.",
  },
};

export default function DesignCheckin() {
  const [feeling, setFeeling] = useState<string | null>(null);
  const [intensity, setIntensity] = useState<string | null>("Medium");

  const chosen = feeling ? FEELINGS.find((f) => f.key === feeling) : null;
  const next = feeling ? NEXT_STEP[feeling] : null;

  return (
    <div className="today-wrap">
      <p className="eyebrow">Check in</p>
      <h1 className="today-greeting">What is here right now?</h1>
      <p className="sub today-lede">
        Choose the closest fit. This does not create a diagnosis or score, and there is no
        wrong answer to give.
      </p>

      <section className="ds-section" aria-labelledby="feeling-h">
        <div className="ds-head">
          <h2 id="feeling-h" className="serif-h">
            Emotional state
          </h2>
          <span className="ds-badge">Private to you</span>
        </div>
        <div className="mood-row" role="group" aria-label="How you feel now">
          {FEELINGS.map((f) => (
            <button
              key={f.key}
              type="button"
              className={feeling === f.key ? "mood-tile checkin-tile selected" : "mood-tile checkin-tile"}
              aria-pressed={feeling === f.key}
              onClick={() => setFeeling(feeling === f.key ? null : f.key)}
            >
              <span className="ds-mark-inline" aria-hidden="true">
                {f.mark}
              </span>
              <strong>{f.label}</strong>
              <small>{f.note}</small>
            </button>
          ))}
        </div>
      </section>

      <section className="ds-section" aria-labelledby="intensity-h">
        <h2 id="intensity-h" className="serif-h">
          How intense?
        </h2>
        <p className="sub">Optional. Skip it and the check-in still saves.</p>
        <div className="ds-chiprow" role="group" aria-label="Intensity">
          {INTENSITIES.map((x) => (
            <button
              key={x}
              type="button"
              className="ds-chip"
              aria-pressed={intensity === x}
              onClick={() => setIntensity(intensity === x ? null : x)}
            >
              {x}
            </button>
          ))}
        </div>
      </section>

      <section className="ds-section" aria-labelledby="note-h">
        <h2 id="note-h" className="serif-h">
          A private note
        </h2>
        <p className="sub">Optional. A few words are enough, and blank is a complete answer.</p>
        <label className="ds-label" htmlFor="checkin-note">
          What you would rather not lose track of
        </label>
        <textarea
          id="checkin-note"
          className="ds-textarea"
          placeholder="A few words are enough…"
        />
        <p className="tiny">
          This note stays with the check-in. It is kept separate from your journal, and from
          anything the companion is given to read.
        </p>
      </section>

      {/* The consequence, not a result. It only appears once there is something
          to act on — before that it would be describing a step that has no
          reason yet. */}
      {chosen && next ? (
        <section className="checkin-next" aria-labelledby="next-h" aria-live="polite">
          <p className="eyebrow">What happens next</p>
          <h2 id="next-h">{next.title}</h2>
          <p className="sub">{next.why}</p>
          <ul className="checkin-reads">
            <li>
              <span className="yes" aria-hidden="true">
                ✓
              </span>
              <span>
                Uses <b>this check-in</b>
                {intensity ? ` — ${chosen.label.toLowerCase()} at ${intensity.toLowerCase()} intensity` : ` — ${chosen.label.toLowerCase()}`}
              </span>
            </li>
            <li>
              <span className="yes" aria-hidden="true">
                ✓
              </span>
              <span>
                Uses <b>your recent sleep entries</b>, because being short of rest changes what
                is worth offering
              </span>
            </li>
            <li>
              <span className="no" aria-hidden="true">
                ✕
              </span>
              <span>
                Does not use <b>your journal</b> — that stays off unless you turn it on
              </span>
            </li>
            <li>
              <span className="no" aria-hidden="true">
                ✕
              </span>
              <span>
                Does not produce <b>a score, a rating or a diagnosis</b>, and nothing here goes
                to an organisation
              </span>
            </li>
          </ul>
          <div className="ds-actions">
            <button type="button" className="ds-cta">
              Save and see the step
            </button>
            <button type="button" className="text-btn">
              Save without a suggestion
            </button>
          </div>
        </section>
      ) : (
        <section className="ds-card" aria-live="polite">
          <p className="sub">
            Once you choose a state, this is where you will see what happens next — which step
            it opens, and what it reads to decide. Nothing here becomes a number.
          </p>
        </section>
      )}

      <details className="today-fold">
        <summary>
          <span>What this is for</span>
          <small>And what it is not</small>
        </summary>
        <p className="sub">
          A check-in gives the app one honest fact about right now, so that what it offers next
          fits the moment rather than an average. It is not an assessment, it is not read by a
          clinician, and it is not compared with anyone else.
        </p>
        <p className="tiny">
          You can check in as often or as rarely as suits you. Missed days are simply days
          without a check-in — nothing counts them.
        </p>
      </details>
    </div>
  );
}
