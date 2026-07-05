import Waitlist from "@/components/Waitlist";
import AppStoreBadge from "@/components/AppStoreBadge";
import { BrandMark } from "@/components/BrandMark";

const SPACES = [
  { tab: "Home", body: "One clear next step, tuned to the time of day and the goals you set." },
  { tab: "Sleep", body: "Mixable soundscapes and a sleep-safe fade-out timer for a quiet mind." },
  { tab: "Talk", body: "A voice and text AI companion that listens, reflects, and acts." },
  { tab: "Journal", body: "Private reflection with gentle prompts — lock it behind Face ID if you like." },
  { tab: "You", body: "Insights from your real check-ins, privacy controls, and real support." },
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
  { tier: "Premium + Human", amount: "₹1,499", note: "/month", featured: false, items: ["Everything in Premium", "Priority human handoff", "Human sessions (rolling out)"] },
];

const FAQ = [
  { q: "Is CereBro a therapist?", a: "No. CereBro is wellness support — it can listen, reflect, and guide gentle exercises, but it never diagnoses, prescribes, or replaces professional care or emergency help." },
  { q: "Is my data private?", a: "Yes. Memory is consent-first and off-limits unless you allow it. There are no ads or third-party trackers, and you can export or permanently delete everything from inside the app." },
  { q: "Does it work offline?", a: "Core tools — breathing, grounding, journaling, and the on-device soundscapes — work without a connection. The AI companion needs to be online." },
  { q: "What platforms is it on?", a: "iOS first, with Android to follow. Join the waitlist and we'll send a calm note the moment it's ready." },
  { q: "Is there a free plan?", a: "Yes — free forever, with daily check-ins, breathing and grounding tools, a basic journal, and weekly insights. Premium adds the full sleep library and richer voice sessions." },
];

export default function Home() {
  return (
    <>
      <nav className="nav">
        <div className="container nav-inner">
          <div className="brand"><BrandMark size={26} /> CereBro</div>
          <div className="nav-links">
            <a href="#features">Features</a>
            <a href="#spaces">The app</a>
            <a href="#pricing">Pricing</a>
            <a href="#faq">FAQ</a>
            <a className="btn btn-ghost" href="#waitlist">Get early access</a>
          </div>
        </div>
      </nav>

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
              <a className="btn btn-primary" href="#waitlist">Join the waitlist</a>
              <AppStoreBadge />
            </div>
            <div className="trustbar">
              {["Private by design", "No ads, ever", "Crisis-aware", "Built for iOS"].map((t) => (
                <span className="trust" key={t}>✦ {t}</span>
              ))}
            </div>
          </div>
          <div className="hero-device">
            <div className="orb-glow" />
            <div className="phone phone-float">
              <img src="/screens/home.webp" alt="CereBro Home screen" width={640} height={1391} />
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
              <div className="icon">🎙️</div>
              <h3>A companion that listens</h3>
              <p>Talk or text with a warm guide that reflects, runs real exercises, and always points to a next step — never a diagnosis.</p>
            </div>
            <div className="bento-cell b-wide">
              <div className="icon">🌙</div>
              <h3>Layered sleep</h3>
              <p>Blend rain, ocean, wind and a soft drone — each at its own level. A sleep-safe timer fades you out on its own.</p>
            </div>
            <div className="bento-cell">
              <div className="icon">📖</div>
              <h3>A private journal</h3>
              <p>Guided prompts, emotion tags, and an optional Face ID lock.</p>
            </div>
            <div className="bento-cell">
              <div className="icon">🫧</div>
              <h3>Calm in two minutes</h3>
              <p>Breathing, 5-4-3-2-1 grounding, and SOS resets.</p>
            </div>
            <div className="bento-cell b-wide">
              <div className="icon">🔥</div>
              <h3>A streak that forgives</h3>
              <p>Show up once a day. Miss one? It's forgiven. Milestones celebrate quietly — never a guilt trip.</p>
            </div>
            <div className="bento-cell b-wide">
              <div className="icon">🆘</div>
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
                <span className="space-n">{String(i + 1).padStart(2, "0")}</span>
                <div>
                  <h3>{s.tab}</h3>
                  <p>{s.body}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Proactive band */}
      <section className="section" id="proactive">
        <div className="container">
          <div className="band reveal">
            <div className="grid grid-2" style={{ alignItems: "center", gap: 36 }}>
              <div>
                <div className="eyebrow">The app becomes agentic</div>
                <h2 style={{ fontSize: 34 }}>It reaches out, gently — before you have to ask</h2>
                <p style={{ color: "var(--muted)", marginTop: 14 }}>
                  CereBro learns from your check-ins and quietly shapes a plan around
                  them. A rough evening might bring a 2-minute reset; a steady week
                  earns a deeper wind-down.
                </p>
              </div>
              <div>
                {PROACTIVE.map((p) => (
                  <span className="pill" key={p}>✦ {p}</span>
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
        </div>
      </section>

      {/* FAQ */}
      <section className="section" id="faq">
        <div className="container" style={{ maxWidth: 760 }}>
          <div className="section-head reveal"><h2>Questions, answered</h2></div>
          <div className="faq reveal">
            {FAQ.map((f) => (
              <details className="faq-item" key={f.q}>
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
          <p className="disclaimer">
            CereBro is wellness support, not emergency care. If you are in immediate
            danger, contact your local emergency services right away.
          </p>
        </div>
      </section>

      <footer className="footer">
        <div className="container footer-inner">
          <div className="brand" style={{ fontSize: 17 }}><BrandMark size={26} /> CereBro</div>
          <div>
            © {new Date().getFullYear()} CereBro · <a href="/privacy">Privacy</a> ·{" "}
            <a href="/terms">Terms</a> · <a href="/support">Support</a>
          </div>
        </div>
      </footer>
    </>
  );
}
