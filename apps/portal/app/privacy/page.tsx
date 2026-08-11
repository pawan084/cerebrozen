"use client";

import { useState } from "react";
import { ALWAYS_PRIVATE, DEFAULT_THRESHOLD, RETENTION_OPTIONS, THRESHOLD_OPTIONS } from "@/lib/mock";
import { Notice, Spacer } from "@/components/ui";

/**
 * PRI-01 — Privacy centre.
 *
 * The three controls the organisation actually gets: how small a group may be
 * before it stops reporting, whether unsafe dimension combinations are
 * suppressed, and whether line managers get dashboards at all. Manager
 * dashboards ship off and the page argues for keeping them off rather than
 * presenting the switch as neutral.
 */
export default function PrivacyPage() {
  const [threshold, setThreshold] = useState<number>(DEFAULT_THRESHOLD);
  const [suppression, setSuppression] = useState(true);
  const [managerDashboards, setManagerDashboards] = useState(false);
  const [retention, setRetention] = useState(RETENTION_OPTIONS[0]);

  return (
    <>
      <div className="eyebrow">Data governance</div>
      <h1>Privacy guardrails by design.</h1>
      <p className="lede">
        Define what the organisation can access, what is prohibited, and when
        aggregate results are suppressed.
      </p>

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
            These four rows are not settings. There is no configuration on this
            page, or any other, that turns them off.
          </p>
        </div>

        <div className="card">
          <h2>Reporting controls</h2>
          <div className="list" style={{ marginTop: 10 }}>
            <div className="list-item">
              <div className="grow">
                <b>Minimum cohort threshold</b>
                <div className="tiny">
                  Suppress below the selected number of active members
                </div>
              </div>
              <label>
                <span className="sr-only">Minimum cohort threshold</span>
                <select
                  className="select"
                  style={{ width: 110 }}
                  value={threshold}
                  onChange={(e) => setThreshold(Number(e.target.value))}
                >
                  {THRESHOLD_OPTIONS.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
            </div>

            <div className="list-item">
              <div className="grow">
                <b>Small-cell suppression</b>
                <div className="tiny">Hide unsafe dimensions and combinations</div>
              </div>
              <button
                type="button"
                className={suppression ? "switch on" : "switch"}
                aria-pressed={suppression}
                aria-label="Small-cell suppression"
                onClick={() => setSuppression(!suppression)}
              />
            </div>

            <div className="list-item">
              <div className="grow">
                <b>Manager dashboards</b>
                <div className="tiny">Keep disabled; central benefits admins only</div>
              </div>
              <button
                type="button"
                className={managerDashboards ? "switch on" : "switch"}
                aria-pressed={managerDashboards}
                aria-label="Manager dashboards"
                onClick={() => setManagerDashboards(!managerDashboards)}
              />
            </div>

            <div className="list-item">
              <div className="grow">
                <b>Aggregate retention</b>
                <div className="tiny">How long completed reports remain</div>
              </div>
              <label>
                <span className="sr-only">Aggregate retention</span>
                <select
                  className="select"
                  style={{ width: 140 }}
                  value={retention}
                  onChange={(e) => setRetention(e.target.value)}
                >
                  {RETENTION_OPTIONS.map((r) => <option key={r}>{r}</option>)}
                </select>
              </label>
            </div>
          </div>

          <div style={{ marginTop: 14 }}>
            {!suppression ? (
              <Notice tone="danger" icon="!">
                With small-cell suppression off, a filtered report can narrow to a
                handful of people and effectively name them. Turn it back on.
              </Notice>
            ) : null}
            {managerDashboards ? (
              <div style={{ marginTop: 10 }}>
                <Notice tone="danger" icon="!">
                  Manager dashboards put participation figures in front of the
                  person who writes the performance review. CereBro recommends
                  leaving this off; reporting stays with central benefits
                  administrators.
                </Notice>
              </div>
            ) : null}
          </div>

          <div className="toolbar" style={{ marginBottom: 0 }}>
            <button className="btn" type="button" disabled>Save guardrails</button>
          </div>
          <p className="help">
            Saving is disabled — this is a design review surface with no backend.
          </p>
        </div>
      </div>

      <Spacer />

      {/* The summary carries the tone of the configuration it describes — a
          green panel over a weakened setting would be its own small lie. */}
      <div className={suppression && !managerDashboards ? "notice" : "notice warn"}>
        <span aria-hidden="true">⛨</span>
        <div>
          The current settings suppress any cohort below <b>{threshold}</b> active
          members, {suppression ? "hide" : "do not hide"} small-cell combinations,
          {managerDashboards ? " expose manager dashboards" : " keep manager dashboards off"},
          and keep aggregate reports for {retention}.
        </div>
      </div>
    </>
  );
}
