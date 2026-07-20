import type { Metadata } from "next";
import Link from "next/link";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import SectionHeading from "@/components/SectionHeading";
import { CheckIcon, LockIcon, ShieldIcon, GlobeIcon } from "@/components/icons";

export const metadata: Metadata = {
  title: "Sovereignty",
  description:
    "CereBroZen runs on your own infrastructure — on-prem or air-gapped — with a mock model where keys are absent, and it never trains on your data. Coaching you can control.",
};

const pillars = [
  {
    icon: ShieldIcon,
    name: "Safe by construction",
    body: "Crisis routing is deterministic code, not a prompt — a scripted, zero-token takeover runs before any model call, identical on every deployment, and it's a release gate. Non-clinical by design, with human handoff.",
  },
  {
    icon: LockIcon,
    name: "Private by schema",
    body: "The database an HR admin's token reaches holds no content column and no content route — a property the build enforces, not a policy we promise. Journals, moods, and coaching stay on the engine; leadership sees aggregate counts only.",
  },
  {
    icon: GlobeIcon,
    name: "Sovereign by design",
    body: "Every service boots and every test passes with zero external credentials — a mock model stands in. On-prem and air-gapped deployment is possible by construction, and nothing reaches the internet unless you supply the key that turns it on.",
  },
];

const proofs = [
  "On-prem / air-gapped deployment — no coaching incumbent offers it",
  "Never trains on your data",
  "Runs fully offline on a mock model; point it at your own on-prem model when you want one",
  "Emotion inference and durable person-scoring refused at the store layer (regulated mode, on by default)",
  "A keyless self-check (/health/status) reports exactly what a deployment reaches for",
  "Region-neutral crisis helplines work with no network at all",
];

export default function SovereigntyPage() {
  return (
    <>
      <PageHero
        eyebrow="Sovereignty"
        title="Coaching you can run on your own infrastructure"
        lead="Zero-retention is a promise made after a breach. Sovereignty is the architecture that prevents one. CereBroZen is built so your people's most candid conversations never have to leave your control."
      />

      <section className="mx-auto max-w-6xl px-6 py-20">
        <SectionHeading
          eyebrow="The moat is the architecture"
          title="Three properties rivals can only match by rebuilding"
          lead="Every serious competitor now advertises aggregate-only analytics. These are the parts they can't copy with a contract clause."
        />
        <div className="mt-14 grid gap-8 md:grid-cols-3">
          {pillars.map((p) => (
            <Reveal
              key={p.name}
              className="rounded-3xl border border-mist-200 bg-white p-8"
            >
              <p.icon className="h-8 w-8 text-zen-600" />
              <h3 className="mt-5 text-lg font-bold text-brand-900">{p.name}</h3>
              <p className="mt-3 leading-relaxed text-brand-800">{p.body}</p>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="bg-mist-50 py-20">
        <div className="mx-auto max-w-4xl px-6">
          <SectionHeading
            eyebrow="What that buys you"
            title="Control, not a retention policy"
            align="left"
          />
          <ul className="mt-10 grid gap-4 sm:grid-cols-2">
            {proofs.map((p) => (
              <Reveal
                key={p}
                className="flex items-start gap-3 rounded-2xl border border-mist-200 bg-white p-5 text-brand-800"
              >
                <CheckIcon className="mt-0.5 h-5 w-5 flex-none text-zen-600" />
                <span>{p}</span>
              </Reveal>
            ))}
          </ul>
          <Reveal className="mt-8 text-sm text-brand-800/80">
            <p>
              95% of executives call sovereign AI mission-critical within three years, as
              the EU AI Act tightens and data-transfer frameworks wobble. On-prem coaching
              is white space — and it&apos;s how CereBroZen was built from the first commit.
            </p>
          </Reveal>
        </div>
      </section>

      <section className="bg-brand-900 py-20 text-white">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <Reveal>
            <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
              See it run in your environment
            </h2>
            <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-white/80">
              We&apos;ll walk your security team through an air-gapped deployment and the
              schema-level guarantees behind &ldquo;counts, never content.&rdquo;
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
