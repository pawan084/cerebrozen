"use client";

import { useCallback, useEffect, useState } from "react";
import {
  activeProgram, enrollProgram, leaveProgram, listPrograms,
  type ActiveProgram, type CatalogProgram,
} from "@/lib/programs";
import { celebrate } from "@/lib/celebrate";

export default function ProgramsPage() {
  const [active, setActive] = useState<ActiveProgram | null | undefined>(undefined); // undefined = loading
  const [catalog, setCatalog] = useState<CatalogProgram[] | null>(null);
  const [busy, setBusy] = useState("");
  const [err, setErr] = useState(false);

  const load = useCallback(() => {
    setErr(false);
    activeProgram()
      .then((r) => setActive(r?.program ?? null))
      .catch(() => { setActive(null); setErr(true); });
    listPrograms().then((c) => setCatalog(c ?? [])).catch(() => setCatalog([]));
  }, []);
  useEffect(() => { load(); }, [load]);

  async function enroll(id: string) {
    if (busy) return;
    setBusy(id);
    try { await enrollProgram(id); celebrate("Let's go"); load(); }
    catch { setErr(true); }
    finally { setBusy(""); }
  }
  async function leave() {
    if (busy || !window.confirm("Leave this program? Your progress resets, but your journal and logs stay.")) return;
    setBusy("leave");
    try { await leaveProgram(); setActive(null); load(); }
    catch { setErr(true); }
    finally { setBusy(""); }
  }

  return (
    <div className="page tool-page">
      <div className="page-head"><div><div className="eyebrow">Journeys</div><h1>Programs</h1></div></div>

      {active === undefined ? (
        <p className="placeholder">Loading…</p>
      ) : active ? (
        <div className="prog-hero">
          <div className="ph-top">
            <div>
              <div className="ph-eyebrow">Day {active.day} of {active.days}{active.completed ? " · complete 🎉" : ""}</div>
              <h2>{active.title}</h2>
              {active.subtitle && <p className="placeholder">{active.subtitle}</p>}
            </div>
            <button className="ghost-btn" onClick={leave} disabled={!!busy}>{busy === "leave" ? "…" : "Leave"}</button>
          </div>
          <div className="ph-track"><div className="ph-fill" style={{ width: `${Math.round((active.day / Math.max(1, active.days)) * 100)}%` }} /></div>
          {active.today_guide && (
            <div className="ph-guide">
              <div className="ph-guide-t">Today · {active.today_guide.title}</div>
              <p>{active.today_guide.body}</p>
            </div>
          )}
        </div>
      ) : (
        <>
          <p className="placeholder" style={{ maxWidth: 560, marginBottom: 20 }}>
            A short guided journey — one small change a day. Pick one to begin; you can only run one at a time.
          </p>
          {catalog === null ? <p className="placeholder">Loading…</p>
            : catalog.length === 0 ? <p className="placeholder">No programs available right now.</p>
              : (
                <div className="tool-grid">
                  {catalog.map((p) => (
                    <div key={p.id} className="tool-card" style={{ cursor: "default" }}>
                      <span className="tc-emoji" aria-hidden="true">🌱</span>
                      <span className="tc-t">{p.title}</span>
                      {p.subtitle && <span className="tc-s">{p.subtitle}</span>}
                      <button className="primary" style={{ marginTop: 10, alignSelf: "flex-start" }}
                        disabled={!!busy} onClick={() => enroll(p.id)}>
                        {busy === p.id ? "Starting…" : "Start"}
                      </button>
                    </div>
                  ))}
                </div>
              )}
        </>
      )}
      {err && <p className="error" style={{ marginTop: 14 }}>Something went wrong reaching your programs — try again shortly.</p>}
    </div>
  );
}
