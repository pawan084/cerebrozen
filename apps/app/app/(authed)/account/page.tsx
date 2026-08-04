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
import { analyticsEnabled, setAnalyticsEnabled, track } from "@/lib/analytics";
import { CONSENT_NOTICE, NOTICE_LANGS } from "@/lib/consentNotice";
import { getThemeMode, setThemeMode, type ThemeMode } from "@/lib/theme";
import { AppHeader } from "@/components/AppHeader";
import { resetTour } from "@/components/GuidedTour";

/** Fires `paywall_view` once, when the upgrade block is actually rendered —
 *  mounting is the honest definition of "seen", not opening the page. */
function PaywallSeen() {
  useEffect(() => { track("paywall_view"); }, []);
  return null;
}
import { Icon } from "@/components/icons";

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
  // Local-only preference (localStorage): analytics never touches the account,
  // so it is not a server consent flag.
  const [usageStats, setUsageStats] = useState(true);
  const [region, setRegion] = useState("");
  const [contact, setContact] = useState<Contact>({
    name: "", method: "email", value: "", relationship: "", notify_consent: false,
  });
  const [contactSaved, setContactSaved] = useState(false);
  const [confirmText, setConfirmText] = useState("");
  // Deletion and crisis-region failures are stated, never swallowed (D8/D10).
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [regionError, setRegionError] = useState<string | null>(null);
  const [status, setStatus] = useState("");
  const [billingMsg, setBillingMsg] = useState("");
  const [push, setPush] = useState<PushStatus | null>(null);
  const [pushOn, setPushOn] = useState(false);
  const [pushMsg, setPushMsg] = useState("");
  const [theme, setTheme] = useState<ThemeMode>("system");
  const [statsOn, setStatsOn] = useState(true);
  // DPDP s.5(3): the consent notice is readable in English or an
  // Eighth-Schedule language, picked right on the notice.
  const [noticeLang, setNoticeLang] = useState("en");
  const notice = CONSENT_NOTICE[noticeLang] ?? CONSENT_NOTICE.en;

  useEffect(() => {
    setUsageStats(analyticsEnabled());
    api("/auth/me").then((u) => {
      setMe(u);
      setRegion(u.region ?? "");
      // The paywall surface here is the upgrade card (iOS parity: PremiumView
      // fires the same event; anonymous, consent-gated in lib/analytics).
      if ((u.subscription_tier ?? "free") === "free") track("paywall_view");
    }).catch(() => {});
    api<Consent>("/users/me/consent").then(setConsent).catch(() => {});
    api<Contact | null>("/users/me/trusted-contact").then((c) => c && setContact(c)).catch(() => {});
    getPushStatus().then(setPush).catch(() => {});
    isSubscribed().then(setPushOn).catch(() => {});
    setTheme(getThemeMode());
    setStatsOn(analyticsEnabled());
  }, []);

  function pickTheme(mode: ThemeMode) {
    setThemeMode(mode);
    setTheme(mode);
  }

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
    const previous = region;
    setRegion(value);
    setRegionError(null);
    try {
      await api("/users/me", { method: "PATCH", body: JSON.stringify({ region: value }) });
    } catch {
      // Register D10: this decides which emergency numbers the crisis screen
      // shows. A silent failure left it LOOKING changed while the server kept
      // the old region — the one place a cosmetic optimistic update is unsafe.
      setRegion(previous);
      setRegionError("We couldn't save that region — your previous choice is still in effect.");
    }
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

  async function openPortal() {
    setBillingMsg("");
    try {
      const { url } = await api<{ url: string }>("/billing/portal", { method: "POST" });
      window.location.href = url;
    } catch (err: any) {
      setBillingMsg(err.message || "Couldn't open the billing portal.");
    }
  }

  async function upgrade() {
    track("paywall_cta", "premium");
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
    // The `finally` here used to clear the session and redirect even when the
    // DELETE had failed — so a user whose data still existed was shown the
    // signed-out screen and reasonably concluded it was gone (register D8).
    // Deletion is the one action that must never be claimed optimistically.
    try {
      await api("/users/me", { method: "DELETE" });
    } catch (err: any) {
      setDeleteError(
        err?.message === "unauthorized"
          ? "Your session expired before we could delete the account. Sign in and try again."
          : "We couldn't delete your account just now — nothing was removed. Please try again.",
      );
      return;
    }
    clearSession();
    router.replace("/signin");
  }

  return (
    <>
      <AppHeader eyebrow="Private by default" title="Settings" />
      <div className="page-body">

      {me && (
        <section className="card cz-in">
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
              <PaywallSeen />
              <button className="btn" onClick={upgrade}>Upgrade to Premium</button>
              {billingMsg && <p className="footnote" role="status">{billingMsg}</p>}
            </div>
          ) : (
            <div style={{ marginTop: 12 }}>
              {/* Stripe's own portal rather than a hand-rolled cancel: proration,
                  trials and dunning are its rules, and a local reimplementation
                  gets them subtly wrong in ways that cost real people money.
                  The label keeps the word "cancel" — cancelling must be as easy
                  to find as subscribing was (OECD dark-pattern checklist), and a
                  button reading only "Manage billing" hides it one click deeper. */}
              <button className="btn ghost" onClick={openPortal}>Manage or cancel subscription</button>
              <p className="footnote">
                Change your card, switch plan, or cancel. Subscribed on iPhone? Manage it in the App Store instead.
              </p>
              {billingMsg &&<p className="footnote" role="status">{billingMsg}</p>}
            </div>
          )}
        </section>
      )}

      {/* Re-run the first-visit guided tour: clears only the tour-done flag. */}
      <button
        type="button"
        className="ui-row cz-in cz-d1"
        style={{ marginBottom: 16 }}
        onClick={() => {
          resetTour();
          router.push("/home");
        }}
      >
        <span className="ui-row-icon"><Icon.spark size={18} /></span>
        <span className="ui-row-body">
          <strong>Take a quick tour</strong>
          <small>See what CereBro can do</small>
        </span>
        <span className="ui-row-chevron" aria-hidden="true">›</span>
      </button>

      {/* Appearance — Android You→Appearance parity. Your choice applies to
          every signed-in page (owner decision 2026-08-04); onboarding and the
          signed-out pages keep their Night branding. */}
      <section className="card cz-in cz-d2" aria-label="Appearance">
        <h2>Appearance</h2>
        <p className="sub">Dawn is a warm-light look; your choice applies everywhere, Sleep included.</p>
        <div className="ui-chips" style={{ marginTop: 10 }}>
          {([["system", "System"], ["night", "Night"], ["dawn", "Dawn"]] as [ThemeMode, string][]).map(([mode, label]) => (
            <button
              key={mode}
              className={theme === mode ? "ui-chip active" : "ui-chip"}
              aria-pressed={theme === mode}
              onClick={() => pickTheme(mode)}
            >
              {label}
            </button>
          ))}
        </div>
      </section>

      {/* lang tracks the picked notice language so screen readers switch
          pronunciation instead of reading Hindi with English phonetics. */}
      <section className="card" aria-label="Privacy choices" lang={noticeLang}>
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

        {/* Analytics is NOT a consent category — it never touches account data.
            It lives here because this is where someone looks for the off
            switch, and shipping counting without one would break the promise
            the copy already makes. */}
        <div className="row" style={{ marginTop: 14, gap: 12, alignItems: "flex-start" }}>
          <div className="grow">
            <strong>Anonymous usage stats</strong>
            <div className="sub">
              Counts only — which onboarding step was reached, whether the plans page was
              opened. Tied to a random id for this browser, never to your account, and never
              shared. Switch it off and nothing is sent.
            </div>
          </div>
          <input
            type="checkbox"
            role="switch"
            checked={usageStats}
            onChange={() => {
              const next = !usageStats;
              setUsageStats(next);
              setAnalyticsEnabled(next);
            }}
            aria-label="Anonymous usage stats"
            style={{ width: 20, height: 20 }}
          />
        </div>
      </section>

      {/* Real human pathways — mirrors Android Settings.kt HumanSupportScreen.
          No fake booking UI, ever; these lines reach real people today. */}
      <section className="card cz-in cz-d3" aria-label="Talk to a human">
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
        {regionError && (
          <p className="sub" role="alert" style={{ color: "var(--danger)" }}>{regionError}</p>
        )}
      </section>

      <form className="card cz-in cz-d4" onSubmit={saveContact} aria-label="Trusted contact">
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

      <section className="card cz-in cz-d5" aria-label="Your data">
        <h2>Your data</h2>
        <p className="sub">
          Download everything the server holds for you, anytime — and see{" "}
          <Link href="/patterns" style={{ color: "var(--lav)" }}>what the AI has learned</Link>{" "}
          (deletable there too).
        </p>
        {/* iOS/Android "Anonymous usage stats" parity — local toggle over the
            first-party, no-auth /events counts (never content, never linked). */}
        <label className="row" style={{ marginTop: 12, gap: 10 }}>
          <input
            type="checkbox"
            role="switch"
            checked={statsOn}
            onChange={() => { setAnalyticsEnabled(!statsOn); setStatsOn(!statsOn); }}
            aria-label="Anonymous usage stats"
            style={{ width: "auto" }}
          />
          <span className="sub">
            Anonymous usage stats — first-party counts (like &quot;onboarding completed&quot;), never your
            content, never linked to your account.
          </span>
        </label>
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

      <section className="card cz-in cz-d6" aria-label="Delete account">
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
        {deleteError && (
          <p className="sub" role="alert" style={{ color: "var(--danger)" }}>{deleteError}</p>
        )}
      </section>
      </div>
    </>
  );
}
