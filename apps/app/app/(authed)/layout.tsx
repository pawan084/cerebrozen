"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { hasSession, signOut } from "@/lib/api";
import { BrandMark, Icon } from "@/components/icons";

const MENU = [
  { href: "/home", label: "Home", icon: Icon.home },
  { href: "/chat", label: "Talk", icon: Icon.talk },
  { href: "/sleep", label: "Sleep", icon: Icon.sleep },
  { href: "/journal", label: "Journal", icon: Icon.journal },
  { href: "/insights", label: "Insights", icon: Icon.insights },
];
const EXPLORE = [
  { href: "/plan", label: "Plan", icon: Icon.plan },
  { href: "/programs", label: "Programs", icon: Icon.spark },
  { href: "/library", label: "Library", icon: Icon.library },
  { href: "/games", label: "Games", icon: Icon.games },
  { href: "/account", label: "Settings", icon: Icon.settings },
];
// The mobile bottom bar keeps the five primary spaces (mirrors iOS).
const MOBILE = [
  { href: "/home", label: "Home", icon: Icon.home },
  { href: "/chat", label: "Talk", icon: Icon.talk },
  { href: "/sleep", label: "Sleep", icon: Icon.sleep },
  { href: "/journal", label: "Journal", icon: Icon.journal },
  { href: "/account", label: "You", icon: Icon.account },
];

export default function AuthedLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const [name, setName] = useState("");

  useEffect(() => {
    if (!hasSession()) { router.replace("/signin"); return; }
    setReady(true);
    import("@/lib/api").then(({ api }) =>
      api("/auth/me").then((me: any) => setName(me.name || "")).catch(() => {}));
  }, [router]);

  if (!ready) return null;
  const active = (href: string) => pathname.startsWith(href);

  const NavLink = ({ href, label, icon: I }: (typeof MENU)[number]) => (
    <Link key={href} href={href} className={active(href) ? "nav-item active" : "nav-item"}>
      <I size={19} /> {label}
    </Link>
  );

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary">
        <Link href="/home" className="sidebar-brand"><BrandMark size={30} /> <span>CereBro</span></Link>

        <div className="nav-group-label">Menu</div>
        <nav className="nav-group">{MENU.map(NavLink)}</nav>
        <div className="nav-group-label">Explore</div>
        <nav className="nav-group">{EXPLORE.map(NavLink)}</nav>

        <div className="premium-card">
          <strong>Unlock Premium</strong>
          <p>Unlimited talks, the full sleep library, and deeper insights.</p>
          <Link href="/account" className="premium-btn">See plans</Link>
        </div>

        <div className="sidebar-foot">
          <div className="user-chip">
            <span className="user-avatar" aria-hidden="true" />
            <div className="user-meta">
              <strong>{name || "Your space"}</strong>
              <small>Free plan</small>
            </div>
          </div>
          <button
            className="signout-icon"
            title="Sign out"
            aria-label="Sign out"
            onClick={async () => { await signOut(); router.replace("/signin"); }}
          >
            <Icon.signout size={18} />
          </button>
        </div>
      </aside>

      <div className="app-main">{children}</div>

      <nav className="mobile-tabs" aria-label="Primary">
        {MOBILE.map(({ href, label, icon: I }) => (
          <Link key={href} href={href} className={active(href) ? "mtab active" : "mtab"}>
            <I size={22} /><span>{label}</span>
          </Link>
        ))}
      </nav>
    </div>
  );
}
