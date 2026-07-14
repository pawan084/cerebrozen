import Link from "next/link";
import Reveal from "@/components/Reveal";
import { BrainIcon, CheckIcon, LockIcon, ShieldIcon } from "@/components/icons";

const pillars = [
  {
    icon: LockIcon,
    title: "Confidentiality",
    body: "Coaching conversations stay private to the individual, with identities protected.",
  },
  {
    icon: BrainIcon,
    title: "AI Safety",
    body: "Moderated boundaries, sensitive-topic escalation, and continuous quality evaluation.",
  },
  {
    icon: ShieldIcon,
    title: "Data Security",
    body: "Encryption in transit and at rest, tenant isolation, and role-based access control.",
  },
  {
    icon: CheckIcon,
    title: "Compliance",
    body: "Data handling designed to align with global privacy and protection standards.",
  },
];

const badges = ["SOC 2 aligned", "GDPR ready", "DPDP aware", "ISO 27001 aligned"];

export default function Security() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-6xl px-6 py-24 text-center">
        <Reveal>
          <h2 className="font-[family-name:var(--font-heading)] text-2xl font-bold text-brand-900 sm:text-3xl">
            CereBroZen: Secure, Compliant, and Built for Growth
          </h2>
        </Reveal>

        <div className="mt-16 grid gap-12 sm:grid-cols-2 lg:grid-cols-4">
          {pillars.map((p, i) => (
            <Reveal key={p.title} delay={i * 100}>
              <p.icon className="mx-auto h-12 w-12 text-zen-500" strokeWidth={1.5} />
              <h3 className="mt-5 font-[family-name:var(--font-heading)] text-xl font-bold text-brand-900">
                {p.title}
              </h3>
              <p className="mx-auto mt-3 max-w-56 text-sm leading-6 text-brand-800">
                {p.body}
              </p>
            </Reveal>
          ))}
        </div>

        <Reveal className="mx-auto mt-16 max-w-4xl">
          <p className="font-[family-name:var(--font-heading)] text-lg font-bold leading-relaxed text-brand-900">
            CereBroZen is engineered to enterprise-grade privacy, security, and
            compliance standards. Conversations are confidential, AI guardrails
            are built in, and data is encrypted and governed for global
            organizations.
          </p>
        </Reveal>

        <Reveal className="mt-10 flex flex-wrap justify-center gap-4">
          {badges.map((b) => (
            <span
              key={b}
              className="rounded-full border-2 border-brand-100 px-5 py-2 text-sm font-semibold text-brand-800"
            >
              {b}
            </span>
          ))}
        </Reveal>

        <Reveal className="mt-14">
          <Link
            href="/security"
            className="inline-flex items-center rounded-full bg-brand-900 px-8 py-4 text-[13px] font-semibold uppercase tracking-wider text-white transition hover:bg-brand-700"
          >
            Explore Enterprise Data Security
          </Link>
        </Reveal>
      </div>
    </section>
  );
}
