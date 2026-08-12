import { ORG_PROFILE } from "@/lib/mock";
import { Notice, PageIntro, Spacer } from "@/components/ui";

/** ORG-01 — Organisation settings. */
export default function SettingsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Organisation"
        title="Profile, branding, regions and contacts."
        lede="Administrative details for this organisation. None of these settings change what an administrator can see about a member — that boundary is not configurable."
      />

      <div className="card">
        <div className="list">
          {ORG_PROFILE.map((f) => (
            <div className="list-item" key={f.label}>
              <div className="grow">
                <b>{f.label}</b>
                <div className="tiny">{f.value}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Branding</h2>
          <p className="tiny">
            An organisation logo may appear on the invitation and on the sponsored-access
            notice. It never appears inside the member’s own screens, where it would imply the
            employer is present.
          </p>
        </div>
        <div className="card tint">
          <h2>Data region</h2>
          <p className="tiny">
            India. Changing the processing region is a contract change and requires a new
            data-processing agreement.
          </p>
        </div>
      </div>

      <Spacer />

      <Notice tone="danger" icon="!">
        There is no setting on this page — or anywhere in the portal — that turns on manager
        dashboards, individual reporting or activity export. Those are not features that exist
        in a disabled state.
      </Notice>
    </>
  );
}
