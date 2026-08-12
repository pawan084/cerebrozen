import Link from "next/link";
import { ADMINS } from "@/lib/mock";
import { ROLE_BOUNDARY } from "@/lib/copy";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/** ROL-02 — Administrator access. */
export default function AdminAccessPage() {
  return (
    <>
      <PageIntro
        eyebrow="Administrator access"
        title="Invite, edit and review access."
        lede="Who holds an administrator account, which role they hold, and when they were last attested."
      />

      <div className="card">
        <div className="list">
          {ADMINS.map((a) => (
            <div className="list-item" key={a.email}>
              <div className="grow">
                <b>{a.name}</b>
                <div className="tiny">
                  {a.email} · {a.role}
                </div>
              </div>
              <Badge tone={a.mfa ? "good" : "danger"}>{a.mfa ? "MFA on" : "MFA missing"}</Badge>
              <span className="tiny">{a.lastActive}</span>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Quarterly access review</h2>
          <p className="tiny">
            Every administrator must be attested by a Benefits owner or Privacy reviewer. An
            account that is not attested loses access rather than being grandfathered.
          </p>
        </div>
        <div className="card tint">
          <h2>The boundary</h2>
          <p className="tiny">{ROLE_BOUNDARY}</p>
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="≡">
        Adding, changing or removing an administrator is written to the audit log with the
        actor and the time.
      </Notice>

      <div className="toolbar">
        <Link className="btn secondary" href="/roles">Back to roles</Link>
        <Link className="btn secondary" href="/audit">Open audit log</Link>
      </div>
    </>
  );
}
