"use client";

import Link from "next/link";
import { useState } from "react";
import { celebrate } from "@/lib/celebrate";

const STEPS = [
  { n: 5, prompt: "Name 5 things you can see." },
  { n: 4, prompt: "Name 4 things you can hear." },
  { n: 3, prompt: "Name 3 things you can feel — your feet, the chair, the air." },
  { n: 2, prompt: "Name 2 things you can smell." },
  { n: 1, prompt: "Name 1 thing you can taste." },
];

export default function Grounding() {
  const [i, setI] = useState(0);
  const [done, setDone] = useState(false);
  const step = STEPS[i];

  function next() { if (i < STEPS.length - 1) setI(i + 1); else { setDone(true); celebrate("Grounded"); } }
  function restart() { setI(0); setDone(false); }

  return (
    <div className="page tool-page">
      <div className="page-head">
        <div>
          <div className="eyebrow"><Link href="/tools" className="link-accent">Tools</Link> · Grounding</div>
          <h1>5-4-3-2-1</h1>
        </div>
      </div>

      {done ? (
        <div className="ground-card">
          <div className="g-big" aria-hidden="true">✓</div>
          <h2>Back in the room.</h2>
          <p className="placeholder">You just walked your senses through the present moment. Notice how you feel now.</p>
          <button className="primary" onClick={restart}>Again</button>
        </div>
      ) : (
        <div className="ground-card">
          <div className="g-big" aria-hidden="true">{step.n}</div>
          <h2 aria-live="polite">{step.prompt}</h2>
          <p className="placeholder">Take your time. There&rsquo;s no rush — just notice.</p>
          <div className="g-dots" aria-hidden="true">
            {STEPS.map((_, k) => <span key={k} className={`dot ${k <= i ? "on" : ""}`} />)}
          </div>
          <button className="primary" onClick={next}>{i < STEPS.length - 1 ? "Next" : "Finish"}</button>
        </div>
      )}
    </div>
  );
}
