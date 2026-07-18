import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";
import CtaBanner from "@/components/home/CtaBanner";
import {
  ChartIcon,
  ChatIcon,
  CheckIcon,
  CompassIcon,
  LeafIcon,
} from "@/components/icons";

export const metadata: Metadata = {
  title: "Platform",
  description:
    "How CereBroZen works: fifteen agents in one governed arc, deterministic routing, coach-owned content, and behavioral analytics — engineered on coaching science.",
};

const arc = [
  {
    step: "01",
    title: "Safety screen",
    time: "~1ms",
    body: "Runs before any model sees the message. The crisis takeover is deterministic: zero tokens, the model is never consulted and cannot be persuaded.",
  },
  {
    step: "02",
    title: "Context",
    time: "~7ms",
    body: "Prior sessions, open commitments, patterns already noticed. A returning person never repeats their intake.",
  },
  {
    step: "03",
    title: "Framing",
    time: "~3s",
    body: "What the session is for, and which method fits it — a structured decision the graph routes on. If the model omits it, the handoff is refused and the agent is re-prompted.",
  },
  {
    step: "04",
    title: "Method & practice",
    time: "varies",
    body: "The chosen method, held to its own arc — including rehearsal against a profiled counterpart when the situation needs practice rather than advice.",
  },
  {
    step: "05",
    title: "Commit",
    time: "~1ms",
    body: "The session cannot close without a saved action. That gate is code, so no amount of model drift gets past it.",
  },
];

const notWrapper = [
  {
    title: "Your coaches own the content",
    body: "Coaching behaviour lives in a versioned workbook, not in source code. A qualified coach edits it directly. Every change is content-hashed, validated before it can ship, and reversible — you improve the coaching without a software release.",
    proof: null,
  },
  {
    title: "Silent drift is caught and repaired",
    body: "When an agent stops emitting a decision the system routes on, most products fall back to a default and nobody finds out. Here it is detected and repaired inside the same turn.",
    proof:
      "We measured this failing roughly one handoff in six before we fixed it. Nothing errored.",
  },
  {
    title: "Quality is a number",
    body: "An evaluation harness scores the coach against golden cases on every change, so a content edit that degrades the coaching is caught before anyone sees it.",
    proof: null,
  },
  {
    title: "It runs anywhere, including nowhere",
    body: "Postgres, local vector search, and a local model. The same codebase runs against a frontier cloud model or entirely inside your perimeter, with no internet at all.",
    proof: null,
  },
];

const modules = [
  {
    icon: ChatIcon,
    title: "In-the-moment coaching",
    body: "The coach connects to the rhythm of real work — calendars, goals, review cycles — and offers help right before the moments that decide outcomes. A two-minute prep before a difficult 1:1 changes the conversation; CereBroZen makes that prep automatic.",
    points: [
      "Context-aware prompts before key meetings and deadlines",
      "Conversation rehearsal with role-played responses",
      "One concrete commitment captured at the end of every session",
    ],
  },
  {
    icon: CompassIcon,
    title: "Guided growth journeys",
    body: "Skills that used to take a decade of trial and error — delegation, feedback, influence, ownership — become structured multi-week journeys, practiced against each person's actual workload rather than hypothetical case studies.",
    points: [
      "Role-aware curricula for ICs, new managers, and senior leaders",
      "Weekly practice loops anchored to real tasks",
      "Progress visible to the individual, trends visible to the org",
    ],
  },
  {
    icon: LeafIcon,
    title: "Well-being & resilience",
    body: "Burnout rarely announces itself. The coach notices early signals — shrinking focus blocks, skipped breaks, rising negativity — and responds with practical resets instead of platitudes.",
    points: [
      "Early-warning signals surfaced privately to the individual",
      "Boundary-setting and recovery routines that fit real schedules",
      "A library of rest and recovery practices to enrol in",
      "A deterministic crisis response that surfaces regional helplines to the person the moment it's needed",
    ],
  },
  {
    icon: ChartIcon,
    title: "Behavioral analytics",
    body: "HR and leadership see behavior change as a metric: commitments made, actions completed, conversations unblocked — aggregated and anonymized, never surveilled.",
    points: [
      "Org-level dashboards of engagement and follow-through",
      "Cohort comparisons for programs and departments",
      "Privacy-preserving by architecture: no individual transcripts",
    ],
  },
];

export default function PlatformPage() {
  return (
    <>
      <PageHero
        eyebrow="Platform"
        title="Fifteen agents, one governed arc"
        lead="The model supplies the words. It does not get to decide the structure, and it cannot talk its way past a gate. That is what makes CereBroZen a coaching engine, not a chatbot."
      />

      {/* the governed arc */}
      <section className="mx-auto max-w-4xl px-6 py-20">
        <Reveal>
          <h2 className="font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
            Every session walks the same arc
          </h2>
        </Reveal>
        <div className="mt-10 divide-y divide-mist-200 border-y border-mist-200">
          {arc.map((s, i) => (
            <Reveal
              key={s.step}
              delay={i * 60}
              className="grid grid-cols-[3rem_1fr_auto] items-baseline gap-4 py-6"
            >
              <span className="font-mono text-sm font-bold text-zen-600">
                {s.step}
              </span>
              <div>
                <h3 className="font-[family-name:var(--font-heading)] font-bold text-brand-900">
                  {s.title}
                </h3>
                <p className="mt-1.5 text-sm leading-6 text-brand-800">
                  {s.body}
                </p>
              </div>
              <span className="font-mono text-xs text-brand-500">{s.time}</span>
            </Reveal>
          ))}
        </div>
      </section>

      {/* not an LLM wrapper */}
      <section className="bg-mist-50">
        <div className="mx-auto max-w-5xl px-6 py-20">
          <Reveal className="text-center">
            <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
              Why it is not an LLM wrapper
            </p>
            <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
              Four things a prompt alone cannot give you
            </h2>
          </Reveal>
          <div className="mt-12 grid gap-6 sm:grid-cols-2">
            {notWrapper.map((c, i) => (
              <Reveal
                key={c.title}
                delay={(i % 2) * 100}
                className="rounded-2xl border border-mist-200 bg-white p-8"
              >
                <h3 className="font-[family-name:var(--font-heading)] text-lg font-bold text-brand-900">
                  {c.title}
                </h3>
                <p className="mt-3 text-sm leading-7 text-brand-800">{c.body}</p>
                {c.proof && (
                  <p className="mt-4 rounded-r border-l-2 border-green-600 bg-green-50 px-4 py-2.5 text-sm text-green-900">
                    <b className="font-mono text-xs uppercase tracking-widest">
                      why it matters
                    </b>{" "}
                    — {c.proof}
                  </p>
                )}
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* product modules */}
      <section className="mx-auto max-w-5xl space-y-8 px-6 py-20">
        <Reveal>
          <h2 className="font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
            What your people get
          </h2>
        </Reveal>
        {modules.map((m, i) => (
          <Reveal
            key={m.title}
            delay={i % 2 === 0 ? 0 : 80}
            className="grid gap-8 rounded-[2rem] border border-mist-200 bg-white p-8 shadow-sm md:grid-cols-[auto_1fr] md:p-10"
          >
            <span className="inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-zen-50 text-zen-600">
              <m.icon className="h-7 w-7" />
            </span>
            <div>
              <h3 className="font-[family-name:var(--font-heading)] text-2xl font-semibold text-brand-900">
                {m.title}
              </h3>
              <p className="mt-3 leading-7 text-brand-800">{m.body}</p>
              <ul className="mt-5 space-y-2.5">
                {m.points.map((p) => (
                  <li key={p} className="flex gap-2.5 text-sm text-brand-800">
                    <CheckIcon className="mt-0.5 h-4 w-4 shrink-0 text-zen-600" />
                    {p}
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>
        ))}
      </section>

      <CtaBanner />
    </>
  );
}
