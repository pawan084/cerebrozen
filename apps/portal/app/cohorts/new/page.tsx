"use client";

import Link from "next/link";
import { useState } from "react";
import {
  ACTIVATION_RATE,
  COHORT_BASE_ELIGIBLE,
  COHORT_REGIONS,
  COHORT_RULES,
  COHORT_SOURCES,
  DEFAULT_THRESHOLD,
  THRESHOLD_OPTIONS,
} from "@/lib/mock";
import { Badge, Notice } from "@/components/ui";
import { SampleData } from "@/components/data";

/**
 * COH-02 — Cohort builder.
 *
 * The threshold control and the privacy preview are one thing, not two: every
 * change to the rules or the minimum recomputes whether this cohort would
 * report at all. An administrator should discover a group is too small to
 * report *here*, while they can still widen it — not after launch when the
 * dashboard mysteriously shows nothing.
 *
 * The arithmetic is deliberately simple and local. It models the shape of the
 * rule, not a real population.
 */
export default function CohortBuilderPage() {
  const [name, setName] = useState("New programme cohort");
  const [threshold, setThreshold] = useState<number>(DEFAULT_THRESHOLD);
  const [rules, setRules] = useState<boolean[]>(COHORT_RULES.map((r) => r.defaultOn));

  const eligible = Math.max(
    0,
    COHORT_BASE_ELIGIBLE + rules.reduce((sum, on, i) => (on ? sum + COHORT_RULES[i].adds : sum), 0),
  );
  const reporters = Math.round(eligible * ACTIVATION_RATE);
  const safe = reporters >= threshold;

  return (
    <>
      <div className="eyebrow">Privacy-safe group</div>
      <h1>Create or edit a cohort.</h1>
      <p className="lede">
        The builder warns when a group or filter combination could increase
        re-identification risk.
      </p>

      <SampleData />

      <div className="grid cols-2">
        <div className="card">
          <div className="form-grid">
            <label>
              <span className="label">Cohort name</span>
              <input
                className="field"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <label>
              <span className="label">Eligibility source</span>
              <select className="select" defaultValue={COHORT_SOURCES[0]}>
                {COHORT_SOURCES.map((s) => <option key={s}>{s}</option>)}
              </select>
            </label>
            <label>
              <span className="label">Region</span>
              <select className="select" defaultValue={COHORT_REGIONS[0]}>
                {COHORT_REGIONS.map((r) => <option key={r}>{r}</option>)}
              </select>
            </label>
            <label>
              <span className="label">Minimum reporting threshold</span>
              <select
                className="select"
                value={threshold}
                onChange={(e) => setThreshold(Number(e.target.value))}
              >
                {THRESHOLD_OPTIONS.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
              <span className="help">
                Below this many active members the cohort stops reporting
                separately. {DEFAULT_THRESHOLD} is the default.
              </span>
            </label>
          </div>

          <div style={{ marginTop: 14 }}>
            <span className="label">Eligibility rules</span>
            {COHORT_RULES.map((rule, i) => (
              <label className="check" key={rule.label}>
                <input
                  type="checkbox"
                  checked={rules[i]}
                  onChange={() =>
                    setRules(rules.map((v, j) => (i === j ? !v : v)))
                  }
                />
                {rule.label}
              </label>
            ))}
          </div>

          <div className="toolbar" style={{ marginBottom: 0 }}>
            <button className="btn" type="button" disabled>Save cohort</button>
            <Link className="btn secondary" href="/cohorts">Cancel</Link>
          </div>
          <p className="help">
            Saving is disabled — this is a design review surface with no backend.
          </p>
        </div>

        {/* Live preview: recomputes on every rule and threshold change. */}
        <div className={safe ? "card success" : "card warning"}>
          <h2>Privacy preview</h2>
          <div className="list" style={{ marginTop: 10 }}>
            <div className="list-item">
              <div className="grow">
                <b>Estimated eligible members</b>
                <div className="tiny">Based on current Workday attributes</div>
              </div>
              <b aria-live="polite">{eligible}</b>
            </div>
            <div className="list-item">
              <div className="grow">
                <b>Estimated active reporters</b>
                <div className="tiny">Using the current activation rate</div>
              </div>
              <b aria-live="polite">{reporters}</b>
            </div>
            <div className="list-item">
              <div className="grow">
                <b>Reporting status</b>
                <div className="tiny">
                  {safe
                    ? `${reporters} active reporters is above the minimum of ${threshold}.`
                    : `${reporters} active reporters is below the minimum of ${threshold}. This cohort will report only as part of a wider population.`}
                </div>
              </div>
              <Badge tone={safe ? "good" : "warn"}>
                {safe ? "Safe" : "Suppressed"}
              </Badge>
            </div>
          </div>

          <div style={{ marginTop: 14 }}>
            <Notice icon="⛨" tone={safe ? "" : "warn"}>
              Department + manager + location combinations are blocked when they
              create a micro-cohort.
            </Notice>
          </div>
        </div>
      </div>
    </>
  );
}
