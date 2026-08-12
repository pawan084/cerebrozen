import Link from "next/link";
import { RUNBOOK_STEPS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** SAF-02 — Safety runbook. */
export default function RunbookPage() {
  return (
    <>
      <PageIntro
        eyebrow="Safety runbook"
        title="Operational ownership without employer surveillance."
        lede="What happens when a crisis signal is detected, and who is responsible at each step. Read the fourth row carefully: the organisation’s role is none."
      />

      <SampleData />

      <div className="card">
        <div className="steps">
          {RUNBOOK_STEPS.map((s, i) => (
            <div className="step" key={s.name}>
              <div className="step-num">{i + 1}</div>
              <div className="grow">
                <b>{s.name}</b>
                <div className="tiny">{s.detail}</div>
              </div>
              <Badge tone={s.owner === "Nobody" ? "danger" : s.owner === "Member" ? "info" : ""}>
                {s.owner}
              </Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <Notice tone="danger" icon="!">
        <b>CereBro is not an emergency service and cannot monitor anyone’s safety.</b>
        <br />
        It surfaces region-correct human help inside the member’s own session. It does not
        dispatch, alert an employer, or watch for risk between sessions.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/safety">Back to safety operations</Link>
      </div>
    </>
  );
}
