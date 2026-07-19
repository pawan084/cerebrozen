"use client";

import { useEffect, useRef, useState } from "react";
import { Soundscape, type LayerName } from "@/lib/soundscape";

const LAYERS: { key: LayerName; emoji: string; label: string }[] = [
  { key: "rain", emoji: "🌧", label: "Rain" },
  { key: "ocean", emoji: "🌊", label: "Ocean" },
  { key: "wind", emoji: "🍃", label: "Wind" },
  { key: "drone", emoji: "🎛", label: "Warm drone" },
];
const TIMERS = [0, 15, 30, 60];

function fmt(sec: number) {
  const m = Math.floor(sec / 60), s = sec % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export default function Sounds() {
  const scRef = useRef<Soundscape | null>(null);
  const [active, setActive] = useState<LayerName[]>([]);
  const [vols, setVols] = useState<Record<LayerName, number>>({ rain: 0.5, ocean: 0.5, wind: 0.5, drone: 0.5 });
  const [master, setMaster] = useState(0.8);
  const [timer, setTimer] = useState(0);
  const [remain, setRemain] = useState(0);
  const [supported, setSupported] = useState(true);

  useEffect(() => {
    const sc = new Soundscape();
    scRef.current = sc;
    setSupported(sc.supported());
    return () => sc.stopAll();
  }, []);

  useEffect(() => {
    if (!timer) { setRemain(0); return; }
    setRemain(timer * 60);
    const iv = setInterval(() => setRemain((r) => {
      if (r <= 1) { scRef.current?.stopAll(); setActive([]); setTimer(0); return 0; }
      return r - 1;
    }), 1000);
    return () => clearInterval(iv);
  }, [timer]);

  function toggle(name: LayerName) {
    scRef.current?.toggle(name, vols[name]);
    setActive(scRef.current?.active() ?? []);
  }
  function setVol(name: LayerName, v: number) { setVols((p) => ({ ...p, [name]: v })); scRef.current?.setVolume(name, v); }
  function setMasterVol(v: number) { setMaster(v); scRef.current?.setMaster(v); }

  return (
    <div className="page tool-page">
      <div className="page-head"><div><div className="eyebrow">Sounds</div><h1>Soundscape</h1></div></div>
      <p className="placeholder" style={{ maxWidth: 520, marginBottom: 20 }}>
        Blend a calming background — synthesized on your device, so it works offline. Mix a few, set a sleep timer, and let it run.
      </p>

      {!supported ? (
        <p className="placeholder">Your browser doesn&rsquo;t support the Web Audio API.</p>
      ) : (
        <>
          <div className="mixer">
            {LAYERS.map((l) => {
              const on = active.includes(l.key);
              return (
                <div key={l.key} className={`mix-layer ${on ? "on" : ""}`}>
                  <button className="mix-toggle" aria-pressed={on} onClick={() => toggle(l.key)}>
                    <span className="mix-emoji" aria-hidden="true">{l.emoji}</span>
                    <span className="mix-label">{l.label}</span>
                    <span className="mix-state">{on ? "On" : "Off"}</span>
                  </button>
                  {on && (
                    <input type="range" min={0} max={1} step={0.02} value={vols[l.key]}
                      aria-label={`${l.label} volume`} onChange={(e) => setVol(l.key, Number(e.target.value))} />
                  )}
                </div>
              );
            })}
          </div>

          <div className="card" style={{ marginTop: 18, maxWidth: 520 }}>
            <label className="mix-master">
              <span>Master volume</span>
              <input type="range" min={0} max={1} step={0.02} value={master} aria-label="Master volume"
                onChange={(e) => setMasterVol(Number(e.target.value))} />
            </label>
            <div className="mix-timer">
              <span>Sleep timer</span>
              <div className="seg" role="radiogroup" aria-label="Sleep timer">
                {TIMERS.map((t) => (
                  <button key={t} role="radio" aria-checked={timer === t} className={`seg-btn ${timer === t ? "on" : ""}`}
                    onClick={() => setTimer(t)}>{t === 0 ? "Off" : `${t}m`}</button>
                ))}
              </div>
            </div>
            {timer > 0 && remain > 0 && <p className="placeholder" style={{ marginTop: 8 }}>Fades out in {fmt(remain)}.</p>}
          </div>
        </>
      )}
    </div>
  );
}
