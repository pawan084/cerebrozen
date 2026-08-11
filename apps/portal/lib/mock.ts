/**
 * Illustrative data for the organisation portal design surface.
 *
 * There is no organisation, sponsorship, entitlement or cohort model in the
 * CereBro backend (docs/REDESIGN_V2.md §3.3), and this app deliberately adds
 * none. Every figure below is a local constant taken from `ref/portal.html`
 * so reviewers can judge layout, hierarchy and copy. Nothing here is fetched,
 * persisted or derived from a real member.
 */

export const ORGS = ["Acme Health", "CereBro Demo University"] as const;

export const ADMIN = { name: "Ananya Kapoor", initials: "AK", role: "Benefits owner" };

/** Unread governance/safety/integration alerts behind the topbar bell. */
export const NOTIFICATIONS = [
  { id: 1, title: "AI safety evaluation due in 18 days", type: "Safety", read: false },
  { id: 2, title: "Caregiver pilot cohort remains suppressed", type: "Privacy", read: false },
  { id: 3, title: "Workday sync completed successfully", type: "Integration", read: true },
];

/* ------------------------------------------------------------- DASH-01 */

export const KPIS = [
  { value: "1,240", label: "Eligible members", delta: "+120 this quarter" },
  { value: "684", label: "Activated", delta: "55% activation" },
  { value: "412", label: "Active this month", delta: "60% of activated" },
  { value: "68%", label: "Week-4 retention", delta: "+7 pts" },
];

/** Weekly active members, eight weeks. `height` is the bar's share of the plot. */
export const WEEKLY_ACTIVE: { week: string; height: number; members: number }[] = [
  { week: "W1", height: 42, members: 211 },
  { week: "W2", height: 55, members: 276 },
  { week: "W3", height: 49, members: 246 },
  { week: "W4", height: 66, members: 331 },
  { week: "W5", height: 72, members: 361 },
  { week: "W6", height: 78, members: 391 },
  { week: "W7", height: 74, members: 371 },
  { week: "W8", height: 82, members: 412 },
];

export const PROGRAMME_FUNNEL = [
  { step: 1, label: "Invitation delivered", detail: "1,126 of 1,240 eligible members", pct: "91%", good: true },
  { step: 2, label: "Programme activated", detail: "512 members joined voluntarily", pct: "45%", good: false },
  { step: 3, label: "Week 4 retained", detail: "348 members remain active", pct: "68%", good: true },
];

export const DATA_FRESHNESS = [
  { label: "Eligibility sync", detail: "Today · 04:10 IST", status: "Success" },
  { label: "Aggregate report refresh", detail: "Today · 05:00 IST", status: "Success" },
];

export const GOVERNANCE_ALERTS = [
  { label: "Safety evaluation due", detail: "18 days remaining" },
  { label: "Small cohort suppressed", detail: "Caregiver benefit" },
];

export const LAUNCH_STEPS = [
  { key: "profile", label: "Organisation profile", done: true },
  { key: "privacy", label: "Privacy model", done: true },
  { key: "sso", label: "Administrator SSO", done: true },
  { key: "eligibility", label: "Eligibility import", done: false },
  { key: "programme", label: "Programme sponsorship", done: false },
  { key: "launch", label: "Launch communication", done: false },
];

/* -------------------------------------------------------------- MEM-01 */

export type EligibilityGroup = {
  name: string;
  eligible: string;
  invited: string;
  activated: string;
  access: string;
  badge: "good" | "warn";
  programme: string;
  tags: string[];
};

export const ELIGIBILITY_GROUPS: EligibilityGroup[] = [
  {
    name: "All India employees",
    eligible: "920", invited: "882", activated: "541",
    access: "Active", badge: "good",
    programme: "Calm Workdays", tags: ["Active"],
  },
  {
    name: "Graduate trainees",
    eligible: "180", invited: "156", activated: "84",
    access: "Active", badge: "good",
    programme: "Sleep Foundations", tags: ["Active", "Ending soon"],
  },
  {
    name: "Caregiver benefit",
    eligible: "140", invited: "88", activated: "59",
    access: "Pilot", badge: "warn",
    programme: "Steady Through Change", tags: ["Pilot"],
  },
];

export const SEAT_METRICS = [
  { value: "1,240", label: "Eligible" },
  { value: "684", label: "Activated" },
  { value: "556", label: "Unused seats", delta: "44.8% available" },
  { value: "31 Mar 2027", label: "Current term" },
];

/* -------------------------------------------------------------- COH-01 */

export type Cohort = {
  name: string;
  size: string;
  badge: "" | "good" | "warn";
  note: string;
  activated: number;
  suppressed?: boolean;
};

export const COHORTS: Cohort[] = [
  {
    name: "All India employees",
    size: "920 eligible", badge: "good",
    note: "Minimum reporting threshold: 20. Managers cannot access enrolment lists.",
    activated: 59,
  },
  {
    name: "Graduate trainees",
    size: "180 eligible", badge: "",
    note: "Assigned: Calm Workdays and Sleep Foundations.",
    activated: 47,
  },
  {
    name: "Caregiver benefit",
    size: "Suppressed", badge: "warn",
    note: "Reports combine with the wider benefits population until the group reaches 20 active members.",
    activated: 42, suppressed: true,
  },
];

/** COH-02 — threshold options, in active members. The default is 20. */
export const THRESHOLD_OPTIONS = [20, 30, 50] as const;
export const DEFAULT_THRESHOLD = 20;

export const COHORT_SOURCES = ["Workday", "CSV import", "Eligibility API"];
export const COHORT_REGIONS = ["India", "All regions"];
export const COHORT_RULES = [
  { label: "Active employment or enrolment", defaultOn: true, adds: 0 },
  { label: "Benefits-eligible status", defaultOn: false, adds: -46 },
  { label: "Voluntary programme invitation", defaultOn: false, adds: -92 },
];
/** Eligible members before any rule narrows the group. */
export const COHORT_BASE_ELIGIBLE = 184;
/** Share of eligible members who activate, used for the live preview. */
export const ACTIVATION_RATE = 0.55;

/* -------------------------------------------------------------- PRO-01 */

export type Programme = {
  name: string;
  tag: string;
  type: string;
  desc: string;
  status: string;
  progress?: number;
};

export const PROGRAMMES: Programme[] = [
  { name: "Calm Workdays", tag: "Active", type: "Stress", desc: "12 weeks · emotional regulation, boundaries and sleep support", status: "348 active members · week 4", progress: 34 },
  { name: "Sleep Foundations", tag: "Available", type: "Sleep", desc: "6 weeks · wind-down, schedule consistency and sleep education", status: "Ready to sponsor" },
  { name: "Steady Through Change", tag: "Available", type: "Stress", desc: "4 weeks · transitions, uncertainty and workload shifts", status: "Ready to sponsor" },
  { name: "Return to Work", tag: "Clinical partner", type: "Clinical", desc: "8 weeks · supported pathway with optional human referral", status: "Clinical review current" },
  { name: "Exam Season Reset", tag: "University", type: "Education", desc: "3 weeks · focus, sleep and acute-stress regulation", status: "Ready to sponsor" },
];

export const PROGRAMME_FILTERS = ["All", "Stress", "Sleep", "Clinical", "Education"];

/* -------------------------------------------------------------- CAM-01 */

export type Campaign = {
  name: string;
  audience: string;
  channel: string;
  delivered: string;
  activation: string;
  status: "Complete" | "Running" | "Suppressed" | "Scheduled";
};

export const CAMPAIGNS: Campaign[] = [
  { name: "Calm Workdays launch", audience: "All India employees", channel: "Email + portal", delivered: "882", activation: "38%", status: "Complete" },
  { name: "Sleep month", audience: "Graduate trainees", channel: "Email", delivered: "882", activation: "24%", status: "Running" },
  { name: "Caregiver reminder", audience: "Eligible caregivers", channel: "Portal", delivered: "88", activation: "Suppressed", status: "Suppressed" },
];

/* -------------------------------------------------------------- ENG-01 */

export const ENGAGEMENT_METRICS = [
  { value: "55%", label: "Activation" },
  { value: "60%", label: "Monthly active" },
  { value: "3.2", label: "Sessions per active member" },
  { value: "68%", label: "Week-4 retention", delta: "+7 pts" },
];

export const ENGAGEMENT_WEEKS: { week: string; height: number }[] = [
  { week: "W1", height: 36 }, { week: "W2", height: 48 },
  { week: "W3", height: 56 }, { week: "W4", height: 52 },
  { week: "W5", height: 66 }, { week: "W6", height: 71 },
  { week: "W7", height: 76 }, { week: "W8", height: 80 },
];

export const FEATURE_FAMILIES = [
  { name: "Calm-now practices", pct: 76 },
  { name: "Sleep support", pct: 62 },
  { name: "Talk companion", pct: 48 },
  { name: "Journal", pct: 31 },
];

/* -------------------------------------------------------------- PRI-01 */

export const ALWAYS_PRIVATE = [
  { what: "Chats and voice transcripts", rule: "No organisation access" },
  { what: "Journal entries and personal notes", rule: "Never used in sponsor reporting" },
  { what: "Mood, sleep and safety data", rule: "No individual-level access" },
  { what: "Referral identity and reason", rule: "Shared only with a provider chosen by the member" },
];

export const RETENTION_OPTIONS = ["24 months", "12 months"];

/* -------------------------------------------------------------- ROL-01 */

export type Role = {
  name: string;
  scope: string;
  /** Kept deliberately short — it is a boundary statement, not a manual. */
  can: string;
  cannot: string;
  holders: number;
};

export const ROLES: Role[] = [
  { name: "Programme admin", scope: "Programmes", can: "Sponsor, pause and schedule programmes and campaigns", cannot: "Change privacy guardrails or billing", holders: 2 },
  { name: "Benefits owner", scope: "Contract", can: "Approve sponsorship, seats and the launch plan", cannot: "Open any personal wellbeing content", holders: 1 },
  { name: "Analyst", scope: "Reporting", can: "Read aggregate reports above the privacy threshold", cannot: "Export without Privacy reviewer approval", holders: 3 },
  { name: "Technical admin", scope: "Integrations", can: "Configure SSO, HRIS sync and eligibility APIs", cannot: "Read report contents or member records", holders: 2 },
  { name: "Finance admin", scope: "Commercial", can: "View invoices, seat counts and renewal forecasts", cannot: "See programme or engagement detail", holders: 1 },
  { name: "Privacy reviewer", scope: "Governance", can: "Set thresholds, approve exports, run access reviews", cannot: "Open any personal wellbeing content", holders: 1 },
  { name: "Read-only auditor", scope: "Assurance", can: "Read configuration, policies and the audit log", cannot: "Change anything, or export member data", holders: 1 },
];

/** The capability grid every role is measured against. */
export const ROLE_CAPABILITIES: {
  capability: string;
  verdict: "Allowed" | "Approval required" | "Never";
  who: string;
}[] = [
  { capability: "Manage programmes and campaigns", verdict: "Allowed", who: "Programme admin, Benefits owner" },
  { capability: "View aggregate reports", verdict: "Allowed", who: "Analyst, Benefits owner, Privacy reviewer" },
  { capability: "Export aggregate reports", verdict: "Approval required", who: "Analyst — countersigned by Privacy reviewer" },
  { capability: "Change privacy thresholds", verdict: "Approval required", who: "Privacy reviewer only" },
  { capability: "View personal wellness content", verdict: "Never", who: "No role, no exception" },
];

export const ADMINS = [
  { name: "Ananya Kapoor", email: "ananya@acme.in", role: "Benefits owner", mfa: true, lastActive: "Today" },
  { name: "Ravi Menon", email: "ravi@acme.in", role: "Programme admin", mfa: true, lastActive: "Today" },
  { name: "Meera Shah", email: "meera@acme.in", role: "Analyst", mfa: true, lastActive: "Yesterday" },
];

/* -------------------------------------------------------------- PRE-01 */

export const ORG_MAY_RECEIVE = [
  { what: "Eligible and activated totals", detail: "No individual activity timeline" },
  { what: "Aggregate programme participation", detail: "Only above the privacy threshold" },
  { what: "Anonymous survey summaries", detail: "Optional and non-diagnostic" },
];
