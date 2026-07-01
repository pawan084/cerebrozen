# CereBro — Ship Readiness & App Store Submission

A practical checklist + metadata for taking the iOS app to TestFlight / the App Store.
Honest status: the app is a high-fidelity, fully-architected build with a green test
suite and a live backend. Remaining gaps are **content depth** and **clinical
credibility**, not engineering.

---

## 1. App Store Connect metadata (draft)

- **Name:** CereBro
- **Subtitle (30 chars):** Calm, proactive AI wellness
- **Primary category:** Health & Fitness   ·   **Secondary:** Lifestyle
- **Age rating:** 17+ (the app gates to 18+ on first launch; mental-health themes)
- **Promotional text (170):** A quieter mind, one small step at a time. Talk it
  out by voice, reflect privately, sleep deeper — with an AI companion that adapts
  to you, never judges, and always points to real help when it matters.
- **Keywords:** wellness,mental health,calm,sleep,meditation,journal,anxiety,stress,mindfulness,AI companion,breathing,mood
- **Support URL:** https://your-domain.com/support   ·   **Marketing URL:** /  ·   **Privacy Policy:** /privacy

**Description (outline):**
1. One calm space for daily mental fitness, better sleep, and calmer focus.
2. Talk tab — a voice + text AI companion that listens, reflects, and runs real
   activities (breathing, grounding, journaling) inline.
3. Sleep — soundscapes with a sleep-safe auto-stop timer.
4. Journal — private reflection, optionally Face ID–locked.
5. Proactive, not noisy — gentle nudges and an adaptive plan.
6. Privacy-first — explicit consent gates what the AI remembers; export or delete
   everything anytime.
7. **Not a medical device.** Wellness support, not therapy or crisis care; crisis
   resources are one tap away and localized to your region.

## 2. Privacy "nutrition" labels (App Privacy)

| Data type | Collected | Linked to user | Used for |
|---|---|---|---|
| Contact (name, email) | Yes (account) | Yes | App functionality |
| Health & Fitness (mood, journal) | Yes | Yes (consent-gated) | App functionality, personalization |
| Audio (voice) | Optional (off by default) | Only if user enables storage | Speech-to-text for the companion |
| Usage data | Yes | Yes | Analytics / product improvement |
| Diagnostics | Optional | No | Crash/perf |

- No data sold. No third-party ad tracking. Voice audio storage is **off by default**.
- Conversations may be scanned for crisis signals to surface support (state in the policy).

## 3. Capabilities / entitlements checklist

- [x] **Microphone** — `NSMicrophoneUsageDescription` (Talk voice loop)
- [x] **Face ID** — `NSFaceIDUsageDescription` (optional Journal lock)
- [x] **Background audio** — `UIBackgroundModes: [audio]` (sleep soundscapes keep
      playing with the screen locked)
- [x] **ATS local networking** — `NSAllowsLocalNetworking` (dev only; remove or scope
      for production, which uses HTTPS)
- [x] **Launch screen** — navy `LaunchBackground` (no white flash)
- [ ] **Sign in with Apple** — code complete on both ends; *enable the capability in
      Xcode → Signing & Capabilities → + Sign in with Apple*, and set
      `APPLE_CLIENT_ID` in `backend/.env`. Until then the button degrades gracefully.

## 4. Pre-submission checklist

- [ ] Bump bundle id / version / build; set the real `PUBLIC_API_URL` (HTTPS).
- [ ] Provide live provider keys via `backend/.env` / secrets (OpenAI/Anthropic,
      Deepgram, ElevenLabs). Without them chat/voice fall back gracefully but the
      flagship loop is degraded. **Rotate any key ever shared in chat/logs.**
- [ ] App icon (1024² present) + 6.7"/6.1" screenshots (use the showcase frames).
- [ ] Localize crisis numbers if launching outside the curated regions
      (US/CA/GB/IE/AU/NZ/IN + International default already covered; region picker ships).
- [ ] Add a visible "Not a medical device / not therapy" line to the marketing copy
      (already in-app via the onboarding AI disclosure + Talk banner).
- [ ] Production hardening is enforced by `app/core/config._guard_production`
      (refuses to boot on weak SECRET_KEY, demo admin password, seeded demo data,
      or wildcard CORS). Verify before deploy.

## 5. Known gaps to "world-class product" (be honest)

1. **Content depth** — sleep/meditation audio is procedurally synthesized
   on-device (real, but not a curated library of narrated stories). A content
   pipeline is the biggest retention lever.
2. **Clinical credibility** — copy is careful and non-clinical, but there is no
   efficacy study or clinician review. Category leaders (Headspace, Wysa, Youper)
   differentiate here.
3. **Validation** — no real-user usability testing, security audit, or load test at
   scale yet.

## 6. What's already strong

- Unified brand design system across iOS + web + admin.
- AI interaction (voice + agentic Oracle + inline activities) at/above category leaders.
- Safety & compliance: persistent AI disclosure, voice+text crisis detection,
  locale-aware crisis resources + region picker, account deletion/export, consent gating.
- **Verified end-to-end live:** backend (102 pytest) + iOS cloud tests
  (auth, chat, agentic plan, LLM-generated starters) pass against the running stack;
  full iOS UI suite green.

## 7. World-class flow-review implementation (Tiers 0–3)

A 5-reviewer deep flow review drove a 4-tier upgrade. **Tiers 0–2 are complete
and verified; Tier 3 is landed for everything buildable without external accounts.**

- **Tier 0 — safety & data loss (done):** locale-aware crisis on `/chat` + Oracle
  stream (no more hardcoded India); crisis surfaces on the primary chat path;
  journal entries persist their real body + timestamp.
- **Tier 1 — real & personal (done):** time-of-day + goal-aware Home with one clear
  next action; honest streak (no fake seed); real sleep player; entry-derived
  journal reflection; signed-out chat gets on-device replies.
- **Tier 2 — depth & retention (done):** streak grace-day + milestones; live sleep
  timer + volume + lock-screen transport; daily local-notification reminder; journal
  search + rotating prompts + emotion tags; multi-turn voice capture + interruption
  handling.
- **Tier 3 — business & compliance (done):** server-side free-tier quota on
  `/chat` + `/oracle` (429), consent enforcement (AI-memory off drops long-term
  recall), age + AI-disclosure attestation logging, `/auth/refresh` rate limit, and
  iOS compliance/consent sync on connect — **plus** the three items below that were
  previously deferred, now implemented with credential seams:
  - **Revenue:** server-side StoreKit 2 receipt validation (`services/appstore.py`:
    JWS + x5c chain, ES256, root-pinned when `appstore_root_cert_path` is set).
    `POST /users/me/subscription/verify` sets the authoritative tier at purchase;
    a **`POST /webhooks/appstore`** App Store Server Notifications V2 handler keeps
    it fresh over renewals/refunds/expiry (mapping via `appAccountToken` = user id,
    which iOS now stamps at purchase). `Products.storekit` is wired into the run
    scheme for simulator testing. Still needs YOUR App Store Connect products.
  - **SMS:** `services/sms.py` (Twilio) delivers trusted-contact phone/SMS alerts
    when configured (logs otherwise) — email contacts already worked.
  - **Auth hardening:** account lockout (5 fails → 15-min lock), server-side token
    revocation via `token_version` (logout + reset bump it), email verification +
    password reset with signed link tokens over a pluggable email service (SMTP
    when configured, else logged). Wire an SMTP provider to deliver real mail.
  - **Crisis escalation:** `TrustedContact` model + CRUD, consent-gated automatic
    notification on a detected crisis (`services/escalation.py`), and an ops alert
    (log + email to `ops_alert_email`). Wire an SMS provider for phone contacts;
    email contacts work as soon as SMTP is set.

### Still gated on YOUR external accounts (pure config — all code + webhooks done)
- **App Store Connect:** create the two subscription products, point the App Store
  Server Notifications URL at `POST /webhooks/appstore`, and (optionally) set
  `appstore_root_cert_path` to Apple's Root CA G3 to pin the chain.
- **SMTP provider:** set `smtp_*` to deliver verification / reset / trusted-contact
  emails (they're logged until then).
- **Twilio:** set `twilio_*` for phone/SMS trusted-contact delivery (logged until then).
