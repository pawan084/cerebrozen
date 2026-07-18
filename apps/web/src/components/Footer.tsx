import Link from "next/link";
import { site } from "@/lib/site";
import { LinkedInIcon, YouTubeIcon } from "@/components/icons";
import { Wordmark } from "@/components/Navbar";

export default function Footer() {
  return (
    <footer className="bg-brand-950 text-white">
      <div className="mx-auto max-w-7xl px-6 py-16">
        <div className="grid gap-12 md:grid-cols-3">
          <div>
            <h3 className="font-[family-name:var(--font-heading)] font-bold">
              Offerings
            </h3>
            <ul className="mt-4 space-y-2.5 text-sm text-white/80">
              <li>
                <Link href="/platform" className="hover:text-white">
                  – AI Coaching
                </Link>
              </li>
              <li>
                <Link href="/platform" className="hover:text-white">
                  – Insights and Analytics
                </Link>
              </li>
              <li>
                <Link href="/solutions" className="hover:text-white">
                  – Solutions
                </Link>
              </li>
            </ul>

            <h3 className="mt-8 font-[family-name:var(--font-heading)] font-bold">
              Support
            </h3>
            <ul className="mt-4 space-y-2.5 text-sm text-white/80">
              <li>
                <Link href="/terms" className="hover:text-white">
                  – Terms of service
                </Link>
              </li>
              <li>
                <Link href="/privacy" className="hover:text-white">
                  – Privacy policy
                </Link>
              </li>
            </ul>

            <p className="mt-8">
              <Link
                href="/contact"
                className="font-[family-name:var(--font-heading)] font-bold hover:text-zen-400"
              >
                Request a Demo
              </Link>
            </p>
          </div>

          <div className="space-y-5">
            {[
              ["Platform", "/platform"],
              ["Enterprise Data Security", "/security"],
              ["Evidence", "/evidence"],
              ["Client Stories", "/clients"],
            ].map(([label, href]) => (
              <p key={href}>
                <Link
                  href={href}
                  className="font-[family-name:var(--font-heading)] font-bold hover:text-zen-400"
                >
                  {label}
                </Link>
              </p>
            ))}
            <div>
              <h3 className="font-[family-name:var(--font-heading)] font-bold">
                Company
              </h3>
              <ul className="mt-4 space-y-2.5 text-sm text-white/80">
                <li>
                  <Link href="/about" className="hover:text-white">
                    – The CereBroZen Story
                  </Link>
                </li>
                <li>
                  <Link href="/contact" className="hover:text-white">
                    – Contact Us
                  </Link>
                </li>
              </ul>
            </div>
          </div>

          <div>
            <h3 className="font-[family-name:var(--font-heading)] font-bold">
              Follow Us
            </h3>
            <div className="mt-4 flex gap-3">
              <a
                href="https://www.youtube.com"
                target="_blank"
                rel="noreferrer"
                aria-label="YouTube"
                className="rounded-full bg-zen-500 p-3 transition hover:bg-zen-400"
              >
                <YouTubeIcon className="h-4 w-4" />
              </a>
              <a
                href="https://www.linkedin.com"
                target="_blank"
                rel="noreferrer"
                aria-label="LinkedIn"
                className="rounded-full bg-zen-500 p-3 transition hover:bg-zen-400"
              >
                <LinkedInIcon className="h-4 w-4" />
              </a>
            </div>
            <div className="mt-8">
              <h3 className="font-[family-name:var(--font-heading)] font-bold">
                Get the app
              </h3>
              <p className="mt-2 max-w-xs text-sm text-white/60">
                Coaching in your pocket — native iOS &amp; Android, coming soon.
              </p>
              <div className="mt-4 flex flex-wrap gap-3">
                <span
                  role="img"
                  aria-label="CereBroZen for iPhone — coming soon to the App Store"
                  className="inline-flex cursor-default items-center gap-2.5 rounded-xl border border-white/15 bg-white/5 px-4 py-2.5 opacity-90"
                >
                  <svg viewBox="0 0 24 24" className="h-6 w-6 shrink-0 fill-white" aria-hidden="true">
                    <path d="M17.05 12.04c-.02-2.05 1.68-3.03 1.75-3.08-.95-1.4-2.44-1.59-2.97-1.61-1.26-.13-2.47.74-3.11.74-.64 0-1.63-.72-2.68-.7-1.38.02-2.65.8-3.36 2.03-1.43 2.49-.37 6.17 1.03 8.19.68.99 1.49 2.1 2.56 2.06 1.03-.04 1.42-.66 2.66-.66 1.24 0 1.59.66 2.68.64 1.11-.02 1.81-1.01 2.49-2 .78-1.15 1.11-2.26 1.13-2.32-.02-.01-2.17-.83-2.19-3.29zM15.02 6.3c.57-.69.95-1.65.85-2.6-.82.03-1.81.55-2.4 1.24-.53.6-.99 1.58-.87 2.51.91.07 1.85-.46 2.42-1.15z" />
                  </svg>
                  <span className="flex flex-col leading-tight text-left">
                    <span className="text-[9px] font-semibold uppercase tracking-wider text-zen-400">
                      Coming soon
                    </span>
                    <span className="text-sm font-semibold text-white">App Store</span>
                  </span>
                </span>
                <span
                  role="img"
                  aria-label="CereBroZen for Android — coming soon to Google Play"
                  className="inline-flex cursor-default items-center gap-2.5 rounded-xl border border-white/15 bg-white/5 px-4 py-2.5 opacity-90"
                >
                  <svg viewBox="0 0 24 24" className="h-6 w-6 shrink-0 fill-white" aria-hidden="true">
                    <path d="M3.6 2.2c-.2.2-.3.5-.3.9v17.8c0 .4.1.7.3.9l9.5-9.8L3.6 2.2zm11 8.6 2.7-2.8-8.9-5.1c-.3-.2-.6-.2-.8-.1l7 8zm0 2.4-7 8c.2.1.5.1.8-.1l8.9-5.1-2.7-2.8zM20.8 11l-2.6-1.5-2.9 3 2.9 3 2.6-1.5c.6-.4.6-1.2 0-1.5z" />
                  </svg>
                  <span className="flex flex-col leading-tight text-left">
                    <span className="text-[9px] font-semibold uppercase tracking-wider text-zen-400">
                      Coming soon
                    </span>
                    <span className="text-sm font-semibold text-white">Google Play</span>
                  </span>
                </span>
              </div>
            </div>

            <div className="mt-10">
              <Wordmark className="text-white" />
              <p className="mt-3 max-w-xs text-sm leading-6 text-white/60">
                {site.tagline}.
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="border-t border-white/10 py-5 text-center text-xs text-white/60">
        Copyright © {new Date().getFullYear()} | {site.name}™ | {site.domain} ·
        All rights reserved
      </div>
    </footer>
  );
}
