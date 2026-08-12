import { REPORT_HISTORY, REPORT_TEMPLATES } from "@/lib/mock";
import { Badge, Notice, PageIntro, PrivacyWall, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** REP-01 — Reports centre. */
export default function ReportsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Reporting"
        title="Generate privacy-safe executive exports."
        lede="Every export is built from the same aggregates shown in this portal, with the same thresholds and suppression applied — an export cannot reveal more than the screen it came from."
      />

      <SampleData />

      <PrivacyWall />

      <div className="grid cols-2">
        <div className="card">
          <h2>Templates</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {REPORT_TEMPLATES.map((t) => (
              <div className="list-item" key={t.name}>
                <div className="grow">
                  <b>{t.name}</b>
                  <div className="tiny">{t.detail}</div>
                </div>
                <Badge>{t.period}</Badge>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Recent exports</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {REPORT_HISTORY.map((h) => (
              <div className="list-item" key={h.name}>
                <div className="grow">
                  <b>{h.name}</b>
                  <div className="tiny">
                    {h.date} · {h.by}
                  </div>
                </div>
                <Badge tone={h.tone}>{h.badge}</Badge>
              </div>
            ))}
          </div>
          <p className="tiny" style={{ marginTop: 12 }}>
            Every export is recorded in the audit log, including who generated it and which
            cohorts were suppressed.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        A report that would breach the threshold is not produced with the offending section
        removed silently — the export states that a cohort was suppressed, so a reader knows
        something is missing rather than assuming the group had no activity.
      </Notice>
    </>
  );
}
