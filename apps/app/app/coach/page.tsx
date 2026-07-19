"use client";

import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useSearchParams } from "next/navigation";
import { logout } from "@/lib/api";
import { coachTurn, setActionStatus, listSessions, loadHistory, AuthExpired, type CoachAction, type SessionMeta } from "@/lib/coach";
import { firstName, useMe } from "@/components/shell";
import { Markdown } from "@/components/markdown";
import { CrisisPanel } from "@/components/crisis";

type Msg = { who: "you" | "coach"; text: string };
type Card = CoachAction & { local?: "saving" | "saved" | "dismissed" };

export default function CoachPage() {
  const me = useMe();
  const name = firstName(me);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [actions, setActions] = useState<Card[]>([]);
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState<number | null>(null);
  /* Sticky for the session, deliberately. The engine flags the turn that tripped the
     screen; clearing it on the next ordinary turn would yank a helpline out from under
     someone who is mid-crisis and just said "ok". It clears when they start a new
     session. */
  const [crisis, setCrisis] = useState(false);
  const [sessions, setSessions] = useState<SessionMeta[]>([]);
  const [showRecents, setShowRecents] = useState(false);
  const sessionId = useRef<string | null>(null);
  const lastUserText = useRef("");
  const streamRef = useRef<HTMLDivElement>(null);

  const params = useSearchParams();
  const refreshSessions = useCallback(() => { listSessions().then(setSessions).catch(() => {}); }, []);
  useEffect(() => { refreshSessions(); }, [refreshSessions]);

  // Prefill from the home-screen "What's on your mind?" search (don't auto-send —
  // let the person review it first).
  useEffect(() => {
    const q = params.get("q");
    if (q) setDraft(q);
  }, [params]);

  useEffect(() => {
    streamRef.current?.scrollTo({ top: streamRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, status, actions]);

  function newSession() {
    sessionId.current = null; lastUserText.current = "";
    setMessages([]); setActions([]); setStatus(""); setShowRecents(false); setCrisis(false);
  }
  async function openSession(s: SessionMeta) {
    setShowRecents(false);
    if (s.session_id === sessionId.current) return;
    sessionId.current = s.session_id;
    // A different session carries its own safety state; don't leak the previous one's.
    setActions([]); setStatus(""); setCrisis(false);
    const history = await loadHistory(s.session_id);
    setMessages(history);
    // So Regenerate on a loaded session re-runs the real last message, not "".
    const lastYou = [...history].reverse().find((m) => m.who === "you");
    lastUserText.current = lastYou?.text ?? "";
  }

  async function runTurn(text: string, edit = false) {
    setBusy(true); setStatus("");
    if (!edit) {
      lastUserText.current = text;
      setMessages((m) => [...m, { who: "you", text }, { who: "coach", text: "" }]);
    } else {
      // regenerate: replace the last coach bubble with a fresh empty one
      setMessages((m) => [...m.slice(0, -1), { who: "coach", text: "" }]);
    }
    const coachIdx = edit ? messages.length - 1 : messages.length + 1;

    try {
      for await (const ev of coachTurn(text, sessionId.current, edit)) {
        if (ev.type === "status") setStatus(ev.msg);
        else if (ev.type === "token") {
          setStatus("");
          setMessages((m) => {
            const next = [...m];
            if (next[coachIdx]) next[coachIdx] = { who: "coach", text: next[coachIdx].text + ev.text };
            return next;
          });
        } else if (ev.type === "done") {
          if (ev.session_id) sessionId.current = ev.session_id;
          if (ev.actions) setActions(ev.actions as Card[]);
          // The deterministic crisis takeover fired: what streamed back is a scripted
          // safety message, not coaching. Surface the helplines rather than letting it
          // read as an ordinary reply.
          if (ev.safety_flag === "crisis") setCrisis(true);
          setStatus("");
          setMessages((m) => {
            const next = [...m];
            const cur = next[coachIdx]?.text ?? "";
            if (!cur.trim() && ev.response_to_user) next[coachIdx] = { who: "coach", text: ev.response_to_user };
            return next;
          });
        } else if (ev.type === "error") throw new Error(ev.detail);
      }
    } catch (err) {
      if (err instanceof AuthExpired) { await logout(); window.location.href = "/"; return; }
      setMessages((m) => {
        const next = [...m];
        next[coachIdx] = { who: "coach", text: "The coach is unreachable right now — your message wasn't lost. Try again in a moment." };
        return next;
      });
    } finally {
      setStatus(""); setBusy(false); refreshSessions();
    }
  }

  function send() {
    const text = draft.trim();
    if (!text || busy) return;
    setDraft("");
    runTurn(text);
  }

  function onKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
  }

  async function copy(text: string, i: number) {
    try { await navigator.clipboard.writeText(text); setCopied(i); setTimeout(() => setCopied(null), 1400); } catch { /* ignore */ }
  }

  async function actOn(a: Card, action: "save" | "delete") {
    if (!sessionId.current || a.local === "saving") return;
    setActions((p) => p.map((x) => x.action_id === a.action_id ? { ...x, local: "saving" } : x));
    try {
      await setActionStatus(sessionId.current, a.action_id, action);
      setActions((p) => p.map((x) => x.action_id === a.action_id ? { ...x, local: action === "save" ? "saved" : "dismissed" } : x));
    } catch {
      setActions((p) => p.map((x) => x.action_id === a.action_id ? { ...x, local: undefined } : x));
    }
  }

  const visible = actions.filter((a) => a.local !== "dismissed" && a.status !== "deleted");
  const anySaved = actions.some((a) => a.local === "saved" || a.status === "saved");
  const lastCoachIdx = messages.map((m) => m.who).lastIndexOf("coach");

  return (
    <div className="chat">
      <div className="chat-head">
        <div>
          <div className="eyebrow">Talk</div>
          <h1>Your coach</h1>
        </div>
        <div className="chat-actions">
          <button className="ghost-btn" onClick={newSession} disabled={busy}>+ New</button>
          <div className="recents-wrap">
            <button className="ghost-btn" onClick={() => setShowRecents((v) => !v)} aria-expanded={showRecents}>Recents ▾</button>
            {showRecents && (
              <div className="recents" role="menu">
                {sessions.length === 0
                  ? <div className="recents-empty">No past sessions yet.</div>
                  : sessions.map((s) => (
                      <button key={s.session_id} role="menuitem"
                        className={`recent ${s.session_id === sessionId.current ? "active" : ""}`}
                        onClick={() => openSession(s)}>
                        <span className="r-title">{s.title || "Untitled session"}</span>
                        <span className="r-meta">{s.ended ? "ended" : "active"}{s.updated_at ? ` · ${new Date(s.updated_at).toLocaleDateString()}` : ""}</span>
                      </button>
                    ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="stream" ref={streamRef}>
        <div className="thread">
          {messages.length === 0 && (
            <div className="intro">
              <h2>{name ? `Hi ${name} — what's on your mind?` : "What's on your mind?"}</h2>
              <p>A conversation you're postponing, a decision that's stalling, a moment you want to get right. Start there — every session ends with one concrete step.</p>
            </div>
          )}
          {messages.map((m, i) => (
            <div key={i} className={`row ${m.who}`}>
              <div className="bubble">
                {m.who === "coach"
                  ? (m.text ? <Markdown text={m.text} /> : (busy ? <span className="dots"><span>·</span><span>·</span><span>·</span></span> : ""))
                  : m.text}
                {m.who === "coach" && m.text && !busy && (
                  <div className="bubble-tools">
                    <button className="tool" onClick={() => copy(m.text, i)}>{copied === i ? "Copied" : "Copy"}</button>
                    {i === lastCoachIdx && <button className="tool" onClick={() => runTurn(lastUserText.current, true)}>Regenerate</button>}
                  </div>
                )}
              </div>
            </div>
          ))}
          {status && <div className="status">{status}</div>}

          {/* Above the commitments on purpose: when the screen has fired, a phone number
              outranks a coaching commitment. */}
          {crisis && <CrisisPanel region={me?.crisis_region ?? ""} />}

          {visible.length > 0 && (
            <div className="commitments">
              <div className="c-head">Your commitments {!anySaved && <span className="c-gate">save at least one to wrap up</span>}</div>
              {visible.map((a) => (
                <div key={a.action_id} className={`commit ${a.local === "saved" || a.status === "saved" ? "saved" : ""}`}>
                  <div className="c-body">
                    <div className="c-text">{a.full_text}</div>
                    {a.expected_outcome && <div className="c-out">→ {a.expected_outcome}</div>}
                  </div>
                  {a.local === "saved" || a.status === "saved"
                    ? <span className="c-done">Committed ✓</span>
                    : (
                      <div className="c-actions">
                        <button className="c-save" disabled={a.local === "saving"} onClick={() => actOn(a, "save")}>{a.local === "saving" ? "…" : "Commit"}</button>
                        <button className="c-skip" disabled={a.local === "saving"} onClick={() => actOn(a, "delete")}>Dismiss</button>
                      </div>
                    )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="composer">
        <div className="composer-inner">
          <textarea value={draft} onChange={(e) => setDraft(e.target.value)} onKeyDown={onKey}
            placeholder="Talk it through…" rows={1} aria-label="Message your coach" />
          <button className="send" onClick={send} disabled={busy || !draft.trim()}>Send</button>
        </div>
      </div>
    </div>
  );
}
