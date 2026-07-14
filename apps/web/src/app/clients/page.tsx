import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import CtaBanner from "@/components/home/CtaBanner";

export const metadata: Metadata = {
  title: "Client Stories",
  description:
    "How organizations use CereBroZen to change behavior at scale — feedback velocity, decision speed, and retention.",
};

const stories = [
  {
    org: "Meridian Financial",
    industry: "Financial services · 9,000 employees",
    headline: "From feedback once a year to feedback every week",
    body: "Managers rehearsed difficult conversations with their coach before having them for real. Within two quarters, feedback cycles ran 3× faster and regretted attrition on coached teams dropped by a quarter.",
    stats: [
      ["3.1×", "faster feedback cycles"],
      ["−26%", "regretted attrition"],
      ["92%", "weekly manager engagement"],
    ],
  },
  {
    org: "Helix Health",
    industry: "Healthcare · 14,000 employees",
    headline: "Cutting decision latency across a hospital network",
    body: "Operational decisions that used to wait for the weekly leadership sync started resolving the same day, with the coach walking owners through a lightweight decision framework at the moment of hesitation.",
    stats: [
      ["58%", "decisions resolved same-day"],
      ["−41%", "escalations to leadership"],
      ["+22%", "staff well-being index"],
    ],
  },
  {
    org: "Nordlicht Group",
    industry: "Industrial technology · 11,000 employees",
    headline: "Scaling leadership habits past the workshop wall",
    body: "A decade of leadership workshops had produced binders, not behavior. Guided journeys turned delegation and ownership into weekly practice — and made bench strength something the board could finally see in numbers.",
    stats: [
      ["78%", "journey completion rate"],
      ["+34%", "internal promotion readiness"],
      ["6 wks", "from kickoff to full rollout"],
    ],
  },
];

export default function ClientsPage() {
  return (
    <>
      <PageHero
        eyebrow="Client stories"
        title="Behavior change, measured in the wild"
        lead="Representative engagement stories from enterprise deployments. Names and figures shown here are illustrative examples of typical outcomes."
      />

      <section className="mx-auto max-w-5xl space-y-10 px-6 py-20">
        {stories.map((s, i) => (
          <Reveal
            key={s.org}
            delay={i * 80}
            className="overflow-hidden rounded-3xl border border-mist-200 bg-white shadow-sm"
          >
            <div className="grid lg:grid-cols-[1.5fr_1fr]">
              <div className="p-8 md:p-10">
                <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
                  {s.org}
                </p>
                <p className="mt-1 text-xs text-brand-800/50">{s.industry}</p>
                <h2 className="mt-4 text-2xl font-bold text-brand-900">
                  {s.headline}
                </h2>
                <p className="mt-4 leading-7 text-brand-800/70">{s.body}</p>
              </div>
              <div className="flex flex-col justify-center gap-6 bg-brand-900 p-8 text-white md:p-10">
                {s.stats.map(([value, label]) => (
                  <div key={label}>
                    <p className="text-3xl font-bold text-zen-400">{value}</p>
                    <p className="mt-1 text-sm text-white/65">{label}</p>
                  </div>
                ))}
              </div>
            </div>
          </Reveal>
        ))}
      </section>

      <CtaBanner />
    </>
  );
}
