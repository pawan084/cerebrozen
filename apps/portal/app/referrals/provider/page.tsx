import Link from "next/link";
import { PROVIDER_DETAIL } from "@/lib/mock";
import { Badge, Metric, Notice, PageIntro, Spacer } from "@/components/ui";

/** REF-02 — Provider detail. */
export default function ProviderDetailPage() {
  const p = PROVIDER_DETAIL;
  return (
    <>
      <PageIntro
        eyebrow="Provider"
        title={`${p.name}.`}
        lede="Verification, regions and member handoff. A provider appears to members only while its verification is current or explicitly flagged."
      />

      <div className="grid cols-4">
        {p.metrics.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} />
        ))}
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Verification</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {p.verification.map((v) => (
              <div className="list-item" key={v.name}>
                <div className="grow">
                  <b>{v.name}</b>
                  <div className="tiny">{v.detail}</div>
                </div>
                <Badge tone={v.tone}>{v.badge}</Badge>
              </div>
            ))}
          </div>
          <p className="tiny" style={{ marginTop: 12 }}>
            “Verified” means a named source was checked on the date shown — not that the
            provider is endorsed.
          </p>
        </div>

        <div className="card tint">
          <h2>Member handoff</h2>
          <p className="tiny">
            The member sees the provider’s hours, languages and coverage, and chooses whether
            to make contact. CereBro passes no reason, no history and no wellbeing content, and
            records nothing about the choice for the organisation.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        Disabling a provider takes effect for members immediately, and existing conversations
        stay with that provider — CereBro cannot reach into them.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/referrals">Back to network</Link>
      </div>
    </>
  );
}
