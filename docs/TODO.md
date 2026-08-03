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
- [ ] **Licensed media for the catalogue** (2026-07-13). The keyed media catalogue
  (`GET /media/catalog` + `POST /admin/media/{id}/upload`) ships with every key
  seeded and **every `url` empty** — the app is fully audible on its bundled loops
  and synthesized tones, and each upload is a pure upgrade with no app release.
  What's missing is the audio/video itself. Needed, all **first-party or licensed**:
  - `scene.night_lake` / `scene.dawn` — no video ships at all today; clients render
    the generative aurora instead. These are the only two keys with *no* fallback of
    their own kind (there is no such thing as a synthesized video).
  - `breathe.inhale` / `.hold` / `.exhale` — recorded cues would beat the synth glide.
  - `game.*`, `chime.timer_bell` — optional; the synthesized tones are good.
  - `ambience.*` — optional; the four bundled loops already ship.
  ⚠️ **Do not source these from `calm/`.** That directory is a competitive teardown of
  Calm's shipped APK (it is git-ignored for exactly this reason, and
  `calm/extracted/TEARDOWN_NOTES.md` says so) — its 51 breathe `.ogg` files and
  `jasper_lake.mp4` are Calm's copyrighted assets. Shipping them is infringement and a
  store-takedown risk. Use it as a spec (phase timings, how many cues), never as a
  source of bytes.

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
- [x] **iOS parity for the redesign** — DONE 2026-07-24 → 2026-07-28 across Waves A–D
  (`docs/IOS_PARITY.md`): Toolkit merge, one breathe engine, presence framing, onboarding
  10 → 8, Sleep CBT-I, safety/credibility/consent, the WCAG contrast gate and finally the
  Dawn/Night dual theme. Every item is **static-verified only** (Windows host) — the
  standing owner action is one macOS `xcodebuild test` + a two-theme screenshot pass.
  Two things stay open and both need a Mac: item 5 (back-to-back `PlayerView` audio
  overlap — a listen test) and the one-time `CereBroTests` unit-test target. One design
  gap is recorded deliberately: **the Sleep tab does not force Night on iOS** (SwiftUI
  can't scope global tokens to a subtree the way Compose snapshot state and CSS variables
  do — the proper fix is an Environment-palette refactor; rationale + cost in
  IOS_PARITY.md "Deliberate divergences").
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

### `main` ⟵ `origin/main` merge (2026-08-02) — the forked-main reconciliation
`main` had **forked**. A `git fetch` reported `origin/v1` deleted and `origin/main`
force-updated; the two lines shared no history after `5ef7416` (13 Jul). Local carried 27
commits (13–29 Jul, author `pawancerebro`) — the web/iOS parity waves, Dawn on both, Android
i18n, the Oracle audit, interventions, the guided routines. `origin/main` carried 72 (30–31
Jul, 70 authored `Pawan Kumar <ohgrtai@gmail.com>` — the same owner on a second identity,
plus 2 by Abhimanyu Kumar) — goals & habits, safety plan, editable memory, recommendations,
the claims gate, Stripe hardening, the free-tier cap, and a 16-screen module audit run on
hardware. **Neither contained the other**, so nothing here was a fast-forward.

Rule applied: **remote wins on defects found on hardware** (this host cannot reproduce them),
**local wins on documented cross-stack contracts**, keep-both wherever additive.
- **Stripe → remote.** The merged `User` has `stripe_customer_id`, so local's
  subscription-search lookup was obsolete; the portal now 409s (a state) instead of 502ing (a
  failure) when there is no customer. Three local tests were rewritten to the kept behaviour
  and a 409 case added.
- **Web Dawn → remote's architecture, local's scoping.** Took the `--dawn-*` scale (values
  declared once, hooks only map), then grafted back `.theme-night` — six pages depend on it
  and remote had no equivalent, so Sleep, `/crisis` and the signed-out funnel would have gone
  light. Also restored the `.cursor` reduce-motion gate remote had lost, and the guided-imagery
  CSS; dropped orphaned `.live-dot`.
  The graft was subtly wrong at first and **only `theme.spec.ts` caught it**: folding
  `.theme-night` into remote's `.onb-root, .authwrap` rule inherited a block that paints from
  `--panel-*` and never redeclares `--night`. The funnel containers don't need it; a
  `.theme-night` *section* wraps ordinary content whose cards and scrims resolve `--night`
  themselves — so Sleep re-themed its text to Night ink but kept the warm-paper ground. Night
  ink on Dawn paper, i.e. the bright-screen-at-bedtime regression the scope exists to prevent.
  `.theme-night` now re-scopes the ground as well.
- **Android theme → remote.** Its Dawn is the on-device fix for a raised card at **1.09:1**
  against its page; local's white-on-near-white had the same flaw. Night went back to the brand
  indigo `#100D2B`: local's navy re-theme never updated `colors.xml`, which remote's new
  `ThemeTokensTest` catches. The five constant brand marks `PremiumFrames.kt` needs were kept.
- **Breathe reset → local.** Remote's tests asserted a *symmetric* reset — the exact
  cross-client bug local fixed on 2026-07-29 (ARCHITECTURE contract: 4 in / 6 out). The
  implementation was right and the two `twoMinutesReached` tests had stale arithmetic; fixed.
- **Onboarding → remote** (removes the "Private previews" chip that silently disabled
  reminders), but **web onboarding → local**: remote still shipped the fake `first_plan`
  preview iOS and Android had already dropped.
- **Talk / Today → remote** (device-audited: pinned composer, free-limit card, Home rhythm,
  and the insights teaser that closes the Android-parity item). **Sleep → welded**: remote's
  `fallback` dedup fix (the stimulus-control advice printed twice) *plus* local's wind-down
  ritual door, which remote lacked.
- **Six defects came from *clean* auto-merges, not conflicts**: duplicate onboarding
  step-tracking effects (double-counting the funnel), a doubled `onboarding_done`, a duplicated
  `openPortal`, duplicate imports, admin error states rendering `ApiError` objects as
  ReactNode, and an `ONBOARDING_STEPS` list whose 10 names indexed against an 8-step UI would
  have labelled step 4 `state_check` instead of `first_reset`.
- Alembic forked at `c93f2b7a5e18` (local +2, remote +8) → new empty merge revision
  `f4b7c2e9a815`; single head restored.
- Verified: backend **448 passed / 2 skipped, 96 %**; `apps/app` tsc + `next build` (23 routes)
  + `scripts/check-claims.mjs`; admin tsc + build; Android **`:app:check` green — 286 tests,
  lint clean, coverage 95.13 %** (added the missing `Api` endpoint tests for goals/habits,
  safety plan, recommendations and per-item memory to get there). **iOS remains uncompiled** —
  static-verified only, and now the strongest reason to run `xcodebuild test` on a Mac.
- Inherited, not caused: `apps/web` is byte-identical to `origin/main` and its `/icon` route
  fails to prerender (`next/og` "Invalid URL", font fetch) — confirm in CI, where the network
  is available.

### `main` ⟵ `v1` merge (2026-07-29) — two design eras reconciled
`main` had been a 13-July snapshot plus 7 commits of 10-July work; `v1` had run 26 commits
past it. The merge auto-resolved everything except 9 files / 15 hunks, which were **not**
mechanical — they were the two eras disagreeing. Resolution rule and the calls made:
- **Both kept** where additive: the `cz-*` motion system and Dawn/imagery CSS (globals.css),
  both cross-stack contract rows (ARCHITECTURE.md), main's guided-tour row *and* v1's
  Appearance picker, main's crisis-region card *and* v1's "Talk to a human" card, and main's
  state-tuned journal prompt now rendering **inside** v1's `theme-night` hero scope.
- **v1 wins on content**, because main still carried things v1 deliberately deleted in
  WEB_PARITY Wave A: the hardcoded "Recent conversations" list, the invented mood-line
  fallback `[3,4,3,4,3,4,4]`, and the best-streak "Day rhythm" headline. A careless
  keep-both here would have **resurrected fakes** the credibility bar removed. The
  `MILESTONES` ring went with the best-streak number it decorated.
- **iOS welded rather than picked**: `Photo` keeps main's layout-neutral base and
  `asset:` bundled-imagery support but v1's *constant-dark* `Brand` colours (Wave E rule —
  a stand-in for a photo must not turn light in Dawn); the splash keeps main's animated
  `NativeEffectIcon` with v1's AA-safe `lavText`; `HomeView` stays v1's de-densified
  version, since reinstating the quick-links grid and weekly teaser would undo IOS_PARITY #6.
- Three joins were **wrong on the first pass and caught by the gates**, not by eye: a
  regex ate a `=` run inside a CSS comment banner, keep-both duplicated three `<section>`
  opening tags in the account page (unbalanced JSX), and the reduce-motion media query lost
  its closing brace so it swallowed the new Dawn CSS (the `next build` failure that found it).
- Verified on the merged tree: `tsc` + `next build` clean, **e2e 15/15**, Android
  `:app:check` green at 96.19 %. **iOS remains uncompiled** — the three-way weld above is
  static-verified only, and is now the strongest reason to run `xcodebuild test` on a Mac.
- Debt found: `QuickLinksGrid` (HomeView.swift) is now defined but unreferenced — main's
  quick-links grid lost its call site to the de-densified Home. Harmless to compile; delete
  it or find it a home.

### iOS: the three guided routines (2026-07-29) — `Features/Tools/Rituals.swift`
**⚠ Static-verified only (Windows host)** — same caveat as every prior iOS wave. Full
detail in [IOS_PARITY.md](IOS_PARITY.md) "Wave E". All three clients now carry the same
routines, with the same three blocks deliberately absent and the same missing "nothing can
harm you".
- [x] Wind-down (Sleep tab), ritual builder + guided imagery (Toolkit → Settle), over one
  parameterized set of step views. The settle step reuses `BreathingPacer.Preset.reset`
  (already *in 4, out 6*) rather than inventing a fourth rhythm — which is exactly the
  divergence Android had to be corrected to match.
- [x] **Every auto-advancing timer is `-resetState`-gated**, the posture CLAUDE.md requires
  for animated/async features (an ungated one hangs the UITest suite). `RitualStore`'s two
  keys join the `-resetState` wipe list, so a saved ritual can't leak between runs and make
  the builder screenshots nondeterministic.
- [x] The runner is keyed by block (two writing steps in a row would otherwise share view
  identity and the second would inherit the first's text), and the imagery countdown is
  keyed on `paused` too, so pausing restarts the task with the new value rather than
  trusting a running closure to observe it.
- [x] New `CereBroUITests.testGuidedRoutines` walks all three with manual controls only,
  asserting the brain dump's privacy line renders *before* anything is written and that
  imagery's caution renders *before* the exercise starts.
- [x] New `CereBroTests/RitualsTest.swift` pins the pure seams Android pins in
  `ScreenLogicTest` — plus a test that the three rejected blocks stay rejected, so none can
  be reintroduced without reading why. Needs the same one-time unit-test-target add as
  `ContrastTest` (documented in both file headers).
- Owner: one macOS `xcodebuild test` pass + a look at the three new screens in both themes.

### Android: the three guided routines (2026-07-29) — `ui/screens/Rituals.kt`
The web routines ported to the primary client, same day: wind-down ritual (Sleep tab →
`winddown`), ritual builder (Toolkit → `ritual`) and guided imagery (Toolkit Settle →
`imagery`). Copy hand-synced with `apps/app`, including every deliberate departure from
the sibling build recorded there — the 4-7-8 rejection, the three dropped blocks
(4-7-8 / Disidentification / affirmations, the last on Wood et al. 2009), the cue-first
structure, and guided imagery's missing "nothing can harm you".
- [x] One parameterized set of step composables (writing · three good things · body scan ·
  paced breath · 5-4-3-2-1) drives all three screens; the words come from the caller,
  because the wind-down speaks to someone already in bed and the builder to someone at
  any hour. `groundSteps()` went `internal` so the 5-4-3-2-1 copy has one home.
- [x] The runner is **keyed by block** — the same reconciliation bug the web version had:
  two writing steps in a row otherwise inherit the first one's text.
- [x] **Found and fixed a real cross-client divergence.** iOS `BreathingPacer.Preset.reset`
  is *in 4, out 6*; Android's `Reset` was *in N, out N*. The same named "two-minute reset"
  — including the onboarding first breath — paced differently on the two phones, and
  Android's version dropped the one part of slow breathing with clear evidence (the
  longer exhale). `breathePhases` now exhales `RESET_EXHALE_EXTRA` seconds longer at every
  pace; the pinning test was updated deliberately and a second test pins iOS parity at the
  default pace.
- [x] Android's Toolkit grounding card had **no `WhyThisWorks`** — the one tool in the app
  with no source, where web's equivalent has always carried one. Added.
- [x] Ritual persistence is device-local (`RitualStore` on the same `Session` pref seam as
  the gratitude garden) and the screen says so — there is no server model for a ritual and
  inventing one to sync eight ids would be the wrong trade. Reads are **sanitized**:
  unknown ids (older/newer install, hand-edited pref) and duplicates are dropped, since a
  duplicate row's reorder arrows would fight over one index.
- [x] Every string went to `values/strings.xml` (~90 new keys, zero literals). Deliberately
  **not** translated in `values-hi`: these carry clinical framing and a safety caution —
  the same class the Hindi draft leaves to the pending clinical review, so they fall back
  to English by design.
- [x] **Emulator smoke found a real usability gap**: only the 40 dp switch toggled a block —
  tapping the block's name, which is what everyone tries, did nothing. The whole row is now
  `toggleable(role = Role.Switch)` (the posture the plan-step rows already had) with the
  switch cleared from the semantics tree, so a screen reader gets one control instead of
  two. Re-verified on device: row taps select, numbering and reorder arrows appear, the
  summary reads "2 steps · about 3 min".
- Verified: `:app:check` green — compile, unit tests (new pure-seam tests for
  `sanitizeRitual` / `moveBlock` / `ritualMinutes` / `nextPromptIndex` / `ritualProgress` /
  the `RitualStore` round-trip), lint, coverage gate **96.19 %** ≥ 95. **Emulator-smoked
  2026-07-29** (API-34, signed-in against the local dev API): wind-down walked all four
  steps to the breathing orb, the builder's cue chips / row toggles / ordering / summary,
  and guided imagery's caution card → running stage with its countdown.
- Open: iOS ports of all three (IOS_PARITY follow-up). The physical device on this host
  refused the install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` — the resident build was signed
  with another key, and uninstalling would have wiped its data), so the on-device pass ran
  on the API-34 emulator.

### Your ritual + guided imagery (2026-07-29) — `apps/app` `/games/ritual`, `/games/imagery`
Fifth and sixth adoptions from the sibling build (`RitualBuilderPage`,
`GuidedImageryPage`), closing that folder's open list. Web's Toolkit now covers all four
sections iOS/Android ship — Ground · Breathe · Reframe · **Settle** — plus a door to a
routine you assemble yourself.
- [x] **Three of the reference's eight ritual blocks did not survive.** 4-7-8 breathing
  (rejected once already when the wind-down landed — a popularised ratio without direct
  evidence, and letting it back in through a side door defeats the point);
  **Disidentification** (Psychosynthesis/Assagioli — recorded as skipped on evidence
  grounds in the original assessment); and **affirmation reading** ("I am enough. I am
  capable."). The last is the one worth spelling out: generic positive self-statements
  *lower* mood and self-regard in people with low self-esteem (Wood, Perunovic & Lee,
  Psychological Science, 2009) — i.e. precisely the users a wellness app selects for.
  That makes it a small harm, not a taste call. Everything selectable is an exercise the
  app already ships with its own provenance; the builder invents no new exercise.
- [x] **The cue is the feature, and the reference has none.** A nicer sequence changes
  nothing about whether it gets done; an if-then plan attached to something already in
  the day roughly doubles follow-through (Gollwitzer & Sheeran, 2006, ~94 studies). So
  "After I ___" leads the page, the plan sentence reads back, and the finish card repeats
  the cue instead of awarding a trophy (F5: notable moments, not every rep).
- [x] Honest about what is *not* built: **no reminder**. There is no web scheduler here,
  and promising a nudge we can't send would be a fake — the screen says the cue is the
  reminder, which is also how the mechanism works. The ritual saves to **localStorage
  only** and says so ("not synced to your account").
- [x] **Guided imagery: the absolute reassurance is gone.** The reference's sixth slide
  reads "You are safe here. Nothing can harm you." Safe-place imagery is exactly the
  exercise where that promise can break — for someone carrying trauma, going looking for
  a calm interior place is a known route to intrusive material instead, and being told
  "nothing can harm you" at that moment reads as the app being wrong about you. The
  mechanism it reached for is kept ("nothing here needs anything from you"), and a
  caution on the way **in** (not after something goes wrong) says stopping is a normal
  outcome and points at 5-4-3-2-1 grounding, which works in the other direction.
- [x] New `components/RitualSteps.tsx` — paced breath, prompt sequence, 5-4-3-2-1,
  writing step, three good things — shared by the wind-down ritual, the Toolkit's
  grounding card and the builder. The 5-4-3-2-1 copy is hand-synced with Android
  `strings.xml ground_step*`, so a second copy was a second thing to forget.
- [x] Two real bugs fixed on the way, both invisible in the reference because it never
  hits them: the runner is **keyed by block** (two writing steps in a row otherwise
  reconcile to the same component and the second inherits the first's text — for a brain
  dump that is a privacy-shaped bug, verified in-browser before/after), and the paced
  breath now derives phase+round from **one counter** instead of bumping a round counter
  inside a state updater (impure updaters run twice under StrictMode, so the dev breath
  count ran at double speed). The imagery countdown was restructured the same way.
- [x] `.imagery-stage` is constant-dark in both themes, the hero/media-art rule — with an
  **opaque** base layer, because a translucent one lets the warm-white Dawn page through
  and washes out the dusk and its cream text with it (caught in a Dawn screenshot).
  Reduce-motion gates the drifting glows and the line fade.
- [x] Fixed en route: the Toolkit page carried **two buttons labelled "Start"** (the box
  breather and Thought Sort, which landed 2026-07-28 without an e2e run). A real
  screen-reader ambiguity as well as a strict-mode locator failure — Thought Sort's CTA is
  now "Start sorting".
- Verified: `next build` clean (types + lint), a real browser walk of both screens in
  Dawn **and** Night — cue → reorder → save → reload-restore → run → finish, and the
  imagery timer, skip, and close — and the **full e2e suite 15/15** in the Docker stack,
  including the new ritual-builder and guided-imagery walk in `app.spec.ts`.
- Open: iOS/Android ports of both. `CustomRitualsPage` (the sibling's server-backed
  ritual CRUD) stays unadopted — it needs a backend model, and a browser-local ritual is
  the honest version until there's a reason to sync one.

### Thought Sort → the Toolkit's Reframe section (2026-07-28) — `apps/app` `/games`
Fourth adoption from the sibling build, and the only one of its 18 games that teaches
something: spotting the named cognitive distortions (all-or-nothing, catastrophising,
"should" statements, labelling) is standard cognitive-restructuring psychoeducation.
Web's Toolkit now covers Breathe · Ground · Reframe, matching three of the four sections
iOS/Android ship.
- [x] Three things deliberately dropped on the way in:
  - **The efficacy claim.** The reference scores a "Thought awareness: 87 %" and
    congratulates "Perfect cognitive awareness!". A ten-item quiz over pre-written
    sentences measures no such faculty, and that is precisely the claim class behind the
    2016 Lumosity FTC settlement. The summary now reports the count and explicitly says
    the count isn't the point.
  - **The reward loop** (trophies, praise ladder) — conflicts with F5, celebrate notable
    moments rather than every rep.
  - **The word "game"** — these are example thoughts about self-criticism.
- [x] The thought bank was rewritten so each "why" names the actual distortion rather
  than offering encouragement, and carries a real `WhyThisWorks` (Beck). **Nothing the
  user has written is ever categorised for them** — only generic examples.
- Not ported: **Cloud Drift** and **Zen Sand**. Both are calm-play canvases that would
  duplicate the Zen Ripples already on iOS/Android, add no teaching value to a web
  Toolkit that deliberately says "more lives in the apps", and — being purely visual —
  could not be verified from this host. They belong on iOS/Android's Settle section,
  on a device. The remaining 15 games stay rejected on the credibility grounds recorded
  in the adoption assessment above.

### Wind-down ritual (2026-07-28) — `apps/app` `/sleep/ritual`
Third adoption from the sibling build (its `SleepRitualPage`). Four guided steps —
empty your head → three good things → body scan → settle the breath — reachable from
the Sleep tab's "Better nights, gently" section, so the CBT-I advice already on that
page has a guided version instead of being something to remember at 1am.
- [x] Two deliberate changes on the way in:
  - The reference ends on **4-7-8 breathing**; that exact ratio is a popularised pattern
    without much direct evidence, and every other exercise here carries a citation. The
    final step reuses the **in-4 / out-6** pattern the iOS/Android breathe engines
    already ship — a longer exhale than inhale is the part with real vagal-tone evidence
    — rather than adding a fourth, unevidenced ratio to the app's vocabulary.
  - The brain dump **never leaves the device** unless the user explicitly taps "Save to
    journal", and the screen says so. "Write down everything on your mind" right before
    bed invites the most unguarded writing a user will do all day; the reference
    discards it silently, which is fine behaviour but silent about it.
- [x] Every step carries a real `WhyThisWorks` source (Scullin 2018 for the brain dump,
  Seligman 2005 for three good things, CBT-I relaxation for the body scan). Gratitude
  and the body scan are both **skippable** — a night where only one good thing comes to
  mind is exactly the night not to be blocked by a form.
- [x] New `.onb-breathe-orb.slow-out` CSS so the orb's 3.8 s transition doesn't finish
  early and sit still through a 6 s exhale. Reduce-motion already handled by the
  existing orb rule. `tsc` clean.
- Open: iOS/Android ports. (RitualBuilder + guided imagery from the same folder landed
  2026-07-29 — see above; the step runners this page used were extracted to
  `components/RitualSteps.tsx` and shared with them, copy unchanged.)

### Interventions: recommend with a visible rationale (2026-07-28)
Second adoption from the `workspace/cerebro` sibling build. The app already nudged; it
never said **what it noticed**. Every offer now carries a plain-language reason computed
from the user's own logged counts, frozen at fire time.
- [x] `intervention_recommendations` (Alembic `e8a5b3d1c742`) + `services/interventions.py`
  + `/interventions` router (`active` / history / accept / dismiss / complete).
  Evaluation is **lazy** — no background job invents suggestions between visits.
- [x] Five code-defined rules over signals we actually hold: `human_support` (unresolved
  crisis flag in the last day → a real person, not a breathing exercise), `rough_sleep`
  (→ the Sleep Reset program), `irregular_bedtime` (noon-anchored spread, the same math
  the iOS/Android "Your rhythm" cards use), `stress_spike`, `low_mood_run`. First match
  by priority wins; **one open offer at a time** (a stack of suggestions is noise).
- [x] **Consent gates the inputs.** Mood rules need `mood_history`, sleep rules need
  `sleep_history`, and the signal fields are `None` rather than `0` when a category is
  off — so a rule can distinguish "no data" from "data says zero" and stay silent rather
  than firing on an absence it isn't allowed to see. Crisis is never consent-gated.
- [x] Mood rules count **days, not entries**: five check-ins in one hard afternoon is one
  day, not a week-long pattern.
- [x] `reason` and the action are frozen at fire time, so later rule edits don't rewrite
  what a user was actually shown; `state_snapshot` holds the counts behind the sentence
  (numbers only, never journal/chat text) so the rationale can be checked, not trusted.
- [x] Dismissing starts a 72 h per-rule cooldown — a suggestion that bounces back the
  moment it's waved away is the nagging pattern the OECD paywall checklist already
  forbids elsewhere.
- [x] **Deliberately absent: any "you haven't checked in for N days" rule.** That is the
  loss/pressure framing REDESIGN removed when streaks became presence framing, and a test
  pins the absence so it can't be added back by accident.
- [x] `apps/app` renders the card above the Home check-in (saying what was noticed before
  asking for more data). iOS/Android surfaces remain open.
- Verified in-container: **330 passed / 2 skipped, coverage 95.65 %** (gate 95);
  `apps/app` `tsc` clean; migration applied to a fresh DB.

### Oracle ops: agent audit trail + pending confirmations (2026-07-28)
Adapted from the `workspace/cerebro` sibling build's **Oracle Studio** admin hub — see
the assessment note under "Open — code/product work" for what was deliberately NOT taken.
Closes a real blind spot: the Oracle *writes user data* (mood, journal, sleep) behind an
`interrupt()` confirmation, and nothing recorded which tools ran, which writes were
approved, or which confirmations were stuck.
- [x] `oracle_tool_calls` table (Alembic `d7f4a2c9e631`, verified by applying the full
  chain to a fresh DB) + `services/oracle_audit.py`. Read tools record `decision="auto"`;
  write tools `open_pending` **before** `interrupt()` suspends the graph and resolve to
  `approved`/`declined` on resume.
- [x] `open_pending` is idempotent **by necessity** — LangGraph re-executes an interrupted
  node from the top when it resumes, so everything before `interrupt()` runs a second
  time; without the guard every confirmation left an orphan pending row that nothing
  resolved. Pinned by a test that replays it three times.
- [x] **Argument names only, never values.** A journal body or mood note copied into an
  audit row would be a second copy of the user's most sensitive content, sitting outside
  the consent flags governing the original, surviving a journal deletion, and needing
  separate DPDP export/erasure. Tested, including that values don't leak through the API.
- [x] Auditing never raises into a tool — observability must not fail a user's approved
  write; a missing pending row logs and returns.
- [x] `GET /admin/oracle/{status,pending,audit}` + an admin **Oracle** tab. `status.
  checkpointer` (`postgres`|`memory`|`none`) surfaces the MemorySaver fallback that was
  previously visible only in a boot log line — a production worker silently running
  in-process (paused confirmations dying on restart, not crossing workers) looked
  identical to a healthy one. The tab warns explicitly on `memory`.
- [x] Audit rows carry `ondelete=CASCADE`; a test asserts `DELETE /users/me` takes the
  agent's trail with it.
- Verified in-container: **306 passed / 2 skipped, coverage 95.45 %** (gate 95, was
  95.34); admin `tsc` clean; migration applied to a fresh DB and the table/indexes/FK
  inspected.

### iOS Dawn/Night dual theme (2026-07-28) — IOS_PARITY.md item 16, closing the backport
**⚠ Static-verified only (Windows host).** Contrast is host-independent math and is
gated by test; *layout* in Dawn is not — OWNER: two-theme screenshot pass on macOS.
- [x] New `DesignSystem/AppTheme.swift`: `ThemeMode` (system/night/dawn) persisted as
  `theme_mode` in the same vocabulary Android's `prefValue()` and web's `data-theme`
  use, plus pure `themeMode(fromPref:)` / `resolveIsNight(mode:systemDark:forceNight:)`
  seams that the test suite gates without rendering anything.
- [x] `Theme.Palette` / `Stroke` / `Gradient` members became computed `static var`s
  resolving a `ThemeSnapshot` global, so **no screen changed** — every screen still
  reads `Theme.Palette.…`. RootView re-keys `.id(theme.generation)` when the resolved
  theme actually flips, which is how SwiftUI is told to re-read global tokens (it has
  no equivalent of Compose snapshot state or CSS variable scoping). `generation` only
  moves when the *outcome* changes, so an input change that doesn't flip costs nothing.
- [x] Dawn values hand-synced with the web app's `[data-theme="dawn"]` block (WEB_PARITY
  Wave E) so phone and app.cerebrozen.in agree; the four roles web has no token for
  (cyan/mint/rose/danger) are the same hues darkened until each cleared AA. Night was
  **not touched** — that was the point of landing item 17 first.
- [x] Surfaces swept: Dawn paints solid fills where Night paints white-alpha glass (a
  white veil over warm white is invisible), veils/hairlines invert to ink, the aurora
  dims through one multiplier, and the primary CTA deepens to a lavender pill with a
  white label rather than staying a cream button with nothing to sit against. Paint that
  sits on **constant-dark art** (hero photos and their scrims, the brand orb, the splash)
  was deliberately left alone — same rule web's Wave E applied to heroes.
- [x] You → Appearance picker (`AppearanceView`), honest about the two surfaces the
  preference doesn't reach (splash + signed-out funnel, both bespoke night art).
- [x] `ContrastTest` now gates BOTH palettes — 0 failures across 105 role×surface pairs,
  tightest 4.51:1 (Dawn mint on the darkest page paint, the same value Android's own
  gate independently measured for that hex) — plus the theme truth table and a
  byte-identical Night pin that fails the build if a future Dawn tweak drifts Night.
- [x] Under `-resetState` the theme is **pinned Night**, same gating posture as the
  splash and the audio engine: a simulator booted in Light appearance would otherwise
  flip to Dawn the instant onboarding finished, re-keying the root view mid-test and
  re-rendering every marketing screenshot in the wrong theme.
- Deliberate divergence recorded: **the Sleep tab does not force Night on iOS** (Android
  and web both pin it). Full rationale and the proper fix — an Environment-palette
  refactor — in IOS_PARITY.md; it carries a real wellness cost, not just a cosmetic one.

### iOS parity backport, Wave A (2026-07-24) — IOS_PARITY.md items 9,10,13,11,4,2,22,23
**⚠ Static-verified only (Windows host) — OWNER: run `xcodebuild test` on macOS
before shipping; UITest funnel + games-hub assertions were checked by hand.**
- [x] Tele-MANAS 14416 now LEADS the iOS IN crisis directory (was 112+KIRAN only —
  iOS had no Tele-MANAS anywhere); voice line only per the Android W25 dead-target
  finding; mirrors backend `services/crisis.py`.
- [x] Fake "Coach booking" flow deleted (invented time slots — App Store 2.1 risk);
  HumanSupportView now ships real tappable lines (Tele-MANAS / iCall 9152987821 /
  findahelpline.com/in) + an honest roadmap card (new `SupportLinkRow`).
- [x] Onboarding ConsentScreen renders all 6 DPDP categories (model_training was
  silently defaulted) AND no longer wipes the user's consent taps on every
  appearance (IOS_PARITY #13 bug — reset now runs once per install).
- [x] Credibility layer: `WhyThisWorks` footers (breathing, grounding, CBT reframe,
  TIPP, gratitude garden, Programs) + "How CereBro is built" honesty cards in
  PrivacyPolicyView — copy hand-synced with Android/web.
- [x] IA: onegoodthing/intention → Journal quick-prompt chips; widget kinds remapped
  to `JournalEntryView(prompt:)` (kinds stay routable — cross-stack contract);
  memorymatch/slidingpuzzle/bubblewrap/colorbreathing killed (REDESIGN §2.2).
- [x] F5 posture: celebrations now fire on FIRST completion only per tool
  (`CelebrationGate`, `-resetState`-wiped); Home post-check-in "A tiny reward ·
  Seal it with a calm game" reframed to a quiet "Settle for a minute" breathe row.
- [x] Paywall: "Manage or cancel anytime" link to Apple's subscriptions page (OECD
  cancel-path indicator; iOS StoreKit is live code).

### iOS parity backport, Wave B (2026-07-24) — IOS_PARITY.md items 1,3,6,7,8,15
**⚠ Static-verified only — same macOS `xcodebuild test` caveat as Wave A.**
- [x] One breathing engine: `BreathingPacer.Preset` (box / color 4-2-6 / reset 4-6
  no-holds); onboarding FirstReset uses `.reset`; Toolkit offers all three.
- [x] `GamesHubView` → `ToolkitView`: Ground · Breathe · Reframe · Settle sections
  over the surviving tools + the Tele-MANAS crisis footer (≤2-tap rule).
- [x] Home de-densified (~10 → 6 blocks): hero → check-in (hidden when the hero IS
  the mood ask) → plan → rail → presence card → collapsed recent check-ins →
  quiet Toolkit row. Cut: sleep row (Sleep tab owns it), baseline ask (moved to
  Insights, where its payoff renders), Programs row (standing door added to the
  Sleep tab, Android sleep_programs_nav parity; enrolled card still links).
- [x] Presence framing: "N days you showed up this week" headline, no "Begin your
  streak" / "Best N days" pressure copy; streak computation untouched (contract).
- [x] Crisis doors: You-header Support button, Journal "If today feels heavy" row.
- [x] Talk: "Try together" rail (CBT reframe / box breathing / grounding) in the
  empty state; Ground chip added mid-conversation + in the voice session.
- [x] UITests updated by hand: hero "Check in" path, Toolkit rename + crisis-footer
  assertion, Programs reached via Sleep.

### iOS parity backport, Wave C (2026-07-24) — IOS_PARITY.md items 14, 12
**⚠ Static-verified only — same macOS `xcodebuild test` caveat.** Item 5
(back-to-back PlayerView audio overlap) is a device listen test — still open.
- [x] Sleep "track" → "improve": "Improve your sleep, night by night" eyebrow;
  "Your rhythm" card (≥3 nights) with noon-anchored bedtime-spread math ported
  from Android's unit-tested helpers; "Bed is for sleep" + "Same wake time"
  stimulus-control cards + the CBT-I provenance footer.
- [x] Onboarding 10 → 8: fake `FirstPlanScreen` deleted (static Dummy steps posing
  as personalization); 18+ attest + underage exit merged into `DisclosureScreen`
  (confirmAge/syncAgeConfirmation preserved, Continue stays gated); `stepNames`
  → 8 canonical names (`age_gate`/`first_plan` never fire — backend list
  unchanged); progress fractions refit; all four funnel UITests re-walked.

### Android Hindi i18n plumbing, pass 1 (2026-07-25)
The display-copy half of the "pure functions still returning English" ledger
(see the Phase-3 item above) — verified: `:app:check` green, coverage gate
96.19% ≥ 95%.
- [x] Res-driven now: `greetingResFor`/`milestoneFor`/`railKindFor` (Today),
  `hoursMinutes` + `minutesLabel`/`spreadLabelText` + `isVariedRhythm` (Sleep),
  `BreathKind` phase model + `phaseLabelRes` (Breathe engine — cues/haptics key
  off the enum, not English labels), `talkTranscript` localized prefixes,
  `Reminders` channel/notification copy, `SoundscapeMixer.Layer.nameRes`.
  New strings in values/ + values-hi/ (hi = DRAFT, same review posture as W16).
- [x] Pass 2 (2026-07-25): the label/value splits that touch persisted state —
  Today `MOODS` gains `labelRes` (API name/note stay English contract values;
  `moodLabelRes` also localizes known names in Recent check-ins), Settings
  `COMPANIONS` → `CompanionOption(value, labelRes, detailRes)` (server value
  unchanged; You header/rows display-localize via `companionLabelRes`),
  onboarding `STATE_OPTIONS` keyed by stable ids (saver stores the key, not the
  English label), `LANGUAGES`/`NOTIFY` → `PickOption(value, labelRes)` (reminder
  hour keys off "morning"/"evening" ids, not `startsWith("Morning")`), `Funnel`
  takes an explicit `progress:` fraction (was matching English eyebrow copy).
  ZERO `i18n: pending` markers remain. `:app:check` green, gate 96.19%.
  **Also found + fixed en route: the cc7cbd4 "ui" commit had silently reverted
  the private-by-default consent fix — mood_history/ai_memory were pre-ticked
  ON again in onboarding (restored all-off, matching iOS/web + the decided
  DPDP posture).** Emulator-smoked 2026-07-25 (API-34 AVD, fresh install):
  EN funnel walk — consent step shows ALL SIX toggles OFF (regression fix
  verified on device); per-app locale `hi` walk — language chips show native
  names, all six state options + notify options render the Hindi drafts,
  "शाम 7 बजे" correctly pre-selected from the stable "evening" id, progress
  bar shows real fractions in Hindi (the old eyebrow-matching would have
  pinned 100%). Remaining before Hindi ship: the clinical/linguistic review
  (owner) only.

### Analytics consent-gate parity, iOS + web (2026-07-24)
The owner's 2026-07-13 decision ("no telemetry before consent", made for
Android) applied cross-client — closing WEB_PARITY item 14 and the parked
iOS note in IOS_PARITY "decisions taken":
- [x] iOS: `Analytics.track` now no-ops until `analytics_unlocked` — set when the
  onboarding Consent step is passed (`Analytics.unlock()` on its Continue) or a
  session authenticates (restore + finishConnect); pre-consent funnel steps are
  deliberately uncounted; flag wiped under `-resetState`. (⚠ static-verified.)
- [x] Web (`apps/app`): new `lib/analytics.ts` — anon install id, no auth header,
  allowlisted names, `source: "app"`, same consent gate (unlock on Consent pass /
  sign-in / live session); onboarding_step fires per step with the canonical
  8-step names (`age_gate`/`first_plan` never fire — backend list unchanged),
  onboarding_done, paywall_view + paywall_cta on /account; "Anonymous usage
  stats" opt-out toggle (iOS/Android parity). The admin funnel now sees web.

### Web parity backport, Waves A–D (2026-07-24) — WEB_PARITY.md landed
The 2026-07-12 audit's landing order executed on `apps/app` (+ one backend
addition), e2e spec updated in the same commits; tsc + backend suite green.
- [x] **Wave A — fakes killed (B1–B8+3)**: hardcoded "Recent conversations",
  fabricated "Gentle patterns"/stat tiles (now computed from real check-ins or
  honestly empty; patterns from `/insights/patterns`), invented mood-line
  fallbacks, journal fabrications, dead search/bell chrome, fake "live session"
  CTA, hardcoded "Free plan" chip (now `subscription_tier`), best-streak
  headline (now days-present-this-week).
- [x] **Wave B — safety**: public static `/crisis` page (works signed-out, dead-API
  safe; Tele-MANAS 14416 → 112 → KIRAN → findahelpline, dialler-only `tel:`
  links, NO WhatsApp row per Android W25); persistent sidebar "Support" door;
  chat/journal crisis banners lead with Tele-MANAS, numbers tappable; account
  "Talk to a human" card (Tele-MANAS/iCall/directories).
- [x] **Wave C — credibility/consent**: onboarding consent renders all 6 DPDP
  categories (model_training added; old drafts deep-merge private-by-default);
  shared `WhyThisWorks` provenance footers; /games → "Toolkit / Small ways to
  steady" + real 5-4-3-2-1 grounding; account "How CereBro is built" honesty
  cards; `today_guide` on Programs + Home; chat "Try together" rail;
  WIDGET_LINKS extended (breathing/grounding→/games, one_good_thing/
  intention_set→/journal; kind names pinned to services/activities.py);
  journal prompts clickable + gratitude/intention quick-entry chips.
- [x] **Wave D — flagship**: Sleep "Your rhythm" card (noon-anchored bedtime
  spread — Android's unit-tested math ported) + stimulus-control education
  cards + improvement framing; onboarding 10 → 8 steps (fake FirstPlan killed,
  18+ attest merged into Disclosure, resume→consent renumbered);
  **`POST /billing/portal`** (backend: Stripe Billing-Portal session via
  subscription-metadata lookup, 503/502-honest, 6 new tests) + account
  "Manage or cancel subscription" row + sidebar upsell now free-tier-only
  (OECD nagging indicator); reduce-motion gate on the streaming caret +
  orphaned-CSS sweep.
- [x] **Wave E — Dawn/Night dual web theme** (WEB_PARITY item 17) — 2026-07-24:
  Dawn var overrides in `apps/app/globals.css` (values mirror Android's
  WCAG-verified `DawnPalette`, incl. AA-darkened accent inks) via
  `prefers-color-scheme: light` + a `data-theme` override; extension vars
  (`--card-soft/--line-soft/--well/--field/--tabbar`) promoted from the
  white-alpha literals (Night values byte-identical); heroes/media art pinned
  constant-dark (Android ContentArt rule) instead of the audit's class sweep —
  deliberate; `.theme-night` scope pins Sleep, onboarding, signin and /crisis
  to Night in every mode; Appearance picker (System/Night/Dawn) on /account
  with a nonce'd pre-paint script (no flash, works under the enforced CSP);
  `theme.spec.ts` e2e asserts Dawn-on-light, Night pinning, picker + reload
  persistence, with screenshots for the visual pass. admin/web stay
  Night-only (hand-duplicated globals — follow-up only if wanted). Web
  analytics (item 14) stays decision-gated.
### CI: the Android job was watching main break and saying nothing (2026-07-31)
The root cause behind that morning's broken `main`, fixed rather than just cleaned up
after. The `android` job already ran `testDebugUnitTest` + `assembleDebug`, so it
*did* fail on the stray `/sdfsdkjfk` in `Session.kt` — but it carried
`continue-on-error: true` from when `apps/android` was a scaffold, so the failure
was a non-blocking annotation and the pipeline stayed green. The flag's own comment
said "flip to blocking once it's built once green"; that condition had been met long
ago, and Android is now the lead client.
- [x] `continue-on-error` removed — the job that compiles the lead client is the one
  job that was allowed to fail. Verified: it is now the only `continue-on-error` in
  the file, and no job carries it.
- [x] Added `:app:lintVitalRelease` to the same step (release-blocking lint was
  running on nobody's machine but a developer's), and lint HTML is uploaded
  alongside the test reports on failure.
- [x] Switched `gradle` → `./gradlew`, dropping the separately pinned
  `gradle-version: 8.11.1`. The wrapper already pins 8.11.1, and two pins that can
  disagree is a drift waiting to happen; now CI runs exactly what everyone runs.
- Verified by running CI's exact command locally: `./gradlew :app:testDebugUnitTest
  :app:assembleDebug :app:lintVitalRelease --no-daemon --stacktrace` → BUILD
  SUCCESSFUL, 244 tests.
- [ ] **Unverified until the next push:** local is macOS + Android Studio's JBR, CI
  is Linux + Temurin 17. If the job has been failing on Linux for something
  platform-specific, this change is what will finally surface it — which is the
  point, but expect the first red to be informative rather than a regression.
- [x] **And the second breakage was invisible too.** The two-heads incident could
  never have failed CI: the suite builds its schema with `Base.metadata.create_all`
  (`init_db`), so pytest never executes a migration — a forked or broken Alembic
  history is simply not exercised, and green CI could ship an API that won't boot.
  The backend job now asserts a single head and runs `alembic upgrade head` against
  its own scratch database (`migrations_ci`, so the pytest path is untouched),
  proving the chain applies from empty rather than merely parsing.
  Verified by reproducing the failure: with `8c27b8990a90` moved aside, `alembic
  heads` reports **2** and the step fails; restored, it reports **1** and passes.
- [ ] Still worth doing and needs GitHub access: a **branch-protection rule** so
  these checks must pass before `main` accepts a push. CI going red does not
  currently stop anything from landing.

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

### Web 100-point improvement run (2026-08-03, autonomous waves)
The full list, per-item status, and what stays owner-blocked live in
**docs/WEB_IMPROVEMENTS.md** (31 shipped / 13 owner-blocked / 9 recorded
decisions / the rest scoped as "later"). Shipped highlights: trust surface
(/security + RFC 9116 security.txt, /refunds, /subprocessors, privacy
retention table, `.legal` prose styles the policy pages never had),
SoftwareApplication/Organization JSON-LD, www→apex redirect + asset caching,
LCP preload + content-visibility, app-shell a11y (skip link, aria-live chat,
focus-to-h1, notice `lang`), journal drafts + server-index search/tags, chat
retry, OTP cooldown, password guidance, **18+ attest on direct signup with
fresh accounts routed through the consent step** (they used to skip straight
to /home), PWA manifest + icons, admin memory-only token + waitlist
created_at/CSV (cross-stack), ESLint green + CI-gated on all three apps, a
CSP-floor gate (scripts/check-csp-sync.mjs), five new e2e specs, and
docs/WEB_STYLE.md. New copy rulebook lives there; hold future surfaces to it.

### Android world-class pass (2026-08-03, emulator walk on the fixed backend)
- [x] **Trends now tells the true reason it's empty.** With mood/sleep history
  consent OFF, the empty state said "check in and this fills in" — false, the
  server honours the flag. New consent-aware copy points at Privacy & memory;
  verified live on the emulator with the granted-nothing smoke account.
- [x] **TrendsScreen's "(pure, unit-tested)" claim is now true** — parseTrends /
  contiguousRuns / durationLabel / trendsEmptyBodyRes pinned in TrendsLogicTest
  (null summaries stay null, gaps split the line, unparseable dates don't
  bridge). Coverage 97.70% ≥ 96 gate.
- [x] **Two unreleased-app claims reworded (en + hi):** talk_ios_only said "lives
  in the iOS app" (nobody can download it) → "isn't in the Android app yet";
  premium_billing_note dropped "On iOS this is live via StoreKit".
- [x] **Cross-client crisis chain verified on-device:** "I have been thinking
  about hurting myself" (the derived form that used to slip the floor) → reply
  names no hotline, promises the platform's resources → sticky "You matter"
  card on Talk → Urgent support leads Tele-MANAS 14416, then 112, KIRAN,
  findahelpline, trusted-contact prompt.
- Checked, no action: reminder times are real on Android (reminderHourFor →
  Reminders.schedule — a local notification at the chosen hour, unlike web's
  discarded picker); Breath Loops patterns are all defensible (Box/Reset/
  Coherent/Triangle — no 4-7-8); the second-breathing-engine consolidation
  stays a decision item.

### Admin (apps/admin) world-class pass (2026-08-03, all ten tabs walked signed-in)
- [x] **The ops dashboard was indexable.** No robots metadata and no header at the
  proxy — added `robots: { index:false, follow:false }` to the layout and
  `X-Robots-Tag: noindex, nofollow` to the admin block in deploy/Caddyfile.
- Verified live, signed in against real data: all ten tabs render (Overview stats,
  Analytics cohorts/funnels, Users incl. detail panel contract, Content library,
  Media catalogue with honest fallback copy, versioned Prompts with the
  risk-prompt double-confirm, Oracle audit, Nudges, Safety, Waitlist). The
  safety-review privacy flow works end to end: excerpts hidden by default with
  char counts only, per-row server-audited reveal ("You opened this. The server
  logged it."), required resolution notes. The queue held the crisis test
  messages from today's web-app review — including two rows matched by the NEW
  "hurting myself" keyword-floor term, live proof of that fix.
- Correction to today's earlier finding: the Oracle path's crisis banner DID fire
  for the pre-fix message (LLM classifier caught it; the banner showed
  default-region lines and the check regexed for Tele-MANAS only). The keyword
  floor gap was still real — it is the only net when the LLM is off or
  under-flags — but the banner path was never broken.
- Noted, not changed: admin keeps its access token in localStorage (apps/app
  holds it in memory only) — simpler, weaker against XSS; the nonce CSP is the
  mitigation. Worth aligning if the dashboard ever grows content-injection
  surface. The single 1,709-line page.tsx split is already on the ledger.

### Web app (apps/app) world-class pass (2026-08-03, full funnel + chat walked live)
- [x] **Crisis keyword floor missed progressive forms — a real heavy message got no
  crisis resources.** "I've been thinking about hurting myself" sailed under the net:
  substring matching means "hurt myself" ≠ "hurting myself", "suicide" ≠ "suicidal".
  Floor now carries derived forms (killing/hurting/harming myself, suicidal, ending my
  life, wanting to die, wish I was dead…); pinned by
  test_safety_keyword_floor_catches_derived_forms.
- [x] **Both chat LLM paths told an India-region user to call a US hotline.** The
  /chat personas said nothing about hotlines and the Oracle prompt actively said
  "gently surface emergency resources" — so the model printed 1-800-273-TALK while
  the platform's own region-correct banner (Tele-MANAS-first) carried the right
  numbers. Both prompts now forbid naming hotlines; the platform attaches local
  resources itself. Verified live: banner up, no US numbers, nothing blocked.
- [x] **Onboarding FirstReset breathed 4-in/2-hold/4-out — fifth recurrence of the
  breathing-contract drift.** The cross-client Reset is 4-in/6-out, no hold
  (ARCHITECTURE contract row). Now 4/6 with the ritual's existing `slow-out` 5.8s
  transition class.
- [x] **Funnel offered reminder times it never honored.** "Morning 9 AM" / "Evening
  7 PM" both collapsed to `email_nudges=true`; delivery is a fixed 9 AM check-in +
  7 PM wind-down (services/nudges.py). Step is now honest on/off chips, fine print
  states the real schedule + the browser-notification path in Settings.
- [ ] Wiring a REAL per-user reminder hour is a cross-stack schema task (users
  column + nudges scheduling + all three clients' pickers) — decide before any
  client re-grows a time picker.
- [x] **"Lives in the iOS app" ×5 (chat ×3, sleep, toolkit)** — an app nobody can
  download yet, stated as shipping. All now "arrives with the mobile apps".
- [ ] **Elevated-risk chat replies get resources only on the Oracle path.** /chat
  appends hotlines for `crisis` only, while /oracle SSEs the banner for
  `elevated` too. Decide whether /chat's non-Oracle fallback should match
  (activities.route already surfaces a crisis suggestion chip for elevated).
- [ ] apps/app has no PWA manifest (sw.js is push-only) — "install to home screen"
  would make the web client feel native on Android before the store app lands.
- Verified working end-to-end in a real browser: full 8-step funnel (18+ gate,
  Hindi consent notice via language carry-through, all-six-consents-off default,
  signup), check-in → presence rail update, chat round-trip + suggested-activity
  widget, free-limit typed error path (code-reviewed), theme Night/Dawn persist,
  honest empty states on Home/Insights/Journal, Settings 13-language notice +
  export + typed DELETE, Tele-MANAS-first /crisis + /support.

### Landing (apps/web) world-class pass (2026-08-03, reviewed in a real browser)
- [x] **Waitlist could announce success on failure** — FIXED. `Waitlist.tsx` parsed the JSON
  of any response: a 429 (the endpoint rate-limits at 10/min per IP — one college NAT hits
  that) or a 5xx still said "You're in" while the email was never recorded. Now only 2xx
  celebrates; 429 gets its own gentle copy.
- [x] **~20 static cards each paid for `backdrop-filter: blur(8px)`** — FIXED. Every card,
  bento cell, space row and FAQ item forced its own raster layer to blur a smooth gradient
  (invisible by definition); scrolling visibly strained the renderer on a Windows machine
  during review. The sticky nav keeps its blur — content really scrolls under it.
- [x] **Dead `images.unsplash.com` remotePattern** in next.config.mjs — removed; no remote
  images exist and the CSP's `img-src 'self'` would block them anyway.
- [x] **`sync-tokens.mjs --check` false-failed on Windows checkouts** (CRLF vs the LF block
  it builds) — normalizes to LF before comparing now. CI behavior unchanged.
- [x] Hero banner alt text said "home, journal and sleep"; the render shows home + sleep.
- [ ] **The hero render contradicts the page it sits on.** `banner-hero.jpg` bakes in a
  "3-day streak" chip (and "Rest easy, Pawan") while the bento cell beside it promises
  "Presence, not streaks … nothing ever counts your misses." Needs a re-render from the
  brand kit with the presence ring instead of a streak chip — asset work, not code.
- [ ] **Scroll-driven `.reveal` can freeze mid-fade under renderer pressure.** Observed once
  (Windows Chrome, DevTools-protocol attached): the final "Be first to feel the calm" head
  held at opacity 0.59 even fully in view; clean loads complete correctly, so likely
  capture-tooling-induced — but if a real-user report ever mentions dimmed sections, drop
  `.reveal` from the last fold (waitlist + FAQ) first.

### From the 2026-08-03 deep review (after pulling the device-push/offline/games drop)
- [x] **Consent was private-by-default on every client and permissive on the server** — FIXED.
  Four model columns defaulted True and `consent_allows` treated a missing row as granted, so
  between signup and the end-of-onboarding PATCH (forever, for an abandoned onboarding) the
  server held grants nobody made — and insights/plans/chat-memory/interventions all read them.
  Now: every column False, missing row = nothing granted, `/chat`'s hand-rolled check routed
  through `consent_allows`, `ConsentSchema` response defaults aligned, suppress-pattern un-gated
  (a suppression narrows what the AI sees — gating it on `ai_memory` 403'd exactly the person
  reducing what we hold). A test pins that a fresh account has granted nothing; this default has
  regressed twice on Android already.
- [x] **Committed conflict markers broke the Android build on main** — FIXED. Six files carried
  literal `<<<<<<<` blocks from the 2026-08-03 "Resolve merge conflicts" commit; each resolved by
  intent (both nav-visibility layers kept with both their tests; boot effect drains outbox +
  registers push + warms media; Trends row kept; funnel constants stay single-valued).
- [x] **Mindful Games shipped the Lumosity claim vocabulary — third recurrence** — FIXED. Tags
  now describe the activity, never the faculty; `check-claims.mjs` gained a COGNITIVE_TRAINING
  ban group so it cannot return. The twelve games themselves are kept.
- [x] **FCM `INVALID_ARGUMENT` buried installs** — FIXED: DEAD only when FCM blames the token,
  else RETRY; a payload bug no longer deregisters every Android device silently.
- [x] **4-7-8 came back inside Breath Loops** — FIXED, fourth recurrence overall of a
  rejected-on-evidence pattern. Replaced with the cross-client Reset (in 4 / out 6 — the part
  of slow breathing with real vagal-tone evidence), and 12 rounds is exactly the 120 seconds
  the "two-minute reset" promises. History decode already skips unknown pattern names.
- [ ] **Breath Loops is a second breathing engine.** `ui/breathing/` (BreathPattern +
  BreathingStateMachine + ViewModel + history) duplicates `ui/screens/Breathe.kt`
  (`breathePhases`, the REDESIGN "one engine" consolidation). It adds real things the first
  lacks — round counting, session history, Coherent/Triangle pacings — but two pacing sources
  will drift (they already disagreed once, on 4-7-8). Fold the loop/history layer over
  `breathePhases`, or retire one. Decide before the next breathing change, not during it.
- [x] **Android Trends screen verified on the emulator against the live backend** (2026-08-03
  smoke): renders, honest "Nothing to chart yet" for a granted-nothing account even after a
  check-in wrote data — the consent gate working end to end. Follow-up nit: the empty-state copy
  says "Check in on Home … and this fills in", but when the real reason is mood_history OFF it
  should say so and point at Privacy & memory (client can read its own consent via
  `Api.consent()`; no backend change needed).
- [ ] **You/Toolkit render premium-dark over Dawn chrome.** With the funnel and every tab
  theme-following, the You screen's PremiumNavRow cards and the Toolkit's fixed dark gradient
  are now the two surfaces that stay dark on a light system — by design (premium glass) or by
  accident? Looked fine on the emulator walk, but it's a deliberate-or-not question for the
  owner with both themes side by side.
- [ ] **The offline guidance pack (BodyScan / CBT-I / MBCT / journeys / insight reel) carries
  clinical-adjacent copy added 2026-08-03** — English-only, and deliberately NOT in `values-hi`
  (same posture as the crisis/TIPP omit list). Wants the same clinical review pass as the rest
  before any Hindi ship; and the MBCT module naming ("MBCT", "body scan") should get a
  `WhyThisWorks` provenance footer like every other exercise. Currently none of the offline
  screens carries one.
- [x] **Product docs relocated out of `apps/android/`** (2026-08-03 structure pass): the three
  .md module/guide documents now live in `docs/` (`ANDROID_MODULES_EN.md`, `ANDROID_MODULES_HI.md`,
  `ANDROID_GUIDE_HI.md`); the four generated HTML/PDF artifacts were dropped from git (derivable
  from the .md, and recoverable from history). Same pass: README/CLAUDE.md no longer call the
  169-file lead client a "scaffold", and CI's Android job runs `:app:check` — the 96% coverage
  gate and full debug lint were previously enforced only on machines that chose to run them.

### Left by the 2026-08-02 forked-main merge
- [ ] **Two suggestion engines now ship side by side.** `/interventions` (rule-driven offers
  off logged signals — crisis/sleep/mood, one open offer, 72 h cooldown, frozen `reason`,
  rendered on Home) and `/recommendations` (practice suggestions off *pattern statements*,
  rendered on the Patterns dashboard, with admin accept/dismiss stats). Different triggers,
  different surfaces, entirely disjoint tables — so the merge did not have to choose, and
  deliberately didn't. But a user can now be offered something in two places by two systems
  with two rationales. Decide whether they unify (likeliest: keep the interventions engine's
  consent-gating/cooldown/audit and give it a pattern-derived rule source, with
  `practice_catalog` as the action vocabulary) or stay separate with clearer boundaries.
- [ ] **`/crisis` and `/support` are two public static pages doing the same job.** Both
  survived because both are linked from safety surfaces (5 pages → `/crisis`, 6 → `/support`)
  and a 404 on a crisis route is the last acceptable regression. `/support` is the factored
  one (`components/CrisisLines` + `lib/crisis`); the sidebar door and the chat/journal banners
  point at it. Fold `/crisis` into it and leave a redirect, rather than maintaining two.
- [ ] **Neither test stack is hermetic on a dev box** — both read the developer's real keys,
  so they exercise a different code path than CI *and* bill real API calls.
  - `pytest`: a populated `backend/.env` makes
    `test_habits::test_decompose_names_the_goal_even_without_an_llm_key` and
    `test_safety_plan::test_crisis_reply_is_unchanged_with_and_without_a_plan` take the
    live-LLM path and fail their own keyless assertions. Run with the keys blanked, or have
    `conftest` blank them under `TESTING=1` (preferred — the tests then match CI by default).
  - `docker-compose.e2e.yml`: the `api` service does `env_file: ./backend/.env` wholesale, so
    the e2e run logs real `POST https://api.openai.com/v1/chat/completions`. CLAUDE.md says
    hermetic tests run with blank keys; the compose file should pin `OPENAI_API_KEY: ""` (and
    the voice keys) in its `environment:` block, which overrides `env_file`.
- [ ] **iOS Dawn is now the odd one out, and it carries the bug the other two just fixed.**
  iOS `Theme.Dawn` was hand-synced to the *old* web Dawn: ground `0xFAFAFC`, resting card
  `0xFFFFFF` — a white card on a near-white ground, ≈**1.02:1**. That is the same flatness
  Android/web corrected on 2026-07-31 by moving the ground to warm paper (web `#f2eee5`,
  Android `#F5F2EC`) and letting shadow carry elevation. iOS `ContrastTest` passes and always
  will: it gates *text* contrast, and card-versus-ground separation is not a text pair, so no
  test catches this. Port the warm ground + a Dawn shadow tier, then re-run the two-theme
  screenshot pass. (Also worth settling while there: web `#f2eee5` and Android `#F5F2EC` are
  not the same warm paper — pick one and sync all three.)
- [ ] `rhythmPrinciple` / `spreadLabel` (SleepScreen) are now exercised only by
  `SleepInsightTest`; the screen branches on `isVariedRhythm` / `spreadLabelText`. Keep the
  pure twins (they pin the boundary and are the non-composable path) or collapse to one pair.


### Adopting from the `workspace/cerebro` sibling build (assessed 2026-07-28)
The owner's other, much larger Cerebro implementation (5 repos: api/web/admin/mobile/infra,
~120 API domains) sits beside this one and is a legitimate internal reference — unlike
`calm/`, which is a competitor teardown and must never be a source of bytes. Assessment:
- [x] **Oracle Studio** — NOT portable as code. It is a hub page over **8 endpoints**, of
  which cerebroSG backed exactly one (`/admin/prompts`), plus links to ~10 admin surfaces
  that don't exist here; it also assumes `@cerebro/ui` + TanStack + Tailwind against our
  hand-rolled single-page admin. What *was* worth taking — the tool-call audit, pending
  confirmations and an agent status band — shipped 2026-07-28 (see "Done — recent").
  Deliberately not taken: the intent router, tool-override registry, and model-accuracy
  card (the last needs an SME moderation-review pipeline that doesn't exist here).
- [x] **Interventions engine** — SHIPPED 2026-07-28 (see "Done — recent"). The rationale
  and escalation-tier ideas ported; the reference's DB-backed ACE/ZER rules did not —
  rules are code-defined over signals cerebroSG actually holds. Follow-ups left open:
  DB-backed rule overrides (admin-editable without a deploy, like the prompt registry),
  and the iOS/Android surfaces (only `apps/app` renders the card today).
- [x] **Tools → wind-down ritual, ritual builder, guided imagery** — ALL SHIPPED on
  `apps/app` (wind-down 2026-07-28; builder + imagery 2026-07-29 — see "Done — recent").
  The reference's 27-item `ToolsPage` grid was **not** taken: an everything-we-have hub is
  the opposite of the REDESIGN de-densification, and this app already has one Toolkit.
  **Skipped on evidence grounds:** Disidentification and Will Training are Psychosynthesis
  (Assagioli) constructs with a much thinner evidence base than everything else this app
  ships with a `WhyThisWorks` citation — they'd need a source we can't currently give;
  and **affirmation reading**, which is worse than thin (Wood et al. 2009 — generic
  positive self-statements lower mood in low-self-esteem readers). That folder's list is
  now closed; what remains from it is `CustomRitualsPage`, deferred as server-backed CRUD
  we have no model for.
- [x] **Games** — Thought Sort adopted 2026-07-28 with the claims stripped (see "Done —
  recent"); Cloud Drift / Zen Sand deferred to iOS/Android where they'd be verifiable on
  a device. Original assessment, kept for the reasoning: ⚠️ take at most 3–4, and
  **strip the efficacy claims**. The reference
  ships 18 arcade games whose catalogue advertises `builds: "Working memory" /
  "Selective attention" / "Cognitive flexibility"`. Importing them wholesale would (a)
  reverse REDESIGN §2.2 / IOS_PARITY item 2, which deliberately killed four mini-games as
  the weakest items against the F9 credibility bar and rebuilt the hub as "Toolkit ·
  small ways to steady", and (b) make unevidenced cognitive-training claims — the exact
  claim class the FTC fined Lumosity $2M for in 2016. Candidates that fit the existing
  Toolkit sections without claims: **Thought Sort** (→ Reframe; genuinely CBT-shaped),
  **Cloud Drift** / **Zen Sand** (→ Settle). Keep the catalogue's structure; drop
  `builds:` or replace it with real provenance via the existing `WhyThisWorks` component.


### `apps/app`: the `.meta` class has no global rule (found 2026-07-29)
`className="meta"` is used on ~20 elements across Home, Account, Library, Plan, Programs,
Sleep, the Toolkit and both ritual screens — durations, consent hints, sub-details, step
counters — but `globals.css` only defines it *scoped*: `.entry .meta` and
`.program-body .meta`. Everywhere else it renders as plain body text, so those lines sit
at the same weight as the copy they're meant to sit under. The fix is one line
(`.meta { color: var(--muted-2); font-size: 12px; }` — both scoped rules are more
specific and keep winning), but it changes the look of six shipped, screenshot-reviewed
pages, so it wants to land as its own change with a fresh visual pass rather than riding
along inside a feature commit.

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
> **Re-checked against the code 2026-07-31.** Tiers 1 and 2 below were written as a
> plan and then shipped, but the checkboxes here were never ticked — so this section
> claimed work was open that the section above records as done, and a "what's next?"
> read landed on already-built features. Verified against `backend/app/models/`
> rather than against the other section.

- [x] **Tier 1 (each closes a gap PRD.md already documents)** — SHIPPED; see the
  Tier 1 section above for the detail. `models/memory.py` (addressable per-item
  memory), `models/recommendation.py` (recommendations + practice catalogue),
  `models/safety_plan.py` (Stanley-Brown, user-authored — the sibling's
  AI-authorship deliberately not copied), weekly digest delivery.
- [x] **Tier 2 — the consumer habit loop** — SHIPPED. `models/habit.py` carries
  `Goal`, `Habit` and `HabitCompletion`, and `POST /goals/{id}/decompose` feeds the
  existing agentic planner. **The old parenthetical here — "No `Habit` or `Goal`
  model exists here at all" — was simply out of date.** Rituals / commitments /
  affirmations were assessed and mostly dropped with reasons (B2C_BACKLOG §4b).
- [ ] **Tier 2's one survivor, still an owner call:** a **daily intention** — does it
  replace the generated `Plan.focus` or sit beside it? (Note: the "Tomorrow's
  intention" journaling tool added 2026-07-31 is *not* this. That one writes a
  journal entry; this question is about what Home leads with.)
- [ ] **Tier 3 — skills content:** genuinely open, and the only tier that is.
  Shipped so far: DBT TIPP and CBT reframe. Absent from both `backend/app` and the
  Android source: MBCT, behavioural activation, role-play, guided imagery, dreams.
  Each needs the non-clinical framing pass + its own PRD row.
- [ ] **Flagged, needs an owner decision before any code:** gamification/leaderboard vs
  the OECD dark-pattern checklist; peer community (24/7 moderation commitment —
  recommend deferring the whole category).
- [x] **The two owner decisions that blocked the first slice were made** (2026-07-30,
  recorded in the Tier 1 section): memory persists only what the user wrote or
  approved, and the safety plan is user-authored.


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
- [x] **Real `duration_min` from the generated MP3** — DONE 2026-07-28. `narrate`
  minted the audio but never touched `duration_min`, so a hand-authored "8 min"
  sat over whatever length the file actually was, on all three clients — a small
  lie in a product that sells honesty. `services/media.mp3_duration_seconds()`
  now reads it from the MPEG frame headers (skips ID3v2, prefers a Xing/Info VBR
  frame count, falls back to the CBR byte-length calculation) and narrate writes
  `duration_minutes()` (half-up, floor 1) into the item. **No new dependency** —
  deliberately: the obvious library (mutagen) is GPL-2.0 and not worth linking
  into a commercial backend for one integer, and tinytag is still a dependency
  for ~60 lines of public-format parsing. Unreadable audio leaves the authored
  number alone (never replace a human's value with a guess) and logs a warning,
  so the stubbed-TTS tests and any odd provider output degrade cleanly. Admin
  content form says the field gets overwritten. Verified in-container: 292
  passed / 2 skipped, coverage 95.34 % (gate 95); admin tsc clean.
- [ ] Follow-ups still open: premium audio gating (signed short-lived media URLs)
  — **note the standing gap**: `/media` is a public StaticFiles mount, so every
  narration MP3 is world-readable by URL today; that is fine while the whole
  catalogue is free, and becomes a hole the moment premium narration exists.
  Bulk "generate all missing" stays deliberately unbuilt — the trigger was "if
  the catalogue outgrows per-row clicks (~25+)" and the seeded catalogue is 9
  scripts, so it would be speculative. Persistent web player (playback stops on
  navigation in `apps/app`, which uses a per-item `<audio controls>`). OWNER:
  click Generate per seeded item (burns ElevenLabs credits, ~15–30k chars total)
  — durations will now be correct automatically.

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

## Shipped 2026-08-01 — offline sync, native push, trends, and the games rebuild

Backend **425 passed / 95.22 %** (in-container, live Postgres); Android **309 unit tests, 0
failures**, `lintVitalRelease` green.

- [x] **Alembic had two heads** — `c8f1b6d94e23` and `c93f2b7a5e18` both descended from
  `b8e6d1a4f527`. `alembic upgrade head` fails on a branched graph; `prestart.py` catches that and
  falls back to `create_all`, which only CREATEs missing tables and never ALTERs an existing one.
  So on any database that already had the schema, **every migration after the branch point
  silently stopped applying** while the boot log showed one warning. Fixed with empty merge
  revision `d2b7f9c41a63`. Check `alembic current` on anything deployed in that window.
- [x] **Native push (FCM)** — `device_tokens` table (one row per install, not one column per user),
  `/users/me/devices` GET/POST/DELETE, `services/fcm.py` (HTTP v1, OAuth2 assertion signed with the
  `jose` we already ship — no `google-auth` dependency), and `notifications.deliver` fanning a nudge
  out to every live install and burying tokens the provider reports gone. Android side is dormant
  until a `google-services.json` exists: the plugin is applied conditionally, so a checkout with no
  Firebase project still builds and the app behaves exactly as before.
- [x] **Offline write queue** — `net/Outbox.kt`: writes persisted to the same encrypted store as the
  refresh token, each carrying its idempotency key **from the moment it is queued** (a key minted at
  send time lets a crashed retry create a second check-in), drained oldest-first with one failure
  stopping the drain so a day is never reordered. Server side: `Idempotency-Key` on `POST /moods`
  and `POST /journal` (409 on key reuse with a different body), `since=` cursors on both GETs.
  Undo works on a queued write too (`Outbox.dropLast`) — otherwise an offline mis-tap syncs the
  mistake back when signal returns.
- [x] **Trends** — `GET /insights/trends` + the Android screen. Gaps stay gaps (the line breaks
  rather than dropping to zero), `enough_data` gates every number, and the mood↔sleep correlation
  is withheld with a reason until ≥7 overlapping nights.
- [x] **Journal search** — server-side `q`/`tag` filters + `GET /journal/tags`, wired behind the
  instant local filter so offline still answers and only *older* entries come from the network.
- [x] **Mindful games rebuilt: 23 → 12.** The old set was seven round-builders behind 23 titles, and
  every answer came from `round % n` — nothing random, nothing harder, twelve titles that were one
  function with a different emoji. Now: one mechanic per game (a test fails if two share one),
  seeded sessions, a difficulty curve (time limits tighten, memory span grows, the field widens),
  expiry counting as a miss, per-game synthesized sound (`audio/GameSound.kt` — the old one used
  DTMF *telephone keypad* tones), and calm games left deliberately unscored. Retired ids redirect
  so saved shortcuts don't dead-end.
- [x] **Sleep: a failed read no longer looks like an empty history** — loading, failed and empty
  were one state, so a user whose request had just failed was told they had never logged a night.
- [x] **The nav pill gets out of the keyboard's way** — it reserved its slot with the IME up,
  leaving a dead band above the keyboard on every screen you can type on (`navVisible`, unit-tested).

### Open from this run

- [ ] **Android's coverage gate has been failing independently of this work.** `:app:check`
  requires 95 %; the tree measures **92.24 %** (was 91.65 % before — this work raised it). The
  shortfall is pre-existing and outside anything touched here: `ui/theme/ColorKt` (29 lines,
  theme-flip getters only one branch of which is exercised), `net/Session` (18), `net/Api` (34
  helpers with no contract test), `Session$FreeLimitException` (5, never constructed in a test),
  `health/HealthConnectSleep` (7). Verified by measuring the tree with the new classes excluded —
  identical number. Either cover those or restate the gate honestly; silently lowering it is the
  one option that should not happen.
- [ ] **`google-services.json` + `FCM_CREDENTIALS_PATH`** — the only things standing between the
  push code and working push. No app release needed once they exist.
- [ ] **Hindi strings for everything added here** — Trends, the games rebuild, the offline-queue
  copy and the Sleep failure state are English-only, consistent with the existing partial-locale
  policy (`values-hi` ships as a deliberate partial pending clinical review).
- [ ] **Games on a device** — the rebuild is unit-tested and compiles, but timing, sound levels and
  the sequence-replay pacing are exactly the things `MODULE_AUDIT.md` says are not real until seen.
  A `toolkit` re-audit is the right next step.

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
