"use client";

import { useState } from "react";

// Tonight (SLP-01) — redesigned against ref/mobile.html `screens['SLP-01']`
// and ref/web.html `sleep()`.
//
// Sleep lost its tab in the IA ruling (REDESIGN_V2 §6.1), so this screen has to
// do more work than the old tab did: it is now reached deliberately, which
// means the first screenful must be tonight and nothing else. History, the
// diary and the insights all sit below the fold or behind a link.
//
// The wind-down plan is reorderable here because the order is the part people
// actually disagree with — someone who journals before breathing should not
// have to abandon the plan to get it. Reordering is real state in this design;
// it does not persist anywhere.
//
// The insights teaser is the one place this screen could over-claim, so its
// copy is association-only: it names what co-occurred and then says, in the
// same paragraph, that it cannot tell you one caused the other.
//
// Mock data only — no fetch, no session. Every value below is a constant.

type Step = { id: string; title: string; detail: string };

const PLAN: Step[] = [
  { id: "dim", title: "Dim the room and set the phone down", detail: "2 minutes · nothing to read" },
  { id: "breathe", title: "Slow breathing, longer out than in", detail: "5 minutes · optional soft chime" },
  { id: "park", title: "Park the unfinished thoughts on paper", detail: "4 minutes · stays private" },
  { id: "sound", title: "Rain over quiet hills, fading out", detail: "45 minutes · fades to silence" },
];

const TOOLS = [
  { mark: "♫", title: "A sound", note: "Rain, ocean or a low drone" },
  { mark: "▤", title: "A sleep story", note: "The Night Train · 24 minutes" },
  { mark: "○", title: "Body scan", note: "12 minutes · voice optional" },
  { mark: "◌", title: "Guided imagery", note: "Forest, shore or rain room" },
];

const LAST_NIGHT = [
  { n: "7h 10m", label: "time asleep, as you recorded it" },
  { n: "11:45 pm", label: "roughly when sleep began" },
  { n: "6:55 am", label: "final wake" },
];

export default function DesignSleep() {
  const [plan, setPlan] = useState<Step[]>(PLAN);

  const move = (from: number, to: number) => {
    if (to < 0 || to >= plan.length) return;
    const next = plan.slice();
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    setPlan(next);
  };

  return (
    <div className="today-wrap">
      <p className="eyebrow">Tonight</p>
      <h1 className="today-greeting">One clear plan for sleep.</h1>
      <p className="sub today-lede">
        Let the day get smaller before bed. Nothing here has to be finished, and stopping part
        way still counts as having wound down.
      </p>

      <section className="sleep-hero" aria-labelledby="tonight-h">
        <p className="eyebrow">Target bedtime</p>
        <h2 id="tonight-h">10:30 pm, wind-down from 9:45 pm.</h2>
        <p>
          Four gentle steps, about twenty minutes in total. The target is the one you set — it is
          not a rule, and a later night is not a failure.
        </p>
        <div className="ds-actions">
          <button type="button" className="ds-cta">
            Begin the wind-down
          </button>
          <button type="button" className="text-btn">
            Change target bedtime
          </button>
        </div>
      </section>

      <section className="ds-section" aria-labelledby="plan-h">
        <div className="ds-head">
          <h2 id="plan-h" className="serif-h">
            Tonight&rsquo;s wind-down
          </h2>
          <span className="ds-badge ok">Works offline</span>
        </div>
        <p className="sub">
          Put these in whatever order suits you. Some people need to write before they can
          breathe.
        </p>
        <ol className="ds-steps">
          {plan.map((s, i) => (
            <li key={s.id} className="ds-step">
              <span className="ds-step-n" aria-hidden="true">
                {i + 1}
              </span>
              <span className="ds-step-copy">
                <strong>{s.title}</strong>
                <small>{s.detail}</small>
              </span>
              <span className="ds-move">
                <button
                  type="button"
                  onClick={() => move(i, i - 1)}
                  disabled={i === 0}
                  aria-label={`Move “${s.title}” earlier`}
                >
                  ↑
                </button>
                <button
                  type="button"
                  onClick={() => move(i, i + 1)}
                  disabled={i === plan.length - 1}
                  aria-label={`Move “${s.title}” later`}
                >
                  ↓
                </button>
              </span>
            </li>
          ))}
        </ol>
        <p className="tiny">
          Skip any step you do not want tonight. The plan reappears tomorrow in the order you
          left it.
        </p>
      </section>

      <section className="ds-section" aria-labelledby="tools-h">
        <h2 id="tools-h" className="serif-h">
          Quick tools
        </h2>
        <p className="sub">For when you do not want the whole plan, or you are already in bed.</p>
        <div className="ds-grid">
          {TOOLS.map((t) => (
            <button key={t.title} type="button" className="ds-tile">
              <span className="ds-mark-inline" aria-hidden="true">
                {t.mark}
              </span>
              <strong>{t.title}</strong>
              <small>{t.note}</small>
            </button>
          ))}
        </div>
      </section>

      {/* The teaser. Association language is not a disclaimer bolted on the end
          — the observation and its limit are one sentence apart, on purpose. */}
      <section className="ds-section" aria-labelledby="insight-h">
        <div className="ds-head">
          <h2 id="insight-h" className="serif-h">
            What your nights have looked like
          </h2>
          <span className="ds-badge warn">Early signal</span>
        </div>
        <div className="ds-card">
          <p className="eyebrow">Noticed across seven nights</p>
          <p className="sub">
            On the nights your wind-down began before 9:45 pm, your bedtimes sat closer together
            — about forty minutes apart rather than an hour and a half. That is an association
            between two things that happened, not evidence that one caused the other. Seven
            nights is a small sample, and plenty of other things about those evenings were
            different too.
          </p>
          <p className="tiny">
            If you would like to find out more, you could try starting earlier on three evenings
            and see how those nights compare. That is your experiment, not a prescription, and
            nothing changes if you do not run it.
          </p>
          <div className="ds-actions">
            <button type="button" className="text-btn">
              Open sleep insights →
            </button>
          </div>
        </div>
      </section>

      <details className="today-fold">
        <summary>
          <span>Last night</span>
          <small>As you recorded it</small>
        </summary>
        <div className="week-row">
          {LAST_NIGHT.map((m) => (
            <div key={m.label} className="week-cell">
              <strong>{m.n}</strong>
              <small>{m.label}</small>
            </div>
          ))}
        </div>
        <p className="tiny">
          Approximate times are enough, and any field can be left blank. These are your notes
          about your night, not a measurement of it.
        </p>
        <button type="button" className="text-btn">
          Add or edit last night →
        </button>
      </details>

      <details className="today-fold">
        <summary>
          <span>Learn about sleep</span>
          <small>Six short modules</small>
        </summary>
        <p className="sub">
          Educational wellness content informed by CBT-I principles — sleep drive, a consistent
          wake time, what to do when you are awake at 3 am. It is reading and practice, not
          clinical care, and it does not replace seeing someone about a sleep problem.
        </p>
        <button type="button" className="text-btn">
          Open the foundations →
        </button>
      </details>
    </div>
  );
}
