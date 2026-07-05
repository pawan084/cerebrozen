"use client";

import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";

const PROGRAMS = [
  { meta: "7 days · Sleep", tag: "In progress", title: "Sleep Deeper", body: "A week of wind-downs to quiet a racing mind and rebuild your rest.", bg: "linear-gradient(160deg,#3a3a7a,#161240)" },
  { meta: "10 days · Calm", tag: "Day 4", title: "Ease Anxiety", body: "Gentle tools to loosen the knot of worry, one small step at a time.", bg: "linear-gradient(160deg,#8a5a6a,#3a2430)" },
  { meta: "5 days · Focus", tag: "New", title: "Find Focus", body: "Clear the mental clutter and settle into steady, unhurried attention.", bg: "linear-gradient(160deg,#2f6a6a,#12302f)" },
  { meta: "14 days · Self-kindness", tag: "New", title: "Self-Compassion", body: "Two soft weeks of learning to speak to yourself a little more kindly.", bg: "linear-gradient(160deg,#7a4a7a,#301640)" },
];

export default function Programs() {
  return (
    <>
      <AppHeader eyebrow="Programs" title="Guided journeys" />
      <div className="page-body">
        <section className="media-hero" style={{ background: "linear-gradient(120deg, rgba(90,82,201,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 85% 30%, rgba(166,139,255,0.3), transparent 45%), var(--night)" }}>
          <p className="eyebrow">Continue your journey</p>
          <h2>Ease Anxiety · Day 4 of 10</h2>
          <div className="bar" style={{ maxWidth: 380, marginTop: 4 }}><div className="bar-fill" style={{ width: "40%" }} /></div>
          <button className="pill-btn" style={{ marginTop: 18, alignSelf: "flex-start" }}><Icon.play size={14} /> Resume today's session</button>
          <span style={{ position: "absolute", top: 30, right: 40, width: 96, height: 96, borderRadius: 22, display: "grid", placeItems: "center", background: "linear-gradient(160deg,#8a7bf0,#5b52c9)", boxShadow: "0 0 40px rgba(138,123,240,.4)" }} aria-hidden="true">
          </span>
        </section>

        <div className="sec-head"><h2 className="serif-h">Guided programs</h2></div>
        <div className="program-grid">
          {PROGRAMS.map((p) => (
            <div key={p.title} className="program-card">
              <div className="program-thumb" style={{ background: p.bg }} />
              <div className="program-body">
                <div className="meta">{p.meta} <span style={{ float: "right", color: "var(--muted-2)" }}>{p.tag}</span></div>
                <h3>{p.title}</h3>
                <p>{p.body}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
