import { ADMINS, ROLES, ROLE_CAPABILITIES } from "@/lib/mock";
import { ROLE_BOUNDARY } from "@/lib/copy";
import { Badge, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** ROL-01 — Roles & permissions. */
export default function RolesPage() {
  return (
    <>
      <PageIntro
        eyebrow="Administrative access"
        title="Roles and least privilege."
        lede="Separate contract, programme, reporting and technical responsibilities. No role can access individual wellness content."
      />

      <SampleData />

      <div className="toolbar">
        <button className="btn" type="button">Invite administrator</button>
        <button className="btn secondary" type="button">Start quarterly access review</button>
      </div>

      <div className="table-wrap">
        <table>
          <caption>The seven organisation roles</caption>
          <thead>
            <tr>
              <th scope="col">Role</th>
              <th scope="col">Scope</th>
              <th scope="col">Can</th>
              <th scope="col">Cannot</th>
              <th scope="col">Holders</th>
            </tr>
          </thead>
          <tbody>
            {ROLES.map((r) => (
              <tr key={r.name}>
                <th scope="row" style={{ fontSize: "12.5px", textTransform: "none", letterSpacing: 0, background: "transparent" }}>
                  {r.name}
                </th>
                <td><Badge>{r.scope}</Badge></td>
                <td>{r.can}</td>
                <td>{r.cannot}</td>
                <td>{r.holders}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Capability grid</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {ROLE_CAPABILITIES.map((c) => (
              <div className="list-item" key={c.capability}>
                <div className="grow">
                  <b>{c.capability}</b>
                  <div className="tiny">{c.who}</div>
                </div>
                <Badge
                  tone={
                    c.verdict === "Allowed" ? "good" : c.verdict === "Never" ? "danger" : "warn"
                  }
                >
                  {c.verdict}
                </Badge>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2>Current administrators</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {ADMINS.map((a) => (
              <div className="list-item" key={a.email}>
                <div className="grow">
                  <b>{a.name}</b>
                  <div className="tiny">{a.email} · {a.role}</div>
                </div>
                <Badge tone="good">{a.mfa ? "MFA enabled" : "MFA required"}</Badge>
              </div>
            ))}
          </div>
        </div>
      </div>

      <Spacer />

      <div className="card success">
        <b>Role boundary</b>
        <p className="tiny" style={{ marginTop: 6 }}>{ROLE_BOUNDARY}</p>
      </div>
    </>
  );
}
