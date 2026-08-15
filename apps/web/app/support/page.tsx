import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { CrisisLines } from "@/components/CrisisLines";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = pageMeta({
  title: "Support",
  description: "Get help with CereBro. Reach our support team, find answers, and know where to turn in a crisis.",
  path: "/support",
});

export default function Support() {
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
            <Link href="/terms">Terms</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">We&apos;re here to help</p>
          <h1>Support</h1>

          <p>
            Questions, feedback, or trouble with the app? We read every message and
            aim to reply within two business days.
          </p>

          {/* First, not last. The footer links this page as "Crisis support", so
              someone arriving through that link is answered before anything about
              billing or exports. It used to sit below three other sections and
              contain no number at all. */}
          <h2 id="crisis">If you need help right now</h2>
          <p>
            These lines are answered by people, not by CereBro. Tap to call — they
            work whether or not you have an account.
          </p>

          <CrisisLines />

          <p>
            These numbers are for India, where CereBro launches first. Outside
            India, dial your local emergency number, or use{" "}
            <a href="https://findahelpline.com" target="_blank" rel="noreferrer">
              findahelpline.com
            </a>{" "}
            to find a line in your country. Inside the app, the numbers shown
            follow your region.
          </p>

          <h2>Contact us</h2>
          <p>
            Email us at <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a> from
            the address linked to your account if your question concerns your data or
            subscription — it helps us find things faster.
          </p>

          <h2>Common requests</h2>
          <ul>
            <li><strong>Account &amp; data</strong> — export or delete everything from within the app (You → Privacy &amp; data), or email us and we&apos;ll take care of it.</li>
            <li><strong>Privacy questions</strong> — see our <Link href="/privacy">Privacy Policy</Link> or write to <a href="mailto:privacy@cerebrozen.in">privacy@cerebrozen.in</a>.</li>
            <li><strong>Terms</strong> — the rules of the road live in our <Link href="/terms">Terms of Service</Link>.</li>
          </ul>

          <h2>What CereBro is, and isn&apos;t</h2>
          <p>
            CereBro is wellness support — a calm companion for everyday mental fitness.
            It is not therapy, medical care, or crisis care, and it is not a substitute
            for the lines <a href="#crisis">above</a>. Nothing you write to it is read
            by a clinician, and no one is monitoring your account for emergencies.
          </p>
          <p>
            What the app does do: when a message sounds like a hard moment, it adds
            crisis resources for your region alongside its reply. It never refuses to
            listen and never blocks you from writing.
          </p>

          <p className="disclaimer">
            CereBro is wellness support, not emergency care. If you are in immediate danger, contact
            your local emergency services right away.
          </p>
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
