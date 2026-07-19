"use client";

import { useCallback, useEffect, useState } from "react";
import { getConsent, logout, updateConsent, updateProfile, type Consent } from "@/lib/api";
import { useMe } from "@/components/shell";
import { YourData } from "@/components/your-data";
import { getThemeChoice, setThemeChoice, type ThemeChoice } from "@/lib/theme";
import { disableLock, enableLock, isLockOn, lockSupported } from "@/lib/lock";

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
  const [theme, setTheme] = useState<ThemeChoice>("dark");

  const load = useCallback(() => {
    setLoading(true);
    getConsent().then(setConsent).catch(() => setError("Couldn't load your consents.")).finally(() => setLoading(false));
  }, []);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { setTheme(getThemeChoice()); }, []);

  function pickTheme(c: ThemeChoice) { setTheme(c); setThemeChoice(c); }

  const [persona, setPersona] = useState<string>("");
  const [personaBusy, setPersonaBusy] = useState(false);
  useEffect(() => { setPersona(me?.companion ?? ""); }, [me?.companion]);
  async function pickPersona(p: string) {
    if (personaBusy) return;
    const prev = persona;
    setPersona(p); setPersonaBusy(true);
    try { await updateProfile({ companion: p }); }
    catch { setPersona(prev); }
    finally { setPersonaBusy(false); }
  }

  const [lockOn, setLockOn] = useState(false);
  const [lockBusy, setLockBusy] = useState(false);
  useEffect(() => { setLockOn(isLockOn()); }, []);
  async function toggleLock() {
    if (lockBusy) return;
    setLockBusy(true);
    try {
      if (lockOn) { disableLock(); setLockOn(false); }
      else { setLockOn(await enableLock()); }
    } finally { setLockBusy(false); }
  }

  async function toggle(key: keyof Consent, next: boolean, label: string) {
    if (busy) return;
    // Withdrawing a data-keeping consent stops new entries being kept — confirm it.
    const keepsData = key === "journal_memory" || key === "mood_history" || key === "sleep_history" || key === "ai_memory" || key === "voice_storage";
    if (!next && keepsData &&
      !window.confirm(`Turn off "${label}"? New entries won't be kept while it's off (this toggle doesn't delete anything you've already saved — use "Your data" for that).`)) {
      return;
    }
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
          <h3>Coach style</h3>
          <p className="placeholder" style={{ marginBottom: 14 }}>
            How your coach speaks to you. You can change this any time.
          </p>
          <div className="persona-grid" role="radiogroup" aria-label="Coach style">
            {[
              ["Calm Guide", "Steady, unhurried, grounding."],
              ["Warm Friend", "Encouraging and kind."],
              ["Straight Talker", "Direct, no fluff."],
              ["Quiet Coach", "Few words, lots of space."],
            ].map(([key, desc]) => (
              <button key={key} type="button" role="radio" aria-checked={persona === key} disabled={personaBusy}
                className={`persona ${persona === key ? "on" : ""}`} onClick={() => pickPersona(key)}>
                <span className="persona-t">{key}</span>
                <span className="persona-d">{desc}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="card" style={{ marginTop: 16 }}>
          <h3>Appearance</h3>
          <p className="placeholder" style={{ marginBottom: 14 }}>
            How CereBroZen looks. Sleep and calming spaces stay dark either way.
          </p>
          <div className="seg" role="radiogroup" aria-label="Appearance">
            {([["system", "System"], ["light", "Dawn"], ["dark", "Night"]] as [ThemeChoice, string][]).map(([v, label]) => (
              <button key={v} type="button" role="radio" aria-checked={theme === v}
                className={`seg-btn ${theme === v ? "on" : ""}`} onClick={() => pickTheme(v)}>
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="card" style={{ marginTop: 16 }}>
          <h3>Journal lock</h3>
          <p className="placeholder" style={{ marginBottom: 14 }}>
            Require your device biometric (Face ID / Touch ID / passkey) to open your journal.
          </p>
          {lockSupported() ? (
            <label className="consent">
              <input type="checkbox" checked={lockOn} disabled={lockBusy} onChange={toggleLock} />
              <span className="c-copy">
                <span className="c-label">Lock my journal{lockBusy && <span className="placeholder"> · …</span>}</span>
                <span className="c-hint">Your entries stay hidden until you verify on this device.</span>
              </span>
            </label>
          ) : (
            <p className="placeholder">This browser doesn&rsquo;t support device biometric lock.</p>
          )}
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
                  <input type="checkbox" checked={!!consent[key]} disabled={!!busy}
                    onChange={(e) => toggle(key, e.target.checked, label)} />
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

        <YourData />
      </div>
    </div>
  );
}
