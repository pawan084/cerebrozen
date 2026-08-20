"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { OUTBOX_EVENT, send } from "@/lib/outbox";
import { AppHeader } from "@/components/AppHeader";
import { GuidedTour } from "@/components/GuidedTour";
import { InterventionCard } from "@/components/InterventionCard";
import { Icon } from "@/components/icons";
import { heroKindFor, heroWhy, heroWorksOffline } from "@/lib/todayHero";

// The check-in vocabulary (TOD-02, six states).
//
// CROSS-STACK CONTRACT: `name` goes to the server verbatim and is READ there
// (backend/app/services/moods.py). Android ui/screens/TodayScreen.kt MOODS and
// iOS Models/DummyData.swift carry the same six.
//
// This list used to be Great/Good/Okay/Low/Anxious — it had drifted from the
// other two clients, which offered Good/Anxious/Low/Tired. Web therefore had no
// way to say "Tired", so a web check-in could never schedule the wind-down
// nudge that keys on it (services/nudges.py). "Great" and "Okay" are gone:
// three shades of fine is a rating scale, which is the thing a check-in must
// not become.
//
// "Not sure" is load-bearing — the answer for someone who cannot name a
// feeling — and the server scores it as neither distress nor contentment.
const MOODS = [
  { emoji: "🙂", name: "Good", note: "Clear", symbol: "sparkles", resp: "Steady is a lovely place to be." },
  { emoji: "😰", name: "Anxious", note: "Loud thoughts", symbol: "exclamationmark.triangle", resp: "Loud thoughts are real. Want a 2-minute reset?" },
  { emoji: "😔", name: "Low", note: "Heavy", symbol: "moon", resp: "Thanks for being honest — let's go gently." },
  { emoji: "😪", name: "Tired", note: "Need rest", symbol: "drop", resp: "Rest is valid. A wind-down could ease you." },
  { emoji: "😵", name: "Overwhelmed", note: "Too much at once", symbol: "exclamationmark.triangle", resp: "One thing at a time. A grounding minute can make room." },
  { emoji: "🤔", name: "Not sure", note: "Closest fit right now", symbol: "minus", resp: "That's allowed — you don't have to name it." },
];

type Streak = { current: number; best: number; week: { date: string; active: boolean }[] };
type Mood = { id: string; mood: string; created_at: string };
type Entry = { id: string; body: string; created_at: string };
type Step = { id: string; title: string; detail: string; symbol: string; order: number; done: boolean };
// `source` ("ai" | "rule") drives the hero's provenance sentence — see
// lib/todayHero.ts. /plans/active has always sent it; Home simply never read it.
type Plan = { id: string; title: string; focus?: string; source?: string; steps: Step[] };
// `today_guide` is additive — absent for programs with no day guides, and for
// servers older than the migration that added them. Omit-tolerant like Android.
type Program = {
  content_id: string;
  title: string;
  day: number;
  days: number;
  completed: boolean;
  today_guide?: { title: string; body: string } | null;
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
  const [checkInError, setCheckInError] = useState<string | null>(null);
  /** Written here, not yet on the server. Said out loud, because the streak
   *  and the trends below will not move until it syncs. */
  const [checkInQueued, setCheckInQueued] = useState(false);
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

  // When the offline queue finally drains, the numbers on this page are stale
  // by exactly the writes it just sent — so refetch the two it can move.
  useEffect(() => {
    const onOutbox = (e: Event) => {
      if (!((e as CustomEvent).detail?.sent > 0)) return;
      setCheckInQueued(false);
      api<Streak>("/users/me/streak").then(setStreak).catch(() => {});
      api<Mood[]>("/moods?limit=60").then(setMoods).catch(() => {});
    };
    window.addEventListener(OUTBOX_EVENT, onOutbox);
    return () => window.removeEventListener(OUTBOX_EVENT, onOutbox);
  }, []);

  async function pick(m: (typeof MOODS)[number]) {
    setPicked(m.name);
    setResp(m.resp);
    setCheckInError(null);
    setCheckInQueued(false);
    try {
      // The outbox keeps this tap when the network drops instead of losing it,
      // and returns null to say so.
      const row = await send("/moods", { mood: m.name, note: m.note, symbol: m.symbol, intensity: 3 });
      setCheckInQueued(row === null);
      if (row !== null) api<Streak>("/users/me/streak").then(setStreak).catch(() => {});
    } catch {
      // Register D3: the affirming response was shown optimistically and the
      // POST error swallowed - the user was told "Love that..." while nothing
      // was saved and the streak never moved. The kindest version of this is
      // still the true one: take the response back and say what happened.
      setPicked(null);
      setResp("");
      setCheckInError("We couldn't save that check-in. Tap a feeling again when you're ready.");
    }
  }

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  const days = ["S", "M", "T", "W", "T", "F", "S"];
  // A gentle mood line for the rail chart, scored by recency — only ever the
  // user's real days. When there aren't two real check-ins yet the card says so;
  // it never draws an invented shape (the `[3,4,3,4,3,4,4]` fallback that used
  // to stand in here was deleted in WEB_PARITY Wave A).
  // One point per LOCAL day, not per check-in (register D21): five check-ins
  // today used to draw a week-looking line under a "This week" label. Days
  // with several check-ins average; days without one simply aren't drawn —
  // same no-invented-shape rule as always.
  // Mirrors the server's ease_score (services/trends.py): a difficult feeling
  // sits low on the axis, a settled one high, and an unknown label — including
  // "Not sure", which is a declined answer rather than a middling one — is
  // neutral instead of guessed at.
  const score = (m: Mood) =>
    ({ Good: 5, Tired: 3, Anxious: 2, Low: 2, Overwhelmed: 1 } as Record<string, number>)[m.mood] ?? 3;
  const byDay = new Map<string, number[]>();
  for (const m of moods) {
    const d = new Date(m.created_at);
    if (Number.isNaN(d.getTime())) continue;
    const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
    if (!byDay.has(key)) byDay.set(key, []);
    byDay.get(key)!.push(score(m));
  }
  // moods arrive newest-first, so insertion order is newest day → oldest.
  const pts = [...byDay.values()]
    .slice(0, 7)
    .reverse()
    .map((scores) => scores.reduce((a, b) => a + b, 0) / scores.length);
  const presentDays = streak?.week?.filter((d) => d.active).length ?? 0;

  // TOD-01: the hero is ONE decision at full volume. `planLoaded` is false only
  // while the first fetch is in flight — a failed fetch counts as loaded, so a
  // dead network shows the honest fallback rather than a spinner forever.
  const planLoaded = plan !== null || planFailed;
  const nextStep = (plan?.steps ?? []).slice().sort((a, b) => a.order - b.order).find((s) => !s.done);
  const heroKind = heroKindFor(planLoaded, (plan?.steps?.length ?? 0) > 0, !!nextStep);
  const heroHref = nextStep ? stepHref(nextStep.symbol) : "/games";
  // Presence framing in the fold summary: it counts what happened, never what
  // was missed. "3 still open" is a statement of availability, not a debt.
  const stepsDone = (plan?.steps ?? []).filter((s) => s.done).length;
  const stepsOpen = (plan?.steps ?? []).length - stepsDone;
  // One honest line for the weekly-insights teaser, from the same mood fetch.
  const weekCheckins = moods.filter((m) => Date.now() - new Date(m.created_at).getTime() < 7 * 86400e3).length;

  return (
    <>
      <AppHeader eyebrow="Welcome back" title={`${greeting}${name ? `, ${name}` : ""}`} />
      <GuidedTour />
      <div className="page-body">
        {/* TOD-01 — one decision at full volume, above everything else.
            The dashboard below it stays for now; the fold-down is the second
            half of this graduation. */}
        <section className="today-hero cz-in" aria-labelledby="next-step">
          <p className="eyebrow">Your next helpful step</p>
          {heroKind === "loading" ? (
            <h2 id="next-step" className="today-hero-title">Finding your next step…</h2>
          ) : heroKind === "plan-done" ? (
            <>
              <h2 id="next-step" className="today-hero-title">
                That is today&rsquo;s plan done.
              </h2>
              <p className="today-why">
                Nothing else is required of you today. Anything more is because you want to.
              </p>
            </>
          ) : (
            <>
              <h2 id="next-step" className="today-hero-title">
                {nextStep?.title ?? "Make a little room around loud thoughts."}
              </h2>
              <ul className="today-chips" aria-label="What this involves">
                <li>3 minutes</li>
                {heroWorksOffline(heroHref) && <li>Works offline</li>}
                <li>Nothing to score</li>
              </ul>
              {/* The provenance sentence follows the plan's REAL generator —
                  never hardcoded. See lib/todayHero.ts for why. */}
              <p className="today-why">
                {heroKind === "fallback"
                  ? "A steady practice to start with. This one is not personalised yet — it will be once you have checked in a few times."
                  : heroWhy(plan?.source)}
              </p>
            </>
          )}
          {heroKind !== "loading" && (
            <div className="today-actions">
              <Link href={heroKind === "plan-done" ? "/plan" : heroHref} className="btn btn-primary today-cta">
                {heroKind === "plan-done" ? "Look at the plan" : "Begin"}
              </Link>
              <Link href="/explore" className="text-btn">Choose something else</Link>
            </div>
          )}
        </section>

        {/* TOD-01: the quick-links grid used to sit here at full volume, four
            tiles competing with the one decision above them. Folded rather than
            deleted — the destinations are still wanted, just not shouted. */}
        <details className="today-fold">
          <summary>
            <span>Somewhere else</span>
            <small>Games, insights, programs, sounds</small>
          </summary>
          <nav className="quick-grid cz-in" aria-label="Quick links">
            {QUICK.map((q) => (
              <Link key={q.label} href={q.href} className="quick-tile" style={{ background: q.bg }}>
                <q.icon size={22} />
                <span>{q.label}</span>
              </Link>
            ))}
          </nav>
        </details>

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
              {/* TOD-02's thesis: a check-in must end on a CONSEQUENCE, not a
                  saved value. No score, no rating, no level, no trend arrow —
                  just what this does next. Both claims are real: moods feed the
                  plan generator (services/agentic.py) and the weekly trends
                  (services/insights.py).

                  It deliberately says nothing about the journal. Whether the
                  journal is read depends on which generator runs — see
                  lib/todayHero.ts — so a flat claim here would be false half
                  the time. */}
              {picked && !checkInError && (
                <p className="tiny">
                  This shapes your next step and your weekly trends. Nothing here is scored.
                </p>
              )}
              {checkInQueued && !checkInError && (
                <p className="tiny" role="status">
                  Saved on this device — it will sync, and count towards your streak, once
                  you are back online.
                </p>
              )}
              {checkInError && <p className="error" role="alert">{checkInError}</p>}
              {/* The door to TOD-02 (/checkin), which graduated 2026-08-12. This
                  row saves one tap and one feeling; that screen adds intensity, a
                  private note, and an itemised account of which signals are
                  switched on. Without this link it would have been a finished
                  screen with no entrance — the exact defect being cleaned up on
                  Android the same week. */}
              <Link href="/checkin" className="text-btn checkin-more">
                Add intensity or a note
              </Link>
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
                {/* The day's own guide, so the card is not day-blind. */}
                {program.today_guide &&
                  (program.today_guide.title.trim() || program.today_guide.body.trim()) && (
                    <div style={{ marginTop: 12, paddingTop: 10, borderTop: "1px solid var(--line)" }}>
                      <p className="eyebrow" style={{ marginBottom: 4 }}>Today&apos;s guide</p>
                      {program.today_guide.title.trim() && (
                        <h4 style={{ margin: "0 0 4px", fontSize: 14 }}>{program.today_guide.title}</h4>
                      )}
                      {program.today_guide.body.trim() && (
                        <p style={{ color: "var(--muted)", fontSize: 13, margin: 0 }}>
                          {program.today_guide.body}
                        </p>
                      )}
                    </div>
                  )}
              </Link>
            )}

            {/* Today's plan — the served agentic plan, same one /plan toggles.
                TOD-01 folds it: the hero already names the next step, so the
                full list is for when you deliberately want the whole day. */}
            <details className="today-fold">
              <summary>
                <span>Your day</span>
                <small>
                  {planLoaded && (plan?.steps?.length ?? 0) > 0
                    ? `${stepsDone} done, ${stepsOpen} still open`
                    : new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}
                </small>
              </summary>
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
              {/* This row renders ONLY when the plan fetch failed, and it used
                  to promise "Personalized from your check-ins" — a claim about
                  a plan the app had just failed to load. Worse, `planFailed`
                  makes `heroKind` "fallback", so the hero directly above was
                  simultaneously saying "This one is not personalised yet". One
                  render, two contradictory claims. Found by comparing this
                  screen against the Android Home, which carries the same
                  `heroKindFor` contract. */}
              {planFailed && (
                <Link href="/plan" className="plan-row">
                  <span className="plan-play" style={{ background: STEP_COLORS[0] }}><Icon.play size={16} /></span>
                  <span className="plan-body"><strong>Open today&apos;s plan</strong><small>We could not load it just now — open to try again</small></span>
                  <span className="plan-start">START</span>
                </Link>
              )}
            </div>
            {plan && (
              <Link href="/plan" className="link" style={{ display: "inline-block", marginTop: 10 }}>
                Open full plan →
              </Link>
            )}
            <p className="tiny">
              Flexible, not a streak. Do what helps and skip what does not — blank slots are
              simply blank.
            </p>
            </details>

            {/* Jump back in */}
            <details className="today-fold">
              <summary>
                <span>Jump back in</span>
                <small>Talk, breathe, sleep, journal</small>
              </summary>
              <div className="jump-grid cz-in cz-d4">
                {JUMP.map((j) => (
                  <Link key={j.label} href={j.href} className="jump-card" style={{ background: j.bg }}>
                    <j.icon size={22} />
                    <span>{j.label}</span>
                  </Link>
                ))}
              </div>
            </details>
          </div>

          {/* Right rail */}
          <div className="rail">
            {/* Merge note: main showed the BEST streak here with a milestone
                ring. Wave A replaced that with days-present — presence framing,
                no loss language — so v1's card wins and the streak-milestone
                ring goes with it. The entrance class is kept. */}
            <div className="rail-card cz-in cz-d2">
              {/* Days present in the week, not a consecutive run: a run is a
                  thing that breaks, which is the loss framing the presence pass
                  removed — and the sub-line right below already promises "no
                  streaks to break". iOS and Android both show the weekly count,
                  so this keeps the three clients saying the same thing. */}
              <span className="kicker">This week</span>
              <div className="rail-big"><b>{presentDays}</b><span>{presentDays === 1 ? "day present" : "days present"}</span></div>
              <p className="sub">Gentle and consistent — no streaks to break.</p>
              <div className="rhythm-bars">
                {/* Register D20: the placeholder used `date: "0".."6"`, and
                    `new Date("0")` PARSES in Chromium (year 2000) — so the
                    empty week printed real but wrong weekday letters instead
                    of falling through to the positional ones. The placeholder
                    now carries no date at all, and the letter is only read
                    from a date that actually parses. */}
                {(streak?.week ?? Array.from({ length: 7 }, () => ({ date: "", active: false }))).map((d, i) => {
                  const parsed = d.date ? new Date(d.date) : null;
                  const letter =
                    parsed && !Number.isNaN(parsed.getTime()) ? days[parsed.getDay()] : days[i];
                  return (
                    <div key={d.date || `slot-${i}`} className="b">
                      <span className={d.active ? "fill" : "fill off"} />
                      <em>{letter}</em>
                    </div>
                  );
                })}
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
                  {/* Tokens, not literals: this chart sits on a page that flips
                      to Dawn, where the Night cyan/lavender lose their ground. */}
                  <defs><linearGradient id="mg" x1="0" x2="1"><stop offset="0" stopColor="var(--cyan)" /><stop offset="1" stopColor="var(--lav)" /></linearGradient></defs>
                </svg>
              ) : (
                <p className="sub" style={{ marginTop: 12 }}>
                  Your line starts after check-ins on two different days — tap a face above and it begins.
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
