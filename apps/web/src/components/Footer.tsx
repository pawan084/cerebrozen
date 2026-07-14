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
