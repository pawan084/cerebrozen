# CereBro — Product Requirements & Roadmap

> Product definition, feature inventory with **honest implementation status**, developer
> checklist, and phase-wise roadmap. Status legend: ✅ shipped & test-verified · 🟡 partial
> (works, with stated gaps) · ⚪ concept (screen/copy exists, feature does not).
>
> **Provenance.** Originally derived from the 2026-07-03 full screen review. **Re-verified
> against source on 2026-07-30**, at `main` = `5ef7416` (2026-07-13) **plus branch
> `fix/ui-worldclass-103`**, which is committed but **not merged to `main`**. **Rows and
> notes marked ‡ depend on that branch — a reader on `main` must not treat them as shipped
> reality.** Every 🟡/⚪ row below was re-read in code this pass; notes state the specific
> remaining gap, and distinguish *blocked on owner credentials* from *blocked on code still
> to write*. Tally on 2026-07-30 after checklist #1/#6/#7/#8 landed: **53 ✅ / 16 🟡 / 3 ⚪**.
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
| Account step (Apple/Google/email embedded form, Maybe later) | 🟡 | Email + emailed one-time code ✅. **Apple: owner credential only** — `CereBro.entitlements` ships `com.apple.developer.applesignin`, backend verifies JWKS; needs the portal capability + `APPLE_CLIENT_ID`. **Google: credential only** — `GoogleAuth.clientID` reads `GIDClientID` from `Info.plist`, so the slot exists; the key and its reversed-URL scheme both need the real id, so neither is committed (a placeholder scheme would be worse than absent — see TECHNICAL.md); `GoogleAuth.isConfigured` is false and the button degrades |
| Consent — private by default, no pre-ticks, recommended card | ✅ | Enforced server-side (AI-memory off drops long-term history). All six DPDP categories are shown at the moment of consent; Android and the web client ‡ now default every toggle **off** |
| Language (5 options, now before the value moment) | 🟡 | **Generated replies honour it as of 2026-07-30 ‡** — `services/language.py` supplies one shared directive to the chat reply, the agentic plan and the Oracle (which takes it via `configurable`, since the graph is compiled once and shared). English returns an empty directive so the majority's prompts are unchanged. Crisis hotlines are appended *after* the model and stay dialable in any language, pinned by a test. Still 🟡 because this is the *model's* output only: the iOS and web **UI** is not localized (zero `.lproj`/`next-intl`), so a Hindi user gets Hindi replies inside English chrome |
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
| Narrated audio pipeline (TTS script → MP3 → `/media`) | 🟡 | Real end-to-end: `narration_script`/`audio_url`/`audio_generated_at` on `content_items` (migration `a7c4e9f2d310`), `POST /admin/content/{id}/narrate` calls ElevenLabs, `media.py` writes `media/narration/{id}.mp3`, `/media` is a mounted static dir, and all three clients stream it (iOS `AVPlayer` in `SoundscapePlayer.playNarration`, Android ExoPlayer in `AmbientService.createStream`, web `<audio>`), each falling back to the bundled bed on failure. **Gaps:** no batch/cron narrator — the seed writes scripts but never `audio_url`, so a fresh prod DB has **zero MP3s** until an admin clicks "Generate audio" per item; ~~and `/media` is unauthenticated~~ — **fixed ‡**: `/content` mints a signed, 12 h, single-item grant only for entitled callers and returns `""` otherwise (clients already treat that as "no narration" and use the bed), and `main.media_guard` 404s any narration request without a valid grant. Regression-tested in `test_content_audio.py` |
| Stories/meditation catalogue | 🟡 | All rails (Sleep, Home, Programs, Search) server-driven via `/content` with local fallback (2026-07-04). Per-item `audio_url` is now the primary path and the keyword→bundled-loop map is only the fallback — but until items are narrated (above) every item still resolves to one of the 4 bundled loops in practice. Android's URL registry is title-keyed and unpopulated for the Sleep hero, which therefore always plays the bed |
| Downloads | ⚪ | **No download code exists in any client** — no `AVAssetDownloadTask`, no ExoPlayer `DownloadService`, and `apps/app/public/sw.js` is explicitly "Web Push only (no fetch interception/caching)". The two shipped strings that asserted otherwise are gone ‡ — iOS `Dummy.offline` and its unreachable `OfflineView` were deleted, and the web library footnote now scopes the claim to the soundscape mixer |
| Sleep diary + morning check-in (manual, quality/bed/wake) | ✅ | Shipped 2026-07-03: iOS check-in (Home + Sleep tab), 7-night trend strip, diary history — local-first, mirrored to `/sleep` (`sleep_logs`); UITest-covered. Plan: [SLEEP_TRACKING.md](SLEEP_TRACKING.md) |
| Wind-down program (CBT-I-informed, non-diagnostic) | ✅ | Shipped 2026-07-03: "Wind down tonight" guide (`wind_down` catalogue kind, admin-authorable, offline fallback; breathing tip opens the pacer). Evidence base: dCBT-I ISI SMD −0.85, depression −0.47 |
| "Sleep Reset" 7-day CBT-I-informed program | ✅ | Seeded as a first-class program with all seven day themes (`seed.py` `_DAY_GUIDES`), stored in `content_items.day_guides` (migration `b8e6d1a4f527`), served by `GET /programs/active` as `today_guide` (clamped to the last day), authorable in the admin day-guides editor, and covered by `tests/test_programs.py`. All three clients now render the guide ‡ (2026-07-30): iOS `RemoteProgram.today_guide` → `ProgramProgressCard`, web `Active.today_guide` on the programs page, alongside Android's existing `parseTodayGuide`. Each treats a blank title+body as no guide, and the field stays additive, so a program without day guides simply shows "day N of 7" as before |
| Real sleep insights (duration trends, sleep × mood) | ✅ | Backend is real and consent-gated (`services/insights.py`: avg duration from `sleep_logs`; the sleep×mood sentence needs ≥3 sleep rows and ≥2 moods per bucket with a ≥0.5 gap), and **iOS now renders it ‡** — `InsightsView` maps `backend.insight.metrics` straight through (2026-07-30). `Dummy.weeklyMetrics` is deleted; signed out, the screen shows only locally-counted rows (check-ins, entries, the sleep diary's own 7-day average) and an honest "nothing to measure yet" when there are none |
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
| Voice loading/error/free-limit states | ✅ | Loading/error states are real, and the free-limit story landed 2026-07-30 ‡. Previously **no client anywhere handled the 429** (zero matches for `429`), so the cap surfaced as a generic failure and the server's explanation never arrived. The 429 detail is now a structured object (`services/usage.FREE_LIMIT_CODE` + `limit`/`used`/`resets_at`) so a client can tell it from the **IP rate limiter, which also returns 429** and means something entirely different — offering an upgrade to someone who merely typed too fast would be manipulative, so the distinction is explicit, never inferred from the status. iOS `FreeLimitView` was dead code and is now presented (with its copy fixed: it said "resets at midnight", but the window is UTC — every client renders the server timestamp in local time). Android and web have matching cards. Rate-limit messages are legible too: slowapi's `error` key is now read, so a throttled user no longer just sees "Request failed (429)" |

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
| Weekly insights (sessions, entries, sleep/mood trends) | ✅ | Server-generated and consent-gated when connected, and rendered where users look for it: the iOS Insights hero is the server `headline`/`summary` and its bars are the server metrics ‡ (2026-07-30) |
| Pattern dashboard ("stress spikes after meetings") | ✅ | **No longer illustrative** — and as of 2026-07-30 there is exactly one of it on iOS. ‡ Two *other* iOS entry points (Weekly Insights → "Pattern dashboard", Privacy & Memory → "Memory detail") opened a fabricated screen built from `Dummy.memoryItems` — invented observations with counts, plus Save/Delete buttons that only dismissed. Both now open the real `PatternDashboardView`; the dummy data and the fake editor are deleted. The row was ✅ while a user's most likely route to it was fake, which is the failure mode this table exists to prevent. `GET /insights/patterns` → `services/insights.compute_patterns` mines a 60-day window with four thresholded, consent-gated rules (hardest time of day, journaling→next-day calm, sleep→mood, weekday rhythm), each returning `{statement, basis}` where `basis` is the real supporting count; thin data returns `[]` with `enough_data: false`. Covered by `tests/test_patterns.py`; rendered on iOS (`PatternDashboardView`), Android (`PatternScreen`) and the web client ‡ (which this pass converted from three hardcoded cards to the live fetch + an honest empty state). Residual gap: clients ignore `enough_data`/`sources`, so "consent off" and "no data yet" read identically |
| Memory detail / edit / delete | ✅ | **Schema landed 2026-07-30 ‡** (`context_memories`): per-item add/edit/delete on all three clients, plus hide-this-pattern. The old note said granular editing was "not implementable against the current schema" — it is now, but deliberately only for what the user wrote or approved (`source` manual/confirmed/onboarding). Mined patterns are still never persisted as facts; hiding one writes a tombstone that `compute_patterns` filters. Reads/writes gate on `ai_memory`, deletion never does, and the wipe/export/admin-firewall paths all cover the new table |
| Export report | ✅ | Full server export (`GET /users/me/export`) |

### Premium
| Feature | Status | Notes |
|---|---|---|
| 3 tiers (Free / ₹499 / ₹1,499), paywall, StoreKit 2 | ✅ | Server-side receipt verification, renewal webhook; needs ASC products |
| Stripe web billing | 🟡 | Code-complete end-to-end and tested (`tests/test_stripe.py`): `POST /billing/checkout` (rate-limited, 503 when unconfigured), hand-rolled HMAC-SHA256 webhook verification with 300 s replay tolerance, price-id→tier map, downgrade on `deleted`/`canceled`/`unpaid`. Wired to the web client's account page. **Inert on owner credentials** (`stripe_enabled == bool(STRIPE_SECRET_KEY)`; all six `STRIPE_*` vars blank). All three code gaps closed 2026-07-30 ‡: `users.stripe_customer_id` is persisted and used to resolve events that arrive **without** our metadata (dashboard/portal edits do); `processed_webhooks` makes delivery idempotent at the DB, so a retried `subscription.deleted` landing after a re-subscribe can no longer downgrade a paying customer; and `POST /billing/portal` opens Stripe's own portal (card, plan, cancel) rather than a hand-rolled cancel, because proration/trial/dunning are Stripe's rules and a local version gets them wrong in ways that cost real money |
| Free-tier quota (midnight-UTC daily cap, 429) | ✅ | Chat + Oracle; real DB-count enforcement at `FREE_DAILY_MESSAGES=50`. Server side only — no client renders the 429 (see the Talk row) |
| Paywall copy honesty | ✅ | The 2026-07-03 scrub held for the iOS paywall and the web landing pricing/FAQ; the last three over-claims were fixed 2026-07-30 ‡. Android's upsell now names the benefit that is actually enforced ("unlimited daily conversations — free includes 50 messages a day", matching `services/usage.py`) instead of "unlimited voice", which implied a voice meter that does not exist; the web library footnote drops "offline playback"; iOS `Dummy.offline` and `OfflineView` are deleted |
| Coach/therapist booking (Premium+Human) | ⚪ | No provider, no booking route, no model, no interest capture — iOS `CoachBookingView` is local state with hardcoded time chips and a "Notify me" button that discards the signal on teardown (it does say "nothing is scheduled yet"). **Sharpest honesty risk in the product:** `premium_human` passes the `billing.py` tier regex and is sellable through both Stripe and StoreKit, while buying only an unlimited chat quota |

### Safety & crisis
| Feature | Status | Notes |
|---|---|---|
| Personal safety plan (Stanley-Brown, user-authored) | ✅ | New 2026-07-30 ‡. Six sections the user writes themselves, versioned (archive-not-delete, so an edit made in distress can't erase what was written when well). `PUT` merges unset fields so the guided flow saves one section at a time. **The model never authors one** — the reference implementation this schema came from has an AI risk-classifier write the plan, which was deliberately not copied. Never gates anything: a test compares the crisis reply and SafetyEvent count with and without a plan. Readable offline on all three clients, plus a print-ready page (`/safety-plan/me/printable`) rather than a PDF dependency |
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
| First-party anonymous analytics (onboarding funnel, paywall) | 🟡 | Real and privacy-clean where it ships: allowlisted events only, random install id (never account-linked), zero third-party SDKs, opt-out toggle, admin funnel chart — on backend, iOS and Android. The browser client posts too as of 2026-07-30 ‡ (`apps/app/lib/analytics.ts`), so `source=app` is live and the browser funnel is visible in admin. It inherits every rule the mobile clients follow — random per-install id, no auth header, allowlisted names, **nothing sent before the Consent step** — and adds the opt-out switch the web account page was missing, since shipping counting without one would have broken the promise the copy already makes. The landing site (`apps/web`) still sends nothing, deliberately: it has no consent surface, so there is nowhere honest to gate it |
| Sign in with Apple / Google | 🟡 | Backend verification complete for both (JWKS, dual issuer, audience). Apple: iOS button + entitlement ship, **blocked on the portal capability + `APPLE_CLIENT_ID`**; Android has no Apple path at all (net-new code). Google: iOS/Android/web flows are written, **blocked on an OAuth client** *and*, on iOS, on adding `GIDClientID` + the reversed-URL scheme to `Info.plist` |
| Sync: plan, journal, check-ins, consent, region, assessment, attest | ✅ | Additive; app fully local offline (zero remote images — all `Dummy.Img` entries are `asset:` bundles) |
| Offline mode | 🟡 | The behaviour is genuinely local-first (server-first with curated local fallback on Home/Sleep, on-device journal analysis, `LocalCompanion` chat). The unreachable "Offline Mode" showcase screen that claimed "downloaded sounds" was deleted 2026-07-30 ‡. Still 🟡 because the real offline story is undocumented in-product — nothing tells the user what does and does not work without a network. The other three views in `StateViews.swift` remain unreferenced dead code |
| Account deletion (typed DELETE, full cascade) | ✅ | |
| Privacy policy + labels | ✅ | In-app + web + PRIVACY_LABELS.md |
| Calm games (8) | ✅ | VoiceOver-labelled |
| Local reminders (iOS `UNUserNotificationCenter`, Android `AlarmManager`) | ✅ | This is what "notifications" actually delivers today on both mobile clients |
| Web Push (VAPID) | ✅ | The only fully wired remote-push channel: `services/webpush.py` (pywebpush, prunes 404/410 endpoints) + `apps/app/lib/push.ts` subscription registration. Self-generated keys — no vendor account needed |
| Remote push (APNs / FCM) + nudge dispatch | 🟡 | Scheduler is real and production-shaped: in-process dispatcher on `NUDGE_DISPATCH_INTERVAL_MINUTES`, `SELECT … FOR UPDATE SKIP LOCKED` so multiple workers are safe, contextual/bedtime-derived scheduling, fallback chain web-push → email. The APNs sender is real (ES256 `.p8` JWT, HTTP/2). **iOS registration shipped 2026-07-30 ‡** (it previously had no `AppDelegate`, no `registerForRemoteNotifications` and zero `registerPushToken` call sites, so `user.push_token` could never be populated): `Features/Notifications/PushRegistrar.swift` adds the delegate and caches the device token, `BackendService.syncPushToken` PUTs it on every connect (and on sign-out clears the synced mark), and the entitlement carries `aps-environment`. Registration is gated on notification authorization the app already asked for — it never prompts on its own. `UIBackgroundModes` deliberately stays `audio`-only: the server sends `apns-push-type: alert`, so `remote-notification` would be an unused mode and an App Review flag. **Now genuinely just needs the key** (APNs `.p8` + Push Notifications on the App ID). FCM is 100% absent (no Firebase in Android, no sender in backend) |
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
1. ~~Honest paywall copy~~ — DONE 2026-07-03 for the iOS paywall and web pricing/FAQ;
   the narrow reopening (Android's "unlimited voice", the web library's "offline
   playback" footnote, iOS `Dummy.offline`'s "Downloaded soundscape") is **closed
   2026-07-30** — the first re-worded to the quota that is actually enforced, the
   second scoped to the mixer, the third deleted along with `OfflineView`.
2. ~~Server-driven content catalogue~~ — DONE 2026-07-04 (all rails via `/content`).
3. ~~Bundle imagery for remaining hero/rail Unsplash URLs~~ — DONE; every `Dummy.Img`
   entry is now an `asset:` reference, zero remote images.
4. ~~Onboarding polish (back nav, single-select reminders, under-18 exit,
   client-side attestation timestamp)~~ — DONE 2026-07-03.
5. ~~Real pattern mining for the dashboard~~ — DONE: `/insights/patterns` with
   thresholded, consent-gated rules and honest empty states on all three clients.
6. ~~iOS remote push is unreachable~~ — DONE 2026-07-30: `PushRegistrar.swift` (delegate +
   token cache), `BackendService.syncPushToken` on connect, `aps-environment` in the
   entitlement. `remote-notification` was deliberately **not** added to `UIBackgroundModes`
   — the server sends alert pushes only, so the mode would be unused (and unused background
   modes draw App Review rejections). Remaining: the APNs key + the App ID capability.
7. ~~Wire the iOS Insights screen to `backend.insight`~~ — DONE 2026-07-30. Went one step
   further than the item asked: `Dummy.weeklyMetrics` was deleted rather than kept as the
   `insight == nil` fallback, because two of its four rows ("Sleep consistency / Improving",
   "Mood stability / Steady") were numbers nobody measured. Signed out, the screen now shows
   only locally-counted rows, or an honest empty state.
8. ~~Render `today_guide` on iOS and web~~ — DONE 2026-07-30. "Sleep Reset" is now a
   7-day program on every client, not a day-blind progress bar on two of them.
9. Narrate the seeded catalogue: a batch/cron narrator (or a seed-time pass), so a fresh
    prod DB isn't shipped with zero MP3s. (The `/media` entitlement check this item used
    to also cover is done — see the narrated-audio row above.)
10. Handle 429 in all clients and present the free-limit state (iOS `FreeLimitView` is
    dead code); state the actual cap and that the reset is UTC midnight.
11. ~~Chat/Oracle prompts honor `User.language`~~ — DONE 2026-07-30. One shared
    directive (`services/language.py`) applied to the chat reply, the agentic plan and
    the Oracle. Note what this does NOT do: localize the app's own UI strings.
12. **Reclassified 2026-07-30 — owner-credential, not code.** The premise was wrong:
    `GoogleAuth.clientID` already reads `GIDClientID` from Info.plist, so the slot
    exists. Committing an empty key changes nothing (empty still reads as
    unconfigured) and a placeholder reversed-URL scheme would register a bogus
    scheme, which is worse than absent. Exactly what to add is now documented in
    TECHNICAL.md's env table; it needs the real OAuth client id and nothing else.
13. ~~Persist Oracle tool confirmations as an audit trail~~ — DONE 2026-07-30.
    `agent_actions` records the proposal when it is *shown* and stamps the decision on
    resume. The user gets their own history at `GET /oracle/actions` (not just admin —
    "did the assistant write that, or did I?" is their question first); admin gets
    per-tool counts only, never summaries, because a summary quotes the user's words
    back. Declines are kept deliberately: a repeatedly refused tool is the signal.
14. ~~Post analytics events from the web clients~~ — DONE 2026-07-30 for `apps/app`
    (`lib/analytics.ts`, same vocabulary and gates as mobile, plus the opt-out toggle the
    account page lacked). `apps/web` (the landing site) deliberately still sends nothing:
    it has no consent surface, so there is nowhere honest to gate it.
15. ~~Stripe hardening~~ — DONE 2026-07-30, all three. Idempotency is enforced by a
    unique constraint rather than an application check, because two concurrent
    deliveries of the same event race and exactly one may win. Still inert without
    `STRIPE_*` keys.
16. ~~UITest auto-dismiss for the iOS Local Network prompt~~ — DONE 2026-07-30.
    `allowLocalNetworkIfAsked` at every one of the 9 launch sites. It **allows** rather
    than dismisses (denying would fail every live-backend test for the rest of that
    install), matches the alert text before tapping so a stray "OK" sheet can't be
    swallowed silently, and waits 0s on simulator runs — a real wait across 23 launches
    would have added over a minute to a ~22-minute suite.
17. VoiceOver live announcements for streaming chat.
18. Finish `values-hi` (69 keys) and get the clinical/linguistic sign-off the file header
    is waiting on.

**Deliberately deferred:** human-support booking marketplace (needs providers + legal),
full UI localization beyond Hindi, real downloads, Play Billing, an interventions engine
that acts on mined patterns (today patterns are display-only).

## 4. Phase-wise roadmap

**Phase 0 — TestFlight (days):** ~~checklist #1, #6, #7~~ (all three landed 2026-07-30) ·
owner credentials above — Phase 0 is now **entirely** owner-blocked on the code side ·
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
