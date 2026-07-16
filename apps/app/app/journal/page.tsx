"use client";

export default function JournalPage() {
  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Journal</div><h1>Your journal</h1></div></div>
      <div className="empty">
        <div className="card">
          <h3>Private by design</h3>
          <p className="placeholder">
            A space to write between sessions — a thought to hold onto, a reflection after a
            hard conversation. Entries are yours alone: <strong>counts, never content</strong> —
            nothing here is ever exposed to your employer or any admin surface.
          </p>
          <p className="placeholder" style={{ marginTop: 12 }}>
            Writing lands here once journaling is enabled for your workspace (it's consent-gated).
          </p>
        </div>
      </div>
    </div>
  );
}
