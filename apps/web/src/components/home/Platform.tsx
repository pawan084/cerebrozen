import Reveal from "@/components/Reveal";

const cards = [
  {
    title: "One Platform, End to End",
    body: "From the first check-in to org-wide analytics: intake, context mapping, nudges, journeys, and insight dashboards in a single system.",
  },
  {
    title: "Tuned to Your Culture",
    body: "The coach speaks in your organization's voice and reinforces the leadership behaviors your culture actually values — not a generic playbook.",
  },
  {
    title: "Grounded in Coaching Science",
    body: "Built on evidence-based coaching psychology, behavioral science, and strict guardrails. Focused on work behaviors — never a substitute for therapy.",
  },
  {
    title: "Adaptive to Every Person",
    body: "It learns each person's role, thinking style, and goals with every conversation, so guidance stays contextual instead of one-size-fits-all.",
  },
  {
    title: "Real-Time Insight for HR",
    body: "Engagement patterns, mindset shifts, and talent readiness surface to HR and business leaders as they happen — aggregated and anonymized.",
  },
];

export default function Platform() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <div className="grid gap-7 md:grid-cols-2 lg:grid-cols-3">
          <Reveal className="pr-4">
            <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
              Built on Science. Designed for Calm.
            </h2>
            <p className="mt-4 text-sm italic text-zen-600">
              Powered by the CereBroZen Coaching Engine™
            </p>
          </Reveal>

          {cards.map((card, i) => (
            <Reveal
              key={card.title}
              delay={(i % 3) * 100}
              className="rounded-[2rem] bg-mist-50 p-8"
            >
              <h3 className="font-[family-name:var(--font-heading)] text-lg font-bold text-brand-900">
                {card.title}
              </h3>
              <p className="mt-3 text-sm leading-6 text-brand-800">
                {card.body}
              </p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
