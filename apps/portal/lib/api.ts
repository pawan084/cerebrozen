/**
 * Session-aware API client for the organisation portal.
 *
 * Same token model as apps/app (docs/WEB_APP_PLAN.md §3): the ACCESS token
 * lives in memory only — XSS cannot lift it from storage — and the REFRESH
 * token in localStorage. Every 401 triggers one rotation via POST /auth/refresh
 * before giving up. No cookies, so no CSRF surface.
 *
 * Deliberately a SEPARATE storage key from apps/app. An administrator is very
 * likely to be a member too, and sharing a key would mean signing out of the
 * portal signed you out of your own wellbeing account in the next tab — or,
 * worse, that opening the portal silently adopted your member session.
 */

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

const REFRESH_KEY = "cerebro_portal_refresh";

let accessToken: string | null = null;

function readRefresh(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

function storeSession(tokens: { access_token: string; refresh_token: string }) {
  accessToken = tokens.access_token;
  window.localStorage.setItem(REFRESH_KEY, tokens.refresh_token);
}

export function clearSession() {
  accessToken = null;
  if (typeof window !== "undefined") window.localStorage.removeItem(REFRESH_KEY);
}

export function hasSession(): boolean {
  return readRefresh() !== null;
}

let refreshInFlight: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  const refresh = readRefresh();
  if (!refresh) return false;
  // One rotation at a time: three widgets loading at once must not race three
  // refreshes, two of which would then be using a rotated-away token.
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${API_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refresh }),
      });
      if (!res.ok) {
        clearSession();
        return false;
      }
      storeSession(await res.json());
      return true;
    } catch {
      return false;
    }
  })().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function authedFetch(path: string, init: RequestInit = {}, allowRetry = true): Promise<Response> {
  if (!accessToken && hasSession()) await refreshSession();

  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(init.headers || {}),
    },
  });

  if (res.status === 401) {
    if (allowRetry && (await refreshSession())) return authedFetch(path, init, false);
    clearSession();
    throw new Error("unauthorized");
  }
  // A 403 is NOT a dead session here — it is the honest answer for a signed-in
  // user who administers no organisation, or an analyst attempting a write.
  // Treating it as expiry would sign them out instead of explaining.
  return res;
}

export class NotAnOrgAdminError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "NotAnOrgAdminError";
  }
}

export async function api<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await authedFetch(path, init);
  if (!res.ok) {
    let detail = `Request failed: ${res.status}`;
    try {
      const body = await res.json();
      if (typeof body?.detail === "string") detail = body.detail;
    } catch {
      /* non-JSON error body — keep the status line */
    }
    if (res.status === 403) throw new NotAnOrgAdminError(detail);
    throw new Error(detail);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

function signInMessage(status: number, detail?: string): string {
  if (status === 400 || status === 401) return "Invalid email or password.";
  if (status === 429) return "Too many attempts just now — wait a minute and try again.";
  if (status >= 500)
    return "We couldn't reach the sign-in service. Nothing is wrong with your account — try again shortly.";
  return detail || "Couldn't sign in just now — try again.";
}

export async function signIn(email: string, password: string): Promise<void> {
  // OAuth2PasswordRequestForm: form-encoded, and the field is `username`.
  const body = new URLSearchParams({ username: email, password });
  const res = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) {
    let detail: string | undefined;
    try {
      const b = await res.json();
      if (typeof b?.detail === "string") detail = b.detail;
    } catch {
      /* ignore */
    }
    throw new Error(signInMessage(res.status, detail));
  }
  storeSession(await res.json());
}

export async function signOut(): Promise<void> {
  try {
    await authedFetch("/auth/logout", { method: "POST" }, false);
  } catch {
    // Revoking server-side is best effort; the local session goes either way.
  }
  clearSession();
}

/* ------------------------------------------------------------------ /org */

export type Org = {
  id: string;
  name: string;
  legal_entity: string;
  region: string;
  primary_contact_email: string;
  privacy_contact_email: string;
  reporting_threshold: number;
  small_cell_suppression: boolean;
  retention_months: number;
  seats_licensed: number;
  contract_start: string | null;
  contract_end: string | null;
  grants_premium: boolean;
  is_active: boolean;
};

export type OrgSummary = {
  organisation: string;
  region: string;
  seats_licensed: number;
  eligible: number;
  invited: number;
  activated: number;
  ended: number;
  reporting_threshold: number;
  small_cell_suppression: boolean;
  individual_reporting_available: boolean;
};

export type Group = {
  id: string;
  name: string;
  rule: string;
  source: string;
  region: string;
  is_active: boolean;
};

/** `activated`/`active` are null when the group is below the threshold. */
export type GroupTotals = {
  group_id: string | null;
  name: string;
  eligible: number;
  activated: number | null;
  active: number | null;
  suppressed: boolean;
  threshold: number;
};

export type Membership = {
  id: string;
  org_id: string;
  group_id: string | null;
  external_ref: string;
  status: string;
  access_start: string | null;
  access_end: string | null;
};

export type Sponsorship = {
  id: string;
  org_id: string;
  group_id: string | null;
  programme_slug: string;
  starts_on: string | null;
  ends_on: string | null;
  is_active: boolean;
};

export const getOrg = () => api<Org>("/org");
export const getSummary = () => api<OrgSummary>("/org/summary");
export const getGroups = () => api<Group[]>("/org/groups");
export const getGroupTotals = () => api<GroupTotals[]>("/org/groups/totals");
export const getMembers = () => api<Membership[]>("/org/members");
export const getProgrammes = () => api<Sponsorship[]>("/org/programmes");

/* --------------------------------------------------------------- writes */

export type OrgSettingsPatch = Partial<{
  legal_entity: string;
  primary_contact_email: string;
  privacy_contact_email: string;
  /** Values below the floor are raised to it server-side, not rejected. */
  reporting_threshold: number;
  small_cell_suppression: boolean;
  retention_months: number;
}>;

export const patchOrg = (body: OrgSettingsPatch) =>
  api<Org>("/org", { method: "PATCH", body: JSON.stringify(body) });

export const createGroup = (body: { name: string; rule?: string; source?: string; region?: string }) =>
  api<Group>("/org/groups", { method: "POST", body: JSON.stringify(body) });

/** The API forbids unknown fields, so this object is the whole contract. */
export const addMember = (body: {
  email: string;
  group_id?: string | null;
  external_ref?: string;
  access_start?: string | null;
  access_end?: string | null;
}) => api<Membership>("/org/members", { method: "POST", body: JSON.stringify(body) });

export const endMembership = (id: string) =>
  api<Membership>(`/org/members/${id}`, { method: "DELETE" });

export type ImportRow = {
  line: number;
  external_ref: string;
  /** "added" | "no_account" | "already_member" | "invalid" */
  outcome: string;
  detail: string;
};

export type ImportResult = { added: number; skipped: number; rows: ImportRow[] };

/**
 * The columns an eligibility file may contain — mirrors
 * `services/eligibility_csv.ALLOWED_COLUMNS` on the backend.
 *
 * Duplicated on purpose. The server's copy is the one that decides, but this
 * one lets the portal refuse a file WITHOUT UPLOADING IT: an HR export with a
 * `diagnosis` column should never leave the administrator's machine, and a
 * check that runs after the upload has already failed at that.
 */
export const ELIGIBILITY_COLUMNS = ["access_end", "access_start", "email", "external_ref"] as const;

/** Header names are matched forgivingly on form, strictly on meaning. */
export function unknownColumns(csvText: string): string[] {
  const header = csvText.split(/\r?\n/, 1)[0] ?? "";
  const allowed = new Set<string>(ELIGIBILITY_COLUMNS);
  return header
    .split(",")
    .map((c) => c.trim().toLowerCase().replace(/\s+/g, "_").replace(/^﻿/, ""))
    .filter((c) => c && !allowed.has(c));
}

export const importMembers = (body: { csv: string; group_id?: string | null }) =>
  api<ImportResult>("/org/members/import", { method: "POST", body: JSON.stringify(body) });

export const sponsorProgramme = (body: {
  programme_slug: string;
  group_id?: string | null;
  starts_on?: string | null;
  ends_on?: string | null;
}) => api<Sponsorship>("/org/programmes", { method: "POST", body: JSON.stringify(body) });

export type OrgAdminRow = {
  id: string;
  email: string;
  name: string;
  role: string;
  attested_on: string | null;
};

export const getAdmins = () => api<OrgAdminRow[]>("/org/admins");

export type LaunchStep = {
  key: string;
  label: string;
  detail: string;
  href: string;
  done: boolean;
};
export type LaunchState = { steps: LaunchStep[]; threshold: number; region: string };

/**
 * The launch checklist, DERIVED rather than stored.
 *
 * Six booleans in a table can say "eligibility connected" while the
 * organisation has no seats. Asking the question directly each time means a
 * step cannot be ticked by editing a row, and the checklist cannot drift from
 * what is actually configured.
 *
 * One read of each thing it describes, in parallel.
 */
export async function getLaunchState(): Promise<LaunchState> {
  const [org, groups, members, programmes] = await Promise.all([
    getOrg(),
    getGroups(),
    getMembers(),
    getProgrammes(),
  ]);
  return {
    threshold: org.reporting_threshold,
    region: org.region,
    steps: [
      {
        key: "profile",
        label: "Organisation profile",
        detail: "Legal entity and the contacts a member or regulator would use",
        href: "/settings",
        done: Boolean(org.legal_entity && org.primary_contact_email && org.privacy_contact_email),
      },
      {
        key: "privacy",
        label: "Privacy and reporting model",
        detail: "Threshold and small-cell suppression",
        href: "/privacy",
        // Suppression on is the default and the safe state; this step is about
        // having looked at it, which we can only evidence by it being on.
        done: org.small_cell_suppression,
      },
      {
        key: "cohorts",
        label: "Eligibility groups",
        detail: "At least one reporting-safe group",
        href: "/cohorts/new",
        done: groups.length > 0,
      },
      {
        key: "eligibility",
        label: "Eligible members",
        detail: "Seats added by invitation, import or API",
        href: "/members/invite",
        done: members.length > 0,
      },
      {
        key: "programme",
        label: "Sponsored programme",
        detail: "At least one funded programme",
        href: "/programmes",
        done: programmes.length > 0,
      },
      {
        key: "seats",
        label: "Licensed seats",
        detail: "Contracted seat count on record",
        href: "/billing",
        done: org.seats_licensed > 0,
      },
    ],
  };
}

export type AuditRow = {
  id: string;
  created_at: string;
  admin_email: string;
  action: string;
  target_type: string;
  target_id: string;
  detail: Record<string, unknown>;
};

export const getAudit = () => api<AuditRow[]>("/org/audit");

/** Billing reads the organisation and its seat usage together. */
export async function getBilling() {
  const [org, summary] = await Promise.all([getOrg(), getSummary()]);
  return { org, summary };
}
