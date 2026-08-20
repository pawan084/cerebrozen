"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { API_URL } from "@/lib/api";
import { LAYERS, Mixer, PRESETS, dominantLayer, matchingPreset, type LayerId } from "@/lib/mixer";

// The soundscape mixer — four blendable layers, the same four Android ships,
// with the same four presets.
//
// The honest part: every `ambience.*` row in the media catalogue currently has
// an empty `url`, and the browser has no bundled loops to fall back on. So the
// layers are synthesised in the browser (see lib/mixer.ts) and an uploaded
// asset takes over per layer the moment one exists. Which one you are hearing
// is stated on the page rather than left to be guessed at.

type Asset = { key: string; url: string };

const MIX_KEY = "cerebro_app_mix";

export default function MixerPage() {
  const mixer = useRef<Mixer>();
  const [playing, setPlaying] = useState(false);
  const [volumes, setVolumes] = useState<number[]>(PRESETS[0].volumes);
  const [master, setMaster] = useState(0.7); // matches Android's default
  const [served, setServed] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  // One <audio> per layer an admin HAS uploaded. Without these the mixer would
  // build no synth voice for that layer (an asset exists, so the synth stands
  // down) and play nothing at all — a slider that moves and makes no sound.
  const els = useRef<Partial<Record<LayerId, HTMLAudioElement | null>>>({});

  if (!mixer.current && typeof window !== "undefined") mixer.current = new Mixer();

  useEffect(() => {
    const m = mixer.current!;
    try {
      const raw = window.localStorage.getItem(MIX_KEY);
      if (raw) {
        const saved = JSON.parse(raw) as { volumes: number[]; master: number };
        if (Array.isArray(saved.volumes) && saved.volumes.length === LAYERS.length) {
          setVolumes(saved.volumes);
          m.setAll(saved.volumes);
        }
        if (typeof saved.master === "number") {
          setMaster(saved.master);
          m.setMaster(saved.master);
        }
      }
    } catch {
      // A blocked store just means starting from the factory blend.
    }
    // Public route — the catalogue is decorative ambience, not user data.
    fetch(`${API_URL}/media/catalog?kind=ambience`)
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((rows: Asset[]) => {
        const byKey: Record<string, string> = {};
        for (const a of rows) if (a.url) byKey[a.key] = a.url;
        setServed(byKey);
        m.setAssets(byKey);
      })
      // Not fatal: with no catalogue every layer is synthesised, which is what
      // happens today anyway. Said quietly rather than as an error banner.
      .catch(() => setError("The sound catalogue didn't answer — playing the built-in layers."));
    return () => m.dispose();
  }, []);

  // Runs after `served` has painted its <audio> tags, which is the only point
  // at which there is an element to hand over.
  useEffect(() => {
    const m = mixer.current;
    if (!m) return;
    for (const l of LAYERS) {
      const el = els.current[l.id];
      if (served[l.key] && el) m.attachElement(l.id, el);
    }
  }, [served]);

  function persist(next: number[], nextMaster: number) {
    try {
      window.localStorage.setItem(MIX_KEY, JSON.stringify({ volumes: next, master: nextMaster }));
    } catch {
      // The mix still works for this visit.
    }
  }

  async function toggle() {
    const m = mixer.current!;
    if (playing) {
      await m.stop();
      setPlaying(false);
    } else {
      // Started from the click, never from an effect — a context built outside
      // a gesture starts suspended and the page would look broken.
      await m.start();
      setPlaying(true);
    }
  }

  function setLayer(i: number, v: number) {
    const next = volumes.map((old, j) => (i === j ? v : old));
    setVolumes(next);
    mixer.current!.setLayer(i, v);
    persist(next, master);
  }

  function applyPreset(volumesIn: number[]) {
    setVolumes(volumesIn);
    mixer.current!.setAll(volumesIn);
    persist(volumesIn, master);
  }

  const preset = matchingPreset(volumes);
  const dominant = dominantLayer(volumes);
  const blendName = preset
    ? PRESETS.find((p) => p.id === preset)!.label
    : dominant
      ? `Mostly ${dominant.toLowerCase()}`
      : "Silent";

  return (
    <>
      <AppHeader eyebrow="Layer your own" title="Soundscape mixer" />
      <div className="today-wrap">
        <p className="sub today-lede">
          Four layers you blend yourself, and nothing about the mix leaves this device.{" "}
          {/* The claim narrows the moment a recorded layer exists — "generated
              here" over a downloaded file would be false, and the per-slider
              labels below already say which is which. */}
          {Object.keys(served).length === 0
            ? "Nothing is downloaded either: the sound is generated here, in this tab."
            : "Some layers are recordings served by CereBro; the rest are generated here, in this tab."}
        </p>

        <section className="ds-card">
          <div className="ds-head">
            <h2 className="serif-h">{blendName}</h2>
            <button type="button" className="ds-cta" onClick={toggle} aria-pressed={playing}>
              {playing ? "Stop" : "Play"}
            </button>
          </div>

          <div className="row" style={{ gap: 7, flexWrap: "wrap", marginBottom: 8 }}>
            {PRESETS.map((p) => (
              <button
                key={p.id}
                type="button"
                className="chip"
                aria-pressed={preset === p.id}
                onClick={() => applyPreset(p.volumes)}
              >
                {p.label}
              </button>
            ))}
          </div>

          {LAYERS.map((l, i) => (
            <div key={l.id} className="mixer-row">
              <label htmlFor={`layer-${l.id}`}>
                {l.label}
                <small>
                  {served[l.key] ? "recorded" : "generated here"} · {Math.round(volumes[i] * 100)}%
                </small>
              </label>
              <input
                id={`layer-${l.id}`}
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={volumes[i]}
                onChange={(e) => setLayer(i, Number(e.target.value))}
              />
            </div>
          ))}

          <div className="mixer-row">
            <label htmlFor="layer-master">
              Overall volume
              <small>{Math.round(master * 100)}%</small>
            </label>
            <input
              id="layer-master"
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={master}
              onChange={(e) => {
                const v = Number(e.target.value);
                setMaster(v);
                mixer.current!.setMaster(v);
                persist(volumes, v);
              }}
            />
          </div>

          {error && <p className="tiny">{error}</p>}

          {LAYERS.filter((l) => served[l.key]).map((l) => (
            <audio
              key={l.key}
              ref={(el) => {
                els.current[l.id] = el;
              }}
              src={served[l.key].startsWith("/") ? `${API_URL}${served[l.key]}` : served[l.key]}
              loop
              preload="auto"
              aria-hidden="true"
            />
          ))}
        </section>

        <section className="ds-card">
          <p className="sub">
            The mix is kept on this device, not on your account. Sleeping soon?{" "}
            <Link href="/sleep" className="link">
              Log tonight
            </Link>{" "}
            when you wake — the mixer does not watch you sleep and records nothing about the
            night.
          </p>
        </section>
      </div>
    </>
  );
}
