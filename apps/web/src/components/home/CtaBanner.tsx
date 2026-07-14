import Image from "next/image";
import Link from "next/link";
import Reveal from "@/components/Reveal";

export default function CtaBanner() {
  return (
    <section className="relative overflow-hidden">
      <div aria-hidden className="absolute inset-0">
        <Image
          src="/cta-bg.jpg"
          alt=""
          fill
          className="object-cover"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-zen-500/80" />
      </div>

      <div className="relative mx-auto max-w-4xl px-6 py-28 text-center">
        <Reveal>
          <h2 className="font-[family-name:var(--font-heading)] text-3xl font-semibold leading-snug text-white sm:text-5xl">
            Bring Calm, Focused Performance to Every Desk
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-white">
            CereBroZen gives every employee an always-on AI coach that improves
            decisions, performance, and engagement in the flow of work.
          </p>
          <div className="mt-10">
            <Link
              href="/contact"
              className="inline-flex items-center rounded-full bg-brand-900 px-8 py-4 text-[13px] font-semibold uppercase tracking-wider text-white transition hover:bg-brand-700"
            >
              Request a Demo
            </Link>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
