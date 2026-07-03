"use client";

import { useCallback, useEffect, useState } from "react";
import { api, clearToken, getToken, login, setToken } from "@/lib/api";

type Tab = "overview" | "users" | "content" | "safety" | "waitlist";

const TABS: { key: Tab; label: string; icon: string }[] = [
  { key: "overview", label: "Overview", icon: "▣" },
  { key: "users", label: "Users", icon: "☺" },
  { key: "content", label: "Content", icon: "♪" },
  { key: "safety", label: "Safety", icon: "♥" },
  { key: "waitlist", label: "Waitlist", icon: "✉" },
];

function fmtDate(s: string) {
  try {
    return new Date(s).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
  } catch {
    return s;
  }
}

export default function AdminPage() {
  const [authed, setAuthed] = useState(false);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>("overview");

  useEffect(() => {
    setAuthed(!!getToken());
    setReady(true);
  }, []);

  if (!ready) return null;
  if (!authed) return <Login onAuthed={() => setAuthed(true)} />;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="dot" /> CereBro
        </div>
        {TABS.map((t) => (
          <button
            key={t.key}
            className={`navitem ${tab === t.key ? "active" : ""}`}
            onClick={() => setTab(t.key)}
          >
            <em style={{ width: 18, display: "inline-block" }}>{t.icon}</em>
            {t.label}
          </button>
        ))}
        <button
          className="navitem logout"
          onClick={() => {
            clearToken();
            setAuthed(false);
          }}
        >
          <em style={{ width: 18, display: "inline-block" }}>⎋</em>
          Sign out
        </button>
      </aside>

      <main className="main">
        {tab === "overview" && <Overview />}
        {tab === "users" && <Users />}
        {tab === "content" && <Content />}
        {tab === "safety" && <Safety />}
        {tab === "waitlist" && <WaitlistTab />}
      </main>
    </div>
  );
}

// Dev-only convenience: prefill + hint for the locally seeded admin account.
// NODE_ENV is inlined at build time, so none of this ships in production.
const IS_DEV = process.env.NODE_ENV !== "production";

function Login({ onAuthed }: { onAuthed: () => void }) {
  const [email, setEmail] = useState(IS_DEV ? "admin@cerebro.app" : "");
  const [password, setPassword] = useState(IS_DEV ? "admin12345" : "");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr("");
    try {
      const token = await login(email, password);
      setToken(token);
      onAuthed();
    } catch {
      setErr("Invalid email or password");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login" onSubmit={submit}>
        <div className="orb" />
        <h1>CereBro Admin</h1>
        <p>Sign in to manage the platform</p>
        <div className="field">
          <label>Email</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="field">
          <label>Password</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>
        <button className="btn btn-primary" style={{ width: "100%" }} disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
        <div className="err">{err}</div>
        {IS_DEV && <div className="hint">Seeded admin: admin@cerebro.app / admin12345</div>}
      </form>
    </div>
  );
}

function useData<T>(loader: () => Promise<T>, deps: any[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [err, setErr] = useState("");
  const run = useCallback(() => {
    loader()
      .then(setData)
      .catch((e) => setErr(e.message === "unauthorized" ? "Session expired — reload to sign in." : e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => {
    run();
  }, [run]);
  return { data, err, reload: run };
}

function Overview() {
  const { data, err } = useData<Record<string, number>>(() => api("/admin/stats"));
  const [nudgeMsg, setNudgeMsg] = useState("");
  const [nudgeBusy, setNudgeBusy] = useState(false);
  const cards = [
    { l: "Users", k: "users" },
    { l: "Mood logs", k: "mood_logs" },
    { l: "Journal entries", k: "journal_entries" },
    { l: "Content items", k: "content_items" },
    { l: "Open safety", k: "open_safety_events" },
  ];

  async function dispatchNudges() {
    setNudgeBusy(true);
    setNudgeMsg("");
    try {
      const res = await api<{ sent: number }>("/admin/nudges/dispatch", { method: "POST" });
      setNudgeMsg(`${res.sent} dispatched`);
    } catch (e: any) {
      setNudgeMsg(e.message === "unauthorized" ? "Session expired — reload to sign in." : e.message);
    } finally {
      setNudgeBusy(false);
    }
  }

  return (
    <>
      <h1 className="page-title serif">Overview</h1>
      <div className="page-sub">Platform at a glance</div>
      {err && <div className="empty">{err}</div>}
      <div className="stats">
        {cards.map((c) => (
          <div className="stat" key={c.k}>
            <div className="n">{data ? data[c.k] ?? 0 : "—"}</div>
            <div className="l">{c.l}</div>
          </div>
        ))}
      </div>
      <div className="toolbar" style={{ marginTop: 20, alignItems: "center", gap: 12, display: "flex" }}>
        <button className="btn btn-primary" onClick={dispatchNudges} disabled={nudgeBusy}>
          {nudgeBusy ? "Dispatching…" : "Dispatch due nudges"}
        </button>
        {nudgeMsg && <span className="page-sub" style={{ marginBottom: 0 }}>{nudgeMsg}</span>}
      </div>
    </>
  );
}

function Users() {
  const { data, err, reload } = useData<any[]>(() => api("/admin/users"));
  async function toggle(u: any) {
    await api(`/admin/users/${u.id}/active?active=${!u.is_active}`, { method: "PATCH" });
    reload();
  }
  return (
    <>
      <h1 className="page-title serif">Users</h1>
      <div className="page-sub">{data?.length ?? 0} accounts</div>
      {err && <div className="empty">{err}</div>}
      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Email</th><th>Name</th><th>Companion</th><th>Role</th><th>Status</th><th>Joined</th><th></th>
            </tr>
          </thead>
          <tbody>
            {(data || []).map((u) => (
              <tr key={u.id}>
                <td className="mono">{u.email}</td>
                <td>{u.name || "—"}</td>
                <td>{u.companion}</td>
                <td>{u.is_admin ? <span className="tag elevated">admin</span> : <span className="tag muted">user</span>}</td>
                <td>{u.is_active ? <span className="tag ok">active</span> : <span className="tag crisis">disabled</span>}</td>
                <td>{fmtDate(u.created_at)}</td>
                <td>
                  <button className="btn btn-ghost btn-sm" onClick={() => toggle(u)}>
                    {u.is_active ? "Disable" : "Enable"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && <div className="empty">No users yet.</div>}
      </div>
    </>
  );
}

const KINDS = ["sleep", "meditation", "breath", "soundscape", "program", "wind_down"];
const EMPTY_CONTENT = {
  title: "", subtitle: "", kind: "meditation", symbol: "sparkles",
  image_url: "", duration_min: 0, premium: false, published: true,
};

function Content() {
  const { data, err, reload } = useData<any[]>(() => api("/admin/content"));
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<any>(EMPTY_CONTENT);
  const [busy, setBusy] = useState(false);

  function set(k: string, v: any) { setForm((f: any) => ({ ...f, [k]: v })); }

  async function create(e: React.FormEvent) {
    e.preventDefault();
    if (!form.title.trim()) return;
    setBusy(true);
    try {
      await api("/admin/content", {
        method: "POST",
        body: JSON.stringify({ ...form, duration_min: Number(form.duration_min) || 0 }),
      });
      setForm(EMPTY_CONTENT);
      setShowForm(false);
      reload();
    } finally {
      setBusy(false);
    }
  }
  async function patch(id: string, body: any) {
    await api(`/admin/content/${id}`, { method: "PATCH", body: JSON.stringify(body) });
    reload();
  }
  async function remove(id: string) {
    await api(`/admin/content/${id}`, { method: "DELETE" });
    reload();
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title serif">Content library</h1>
          <div className="page-sub" style={{ marginBottom: 0 }}>{data?.length ?? 0} items</div>
        </div>
        <button className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? "Close" : "+ New item"}
        </button>
      </div>

      {showForm && (
        <form className="card cform" onSubmit={create}>
          <div className="full">
            <label>Title</label>
            <input type="text" value={form.title} onChange={(e) => set("title", e.target.value)} required />
          </div>
          <div className="full">
            <label>Subtitle</label>
            <input type="text" value={form.subtitle} onChange={(e) => set("subtitle", e.target.value)} />
          </div>
          <div>
            <label>Kind</label>
            <select value={form.kind} onChange={(e) => set("kind", e.target.value)}>
              {KINDS.map((k) => <option key={k} value={k}>{k}</option>)}
            </select>
          </div>
          <div>
            <label>Duration (min)</label>
            <input type="number" min={0} value={form.duration_min} onChange={(e) => set("duration_min", e.target.value)} />
          </div>
          <div className="full">
            <label>Image URL</label>
            <input type="text" value={form.image_url} onChange={(e) => set("image_url", e.target.value)} placeholder="https://…" />
          </div>
          <label className="check">
            <input type="checkbox" checked={form.premium} onChange={(e) => set("premium", e.target.checked)} /> Premium
          </label>
          <label className="check">
            <input type="checkbox" checked={form.published} onChange={(e) => set("published", e.target.checked)} /> Published
          </label>
          <div className="full">
            <button className="btn btn-primary" disabled={busy}>{busy ? "Creating…" : "Create item"}</button>
          </div>
        </form>
      )}

      {err && <div className="empty">{err}</div>}
      <div className="card">
        <table>
          <thead>
            <tr><th>Title</th><th>Kind</th><th>Duration</th><th>Tier</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            {(data || []).map((c) => (
              <tr key={c.id}>
                <td>{c.title}</td>
                <td><span className="tag muted">{c.kind}</span></td>
                <td>{c.duration_min ? `${c.duration_min} min` : "—"}</td>
                <td>{c.premium ? <span className="tag elevated">premium</span> : <span className="tag ok">free</span>}</td>
                <td>{c.published ? "Published" : "Draft"}</td>
                <td>
                  <div className="row-actions">
                    <button className="btn btn-ghost btn-sm" onClick={() => patch(c.id, { published: !c.published })}>
                      {c.published ? "Unpublish" : "Publish"}
                    </button>
                    <button className="btn btn-ghost btn-sm" onClick={() => patch(c.id, { premium: !c.premium })}>
                      {c.premium ? "Make free" : "Make premium"}
                    </button>
                    <button className="btn btn-danger btn-sm" onClick={() => remove(c.id)}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && <div className="empty">No content yet.</div>}
      </div>
    </>
  );
}

function Safety() {
  const [showResolved, setShowResolved] = useState(false);
  const { data, err, reload } = useData<any[]>(
    () => api(`/admin/safety?resolved=${showResolved}`),
    [showResolved]
  );
  async function resolve(id: string) {
    await api(`/admin/safety/${id}/resolve`, { method: "PATCH" });
    reload();
  }
  return (
    <>
      <h1 className="page-title serif">Safety review</h1>
      <div className="page-sub">Flagged signals from journal &amp; chat — wellness support, never a clinical gate.</div>
      <div className="toolbar">
        <select value={String(showResolved)} onChange={(e) => setShowResolved(e.target.value === "true")}>
          <option value="false">Open</option>
          <option value="true">Resolved</option>
        </select>
      </div>
      {err && <div className="empty">{err}</div>}
      <div className="card">
        <table>
          <thead>
            <tr><th>Source</th><th>Risk</th><th>Reason</th><th>Excerpt</th><th>When</th><th></th></tr>
          </thead>
          <tbody>
            {(data || []).map((s) => (
              <tr key={s.id}>
                <td>{s.source}</td>
                <td><span className={`tag ${s.risk_level === "crisis" ? "crisis" : "elevated"}`}>{s.risk_level}</span></td>
                <td>{s.reason || "—"}</td>
                <td style={{ maxWidth: 300, color: "var(--muted)" }}>{s.excerpt}</td>
                <td>{fmtDate(s.created_at)}</td>
                <td>
                  {!s.resolved && (
                    <button className="btn btn-ghost btn-sm" onClick={() => resolve(s.id)}>Resolve</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && <div className="empty">Nothing here. 🌙</div>}
      </div>
    </>
  );
}

function WaitlistTab() {
  const { data, err } = useData<any[]>(() => api("/admin/waitlist"));
  return (
    <>
      <h1 className="page-title serif">Waitlist</h1>
      <div className="page-sub">{data?.length ?? 0} signups from the landing page</div>
      {err && <div className="empty">{err}</div>}
      <div className="card">
        <table>
          <thead><tr><th>Email</th><th>Source</th></tr></thead>
          <tbody>
            {(data || []).map((w, i) => (
              <tr key={i}><td className="mono">{w.email}</td><td><span className="tag muted">{w.source}</span></td></tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && <div className="empty">No signups yet.</div>}
      </div>
    </>
  );
}
