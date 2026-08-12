import Link from "next/link";
import { INTEGRATION_DETAIL } from "@/lib/mock";
import { Badge, Metric, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** INT-02 — Integration detail. */
export default function IntegrationDetailPage() {
  const d = INTEGRATION_DETAIL;
  return (
    <>
      <PageIntro
        eyebrow="Integration"
        title={`${d.name}.`}
        lede="Configuration, logs and the data boundary — including the fields this connection will refuse."
      />

      <SampleData />

      <div className="grid cols-4">
        {d.metrics.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} />
        ))}
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Fields</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {d.fields.map((f) => (
              <div className="list-item" key={f.name}>
                <div aria-hidden="true" className={f.accepted ? "item-ic sage" : "item-ic"}>
                  {f.accepted ? "✓" : "✕"}
                </div>
                <div className="grow">
                  <b>{f.name}</b>
                </div>
                <Badge tone={f.accepted ? "good" : "danger"}>
                  {f.accepted ? "Accepted" : "Rejected"}
                </Badge>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Recent syncs</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {d.log.map((l) => (
              <div className="list-item" key={l.at}>
                <div className="grow">
                  <b>{l.at}</b>
                  <div className="tiny">{l.detail}</div>
                </div>
                <Badge tone={l.tone}>{l.badge}</Badge>
              </div>
            ))}
          </div>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        A rejected record is reported with the reason and the row number — never with the
        rejected value, in case the value is exactly what should not have been sent.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/integrations">Back to integrations</Link>
      </div>
    </>
  );
}
