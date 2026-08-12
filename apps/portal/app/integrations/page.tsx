import Link from "next/link";
import { INTEGRATIONS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/** INT-01 — Integrations. */
export default function IntegrationsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Connections"
        title="SSO, HRIS, benefits and provider connections."
        lede="Integrations move eligibility and identity. None of them can carry wellbeing content in either direction."
      />

      <div className="card">
        <div className="list">
          {INTEGRATIONS.map((i) => (
            <div className="list-item" key={i.name}>
              <div className="grow">
                <b>{i.name}</b>
                <div className="tiny">{i.detail}</div>
              </div>
              <Badge tone={i.tone}>{i.badge}</Badge>
              <Link className="btn ghost small" href="/integrations/detail">Open</Link>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <Notice tone="warn" icon="!">
        The eligibility importer rejects fields that look like health, mood, journal, sleep,
        medical, referral or safety data. An integration cannot be configured to send them.
      </Notice>
    </>
  );
}
