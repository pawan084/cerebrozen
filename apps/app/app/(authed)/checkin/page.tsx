"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";

// Check in (TOD-02), graduated from /design/checkin on 2026-08-12.
//
// The screen's job is that a check-in ends on a CONSEQUENCE, not a value: you
// say what is here, and it tells you what it will do with that — including what
// it will deliberately not read. So the panel below the picker is "what happens
// next", never a score, a rating, a level or a trend.
//
// Three things changed on the way out of the design surface, each because the
// mock was allowed to assert what the product has to prove:
//
//  1. The first state was "Clear". The wire vocabulary is Good · Anxious · Low ·
//     Tired · Overwhelmed · Not sure (backend/app/services/moods.py), and
//     "Clear" is exactly the drift that was removed from Android's check-in
//     detail screen the same week. `name` is the value the server reads, so it
//     is not a place to be expressive.
//  2. The mock stated "Does not use your journal" as a flat fact. That is only
//     true while `journal_memory` consent is off — and it defaults off, so the
//     sentence would have been right for most people and wrong for exactly the
//     people who had changed it. The list now reads /users/me/consent and says
//     what is actually switched on.
//  3. "Save and see the step" promised a per-feeling destination that does not
//     exist. Saving lands you back on Today, where the hero already reads the
//     plan; the button says that instead.
//
// What did NOT graduate: the mock's per-feeling "what happens next" copy, which
// described routing this app does not do (`tired` → "opens tonight's wind-down"
// is not what a check-in triggers). The honest version names the one thing a
// check-in genuinely changes — the signals Today's next step is computed from.

const MOODS = [
  { emoji: "🙂", name: "Good", note: "Steady", symbol: "sparkles" },
  { emoji: "😰", name: "Anxious", note: "Thoughts feel loud", symbol: "exclamationmark.triangle" },
  { emoji: "😔", name: "Low", note: "Everything feels heavy", symbol: "moon" },
  { emoji: "😪", name: "Tired", note: "I need rest", symbol: "drop" },
  { emoji: "😵", name: "Overwhelmed", note: "Too much at once", symbol: "exclamationmark.triangle" },
  { emoji: "🤔", name: "Not sure", note: "Closest fit right now", symbol: "minus" },
] as const;

// The API stores intensity on a 1–5 scale (schemas MoodCreate, ge=1 le=5) and
// services/insights.py averages it into a stability figure, so this mapping is
// load-bearing rather than cosmetic. Three labels deliberately sit INSIDE the
// range: recording a three-way choice as 1 or 5 would claim the extremes of a
// scale the user was never shown, and would drag that average further than the
// answer warrants.
const INTENSITIES = [
  { label: "Light", value: 2 },
  { label: "Medium", value: 3 },
  { label: "Strong", value: 4 },
] as const;

type Consent = {
  mood_history: boolean;
  ai_memory: boolean;
  voice_storage: boolean;
  model_training: boolean;
  journal_memory: boolean;
  sleep_history: boolean;
};

export default function CheckinPage() {
  const [mood, setMood] = useState<string | null>(null);
  const [intensity, setIntensity] = useState<number | null>(3);
  const [note, setNote] = useState("");
  const [consent, setConsent] = useState<Consent | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // A failed read leaves `consent` null, and the list below then says it
    // could not check rather than guessing a state.
    api<Consent>("/users/me/consent").then(setConsent).catch(() => setConsent(null));
  }, []);

  const chosen = mood ? MOODS.find((m) => m.name === mood) ?? null : null;

  async function save() {
    if (!chosen || saving) return;
    setSaving(true);
    setError(null);
    try {
      await api("/moods", {
        method: "POST",
        body: JSON.stringify({
          mood: chosen.name,
          note: note.trim() || chosen.note,
          symbol: chosen.symbol,
          intensity: intensity ?? 3,
        }),
      });
      setSaved(true);
    } catch {
      // Same rule the home check-in learned the hard way: never show a warm
      // confirmation over a write that failed.
      setError("We couldn't save that check-in. Try again when you're ready.");
    } finally {
      setSaving(false);
    }
  }

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
          {MOODS.map((m) => (
            <button
              key={m.name}
              type="button"
              className={mood === m.name ? "mood-tile checkin-tile selected" : "mood-tile checkin-tile"}
              aria-pressed={mood === m.name}
              onClick={() => {
                setMood(mood === m.name ? null : m.name);
                setSaved(false);
              }}
            >
              <span className="ds-mark-inline" aria-hidden="true">
                {m.emoji}
              </span>
              <strong>{m.name}</strong>
              <small>{m.note}</small>
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
              key={x.label}
              type="button"
              className="ds-chip"
              aria-pressed={intensity === x.value}
              onClick={() => setIntensity(intensity === x.value ? null : x.value)}
            >
              {x.label}
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
          maxLength={255}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
        <p className="tiny">
          This note stays with the check-in. It is kept separate from your journal, and the
          companion is given your feeling — never these words.
        </p>
      </section>

      {chosen ? (
        <section className="checkin-next" aria-labelledby="next-h" aria-live="polite">
          <p className="eyebrow">What happens next</p>
          <h2 id="next-h">This becomes one signal, not a verdict.</h2>
          <p className="sub">
            Today&rsquo;s next step is chosen from a few recent signals. Here is exactly which of
            them are switched on for you right now.
          </p>
          <ul className="checkin-reads">
            <li>
              <span className="yes" aria-hidden="true">
                ✓
              </span>
              <span>
                Uses <b>this check-in</b> — {chosen.name.toLowerCase()}
                {intensity ? ` at ${INTENSITIES.find((i) => i.value === intensity)?.label.toLowerCase()} intensity` : ""}
              </span>
            </li>
            {consent === null ? (
              <li>
                <span className="no" aria-hidden="true">
                  ✕
                </span>
                <span>
                  We couldn&rsquo;t read your privacy settings just now, so this list is
                  incomplete rather than guessed. They are in{" "}
                  <Link href="/account">your account</Link>.
                </span>
              </li>
            ) : (
              <>
                <li>
                  <span className={consent.sleep_history ? "yes" : "no"} aria-hidden="true">
                    {consent.sleep_history ? "✓" : "✕"}
                  </span>
                  <span>
                    {consent.sleep_history ? "Uses" : "Does not use"} <b>your recent sleep</b>
                    {consent.sleep_history
                      ? ", because being short of rest changes what is worth offering"
                      : " — sleep history is switched off"}
                  </span>
                </li>
                <li>
                  <span className={consent.journal_memory ? "yes" : "no"} aria-hidden="true">
                    {consent.journal_memory ? "✓" : "✕"}
                  </span>
                  <span>
                    {consent.journal_memory ? "Uses" : "Does not use"} <b>your journal</b>
                    {consent.journal_memory
                      ? " — you switched journal memory on, and it reads entry titles"
                      : " — that stays off unless you turn it on"}
                  </span>
                </li>
                <li>
                  <span className={consent.mood_history ? "yes" : "no"} aria-hidden="true">
                    {consent.mood_history ? "✓" : "✕"}
                  </span>
                  <span>
                    {consent.mood_history ? "Uses" : "Does not use"} <b>your earlier check-ins</b>
                    {consent.mood_history ? "" : " — mood history is switched off, so today stands alone"}
                  </span>
                </li>
              </>
            )}
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

          {error ? <p className="form-error">{error}</p> : null}

          {saved ? (
            <div className="ds-actions" aria-live="polite">
              <Link href="/home" className="ds-cta">
                Saved — back to Today
              </Link>
              <Link href="/insights" className="text-btn">
                See what it adds up to
              </Link>
            </div>
          ) : (
            <div className="ds-actions">
              <button type="button" className="ds-cta" onClick={save} disabled={saving}>
                {saving ? "Saving…" : "Save this check-in"}
              </button>
            </div>
          )}
        </section>
      ) : (
        <section className="ds-card" aria-live="polite">
          <p className="sub">
            Once you choose a state, this is where you will see what happens next — what it
            reads to decide, and what it leaves alone. Nothing here becomes a number.
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
