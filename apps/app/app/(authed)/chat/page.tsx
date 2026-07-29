"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { OracleWidget, oracleAvailable, oracleStream } from "@/lib/oracle";
import { AppHeader } from "@/components/AppHeader";
import { CrisisLines } from "@/components/CrisisLines";
import type { CrisisLine } from "@/lib/crisis";

const STARTERS = [
  "I feel anxious and I don't know why",
  "Help me wind down before bed",
  "I want to talk through a hard day",
  "Just two minutes to reset",
];

type Msg = { id: string; role: "user" | "assistant"; text: string; widget?: OracleWidget | null };
type Suggestion = { label: string; action: string };
type CrisisInfo = { message?: string; lines?: CrisisLine[] };
// A paused write tool. `summary` is what the server *should* send; `tool`/`args`
// are what it always sends, and the card falls back to them so nobody approves
// an account write blind.
type ConfirmReq = { thread_id: string; summary?: string; tool?: string; args?: Record<string, unknown> };

const TOOL_ACTIONS: Record<string, string> = {
  log_mood: "save a mood check-in",
  save_journal: "save a journal entry",
  log_sleep: "save last night's sleep diary",
};

function confirmHeadline(req: ConfirmReq): string {
  if (req.summary?.trim()) return req.summary;
  const action = req.tool && TOOL_ACTIONS[req.tool];
  if (action) return `The companion wants to ${action}.`;
  if (req.tool) return `The companion wants to run “${req.tool}” on your account.`;
  return "The companion wants to change something in your account — it didn't say what.";
}

function confirmDetail(req: ConfirmReq): string {
  const args = Object.entries(req.args ?? {}).filter(([, v]) => v !== "" && v !== null && v !== undefined);
  if (args.length) return args.map(([k, v]) => `${k.replace(/_/g, " ")}: ${String(v)}`).join(" · ");
  if (req.tool) return "No details came with this request.";
  return "Nothing here describes the change — if you're unsure, choose Not now.";
}

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
  const [confirmReq, setConfirmReq] = useState<ConfirmReq | null>(null);
  const [crisis, setCrisis] = useState<CrisisInfo | null>(null);
  const [started, setStarted] = useState(false);
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
        const block = ev.resources;
        setCrisis({ message: block?.message, lines: block?.lines ?? block?.resources });
      } else if (ev.type === "tool_confirm") {
        setConfirmReq({ thread_id: ev.thread_id, summary: ev.summary, tool: ev.tool, args: ev.args });
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

  function begin(text?: string) {
    setStarted(true);
    if (text) void send(text);
  }

  return (
    <>
      <AppHeader eyebrow="Talk" title="A space to be heard" />
      <div className="page-body">
      {crisis && (
        <div className="crisis" role="alert">
          <strong>{crisis.message || "If things feel heavy right now, you deserve support."}</strong>
          {/* Tele-MANAS leads, every number dials — and the conversation is never blocked. */}
          <CrisisLines lines={crisis.lines?.length ? crisis.lines : undefined} compact />
          <div className="row" style={{ gap: 10, marginTop: 10 }}>
            <Link className="btn ghost" href="/support" style={{ padding: "6px 14px" }}>More ways to get help</Link>
            <button className="btn ghost" style={{ padding: "6px 14px" }} onClick={() => setCrisis(null)}>
              Dismiss
            </button>
          </div>
        </div>
      )}

      {!started && messages.length === 0 ? (
        <>
          {/* One honest CTA: there is no voice session on the web, so there is no
              live affordance to imply one (the footnote below says where voice lives). */}
          <section className="talk-hero cz-in">
            <div className="talk-orb" aria-hidden="true" />
            <h2>I'm here whenever you're ready</h2>
            <p>Type whatever's on your mind — no pressure to have the right words.</p>
            <div className="talk-actions">
              <button className="pill-btn" onClick={() => begin()}>Start talking</button>
            </div>
            <p className="footnote" style={{ marginTop: 14 }}>
              Voice conversations live in the iOS app. Here, the companion listens in writing.
            </p>
          </section>
          <div className="cz-in cz-d1">
            <h2 className="serif-h" style={{ marginBottom: 14 }}>Not sure where to start?</h2>
            {STARTERS.map((s) => (
              <button key={s} className="suggest-row" onClick={() => begin(s)}>{s}</button>
            ))}
          </div>
        </>
      ) : (
        <>
          <div className="ai-note cz-in" role="note">
            <span className="ai-note-dot" aria-hidden="true">ⓘ</span>
            AI companion — not a therapist or crisis service. It listens and guides; it can't
            diagnose, prescribe, or handle emergencies.
          </div>
          <section className="card chatbox cz-in cz-d1" aria-label="Conversation">
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
            <strong>{confirmHeadline(confirmReq)}</strong>
            <p className="sub">{confirmDetail(confirmReq)}</p>
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
      )}
      </div>
    </>
  );
}
