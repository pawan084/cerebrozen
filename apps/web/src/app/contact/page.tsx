import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import DemoForm from "@/components/DemoForm";
import { site } from "@/lib/site";
import { CheckIcon } from "@/components/icons";

export const metadata: Metadata = {
  title: "Request a Demo",
  description:
    "See CereBroZen in action — a live 30-minute walkthrough tailored to your organization.",
};

const expectations = [
  "A live 30-minute walkthrough, tailored to your org",
  "Real coaching flows for your hardest people-moments",
  "Straight answers on security, privacy, and rollout",
  "The test suite run in front of your security reviewer, on request",
  "A design-partner track if you want to shape the roadmap",
];

export default function ContactPage() {
  return (
    <>
      <PageHero
        eyebrow="Request a demo"
        title="See your organization, coached"
        lead="Tell us a little about your team and we'll show you exactly how CereBroZen would work inside it."
      />

      <section className="mx-auto grid max-w-6xl items-start gap-12 px-6 py-20 lg:grid-cols-[1fr_1.3fr]">
        <Reveal>
          <h2 className="text-xl font-semibold text-brand-900">
            What to expect
          </h2>
          <ul className="mt-6 space-y-4">
            {expectations.map((item) => (
              <li key={item} className="flex gap-3 text-brand-800/75">
                <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-zen-100 text-zen-700">
                  <CheckIcon className="h-3.5 w-3.5" strokeWidth={2.6} />
                </span>
                {item}
              </li>
            ))}
          </ul>
          <div className="mt-10 rounded-3xl bg-mist-50 p-6">
            <p className="text-sm font-semibold text-brand-900">
              Prefer email?
            </p>
            <a
              href={`mailto:${site.email}`}
              className="mt-1 block text-sm font-medium text-zen-700 hover:text-zen-600"
            >
              {site.email}
            </a>
          </div>
        </Reveal>

        <Reveal delay={120}>
          <DemoForm />
        </Reveal>
      </section>
    </>
  );
}
