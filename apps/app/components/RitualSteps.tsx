"use client";

// Shared runners for the app's guided routines.
//
// Three surfaces now walk someone through a sequence of steps — the wind-down
// ritual (/sleep/ritual), the Toolkit's grounding card (/games) and the
// personal ritual builder (/games/ritual). Without this file they would hold
// three copies of the same paced-breathing loop and two of the 5-4-3-2-1
// copy — and that copy is hand-synced with Android `strings.xml ground_step*`,
// so a second copy is a second thing to forget when it changes.
//
// Each primitive owns its mechanics (timers, dots, the journal write) and
// takes its words from the caller: the wind-down speaks to someone already in
// bed, the builder to someone assembling a routine at any hour, and that
// difference is the whole point of those screens.

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import { Icon } from "@/components/icons";

// ── Paced breathing ─────────────────────────────────────────────────────
export type BreathPhase = { label: string; ms: number; state: string };

// In 4, out 6 — no holds. Matches the iOS/Android "reset" preset; a longer
// exhale than inhale is the part of slow breathing with real evidence behind
// it. `slow-out` stretches the orb transition so it doesn't finish early and
// sit still through the last two seconds of the exhale.
export const SLOW_EXHALE: readonly BreathPhase[] = [
  { label: "Breathe in", ms: 4000, state: "in" },
  { label: "Breathe out", ms: 6000, state: "out slow-out" },
];

// Four equal counts — the box pattern the iOS/Android Toolkits ship.
export const BOX_BREATH: readonly BreathPhase[] = [
  { label: "Breathe in", ms: 4000, state: "in" },
  { label: "Hold", ms: 4000, state: "hold" },
  { label: "Breathe out", ms: 4000, state: "out" },
  { label: "Hold", ms: 4000, state: "hold" },
];

export function PacedBreath({
  phases,
  cycles,
  idleLabel,
  doneLabel,
  ctaLabel,
  doneCtaLabel,
  onNext,
}: {
  phases: readonly BreathPhase[];
  cycles: number;
  idleLabel: string;
  doneLabel: string;
  ctaLabel: string;
  doneCtaLabel: string;
  onNext: () => void;
}) {
  const [running, setRunning] = useState(true);
  // One counter, everything derived from it. The obvious alternative — bumping
  // a round counter from inside the phase updater — makes the updater impure,
  // and React double-invokes updaters in StrictMode, so the breath count runs
  // at double speed in dev.
  const [tick, setTick] = useState(0);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const phase = tick % phases.length;
  const round = Math.floor(tick / phases.length);
  const done = round >= cycles;

  useEffect(() => {
    if (!running || done) return;
    timer.current = setTimeout(() => setTick((n) => n + 1), phases[phase].ms);
    return () => clearTimeout(timer.current);
  }, [running, phase, done, phases]);

  return (
    <>
      <div className="onb-breathe">
        <p className="onb-breathe-label">
          {done ? doneLabel : running ? phases[phase].label : idleLabel}
        </p>
        <div
          className={`onb-breathe-orb ${running && !done ? phases[phase].state : ""}`}
          aria-hidden="true"
        />
        {!done && (
          <button
            className="pill-btn tinted"
            onClick={() => setRunning((r) => !r)}
            aria-pressed={running}
          >
            <Icon.play size={14} /> {running ? "Pause" : "Resume"}
          </button>
        )}
        <p className="meta">
          {Math.min(round, cycles)} of {cycles} breaths
        </p>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button className="btn" onClick={onNext}>
          {done ? doneCtaLabel : ctaLabel}
        </button>
      </div>
    </>
  );
}

// ── Auto-advancing prompt line (body scan) ──────────────────────────────
export function PromptSequence({
  prompts,
  ms,
  ctaLabel,
  doneCtaLabel,
  onNext,
}: {
  prompts: readonly string[];
  ms: number;
  ctaLabel: string;
  doneCtaLabel: string;
  onNext: () => void;
}) {
  const [i, setI] = useState(0);
  const last = i >= prompts.length - 1;

  useEffect(() => {
    if (last) return;
    const t = setTimeout(() => setI((n) => n + 1), ms);
    return () => clearTimeout(t);
  }, [i, last, ms]);

  return (
    <>
      <p className="serif-h" style={{ fontSize: 24, margin: "22px 0", minHeight: 64 }}>
        {prompts[i]}
      </p>
      <div style={{ display: "flex", gap: 6, justifyContent: "center" }} aria-hidden="true">
        {prompts.map((_, n) => (
          <div
            key={n}
            style={{
              width: 7,
              height: 7,
              borderRadius: 999,
              background: n <= i ? "var(--lav)" : "var(--line)",
            }}
          />
        ))}
      </div>
      <div style={{ marginTop: 18 }}>
        {/* Always skippable — a body scan you're stuck inside is not relaxing. */}
        <button className="btn" onClick={onNext}>
          {last ? doneCtaLabel : ctaLabel}
        </button>
      </div>
    </>
  );
}

// ── 5-4-3-2-1 sensory steps ─────────────────────────────────────────────
// Copy hand-synced with Android `strings.xml ground_step*`.
export const GROUND_STEPS = [
  { title: "5 things you can see", hint: "Look around — name five, slowly." },
  { title: "4 things you can feel", hint: "Textures, temperature, your feet on the floor." },
  { title: "3 things you can hear", hint: "Near, then far." },
  { title: "2 things you can smell", hint: "Or two scents you like." },
  { title: "1 thing you can taste", hint: "Or one slow, full breath." },
];

export function SensorySteps({
  // The Toolkit card loops back to the start (it's a standalone tool you dip
  // into); inside a ritual the last step hands over to the next block.
  onFinish,
  finishLabel = "Start over",
}: {
  onFinish?: () => void;
  finishLabel?: string;
}) {
  const [step, setStep] = useState(0);
  const last = step === GROUND_STEPS.length - 1;
  const s = GROUND_STEPS[step];
  return (
    <div>
      <p className="eyebrow">5 · 4 · 3 · 2 · 1</p>
      <h3 style={{ margin: "4px 0 4px" }}>{s.title}</h3>
      <p className="sub">{s.hint}</p>
      <button
        className="pill-btn tinted"
        onClick={() => {
          if (!last) return setStep(step + 1);
          if (onFinish) return onFinish();
          setStep(0);
        }}
        style={{ marginTop: 10 }}
      >
        {last ? finishLabel : "Next"}
      </button>
      {step > 0 && (
        <button
          onClick={() => setStep(step - 1)}
          style={{
            background: "none",
            border: "none",
            cursor: "pointer",
            font: "inherit",
            color: "var(--muted)",
            marginLeft: 12,
          }}
        >
          Back
        </button>
      )}
    </div>
  );
}

// ── Writing step ────────────────────────────────────────────────────────
// The privacy posture is the point: what someone writes here stays in the
// browser tab unless they press Save, and the screen says so before they
// start. These prompts invite the most unguarded writing a user will do all
// day, and silence about where it goes is not the same as discarding it.
export function WritingStep({
  eyebrow,
  title,
  sub,
  placeholder,
  ariaLabel,
  rows = 7,
  journalTitle,
  journalTag,
  journalSymbol = "book",
  why,
  ctaLabel = "Continue",
  onNext,
}: {
  eyebrow: string;
  title: string;
  sub: string;
  placeholder: string;
  ariaLabel: string;
  rows?: number;
  journalTitle: string;
  journalTag: string;
  journalSymbol?: string;
  why: string;
  ctaLabel?: string;
  onNext: () => void;
}) {
  const [text, setText] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  async function saveToJournal() {
    if (!text.trim() || busy) return;
    setBusy(true);
    try {
      await api("/journal", {
        method: "POST",
        body: JSON.stringify({
          title: journalTitle,
          body: text,
          tags: [journalTag],
          symbol: journalSymbol,
        }),
      });
      setSaved(true);
    } catch {
      /* keep the text on screen; the user can copy it or try again */
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <p className="eyebrow">{eyebrow}</p>
      <h2 className="serif-h" style={{ marginTop: 6 }}>
        {title}
      </h2>
      <p className="sub">{sub}</p>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        rows={rows}
        style={{ width: "100%", marginTop: 12 }}
        aria-label={ariaLabel}
      />
      <p className="footnote">
        This stays on your device and is never sent anywhere — unless you choose to save it.
      </p>
      <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
        <button className="btn" onClick={onNext}>
          {ctaLabel}
        </button>
        <button
          className="btn ghost"
          onClick={saveToJournal}
          disabled={!text.trim() || busy || saved}
        >
          {saved ? "Saved to journal" : busy ? "Saving…" : "Save to journal"}
        </button>
      </div>
      <WhyThisWorks text={why} />
    </>
  );
}

// ── Three good things ───────────────────────────────────────────────────
export function ThreeGoodThings({
  eyebrow,
  title,
  sub,
  why,
  skipLabel,
  continueLabel = "Continue",
  onNext,
}: {
  eyebrow: string;
  title: string;
  sub: string;
  why: string;
  skipLabel: string;
  continueLabel?: string;
  onNext: () => void;
}) {
  const [items, setItems] = useState(["", "", ""]);
  const set = (i: number, v: string) =>
    setItems((prev) => prev.map((x, j) => (j === i ? v : x)));
  // Not gated on filling all three: a day where only one thing comes to mind
  // is exactly the day not to be blocked by a form.
  const any = items.some((s) => s.trim());

  return (
    <>
      <p className="eyebrow">{eyebrow}</p>
      <h2 className="serif-h" style={{ marginTop: 6 }}>
        {title}
      </h2>
      <p className="sub">{sub}</p>
      {items.map((v, i) => (
        <input
          key={i}
          value={v}
          onChange={(e) => set(i, e.target.value)}
          placeholder={`Good thing ${i + 1}`}
          aria-label={`Good thing ${i + 1}`}
          style={{ width: "100%", marginTop: 8 }}
        />
      ))}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button className="btn" onClick={onNext}>
          {any ? continueLabel : skipLabel}
        </button>
      </div>
      <WhyThisWorks text={why} />
    </>
  );
}
