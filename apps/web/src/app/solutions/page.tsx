import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import CtaBanner from "@/components/home/CtaBanner";
import {
  CheckIcon,
  CompassIcon,
  LeafIcon,
  UsersIcon,
} from "@/components/icons";

export const metadata: Metadata = {
  title: "Solutions",
  description:
    "CereBroZen solutions for workforce performance, leadership development, and organizational well-being.",
};

const solutions = [
  {
    icon: UsersIcon,
    name: "Workforce performance",
    tagline: "Unblock the everyday moments that decide results",
    body: "Most performance is lost in small, invisible moments: the unclear ask nobody questioned, the decision that waited a week, the feedback that never happened. CereBroZen coaches every employee through those moments as they occur.",
    outcomes: [
      "Faster decisions with less escalation",
      "Feedback delivered early, while it's still useful",
      "Ownership habits that survive ambiguity",
    ],
  },
  {
    icon: CompassIcon,
    name: "Leadership development",
    tagline: "Turn new managers into confident leaders — at scale",
    body: "The jump from IC to manager is where most careers wobble and most teams suffer. Instead of a two-day workshop and good luck, new leaders get a coach in their corner for every hard first: first tough conversation, first underperformer, first re-org.",
    outcomes: [
      "Structured journeys for first-time and rising managers",
      "Rehearsal before high-stakes conversations",
      "Leadership bench strength you can measure",
    ],
  },
  {
    icon: LeafIcon,
    name: "Well-being & retention",
    tagline: "Catch burnout while it's still a signal, not a resignation",
    body: "Well-being programs fail when they're generic. CereBroZen notices individual early-warning signs and responds with concrete, private support — protecting focus, energy, and ultimately your retention numbers.",
    outcomes: [
      "Early, private signals instead of after-the-fact surveys",
      "Practical recovery and boundary-setting routines",
      "Aggregate well-being trends for leadership, minus surveillance",
    ],
  },
];

export default function SolutionsPage() {
  return (
    <>
      <PageHero
        eyebrow="Solutions"
        title="One coach, three problems it quietly solves"
        lead="Whether you start with performance, leadership, or well-being, the same coaching engine compounds across all three."
      />

      <section className="mx-auto max-w-6xl space-y-10 px-6 py-20">
        {solutions.map((s, i) => (
          <Reveal
            key={s.name}
            className={`grid items-center gap-10 rounded-3xl border border-mist-200 p-8 md:p-12 lg:grid-cols-2 ${
              i % 2 === 1 ? "bg-mist-50" : "bg-white"
            }`}
          >
            <div className={i % 2 === 1 ? "lg:order-2" : ""}>
              <span className="inline-flex rounded-2xl bg-brand-50 p-3 text-brand-700">
                <s.icon className="h-7 w-7" />
              </span>
              <h2 className="mt-5 text-2xl font-bold text-brand-900 sm:text-3xl">
                {s.name}
              </h2>
              <p className="mt-2 font-medium text-zen-700">{s.tagline}</p>
              <p className="mt-4 leading-7 text-brand-800/70">{s.body}</p>
            </div>
            <div className={i % 2 === 1 ? "lg:order-1" : ""}>
              <div className="rounded-3xl bg-brand-900 p-8 text-white">
                <p className="text-xs font-semibold uppercase tracking-widest text-zen-400">
                  What you get
                </p>
                <ul className="mt-5 space-y-4">
                  {s.outcomes.map((o) => (
                    <li key={o} className="flex gap-3 text-sm text-white/85">
                      <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-zen-500/20 text-zen-400">
                        <CheckIcon className="h-3 w-3" strokeWidth={2.6} />
                      </span>
                      {o}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </Reveal>
        ))}
      </section>

      <CtaBanner />
    </>
  );
}
