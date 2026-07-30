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
| "Employer/support sees counts, never content" | Admin views project counts only; excerpts are a separate, logged, per-row GET | `tests/test_admin_metrics.py` (incl. `context_memories` coverage) |
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

## 3. Capability claims

| Claim (as shown) | Mechanism | Test |
| --- | --- | --- |
| "Unlimited daily conversations · free includes 50 messages a day" | `services/usage.py` — real DB count, 429 past the cap, premium tiers exempt | `tests/test_usage.py` |
| "Patterns only appear once real check-ins support them — no guesses" | `compute_patterns` thresholds every rule and returns `[]` below them | `tests/test_patterns.py` |
| "Edit or delete any of it" | `context_memories` is addressable: PATCH/DELETE per row | `tests/test_memory.py` |
| "Works in your browser" | `apps/app` is a real authenticated client with public signup | `e2e/tests/app.spec.ts` |
| "Private by design — no ads, nothing sold, and nothing remembered unless you allow it" | Three separate mechanisms: zero ad/third-party SDKs in any client; no data sale (first-party `/events` only); and all six consent categories default **off**, with reads/writes gated on them | `tests/test_events.py`, `tests/test_consent.py`, `ConsentDefaultsTest` (Android) |
| "Safety scanning … never blocks your writing, and nobody at CereBro reads it" | `services/safety.py::scan_and_record` only ever RAISES a risk level and attaches resources — no code path rejects or edits an entry; admin surfaces project counts, and an excerpt read is a separate, logged, per-row GET | `tests/test_safety.py`, `tests/test_admin_metrics.py` |

## 4. Deliberately banned phrases

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
