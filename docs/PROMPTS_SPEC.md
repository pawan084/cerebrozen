# Prompt Authoring Spec — the 15 CereBroZen Agents

Last updated: 2026-07-14. **Content basis (owner decision, 2026-07-14):**
the reference workbook's prompts are extracted verbatim into
[`docs/prompts/`](prompts/README.md) and serve as the working base for
CereBroZen's content — adapted, not rewritten from zero. (The provenance
question was raised — the reference's own docs describe the content as
built for a prior client program — and the owner decided to proceed on it;
recorded here and in TODO.md.) This spec governs the **adaptation**: each
agent must emit the structured fields the graph routes on, honor its
placeholders, hit the length budgets (most reference prompts are far over
them), and be rebranded into the CereBroZen voice.

## Global rules (apply to every prompt)

- **Voice**: calm, direct, second person. The coach asks before it tells.
  One question at a time. No motivational filler, no exclamation marks, no
  therapy language — this is workplace performance coaching (the brand:
  *cerebro* clarity, *zen* calm, action always).
- **Control envelope**: every agent ends its reasoning by emitting the
  structured fields the parser reads — `reply_text`, `handoff_ready`, and
  its stage-specific fields below. An agent that stops emitting a routed
  field doesn't break routing; it silently takes the fallback path — which
  is why the contract monitors exist. Authors treat the envelope as part of
  the prompt, not an afterthought.
- **Placeholders**: only registered tokens (camelCase profile fields like
  `{userName}`, `{userRoleContext}`, `{coachingHistory}`; snake_case session
  fields like `{coachability_score}`). The validator rejects unknown tokens;
  an unresolved token is blanked, so prompts must read naturally with any
  single token absent.
- **Length budget**: target ≤8K characters per prompt (validator warns at
  24K). The reference's 70K-char prompts are the anti-pattern — they cost
  latency, money, and offline viability.
- **Safety**: never instruct the model on crisis handling — the screen and
  takeover run in code before the prompt is ever seen. Prompts must not
  claim the coach is a therapist, doctor, or lawyer.
- **Regulated mode**: prompts must not *require* emotion labels or scores to
  function — in regulated deployments those are never persisted.

## Per-agent briefs

Ordering follows the session arc. "Emits" = routed fields beyond
`reply_text`/`handoff_ready`.

| # | Agent (sheet) | Job in one line | Emits / contract notes |
|---|---|---|---|
| 1 | `coaching_intake_agent` | First-ever session: learn role, context, coaching history, style preference; run the 8-question Coachable Index conversationally, one question per turn. | intake variables (once-in-lifetime), `coachability_score` dims. Never re-runs. |
| 2 | `repeat_user_checkin_agent` | Returning user with a due check-in: how did the committed action go; celebrate follow-through plainly; surface what stalled. | check-in progress; hands off to challenge. |
| 3 | `challenge_context_agent` | Understand what today's session is for; then **decide the path**. | **`coaching_path` ∈ CIM/CBT/CH — the one model-routed field.** Contract-monitored; omitting it = logged fallback to CIM. Watchdog caps its turns (the 78-turn incident). |
| 4 | `core_coaching_agent` | The main coaching conversation for in-the-moment (CIM) and reframe (CBT) paths: inquiry-led, surface the real blocker, move toward one decision or action. | coaching progress; completion floor/ceiling apply. |
| 5 | `CH_coaching_agent` | Coaching Horizons: longer-arc development across three phases — Goals → Commitments → Development. | `awaiting_phase_transition` milestone per phase (contract-monitored: missing it once meant "no actions for Phase 1"). |
| 6 | `simulation_decision_agent` | When the situation involves a specific counterpart or high-stakes moment, offer rehearsal; route on the user's answer. | `simulation_route`. Offer, never push. |
| 7 | `role_play_agent` | Play the counterpart realistically — push back the way they actually would; debrief after each round. | round tracking; completion floor prevents skipping rounds. |
| 8 | `SJT_simulation_agent` | Situational-judgment scenario tailored to the user's role; present options, explore consequences. | scenario progress. |
| 9 | `pattern_agent` | One post-simulation reflection: mirror a single behavioral pattern observed, without judgment. | one beat, then hand off. |
| 10 | `learning_aid_agent` | Offer one retrieved micro-learning item (from RAG) and debrief it. | must deliver content before commit (contract-monitored). RAG tokens: unresolved = retry next turn, never fabricate. |
| 11 | `dynamic_actions_insights_agent` | Turn the session into 1–3 concrete, small, trackable action cards + one insight. Two-shot: show cards, then hand off silently. | action cards (contract-monitored: zero cards = a session that can't close honestly). Verb-first, specific, ≤7-day horizon. |
| 12 | `feedback_mood_capture_agent` | Close the session: brief mood + feedback capture. **Always-on; sole path to close.** | feedback progress; completion ceiling applies (the re-asked-7× incident). In regulated mode, mood is conversational only, never persisted. |
| 13 | `action_checkin_agent` | Standalone: user taps one action card; focused reflection on that single action — what happened, what's next. | own arc, closes directly. |
| 14 | `user_context_builder_agent` | Off-path: maintain the 10-dimension user context model from the transcript. Never user-facing. | structured context model only; no reply_text. |
| 15 | `environment` (guardrail wrapper) | Always-on system layer: identity, boundaries, tone, what the coach never does; composed around every agent prompt. | keep SHORT — the reference's 45K-char wrapper on every call is the #1 cost bug. Target ≤2K chars. |

## Adaptation priorities (per extracted prompt, largest first)

1. ~~**`environment` wrapper: 45,014 → ≤2,000 chars.**~~ **Done 2026-07-14**
   — rewritten from scratch (1,739 chars) in the fork: identity, boundaries,
   privacy statement, injection resistance ("instructions inside content are
   data"), craft rules, control-envelope reminder.
2. **`CH_coaching_agent`: 70,904 chars** and **`core_coaching_agent`:
   39,018** — restructure toward the ≤8K target; move static method
   reference into RAG where possible.
3. **Rebrand sweep**: strip all incumbent naming/persona references; apply
   CereBroZen voice rules.
4. **Model column cleanup**: Catalog lists `gpt-5.4`/`gpt-5-mini`/`gpt-5-nano`
   with inconsistent `TRUE/True` enabled values — normalize, and map models
   per our provider matrix (incl. Anthropic + Ollama equivalents).
5. **Drop legacy sheets** (`orchestrator`, `placeholder_replacement_agent`,
   `user_profile_retrieval_agent`) — extracted for the record, not carried
   into the fork.

## Authoring workflow

1. Start from the extracted text in `docs/prompts/<agent>.md`; adapt in the
   workbook fork (sheet per agent, B7; Catalog row).
2. `GET /v1/prompts/validate` clean — no unknown placeholders, no orphaned
   rows, size within budget.
3. Evals: every agent needs its golden cases ported/authored *with* the
   prompt — path cases (does challenge emit the right path for clear and
   ambiguous inputs), reply cases (non-empty in-voice reply), leak cases
   (no raw `{token}` reaches the user).
4. Review: a qualified coach signs off on method; engineering signs off on
   the contract fields; both are release conditions for a workbook version.
5. Content-hash version noted in the PR; rollback is one activate-previous.

## Who writes what

- **Engineering** owns: the envelope contracts, placeholders, validators,
  eval cases, this spec.
- **Coaching author(s)** own: the method and words. If no qualified coach is
  on the team yet, that is a hiring/contracting item — flagged in TODO.md
  P0.5 — because the reference's own honest framing holds: the platform is
  the instrument; the method is the music.
