import type { Metadata } from "next";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = {
  title: "Subprocessors — CereBro",
  description: "The third-party processors CereBro uses, what they receive, and why.",
  alternates: { canonical: "/subprocessors" },
};

// The same list the privacy policy names, in table form with the "what they
// get" column — nothing here that isn't wired in backend/app/services.
const ROWS: { name: string; purpose: string; receives: string }[] = [
  {
    name: "OpenAI / Anthropic",
    purpose: "Language model replies (chat, plans, insights)",
    receives: "The minimum conversation text needed for a reply — only what your consent flags allow",
  },
  {
    name: "Deepgram",
    purpose: "Speech-to-text for the voice companion",
    receives: "Voice audio, transcribed then discarded unless voice storage is switched on (off by default)",
  },
  {
    name: "ElevenLabs",
    purpose: "Text-to-speech for the voice companion",
    receives: "The reply text to be spoken — never your recordings",
  },
  {
    name: "Apple App Store / Google Play (at store launch)",
    purpose: "Subscription billing for the store apps",
    receives: "Purchase and subscription state — the stores never see your wellness data",
  },
];

export default function Subprocessors() {
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
            <Link href="/security">Security</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">Who touches what</p>
          <h1>Subprocessors</h1>

          <p>
            These are the third parties that process data on CereBro&apos;s behalf,
            what each one receives, and why. Each runs under its own data-processing
            terms and does not use your content to train its models through our API
            integrations. Everything else — your account, journal, check-ins, and
            sleep logs — lives on servers we operate.
          </p>

          <table>
            <thead>
              <tr><th>Processor</th><th>Purpose</th><th>What it receives</th></tr>
            </thead>
            <tbody>
              {ROWS.map((r) => (
                <tr key={r.name}>
                  <td><strong>{r.name}</strong></td>
                  <td>{r.purpose}</td>
                  <td>{r.receives}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <p>
            The AI and voice integrations degrade gracefully: with no provider
            configured, the app still runs with local fallbacks — so none of these
            processors is load-bearing for your data staying yours.
          </p>
          <p>
            We&apos;ll update this page before adding a processor, and the privacy
            policy&apos;s date bumps whenever this list changes. Questions:{" "}
            <a href="mailto:privacy@cerebrozen.in">privacy@cerebrozen.in</a>.
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
