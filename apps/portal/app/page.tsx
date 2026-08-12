"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getSummary } from "@/lib/api";
import { Metric, PageIntro, PrivacyWall, Spacer } from "@/components/ui";

/**
 * DASH-01 — Organisation dashboard. LIVE (2026-08-12).
 *
 * Reads GET /org/summary. Every figure is a count over org_memberships; there
 * is no per-member row behind any of them, which is why the fourth tile can say
 * so plainly rather than the page implying it.
 */
export default function DashboardPage() {
  return (
    <RequireSession>
      <LiveScreen load={getSummary} what="your organisation">
        {(s) => (
          <>
            <PageIntro
              eyebrow="Organisation overview"
              title="Support people without watching them."
              lede={`${s.organisation} · ${s.region}. CereBro gives eligible members private emotional-regulation, sleep and reflection support while your organisation sees only safe aggregate programme performance.`}
            />

            <PrivacyWall />

            <div className="grid cols-4">
              <Metric value={String(s.eligible)} label="Eligible members" />
              <Metric
                value={String(s.activated)}
                label="Activated"
                delta={s.eligible ? `${Math.round((s.activated / s.eligible) * 100)}% activation` : undefined}
              />
              <Metric value={String(s.seats_licensed)} label="Licensed seats" />
              <Metric
                value={String(s.reporting_threshold)}
                label="Reporting threshold"
                delta={s.small_cell_suppression ? "Small cells suppressed" : "Suppression off"}
                warn={!s.small_cell_suppression}
              />
            </div>

            <Spacer />

            <div className="grid cols-2">
              <div className="card">
                <h2>Membership</h2>
                <div className="list" style={{ marginTop: 10 }}>
                  <div className="list-item">
                    <div className="grow"><b>Invited</b></div>
                    <span>{s.invited}</span>
                  </div>
                  <div className="list-item">
                    <div className="grow"><b>Activated</b></div>
                    <span>{s.activated}</span>
                  </div>
                  <div className="list-item">
                    <div className="grow"><b>Ended</b></div>
                    <span>{s.ended}</span>
                  </div>
                </div>
                <div className="toolbar">
                  <Link className="btn secondary" href="/members">Members &amp; seats</Link>
                  <Link className="btn secondary" href="/cohorts">Cohorts</Link>
                </div>
              </div>

              <div className="card tint">
                <h2>What this dashboard cannot show</h2>
                <p className="tiny">
                  Individual reporting is{" "}
                  <b>{s.individual_reporting_available ? "available" : "not available"}</b> — and
                  it is not a setting that has been switched off. There is no per-member
                  activity record for this organisation to read, so there is nothing to enable.
                </p>
              </div>
            </div>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
