"use client";

/* Invitation acceptance: the link from the invite cards lands here
   (/accept?token=…). Name + password → account created → signed in. */

import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { acceptInvitation } from "@/lib/api";

function AcceptForm() {
  const token = useSearchParams().get("token") ?? "";
  const router = useRouter();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const password = String(data.get("password"));
    if (password !== String(data.get("confirm"))) {
      setError("Passwords don't match.");
      return;
    }
    setError("");
    setBusy(true);
    try {
      await acceptInvitation(
        String(data.get("token")),
        String(data.get("name")),
        password,
      );
      router.replace("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap card">
      <h2>Join your organization</h2>
      <p className="hint" style={{ marginTop: 6 }}>
        You&apos;ve been invited to CereBroZen. Choose how you&apos;ll sign in — the
        invitation is single-use and holds your seat until it expires.
      </p>
      <form className="stack" onSubmit={submit} style={{ marginTop: 12 }}>
        {!token && (
          <label>Invitation token<input name="token" required /></label>
        )}
        {token && <input type="hidden" name="token" value={token} />}
        <label>Your name<input name="name" required autoComplete="name" /></label>
        <label>
          Password
          <input name="password" type="password" required minLength={10} autoComplete="new-password" />
        </label>
        <label>
          Confirm password
          <input name="confirm" type="password" required minLength={10} autoComplete="new-password" />
        </label>
        <button className="primary" disabled={busy}>
          {busy ? "…" : "Accept invitation"}
        </button>
        {error && <p className="error">{error}</p>}
      </form>
    </div>
  );
}

export default function AcceptPage() {
  return (
    <div className="shell">
      <header className="topbar">
        <span className="wordmark">CereBr<em>o</em>Zen · admin</span>
      </header>
      <Suspense fallback={null}>
        <AcceptForm />
      </Suspense>
    </div>
  );
}
