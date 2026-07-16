"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon, firstName, useMe } from "@/components/shell";

const MOODS = ["😣", "😔", "😐", "🙂", "😌"];
const DAYS = ["M", "T", "W", "T", "F", "S", "S"];

function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}
// Mon=0..Sun=6, so the row reads M T W T F S S like the design.
function weekIndex(): number {
  return (new Date().getDay() + 6) % 7;
}

// A CTA "plan" row — the coaching analogues of the reference's audio sessions.
const PLAN = [
  { icon: "talk", tint: "linear-gradient(135deg,#ffd0c4,#f5836b)", t: "Talk it through", s: "A live coaching session · ends with one concrete step", href: "/coach", cta: "Start" },
  { icon: "journal", tint: "linear-gradient(135deg,#a9f0e6,#56c8c0)", t: "Write it down", s: "A private journal entry · only you ever see it", href: "/journal", cta: "Open" },
  { icon: "insights", tint: "linear-gradient(135deg,#c3ccff,#7c8cff)", t: "See your patterns", s: "Insights from your sessions over time", href: "/insights", cta: "View" },
];

const TILES = [
  { icon: "talk", t: "Talk now", href: "/coach" },
  { icon: "journal", t: "Journal", href: "/journal" },
  { icon: "insights", t: "Insights", href: "/insights" },
  { icon: "settings", t: "Settings", href: "/settings" },
];

export default function Home() {
  const me = useMe();
  const [mood, setMood] = useState<number | null>(null);
  const today = weekIndex();
  const dateLabel = new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" });

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Welcome back</div>
          <h1>{greeting()}, {firstName(me) || "there"}</h1>
        </div>
        <div className="head-left">
          <div className="search">{Icon.search}<span>Search…</span></div>
        </div>
      </div>

      <div className="dash">
        {/* ── main column ── */}
        <div className="col">
          <section className="checkin">
            <div className="eyebrow">Daily check-in</div>
            <h2>How are you arriving today?</h2>
            <div className="moods">
              {MOODS.map((m, i) => (
                <button key={i} className={`mood ${mood === i ? "sel" : ""}`}
                  onClick={() => setMood(i)} aria-label={`mood ${i + 1}`}>{m}</button>
              ))}
            </div>
            <p className="hint">
              {mood === null
                ? "Tap how you're feeling — there's no wrong answer."
                : "Noted — thanks for checking in. This stays private to you."}
            </p>
          </section>

          <div>
            <div className="sec-title"><h2>Your coaching</h2><span>{dateLabel}</span></div>
            {PLAN.map((p) => (
              <Link key={p.t} href={p.href} className="plan-item">
                <span className="p-icon" style={{ background: p.tint }}>{Icon[p.icon]}</span>
                <span className="p-body"><span className="t">{p.t}</span><span className="s">{p.s}</span></span>
                <span className="p-start">{p.cta}</span>
              </Link>
            ))}
          </div>

          <div>
            <div className="sec-title"><h2>Jump back in</h2></div>
            <div className="jump">
              {TILES.map((t) => (
                <Link key={t.t} href={t.href} className="tile">
                  {Icon[t.icon]}<span className="t">{t.t}</span>
                </Link>
              ))}
            </div>
          </div>
        </div>

        {/* ── right rail ── */}
        <div className="col">
          <div className="card">
            <div className="rhythm-head">
              <span className="stat-num">{today + 1}</span>
              <span className="lbl">of 7 days<br />this week</span>
            </div>
            <div className="dots">
              {DAYS.map((_, i) => <span key={i} className={`dot ${i <= today ? "on" : ""}`} />)}
            </div>
            <div className="dot-lbls">{DAYS.map((d, i) => <span key={i}>{d}</span>)}</div>
            <p className="placeholder" style={{ marginTop: 14 }}>Check in daily to build your rhythm — gently, no streaks to break.</p>
          </div>

          <div className="card">
            <div className="sec-title" style={{ margin: "0 0 6px" }}>
              <h3>Mood this week</h3><Link href="/insights" className="link-accent">Details</Link>
            </div>
            <svg className="spark" viewBox="0 0 300 74" preserveAspectRatio="none" aria-hidden="true">
              <polyline fill="none" stroke="url(#g)" strokeWidth="2.5"
                points="0,52 50,44 100,50 150,30 200,40 250,24 300,32" />
              <defs><linearGradient id="g" x1="0" x2="1"><stop offset="0" stopColor="#56c8c0" /><stop offset="1" stopColor="#f5836b" /></linearGradient></defs>
            </svg>
            <p className="placeholder">Your trend fills in as you check in and complete sessions.</p>
          </div>

          <div className="card reflection">
            <div className="eyebrow">Last reflection</div>
            <p className="q">Your reflections appear here — a line you want to remember from a session or journal entry.</p>
            <Link href="/journal" className="link-accent">Open journal →</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
