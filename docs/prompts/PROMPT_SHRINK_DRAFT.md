# Prompt-shrink draft — CH_coaching_agent & core_coaching_agent

> **Status: DRAFT for coach review. Not applied to the live workbook.**
> Produced 2026-07-16. The target is ≤8K chars/agent (`docs/PROMPTS_SPEC.md`);
> coaching-quality sign-off is a release condition, so this is a starting shape,
> not a finished prompt.

## The finding

| Agent | Now | Target | Gap |
|---|---|---|---|
| `CH_coaching_agent` | ~70,934 ch (~17.7K tok) | ≤8,000 | ~9× |
| `core_coaching_agent` | ~39,023 ch (~9.8K tok) | ≤8,000 | ~5× |

These prompts are **not padded**. The size is real content:

- `core` inlines **six full module playbooks** (M1 Performance anxiety … M6
  Decision paralysis), each a mini-protocol, plus 6 stages and RAG triggers.
- `CH` inlines a **24-step Phase 1 + 15-step Phase 2 + 22-step Phase 3** script,
  each step spelled out turn-by-turn, plus per-milestone output contracts.

You cannot reach ≤8K by trimming words. A word-level squeeze would drop the
protocol detail that *is* the coaching — the thing a coach would refuse to ship.

## The shrink strategy: externalize, don't compress

The reduction has to come from **moving content out of the prompt**, not
shortening it. Two mechanisms already exist in the engine:

1. **Retrieved playbooks (RAG).** The module playbooks (M1–M6) and the CH
   step-scripts are *reference procedure* — exactly what SSKB retrieval is for.
   Author them as SSKB documents (a new `sskb_playbook/` type, or `sskb_concept`
   items), and have the prompt pull the *one* relevant playbook via a placeholder
   (`{SSKB_ModulePlaybook}`) after module/phase selection, instead of carrying all
   six/all-three inline every turn. This is the single biggest win: `core` drops
   from six playbooks to a selection rule + one retrieved playbook.
2. **Structured module/step tables in code.** The routing rules, the milestone
   output contracts, and the completion gates are *deterministic* — they belong
   next to the graph (like `STAGE_NODE`), surfaced to the prompt as a compact
   table, not prose repeated per module.

What must **stay inline** (the irreducible core, well under 8K):
- Identity + what-you-are / what-you-do-not.
- The output contract (response_to_user, handoff_ready, routed fields, milestones) —
  the graph routes on these; they are the contract, keep them exact.
- Stage/phase **selection** logic (which module, which phase) and the completion
  floor/ceiling behaviour.
- The RAG trigger placeholders and how to weave retrieved content in.

## Illustrative slimmed skeleton — `core_coaching_agent` (target ≤8K)

> Sketch of the target *shape*, not final copy. The M1–M6 detail is replaced by a
> selection rule + one retrieved playbook. A coach fills the retrieved playbooks
> and confirms the behaviour matches the current inline version.

```
# core_coaching_agent

## What you are
CereBroZen's primary coaching slot (CIM + CBT, unified). You run one structured
coaching conversation using CBT invisibly — surface the thinking pattern under the
challenge, test it against evidence, build one concrete behavioural shift. The user
never hears a framework named.

You do NOT: build/update user context · run simulations · retrieve memory
independently · name CBT concepts academically · end the session (feedback does).

## Output contract  (emit every turn)
response_to_user: the single user-facing message. (NOT `reply_text` — the parser
reads `_USER_TEXT_KEYS`, and `reply_text` parses to an empty reply. This draft
said `reply_text` until 2026-07-17.)
handoff_ready: true only when this slot is complete (completion rule below).
progress: {module, stage, concept_delivered, framework_applied, values_applied}.
[Exact field spec unchanged from the current prompt — do not paraphrase.]

## Stage 0 — behavioural intake check
[unchanged, ~1 short paragraph]

## Stage 1 — module selection
Pick ONE module from the table; you do not see the others' content until picked.
| Module | Use when the challenge is… |
| M1 | performance anxiety at high-stakes moments |
| M2 | imposter syndrome / persistent self-doubt |
| M3 | difficult-conversation avoidance / unresolved conflict |
| M4 | emotional reactivity under pressure |
| M5 | chronic overwhelm / burnout / disengagement |
| M6 | decision paralysis / over-analysis |
On selection, retrieve that module's playbook: {SSKB_ModulePlaybook}
Execute it per the playbook's own steps. (Module protocols now live in SSKB, one
document per module — authored from the current inline M1–M6 text, verbatim.)

## Stage 2–3 — evidence-based concept  (SSKB Extract1)
Query {SSKB_Concept}; deliver one concept, evidence-informed, never as a lecture.

## Stage 4 — client framework & values  (CSKB)
If {CSKB_Framework} present → apply it (its own steps). If {CSKB_Values} present →
apply them. Trigger logic + phase rules: [retain the current compact rules].

## Stage 5 — metaphor · completion · summary fields
[retain current completion rule (strict) + summary-field writes — these gate the
turn and must stay exact.]
```

Rough budget: identity+contract ~2K, stage/selection logic ~2.5K, RAG/trigger
rules ~2K → **~6.5K inline**, with the M1–M6 protocols (the bulk of today's 39K)
moved to six retrieved SSKB playbook docs.

## `CH_coaching_agent` — same move, larger scope

CH is three phases of numbered steps. Externalize each phase's step-script to a
retrieved **phase playbook** (`{CSKB_PhasePlaybook}` or an SSKB equivalent),
keyed by `active_phase`. Keep inline: role, session-state variables, use-case
detection, the cross-phase rules, and the four milestone output contracts (the
graph routes on `awaiting_phase_transition` — keep those exact). That alone moves
~50K of step detail out.

## How to verify a filled-in draft (before coach sign-off)

1. **Structure/loadability** — put the slimmed prompt in the workbook; the new
   `tests/test_workbook_loadable.py` gate confirms it loads clean and the oversize
   warning clears (`issue_count` drops).
2. **Routing contract** — `scripts/eval.py` (real model, nightly) confirms the
   slimmed prompt still emits the routed fields and picks the right path.
3. **Coaching quality** — human coach review against transcripts. Not automatable;
   this is the release gate the size number can't stand in for.

## Open decisions for the coach / product

- Where do playbooks live — SSKB (retrieved, hot-swappable) vs a code-side
  structured reference (deterministic, versioned with the graph)? SSKB is
  recommended (already the mechanism, already per-tenant-overridable).
- Is per-turn playbook retrieval acceptable latency/cost, or should the selected
  playbook be fetched once and carried in state for the module's duration?
```
