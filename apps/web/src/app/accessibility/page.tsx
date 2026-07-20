import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import { site } from "@/lib/site";

export const metadata: Metadata = {
  title: "Accessibility",
  description:
    "How CereBroZen approaches accessibility — what's in place, what we're working toward, and how to tell us where we fall short.",
};

const sections = [
  {
    title: "1. Our commitment",
    body: [
      "A coaching tool is only useful if everyone it's meant for can actually use it. We're working toward conforming with the Web Content Accessibility Guidelines (WCAG) 2.1 at Level AA across our website and product, and we treat accessibility as ongoing work, not a one-time checkbox.",
    ],
  },
  {
    title: "2. What's in place today",
    body: [
      "Motion is optional: animations respect your operating system's “reduce motion” setting, falling back to instant, non-animated transitions.",
      "Keyboard and focus: interactive controls are reachable and operable by keyboard, with a visible focus indicator.",
      "Colour and contrast: colour is never the only way we convey meaning, and text/background contrast is checked automatically in our build so a regression can't ship silently.",
      "Structure and semantics: pages use meaningful headings and semantic markup so assistive technologies can navigate them.",
    ],
  },
  {
    title: "3. What we're still improving",
    body: [
      "We're expanding automated accessibility checks and running manual screen-reader passes on the highest-traffic flows. Some areas — richer data visualisations and a few interactive components — are still being brought fully in line with WCAG 2.1 AA. If you hit a barrier, it helps us prioritise.",
    ],
  },
  {
    title: "4. Tell us where we fall short",
    body: [
      "If something is hard or impossible to use with your assistive technology, we want to know — it's the fastest way for us to fix it. Please include the page, your device and assistive technology, and what happened.",
    ],
  },
];

export default function AccessibilityPage() {
  return (
    <>
      <PageHero
        eyebrow="Accessibility"
        title="Built to be usable by everyone it's for"
        lead="Where we are, what's still in progress, and how to tell us where we fall short."
      />
      <section className="mx-auto max-w-3xl space-y-8 px-6 py-16 leading-7 text-brand-800/75">
        {sections.map((s) => (
          <div key={s.title}>
            <h2 className="text-lg font-semibold text-brand-900">{s.title}</h2>
            {s.body.map((p) => (
              <p key={p} className="mt-2">
                {p}
              </p>
            ))}
          </div>
        ))}
        <div>
          <h2 className="text-lg font-semibold text-brand-900">5. Contact</h2>
          <p className="mt-2">
            Accessibility feedback:{" "}
            <a
              href={`mailto:${site.email}`}
              className="font-medium text-zen-700 hover:text-zen-600"
            >
              {site.email}
            </a>
            .
          </p>
        </div>
      </section>
    </>
  );
}
