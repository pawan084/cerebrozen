import Link from "next/link";
import { GROUP_DETAIL } from "@/lib/mock";
import { Badge, Metric, Notice, PageIntro, Spacer } from "@/components/ui";

/** MEM-03 — Eligibility group detail. */
export default function GroupDetailPage() {
  const g = GROUP_DETAIL;
  return (
    <>
      <PageIntro
        eyebrow="Eligibility group"
        title={`${g.name}.`}
        lede="This page shows eligibility and aggregate access status. It does not reveal who used practices, Talk, Journal, Sleep or safety features."
      />

      <div className="grid cols-4">
        {g.metrics.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} />
        ))}
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Eligibility rules</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {g.rules.map((r) => (
              <div className="list-item" key={r.name}>
                <div className="grow">
                  <b>{r.name}</b>
                  <div className="tiny">{r.detail}</div>
                </div>
                <Badge tone={r.tone}>{r.badge}</Badge>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Assigned benefits</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {g.benefits.map((b) => (
              <div className="list-item" key={b.name}>
                <div aria-hidden="true" className={b.tone === "good" ? "item-ic sage" : "item-ic blue"}>
                  {b.icon}
                </div>
                <div className="grow">
                  <b>{b.name}</b>
                  <div className="tiny">{b.detail}</div>
                </div>
                <Badge tone={b.tone}>{b.badge}</Badge>
              </div>
            ))}
          </div>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        Ending sponsorship removes organisation-funded entitlements from the chosen date.
        Personal history stays with the member, and safety tools remain available to them
        whether or not the organisation is paying.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/members">Back to members</Link>
      </div>
    </>
  );
}
