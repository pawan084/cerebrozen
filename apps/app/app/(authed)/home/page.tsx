"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";
import { GuidedTour } from "@/components/GuidedTour";
import { InterventionCard } from "@/components/InterventionCard";
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
type Step = { id: string; title: string; detail: string; symbol: string; order: number; done: boolean };
type Plan = { id: string; title: string; steps: Step[] };
// today_guide is additive/optional (W15) — omit-tolerant like Android.
type Program = {
  content_id: string; title: string; day: number; days: number; completed: boolean;
  today_guide?: { title?: string; body?: string } | null;
};

// Step wells cycle these gradients; the step's SF-symbol name picks the web
// surface that actually runs it (breathing → Games, wind-down → Sleep, …).
const STEP_COLORS = [
  "linear-gradient(135deg,#8a7bf0,#5b52c9)",
  "linear-gradient(135deg,#8fe6ee,#4fd8e0)",
  "linear-gradient(135deg,#f0a48c,#e08a9a)",
];
function stepHref(symbol: string) {
  if (symbol.startsWith("wind")) return "/games";
  if (symbol.startsWith("moon") || symbol === "bell") return "/sleep";
  if (symbol === "book" || symbol === "brain") return "/journal";
  if (symbol === "mic" || symbol.startsWith("person") || symbol === "heart") return "/chat";
  return "/plan";
}
// Constant-dark tiles (white labels) — each tint sits on a literal night base
// so the cards read identically in Night and Dawn.
const JUMP = [
  { label: "Talk now", href: "/chat", icon: Icon.talk, bg: "linear-gradient(160deg,rgba(138,123,240,0.35),rgba(255,255,255,0.02)), #14102c" },
  { label: "Breathe", href: "/games", icon: Icon.spark, bg: "linear-gradient(160deg,rgba(143,230,238,0.28),rgba(255,255,255,0.02)), #14102c" },
  { label: "Sleep", href: "/sleep", icon: Icon.sleep, bg: "linear-gradient(160deg,rgba(166,139,255,0.32),rgba(255,255,255,0.02)), #14102c" },
  { label: "Journal", href: "/journal", icon: Icon.journal, bg: "linear-gradient(160deg,rgba(240,164,140,0.28),rgba(255,255,255,0.02)), #14102c" },
];
// (The MILESTONES list that lived here rang a celebration ring around the BEST
// streak. Wave A moved this rail to presence framing — days present, no streak
// to break — so the ring had nothing left to fire on. iOS keeps its own
// AppState.milestones for the streak card it still shows.)
// Quick links under the greeting (ref mock 4-tile grid).
const QUICK = [
  { label: "Games", href: "/games", icon: Icon.games, bg: "linear-gradient(160deg,rgba(143,230,238,0.22),rgba(255,255,255,0.03))" },
  { label: "Insights", href: "/insights", icon: Icon.insights, bg: "linear-gradient(160deg,rgba(138,123,240,0.26),rgba(255,255,255,0.03))" },
  { label: "Programs", href: "/programs", icon: Icon.spark, bg: "linear-gradient(160deg,rgba(166,139,255,0.24),rgba(255,255,255,0.03))" },
  { label: "Sounds", href: "/library?kind=sounds", icon: Icon.library, bg: "linear-gradient(160deg,rgba(240,164,140,0.22),rgba(255,255,255,0.03))" },
];

export default function Home() {
  const [name, setName] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const [resp, setResp] = useState("");
  const [streak, setStreak] = useState<Streak | null>(null);
  const [moods, setMoods] = useState<Mood[]>([]);
  const [reflection, setReflection] = useState<string>("");
  const [plan, setPlan] = useState<Plan | null>(null);
  const [program, setProgram] = useState<Program | null>(null);
  const [planFailed, setPlanFailed] = useState(false);

  useEffect(() => {
    api("/auth/me").then((m: any) => setName(m.name || "")).catch(() => {});
    api<Streak>("/users/me/streak").then(setStreak).catch(() => {});
    // 60 keeps the week counter honest for multiple check-ins a day; the rail
    // chart below still only reads the newest 7.
    api<Mood[]>("/moods?limit=60").then(setMoods).catch(() => {});
    api<Entry[]>("/journal?limit=1").then((e) => e[0]?.body && setReflection(e[0].body)).catch(() => {});
    api<Plan>("/plans/active").then(setPlan).catch(() => setPlanFailed(true));
    api<{ program: Program | null }>("/programs/active").then((r) => setProgram(r.program)).catch(() => {});
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
  // A gentle mood line for the rail chart — only ever the user's real days.
  // (Merge note: main's `[3,4,3,4,3,4,4]` fallback shape was an invented mood
  // line, deleted in WEB_PARITY Wave A. The empty state is honest instead.)
  const pts = moods.slice(0, 7).reverse().map((m) => ({ Great: 5, Good: 4, Okay: 3, Low: 2, Anxious: 1 } as any)[m.mood] ?? 3);
  const presentDays = streak?.week?.filter((d) => d.active).length ?? 0;
  // One honest line for the weekly-insights teaser, from the same mood fetch.
  const weekCheckins = moods.filter((m) => Date.now() - new Date(m.created_at).getTime() < 7 * 86400e3).length;

  return (
    <>
      <AppHeader eyebrow="Welcome back" title={`${greeting}${name ? `, ${name}` : ""}`} />
      <GuidedTour />
      <div className="page-body">
        {/* Quick links (ref mock 4-tile grid) */}
        <nav className="quick-grid cz-in" aria-label="Quick links">
          {QUICK.map((q) => (
            <Link key={q.label} href={q.href} className="quick-tile" style={{ background: q.bg }}>
              <q.icon size={22} />
              <span>{q.label}</span>
            </Link>
          ))}
        </nav>

        <div className="dash-grid">
          <div>
            {/* Above the check-in on purpose: if the engine noticed something,
                saying so before asking for more data is the honest order. */}
            <InterventionCard />
            {/* Check-in hero */}
            <section className="checkin-hero cz-in cz-d1" aria-label="Daily check-in">
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

            {/* Weekly-insights teaser (ref "This week" strip). */}
            <Link
              href="/insights"
              className="card cz-in cz-d2"
              style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 14, textDecoration: "none" }}
            >
              <span style={{ flex: 1, minWidth: 0 }}>
                <strong className="serif" style={{ fontSize: 16 }}>This week</strong>
                <span style={{ display: "block", color: "var(--muted)", fontSize: 13, marginTop: 2 }}>
                  {weekCheckins > 0
                    ? `${weekCheckins} check-in${weekCheckins === 1 ? "" : "s"} logged · see what changed`
                    : "See what changed · weekly insights"}
                </span>
              </span>
              <Icon.chevron size={18} />
            </Link>

            {/* Active multi-day journey (ref "PROGRAM · DAY 3 OF 7" card). */}
            {program && (
              <Link
                href="/programs"
                className="card cz-in cz-d2"
                style={{ display: "block", marginTop: 14, textDecoration: "none" }}
              >
                <p className="eyebrow" style={{ color: "var(--cyan)", marginBottom: 4 }}>
                  Program · day {program.day} of {program.days}
                </p>
                <h3 style={{ margin: "0 0 8px" }}>{program.title}</h3>
                <div style={{ height: 6, borderRadius: 3, background: "var(--card-strong)" }} aria-hidden="true">
                  <div
                    style={{
                      height: 6, borderRadius: 3, width: `${Math.min(100, (program.day / Math.max(1, program.days)) * 100)}%`,
                      background: "var(--lav)",
                    }}
                  />
                </div>
                <p style={{ color: "var(--muted)", fontSize: 13, margin: "8px 0 0" }}>
                  {program.completed ? "Complete — beautifully done." : "Showing up is the whole assignment today."}
                </p>
                {program.today_guide?.title?.trim() && (
                  <p style={{ color: "var(--text)", fontSize: 13, margin: "8px 0 0" }}>
                    <strong>Today&apos;s focus · </strong>{program.today_guide.title}
                  </p>
                )}
              </Link>
            )}

            {/* Today's plan — the served agentic plan, same one /plan toggles. */}
            <div className="sec-head">
              <h2 className="serif-h">Today's plan</h2>
              <span className="sec-date">{new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}</span>
            </div>
            <div className="plan-list cz-in cz-d3">
              {(plan?.steps ?? [])
                .slice()
                .sort((a, b) => a.order - b.order)
                .map((s, i) => (
                  <Link key={s.id} href={s.done ? "/plan" : stepHref(s.symbol)} className="plan-row">
                    <span
                      className="plan-play"
                      style={s.done
                        ? { background: "var(--well)", color: "var(--muted)", fontWeight: 700 }
                        : { background: STEP_COLORS[i % STEP_COLORS.length], fontWeight: 700 }}
                    >
                      {s.done ? "✓" : <Icon.play size={16} />}
                    </span>
                    <span className="plan-body">
                      <strong style={s.done ? { textDecoration: "line-through", color: "var(--muted)" } : undefined}>{s.title}</strong>
                      <small>{s.detail}</small>
                    </span>
                    <span className="plan-start">{s.done ? "DONE" : "START"}</span>
                  </Link>
                ))}
              {planFailed && (
                <Link href="/plan" className="plan-row">
                  <span className="plan-play" style={{ background: STEP_COLORS[0] }}><Icon.play size={16} /></span>
                  <span className="plan-body"><strong>Open today's plan</strong><small>Personalized from your check-ins</small></span>
                  <span className="plan-start">START</span>
                </Link>
              )}
            </div>
            {plan && (
              <Link href="/plan" className="link" style={{ display: "inline-block", marginTop: 10 }}>
                Open full plan →
              </Link>
            )}

            {/* Jump back in */}
            <div className="sec-head"><h2 className="serif-h">Jump back in</h2></div>
            <div className="jump-grid cz-in cz-d4">
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
            {/* Merge note: main showed the BEST streak here with a milestone
                ring. Wave A replaced that with days-present — presence framing,
                no loss language — so v1's card wins and the streak-milestone
                ring goes with it. The entrance class is kept. */}
            <div className="rail-card cz-in cz-d2">
              <span className="kicker">This week</span>
              <div className="rail-big"><b>{presentDays}</b><span>{presentDays === 1 ? "day present" : "days present"}</span></div>
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

            <div className="rail-card cz-in cz-d3">
              <div className="sec-head" style={{ margin: 0 }}>
                <span className="serif-h" style={{ fontSize: 18 }}>Mood this week</span>
                <Link href="/insights" className="link">Details</Link>
              </div>
              {pts.length >= 2 ? (
                <svg viewBox="0 0 300 90" style={{ width: "100%", height: 80, marginTop: 12 }} aria-hidden="true">
                  <polyline
                    fill="none" stroke="url(#mg)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"
                    points={pts.map((s, i) => `${(i / (pts.length - 1)) * 290 + 5},${80 - ((s - 1) / 4) * 66}`).join(" ")}
                  />
                  <defs><linearGradient id="mg" x1="0" x2="1"><stop offset="0" stopColor="#8fe6ee" /><stop offset="1" stopColor="#8a7bf0" /></linearGradient></defs>
                </svg>
              ) : (
                <p className="sub" style={{ marginTop: 12 }}>
                  Your line starts after two check-ins.
                </p>
              )}
            </div>

            <div className="rail-card reflection cz-in cz-d4">
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
