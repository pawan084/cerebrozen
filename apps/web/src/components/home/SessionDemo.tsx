import Reveal from "@/components/Reveal";

const exchange = [
  {
    who: "Them",
    coach: false,
    text: "I have to tell a senior engineer that his code reviews are crushing the juniors. I've been putting it off for three weeks.",
  },
  {
    who: "Coach",
    coach: true,
    text: "Three weeks is data. What are you protecting by not having this conversation — him, them, or you?",
  },
  {
    who: "Them",
    coach: false,
    text: "Me, probably. Last time I raised something with him, I backed down.",
  },
  {
    who: "Coach",
    coach: true,
    text: "Then let's not rehearse the version where he agrees. I'll play him, and I'll push back the way he did last time. Open however you plan to open.",
  },
];

const state = [
  { label: "stage", value: "practice", ok: false },
  { label: "turn cost", value: "$0.0024", ok: false },
  { label: "safety", value: "clear", ok: true },
  { label: "commitment", value: "pending", ok: false },
];

export default function SessionDemo() {
  return (
    <section className="bg-mist-50">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <div className="grid items-center gap-14 lg:grid-cols-[1fr_1.2fr]">
          <Reveal>
            <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
              Not Advice. Practice — Then Follow-Through.
            </h2>
            <p className="mt-6 leading-8 text-brand-800">
              A real exchange shape, with the state the engine is actually
              tracking on every turn. The session cannot close until a
              commitment is saved — and in seven days the coach will ask how it
              went.
            </p>
            <p className="mt-4 text-sm text-brand-600">
              Rehearsal beats reassurance: when a situation needs practice
              rather than advice, the coach role-plays the counterpart and
              pushes back the way they actually would.
            </p>
          </Reveal>

          <Reveal delay={150}>
            <div className="overflow-hidden rounded-2xl border border-mist-200 bg-white shadow-xl shadow-brand-900/10">
              <div className="flex items-center gap-2 border-b border-mist-100 bg-mist-50 px-4 py-3">
                <span className="h-2.5 w-2.5 rounded-full bg-zen-400" />
                <span className="h-2.5 w-2.5 rounded-full bg-mist-200" />
                <span className="h-2.5 w-2.5 rounded-full bg-mist-200" />
                <span className="ml-3 font-mono text-xs text-brand-600">
                  session · turn 4
                </span>
              </div>

              <div className="space-y-4 px-5 py-6">
                {exchange.map((m, i) => (
                  <div key={i} className="flex gap-4">
                    <span
                      className={`min-w-14 pt-0.5 font-mono text-[10px] font-bold uppercase tracking-widest ${
                        m.coach ? "text-zen-600" : "text-brand-500"
                      }`}
                    >
                      {m.who}
                    </span>
                    <p
                      className={`text-sm leading-relaxed ${
                        m.coach ? "text-brand-900" : "text-brand-600"
                      }`}
                    >
                      {m.text}
                    </p>
                  </div>
                ))}

                <div className="flex flex-wrap gap-2 border-t border-mist-100 pt-4">
                  {state.map((s) => (
                    <span
                      key={s.label}
                      className="rounded bg-mist-50 px-2.5 py-1 font-mono text-[11px] text-brand-600"
                    >
                      {s.label}{" "}
                      <b className={s.ok ? "text-green-700" : "text-zen-600"}>
                        {s.value}
                      </b>
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
