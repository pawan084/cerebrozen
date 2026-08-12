import Link from "next/link";
import { PROVIDERS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** REF-01 — Referral network. */
export default function ReferralsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Human support"
        title="Consent-based access to human support."
        lede="Providers a member can choose to reach. Every handoff is member-initiated, and the organisation is never told that one happened."
      />

      <SampleData />

      <Notice tone="info" icon="⛨">
        <b>Referral is a member decision, not an administrative one.</b>
        <br />
        Nobody in this portal can refer a member, see that a member was referred, or learn
        which provider they chose.
      </Notice>

      <div className="card">
        <div className="list">
          {PROVIDERS.map((p) => (
            <div className="list-item" key={p.name}>
              <div className="grow">
                <b>{p.name}</b>
                <div className="tiny">
                  {p.region} · {p.detail}
                </div>
              </div>
              <Badge tone={p.tone}>{p.badge}</Badge>
              <Link className="btn ghost small" href="/referrals/provider">Open</Link>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <Notice tone="warn" icon="!">
        A provider marked <b>Review due</b> is still shown to members, because removing a
        working line on a paperwork deadline would be the more harmful failure. It is flagged
        here so verification is chased.
      </Notice>
    </>
  );
}
