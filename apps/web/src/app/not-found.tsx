import type { Metadata } from "next";
import Link from "next/link";
import { navLinks } from "@/lib/site";

export const metadata: Metadata = {
  title: "Page not found",
  robots: { index: false, follow: true },
};

export default function NotFound() {
  return (
    <section className="mx-auto flex max-w-3xl flex-col items-center px-6 py-28 text-center sm:py-36">
      <p className="font-[family-name:var(--font-heading)] text-sm font-bold uppercase tracking-widest text-zen-600">
        404
      </p>
      <h1 className="mt-4 font-[family-name:var(--font-heading)] text-4xl font-bold tracking-tight text-brand-900 sm:text-5xl text-balance">
        This page wandered off
      </h1>
      <p className="mt-4 max-w-md text-brand-600">
        The link may be old or mistyped. Here&rsquo;s the way back.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Link
          href="/"
          className="rounded-full bg-brand-900 px-6 py-3 text-[13.5px] font-semibold text-white transition hover:bg-brand-800"
        >
          Back to home
        </Link>
        <Link
          href="/contact"
          className="rounded-full border-2 border-zen-500 px-6 py-3 text-[13.5px] font-semibold text-zen-600 transition hover:bg-zen-500 hover:text-white"
        >
          Request a demo
        </Link>
      </div>
      <nav aria-label="Site sections" className="mt-12 flex flex-wrap justify-center gap-x-6 gap-y-2 border-t border-mist-100 pt-8 text-sm">
        {navLinks.map((link) => (
          <Link key={link.href} href={link.href} className="font-semibold text-brand-800 hover:text-zen-600">
            {link.label}
          </Link>
        ))}
      </nav>
    </section>
  );
}
