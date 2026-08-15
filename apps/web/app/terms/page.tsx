import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = pageMeta({
  title: "Terms of Use",
  description: "The terms governing your use of CereBro.",
  path: "/terms",
});

const UPDATED = "29 June 2026";

export default function Terms() {
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
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">The agreement</p>
          <h1>Terms of Use</h1>
          <p className="muted">Last updated {UPDATED}</p>

          <h2>1. Wellness, not medical care</h2>
          <p>
            CereBro provides self-guided wellness tools and an AI companion for reflection. It does
            <strong> not</strong> provide medical or mental-health diagnosis, treatment, or therapy,
            and is not a substitute for professional care. If you are in crisis or immediate danger,
            contact your local emergency services or a crisis helpline right away.
          </p>

          <h2>2. Eligibility</h2>
          <p>You must be 18 or older to use CereBro.</p>

          <h2>3. Your account</h2>
          <p>
            You are responsible for keeping your credentials secure and for activity under your
            account. You may delete your account at any time from within the app.
          </p>

          <h2>4. Acceptable use</h2>
          <p>
            Use CereBro only for lawful, personal wellness purposes. Do not misuse, disrupt, or
            attempt to reverse-engineer the service, and do not rely on it for emergencies.
          </p>

          <h2>5. AI limitations</h2>
          <p>
            AI-generated content can be inaccurate or incomplete. Treat suggestions as supportive
            prompts, not professional advice, and use your own judgment.
          </p>

          <h2>6. Subscriptions</h2>
          <p>
            <strong>Nothing is on sale yet.</strong> There is no web billing and no store app, so
            today there is no way to pay us and no subscription to cancel. The browser app&apos;s
            free tier asks for no payment details.
          </p>
          <p>
            When paid plans do launch, they will renew per the terms shown at purchase; purchases
            made through an app store will be managed through that store account, and you will be
            able to cancel anytime with access continuing to the end of the billing period. Our{" "}
            <Link href="/refunds">refunds page</Link> holds the detail and is the page we will
            update first.
          </p>

          <h2>7. Disclaimers &amp; liability</h2>
          <p>
            The service is provided &quot;as is&quot; without warranties. To the extent permitted by
            law, we are not liable for indirect or consequential damages arising from your use of the
            service.
          </p>

          <h2>8. Changes</h2>
          <p>We may update these terms and will revise the date above. Continued use means acceptance.</p>

          <h2>9. Contact</h2>
          <p>
            Questions: <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a>.
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
