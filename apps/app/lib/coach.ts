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

export type CoachEvent =
  | { type: "status"; msg: string }
  | { type: "token"; text: string }
  | { type: "node"; [k: string]: unknown }
  | { type: "done"; response_to_user?: string; session_id?: string; actions?: unknown[]; [k: string]: unknown }
  | { type: "error"; detail: string };

export class AuthExpired extends Error {}

async function openStream(text: string, sessionId: string | null, token: string): Promise<Response> {
  const url = sessionId
    ? `${ENGINE}/v1/sessions/${encodeURIComponent(sessionId)}/turn?stream=true`
    : `${ENGINE}/v1/sessions/start?stream=true`;
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ text }),
  });
}

/** Async-iterate the coach's SSE events for one turn. Refreshes the token once
 *  on a 401 before giving up (throws AuthExpired → caller sends user to login). */
export async function* coachTurn(
  text: string,
  sessionId: string | null,
): AsyncGenerator<CoachEvent> {
  let token = await accessToken();
  if (!token) throw new AuthExpired("not signed in");

  let res = await openStream(text, sessionId, token);
  if (res.status === 401) {
    token = await accessToken(true);
    if (!token) throw new AuthExpired("session expired");
    res = await openStream(text, sessionId, token);
  }
  if (res.status === 401) throw new AuthExpired("session expired");
  if (!res.ok || !res.body) {
    const detail = (await res.json().catch(() => null))?.detail ?? `HTTP ${res.status}`;
    throw new Error(String(detail));
  }

  const reader = res.body.getReader();
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
