"use client";

import { useCallback, useEffect, useState } from "react";
import { getConsent, logout, updateConsent, type Consent } from "@/lib/api";
import { useMe } from "@/components/shell";

/* The six DPDP consents (platform models.CONSENT_KEYS). Each is OFF until the
   person turns it on; the engine enforces from the signed claim, so a change here
   rotates the session and bites on the next request. */
const KEYS: { key: keyof Consent; label: string; hint: string }[] = [
  { key: "mood_history", label: "Mood history", hint: "Keep your check-ins so you can see a trend over time." },
  { key: "journal_memory", label: "Journal", hint: "Keep your journal entries. Only you can ever read them." },
  { key: "ai_memory", label: "Coach memory", hint: "Let your coach remember past sessions, so you don't start over each time." },
  { key: "sleep_history", label: "Sleep history", hint: "Keep sleep logs alongside your check-ins." },
  { key: "voice_storage", label: "Voice", hint: "Keep voice recordings from spoken sessions." },
  { key: "model_training", label: "Improve the model", hint: "Allow your (de-identified) data to help improve coaching quality." },
];

export default function SettingsPage() {
  const me = useMe();
  const [consent, setConsent] = useState<Consent | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string>("");
  const [error, setError] = useState("");

  const load = useCallback(() => {
    setLoading(true);
    getConsent().then(setConsent).catch(() => setError("Couldn't load your consents.")).finally(() => setLoading(false));
  }, []);
  useEffect(() => { load(); }, [load]);

  async function toggle(key: keyof Consent, next: boolean) {
    if (busy) return;
    setBusy(key); setError("");
    try {
      setConsent(await updateConsent({ [key]: next } as Partial<Consent>));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't save that.");
    } finally { setBusy(""); }
  }

  async function signOut() { await logout(); window.location.href = "/"; }

  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Settings</div><h1>Settings</h1></div></div>
      <div className="empty">
        <div className="card">
          <h3>Account</h3>
          <p className="placeholder" style={{ marginBottom: 2 }}>{me?.name || "—"}</p>
          <p className="placeholder">{me?.email}</p>
          <div style={{ marginTop: 18 }}>
            <button className="primary" onClick={signOut}>Sign out</button>
          </div>
        </div>

        <div className="card" style={{ marginTop: 16 }}>
          <h3>Privacy &amp; consent</h3>
          <p className="placeholder" style={{ marginBottom: 14 }}>
            Each is off until you turn it on, and you can withdraw any of them at any time —
            withdrawal takes effect on your next request, not later. Your conversations are
            always private to you: your employer only ever sees anonymised counts.
          </p>
          {loading ? <p className="placeholder">Loading…</p> : !consent ? (
            <p className="placeholder">Couldn&rsquo;t load your consents. <button className="tool" onClick={load}>Retry</button></p>
          ) : (
            <div className="consents">
              {KEYS.map(({ key, label, hint }) => (
                <label key={key} className="consent">
                  <input type="checkbox" checked={!!consent[key]} disabled={busy === key}
                    onChange={(e) => toggle(key, e.target.checked)} />
                  <span className="c-copy">
                    <span className="c-label">{label}{busy === key && <span className="placeholder"> · saving…</span>}</span>
                    <span className="c-hint">{hint}</span>
                  </span>
                </label>
              ))}
            </div>
          )}
          {error && <p className="error">{error}</p>}
        </div>
      </div>
    </div>
  );
}
