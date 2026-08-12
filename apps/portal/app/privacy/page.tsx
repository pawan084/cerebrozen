"use client";

import { useState } from "react";
import Link from "next/link";
import { ALWAYS_PRIVATE } from "@/lib/mock";
import { LiveScreen, RequireSession, SaveStatus, useSave } from "@/components/data";
import { getOrg, patchOrg, type Org } from "@/lib/api";
import { Notice, Spacer } from "@/components/ui";

const THRESHOLD_CHOICES = [20, 30, 50];
const RETENTION_CHOICES = [12, 24, 36];

/**
 * PRI-01 — Privacy centre. LIVE (2026-08-12).
 *
 * The three controls an organisation actually gets, now reading and writing the
 * real settings. The fourth control the prototype had — manager dashboards —
 * is absent rather than disabled: there is no such column, and a switch would
 * imply the capability exists behind it.
 *
 * The threshold is the one setting where the server disagrees with the user on
 * purpose. Asking for anything below 20 is CLAMPED, not rejected, and the value
 * that comes back is the value that was stored — so this screen re-renders from
 * the response rather than from what was clicked.
 */
function PrivacyControls({ initial }: { initial: Org }) {
  const [org, setOrg] = useState(initial);
  const { save, ...state } = useSave();

  const update = (patch: Parameters<typeof patchOrg>[0]) =>
    save(() => patchOrg(patch), (fresh) => setOrg(fresh));

  return (
    <>
      <div className="grid cols-2">
        <div className="card success">
          <h2>Always private</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {ALWAYS_PRIVATE.map((row) => (
              <div className="list-item" key={row.what}>
                <div aria-hidden="true" className="item-ic sage">✓</div>
                <div>
                  <b>{row.what}</b>
                  <div className="tiny">{row.rule}</div>
                </div>
              </div>
            ))}
          </div>
          <p className="tiny" style={{ marginTop: 12 }}>
            These four rows are not settings. There is no configuration on this page, or any
            other, that turns them off — and no column in the database they could be stored in.
          </p>
        </div>

        <div className="card">
          <h2>Reporting controls</h2>

          <div style={{ marginTop: 14 }}>
            <span className="label">Minimum cohort threshold</span>
            <p className="tiny">
              No participation figure is reported for a group smaller than this. 20 is the
              floor — a smaller number is raised to it rather than refused.
            </p>
            <div className="toolbar">
              {THRESHOLD_CHOICES.map((n) => (
                <button
                  key={n}
                  type="button"
                  className={org.reporting_threshold === n ? "filter active" : "filter"}
                  aria-pressed={org.reporting_threshold === n}
                  disabled={state.status === "saving"}
                  onClick={() => update({ reporting_threshold: n })}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>

          <Spacer />

          <div className="list-item">
            <div className="grow">
              <b>Small-cell suppression</b>
              <div className="tiny">Hide dimension combinations that would isolate individuals.</div>
            </div>
            <button
              type="button"
              className={org.small_cell_suppression ? "filter active" : "filter"}
              aria-pressed={org.small_cell_suppression}
              disabled={state.status === "saving"}
              onClick={() => update({ small_cell_suppression: !org.small_cell_suppression })}
            >
              {org.small_cell_suppression ? "On" : "Off"}
            </button>
          </div>

          <Spacer />

          <div>
            <span className="label">How long completed reports remain</span>
            <div className="toolbar">
              {RETENTION_CHOICES.map((m) => (
                <button
                  key={m}
                  type="button"
                  className={org.retention_months === m ? "filter active" : "filter"}
                  aria-pressed={org.retention_months === m}
                  disabled={state.status === "saving"}
                  onClick={() => update({ retention_months: m })}
                >
                  {m} months
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <Spacer />
      <SaveStatus state={state} savedLabel="Reporting controls updated." />

      <Notice tone="info" icon="⛨">
        Suppression is applied on the server before any number is sent, so tightening the
        threshold takes effect on the next read — there is no cached figure on this device that
        could outlive the change.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/privacy/data-map">Data map &amp; retention</Link>
      </div>
    </>
  );
}

export default function PrivacyPage() {
  return (
    <RequireSession>
      <div className="eyebrow">Data governance</div>
      <h1>Privacy guardrails by design.</h1>
      <p className="lede">
        Define what the organisation can access, what is prohibited, and when aggregate results
        are suppressed.
      </p>
      <LiveScreen load={getOrg} what="your privacy settings">
        {(org) => <PrivacyControls initial={org} />}
      </LiveScreen>
    </RequireSession>
  );
}
