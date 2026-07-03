"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Step = { id: string; title: string; detail: string; symbol: string; order: number; done: boolean };
type Plan = { id: string; title: string; focus: string; rationale: string; source: string; steps: Step[] };

export default function PlanPage() {
  const [plan, setPlan] = useState<Plan | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Plan>("/plans/active").then(setPlan).catch(() => setError("Couldn't load your plan."));
  }, []);

  function flip(id: string, done: boolean) {
    setPlan((p) => p && { ...p, steps: p.steps.map((s) => (s.id === id ? { ...s, done } : s)) });
  }

  async function toggle(step: Step) {
    flip(step.id, !step.done); // optimistic; the server response reconciles
    try {
      setPlan(await api<Plan>(`/plans/steps/${step.id}`, {
        method: "PATCH",
        body: JSON.stringify({ done: !step.done }),
      }));
    } catch {
      flip(step.id, step.done); // revert on failure
    }
  }

  async function regenerate() {
    if (busy) return;
    setBusy(true);
    try {
      setPlan(await api<Plan>("/plans/generate", { method: "POST" }));
    } finally {
      setBusy(false);
    }
  }

  const doneCount = plan?.steps.filter((s) => s.done).length ?? 0;

  return (
    <>
      <p className="eyebrow">Agentic plan{plan ? ` · ${plan.source === "ai" ? "personalized by AI" : "curated"}` : ""}</p>
      <h1>{plan?.title ?? "Daily plan"}</h1>
      {error && <p className="error">{error}</p>}

      {plan && (
        <>
          <section className="card">
            <h2>Why this plan</h2>
            <p className="sub">{plan.rationale || `Built around ${plan.focus || "your goals"}.`}</p>
            <p className="footnote">
              {doneCount} of {plan.steps.length} steps complete · updates from your check-ins and sleep diary
            </p>
          </section>

          <section className="card" aria-label="Steps">
            {plan.steps
              .slice()
              .sort((a, b) => a.order - b.order)
              .map((s) => (
                <div className="entry row" key={s.id}>
                  <input
                    type="checkbox"
                    checked={s.done}
                    onChange={() => toggle(s)}
                    aria-label={`Mark ${s.title} ${s.done ? "not done" : "done"}`}
                    style={{ width: 20, height: 20 }}
                  />
                  <div className="grow">
                    <strong style={{ textDecoration: s.done ? "line-through" : "none" }}>{s.title}</strong>
                    <div className="meta">{s.detail}</div>
                  </div>
                </div>
              ))}
          </section>

          <button className="btn" onClick={regenerate} disabled={busy}>
            {busy ? "Updating…" : "Update plan from my latest check-ins"}
          </button>
        </>
      )}
    </>
  );
}
