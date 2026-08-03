"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { FreeLimitError, api } from "@/lib/api";
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
// The offline fallback list and the tel:/http href rule used to live here. Both
// moved to `lib/crisis` + <CrisisLines>, so chat, journal and /support cannot
// drift apart on "Tele-MANAS first, every number tappable".

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

// Where an inline activity lands on the web; unmapped kinds stay app-only
// (mirror of Android TalkScreen widgetRoute — add mappings, never remove
// kinds: the honest "lives in the app" fallback must survive).
const WIDGET_LINKS: Record<string, string> = {
  mood_check: "/home",
  mini_journal: "/journal",
  journal: "/journal",
  sleep_checkin: "/sleep",
  breathing: "/games",
  grounding: "/games",
  one_good_thing: "/journal",
  intention_set: "/journal",
};

// "Try together" — rule-based structured exercises offered up front (evidence
// F3: structure beats open-ended chat); client-only links, no LLM dependency.
const TRY_TOGETHER = [
  { label: "Box breathing", detail: "4·4·4·4 — follow the orb", href: "/games" },
  { label: "5-4-3-2-1 grounding", detail: "Anchor through the senses", href: "/games" },
  { label: "One good thing", detail: "A 30-second journal entry", href: "/journal" },
];

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
  // The free daily cap, shown as its own calm card rather than an error bubble.
  const [freeLimit, setFreeLimit] = useState<FreeLimitError | null>(null);
  const [started, setStarted] = useState(false);
  // The message that failed to send, so "Try again" can resend it verbatim —
  // without this, an error meant retyping from memory on a bad connection.
  const [failedText, setFailedText] = useState<string | null>(null);
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
    setFailedText(null);
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
      // The free cap is a product state, not a failure. Before this it fell
      // into the generic branch below and the server's explanation — the
      // number, the reset time — never reached anyone.
      if (err instanceof FreeLimitError) {
        setFreeLimit(err);
      } else {
        if (err?.message !== "unauthorized") setFailedText(t);
        push({
          id: uid(),
          role: "assistant",
          text: err?.message === "unauthorized" ? "Your session expired — please sign in again."
            : "I couldn't reach the companion just now — your words are kept below.",
        });
      }
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
      {/* Free daily cap. A card, not an error bubble: it states the number, when
          the count clears in LOCAL time, and what still works meanwhile. */}
      {freeLimit && (
        <div className="card" style={{ marginBottom: 14 }} role="status">
          <h2 style={{ fontSize: 17 }}>Today&apos;s free messages are used</h2>
          <p className="sub" style={{ maxWidth: 560 }}>
            {freeLimit.limit > 0 && <>Free includes {freeLimit.limit} messages a day; y</>}
            {freeLimit.limit === 0 && <>Y</>}our count resets at {freeLimit.resetText}. Journal,
            sleep, breathing and your plan are all still here in the meantime.
          </p>
          <div className="row" style={{ gap: 10, marginTop: 10 }}>
            <Link className="btn" href="/account" style={{ padding: "6px 14px" }}>See plans</Link>
            <button className="btn ghost" style={{ padding: "6px 14px" }} onClick={() => setFreeLimit(null)}>
              Dismiss
            </button>
          </div>
        </div>
      )}
      {crisis && (
        <div className="crisis" role="alert">
          <strong>{crisis.message || "If things feel heavy right now, you deserve support."}</strong>
          {/* Tele-MANAS leads, every number dials — and the conversation is never
              blocked. Region-aware when the server sent a block; the component's
              own list otherwise, so this never renders empty. */}
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
            <h2>I&apos;m here whenever you&apos;re ready</h2>
            {/* Says where voice is: the browser client has text only, and the
                orb above otherwise reads like a mic. */}
            <p>Just type — no pressure to have the right words. Voice arrives with the mobile apps.</p>
            <div className="talk-actions">
              <button className="pill-btn" onClick={() => begin()}>Start talking</button>
            </div>
            <p className="footnote" style={{ marginTop: 14 }}>
              Voice conversations arrive with the mobile apps. Here, the companion listens in writing.
            </p>
          </section>
          <div className="dash-grid" style={{ gridTemplateColumns: "minmax(0,1fr) minmax(0,1fr)" }}>
            <div className="cz-in cz-d1">
              <h2 className="serif-h" style={{ marginBottom: 14 }}>Not sure where to start?</h2>
              {STARTERS.map((s) => (
                <button key={s} className="suggest-row" onClick={() => begin(s)}>{s}</button>
              ))}
            </div>
            {/* The "Try together" rail (WEB_PARITY Wave C, iOS/Android parity):
                a structured exercise offered before a conversation, per the
                rule-based-first evidence. The slot it replaced was a hardcoded
                "Recent conversations" list — deleted in Wave A as a fake. */}
            <div className="cz-in cz-d2">
              <h2 className="serif-h" style={{ marginBottom: 14 }}>Try together</h2>
              {TRY_TOGETHER.map((t) => (
                <Link key={t.label} href={t.href} className="suggest-row" style={{ display: "block", textDecoration: "none" }}>
                  <strong>{t.label}</strong>
                  <span style={{ color: "var(--muted)" }}> — {t.detail}</span>
                </Link>
              ))}
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="ai-note cz-in" role="note">
            <span className="ai-note-dot" aria-hidden="true">ⓘ</span>
            AI companion — not a therapist or crisis service. It listens and guides; it can&apos;t
            diagnose, prescribe, or handle emergencies.
          </div>
          {/* aria-live polite: streamed tokens and new replies are announced
              once settled; without it the conversation is silent to a screen
              reader unless they hunt for it. */}
          <section className="card chatbox cz-in cz-d1" aria-label="Conversation" aria-live="polite">
        {messages.length === 0 && !streaming && (
          <p className="sub">What&apos;s on your mind? The companion listens first, then offers one small step.</p>
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
                  <p className="footnote">This one arrives with the mobile apps.</p>
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

      {failedText && (
        <div className="chips">
          <button className="chip" onClick={() => void send(failedText)} disabled={busy}>
            ↻ Try sending again
          </button>
        </div>
      )}

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
        Voice conversations arrive with the mobile apps. Free accounts have a daily message allowance.
      </p>
        </>
      )}
      </div>
    </>
  );
}
