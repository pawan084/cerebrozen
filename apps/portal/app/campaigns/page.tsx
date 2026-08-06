import { CAMPAIGNS } from "@/lib/mock";
import { CAMPAIGN_PROHIBITED } from "@/lib/copy";
import { Badge, PageIntro } from "@/components/ui";

/** CAM-01 — Campaigns. */
export default function CampaignsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Communication"
        title="Campaigns that invite, never pressure."
        lede="Use benefit communications and reminders without revealing who has or has not used CereBro."
      />

      {/* Non-negotiable. The one place an administrator is most likely to reach
          for a coercive tactic is the one place the ban must be unmissable. */}
      <div className="notice warn">
        <span aria-hidden="true">!</span>
        <div>
          <b>Prohibited:</b> {CAMPAIGN_PROHIBITED}
        </div>
      </div>

      <div className="toolbar">
        <button className="btn" type="button">Create campaign</button>
        <button className="btn secondary" type="button">Export campaign summary</button>
      </div>

      <div className="table-wrap">
        <table>
          <caption>Organisation campaigns</caption>
          <thead>
            <tr>
              <th scope="col">Campaign</th>
              <th scope="col">Audience</th>
              <th scope="col">Channel</th>
              <th scope="col">Delivered</th>
              <th scope="col">Anonymous activation</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {CAMPAIGNS.map((c) => (
              <tr key={c.name}>
                <th scope="row" style={{ fontSize: "12.5px", textTransform: "none", letterSpacing: 0, background: "transparent" }}>
                  {c.name}
                </th>
                <td>{c.audience}</td>
                <td>{c.channel}</td>
                <td>{c.status === "Scheduled" ? "—" : c.delivered}</td>
                <td>{c.activation}</td>
                <td>
                  <Badge tone={c.status === "Complete" ? "good" : c.status === "Suppressed" ? "warn" : ""}>
                    {c.status}
                  </Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="spacer" />

      <div className="grid cols-2">
        <div className="card success">
          <h2>What a campaign may target</h2>
          <div className="list" style={{ marginTop: 10 }}>
            <div className="list-item">
              <div aria-hidden="true" className="item-ic sage">✓</div>
              <div><b>An eligibility group</b><div className="tiny">Everyone in the group, or nobody</div></div>
            </div>
            <div className="list-item">
              <div aria-hidden="true" className="item-ic sage">✓</div>
              <div><b>A region or entity</b><div className="tiny">Where the benefit is available</div></div>
            </div>
            <div className="list-item">
              <div aria-hidden="true" className="item-ic sage">✓</div>
              <div><b>A launch or renewal moment</b><div className="tiny">Timing, not behaviour</div></div>
            </div>
          </div>
        </div>

        <div className="card danger">
          <h2>What it may never target</h2>
          <p className="tiny" style={{ marginTop: 8 }}>
            Usage, non-usage, streaks, session counts, mood, sleep, referral
            history or any list of individuals derived from what members did
            inside the app. Those audiences cannot be built here because the
            data that would define them never reaches this portal.
          </p>
        </div>
      </div>
    </>
  );
}
