"use client";

import { useCallback, useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { api } from "@/lib/api";

// The two things on this screen are the only ones in the product the USER
// defines — everything else (plan, patterns, suggestions) is generated for them.
//
// Deliberately not gamified: habits show which of the last seven days happened,
// never a streak count. A gap is a gap. The premium/engagement surfaces here are
// gated behind an anti-dark-pattern checklist, and a broken-chain counter on
// mental-health activity is exactly what that checklist exists to stop.
type Goal = { id: string; title: string; why: string; status: string };
type Habit = {
  id: string;
  title: string;
  cue: string;
  target_per_week: number;
  recent_days: string[];
  done_today: boolean;
};

const DAY_LABEL = ["S", "M", "T", "W", "T", "F", "S"];

function lastSevenDays(): { iso: string; label: string }[] {
  const out: { iso: string; label: string }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    out.push({ iso: d.toISOString().slice(0, 10), label: DAY_LABEL[d.getDay()] });
  }
  return out;
}

export default function Goals() {
  const [goals, setGoals] = useState<Goal[] | null>(null);
  const [habits, setHabits] = useState<Habit[] | null>(null);
  const [goalDraft, setGoalDraft] = useState("");
  const [habitDraft, setHabitDraft] = useState("");
  const [cueDraft, setCueDraft] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(() => {
    api<Goal[]>("/goals").then(setGoals).catch(() => setGoals([]));
    api<Habit[]>("/habits").then(setHabits).catch(() => setHabits([]));
  }, []);

  useEffect(load, [load]);

  async function run(fn: () => Promise<unknown>, message: string) {
    try {
      await fn();
      setError("");
      load();
    } catch {
      setError(message);
    }
  }

  const week = lastSevenDays();

  return (
    <>
      <AppHeader eyebrow="Yours to define" title="Goals & habits" />
      <div className="page-body">
        <p style={{ color: "var(--muted)", maxWidth: 620 }}>
          The rest of CereBro suggests things to you. This is the part you decide.
        </p>
        {error && <p className="error">{error}</p>}

        <section className="card cz-in" style={{ marginTop: 14 }}>
          <h2>What you&apos;re working towards</h2>
          {goals === null ? (
            <p className="sub">Loading…</p>
          ) : goals.length === 0 ? (
            <p className="sub">Nothing yet. One is plenty to start with.</p>
          ) : (
            goals.map((g) => (
              <div key={g.id} style={{ borderTop: "1px solid var(--line)", padding: "12px 0" }}>
                <strong>{g.title}</strong>
                {g.why && <p className="sub" style={{ margin: "4px 0 0" }}>{g.why}</p>}
                <div style={{ display: "flex", gap: 14, marginTop: 8, flexWrap: "wrap" }}>
                  <button
                    className="btn"
                    onClick={() => run(
                      () => api(`/goals/${g.id}/decompose`, { method: "POST" }),
                      "Couldn't build a plan from that — try again.",
                    )}
                  >
                    Make this today&apos;s plan
                  </button>
                  <button
                    onClick={() => run(
                      () => api(`/goals/${g.id}`, { method: "PATCH", body: JSON.stringify({ status: "achieved" }) }),
                      "Couldn't update that.",
                    )}
                    style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "var(--ok)" }}
                  >
                    Done
                  </button>
                  {/* Letting a goal go is an outcome, not a failure. */}
                  <button
                    onClick={() => run(
                      () => api(`/goals/${g.id}`, { method: "PATCH", body: JSON.stringify({ status: "released" }) }),
                      "Couldn't update that.",
                    )}
                    style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "var(--muted)" }}
                  >
                    Let it go
                  </button>
                </div>
              </div>
            ))
          )}
          <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
            <input
              value={goalDraft}
              onChange={(e) => setGoalDraft(e.target.value)}
              placeholder="Something you're working towards…"
              aria-label="Add a goal"
              style={{ flex: "1 1 240px", minWidth: 0 }}
            />
            <button
              className="btn"
              disabled={!goalDraft.trim()}
              onClick={() => run(async () => {
                await api("/goals", { method: "POST", body: JSON.stringify({ title: goalDraft.trim() }) });
                setGoalDraft("");
              }, "Couldn't save that goal.")}
            >
              Add goal
            </button>
          </div>
        </section>

        <section className="card cz-in cz-d1" style={{ marginTop: 14 }}>
          <h2>Small things you repeat</h2>
          <p className="sub" style={{ maxWidth: 560 }}>
            The last seven days, so you can see the shape of it. No streak to break.
          </p>
          {habits === null ? (
            <p className="sub">Loading…</p>
          ) : habits.length === 0 ? (
            <p className="sub">Nothing yet.</p>
          ) : (
            habits.map((h) => (
              <div key={h.id} style={{ borderTop: "1px solid var(--line)", padding: "12px 0" }}>
                <div style={{ display: "flex", gap: 12, justifyContent: "space-between", flexWrap: "wrap" }}>
                  <div style={{ minWidth: 0 }}>
                    <strong>{h.title}</strong>
                    {h.cue && <div className="meta">{h.cue}</div>}
                    <div style={{ display: "flex", gap: 6, marginTop: 8 }} aria-hidden="true">
                      {week.map((d) => (
                        <span
                          key={d.iso}
                          title={d.iso}
                          style={{
                            width: 22, height: 22, borderRadius: 999,
                            display: "grid", placeItems: "center", fontSize: 11,
                            background: h.recent_days.includes(d.iso) ? "var(--lav)" : "var(--card-strong)",
                            color: h.recent_days.includes(d.iso) ? "var(--ink)" : "var(--muted-2)",
                          }}
                        >
                          {d.label}
                        </span>
                      ))}
                    </div>
                    <span className="meta" style={{ display: "block", marginTop: 6 }}>
                      {h.recent_days.length} of the last 7 days
                    </span>
                  </div>
                  <button
                    className="btn"
                    onClick={() => run(
                      () => api(`/habits/${h.id}/complete`, { method: h.done_today ? "DELETE" : "POST" }),
                      "Couldn't record that.",
                    )}
                    style={{ alignSelf: "flex-start" }}
                  >
                    {h.done_today ? "Done today ✓" : "Mark today"}
                  </button>
                </div>
              </div>
            ))
          )}
          <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
            <input
              value={habitDraft}
              onChange={(e) => setHabitDraft(e.target.value)}
              placeholder="A small thing to repeat…"
              aria-label="Add a habit"
              style={{ flex: "1 1 200px", minWidth: 0 }}
            />
            {/* An implementation intention — "after I brush my teeth" — is the one
                habit mechanism with decent evidence, so the cue gets its own field. */}
            <input
              value={cueDraft}
              onChange={(e) => setCueDraft(e.target.value)}
              placeholder="After I…"
              aria-label="When will you do it?"
              style={{ flex: "1 1 140px", minWidth: 0 }}
            />
            <button
              className="btn"
              disabled={!habitDraft.trim()}
              onClick={() => run(async () => {
                await api("/habits", {
                  method: "POST",
                  body: JSON.stringify({ title: habitDraft.trim(), cue: cueDraft.trim() }),
                });
                setHabitDraft("");
                setCueDraft("");
              }, "Couldn't save that habit.")}
            >
              Add habit
            </button>
          </div>
        </section>
      </div>
    </>
  );
}
