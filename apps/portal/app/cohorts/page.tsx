import Link from "next/link";
import { COHORTS, DEFAULT_THRESHOLD } from "@/lib/mock";
import { Badge, PageIntro, Progress } from "@/components/ui";

/** COH-01 — Cohorts. */
export default function CohortsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Group configuration"
        title="Cohorts without individual surveillance."
        lede="Use eligibility groups to assign programmes and compare aggregate participation. Reporting is suppressed below the threshold."
      />

      <div className="toolbar">
        <Link className="btn" href="/cohorts/new">Create cohort</Link>
        <Link className="btn secondary" href="/privacy">Review privacy threshold</Link>
      </div>

      <div className="grid cols-3">
        {COHORTS.map((c) => (
          <div key={c.name} className={c.suppressed ? "card warning" : "card"}>
            <div className="between">
              <h2>{c.name}</h2>
              <Badge tone={c.badge}>{c.size}</Badge>
            </div>
            <p className="tiny" style={{ margin: "10px 0 14px" }}>{c.note}</p>
            <Progress
              value={c.activated}
              label={`${c.activated}% of eligible members activated`}
            />
            <p className="tiny" style={{ margin: "8px 0 14px" }}>{c.activated}% activated</p>
            <Link className="btn secondary" href="/cohorts/new">Edit cohort</Link>
          </div>
        ))}
      </div>

      <div className="spacer" />

      <div className="notice">
        <span aria-hidden="true">⛨</span>
        <div>
          A cohort is a reporting unit, not a roster. Below {DEFAULT_THRESHOLD}{" "}
          active members a cohort stops reporting separately and folds into the
          wider population — the suppressed row above is that rule working, not a
          fault to fix.
        </div>
      </div>
    </>
  );
}
