"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getProgrammes } from "@/lib/api";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * PRO-01 — Programme library. LIVE (2026-08-12).
 *
 * Reads GET /org/programmes: what this organisation funds, for which cohort,
 * between which dates. Sponsorship makes a programme available and enrols
 * nobody, so there is no completion figure here to report — the absence is the
 * design, not a gap in the API.
 */
export default function ProgrammesPage() {
  return (
    <RequireSession>
      <LiveScreen load={getProgrammes} what="sponsored programmes">
        {(programmes) => (
          <>
            <PageIntro
              eyebrow="Sponsored programmes"
              title="What your organisation funds."
              lede="Sponsorship makes a programme available to a cohort. Taking it up stays the member's decision, and no completion is reported back to you."
            />

            {programmes.length === 0 ? (
              <Notice tone="info" icon="i">
                Nothing sponsored yet. Members still have the whole free product; sponsorship
                adds the funded programme and premium entitlement on top.
              </Notice>
            ) : (
              <div className="card">
                <div className="list">
                  {programmes.map((p) => (
                    <div className="list-item" key={p.id}>
                      <div className="grow">
                        <b>{p.programme_slug}</b>
                        <div className="tiny">
                          {p.starts_on ?? "open start"} → {p.ends_on ?? "open end"}
                          {p.group_id ? " · one cohort" : " · all eligible members"}
                        </div>
                      </div>
                      <Badge tone={p.is_active ? "good" : ""}>{p.is_active ? "Active" : "Paused"}</Badge>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <Spacer />

            <Notice tone="info" icon="i">
              There is no per-member progress on this page because none is collected. A
              programme funnel would require recording who started and who stopped, which is
              the record this product does not keep.
            </Notice>

            <div className="toolbar">
              <Link className="btn secondary" href="/programmes/pathway">Pathway builder</Link>
            </div>
          </>
        )}
      </LiveScreen>
    </RequireSession>
  );
}
