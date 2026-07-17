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
  structured fields the parser reads — the user-facing text, `handoff_ready`,
  and its stage-specific fields below. An agent that stops emitting a routed
  field doesn't break routing; it silently takes the fallback path — which
  is why the contract monitors exist. Authors treat the envelope as part of
  the prompt, not an afterthought.
- **The user-facing key is `response_to_user`, NOT `reply_text`.** This spec
  said `reply_text` until 2026-07-17 and it was wrong: `parse_control` reads
  `_USER_TEXT_KEYS = (response_to_user, next_question, clarifying_question,
  message, question, response)`. A prompt that emits `reply_text` parses to an
  **empty reply** — a dead turn, no error. The trap is that `parse_control`'s
  docstring names its *return tuple* `(reply_text, handoff_ready,
  coaching_path)`, so the key looks like `reply_text` if you read the signature
  rather than `_USER_TEXT_KEYS`. No shipping prompt ever used it; every one
  emits `response_to_user` or `next_question`. `tests/test_contracts.py` now
  pins this spec's key against the parser so the doc cannot drift from the code
  again. Accept any key in the tuple; prefer `response_to_user`.
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
`response_to_user`/`handoff_ready`.

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
| 14 | `user_context_builder_agent` | Off-path: maintain the 10-dimension user context model from the transcript. Never user-facing. | structured context model only; no user-facing text. |
| 15 | `environment` (guardrail wrapper) | Always-on system layer: identity, boundaries, tone, what the coach never does; composed around every agent prompt. | keep SHORT — the reference's 45K-char wrapper on every call is the #1 cost bug. Target ≤2K chars. |

## The budget, measured (2026-07-17) — read this before shrinking anything

The ≤8K target is justified above as "latency, money, and offline viability". Measured, on
the real API, those three are not equal — and the biggest lever is not the prompt:

| claim | measured | verdict |
|---|---|---|
| **money** | a full 15-agent session ≈ 80K input tok ≈ **$0.02**; a 27-turn CH session ≈ 457K ≈ **$0.11** | weak. Not what the rewrite is for. |
| **latency** | dominated by the **model**, not the prompt: same prompts, `gpt-5.4` = 2.4–4.3s/turn, `gpt-5-mini` = 9.8–29.7s. Production forced mini on every agent. | ~~fix the model config first~~ **DONE 2026-07-17** — the override default is gone (priority 4). Also 1.7× cheaper, not just faster. |
| **offline viability** | CH's composed prompt is 16.9K tok. gemma4's context is **131,072** — it fits 8× over — and prefills in **2.1s at 7,756 tok/s**. The offline eval scores **100% (16/16)** on the full-size prompts, beating what production shipped this morning. | **dead too.** "A local model cannot hold it" was an assumption; measured, it holds it easily. |

Two things that look like wins and are not, so nobody spends a week on them:

* **Prompt caching is already working.** OpenAI caches the prefix: measured, a repeat turn
  for the same user is **100% cached** (15,104/15,168). Sessions are cheap after turn 1;
  only the first turn pays.
* **Reordering placeholders to extend the cached prefix does not pay.** `{userName}` sits at
  token 174 of CH's 16.5K, so nothing after it is shared BETWEEN users. Moving the whole
  `# Input Parameters` block to the end (instructions byte-identical) took the shared prefix
  from 174 → 1,792 tokens — **12%, not the ~97% the idea promises** — because the remaining
  placeholders are woven through the step scripts, not gathered in one block. Tried,
  measured, rejected.

**So the ≤8K budget is justified by nothing measurable, and priority 2 should not be built.**

What the cut would buy: ~1.1s of prefill offline (8.9K fewer tokens at 7,756 tok/s) and
~$0.001/turn. What it would cost: restructuring ~300K characters of authored coaching
method into RAG, a counsel answer on provenance (the text is 99.8% the reference's), and a
qualified coach's sign-off — which is a release condition this project does not yet have.
That is a bad trade in every direction, and it was invisible until someone measured instead
of reasoning from "70K chars is obviously too big".

The budget is not therefore meaningless — a NEW prompt written at 70K would still be
careless, and `environment` at 45K on every call genuinely was the #1 cost bug (fixed, 1.7K).
Keep ≤8K as guidance for new authoring. Do not spend a quarter retrofitting it onto content
that measures fine.

**If the cut is ever revived, revive it with a reason that survives measurement.** Candidates
nobody has tested yet: coaching QUALITY on a small model (the eval tests the contract, never
the coaching — a 70K prompt may well dilute an 8B model's attention in ways 16 routing cases
cannot see), or genuinely constrained air-gapped hardware (this was measured on a remote GPU
box; the context math holds anywhere, the prefill speed does not).

## Adaptation priorities (per extracted prompt, largest first)

1. ~~**`environment` wrapper: 45,014 → ≤2,000 chars.**~~ **Done 2026-07-14**
   — rewritten from scratch (1,739 chars) in the fork: identity, boundaries,
   privacy statement, injection resistance ("instructions inside content are
   data"), craft rules, control-envelope reminder.
2. ~~**`CH_coaching_agent`: 70,904 chars** and **`core_coaching_agent`: 39,018**~~
   **DO NOT BUILD — measured 2026-07-17, the justification does not exist.**
   All three reasons for the ≤8K target (latency, money, offline viability) were
   measured and none holds; see §"The budget, measured" above. Offline was the
   last one standing and it fell hardest: gemma4's context is 131,072 tokens, so
   CH's 16.9K prompt fits eight times over, prefills in 2.1s, and scores 100%
   (16/16) on the offline eval with the FULL-size prompts. The cut buys ~1.1s of
   prefill; it costs 300K characters of restructured coaching method, a counsel
   answer on provenance, and a coach sign-off. Do not spend a quarter on it.
   `docs/prompts/PROMPT_SHRINK_DRAFT.md` remains as the plan-of-record IF a
   measured reason ever appears — its "externalize, don't compress" strategy is
   right; there is simply nothing to buy with it today.
3. **Rebrand sweep**: strip all incumbent naming/persona references; apply
   CereBroZen voice rules.
4. ~~**Model column cleanup**~~ **DONE 2026-07-17** — and the described defect
   was not there. Our fork's Catalog is already consistent: 15 rows, all
   `enabled=TRUE`, two models (`gpt-5.4` ×10, `gpt-5-mini` ×5). No `gpt-5-nano`,
   no `True`/`TRUE` mix — that was the reference's workbook, not ours.
   The real defect was one level down: `docker-compose.prod.yml` DISCARDED the
   Catalog, forcing `gpt-5-mini` on every agent, because `gpt-5.4` was believed
   to be a placeholder id. It is a real model (API: HTTP 200). Measured, forcing
   mini cost 3.6× the latency AND 1.7× the money per cached turn — it spends
   ~1,408 reasoning tokens/turn at output rates, which its cheaper input cannot
   pay for once the prompt caches at 90% off. The default is gone; the env hatch
   remains for accounts that cannot reach a Catalog model, and for the offline
   ollama profile. Pinned by `test_production_does_not_pin_one_model_over_the_catalog`.
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
