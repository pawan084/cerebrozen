"use client";

// The web port of iOS AuthForm: Sign in with Apple / Continue with Google /
// email — with a passwordless one-time-code option. Used standalone on /signin
// and embedded inline in the onboarding "Save your space" step. Calls onAuthed()
// once a session exists (the caller decides where to go next).

import { useEffect, useRef, useState } from "react";
import {
  signIn, signUp, requestOtp, verifyOtp, signInApple, signInGoogle,
} from "@/lib/api";
import {
  appleIdentityToken, googleIdToken, NotConfiguredError,
} from "@/lib/social";

type Mode = "signIn" | "signUp";

/** How the session came to exist — callers route fresh accounts through the
 * consent steps, returning users straight in. "otp" may be either (the code
 * path signs up new addresses), so callers treat it as possibly-new. */
export type AuthOutcome = "signIn" | "signUp" | "otp";

export default function AuthPanel({
  initialMode = "signIn",
  requireAgeAttest = false,
  onAuthed,
}: {
  initialMode?: Mode;
  /** Standalone /signin sets this: account creation outside the funnel never
   * passed the 18+ gate the funnel shows at step 1. Embedded funnel use leaves
   * it off — the gate already ran there. */
  requireAgeAttest?: boolean;
  onAuthed: (outcome: AuthOutcome) => void;
}) {
  const [mode, setMode] = useState<Mode>(initialMode);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [useCode, setUseCode] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [ageOk, setAgeOk] = useState(false);
  // Resend cooldown: requesting a second code inside the window burns rate
  // limit for no benefit — the timer says when trying again can work.
  const [resendAt, setResendAt] = useState(0);
  const [nowTick, setNowTick] = useState(0);

  // The last thing the user tried, so the error can offer a real retry instead
  // of making them re-enter everything.
  const lastAction = useRef<(() => Promise<void>) | null>(null);

  function reset(msg = "") {
    setError("");
    setNotice(msg);
  }

  /** fetch() rejects with a bare TypeError when the API is unreachable — the
   * browser's "Failed to fetch" is a developer string, never user copy. */
  function friendlyError(err: any): string {
    const raw = typeof err?.message === "string" ? err.message : "";
    if (err instanceof TypeError || /failed to fetch|load failed|networkerror|network request/i.test(raw)) {
      return "We couldn't reach CereBro just now — it's usually the connection.";
    }
    return raw || "That didn't go through.";
  }

  async function withBusy(fn: () => Promise<void>) {
    lastAction.current = fn;
    setBusy(true);
    reset();
    try {
      await fn();
    } catch (err: any) {
      // A provider that isn't wired yet is an honest notice, not an error.
      if (err instanceof NotConfiguredError) setNotice(err.message);
      else setError(friendlyError(err));
      setBusy(false);
    }
  }

  function retry() {
    const again = lastAction.current;
    if (again) void withBusy(again);
  }

  const doApple = () =>
    withBusy(async () => {
      const { token, name: appleName } = await appleIdentityToken();
      await signInApple(token, appleName);
      onAuthed("otp");
    });

  const doGoogle = () =>
    withBusy(async () => {
      const idToken = await googleIdToken();
      await signInGoogle(idToken);
      onAuthed("otp");
    });

  const resendWait = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));
  useEffect(() => {
    if (!resendAt) return;
    const t = setInterval(() => setNowTick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [resendAt]);
  void nowTick;

  async function submitEmail(e: React.FormEvent) {
    e.preventDefault();
    await withBusy(async () => {
      if (useCode && !codeSent) {
        await requestOtp(email);
        setCodeSent(true);
        setResendAt(Date.now() + 60_000);
        setNotice("Code sent — enter the 6 digits from your email.");
        setBusy(false);
        return;
      }
      if (useCode) { await verifyOtp(email, code); onAuthed("otp"); }
      else if (mode === "signUp") { await signUp(email, password, name); onAuthed("signUp"); }
      else { await signIn(email, password); onAuthed("signIn"); }
    });
  }

  async function resendCode() {
    await withBusy(async () => {
      await requestOtp(email);
      setResendAt(Date.now() + 60_000);
      setNotice("A fresh code is on its way.");
      setBusy(false);
    });
  }

  /** A gentle, honest read on the password — guidance, never a gate beyond the
   * server's own minimum. No trademarked "strength" theatre: length is the
   * only factor that reliably matters, so length is what's measured. */
  const passHint = (() => {
    if (mode !== "signUp" || useCode || !password) return null;
    if (password.length < 8) return { tone: "warn", text: "At least 8 characters." };
    if (password.length < 12) return { tone: "ok", text: "Decent — longer is stronger." };
    return { tone: "good", text: "Good length." };
  })();

  const emailCta = useCode
    ? codeSent ? "Sign in with code" : "Email me a code"
    : mode === "signUp" ? "Create my account" : "Continue with email";

  return (
    <div className="authpanel">
      <button type="button" className="social-btn" onClick={doApple} disabled={busy}>
        <AppleMark /> Sign in with Apple
      </button>
      <button type="button" className="social-btn" onClick={doGoogle} disabled={busy}>
        <GoogleMark /> Continue with Google
      </button>

      <div className="divider"><span>or use email</span></div>

      <div className="segment" role="tablist" aria-label="Sign in or create account">
        <button
          type="button"
          role="tab"
          aria-selected={mode === "signIn"}
          className={mode === "signIn" ? "seg active" : "seg"}
          onClick={() => { setMode("signIn"); reset(); }}
        >
          Sign in
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === "signUp"}
          className={mode === "signUp" ? "seg active" : "seg"}
          onClick={() => { setMode("signUp"); reset(); }}
        >
          Create account
        </button>
      </div>

      <form onSubmit={submitEmail}>
        {mode === "signUp" && !useCode && (
          <label className="field">
            <span>Name</span>
            <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" />
          </label>
        )}
        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
        </label>
        {useCode ? (
          codeSent && (
            <>
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
              <button
                type="button"
                className="linklike"
                disabled={busy || resendWait > 0}
                onClick={resendCode}
              >
                {resendWait > 0 ? `Resend code in ${resendWait}s` : "Resend code"}
              </button>
            </>
          )
        ) : (
          <label className="field">
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete={mode === "signUp" ? "new-password" : "current-password"}
            />
            {passHint && (
              <span className={`pass-hint pass-${passHint.tone}`} role="status">{passHint.text}</span>
            )}
          </label>
        )}

        {/* Outside the funnel there was no 18+ moment at all — an account could
            exist without the confirmation every other door requires. */}
        {requireAgeAttest && mode === "signUp" && !useCode && (
          <label className="field check-line">
            <input
              type="checkbox"
              checked={ageOk}
              onChange={(e) => setAgeOk(e.target.checked)}
              required
            />
            <span>I&apos;m 18 or older. CereBro is built for adults.</span>
          </label>
        )}

        {notice && <p className="sub" role="status">{notice}</p>}
        {error && (
          <div className="error-note" role="alert">
            <p>{error}</p>
            <button type="button" className="linklike" onClick={retry} disabled={busy}>
              Try again
            </button>
          </div>
        )}

        <button className="btn pill-cta" style={{ width: "100%" }} disabled={busy}>
          {busy ? "One moment…" : emailCta}
        </button>
      </form>

      <div className="auth-links">
        <button
          type="button"
          className="linklike"
          onClick={() => { setUseCode(!useCode); setCodeSent(false); setCode(""); reset(); }}
        >
          {useCode ? "Use a password instead" : "Sign in without a password"}
        </button>
      </div>
    </div>
  );
}

function AppleMark() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M16.36 12.9c.02 2.5 2.2 3.34 2.22 3.35-.02.06-.35 1.2-1.15 2.37-.69 1.02-1.4 2.03-2.53 2.05-1.1.02-1.46-.65-2.72-.65s-1.66.63-2.7.67c-1.09.04-1.92-1.1-2.62-2.11-1.42-2.06-2.51-5.83-1.05-8.38.72-1.27 2.02-2.07 3.43-2.09 1.07-.02 2.08.72 2.73.72.65 0 1.88-.89 3.17-.76.54.02 2.05.22 3.02 1.64-.08.05-1.8 1.05-1.78 3.13zM14.3 5.4c.58-.7.97-1.67.86-2.64-.83.03-1.84.55-2.44 1.25-.54.62-1.01 1.61-.88 2.56.93.07 1.88-.47 2.46-1.17z" />
    </svg>
  );
}

function GoogleMark() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0012 23z" />
      <path fill="#FBBC05" d="M5.84 14.1a6.6 6.6 0 010-4.2V7.06H2.18a11 11 0 000 9.88l3.66-2.84z" />
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 002.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38z" />
    </svg>
  );
}
