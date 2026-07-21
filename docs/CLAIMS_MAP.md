# Claims map — every public claim → its mechanism → the test that proves it

CLAUDE.md rule 6: *"Marketing claims must map to mechanisms."* This table is that mapping,
made auditable. Each row is a claim we make (on `apps/web`, in the product, or to a security
reviewer), the code that makes it TRUE, and the test that would fail if it stopped being true.
A claim with no mechanism, or a mechanism with no test, is a bug in this table.

Paths are relative to the repo root. "Gate" = the test is wired into a build-failing check.

**This table is the CI-gated one** — `scripts/check-claims.mjs` fails the build when a claim
on `apps/web` has no row here. [SECURITY.md](SECURITY.md) §"Compliance mapping" holds a
second, broader table of compliance *postures* that is **not** gated. New marketing claim →
it belongs here, or the gate cannot see it.

---

## Safety (safety-as-code)

| Claim | Mechanism | Proof |
|---|---|---|
| "Crisis handling is deterministic — a scripted, zero-token reply, identical everywhere, never the coaching model" | `services/engine/app/graph/crisis.py` (`crisis_screen`, `safe_response`); the model classifier only sharpens *detection*, never the reply (`crisis_classifier.py`) | `services/engine/tests/test_crisis.py`, `test_crisis_classifier.py` |
| "Every detected language gets a reply that routes to help — fallback, never silence" | `crisis.py:safe_response` (per-language, English fallback, `{line}` helpline) | `services/engine/tests/test_crisis_language_coverage.py` |
| "The crisis screen answers in the language it detected — all ~20, none fall back to English" | `crisis.py:_MESSAGES` covers every language in `_LATIN`/`_OTHER` (`hi-latn` served by `hi`) | `test_crisis_language_coverage.py::test_no_detected_language_falls_back_to_english_any_more`, `::test_a_non_latin_reply_is_actually_in_its_own_script` |
| "Crisis replies are native-speaker reviewed in **English only**; the other ~20 are drafted and labelled as such" | `crisis._NATIVE_REVIEWED` (honesty registry, not a gate — a drafted reply still ships, and logs `crisis.reply_language_unreviewed`); clients override with a reviewed set via `CEREBROZEN_CRISIS_MESSAGES_FILE` | `test_crisis_language_coverage.py::test_the_review_registry_cannot_claim_a_language_we_do_not_have`, `::test_serving_an_unreviewed_language_is_logged_not_suppressed` |
| "Obfuscated spellings don't defeat the screen (`su1c1de`, `k1ll_myself`, `$uicide`)" | `crisis._LEET` per-letter classes + separator tolerance, applied to the same term list — new spellings only, never new words | `services/engine/tests/test_crisis.py::test_obfuscated_spellings_do_not_defeat_the_screen` (+ the digit-bearing business sentences in `test_ordinary_messages_are_not_flagged`) |
| "A coach, not a companion — it never claims to be human, licensed, or your friend" | always-on `graph/guardrails.py::NON_COMPANION` prepended to **every** turn's system prompt, in code and outside the editable workbook | `services/engine/tests/test_boundaries.py` (guardrail present on every path, survives an empty workbook), `test_nodes.py::test_an_ordinary_coaching_turn_carries_no_disclosure_block` |
| "Ask what it is and it tells you — every time" | `app/safety/boundaries.py` detects being-treated-as-a-person/relationship/clinician and appends a mandatory disclosure block to that turn, last so nothing outranks it; counted content-free as `cerebrozen_boundary_prompted_total{kind}` | `test_boundaries.py`, `test_nodes.py::test_a_message_that_treats_the_coach_as_a_person_forces_a_disclosure_into_the_turn`, `::test_the_disclosure_is_counted_by_kind_and_never_by_content` |
| "Reminders never use guilt, longing, or streak-loss to pull you back" | `app/notifications.py::_format_payload` — count + link only, on every channel | `test_nudge_channels.py::test_a_nudge_never_uses_longing_guilt_or_loss_to_pull_the_user_back` |
| "The crisis reply itself says it is an AI, in every language" | `crisis._AI_DISCLOSURE` appended to every reply **after** the client-override path, so a deployment can improve the body but cannot edit the disclosure out | `test_crisis_language_coverage.py::test_every_crisis_reply_discloses_that_it_is_an_AI`, `::test_a_client_cannot_override_the_disclosure_away` |
| "Companion drift is measured, not assumed" | `app/safety/companion_scenarios.py` — 17 adversarial scenarios + an output-side drift detector (false-claim / simulated-bond); coverage pinned so a regression fails the build | `services/engine/tests/test_companion_redteam.py` |
| "A long session offers you a break — we don't optimise for time-on-app" | `app/safety/pacing.py` — code-owned pause block after 20 turns, periodic not per-turn | `services/engine/tests/test_pacing.py` |
| "Say repeatedly that you're not coping and the coach points you at real support, without ending the session" | `pacing.block_for` distress route (3 in a session) → EAP/person/doctor; explicitly not a crisis takeover; counted as `cerebrozen_session_pacing_total{kind}` | `test_pacing.py::test_repeated_not_coping_routes_to_real_support`, `::test_the_distress_route_is_not_a_crisis_takeover`, `::test_ordinary_work_stress_is_not_treated_as_distress` |
| "Crisis red-team is a release gate" | `services/engine/app/safety/redteam_scenarios.py` | `services/engine/tests/test_crisis_redteam.py` — **gate** |
| "Non-clinical / no emotion inference (regulated mode, on by default)" | `services/engine/app/governance.py` — refused at the store layer | `services/engine/tests/test_regulated_workplace.py` |
| "18+ only" | `services/platform/app/routers/users.py` (`attest`), `models.User.adult_attested_at` | platform consent/attest tests |
| "Crisis helplines are never hardcoded in clients; a region-neutral offline floor" | `services/engine/app/safety/helplines.py`; client `data/Helplines.kt` | `test_helplines.py`, `HelplinesTest.kt` |

## Privacy — "counts, never content"

| Claim | Mechanism | Proof |
|---|---|---|
| "The database an HR admin's token reaches holds no content — a schema property, not a promise" | `services/platform` has no content column/route; content lives on the engine | `services/platform/tests/test_wellness_account.py::test_no_platform_route_exposes_wellness_content`, `::test_no_platform_table_holds_content_columns` (covers **every** table incl. billing) |
| "Consent is opt-in, per category, and withdrawal bites immediately" | six flags default false (`models.CONSENT_KEYS`); signed into the JWT (`security.issue_access_token`); engine enforces per-write 403 (`engine wellness.py`); a change rotates the token (`users.update_consent`) | `test_wellness_account.py` (consent suite), `test_a_withdrawal_takes_effect_on_the_very_next_request` |
| "You can see every consent change you made, and when" | append-only `models.ConsentEvent`, `GET /users/me/consent/history` | `services/platform/tests/test_consent_history.py` |
| "HR sees only aggregates above a cohort floor" | `services/platform/app/routers/analytics.py` (k-anon floor); admin renders "—" below it | `services/platform/tests/test_analytics.py` |
| "Export and erase are self-serve" | platform `users.py` (export/delete), engine `privacy.py` (memory/account erasure) | `test_offboarding.py`, platform user tests |

## Sovereignty — "run it anywhere"

| Claim | Mechanism | Proof |
|---|---|---|
| "Every service boots and every suite passes with zero external credentials (mock model)" | engine `conftest.py` pins the mock provider + blanks all external URLs; platform runs in-memory SQLite | engine suite (1874 tests, **gate 96% branch**) + platform suite (**gate 95%**), both fully offline |
| "A keyless self-check reports exactly what a deployment reaches for" | `platform main.py:/health/status`, `engine api.py:/health/status` (`sovereign_ready`, provider/redis/mongo/voice flags) | `services/platform/tests/test_health.py`; `services/engine/tests/test_api_layer.py::test_health_status_*` |
| "On-prem / air-gapped is possible by construction" | documented boot + degradation matrix | `docs/SELF_HOSTING.md` |

## Consumer (B2C freemium)

| Claim | Mechanism | Proof |
|---|---|---|
| "Anyone can self-serve a private personal account" | `POST /auth/signup` → personal org-of-one (`models.is_personal_org`) | `services/platform/tests/test_signup.py` |
| "Free-tier limits are enforced, not just shown — the paywall can't be bypassed via the API" | `plan` in the signed JWT (`security.issue_access_token`, `auth._resolve_plan`); engine caps free coaching at the turn endpoint (`ratelimit.limit_free_daily_turns`) | `services/platform/tests/test_billing.py::test_jwt_plan_claim_*`; `services/engine/tests/test_freemium_gating.py` |
| "Safety is never paywalled" | crisis/check-ins/coach core are outside the entitlement gates (`models.entitlements_for` lists only paid depth) | `test_billing.py` (free entitlements), the free-daily-cap tests (coach still answers under the cap) |
| "Cancel keeps access until the period ends" | `models.subscription_grants` period-end grace; webhook `subscription.deleted` ends now | `test_billing.py::test_a_cancelled_subscription_keeps_access_until_period_end`, `test_billing_stripe.py` |
| "Real payments plug in behind a seam; nothing is stored but an opaque reference" | `billing_providers.py` (mock / Stripe / Google Play), `/billing/webhook` (signature-verified), `/billing/play/verify`; `models.Subscription` has no card data | `services/platform/tests/test_billing_stripe.py` (full flow via a faked SDK) |
| "Deleting a personal account leaves no orphan org or billing" | `users.delete_me` retires the solo org + drops its subscription | `services/platform/tests/test_personal_org_cleanup.py` |
| "Price shown in one place" | `billing.PRICES` → `GET /billing/prices`; web `pricing/page.tsx` mirrors it | `test_billing.py::test_prices_endpoint_is_public_and_lists_plus` |

## Auth & abuse-resistance

| Claim | Mechanism | Proof |
|---|---|---|
| "We never reveal whether an email has an account" | identical errors on login; signup 409 is the one deliberate exception; OTP/forgot always 200 | `test_signup.py`, `test_otp.py`, `test_password_reset.py` |
| "Passwordless codes can't be brute-forced" | `OtpCode` single-use, expiring, attempt-capped (`auth.otp_verify`) | `test_otp.py::test_a_code_burns_after_too_many_wrong_guesses` |
| "Password reset revokes every session" | `auth.password_reset` revokes all refresh tokens | `test_password_reset.py` |
| "Auth endpoints are rate-limited" | `app/ratelimit.py` per-IP limiter on signup/login/otp/forgot | `services/platform/tests/test_rate_limit.py` |

---

## Known gaps — claims we must NOT make yet (until the mechanism lands)

- **"Crisis support reviewed in ~20 languages"** — detection and scripted replies now both span every language in the lexicon (backlog #1, closed), but only **English** has been read by a native speaker; the rest are drafted in-house and marked so in `crisis._NATIVE_REVIEWED`. Say "the safety screen runs in ~20 languages and answers in the one it detected"; do **not** say reviewed, certified, or professionally translated until a speaker signs each one off (that is a needs-human item, and it moves languages into the registry).
- **"Sign in with Google"** — the app has the UI; there is no `/auth/google` backend yet (backlog #7). Keep it hidden until built.
- **"Real payments"** — the Stripe adapter is inert without the SDK + `STRIPE_*` keys. Google Play is **half** built, and the halves matter: the **server-side** receipt verification exists (`billing_providers.verify_play_purchase`, `POST /billing/play/verify`, replay-protected by a unique index on `provider_ref`), but the **client** does not — there is no `com.android.billingclient` dependency in `apps/android/app/build.gradle.kts`, so nothing can actually raise a Play purchase (#50). Don't imply live billing on either provider until keys are configured and the Play client ships.
- **"Never trains on your data"** — true as an architectural absence, but it has no positive test (you can't test the non-existence of a training pipeline). Assert it in the security review with the data-flow, not as a passing check.
