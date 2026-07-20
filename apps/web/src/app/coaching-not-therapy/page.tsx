import type { Metadata } from "next";
import Link from "next/link";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import { CheckIcon, ShieldIcon } from "@/components/icons";

export const metadata: Metadata = {
  title: "Coaching, not therapy",
  description:
    "CereBroZen is non-clinical performance and wellbeing coaching — not therapy, not a medical device, and not a crisis service. Here's exactly what that means and how safety works.",
};

const isnt = [
  "Therapy, counselling, or treatment for a mental-health condition",
  "A medical device — it does not diagnose, treat, cure, or prevent any illness",
  "A crisis or emergency service",
  "A substitute for a licensed professional",
];

const is = [
  "Everyday performance and wellbeing coaching — skills, habits, reflection",
  "Guidance to support your own judgement, not clinical advice",
  "Private by design — your conversations are yours",
  "Honest about its limits, in the product and here",
];

export default function CoachingNotTherapyPage() {
  return (
    <>
      <PageHero
        eyebrow="What this is"
        title="Coaching, not therapy — and we mean it"
        lead="The most trustworthy thing an AI wellbeing product can do is be precise about what it is. CereBroZen is non-clinical coaching. That's a deliberate line, and it's where the product stays."
      />

      <section className="mx-auto max-w-5xl px-6 py-16">
        <div className="grid gap-8 md:grid-cols-2">
          <Reveal className="rounded-3xl border border-mist-200 bg-mist-50 p-8">
            <h2 className="text-lg font-bold text-brand-900">What CereBroZen is not</h2>
            <ul className="mt-5 space-y-3">
              {isnt.map((x) => (
                <li key={x} className="flex items-start gap-3 text-brand-800">
                  <span aria-hidden className="mt-0.5 flex-none font-bold text-brand-800/50">
                    —
                  </span>
                  <span>{x}</span>
                </li>
              ))}
            </ul>
          </Reveal>
          <Reveal className="rounded-3xl border border-zen-500/40 bg-white p-8 shadow-sm">
            <h2 className="text-lg font-bold text-brand-900">What it is</h2>
            <ul className="mt-5 space-y-3">
              {is.map((x) => (
                <li key={x} className="flex items-start gap-3 text-brand-800">
                  <CheckIcon className="mt-0.5 h-5 w-5 flex-none text-zen-600" />
                  <span>{x}</span>
                </li>
              ))}
            </ul>
          </Reveal>
        </div>
      </section>

      <section className="bg-mist-50 py-16">
        <div className="mx-auto max-w-3xl px-6 leading-7 text-brand-800">
          <Reveal>
            <h2 className="text-2xl font-bold tracking-tight text-brand-900">
              How safety works
            </h2>
            <p className="mt-4">
              Because we&apos;re not a crisis service, we don&apos;t pretend to be one. If
              the coach detects signs of a crisis or self-harm, it doesn&apos;t try to
              counsel you through it — it steps back and points you to real help. That
              routing is deterministic code, identical on every deployment, and it runs
              before any AI response. The coach is also always disclosed as AI, and as not
              a therapist or crisis service, inside the product.
            </p>
            <p className="mt-4">
              If you&apos;re struggling with something clinical, that&apos;s exactly when a
              licensed professional is the right call — and the app makes it easy to reach
              regional helplines and, if you&apos;ve set one, a trusted contact.
            </p>
          </Reveal>
          <Reveal className="mt-8 flex items-start gap-3 rounded-2xl border border-mist-200 bg-white p-6">
            <ShieldIcon className="mt-0.5 h-5 w-5 flex-none text-zen-600" />
            <p className="text-sm">
              <span className="font-semibold text-brand-900">In an emergency,</span> contact
              your local emergency number or a helpline now — the app surfaces these under
              &ldquo;Urgent support.&rdquo; CereBroZen is wellness support, not emergency care.
            </p>
          </Reveal>
        </div>
      </section>

      <section className="bg-brand-900 py-16 text-white">
        <div className="mx-auto max-w-3xl px-6 text-center">
          <Reveal>
            <p className="text-lg leading-relaxed text-white/85">
              Staying non-clinical isn&apos;t a limitation we apologise for — it&apos;s the
              posture that keeps a wellbeing product safe and trustworthy. See how the
              architecture backs that up.
            </p>
            <Link
              href="/security"
              className="mt-6 inline-block rounded-full bg-zen-500 px-8 py-3.5 text-sm font-semibold text-white transition hover:bg-zen-600"
            >
              How we keep it safe
            </Link>
          </Reveal>
        </div>
      </section>
    </>
  );
}
