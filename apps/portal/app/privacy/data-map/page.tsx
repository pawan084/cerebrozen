"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getOrg } from "@/lib/api";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * PRI-02 — Data map & retention. LIVE for the parts the model knows.
 *
 * Region and report retention come from the organisation's real settings; the
 * flows themselves are structural facts about the product rather than
 * per-customer configuration, so they are stated rather than fetched.
 *
 * The important row is the last one, and it is deliberately not a flow: personal
 * wellbeing content has no arrow out of the member account, which is why it
 * carries no retention period here.
 */
export default function DataMapPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Data governance"
        title="What moves, where it lives and how long it stays."
        lede="Four flows, and one of them is the important one: personal wellbeing content does not appear as something that travels, because it does not."
      />
      <LiveScreen load={getOrg} what="your retention settings">
        {(org) => (
          <>
            <div className="card">
              <div className="list">
                <div className="list-item">
                  <div className="grow">
                    <b>Eligibility identifiers</b>
                    <div className="tiny">Your HRIS or CSV → CereBro membership</div>
                  </div>
                  <Badge>Contract term + 30 days</Badge>
                </div>
                <div className="list-item">
                  <div className="grow">
                    <b>Administrator identity</b>
                    <div className="tiny">Your sign-in → portal session</div>
                  </div>
                  <Badge>Session only</Badge>
                </div>
                <div className="list-item">
                  <div className="grow">
                    <b>Aggregate participation</b>
                    <div className="tiny">CereBro platform → your reporting</div>
                  </div>
                  <Badge>{org.retention_months} months</Badge>
                </div>
                <div className="list-item">
                  <div className="grow">
                    <b>Personal wellbeing content</b>
                    <div className="tiny">Stays in the member account — no arrow out</div>
                  </div>
                  <Badge tone="good">Member-controlled</Badge>
                </div>
              </div>
            </div>

            <Spacer />

            <div className="grid cols-2">
              <div className="card tint">
                <h2>Primary region</h2>
                <p className="tiny">
                  {org.region}. Changing the processing region is a contract change, not a
                  portal setting.
                </p>
              </div>
              <div className="card">
                <h2>Deletion</h2>
                <p className="tiny">
                  Ending sponsorship removes organisation-funded entitlements. It does not
                  delete the member&rsquo;s account or their history — that belongs to them, and
                  only they can erase it, from inside the app.
                </p>
              </div>
            </div>

            <Spacer />

            <Notice tone="info" icon="⛨">
              Report retention is {org.retention_months} months, set by you in the privacy
              centre. It governs aggregates only; there is no per-member record whose retention
              it could describe.
            </Notice>

            <div className="toolbar">
              <Link className="btn secondary" href="/privacy">Back to privacy centre</Link>
            </div>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
