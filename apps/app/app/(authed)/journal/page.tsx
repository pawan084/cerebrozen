"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Entry = {
  id: string;
  title: string;
  body: string;
  tags: string[];
  risk_level: string;
  created_at: string;
};

export default function Journal() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");
  const [busy, setBusy] = useState(false);
  const [showSupport, setShowSupport] = useState(false);

  useEffect(() => {
    void reload();
  }, []);

  async function reload() {
    try {
      setEntries(await api<Entry[]>("/journal"));
    } catch {}
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      const entry = await api<Entry>("/journal", {
        method: "POST",
        body: JSON.stringify({
          title,
          body,
          tags: tags.split(",").map((t) => t.trim()).filter(Boolean),
          symbol: "book",
        }),
      });
      // Safety contract: writing is never blocked — but a heavy entry
      // surfaces support alongside it.
      setShowSupport(["elevated", "crisis"].includes(entry.risk_level));
      setTitle("");
      setBody("");
      setTags("");
      await reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <p className="eyebrow">Private by default</p>
      <h1>Journal</h1>

      {showSupport && (
        <div className="crisis" role="alert">
          <strong>That sounded heavy — you deserve support right now.</strong>
          <br />
          In India: emergency services <strong>112</strong> · KIRAN mental-health helpline{" "}
          <strong>1800-599-0019</strong>. Elsewhere:{" "}
          <a href="https://findahelpline.com" target="_blank" rel="noreferrer">findahelpline.com</a>.
          Your entry was saved — writing is never blocked.
        </div>
      )}

      <form className="card" onSubmit={save} aria-label="New entry">
        <h2>New entry</h2>
        <label className="field">
          <span>Title</span>
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={120} />
        </label>
        <label className="field">
          <span>What's on your mind?</span>
          <textarea rows={5} value={body} onChange={(e) => setBody(e.target.value)} />
        </label>
        <label className="field">
          <span>Tags (comma-separated)</span>
          <input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="Work, Sleep" />
        </label>
        <button className="btn" disabled={busy || !title.trim()}>
          {busy ? "Saving…" : "Save entry"}
        </button>
      </form>

      <section className="card" aria-label="History">
        <h2>History</h2>
        {entries.length === 0 && <p className="sub">Nothing here yet — your entries collect below.</p>}
        {entries.map((e) => (
          <div className="entry" key={e.id}>
            <strong>{e.title}</strong>
            <div className="meta">{new Date(e.created_at).toLocaleString()}</div>
            {e.body && <p style={{ marginTop: 6 }}>{e.body}</p>}
            <div style={{ marginTop: 6 }}>
              {e.tags.map((t) => (
                <span className="tag" key={t}>{t}</span>
              ))}
            </div>
          </div>
        ))}
      </section>
    </>
  );
}
