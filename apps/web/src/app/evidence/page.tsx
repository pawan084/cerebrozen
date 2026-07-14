import type { Metadata } from "next";
import Link from "next/link";
import PageHero from "@/components/PageHero";
import Reveal from "@/components/Reveal";

export const metadata: Metadata = {
  title: "Evidence",
  description:
    "Evidence, not endorsement: the tests, the coverage, the numbers we'd rather not publish, and exactly what CereBroZen does not claim yet.",
};

const stats = [
  { n: "1,416", l: "Tests. Offline, deterministic — run them yourself.", bad: false },
  { n: "99.2%", l: "Branch coverage. Both sides of every decision.", bad: false },
  { n: "0", l: "Cloud dependencies required. It runs air-gapped.", bad: false },
  { n: "~5¢", l: "Model cost per full coaching session.", bad: false },
  { n: "15", l: "Agents in one governed arc.", bad: false },
  { n: "1 / 22", l: "Crisis red team — implicit disclosures caught.", bad: true },
];

const notClaimed = [
  {
    title: "Crisis detection is a keyword screen, not a classifier.",
    body: "The takeover is deterministic and airtight — the model is never consulted and cannot be talked round. But the detection in front of it is a word list, and a word list cannot understand euphemism. Red-teamed against how people actually disclose — passive ideation, planning, method-seeking — it currently catches roughly one implicit disclosure in twenty-two. We publish that because you would rather hear it from us than discover it. The classifier is the next thing we build.",
  },
  {
    title: "Escalation to a human is not built.",
    body: "A crisis reply reaches the person and nobody else. If another vendor tells you their AI “alerts a designated contact”, ask them to demonstrate it end to end, in front of you.",
  },
  {
    title: "Air-gapped coaching quality is unmeasured.",
    body: "The offline stack runs — that is demonstrated. Whether an open-weight model coaches to the same standard as a frontier one has not been tested, and we will measure it with you before either of us commits.",
  },
  {
    title: "Your coaching content is the long pole.",
    body: "The platform is the instrument; the method is the music. Good content is weeks of work by qualified coaches, and no amount of engineering substitutes for it.",
  },
];

const verify = [
  {
    title: "The test suite",
    body: "Offline and deterministic — no network, no keys, no database. It runs on your laptop in under a minute, and it fails if coverage drops.",
  },
  {
    title: "The air-gap",
    body: "Disconnect the host. The product keeps working, because nothing in the coaching path needs the internet.",
  },
  {
    title: "Regulated mode",
    body: "16 tests prove that emotion inference and person-scoring stay off. We will run them in front of your DPO.",
  },
  {
    title: "The routing graph",
    body: "We walk the live graph with your engineer and run a real session through it, node by node. Sessions are reproducible.",
  },
];

export default function EvidencePage() {
  return (
    <>
      <PageHero
        eyebrow="Evidence, not endorsement"
        title="Including the numbers we would rather not publish"
        lead="A vendor who only shows you their good results has told you nothing about their engineering — only about their marketing. Every claim on this page comes with a way to check it."
      />

      {/* stats */}
      <section className="mx-auto max-w-6xl px-6 py-20">
        <div className="grid gap-px overflow-hidden rounded-2xl border border-mist-200 bg-mist-200 sm:grid-cols-3">
          {stats.map((s) => (
            <div key={s.l} className="bg-white p-7">
              <p
                className={`font-mono text-3xl font-semibold tabular-nums tracking-tight ${
                  s.bad ? "text-zen-600" : "text-brand-900"
                }`}
              >
                {s.n}
              </p>
              <p className="mt-2 text-sm leading-6 text-brand-600">{s.l}</p>
            </div>
          ))}
        </div>
        <p className="mt-6 text-center text-sm text-brand-600">
          The number in coral is the one we&apos;d rather not publish. It stays
          on this page until the classifier replaces the keyword screen.
        </p>
      </section>

      {/* honesty block */}
      <section className="bg-mist-50">
        <div className="mx-auto max-w-4xl px-6 py-20">
          <Reveal>
            <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
              What we do not claim
            </p>
            <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
              Four things this does not do yet
            </h2>
          </Reveal>
          <div className="mt-10 space-y-6">
            {notClaimed.map((item, i) => (
              <Reveal
                key={item.title}
                delay={i * 80}
                className="rounded-2xl border-l-4 border-zen-500 bg-white p-7 shadow-sm"
              >
                <h3 className="font-[family-name:var(--font-heading)] font-bold text-brand-900">
                  {item.title}
                </h3>
                <p className="mt-2 text-sm leading-7 text-brand-800">
                  {item.body}
                </p>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* no logos, on purpose */}
      <section className="mx-auto max-w-4xl px-6 py-20">
        <Reveal className="rounded-[2rem] bg-brand-950 p-10 text-white md:p-14">
          <p className="text-sm font-semibold uppercase tracking-widest text-zen-400">
            Read this before the testimonials
          </p>
          <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium sm:text-4xl">
            Endorsements are the weakest evidence on this site
          </h2>
          <p className="mt-5 leading-8 text-white/80">
            Anyone can buy a logo strip. A vendor with nothing to show you but
            customer logos is asking you to trust their other customers&apos;
            judgement instead of your own. That is why every substantive claim
            here — routing, privacy, the regulated mode, the commit gate — is
            backed by something you can run, read, or watch fail, and the
            things we cannot back yet are listed above as plainly as the
            things we can.
          </p>
        </Reveal>
      </section>

      {/* run it yourself */}
      <section className="bg-mist-50">
        <div className="mx-auto max-w-5xl px-6 py-20">
          <Reveal className="text-center">
            <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
              How to verify any of it
            </p>
            <h2 className="mt-3 font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
              Run it yourself
            </h2>
          </Reveal>
          <div className="mt-12 grid gap-6 sm:grid-cols-2">
            {verify.map((v, i) => (
              <Reveal
                key={v.title}
                delay={(i % 2) * 100}
                className="rounded-2xl border border-mist-200 bg-white p-7"
              >
                <h3 className="font-[family-name:var(--font-heading)] font-bold text-brand-900">
                  {v.title}
                </h3>
                <p className="mt-2 text-sm leading-6 text-brand-800">{v.body}</p>
              </Reveal>
            ))}
          </div>
          <Reveal className="mt-14 text-center">
            <Link
              href="/contact"
              className="inline-flex items-center rounded-full bg-brand-900 px-8 py-4 text-[13px] font-semibold uppercase tracking-wider text-white transition hover:bg-brand-700"
            >
              Apply to Be a Design Partner
            </Link>
            <p className="mt-4 text-sm text-brand-600">
              No demo gate. No &ldquo;book a call to see pricing&rdquo;. The
              tests are the demo.
            </p>
          </Reveal>
        </div>
      </section>
    </>
  );
}
