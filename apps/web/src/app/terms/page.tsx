import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import { site } from "@/lib/site";

export const metadata: Metadata = {
  title: "Terms of Service",
  description: "The terms that govern use of CereBroZen.",
};

// TODO: have counsel review these terms before launch.

const sections = [
  {
    title: "1. The service",
    body: [
      `${site.name} provides AI-based coaching and related analytics to customer organizations under a subscription agreement. If your organization has a signed agreement with us, that agreement governs where it differs from these terms. Individual users access the service through their organization's subscription.`,
      "Coaching output is guidance to support your own judgement at work. It is not professional medical, psychological, legal, or financial advice, and it is not a substitute for therapy or for emergency services.",
    ],
  },
  {
    title: "2. Your account",
    body: [
      "Keep your credentials confidential and tell us promptly if you believe your account has been compromised. You are responsible for activity under your account. Access ends when your organization's subscription ends or when your organization removes you from it.",
    ],
  },
  {
    title: "3. Acceptable use",
    body: [
      "Use the service only for lawful workplace purposes. In particular, you must not attempt to re-identify anonymized analytics, access another user's private conversations, probe or circumvent security controls, or use the service to build a competing product. Organizations must not use the service, or data derived from it, to surveil, score, or take automated adverse action against individual employees.",
    ],
  },
  {
    title: "4. Your data",
    body: [
      "Your coaching conversations remain private to you, as described in our Privacy Policy. You retain ownership of the content you provide; you grant us the rights needed to operate the service. We use aggregated, anonymized data to provide analytics to your organization and to improve the service.",
    ],
  },
  {
    title: "5. Our intellectual property",
    body: [
      "The platform, its coaching methodology, and all associated software and content are owned by us or our licensors. These terms grant a right to use the service — not a licence to copy, modify, or redistribute it.",
    ],
  },
  {
    title: "6. Disclaimers and liability",
    body: [
      "The service is provided on an “as is” and “as available” basis to the extent permitted by law. We do not warrant that coaching guidance will achieve any particular outcome. To the maximum extent permitted by law, our aggregate liability arising out of the service is limited as set out in the applicable customer agreement; nothing in these terms limits liability that cannot be limited by law.",
    ],
  },
  {
    title: "7. Suspension and termination",
    body: [
      "We may suspend or terminate access for material breach of these terms, for security reasons, or where required by law. Your organization may end its subscription in accordance with its agreement. On termination, data is handled as described in the Privacy Policy and the applicable agreement.",
    ],
  },
  {
    title: "8. Changes",
    body: [
      "We may update these terms from time to time. If we make material changes, we will update this page and note the new effective date, and notify customer organizations directly. Continued use after changes take effect constitutes acceptance.",
    ],
  },
];

export default function TermsPage() {
  return (
    <>
      <PageHero
        eyebrow="Legal"
        title="Terms of Service"
        lead="The terms that govern use of CereBroZen. Last updated July 14, 2026."
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
          <h2 className="text-lg font-semibold text-brand-900">9. Contact</h2>
          <p className="mt-2">
            Questions about these terms:{" "}
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
