"use client";

/* CereBroZen admin: one page, role-gated tabs (the ref/Zen admin pattern).
   org_admin  → Overview · People · Invite
   internal_admin → Tenants · Demo requests  */

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, apiJson, getTokens, login, logout } from "@/lib/api";

type Me = { id: string; email: string; name: string; role: string; org_id: string | null; org_name: string | null };
type Org = { id: string; name: string; slug: string; seats_total: number; seats_used: number; regulated_mode: boolean; crisis_region: string; is_active: boolean };
type Person = { id: string; email: string; name: string; role: string; is_active: boolean };
type Demo = { id: string; name: string; email: string; company: string; size: string; message: string; status: string; created_at: string };

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
  const [org, setOrg] = useState<Org | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    apiJson<Org>("/orgs/me").then(setOrg).catch((e) => setError(e.message));
  }, []);
  if (error) return <p className="error">{error}</p>;
  if (!org) return <p className="hint">Loading…</p>;
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

function People() {
  const [people, setPeople] = useState<Person[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    apiJson<Person[]>("/orgs/me/people").then(setPeople).catch((e) => setError(e.message));
  }, []);
  if (error) return <p className="error">{error}</p>;
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
    </div>
  );
}

function Invite() {
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setError(""); setToken("");
    try {
      const out = await apiJson<{ invitation_token: string }>("/orgs/me/invitations", {
        method: "POST",
        body: JSON.stringify({ email: data.get("email"), role: data.get("role") }),
      });
      setToken(out.invitation_token);
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
            Share this token with them — it is shown once and holds a seat until it expires. (Email delivery is a Phase 2 wiring.)
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
    await api(`/orgs/${org.id}`, { method: "PATCH", body: JSON.stringify({ is_active: !org.is_active }) });
    load();
  }
  return (
    <>
      <div className="card">
        <h2>Tenants</h2>
        <table>
          <thead><tr><th>Name</th><th>Slug</th><th>Seats</th><th>Regulated</th><th>Status</th><th /></tr></thead>
          <tbody>
            {(orgs ?? []).map((o) => (
              <tr key={o.id}>
                <td>{o.name}</td><td>{o.slug}</td><td>{o.seats_used} / {o.seats_total}</td>
                <td><span className={`pill ${o.regulated_mode ? "ok" : "off"}`}>{o.regulated_mode ? "on" : "off"}</span></td>
                <td><span className={`pill ${o.is_active ? "ok" : "off"}`}>{o.is_active ? "active" : "inactive"}</span></td>
                <td><button className="ghost" onClick={() => toggle(o)}>{o.is_active ? "deactivate" : "activate"}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
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
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setError(""); setToken("");
    try {
      const out = await apiJson<{ invitation_token: string }>(
        `/orgs/${data.get("org")}/invitations`,
        { method: "POST", body: JSON.stringify({ email: data.get("email"), role: "org_admin" }) },
      );
      setToken(out.invitation_token);
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
            Share this token once — they accept via POST /auth/accept-invitation (name + password) and land in the HR view.
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
  if (error) return <p className="error">{error}</p>;
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
      {rows?.length === 0 && <p className="hint">No demo requests yet.</p>}
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
      ? [["tenants", "Tenants"], ["demos", "Demo requests"]]
      : [["overview", "Overview"], ["people", "People"], ["invite", "Invite"]];

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
      {tab === "people" && <People />}
      {tab === "invite" && <Invite />}
      {tab === "tenants" && <Tenants />}
      {tab === "demos" && <Demos />}
    </div>
  );
}
