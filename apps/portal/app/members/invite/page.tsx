"use client";

import { useState } from "react";
import Link from "next/link";
import { LiveScreen, RequireSession, SaveStatus, useSave } from "@/components/data";
import { addMember, getGroups, type Group } from "@/lib/api";
import { Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * MEM-02 — Invite & eligibility import. LIVE for the single invitation.
 *
 * The CSV importer is still not built; the field is gone rather than left
 * inert, because a file input that silently does nothing is worse than an
 * honest absence.
 *
 * The API rejects unknown fields outright (`extra="forbid"`), which is the
 * property a bulk importer must keep: a column called `mood` or `diagnosis`
 * has to fail loudly, not be dropped on the floor.
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

      <div className="card tint">
        <h2>Bulk import</h2>
        <p className="tiny">
          Not built yet. When it is, it will accept eligibility identifiers, access dates and
          group only, and reject any column that looks like health, mood, journal, sleep,
          medical, referral or safety data — loudly, rather than by ignoring it.
        </p>
      </div>
    </>
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
