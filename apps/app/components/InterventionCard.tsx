"use client";

// The "here's something that might help, and here's why" card.
//
// Adapted from the sibling build's InterventionBanner. The part worth keeping
// is that the card **states what prompted it** — the app has always nudged,
// but never said what it noticed. The reason comes from the server already
// worded and frozen (see services/interventions.py); the client never
// recomputes or paraphrases it, so what's shown is what was recorded.
//
// Deliberately quiet: one card, dismissible, never re-appearing inside the
// rule's cooldown. It is an offer, not an alert.

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Recommendation = {
  id: string;
  rule_slug: string;
  tier: string;
  reason: string;
  action_kind: string;
  action_target: string;
};

// action_target → where it actually goes. Mirrors the widget-kind routing in
// the chat page (WIDGET_LINKS); kinds are a cross-stack contract.
const HREFS: Record<string, string> = {
  human_support: "/crisis",
  sleep_checkin: "/sleep",
  grounding: "/games",
  breathing: "/games",
  mini_journal: "/journal",
};

const COPY: Record<string, { title: string; cta: string }> = {
  human_support: { title: "Talking to someone is an option", cta: "See support" },
  "Sleep Reset": { title: "There's a 7-day sleep reset", cta: "Open the program" },
  sleep_checkin: { title: "Steadying your bedtime may help", cta: "Open sleep" },
  grounding: { title: "A grounding minute might land well", cta: "Try grounding" },
  mini_journal: { title: "Writing it down can help", cta: "Open journal" },
};

export function InterventionCard() {
  const [rec, setRec] = useState<Recommendation | null>(null);
  const [gone, setGone] = useState(false);

  useEffect(() => {
    // Failing quietly is correct: a suggestion is never load-bearing, and an
    // error banner about a missing suggestion would be worse than silence.
    api<Recommendation | null>("/interventions/active")
      .then(setRec)
      .catch(() => setRec(null));
  }, []);

  if (!rec || gone) return null;

  const href =
    rec.action_kind === "program" ? "/programs" : HREFS[rec.action_target] ?? "/home";
  const copy = COPY[rec.action_target] ?? { title: "Something that might help", cta: "Open" };
  const urgent = rec.tier === "human_support";

  async function resolve(action: "accept" | "dismiss") {
    if (action === "dismiss") setGone(true);
    try {
      await api(`/interventions/${rec!.id}/${action}`, { method: "POST" });
    } catch {
      /* the user's intent already took effect in the UI; don't block on it */
    }
  }

  return (
    <div
      className="card"
      style={{ borderColor: urgent ? "var(--warm)" : undefined, marginBottom: 16 }}
    >
      <div className="eyebrow">{urgent ? "Support" : "A suggestion"}</div>
      <h3 style={{ margin: "6px 0 4px", fontSize: 16 }}>{copy.title}</h3>
      {/* The rationale is the point of the card — it's what makes the
          suggestion auditable rather than a black box. */}
      <p className="sub" style={{ margin: 0 }}>{rec.reason}</p>
      <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
        <Link href={href} className="btn" onClick={() => resolve("accept")}>
          {copy.cta}
        </Link>
        <button type="button" className="btn ghost" onClick={() => resolve("dismiss")}>
          Not now
        </button>
      </div>
    </div>
  );
}
