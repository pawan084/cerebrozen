import Waitlist from "@/components/Waitlist";

const FEATURES = [
  { icon: "🌙", title: "Sleep that comes easier", body: "Sleep stories, soundscapes, and wind-down breathing that fade out on their own." },
  { icon: "🫧", title: "Calm in two minutes", body: "Guided breathing, grounding, and SOS resets for the moments that spike." },
  { icon: "📖", title: "Private journaling", body: "Reflect with consent-first prompts. You choose what the AI can ever read." },
  { icon: "🎙️", title: "A companion that listens", body: "Talk or text with a warm guide — never a diagnosis, always a next step." },
  { icon: "📊", title: "Insights you can feel", body: "Weekly patterns from your real check-ins, not vanity streaks." },
  { icon: "🔒", title: "Privacy by design", body: "Granular memory controls and on-device-first data. Delete anything, anytime." },
];

const PROACTIVE = [
  "Agentic daily plan that adapts to your mood + journal",
  "Gentle nudges — never noisy, always easy to mute",
  "Timed check-ins when patterns shift",
  "Crisis-aware support that surfaces real help",
];

const PLANS = [
  {
    tier: "Free",
    amount: "₹0",
    note: "Forever",
    featured: false,
    items: ["Daily check-ins", "Breathing & grounding", "Basic journal", "Weekly insights"],
  },
  {
    tier: "Premium",
    amount: "₹499",
    note: "/month",
    featured: true,
    items: ["Everything in Free", "Full sleep library", "Offline downloads", "Unlimited voice companion", "Agentic plans"],
  },
  {
    tier: "Premium + Human",
    amount: "₹1,499",
    note: "/month",
    featured: false,
    items: ["Everything in Premium", "Coach & therapist booking", "Priority human handoff"],
  },
];

export default function Home() {
  return (
    <>
      <nav className="nav">
        <div className="container nav-inner">
          <div className="brand">
            <span className="dot" /> CereBro
          </div>
          <div className="nav-links">
            <a href="#features">Features</a>
            <a href="#proactive">Proactive</a>
            <a href="#pricing">Pricing</a>
            <a className="btn btn-ghost" href="#waitlist">Get early access</a>
          </div>
        </div>
      </nav>

      <header className="hero">
        <div className="container">
          <div className="orb" />
          <div className="eyebrow">Daily mental fitness</div>
          <h1>Your quiet space<br />for a calmer mind</h1>
          <p className="lead">
            Better sleep, calmer focus, and a companion that gently adapts to how you
            actually feel — not another feed to keep up with.
          </p>
          <div className="hero-cta">
            <a className="btn btn-primary" href="#waitlist">Join the waitlist</a>
            <a className="btn btn-ghost" href="#proactive">See how it works</a>
          </div>
        </div>
      </header>

      <section className="section" id="features">
        <div className="container">
          <div className="section-head">
            <h2>Everything to steady the day</h2>
            <p>Small, science-informed tools — designed to feel calm, never clinical.</p>
          </div>
          <div className="grid grid-3">
            {FEATURES.map((f) => (
              <div className="card" key={f.title}>
                <div className="icon">{f.icon}</div>
                <h3>{f.title}</h3>
                <p>{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section" id="proactive">
        <div className="container">
          <div className="band">
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

      <section className="section" id="pricing">
        <div className="container">
          <div className="section-head">
            <h2>Start free. Upgrade when it helps.</h2>
            <p>No ads, ever. Your calm isn&apos;t the product.</p>
          </div>
          <div className="grid grid-3">
            {PLANS.map((p) => (
              <div className={`card price-card ${p.featured ? "featured" : ""}`} key={p.tier}>
                {p.featured && <span className="badge">Most popular</span>}
                <div className="tier">{p.tier}</div>
                <div className="amount">
                  {p.amount} <span style={{ fontSize: 15, color: "var(--muted)" }}>{p.note}</span>
                </div>
                <ul>
                  {p.items.map((i) => (
                    <li key={i}>{i}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section waitlist" id="waitlist">
        <div className="container">
          <div className="section-head" style={{ marginBottom: 8 }}>
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
          <div className="brand" style={{ fontSize: 17 }}>
            <span className="dot" /> CereBro
          </div>
          <div>
            © {new Date().getFullYear()} CereBro · <a href="/privacy">Privacy</a> ·{" "}
            <a href="/terms">Terms</a>
          </div>
        </div>
      </footer>
    </>
  );
}
