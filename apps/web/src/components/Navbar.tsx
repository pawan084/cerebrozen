"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { navLinks, signInTargets, site } from "@/lib/site";
import { CloseIcon, MenuIcon } from "@/components/icons";

export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span
      className={`font-[family-name:var(--font-serif)] text-2xl font-semibold tracking-tight ${className}`}
    >
      CereBr<span className="text-zen-500">o</span>Zen
    </span>
  );
}

/* "Sign in" is two destinations, not one: employees go to the coaching app, HR and
   admins to the console. Asking beats guessing — a wrong guess lands somebody on a
   login screen their account can't pass. */
function SignInMenu() {
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    const onClick = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onClick);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onClick);
    };
  }, [open]);

  return (
    <div className="relative" ref={wrap}>
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1.5 text-[13.5px] font-semibold text-brand-900 transition-colors hover:text-zen-600"
      >
        Sign in
        <svg
          viewBox="0 0 12 12"
          aria-hidden="true"
          className={`h-3 w-3 transition-transform ${open ? "rotate-180" : ""}`}
        >
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 top-full z-50 mt-3 w-72 overflow-hidden rounded-2xl border border-mist-100 bg-white shadow-[0_12px_40px_rgba(16,16,16,0.14)]"
        >
          {signInTargets.map((t) => (
            <a
              key={t.href}
              href={t.href}
              role="menuitem"
              className="group flex items-center gap-3 border-b border-mist-100 px-5 py-4 last:border-b-0 hover:bg-mist-50"
            >
              <span className="flex-1">
                <span className="block text-[13.5px] font-semibold text-brand-900">{t.who}</span>
                <span className="block text-[12.5px] text-brand-600">{t.what}</span>
                <span className="mt-0.5 block text-[11px] text-brand-500">{t.host}</span>
              </span>
              <span aria-hidden="true" className="text-zen-500 transition-transform group-hover:translate-x-0.5">→</span>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

export default function Navbar() {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={`sticky top-0 z-50 bg-white/95 backdrop-blur-md transition-shadow ${
        scrolled ? "shadow-[0_1px_12px_rgba(16,16,16,0.08)]" : ""
      }`}
    >
      <nav className="mx-auto flex h-[72px] max-w-7xl items-center justify-between px-6">
        <Link href="/" onClick={() => setOpen(false)}>
          <Wordmark className="text-brand-900" />
        </Link>

        <div className="hidden items-center gap-8 lg:flex">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-[13.5px] font-semibold text-brand-900 transition-colors hover:text-zen-600"
            >
              {link.label}
            </Link>
          ))}
          <SignInMenu />
          <Link
            href="/contact"
            className="rounded-full border-2 border-zen-500 px-6 py-2.5 text-[13.5px] font-semibold text-zen-600 transition hover:bg-zen-500 hover:text-white"
          >
            Request a demo
          </Link>
        </div>

        <button
          type="button"
          aria-label={open ? "Close menu" : "Open menu"}
          aria-expanded={open}
          aria-controls="mobile-menu"
          className="rounded-md p-2 text-brand-900 lg:hidden"
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <CloseIcon className="h-6 w-6" /> : <MenuIcon className="h-6 w-6" />}
        </button>
      </nav>

      {open && (
        <div
          id="mobile-menu"
          className="border-t border-mist-100 bg-white px-6 pb-6 pt-2 lg:hidden"
        >
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="block py-3 text-base font-semibold text-brand-900"
              onClick={() => setOpen(false)}
            >
              {link.label}
            </Link>
          ))}
          <div className="mt-3 border-t border-mist-100 pt-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-brand-500">Sign in</p>
            {/* No dropdown here: the drawer is already a menu, so both destinations
                are listed outright rather than hidden behind a second tap. */}
            {signInTargets.map((t) => (
              <a
                key={t.href}
                href={t.href}
                className="flex items-center gap-3 py-3"
                onClick={() => setOpen(false)}
              >
                <span className="flex-1">
                  <span className="block text-base font-semibold text-brand-900">{t.who}</span>
                  <span className="block text-xs text-brand-600">{t.what}</span>
                </span>
                <span aria-hidden="true" className="text-zen-500">→</span>
              </a>
            ))}
          </div>
          <Link
            href="/contact"
            className="mt-3 block rounded-full border-2 border-zen-500 px-5 py-3 text-center text-sm font-semibold text-zen-600"
            onClick={() => setOpen(false)}
          >
            Request a demo
          </Link>
          <p className="mt-4 text-xs text-brand-600">{site.tagline}</p>
        </div>
      )}
    </header>
  );
}
