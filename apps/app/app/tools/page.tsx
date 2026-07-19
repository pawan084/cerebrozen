import Link from "next/link";

const TOOLS = [
  { href: "/tools/breathe", emoji: "🫧", t: "Breathing", s: "Box, 4-7-8, or coherent — a guided pacer with a pacing orb." },
  { href: "/tools/grounding", emoji: "🌿", t: "5-4-3-2-1 grounding", s: "Walk your senses back to the present moment." },
  { href: "/tools/reframe", emoji: "🧠", t: "Thought reframe", s: "A CBT thought record — untangle a sticky thought in four steps." },
  { href: "/tools/tipp", emoji: "🧊", t: "TIPP", s: "A DBT skill for the sharpest moments, when feelings are too big to think." },
  { href: "/programs", emoji: "🌱", t: "Programs", s: "Short guided journeys — one small change a day." },
  { href: "/coach", emoji: "💬", t: "Talk it through", s: "A live coaching session that ends with one concrete step." },
  { href: "/journal", emoji: "📓", t: "Write it down", s: "A private journal entry — only you ever see it." },
];

export default function ToolsHub() {
  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Practices</div><h1>Tools</h1></div></div>
      <p className="placeholder" style={{ maxWidth: 560, marginBottom: 22 }}>
        Quick ways to steady yourself — no session required. Reach for these any time.
      </p>
      <div className="tool-grid">
        {TOOLS.map((t) => (
          <Link key={t.href} href={t.href} className="tool-card">
            <span className="tc-emoji" aria-hidden="true">{t.emoji}</span>
            <span className="tc-t">{t.t}</span>
            <span className="tc-s">{t.s}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
