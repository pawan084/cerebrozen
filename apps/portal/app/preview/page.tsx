import Link from "next/link";
import { ORG_MAY_RECEIVE } from "@/lib/mock";
import { NEVER_AVAILABLE } from "@/lib/copy";
import { PageIntro } from "@/components/ui";

/** PRE-01 — Member experience preview. */
export default function PreviewPage() {
  return (
    <>
      <PageIntro
        eyebrow="Member-facing experience"
        title="Preview the privacy boundary before launch."
        lede="This view shows how sponsored access appears inside the CereBro member app. Organisation administrators cannot enter the member’s private wellbeing areas."
      />

      <div className="preview-shell">
        {/* A static rendering of the member's first screen. Nothing here is
            interactive: the point is what an administrator is allowed to see,
            and this is the whole of it. */}
        <div className="phone">
          <div className="phone-bar">
            <span>10:08</span>
            <span>5G · 86%</span>
          </div>
          <div className="phone-hero">
            <div className="eyebrow">Sponsored by Acme Health</div>
            <h2>Your Calm Workdays programme.</h2>
            <p className="tiny" style={{ marginTop: 6 }}>
              Premium access is covered until 31 March 2027.
            </p>
            <button className="btn small" type="button" disabled style={{ marginTop: 12 }}>
              Continue programme
            </button>
          </div>
          <div className="phone-card">
            <b>Your private space stays private</b>
            <p className="tiny" style={{ marginTop: 6 }}>
              Acme Health cannot see your chats, journal, moods, sleep records,
              safety plan, trusted contacts or individual activity.
            </p>
          </div>
          <div className="phone-card">
            <b>Included</b>
            <p className="tiny" style={{ marginTop: 6 }}>
              Weekly pathway · unlimited companion · sleep support · optional
              EAP referral
            </p>
          </div>
          <div className="phone-nav" aria-hidden="true">
            <span>Today</span><span>Explore</span><span>Talk</span>
            <span>Journal</span><span>You</span>
          </div>
        </div>

        <div>
          <div className="card">
            <div className="eyebrow">What a member sees</div>
            <h2>Sponsorship, stated plainly.</h2>
            <p className="tiny" style={{ marginTop: 8 }}>
              The member is told who is paying, what is covered and until when —
              on the first screen, not in a settings page. The same screen states
              what the sponsor cannot see, in the member’s own words rather than
              ours.
            </p>
            <div className="toolbar" style={{ marginBottom: 0 }}>
              <Link className="btn" href="/privacy">Review privacy controls</Link>
              <Link className="btn secondary" href="/campaigns">Review campaigns</Link>
            </div>
          </div>
        </div>
      </div>

      <div className="spacer" />

      {/* The disclosure: two columns, side by side, so the boundary is read as
          one statement rather than two claims on separate screens. */}
      <div className="grid cols-2">
        <div className="card success">
          <h2>What the organisation may receive</h2>
          <div className="list" style={{ marginTop: 10 }}>
            {ORG_MAY_RECEIVE.map((row) => (
              <div className="list-item" key={row.what}>
                <div aria-hidden="true" className="item-ic sage">✓</div>
                <div>
                  <b>{row.what}</b>
                  <div className="tiny">{row.detail}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="card danger">
          <h2>Never available</h2>
          <p className="tiny" style={{ marginTop: 10 }}>{NEVER_AVAILABLE}</p>
          <p className="tiny" style={{ marginTop: 12 }}>
            Not on request, not under contract, not for an investigation, not in
            aggregate. This list is the boundary the rest of the portal is built
            around.
          </p>
        </div>
      </div>
    </>
  );
}
