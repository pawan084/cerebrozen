// Small inline glyphs for the feature grid, drawn the same way as BrandMark:
// CSP-clean inline SVG, crisp at any size. They paint with `currentColor`, so the
// tile sets the hue from a palette token (`.bento-cell .icon { color: … }`) and no
// raw hex lands in a component. Decorative only — the card heading carries the
// meaning, so callers mark the tile aria-hidden.

// An open brand ring cradling a heart. Replaces the 🆘 emoji, whose siren-red
// reads as alarm; a crisis door should look like a calm, steady offer of help.
export function SupportGlyph({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" style={{ display: "block" }}>
      <path
        d="M17.4 5.6 A8.4 8.4 0 1 0 17.4 18.4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        opacity="0.55"
      />
      <path
        d="M12 16.1c-2.5-1.7-4.2-3.2-4.2-5.1 0-1.3 1.05-2.25 2.3-2.25.85 0 1.5.42 1.9 1.05.4-.63 1.05-1.05 1.9-1.05 1.25 0 2.3.95 2.3 2.25 0 1.9-1.7 3.4-4.2 5.1z"
        fill="currentColor"
      />
    </svg>
  );
}

// A presence ring: the arc fills with the days you showed up (per docs/REDESIGN
// §3.6 — the track never disappears, so a missed day dims rather than resets).
export function PresenceGlyph({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" style={{ display: "block" }}>
      <circle cx="12" cy="12" r="8.4" stroke="currentColor" strokeWidth="1.7" opacity="0.28" />
      <path
        d="M12 3.6 A8.4 8.4 0 0 1 18.4 17.4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <circle cx="12" cy="12" r="2.6" fill="currentColor" opacity="0.9" />
    </svg>
  );
}
