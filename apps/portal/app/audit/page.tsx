"use client";

import { LiveScreen, RequireSession } from "@/components/data";
import { getAudit } from "@/lib/api";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

const ACTION_LABELS: Record<string, string> = {
  "org.settings_update": "Changed organisation settings",
  "org.group_create": "Created an eligibility group",
  "org.seat_add": "Added a seat",
  "org.seat_end": "Ended a seat",
  "org.programme_sponsor": "Sponsored a programme",
};

/**
 * AUD-01 — Audit log. LIVE (2026-08-13).
 *
 * This screen promised "trace every administrative action" while nothing
 * recorded what an org administrator did — so it was the one surface the claim
 * was false for. Every mutating /org route now writes a row, and this reads
 * them back, scoped to the organisation by an id stamped at write time.
 *
 * There are no member entries because there are no administrative actions that
 * touch a member's wellbeing data. A log of reads would imply reads are
 * possible.
 */
export default function AuditPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Governance"
        title="Trace every administrative action."
        lede="Who did what, and when. Eligibility, programmes, reports and settings — because those are the only actions this portal can take."
      />
      <LiveScreen load={getAudit} what="your audit trail">
        {(rows) =>
          rows.length === 0 ? (
            <Notice tone="info" icon="i">
              Nothing recorded yet. The trail starts with the first administrative change.
            </Notice>
          ) : (
            <>
              <div className="card">
                <div className="list">
                  {rows.map((r) => (
                    <div className="list-item" key={r.id}>
                      <div className="grow">
                        <b>{ACTION_LABELS[r.action] ?? r.action}</b>
                        <div className="tiny">
                          {r.admin_email}
                          {Object.keys(r.detail).length
                            ? ` · ${Object.entries(r.detail)
                                .map(([k, v]) => `${k}: ${String(v)}`)
                                .join(", ")}`
                            : ""}
                        </div>
                      </div>
                      <Badge>{new Date(r.created_at).toLocaleString()}</Badge>
                    </div>
                  ))}
                </div>
              </div>

              <Spacer />

              <Notice tone="info" icon="⛨">
                Nothing in the portal updates or deletes these rows, and there is no route that
                could — the point of a trail is that the person being trailed cannot edit it.
                CereBro staff actions are recorded separately and are not shown here: what we
                do is our trail, not yours.
              </Notice>
            </>
          )
        }
      </LiveScreen>
    </RequireSession>
  );
}
