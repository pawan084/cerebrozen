"use client";

import { useState } from "react";
import { LiveScreen, RequireSession, SaveStatus, useSave } from "@/components/data";
import { getOrg, patchOrg, type Org } from "@/lib/api";
import { Notice, PageIntro, Spacer } from "@/components/ui";

/**
 * ORG-01 — Organisation settings. LIVE (2026-08-12).
 *
 * Editable: the administrative details. NOT editable here, and deliberately so
 * — seats, contract dates and whether sponsorship grants premium are commercial
 * terms, which BIL-02 says are changed by agreement rather than by a form. They
 * are shown read-only so the page is still the whole picture.
 */
function SettingsForm({ initial }: { initial: Org }) {
  const [org, setOrg] = useState(initial);
  const [legal, setLegal] = useState(initial.legal_entity);
  const [primary, setPrimary] = useState(initial.primary_contact_email);
  const [privacy, setPrivacy] = useState(initial.privacy_contact_email);
  const { save, ...state } = useSave();

  const dirty =
    legal !== org.legal_entity ||
    primary !== org.primary_contact_email ||
    privacy !== org.privacy_contact_email;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    await save(
      () =>
        patchOrg({
          legal_entity: legal,
          primary_contact_email: primary,
          privacy_contact_email: privacy,
        }),
      (fresh) => {
        setOrg(fresh);
        // Re-seed from the response, not from what was typed: the server is the
        // authority on what was actually stored.
        setLegal(fresh.legal_entity);
        setPrimary(fresh.primary_contact_email);
        setPrivacy(fresh.privacy_contact_email);
      },
    );
  }

  return (
    <>
      <form className="card" onSubmit={submit}>
        <h2>Organisation profile</h2>
        <div className="form-grid" style={{ marginTop: 14 }}>
          <label>
            <span className="label">Legal entity</span>
            <input className="field" value={legal} onChange={(e) => setLegal(e.target.value)} />
          </label>
          <label>
            <span className="label">Programme contact</span>
            <input
              className="field"
              type="email"
              value={primary}
              onChange={(e) => setPrimary(e.target.value)}
            />
          </label>
          <label>
            <span className="label">Privacy contact</span>
            <input
              className="field"
              type="email"
              value={privacy}
              onChange={(e) => setPrivacy(e.target.value)}
            />
          </label>
        </div>
        <div className="toolbar">
          <button type="submit" className="btn" disabled={!dirty || state.status === "saving"}>
            {state.status === "saving" ? "Saving…" : "Save profile"}
          </button>
        </div>
      </form>

      <Spacer />
      <SaveStatus state={state} savedLabel="Organisation profile saved." />

      <div className="grid cols-2">
        <div className="card tint">
          <h2>Commercial terms</h2>
          <div className="list" style={{ marginTop: 10 }}>
            <div className="list-item">
              <div className="grow"><b>Licensed seats</b></div>
              <span>{org.seats_licensed}</span>
            </div>
            <div className="list-item">
              <div className="grow"><b>Contract</b></div>
              <span>
                {org.contract_start ?? "open"} → {org.contract_end ?? "open"}
              </span>
            </div>
            <div className="list-item">
              <div className="grow"><b>Sponsorship grants premium</b></div>
              <span>{org.grants_premium ? "Yes" : "No"}</span>
            </div>
          </div>
          <p className="tiny" style={{ marginTop: 12 }}>
            Read-only here. These are contract terms, changed by agreement rather than by a
            form somebody can edit at 2am.
          </p>
        </div>

        <div className="card">
          <h2>Data region</h2>
          <p className="tiny">
            {org.region}. Changing the processing region requires a new data-processing
            agreement, so it is not a portal setting either.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="danger" icon="!">
        There is no setting on this page — or anywhere in the portal — that turns on manager
        dashboards, individual reporting or activity export. Those are not features in a
        disabled state; there is no column behind them to switch.
      </Notice>
    </>
  );
}

export default function SettingsPage() {
  return (
    <RequireSession>
      <PageIntro
        eyebrow="Organisation"
        title="Profile, branding, regions and contacts."
        lede="Administrative details for this organisation. None of these settings change what an administrator can see about a member — that boundary is not configurable."
      />
      <LiveScreen load={getOrg} what="your organisation">
        {(org) => <SettingsForm initial={org} />}
      </LiveScreen>
    </RequireSession>
  );
}
