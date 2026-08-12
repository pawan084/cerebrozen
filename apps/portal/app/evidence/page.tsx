import { EVIDENCE_ITEMS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** EVI-01 — Evidence library. */
export default function EvidencePage() {
  return (
    <>
      <PageIntro
        eyebrow="Content governance"
        title="Clinical review and content governance."
        lede="What each part of the product claims, what it is based on, and when it was last reviewed. Organisations can read this; they cannot change it."
      />

      <SampleData />

      <div className="card">
        <div className="list">
          {EVIDENCE_ITEMS.map((e) => (
            <div className="list-item" key={e.name}>
              <div className="grow">
                <b>{e.name}</b>
                <div className="tiny">{e.detail}</div>
              </div>
              <Badge tone={e.tone}>{e.badge}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card tint">
          <h2>The standing rule</h2>
          <p className="tiny">
            CereBro is a companion alongside care, never a replacement. Nothing in the product
            diagnoses, treats or claims a clinical outcome, and content that drifts toward that
            language is rewritten rather than footnoted.
          </p>
        </div>
        <div className="card">
          <h2>What review covers</h2>
          <ul className="tiny" style={{ marginTop: 10, paddingLeft: 18 }}>
            <li>Whether a claim is supported by the mechanism behind it</li>
            <li>Whether comfort content is labelled as comfort content</li>
            <li>Whether survey wording stays non-diagnostic</li>
            <li>Whether an activity implies a faculty it does not train</li>
          </ul>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        “In review” means the item is live and its wording is being re-checked — not that it
        is withheld from members.
      </Notice>
    </>
  );
}
