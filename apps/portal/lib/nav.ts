/**
 * Portal information architecture.
 *
 * The prototype (`ref/portal.html`) defines 36 routes in five groups. This
 * design surface builds ten of them. The rest are still listed — with no
 * `href` — so the shape of the portal is reviewable and so nobody mistakes
 * a missing item for a deleted one. Unbuilt entries render as disabled.
 */
export type NavItem = {
  /** Prototype route code, e.g. "DASH-01" — kept for traceability. */
  code: string;
  icon: string;
  label: string;
  /** Present only when the route is built. */
  href?: string;
};

export type NavGroup = { title: string; items: NavItem[] };

export const NAV: NavGroup[] = [
  {
    title: "Overview",
    items: [
      { code: "DASH-01", icon: "⌂", label: "Dashboard", href: "/" },
      { code: "SET-01", icon: "✓", label: "Launch checklist" },
      { code: "MEM-01", icon: "◎", label: "Members & seats", href: "/members" },
      { code: "COH-01", icon: "◫", label: "Cohorts", href: "/cohorts" },
    ],
  },
  {
    title: "Programmes",
    items: [
      { code: "PRO-01", icon: "✦", label: "Programme library", href: "/programmes" },
      { code: "CAM-01", icon: "◈", label: "Campaigns", href: "/campaigns" },
      { code: "REF-01", icon: "↗", label: "Referral network" },
      { code: "PRE-01", icon: "▣", label: "Member preview", href: "/preview" },
    ],
  },
  {
    title: "Reporting",
    items: [
      { code: "ENG-01", icon: "▥", label: "Engagement", href: "/engagement" },
      { code: "OUT-01", icon: "⌁", label: "Outcomes" },
      { code: "REP-01", icon: "⇩", label: "Reports centre" },
    ],
  },
  {
    title: "Trust & governance",
    items: [
      { code: "PRI-01", icon: "⛨", label: "Privacy centre", href: "/privacy" },
      { code: "SAF-01", icon: "!", label: "Safety operations" },
      { code: "EVI-01", icon: "◉", label: "Evidence library" },
      { code: "SEC-01", icon: "◆", label: "Security & compliance" },
      { code: "INT-01", icon: "⌘", label: "Integrations" },
      { code: "ROL-01", icon: "♙", label: "Roles & permissions", href: "/roles" },
      { code: "AUD-01", icon: "≡", label: "Audit log" },
    ],
  },
  {
    title: "Organisation",
    items: [
      { code: "BIL-01", icon: "₹", label: "Billing & contract" },
      { code: "ORG-01", icon: "⚙", label: "Organisation settings" },
      { code: "SUP-01", icon: "?", label: "Support & status" },
    ],
  },
];

/** Page title + subtitle shown in the sticky topbar, keyed by pathname. */
export const PAGE_META: Record<string, { title: string; sub: string }> = {
  "/": { title: "Organisation dashboard", sub: "Acme Health · India" },
  "/members": { title: "Members & seats", sub: "Eligibility, invitations and licence use" },
  "/cohorts": { title: "Cohorts", sub: "Privacy-safe group configuration" },
  "/cohorts/new": { title: "Cohort builder", sub: "Create a reporting-safe eligibility group" },
  "/programmes": { title: "Programme library", sub: "Curate sponsored member experiences" },
  "/campaigns": { title: "Campaigns", sub: "Invite and educate without pressure" },
  "/engagement": { title: "Engagement analytics", sub: "Anonymous group participation only" },
  "/privacy": { title: "Privacy centre", sub: "Data separation and reporting controls" },
  "/roles": { title: "Roles & permissions", sub: "Least-privilege administration" },
  "/preview": { title: "Member experience preview", sub: "See exactly what sponsored members receive" },
};
