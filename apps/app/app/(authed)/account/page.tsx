"use client";

import Link from "next/link";
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
import { CONSENT_NOTICE, NOTICE_LANGS } from "@/lib/consentNotice";
import { AppHeader } from "@/components/AppHeader";

type Consent = {
  mood_history: boolean; ai_memory: boolean; voice_storage: boolean;
  model_training: boolean; journal_memory: boolean; sleep_history: boolean;
};
type Contact = { name: string; method: string; value: string; relationship: string; notify_consent: boolean };

// Labels/hints render from the localized consent notice (DPDP s.5(3) —
// lib/consentNotice.ts); this fixes the category order.
const CONSENT_KEYS: (keyof Consent)[] = [
  "mood_history", "ai_memory", "journal_memory", "sleep_history", "voice_storage", "model_training",
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
  // DPDP s.5(3): the consent notice is readable in English or an
  // Eighth-Schedule language, picked right on the notice.
  const [noticeLang, setNoticeLang] = useState("en");
  const notice = CONSENT_NOTICE[noticeLang] ?? CONSENT_NOTICE.en;

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

  async function openPortal() {
    setBillingMsg("");
    try {
      const { url } = await api<{ url: string }>("/billing/portal", { method: "POST" });
      window.location.href = url;
    } catch (err: any) {
      setBillingMsg(err.message || "Couldn't open the billing portal.");
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
          {(me.subscription_tier ?? "free") === "free" ? (
            <div style={{ marginTop: 12 }}>
              <button className="btn" onClick={upgrade}>Upgrade to Premium</button>
              {billingMsg && <p className="footnote" role="status">{billingMsg}</p>}
            </div>
          ) : (
            <div style={{ marginTop: 12 }}>
              {/* Cancelling must be as easy as subscribing (OECD checklist). */}
              <button className="btn ghost" onClick={openPortal}>Manage or cancel subscription</button>
              <p className="footnote">
                Opens Stripe&apos;s billing portal. Subscribed on iPhone? Manage it in the App Store instead.
              </p>
              {billingMsg && <p className="footnote" role="status">{billingMsg}</p>}
            </div>
          )}
        </section>
      )}

      <section className="card" aria-label="Privacy choices">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
          <h2>{notice.title}</h2>
          <label className="row" style={{ gap: 6 }}>
            <span aria-hidden="true">🌐</span>
            <select
              aria-label="Notice language"
              value={noticeLang}
              onChange={(e) => setNoticeLang(e.target.value)}
            >
              {NOTICE_LANGS.map((code) => (
                <option key={code} value={code}>{CONSENT_NOTICE[code].nativeName}</option>
              ))}
            </select>
          </label>
        </div>
        <p className="sub">{notice.caption}</p>
        {consent &&
          CONSENT_KEYS.map((key) => (
            <div className="entry row" key={key}>
              <div className="grow">
                <strong>{notice.categories[key].label}</strong>
                <div className="meta">{notice.categories[key].hint}</div>
              </div>
              <input
                type="checkbox"
                role="switch"
                checked={consent[key]}
                onChange={() => toggleConsent(key)}
                aria-label={notice.categories[key].label}
                style={{ width: 20, height: 20 }}
              />
            </div>
          ))}
      </section>

      {/* Real human pathways — mirrors Android Settings.kt HumanSupportScreen.
          No fake booking UI, ever; these lines reach real people today. */}
      <section className="card" aria-label="Talk to a human">
        <h2>Talk to a human</h2>
        <p className="sub">
          CereBro is a companion, not a clinician. When you want a real person, these connect
          you with one.
        </p>
        <div className="entry row">
          <div className="grow">
            <strong>Tele-MANAS — call <a href="tel:14416" style={{ color: "var(--cyan)" }}>14416</a></strong>
            <div className="meta">Free government mental-health line · 24/7</div>
          </div>
        </div>
        <div className="entry row">
          <div className="grow">
            <strong>iCall — talk to a counsellor at <a href="tel:9152987821" style={{ color: "var(--cyan)" }}>9152987821</a></strong>
            <div className="meta">Trained counsellors by phone</div>
          </div>
        </div>
        <div className="entry row">
          <div className="grow">
            <strong>
              <a href="https://findahelpline.com/in" target="_blank" rel="noreferrer" style={{ color: "var(--cyan)" }}>
                Find a therapist or helpline
              </a>
            </strong>
            <div className="meta">Directories of professional help near you</div>
          </div>
        </div>
        <p className="footnote">
          In a heavy moment? <Link href="/crisis" style={{ color: "var(--lav)" }}>Urgent support</Link> is
          two taps from anywhere.
        </p>
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
        <p className="sub">
          Download everything the server holds for you, anytime — and see{" "}
          <Link href="/patterns" style={{ color: "var(--lav)" }}>what the AI has learned</Link>{" "}
          (deletable there too).
        </p>
        <button className="btn ghost" onClick={exportData}>Download my data (JSON)</button>
        {status && <p className="success" role="status">{status}</p>}
      </section>

      {/* Honesty cards — hand-synced with Android privacypolicy_* strings
          (REDESIGN F9: say what's evidence, what CereBro is not, who's involved). */}
      <section className="card" aria-label="How CereBro is built">
        <h2>How CereBro is built</h2>
        <div className="entry">
          <strong>Evidence, labeled</strong>
          <div className="meta">
            Tools are labeled with why they work. Where something is comfort rather than
            therapy, we say so.
          </div>
        </div>
        <div className="entry">
          <strong>What CereBro is not</strong>
          <div className="meta">
            A companion alongside care, never a replacement. It doesn&apos;t diagnose or treat.
          </div>
        </div>
        <div className="entry">
          <strong>Professional involvement</strong>
          <div className="meta">
            Built with published clinical research; a formal clinical advisory process is on
            our roadmap.
          </div>
        </div>
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
