"use client";

import { useState } from "react";
import { API_URL } from "@/lib/api";

export default function Waitlist() {
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState("");
  // Success swaps the form out entirely, so there's no half-filled field left to
  // resubmit; errors keep the form (with what you typed) so you can retry.
  const [joined, setJoined] = useState(false);
  const [busy, setBusy] = useState(false);
  // Honeypot: hidden from humans; bots that auto-fill it get a fake success.
  const [company, setCompany] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!email) return;
    if (company) {
      // Bot filled the honeypot — pretend it worked, skip the API.
      setMsg("You're in. We'll send a calm note when it's ready.");
      setJoined(true);
      setEmail("");
      return;
    }
    setBusy(true);
    setMsg("");
    try {
      const res = await fetch(`${API_URL}/waitlist`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const data = await res.json();
      setMsg(
        data.status === "already_joined"
          ? "You're already on the list — we'll be in touch."
          : "You're in. We'll send a calm note when it's ready."
      );
      setJoined(true);
      setEmail("");
    } catch {
      setMsg("Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  if (joined) {
    return (
      <div className="wl-done">
        <div className="wl-done-title">Thank you</div>
        {/* Polite live region: the confirmation replaces the form, so screen
            readers are told what happened rather than losing focus context. */}
        <div className="wl-msg" role="status" aria-live="polite">{msg}</div>
      </div>
    );
  }

  return (
    <div>
      <form className="wl-form" onSubmit={submit}>
        <input
          type="text"
          name="company"
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          tabIndex={-1}
          autoComplete="off"
          aria-hidden="true"
          style={{
            position: "absolute",
            left: "-9999px",
            width: 1,
            height: 1,
            opacity: 0,
            pointerEvents: "none",
          }}
        />
        <input
          type="email"
          required
          placeholder="you@email.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-label="Email address"
        />
        <button className="btn btn-primary" type="submit" disabled={busy}>
          {busy ? "Joining…" : "Join the waitlist"}
        </button>
      </form>
      <div className="wl-msg" role="status" aria-live="polite">{msg}</div>
    </div>
  );
}
