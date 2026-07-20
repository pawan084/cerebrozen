import type { Metadata } from "next";
import Link from "next/link";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import { CheckIcon, ShieldIcon } from "@/components/icons";
import { site } from "@/lib/site";
import { plusProductSchema } from "@/lib/structured-data";

export const metadata: Metadata = {
  title: "Pricing",
  description:
    "Start free, or unlock everything with CereBro Plus for $59.99 a year. For teams, bring private, aggregate-only coaching to your whole organization.",
};

// Display prices mirror the platform's single source of truth
// (services/platform billing.PRICES, served at GET /billing/prices).
const PLUS_YEARLY = "$59.99";
const PLUS_MONTHLY = "$9.99";

const tiers = [
  {
    name: "Free",
    price: "$0",
    cadence: "always",
    blurb: "The daily practice — yours, private, forever.",
    features: [
      "Daily check-ins",
      "Your AI coach (fair daily limit)",
      "Breathing & grounding tools",
      "A private journal",
      "Crisis support — always free",
      "One guided program",
    ],
    cta: { label: "Start free", href: site.appUrl },
    highlight: false,
  },
  {
    name: "CereBro Plus",
    price: PLUS_YEARLY,
    cadence: `per year · or ${PLUS_MONTHLY}/month`,
    blurb: "Go deeper — unlimited coaching and every tool.",
    features: [
      "Everything in Free",
      "Unlimited coaching — no daily cap",
      "Talk out loud — voice in, spoken replies",
      "Every guided program",
      "Sleep tracking & trends",
      "Weekly insights",
      "Pattern dashboard — transparent AI memory",
      "Ambient soundscapes",
    ],
    cta: { label: "Go Plus", href: site.appUrl },
    highlight: true,
  },
];

export default function PricingPage() {
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(plusProductSchema) }}
      />
      <PageHero
        eyebrow="Pricing"
        title="Start free. Go deeper when you're ready."
        lead="The core practice is free for good. CereBro Plus unlocks the depth — and teams get the whole thing, privately, across the organization."
      />

      <section className="mx-auto max-w-5xl px-6 py-16">
        <div className="grid gap-8 md:grid-cols-2">
          {tiers.map((t) => (
            <Reveal
              key={t.name}
              className={`flex flex-col rounded-3xl border p-8 md:p-10 ${
                t.highlight
                  ? "border-zen-500 bg-white shadow-lg ring-1 ring-zen-500/20"
                  : "border-mist-200 bg-mist-50"
              }`}
            >
              <h2 className="text-xl font-bold text-brand-900">{t.name}</h2>
              <p className="mt-4 flex items-baseline gap-2">
                <span className="text-4xl font-bold tracking-tight text-brand-900">
                  {t.price}
                </span>
                <span className="text-sm text-brand-800/70">{t.cadence}</span>
              </p>
              <p className="mt-3 text-brand-800">{t.blurb}</p>
              <ul className="mt-6 space-y-3">
                {t.features.map((f) => (
                  <li key={f} className="flex items-start gap-3 text-brand-800">
                    <CheckIcon className="mt-0.5 h-5 w-5 flex-none text-zen-600" />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
              <div className="mt-8 pt-2">
                <Link
                  href={t.cta.href}
                  className={`inline-block rounded-full px-7 py-3 text-sm font-semibold transition ${
                    t.highlight
                      ? "bg-zen-500 text-white hover:bg-zen-600"
                      : "border-2 border-zen-500 text-zen-600 hover:bg-zen-500 hover:text-white"
                  }`}
                >
                  {t.cta.label}
                </Link>
              </div>
            </Reveal>
          ))}
        </div>

        <Reveal className="mt-8 flex items-start gap-3 rounded-2xl bg-mist-50 p-6 text-sm text-brand-800">
          <ShieldIcon className="mt-0.5 h-5 w-5 flex-none text-zen-600" />
          <p>
            <span className="font-semibold text-brand-900">Safety is never paywalled.</span>{" "}
            Crisis support, check-ins, and the coach&apos;s core availability stay free on
            every plan. Plus auto-renews and can be cancelled anytime. This is{" "}
            <Link href="/coaching-not-therapy" className="font-medium text-zen-700 hover:text-zen-600">
              coaching, not therapy
            </Link>
            .
          </p>
        </Reveal>
      </section>

      <section className="bg-brand-900 py-20 text-white">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <Reveal>
            <p className="text-sm font-semibold uppercase tracking-widest text-zen-400">
              For teams
            </p>
            <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
              Bring it to your whole organization
            </h2>
            <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-white/80">
              Enterprise is seat-licensed and private by construction — employees get a
              coach they trust, leadership gets aggregate-only trends (never transcripts),
              and it can run on your own infrastructure. Pricing is tailored to your team.
            </p>
            <Link
              href="/contact"
              className="mt-8 inline-block rounded-full bg-zen-500 px-8 py-3.5 text-sm font-semibold text-white transition hover:bg-zen-600"
            >
              Request a demo
            </Link>
          </Reveal>
        </div>
      </section>
    </>
  );
}
