"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { hasSession, signOut } from "@/lib/api";

const TABS = [
  { href: "/home", label: "Today" },
  { href: "/chat", label: "Chat" },
  { href: "/journal", label: "Journal" },
  { href: "/sleep", label: "Sleep" },
  { href: "/plan", label: "Plan" },
  { href: "/insights", label: "Insights" },
  { href: "/library", label: "Library" },
  { href: "/account", label: "Account" },
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

  return (
    <>
      <header className="topbar">
        <div className="brand"><span className="dot" /> CereBro</div>
        <nav className="tabs" aria-label="Sections">
          {TABS.map((t) => (
            <Link key={t.href} href={t.href} className={pathname.startsWith(t.href) ? "active" : ""}>
              {t.label}
            </Link>
          ))}
        </nav>
        <div className="spacer" />
        <button
          className="btn ghost"
          onClick={async () => {
            await signOut();
            router.replace("/signin");
          }}
        >
          Sign out
        </button>
      </header>
      <main className="page">{children}</main>
    </>
  );
}
