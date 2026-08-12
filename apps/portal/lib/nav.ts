/**
 * Portal information architecture.
 *
 * The prototype (`ref/portal.html`) defines 36 routes in five groups. All 36
 * are built as of 2026-08-12 — the ten that existed before, plus the remaining
 * twenty-six. `href` is therefore present on every sidebar entry; the disabled
 * state below is kept in the type because a future route may be listed before
 * it is built, and a missing item is worse than a disabled one.
 *
 * Detail routes (MEM-03, PRO-02, PRO-03, CAM-02, REF-02, PRI-02, SAF-02,
 * INT-02, ROL-02, BIL-02) are deliberately NOT sidebar entries: they are
 * reached from their parent, exactly as in the prototype. They still appear in
 * PAGE_META so the topbar names them correctly.
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
      { code: "SET-01", icon: "✓", label: "Launch checklist", href: "/setup" },
      { code: "MEM-01", icon: "◎", label: "Members & seats", href: "/members" },
      { code: "COH-01", icon: "◫", label: "Cohorts", href: "/cohorts" },
    ],
  },
  {
    title: "Programmes",
    items: [
      { code: "PRO-01", icon: "✦", label: "Programme library", href: "/programmes" },
      { code: "CAM-01", icon: "◈", label: "Campaigns", href: "/campaigns" },
      { code: "REF-01", icon: "↗", label: "Referral network", href: "/referrals" },
      { code: "PRE-01", icon: "▣", label: "Member preview", href: "/preview" },
    ],
  },
  {
    title: "Reporting",
    items: [
      { code: "ENG-01", icon: "▥", label: "Engagement", href: "/engagement" },
      { code: "OUT-01", icon: "⌁", label: "Outcomes", href: "/outcomes" },
      { code: "REP-01", icon: "⇩", label: "Reports centre", href: "/reports" },
    ],
  },
  {
    title: "Trust & governance",
    items: [
      { code: "PRI-01", icon: "⛨", label: "Privacy centre", href: "/privacy" },
      { code: "SAF-01", icon: "!", label: "Safety operations", href: "/safety" },
      { code: "EVI-01", icon: "◉", label: "Evidence library", href: "/evidence" },
      { code: "SEC-01", icon: "◆", label: "Security & compliance", href: "/security" },
      { code: "INT-01", icon: "⌘", label: "Integrations", href: "/integrations" },
      { code: "ROL-01", icon: "♙", label: "Roles & permissions", href: "/roles" },
      { code: "AUD-01", icon: "≡", label: "Audit log", href: "/audit" },
    ],
  },
  {
    title: "Organisation",
    items: [
      { code: "BIL-01", icon: "₹", label: "Billing & contract", href: "/billing" },
      { code: "ORG-01", icon: "⚙", label: "Organisation settings", href: "/settings" },
      { code: "SUP-01", icon: "?", label: "Support & status", href: "/support" },
    ],
  },
];

/** Page title + subtitle shown in the sticky topbar, keyed by pathname. */
export const PAGE_META: Record<string, { title: string; sub: string }> = {
  "/": { title: "Organisation dashboard", sub: "Acme Health · India" },
  "/setup": { title: "Launch checklist", sub: "Complete the requirements for a safe rollout" },
  "/members": { title: "Members & seats", sub: "Eligibility, invitations and licence use" },
  "/members/invite": { title: "Invite & eligibility import", sub: "Add eligible members without wellness data" },
  "/members/group": { title: "Eligibility group detail", sub: "Access dates, rules and programme assignment" },
  "/cohorts": { title: "Cohorts", sub: "Privacy-safe group configuration" },
  "/cohorts/new": { title: "Cohort builder", sub: "Create a reporting-safe eligibility group" },
  "/programmes": { title: "Programme library", sub: "Curate sponsored member experiences" },
  "/programmes/detail": { title: "Programme detail", sub: "Programme configuration and aggregate health" },
  "/programmes/pathway": { title: "Pathway builder", sub: "Build from approved CereBro modules" },
  "/campaigns": { title: "Campaigns", sub: "Invite and educate without pressure" },
  "/campaigns/new": { title: "Campaign builder", sub: "Audience, content, preview and schedule" },
  "/referrals": { title: "Referral network", sub: "Consent-based access to human support" },
  "/referrals/provider": { title: "Provider detail", sub: "Verification, regions and member handoff" },
  "/preview": { title: "Member experience preview", sub: "See exactly what sponsored members receive" },
  "/engagement": { title: "Engagement analytics", sub: "Anonymous group participation only" },
  "/outcomes": { title: "Outcome reporting", sub: "Aggregate, consented and non-diagnostic" },
  "/reports": { title: "Reports centre", sub: "Generate privacy-safe executive exports" },
  "/privacy": { title: "Privacy centre", sub: "Data separation and reporting controls" },
  "/privacy/data-map": { title: "Data map & retention", sub: "What moves, where it lives and how long it stays" },
  "/safety": { title: "Safety operations", sub: "Resource verification and escalation testing" },
  "/safety/runbook": { title: "Safety runbook", sub: "Operational ownership without employer surveillance" },
  "/evidence": { title: "Evidence library", sub: "Clinical review and content governance" },
  "/security": { title: "Security & compliance", sub: "Controls, documents and review status" },
  "/integrations": { title: "Integrations", sub: "SSO, HRIS, benefits and provider connections" },
  "/integrations/detail": { title: "Integration detail", sub: "Configuration, logs and data boundary" },
  "/roles": { title: "Roles & permissions", sub: "Least-privilege administration" },
  "/roles/admins": { title: "Administrator access", sub: "Invite, edit and review access" },
  "/audit": { title: "Audit log", sub: "Trace every administrative action" },
  "/billing": { title: "Billing & contract", sub: "Seats, invoices and renewal" },
  "/billing/contract": { title: "Contract & invoices", sub: "Commercial documents and change requests" },
  "/settings": { title: "Organisation settings", sub: "Profile, branding, regions and contacts" },
  "/support": { title: "Support & status", sub: "Help, incidents and implementation support" },
  "/notifications": { title: "Notifications", sub: "Governance, safety and integration alerts" },
  "/signin": { title: "Administrator sign in", sub: "Secure access to the organisation portal" },
  "/signin/verify": { title: "Verify your identity", sub: "Multi-factor authentication" },
};
