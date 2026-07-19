"use client";

import { useEffect, useState } from "react";
import { updateConsent, updateProfile, type Consent } from "@/lib/api";
import { firstName, useMe } from "@/components/shell";

const FOCUS = [
  "Difficult conversations", "Decisions", "Focus & procrastination",
  "Well-being & rest", "Leading a team", "Confidence",
];
const CONSENTS: { key: keyof Consent; label: string; hint: string }[] = [
  { key: "mood_history", label: "Keep my check-ins", hint: "So you can see a trend over time." },
  { key: "journal_memory", label: "Keep my journal", hint: "Private to you, always." },
  { key: "ai_memory", label: "Let the coach remember", hint: "So you don't start over each time." },
];
const STEPS = 4;

export function Onboarding() {
  const me = useMe();
  const [show, setShow] = useState(false);
  const [step, setStep] = useState(0);
  const [focus, setFocus] = useState<string[]>([]);
  const [consents, setConsents] = useState<Partial<Record<keyof Consent, boolean>>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    try { if (!localStorage.getItem("cbz-onboarded")) setShow(true); } catch { /* ignore */ }
  }, []);
  useEffect(() => {
    if (!show) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") done(); };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show]);

  if (!show) return null;

  function done() { try { localStorage.setItem("cbz-onboarded", "1"); } catch { /* ignore */ } setShow(false); }
  const toggleFocus = (f: string) => setFocus((p) => (p.includes(f) ? p.filter((x) => x !== f) : [...p, f]));
  const toggleConsent = (k: keyof Consent) => setConsents((p) => ({ ...p, [k]: !p[k] }));

  async function finish() {
    setSaving(true);
    try {
      if (focus.length) await updateProfile({ goals: focus }).catch(() => {});
      const on = Object.fromEntries(Object.entries(consents).filter(([, v]) => v));
      if (Object.keys(on).length) await updateConsent(on as Partial<Consent>).catch(() => {});
    } finally { setSaving(false); done(); }
  }

  return (
    <div className="onb-overlay" role="dialog" aria-modal="true" aria-label="Welcome to CereBroZen">
      <div className="onb-card">
        <div className="onb-dots" aria-hidden="true">
          {Array.from({ length: STEPS }).map((_, i) => <span key={i} className={`onb-dot ${i <= step ? "on" : ""}`} />)}
        </div>

        {step === 0 && (
          <>
            <h2>Welcome{firstName(me) ? `, ${firstName(me)}` : ""}.</h2>
            <p>An always-on coach, private to you. It helps with work behaviours — and it&rsquo;s never a substitute for a therapist or clinician.</p>
            <div className="onb-nav end"><button className="primary" onClick={() => setStep(1)}>Get started</button></div>
          </>
        )}
        {step === 1 && (
          <>
            <h2>What would help most?</h2>
            <p className="onb-sub">Pick a few — you can change this any time.</p>
            <div className="onb-chips">
              {FOCUS.map((f) => (
                <button key={f} type="button" className={`onb-chip ${focus.includes(f) ? "on" : ""}`}
                  aria-pressed={focus.includes(f)} onClick={() => toggleFocus(f)}>{f}</button>
              ))}
            </div>
            <div className="onb-nav"><button className="ghost-btn" onClick={() => setStep(0)}>Back</button><button className="primary" onClick={() => setStep(2)}>Next</button></div>
          </>
        )}
        {step === 2 && (
          <>
            <h2>Your data, your call.</h2>
            <p className="onb-sub">Each is off until you turn it on. Your employer only ever sees anonymised counts.</p>
            <div className="onb-consents">
              {CONSENTS.map((c) => (
                <label key={c.key} className="onb-consent">
                  <input type="checkbox" checked={!!consents[c.key]} onChange={() => toggleConsent(c.key)} />
                  <span><span className="onb-c-l">{c.label}</span><span className="onb-c-h">{c.hint}</span></span>
                </label>
              ))}
            </div>
            <div className="onb-nav"><button className="ghost-btn" onClick={() => setStep(1)}>Back</button><button className="primary" onClick={() => setStep(3)}>Next</button></div>
          </>
        )}
        {step === 3 && (
          <>
            <h2>You&rsquo;re set.</h2>
            <p>Start with a quick check-in, or just say what&rsquo;s on your mind. Everything you write stays private to you.</p>
            <div className="onb-nav"><button className="ghost-btn" onClick={done}>Skip</button><button className="primary" disabled={saving} onClick={finish}>{saving ? "…" : "Enter CereBroZen"}</button></div>
          </>
        )}
      </div>
    </div>
  );
}
