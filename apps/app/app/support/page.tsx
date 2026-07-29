import Link from "next/link";
import type { Metadata } from "next";
import { CrisisLines } from "@/components/CrisisLines";
import { HUMAN_SUPPORT, crisisHref, isWebLink } from "@/lib/crisis";

// The persistent "Support" door (design system §1: crisis ≤2 taps from any
// screen, calm — never a scare button). Deliberately public and deliberately
// static: no session guard, no data fetch, no client JS, so it still renders
// when the API is down, the session has expired, or the network is slow.

export const metadata: Metadata = {
  title: "Support — CereBro",
  description: "Helplines that reach real people, any time.",
};

export default function Support() {
  return (
    <main className="support-page">
      <Link href="/home" className="onb-link support-back">← Back to CereBro</Link>

      <p className="eyebrow">You&apos;re not alone</p>
      <h1 className="page-title">Support</h1>
      <p className="support-lede">
        If things feel urgent, these numbers reach real people, 24/7. You don&apos;t need the
        right words — calling is enough.
      </p>

      <CrisisLines />

      <p className="footnote">
        These lines are for India. Anywhere else, dial your local emergency number —
        findahelpline.com lists free helplines country by country.
      </p>

      <div className="sec-head"><h2 className="serif-h">When you want a real person</h2></div>
      <p className="support-lede">
        CereBro is a companion, not a clinician. These connect you with someone who is.
      </p>
      <ul className="crisis-lines">
        {HUMAN_SUPPORT.map((s) => {
          const web = isWebLink(s.target);
          return (
            <li key={s.name}>
              <a
                className="crisis-line"
                href={crisisHref(s.target)}
                aria-label={web ? `Open ${s.name}` : `Call ${s.name}`}
                {...(web ? { target: "_blank", rel: "noreferrer" } : {})}
              >
                <span className="crisis-line-name">
                  {s.name}
                  <small>{s.detail}</small>
                </span>
                <span className="crisis-line-num">{web ? "Open" : "Call"}</span>
              </a>
            </li>
          );
        })}
      </ul>

      <div className="sec-head"><h2 className="serif-h">What CereBro can do here</h2></div>
      <div className="panel">
        <p className="sub">
          It listens, and it never blocks you — nothing you write or say is refused, and a heavy
          entry is still saved. It can&apos;t diagnose, prescribe, or handle an emergency, which is
          why this page exists.
        </p>
        <p className="sub" style={{ marginTop: 10 }}>
          You can name someone we may contact in a detected crisis — only with your consent —
          on the <Link href="/account">settings</Link> page.
        </p>
      </div>
    </main>
  );
}
