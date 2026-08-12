import Link from "next/link";
import { Notice } from "@/components/ui";

/**
 * AUTH-01 — Administrator sign in.
 *
 * Renders the intended sign-in surface and authenticates NOBODY. There is no
 * session, no cookie, no redirect and no "demo workspace" button, because this
 * portal has no backend auth: a control that appeared to sign someone in would
 * imply a gate that does not exist, and a fake gate is worse than an obvious
 * absence. The prototype carried the same warning; this keeps it and removes
 * the simulated actions rather than porting them.
 *
 * The Caddy block for portal.cerebrozen.in stays commented out until this
 * screen is wired to a real identity provider (deploy/Caddyfile).
 */
export const metadata = { title: "Administrator sign in · CereBro for Organisations" };

export default function SignInPage() {
  return (
    <>
      <div className="eyebrow">Administrator access</div>
      <h1>Govern wellbeing programmes without seeing personal wellbeing data.</h1>
      <p className="lede">
        Secure access for benefits, programme, technical, privacy, finance and audit
        administrators.
      </p>

      <Notice tone="danger" icon="!">
        <b>This screen does not sign anyone in.</b>
        <br />
        It is the layout for administrator access; no identity provider, session or
        authorisation check exists behind it yet. Nothing on this page grants access to
        anything, and the portal is not published on a public host until it does.
      </Notice>

      <div className="grid cols-2">
        <div className="card">
          <h2>Sign in</h2>
          <p className="tiny">Use your organisation administrator account.</p>
          <label style={{ display: "block", marginTop: 20 }}>
            <span className="label">Work email</span>
            <input className="field" type="email" placeholder="you@organisation.com" disabled />
          </label>
          <div className="toolbar">
            <span className="btn secondary" aria-disabled="true">Continue with SSO</span>
            <span className="btn secondary" aria-disabled="true">Email verification code</span>
          </div>
          <p className="tiny" style={{ marginTop: 12 }}>
            Both controls are inert. Production requires a real identity provider and
            backend session management.
          </p>
        </div>

        <div className="card tint">
          <h2>What an administrator can reach</h2>
          <p className="tiny">
            Every administrator action is role-restricted and recorded. No role, however
            senior, reaches a member’s chat, journal, mood history, sleep data or safety plan.
          </p>
          <div className="toolbar">
            <Link className="btn secondary" href="/roles">Roles &amp; permissions</Link>
            <Link className="btn secondary" href="/privacy">Privacy centre</Link>
          </div>
        </div>
      </div>
    </>
  );
}
