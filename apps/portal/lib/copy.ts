/**
 * Fixed privacy copy — reproduced verbatim from `ref/portal.html`.
 *
 * These five strings are the portal's promise to members, quoted in the
 * spec and in docs/REDESIGN_V2.md §4. They live in one module so a reviewer
 * can diff them against the prototype in one place, and so no page can
 * paraphrase one by accident. Do not soften, shorten or reword them.
 */

/** Pinned at the bottom of the sidebar on every route. */
export const PRIVACY_WALL_SIDEBAR =
  "No administrator can open a member’s chat, journal, mood history, sleep data, safety plan, referral reason, or personal activity timeline.";

/** The reusable notice repeated on every reporting surface. */
export const PRIVACY_WALL_NOTICE_TITLE =
  "Individual wellness data is not available here.";
export const PRIVACY_WALL_NOTICE_BODY =
  "Reporting uses anonymous group totals, minimum cohort thresholds and small-cell suppression. Managers cannot see who used CereBro.";

/** CAM-01 — the practices this portal will not implement. */
export const CAMPAIGN_PROHIBITED =
  "manager-targeted lists, inactive-employee exports, streak pressure, forced enrolment or messages implying performance consequences.";

/** ROL-01 — no role, however senior, reaches wellbeing content. */
export const ROLE_BOUNDARY =
  "Even the Benefits owner and Privacy reviewer cannot access personal wellbeing content. Role permissions apply only to eligibility, programmes, aggregate reports, billing, integrations and governance.";

/** PRE-01 — the never-available list, shown to admins before launch. */
export const NEVER_AVAILABLE =
  "Chats, voice transcripts, journal entries, moods, sleep records, safety plans, trusted contacts, crisis-resource use, referral reasons, provider choice and individual usage.";
