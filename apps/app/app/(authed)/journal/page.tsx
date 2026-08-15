"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";
import { CrisisLines } from "@/components/CrisisLines";

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

// A half-written entry survives a closed tab: the draft mirrors to this key on
// every keystroke and clears only on a successful save. Local-only — drafts
// never travel until the person chooses to save.
const DRAFT_KEY = "cerebro_app_journal_draft";

export default function Journal() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");
  const [busy, setBusy] = useState(false);
  const [support, setSupport] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [mood, setMood] = useState("");
  // Search over the same server index Android uses (/journal?q= & ?tag=).
  const [query, setQuery] = useState("");
  const [tagFilter, setTagFilter] = useState("");
  const [allTags, setAllTags] = useState<string[]>([]);
  const [searched, setSearched] = useState(false);

  // Takes its query and tag as required arguments rather than defaulting them
  // from state (WEB-01). The defaults made every render a new `reload`, which
  // is why the mount effect below had to omit it from its dependencies and
  // earn an exhaustive-deps warning — the shape a stale closure ships in.
  // With no component values captured, the identity is stable and the effect
  // can name it honestly. It has to sit above that effect, too: the old
  // `function reload` hoisted, a `const` does not.
  const reload = useCallback(async (q: string, tag: string) => {
    const params = new URLSearchParams();
    if (q.trim()) params.set("q", q.trim());
    if (tag) params.set("tag", tag);
    const qs = params.toString();
    try {
      setEntries(await api<Entry[]>(`/journal${qs ? `?${qs}` : ""}`));
      setSearched(Boolean(qs));
    } catch {}
  }, []);

  useEffect(() => {
    // Unfiltered on mount — the search box and tag chips start empty, so this
    // is exactly what the old default-argument call resolved to.
    void reload("", "");
    api<string[]>("/journal/tags").then(setAllTags).catch(() => {});
    // Same /moods feed Home reads — only today's check-in tunes the prompt (mirrors
    // iOS); a stale or Good/none check-in keeps the default.
    api<Checkin[]>("/moods?limit=1").then((m) => {
      const latest = m[0];
      if (latest && new Date(latest.created_at).toDateString() === new Date().toDateString()) setMood(latest.mood);
    }).catch(() => {});
    // Restore an unsaved draft from a previous visit, composer open and ready.
    try {
      const raw = window.localStorage.getItem(DRAFT_KEY);
      if (raw) {
        const d = JSON.parse(raw) as { title?: string; body?: string; tags?: string };
        if (d.title || d.body) {
          setTitle(d.title ?? ""); setBody(d.body ?? ""); setTags(d.tags ?? "");
          setOpen(true);
        }
      }
    } catch {}
  }, [reload]);

  // Mirror the draft on change; remove the key once everything is empty.
  useEffect(() => {
    if (!open) return;
    if (title.trim() || body.trim()) {
      window.localStorage.setItem(DRAFT_KEY, JSON.stringify({ title, body, tags }));
    } else {
      window.localStorage.removeItem(DRAFT_KEY);
    }
  }, [open, title, body, tags]);

  const tuned = mood ? TUNED.find((t) => t.match.test(mood)) : undefined;

  async function save(e: React.FormEvent) {
    e.preventDefault(); if (busy || !title.trim()) return; setBusy(true); setSaveError(null);
    try {
      const entry = await api<Entry>("/journal", { method: "POST", body: JSON.stringify({ title, body, tags: tags.split(",").map((t) => t.trim()).filter(Boolean), symbol: "book" }) });
      setSupport(["elevated", "crisis"].includes(entry.risk_level));
      window.localStorage.removeItem(DRAFT_KEY);
      setTitle(""); setBody(""); setTags(""); setOpen(false); await reload(query, tagFilter);
      api<string[]>("/journal/tags").then(setAllTags).catch(() => {});
    } catch {
      // Register D5: the draft survived but nothing said why the entry never
      // appeared. The words are the point here - they stay, and so does the
      // reason.
      setSaveError("We couldn't save that entry. Your writing is still here — try again.");
    } finally { setBusy(false); }
  }

  function applyPrompt(p: { prompt: string; tag: string }) {
    setTitle(p.prompt);
    setTags(p.tag);
    setOpen(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const monthEntries = entries.filter((e) => new Date(e.created_at).getMonth() === new Date().getMonth());
  const monthCount = monthEntries.length;
  // Derived from the entries actually written this month — never a stock claim.
  // Needs at least three entries and a clear plurality before it says anything.
  const whenLine = (() => {
    if (monthCount < 3) return "";
    const parts = ["at night", "in the morning", "in the afternoon", "in the evening"];
    const tally = [0, 0, 0, 0];
    for (const e of monthEntries) {
      const h = new Date(e.created_at).getHours();
      tally[h < 5 || h >= 22 ? 0 : h < 12 ? 1 : h < 17 ? 2 : 3] += 1;
    }
    const top = tally.indexOf(Math.max(...tally));
    return tally[top] * 2 > monthCount ? `Most often ${parts[top]}.` : "";
  })();

  return (
    <>
      <AppHeader eyebrow="Journal" title="Reflect, gently" />
      <div className="page-body">
        {support && (
          <div className="crisis" role="alert">
            <strong>That sounded heavy — you deserve support right now.</strong>
            <CrisisLines compact />
            <p className="footnote">
              Your entry was saved — writing is never blocked.{" "}
              <Link href="/support">More ways to get help</Link>
            </p>
          </div>
        )}
        <div className="dash-grid">
          <div>
            {/* theme-night keeps the hero dark in Dawn, so the composer's
                var-driven fields re-resolve to Night ink (the hero carries its
                own art). The state-tuned prompt pairs with iOS
                JournalPrompts.tuned — see the ARCHITECTURE cross-stack row. */}
            <section className="prompt-hero theme-night cz-in">
              {/* Only the tuned variant is actually shaped by a check-in — the
                  default prompt says only what it is. */}
              <p className="eyebrow">{tuned ? tuned.eyebrow : "Today's prompt"}</p>
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
                  <label className="field"><span>What&apos;s on your mind?</span><textarea rows={4} value={body} onChange={(e) => setBody(e.target.value)} /></label>
                  <label className="field"><span>Tags (comma-separated)</span><input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="Work, Sleep" /></label>
                  <button className="btn" disabled={busy || !title.trim()}>{busy ? "Saving…" : "Save entry"}</button>
                  {saveError && <p className="error" role="alert">{saveError}</p>}
                </form>
              )}
            </section>

            <div className="sec-head"><h2 className="serif-h">Recent entries</h2></div>
            {/* Search + tag filter over the server's own journal index. */}
            <form
              className="row"
              style={{ gap: 8, marginBottom: 12, flexWrap: "wrap" }}
              onSubmit={(e) => { e.preventDefault(); void reload(query, tagFilter); }}
              role="search"
              aria-label="Search journal"
            >
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search your entries…"
                aria-label="Search your entries"
                style={{ flex: "1 1 200px" }}
              />
              <button className="btn ghost" disabled={busy}>Search</button>
              {(query || tagFilter) && (
                <button
                  type="button"
                  className="linklike"
                  onClick={() => { setQuery(""); setTagFilter(""); void reload("", ""); }}
                >
                  Clear
                </button>
              )}
              {allTags.length > 0 && (
                <div className="row" style={{ gap: 6, flexBasis: "100%", flexWrap: "wrap" }}>
                  {allTags.map((t) => (
                    <button
                      key={t}
                      type="button"
                      className="chip"
                      aria-pressed={tagFilter === t}
                      style={tagFilter === t ? { borderColor: "var(--lav)", color: "var(--text)" } : undefined}
                      onClick={() => { const next = tagFilter === t ? "" : t; setTagFilter(next); void reload(query, next); }}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              )}
            </form>
            {entries.length === 0 && (
              <p className="footnote">
                {searched
                  ? "No entries match that — try fewer words, or clear the filter."
                  : "Nothing here yet — your entries collect below."}
              </p>
            )}
            {entries.map((e, i) => (
              <article className={`entry-card cz-in cz-d${Math.min(i + 1, 6)}`} key={e.id}>
                <span className="date">{new Date(e.created_at).toLocaleDateString(undefined, { month: "long", day: "numeric" })}</span>
                <q>{e.body || e.title}</q>
                {/* A flagged entry used to carry a bare 😔. A risk signal never
                    shows without a route attached (design system §1). */}
                {(e.risk_level === "crisis" || e.risk_level === "elevated") && (
                  <Link href="/support" className="entry-support">
                    That day read as a heavy one — support is here, any time →
                  </Link>
                )}
              </article>
            ))}
          </div>

          <div className="rail">
            <div className="rail-card cz-in cz-d1">
              <span className="kicker">This month</span>
              <div className="rail-big"><b>{monthCount}</b><span>{monthCount === 1 ? "entry" : "entries"}</span></div>
              {whenLine && <p className="sub">{whenLine}</p>}
              <p className="sub">Every entry stays private by default.</p>
            </div>
            <div className="rail-card cz-in cz-d2">
              <span className="serif-h" style={{ fontSize: 18 }}>Prompts you can revisit</span>
              <div className="plist" style={{ marginTop: 8 }}>
                {REVISIT.map((p) => (
                  <button
                    key={p.prompt}
                    className="prompt-item"
                    onClick={() => applyPrompt(p)}
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
