import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import CtaBanner from "@/components/home/CtaBanner";
import { BrainIcon, CheckIcon, LockIcon, ShieldIcon } from "@/components/icons";

export const metadata: Metadata = {
  title: "Enterprise Data Security",
  description:
    "How CereBroZen survives a security review: air-gapped deployment, a regulated workplace mode with no emotion inference and no employee scoring, and routing an auditor can read.",
};

const pillars = [
  {
    icon: LockIcon,
    title: "Confidentiality",
    body: "Coaching conversations stay private to the individual, with identities protected. Leaders see aggregated trends — never transcripts.",
  },
  {
    icon: BrainIcon,
    title: "AI Safety",
    body: "A deterministic safety screen runs before any model sees a message. The crisis takeover is code: zero tokens, and it cannot be persuaded.",
  },
  {
    icon: ShieldIcon,
    title: "Data Security",
    body: "Encryption in transit and at rest, tenant isolation, and role-based access control — or no egress at all, in the air-gapped deployment.",
  },
  {
    icon: CheckIcon,
    title: "Compliance",
    body: "Data handling designed to align with global privacy standards, including a regulated mode built with the EU AI Act's workplace rules in mind.",
  },
];

export default function SecurityPage() {
  return (
    <>
      <PageHero
        eyebrow="Security & data protection"
        title="An AI coach your security review can survive"
        lead="Your people will tell an AI coach things they would never tell their manager. That is the product — and it is the risk. So every answer on this page comes with the way to check it, because a security review is not a vibe."
      />

      {/* four pillars */}
      <section className="mx-auto max-w-6xl px-6 py-20 text-center">
        <div className="grid gap-12 sm:grid-cols-2 lg:grid-cols-4">
          {pillars.map((p, i) => (
            <Reveal key={p.title} delay={i * 100}>
              <p.icon className="mx-auto h-12 w-12 text-zen-500" strokeWidth={1.5} />
              <h3 className="mt-5 font-[family-name:var(--font-heading)] text-xl font-bold text-brand-900">
                {p.title}
              </h3>
              <p className="mx-auto mt-3 max-w-60 text-sm leading-6 text-brand-800">
                {p.body}
              </p>
            </Reveal>
          ))}
        </div>
      </section>

      {/* data-flow: cloud vs air-gapped */}
      <section className="bg-mist-50">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <Reveal className="mx-auto max-w-3xl text-center">
            <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
              Where the data goes
            </p>
            <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
              Nothing has to leave your network
            </h2>
            <p className="mt-4 leading-7 text-brand-800">
              The same codebase runs against a frontier cloud model or entirely
              inside your perimeter — Postgres, local vector search, and a
              local model, with no internet at all.
            </p>
          </Reveal>

          <div className="mt-12 grid gap-6 lg:grid-cols-2">
            <Reveal className="rounded-2xl border border-mist-200 bg-white p-7">
              <h3 className="font-[family-name:var(--font-heading)] font-bold text-brand-900">
                Cloud model{" "}
                <span className="ml-1 rounded bg-zen-50 px-2 py-0.5 font-mono text-[10px] font-bold uppercase tracking-widest text-zen-600">
                  data leaves
                </span>
              </h3>
              <svg viewBox="0 0 260 120" className="mt-4 w-full" role="img" aria-label="With a cloud model, conversations leave your network for an external model provider.">
                <defs>
                  <marker id="arwR" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto">
                    <path d="M0,0 L7,3.5 L0,7 z" fill="#ef5b5b" />
                  </marker>
                </defs>
                <rect x="6" y="26" width="120" height="68" rx="6" fill="#f8efe4" stroke="#e7d6bf" />
                <text x="18" y="44" fontSize="9" fill="#4a4a4a" fontFamily="monospace">Your network</text>
                <rect x="18" y="54" width="44" height="26" rx="4" fill="#ffffff" stroke="#e7d6bf" />
                <text x="28" y="70" fontSize="9" fill="#101010" fontFamily="monospace">app</text>
                <rect x="70" y="54" width="44" height="26" rx="4" fill="#ffffff" stroke="#e7d6bf" />
                <text x="78" y="70" fontSize="9" fill="#101010" fontFamily="monospace">data</text>
                <path d="M128,64 L196,64" fill="none" stroke="#ef5b5b" strokeWidth="1.6" markerEnd="url(#arwR)" />
                <text x="132" y="56" fontSize="8.5" fill="#ef5b5b" fontFamily="monospace">transcripts</text>
                <rect x="200" y="44" width="54" height="40" rx="6" fill="#ffffff" stroke="#ef5b5b" />
                <text x="208" y="61" fontSize="8.5" fill="#ef5b5b" fontFamily="monospace">model</text>
                <text x="208" y="73" fontSize="8.5" fill="#ef5b5b" fontFamily="monospace">provider</text>
              </svg>
              <p className="mt-4 text-sm leading-6 text-brand-800">
                Every message goes to an external provider. That is a question
                your review has to answer, and an exception someone has to
                sign.
              </p>
            </Reveal>

            <Reveal delay={120} className="rounded-2xl border border-mist-200 bg-white p-7">
              <h3 className="font-[family-name:var(--font-heading)] font-bold text-brand-900">
                Air-gapped{" "}
                <span className="ml-1 rounded bg-green-50 px-2 py-0.5 font-mono text-[10px] font-bold uppercase tracking-widest text-green-700">
                  nothing leaves
                </span>
              </h3>
              <svg viewBox="0 0 260 120" className="mt-4 w-full" role="img" aria-label="Air-gapped: the app, the data and the model all sit inside your network. Nothing leaves.">
                <rect x="6" y="20" width="248" height="82" rx="8" fill="none" stroke="#15803d" strokeWidth="1.4" strokeDasharray="5 4" />
                <text x="16" y="36" fontSize="9" fill="#15803d" fontFamily="monospace">Your network — no egress</text>
                <rect x="20" y="48" width="60" height="34" rx="4" fill="#f8efe4" stroke="#e7d6bf" />
                <text x="38" y="69" fontSize="9" fill="#101010" fontFamily="monospace">app</text>
                <rect x="98" y="48" width="60" height="34" rx="4" fill="#f8efe4" stroke="#e7d6bf" />
                <text x="114" y="69" fontSize="9" fill="#101010" fontFamily="monospace">data</text>
                <rect x="176" y="48" width="60" height="34" rx="4" fill="#f8efe4" stroke="#15803d" />
                <text x="190" y="69" fontSize="9" fill="#15803d" fontFamily="monospace">model</text>
                <path d="M80,65 L96,65" stroke="#15803d" strokeWidth="1.6" />
                <path d="M158,65 L174,65" stroke="#15803d" strokeWidth="1.6" />
              </svg>
              <p className="mt-4 text-sm leading-6 text-brand-800">
                The model runs inside the perimeter. There is no provider, no
                egress, and no exception to sign — the question stops
                existing.
              </p>
            </Reveal>
          </div>
        </div>
      </section>

      {/* the three questions */}
      <section className="mx-auto max-w-4xl px-6 py-20">
        <Reveal className="text-center">
          <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
            The questions your reviewers will ask
          </p>
          <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
            Answered, with the way to check each one
          </h2>
        </Reveal>

        <div className="mt-12 space-y-6">
          <Reveal className="rounded-2xl border border-mist-200 bg-white p-8 shadow-sm">
            <h3 className="font-[family-name:var(--font-heading)] text-xl font-bold text-brand-900">
              &ldquo;Does it profile our employees?&rdquo;
            </h3>
            <p className="mt-3 leading-7 text-brand-800">
              It does not have to. One setting disables emotion inference and
              any durable rating of a person — and it is a{" "}
              <strong>
                property of the deployment, not a checkbox in an admin panel
              </strong>
              . Emotion records are refused at the database, the last gate
              before the disk. Scoring variables are refused at load, so they
              are never registered at all: nothing can capture them, and no
              later content edit can quietly bring them back.
            </p>
            <pre className="mt-4 overflow-x-auto rounded-lg bg-brand-950 px-4 py-3 font-mono text-sm text-zen-400">
              CEREBROZEN_REGULATED_WORKPLACE=true{"   "}
              <span className="text-white/40"># no emotion inference. no person-score.</span>
            </pre>
            <p className="mt-4 rounded-r border-l-2 border-green-600 bg-green-50 px-4 py-2.5 text-sm text-green-900">
              <b className="font-mono text-xs uppercase tracking-widest">check it</b>{" "}
              — 16 tests prove it. We will run them in front of your DPO.
            </p>
          </Reveal>

          <Reveal delay={80} className="rounded-2xl border border-mist-200 bg-white p-8 shadow-sm">
            <h3 className="font-[family-name:var(--font-heading)] text-xl font-bold text-brand-900">
              &ldquo;What will it do — not what did it do?&rdquo;
            </h3>
            <p className="mt-3 leading-7 text-brand-800">
              Routing is a state machine, not a mood. Which method a person
              gets, what must be true before an agent may hand off, when a
              session is allowed to close — all of it is code. An auditor can{" "}
              <em>read</em> it. Sessions are reproducible.
            </p>
            <p className="mt-4 rounded-r border-l-2 border-green-600 bg-green-50 px-4 py-2.5 text-sm text-green-900">
              <b className="font-mono text-xs uppercase tracking-widest">check it</b>{" "}
              — we walk the live graph with your engineer and run a real
              session through it, node by node.
            </p>
          </Reveal>

          <Reveal delay={160} className="rounded-2xl border border-mist-200 bg-white p-8 shadow-sm">
            <h3 className="font-[family-name:var(--font-heading)] text-xl font-bold text-brand-900">
              &ldquo;What do you keep, and can we get rid of it?&rdquo;
            </h3>
            <p className="mt-3 leading-7 text-brand-800">
              Coaching transcripts, the commitments a person made, and the
              patterns noticed across sessions. Nothing else. In regulated
              mode: no emotion record and no score.{" "}
              <strong>
                Deletion is a function of the product, not a support ticket.
              </strong>
            </p>
          </Reveal>
        </div>
      </section>

      {/* emotion inference & worker scoring */}
      <section className="bg-brand-950 text-white">
        <div className="mx-auto max-w-3xl px-6 py-20">
          <Reveal>
            <p className="text-sm font-semibold uppercase tracking-widest text-zen-400">
              Emotion inference · worker scoring
            </p>
            <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium sm:text-4xl">
              The two things a coaching AI should be most careful about
            </h2>
            <div className="mt-6 space-y-5 leading-8 text-white/80">
              <p>
                A coaching product hears a person at their most honest. It is
                therefore in a position to do two things that, in an{" "}
                <em>employment</em> context, are legally and ethically loaded:{" "}
                <strong className="text-white">infer their emotional state</strong>, and{" "}
                <strong className="text-white">keep a durable score about them</strong> —
                computed from a conversation they were told was confidential.
              </p>
              <p>
                Under the EU AI Act, inferring the emotions of a natural person
                in the workplace is a prohibited practice, and AI used in
                employment and worker management is high-risk. Many products in
                this category do both by default, because the analytics they
                sell back to HR depend on it.
              </p>
              <p>
                <strong className="text-white">
                  Regulated mode removes the score, not the coaching.
                </strong>{" "}
                The coach still remembers the goal, the commitments, and what a
                person is working on. It simply does not keep a rating of them.
              </p>
              <p className="text-sm text-white/50">
                This describes how the software behaves; it is not legal
                advice. Your counsel decides what your obligations are. Our job
                is to make the answer <em>enforceable</em> once they do.
              </p>
            </div>
          </Reveal>
        </div>
      </section>

      <CtaBanner />
    </>
  );
}
