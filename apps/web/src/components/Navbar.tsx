"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { navLinks, site } from "@/lib/site";
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
