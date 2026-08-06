import Link from "next/link";
import {
  DATA_FRESHNESS,
  GOVERNANCE_ALERTS,
  KPIS,
  LAUNCH_STEPS,
  PROGRAMME_FUNNEL,
  WEEKLY_ACTIVE,
} from "@/lib/mock";
import { Badge, BarChart, Metric, PrivacyWall, Progress, Spacer } from "@/components/ui";

/** DASH-01 — Organisation dashboard. */
export default function DashboardPage() {
  const done = LAUNCH_STEPS.filter((s) => s.done).length;
  const pct = Math.round((done / LAUNCH_STEPS.length) * 100);
  const remaining = LAUNCH_STEPS.filter((s) => !s.done).map((s) => s.label);
  const peak = WEEKLY_ACTIVE[WEEKLY_ACTIVE.length - 1];

  return (
    <>
      <section className="hero">
        <div className="eyebrow">Organisation overview</div>
        <h1>Support people without watching them.</h1>
        <p className="lede">
          CereBro gives eligible members private emotional-regulation, sleep and
          reflection support while your organisation sees only safe aggregate
          programme performance.
        </p>
        <div className="toolbar" style={{ marginBottom: 0 }}>
          <Link className="btn" href="/members">Invite eligible members</Link>
          <Link className="btn secondary" href="/programmes">Launch a programme</Link>
          <Link className="btn secondary" href="/privacy">Review privacy model</Link>
        </div>
      </section>

      <Spacer />
      <PrivacyWall />
      <Spacer />

      <div className="grid cols-4">
        {KPIS.map((k) => (
          <Metric key={k.label} value={k.value} label={k.label} delta={k.delta} />
        ))}
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <div className="between">
            <div>
              <div className="eyebrow">Participation trend</div>
              <h2>Weekly active members</h2>
            </div>
            <Badge tone="good">Threshold met</Badge>
          </div>
          <BarChart
            bars={WEEKLY_ACTIVE}
            label={`Weekly active members over ${WEEKLY_ACTIVE.length} weeks, rising to a peak of ${peak.members} in week ${WEEKLY_ACTIVE.length}`}
          />
          <div className="toolbar" style={{ marginBottom: 0 }}>
            <Link className="btn secondary" href="/engagement">Open engagement</Link>
          </div>
        </div>

        <div className="card">
          <div className="eyebrow">Programme health</div>
          <h2>Calm Workdays · 12 weeks</h2>
          <p className="tiny" style={{ marginTop: 6 }}>
            Calm Workdays programme funnel only — organisation-wide totals appear
            in the tiles above.
          </p>
          <div className="list" style={{ marginTop: 10 }}>
            {PROGRAMME_FUNNEL.map((f) => (
              <div className="list-item" key={f.step}>
                <div aria-hidden="true" className={f.good ? "item-ic sage" : "item-ic"}>
                  {f.step}
                </div>
                <div className="grow">
                  <b>{f.label}</b>
                  <div className="tiny">{f.detail}</div>
                </div>
                <Badge tone={f.good ? "good" : ""}>{f.pct}</Badge>
              </div>
            ))}
          </div>
          <div className="toolbar" style={{ marginBottom: 0 }}>
            <Link className="btn secondary" href="/programmes">Open programme library</Link>
          </div>
        </div>
      </div>

      <Spacer />

      <div className="grid cols-3">
        <div className="card">
          <div className="between">
            <h2>Launch readiness</h2>
            <Badge tone={done === LAUNCH_STEPS.length ? "good" : "warn"}>
              {done} of {LAUNCH_STEPS.length}
            </Badge>
          </div>
          <p className="tiny" style={{ margin: "10px 0" }}>
            {remaining.length
              ? `${remaining.join(", ")} remain${remaining.length === 1 ? "s" : ""} incomplete.`
              : "All launch requirements are complete."}
          </p>
          <Progress value={pct} label={`${pct}% of launch requirements complete`} />
        </div>

        <div className="card">
          <div className="between">
            <h2>Data freshness</h2>
            <Badge tone="good">Current</Badge>
          </div>
          <div className="list" style={{ marginTop: 10 }}>
            {DATA_FRESHNESS.map((d) => (
              <div className="list-item" key={d.label}>
                <div className="grow">
                  <b>{d.label}</b>
                  <div className="tiny">{d.detail}</div>
                </div>
                <Badge tone="good">{d.status}</Badge>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <div className="between">
            <h2>Governance alerts</h2>
            <Badge tone="danger">{GOVERNANCE_ALERTS.length} open</Badge>
          </div>
          <div className="list" style={{ marginTop: 10 }}>
            {GOVERNANCE_ALERTS.map((a) => (
              <div className="list-item" key={a.label}>
                <div className="grow">
                  <b>{a.label}</b>
                  <div className="tiny">{a.detail}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
