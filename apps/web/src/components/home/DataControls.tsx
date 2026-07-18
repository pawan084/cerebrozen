import Link from "next/link";
import Reveal from "@/components/Reveal";
import { BrainIcon, LockIcon, ShieldIcon, UsersIcon } from "@/components/icons";

// Every card maps to a shipped mechanism (see docs/SECURITY.md), so this markets
// nothing the product doesn't do — CLAUDE.md rule 6.
const controls = [
  {
    icon: BrainIcon,
    title: "See what the coach learned",
    body: "Every statement the coach has formed about a person — shown with the counts behind it, and deletable. A claim you can't audit is a horoscope.",
  },
  {
    icon: ShieldIcon,
    title: "Consent that applies instantly",
    body: "Six data categories, each a toggle. Withdraw one and it takes effect on the very next message — the session re-keys, it doesn't wait for a token to expire.",
  },
  {
    icon: LockIcon,
    title: "Export or erase in one click",
    body: "Take your whole record across both services, or delete it — a wipe that re-scans and refuses to report success on a partial erasure.",
  },
  {
    icon: UsersIcon,
    title: "No metric for a crowd of one",
    body: "HR analytics never render a number for a cohort under eight people. The floor is enforced in the aggregation layer, not the interface — nothing to leak.",
  },
];

export default function DataControls() {
  return (
    <section className="bg-mist-50">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-bold uppercase tracking-wider text-zen-600">
            Privacy you can operate
          </p>
          <h2 className="mt-3 font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
            Your data, your controls
          </h2>
          <p className="mt-5 leading-7 text-brand-800">
            Not a policy page — buttons in the product. Everything the coach knows
            is visible, revocable, and yours to take or delete.
          </p>
        </Reveal>

        <div className="mt-16 grid gap-7 sm:grid-cols-2 lg:grid-cols-4">
          {controls.map((c, i) => (
            <Reveal
              key={c.title}
              delay={(i % 4) * 90}
              className="group rounded-[1.75rem] border border-mist-200 bg-white p-7 shadow-sm transition duration-500 hover:-translate-y-1.5 hover:shadow-xl"
            >
              <c.icon className="h-10 w-10 text-zen-500" strokeWidth={1.5} />
              <h3 className="mt-5 font-[family-name:var(--font-heading)] text-lg font-bold leading-snug text-brand-900">
                {c.title}
              </h3>
              <p className="mt-3 text-sm leading-6 text-brand-800">{c.body}</p>
            </Reveal>
          ))}
        </div>

        <Reveal className="mt-12 text-center">
          <Link
            href="/security"
            className="inline-flex items-center rounded-full border-2 border-zen-500 px-7 py-3.5 text-[13px] font-semibold uppercase tracking-wider text-zen-600 transition hover:bg-zen-500 hover:text-white"
          >
            How we keep this honest
          </Link>
        </Reveal>
      </div>
    </section>
  );
}
