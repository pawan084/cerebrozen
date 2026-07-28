"use client";

// Wind-down ritual — a four-step guided routine before bed.
//
// Adapted from the sibling build's SleepRitualPage (brain dump → gratitude →
// body scan → breathing). Two deliberate changes on the way in:
//
//  1. The reference ends on **4-7-8 breathing**. That specific ratio is a
//     popularised pattern with little direct evidence, and this app's
//     credibility rule is that a claim carries its source. The final step
//     reuses the slow-exhale pattern the iOS/Android breathe engines already
//     ship (in 4, out 6 — a longer exhale than inhale is the part with actual
//     parasympathetic evidence behind it), rather than adding a fourth,
//     unevidenced ratio to the app's vocabulary.
//  2. The brain dump **never leaves the device** unless the user explicitly
//     saves it. The reference discards it silently; here it says so, because
//     "write down everything on your mind" right before bed invites the most
//     unguarded writing a user will do all day.
//
// The Sleep tab is `.theme-night`-scoped, so this page stays Night in Dawn too.

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import { Icon } from "@/components/icons";

type Phase = "dump" | "gratitude" | "scan" | "settle" | "done";
const ORDER: Phase[] = ["dump", "gratitude", "scan", "settle"];

const SCAN_PROMPTS = [
  "Let your jaw come unclenched.",
  "Drop your shoulders away from your ears.",
  "Open your hands.",
  "Soften your belly.",
  "Let your legs get heavy.",
  "Let the whole body sink into the bed.",
];
const SCAN_MS = 6000;

// In 4, out 6 — no holds. Matches the iOS/Android "reset" preset.
const BREATH = [
  { label: "Breathe in", ms: 4000, state: "in" },
  { label: "Breathe out", ms: 6000, state: "out slow-out" },
] as const;
const BREATH_CYCLES = 6;

export default function SleepRitual() {
  const [phase, setPhase] = useState<Phase>("dump");
  const step = ORDER.indexOf(phase);

  return (
    <>
      <AppHeader eyebrow="Wind down" title="A ritual for tonight" />
      <div className="page-body">
        <div className="card" style={{ marginBottom: 16 }}>
          <div style={{ display: "flex", gap: 6 }} aria-hidden="true">
            {ORDER.map((p, i) => (
              <div
                key={p}
                style={{
                  flex: 1, height: 4, borderRadius: 999,
                  background: phase === "done" || step >= i ? "var(--lav)" : "var(--line)",
                }}
              />
            ))}
          </div>
          <p className="meta" style={{ marginTop: 8 }}>
            {phase === "done" ? "Finished" : `Step ${step + 1} of ${ORDER.length}`}
          </p>
        </div>

        {phase === "dump" && <BrainDump onNext={() => setPhase("gratitude")} />}
        {phase === "gratitude" && <Gratitude onNext={() => setPhase("scan")} />}
        {phase === "scan" && <BodyScan onNext={() => setPhase("settle")} />}
        {phase === "settle" && <Settle onNext={() => setPhase("done")} />}
        {phase === "done" && <Goodnight />}
      </div>
    </>
  );
}

// ── 1. Brain dump ───────────────────────────────────────────────────────
function BrainDump({ onNext }: { onNext: () => void }) {
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
          title: "Before bed", body: text, tags: ["wind-down"], symbol: "moon",
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
    <div className="card">
      <p className="eyebrow">Step one · Empty the desk</p>
      <h2 className="serif-h" style={{ marginTop: 6 }}>What&apos;s still on your mind?</h2>
      <p className="sub">
        Anything unfinished, worrying, or just loud. Don&apos;t organise it — the point is
        getting it out of your head, not writing well.
      </p>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Whatever's there…"
        rows={7}
        style={{ width: "100%", marginTop: 12 }}
        aria-label="Brain dump"
      />
      {/* Said plainly: this is the most unguarded writing a user will do all
          day, and they should know where it goes before they start. */}
      <p className="footnote">
        This stays on your device and is never sent anywhere — unless you choose to save it.
      </p>
      <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
        <button className="btn" onClick={onNext}>Continue</button>
        <button className="btn ghost" onClick={saveToJournal} disabled={!text.trim() || busy || saved}>
          {saved ? "Saved to journal" : busy ? "Saving…" : "Save to journal"}
        </button>
      </div>
      <WhyThisWorks text="Writing down what's unfinished before bed helps people fall asleep faster than writing about what they've completed (Scullin et al., Journal of Experimental Psychology: General, 2018)." />
    </div>
  );
}

// ── 2. Three good things ────────────────────────────────────────────────
function Gratitude({ onNext }: { onNext: () => void }) {
  const [items, setItems] = useState(["", "", ""]);
  const set = (i: number, v: string) =>
    setItems((prev) => prev.map((x, j) => (j === i ? v : x)));
  // Not gated on filling all three: a night where only one thing comes to mind
  // is exactly the night not to be blocked by a form.
  const any = items.some((s) => s.trim());

  return (
    <div className="card">
      <p className="eyebrow">Step two · Three good things</p>
      <h2 className="serif-h" style={{ marginTop: 6 }}>What went right today?</h2>
      <p className="sub">Small counts. The coffee, a message, getting through it.</p>
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
        <button className="btn" onClick={onNext}>{any ? "Continue" : "Skip tonight"}</button>
      </div>
      <WhyThisWorks text="Naming three specific good things each night is one of the most replicated positive-psychology exercises, with measured effects on mood up to six months out (Seligman et al., American Psychologist, 2005)." />
    </div>
  );
}

// ── 3. Body scan ────────────────────────────────────────────────────────
function BodyScan({ onNext }: { onNext: () => void }) {
  const [i, setI] = useState(0);
  const last = i >= SCAN_PROMPTS.length - 1;

  useEffect(() => {
    if (last) return;
    const t = setTimeout(() => setI((n) => n + 1), SCAN_MS);
    return () => clearTimeout(t);
  }, [i, last]);

  return (
    <div className="card" style={{ textAlign: "center" }}>
      <p className="eyebrow">Step three · Let go, top to bottom</p>
      <p className="serif-h" style={{ fontSize: 24, margin: "22px 0", minHeight: 64 }}>
        {SCAN_PROMPTS[i]}
      </p>
      <div style={{ display: "flex", gap: 6, justifyContent: "center" }} aria-hidden="true">
        {SCAN_PROMPTS.map((_, n) => (
          <div key={n} style={{
            width: 7, height: 7, borderRadius: 999,
            background: n <= i ? "var(--lav)" : "var(--line)",
          }} />
        ))}
      </div>
      <div style={{ marginTop: 18 }}>
        {/* Always skippable — a body scan you're stuck inside is not relaxing. */}
        <button className="btn" onClick={onNext}>{last ? "Continue" : "Skip ahead"}</button>
      </div>
      <WhyThisWorks text="Progressive muscle relaxation is one of the standard relaxation components of CBT-I, the best-evidenced treatment for insomnia (Lancet Digital Health, 2025)." />
    </div>
  );
}

// ── 4. Settle (in 4, out 6) ─────────────────────────────────────────────
function Settle({ onNext }: { onNext: () => void }) {
  const [running, setRunning] = useState(true);
  const [phase, setPhase] = useState(0);
  const [cycles, setCycles] = useState(0);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const done = cycles >= BREATH_CYCLES;

  useEffect(() => {
    if (!running || done) return;
    timer.current = setTimeout(() => {
      setPhase((p) => {
        const next = (p + 1) % BREATH.length;
        if (next === 0) setCycles((c) => c + 1);
        return next;
      });
    }, BREATH[phase].ms);
    return () => clearTimeout(timer.current);
  }, [running, phase, done]);

  return (
    <div className="card">
      <p className="eyebrow">Step four · Settle</p>
      <div className="onb-breathe">
        <p className="onb-breathe-label">
          {done ? "That's it — rest well." : running ? BREATH[phase].label : "In 4 · out 6"}
        </p>
        <div className={`onb-breathe-orb ${running && !done ? BREATH[phase].state : ""}`} aria-hidden="true" />
        {!done && (
          <button className="pill-btn tinted" onClick={() => setRunning((r) => !r)} aria-pressed={running}>
            <Icon.play size={14} /> {running ? "Pause" : "Resume"}
          </button>
        )}
        <p className="meta">{Math.min(cycles, BREATH_CYCLES)} of {BREATH_CYCLES} breaths</p>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button className="btn" onClick={onNext}>{done ? "Finish" : "I'm settled"}</button>
      </div>
      <WhyThisWorks text="A longer exhale than inhale is the part of slow breathing with the clearest evidence — it raises vagal tone and slows heart rate. The count matters less than the ratio." />
    </div>
  );
}

function Goodnight() {
  return (
    <div className="card" style={{ textAlign: "center" }}>
      <h2 className="serif-h" style={{ fontSize: 22 }}>Goodnight.</h2>
      <p className="sub">Nothing else is needed tonight.</p>
      <div style={{ display: "flex", gap: 8, justifyContent: "center", marginTop: 14, flexWrap: "wrap" }}>
        <Link href="/sleep" className="btn ghost">Back to sleep</Link>
      </div>
    </div>
  );
}
