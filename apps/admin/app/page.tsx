"use client";

import { useEffect, useId, useState } from "react";
import { API_URL, ApiError, api, clearToken, hasSession, login, logout, setToken, upload } from "@/lib/api";
import { BrandMark, Icon } from "@/components/icons";

type Tab = "overview" | "analytics" | "users" | "content" | "media" | "prompts" | "oracle" | "nudges" | "safety" | "waitlist";

const TABS: { key: Tab; label: string }[] = [
  { key: "overview", label: "Overview" },
  { key: "analytics", label: "Analytics" },
  { key: "users", label: "Users" },
  { key: "content", label: "Content" },
  { key: "media", label: "Media" },
  { key: "prompts", label: "Prompts" },
  { key: "oracle", label: "Oracle" },
  { key: "nudges", label: "Nudges" },
  { key: "safety", label: "Safety" },
  { key: "waitlist", label: "Waitlist" },
];

function fmtDate(s: string) {
  // `new Date("garbage")` never throws — it returns Invalid Date, so the old
  // try/catch was dead code and malformed input printed "Invalid Date"
  // instead of the raw value (register E64).
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

export default function AdminPage() {
  const [authed, setAuthed] = useState(false);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>("overview");
  // Narrow viewports get an off-canvas drawer; ≥900px the CSS ignores this.
  const [navOpen, setNavOpen] = useState(false);

  useEffect(() => {
    // The access token is memory-only now, so a reload never has one — a
    // stored refresh token is what says "signed in"; the first API call
    // rotates it into a fresh access token.
    setAuthed(hasSession());
    setReady(true);
  }, []);

  useEffect(() => {
    if (!navOpen) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setNavOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [navOpen]);

  if (!ready) return null;
  if (!authed) return <Login onAuthed={() => setAuthed(true)} />;

  const current = TABS.find((t) => t.key === tab);

  return (
    <>
      <div className="navbar">
        <button
          className="iconbtn"
          aria-label={navOpen ? "Close navigation" : "Open navigation"}
          aria-expanded={navOpen}
          onClick={() => setNavOpen((o) => !o)}
        >
          {navOpen ? <Icon.close /> : <Icon.menu />}
        </button>
        <div className="brand">
          <BrandMark size={22} /> {current?.label ?? "CereBro"}
        </div>
      </div>
      {navOpen && (
        <button className="scrim" aria-label="Close navigation" onClick={() => setNavOpen(false)} />
      )}
      <div className="shell">
        <aside className={`sidebar ${navOpen ? "open" : ""}`}>
        <div className="brand">
          <BrandMark size={26} /> CereBro
        </div>
        {TABS.map((t) => {
          // Fallback glyph: a missing icon must never take down the dashboard.
          const I = Icon[t.key] ?? Icon.overview;
          return (
            <button
              key={t.key}
              className={`navitem ${tab === t.key ? "active" : ""}`}
              aria-current={tab === t.key ? "page" : undefined}
              onClick={() => { setTab(t.key); setNavOpen(false); }}
            >
              <I /> {t.label}
            </button>
          );
        })}
        <button
          className="navitem logout"
          onClick={async () => {
            // Register E37: clearToken() only wiped memory + localStorage,
            // so a refresh token lifted earlier stayed valid after the
            // operator "signed out". POST /auth/logout revokes it
            // server-side; a failed revoke must still sign them out here.
            await logout();
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
        {tab === "media" && <Media />}
        {tab === "prompts" && <PromptsTab />}
        {tab === "oracle" && <OracleTab />}
        {tab === "nudges" && <NudgesTab />}
        {tab === "safety" && <Safety />}
        {tab === "waitlist" && <WaitlistTab />}
      </main>
      </div>
    </>
  );
}

// Dev-only convenience: prefill + hint for the locally seeded admin account.
// Every condition below is inlined at build time, so a production bundle can't
// contain the credentials at all — and no single flag being wrong is enough to
// leak them. All three must agree that this is a local/dev build:
//   1. a non-production NODE_ENV,
//   2. the API pointed at a loopback/dev host (never a real domain),
//   3. no explicit opt-out (NEXT_PUBLIC_HIDE_DEV_LOGIN=1 for demo builds).
const DEV_API_HOSTS = ["localhost", "127.0.0.1", "0.0.0.0", "api", "host.docker.internal"];
const SHOW_DEV_CREDENTIALS = (() => {
  if (process.env.NODE_ENV === "production") return false;
  if (process.env.NEXT_PUBLIC_HIDE_DEV_LOGIN === "1") return false;
  try {
    const host = new URL(process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000").hostname;
    return DEV_API_HOSTS.includes(host) || host.endsWith(".local");
  } catch {
    return false;
  }
})();

function Login({ onAuthed }: { onAuthed: () => void }) {
  const uid = useId();
  const [email, setEmail] = useState(SHOW_DEV_CREDENTIALS ? "admin@cerebro.app" : "");
  const [password, setPassword] = useState(SHOW_DEV_CREDENTIALS ? "admin12345" : "");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr("");
    try {
      const token = await login(email, password);
      setToken(token);
      // Register E38: `/auth/login` is the shared user endpoint, so a valid
      // NON-admin credential used to "sign in" to a shell where every call
      // 403s and the session ends with a misleading message. Check the role
      // at the door and say what's actually true.
      const me = await api<any>("/auth/me");
      if (!me?.is_admin) {
        clearToken();
        setErr("That account signed in, but it isn't an admin account.");
        return;
      }
      onAuthed();
    } catch (e: any) {
      // A down backend is not a wrong password — say which one it was.
      if (e instanceof ApiError && e.kind === "offline") {
        setErr("We couldn't reach the server. Your details are probably fine — try again in a moment.");
      } else if (e instanceof ApiError && e.kind !== "unauthorized") {
        setErr("The server had trouble signing you in. Try again shortly.");
      } else {
        setErr("That email and password don't match an account.");
      }
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
          <label htmlFor={`${uid}-email`}>Email</label>
          <input
            id={`${uid}-email`}
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor={`${uid}-password`}>Password</label>
          <input
            id={`${uid}-password`}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <button className="btn btn-primary" style={{ width: "100%" }} disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
        <div className="err" role="alert">{err}</div>
        {SHOW_DEV_CREDENTIALS && <div className="hint">Seeded admin: admin@cerebro.app / admin12345</div>}
      </form>
    </div>
  );
}

function useData<T>(loader: () => Promise<T>, deps: any[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [err, setErr] = useState<ApiError | null>(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    // Ignore a stale in-flight response when deps change (e.g. a slow unfiltered
    // fetch resolving after a search) so the newest request always wins.
    let ignore = false;
    loader()
      .then((d) => { if (!ignore) { setData(d); setErr(null); } })
      .catch((e) => {
        if (ignore) return;
        setErr(e instanceof ApiError ? e : new ApiError("request", String(e?.message || e)));
      });
    return () => { ignore = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  // `loading` distinguishes in-flight from empty (register E47): headers used
  // to assert "0 shown" / "0 signups" while the request was still out.
  return { data, err, loading: data === null && err === null, reload: () => setTick((t) => t + 1) };
}

/** The one place a failed load is explained. Plain words, and a way forward. */
function Problem({ err, onRetry }: { err: ApiError | null; onRetry?: () => void }) {
  if (!err) return null;
  const expired = err.kind === "unauthorized";
  const offline = err.kind === "offline";
  return (
    <div className="state" role="status">
      <strong>
        {expired ? "Your session ended" : offline ? "We can't reach the server" : "The server had trouble with that"}
      </strong>
      <p>
        {expired
          ? "Sign in again to pick up where you left off."
          : offline
            ? "Nothing loaded because the CereBro API didn't answer. It may be restarting, or this machine is offline."
            : `The request came back as an error${err.status ? ` (${err.status})` : ""}. Nothing was changed.`}
      </p>
      {expired ? (
        <button className="btn btn-ghost btn-sm" type="button" onClick={() => window.location.reload()}>
          Sign in again
        </button>
      ) : (
        onRetry && (
          <button className="btn btn-ghost btn-sm" type="button" onClick={onRetry}>
            Try again
          </button>
        )
      )}
    </div>
  );
}

/** Honest empty state — shown only when the load succeeded and returned nothing. */
function Empty({ children }: { children: React.ReactNode }) {
  return <div className="empty">{children}</div>;
}

/** Live character budget for the length-capped fields. */
function Counter({ value, max, id }: { value: string; max: number; id?: string }) {
  const used = value.length;
  return (
    <span className={`counter ${used > max * 0.9 ? "near" : ""}`} id={id}>
      {used} / {max} characters
    </span>
  );
}

/** A short, human message for an action that failed mid-flight. */
function actionMessage(e: unknown, fallback: string) {
  if (e instanceof ApiError) {
    if (e.kind === "offline") return "We couldn't reach the server — nothing was changed.";
    if (e.kind === "unauthorized") return "Your session ended — reload to sign in again.";
  }
  return fallback;
}

function Overview() {
  const { data, err, reload } = useData<Record<string, number>>(() => api("/admin/stats"));
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
      setNudgeMsg(actionMessage(e, "Dispatch didn't go through — try again."));
    } finally {
      setNudgeBusy(false);
    }
  }

  return (
    <>
      <h1 className="page-title serif">Overview</h1>
      <div className="page-sub">Platform at a glance</div>
      <Problem err={err} onRetry={reload} />
      <div className="stats">
        {cards.map((c) => (
          <div className="stat" key={c.k}>
            <div className="n">{data ? data[c.k] ?? 0 : "—"}</div>
            <div className="l">{c.l}</div>
          </div>
        ))}
      </div>
      <div className="toolbar inline-actions stack">
        <button className="btn btn-primary" onClick={dispatchNudges} disabled={nudgeBusy}>
          {nudgeBusy ? "Dispatching…" : "Dispatch due nudges"}
        </button>
        {nudgeMsg && <span className="page-sub flush">{nudgeMsg}</span>}
      </div>
    </>
  );
}

// Display names for the anonymous onboarding steps (backend
// services/metrics.py ONBOARDING_STEPS — keep the two lists in step).
// Written as what a person did, not as the event key.
const ONBOARDING_LABELS: Record<string, string> = {
  welcome: "Opened the app",
  age_gate: "Confirmed their age",
  disclosure: "Read the AI disclosure",
  language: "Chose a language",
  state_check: "Said how they were doing",
  first_reset: "Tried a first breathing reset",
  first_plan: "Saw their starting plan",
  signup: "Created an account",
  consent: "Made their consent choices",
  notifications: "Answered the notifications ask",
};

const ENGAGEMENT_LABELS: Record<string, string> = {
  mood_logs: "Mood check-ins",
  journal_entries: "Journal entries",
  chat_messages: "Messages sent to the companion",
  sleep_logs: "Sleep logs",
  plan_steps_done: "Plan steps completed",
};

function label(map: Record<string, string>, key: string) {
  return map[key] ?? key.replace(/_/g, " ");
}

/** One labelled bar in a funnel. */
function Meter({ label: text, n, max }: { label: string; n: number; max: number }) {
  return (
    <div className="meter-row">
      <div className="meter-label">
        <span>{text}</span>
        <span className="mono">{n}</span>
      </div>
      <div className="meter">
        <i style={{ width: `${(n / max) * 100}%` }} />
      </div>
    </div>
  );
}

function Analytics() {
  const { data, err, reload } = useData<any>(() => api("/admin/metrics/overview"));
  // Register E48: destructuring only `data` swallowed a failed funnel fetch —
  // the panel silently vanished with no message or retry.
  const { data: onb, err: onbErr, reload: reloadOnb } = useData<any>(() => api("/admin/metrics/funnel?days=30"));
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
      <Problem err={err} onRetry={reload} />
      {data && (
        <>
          <div className="stats">
            <div className="stat"><div className="n">{data.actives.dau}</div><div className="l">Active today</div></div>
            <div className="stat"><div className="n">{data.actives.wau}</div><div className="l">Active 7d</div></div>
            <div className="stat"><div className="n">{data.actives.mau}</div><div className="l">Active 30d</div></div>
            <div className="stat"><div className="n">{data.signups.d7}</div><div className="l">Signups 7d</div></div>
            <div className="stat"><div className="n">{data.signups.total}</div><div className="l">Accounts</div></div>
          </div>

          <div className="panel">
            <h3 className="serif">Retention (signup cohorts, last 35 days)</h3>
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

          <div className="panel">
            <h3 className="serif">Activation funnel (lifetime)</h3>
            {funnelSteps.map((s) => (
              <Meter key={s.l} label={s.l} n={s.n} max={funnelMax} />
            ))}
          </div>

          {onb && (
            <div className="panel">
              <h3 className="serif" style={{ marginBottom: 4 }}>Onboarding funnel (30 days)</h3>
              <div className="note">
                Unique installs per step — anonymous events, never linked to accounts.
                Completed: <span className="mono">{onb.completed}</span> · Paywall views:{" "}
                <span className="mono">{onb.paywall_views}</span> · Paywall taps:{" "}
                <span className="mono">{onb.paywall_taps}</span>
              </div>
              {onb.steps.map((s: any) => (
                <Meter key={s.step} label={label(ONBOARDING_LABELS, s.step)} n={s.installs} max={onbMax} />
              ))}
            </div>
          )}
          {onbErr && (
            <div className="panel">
              <h3 className="serif" style={{ marginBottom: 4 }}>Onboarding funnel (30 days)</h3>
              <div className="note">The funnel didn&apos;t load — the numbers above are unaffected.</div>
              <button className="btn btn-ghost btn-sm" onClick={reloadOnb}>Try again</button>
            </div>
          )}

          <div className="panel">
            <h3 className="serif">Engagement, trailing 7 days</h3>
            <table>
              <tbody>
                {Object.entries(data.engagement_7d).map(([k, v]) => (
                  <tr key={k}><td>{label(ENGAGEMENT_LABELS, k)}</td><td className="mono">{String(v)}</td></tr>
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
  const { data, err, reload } = useData<any>(() => api(`/admin/users/${id}`), [id]);
  return (
    <div className="panel">
      <div className="panel-head">
        <h3 className="serif">Account details</h3>
        <button className="btn btn-ghost btn-sm" onClick={onClose}>Close</button>
      </div>
      <div className="page-sub" style={{ marginTop: 4 }}>
        Counts and account state only — journal, chat, and sleep contents never leave the user&apos;s space.
      </div>
      <Problem err={err} onRetry={reload} />
      {data && (
        <table>
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

const USERS_PAGE = 50;

function Users() {
  const [q, setQ] = useState("");
  // Real offset pagination (register E46): "Load more" used to grow `limit`
  // and refetch the entire result set from row zero — each click
  // re-transferred everything already on screen.
  const [data, setData] = useState<any[] | null>(null);
  const [err, setErr] = useState<ApiError | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [busyMore, setBusyMore] = useState(false);
  const loading = data === null && err === null;

  function page(offset: number, limit = USERS_PAGE): Promise<any[]> {
    return api(`/admin/users?q=${encodeURIComponent(q)}&limit=${limit}&offset=${offset}`);
  }
  const asApiError = (e: any) =>
    e instanceof ApiError ? e : new ApiError("request", String(e?.message || e));

  useEffect(() => {
    let ignore = false;
    setData(null);
    setErr(null);
    page(0)
      .then((d) => { if (!ignore) { setData(d); setHasMore(d.length >= USERS_PAGE); } })
      .catch((e) => { if (!ignore) setErr(asApiError(e)); });
    return () => { ignore = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  async function loadMore() {
    if (!data || busyMore) return;
    setBusyMore(true);
    try {
      const d = await page(data.length);
      setData([...data, ...d]);
      setHasMore(d.length >= USERS_PAGE);
    } catch (e) {
      setErr(asApiError(e));
    } finally {
      setBusyMore(false);
    }
  }

  // Refresh every row currently on screen (after enable/disable/nudge).
  async function reload() {
    const count = Math.max(data?.length ?? 0, USERS_PAGE);
    try {
      const d = await page(0, count);
      setData(d);
      setHasMore(d.length >= count);
      setErr(null);
    } catch (e) {
      setErr(asApiError(e));
    }
  }

  const [selected, setSelected] = useState<string | null>(null);
  const [nudgeUser, setNudgeUser] = useState<any | null>(null);
  // Revoking access is destructive: it needs a second step and a reason.
  const [disabling, setDisabling] = useState<any | null>(null);

  const [activeErr, setActiveErr] = useState("");
  async function setActive(u: any, active: boolean) {
    setActiveErr("");
    try {
      await api(`/admin/users/${u.id}/active?active=${active}`, { method: "PATCH" });
    } catch {
      // Register E49: an unhandled rejection left the row untouched with no
      // explanation.
      setActiveErr(`Couldn't ${active ? "enable" : "disable"} ${u.email} — nothing changed. Try again.`);
    }
    reload();
  }

  return (
    <>
      <h1 className="page-title serif">Users</h1>
      <div className="page-sub">
        {loading ? "Loading…" : `${data?.length ?? 0} shown${q ? ` · matching "${q}"` : ""}`}
      </div>
      <div className="toolbar" style={{ marginBottom: 12 }}>
        <div className="search">
          <Icon.search />
          <input
            placeholder="Search by email, name, or user id…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
      </div>
      {activeErr && <div className="err" role="alert">{activeErr}</div>}
      <Problem err={err} onRetry={reload} />
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
                    {u.is_active ? (
                      <button className="btn btn-danger btn-sm" onClick={() => setDisabling(u)}>Disable</button>
                    ) : (
                      <button className="btn btn-ghost btn-sm" onClick={() => setActive(u, true)}>Enable</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && !err && (
          <Empty>{q ? `No account matches "${q}".` : "No accounts yet."}</Empty>
        )}
        {data && hasMore && (
          <div style={{ padding: 12, textAlign: "center" }}>
            <button className="btn btn-ghost btn-sm" disabled={busyMore} onClick={loadMore}>
              {busyMore ? "Loading…" : "Load more"}
            </button>
          </div>
        )}
      </div>
      {disabling && (
        <DisableUserPanel
          user={disabling}
          onCancel={() => setDisabling(null)}
          onDone={() => { setDisabling(null); reload(); }}
        />
      )}
      {selected && <UserDetail id={selected} onClose={() => setSelected(null)} />}
      {nudgeUser && <NudgeUserModal user={nudgeUser} onClose={() => setNudgeUser(null)} />}
    </>
  );
}

// Step two of disabling an account: name the reason, then confirm. The reason
// is sent alongside the PATCH so it can be recorded once the backend keeps an
// admin action log (see report — the endpoint currently ignores the body).
function DisableUserPanel({ user, onCancel, onDone }: { user: any; onCancel: () => void; onDone: () => void }) {
  const uid = useId();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  async function confirm() {
    setBusy(true);
    setMsg("");
    try {
      await api(`/admin/users/${user.id}/active?active=false`, {
        method: "PATCH",
        body: JSON.stringify({ reason: reason.trim() }),
      });
      onDone();
    } catch (e) {
      setMsg(actionMessage(e, "That didn't go through — the account is unchanged."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3 className="serif">Disable <span className="mono">{user.email}</span>?</h3>
        <button className="btn btn-ghost btn-sm" onClick={onCancel}>Cancel</button>
      </div>
      <div className="warn">
        <strong>They&apos;ll be signed out and locked out.</strong>
        Their data stays untouched, and you can re-enable the account at any time.
      </div>
      <div className="mt-3">
        <label htmlFor={`${uid}-reason`}>Why are you disabling this account?</label>
        <input
          id={`${uid}-reason`}
          type="text"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          maxLength={255}
          placeholder="e.g. account holder asked us to pause access"
        />
        <Counter value={reason} max={255} />
      </div>
      <div className="inline-actions mt-3">
        <button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={confirm}>
          {busy ? "Disabling…" : "Yes, disable access"}
        </button>
        <button className="btn btn-ghost" onClick={onCancel}>Keep access</button>
        {!reason.trim() && <span className="page-sub flush">A reason is required.</span>}
        {msg && <span className="page-sub flush">{msg}</span>}
      </div>
    </div>
  );
}

function NudgeUserModal({ user, onClose }: { user: any; onClose: () => void }) {
  const uid = useId();
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
    } catch (e: any) {
      setMsg(actionMessage(e, "That didn't queue — try again."));
    } finally { setBusy(false); }
  }
  return (
    <div className="panel">
      <div className="panel-head">
        <h3 className="serif">Nudge <span className="mono">{user.email}</span></h3>
        <button className="btn btn-ghost btn-sm" onClick={onClose}>Close</button>
      </div>
      <form className="cform" onSubmit={send} style={{ marginTop: 8, padding: 0 }}>
        <div className="full">
          <label htmlFor={`${uid}-title`}>Title</label>
          <input
            id={`${uid}-title`}
            type="text"
            aria-describedby={`${uid}-title-count`}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            maxLength={160}
          />
          <Counter id={`${uid}-title-count`} value={title} max={160} />
        </div>
        <div className="full">
          <label htmlFor={`${uid}-body`}>Body</label>
          <input
            id={`${uid}-body`}
            type="text"
            aria-describedby={`${uid}-body-count`}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            required
            maxLength={500}
          />
          <Counter id={`${uid}-body-count`} value={body} max={500} />
        </div>
        <div className="full inline-actions">
          <button className="btn btn-primary" disabled={busy || !title.trim() || !body.trim()}>{busy ? "Queuing…" : "Queue nudge"}</button>
          {msg && <span className="page-sub flush">{msg}</span>}
        </div>
      </form>
    </div>
  );
}

const KINDS = ["sleep", "meditation", "breath", "soundscape", "program", "wind_down"];

// The icon the apps draw in the artwork well when an item has no image.
// Values are SF Symbol names (iOS renders them directly); the list mirrors the
// symbols already used by the seeded catalogue. Any name the backend already
// holds is kept selectable even if it isn't listed here.
const SYMBOLS: { value: string; label: string }[] = [
  { value: "sparkles", label: "Sparkles (default)" },
  { value: "moon.stars", label: "Moon and stars" },
  { value: "moon.zzz", label: "Moon asleep" },
  { value: "moon.haze", label: "Moon in haze" },
  { value: "sun.max", label: "Sun" },
  { value: "waveform", label: "Waveform" },
  { value: "wind", label: "Wind" },
  { value: "leaf", label: "Leaf" },
  { value: "brain", label: "Brain" },
  { value: "scope", label: "Focus ring" },
  { value: "alarm", label: "Alarm clock" },
  { value: "bed.double", label: "Bed" },
  { value: "figure.mind.and.body", label: "Mind and body" },
  { value: "heart", label: "Heart" },
  { value: "drop", label: "Water drop" },
  { value: "book", label: "Book" },
];

const EMPTY_CONTENT = {
  title: "", subtitle: "", kind: "meditation", symbol: "sparkles",
  image_url: "", duration_min: 0, premium: false, published: true,
  narration_script: "", day_guides: [] as { title: string; body: string }[],
};

// ── Media catalogue ─────────────────────────────────────────────────────
// Every sound and video the clients can play, addressed by a stable key. The
// clients ship with a bundled loop or a synthesized tone for each key, so a row
// with an empty `url` is NOT broken — it means "no server asset yet, the app is
// playing its own fallback". Uploading here replaces that sound for every user
// on their next launch, with no app release. `scene.*` is the exception worth
// calling out: there is no such thing as a synthesized video, so those keys are
// genuinely silent-until-uploaded (the clients draw their generative artwork).
const MEDIA_KIND_HELP: Record<string, string> = {
  ambience: "Looping bed. Empty ⇒ the app plays its bundled loop.",
  breathe: "Breath phase cue. Empty ⇒ the app plays its synthesized glide.",
  game: "Toolkit activity sound. Empty ⇒ the app plays its synthesized tone.",
  chime: "One-shot chime. Empty ⇒ the app plays its synthesized bell.",
  scene: "Looping background video. Empty ⇒ no video plays (the app draws its aurora instead).",
};

const MEDIA_ACCEPT = ".mp3,.m4a,.ogg,.wav,.mp4,.webm";

function Media() {
  const { data, err, reload } = useData<any[]>(() => api("/admin/media"));
  const [busyId, setBusyId] = useState<string | null>(null);
  const [msg, setMsg] = useState("");

  async function onPick(asset: any, file: File | undefined) {
    if (!file) return;
    setBusyId(asset.id);
    setMsg("");
    try {
      await upload(`/admin/media/${asset.id}/upload`, file);
      setMsg(`Uploaded ${file.name} → ${asset.key}. Clients pick it up on next launch.`);
      reload();
    } catch (e: any) {
      setMsg(String(e?.message || e));
    } finally {
      setBusyId(null);
    }
  }

  async function clearAsset(asset: any) {
    setBusyId(asset.id);
    try {
      // Point the key back at nothing: the clients fall straight back to their
      // bundled/synthesized sound. (Deleting the row would remove the key itself,
      // which is a schema change, not a content one — hence "Clear", not "Delete".)
      await api(`/admin/media/${asset.id}`, {
        method: "PATCH",
        body: JSON.stringify({ url: "", mime: "" }),
      });
      setMsg(`Cleared ${asset.key} — the app is back on its built-in sound.`);
      reload();
    } catch (e) {
      // Register E50: a failed clear was silent — busy flag reset, nothing said.
      setMsg(actionMessage(e, `Couldn't clear ${asset.key} — it still points at the upload.`));
    } finally {
      setBusyId(null);
    }
  }

  const rows = data ?? [];
  const filled = rows.filter((a: any) => a.url).length;
  const groups = Array.from(new Set(rows.map((a: any) => a.kind)));

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title serif">Media catalogue</h1>
          <div className="page-sub" style={{ marginBottom: 0 }}>
            {filled} of {rows.length} keys have an uploaded asset — the rest play the
            app&apos;s built-in sound, which is normal.
          </div>
        </div>
      </div>

      <Problem err={err} onRetry={reload} />
      {msg && <div className="card" style={{ marginBottom: 12 }}>{msg}</div>}

      {groups.map((kind) => (
        <div key={String(kind)} className="card" style={{ marginBottom: 14 }}>
          <div style={{ marginBottom: 10 }}>
            <strong className="serif" style={{ fontSize: 16, textTransform: "capitalize" }}>{String(kind)}</strong>
            <div className="page-sub" style={{ marginBottom: 0 }}>{MEDIA_KIND_HELP[String(kind)] || ""}</div>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Title</th>
                <th>Asset</th>
                <th style={{ textAlign: "right" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.filter((a: any) => a.kind === kind).map((a: any) => {
                const busy = busyId === a.id;
                const isVideo = String(a.mime).startsWith("video/");
                return (
                  <tr key={a.id}>
                    <td><code>{a.key}</code></td>
                    <td>{a.title}</td>
                    <td>
                      {a.url ? (
                        isVideo ? (
                          <video src={`${API_URL}${a.url}`} muted loop playsInline controls style={{ maxWidth: 200, borderRadius: 6 }} />
                        ) : (
                          <audio src={`${API_URL}${a.url}`} controls preload="none" style={{ maxWidth: 220 }} />
                        )
                      ) : (
                        <span className="page-sub">
                          {kind === "scene" ? "No video — the app draws its aurora" : "Using the app's built-in sound"}
                        </span>
                      )}
                    </td>
                    <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                      <label className="btn" style={{ cursor: busy ? "wait" : "pointer" }}>
                        {busy ? "Uploading…" : a.url ? "Replace" : "Upload"}
                        <input
                          type="file"
                          accept={MEDIA_ACCEPT}
                          disabled={busy}
                          style={{ display: "none" }}
                          onChange={(e) => { onPick(a, e.target.files?.[0]); e.target.value = ""; }}
                        />
                      </label>
                      {a.url && (
                        <button className="btn" disabled={busy} onClick={() => clearAsset(a)} style={{ marginLeft: 8 }}>
                          Clear
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ))}

      {rows.length === 0 && !err && (
        <div className="card">
          No catalogue keys. They are seeded on backend boot — check that the API started.
        </div>
      )}
    </>
  );
}

function Content() {
  const uid = useId();
  const { data, err, loading, reload } = useData<any[]>(() => api("/admin/content"));
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<any>(EMPTY_CONTENT);
  const [editId, setEditId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [narratingId, setNarratingId] = useState<string | null>(null);
  const [audioMsg, setAudioMsg] = useState("");
  // Deleting is permanent, so it takes two deliberate clicks.
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [deleteMsg, setDeleteMsg] = useState("");
  // Register E49: save and the publish/premium toggles rejected unhandled —
  // zero feedback, the form/row frozen in its pre-action state.
  const [saveMsg, setSaveMsg] = useState("");

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
    setSaveMsg("");
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
    } catch (e) {
      setSaveMsg(actionMessage(e, "That didn't save — your edits are still in the form."));
    } finally {
      setBusy(false);
    }
  }
  async function patch(id: string, body: any) {
    setSaveMsg("");
    try {
      await api(`/admin/content/${id}`, { method: "PATCH", body: JSON.stringify(body) });
    } catch (e) {
      setSaveMsg(actionMessage(e, "That change didn't apply — the item is unchanged."));
    }
    reload();
  }
  async function remove(id: string) {
    setDeleteMsg("");
    try {
      await api(`/admin/content/${id}`, { method: "DELETE" });
      setConfirmDelete(null);
      reload();
    } catch (e) {
      setDeleteMsg(actionMessage(e, "That didn't delete — the item is still here."));
    }
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
          ? "Audio narration isn't configured yet — no text-to-speech service is connected."
          : "The narration didn't generate — try again."
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
          <div className="page-sub flush">{loading ? "Loading…" : `${data?.length ?? 0} items`}</div>
        </div>
        <button className="btn btn-primary" onClick={() => (showForm ? closeForm() : setShowForm(true))}>
          {showForm ? "Close" : "+ New item"}
        </button>
      </div>

      {showForm && (
        <form className="card cform" onSubmit={save}>
          <div className="full" style={{ marginBottom: -2 }}>
            <strong className="serif ttl">{editId ? "Edit item" : "New item"}</strong>
          </div>
          <div className="full">
            <label htmlFor={`${uid}-title`}>Title</label>
            <input id={`${uid}-title`} type="text" value={form.title} onChange={(e) => set("title", e.target.value)} required />
          </div>
          <div className="full">
            <label htmlFor={`${uid}-subtitle`}>Subtitle</label>
            <input id={`${uid}-subtitle`} type="text" value={form.subtitle} onChange={(e) => set("subtitle", e.target.value)} />
          </div>
          <div>
            <label htmlFor={`${uid}-kind`}>Kind</label>
            <select id={`${uid}-kind`} value={form.kind} onChange={(e) => set("kind", e.target.value)}>
              {KINDS.map((k) => <option key={k} value={k}>{k}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor={`${uid}-duration`}>Duration (min)</label>
            <input id={`${uid}-duration`} type="number" min={0} value={form.duration_min} onChange={(e) => set("duration_min", e.target.value)} />
            <small className="field-hint">
              Generating audio overwrites this with the real length of the MP3 —
              for a narrated item the file is the content, so the number clients
              show has to match it.
            </small>
          </div>
          <div>
            <label htmlFor={`${uid}-symbol`}>Icon</label>
            <select id={`${uid}-symbol`} value={form.symbol} onChange={(e) => set("symbol", e.target.value)}>
              {!SYMBOLS.some((s) => s.value === form.symbol) && form.symbol && (
                <option value={form.symbol}>{form.symbol} (current)</option>
              )}
              {SYMBOLS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
            <div className="note tight" style={{ marginTop: 6 }}>
              Drawn in the artwork well when the item has no image.
            </div>
          </div>
          <div>
            <label htmlFor={`${uid}-image`}>Image URL</label>
            <input id={`${uid}-image`} type="text" value={form.image_url} onChange={(e) => set("image_url", e.target.value)} placeholder="https://…" />
            <div className="note tight" style={{ marginTop: 6 }}>Optional — leave empty to use the icon.</div>
          </div>
          <div className="full">
            <label htmlFor={`${uid}-script`}>Narration script</label>
            <textarea
              id={`${uid}-script`}
              rows={6}
              value={form.narration_script}
              onChange={(e) => set("narration_script", e.target.value)}
              placeholder="Read aloud by the narrator voice — keep it calm and non-clinical. Leave empty for ambient-only items."
            />
          </div>
          <div className="full" role="group" aria-labelledby={`${uid}-guides-label`}>
            <div className="flabel" id={`${uid}-guides-label`}>Day guides (programs)</div>
            <div className="note tight">
              One guide per program day — day N of an enrollment reads guide N. Leave empty for non-programs.
            </div>
            {form.day_guides.map((g: any, i: number) => (
              <div key={i} className="card" style={{ padding: 12, marginBottom: 10 }}>
                <div className="row-between" style={{ marginBottom: 6 }}>
                  <span className="tag muted">Day {i + 1}</span>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => removeGuide(i)}>Remove</button>
                </div>
                <label htmlFor={`${uid}-guide-${i}-title`}>Title</label>
                <input
                  id={`${uid}-guide-${i}-title`}
                  type="text"
                  aria-describedby={`${uid}-guide-${i}-title-count`}
                  value={g.title}
                  onChange={(e) => setGuide(i, "title", e.target.value)}
                  placeholder="Day title"
                  required
                  maxLength={160}
                />
                <Counter id={`${uid}-guide-${i}-title-count`} value={g.title} max={160} />
                <label htmlFor={`${uid}-guide-${i}-body`} style={{ marginTop: 8 }}>Body</label>
                <textarea
                  id={`${uid}-guide-${i}-body`}
                  rows={3}
                  value={g.body}
                  onChange={(e) => setGuide(i, "body", e.target.value)}
                  placeholder="What this day asks of the user — calm, one focus."
                />
              </div>
            ))}
            <button type="button" className="btn btn-ghost btn-sm" onClick={addGuide}>+ Add day</button>
          </div>
          <label className="check" htmlFor={`${uid}-premium`}>
            <input id={`${uid}-premium`} type="checkbox" checked={form.premium} onChange={(e) => set("premium", e.target.checked)} /> Premium
          </label>
          <label className="check" htmlFor={`${uid}-published`}>
            <input id={`${uid}-published`} type="checkbox" checked={form.published} onChange={(e) => set("published", e.target.checked)} /> Published
          </label>
          <div className="full">
            <button className="btn btn-primary" disabled={busy}>{busy ? (editId ? "Saving…" : "Creating…") : (editId ? "Save changes" : "Create item")}</button>
          </div>
        </form>
      )}

      <Problem err={err} onRetry={reload} />
      {audioMsg && <div className="empty" role="status">{audioMsg}</div>}
      {deleteMsg && <div className="empty" role="status">{deleteMsg}</div>}
      {saveMsg && <div className="empty" role="alert">{saveMsg}</div>}
      <div className="card">
        <table>
          <thead>
            <tr><th>Art</th><th>Title</th><th>Kind</th><th>Duration</th><th>Tier</th><th>Audio</th><th>Status</th><th></th></tr>
          </thead>
          <tbody>
            {(data || []).map((c) => (
              <tr key={c.id}>
                <td><Thumb item={c} /></td>
                <td>{c.title}</td>
                <td><span className="tag muted">{c.kind}</span></td>
                <td>{c.duration_min ? `${c.duration_min} min` : "—"}</td>
                <td>{c.premium ? <span className="tag elevated">premium</span> : <span className="tag ok">free</span>}</td>
                <td>{c.audio_url ? <span className="tag ok">narrated</span> : c.narration_script ? <span className="tag muted">script only</span> : "—"}</td>
                <td>{c.published ? "Published" : "Draft"}</td>
                <td>
                  {confirmDelete === c.id ? (
                    <div className="confirm">
                      <span className="ask">Delete “{c.title}” for good?</span>
                      <button className="btn btn-danger btn-sm" onClick={() => remove(c.id)}>Yes, delete</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setConfirmDelete(null)}>Keep it</button>
                    </div>
                  ) : (
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
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => { setDeleteMsg(""); setConfirmDelete(c.id); }}
                    >
                      Delete
                    </button>
                  </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && !err && <Empty>No content yet — add the first item.</Empty>}
      </div>
    </>
  );
}

/** Small artwork preview for a content row: the image if there is one, the
 *  icon name if there isn't, and the icon name again if the image 404s. */
function Thumb({ item }: { item: any }) {
  const [broken, setBroken] = useState(false);
  const symbol = item.symbol || "sparkles";
  if (item.image_url && !broken) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        className="thumb"
        src={item.image_url}
        alt=""
        loading="lazy"
        // The CSP lets admin load images from any https host (operators paste
        // third-party CDN URLs). Don't hand that host a referrer revealing the
        // admin origin/path. Deliberately NOT crossOrigin="anonymous" — that
        // would make this a CORS request and blank the preview on any CDN
        // that doesn't send Access-Control-Allow-Origin.
        referrerPolicy="no-referrer"
        onError={() => setBroken(true)}
      />
    );
  }
  return (
    <div className="thumb thumb-empty" title={broken ? `Image didn't load — showing ${symbol}` : symbol}>
      {broken ? "no image" : symbol.split(".")[0]}
    </div>
  );
}

// Editing one of these changes how risk is detected in journal and chat text,
// so it gets a warning, an acknowledgement, and a second confirmation click.
const RISK_PROMPTS = ["safety_classifier"];

// Versioned LLM prompt registry (backend services/prompts.py): every save is a
// new immutable version, rollback = activate an old one, revert = the code
// default serves again. Prompt edits reach production without a deploy.
function PromptsTab() {
  const uid = useId();
  const { data, err, reload } = useData<any[]>(() => api("/admin/prompts"));
  const [open, setOpen] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [ack, setAck] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [msg, setMsg] = useState("");

  function startEdit(p: any) {
    setOpen(p.name);
    setDraft(p.template);
    setNotes("");
    setAck(false);
    setConfirming(false);
    setMsg("");
  }

  async function save(name: string) {
    if (!draft.trim()) return;
    // Risk prompts: first click asks, second click saves.
    if (RISK_PROMPTS.includes(name) && !confirming) {
      setConfirming(true);
      return;
    }
    setBusy(true);
    setMsg("");
    try {
      await api(`/admin/prompts/${name}`, { method: "POST", body: JSON.stringify({ template: draft, notes }) });
      setOpen(null);
      setConfirming(false);
      reload();
    } catch (e) {
      setMsg(actionMessage(e, "That didn't save — the active version is unchanged."));
    } finally {
      setBusy(false);
    }
  }
  async function activate(name: string, version: number) {
    setMsg("");
    try {
      await api(`/admin/prompts/${name}/versions/${version}/activate`, { method: "POST" });
    } catch (e) {
      // Register E49: an unhandled rejection meant no feedback and no way to
      // know the live version never changed.
      setMsg(actionMessage(e, "Activation didn't go through — the live version is unchanged."));
    }
    reload();
  }
  async function revert(name: string) {
    setMsg("");
    try {
      await api(`/admin/prompts/${name}/revert`, { method: "POST" });
      setOpen(null);
    } catch (e) {
      setMsg(actionMessage(e, "The revert didn't go through — the live version is unchanged."));
    }
    reload();
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title serif">Prompt registry</h1>
          <div className="page-sub flush">
            Versioned LLM prompts — edits go live without a deploy; the code default is always the safe floor.
          </div>
        </div>
      </div>
      <Problem err={err} onRetry={reload} />
      {(data || []).map((p) => {
        const risky = RISK_PROMPTS.includes(p.name);
        return (
        <div className="card" key={p.name} style={{ padding: 18, marginBottom: 14 }}>
          <div className="row-between">
            <div>
              <strong className="serif ttl">{p.name}</strong>{" "}
              {p.source === "registry"
                ? <span className="tag elevated">v{p.active_version} active</span>
                : <span className="tag ok">code default</span>}
              {risky && <span className="tag crisis" style={{ marginLeft: 6 }}>affects risk detection</span>}
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
            <div className="mt-3">
              {risky && (
                <div className="warn" style={{ marginBottom: 12 }}>
                  <strong>This prompt decides what counts as a risk signal.</strong>
                  It classifies journal and chat text, so a weaker version can mean distress
                  goes unflagged. Safety never blocks a message — but this is what decides
                  whether support is offered at all. Prefer a small, tested change, and say
                  what you changed in the notes. The code default stays available as a floor:
                  “Revert to code default” restores it instantly.
                </div>
              )}
              <label htmlFor={`${uid}-${p.name}-template`}>Template</label>
              <textarea
                id={`${uid}-${p.name}-template`}
                aria-label={`Template for ${p.name}`}
                value={draft}
                onChange={(e) => { setDraft(e.target.value); setConfirming(false); }}
                rows={10}
                style={{ width: "100%", fontFamily: "monospace", fontSize: 12.5 }}
              />
              <label htmlFor={`${uid}-${p.name}-notes`}>Notes (what changed)</label>
              <input
                id={`${uid}-${p.name}-notes`}
                type="text"
                aria-describedby={`${uid}-${p.name}-notes-count`}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                maxLength={255}
              />
              <Counter id={`${uid}-${p.name}-notes-count`} value={notes} max={255} />
              {risky && (
                <label className="ack" htmlFor={`${uid}-${p.name}-ack`}>
                  <input
                    id={`${uid}-${p.name}-ack`}
                    type="checkbox"
                    checked={ack}
                    onChange={(e) => { setAck(e.target.checked); setConfirming(false); }}
                  />
                  I understand this changes how risk is detected for every user, as soon as it saves.
                </label>
              )}
              <div className="inline-actions mt-3">
                <button
                  className={`btn btn-sm ${risky && confirming ? "btn-danger" : "btn-primary"}`}
                  disabled={busy || (risky && !ack)}
                  onClick={() => save(p.name)}
                >
                  {busy
                    ? "Saving…"
                    : risky && confirming
                      ? "Confirm — make this the live risk prompt"
                      : "Save as new version"}
                </button>
                {risky && confirming && !busy && (
                  <button className="btn btn-ghost btn-sm" onClick={() => setConfirming(false)}>Cancel</button>
                )}
                {msg && <span className="page-sub flush">{msg}</span>}
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
        );
      })}
      {data && data.length === 0 && !err && <Empty>No prompts registered.</Empty>}
    </>
  );
}

const NUDGES_PAGE = 500;

function NudgesTab() {
  const uid = useId();
  // Explicit limit at the server cap (register E45): the silent default of
  // 100 made older sends vanish with nothing saying the list was partial —
  // the footer below owns up when a page is full.
  const { data, err, reload } = useData<any[]>(() => api(`/admin/nudges?limit=${NUDGES_PAGE}`));
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
      setMsg(actionMessage(e, "That didn't queue — nothing was sent."));
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
          <label htmlFor={`${uid}-title`}>Title</label>
          <input
            id={`${uid}-title`}
            type="text"
            aria-describedby={`${uid}-title-count`}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            maxLength={160}
          />
          <Counter id={`${uid}-title-count`} value={title} max={160} />
        </div>
        <div className="full">
          <label htmlFor={`${uid}-body`}>Body</label>
          <input
            id={`${uid}-body`}
            type="text"
            aria-describedby={`${uid}-body-count`}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            required
            maxLength={500}
          />
          <Counter id={`${uid}-body-count`} value={body} max={500} />
        </div>
        <div className="full inline-actions">
          <button className="btn btn-primary" disabled={busy || !title.trim() || !body.trim()}>
            {busy ? "Queuing…" : "Queue for all active users"}
          </button>
          {msg && <span className="page-sub flush">{msg}</span>}
        </div>
      </form>
      <Problem err={err} onRetry={reload} />
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
        {data && data.length === 0 && !err && <Empty>No nudges yet.</Empty>}
        {data && data.length >= NUDGES_PAGE && (
          <div className="note" style={{ padding: 12 }}>
            Showing the latest {NUDGES_PAGE} — older sends exist beyond this page.
          </div>
        )}
      </div>
    </>
  );
}

function fmtWhen(s: string) {
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;  // see fmtDate (register E64)
  return d.toLocaleString(undefined, {
    month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit",
  });
}

const SAFETY_PAGE = 200;

function Safety() {
  const [showResolved, setShowResolved] = useState(false);
  // Bounded fetch (register E42): the queue used to pull every event ever
  // recorded on each tab open; the footer below says when a page is full.
  const { data, err, loading, reload } = useData<any[]>(
    () => api(`/admin/safety?resolved=${showResolved}&limit=${SAFETY_PAGE}`),
    [showResolved]
  );
  // Someone's own words are never on screen until a reviewer asks for them,
  // one row at a time. Revealing is per-session and never sticky.
  // Someone's own words never travel with the list — the server sends only a
  // character count. Revealing fetches that one row's text, which is what
  // writes the audit line server-side. Never sticky, never bulk.
  const [revealed, setRevealed] = useState<Record<string, string>>({});
  const [revealErr, setRevealErr] = useState<Record<string, string>>({});
  const [resolving, setResolving] = useState<string | null>(null);

  async function reveal(s: any) {
    setRevealErr((e) => ({ ...e, [s.id]: "" }));
    try {
      const r = await api<{ id: string; excerpt: string }>(`/admin/safety/${s.id}/excerpt`);
      setRevealed((prev) => ({ ...prev, [s.id]: r.excerpt || "" }));
    } catch (e) {
      setRevealErr((prev) => ({
        ...prev,
        [s.id]: actionMessage(e, "That excerpt didn't load — it stays hidden."),
      }));
    }
  }
  function hide(id: string) {
    setRevealed((r) => {
      const next = { ...r };
      delete next[id];
      return next;
    });
    setRevealErr((e) => ({ ...e, [id]: "" }));
  }

  return (
    <>
      <h1 className="page-title serif">Safety review</h1>
      <div className="page-sub">
        Flagged signals from journal &amp; chat — wellness support, never a clinical gate.
      </div>
      <div className="warn" style={{ marginBottom: 16 }}>
        <strong>What people wrote stays hidden until you ask for it.</strong>
        Triage from the risk level and reason where you can. If you need someone&apos;s own
        words to make a call, reveal that one row — the reveal is noted on the row, and
        everything hides again when you leave this page.
      </div>
      <div className="toolbar">
        <label htmlFor="safety-filter" className="flabel" style={{ marginBottom: 0 }}>Showing</label>
        <select
          id="safety-filter"
          value={String(showResolved)}
          onChange={(e) => { setShowResolved(e.target.value === "true"); setRevealed({}); setResolving(null); }}
        >
          <option value="false">Open</option>
          <option value="true">Resolved</option>
        </select>
      </div>
      <Problem err={err} onRetry={reload} />
      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Source</th><th>Risk</th><th>Reason</th><th>What they wrote</th><th>When</th><th></th>
            </tr>
          </thead>
          <tbody>
            {(data || []).map((s) => {
              // undefined = never fetched; "" = fetched and genuinely empty.
              const shownText = revealed[s.id];
              return (
                <tr key={s.id}>
                  <td>
                    {s.source}
                    {/* Register E53: the flag could never reach its user — the
                        id was fetched and dropped. It pastes into Users
                        search, which matches ids. */}
                    {s.user_id && (
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{ display: "block", marginTop: 4 }}
                        title={`Copy user id ${s.user_id} — paste it into Users search`}
                        onClick={() => navigator.clipboard?.writeText(String(s.user_id)).catch(() => {})}
                      >
                        Copy user id
                      </button>
                    )}
                  </td>
                  <td><span className={`tag ${s.risk_level === "crisis" ? "crisis" : "elevated"}`}>{s.risk_level}</span></td>
                  <td>{s.reason || "—"}</td>
                  <td className="cell-narrow">
                    {shownText !== undefined ? (
                      <>
                        <div className="excerpt">{shownText || "—"}</div>
                        <div className="note tight" style={{ marginTop: 6, marginBottom: 0 }}>
                          You opened this. The server logged it.
                        </div>
                        <button className="btn btn-ghost btn-sm mt-2" onClick={() => hide(s.id)}>
                          <Icon.eyeOff /> Hide again
                        </button>
                      </>
                    ) : (
                      <>
                        <div className="redacted" aria-hidden="true">••••• ••••••• •••• •••••</div>
                        <div className="note tight" style={{ marginTop: 4, marginBottom: 0 }}>
                          Hidden{s.excerpt_chars ? ` · ${s.excerpt_chars} characters` : ""}
                        </div>
                        {revealErr[s.id] && <div className="state warn tight">{revealErr[s.id]}</div>}
                        <button className="btn btn-ghost btn-sm mt-2" onClick={() => reveal(s)}>
                          <Icon.eye /> Reveal excerpt
                        </button>
                      </>
                    )}
                  </td>
                  {/* fmtWhen, not fmtDate (register E52): triage must tell a
                      five-minute-old flag from a twenty-hour-old one. */}
                  <td>{fmtWhen(s.created_at)}</td>
                  <td>
                    {s.resolved ? (
                      <div className="note tight" style={{ marginBottom: 0, textAlign: "right" }}>
                        {/* The resolver's email when the API can name them
                            (register E54); the raw id only as a last resort. */}
                        Closed{s.resolved_by_email ? ` by ${s.resolved_by_email}` : s.resolved_by ? ` by ${s.resolved_by}` : ""}
                        {s.resolved_at ? ` · ${fmtWhen(s.resolved_at)}` : ""}
                        {s.resolution_note ? <div style={{ marginTop: 2 }}>“{s.resolution_note}”</div> : null}
                      </div>
                    ) : (
                      <div className="row-actions">
                        <button className="btn btn-ghost btn-sm" onClick={() => setResolving(resolving === s.id ? null : s.id)}>
                          {resolving === s.id ? "Cancel" : "Resolve"}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
            {resolving && (data || []).some((s) => s.id === resolving) && (
              <tr>
                <td colSpan={6} style={{ paddingTop: 0 }}>
                  <ResolvePanel
                    id={resolving}
                    onCancel={() => setResolving(null)}
                    onDone={() => { setResolving(null); reload(); }}
                  />
                </td>
              </tr>
            )}
          </tbody>
        </table>
        {loading && <Empty>Loading…</Empty>}
        {data && data.length === 0 && !err && (
          <Empty>{showResolved ? "Nothing has been closed yet." : "Nothing open right now. 🌙"}</Empty>
        )}
        {data && data.length >= SAFETY_PAGE && (
          <div className="note" style={{ padding: 12 }}>
            Showing the latest {SAFETY_PAGE} — older events exist beyond this page.
          </div>
        )}
      </div>
    </>
  );
}

// Closing a flag records what the reviewer did about it. The note is required
// server-side (SafetyResolve.note, min_length=1) and is persisted alongside the
// resolving admin's id and a timestamp, so a closed row can always say who
// decided it was handled, and why.
function ResolvePanel({ id, onCancel, onDone }: { id: string; onCancel: () => void; onDone: () => void }) {
  const uid = useId();
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  async function confirm() {
    setBusy(true);
    setMsg("");
    try {
      await api(`/admin/safety/${id}/resolve`, {
        method: "PATCH",
        body: JSON.stringify({ note: note.trim() }),
      });
      onDone();
    } catch (e) {
      setMsg(actionMessage(e, "That didn't save — the flag is still open."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel" style={{ marginTop: 0 }}>
      <label htmlFor={`${uid}-note`}>What happened with this one?</label>
      <textarea
        id={`${uid}-note`}
        rows={3}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        maxLength={500}
        aria-describedby={`${uid}-note-count`}
        placeholder="e.g. reached out, support resources shared, follow-up not needed"
      />
      <Counter id={`${uid}-note-count`} value={note} max={500} />
      <div className="inline-actions mt-3">
        <button className="btn btn-primary btn-sm" disabled={busy || !note.trim()} onClick={confirm}>
          {busy ? "Closing…" : "Close this flag"}
        </button>
        <button className="btn btn-ghost btn-sm" onClick={onCancel}>Cancel</button>
        {!note.trim() && <span className="page-sub flush">A short note is required.</span>}
        {msg && <span className="page-sub flush">{msg}</span>}
      </div>
    </div>
  );
}

/** RFC-4180-enough CSV: quote everything, double internal quotes — and
 * neutralise spreadsheet formulas.
 *
 * Register E36: a cell beginning `=`, `+`, `-`, `@` (or a leading tab/CR,
 * which Excel strips before parsing) is EXECUTED on open in Excel and
 * Sheets, and the waitlist `source` column is attacker-controlled through
 * the public POST /waitlist. Quoting alone does not stop it; a prefixed
 * apostrophe does, and spreadsheets hide it on display.
 */
function csvCell(value: unknown): string {
  const raw = String(value ?? "");
  const dangerous = /^[=+@\-\t\r]/.test(raw);
  const safe = dangerous ? `'${raw}` : raw;
  return `"${safe.replace(/"/g, '""')}"`;
}

function toCsv(rows: string[][]): string {
  return rows.map((r) => r.map(csvCell).join(",")).join("\r\n");
}

const WAITLIST_PAGE = 1000;

function WaitlistTab() {
  const { data, err, loading, reload } = useData<any[]>(() => api(`/admin/waitlist?limit=${WAITLIST_PAGE}`));

  function exportCsv() {
    const rows = [["email", "source", "joined"], ...(data || []).map((w) => [w.email, w.source, w.created_at ?? ""])];
    const blob = new Blob([toCsv(rows)], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `cerebro-waitlist-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title serif">Waitlist</h1>
          <div className="page-sub flush">
            {loading ? "Loading…" : `${data?.length ?? 0} signups from the landing page`}
          </div>
        </div>
        {(data?.length ?? 0) > 0 && (
          <button className="btn btn-ghost" onClick={exportCsv}>
            Export CSV{data && data.length >= WAITLIST_PAGE ? " (latest page)" : ""}
          </button>
        )}
      </div>
      <Problem err={err} onRetry={reload} />
      <div className="card">
        <table>
          <thead><tr><th>Email</th><th>Source</th><th>Joined</th></tr></thead>
          <tbody>
            {(data || []).map((w) => (
              // Email is unique server-side — a stable key, unlike the array
              // index (register E65).
              <tr key={w.email}>
                <td className="mono">{w.email}</td>
                <td><span className="tag muted">{w.source}</span></td>
                <td>{w.created_at ? fmtDate(w.created_at) : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.length === 0 && !err && <Empty>No signups yet.</Empty>}
        {data && data.length >= WAITLIST_PAGE && (
          <div className="note" style={{ padding: 12 }}>
            Showing the latest {WAITLIST_PAGE} — the CSV exports only what&apos;s shown.
          </div>
        )}
      </div>
    </>
  );
}

// ── Oracle ops ──────────────────────────────────────────────────────────
// The agent can WRITE user data (mood, journal, sleep) behind an in-chat
// confirmation. This tab is the only operator view of that: what it ran, what
// was approved, and what is still waiting. Argument VALUES are deliberately
// never sent by the API — only their names.

type OracleCall = {
  id: string;
  thread_id: string;
  tool: string;
  risk_tier: string;
  decision: string;
  arg_keys: string[];
  created_at: string;
  resolved_at: string | null;
};

function ago(iso: string) {
  const secs = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (secs < 90) return `${Math.round(secs)}s ago`;
  if (secs < 5400) return `${Math.round(secs / 60)}m ago`;
  if (secs < 172800) return `${Math.round(secs / 3600)}h ago`;
  return `${Math.round(secs / 86400)}d ago`;
}

function decisionTag(decision: string) {
  const cls =
    decision === "approved" ? "ok" : decision === "pending" ? "elevated" : "muted";
  return <span className={`tag ${cls}`}>{decision}</span>;
}

function OracleTab() {
  const { data: status, err: statusErr } = useData<any>(() => api("/admin/oracle/status"));
  const { data: pending, err: pendingErr } = useData<OracleCall[]>(() => api("/admin/oracle/pending"));
  const { data: trail, err: trailErr } = useData<OracleCall[]>(() => api("/admin/oracle/audit?limit=25"));

  return (
    <>
      <h1 className="page-title serif">Oracle</h1>
      <div className="page-sub">
        What the agent did, and what it is still waiting on. Tool arguments are recorded
        by name only — never their values.
      </div>
      <Problem err={statusErr} />

      <div className="stats">
        <div className="stat"><div className="n">{status?.enabled ? "Live" : "Off"}</div><div className="l">Agent</div></div>
        <div className="stat"><div className="n">{status?.checkpointer ?? "—"}</div><div className="l">Checkpointer</div></div>
        <div className="stat"><div className="n">{status?.counts?.pending ?? 0}</div><div className="l">Pending</div></div>
        <div className="stat"><div className="n">{status?.counts?.approved ?? 0}</div><div className="l">Approved</div></div>
        <div className="stat"><div className="n">{status?.counts?.total ?? 0}</div><div className="l">Tool calls</div></div>
      </div>

      {status?.checkpointer === "memory" && (
        <div className="card" style={{ borderColor: "var(--danger)", marginBottom: 20 }}>
          Running on the in-process MemorySaver — paused confirmations will not survive a
          restart and do not cross workers. Check the boot logs for why Postgres
          checkpointing failed.
        </div>
      )}
      {status?.checkpointer === "none" && (
        <div className="page-sub" style={{ marginBottom: 20 }}>
          The agent graph has not been built in this process — expected when no LLM key is
          set, or before the first Oracle request of the process.
        </div>
      )}

      <h3 className="serif" style={{ marginBottom: 10 }}>Awaiting a decision</h3>
      <Problem err={pendingErr} />
      <div className="card">
        <table>
          <thead><tr><th>Tool</th><th>Arguments</th><th>Thread</th><th>Waiting</th></tr></thead>
          <tbody>
            {(pending || []).map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.tool}</td>
                <td className="mono">{r.arg_keys.join(", ") || "—"}</td>
                <td className="mono">{r.thread_id}</td>
                <td>{ago(r.created_at)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {pending && pending.length === 0 && <div className="empty">Nothing stuck right now.</div>}
      </div>

      <h3 className="serif" style={{ marginTop: 24, marginBottom: 10 }}>Recent tool calls</h3>
      <Problem err={trailErr} />
      <div className="card">
        <table>
          <thead><tr><th>Tool</th><th>Tier</th><th>Decision</th><th>Arguments</th><th>When</th></tr></thead>
          <tbody>
            {(trail || []).map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.tool}</td>
                <td><span className="tag muted">{r.risk_tier}</span></td>
                <td>{decisionTag(r.decision)}</td>
                <td className="mono">{r.arg_keys.join(", ") || "—"}</td>
                <td>{ago(r.created_at)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {trail && trail.length === 0 && (
          <div className="empty">No agent tool calls yet.</div>
        )}
      </div>
    </>
  );
}
