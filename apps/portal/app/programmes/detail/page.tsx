import Link from "next/link";
import { PROGRAMME_DETAIL, PROGRAMME_FUNNEL } from "@/lib/mock";
import { Badge, Metric, Notice, PageIntro, PrivacyWall, Spacer } from "@/components/ui";

/** PRO-02 — Programme detail. */
export default function ProgrammeDetailPage() {
  const p = PROGRAMME_DETAIL;
  return (
    <>
      <PageIntro
        eyebrow="Sponsored programme"
        title={`${p.name}.`}
        lede={`${p.window}. Configuration and aggregate health for one programme — organisation-wide totals live on the dashboard.`}
      />

      <PrivacyWall />

      <div className="grid cols-4">
        {p.metrics.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} delta={m.delta} />
        ))}
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <div className="between mobile-stack">
            <div>
              <div className="eyebrow">Participation funnel</div>
              <h2>How far members get</h2>
            </div>
            <Badge tone="good">Threshold met</Badge>
          </div>
          <div className="list" style={{ marginTop: 10 }}>
            {PROGRAMME_FUNNEL.map((f, i) => (
              <div className="list-item" key={f.label}>
                <div aria-hidden="true" className={i % 2 === 0 ? "item-ic sage" : "item-ic"}>
                  {i + 1}
                </div>
                <div className="grow">
                  <b>{f.label}</b>
                  <div className="tiny">{f.detail}</div>
                </div>
                <Badge tone={f.good ? "good" : ""}>{f.pct}</Badge>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Modules included</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {p.modules.map((m) => (
              <div className="list-item" key={m.name}>
                <div className="grow">
                  <b>{m.name}</b>
                  <div className="tiny">{m.detail}</div>
                </div>
                <Badge tone={m.badge === "Core" ? "good" : ""}>{m.badge}</Badge>
              </div>
            ))}
          </div>
          <div className="toolbar">
            <Link className="btn secondary" href="/programmes/pathway">Open pathway builder</Link>
          </div>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        Participation is voluntary at every step. A member who never opens the programme is
        not reported as anything other than “not activated”, and no reminder escalates.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/programmes">Back to library</Link>
      </div>
    </>
  );
}
