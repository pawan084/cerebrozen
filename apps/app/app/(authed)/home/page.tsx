"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";

// 5-emoji check-in matching the ref; each maps into the shared mood taxonomy.
const MOODS = [
  { emoji: "😊", name: "Great", note: "Bright", symbol: "sparkles", resp: "Love that — let's protect the good energy today." },
  { emoji: "🙂", name: "Good", note: "Steady", symbol: "sun.max", resp: "Steady is a lovely place to be." },
  { emoji: "😐", name: "Okay", note: "Neutral", symbol: "minus", resp: "Okay is allowed. A small reset can lift it." },
  { emoji: "😔", name: "Low", note: "Heavy", symbol: "moon", resp: "Thanks for being honest — let's go gently." },
  { emoji: "😰", name: "Anxious", note: "Loud", symbol: "exclamationmark.triangle", resp: "Loud thoughts are real. Want a 2-minute reset?" },
];

type Streak = { current: number; best: number; week: { date: string; active: boolean }[] };
type Mood = { id: string; mood: string; created_at: string };
type Entry = { id: string; body: string; created_at: string };

const PLAN = [
  { title: "Ease into the morning", sub: "Guided breathwork · 4 min", href: "/games", color: "linear-gradient(135deg,#8a7bf0,#5b52c9)" },
  { title: "Midday reset", sub: "Box breathing · 2 min", href: "/games", color: "linear-gradient(135deg,#8fe6ee,#4fd8e0)" },
  { title: "Sleep wind-down", sub: "Story · 12 min", href: "/sleep", color: "linear-gradient(135deg,#f0a48c,#e08a9a)" },
];
const JUMP = [
  { label: "Talk now", href: "/chat", icon: Icon.talk, bg: "linear-gradient(160deg,rgba(138,123,240,0.35),rgba(255,255,255,0.02))" },
  { label: "Breathe", href: "/games", icon: Icon.spark, bg: "linear-gradient(160deg,rgba(143,230,238,0.28),rgba(255,255,255,0.02))" },
  { label: "Sleep", href: "/sleep", icon: Icon.sleep, bg: "linear-gradient(160deg,rgba(166,139,255,0.32),rgba(255,255,255,0.02))" },
  { label: "Journal", href: "/journal", icon: Icon.journal, bg: "linear-gradient(160deg,rgba(240,164,140,0.28),rgba(255,255,255,0.02))" },
];

export default function Home() {
  const [name, setName] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const [resp, setResp] = useState("");
  const [streak, setStreak] = useState<Streak | null>(null);
  const [moods, setMoods] = useState<Mood[]>([]);
  const [reflection, setReflection] = useState<string>("");

  useEffect(() => {
    api("/auth/me").then((m: any) => setName(m.name || "")).catch(() => {});
    api<Streak>("/users/me/streak").then(setStreak).catch(() => {});
    api<Mood[]>("/moods?limit=14").then(setMoods).catch(() => {});
    api<Entry[]>("/journal?limit=1").then((e) => e[0]?.body && setReflection(e[0].body)).catch(() => {});
  }, []);

  async function pick(m: (typeof MOODS)[number]) {
    setPicked(m.name);
    setResp(m.resp);
    try {
      await api("/moods", { method: "POST", body: JSON.stringify({ mood: m.name, note: m.note, symbol: m.symbol, intensity: 3 }) });
      api<Streak>("/users/me/streak").then(setStreak).catch(() => {});
    } catch {}
  }

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  const days = ["S", "M", "T", "W", "T", "F", "S"];
  // A gentle mood line for the rail chart (score by recency; fallback shape).
  const scores = moods.slice(0, 7).reverse().map((m) => ({ Great: 5, Good: 4, Okay: 3, Low: 2, Anxious: 1 } as any)[m.mood] ?? 3);
  const pts = (scores.length >= 2 ? scores : [3, 4, 3, 4, 3, 4, 4]);

  return (
    <>
      <AppHeader eyebrow="Welcome back" title={`${greeting}${name ? `, ${name}` : ""}`} />
      <div className="page-body">
        <div className="dash-grid">
          <div>
            {/* Check-in hero */}
            <section className="checkin-hero" aria-label="Daily check-in">
              <div className="checkin-orb" aria-hidden="true" />
              <p className="eyebrow">Daily check-in</p>
              <h2>How are you arriving today?</h2>
              <div className="emoji-row">
                {MOODS.map((m) => (
                  <button key={m.name} className={picked === m.name ? "emoji-btn sel" : "emoji-btn"}
                    aria-label={m.name} aria-pressed={picked === m.name} onClick={() => pick(m)}>
                    {m.emoji}
                  </button>
                ))}
              </div>
              <p className="checkin-note">{resp || "Tap how you're feeling — there's no wrong answer."}</p>
            </section>

            {/* Today's plan */}
            <div className="sec-head">
              <h2 className="serif-h">Today's plan</h2>
              <span className="sec-date">{new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}</span>
            </div>
            <div className="plan-list">
              {PLAN.map((s) => (
                <Link key={s.title} href={s.href} className="plan-row">
                  <span className="plan-play" style={{ background: s.color }}><Icon.play size={16} /></span>
                  <span className="plan-body"><strong>{s.title}</strong><small>{s.sub}</small></span>
                  <span className="plan-start">START</span>
                </Link>
              ))}
            </div>

            {/* Jump back in */}
            <div className="sec-head"><h2 className="serif-h">Jump back in</h2></div>
            <div className="jump-grid">
              {JUMP.map((j) => (
                <Link key={j.label} href={j.href} className="jump-card" style={{ background: j.bg }}>
                  <j.icon size={22} />
                  <span>{j.label}</span>
                </Link>
              ))}
            </div>
          </div>

          {/* Right rail */}
          <div className="rail">
            <div className="rail-card">
              <span className="kicker">Day rhythm</span>
              <div className="rail-big"><b>{streak?.best ?? streak?.current ?? 0}</b><span>day rhythm</span></div>
              <p className="sub">Gentle and consistent — no streaks to break.</p>
              <div className="rhythm-bars">
                {(streak?.week ?? Array.from({ length: 7 }, (_, i) => ({ date: `${i}`, active: false }))).map((d, i) => (
                  <div key={d.date} className="b">
                    <span className={d.active ? "fill" : "fill off"} />
                    <em>{days[new Date(d.date).getDay()] ?? days[i]}</em>
                  </div>
                ))}
              </div>
            </div>

            <div className="rail-card">
              <div className="sec-head" style={{ margin: 0 }}>
                <span className="serif-h" style={{ fontSize: 18 }}>Mood this week</span>
                <Link href="/insights" className="link">Details</Link>
              </div>
              <svg viewBox="0 0 300 90" style={{ width: "100%", height: 80, marginTop: 12 }} aria-hidden="true">
                <polyline
                  fill="none" stroke="url(#mg)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"
                  points={pts.map((s, i) => `${(i / (pts.length - 1)) * 290 + 5},${80 - ((s - 1) / 4) * 66}`).join(" ")}
                />
                <defs><linearGradient id="mg" x1="0" x2="1"><stop offset="0" stopColor="#8fe6ee" /><stop offset="1" stopColor="#8a7bf0" /></linearGradient></defs>
              </svg>
            </div>

            <div className="rail-card reflection">
              <span className="kicker">Last reflection</span>
              <q style={{ marginTop: 10 }}>{reflection || "Your reflections will appear here — write your first journal entry to begin."}</q>
              <Link href="/journal" className="link">Open journal →</Link>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
