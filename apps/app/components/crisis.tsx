"use client";

/* The crisis panel.
 *
 * The engine screens every turn BEFORE any model call and, on a crisis, takes over
 * deterministically: what streams back is a scripted, zero-token safety message, not
 * coaching (`services/engine/app/graph/crisis.py`). Until now this client rendered that as
 * an ordinary chat bubble — the takeover fired, and the person saw a normal-looking reply
 * with no way to reach anyone. This is the surface that was missing.
 *
 * The numbers come from the ENGINE, for the person's own region. This client holds none:
 * the Zen reference hardcodes India's KIRAN number in exactly this place
 * (`ref/Zen/apps/app/app/(authed)/chat/page.tsx:154`), and our Android app shipped that
 * same bug until 2026-07-16. See lib/helplines.ts.
 *
 * It renders immediately from NEUTRAL and never blocks on the fetch: a person in crisis
 * must not watch a spinner where a phone number should be.
 */

import { useEffect, useState } from "react";
import { NEUTRAL, hrefFor, loadHelplines, type Helpline } from "@/lib/helplines";

export function CrisisPanel({ region }: { region: string }) {
  // Start from the neutral floor so the first paint already has something dialable.
  const [lines, setLines] = useState<Helpline[]>(NEUTRAL);

  useEffect(() => {
    let live = true;
    loadHelplines(region).then((rows) => { if (live) setLines(rows); });
    return () => { live = false; };
  }, [region]);

  return (
    // role="alert" so a screen reader announces it the moment it appears — this is the
    // one thing on the page that must interrupt.
    <aside className="crisis" role="alert" aria-label="Support is available right now">
      <p className="crisis-lede">
        It sounds like you may be going through something serious. You deserve to talk to
        someone who can help you stay safe — more than a coach can offer.
      </p>
      <ul className="crisis-lines">
        {lines.map((h) => (
          <li key={h.target}>
            <a href={hrefFor(h)} target={h.kind === "url" ? "_blank" : undefined}
               rel={h.kind === "url" ? "noreferrer" : undefined}>
              <span className="cl-name">{h.name}</span>
              <span className="cl-detail">{h.detail}</span>
              <span className="cl-target" aria-hidden="true">{h.kind === "tel" ? h.target : "Open ↗"}</span>
            </a>
          </li>
        ))}
      </ul>
      <p className="crisis-foot">
        Your coach is still here whenever you&rsquo;re ready to keep talking.
      </p>
    </aside>
  );
}
