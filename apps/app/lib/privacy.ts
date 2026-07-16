/* Right of access and right to erasure, from the client.
 *
 * Both servers already implement this and are tested; nothing here is new machinery. What
 * was missing is the caller — and the marketing site sells "deletion is a product function"
 * (SECURITY.md: "Deletion & export | **Product functions in the app**"), which was a claim
 * with no client behind it.
 *
 * ═══ Why this file is not two fetches ═══
 *
 * Our data lives in TWO services on purpose — the platform holds the account, the engine
 * holds the content, and that split is what makes "counts, never content" a property of the
 * schema (SECURITY.md). The cost of that split lands here: neither service alone can answer
 * "everything about me", and neither alone can erase it. `users.py`'s own export says so:
 * "Coaching content is exported from the coaching engine (/v1/privacy/me/export)".
 *
 * EXPORT is a merge. Half an export silently presented as a whole one is a false answer to
 * a statutory request, so a failure on either side is reported, never quietly dropped.
 *
 * ERASURE IS ORDERED, AND THE ORDER IS LOAD-BEARING:
 *
 *     engine (content) FIRST → platform (account) SECOND
 *
 * The platform's delete revokes every refresh token (`users.py::delete_me`). Do it first and
 * the person can never authenticate again — so if the engine erase then fails, their
 * coaching history is stranded in the engine forever, with no account left to prove it was
 * theirs and no way to retry. Content first means a failure is always recoverable: they
 * still have an account, and they can try again.
 *
 * And the engine is honest about failing: it re-scans after deleting and returns 500 with
 * `verified: false` rather than claim a partial erasure worked, "because a partial erasure
 * reported as success is the single worst outcome here. The person believes their data is
 * gone. It is not." So we ABORT on that — we do not delete the account, and we say so.
 */

import { accessToken, clearTokens } from "./api";

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8100";
const ENGINE = process.env.NEXT_PUBLIC_ENGINE_URL || "http://localhost:8000";

export class NotSignedIn extends Error {}

/** Everything both services hold about the caller, in one document. */
export type ExportBundle = {
  exported_at: string;
  /** Identity, consent, trusted contact — the platform's half. */
  account: unknown;
  /** Journals, moods, sessions, checkpoints — the engine's half. */
  coaching: unknown;
  /** Present only if a half failed. An export that silently lost a half is a lie. */
  incomplete?: string[];
};

async function authed(url: string, init?: RequestInit): Promise<Response> {
  const token = await accessToken();
  if (!token) throw new NotSignedIn("not signed in");
  return fetch(url, { ...init, headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${token}` } });
}

/**
 * Right of access. Merges both services into one document.
 *
 * A half that fails is named in `incomplete` rather than omitted: the person asked what we
 * hold, and "here is some of it, presented as all of it" is a worse answer than "here is
 * this half, and this half failed".
 */
export async function exportEverything(): Promise<ExportBundle> {
  const incomplete: string[] = [];

  const grab = async (label: string, url: string): Promise<unknown> => {
    try {
      const r = await authed(url);
      if (!r.ok) {
        incomplete.push(`${label} (HTTP ${r.status})`);
        return null;
      }
      return await r.json();
    } catch (e) {
      if (e instanceof NotSignedIn) throw e;
      incomplete.push(`${label} (unreachable)`);
      return null;
    }
  };

  const [account, coaching] = await Promise.all([
    grab("account", `${BASE}/users/me/export`),
    grab("coaching content", `${ENGINE}/v1/privacy/me/export`),
  ]);

  return {
    exported_at: new Date().toISOString(),
    account,
    coaching,
    ...(incomplete.length ? { incomplete } : {}),
  };
}

/** Hand the bundle to the browser as a file. */
export function downloadBundle(bundle: ExportBundle, email: string) {
  const safe = (email || "export").replace(/[^a-z0-9]+/gi, "-").toLowerCase();
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = `cerebrozen-${safe}-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export type EraseResult =
  | { ok: true }
  /** The engine could not verify erasure. The account is UNTOUCHED and still usable. */
  | { ok: false; stage: "coaching"; detail: string; remaining?: unknown }
  /** Content is gone; the account row survives. Recoverable by retrying. */
  | { ok: false; stage: "account"; detail: string };

/**
 * Right to erasure, across both services, in the only safe order.
 *
 * Returns rather than throws: every failure here needs a specific, honest sentence in the
 * UI, and an exception would collapse them into "something went wrong" — which, for a
 * deletion, is the one message that must never stand in for the truth.
 */
export async function deleteEverything(): Promise<EraseResult> {
  // 1. Content first. If this fails we must not touch the account (see file header).
  let engineReport: { verified?: boolean; remaining?: unknown; detail?: string } = {};
  try {
    const r = await authed(`${ENGINE}/v1/privacy/me?confirm=true`, { method: "DELETE" });
    engineReport = await r.json().catch(() => ({}));
    // The engine re-scans and returns 500 + verified:false on a partial erasure. Treat
    // anything but a verified success as a failure — never round it up.
    if (!r.ok || engineReport.verified === false) {
      return {
        ok: false,
        stage: "coaching",
        detail: engineReport.detail || `The coaching engine could not confirm your data was erased (HTTP ${r.status}).`,
        remaining: engineReport.remaining,
      };
    }
  } catch (e) {
    if (e instanceof NotSignedIn) throw e;
    return { ok: false, stage: "coaching", detail: "The coaching engine is unreachable. Nothing was deleted." };
  }

  // 2. Account second. Revokes every refresh token, so nothing can follow it.
  try {
    const r = await authed(`${BASE}/users/me?confirm=true`, { method: "DELETE" });
    if (!r.ok && r.status !== 204) {
      return {
        ok: false,
        stage: "account",
        detail: `Your coaching data was erased, but your account record could not be removed (HTTP ${r.status}). Contact support — your content is already gone.`,
      };
    }
  } catch (e) {
    if (e instanceof NotSignedIn) throw e;
    return {
      ok: false,
      stage: "account",
      detail: "Your coaching data was erased, but your account record could not be removed. Contact support — your content is already gone.",
    };
  }

  // The tokens are dead server-side now; drop the local copies too. Not logout() —
  // there is no account left to log out of, and its /auth/logout would 401.
  clearTokens();
  return { ok: true };
}
