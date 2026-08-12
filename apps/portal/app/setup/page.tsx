import Link from "next/link";
import { SETUP_REQUIREMENTS } from "@/lib/mock";
import { Metric, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** SET-01 — Launch checklist. */
export default function SetupPage() {
  const done = SETUP_REQUIREMENTS.filter((r) => r.done).length;
  const total = SETUP_REQUIREMENTS.length;
  const remaining = SETUP_REQUIREMENTS.filter((r) => !r.done).map((r) => r.label);

  return (
    <>
      <PageIntro
        eyebrow="Implementation"
        title="Launch safely, one requirement at a time."
        lede="The portal will not mark the organisation ready until privacy, identity, eligibility, programme and member-communication requirements are complete."
      />

      <SampleData />

      <div className="grid cols-4">
        <Metric value={`${done}/${total}`} label="Requirements complete" />
        <Metric
          value={done === total ? "Ready" : "Not ready"}
          label="Launch status"
          delta={done === total ? "All checks passed" : "Complete remaining steps"}
          warn={done !== total}
        />
        <Metric value="20" label="Reporting threshold" />
        <Metric value="India" label="Primary data region" />
      </div>

      <Spacer />

      <div className="steps">
        {SETUP_REQUIREMENTS.map((r, i) => (
          <div className={r.done ? "step done" : "step"} key={r.key}>
            <div className="step-num">{r.done ? "✓" : i + 1}</div>
            <div className="grow">
              <b>{r.label}</b>
              <div className="tiny">{r.detail}</div>
            </div>
            <Link className="btn secondary small" href={r.href}>
              {r.done ? "Review" : "Complete"}
            </Link>
          </div>
        ))}
      </div>

      <Spacer />

      {remaining.length ? (
        <Notice tone="warn" icon="!">
          {remaining.join(", ")} {remaining.length === 1 ? "remains" : "remain"} incomplete.
          Members cannot be invited until the eligibility connection is in place.
        </Notice>
      ) : (
        <Notice tone="" icon="✓">All launch requirements are complete.</Notice>
      )}
    </>
  );
}
