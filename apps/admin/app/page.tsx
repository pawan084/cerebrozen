"use client";

/* CereBroZen admin: one page, role-gated tabs (the ref/Zen admin pattern).
   org_admin  → Overview · People · Invite
   internal_admin → Tenants · Demo requests  */

import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { AuthExpired, api, apiJson, engineApi, engineJson, getTokens, login, logout } from "@/lib/api";
import { SITE_URL, siteLinks } from "@/lib/site";
import { AgentFlowCanvas } from "@/components/flow";

type Me = { id: string; email: string; name: string; role: string; org_id: string | null; org_name: string | null };
type Org = { id: string; name: string; slug: string; seats_total: number; seats_used: number; regulated_mode: boolean; crisis_region: string; is_active: boolean };
type Person = { id: string; email: string; name: string; role: string; is_active: boolean };
type Metric = { value: number | null; suppressed: boolean };
type Theme = { theme: string; people: number; events: number };
type Analytics = {
  window_days: number; cohort_floor: number;
  seats: { total: number; active_members: number };
  metrics: Record<string, Metric>;
  themes: { cohort_floor: number; top: Theme[]; suppressed: number };
};
type Demo = { id: string; name: string; email: string; company: string; size: string; message: string; status: string; created_at: string };

/* ── shared data-loading: skeleton while loading, retry on error ── */
function useLoad<T>(loader: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const ref = useRef(loader);
  ref.current = loader;
  const [expired, setExpired] = useState(false);
  const reload = useCallback(() => {
    setLoading(true); setError("");
    ref.current().then(setData).catch((e) => {
      // A dead session is not a failed request: Retry cannot fix it (the tokens are
      // already gone), so the card must offer the one action that can.
      setExpired(e instanceof AuthExpired);
      setError(e.message);
    }).finally(() => setLoading(false));
  }, []);
  useEffect(() => { reload(); }, [reload]);
  return { data, error, loading, reload, expired };
}
function Skeleton({ rows = 3 }: { rows?: number }) {
  return <div className="skeleton">{Array.from({ length: rows }).map((_, i) => <div key={i} className="skeleton-row" />)}</div>;
}
function Failed({ msg, onRetry, expired }: { msg: string; onRetry?: () => void; expired?: boolean }) {
  // Retry is the wrong verb for an expired session — refresh already ran and lost, the
  // tokens are cleared, and pressing it just fails again with the same opaque 401. A
  // reload lands on the sign-in screen, which is the thing the operator actually needs.
  return (
    <div className="card"><div className="failed">
      <p className="error">{msg}</p>
      {expired
        ? <button className="ghost" onClick={() => window.location.reload()}>Reload to sign in</button>
        : onRetry && <button className="ghost" onClick={onRetry}>Retry</button>}
    </div></div>
  );
}

/* Demo sign-in: one-click fill of the platform's dev-seeded personas. DEV ONLY.
   Gated on NODE_ENV *alone* and nothing else: webpack constant-folds it at build time,
   so a production build proves the branch dead and strips the block AND these
   credential strings out of the bundle entirely. (An earlier version also allowed an
   env-var opt-in — that made the condition runtime-unknowable, the minifier kept the
   branch, and the credentials shipped in the public JS. Verified by grepping .next.)
   The accounts can't exist in prod either: guard_production() refuses to boot with
   CEREBROZEN_SEED_DEV_ADMIN on. */
const SHOW_DEMO = process.env.NODE_ENV === "development";
const DEMOS = [
  { email: "admin@cerebrozen.in", password: "admin12345", who: "Dev Admin", role: "internal admin · ops" },
  { email: "hr@cerebrozen.in", password: "demo12345", who: "Dana Okafor", role: "org admin · Demo Co" },
];

function Login({ onDone }: { onDone: () => void }) {
  // Pre-filled in dev so a fresh clone is one click from signed in. Same NODE_ENV-only
  // gate as the chips below and the same strings — no new credential literal — so a
  // production build constant-folds SHOW_DEMO to false, these become "", and the
  // accounts leave the bundle with the chips. Chips still switch persona.
  const [email, setEmail] = useState(SHOW_DEMO ? DEMOS[0].email : "");
  const [password, setPassword] = useState(SHOW_DEMO ? DEMOS[0].password : "");
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    try {
      await login(email, password);
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "login failed");
    }
  }
  return (
    <div className="login-wrap card">
      {/* enso mark, echoing the marketing site's hero */}
      <svg viewBox="0 0 48 48" width="36" height="36" aria-hidden="true" style={{ display: "block", marginBottom: 12 }}>
        <path d="M34.5 13.5a13 13 0 1 0 3.2 8.6" fill="none" stroke="#f56b6b" strokeWidth="4.5" strokeLinecap="round" />
        <circle cx="36" cy="12" r="3" fill="#f56b6b" />
      </svg>
      {/* The site is a separate deployment — a real navigation, not a route. */}
      <a className="wordmark home" href={SITE_URL}>CereBr<em>o</em>Zen<span className="wm-sub"> · admin</span></a>
      <h2>Sign in</h2>
      <form className="stack" onSubmit={submit}>
        <label>Email<input name="email" type="email" required autoComplete="username"
          value={email} onChange={(e) => setEmail(e.target.value)} /></label>
        <label>Password<input name="password" type="password" required autoComplete="current-password"
          value={password} onChange={(e) => setPassword(e.target.value)} /></label>
        <button className="primary">Sign in</button>
        {error && <p className="error">{error}</p>}
      </form>
      {SHOW_DEMO && (
        <div className="demo">
          <div className="demo-lbl">Demo — development only</div>
          {DEMOS.map((d) => (
            <button key={d.email} type="button" className="demo-chip"
              onClick={() => { setEmail(d.email); setPassword(d.password); setError(""); }}>
              <span className="dc-who">{d.who}</span>
              <span className="dc-role">{d.role}</span>
            </button>
          ))}
        </div>
      )}
      {/* Privacy and Terms live on the site; a login screen is where people look for
          them, and the admin must not fork its own copy. */}
      <nav className="login-foot">
        {siteLinks.map((l) => <a key={l.href} href={l.href}>{l.label}</a>)}
      </nav>
    </div>
  );
}

function OrgOverview() {
  const { data: org, error, loading, reload, expired } = useLoad<Org>(() => apiJson<Org>("/orgs/me"));
  if (loading) return <div className="card"><Skeleton rows={2} /></div>;
  if (error) return <Failed msg={error} onRetry={reload} expired={expired} />;
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
        Aggregates only, with cohort floors — this portal never shows coaching content.
        Full metrics are on the Analytics tab.
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

// Raw role enums read like a database dump in a roster; humanize them for HR.
const ROLE_LABEL: Record<string, string> = {
  internal_admin: "Internal admin",
  org_admin: "Org admin",
  user: "Member",
};

function OrgAnalytics() {
  const { data, error, loading, reload, expired } = useLoad<Analytics>(() => apiJson<Analytics>("/orgs/me/analytics"));
  if (loading) return <div className="card"><Skeleton rows={3} /></div>;
  if (error) return <Failed msg={error} onRetry={reload} expired={expired} />;
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
            <div className={`stat${m.suppressed ? " suppressed" : ""}`} key={name}>
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
        <h2>What your org is coaching on</h2>
        {data.themes.top.length === 0 ? (
          <p className="hint">
            No development area yet has {data.themes.cohort_floor} or more people working on
            it — themes appear once a cohort is large enough that naming one identifies no one.
          </p>
        ) : (
          <ul className="themes">
            {data.themes.top.map((t) => (
              <li key={t.theme}>
                <span>{t.theme}</span>
                <b>{t.people} {t.people === 1 ? "person" : "people"}</b>
              </li>
            ))}
          </ul>
        )}
        {data.themes.suppressed > 0 && (
          <p className="hint" style={{ marginTop: 10 }}>
            {data.themes.suppressed} further {data.themes.suppressed === 1 ? "theme is" : "themes are"}{" "}
            below the {data.themes.cohort_floor}-person floor and withheld — counted, never named.
          </p>
        )}
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

const PAGE = 25;

function People({ me }: { me: Me }) {
  /* Search and paging are SERVER-side. This roster used to fetch every row and render
     them all: a 2,000-seat tenant shipped 2,000 rows on every visit, and filtering in the
     browser would still have sent them. */
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<Person[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState("");
  const [loadingMore, setLoadingMore] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [failed, setFailed] = useState("");
  // This list loads by hand rather than through useLoad, so it needs the same distinction:
  // a dead session cannot be retried, only signed into again.
  const [expired, setExpired] = useState(false);

  const fetchPage = useCallback(async (term: string, offset: number) => {
    const url = `/orgs/me/people?limit=${PAGE}&offset=${offset}${term ? `&q=${encodeURIComponent(term)}` : ""}`;
    return apiJson<{ total: number; people: Person[] }>(url);
  }, []);

  const load = useCallback((term: string) => {
    setError("");
    fetchPage(term, 0)
      .then((r) => { setRows(r.people); setTotal(r.total); })
      .catch((e) => { setExpired(e instanceof AuthExpired); setError(e.message); });
  }, [fetchPage]);

  // Debounced: a keystroke per request would hammer the roster of a big tenant.
  useEffect(() => {
    const t = setTimeout(() => load(q), q ? 250 : 0);
    return () => clearTimeout(t);
  }, [q, load]);

  async function more() {
    if (!rows) return;
    setLoadingMore(true);
    try {
      const r = await fetchPage(q, rows.length);
      setRows([...rows, ...r.people]);
      setTotal(r.total);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't load more.");
    } finally {
      setLoadingMore(false);
    }
  }
  const reload = () => load(q);
  const people = rows;
  const loading = rows === null && !error;

  /* Offboarding was the hole here: this roster showed a leaver and offered nothing to do
     about them, and the seat they no longer used stayed counted against the org. */
  async function toggle(p: Person) {
    const msg = p.is_active
      ? `Deactivate ${p.email}? They lose access immediately and their seat is freed. Their own coaching content is untouched — it is theirs, not the company's.`
      : `Reactivate ${p.email}? They can sign in again and this uses a seat.`;
    if (!window.confirm(msg)) return;
    setBusy(p.id); setFailed("");
    try {
      await api(`/orgs/me/people/${p.id}`, { method: "PATCH", body: JSON.stringify({ is_active: !p.is_active }) });
      reload();
    } catch (e) {
      setFailed(e instanceof Error ? e.message : "Couldn't change their status.");
    } finally {
      setBusy(null);
    }
  }

  if (error && !rows) return <Failed msg={error} onRetry={reload} expired={expired} />;
  return (
    <div className="card">
      <div className="people-head">
        <input className="wb-search" type="search" placeholder="Search by name or email"
          aria-label="Search people" style={{ marginLeft: "auto" }}
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {loading ? <Skeleton /> : (
      <>
      <table className="roster">
        <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th aria-label="Actions" /></tr></thead>
        <tbody>
          {(people ?? []).map((p) => (
            <tr key={p.id}>
              <td className="cell-name">{p.name || <span className="muted">—</span>}</td>
              <td className="cell-email">{p.email}</td>
              <td className="muted">{ROLE_LABEL[p.role] ?? p.role}</td>
              <td><span className={`pill ${p.is_active ? "ok" : "off"}`}>{p.is_active ? "active" : "disabled"}</span></td>
              <td className="cell-action">
                {/* No self-deactivation: with one org_admin it locks the whole tenant out
                    of its own console. The server refuses it too — this only saves the
                    round trip and the confusing error. */}
                {p.id === me.id ? <span className="muted">you</span> : (
                  <button className="ghost sm" disabled={busy === p.id} onClick={() => toggle(p)}>
                    {busy === p.id ? "…" : p.is_active ? "deactivate" : "reactivate"}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {failed && <p className="error">{failed}</p>}
      {people?.length === 0 && (
        <p className="empty-state">
          {q ? `No one matches “${q}”.` : "No members yet — invite someone from the Invite tab."}
        </p>
      )}
      {(people?.length ?? 0) > 0 && (
        <div className="people-foot">
          {/* The filtered total, so a search says how many it FOUND — "1 of 340" would
              read as a broken filter. */}
          <span className="hint">Showing {people!.length} of {total}</span>
          {people!.length < total && (
            <button className="ghost" disabled={loadingMore} onClick={more}>
              {loadingMore ? "…" : "Load more"}
            </button>
          )}
        </div>
      )}
      </>
      )}
      <p className="hint" style={{ marginTop: 12 }}>
        Deactivating ends someone&rsquo;s access and frees their seat. It is not deletion:
        their coaching content is theirs, and only they can export or erase it.
      </p>
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
      <p className="panel-status">
        Send an invitation to join this workspace. They set their own password when they
        accept — you never see it.
      </p>
      <form className="stack" onSubmit={submit}>
        <label>Work email<input name="email" type="email" required /></label>
        <label>Role
          {/* Values are the API enums; labels are the humanized forms (see ROLE_LABEL). */}
          <select name="role" defaultValue="user">
            <option value="user">Member</option>
            <option value="org_admin">Org admin</option>
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

/* Seats, inline. Renewals and upsells are a seat change, and until now that meant editing
   Postgres by hand — the API has accepted `seats_total` all along. Refuses to go below the
   seats already in use: the server would reject it anyway, and a number that silently
   disagrees with the roster is worse than a refusal. */
function SeatEditor({ org, onSaved }: { org: Org; onSaved: () => void }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(String(org.seats_total));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (!editing) {
    return (
      <button className="linklike" onClick={() => { setEditing(true); setValue(String(org.seats_total)); setError(""); }}>
        {org.seats_used} / {org.seats_total}
      </button>
    );
  }
  const next = Number(value);
  const tooFew = Number.isFinite(next) && next < org.seats_used;
  async function save() {
    if (!Number.isFinite(next) || next < 1 || tooFew) return;
    setBusy(true); setError("");
    try {
      await api(`/orgs/${org.id}`, { method: "PATCH", body: JSON.stringify({ seats_total: next }) });
      setEditing(false);
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <span className="seat-edit">
      <span className="hint">{org.seats_used} /</span>
      <input type="number" min={org.seats_used || 1} value={value} disabled={busy}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => { if (e.key === "Enter") save(); if (e.key === "Escape") setEditing(false); }} />
      <button className="ghost" disabled={busy || tooFew} onClick={save}>{busy ? "…" : "save"}</button>
      <button className="ghost" disabled={busy} onClick={() => setEditing(false)}>cancel</button>
      {tooFew && <span className="error">{org.seats_used} in use</span>}
      {error && <span className="error">{error}</span>}
    </span>
  );
}

/* The crisis region drives which helplines this tenant's employees are shown
   (engine app/safety/helplines.py). It is a safety setting, so the options come from the
   ENGINE rather than a list retyped here — a region the engine cannot localise would
   silently fall back to the international finder, and the operator would never know they
   had picked something that does nothing. */
function RegionEditor({ org, regions, onSaved }: { org: Org; regions: string[]; onSaved: () => void }) {
  const [busy, setBusy] = useState(false);
  async function set(next: string) {
    if (next === org.crisis_region) return;
    setBusy(true);
    try {
      await api(`/orgs/${org.id}`, { method: "PATCH", body: JSON.stringify({ crisis_region: next }) });
      onSaved();
    } finally {
      setBusy(false);
    }
  }
  return (
    <select className="mini" value={org.crisis_region} disabled={busy || regions.length === 0}
      onChange={(e) => set(e.target.value)} aria-label={`Crisis region for ${org.name}`}>
      {/* "" is a real choice, not a placeholder: it means the engine serves an
          international directory rather than guessing a country. */}
      <option value="">international</option>
      {regions.map((r) => <option key={r} value={r}>{r}</option>)}
    </select>
  );
}

// ── Per-org knowledge base (CSKB) — the "Tuned to Your Culture" mechanism ────
// A tenant's own material (their competency framework, their values), retrieved per turn
// and woven into the coaching. Without it the coach improvises over an empty index and
// the marketing claim has no mechanism behind it (rule 6) — and it fails SILENTLY: no
// values doc → no {CSKB_Values} → the prompt's field-presence gate takes the absent
// branch → ungrounded coaching, no error. This panel exists to make that visible.
//
// Ops-curated, not self-serve: everything indexed here is retrieved straight into the
// coach's context on a later turn, so an upload box is an instruction channel into every
// session that tenant runs. PRODUCT.md ships "curated" and puts self-serve in v2 behind
// SECURITY.md's injection review.
type CskbDoc = { doc_key: string; doc_type: string; source: string; chunks: number; chars: number };
type CskbHealth = {
  org_id: string; enabled: boolean; docs: CskbDoc[];
  by_type: Record<string, { docs: number; chunks: number }>;
  retrievable: string[]; missing: string[];
};

const DOC_TYPES = ["values", "frameworks", "competencies", "learning_aids", "general"];

function KnowledgeBase({ org }: { org: Org }) {
  const [kb, setKb] = useState<CskbHealth | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(() => {
    engineJson<CskbHealth>(`/v1/cskb/${encodeURIComponent(org.id)}`)
      .then(setKb).catch((e) => setError(e.message));
  }, [org.id]);
  useEffect(load, [load]);

  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const d = new FormData(form);
    setBusy(true); setError("");
    try {
      const r = await engineApi(`/v1/cskb/${encodeURIComponent(org.id)}`, {
        method: "POST",
        body: JSON.stringify({
          title: String(d.get("title") || ""),
          doc_type: String(d.get("doc_type") || ""),
          text: String(d.get("text") || ""),
        }),
      });
      if (!r.ok) throw new Error((await r.json())?.detail || `HTTP ${r.status}`);
      form.reset();
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(doc: CskbDoc) {
    if (!window.confirm(`Remove "${doc.doc_key}" from ${org.name}'s knowledge base?\n\nTheir coach stops retrieving it on the next turn.`)) return;
    setBusy(true); setError("");
    try {
      const r = await engineApi(
        `/v1/cskb/${encodeURIComponent(org.id)}/docs?doc_key=${encodeURIComponent(doc.doc_key)}`,
        { method: "DELETE" },
      );
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed.");
    } finally {
      setBusy(false);
    }
  }

  if (!kb) return <div className="kb"><Skeleton rows={2} /></div>;
  if (!kb.enabled) {
    return (
      <div className="kb">
        <p className="hint">
          No manageable vector index (pgvector) is configured, so this tenant&rsquo;s knowledge
          base cannot be read or written here. <b>That is not the same as empty</b> — nothing
          uploaded would be indexed.
        </p>
      </div>
    );
  }
  return (
    <div className="kb">
      <div className="kb-head">
        <b>Knowledge base</b>
        {kb.missing.length === 0
          ? <span className="pill ok">grounded</span>
          : <span className="pill off">ungrounded: no {kb.missing.join(", ")}</span>}
      </div>
      {kb.missing.length > 0 && (
        <p className="hint">
          {/* One expression, not text-around-an-expression: JSX collapses the whitespace
              between `{expr}` and a following line, which rendered "learning_aidson this
              tenant". Only visible by looking at the page. */}
          {`The coach retrieves nothing for ${kb.missing.join(", ")} on this tenant, so those turns run on the general method alone. It does not error — it just stops being “tuned to your culture”.`}
        </p>
      )}
      {kb.docs.length > 0 && (
        <table className="table">
          <thead><tr><th>Document</th><th>Type</th><th>Chunks</th><th /></tr></thead>
          <tbody>
            {kb.docs.map((d) => (
              <tr key={d.doc_key}>
                <td><code>{d.doc_key.replace(`cskb:${org.id}:`, "")}</code></td>
                <td>{d.doc_type}</td>
                {/* Chunks, not documents: retrieval sees chunks, so a file that chunked
                    to zero is indexed in name only. */}
                <td>{d.chunks}</td>
                <td><button className="ghost" disabled={busy} onClick={() => remove(d)}>remove</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <form className="kb-form" onSubmit={upload}>
        <input name="title" placeholder="Document title" required maxLength={200} />
        <select name="doc_type" defaultValue="values">
          {DOC_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
        <textarea name="text" placeholder="Paste the document text — their values, their competency framework…" required rows={3} />
        <button className="ghost" disabled={busy}>{busy ? "Indexing…" : "Add to knowledge base"}</button>
      </form>
      <p className="hint">
        Re-using a title replaces that document rather than duplicating it. Curated by us,
        not uploaded by the customer: indexed text is retrieved into the coach&rsquo;s context on
        a later turn, so this is an instruction channel into every session they run
        (docs/SECURITY.md — self-serve is v2, behind the injection review).
      </p>
      {error && <p className="error">{error}</p>}
    </div>
  );
}

function Tenants() {
  const [orgs, setOrgs] = useState<Org[] | null>(null);
  const [regions, setRegions] = useState<string[]>([]);
  const [openKb, setOpenKb] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [why, setWhy] = useState(false);
  const [q, setQ] = useState("");
  const [error, setError] = useState("");
  const [expired, setExpired] = useState(false);
  const load = useCallback(() => {
    apiJson<Org[]>("/orgs").then(setOrgs).catch((e) => {
      setExpired(e instanceof AuthExpired);
      setError(e.message);
    });
  }, []);
  useEffect(load, [load]);
  useEffect(() => {
    // One source of truth for which regions are real — the engine that serves the numbers.
    engineJson<{ regions: string[] }>("/v1/safety/helplines")
      .then((r) => setRegions(r.regions))
      .catch(() => setRegions([]));
  }, []);
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
      // Close on success: the new tenant is now the thing to look at, in the table above.
      // Leaving the form open invites a second create nobody asked for.
      setCreating(false);
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
  const needle = q.trim().toLowerCase();
  const shown = (orgs ?? []).filter(
    (o) => !needle || o.name.toLowerCase().includes(needle) || o.slug.toLowerCase().includes(needle),
  );
  return (
    <>
      <div className="card">
        <div className="people-head">
          {/* Client-side, deliberately: /orgs returns every tenant in one response, so a
              server query would not save the fetch — it would only move the filter. This
              makes them FINDABLE now; the unbounded fetch is a separate server concern and
              the honest limit of this control (see the note under the table). */}
          {(orgs?.length ?? 0) > 5 && (
            <input className="wb-search" type="search" placeholder="Filter tenants…"
              aria-label="Filter tenants" value={q} onChange={(e) => setQ(e.target.value)} />
          )}
          {!creating && <button className="ghost" style={{ marginLeft: "auto" }} onClick={() => setCreating(true)}>+ New tenant</button>}
        </div>
        {orgs === null ? (
          error ? <Failed msg={error} onRetry={load} expired={expired} /> : <Skeleton />
        ) : (
          <>
            <table>
              <thead><tr><th>Name</th><th>Slug</th><th>Seats</th><th>Crisis region</th><th>Regulated</th><th>Status</th><th /><th /></tr></thead>
              <tbody>
                {shown.map((o) => (
                  <Fragment key={o.id}>
                  <tr>
                    <td>{o.name}</td><td>{o.slug}</td>
                    <td><SeatEditor org={o} onSaved={load} /></td>
                    <td><RegionEditor org={o} regions={regions} onSaved={load} /></td>
                    {/* Read-only, deliberately. SECURITY.md: regulated-mode opt-out is "a
                        contract-level decision with counsel sign-off, NOT an admin toggle"
                        — EU AI Act Art. 5 sits behind it. The API accepts the field for an
                        operator acting on a signed contract; a switch in a console that
                        anyone with the tab can flick is a different thing entirely, and
                        this page already tells the reader so. */}
                    <td><span className={`pill ${o.regulated_mode ? "ok" : "off"}`}>{o.regulated_mode ? "on" : "off"}</span></td>
                    <td><span className={`pill ${o.is_active ? "ok" : "off"}`}>{o.is_active ? "active" : "inactive"}</span></td>
                    <td><button className="ghost" onClick={() => toggle(o)}>{o.is_active ? "deactivate" : "activate"}</button></td>
                    {/* Per-tenant, so it lives on the tenant's row rather than in a tab
                        that would need its own org picker — the thing that made the safety
                        queue empty was an operator surface guessing which org it meant. */}
                    <td><button className="linklike" onClick={() => setOpenKb(openKb === o.id ? null : o.id)}>
                      {openKb === o.id ? "hide KB" : "knowledge base"}
                    </button></td>
                  </tr>
                  {openKb === o.id && (
                    <tr><td colSpan={8}><KnowledgeBase org={o} /></td></tr>
                  )}
                  </Fragment>
                ))}
              </tbody>
            </table>
            {orgs.length === 0 && <p className="empty-state">No tenants yet — create the first one.</p>}
            {orgs.length > 0 && shown.length === 0 && (
              <p className="empty-state">No tenant matches &ldquo;{q}&rdquo;.</p>
            )}
            {/* One line, with the reasoning behind a disclosure. The rules have not
                changed — regulated mode is still not a switch — but an operator reads this
                page to DO something, and three paragraphs of policy between them and the
                table is a wall they learn to scroll past. Detail on demand keeps it
                available without making it the room's furniture. */}
            <p className="hint" style={{ marginTop: 12 }}>
              Seats and crisis region are editable. <b>Regulated mode is not.</b>{" "}
              <button className="linklike" onClick={() => setWhy((w) => !w)}>
                {why ? "hide why" : "why?"}
              </button>
            </p>
            {why && (
              <p className="hint">
                Turning regulated mode off is a contract-level decision with counsel
                sign-off, not a switch (docs/SECURITY.md) — EU AI Act Art. 5 sits behind it,
                and new tenants start with it ON. The crisis region decides which helplines
                this tenant&rsquo;s people see; &ldquo;international&rdquo; is a real answer,
                not a missing one.
              </p>
            )}
          </>
        )}
      </div>
      {/* Collapsed by default. Creating a tenant is a rare, deliberate act; it was taking
          half the viewport on every visit to a page whose actual job is the table above. */}
      {creating && (
        <div className="card">
          <div className="people-head">
            <h2>New tenant</h2>
            <button className="linklike" onClick={() => { setCreating(false); setError(""); }}>cancel</button>
          </div>
          <form className="stack" onSubmit={create}>
            <label>Name<input name="name" required minLength={2} autoFocus /></label>
            <label>Slug<input name="slug" required pattern="[a-z0-9-]+" /></label>
            <label>Seats<input name="seats_total" type="number" defaultValue={50} min={1} /></label>
            <button className="primary">Create tenant</button>
            {error && <p className="error">{error}</p>}
          </form>
        </div>
      )}
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
/** One agent as GET /v1/prompts/{stage} reports it — what the flow inspector shows. */
type NodeDetail = { stage?: string; prompt: string; model?: string; enabled?: boolean; always_on?: boolean; size?: number; version?: string };
type WbAgent = { stage: string; sheet: string; enabled: boolean; always_on: boolean; model: string; size: number; prompt: string };
type Validation = { issue_count?: number; ok?: boolean; issues?: unknown[] };
type WbResp = { source: string; editable: boolean; count: number; agents: WbAgent[]; version?: string; degraded?: boolean; degraded_reason?: string; validation?: Validation };

// Prompt workbook — VIEW + EDIT. The engine owns the workbook (validate-on-save,
// hot-reload); this is the ops surface onto it. Editable only when the engine serves
// from an editable source. Safety is still code, not content: crisis text lives in the
// engine, and always-on agents (environment, feedback) can't be disabled here.
type PromptVersion = { version_id: string; size: number; actor: string; reason: string; at: string; hash: string };

/* What this prompt used to say, and the way back.
 *
 * A save overwrote the sheet and the audit line recorded the actor and the version numbers
 * but not the text — so a bad edit to a 39,000-character coaching prompt was unrecoverable
 * from this console. The only copy was in git, which an operator on a running deployment
 * does not have. */
function PromptHistory({ stage, onRestored }: { stage: string; onRestored: () => void }) {
  const [rows, setRows] = useState<PromptVersion[] | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    engineJson<{ versions: PromptVersion[] }>(`/v1/prompts/${stage}/versions`)
      .then((r) => setRows(r.versions))
      .catch((e) => setError(e.message));
  }, [stage]);
  useEffect(() => { if (open) load(); }, [open, load]);
  useEffect(() => { setOpen(false); setRows(null); setError(""); }, [stage]);

  async function restore(v: PromptVersion) {
    if (!window.confirm(
      `Restore the version from ${new Date(v.at).toLocaleString()} (${v.size.toLocaleString()} ch)?\n\n` +
      "The text it replaces is saved first, so this is itself undoable.",
    )) return;
    setBusy(v.version_id); setError("");
    try {
      const r = await engineApi(`/v1/prompts/${stage}/revert/${v.version_id}`, { method: "POST" });
      const body = await r.json().catch(() => null);
      if (!r.ok) {
        // A version can rot — the validator moves. Say which rule, not "failed".
        setError(body?.detail?.message ?? body?.detail ?? `HTTP ${r.status}`);
        return;
      }
      load();
      onRestored();
    } catch (e) {
      setError(e instanceof Error ? e.message : "restore failed");
    } finally { setBusy(null); }
  }

  if (!open) {
    return <button className="ghost" onClick={() => setOpen(true)}>History</button>;
  }
  return (
    <div className="pv">
      <div className="pv-head">
        <b>Earlier versions</b>
        <button className="ghost" onClick={() => setOpen(false)}>Close</button>
      </div>
      {error && <p className="error">{error}</p>}
      {rows === null ? <Skeleton rows={2} /> : rows.length === 0 ? (
        <p className="hint">No earlier versions — the first save here records what it replaces.</p>
      ) : (
        <table>
          <thead><tr><th>When</th><th>Size</th><th>By</th><th>Why</th><th /></tr></thead>
          <tbody>
            {rows.map((v) => (
              <tr key={v.version_id}>
                <td>{new Date(v.at).toLocaleString()}</td>
                <td>{v.size.toLocaleString()} ch</td>
                <td className="hint">{v.actor.slice(0, 8)}</td>
                <td className="hint">{v.reason}</td>
                <td>
                  <button className="ghost" disabled={busy === v.version_id} onClick={() => restore(v)}>
                    {busy === v.version_id ? "…" : "restore"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function PromptWorkbook() {
  const [data, setData] = useState<WbResp | null>(null);
  const [sel, setSel] = useState("");
  const [draft, setDraft] = useState<{ prompt: string; model: string; enabled: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<string[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [saved, setSaved] = useState("");
  const [query, setQuery] = useState("");

  const load = useCallback(() => {
    engineJson<WbResp>("/v1/prompts").then(setData).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  async function downloadWorkbook() {
    try {
      const r = await engineApi("/v1/prompts/download");
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const url = URL.createObjectURL(await r.blob());
      const a = document.createElement("a");
      a.href = url; a.download = "agent_prompts.xlsx"; a.click();
      URL.revokeObjectURL(url);
    } catch (e) { setError(e instanceof Error ? e.message : "download failed"); }
  }

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
      <p className="panel-status">
        source: {data.source} · {editable ? "editable" : "read-only"} · {data.count} agents in the workbook{data.version ? ` · v${data.version}` : ""}
      </p>
      <p className="hint" style={{ marginBottom: 10 }}>
        Every agent whose prompt lives in the workbook. Two of them aren&rsquo;t graph nodes —
        <b> environment</b> (the guardrail wrapper composed into every prompt) and{" "}
        <b>user_context_builder_agent</b> (off-path) — which is why the Agent flow counts fewer.
      </p>
      <div style={{ display: "flex", gap: 8, marginBottom: 10, alignItems: "center", flexWrap: "wrap" }}>
        <button className="ghost" onClick={reload} disabled={busy}>Reload from source</button>
        <button className="ghost" onClick={downloadWorkbook}>Download .xlsx</button>
        <input className="wb-search" placeholder="Filter agents…" value={query}
          onChange={(e) => setQuery(e.target.value)} aria-label="Filter agents" />
        {/* Feedback belongs NEXT TO THE BUTTON. `saved`/`error` are also rendered inside
            the editor panel below — which only exists once an agent is open, so an upload
            from this toolbar reported its result to an empty room. Replacing the global
            workbook and saying nothing is the exact silent-success this console keeps
            finding elsewhere. */}
        {saved && <span className="pill ok">{saved}</span>}
        {error && <span className="error">{error}</span>}
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
          {data.agents.filter((a) => a.stage.toLowerCase().includes(query.toLowerCase())).map((a) => (
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
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <button className="primary" disabled={busy || !dirty} onClick={save}>{busy ? "Saving…" : "Save"}</button>
              {/* "Revert edits" drops the unsaved draft. "History" goes back to text that
                  was already SAVED — different buttons for different regrets. */}
              <button className="ghost" disabled={busy} onClick={() => open(agent.stage)}>Revert edits</button>
              <PromptHistory stage={agent.stage} onRestored={() => { load(); setSaved("Restored"); }} />
              {dirty && <span className="pr-dirty">● unsaved</span>}
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

type Escalation = {
  id: string; org_id?: string; user_id: string; session_id: string;
  detected_by?: string; at?: string; delivered?: boolean;
  acknowledged_at?: string | null; acknowledged_by?: string;
};
type SafetyResp = { armed: boolean; classifier_enabled: boolean; status: string; count: number; escalations: Escalation[] };

// Safety queue — crisis escalations. Signal only, never content: who tripped the screen,
// in which session, and whether the designated contact was reached. The disclosure itself
// is never stored or shown (docs/SECURITY.md — "counts, never content").
function SafetyQueue() {
  const [data, setData] = useState<SafetyResp | null>(null);
  const [status, setStatus] = useState<"open" | "resolved" | "all">("open");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => {
    engineJson<SafetyResp>(`/v1/safety/escalations?status=${status}`).then(setData).catch((e) => setError(e.message));
  }, [status]);
  useEffect(load, [load]);

  /* The queue was read-only, so it never drained: an operator who had already reached
     someone had no way to say so, and the row stayed open forever. */
  async function ack(e: Escalation) {
    if (!window.confirm(
      "Mark this escalation handled?\n\nIt records that you handled it, and when — nothing about what was said.",
    )) return;
    setBusy(e.id); setError("");
    try {
      const r = await engineApi(`/v1/safety/escalations/${encodeURIComponent(e.id)}/ack`, { method: "POST" });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't acknowledge.");
    } finally {
      setBusy(null);
    }
  }

  if (error && !data) return <Failed msg={error} onRetry={load} />;
  if (!data) return <div className="card"><Skeleton rows={4} /></div>;
  return (
    <div className="card">
      <div className="people-head">
        <p className="panel-status">crisis escalations · signal only, never content · {data.count}</p>
        {/* Open by default: a queue whose default view includes everything ever handled is
            a queue nobody reads, and this is the one an operator must read. */}
        <div className="seg">
          {(["open", "resolved", "all"] as const).map((s) => (
            <button key={s} className={status === s ? "active" : ""} onClick={() => setStatus(s)}>{s}</button>
          ))}
        </div>
      </div>
      <div className="stats" style={{ marginBottom: 12 }}>
        <div className="stat"><b><span className={`pill ${data.armed ? "ok" : "off"}`}>{data.armed ? "armed" : "not armed"}</span></b><span>escalation contact</span></div>
        <div className="stat"><b><span className={`pill ${data.classifier_enabled ? "ok" : "off"}`}>{data.classifier_enabled ? "on" : "off"}</span></b><span>crisis classifier</span></div>
      </div>
      {!data.armed && (
        <p className="hint">No designated contact is configured (CEREBROZEN_CRISIS_ESCALATION_URL). A person in crisis still receives their helpline reply — but no human is notified. A silently-unconfigured safety net is worse than an absent one.</p>
      )}
      {/* NOTE FOR ANYONE EXTENDING THIS TABLE: the reference's admin has an "Excerpt"
          column here showing the flagged journal/chat text. Do not port it, and do not add
          a "notes"/"outcome" free-text box either — that is the same leak wearing a
          different hat. The row is: who tripped the screen, in which session, whether the
          contact was reached, and now whether a human has picked it up. Never what was
          said (CLAUDE.md rule 5; test_escalation_records.py asserts the field set). */}
      <table className="table">
        <thead><tr><th>When</th><th>Tenant</th><th>User</th><th>Session</th><th>Detected by</th><th>Contact reached</th><th>Handled</th><th /></tr></thead>
        <tbody>
          {data.escalations.map((e, i) => (
            <tr key={`${e.id}:${i}`}>
              <td className="nowrap">{e.at ? new Date(e.at).toLocaleString() : "—"}</td>
              {/* The queue spans tenants (the engine's operators are the responders), so a
                  row has to say whose it is — otherwise a user id is unattributable and an
                  operator cannot tell which client's programme to follow. Ids are shortened
                  to keep the table on-screen; the full value is on hover (title). */}
              <td>{e.org_id ? <code title={e.org_id}>{e.org_id.slice(0, 8)}</code> : "—"}</td>
              <td>{e.user_id ? <code title={e.user_id}>{e.user_id.slice(0, 8)}</code> : "—"}</td>
              <td>{e.session_id ? <code title={e.session_id}>{e.session_id.slice(0, 8)}</code> : "—"}</td>
              <td>{e.detected_by ?? "—"}</td>
              <td><span className={`pill ${e.delivered ? "ok" : "off"}`}>{e.delivered ? "yes" : "no"}</span></td>
              <td>
                {e.acknowledged_at
                  ? <span className="pill ok" title={`by ${e.acknowledged_by} · ${new Date(e.acknowledged_at).toLocaleString()}`}>
                      {e.acknowledged_by?.slice(0, 8) || "yes"}
                    </span>
                  : <span className="pill off">open</span>}
              </td>
              <td>
                {!e.acknowledged_at && (
                  <button className="ghost" disabled={busy === e.id} onClick={() => ack(e)}>
                    {busy === e.id ? "…" : "resolve"}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.count === 0 && (
        <p className="hint">
          {status === "open"
            ? "Nothing open — every escalation has been picked up."
            : status === "resolved"
              ? "Nothing has been marked handled yet."
              : "No escalations — nothing has tripped the crisis screen."}
        </p>
      )}
      {error && <p className="error">{error}</p>}
      <p className="hint" style={{ marginTop: 10 }}>
        This is a signal, not a record of what anyone said — and &ldquo;handled&rdquo; records
        only who picked it up and when. Who responds, how they are trained, and how fast they
        act is the deployment&rsquo;s programme — not this feature.
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
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState("");
  const load = useCallback(() => {
    engineJson<NudgesResp>("/v1/nudges").then(setData).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  /* The queue was read-only: an operator could see nudges were due and had no way to send
     them. POST /v1/nudges/dispatch has existed and been role-gated the whole time.

     Confirmed, because this REACHES PEOPLE — it is the one control on this console that
     puts a notification on a stranger's phone. The confirm says how many, since "dispatch"
     alone does not tell you whether that is 0 or 400. Verified against the live route
     before wiring: {"armed":false,"due":0,"delivered":0}, and an org_admin gets 403. */
  async function dispatch() {
    const due = data?.count ?? 0;
    if (!window.confirm(
      `Send check-in nudges now?\n\n${due} ${due === 1 ? "person is" : "people are"} due. ` +
      (data?.armed
        ? "They will be notified."
        : "The delivery channel is NOT armed, so nothing will actually reach anyone — this " +
          "will scan and report only."),
    )) return;
    setBusy(true); setError(""); setResult("");
    try {
      const r = await engineApi("/v1/nudges/dispatch", { method: "POST" });
      const out = await r.json().catch(() => null);
      if (!r.ok) throw new Error(out?.detail || `HTTP ${r.status}`);
      // Counts, never content — the same rule the queue itself follows.
      setResult(`Scanned ${out.due} due · delivered ${out.delivered}${out.armed ? "" : " (not armed — nothing sent)"}`);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "dispatch failed");
    } finally {
      setBusy(false);
    }
  }

  if (error && !data) return <Failed msg={error} onRetry={load} />;
  if (!data) return <div className="card"><Skeleton rows={4} /></div>;
  return (
    <div className="card">
      <div className="people-head">
        <p className="panel-status">check-in reminders · signal only · {data.count}</p>
        <button className="ghost" style={{ marginLeft: "auto" }} onClick={dispatch} disabled={busy}>
          {busy ? "Dispatching…" : "Dispatch now"}
        </button>
      </div>
      <div className="stats" style={{ marginBottom: 12 }}>
        <div className="stat"><b><span className={`pill ${data.armed ? "ok" : "off"}`}>{data.armed ? "armed" : "not armed"}</span></b><span>delivery channel</span></div>
      </div>
      {result && <p className="hint"><span className="pill ok">{result}</span></p>}
      {error && <p className="error">{error}</p>}

      {!data.armed && (
        <p className="hint">No delivery endpoint is configured (CEREBROZEN_NUDGE_DELIVERY_URL). The scheduler still computes who&rsquo;s due, but no reminder is sent until a channel is wired.</p>
      )}
      <table className="table">
        <thead><tr><th>When</th><th>User</th><th>Commitments due</th><th>Sessions</th><th>Delivered</th><th>Org</th></tr></thead>
        <tbody>
          {data.nudges.map((n, i) => (
            <tr key={`${n.user_id}:${n.at ?? ""}:${i}`}>
              <td className="nowrap">{n.at ? new Date(n.at).toLocaleString() : "—"}</td>
              <td>{n.user_id ? <code title={n.user_id}>{n.user_id.slice(0, 8)}</code> : "—"}</td>
              <td>{n.due_count ?? "—"}</td>
              <td>{n.session_ids?.length ?? 0}</td>
              <td><span className={`pill ${n.delivered ? "ok" : "off"}`}>{n.delivered ? "yes" : "no"}</span></td>
              <td>{n.org_id ? <code title={n.org_id}>{n.org_id.slice(0, 8)}</code> : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.count === 0 && <p className="empty-state">No nudges yet — nothing is due for a check-in.</p>}
    </div>
  );
}

// Agent flow — the engine's real compiled arc as a full-viewport canvas workspace
// (agents rail · canvas · node inspector), read-only: the graph is compiled in
// build_graph.py and routing is code predicates over typed state. The Prompt workbook
// stays the source of truth for what any node actually says.
function AgentFlow() {
  const [agents, setAgents] = useState<AgentRow[] | null>(null);
  const [error, setError] = useState("");
  const [stage, setStage] = useState<string | null>(null);
  const [detail, setDetail] = useState<NodeDetail | null>(null);

  useEffect(() => {
    engineJson<AgentsResp>("/v1/agents").then((r) => setAgents(r.agents)).catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    setDetail(null);
    if (!stage) return;
    engineJson<NodeDetail>(`/v1/prompts/${stage}`).then(setDetail).catch(() => setDetail(null));
  }, [stage]);

  if (error) return <Failed msg={error} />;
  if (!agents) return <div className="card"><Skeleton rows={4} /></div>;
  return (
    <div className="flow-page">
      <div className="flow-head">
        <span className="hint">
          the governed coaching arc · {agents.length} agents in the arc · routing is
          deterministic (code predicates over typed state) · read-only
        </span>
      </div>

      <div className="flow-3">
        <aside className="flow-rail">
          <div className="rail-lbl">Agents in the arc · {agents.length}</div>
          {agents.map((a) => (
            <button key={a.stage} className={`rail-item ${stage === a.stage ? "active" : ""}`}
              onClick={() => setStage(a.stage)}>
              <span className="ri-name">{a.stage}</span>
              <span className="ri-meta">
                {a.model || "—"} · {(a.size / 1000).toFixed(1)}k
                {!a.enabled && <span className="ri-off">off</span>}
              </span>
            </button>
          ))}
          <p className="rail-note">
            <b>environment</b> (guardrail wrapper, composed into every prompt) and{" "}
            <b>user_context_builder_agent</b> (off-path) are in the workbook but are not
            nodes, so they aren&rsquo;t on the canvas.
          </p>
        </aside>

        <div className="flow-main">
          <AgentFlowCanvas agents={agents} focusStage={stage} onInspect={setStage} />
        </div>

        {stage && (
          <aside className="node-insp">
            <div className="ni-head">
              <div><div className="ni-eyebrow">agent</div><div className="ni-title">{stage}</div></div>
              <button className="ni-x" onClick={() => setStage(null)} aria-label="Close inspector">×</button>
            </div>
            <div className="ni-body">
              {!detail ? <Skeleton rows={3} /> : (
                <>
                  <div className="ni-meta">
                    <span className="pill">{detail.model || "—"}</span>
                    <span className={`pill ${detail.enabled ? "ok" : "off"}`}>{detail.enabled ? "enabled" : "disabled"}</span>
                    {detail.always_on && <span className="pill">always-on</span>}
                    {detail.size != null && <span className="hint">{detail.size.toLocaleString()} ch</span>}
                  </div>
                  <p className="hint" style={{ margin: "10px 0" }}>
                    Read-only here — the <b>Prompt workbook</b> is the source of truth. Edit it there
                    and this node uses the new content on its next run.
                  </p>
                  <pre className="prompt ni-prompt">{detail.prompt}</pre>
                </>
              )}
            </div>
          </aside>
        )}
      </div>
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
      <p className="panel-status">test any prompt against the live model</p>
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

  /* Seven pills in one undifferentiated row asked the operator to hold three unrelated
     jobs in their head at once. They are not unrelated to each OTHER — they are three
     jobs, and which one you are doing decides which tabs you care about:

       Customers  — who is on the platform, and who wants to be
       Coaching   — the workbook, the compiled arc, and a place to try one
       Queues     — the two things that need a human, and drain

     The HR side (org_admin) keeps a flat row: four tabs, one job, and a group label on
     every one of them would be noise dressed as structure. */
  const groups: [string, string[][]][] =
    me.role === "internal_admin"
      ? [
          ["Customers", [["tenants", "Tenants"], ["demos", "Demo requests"]]],
          ["Coaching", [["prompts", "Prompt workbook"], ["flow", "Agent flow"], ["console", "Console"]]],
          ["Queues", [["safety", "Safety queue"], ["nudges", "Nudges"]]],
        ]
      : [["", [["overview", "Overview"], ["analytics", "Analytics"], ["people", "People"], ["invite", "Invite"]]]];

  // The current section's title + its group, for the main-column header bar.
  const current = groups
    .flatMap(([group, items]) => items.map(([id, label]) => ({ id, label, group })))
    .find((s) => s.id === tab);

  return (
    // Sidebar + main. The agent flow is a canvas workspace, not a document — `wide`
    // drops the main column's reading width so the graph gets the full viewport.
    <div className={`console ${tab === "flow" ? "wide" : ""}`}>
      <aside className="sidebar">
        <a className="wordmark home" href={SITE_URL} title="cerebrozen.in">
          CereBr<em>o</em>Zen<span className="wm-sub"> · admin</span>
        </a>
        <nav className="side-nav">
          {groups.map(([group, items]) => (
            <div className="nav-group" key={group || "all"}>
              {group && <span className="nav-group-label">{group}</span>}
              {items.map(([id, label]) => (
                <button
                  key={id}
                  className={`nav-item ${tab === id ? "active" : ""}`}
                  onClick={() => setTab(id)}
                >
                  {label}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="side-foot">
          <span className="who-side">
            {me.email}
            <small>{me.role}{me.org_name ? ` · ${me.org_name}` : ""}</small>
          </span>
          <button className="ghost" onClick={async () => { await logout(); setMe(null); }}>Sign out</button>
        </div>
      </aside>
      <main className="main">
        <div className="main-head">
          {current?.group && <span className="main-eyebrow">{current.group}</span>}
          <h1 className="main-title">{current?.label ?? ""}</h1>
        </div>
        {tab === "overview" && <OrgOverview />}
        {tab === "analytics" && <OrgAnalytics />}
        {tab === "people" && <People me={me} />}
        {tab === "invite" && <Invite />}
        {tab === "tenants" && <Tenants />}
        {tab === "demos" && <Demos />}
        {tab === "prompts" && <PromptWorkbook />}
        {tab === "flow" && <AgentFlow />}
        {tab === "safety" && <SafetyQueue />}
        {tab === "nudges" && <Nudges />}
        {tab === "console" && <Console />}
      </main>
    </div>
  );
}
