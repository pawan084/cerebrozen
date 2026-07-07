"use client";

// Web onboarding funnel — a faithful, web-adapted port of the iOS OnboardingFlow.
// Value-first: legal/transparency gates, then a felt benefit (2-minute breathing
// reset) and a first plan BEFORE the account ask. Choices are collected locally
// and carried to the server once the account exists (lib/onboarding.applyOnboarding).

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import AuthPanel from "@/components/AuthPanel";
import { hasOnboarded, hasSession, setOnboarded } from "@/lib/api";
import {
  applyOnboarding, clearDraft, Draft, FEELINGS, freshDraft, LANGUAGES,
  loadDraft, planTitle, PLAN_STEPS, REMINDER_TIMES, saveDraft,
} from "@/lib/onboarding";
import { CONSENT_NOTICE, defaultNoticeLang, NOTICE_LANGS } from "@/lib/consentNotice";

const PROGRESS = [0.08, 0.15, 0.25, 0.35, 0.45, 0.58, 0.7, 0.8, 0.88, 0.96];

export default function Onboarding() {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<Draft>(freshDraft());

  // Resume logic mirrors iOS gating: done → app; account-but-unfinished → jump to
  // the post-signup preference steps; brand-new → start at welcome.
  useEffect(() => {
    if (hasOnboarded()) { router.replace("/home"); return; }
    setDraft(loadDraft());
    if (hasSession()) setStep(8);
    setReady(true);
  }, [router]);

  function update(patch: Partial<Draft>) {
    setDraft((d) => {
      const next = { ...d, ...patch };
      saveDraft(next);
      return next;
    });
  }

  const next = () => setStep((s) => Math.min(s + 1, PROGRESS.length - 1));
  const back = () => setStep((s) => Math.max(s - 1, 0));

  async function finish() {
    await applyOnboarding(draft);
    setOnboarded();
    clearDraft();
    router.replace("/home");
  }

  if (!ready) return <div className="onb-root" />;

  return (
    <div className="onb-root">
      <div className="onb-stage" key={step}>
        {step === 0 && <Welcome onBegin={next} />}
        {step === 1 && <AgeGate onContinue={next} onBack={back} />}
        {step === 2 && <Disclosure onContinue={next} onBack={back} />}
        {step === 3 && (
          <Language draft={draft} update={update} onContinue={next} onBack={back} />
        )}
        {step === 4 && (
          <StateCheck draft={draft} update={update} onContinue={next} onBack={back} />
        )}
        {step === 5 && <FirstReset onContinue={next} onBack={back} />}
        {step === 6 && <FirstPlan draft={draft} onContinue={next} onBack={back} />}
        {step === 7 && <Signup onAuthed={next} onBack={back} />}
        {step === 8 && (
          <ConsentStep draft={draft} update={update} onContinue={next} onBack={back} />
        )}
        {step === 9 && (
          <Notifications draft={draft} update={update} onFinish={finish} onBack={back} />
        )}
      </div>
    </div>
  );
}

/* ---------- shared chrome ---------- */

function Progress({ value }: { value: number }) {
  return (
    <div
      className="onb-progress"
      role="progressbar"
      aria-label="Setup progress"
      aria-valuenow={Math.round(value * 100)}
    >
      <span style={{ width: `${value * 100}%` }} />
    </div>
  );
}

function BackButton({ onBack }: { onBack?: () => void }) {
  if (!onBack) return null;
  return (
    <button className="onb-back" onClick={onBack} aria-label="Back">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
        <path d="M15 5l-7 7 7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </button>
  );
}

function Scaffold({
  eyebrow, title, caption, progress, canContinue = true, continueLabel = "Continue",
  onContinue, onBack, children,
}: {
  eyebrow: string; title: string; caption: string; progress: number;
  canContinue?: boolean; continueLabel?: string;
  onContinue: () => void; onBack?: () => void; children?: React.ReactNode;
}) {
  return (
    <div className="onb-step">
      <BackButton onBack={onBack} />
      <p className="onb-eyebrow">{eyebrow}</p>
      <h1 className="onb-title">{title}</h1>
      <p className="onb-caption">{caption}</p>
      <div className="onb-content">{children}</div>
      <div className="onb-footer">
        <Progress value={progress} />
        <button className="btn pill-cta" disabled={!canContinue} onClick={onContinue}>
          {continueLabel}
        </button>
      </div>
    </div>
  );
}

/* ---------- 0 · Welcome ---------- */

function Welcome({ onBegin }: { onBegin: () => void }) {
  return (
    <div className="onb-step onb-center">
      <div className="onb-orb" aria-hidden="true" />
      <h1 className="onb-title onb-display">Welcome to CereBro</h1>
      <p className="onb-caption">
        Your quiet space for daily mental fitness, better sleep, and calmer focus.
      </p>
      <p className="onb-fine">Private by design — nothing is ever shared.</p>
      <div className="onb-footer">
        <Progress value={PROGRESS[0]} />
        <button className="btn pill-cta" onClick={onBegin}>
          <span aria-hidden="true">🌬️</span> Try a 2-minute reset
        </button>
        <Link href="/signin" className="onb-link">I already have an account</Link>
      </div>
    </div>
  );
}

/* ---------- 1 · Age gate ---------- */

function AgeGate({
  onContinue, onBack,
}: { onContinue: () => void; onBack: () => void }) {
  const [confirmed, setConfirmed] = useState(false);
  const [underage, setUnderage] = useState(false);
  return (
    <Scaffold
      eyebrow="For adults only" title="A quick check"
      caption="CereBro is built for adults. A quick check keeps the experience safe and appropriate."
      progress={PROGRESS[1]} canContinue={confirmed} onContinue={onContinue} onBack={onBack}
    >
      <div className="onb-danger">
        <strong>Wellness support, not emergency care.</strong>
        <span>If you are in immediate danger, call emergency services now.</span>
      </div>
      <button
        className={confirmed ? "onb-row selected" : "onb-row"}
        onClick={() => setConfirmed((c) => !c)}
        aria-pressed={confirmed}
      >
        <span className="onb-row-icon">{confirmed ? "✓" : "🛡️"}</span>
        <span className="onb-row-body">
          <strong>{confirmed ? "Confirmed: I am 18 or older" : "I am 18 or older"}</strong>
          <small>{confirmed ? "Thank you" : "Tap to confirm — required to continue"}</small>
        </span>
      </button>
      <button className="onb-quiet" onClick={() => setUnderage(true)}>I'm not 18 yet</button>
      {underage && (
        <p className="onb-fine" role="status">
          CereBro is built for adults, so we can't offer it to you yet. If things feel heavy,
          please talk to a trusted adult — or reach a free helpline like Childline (1098 in India).
        </p>
      )}
    </Scaffold>
  );
}

/* ---------- 2 · AI disclosure ---------- */

function Disclosure({ onContinue, onBack }: { onContinue: () => void; onBack: () => void }) {
  return (
    <Scaffold
      eyebrow="Honesty first" title="What CereBro is — and isn't"
      caption="Here's exactly what your AI companion can and can't do for you."
      progress={PROGRESS[2]} onContinue={onContinue} onBack={onBack}
    >
      <div className="onb-twocol">
        <div className="onb-mini">
          <strong>Can help</strong>
          <p>Listen, reflect, guide tools, suggest a plan.</p>
        </div>
        <div className="onb-mini">
          <strong>Can't do</strong>
          <p>Diagnose, prescribe, replace therapy, or handle emergencies.</p>
        </div>
      </div>
    </Scaffold>
  );
}

/* ---------- 3 · Language ---------- */

function Language({
  draft, update, onContinue, onBack,
}: { draft: Draft; update: (p: Partial<Draft>) => void; onContinue: () => void; onBack: () => void }) {
  function toggle(lang: string) {
    const has = draft.languages.includes(lang);
    const langs = has ? draft.languages.filter((l) => l !== lang) : [...draft.languages, lang];
    update({ languages: langs });
  }
  return (
    <Scaffold
      eyebrow="Speak your language" title="Language"
      caption="Talk and reflect in the language you think in. Mix more than one if that's you."
      progress={PROGRESS[3]} onContinue={onContinue} onBack={onBack}
    >
      <div className="onb-chips">
        {LANGUAGES.map((lang) => (
          <button
            key={lang}
            className={draft.languages.includes(lang) ? "onb-chip active" : "onb-chip"}
            onClick={() => toggle(lang)}
          >
            {lang}
          </button>
        ))}
      </div>
    </Scaffold>
  );
}

/* ---------- 4 · State check ---------- */

function StateCheck({
  draft, update, onContinue, onBack,
}: { draft: Draft; update: (p: Partial<Draft>) => void; onContinue: () => void; onBack: () => void }) {
  function pick(f: (typeof FEELINGS)[number]) {
    update({ feeling: f.label, motivations: [f.motivation], goals: [f.goal] });
    setTimeout(onContinue, 450);
  }
  return (
    <Scaffold
      eyebrow="One tap is enough" title="What feels most true right now?"
      caption="No questionnaire — just pick the one that fits today. CereBro shapes your first reset and plan around it."
      progress={PROGRESS[4]} canContinue={!!draft.feeling} onContinue={onContinue} onBack={onBack}
    >
      <div className="onb-list">
        {FEELINGS.map((f) => (
          <button
            key={f.label}
            className={draft.feeling === f.label ? "onb-row selected" : "onb-row"}
            onClick={() => pick(f)}
            aria-pressed={draft.feeling === f.label}
          >
            <span className="onb-row-icon">{f.emoji}</span>
            <span className="onb-row-body">
              <strong>{f.label}</strong>
              {draft.feeling === f.label && <small>That's what we'll start with</small>}
            </span>
            <span className="onb-chevron">›</span>
          </button>
        ))}
      </div>
    </Scaffold>
  );
}

/* ---------- 5 · First reset (breathing) ---------- */

function FirstReset({ onContinue, onBack }: { onContinue: () => void; onBack: () => void }) {
  const PHASES = [
    { label: "Breathe in", ms: 4000 },
    { label: "Hold", ms: 2000 },
    { label: "Breathe out", ms: 4000 },
  ];
  const [phase, setPhase] = useState(0);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => {
    timer.current = setTimeout(() => setPhase((p) => (p + 1) % PHASES.length), PHASES[phase].ms);
    return () => clearTimeout(timer.current);
  }, [phase]);
  const state = phase === 0 ? "in" : phase === 2 ? "out" : "hold";
  return (
    <div className="onb-step onb-center">
      <BackButton onBack={onBack} />
      <p className="onb-eyebrow">Your first reset</p>
      <h1 className="onb-title">Let's steady your body</h1>
      <p className="onb-caption">
        Two minutes of guided breathing — follow the orb for a few cycles, or skip ahead if now
        isn't the moment.
      </p>
      <div className="onb-breathe">
        <p className="onb-breathe-label">{PHASES[phase].label}</p>
        <div className={`onb-breathe-orb ${state}`} aria-hidden="true" />
      </div>
      <div className="onb-footer">
        <Progress value={PROGRESS[5]} />
        <button className="btn pill-cta" onClick={onContinue}>I feel steadier</button>
        <button className="btn ghost onb-secondary" onClick={onContinue}>Skip for now →</button>
      </div>
    </div>
  );
}

/* ---------- 6 · First plan ---------- */

function FirstPlan({
  draft, onContinue, onBack,
}: { draft: Draft; onContinue: () => void; onBack: () => void }) {
  return (
    <div className="onb-step">
      <BackButton onBack={onBack} />
      <p className="onb-eyebrow">Made around you</p>
      <h1 className="onb-title">First Plan</h1>
      <div className="onb-hero">
        <span className="onb-hero-tag">Today</span>
        <h2>{planTitle(draft.goals[0])}</h2>
        <p>A light plan: one thing now, one tonight, one tomorrow — tuned to what you picked.</p>
      </div>
      <div className="onb-list">
        {PLAN_STEPS.map((s) => (
          <div key={s.title} className="onb-row static">
            <span className="onb-row-icon">{s.emoji}</span>
            <span className="onb-row-body">
              <strong>{s.title}</strong>
              <small>{s.detail}</small>
            </span>
          </div>
        ))}
      </div>
      <div className="onb-footer">
        <Progress value={PROGRESS[6]} />
        <button className="btn pill-cta" onClick={onContinue}>Keep going →</button>
      </div>
    </div>
  );
}

/* ---------- 7 · Signup ---------- */

function Signup({ onAuthed, onBack }: { onAuthed: () => void; onBack: () => void }) {
  return (
    <div className="onb-step">
      <BackButton onBack={onBack} />
      <p className="onb-eyebrow">Yours to keep</p>
      <h1 className="onb-title">Save your space</h1>
      <p className="onb-caption">
        You've shaped your plan — create your private space to keep it. No social feed, no sharing,
        just you.
      </p>
      <div className="onb-content">
        <AuthPanel initialMode="signUp" onAuthed={onAuthed} />
      </div>
      <div className="onb-footer">
        <Progress value={PROGRESS[7]} />
      </div>
    </div>
  );
}

/* ---------- 8 · Consent ---------- */

// The onboarding notice shows the 5 categories about to collect data
// (model_training stays a separate opt-in on the account page).
const CONSENT_KEYS: (keyof Draft["consent"])[] = [
  "mood_history", "ai_memory", "journal_memory", "sleep_history", "voice_storage",
];

function ConsentStep({
  draft, update, onContinue, onBack,
}: { draft: Draft; update: (p: Partial<Draft>) => void; onContinue: () => void; onBack: () => void }) {
  // DPDP s.5(3): the notice itself is readable in English or an Eighth-Schedule
  // language — the option lives on the notice, seeded from the language step.
  const [lang, setLang] = useState(() => defaultNoticeLang(draft.languages));
  const notice = CONSENT_NOTICE[lang] ?? CONSENT_NOTICE.en;
  const remembering =
    draft.consent.mood_history && draft.consent.ai_memory &&
    draft.consent.journal_memory && draft.consent.sleep_history;

  function setAll(on: boolean) {
    update({
      consent: {
        ...draft.consent,
        mood_history: on, ai_memory: on, journal_memory: on, sleep_history: on,
      },
    });
  }
  function toggle(key: keyof Draft["consent"]) {
    update({ consent: { ...draft.consent, [key]: !draft.consent[key] } });
  }

  return (
    <Scaffold
      eyebrow="Privacy choices" title={notice.title}
      caption={notice.caption}
      progress={PROGRESS[8]} onContinue={onContinue} onBack={onBack}
    >
      <label style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
        <span aria-hidden="true">🌐</span>
        <select
          aria-label="Notice language"
          value={lang}
          onChange={(e) => setLang(e.target.value)}
          style={{ flex: "0 1 auto" }}
        >
          {NOTICE_LANGS.map((code) => (
            <option key={code} value={code}>{CONSENT_NOTICE[code].nativeName}</option>
          ))}
        </select>
      </label>
      <button
        className={remembering ? "onb-row selected" : "onb-row"}
        onClick={() => setAll(!remembering)}
        aria-pressed={remembering}
      >
        <span className="onb-row-icon">{remembering ? "✓" : "✨"}</span>
        <span className="onb-row-body">
          <strong>{remembering ? notice.recommendOn : notice.recommendOff}</strong>
          <small>{remembering ? notice.recommendOnSub : notice.recommendOffSub}</small>
        </span>
      </button>
      <div className="onb-toggles">
        {CONSENT_KEYS.map((key) => (
          <label key={key} className="onb-toggle">
            <span className="onb-toggle-body">
              <strong>{notice.categories[key].label}</strong>
              <small>{notice.categories[key].hint}</small>
            </span>
            <input
              type="checkbox"
              role="switch"
              aria-label={notice.categories[key].label}
              checked={draft.consent[key]}
              onChange={() => toggle(key)}
            />
          </label>
        ))}
      </div>
    </Scaffold>
  );
}

/* ---------- 9 · Notifications ---------- */

function Notifications({
  draft, update, onFinish, onBack,
}: { draft: Draft; update: (p: Partial<Draft>) => void; onFinish: () => void; onBack: () => void }) {
  const [busy, setBusy] = useState(false);
  async function enter() {
    setBusy(true);
    await onFinish();
  }
  return (
    <Scaffold
      eyebrow="Gentle reminders" title="Notifications"
      caption="You've had your first win — want a quiet nudge to come back tomorrow? Never noisy, always easy to turn off."
      progress={PROGRESS[9]} continueLabel={busy ? "One moment…" : "Enter CereBro"}
      canContinue={!busy} onContinue={enter} onBack={onBack}
    >
      <div className="onb-chips">
        {REMINDER_TIMES.map((t) => (
          <button
            key={t}
            className={draft.reminder === t ? "onb-chip active" : "onb-chip"}
            onClick={() => update({ reminder: t })}
          >
            {t}
          </button>
        ))}
      </div>
      <p className="onb-fine">On the web we'll send a gentle email nudge — reminders on your phone come with the iOS app.</p>
    </Scaffold>
  );
}
