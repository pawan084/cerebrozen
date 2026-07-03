"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { signIn } from "@/lib/api";

export default function SignIn() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await signIn(email, password);
      router.replace("/home");
    } catch (err: any) {
      setError(err.message || "Sign-in failed.");
      setBusy(false);
    }
  }

  return (
    <div className="authwrap">
      <form className="authcard" onSubmit={submit}>
        <div className="brand"><span className="dot" /> CereBro</div>
        <h1>Welcome back</h1>
        <p className="sub">Your space, on the web — check-ins, journal, and sleep.</p>
        <label className="field">
          <span>Email</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn" style={{ width: "100%" }} disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
        <p className="swap">
          New here? <Link href="/signup">Create your space</Link>
        </p>
      </form>
    </div>
  );
}
