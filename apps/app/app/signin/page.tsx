"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { requestOtp, signIn, verifyOtp } from "@/lib/api";

export default function SignIn() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [useCode, setUseCode] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (useCode && !codeSent) {
        await requestOtp(email);
        setCodeSent(true);
        setNotice("Code sent — enter the 6 digits from your email.");
        setBusy(false);
        return;
      }
      if (useCode) {
        await verifyOtp(email, code);
      } else {
        await signIn(email, password);
      }
      router.replace("/home");
    } catch (err: any) {
      setError(err.message || "Sign-in failed.");
      setBusy(false);
    }
  }

  function toggleMode() {
    setUseCode(!useCode);
    setCodeSent(false);
    setCode("");
    setNotice("");
    setError("");
  }

  const cta = useCode ? (codeSent ? "Sign in with code" : "Email me a code") : "Sign in";

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
        {useCode ? (
          codeSent && (
            <label className="field">
              <span>Code</span>
              <input
                type="text"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
                autoComplete="one-time-code"
              />
            </label>
          )
        ) : (
          <label className="field">
            <span>Password</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
          </label>
        )}
        {notice && <p className="sub" role="status">{notice}</p>}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn" style={{ width: "100%" }} disabled={busy}>
          {busy ? "One moment…" : cta}
        </button>
        <p className="swap">
          <button type="button" className="linklike" onClick={toggleMode}>
            {useCode ? "Use a password instead" : "Sign in without a password"}
          </button>
        </p>
        <p className="swap">
          New here? <Link href="/signup">Create your space</Link>
        </p>
      </form>
    </div>
  );
}
