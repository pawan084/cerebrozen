"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

type Entry = { id: string; title: string; body: string; tags: string[]; risk_level: string; created_at: string };
type Checkin = { mood: string; created_at: string };
// Every prompt opens the composer prefilled (prompt-as-title, Android
// JournalScreen pattern) — the last two absorb the old onegoodthing/intention
// mini-tools as quick entries (REDESIGN IA consolidation).
const REVISIT: { prompt: string; tag: string }[] = [
  { prompt: "What do you need more of this week?", tag: "Reflection" },
  { prompt: "Name a worry, then set it down.", tag: "Release" },
  { prompt: "Who made today a little easier?", tag: "Gratitude" },
  { prompt: "One good thing from today", tag: "Gratitude" },
  { prompt: "Tonight's intention", tag: "Intention" },
];

// State-tuned prompts (ref mock): the latest mood check-in shapes the hero.
// The .eyebrow class uppercases, so "For a tense day" renders FOR A TENSE DAY.
const TUNED = [
  { match: /anxious|tense/i, eyebrow: "For a tense day", title: "Name the worry", body: "Write the thought that keeps circling — then one truer thought beside it." },
  { match: /low|heavy|sad/i, eyebrow: "For a heavy day", title: "One kind sentence", body: "Write to yourself as you would to a friend." },
  { match: /tired|exhaust/i, eyebrow: "For a tired day", title: "Put the day down", body: "List what can wait until tomorrow — give yourself permission." },
];

export default function Journal() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");
  const [busy, setBusy] = useState(false);
  const [support, setSupport] = useState(false);
  const [mood, setMood] = useState("");

  useEffect(() => {
    void reload();
    // Same /moods feed Home reads — only today's check-in tunes the prompt (mirrors
    // iOS); a stale or Good/none check-in keeps the default.
    api<Checkin[]>("/moods?limit=1").then((m) => {
      const latest = m[0];
      if (latest && new Date(latest.created_at).toDateString() === new Date().toDateString()) setMood(latest.mood);
    }).catch(() => {});
  }, []);
  async function reload() { try { setEntries(await api<Entry[]>("/journal")); } catch {} }
  const tuned = mood ? TUNED.find((t) => t.match.test(mood)) : undefined;

  async function save(e: React.FormEvent) {
    e.preventDefault(); if (busy || !title.trim()) return; setBusy(true);
    try {
      const entry = await api<Entry>("/journal", { method: "POST", body: JSON.stringify({ title, body, tags: tags.split(",").map((t) => t.trim()).filter(Boolean), symbol: "book" }) });
      setSupport(["elevated", "crisis"].includes(entry.risk_level));
      setTitle(""); setBody(""); setTags(""); setOpen(false); await reload();
    } finally { setBusy(false); }
  }

  function usePrompt(p: { prompt: string; tag: string }) {
    setTitle(p.prompt);
    setTags(p.tag);
    setOpen(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const monthCount = entries.filter((e) => new Date(e.created_at).getMonth() === new Date().getMonth()).length;

  return (
    <>
      <AppHeader eyebrow="Journal" title="Reflect, gently" />
      <div className="page-body">
        {support && (
          <div className="crisis" role="alert">
            <strong>That sounded heavy — you deserve support right now.</strong><br />
            In India: Tele-MANAS <a href="tel:14416" style={{ color: "inherit" }}><strong>14416</strong></a> (real people, 24/7) ·
            emergency <a href="tel:112" style={{ color: "inherit" }}><strong>112</strong></a> ·
            KIRAN <a href="tel:18005990019" style={{ color: "inherit" }}><strong>1800-599-0019</strong></a> ·{" "}
            <Link href="/crisis" style={{ color: "inherit", fontWeight: 700 }}>all support options</Link>.
            Your entry was saved — writing is never blocked.
          </div>
        )}
        <div className="dash-grid">
          <div>
            {/* Both sides kept: main's state-tuned prompt (the ARCHITECTURE
                cross-stack row pairs it with iOS JournalPrompts.tuned) inside
                v1's theme-night scope, so the hero stays dark in Dawn and the
                composer's var-driven fields re-resolve to Night ink. */}
            <section className="prompt-hero theme-night cz-in">
              <p className="eyebrow">{tuned ? tuned.eyebrow : "Today's prompt · shaped by your check-in"}</p>
              <h2>{tuned ? tuned.title : "What's one small thing that felt lighter today?"}</h2>
              {tuned && (
                <p style={{ color: "rgba(255,255,255,0.78)", fontSize: 14, margin: "-8px 0 16px", maxWidth: "48ch" }}>
                  {tuned.body}
                </p>
              )}
              {!open && <button className="pill-btn" onClick={() => setOpen(true)}>+ Write an entry</button>}
              {open && (
                <form onSubmit={save} style={{ marginTop: 6 }}>
                  <label className="field"><span>Title</span><input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={120} /></label>
                  <label className="field"><span>What's on your mind?</span><textarea rows={4} value={body} onChange={(e) => setBody(e.target.value)} /></label>
                  <label className="field"><span>Tags (comma-separated)</span><input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="Work, Sleep" /></label>
                  <button className="btn" disabled={busy || !title.trim()}>{busy ? "Saving…" : "Save entry"}</button>
                </form>
              )}
            </section>

            <div className="sec-head"><h2 className="serif-h">Recent entries</h2></div>
            {entries.length === 0 && <p className="footnote">Nothing here yet — your entries collect below.</p>}
            {entries.map((e, i) => (
              <article className={`entry-card cz-in cz-d${Math.min(i + 1, 6)}`} key={e.id}>
                <span className="emoji">{e.risk_level === "crisis" || e.risk_level === "elevated" ? "😔" : "🙂"}</span>
                <span className="date">{new Date(e.created_at).toLocaleDateString(undefined, { month: "long", day: "numeric" })}</span>
                <q>{e.body || e.title}</q>
              </article>
            ))}
          </div>

          <div className="rail">
            <div className="rail-card cz-in cz-d1">
              <span className="kicker">This month</span>
              <div className="rail-big"><b>{monthCount}</b><span>{monthCount === 1 ? "entry" : "entries"}</span></div>
              <p className="sub">Every entry stays private by default.</p>
            </div>
            <div className="rail-card cz-in cz-d2">
              <span className="serif-h" style={{ fontSize: 18 }}>Prompts you can revisit</span>
              <div className="plist" style={{ marginTop: 8 }}>
                {REVISIT.map((p) => (
                  <button
                    key={p.prompt}
                    className="prompt-item"
                    onClick={() => usePrompt(p)}
                    style={{ display: "block", width: "100%", textAlign: "left", background: "none", border: "none", cursor: "pointer", font: "inherit", color: "inherit" }}
                  >
                    {p.prompt}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
