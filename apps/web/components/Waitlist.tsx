"use client";

import { useState } from "react";
import { API_URL } from "@/lib/api";

export default function Waitlist() {
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  // Honeypot: hidden from humans; bots that auto-fill it get a fake success.
  const [company, setCompany] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!email) return;
    if (company) {
      // Bot filled the honeypot — pretend it worked, skip the API.
      setMsg("You're in. We'll send a calm note when it's ready.");
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
      setEmail("");
    } catch {
      setMsg("Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
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
      <div className="wl-msg">{msg}</div>
    </div>
  );
}
