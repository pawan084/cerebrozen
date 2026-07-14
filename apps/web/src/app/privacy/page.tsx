import type { Metadata } from "next";
import PageHero from "@/components/PageHero";
import { site } from "@/lib/site";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "How CereBroZen collects, uses, and protects personal data.",
};

// TODO: have counsel review this policy before launch.

const sections = [
  {
    title: "1. Who we are",
    body: [
      `${site.name} ("we", "us") provides an AI coaching platform to customer organizations under a subscription agreement. This policy explains what personal data we handle when you use the platform or this website, and the choices you have. Where we process employee data on behalf of a customer organization, that organization is the data controller and we act as its processor under the applicable agreement.`,
    ],
  },
  {
    title: "2. What we collect",
    body: [
      "Account details: your name, work email address, role, and the organization you belong to.",
      "Coaching content: the conversations you have with the coach, the commitments you record, and the patterns the coach notices across your sessions. This content remains private to you — it is never shared with your employer.",
      "Product usage events: sign-ins, feature usage, and technical logs used to keep the service reliable and secure.",
      "Website enquiries: if you request a demo, the details you submit are used only to arrange that conversation.",
    ],
  },
  {
    title: "3. How we use it",
    body: [
      "To deliver the coaching service: remembering your goals, commitments, and context between sessions so a returning person never repeats their intake.",
      "To produce analytics for your organization: aggregated and anonymized only — trends in engagement, follow-through, and well-being. Individual transcripts, and anything that could identify an individual's conversations, are never included.",
      "To operate, secure, and improve the platform, and to meet our legal obligations.",
      "We do not sell personal data. We do not use your coaching conversations to advertise to you.",
    ],
  },
  {
    title: "4. What your employer can and cannot see",
    body: [
      "Your employer sees aggregate, anonymized trends across teams and programs. Your employer does not see your transcripts, your commitments, or any individual record of what you discussed. Attempting to re-identify anonymized analytics is prohibited under our terms with every customer.",
      "In deployments running regulated workplace mode, the platform additionally keeps no emotion inference records and no durable scoring of any individual.",
    ],
  },
  {
    title: "5. Retention and deletion",
    body: [
      "We keep coaching content for as long as your account is active, so the coach can maintain context across sessions. You can export or delete your coaching history from your account at any time — deletion is a function of the product, not a support ticket. When a customer contract ends, customer data is deleted or returned in accordance with the agreement.",
    ],
  },
  {
    title: "6. Security",
    body: [
      "Data is encrypted in transit and at rest. Customer environments are tenant-isolated, and access within our team is role-based and logged. For customers who require it, the platform can run entirely inside their own network with no external egress.",
    ],
  },
  {
    title: "7. Your rights",
    body: [
      "Depending on where you are, you may have rights to access, correct, export, delete, or restrict the processing of your personal data — including under the EU GDPR and India's DPDP Act. You can exercise the most common of these (export and deletion) directly in the product. For anything else, or if your request concerns data controlled by your employer, contact us and we will help route the request correctly.",
    ],
  },
  {
    title: "8. Changes to this policy",
    body: [
      "If we make material changes, we will update this page and note the new effective date. Significant changes affecting customer organizations are communicated to them directly.",
    ],
  },
];

export default function PrivacyPage() {
  return (
    <>
      <PageHero
        eyebrow="Legal"
        title="Privacy Policy"
        lead="What we collect, how we use it, and the choices you have. Last updated July 14, 2026."
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
            For any privacy question or request, write to{" "}
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
