import Link from "next/link";
import { Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** MEM-02 — Invite & eligibility import. */
export default function InvitePage() {
  return (
    <>
      <PageIntro
        eyebrow="Eligibility onboarding"
        title="Invite members without importing wellness data."
        lede="CereBro accepts eligibility identifiers, access dates and programme assignment only. Personal wellbeing content never enters the organisation portal."
      />

      <SampleData />

      <div className="grid cols-2">
        <div className="card">
          <h2>Single invitation</h2>
          <div className="form-grid" style={{ marginTop: 16 }}>
            <label>
              <span className="label">Work or university email</span>
              <input className="field" type="email" placeholder="member@organisation.com" />
            </label>
            <label>
              <span className="label">Access end date</span>
              <input className="field" type="date" defaultValue="2027-03-31" />
            </label>
            <label>
              <span className="label">Eligibility group</span>
              <select className="select" defaultValue="All India employees">
                <option>All India employees</option>
                <option>Graduate trainees</option>
                <option>Caregiver benefit</option>
              </select>
            </label>
            <label>
              <span className="label">Programme</span>
              <select className="select" defaultValue="Calm Workdays">
                <option>Calm Workdays</option>
                <option>Sleep Foundations</option>
                <option>Steady Through Change</option>
              </select>
            </label>
          </div>
          <div className="toolbar">
            <Link className="btn secondary" href="/members">Back to members</Link>
          </div>
        </div>

        <div className="card tint">
          <h2>Bulk eligibility import</h2>
          <p className="tiny">
            Required columns: external eligibility ID, email or SSO identifier, start date, end
            date and group. Do not include health or wellness fields.
          </p>
          <label style={{ display: "block", marginTop: 16 }}>
            <span className="label">Eligibility CSV</span>
            <input className="field" type="file" accept=".csv,text/csv" />
          </label>
          <Spacer />
          <Notice tone="warn" icon="!">
            The importer rejects columns that appear to contain diagnoses, mood, journal, sleep,
            medical, referral or safety data.
          </Notice>
        </div>
      </div>

      <Spacer />

      <div className="card">
        <div className="between mobile-stack">
          <div>
            <h2>Automated eligibility</h2>
            <p className="tiny">
              Use SSO attributes, HRIS sync or the Eligibility API for the ongoing access
              lifecycle.
            </p>
          </div>
          <Link className="btn secondary" href="/integrations">Open integrations</Link>
        </div>
      </div>
    </>
  );
}
