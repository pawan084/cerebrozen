import { AUDIT_ENTRIES } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/** AUD-01 — Audit log. */
export default function AuditPage() {
  return (
    <>
      <PageIntro
        eyebrow="Governance"
        title="Trace every administrative action."
        lede="Who did what, and when. The log records administration — eligibility, programmes, reports, settings — because those are the only actions this portal can take."
      />

      <div className="toolbar">
        <span className="filter active">All actions</span>
        <span className="filter">Reports</span>
        <span className="filter">Privacy</span>
        <span className="filter">Access</span>
      </div>

      <div className="card">
        <div className="list">
          {AUDIT_ENTRIES.map((e) => (
            <div className="list-item" key={e.at}>
              <div className="grow">
                <b>{e.action}</b>
                <div className="tiny">
                  {e.who} · {e.target}
                </div>
              </div>
              <Badge tone={e.tone}>{e.at}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        There are no entries about members, because there are no administrative actions that
        touch a member’s wellbeing data. A log of reads would imply reads are possible.
      </Notice>
    </>
  );
}
