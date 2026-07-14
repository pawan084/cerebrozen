import Reveal from "@/components/Reveal";

export default function PageHero({
  eyebrow,
  title,
  lead,
}: {
  eyebrow: string;
  title: string;
  lead?: string;
}) {
  return (
    <section className="bg-mist-50">
      <div className="mx-auto max-w-4xl px-6 py-20 text-center lg:py-28">
        <Reveal>
          <p className="text-sm font-semibold uppercase tracking-widest text-zen-600">
            {eyebrow}
          </p>
          <h1 className="mt-4 font-[family-name:var(--font-heading)] text-4xl font-medium tracking-tight text-brand-900 sm:text-5xl">
            {title}
          </h1>
          {lead && (
            <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-brand-800">
              {lead}
            </p>
          )}
        </Reveal>
      </div>
    </section>
  );
}
