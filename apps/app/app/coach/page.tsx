"use client";

import { useCallback, useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from "react";
import { useSearchParams } from "next/navigation";
import { logout } from "@/lib/api";
import { coachTurn, setActionStatus, listSessions, loadHistory, AuthExpired, type CoachAction, type SessionMeta } from "@/lib/coach";
import { firstName, useMe } from "@/components/shell";
import { Markdown } from "@/components/markdown";
import { CrisisPanel } from "@/components/crisis";
import { getRecognition, speak, stopSpeaking, sttSupported, ttsSupported, type Recognition } from "@/lib/voice";

type Msg = { who: "you" | "coach"; text: string };
type Card = CoachAction & { local?: "saving" | "saved" | "dismissed" };

// Conversation starters — beat the blank box (the semi-guided pattern).
const STARTERS = [
  "Help me prepare for a hard conversation.",
  "I keep procrastinating on something important.",
  "I got tough feedback and I'm still stuck on it.",
  "I have a decision to make and I'm going in circles.",
];

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
  const [recentSearch, setRecentSearch] = useState("");
  const [atBottom, setAtBottom] = useState(true);
  const [announce, setAnnounce] = useState("");
  const [listening, setListening] = useState(false);
  const [speakOn, setSpeakOn] = useState(false);
  const [voiceCaps, setVoiceCaps] = useState({ stt: false, tts: false });
  const sessionId = useRef<string | null>(null);
  const lastUserText = useRef("");
  const streamRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const recentsWrap = useRef<HTMLDivElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);
  const recogRef = useRef<Recognition | null>(null);
  const speakOnRef = useRef(false);
  const runTurnRef = useRef<(text: string, edit?: boolean) => void>(() => {});

  const params = useSearchParams();
  const refreshSessions = useCallback(() => { listSessions().then(setSessions).catch(() => {}); }, []);
  useEffect(() => { refreshSessions(); }, [refreshSessions]);

  // Prefill from the home-screen "What's on your mind?" search (don't auto-send —
  // let the person review it first).
  useEffect(() => {
    const q = params.get("q");
    if (q) setDraft(q);
  }, [params]);

  // Anchored auto-scroll: only follow the stream while the user is already at the
  // bottom, so scrolling up to re-read isn't yanked back (a scroll-to-latest pill
  // returns them). WAI streaming-chat pattern.
  useEffect(() => {
    if (atBottom) streamRef.current?.scrollTo({ top: streamRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, status, actions, atBottom]);

  const onStreamScroll = () => {
    const el = streamRef.current;
    if (el) setAtBottom(el.scrollHeight - el.scrollTop - el.clientHeight < 48);
  };
  const jumpToLatest = () => {
    setAtBottom(true);
    streamRef.current?.scrollTo({ top: streamRef.current.scrollHeight, behavior: "smooth" });
  };

  // Recents menu: close on Escape or outside click (WAI menu pattern).
  useEffect(() => {
    if (!showRecents) return;
    const onKey = (e: globalThis.KeyboardEvent) => e.key === "Escape" && setShowRecents(false);
    const onClick = (e: MouseEvent) => { if (!recentsWrap.current?.contains(e.target as Node)) setShowRecents(false); };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onClick);
    return () => { document.removeEventListener("keydown", onKey); document.removeEventListener("mousedown", onClick); };
  }, [showRecents]);

  // Voice is browser-native + keyless; probe support on mount and keep the
  // speak-replies flag on a ref so streaming callbacks read the latest value.
  useEffect(() => { setVoiceCaps({ stt: sttSupported(), tts: ttsSupported() }); }, []);
  useEffect(() => { speakOnRef.current = speakOn; if (!speakOn) stopSpeaking(); }, [speakOn]);
  useEffect(() => () => { stopSpeaking(); recogRef.current?.abort(); }, []);

  function startListening() {
    const r = getRecognition();
    if (!r) return;
    recogRef.current = r;
    setListening(true);
    stopSpeaking();
    let finalText = "";
    r.onresult = (e) => {
      let interim = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const res = e.results[i];
        const t = res[0]?.transcript ?? "";
        if (res.isFinal) finalText += t; else interim += t;
      }
      setDraft((finalText + interim).trim());
    };
    r.onend = () => {
      setListening(false);
      recogRef.current = null;
      const text = finalText.trim();
      if (text) { setDraft(""); runTurnRef.current(text); } // voice loop: speak → send
    };
    r.onerror = () => { setListening(false); recogRef.current = null; };
    try { r.start(); } catch { setListening(false); recogRef.current = null; }
  }
  function toggleMic() { if (listening) recogRef.current?.stop(); else startListening(); }

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
    setBusy(true); setStatus(""); setAnnounce(""); setAtBottom(true);
    if (!edit) {
      lastUserText.current = text;
      setMessages((m) => [...m, { who: "you", text }, { who: "coach", text: "" }]);
    } else {
      // regenerate: replace the last coach bubble with a fresh empty one
      setMessages((m) => [...m.slice(0, -1), { who: "coach", text: "" }]);
    }
    const coachIdx = edit ? messages.length - 1 : messages.length + 1;
    const controller = new AbortController();
    abortRef.current = controller;
    let acc = ""; // the reply so far — so a stop/disconnect keeps what streamed

    try {
      for await (const ev of coachTurn(text, sessionId.current, edit, controller.signal)) {
        if (ev.type === "status") setStatus(ev.msg);
        else if (ev.type === "token") {
          acc += ev.text;
          setStatus("");
          setMessages((m) => {
            const next = [...m];
            if (next[coachIdx]) next[coachIdx] = { who: "coach", text: acc };
            return next;
          });
        } else if (ev.type === "done") {
          if (ev.session_id) sessionId.current = ev.session_id;
          if (ev.actions) setActions(ev.actions as Card[]);
          // The deterministic crisis takeover fired: what streamed back is a scripted
          // safety message, not coaching. Surface the helplines rather than letting it
          // read as an ordinary reply.
          if (ev.safety_flag === "crisis") setCrisis(true);
          if (!acc.trim() && ev.response_to_user) acc = ev.response_to_user;
          setStatus("");
          setMessages((m) => {
            const next = [...m];
            next[coachIdx] = { who: "coach", text: acc };
            return next;
          });
          setAnnounce(acc); // announce the completed reply politely to screen readers
          if (speakOnRef.current) speak(acc); // spoken coach reply (voice mode)
        } else if (ev.type === "error") throw new Error(ev.detail);
      }
    } catch (err) {
      if (err instanceof AuthExpired) { await logout(); window.location.href = "/"; return; }
      setMessages((m) => {
        const next = [...m];
        if (controller.signal.aborted) {
          // User hit Stop — keep whatever arrived rather than discarding it.
          next[coachIdx] = { who: "coach", text: acc.trim() ? acc + "\n\n_(stopped)_" : "_(stopped)_" };
        } else if (acc.trim()) {
          // Mid-stream disconnect: preserve the partial reply, don't nuke it.
          next[coachIdx] = { who: "coach", text: acc + "\n\n_(the connection dropped before the rest arrived — try again)_" };
        } else {
          next[coachIdx] = { who: "coach", text: "The coach is unreachable right now — your message wasn't lost. Try again in a moment." };
        }
        return next;
      });
    } finally {
      abortRef.current = null;
      setStatus(""); setBusy(false); refreshSessions();
    }
  }

  function stop() {
    abortRef.current?.abort();
    stopSpeaking();
  }
  // Keep the ref pointing at the current runTurn so voice's onend calls the latest closure.
  runTurnRef.current = runTurn;

  function send() {
    const text = draft.trim();
    if (!text || busy) return;
    setDraft("");
    if (taRef.current) taRef.current.style.height = "auto"; // reset the grown textarea
    runTurn(text);
  }

  function onKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
  }

  // Auto-grow the composer up to a cap so multi-line drafts aren't stuck in one row.
  function onDraftChange(e: ChangeEvent<HTMLTextAreaElement>) {
    setDraft(e.target.value);
    const el = e.target;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 140) + "px";
  }

  async function copy(text: string, i: number) {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        // Fallback for insecure contexts (http) where the Clipboard API is unavailable.
        const ta = document.createElement("textarea");
        ta.value = text; ta.style.position = "fixed"; ta.style.opacity = "0";
        document.body.appendChild(ta); ta.focus(); ta.select();
        document.execCommand("copy"); document.body.removeChild(ta);
      }
      setCopied(i); setTimeout(() => setCopied(null), 1400);
    } catch { /* clipboard blocked — nothing we can do, fail quietly */ }
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
          {voiceCaps.tts && (
            <button className={`ghost-btn ${speakOn ? "on" : ""}`} onClick={() => setSpeakOn((v) => !v)}
              aria-pressed={speakOn} aria-label={speakOn ? "Turn off spoken replies" : "Read replies aloud"}>
              {speakOn ? "🔊 Voice on" : "🔈 Voice"}
            </button>
          )}
          <button className="ghost-btn" onClick={newSession} disabled={busy}>+ New</button>
          <div className="recents-wrap" ref={recentsWrap}>
            <button className="ghost-btn" onClick={() => setShowRecents((v) => !v)} aria-expanded={showRecents} aria-haspopup="menu">Recents ▾</button>
            {showRecents && (() => {
              const q = recentSearch.trim().toLowerCase();
              const filtered = q ? sessions.filter((s) => (s.title || "untitled session").toLowerCase().includes(q)) : sessions;
              return (
                <div className="recents" role="menu">
                  {sessions.length >= 4 && (
                    <input className="recents-search" value={recentSearch} onChange={(e) => setRecentSearch(e.target.value)}
                      placeholder="Search sessions…" aria-label="Search past sessions" />
                  )}
                  {sessions.length === 0
                    ? <div className="recents-empty">No past sessions yet.</div>
                    : filtered.length === 0
                      ? <div className="recents-empty">No sessions match &ldquo;{recentSearch}&rdquo;.</div>
                      : filtered.map((s) => (
                          <button key={s.session_id} role="menuitem"
                            className={`recent ${s.session_id === sessionId.current ? "active" : ""}`}
                            onClick={() => openSession(s)}>
                            <span className="r-title">{s.title || "Untitled session"}</span>
                            <span className="r-meta">{s.ended ? "ended" : "active"}{s.updated_at ? ` · ${new Date(s.updated_at).toLocaleDateString()}` : ""}</span>
                          </button>
                        ))}
                </div>
              );
            })()}
          </div>
        </div>
      </div>

      <div className="stream" ref={streamRef} onScroll={onStreamScroll} aria-busy={busy}>
        <div className="thread">
          {messages.length === 0 && (
            <div className="intro">
              <h2>{name ? `Hi ${name} — what's on your mind?` : "What's on your mind?"}</h2>
              <p>A conversation you're postponing, a decision that's stalling, a moment you want to get right. Start there — every session ends with one concrete step.</p>
              <div className="starters">
                {STARTERS.map((s) => (
                  <button key={s} type="button" className="starter" disabled={busy}
                    onClick={() => { setDraft(""); runTurn(s); }}>
                    {s}
                  </button>
                ))}
              </div>
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
          {status && <div className="status" aria-live="polite">{status}</div>}
          {/* Screen-reader announcement of the completed reply — a primed polite region
              (streaming token-by-token would flood, so we announce once on done). */}
          <div className="sr-only" aria-live="polite" aria-atomic="true">{announce}</div>

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
        {!atBottom && (
          <button className="to-latest" onClick={jumpToLatest} aria-label="Scroll to latest message">↓ Latest</button>
        )}
        <div className="composer-inner">
          <textarea ref={taRef} value={draft} onChange={onDraftChange} onKeyDown={onKey}
            placeholder={listening ? "Listening…" : "Talk it through…"} rows={1} aria-label="Message your coach" />
          {voiceCaps.stt && !busy && (
            <button className={`mic ${listening ? "on" : ""}`} onClick={toggleMic}
              aria-pressed={listening} aria-label={listening ? "Stop listening" : "Speak to your coach"}>
              <span aria-hidden="true">{listening ? "●" : "🎙"}</span>
            </button>
          )}
          {busy
            ? <button className="send stop" onClick={stop} aria-label="Stop generating">■ Stop</button>
            : <button className="send" onClick={send} disabled={!draft.trim()}>Send</button>}
        </div>
      </div>
    </div>
  );
}
