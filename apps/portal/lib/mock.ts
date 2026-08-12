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

/* ================================================================
   The 26 remaining routes, built out 2026-08-12. Same rule as
   everything above: fixed sample data, no backing model. Nothing
   here is fetched, and no value belongs to a real organisation.
   ================================================================ */

/* -------------------------------------------------------------- SET-01 */

/** Launch requirements, each pointing at the route that completes it. */
export const SETUP_REQUIREMENTS = [
  { key: "profile", label: "Organisation profile", detail: "Legal entity, programme owner and support contacts", href: "/settings", done: true },
  { key: "privacy", label: "Privacy and reporting model", detail: "Threshold, suppression and data-processing terms", href: "/privacy", done: true },
  { key: "sso", label: "Administrator SSO and MFA", detail: "Secure administrator access", href: "/integrations", done: true },
  { key: "eligibility", label: "Eligibility connection", detail: "CSV, HRIS or API", href: "/members/invite", done: false },
  { key: "programme", label: "Sponsored programme", detail: "Choose cohort, dates and benefits", href: "/programmes", done: false },
  { key: "launch", label: "Member launch communication", detail: "Privacy-safe invitation and support route", href: "/campaigns/new", done: false },
];

/* -------------------------------------------------------------- MEM-03 */

export const GROUP_DETAIL = {
  name: "All India employees",
  metrics: [
    { value: "920", label: "Eligible" },
    { value: "882", label: "Invited" },
    { value: "541", label: "Activated" },
    { value: "31 Mar 2027", label: "Access ends" },
  ],
  rules: [
    { name: "Employment region", detail: "India", badge: "Required", tone: "" as const },
    { name: "Worker status", detail: "Active employees only", badge: "Required", tone: "" as const },
    { name: "Access lifecycle", detail: "Nightly Workday sync", badge: "Automated", tone: "good" as const },
  ],
  benefits: [
    { icon: "✦", name: "Calm Workdays", detail: "12-week programme", badge: "Active", tone: "good" as const },
    { icon: "∞", name: "CereBro Premium", detail: "During sponsorship term", badge: "Included", tone: "good" as const },
    { icon: "↗", name: "EAP referral", detail: "Member-controlled handoff", badge: "Optional", tone: "" as const },
  ],
};

/* -------------------------------------------------------------- PRO-02 */

export const PROGRAMME_DETAIL = {
  name: "Calm Workdays",
  window: "1 Apr 2026 – 31 Mar 2027 · India",
  metrics: [
    { value: "1,126", label: "Invited", delta: "91% of eligible" },
    { value: "512", label: "Activated", delta: "45% of invited" },
    { value: "348", label: "Week 4 retained", delta: "68% of activated" },
    { value: "4.5/5", label: "Anonymous rating", delta: "204 responses" },
  ],
  modules: [
    { name: "Two-minute reset", detail: "Breathing · 12 sessions", badge: "Core" },
    { name: "Wind-down ritual", detail: "Sleep · CBT-I informed", badge: "Core" },
    { name: "Thought reframing", detail: "CBT-structured reflection", badge: "Core" },
    { name: "Weekly reflection", detail: "Journalling prompt", badge: "Optional" },
  ],
};

/* -------------------------------------------------------------- PRO-03 */

export const PATHWAY_MODULES = [
  { name: "Grounding practices", detail: "5-4-3-2-1, body scan, TIPP", weeks: "Weeks 1–2" },
  { name: "Breathing and regulation", detail: "Box breathing, two-minute reset", weeks: "Weeks 2–4" },
  { name: "Sleep foundations", detail: "Wind-down, bedtime anchor, CBT-I education", weeks: "Weeks 4–8" },
  { name: "Thought work", detail: "Reframing, thought sorting", weeks: "Weeks 6–10" },
  { name: "Reflection", detail: "Gratitude, one good thing, intention", weeks: "Weeks 8–12" },
];

/* -------------------------------------------------------------- REF-01 */

export const PROVIDERS = [
  { name: "Tele-MANAS", region: "India · national", detail: "Government mental-health helpline · 24/7 · 20 languages", badge: "Verified", tone: "good" as const },
  { name: "iCall", region: "India · national", detail: "Counsellor helpline · Mon–Sat, 10:00–20:00 IST", badge: "Verified", tone: "good" as const },
  { name: "Acme EAP", region: "India · employees", detail: "Employer-funded counselling · member-initiated only", badge: "Contracted", tone: "" as const },
  { name: "Regional clinic network", region: "India · 4 cities", detail: "In-person referral · awaiting re-verification", badge: "Review due", tone: "warn" as const },
];

/* -------------------------------------------------------------- REF-02 */

export const PROVIDER_DETAIL = {
  name: "Tele-MANAS",
  metrics: [
    { value: "24/7", label: "Hours" },
    { value: "20", label: "Languages" },
    { value: "National", label: "Coverage" },
    { value: "Verified", label: "Status" },
  ],
  verification: [
    { name: "Source", detail: "MoHFW Tele-MANAS listing", badge: "Named source", tone: "good" as const },
    { name: "Last checked", detail: "12 Aug 2026", badge: "Current", tone: "good" as const },
    { name: "Re-verification", detail: "Every 90 days", badge: "Scheduled", tone: "" as const },
  ],
};

/* -------------------------------------------------------------- OUT-01 */

export const OUTCOME_MEASURES = [
  { name: "Perceived stress", detail: "Voluntary pre/post survey", change: "−12%", n: "n=204", tone: "good" as const },
  { name: "Sleep satisfaction", detail: "Voluntary pre/post survey", change: "+9%", n: "n=188", tone: "good" as const },
  { name: "Workday focus", detail: "Voluntary pre/post survey", change: "+4%", n: "n=141", tone: "" as const },
  { name: "Caregiver cohort", detail: "Below reporting threshold", change: "Suppressed", n: "n<20", tone: "warn" as const },
];

/* -------------------------------------------------------------- REP-01 */

export const REPORT_TEMPLATES = [
  { name: "Executive summary", detail: "Participation, satisfaction and programme health", period: "Quarterly" },
  { name: "Programme performance", detail: "Funnel and retention for one programme", period: "Monthly" },
  { name: "Privacy assurance", detail: "Thresholds applied, cohorts suppressed, exports made", period: "Quarterly" },
  { name: "Renewal pack", detail: "Commercial and participation summary for contract review", period: "Annual" },
];

export const REPORT_HISTORY = [
  { name: "Q1 executive summary", date: "12 Jul 2026", by: "Ananya Kapoor", badge: "Delivered", tone: "good" as const },
  { name: "Privacy assurance Q1", date: "12 Jul 2026", by: "Meera Shah", badge: "Delivered", tone: "good" as const },
  { name: "Caregiver pilot", date: "02 Jul 2026", by: "Ravi Menon", badge: "Suppressed", tone: "warn" as const },
];

/* -------------------------------------------------------------- PRI-02 */

export const DATA_FLOWS = [
  { name: "Eligibility identifiers", from: "Workday", to: "CereBro membership", retention: "Contract term + 30 days", tone: "" as const },
  { name: "Administrator identity", from: "Okta SSO", to: "Portal session", retention: "Session only", tone: "" as const },
  { name: "Aggregate participation", from: "CereBro platform", to: "Portal reporting", retention: "24 months", tone: "" as const },
  { name: "Personal wellbeing content", from: "Member device", to: "Never leaves the member account", retention: "Member-controlled", tone: "good" as const },
];

/* -------------------------------------------------------------- SAF-01 */

export const SAFETY_CHECKS = [
  { name: "Crisis resource verification", detail: "India lines checked against named government sources", badge: "Current", tone: "good" as const },
  { name: "Escalation routing test", detail: "Last run 05 Aug 2026", badge: "Passed", tone: "good" as const },
  { name: "AI safety evaluation", detail: "Due in 18 days", badge: "Due", tone: "warn" as const },
  { name: "Operational incidents", detail: "None open", badge: "Clear", tone: "good" as const },
];

/* -------------------------------------------------------------- SAF-02 */

export const RUNBOOK_STEPS = [
  { name: "Detection", detail: "The platform detects a crisis signal inside a member’s own session.", owner: "CereBro" },
  { name: "In-product response", detail: "Region-correct helplines and the member’s safety plan are surfaced immediately.", owner: "CereBro" },
  { name: "Trusted contact", detail: "Reached only if the member switched that on themselves.", owner: "Member" },
  { name: "Employer role", detail: "None. The organisation is never told that a member was flagged.", owner: "Nobody" },
  { name: "Operational failure", detail: "A broken helpline or provider outage is recorded here — without member identity.", owner: "Organisation" },
];

/* -------------------------------------------------------------- EVI-01 */

export const EVIDENCE_ITEMS = [
  { name: "CBT-I informed sleep module", detail: "Reviewed against published CBT-I components", badge: "Reviewed", tone: "good" as const },
  { name: "Grounding and breathing practices", detail: "Framed as regulation support, not treatment", badge: "Reviewed", tone: "good" as const },
  { name: "Mindful activities", detail: "Labelled comfort content, with no cognitive-training claim", badge: "Reviewed", tone: "good" as const },
  { name: "Outcome survey wording", detail: "Non-diagnostic phrasing review", badge: "In review", tone: "warn" as const },
];

/* -------------------------------------------------------------- SEC-01 */

export const SECURITY_CONTROLS = [
  { name: "Encryption in transit and at rest", detail: "TLS 1.3 · AES-256", badge: "Active", tone: "good" as const },
  { name: "Administrator MFA", detail: "Required for every portal role", badge: "Enforced", tone: "good" as const },
  { name: "Least-privilege roles", detail: "No role reaches wellbeing content", badge: "Active", tone: "good" as const },
  { name: "Penetration test", detail: "Annual · last completed Feb 2026", badge: "Current", tone: "good" as const },
  { name: "Sub-processor review", detail: "Annual · next due Oct 2026", badge: "Scheduled", tone: "" as const },
];

/* -------------------------------------------------------------- INT-01 */

export const INTEGRATIONS = [
  { name: "Okta SSO", detail: "SAML 2.0 · administrator access", badge: "Connected", tone: "good" as const },
  { name: "Workday HRIS", detail: "Nightly eligibility sync", badge: "Connected", tone: "good" as const },
  { name: "Eligibility API", detail: "Create, suspend and end sponsored membership", badge: "Available", tone: "" as const },
  { name: "Benefits platform", detail: "Not connected", badge: "Not connected", tone: "" as const },
];

/* -------------------------------------------------------------- INT-02 */

export const INTEGRATION_DETAIL = {
  name: "Workday HRIS",
  metrics: [
    { value: "Nightly", label: "Sync frequency" },
    { value: "04:10 IST", label: "Last run" },
    { value: "1,240", label: "Records received" },
    { value: "0", label: "Rejected" },
  ],
  fields: [
    { name: "External eligibility ID", accepted: true },
    { name: "Work email or SSO identifier", accepted: true },
    { name: "Access start and end date", accepted: true },
    { name: "Eligibility group", accepted: true },
    { name: "Any health, mood, journal or sleep field", accepted: false },
  ],
  log: [
    { at: "12 Aug · 04:10", detail: "1,240 records · 0 rejected", badge: "Success", tone: "good" as const },
    { at: "11 Aug · 04:10", detail: "1,238 records · 0 rejected", badge: "Success", tone: "good" as const },
    { at: "10 Aug · 04:10", detail: "1,238 records · 2 rejected (missing end date)", badge: "Partial", tone: "warn" as const },
  ],
};

/* -------------------------------------------------------------- AUD-01 */

export const AUDIT_ENTRIES = [
  { at: "12 Aug 2026 · 09:14", who: "Ananya Kapoor", action: "Opened privacy centre", target: "Privacy centre", tone: "" as const },
  { at: "12 Aug 2026 · 08:52", who: "Ravi Menon", action: "Sponsored programme", target: "Calm Workdays", tone: "" as const },
  { at: "11 Aug 2026 · 17:31", who: "Meera Shah", action: "Generated report", target: "Q1 executive summary", tone: "" as const },
  { at: "11 Aug 2026 · 11:02", who: "Ananya Kapoor", action: "Changed reporting threshold", target: "20 members", tone: "warn" as const },
  { at: "10 Aug 2026 · 09:44", who: "System", action: "Suppressed cohort in report", target: "Caregiver benefit", tone: "warn" as const },
];

/* -------------------------------------------------------------- BIL-01 */

export const BILLING_METRICS = [
  { value: "1,240", label: "Licensed seats" },
  { value: "684", label: "Activated seats", delta: "55% of licensed" },
  { value: "31 Mar 2027", label: "Renewal date" },
  { value: "Annual", label: "Billing cycle" },
];

export const INVOICES = [
  { ref: "INV-2026-014", date: "01 Apr 2026", amount: "₹18,60,000", badge: "Paid", tone: "good" as const },
  { ref: "INV-2025-031", date: "01 Apr 2025", amount: "₹15,20,000", badge: "Paid", tone: "good" as const },
];

/* -------------------------------------------------------------- ORG-01 */

export const ORG_PROFILE = [
  { label: "Legal entity", value: "Acme Health Services Private Limited" },
  { label: "Primary region", value: "India" },
  { label: "Programme owner", value: "Ananya Kapoor · Benefits owner" },
  { label: "Privacy contact", value: "privacy@acme.in" },
  { label: "Support contact", value: "benefits@acme.in" },
];

/* -------------------------------------------------------------- SUP-01 */

export const SUPPORT_CHANNELS = [
  { name: "Implementation support", detail: "Launch, eligibility and integration help", badge: "Mon–Fri", tone: "" as const },
  { name: "Privacy and governance", detail: "Data-processing terms, DPDP questions, audits", badge: "Mon–Fri", tone: "" as const },
  { name: "Platform status", detail: "No incidents in the last 90 days", badge: "Operational", tone: "good" as const },
];
