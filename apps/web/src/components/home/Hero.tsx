import Link from "next/link";
import Image from "next/image";
// Static import lets next/image generate a blur placeholder + intrinsic size, so the
// 646 KB LCP photo fades in instead of popping. data: URL is allowed by the CSP.
import heroImg from "../../../public/hero.jpg";

export default function Hero() {
  return (
    <section className="relative overflow-hidden bg-white">
      {/* photographic backdrop, fading to white on the left, settling from a slow push-in */}
      <div aria-hidden className="absolute inset-0">
        <Image
          src={heroImg}
          alt=""
          fill
          priority
          placeholder="blur"
          sizes="100vw"
          className="animate-ken-burns object-cover object-[70%_20%] opacity-90"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-white via-white/85 to-white/10" />
      </div>

      <div className="relative mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <div className="max-w-2xl">
          {/* enso mark, echoing the logo — draws itself on load */}
          <svg
            viewBox="0 0 48 48"
            className="animate-rise h-14 w-14"
            aria-hidden
          >
            <path
              className="enso-path"
              pathLength={1}
              d="M34.5 13.5a13 13 0 1 0 3.2 8.6"
              fill="none"
              stroke="#f56b6b"
              strokeWidth="4.5"
              strokeLinecap="round"
            />
            <circle cx="36" cy="12" r="3" fill="#f56b6b" />
          </svg>

          <h1
            className="animate-rise mt-6 font-[family-name:var(--font-heading)] text-[2.6rem] font-medium leading-[1.15] text-brand-900 sm:text-5xl lg:text-[3.4rem]"
            style={{ animationDelay: "120ms" }}
          >
            Every Employee, Coached in the Moments That Matter
          </h1>
          <p
            className="animate-rise mt-6 max-w-md text-lg leading-relaxed text-brand-800"
            style={{ animationDelay: "260ms" }}
          >
            A coaching-native AI platform that turns everyday hesitation into
            clear decisions and measurable performance.
          </p>
          <div className="animate-rise mt-9" style={{ animationDelay: "400ms" }}>
            <Link
              href="/contact"
              className="group inline-flex items-center gap-2 rounded-full bg-brand-900 px-8 py-4 text-[13px] font-semibold uppercase tracking-wider text-white transition duration-300 hover:-translate-y-0.5 hover:bg-brand-700 hover:shadow-xl hover:shadow-brand-900/20"
            >
              Request a Demo
              <span className="transition-transform duration-300 group-hover:translate-x-1" aria-hidden>
                →
              </span>
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
