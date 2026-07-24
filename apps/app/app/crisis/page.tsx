import Link from "next/link";

// Urgent support — deliberately static: no client JS, no data fetches, no auth
// gate, so it renders even when the API is down or the user is signed out
// (WEB_PARITY item 1; REDESIGN §2.3 Tele-MANAS-first on every crisis surface).
// The directory mirrors backend services/crisis.py IN + Android CrisisScreen —
// change all together or none. No WhatsApp row: no official national
// Tele-MANAS WhatsApp exists (Android W25 finding) — a crisis surface must
// never point at a dead chat.
const LINES = [
  { name: "Tele-MANAS — real people, 24/7", detail: "Free government mental-health line", target: "tel:14416", label: "Call 14416" },
  { name: "Emergency services", detail: "Immediate danger, anywhere in India", target: "tel:112", label: "Call 112" },
  { name: "KIRAN mental-health helpline", detail: "24/7 support in 13 languages", target: "tel:18005990019", label: "Call 1800-599-0019" },
  { name: "Find a helpline", detail: "Outside India — helplines worldwide", target: "https://findahelpline.com/in", label: "findahelpline.com" },
];

export const metadata = { title: "Urgent support — CereBro" };

export default function Crisis() {
  return (
    <div className="authwrap theme-night">
      <main className="authcard" style={{ maxWidth: 560 }}>
        <p className="eyebrow">You&apos;re not alone</p>
        <h1>Urgent support</h1>
        <p className="sub">
          If you&apos;re in immediate danger, please reach out now. These lines connect you to
          real people — CereBro is a companion, not a crisis service.
        </p>
        {LINES.map((l) => (
          <a
            key={l.name}
            href={l.target}
            target={l.target.startsWith("http") ? "_blank" : undefined}
            rel={l.target.startsWith("http") ? "noreferrer" : undefined}
            className="card row"
            style={{
              display: "flex", justifyContent: "space-between", alignItems: "center",
              gap: 12, marginTop: 12, textDecoration: "none", color: "inherit",
            }}
          >
            <span>
              <strong style={{ display: "block" }}>{l.name}</strong>
              <span className="sub" style={{ margin: 0 }}>{l.detail}</span>
            </span>
            <strong style={{ color: "var(--cyan)", whiteSpace: "nowrap" }}>{l.label}</strong>
          </a>
        ))}
        <p className="footnote" style={{ marginTop: 16 }}>
          Numbers open your phone&apos;s dialler — nothing is called automatically.
        </p>
        <p className="swap">
          <Link href="/">Back to CereBro</Link>
        </p>
      </main>
    </div>
  );
}
