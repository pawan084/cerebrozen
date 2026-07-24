// One-line provenance footer under a tool or content rail — the web port of
// Android Common.kt WhyThisWorks (REDESIGN F9 credibility layer). Copy is
// hardcoded per surface, hand-synced with Android strings.xml `*_why` texts.
export function WhyThisWorks({ text }: { text: string }) {
  return (
    <p className="footnote" style={{ marginTop: 10 }}>
      <strong style={{ color: "var(--cyan)" }}>Why this works · </strong>
      {text}
    </p>
  );
}
