"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { OracleWidget, oracleAvailable, oracleStream } from "@/lib/oracle";
import { PageHeader } from "@/components/ui";

type Msg = { id: string; role: "user" | "assistant"; text: string; widget?: OracleWidget | null };
type Suggestion = { label: string; action: string };
type CrisisInfo = { message?: string; resources?: { name: string; number: string }[] };

// Where an inline activity lands on the web; unmapped kinds stay app-only.
const WIDGET_LINKS: Record<string, string> = {
  mood_check: "/home",
  mini_journal: "/journal",
  journal: "/journal",
  sleep_checkin: "/sleep",
};

// crypto.randomUUID() needs a secure context (absent on plain-http origins
// like the e2e stack) — local bubble keys don't need cryptographic ids anyway.
let uidCounter = 0;
const uid = () => `local-${Date.now()}-${uidCounter++}`;

export default function Chat() {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState("");
  const [busy, setBusy] = useState(false);
  const [useOracle, setUseOracle] = useState(false);
  const [threadId, setThreadId] = useState("web");
  const [confirmReq, setConfirmReq] = useState<{ thread_id: string; summary?: string } | null>(null);
  const [crisis, setCrisis] = useState<CrisisInfo | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api("/auth/me").then((me) => setThreadId(`web-${me.id}`)).catch(() => {});
    api<any[]>("/chat")
      .then((h) => setMessages(h.map((m) => ({ id: m.id, role: m.role, text: m.text }))))
      .catch(() => {});
    oracleAvailable().then(setUseOracle);
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streaming]);

  function push(msg: Msg) {
    setMessages((m) => [...m, msg]);
  }

  async function consume(stream: AsyncGenerator<any>) {
    let acc = "";
    let widget: OracleWidget | null = null;
    for await (const ev of stream) {
      if (ev.type === "token") {
        acc += ev.text;
        setStreaming(acc);
      } else if (ev.type === "widget") {
        widget = ev.widget;
      } else if (ev.type === "crisis") {
        setCrisis(ev.resources ?? {});
      } else if (ev.type === "tool_confirm") {
        setConfirmReq({ thread_id: ev.thread_id, summary: ev.summary });
      } else if (ev.type === "done" || ev.type === "error") {
        const text = ev.type === "done" ? ev.text || acc : acc || ev.detail;
        if (text.trim() || widget) {
          push({ id: uid(), role: "assistant", text: text.trim(), widget });
        }
        acc = "";
        widget = null;
      }
    }
    // Stream ended while paused for confirmation — keep the card, drop the bubble.
    setStreaming("");
  }

  async function send(text: string) {
    const t = text.trim();
    if (!t || busy) return;
    setBusy(true);
    setInput("");
    setSuggestions([]);
    push({ id: uid(), role: "user", text: t });
    try {
      if (useOracle) {
        await consume(oracleStream("/oracle/messages", { text: t, thread_id: threadId }));
      } else {
        const reply = await api<any>("/chat/messages", {
          method: "POST",
          body: JSON.stringify({ text: t }),
        });
        push({ id: reply.reply.id, role: "assistant", text: reply.reply.text, widget: reply.widget });
        const sugg: Suggestion[] = reply.suggestions ?? [];
        if (sugg.some((s) => s.action === "crisis")) setCrisis({});
        setSuggestions(sugg.filter((s) => s.action !== "crisis"));
      }
    } catch (err: any) {
      push({
        id: uid(),
        role: "assistant",
        text: err?.message === "unauthorized" ? "Your session expired — please sign in again."
          : "I couldn't reach the companion just now — please try again.",
      });
    } finally {
      setBusy(false);
    }
  }

  async function resolveConfirm(approved: boolean) {
    const req = confirmReq;
    if (!req) return;
    setConfirmReq(null);
    setBusy(true);
    try {
      await consume(oracleStream("/oracle/confirm", { thread_id: req.thread_id, approved }));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader eyebrow="AI voice companion" title="Talk" />
      <div className="ai-note" role="note">
        <span className="ai-note-dot" aria-hidden="true">ⓘ</span>
        AI companion — not a therapist or crisis service. It listens and guides; it can't
        diagnose, prescribe, or handle emergencies.
      </div>

      {crisis && (
        <div className="crisis" role="alert">
          <strong>{crisis.message || "If things feel heavy right now, you deserve support."}</strong>
          <br />
          {(crisis.resources && crisis.resources.length > 0
            ? crisis.resources
            : [
                { name: "Emergency services (India)", number: "112" },
                { name: "KIRAN mental-health helpline", number: "1800-599-0019" },
                { name: "Find a helpline", number: "https://findahelpline.com" },
              ]
          ).map((r) => (
            <span key={r.name}>
              {r.name}: <strong>{r.number}</strong> ·{" "}
            </span>
          ))}
          <button className="btn ghost" style={{ marginLeft: 8, padding: "4px 12px" }} onClick={() => setCrisis(null)}>
            Dismiss
          </button>
        </div>
      )}

      <section className="card chatbox" aria-label="Conversation">
        {messages.length === 0 && !streaming && (
          <p className="sub">What's on your mind? The companion listens first, then offers one small step.</p>
        )}
        {messages.map((m) => (
          <div key={m.id} className={`msg ${m.role === "user" ? "user" : "ai"}`}>
            <p>{m.text}</p>
            {m.widget && (
              <div className="widgetcard">
                <span className="eyebrow">Suggested activity</span>
                <strong>{m.widget.title}</strong>
                <p className="sub">{m.widget.description}</p>
                {WIDGET_LINKS[m.widget.widget_kind] ? (
                  <Link className="btn ghost" href={WIDGET_LINKS[m.widget.widget_kind]}>Open</Link>
                ) : (
                  <p className="footnote">This one lives in the iOS app.</p>
                )}
              </div>
            )}
          </div>
        ))}
        {streaming && (
          <div className="msg ai">
            <p>{streaming}<span className="cursor">▍</span></p>
          </div>
        )}
        {confirmReq && (
          <div className="widgetcard" role="alertdialog" aria-label="Confirm action">
            <span className="eyebrow">The companion wants to act</span>
            <strong>{confirmReq.summary || "Approve this action?"}</strong>
            <div className="row" style={{ marginTop: 8 }}>
              <button className="btn" onClick={() => resolveConfirm(true)}>Approve</button>
              <button className="btn ghost" onClick={() => resolveConfirm(false)}>Not now</button>
            </div>
          </div>
        )}
        <div ref={endRef} />
      </section>

      {suggestions.length > 0 && (
        <div className="chips">
          {suggestions.map((s) => (
            <button key={s.label} className="chip" onClick={() => send(s.label)}>{s.label}</button>
          ))}
        </div>
      )}

      <form
        className="composer"
        onSubmit={(e) => {
          e.preventDefault();
          void send(input);
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Say what's on your mind…"
          aria-label="Message"
        />
        <button className="btn" disabled={busy || !input.trim()}>
          {busy ? "…" : "Send"}
        </button>
      </form>
      <p className="footnote">
        Voice conversations live in the iOS app. Free accounts have a daily message allowance.
      </p>
    </>
  );
}
