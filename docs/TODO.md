# CereBro — TODO / Known Debt

> Prioritized output of the full-codebase review (2026-07-02), updated after the
> implementation pass the same day. Check items off as they land; re-run a review pass
> periodically. Companions: [ARCHITECTURE.md](ARCHITECTURE.md), [TECHNICAL.md](TECHNICAL.md).

## Done — component tests reach the portal, and the resolver that made them honest (2026-08-21)

**673 tests.** Three pieces of test infrastructure had to be fixed first, and
each of them was silently wrong in a way that would have made the tests pass
for the wrong reason — which is the failure this whole campaign exists to catch.

1. **The `@/` alias pointed at `apps/app` for all four apps.** Every app defines
   `@` as its own root, so a test rendering `apps/web`'s `CrisisLines` would
   have imported **`apps/app`'s** `lib/crisis` — and passed, because the two
   copies happen to agree. 56 files outside `apps/app` use `@/`. It now resolves
   against the app the IMPORTER lives in, which is the only thing that says who
   is asking. `tests/portal/Shell.test.tsx` is the proof: it imports
   `@/lib/copy`, which exists only in the portal.
2. **`vi.mock("next/link", …)` registered nothing, silently.** `next` is
   installed per app and not at the repo root, so vitest cannot resolve the id
   from a test file. The component then loaded the REAL module, whose hooks read
   a router context that does not exist under `@testing-library`, and every
   render died on *"Cannot read properties of null (reading 'useContext')"* with
   nothing pointing at the cause. `next/link` and `next/navigation` are now
   ALIASED to stubs in `tests/stubs/`.
3. **React had to be pinned to the root copy.** All four apps carry their own
   `node_modules/react`, and `resolve.dedupe` does not cover the subpath the
   automatic JSX runtime imports.

`tsconfig.json`'s `paths` now MIRROR the vitest aliases, so the typechecker
resolves what the runtime resolves rather than a plausible-looking substitute.

**What the tests themselves cover:**
- **`CrisisLines`** — Tele-MANAS first whatever order the server sends, every
  shipped number tappable, hyphens stripped from the `tel:` but kept on screen,
  `rel="noreferrer"` on the web link (the opened page must not get a handle back
  onto a page mid-crisis-conversation), and an aria-label that says a tap places
  a CALL. Plus: the landing's copy is asserted **byte-identical** to the app's
  below the header comment — the file claims to be a deliberate mirror, and the
  landing is the FIRST surface someone in crisis reaches.
- **Portal `Shell`** — the privacy wall renders verbatim on every route tested,
  is named, and offers no way to dismiss it; the topbar names an unknown route
  rather than rendering an unnamed shell; the scrim is a real `<button>` so it
  is keyboard-reachable; the menu label follows its state; and the sidebar says
  the numbers are illustrative.

**Mutation sweep: 7 more, all 7 caught** — 34 across the campaign, 32 caught,
2 proven equivalent.

## Done — component tests, and the 18+ gate Apple never met (2026-08-21)

The unit suite covered `lib/` and nothing else. Components hold real logic, and
the first one worth testing turned out to hold a real hole.

**`AuthPanel` gates account creation on an 18+ confirmation** when used outside
the onboarding funnel. Register D23 records that this used to be `!useCode`, so
a passwordless (OTP) sign-up met no 18+ moment at all "— and neither did Google";
the comment above the checkbox then claimed *"the one gate every account has to
pass now renders for every sign-up path."*

**Rendering it was only half of it.** The form paths are stopped by `required`
on the checkbox — the browser refuses to submit, so `submitEmail`'s own check
never even runs. But the social buttons are `type="button"` and never submit, so
that JS check is the ONLY gate they have. **Google had one. Apple did not.**
Clicking "Sign in with Apple" in account-creation mode with the box unticked
went straight through to `signInApple`.

Latent rather than live, because Apple stays inert until a Services ID is
configured — but the day Apple is wired is not the day to discover it was the
exception, and a comment asserting completeness made it invisible. Fixed by
mirroring `doGoogle`, and the comment now says which mechanism guards which
door instead of claiming they are all the same.

`tests/app/AuthPanel.test.tsx` walks all four doors (email, OTP, Google, Apple)
in both directions. It failed on Apple before the fix and passes after — which
is the whole proof.

**Two setup notes:**
- `@testing-library/react` needs `resolve.dedupe: ["react", "react-dom"]`.
  Without it the component resolves React from `apps/app/node_modules` and the
  test library from the root, and two copies means a null hook dispatcher:
  *"Cannot read properties of null (reading 'useState')"* on every render.
- `vi.mock` factories are hoisted above every `const`, so the mocked modules
  have to be built with `vi.hoisted` — otherwise the failure is "Cannot access
  'api' before initialization" at import time, nowhere near the assertion.

**Coverage scope widened to include `components/`, so the headline number FELL
from 97.6% to ~50%.** That is not a regression: `lib/` is still 97.6%, and a
number that excluded components was flattering. The components are the next
surface — `RitualSteps` (358 lines), `ThoughtSort`, `JourneyPath`,
`OfflineProgram`, the portal `Shell`, and the landing's `Waitlist`.

## Done — the Android write flows now actually run on CI (2026-08-21)

Fourteen of the twenty-two instrumented tests are WRITE flows, and they need a
real account against a real backend — a journal entry that is not stored
anywhere proves nothing. `BackendFixture.signInOrSkip` calls `assumeTrue`, so
with no API beside the emulator they SKIPPED and the job went green. That was
the right trade when CI had no backend. It stopped being right the moment those
fourteen became the tests that matter: **a green Android job meant eight tests
passed and fourteen quietly did not run.**

- The emulator's debug build already points at `http://10.0.2.2:8000` — its
  alias for the host loopback — so publishing the API on the runner is all it
  takes. No `adb reverse`, no build-config change.
- `docker compose up -d --build db api` before the emulator step, with
  `backend/.env` copied from `.env.example` (blank keys on purpose: these tests
  assert the keyless behaviour). Readiness is proved by an actual
  **authenticated round trip**, not a socket check — a port that accepts a
  connection but is a different service is a trap this repo has already hit.
- **`scripts/check-android-skips.py` is the gate that keeps it honest.**
  `assumeTrue` is the ONLY skip mechanism in the suite — there is no `@Ignore`
  anywhere — so any skip means the backend was unreachable and the write flows
  silently did not run again. Missing XML fails too: "no results" and
  "everything passed" must never look the same. Exercised against fixtures for
  all three outcomes (clean → 0, skipped → 1, missing → 1) before it shipped.
- Timeout 25 → 40 minutes, since fourteen tests that previously skipped now do
  real network work, and the API image has to build.

Same shape as the two path-filter holes closed this week: the job was green,
and green meant less than it looked.

**What it found on the very first clean run.** 22 tests, **0 skipped** — and one
failure: `PrivacyFlowE2ETest.a_memory_can_be_added_edited_and_deleted_one_at_a_time`
died on the server's own sentence, *"AI memory is switched off in your privacy
settings."* The test wrote a memory without granting `ai_memory` first. It had
been passing on a handset **only because the demo account happened to have
consent left on from earlier manual use** — ambient state doing the work the
test claimed to be doing. This is the same mistake already fixed once in
`ConsentFlowE2ETest` the same day; it was fixed there and missed here, and only
a freshly seeded database could tell the difference. Now it reads the current
consent, grants it, and restores it in a `finally` — the pattern the safety-plan
test in the same file already used.

**Two attempts to get the backend up, both worth recording:**
1. `docker-compose.yml` mounts `./backend:/app` for hot reload. On a runner the
   checkout belongs to `runner` and the container runs as a non-root `appuser`
   with a different uid, so the API's first act — `mkdir` of `media_root`
   INSIDE the mounted directory — is `PermissionError: [Errno 13]`. It dies at
   import and the health probe times out. Locally the same compose is fine
   because Docker Desktop's bind mounts are permissive.
   `docker-compose.e2e.yml` already avoids this by not mounting the source, so
   `docker-compose.ci.yml` does the same for the dev stack.
2. **`volumes: []` did not drop the mount.** Compose MERGES list-valued keys
   across overlay files, so an empty list means "add nothing" and the original
   survives. The override looked correct, changed nothing, and would have
   failed on CI identically. `!reset []` is the documented way. Caught only by
   inspecting the merged `config` output and the running container's mount
   count instead of trusting the file.

## Done — web unit coverage, third wave: 97.6% (2026-08-21)

**74.3% → 97.6%; 554 → 632 tests.** The four Next apps went from no unit tests
and no coverage instrumentation at all to this in one day.

- **`mixer.ts` (0 → 99.5%).** Needed a Web Audio stand-in, which was the reason
  it kept being deferred. The point of the fake is not that noise sounds like
  rain — it is that an uploaded server asset SUPERSEDES the synthesised layer
  (both playing would be two rains over each other), that the master opens at
  Android's 0.7 rather than louder, and that volumes ramp rather than jump.
  The four presets are asserted against `SoundscapeMixer.kt` by id, order and
  vector.
- **`portal/lib/api.ts` (63 → 95.6%).** Including `unknownColumns`, which is
  the safeguard that lets the portal refuse an HR export carrying a
  `diagnosis` column WITHOUT UPLOADING IT — forgiving about form ("Access
  Start", a BOM, CRLF) and strict about meaning. Plus the launch checklist,
  which must not tick "Organisation profile" while the privacy contact — the
  address a regulator or member would use — is blank.
- **`admin/lib/api.ts` (65 → 97.3%)** — logout clearing locally either way,
  `hasSession` as a real round-trip (the refresh cookie is httpOnly and
  unreadable, so a storage check would render the whole shell and then throw
  the operator out on the first request), and the multipart upload path.
- **`apps/app/lib/api.ts` (56 → 96%)** — the auth helpers, including register
  D22: only 400/401 may blame the credentials. An outage or a rate limit
  telling someone their password is wrong sends them to a pointless reset.
- **`web/lib/api.ts` 0 → 100%** (two lines, but an unset build ARG would
  otherwise produce `undefined/waitlist` on the one page that has to convert).

**Final: 632 tests, 97.6% lines, 90.8% branches. Per app: web 100%, app 97.7%,
admin 97.3%, portal 97%.**

**Mutation testing across all three waves: 27 mutants, 25 caught, 2 proven
EQUIVALENT rather than missed** — worth writing down, because "a survivor means
a weak test" is only usually true:
- `setAssets` storing `""` instead of skipping it changes nothing, since
  `start()` gates on truthiness anyway.
- Moving admin's `clearToken()` out of `finally` changes nothing, because the
  bare `catch {}` above it swallows and control falls through regardless.
- The one REAL survivor was mine: "ramps the master" asserted that SOME node had
  ramped, and the per-layer ramps in `applyVolumes` were carrying it, so a
  mutant that jumped the master straight to `.value` still passed. Now asserted
  on the master node specifically, and on the target value. That is the whole
  argument for mutation testing — the test read fine and proved nothing.

## Done — web unit coverage, second wave (2026-08-21)

**49.5% → 74.3%; 442 → 554 tests.** Six more modules, chosen because each one
carries a promise that fails silently:

- **`analytics.ts` (0 → 96%).** Every rule in its header is a privacy promise
  invisible on screen: nothing fires before consent, no bearer token rides
  along, the id names the install and never the person, opt-out is honoured,
  and a blocked `localStorage` degrades to sending nothing rather than
  crashing a screen. Also pins the event vocabulary against the backend's
  `ALLOWED_EVENTS` — unknown names are dropped server-side, so a rename here
  is a quietly incomplete admin funnel, not an error.
- **`onboarding.ts` (0 → 100%).** Private by default (nothing pre-ticked —
  consent must be an action); the consent deep-merge, so a draft saved before
  `model_training` existed still yields `false` and not `undefined` (a privacy
  bug and an uncontrolled-input bug at once); `applyOnboarding` best-effort per
  call. `STEP_NAMES` is checked against `metrics.ONBOARDING_STEPS` for
  membership AND order — the admin funnel joins on those strings.
- **`oracle.ts` (0 → 100%).** The decisive one: a frame split across network
  reads must still parse. A malformed frame is skipped rather than killing the
  stream, because a dead stream mid-reply reads as the companion refusing to
  talk to you.
- **`voice.ts` (0 → 100%).** The microphone track is stopped when a recording
  ends — leaving it live keeps the browser's recording indicator on, which on
  a page about privacy is the worst thing to get wrong and is invisible in a
  screenshot. Plus: the object URL is revoked so spoken replies do not leak for
  the life of the tab, and `/voice/status` failing means NO microphone rather
  than a button that fails when pressed.
- **`push.ts` (0 → 100%).** Unsubscribing locally happens even when the server
  call fails — otherwise someone who switched notifications off keeps getting
  them. Also the base64url → bytes VAPID conversion, which fails opaquely
  inside the browser long after the toggle has flipped.
- **`portal/lib/api.ts` (0 → 75%).** The separate refresh key is asserted
  against `apps/app`'s: an administrator is very likely a member too, and a
  shared key would mean signing out of the portal signed you out of your own
  wellbeing account. Also 403-is-an-answer-not-an-expiry, and single-flight
  rotation — three widgets loading at once must not race three refreshes,
  since a rotated refresh token is single-use and the losers sign you out.

**Mutation-swept again: 11 more deliberate breakages, all 11 caught** (19 across
both waves). Including: firing funnel events before consent; attaching a bearer
token to an anonymous event; pre-ticking a consent category; shallow-merging the
draft; dropping a split SSE frame; leaving the microphone live; enabling voice on
a truthy-but-not-true flag; keeping notifications on when the server refuses;
sharing the member app's session key; and racing three token rotations.

**Still uncovered:** `apps/app/lib/mixer.ts` (272 lines, needs Web Audio fakes)
and the untested half of `apps/app/lib/api.ts` (the auth helpers below `api()`).

**A vitest trap worth knowing.** A `vi.fn()` whose implementation throws — or
returns a rejected promise — has that error recorded and re-surfaced as an
unhandled one, failing the test even when the code under test catches it and
returns normally. Proven by calling the function directly: it returned `false`
and threw nothing, while the same assertion through the spy failed. The fix is
to throw from the mock MODULE (via `vi.hoisted` state) instead of from the spy.

## Done — the four Next apps get unit tests at last (2026-08-21)

Answering "what is the test coverage of web and admin" honestly meant saying
there was no number, because nothing measured one: **no unit tests and no
coverage instrumentation in any of the four apps**, with 71 Playwright tests as
the entire safety net for four deployed surfaces. That was the largest gap in
the repo. Closed:

- **Vitest + jsdom, one runner at the repo ROOT** rather than four per-app
  setups. The `lib/` modules are plain TypeScript, only `apps/app` uses the
  `@/` alias, and a single coverage number for the whole web surface is the
  point of the exercise. Tests live in `tests/`, deliberately NOT beside the
  sources, so `next build` and the production images never see them (verified
  by rebuilding the app image — the Dockerfile copies only `apps/app`, so the
  root lockfile never enters the build context).
- **442 tests across 11 files; coverage 0% → 49.5%** overall, `apps/web/lib` at
  96%. Targeted by risk rather than by line count: the offline write queue, the
  `?next=` open-redirect allow-list, both hand-copied crisis directories, the
  DPDP consent notice (13 languages × 6 categories), the session and error
  mapping in the app AND admin API clients, the portal IA + privacy-wall copy,
  `todayHero`, `pageMeta`, `theme`, `appUrl`, `social`.
- **Mutation-swept, because a suite that cannot fail is decoration.** Eight
  deliberate breakages, all eight CAUGHT: hiding a 4xx behind "saved, will
  sync"; allowing `//evil.com` through the redirect guard; flipping the hero
  branch order so a slow fetch reads as "you have no plan"; dialling a number
  with its display hyphens; stamping JSON over a FormData upload; softening the
  privacy-wall sentence; making the plan-source check case-sensitive so "AI"
  claims the wrong privacy; reporting a dead admin backend as bad credentials.
- **Cross-stack contracts now checked rather than trusted.** `todayHero`'s two
  provenance sentences are asserted word-for-word against Android
  `strings.xml`; the two crisis directories are asserted against each other;
  `PERSONAL_KEYS` in `api.ts` is asserted to still list the outbox key that
  `outbox.ts` writes to (neither module can import the other's constant, and
  the failure is a shared browser posting one person's check-ins into the next
  person's account); every portal sidebar href is asserted to have a
  `page.tsx` on disk and a `PAGE_META` title.
- **Wired into CI** as a step in the existing `web` job, and `tests/**`,
  `package.json`, `package-lock.json`, `vitest.config.ts` were added to the
  `clients` path filter — the same lesson as `e2e/**` last week: a filter that
  omits the tests hands you a green tick meaning "nothing ran". Root
  `tsconfig.json` added so `tsc --noEmit` typechecks the tests the way all four
  apps are already typechecked.
- `apps/app/lib/todayHero.ts` said "there is no unit-test runner in apps/app,
  so unlike the Android twin these functions are only covered by e2e". That
  sentence is now false, and was updated rather than left to rot.

**Still uncovered and worth a later pass:** `apps/portal/lib/api.ts` (409
lines), `apps/app/lib/mixer.ts` (272, needs Web Audio fakes), `analytics.ts`,
`onboarding.ts`, `voice.ts`, `push.ts`, `oracle.ts`. `portal/lib/mock.ts` is
excluded from coverage on purpose — 500 lines of fixture data for screens whose
models do not exist yet, so measuring it would flatter the number without
testing anything.

## Done — the last four uncovered web surfaces (2026-08-21)

Re-ran the route count across all four Next apps afterwards, which is the only
honest way to answer "have you tested everything". It found four more surfaces
reached by nothing, and each one was worth more than a smoke check:

- **`/delete-account`** — the URL pasted into the Play listing under App content
  → Data safety. In the sitemap, in the footer, tested by nothing: a store-listing
  dependency that could 404 or silently lose its `mailto:` and no one would know.
  Now asserts both routes out (in-app path and the email one for someone who has
  already uninstalled), the subject line, the 30-day commitment, and BOTH honesty
  headings — what gets deleted and what does not.
- **`/terms`** — clause 1, "Wellness, not medical care", is what the App Store
  review and the whole "companion, never clinician" position rest on. A rewrite
  that softened it now breaks a test.
- **`/sleep/ritual`** — the last authed route with no test. Walks all four steps
  to Goodnight, and pins the promise printed under the brain-dump textarea:
  *"This stays on your device and is never sent anywhere — unless you choose to
  save it."* It records every request the page makes and asserts the typed words
  appear in none of them. **Mutation-checked**: clicking "Save to journal" makes
  it fail naming `POST /journal`, so the detector is real and the promise holds
  in both directions.
- **Admin → Oracle** — the one tab of ten no test opened, and the one most likely
  to break in the configuration CI actually runs: four admin reads with
  `ORACLE_ENABLED` unset and no LLM key. "Everything degrades without keys" is a
  hard rule, and an operator page that errors when a feature is merely off is the
  most ordinary way to break it. Asserts Agent reads **Off**, both tables render,
  and `.state` — the shared error box for every failed admin read — appears zero
  times, which catches whichever of the four calls regresses.
- Also folded **Terms** and **Delete your account** into the footer-link test,
  which had been walking six of the eight footer destinations.

Counts after: **web 11/11, app 30/31, admin 10/10 tabs, portal 36/36**; the full
Playwright suite is **71 passing**. The one route left uncovered is `/design`,
deliberately: it is scaffolding outside the `(authed)` group, mock data, no
network, unreachable from the signed-in app, and its own header says "nothing
here proves the real screen works". Testing it would be testing the scaffolding.

**The stale-image trap bit again** — `docker compose run --rm e2e` runs the tests
BAKED INTO the image, so the first run of these reported 24 passed, which was
the old count. The test count is the tell; `build e2e` first.

## Done — web write-path e2e, and the vanishing plan tick (2026-08-21)

A route-coverage count found four `apps/app` routes reached by no test — `/safety-plan`,
`/plan`, `/goals`, `/programs` — every one of them a WRITE. Covering them found a real
backend bug, which is the entire argument for covering them.

- **`GET /plans/active` minted a plan per concurrent caller.** It creates one when the
  account has none; concurrent readers all saw none and all generated, and because
  `generate_plan` deactivates whatever it finds, the last commit won. Measured: **six
  parallel reads → six plans, one active.** The symptom is not stray rows. A client
  holding a loser went on ticking steps against a plan the server no longer called
  active — `PATCH /plans/steps/{id}` answered **200**, the checkbox went green, and the
  next load showed a different plan with nothing done. Progress silently evaporated.
  Fixed with a per-user `pg_advisory_xact_lock` plus a re-check under it, so late
  arrivals adopt the winner instead of racing it — no migration, released with the
  transaction. Pinned by `tests/test_plan_concurrency.py`; **both tests were confirmed
  to fail with the lock removed**, and six parallel reads now yield one plan.
- **Found by the browser, not by review.** `app.spec.ts::ticking a plan step reaches the
  server` failed on its first run and stayed failed — PATCH 200, then `/plans/active`
  reporting nothing done. Two wrong diagnoses came first (a stale login, then the API
  itself), both ruled out by a control on the dev stack showing the same plan id across
  a second login. `app.spec.ts` is now **16 tests, all passing**.
- **Latent, NOT fixed: `GET /recommendations/mine` seeds on read** and dedupes by
  reading existing slugs first — a read-then-write with no unique constraint behind it
  (`recommendations` has plain indexes on `user_id`/`practice_slug`, nothing unique).
  Tried to reproduce a duplicate: **three rounds of twelve parallel reads, all 200, two
  rows and two slugs every time.** So it is a theoretical window, not a demonstrated
  bug, and it is recorded rather than locked — adding a lock for an unreproducible race
  buys nothing measurable. If duplicate suggestions are ever reported, start here.
- **The admin excerpt GET also writes** (an audit row per read) and that is correct:
  two reads of someone's private words *should* be two log lines.

## Done — V2 device walk 1 (2026-08-15 night, OnePlus CPH2681, live backend via adb reverse)

Fresh install → full V2 walk, screenshots in the session scratchpad. **Verified on glass:**
the 4-step funnel (Welcome → Honesty 1/4 → Privacy 2/4 with ALL SIX switches OFF →
check-in 3/4 with honest tiles → Guest 4/4 → straight to Today); the new Today (hint chip,
fused card, Your-day rows incl. evening prompt, Quick helps, This-week empty-state card,
guest one-liner — ~1.5 screenfuls); tab bar Today·Sleep·Talk·Journal·You with the
crescent; Sleep Tonight page + doors + "Your sleep" details page with back link; Talk's
new top-bar shield; the trimmed You. **Six walk defects found, five fixed + re-verified
same night, all gated (`:app:check` REAL_EXIT:0):**
1. **Guest morph dead (critical)** — a guest's mood tap error'd out and the card never
   became the step; now a guest-gate failure still morphs ("Anxious — noted · Undo") with
   the honest not-saved line. Verified: guest tap → grounding step card.
2. "Overwhelmed" clipped in the compact grid — root cause was width, not type
   (labelSmall is the tracking-wide eyebrow role and made it WORSE); grid is 2-across now,
   all labels complete at 720px.
3. Disclosure CTA said "Continue to first check-in" but lands on consent → "Continue"
   (EN + new HI).
4. Sleep tab root wore a back arrow → brand mark (onBack dropped at registration).
5. "Your sleep" guest load-failure showed a retry that can never work → `GuestSignInCard`.
6. NOT yet fixed: Today's status line ghosts under the translucent top bar on scroll —
   pre-existing (audit-K "hero/app-bar ghosting"), stays on that list.
Note: the installed debug build points at localhost:8000 — off-USB it runs local-first
with the offline line (by design); rebuild plain `assembleDebug` before handing the phone
to anyone expecting live sync, or sign in over adb reverse.

**Signed-in walk (same night, smoke account over adb reverse) — all green:** auth flow
(the IME Done key submits; field drafts survive a Gboard-settings detour via
rememberSaveable); check-in round-trips the server ("Earlier · Good · Clear" on reload);
the hero renders a REAL AI plan step ("Evening Relaxation") with the ai-path provenance
sentence and — wired during the walk, it had been missed in V2-b — **"· STEP 2 OF 3" in
the eyebrow** (tappable → plan); program row shows "Sleep Reset · Day 7 of 7"; presence
dots render with today's halo; signed-in You confirms **no Premium row for free
unsponsored** + danger-tinted Delete; sign-out dialog honest, returns to Welcome.
Nameless-account greeting correctly falls back to the friend line (not a defect — the
smoke profile has no name). Final `:app:check` REAL_EXIT:0. Phone left at fresh Welcome
on the new build.

**V2 visual-language pass 1 (owner: "why have you not followed the design", 2026-08-15
night):** honest gap named — structure matched the approved prototype, the visual layer
was partial, and the phone's light mode showed Dawn while the prototype is Night.
Shipped + Night-verified on device: **the orb** (FocusCard `orb = true`, drawn from the
section accent, both themes safe) glows on THE CARD; **mood-chip icon wells wear each
mood's hue** (14% wash); **`pastel` FocusCard de-hexed** — the hardcoded light stops
(0xFFFFE1D4/0xFFE0C9EC) washed Night's card out silver and sank "more options →";
now `AccentSoft/FieldFill` tokens, both palettes gated by ContrastTest (`:app:check`
REAL_EXIT:0). **Visual pass 2 (2026-08-16, Night-verified on device):** Sleep's night-blue world
(`Accent.sleep` Periwinkle→Cyan — the animated aurora shifts blue on the Sleep tab);
**greeting name defect fixed** — `today_greeting_format` had DROPPED %2$s, so no
signed-in user ever saw their name; the format carries the name now, greetings went
lowercase-calm, and `today_friend` carries the gentle guest line (EN+HI); ask-card
title padded clear of the orb.
**⚠️ PRIVACY DEFECT found & fixed on this walk:** `home_snapshot`/`sleep_snapshot`
(+ toolkit_recent, milestone, banner-dismissals, talk_crisis_sticky, mixer_state,
consent_sync_failed) **survived sign-out** — a fresh guest was greeted by the previous
account's NAME and last check-in line. On a shared device in a family-stigma context
that is the exact leak the privacy posture forbids. `Session.signOut()` now sweeps
`PERSONAL_PREF_KEYS`; pinned by `AuthFlowTest.signOut_clears_the_leaving_persons_
snapshots`; e2e-verified on device (sign in → out → guest sees only the gentle line).
**Visual gaps still open (V2-e/f):** This-week mood sparkline (needs an owner call on
the honest mood→height mapping — charts stay sentences-first); You "Your month"
presence grid; journal mood-pills; richer nav active-pill treatment.

- [x] **V2-e part 1 (2026-08-16): the dead-code sweep + in-context reminders.**
  Onboarding: the six retired steps are DELETED, not parked — `OStep` is seven
  entries in funnel order (AnimatedContent direction derives from ordinal), their
  when-branches, `ResetStep`, `OnboardingFeatureCard`, the `language`/`notify`/
  `resetDone` vars and the whole Notify machinery (NOTIFY, `reminderHourFor`,
  `applyReminderChoice`, permission launcher) are gone; `funnelStepIndex/Progress`
  hold only live steps. Today: `FoldSection`, `ContentRail`, `railKindLabel`,
  `todayExtra` deleted (`railKindFor`/`artKindForTitle`/`checkInsToday` stay —
  tested pure fns for Explore's future rail). GuidedTour: overlay + stops deleted;
  `TourState` survives as the hint's memory (briefly swept by an over-greedy cut,
  caught by the compiler, restored). Routes: `talk/live`, `talk/chat`, `dailyplan`
  deleted from the graph + bottom-bar set; `NavigationChromeTest` now pins the
  aliases STAY dead; `RouteReachabilityTest`'s excuse list emptied; the retired
  NOTIFY test removed with its subject. **Reminders ask lives in context now**: an
  InfoBanner on Today after a check-in lands, once ever, doors to Settings →
  Reminders (EN+HI). "Toolkit" is renamed **"Practices"** (EN+HI). Gates:
  compile ×3 + full `:app:check :app:assembleDebug` REAL_EXIT:0; morph re-verified
  on device. Noted for later: `loggedMood` is remember-scoped, so a tab hop
  returns a GUEST to the ask card (pre-existing; signed-in users get the marked
  chip from the server row).
- [x] **V2-e part 2 (2026-08-16): the one-per-behavior kill + You merges.**
  **Practices trim** (device-verified): zenripples, patternglow, bodyscan, gratitude
  and sounds cards left the hub; Box+Reset became ONE "Breathing" card (breathe/box
  with no start pattern IS the pattern picker). The hub is now Ground (5-4-3-2-1 ·
  Bubble pop featured · Mindful Games door) / Breathe (one) / Reframe (CBT · TIPP) /
  Settle (imagery · ritual) / support — ~9 doors from 15. **Games.kt DELETED whole**
  (PatternGlowScreen, ZenRipplesScreen, the orphaned GratitudeGardenScreen + helpers
  + routes + RippleBrightnessTest + the flowerFor pin): the registry's pattern-recall
  / color-tap / still-point are the one implementation each. `toolkitRecentLabelRes`
  declines the retired routes (pinned — a stale recent-chip pref hides, never
  crashes). bodyscan + gratitude keep their Calm-now doors (RouteReachability green).
  **You merges**: legal trio (policy · export · delete, danger tint kept) moved INTO
  Privacy & memory under the Legal header; appearance+language = ONE row
  ("Appearance & language · Night · English") with the language door inside the
  Appearance screen. You is ~11 rows for a typical free user (from 23).
  **Explore route**: decision recorded — stays registered, deeplink-only, excused in
  RouteReachability; final shape follows the search decision (V2-f or later).
  Gates: full `:app:check :app:assembleDebug` REAL_EXIT:0 ×2; Practices + morph
  re-verified on device. Note: the ask-card title now wraps to two lines beside the
  orb at 720px — acceptable (matches the greeting's rhythm), revisit if it grates.
- [x] **V2-f (2026-08-16): copy diet + Sleep-insights localization — the last wave.**
  **Copy diet** (Audit-L L4): the 18 `*_why` provenance lines are one calm sentence
  each, author-year citations out of the UI (`SalvagedToolsTest`'s pin FLIPPED to
  enforce the diet: 30–120 chars, no parens); `journal_private_body` +
  `privacypolicy_private_body` compressed with every disclosure kept (safety-scan
  via provider, reviewer exception + audit trail); 4 error essays → one line saying
  what happened and what is safe; `humansupport_intro` / `toolkit_tipp_subtitle` /
  `offline_disclaimer` trimmed; 6 HI mirrors. `scripts/check-claims.mjs` green
  ("No unbacked claims across 206 user-facing files").
  **Sleep-insights localized** (Audit-L L6): `ReferenceSleepInsightsScreen`'s ~20
  hardcoded English literals → 18 new `si_*` strings EN+HI; window state now holds
  locale-free ids (`week/month/3m`) with chips rendering localized labels; the
  bar-chart's last raw hex pair → `Cyan→Periwinkle` tokens. Device-verified EN
  **and HI** (per-app locale): hero/chips/stats/"record, not a diagnosis" branch
  all render; window switching works; ≥3-nights honesty branch correct per window.
  Gate: full `:app:check :app:assembleDebug` REAL_EXIT:0. `ConsentNotice.kt`
  literals (L6's other half) remain open below.
  ⚠️ **New debt found — Robolectric AppNotIdle flake**: three consecutive full-suite
  runs failed with 14–26 `AppNotIdleException`s ("Compose did not get idle… 4M
  attempts") across DIFFERENT previously-green Compose test classes, same code
  green before and after (isolated classes always pass; full suite passed clean on
  the 4th run, 27s). Cross-test contamination — some run leaves an infinite
  composition/idling condition that poisons every later `createComposeRule` class
  in the JVM. Not reproducible on demand; if CI hits it, rerun once and treat a
  repeat as the signal to bisect (execution order is recoverable from the
  test-results XML timestamps).

## V3 SHIPPED — chat-first + proactive (2026-08-16, uncommitted)

All six waves implemented, gated (`:app:check :app:assembleDebug` REAL_EXIT:0,
517 tests, coverage 96.17%, claims gate green across 207 files) and device-walked
in **both themes**.

- **V3-a IA shell**: tabs 5→3 (**Home · Chat · Sleep**), app **opens on Chat**
  (`startDestination = Tab.Talk.route`). You → the **gear** in every tab root's
  top bar (`CereBroTopBar.onSettings`); Journal → a chat tool + a room doored
  from Home (route kept, lights Home). `you`/`reminders` leave the bottom-bar
  set (settings rooms are full-screen pushes). NavigationChromeTest re-pinned
  (`theTabsAreTheV3Three`, `youAndJournalSurviveAsRoutesNotTabs`); BottomNavImeTest
  labels updated.
- **V3-b Home**: journey hero (greeting · presence sentence · program day ·
  progress bar · Tonight pill) + **Today's care** (progress ring, ≤3 rows, the
  plan's next step with its honest provenance) + mood card (6 wire moods, week
  strip) + **sleep graph** (7 day-slots, newest solid) + quiet-days
  re-engagement + "what CereBro remembers".
- **V3-c Chat core**: the companion **speaks first** — deterministic opener (no
  LLM): morning asks about sleep and **logs the night from chat** (the form is
  no longer the only path), then the mood ask, whose answer earns a
  **next-best-action card**. The **＋ tools tray** carries 8 tools inside the
  conversation.
- **V3-d honesty + ladder**: **ask again / this didn't help** under every reply
  the user asked for; a **middle escalation rung** (`soundsHeavy`) between a
  normal reply and the full crisis banner — warm, one pathway (Tele-MANAS),
  dismissible, and **suppressed whenever the server's crisis scan already fired**
  (verified on device: typing "hopeless" produced the server's Call-14416 card
  and the rung correctly stood down).
- **V3-e proactive**: notification **quick actions** (Check in → `QuickLogActivity`,
  a translucent dialog that logs a mood **without opening the app**; Open →
  the app), **one nudge a day** and **quiet hours** (default 22–07, wrapping
  midnight, same-hour = quiet all day) — both enforced in `shouldPost` and
  pinned; Settings gained a quiet-hours picker; the test-nudge button passes
  `force = true` so an explicitly requested test always arrives.
- **V3-f**: ~75 new strings EN+HI, `CompanionFirstTest` + Reminders pins, full
  gate, device walk.

**Reference-fidelity pass** (owner: "compare each component — icons, graph,
quick check-in, widget, theme", ref `~/Downloads/Archive/complete.html`):
themed hero (**Dawn now wears the reference's peach→lilac pane with serif
italic greeting**; Night keeps deep plum), mood tiles fill with the **mood's own
hue** when chosen (OnPrimary ink), circular accent-mist **icon wells** on care
rows + tool tiles, the **progress ring**, the **NBA card** rebuilt as an
accent pane + solid icon badge + one full-width pill, sleep graph in the
reference's bar language. Deliberate divergences recorded below.

**Deliberate divergences from the reference** (each is a CereBro rule, not an
oversight): no per-message "Safety checked" chip (it would claim live scanning
under every reply — our one persistent AI-disclosure pill is the honest form,
and the crisis scan announces itself when it fires); **chat is never paused**
when a safety service is unreachable (the reference disables its composer —
ours must never block, only add support); no streak/percentage "consistency"
score (presence framing: sentences, never scores); quick-log never renders over
the lock screen (family-context privacy, §9).

**The four deferred items — all CLOSED 2026-08-16** (owner: "why are u asking
for approval, instead do all of them without"):
- [x] **Mood-sparkline mapping — RESOLVED by refusing the mapping.** A sparkline
      needs every mood to have a height, and "Anxious" is not objectively lower
      than "Tired"; inventing that order is precisely the scoring this product
      refuses. Trends now carries **"Which feelings showed up"** — per-mood
      **counts** with share-bars ("2 check-ins in the last 30 days — counted,
      never scored"), the reference's moods-detail component. The 1–5 ease line
      above it stays the SERVER's own measure, gap-broken and gated on
      `enough_data`. Pinned: `moods are counted, never scored`.
- [x] **You "Your month"** — a 30-dot presence grid + "You showed up on N days
      this month". Presence framing only: no streak, no percentage, no gap
      called out, nothing resets. Pinned: `presence month counts days shown up
      and nothing else` (two check-ins on one day = one lit dot).
- [x] **Journal mood pills** — the feeling chosen in the composer now also rides
      as a `mood:<wire>` **tag** (existing `tags` field, no migration), and the
      entry rows wear it as a tinted pill. It shows **only what the writer
      picked** — never a mood inferred from the words, never one borrowed from
      that day's check-in. Unknown/hand-written tags render no pill rather than
      raw text. Pinned: `a journal mood pill shows only what the writer chose`.
- [x] **`ConsentNotice.kt` localization — CORRECTLY NOT DONE.** The item was
      mis-scoped when written. That file holds the DPDP §5(3) notice in **13
      languages at once**, and the requirement is that a Hindi speaker can read
      the Hindi notice *whatever the app locale is*. Moving those literals into
      `values-xx/` would make each notice visible only in its own locale and
      **break the legal requirement**. The literals stay in code by design (the
      file says so; it is a hand-duplicated cross-stack contract with web/iOS).
      The genuinely open localization work is the remaining ten Eighth-Schedule
      languages + professional review — owner items, listed under audit L6.

### V3 follow-up (2026-08-16): demo data + the icon pass

- [x] **A pre-filled demo account for testing.** `pawan@cerebro.app / demo12345`
      now boots with a month of plausible history — 19 check-ins across 30 days,
      12 sleep nights, 5 journal entries (each carrying a `mood:` tag, so the new
      pills are visible), an active 3-step plan with one step done, and a Sleep
      Reset enrolment on day 4. `backend/app/seed.py` `_seed_demo_journey`,
      behind `SEED_DEMO_DATA` like the demo password itself. **Idempotent via a
      marker** (`MoodLog.trigger = "demo-seed"`), not via "does this account have
      data" — a demo account collects stray taps the moment anyone opens it, and
      a presence check would then refuse to seed forever. Seeding only INSERTS,
      so a tester's own rows are never touched. Note: the api image is baked, so
      changing the seed needs `docker compose up -d --build api`, not `restart`.
- [x] **Icons that carry information** (owner: "not used relevants and proper
      icon"). Home drew a **calendar beside every plan step**, so "Nature Walk"
      wore a date glyph. New `stepIcon(symbol, title)` in Common.kt resolves in
      three passes: the backend's own symbol vocabulary (`services/agentic.py`
      `_STEP_LIBRARY`: wind, book, moon.*, bell, leaf, brain, sparkles, target,
      mic, person.*, heart, figure.walk…), then the step's TITLE (the AI path
      names things in plain words while its symbols are unpredictable), then a
      neutral gentle default. Pinned by three tests including the "Nature Walk"
      regression. Plan-screen numbered thumbnails kept deliberately — order is
      real information in a plan.
- [x] **Two defects the demo walk exposed**, both fixed:
      - Quiet-hours card ~600dp tall with no visible time — its label Column had
        no `weight`, so the long hint squeezed the value to zero width and
        Compose wrapped it one character per line.
      - **The Journal room was unreachable before 17:00.** V3 dropped the Journal
        tab and left only the evening prompt, so a whole feature sat behind a
        clock. Home's care card now carries a permanent "Your journal" door
        (the evening prompt still replaces it after 17:00 — write, then read).

### V4 (2026-08-16): chat redesign + live voice mode

**Chat is the flagship, so it now carries the least chrome.** Removed from the
thread: the top **AI-disclosure card** (the screen had THREE disclosures — that
card, the line above the composer, and the periodic sheet; the composer line is
now the single persistent one, and it is tappable for the full points, which is
what design §8 actually asks for), the full-width **save-to-journal row**, and
the **"Quick SOS reset"** chip (the ＋ tray already carries Ground and Breathe,
and the crisis shield is in the top bar — one implementation per behaviour).
The two thread actions moved INTO the tray, where "what can this conversation
do" is already the question: **Save to journal** and **Start fresh** appear
there only once a conversation exists. Net: opener → bubbles → suggested
activity → composer, with nothing competing.

**Live voice mode, properly.**
- `VoiceEngine` now streams **partial results** (`EXTRA_PARTIAL_RESULTS` was
  off and `onPartialResults` was an empty override), exposed as `partial` and
  cleared on every resolution — so the live screen can show you **your own
  words as you speak them**. Empty on the cloud path by design (Deepgram
  transcribes the whole take server-side), where the screen falls back to the
  companion's words.
- New `VoiceWaveform`: five bars driven by the real mic amplitude. It answers
  the only question a listening screen is asked — "is it hearing me?" — and it
  is never a decorative loop: Reduce Motion holds a static resting shape.
- Two defects found by walking it: the overlay was **translucent** (0.97) so the
  transcript ghosted through and it read as a dialog over the chat; and the
  **tab pill drew on top of a live call**, one tap from silently abandoning the
  session — the overlay lives inside Talk and cannot cover Scaffold chrome.
  Fixed with `VoiceSessionState.active`, which `navVisible(route, imeOpen,
  voiceLive)` honours exactly like the keyboard rule. Pinned by
  `aLiveVoiceSessionTakesTheWholeScreen`; cleared on dispose so a mid-session
  tab switch can't strand the tabs hidden.

**V4 craft pass (same day, owner: "still not perfect").** Four things on the
flagship screen were broken rather than debatable, and every one of them was a
measurement failure — copy written without checking the space it had:
- the top bar asked **"How are you feeling today?"**, a 25-character question in
  a landmark slot, which ellipsized to "How are you feeling…" beside the gear
  and the shield. The bar names WHO you are talking to now (**CereBro · here
  with you**); the question belongs in the conversation, where the companion's
  opener already asks it.
- the composer was **[＋ 44][field][mic 44][send 52]** with three 8dp gaps —
  164dp of chrome on a 360dp screen, leaving the field too narrow to fit its own
  placeholder, so "Say what's on your mind…" **wrapped onto two lines inside a
  chat composer**. Mic and send moved INSIDE the field's trailing slot (the
  reference's inputpill), and the placeholder became "Message CereBro…".
  `SendButton` gained `compact` (40dp) for in-pill use.
- the **orb floated above the thread** as a small disconnected circle over the
  first bubble — decoration, since the composer's mic already carries voice. It
  now renders only when it IS the interaction: an empty screen, or a live turn.
- the **"Try together" label cost ~90dp inline** with its chips, which pushed
  the third offer off the right edge on every 360dp phone. The chips became
  verbs (Reframe · Breathe · Ground) and the label moved to the row's
  `contentDescription` — screen readers keep the grouping, the layout stops
  paying for it. (The label was inline deliberately, so a scroll fold couldn't
  strand a heading over nothing; removing it retires that problem too.)

### V5 (2026-08-16): the companion follows up, and the thread gets a face

Owner: "still not perfect ui and ux, use more tips and tricks, also it should be
proactive and user friendly." The proactive gap was real — the companion spoke
first **only on an empty thread**, then went silent for the rest of the app's
life, so every later visit opened on a dead transcript.

- **Follow-ups on real events** (`followUpOwed`, pure + pinned). Opening a
  suggested activity from chat sets a pref; on the way back the companion asks
  **"you tried that a moment ago — how did it land?"** with one-tap replies —
  the only place in the app that ever asks whether a suggestion helped. A gap
  of ≥3h earns a **welcome-back** offer instead. Everything else earns silence:
  a companion that greets you each time you glance at the tab is a nag, and an
  empty thread stays the opener's job so a first run is never greeted twice.
  Deterministic — no LLM key needed; an unparseable timestamp keeps it quiet
  rather than guessing.
- **Quick replies**: the canned ones ("it helped", "why that?", "not now") are
  answered on-device — spending a network turn to get a vaguer version of the
  honest answer would be worse. The composer is never the only way forward.
  They obey the existing one-rail rule: the generic Try-together offers stand
  down while quick replies are up.
- **Avatars**: the companion's own brand orb beside the LAST bubble of each of
  its runs (earlier bubbles keep the width via a spacer, so a three-bubble
  answer doesn't stamp three faces down the margin). A first attempt drew a
  two-colour tinted disc, which read as a broken image at 26dp — it uses the
  real `BrandMark` now, the same orb as the top bar and the splash.
- **"Start fresh" left the presence row** for the ＋ tray, where it already
  lived beside "Save to journal" — four links edge to edge was the last chrome
  above the thread.
- **One dismissible tip** ("Tap ＋ for check-ins, breathing, sounds and more"),
  once ever: the tray holds eight tools and nothing on screen said so.

### V5 follow-through (2026-08-16): Home gets the same proactive treatment

- **Home's hero notices what you just did** (`heroLineFor`, pure + pinned). It
  said one thing forever — the week's presence count — whether you had checked
  in ten minutes ago or not opened the app in a fortnight. Now, in order:
  offline → a check-in in the last 90 minutes → **a plan step finished today**
  → several quiet days → the week → the honest empty line. Presence framing
  throughout: every branch names something that happened, never something that
  didn't, and a first run is never told it has been "quiet".
- **Backend: `PlanStepOut.done_at` is now serialized.** Writing the branch above
  exposed that the field was dead on arrival — `PATCH /plans/steps/{id}` has
  always written the column, but the schema never returned it, so
  `stepsDoneToday` could only ever count zero. Additive and null-safe (null when
  not done, and for rows ticked before it shipped); un-ticking clears it, so a
  re-done step is dated by its new completion. Pinned by
  `test_data_flows.py::test_a_step_reports_when_it_was_done_not_just_that_it_was`;
  recorded in ARCHITECTURE's route table.
- **The suggested-activity card stopped shouting.** It was an accent pane with a
  full-bleed deep-plum CTA sitting directly under the companion's own words —
  the loudest thing on a screen whose primary action is *talking*. Badge,
  eyebrow and title now share one row, the description is bodySmall, and the
  CTA is a wrap-width pill: an offer, not a demand.

### V5 motion pass (2026-08-16) — effects that carry information

Owner asked for "some effect to make it world class". The app already had
entrance motion (`appear`, `popIn`), press feedback, shimmer, the bloom ring
and the orb; what it lacked was motion where movement *means* something.

- **`Modifier.grow(index, vertical)`** — a bar that grows from its own baseline,
  staggered. A bar's height (or width) IS its value, so growing from the
  baseline shows the value being measured out rather than dropping a finished
  shape on screen. Applied to Home's seven-slot sleep graph (bottom-anchored,
  oldest first) and Trends' per-mood counts (left-anchored, heaviest first).
  420ms / 45ms apart — slower reads as the app being slow. Reduce Motion
  settles instantly at full size: an animated chart is a nicety, a blank one is
  a bug.
- **A scroll-aware top bar** (`CereBroTopBar.scrolled`): a hairline fades in
  along the bottom edge once content has scrolled beneath a FIXED bar. Without
  it a scrolled page and the top of the page look identical, and the bar reads
  as painted on rather than floating over content. Wired on Home (its scroll
  state is hoisted for this); screens whose bar scrolls with the body leave it
  false.

### Web surfaces reviewed in the light theme (2026-08-17)

First walk of the landing page, the browser app and admin **rebuilt from source** — and the
first lesson was about the walk itself: `docker compose up -d <svc>` reuses whatever image
exists, so the containers were serving old builds. The landing page I first reviewed was a
different design generation entirely, and `/explore` 404'd purely because the `app` image
predated the route. Nothing containerised is worth reviewing without `--build`.

- **`.checkin-hero` / `.prompt-hero` / `.talk-hero` headings were invisible in Dawn.** The
  palette note in `apps/app/app/globals.css` already says these dark inset panels "carry
  white text", and they pin their eyebrows and paragraphs — but never their HEADINGS, which
  inherited `--text`. White in Night, so nothing showed; near-black in Dawn, putting #211d20
  on a #171019 panel. Measured **1.12:1** for "How are you arriving today?" and **1.72:1**
  for "Add intensity or a note", against a 4.5:1 floor; **18.67:1** after. `.hero-card`
  was already correct because it sets `color` on the panel itself — which is what the other
  three should have copied.
- **The landing page passes.** Zero contrast failures across every measurable string in the
  light theme (18 unmeasurable, all inside gradient panels), and all 34 links resolve.
- **All 20 browser-app routes and the landing's legal/support pages return 200.**
- Method note: two rounds of false positives before the real one — a `color(srgb 0.97 …)`
  background parsed as 0-255, and a background probe that fell back to page cream inside dark
  panels and reported a perfect 1.00:1. A contrast sweep is only as honest as its parser.

### Web ⇄ Android parity: the gaps closed (2026-08-20)

The comparison found seven real gaps. All seven are built; the ones that remain are
listed below them, honestly, rather than described as done.

- **The offline write queue.** The biggest, because it was the only one that LOST
  something: a check-in, journal entry or night saved without a signal simply failed.
  `apps/app/lib/outbox.ts` mirrors `net/Outbox.kt` against the same idempotency contract —
  key minted when the item is queued (not at send time, or a retry after a crash creates a
  second check-in), oldest-first drain, one failure stops it, and **a 4xx is rethrown rather
  than queued** because "saved, will sync" over a request the server refused is a lie found
  out later. The shell shows what is waiting; `/checkin`, `/journal` and `/sleep` each say
  "saved on this device" rather than "Saved". The journal additionally says the safety scan
  has **not** read a queued entry — it is server-side, so it genuinely has not.
  Pinned by an e2e test that goes offline mid-page and then asserts the SERVER-computed
  streak moves once the network returns.
- **Voice.** `lib/voice.ts` → `/voice/stt` and `/voice/tts`. The transcript lands in the
  composer for review, never straight into the conversation. The microphone only exists when
  `/voice/status` says the key is configured **and** the browser can record — the same
  "hide it rather than ship a dead button" ruling Android applies to Google sign-in. The
  composer's "voice arrives with the mobile apps" footnote is gone; when voice is off, the
  page says so instead.
- **Sleep insights** (`/sleep/insights`) — week/month/3-months over `/sleep/summary`, every
  tile gated on `enough_data` so nothing prints "0h 0m" as if it were a measurement.
- **Trends** (`/insights/trends`) — the day-by-day series with unlogged days ABSENT rather
  than zero, and the mood↔sleep link withheld with its reason until enough overlapping days
  exist.
- **The mixer** (`/sleep/mixer`) — four layers, four presets, same vectors as Android. The
  layers are **synthesised in-browser** (`lib/mixer.ts`, Web Audio): every `ambience.*` row
  in the catalogue ships an empty `url` today, so fetching the same files would have shipped
  four silent sliders. An uploaded asset supersedes a layer per layer, and each slider's
  label says which of the two you are hearing.
- **Body scan** (`/games/bodyscan`), **CBT-I** and **MBCT** (`/library/cbti`, `/library/mbct`)
  — Android's copy verbatim, disclaimers included. Pause holds the remaining seconds, which
  is Android register B34 ported rather than re-learned.
- `authedFetch` no longer stamps `Content-Type: application/json` over a `FormData` body —
  that would have broken the STT upload before it left the browser.

**Still open** (Android routes with no web equivalent, none of them a data-loss or honesty
gap): TIPP, gratitude, one-good-thing, intention, the CBT thought record, crisis grounding,
insight reel, wind-down, the mindful mini-games, baseline assessment, trusted contact, the
standalone player.

### iOS: the four screenshot-tour failures, and the orphan under them (2026-08-21)

iOS had failed **every time it has ever run in CI** — 2026-08-17 three times, and again
today. Always the same four, always `testScreenshotTour`: `Sounds`, `Insights`,
`Programs`, `Check how you feel` "not reachable from Home". The other 18 UI tests pass.

A previous pass diagnosed it as scroll position and added `swipeDown` plus a retry. The
timing disproves it: that fix landed at **14:15** and the run that failed identically
started at **14:29**. The diagnosis was wrong.

**What was actually true.** `QuickLinksGrid` — the four-tile explore row naming Toolkit,
Insights, Programs and Sounds — had **no call site anywhere in the app**. It was orphaned
by the Home de-densify. Three of the four were therefore never on Home to reach; Toolkit
passed only because it also has a real `NavRow`. And "Check how you feel" renders
`if focus.route != .mood`, while the tour launches `-resetState YES` → `!checkedInToday` →
the HERO is the mood ask → the row is hidden on **every** run. Corroboration: `testHomeFlow`
and `testPhase2DataLayer` already tap the hero's "Check in" with a fallback, and both pass.

**The real bug underneath: `SoundLibraryView` had no door.** Its only reference in the
entire app was inside that dead grid — a finished screen behind nothing, while grepping
"Sounds" in `HomeView` still made it look reachable. That is exactly how an orphan hides,
and exactly the trap already hit on Android (the Practice library, and the door
`guidedimagery` never had) and now tested for on web.

Fixed at both layers:

- **App** — `SoundLibraryView` gets a door in Sleep, beside the meditation library it
  belongs next to. `QuickLinksGrid` deleted rather than re-wired: the de-densify cut Home
  rows deliberately, and every destination it named is reachable elsewhere (Toolkit from
  its own row, Insights from You, Programs from Sleep and the enrolled card).
- **Test** — each tour entry now names where its destination actually lives, and takes a
  LIST of candidate labels so the conditional check-in row can fall back to the hero CTA.

**Unverified until CI says so.** There is no Mac here, so this is a source-level diagnosis
with a source-level fix; the iOS job takes 41–51 minutes and is the only thing that can
confirm it. Called out rather than glossed: everything above is evidence-backed, but
"compiles and passes" is not yet among the evidence.

### Admin: the two tabs with no test at all (2026-08-21)

Prompted by a fair question — had admin been tested? It had: 12 e2e tests across 10 tabs,
green in CI. But a count showed **two tabs named in no test at all** (Media, Oracle) and
**9 of 29 admin routes** named nowhere. Two of those nine mattered more than the rest.

- **Media** is the pipeline that decides whether premium narration is a real file or
  silence — `services/media.playback_url` returns `""` for an un-entitled item, and every
  `ambience.*` key still ships empty, which is why the web mixer had to synthesise its
  four layers. The test uploads to a key, asserts the PUBLIC `/media/catalog` (the
  endpoint clients actually read) now carries a url, then clears it and asserts the key
  still EXISTS with no url — Clear must hand the key back, not delete it, or clients lose
  their fallback.
- **Disabling an account** is the one admin action that changes what a real person can do,
  and `/admin/users/{id}/active` was named in no test anywhere. The dialog promises
  "They'll be signed out and locked out"; the test proves the second half by signing the
  account in before and after, and proves re-enabling restores access so a mistake is
  recoverable. It also pins the two-step guard: the confirm stays disabled until a reason
  is typed.

**Verified by mutation:** removing `!reason.trim()` from the confirm's `disabled` — making
access revocable in one stray click — fails with "the confirm was live before a reason was
given".

Three method notes, all mistakes worth not repeating: `docker compose run` without
`--build` runs the image's BAKED copy of the tests, so edits appear to have no effect (two
runs were spent on that); a `Buffer.from("... ...")` written through a script put real
control bytes into a source file, and the upload endpoint validates only the EXTENSION and
non-emptiness anyway; and the media `<input type=file>` is hidden inside a
`<label class="btn">`, so it can be filled but never asserted visible.

Web e2e **61 → 63**, full suite green locally.

**And the hole that found:** pushing them turned CI entirely green with **every job
skipped** — no path filter mentioned `e2e/**`, so editing a test did not run it. Every e2e
change this session reached `main` with the Playwright suite skipped. They were verified
locally, so nothing shipped unproven, but the gate was not gating. `e2e/**` now feeds both
the `backend` and `web` filters, which is what the Playwright job keys on.

**Still untested on admin:** the Oracle tab, and 7 remaining routes — `/digest/run`,
`/content/{id}/narrate`, `/oracle/pending/{id}/expire`, `/prompts/{name}/revert`,
`/prompts/{name}/versions/{v}/activate`, `/media/{id}` (the direct PATCH),
`/users/{user_id}`.

### Habits, and the promise attached to marking one (2026-08-21)

The last item on the write-flow list. The add is the least interesting part — goals with a
second field — so the test is aimed at the sentence on `Api.setHabitToday`: *"Idempotent
server-side and undoable — a mis-tap is never permanent."* That is a claim about a control
people press while distracted, on a screen whose whole framing is "flexible, not a
streak", and an undo that silently did nothing would be a quiet betrayal of it.

- add, mark, and **un-mark** through the screen, asking the server after each
- marking the same day twice must not add a second day

The second assertion reads `recent_days`, not a counter, because `HabitOut` deliberately
has neither a count nor a streak field — *"the schema shouldn't be able to say 'you broke
it'"*. That absence is what the test relies on, so it reads the seven-day window rather
than inventing something to count.

**Verified by mutation:** collapsing `if (done) "POST" else "DELETE"` to always-POST — an
un-mark that does nothing — fails the suite. Worth recording precisely: it fails at the UI
assertion ("never appeared on screen: Mark today") rather than at the server one, which is
the correct ordering, since a button whose un-mark never fires also never flips back. The
server assertion stays as the backstop for the other failure shape — the button flips and
nothing is sent.

Throwaway account, because `Api` exposes no habit delete: the backend has
`DELETE /habits/{id}` and nothing on Android calls it, so a habit added to the demo
account could not be removed and the next device walk would screenshot it. Adding a client
method purely so a test can clean up would be changing the product to suit the test.

Instrumented suite **20 → 22**, stable across repeated full runs. Demo account verified
pristine after: 0 habits, 0 goals, Sleep Reset still day 4.

**The write-flow list is now empty.** 8 → 22 instrumented tests over this stretch:
journal, goals, sleep, trusted contact, safety plan, memory CRUD, export, account
deletion, programme enrolment, consent toggles, habits — each driven through the UI and
verified against the server, three of them mutation-checked.

### The consent toggles — is the switch connected to anything? (2026-08-21)

The last claim with no client-side proof, and the one where a silent failure is worst:
`test_consent_off_blocks_reads_and_writes_but_never_deletion` shows the SERVER honours
`ai_memory`, but nothing checked whether the switch on the privacy screen reaches it. This
repo has shipped a control wired to nothing before — `setTrustedContact` existed and
nothing on Android called it — and a consent toggle is the worst place for that: the
person believes they withdrew something they did not.

`ConsentFlowE2ETest` drives the actual `AppSwitch` and then asks the server, rather than
calling `updateConsent` (which would test the API, already tested):

- a fresh account has granted **nothing** — the default the product claims, and the
  baseline without which a toggle test could pass by accident
- the switch moves the server in **both** directions, off included — withdrawing is the
  half that costs the product something, so it is the half worth proving
- with memory off, a write is refused **and a delete still succeeds** — consent off must
  never trap data someone asked to remove

**Verified by mutation.** Replacing the toggle's `Api.updateConsent(...)` with an empty
block — the exact `setTrustedContact` failure — fails the suite with "the switch read On
but the server did not record the grant". A test for a wired-up control that cannot detect
an unwired one is decoration.

`turnOff` was added beside `turnOn` in `DeviceE2E`, both now one `setToggle` with the
frame-timing reasoning `turnOn` already carried.

**The first run failed usefully:** it wrote a memory before granting consent and got the
server's own "AI memory is switched off in your privacy settings" — because a fresh
account consents to nothing. The gate reminded the test of the product's documented
default.

Instrumented suite **17 → 20**, stable across repeated full runs. Demo account verified
unchanged after (consent intact, Sleep Reset still day 4); the consent tests run on a
throwaway account because consent state is rendered on the check-in screen, so flipping it
on the demo account would change what a device walk screenshots.

**Still not exercised end-to-end:** habits — the last item on the list, and the least
interesting of them.

### The privacy claims, proven from the client too (2026-08-21)

Four `CLAIMS_MAP` rows named a mechanism and a BACKEND test, and none of them was proven
from the app. That gap matters here specifically: this repo has already shipped a screen
wired to nothing — `setTrustedContact` existed and nothing on Android ever called it, so
nobody had been asked for consent at all. A claim is only as good as the client calling
the endpoint that makes it true.

- **"My safety plan — yours, in your words"** — type a warning sign, wait for the screen's
  own "Saved.", then read `/safety-plan/me` back and assert the words are in it. Restores
  the section afterwards.
- **"Edit or delete any of it"** — add a memory, edit it, delete it, assert it is gone.
- **"Export or delete everything from inside the app"** — the export has to carry real
  rows; a well-formed but EMPTY document would pass a status check and betray the promise.
- **The same claim, the destructive half** — `AccountDeletionE2ETest` signs up a
  throwaway account, writes a check-in to it, deletes the account, and proves the
  credentials stop working. A soft delete that leaves sign-in intact would satisfy "the
  row is gone" and fail the promise. It lives in its own class deliberately: a `@Before`
  that signs into the demo account, sitting next to a test that deletes whatever it is
  signed into, is one editing mistake away from wiping the fixture.

**The lesson of this slice was about cleanup, not coverage.** The programme test first ran
against the demo account, left the programme and re-enrolled it, and looked clean — the
same programme was active again. But `day` is derived from `started_at`, so the account
silently went from **day 4 to day 1**, and the next device walk would have screenshotted a
different product. "Restored" has to mean the state is the same, not that a row exists
again. That test now runs on a throwaway account (`BackendFixture.asThrowaway`), and the
demo account's enrolment was put back to the seed's documented day.

Verified after a full run: demo account still `Sleep Reset day 4 of 7`, 0 stray goals, 0
stray memories, 0 leftover `e2e-delete-*` users.

Instrumented suite **12 → 17**, stable across repeated full runs. 543 unit tests green.

**Still not exercised end-to-end:** habits, and the consent toggles that gate memory
("Turn memory off and it forgets" is still backend-only from the client's side).

### Android write flows, on hardware against a real server (2026-08-21)

The gap a count exposed: of 83 `Session` API methods, **75 were pinned by unit tests at
the URL/contract level and only 4 were exercised as a flow on a device**. Contract-pinning
proves the request is shaped right; it does not prove the button is wired to it, that the
screen reflects the result, or that the entry survives the trip — and every defect the
device walks found was of that second kind.

`WriteFlowE2ETest` adds four, each shaped the same way: **drive the UI, then ask the
SERVER.** Asserting a row appeared in a list would pass against a purely local optimistic
update, which this app can genuinely do — it ships an offline queue.

- **journal** — compose an entry, then read `/journal` back and delete it
- **goals** — add one, read `/goals`, then release it (goals have no DELETE)
- **trusted contact** — the one write where a wrong DEFAULT is dangerous rather than
  annoying: `notify_consent` decides whether `escalation.on_crisis` messages this person
  at the worst moment of someone's life, and the API comment records that it once
  hardcoded `true`. Now asserted from the client side, and the prior value restored after
- **sleep** — save a night through the screen's own disabled-until-chosen button, then
  read it back

**They SKIP without a backend, and that is a stated trade.** CI's Android job is an
emulator with no `api` beside it, so a hard requirement would turn a green pipeline red
for an expected absence. This mirrors the rule the iOS live-backend tests already follow.
Their real value is on a handset or any runner that brings a backend. Reachability is an
authenticated round trip, not a socket check — a port that answers but is a different
service is a trap this repo has hit before.

**Two bugs found, both in the test harness rather than the app** — which is the honest
result and worth recording as such:

1. `requireText(title)` after tapping Add matched the draft still sitting in the INPUT, so
   the assertion passed instantly and raced a POST that took 2.5s. The test reported "the
   goal never reached /goals" against a server that had stored it. Now it waits for the
   cleared field — the screen's own success signal (register B89).
2. **`resetToFirstRun` did not clear the in-memory session.** `Session` holds the access
   token in a `@Volatile` field and `init()` does not reset it, so a prefs wipe left the
   process still authenticated. Nothing noticed while every instrumented test was a guest;
   adding one that signs in turned it into `GuestAppE2ETest` failing on every full-suite
   run while passing alone. That is an order dependency, not a flake — and it is very
   likely what the "intermittent" failure recorded on 2026-08-20 actually was (same
   assertion, same shape), though that instance was never proven.

Full suite now **12/12, stable over three consecutive runs** (was 8 tests). 543 unit tests
green.

**Still not exercised end-to-end:** programme enrolment, memory edit/delete, export,
account deletion, habits, the safety plan.

### The two disabled buttons — not a compliance fix, a legibility one (2026-08-21)

The last two flags on the Dawn walk were "Previous" on step 1 of TIPP (1.94:1) and of the
5-4-3-2-1 grounding sequence (2.33:1), both correctly disabled. WCAG 1.4.3 exempts
inactive components, so neither was a violation — but at those ratios they read as
**absent** rather than as a control you cannot use yet, and a step counter whose back
button appears to vanish is a worse answer than one that is plainly there and plainly
unavailable.

Both now use a shared `DisabledTextInk` (`TextMuted` at 0.7): measured on the handset at
**3.07:1 Dawn / 4.47:1 Night**, identical across the two screens, against an ENABLED label
near 16:1. The gap is what makes it read as disabled, and it is still about fivefold.
Going brighter would be the opposite mistake — a disabled control that looks live earns a
tap and gives nothing back.

The Material one needed an explicit `ButtonDefaults.textButtonColors(disabledContentColor
= …)`: its default is `onSurface` at 38%, which is where the 2.33:1 came from.

**The gate pins the gap, not just the floor** — 3:1 minimum AND strictly less than
`enabled / 2.5`, in both themes. Writing it exposed a real limitation in the test helper:
`assertContrast` reads RGB and ignores alpha, so a translucent ink was being scored at
FULL strength and the first version of the assertion failed on its own arithmetic rather
than on the colour. The test now flattens the ink onto its background first, and was
verified to fail at the old alpha ("2.64:1 — too faint to read as a control", matching the
device).

**Dawn now has no real contrast findings.** 58 routes: 5 flags, all of them the documented
filled-pill artifact.

### Dawn walked too — the DEFAULT appearance, which I had not been testing (2026-08-21)

Prompted by a fair question: why test Night? Because the phone was in dark mode and the
app's default is `ThemeMode.System`. But `AppTheme.systemDark` carries its own answer —
*"Defaults to false: Light Dawn is the base appearance, Night is the opt-in."* So the
58-route walk had been covering the opt-in, and the base appearance had only ~6 spot
checks — after a session of changes to SHARED tokens (`colorScheme.primary`,
`PickRowSelectedFill`, `DangerSoftInk`, the Explore hero, eight `Color.White` sites).

The automated gate was **not** the biased part: `ContrastTest` runs 33 assertions in Night
and 39 in Dawn. It was the device walk that was one-sided.

**Dawn is markedly healthier: 8 contrast flags against Night's 32, and no dead screens.**
That fits — every bug this session was Night-only, which is what you would expect of a
palette authored in the light theme and derived into the dark one. Of the 8: two are the
WCAG-exempt disabled buttons, three are filled-pill artifacts, and three are real but
marginal.

**The three marginal ones are a lesson about my own instrument.** "CHECK IN",
"SLEEP INSIGHTS" and "GROUND · 3 MINUTES" measured **4.42, 4.48 and 4.24** against a 4.5
floor. Token arithmetic said 4.88 and passed them — because it assumes the flat surface
token IS the background. Sampling the pixels showed the ink was exactly `--warm`
(`#A45161`) but the paper was `#F0E7EE` and `#ECE2ED`, not `#F8F4EE`: those screens layer
a wash over the base surface. **Pixels were right and the arithmetic was wrong**, the
mirror of the filled-pill case where the arithmetic was right and the pixels were wrong.
Neither instrument is sufficient alone.

**Fixed at the right layer — the wash, not the token.** The first attempt darkened Dawn's
`warm` to `#954454` and `ContrastTest::dawnPalette_pinsTheCanonicalLightValues` rejected it,
correctly: that palette mirrors `design/tokens.css` byte for byte, and
`scripts/check-contrast.mjs` already passes 108 pairings including `warm` on every neutral
ground it is allowed to land on. The token meets its contract. What broke the assumption
underneath it was the wash, so the wash is what moved:

- **`AuroraBackground`'s Dawn orbs** were taking 8.6–9.9% of the surface's luminance away
  from under page text. On a LIGHT ground a tint can only subtract, so decoration that is
  free on Night is not free here. Dawn alphas `0.10/0.08/0.04 → 0.06/0.05/0.025`, specks
  `0.13 → 0.08`. Night untouched — there the orbs ADD luminance to a dark ground, so they
  help its text rather than hurt it.
- **`FocusCard(pastel = true)`'s wash** took the card's ground to `#ECE2ED`. Half strength
  on Dawn only; Night keeps the full gradient, where the same eyebrow is a pale pink on a
  dark card and the wash costs it nothing.

Measured after, on the handset: **4.42 → 4.56**, **4.48 → 4.59**, **4.24 → 4.71**. Night
re-checked on the same three screens (5.87 / 5.71 / 6.61) — unchanged, as the branches
require. Full Dawn re-walk: **8 contrast flags → 5**, and the mid-range list is now only
the two WCAG-exempt disabled buttons. The soft diagonal wash is still visibly there; it is
lighter, not gone.

Worth keeping: the fix that was rejected was the one that changed a shared brand colour
for three Android eyebrows. The gate that stopped it was a palette-pinning test, not a
contrast test — the value was legal, it just was not Android's to choose.

### The last contrast flags, chased to two root causes (2026-08-21)

The walk's mid-range flags — the ones left "unverified" when the literal sweep landed —
turned out to be **real**, and to come from two places rather than seven. Re-walked after
each fix; the mid-range list went **21 → 1**, and the one survivor is not a defect.

**`colorScheme.primary` was a constant.** Material hands `primary` to every unstyled
`TextButton` as its label colour, and both schemes set it to `BrandPrimary` — a fixed dark
plum (`#8A4A78`). On Night's page that is **2.96:1**, on a Night card **2.68:1**.
Twenty-three TextButtons carried no explicit colour and inherited exactly that, which is
what kept surfacing as "Next" / "Previous" / "Pause" at 2.6–2.8:1 on the offline guidance
screens. `primary` is now the per-theme `Periwinkle` (9.70:1 Night, 9.91:1 Dawn), and
`onPrimary` moves with it — mandatory, because the fill is a PALE plum in Night and the
Cream ink that suited the dark one would vanish on it. The comment above that block
already warned about this exact trap; the code had drifted from it.

**`Color.White` on a `Periwinkle` fill, in a conditional.** `Periwinkle` is `#D9ACDE` in
Night — a light pink — so white-on-it measured **1.93:1**, against 10.85:1 in Dawn. That
is every selected mood tile, every intensity chip, and the onboarding option rows: the
SELECTED state was the one you could not read. Eight sites, now `OnPrimary` (8.77:1 /
10.61:1). The earlier sweep missed them because they are written
`if (active) Color.White else …` — a plain `color = Color.White` grep does not see it.

**The one remaining flag is correct behaviour.** `tipp` "Previous" at 2.63:1 is the button
DISABLED on step 1 of 4, dimmed deliberately to say so. WCAG 1.4.3 exempts inactive
components from the contrast minimum, so dimming it is the affordance, not a defect.

**Everything else is the known filled-pill artifact:** of 32 remaining flags, 31 sit at
1.00–1.10:1, which is the signature of taking ink as the extreme pixel inside a filled
button. Verified individually as legible on the handset.

Also checked and cleared: a scan pairing every `color =` / `tint =` in a screen with its
nearest enclosing `.background()` produced six candidates, and all six were mis-pairings —
the "background" was a 10dp status dot or a 1dp divider. Worth knowing before trusting
that shape of scan.

`ContrastTest` gains both pairings in both themes (542 unit tests). Dawn re-walked on the
handset after the theme change: no regression, filled components and TextButton ink both
correct.

### The 12 small tap targets: eleven did not exist (2026-08-20)

Measured before fixing, and the measurement is most of the story.

**Eleven of the twelve were clipping artifacts.** `uiautomator` reports VISIBLE
bounds — a 200px card at the bottom of a scroll container measures the sliver you can
see. Every one of the twelve ended at exactly **y=1604**, the screen edge, which is the
tell. Re-measured after scrolling, they either grew or scrolled away.

**Three more, found statically, were also not violations.** `TopBarAction` is
`.size(46.dp)`, `CircleAction` is `.size(47.dp)`, and the crisis back button is a raw
46dp `Box` — all under the 48dp floor visually. But Compose expands an interactive
node's touch target to the 48dp minimum: measured on the handset, every one of them
reports **96x96** at the touch layer. A small visual size is not a small tap target.
Left as they are: changing them alters the design for no accessibility gain.

**One was real, and only under animation.** The Sleep hero's Play pill pulses on an
infinite `animateFloat(0.98f, 1.03f)` through a `graphicsLayer`, and `graphicsLayer`
scale transforms HIT TESTING, not only pixels. Sampled across the cycle it oscillated
**94–98px** against a 96px floor — the app's most-invited tap shrinking below the
target size while someone is aiming at it. The pulse now runs `1f..1.05f`: same
invitation, same 5% of travel, never smaller than the layout. Re-sampled after the fix:
a steady **100px**. (`pressScale`, which shrinks to 0.96 while HELD, is deliberately
untouched — by then contact is already made.)

**The auditor is now in the repo, with its own errors written down.**
`scripts/android-walk-audit.py` skips nodes flush against a viewport edge and counts
them separately, marks filled-control contrast as low-confidence, and downgrades every
tap finding to `SMALLTAP?` — a single dump races layout, and the bottom nav pill
measured 220x11 seven seconds after launch versus 212x114 settled. Re-run over the same
58 captures: **SMALLTAP 12 → 2**, and both survivors are that nav pill mid-animation.

The docstring states plainly what the tool gets wrong in both directions, including that
it called `explore` clean while the worst colour literal in the app (1.89:1) sat on that
screen, because a gradient is not flat enough to measure against. Pixels find places to
look; the palette arithmetic decides.

### The 37 hex literals swept out of Android screens (2026-08-20)

The systemic version of the two colour bugs above: `CLAUDE.md` has said "screens read
design tokens only" since the iOS client, and 37 opaque `Color(0xFF…)` literals had
accumulated in `ui/screens/` anyway. **All 37 are gone**, and a test now enforces the rule
rather than restating it.

Every one was authored while looking at Dawn, and every one failed in Night. Measured
against the surface each actually sits on:

| literal | where | Night before | after |
| --- | --- | --- | --- |
| `6C2768` ×5 | symbols, crisis links, Explore eyebrow | **1.89:1** | 8.77 (`Periwinkle`) |
| `6E376B` ×2 | back arrows | **2.15:1** | 7.74 (`Periwinkle`) |
| `A52F50` | crisis back arrow | **2.78:1** | 6.58 (`Danger`) |
| `955386` ×2 | chevrons | **3.10:1** | 7.71 (`TextMuted2`) |
| `B13D57` ×6 | page eyebrows | **3.27:1** | 8.91 (`Warm`) |
| `776E6E`, `817980` | subtitles, placeholder | 3.41–4.01 | 7.71 (`TextMuted`) |
| `4B775E` ×4, `C75270` ×4 | family icons | 3.30–3.92 | 9.30 / 7.35 (`Ok` / `Warm`) |
| `E34B4B` ×3, `D45369` | urgent icons | on `DangerSoft` | 6.58 (`Danger`) |
| peach / lavender ×3 | Explore hero gradient | mixed a flipping stop with two fixed ones | `ExploreHero*` |

Three that were not simply a wrong token:

- **The Explore hero mixed art with theme.** Two stops were fixed hex and the middle one
  was `AccentSoft`, which flips — so Night got a dark band between two light ones, and the
  eyebrow sat on a mid-tone wash at 1.89:1. It now follows the shape `MixerHero*` and
  `SleepHero*` already use. The eyebrow is `Cyan`, and it is gated against **every stop**
  of the gradient, not just the one under the glyphs today (tightest: 4.80:1 on Dawn).
- **A `Color.White` button label was unreadable in both states.** Not a hex literal, so
  the sweep nearly missed it — found because the walk's 2.53:1 flag on gratitude
  "Save privately" cross-validated exactly against token arithmetic. White measured
  **2.95:1** on the enabled Accent2 pill and **2.53:1** on the disabled one, in Night. The
  label now follows the fill: `OnAccent` (5.73) / `DisabledInk` (6.80). Four other
  `Color.White` inks fixed the same way; `DisabledFill` in Night was also making the
  disabled state LOUDER than the live one.
- **`FocusCard(accent = …)`** was the only call site in the app overriding that default,
  with a plum one shade off `BrandPrimary`, for a shadow tint. Override dropped.

**The gate.** `NoRawColorsInScreensTest` walks `ui/screens/` and fails on any opaque
literal, naming file, line and value. Verified to fail before being left green.
Translucent literals are deliberately allowed — a colour with alpha below `FF` composes
over whatever is beneath it, so it cannot be light-theme-only by construction; the two
that remain are a 15% white highlight and a 19% plum stroke on a decorative canvas.
`ContrastTest` gains 12 new pairings covering every replacement, in both themes.

**On the walk auditor, again.** Its mid-range flags proved RIGHT where they could be
cross-checked: gratitude "Save privately" 2.53:1 and breathing-intro "BREATHING" 3.27:1
both matched token arithmetic exactly. But it reported `explore` as **clean** while the
worst literal in the app (1.89:1) was on that screen — a gradient is not flat, so it was
skipped as unmeasurable. It errs in both directions; the token arithmetic is the
instrument that decides.

Gates: 537 unit tests green (ContrastTest 28, NoRawColorsInScreens 1), instrumented 8/8,
and both themes re-walked on the handset — Dawn's art is pixel-for-pixel what it was.

### All 58 Android routes walked and measured (2026-08-20, CPH2681)

The first systematic pass over the whole graph rather than the screens someone thought to
open. **Every route rendered — no blank screens, no crashes, 58/58.**

**How it was made possible.** The app had no way to address a route from outside: one
LAUNCHER entry, and `cerebro://` deliberately allow-listed to 20 routes because a
notification must never navigate somewhere arbitrary. That allow-list is untouched; a
debug-only intent extra was added instead (`am start … -e walk_route <route>` →
`DeeplinkBus.offerDebugRoute`, gated on `BuildConfig.DEBUG`). A 59-route app that cannot
be addressed is a 59-route app that gets reviewed once and then never again.

**Two real defects, both Night-only, both confirmed by token arithmetic rather than by
eye — and both invisible to a light-theme review:**

- **The selected row in every pick list was the one you could not read.** Appearance,
  Language and Crisis region share `SelectableRow`, whose label is `ChipSelectedInk` —
  on-accent ink. `PickRowSelectedFill` resolved to `accentSoft` in Night, so dark ink sat
  on a dark surface: **1.27:1** against a 4.5:1 floor, while Dawn ran 10.61:1. The ink was
  designed against Night's accent pill (`#D9ACDE`), which is the 8.77:1 the palette
  comment already documents. Fixed by using the accent fill in both themes.
- **The crisis screen's disclaimer was invisible in Night.** "CereBro is not an emergency
  service and cannot monitor your safety", under the call-112 banner, was a raw
  `Color(0xFF542D34)` written into the screen file — 10.02:1 on Dawn's pale `dangerSoft`,
  **1.27:1** on Night's dark one. On the most safety-critical screen in the product. Now
  a theme-aware `DangerSoftInk` token; Dawn keeps the exact colour it had.

**Why the existing 482-line `ContrastTest` did not catch either.** It gates *text role x
neutral surface*. The first is an ink on a fill that pairing never enumerated; the second
never entered the token graph at all. Both pairings are now in the gate, and the gate was
verified to FAIL on the old colours (`contrast 1.27:1 is below the 4.5:1 gate`) before
being left green — a test that cannot fail is decoration. **35 raw `Color(0xFF…)` literals
remain in screen files**; each is a place a token test cannot reach, and that is the
systemic version of this bug. Not fixed here.

**On the auditor itself.** The sweep flagged 54 contrast findings; most are NOT real. The
1.00:1 cluster is almost entirely filled primary buttons ("Save night", "Begin", "Next",
"Play"), where picking ink as the extreme pixel inside the glyph box fails against a pill
fill — several of those were confirmed legible by eye in the same session. The two fixes
above were each verified independently from the palette hex before any change was made.
The remaining mid-range flags (checkin "Tired" 1.93, gratitude 2.53, tipp "Previous" 2.63,
insightreel "Pause" 2.67, crisisgrounding "Next" 2.68) are **unverified** and still open.
Also unverified: 12 SMALLTAP flags for clickable nodes under the 96px (48dp) floor.

**Instrumented flake, now characterised.** The first `am instrument` run after an
`adb install` failed 2 of 2 times (`GuestAppE2ETest::a_guests_check_in_is_answered_not_errored`,
"never appeared on screen: Anxious", 52.6s); every subsequent run passed, 3 of 3, in ~24s.
Consistent with cold-start work racing the suite's `resetToFirstRun`. Not fixed.

**Note for the next walk:** the debug route hook only takes effect on a COLD start —
`onNewIntent` delivers the extra but the running NavHost does not act on it. The walk
force-stops between routes, which also gives each screen a clean back stack. Worth fixing
if the walk is ever run often enough for the ~3s per route to matter.

### The Android app walked on the same handset (2026-08-20, CPH2681, live backend)

Built fresh from main (the installed APK was three days old and predated this session's
Sleep commits), installed, and walked against the dev backend over `adb reverse`. The two
Sleep fixes from earlier hold up on device: the times read in full Periwinkle, and the
save verdict sits directly under the Save button. `POST /sleep` came back **201** and the
card collapsed to "Logged · Good · 23:00–07:00".

One real defect, in the offline queue's *surface* rather than its storage:

- **A queued write could be both unsent and invisible.** `TodayScreen` gated the
  queued-writes banner on `Session.servedStale` — so the banner, and the "Send now" it
  carries, disappeared the moment reads started working again, which is exactly when it
  becomes useful. Walked: a check-in logged with the backend unreachable said "Kept on
  this device — it will sync when you're back"; the link was restored; the next read
  succeeded; the row then reverted to the OLDER server value with nothing on screen
  admitting a write was pending. Nothing was lost — the drain at app start sent it
  (`POST /moods` 201) — but "not lost" is not "the person can see what is happening".
  Fixed: the banner now shows whenever `Outbox.count() > 0`, with copy that fits the
  online case (the offline sentence explains the stale reads too, and would be a lie once
  reads are live), and Today drains once automatically when it stops serving stale reads.
- **`Outbox.drain()` had no lock.** Adding an automatic trigger made a pre-existing race
  easy to hit: two callers read `pending()` together and both POSTed the same entry, and
  the server's idempotency guard answered the loser **409 in the same millisecond**.
  Nothing was duplicated — that is what the guard is for — but the race was real.
  `drain()` now serialises on a `Mutex`.

Verified after the fix: queue offline → restore → the entry sends itself, the banner
clears, and the row shows the synced value, with no cold start. `GET /moods` confirms
**one** row per write across the whole walk (four writes, four rows, including the one
made from the browser earlier).

**Open, not closed:** after the mutex the drain still issues a second POST that the
idempotency layer answers as a replay (201 in 6.79ms against the real one's 2336ms). One
row lands, so this is waste rather than a correctness bug, and the cause was not chased.

**Flake, seen once:** `GuestAppE2ETest::a_guests_check_in_is_answered_not_errored` failed
the first full instrumented run ("never appeared on screen: Anxious", 52.6s), then passed
alone and passed a full re-run (8/8, 23.8s). Recorded as intermittent — not fixed, and
not attributed.

### The web client walked on a real handset (2026-08-20, CPH2681 over CDP)

The new rooms were built and shipped green — 58 e2e, typecheck, lint, claims gate — and
then walked on a phone, where six things were wrong that no gate was asking about. The
method is the point: Chrome on the device over `adb forward tcp:9222
localabstract:chrome_devtools_remote`, then Playwright `connectOverCDP`, so every number
below was **measured on the handset** rather than judged from a screenshot.

- **Today was 128px wider than the screen.** `documentElement.clientWidth` 360,
  `scrollWidth` 488, 75 elements past the right edge, and Chrome zooming the page out to
  fit (`innerWidth` 488). The layout was innocent — sidebar hidden, tabs shown,
  `.dash-grid` correctly 324px — but the grid ITEM measured 470. Cause: `.emoji-row`, six
  58px tiles and five 12px gaps = **408px of min-content**, inside a grid child whose
  default `min-width: auto` refuses to shrink. Fixed by wrapping the row (not scrolling
  it: it is the primary action, and hiding two of six feelings behind a swipe makes the
  easiest thing to reach the one nobody sees) plus `.dash-grid > * { min-width: 0 }`.
  After: scrollWidth 360, offenders 0. **It passed at 390px and failed at 360** — the
  overflow test now measures at the handset's real width.
- **`.ds-cta` was scoped to `.design-root`.** The primary button on /checkin — and on
  every room added since — had no rule outside the design prototype, so it rendered as a
  grey UA button under the 48px floor. `.text-btn` and `.today-cta` were promoted when
  these screens graduated and carry a comment warning about exactly this; `.ds-cta` and
  `.ds-textarea` were missed. Promoted, with a test.
- **A chosen chip did not look chosen.** `aria-pressed` was set on the Trends/Sleep
  windows, the mixer presets and the journal's tag filters, and styled only for
  `.ds-chip`. Tapping "Still air" changed the blend and the heading while the chip sat
  still. Added `.chip[aria-pressed="true"]`, with a test that the two states paint
  different backgrounds.
- **Three stat tiles stacked to ~750px** on Sleep insights, pushing the chart they explain
  off the bottom. `.stat-tiles.compact` keeps them a row of three on a phone. "7h 43m"
  wrapped inside its tile, so the duration now carries a non-breaking space.
- **Two-night weeks drew two half-screen slabs.** `.sleep-bar-col` is capped at 48px and
  left-packed.
- **Body scan put Begin eight cards below the fold** — the controls now sit above the
  script. `.text-btn:disabled` also had no style, so a dead "Back" looked live.

Verified on the handset, not assumed: a check-in made with the API tunnel cut showed
"Saved on this device" and "It will sync the next time you are online", the queue drained
on the next load, and `POST /moods` came back **201** in the API log. The mixer's
synthesised layers registered with Android's own audio service
(`AudioPlaybackConfiguration ... type:AAudio state:started usage=USAGE_MEDIA
sampleRate=48000`) and stopped cleanly. Trends read 12 nights, "Averaging 7h 43m", and
withheld the mood-sleep link with its reason. Sleep insights showed "—" under Week
because that window genuinely holds 2 nights (`enough_data: false` from the API) and the
real figures under Month.

Two environment traps worth keeping: a stray `python.exe` on `0.0.0.0:8000` from another
project swallowed the phone's `adb reverse` tunnel (the API log showed **no** `/auth/login`
at all — that is the diagnostic), routed around with a socat container on 8001 rather than
killing someone else's server; and a mid-walk `api` restart produced a genuine
"We couldn't load your nights just now", which is the error branch behaving correctly.

### V6 density + honesty pass (2026-08-16, uncommitted) — walked on the OnePlus

A whole-app pass on the two things a 720px phone punishes: **space** (padding,
type and hero heights tuned for a mock, not a handset) and **claims the UI could
not back**. Gated `:app:check :app:assembleDebug :app:lintVitalRelease`
REAL_EXIT:0, coverage 96.15%, claims gate green across 207 files, and walked on
the OnePlus CPH2681 against a live backend on the demo account.

- **Chrome and density**: top bar 66→60dp and **fully opaque** (at 96% the hero
  ghosted through it — that closes audit-K's "hero/app-bar ghosting"), card
  padding down one step at every width, Practice library adopted `CereBroTopBar`
  instead of its own hand-rolled 76dp bar, Toolkit/PremiumNavRow/Settings rows
  tightened, hero heights 220→184dp, the voice orb 150→112dp.
- **Nav chrome tells the truth**: `shouldShowBottomBar` is now the four true
  roots (`home`, `talk`, `sleep`, `journal`). Every other room owns a Back
  button and no longer pretends to be a selected tab. Test re-pinned over the
  full pushed set.
- **Sleep**: the five quality answers are equal-width and complete (a clipped
  fifth chip read as broken, not scrollable), **vector faces replace the emoji
  scale** (OEM emoji changed shape/weight with the device font), and the Health
  Connect import moved **below** the manual path it optionally replaces.
- **Honesty**: Home's "picked with you, not for you" provenance line now renders
  **only for a real server plan** — the local offline fallback names its own
  reason and must not inherit a personalization claim. An offline session also
  stops waiting for a plan that cannot arrive (`planLoaded || servedStale`), so
  the most important card no longer shimmers blank until the network times out.
  Home's hero progress bar only draws when a **named programme** gives it
  context (it used to fall back to "days present this week", a week bar wearing
  a programme's clothes).
- **Chat**: the ＋ tray carries **four** tools plus one explicit "All tools →
  Browse every practice" door (eight tiles was a catalogue, not a choice), the
  duplicate offline line under the composer went (the banner already says it,
  three times on one screen), and the empty-state card went (the orb is the art).
- **Insights**: the four-tab strip that navigated away on tap became four
  labelled rows; heading and the four row labels moved into resources (they were
  **English literals in Kotlin — invisible to translation**, not merely
  untranslated), EN + HI.
- **Dead weight removed**, since deleting a placement without deleting the thing
  is how cruft starts: 15 orphaned strings across EN + HI, the `FeaturedGameCard`
  billboard and its seven palette tokens + `contentArtBackground` (its one
  placement became a plain exercise row), and the You screen's month-presence
  grid **with its `Api.moods()` fetch** — that call was still costing every open
  of Settings a round-trip nothing drew. `presenceMonth` stays (pure, tested).

**Six defects the walk found, all fixed and re-verified on glass:**
1. **Sleep's TONIGHT badge was invisible in Dawn** — it used `ArtTextSoft`, the
   constant for text over *always-dark* art, on a hero that is themed. Pale
   lilac on pale lilac, beside the Play pill: it read as a rendering artifact.
   Now `SleepHeroMeta`.
2. **You/Settings had no way out.** Dropping its tab pill was right, but the
   screen kept `PremiumPage` (the *root/tab* frame) — no pill, no back arrow,
   and a gear in the corner that had already been used to get in. Gesture-back
   worked; nothing on screen said so. Now `PremiumSubPage(onBack)`.
3. **Insights' third stat tile overflowed its own tile and the card** — the
   server sends both `8` and `7h 41m avg` into a ~55dp tile at one type size.
   The long form steps down now.
4. The ＋ coach-mark promised "check-ins, breathing, **sounds** and more —
   without leaving the chat"; after the tray trim, sounds lives behind the All
   tools door, which *does* leave the chat. Wrong twice; reworded.
5. The Toolkit's grounding card repeated its own title in its subtitle and then
   truncated mid-word ("…through you…") — its own card string now.
6. `String.format("%.1f", …)` without a locale in Trends (a Hindi/`hi-IN` locale
   renders Devanagari digits into an English sentence).

**CLOSED — the rooms behind the unreachable screen.** `PracticeLibraryScreen`
was reached only from `ExploreScreen`, and `explore` has been deeplink-only
since V2-d — so in-app nothing could open it. It looked like a duplicate of the
Toolkit; it is not. It is the **only** door to `breathing-intro`, `bodyscan`,
`guidedimagery` and `gratitude`, because V2-e deliberately took those cards out
of the Toolkit and delegated them to it ("Calm-now doors bodyscan and
gratitude", still in the comment there). Two deliberate decisions, one wave
apart, left four finished rooms with no way in. `search` was stranded the same
way — Explore's header was its only caller.

Both now have doors, placed where the thing they open belongs:
- **Toolkit → "Practice library · Seven clear families"**, a `PremiumNavRow`
  before the support card. A row, not an exercise card: it is a door, not a
  practice, and the Toolkit stays V2-e's curated short list.
- **Sounds (Library pane) → "Search · The whole library"**. `SEARCH_KINDS` is
  soundscape/sleep/meditation/program/wind_down — exactly this hub's catalogue,
  so the catalogue's owner carries the search of it.

**And the test that missed it now catches it.** `RouteReachabilityTest` asked
"does anything navigate here?" — every stranded route passed, because something
did: a screen that was itself unreachable. New case, `a deliberately unreachable
screen is never the only door to something`, walks the excuse list, finds each
excused screen's file (only whole-file islands count, so a file that also holds
a reachable screen raises no false alarm), and fails if a route is doored from
an island and nowhere else. Verified by removing both doors and watching it
fail with exactly `[practice-library, search]` before restoring them.

### Positioning research (2026-08-16)

[POSITIONING.md](POSITIONING.md) — market map, USP in four pillars, and the
timing thesis (8 US states now ban or regulate AI therapy; Woebot retired its
consumer app; no chatbot holds FDA clearance — so "companion, never clinician"
turned into a moat). Shareable deck:
claude.ai/code/artifact/01d01938-813d-4d35-9e1c-17008e250e54

- [ ] **Re-verify India funding amounts before any external use.**
      INVESTOR_READINESS §5 was single-pass research (2026-07-03), never
      adversarially verified. The deck does not quote those figures; anything
      that does must re-confirm them first.
- [ ] **Re-check the competitor row assessments** (Calm / Headspace / Wysa /
      Amaha / Replika) before external use — they were read off public product
      surfaces in Aug 2026, not from anything internal.
- [ ] **Keep the "not true yet" panel in every version of the deck** (not live,
      zero users, no retention data, no named clinical advisor, iOS/web on the
      older shape, Hindi safety copy unreviewed). It is the same discipline
      CLAIMS_MAP enforces on product copy, applied to the pitch.

**Open after V3/V4/V5**: the AppNotIdle Robolectric flake (see V2-f note above);
the Play launch track; Hindi clinical review of the V3 safety-adjacent copy.

## Open — V3 direction: chat-first + proactive (owner call, 2026-08-16)

Post-V2 owner feedback: features relevant, still cluttered / not user-friendly; **focus on
chat first; the app should be proactive.** Owner supplied two references — a tracker-home
screenshot and the **Aira design gallery** (`~/Downloads/Archive/complete.html`: warm ivory
light world, deep-plum hero, floating dark nav pill, chat with memory chip / next-best-action
card / ＋ tools tray / uncertainty labels + reply controls / inline escalation ladder;
quick-action notifications, quick-log popup, notification inbox + quiet hours, supportive
re-engagement). Proposal prototype (interactive, mobile-first, light Dawn world):
claude.ai/code/artifact/51a16216-aa14-434c-8164-19fe3f070200 — **awaiting owner/Deepak
approval before implementation.** Headlines: tabs 5→3 (Home · Chat · Sleep; You→gear,
Journal→chat tool + room), chat logs sleep + mood conversationally, next-best-action card,
tools tray in-conversation, proactive rules (1 nudge/day, discreet lock screen "A moment
for you", quick-log without opening the app, inbox). CereBro deltas from Aira kept on
purpose: safety never blocks (no chat pause), Tele-MANAS-first ladder, 6-mood wire taxonomy.

## Open — audit L: declutter/verbosity review from first outside-tester feedback (2026-08-15)

Full report: [audit/L-declutter-feedback-2026-08-15.md](audit/L-declutter-feedback-2026-08-15.md).
Tester feedback ("cluttered, too much to read, be self-intuitive, launch now") verified against
the Android codebase in four deep passes. Confirmed: first run = 13 screens / ~610 words / 14 taps;
Today worst-case 22 blocks / 32 CTAs; "not medical care" ×12, "no streaks" ×6; 6 breathing +
5 gratitude + 4 grounding implementations; 16 dead `urgent_*` strings duplicating `crisis_*`;
Sleep has no permanent Today door. Also found real defects: **consent pre-ticked + 6 categories
POSTed while 3 shown (DPDP)**, onboarding state tiles write the wrong mood, crisis shield absent
on Talk/Journal top bars, Sleep-insights screen fully hardcoded English (760-key values-hi gap).

- [x] **Wave L1 — correctness & safety** — DONE via V2-a/V2-c (2026-08-15): consent
      defaults/categories ✓ (pinned), mood-tile mapping ✓ (pinned), `onUrgent` on
      Talk/Journal + breathe session ✓, custom-time reminder moot (Notify step retired),
      Reflection step retired ✓. Remaining from this wave: **dead crisis twin deletion**
      (`CrisisScreen` + 16 `urgent_*` strings) → rides V2-d.
- [x] **Wave L2 — onboarding cut** — DONE via V2-c (2026-08-15), deeper than planned:
      13→5 screens (spec'd 8); tour modal → one inline hint (V2-b).
- [ ] **Wave L3 — Today declutter** (compact mood grid, one hero CTA on house `PrimaryButton`,
      no rail, folds calmed, bell removed, "Tonight"/Sleep-door decision).
      → **Superseded by [REDESIGN_V2.md](REDESIGN_V2.md)** (2026-08-15): app-wide compact
      redesign — global spacing/type system, all 5 tabs + onboarding/crisis/settings specs,
      delivery waves V2-a…f. Waves L1–L6 map into it; L1's defects ride wave V2-c.
      **Design APPROVED by owner 2026-08-15** (interactive prototype:
      claude.ai/code/artifact/8b24bcae-7907-4027-b3fa-f67096f47e2f — the reference for
      implementation; owner sharing it with Deepak).
      - [x] **V2-a (partial, 2026-08-15): foundation** — `Space` tokens compacted 4/8/14/24
        (ratios preserved); `Page`/`PageHeader` gained `onUrgent` and the shield now sits on
        Talk's top bar, both Journal frames, and BreathLoops (picker + the running session —
        the Audit-L "no urgent door mid-session" defect); `ReferenceAction` delegates to house
        `PrimaryButton` (raw-hex plum pill retired). `:app:compileDebugKotlin
        :app:testDebugUnitTest` green (exit 0). Still in V2-a: row-height compaction sweep +
        an emulator/device look at the tightened rhythm.
      - [x] **V2-b (2026-08-15): the new Today shipped** — the approved one-card design:
        greeting (34sp) + one rotating status line (offline → earlier check-in) · **THE CARD**
        (no check-in today → 3-across compact mood grid inside the hero slot; a check-in
        morphs it into the one next step with "step x of y" in the eyebrow (→plan), one
        white pill, "more options →" tertiary (→practices), and the in-card undoable
        confirmation) · **Your day** rows (Tonight — Sleep's permanent clock-aware door —
        + program + evening journal prompt + build-a-plan) · **Quick helps** (Breathe /
        Ground / Sounds / Games) · **This week** card (presence dots + kind sentence +
        milestone, one door to Insights) · guest one-liner. Deleted from Today: date row,
        bell, lede, GuestSignInCard, rail (+its fetch), both folds, sleep/wind-down/program
        banners (absorbed into rows), metric tiles + duplicate insights doors, recent list,
        4-stop tour modal (→ one dismissible hint line). 18 new strings EN+HI (warm spoken
        register). Pure fns (homeBannerPriority, railKindFor, FoldSection…) kept — tests
        untouched; compile + full unit suite green (41s, exit 0). Dead-code sweep of the
        now-unused Today helpers rides V2-e. **Not yet walked on a device.**
      - [x] **V2-c (2026-08-15): onboarding 13→5 + the three data defects, CI-pinned** —
        funnel `order` is now Welcome → Disclosure → Consent → State → Guest(→Today);
        Language (auto-detected; changeable in You), Intro, Reset, Reflection, Notify and
        Ready are off-path (branches remain till the V2-e sweep; "Continue as guest" now
        IS the continue). Defects fixed: **consent defaults all-false again** (third
        restoration — extracted to `defaultConsent()` and pinned by
        `consent_defaults_are_all_false_and_cover_all_six_categories`), **all six DPDP
        categories now render as switches** (three were being POSTed unseen), and the
        **state tiles write the mood their label says** ("Clear" wrote "Anxious";
        stressed→Good, distant→Overwhelmed — pinned by
        `state_tiles_write_the_mood_their_label_says`; `onboardingMoodNote` gained
        Overwhelmed). Stepper contract updated: `ONBOARDING_STEPS`=4,
        funnelStepIndex/funnelProgress re-mapped, both tests rewritten. The "custom time"
        reminder defect is moot (Notify left the funnel; Settings has a real picker).
        Reminders in-context ask (post-first-check-in) still TODO — rides V2-e.
        Compile + full unit suite green (exit 0). **Not yet walked on a device.**
      - [x] **V2-d part 1 (2026-08-15): tabs · one crisis surface · You trim.**
        **Tabs**: Sleep takes the second slot back (new `ic_tab_sleep` crescent), Explore
        retires to a deeplink-only route; bottom-bar set + highlight mapping updated
        (practice-library/gratitude light Home; sleepinsights lights Sleep);
        `NavigationChromeTest` re-pinned to the new ruling. ⚠️ Doc-lineage incident,
        caught and repaired: the new spec OVERWROTE the 2026-08-06 Light-Dawn
        `REDESIGN_V2.md` unread — recovered from git to
        [REDESIGN_V2_2026-08-06-lightdawn.md](REDESIGN_V2_2026-08-06-lightdawn.md) with a
        supersession header; both docs now cross-reference, and stale code citations to
        "§6.1/§3.1" were repointed.
        **Crisis**: the onboarding-only `CrisisScreen` twin deleted; onboarding now uses
        `UrgentSupportScreen(inFunnel = true)` (in-app doors hidden — no dead taps on a
        crisis surface; dial intents work everywhere); all 18 dead `urgent_*` strings
        deleted from EN + HI.
        **You**: 23 → ~16 rows — trends/patterns rows out (Insights doors both),
        crisis-region row out (in-context on the crisis screen), tour row out (tour is
        retired), and the **Premium row hides for free unsponsored members** (nothing is
        purchasable on Android until Play Billing; sponsored/active keep the manage door).
        Remaining to reach ~10: merge appearance+language and fold the legal trio into
        Privacy & data — rides V2-e with the screen merges.
        Gate: `:app:check :app:assembleDebug` green with the REAL exit code checked
        (`$?` on the gradle command itself). ⚠️ The `cmd | grep …; echo EXIT` pattern
        bit AGAIN this session — two intermediate "green" claims were actually
        compile failures (a stale `CrisisScreen` import, then two test references to
        deleted `urgent_*`/old routes), caught only by the closing gate. Same trap
        this file already documents for pytest+tail. Fixed: import removed,
        `SafetyCopyTest` drops the deleted string, `RouteReachabilityTest` records
        `explore` as deeplink-only with the V2-d reason.
      - [x] **V2-d part 2 (2026-08-15): the Sleep split shipped** — one route, two pages
        sharing composition state. **TONIGHT** (default): hero/check-in (time-ordered as
        before), NowPlayingBar + timer row, ritual door, Sounds door, "Your sleep" door,
        the one CBT-I citation. **YOUR SLEEP** (`showDetails`, back-gesture returns):
        summary/chart/rhythm card (incl. `sleepinsights` link), editable diary (Edit flips
        back to Tonight where the form lives — the save/edit flow is untouched),
        mixer + programs doors, wind-down guides. Deleted from the tab: the sleep-stories
        ContentList (the Sounds hub IS the catalogue) and the morning/night door
        reordering (each page owns its doors). 3 new strings EN+HI. Full gate green with
        REAL exit code (`:app:check :app:assembleDebug`, REAL_EXIT:0). Sleep-insights
        localization still pending (V2-f). **Not yet walked on a device.**
- [x] **Wave L4 — copy diet** — DONE via V2-f (2026-08-16): `*_why` one line no citations
      (pinned), disclosures compressed honestly, error essays one line, acronyms plain.
- [x] **Wave L5 — IA consolidation** — DONE via V2-d/V2-e (2026-08-15/16): one implementation
      per behavior, Practices 15→~9, You 23→~11, Sleep split, guest wall one shape,
      orphans/aliases killed. (SafetyPlan single-save not revisited — its current shape stayed.)
- [~] **Wave L6 — localization** — Sleep-insights DONE via V2-f (18 `si_*` EN+HI,
      device-verified in Hindi). Still open: `ConsentNotice.kt` literals → resources; a
      sweep for remaining hardcoded literals; hi parity for strings touched this cycle.
- [ ] **Owner: Play launch track** (keystore, Play Console, data-safety + Health Connect forms,
      account-deletion URL deploy, graphics) — see report §4; no app-code blockers for internal
      testing; launch free without Play Billing.

## Open — audit K: emulator UI/UX walk (2026-08-15, ~28 screens rated)

Full report: [audit/K-emulator-uiux-2026-08-15.md](audit/K-emulator-uiux-2026-08-15.md).
Fresh-install onboarding → guest walk → signed-in walk on a headless Pixel 6 AVD.
Overall ~8/10; the honesty patterns hold up live. Four systemic findings, none fixed yet:

- [x] **Guest states lie — FIXED (2026-08-15 wave 1, verified on emulator screenshots):**
  `Api.activePlan()` no longer swallows failures (the eternal spinner was unreachable
  error-state); `Throwable.isGuestGate()` + `GuestSignInCard(subtitle=…)` now answer the
  guest 401 with a sign-in door on Daily plan, ContentList (Sounds + every catalogue),
  Privacy & memory (switches hidden — they could only lie), Safety plan (editor gated —
  guest writing could never save), and both insights screens. Pinned by
  `GuestGateScreensTest` (3 tests) + the rewritten `activePlan` contract test; full unit
  suite 504 green.
- [x] **Two icon languages + emoji + broken medallion — FIXED (wave 2, verified on
  emulator):** shared `moodIcon()` now faces the six states everywhere (Today grid,
  check-in sheet, onboarding first check-in); onboarding's suits/arrows/×/◇ are outlined
  icons; games registry carries ImageVectors (emoji retired; uniqueness pinned in
  `MindfulGameRegistryTest`); Ground family wears LocalFlorist, not the crisis shield;
  `EmptyStateArt` overlays a guaranteed-contrast glyph (journal/insight/sleep) over the
  generative bed; Insights-hub rows and Sleep section headers use real icons. Sleep's
  emoji rest scale stays — owner decision #25.
- [x] **Tour references invisible UI — copy FIXED (wave 3):** stop 1 names the
  "How are you right now?" tiles, stop 2 says "the big card at the top of this screen"
  (English + Hindi). Mechanical spotlight/scroll-to anchoring remains future polish.
- [x] **Jargon — FIXED (wave 4, en + hi where translated):** "prototype"→CereBro;
  "Agentic plan"→"Built for you"; grounding subtitle de-clinicalised; TIPP expanded to
  "Temperature, movement and sensory skills"; Premium/inbox dev-speak humanised;
  "Enter Today"→"Take me there"; welcome privacy said once; "988 … · 988" deduped
  (append target only when the name doesn't carry it).
- [x] **Human support region-aware — FIXED (wave 5, verified):** rides
  `rememberCrisisRegion()`/`primaryCrisisLine()` like every crisis surface; IN keeps
  Tele-MANAS + iCall + the coach note, elsewhere the region's line + regional
  findahelpline path. **ONB-08 permission timing FIXED** same wave: the funnel advances
  from the permission launcher's callback, so the dialog lands on the step that caused it.
- **Device e2e (2026-08-15 evening, OnePlus CPH2681, live backend via adb reverse):**
  all five waves re-verified on the handset — mood icons crisp at 720p; plan/sounds/
  privacy/safety-plan guest gates render and the sign-in door genuinely lands on Auth;
  Human support keeps Tele-MANAS + iCall on the IN device (region branch verified both
  ways vs the US emulator); re-armed tour fires stop 1 with the check-in tiles visible,
  so the new copy is true on screen; crisis card shows the number exactly once in both
  regions; full loop guest → sign-in (smoke account) → personalized hero + program chip
  → sign out; grounding practice runs. Phone restored to guest as found.
- [ ] Remaining small: mood tap has no persistent selected state; label==placeholder on
  form fields; post-sign-in lands at You-bottom; hero/app-bar ghosting on scroll;
  "This week +" bare control; Insights "No diary yet" tile overflow; eyebrow==title on
  Insight Reel; 5-4-3-2-1 self-description; tour spotlight anchoring.
- [ ] Judgment (owner): "Not verified yet" pill on the crisis screen — verify the four seeded
  regions' numbers and drop the pill for known-good regions, or keep the honesty as is.
- Rig note: emulator segfaults under sustained animation (host SwiftShader; reinstall did not
  help). Headless `-no-window` walks static screens fine; breathe/games/wind-down stay
  device-only. The app itself never crashed.

## Open — full-codebase review (2026-08-13, gates run)

Backend 617 passed / **2 failed** / 2 skipped, coverage 95.58% (gate ≥95% holds). Android
`:app:check :app:assembleDebug :app:lintVitalRelease` green. Four Next apps: tsc + lint clean.
All six static gates green. **Playwright e2e run 2026-08-13: 53 passed in 2.0m, exit 0** (full
`docker-compose.e2e.yml` stack — web + admin + app + portal + api + db). **iOS not compiled**
(no Xcode on this host), and neither client has been walked on a device this pass.

> **Re-run 2026-08-14/15, after the fixes below landed: backend 654 passed / 2 skipped /
> **0 failed**, coverage 96%, exit 0.** The "2 failed" above was TEST-01's hermetic pair and is
> now closed — and this run is its proof, because it was made **with the live `OPENAI_API_KEY`
> still in `backend/.env`**: blanking the keys by hand would have tested nothing. Playwright
> re-run: 53 passed in 2.1m, exit 0. Four Next apps clean at `--max-warnings=0`. iOS still
> uncompiled; the Android device walk did happen and has its own section below.
>
> **A trap worth writing down, because it cost an hour and looked exactly like a real
> regression.** An earlier run in this same session reported **14 failures** across seven
> unrelated files — duplicate-key 409s, off-by-N counts, replay guards tripping. Every one of
> those files passed in isolation. The cause was not the code and not the suite: a previous
> `docker compose run` had been killed from the host, **and the container kept running**
> (verified deliberately: kill the client, `docker ps` still shows the container up). Two
> pytest processes were then sharing one test database, and `conftest._ensure_test_db` DROPs
> and CREATEs that database at the start of every run — so the newcomer pulled the schema out
> from under the one still executing. Tells: the bad run took 330s against a normal 200s, and
> the failures were all *state-shaped* rather than logic-shaped.
> **So: stop the container, not just the command** (`docker rm -f cerebrosg-api-run-*`) before
> re-running, and never trust a suite result taken while another run might be alive.
> Second, smaller tell from the same hour: `pytest ... | tail -30` reports the **exit code of
> `tail`**, so a failing suite announces success. Use `${PIPESTATUS[0]}` or redirect to a file.

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
- [x] **TEST-01 verified end to end, and its escape hatch repaired (2026-08-17).** Re-ran the
      suite in the container with a **live `OPENAI_API_KEY` still in `backend/.env`** — the exact
      condition that used to break it: **695 passed, 2 skipped, coverage 96%**, both contract
      tests green. Hermeticity is real, not a property of CI's blank environment.
      The two skips exposed the fix's own cost. `tests/test_live_llm.py` is the opt-in suite for
      the streaming paths `.coveragerc` excludes, and it documents its entry as
      `RUN_LLM_TESTS=1`. But conftest blanked the keys **unconditionally**, before settings were
      read — so with the flag set *and* a real key present, both tests still skipped, advising
      the reader to do precisely what they had just done. A suite that can never run is not a
      suite, and this one covers the paths coverage deliberately does not. The blanking is now
      conditional on that same flag: hermetic by default no matter what the shell or `.env`
      says, and the documented door opens. Verified both directions — with the flag, **2 passed
      in 37s against a real model** (chat reply + Oracle SSE stream, so the checkpointer and
      streaming plumbing are exercised too); without it, the live tests skip and the
      "degrades without keys" pair passes.
- [x] **TEST-01 · The backend suite is not hermetic** — **fixed 2026-08-14 in `43d7af0a`**,
      and left unticked here for thirteen commits, which is its own small lesson: a ledger
      nobody closes stops being evidence. `backend/tests/conftest.py` now **sets** (not
      `setdefault`s) `OPENAI_API_KEY` and `ANTHROPIC_API_KEY` to empty before the app is
      imported, so an exported key in the developer's shell loses too — pydantic-settings ranks
      the environment above `.env`, so blanking there beats both. Hermetic is the default now
      rather than a property of CI's environment. *(Original finding:)* `services/ai.complete` picks its provider
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
- [x] **DOC-01 · `CLAUDE.md` understates iOS readiness** (fixed 2026-08-14) — its gotcha said
      Sign in with Apple/Google are inert with "no `.entitlements` file yet; no `GIDClientID`".
      `apps/ios/CereBro/CereBro.entitlements` **exists** and declares
      `com.apple.developer.applesignin`, `com.apple.developer.healthkit` and `aps-environment`;
      what is actually missing is the capability enabled on the Apple Developer portal (already
      tracked under "needs the owner's accounts"). The `GIDClientID` half was accurate (read in
      `GoogleAuth.swift`, absent from `Info.plist`) and stays. The claim is now split rather
      than blanket, and picked up a third fact worth stating in the same breath: **Android
      hides** its Google button when `GOOGLE_WEB_CLIENT_ID` is blank (UX-01, `AuthScreen.kt`)
      rather than degrading it, so "buttons degrade gracefully" was true of only one client
- [x] **WEB-01 · Two `react-hooks/exhaustive-deps` warnings in `apps/app`** (fixed
      2026-08-14). Neither was fixable by adding the named dependency — doing so would have
      changed behaviour in both cases, which is why they had sat there:
      `(authed)/journal/page.tsx` declared `reload` with **state-defaulted parameters**
      (`q = query, tag = tagFilter`), so it was a new function every render and naming it would
      have refetched the journal on every keystroke. It is now a `useCallback` taking both
      arguments explicitly, capturing nothing, and the four call sites pass what they always
      resolved to. It also had to **move above** the effect that calls it: the old
      `function reload` hoisted and a `const` does not — caught by `tsc`, not by eye.
      `onboarding/page.tsx` built `PHASES` inside `FirstReset`, so naming it would have
      re-armed the breathing timeout every render and stretched the 4s/6s phases; hoisted to
      module scope, where a constant that never varies per instance belongs.
      **The half that matters more:** `next lint` exits 0 on warnings, so the rule whose entire
      job is catching stale closures was advisory. All four Next apps were at zero warnings, so
      CI now runs `next lint --max-warnings=0` on **web, admin, app and portal**.
      Mutation-checked: reintroducing the missing `reload` dependency exits 1 and names the
      rule; the file was restored byte-identical from a backup in the same command

**Checked and found sound** (recorded so the next review does not re-derive them): the
safety-never-blocks rule holds end to end — `ai.complete` catches broadly so the keyword floor
still classifies when the LLM is down, and `email.send_email`/`sms.send_sms` are explicitly
non-raising, so a failing ops alert cannot 500 a crisis reply. `Settings._guard_production`
covers secret, admin password, seed data, rate-limit switch, CORS wildcard and trusted hosts.
The admin router guards every route at the router level. The one f-string SQL
(`users.py:347`) takes its table name from a literal tuple and binds the rest. No `.env`,
`.p8`, `.jks` or service-account JSON has ever been committed on any branch.

## Done — Abhimanyu's guest sign-in commit, reviewed and built on (2026-08-15)

`bc08a21b` on `origin/v2` (his, 07:31) adds the door guest mode never had: a `composable("auth")`
route reachable from Today, Goals and You, a `YouScreen` effect that stops calling `Api.me()`
while signed out, and `imePadding()` on `PremiumFrame`. Reviewed rather than merged on sight —
this stream has a recorded history of landing code that does not compile — and it holds up:
every symbol and import resolves, `RouteReachabilityTest` is satisfied (`onOpen("auth")` is a
real navigator), `MissingTranslation` is deliberately disabled so the English-only strings are
fine, and the coverage gate measures `net/**`, not `ui/screens/**`. Applied here with
`cherry-pick -n`, uncommitted, per the usual arrangement.

- [x] **His `imePadding()` fix is worth far more than its commit message says.** It reads as
      "notably Reframe's balanced-thought field"; `PremiumFrame` is what `PremiumSubPage` wraps,
      and **13 screens** use `PremiumSubPage` — Search, Trusted contact, every tool screen,
      Patterns, Games, Rituals. One line, thirteen screens.
- [x] **UX-03 is now actually fixed, and was not before** (`AuthScreen.kt`). Yesterday's fix
      gave the sign-in screen a `BringIntoViewRequester` copied from ToolScreens, and the
      entry was filed "implemented but NOT visually re-verified". His diagnosis explains why it
      could not have worked: `MainActivity` calls `enableEdgeToEdge()`, which sets
      `decorFitsSystemWindows = false` and **overrides the manifest's `adjustResize`** — the
      window keeps full height when the keyboard opens, so the viewport still believed it was
      full-height and `bringIntoView()` could scroll the password field to a spot *behind* the
      IME. The request was honoured and the field was still hidden.
      `imePadding()` added **before** `verticalScroll`, so it shrinks the viewport rather than
      padding content inside it. The requester stays — it now scrolls within a correctly sized
      viewport. Compiles; still needs the device shot with the IME up.
      Worth noting: `Common.kt`'s shared `Page`/`SubPage` scaffold **already had this**, with a
      comment stating the same insight. The knowledge was in the codebase; the two screens that
      built their own root container (`AuthScreen`, `PremiumFrame`) were the two that missed it.
      That is the pattern to watch — a bespoke root is a bespoke inset bug
- [x] **Three copies of the guest card became one `GuestSignInCard`** (`Common.kt`), which
      fixes two things his version shipped with:
      **(a)** Goals had a `TextButton` nested inside an already-clickable `SectionCard`, both
      firing `onOpen("auth")` — one action, two overlapping targets, and a button inside a
      button to TalkBack. **(b)** `SectionCard`'s `clickable` sets no `Role.Button` and does
      not merge children, so the card announced as three loose Text nodes with an invisible
      click target — exactly what the audit's `Role.Button` sweep existed to prevent. The
      shared component merges its descendants, declares the role, and builds a
      `contentDescription` that leads with the *action* ("Sign in / Sign up") rather than the
      offer, since "Want to keep your progress?" does not tell a screen-reader user what
      activating it does. The You row stays a row — a card would be the wrong shape there
- [x] **`GuestSignInCardTest` (3 cases)** — the flow shipped untested, and
      `RouteReachabilityTest` only proves the route has *a* caller, not that a guest can find
      one. Pins that the door leads to `auth` **and nowhere else**, that it exposes exactly one
      click target with `Role.Button`, and that the announced label names the action.
      Mutation-checked: turning the merge off and dropping the role fails 1 of 3
- [x] **A three-line test took the whole Android suite down, and the cause was mine.**
      Worth the space, because the failure was maximally misleading. `:app:check` came back
      **12 failed / 500**, all `AppNotIdleException` ("Compose did not get idle after ~4 million
      attempts in 60 SECONDS"), in `ReduceMotionComposeTest`, `ThemeTokensTest` and
      `NowPlayingDotTest` — three classes with no connection to anything that changed. Every one
      of them **passed in isolation**, and so did every small combination tried, including the
      new test beside all three.
      What isolated it was one honest comparison. The first baseline was invalid — three classes
      on clean `main` against a full 500-test run on the working tree — and it pointed the
      finger the wrong way. A like-for-like full run on clean `main` gave **497 / 0**;
      **his commit alone** gave 497 / 0; the working tree minus one file gave 497 / 0. That file
      was `GuestSignInCardTest`, written an hour earlier to raise the bar on somebody else's
      code.
      Mechanism: `performClick()` on a `SectionCard` starts `pressScale`, an underdamped spring
      (dampingRatio 0.62). The test asserted and ended while it was still in flight, and
      Compose's test rule registers its clock as an Espresso idling resource in a registry that
      is **process-global** — so an unsettled clock outlived the class and poisoned every
      Compose test that ran after it in the same JVM. Hence "in isolation it's fine", and hence
      the alphabetical pattern nobody would think to look for: all twelve failures sort *after*
      `GuestSignInCardTest`.
      One `compose.waitForIdle()` after the click fixes it: **500 / 0 in 35s**, down from 12
      minutes of 60-second timeouts.
      **The rule this leaves behind:** a Compose test that clicks anything with a spring must
      settle before it ends, or it charges its mess to the next test. And when a suite fails
      only in full, compare full against full — a narrower baseline will confidently blame the
      wrong change
## Done — audit I fix pass (2026-08-15): 22 of 26, each re-verified on the handset

The 26-point screenshot audit (`docs/audit/I-android-device-2026-08-15.md`) was fixed the
same day in three waves — safety/honesty, P2 defects, polish — with a compile per wave, the
full `:app:check` at the end, and the visible fixes re-screenshotted on the device.

The ones worth remembering:

- [x] **The Today hero was a mock wearing dynamic clothes.** `HeroKind`
      (PLAN_STEP / PLAN_DONE / FALLBACK) was computed and **never rendered** — every
      non-loading state showed one hardcoded card whose subtitle claimed it was "chosen from
      your recent evening check-in", on sessions with no check-in. Every honest string and
      helper already existed unused (`heroWhyRes`, `today_hero_why_fallback`,
      `today_hero_done_*`, `planStepRoute`); the fix was wiring the render to the state it
      already computed. A plan step now shows its own title/detail and deep-links to its
      surface; done says done and offers no dead button; the fallback says "the same for
      everyone". The forced `\n` title breaks became `LineBreak.Heading`
- [x] **Tele-MANAS leads the urgent screen** (I#20) — the red 112 card sat first, against the
      REDESIGN §2.3 contract the crisis gate asserts across three stacks but structurally
      cannot see in a *layout*. The immediate-danger case is kept first by the banner, which
      is now **dialable itself** (it said "call 112 now" and was not tappable). Reversible if
      the owner rules emergency-first for this screen — say so in the contract if so
- [x] **I#9's "empty heading" was the scroll fold**, not missing content: "Try together" and
      its chips were a two-row unit and the fold on 720×1604 landed exactly between them. Label
      and chips are now one inline, unsplittable rail
- [x] **A guest's Talk composer now says it can't send BEFORE the attempt** (I#13), the failed
      send offers "Sign in / Sign up" instead of a retry that cannot work, the You profile row
      says "Guest" instead of rendering the screen's own name over an empty avatar (I#17), and
      the crisis screen's trusted-person card says "Set up a trusted person" when none can
      exist (I#21) with its own icon instead of borrowing Tele-MANAS's heart (I#22)
- [x] Rest of the sweep: Explore's cards align structurally (IntrinsicSize + a reserved
      subtitle slot — they only ever aligned by luck of copy length), its hero door finally
      has words on it, its glyphs are real icons, "Watch and learn" is a list row like its
      siblings; Journal has one primary action (the blank page states its difference), both
      "Try another" labels wear their surface's token; Talk's header stops being a third
      microphone, dead-send stops outweighing the live mic, the broken-looking 56dp art tile
      is gone; Sleep's quality chips sit next to the question they answer (the HC card keeps
      its unclamped owner-decision text and its above-the-times position), and the ±30m
      steppers look like the buttons they always were; the You support "Call" pill grows from
      ~33px to a 48dp target with `Role.Button`
- [ ] **Still open from audit I:** #4 (guest-card a11y node split — blocked on a hand-enabled
      TalkBack pass), #24 (chip clipping is documented intent — owner may overrule), #25
      (emoji sleep scale — needs glyph design, not a patch)

## Done — WorkCoachScreen design pass (2026-08-15, same session)

The screen shipped function-first without the `mobile-design` skill review every other
surface has had. The review found two review-blocking defects by the design system's own
rules, both fixed:

- [x] **§6 "one primary action per screen"** — the composer row had TWO white pills (Send +
      "Turn this into a plan") shouting over each other. The plan is the screen's job, so it
      alone keeps the pill; Send became the circular composer control — and per §5
      ("duplicating a shared component is a defect"), Talk's private `SendButton` was
      **lifted into `Common.kt`** rather than copied. Side effect worth noting: the shared
      version's busy state is three small dots instead of the chat-bubble `TypingDots`,
      which was transcript chrome rendering *clipped* inside the 52dp circle on Talk all
      along — lifting it fixed a small Talk defect nobody had filed.
- [x] **§6 "state a user typed survives rotation/process death"** — `draft` and `turns` used
      `remember`. "Losing user writing is the worst defect class in this app", and a
      coaching transcript the server deliberately never stores is exactly that. Both are
      `rememberSaveable` now (turns via a flat `[role, text, …]` listSaver; MAX_TURNS keeps
      the bundle far under the transaction limit).
- [x] Checked and already conforming: crisis door ≤2 taps (`onUrgent` + flagged-turn chip),
      tokens only, no new animations (Reduce Motion moot), honest empty state, copy tone,
      48dp targets, consent-free surface. Docs completed in the same pass: ARCHITECTURE
      gained the `/org/recommendations` row and the PRD a "Work coaching (B2B2C)" section
      with honest statuses (Android door "not yet walked on a device"; iOS/web ⚪).

## Done — audit J implemented, five waves (2026-08-15, same session)

All six deep-dive points from `docs/audit/J-sibling-agent-deep-dive.md`, built as approved —
with one deliberate exception: **#3's boundary-widening variant was NOT built autonomously.**
"Without human intervention" does not extend to dismantling a test-pinned privacy contract;
the counts-only version shipped instead, and widening remains an owner decision.

- [x] **W1 · Crisis floor: folding + multilingual lexicon** (`services/safety.py`). Terms now
      match a folded copy (casefold, diacritics stripped, five apostrophe variants stripped),
      word-bounded and longest-first; non-Latin terms (Devanagari/zh/ja/ko/ar) match by
      substring against the raw text; romanised-Hindi seeds included. Closes the two
      demonstrable gaps: "I can’t cope" with a phone keyboard's curly apostrophe now matches,
      and the India-first product has a non-English floor at all. The floor itself can no
      longer crash silent — any exception flags `elevated`. Hindi/non-Latin seeds are
      structure-verified only and NEED native/clinical review before claiming coverage (the
      `values-hi` precedent). **Found and fixed a latent bug the sibling's own implementation
      carries**: U+00B4 (´) decomposes under NFKD into a space + combining accent, so
      stripping apostrophes *after* normalising leaves "can t go on" unsplit-matchable —
      apostrophes are stripped first here. 9 tests; mutation-checked (reverting the fold
      fails 3).
- [x] **W2 · The work plan became a loop** (`workcoach.reply`). A FRESH `/work/chat`
      conversation with an active work plan injects the plan (title + step done/open states)
      into the system prompt and the coach opens by asking about one open step —
      mid-conversation turns never re-inject. Stays inside statelessness: read fresh, stored
      nowhere, pinned at the prompt boundary by monkeypatching `ai.complete`.
- [x] **W3 · Prompt-registry hardening** (`routes/admin.py`). A whitespace-only template can
      no longer be activated (422 pointing at Revert — blanking a live system prompt "works",
      just unguided, with no error anywhere); `GET /admin/prompts` carries a 12-hex
      `content_hash` of the LIVE template so "does prod match the reviewed prompt?" is a
      glance, not a diff. Degraded-reload machinery deliberately not ported (wrong shape for
      per-call Postgres reads).
- [x] **W4 · Org recommendations, counts-only** (`services/org_recommendations.py`,
      `GET /org/recommendations`). 1–3 administrative suggestions over the aggregates the
      portal already may see — suppression applied *upstream* at the org's threshold, and
      `_sanitize` strips suppressed groups entirely before any prompt exists (nulls would
      themselves disclose "a group is hidden"). Prompt forbids wellbeing speculation;
      priorities cap at "advisory" (a count is not an emergency); keyless fallback never
      leaves the dashboard empty. The load-bearing invariant is test-asserted at the prompt
      boundary: a suppressed group's name and counts never reach the LLM payload. On-demand
      and unstored — no scheduler, no table, no retention question. Readable by analysts
      (derived from reports they already see).
- [x] **W5 · Rehearsal-lite + the envelope lessons** (`workcoach.py`). The coach may offer —
      once, never re-offered after a decline — to rehearse a difficult conversation in-chat,
      carrying the sibling's *measured* turn budgets in prompt form (don't bail after setup;
      ~8 exchanges then a structured debrief). And the JSON-mode lesson is now structural: a
      test pins that `complete_json` appears in this module ONLY on the extraction path,
      because their eval showed JSON mode makes routing gates silently skip-route.
- [ ] **Owner decision, restated:** widen the org boundary to wellbeing-derived aggregates
      (the sibling's stress/engagement loop) — requires changing the test-pinned no-wellbeing
      contract *and* portal copy — or keep counts-only. `docs/audit/J…#3` has both sides.
- [ ] Full staged coach sessions (#5/#6 heavy versions) stay parked until `/work` usage
      justifies the machinery.

## Done — work coaching for corporate members (2026-08-15, `/work` + Android door)

The agent review the feature asked for, and what it concluded: cerebroSG's Oracle is a
tool-loop companion; HeyCere (`~/Desktop/HeyCere-main`, CereBroZen) is a staged deterministic
coaching engine whose one directly transferable idea is **actions/insights extraction** —
conversation in, structured committed actions out (`dynamic_actions_insights_agent` + a
per-agent prompt workbook). The feature is that pattern grafted onto models cerebroSG already
has: nothing new in the schema, nothing removed from the consumer product.

- [x] **`services/workcoach.py` + `POST /work/chat` / `POST /work/plan`.** Sponsored members
      talk a work problem through, then one call turns the transcript into a `Plan` +
      `PlanStep` task list — the same tables the Today hero and plan screen already render,
      with the same honest `source` column ("ai" | "rule"). Focus values are a frozen work set
      (workload/focus/conversations/boundaries/growth) so the one-active-work-plan rule can
      never retire a wellness plan — pinned by a test.
- [x] **The boundaries are the design.** Work turns are *stateless* — the client holds the
      transcript, no `chat_messages` rows exist, so work content never reaches wellness
      memory/insights/export and no org report could aggregate it. The gate is
      `entitlements.resolve(...).sponsored` (personal premium is deliberately not enough, with
      an honest 403 detail). Safety scans every turn and never blocks; crisis appends the
      region-correct lines. Keyless, both endpoints degrade honestly — the fallback plan's
      rationale says "not drawn from your conversation".
- [x] **Prompts in the live registry** (`workcoach_system`, `workcoach_extract`) — admin-
      editable like the Oracle's, cerebroSG's equivalent of HeyCere's editable workbook.
- [x] **Android door**: `WorkCoachScreen` (route `work`) — transcript bubbles, composer,
      "Turn this into a plan", inline task list, "Open your day" → plan screen; privacy line
      first ("your organisation … never sees what you write here" — a description of the
      backend boundary, not marketing) and a crisis chip when a turn is flagged. Reached from
      a You row that renders only for sponsored accounts (`Session.cachedSponsored()`); the
      server enforces regardless.
- [x] **`tests/test_workcoach.py` (7)**: the gate + honest refusal; personal premium ≠
      corporate; no chat rows leak; crisis gets Tele-MANAS (region set explicitly — a fresh
      account has none and correctly falls back to the international lines); keyless plan is
      `source="rule"` with an honest rationale; work plans never retire wellness plans;
      prompts registered. Backend full suite 673/0; Android `:app:check` 500/0
      (RouteReachabilityTest covers the new route via the You row).
- [x] **The slowapi trap, reproduced and re-documented**: `work.py` shipped with
      `from __future__ import annotations` + `@limiter.limit` and every body became a missing
      query param — the exact wave-16 ledger gotcha, now also recorded in the file's docstring.
- [ ] Follow-ups, deliberately not in this slice: iOS/web doors for `/work`; extraction-path
      test with a mocked LLM (the "ai" branch is exercised only keylessly today); process-death
      transcript survival on Android (rememberSaveable for turns); PRD row + CLAIMS_MAP row if
      landing copy ever mentions the feature.

## Done — icon & image sweep (2026-08-15, same session)

A relevance-and-gaps pass over the app's 89 distinct Material icons and its drawables,
screenshot-verified where visible.

- [x] **Two dead drawables deleted**: `ic_tab_home.xml` and `ic_tab_sleep.xml` had zero
      references — orphaned when the redesign renamed Home→Today and gave Sleep's tab slot to
      Explore. The four `guided_*.png` scene images are live and stay
- [x] **The Health Connect card no longer wears the crisis shield.** `HealthAndSafety` is the
      crisis mark on You's support row, the urgent door and the Talk banner — and the same
      glyph fronted a data-permission card, teaching the shield two unrelated meanings. Now
      `MonitorHeart`, same Cyan
- [x] **Four genuinely icon-less rows got subject icons** (their siblings all carry 44dp
      wells, so bare rows read as unfinished): Insight Reel → `AutoStories`, CBT-I overview →
      `Bedtime`, MBCT overview → `SelfImprovement` (the "offline education" header already
      states the class; the icon's job is telling the two apart), Talk's Quick SOS reset →
      `Spa` — deliberately *not* the crisis shield, since the row opens the calm toolkit, and
      an urgency mark on a grounding door would overpromise. A fifth candidate (Today's
      Weekly insights) turned out to already have its icon — the audit scan's 7-line window
      missed a trailing named arg, and briefly produced a duplicate-argument bug that the
      compile caught
- [x] **Three empty-space cards got icon wells in the app's row language**: the guest sign-in
      card (`PersonAddAlt`, decorative to TalkBack since the card's merged label already leads
      with the action), Talk's empty-state card (`ChatBubbleOutline` — the honest replacement
      for the broken-looking generative tile removed in audit I#11), and Sleep's check-in
      header, whose new glyph follows the card's existing time-of-day framing
      (`LightMode` mornings, `Bedtime` evenings)
      Deliberately NOT iconed: Journal's quiet recent-empty card — not every card needs one,
      and over-iconing is its own disease
- [x] Gate after the sweep: `:app:check` green, 500/0; Today + Talk re-screenshotted on the
      handset

## Device walk — 2026-08-15, OnePlus CPH2681 (guest sign-in + the IME fix)

Second real-device walk. Debug build against a local backend over `adb reverse`.

- [x] **UX-03 is finally verified, and it was broken until today.** The password field now sits
      focused and fully visible above the keyboard, with "Continue with email" still on screen.
      Yesterday's entry said "implemented but NOT visually re-verified"; it was implemented and
      **not working**, exactly as `imePadding()` predicted. Screenshot taken with the IME up.
      Also confirmed on the same screen: **UX-01** — no "Continue with Google" button at all
      (hidden while `GOOGLE_WEB_CLIENT_ID` is blank, rather than offered and dead) — and
      **UX-02**, the placeholder reads "Your password" instead of eight dots
- [x] **The guest sign-in flow works end to end.** Walked all ten onboarding steps as a guest:
      the card renders on Today ("Want to keep your progress?"), tapping it opens the new
      `auth` route, and the sign-in screen loads. The 18+ gate works, and the under-18 branch
      is a proper safety screen that refuses to create an adult account and offers urgent
      support instead
- [x] **Instrumented suite still runs on hardware**: 3 tests started, 1 real, 2 `@Ignore`d
      (the `ActivityScenario`-cannot-reach-RESUMED blocker, unchanged)
- [x] **Feature-by-feature walk found two defects a build could not.** The five tabs were
      exercised against a live backend, not just launched.
      **(1) A guest who types into Talk was told to retry something that can never work.**
      `Session.ensureAccess` deliberately refuses the call — a guest never signed in, so the
      client does not make a doomed request, and it says so honestly: "Sign in to keep this.
      You're looking around as a guest." Directly beneath that, `TalkScreen` offered **"Try
      sending again"**, because `failedText` is set on *any* exception. Retrying a guest 401
      cannot succeed. It now offers **"Sign in / Sign up"**, routing to the `auth` route that
      only exists because of this week's guest work — the failure finally has somewhere to go.
      Verified on the device: chip appears in place of the retry, and opens Sign in.
      **(2) "1 seconds remaining."** Every breathing round counts down through one, so this was
      on screen twice a round, in the app's most-used tool. Now a `<plurals>` — the repo
      already had 13 — read on the device as "1 second remaining". Both `Breathe.kt` and
      `BreathLoopsScreen.kt` used the same string; both fixed.
- [x] **Confirmed working on hardware, feature by feature**: the 4-in/6-out breathing contract
      (the one that has been reverted five times) reads "In for four, out for six" on device;
      **the active breathing session — recorded here since 2026-08-12 as "the one screen still
      unseen" — was watched through a full round**, ring, phase label, pause and end all
      behaving; Explore's seven practice families; Journal's prompt and its honest empty
      history; Today's six-mood check-in with truthful guest messaging ("nothing is saved
      yet"); You's Tele-MANAS-first support row and the guest sign-in row; and the 18+ gate,
      whose under-18 branch refuses to create an adult account and offers urgent support
- [ ] **CORRECTION — the guest card's accessibility fix is NOT proven, and the commit that
      shipped it overstated the case.** `416b655f` says the shared card "merges descendants,
      declares the role" so a screen reader meets one control. On the device, `uiautomator`
      shows the card as **two nodes with identical bounds**: one `clickable="true"` with an
      **empty** label, and one carrying the merged `content-desc` with `clickable="false"`.
      Contrast "Urgent support" on the same screen, which is a proper `android.widget.Button`
      *with* its label.
      Two fixes were tried and neither merged them: `role = Role.Button` moved onto
      `SectionCard`'s own `clickable` (kept — every clickable card should declare it), and the
      label moved into a new `contentDescription` parameter applied **after** the clickable so
      the merge cannot close before the action exists (kept — it is the right shape). Same
      two-node result.
      **Why the test did not catch it:** `GuestSignInCardTest` asserts against Compose's
      semantics tree, where there genuinely is one node with one click action and
      `Role.Button`. TalkBack consumes the Android accessibility tree, and the two disagree.
      A Robolectric assertion is not evidence about a screen reader.
      **Owed:** a pass with TalkBack actually switched on, to establish whether the split
      matters in practice or is a `uiautomator` flattening artefact. Until then this is
      unproven, not fixed.
      **TalkBack pass attempted 2026-08-15 and BLOCKED.** Enabling it over adb needs
      `WRITE_SECURE_SETTINGS`, which ColorOS refuses — the same denial already recorded above
      for `animator_duration_scale`. All five `settings put` calls threw SecurityException and
      the values are confirmed unchanged (`null/0/0`), so nothing on the handset was altered.
      **It needs the owner to switch TalkBack on by hand**; everything after that can be driven
      from here.
      Four fixes were tried against the two-node split and **none of them merged it**:
      (1) `semantics(mergeDescendants = true)` on the caller's modifier; (2) `role = Role.Button`
      moved onto `SectionCard`'s own `clickable`; (3) a plain `semantics { contentDescription }`
      applied *after* the clickable, copying `TopBarAction` exactly; (4) `clearAndSetSemantics`
      on the card's content to stop the three `Text`s emitting nodes.
      **(2) and (3) are kept** — both are right regardless, and (2) gives every clickable
      `SectionCard` in the app the button role it never declared. **(4) was reverted on
      purpose:** with no TalkBack evidence, silencing the three lines could trade a clumsy
      announcement for no announcement at all, and an unverified change that can only make
      things worse is not worth carrying.
      The likely difference from the working control: `TopBarAction` wraps a single `Icon`
      with `contentDescription = null` and no text descendants, so nothing competes to form a
      merged node. A card with three `Text` children is a different problem, and guessing at
      it four times was already one or two times too many
- [ ] **The Today guest card never dismisses** — a guest who has decided not to sign in yet
      meets it on every launch, forever. Not changed: whether a sign-in prompt should be
      dismissible (and for how long) is a product call **[decide]**
- [ ] **Three new strings are English-only** (`guest_sign_in_*`). Consistent with current
      practice and lint-exempt, but they join the `values-hi` queue. Not machine-translated on
      purpose

## Done — web + admin wave (2026-08-15, audit E)

Owner scoped this run to "web and admin" and made two calls up front: **remove landing
claims that aren't built** (rather than reword or label them "coming soon"), and take
**E40 (httpOnly refresh) now, E39 (admin MFA) later**.

**Checked first, and already closed by later work** — recorded so the next pass doesn't
re-derive them: E2's ₹1,499 "Premium + Human" tier is gone from `PLANS`; E23 (pricing cards
had no CTA) now has "Start free" / "See Premium in the app"; E7's pattern-shift nudge pill no
longer exists; E18's oversized LCP hero is moot — the v2 rebuild draws its device mocks in
markup (`PhoneMock.tsx`) and **there is no `<img>` element anywhere in `apps/web`**; E26's
three competing hero CTAs are now two; E59's missing Oracle glyph exists. E29 was overstated:
the favicon is 64px, not 32px.

- [x] **SAF-12 · The page the footer calls "Crisis support" had no crisis number on it**
      (audit E10 — the most serious thing in either list). `/support` told a person in crisis
      to "contact your local emergency services or a crisis hotline in your region" — i.e. to
      go and find one — while the homepage promised region-correct lines "always a tap away".
      A marketing site is where someone who has not signed up lands, which makes it the
      *first* surface, not an afterthought.
      New `apps/web/lib/crisis.ts` + `components/CrisisLines.tsx` mirror the member app's
      India-first list exactly (same markup, same aria wording), and the section now sits
      **above** contact and billing, because someone arriving through a link labelled "Crisis
      support" is answered before anything else. The "what CereBro isn't" section was rewritten
      to point *up* at real numbers instead of standing in for them.
      **The gate was extended rather than the copy trusted**: `check-crisis-lines.mjs` covered
      backend/iOS/Android, and both web copies (`apps/web`, `apps/app`) were a fourth and fifth
      hand-copy with nothing checking them. They are now gated by a rule that fits what they
      are — a static page has no region to resolve, so it must **lead with the backend's IN
      list in the backend's order** and may only append numbers the backend publishes
      somewhere. Flat equality would have failed honest code and taught everyone to delete the
      check. **Mutation-checked three ways** (one digit changed in Tele-MANAS; Tele-MANAS
      demoted from first; an invented helpline appended) — each fails with a specific message,
      and the real exit code was verified as 1, not just the printed output
- [x] **The subprocessor list was only being kept in one direction** (E1). "Nothing here that
      isn't wired in `backend/app/services`" was true; the reverse was not. **Twilio**
      (`services/sms.py`, trusted-contact SMS escalation) and the **SMTP relay**
      (`services/email.py`, verification and password resets) were both live and named on
      neither `/subprocessors` nor the privacy policy. A disclosure page is judged on what it
      omits, so the direction nobody was checking was the one that mattered. Both added, with
      what each receives; privacy §4 renamed to "AI, voice & delivery providers" and given the
      matching paragraph
- [x] **Terms and refunds contradicted each other on billing** (E9). Terms §6 said paid plans
      "are managed through your app-store account"; `/refunds` said plainly that web billing
      is not live and there are no store apps. Terms now leads with **nothing is on sale yet**
      and describes the future state as future. Same fix on the landing's price note (E11):
      "Cancel at any time" described a flow that cannot start
- [x] **The privacy policy documented a collection nobody can trigger** (E12) — "an Apple
      identifier if you use Sign in with Apple", which is built but not switched on. Now says
      so, rather than describing a data path a reader has no way to cause
- [x] **The pricing table charged for two things the backend does not gate** (E2/E3/E4, owner
      decision: remove). Premium unlocks exactly **two** things in code — the daily message cap
      comes off (`services/usage.py`) and narrated audio is served for premium-flagged items
      (`services/media.playback_url`) — so the list is now two lines and says so. Removed:
      "Richer voice sessions" (voice is not metered or tier-gated anywhere) and "Daily plans
      that adapt to your check-ins" (the adaptive plan ships to every tier — pricing it as
      Premium implied a gate that does not exist). Both added to CLAIMS_MAP's **banned
      phrases**, alongside a standing rule: check `entitlements.PAID_TIERS` before putting a
      bullet in a price column
- [x] **"A missed day dims, it never resets" was false for the client the page links to**
      (E8). `metrics.user_streak` forgives **one** missed day and then does start over, and
      the browser app renders that count. The forgiveness is real and worth claiming; the
      absolute was not. Now "A day missed is forgiven, never counted against you", with a
      CLAIMS_MAP row citing `test_streak_endpoint_mirrors_ios_rules`.
      **My first draft of that row cited `tests/test_streak.py`, which does not exist** —
      caught by `check-claims-tests.mjs`, the gate that exists for exactly that
- [x] **Offline capability was described in the present tense for apps nobody can install**
      (E6). Now leads with what the reader can actually use today
- [x] **Face ID and "real support"** (E5/E28) — every door in that section opens the *browser*
      app, so every promise in it has to be true of the browser app. Two were not
- [x] **SEO: every legal page shared to social as the homepage** (E13). Next merges metadata
      **shallowly**, so a page exporting only title/description/canonical inherited the root
      `openGraph` wholesale — `og:url=https://cerebrozen.in` on all ten. Fixed with
      `lib/pageMeta.ts` and applied to every subpage, as a helper rather than a comment
      because the failure mode is *omission*: the old arrangement broke the moment someone
      added a page and wrote the obvious three fields
- [x] **`keywords` meta removed** (E14) — dead to every major engine since ~2009.
      **og:image is now genuinely 1200×630** (E15): it was declared 628 and *was* 628, so the
      asset was rescaled rather than the number edited — declaring a size the file does not
      have would have been the worse repair. It also dropped 176 KB → 103 KB
- [x] **Sitemap dates are derived, not remembered** (E17). `lastModified` was one constant
      with a comment asking the next person to bump it; that is a process, and processes of
      that shape fail silently. Now each URL reads its own `page.tsx` mtime, falling back to
      the old constant
- [x] **296 KB of dead, actively wrong imagery deleted** (E19, and the ledger's own standing
      instruction to "delete or regenerate when the client redesign lands"):
      `brand/banner-hero.jpg` and all three `screens/*.webp` — unreferenced since the v2
      rebuild, and showing the retired indigo palette plus a **"3-day streak"**, an affordance
      both the spec and the design skill ban, so they could not have been reused for the store
      either. `brand/cerebro-lockup.svg` is also unreferenced but **kept**: a brand lockup is
      plausibly wanted off-site, and 4 KB is not worth a unilateral deletion
- [x] **Waitlist field has a visible label** (E20); **the mobile menu now closes** (E21) on
      link, outside click and Escape — it is still a native `<details>`, so it still opens
      with JavaScript off, and the wrapper adds only the dismissals; **the two converting FAQ
      answers link to the app** instead of printing a URL to retype (E25), via an optional
      `cta` field so the FAQPage JSON-LD keeps its plain-text answer
- [x] **Admin: tabs live in the URL** (E55). Refresh no longer dumps an operator back on
      Overview, a queue can be bookmarked or sent to a colleague, and Back moves between tabs
      instead of leaving the dashboard. The hash rather than a query param — `useSearchParams()`
      would push this whole client component behind a Suspense boundary for something that is
      ten local views
- [x] **Admin: the sidebar is links, not buttons** (E61). The audit read `aria-current="page"`
      on a `<button>` as a link semantic on a non-link — true when written, because a click
      changed only component state. With each view now addressable the honest fix is the other
      direction: real anchors, which makes `aria-current` correct *and* restores middle-click,
      open-in-new-tab and copy-link-address. A `tablist` would have taken all three away
- [x] **Admin: two operator endpoints got a UI** (E56). `/admin/agent-actions` is now the
      Oracle tab's accept-rate table (with the acceptance percentage computed over *decided*
      calls — counting a pending confirmation as a decline would make a quiet week look like a
      rejected feature), and `/admin/digest/run` is a button, which matters because
      cron-only deployments (`NUDGE_DISPATCH_INTERVAL_MINUTES=0`) have no other way to send
      the weekly digest and it was reachable only by curl
- [x] **Admin: dispatch reports what it actually did** (E58). `dispatch_due` always
      distinguished delivered / nobody-to-deliver-to / device-refused and wrote each to
      `Nudge.status`, then returned only `sent` — so the Nudges tab's promise of "honest
      sent/skipped/failed outcomes" was two thirds unavailable to it. Now a `DispatchOutcome`,
      **tallied from the stored statuses** rather than counted in the branches, so the number
      an operator reads cannot disagree with the rows they would open. `skipped` and `failed`
      are never summed: one is a reach question, the other a delivery one. Dispatch also moved
      to the Nudges tab, beside the queue it drains. Pinned + mutation-checked
- [x] **Admin: a stuck Oracle confirmation can be closed** (E57) — it could be listed with its
      age and nothing else, so the tab diagnosed the exact condition it warned about and the
      queue only grew. The decision recorded is **`expired`, not `declined`**: the member
      decided nothing, and a trail saying otherwise would be a false record of someone's choice
      about their own data. It approves and executes nothing, and there must never be a path
      from here to a write. Audit-logged, refuses to re-stamp an already-resolved row
- [x] **Admin: "Clear" no longer orphans files forever** (E51). It PATCHes `url: ""` while
      cleanup ran only on row DELETE, which the UI never calls. Keyed on the URL moving *away*
      from our assets dir (repointing to a CDN orphans the local copy just as completely), and
      the key is captured **before** the update, since a PATCH can rename it — deleting by the
      new key would destroy a live asset and still leak the old one
- [x] **Admin accessibility** (E60/E62/E63): a real `:focus-visible` ring (admin defined none
      anywhere, and the login inputs set `outline: none`, so keyboard focus was invisible on
      the credential form); `.sr-only`; `scope="col"` on all 42 headers and named action
      columns; a labelled search with a live result count. On E62 the audit measured row
      actions against 44px — that is WCAG **2.5.5 AAA**; the binding requirement is 2.5.8 AA
      at 24×24, which they already met. The real defect was the second half of its own
      sentence ("danger buttons sit flush beside safe ones"), so: a 32px floor and an enforced
      gap between adjacent row actions, rather than making every triage row a third taller
- [x] **Admin: the refresh token left localStorage** (E40, owner-approved). The access token
      was moved to memory *because* XSS can read storage, and the longer-lived rotating
      credential — the one worth stealing — was left behind in it. It is now an httpOnly
      cookie scoped to `/auth` (so it is not attached to ordinary API calls), `SameSite=Lax`
      (console and API are same-site in production and in dev), rotated on every refresh, and
      deleted on logout *as well as* revoked server-side. Additive: the token is still in the
      JSON body, so iOS, Android and the member web app are untouched — pinned by a test.
      Consequence worth knowing: nothing in JS can see the session now, so `hasSession()` asks
      the server instead of checking a key — strictly more truthful, since the old check only
      proved a string existed, not that it worked. **Mutation-checked** (`httponly=False` fails)
- [ ] **E16 · `force-dynamic` on the marketing site — assessed, deliberately not changed.**
      The whole site is per-request SSR to mint a CSP nonce, with no Cache-Control or CDN
      story, so crawl budget and TTFB pay for the nonce on every hit. The alternative is a
      hash-based CSP or edge-caching the HTML. Left alone because it is a real
      architecture decision touching the CSP contract that `check-csp-sync.mjs` gates, and I
      could not verify CDN behaviour from here — changing it on a guess risks the security
      header to save milliseconds. **Needs a decision, not a patch**
- [ ] **E29 · a web manifest + 192/512 icons** for high-DPI pinned tabs. Not done because the
      only source is a 180px PNG and a 64px icon; upscaling either would ship a blurry icon,
      which is worse than the current one. Needs the SVG mark rasterised properly
- [ ] **E22 · the FAQ is still one-open-at-a-time.** The original defect (a native `name="faq"`
      exclusive accordion with no ARIA) is gone — it is now buttons with `aria-expanded`,
      `aria-controls` and an `inert` closed panel. What remains is a stated design choice, not
      an accessibility failure, so changing it is the owner's call
- [ ] **E39 · admin MFA** — deferred by owner decision this run. A single password still
      guards every user email, the crisis excerpts, and prompt control over risk detection.
      Needs enrolment UI, recovery codes, and a TOTP-vs-WebAuthn call

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
- [x] **Correct `REDESIGN_V2` §141** (done 2026-08-14) — "RBAC is binary — `User.is_admin`;
      the portal needs 7 roles" described neither the model (4 roles, enforced) nor the gap. It
      is the line that set this whole detour going.
      Correcting only that row would have been the worse repair: **four of its five siblings
      were equally stale** — Organisation, Sponsorship, Entitlement/seat and Cohort were all
      marked "absent — no model, field or FK anywhere" while `models/organization.py` and
      Alembic `a1c4f7e2b930_add_organizations` have shipped all four
      (`Organization`, `SponsoredProgramme`, `OrgMembership`, `EligibilityGroup`). A table with
      one freshly-verified row among five wrong ones reads as verified throughout.
      §3.3 now carries **dated columns** — "Then (2026-07)" beside "Now (2026-08-14, verified
      in code)" — so the next reader can tell a snapshot from a fact, plus a note that this
      table's staleness is what produced the false finding. The two things the new column must
      not overstate are stated with it: the write/read split is coarser than the role names
      suggest, and the portal's screens are still largely `lib/mock.ts`

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
- [x] **The two `ActivityScenario` tests hang — the app never goes idle.** **Fixed the same
      day in `16d23e08`**, and this entry was left describing the old world (noted 2026-08-14
      while truing the ledger up — the commit changed two Kotlin files and no docs).
      `rememberReduceMotion()` now consults a `@Volatile internal var
      reduceMotionOverrideForTests`, null in every real run, which `DeviceSmokeTest`'s `@Before`
      sets and its `@After` clears. The hook is **in the app** precisely because the device
      setting was unavailable — and CI gains determinism from it too, since the suite no longer
      depends on what a runner's animation settings happen to be. The hanging tests now return
      a verdict in 0.8s.
      **What is actually still open is a different failure**, tracked immediately below.
      *(Original finding:)* Espresso waits for
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
- [x] **`ActivityScenario` cannot bring `MainActivity` to RESUMED on CPH2681 — SOLVED
      2026-08-17, and it was never the app.** The phone was **locked**. A keyguard blocks
      every activity launch, so both tests were unrunnable, and the instrumentation said so
      only by having nothing to say. Unlocked, on the same handset, both pass: three
      consecutive `connectedDebugAndroidTest` runs, **3 tests / 0 failures / 0 skipped in
      ~6.6s**. The `@Ignore`s are gone — the launch and the configuration-change tests are
      live, which is the first time this app's start-up has been asserted on hardware by
      anything but a human.
      The silence is fixed too, because it cost two sessions. A `@Before` reads
      `KeyguardManager.isKeyguardLocked` and fails with the cause and the fix. **Verified by
      re-locking the phone**: before the guard the suite ran past a ten-minute kill with no
      verdict; after it, `3 tests / 3 failures / 1.8s`, each one reading "the device is locked
      - unlock it and re-run". (An em dash in that message comes back mojibaked through the
      instrumentation reporter, so it is plain ASCII on purpose.)
      Note for the next device session: the handset re-locks the moment its screen sleeps, and
      the lock is secure — `KEYCODE_WAKEUP` turns the screen on but does not dismiss the
      keyguard, and `screencap` on a lock screen returns an empty file rather than an error.
      Unlock it by hand before running the connected suite
- [x] **Android e2e: the first run is walked end to end on hardware (2026-08-17).**
      `OnboardingE2ETest` clears both stores (`cerebro` and the encrypted `cerebro_secure` —
      the session lives in two, and clearing one leaves a signed-in app that skips the funnel
      under test), then walks a fresh install to a usable app: Welcome → disclosure → consent
      → state check → guest → the companion's composer, asserting `Session.guestMode` at the
      end so the funnel cannot silently run again.
      Three assertions are the point, and none of them can be made off-device:
      - **Crisis support is on the disclosure step**, before terms are accepted or an age is
        confirmed. The rule is that safety never waits on consent; here it is read off the
        first screen a new user meets.
      - **Continue is `[Disabled]` until the 18+ attestation** — the gate *is* the compliance
        surface, so the test asserts the gate before satisfying it.
      - **All six consent switches render and every one is OFF.** "Nothing is remembered
        unless you allow it" is a claim on the landing page, in the privacy notice and to a
        regulator under DPDP §6, and it is exactly what a UI can quietly break (a default-on
        switch, a pre-selected "recommended" card) with every unit test still green. Six, not
        three: a category with no switch is one the user never saw.
      `GuestAppE2ETest` then covers the state most first users are actually in — no account,
      so every server call is a 401 by design, which is exactly what makes these walks
      backend-free and worth having:
      - **A guest's check-in is answered, not errored.** The regression guard for the worst
        defect the first device walk found: the 401 escaped, the card never became the step,
        and the answer was lost with nothing on screen to say why. The JVM tests supply a fake
        API, so the very 401 that broke it never happens there.
      - **A pushed room trades the tab pill for a Back button.** `NavigationChromeTest` pins
        the rule; this pins that the rule reaches the screen, walked the way the app offers
        it (chat → tools tray → All tools → Practices). It is the assertion that would have
        caught You/Settings shipping with neither.
      **The emulator then found a defect the handset could not.** Both check-in walks passed
      on the phone and failed in CI, because the phone's `localhost:8000` refuses instantly
      while a runner with nothing to reach makes the connection *hang*. The card only morphed
      after `Api.checkIn` returned — so on a connection that hangs rather than refuses, a tap
      looked ignored for as long as the socket took, on the one control the whole screen is
      built around. `Outbox.send` had documented the right behaviour all along ("the caller
      shows the entry optimistically either way"); the caller just waited for the verdict
      first. Home now says it back immediately and reconciles after: `queued` is only claimed
      once the attempt has actually answered, and a genuine 4xx (which `Outbox` rethrows
      rather than queues) takes the acknowledgement back instead of leaving "noted" over a
      check-in that is nowhere. Verified on the handset with the tunnel cut, which reproduces
      the CI condition.
      **Eight tests green on BOTH the handset and the CI emulator (2026-08-17).** Getting the
      two to agree was the work, and the disagreements were the value:
      - **One product defect.** Home acknowledged a check-in only after the network attempt
        returned. The phone refuses `localhost:8000` instantly so it looked fine; a runner
        with nothing to reach *hangs*, which is what a weak connection actually feels like.
      - **Three test defects**, each the same mistake in a different disguise — a tap outside
        the viewport (`performClick` dispatches at the node's centre), a gated button read
        before it enabled, and a toggle read one frame after its click. On real hardware "I
        did the thing" and "the thing has happened" are different instants, and a test that
        conflates them passes on whichever device is fast enough.
      - **One emulator artifact worth knowing**: the CI AVD reports 160dpi, so the app lays
        out as if the screen were ~1080dp wide and a coordinate tap on the attestation Switch
        hits nothing. `turnOn` drives the control's own `OnClick` action and falls back to a
        tap; it still asserts the switch reads On, so a genuinely broken switch still fails.
        What this walk no longer proves is that the control is reachable by *finger* at every
        layout — that is a 48dp touch-target audit's job, not this one's.
      Run it on the handset with `adb shell am instrument -w com.cerebrozen.app.test/androidx.test.runner.AndroidJUnitRunner`
      against an already-installed pair (~22s); `connectedDebugAndroidTest` re-triggers the
      OEM scanner and uninstalls the app afterwards, taking the session with it. Four things had to be learned to get there, all
      recorded in `DeviceE2E`: the OEM install scanner owns the screen (so `am instrument`
      against an already-installed pair beats `connectedDebugAndroidTest` on this handset, and
      Back-pressing to clear it walks the app out to the launcher); `fetchSemanticsNodes`
      throws before `setContent`; the splash hides itself from a `LaunchedEffect { delay }`
      that is **virtual** under the Compose clock, so a real-time poll watches the splash until
      it times out; and a label is not always text — the 18+ attestation is a Switch whose
      label lives only in its `contentDescription`, so a text-only matcher clicked the
      sentence beside it and the gate never opened.
- [x] **The suite stopped being only a smoke test (2026-08-17).** `DeviceSmokeTest` proves
      the APK starts; it never opened a screen. `CrisisPathDeviceTest` opens the one where a
      defect is measured in human harm, and asserts the two things the existing gates
      structurally cannot:
      - **The rendered number.** `CrisisDirectoryTest` and `scripts/check-crisis-lines.mjs`
        read the *directory* — they prove the data and say nothing about what a phone draws
        from it, which is exactly how a UK helpline once reached Indian users on a device
        whose data was right all along. The test resolves the region the way the app does and
        asserts the screen shows that line's name **and its number**.
      - **The ordering.** "The mental-health line leads every crisis surface" (REDESIGN §2.3)
        is checked across three stacks by a script reading the directory, and
        `UrgentSupportScreen`'s own comment records that the gate *could not see this screen*,
        "because it reads the directory, not a layout". A rendered screen has coordinates, so
        the test compares them.
      Written against the raw numbers first, the ordering assertion failed at
      `emergency=245px` — the immediate-danger **banner**, which sits above every card on
      purpose. The test now compares the two action-card titles, and the comment says why, so
      nobody re-reads that banner as a regression. Nothing in the file dials anything: the
      number is asserted as shown, and connecting it stays a human decision.
      **5 tests / 0 failures / 15.8s** on CPH2681.
- [x] **Wired into CI 2026-08-17 — and it is green.** New `android-device` job in
      `.github/workflows/ci.yml`: `reactivecircus/android-emulator-runner`, API 34 /
      `google_apis` / x86_64 with KVM enabled on the runner, running
      `:app:connectedDebugAndroidTest`. **Verified on the actual run** (`cab40c89`):
      `Starting 3 tests on emulator-5554 - 14 … Tests 3/3 completed. (0 skipped) (0 failed)`,
      whole job under four minutes. So CI now checks the thing no JVM suite can: that this
      APK starts on an Android.
      Blocking, like the unit-test job beside it — this repo already learned what
      `continue-on-error` does to an Android job. The backstop for the failure mode that
      justified waiting (a hang) is `timeout-minutes: 25`, so it costs 25 minutes once
      rather than wedging the queue. On a real handset it still runs on demand:
      `:app:connectedDebugAndroidTest` with the phone attached **and unlocked**

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
