"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

type Entry = { id: string; title: string; body: string; tags: string[]; risk_level: string; created_at: string };
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

export default function Journal() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");
  const [busy, setBusy] = useState(false);
  const [support, setSupport] = useState(false);

  useEffect(() => { void reload(); }, []);
  async function reload() { try { setEntries(await api<Entry[]>("/journal")); } catch {} }

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
            {/* theme-night: the hero stays dark in Dawn, and the composer's
                var-driven labels/fields inside re-resolve to Night ink. */}
            <section className="prompt-hero theme-night">
              <p className="eyebrow">Today's prompt</p>
              <h2>What's one small thing that felt lighter today?</h2>
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
            {entries.map((e) => (
              <article className="entry-card" key={e.id}>
                <span className="date">{new Date(e.created_at).toLocaleDateString(undefined, { month: "long", day: "numeric" })}</span>
                <q>{e.body || e.title}</q>
              </article>
            ))}
          </div>

          <div className="rail">
            <div className="rail-card">
              <span className="kicker">This month</span>
              <div className="rail-big"><b>{monthCount}</b><span>{monthCount === 1 ? "entry" : "entries"}</span></div>
              <p className="sub">Every entry stays private by default.</p>
            </div>
            <div className="rail-card">
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
