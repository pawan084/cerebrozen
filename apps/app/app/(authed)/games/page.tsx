"use client";

import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";

const GAMES = [
  { title: "Breathing Pacer", sub: "Follow the glow · 3 min", icon: "◎", bg: "linear-gradient(160deg,#2f6a6a,#12302f)" },
  { title: "Color Flow", sub: "Drift through hues · 5 min", icon: "✦", bg: "linear-gradient(160deg,#7a4a6a,#301630)" },
  { title: "Zen Garden", sub: "Rake the sand · open", icon: "〜", bg: "linear-gradient(160deg,#3a5a3a,#16241a)" },
];

export default function Games() {
  return (
    <>
      <AppHeader eyebrow="Calm play" title="Games to settle the mind" />
      <div className="page-body">
        <section className="media-hero" style={{ minHeight: 200, background: "linear-gradient(120deg, rgba(60,90,90,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 88% 30%, rgba(143,230,238,0.28), transparent 42%), var(--night)" }}>
          <div className="hero-orb" style={{ background: "radial-gradient(circle at 40% 36%, #fff, var(--cyan) 55%, #4fd8e0)" }} aria-hidden="true" />
          <p className="eyebrow">Featured</p>
          <h2>Bubble Pop</h2>
          <p>A soft, wordless game of popping drifting bubbles — a two-minute way to settle a busy mind.</p>
          <button className="pill-btn tinted" style={{ marginTop: 18, alignSelf: "flex-start" }}><Icon.play size={14} /> Play now</button>
        </section>

        <div className="sec-head"><h2 className="serif-h">More calm play</h2></div>
        <div className="media-grid" style={{ gridTemplateColumns: "repeat(3,1fr)" }}>
          {GAMES.map((g) => (
            <div key={g.title} className="media-card" style={{ background: g.bg, minHeight: 180 }}>
              <span style={{ fontSize: 26 }}>{g.icon}</span>
              <span className="cap"><strong>{g.title}</strong><small>{g.sub}</small></span>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
