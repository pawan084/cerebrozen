"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { signIn } from "@/lib/api";

/**
 * AUTH-01 — Administrator sign in.
 *
 * This screen shipped deliberately inert on 2026-08-12: it rendered the access
 * flow and authenticated nobody, because there was no backend behind it and a
 * control that appears to sign someone in implies a gate that does not exist.
 * The backend now exists (`/org`, models/organization.py), so the form is real.
 *
 * What is still NOT here, and should not be added until it is genuinely built:
 * the prototype's "Continue with SSO" and "Open demo workspace" buttons. There
 * is no identity provider and no demo tenant, so both would be the same lie in
 * a new place. Password sign-in is the one path that works.
 *
 * Being signed in is not the same as having access: the backend answers 403 to
 * any account that administers no organisation, and the portal explains that
 * rather than pretending the credentials were wrong.
 */
export default function SignInPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await signIn(email.trim(), password);
      router.replace("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't sign in just now — try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="eyebrow">Administrator access</div>
      <h1>Govern wellbeing programmes without seeing personal wellbeing data.</h1>
      <p className="lede">
        Secure access for benefits, programme, technical, privacy, finance and audit
        administrators.
      </p>

      <div className="grid cols-2">
        <div className="card">
          <h2>Sign in</h2>
          <p className="tiny">Use your organisation administrator account.</p>
          <form onSubmit={submit}>
            <label style={{ display: "block", marginTop: 20 }}>
              <span className="label">Work email</span>
              <input
                className="field"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@organisation.com"
              />
            </label>
            <label style={{ display: "block", marginTop: 12 }}>
              <span className="label">Password</span>
              <input
                className="field"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            {error ? (
              <div className="notice danger" role="alert" style={{ marginTop: 14 }}>
                <span aria-hidden="true">!</span>
                <div>{error}</div>
              </div>
            ) : null}
            <div className="toolbar">
              <button type="submit" className="btn" disabled={busy}>
                {busy ? "Signing in…" : "Sign in"}
              </button>
            </div>
          </form>
          <p className="tiny" style={{ marginTop: 12 }}>
            SSO and multi-factor sign-in are not built yet. When they are, they will replace
            this form rather than sit beside it.
          </p>
        </div>

        <div className="card tint">
          <h2>What an administrator can reach</h2>
          <p className="tiny">
            Every administrator action is role-restricted and recorded. No role, however
            senior, reaches a member&rsquo;s chat, journal, mood history, sleep data or safety
            plan &mdash; that boundary is enforced by the absence of any read path, not by a
            permission you could be granted.
          </p>
          <div className="toolbar">
            <Link className="btn secondary" href="/roles">
              Roles &amp; permissions
            </Link>
            <Link className="btn secondary" href="/privacy">
              Privacy centre
            </Link>
          </div>
        </div>
      </div>
    </>
  );
}
