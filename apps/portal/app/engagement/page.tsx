import { ENGAGEMENT_METRICS, ENGAGEMENT_WEEKS, FEATURE_FAMILIES } from "@/lib/mock";
import { BarChart, Metric, Notice, PageIntro, PrivacyWall, Progress, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** ENG-01 — Engagement analytics. */
export default function EngagementPage() {
  return (
    <>
      <PageIntro
        eyebrow="Anonymous participation"
        title="Engagement without identity tracking."
        lede="Understand whether the benefit is being discovered and used while keeping personal wellness behaviour private."
      />

      <SampleData />

      <PrivacyWall />

      <div className="toolbar">
        <span className="filter active">Last 8 weeks</span>
        <span className="filter">Last 4 weeks</span>
        <span className="filter">All programmes</span>
        <span className="filter">All safe cohorts</span>
      </div>

      <div className="grid cols-4">
        {ENGAGEMENT_METRICS.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} delta={m.delta} />
        ))}
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Weekly participation</h2>
          <BarChart
            bars={ENGAGEMENT_WEEKS}
            label="Weekly participation over 8 weeks, generally increasing and peaking in week 8"
          />
        </div>

        <div className="card">
          <h2>Feature families used</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {FEATURE_FAMILIES.map((f) => (
              <div className="list-item" key={f.name}>
                <div className="grow">
                  <b>{f.name}</b>
                  <div style={{ marginTop: 6 }}>
                    <Progress
                      value={f.pct}
                      label={`${f.name} used by ${f.pct}% of active members`}
                    />
                  </div>
                </div>
                <span>{f.pct}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        Feature-family percentages are based on active members and do not expose
        which individuals used each feature.
      </Notice>
    </>
  );
}
