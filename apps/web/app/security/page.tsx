import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = pageMeta({
  title: "Security",
  description: "How CereBro protects your data, and how to report a vulnerability.",
  path: "/security",
});

// Every claim on this page names a mechanism that exists in this repo — the
// CLAIMS_MAP rule applies to security copy the same as marketing copy.
export default function Security() {
  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>

      <nav className="nav">
        <div className="container nav-inner">
          <Link className="brand" href="/">
            <BrandMark size={26} /> CereBro
          </Link>
          <div className="nav-links">
            <Link href="/">Home</Link>
            <Link href="/privacy">Privacy</Link>
            <Link href="/support">Support</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">Built to be boring about it</p>
          <h1>Security</h1>

          <p>
            CereBro holds people&apos;s private reflections, so security is treated as a
            product feature, not an afterthought. This page says what we actually do —
            and how to tell us if you find something we missed.
          </p>

          <h2>What protects your data</h2>
          <ul>
            <li><strong>Encryption in transit</strong> — every connection uses HTTPS/TLS; certificates are provisioned and renewed automatically.</li>
            <li><strong>Passwords are never stored</strong> — only salted hashes (bcrypt). Sign-in attempts are rate-limited.</li>
            <li><strong>Short-lived sessions</strong> — access tokens expire quickly and refresh tokens are single-use: each refresh rotates and revokes the old one.</li>
            <li><strong>Strict content-security policy</strong> — our sites run with per-request script nonces (no inline-script allowance), and the API origin serves no HTML at all.</li>
            <li><strong>Locked-down production</strong> — the database is not exposed to the internet, app ports bind locally behind a TLS proxy, and the API refuses to boot on insecure defaults (weak keys, demo passwords, wildcard CORS).</li>
            <li><strong>Consent enforced server-side</strong> — what the AI may read is checked where the data is read, not just hidden in the UI.</li>
            <li><strong>No third-party trackers</strong> — no ad SDKs, no third-party analytics scripts; usage counts are first-party, anonymous, and optional.</li>
          </ul>

          <h2>Reporting a vulnerability</h2>
          <p>
            If you believe you&apos;ve found a security issue, please email{" "}
            <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a> with
            &quot;Security&quot; in the subject line. Include what you found, how to
            reproduce it, and what you think the impact is.
          </p>
          <ul>
            <li>We&apos;ll acknowledge your report within <strong>3 business days</strong>.</li>
            <li>Please don&apos;t access other people&apos;s data, disrupt the service, or publicly disclose before we&apos;ve had a reasonable chance to fix it.</li>
            <li>We don&apos;t run a paid bounty program yet — we&apos;ll say so plainly rather than imply one — but we credit good-faith reporters if they&apos;d like.</li>
          </ul>
          <p>
            A machine-readable version of this contact lives at{" "}
            <a href="/.well-known/security.txt">/.well-known/security.txt</a>.
          </p>

          <p className="disclaimer">
            CereBro is wellness support, not emergency care. If you are in immediate
            danger, contact your local emergency services right away.
          </p>
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
