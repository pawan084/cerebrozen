"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getMembers } from "@/lib/api";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * MEM-01 — Members & seats. LIVE (2026-08-12).
 *
 * Reads GET /org/members, which returns sponsorship rows and no identity: no
 * user id, no email, no name. Seats are managed by the organisation's own
 * external_ref, so this table cannot become a roster mapping payroll to CereBro
 * accounts.
 */
export default function MembersPage() {
  return (
    <RequireSession>
      <LiveScreen load={getMembers} what="seats">
        {(members) => (
          <>
            <PageIntro
              eyebrow="Eligibility"
              title="Seats, not a roster."
              lede="Who is sponsored, between which dates. This table carries no name, no email and no account identifier — nothing here can be joined back to a person's use of CereBro."
            />

            {members.length === 0 ? (
              <Notice tone="info" icon="i">
                No seats yet. <Link href="/members/invite">Invite an eligible member</Link> to
                create the first one.
              </Notice>
            ) : (
              <div className="card">
                <div className="list">
                  {members.map((m) => (
                    <div className="list-item" key={m.id}>
                      <div className="grow">
                        <b>{m.external_ref || "(no reference)"}</b>
                        <div className="tiny">
                          {m.access_start ?? "open start"} → {m.access_end ?? "open end"}
                        </div>
                      </div>
                      <Badge tone={m.status === "active" ? "good" : m.status === "ended" ? "" : "info"}>
                        {m.status}
                      </Badge>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <Spacer />

            <Notice tone="info" icon="⛨">
              Ending a seat removes the organisation-funded entitlement on the chosen date. The
              person keeps their account, their history and their safety tools, whether or not
              anyone is paying.
            </Notice>

            <div className="toolbar">
              <Link className="btn secondary" href="/members/invite">Invite &amp; import</Link>
            </div>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
