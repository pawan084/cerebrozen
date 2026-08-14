# CereBro — Claims Map

> Every user-facing claim that could be doubted, and the mechanism that makes it
> true. If a claim has no mechanism, it does not ship.
>
> The automated half is `scripts/check-claims.mjs`, which fails the build on a
> banned phrase across **all four clients** — web, app, iOS Swift and Android
> `strings.xml`. Copy honesty was previously enforced by review alone, and this
> session found three live over-claims plus a fabricated screen, which is why
> the gate exists again. Companions: [PRD.md](PRD.md) (honest per-feature
> status), [ARCHITECTURE.md](ARCHITECTURE.md).

## How to use this file

- **Adding a claim?** Add a row with the mechanism *and* the test that pins it.
  A row without a test is an intention, not a claim.
- **Gate flagged you?** Fix the copy, or — if the capability genuinely now
  exists — add the row here and remove the phrase from the script. Do not add a
  silent allowlist; that turns the gate into decoration.
- **Removing a feature?** Delete its row and re-ban the phrase.

## 1. Privacy claims

| Claim (as shown) | Mechanism | Test |
| --- | --- | --- |
| "No ads, ever · no third-party trackers" | Zero ad/analytics SDKs in any client; first-party `/events` with an allow-list | `tests/test_events.py` |
| "Usage counts are anonymous and optional" | Random install id, never account-linked; opt-out toggle | `tests/test_events.py` |
| "Support tooling shows counts and account state, not your words. One exception: if an entry is flagged as a crisis, a reviewer can open that entry — and every time one is opened, that is recorded" | Admin views project counts only; `admin.read_safety_excerpt` is the single path to verbatim text, is per-row and deliberate, and writes an `admin_audit` row naming the admin. Android said "never the words" until 2026-08-12 — an absolute this endpoint disproves. The path is defensible; denying it was not | `tests/test_admin_metrics.py` (incl. `context_memories` coverage), `tests/test_admin_audit_log.py` |
| "Export or delete everything from inside the app" | `GET /users/me/export` enumerates every user-scoped table; `DELETE /users/me` cascades | `tests/test_account.py`, `tests/test_memory.py`, `tests/test_safety_plan.py` |
| "Turn memory off and it forgets" | `ai_memory` gates reads/writes of `context_memories`; deletion is never gated | `tests/test_memory.py::test_consent_off_blocks_reads_and_writes_but_never_deletion` |
| "This note stays with the check-in… the companion is given your feeling — never these words" (`/checkin`) | `agentic._recent_signals` selects `MoodLog.mood`, not the row — so the note column never reaches the plan generator, while the feeling itself does (consent-gated on `mood_history`) | `tests/test_dpdp.py::test_the_companion_is_given_the_feeling_never_the_note` |
| "Here is exactly which of them are switched on for you right now" (`/checkin`) | The read list is rendered from a live `GET /users/me/consent`, per category, rather than stated flatly; a failed read says so instead of guessing. The design mock's flat "Does not use your journal" was true only while `journal_memory` was off | `tests/test_dpdp.py::test_plan_signals_respect_itemized_consent`, `::test_a_fresh_account_has_granted_nothing` |
| "Delete all memory" | Wipe clears chat, insights, saved notes and the Oracle checkpoint | `tests/test_memory.py::test_wipe_all_removes_memories_too` |

## 2. Safety claims

| Claim (as shown) | Mechanism | Test |
| --- | --- | --- |
| "Crisis-aware · region-correct lines always a tap away" | `services/crisis.py` per-region resources appended to every flagged reply | `tests/test_crisis.py` |
| "Tele-MANAS 14416 first in India" | Region ordering in `crisis.reply_suffix("IN")` | `tests/test_crisis.py` |
| "Safety never blocks" | Crisis scanning adds resources; it never rejects a message, and a safety plan never gates a reply | `tests/test_safety_plan.py::test_crisis_reply_is_unchanged_with_and_without_a_plan` |
| "Your safety plan is yours — we don't score or share it" | No read path outside the owner; no model write path exists at all | `tests/test_safety_plan.py::test_one_account_cannot_see_anothers_plan` |
| "Not a therapist, diagnosis, or crisis service" | Disclosure copy on every AI surface; no diagnostic or scoring code exists | `DisclosureCopyTest` (Android), PRD §2 |
| **"Verified"** (crisis screen badge) | Only rendered where the numbers were actually checked against a named source. `VERIFIED_CRISIS_REGIONS` is `{IN}` — MoHFW Tele-MANAS and the ERSS 112 listing, the same two sources `/safety` cites. Every other region reads "Not verified yet" and says why. The claim appears three times on that screen (badge, strapline, and the detail line under the number someone would dial); all three branch on the same predicate, so they cannot disagree | `CrisisVerifiedBadgeTest` (Android) |
| "We haven't checked these against an official source" (non-IN regions) | The honest branch of the above — shown instead of the badge, not in addition to it | `CrisisVerifiedBadgeTest::an unknown region is not verified` |
| "Someone you selected; reached automatically **only if you switched that on**" | `escalation.on_crisis` notifies the trusted contact on a crisis-level `SafetyEvent`, hard-gated on `TrustedContact.notify_consent` (default `False`). Until 2026-08-12 this line read "CereBro never contacts them automatically" — an absolute the backend disproves the moment consent is on, and one the trusted-contact screen already contradicted two taps away | `SafetyCopyTest::the trusted-contact line does not deny an automatic contact that happens`, `tests/test_escalation.py` |
| TIPP's self-harm note — a risk signal that carries its own pathway | The note *is* the crisis door (`onUrgent` → `crisis`), not a description of where one lives. It previously ended "Urgent support lives in the You tab" on a screen whose only controls were Back/Previous/Next | `SafetyCopyTest::the TIPP self-harm note does not send the user hunting for a tab`; pathway walked on hardware 2026-08-12 |

## 3. Capability claims

| Claim (as shown) | Mechanism | Test |
| --- | --- | --- |
| "Unlimited daily conversations · free includes 50 messages a day" | `services/usage.py` — real DB count, 429 past the cap, premium tiers exempt | `tests/test_usage_limit.py` |
| "Patterns only appear once real check-ins support them — no guesses" | `compute_patterns` thresholds every rule and returns `[]` below them | `tests/test_patterns.py` |
| "Edit or delete any of it" | `context_memories` is addressable: PATCH/DELETE per row | `tests/test_memory.py` |
| "Works in your browser" | `apps/app` is a real authenticated client with public signup | `e2e/tests/app.spec.ts` |
| "Private by design — no ads, nothing sold, and nothing remembered unless you allow it" | Three separate mechanisms: zero ad/third-party SDKs in any client; no data sale (first-party `/events` only); and all six consent categories default **off**, with reads/writes gated on them | `tests/test_events.py`, `tests/test_consent_enforced.py`, `tests/test_dpdp.py::test_a_fresh_account_has_granted_nothing` |
| "Safety scanning … never blocks your writing, and nobody at CereBro reads it" | `services/safety.py::scan_and_record` only ever RAISES a risk level and attaches resources — no code path rejects or edits an entry; admin surfaces project counts, and an excerpt read is a separate, logged, per-row GET | `tests/test_safety_reach.py`, `tests/test_admin_metrics.py` |
| "My safety plan — yours, in your words · works offline" | `Session.api` caches every GET and serves the last copy when a read fails; the screen shows the cached plan with an honest "saved on this device" banner, and says so plainly when there is no cached copy rather than showing empty boxes | `SafetyPlanTest`, `tests/test_safety_plan.py`; the three network states verified on hardware 2026-07-31 |
| "First-party — computed on your own data, never sold or shared" (Weekly insights) | `services/insights.py` imports no AI module at all — the weekly read is pure SQL over the user's own rows, and each category is gated on its own consent flag, so a switched-off category reads as "no data" rather than being computed anyway | `tests/test_insights_no_guesses.py`, `tests/test_insights_no_guesses.py` |
| "Two-minute reset" / "Try a 2-minute reset" / "Fast anxiety-stress reset — 2 minutes" | `twoMinutesReached` in `ui/screens/Breathe.kt` derives elapsed seconds from completed cycles at the user's chosen pace and marks the two-minute point once it has genuinely passed. The exercise stays open-ended — it is a mark, not a timer or a stop | `BreathePacingTest` (mark timing, slower paces, Reset-only); verified in real time on hardware 2026-07-31 |

## 4. Claims the public pages make (2026-08-12)

Three pages from `ref/landing.html` shipped this session. Each one states
something the product is held to **and** what is not true yet — and the second
half is the half that rots quietly, so the e2e walks both.

| Claim (as shown) | Mechanism | Test |
| --- | --- | --- |
| `/safety` — "Human help comes first" with 14416 and 112 on the page | Not a link to a link: both numbers render in the page body, so the pathway survives a failed client-side nav | `trust-pages.spec.ts::safety centre leads with human help and names the limits` |
| `/safety` — "What is not in place yet" | A standing section, not a footnote. Present tense on purpose: it lists the gaps rather than implying a roadmap | same test (the heading is asserted) |
| `/accessibility` — "we do not claim conformance" | **A claim about the absence of a claim.** No WCAG level is asserted anywhere on the site; `check-contrast.mjs` gates measured text contrast, which is one criterion, not conformance | `trust-pages.spec.ts::accessibility page claims no conformance it has not earned` |
| `/organizations` — "not yet available" | There is no org checkout, no seat billing and no B2B plan in `check-prices.mjs`'s price set. The page describes the model without offering it | `trust-pages.spec.ts::organizations page states the boundary and that it is not yet buyable` |
| `/organizations` — the list of what a sponsor "must never receive" | The same boundary the admin API enforces: `apps/portal` and the admin metrics endpoints project counts only; content is a separate, logged, per-row GET that no org-scoped view calls | `tests/test_admin_metrics.py`, `trust-pages.spec.ts` |
| Portal `/members/invite` — a refused file "never left your computer" | The header allowlist runs in the browser on the first line of the file, before any request is made; the upload only happens after it passes. The server repeats the check, so the browser's copy is a privacy measure rather than the guarantee | `portal-live.spec.ts::an eligibility file with a health column is refused without being uploaded` (it watches the network, not the wording) |
| Portal `/members/invite` — "any other column is refused and the file is not imported" | An allowlist over the header, not a denylist of words, and the whole file is rejected rather than the column dropped — a silently ignored column teaches the administrator that sending it was fine | `tests/test_eligibility_import.py::test_a_file_carrying_health_data_imports_nothing_at_all`, `tests/test_eligibility_import.py::test_the_allowlist_stops_columns_no_denylist_would_have` |
| Portal `/members/invite` — "CereBro does not report addresses back to you here" | The import report carries line number, `external_ref` and outcome only. The audit row for an import carries counts | `tests/test_eligibility_import.py::test_the_report_never_contains_an_email_address`, `tests/test_eligibility_import.py::test_the_trail_records_one_action_not_five_hundred` |
| `/account` (sponsored member) — "nothing to pay or cancel here" | Not a hidden button: the branch renders no upgrade CTA and no billing-portal link, because a sponsored member has no Stripe customer for one to open. `sponsored` comes from `services/entitlements.resolve`, which sets it only when an organisation — not a purchase — is paying | `tests/test_entitlements.py::test_me_reports_the_tier_the_server_will_enforce`, `tests/test_entitlements.py::test_a_purchase_is_never_reported_as_sponsored` |
| `/account` (sponsored member) — "They can see that a seat is used; they never see what you write, log or say" | The organisation boundary, restated at the one place the member is told an employer is involved. Sponsorship reads `org_memberships` and nothing else; the org model, service and routes import no wellbeing model at all, so there is no read path to what they write | `tests/test_org.py` (the structural import test), `tests/test_org.py::test_ending_sponsorship_keeps_the_account` |
| `/account` (sponsored member) — "If the sponsorship ends, your account stays — it returns to the free tier" | Entitlement is computed per request and never written to `users.subscription_tier`, so an ended membership stops granting the moment it ends, and the account itself is untouched by the change | `tests/test_entitlements.py::test_the_stored_tier_is_never_written_by_sponsorship`, `tests/test_entitlements.py::test_the_cap_returns_when_the_sponsorship_ends` |
| iOS `PremiumView` / Android `PremiumScreen` (sponsored member) — "There is nothing to pay or cancel here" | The same absence the web account screen makes, on the two surfaces that used to contradict it: iOS renders `sponsoredState`, which has no product cards, no purchase CTA and no Apple manage-subscriptions link; Android renders no price list. Both branch on the server's `sponsored` flag, never on StoreKit or a local guess | `tests/test_entitlements.py::test_me_reports_the_tier_the_server_will_enforce`, `tests/test_entitlements.py::test_a_purchase_is_never_reported_as_sponsored` |
| iOS / Android (sponsored member) — "group totals only, and only for groups large enough that no one person can be picked out of them" | `reporting_threshold` is clamped to `MIN_REPORTING_THRESHOLD` on write, so an administrator cannot lower it to a number that would identify someone; it is a floor, not a default | `tests/test_org.py::test_threshold_cannot_be_set_below_the_floor`, `tests/test_org.py::test_patching_the_threshold_clamps_rather_than_rejecting`, `tests/test_org.py::test_a_group_over_the_threshold_reports_numbers` |
| Android `Session.rememberEntitlement` — the remembered tier is never an unlock | It decides what a screen *says*, not what an account may use: `usage.enforce_quota` and `media.is_entitled` resolve server-side through `services/entitlements`, and the cache is dropped at sign-out so it cannot outlive the account that earned it | `SessionStoreTest::the_entitlement_is_remembered_and_does_not_outlive_the_account`, `tests/test_entitlements.py::test_sponsorship_lifts_the_free_chat_cap`, `tests/test_entitlements.py::test_premium_narration_follows_the_resolved_tier` |

## 5. Deliberately banned phrases

Currently blocked by the gate because they were, or would be, false here:

| Phrase | Why |
| --- | --- |
| "unlimited voice" | Voice is not metered at all — the phrase implied a meter and a benefit that did not exist. Shipped in Android `premium_intro` until 2026-07-30. |
| "downloaded soundscape" / "available offline" / "offline playback" / "download for offline" | **No client implements downloads** — no `AVAssetDownloadTask`, no ExoPlayer `DownloadService`, and `apps/app/public/sw.js` is push-only. Shipped in iOS `Dummy.offline` and the web library footnote until 2026-07-30. |
| "clinically proven", "fda approved", "cures …", "treats depression" … | This is not a clinical product. False by construction, not merely unproven. |
| "guaranteed to", "100% effective", "risk-free" | No wellness product can promise an outcome. |
| "nothing is ever shared" / "never shared with anyone" | **False.** A Talk message goes to OpenAI or Anthropic (`services/ai.py`); voice goes to Deepgram or ElevenLabs (`services/voice.py`). Shipped as the second sentence on the Welcome screen of Android, iOS *and* the browser client until 2026-07-30 — the first privacy statement a new user ever read, and the strongest one the product cannot keep. The true version is the row above. |

**Note on offline:** the app genuinely *is* local-first in places (curated
fallbacks, on-device journal analysis, `LocalCompanion`, and a safety plan
cached on every client). That is real and can be described — but in those
words, not as "available offline", which reads as downloaded media.
