"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, authedFetch, clearSession } from "@/lib/api";
import {
  getPushStatus,
  isSubscribed,
  pushSupported,
  subscribePush,
  unsubscribePush,
  type PushStatus,
} from "@/lib/push";
import { AppHeader } from "@/components/AppHeader";

type Consent = {
  mood_history: boolean; ai_memory: boolean; voice_storage: boolean;
  model_training: boolean; journal_memory: boolean; sleep_history: boolean;
};
type Contact = { name: string; method: string; value: string; relationship: string; notify_consent: boolean };

const CONSENT_LABELS: [keyof Consent, string, string][] = [
  ["mood_history", "Mood history", "Let insights use your check-in history."],
  ["ai_memory", "AI memory", "Let the companion remember context between conversations."],
  ["journal_memory", "Journal memory", "Let journal titles tune your plan and insights."],
  ["sleep_history", "Sleep history", "Let your sleep diary tune plans and insights."],
  ["voice_storage", "Voice storage", "Keep voice transcripts after a session ends."],
  ["model_training", "Model training", "Allow anonymized snippets to improve responses."],
];

// Mirrors the backend/iOS crisis-region contract (services/crisis.py).
const REGIONS = [
  ["", "Automatic (device region)"],
  ["IN", "India"],
  ["US", "United States"],
  ["CA", "Canada"],
  ["GB", "United Kingdom"],
  ["IE", "Ireland"],
  ["AU", "Australia"],
  ["NZ", "New Zealand"],
];

export default function Account() {
  const router = useRouter();
  const [me, setMe] = useState<any>(null);
  const [consent, setConsent] = useState<Consent | null>(null);
  const [region, setRegion] = useState("");
  const [contact, setContact] = useState<Contact>({
    name: "", method: "email", value: "", relationship: "", notify_consent: false,
  });
  const [contactSaved, setContactSaved] = useState(false);
  const [confirmText, setConfirmText] = useState("");
  const [status, setStatus] = useState("");
  const [billingMsg, setBillingMsg] = useState("");
  const [push, setPush] = useState<PushStatus | null>(null);
  const [pushOn, setPushOn] = useState(false);
  const [pushMsg, setPushMsg] = useState("");

  useEffect(() => {
    api("/auth/me").then((u) => {
      setMe(u);
      setRegion(u.region ?? "");
    }).catch(() => {});
    api<Consent>("/users/me/consent").then(setConsent).catch(() => {});
    api<Contact | null>("/users/me/trusted-contact").then((c) => c && setContact(c)).catch(() => {});
    getPushStatus().then(setPush).catch(() => {});
    isSubscribed().then(setPushOn).catch(() => {});
  }, []);

  async function toggleBrowserPush() {
    if (!push?.enabled) return;
    setPushMsg("");
    try {
      if (pushOn) {
        await unsubscribePush();
        setPushOn(false);
      } else {
        await subscribePush(push.public_key);
        setPushOn(true);
      }
    } catch (err: any) {
      setPushMsg(err.message || "Couldn't update browser notifications.");
    }
  }

  async function toggleConsent(key: keyof Consent) {
    if (!consent) return;
    const next = { ...consent, [key]: !consent[key] };
    setConsent(next);
    try {
      setConsent(await api<Consent>("/users/me/consent", { method: "PATCH", body: JSON.stringify(next) }));
    } catch {
      setConsent(consent); // revert on failure
    }
  }

  async function saveRegion(value: string) {
    setRegion(value);
    try {
      await api("/users/me", { method: "PATCH", body: JSON.stringify({ region: value }) });
    } catch {}
  }

  async function saveContact(e: React.FormEvent) {
    e.preventDefault();
    try {
      setContact(await api<Contact>("/users/me/trusted-contact", { method: "PUT", body: JSON.stringify(contact) }));
      setContactSaved(true);
    } catch {
      setStatus("Couldn't save the trusted contact.");
    }
  }

  async function exportData() {
    setStatus("Preparing your export…");
    const res = await authedFetch("/users/me/export");
    if (!res.ok) {
      setStatus("Export failed — try again.");
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "cerebro-export.json";
    a.click();
    URL.revokeObjectURL(url);
    setStatus("Export downloaded.");
  }

  async function toggleEmailNudges() {
    if (!me) return;
    const next = !me.email_nudges;
    setMe({ ...me, email_nudges: next });
    try {
      setMe(await api("/users/me", { method: "PATCH", body: JSON.stringify({ email_nudges: next }) }));
    } catch {
      setMe(me);
    }
  }

  async function upgrade() {
    setBillingMsg("");
    try {
      const { url } = await api<{ url: string }>("/billing/checkout", {
        method: "POST",
        body: JSON.stringify({ tier: "premium" }),
      });
      window.location.href = url;
    } catch (err: any) {
      // 503 until Stripe is configured — show the honest server message.
      setBillingMsg(err.message || "Web billing isn't available yet.");
    }
  }

  async function deleteAccount() {
    if (confirmText !== "DELETE") return;
    try {
      await api("/users/me", { method: "DELETE" });
    } finally {
      clearSession();
      router.replace("/signin");
    }
  }

  return (
    <>
      <AppHeader eyebrow="Private by default" title="Settings" />
      <div className="page-body">

      {me && (
        <section className="card">
          <h2>{me.name}</h2>
          <p className="sub">{me.email} · {me.subscription_tier ?? "free"} tier</p>
          <label className="row" style={{ marginTop: 10, gap: 10 }}>
            <input
              type="checkbox"
              role="switch"
              checked={pushOn}
              onChange={toggleBrowserPush}
              disabled={!push?.enabled || !pushSupported()}
              aria-label="Browser notifications"
              style={{ width: "auto" }}
            />
            <span className="sub">
              {push && !push.enabled
                ? "Browser notifications aren't configured on this server yet — email nudges below still work."
                : !pushSupported() && push
                  ? "This browser doesn't support push notifications — email nudges below still work."
                  : "Browser notifications — gentle nudges arrive on this device, even with the tab closed."}
            </span>
          </label>
          {pushMsg && <p className="footnote" role="status">{pushMsg}</p>}
          <label className="row" style={{ marginTop: 10, gap: 10 }}>
            <input
              type="checkbox"
              role="switch"
              checked={!!me.email_nudges}
              onChange={toggleEmailNudges}
              aria-label="Email nudges"
              style={{ width: "auto" }}
            />
            <span className="sub">Email me my nudges — gentle reminders arrive by email when no browser is subscribed.</span>
          </label>
          {(me.subscription_tier ?? "free") === "free" && (
            <div style={{ marginTop: 12 }}>
              <button className="btn" onClick={upgrade}>Upgrade to Premium</button>
              {billingMsg && <p className="footnote" role="status">{billingMsg}</p>}
            </div>
          )}
        </section>
      )}

      <section className="card" aria-label="Privacy choices">
        <h2>What CereBro remembers</h2>
        <p className="sub">All off unless you turn them on — enforced server-side.</p>
        {consent &&
          CONSENT_LABELS.map(([key, label, hint]) => (
            <div className="entry row" key={key}>
              <div className="grow">
                <strong>{label}</strong>
                <div className="meta">{hint}</div>
              </div>
              <input
                type="checkbox"
                role="switch"
                checked={consent[key]}
                onChange={() => toggleConsent(key)}
                aria-label={label}
                style={{ width: 20, height: 20 }}
              />
            </div>
          ))}
      </section>

      <section className="card" aria-label="Crisis resources region">
        <h2>Crisis resources region</h2>
        <p className="sub">Sets which hotlines appear if a conversation gets heavy.</p>
        <select value={region} onChange={(e) => saveRegion(e.target.value)} aria-label="Region">
          {REGIONS.map(([code, label]) => (
            <option key={code} value={code}>{label}</option>
          ))}
        </select>
      </section>

      <form className="card" onSubmit={saveContact} aria-label="Trusted contact">
        <h2>Trusted contact</h2>
        <p className="sub">
          Someone we may notify — only with the consent below — if a crisis is detected.
        </p>
        <div className="row">
          <label className="field grow">
            <span>Name</span>
            <input value={contact.name} onChange={(e) => setContact({ ...contact, name: e.target.value })} />
          </label>
          <label className="field grow">
            <span>Email</span>
            <input value={contact.value} onChange={(e) => setContact({ ...contact, value: e.target.value, method: "email" })} />
          </label>
        </div>
        <div className="row" style={{ marginBottom: 10 }}>
          <input
            id="notify"
            type="checkbox"
            checked={contact.notify_consent}
            onChange={(e) => setContact({ ...contact, notify_consent: e.target.checked })}
            style={{ width: 18, height: 18 }}
          />
          <label htmlFor="notify" className="sub">I consent to CereBro contacting them in a detected crisis.</label>
        </div>
        {contactSaved && <p className="success">Trusted contact saved.</p>}
        <button className="btn ghost">Save contact</button>
      </form>

      <section className="card" aria-label="Your data">
        <h2>Your data</h2>
        <p className="sub">Download everything the server holds for you, anytime.</p>
        <button className="btn ghost" onClick={exportData}>Download my data (JSON)</button>
        {status && <p className="success" role="status">{status}</p>}
      </section>

      <section className="card" aria-label="Delete account">
        <h2>Delete account</h2>
        <p className="sub">
          Permanently removes your account and every journal entry, check-in, chat, and sleep log —
          server-side, immediately. This cannot be undone.
        </p>
        <label className="field">
          <span>Type DELETE to confirm</span>
          <input value={confirmText} onChange={(e) => setConfirmText(e.target.value)} placeholder="DELETE" />
        </label>
        <button
          className="btn"
          style={{ background: "none", border: "1px solid var(--danger)", color: "var(--danger)" }}
          disabled={confirmText !== "DELETE"}
          onClick={deleteAccount}
          type="button"
        >
          Delete my account
        </button>
      </section>
      </div>
    </>
  );
}
