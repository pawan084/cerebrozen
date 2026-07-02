import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Support — CereBro",
  description: "Get help with CereBro. Reach our support team, find answers, and know where to turn in a crisis.",
};

export default function Support() {
  return (
    <>
      <nav className="nav">
        <div className="container nav-inner">
          <Link className="brand" href="/">
            <span className="dot" /> CereBro
          </Link>
          <div className="nav-links">
            <Link href="/">Home</Link>
            <Link href="/privacy">Privacy</Link>
            <Link href="/terms">Terms</Link>
          </div>
        </div>
      </nav>

      <main className="section">
        <div className="container legal">
          <p className="eyebrow">We&apos;re here to help</p>
          <h1>Support</h1>

          <p>
            Questions, feedback, or trouble with the app? We read every message and
            aim to reply within two business days.
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

          <h2>A note on crisis care</h2>
          <p>
            CereBro is wellness support — a calm companion for everyday mental fitness.
            It is not therapy, medical care, or crisis care. If you are in immediate
            danger or thinking about harming yourself, please contact your local
            emergency services or a crisis hotline in your region right away. The app
            also surfaces localized crisis resources whenever it matters.
          </p>

          <p className="disclaimer">
            CereBro is wellness support, not emergency care. If you are in immediate danger, contact
            your local emergency services right away.
          </p>
        </div>
      </main>

      <footer className="footer">
        <div className="container footer-inner">
          <Link className="brand" href="/" style={{ fontSize: 17 }}>
            <span className="dot" /> CereBro
          </Link>
          <div>
            © {new Date().getFullYear()} CereBro · <Link href="/privacy">Privacy</Link> ·{" "}
            <Link href="/terms">Terms</Link> · <Link href="/support">Support</Link>
          </div>
        </div>
      </footer>
    </>
  );
}
