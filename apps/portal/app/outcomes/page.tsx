import { OUTCOME_MEASURES } from "@/lib/mock";
import { Badge, Notice, PageIntro, PrivacyWall, Spacer } from "@/components/ui";

/** OUT-01 — Outcome reporting. */
export default function OutcomesPage() {
  return (
    <>
      <PageIntro
        eyebrow="Outcome reporting"
        title="Aggregate, consented and non-diagnostic."
        lede="Voluntary pre/post survey summaries. These describe a group of people who chose to answer — they are not clinical measurement, and they cannot be attributed to anyone."
      />

      <PrivacyWall />

      <div className="card">
        <div className="list">
          {OUTCOME_MEASURES.map((m) => (
            <div className="list-item" key={m.name}>
              <div className="grow">
                <b>{m.name}</b>
                <div className="tiny">
                  {m.detail} · {m.n}
                </div>
              </div>
              <Badge tone={m.tone}>{m.change}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>How to read this</h2>
          <ul className="tiny" style={{ marginTop: 10, paddingLeft: 18 }}>
            <li>Respondents opted in; non-responders are not represented</li>
            <li>Change is descriptive, not causal — nothing here shows CereBro caused it</li>
            <li>No measure is a clinical instrument or a diagnosis</li>
            <li>Cohorts under the threshold are suppressed rather than rounded</li>
          </ul>
        </div>

        <div className="card tint">
          <h2>Why one row says “Suppressed”</h2>
          <p className="tiny">
            The caregiver cohort has fewer respondents than the reporting threshold. Showing a
            percentage for a group that small can identify individuals, so no number is
            produced — not a rounded one, and not a range.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="warn" icon="!">
        Do not present these figures as evidence that the programme improved anyone’s health.
        They summarise what a self-selected group reported, over a period in which many other
        things also happened.
      </Notice>
    </>
  );
}
