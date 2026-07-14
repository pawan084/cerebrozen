import Reveal from "@/components/Reveal";

const clients = [
  "Meridian Financial",
  "Helix Health",
  "Nordlicht Group",
  "Atlas & Vane",
  "Quantica Systems",
  "Kestrel Logistics",
  "Ardent Energy",
  "Panora Insurance",
];

export default function Logos() {
  return (
    <section className="border-t border-mist-100 bg-white">
      <div className="mx-auto max-w-7xl px-6 py-14">
        <div className="grid grid-cols-2 items-center gap-x-6 gap-y-10 sm:grid-cols-4">
          {clients.map((name, i) => (
            <Reveal
              key={name}
              delay={(i % 4) * 70}
              className="flex items-center justify-center"
            >
              <span className="select-none text-center font-[family-name:var(--font-heading)] text-lg font-semibold tracking-tight text-brand-500 transition hover:text-brand-900">
                {name}
              </span>
            </Reveal>
          ))}
        </div>
        <Reveal className="mt-10 text-center text-xs text-brand-500">
          Illustrative client names, shown to represent typical deployments.
        </Reveal>
      </div>
    </section>
  );
}
