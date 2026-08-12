"use client";

import { useState } from "react";
import Link from "next/link";
import { RequireSession, SaveStatus, useSave } from "@/components/data";
import { createGroup } from "@/lib/api";
import { Notice, PageIntro, Spacer } from "@/components/ui";

const SOURCES = [
  { value: "manual", label: "Added by hand" },
  { value: "csv", label: "CSV import" },
  { value: "hris", label: "HRIS sync" },
  { value: "api", label: "Eligibility API" },
];

/**
 * COH-02 — Cohort builder. LIVE (2026-08-12).
 *
 * The prototype previewed an estimated cohort size as you typed. That did not
 * graduate: the estimate came from a made-up base and an assumed activation
 * rate, and a number an administrator can read as a headcount should come from
 * counting people, not from multiplying two constants. The real size appears on
 * the cohorts screen once the group exists — suppressed if it is too small.
 */
function CohortForm() {
  const [name, setName] = useState("");
  const [rule, setRule] = useState("");
  const [source, setSource] = useState("manual");
  const [region, setRegion] = useState("IN");
  const [created, setCreated] = useState<string | null>(null);
  const { save, ...state } = useSave();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const group = await save(() =>
      createGroup({ name: name.trim(), rule: rule.trim(), source, region }),
    );
    if (group) {
      setCreated(group.name);
      setName("");
      setRule("");
    }
  }

  return (
    <>
      <form className="card" onSubmit={submit}>
        <h2>Create a cohort</h2>
        <div className="form-grid" style={{ marginTop: 16 }}>
          <label>
            <span className="label">Cohort name</span>
            <input
              className="field"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="All India employees"
            />
          </label>
          <label>
            <span className="label">Eligibility rule</span>
            <input
              className="field"
              value={rule}
              onChange={(e) => setRule(e.target.value)}
              placeholder="Active employees in India"
            />
          </label>
          <label>
            <span className="label">Source</span>
            <select className="select" value={source} onChange={(e) => setSource(e.target.value)}>
              {SOURCES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="label">Region</span>
            <input className="field" value={region} onChange={(e) => setRegion(e.target.value)} />
          </label>
        </div>
        <div className="toolbar">
          <button type="submit" className="btn" disabled={state.status === "saving"}>
            {state.status === "saving" ? "Creating…" : "Create cohort"}
          </button>
          <Link className="btn secondary" href="/cohorts">Back to cohorts</Link>
        </div>
      </form>

      <Spacer />
      <SaveStatus state={state} savedLabel={created ? `“${created}” created.` : "Cohort created."} />

      <Notice tone="info" icon="⛨">
        A cohort is an eligibility rule, not a segment of behaviour. It cannot be defined by
        what members did — there is no such field to define it with — and its participation
        figure is withheld entirely while it stays below your reporting threshold.
      </Notice>
    </>
  );
}

export default function CohortBuilderPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Cohort builder"
        title="Create a reporting-safe eligibility group."
        lede="Name the group and the rule that decides who is in it. Size is counted later, from the seats you add — it is not estimated here."
      />
      <CohortForm />
    </RequireSession>
  );
}
