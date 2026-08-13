"use client";

import { useState } from "react";
import Link from "next/link";
import { LiveScreen, RequireSession, SaveStatus, useSave } from "@/components/data";
import {
  ELIGIBILITY_COLUMNS,
  addMember,
  getGroups,
  importMembers,
  unknownColumns,
  type Group,
  type ImportResult,
} from "@/lib/api";
import { Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * MEM-02 — Invite & eligibility import. Both halves are LIVE.
 *
 * The API rejects unknown fields outright (`extra="forbid"`), and the bulk
 * importer keeps that property where it matters most: a column called `mood` or
 * `diagnosis` fails loudly and takes the whole file with it, rather than being
 * dropped on the floor. See `BulkImport` below for why the header is checked in
 * the browser as well as on the server.
 */
function InviteForm({ groups }: { groups: Group[] }) {
  const [email, setEmail] = useState("");
  const [groupId, setGroupId] = useState("");
  const [ref, setRef] = useState("");
  const [end, setEnd] = useState("");
  const { save, ...state } = useSave();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const created = await save(() =>
      addMember({
        email: email.trim(),
        group_id: groupId || null,
        external_ref: ref.trim(),
        access_end: end || null,
      }),
    );
    if (created) {
      setEmail("");
      setRef("");
    }
  }

  return (
    <>
      <form className="card" onSubmit={submit}>
        <h2>Single invitation</h2>
        <div className="form-grid" style={{ marginTop: 16 }}>
          <label>
            <span className="label">Work or university email</span>
            <input
              className="field"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="member@organisation.com"
            />
          </label>
          <label>
            <span className="label">Your reference</span>
            <input
              className="field"
              value={ref}
              onChange={(e) => setRef(e.target.value)}
              placeholder="EMP-1024"
            />
          </label>
          <label>
            <span className="label">Eligibility group</span>
            <select className="select" value={groupId} onChange={(e) => setGroupId(e.target.value)}>
              <option value="">No group</option>
              {groups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="label">Access end date</span>
            <input className="field" type="date" value={end} onChange={(e) => setEnd(e.target.value)} />
          </label>
        </div>
        <div className="toolbar">
          <button type="submit" className="btn" disabled={state.status === "saving"}>
            {state.status === "saving" ? "Adding…" : "Add seat"}
          </button>
          <Link className="btn secondary" href="/members">Back to members</Link>
        </div>
      </form>

      <Spacer />
      <SaveStatus state={state} savedLabel="Seat added." />

      <Notice tone="warn" icon="!">
        The address must already have a CereBro account. Adding a seat does not create one —
        an employer cannot conjure an account for somebody, so the person signs up first and
        the seat is added after.
      </Notice>

      <Spacer />

      <Spacer />
      <BulkImport groups={groups} />
    </>
  );
}

/**
 * MEM-02 bulk import.
 *
 * The header is checked HERE, before the file is read past its first line and
 * before anything is sent. That ordering is the point: an export carrying a
 * `diagnosis` column should never leave the administrator's machine, and a
 * server-side check alone would mean we receive the data in order to refuse it.
 * The backend checks again — this copy is a privacy measure, not the guarantee.
 */
function BulkImport({ groups }: { groups: Group[] }) {
  const [groupId, setGroupId] = useState("");
  const [refused, setRefused] = useState<string[] | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const { save, ...state } = useSave();

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";                      // so the same file can be retried
    if (!file) return;
    setResult(null);
    setRefused(null);

    const text = await file.text();
    const unknown = unknownColumns(text);
    if (unknown.length) {
      setRefused(unknown);                    // nothing is uploaded
      return;
    }
    const imported = await save(() => importMembers({ csv: text, group_id: groupId || null }));
    if (imported) setResult(imported);
  }

  const failures = result?.rows.filter((r) => r.outcome !== "added") ?? [];

  return (
    <div className="card">
      <h2>Bulk import</h2>
      <p className="tiny">
        A CSV with a header row. Only <code>{ELIGIBILITY_COLUMNS.join("</code>, <code>")}</code>{" "}
        — any other column is refused and the file is not imported, rather than the column being
        dropped quietly. Nothing about a person&rsquo;s health, mood, absence or care belongs in
        this file, and it will not be accepted if it is there.
      </p>

      <div className="form-grid" style={{ marginTop: 16 }}>
        <label>
          <span className="label">Assign every row to</span>
          <select className="select" value={groupId} onChange={(e) => setGroupId(e.target.value)}>
            <option value="">No group</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>{g.name}</option>
            ))}
          </select>
        </label>
        <label>
          <span className="label">Eligibility file</span>
          <input
            className="field"
            type="file"
            accept=".csv,text/csv"
            onChange={onFile}
            disabled={state.status === "saving"}
          />
        </label>
      </div>

      {state.status === "saving" && <p className="tiny" role="status">Importing…</p>}

      {refused && (
        <Notice tone="danger" icon="!">
          <b>Not imported, and not uploaded.</b> This file has {refused.length === 1 ? "a column" : "columns"}{" "}
          CereBro does not accept: <b>{refused.join(", ")}</b>. It never left your computer — remove
          the {refused.length === 1 ? "column" : "columns"} and choose it again.
        </Notice>
      )}

      <Spacer />
      <SaveStatus state={state} savedLabel="Import finished." />

      {result && (
        <>
          <p className="tiny" style={{ marginTop: 12 }}>
            <b>{result.added}</b> {result.added === 1 ? "seat" : "seats"} added
            {result.skipped > 0 && <> · <b>{result.skipped}</b> not added</>}.
          </p>
          {failures.length > 0 && (
            <div className="table-wrap" style={{ marginTop: 12 }}>
              <table>
                <caption>Rows that were not added</caption>
                <thead>
                  <tr>
                    <th scope="col">Line</th>
                    <th scope="col">Your reference</th>
                    <th scope="col">Why not</th>
                  </tr>
                </thead>
                <tbody>
                  {failures.map((row) => (
                    <tr key={row.line}>
                      <td>{row.line}</td>
                      <td>{row.external_ref || "—"}</td>
                      <td>{row.detail || row.outcome}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="tiny" style={{ marginTop: 12 }}>
            Rows are identified by line number and your own reference. CereBro does not report
            addresses back to you here — the seat list is not a roster of who holds an account.
          </p>
        </>
      )}
    </div>
  );
}

export default function InvitePage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Eligibility onboarding"
        title="Invite members without importing wellness data."
        lede="CereBro accepts eligibility identifiers, access dates and programme assignment only. Personal wellbeing content never enters the organisation portal."
      />
      <LiveScreen load={getGroups} what="your eligibility groups">
        {(groups) => <InviteForm groups={groups} />}
      </LiveScreen>
    </RequireSession>
  );
}
