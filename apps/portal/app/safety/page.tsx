import Link from "next/link";
import { SAFETY_CHECKS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** SAF-01 — Safety operations. */
export default function SafetyPage() {
  return (
    <>
      <PageIntro
        eyebrow="Safety operations"
        title="Resource verification and escalation testing."
        lede="This page is about whether the safety machinery works — helplines reachable, routing tested, evaluations current. It is not, and cannot be, a view of members in distress."
      />

      <SampleData />

      <Notice tone="danger" icon="!">
        <b>No member appears on this page, ever.</b>
        <br />
        The organisation is not told when a crisis signal is detected, who it concerned, or
        that one occurred at all. Safety operations means the plumbing, not the people.
      </Notice>

      <div className="card">
        <div className="list">
          {SAFETY_CHECKS.map((c) => (
            <div className="list-item" key={c.name}>
              <div className="grow">
                <b>{c.name}</b>
                <div className="tiny">{c.detail}</div>
              </div>
              <Badge tone={c.tone}>{c.badge}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Operational incidents</h2>
          <p className="tiny">
            Record a broken helpline number, a provider outage or a failed escalation route.
            Never a member name, a wellbeing detail or a referral reason.
          </p>
        </div>
        <div className="card tint">
          <h2>Who owns what</h2>
          <p className="tiny">
            The runbook sets out which steps belong to CereBro, which to the member, and which
            to the organisation — which is fewer than most people expect.
          </p>
          <div className="toolbar">
            <Link className="btn secondary" href="/safety/runbook">Open runbook</Link>
          </div>
        </div>
      </div>
    </>
  );
}
