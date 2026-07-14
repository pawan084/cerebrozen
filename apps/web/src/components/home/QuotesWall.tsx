import Image from "next/image";
import Reveal from "@/components/Reveal";

const quotes = [
  "It doesn't just answer my question — it asks the tough ones nobody else does, then walks me to a decision.",
  "I used to rehearse difficult conversations in my head for days. Now I rehearse them once, properly, and just have them.",
  "It feels personal and real — support I can lean on at any hour, without judgment.",
  "It caught me spiraling on a decision I'd sat on for a week, and had me commit to a call within the hour.",
  "The check-ins keep pulling me back and holding me accountable. Two minutes that steer my whole week.",
  "I feel less judged talking things through with my coach first.",
  "It helps me pause, slow my thinking, and change my mindset before I walk into the room.",
  "Anyone whose role demands strategic, out-of-the-box thinking will get real value from this.",
];

export default function QuotesWall() {
  return (
    <section className="relative overflow-hidden">
      <div aria-hidden className="absolute inset-0">
        <Image
          src="/quotes-bg.jpg"
          alt=""
          fill
          className="object-cover"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-brand-950/70" />
      </div>

      <div className="relative mx-auto max-w-7xl px-6 py-24">
        <Reveal className="text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-3xl font-medium text-white sm:text-4xl">
            What CereBroZen Users Have to Say
          </h2>
        </Reveal>

        <div className="mt-14 columns-1 gap-6 sm:columns-2 lg:columns-4">
          {quotes.map((q, i) => (
            <Reveal
              key={q}
              delay={(i % 4) * 90}
              className="mb-6 break-inside-avoid rounded-[1.75rem] border border-white/50 p-6"
            >
              <p className="text-sm font-medium leading-relaxed text-white">
                {q}
              </p>
            </Reveal>
          ))}
        </div>
        <Reveal className="mt-10 text-center text-xs text-white/50">
          Illustrative user quotes, representative of typical coaching
          sessions.
        </Reveal>
      </div>
    </section>
  );
}
