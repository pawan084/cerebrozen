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
// The step mechanics live in `components/RitualSteps` — shared with the
// personal ritual builder (/games/ritual). The words stay here: this screen
// speaks to someone already in bed.
//
// Follows the selected appearance like every authed page (owner decision
// 2026-08-04 — appearance is global; the Sleep tab's Night scoping was
// removed on all clients in the same change).

import Link from "next/link";
import { useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import {
  PacedBreath,
  PromptSequence,
  SLOW_EXHALE,
  ThreeGoodThings,
  WritingStep,
} from "@/components/RitualSteps";

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

        {phase === "dump" && (
          <div className="card">
            <WritingStep
              eyebrow="Step one · Empty the desk"
              title="What's still on your mind?"
              sub="Anything unfinished, worrying, or just loud. Don't organise it — the point is getting it out of your head, not writing well."
              placeholder="Whatever's there…"
              ariaLabel="Brain dump"
              journalTitle="Before bed"
              journalTag="wind-down"
              journalSymbol="moon"
              why="Writing down what's unfinished before bed helps people fall asleep faster than writing about what they've completed (Scullin et al., Journal of Experimental Psychology: General, 2018)."
              onNext={() => setPhase("gratitude")}
            />
          </div>
        )}

        {phase === "gratitude" && (
          <div className="card">
            <ThreeGoodThings
              eyebrow="Step two · Three good things"
              title="What went right today?"
              sub="Small counts. The coffee, a message, getting through it."
              skipLabel="Skip tonight"
              why="Naming three specific good things each night is one of the most replicated positive-psychology exercises, with measured effects on mood up to six months out (Seligman et al., American Psychologist, 2005)."
              onNext={() => setPhase("scan")}
            />
          </div>
        )}

        {phase === "scan" && (
          <div className="card" style={{ textAlign: "center" }}>
            <p className="eyebrow">Step three · Let go, top to bottom</p>
            <PromptSequence
              prompts={SCAN_PROMPTS}
              ms={SCAN_MS}
              ctaLabel="Skip ahead"
              doneCtaLabel="Continue"
              onNext={() => setPhase("settle")}
            />
            <WhyThisWorks text="Progressive muscle relaxation is one of the standard relaxation components of CBT-I, the best-evidenced treatment for insomnia (Lancet Digital Health, 2025)." />
          </div>
        )}

        {phase === "settle" && (
          <div className="card">
            <p className="eyebrow">Step four · Settle</p>
            <PacedBreath
              phases={SLOW_EXHALE}
              cycles={BREATH_CYCLES}
              idleLabel="In 4 · out 6"
              doneLabel="That's it — rest well."
              ctaLabel="I'm settled"
              doneCtaLabel="Finish"
              onNext={() => setPhase("done")}
            />
            <WhyThisWorks text="A longer exhale than inhale is the part of slow breathing with the clearest evidence — it raises vagal tone and slows heart rate. The count matters less than the ratio." />
          </div>
        )}

        {phase === "done" && <Goodnight />}
      </div>
    </>
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
