import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Privacy Policy — CereBro",
  description: "How CereBro collects, uses, and protects your data. Privacy by design.",
};

const UPDATED = "29 June 2026";

export default function Privacy() {
  return (
    <>
      <nav className="nav">
        <div className="container nav-inner">
          <Link className="brand" href="/">
            <span className="dot" /> CereBro
          </Link>
          <div className="nav-links">
            <Link href="/">Home</Link>
            <Link href="/terms">Terms</Link>
          </div>
        </div>
      </nav>

      <main className="section">
        <div className="container legal">
          <p className="eyebrow">Privacy by design</p>
          <h1>Privacy Policy</h1>
          <p className="muted">Last updated {UPDATED}</p>

          <p>
            CereBro is a calm, proactive mental-wellness companion. This policy explains what
            we collect, why, and the controls you have. We designed CereBro to collect as little
            as possible and to put you in charge of what is remembered. CereBro is wellness
            support, not a medical service, and never a substitute for professional care or
            emergency services.
          </p>

          <h2>1. Who we are</h2>
          <p>
            CereBro (&quot;we&quot;, &quot;us&quot;) provides the CereBro mobile app and related
            services. For business (B2B) customers, your employer or organization may sponsor your
            access, but your individual reflections, mood logs, journal entries, and conversations
            are private to you and are <strong>never shared with your employer</strong> in an
            identifiable form.
          </p>

          <h2>2. Data we collect</h2>
          <ul>
            <li><strong>Account</strong> — email, name, and a securely hashed password (or an Apple identifier if you use Sign in with Apple).</li>
            <li><strong>Onboarding choices</strong> — language, companion style, and your self-reflection (motivations and goals) used to personalize your plan and conversation starters.</li>
            <li><strong>Wellness data you create</strong> — mood check-ins, journal entries, chat messages, and plan progress.</li>
            <li><strong>Voice</strong> — when you use the voice companion, audio is transcribed to text. Audio storage is <strong>off by default</strong> and only retained if you explicitly enable it.</li>
            <li><strong>Consent flags</strong> — your choices for what the AI may remember (mood history, AI memory, voice storage, model training).</li>
            <li><strong>Operational data</strong> — minimal logs needed to run the service securely (e.g. rate-limiting, error diagnostics). We do not sell data or use third-party advertising trackers.</li>
          </ul>

          <h2>3. How we use your data</h2>
          <ul>
            <li>To provide the core experience — your plan, insights, journal, and companion.</li>
            <li>To personalize gentle, proactive nudges and conversation starters from <em>your</em> check-ins.</li>
            <li>To detect crisis/safety signals in chat and journal text so we can surface real help. This is wellness support, never a clinical gate or automated report to third parties.</li>
            <li>We use your content to train models <strong>only</strong> if you opt in via the model-training consent flag. It is off by default.</li>
          </ul>

          <h2>4. AI &amp; voice providers</h2>
          <p>
            To generate replies and process voice, we send the minimum necessary text to trusted
            processors: <strong>OpenAI</strong> or <strong>Anthropic</strong> (language),
            <strong> Deepgram</strong> (speech-to-text), and <strong>ElevenLabs</strong> (text-to-speech).
            These providers process data on our behalf under their terms and do not use it to train
            their models when used through our API integrations. The app runs with graceful local
            fallbacks, so core features work even when AI is disabled.
          </p>

          <h2>5. Your controls &amp; rights</h2>
          <ul>
            <li><strong>Consent toggles</strong> — change what the AI remembers at any time in Settings.</li>
            <li><strong>Export</strong> — request a copy of your data from within the app (You → Privacy &amp; data).</li>
            <li><strong>Delete your account</strong> — permanently erase your account and all associated data from within the app (You → Privacy &amp; data → Delete account), or by emailing us. Deletion is immediate and irreversible.</li>
            <li>Depending on your region, you may have rights to access, correct, or restrict processing of your data.</li>
          </ul>

          <h2>6. Data retention &amp; security</h2>
          <p>
            We keep your data only while your account is active. When you delete your account, we
            remove your personal data from our systems promptly. Data is encrypted in transit, access
            is restricted, and passwords are stored only as salted hashes.
          </p>

          <h2>7. Children</h2>
          <p>CereBro is built for adults (18+). We do not knowingly collect data from children.</p>

          <h2>8. Changes</h2>
          <p>
            We will update this policy as the product evolves and revise the date above. Material
            changes will be highlighted in the app.
          </p>

          <h2>9. Contact</h2>
          <p>
            Questions or requests: <a href="mailto:privacy@cerebrozen.in">privacy@cerebrozen.in</a>.
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
            <Link href="/terms">Terms</Link>
          </div>
        </div>
      </footer>
    </>
  );
}
