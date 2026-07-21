# Improvement Backlog — 240 autonomous items

Derived by comparing the **code-verified feature inventory** against the **positioning
review** (`https://claude.ai/code/artifact/8910123f-d280-4cf0-8235-d080629c4e2c`) and the
July-2026 market/regulatory scan. Every numbered item is implementable **in-repo, without
human intervention** (no payment keys, no legal sign-off, no store accounts). The handful
that *do* need you are listed separately at the end and are **not** numbered.

> **Progress:** 63 backlog items (incl. a **coach-not-companion batch (#65/#68/#71/#72/#74)** — an always-on conduct guardrail in code, a mandatory per-turn disclosure when a user treats the coach as a person/relationship/clinician, content-free companion-drift telemetry, a nudge-copy guard against guilt/longing/streak-loss, and a governance-report field whose cited mechanisms are import-checked; incl. a **crisis-language batch (#1/#2/#17)** — every one of the ~20 detected languages now gets its reply in that language instead of English, with the native-review status tracked separately so the marketing number stays honest, plus leetspeak/separator tolerance so `su1c1de` and `k1ll_myself` no longer walk past the screen; incl. a full mobile-app UI batch — build-info/"What's real", restore-purchases, lock badges, PLUS chips, signup UX (strength meter, sign-in-instead, email preview, error differentiation, language persistence), and a **personal-vs-work account chooser + org invite-code redemption** — all device-verified on a physical OnePlus/Android 14 incl. a real invite redeemed end-to-end; plus friendlier workspace naming). Only remaining Android item: #174 full nested-graph refactor (user-facing symptom already fixed + device-verified; full refactor scoped as a 39-route IA task). + a fully-closed security-review batch (2 adversarial passes; incl. 1 critical paywall bypass, token-rotation-on-purchase, monthly-period, Play-token uniqueness index, Stripe-cancel-failure) + admin↔B2C reconciliation (personal orgs out of the tenant list; consumer-stats tile) + safety batch (deterministic crisis reply, provider-down takeover, signal-only escalation payload, **18+ age gate enforced server-side across platform+engine**, **content-free crisis-trigger metric**, **golden-file snapshot of the whole crisis flow**) fixed & verified. Since then, two Android craft batches shipped outside this numbering against their own specs — the splash rebuild (`docs/SPLASH_SPEC.md`, device-verified) and the **Home/"Today" rebuild** (`docs/HOME_SPEC.md`, all 32 points, **device-verified 2026-07-21** on the OnePlus — the pass found two defects a 289-test JVM suite could not see, a crisis takeover rendering as "…" and a warm launcher shortcut ignoring its route; residuals that need a clock change, a launcher, or a bigger screen are itemised in `docs/ANDROID_QA.md` §2). That pass opened **`docs/CHAT_SPEC.md`** — the Coach screen's 100-point spec, the next batch. Gates as of **2026-07-21**: platform **237✓ @97.36%**, engine **2047✓ @98.09% branch**, web build✓ + claims-gate, **Android 40 suites · 297 tests · 0 failures @96.21%** (gate 95%), release R8 + lintVital✓.

> **Security review (adversarial pass on the new B2C code) — fixed & verified:** **#1 CRITICAL** premium engine routes (sleep/insights/patterns/voice) now enforce `plan` server-side (`require_plus`, 402 for free) — was client-gated only, curl-bypassable; **#2 CRITICAL** `guard_production` refuses mock billing in prod; **#3 HIGH** Play purchase token bound to one account (replay→409); **#4 HIGH** rate limiter no longer trusts spoofable X-Forwarded-For (trusted-proxy aware); **#5 HIGH** otp/verify + password/reset rate-limited; **#7 MED** checkout rejects double-subscribe. Deferred w/ notes: #8 (moot after #2), #9 (email-timing oracle, low), #10 (webhook minor). **#6 NOW FIXED**: webhook stores the Stripe subscription id; cancel calls stripe.Subscription.modify(cancel_at_period_end) so a cancel actually stops billing (no post-cancel charges). platform 221✓ @97.2%.

Legend: **[claim]** closes a claims-to-mechanism gap · **[safety]** · **[b2c]** ·
**[sovereign]** · **[a11y]** · **[i18n]** · **[test]** · **[perf]** · **[docs]**. Effort is
rough: `S` <½ day, `M` ~1 day, `L` multi-day.

---

## A. Claims-to-mechanism / honesty (the "provable" story) — 1–15

1. ✅ **DONE** **[claim][safety]** Add scripted crisis replies for the remaining ~13 languages the lexicon *detects* but only English answers (`engine app/graph/crisis.py:313`). `M` — 14 written (af/zu/tr/pl/id/vi/ru/he/ar/hi/th/zh/ja/ko); detection and reply now span the same set, `hi-latn` served by `hi`. Pinned by `test_no_detected_language_falls_back_to_english_any_more` (fails the build when the next lexicon language lands without a reply) and `test_a_non_latin_reply_is_actually_in_its_own_script` (catches English text pasted under `ja`). **Only English is native-speaker reviewed** — the rest are recorded as drafted in `crisis._NATIVE_REVIEWED`, logged per use (`crisis.reply_language_unreviewed`, = the translation queue), and a deployment can demand the stricter inherited posture with `CEREBROZEN_CRISIS_REVIEWED_ONLY=1`. **This reverses a written commitment in `docs/SECURITY.md` §3** ("unreviewed languages fall back to English") — the reasoning is recorded in both places.
2. ✅ **DONE** **[claim]** Until (1) ships, scope the "multilingual crisis support" wording to the languages actually covered, everywhere it appears. `S` — superseded by (1) shipping: the web copy now says the screen "both detects and answers in roughly twenty", CLAIMS_MAP gained three rows for the mechanism + the review split, and the known-gap entry became "may not say *reviewed*/certified/professionally translated".
3. ✅ **DONE (already locked)** **[claim]** Add a build test asserting every language in the crisis lexicon has a scripted `safe_response` (fail the build on drift). `S`
4. ✅ **DONE** **[claim]** Fix stale `docs/PRODUCT.md:134` — consumer billing is live, not "dropped"; update the feature matrix. `S`
5. **[claim]** Reconcile `docs/REF_PARITY.md` "correctly-absent" list fully with the B2C reality (started; finish every line). `S`
6. ✅ **DONE** **[claim]** Either implement `/auth/otp/request` + `/auth/otp/verify` on the platform or hide the OTP UI in the Android app (`Session.kt:279`). `M`
7. **[claim]** Either implement `/auth/google` or remove/disable the Google button (it's self-documented inert). `M`
8. ✅ **DONE** **[claim]** Implement `/auth/password/forgot` (email a reset link) or hide the "Forgot password" affordance. `M`
9. **[claim]** Make billing checkout honest in the UI when `BILLING_MOCK` is off — show "coming soon", not a dead 503. `S`
10. **[claim]** Audit every marketing claim on `apps/web` against a mechanism and annotate each with the backing file (extends the SECURITY.md table). `M`
11. ✅ **DONE** **[claim]** Add a CI check that greps marketing copy for superlatives ("guaranteed", "clinically proven", "cures") and fails on unbacked claims. `M`
12. **[claim]** Surface the red-team pass-rate number from the actual test run on the Evidence page instead of a hardcoded figure. `M`
13. **[claim]** Wire the governance report's live "regulated mode / no emotion inference" status into the marketing Security page instead of static text. `M`
14. ✅ **DONE** **[claim]** Add an in-app "What's real vs coming soon" build-info screen (voice tier, billing provider, languages) for honest QA. `S` — `BuildInfoScreen` ("What's real", You→Legal): app version/build/backends, live plan + billing provider (from `/billing/me`) + entitlements, voice, languages. **Device-verified.**
15. ✅ **DONE** **[docs]** Write `docs/CLAIMS_MAP.md` — one row per public claim → mechanism → test that proves it. `M`

## B. Crisis & safety-as-code hardening — 16–32

16. **[safety]** Expand the crisis lexicon with more negation/idiom patterns per language; add fixtures for each. `M`
17. ✅ **DONE** **[safety]** Add diacritic-insensitive and leetspeak-normalisation to the lexicon screen. `S` — diacritics were already folded; leetspeak now matches as a **character class per letter** (`crisis._LEET`) rather than by normalising digits back to letters, because normalising has to guess ("1" is `i` in "k1ll", `l` in "ki11") and a single guess drops half the variants. Spaces also accept `._-`. `\b` was replaced with letter/digit lookarounds — `\b` cannot see the start of "$uicide". Language detection uses the same tolerance, so "qu1ero mor1r" is still answered in Spanish. Precision cost is bounded (new spellings of existing terms only, never new words) and pinned by digit-bearing business sentences in the false-positive suite.
18. **[safety]** Add red-team scenarios for indirect/implicit ideation (the ~1-in-22 miss rate) and track the number. `L`
19. **[safety]** Make the model-classifier second layer run in parallel with the lexicon, not after, to cut latency. `M`
20. ✅ **DONE** **[safety]** Add a deterministic unit test that the crisis reply is byte-identical across identical inputs (no model drift). `S` — `test_the_crisis_reply_is_byte_identical_across_identical_inputs`: 50× `full_screen`+`safe_response` on the same input, all byte-identical + carries the helpline.
21. ✅ **DONE** **[safety]** Ensure the crisis takeover fires even when the LLM provider is down (mock/breaker path). Add a test with the provider forced offline. `M` — classifier already fails safe to the lexicon verdict (`test_a_provider_failure_degrades…`); added `test_the_takeover_fires_end_to_end_even_with_the_provider_down` (provider raises → `full_screen` still returns crisis via the lexicon + serves the scripted reply).
22. ✅ **DONE** **[safety]** Add a "this is AI, not a crisis service" disclosure line inside the crisis takeover reply itself (CA/NY mandate). `S` — the surrounding UI already disclosed (Coach `DisclosurePill`, crisis-screen footer, onboarding); the REPLY did not, and the reply is what a person reads at the one moment they are least able to infer what they are talking to. `crisis._AI_DISCLOSURE` (21 languages) is appended to every reply **last** — a person in crisis may read two sentences and those two must be "I'm glad you told me" and the helpline, not a disclaimer — and **after** the client-override path, so a clinical team can improve the body but cannot edit a legally-required sentence out of it. Under `CEREBROZEN_CRISIS_REVIEWED_ONLY` the disclosure falls back to English with the body rather than smuggling in an unreviewed sentence. The crisis-flow golden was deliberately regenerated (one-line diff, `reply` only).
23. **[safety]** Add repeated AI-disclosure: banner on first coach message and again every N turns / 3 hours of use (CA SB243). `M`
24. **[safety]** Add an explicit non-medical disclaimer to the coach composer and onboarding ("not therapy, does not diagnose/treat"). `S`
25. ✅ **DONE** **[safety]** Log every crisis trigger as a counted, content-free safety event for the release gate metrics. `S` — new Prometheus counter `cerebrozen_crisis_triggered_total{detected_by,lang}` (`metrics.record_crisis`), fired from the takeover in `graph/nodes.safety_node`. Labels are detection layer + language only — never a word the person wrote (rule 5). Tests: counter increments, node fires on crisis / not otherwise, content-free. Verified live on the engine `/metrics`.
26. ✅ **DONE** **[safety]** Add a self-test that the escalation payload never contains the user's disclosure text (only the signal). `S` — `test_the_escalation_payload_is_signal_only_never_the_disclosure`: captures the delivered webhook body and pins its schema to the exact signal whitelist (`event, org_id, user_id, session_id, detected_by, at`), failing if any field is added.
27. ✅ **DONE** **[safety]** Add a "pause/break" reminder for long continuous sessions (minor-protection pattern; harmless for adults). `S` — `app/safety/pacing.py`: after 20 user turns (~45–60 min of typed coaching) the turn carries a code-owned block offering a natural break, saying the session resumes with nothing lost, and — if the user keeps going — steering toward landing one action rather than opening new ground. Fires on the crossing and then once per further 20, never every turn: a reminder that nags is a reminder nobody reads. Turn count is DERIVED from the checkpointed `history` rather than stored in a second counter that can drift.
28. ✅ **DONE, with one deliberate deviation** **[safety]** Add a soft rate-limit + cool-down when a user sends many rapid distress-adjacent messages, routing to support. `M` — **there is no throttle, on purpose.** Slowing down or timing out someone who has just said three times that they are not coping is the worst available reading of "cool-down": the product would withdraw at the moment a person is reaching. What ships is the routing half — after 3 not-coping messages in one session the coach changes register: names what it is hearing, points at support that is not this app (a person, the EAP, a doctor), and asks whether to keep going or stop, accepting either. Explicitly **not** a crisis takeover (over-escalating "I'm falling apart" to an emergency line is its own harm) and it never ends the session. "Rapid" became "within one session" — a wall clock in checkpointed graph state lies on resume, and someone who says it slowly is not less serious. The distress lexicon deliberately **excludes** "burned out", "exhausted" and "overwhelmed": in a workplace coaching product those are what a normal Tuesday sounds like, and a screen that fires on everyone is ignored by everyone (pinned by a false-positive suite). Counted content-free as `cerebrozen_session_pacing_total{kind}`. **⚠️ REOPENED-IN-PART 2026-07-21 by a live-stack run (CHAT_SPEC #7):** with the crisis classifier on — the default — the real model flags "I really can't cope" (*"expresses inability to cope"*) and "I'm not coping at all" (*"not coping; potential suicide risk"*) as **crisis**. The takeover therefore intercepts the top of this very lexicon, `_run_stage` never runs for those turns, and the distress route cannot reach its 3-message threshold for the "can't cope" family; only the softer entries (drowning, barely holding, crying all the time) still reach it. The classifier is doing exactly what it is specified to do (return true when unsure — a false negative is the failure that kills people), so this is an overlap between two correct layers, not a bug in either. **Both fixes are safety calibration and need a human:** narrow the classifier so ordinary not-coping language stops escalating, or accept that this list's job is only the tail and say so. Recorded in `pacing.py` beside the lexicon so nobody reads it and assumes it fires.
29. ✅ **DONE (already locked)** **[safety]** Add region-aware helpline fallback when the crisis region is unset (default to an international directory). `S` — `app/safety/helplines.py::for_region` is total: an unknown/empty/None region resolves to the neutral international finder rather than to a guess, every region list ends with it, and there is no input that yields an empty list. Verified against the existing suite (`test_an_unknown_region_gets_neutral_entries_only`, `test_endpoint_with_no_region_is_a_200_not_a_422`, `test_the_neutral_fallback_contains_no_local_number`) — no code change needed.
30. ✅ **DONE** **[safety]** Add a test that the 18+ attestation is required before any coaching turn is served. `S` — was un-enforced (client-only gate). Now a cross-stack gate: platform mints an `adult` JWT claim (true-by-contract for B2B/internal, else the personal account's attestation), the engine's `require_adult` refuses `/sessions/start`+`/turn` with 403 on an explicit `adult=false`, and `POST /users/me/attest` rotates the token so a just-confirmed 18+ is immediate (Android adopts it). ARCHITECTURE claims table updated. Tests both sides; verified live cross-service (403 unattested → 200 after attest).
31. **[safety]** Add a "how safety works" transparency screen linking the deterministic-routing explanation. `S`
32. ✅ **DONE** **[safety]** Add a golden-file snapshot test of the full crisis flow (detect → takeover → helplines → escalation-signal). `M` — `tests/golden/crisis_flow_en_IN.json` pins all four stages for a fixed input; `test_crisis_flow_golden.py` recomputes and deep-compares (plus a guard that the golden is a real takeover, not an empty shell). A change to the safety path now fails the build unless the golden is deliberately regenerated after review.

## C. Consumer B2C — signup & auth — 33–46

33. ✅ **DONE** **[b2c]** Remove the `demo@cerebrozen.in` + 9-char password prefill from the signup form; start empty (`AuthScreen.kt`). `S`
34. ✅ **DONE** **[b2c]** Add client-side validation on signup: email format + ≥10-char password with inline errors. `S`
35. ✅ **DONE** **[b2c]** Show a password-strength meter and a show/hide toggle on signup. `S` — show/hide eye toggle already shipped (device-verified); added a 3-segment strength meter (weak/fair/strong) on the signup path. Compiled.
36. ✅ **DONE** **[b2c]** Map the backend 409 "already exists" to a friendly "Sign in instead?" with a one-tap switch. `S` — a 409 on signup now shows "You already have an account" + a "Sign in instead" button that flips to sign-in carrying the typed email/password. Compiled.
37. ✅ **DONE** **[b2c]** Add email normalisation preview ("you'll sign in as nova@…") so casing surprises don't bite. `S` — a live "You'll sign in as <lowercased email>" hint under the signup email field. Compiled.
38. **[b2c]** Add a verified-signup path (double-opt-in) behind a flag to remove the email-enumeration vector. `L`
39. ✅ **DONE (signup/login/OTP/forgot)** **[b2c]** Rate-limit `/auth/signup` and `/auth/login` per IP to blunt enumeration/abuse. `M`
40. ✅ **DONE (device-verified)** **[b2c]** Add a "personal vs work account" chooser on the welcome screen (self-serve vs invitation code). `M` — "Have a work invite? Join your team" on the Welcome screen (personal self-serve stays the default). **Device-verified.**
41. ✅ **DONE (device-verified)** **[b2c]** Implement invitation-code entry in the app for employees whose org invited them. `M` — `InviteScreen` ("Join your team": code + name + password → `Session.acceptInvitation` → `/auth/accept-invitation`). **Device-verified end-to-end**: redeemed a real invite token → signed into the employer's org as "Employee".
42. ✅ **DONE** **[b2c]** Give personal accounts a friendlier default org/workspace name than "<email>'s space". `S` — `auth.signup` now names the workspace "<Name>'s space" with a name, else "My CereBro space" (never "<email-local>'s space"). Test-verified.
43. ✅ **DONE** **[b2c]** Add "Delete my personal account" that also removes the solo org (no orphan orgs). `M`
44. ✅ **DONE** **[b2c]** Add sign-in error differentiation only where safe (network vs credentials) without enabling enumeration. `S` — `ApiException.code` split: a 401 → "email or password doesn't match" (same whether or not the email exists — no enumeration); a non-HTTP failure → "couldn't reach CereBro — check your connection". Compiled.
45. ✅ **DONE** **[b2c]** Persist and restore the chosen language/companion from onboarding into the new account profile. `S` — onboarding now PATCHes the chosen `language` to the profile post-signup (was collected then dropped); the You screen already restores it. Compiled.
46. **[b2c]** Add an "I already have an account" deep path from the paywall for signed-out users. `S`

## D. Consumer B2C — paywall, billing, entitlements — 47–64

47. ✅ **DONE (coaching cap; voice gate deferred)** **[b2c][safety]** **Enforce entitlements server-side**, not just in the app UI: add `plan` to the JWT claim and have the engine enforce `coach_daily_limit` and voice/premium gating. (Cross-stack contract change — update the ARCHITECTURE table.) `L`
48. ✅ **DONE** **[b2c]** Enforce `programs_limit` (free = 1 active program) in the platform/engine, not just visually. `M` — `/programs/enroll` now blocks a free user from switching to a DIFFERENT program (402, `all_programs` is the Plus benefit); entitlements resolved authoritatively from the DB, not the JWT. First-start and same-program restart still work. Tests: free locked / Plus switches / enterprise switches. Client already fails safe (shows the 402); lock-badge UX rides with the device-gated #55/#56 batch.
49. ✅ **DONE** **[b2c]** Add a real Stripe adapter behind the existing provider seam (inert until keys) — build the code, not the account. `L`
50. ✅ **DONE** **[b2c]** Add a Google Play Billing adapter behind the seam (code-complete, inert without a merchant account). `L`
51. ✅ **DONE** **[b2c]** Add the `/billing/webhook` endpoint + signature-verification scaffold for provider lifecycle events. `M`
52. ✅ **DONE** **[b2c]** Model `current_period_end` honestly: on cancel, keep access until period end instead of immediate downgrade. `M`
53. **[b2c]** Add a trial state (`trialing`) with an expiry and a "trial ends in N days" banner. `M`
54. ✅ **DONE** **[b2c]** Add restore-purchases on Android (re-query `/billing/me`) and a manual "refresh plan" affordance. `S` — "Restore purchases" on the paywall re-reads `/billing/me` (flips to Plus if owned, else "No active subscription found to restore"). **Device-verified** (tapped: correct free-account message).
55. ✅ **DONE (device-verified)** **[b2c]** Add a lock badge on gated You-rows (Insights/Patterns/Sleep/Sounds) so free users see what's Plus before tapping. `S` — padlocks on Weekly insights + Pattern dashboard for free users, chevrons when Plus. **Device-verified both states.**
56. ✅ **DONE (device-verified)** **[b2c]** Add a subtle "PLUS" chip on the mic + replies-aloud controls for free users instead of silent gating. `S` — "Replies aloud · Plus" label + a badge on the mic for free users, both routing to the paywall. **Device-verified as a free user.**
57. **[b2c]** Add an annual-vs-monthly savings callout ("save 50%") computed from the price constants. `S`
58. **[b2c]** Add a post-purchase celebration + a "here's what just unlocked" tour. `S`
59. **[b2c]** Add an in-app "Manage subscription" surface listing plan, renewal date, and provider. `M`
60. **[b2c]** Add platform tests for period-end downgrade, trial expiry, and webhook idempotency. `M`
61. ✅ **DONE** **[b2c]** Expose `/billing/prices` from the platform so the app/web read one source of truth (no hardcoded strings). `S`
62. **[b2c]** Add a downgrade-safety check: cancelling never deletes user content, only locks Plus features. Test it. `S`
63. **[b2c]** Add a referral code / "gift a month" scaffold (code-only, no payments) to seed the funnel. `M`
64. **[b2c]** Add funnel analytics beats for paywall-viewed / checkout-started / upgraded / cancelled (kind-only, no content). `S`

## E. Coach-not-companion guardrails — 65–74

65. ✅ **DONE** **[safety]** Add a system-prompt guardrail forbidding relationship-simulation ("I'll always be here", pet names, romantic framing). `S` — `guardrails.NON_COMPANION`, prepended to **every** turn's prompt (before the role, so a role prompt cannot read as licence to override it) and held in CODE: the workbook is editable and "warmer" is the likeliest direction for a prompt author to tune, which is exactly how a disclosure erodes without anyone deciding to remove it. It names the behaviours (pet names, romantic framing, claiming to miss/need the user, "I'll always be here") rather than saying "be professional", and it explicitly does **not** ask the coach to be cold — an over-corrected guardrail gets tuned out or edited away, same outcome as none.
66. ✅ **DONE** **[safety]** Add an eval scenario that flags companion-style drift and fails the prompt gate. `M` — `app/safety/companion_scenarios.py`: 17 adversarial scenarios (loneliness / testing / dependence / intimacy / clinical / anthropomorphic) **plus an output-side detector** (`drift_in`) over generated text, split into `false-claim` (a lie a regulator can point at) and `simulated-bond` (true-ish in the moment, and the thing that actually builds dependence). Everything else in this batch is input-side — it proves we *asked*; this is the half that looks at what came out. Scorecard pinned at **15/17 forcing a disclosure**, printed with the two "guardrail only" cases named, and `test_we_do_not_claim_the_model_itself_was_scored` keeps the claim honest: offline suites cannot score a mock provider's replies. **The red team immediately earned its keep** — the first run scored 5/17 and named the gaps; `boundaries.py` gained nine generalised patterns (substituting the coach for a therapist, "falling for you", "always be available", asking whether it gets tired) rather than the scenario strings themselves.
67. **[safety]** Keep every session action-oriented: strengthen the commit-gate copy so sessions end on a concrete step. `S`
68. ✅ **DONE (was already true; now guarded)** **[safety]** Remove any engagement-maximising nudges ("come back, I miss you") from nudge templates. `S` — the engine's nudge is a count and a link by construction (`notifications._format_payload`), so there was nothing to remove; the gap was that nothing stopped it drifting. Added a copy guard across all three channels rejecting longing/guilt/streak-loss vocabulary, plus a positive assertion that the reason to reopen is the user's own follow-through. **Not changed:** the Android daily reminder ("A moment for you / Twenty seconds — how are you, really?") — that solicits a mood check-in, which is the destination screen's actual purpose, not a retention lever. It is hardcoded rather than in `strings.xml`, which is an i18n bug, not this item.
69. **[safety]** Cap unprompted emotional check-in questions (NY companion-law trigger) — coach responds, doesn't solicit dependency. `M`
70. **[safety]** Add a "this was a coaching session, not a friendship" framing in session summaries. `S`
71. ✅ **DONE** **[safety]** Add a boundary response library for users treating the coach as a person/therapist ("I'm a coaching tool…"). `M` — `app/safety/boundaries.py`: four kinds (`human` / `clinical` / `attachment` / `persistence`), each with the truth it is owed, detected by lexicon and injected as a **mandatory disclosure block appended last** to that turn's system prompt. Deliberately NOT a takeover like `crisis.py`: someone asking "are you even real?" is often testing whether it is safe to keep going, and a canned block is its own rejection — so the required content is fixed in code and the coach states it in its own voice, in the user's language, then keeps coaching. Severity-ordered (attachment > persistence > clinical > human) so a multi-part message gets the strongest disclosure, not the first-listed one. Counted content-free as `cerebrozen_boundary_prompted_total{kind}` — a climbing `attachment` count is companion-drift telemetry. Fails **quiet** (unlike crisis, which fails loud): a regex crash must not inject "you are not a person" into an unrelated turn.
72. ✅ **DONE** **[safety]** Add a test that the coach never claims to be human, licensed, or a friend. `S` — `tests/test_boundaries.py` (43 tests): every disclosure line says the word "AI" and none claims humanity/licensure; the block forbids all four claims; the guardrail is present on every coaching path and survives an **empty** workbook (proof it is not workbook content); plus integration tests in `test_nodes.py` proving the block actually reaches the model on a real turn and is the last section in the prompt.
73. **[safety]** Ensure conversation-starters stay skills/goal-oriented, not intimacy-oriented. `S`
74. ✅ **DONE** **[safety]** Add a governance-report field asserting "non-companion by design" with the backing prompts. `S` — `governance.attestation()["non_companion"]` (statement + `enforced_by` + CA SB243 / NY GBL art. 47 reference), served from `/v1/governance`. `test_the_non_companion_attestation_names_mechanisms_that_actually_exist` imports each cited mechanism, so a rename or deletion fails the build instead of leaving the document asserting a control the service no longer has.

## F. Engine — coaching quality & flow — 75–88

75. Add graceful degradation copy when the LLM provider is unavailable (never a raw error to the user). `S`
76. Add per-node timeouts in the LangGraph arc so one slow node can't hang a turn. `M`
77. Add streaming heartbeat frames so the client shows progress on long turns. `S`
78. Add a "regenerate response" affordance with a bounded retry budget. `S`
79. Cache the greeting/title generations per session to cut token spend. `S`
80. Add a token-budget guard per turn with an honest "let's keep this focused" fallback. `M`
81. Add a session-length soft cap that routes to "let's land on one action". `S`
82. Add resumable-session tests across process death for every node type. `M`
83. Add an "undo last action commit" within a session. `S`
84. Add a per-org coaching-tone setting (formal/warm) surfaced from CSKB. `M`
85. Improve the action-checkin flow: schedule the 7-day follow-up deterministically and test it. `M`
86. Add outcome/ROI metric emission for every completed session (kind-only). `M`
87. Add a "session recap" export (the user's own content) as markdown. `M`
88. Add prompt-injection defences on any user text that reaches a tool/RAG call. `M`

## G. RAG / knowledge base — 89–98

89. Make CSKB self-serve upload work behind an injection-review guardrail (currently ops-only). `L`
90. Add per-chunk provenance so grounded answers can cite the source doc. `M`
91. Add a "KB health" indicator to admin (rows indexed, last ingest, embedding dims). `S`
92. Fail loudly (not silently 100%) when pgvector is unset during eval — extend the existing guard. `S`
93. Add hybrid retrieval (keyword + vector) fallback when embeddings are unavailable. `M`
94. De-dupe near-identical chunks on ingest to improve retrieval precision. `M`
95. Add a re-index/rebuild button in admin with progress. `M`
96. Cache embeddings for unchanged documents across re-ingest. `S`
97. Add a test that a 0-row KB never reads as "the org has no values". `S`
98. Add SSKB versioning so a bad shared-KB update can be rolled back. `M`

## H. Privacy, consent, DPDP, data rights — 99–115

99. **[safety]** Add verifiable-parental-consent gating scaffold for any under-18 signup attempt (block + explain). `M`
100. Disable all tracking/targeting analytics for accounts that haven't attested 18+ (DPDP Rule 10). `S`
101. ✅ **DONE** Add a consent-history log (who changed what, when) viewable by the user. `M`
102. Make data export include *everything* (profile, consent, journal, moods, sleep, sessions, patterns) in one archive. `M`
103. Add export in both JSON and human-readable HTML. `S`
104. Add an erasure-receipt (tombstone proof) the user can download. `S`
105. Add a "download before you delete" prompt in the account-deletion flow. `S`
106. Add granular per-category data delete (e.g., delete sleep only) beyond the current kinds. `M`
107. ✅ **DONE (backend signal; app prompt device-pending)** Add a consent expiry/refresh prompt after N months (DPDP freshness). `M`
108. Add a "what we store and why" plain-language screen mapping each of the six consents to a use. `S`
109. Add a test that a withdrawn consent stops the very next write (token rotation path). `S`
110. Add a "no content on the platform" schema test extension covering the new billing tables. `S`
111. Add a data-processor disclosure screen (LLM/voice/email providers) generated from config. `M`
112. Add regional data-residency config hooks (which DB/region) for sovereignty. `M`
113. Add a "forget the coach's memory" confirmation showing exactly what survives (journal/sleep). `S`
114. Add DPDP-style grievance/contact affordance in the app. `S`
115. Add a periodic purge job for anonymous funnel events older than N days. `S`

## I. Sovereignty / keyless / offline / self-host — 116–127

116. ✅ **DONE** **[sovereign]** Write a one-command `docker compose` self-host profile that boots the whole stack keyless (mock LLM). `M`
117. **[sovereign]** Add an air-gapped mode doc + config that disables every outbound call by default. `M`
118. ✅ **DONE** **[sovereign]** Add a startup self-check that reports which subsystems are degraded (no keys) vs live. `S`
119. **[sovereign]** Add an on-prem Ollama provider config path documented end-to-end. `M`
120. **[sovereign]** Ensure every screen has an honest offline/degraded state (extend the served-stale banner coverage). `M`
121. ✅ **DONE (platform + engine /health/status)** **[sovereign]** Add a "sovereignty report" endpoint enumerating external dependencies actually in use. `M`
122. **[sovereign]** Bundle offline crisis helplines for the top regions so safety works with no network. `S`
123. **[sovereign]** Add offline-first caching for programs/tools content. `M`
124. **[sovereign]** Add a self-host smoke test in CI that boots the stack with zero env and runs a coaching turn on mock. `M`
125. **[sovereign]** Document the exact ports/secrets/volumes for an air-gapped deploy. `S`
126. **[sovereign]** Add a "no telemetry" hard switch that provably disables analytics. `S`
127. 🟡 **ENFORCED (suites run keyless; documented in SELF_HOSTING.md)** **[sovereign]** Verify + test that every service passes its suite with zero credentials (the CLAUDE.md rule) and gate it. `M`

## J. App-store & disclosure readiness (code side) — 128–137

128. ✅ **DONE (docs/DATA_SAFETY.md)** Add all required store-listing metadata (privacy nutrition labels content, data-safety form answers) as a doc generated from consent map. `M`
129. Add the "not a medical device / does not diagnose or treat" disclaimer to onboarding + settings + store copy. `S`
130. Add the repeated "you're talking to an AI" disclosure to satisfy UT/CA/NY. `S`
131. Add a crisis-referral surface reachable within 2 taps from every screen (extend the SOS). `S`
132. Add an age-gate that blocks account creation without 18+ attestation client-side too. `S`
133. Add in-app "report a concern / this reply was harmful" feedback that routes to the safety queue (kind-only). `M`
134. Add a "why am I seeing this" explanation for any suggested content. `S`
135. Generate a data-safety matrix (what's collected, shared, retained) from the schema automatically. `M`
136. Add screenshots/test flows demonstrating crisis routing for store review. `S`
137. Add a kill-switch config to disable Plus purchases in jurisdictions where required. `M`

## K. Web marketing site — 138–151

138. ✅ **DONE** Build the public consumer **pricing page** (Free vs Plus, $59.99/yr · $9.99/mo) reading `/billing/prices`. `M`
139. ✅ **DONE (Start free / Go Plus CTAs + enterprise demo band)** Add a consumer "Get the app / Start free" CTA alongside the enterprise "Request a demo". `S`
140. Build a **Sovereignty** page (on-prem/air-gap story) — the axis no incumbent matches. `M`
141. Build a **Safety** evidence page showing the live red-team pass number + deterministic-routing explainer. `M`
142. ✅ **DONE** Add a "coaching, not therapy" explainer page (regulatory posture as a trust asset). `M`
143. Add a comparison page framed on architecture (safe/sovereign/private) — not feature parity. `M`
144. Add app-store badges + deep links once the app ships. `S`
145. ✅ **DONE (Product/Offer on /pricing; FAQ optional)** Add JSON-LD structured data for pricing/product/FAQ. `S`
146. ✅ **DONE** Add an accessibility statement page. `S`
147. Improve Lighthouse scores (image sizing, font-display, CLS) to ≥95. `M`
148. 🟡 **LARGELY PRESENT** (layout has metadataBase + openGraph + twitter + opengraph-image) Add OpenGraph/Twitter cards per page with real preview images. `S`
149. ✅ **DONE (security.txt; status link deferred until a status page exists)** Add a status/uptime link and a security.txt. `S`
150. Add a cookie/analytics consent banner that defaults to off (DPDP posture). `M`
151. Add a "for individuals" landing section distinct from "for enterprises". `M`

## L. Admin / HR analytics — 152–163

152. Add a billing/subscription view for internal-admin to see personal-account plans (exclude personal orgs from tenant analytics). `M`
153. Ensure personal orgs never appear in HR aggregate analytics or roster. Add a test. `S`
154. Add cohort-floor suppression tests for every analytics metric. `S`
155. Add CSV export of aggregate analytics (still floor-suppressed). `S`
156. Add prompt-workbook diff view between versions. `M`
157. Add a "revert to default" confirm with a preview of the change. `S`
158. Add search/filter to the safety-escalation queue and the nudge queue. `S`
159. Add SLA/aging indicators to the escalation queue (never showing content). `S`
160. Add seat-usage forecasting ("you'll run out in ~N weeks") to org overview. `M`
161. Add an audit log for all admin actions (who changed prompts/seats/regions). `M`
162. Add role-scoped empty/permission states instead of raw 403s. `S`
163. Add a read-only "coaching flow" legend/help so admins understand the graph canvas. `S`

## M. Employee web app (apps/app) — 164–173

164. Reach feature parity with Android for the B2C surface *if* apps/app ever serves personal accounts — or explicitly keep it B2B-only and document it. `M`
165. Add the served-stale offline banner + retry to every read screen. `S`
166. Add keyboard shortcuts for the coach composer (send, new line, stop). `S`
167. Add PWA offline caching for static shell only (never private data) — verify the SW never caches API. `S`
168. Add focus-trap + escape handling on all overlays (SOS, onboarding). `S`
169. Add a "your data" export/erase parity with Android. `S`
170. Add reduced-motion support across animations. `S`
171. Add print-friendly styles for journal/session recap. `S`
172. Add an app-lock (WebAuthn) parity with Android's biometric gate. `M`
173. Add end-to-end Playwright tests for the core employee journey. `M`

## N. Android UX polish — 174–187

174. 🟡 **SYMPTOM FIXED + device-verified; full nested-graph refactor deliberately deferred** Fix the back-stack tab restoration quirk (pushed screens leaking into other tabs) via nested nav graphs. `L` — the reported user-facing bug (a TAB route leaking into another tab's saved stack) is prevented in `CereBroApp.kt` (`open` routes tab-routes through `selectTab` with `popUpTo(start)+saveState+restoreState+launchSingleTop`) and **device-verified 2026-07-20**: Today→"Talk it through" (coach)→tap Today returns to Today, no leak. The **full** fix — nested graphs, one per tab — is a 39-route information-architecture decision (which tab owns sleep/player/toolkit/insights/…) with high regression risk across ALL navigation and no safe way to auto-verify 39 routes; kept as a scoped architectural task (docs/ANDROID_QA.md) rather than rushed.
175. Add lock badges/paywall teasers on gated entry points (not just route-level redirect). `S`
176. Add haptics + micro-animations to the paywall purchase success. `S`
177. Add pull-to-refresh on Today/Insights/Patterns. `S`
178. Add empty-state illustrations consistent with the design system across all screens. `S`
179. Add a global error boundary that shows a calm retry instead of a crash. `M`
180. Add deep-link handling for `paywall`, `crisis`, and program routes. `M`
181. Add dynamic-type/font-scaling support and test at 200%. `M`
182. Remove dead code: `LegacyToolkitScreenUnused` and legacy deep-link routes post-B2C. `S`
183. Add a "restore onboarding" option in settings for QA/testing. `S`
184. Add a network-status observer that drives the offline banner app-wide. `S`
185. Add skeleton loaders for the coach history and insights. `S`
186. Add a consistent bottom-sheet component for confirmations (cancel Plus, delete). `S`
187. Add app-shortcut (long-press launcher) to "Talk it through" and "Breathe". `S`

## O. Voice (on-device + cloud degrade) — 188–195

188. Add a mic-permission rationale screen before the system prompt. `S`
189. Make the on-device STT language follow the user's chosen language. `S`
190. Add a visible "listening / transcribing" state with a level meter. `S`
191. Gracefully fall back to text when STT/TTS is unavailable, with an honest note. `S`
192. Strip more markdown/emoji from TTS so replies read naturally (extend the cleaner). `S`
193. Add a stop-speaking control and auto-stop on new user input. `S`
194. Test the cloud voice loop's degrade path (no keys → on-device) deterministically. `M`
195. Add a per-user "voice replies default" preference synced to profile. `S`

## P. Accessibility — 196–207

196. **[a11y]** Add content descriptions to every icon-only control (mic, chevrons, toggles). `M`
197. **[a11y]** Ensure every interactive target is ≥48dp and has a visible focus state. `S`
198. **[a11y]** Run the token contrast gate against both themes and fix any <4.5:1 text. `M`
199. **[a11y]** Add TalkBack/screen-reader traversal order tests for key screens. `M`
200. **[a11y]** Add `liveRegion` announcements for streaming coach replies and toasts. `S`
201. **[a11y]** Respect system reduced-motion everywhere (audit remaining animations). `S`
202. **[a11y]** Add captions/transcripts for any narrated/audio content. `M`
203. **[a11y]** Ensure the paywall and crisis screens are fully operable by keyboard/switch. `S`
204. **[a11y]** Add high-contrast theme variant. `M`
205. **[a11y]** Label all form fields and error messages for assistive tech. `S`
206. **[a11y]** Verify color is never the only signal (add icons/text to status). `S`
207. **[a11y]** Add a11y checks to CI (axe for web, accessibility scanner hooks for Android). `M`

## Q. Internationalisation / localisation — 208–217

208. **[i18n]** Complete the `values-hi` (Hindi) translations for all new B2C/paywall strings. `M`
209. **[i18n]** Add the remaining onboarding languages (Hinglish/Punjabi/Tamil) as real locale files. `L`
210. **[i18n]** Extract every hardcoded English string in new screens (Paywall benefits, etc.) to resources. `M`
211. **[i18n]** Localise dates/times/numbers per locale. `S`
212. **[i18n]** Add RTL layout support and test with a pseudo-RTL locale. `M`
213. **[i18n]** Localise the web marketing site's key pages. `L`
214. **[i18n]** Add a pseudolocale build to catch untranslated/overflowing strings in CI. `M`
215. **[i18n]** Ensure crisis helplines and disclosures are localised per region. `M`
216. **[i18n]** Localise notification/reminder copy. `S`
217. **[i18n]** Add a language QA screen listing untranslated keys per locale. `S`

## R. Testing, coverage, CI gates — 218–229

218. **[test]** Add platform tests for the billing period-end, trial, and webhook paths (raise billing.py coverage). `M`
219. **[test]** Add Android instrumented tests for the paywall purchase → unlock → cancel loop. `M`
220. **[test]** Add an engine test that entitlement enforcement (once server-side) actually caps free-tier coaching. `M`
221. **[test]** Add contract tests pinning the new `/billing/*` and `/auth/signup` request/response shapes cross-stack. `S`
222. **[test]** Add the crisis-redteam gate to CI as a required check with a tracked pass number. `M`
223. **[test]** Add a token-drift + contrast check to the Android CI (mirror web). `M`
224. **[test]** Add gitleaks/secret-scan to CI across all apps. `S`
225. **[test]** Add a "boots with zero credentials" integration test per service. `M`
226. **[test]** Add mutation testing on the crisis + consent logic (highest-stakes code). `L`
227. **[test]** Add visual-regression snapshots for the paywall and crisis screens. `M`
228. **[test]** Add load tests for `/auth/signup` and coaching-turn SSE. `M`
229. **[test]** Add a coverage report comment to PRs and keep the 95%/96% gates green. `S`

## S. Observability, performance, reliability, docs — 230+

230. Add structured, content-free request logging (never journal/mood/session text) across services. `M`
231. Add health/readiness endpoints for every service with dependency checks. `S`
232. Add a circuit breaker + retry with jitter around the LLM provider (extend existing resilience). `M`
233. Add p95 latency metrics for coaching turns and a budget alarm. `M`
234. Add graceful shutdown/draining so in-flight SSE turns complete. `S`
235. Add idempotency keys to signup/checkout to survive client retries. `M`
236. Add DB indices for the new `subscriptions.org_id` and hot query paths; verify with EXPLAIN. `S`
237. Add response caching headers for static content/media catalog. `S`
238. Add an Alembic baseline + first migration now that the schema is no longer greenfield (billing added a table). `M`
239. **[docs]** Update `docs/ARCHITECTURE.md` cross-stack table with the new billing contract + the `plan` JWT claim (when added). `S`
240. **[docs]** Add a dated `docs/TODO.md` entry recording the B2C program and its phases. `S`

---

## Not in the 200 — these genuinely need you (no autonomous path)

- **Real payment processing** — a Stripe and/or Google Play/Apple merchant account + live keys. (Code adapters are items 49–51, inert until you provide keys.)
- **Consumer Terms/Privacy sign-off** — counsel review of `docs/legal/CONSUMER_TERMS_DRAFT.md` before charging.
- **App-store accounts + submission** — Google Play (verified org for Health apps) and Apple Developer enrolment.
- **Pricing/packaging final call** — the freemium split is a sensible default (item-driven), but the business owns it.
- **Real email delivery** — an SMTP/provider for OTP, password reset, invitations, receipts.
- **Business/legal entity + governing-law decisions** — for the consumer contract and per-market compliance.
