import Link from "next/link";
import { CAMPAIGN_PROHIBITED } from "@/lib/copy";
import { Notice, PageIntro, Spacer } from "@/components/ui";

/** CAM-02 — Campaign builder. */
export default function CampaignBuilderPage() {
  return (
    <>
      <PageIntro
        eyebrow="Member communication"
        title="Audience, content, preview and schedule."
        lede="Campaigns invite and educate. They never pressure, and they never imply that participation is observed."
      />

      <div className="grid cols-2">
        <div className="card">
          <h2>Audience</h2>
          <div className="form-grid" style={{ marginTop: 14 }}>
            <label>
              <span className="label">Eligibility group</span>
              <select className="select" defaultValue="All India employees">
                <option>All India employees</option>
                <option>Graduate trainees</option>
                <option>Caregiver benefit</option>
              </select>
            </label>
            <label>
              <span className="label">Channel</span>
              <select className="select" defaultValue="Email">
                <option>Email</option>
                <option>SSO portal banner</option>
              </select>
            </label>
            <label>
              <span className="label">Send date</span>
              <input className="field" type="date" defaultValue="2026-09-01" />
            </label>
          </div>
        </div>

        <div className="card">
          <h2>Message</h2>
          <label style={{ display: "block", marginTop: 14 }}>
            <span className="label">Subject</span>
            <input className="field" defaultValue="A private wellbeing benefit, paid for by Acme" />
          </label>
          <label style={{ display: "block", marginTop: 12 }}>
            <span className="label">Body</span>
            <textarea
              className="field"
              rows={5}
              defaultValue={"Your employer has paid for CereBro, and cannot see how you use it. Joining is optional and nobody is told whether you did."}
            />
          </label>
        </div>
      </div>

      <Spacer />

      <Notice tone="danger" icon="!">
        <b>This builder will not produce:</b> {CAMPAIGN_PROHIBITED}
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/campaigns">Back to campaigns</Link>
        <Link className="btn secondary" href="/preview">Preview what members receive</Link>
      </div>
    </>
  );
}
