import Link from "next/link";
import Reveal from "@/components/Reveal";
import { ChartIcon, ChatIcon, CompassIcon, LeafIcon } from "@/components/icons";

const features = [
  {
    icon: ChatIcon,
    title: "Coaching In The Moment",
    kicker: "Your in-the-moment AI coach",
    body: "Deep, inquiry-led coaching that surfaces the hidden patterns in how you think and act — personalized to your style, right when the challenge arises.",
    art: "from-[#aebcdd] to-[#8fa3d0]",
  },
  {
    icon: CompassIcon,
    title: "Coaching Horizons",
    kicker: "Journeys for longer-term growth",
    body: "Structured multi-week paths that help managers navigate stakeholders, elevate their leadership brand, and build promotion-ready capability.",
    art: "from-[#9fd8db] to-[#6fc3c9]",
  },
  {
    icon: LeafIcon,
    title: "Coaching for Well-Being",
    kicker: "Foundations for sustainable pace",
    body: "Transformative journeys that protect energy and focus — boundaries, recovery, and resilience that lay the groundwork for performance.",
    art: "from-[#f6c1ac] to-[#ef9e86]",
  },
  {
    icon: ChartIcon,
    title: "Behavior Analytics",
    kicker: "The clarity layer for HR",
    body: "Converts coaching activity into real-time, anonymized signals on productivity, engagement, and well-being — so HR sees readiness and risk early.",
    art: "from-[#f2ddb8] to-[#e7c68e]",
  },
];

export default function Features() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <Reveal className="mx-auto max-w-3xl text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
            From AI Coaching to a Behavioral Clarity System™
          </h2>
          <p className="mt-5 leading-7 text-brand-800">
            CereBroZen combines AI coaching with behavioral intelligence to
            change what managers do tomorrow — how they run 1:1s, handle
            conflict, delegate, give feedback, and lead through change.
          </p>
        </Reveal>

        <div className="mt-16 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f, i) => (
            <Reveal key={f.title} delay={(i % 4) * 90} className="text-center">
              <div
                className={`flex h-40 items-center justify-center rounded-2xl bg-gradient-to-br ${f.art}`}
              >
                <f.icon className="h-16 w-16 text-white" strokeWidth={1.3} />
              </div>
              <h3 className="mt-5 font-[family-name:var(--font-heading)] text-lg font-bold leading-snug text-brand-900">
                {f.title}
              </h3>
              <p className="mt-2 text-xs font-semibold text-brand-800">
                {f.kicker}
              </p>
              <p className="mt-3 text-sm leading-6 text-brand-800">{f.body}</p>
            </Reveal>
          ))}
        </div>

        <Reveal className="mt-14 text-center">
          <Link
            href="/platform"
            className="inline-flex items-center rounded-full bg-zen-500 px-7 py-3.5 text-[13px] font-semibold uppercase tracking-wider text-white transition hover:bg-zen-600"
          >
            See How It Works
          </Link>
        </Reveal>
      </div>
    </section>
  );
}
