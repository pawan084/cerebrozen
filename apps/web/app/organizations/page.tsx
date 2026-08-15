import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = pageMeta({
  title: "For organizations",
  description: "Sponsor private member access without receiving personal wellness data. The privacy boundary, in full.",
  path: "/organizations",
});

// Structure and copy follow ref/landing.html's `organizations` page, including
// its two-column "never shared / potentially reportable" boundary — that table
// is the point of the page and is reproduced in full.
//
// One deliberate departure: the prototype reads as though the organisation
// layer exists. It does not. There is no organisation, sponsorship, entitlement
// or cohort model in this backend (REDESIGN_V2 §3.3), and apps/portal is a
// design surface on mock data. So the status is stated plainly at the top and
// the CTA is a conversation, not a signup. Selling a portal that cannot yet
// hold a seat would be the worst kind of over-claim on the one page whose
// subject is trust.

const AUDIENCES: ReadonlyArray<{ who: string; what: string }> = [
  { who: "Employers", what: "Voluntary stress and sleep support, with no manager-level view of anyone's activity." },
  { who: "Universities", what: "Exam-period programmes and private pathways to campus support." },
  { who: "Clinics", what: "Structured self-care between appointments, with consent-based referral." },
  { who: "Insurers & NGOs", what: "Sponsored preventive wellness, kept strictly separate from claims or eligibility decisions." },
];

const CAPABILITIES: ReadonlyArray<{ name: string; detail: string }> = [
  { name: "Eligibility", detail: "Codes, verified email, SSO, API or HRIS-fed membership records." },
  { name: "Programmes", detail: "Approved stress, sleep, mindfulness and custom pathways." },
  { name: "Privacy-safe reporting", detail: "Aggregate activation and participation above a minimum cohort size, never per person." },
  { name: "Referral networks", detail: "Member-controlled access to EAP, clinic and public support resources." },
  { name: "Governance", detail: "Roles, MFA, audit history, data-processing controls and report-access history." },
  { name: "Contracts & billing", detail: "Seats, sponsorship dates, invoices, renewals and access transition plans." },
];

const NEVER_SHARED: readonly string[] = [
  "Chats and voice transcripts",
  "Journal entries and personal notes",
  "Individual mood and sleep records",
  "Safety plans, trusted contacts and crisis-resource use",
  "Referral reasons or provider selection",
];

const REPORTABLE: readonly string[] = [
  "Eligible-member and activation totals",
  "Aggregate programme participation",
  "Optional anonymous satisfaction surveys",
  "Group-level outcomes above privacy thresholds",
  "Administrative, billing and integration status",
];

export default function Organizations() {
  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>

      <nav className="nav">
        <div className="container nav-inner">
          <Link className="brand" href="/">
            <BrandMark size={26} /> CereBro
          </Link>
          <div className="nav-links">
            <Link href="/">Home</Link>
            <Link href="/privacy">Privacy</Link>
            <Link href="/support">Support</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">B2B2C sponsored access</p>
          <h1>Fund wellness access. Do not create surveillance.</h1>

          <p className="lede">
            CereBro gives employers, universities, clinics, insurers and public programmes a
            way to sponsor private member access, while reporting stays limited to approved
            aggregate information.
          </p>

          <p className="disclaimer">
            <strong>Status: in design, not yet available.</strong> The organisation layer —
            sponsorship, seats, cohorts and role-based access — is being designed and is not
            built. There is nothing to buy today. If the model below fits how you would want
            to sponsor access, we would rather talk during the design than after it.
          </p>

          <h2 id="boundary">Organizations connect to membership — not personal wellness data.</h2>
          <p>
            Eligibility, sponsorship and aggregate reporting are separated from chats,
            journals, moods, sleep records, safety plans and referral reasons. Those two sides
            meet only at the membership record.
          </p>

          <h3>What organizations must never receive</h3>
          <ul>
            {NEVER_SHARED.map((x) => (<li key={x}>{x}</li>))}
          </ul>

          <h3>What may be reported, in aggregate</h3>
          <ul>
            {REPORTABLE.map((x) => (<li key={x}>{x}</li>))}
          </ul>
          <p>
            Aggregate figures are only meaningful above a minimum cohort size. Below that
            threshold a &quot;group&quot; is a person wearing a group&apos;s name, so the
            intended behaviour is to withhold the number rather than round it.
          </p>

          <h2>Who it is for</h2>
          <p>
            One privacy model, different delivery contexts. Every deployment needs its own
            legal, security, operational and clinical review, and the product experience stays
            member-controlled in all of them.
          </p>
          <ul>
            {AUDIENCES.map((a) => (
              <li key={a.who}><strong>{a.who}</strong> — {a.what}</li>
            ))}
          </ul>

          <h2>What sponsoring access would involve</h2>
          <p>
            The capabilities below describe the intended portal. Availability depends on
            backend, security, legal and operational implementation — not on interface design
            alone.
          </p>
          <ul>
            {CAPABILITIES.map((c) => (
              <li key={c.name}><strong>{c.name}</strong> — {c.detail}</li>
            ))}
          </ul>

          <h2>Design a pilot around trust, not surveillance</h2>
          <p>
            Email <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a> with
            &quot;Pilot&quot; in the subject line, and tell us your organisation type, roughly
            how many members it would cover, and the programme you have in mind. We will say
            plainly what exists today and what does not.
          </p>

          <p>
            <Link href="/">Return to the consumer experience</Link> ·{" "}
            <Link href="/privacy">Privacy approach</Link> ·{" "}
            <Link href="/safety">Safety centre</Link>
          </p>

          <p className="disclaimer">
            CereBro is wellness support, not emergency care, and not a medical device. It does
            not diagnose or treat any condition, and it is not a substitute for an employer&apos;s
            or institution&apos;s duty of care.
          </p>
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
