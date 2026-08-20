"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { hasSession, signOut } from "@/lib/api";
import { currentPath } from "@/lib/nextPath";
import { OUTBOX_EVENT, pendingCount, startOutbox } from "@/lib/outbox";
import { BrandMark, Icon } from "@/components/icons";

// Nav restructured to the spec's IA (ref/, ruling recorded in REDESIGN_V2.md §6).
// The headline change: Sleep is no longer a primary destination — it moved under
// Explore, which takes its slot. "Home" is "Today" everywhere now.
//
// The desktop sidebar is NOT just the five tabs: web.html groups it
// Workspace / Library / Account, which is what these three arrays are.
const MENU = [
  { href: "/home", label: "Today", icon: Icon.home },
  { href: "/explore", label: "Explore", icon: Icon.search },
  { href: "/chat", label: "Talk", icon: Icon.talk },
  { href: "/journal", label: "Journal", icon: Icon.journal },
  { href: "/plan", label: "Plan", icon: Icon.plan },
];
const EXPLORE = [
  // Sleep leads the library group: demoting it from the tab bar is the ruling,
  // burying it is not. It is still the heaviest thing in the product.
  { href: "/sleep", label: "Sleep", icon: Icon.sleep },
  { href: "/programs", label: "Programmes", icon: Icon.spark },
  { href: "/library", label: "Catalogue", icon: Icon.library },
  // "Toolkit", not "Games": the hub is Ground/Breathe/Reframe/Settle, and the
  // four mini-games REDESIGN §2.2 removed are exactly what the old name promised.
  { href: "/games", label: "Toolkit", icon: Icon.games },
  { href: "/insights", label: "Insights", icon: Icon.insights },
  { href: "/goals", label: "Goals & habits", icon: Icon.spark },
  // A plan is written when things are steady, so it belongs in the calm part of
  // the nav — not filed under crisis, where nobody browses.
  { href: "/safety-plan", label: "Safety plan", icon: Icon.support },
  { href: "/account", label: "Settings", icon: Icon.settings },
  // Crisis stays ≤2 clicks from anywhere (REDESIGN §2.3), but as the single
  // styled `.support-door` below the nav rather than a row here — two entries
  // both labelled "Support" is what merging the two designs first produced.
];
// The mobile bottom bar IS the spec's five tabs, exactly:
// Today · Explore · Talk · Journal · You.
// Support left this bar and became a permanent app-bar entry in AppHeader —
// the spec keeps urgent support outside the tab set, and it had to land there
// first so crisis never stopped being ≤2 taps on a phone (design system §1).
const MOBILE = [
  { href: "/home", label: "Today", icon: Icon.home },
  { href: "/explore", label: "Explore", icon: Icon.search },
  { href: "/chat", label: "Talk", icon: Icon.talk },
  { href: "/journal", label: "Journal", icon: Icon.journal },
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
  // `null` until /auth/me answers: defaulting to "free" flashed a "Free plan"
  // chip at paying users on every load.
  const [tier, setTier] = useState<string | null>(null);
  const [upsell, setUpsell] = useState(false);
  /** Writes made without a network, still on this device. Shown in the shell
   *  rather than only on the screen that made them: the person may well have
   *  navigated away, and "did that save?" deserves an answer from anywhere. */
  const [pending, setPending] = useState(0);

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
    // A live session = established relationship → telemetry unlocked (the
    // same rule iOS/Android apply on connect; the opt-out still governs).
    import("@/lib/analytics").then(({ unlockAnalytics }) => unlockAnalytics());
    setUpsell(window.localStorage.getItem(PREMIUM_DISMISSED) !== "1");
    import("@/lib/api").then(({ api }) =>
      api("/auth/me").then((me: any) => {
        setName(me.name || "");
        setTier(me.subscription_tier || "free");
      }).catch(() => {}));
  }, [router]);

  // The queue drains on load and whenever the browser says the network is
  // back. Mounted here so it runs for every authed screen, once.
  useEffect(() => {
    if (!ready) return;
    const stopOutbox = startOutbox();
    setPending(pendingCount());
    const onChange = (e: Event) => setPending((e as CustomEvent).detail?.pending ?? 0);
    window.addEventListener(OUTBOX_EVENT, onChange);
    return () => {
      stopOutbox();
      window.removeEventListener(OUTBOX_EVENT, onChange);
    };
  }, [ready]);

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
      <a className="skip-link" href="#main">Skip to content</a>
      <aside className="sidebar" aria-label="Primary">
        <Link href="/home" className="sidebar-brand"><BrandMark size={30} /> <span>CereBro</span></Link>

        <div className="nav-group-label">Workspace</div>
        <nav className="nav-group">{MENU.map(NavLink)}</nav>
        <div className="nav-group-label">Library</div>
        <nav className="nav-group">{EXPLORE.map(NavLink)}</nav>

        {/* A calm, always-there door — crisis stays ≤2 taps from every screen. */}
        <Link href="/support" className={active("/support") ? "support-door active" : "support-door"}>
          <Icon.support size={19} />
          <span>
            <strong>Support</strong>
            <small>Real people, 24/7</small>
          </span>
        </Link>

        {/* Free accounts only, and dismissible — a permanent upsell in a
            wellness app leans on the OECD "nagging" indicator, and premium
            users never need it. */}
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
              {/* Named map, not a capitalise: the tier value is `premium_human`,
                  which generic title-casing renders as "Premium_human plan". */}
              {tier && (
                <small>
                  {{ premium: "Premium", premium_human: "Premium + Human" }[tier] ?? "Free plan"}
                </small>
              )}
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

      <div className="app-main" id="main">
        {pending > 0 && (
          <p className="outbox-bar" role="status">
            {pending === 1 ? "1 entry is" : `${pending} entries are`} saved on this device and
            waiting to send. Nothing is lost — this clears itself when you are back online.
          </p>
        )}
        {children}
      </div>

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
