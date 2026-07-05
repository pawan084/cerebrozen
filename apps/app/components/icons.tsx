// Small inline SVG icon set for the web app shell + screens. Inline (not an
// icon font / remote sprite) keeps everything CSP-clean and same-origin.

type P = { size?: number; className?: string };
const svg = (size: number, className: string | undefined, d: React.ReactNode) => (
  <svg
    width={size} height={size} viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round"
    className={className} aria-hidden="true"
  >
    {d}
  </svg>
);

export const Icon = {
  home: ({ size = 20, className }: P) => svg(size, className, <path d="M4 11l8-6 8 6v8a1 1 0 01-1 1h-4v-6h-6v6H5a1 1 0 01-1-1z" />),
  talk: ({ size = 20, className }: P) => svg(size, className, <path d="M4 5h16v11H8l-4 3z" />),
  sleep: ({ size = 20, className }: P) => svg(size, className, <path d="M20 14a8 8 0 01-10-10 8 8 0 1010 10z" />),
  journal: ({ size = 20, className }: P) => svg(size, className, <><path d="M6 4h9l3 3v13H6z" /><path d="M9 10h6M9 14h4" /></>),
  insights: ({ size = 20, className }: P) => svg(size, className, <path d="M5 19V9M12 19V5M19 19v-7" />),
  plan: ({ size = 20, className }: P) => svg(size, className, <><path d="M4 6h16M4 12h16M4 18h10" /><circle cx="19" cy="18" r="2.4" /></>),
  library: ({ size = 20, className }: P) => svg(size, className, <><rect x="4" y="4" width="16" height="16" rx="2" /><path d="M9 4v16M14 9l3 3-3 3" /></>),
  account: ({ size = 20, className }: P) => svg(size, className, <><circle cx="12" cy="8" r="3.4" /><path d="M5 20c1.2-3.4 4-5 7-5s5.8 1.6 7 5" /></>),
  signout: ({ size = 20, className }: P) => svg(size, className, <><path d="M14 4h4a1 1 0 011 1v14a1 1 0 01-1 1h-4" /><path d="M10 12H3m0 0l3-3m-3 3l3 3" /></>),
  search: ({ size = 20, className }: P) => svg(size, className, <><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.2-3.2" /></>),
  bell: ({ size = 20, className }: P) => svg(size, className, <path d="M6 9a6 6 0 1112 0c0 5 2 6 2 6H4s2-1 2-6zM10 20a2 2 0 004 0" />),
  wind: ({ size = 20, className }: P) => svg(size, className, <path d="M3 8h11a2.5 2.5 0 10-2.5-2.5M3 16h15a2.5 2.5 0 11-2.5 2.5M3 12h9" />),
  book: ({ size = 20, className }: P) => svg(size, className, <path d="M4 5a2 2 0 012-2h11v16H6a2 2 0 00-2 2z" />),
  play: ({ size = 20, className }: P) => svg(size, className, <path d="M8 5l11 7-11 7z" fill="currentColor" stroke="none" />),
  chevron: ({ size = 20, className }: P) => svg(size, className, <path d="M9 6l6 6-6 6" />),
  spark: ({ size = 20, className }: P) => svg(size, className, <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z" />),
  games: ({ size = 20, className }: P) => svg(size, className, <><rect x="3" y="8" width="18" height="9" rx="4.5" /><path d="M8 12.5h2.4M9.2 11.3v2.4" /><circle cx="15.4" cy="12" r=".5" fill="currentColor" /><circle cx="17" cy="13.4" r=".5" fill="currentColor" /></>),
  settings: ({ size = 20, className }: P) => svg(size, className, <><circle cx="12" cy="12" r="3.2" /><path d="M12 3v2.2M12 18.8V21M4.2 7l1.9 1.1M17.9 15.9l1.9 1.1M4.2 17l1.9-1.1M17.9 8.1l1.9-1.1" /></>),
  bellDot: ({ size = 20, className }: P) => svg(size, className, <><path d="M6 9a6 6 0 1112 0c0 5 2 6 2 6H4s2-1 2-6zM10 20a2 2 0 004 0" /><circle cx="18" cy="5" r="2.6" fill="var(--warm)" stroke="none" /></>),
};

/** The CereBro brand mark — open "C" ring cradling a glowing orb (warm palette). */
export function BrandMark({ size = 26 }: { size?: number }) {
  return (
    <svg
      width={size} height={size} viewBox="4 32 296 296" fill="none" aria-hidden="true"
      style={{ display: "block", flex: `0 0 ${size}px`, filter: "drop-shadow(0 0 7px rgba(138,123,240,.5))" }}
    >
      <defs>
        <radialGradient id="cbMarkOrb" cx="38%" cy="34%" r="75%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="42%" stopColor="#dfe0ff" />
          <stop offset="72%" stopColor="#8a7bf0" />
          <stop offset="100%" stopColor="#5b52c9" />
        </radialGradient>
        <linearGradient id="cbMarkRing" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#cbb6ff" />
          <stop offset="100%" stopColor="#8fe6ee" />
        </linearGradient>
      </defs>
      <path d="M236 92 A112 112 0 1 0 236 268" stroke="url(#cbMarkRing)" strokeWidth="32" strokeLinecap="round" />
      <ellipse cx="180" cy="180" rx="86" ry="30" transform="rotate(-27 180 180)" stroke="#bdf3f7" strokeWidth="5" opacity="0.5" />
      <circle cx="180" cy="180" r="56" fill="url(#cbMarkOrb)" />
      <circle cx="156" cy="144" r="12" fill="#ffffff" opacity="0.38" />
    </svg>
  );
}
