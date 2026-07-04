# CereBro — TODO / Known Debt

> Prioritized output of the full-codebase review (2026-07-02), updated after the
> implementation pass the same day. Check items off as they land; re-run a review pass
> periodically. Companions: [ARCHITECTURE.md](ARCHITECTURE.md), [TECHNICAL.md](TECHNICAL.md).

## Open — needs the owner's accounts/credentials (no code left to write)

- [ ] **Rotate any previously shared provider keys** (OpenAI/Deepgram/ElevenLabs) and the
  Phase-0 items in RELEASE_PLAN.md (shared VPS/root passwords, shared SECRET_KEY).
- [ ] **Apple Developer portal:** enable the Sign in with Apple capability for
  `com.cerebrozen.app` (the app now ships the entitlement + `CereBro.entitlements`;
  set `APPLE_CLIENT_ID` in prod env).
- [ ] **Apple Developer portal:** add the HealthKit capability to
  `com.cerebrozen.app` (entitlement + `NSHealthShareUsageDescription` shipped
  2026-07-03; simulator works without it, physical-device builds need the App ID
  capability).
- [ ] **Google Sign-In:** create the OAuth client; add `GIDClientID` + reversed URL scheme
  to Info.plist and `GOOGLE_CLIENT_ID` server-side.
- [ ] **App Store Connect:** create `com.cerebrozen.premium.monthly` +
  `com.cerebrozen.premium.annual` (₹3,999) + `com.cerebrozen.premiumhuman.monthly` +
  `com.cerebrozen.premiumhuman.annual` (₹11,999), point Server Notifications V2 at
  `POST /webhooks/appstore`. (Annual SKUs are code-complete client+server side
  2026-07-03 — investor gap #3.)
- [ ] **Ops config:** `SMTP_*`, `TWILIO_*`, `OPS_ALERT_EMAIL`, `APNS_*`, and `ASC_*`
  GitHub secrets (TestFlight workflow).

## Open — code/product work

### Sleep tracking module — validated GO (2026-07-03), plan in [SLEEP_TRACKING.md](SLEEP_TRACKING.md)
Ordered for delivery; framing rule everywhere: non-diagnostic "sleep awareness", no
accuracy/staging claims (App Store 1.4.1 + 5.1.3, AASM position).
- [x] Backend: `sleep_logs` table (Alembic `9e8d4f7c2b65`) + `/sleep` router
  (upsert-by-date, range list, weekly summary: avg duration, bedtime consistency,
  quality trend, `enough_data` gate) + 7 tests — 2026-07-03, suite 184 passed /
  95.68 % coverage; migration verified on a fresh DB; live-API smoke-tested.
- [x] iOS: morning sleep check-in (Home row + Sleep tab CTA→edit row), 7-day trend
  strip (real data only, 3-night honesty gate), diary history — local-first
  `SleepEntry` in `AppState`, mirrored to `/sleep`, demo-seeded under `-resetState`
  (today left unlogged so the CTA stays deterministic). 2026-07-03: build green,
  Sleep+Home UITests pass incl. new save→diary assertion.
- [x] Content: CBT-I-informed wind-down guide as `/content` items (new `wind_down`
  kind: model docstring + admin CMS + iOS renderer + local fallback) and Sleep-tab
  rails (stories/soundscapes/meditations) now server-driven with `Dummy` fallback;
  seed is additive-by-title so new items reach existing dev DBs. 2026-07-03:
  backend 185 passed / 95.68 %, live `/content?kind=wind_down` verified, admin
  tsc clean. Home rails + search migration still open (item below).
- [x] Insights: server weekly insights now compute a real Sleep metric (avg duration,
  "No diary yet" empty state) + a sleep × mood note only when the week's own data
  supports it (both buckets ≥2, gap ≥0.5). 2026-07-03. iOS *local* fallback insights
  still show illustrative strings (labeled) — honest-local computation is follow-up.
- [x] Plans/nudges/Oracle: fallback planner protects the wind-down after short/rough
  nights (LLM prompt also carries the diary summary); `wind_down` nudge anchors
  ~45 min before the user's own average bedtime (timezone-aware, upserts in place);
  `log_sleep` Oracle tool + `sleep_checkin` widget kind wired backend + iOS in the
  same commit. 2026-07-03: 190 passed / 95.72 %.
- [x] v1.5: HealthKit sleep read (opt-in, off by default) — entitlement +
  `NSHealthShareUsageDescription`, `HealthKitSleep` read-only manager, check-in
  toggle + pre-fill (user still confirms; `source: healthkit` flows to the server),
  PRIVACY_LABELS row updated. Never writes to HealthKit; no PHI in iCloud.
  2026-07-03. Portal App ID capability = owner item above.
- [ ] Check instrument licensing before using ISI/PSQI verbatim (both copyrighted;
  commercial use needs permission) — until then, the plain-language 1–5 baseline returns
  contextually and feeds `sleep_logs`.

### Strategy-doc adoptions (2026-07-03) — remaining decisions/work
- [x] **Analytics vs "no trackers" promise** — DECIDED + shipped 2026-07-04:
  first-party anonymous counts on our own Postgres, zero third-party SDKs.
  `product_events` table (Alembic `b17c4e8f2a93`) + `POST /events` (allowlisted
  names, random install id, endpoint takes NO auth so rows can't join accounts)
  + `GET /admin/metrics/funnel` + admin funnel chart; iOS `Analytics.track`
  (onboarding steps, paywall view/CTA; no-ops under `-resetState` and when the
  new "Anonymous usage stats" toggle in Privacy & Memory is off); privacy
  policy/labels/landing copy reconciled (PRIVACY_LABELS now declares Product
  Interaction, not-linked). Unblocks experimentation.
- [x] Email one-time-code (passwordless) sign-in — 2026-07-04: `login_codes` table
  (Alembic `af3e6b9c1d57`) + `POST /auth/otp/request` / `/auth/otp/verify`
  (find-or-create like Apple/Google, marks email verified, clears password
  lockout; single-use, 10 min TTL, burns after 5 wrong tries; hashed at rest);
  iOS AuthForm "Sign in without a password" flow (`.oneTimeCode` AutoFill) +
  web-app signin code mode. Passkeys deferred to v2.
- [x] Contextual baseline capture — 2026-07-04: `BaselineCheckView` (two 1–5
  scales) offered as a Home row once ≥3 mood check-ins exist and no baseline yet;
  `setBaseline` stamps the date once; Insights "Your starting point" renders again.
- [x] Companion persona picker — 2026-07-04: "Companion style" row in You →
  `CompanionStyleView` (4 styles, default Calm Guide), persisted locally and
  synced to the server profile (`PATCH /users/me companion`; re-applied on
  connect; server value adopted on a fresh install still at the default).
- [x] 90-second onboarding (one-tap state → breathing reset → mini-plan → account)
- [x] Consent private-by-default (no pre-ticked toggles + recommended card)
- [x] Language moved before the value moment

### Web app v1 + admin v2 — plan in [WEB_APP_PLAN.md](WEB_APP_PLAN.md)
- [x] Infra prep (2026-07-03): `apps/app` Next.js scaffold (:3002), CORS origin added
  (dev default + env examples), Caddy `app.cerebrozen.in` block, dev/e2e/prod compose
  services, CI typecheck job. Design tokens: third CSS copy for now (per-app Docker
  contexts) — extraction still open below.
- [x] Auth client with `POST /auth/refresh` rotation (2026-07-03): app keeps the access
  token in memory + refresh in localStorage with one rotation retry per 401; admin
  upgraded to the same pattern (sessions no longer die at 30 min).
- [x] Web v1 first slice (2026-07-03): signup/signin, Today (mood check-in + recent),
  Journal (composer/history + crisis-support banner on elevated risk — never blocks),
  Sleep diary (check-in, honest weekly summary, history — closes SLEEP_TRACKING #6).
- [x] Web v1 features (2026-07-03): chat (Oracle SSE fetch-streaming w/ tool-confirm
  + crisis banner, `/chat` fallback + chips), plan (optimistic step toggle,
  regenerate), insights (5 real metrics + upcoming nudges), account (consent,
  region, trusted contact, export download, typed DELETE). Found + fixed a real
  backend bug on the way: first `/oracle/messages` on a fresh DB hung forever —
  langgraph's `setup()` runs `CREATE INDEX CONCURRENTLY`, blocked by any
  idle-in-transaction pool connection; the graph now warms in the app lifespan
  pre-traffic, with a 30 s setup timeout falling back to MemorySaver.
- [x] Library page (2026-07-03): served `/content` catalogue grouped by kind on the
  web app; honest "playback lives in iOS" footnote.
- [ ] Web v1 remaining: Google sign-in (needs owner OAuth client id).
- [x] Shared design tokens — 2026-07-04: canonical `design/tokens.css` +
  `scripts/sync-tokens.mjs` rewriting marker-delimited blocks in all three
  `globals.css` (checked-in copies stay Docker-friendly); CI drift gate
  (`--check`). Union palette reconciled (web `--card` 0.05 → 0.045).
- [x] Streaks on web (2026-07-03): `GET /users/me/streak` computes the "mindful days"
  streak server-side (same one-grace-day rules as iOS — now a cross-stack contract);
  Today page shows the streak card + week dots. iOS still computes locally
  (offline-first) — keep the rules in sync.
- [x] Playwright spec for the web app in the existing `e2e/` stack (signup → check-in →
  journal → sleep → reload survives via refresh rotation). 2026-07-03.
- [x] Admin v2 (2026-07-03): first-party Analytics tab (`GET /admin/metrics/overview` —
  DAU/WAU/MAU, signup-cohort D1/D7/D30 retention, activation funnel, 7-day engagement;
  aggregates only, no per-user browsing) + per-user support view (`GET /admin/users/{id}`
  — counts/consent/last-active; journal/chat/sleep contents never cross the endpoint,
  test-pinned).
- [x] Nudge authoring (2026-07-03): `POST /admin/nudges` (one user or all active,
  kind `announcement`, delivery via the existing scheduler) + `GET /admin/nudges`
  (kind-filterable) + admin Nudges tab. Admin v2 complete.
- [x] Stripe web billing — 2026-07-04: `services/stripe_billing.py` (httpx REST +
  manual HMAC webhook verification, no SDK), `POST /billing/checkout` (503 until
  `STRIPE_*` set) + `POST /webhooks/stripe` → same `subscription_tier` contract;
  account-page "Upgrade" button degrades honestly. Owner: create Stripe products +
  webhook endpoint + keys.
- [x] Email nudges for web-only users — 2026-07-04: `users.email_nudges` opt-in
  (Alembic `d41f6a8c2e95`, account-page toggle); `dispatch_due` falls back to
  email when there's no push token and the user opted in.
- [x] `/auth/apple` Services-ID audience — 2026-07-04: `APPLE_SERVICES_CLIENT_ID`
  accepted as a second token audience (web button itself still needs the owner's
  Services ID + Apple JS wiring).
- [ ] Post-v1 (remaining): Web Push (VAPID) as a richer alternative to email nudges.
- [x] Oracle agent consent — verified 2026-07-04: the graph's system prompt embeds
  NO user data; its only data read (`get_weekly_insights`) delegates to the already
  consent-gated `insights.compute_weekly`, and every write tool is individually
  user-confirmed via `interrupt()`. Nothing left to gate.

### Investor-readiness actions — benchmarks + full list in [INVESTOR_READINESS.md](INVESTOR_READINESS.md)
- [x] **Decide analytics** — done 2026-07-04 (see the strategy-doc item above):
  first-party anonymous events + admin funnel shipped; D1/D7/D30 + activation
  already came from `metrics/overview`; the funnel adds pre-account steps.
- [ ] Annual subscription SKUs + 7-day-trial design; treat the first-session paywall as
  the primary experiment surface (89.4 % of trial starts happen Day 0).
- [ ] Financial model anchored to IN/SEA benchmarks ($14 Y1 LTV/payer, 15.2 %
  trial-to-paid) with US distribution + ₹1,499 tier as blend-up levers.
- [ ] Clinical-credibility package: named clinical advisor, cite conservative dCBT-I
  meta-analytic effects, write up the crisis-safety design as a diligence artifact.

### DPDP Act readiness — checklist + deadlines in [DPDP_COMPLIANCE.md](DPDP_COMPLIANCE.md)
Substantive obligations bite **13 May 2027**; SPDI Rules 2011 (mental-health data =
sensitive) apply **today** and are already satisfied. Ordered by lead time:
- [x] Consent screen itemised — 2026-07-04: `journal_memory` + `sleep_history` flags
  (Alembic `c29d5f7e4b18`) across backend model/schemas + iOS Consent/Privacy screens +
  web account page; every category now ENFORCED at its read site (chat recall, plan
  signals `agentic._recent_signals`, weekly insights) — previously only `ai_memory` did
  anything. Oracle context gating is still open (below).
- [x] Rule 8(3) deletion ledger — 2026-07-04: `deletion_ledger` (hashed email +
  account age only, written in the same transaction as the cascade delete; ops purge
  after 12 months). Content still hard-deletes instantly.
- [x] Grievance contact published — 2026-07-04: grievance@cerebrozen.in + 90-day SLA +
  Board-escalation note on the web privacy policy and the in-app policy screen.
  (Owner: create the mailbox.)
- [x] Breach-notification runbook — 2026-07-04: [BREACH_RUNBOOK.md](BREACH_RUNBOOK.md)
  (roles, statutory clock incl. CERT-In 6 h today, templates, preparedness checklist).
- [ ] Processor security clauses with LLM/voice/email/SMS vendors (Rule 6(1)(f)).
- [ ] DPIIT startup recognition (eligibility for the s. 17(3) exemption if an SDF class
  notification ever covers wellness apps).
- [ ] Localize consent/notice screens first (Eighth-Schedule language option).

### Onboarding flow review (2026-07-02) — smaller findings
- [x] Back navigation — 2026-07-04: back chevron on every step > 0 (`StepScaffold`
  `onBack` + `OnboardingBackButton` on the custom screens); UI test covers it.
- [x] Notifications step single-select — 2026-07-04: `ChipRow(singleSelect:)`,
  inert "Private previews" option removed.
- [x] Age gate — 2026-07-04: under-18 exit ("I'm not 18 yet" → honest message +
  Childline pointer); confirmed-at persisted (`AppState.ageConfirmedAt`) and sent
  with `attest()` (server honors past client times, caps future clocks).
- [x] Consent toggles pre-checked on — fixed 2026-07-03 (private-by-default).
- [x] `FirstPlanScreen.planTitle` sparse mapping — now covers 6 goals + calm default.
- [x] `OnboardingProgress` accessibility value — 2026-07-04: label + percent value;
  baseline date now stamps once (`setBaseline` keeps the original date).

- [x] iOS imagery — 2026-07-04: ALL remaining remote Unsplash URLs removed
  (`Dummy.Img.*` and the server seed's `image_url` are now empty); every hero/
  rail renders the branded gradient + symbol well `Photo` already draws. Zero
  network images: offline-correct, private, App-Review-safe. Bundle real
  licensed art via the CMS/asset catalog if it ever lands.
- [x] Remaining `Dummy` catalogue — 2026-07-04: Home rails (time-matched kinds
  from `backend.catalogue`, sleep-goal bias preserved), Programs (`kind=program`
  + new "Stop overthinking" seed item), Search (whole served catalogue as the
  pool) all server-first with the curated local fallback offline; UI tests
  stay deterministic (`loadCatalogue` no-ops under `-resetState`).
- [x] Backend test isolation — 2026-07-04: conftest now runs the suite in a
  dedicated `<db>_test` database, dropped + recreated fresh per run (active
  whenever DATABASE_URL is set, i.e. container + CI); dev data stays untouched
  and create_all can never race the dev DB's Alembic state again.
- [x] VoiceOver for streaming chat — 2026-07-04: the live bubble is marked
  `.updatesFrequently` ("CereBro is replying") and the completed reply is
  announced once via `UIAccessibility.post` — deliberate: per-token speech is
  noise, one announcement is the accessible pattern.
- [x] Opt-in live-LLM suite — 2026-07-04: `tests/test_live_llm.py`
  (`RUN_LLM_TESTS=1` + a key: real /chat reply + Oracle SSE liveness; skipped
  hermetically otherwise). Verified live: 2 passed against real keys.
- [ ] **Android app** — slices 1+2 shipped 2026-07-04: zero-SDK API client
  (auth + refresh rotation), live Today/Journal/Sleep/Talk tabs — ALL verified
  end-to-end on an API-35 emulator against the dev backend (sign-in as the
  seeded demo user, check-in advanced the server streak 3→4, journal + sleep
  writes landed, /chat returned a live LLM reply with suggestion chips).
  Remaining for parity: plans/insights/You tabs, offline-first store,
  Apple/Google sign-in, Play Billing, FCM, soundscape audio, CI wrapper.
- [x] Check-in ritual reward — 2026-07-04: saving a mood check-in now offers
  "A tiny reward — seal it with a 1-minute calm game" (routes to Games; offered,
  never forced). The proactive ritual itself was already the Home hero + daily
  reminder.
- [ ] Content depth + clinical credibility (SHIP_READINESS.md "honest gaps").
- [x] `mcp.cerebrozen.in` — dropped 2026-07-04 (dangling subdomain removed from
  the Caddyfile comment; owner: delete the DNS record).
- [x] CSP — 2026-07-04: pragmatic policy in the shared Caddy snippet (blocks
  remote scripts/objects/frames/images, pins connect-src to our origins;
  'unsafe-inline' script/style stays for Next hydration). Upgrade path: per-app
  nonce middleware, still open below.
- [ ] CSP nonce upgrade: Next middleware per app to drop 'unsafe-inline' scripts.

## Done — implementation pass 2026-07-02

### P0 (verified)
- [x] **Oracle durable checkpointing** — `AsyncPostgresSaver` on the app DB (MemorySaver
  only as logged dev fallback); paused confirmations now survive restarts and cross
  gunicorn workers. Verified live: SSE streams + "Oracle checkpointer: Postgres" boot log.
- [x] **Nudge delivery scheduler** — in-process asyncio loop in `app.main` lifespan every
  `NUDGE_DISPATCH_INTERVAL_MINUTES` (default 5, 0 = external cron); `dispatch_due` claims
  rows `FOR UPDATE SKIP LOCKED` so multi-worker/cron passes never double-send.
- [x] **App Store receipt pinning** — Apple Root CA-G3 PEM bundled at
  `backend/app/certs/`, prod template points at it, and `verify_transaction` now rejects
  transactions whose `bundleId` isn't ours (tests added).
- [x] **Admin UI credential leak** — seeded-creds prefill + hint gated to dev builds.
- [x] **Caddy security headers** — shared snippet (HSTS, nosniff, SAMEORIGIN,
  Referrer-Policy, Permissions-Policy) imported into all three site blocks.
- [x] **Rate limits on expensive endpoints** — `/chat` 30/min, `/oracle/*` 30/min,
  `/voice/stt` 20/min, `/voice/tts` 60/min, `/waitlist` 10/min; limiter now keys on
  `X-Forwarded-For` behind Caddy.
- [x] **Oracle error frames** — generic client message; real exception server-logged.

### P1
- [x] SIWA entitlement file + `CODE_SIGN_ENTITLEMENTS` wired (build verified).
- [x] Privacy-label tables reconciled (SHIP_READINESS now matches PRIVACY_LABELS: no
  analytics, no diagnostics).
- [x] Stale URLs — SHIP_READINESS support/marketing → cerebrozen.in; iOS privacy link
  fixed; new `apps/web/app/support/page.tsx` (+ sitemap/footer).
- [x] Pricing aligned — paywall renders StoreKit `displayPrice`; `Products.storekit` set
  to Indian storefront ₹499/₹1,499; fallbacks consistent.

### P2
- [x] Quota window is midnight-UTC (was rolling 24 h); test pins the boundary.
- [x] `dispatch_due` outcomes honest: `skipped` (no token) / `failed` (push error) instead
  of fake `sent` — queryable dead-letter, no silent drops.
- [x] Apple private-relay/no-email sign-in — `users.apple_sub` column (migration
  `8c7f5d1b9e46`), lookup by stable `sub` first, synthesized address when Apple withholds
  email, legacy accounts adopt the sub.
- [x] `prestart` fails loudly in production when migrations fail (create_all fallback is
  dev-only).
- [x] JWKS caches (Apple + Google) refresh on a 6 h TTL.
- [x] Web/admin typecheck in CI (`tsc --noEmit` job; committed `next-env.d.ts`). No ESLint
  config exists, so no lint step.
- [x] Accessibility pass — VoiceOver labels/traits on all game tap targets, slider values
  on sleep volume/timer, journal/safety field labels.
- [x] Admin "Dispatch due nudges" button on Overview (manual pass alongside the scheduler).
- [x] Waitlist spam — hidden honeypot field client-side + 10/min IP rate limit server-side.
- [x] Transaction ownership — reviewed: services `flush()`, routes `commit()`; the flagged
  double-commit did not exist (dispatch_due commits by design — it's a job, not a route).

Verification: backend **177 passed, 95% coverage** (in-container, live Postgres); web +
admin `tsc --noEmit` clean; iOS `xcodebuild build` succeeded with the new entitlement.
