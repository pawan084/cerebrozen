// The CereBro brand mark — an open "C" ring cradling a glowing orb, recolored to
// the warm palette (warm-lavender orb, lavender→cyan ring). Inline SVG so it
// stays crisp at any size and CSP-clean. Duplicate gradient ids across instances
// are fine (SVG resolves to the first; both render identically).

export function BrandMark({ size = 26, glow = true }: { size?: number; glow?: boolean }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="4 32 296 296"
      fill="none"
      aria-hidden="true"
      style={{ display: "block", filter: glow ? "drop-shadow(0 0 7px rgba(138,123,240,.5))" : undefined }}
    >
      <defs>
        <radialGradient id="cbOrb" cx="38%" cy="34%" r="75%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="42%" stopColor="#dfe0ff" />
          <stop offset="72%" stopColor="#8a7bf0" />
          <stop offset="100%" stopColor="#5b52c9" />
        </radialGradient>
        <linearGradient id="cbRing" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#cbb6ff" />
          <stop offset="100%" stopColor="#8fe6ee" />
        </linearGradient>
      </defs>
      <path d="M236 92 A112 112 0 1 0 236 268" stroke="url(#cbRing)" strokeWidth="32" strokeLinecap="round" />
      <ellipse cx="180" cy="180" rx="86" ry="30" transform="rotate(-27 180 180)" stroke="#bdf3f7" strokeWidth="5" opacity="0.5" />
      <circle cx="180" cy="180" r="56" fill="url(#cbOrb)" />
      <circle cx="156" cy="144" r="12" fill="#ffffff" opacity="0.38" />
    </svg>
  );
}
