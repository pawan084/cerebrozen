"use client";

/* The app shell: auth gate + sidebar nav + shared user context.
   Unauthed → full-screen Login. Authed → sidebar (dark, coral brand) + the page
   in the main area. `me` is fetched once here and shared via useMe(). */

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  createContext, useCallback, useContext, useEffect, useState, type FormEvent, type ReactNode,
} from "react";
import { hasSession, login, logout, me, type Me } from "@/lib/api";
import { SITE_URL, siteLinks } from "@/lib/site";

const MeCtx = createContext<Me | null>(null);
export const useMe = () => useContext(MeCtx);

export function firstName(m: Me | null): string {
  if (!m) return "";
  return (m.name?.trim() || m.email.split("@")[0]).split(" ")[0];
}

/* ── inline icons (stroke, currentColor — no icon dependency). Inner markup is a
   static developer-authored string, so there is no JSX-in-argument to misparse. ── */
const s = (inner: string) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
    strokeLinecap="round" strokeLinejoin="round" dangerouslySetInnerHTML={{ __html: inner }} />
);
export const Icon: Record<string, ReactNode> = {
  home: s('<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>'),
  talk: s('<path d="M21 11.5a8.4 8.4 0 0 1-8.5 8.5 9 9 0 0 1-3.9-.9L3 21l1.9-5.6a8.4 8.4 0 0 1-.9-3.9A8.5 8.5 0 1 1 21 11.5Z"/>'),
  journal: s('<path d="M4 4h13a2 2 0 0 1 2 2v14H6a2 2 0 0 1-2-2Z"/><path d="M8 4v16"/>'),
  insights: s('<path d="M4 20V10"/><path d="M10 20V4"/><path d="M16 20v-7"/><path d="M22 20H2"/>'),
  settings: s('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7.6 1.6 1.6 0 0 0-1 1.5V22a2 2 0 0 1-4 0v-.2a1.6 1.6 0 0 0-2.7-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 4.6 15a1.6 1.6 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.2A1.6 1.6 0 0 0 4.6 9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9 4.6a1.6 1.6 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.2a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.6 1.7V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.2a1.6 1.6 0 0 0-1.4 1Z"/>'),
  menu: s('<path d="M4 6h16"/><path d="M4 12h16"/><path d="M4 18h16"/>'),
  search: s('<circle cx="11" cy="11" r="7"/><path d="m20 20-3-3"/>'),
  play: s('<path d="M7 4v16l13-8Z"/>'),
  journalTile: s('<path d="M4 4h13a2 2 0 0 1 2 2v14H6a2 2 0 0 1-2-2Z"/><path d="M8 4v16"/>'),
  moon: s('<path d="M21 12.8A8.5 8.5 0 1 1 11.2 3a6.6 6.6 0 0 0 9.8 9.8Z"/>'),
  spark: s('<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18"/>'),
  bell: s('<path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8Z"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>'),
};

/* Demo sign-in: one-click fill of the platform's dev-seeded member. DEV ONLY.
   Gated on NODE_ENV *alone* and nothing else: webpack constant-folds it at build time,
   so a production build proves the branch dead and strips the block AND these
   credential strings out of the bundle entirely. (An earlier version also allowed an
   env-var opt-in — that made the condition runtime-unknowable, the minifier kept the
   branch, and the credentials shipped in the public JS. Verified by grepping .next.)
   The accounts can't exist in prod either: guard_production() refuses to boot with
   CEREBROZEN_SEED_DEV_ADMIN on. */
const SHOW_DEMO = process.env.NODE_ENV === "development";
const DEMO = { email: "demo@cerebrozen.in", password: "demo12345", who: "Alex Rivera", role: "member · Demo Co" };

/* ── Login (full-screen, unauthed) ── */
function Login({ onDone }: { onDone: () => void }) {
  // Pre-filled in dev so a fresh clone is one click from signed in. Same NODE_ENV-only
  // gate as the chip below and the same strings — no new credential literal — so a
  // production build constant-folds SHOW_DEMO to false and these become "".
  const [email, setEmail] = useState(SHOW_DEMO ? DEMO.email : "");
  const [password, setPassword] = useState(SHOW_DEMO ? DEMO.password : "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(""); setBusy(true);
    try {
      await login(email, password);
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed");
      setBusy(false);
    }
  }
  return (
    <main className="center">
      <div className="login-card">
        {/* enso mark, echoing the marketing site's hero */}
        <svg viewBox="0 0 48 48" width="38" height="38" aria-hidden="true" style={{ display: "block", marginBottom: 14 }}>
          <path d="M34.5 13.5a13 13 0 1 0 3.2 8.6" fill="none" stroke="#f56b6b" strokeWidth="4.5" strokeLinecap="round" />
          <circle cx="36" cy="12" r="3" fill="#f56b6b" />
        </svg>
        {/* The site is a separate deployment — a real navigation, not a route. */}
        <a className="wordmark home" href={SITE_URL}>CereBr<span className="o">o</span>Zen</a>
        <h1>Welcome back</h1>
        <p className="sub">Sign in to talk with your coach.</p>
        <form onSubmit={submit}>
          <label>Work email
            <input name="email" type="email" required autoComplete="username" placeholder="you@company.com"
              value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label>Password
            <input name="password" type="password" required autoComplete="current-password"
              value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <button className="primary" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
          {error && <p className="error">{error}</p>}
        </form>
        {SHOW_DEMO && (
          <div className="demo">
            <div className="demo-lbl">Demo — development only</div>
            <button type="button" className="demo-chip"
              onClick={() => { setEmail(DEMO.email); setPassword(DEMO.password); setError(""); }}>
              <span className="dc-who">{DEMO.who}</span>
              <span className="dc-role">{DEMO.role}</span>
            </button>
          </div>
        )}
        {/* Privacy and Terms live on the site; a login screen is where people look
            for them, and this app must not fork its own copy. */}
        <nav className="login-foot">
          {siteLinks.map((l) => <a key={l.href} href={l.href}>{l.label}</a>)}
        </nav>
      </div>
    </main>
  );
}

const MENU = [
  { href: "/", label: "Home", icon: "home" },
  { href: "/coach", label: "Talk", icon: "talk" },
  { href: "/journal", label: "Journal", icon: "journal" },
  { href: "/insights", label: "Insights", icon: "insights" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  const load = useCallback(async () => {
    // hasSession(), not "do we hold an access token": the access token is memory-only and
    // is therefore ALWAYS absent on a fresh load. Gating on it would sign everyone out on
    // every refresh of the page. me() spends the refresh token to mint a new one.
    if (!hasSession()) { setUser(null); setReady(true); return; }
    try {
      setUser(await me());
    } catch {
      // A network rejection while minting the session must NOT strand the app on the
      // booting spinner forever — fall through to the login screen instead.
      setUser(null);
    } finally {
      setReady(true);
    }
  }, []);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { setOpen(false); }, [pathname]);

  if (!ready) return <div className="center" aria-busy="true"><div className="booting"><span className="glyph" /></div></div>;
  if (!user) return <Login onDone={load} />;

  async function signOut() {
    await logout();
    setUser(null);
  }
  const initial = (user.name?.trim() || user.email).charAt(0).toUpperCase();

  return (
    <MeCtx.Provider value={user}>
      <button className="menu-btn" aria-label="Menu" onClick={() => setOpen(true)}>{Icon.menu}</button>
      <div className="app">
        <div className={`scrim ${open ? "show" : ""}`} onClick={() => setOpen(false)} />
        <aside className={`sidebar ${open ? "open" : ""}`}>
          <a className="brand" href={SITE_URL} title="cerebrozen.in">
            <span className="glyph" />
            <span className="wordmark">CereBr<span className="o">o</span>Zen</span>
          </a>
          <div className="nav-label">Menu</div>
          <nav className="nav">
            {MENU.map((n) => (
              <Link key={n.href} href={n.href} className={pathname === n.href ? "active" : ""}>
                {Icon[n.icon]}{n.label}
              </Link>
            ))}
          </nav>
          <div className="nav-label">Account</div>
          <nav className="nav">
            <Link href="/settings" className={pathname === "/settings" ? "active" : ""}>{Icon.settings}Settings</Link>
          </nav>
          <div className="side-spacer" />
          <div className="side-user">
            <div className="avatar">{initial}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="u-name">{firstName(user)}</div>
              <div className="u-sub">{user.role === "user" ? "Member" : user.role}</div>
            </div>
            <button className="linkbtn" onClick={signOut}>Sign out</button>
          </div>
        </aside>
        <main className="main">{children}</main>
      </div>
    </MeCtx.Provider>
  );
}
