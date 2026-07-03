"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { signUp } from "@/lib/api";

export default function SignUp() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await signUp(email, password, name);
      router.replace("/home");
    } catch (err: any) {
      setError(err.message || "Sign-up failed.");
      setBusy(false);
    }
  }

  return (
    <div className="authwrap">
      <form className="authcard" onSubmit={submit}>
        <div className="brand"><span className="dot" /> CereBro</div>
        <h1>Create your space</h1>
        <p className="sub">Private by default. Everything here stays yours.</p>
        <label className="field">
          <span>Name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} required autoComplete="name" />
        </label>
        <label className="field">
          <span>Email</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} autoComplete="new-password" />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn" style={{ width: "100%" }} disabled={busy}>
          {busy ? "Creating…" : "Create account"}
        </button>
        <p className="swap">
          Already have a space? <Link href="/signin">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
