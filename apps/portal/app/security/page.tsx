import { SECURITY_CONTROLS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** SEC-01 — Security & compliance. */
export default function SecurityPage() {
  return (
    <>
      <PageIntro
        eyebrow="Security &amp; compliance"
        title="Controls, documents and review status."
        lede="What is in place, when it was last checked, and when it is next due. Nothing here is a certification claim the product has not earned."
      />

      <SampleData />

      <div className="card">
        <div className="list">
          {SECURITY_CONTROLS.map((c) => (
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
          <h2>Documents on request</h2>
          <ul className="tiny" style={{ marginTop: 10, paddingLeft: 18 }}>
            <li>Data-processing agreement</li>
            <li>Sub-processor list</li>
            <li>Penetration-test summary</li>
            <li>DPDP compliance notes</li>
          </ul>
        </div>
        <div className="card tint">
          <h2>What is not claimed</h2>
          <p className="tiny">
            No ISO or SOC certification is asserted here. Where an audit has not been done, this
            page says so rather than implying coverage through adjacent wording.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="⛨">
        The strongest security property is architectural rather than procedural: the portal has
        no read path to personal wellbeing content, so no administrator credential can be
        misused to reach it.
      </Notice>
    </>
  );
}
