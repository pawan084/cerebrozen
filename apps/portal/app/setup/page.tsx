"use client";

import Link from "next/link";
import { LiveScreen, RequireSession } from "@/components/data";
import { getLaunchState, type LaunchState } from "@/lib/api";
import { Metric, Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * SET-01 — Launch checklist. LIVE (2026-08-13).
 *
 * Derived from real state rather than stored as six booleans. A stored flag can
 * say "eligibility connected" while the organisation has no seats — this asks
 * the question directly each time, so the checklist cannot drift from what is
 * actually configured, and nobody can tick a box by editing a row.
 */
function Checklist({ state }: { state: LaunchState }) {
  const done = state.steps.filter((s) => s.done).length;
  const total = state.steps.length;
  const remaining = state.steps.filter((s) => !s.done).map((s) => s.label);

  return (
    <>
      <div className="grid cols-4">
        <Metric value={`${done}/${total}`} label="Requirements complete" />
        <Metric
          value={done === total ? "Ready" : "Not ready"}
          label="Launch status"
          delta={done === total ? "All checks passed" : "Complete remaining steps"}
          warn={done !== total}
        />
        <Metric value={String(state.threshold)} label="Reporting threshold" />
        <Metric value={state.region} label="Primary data region" />
      </div>

      <Spacer />

      <div className="steps">
        {state.steps.map((s, i) => (
          <div className={s.done ? "step done" : "step"} key={s.key}>
            <div className="step-num">{s.done ? "✓" : i + 1}</div>
            <div className="grow">
              <b>{s.label}</b>
              <div className="tiny">{s.detail}</div>
            </div>
            <Link className="btn secondary small" href={s.href}>
              {s.done ? "Review" : "Complete"}
            </Link>
          </div>
        ))}
      </div>

      <Spacer />

      {remaining.length ? (
        <Notice tone="warn" icon="!">
          {remaining.join(", ")} {remaining.length === 1 ? "remains" : "remain"} incomplete.
          Each one is checked against your actual configuration, so a step ticks itself when
          the thing it describes exists.
        </Notice>
      ) : (
        <Notice tone="" icon="✓">
          All launch requirements are complete.
        </Notice>
      )}
    </>
  );
}

export default function SetupPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Implementation"
        title="Launch safely, one requirement at a time."
        lede="The portal will not mark the organisation ready until privacy, identity, eligibility, programme and member-communication requirements are complete."
      />
      <LiveScreen load={getLaunchState} what="your launch state">
        {(state) => <Checklist state={state} />}
      </LiveScreen>
    </RequireSession>
  );
}
