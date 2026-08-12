import type { Metadata } from "next";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = {
  title: "Accessibility — CereBro",
  description:
    "What CereBro's interfaces do for accessibility today, and what still needs testing with disabled users.",
  alternates: { canonical: "/accessibility" },
};

// Structure follows ref/landing.html's `accessibility` page. The prototype
// separates "website reference" from "production validation"; that split is the
// honest part and is kept. Claims about the website are checked against this
// repo (contrast gate, reduced-motion handling, focus styles); claims about the
// apps are listed as outstanding, because no assistive-technology pass has been
// run with disabled users and saying otherwise would be an accessibility claim
// we cannot evidence.
export default function Accessibility() {
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
          <p className="eyebrow">Accessibility</p>
          <h1>A calm interface must also be usable.</h1>

          <p className="lede">
            A product for people under stress has no business being difficult to operate.
            The target is keyboard access, screen-reader semantics, large text, sufficient
            contrast, generous touch targets and respect for reduced-motion preferences.
          </p>

          <h2>On this website</h2>
          <ul>
            <li>
              <strong>Content does not depend on scroll effects.</strong> Text is present in
              the markup and readable without JavaScript reveal animations.
            </li>
            <li>
              <strong>Every page starts with a skip link</strong> to the main region, and
              headings run in order without skipped levels.
            </li>
            <li>
              <strong>Focus stays visible.</strong> Interactive controls keep a visible focus
              ring rather than removing the outline.
            </li>
            <li>
              <strong>Motion follows the operating system.</strong> Animation is reduced when{" "}
              <code>prefers-reduced-motion</code> is set.
            </li>
            <li>
              <strong>Colour is checked automatically.</strong> Every text and fill role in
              our design tokens is gated in CI against the surfaces it can land on — 4.5:1
              for text, 3:1 for fills — so a palette change cannot quietly drop below the
              floor.
            </li>
          </ul>

          <h2>In the apps</h2>
          <ul>
            <li>
              <strong>Reduce Motion is treated as a contract.</strong> Looping and entrance
              animations have a static fallback rather than disappearing.
            </li>
            <li>
              <strong>Both appearances are contrast-tested.</strong> Dawn and Night are gated
              at 4.5:1 for text roles by an automated test, not by eye.
            </li>
            <li>
              <strong>Text scales with your device setting</strong> rather than being pinned
              to a fixed size.
            </li>
          </ul>

          <h2>Not yet validated</h2>
          <p>
            We have not completed the following, and list them rather than imply a
            conformance level we have not earned:
          </p>
          <ul>
            <li>Testing with TalkBack, VoiceOver, Voice Access and switch access.</li>
            <li>200% text and display-zoom passes across every screen.</li>
            <li>Colour-correction and colour-blindness review.</li>
            <li>Captions and transcripts for audio content.</li>
            <li>Real-device testing with disabled users — the only test that settles it.</li>
            <li>A formal WCAG 2.2 AA audit. We do not claim conformance today.</li>
          </ul>

          <h2>Telling us something is unusable</h2>
          <p>
            If something here or in the apps blocks you, please email{" "}
            <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a> with
            &quot;Accessibility&quot; in the subject line and tell us what you were trying to
            do. We will acknowledge within 3 business days.
          </p>

          <p className="disclaimer">
            CereBro is wellness support, not emergency care. If you are in immediate danger,
            contact your local emergency services right away.
          </p>
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
