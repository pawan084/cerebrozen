import Link from "next/link";
import { BILLING_METRICS } from "@/lib/mock";
import { Metric, Notice, PageIntro, Spacer } from "@/components/ui";

/** BIL-01 — Billing & contract. */
export default function BillingPage() {
  return (
    <>
      <PageIntro
        eyebrow="Commercial"
        title="Seats, invoices and renewal."
        lede="Sponsorship is billed on licensed seats, not on how much anyone used the product — usage-based pricing would create a reason to watch individuals."
      />

      <div className="grid cols-4">
        {BILLING_METRICS.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} delta={m.delta} />
        ))}
      </div>

      <Spacer />

      <Notice tone="info" icon="⛨">
        Activation figures appear here because seats are the commercial unit. They are group
        totals, and no invoice line ever names a member.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/billing/contract">Contract &amp; invoices</Link>
      </div>
    </>
  );
}
