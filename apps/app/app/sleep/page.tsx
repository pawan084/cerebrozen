"use client";

import { useEffect, useMemo, useState } from "react";
import { addSleep, listSleep, Unavailable, type SleepEntry } from "@/lib/wellness";
import { celebrate } from "@/lib/celebrate";

const QUALITY = ["😩", "😔", "😐", "🙂", "😴"];
const QLABEL = ["rough", "restless", "okay", "good", "deep"];

function hoursLabel(min?: number): string {
  if (!min || min <= 0) return "—";
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m ? `${h}h ${m}m` : `${h}h`;
}

export default function SleepPage() {
  const [nights, setNights] = useState<SleepEntry[] | null>(null);
  const [loadErr, setLoadErr] = useState(false);
  const [quality, setQuality] = useState<number | null>(null);
  const [bed, setBed] = useState("23:00");
  const [wake, setWake] = useState("07:00");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    listSleep()
      .then((s) => { setNights(s ?? []); setLoadErr(false); })
      .catch((e) => { setNights([]); setLoadErr(!(e instanceof Unavailable)); });
  }, []);

  async function log() {
    if (quality === null || saving) return;
    setSaving(true); setNote("");
    try {
      const saved = await addSleep({ bedtime: bed, wake_time: wake, quality: quality + 1 });
      setNights((prev) => [saved ?? { bedtime: bed, wake_time: wake, quality: quality + 1 }, ...(prev ?? [])]);
      setNote("Logged — rest well.");
      celebrate("Logged");
      setQuality(null);
    } catch (e) {
      setNote(
        e instanceof Unavailable
          ? e.reason === "consent"
            ? "Turn on Sleep history in Settings to keep a log."
            : e.reason === "disabled"
              ? "Sleep logging isn't enabled for your workspace."
              : "Couldn't save that — check your connection and try again."
          : "Couldn't save that — try again in a moment."
      );
    } finally { setSaving(false); }
  }

  const recent = useMemo(() => (nights ?? []).slice(0, 7).reverse(), [nights]);
  const avg = useMemo(() => {
    const ds = (nights ?? []).map((n) => n.duration_min ?? 0).filter((d) => d > 0);
    return ds.length ? Math.round(ds.reduce((a, b) => a + b, 0) / ds.length) : 0;
  }, [nights]);
  const maxMin = Math.max(600, ...recent.map((n) => n.duration_min ?? 0)); // scale to ≥10h

  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Rest</div><h1>Sleep</h1></div></div>

      <div className="dash">
        <div className="col">
          <section className="checkin sleep-checkin">
            <div className="eyebrow">Morning check-in</div>
            <h2>How did last night go?</h2>
            <div className="moods" role="radiogroup" aria-label="Sleep quality">
              {QUALITY.map((q, i) => (
                <button key={i} type="button" role="radio" aria-checked={quality === i}
                  className={`mood ${quality === i ? "sel" : ""}`} onClick={() => setQuality(i)}
                  aria-label={`Slept ${QLABEL[i]}`}><span aria-hidden="true">{q}</span></button>
              ))}
            </div>
            <div className="sleep-times">
              <label className="st">Bed<input type="time" value={bed} onChange={(e) => setBed(e.target.value)} /></label>
              <label className="st">Wake<input type="time" value={wake} onChange={(e) => setWake(e.target.value)} /></label>
              <button className="primary" disabled={quality === null || saving} onClick={log}>
                {saving ? "Saving…" : "Log last night"}
              </button>
            </div>
            {note && <p className="hint" aria-live="polite" style={{ position: "relative", zIndex: 1 }}>{note}</p>}
          </section>

          <div className="card" style={{ marginTop: 16 }}>
            <div className="sec-title" style={{ margin: "0 0 12px" }}><h3>Your nights</h3></div>
            {nights === null ? (
              <p className="placeholder">Loading…</p>
            ) : loadErr ? (
              <p className="placeholder">Couldn&rsquo;t load your sleep log just now. It&rsquo;s not lost — try again shortly.</p>
            ) : recent.length === 0 ? (
              <p className="placeholder">Log a few mornings and your nights appear here.</p>
            ) : (
              <>
                <div className="sleep-bars" role="img" aria-label={`Last ${recent.length} nights; average ${hoursLabel(avg)}`}>
                  {recent.map((n, i) => (
                    <div key={i} className="sb-col">
                      <div className="sb-track">
                        <div className="sb-fill" style={{ height: `${Math.min(100, ((n.duration_min ?? 0) / maxMin) * 100)}%` }} />
                      </div>
                      <span className="sb-lbl">{hoursLabel(n.duration_min).replace("h", "")}</span>
                    </div>
                  ))}
                </div>
                <p className="placeholder" style={{ marginTop: 12 }}>Average {hoursLabel(avg)} · consistency matters more than any single night.</p>
              </>
            )}
          </div>
        </div>

        <div className="col">
          <div className="card">
            <h3>Why consistency</h3>
            <p className="placeholder">
              Going to bed and waking at similar times — even on weekends — steadies your body clock more
              than chasing a perfect eight hours. A rough night isn&rsquo;t a setback; the rhythm is what counts.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
