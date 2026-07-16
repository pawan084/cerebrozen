/* Coaching-engine client. A turn is an SSE stream of JSON events:
     {"type":"status","msg"}   – what the coach is doing (pre-first-token)
     {"type":"token","text"}   – streamed reply text
     {"type":"node", ...}      – graph node lifecycle (ignored here)
     {"type":"done", response_to_user, session_id, actions, ...}
     {"type":"error","detail"}
   The engine mints session_id on /start and returns it on the `done` event; the
   client stores it and sends it on each subsequent /turn. JWT-protected. */

import { accessToken } from "./api";

const ENGINE = process.env.NEXT_PUBLIC_ENGINE_URL || "http://localhost:8000";

/** One commitment the coach surfaced (right-panel card in the reference apps). */
export type CoachAction = {
  action_id: string;
  full_text?: string;
  expected_outcome?: string;
  roi_metrics?: string[];
  status?: string;
};

export type CoachEvent =
  | { type: "status"; msg: string }
  | { type: "token"; text: string }
  | { type: "node"; [k: string]: unknown }
  | { type: "done"; response_to_user?: string; session_id?: string; actions?: CoachAction[]; ended?: boolean; [k: string]: unknown }
  | { type: "error"; detail: string };

export class AuthExpired extends Error {}

async function openStream(text: string, sessionId: string | null, token: string, edit = false): Promise<Response> {
  const url = sessionId
    ? `${ENGINE}/v1/sessions/${encodeURIComponent(sessionId)}/turn?stream=true${edit ? "&edit=true" : ""}`
    : `${ENGINE}/v1/sessions/start?stream=true`;
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ text }),
  });
}

/** Async-iterate the coach's SSE events for one turn. Refreshes the token once
 *  on a 401 before giving up (throws AuthExpired → caller sends user to login).
 *  `edit` re-runs the last turn via the engine's time-travel (turn?edit=true). */
export async function* coachTurn(
  text: string,
  sessionId: string | null,
  edit = false,
): AsyncGenerator<CoachEvent> {
  let token = await accessToken();
  if (!token) throw new AuthExpired("not signed in");

  let res = await openStream(text, sessionId, token, edit);
  if (res.status === 401) {
    token = await accessToken(true);
    if (!token) throw new AuthExpired("session expired");
    res = await openStream(text, sessionId, token, edit);
  }
  if (res.status === 401) throw new AuthExpired("session expired");
  if (!res.ok || !res.body) {
    const detail = (await res.json().catch(() => null))?.detail ?? `HTTP ${res.status}`;
    throw new Error(String(detail));
  }

  yield* readSse(res);
}

/** Save or dismiss one commitment card (the commit gate wants ≥1 saved to close). */
export async function setActionStatus(
  sessionId: string,
  actionId: string,
  action: "save" | "delete",
): Promise<void> {
  const token = await accessToken();
  if (!token) throw new AuthExpired("not signed in");
  const r = await fetch(`${ENGINE}/v1/sessions/${encodeURIComponent(sessionId)}/actions/status`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ action_id: actionId, action }),
  });
  if (r.status === 401) throw new AuthExpired("session expired");
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `HTTP ${r.status}`);
}

export type SessionMeta = { session_id: string; title?: string; resumable?: boolean; ended?: boolean; updated_at?: string };

/** The user's past sessions, newest first (the Recents list). */
export async function listSessions(): Promise<SessionMeta[]> {
  const token = await accessToken();
  if (!token) return [];
  const r = await fetch(`${ENGINE}/v1/sessions`, { headers: { Authorization: `Bearer ${token}` } });
  if (!r.ok) return [];
  return ((await r.json())?.sessions ?? []) as SessionMeta[];
}

/** Load a past session's transcript, mapped to the chat's you/coach shape. */
export async function loadHistory(sessionId: string): Promise<{ who: "you" | "coach"; text: string }[]> {
  const token = await accessToken();
  if (!token) return [];
  const r = await fetch(`${ENGINE}/v1/sessions/${encodeURIComponent(sessionId)}/history`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!r.ok) return [];
  const out: { who: "you" | "coach"; text: string }[] = [];
  for (const e of ((await r.json())?.chat_history ?? []) as Array<{ user?: { text?: string }; bot?: { text?: string } }>) {
    if (e.user?.text) out.push({ who: "you", text: e.user.text });
    else if (e.bot?.text) out.push({ who: "coach", text: e.bot.text });
  }
  return out;
}

async function* readSse(res: Response): AsyncGenerator<CoachEvent> {
  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  let buf = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let sep: number;
    // SSE frames are separated by a blank line.
    while ((sep = buf.indexOf("\n\n")) !== -1) {
      const frame = buf.slice(0, sep);
      buf = buf.slice(sep + 2);
      for (const line of frame.split("\n")) {
        if (!line.startsWith("data:")) continue;
        const raw = line.slice(5).trim();
        if (!raw) continue;
        try {
          yield JSON.parse(raw) as CoachEvent;
        } catch {
          /* ignore a partial/garbled frame */
        }
      }
    }
  }
}
