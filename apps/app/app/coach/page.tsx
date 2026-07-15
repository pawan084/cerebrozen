"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { getTokens, logout, me } from "@/lib/api";
import { coachTurn, AuthExpired } from "@/lib/coach";

type Msg = { who: "you" | "coach"; text: string };

export default function CoachPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [messages, setMessages] = useState<Msg[]>([]);
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const sessionId = useRef<string | null>(null);
  const streamRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!getTokens()) {
      router.replace("/");
      return;
    }
    me().then((m) => {
      if (!m) router.replace("/");
      else setName((m.name || m.email).split(" ")[0].split("@")[0]);
    });
  }, [router]);

  useEffect(() => {
    streamRef.current?.scrollTo({ top: streamRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, status]);

  async function send() {
    const text = draft.trim();
    if (!text || busy) return;
    setDraft("");
    setBusy(true);
    setStatus("");
    setMessages((m) => [...m, { who: "you", text }, { who: "coach", text: "" }]);
    const coachIdx = messages.length + 1; // index of the empty coach msg we just added

    try {
      for await (const ev of coachTurn(text, sessionId.current)) {
        if (ev.type === "status") {
          setStatus(ev.msg);
        } else if (ev.type === "token") {
          setStatus("");
          setMessages((m) => {
            const next = [...m];
            if (next[coachIdx]) next[coachIdx] = { who: "coach", text: next[coachIdx].text + ev.text };
            return next;
          });
        } else if (ev.type === "done") {
          if (ev.session_id) sessionId.current = ev.session_id;
          setStatus("");
          setMessages((m) => {
            const next = [...m];
            const cur = next[coachIdx]?.text ?? "";
            if (!cur.trim() && ev.response_to_user) {
              next[coachIdx] = { who: "coach", text: ev.response_to_user };
            }
            return next;
          });
        } else if (ev.type === "error") {
          throw new Error(ev.detail);
        }
      }
    } catch (err) {
      if (err instanceof AuthExpired) {
        await logout();
        router.replace("/");
        return;
      }
      setMessages((m) => {
        const next = [...m];
        next[coachIdx] = {
          who: "coach",
          text: "The coach is unreachable right now — your message wasn't lost. Try again in a moment.",
        };
        return next;
      });
    } finally {
      setStatus("");
      setBusy(false);
    }
  }

  function onKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  }

  async function signOut() {
    await logout();
    router.replace("/");
  }

  return (
    <div className="shell">
      <div className="topbar">
        <span className="wordmark">CereBr<span className="o">o</span>Zen</span>
        <span className="who">{name ? `Signed in as ${name}` : ""}</span>
        <button className="linkbtn" onClick={signOut}>Sign out</button>
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
                {m.text || (m.who === "coach" && busy ? (
                  <span className="dots"><span>·</span><span>·</span><span>·</span></span>
                ) : "")}
              </div>
            </div>
          ))}
          {status && <div className="status">{status}</div>}
        </div>
      </div>

      <div className="composer">
        <div className="composer-inner">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKey}
            placeholder="Talk it through…"
            rows={1}
          />
          <button className="send" onClick={send} disabled={busy || !draft.trim()}>Send</button>
        </div>
      </div>
    </div>
  );
}
