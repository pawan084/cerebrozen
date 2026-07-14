import Reveal from "@/components/Reveal";
import { ChartIcon, SparkIcon, UsersIcon } from "@/components/icons";

const props = [
  {
    icon: SparkIcon,
    title: "Every Employee, Supported 24/7",
    body: "From individual contributors to senior leaders, coaching is always on — real-time help in the flow of work, whenever the moment demands it.",
  },
  {
    icon: UsersIcon,
    title: "Performance That Sustains",
    body: "Development that's continuous and built to last — not a workshop that fades by Friday. Habits compound quarter after quarter, at enterprise scale.",
  },
  {
    icon: ChartIcon,
    title: "Impact You Can Measure",
    body: "Every commitment and follow-through is quantified, so leadership sees behavior change as hard data the whole organization can trust.",
  },
];

export default function ValueProps() {
  return (
    <section className="bg-mist-50 pb-24">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-7 md:grid-cols-3">
          {props.map((item, i) => (
            <Reveal
              key={item.title}
              delay={i * 120}
              className="rounded-[2rem] bg-zen-500 p-8 text-white shadow-lg shadow-zen-500/20 transition hover:-translate-y-1"
            >
              <h3 className="font-[family-name:var(--font-heading)] text-xl font-bold leading-snug">
                {item.title}
              </h3>
              <p className="mt-4 text-sm leading-6 text-white/90">{item.body}</p>
              <item.icon className="mt-6 h-10 w-10 text-white/80" />
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
