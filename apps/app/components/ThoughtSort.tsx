"use client";

// Thought Sort — a Reframe exercise for the Toolkit.
//
// Ported from the sibling build's ThoughtSortGame, which is the one game in
// that catalogue that teaches something real: spotting the named cognitive
// distortions (overgeneralisation, all-or-nothing, catastrophising,
// perfectionism) is standard cognitive-restructuring psychoeducation.
//
// Three things were deliberately left behind:
//
//  1. **The efficacy claim.** The reference scores a "Thought awareness: 87%"
//     and congratulates "Perfect cognitive awareness!". A ten-item quiz over
//     pre-written sentences measures no such faculty, and unevidenced
//     cognitive-training claims are the exact class the FTC acted on in the
//     2016 Lumosity settlement. The summary here reports what happened and
//     nothing more.
//  2. **The reward loop.** Trophies and streak-style praise conflict with F5
//     (celebrate notable moments, not every rep).
//  3. **The word "game".** These are example thoughts, not the user's own —
//     framing matters when the subject is self-criticism.
//
// The thoughts are deliberately generic examples. Nothing the user has written
// is ever categorised for them.

import { useCallback, useEffect, useRef, useState } from "react";

type Thought = { text: string; helpful: boolean; why: string };

const THOUGHTS: Thought[] = [
  { text: "I made a mistake, but I can learn from it", helpful: true, why: "Specific and forward-looking, rather than a verdict on you." },
  { text: "I always mess everything up", helpful: false, why: "Overgeneralisation — one event stretched into 'always' and 'everything'." },
  { text: "This is hard, but I can handle it", helpful: true, why: "Names the difficulty without concluding you're powerless in it." },
  { text: "I'm worthless", helpful: false, why: "Labelling — a whole-person judgement drawn from a moment." },
  { text: "I'll try my best, and that's enough", helpful: true, why: "Sets a standard you can actually meet." },
  { text: "Nobody likes me", helpful: false, why: "All-or-nothing thinking — no room for the people who do." },
  { text: "I can ask for help when I need it", helpful: true, why: "Treats support as available rather than as failure." },
  { text: "I should be good at this by now", helpful: false, why: "A 'should' statement — a rule you didn't agree to, used against yourself." },
  { text: "It's okay to feel uncomfortable sometimes", helpful: true, why: "Allows the feeling instead of adding a second problem on top." },
  { text: "If this goes wrong, everything falls apart", helpful: false, why: "Catastrophising — jumping to the worst link in a long chain." },
  { text: "I've got through hard things before", helpful: true, why: "Draws on evidence you already have." },
  { text: "Everyone else has it figured out", helpful: false, why: "Comparison against a version of others you can't actually see." },
];

const ROUND = 8;
const FEEDBACK_MS = 2600;

function shuffle<T>(arr: readonly T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

type Phase = "intro" | "sorting" | "feedback" | "done";

export function ThoughtSort() {
  const [phase, setPhase] = useState<Phase>("intro");
  const [deck, setDeck] = useState<Thought[]>([]);
  const [idx, setIdx] = useState(0);
  const [matched, setMatched] = useState(0);
  const [picked, setPicked] = useState<"helpful" | "unhelpful" | "unsure" | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>();

  const thought = deck[Math.min(idx, Math.max(0, deck.length - 1))];

  function start() {
    setDeck(shuffle(THOUGHTS).slice(0, ROUND));
    setIdx(0);
    setMatched(0);
    setPicked(null);
    setPhase("sorting");
  }

  function pick(bucket: "helpful" | "unhelpful" | "unsure") {
    if (phase !== "sorting") return;
    if (bucket !== "unsure" && (bucket === "helpful") === thought.helpful) {
      setMatched((m) => m + 1);
    }
    setPicked(bucket);
    setPhase("feedback");
  }

  const advance = useCallback(() => {
    if (idx + 1 >= deck.length) {
      setPhase("done");
    } else {
      setIdx((i) => i + 1);
      setPicked(null);
      setPhase("sorting");
    }
  }, [idx, deck.length]);

  useEffect(() => {
    if (phase !== "feedback") return;
    timer.current = setTimeout(advance, FEEDBACK_MS);
    return () => clearTimeout(timer.current);
  }, [phase, advance]);

  if (phase === "intro") {
    return (
      <div>
        <p className="sub">
          Eight example thoughts, one at a time. Decide whether each one is helping the
          person thinking it — and if you can&apos;t tell, &ldquo;Not sure&rdquo; is a real
          answer, not a cop-out.
        </p>
        {/* Not just "Start": the Toolkit page also carries the box breather's
            Start, and two identically-named buttons on one page is a real
            screen-reader ambiguity (it broke the e2e locator the same way). */}
        <button className="btn" onClick={start} style={{ marginTop: 12 }}>Start sorting</button>
      </div>
    );
  }

  if (phase === "done") {
    return (
      <div>
        {/* Reports what happened. No percentage, no faculty score, no praise
            ladder — a short quiz over sample sentences doesn't support one. */}
        <p className="serif-h" style={{ fontSize: 20, margin: "0 0 4px" }}>
          You matched {matched} of {deck.length}.
        </p>
        <p className="sub">
          The point isn&apos;t the count — it&apos;s noticing that unhelpful thoughts tend
          to share a shape: absolutes, verdicts, and leaps to the worst case.
        </p>
        <button className="btn ghost" onClick={start} style={{ marginTop: 12 }}>Again</button>
      </div>
    );
  }

  return (
    <div>
      <p className="meta">Thought {Math.min(idx + 1, deck.length)} of {deck.length}</p>
      <blockquote
        className="card"
        style={{ margin: "10px 0", background: "var(--card-strong)" }}
      >
        <p className="serif-h" style={{ fontSize: 19, margin: 0 }}>&ldquo;{thought.text}&rdquo;</p>
      </blockquote>

      {phase === "sorting" ? (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button className="btn ghost" onClick={() => pick("helpful")}>Helping</button>
          <button className="btn ghost" onClick={() => pick("unhelpful")}>Not helping</button>
          <button className="btn ghost" onClick={() => pick("unsure")}>Not sure</button>
        </div>
      ) : (
        <div aria-live="polite">
          <p style={{ margin: "0 0 4px", fontWeight: 700 }}>
            {picked === "unsure"
              ? "Fair — plenty of thoughts sit in between."
              : (picked === "helpful") === thought.helpful
                ? "Yes — that one's helping."
                : "This one's usually not helping."}
          </p>
          <p className="sub" style={{ margin: 0 }}>{thought.why}</p>
          <button className="btn ghost" onClick={advance} style={{ marginTop: 10 }}>Next</button>
        </div>
      )}
    </div>
  );
}
