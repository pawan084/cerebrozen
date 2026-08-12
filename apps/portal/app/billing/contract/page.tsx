import Link from "next/link";
import { INVOICES } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/** BIL-02 — Contract & invoices. */
export default function ContractPage() {
  return (
    <>
      <PageIntro
        eyebrow="Commercial documents"
        title="Contract and invoices."
        lede="Commercial documents and change requests. Seat changes take effect at the next renewal unless an amendment is agreed."
      />

      <div className="card">
        <h2>Invoices</h2>
        <div className="list" style={{ marginTop: 10 }}>
          {INVOICES.map((i) => (
            <div className="list-item" key={i.ref}>
              <div className="grow">
                <b>{i.ref}</b>
                <div className="tiny">{i.date}</div>
              </div>
              <span>{i.amount}</span>
              <Badge tone={i.tone}>{i.badge}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Change requests</h2>
          <p className="tiny">
            Seat count, contract term, data region and sponsored programmes are contract terms.
            Raise a change request rather than editing them in the portal.
          </p>
        </div>
        <div className="card tint">
          <h2>Ending sponsorship</h2>
          <p className="tiny">
            Members keep their accounts and their history. They lose the organisation-funded
            entitlement, and safety tools remain available to them regardless.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        Renewal figures are drawn from the same aggregates as every other report, so a
        commercial conversation cannot become a route to individual data.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/billing">Back to billing</Link>
      </div>
    </>
  );
}
