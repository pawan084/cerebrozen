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

## Open — needs a product/legal decision (surfaced by the 2026-07-12 Android deep review)

- [x] **Analytics fire before the consent screen** — DECIDED + IMPLEMENTED 2026-07-13
  (owner: gate until consent). `Analytics.track` no-ops until `analytics_unlocked`, set on
  passing the onboarding Consent step or on an authenticated session (returning users).
  Funnel events before Consent are intentionally uncounted.
- [x] **Onboarding Consent step shows only 3 of 6 categories** — FIXED 2026-07-12 (redesign
  W3): all six categories now render with labels/hints; defaults unchanged.
- [x] **Health Connect consent boundary** — DECIDED + IMPLEMENTED 2026-07-13 (owner: the
  OS-level HC grant is the consent act for the local read; the in-app `sleep_history` toggle
  governs server-side memory). SleepScreen states the boundary next to the prefill button
  (`sleep_hc_boundary_hint`).

## Open — redesign follow-ups (from docs/REDESIGN.md, Phases 1–2 shipped 2026-07-12)

- [x] **Dawn light theme** (REDESIGN §4.1 Phase 2 remainder) — shipped 2026-07-12 without a
  screen migration: the top-level tokens in `Color.kt`/`Tokens.kt` are now theme-aware
  getters resolving `AppTheme.isNight` (snapshot state), so every screen got Dawn for free.
  You → Appearance persists `theme_mode` (System/Night/Dawn); Sleep, the splash and the
  signed-out funnel force Night; `ContrastTest` gates both palettes ≥4.5:1 and pins the
  Night palette byte-identical.
- [ ] **iOS parity for the redesign** — the Android IA changes (Toolkit merge, breathe
  engine, sounds consolidation, presence framing, onboarding trim) intentionally diverge
  from iOS until backported.
- [ ] **Phase 3 roadmap**: Hindi UI localization (externalize strings as they're touched),
  premium launch behind the OECD dark-pattern checklist. Android groundwork landed
  2026-07-12 (W11): ~370 user-facing strings across all Compose screens now live in
  `app/src/main/res/values/strings.xml` (`stringResource`, positional args, plurals);
  ConsentNotice.kt keeps its own 13-language system. **DRAFT `values-hi/strings.xml`
  created 2026-07-12 (W16)** — 530 of 657 resources machine-translated (आप-form, calm
  tone, brand words Latin, placeholders/plurals preserved), builds green; **pending
  qualified clinical/linguistic review before ship**. Deliberately left in English
  (resource fallback) pending that review: crisis screen (`crisis_*`), human-support
  directory (`humansupport_*`), Talk AI-disclosure + in-chat crisis banner + SOS/
  reframe chips, TIPP (DBT) skill, CBT reframe tool, "Why this works" provenance
  texts, sleep CBT-I education cards, `sleep_hc_boundary_hint`, onboarding
  disclosure/age-gate/danger line, crisis-region picker, journal safety-escalation +
  safety-scanning copy, privacy-policy clinical-positioning cards (full list in the
  file header). Remaining before a shippable Hindi
  drop: the review sign-off above, plus pure functions still returning English copy
  (`greetingFor`, `milestoneLine`,
  `railKindFor`, `minutesToLabel`, `spreadLabel`, `rhythmPrinciple`, `breathePhases`
  labels, `talkTranscript` prefixes — all marked `// i18n: pending`), value-doubling
  lists needing a label/value split (Today `MOODS`, onboarding `STATE_OPTIONS` /
  `LANGUAGES` / `NOTIFY`, Settings `COMPANIONS`, YouScreen profile fallbacks), the
  onboarding `Funnel` progress keyed off English eyebrows, and non-Compose copy
  (`notify/Reminders.kt` notification title/body, `audio/SoundscapeMixer.kt` layer
  names). CBT-I weekly program (backend)
  seeded 2026-07-12 (W12): "Sleep Reset" 7-day program in the `/content` catalogue
  (kind=program, free), enrollable via the existing `/programs` flow. Per-day program
  model DONE 2026-07-12 (W15): nullable JSONB `content_items.day_guides`
  (`[{"title","body"}]`, Alembic `b8e6d1a4f527`), Sleep Reset seeded with its seven
  day guides (idempotent, backfill-only-where-NULL like narration_script), and
  `GET /programs/active` additively returns `today_guide` for the enrollment's
  current day (clamped to the last guide; programs without guides omit the field,
  so iOS — which ignores unknown JSON fields — is unaffected). Android
  ProgramsScreen renders the guide under the enrolled hero; an iOS "today's
  focus" card remains open when iOS work resumes. Day guides are editable
  from the admin CMS (W17): `ContentCreate`/`ContentUpdate` accept
  `day_guides` (validated `DayGuide` list; explicit null clears) and the
  admin Content form has a per-day title+body row editor. (Found while
  verifying: `backend/Dockerfile` COPY could carry a read-only `media/` mode
  from Windows/OneDrive checkouts, 500-ing narration saves in image-only
  runs like the e2e stack — fixed with an explicit `chmod -R u+w media`.)
- [x] **Onboarding `onAccountCreated` race** — FIXED 2026-07-12 (W7): post-signup writes run
  under `NonCancellable` in AuthScreen's `signUpThenPersonalize`; `AuthFlowTest` reproduces
  the race and fails without the fix.
- [x] **Night-palette accent contrast debt** — FIXED 2026-07-12: Night `Periwinkle`
  brightened 0xFF8B78F2 → 0xFFA89AF6 (minimal in-family lighten clearing 4.5:1 on
  CardFill 5.33 / Night 7.73 / raised 4.66); nav-wash constants follow; ContrastTest
  now gates it and the Night pin was updated deliberately.

## Done — recent

### The three open PRs, resolved (2026-07-31)
All three were opened 3 weeks ago off a base that `main` has since moved **135 commits**
past. Dispositions, with the evidence for each:
- **PR #3 (`cc7cbd4`, "ui")** — zero commits and an empty diff against `main`; already
  contained. Nothing to merge.
- **PR #1 (`9cb3da4`, ".gitignore")** — its two commits are both ancestors of PR #2, so it
  is a strict subset. Superseded.
- **PR #2 (`d5c20be`, "Add Android v1 updates")** — **not merged, deliberately.** It
  predates two things that landed on `main` since: the string externalisation and the Dawn
  theme. Measured, not guessed: PR #2's TodayScreen/SleepScreen/TalkScreen contain **0**
  `stringResource` calls against main's 33/49/67, and its `Color.kt` has **0** `isNight`
  references against main's 35. A test merge conflicts in 11 files — every major screen.
  Merging it would put hardcoded English and raw hex back into the app and undo W11/W16
  and the Dawn work.
- [x] **Salvaged from PR #2 — the two prompts worth keeping**: "One good thing" and
  "Tomorrow's intention", ported onto today's `main` rather than merged. They reuse the
  existing `JournalingTool`, take their copy from `strings.xml`, and each carries a
  "why this works" provenance line like CBT and TIPP do. Verified end to end on a
  CPH2681 against the local API: `POST /journal 201`, and the row reads
  `One good thing today: shipped the keyboard fix` — the compose template doing its job.
- **Rejected from PR #2, on purpose:**
  - `StressAlertCard` — a Home card reading "ELEVATED STRESS DETECTED / Your heart rate
    variability dipped / From Apple Watch" with **no HRV source anywhere in the app**, on
    Android. Fabricated data presented as a measurement; exactly what
    `docs/CLAIMS_MAP.md` and `scripts/check-claims.mjs` exist to prevent. (The genuine
    version of this idea is still open below under proactive stress detection.)
  - `MorningCheckInScreen` — not a missing feature. `main`'s SleepScreen already does this
    inline (quality chips, bed/wake times, `Api.logSleep`); PR #2's copy is the same
    capability as a separate screen with 9 raw-hex values.
  - Journal biometric lock — already on `main` in 5 files.
- [ ] **The PRs still need closing on GitHub** — `gh` is not installed here and there is no
  API token, so this could not be done from the CLI. #1 and #3 close as superseded/contained;
  #2 closes with the note above. PR #1 would now also conflict with the `.gitignore` rewrite.

### main was unbuildable and unbootable for ~40 minutes (2026-07-31)
`d40a3d4` was cut from an old `1a27bbf` and merged in via `009250f`. Three separate
breakages, fixed in that order:
- [x] **Build**: `/sdfsdkjfk` between `SENSITIVE_KEYS` and `signedIn` in `Session.kt` —
  a stray keystroke that failed `compileDebugKotlin`, so nothing Android could build.
  Line removed, nothing around it touched.
- [x] **Boot**: two alembic heads. `c93f2b7a5e18` (media_assets) claimed
  `b8e6d1a4f527` as its parent, which `c7a4e91b6d38` already held, and `prestart.py`
  runs `upgrade head` at boot — which refuses to choose. Merge revision
  `8c27b8990a90` joins them; empty on purpose (the branches touch disjoint tables).
  **Generated with `alembic merge`, not hand-written** — this is exactly the case the
  CLAUDE.md gotcha warns about. Verified by applying the whole chain to a virgin
  database: both branches converge, `media_assets` + `content_items.video_url` exist,
  `alembic_version` = the merge.
- [x] **Repo hygiene**: 14,270 tracked junk files removed — two Windows virtualenvs
  (`env/` 4,624 and `backend/env/` 6,180, including `Scripts/*.exe`) and 3,466
  `__MACOSX/` AppleDouble stubs from an unpacked third-party APK. Nothing tracked
  referenced them. `.gitignore` now covers `env/` (it only had `venv/`/`.venv/`),
  `__MACOSX/`, and `*.xapk`/`*.apk`/`*.aab`.
  **History is not rewritten** — `.git` stays ~233 MB. Recovering that needs a
  force-push and every clone re-made; left as the owner's call.
- Checked and clean: no secrets entered history (no `.env`/`.pem`/`.key`), and the
  decompiled Calm APK's audio was never committed — only the `__MACOSX/._*` metadata
  stubs that name it.
- [ ] **Follow-up: `media_assets` is schema with no code behind it** — the migration
  landed without a SQLAlchemy model, a route, or a seed, and nothing in `backend/app`
  references `media_assets` or `video_url`. Harmless (the column has a server default)
  but dead until the model lands. Whoever owns the media work should either bring the
  ORM side or drop the table.
- Verified after all three: backend **379 passed, 2 skipped, 95% coverage**; Android
  **229 unit tests green**; claims gate clean.

### Android: the tab bar now yields its slot to the keyboard (2026-07-31)
The last unblocked item from the 2026-07-31 audit list. `BottomNavBar` emitted the pill
unconditionally, so with the IME up Scaffold still charged the body the bar's ~78dp for a
bar the keyboard was covering — and every screen body also carries `imePadding()`
(Common.kt `Page`), so the two stacked into an empty band above the keyboard. Measured on a
CPH2681 (Android 14): ~90dp of dead space between the Talk composer and the keyboard.
- [x] The pill is hoisted out of the Scaffold into `BottomNavBar`, which returns before
  emitting anything when the IME is visible — Scaffold then reserves nothing. `imeVisible`
  is a parameter defaulting to `WindowInsets.isImeVisible`, so the rule renders off-device.
- [x] `BottomNavImeTest` (Robolectric) measures the **reserved slot**, not the pill's
  presence: keyboard up → the body reaches the window bottom; keyboard down → ≥72dp is
  reserved and the tabs are displayed. Confirmed to fail with the guard removed.
- [x] Verified on the device both ways: composer flush against the keyboard while typing,
  nav back and focus retained after dismissing it.
- Note for whoever runs the suite cold: the first full `testDebugUnitTest` on a cold
  Robolectric cache took 11m and threw 12 `AppNotIdleException`s across three unrelated
  Compose classes. Warm runs are ~15s and green (229 tests). It's an Espresso idle timeout
  under first-run load, not a real failure — re-run before chasing it.

### Landing → web app: the missing front door (2026-07-30)
The landing had **zero** links to `apps/app`. Every CTA was "Join the waitlist" and the only
other button was the App Store "coming soon" chip, so a visitor could not reach a product
that has been live at `app.cerebrozen.in` the whole time. Structure borrowed from the Aira
HTML reference (nav sign-in + pill CTA, hero primary/secondary, per-feature "Open X →",
grouped footer); **palette deliberately unchanged** — CereBro's dark indigo tokens stay, so
the landing still matches the app a visitor clicks into and the "web mirrors the iOS palette"
rule in CLAUDE.md holds.
- [x] `apps/web/lib/appUrl.ts` + `NEXT_PUBLIC_APP_URL` build arg in all three compose files
  (default `http://localhost:3002`, prod `https://app.cerebrozen.in` via `PUBLIC_APP_URL`).
  **This fails silently if unset** — the page builds fine and points every link at localhost
  — so `landing.spec.ts` now asserts the hrefs against `APP_URL`.
- [x] Links from four places: nav (Sign in · Open the app), hero (primary CTA; waitlist
  demoted to secondary), the five space cards (Home/Sleep/Talk/Journal/You → their routes),
  and a grouped footer (Open the app · Account · Trust).
- [x] **`?next=` return path in `apps/app`** — without it every deep link resolved and then
  dumped the visitor on Home, so the links would have been a lie. `(authed)/layout.tsx`
  redirects to `/signin?next=<path>`; `signin/page.tsx` returns there. `lib/nextPath.ts` is
  an allow-list (same-origin absolute paths only) because `next` is attacker-controlled —
  `//evil.com`, backslash variants and auth-route loops all fall back to `/home`. Both
  directions pinned in `app.spec.ts`.
- [x] Copy that the change would have made false: the FAQ said "iOS comes first" and "no
  committed public date" — now states the browser version is open today and iOS is next; the
  offline answer is scoped to the mobile apps (the browser client has no offline caching —
  `sw.js` is push-only); hero trust chip "Built for iOS" → "Works in your browser".
- Verified: `tsc --noEmit` on web + app, both containers rebuilt, and a live browser check of
  the whole hand-off (landing link → signed-out bounce → sign-in → lands on /sleep, and an
  off-origin `next` refused). Full e2e suite green.
- [x] Found doing this: `/privacy`, `/terms`, `/support` and the 404 each carried their own
  hand-copied footer, so the app links would have existed on the landing alone — a reader of
  the privacy policy had no door. All five now render one `components/SiteFooter.tsx`.
- [ ] Not done: **admin (:3001) is deliberately not linked** from a public landing page.
- [ ] Follow-up: the App Store badge is still a "coming soon" chip pointing at `#waitlist`;
  when iOS ships, `NEXT_PUBLIC_APP_STORE_URL` turns it into a real listing link.

### PRD checklist #1 / #6 / #7 — the last Phase-0 code (2026-07-30)
Phase 0 (TestFlight) is now entirely owner-blocked; no code is left in it.
- [x] **#6 iOS remote push made reachable.** The server half was always real (ES256
  APNs sender + nudge dispatcher), but nothing populated `user.push_token`:
  no `AppDelegate`, no `registerForRemoteNotifications()`, and
  `APIClient.registerPushToken` had zero call sites. New
  `Features/Notifications/PushRegistrar.swift` (delegate + hex token + UserDefaults
  cache), `@UIApplicationDelegateAdaptor` in `CereBroApp`,
  `BackendService.syncPushToken()` drained on every connect (and on the
  `tokenReceived` notification, for a token that arrives mid-session),
  `aps-environment` in the entitlement. Two rules kept from `ReminderManager`:
  registration never prompts on its own (it is gated on authorization the user
  already granted, and re-attempted right after they grant it), and it is a no-op
  under `-resetState`. Sign-out clears the synced mark so the next account
  re-registers — and, found while reviewing this change, sign-out now also PUTs an empty
  token *before* revoking the session: otherwise the departing account keeps this device as
  its APNs destination and its nudges arrive for whoever holds the phone next (the server
  reads `""` as no token and falls back to Web Push/email). **Deliberate deviation from the
  checklist text:**
  `remote-notification` was NOT added to `UIBackgroundModes` — the server sends
  `apns-push-type: alert` only, so the mode would be unused, and unused background
  modes draw App Review rejections. Still owner-blocked: APNs `.p8` + the Push
  Notifications capability on the App ID (adding `aps-environment` means device
  builds will not sign until that capability exists — simulator is unaffected).
- [x] **#7 iOS Insights wired to the real insight.** `InsightsView` renders
  `backend.insight` — server `headline`/`summary` as the hero, server metrics as the
  bars. Went further than the item asked and **deleted `Dummy.weeklyMetrics`** instead
  of keeping it as the `insight == nil` fallback: two of its four rows ("Sleep
  consistency / Improving / 0.62", "Mood stability / Steady / 0.7") were numbers
  nobody measured. Signed out, the screen now counts only what is on the device
  (check-ins + plan steps, journal entries, the sleep diary's own 7-day average) and
  says "Nothing to measure yet" when there is none. `Dummy.baselineMetrics` went with
  it (also unused; the real baseline comes from `state.baselineStress/Sleep`).
- [x] **#1 the last three paywall/feature over-claims.** Android `premium_intro` said
  "unlimited voice", implying a voice meter that does not exist — voice is not metered
  at all; it now names the quota `services/usage.py` actually enforces ("unlimited
  daily conversations — free includes 50 messages a day"), en + hi. The web library
  footnote no longer promises "offline playback"; it scopes the claim to the mixer.
  iOS `Dummy.offline` and its unreachable `OfflineView` are deleted rather than
  reworded — no client implements downloads. Note the Hindi `premium_intro` now carries a
  numeric entitlement claim ("हर दिन 50 मैसेज"); it is machine-assisted like the rest of
  the draft `values-hi`, so add it to the reviewer's list — a mistranslated quota is a
  pricing claim, not a tone problem.
- [ ] Follow-up found while doing this: the other three views in
  `apps/ios/CereBro/Features/States/StateViews.swift` (`EmptyJournalView`,
  `VoiceLoadingView`, `VoiceErrorView`) are also unreferenced. Their copy is honest,
  so they were left alone — but they are dead code either way.
- [x] **#8 `today_guide` on iOS and web** (checklist item 8, Phase 1) — "Sleep Reset" was a
  7-day program only on Android; iOS and web showed "day N of 7" and nothing about day N.
  iOS `RemoteProgram.today_guide` → a guide block in `ProgramProgressCard`; web
  `Active.today_guide` → a guide section on the programs page. Additive on all three:
  no `day_guides` ⇒ the field is absent ⇒ the card renders exactly as before, and a blank
  title+body counts as no guide (matching Android's `parseTodayGuide`). New contract row
  in ARCHITECTURE.
- Verified: iOS `BUILD SUCCEEDED` + UITest suite, Android `testDebugUnitTest` green,
  `tsc --noEmit` clean on `apps/app`. Backend untouched.

### Evidence-based redesign, Phases 1–2 (2026-07-12) — 6 implementation waves
Research-driven redesign per docs/REDESIGN.md (verified findings F1–F11). All waves
compile/test-green; emulator smoke-verified end-to-end (Home, Toolkit, breathe engine,
Sounds/Mixer, Sleep CBT-I cards, 8-step onboarding, 6-category consent; zero crashes).
- [x] **IA consolidation**: 4 breathing surfaces → one parameterized `Breathe.kt` engine;
  Games+Tools → one Toolkit hub (Ground/Breathe/Reframe/Settle); killed memorymatch,
  slidingpuzzle, bubblewrap, colorbreathing; onegoodthing/intention → Journal prompt chips;
  sounds+soundscape+player → one Sounds hub (Library|Mixer) with `sounds/mixer` deep-link.
- [x] **Audio exclusivity**: `Player.play` ⇄ `SoundscapeMixer.play` cross-stop (loop-safe,
  Robolectric-tested 4/4) — the two engines can no longer play simultaneously.
- [x] **Home de-densified** 11 → 6 blocks, check-in first; streak → "presence" framing
  (no loss/reset language anywhere).
- [x] **Safety**: crisis ≤2 taps (You Support door + Toolkit footer); Tele-MANAS now leads
  CrisisScreen (call + WhatsApp); HumanSupport stubs replaced with real Tele-MANAS/iCall/
  findahelpline links.
- [x] **Credibility layer**: `WhyThisWorks` provenance footers on breathe/CBT/TIPP/
  gratitude/programs; "How CereBro is built" honesty cards; Sleep reframed to
  "improve your sleep" with CBT-I stimulus-control education + "Your rhythm" consistency
  insight (pure helpers, unit-tested incl. midnight wrap).
- [x] **Talk**: "Try together" structured-exercise chips (CBT reframe / box breathing /
  grounding) in empty + active conversations — rule-based-first per evidence F3.
- [x] **Onboarding**: 10 → 8 steps (fake Plan preview killed; Age merged into Disclosure);
  consent step now renders all 6 DPDP categories.
- [x] **Tokens**: semantic role layer in Color.kt; WCAG contrast fixed (TextMuted2
  0xFF928CAC → 0xFFA5A0BA; all text/surface pairs ≥4.5:1) with a 7-test ContrastTest gate;
  fake glassmorphism + Haze dependency removed; 12 orphaned tokens pruned.

### Android artwork system (2026-07-12) — W21
- [x] **W21 generative content art** (`ui/screens/ContentArt.kt`): deterministic Canvas
  artwork per (title, kind) — kind-family diagonal gradient (soundscape/sleep→Violet/
  ThumbBlue, meditation/wind_down→Teal/ThumbBlue, program→ArtWarm/ThumbRose,
  default→ArtPeriwinkle/ThumbIndigo) with an fmix32-avalanched per-title hue drift
  (`artSeed`, unit-tested for determinism + distribution), one calm motif per kind
  (moon+stars / sine waves / breathing rings / rising day-dot path / brand orb) and an
  8% top-left light. Static, network-free, constant-dark in both themes. Applied to
  `ContentRow`/`ContentList`/Search rows, Today rail, `HeroCard` (Unsplash `HeroImg`
  URLs deleted — heroes are art-first, AsyncImage only over real `image_url`s),
  Player art, Programs rows + enrolled `GradientHero`, `FeaturedGameCard`, and
  `InfoBanner` gained `artKind` (40dp art medallion + ≤10% leading accent wash —
  worst-case blend contrast-gated in `ContrastTest` for both themes; program +
  wind-down banners wired, utility banners stay icon-only).

### Android deep review + fixes (2026-07-12) — 6-agent audit, then fixed
Ran a parallel 6-dimension review of the whole Android client, then fixed the findings
(`:app:assembleDebug` + `:app:testDebugUnitTest` green via the AS-bundled JBR). Highlights:
- [x] **App identity restored**: reverted an accidental `com.cerebro.app` namespace/applicationId
  (a "cerebro**zen**"→"cerebro" slip in the `cc7cbd4` "ui" commit) back to `com.cerebrozen.app`,
  collapsing the namespace-vs-source split-brain (manifest back to relative component names).
- [x] **PATCH works in prod**: `Session.realHttp` now forces the method past Android's
  `HttpURLConnection` allow-list via reflection — profile/plan/consent PATCH writes were throwing
  `ProtocolException` (tests missed it; transport is stubbed).
- [x] **Voice/mic**: "Text" during a live session now tears down the mic (`endSession`) instead of
  leaving it hot; TTS gated on init so the first cold-start reply isn't dropped; recorded voice
  files deleted on dispose; cloud playback disk I/O off the main thread.
- [x] **Audio services**: foreground-start contract satisfied before player creation (no more
  `ForegroundServiceDidNotStartInTimeException`), `SoundscapeService` player creation guarded,
  audio-focus + becoming-noisy + wake-mode on every ExoPlayer, re-entrant `release()` in
  `onPlayerError` hopped to the main handler, idle-service starts guarded.
- [x] **Reminders survive reboot**: added `BootReceiver` (BOOT_COMPLETED + MY_PACKAGE_REPLACED) +
  `RECEIVE_BOOT_COMPLETED`; wired the onboarding notification choice to actually schedule.
- [x] **Consent integrity**: Settings consent/companion/region toggles now revert + surface an
  error on a failed server write (were silently optimistic); Journal "Private mode" toggle routed
  through the same device-credential gate as Settings (shared `BiometricGate.kt`).
- [x] **Networking hardening**: refresh token + response cache moved to `EncryptedSharedPreferences`
  (private-prefs fallback); SSE cancellable + disconnects on leave; GET cache-fallback only on
  connectivity/5xx (not 4xx); DEBUG log no longer echoes unparseable bodies raw.
- [x] **State loss**: `rememberSaveable` for the onboarding funnel (step + selections + consent),
  Talk draft + crisis banner, Journal draft, Auth identifiers (not the password).
- [x] **Compose correctness**: Talk auto-scrolls to newest; draft cleared on send; `MediaUrls.register`
  moved out of composition into a `LaunchedEffect`; Zen-ripples frame loop self-stops when idle;
  Pattern-glow replay keyed on a nonce; onboarding breathing honours Reduce Motion; removed a dead
  duplicate `SignUpStep`.
- [x] **Design tokens**: eliminated all raw brand `Color(0x…)` hex from `Onboarding`/`Auth`/`Common`/
  `Extras` screens — promoted to named tokens in `Color.kt`; updated stale glass/CTA KDoc after the
  opaque reskin.

### Android UI/UX audit + fixes (2026-07-08) — full-screen design-system + a11y pass
Audited all ~20 Compose screens against the design tokens / `Common.kt` shared
components, then fixed the findings (compiles clean via the AS-bundled JDK 21;
`:app:testDebugUnitTest` green). Highlights:
- [x] **Design-token discipline**: removed all 12 raw `Color(0x…)` hex literals from
  screens — tokenized `HeroCard` (shared, fixed 4 at once), the Talk voice-orb and
  You-avatar gradients, and `GuidedTour`'s card. Added tokens `PeriwinkleDeep`/
  `PeriwinkleSoft` + thumbnail-floor tokens (`ThumbBlue/Rose/Indigo`) to `Color.kt`;
  promoted `Type.kt` to define the previously-undefined `titleSmall`/`bodySmall`/
  `labelLarge` (were silently falling back to Material defaults across 9 files).
- [x] **Shared components** (`Common.kt`): added `AppSwitch` (brand-tinted), `DangerButton`
  (destructive CTA), `SectionCard(onClick)`, and `AppTextField` `trailingIcon`/
  `keyboardActions` slots — replaced hand-rolled cards/switches/buttons and the
  raw `MaterialTheme.colorScheme.error` usages app-wide.
- [x] **Crisis safety (High)**: `Extras.CrisisScreen` helpline numbers/URL are now
  tappable (`tel:`/`https:` intents) with labels — a user in crisis can dial.
- [x] **Touch targets ≥48dp**: Today search well (was 40dp), Extras favourite heart
  (was 22dp), Games bubble-wrap cells (`minimumInteractiveComponentSize`).
- [x] **a11y**: semantics/`contentDescription` on the sleep chart, game tiles/pads,
  volume slider, time steppers; full-row `toggleable` on plan steps; icon play control
  replacing a `▶` glyph.
- [x] **State coverage**: real loading + error/retry states for Plan, Patterns, Search,
  and Extras Insights/Programs (failures no longer masquerade as empty/"no data").
- [x] **Forms**: IME Next/Done + focus flow and password-reveal toggles on Auth &
  Onboarding; Settings export-failure now shows in `Danger` (was green `Ok`), and
  account-deletion no longer signs out on server failure (busy/error states added).
- [x] **i18n**: Urdu (`ur`) consent notice now mirrors RTL on Onboarding + Settings.
- Remaining (owner): real-device QA + full TalkBack audit still pending (emulator/
  compile-verified only); two pre-existing `MenuBook` AutoMirrored deprecation warnings.
- [x] **Motion/polish pass — Today + Talk** (2026-07-08): added shared, calm-by-design
  motion primitives in `Common.kt` — `Modifier.pressScale` (soft spring press-in on
  `PrimaryButton`/`DangerButton`/`PickChip`/`QuickTile`), `Modifier.appear` (one-shot
  rise+fade with optional stagger index), and animated selection cross-fade on
  `PickChip`. Today: screen settle-in on load, staggered quick-tile cascade, cascading
  streak-week dots, check-in confirmation eases in via `AnimatedVisibility`. Talk:
  chat bubbles rise in, live reply shows a blinking-caret `StreamingBubble`, and a
  pulsing `TypingDots` indicator while the companion composes. Compiles clean; units
  green.
- [x] **Motion extended to the remaining tabs** (2026-07-08): `SectionCard(onClick)`
  now carries its own press-in, so `NavRow`/`SelectableRow` (Settings/You) and
  `ContentRow` (Sounds, Sleep stories, Search, Favourites, Games hub) inherit it;
  `SubPage` gains the settle-in rise so all ~15 pushed sub-screens ease in; Journal
  history entries stagger. All five nav tabs + sub-screens now share one calm-motion
  language. Remaining: on-device tuning of durations/damping (numbers live in
  `Common.kt`), and a TalkBack pass to confirm the added semantics read well.

### Android Reduce-Motion parity (2026-07-08)
- [x] Added a `rememberReduceMotion()` helper (reads `ANIMATOR_DURATION_SCALE == 0`,
  the Android analogue of iOS Reduce Motion) and wired it through the motion
  primitives, matching iOS's policy — guard entrances + looping animations, keep
  discrete press/selection feedback: `appear` settles instantly; the `Page`/
  `SubPage`/`TodayScreen` settle-in rises snap; the Talk `VoiceOrb` pulse,
  `StreamingBubble` caret, and `TypingDots` rest static. `pressScale` and the
  `PickChip` selection cross-fade intentionally stay (iOS keeps `.pressable` and
  chip springs too). Compiles clean; units green.
- [x] Automated guard for the branch: `reduceMotionFromScale(scale)` pure seam +
  a `ScreenLogicTest` case, PLUS the first Android **Compose** test —
  `ReduceMotionComposeTest` renders `rememberReduceMotion()` and the `appear`
  entrance off-device via Robolectric, asserting the branch flips with
  `ANIMATOR_DURATION_SCALE` (0 → reduced, 1 → full). Added Robolectric 4.14.1 +
  `androidx.compose.ui:ui-test-junit4` + `ui-test-manifest` +
  `testOptions.unitTests.isIncludeAndroidResources`. Runs in the existing
  `:app:testDebugUnitTest` job (no emulator); note Robolectric adds ~75s to that
  job's first Compose-test run. Suite 51→**52** passing.

### iOS chat-motion parity (2026-07-08)
- [x] Ported the two genuinely-missing Android chat micro-interactions to iOS
  (`Features/Talk/TalkView.swift`): committed chat bubbles now `.entrance()` in
  (both the Oracle `backend.chat` and offline `state.chatHistory` loops), and the
  streaming Oracle reply shows a `TypingDots` indicator until the first token,
  then a `StreamingBubble` with a blinking caret (was a static "…"). Both honor
  Reduce Motion. NOTE: **static-verified only** — the build host is Windows, so
  this wasn't compiled with `xcodebuild`; owner should build once on macOS.
  Everything else in the Android motion pass (`.pressable`, `.entrance` staggering,
  animated chip/mood selection, `.celebration` check-in reward, `ScreenScaffold`
  settle-in) already existed on iOS at parity-or-better, so nothing else ported.

## Open — code/product work

### B2C Tier 1 — SHIPPED 2026-07-30 (see the commits on `fix/ui-worldclass-103`)
- [x] **Persisted, addressable memory** (`context_memories`) — closes the PRD note that
  granular editing was "not implementable against the current schema". Only what the user
  wrote or approved is stored; mined patterns stay computed and are *hidden* via a
  tombstone, never persisted as facts. Per-item edit/delete on all three clients.
- [x] **Personal safety plan** (Stanley-Brown, versioned, archive-not-delete) — **user-authored**;
  the reference implementation's AI risk-classifier authorship was deliberately not copied.
  Guided flow + offline copy on all three clients, print-ready page instead of a PDF dependency.
- [x] **Weekly digest** — `compute_weekly` was computed but never delivered. Snapshots one
  `Insight` row per ISO week (the model's first ever writer) and rides the existing dispatcher.
  A quiet week is not sent.
- [x] **Recommendations** — closes "an interventions engine that acts on mined patterns".
  Hand-authored `practice_catalog`, every suggestion carries its reason verbatim, dismissing
  is permanent. The reference's `interventions` rule engine was NOT ported (its own clinical
  review puts it in the always-excluded tier).
- [x] **Goals + habits** — the first things in the app the *user* defines. `decompose` feeds
  the one existing plan; habits have no streak field by design.
- [x] **Claims gate revived** (`scripts/check-claims.mjs` + `docs/CLAIMS_MAP.md`, in CI),
  widened from the sibling's web-only scan to iOS Swift and Android strings.xml — where
  every over-claim actually found here was living.
- [x] Recommendations now render on iOS and Android too — all three clients in step.
- [x] Goals & habits on iOS and Android — all three clients in step.
- [x] Rituals / commitments / affirmations **assessed and mostly dropped** — the reasoning
  is in B2C_BACKLOG.md §4b. Commitments duplicate goals + plan steps; gratitude is a
  journal entry; custom rituals are habits and daily quests are the plan + streak;
  affirmations should be a `content_items` kind, not three new tables. One survivor left
  open on purpose: a **daily intention**, which needs a product call first (does it replace
  the generated `Plan.focus`, or sit beside it?).

### B2C feature candidates — plan in [B2C_BACKLOG.md](B2C_BACKLOG.md) (2026-07-30)
Filtered from the second CereBro codebase at `~/Desktop/workspace/cerebro` (a **different
product**, `cerebrolearning.com`, 111 API domains). B2B/HR and clinical/EHR planes are
excluded by the B2C-only decision; the doc says why per category.
- [ ] **Tier 1 (each closes a gap PRD.md already documents):** persisted addressable
  `memory` (today's schema makes per-item edit/delete impossible) · `recommendations` +
  practice catalogue (patterns are display-only) · **personal safety plan**
  (Stanley-Brown six sections; take the schema, *not* the sibling's AI-authorship —
  user-authored, per the doc) · weekly digest delivery.
- [ ] **Tier 2 — the consumer habit loop:** habits, goals (+ `decompose` → feeds the
  existing agentic planner), rituals/intention, commitments, affirmations. No `Habit` or
  `Goal` model exists here at all.
- [ ] **Tier 3 — skills content:** DBT skills, MBCT, behavioural activation, role-play,
  guided imagery, dreams. Each needs the non-clinical framing pass + its own PRD row.
- [ ] **Flagged, needs an owner decision before any code:** gamification/leaderboard vs
  the OECD dark-pattern checklist; peer community (24/7 moderation commitment —
  recommend deferring the whole category).
- [ ] **Two owner decisions block the recommended first slice:** what memory persists
  (privacy posture) and who authors the safety plan.


### Narrated-audio content pipeline (2026-07-07) — content depth, the biggest retention lever
- [x] Backend: `content_items` gains `narration_script` (admin-authored) + `audio_url`
  + `audio_generated_at` (Alembic `a7c4e9f2d310`); `POST /admin/content/{id}/narrate`
  (synchronous ElevenLabs via the existing `voice.synthesize` with a 300 s budget,
  3/min rate limit, honest 503/400/422/502 ladder); MP3s at `MEDIA_ROOT/narration/`
  served by a public `/media` StaticFiles mount (Range/ETag — native players seek);
  prod named volume `media:/app/media` (+ Dockerfile pre-chown mkdir); delete unlinks
  minted files; public `/content` exposes `audio_url` but NEVER the script
  (`AdminContentOut` carries it for the CMS). 9 seed narration scripts (sleep story,
  breathwork, 3 meditations, 4 wind-downs — soundscapes/programs deliberately none;
  empty-only backfill never clobbers admin edits).
- [x] Clients (same-day): iOS — `SoundscapePlayer` streams narration via `AVPlayer(url:)`
  (failure → ambient engine fallback; never loops; mix UI hidden while narrating; all
  behind the `-resetState` gate so UITests stay deterministic); Android —
  `MediaUrls` resolve/registry → `AmbientService` `setDataSource`+`prepareAsync`
  (onError → bundled bed; honest notification copy); web — `<audio controls>` on
  Library + Sleep stories + CSP `media-src`; admin — script textarea + per-row
  Generate/Regenerate with keyless-honest error.
- [x] Android now-playing bar labelled "AMBIENT BED" even while a narrated title
  streamed its own audio — `NowPlayingBar` now derives the label from
  `MediaUrls.urlFor(title)` (narration vs ambient), matching the full `PlayerScreen`.
  Found on-device 2026-07-08; iOS ("Now playing" neutral eyebrow) and web (per-item
  `<audio>`, narrated items only) were already correct — no parallel bug.
- [x] iOS player eyebrow now mirrors Android's narration/ambient distinction —
  "Now playing · Narration" vs "· Ambient bed", driven by `SoundscapePlayer.isNarrating`
  (reactive, follows the honest fallback if narration fails). 2026-07-08. Not built on
  the Windows dev box — verify on a simulator before shipping.
- [ ] Follow-ups: premium audio gating (signed short-lived media URLs) once a
  premium narration catalogue exists; bulk "generate all missing" if the catalogue
  outgrows per-row clicks (~25+); persistent web player; compute real `duration_min`
  from the generated MP3 (needs a decoder dep). OWNER: click Generate per seeded item
  (burns ElevenLabs credits, ~15–30k chars total).

### Ref-mock audit follow-ups (ref/ design screens, audited 2026-07-07)
- [x] Backend + Android: program enrollment (`/programs` router + `program_enrollments`
  table, Alembic `0b8e5d2f7a41`; day computed from start date) — "PROGRAM · DAY X OF 7"
  Home card + enroll/leave on Programs. Device-verified; suite 250 passed / 95 %.
- [x] Backend + Android: Pattern Dashboard (`GET /insights/patterns` honest 60-day
  derivations w/ per-source consent gates + `DELETE /users/me/memory` chat/insights/
  Oracle-checkpoint wipe) — You → Pattern dashboard screen.
- [x] Android: Daily Plan route (step toggles + regenerate), Search route (whole
  catalogue), immersive live-voice session overlay (timer/state/End/Text),
  first-run guided tour (4 stops, `tour_done` pref).
- [x] iOS + web ports (2026-07-07): iOS — RemoteProgram/RemotePatterns APIClient
  endpoints, Home ProgramProgressCard, ProgramsView real enroll/leave, Pattern
  dashboard (You row), GuidedTourOverlay (gated off under `-resetState` so
  UITests stay deterministic; build + Home UITests green). Web — /patterns page
  (+account link), programs enroll/active banner, Home journey card, GuidedTour
  overlay; e2e journey extended (tour walk/skip, enroll → Home card, patterns
  empty state + delete-memory round-trip) — full docker e2e suite green.
- [ ] Proactive stress detection (ref Home card: Watch HRV → "start 2-min reset") —
  blocked on HealthKit capability/portal (owner) + needs the paired-Watch feature bet.

Interactive-mock comparison round 2 (`ref/CereBro App.html` driven end-to-end in
Playwright, 2026-07-09). Onboarding matches step-for-step where it matters; iOS is a
deliberate superset (under-18 exit, signup step, 5 consent toggles + one-tap
"Remember my patterns", "Private previews" chip intentionally dropped 07-04).
Remaining iOS deltas the mock still wins on — CLOSED for iOS + web 2026-07-09
(iOS: sim build green + 6 affected UITests passed; web: `next build` green):
- [x] iOS Home quick-links grid (Games / Insights / Programs / Sounds) —
  `QuickLinksGrid` on Home; Sounds opens a new `SoundLibraryView` (filter chips
  over the served catalogue, offline fallback). Web: `.quick-grid` on /home.
- [x] Weekly-insights teaser card on Home ("This week · See what changed ·
  weekly insights" → Insights) — iOS NavRow + web teaser card (web shows an
  honest last-7-days check-in count when data exists).
- [x] State-tuned journal prompt — `JournalPrompts.tuned(toMood:)` reshapes the
  Journal hero from today's check-in (tense/heavy/tired variants); same mapping
  on web /journal via `GET /moods?limit=1`. Daily rotation stays the fallback.
- [x] "Take a quick tour" row in You/account — clears only the tour-done flag
  and returns to Home where the tour re-runs (nothing else touched).
- [x] Motion accents from the mock's system: iOS `RadiatingRing` (streak
  milestone halo) + occasional `sheen()` on the Premium upsell (distinct from
  the continuous loading `shimmer()`), both Reduce-Motion-gated. Web: full cz*
  keyframe port (13 keyframes + settle/spring easing tokens) in globals.css —
  entrance staggers on all authed pages, selection pop, orb breathe, premium
  sheen, streak ring, button press springs, one `prefers-reduced-motion` kill
  switch.
- [x] Android parity for the new bits it lacks — DONE 2026-07-31. All four, each
  verified on the `cere_smoke` emulator against the local API:
  - **Weekly-insights teaser on Home.** Insights was reachable only from You, so the
    one screen answering "did any of this help?" sat two taps off the main surface.
    The subtitle carries the real last-7-days count (`checkInsThisWeek`, seven days
    *inclusive* so it matches the presence ring beside it) and falls back to plain
    copy at zero. Seen live: "1 check-in in the last 7 days".
  - **State-tuned journal prompt.** Mirrors `JournalPrompts.tuned(toMood:)`; an
    Anxious check-in turns the hero into "For a tense day / Name the worry". Two
    deliberate divergences from iOS, both pinned in `TunedPromptTest`: the match is
    **case-insensitive** (mobile posts "Anxious", the browser client posts "anxious",
    and iOS's exact-string `switch` silently misses the latter — both castings are in
    the dev database), and "today" is resolved in the **reader's timezone**, not the
    UTC one the server stamps, so a late-night entry still tunes. "Try another" opts
    out into the rotation.
  - **Tour re-trigger row in You.** `TourState.reset()` clears only `tour_done`;
    verified the four-stop overlay re-runs from stop 1.
  - **Motion accents.** `RadiatingRing` (iOS's numbers exactly: 0.6→1.35, 0.5→0
    opacity, 2s ease-out) on the streak-milestone line, and `Modifier.sheen()` on the
    Premium row. Both Reduce-Motion-gated, and both take the gate as a *parameter* —
    an endless animation is how a Compose test stops going idle.
- [x] Follow-up from that pass, now closed: **the milestone halo is verified on a
  device**. It only draws when `milestoneLine(streak)` is non-null, so the demo
  account's streak of 1 could never show it; two backdated `mood_logs` rows took the
  server-computed streak to 3 (`isMilestone` = 3/7/14/21/30/50/100), and four frames
  0.7s apart caught the ring mid-swell, gone, mid-swell, gone — expanding and fading
  on its 2s loop as designed. The seeded rows were deleted afterwards and the streak
  confirmed back at 1. (The sheen was caught on device in both themes too — and
  needed fixing: a white sweep is invisible on Dawn's near-white card, so the
  highlight is now theme-aware.)
- [ ] Splash consolidation nice-to-haves (review-2 deferrals, 2026-07-10):
  OrbMark's three breathing circles vs `RadiatingRing` are two names for one
  ring vocabulary; the Wordmark glint is a third shimmer implementation
  (Shimmer/Sheen exist); the three splash TimelineViews could share one
  30fps Canvas clock. All bounded to a 2.2s screen — polish, not debt debt.
- [ ] Signed-out re-entry to auth now lives ONLY on Talk ("Sign in to talk
  live") after the You sign-in CTA removal (product decision: login is part
  of onboarding). If "Maybe later" ever leaves the signup step, delete this
  note; if it stays, consider whether You should regain an entry point.

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
- [x] Instrument licensing CHECKED (2026-07-07): both are paid-license for
  commercial products — **PSQI** © U. Pittsburgh (free non-commercial only;
  commercial license via Pitt Office of Technology Management; no modifications
  without written permission); **ISI** © C.M. Morin, distributed by Mapi
  Research Trust/ePROVIDE (license agreement + user fee for commercial use;
  translations via Mapi/ICON). Verdict: keep NOT shipping either verbatim — the
  own-wording plain-language 1–5 baseline stays (details + sources in
  [SLEEP_TRACKING.md](SLEEP_TRACKING.md) non-goals). Owner: license via
  Pitt/Mapi only if a validated instrument ever becomes a product requirement.

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

### Design refresh — "Newsreader warm" system (ref/ mockups, 2026-07-05)
Implementing the Claude-designed refresh in `ref/` across iOS + web (Android later).
The `ref/` HTML mockups are the target; `uploads/ios-screens/*.jpg` are current-iOS
renders used as design input. Decisions locked with owner: full token evolution,
responsive sidebar + mobile-tab web shell, full web auth parity (Apple+Google+email+OTP),
sequence tokens → web onboarding/auth → web shell/screens → iOS polish → landing.
- [x] **Phase 1 — token + type foundation (2026-07-05):** warmed the shared palette
  (`design/tokens.css` → synced to all `globals.css`; iOS `Theme.swift` Brand mirror):
  night `#080b22`→`#0e0c22`, periwinkle `#6f7bf7`→`#8a7bf0`, ink→`#1c1740`, amber→coral
  `#f0a48c`, rose→`#e08a9a`, mint→`#7ee0a8`, added `--warm`/`--cyan`. Web headings
  Georgia→**Newsreader** via `next/font/google` (self-hosted at build — CSP-safe;
  `--font-serif` with Georgia fallback) in `apps/{app,web}`. Warmed the hardcoded rgba
  glows/backdrops + OG/favicon generators. `apps/app` build green (Newsreader woff2
  self-hosted, 10 routes). iOS keeps its native New York serif (platform Newsreader-alike).
- [x] **Phase 2 — web onboarding + auth (2026-07-05):** ported the iOS 10-step funnel
  to `apps/app/app/onboarding/page.tsx` (value-first: age gate → AI disclosure →
  language → one-tap state → CSS breathing reset → goal-derived first plan → signup →
  private-by-default consent → reminders → Enter). Draft collected locally
  (`lib/onboarding.ts`) and applied to the server after the account exists
  (attest + consent + profile/motivations/goals + email-nudge opt-in). Shared
  `components/AuthPanel.tsx` (web port of iOS AuthForm): **Sign in with Apple +
  Continue with Google** (via `lib/social.ts`, SDKs loaded only when
  `NEXT_PUBLIC_{GOOGLE_CLIENT_ID,APPLE_SERVICES_ID}` set — inert-but-honest
  otherwise, CSP-clean by default) + email/password + passwordless OTP. Gating
  mirrors iOS (`hasOnboarded` in localStorage; `/` gate → onboarding|signin|home;
  `/signup` redirects into the funnel; returning sign-in marks onboarded). Build
  green; Playwright walkthrough screenshots verified all 10 steps render on-brand
  (warm palette + Newsreader, one-tap consent flips the 4 pattern toggles). Closes
  the standing "Google sign-in" web item. Owner still needs the OAuth client ids to
  make the social buttons live.
- [x] **Phase 3 — web app shell + screens (2026-07-05):** rebuilt `apps/app`
  `(authed)/layout.tsx` as a responsive shell — left **sidebar** on desktop (Menu:
  Home/Talk/Sleep/Journal/You + Explore: Insights/Plan/Library + Sign out) and a
  floating **bottom pill tab bar** on mobile (< 900px), per `CereBro Web.dc.html`.
  Extracted a reusable component library: `components/icons.tsx` (inline SVGs, CSP-
  clean), `components/ui.tsx` (PageHeader, HeroCard, Panel, SectionTitle, Row, Chip,
  WeekDots). Rebuilt **Home** to the hero-card design (gradient mood check-in card,
  streak week-dots, "Keep going" rows); the other authed screens inherit the new
  shell + warm palette + Newsreader. Verified end-to-end: brought up db+api, created
  a real account through the funnel, screenshotted the authed shell at desktop
  (sidebar) + mobile (bottom tabs) with live streak/name data. Build green (15 routes).
  Follow-up (optional): per-screen hero rebuilds for Talk/Sleep/Journal/Insights.
- [x] **Phase 4 — iOS refresh + polish (2026-07-05):** the warm palette propagates
  through tokens — audit found **zero hardcoded hexes** outside `Theme.swift` +
  `SplashView`, so every screen moved with the palette. `xcodebuild` green (iPhone 17
  Pro sim, iOS 27); launched + screenshotted Home — warm indigo/purple gradient, New
  York serif headings, hero card + streak orb + floating tab bar all render on-brand.
  (The funnel + auth already matched the ref pre-refresh.)
- [x] **Phase 5 — landing refresh (`apps/web`, 2026-07-05):** landing already carried
  the warm palette + Newsreader from Phase 1; the phone hero screenshot was the last
  stale (cool) asset — regenerated `public/screens/home.webp` from a fresh warm iOS
  Home capture (640×1391 webp), so the hero now matches the warm page. Warmed the
  OG/favicon generators earlier (Phase 1). Follow-up: `journal-entry.webp` +
  `sleep-player.webp` showcase thumbnails still show the old palette — regenerating
  them authentically needs an XCUITest nav pass (simctl can't tap; Simulator ran
  headless), deferred as low-priority (below the fold).

### Design refresh — open follow-ups
- [x] Logo adoption (2026-07-05): adopted the **C-ring + orb mark**, warm-recolored to the
  palette (lavender→cyan ring, warm-lavender orb; the vector has no "eye" dot — that was
  raster-only). New warm SVGs `apps/web/public/brand/{cerebro-mark,cerebro-lockup}.svg`
  (Newsreader wordmark). Reusable inline `BrandMark` in apps/web (`components/BrandMark.tsx`)
  + apps/app (`components/icons.tsx`) — landing nav/footer + app sidebar now show the mark.
  iOS: rendered a warm 1024 opaque app icon (flattened RGB, App-Store-safe) → `AppIcon`,
  and a transparent tight mark → `BrandLogo`; `SplashView.OrbMark` no longer circle-clips
  (open ring). Warmed the `LaunchBackground` (#0e0c22) + `AccentColor` (#8a7bf0) colorsets
  (asset colorsets Phase 1 missed). Verified: web builds green + nav mark on-brand; iOS
  builds green + new springboard icon confirmed. OG/favicon deliberately kept as the warm
  orb (the mark's orb element — reads better at 16-32px, avoids satori path limits).
- [x] Marketing banners re-rendered (2026-07-05): all four (App Store feature 1024×500,
  social/OG 1200×628, hero 1920×1080, story 1080×1920) rebuilt with the warm palette, the
  new C-ring mark + Newsreader wordmark, and the current warm app UI (Home / onboarding /
  splash), replacing the kit's old-UI device shots. Live in `apps/web/public/brand/banners/`.
- [x] Per-screen web hero rebuilds (2026-07-05): Talk (AI-disclosure note + serif header),
  Sleep (violet "This morning" hero), Journal ("Release the day" prompt hero), Insights
  (weekly-headline hero + metric bars) all rebuilt with PageHeader + HeroCard + SectionTitle,
  data logic untouched. Build green; screenshotted signed-in against live backend.
- [x] Refresh the two landing showcase thumbnails (2026-07-06): sleep-player + journal
  regenerated from the warm iOS build so the showcase matches the warm-refreshed hero
  (all three screens now one palette).
- [x] Brand-kit assets wired into web clients (2026-07-10): landing hero now features a
  text-free crop of the kit hero banner (`/brand/banner-hero.jpg`, framed card + palette
  fade), static OG/Twitter image → `/brand/banner-social.jpg` (replaced the generated
  `opengraph-image.tsx`/`twitter-image.tsx`), apple-touch-icons on web+app, and the
  512w mark as the web app's favicon (`apps/app/public/brand/cerebro-mark.png`).
  Nav/sidebar keep the crisp code-drawn SVG mark (raster lockup bakes an illegible
  tagline + glow halo at ≤34px). Added public weight ~559 KB total.

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
- [x] Dead-decoration sweep (2026-07-07): Programs now fetches the real
  `GET /content?kind=program` catalogue (hero mirrors the first program; CTA →
  `/plan`); Games gained a genuinely playable box-breathing game (reuses the
  onboarding `.onb-breathe` CSS + phase logic); Sleep soundscapes/stories fetch
  `/content?kind=soundscape|sleep` (dead PLAY buttons removed); Plan + Library
  restored to the EXPLORE nav (were built but orphaned). e2e app spec asserts the
  real program title (grid-card h3 — the hero h2 mirrors it, so `getByText` was
  ambiguous), Start→Stop breathing, and Plan/Library reachability. 11/11 e2e green.
- [x] Home "Today's plan" wired to `GET /plans/active` (2026-07-07): renders the
  served agentic steps (sorted by order; done steps show ✓/DONE/strikethrough and
  link to `/plan`, undone rows deep-link by step symbol — wind→Games, moon/bell→
  Sleep, book/brain→Journal, mic/person/heart→Chat, else `/plan`); quiet
  "Open today's plan" fallback row only on fetch failure; "Open full plan →" link.
  e2e asserts ≥2 real step rows (LLM titles vary, so shape not text; the error
  fallback renders exactly one row, keeping the assertion honest). 11/11 green.
- [x] Web v1 remaining: Google (+ Apple) sign-in — done in the Design-refresh Phase 2
  (2026-07-05) via `components/AuthPanel` + `lib/social`; buttons are live once the owner
  sets `NEXT_PUBLIC_GOOGLE_CLIENT_ID` / `NEXT_PUBLIC_APPLE_SERVICES_ID`.
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
- [x] Web Push (VAPID) — 2026-07-07: `web_push_subscriptions` (Alembic `e52a9c7d3b81`)
  + `/users/me/push-subscriptions` (status+key GET / register POST / unregister
  DELETE; endpoint unique — a shared browser notifies whoever subscribed last) +
  `services/webpush.py` (pywebpush, RFC 8291 encrypted payloads; 404/410 endpoints
  pruned in place). `dispatch_due` preference: native push → browser push → email
  opt-in → honest `skipped`. Keys are a self-generated VAPID pair (`npx web-push
  generate-vapid-keys`; no third-party account — owner sets `VAPID_*` in prod env
  — verified in-container that base64url strings roundtrip); keyless = the
  account-page toggle disables with an honest note (e2e-pinned) and delivery logs.
  apps/app: `public/sw.js` (push + deeplink click-through), `lib/push.ts`,
  account-page "Browser notifications" toggle.
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
- [ ] Processor security clauses with LLM/voice/email/SMS vendors (Rule 6(1)(f)) —
  **prepared 2026-07-07**: per-vendor table + 6-point clause checklist drafted in
  [DPDP_COMPLIANCE.md](DPDP_COMPLIANCE.md) §4; what's left is pure owner execution
  (accept each vendor's self-serve DPA, archive the PDFs, record the no-training
  settings) before 13 May 2027.
- [ ] DPIIT startup recognition (eligibility for the s. 17(3) exemption if an SDF class
  notification ever covers wellness apps).
- [x] Localize consent/notice screens — 2026-07-07: a "notice language" picker ON
  each consent surface (DPDP s.5(3) — iOS onboarding ConsentScreen + PrivacyView
  via `Trust/ConsentNotice.swift`; web onboarding consent step + account page via
  `apps/app/lib/consentNotice.ts`; the two files are a hand-synced cross-stack
  contract). English + the 12 most-spoken Eighth-Schedule languages (hi bn te mr
  ta ur gu kn ml or pa as); defaults follow the app-language step (Hinglish →
  English — Latin script). e2e asserts the हिन्दी re-render on the account page.
  OWNER before 13 May 2027: professional review of all translations + the
  remaining 10 languages (Bodo, Dogri, Kashmiri, Konkani, Maithili, Manipuri,
  Nepali, Sanskrit, Santali, Sindhi); full privacy-policy translation is separate.

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
  Gradle wrapper now committed (./gradlew just works). Warm design refresh
  applied 2026-07-06: `Color.kt` mirrors the warm tokens (indigo night #0e0c22 +
  warm-lavender accent #8a7bf0 + coral/cyan/ok), so every token-driven screen
  recolored at once — emulator-verified Today/Talk/Sleep in the warm palette,
  matching iOS/web. Feature parity round applied 2026-07-06 (all emulator-verified
  against the dev backend): (1) onboarding funnel — welcome → 18+ attest → AI
  disclosure → language → state-check → breathing reset → account → consent →
  notifications; (2) You/Settings depth — live consent toggles (GET/PATCH
  /users/me/consent), data export, account delete, crisis link, sign out;
  (3) new destinations off a Home quick-grid — Insights (/insights/weekly bars),
  Programs + Sounds (/content by kind), Games (live box-breathing), Crisis
  (offline directory + trusted-contact status). Audio + Sleep round 2026-07-06:
  a real MediaPlayer with a bundled ambient bed (res/raw) + a now-playing
  transport wired into Sounds and the Sleep "Wind down" library; Sleep gained a
  live 7-night bar chart (shows at ≥2 nights) — emulator-verified (dumpsys:
  MediaPlayer state:started @16 kHz). Voice + prompt round 2026-07-06: the Talk
  tab is now a real voice companion — an orb driving on-device SpeechRecognizer →
  /chat → TextToSpeech (keyless; RECORD_AUDIO runtime-requested; degrades to text
  where no recognition service exists) — emulator-verified (mic permission →
  cyan listening orb → AudioService recording); Journal gained a rotating
  prompt hero ("Try another"). iOS-interface-parity round 2026-07-06: the You tab
  is now the iOS ProfileView nav-row hub (profile header "name · companion ·
  language" + rows) with new sub-screens — Companion style (4-persona picker →
  PATCH /users/me companion), Privacy & memory, Daily reminder, Premium plan,
  Crisis region (→ PATCH region), Human support, Privacy policy, Export, Delete;
  Games gained the iOS 5-4-3-2-1 Grounding tool. Emulator-verified (You hub +
  companion picker). Fix-all-possible round 2026-07-06: Today's plan card on Home
  (/plans/active); a transparent offline read-cache in the API client (GET
  responses cached, served on network failure — emulator-verified cold-start in
  airplane mode; also fixed refresh() so a network blip no longer signs the user
  out); a real local daily-reminder notification (AlarmManager + channel +
  POST_NOTIFICATIONS, no FCM — dumpsys-verified); a playable Bubble-pop game.
  Polish round 2026-07-06: the C-ring brand mark now ships as the adaptive
  launcher icon (rendered to density buckets + adaptive-icon XML — no more default
  robot), an in-app Canvas BrandMark (onboarding + a brief branded splash), fade
  screen transitions, and haptics on bubble-pop + mood chips. Polish round 2 (all
  emulator-verified): the Newsreader variable font now ships (res/font, wired into
  Type.kt display/headline); quick-grid + You nav-row icons (material-icons-
  extended); a real background-audio FOREGROUND service (AmbientService +
  MediaSession + MediaStyle transport notification with play/pause + lock-screen
  controls — dumpsys-verified category=transport); content-rise page entrance;
  drifting bubbles; and more haptics (companion/region select, tab switch, check-in
  confirm). Remaining polish nice-to-haves: custom (non-Material) brand icon set,
  ambient background motion on Home/Talk. Remaining (genuinely blocked): per-track NARRATED
  audio (needs the content pipeline to serve audio URLs — today every title
  shares the ambient bed), Home HealthKit/Health-Connect card (heavy native).
  Auth round 2026-07-06: passwordless email OTP now fully works
  (/auth/otp/request+verify — emulator-verified end-to-end, new account created);
  "Continue with Google" via Credential Manager → /auth/google is code-complete
  and degrades gracefully until `google_web_client_id` is set (mirrors iOS's
  inert GIDClientID). Owner-blocked (need config): Google sign-in web client id,
  Apple sign-in (Android web-OAuth flow, not yet built), Play Billing (Play
  Console products), FCM push (Firebase project).
  UI/quality round 2026-07-06 (emulator-verified, pushed to main): referenced the
  iOS design system directly — content cards now load real `image_url` photos via
  Coil (iOS's AsyncImage-with-gradient-fallback pattern); photographic `HeroCard`s
  on Home/Journal/Sleep; Talk chat bubbles; a design-system pass (`Modifier.glass`
  cards, gradient `PrimaryButton`, filled `PickChip`, styled `AppTextField`,
  nav-bar selected pill + hairline). First automated Android tests: `SessionTest`
  (6 — auth/refresh/offline-cache, incl. the network-blip-no-signout fix) +
  `ScreenLogicTest` (6 — sleep math, greeting, parsers), via injectable Store/http
  seams on `Session` + `internal` screen logic; CI Android job now runs
  `:app:testDebugUnitTest` before assemble. Accessibility: labeled play/pause +
  voice-orb controls, ≥48dp `PickChip` targets — full TalkBack/real-device audit
  tracked in [ANDROID_QA.md](ANDROID_QA.md). Deps added: `coil-compose`.
  Release-readiness round 2026-07-06: the release build is verified for the first
  time (`assembleRelease` + `bundleRelease` green → `app-release.aab`, unsigned
  pending the owner's upload key); privacy-hardened for Play (`allowBackup="false"`
  + `data_extraction_rules` exclude the refresh token + personal-data cache from
  cloud backup AND device transfer; release stays HTTPS-only with the prod API
  baked in). Play submission runbook + Data-Safety mapping + owner checklist in
  [ANDROID_RELEASE.md](ANDROID_RELEASE.md). R8 minify ENABLED 2026-07-07
  (+ resource shrinking): APK 13.3 MB → 2.5 MB (−81%); emulator-smoked on a
  debug-signed release build (launch → funnel → auth incl. inert Google path,
  zero AndroidRuntime errors) — owner repeats the QA pass on a real device
  before Play upload. Regulatory-parity round 2026-07-07 (top-3 gaps from a
  fresh iOS↔Android audit, all emulator-verified live against the dev backend):
  (1) DPDP consent-notice i18n — `ui/screens/ConsentNotice.kt` (13 languages,
  third copy of the cross-stack contract) + notice-language picker on the
  onboarding consent step AND Privacy & memory (हिन्दी/தமிழ் re-render
  verified); fixed en route: Android had 4 consent toggles PRE-TICKED —
  now everything defaults off (private-by-default parity with iOS/web);
  (2) persistent AI-disclosure pill on Talk + details dialog + 3 h periodic
  re-show (mirrors iOS AIDisclosure); (3) crisis banner on Talk when a reply
  carries the `crisis` suggestion action (sticky, → Crisis screen — verified
  end-to-end: risky message → live safety scan → banner → 112 screen).
  Unit tests 12→16 (crisis detection, notice mapping/fallback, 13×6 contract
  shape). Parity batch 2 (2026-07-07, all emulator-verified live): forgot-
  password ("Forgot password?" → /auth/password/forgot — reset link confirmed
  in api logs); conversation starters on empty Talk (POST /assessment/topics →
  chips; live LLM topics rendered + tapped); Talk "Save this conversation to
  my journal" (→ /journal, entry confirmed in History); journal search (local
  title/body filter, shows at >3 entries); first-party analytics
  (`net/Analytics.kt`: anon install id + opt-out toggle in Privacy & memory,
  onboarding_step/onboarding_done/paywall_view — funnel steps mapped to the
  canonical cross-stack names; verified rows in `product_events` incl.
  welcome/age_gate/disclosure + paywall_view). Found+fixed a real backend bug:
  `/events` `source` pattern rejected `android` with 422 (predated the client)
  — pattern extended + test pinned. Unit tests 16→23. Oracle round (2026-07-07):
  Talk now upgrades to the streaming agentic Oracle when the server has it
  (`Session.sse` — HttpURLConnection SSE with the same refresh-rotation
  semantics as `api()`, seam-tested; deterministic /chat stays the fallback):
  token streaming bubble, inline `widget` frames → `WidgetCard` (breathing/
  grounding→Games, mood_check→Home, mini_journal→Journal, sleep_checkin→Sleep,
  else honest iOS-only note — third copy of the widget-kinds contract),
  `tool_confirm` → Approve/Not-now card → `/oracle/confirm` resumes the same
  thread, `crisis` frames raise the existing banner. Emulator-verified LIVE:
  real LLM stream → "5-4-3-2-1 grounding" widget card → Open→Games; "log my
  mood as anxious" → interrupt card → Approve → resumed stream → mood row in
  Postgres. Unit tests 23→29 (SSE line parse, frame order, 401-rotation
  replay, error-detail surfacing, widget parse/route). Final parity batch
  (2026-07-07, all emulator-verified live): contextual baseline (Home row at
  ≥3 real check-ins → two 1–5 scales, local-only via the Store seam, first
  save wins the date → Insights "Your starting point" card); journal lock
  (androidx.biometric behind a Privacy toggle — graceful unlock with no
  screen lock enrolled, AND the real device-credential prompt verified with
  an emulator PIN); sleep favourites (heart per row + Favourites section,
  keyed by title) + sleep auto-stop timer (NowPlaying chip off→15→30→45→60
  min; AmbientService fades ~10 s then stops); 5 new calm games — Memory
  match, Pattern glow, Zen ripples, Bubble wrap, Gratitude garden
  (persisted) — the Games hub now has 8 activities (iOS-hub parity). Unit
  tests 29→34. **The iOS↔Android parity list is CLOSED.** Per-track narrated
  audio UNBLOCKED 2026-07-07 by the narrated-audio pipeline (see its section
  above) — Android streams `audio_url` tracks with the bundled bed as the
  fallback. Still open: sound MIXING needs multiple simultaneous real stems
  (content work); Health Connect stays deferred-heavy.
- [x] Check-in ritual reward — 2026-07-04: saving a mood check-in now offers
  "A tiny reward — seal it with a 1-minute calm game" (routes to Games; offered,
  never forced). The proactive ritual itself was already the Home hero + daily
  reminder.
- [x] Prompt registry — 2026-07-07: versioned, admin-editable LLM prompts.
  `prompt_templates` (Alembic `f61b3d8e4c92`; immutable versions per name, one
  active) + `services/prompts.py` (modules register code defaults at import;
  call sites read `await prompts.get(name)` — active row overrides, any miss or
  DB error falls back to the default so the LLM path can't break). All four
  prompts wired: `agentic_plan`, `safety_classifier`, `assessment_topics`,
  `oracle_system` (the Oracle node re-reads per turn — edits apply without a
  graph rebuild). Admin: `/admin/prompts` (list/save/activate/revert) + a
  "Prompts" dashboard tab (edit → new version, rollback, revert to code
  default). Prompt changes reach production without a deploy.
- [ ] Content depth + clinical credibility (SHIP_READINESS.md "honest gaps") —
  content depth materially advanced 2026-07-07 (narrated-audio pipeline above:
  real per-track narration on all three clients); still open: a larger authored
  catalogue, sound mixing stems, and the clinical-credibility package (named
  advisor + efficacy citations — also investor item below).
- [x] `mcp.cerebrozen.in` — dropped 2026-07-04 (dangling subdomain removed from
  the Caddyfile comment; owner: delete the DNS record).
- [x] CSP — 2026-07-04: pragmatic policy in the shared Caddy snippet (blocks
  remote scripts/objects/frames/images, pins connect-src to our origins;
  'unsafe-inline' script/style stayed for Next hydration). Superseded 2026-07-07
  by the per-app nonce middleware (next item).
- [x] CSP nonce upgrade — 2026-07-07: per-app `middleware.ts` (hand-duplicated
  across apps/web+admin+app like the token blocks) issues a per-request script
  NONCE — `script-src 'self' 'nonce-…'`, no 'unsafe-inline' scripts (styles keep
  it — Next injects inline styles); `worker-src 'self'` pinned so /sw.js can't
  break; `connect-src` derives from `NEXT_PUBLIC_API_URL` (dev/e2e/prod). Root
  layouts force dynamic rendering (prerendered HTML can't carry a fresh nonce;
  nothing used static output — the landing trades static optimization away
  deliberately). `next dev` keeps a relaxed policy (react-refresh needs eval).
  Caddy: CSP removed from the shared snippet (apps' headers pass through);
  the API block gets `default-src 'none'` defense-in-depth. Also fixed:
  apps/app Dockerfile now copies `public/` (the Web Push sw.js 404'd in
  container builds). Verified: e2e green against production builds with
  Chromium ENFORCING the nonce policy; headers + nonce-attr match curl-checked.

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

## Open after the 2026-07-31 module-audit run

Everything the audits found is fixed and merged; these are the items deliberately
left, each with the reason it was left rather than done.

- [ ] **iOS catch-up** — iOS still reads `today_guide` only, so it has no journey path;
  the backend already sends `guides`. Parked by the user: Android leads, iOS follows once
  Android is finished. First item in the iOS queue.
- [ ] **Content art needs real imagery** — `artVariant` now varies composition, anchor and
  gradient axis within a kind (verified), but at 48dp it is incremental. Genuinely
  distinctive art needs commissioned illustration; that is an asset/budget decision, not
  a code change.
- [ ] **Two DPDP consent hints describe the default, not the category** — "Voice storage ·
  Off by default", "Model training · Separate opt-in only". True, but they say nothing
  about what the data is for, and they are stale once someone switches one on. The fix is
  26 strings inside a hand-shipped 13-language notice — legally-operative text that needs a
  translator, not a coder.
- [ ] **Goal `e484cbe9` on the demo account** — a mis-tap during the Goals audit moved it
  from a resolved state to `active`. The pre-audit read proves it was not active before but
  not which resolved state it held, so it was flagged rather than guessed at. One PATCH if
  anyone knows.
- [ ] **`services/engine/.env` in the working tree** — gitignored (never entered history),
  but if it holds live keys the "rotate anything exposed" rule may apply. Whoever put that
  checkout there should decide.

Verification: backend **177 passed, 95% coverage** (in-container, live Postgres); web +
admin `tsc --noEmit` clean; iOS `xcodebuild build` succeeded with the new entitlement.
