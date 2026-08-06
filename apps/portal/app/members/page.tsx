import Link from "next/link";
import { ELIGIBILITY_GROUPS, SEAT_METRICS } from "@/lib/mock";
import { Badge, Metric, PageIntro, PrivacyWall } from "@/components/ui";

/** MEM-01 — Members & seats. */
export default function MembersPage() {
  return (
    <>
      <PageIntro
        eyebrow="Access management"
        title="Members and sponsored seats."
        lede="Manage eligibility and invitations without exposing who uses individual wellbeing features."
      />

      <PrivacyWall />

      <div className="toolbar">
        <Link className="btn" href="/cohorts/new">Invite or import members</Link>
        <button className="btn secondary" type="button">Export eligibility list</button>
        <Link className="btn secondary" href="/roles">Manage administrators</Link>
      </div>

      <div className="grid cols-4">
        {SEAT_METRICS.map((m) => (
          <Metric key={m.label} value={m.value} label={m.label} delta={m.delta} />
        ))}
      </div>

      <div className="toolbar">
        {["All", "Active", "Pilot", "Ending soon"].map((f) => (
          <span key={f} className={f === "All" ? "filter active" : "filter"}>
            {f}
          </span>
        ))}
      </div>

      <div className="table-wrap">
        <table>
          <caption>Eligibility groups</caption>
          <thead>
            <tr>
              <th scope="col">Group</th>
              <th scope="col">Eligible</th>
              <th scope="col">Invited</th>
              <th scope="col">Activated</th>
              <th scope="col">Access</th>
              <th scope="col">Programme</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {ELIGIBILITY_GROUPS.map((g) => (
              <tr key={g.name}>
                <th scope="row" style={{ fontSize: "12.5px", textTransform: "none", letterSpacing: 0, background: "transparent" }}>
                  {g.name}
                </th>
                <td>{g.eligible}</td>
                <td>{g.invited}</td>
                <td>{g.activated}</td>
                <td><Badge tone={g.badge}>{g.access}</Badge></td>
                <td>{g.programme}</td>
                <td>
                  <Link className="row-link" href="/cohorts">Open</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="spacer" />

      <div className="card tint">
        <b>Counts, not people.</b>
        <p className="tiny" style={{ marginTop: 4 }}>
          A group row shows how many members are eligible, invited and activated.
          It never lists who they are, and it never reports what any of them did
          inside the app.
        </p>
      </div>
    </>
  );
}
