// Inline SVG brand mark + nav icons for the admin (CSP-clean, no icon font).

export function BrandMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="4 32 296 296" fill="none" aria-hidden="true"
      style={{ display: "block", filter: "drop-shadow(0 0 7px rgba(138,123,240,.5))" }}>
      <defs>
        <radialGradient id="aOrb" cx="38%" cy="34%" r="75%">
          <stop offset="0%" stopColor="#fff" /><stop offset="42%" stopColor="#dfe0ff" />
          <stop offset="72%" stopColor="#8a7bf0" /><stop offset="100%" stopColor="#5b52c9" />
        </radialGradient>
        <linearGradient id="aRing" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#cbb6ff" /><stop offset="100%" stopColor="#8fe6ee" />
        </linearGradient>
      </defs>
      <path d="M236 92 A112 112 0 1 0 236 268" stroke="url(#aRing)" strokeWidth="32" strokeLinecap="round" />
      <ellipse cx="180" cy="180" rx="86" ry="30" transform="rotate(-27 180 180)" stroke="#bdf3f7" strokeWidth="5" opacity="0.5" />
      <circle cx="180" cy="180" r="56" fill="url(#aOrb)" />
      <circle cx="156" cy="144" r="12" fill="#fff" opacity="0.38" />
    </svg>
  );
}

const s = (d: React.ReactNode) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{d}</svg>
);

export const Icon: Record<string, () => JSX.Element> = {
  overview: () => s(<><rect x="3" y="3" width="8" height="8" rx="1.5" /><rect x="13" y="3" width="8" height="8" rx="1.5" /><rect x="3" y="13" width="8" height="8" rx="1.5" /><rect x="13" y="13" width="8" height="8" rx="1.5" /></>),
  analytics: () => s(<path d="M4 19V5m0 14h16M8 16V9m4 7v-4m4 4V7" />),
  users: () => s(<><circle cx="9" cy="8" r="3" /><path d="M3 20c1-3.3 3.3-5 6-5s5 1.7 6 5" /><path d="M16 5.5a3 3 0 010 5.6M21 20c-.5-2-1.6-3.4-3.2-4.2" /></>),
  content: () => s(<><path d="M9 18V6l10-2v12" /><circle cx="6.5" cy="18" r="2.5" /><circle cx="16.5" cy="16" r="2.5" /></>),
  nudges: () => s(<path d="M12 3l1.9 5.4L19 10l-5.1 1.6L12 17l-1.9-5.4L5 10l5.1-1.6z" />),
  prompts: () => s(<><rect x="3" y="4" width="18" height="14" rx="2" /><path d="M7 9l3 3-3 3M12.5 15H17" /></>),
  safety: () => s(<path d="M12 21s7-3.6 7-9V5l-7-2-7 2v7c0 5.4 7 9 7 9z" />),
  waitlist: () => s(<><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M4 7l8 6 8-6" /></>),
  signout: () => s(<><path d="M14 4h4a1 1 0 011 1v14a1 1 0 01-1 1h-4" /><path d="M10 12H3m0 0l3-3m-3 3l3 3" /></>),
  search: () => s(<><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.2-3.2" /></>),
};
