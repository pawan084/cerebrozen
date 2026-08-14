# CereBro — TODO / Known Debt

> Prioritized output of the full-codebase review (2026-07-02), updated after the
> implementation pass the same day. Check items off as they land; re-run a review pass
> periodically. Companions: [ARCHITECTURE.md](ARCHITECTURE.md), [TECHNICAL.md](TECHNICAL.md).

## Open — full-codebase review (2026-08-13, gates run)

Backend 617 passed / **2 failed** / 2 skipped, coverage 95.58% (gate ≥95% holds). Android
`:app:check :app:assembleDebug :app:lintVitalRelease` green. Four Next apps: tsc + lint clean.
All six static gates green. **Playwright e2e run 2026-08-13: 53 passed in 2.0m, exit 0** (full
`docker-compose.e2e.yml` stack — web + admin + app + portal + api + db). **iOS not compiled**
(no Xcode on this host), and neither client has been walked on a device this pass.

- [x] **SAF-10 · The crisis directory is now gated, not just consistent** (2026-08-13, `WC-32`).
      `CLAUDE.md` lists crisis regions among the contracts kept in sync **by hand**, and unlike
      tokens, CSP, prices and claims, nothing enforced it — while this is the one where drift is
      measured in human harm: a member in crisis is shown these numbers by the client and told
      them again by the server (`crisis.reply_suffix`). Audited first: backend, iOS and Android
      already agree on all **7 regions** (US/CA/GB/IE/AU/NZ/IN), their number *order*, and the
      unknown-region fallback. They agreed by discipline; `scripts/check-crisis-lines.mjs` is
      what keeps them agreeing, and it is now CI's seventh gate.
      Order is asserted, not just membership — "Tele-MANAS leads every crisis surface"
      (REDESIGN §2.3) is a design rule, so a reordered list is a real regression.
      **Mutation-checked**: one digit changed in Android's Tele-MANAS number fails the gate with
      both lists printed; the file was restored byte-identical in the same command.
      Two things learned building it, both kept in the script's comments: URL spelling is
      normalized (backend stores `https://findahelpline.com`, Android the bare host — a real
      difference in representation, not in what gets dialled), and the gate **distinguishes "I
      could not parse this" from "these disagree"**. The first version mis-read iOS's `default:`
      branch and confidently reported a fallback drift that did not exist — a gate that cries
      wolf about crisis numbers gets muted, and a muted gate is worse than none
- [x] **SEC-02 · Production booted with no administrator** (found and fixed 2026-08-13).
      `is_admin` is written in exactly one place in the backend — `seed._ensure_user` — and no
      route grants or revokes it, so an installation that never runs that line can only reach
      its own admin console through an `UPDATE` against Postgres. Production was exactly that
      installation: the call sat **below** `if not settings.seed_demo_data: return`, and
      `_guard_production` **requires** `SEED_DEMO_DATA=false`, so the two rules cancelled each
      other out and every real deploy came up locked out — while `ADMIN_EMAIL`/`ADMIN_PASSWORD`
      sat in the environment, validated by the boot guard, looking like they had provisioned
      someone. Nothing caught it because nothing ran the production seed path.
      The administrator now seeds **above** the guard, alongside `_seed_media`, which is there
      for the same reason (structural, not demo). Safe on both counts that matter: the boot
      guard already refuses to start while `ADMIN_PASSWORD` is the demo value, so this cannot
      mint a known-password admin in production; and `_ensure_user` returns an existing row
      untouched, so a rotated `ADMIN_PASSWORD` does not silently reset the account on the next
      reboot. `tests/test_seed_admin.py` pins all three properties (admin exists after a
      demo-data-off boot; the demo account did *not* follow it up; a reboot is not a password
      reset) and was mutation-checked by moving the call back below the guard — 2 of the 3 fail.
      **Still open underneath this:** `REDESIGN_V2.md` §141 notes RBAC is one binary column
      where the portal's design needs seven roles, so `is_admin` is due a rethink regardless
- [x] **SEC-01 · Rate limits were bypassable with a forged header** (fixed 2026-08-13).
      `client_ip` now counts back from the **end** of `X-Forwarded-For` instead of reading the
      front. Caddy appends the peer it saw rather than replacing the header, so the first entry
      is a string the caller typed and only trailing entries are ours. New
      `settings.trusted_proxy_hops` says how many are ours — explicit, because the bug it
      replaces was an *implicit* trust assumption ("set by the Caddy reverse proxy"; it is
      appended, not set).
      **It defaults to 0, not 1**, because the two ways to misconfigure it fail very
      differently: too high reads a caller-supplied hop and every request mints its own bucket
      (silent — the original bug); too low keys real users onto one shared address and they
      collect 429s (loud — reported within the hour). The default is the one that cannot be
      quietly wrong on a box nobody configured, and `_guard_production` now refuses to boot
      production until it is declared. `TRUSTED_PROXY_HOPS=1` added to
      `backend/.env.production.example` — **the deploy must set it**.
      `tests/test_ratelimit_key.py` (7 cases) pins the direction of the count, the rotating-
      forged-prefix attack landing on one key, the short-chain fallback (claiming two proxies
      while one answers must not slide back to `parts[0]`), the two-proxy case, hops=0, and the
      production boot guard.
      **Caveat — not mutation-checked.** SEC-02's fix was verified by reintroducing the bug and
      watching the tests fail; the same check here was blocked by the tool permission classifier
      twice, so the tests are known-passing against correct code but have not been proven to
      fail against the broken version. Worth someone re-running: flip `parts[-hops]` to
      `parts[0]` in `client_ip` and confirm `test_a_forged_prefix_cannot_move_the_bucket` goes
      red. *(Original finding, retained for the record:)* `core/ratelimit.client_ip`
      keys the limiter on the **first** `X-Forwarded-For` hop. Caddy *appends* the real client to
      any incoming XFF rather than replacing it, and `deploy/Caddyfile` sets no `trusted_proxies`,
      so the first hop is whatever the caller sent. **Confirmed against the running API**: 26
      logins carrying one spoofed XFF hit 429 at the cap; 30 logins rotating the spoofed value
      returned 30×401 and never tripped it. That defeats the limiter on ~20 endpoints — login
      brute-force, OTP request, password reset, and the LLM/TTS cost guards on `/chat`,
      `/oracle`, `/habits` and admin narration. The docstring says the header is "set by the
      Caddy reverse proxy", which is the mistake: Caddy appends, it does not set. Fix is the
      **last** hop (`fwd.split(",")[-1]`) for a one-proxy deployment, or `trusted_proxies` in
      Caddy plus `request.client.host`. Whichever is chosen, pin it with a test that sends a
      forged XFF and asserts the bucket does not move
- [x] **TEST-01 · The backend suite was not hermetic** (fixed 2026-08-13, `WC-4`/`WC-91`).
      `conftest` now blanks `OPENAI_API_KEY`/`ANTHROPIC_API_KEY` beside the existing
      `TESTING=1` line, before any app import. Set rather than `setdefault`, so an exported key
      in the developer's shell loses too — pydantic-settings ranks the environment above the
      `.env` file, so this beats both. `tests/test_hermetic.py` is the tripwire and asserts the
      *effect* (`ai_provider == "none"`, `oracle_available is False`, `complete()` returns None)
      rather than the mechanism, so it still fires if provider selection changes shape instead
      of the two lines being deleted.
      Verified the way that matters: the full suite now runs green **with the live key still in
      `backend/.env`** and no blanking on the command line — the exact condition that was
      failing. 638 passed, coverage 95.52%
- [x] **TZ-01 · The sleep-date bound survived the C59-C65 timezone sweep** (found and fixed
      2026-08-13, surfaced by TEST-01's verification run). `SleepLogCreate._plausible_night`
      still asked `datetime.now(utc).date()` what day it was, while `core/localtime`'s docstring
      claims every "what day is it for this user" question goes through it. A pydantic validator
      has no user, so it *cannot* consult localtime — which makes a day-precise bound the wrong
      shape for it, not just the wrong implementation. Local dates span UTC-12..UTC+14, so a
      member's today sits up to a day either side of UTC's, and **a real Asia/Kolkata member had
      their tomorrow rejected for the 5.5 hours a day when IST is already on the next date**.
      The bound is now loose by one further day in each direction (`+2` future, `-731` past),
      which keeps register C26's actual intent — reject 1970 and 2099 — true in every zone,
      and leaves the day-precise question to the per-user code that has a timezone to consult.
      Caught because the verification run crossed 18:30 UTC: the identical window that broke
      the three tests fixed earlier under "date.today() is banned in the backend suite".
      `tests/test_sleep_date_bounds.py` (6 cases) pins it against **UTC offsets rather than
      wall-clock time**, so it asserts the same thing at 03:00 and 23:00 — which
      `test_input_bounds::test_sleep_rejects_implausible_dates`, the test that failed, cannot.
      Still open nearby: `services/organizations.py:156` also takes `utcnow().date()`, but for
      contract access windows, where a UTC boundary is arguably correct — **[decide]**
- [ ] **TEST-01 · The backend suite is not hermetic** — `services/ai.complete` picks its provider
      from *key presence*, never from `TESTING`, and `backend/.env` carries a live
      `OPENAI_API_KEY`. So a local `pytest` run makes **real OpenAI calls**: billable, slow, and
      non-deterministic. Two tests fail as a direct result —
      `test_habits::test_decompose_names_the_goal_even_without_an_llm_key` and
      `test_safety_plan::test_crisis_reply_is_unchanged_with_and_without_a_plan` — both of which
      exist to pin the "degrades without keys" contract. **Confirmed**: both pass when the run is
      given blank keys. The contract is therefore only ever verified on CI, and a developer
      running the suite locally sees red on two tests that are not broken. `conftest` should
      blank the provider keys when `TESTING=1`, so hermetic is the default rather than a property
      of CI's environment
- [ ] **DOC-01 · `CLAUDE.md` understates iOS readiness** — its gotcha says Sign in with
      Apple/Google are inert with "no `.entitlements` file yet; no `GIDClientID`".
      `apps/ios/CereBro/CereBro.entitlements` **exists** and declares
      `com.apple.developer.applesignin`, `com.apple.developer.healthkit` and `aps-environment`.
      The `GIDClientID` half is still accurate (read in `GoogleAuth.swift`, absent from
      `Info.plist`). Split the claim so the file's existence is not denied
- [ ] **WEB-01 · Two `react-hooks/exhaustive-deps` warnings in `apps/app`** —
      `(authed)/journal/page.tsx:71` (missing `reload`) and `onboarding/page.tsx:317` (missing
      `PHASES`). `next lint` exits 0 on warnings so CI is green, which is exactly how a stale
      closure reaches production

**Checked and found sound** (recorded so the next review does not re-derive them): the
safety-never-blocks rule holds end to end — `ai.complete` catches broadly so the keyword floor
still classifies when the LLM is down, and `email.send_email`/`sms.send_sms` are explicitly
non-raising, so a failing ops alert cannot 500 a crisis reply. `Settings._guard_production`
covers secret, admin password, seed data, rate-limit switch, CORS wildcard and trusted hosts.
The admin router guards every route at the router level. The one f-string SQL
(`users.py:347`) takes its table name from a literal tuple and binds the rest. No `.env`,
`.p8`, `.jks` or service-account JSON has ever been committed on any branch.

## Open — first real-device walk (2026-08-14, OnePlus CPH2681, Android 14)

The Android app had **never been run on a device or emulator** by any automated or manual
pass — 49 unit-test files, zero instrumented tests. This is the first walk: sign-in through
Today and You against a live local backend (`adb reverse`). Six screenshots. The emulator is
broken on this machine (crashes on `opengl32sw`), so the phone is the smoke device now — and
the signing-key clash recorded in July is gone, `adb install -r` succeeds.

- [x] **SAF-11 · The crisis region followed the UI language, not the phone's location**
      (found and fixed 2026-08-14). `rememberCrisisRegion` resolved via
      `Locale.getDefault().country`, which answers "what language is this UI in", not "where
      is this handset". The device ships `persist.sys.locale=en-GB` from the factory while its
      **SIM, network and timezone all reported IN** — so the You screen offered
      **"Samaritans · 116 123", a UK number that does not answer from India**, and Tele-MANAS
      was nowhere, in direct violation of "Tele-MANAS leads every crisis surface"
      (REDESIGN §2.3). en-GB is a factory default across OnePlus, Oppo, Xiaomi and Realme
      handsets sold in India, so the primary market was the one getting it wrong.
      New `deviceCrisisCountry(context)` resolves **network → SIM → locale**: network first so
      a visitor gets the numbers that answer where they are standing; SIM next because it is
      right exactly when there is no service, which is when someone may be reaching for a
      helpline; locale last, since it is all a wifi-only tablet has. Every getter is wrapped —
      `TelephonyManager` is absent on non-telephony devices and some OEM builds throw — because
      a crisis surface must degrade to a worse answer, never to a crash. Resolved once and
      reused for seed and refresh, so a SIM registering mid-load cannot flip the helpline under
      the user's thumb. `effectiveRegion` stays pure, so an explicit profile choice still wins.
      **Verified on the same handset**: the row now reads "Tele-MANAS — real people, 24/7 ·
      14416". `CrisisCountryResolutionTest` (6 cases) pins the order; mutation-checked by
      reverting to locale-first, which fails 3 of 6.
      **This is the gap in SAF-10's gate**: `check-crisis-lines.mjs` proves the three stacks
      agree on *what each region's numbers are*, and structurally cannot catch *the wrong
      region being picked*. Different bug, and only a device found it.
      iOS reads `Locale.current.region`, which on iOS is the user's explicit Region setting
      rather than the language — a genuinely different signal, so probably correct. **Unverified
      — confirm on a device before assuming**
- [x] **UX-01 · The primary CTA on sign-in is a button that cannot work.** "Continue with
      Google" is the full-width filled purple control; email sits below a divider as the
      secondary. Google sign-in is inert (no `GIDClientID` in the app; see the iOS gotcha in
      `CLAUDE.md`). Until it is configured, the most prominent control on the sign-in screen
      does nothing — either configure it or demote it below email
- [x] **UX-02 · The password placeholder renders as eight dots**, visually identical to a
      saved password. A member landing on sign-in sees what looks like a filled field, and the
      correctly-disabled "Continue with email" therefore looks broken rather than waiting. Use
      a text placeholder, or none
- [x] **UX-03 · The password field sits under the keyboard on sign-in.** `ToolScreens.kt` got
      a `BringIntoViewRequester` for exactly this problem in `355deb8d`; the auth screen never
      did. Same fix, different screen
- [x] **UX-04 · Today's sleep prompt truncates mid-word** — "A 20-second check-i…". The row
      gives "Log it" and the dismiss ✕ their full width and lets the subtitle clip
- [x] **UX-05 · Appearance subtitle reads "System default · switches with your phone or your
      call"** — "your call" appears to be a copy error
      **All five fixed 2026-08-14**, and four re-verified on the same handset by screenshot:
      the Google button and its divider now render only when `GOOGLE_WEB_CLIENT_ID` is
      non-blank (the same call audit H1 made on the paywall's dead "Start free trial" —
      removed rather than left to apologise for itself, and it returns with no further change
      once the client id lands); the placeholder reads "Your password"; the sleep banner's
      `maxLines` goes 2 → 3, which fits because three lines of `bodyMedium` plus the row's
      12dp padding lands at ~72dp, the ceiling the action box is already sized against — so
      the copy did not have to be cut, and truncating the reassurance out of a prompt asking
      someone to act was the wrong thing to drop; the Appearance idiom is now unambiguous.
      **UX-03 is implemented but NOT visually re-verified** — it needs the field focused with
      the IME up, and the shot budget was spent. Treat as implemented-not-verified.
- [x] **Verified good on device**: the bottom-nav `navigationBarsPadding()` change from
      `355deb8d` is **correct** on gesture navigation — the bar clears the gesture area with
      proper spacing. That was the open device-only question from the merge, now settled.
      Light Dawn renders cleanly, the crisis door is present top-right on Today, and sign-in
      works end to end against a local backend

## Closed — SEC-03 was wrong. Org RBAC is implemented and enforced (2026-08-14)

**Retraction.** The entry filed here earlier today claimed the organisation roles were "stored
and never consulted" and that "every org admin holds every power". **That was false**, and it
was published to `main` and `v2` before anyone checked it. What is actually true:

* `_require_write(admin)` guards **all six** write routes in `api/routes/organizations.py`
  (lines 103, 151, 288, 378, 438, 477).
* `ROLES_CAN_WRITE = {benefits_owner, programme_admin}`, so `analyst` and `privacy_reviewer`
  are read-only — which is exactly the matrix the owner specified when asked.
* `test_org.py::test_analyst_can_read_but_not_write` already covered it.

**How the error was made, since the method matters more than the incident.** Three greps, three
false negatives, each because the pattern searched for was imagined rather than read:
`grep "role" | grep -E "403|!=|=="` missed `admin.role not in ROLES_CAN_WRITE`; a search for
`require_role` missed `_require_write` (a name I invented, not one in the codebase); and
`grep "role" | grep "403"` required both tokens on one *line*, so it missed a test whose name
says "not write" and whose assertion is three lines below. Reading the file would have taken
less time than any of them. **A grep that finds nothing is evidence about the grep.**

- [x] **The real gap — enforcement was covered on 1 of 6 write routes** — is closed.
      `tests/test_org_roles.py` asserts the matrix: every write route × every read-only role
      returns **403 specifically** (not 404, not 422 — a route that refuses by accident would
      pass a looser assertion while leaving the hole open), plus the mirror image that a
      `benefits_owner` is *not* refused (a `_require_write` that raised unconditionally would
      satisfy every other assertion and break the product), plus a pin on `ROLES_CAN_WRITE`
      itself so widening it is a deliberate, reviewed act. 16 tests.
      Worth knowing for the next route: **FastAPI validates the body before the route function
      runs**, so an invalid body returns 422 without reaching `_require_write`. The matrix uses
      valid bodies deliberately; a test with a sloppy one passes for the wrong reason.
- [ ] **`programme_admin` and `privacy_reviewer` are both simply "write" and "read"** — the
      names promise more granularity than `ROLES_CAN_WRITE` delivers. A `privacy_reviewer`
      cannot edit the privacy centre, and a `programme_admin` can change the reporting
      threshold and remove seats. Whether that matters is a product call **[decide]**; the
      current behaviour is at least least-privilege in the safe direction
- [ ] **Correct `REDESIGN_V2` §141** — "RBAC is binary — `User.is_admin`; the portal needs 7
      roles" describes neither the model (4 roles, enforced) nor the gap. It is the line that
      set this whole detour going

## Open — instrumented tests exist, and do not yet pass unattended (2026-08-14, `WC-281`)

- [x] **The `androidTest` source set exists for the first time.** Runner + dependencies wired
      (`testInstrumentationRunner`, `androidx.test` ext/runner/rules/uiautomator, Compose
      `ui-test-junit4`). Verified working end to end on the phone: `connectedDebugAndroidTest`
      discovered and started **3 tests on CPH2681 — 1 completed, 0 failed**. The infrastructure
      is real, not scaffolded.
      `DeviceSmokeTest` covers only what a JVM cannot answer — the APK actually starting on
      Android, surviving recreation, and the crisis region resolving from **real** telephony
      rather than a shadow. Everything assertable off-device stays in `src/test` on Robolectric,
      which runs everywhere and costs no emulator
- [ ] **The two `ActivityScenario` tests hang — the app never goes idle.** Espresso waits for
      the main looper to quiesce and the app's infinite Compose animations (the sheen, the
      breathing orb) never let it. This is **the same gotcha `CLAUDE.md` already documents for
      iOS** — "`-resetState YES` … skips the splash and the real audio engine — keep new
      animated/async features gated the same way or the suite hangs" — arriving on Android
      because Android never had a suite to hang.
      The usual mitigation (`adb shell settings put global animator_duration_scale 0`) is
      **refused by this handset**: ColorOS requires `WRITE_SECURE_SETTINGS` and denies it to
      adb, so device settings cannot be the answer here even manually.
      The fix is the one iOS already made — a hook **in the app** that stills infinite
      animations under test, rather than depending on a device setting. `rememberReduceMotion()`
      is the natural seam: it already observes `ANIMATOR_DURATION_SCALE`, so give it a
      test-only override (instrumentation argument or a debug `BuildConfig` flag) and both
      tests should settle. Until then the launch tests are written but not runnable unattended
- [ ] **Not wired into CI yet, deliberately.** A suite that hangs would turn a 10-minute job
      into a timeout and teach everyone to ignore it. Wire it once the animation hook lands —
      and note CI has no device, so it needs Gradle Managed Devices or
      `reactivecircus/android-emulator-runner`, not the phone

## Open — merged from `v2` (Abhimanyu, 2026-08-13)

`355deb8d` "Android: fix onboarding, navigation and mindful tools" — fast-forwarded into
`main`. Compiles; `:app:check :app:assembleDebug :app:lintVitalRelease` green after the
fix below. What it changed and what it leaves open:

- [x] **The mindful menu is eight games, not twelve** — `object-tray`, `path-memory`,
      `mirror-tap` and `zen-sand` were retired and aliased onto the survivors, and four
      mechanics (`ChangeSpotting`, `PathRecall`, `BilateralTap`, `SandDraw`) went with
      them. **This broke `GameEngineTest::every_other_game_is_scored`**, which pinned nine
      scored games and now sees six — the count doing exactly the job it was written for.
      Fixed, and `isScored` now reads `GameCategory.Calm` off the registry instead of
      holding its own list of ids: that list still named `zen-sand` after the game was
      gone, and stayed correct only because the retired id happens to alias to another
      calm game. A category declared once cannot drift when the menu changes
- [ ] **The bottom nav bar takes `navigationBarsPadding()` again** (`CereBroApp.kt:290`),
      reversing a documented decision — the comment it replaced said Scaffold already owns
      that slot and an extra inset lifted the capsule "much too high". One of the two is
      wrong on any given device and only a device can say which. **Needs an emulator/phone
      check on both gesture and three-button navigation** before this is trusted
- [ ] **In-app language switching writes through deprecated `Resources.updateConfiguration`**
      (`applyOnboardingLanguage`, OnboardingScreen.kt) rather than per-app locales
      (`AppCompatDelegate.setApplicationLocales` / API 33 `LocaleManager`) — which is why it
      needs `restoreAppLanguage()` called from `MainActivity.onCreate` to survive a restart.
      It works and is `@Suppress`ed; it is worth moving to the platform API, and it silently
      maps every language other than Hindi to English chrome (correct today — only `values-hi`
      exists — but it is a mapping nobody will remember to extend)
- [ ] **The three onboarding intro cards all call `next()`** — three tappable cards with one
      destination, described in the comment as each opening "the next required step". Either
      they should route to distinct steps or read as one control
- [x] **The retired games' strings are gone** (2026-08-14, `WC-195`). Eleven, not the eight
      first counted: the four titles and their `_desc` pairs, plus three faculty labels
      (`mg_coordination`, `mg_spatial_memory`, `mg_visual_memory`) that lost their only users
      when the mechanics went. Found by scanning every `mg_*` key for a `R.string.` reference
      rather than by listing the games from memory, and safe to delete because the codebase
      contains **zero `getIdentifier` calls** — nothing could be reaching them dynamically.
      Re-scanned after: no `mg_*` string is unreferenced. `:app:check` green
- [ ] **Mindful Games is entirely untranslated — 56 `mg_*` strings in `values`, 0 in
      `values-hi`** (`WC-194`). Every game title, description and faculty label falls back to
      English for a Hindi user, which is the largest single localisation hole on Android.
      Deliberately **not** done in bulk by an agent: 56 strings of mental-health copy is a
      content task where a bad translation is worse than the English fallback. Needs a native
      speaker, or a reviewer who is one

## Open — Light Dawn redesign (`ref/`, started 2026-08-06)

Spec: [REDESIGN_V2.md](REDESIGN_V2.md). Phase 1 (token inversion) is done and verified;
everything below is open.

- [x] **Wave 2 (landing) — the three pages `ref/landing.html` carries and this site did not**
      (2026-08-12): `/organizations`, `/safety`, `/accessibility`. Footer gained an
      Organizations column, `sitemap.ts` gained all three, and `trust-pages.spec.ts` gained a
      test per page. The prototype is written in the future tense ("in the intended mobile
      product", "the design target includes") because it is a design reference; transposing
      that to a live site in the present tense would have over-claimed on the three pages
      where it matters most. So each page keeps `ref/`'s structure and splits into what is
      true today vs what is not: Safety names the mechanisms (the public `/crisis` route
      outside the session guard, Tele-MANAS-first, safety plan never read back) and then
      lists what production safety still needs; Accessibility says outright **"we do not
      claim conformance today"**; Organizations leads with **"Status: in design, not yet
      available"** because there is no organisation, sponsorship, entitlement or cohort model
      in the backend (§3.3) — its never-shared / reportable boundary is reproduced in full
- [ ] **Wave 3 (member web) — Today is PARTLY graduated** (2026-08-12). `lib/todayHero.ts`
      hand-mirrors the Android contract (`heroKindFor` TodayScreen.kt:778,
      `OFFLINE_HERO_ROUTES` :791, `heroWhyRes` :808) and the TOD-01 hero now renders on the
      real `/home` from `/plans/active` — the provenance sentence branches on `plan.source`
      (never hardcoded: the rule generator does not read the journal, the AI planner reads
      journal *titles* under consent, so a flat claim is false half the time), and "Works
      offline" only shows when the target route genuinely is. The dashboard folds behind
      Your day / Jump back in / Somewhere else. Note `apps/app` has **no unit-test runner**,
      so `lib/todayHero.ts` is e2e-covered only — unlike its Android twin, which
      `ScreenLogicTest` pins
- [x] **SAF-01 → `/crisis`** (2026-08-12). The mock's `useState` region selector did NOT
      graduate: `/crisis` is a server component on purpose ("renders even when the API is
      down"), and a client selector would make *which emergency number you see* depend on a
      JS bundle. Every region is in the markup behind a native `<details>`, and an e2e test
      loads the page with `javaScriptEnabled: false` to keep it that way. A "Verified" badge
      now requires a named source **and** a check date — India has both; US/UK say plainly
      they are unverified (the inverse of the bug the ref/ audit found)
- [x] **EXP-01 → `/explore`** (2026-08-12). The six needs shipped in Wave 1; the secondary
      search graduated and filters the cards **on this page** rather than the catalogue — a
      box searching a different corpus than the cards beneath it is worse than no box.
      `.explore-search` is pinned to 48px (the base `input` rule lands ~42px).
      **"Recently used" did NOT graduate:** there is no recents store on web
- [x] **SLP-01 → `/sleep`** (2026-08-12). What graduated is the ORDER — tonight leads (the
      wind-down ritual), and the rhythm, the sounds and last night's check-in fold below.
      **Two features deliberately did not:** the reorderable wind-down (the mock states it
      "does not persist anywhere" — a reorder that forgets on reload is the same fake-save
      class as a Save button that only sets a boolean) and the "10:30 pm, wind-down from
      9:45 pm" line (**no target-bedtime field exists** in `backend/app/models/sleep.py`, so
      the number would be invented)
- [x] **TOD-02 is unblocked and shipped** (2026-08-12). The cross-stack change this was
      waiting on landed first — `backend/app/services/moods.py` is now the single definition
      of the six states and of "difficult", `agentic.py` and `nudges.py` read it instead of
      each carrying a narrower copy, and all four clients converged. The screen then
      graduated to `/checkin`, linked from Today's check-in hero. Original blocking note
      below, kept because it is the reason the order mattered:
      **TOD-02 was BLOCKED on a cross-stack change, not on design.** Its six states add
      "Overwhelmed" and "Not sure" to the shipped five, and mood strings are **interpreted
      server-side**: `agentic.py:130` and `nudges.py:69` both test
      `{"anxious","low","tired"}`, so an "Overwhelmed" check-in would be read as *not
      stressed* — suppressing the stress-aware plan and the wind-down nudge for the user who
      most needs them. `insights.py:152` already knows "overwhelmed"; nothing knows "not
      sure". Adding the states needs backend + Android + iOS in one commit (CLAUDE.md
      cross-stack rule). What DID graduate web-side is TOD-02's thesis: the check-in now ends
      on a **consequence** ("shapes your next step and your weekly trends. Nothing here is
      scored") rather than a saved value. It deliberately says nothing about the journal,
      because whether the journal is read depends on the generator — see `lib/todayHero.ts`
- [x] **`app/design/` has reached zero** (2026-08-12) — `checkin` was the last one and it
      graduated to `/checkin`. The index page now renders an empty state rather than an empty
      list, and the surface itself stays: the per-screen notes recording what each graduation
      *dropped*, and why, are the useful residue

- [x] `design/tokens.css` inverted to light-first Light Dawn + Night opt-in; synced to
      web/admin/app; `scripts/check-contrast.mjs` added and wired into CI (108 pairings pass)
- [x] Primary CTA moved from white pill to accent fill (a pale pill is invisible on ivory);
      landing nav + app topbar de-hardcoded from `rgba(14,12,34,…)`
- [x] **Wave 1 (partial) — five-tab IA on `apps/app`**: Today · Explore · Talk · Journal · You.
      Sleep demoted under Explore; new `/explore` hub (EXP-01) with the spec's six practice
      families, each on a distinct real destination; Toolkit gained `#breathe/#ground/
      #reframe/#settle` anchors so those families land somewhere specific. Urgent support
      moved OUT of the mobile tab bar and INTO a permanent `AppHeader` entry — that landed
      first, so crisis never stopped being ≤2 taps. Landing space cards + footer + three
      e2e specs updated to match. **`/explore` is compile-verified only — not seen running**
      (it is behind auth and Docker was not up)
- [x] **Design surface at `/design`** (owner direction 2026-08-06: design first, wire later).
      Redesigned screens render with mock data, no auth and no backend — the same thing the
      `ref/` prototypes are — so they can be reviewed without Docker and without tearing the
      working API wiring out of the live screens. `noindex`. Each screen graduates into its
      real route once signed off; this surface is scaffolding and should shrink to nothing.
- [x] **TOD-01 Today redesigned** — one decision at full volume (self-explaining
      recommendation incl. "it did not use your journal"), a quieter check-in row, and
      Your day / Tonight / This week folded into `<details>`. Presence-framed throughout:
      counts days shown up, never days missed. Verified running at `/design/today`
- [x] **TOD-02 check-in, EXP-01 explore, SLP-01 tonight, SAF-01 urgent support** built on
      `/design`. SAF-01 verified interactively: India is the only verified region; switching
      to US/UK flips the badge to "Not verified yet" and warns the numbers are unchecked;
      "Elsewhere" shows no number at all. This is the honest version of the bug the ref/
      audit flagged (Indian numbers badged "Verified" for every country)
- [x] **Organisation portal design surface at `apps/portal`** (port 3003, `npm run dev`).
      Shell (284px sidebar, five nav groups, sticky topbar, permanent privacy wall) plus 10
      of the prototype's 36 routes: DASH-01, MEM-01, COH-01, COH-02, PRO-01, CAM-01, ENG-01,
      PRI-01, ROL-01, PRE-01. Mock data only (`lib/mock.ts`); the five non-negotiable privacy
      strings are quoted verbatim in `lib/copy.ts`. `tsc --noEmit`, `next build` and
      `next lint` all clean; every route opened and looked at in a browser except the
      ≤820px drawer, which could not be given a real narrow viewport (rules verified in the
      parsed stylesheet instead). Not deployed, not in compose, no backend.
- [x] **`apps/portal/app/globals.css` is in `scripts/sync-tokens.mjs` TARGETS** — already
      done when checked on 2026-08-12; the gate covers all four `globals.css` copies
- [x] **All 36 portal routes are built** (2026-08-12). The 26 that were disabled nav items
      now exist, typecheck, lint clean, build, and each returns 200 with a heading — walked on
      a running server, not inferred from the build output. `lib/nav.ts` has an `href` on
      every sidebar entry; the ten detail routes (MEM-03, PRO-02, PRO-03, CAM-02, REF-02,
      PRI-02, SAF-02, INT-02, ROL-02, BIL-02) stay out of the sidebar and are reached from
      their parents, as in the prototype. `portal.spec.ts` walks all 36.
      **AUTH-01/AUTH-02 render the access flow and authenticate nobody** — no identity
      provider, no session, no cookie, the email field disabled and no submit button anywhere.
      A control that appeared to sign someone in would imply a gate that does not exist, and a
      fake gate is worse than an obvious absence. The prototype's "Open demo workspace" button
      was not ported for the same reason. An e2e test asserts no cookie is set.
      *Generator bug worth remembering*: the pages were written by a script that emitted
      `\uXXXX` into JSX **text**, where backslash-u is not an escape — 21 pages rendered
      "anyone\u2019s safety" verbatim. Invisible in a diff and in `tsc`; caught by reading the
      served HTML. `portal.spec.ts` now asserts no page renders a literal escape
- [x] **The organisation model exists** (2026-08-12). `models/organization.py` +
      `a1c4f7e2b930`: `organizations`, `org_admins`, `eligibility_groups`, `org_memberships`,
      `sponsored_programmes`. `services/organizations.py` owns the reporting rules and
      `/org` is the API. 19 tests in `tests/test_org.py`; migration verified with
      `alembic upgrade head` against a real database, not just `create_all` in fixtures.
      **The design is mostly about what is absent.** There is no per-member activity table
      and no `manager_dashboards` column — the portal's Settings screen tells administrators
      that individual reporting is "not a feature that exists in a disabled state", and a
      column by that name would make the sentence false the moment somebody flipped it in
      psql. `OrgMembership` is an entitlement row with no `last_active`, no `sessions` and no
      `programme_progress`. `MembershipOut` returns no user id, email or name, so no employer
      is handed a payroll→CereBro mapping.
      Cross-tenant reads are prevented structurally rather than by a check: every route
      resolves the organisation from the signed-in user and **no route takes an `org_id`**, so
      the request cannot be expressed. A test asserts that. Another asserts the org model,
      service and routes import no wellbeing model at all — if someone adds
      `from app.models.mood import MoodLog`, the join is one line away and the suite fails
      first. Being a CereBro platform admin grants nothing here; they are different jobs
- [x] **Four portal screens read the real backend** (2026-08-12). Dashboard, Members,
      Cohorts and Programmes call `/org` through `apps/portal/lib/api.ts` — the same token
      model as `apps/app` (access in memory, refresh in localStorage) but a **separate storage
      key**, because an administrator is very likely a member too and sharing a key would mean
      signing out of the portal signed you out of your own wellbeing account in the next tab.
      `/signin` is now a real form. It shipped deliberately inert this morning because there
      was no backend; there is one now. What did **not** become real: "Continue with SSO" and
      "Open demo workspace", because there is still no identity provider and no demo tenant,
      and both would be the same lie in a new place.
      **The suppressed path is the one that was actually verified.** `portal-live.spec.ts`
      provisions its own organisation, adds a two-member cohort, signs in through the UI and
      asserts the cohort reads "Too small to report" and specifically *not* "0 activated" —
      a blank or a zero would tell an employer that nobody in a small team engaged, which is
      the inference the whole threshold exists to prevent
- [x] **`POST /admin/organizations`** (2026-08-12) — platform-admin provisioning. There was no
      way to create an organisation through the API at all: the first row had to be written by
      hand in psql, which is not an onboarding path and left nothing able to set up the state a
      test needs. Deliberately on `/admin`, not `/org`: an org admin cannot create another
      organisation or promote themselves into one, and a test asserts that
- [x] **The portal's four backed forms write for real** (2026-08-12): privacy centre and
      settings (`PATCH /org`), invite (`POST /org/members`), cohort builder
      (`POST /org/groups`). Eight screens are live now, not four. Saves follow the rule the
      consent toggles set — a failed write says "Not saved… nothing was changed" and the
      control returns to the stored value, and every screen re-renders from the RESPONSE
      rather than from what was clicked. That last part matters most on the threshold, where
      the server deliberately disagrees: asking for 5 stores 20, and an e2e test asserts the
      portal then shows 20.
      Two things from the prototype did not graduate. The cohort builder's live size estimate
      multiplied a made-up base by an assumed activation rate as you typed — a number an
      administrator reads as a headcount should come from counting people, so the real size
      appears on the cohorts screen instead, suppressed when it is too small. And the CSV file
      input is **gone rather than left inert**: a file picker that silently does nothing is
      worse than an honest absence, so the card says the importer is not built and states the
      rule it will have to keep
- [x] **Four more screens went live** (2026-08-13): administrator access (new
      `GET /org/admins`), the launch checklist, group detail, and — the interesting one —
      **the checklist is DERIVED, not stored**. Six booleans in a table can say "eligibility
      connected" while the organisation has no seats; it now asks each question against real
      state, so a step cannot be ticked by editing a row and cannot drift from what is
      configured. An e2e test creates a group through the API and asserts the step ticks
      itself on reload.
      `GET /org/admins` returns identity, unlike the seat list, and the asymmetry is the
      point: attesting an administrator is meaningless without knowing who is being attested,
      while a member is not an officer of anything. It still says nothing about that person
      as a CereBro *user* — holding an admin role does not make their own account the
      organisation's business, and a test pins that
- [x] **The e2e suite was quietly flaky, and the summary line hid it** (2026-08-13). A run
      reported "44 passed" while 45 tests were declared — the difference was **1 flaky**: a
      test that failed, retried and passed, which Playwright counts as passed. Comparing
      declared against passed is the only reason it surfaced; do that rather than reading the
      summary.
      The cause was real and would have hit CI: signup is capped at **10/minute per IP**,
      every browser test shares one IP, and the portal spec creates about eight accounts, so
      the suite trips its own limiter partway through. `ratelimit.py` already had a documented
      off-switch for pytest for exactly this reason and the Playwright stack had none, so it
      now has `RATE_LIMIT_ENABLED`, set to `0` only in `docker-compose.e2e.yml`.
      Because that is an off-switch on a security control, `Settings._guard_production`
      **refuses to boot** when it is disabled with `ENV=production` — verified in both
      directions. No e2e test asserts rate-limiting behaviour, so nothing is lost by
      disabling it there; the alternative (fewer signups, shared fixtures) was rejected
      because it would make the tests depend on each other
- [x] **The audit log was recording the wrong person** (2026-08-13). `add_member` already
      binds a local `user` to the *member being looked up*, which shadowed the injected caller
      dependency — so every "seat added" row named **the member as the administrator who
      acted**, turning the trail into precisely the payroll→account mapping the seat list is
      designed not to be. Caught by a test asserting the member's address never appears in the
      trail. The dependency is `actor` in all five mutating routes now, with the reason
      written above it
- [x] **AUD-01 is live, and its promise is now true** (2026-08-13). The screen said "trace
      every administrative action" while **nothing recorded org-admin actions at all** — it
      was the one surface its own claim was false for. `admin_audit_logs` gained a nullable
      `org_id` (migration `b2d5e8a1c473`), every mutating `/org` route writes a row, and
      `GET /org/audit` filters on an id stamped at write time, so a client cannot request
      another organisation's trail because it never supplies the id. CereBro staff actions
      keep a NULL `org_id` and stay out of a customer's trail — what we do is our trail, not
      theirs. Four backend tests plus an e2e that acts through the UI and reads it back
- [x] **Billing and the data map are live for what the model knows** (2026-08-13). Billing
      shows seats, activation and contract dates and **deliberately shows no invoice table**:
      there is no billing integration, and a plausible-looking invoice list is the kind of
      fiction someone forwards to finance. The data map's last row carries no retention
      period, because personal wellbeing content has no arrow out of the member account
- [ ] **20 portal screens still render `lib/mock.ts`** and every one says so in a warning
      notice above the fold. That banner is load-bearing while the portal is part live: the
      wired and unwired screens look identical, so an administrator who cannot tell them apart
      would read invented figures as their own. Delete the banner per screen as it becomes
      true.
      What is left needs data that does not exist rather than wiring: **campaigns** and the
      **pathway builder** have no model at all; **engagement** and **outcomes** would need
      behavioural aggregates the product deliberately does not collect per member, so they
      need a genuine design answer (survey responses? session counts above threshold?) before
      they can be anything but a mock; **member preview** is static by nature
- [ ] **No SSO, so the portal stays off a public host** — `deploy/Caddyfile` keeps
      `portal.cerebrozen.in` commented out. Password auth alone is not enough for an
      administration console, and the OIDC plumbing cannot be *verified* without a real
      identity provider configured. Shipping unverifiable auth here would be the one mistake
      this whole surface has been built to avoid
- [x] **Sponsorship grants premium now** (2026-08-13). `organizations.is_sponsored()` was
      correct and unused: an organisation could pay for a seat and the member got a database
      row and nothing else. New `services/entitlements.py` is the one place that answers "what
      may this account use today", and the two gates that decided it — `usage.enforce_quota`
      and `media.is_entitled` — no longer read `user.subscription_tier` at all. Both used to
      keep their own private copy of the paid-tier set, which is exactly how a third gate
      would have been written that sponsorship again did not reach; `media.is_entitled` now
      takes the *resolved tier* rather than a user, so it structurally cannot read the column.
      **The grant is never written back.** One line would have set the tier on the user row
      and it would have been wrong: sponsorships end, and a stored tier would leave that
      account premium forever with nobody paying. `test_entitlements` pins the column
      untouched after a sponsored member has used premium.
      `/users/me` and `/auth/me` report the *effective* tier, because a client showing a
      paywall the server would let the member walk past is the same lie in the other
      direction — plus a new `sponsored` flag, since the difference that matters to a member
      is whether they can cancel it. `apps/app`'s account screen has a third branch on it: no
      upgrade button, no Stripe portal (that would open on a customer who does not exist),
      and a sentence saying who pays and what they can see. `/admin/users` deliberately keeps
      showing the stored column — staff answering a billing question need the purchase, not
      the employer's grant. *iOS and Android closed 2026-08-13, below*
- [x] **The two native clients stopped selling premium to people who already have it**
      (2026-08-13). They branched on tier alone, so a sponsored member unlocked correctly and
      was then shown the thing they cannot act on: on iOS a paywall plus "Manage or cancel
      anytime in your Apple ID subscriptions", which opens a page with nothing on it — read
      as either a lie or a charge they cannot find, and hunting for a charge you cannot see
      is a worse afternoon than never being offered the link. On Android, a price list for a
      seat their employer pays for.
      Both now branch on the server's `sponsored` flag. iOS `PremiumView` splits into
      `sponsoredState` (no products, no purchase CTA, no Apple link) and `purchaseState`
      (unchanged), and the You row stops promising "Manage your subscription". Android's
      `PremiumScreen` gets three states — sponsored, bought-elsewhere, and the paywall — and
      the You row drops its sheen when there is nothing to offer, because that animation is
      what makes the row read as an offer rather than a setting.
      **Two things fell out of doing it.** `paywall_view` fired for everyone who opened the
      screen, putting members who *could not convert* into the denominator of the conversion
      rate; it now waits for the tier to resolve and fires only on an actual paywall. And
      Android had nowhere to remember an entitlement, so a failed profile read would have
      demoted a sponsored member back to a price list — `Session.rememberEntitlement` keeps
      the last answer and `signOut` drops it, since devices are shared and inheriting it
      would tell the next person their employer pays for a seat that is not theirs. It
      decides what a screen *says*, never what an account may *use*
- [ ] **The sponsored branches are unverified on a device** — Android is JVM-verified only
      (`:app:check` green, 2 new `SessionStoreTest` cases) and iOS is static-only on this
      Windows host: not compiled, not run. Both need a walk against a live backend with a
      sponsored account before this is trusted. iOS additionally needs `xcodebuild` on a Mac
- [x] **Three tests only passed before 18:30 UTC** (2026-08-13, found by running the suite
      at 23:30 UTC). `test_habits` (×2) and `test_admin_metrics::test_streak_endpoint_mirrors
      _ios_rules` built their fixtures from `date.today()` — the *container's* zone, UTC —
      and compared them against endpoints that answer in the user's own timezone, which
      defaults to `Asia/Kolkata`. For the five and a half hours after 18:30 UTC the two are
      different days and all three failed. `app/core/localtime`'s docstring names this exact
      bug on the app side; the tests were the last consumers still asking the container what
      day it is. They now ask the account (`/users/me` → `local_today(tz)`), and the streak
      fixture stamps instants that land on the intended *local* day and never in the future
- [x] **`date.today()` is now banned in the backend suite** (2026-08-13). The five remaining
      files migrated to `tests/dates.py` (`account_today` / `account_day` / `account_iso`, or
      `local_today(user.timezone)` where the test holds the row), and
      `test_local_days::test_no_test_seeds_a_fixture_from_the_containers_clock` walks every
      test file's **AST** — so the explanations in the docstrings do not read as violations —
      and fails naming `file:line`. Mutation-checked: a probe file with one `date.today()` is
      caught. A second pin asserts `User.timezone`'s column default still equals the constant
      `tests/dates` computes from, because if the default moved, every fixture in the suite
      would quietly start describing the wrong day
- [ ] **Sponsored members are invisible to `/admin/metrics`** — the premium count is
      `subscription_tier IN (...)`, so a sponsored seat reads as a free user there. Arguably
      right (it is not subscription revenue) but it is currently accidental rather than
      decided, and B2B seats need their own line once there is more than one organisation
- [x] **Bulk eligibility import** (2026-08-13). `POST /org/members/import` takes the CSV as
      **text**, unparsed: had the portal split it into rows first, the promise that an
      unrecognised column is rejected would be a promise made by a browser, and the header row
      is exactly where it has to hold. `services/eligibility_csv.py` is an **allowlist** over
      the header, not a denylist of alarming words — `mood` and `diagnosis` are rejected, but
      so are `wellbeing_score` and `eap_referral` and whatever nobody has thought of yet. The
      file is rejected **whole**: dropping the offending column would teach the administrator
      that sending it was fine. Each row is then validated by the same `MembershipCreate` the
      single-invite route uses, so the two paths cannot drift on what a seat may contain, and
      a bad row is reported and skipped while the rest import — failing 400 valid rows over one
      typo pushes people towards splitting files until it works.
      The portal checks the header **before reading the file past its first line and before
      sending anything**, so an export carrying a diagnosis column never leaves the
      administrator's machine; the server checks again, because the browser's copy is a
      privacy measure and not the guarantee. An e2e watches the wire to prove the refusal
      makes no request. The report identifies rows by line number and the organisation's own
      `external_ref`, **never by email** — an import report is part of the seat list, and the
      seat list is deliberately not a roster. One `org.seat_import` audit row per import
      rather than one per seat: five hundred identical entries would bury every other action,
      and a trail nobody can read is not accountability. 15 backend tests + 1 e2e
- [ ] **Portal forms are inert by design, and that will need revisiting** — selects, date
      fields and text areas across the invite, cohort, pathway and campaign builders hold
      `defaultValue` and do nothing. That is honest for a design surface, but once a backend
      exists each one needs the same treatment the consent toggles got: optimistic state that
      reverts and says so when the write fails, never a UI that claims a save it did not make
- [x] **`apps/portal` scaffolded** (new app, port 3003) — shell + 10 of 36 routes on mock
      data, no auth, no backend, no organisation model. The 26 unbuilt routes render as
      disabled nav items so the full IA stays reviewable. Added to `sync-tokens.mjs` TARGETS;
      the sync gate independently confirms its token block is byte-identical.
      *Superseded 2026-08-12*: all 36 routes are built and nothing is disabled any more
- [x] **The organisation portal is wired into the stack** (2026-08-12). It had 10 of
      `ref/portal.html`'s 36 screens and no way to run: no Dockerfile, no compose service, no
      CI step, nothing in the Caddyfile, no e2e. Now it has a Dockerfile on :3003, a
      `docker compose` service, its own typecheck+lint step in CI, and it joins the e2e stack
      with `portal.spec.ts` walking all ten routes plus the sidebar's own links.
      **It also had no CSP.** The other three Next apps each carry a hand-copied
      `middleware.ts`; the portal never got one because nothing served it. Wiring it in
      without one would have deployed the least-protected surface in the product on the host
      that shows an employer their organisation's data. It now carries the same nonce policy
      (tighter than admin's — no third-party `img-src`, since nothing here renders a remote
      image), `check-csp-sync.mjs` gates four files instead of three, and the header was read
      off a running server rather than assumed.
      **The Caddy block is deliberately commented out.** `AUTH-01`/`AUTH-02` are among the 26
      unbuilt screens, so there is no sign-in in front of it; publishing an unauthenticated
      console on a real subdomain would be the actual mistake. It runs locally and in CI
      until those exist
- [x] **`apps/portal` is in CI** (2026-08-12) — its own tsc + lint step, and
      `check-csp-sync.mjs` now pins four middlewares because the portal finally has one
- [x] **Portal responsive/a11y verified, and it found two defects** (2026-08-13).
      `e2e/tests/portal-a11y.spec.ts` drives the portal at 390×844 with axe
      (`@axe-core/playwright`, serious+critical, WCAG 2.1 AA tags) on sign-in, the dashboard
      and members-on-a-phone: **no serious or critical violations**. No page scrolls sideways
      at phone width (wide tables scroll inside their own `.table-wrap`), and reduced motion
      is honoured in the computed styles rather than merely declared in a media query.
      The two defects reading the media queries could never have found:
      **(1) the closed drawer was still tabbable.** `transform: translateX(-105%)` moves an
      element; it does not hide it. The off-canvas nav stayed in the tab order and in the
      accessibility tree, so tabbing from the topbar landed in a menu nobody could see. Fixed
      with `visibility: hidden` (delayed by the length of the slide on the way out, instant on
      the way in, so the animation still plays). The test asserts the link is in the DOM, not
      visible, **and absent from the accessibility tree** — mutation-checked by reverting the
      CSS, which fails it with "Received: visible".
      **(2) the toggle's label lied.** `aria-label="Open navigation"` was fixed while
      `aria-expanded` flipped, so a screen reader announced "Open navigation, expanded" — an
      instruction to do the thing already done. It now follows the state and carries
      `aria-controls`.
      Two of my own test bugs on the way in are worth remembering: a role locator naming a
      link "Members" (the label is "Members & seats") matched nothing, and `not.toBeVisible()`
      passes for a locator that matches nothing — so the first version was a **false pass**;
      and `getByRole` skips the accessibility tree, so it cannot prove DOM presence
- [x] **`.chip` and `.ui-chip` now meet the 48px floor in `apps/app`** (2026-08-13). Both are
      buttons everywhere they appear — chat retry and suggestions, the ritual cue picker,
      journal tag filters, the appearance picker — and stood at 31px and 42px against the
      floor the rest of `globals.css` keeps, which made the easiest things to mis-tap the ones
      people reach for while distracted. `app.spec.ts::every chip is a tap target, not a
      decoration` measures every visible chip on two screens that render them unconditionally,
      and fails if a screen renders none rather than passing vacuously
- [ ] **Night cannot be pinned per-subtree from `design/tokens.css`** — it is scoped
      `:root[data-theme="night"]`. `apps/app` works around this with its own `.theme-night`
      class. If any client needs a night-pinned subtree, that mechanism has to move into the
      shared tokens or be duplicated per app
- [x] **`.text-btn`, `.tiny`, `.btn-primary` promoted app-wide** (2026-08-12) — they were
      defined under `.design-root` only, so a graduated screen would have rendered raw UA
      buttons at `min-height: 0`, failing the 48px rule. Same declarations, unscoped, so the
      design surface and the real route render identically. **`.sub` deliberately NOT
      promoted**: shipped screens rely on its descendant rules (`.card .sub`, `.authcard .sub`)
      and a global would restyle every one of them
- [x] **Wave 1 (Android) — five-tab IA**: Today · Explore · Talk · Journal · You.
      `enum class Tab` relabelled (route stays `home`, so deeplinks/back-stack/nudges are
      untouched); Sleep left the tab bar for a pushed `sleep` destination and gained a
      visible back door; new `ExploreScreen` hub with the spec's six practice families on
      six real destinations (sleep · breathe/reset · sounds · cbt · toolkit · programs) plus
      a quiet support door. New `ic_tab_today` (dawn) and `ic_tab_explore` (compass)
      drawables in the existing 2dp line style. Crisis never depended on the Sleep tab — it
      hangs off You's Support card, and Explore now carries a second door, so ≤2 taps held
      throughout (pinned by `NavigationChromeTest`)
- [x] **Android token port** — `ui/theme/{Color,Theme,Tokens}.kt` on the canonical Light
      Dawn role scale with Dawn as the default appearance (`AppTheme.systemDark` starts
      false); Night re-toned indigo → plum. Every canonical role is byte-pinned against
      `design/tokens.css` in both directions, and every text/tonal role clears 4.5:1 on all
      three neutral grounds **and** its own `-soft` wash in both themes — **no value needed
      adjusting**, the web-side darkening of `--text-faint`/`--warm`/`--danger`/`--amber`
      already did that work. `ContrastTest.kt` 19 → 22 tests; `ThemeTokensTest.kt` 11 → 13.
      `res/values/colors.xml` now holds the Dawn ground with the plum floor in a new
      `values-night/` (a light-theme device used to flash deep indigo on every cold launch)
- [x] **YOU-05 Android language picker** (`LanguageScreen` in `ui/screens/Settings.kt`,
      route `language`, You → Personalise row). Onboarding asked for a language and
      *nothing could change the answer*: the You profile card rendered the saved value
      ("Calm Guide · Hindi") but its tap target opened the companion picker, so a wrong
      tap on the first run was permanent. Follows the `CompanionStyleScreen` null-state
      rule (a failed read selects nothing rather than showing "English" as an answer the
      screen never learned), reverts on a refused write, and renders an unknown stored
      value as its own row because the field is free text server-side
      (`services/language.py`). Copy is scoped to what the setting actually does — the
      backend reply directive for chat/plan/Oracle/starters — and says outright that app
      chrome follows the device locale and that helpline names and numbers are never
      translated. `LANGUAGES` in `OnboardingScreen.kt` went `private` → `internal` so the
      two pickers cannot drift. en + hi strings; `:app:testDebugUnitTest` and
      `:app:lintVitalRelease` green
- [x] **TOD-06 Android notification inbox** (`ui/screens/NotificationInbox.kt`, route
      `notifications`, Today header bell + You → Reminders row). Android had *no record*
      of what it had sent: a nudge existed only while it sat in the shade, so "did my
      reminder fire?" was unanswerable once it was swiped away. New
      `notify/NotificationLog.kt` is written by the only two places that post —
      `Reminders.show` (local alarm) and `Push.show` (FCM) — immediately **after**
      `notify()`, so the log records what was delivered, never what was intended. Local
      only, capped at 30, dismissal matched on the instant rather than a list index (a
      nudge arriving between render and tap would otherwise dismiss the wrong row).
      Split into Scheduled / Delivered because "is it on" and "did it fire" are different
      questions with different evidence. The empty state distinguishes "nothing has
      arrived" from "server nudges are off in this build" — `Push.available()` is false
      without a `google-services.json`, so the flat version of that sentence would have
      been a quiet lie. `NotificationLogTest` (9 tests). **Today's header lost its search
      pill and initial-letter avatar** to match TOD-01's single trailing bell; both
      destinations survive (search is Explore's trailing icon, profile is the You tab)
- [x] **Android: the Dawn pass shipped nine mock screens; they are gone** (`ANDROID_AUDIT.txt`
      is the full record). Home was the worst of it: five rows hardcoded into "Your day" —
      "Morning check-in · Completed at 9:12 AM" for every user on every launch — with the
      real plan, the presence week ring, the milestone line and recent check-ins all
      switched off behind `if (false)`, while the summary above them read the true counts.
      Nine routes had been pointed at `Reference*` screens that either never called the API
      or had lost what they replaced: `reminders` (Save button with an empty body, so the
      inbox that reads its prefs always said "no reminder scheduled"), `cbt` (a save button
      that was a painted Box with no click handler — the thought record was discarded),
      `bodyscan` (frozen "2:41" over an empty Play), `tipp`, `baseline` (wrote a prefs key
      nothing read, so Insights' "Your starting point" could never fill), `goals` (no
      habits, no way to finish one), `patterns` (read-only, leaving per-item memory and
      recommendations with no reachable UI at all), `trends`, `dailyplan`. All now route to
      the real screens. `NoticeChangeScreen` and `BodyScanContentDetailScreen` were deleted
      rather than kept — an honest gap beats four un-clickable choices, one of which
      promised "CereBro will suggest a different next step". `one_good_thing`/`intention_set`
      pointed at the bare Journal composer, leaving both tools' screens unreachable.
      **Crash fixed**: `NotificationLog.routeFor("checkin")` returned `"today"`, a route the
      graph never had, so the inbox's Open button called `navigate()` on nothing; the nudge
      map is now checked against `EXTERNAL_ROUTES`, the set the deeplink resolver already
      vets. The old test asserted `"today"` — it pinned the crash instead of catching it.
      **Nine top bars became one** (`CereBroTopBar` in `Common.kt`, 14 call sites, none
      hand-rolled): leading back-or-brand-mark, serif title over a quiet subtitle, crisis
      door last and in the same pixels everywhere. Every tap target regained `Role.Button`,
      a content description and press feedback. The crisis screen's copy moved to
      strings.xml with Hindi (all 16 `crisis_*` Hindi strings already existed and went
      unused), as did the practice/breathing/gratitude family.
      **Counts corrected after the reconciliation below** (this entry was written on the
      branch, before the two remediations were merged): raw hex outside the token file is
      **209 → 42**, not 19; the English-only literals are **11 in `TodayScreen.kt` and 2 in
      `ExploreScreen.kt`**, not ~65 and ~17; the suite is **470** tests. `:app:lintDebug`
      and `check-claims.mjs` green.
      *Still open*: those 13 literals, the 42 art/gradient hex values that have no canonical
      role, and `Api.pushStatus()` is uncalled with no push toggle in Settings
- [x] **The audit was remediated TWICE, in parallel, and reconciled** (2026-08-12, merge
      `c30b9971`). Two branches fixed `ANDROID_AUDIT.txt` without knowing about each other
      and agreed on most of it — BUG-01 got a byte-identical fix on both sides. Where they
      differed, each route was decided on merits rather than by taking a side:
      **from the branch** — `patterns`→`PatternScreen` and `trends`→`TrendsScreen` (two more
      real screens that were imported and never routed), `dailyplan`→`PlanScreen` rather
      than deletion so a stale link lands somewhere real, the `NotificationLogTest` that
      asserts `Tab.Home.route` and validates against `EXTERNAL_ROUTES` instead of a
      hand-copied route list, and the `practicelib_*` en+hi externalisation;
      **kept from main** — `sleepinsights` (wired week/month/3-month charts with no twin,
      now linked from the Sleep rhythm line) and `guidedimagery` (four journeys + TTS, still
      an IA decision), the nav guard that checks `graph.findNode` and logs rather than
      wrapping `navigate` in `runCatching`, and the canonical palette / mood taxonomy /
      Verified badge / chime wiring / crisis strings that the branch predated.
      **Process note:** this is the second parallel-work collision in the repo's history.
      Agree who owns `apps/android` before the next pass.
- [x] **Mood taxonomy unified across backend, Android, iOS and web** (2026-08-12). An
      "Overwhelmed" check-in read as *not* struggling: `agentic.py` and `nudges.py` each
      carried their own copy of the difficult-mood set and both omitted it, so the strongest
      signal a user can send produced the steady-baseline plan and scheduled no supportive
      nudge. `backend/app/services/moods.py` is now the single definition (`DIFFICULT`,
      `is_difficult()`); `insights`, `trends`, `agentic` and `nudges` all read it. Clients
      converged on the spec's six — Good · Anxious · Low · Tired · Overwhelmed · Not sure.
      Web had drifted furthest (Great/Good/Okay/Low/Anxious, **no Tired at all**, so a web
      check-in could never fire the wind-down nudge that keys on that word). Android alone
      held three more copies: `CheckInDetailScreen` said "Clear" where Today said "Good",
      and onboarding seeded "Okay", a state no picker offers. Unknown labels stay neutral,
      which is what makes "Not sure" safe to offer. Contract row added to ARCHITECTURE.md
- [x] **Android's Dawn palette was never the canonical one** (2026-08-12). It was taken
      "byte-for-byte with the Light Dawn phone in `ref/mobile.html`" — a different source
      from `design/tokens.css` — and the two disagree on every neutral: canonical `--text`
      is a warm `#211D20`, Android shipped indigo `#1C1740`; the accent was indigo, not the
      plum `#5A2B5C`. **Nothing could catch it**: `sync-tokens.mjs` gates the four
      `globals.css` files and cannot read Kotlin, and `ContrastTest` pinned Android's own
      drifted values under a comment calling itself "the mirror of tokens.css `:root`". The
      screen authors noticed even though the tooling did not — the light-dawn screens were
      full of raw hex like `#F3ECF3`, which **is** `--surface-field` exactly, written by
      hand because the token did not carry it. `DawnPalette` is now tokens.css byte for
      byte and `ContrastTest` pins the canonical values
- [x] **Screen review wave: the practice and crisis children** (2026-08-12, 38 → 46 of ~64).
      Eight new screens walked on device; five defects, two of them safety-rule breaches.
      All five verified fixed on hardware, not just in the diff.
      **TIPP had no crisis door.** It is entered at "a 9 or 10 when thinking feels
      impossible", is the one screen in the app that names self-harm — and its only
      tappable elements were Back, Previous, Next and an expander. The note raised the risk
      and then gave *directions*: "Urgent support lives in the You tab". Someone at a 9 or 10
      was asked to back out, find a tab and find a row, from memory. The note is now the
      pathway (`onUrgent`, the convention four other screens already use), and the copy is
      translated to Hindi under the file's own crisis-copy rule — the rest of `tipp_*` stays
      English by design, but this pair names self-harm.
      **The crisis screen denied a real third-party disclosure.** "Someone you selected;
      CereBro never contacts them automatically" — but `escalation.on_crisis` emails or texts
      the trusted contact on a crisis-level event whenever `notify_consent` is on, and the
      trusted-contact screen says so plainly two taps away. The two screens contradicted each
      other and the backend settled it. It defaults off, so the sentence was true right up
      until a user enabled the feature, which is exactly when being wrong about it matters.
      The dead `urgent_trusted_detail` copy carried the same claim and was corrected too,
      rather than left in resources for someone to reuse
- [x] **Two more claims that `check-claims.mjs` structurally could not catch** (2026-08-12).
      Both were wrong *in their own words*, and the gate matches literal banned phrases.
      Explore's "Favourites and downloads · Saved and offline" promised the one capability
      the banned-phrase list exists for — no client implements downloads — while opening
      neither favourites nor downloads: it routed to `sounds`, the same destination as the
      "Sound · Audio and mixer" card two rows above. Deleted rather than reworded; a second
      row to one destination is not worth honest copy. And the grounding intro stated "Voice
      guidance on · Soft chime between steps" as a fact about the practice one tap away —
      `GroundingScreen` has no TextToSpeech, no chime and no sound of any kind. Stating it as
      *on* also implied a setting to turn off, and there isn't one.
      *Lesson for the gate*: a phrase list catches recidivism, not invention. Both of these
      needed a screen walk to find, which is the argument for finishing the remaining ~18
- [x] **Screen review wave: settings, search and the games** (2026-08-12, 46 → 51 of ~64).
      **Every switch in the app was anonymous to a screen reader.** Found on Zen ripples,
      whose water-drop toggle rendered with no text and no content description — but it was
      never a Zen ripples bug: `AppSwitch` took no label, and a Compose `Switch` has no text
      of its own, so on all fourteen call sites the visible label was a *sibling* `Text` and
      therefore a separate semantics node. Twelve of the fourteen were bare; two (Breathe,
      Rituals) already cleared the switch's semantics and made the row the accessible toggle,
      which is why this needed checking rather than assuming. The bare twelve included all
      seven DPDP consent toggles, the 18-or-older age gate, the journal lock, the
      trusted-contact crisis permission and the analytics opt-out — where "specific and
      informed" is a legal standard, and where a control with no accessible name fails
      WCAG 4.1.2 outright. `label` is now required so the compiler catches the next one, and
      `SwitchLabelTest` guards that it is real and localized rather than an English literal
      **Search claimed the whole app and indexed a fifth of it.** "Everything served to the
      apps is searchable" — `SEARCH_KINDS` is five `/content` kinds, so searching "ground"
      returned nothing while the app carried a grounding family, a crisis-grounding screen
      and a 5-4-3-2-1 practice. The placeholder on the same screen already said the true
      thing ("Sounds, stories, programs…"); the body copy over-claimed past it
      **The privacy policy denied an audited read path.** "Support tooling sees counts and
      account state — never the words", but `admin.read_safety_excerpt` serves the verbatim
      text behind a flagged event. That path is deliberate, per-row and writes an
      `admin_audit` row naming the admin — defensible, which is exactly why the copy should
      describe it instead of denying it. Same failure shape as the trusted-contact line: an
      absolute privacy claim that a real code path contradicts. `CLAIMS_MAP` §1 carried the
      same absolute and was corrected with it
      *Clean on this pass*: Human support (names its coach directory as roadmap rather than
      implying it exists), delete account, crisis region, gratitude, CBT reframe
- [x] **Screen review wave: games, programmes and the practice intros** (2026-08-12,
      51 → 62 of ~64). Walked: the 12 mindful games + a played-through round, pattern glow,
      still point, zen sand, imagery, ritual builder, insight reel, CBT-I and MBCT overviews,
      breathing prep. **Most of this wave was clean** — and two screens are quietly the best
      in the app: the imagery intro warns that going looking for a calm place can turn up the
      opposite and offers 5-4-3-2-1 instead, and the ritual builder says "CereBro won't nag
      you about this. The cue is the reminder." Both CBT-I and MBCT overviews carry the
      clinician disclaimer. Rule Switch ends on "0 of 6" without a hint of failure framing.
      One defect: **the breathing-prep screen used a raw Material `Switch`** with its own
      hardcoded track and thumb colours — outside the design system *and* outside the
      accessibility fix from the previous wave, so a screen reader met "Soft chime" and
      "Haptics" as nameless toggles. It also rendered four English literals past the
      `breathprep_*` strings that already existed in strings.xml **and in Hindi**. Now
      `AppSwitch` + `stringResource`, verified announcing on device.
      *This one slipped past my own test*: `SwitchLabelTest` only looked at `AppSwitch` call
      sites, so the one screen that avoided the component avoided the check. It now also
      fails on a raw `Switch(` anywhere outside `Common.kt`, and the detection was verified
      against the original defective line rather than assumed
- [x] **`CLAIMS_MAP` cited a test that did not exist** (2026-08-12). §2's "Not a therapist,
      diagnosis, or crisis service" row named `DisclosureCopyTest` (Android) as its
      mechanism, and there was no such file anywhere in the tree — the row cited a guarantee
      nobody had written. `ScreenLogicTest` covers *when* the disclosure re-shows (the 3-hour
      cadence); nothing covered *what it says*. Found while auditing which tests actually ran
      during the merge gate rather than trusting the row. Written rather than softened, since
      the whole point of that file is that a row without a test is an intention: it now pins
      that the Talk pill names all three denials (AI, not a therapist, not a crisis service),
      that every AI surface disclaims medical care, and that none of this copy uses a banned
      medical verb except as a denial — "never diagnoses or prescribes" has to stay legal
      while "treats depression" does not
- [x] **Every `CLAIMS_MAP` citation now resolves, and CI keeps it that way** (2026-08-12).
      The audit found **six** broken references, not one: `DisclosureCopyTest` and
      `ConsentDefaultsTest` had never been written, and four backend files had been renamed
      without the doc following — `test_usage.py` → `test_usage_limit.py`, `test_consent.py`
      → `test_consent_enforced.py`, `test_safety.py` → `test_safety_reach.py`,
      `test_insights.py` → `test_insights_no_guesses.py`. Renames are the quieter failure:
      the claim stayed true and only its evidence went missing, so nothing ever complained.
      `scripts/check-claims-tests.mjs` resolves every `tests/…`, `::test_…` and `` `FooTest` ``
      in the table to a real file, function or class, and runs in CI beside `check-claims`.
      Verified by breaking a citation on purpose and watching it fail
- [x] **`guidedimagery` has a door** (2026-08-12). It is the seventh family in the Practice
      library — "Picture somewhere calm · Four places to settle into" — which is where a user
      already goes to choose a practice by need. Two things fell out of placing it there.
      The sleep family's subtitle read **"Body scan and imagery"** and opened only the body
      scan, so it had been advertising the missing screen all along; it now reads "A slow
      body scan". And the library called itself "Six clear families" in three places, so the
      count moved to seven in English and Hindi rather than leaving copy that contradicts the
      list beneath it.
      **A test now enforces the general rule** (`RouteReachabilityTest`): every route
      registered in the NavHost must have something that navigates to it. It counts only real
      navigation — `onOpen`/`open`/`openTool`/`navigate` and the widget map — because naming
      a route in an accent `when` or the bottom-bar set is *styling*, and that is exactly
      what made `talk/live` look connected when it was not
- [ ] **A fourth dead alias: `dailyplan`** — found by the new test, not by eye, and my own
      earlier orphan scan had missed it because that scan counted any nav-ish mention.
      `composable("dailyplan")` and `composable("plan")` both render
      `PlanScreen(onBack = back)`. The merge note said it was kept "so a stale link lands
      somewhere real" — **that reason does not hold**: `dailyplan` is not in
      `EXTERNAL_ROUTES`, so no external link can reach it either. It is a third name for one
      screen, like the two talk aliases. All four are listed in `RouteReachabilityTest`'s
      `knownUnreachable` with their reasons, which is a holding position, not a fix
- [ ] **Two imagery implementations are now both reachable** — the design rules cap this at
      one per behaviour ("never two pop games / four breathing screens"). The toolkit's
      "Build somewhere calm, one sense at a time" opens `imagery` (Rituals.kt): a single
      eight-line script, and notably the one that warns "if it stops feeling calm, stop" and
      offers 5-4-3-2-1 instead. The new Practice-library door opens `guidedimagery`: four
      landscapes, five steps each, with voice cues. They are genuinely different exercises,
      but they are the same *behaviour*, and giving the second one a door made the overlap
      live rather than theoretical. Owner call: merge them, or keep both and say in the copy
      how they differ. Note the safety-out belongs on whichever survives
- [ ] **Two screens are registered but unreachable, and two more are aliases** (2026-08-12,
      found by checking all 58 static routes for a navigation reference rather than by eye).
      `guidedimagery` has **zero** references anywhere outside its own `composable(...)` —
      four journeys and a TTS engine no user can open. `talk/live` and `talk/chat` both
      render `TalkScreen(onOpen = open)`, the exact same call as the Talk tab, and nothing
      navigates to either: three route names, one screen. They also inflate the route list
      and the bottom-bar/accent maps, which is part of why "~64 screens" overstates the real
      surface. **Now that all four have actually been opened** (see the entry above), the
      recommendation is no longer symmetrical: `guidedimagery` is finished product — four
      journeys, five steps each, voice cues and pause — so it wants a door, not a delete.
      The two talk aliases are pure duplication and can go. `intention` and `onegoodthing`
      are likewise complete and reachable *only* if the model emits an `intention_set` /
      `one_good_thing` widget; that may be deliberate, but three finished screens sitting
      behind a model's discretion is worth an owner decision rather than an assumption
- [ ] **Mindful game "practice" tags are keyed by the faculty names they deliberately
      avoid** — the values are correct activity descriptions ("Hold a sequence in mind"), but
      the resource keys are `mg_working_memory`, `mg_selective_attention`,
      `mg_inhibitory_control`. The KDoc on that field records that this exact claim class
      came back "through a third door" once already. Low risk today because
      `check-claims.mjs` bans the vocabulary in *values*, but a key that names a faculty
      invites someone to "fix" the value to match it. Rename the keys
- [ ] **Judgment call for the owner: "0 of 6 mindful responses"** on a game's completion
      card. The surrounding copy does the forgiveness work ("Beautifully done", "Progress
      comes from returning, not perfection"), but naming correct answers *mindful* responses
      implies the other six were unmindful. Presence framing would count rounds shown up for,
      not answers matched
- [x] **Screen review complete: every screen in the app has now been opened and looked at**
      (2026-08-12). The last four had no door, so they were reached by temporarily adding
      them to `EXTERNAL_ROUTES`, building, capturing, and reverting — the patch never reached
      a commit (verified: tree byte-identical to HEAD afterwards). Worth doing rather than
      reading the code, because it answered the question the code could not: are these
      half-built things safe to delete, or finished work that lost its entrance?
      **They are finished.** `guidedimagery` renders four journeys (forest, ocean, mountain,
      meadow), each a five-step sequence with voice cues, pause and exit. `onegoodthing`
      ("Anything counts — a kind word, a finished task, a decent cup of tea") and `intention`
      ("Not a to-do list — one thing that would make tomorrow feel steadier") are both
      complete, well-written and save to the journal. None of this is scaffolding.
      That changes the recommendation in the orphan entry below: this is built product with
      no entrance, not dead code to sweep. `talk/live` is the exception — confirmed on device
      to render text identical to the Talk tab, so it really is just a third name
- [ ] **The active breathing session is the one screen still unseen** (2026-08-12). Not for
      lack of trying: the emulator process died **three times out of three** at exactly the
      same step — tapping "Start Box Breathing" to enter the animated session. Reproducible,
      so not a flake. The emulator log ends mid-line with no error and no guest-side fatal,
      and an app crash would leave the emulator running and show in logcat, so the likely
      cause is the host SwiftShader software renderer failing on the breathing animation
      rather than anything in the app. **Not provable either way from here** — it needs a
      physical device or a hardware-GPU AVD. Worth actually checking rather than assuming
      environment, because a renderer that heavy would also matter on low-end phones, which
      is a large part of the India-first audience. This is also the screen Abhimanyu's
      `a6ae5e3b` moved the voice toggle into, so that change is likewise unverified on device
- [x] **The Android screen review is finished — every screen opened and looked at**
      (2026-08-12), across roughly a dozen waves. The only surface still unseen is the
      *active* breathing session, for the environment reason in its own entry above. Four
      method notes, all learned
      the hard way — a frozen emulator framebuffer yields *plausible* screenshots of the last
      good frame (hash two captures ~10s apart to catch it), and distinct file hashes do not
      mean distinct screens: seven "successful" deeplink captures were all Today, because the
      routes were not in `EXTERNAL_ROUTES`. Open one and look before trusting a batch. Third:
      only 20 of the 59 routes are in `EXTERNAL_ROUTES`, so **almost everything left must be
      reached by tapping, not by deeplink** — a `cerebro://` to anything else silently lands
      on Today. The harness that works is `uiautomator dump` → tap by text/content-desc, and
      printing the screen's own text next to every capture so a wrong screen is obvious
      immediately rather than three screenshots later. Fourth: a route with no door can still
      be reviewed — add it to `EXTERNAL_ROUTES` temporarily, build, capture, revert, and check
      `git status` is clean before committing. `am start` needs `-a android.intent.action.VIEW`
      for the URI to be read; `-n` alone brings the app forward without consuming the deeplink,
      which looks exactly like a route that does not exist
- [ ] **HC-06: practice content is still hardcoded** — the library ships as Kotlin literals
      rather than coming from `Api.content()`. Blocked on the backend, which only knows
      `sleep` and `soundscape`; extending `/content` is the actual task
- [ ] **`InsightsScreen` is orphaned** — the reader with the baseline card is not routed
      anywhere; `WeeklyInsightsScreen` is what users reach. Port the card across and delete
      the orphan, rather than leaving two insight readers to drift apart
- [ ] **The iOS half of the mood-taxonomy change is unverified** — edited without a macOS
      machine to build on, so it is reviewed-but-not-compiled. Confirm on the next Mac pass
- [ ] **`sync-tokens.mjs` still cannot gate Android** — it compares CSS text, and Android's
      palette is Kotlin. `ContrastTest` is the only thing standing between that palette and
      another silent drift, and it was itself wrong until today. Worth a real check that
      parses `Color.kt` against `design/tokens.css`
- [x] **Crisis: a Verified badge only where the numbers were verified** (2026-08-12). The
      badge rendered unconditionally, so every region wore it — a US user saw green
      "Verified" against 911 and 988, numbers nobody here has checked. This is the exact bug
      the `ref/` audit found on the prototypes. The claim was made three times on one screen
      (badge, strapline, and the line carrying the number someone would dial); all three are
      now conditional. India keeps it — checked against the MoHFW Tele-MANAS listing and the
      ERSS 112 listing, the sources the web `/safety` page cites. Everywhere else says
      "Not verified yet" and gives the reason
- [x] **Guest mode stopped telling guests the network broke** (2026-08-12). `guestMode`
      gated the auth screen and nothing else, so every server-backed screen rendered its own
      failure copy — "Couldn't load patterns. Please try again." — about a request that was
      never going to succeed. Fixed at one seam: `ensureAccess` throws guest-specific copy,
      and since every screen already surfaces `ApiException.message` through
      `Throwable.userMessage`, they all changed at once. **Unverified on device** — it needs
      a signed-out session
- [ ] **Android gaps still open vs `ref/mobile.html`** — verified against the prototype,
      *not* the whole list the first read suggested. Already built and needing nothing:
      PVR-04 memory list (it is `PatternScreen.kt`, with inspect/edit/delete), SND-01/03/04
      (library + favourites + mixer live in `SoundsScreen`). Genuinely missing: **TOD-06**
      notification inbox, **ACC-05** app diagnostics, **VID-01/02/03** video lessons (owner
      ruling 2026-08-06: UI shell only, no real playback), **ORG-01…07** sponsored access
      (needs a backend `org` router + membership model + Alembic revision). Needs a UX call
      rather than code: **TLK-06** — Talk's "Memory: on" chip opens the consent switch, and
      the prototype also links the remembered-items list; the header already carries
      persona + memory + start-fresh, so where the second link goes is a design decision.
      **TLK-05** (a list of past conversations) is *not* treated as a gap — this product
      deliberately ships one thread
- [ ] **Android: `Type.kt` untouched by the port** — the type scale carries no colour, and
      the spec's display-font divergence (Iowan/Georgia/Fraunces) was resolved in favour of
      keeping what ships. Nunito stays; revisit only if the owner picks a display serif
- [ ] **Android: not run on a device or emulator** — the port is verified by the JVM/
      Robolectric suite only (447 tests). The Dawn arm of every screen, the new Explore hub,
      the re-toned hero panels and the two new tab icons have not been *seen*
- [ ] **iOS token port** — `DesignSystem/Theme.swift`; note its comment claiming Dawn is
      "hand-synced with the web app" is already stale and gets more so until this lands
- [ ] **iOS five-tab IA** — `RootView.swift` `MainTabView` still ships
      Home · Sleep · Talk · Journal · You
- [ ] **iOS token port** — `DesignSystem/Theme.swift`; note its comment claiming Dawn is
      "hand-synced with the web app" is already stale and gets more so until this lands
- [ ] **Night-era veil sweep** — ~53 `rgba(255,255,255,…)` overlays across the three web
      apps still assume a dark ground; they read grey or vanish on ivory
- [ ] **Marketing screenshots are stale** — every baked phone image on the landing page
      shows the old indigo app, and at least one shows a **"3-day streak"**, an affordance
      both the spec and the design skill ban. Regenerate after the client redesign.
      *2026-08-06: the landing home no longer renders any of them* — the v2 rebuild
      draws its three device mocks in markup from the tokens
      (`apps/web/components/PhoneMock.tsx`), so the page stopped contradicting itself.
      `public/brand/banner-hero.jpg` and `public/screens/*.webp` are now unused on the
      site but still shipped; delete or regenerate them when the client redesign lands
- [ ] **e2e theme spec** — values updated to the new grounds, but the suite has not been
      run (needs the docker stack); run `docker-compose.e2e.yml` before trusting it
- [ ] Owner decisions blocking IA work — see REDESIGN_V2.md §6 (Sleep as a top-level tab,
      iOS/`apps/app` standing vs an Android-only spec, en-GB spelling, cohort floor)
- [ ] **B2B2C is unbuilt end to end** *(partly closed 2026-08-12: the backend model and
      `/org` API now exist — see the organisation-model entry above. What remains is the
      join to the portal, entitlement enforcement, and everything commercial.)* — no organisation, sponsorship, entitlement or cohort
      model; RBAC is one boolean where the portal needs 7 roles; `apps/admin` is an internal
      staff console, not the org portal, and should stay one

> **2026-08-04 — the 500-point register:** a full placement/sequence/bug audit
> across all clients + backend produced **679 justified points** in
> [AUDIT_500.md](AUDIT_500.md) (index + ranked top 20) with the evidence in
> `docs/audit/A–H`. Fixes are landing as waves (ledger below); §H of the
> register is this file's open items, restated with citations.
>
> **Wave 10 — backend security cluster (register C1-C3) is CLOSED:** oracle
> threads are namespaced per caller (`scoped_thread_id` — a foreign UUID
> resumes nothing; existing default threads preserved, custom client thread
> ids migrate into the caller's namespace one time); StoreKit receipts are
> bound to their buyer (appAccountToken must match the caller AND
> `users.apple_original_transaction_id` is unique — first verifier owns the
> subscription; Alembic `b7e4c9a2d615`); the App Store webhook has the same
> ProcessedWebhook replay guard Stripe always had (keyed on
> notificationUUID). All pinned in `tests/test_subscription_binding.py`;
> hermetic suite 506 passed / 96%.
>
> **Wave 11 — data integrity + DPDP (register C4/C5, findings 51/66/67):**
> the idempotency key is now RESERVED before the write and completed in the
> same transaction (was: recorded after the commit in a separate transaction,
> so concurrent retries both inserted and the loser's IntegrityError was
> swallowed); mood `note`/`trigger` go through `safety.scan_and_record` like
> journal and chat (risk written into a check-in produced no event before);
> `voice_storage` is enforced and reported at `/voice/stt` (`audio_retained`);
> `model_training` gained `services/training.py` — the single gate any future
> corpus build must pass (no pipeline exists; the seam does). Pinned in
> `tests/test_consent_enforced.py`; hermetic suite 512 passed / 96%.
>
> **Wave 12 — web app cluster (register D1, D8, D10 + premium narration):**
> `authedFetch` no longer treats **403 as a dead session** (401 still refreshes
> once then signs out) — a consent-gated refusal now reaches the caller, so the
> Patterns page's own "is AI memory switched on?" message can finally appear
> instead of the user being signed out; the catalogue is fetched **as the
> signed-in user** on library/sleep/programs, so premium narration keeps its
> `audio_url` (the anonymous read stripped it from the very items a subscriber
> pays for); deleting an account no longer claims success on failure (was:
> `finally` cleared the session and redirected even when the DELETE failed);
> the crisis-region select reverts and states the failure instead of silently
> looking saved. Pinned in `e2e/tests/app.spec.ts`; **e2e suite 25 passed,
> exit 0**. NOTE: register D1's claim that the GET signs users out was
> **inaccurate** — only the write path is consent-gated; corrected in
> `docs/audit/D-web-app.md`.
>
> **Wave 13 — web silent failures & dead ends (register D3-D7, D9, D13-D15):**
> the Home check-in no longer congratulates a save that failed (it takes the
> affirming response back and says so); Sleep, Journal and Plan saves gained
> catches (were `try/finally` with no catch → unhandled rejections, console
> errors, user silence — the journal draft still survives, and now says why);
> `/plan` is no longer a dead end for a user who has never had a plan (the
> generate button lived inside `{plan && …}`); the DPDP export can't hang
> forever on a network failure; Programs matches the active journey by
> `content_id` not title equality; Goals' week circles use LOCAL day keys (the
> UTC key shifted "today" for IST users before ~05:30); "Make this today's
> plan" has a busy guard. e2e suite 25 passed / exit 0.
>
> **Wave 15 — the web client stops keeping and claiming what it shouldn't**
> (register D17-D20, D22-D24; D25 withdrawn as a false finding): signing out —
> and deleting an account — now clears every personal key from localStorage,
> not just the refresh token (the cached **safety plan**, the journal draft and
> the onboarding answers stayed readable by the next user of a shared browser);
> the 18+ gate renders for **every** sign-up path and is enforced in code, so
> OTP and Google sign-ups no longer create accounts the `/attest` POST then
> claims were gated; the safety plan caches what the SERVER confirmed rather
> than every textarea on screen (unsent words were being presented as "the copy
> saved on this device"); the printable tab opens synchronously so Safari's
> popup blocker can't kill it, and a block no longer blames the user's plan;
> sign-in stops reporting a 500 or a rate-limit as "Invalid email or password";
> Home's empty week no longer prints real-but-wrong weekday letters (`new
> Date("0")` parses in Chromium); the trusted-contact "saved" note clears on
> edit. e2e 25 passed / exit 0.
>
> **Wave 14 — operator accountability (register E31-E37):** new
> `admin_audit_logs` table + `services/admin_audit.py` + read-only
> `GET /admin/audit` (Alembic `c3f8a1d64b27`), wired into content CRUD, user
> enable/disable, prompt save/activate/revert and nudge broadcasts — the
> operator surface was entirely unattributable; the disable **reason** is no
> longer discarded (the route declared only the `active` query param, so
> FastAPI dropped the body the panel sent); the safety-excerpt reveal is a
> durable row, matching what the UI and CLAIMS_MAP already claimed (was a
> rotating `logger.info`) and recording THAT it happened, never what was read;
> waitlist CSV escapes formula-injection cells and the public `source` field
> is bounded/validated; admin sign-out calls `POST /auth/logout` so a lifted
> refresh token stops working. Pinned in `tests/test_admin_audit_log.py`
> (backend 518 passed / 96%; e2e 25 passed / exit 0).
>
> **Wave 16 — auth hardening + abuse/cost limits (register C8-C11, C16-C18,
> C76-C79; C7 accepted-by-design, annotated in the audit):** login burns a
> dummy bcrypt verify for unknown emails (the early return was a ~100 ms
> timing oracle) and the lockout message is only shown to a caller holding
> the CORRECT password — a wrong guess against a locked account reads like
> any wrong guess; the password-reset link is single-use (token carries the
> token generation, redemption bumps it — a leaked URL no longer replays for
> its full hour); `/auth/verify*`, `/auth/password/reset` and `/auth/logout`
> gained the rate limits every neighbouring route already had; the public
> waitlist answers "joined" whether or not the address was already on the
> list (it was a membership oracle for any email; web copy updated);
> `ChatSend`/`OracleSend` text capped at 4000 and journal body/tags bounded
> (uncapped bodies fed the LLM prompt and Text columns); `/assessment/topics`,
> `/plans/generate` and `/goals/{id}/decompose` gained IP rate limits and
> `/voice/tts` + `/oracle/confirm` now draw on the free-tier daily quota
> (each was an unmetered provider-billed call); `/voice/stt` reads cap+1
> bytes instead of buffering the whole upload before measuring it. Pinned in
> `tests/test_auth_hardening.py` + `tests/test_abuse_guards.py`.
>
> **Wave 17 — 500s become 4xx, races upsert, the database states the
> invariants (register C19-C22, C24, C26-C28, C30-C32, C52-C53, C86-C88):**
> profile, push-token and content schemas now mirror their column sizes (an
> over-long value was a Postgres DataError → 500); timezones are validated
> against the IANA database (a typo silently moved nudges/digests/patterns
> to UTC) and regions against the crisis directory (`KNOWN_REGIONS` pinned
> against `crisis._REGIONS`), lowercase canonicalised; sleep dates must be
> plausible (±tomorrow…-2y) and a zero-minute night is refused; passphrases
> over 72 bytes are a 422 instead of bcrypt's ValueError→500; link tokens
> with garbage subjects are 400; negative `?limit=` is floored on
> moods/journal/sleep; `?platform=` is a closed set (windows was answered
> with the APNs flag); the seven check-then-insert races (signup, OTP row,
> waitlist, sleep night, habit double-tap, device token, web-push endpoint)
> handle IntegrityError by adopting the winner's row instead of 500ing;
> `insights` gained `uq_insights_user_period` (Alembic `d7e2c9a4b816`,
> deduping first) so two dispatcher workers can't double-snapshot a week;
> Stripe's signature parser treats a non-numeric `t=`/non-UTF-8 body as
> StripeError not a 500; `/content?q=` and `/admin/users?q=` escape LIKE
> wildcards via the new shared `services/textsearch.escape_like` (journal's
> local fix, promoted). Pinned in `tests/test_input_bounds.py`.
>
> **Wave 18 — web app correctness tail (register D2, D11-D12, D16, D21,
> D55-D58):** chat no longer derives a thread id client-side (it defaulted to
> the shared literal "web" until /auth/me resolved, so an early message could
> checkpoint under a different key than later ones — the server now receives
> none and defaults to the caller's user id, the Android contract); an SSE
> `error` frame mid-stream keeps the same "Try sending again" chip a
> pre-stream failure always had; chat history is fetched with `?limit=100`
> and hydration no longer auto-scrolls the page to the composer (only the
> user's own sends do); Home's "Mood this week" buckets by LOCAL day — five
> check-ins today no longer draw a week-looking line (days average, absent
> days aren't drawn, empty-state copy says "two different days"); the two
> "Anonymous usage stats" switches share one state; `paywall_view` fires
> once (from the card actually rendering, not also from the page's /auth/me
> effect); onboarding's terminal funnel event fires before the awaited
> PATCHes so closing the tab at the last screen can't erase the completion;
> the sleep post-save refetch is awaited so "Your rhythm" can't sit stale
> beside "Saved". tsc clean; e2e suite green.
>
> **Wave 19 — the operator surface tells the truth at scale (register E38,
> E41-E50, E52-E54, E59, E64-E66 + backend C33-C35 list bounds):** admin
> sign-in checks the role at the door (a valid USER credential used to enter
> a shell where every call 403s and the exit copy blamed the password);
> every admin list is bounded server-side (users/safety/content/media/
> waitlist clamp `?limit=`, safety+waitlist+nudges footers own up when a
> page is full, the waitlist CSV button says "(latest page)"); Users "Load
> more" pages by offset instead of refetching everything from row zero, and
> search also matches user ids; the Safety queue shows time-of-day (triage
> could not tell five minutes from twenty hours), a Copy-user-id action (a
> flag could never reach its account), and the resolver's EMAIL (the one
> attribution recorded rendered as a raw UUID); loading states replace the
> false "0 shown / 0 items / 0 signups" headers; the funnel panel states
> its failure instead of vanishing; content save and publish/premium
> toggles, user enable, prompt activate/revert and media clear all catch
> and say what didn't happen; content asset URLs must be http(s) or
> backend-relative (a pasted `javascript:` persisted and was served to
> every client for rendering); the Oracle tab gets its own glyph instead of
> silently wearing Overview's through the fallback; `fmtDate`'s dead
> try/catch handles Invalid Date for real; waitlist rows key by email; an
> offline media upload reads as the friendly offline copy, not "TypeError:
> Failed to fetch". Pinned in `tests/test_admin_bounds.py`; tsc clean.
>
> **Wave 20 — the safety pipeline reaches every write, and deletion means
> deleted (register C68-C71, C73-C75):** the Oracle's `log_mood` note — the
> one write where the MODEL chose the text — now goes through
> `scan_and_record` like every hand-written note (source "mood", pointing
> at its row); `POST /users/me/memory` and `POST /goals` (`why`) were
> unscanned 2000-char prose paths, now scanned (sources "memory"/"goal" —
> the write is kept either way, the scan only ADDS); a journal POST that
> scores elevated/crisis answers with the same region-aware `resources`
> block /chat and /oracle always carried (JournalOut.resources, additive and
> ignored by current clients — they CAN now drop their hand-mirrored
> hotline directory for this path when next touched);
> chat + oracle safety events point at the message they came from
> (`source_id` was always None, so the admin queue could name the risk but
> never the message); `DELETE /users/me` purges the LangGraph checkpoint
> tables the memory wipe always purged (they're keyed by thread id, so the
> account cascade never reached them — shared `_purge_oracle_threads`);
> the export adds habits + completions, program enrollments, intervention
> + pattern recommendations, devices, trusted contact and safety events
> ("a complete copy" now is). C72 (concurrent classifier) deliberately NOT
> taken: the reply's crisis suffix depends on the classifier's verdict, so
> they are not independent as the register claims — annotated in audit C.
> Pinned in `tests/test_safety_reach.py`.
>
> **Wave 21 — "today" is the user's day (register C59-C65):** new
> `app/core/localtime` (`tz_for`/`local_now`/`local_today`/`local_date`,
> junk-tz falls back to UTC for legacy rows) and every "what day is it"
> read goes through it: habit complete/uncomplete and the 7 day-dots use
> the user's day (a tick at 00:30 IST counted as yesterday); the sleep
> summary and intervention signals used `date.today()` — the CONTAINER's
> zone, neither UTC nor the user's; the streak buckets by the user's
> calendar days in SQL (`timezone(zone, created_at)::date`) so it stops
> disagreeing with the Android/iOS local-day count for every evening
> check-in east of UTC; pattern rules use local days end-to-end (the
> weekday-rhythm rule read UTC weekdays three rules below a bucket that
> converted); the weekly insight's sleep window matches its mood/journal
> window (was six nights against seven days); the program day rolls over
> at the user's midnight, not 05:30 IST; and the sleep↔mood pairing is
> UNIFIED (register C63): a diary date is the wake morning, so the night
> and the day it affects share one date — trends' +1 mapping was off by
> one and could tell the same user "no clear link" while the weekly said
> "calmer after 7+ hours". Trends' correlation test re-anchored to the
> corrected pairing. Pinned in `tests/test_local_days.py`.

## 2026-08-04 Android audit-fix waves (owner: iOS deferred by decision)

- [x] **Android redesigned data surfaces reconnected to production APIs (2026-08-10):**
  detailed Check-in now persists through `POST /moods` (including intensity and
  private note); Today's “This week” and the unchanged redesigned Weekly Insights
  screen removed the illustrative `4 / 3 / 6h 48` values and render
  `/insights/weekly` metrics. New Journal Entry already used the offline-safe
  `POST /journal` path and remains server-backed. The redesigned UI/navigation
  was deliberately preserved; this is data wiring, not a screen replacement.
  Follow-up in the same pass kept the reference visual layouts but removed
  user-data fixtures from Trends (`/insights/trends`), Patterns
  (`/insights/patterns`), Sleep Insights (`/sleep` + `/sleep/summary`), Goals
  (`/goals`, including create/decompose), and Daily Plan (`/plans/active` +
  step PATCH). Static instructions and choice taxonomies remain client copy;
  they are not user measurements and do not belong in an API.

- [x] **Wave 1 — crisis & safety cluster** (register: G crisis-region cluster,
  A16-21, B4/B14/B20/B53, H2/H4/H16): `CrisisDirectory.kt` mirrors backend
  `crisis.py` (US/CA/GB/IE/AU/NZ/IN + 112/findahelpline default; pinned in
  `CrisisDirectoryTest`); every dial surface (Crisis list, You pill+subtitle,
  Talk pill, Toolkit support card, Journal card) now follows the crisis region,
  offline-first via `crisis_region` pref mirror + device locale. CrisisScreen
  gained region row, grounding door (orphaned `crisisgrounding` reachable),
  safety-plan door, honest trusted-contact unknown state. Journal support card
  moved above recents + acts (dial pill + More support). SafetyPlan values
  survive recreation (JSON saver, pinned), fields named for TalkBack, Done CTA.
  CrisisRegionScreen no longer renders a failed read as "Auto-detect selected".
- [x] **Wave 2 — reachability** (register: A1-A6, A9, A7, A12, A24, A26, A53,
  B93, H5): notification deeplinks navigate (`routeForDeeplink` allowlist +
  `DeeplinkBus`, MainActivity reads launch intent + onNewIntent; pinned in
  NavigationChromeTest); reminder hour persisted (`Reminders.storedHour/`
  `rememberHour`, toggle + BootReceiver re-arm at the user's hour, pinned) with
  a time row + TimePickerDialog on RemindersScreen, denial feedback and an
  on-resume revoked-permission banner with a settings door; orphaned surfaces
  wired (CBT-I + MBCT doors on Programs, Body Scan card in Toolkit Settle,
  Insight Reel row on Insights, crisis grounding via Wave 1); talk/live+chat
  aliases keep tab chrome; server "breathing" chip and widget open the same
  surface; cross-tab open() uses the tab pop/save/restore pattern (no dup tab
  entries); journal/new back arrow matches system back. Still open from this
  cluster: A10/A11 duplicate-surface retirements (guidedimagery ×2,
  onegoodthing/intention) → owner call; A66/A67 onboarding notify-before-
  account sequence.
- [x] **Wave 3 — state & logic bugs** (register: B1-B3, B7-B9, B11-B13, B16,
  B42-B45): sequence/path memory games score the FULL sequence in order with a
  progress line + lit retraced prefix (was: one tap of cell one passed span 6);
  rememberSaveable sweep (Grounding step, Baseline picks, CBT-reframe answers
  via listSaver, Tipp step, ritual body-scan index, Pattern edit draft,
  PatternGlow best); BreatheEngine defaults now read the persisted
  haptics/chime prefs so ritual/tool/onboarding hosts honor the user's choice;
  BreathLoops completion no longer double-fires success haptics; WritingStep
  save errors go through userMessage (no more raw localhost text); Goals day
  dots use the locale's narrow weekday name; Trends duration units come from
  resources; Insights clamps the baseline read to 1..5 instead of crashing on
  a corrupt pref. Device-only check outstanding: hand-play the sequence game.
- [x] **Wave 4 — honest errors & dead CTAs** (register: H1, H3, H23-H26, B15,
  B21, B83): DPDP export now leaves the phone — a share-sheet button with the
  payload held in memory only, and `Session.cacheablePath` excludes
  `/users/me/export` from the pref-backed response cache entirely (pinned:
  online GET stores no cache key, offline export fails honestly instead of
  replaying stale personal data); PremiumScreen's permanently-disabled
  "Start free trial" button removed — pricing + honest note until Play Billing
  is configured; retry buttons on Trends, Insights, Programs, and every
  ContentList error; CompanionStyleScreen failed read no longer renders as
  no-selection-and-silence (null-state + error + retry, pref-safe writes);
  BreathingScreen/JournalingTool saves gained in-flight guards (rapid taps
  queued identical journal writes). Remaining from this cluster: B5 plan
  toggle race, B6 pattern-delete leaves suggestions, B17-B22 failed-read
  branches on Goals/Patterns/Search/Crisis-contact (partial: crisis + trusted
  contact done in Wave 1), H7-H22 forward-CTA additions.
- [x] **Wave 5 — Reduce Motion clocks, a11y, races** (register: B5, B6, B8
  earlier, B23-B27, B46-B52, B55, B57, B58, B70): `restingFloat` helper —
  the five gate-the-read-not-the-clock infinite transitions (mixer slider ×5
  instances, mixer hero, Toolkit ambient, featured game card, breathe
  background) now create NO transition under Reduce Motion; PremiumMixerSwitch
  meets the 48dp floor with real toggleable state semantics; SleepTimerPill
  48dp target; ChipWrap/BreathePaceControl selected-state semantics
  (selectable); BreatheSettingRow row-level toggleable (switch was nameless);
  memory-game grid cells named per-cell with Role.Button (was TalkBack-
  invisible); RitualBuilder arrows 36→48dp; PatternGlow pads announce disabled
  during the watch phase; RoundTimer carries progressBarRangeInfo + a soft
  haptic when time runs short; plan-step toggle race fixed with a mutation
  counter (stale response can no longer revert a newer toggle) + selection
  haptic; pattern "Delete everything" also clears the derived suggestions.
  Still open: B54/B56 (BubblePop semantics, recents-chip role — PickChip API
  change), B59 RTL scope, token-drift sweep (B60-B68).
- [x] **Wave 6 — failed reads honest, dead ends opened** (register: B10, B17-
  B19, B22, B28, B69, B71, B89, B90, H7-H9, H14, H17, H20-H22): Goals load
  failure no longer renders "No goals yet" (error + retry; drafts survive a
  failed add); Patterns' memories/recommendations failures say so with retry
  instead of "nothing saved yet"/vanishing, and accept/dismiss report their
  outcome; Search shows a partial-failure hint when some kinds 500; "Make
  today's plan" opens the plan it made; empty Player offers Browse sounds;
  saved Baseline offers See your insights; Grounding completion leads with
  Done; PatternGlow has a finish; Journal Read offers Write another; Home's
  settled check-in line and Sleep's "Your sleep" card open Trends; BubblePop
  under Reduce Motion respawns a static set when emptied; game answer haptics
  follow the documented vocabulary (selection/warning per round, success once
  at completion); BreathLoops Clear is two-tap armed; BreatheWhyCard holds
  still under Reduce Motion like its sibling. Deferred: H12/H13 programs
  completion journeys, H18 gratitude read-back, H19 reel ending (design
  calls), B84-B88/B91-B92 net-layer polish.
- [x] **Wave 7 — token discipline & component grammar** (register: B60-B63,
  B65-B68, B86, B88; B64 reviewed-and-kept): Grounding's step UI wears
  Ok/TextSoft tokens and its bespoke gradient CTA became PrimaryButton (role +
  haptic + 48dp for free); the Toolkit hub's near-BrandPrimary purple
  (0xFF7A5CFF vs token 0xFF7C6FF0) now IS BrandPrimary, as is
  BreathePaceControl's selected fill (was a third distinct purple); the
  imagery caution card uses the Danger token (theme-aware); game
  progress/success/warning colors use Ok/Warm tokens — the stroop/go-no-go
  color literals stay raw BY DESIGN (a "green" word must be green), and
  BreathLoops' orb phase palette is documented as a deliberate art surface
  like the mixer hero; all seven raw OutlinedTextFields (Goals ×3, Patterns
  ×2, SafetyPlan ×1 + edit) became AppTextField with real labels; onboarding's
  Welcome/Funnel CTAs carry Role.Button; Search's five catalogue reads load
  concurrently (were strictly sequential); Programs' Leave failure says so.
  Remaining token notes: games category accents (gameAccent) are deliberate
  identities, BubblePop pool gradient is art — both left; B87 title-vs-id
  enrollment match needs a backend payload check first.
- [x] **Wave 8 — timers, layout resilience, last races** (register: B30, B32,
  B33, B35-B41, B54, B87, B91, B92): breathe pacer reads haptics/chime via
  rememberUpdatedState (a mid-session toggle no longer stretches the second or
  replays a cue); ToolAmbience start() returns an ownership token so a
  disposing screen can't kill the bed the incoming screen just started;
  SoundscapeMixer gained TIMER_CYCLE + setTimer() — the sleep-timer card picks
  a target with ONE intent instead of four blind cycles; ContentList state
  keyed on kind; Trends picker is a FlowRow (720px/large-font) and a stale
  chart is marked while a new window loads; onboarding consent hints wrap
  (no "…" at the moment of consent) and StateOptionRow/game tiles grow at
  large font; unknown gameId renders words + Browse instead of a blank
  screen; GratitudeGarden per-axis slopes (the whole garden grew on one
  mod-100 diagonal); BubblePop bubbles carry name+role for TalkBack;
  enrollment matches by content_id (title only as legacy fallback — backend
  _view verified to send content_id); Insights metric rows keyed by label.
  Remaining (small): B29 imagery ticker robustness, B31 BubblePop drift perf,
  B34 body-scan pause restarts step wait, B84-B85 Session i18n + cache cap,
  B56 PickChip role, B59 RTL scope.
- [x] **Wave 10a — the three render bugs the screen-render pass surfaced**
  (register B58, B72, plus an unfiled string bug): Toolkit exercise cards render
  their level beside the duration ("3 min · Guided") — it and a `category` badge
  were passed by all 15 call sites and rendered by neither; the dead
  `ToolkitBadge` composable and the four now-unused `toolkit_badge_*` strings
  are gone (the badge only repeated the section header the card sits under, and
  `toolkit_badge_ground` stays because GroundingScreen uses it as its eyebrow).
  The games hub no longer prints `mg_subtitle` twice — it has its own short
  eyebrow (`mg_eyebrow`, en + hi) with the sentence said once in the body.
  PatternGlow's best-round line was `patternglow_best_suffix` ("  ·  best round
  %1$d"), a string written to be APPENDED but rendered standalone, so `.trim()`
  left a dangling bullet on screen — now `patternglow_best` ("Best round %1$d").
- [x] **OWNER DECISION 2026-08-05 — the Sound Mixer hero follows the theme.**
  Its deep-night gradient, edge, specks, ink, waveform and play pill were
  hardcoded hexes that survived Dawn (an "art surface", like the Sleep hero).
  They are now `MixerHero*` / `MixerWave*` / `MixerPlay*` roles in `Color.kt`:
  Night keeps byte-identical paint, Dawn gets a light lavender-to-paper wash
  with ink text. Extended the same day to the **Sleep wind-down hero**
  (`SleepHero*` roles — dusk-light panel, moon and star field retained) and the
  **Toolkit's featured Bubble-pop billboard** (`Featured*` roles — the
  generative art is unchanged; only the scrim over it flips, so Dawn lifts the
  art to a pastel wash under ink text instead of sinking it). Night is
  byte-identical on all three. **No constant-dark panels remain**; content
  thumbnails and hero ART (drawn from each item's title) stay as they are —
  those are pictures, not surfaces.
- [x] **Wave 9 — the code tail** (register: B29, B31, B34, B56, B84, B85):
  imagery ticker keyed per line (no stall window); BubblePop drift at 15Hz
  with an empty-pool skip; body-scan pause keeps the seconds already waited
  (per-step remaining, was full-step replay); PickChip gained
  announceSelection=false for the two recents ACTION chips (no more "not
  selected" lies to TalkBack); Session's user-facing failure strings localize
  (net_unreachable/net_signed_out/net_request_failed en+hi, English fallback
  keeps unit-test pins meaningful); the response cache is capped at 48
  entries with oldest-written eviction (pinned in SessionTest). **The
  register's Android CODE items are now done** — what remains on Android is:
  design-call CTAs (H12/H13/H18/H19), duplicate-surface retirements (A10/A11)
  and onboarding notify sequence (A66/A67) as owner calls, the B59 RTL device
  pass, the queued owner decisions, and the device-only checks (TalkBack
  traversal, hand-play the sequence game, Reminders time row).

## 2026-08-05 Play Store readiness pass

Prompted by "kya kya missing hai" for a Play submission. Everything code-side
that blocked an upload is now closed; see [ANDROID_RELEASE.md](ANDROID_RELEASE.md)
and `apps/android/playstore/`.

- [x] **`signingConfigs.release` in `app/build.gradle.kts`** — fed from the same
  `secret()` chain as the API keys, and only *created* when a readable keystore
  is configured, so a keyless checkout still builds green and emits the unsigned
  artifact (the "degrades without keys" rule applied to signing).
- [x] **`versionName` 0.1.0 → 1.0.0** (`versionCode` stays 1 for a first upload).
- [x] **`*.jks` added to `apps/android/.gitignore`** — only `*.keystore` was
  listed, so the file `keytool` actually emits by default was committable.
- [x] **`/delete-account` web page** (`apps/web/app/delete-account/page.tsx`) —
  Play requires account deletion reachable *without* installing the app. Linked
  from SiteFooter + sitemap. **Needs a web deploy before the URL is usable.**
- [x] **512×512 Play icon** (`apps/android/playstore/play-icon-512.png`) — 32-bit
  RGBA, rendered from the same vector layers as the launcher icon so store and
  launcher art cannot drift.
- [x] **Store listing copy drafted** (`apps/android/playstore/LISTING_COPY.md`),
  verified against `check-claims.mjs`'s banned-phrase list, with the deliberately
  omitted claim classes written down.
- [x] **ANDROID_RELEASE.md corrected** — its "2.5 MB release APK" was stale (the
  real figure is 16.6 MB after media3/Health Connect/Firebase/Coil landed), its
  permission list omitted `health.READ_SLEEP`/WAKE_LOCK/VIBRATE, and it never
  mentioned the Health Connect declaration form.
- [ ] **Feature graphic 1024×500 + phone screenshots** — the only store assets
  still missing.
- [ ] **Android Play Billing client** — not built at all (iOS/web billing is
  done). First Play release therefore ships as a free app; do not write pricing
  copy until the release that ships billing.
- Verified after the changes: `:app:assembleRelease` **BUILD SUCCESSFUL**
  (R8 + lintVital green), web `tsc --noEmit` clean, claims gate clean over 113
  user-facing files.

## Open — needs the owner's accounts/credentials (no code left to write)

- [ ] **Play upload keystore** — the Gradle config is in place; create the key
  (`keytool -genkey -v -keystore cerebro-upload.jks -keyalg RSA -keysize 2048
  -validity 10000 -alias cerebro`) and set `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/
  `KEY_ALIAS`/`KEY_PASSWORD` in `local.properties`. Back it up twice — losing it
  means the app can never be updated on Play.
- [ ] **Play Console** — developer account ($25), Health Connect data-type
  declaration form (required by `health.READ_SLEEP`), Data safety form, content
  rating. Confirm in Console whether the 12-testers × 14-days closed-testing rule
  applies (personal accounts only) and whether the target API level minimum has
  moved past 35.
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

## Done — 2026-08-03 Android Home deep polish (58-point audit, 4 commits)

`03806ead` → `08699a49`: header (rotating goal eyebrow, Library search pill,
avatar shortcut, small-hours continuity, once-per-session rise, status scrim),
banners (eased entrance/exit, offline "Send now" drain, wind-down copy+wave
medallion), check-in (earlier-mood ring, say-more bridge, 8s settle, undo
feedback, merged semantics, tap-race guard), plan hero (focus-keyed art,
1-line subtitle, evening "steps still open tonight", next-step deep link,
START chip, per-step progress bar, skeleton), rail (tap plays the item →
player, title-keyed wave art, play pill, kind meta, skeleton), doors (icons,
"Weekly insights" rename, tiered copy, toolkit recents subtitle, state-aware
order), presence (folded header, 18dp dots, today halo, LAST 7 DAYS eyebrow,
tappable, late-milestone catch-up), recents (localized display copy, row taps,
time dedup, "+N more today"), cached-first snapshot paint (verified offline),
themed refresh indicator. Point 15 (fifth mood) queued below.

## Done — 2026-08-03/04 Android Sleep deep polish (56-point audit, 3 commits)

`b8b5bda5` → S4: live header subtitle + CBT-I chip + moon shortcut + scrim;
honest hero (no fake duration, Play↔Pause state, plain TONIGHT, 220dp); check-in
(evening framing, settle line + Edit, duration preview, upsert honesty, quiet
celebration gate, unclamped HC consent, chip bleed/haptics/semantics, time
pills, press-repeat steppers, save hint); merged "Your sleep" card (chart axis +
quality tint + tap-a-bar, humanized editable diary via upsert, bedtime window,
empty-state action, milestone lines); night-aware door order + enrolled
Programs copy; sounds tap→player + All-sounds link + Sleep-timer row; guide
rows honestly dressed (muted meta + per-guide glyphs); pull-to-refresh +
cached-first snapshot + parallel reload + 640dp max-width.

Deferred from that audit (need decisions or hardware):
- [x] **DELETE /sleep/{date} backend route** — owner-scoped delete plus Android diary UI, confirmation, API helper and contract tests (2026-08-10); iOS/web UI wiring remains a client task.
- [x] **PUT /journal/{id} backend route** — owner-scoped replacement re-runs the safety scan; Android History → Entry edit/delete UI, confirmations, API helpers and contract tests added (2026-08-10).
- [ ] **You page compact density + collapsed header** (Others audit #42/#45) — owner call on the 72dp-row look before reworking PremiumNavRow/PremiumPage.
- [ ] **Talk conversation search** (Others audit #20) — needs a history surface design.
- [ ] **Talk voice-engine work** (chat audit 2026-08-04 #29-32/34): compact-orb ripple,
  in-session mic mute, full-caption view, TTS voice preview, presence debounce — all
  need VoiceEngine/CloudVoice changes, not screen work.
- [ ] **Talk page width cap on tablets** (chat audit #5) — shared Page component change;
  same bucket as the You density rework.
- [ ] **Partial text selection in bubbles** (chat audit #10) — SelectionContainer
  conflicts with the long-press copy gesture; needs a design call.
- [ ] **CBT reframe seeded from the conversation** (chat audit #22) — route arg design.
- [ ] **Chip-rail collection semantics + RTL bubble pass** (chat audit #47/#49) — device-only.
- [ ] **Sounds audit deferrals (2026-08-04)**: favourites recency order + pruning of
  renamed titles (needs a richer SleepFavs store); premium-row upsell path (needs a
  client entitlement signal); named saved mixes (backlog, sibling CustomRituals shape);
  "Activity sounds" placement (owner call — it's an app-wide setting living in the
  Mixer); loop-seam listen + server-asset supersede check (device/asset-gated).
  DONE from that audit: mix persistence, fav-kind fix, preset-tap-plays, Just-rain
  preset, duck, MediaSession callback, toggle-restore, honesty hints, token pill.
  (The `caae1caf` merge's pending mixer visual pass also cleared — verified live.)
- [ ] **Collapsing Sleep header** (audit #4) — design decision on scroll behavior.
- [ ] **Dawn→Night crossfade on tab entry** (audit #52) — needs a theme-layer transition, not screen work.
- [ ] **TalkBack traversal pass for the time-aware order** (audit #54) — device-only.

## DECIDED 2026-08-04: appearance is global — Sleep follows the chosen theme

**Owner decision (Pawan, 2026-08-04, in session): the Sleep surfaces no longer
force Night; the user's Appearance choice governs every signed-in screen.**
Implemented on every client in one commit, per the process the old rule
demanded: Android dropped `SLEEP_CONTEXT_ROUTES` + `AppTheme.forceNight` and
retired the pinning test with a pointer here; web unwrapped the Sleep page's
`.theme-night` scope and the e2e now asserts Sleep renders Dawn under
system-light; iOS already conformed (its recorded divergence becomes the
converged behavior). Signed-out/crisis/onboarding surfaces keep their Night
branding — the decision covers the authed appearance only.
History, for the record: the rule originated in a hardware finding
(full-brightness player mid-wind-down) and was removed/restored four times
before this decision; the wind-down concern is now answered by the theme
picker (Night is one tap away) rather than by forcing.

## Open — owner decisions queued by the 2026-08-02 Android page-by-page polish (waves 1–8)

The 8-wave UI/UX pass (commits `655b0cb6` → `2ad7697e`: Home, Talk, Journal, You,
Toolkit + GroundingScreen, Breath Loops pause/partial-credit, Sleep time-aware
layout, Trusted-contact field validation + reach actions) implemented the
mechanical audit points and deliberately queued these for the owner:

- [ ] **4 vs 5 moods on Home** — the check-in rail shows 4; taxonomy has 5 (cross-stack contract).
- [ ] **Merge Trends / Insights / Patterns doors on You** — three analytics doors overlap; one hub?
- [ ] **Crisis screen always-dark** — force Night on the crisis surface regardless of theme?
- [ ] **Configurable breathing rounds** — Breath Loops rounds are fixed per pattern today.
- [ ] **Home search scope** — what the Search door should actually index.
- [ ] **Journal voice entry** — dictation into entries (permissions + privacy copy needed).
- [ ] **Premium door placement** — the sheen row sits standalone on You; keep or move.
- [ ] **Trusted-contact "what gets sent" copy** — show the escalation message body verbatim
  before consent. (The consent switch stays default-OFF — decided 2026-07-13, unchanged.)
- Device-only checks outstanding: haptic feel (`Haptics.tap` on breath phase change,
  `success` on completion), TTS voice-cue quality, and a TalkBack pass — the emulator rig
  can't judge these.

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

### iOS world-class pass (2026-08-03 — STATIC ONLY, Windows host; needs one
### macOS `xcodebuild test` before shipping)
- [x] **iOS was the phantom-grant client.** `Consent()` defaulted four
  categories true, and RootView pushed `state.consent` to the server on every
  launch — so a returning user signing in on a fresh iPhone had their real
  recorded choices OVERWRITTEN by defaults nobody chose (the funnel's
  all-off reset only runs if the consent step is reached; sign-in skips it).
  Fixed with the exact guard the assessment push has always had:
  `hasConsentChoice` (set by passing the consent step or moving a Privacy
  switch), defaults all-false, decoder missing-key = not granted (journal
  keeps its umbrella inheritance), and a new `GET /users/me/consent` adoption
  on connect so a fresh device mirrors the account instead of the reverse.
  Pinned in ConsentAndErrorsTest; the funnel UITest already asserted
  "must not be pre-ticked".
- [x] **Pydantic-422 sentences now surface on iOS too** — both APIClient
  status-handling paths parse the array `detail` shape (and the JSON path
  now recognises the free-tier cap). Same fix as Android e5697f83, pinned in
  ConsentAndErrorsTest.
- Checked, clean: breathing presets (box 4-4-4-4 / color 4-2-6 / reset 4-6,
  RitualsTest pins the 4-7-8 rejection), crisis directory Tele-MANAS-first,
  claims/prices gates green over Swift copy, reminder hour is a real local
  notification.
- Respected, not "fixed": **Sleep does NOT pin Night on iOS** — a recorded
  2026-07-28 decision with a real technical argument (global-static tokens;
  subtree pinning needs the Environment-palette refactor). That refactor is
  the honest fix and stays open; it is also what would let iOS rejoin the
  cross-client pin web/Android hold.
- [ ] **macOS verification owed:** `xcodebuild test` (unit + UITests) on the
  consent-guard changes before any store build; the funnel walk and a
  fresh-device sign-in walk are the two flows to exercise.

### 2026-08-03 pull review: the craft pass (ad163877), reconciled
The drop is genuinely good (aurora depth field, BreathVoice on-device phase
narration over the ambient bed, trusted-contact clarity, premium framing on
six screens, Sleep literals → themed tokens) and is kept whole — EXCEPT its
last line: "Appearance also becomes global: Sleep and the routes it pushes
now follow the System/Night/Dawn choice instead of being pinned Night."
That reverts the Sleep-stays-Night contract for the THIRD time, and this
time the pinning test was deleted rather than argued with. Restored: the
route set, the forceNight line, and the test — with comments stating the
hardware history and the cross-client stakes (web's theme.spec.ts pins the
same surfaces; the drop made the two clients contradict each other the same
afternoon both suites were green).
- [ ] **Owner call, recorded here on purpose:** if Sleep-follows-appearance
  is genuinely wanted, it must ship on web + iOS + Android in one change,
  with the e2e pins updated — not by one client deleting its test. Until
  then the pin stands on all clients.
- [x] **Trusted-contact values are validated against their method** (found
  while emulator-testing the drop's clarity card: an adb-mangled
  "sister%40example.com" saved fine and would have failed silently at
  escalation). method is now a strict enum; email values must parse as
  email, sms/phone as ≥7-digit numbers; consent cannot be switched on over
  an empty value. Reads are deliberately unvalidated so historical rows
  stay visible — the clarity card shows the typo, the validator prevents
  the next one. Pinned in test_escalation.py.
- [x] Follow-up nit FIXED (e5697f83): Android now surfaces the server's 422
  sentence. The bug was two layers deep in Session.raw — pydantic's array
  `detail` shape was unparsed, and org.json's optString was serializing it
  to raw JSON (the exact failure the code's own comment warned about for
  objects). Fixed at the Session layer, so all 19 userMessage call sites
  benefit; pinned in SessionTest.

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
- [x] **The hero render contradicts the page it sits on.** `banner-hero.jpg` baked in a
  "3-day streak" chip (and "Rest easy, Pawan") while the cell beside it promised
  "Presence, not streaks". FIXED 2026-08-06 by dropping the render from the landing
  entirely: the v2 hero draws a token-built Today mock instead. The asset is still in
  `public/brand/` and still wrong — re-render or delete it before it is reused.
- [x] **Scroll-driven `.reveal` can freeze mid-fade under renderer pressure.** Root cause
  found 2026-08-06: `animation-range: entry 0% cover 22%` finishes 22% of the way through
  the element *covering the viewport* — fine for a 400px card, a very long way up a
  1000px-tall v2 band, so the plum CTA box genuinely sat at part opacity for most of its
  scroll. Range is now `entry 0% entry 85%`, which completes as the block finishes
  entering, at every section height.

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

### Open — from the 2026-08-06 Android Today rebuild

- [ ] **The "did not use your journal" line must branch on `plan.source`, on every client.**
      Only the RULE generator ignores the journal; the AI planner sends recent journal
      *titles* (never bodies, and only under `journal_memory` consent) —
      `backend/app/services/agentic.py::_recent_signals`. Android does this correctly via
      `heroWhyRes(source)`. The web design mock (`apps/app/app/design/today/page.tsx`) has a
      comment pinning the rule but is NOT wired yet — if it is wired without branching, it
      ships a false privacy claim. `scripts/check-claims.mjs` does not catch this, because
      the sentence is only false conditionally.
- [ ] **`heroKindFor` / `heroWorksOffline` / `heroWhyRes` have no unit tests** — they belong
      in `ScreenLogicTest.kt`, which was outside the rebuild's scope. ~15 lines.
- [ ] **No Android screen has been seen rendered.** Theme port, hex sweep and the Today
      rebuild are all unit-test + static verification only. Hindi chip wrapping in the
      hero `FlowRow` and the serif line-count at 360dp are specifically unchecked.
- [ ] **`ArtOk` is a genuinely missing token** — the "forest" guided journey needs a light
      green that survives on both grounds; `Ok` goes deep on Dawn, so it currently uses
      `Iris` (a violet) and reads wrong.
