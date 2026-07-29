# CereBro — Product Requirements & Roadmap

> Product definition, feature inventory with **honest implementation status**, developer
> checklist, and phase-wise roadmap. Status legend: ✅ shipped & test-verified · 🟡 partial
> (works, with stated gaps) · ⚪ concept (screen/copy exists, feature does not).
>
> **Provenance.** Originally derived from the 2026-07-03 full screen review. **Re-verified
> against source on 2026-07-30**, at `main` = `5ef7416` (2026-07-13) **plus the uncommitted
> working tree on branch `fix/ui-worldclass-103`** (63 modified + 10 new files; `HEAD ==
> main`, so nothing on that branch is committed yet). **Rows and notes marked ‡ depend on
> that uncommitted work — a reader must not treat them as merged reality.** Every 🟡/⚪ row
> below was re-read in code this pass; notes state the specific remaining gap, and
> distinguish *blocked on owner credentials* from *blocked on code still to write*.
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
journal + region-aware crisis safety in one calm, dark, native experience — now on iOS
**and** a near-parity native Android client, with a slim authenticated web client.
Positioning: B2C first (Calm/Youper/Rosebud territory), B2B-ready later.

## 2. Feature inventory (by module, with status)

### Onboarding
| Feature | Status | Notes |
|---|---|---|
| Welcome (Get started / returning sign-in / DEBUG preview) | ✅ | Returning users skip the flow entirely |
| Age gate (affirmative 18+, gated Continue) | ✅ | Under-18 exit message; tap time persisted on-device and carried by attest() |
| AI limitation disclosure | ✅ | Can/can't cards; re-disclosed every 3 h in Talk/Chat |
| One-tap state check (6 feelings → taxonomy) | ✅ | Replaced the chip questionnaire (2026-07-03, "90-second" flow); syncs on connect |
| First reset (guided breathing before any account ask) | ✅ | Skippable; completing it starts the streak |
| Mini-plan before signup | ✅ | Gives the account ask its reason ("save this") |
| Baseline (stress/sleep 1–5) | ✅ | Contextual Home ask after 3 check-ins; feeds Insights "Your starting point" |
| Companion persona | ✅ | "Companion style" picker in You (4 styles); synced to the server profile |
| Account step (Apple/Google/email embedded form, Maybe later) | 🟡 | Email + emailed one-time code ✅. **Apple: owner credential only** — `CereBro.entitlements` ships `com.apple.developer.applesignin`, backend verifies JWKS; needs the portal capability + `APPLE_CLIENT_ID`. **Google: credential *and* code** — `apps/ios/Info.plist` has no `GIDClientID` or reversed-URL scheme, so there is nowhere for the owner to drop the client id; `GoogleAuth.isConfigured` is false and the button degrades |
| Consent — private by default, no pre-ticks, recommended card | ✅ | Enforced server-side (AI-memory off drops long-term history). All six DPDP categories are shown at the moment of consent; Android and the web client ‡ now default every toggle **off** |
| Language (5 options, now before the value moment) | 🟡 | Persisted, but read in exactly one place: starter/topic generation (`services/assessment.py` — "Write every topic in {language}"). Chat and Oracle prompts never read `User.language`; iOS/web UI is not localized |
| Notifications opt-in | ✅ | Single-select (one slot, one choice); inert "Private previews" removed |
| First plan | ✅ | Title keys off first goal (4 mapped, rest fall through) |

### Home / daily loop
| Feature | Status | Notes |
|---|---|---|
| Greeting + goal-aware daily focus | ✅ | Time-of-day + first goal |
| 20-second mood check-in → next best action | ✅ | Updates Home; records streak |
| Streak (grace day, milestones, week dots) | ✅ | Deliberately gentle; the web client ‡ now shows the *current* run only (it previously showed best-ever in its place) |
| Morning/afternoon recommendation rails | ✅ | Server `/content` (time-matched kinds), curated local fallback offline (2026-07-04) |
| Agentic daily plan (generate, update, step completion) | ✅ | Server-driven when connected (real LLM), local fallback |
| Search | ✅ | Searches the whole served `/content` catalogue; local fallback offline (2026-07-04) |

### Sleep
| Feature | Status | Notes |
|---|---|---|
| Player: real audio, mix layers, volume, auto-stop fade timer | ✅ | Bundled studio loops + synth fallback; lock-screen controls |
| Favorites | ✅ | Persisted by title |
| Narrated audio pipeline (TTS script → MP3 → `/media`) | 🟡 | Real end-to-end: `narration_script`/`audio_url`/`audio_generated_at` on `content_items` (migration `a7c4e9f2d310`), `POST /admin/content/{id}/narrate` calls ElevenLabs, `media.py` writes `media/narration/{id}.mp3`, `/media` is a mounted static dir, and all three clients stream it (iOS `AVPlayer` in `SoundscapePlayer.playNarration`, Android ExoPlayer in `AmbientService.createStream`, web `<audio>`), each falling back to the bundled bed on failure. **Gaps:** no batch/cron narrator — the seed writes scripts but never `audio_url`, so a fresh prod DB has **zero MP3s** until an admin clicks "Generate audio" per item; and `/media` is unauthenticated, so a premium item's MP3 is fetchable by URL without an entitlement check |
| Stories/meditation catalogue | 🟡 | All rails (Sleep, Home, Programs, Search) server-driven via `/content` with local fallback (2026-07-04). Per-item `audio_url` is now the primary path and the keyword→bundled-loop map is only the fallback — but until items are narrated (above) every item still resolves to one of the 4 bundled loops in practice. Android's URL registry is title-keyed and unpopulated for the Sleep hero, which therefore always plays the bed |
| Downloads | ⚪ | **No download code exists in any client** — no `AVAssetDownloadTask`, no ExoPlayer `DownloadService`, and `apps/app/public/sw.js` is explicitly "Web Push only (no fetch interception/caching)". Two shipped strings still assert otherwise: iOS `Dummy.offline` ("Downloaded soundscape · Available offline") and the web library footnote ("offline playback live in the iOS & Android apps") |
| Sleep diary + morning check-in (manual, quality/bed/wake) | ✅ | Shipped 2026-07-03: iOS check-in (Home + Sleep tab), 7-night trend strip, diary history — local-first, mirrored to `/sleep` (`sleep_logs`); UITest-covered. Plan: [SLEEP_TRACKING.md](SLEEP_TRACKING.md) |
| Wind-down program (CBT-I-informed, non-diagnostic) | ✅ | Shipped 2026-07-03: "Wind down tonight" guide (`wind_down` catalogue kind, admin-authorable, offline fallback; breathing tip opens the pacer). Evidence base: dCBT-I ISI SMD −0.85, depression −0.47 |
| "Sleep Reset" 7-day CBT-I-informed program | 🟡 | Seeded as a first-class program with all seven day themes (`seed.py` `_DAY_GUIDES`), stored in `content_items.day_guides` (migration `b8e6d1a4f527`), served by `GET /programs/active` as `today_guide` (clamped to the last day), authorable in the admin day-guides editor, and covered by `tests/test_programs.py`. **Gap: only Android renders the guide.** iOS `RemoteProgram` and the web `Active` type have no `today_guide` field — both still show only "day N of 7" |
| Real sleep insights (duration trends, sleep × mood) | 🟡 | Backend is real and consent-gated (`services/insights.py`: avg duration from `sleep_logs`; the sleep×mood sentence needs ≥3 sleep rows and ≥2 moods per bucket with a ≥0.5 gap). **Gap: the iOS Insights screen never shows it** — it renders `Dummy.weeklyMetrics` ("Sleep consistency / Improving / 0.62") and only overrides two rows locally; the server `insight` is rendered solely in `CloudSyncView` |
| HealthKit / Health Connect sleep read (opt-in) | 🟡 | Both platforms shipped: iOS read-only `HKCategoryType(.sleepAnalysis)` with the entitlement + `NSHealthShareUsageDescription`; Android `SleepSessionRecord` with `READ_SLEEP` + rationale intent-filter. Off-by-default check-in pre-fill, user confirms, never a headline accuracy claim. **Blocked on an owner credential:** the App ID needs the HealthKit capability for physical-device iOS builds (simulator works without it). Manual-tap only — no background/observer query on either platform |

### Talk (voice + chat)
| Feature | Status | Notes |
|---|---|---|
| Voice loop (mic→STT→LLM→TTS, sentence-streamed, barge-in, VAD) | ✅ | Live-verified; needs Deepgram/ElevenLabs keys |
| Oracle agent (tools, confirm-before-write, SSE streaming) | ✅ | Durable Postgres checkpoints; `interrupt()` before every write tool, resumed by `POST /oracle/confirm`. Note: the confirmation is **ephemeral** — approvals and declines are never persisted, so there is no audit table and no admin review surface (see checklist) |
| Text chat fallback + signed-out local companion | ✅ | |
| Personalized conversation starters | ✅ | LLM + curated anchor first; live-verified e2e |
| Inline activity widgets + suggestion chips | ✅ | 8+ activities launch native screens; deterministic keyword+risk routing (`services/activities.py`), no LLM round-trip |
| Crisis banner on voice + text paths | ✅ | Backend risk scan; never blocks. Tele-MANAS 14416 now leads the returned lines |
| SOS reset, breathing pacer, grounding, CBT, micro-activities | ✅ | All with background ambience + mute toggle |
| Voice loading/error/free-limit states | 🟡 | Loading/error states are real. The free-limit story is not: **no client anywhere handles the 429** (zero matches for `429` across iOS, Android and the web client), so the server's "Daily free limit reached (50 messages)" never reaches the user — it surfaces as a generic failure. iOS's explanatory `FreeLimitView` is dead code (declared, never presented), and its corrected copy states no number and implies local midnight (the reset is UTC). Android still advertises "unlimited voice", which implies a voice meter that does not exist (voice is not metered at all) |

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
| Weekly insights (sessions, entries, sleep/mood trends) | 🟡 | Server-generated and consent-gated when connected. Same gap as sleep insights: the iOS Insights screen renders `Dummy.weeklyMetrics` rather than `backend.insight`, so a connected user still sees illustrative rows there |
| Pattern dashboard ("stress spikes after meetings") | ✅ | **No longer illustrative.** `GET /insights/patterns` → `services/insights.compute_patterns` mines a 60-day window with four thresholded, consent-gated rules (hardest time of day, journaling→next-day calm, sleep→mood, weekday rhythm), each returning `{statement, basis}` where `basis` is the real supporting count; thin data returns `[]` with `enough_data: false`. Covered by `tests/test_patterns.py`; rendered on iOS (`PatternDashboardView`), Android (`PatternScreen`) and the web client ‡ (which this pass converted from three hardcoded cards to the live fetch + an honest empty state). Residual gap: clients ignore `enough_data`/`sources`, so "consent off" and "no data yet" read identically |
| Memory detail / edit / delete | 🟡 | Viewing and an all-or-nothing wipe are real (`DELETE /users/me/memory` clears chat, insights and the LangGraph checkpoint rows). **Granular editing is not merely unbuilt — it is not implementable against the current schema:** patterns are computed on the fly and never persisted, so there is no addressable row to edit or suppress, and no per-item route exists |
| Export report | ✅ | Full server export (`GET /users/me/export`) |

### Premium
| Feature | Status | Notes |
|---|---|---|
| 3 tiers (Free / ₹499 / ₹1,499), paywall, StoreKit 2 | ✅ | Server-side receipt verification, renewal webhook; needs ASC products |
| Stripe web billing | 🟡 | Code-complete end-to-end and tested (`tests/test_stripe.py`): `POST /billing/checkout` (rate-limited, 503 when unconfigured), hand-rolled HMAC-SHA256 webhook verification with 300 s replay tolerance, price-id→tier map, downgrade on `deleted`/`canceled`/`unpaid`. Wired to the web client's account page. **Inert on owner credentials** (`stripe_enabled == bool(STRIPE_SECRET_KEY)`; all six `STRIPE_*` vars blank). **Code gaps regardless of keys:** no `stripe_customer_id` persisted (mapping relies on metadata surviving every event), no webhook event-id idempotency, no billing-portal/cancel route |
| Free-tier quota (midnight-UTC daily cap, 429) | ✅ | Chat + Oracle; real DB-count enforcement at `FREE_DAILY_MESSAGES=50`. Server side only — no client renders the 429 (see the Talk row) |
| Paywall copy honesty | 🟡 | The 2026-07-03 scrub held for the iOS paywall and the web landing pricing/FAQ. Three over-claims survive elsewhere: Android's "unlimited voice" upsell string, the web library's "offline playback live in the iOS & Android apps" footnote, and iOS `Dummy.offline`'s "Downloaded soundscape" |
| Coach/therapist booking (Premium+Human) | ⚪ | No provider, no booking route, no model, no interest capture — iOS `CoachBookingView` is local state with hardcoded time chips and a "Notify me" button that discards the signal on teardown (it does say "nothing is scheduled yet"). **Sharpest honesty risk in the product:** `premium_human` passes the `billing.py` tier regex and is sellable through both Stripe and StoreKit, while buying only an unlimited chat quota |

### Safety & crisis
| Feature | Status | Notes |
|---|---|---|
| Region-aware crisis resources (7 regions + intl), override picker | ✅ | Mirrored backend/iOS/Android/web (cross-stack contract); Tele-MANAS 14416 leads every surface |
| Always-available Support page (web client) | ✅‡ | New public `/support` route: no session guard, no fetch, no client JS, so it renders with a dead API or an expired session; static `lib/crisis.ts` directory mirrors iOS/Android/backend |
| Trusted contact + consent-gated crisis escalation | ✅ | Email/SMS via SMTP/Twilio when configured, ops alert |
| Human support handoff | 🟡 | What ships is a static directory of external doors (Tele-MANAS, iCall, findahelpline) on web ‡ and Android — real, offline-safe, and honest. What does not exist is any in-product handoff: no provider, no availability, no booking |
| AI boundary messaging everywhere | ✅ | Banner + periodic re-disclosure |
| Admin safety queue (review + resolve) | ✅ ‡ | Private text never travels with the queue: `SafetyEventOut` carries `excerpt_chars`, not `excerpt`, so the list shows "N characters, hidden". Reading one person's words is a separate `GET /admin/safety/{id}/excerpt` that emits an audit line naming the admin. `PATCH …/resolve` requires a non-blank note (whitespace-only is refused by a validator) and writes `resolved_by`/`resolved_at`/`resolution_note` — migration `c7a4e91b6d38`. Test-covered end to end in `test_admin.py` |

### Accounts, sync, platform
| Feature | Status | Notes |
|---|---|---|
| Auth: email (+ lockout, revocation, verify/reset emails) | ✅ | Hardened |
| Auth: emailed one-time code (passwordless) | ✅ | iOS + web sign-in; creates the account at verify |
| First-party anonymous analytics (onboarding funnel, paywall) | 🟡 | Real and privacy-clean where it ships: allowlisted events only, random install id (never account-linked), zero third-party SDKs, opt-out toggle, admin funnel chart — on backend, iOS and Android. **Gap: the web clients never post to `/events`**, so the `source` values `web`/`app` are unused and the browser funnel is invisible in admin |
| Sign in with Apple / Google | 🟡 | Backend verification complete for both (JWKS, dual issuer, audience). Apple: iOS button + entitlement ship, **blocked on the portal capability + `APPLE_CLIENT_ID`**; Android has no Apple path at all (net-new code). Google: iOS/Android/web flows are written, **blocked on an OAuth client** *and*, on iOS, on adding `GIDClientID` + the reversed-URL scheme to `Info.plist` |
| Sync: plan, journal, check-ins, consent, region, assessment, attest | ✅ | Additive; app fully local offline (zero remote images — all `Dummy.Img` entries are `asset:` bundles) |
| Offline mode | 🟡 | The behaviour is genuinely local-first (server-first with curated local fallback on Home/Sleep, on-device journal analysis, `LocalCompanion` chat). The static "Offline Mode" showcase screen is unchanged **and unreachable** — `OfflineView` (and the rest of `StateViews.swift`) has no reference anywhere in the app, and its copy still claims "downloaded sounds" |
| Account deletion (typed DELETE, full cascade) | ✅ | |
| Privacy policy + labels | ✅ | In-app + web + PRIVACY_LABELS.md |
| Calm games (8) | ✅ | VoiceOver-labelled |
| Local reminders (iOS `UNUserNotificationCenter`, Android `AlarmManager`) | ✅ | This is what "notifications" actually delivers today on both mobile clients |
| Web Push (VAPID) | ✅ | The only fully wired remote-push channel: `services/webpush.py` (pywebpush, prunes 404/410 endpoints) + `apps/app/lib/push.ts` subscription registration. Self-generated keys — no vendor account needed |
| Remote push (APNs / FCM) + nudge dispatch | 🟡 | Scheduler is real and production-shaped: in-process dispatcher on `NUDGE_DISPATCH_INTERVAL_MINUTES`, `SELECT … FOR UPDATE SKIP LOCKED` so multiple workers are safe, contextual/bedtime-derived scheduling, fallback chain web-push → email. The APNs sender is real (ES256 `.p8` JWT, HTTP/2). **This is not "just needs an APNs key":** iOS never registers a device token — there is no `AppDelegate`, no `registerForRemoteNotifications`, `APIClient.registerPushToken` has zero call sites, the entitlement has no `aps-environment` and `UIBackgroundModes` is `audio` only — so `user.push_token` can never be populated and the APNs branch is unreachable. FCM is 100% absent (no Firebase in Android, no sender in backend) |
| Localization | 🟡 | Android has a DRAFT `values-hi` — **662 of 731 keys** translated (69 fall back to English, including the whole TIPP and CBT tools, the onboarding disclosure and privacy-policy cards), header-marked "pending qualified clinical review"; crisis/support copy was translated this pass ‡ so a Hindi-preferring user in crisis no longer meets an English wall. iOS and the web clients are **not** localized at all (zero `.lproj`/`.xcstrings`, zero `NSLocalizedString`, no `next-intl`). The one cross-platform exception is the consent notice, hand-shipped in 13 languages on iOS and web |

### Android (native client)
| Feature | Status | Notes |
|---|---|---|
| Feature parity with iOS | 🟡 | The full IA now exists in Compose — onboarding, Today, Talk, Journal, Sleep, Search, Plan, Programs, Patterns, Tools, Games, Settings, biometric gate, consent notice, guided tour. **Gaps: no billing/paywall** (strings say so plainly: "Billing isn't wired on Android yet — Play Billing lands with Play Console setup"), no remote push, no Sign in with Apple |
| Dawn light theme + contrast gate | ✅ | Theme-aware token getters, so every screen got Dawn without a migration; `ContrastTest` gates both palettes ≥4.5:1 and pins the Night palette byte-identical |
| Health Connect sleep read | ✅ | See the Sleep module row |
| Hindi (`values-hi`) | 🟡 | See Localization above — DRAFT, pending clinical sign-off |
| Consent defaults, a11y and reduce-motion pass | ✅‡ | All six onboarding consent toggles now default off, with a retry + `consent_sync_failed` flag when the post-signup write fails |
| Play Billing | ⚪ | No service, no client code, no Play Console setup |

## 3. Developer checklist (turns 🟡/⚪ into ✅)

**Owner-credentials (no code):** rotate exposed provider keys · Apple portal SIWA
capability + `APPLE_CLIENT_ID` · Apple portal HealthKit capability (device builds) ·
Google OAuth client · ASC subscription products + Server-Notifications URL · `STRIPE_*`
(secret, webhook secret, 4 price ids) · `SMTP_*`, `TWILIO_*`, `OPS_ALERT_EMAIL`, `APNS_*`,
`ASC_*` secrets.

**Code, ordered by impact:**
1. ~~Honest paywall copy~~ — DONE 2026-07-03 for the iOS paywall and web pricing/FAQ.
   **Reopened, narrowly:** three over-claims remain — Android's "unlimited voice",
   the web library's "offline playback" footnote, and iOS `Dummy.offline`'s
   "Downloaded soundscape". Delete or qualify all three.
2. ~~Server-driven content catalogue~~ — DONE 2026-07-04 (all rails via `/content`).
3. ~~Bundle imagery for remaining hero/rail Unsplash URLs~~ — DONE; every `Dummy.Img`
   entry is now an `asset:` reference, zero remote images.
4. ~~Onboarding polish (back nav, single-select reminders, under-18 exit,
   client-side attestation timestamp)~~ — DONE 2026-07-03.
5. ~~Real pattern mining for the dashboard~~ — DONE: `/insights/patterns` with
   thresholded, consent-gated rules and honest empty states on all three clients.
6. **iOS remote push is unreachable** — add an `AppDelegate`/`UIApplicationDelegateAdaptor`,
   call `registerForRemoteNotifications()`, wire the existing `APIClient.registerPushToken`,
   add `aps-environment` to the entitlement and `remote-notification` to `UIBackgroundModes`.
   Without this the APNs key buys nothing.
7. Wire the iOS Insights screen to `backend.insight` (Dummy only when `insight == nil`),
   so server-computed sleep/mood actually reaches the screen users open.
8. Render `today_guide` on iOS and web so "Sleep Reset" is a 7-day program everywhere,
   not only on Android.
9. Narrate the seeded catalogue: a batch/cron narrator (or a seed-time pass), plus an
    entitlement check in front of `/media` for premium items.
10. Handle 429 in all clients and present the free-limit state (iOS `FreeLimitView` is
    dead code); state the actual cap and that the reset is UTC midnight.
11. Chat/Oracle prompts honor `User.language` (today only starter generation does).
12. Add `GIDClientID` + reversed-URL scheme to `apps/ios/Info.plist` so the owner's
    Google client id has somewhere to live.
13. Persist Oracle tool confirmations (approve/decline) as an audit trail, and give admin
    a pending-confirmations surface — today the only durable trace is the LangGraph
    checkpoint.
14. Post analytics events from the web clients so the browser funnel is not invisible.
15. Stripe hardening: persist `stripe_customer_id`, dedupe webhooks by event id, add a
    billing-portal/cancel route.
16. UITest auto-dismiss for the iOS Local Network prompt (fresh-install device runs).
17. VoiceOver live announcements for streaming chat.
18. Finish `values-hi` (69 keys) and get the clinical/linguistic sign-off the file header
    is waiting on.

**Deliberately deferred:** human-support booking marketplace (needs providers + legal),
full UI localization beyond Hindi, real downloads, Play Billing, an interventions engine
that acts on mined patterns (today patterns are display-only).

## 4. Phase-wise roadmap

**Phase 0 — TestFlight (days):** owner credentials above · checklist #1, #6, #7 ·
push commits + CI green · TestFlight via existing fastlane workflow.

**Phase 1 — App Store v1 (1–2 weeks):** checklist #8–#12 · content depth (narrate the
seeded catalogue so per-item audio is real, not just possible) · privacy labels into
ASC · beta feedback loop.

**Phase 2 — Post-launch growth (1–2 months):** ~~sleep tracking module v1~~ (diary +
CBT-I-informed wind-down + "Sleep Reset" + server-side sleep insights — shipped; the
remaining work is client rendering, #9/#10) · ~~real insights/patterns~~ (shipped) ·
**first-party privacy-preserving analytics + annual plan/trial design** (investor gaps
#1/#3 in [INVESTOR_READINESS.md](INVESTOR_READINESS.md); analytics shipped on mobile,
#16 remains) · adaptive reminders · downloads if premium promises them · deeper voice
polish · ~~Android start~~ (Android is now at near-parity — see its module above).

**Phase 2.5 — Web app v1 (parallel track):** slim authenticated browser client at
`app.cerebrozen.in` — scope in [WEB_APP_PLAN.md](WEB_APP_PLAN.md); deliberately a
subset, not parity (the verified market-leader pattern). Shipped 2026-07-03
(`apps/app`: auth + refresh sessions, mood check-in, Oracle-streaming chat with crisis
banner + tool confirms, journal, sleep diary, plan, programs, library, games, insights,
patterns, account with consent/region/contact/export/delete). Stripe checkout is now
wired and content pages exist. Remaining: Google/Apple sign-in (owner credentials),
analytics events (#16), admin user-support tooling.

**Phase 3 — Expansion:** B2B/enterprise offering (employer reporting on the web app) ·
human-support marketplace · Play Billing · an interventions engine that turns mined
patterns into nudges/plan changes · full localization (Hindi first, then Tamil).

## 5. Verification snapshot (2026-07-30)

Run this session, against the working tree described in the provenance note:

- **Backend pytest (in-container, hermetic): 262 passed · 2 skipped · 95% coverage** —
  clears the `--cov-fail-under=95` CI gate. This supersedes the previously quoted
  "177 tests / 95%+", which was stale.
  An intermediate run during this session showed 261 passed · 1 failed · 94%: the safety
  review queue was mid-change, and `tests/test_admin.py::test_admin_safety_queue_and_resolve`
  422'd because the resolve route had just started requiring a resolution note. The test was
  then rewritten to the new contract (queue carries no excerpt · the excerpt endpoint 404s
  on an unknown id · a blank note is refused · a resolved row records who and when) rather
  than relaxed, which is what took coverage 92% → 95%.
- **Alembic: single head at `c7a4e91b6d38`** (safety-event resolution audit), applied
  cleanly from `b8e6d1a4f527`. ‡
- **Android unit tests: 196 tests, 0 failures** (Gradle, this branch).
- **Web / admin / web-app typecheck: clean** (`tsc --noEmit`, all three).

Not run this session, and therefore **not** current evidence for any row above:

- **iOS UITests** — the "19 UITests green on simulator and physical iPhone 16 incl.
  live-LLM cloud flows" result dates from 2026-07-03 and has not been re-run against
  the changes since.
- **Playwright e2e** (web + admin + app) — last known green in CI on `main`; the
  uncommitted UI pass touches `e2e/tests/admin.spec.ts` and `e2e/tests/app.spec.ts`
  and has not been re-run here.

Everything marked ✅ is covered by at least one of the suites above **or** was read in
source this pass; where a ✅ rests only on a source read rather than a test, the note says
what the code does. Rows marked ‡ exist only in the uncommitted `fix/ui-worldclass-103`
working tree.
