"use client";

export default function InsightsPage() {
  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Insights</div><h1>Your patterns</h1></div></div>
      <div className="empty">
        <div className="card">
          <h3>Yours, and only yours</h3>
          <p className="placeholder">
            As you talk with your coach and check in, patterns take shape here — the themes you
            return to, the commitments you keep, how your weeks trend. Personal insight for you,
            never a report about you: your employer only ever sees anonymised, cohort-floored counts.
          </p>
          <p className="placeholder" style={{ marginTop: 12 }}>
            Your first insights appear after a session or two.
          </p>
        </div>
      </div>
    </div>
  );
}
