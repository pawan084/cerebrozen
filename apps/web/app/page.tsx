import { headers } from "next/headers";
import Waitlist from "@/components/Waitlist";
import AppStoreBadge from "@/components/AppStoreBadge";
import { BrandMark } from "@/components/BrandMark";
import { PresenceGlyph, SupportGlyph } from "@/components/Glyphs";
import { appHref } from "@/lib/appUrl";
import { SiteFooter } from "@/components/SiteFooter";

const NAV_LINKS = [
  { href: "#features", label: "Features" },
  { href: "#spaces", label: "The app" },
  { href: "#pricing", label: "Pricing" },
  { href: "#faq", label: "FAQ" },
];

// The waitlist is now the *secondary* door — the browser app is real and open,
// so the page stopped pretending the only way in is to wait.
const CTA = "Join the waitlist";
const APP_CTA = "Open the app";

const SPACES = [
  // `route` is the matching screen in the web app. A signed-out visitor is sent
  // to sign-in and returned here afterwards (`?next=`), so the click keeps its
  // intent instead of dumping everyone on the home screen.
  { tab: "Home", route: "/home", body: "One clear next step, tuned to the time of day and the goals you set." },
  { tab: "Sleep", route: "/sleep", body: "Mixable soundscapes and a sleep-safe fade-out timer for a quiet mind." },
  { tab: "Talk", route: "/chat", body: "A voice and text AI companion that listens, reflects, and acts." },
  { tab: "Journal", route: "/journal", body: "Private reflection with gentle prompts — lock it behind Face ID if you like." },
  { tab: "You", route: "/account", body: "Insights from your real check-ins, privacy controls, and real support." },
];

const PROACTIVE = [
  "Agentic daily plan that adapts to your mood + journal",
  "Gentle nudges — never noisy, always easy to mute",
  "Timed check-ins when patterns shift",
  "Crisis-aware support that surfaces real help",
];

const SAFETY = [
  { title: "Consent-first memory", body: "You decide what CereBro remembers. Turn it off and it forgets. Export or delete everything, anytime." },
  { title: "Honest about what it is", body: "A supportive AI companion — not a therapist, diagnosis, or crisis service. It says so, clearly and often." },
  { title: "Crisis-aware by design", body: "Region-correct crisis lines are always a tap away, and you can nominate a trusted contact to notify." },
  { title: "No ads, ever", body: "Your calm isn't the product. No third-party trackers, no ad SDKs, no selling your data." },
];

const PLANS = [
  { tier: "Free", amount: "₹0", note: "Forever", featured: false, items: ["Daily check-ins", "Breathing & grounding", "Basic journal", "Weekly insights"] },
  { tier: "Premium", amount: "₹499", note: "/month", featured: true, items: ["Everything in Free", "Full sleep library + mixing", "Richer voice sessions", "Agentic plans"] },
  // Human support is not built yet (docs/PRD.md marks it concept-only), so every
  // line that depends on it has to carry the same "rolling out" qualifier.
  { tier: "Premium + Human", amount: "₹1,499", note: "/month", featured: false, items: ["Everything in Premium", "Priority human handoff (rolling out)", "Human sessions (rolling out)"] },
];

// Shipped commitments, not marketing numbers. Each one maps to a feature marked
// done in docs/PRD.md §2 — we quote no user counts, outcomes, or testimonials
// because there is nothing truthful to quote yet.
const RECEIPTS = [
  "Export or delete everything from inside the app",
  "No ad SDKs, no third-party analytics — usage counts are anonymous and optional",
  "Region-aware crisis lines built in, Tele-MANAS 14416 first in India",
];

const FAQ = [
  { q: "Is CereBro a therapist?", a: "No. CereBro is wellness support — it can listen, reflect, and guide gentle exercises, but it never diagnoses, prescribes, or replaces professional care or emergency help." },
  { q: "Is my data private?", a: "Yes. Memory is consent-first and off-limits unless you allow it. There are no ads or third-party trackers, and you can export or permanently delete everything from inside the app." },
  { q: "When does it launch?", a: "The browser version is open now — create an account at app.cerebrozen.in and use it today, free. The iOS app has no public date yet, and we'd rather say that than invent one; the waitlist hears first." },
  { q: "Does it work offline?", a: "In the mobile apps, core tools — breathing, grounding, journaling, and the on-device soundscapes — work without a connection. The browser version needs to be online, and the AI companion always does." },
  { q: "What platforms is it on?", a: "Any modern browser today, at app.cerebrozen.in. iOS is next, with Android to follow — join the waitlist and we'll send a calm note the moment it's ready." },
  { q: "Is there a free plan?", a: "Yes — free forever, with daily check-ins, breathing and grounding tools, a basic journal, and weekly insights. Premium adds the full sleep library and richer voice sessions." },
];

// Search engines read the FAQ from the same array the page renders, so the two
// can never drift. The CSP forbids un-nonced inline scripts (middleware.ts) and
// that includes ld+json, hence the nonce.
const FAQ_JSONLD = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  mainEntity: FAQ.map((f) => ({
    "@type": "Question",
    name: f.q,
    acceptedAnswer: { "@type": "Answer", text: f.a },
  })),
};

// App + org schema, from the same truthful facts the page states: the browser
// app is what exists today, the free tier is the honest lowest price, and no
// ratings are claimed because none exist yet.
const APP_JSONLD = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "CereBro",
  applicationCategory: "HealthApplication",
  operatingSystem: "Web browser",
  url: "https://app.cerebrozen.in",
  description:
    "A calm, private mental-wellness companion: daily check-ins, breathing and grounding, a private journal, sleep tools, and an AI companion that adapts to how you feel.",
  offers: { "@type": "Offer", price: "0", priceCurrency: "INR" },
  publisher: { "@type": "Organization", name: "CereBro", url: "https://cerebrozen.in" },
};

const ORG_JSONLD = {
  "@context": "https://schema.org",
  "@type": "Organization",
  name: "CereBro",
  url: "https://cerebrozen.in",
  logo: "https://cerebrozen.in/brand/cerebro-mark.svg",
  email: "support@cerebrozen.in",
};

function Check() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4.5 12.6 9.3 17.4 19.5 7.2" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export default async function Home() {
  // `await` so this compiles against both the sync (Next 14) and async (Next 15+)
  // headers() signatures.
  const nonce = (await headers()).get("x-nonce") ?? undefined;

  return (
    <>
      <script
        type="application/ld+json"
        nonce={nonce}
        dangerouslySetInnerHTML={{ __html: JSON.stringify(FAQ_JSONLD) }}
      />
      <script
        type="application/ld+json"
        nonce={nonce}
        dangerouslySetInnerHTML={{ __html: JSON.stringify(APP_JSONLD) }}
      />
      <script
        type="application/ld+json"
        nonce={nonce}
        dangerouslySetInnerHTML={{ __html: JSON.stringify(ORG_JSONLD) }}
      />

      {/* Hoisted to <head> by Next: the hero image is the LCP element and would
          otherwise only be discovered after HTML+CSS parse. */}
      <link rel="preload" as="image" href="/brand/banner-hero.jpg" fetchPriority="high" />

      <a className="skip-link" href="#main">Skip to content</a>

      <nav className="nav">
        <div className="container nav-inner">
          <div className="brand"><BrandMark size={26} /> CereBro</div>
          <div className="nav-links nav-links--full">
            {NAV_LINKS.map((l) => (
              <a href={l.href} key={l.href}>{l.label}</a>
            ))}
            <a className="nav-signin" href={appHref("/signin")}>Sign in</a>
            <a className="btn btn-primary" href={appHref("/")}>{APP_CTA}</a>

            {/* Mobile only (CSS): a native disclosure — no JS, no motion. */}
            <details className="nav-menu">
              <summary>Menu</summary>
              <div className="nav-menu-panel">
                {NAV_LINKS.map((l) => (
                  <a href={l.href} key={l.href}>{l.label}</a>
                ))}
                <a href={appHref("/signin")}>Sign in</a>
                <a className="btn btn-primary" href={appHref("/")}>{APP_CTA}</a>
                <a className="btn btn-ghost" href="#waitlist">{CTA}</a>
              </div>
            </details>
          </div>
        </div>
      </nav>

      <main id="main">
        {/* Hero — copy + phone mockup */}
        <header className="hero">
          <div className="container hero-grid">
            <div className="hero-copy">
              <div className="eyebrow">Daily mental fitness</div>
              <h1>Your quiet space<br />for a calmer mind</h1>
              <p className="lead">
                Better sleep, calmer focus, and a companion that gently adapts to how you
                actually feel — not another feed to keep up with.
              </p>
              <div className="hero-cta">
                <a className="btn btn-primary" href={appHref("/")}>Open CereBro in your browser</a>
                <a className="btn btn-ghost" href="#waitlist">{CTA}</a>
                <AppStoreBadge />
              </div>
              <div className="trustbar">
                {["Private by design", "No ads, ever", "Crisis-aware", "Works in your browser"].map((t) => (
                  <span className="trust" key={t}>
                    <span className="trust-glyph" aria-hidden="true">✦</span>
                    {t}
                  </span>
                ))}
              </div>
            </div>
            <div className="hero-device">
              <div className="orb-glow" />
              <div className="hero-banner phone-float">
                <img
                  src="/brand/banner-hero.jpg"
                  alt="CereBro on three iPhones — the home check-in and sleep screens"
                  width={970}
                  height={1080}
                  fetchPriority="high"
                />
              </div>
            </div>
          </div>
        </header>

        {/* Features — bento grid */}
        <section className="section" id="features">
          <div className="container">
            <div className="section-head reveal">
              <h2>Everything to steady the day</h2>
              <p>Small, science-informed tools — designed to feel calm, never clinical.</p>
            </div>
            <div className="bento reveal">
              <div className="bento-cell b-lg accent">
                <div className="icon" aria-hidden="true">🎙️</div>
                <h3>A companion that listens</h3>
                <p>Talk or text with a warm guide that reflects, runs real exercises, and always points to a next step — never a diagnosis.</p>
              </div>
              <div className="bento-cell b-wide">
                <div className="icon" aria-hidden="true">🌙</div>
                <h3>Layered sleep</h3>
                <p>Blend rain, ocean, wind and a soft drone — each at its own level. A sleep-safe timer fades you out on its own.</p>
              </div>
              <div className="bento-cell">
                <div className="icon" aria-hidden="true">📖</div>
                <h3>A private journal</h3>
                <p>Guided prompts, emotion tags, and an optional Face ID lock.</p>
              </div>
              <div className="bento-cell">
                <div className="icon" aria-hidden="true">🫧</div>
                <h3>Calm in two minutes</h3>
                <p>Breathing, 5-4-3-2-1 grounding, and SOS resets.</p>
              </div>
              <div className="bento-cell b-wide">
                <div className="icon" aria-hidden="true"><PresenceGlyph size={26} /></div>
                <h3>Presence, not streaks</h3>
                <p>The ring fills with the days you showed up. Miss one and it simply dims — it never resets, and nothing ever counts your misses.</p>
              </div>
              <div className="bento-cell b-wide">
                <div className="icon" aria-hidden="true"><SupportGlyph size={26} /></div>
                <h3>Help when it matters</h3>
                <p>Locale-aware crisis lines one tap away, plus an optional trusted contact you choose to notify.</p>
              </div>
            </div>
          </div>
        </section>

        {/* See it in action — device showcase */}
        <section className="section" id="showcase">
          <div className="container">
            <div className="section-head reveal">
              <h2>Calm you can see</h2>
              <p>Designed to feel like a quiet room, not a dashboard.</p>
            </div>
            <div className="showcase reveal">
              <figure className="shot">
                <div className="phone"><img src="/screens/sleep-player.webp" alt="CereBro sleep player" loading="lazy" width={640} height={1391} /></div>
                <figcaption>Layered soundscapes with a sleep-safe timer</figcaption>
              </figure>
              <figure className="shot shot-raise">
                <div className="phone"><img src="/screens/journal-entry.webp" alt="CereBro journal" loading="lazy" width={640} height={1391} /></div>
                <figcaption>A private journal with gentle daily prompts</figcaption>
              </figure>
            </div>
          </div>
        </section>

        {/* The five spaces */}
        <section className="section" id="spaces">
          <div className="container">
            <div className="section-head reveal">
              <h2>Five calm spaces, one home</h2>
              <p>A tab for each part of a steady day — nothing louder than it needs to be.</p>
            </div>
            <div className="spaces reveal">
              {SPACES.map((s, i) => (
                <div className="space" key={s.tab}>
                  <span className="space-n" aria-hidden="true">{String(i + 1).padStart(2, "0")}</span>
                  <div>
                    <h3>{s.tab}</h3>
                    <p>{s.body}</p>
                    <a className="space-open" href={appHref(s.route)}>
                      Open {s.tab}
                      <span aria-hidden="true">→</span>
                    </a>
                  </div>
                </div>
              ))}
            </div>
            <p className="spaces-note">
              Each space opens in the browser app. New here?{" "}
              <a href={appHref("/signup")}>Create a free account</a> — it takes a minute.
            </p>
          </div>
        </section>

        {/* Proactive band */}
        <section className="section" id="proactive">
          <div className="container">
            <div className="band reveal">
              <div className="grid grid-2" style={{ alignItems: "center", gap: 36 }}>
                <div>
                  <div className="eyebrow">The app becomes agentic</div>
                  <h2>It reaches out, gently — before you have to ask</h2>
                  <p style={{ color: "var(--muted)", marginTop: 14 }}>
                    CereBro learns from your check-ins and quietly shapes a plan around
                    them. A rough evening might bring a 2-minute reset; a steady week
                    earns a deeper wind-down.
                  </p>
                </div>
                <div>
                  {PROACTIVE.map((p) => (
                    <span className="pill" key={p}>
                      <span aria-hidden="true">✦</span>
                      {p}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Safety */}
        <section className="section" id="safety">
          <div className="container">
            <div className="section-head reveal">
              <h2>Care you can trust</h2>
              <p>Built privacy-first, honest about its limits, and always pointing to real help.</p>
            </div>
            <div className="grid grid-2 reveal">
              {SAFETY.map((s) => (
                <div className="card safety-card" key={s.title}>
                  <h3>{s.title}</h3>
                  <p>{s.body}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Pricing */}
        <section className="section" id="pricing">
          <div className="container">
            <div className="section-head reveal">
              <h2>Start free. Upgrade when it helps.</h2>
              <p>No ads, ever. Your calm isn&apos;t the product.</p>
            </div>
            <div className="grid grid-3 reveal">
              {PLANS.map((p) => (
                <div className={`card price-card ${p.featured ? "featured" : ""}`} key={p.tier}>
                  {p.featured && <span className="badge">Most popular</span>}
                  <div className="tier">{p.tier}</div>
                  <div className="amount">
                    {p.amount} <span style={{ fontSize: 15, color: "var(--muted)" }}>{p.note}</span>
                  </div>
                  <ul>
                    {p.items.map((i) => (<li key={i}>{i}</li>))}
                  </ul>
                </div>
              ))}
            </div>
            <p className="price-note reveal">Launch pricing · India, shown in ₹. Cancel anytime.</p>
          </div>
        </section>

        {/* FAQ */}
        <section className="section" id="faq">
          <div className="container" style={{ maxWidth: 760 }}>
            <div className="section-head reveal"><h2>Questions, answered</h2></div>
            <div className="faq reveal">
              {FAQ.map((f) => (
                // Shared name → the browser keeps one answer open at a time.
                <details className="faq-item" name="faq" key={f.q}>
                  <summary>{f.q}</summary>
                  <p>{f.a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        {/* Final CTA / waitlist */}
        <section className="section waitlist" id="waitlist">
          <div className="container">
            <div className="section-head reveal" style={{ marginBottom: 8 }}>
              <h2>Be first to feel the calm</h2>
              <p>Join the waitlist for early access on iOS.</p>
            </div>
            <Waitlist />
            <ul className="receipts">
              {RECEIPTS.map((r) => (
                <li className="receipt" key={r}><Check />{r}</li>
              ))}
            </ul>
            <p className="receipts-note">
              Things we&apos;ve already built, not things we&apos;re promising —{" "}
              <a href="/privacy">read the privacy policy</a> for the detail.
            </p>
            <p className="disclaimer">
              CereBro is wellness support, not emergency care. If you are in immediate
              danger, contact your local emergency services right away.
            </p>
          </div>
        </section>
      </main>

      <SiteFooter />
    </>
  );
}
