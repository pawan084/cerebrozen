# CereBro — Product Requirements & Roadmap

> Product definition, feature inventory with **honest implementation status**, developer
> checklist, and phase-wise roadmap. Derived from the 2026-07-03 full screen review +
> code ground-truth. Status legend: ✅ shipped & test-verified · 🟡 partial (works, with
> stated gaps) · ⚪ concept (screen/copy exists, feature does not).
>
> Companions: [ARCHITECTURE.md](ARCHITECTURE.md) (how it's built), [TODO.md](TODO.md)
> (debt), [SHIP_READINESS.md](SHIP_READINESS.md) (App Store runbook),
> [SLEEP_TRACKING.md](SLEEP_TRACKING.md) (validated sleep-module plan),
> [WEB_APP_PLAN.md](WEB_APP_PLAN.md) (browser client + admin v2),
> [INVESTOR_READINESS.md](INVESTOR_READINESS.md) (benchmarks + gap list).

## 1. Product definition

CereBro is a **privacy-first daily mental-fitness companion**: check in, get an
AI-personalized daily plan, talk it out with a voice/chat companion, journal privately,
sleep better — with crisis-safe boundaries and a human-support handoff. Non-medical by
design; every AI surface carries the "not a therapist or crisis service" boundary.

**Differentiator:** the combination of agentic daily plan + AI voice companion + private
journal + region-aware crisis safety in one calm, dark, native iOS experience.
Positioning: B2C first (Calm/Youper/Rosebud territory), B2B-ready later.

## 2. Feature inventory (by module, with status)

### Onboarding
| Feature | Status | Notes |
|---|---|---|
| Welcome (Get started / returning sign-in / DEBUG preview) | ✅ | Returning users skip the flow entirely |
| Age gate (affirmative 18+, gated Continue) | 🟡 | Works; no under-18 exit path, attestation stamped at first connect (TODO) |
| AI limitation disclosure | ✅ | Can/can't cards; re-disclosed every 3 h in Talk/Chat |
| One-tap state check (6 feelings → taxonomy) | ✅ | Replaced the chip questionnaire (2026-07-03, "90-second" flow); syncs on connect |
| First reset (guided breathing before any account ask) | ✅ | Skippable; completing it starts the streak |
| Mini-plan before signup | ✅ | Gives the account ask its reason ("save this") |
| Baseline (stress/sleep 1–5) | ⚪ | Removed from onboarding; reintroduce contextually (TODO) |
| Companion persona | 🟡 | Defaults to Calm Guide; picker removed from onboarding, needs a settings home |
| Account step (Apple/Google/email embedded form, Maybe later) | 🟡 | Email ✅ · Apple needs portal capability · Google needs OAuth client id |
| Consent — private by default, no pre-ticks, recommended card | ✅ | Enforced server-side (AI-memory off drops long-term history) |
| Language (5 options, now before the value moment) | 🟡 | Persisted; only starters generation honors it; UI not localized |
| Notifications opt-in | 🟡 | Works; multi-select UI for a single slot, "Private previews" option is inert |
| First plan | ✅ | Title keys off first goal (4 mapped, rest fall through) |

### Home / daily loop
| Feature | Status | Notes |
|---|---|---|
| Greeting + goal-aware daily focus | ✅ | Time-of-day + first goal |
| 20-second mood check-in → next best action | ✅ | Updates Home; records streak |
| Streak (grace day, milestones, week dots) | ✅ | Deliberately gentle |
| Morning/afternoon recommendation rails | 🟡 | Static `Dummy` catalogue (TODO: server `/content`) |
| Agentic daily plan (generate, update, step completion) | ✅ | Server-driven when connected (real LLM), local fallback |
| Search | 🟡 | Searches the local static catalogue only |

### Sleep
| Feature | Status | Notes |
|---|---|---|
| Player: real audio, mix layers, volume, auto-stop fade timer | ✅ | Bundled studio loops + synth fallback; lock-screen controls |
| Favorites | ✅ | Persisted by title |
| Stories/meditation catalogue | 🟡 | Static `Dummy` items; audio maps to 4 bundled loops by keyword |
| Downloads | ⚪ | Paywall copy only — no download feature exists |
| Sleep diary + morning check-in (manual, quality/bed/wake) | ⚪ | **Validated GO** — research-backed plan in [SLEEP_TRACKING.md](SLEEP_TRACKING.md); `sleep_logs` + `/sleep` API + iOS check-in |
| Wind-down program (CBT-I-informed, non-diagnostic) | ⚪ | The evidence engine (dCBT-I: ISI SMD −0.85, depression −0.47); ships as `/content` items, finally retiring the `Dummy` sleep rails |
| Real sleep insights (duration/consistency trends, sleep × mood) | ⚪ | Replaces today's illustrative Insights strings with computed diary data |
| HealthKit sleep read (Apple Watch stages, opt-in) | ⚪ | v1.5 enhancer only — phone-only staging rejected by evidence; never a headline accuracy claim |

### Talk (voice + chat)
| Feature | Status | Notes |
|---|---|---|
| Voice loop (mic→STT→LLM→TTS, sentence-streamed, barge-in, VAD) | ✅ | Live-verified; needs Deepgram/ElevenLabs keys |
| Oracle agent (tools, confirm-before-write, SSE streaming) | ✅ | Durable Postgres checkpoints |
| Text chat fallback + signed-out local companion | ✅ | |
| Personalized conversation starters | ✅ | LLM + curated anchor first; live-verified e2e |
| Inline activity widgets + suggestion chips | ✅ | 8+ activities launch native screens |
| Crisis banner on voice + text paths | ✅ | Backend risk scan; never blocks |
| SOS reset, breathing pacer, grounding, CBT, micro-activities | ✅ | All with background ambience + mute toggle |
| Voice loading/error/free-limit states | 🟡 | Real states; "free voice minutes" copy vs actual server quota = 50 msgs/day |

### Journal
| Feature | Status | Notes |
|---|---|---|
| Entries: prompts, tags, search, history, offline reopen | ✅ | Local-first, mirrors to server when connected |
| AI reflection | ✅ | Derived reflection + reframe |
| Face ID lock | ✅ | Graceful when no biometrics |
| Private mode (what AI can read) | ✅ | Consent-gated |

### Insights & memory
| Feature | Status | Notes |
|---|---|---|
| Weekly insights (sessions, entries, sleep/mood trends) | 🟡 | Server-generated when connected; illustrative locally |
| Pattern dashboard ("stress spikes after meetings") | ⚪ | Illustrative copy — no real pattern mining |
| Memory detail / edit / delete | 🟡 | View + delete real; granular editing is concept |
| Export report | ✅ | Full server export (`GET /users/me/export`) |

### Premium
| Feature | Status | Notes |
|---|---|---|
| 3 tiers (Free / ₹499 / ₹1,499), paywall, StoreKit 2 | ✅ | Server-side receipt verification, renewal webhook; needs ASC products |
| Free-tier quota (midnight-UTC daily cap, 429) | ✅ | Chat + Oracle |
| Paywall copy honesty | ✅ | Fixed 2026-07-03 — claims now match shipped features (app + web) |
| Coach/therapist booking (Premium+Human) | ⚪ | Screens are now honestly labeled "rolling out"; provider integration still absent |

### Safety & crisis
| Feature | Status | Notes |
|---|---|---|
| Region-aware crisis resources (7 regions + intl), override picker | ✅ | Mirrored backend/iOS (cross-stack contract) |
| Trusted contact + consent-gated crisis escalation | ✅ | Email/SMS via SMTP/Twilio when configured, ops alert |
| Human support handoff | ⚪ | Screens exist; booking inert |
| AI boundary messaging everywhere | ✅ | Banner + periodic re-disclosure |

### Accounts, sync, platform
| Feature | Status | Notes |
|---|---|---|
| Auth: email (+ lockout, revocation, verify/reset emails) | ✅ | Hardened |
| Sign in with Apple / Google | 🟡 | Code + entitlement done; portal/OAuth config pending |
| Sync: plan, journal, check-ins, consent, region, assessment, attest | ✅ | Additive; app fully local offline |
| Offline mode | 🟡 | Genuinely local-first; the "offline" showcase screen itself is static |
| Account deletion (typed DELETE, full cascade) | ✅ | |
| Privacy policy + labels | ✅ | In-app + web + PRIVACY_LABELS.md |
| Calm games (8) | ✅ | VoiceOver-labelled |
| Push notifications / nudges | 🟡 | Server scheduler + honest dispatch done; needs APNs key |
| Localization | ⚪ | English UI only; language selection feeds starters only |

## 3. Developer checklist (turns 🟡/⚪ into ✅)

**Owner-credentials (no code):** rotate exposed provider keys · Apple portal SIWA
capability + `APPLE_CLIENT_ID` · Google OAuth client + `GIDClientID` · ASC subscription
products + Server-Notifications URL · `SMTP_*`, `TWILIO_*`, `OPS_ALERT_EMAIL`, `APNS_*`,
`ASC_*` secrets.

**Code, ordered by impact:**
1. ~~Honest paywall copy~~ — DONE 2026-07-03: "downloads"/"unlimited voice"/"coach &
   therapist booking" claims scrubbed from the app paywall, free-limit screen,
   fallback price cards, and the web landing pricing + FAQ. Also de-faked: coach
   booking (no invented clinicians, honest "rolling out" capture), insights hero
   (example labeled as example), "See all" affordances, static rows' chevrons.
2. Server-driven content catalogue (`/content` route exists; migrate Home/Sleep rails).
3. Bundle imagery for remaining ~13 hero/rail Unsplash URLs (offline + App Review).
4. Onboarding polish: back navigation, single-select reminders, under-18 exit,
   client-side attestation timestamp.
5. Chat/Oracle prompts honor `User.language`.
6. Real pattern mining for the dashboard (or clearly label as examples).
7. Voice free-limit copy ↔ actual quota alignment.
8. UITest auto-dismiss for the iOS Local Network prompt (fresh-install device runs).
9. VoiceOver live announcements for streaming chat.

**Deliberately deferred:** human-support booking marketplace (needs providers + legal),
full UI localization, Android (scaffold exists), real downloads.

## 4. Phase-wise roadmap

**Phase 0 — TestFlight (days):** owner credentials above · checklist #1 ·
push commits + CI green · TestFlight via existing fastlane workflow.

**Phase 1 — App Store v1 (1–2 weeks):** checklist #2–#5 · content depth (real
meditation scripts/audio beyond 4 loops) · privacy labels into ASC · beta feedback loop.

**Phase 2 — Post-launch growth (1–2 months):** **sleep tracking module v1** (diary +
CBT-I-informed wind-down program + real sleep insights — validated plan in
[SLEEP_TRACKING.md](SLEEP_TRACKING.md)) · real insights/patterns (#6) · **first-party
privacy-preserving analytics + annual plan/trial design** (investor gaps #1/#3 in
[INVESTOR_READINESS.md](INVESTOR_READINESS.md)) · adaptive reminders · downloads if
premium promises them · deeper voice polish · Android start.

**Phase 2.5 — Web app v1 (parallel track):** slim authenticated browser client at
`app.cerebrozen.in` (auth, check-ins, journal, chat, plans, insights, account) + admin
v2 (session refresh, analytics tab, user support) — scope in
[WEB_APP_PLAN.md](WEB_APP_PLAN.md). Deliberately a subset, not parity (the verified
market-leader pattern).

**Phase 3 — Expansion:** B2B/enterprise offering (employer reporting on the web app) ·
Stripe web billing · human-support marketplace · HealthKit sleep read (module v1.5) ·
full localization (Hindi/Tamil first).

## 5. Verification snapshot (2026-07-03)

Backend 177 tests / 95%+ coverage (hermetic, in-container) · iOS 19 UITests green on
simulator **and** physical iPhone 16 incl. live-LLM cloud flows · web/admin typecheck
clean · e2e Playwright in CI. Everything in "✅" above is covered by at least one of
these suites or was manually verified on device this week.
