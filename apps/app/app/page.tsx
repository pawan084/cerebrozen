"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { getTokens, login } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (getTokens()) router.replace("/coach");
  }, [router]);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setBusy(true);
    const data = new FormData(e.currentTarget);
    try {
      await login(String(data.get("email")), String(data.get("password")));
      router.replace("/coach");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed");
      setBusy(false);
    }
  }

  return (
    <main className="center">
      <div className="card">
        <div className="wordmark">CereBr<span className="o">o</span>Zen</div>
        <h1>Welcome back</h1>
        <p className="sub">Sign in to talk with your coach.</p>
        <form onSubmit={submit}>
          <label>
            Work email
            <input name="email" type="email" required autoComplete="username" placeholder="you@company.com" />
          </label>
          <label>
            Password
            <input name="password" type="password" required autoComplete="current-password" />
          </label>
          <button className="primary" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
          {error && <p className="error">{error}</p>}
        </form>
      </div>
    </main>
  );
}
