import Image from "next/image";
import Link from "next/link";
import Reveal from "@/components/Reveal";

export default function CaseStudy() {
  return (
    <section className="bg-mist-50">
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-6 py-24 lg:grid-cols-[1.2fr_1fr] lg:py-28">
        <Reveal>
          <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
            Faster Feedback, Steadier Teams at Meridian Financial
          </h2>
          <p className="mt-6 max-w-xl leading-8 text-brand-800">
            When Meridian Financial rolled CereBroZen out to its middle
            managers and early-career professionals, the annual-review
            bottleneck dissolved: feedback cycles ran three times faster and
            regretted attrition on coached teams fell by a quarter within two
            quarters. The program is now expanding to leadership development,
            new-hire onboarding, and frontline leaders company-wide.
          </p>
          <div className="mt-9">
            <Link
              href="/clients"
              className="inline-flex items-center rounded-full bg-zen-500 px-7 py-3.5 text-[13px] font-semibold uppercase tracking-wider text-white transition hover:bg-zen-600"
            >
              Read the Client Story
            </Link>
          </div>
          <p className="mt-6 text-xs text-brand-500">
            Illustrative client story — names, people, and figures are
            representative examples.
          </p>
        </Reveal>

        {/* phone mockup */}
        <Reveal delay={150} className="mx-auto">
          <div className="w-72 rounded-[2.6rem] border-[10px] border-brand-900 bg-brand-900 shadow-2xl shadow-brand-900/30">
            <div className="overflow-hidden rounded-[2rem] bg-white">
              <div className="relative h-80 w-full">
                <Image
                  src="/person-2.jpg"
                  alt="Portrait of Priya Raghavan, Chief Human Resources Officer at Meridian Financial"
                  fill
                  className="object-cover"
                  sizes="288px"
                />
              </div>
              <div className="bg-brand-900 px-5 py-5 text-center">
                <p className="font-[family-name:var(--font-heading)] font-bold text-white">
                  Priya Raghavan
                </p>
                <p className="mt-0.5 text-xs text-white/70">
                  Chief Human Resources Officer
                </p>
                <p className="mt-3 font-[family-name:var(--font-serif)] text-sm text-zen-400">
                  Meridian Financial
                </p>
              </div>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
