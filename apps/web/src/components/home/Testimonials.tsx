import Reveal from "@/components/Reveal";
import TestimonialCarousel, {
  type Testimonial,
} from "@/components/TestimonialCarousel";

const testimonials: Testimonial[] = [
  {
    quote:
      "I expected another chatbot with a motivational tone. What we got was a coaching system our managers actually open before their hardest conversations — and the behavior change shows.",
    name: "Priya Raghavan",
    role: "Chief Human Resources Officer",
    org: "Meridian Financial",
    photo: "/person-2.jpg",
  },
  {
    quote:
      "We tried workshops for a decade. CereBroZen was the first thing that scaled coaching to all eleven thousand people — and gave us the data to prove it worked.",
    name: "Daniel Okafor",
    role: "VP, People & Culture",
    org: "Nordlicht Group",
    photo: "/person-3.jpg",
  },
  {
    quote:
      "Decision latency was our silent tax. Six months in, teams escalate less, decide faster, and the well-being scores moved in the same direction.",
    name: "Sofia Almeida",
    role: "Chief Operating Officer",
    org: "Helix Health",
    photo: "/person-1.jpg",
  },
];

export default function Testimonials() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <Reveal className="text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-3xl font-medium text-brand-900 sm:text-4xl">
            Trusted by Global Enterprises
          </h2>
          <p className="mx-auto mt-4 max-w-2xl text-brand-800">
            Used by leadership and HR teams to bring AI coaching and behavioral
            clarity to thousands of managers.
          </p>
        </Reveal>
        <Reveal className="mt-16">
          <TestimonialCarousel items={testimonials} />
        </Reveal>
        <Reveal className="mt-10 text-center text-xs text-brand-500">
          Illustrative testimonials — names, organizations, and photos are
          representative examples, not real customers.
        </Reveal>
      </div>
    </section>
  );
}
