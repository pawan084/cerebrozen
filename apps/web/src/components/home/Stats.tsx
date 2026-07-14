import Counter from "@/components/Counter";
import Reveal from "@/components/Reveal";

const stats = [
  { end: 64, label: "Engagement" },
  { end: 74, label: "Decision Confidence" },
  { end: 53, label: "Well-Being" },
];

export default function Stats() {
  return (
    <section className="bg-mist-50">
      <div className="mx-auto max-w-7xl px-6 py-16">
        <Reveal className="text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-xl font-bold uppercase tracking-wide text-brand-900 sm:text-2xl">
            Trusted by teams that run on clarity
          </h2>
          <p className="mt-2 text-sm text-brand-800">
            Measured across 90 days
          </p>
        </Reveal>

        <div className="mt-12 grid gap-10 sm:grid-cols-3">
          {stats.map((stat, i) => (
            <Reveal
              key={stat.label}
              delay={i * 120}
              className="flex items-center justify-center gap-5"
            >
              <span className="flex h-24 w-24 shrink-0 items-center justify-center rounded-full bg-zen-400/80">
                <span className="font-[family-name:var(--font-heading)] text-2xl font-bold text-brand-900">
                  +<Counter end={stat.end} />%
                </span>
              </span>
              <span className="font-[family-name:var(--font-heading)] text-lg font-bold text-brand-900">
                {stat.label}
              </span>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
