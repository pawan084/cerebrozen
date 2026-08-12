"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getGroupTotals } from "@/lib/api";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * MEM-03 — Eligibility group detail. LIVE (2026-08-13).
 *
 * Shows every group's eligibility and access status, from the same suppressed
 * totals the cohorts screen uses. The prototype had a single hardcoded group
 * with an "assigned benefits" list; benefits per group are sponsorship rows, so
 * that section moved to the programmes screen where the data actually lives
 * rather than being invented here.
 */
export default function GroupDetailPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Eligibility groups"
        title="Eligibility and access, per group."
        lede="This page shows eligibility and aggregate access status. It does not reveal who used practices, Talk, Journal, Sleep or safety features."
      />
      <LiveScreen load={getGroupTotals} what="your groups">
        {(totals) =>
          totals.length === 0 ? (
            <Notice tone="info" icon="i">
              No eligibility groups yet. Create one from the{" "}
              <Link href="/cohorts/new">cohort builder</Link>.
            </Notice>
          ) : (
            <>
              <div className="card">
                <div className="list">
                  {totals.map((t) => (
                    <div className="list-item" key={t.group_id ?? t.name}>
                      <div className="grow">
                        <b>{t.name}</b>
                        <div className="tiny">{t.eligible} eligible seats</div>
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

              <Spacer />

              <Notice tone="info" icon="⛨">
                Ending a group&rsquo;s sponsorship removes organisation-funded entitlements from
                the chosen date. Personal history stays with each member, and safety tools
                remain available to them whether or not anyone is paying.
              </Notice>

              <div className="toolbar">
                <Link className="btn secondary" href="/members">Back to members</Link>
                <Link className="btn secondary" href="/cohorts">Cohort reporting</Link>
              </div>
            </>
          )
        }
      </LiveScreen>
    </RequireSession>
  );
}
