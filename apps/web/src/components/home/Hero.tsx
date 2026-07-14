import Link from "next/link";
import Image from "next/image";
import Reveal from "@/components/Reveal";

export default function Hero() {
  return (
    <section className="relative overflow-hidden bg-white">
      {/* photographic backdrop, fading to white on the left */}
      <div aria-hidden className="absolute inset-0">
        <Image
          src="/hero.jpg"
          alt=""
          fill
          priority
          sizes="100vw"
          className="object-cover object-[70%_20%] opacity-90"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-white via-white/85 to-white/10" />
      </div>

      <div className="relative mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <Reveal className="max-w-2xl">
          {/* enso mark, echoing the logo */}
          <svg viewBox="0 0 48 48" className="h-14 w-14" aria-hidden>
            <path
              d="M34.5 13.5a13 13 0 1 0 3.2 8.6"
              fill="none"
              stroke="#f56b6b"
              strokeWidth="4.5"
              strokeLinecap="round"
            />
            <circle cx="36" cy="12" r="3" fill="#f56b6b" />
          </svg>

          <h1 className="mt-6 font-[family-name:var(--font-heading)] text-[2.6rem] font-medium leading-[1.15] text-brand-900 sm:text-5xl lg:text-[3.4rem]">
            Every Employee, Coached in the Moments That Matter
          </h1>
          <p className="mt-6 max-w-md text-lg leading-relaxed text-brand-800">
            A coaching-native AI platform that turns everyday hesitation into
            clear decisions and measurable performance.
          </p>
          <div className="mt-9">
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
