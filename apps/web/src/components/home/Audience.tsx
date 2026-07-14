import Reveal from "@/components/Reveal";
import { CompassIcon, TargetIcon, UsersIcon } from "@/components/icons";

const audiences = [
  {
    icon: UsersIcon,
    title: "Employees Across the Workforce",
    body: "Individual contributors and managers who need real-time support to make better decisions, navigate friction, and perform in the flow of work.",
  },
  {
    icon: CompassIcon,
    title: "Emerging and High-Potential Talent",
    body: "Rising leaders building capability and confidence, who need practical, in-the-moment coaching — not another theory-heavy course.",
  },
  {
    icon: TargetIcon,
    title: "Enterprise Leaders and HR Teams",
    body: "CHROs, L&D, and business leaders who need scalable coaching, behavioral insight, and measurable impact across the organization.",
  },
];

export default function Audience() {
  return (
    <section className="bg-zen-500">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <Reveal className="text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-3xl font-medium text-white sm:text-4xl">
            Who CereBroZen Is Built For
          </h2>
        </Reveal>

        <div className="mt-16 grid gap-12 md:grid-cols-3">
          {audiences.map((a, i) => (
            <Reveal key={a.title} delay={i * 120} className="text-center">
              <a.icon className="mx-auto h-14 w-14 text-white" strokeWidth={1.4} />
              <h3 className="mt-6 font-[family-name:var(--font-heading)] text-lg font-bold text-white">
                {a.title}
              </h3>
              <p className="mx-auto mt-4 max-w-xs leading-7 text-white/95">
                {a.body}
              </p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
