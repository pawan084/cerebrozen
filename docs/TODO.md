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
- [ ] **Google Sign-In:** create the OAuth client; add `GIDClientID` + reversed URL scheme
  to Info.plist and `GOOGLE_CLIENT_ID` server-side.
- [ ] **App Store Connect:** create `com.cerebrozen.premium.monthly` +
  `com.cerebrozen.premiumhuman.monthly`, point Server Notifications V2 at
  `POST /webhooks/appstore`.
- [ ] **Ops config:** `SMTP_*`, `TWILIO_*`, `OPS_ALERT_EMAIL`, `APNS_*`, and `ASC_*`
  GitHub secrets (TestFlight workflow).

## Open — code/product work

### Strategy-doc adoptions (2026-07-03) — remaining decisions/work
- [ ] **Analytics vs "no trackers" promise** — the funnel KPIs / A/B slate from the
  redesign strategy require product analytics, but privacy labels + landing copy say
  none. Decide: first-party anonymized counts disclosed in the privacy hub, or stay
  measurement-free. Blocks any experimentation work.
- [ ] Email one-time-code (passwordless) sign-in — email service exists; add OTP
  issue/verify endpoints + iOS field. Passkeys deferred to v2.
- [ ] Contextual baseline capture — the stress/sleep 1–5 scales were removed from
  onboarding (90-second flow); reintroduce as a gentle ask after the first few
  check-ins so Insights' "starting point" returns.
- [ ] Companion persona picker — removed from onboarding; add a "Companion style"
  row in You/settings (default stays Calm Guide).
- [x] 90-second onboarding (one-tap state → breathing reset → mini-plan → account)
- [x] Consent private-by-default (no pre-ticked toggles + recommended card)
- [x] Language moved before the value moment

### Onboarding flow review (2026-07-02) — smaller findings not yet fixed
- [ ] No back navigation between onboarding steps (a mis-tapped Continue is
  unrecoverable mid-flow); add a back chevron to `StepScaffold` for steps > 0.
- [ ] Notifications step is multi-select for a single reminder slot: both times
  → morning silently wins; "No reminders" + a time is contradictory-but-allowed;
  "Private previews" maps to nothing. Make it single-select.
- [ ] Age gate: no under-18 exit path, and the confirmation isn't persisted
  client-side (attestation inferred from flow completion, stamped at first
  connect). Persist a local confirmed-at and send it with `attest()`.
- [x] Consent toggles pre-checked on — fixed 2026-07-03 (private-by-default).
- [x] `FirstPlanScreen.planTitle` sparse mapping — now covers 6 goals + calm default.
- [ ] `OnboardingProgress` has no accessibility value (VoiceOver users get no
  sense of progress); re-running onboarding re-stamps the baseline date.

- [ ] iOS imagery: bundle real assets for the remaining content heroes/rails
  (offline correctness, privacy, App Review safety). 2026-07-03: photo usage cut
  hard — rows/onboarding/talk no longer render photos (symbol wells only); the
  worst URLs (office, laptop-hands, portrait-near-crisis, desert road) retargeted
  to calm nature. What's left is ~13 Unsplash URLs on heroes + rail cards.
- [ ] Most iOS catalogue content (sleep/meditations/programs rails) is `Dummy` static
  data; only plan/insights/chat are server-driven. Migrate catalogue to `/content`.
- [ ] Backend tests require a live Postgres (autouse `init_db()`); consider transactional
  isolation or a dedicated per-run test DB.
- [ ] VoiceOver announcements for streaming chat text (labels/traits pass is done; live
  token announcements are not).
- [ ] Opt-in live-LLM integration suite (`RUN_LLM_TESTS`) to cover the Oracle stream paths
  excluded from coverage.
- [ ] **Android app** — `apps/android` is a Compose scaffold (placeholder tabs). Roadmap:
  networking, feature parity with iOS, Play Billing, FCM.
- [ ] Surface the daily mood check-in as an explicit proactive ritual; light reward tie-in
  to games (competitor benchmark follow-ups).
- [ ] Content depth + clinical credibility (SHIP_READINESS.md "honest gaps").
- [ ] `mcp.cerebrozen.in` reserved in DNS/Caddyfile with no service behind it — build or drop.
- [ ] Consider a CSP for web/admin (deliberately omitted from the Caddy header block —
  Next.js inline scripts need nonces/hashes first).

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
