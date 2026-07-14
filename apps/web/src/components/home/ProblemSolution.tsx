import Reveal from "@/components/Reveal";

export default function ProblemSolution() {
  return (
    <section className="bg-mist-50">
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-6 py-24 lg:grid-cols-[1.1fr_auto_1fr] lg:py-32">
        <Reveal>
          <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
            Performance Gaps Are Behavior Gaps — Close Them Where They Start
          </h2>
        </Reveal>

        <div aria-hidden className="hidden h-full w-1 rounded-full bg-zen-500 lg:block" />

        <Reveal delay={150} className="space-y-6 text-brand-900">
          <div>
            <p className="font-bold">
              Results slip long before the dashboard says so — in small,
              invisible moments:
            </p>
            <ul className="mt-2 space-y-1">
              <li>– The conversation someone kept postponing</li>
              <li>– The decision that waited another week</li>
              <li>– The ownership that stalled at the first ambiguity</li>
            </ul>
          </div>
          <div>
            <p className="font-bold">
              The cost compounds quietly: slower execution, lower energy,
              missed quarters.
              <br />
              With CereBroZen, you can:
            </p>
            <ul className="mt-2 space-y-1">
              <li>– See where behavior stalls across the organization</li>
              <li>– Coach people through the moment, not weeks after it</li>
              <li>– Reinforce the habits that compound into results</li>
            </ul>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
