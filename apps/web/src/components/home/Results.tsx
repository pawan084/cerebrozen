import Reveal from "@/components/Reveal";

const rowOne = [
  {
    value: "2M+",
    label: "Coaching Conversations",
    body: "91% of users commit to a concrete action after a conversation.",
    circle: "bg-zen-400/80",
  },
  {
    value: ">60%",
    label: "Action Completion",
    body: "Regular check-ins turn stated intentions into finished actions.",
    circle: "bg-mist-100",
  },
  {
    value: "+53%",
    label: "Well-Being",
    body: "Sustained improvement in day-to-day energy and emotional balance.",
    circle: "bg-zen-400/80",
  },
];

const rowTwo = [
  {
    value: "+55%",
    label: "Productivity",
    body: "The behaviors that drive output show up measurably more often.",
    circle: "bg-zen-400/80",
  },
  {
    value: "+64%",
    label: "Engagement",
    body: "Engagement climbs across every dimension we track.",
    circle: "bg-mist-100",
  },
];

function Stat({
  value,
  label,
  body,
  circle,
}: {
  value: string;
  label: string;
  body: string;
  circle: string;
}) {
  return (
    <div className="max-w-xs">
      <div className="relative">
        <span
          aria-hidden
          className={`absolute -left-5 -top-4 h-14 w-14 rounded-full ${circle}`}
        />
        <p className="relative font-[family-name:var(--font-heading)] text-4xl font-bold text-brand-900">
          {value}{" "}
          <span className="text-base font-semibold">{label}</span>
        </p>
      </div>
      <p className="mt-3 text-sm leading-6 text-brand-800">{body}</p>
    </div>
  );
}

export default function Results() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <Reveal className="text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium text-brand-900 sm:text-5xl">
            Client Results That Matter
          </h2>
          <p className="mt-3 font-[family-name:var(--font-heading)] text-lg font-bold text-brand-900">
            Trusted. Measured. Delivered.
          </p>
        </Reveal>

        <div className="mt-16 flex flex-wrap justify-center gap-x-20 gap-y-12">
          {rowOne.map((s, i) => (
            <Reveal key={s.label} delay={i * 100}>
              <Stat {...s} />
            </Reveal>
          ))}
        </div>
        <div className="mt-14 flex flex-wrap justify-center gap-x-20 gap-y-12">
          {rowTwo.map((s, i) => (
            <Reveal key={s.label} delay={i * 100}>
              <Stat {...s} />
            </Reveal>
          ))}
        </div>

        <Reveal className="mt-12 text-center text-xs text-brand-500">
          Aggregated across enterprise deployments · illustrative pilot data
        </Reveal>
      </div>
    </section>
  );
}
