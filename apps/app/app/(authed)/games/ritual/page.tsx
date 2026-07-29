"use client";

// Your ritual — assemble a short routine from the Toolkit and anchor it to
// something you already do.
//
// Fifth adoption from the `workspace/cerebro` sibling build (its
// `RitualBuilderPage`). What that page is: a block sequencer — toggle eight
// exercises on, reorder them, run them back to back, save to localStorage.
// Three of its eight blocks did not survive the trip:
//
//  • **4-7-8 breathing.** Already rejected once, when the wind-down ritual
//    landed: a popularised ratio without direct evidence, in an app where the
//    other exercises carry a citation. Adding it here would reintroduce it
//    through a side door.
//  • **Disidentification** ("I have thoughts, but I am not my thoughts") —
//    Psychosynthesis (Assagioli). Recorded as skipped in the original
//    adoption assessment on evidence grounds; nothing has changed.
//  • **Affirmation reading** ("I am enough. I am capable.") — this is the one
//    worth spelling out. Generic positive self-statements *lower* mood and
//    self-regard in exactly the people most likely to be handed them: those
//    with low self-esteem (Wood, Perunovic & Lee, Psychological Science,
//    2009). A wellness app's users are selected for low mood. That makes it
//    not a taste call but a small harm, and it is out.
//
// What the reference *lacks*, and what actually justifies this screen, is the
// cue. Assembling a nicer sequence changes nothing about whether it gets done;
// deciding **when** does — an if-then plan attached to something already in
// the day is one of the better-replicated findings in behaviour change
// (Gollwitzer & Sheeran, 2006, ~94 studies). So the anchor is the first thing
// on the page, not a footnote.
//
// Everything selectable here is already in the app with its own provenance —
// this screen invents no new exercise, it only sequences them.

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import {
  BOX_BREATH,
  PacedBreath,
  PromptSequence,
  SLOW_EXHALE,
  SensorySteps,
  ThreeGoodThings,
  WritingStep,
} from "@/components/RitualSteps";

type BlockId =
  | "settle" | "box" | "ground" | "scan" | "dump" | "good" | "intention" | "reflect";

type Block = { id: BlockId; label: string; note: string; minutes: number };

const BLOCKS: Block[] = [
  { id: "settle", label: "Settle the breath", note: "In 4, out 6 — six slow breaths.", minutes: 1 },
  { id: "box", label: "Box breathing", note: "Four equal counts: in, hold, out, hold.", minutes: 2 },
  { id: "ground", label: "5-4-3-2-1 grounding", note: "Name what's around you, sense by sense.", minutes: 2 },
  { id: "scan", label: "Let go, top to bottom", note: "A short body scan for held tension.", minutes: 1 },
  { id: "dump", label: "Empty the desk", note: "Write out whatever's still loud.", minutes: 3 },
  { id: "good", label: "Three good things", note: "Name what went right today.", minutes: 2 },
  { id: "intention", label: "One intention", note: "Decide what this next stretch is for.", minutes: 1 },
  { id: "reflect", label: "Write freely", note: "No prompt, no rules.", minutes: 5 },
];

// Daytime wording — the wind-down's version of this ends "sink into the bed",
// which is wrong at 3pm.
const SCAN_PROMPTS = [
  "Let your jaw come unclenched.",
  "Drop your shoulders away from your ears.",
  "Open your hands.",
  "Soften your belly.",
  "Let your feet feel the floor.",
  "Let the chair take your weight.",
];

// Suggestions, not a fixed list — the cue has to be something already in
// *your* day, so the field stays free text.
const CUE_IDEAS = [
  "make my morning coffee",
  "close my laptop",
  "get off the bus",
  "brush my teeth",
];

const STORE_KEY = "cerebro.ritual.v1";
type Saved = { blocks: BlockId[]; cue: string };

function load(): Saved | null {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Saved;
    const known = new Set(BLOCKS.map((b) => b.id));
    return {
      blocks: (parsed.blocks ?? []).filter((id) => known.has(id)),
      cue: typeof parsed.cue === "string" ? parsed.cue : "",
    };
  } catch {
    return null;
  }
}

export default function RitualBuilder() {
  const [chosen, setChosen] = useState<BlockId[]>([]);
  const [cue, setCue] = useState("");
  const [saved, setSaved] = useState(false);
  const [phase, setPhase] = useState<"build" | "run" | "done">("build");
  const [at, setAt] = useState(0);

  // Read after mount: the server render has no localStorage, and seeding
  // state from it directly would hydrate-mismatch.
  useEffect(() => {
    const s = load();
    if (!s) return;
    setChosen(s.blocks);
    setCue(s.cue);
  }, []);

  const picked = chosen
    .map((id) => BLOCKS.find((b) => b.id === id))
    .filter(Boolean) as Block[];
  const minutes = picked.reduce((n, b) => n + b.minutes, 0);

  function toggle(id: BlockId) {
    setSaved(false);
    setChosen((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }

  function move(idx: number, by: -1 | 1) {
    const to = idx + by;
    if (to < 0 || to >= chosen.length) return;
    setSaved(false);
    setChosen((prev) => {
      const next = [...prev];
      [next[idx], next[to]] = [next[to], next[idx]];
      return next;
    });
  }

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({ blocks: chosen, cue } satisfies Saved));
      setSaved(true);
    } catch {
      /* private-mode browsers refuse writes; the ritual still runs this session */
    }
  }

  if (phase === "run" && picked.length) {
    return (
      <Runner
        blocks={picked}
        at={at}
        onAdvance={() => (at < picked.length - 1 ? setAt(at + 1) : setPhase("done"))}
        onExit={() => setPhase("build")}
      />
    );
  }

  if (phase === "done") {
    return <Finished cue={cue.trim()} onAgain={() => setPhase("build")} />;
  }

  return (
    <>
      <AppHeader eyebrow="Toolkit" title="Your ritual" />
      <div className="page-body">
        {/* The cue leads. Reordering blocks is the fun part; deciding when it
            happens is the part with the evidence. */}
        <section className="card">
          <p className="eyebrow">First, the when</p>
          <h2 className="serif-h" style={{ marginTop: 6 }}>Attach it to something you already do</h2>
          <p className="sub">
            A routine with no cue is a good intention. A routine that rides on something already
            in your day is a plan.
          </p>
          <label className="field" style={{ marginTop: 12 }}>
            <span>After I…</span>
            <input
              value={cue}
              onChange={(e) => { setCue(e.target.value); setSaved(false); }}
              placeholder="make my morning coffee"
              aria-label="After I"
            />
          </label>
          <div className="chips">
            {CUE_IDEAS.map((c) => (
              <button key={c} className="chip" onClick={() => { setCue(c); setSaved(false); }}>
                {c}
              </button>
            ))}
          </div>
          {cue.trim() && (
            <p className="serif-h" style={{ fontSize: 18, marginTop: 14 }}>
              After I {cue.trim()}, I&apos;ll run this.
            </p>
          )}
          {/* Honest about what we won't do: there is no web reminder here, and
              promising one we can't send would be a fake. It also happens to be
              the point — the cue is meant to do the reminding. */}
          <p className="footnote">
            CereBro won&apos;t nag you about this. The cue is the reminder — that&apos;s how it works.
          </p>
          <WhyThisWorks text="Deciding in advance when and where you'll do something — an 'if-then' plan tied to a moment already in your day — roughly doubles follow-through compared with intention alone, across ~94 studies (Gollwitzer & Sheeran, Advances in Experimental Social Psychology, 2006)." />
        </section>

        <div className="sec-head"><h2 className="serif-h">Then, the what</h2></div>
        <section className="card">
          <p className="sub" style={{ marginBottom: 12 }}>
            Pick a few. Short beats thorough — a five-minute routine you keep is worth more than a
            twenty-minute one you skip.
          </p>
          {BLOCKS.map((b) => {
            const idx = chosen.indexOf(b.id);
            const on = idx >= 0;
            return (
              <div
                key={b.id}
                className="row"
                style={{
                  gap: 12, padding: "10px 0",
                  borderTop: "1px solid var(--line)",
                }}
              >
                <input
                  type="checkbox"
                  checked={on}
                  onChange={() => toggle(b.id)}
                  aria-label={b.label}
                  style={{ width: 20, flex: "0 0 auto", padding: 0 }}
                />
                <div className="grow">
                  <strong style={{ fontWeight: 600 }}>
                    {on && <span style={{ color: "var(--lav)" }}>{idx + 1}. </span>}
                    {b.label}
                  </strong>
                  <div className="meta">{b.note} · {b.minutes} min</div>
                </div>
                {on && (
                  <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    <button
                      className="linklike"
                      style={{ textDecoration: "none" }}
                      onClick={() => move(idx, -1)}
                      disabled={idx === 0}
                      aria-label={`Move ${b.label} earlier`}
                    >
                      ▲
                    </button>
                    <button
                      className="linklike"
                      style={{ textDecoration: "none" }}
                      onClick={() => move(idx, 1)}
                      disabled={idx === chosen.length - 1}
                      aria-label={`Move ${b.label} later`}
                    >
                      ▼
                    </button>
                  </div>
                )}
              </div>
            );
          })}

          <p className="meta" style={{ marginTop: 14 }} role="status">
            {picked.length
              ? `${picked.length} ${picked.length === 1 ? "step" : "steps"} · about ${minutes} min`
              : "Nothing picked yet."}
          </p>

          <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
            <button
              className="btn"
              disabled={!picked.length}
              onClick={() => { setAt(0); setPhase("run"); }}
            >
              Start the ritual
            </button>
            <button className="btn ghost" disabled={!picked.length || saved} onClick={save}>
              {saved ? "Saved" : "Save it"}
            </button>
          </div>
          <p className="footnote">
            Saved in this browser only — it isn&apos;t synced to your account or sent to us.
          </p>
        </section>

        <p className="footnote">
          Every step here is a tool from the{" "}
          <Link href="/games" style={{ color: "var(--lav)", fontWeight: 700 }}>Toolkit</Link> —
          nothing new to learn.
        </p>
        <p className="footnote">
          In a heavier moment, tools aren&apos;t enough —{" "}
          <Link href="/crisis" style={{ color: "var(--lav)", fontWeight: 700 }}>urgent support</Link>{" "}
          is right here.
        </p>
      </div>
    </>
  );
}

// ── Runner ──────────────────────────────────────────────────────────────
function Runner({
  blocks, at, onAdvance, onExit,
}: {
  blocks: Block[];
  at: number;
  onAdvance: () => void;
  onExit: () => void;
}) {
  const b = blocks[at];
  const lastOne = at === blocks.length - 1;
  const nextLabel = lastOne ? "Finish" : "Next step";

  return (
    <>
      <AppHeader eyebrow="Your ritual" title={b.label} />
      <div className="page-body">
        <div className="card" style={{ marginBottom: 16 }}>
          <div style={{ display: "flex", gap: 6 }} aria-hidden="true">
            {blocks.map((x, i) => (
              <div
                key={x.id}
                style={{
                  flex: 1, height: 4, borderRadius: 999,
                  background: i <= at ? "var(--lav)" : "var(--line)",
                }}
              />
            ))}
          </div>
          <div className="row" style={{ marginTop: 8 }}>
            <p className="meta grow">Step {at + 1} of {blocks.length}</p>
            {/* Leaving is one click at every step — a routine you can't put
                down mid-way is a trap, not a ritual. */}
            <button className="linklike" onClick={onExit}>Stop here</button>
          </div>
        </div>

        <section className="card" style={b.id === "scan" ? { textAlign: "center" } : undefined}>
          {/* Keyed by block: two writing steps in a row (or two breathing
              blocks) reconcile to the same component type at the same
              position, so without this the second one inherits the first's
              text — or its finished breath count. */}
          <Step key={b.id} block={b} nextLabel={nextLabel} onNext={onAdvance} />
        </section>
      </div>
    </>
  );
}

// Deliberately quiet: no trophy, no count, no streak. F5 — celebrate notable
// moments, not every repetition. The one thing worth saying is the cue, which
// is what makes the next run happen.
function Finished({ cue, onAgain }: { cue: string; onAgain: () => void }) {
  return (
    <>
      <AppHeader eyebrow="Your ritual" title="Done" />
      <div className="page-body">
        <section className="card" style={{ textAlign: "center" }}>
          <h2 className="serif-h" style={{ fontSize: 22 }}>That&apos;s the ritual.</h2>
          <p className="sub">
            {cue ? `Next time: after you ${cue}.` : "Nothing else is needed right now."}
          </p>
          <div style={{ display: "flex", gap: 8, justifyContent: "center", marginTop: 14, flexWrap: "wrap" }}>
            <Link href="/games" className="btn ghost">Back to the Toolkit</Link>
            <button className="btn ghost" onClick={onAgain}>Change it</button>
          </div>
        </section>
      </div>
    </>
  );
}

function Step({ block, nextLabel, onNext }: { block: Block; nextLabel: string; onNext: () => void }) {
  switch (block.id) {
    case "settle":
      return (
        <>
          <PacedBreath
            phases={SLOW_EXHALE}
            cycles={6}
            idleLabel="In 4 · out 6"
            doneLabel="That's six."
            ctaLabel="I'm settled"
            doneCtaLabel={nextLabel}
            onNext={onNext}
          />
          <WhyThisWorks text="A longer exhale than inhale is the part of slow breathing with the clearest evidence — it raises vagal tone and slows heart rate. The count matters less than the ratio." />
        </>
      );
    case "box":
      return (
        <>
          <PacedBreath
            phases={BOX_BREATH}
            cycles={5}
            idleLabel="Box breathing · 4·4·4·4"
            doneLabel="That's five rounds."
            ctaLabel="That's enough"
            doneCtaLabel={nextLabel}
            onNext={onNext}
          />
          <WhyThisWorks text="Paced breathing is used in clinical distress-tolerance and relaxation protocols. Slowing the breath activates the body's calming response." />
        </>
      );
    case "ground":
      return (
        <>
          <SensorySteps onFinish={onNext} finishLabel={nextLabel} />
          <WhyThisWorks text="Sensory grounding redirects attention from spiralling thoughts to the here-and-now — a widely taught anxiety-management skill." />
        </>
      );
    case "scan":
      return (
        <>
          <p className="eyebrow">Let go, top to bottom</p>
          <PromptSequence
            prompts={SCAN_PROMPTS}
            ms={6000}
            ctaLabel="Skip ahead"
            doneCtaLabel={nextLabel}
            onNext={onNext}
          />
          <WhyThisWorks text="Progressive muscle relaxation is one of the standard relaxation components of CBT-I, the best-evidenced treatment for insomnia (Lancet Digital Health, 2025)." />
        </>
      );
    case "dump":
      return (
        <WritingStep
          eyebrow="Empty the desk"
          title="What's still loud?"
          sub="Anything unfinished, worrying, or circling. Don't organise it — getting it out is the point."
          placeholder="Whatever's there…"
          ariaLabel="Brain dump"
          journalTitle="From my ritual"
          journalTag="ritual"
          why="Writing down what's unfinished helps people let go of it — the effect is strongest for what's still open, not what's already done (Scullin et al., Journal of Experimental Psychology: General, 2018)."
          ctaLabel={nextLabel}
          onNext={onNext}
        />
      );
    case "good":
      return (
        <ThreeGoodThings
          eyebrow="Three good things"
          title="What went right?"
          sub="Small counts. The coffee, a message, getting through it."
          skipLabel="Skip this one"
          continueLabel={nextLabel}
          why="Naming three specific good things is one of the most replicated positive-psychology exercises, with measured effects on mood up to six months out (Seligman et al., American Psychologist, 2005)."
          onNext={onNext}
        />
      );
    case "intention":
      return (
        <WritingStep
          eyebrow="One intention"
          title="What is this next stretch for?"
          sub="One line. Concrete beats noble — 'reply to two emails, then stop' travels further than 'be productive'."
          placeholder="Today I'll…"
          ariaLabel="Intention"
          rows={3}
          journalTitle="An intention"
          journalTag="intention"
          journalSymbol="sparkles"
          why="Naming a specific, concrete intention — what, when, where — is what separates a plan from a wish; vague goals reliably underperform specific ones (Gollwitzer & Sheeran, 2006)."
          ctaLabel={nextLabel}
          onNext={onNext}
        />
      );
    case "reflect":
      return (
        <WritingStep
          eyebrow="Write freely"
          title="Anything at all"
          sub="No prompt, no structure, nobody reading it."
          placeholder="…"
          ariaLabel="Free writing"
          rows={9}
          journalTitle="From my ritual"
          journalTag="ritual"
          why="Writing about what's on your mind, for a few minutes at a time, has small but repeatedly measured effects on wellbeing — the writing does the work, not the reading back (Pennebaker, Psychological Science, 1997)."
          ctaLabel={nextLabel}
          onNext={onNext}
        />
      );
  }
}
