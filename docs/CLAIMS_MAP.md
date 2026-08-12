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
| "Unlimited daily conversations · free includes 50 messages a day" | `services/usage.py` — real DB count, 429 past the cap, premium tiers exempt | `tests/test_usage.py` |
| "Patterns only appear once real check-ins support them — no guesses" | `compute_patterns` thresholds every rule and returns `[]` below them | `tests/test_patterns.py` |
| "Edit or delete any of it" | `context_memories` is addressable: PATCH/DELETE per row | `tests/test_memory.py` |
| "Works in your browser" | `apps/app` is a real authenticated client with public signup | `e2e/tests/app.spec.ts` |
| "Private by design — no ads, nothing sold, and nothing remembered unless you allow it" | Three separate mechanisms: zero ad/third-party SDKs in any client; no data sale (first-party `/events` only); and all six consent categories default **off**, with reads/writes gated on them | `tests/test_events.py`, `tests/test_consent.py`, `ConsentDefaultsTest` (Android) |
| "Safety scanning … never blocks your writing, and nobody at CereBro reads it" | `services/safety.py::scan_and_record` only ever RAISES a risk level and attaches resources — no code path rejects or edits an entry; admin surfaces project counts, and an excerpt read is a separate, logged, per-row GET | `tests/test_safety.py`, `tests/test_admin_metrics.py` |
| "My safety plan — yours, in your words · works offline" | `Session.api` caches every GET and serves the last copy when a read fails; the screen shows the cached plan with an honest "saved on this device" banner, and says so plainly when there is no cached copy rather than showing empty boxes | `SafetyPlanTest`, `tests/test_safety_plan.py`; the three network states verified on hardware 2026-07-31 |
| "First-party — computed on your own data, never sold or shared" (Weekly insights) | `services/insights.py` imports no AI module at all — the weekly read is pure SQL over the user's own rows, and each category is gated on its own consent flag, so a switched-off category reads as "no data" rather than being computed anyway | `tests/test_insights.py`, `tests/test_insights_no_guesses.py` |
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
