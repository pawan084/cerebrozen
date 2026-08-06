"use client";

import { useState } from "react";

// Urgent support (SAF-01) — redesigned against ref/mobile.html
// `screens['SAF-01']` and ref/web.html `safety()`.
//
// Two things this screen exists to get right.
//
// First, order. Tele-MANAS 14416 comes before 112, because a mental-health
// helpline is the right first door for almost everyone who reaches this screen,
// and 112 is for immediate physical danger. The ref prototype puts emergency
// first; the ruling in REDESIGN_V2 §4 names Tele-MANAS as the lead, so that is
// what this does.
//
// Second, honesty about verification. This repo's own audit found Indian
// numbers rendered with a "Verified" badge for every country. Here a resource
// carries a badge only when it has a named source and a date it was last
// checked against that source — India is the only region that does. Every other
// region renders an explicit unverified warning above its numbers, and
// "Elsewhere" shows no number at all rather than a guessed one.
//
// Nothing on this screen is behind a subscription, a sign-in or a safety check.
//
// Mock data only — no fetch, no session. Every value below is a constant.

type Resource = {
  name: string;
  number: string;
  detail: string;
  source?: string;
  checked?: string;
};

type Region = {
  key: string;
  label: string;
  verified: boolean;
  helpline: Resource | null;
  emergency: Resource | null;
};

const REGIONS: Region[] = [
  {
    key: "in",
    label: "India",
    verified: true,
    helpline: {
      name: "Tele-MANAS",
      number: "14416",
      detail: "or 1800 891 4416 · free, 24 hours, in 20 languages",
      source: "Ministry of Health and Family Welfare, Tele-MANAS",
      checked: "5 August 2026",
    },
    emergency: {
      name: "Emergency services",
      number: "112",
      detail: "police, fire, ambulance and other emergencies",
      source: "Government of India, Emergency Response Support System",
      checked: "5 August 2026",
    },
  },
  {
    key: "us",
    label: "United States",
    verified: false,
    helpline: {
      name: "988 Suicide & Crisis Lifeline",
      number: "988",
      detail: "call or text · free, 24 hours",
    },
    emergency: { name: "Emergency services", number: "911", detail: "police, fire and medical emergencies" },
  },
  {
    key: "uk",
    label: "United Kingdom",
    verified: false,
    helpline: { name: "Samaritans", number: "116 123", detail: "free, 24 hours" },
    emergency: { name: "Emergency services", number: "999", detail: "police, fire and medical emergencies" },
  },
  {
    key: "other",
    label: "Elsewhere",
    verified: false,
    helpline: null,
    emergency: null,
  },
];

const CANNOT_CALL = [
  {
    mark: "✎",
    tone: "info",
    title: "Text or message instead",
    note: "Tele-MANAS answers on 14416 by phone; many services also take a text. Nothing is sent until you send it.",
  },
  {
    mark: "◎",
    tone: "warm",
    title: "Message someone you already trust",
    note: "Opens a message to the person you chose, with words already written if you want them. CereBro never contacts anyone on your behalf.",
  },
  {
    mark: "→",
    tone: "ok",
    title: "Move towards another person",
    note: "A different room, a neighbour, a shop that is open. Being near someone counts as a step.",
  },
  {
    mark: "▤",
    tone: "",
    title: "Open your safety plan",
    note: "What you wrote for yourself when things were steadier. Works without a connection.",
  },
];

// A call action. Only phrasing content inside the <button> — a <p> there is
// invalid HTML and React will warn about the nesting at hydration.
function CallAction({ resource, mark }: { resource: Resource; mark: string }) {
  return (
    <button type="button" className="ds-crisis">
      <span className="ds-mark danger" aria-hidden="true">
        {mark}
      </span>
      <span className="ds-crisis-copy">
        <strong>Call {resource.name}</strong>
        <span className="ds-number">{resource.number}</span>
        <small>{resource.detail}</small>
        {resource.source ? (
          <>
            <small>
              <span className="ds-badge ok">✓ Verified</span>
            </small>
            <small className="ds-source">
              Source: {resource.source}. Last checked {resource.checked}. Services change — if
              you can, confirm before you rely on it.
            </small>
          </>
        ) : (
          <small>
            <span className="ds-badge warn">Not verified yet</span>
          </small>
        )}
      </span>
    </button>
  );
}

export default function DesignUrgent() {
  const [regionKey, setRegionKey] = useState("in");
  const region = REGIONS.find((r) => r.key === regionKey) ?? REGIONS[0];

  return (
    <div className="today-wrap">
      <p className="eyebrow">Urgent support</p>
      <h1 className="today-greeting">Human help comes first.</h1>

      <div className="urgent-standing">
        <strong>This page is never locked.</strong>
        It stays available whether or not you are signed in, whether or not you pay, and whether
        or not anything has been flagged. CereBro is not an emergency service — it cannot watch
        over you, and it cannot send anyone to you.
      </div>

      <section className="ds-section" aria-labelledby="region-h">
        <div className="ds-head">
          <h2 id="region-h" className="serif-h">
            Where you are
          </h2>
          {region.verified ? (
            <span className="ds-badge ok">✓ Checked against its source</span>
          ) : (
            <span className="ds-badge warn">Not verified yet</span>
          )}
        </div>
        <div className="ds-chiprow" role="group" aria-label="Region">
          {REGIONS.map((r) => (
            <button
              key={r.key}
              type="button"
              className="ds-chip"
              aria-pressed={r.key === regionKey}
              onClick={() => setRegionKey(r.key)}
            >
              {r.label}
            </button>
          ))}
        </div>
      </section>

      {!region.verified ? (
        <div className="ds-note warn" role="status">
          <strong>Resources for {region.label} have not been verified yet.</strong>{" "}
          {region.helpline
            ? "The numbers below are shown because they may help, but nobody here has checked them against an official source. Please confirm your local emergency number before you rely on it."
            : "CereBro does not hold checked numbers for this region. Please use your local emergency number — it is worth finding it now rather than when you need it."}{" "}
          India is the only region checked so far.
        </div>
      ) : null}

      <section className="ds-section" aria-labelledby="reach-h">
        <h2 id="reach-h" className="serif-h">
          Reach a person now
        </h2>
        <p className="sub">
          The first line is for how you feel. The second is for immediate physical danger.
        </p>
        <div className="ds-list">
          {region.helpline ? <CallAction resource={region.helpline} mark="♡" /> : null}

          {region.emergency ? (
            <CallAction resource={region.emergency} mark="☎" />
          ) : (
            <div className="ds-card">
              <p className="sub">
                Use your local emergency number. If you are not sure what it is, the person
                nearest to you, a hotel desk or an operator will know it.
              </p>
            </div>
          )}
        </div>
      </section>

      <section className="ds-section" aria-labelledby="cannot-h">
        <h2 id="cannot-h" className="serif-h">
          If you cannot call
        </h2>
        <p className="sub">
          Calling is not possible for everyone, and not being able to call is not a reason to
          stop. These do the same job by another route.
        </p>
        <div className="ds-list">
          {CANNOT_CALL.map((c) => (
            <button key={c.title} type="button" className="ds-crisis secondary">
              <span className={c.tone ? `ds-mark ${c.tone}` : "ds-mark"} aria-hidden="true">
                {c.mark}
              </span>
              <span className="ds-crisis-copy">
                <strong>{c.title}</strong>
                <small>{c.note}</small>
              </span>
            </button>
          ))}
        </div>
      </section>

      <section className="ds-section" aria-labelledby="safe-h">
        <h2 id="safe-h" className="serif-h">
          I am safe for now
        </h2>
        <p className="sub">
          You do not have to be in danger to have opened this page, and you can leave it at any
          point without explaining why.
        </p>
        <div className="ds-list">
          <button type="button" className="ds-row">
            <span className="ds-mark ok" aria-hidden="true">
              ○
            </span>
            <span className="ds-row-copy">
              <strong>Stay a minute and ground with me</strong>
              <small>Four short steps, one at a time. Works without a connection.</small>
            </span>
            <span className="ds-chevron" aria-hidden="true">
              ›
            </span>
          </button>
          <button type="button" className="ds-row">
            <span className="ds-mark" aria-hidden="true">
              ←
            </span>
            <span className="ds-row-copy">
              <strong>Go back to today</strong>
              <small>Urgent support stays one tap away from every screen.</small>
            </span>
            <span className="ds-chevron" aria-hidden="true">
              ›
            </span>
          </button>
        </div>
      </section>

      <details className="today-fold">
        <summary>
          <span>What CereBro can and cannot do here</span>
          <small>Worth knowing before you need it</small>
        </summary>
        <p className="sub">
          CereBro cannot monitor you, cannot dispatch help, and cannot tell whether a service is
          open right now. It can hold the numbers, hold your safety plan and hold the words you
          wrote for a moment like this — and it can get out of the way quickly.
        </p>
        <p className="tiny">
          Opening this page tells no one. It is not sent to an employer, a university, an insurer
          or a family member, and it does not appear in any summary of your use.
        </p>
      </details>
    </div>
  );
}
