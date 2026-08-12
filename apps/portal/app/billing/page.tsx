"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getBilling } from "@/lib/api";
import { Metric, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * BIL-01 — Billing & contract. LIVE for what the model knows (2026-08-13).
 *
 * Seats, activation and contract dates are real. Invoices are NOT shown at all
 * rather than mocked: there is no billing integration, and a plausible invoice
 * table is the kind of fiction someone forwards to finance.
 */
export default function BillingPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Commercial"
        title="Seats, usage and renewal."
        lede="Sponsorship is billed on licensed seats, not on how much anyone used the product — usage-based pricing would create a reason to watch individuals."
      />
      <LiveScreen load={getBilling} what="your contract">
        {({ org, summary }) => (
          <>
            <div className="grid cols-4">
              <Metric value={String(org.seats_licensed)} label="Licensed seats" />
              <Metric
                value={String(summary.activated)}
                label="Activated seats"
                delta={
                  org.seats_licensed
                    ? `${Math.round((summary.activated / org.seats_licensed) * 100)}% of licensed`
                    : undefined
                }
              />
              <Metric value={org.contract_end ?? "Open"} label="Renewal date" />
              <Metric value={org.grants_premium ? "Included" : "Not included"} label="Premium entitlement" />
            </div>

            <Spacer />

            <div className="grid cols-2">
              <div className="card">
                <h2>Contract</h2>
                <div className="list" style={{ marginTop: 10 }}>
                  <div className="list-item">
                    <div className="grow"><b>Starts</b></div>
                    <span>{org.contract_start ?? "Open"}</span>
                  </div>
                  <div className="list-item">
                    <div className="grow"><b>Ends</b></div>
                    <span>{org.contract_end ?? "Open"}</span>
                  </div>
                  <div className="list-item">
                    <div className="grow"><b>Region</b></div>
                    <span>{org.region}</span>
                  </div>
                </div>
              </div>

              <div className="card tint">
                <h2>Invoices</h2>
                <p className="tiny">
                  Not shown here. There is no billing integration yet, and a plausible-looking
                  invoice table is exactly the sort of fiction that gets forwarded to finance.
                  Invoices come from your CereBro contact until this is real.
                </p>
              </div>
            </div>

            <Spacer />

            <Notice tone="info" icon="⛨">
              Activation appears here because seats are the commercial unit. It is a group
              total, and no line on any invoice will ever name a member.
            </Notice>

            <div className="toolbar">
              <Link className="btn secondary" href="/settings">Organisation settings</Link>
            </div>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
