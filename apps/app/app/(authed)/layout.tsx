"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { hasSession, signOut } from "@/lib/api";
import { BrandDot, Icon } from "@/components/icons";

// Primary destinations (the mobile bottom bar shows these five, mirroring iOS
// Home / Talk / Sleep / Journal / You).
const PRIMARY = [
  { href: "/home", label: "Home", icon: Icon.home },
  { href: "/chat", label: "Talk", icon: Icon.talk },
  { href: "/sleep", label: "Sleep", icon: Icon.sleep },
  { href: "/journal", label: "Journal", icon: Icon.journal },
  { href: "/account", label: "You", icon: Icon.account },
];
// Deeper spaces — desktop sidebar only.
const EXPLORE = [
  { href: "/insights", label: "Insights", icon: Icon.insights },
  { href: "/plan", label: "Plan", icon: Icon.plan },
  { href: "/library", label: "Library", icon: Icon.library },
];

export default function AuthedLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!hasSession()) router.replace("/signin");
    else setReady(true);
  }, [router]);

  if (!ready) return null;

  const active = (href: string) => pathname.startsWith(href);

  async function doSignOut() {
    await signOut();
    router.replace("/signin");
  }

  const NavLink = ({ href, label, icon: I }: (typeof PRIMARY)[number]) => (
    <Link key={href} href={href} className={active(href) ? "nav-item active" : "nav-item"}>
      <I size={20} /> {label}
    </Link>
  );

  return (
    <div className="app-shell">
      {/* Desktop sidebar */}
      <aside className="sidebar" aria-label="Primary">
        <Link href="/home" className="sidebar-brand">
          <BrandDot /> <span>CereBro</span>
        </Link>
        <div className="nav-group-label">Menu</div>
        <nav className="nav-group">{PRIMARY.map(NavLink)}</nav>
        <div className="nav-group-label">Explore</div>
        <nav className="nav-group">{EXPLORE.map(NavLink)}</nav>
        <div className="sidebar-foot">
          <button className="nav-item" onClick={doSignOut}>
            <Icon.signout size={20} /> Sign out
          </button>
        </div>
      </aside>

      <div className="app-main">
        <main className="page">{children}</main>
      </div>

      {/* Mobile bottom tab bar */}
      <nav className="mobile-tabs" aria-label="Primary">
        {PRIMARY.map(({ href, label, icon: I }) => (
          <Link key={href} href={href} className={active(href) ? "mtab active" : "mtab"}>
            <I size={22} />
            <span>{label}</span>
          </Link>
        ))}
      </nav>
    </div>
  );
}
