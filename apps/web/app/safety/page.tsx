import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";
import { appHref } from "@/lib/appUrl";

export const metadata: Metadata = pageMeta({
  title: "Safety centre",
  description: "How CereBro makes urgent-support pathways visible, what it cannot do, and where to get human help now.",
  path: "/safety",
});

// Structure follows ref/landing.html's `safety` page. The prototype is written
// in the future tense ("in the intended mobile product") because it is a design
// reference; a live page cannot be. Every line below is either true today —
// with the code that makes it true named in CLAIMS_MAP — or explicitly marked
// as not yet in place. Restating the prototype in the present tense would have
// been the exact over-claim check-claims.mjs exists to block.
export default function Safety() {
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
          <p className="eyebrow">Safety centre</p>
          <h1>Human help comes first.</h1>

          <p className="lede">
            CereBro is built to keep urgent-support pathways visible. It cannot assess
            immediate danger, and it does not replace local emergency or crisis services.
          </p>

          <p className="disclaimer">
            <strong>If there is immediate danger, contact your local emergency service now.</strong>{" "}
            In India that is <strong>112</strong>. This website does not operate a live
            crisis line and does not monitor anyone&apos;s safety.
          </p>

          <h2>What is in the product today</h2>
          <ul>
            <li>
              <strong>Urgent support opens without an account.</strong>{" "}
              <a href={appHref("/crisis")}>The crisis page</a> sits outside the sign-in
              boundary, so it loads before you create anything.
            </li>
            <li>
              <strong>Safety tools are not behind the paywall.</strong> The free tier limits
              daily companion messages; it does not gate the crisis page, the safety plan or
              the grounding tools.
            </li>
            <li>
              <strong>Region is shown, and so is whether it is verified.</strong> Support
              details name their region and carry a verification badge. India is the region
              we have verified; other regions say plainly that their numbers are unchecked
              rather than borrowing India&apos;s badge.
            </li>
            <li>
              <strong>Tele-MANAS 14416 leads in India</strong> — free, 24/7, and staffed by
              people, alongside 112 for emergencies.
            </li>
            <li>
              <strong>A personal safety plan is yours alone.</strong> CereBro does not read
              it back to you as advice, score it, or share it.
            </li>
            <li>
              <strong>Scanning never blocks you.</strong> When a message looks like distress,
              CereBro adds resources — it never refuses to reply and never withholds the
              conversation.
            </li>
            <li>
              <strong>Nobody is contacted on your behalf.</strong> A trusted contact is
              someone you choose and you reach; CereBro never messages them automatically.
            </li>
          </ul>

          <h2>What is not in place yet</h2>
          <p>
            These are requirements for a production safety posture, and we would rather list
            them than imply they are done:
          </p>
          <ul>
            <li>Continuous re-verification of helpline directories outside India.</li>
            <li>Independent clinical review and AI-safety evaluation of companion responses.</li>
            <li>A documented incident-response and escalation process.</li>
            <li>Safety testing in every language the interface offers.</li>
            <li>Defined behaviour when an upstream service degrades mid-conversation.</li>
          </ul>

          <h2>Reaching a person</h2>
          <p>
            If you need support right now, use a human service rather than this page:
            emergency services on <strong>112</strong>, or{" "}
            <strong>Tele-MANAS on 14416</strong> for free mental-health support in 20
            languages. Outside India, an international directory is available at{" "}
            <a href="https://findahelpline.com" rel="noreferrer">findahelpline.com</a>.
          </p>

          <p className="disclaimer">
            CereBro is wellness support, not emergency care, and not a medical device. It
            does not diagnose or treat any condition.
          </p>
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
