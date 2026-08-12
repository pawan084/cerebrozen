import Link from "next/link";
import { DATA_FLOWS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/** PRI-02 — Data map & retention. */
export default function DataMapPage() {
  return (
    <>
      <PageIntro
        eyebrow="Data governance"
        title="What moves, where it lives and how long it stays."
        lede="Four flows, and one of them is the important one: personal wellbeing content does not appear in this table as something that travels, because it does not."
      />

      <div className="card">
        <div className="list">
          {DATA_FLOWS.map((f) => (
            <div className="list-item" key={f.name}>
              <div className="grow">
                <b>{f.name}</b>
                <div className="tiny">
                  {f.from} → {f.to}
                </div>
              </div>
              <Badge tone={f.tone}>{f.retention}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card tint">
          <h2>Primary region</h2>
          <p className="tiny">
            Member and eligibility data for this organisation is processed in India. Changing
            the region is a contract change, not a portal setting.
          </p>
        </div>
        <div className="card">
          <h2>Deletion</h2>
          <p className="tiny">
            Ending sponsorship removes organisation-funded entitlements. It does not delete the
            member’s account or their history — that belongs to them, and only they can erase
            it, from inside the app.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="⛨">
        The row that matters most is the last one. Chats, journals, moods, sleep records and
        safety plans have no arrow out of the member account, which is why no retention period
        is set for them here.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/privacy">Back to privacy centre</Link>
      </div>
    </>
  );
}
