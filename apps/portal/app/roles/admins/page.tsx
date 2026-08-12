"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getAdmins } from "@/lib/api";
import { ROLE_BOUNDARY } from "@/lib/copy";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

const ROLE_LABELS: Record<string, string> = {
  benefits_owner: "Benefits owner",
  programme_admin: "Programme admin",
  analyst: "Analyst",
  privacy_reviewer: "Privacy reviewer",
};

/**
 * ROL-02 — Administrator access. LIVE (2026-08-13).
 *
 * Identity IS shown here, unlike the seat list, and the difference is the whole
 * point: an administrator is a named officer of the organisation and a
 * quarterly attestation is meaningless without knowing who is being attested.
 * A member is not, which is why seats carry no name at all.
 */
export default function AdminAccessPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Administrator access"
        title="Who can see the reports."
        lede="Every administrator of this organisation, their role, and when they were last attested."
      />
      <LiveScreen load={getAdmins} what="administrators">
        {(admins) => (
          <>
            <div className="card">
              <div className="list">
                {admins.map((a) => (
                  <div className="list-item" key={a.id}>
                    <div className="grow">
                      <b>{a.name || a.email}</b>
                      <div className="tiny">{a.email}</div>
                    </div>
                    <Badge tone={a.role === "analyst" ? "" : "good"}>
                      {ROLE_LABELS[a.role] ?? a.role}
                    </Badge>
                    <span className="tiny">
                      {a.attested_on ? `Attested ${a.attested_on}` : "Not attested"}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <Spacer />

            <div className="grid cols-2">
              <div className="card">
                <h2>Adding or removing an administrator</h2>
                <p className="tiny">
                  Not done from this screen, deliberately. Administrator access is granted
                  through CereBro provisioning, so a compromised session here cannot grant
                  itself company.
                </p>
              </div>
              <div className="card tint">
                <h2>The boundary</h2>
                <p className="tiny">{ROLE_BOUNDARY}</p>
              </div>
            </div>

            <Spacer />

            <Notice tone="info" icon="i">
              An analyst can read this list. Knowing who can see the reports is part of the
              governance story rather than a privileged fact.
            </Notice>

            <div className="toolbar">
              <Link className="btn secondary" href="/roles">Back to roles</Link>
            </div>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
