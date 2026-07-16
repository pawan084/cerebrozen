"use client";

/* CereBroZen admin: one page, role-gated tabs (the ref/Zen admin pattern).
   org_admin  → Overview · People · Invite
   internal_admin → Tenants · Demo requests  */

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { api, apiJson, engineApi, engineJson, getTokens, login, logout } from "@/lib/api";

type Me = { id: string; email: string; name: string; role: string; org_id: string | null; org_name: string | null };
type Org = { id: string; name: string; slug: string; seats_total: number; seats_used: number; regulated_mode: boolean; crisis_region: string; is_active: boolean };
type Person = { id: string; email: string; name: string; role: string; is_active: boolean };
type Metric = { value: number | null; suppressed: boolean };
type Analytics = {
  window_days: number; cohort_floor: number;
  seats: { total: number; active_members: number };
  metrics: Record<string, Metric>;
};
type Demo = { id: string; name: string; email: string; company: string; size: string; message: string; status: string; created_at: string };

/* ── shared data-loading: skeleton while loading, retry on error ── */
function useLoad<T>(loader: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const ref = useRef(loader);
  ref.current = loader;
  const reload = useCallback(() => {
    setLoading(true); setError("");
    ref.current().then(setData).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);
  useEffect(() => { reload(); }, [reload]);
  return { data, error, loading, reload };
}
function Skeleton({ rows = 3 }: { rows?: number }) {
  return <div className="skeleton">{Array.from({ length: rows }).map((_, i) => <div key={i} className="skeleton-row" />)}</div>;
}
function Failed({ msg, onRetry }: { msg: string; onRetry?: () => void }) {
  return (
    <div className="card"><div className="failed">
      <p className="error">{msg}</p>
      {onRetry && <button className="ghost" onClick={onRetry}>Retry</button>}
    </div></div>
  );
}

function Login({ onDone }: { onDone: () => void }) {
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    try {
      await login(String(data.get("email")), String(data.get("password")));
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "login failed");
    }
  }
  return (
    <div className="login-wrap card">
      <h2>Sign in</h2>
      <form className="stack" onSubmit={submit}>
        <label>Email<input name="email" type="email" required autoComplete="username" /></label>
        <label>Password<input name="password" type="password" required autoComplete="current-password" /></label>
        <button className="primary">Sign in</button>
        {error && <p className="error">{error}</p>}
      </form>
    </div>
  );
}

function OrgOverview() {
  const { data: org, error, loading, reload } = useLoad<Org>(() => apiJson<Org>("/orgs/me"));
  if (loading) return <div className="card"><Skeleton rows={2} /></div>;
  if (error) return <Failed msg={error} onRetry={reload} />;
  if (!org) return null;
  return (
    <div className="card">
      <h2>{org.name}</h2>
      <div className="stats">
        <div className="stat"><b>{org.seats_used} / {org.seats_total}</b><span>seats used</span></div>
        <div className="stat"><b>{org.regulated_mode ? "ON" : "OFF"}</b><span>regulated mode</span></div>
        <div className="stat"><b>{org.crisis_region}</b><span>crisis region</span></div>
        <div className="stat"><b>{org.is_active ? "active" : "inactive"}</b><span>status</span></div>
      </div>
      <p className="hint" style={{ marginTop: 12 }}>
        Analytics land in Phase 2 — aggregates only, with cohort floors. This portal never shows coaching content.
      </p>
    </div>
  );
}

const METRIC_LABELS: Record<string, string> = {
  active_coaching_users: "active coaching users",
  sessions_started: "sessions started",
  sessions_completed: "sessions completed",
  session_completion_rate: "session completion",
  actions_saved: "commitments made",
  actions_completed: "commitments kept",
  action_completion_rate: "follow-through",
};

function OrgAnalytics() {
  const { data, error, loading, reload } = useLoad<Analytics>(() => apiJson<Analytics>("/orgs/me/analytics"));
  if (loading) return <div className="card"><Skeleton rows={3} /></div>;
  if (error) return <Failed msg={error} onRetry={reload} />;
  if (!data) return null;
  const anySuppressed = Object.values(data.metrics).some((m) => m.suppressed);
  const fmt = (name: string, m: Metric) =>
    m.suppressed ? "—" : name.includes("rate") ? `${Math.round((m.value ?? 0) * 100)}%` : String(m.value);
  return (
    <>
      <div className="card">
        <h2>Last {data.window_days} days</h2>
        <div className="stats">
          {Object.entries(data.metrics).map(([name, m]) => (
            <div className="stat" key={name}>
              <b>{fmt(name, m)}</b>
              <span>{METRIC_LABELS[name] ?? name}</span>
            </div>
          ))}
        </div>
        {anySuppressed && (
          <p className="hint" style={{ marginTop: 14 }}>
            &ldquo;—&rdquo; means fewer than {data.cohort_floor} people contributed to that
            metric, so it is suppressed — aggregates never describe groups small enough
            to identify anyone. It fills in as adoption grows.
          </p>
        )}
        <p className="hint" style={{ marginTop: 8 }}>
          Counts and trends only — no transcripts, no individual records, ever.
        </p>
      </div>
      <div className="card">
        <h2>Seats</h2>
        <div className="stats">
          <div className="stat"><b>{data.seats.active_members} / {data.seats.total}</b><span>active members</span></div>
        </div>
      </div>
    </>
  );
}

function People() {
  const { data: people, error, loading, reload } = useLoad<Person[]>(() => apiJson<Person[]>("/orgs/me/people"));
  if (loading) return <div className="card"><Skeleton /></div>;
  if (error) return <Failed msg={error} onRetry={reload} />;
  return (
    <div className="card">
      <h2>People</h2>
      <table>
        <thead><tr><th>Email</th><th>Name</th><th>Role</th><th>Status</th></tr></thead>
        <tbody>
          {(people ?? []).map((p) => (
            <tr key={p.id}>
              <td>{p.email}</td><td>{p.name}</td><td>{p.role}</td>
              <td><span className={`pill ${p.is_active ? "ok" : "off"}`}>{p.is_active ? "active" : "disabled"}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
      {people?.length === 0 && <p className="empty-state">No members yet — invite someone from the Invite tab.</p>}
    </div>
  );
}

function Invite() {
  const [token, setToken] = useState("");
  const [emailed, setEmailed] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setError(""); setToken("");
    try {
      const out = await apiJson<{ invite_link: string; emailed: boolean }>("/orgs/me/invitations", {
        method: "POST",
        body: JSON.stringify({ email: data.get("email"), role: data.get("role") }),
      });
      setToken(out.invite_link);
      setEmailed(out.emailed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
    }
  }
  return (
    <div className="card">
      <h2>Invite someone</h2>
      <form className="stack" onSubmit={submit}>
        <label>Work email<input name="email" type="email" required /></label>
        <label>Role
          <select name="role" defaultValue="user">
            <option value="user">user</option>
            <option value="org_admin">org_admin</option>
          </select>
        </label>
        <button className="primary">Create invitation</button>
        {error && <p className="error">{error}</p>}
      </form>
      {token && (
        <>
          <p className="hint" style={{ marginTop: 12 }}>
            {emailed ? "Invitation emailed — here's the link too, in case it lands in spam:" : "Email isn't configured (SMTP env vars) — share this link manually; shown once, the seat is held until it expires:"}
          </p>
          <p className="token-reveal">{token}</p>
        </>
      )}
    </div>
  );
}

function Tenants() {
  const [orgs, setOrgs] = useState<Org[] | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    apiJson<Org[]>("/orgs").then(setOrgs).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    setError("");
    try {
      await apiJson("/orgs", {
        method: "POST",
        body: JSON.stringify({
          name: data.get("name"),
          slug: data.get("slug"),
          seats_total: Number(data.get("seats_total") || 50),
        }),
      });
      form.reset();
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
    }
  }
  async function toggle(org: Org) {
    const msg = org.is_active
      ? `Deactivate ${org.name}? Its members lose access until it's reactivated.`
      : `Activate ${org.name}?`;
    if (!window.confirm(msg)) return;
    await api(`/orgs/${org.id}`, { method: "PATCH", body: JSON.stringify({ is_active: !org.is_active }) });
    load();
  }
  return (
    <>
      <div className="card">
        <h2>Tenants</h2>
        {orgs === null ? (
          error ? <div className="failed"><p className="error">{error}</p><button className="ghost" onClick={load}>Retry</button></div> : <Skeleton />
        ) : (
          <>
            <table>
              <thead><tr><th>Name</th><th>Slug</th><th>Seats</th><th>Regulated</th><th>Status</th><th /></tr></thead>
              <tbody>
                {orgs.map((o) => (
                  <tr key={o.id}>
                    <td>{o.name}</td><td>{o.slug}</td><td>{o.seats_used} / {o.seats_total}</td>
                    <td><span className={`pill ${o.regulated_mode ? "ok" : "off"}`}>{o.regulated_mode ? "on" : "off"}</span></td>
                    <td><span className={`pill ${o.is_active ? "ok" : "off"}`}>{o.is_active ? "active" : "inactive"}</span></td>
                    <td><button className="ghost" onClick={() => toggle(o)}>{o.is_active ? "deactivate" : "activate"}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {orgs.length === 0 && <p className="empty-state">No tenants yet — create one below.</p>}
          </>
        )}
      </div>
      <div className="card">
        <h2>New tenant</h2>
        <form className="stack" onSubmit={create}>
          <label>Name<input name="name" required minLength={2} /></label>
          <label>Slug<input name="slug" required pattern="[a-z0-9-]+" /></label>
          <label>Seats<input name="seats_total" type="number" defaultValue={50} min={1} /></label>
          <button className="primary">Create tenant</button>
          {error && <p className="error">{error}</p>}
        </form>
        <p className="hint" style={{ marginTop: 10 }}>New tenants start with regulated mode ON — turning it off is a contract-level decision (SECURITY.md).</p>
      </div>
      <InviteFirstAdmin orgs={orgs ?? []} />
    </>
  );
}

function InviteFirstAdmin({ orgs }: { orgs: Org[] }) {
  const [token, setToken] = useState("");
  const [emailed, setEmailed] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setError(""); setToken("");
    try {
      const out = await apiJson<{ invite_link: string; emailed: boolean }>(
        `/orgs/${data.get("org")}/invitations`,
        { method: "POST", body: JSON.stringify({ email: data.get("email"), role: "org_admin" }) },
      );
      setToken(out.invite_link);
      setEmailed(out.emailed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
    }
  }
  return (
    <div className="card">
      <h2>First org admin</h2>
      <p className="hint">A new tenant is a locked room until its first org admin accepts an invitation.</p>
      <form className="stack" onSubmit={submit} style={{ marginTop: 10 }}>
        <label>Tenant
          <select name="org" required>
            {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
          </select>
        </label>
        <label>Admin email<input name="email" type="email" required /></label>
        <button className="primary">Create admin invitation</button>
        {error && <p className="error">{error}</p>}
      </form>
      {token && (
        <>
          <p className="hint" style={{ marginTop: 12 }}>
            {emailed ? "Invitation emailed — here's the link too:" : "Email isn't configured — share this link manually; they set a name + password and land in the HR view:"}
          </p>
          <p className="token-reveal">{token}</p>
        </>
      )}
    </div>
  );
}

function Demos() {
  const [rows, setRows] = useState<Demo[] | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    apiJson<Demo[]>("/admin/demo-requests").then(setRows).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);
  async function setStatus(id: string, status: string) {
    await api(`/admin/demo-requests/${id}`, { method: "PATCH", body: JSON.stringify({ status }) });
    load();
  }
  if (error) return <Failed msg={error} onRetry={load} />;
  if (rows === null) return <div className="card"><Skeleton /></div>;
  return (
    <div className="card">
      <h2>Demo requests</h2>
      <table>
        <thead><tr><th>Company</th><th>Contact</th><th>Size</th><th>Message</th><th>Status</th></tr></thead>
        <tbody>
          {(rows ?? []).map((d) => (
            <tr key={d.id}>
              <td>{d.company}</td>
              <td>{d.name} · {d.email}</td>
              <td>{d.size}</td>
              <td style={{ maxWidth: 280 }}>{d.message}</td>
              <td>
                <select value={d.status} onChange={(e) => setStatus(d.id, e.target.value)}>
                  <option>new</option><option>contacted</option><option>closed</option>
                </select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <p className="empty-state">No demo requests yet.</p>}
    </div>
  );
}

type AgentRow = { stage: string; model: string; enabled: boolean; size: number };
type AgentsResp = { agents: AgentRow[] };
type WbAgent = { stage: string; sheet: string; enabled: boolean; always_on: boolean; model: string; size: number; prompt: string };
type Validation = { issue_count?: number; ok?: boolean; issues?: unknown[] };
type WbResp = { source: string; editable: boolean; count: number; agents: WbAgent[]; version?: string; degraded?: boolean; degraded_reason?: string; validation?: Validation };

// Prompt workbook — VIEW + EDIT. The engine owns the workbook (validate-on-save,
// hot-reload); this is the ops surface onto it. Editable only when the engine serves
// from an editable source. Safety is still code, not content: crisis text lives in the
// engine, and always-on agents (environment, feedback) can't be disabled here.
function PromptWorkbook() {
  const [data, setData] = useState<WbResp | null>(null);
  const [sel, setSel] = useState("");
  const [draft, setDraft] = useState<{ prompt: string; model: string; enabled: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<string[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [saved, setSaved] = useState("");

  const load = useCallback(() => {
    engineJson<WbResp>("/v1/prompts").then(setData).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  const agent = data?.agents.find((a) => a.stage === sel) ?? null;
  function open(stage: string) {
    const a = data?.agents.find((x) => x.stage === stage);
    if (!a) return;
    setSel(stage);
    setDraft({ prompt: a.prompt, model: a.model, enabled: a.enabled });
    setErrors([]); setWarnings([]); setSaved(""); setError("");
  }

  async function save() {
    if (!agent || !draft || busy) return;
    const payload: Record<string, unknown> = {};
    if (draft.prompt !== agent.prompt) payload.prompt = draft.prompt;
    if (draft.model !== agent.model) payload.model = draft.model;
    if (draft.enabled !== agent.enabled) payload.enabled = draft.enabled;
    if (Object.keys(payload).length === 0) { setError("No changes to save."); return; }
    setBusy(true); setError(""); setErrors([]); setWarnings([]); setSaved("");
    try {
      const r = await engineApi(`/v1/prompts/${agent.stage}`, {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
      });
      const body = await r.json().catch(() => null);
      if (!r.ok) {
        const detail = body?.detail;
        if (detail && typeof detail === "object") { setErrors(detail.errors ?? []); setError(detail.message ?? `HTTP ${r.status}`); }
        else setError(typeof detail === "string" ? detail : `HTTP ${r.status}`);
        return;
      }
      setWarnings(body?.warnings ?? []);
      setSaved(`Saved · v${body?.version ?? "?"} · ${Number(body?.size ?? 0).toLocaleString()} ch`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "save failed");
    } finally { setBusy(false); }
  }

  async function reload() {
    setBusy(true); setError("");
    try { await engineApi("/v1/prompts/reload", { method: "POST" }); load(); setSel(""); }
    catch (e) { setError(e instanceof Error ? e.message : "reload failed"); }
    finally { setBusy(false); }
  }

  if (error && !data) return <div className="card"><p className="error">{error}</p></div>;
  if (!data) return <div className="card"><Skeleton rows={5} /></div>;
  const editable = data.editable;
  const issues = data.validation?.issue_count ?? 0;
  const dirty = !!(agent && draft && (draft.prompt !== agent.prompt || draft.model !== agent.model || draft.enabled !== agent.enabled));
  return (
    <div className="card">
      <h2>Prompt workbook <span className="hint">
        source: {data.source} · {editable ? "editable" : "read-only"} · {data.count} agents{data.version ? ` · v${data.version}` : ""}
      </span></h2>
      <div style={{ display: "flex", gap: 8, marginBottom: 10, alignItems: "center" }}>
        <button className="ghost" onClick={reload} disabled={busy}>Reload from source</button>
        {data.degraded && <span className="pill off">degraded: {data.degraded_reason || "serving fallback"}</span>}
        <span className={`pill ${issues ? "off" : "ok"}`}>{issues ? `${issues} validation issue${issues === 1 ? "" : "s"}` : "validation clean"}</span>
      </div>
      {!!issues && (
        <details className="mermaidsrc" style={{ marginBottom: 12 }}>
          <summary>Validation report</summary>
          <pre className="prompt">{JSON.stringify(data.validation, null, 2)}</pre>
        </details>
      )}
      <table className="table">
        <thead><tr><th>Agent</th><th>Model</th><th>Prompt size</th><th>Enabled</th><th></th></tr></thead>
        <tbody>
          {data.agents.map((a) => (
            <tr key={a.stage} className={a.stage === sel ? "active" : ""}>
              <td>{a.stage}{a.always_on ? <span className="hint"> · always-on</span> : null}</td>
              <td>{a.model || "—"}</td><td>{a.size.toLocaleString()} ch</td>
              <td><span className={`pill ${a.enabled ? "ok" : "off"}`}>{a.enabled ? "yes" : "no"}</span></td>
              <td><button className="ghost" onClick={() => open(a.stage)}>{editable ? "edit" : "view"}</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      {agent && draft && (
        <div className="promptview">
          <h3>{agent.stage}{agent.always_on ? <span className="hint"> · always-on (cannot be disabled)</span> : null}</h3>
          <label className="fld">Model
            <input value={draft.model} disabled={!editable}
              onChange={(e) => setDraft({ ...draft, model: e.target.value })} />
          </label>
          <label className="fld" style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
            <input type="checkbox" checked={draft.enabled} disabled={!editable || agent.always_on}
              onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })} style={{ width: "auto" }} />
            Enabled{agent.always_on ? " (locked on)" : ""}
          </label>
          <label className="fld">Prompt <span className="hint">{draft.prompt.length.toLocaleString()} ch · validated on save</span>
            <textarea value={draft.prompt} disabled={!editable} rows={16}
              onChange={(e) => setDraft({ ...draft, prompt: e.target.value })} />
          </label>
          {editable && (
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <button className="primary" disabled={busy || !dirty} onClick={save}>{busy ? "Saving…" : "Save"}</button>
              <button className="ghost" disabled={busy} onClick={() => open(agent.stage)}>Revert edits</button>
              {saved && <span className="pill ok">{saved}</span>}
            </div>
          )}
          {error && <p className="error">{error}</p>}
          {errors.length > 0 && (
            <div className="output"><b className="error">Validation failed — not saved:</b>
              <ul>{errors.map((x, i) => <li key={i} className="error">{x}</li>)}</ul>
            </div>
          )}
          {warnings.length > 0 && (
            <div className="output"><b className="hint">Saved with warnings:</b>
              <ul>{warnings.map((x, i) => <li key={i} className="hint">{x}</li>)}</ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

type Escalation = { org_id?: string; user_id: string; session_id: string; detected_by?: string; at?: string; delivered?: boolean };
type SafetyResp = { armed: boolean; classifier_enabled: boolean; count: number; escalations: Escalation[] };

// Safety queue — crisis escalations. Signal only, never content: who tripped the screen,
// in which session, and whether the designated contact was reached. The disclosure itself
// is never stored or shown (docs/SECURITY.md — "counts, never content").
function SafetyQueue() {
  const [data, setData] = useState<SafetyResp | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    engineJson<SafetyResp>("/v1/safety/escalations").then(setData).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);
  if (error) return <Failed msg={error} onRetry={load} />;
  if (!data) return <div className="card"><Skeleton rows={4} /></div>;
  return (
    <div className="card">
      <h2>Safety queue <span className="hint">crisis escalations · signal only, never content · {data.count}</span></h2>
      <div className="stats" style={{ marginBottom: 12 }}>
        <div className="stat"><b><span className={`pill ${data.armed ? "ok" : "off"}`}>{data.armed ? "armed" : "not armed"}</span></b><span>escalation contact</span></div>
        <div className="stat"><b><span className={`pill ${data.classifier_enabled ? "ok" : "off"}`}>{data.classifier_enabled ? "on" : "off"}</span></b><span>crisis classifier</span></div>
      </div>
      {!data.armed && (
        <p className="hint">No designated contact is configured (CEREBROZEN_CRISIS_ESCALATION_URL). A person in crisis still receives their helpline reply — but no human is notified. A silently-unconfigured safety net is worse than an absent one.</p>
      )}
      <table className="table">
        <thead><tr><th>When</th><th>User</th><th>Session</th><th>Detected by</th><th>Contact reached</th><th>Org</th></tr></thead>
        <tbody>
          {data.escalations.map((e, i) => (
            <tr key={`${e.session_id}:${e.at ?? ""}:${i}`}>
              <td>{e.at ? new Date(e.at).toLocaleString() : "—"}</td>
              <td>{e.user_id}</td>
              <td>{e.session_id}</td>
              <td>{e.detected_by ?? "—"}</td>
              <td><span className={`pill ${e.delivered ? "ok" : "off"}`}>{e.delivered ? "yes" : "no"}</span></td>
              <td>{e.org_id ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.count === 0 && <p className="hint">No escalations — nothing has tripped the crisis screen.</p>}
      <p className="hint" style={{ marginTop: 10 }}>
        This is a signal, not a record of what anyone said. Who responds, how they are trained, and how fast they act is the deployment&rsquo;s programme — not this feature.
      </p>
    </div>
  );
}

type Nudge = { org_id?: string; user_id: string; due_count?: number; session_ids?: string[]; at?: string; delivered?: boolean };
type NudgesResp = { armed: boolean; count: number; nudges: Nudge[] };

// Nudges — check-in reminder deliveries. Signal only (who has commitments due to check
// in on, and whether the reminder was delivered), never a commitment body.
function Nudges() {
  const [data, setData] = useState<NudgesResp | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    engineJson<NudgesResp>("/v1/nudges").then(setData).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);
  if (error) return <Failed msg={error} onRetry={load} />;
  if (!data) return <div className="card"><Skeleton rows={4} /></div>;
  return (
    <div className="card">
      <h2>Nudges <span className="hint">check-in reminders · signal only · {data.count}</span></h2>
      <div className="stats" style={{ marginBottom: 12 }}>
        <div className="stat"><b><span className={`pill ${data.armed ? "ok" : "off"}`}>{data.armed ? "armed" : "not armed"}</span></b><span>delivery channel</span></div>
      </div>
      {!data.armed && (
        <p className="hint">No delivery endpoint is configured (CEREBROZEN_NUDGE_DELIVERY_URL). The scheduler still computes who&rsquo;s due, but no reminder is sent until a channel is wired.</p>
      )}
      <table className="table">
        <thead><tr><th>When</th><th>User</th><th>Commitments due</th><th>Sessions</th><th>Delivered</th><th>Org</th></tr></thead>
        <tbody>
          {data.nudges.map((n, i) => (
            <tr key={`${n.user_id}:${n.at ?? ""}:${i}`}>
              <td>{n.at ? new Date(n.at).toLocaleString() : "—"}</td>
              <td>{n.user_id}</td>
              <td>{n.due_count ?? "—"}</td>
              <td>{n.session_ids?.length ?? 0}</td>
              <td><span className={`pill ${n.delivered ? "ok" : "off"}`}>{n.delivered ? "yes" : "no"}</span></td>
              <td>{n.org_id ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.count === 0 && <p className="empty-state">No nudges yet — nothing is due for a check-in.</p>}
    </div>
  );
}

// Agent flow — the governed coaching arc, rendered as the engine's real branching
// graph (Mermaid → SVG), with the agents table below.
function AgentFlow() {
  const [agents, setAgents] = useState<AgentRow[] | null>(null);
  const [src, setSrc] = useState("");
  const [svg, setSvg] = useState("");
  const [zoom, setZoom] = useState(1);
  const [error, setError] = useState("");
  function downloadSvg() {
    const blob = new Blob([svg], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = "agent-flow.svg"; a.click();
    URL.revokeObjectURL(url);
  }
  useEffect(() => {
    engineJson<AgentsResp>("/v1/agents").then((r) => setAgents(r.agents)).catch((e) => setError(e.message));
    engineJson<{ mermaid: string }>("/v1/graph/mermaid").then((r) => setSrc(r.mermaid)).catch(() => {});
  }, []);
  useEffect(() => {
    if (!src) return;
    let alive = true;
    (async () => {
      try {
        const mermaid = (await import("mermaid")).default;
        mermaid.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict" });
        const { svg } = await mermaid.render("agentgraph", src);
        if (alive) setSvg(svg);
      } catch {
        /* fall back to the source <pre> below */
      }
    })();
    return () => { alive = false; };
  }, [src]);
  if (error) return <div className="card"><p className="error">{error}</p></div>;
  if (!agents) return <div className="card"><Skeleton rows={4} /></div>;
  return (
    <div className="card">
      <h2>Agent flow <span className="hint">the governed coaching arc · {agents.length} agents · routing is deterministic (code predicates over typed state)</span></h2>
      {svg ? (
        <>
          <div className="graphbar">
            <button className="ghost" aria-label="Zoom out" onClick={() => setZoom((z) => Math.max(0.5, +(z - 0.25).toFixed(2)))}>−</button>
            <span className="z">{Math.round(zoom * 100)}%</span>
            <button className="ghost" aria-label="Zoom in" onClick={() => setZoom((z) => Math.min(4, +(z + 0.25).toFixed(2)))}>+</button>
            <button className="ghost" onClick={() => setZoom(1)}>Reset</button>
            <button className="ghost" onClick={downloadSvg}>Download SVG</button>
            <span className="hint">scroll to pan · zoom to read</span>
          </div>
          <div className="graph">
            <div style={{ zoom }} dangerouslySetInnerHTML={{ __html: svg.replace(/max-width:\s*[\d.]+px;?/g, "").replace(/\swidth="100%"/g, "") }} />
          </div>
        </>
      ) : src
        ? <pre className="prompt">{src}</pre>
        : <p className="hint">Rendering graph…</p>}
      <details className="mermaidsrc" style={{ marginTop: 16 }}>
        <summary>Agents in the arc ({agents.length})</summary>
        <table className="table">
          <thead><tr><th>Agent</th><th>Model</th><th>Enabled</th></tr></thead>
          <tbody>
            {agents.map((a) => (
              <tr key={a.stage}><td>{a.stage}</td><td>{a.model}</td><td>{a.enabled ? "yes" : "no"}</td></tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}

// Console — test any prompt against the live model (the ref app's Console).
function Console() {
  const [system, setSystem] = useState("You are a helpful assistant.");
  const [user, setUser] = useState("");
  const [model, setModel] = useState("");
  const [busy, setBusy] = useState(false);
  const [out, setOut] = useState<{ reply?: string; text?: string; model?: string; prompt_tokens?: number; completion_tokens?: number; cost_usd?: number } | null>(null);
  const [error, setError] = useState("");
  async function run() {
    if (!user.trim() || busy) return;
    setBusy(true); setError(""); setOut(null);
    try {
      const r = await engineApi("/v1/console/run", {
        method: "POST",
        body: JSON.stringify({ system, user, model: model || undefined }),
      });
      if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `HTTP ${r.status}`);
      setOut(await r.json());
    } catch (e) {
      setError(e instanceof Error ? e.message : "run failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="card">
      <h2>Console <span className="hint">test any prompt against the live model</span></h2>
      <label className="fld">System <span className="hint">supports {"{{variables}}"}</span>
        <textarea value={system} onChange={(e) => setSystem(e.target.value)} rows={3} />
      </label>
      <label className="fld">User
        <textarea value={user} onChange={(e) => setUser(e.target.value)} rows={5} placeholder="Your prompt…" />
      </label>
      <label className="fld">Model <span className="hint">optional — defaults to the configured cascade</span>
        <input value={model} onChange={(e) => setModel(e.target.value)} placeholder="gpt-5-mini" />
      </label>
      <button className="primary" disabled={busy || !user.trim()} onClick={run}>{busy ? "Running…" : "Run"}</button>
      {error && <p className="error">{error}</p>}
      {out && (
        <div className="output">
          <div className="hint">{out.model} · {out.prompt_tokens}→{out.completion_tokens} tok · ${out.cost_usd}</div>
          <pre className="prompt">{out.reply || out.text}</pre>
        </div>
      )}
    </div>
  );
}

export default function Admin() {
  const [me, setMe] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState("");

  const loadMe = useCallback(async () => {
    if (!getTokens()) { setMe(null); setReady(true); return; }
    try {
      const who = await apiJson<Me>("/users/me");
      setMe(who);
      setTab(who.role === "internal_admin" ? "tenants" : "overview");
    } catch {
      setMe(null);
    }
    setReady(true);
  }, []);
  useEffect(() => { loadMe(); }, [loadMe]);

  if (!ready) return null;
  if (!me) return <div className="shell"><Login onDone={loadMe} /></div>;

  const tabs =
    me.role === "internal_admin"
      ? [["tenants", "Tenants"], ["demos", "Demo requests"], ["prompts", "Prompt workbook"], ["flow", "Agent flow"], ["safety", "Safety queue"], ["nudges", "Nudges"], ["console", "Console"]]
      : [["overview", "Overview"], ["analytics", "Analytics"], ["people", "People"], ["invite", "Invite"]];

  return (
    <div className="shell">
      <header className="topbar">
        <span className="wordmark">CereBr<em>o</em>Zen · admin</span>
        <span className="who">
          {me.email} ({me.role}{me.org_name ? ` · ${me.org_name}` : ""})
          <button className="ghost" onClick={async () => { await logout(); setMe(null); }}>Sign out</button>
        </span>
      </header>
      <nav className="tabs">
        {tabs.map(([id, label]) => (
          <button key={id} className={tab === id ? "active" : ""} onClick={() => setTab(id)}>{label}</button>
        ))}
      </nav>
      {tab === "overview" && <OrgOverview />}
      {tab === "analytics" && <OrgAnalytics />}
      {tab === "people" && <People />}
      {tab === "invite" && <Invite />}
      {tab === "tenants" && <Tenants />}
      {tab === "demos" && <Demos />}
      {tab === "prompts" && <PromptWorkbook />}
      {tab === "flow" && <AgentFlow />}
      {tab === "safety" && <SafetyQueue />}
      {tab === "nudges" && <Nudges />}
      {tab === "console" && <Console />}
    </div>
  );
}
