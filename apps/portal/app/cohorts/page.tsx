"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getGroupTotals } from "@/lib/api";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * COH-01 — Cohorts. LIVE (2026-08-12).
 *
 * Reads GET /org/groups/totals, which applies the organisation's reporting
 * threshold server-side. A suppressed group arrives with null counts and
 * `suppressed: true` rather than being omitted, and this screen renders that
 * difference explicitly — "too small to report" must not look like "nobody
 * activated", which is exactly what a blank cell would imply.
 */
export default function CohortsPage() {
  return (
    <RequireSession>
      <LiveScreen load={getGroupTotals} what="cohorts">
        {(totals) => (
          <>
            <PageIntro
              eyebrow="Privacy-safe groups"
              title="Cohorts, with the small ones withheld."
              lede="Group totals for reporting. A cohort below your threshold reports no participation figure at all — not a rounded one, and not a range."
            />

            {totals.length === 0 ? (
              <Notice tone="info" icon="i">
                No cohorts yet. Create one from the{" "}
                <Link href="/cohorts/new">cohort builder</Link>.
              </Notice>
            ) : (
              <div className="card">
                <div className="list">
                  {totals.map((t) => (
                    <div className="list-item" key={t.group_id ?? t.name}>
                      <div className="grow">
                        <b>{t.name}</b>
                        <div className="tiny">
                          {t.eligible} eligible · threshold {t.threshold}
                        </div>
                      </div>
                      {t.suppressed ? (
                        <Badge tone="warn">Too small to report</Badge>
                      ) : (
                        <Badge tone="good">{t.activated} activated</Badge>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            <Spacer />

            <Notice tone="info" icon="⛨">
              Suppression is applied before the numbers leave the server, so a withheld figure
              is not present in the response and cannot be recovered from this page.
            </Notice>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
