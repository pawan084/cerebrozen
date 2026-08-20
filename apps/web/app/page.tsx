import { headers } from "next/headers";
import Waitlist from "@/components/Waitlist";
import MobileNav from "@/components/MobileNav";
import AppStoreBadge from "@/components/AppStoreBadge";
import { BrandMark } from "@/components/BrandMark";
import { appHref } from "@/lib/appUrl";
import { SiteFooter } from "@/components/SiteFooter";
import { PhoneMock } from "@/components/PhoneMock";
import Faq, { type FaqEntry } from "@/components/Faq";

const NAV_LINKS = [
  { href: "#experience", label: "The app" },
  { href: "#how", label: "How it works" },
  { href: "#trust", label: "Safety & privacy" },
  { href: "#pricing", label: "Pricing" },
];

// The waitlist is the *secondary* door — the browser app is real and open, so
// the page stopped pretending the only way in is to wait.
const CTA = "Join the waitlist";
const APP_CTA = "Open the app";

// Three principles, on the hairline band directly under the hero.
const PRINCIPLES = [
  {
    title: "Action before browsing",
    body: "Open the app and get one relevant suggestion — not a catalogue to search.",
  },
  {
    title: "Wellness, not diagnosis",
    body: "Clear boundaries, and a route to a human when the app isn't the right help.",
  },
  {
    title: "Privacy you can understand",
    body: "Inspect, edit, switch off or delete what CereBro remembers about you.",
  },
];

const OUTCOMES = [
  {
    title: "Calm now",
    body: "Check in, then start a one-to-five-minute grounding, breathing or thought exercise. Stop whenever you like.",
    link: "See the calming flow",
    href: "#experience",
  },
  {
    title: "Sleep better tonight",
    body: "One wind-down plan, layered soundscapes you mix yourself, and a sleep-safe timer that fades out on its own.",
    link: "See the sleep story",
    href: "#sleep",
  },
  {
    title: "Understand your patterns",
    body: "Journal privately, then read insights drawn from your own check-ins — while you control what is remembered.",
    link: "See the privacy boundary",
    href: "#trust",
  },
];

const SPACES = [
  // `route` is the matching screen in the web app. A signed-out visitor is sent
  // to sign-in and returned here afterwards (`?next=`), so the click keeps its
  // intent instead of dumping everyone on the home screen.
  { tab: "Today", route: "/home", body: "One clear next step, tuned to the time of day and the goals you set." },
  // Explore took Sleep's slot in the tab set (ref/ ruling, REDESIGN_V2.md §6);
  // sleep is still the first thing behind this door, so the copy leads with it.
  { tab: "Explore", route: "/explore", body: "Find a tool by what would help — sleep, sounds, grounding or thought work." },
  // "voice and text" was false of the surface this door opens. The browser
  // chat is text-only and says so itself ("Voice arrives with the mobile
  // apps"), so the landing page was promising a capability the destination
  // openly disclaims. Third one caught on this list for the same reason as the
  // Face ID and "real support" fixes noted below — the rule was written, and
  // this row was missed by it.
  { tab: "Talk", route: "/chat", body: "An AI companion that listens, reflects, and acts — in writing here, with voice on the mobile apps." },
  // Every door here opens the BROWSER app, so every promise has to be true of
  // the browser app. Two were not: "lock it behind Face ID" describes an iOS
  // feature reached by a button that goes to the web (and iOS is not
  // downloadable), and "real support" was un-inventoried phrasing with no
  // mechanism behind it. If a capability is mobile-only, the copy has to carry
  // the surface with it — there is no version of this page where an unreachable
  // feature sells honestly.
  { tab: "Journal", route: "/journal", body: "Private reflection with gentle prompts, and a search that only you can run." },
  { tab: "You", route: "/account", body: "Insights from your own check-ins, privacy controls, and the crisis lines one tap away." },
];

const STEPS = [
  { title: "Check in", body: "Pick the closest feeling and how strong it is. About twenty seconds." },
  { title: "Get one suggestion", body: "One short recommendation, with the reason written in plain language." },
  { title: "Try, switch or stop", body: "Start it, choose something else, or leave. Nothing is lost either way." },
  { title: "Reflect when it helps", body: "Optionally note what worked, and read patterns once there is enough to read." },
];

const SLEEP_POINTS = [
  { title: "Tonight first", body: "One wind-down to follow, not a library to search." },
  { title: "Built to end", body: "Timers, fade-out and clear stopping points. No autoplay feed." },
  { title: "Yours alone", body: "The sleep diary is private, and nothing in it is ranked or scored." },
];

// Both panels say only what the product already does. The crisis ordering is
// fixed by the safety rules: Tele-MANAS 14416 leads in India, then 112.
const TRUST_PANELS = [
  {
    title: "Safety-aware by design",
    intro: "Crisis resources are reachable without signing in, and they never sit behind the paywall.",
    items: [
      "Region-aware crisis lines — Tele-MANAS 14416 first in India, then 112 for emergencies",
      "An optional trusted contact that you nominate, and can remove at any time",
      "Safety scanning only ever adds help — it never blocks or rejects what you wrote",
      "Only India's numbers are marked verified; elsewhere the app says so plainly",
    ],
    cta: "Read the crisis support page",
    href: "/support",
  },
  {
    title: "Memory you can inspect",
    intro: "Privacy is a screen inside the product, not a paragraph in the footer.",
    items: [
      "Consent-first memory — turn it off and CereBro forgets",
      "Export or permanently delete everything from inside the app",
      "No ads, no ad SDKs, no third-party analytics",
      "Usage counts are anonymous and optional",
    ],
    cta: "Read the privacy policy",
    href: "/privacy",
  },
];

const COMPARE_TYPICAL = [
  "A large catalogue to browse",
  "Generic recommendations",
  "Personalisation you cannot see",
  "Safety information filed away in a separate tab",
  "Streaks and feeds that punish a missed day",
];

const COMPARE_CEREBRO = [
  "One suggestion for the moment you are actually in",
  "The reason shown, and alternatives offered",
  "Memory you can read, edit and switch off",
  "Crisis resources built into the experience",
  // Was "a missed day dims, it never resets". The second half was false for the
  // client this page's doors actually open: `metrics.user_streak` forgives one
  // missed day and then does start over, and the browser app renders that count.
  // The forgiveness is real and worth claiming; "never resets" was not, and a
  // guarantee about how gently you are treated is exactly the kind a person
  // notices breaking.
  "A day missed is forgiven, never counted against you",
];

const PLANS = [
  {
    tier: "Free",
    blurb: "For checking in and getting through the ordinary hard evenings.",
    amount: "₹0",
    note: "Forever",
    featured: false,
    items: [
      "Daily check-ins",
      "Breathing and grounding tools",
      "A private journal",
      "Weekly insights from your own check-ins",
      "Crisis resources and a trusted contact",
    ],
  },
  {
    // Every line here is something the backend actually gates. Premium unlocks
    // exactly two things in code — the daily message cap comes off
    // (`services/usage.py`) and narrated audio is served for items flagged
    // premium (`services/media.playback_url`) — so the list is two lines long
    // and says so.
    //
    // Removed 2026-08-15 rather than reworded, because no mechanism existed:
    // "Richer voice sessions" (voice is not metered or tier-gated anywhere) and
    // "Daily plans that adapt to your check-ins" (the adaptive plan ships to
    // every tier — pricing it as Premium implied a gate that does not exist).
    // A pricing table is the one surface where an aspirational line is a
    // charge for something the buyer will not receive.
    tier: "Premium",
    blurb: "For unlimited conversations and narrated sessions.",
    amount: "₹499",
    note: "/month",
    featured: true,
    items: [
      "Everything in Free",
      "Unlimited conversations — Free has a daily message limit",
      "Narrated audio for the sleep and calm sessions that have it",
      "Export and delete, exactly as on Free",
    ],
  },
];

// Shipped commitments, not marketing numbers. Each one maps to a feature marked
// done in docs/PRD.md §2 — we quote no user counts, outcomes, or testimonials
// because there is nothing truthful to quote yet.
const RECEIPTS = [
  "Export or delete everything from inside the app",
  "No ad SDKs, no third-party analytics — usage counts are anonymous and optional",
  "Region-aware crisis lines built in, Tele-MANAS 14416 first in India",
];

const FAQ: FaqEntry[] = [
  { q: "Is CereBro a therapist?", a: "No. CereBro is wellness support — it can listen, reflect, and guide gentle exercises, but it never diagnoses, prescribes, or replaces professional care or emergency help." },
  { q: "Is my data private?", a: "Yes. Memory is consent-first and off-limits unless you allow it. There are no ads or third-party trackers, and you can export or permanently delete everything from inside the app." },
  { q: "When does it launch?", a: "The browser version is open now — create an account at app.cerebrozen.in and use it today, free. The iOS app has no public date yet, and we'd rather say that than invent one; the waitlist hears first.", cta: { label: "Open CereBro in your browser →", href: appHref("/") } },
  { q: "Does it work without a connection?", a: "The browser version — the one you can use today — needs to be online, and the AI companion always does. Offline breathing, grounding, journaling and on-device soundscapes are built in the mobile apps, which aren't publicly installable yet, so that isn't something we can offer you right now." },
  { q: "What platforms is it on?", a: "Any modern browser today, at app.cerebrozen.in. iOS is next, with Android to follow — join the waitlist and we'll send a calm note the moment it's ready.", cta: { label: "Open CereBro in your browser →", href: appHref("/") } },
  { q: "Is there a free plan?", a: "Yes — free forever, with daily check-ins, breathing and grounding tools, a private journal, weekly insights, and the adaptive daily plan. Premium lifts the daily message limit on conversations and unlocks narrated audio for the sessions that have it. Crisis resources stay free on both." },
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

      <a className="skip-link" href="#main">Skip to content</a>

      <nav className="nav">
        <div className="container nav-inner">
          <div className="brand"><BrandMark size={28} /> CereBro</div>
          <div className="nav-links nav-links--full">
            {NAV_LINKS.map((l) => (
              <a href={l.href} key={l.href}>{l.label}</a>
            ))}
            <a className="nav-signin" href={appHref("/signin")}>Sign in</a>
            <a className="btn btn-primary" href={appHref("/")}>{APP_CTA}</a>

            {/* Mobile only (CSS): still a native disclosure — no motion, and it
                opens with JavaScript off. MobileNav adds only the dismissals it
                never had (link, outside click, Escape). */}
            <MobileNav>
              {NAV_LINKS.map((l) => (
                <a href={l.href} key={l.href}>{l.label}</a>
              ))}
              <a href={appHref("/signin")}>Sign in</a>
              <a className="btn btn-primary" href={appHref("/")}>{APP_CTA}</a>
              <a className="btn btn-ghost" href="#waitlist">{CTA}</a>
            </MobileNav>
          </div>
        </div>
      </nav>

      <main id="main">
        {/* ── 1. Hero ─────────────────────────────────────────────────────── */}
        <header className="hero">
          <div className="container hero-grid">
            <div className="hero-copy">
              <p className="status-pill">
                <span className="status-dot" aria-hidden="true" />
                Open now in your browser · iOS next · India-first
              </p>
              <h1>
                A calmer mind,<br />
                <span>one clear next step.</span>
              </h1>
              <p className="lede">
                CereBro turns a short, private check-in into one thing worth doing
                next — breathing, grounding, sleep support, a journal, and a
                companion that knows when to point you at a person instead.
              </p>
              <div className="hero-cta">
                <a className="btn btn-primary" href={appHref("/")}>Open CereBro in your browser</a>
                <a className="btn btn-ghost" href="#how">See how the loop works</a>
              </div>
              <p className="availability">
                Free to start · No card needed · Works in any modern browser
              </p>
              <div className="trust-row" aria-label="Core assurances">
                <span>
                  <b className="check" aria-hidden="true">✓</b>
                  Crisis resources are never behind the paywall
                </span>
                <span>
                  <b className="check" aria-hidden="true">✓</b>
                  Consent-first memory you can inspect
                </span>
                <span>
                  <b className="check" aria-hidden="true">✓</b>
                  No ads and no third-party trackers
                </span>
              </div>
            </div>
            <div className="hero-visual">
              <div className="halo" aria-hidden="true" />
              <PhoneMock kind="today" />
              <div className="note-card a" aria-hidden="true">
                <strong>One next step</strong>
                <small>Not a wall of content.</small>
              </div>
              <div className="note-card b" aria-hidden="true">
                <strong>Private by design</strong>
                <small>See what is remembered, and why.</small>
              </div>
            </div>
          </div>
        </header>

        {/* ── 2. Principles band ──────────────────────────────────────────── */}
        <section className="principles" aria-label="How CereBro is built">
          <div className="container principle-grid">
            {PRINCIPLES.map((p) => (
              <div className="principle" key={p.title}>
                <strong>{p.title}</strong>
                <p>{p.body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* ── 3. Outcomes ─────────────────────────────────────────────────── */}
        <section className="section" id="outcomes">
          <div className="container">
            <div className="shead reveal">
              <div>
                <p className="kicker">Three outcomes</p>
                <h2>Built around the moment you are actually in.</h2>
              </div>
              <p>
                Most wellness apps ask you to browse. CereBro starts from how you
                feel right now, and narrows the choice down to one.
              </p>
            </div>
            <div className="outcome-grid reveal">
              {OUTCOMES.map((o, i) => (
                <article className="outcome" key={o.title}>
                  <span className="orb-art" aria-hidden="true" />
                  <div className="outcome-num" aria-hidden="true">{String(i + 1).padStart(2, "0")}</div>
                  <h3>{o.title}</h3>
                  <p>{o.body}</p>
                  <a className="outcome-link" href={o.href}>
                    {o.link}<span aria-hidden="true">→</span>
                  </a>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* ── 4. The app — first dark panel ───────────────────────────────── */}
        <section className="section" id="experience">
          <div className="container">
            <div className="tour-wrap on-dark reveal">
              <div className="shead">
                <div>
                  <p className="kicker">Inside the app</p>
                  <h2>Five calm spaces, one home.</h2>
                </div>
                <p>
                  A tab for each part of a steady day. Every one of them opens in
                  the browser app, and nothing is louder than it needs to be.
                </p>
              </div>
              <div className="tour-grid">
                <div className="tour-media">
                  <PhoneMock kind="today" />
                </div>
                <div>
                  <div className="spaces">
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
                    New here? <a href={appHref("/signup")}>Create a free account</a> — it takes a minute.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── 5. How the loop works ───────────────────────────────────────── */}
        <section className="section" id="how">
          <div className="container">
            <div className="shead reveal">
              <div>
                <p className="kicker">How it works</p>
                <h2>From a hard moment to one manageable action.</h2>
              </div>
              <p>
                You stay free to switch, skip or stop. CereBro adapts without
                streak pressure — a missed day is forgiven, and nothing counts
                your misses.
              </p>
            </div>
            <div className="steps reveal">
              {STEPS.map((s, i) => (
                <article className="step" key={s.title}>
                  <div className="step-num" aria-hidden="true">{i + 1}</div>
                  <div>
                    <h3>{s.title}</h3>
                    <p>{s.body}</p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* ── 6. Sleep story band ─────────────────────────────────────────── */}
        {/* The pull-quote is a quote, not a heading — so the band carries its own
            label rather than leaving a section with no accessible name. */}
        <section className="section story-band" id="sleep" aria-label="Sleep and sound">
          <div className="container story-grid">
            <div className="story-phones" aria-hidden="true">
              <PhoneMock kind="sleep" />
              <PhoneMock kind="journal" />
            </div>
            <div className="story-copy">
              <p className="kicker">Sleep and sound</p>
              <p className="quote">
                A quieter evening — without turning bedtime into one more thing to
                be good at.
              </p>
              <p className="lede">
                A simple wind-down plan, layered soundscapes you mix yourself, and
                a private sleep diary that nothing scores.
              </p>
              <div className="story-points">
                {SLEEP_POINTS.map((p) => (
                  <div className="story-point" key={p.title}>
                    <b>{p.title}</b>
                    <span>{p.body}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* ── 7. Trust — second dark panel ────────────────────────────────── */}
        <section className="section trust-section on-dark" id="trust">
          <div className="container">
            <div className="shead reveal">
              <div>
                <p className="kicker">Safety and privacy</p>
                <h2>Trust should be visible in the interface.</h2>
              </div>
              <p>
                CereBro is wellness support — not an emergency service, a
                therapist, or a diagnosis. When the app is not the right help, it
                says so and points you to people who are.
              </p>
            </div>
            <div className="trust-grid reveal">
              {TRUST_PANELS.map((panel) => (
                <article className="trust-panel" key={panel.title}>
                  <h3>{panel.title}</h3>
                  <p>{panel.intro}</p>
                  <ul className="trust-list">
                    {panel.items.map((item) => (
                      <li key={item}>
                        <span className="trust-icon" aria-hidden="true">✓</span>
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                  <div className="trust-action">
                    <a className="btn btn-ghost btn-sm" href={panel.href}>{panel.cta}</a>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* ── 8. Typical vs CereBro ───────────────────────────────────────── */}
        <section className="section" id="compare">
          <div className="container">
            <div className="shead reveal">
              <div>
                <p className="kicker">Why CereBro</p>
                <h2>More guidance than a library. More boundaries than a general chatbot.</h2>
              </div>
              <p>
                The difference is not the number of features. It is how the product
                decides, how it explains itself, and where it stops.
              </p>
            </div>
            <div className="compare reveal">
              <article className="compare-card">
                <h3>A typical wellness app</h3>
                <ul className="compare-list">
                  {COMPARE_TYPICAL.map((i) => (<li key={i}>{i}</li>))}
                </ul>
              </article>
              <article className="compare-card is-cerebro">
                <h3>CereBro</h3>
                <ul className="compare-list">
                  {COMPARE_CEREBRO.map((i) => (<li key={i}>{i}</li>))}
                </ul>
              </article>
            </div>
          </div>
        </section>

        {/* ── 9. Pricing ──────────────────────────────────────────────────── */}
        <section className="section" id="pricing">
          <div className="container">
            <div className="shead reveal">
              <div>
                <p className="kicker">Access</p>
                <h2>Start free. Upgrade only if it helps.</h2>
              </div>
              <p>
                No ads, ever. The safety tools stay free on every plan — including
                after a subscription ends.
              </p>
            </div>
            <div className="pricing-grid reveal">
              {PLANS.map((p) => (
                <article className={`price ${p.featured ? "featured" : ""}`} key={p.tier}>
                  {p.featured && <span className="price-tag">Most popular</span>}
                  <h3>{p.tier}</h3>
                  <p>{p.blurb}</p>
                  <div className="amount">
                    {p.amount} <span>{p.note}</span>
                  </div>
                  <ul>
                    {p.items.map((i) => (<li key={i}>{i}</li>))}
                  </ul>
                  <a className="btn btn-primary" href={appHref(p.featured ? "/account" : "/signup")}>
                    {p.featured ? "See Premium in the app" : "Start free"}
                  </a>
                </article>
              ))}
            </div>
            {/* "Cancel at any time" described a flow that cannot start: there is
                no web billing and no store app, so nothing on this page is
                purchasable today. Saying so is better copy anyway — the free tier
                needing no card is the actual selling point. */}
            <p className="price-note reveal">
              Launch pricing for India, shown in ₹. Premium isn&apos;t on sale yet —
              the free plan needs no payment details, and we&apos;ll say plainly when
              paid plans open. The <a href="/refunds">refunds page</a> is written
              already.
            </p>
          </div>
        </section>

        {/* ── 10. FAQ ─────────────────────────────────────────────────────── */}
        <section className="section-sm" id="faq">
          <div className="container">
            <div className="faq reveal">
              <p className="kicker">Frequently asked</p>
              <h2>Questions worth answering clearly.</h2>
              <Faq items={FAQ} />
            </div>
          </div>
        </section>

        {/* ── 11. Final CTA ───────────────────────────────────────────────── */}
        <section className="final" id="waitlist">
          <div className="container">
            <div className="final-box reveal">
              <h2>One useful next step is enough for now.</h2>
              <p>
                The browser app is open today. Join the waitlist and we will send
                one calm note when iOS is ready — nothing else.
              </p>
              <Waitlist />
              <p className="final-note">
                One email, no drip campaign. Unsubscribe from the same note.
              </p>
            </div>

            <div style={{ display: "flex", justifyContent: "center", marginTop: 28 }}>
              <AppStoreBadge />
            </div>

            <ul className="receipts">
              {RECEIPTS.map((r) => (
                <li className="receipt" key={r}><Check />{r}</li>
              ))}
            </ul>
            <p className="receipts-note" style={{ textAlign: "center" }}>
              Things we&apos;ve already built, not things we&apos;re promising —{" "}
              <a href="/privacy">read the privacy policy</a> for the detail.
            </p>
            <p className="disclaimer">
              CereBro is wellness support, not emergency care. In India, Tele-MANAS
              is on 14416 and emergency services on 112. If you are in immediate
              danger, contact your local emergency services right away.
            </p>
          </div>
        </section>
      </main>

      <SiteFooter />
    </>
  );
}
