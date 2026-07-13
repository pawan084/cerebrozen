"use client";

import { useEffect, useState } from "react";
import { api, clearToken, getToken, login, setToken } from "@/lib/api";
import { BrandMark, Icon } from "@/components/icons";

type Tab = "overview" | "analytics" | "users" | "content" | "prompts" | "nudges" | "safety" | "waitlist";

const TABS: { key: Tab; label: string }[] = [
  { key: "overview", label: "Overview" },
  { key: "analytics", label: "Analytics" },
  { key: "users", label: "Users" },
  { key: "content", label: "Content" },
  { key: "prompts", label: "Prompts" },
  { key: "nudges", label: "Nudges" },
  { key: "safety", label: "Safety" },
  { key: "waitlist", label: "Waitlist" },
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
          <BrandMark size={26} /> CereBro
        </div>
        {TABS.map((t) => {
          const I = Icon[t.key];
          return (
            <button
              key={t.key}
              className={`navitem ${tab === t.key ? "active" : ""}`}
              onClick={() => setTab(t.key)}
            >
              <I /> {t.label}
            </button>
          );
        })}
        <button
          className="navitem logout"
          onClick={() => {
            clearToken();
            setAuthed(false);
          }}
        >
          <Icon.signout /> Sign out
        </button>
      </aside>

      <main className="main">
        {tab === "overview" && <Overview />}
        {tab === "analytics" && <Analytics />}
        {tab === "users" && <Users />}
        {tab === "content" && <Content />}
        {tab === "prompts" && <PromptsTab />}
        {tab === "nudges" && <NudgesTab />}
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
  const [tick, setTick] = useState(0);
  useEffect(() => {
    // Ignore a stale in-flight response when deps change (e.g. a slow unfiltered
    // fetch resolving after a search) so the newest request always wins.
    let ignore = false;
    loader()
      .then((d) => { if (!ignore) { setData(d); setErr(""); } })
      .catch((e) => { if (!ignore) setErr(e.message === "unauthorized" ? "Session expired — reload to sign in." : e.message); });
    return () => { ignore = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, err, reload: () => setTick((t) => t + 1) };
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

function Analytics() {
  const { data, err } = useData<any>(() => api("/admin/metrics/overview"));
  const { data: onb } = useData<any>(() => api("/admin/metrics/funnel?days=30"));
  const pct = (r: number | null) => (r === null || r === undefined ? "n/a" : `${Math.round(r * 100)}%`);
  const onbMax = Math.max(1, ...(onb?.steps ?? []).map((s: any) => s.installs));
  const funnelSteps = data
    ? [
        { l: "Signed up", n: data.funnel.signups },
        { l: "First mood check-in", n: data.funnel.mood },
        { l: "First journal entry", n: data.funnel.journal },
        { l: "First sleep log", n: data.funnel.sleep },
        { l: "Premium", n: data.funnel.premium },
      ]
    : [];
  const funnelMax = Math.max(1, ...funnelSteps.map((s) => s.n));

  return (
    <>
      <h1 className="page-title serif">Analytics</h1>
      <div className="page-sub">
        First-party aggregates computed on our own Postgres — no third-party SDKs,
        no per-user browsing, no content read.
      </div>
      {err && <div className="empty">{err}</div>}
      {data && (
        <>
          <div className="stats">
            <div className="stat"><div className="n">{data.actives.dau}</div><div className="l">Active today</div></div>
            <div className="stat"><div className="n">{data.actives.wau}</div><div className="l">Active 7d</div></div>
            <div className="stat"><div className="n">{data.actives.mau}</div><div className="l">Active 30d</div></div>
            <div className="stat"><div className="n">{data.signups.d7}</div><div className="l">Signups 7d</div></div>
            <div className="stat"><div className="n">{data.signups.total}</div><div className="l">Accounts</div></div>
          </div>

          <div className="card" style={{ marginTop: 16, padding: 18 }}>
            <h3 className="serif" style={{ marginBottom: 10 }}>Retention (signup cohorts, last 35 days)</h3>
            <table>
              <thead><tr><th>Window</th><th>Cohort</th><th>Retained</th><th>Rate</th></tr></thead>
              <tbody>
                {(["d1", "d7", "d30"] as const).map((k) => (
                  <tr key={k}>
                    <td>{k.toUpperCase()}</td>
                    <td>{data.retention[k].cohort}</td>
                    <td>{data.retention[k].retained}</td>
                    <td>{pct(data.retention[k].rate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="card" style={{ marginTop: 16, padding: 18 }}>
            <h3 className="serif" style={{ marginBottom: 10 }}>Activation funnel (lifetime)</h3>
            {funnelSteps.map((s) => (
              <div key={s.l} style={{ margin: "8px 0" }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
                  <span>{s.l}</span><span className="mono">{s.n}</span>
                </div>
                <div style={{ height: 8, borderRadius: 99, background: "rgba(255,255,255,0.08)", marginTop: 4 }}>
                  <div style={{
                    height: "100%", borderRadius: 99, width: `${(s.n / funnelMax) * 100}%`,
                    background: "linear-gradient(90deg, var(--lav), var(--lav-2))",
                  }} />
                </div>
              </div>
            ))}
          </div>

          {onb && (
            <div className="card" style={{ marginTop: 16, padding: 18 }}>
              <h3 className="serif" style={{ marginBottom: 4 }}>Onboarding funnel (30 days)</h3>
              <div style={{ fontSize: 12, opacity: 0.65, marginBottom: 10 }}>
                Unique installs per step — anonymous events, never linked to accounts.
                Completed: <span className="mono">{onb.completed}</span> · Paywall views:{" "}
                <span className="mono">{onb.paywall_views}</span> · Paywall taps:{" "}
                <span className="mono">{onb.paywall_taps}</span>
              </div>
              {onb.steps.map((s: any) => (
                <div key={s.step} style={{ margin: "8px 0" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
                    <span>{s.step.replace(/_/g, " ")}</span><span className="mono">{s.installs}</span>
                  </div>
                  <div style={{ height: 8, borderRadius: 99, background: "rgba(255,255,255,0.08)", marginTop: 4 }}>
                    <div style={{
                      height: "100%", borderRadius: 99, width: `${(s.installs / onbMax) * 100}%`,
                      background: "linear-gradient(90deg, var(--lav), var(--lav-2))",
                    }} />
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="card" style={{ marginTop: 16, padding: 18 }}>
            <h3 className="serif" style={{ marginBottom: 10 }}>Engagement, trailing 7 days</h3>
            <table>
              <tbody>
                {Object.entries(data.engagement_7d).map(([k, v]) => (
                  <tr key={k}><td>{k.replace(/_/g, " ")}</td><td className="mono">{String(v)}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

function UserDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { data, err } = useData<any>(() => api(`/admin/users/${id}`), [id]);
  return (
    <div className="card" style={{ marginTop: 16, padding: 18 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h3 className="serif">Account details</h3>
        <button className="btn btn-ghost btn-sm" onClick={onClose}>Close</button>
      </div>
      <div className="page-sub" style={{ marginTop: 4 }}>
        Counts and account state only — journal, chat, and sleep contents never leave the user's space.
      </div>
      {err && <div className="empty">{err}</div>}
      {data && (
        <table style={{ marginTop: 8 }}>
          <tbody>
            <tr><td>Email</td><td className="mono">{data.user.email}</td></tr>
            <tr><td>Tier</td><td>{data.user.subscription_tier || "free"}</td></tr>
            <tr><td>Region / language</td><td>{data.user.region || "auto"} · {data.user.language}</td></tr>
            <tr><td>Joined</td><td>{fmtDate(data.user.created_at)}</td></tr>
            <tr><td>Last active</td><td>{data.last_active ? fmtDate(data.last_active) : "—"}</td></tr>
            <tr>
              <td>Activity</td>
              <td>
                {data.counts.moods} moods · {data.counts.journals} journals ·{" "}
                {data.counts.chat_messages} chats · {data.counts.sleep_logs} sleep logs
              </td>
            </tr>
            <tr>
              <td>Safety</td>
              <td>
                {data.counts.open_safety_events} open events ·{" "}
                {data.trusted_contact ? "trusted contact set" : "no trusted contact"}
              </td>
            </tr>
            <tr>
              <td>Consent</td>
              <td>
                {data.consent
                  ? Object.entries(data.consent).map(([k, v]) => (
                      <span key={k} className={`tag ${v ? "ok" : "muted"}`} style={{ marginRight: 6 }}>
                        {k.replace(/_/g, " ")}
                      </span>
                    ))
                  : "—"}
              </td>
            </tr>
            <tr><td>Pending nudges</td><td>{data.counts.pending_nudges}</td></tr>
          </tbody>
        </table>
      )}
    </div>
  );
}

function Users() {
  const [q, setQ] = useState("");
  const [limit, setLimit] = useState(50);
  const { data, err, reload } = useData<any[]>(
    () => api(`/admin/users?q=${encodeURIComponent(q)}&limit=${limit}`),
    [q, limit]
  );
  const [selected, setSelected] = useState<string | null>(null);
  const [nudgeUser, setNudgeUser] = useState<any | null>(null);

  async function toggle(u: any) {
    await api(`/admin/users/${u.id}/active?active=${!u.is_active}`, { method: "PATCH" });
    reload();
  }

  return (
    <>
      <h1 className="page-title serif">Users</h1>
      <div className="page-sub">{data?.length ?? 0} shown{q ? ` · matching "${q}"` : ""}</div>
      <div className="toolbar" style={{ marginBottom: 12 }}>
        <div className="search">
          <Icon.search />
          <input
            placeholder="Search by email or name…"
            value={q}
            onChange={(e) => { setLimit(50); setQ(e.target.value); }}
          />
        </div>
      </div>
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
                  <div className="row-actions">
                    <button className="btn btn-ghost btn-sm" onClick={() => setSelected(u.id === selected ? null : u.id)}>Details</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => setNudgeUser(u)}>Nudge</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => toggle(u)}>{u.is_active ? "Disable" : "Enable"}</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && <div className="empty">No matching users.</div>}
        {data && data.length >= limit && (
          <div style={{ padding: 12, textAlign: "center" }}>
            <button className="btn btn-ghost btn-sm" onClick={() => setLimit((l) => l + 50)}>Load more</button>
          </div>
        )}
      </div>
      {selected && <UserDetail id={selected} onClose={() => setSelected(null)} />}
      {nudgeUser && <NudgeUserModal user={nudgeUser} onClose={() => setNudgeUser(null)} />}
    </>
  );
}

function NudgeUserModal({ user, onClose }: { user: any; onClose: () => void }) {
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");
  async function send(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setMsg("");
    try {
      await api("/admin/nudges", { method: "POST", body: JSON.stringify({ title, body, user_id: user.id }) });
      setMsg("Queued ✓ — the scheduler delivers it.");
      setTitle(""); setBody("");
    } catch (e: any) { setMsg(e.message); } finally { setBusy(false); }
  }
  return (
    <div className="card" style={{ marginTop: 16, padding: 18 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h3 className="serif">Nudge <span className="mono">{user.email}</span></h3>
        <button className="btn btn-ghost btn-sm" onClick={onClose}>Close</button>
      </div>
      <form className="cform" onSubmit={send} style={{ marginTop: 8 }}>
        <div className="full"><label>Title</label><input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={160} /></div>
        <div className="full"><label>Body</label><input value={body} onChange={(e) => setBody(e.target.value)} required maxLength={500} /></div>
        <div className="full" style={{ display: "flex", gap: 12, alignItems: "center" }}>
          <button className="btn btn-primary" disabled={busy || !title.trim() || !body.trim()}>{busy ? "Queuing…" : "Queue nudge"}</button>
          {msg && <span className="page-sub" style={{ marginBottom: 0 }}>{msg}</span>}
        </div>
      </form>
    </div>
  );
}

const KINDS = ["sleep", "meditation", "breath", "soundscape", "program", "wind_down"];
const EMPTY_CONTENT = {
  title: "", subtitle: "", kind: "meditation", symbol: "sparkles",
  image_url: "", duration_min: 0, premium: false, published: true,
  narration_script: "", day_guides: [] as { title: string; body: string }[],
};

function Content() {
  const { data, err, reload } = useData<any[]>(() => api("/admin/content"));
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<any>(EMPTY_CONTENT);
  const [editId, setEditId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [narratingId, setNarratingId] = useState<string | null>(null);
  const [audioMsg, setAudioMsg] = useState("");

  function set(k: string, v: any) { setForm((f: any) => ({ ...f, [k]: v })); }
  function closeForm() { setForm(EMPTY_CONTENT); setEditId(null); setShowForm(false); }
  function startEdit(item: any) {
    setForm({
      title: item.title, subtitle: item.subtitle || "", kind: item.kind,
      symbol: item.symbol || "sparkles", image_url: item.image_url || "",
      duration_min: item.duration_min || 0, premium: item.premium, published: item.published,
      narration_script: item.narration_script || "",
      day_guides: (item.day_guides || []).map((g: any) => ({ title: g.title || "", body: g.body || "" })),
    });
    setEditId(item.id);
    setShowForm(true);
  }
  function setGuide(i: number, k: "title" | "body", v: string) {
    setForm((f: any) => ({
      ...f,
      day_guides: f.day_guides.map((g: any, idx: number) => (idx === i ? { ...g, [k]: v } : g)),
    }));
  }
  function addGuide() { setForm((f: any) => ({ ...f, day_guides: [...f.day_guides, { title: "", body: "" }] })); }
  function removeGuide(i: number) {
    setForm((f: any) => ({ ...f, day_guides: f.day_guides.filter((_: any, idx: number) => idx !== i) }));
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!form.title.trim()) return;
    setBusy(true);
    try {
      // No guide rows ⇒ explicit null (clears them server-side): NULL, not [],
      // marks a non-program row per the W15 contract.
      const body = JSON.stringify({
        ...form,
        duration_min: Number(form.duration_min) || 0,
        day_guides: form.day_guides.length ? form.day_guides : null,
      });
      if (editId) await api(`/admin/content/${editId}`, { method: "PATCH", body });
      else await api("/admin/content", { method: "POST", body });
      closeForm();
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
  async function narrate(id: string) {
    setNarratingId(id);
    setAudioMsg("");
    try {
      await api(`/admin/content/${id}/narrate`, { method: "POST" });
      reload();
    } catch (e: any) {
      setAudioMsg(
        String(e?.message || e).includes("503")
          ? "Text-to-speech isn't configured (no ElevenLabs key)."
          : "Narration failed — try again."
      );
    } finally {
      setNarratingId(null);
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title serif">Content library</h1>
          <div className="page-sub" style={{ marginBottom: 0 }}>{data?.length ?? 0} items</div>
        </div>
        <button className="btn btn-primary" onClick={() => (showForm ? closeForm() : setShowForm(true))}>
          {showForm ? "Close" : "+ New item"}
        </button>
      </div>

      {showForm && (
        <form className="card cform" onSubmit={save}>
          <div className="full" style={{ marginBottom: -2 }}>
            <strong className="serif" style={{ fontSize: 16 }}>{editId ? "Edit item" : "New item"}</strong>
          </div>
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
          <div className="full">
            <label>Narration script</label>
            <textarea
              rows={6}
              value={form.narration_script}
              onChange={(e) => set("narration_script", e.target.value)}
              placeholder="Read aloud by the narrator voice — keep it calm and non-clinical. Leave empty for ambient-only items."
              style={{ width: "100%", resize: "vertical" }}
            />
          </div>
          <div className="full">
            <label>Day guides (programs)</label>
            <div style={{ fontSize: 12, opacity: 0.65, marginBottom: 8 }}>
              One guide per program day — day N of an enrollment reads guide N. Leave empty for non-programs.
            </div>
            {form.day_guides.map((g: any, i: number) => (
              <div key={i} className="card" style={{ padding: 12, marginBottom: 10 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                  <span className="tag muted">Day {i + 1}</span>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => removeGuide(i)}>Remove</button>
                </div>
                <label>Title</label>
                <input
                  type="text"
                  value={g.title}
                  onChange={(e) => setGuide(i, "title", e.target.value)}
                  placeholder="Day title"
                  required
                  maxLength={160}
                />
                <label style={{ marginTop: 8, display: "block" }}>Body</label>
                <textarea
                  rows={3}
                  value={g.body}
                  onChange={(e) => setGuide(i, "body", e.target.value)}
                  placeholder="What this day asks of the user — calm, one focus."
                  style={{ width: "100%", resize: "vertical" }}
                />
              </div>
            ))}
            <button type="button" className="btn btn-ghost btn-sm" onClick={addGuide}>+ Add day</button>
          </div>
          <label className="check">
            <input type="checkbox" checked={form.premium} onChange={(e) => set("premium", e.target.checked)} /> Premium
          </label>
          <label className="check">
            <input type="checkbox" checked={form.published} onChange={(e) => set("published", e.target.checked)} /> Published
          </label>
          <div className="full">
            <button className="btn btn-primary" disabled={busy}>{busy ? (editId ? "Saving…" : "Creating…") : (editId ? "Save changes" : "Create item")}</button>
          </div>
        </form>
      )}

      {err && <div className="empty">{err}</div>}
      {audioMsg && <div className="empty">{audioMsg}</div>}
      <div className="card">
        <table>
          <thead>
            <tr><th>Title</th><th>Kind</th><th>Duration</th><th>Tier</th><th>Audio</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            {(data || []).map((c) => (
              <tr key={c.id}>
                <td>{c.title}</td>
                <td><span className="tag muted">{c.kind}</span></td>
                <td>{c.duration_min ? `${c.duration_min} min` : "—"}</td>
                <td>{c.premium ? <span className="tag elevated">premium</span> : <span className="tag ok">free</span>}</td>
                <td>{c.audio_url ? <span className="tag ok">narrated</span> : c.narration_script ? <span className="tag muted">script only</span> : "—"}</td>
                <td>{c.published ? "Published" : "Draft"}</td>
                <td>
                  <div className="row-actions">
                    <button className="btn btn-ghost btn-sm" onClick={() => startEdit(c)}>Edit</button>
                    {c.narration_script && (
                      <button
                        className="btn btn-ghost btn-sm"
                        disabled={narratingId !== null}
                        onClick={() => narrate(c.id)}
                      >
                        {narratingId === c.id ? "Generating… ~30s" : c.audio_url ? "Regenerate audio" : "Generate audio"}
                      </button>
                    )}
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

// Versioned LLM prompt registry (backend services/prompts.py): every save is a
// new immutable version, rollback = activate an old one, revert = the code
// default serves again. Prompt edits reach production without a deploy.
function PromptsTab() {
  const { data, err, reload } = useData<any[]>(() => api("/admin/prompts"));
  const [open, setOpen] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);

  function startEdit(p: any) {
    setOpen(p.name);
    setDraft(p.template);
    setNotes("");
  }

  async function save(name: string) {
    if (!draft.trim()) return;
    setBusy(true);
    try {
      await api(`/admin/prompts/${name}`, { method: "POST", body: JSON.stringify({ template: draft, notes }) });
      setOpen(null);
      reload();
    } finally {
      setBusy(false);
    }
  }
  async function activate(name: string, version: number) {
    await api(`/admin/prompts/${name}/versions/${version}/activate`, { method: "POST" });
    reload();
  }
  async function revert(name: string) {
    await api(`/admin/prompts/${name}/revert`, { method: "POST" });
    setOpen(null);
    reload();
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title serif">Prompt registry</h1>
          <div className="page-sub" style={{ marginBottom: 0 }}>
            Versioned LLM prompts — edits go live without a deploy; the code default is always the safe floor.
          </div>
        </div>
      </div>
      {err && <div className="empty">{err}</div>}
      {(data || []).map((p) => (
        <div className="card" key={p.name} style={{ marginBottom: 14 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
            <div>
              <strong className="serif" style={{ fontSize: 16 }}>{p.name}</strong>{" "}
              {p.source === "registry"
                ? <span className="tag elevated">v{p.active_version} active</span>
                : <span className="tag ok">code default</span>}
            </div>
            <div className="row-actions">
              {open === p.name
                ? <button className="btn btn-ghost btn-sm" onClick={() => setOpen(null)}>Close</button>
                : <button className="btn btn-ghost btn-sm" onClick={() => startEdit(p)}>Edit</button>}
              {p.source === "registry" && (
                <button className="btn btn-ghost btn-sm" onClick={() => revert(p.name)}>Revert to code default</button>
              )}
            </div>
          </div>
          <p style={{ whiteSpace: "pre-wrap", opacity: 0.75, margin: "10px 0 0", fontSize: 13 }}>
            {open === p.name ? "" : p.template.length > 400 ? `${p.template.slice(0, 400)}…` : p.template}
          </p>
          {open === p.name && (
            <div style={{ marginTop: 10 }}>
              <label>Template</label>
              <textarea
                aria-label={`Template for ${p.name}`}
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                rows={10}
                style={{ width: "100%", fontFamily: "monospace", fontSize: 12.5 }}
              />
              <label>Notes (what changed)</label>
              <input type="text" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={255} />
              <div style={{ marginTop: 10 }}>
                <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => save(p.name)}>
                  {busy ? "Saving…" : "Save as new version"}
                </button>
              </div>
            </div>
          )}
          {p.versions.length > 0 && (
            <table style={{ marginTop: 12 }}>
              <thead><tr><th>Version</th><th>Notes</th><th>Created</th><th>Status</th><th></th></tr></thead>
              <tbody>
                {p.versions.map((v: any) => (
                  <tr key={v.version}>
                    <td>v{v.version}</td>
                    <td>{v.notes || "—"}</td>
                    <td>{fmtDate(v.created_at)}</td>
                    <td>{v.active ? <span className="tag elevated">active</span> : "—"}</td>
                    <td>
                      {!v.active && (
                        <button className="btn btn-ghost btn-sm" onClick={() => activate(p.name, v.version)}>
                          Activate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ))}
      {data && data.length === 0 && <div className="empty">No prompts registered.</div>}
    </>
  );
}

function NudgesTab() {
  const { data, err, reload } = useData<any[]>(() => api("/admin/nudges"));
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  async function send(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg("");
    try {
      const res = await api<{ created: number }>("/admin/nudges", {
        method: "POST",
        body: JSON.stringify({ title, body }),
      });
      setMsg(`Queued for ${res.created} user${res.created === 1 ? "" : "s"} — the scheduler delivers it.`);
      setTitle("");
      setBody("");
      reload();
    } catch (e: any) {
      setMsg(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1 className="page-title serif">Nudges</h1>
      <div className="page-sub">
        Author a one-off announcement for every active user; delivery runs through the
        existing scheduler (honest sent/skipped/failed outcomes).
      </div>
      <form className="card cform" onSubmit={send}>
        <div className="full">
          <label>Title</label>
          <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={160} />
        </div>
        <div className="full">
          <label>Body</label>
          <input type="text" value={body} onChange={(e) => setBody(e.target.value)} required maxLength={500} />
        </div>
        <div className="full" style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button className="btn btn-primary" disabled={busy || !title.trim() || !body.trim()}>
            {busy ? "Queuing…" : "Queue for all active users"}
          </button>
          {msg && <span className="page-sub" style={{ marginBottom: 0 }}>{msg}</span>}
        </div>
      </form>
      {err && <div className="empty">{err}</div>}
      <div className="card">
        <table>
          <thead>
            <tr><th>Title</th><th>User</th><th>Kind</th><th>Status</th><th>Scheduled</th></tr>
          </thead>
          <tbody>
            {(data || []).map((n) => (
              <tr key={n.id}>
                <td>{n.title}</td>
                <td className="mono">{n.email}</td>
                <td><span className="tag muted">{n.kind}</span></td>
                <td><span className={`tag ${n.status === "sent" ? "ok" : n.status === "failed" ? "crisis" : "muted"}`}>{n.status}</span></td>
                <td>{fmtDate(n.scheduled_for)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && <div className="empty">No nudges yet.</div>}
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
