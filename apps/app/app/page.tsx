"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Icon, firstName, useMe } from "@/components/shell";
import { addMood, listJournal, listMoods, Unavailable, type MoodEntry } from "@/lib/wellness";
import { celebrate } from "@/lib/celebrate";

const MOODS = ["😣", "😔", "😐", "🙂", "😌"];
const MOOD_LABELS = ["struggling", "low", "okay", "good", "great"];
const DAYS = ["M", "T", "W", "T", "F", "S", "S"];

function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}
// Mon=0..Sun=6, so the row reads M T W T F S S like the design.
function weekdayIdx(d: Date): number {
  return (d.getDay() + 6) % 7;
}
function moodDate(m: MoodEntry): Date | null {
  const s = m.created_at || m.at;
  if (!s) return null;
  const d = new Date(s);
  return isNaN(d.getTime()) ? null : d;
}
function isThisWeek(d: Date): boolean {
  const now = new Date();
  const monday = new Date(now);
  monday.setHours(0, 0, 0, 0);
  monday.setDate(now.getDate() - weekdayIdx(now));
  const nextMon = new Date(monday);
  nextMon.setDate(monday.getDate() + 7);
  return d >= monday && d < nextMon;
}

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
  const router = useRouter();
  const [mood, setMood] = useState<number | null>(null);
  const [moodNote, setMoodNote] = useState("");
  const [q, setQ] = useState("");
  // null = still loading; [] = loaded-but-empty (or failed → treated as empty, honestly)
  const [moods, setMoods] = useState<MoodEntry[] | null>(null);
  const [reflection, setReflection] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    listMoods().then((m) => live && setMoods(m ?? [])).catch(() => live && setMoods([]));
    listJournal()
      .then((entries) => {
        if (!live) return;
        const body = entries?.[0]?.body?.trim();
        setReflection(body ? body.split("\n")[0].slice(0, 160) : "");
      })
      .catch(() => live && setReflection(""));
    return () => { live = false; };
  }, []);

  async function pickMood(i: number) {
    setMood(i);
    setMoodNote("Noted — thanks for checking in. This stays private to you.");
    try {
      const saved = await addMood(MOOD_LABELS[i], MOODS[i], i + 1);
      // Reflect it in the week/trend immediately (real data, not faked).
      setMoods((prev) => [
        { ...(saved ?? {}), intensity: i + 1, created_at: new Date().toISOString() } as MoodEntry,
        ...(prev ?? []),
      ]);
      celebrate("Checked in");
    } catch (e) {
      if (e instanceof Unavailable) {
        setMoodNote(
          e.reason === "consent"
            ? "Noted — turn on Mood history in Settings to keep a trend."
            : e.reason === "disabled"
              ? "Noted — not saved: wellness storage isn't enabled for your workspace."
              : "Noted — but we couldn't save it just now. Check your connection and try again."
        );
      } else {
        setMoodNote("Noted — but we couldn't save it just now. Try again in a moment.");
      }
    }
  }

  // Real weekly rhythm: which days this week actually have a check-in.
  const checkedDays = useMemo(() => {
    const s = new Set<number>();
    for (const m of moods ?? []) {
      const d = moodDate(m);
      if (d && isThisWeek(d)) s.add(weekdayIdx(d));
    }
    return s;
  }, [moods]);

  // Real trend: the most recent check-ins, oldest→newest, from actual intensities.
  const trend = useMemo(() => {
    return (moods ?? [])
      .filter((m) => typeof m.intensity === "number" && (m.intensity as number) > 0)
      .slice(0, 7)
      .reverse();
  }, [moods]);

  const sparkPoints = useMemo(() => {
    if (trend.length < 2) return "";
    return trend
      .map((m, i) => {
        const x = (i / (trend.length - 1)) * 300;
        const clamped = Math.min(5, Math.max(1, m.intensity as number));
        const y = 64 - ((clamped - 1) / 4) * 54; // 1→64 (low), 5→10 (high)
        return `${x.toFixed(0)},${y.toFixed(0)}`;
      })
      .join(" ");
  }, [trend]);

  const loading = moods === null;
  const dateLabel = new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" });

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="eyebrow">Welcome back</div>
          <h1>{greeting()}, {firstName(me) || "there"}</h1>
        </div>
        <div className="head-left">
          <form
            className="search"
            onSubmit={(e) => {
              e.preventDefault();
              const t = q.trim();
              router.push(t ? `/coach?q=${encodeURIComponent(t)}` : "/coach");
            }}
          >
            {Icon.search}
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              aria-label="Talk to your coach"
              placeholder="What's on your mind?"
            />
          </form>
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
                  onClick={() => pickMood(i)} aria-label={`I'm feeling ${MOOD_LABELS[i]}`}
                  aria-pressed={mood === i}><span aria-hidden="true">{m}</span></button>
              ))}
            </div>
            <p className="hint" aria-live="polite">
              {mood === null ? "Tap how you're feeling — there's no wrong answer." : moodNote}
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
              <span className="stat-num">{checkedDays.size}</span>
              <span className="lbl">of 7 days<br />checked in</span>
            </div>
            <div className="dots">
              {DAYS.map((_, i) => <span key={i} className={`dot ${checkedDays.has(i) ? "on" : ""}`} />)}
            </div>
            <div className="dot-lbls">{DAYS.map((d, i) => <span key={i}>{d}</span>)}</div>
            <p className="placeholder" style={{ marginTop: 14 }}>
              {checkedDays.size === 0
                ? "Check in daily to build your rhythm — gently, no streaks to break."
                : "Your rhythm, this week — gently, no streaks to break."}
            </p>
          </div>

          <div className="card">
            <div className="sec-title" style={{ margin: "0 0 6px" }}>
              <h3>Mood this week</h3><Link href="/insights" className="link-accent">Details</Link>
            </div>
            {sparkPoints ? (
              <>
                <svg className="spark" viewBox="0 0 300 74" preserveAspectRatio="none" role="img"
                  aria-label={`Your mood trend across your last ${trend.length} check-ins`}>
                  <polyline fill="none" stroke="url(#g)" strokeWidth="2.5" points={sparkPoints} />
                  <defs><linearGradient id="g" x1="0" x2="1"><stop offset="0" stopColor="#56c8c0" /><stop offset="1" stopColor="#f5836b" /></linearGradient></defs>
                </svg>
                <p className="placeholder">From your own check-ins — private to you.</p>
              </>
            ) : (
              <p className="placeholder" style={{ marginTop: 8 }}>
                {loading ? "Loading your trend…" : "Your trend fills in once you've checked in a couple of times."}
              </p>
            )}
          </div>

          <div className="card reflection">
            <div className="eyebrow">Last reflection</div>
            {reflection ? (
              <p className="q">“{reflection}”</p>
            ) : (
              <p className="q">Your reflections appear here — a line you want to remember from a session or journal entry.</p>
            )}
            <Link href="/journal" className="link-accent">Open journal →</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
