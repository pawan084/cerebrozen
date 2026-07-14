import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import SectionHeading from "@/components/SectionHeading";
import CtaBanner from "@/components/home/CtaBanner";
import { BrainIcon, LeafIcon, TargetIcon } from "@/components/icons";

export const metadata: Metadata = {
  title: "About Us",
  description:
    "CereBroZen's mission: a sharp mind and a calm one are the same mind. We're building the coach every working person deserves.",
};

const values = [
  {
    icon: BrainIcon,
    title: "Cerebro — clarity",
    body: "We believe most workplace struggle is fog, not weakness. Our job is to help people see their situation clearly enough to act on it.",
  },
  {
    icon: LeafIcon,
    title: "Zen — calm",
    body: "Sustainable performance is calm performance. We optimize for steady follow-through over adrenaline, and recovery over heroics.",
  },
  {
    icon: TargetIcon,
    title: "Action — always",
    body: "Insight without action is entertainment. Every conversation our coach holds ends in one small, concrete, trackable next step.",
  },
];

export default function AboutPage() {
  return (
    <>
      <PageHero
        eyebrow="About us"
        title="A sharp mind and a calm mind are the same mind"
        lead="CereBroZen exists because great coaching changes careers — and almost nobody gets it. We're making it a default part of work, for everyone, in every role."
      />

      <section className="mx-auto max-w-3xl px-6 py-20">
        <Reveal>
          <h2 className="text-2xl font-bold text-brand-900">Our story</h2>
          <div className="mt-6 space-y-5 leading-8 text-brand-800/75">
            <p>
              The idea came from a pattern we couldn&apos;t unsee: in every
              organization we&apos;d worked in, the people who grew fastest
              weren&apos;t the smartest — they were the ones who happened to
              have a great manager, mentor, or coach in their corner at the
              right moments. Everyone else was left to figure it out alone.
            </p>
            <p>
              Executive coaching worked, but it could never scale: one coach,
              one client, a few hours a month, at a price that limited it to
              the top of the org chart. Meanwhile, the moments that actually
              decide performance — a hesitant decision at 2 p.m., a feedback
              conversation postponed for the fourth time — happen everywhere,
              every day, to everyone.
            </p>
            <p>
              Modern AI finally made the impossible arithmetic work: coaching
              quality that used to be reserved for executives, available to
              every employee, in the exact moment they need it. We built
              CereBroZen — <em>cerebro</em> for clarity of mind, <em>zen</em>{" "}
              for calm under pressure — to be that coach.
            </p>
          </div>
        </Reveal>
      </section>

      <section className="bg-mist-50">
        <div className="mx-auto max-w-7xl px-6 py-20">
          <SectionHeading eyebrow="What we believe" title="Three ideas we run on" />
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {values.map((v, i) => (
              <Reveal
                key={v.title}
                delay={i * 110}
                className="rounded-3xl border border-mist-200 bg-white p-8 text-center"
              >
                <span className="inline-flex rounded-full bg-zen-50 p-4 text-zen-600">
                  <v.icon className="h-7 w-7" />
                </span>
                <h3 className="mt-4 text-lg font-semibold text-brand-900">
                  {v.title}
                </h3>
                <p className="mt-3 text-sm leading-6 text-brand-800/70">
                  {v.body}
                </p>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      <CtaBanner />
    </>
  );
}
