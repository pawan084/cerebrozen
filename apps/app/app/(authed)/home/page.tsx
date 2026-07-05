"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { PageHeader, Panel, Row, SectionTitle, WeekDots } from "@/components/ui";
import { Icon } from "@/components/icons";

// Mirrors the iOS one-tap moods (Dummy.moods) so check-ins read the same across
// clients; `symbol` carries the SF Symbol name the iOS app renders.
const MOODS = [
  { name: "Good", note: "Clear", symbol: "sparkles", emoji: "😊" },
  { name: "Anxious", note: "Loud thoughts", symbol: "exclamationmark.triangle", emoji: "😰" },
  { name: "Low", note: "Heavy", symbol: "moon", emoji: "😔" },
  { name: "Tired", note: "Need rest", symbol: "drop", emoji: "😴" },
];

type Mood = { id: string; mood: string; note: string; created_at: string };
type Streak = { current: number; best: number; week: { date: string; active: boolean }[] };

export default function Home() {
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const [moods, setMoods] = useState<Mood[]>([]);
  const [streak, setStreak] = useState<Streak | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api("/auth/me").then((me) => setName(me.name)).catch(() => {});
    void reload();
  }, []);

  async function reload() {
    try {
      setMoods(await api<Mood[]>("/moods?limit=5"));
      setStreak(await api<Streak>("/users/me/streak"));
    } catch {}
  }

  async function checkIn() {
    const mood = MOODS.find((m) => m.name === selected);
    if (!mood || busy) return;
    setBusy(true);
    try {
      await api("/moods", {
        method: "POST",
        body: JSON.stringify({ mood: mood.name, note: mood.note, symbol: mood.symbol, intensity: 3 }),
      });
      setSaved(true);
      setSelected(null);
      await reload();
    } finally {
      setBusy(false);
    }
  }

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  return (
    <>
      <PageHeader eyebrow="Today" title={`${greeting}${name ? `, ${name}` : ""}`} />

      {/* Hero mood check-in */}
      <section className="hero-card" aria-label="Mood check-in">
        <span className="hero-tag">Daily check-in</span>
        <h2 className="hero-title">How are you arriving?</h2>
        <p className="hero-sub">A 20-second check-in shapes your next best step.</p>
        <div className="mood-row">
          {MOODS.map((m) => (
            <button
              key={m.name}
              className={`mood-btn${selected === m.name ? " selected" : ""}`}
              aria-pressed={selected === m.name}
              aria-label={`${m.name} — ${m.note}`}
              onClick={() => setSelected(m.name)}
            >
              <span className="mood-emoji">{m.emoji}</span>
              <span className="mood-name">{m.name}</span>
            </button>
          ))}
        </div>
        {saved && <p className="success" role="status">Checked in — noted gently.</p>}
        <button className="hero-cta" onClick={checkIn} disabled={!selected || busy}>
          {busy ? "Saving…" : "Check in"}
        </button>
      </section>

      {streak && (
        <Panel aria-label="Streak">
          <div className="row">
            <div className="grow">
              <h2>{streak.current === 0 ? "Begin your streak" : `${streak.current}-day streak`}</h2>
              <p className="sub">
                {streak.current === 0
                  ? "Show up once today to start — gentle, no pressure."
                  : `Best ${streak.best} days · one missed day is always forgiven.`}
              </p>
            </div>
            <WeekDots week={streak.week} />
          </div>
        </Panel>
      )}

      <SectionTitle title="Keep going" />
      <Row
        icon={<Icon.journal size={18} />}
        title="Journal"
        subtitle="Get it out of your head — private by default."
        href="/journal"
      />
      <Row
        icon={<Icon.sleep size={18} />}
        title="Sleep diary"
        subtitle="Log last night in 20 seconds; watch your week take shape."
        href="/sleep"
      />
      <Row
        icon={<Icon.talk size={18} />}
        title="Talk it through"
        subtitle="Reflect with your AI companion, any hour."
        href="/chat"
      />

      {moods.length > 0 && (
        <>
          <SectionTitle title="Recent check-ins" />
          <Panel aria-label="Recent check-ins">
            {moods.map((m) => (
              <div className="entry" key={m.id}>
                <strong>{m.mood}</strong> <span className="meta">· {m.note}</span>
                <div className="meta">{new Date(m.created_at).toLocaleString()}</div>
              </div>
            ))}
          </Panel>
        </>
      )}

      <p className="footnote">
        CereBro is not a therapist or crisis service. The full experience — voice companion,
        soundscapes, daily plans — lives in the iOS app.
      </p>
    </>
  );
}
