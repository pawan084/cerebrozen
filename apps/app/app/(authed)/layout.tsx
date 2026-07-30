"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { hasSession, signOut } from "@/lib/api";
import { currentPath } from "@/lib/nextPath";
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
// The mobile bottom bar keeps the primary spaces (mirrors iOS) plus the Support
// door — crisis has to stay one tap away on a phone too (design system §1).
const MOBILE = [
  { href: "/home", label: "Home", icon: Icon.home },
  { href: "/chat", label: "Talk", icon: Icon.talk },
  { href: "/sleep", label: "Sleep", icon: Icon.sleep },
  { href: "/journal", label: "Journal", icon: Icon.journal },
  { href: "/support", label: "Support", icon: Icon.support },
  { href: "/account", label: "You", icon: Icon.account },
];

// The upsell is dismissible per device — an always-on upgrade card in a wellness
// app is the OECD "nagging" indicator (design system §8).
const PREMIUM_DISMISSED = "cerebro_app_premium_dismissed";

export default function AuthedLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const [name, setName] = useState("");
  const [tier, setTier] = useState<string | null>(null);
  const [upsell, setUpsell] = useState(false);

  useEffect(() => {
    if (!hasSession()) {
      // Carry the destination through sign-in, so a landing-page link like
      // "Open Sleep →" actually lands on Sleep instead of dumping everyone on
      // Home. Read from `window` rather than the `pathname` hook so this effect
      // keeps its one-shot deps and doesn't refetch /auth/me on every nav.
      router.replace(`/signin?next=${encodeURIComponent(currentPath())}`);
      return;
    }
    setReady(true);
    setUpsell(window.localStorage.getItem(PREMIUM_DISMISSED) !== "1");
    import("@/lib/api").then(({ api }) =>
      api("/auth/me").then((me: any) => {
        setName(me.name || "");
        setTier(me.subscription_tier || "free");
      }).catch(() => {}));
  }, [router]);

  function dismissUpsell() {
    window.localStorage.setItem(PREMIUM_DISMISSED, "1");
    setUpsell(false);
  }

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

        {/* A calm, always-there door — crisis stays ≤2 taps from every screen. */}
        <Link href="/support" className={active("/support") ? "support-door active" : "support-door"}>
          <Icon.support size={19} />
          <span>
            <strong>Support</strong>
            <small>Real people, 24/7</small>
          </span>
        </Link>

        {upsell && tier === "free" && (
          <div className="premium-card">
            <strong>Unlock Premium</strong>
            <p>Unlimited talks, the full sleep library, and deeper insights.</p>
            <Link href="/account" className="premium-btn">See plans</Link>
            <button type="button" className="premium-dismiss" onClick={dismissUpsell}>Not now</button>
          </div>
        )}

        <div className="sidebar-foot">
          <div className="user-chip">
            <span className="user-avatar" aria-hidden="true" />
            <div className="user-meta">
              <strong>{name || "Your space"}</strong>
              {tier && <small>{tier === "free" ? "Free plan" : `${tier[0].toUpperCase()}${tier.slice(1)} plan`}</small>}
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
