import Link from "next/link";

// Urgent support (SAF-01, graduated from /design/urgent 2026-08-12).
//
// STILL DELIBERATELY STATIC — no "use client", no data fetch, no auth gate, so
// it renders when the API is down, the user is signed out, or JavaScript never
// arrives (WEB_PARITY item 1; REDESIGN §2.3 Tele-MANAS-first on every crisis
// surface). The design mock used a useState region selector; that would have
// made *which emergency number you see* depend on JS running. Every region is
// therefore rendered in the markup instead, India first and open, the rest in a
// <details> that works without script.
//
// What graduated from the mock is the honesty the shipped page lacked: a
// resource carries a "Verified" badge only when it names a source AND the date
// it was last checked against that source. India is the only region we have
// checked. The ref/ audit flagged the opposite bug — Indian numbers badged
// "Verified" for every country — so an unchecked region says so plainly rather
// than borrowing India's badge.
//
// The directory mirrors backend services/crisis.py + Android CrisisScreen +
// iOS CrisisResources.swift — a hand-duplicated cross-stack contract. Change
// all of them together or none. No WhatsApp row: no official national
// Tele-MANAS WhatsApp exists (Android W25 finding), and a crisis surface must
// never point at a dead chat.

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

const INDIA: Region = {
  key: "in",
  label: "India",
  verified: true,
  helpline: {
    name: "Tele-MANAS — real people, 24/7",
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
};

const ELSEWHERE: Region[] = [
  {
    key: "us",
    label: "United States",
    verified: false,
    helpline: { name: "988 Suicide & Crisis Lifeline", number: "988", detail: "call or text · free, 24 hours" },
    emergency: { name: "Emergency services", number: "911", detail: "police, fire and medical emergencies" },
  },
  {
    key: "uk",
    label: "United Kingdom",
    verified: false,
    helpline: { name: "Samaritans", number: "116 123", detail: "free, 24 hours" },
    emergency: { name: "Emergency services", number: "999", detail: "police, fire and medical emergencies" },
  },
];

function tel(number: string) {
  return `tel:${number.replace(/\s/g, "")}`;
}

function ResourceRow({ r }: { r: Resource }) {
  return (
    <a
      href={tel(r.number)}
      className="card row"
      style={{
        display: "flex", justifyContent: "space-between", alignItems: "center",
        gap: 12, marginTop: 12, textDecoration: "none", color: "inherit",
      }}
    >
      <span>
        <strong style={{ display: "block" }}>{r.name}</strong>
        <span className="sub" style={{ margin: 0 }}>{r.detail}</span>
        {r.source && r.checked && (
          <span className="footnote" style={{ display: "block", marginTop: 4 }}>
            {r.source} · checked {r.checked}
          </span>
        )}
      </span>
      <strong style={{ color: "var(--cyan)", whiteSpace: "nowrap" }}>Call {r.number}</strong>
    </a>
  );
}

export const metadata = { title: "Urgent support — CereBro" };

export default function Crisis() {
  return (
    <div className="authwrap theme-night">
      <main className="authcard" style={{ maxWidth: 560 }}>
        <p className="eyebrow">You&apos;re not alone</p>
        <h1>Urgent support</h1>
        <p className="sub">
          If you&apos;re in immediate danger, please reach out now. These lines connect you to
          real people — CereBro is a companion, not a crisis service, and cannot monitor your
          safety.
        </p>

        <p className="eyebrow" style={{ marginTop: 18 }}>
          India · verified
        </p>
        {INDIA.helpline && <ResourceRow r={INDIA.helpline} />}
        {INDIA.emergency && <ResourceRow r={INDIA.emergency} />}

        <a
          href="tel:18005990019"
          className="card row"
          style={{
            display: "flex", justifyContent: "space-between", alignItems: "center",
            gap: 12, marginTop: 12, textDecoration: "none", color: "inherit",
          }}
        >
          <span>
            <strong style={{ display: "block" }}>KIRAN mental-health helpline</strong>
            <span className="sub" style={{ margin: 0 }}>24/7 support in 13 languages</span>
          </span>
          <strong style={{ color: "var(--cyan)", whiteSpace: "nowrap" }}>Call 1800-599-0019</strong>
        </a>

        {/* Other regions are present in the markup, not behind a script. */}
        <details style={{ marginTop: 18 }}>
          <summary style={{ cursor: "pointer" }}>Outside India</summary>
          <p className="footnote" style={{ marginTop: 8 }}>
            We have not verified these against an official source, so they carry no
            verification date. Please confirm the number locally if you can.
          </p>
          {ELSEWHERE.map((region) => (
            <div key={region.key} style={{ marginTop: 12 }}>
              <p className="eyebrow" style={{ margin: 0 }}>{region.label} · not verified yet</p>
              {region.helpline && <ResourceRow r={region.helpline} />}
              {region.emergency && <ResourceRow r={region.emergency} />}
            </div>
          ))}
          <a
            href="https://findahelpline.com"
            target="_blank"
            rel="noreferrer"
            className="card row"
            style={{
              display: "flex", justifyContent: "space-between", alignItems: "center",
              gap: 12, marginTop: 12, textDecoration: "none", color: "inherit",
            }}
          >
            <span>
              <strong style={{ display: "block" }}>Find a helpline</strong>
              <span className="sub" style={{ margin: 0 }}>Anywhere else — helplines worldwide</span>
            </span>
            <strong style={{ color: "var(--cyan)", whiteSpace: "nowrap" }}>findahelpline.com</strong>
          </a>
        </details>

        <p className="footnote" style={{ marginTop: 16 }}>
          Numbers open your phone&apos;s dialler — nothing is called automatically, and CereBro
          never contacts anyone on your behalf.
        </p>
        <p className="swap">
          <Link href="/">Back to CereBro</Link>
        </p>
      </main>
    </div>
  );
}
