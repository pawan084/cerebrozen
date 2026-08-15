# Audit J — sibling agent stack, deep-dive with verdicts (2026-08-15)

Follow-up to `B2C_BACKLOG.md §0.2`: each of the six points read at implementation depth
in `~/workspace/cerebro`, mapped against this repo's actual code, with an explicit
**relevant-or-not** verdict. Ordered by verdict strength, not by their numbering.

---

## 1. Crisis pre-filter folding + lexicon — **RELEVANT, and it exposes two live gaps today**

**Theirs** (`api/app/ai/graph/crisis.py`): `_fold_latin` = NFKD-normalise → strip
combining marks → casefold → strip *five* apostrophe variants (`' ’ ʼ \` ´`); Latin terms
matched word-bounded against the folded text, longest-first; non-Latin terms (zh/ja/ko/
hi-Devanagari/ar/ru) by raw substring against the UNfolded text; `crisis_screen` never
raises — any exception returns "crisis" (fail toward flagging).

**Ours** (`services/safety.py::_keyword_risk`): plain `term in text.lower()`.

**The two gaps this exposes in our floor, verifiable right now:**
1. **The curly apostrophe.** Our lists spell `"can't cope"` with a straight `'` (and
   hand-duplicate `"cant go on"` for exactly one phrase). Phone keyboards — the device
   this product ships on — default to `’` (U+2019). *"I can’t cope"* typed on the OnePlus
   does not match our elevated floor today. Their fold makes every apostrophe variant
   match from one canonical spelling.
2. **Zero non-English coverage, in an India-first product.** The UI ships Hindi; the
   floor knows only English. Their seed list carries Devanagari + romanised Hindi
   (`खुदकुशी`/`khudkushi`, `मरना चाहता`), plus es/pt/zh/ja/ko/ar/ru.

**Implementation here:** extend `_keyword_risk` — fold once, compile the Latin lists
(both tiers) into word-bounded longest-first regexes, add a non-Latin substring tier,
keep the existing crisis/elevated split and the floor-under-LLM semantics untouched.
Tests: curly-apostrophe match, Devanagari match, `"die"`-inside-`"diet"` non-match,
progressive forms (already pinned), fold crash → flags.
**Effort:** small (one function + tests). **Risk:** false positives from short terms —
mitigated by word bounds; the Hindi seed ships flagged for native review, the exact
precedent `values-hi` already set for safety strings.
**Decision needed:** none. This is hardening an existing mechanism.

## 2. Action check-ins on the work plan — **RELEVANT; the next slice of /work**

**Theirs:** `coach_action_checkin` is a first-class stage, seeded at session entry, with
its own progress channel. HeyCere independently has `repeat_user_checkin_agent`
("pre-session loop-closing, gated"). Two mature coaching engines converged on the same
rule: **a session opens by closing the last session's loop.**

**Ours:** `/work/plan` writes a plan and the relationship ends. Nothing ever asks how it
went — the plan is a list, not a loop.

**Implementation here, without violating /work's statelessness:** the *server* prepends
plan context, not transcript context. On `/work/chat`, when the request carries an empty
`history` (a fresh conversation) and the user has an active work-focus plan, inject into
the system prompt: plan title + step titles + done flags — data the user already owns in
`plans`, read fresh per turn, never stored anywhere new. The prompt instructs: open by
asking about one undone step, warmly, before anything else. Client optionally shows a
"Check in on my plan" opener chip. Step completion stays where it already lives
(`PATCH /plans/{id}/steps/{sid}` exists).
**Effort:** small (one query + prompt section + 2 tests: fresh-conversation-with-plan
injects; mid-conversation doesn't). **Risk:** none new — no storage, no schema.
**Decision needed:** none.

## 3. HR/org AI recommendations — **RELEVANT BUT BLOCKED on an owner decision**

**Theirs** (`domains/hr_recommendations`): weekly job aggregates per-org engagement/
stress/trends, `_sanitize_for_llm` drops every node with `users_n < 5` *before the
prompt is built* (test-asserted), LLM returns 1–5 structured recommendations, heuristic
fallback so the dashboard is never empty, acted_on/dismissed with audit.

**The collision:** our `models/organization.py` states — and `test_org.py` *asserts* —
that the org module imports no wellbeing model at all: "the boundary is enforced by the
absence of read paths." Their loop's inputs (stress index, engagement) are precisely the
wellbeing-derived org aggregates our architecture refuses to create. Porting it as-is
would dismantle a deliberate, test-pinned privacy boundary and contradict the portal's
public claim ("totals, never a person" — but also currently: *no wellbeing totals at
all*).

**The legitimate reduced version:** recommendations computed ONLY over what the portal
already may see — eligibility/activation/seat counts per group, with our threshold (20,
stricter than their 5). Output like "Group X activation is 12% vs 64% elsewhere —
consider a comms push." Honest, useful, thin.
**Effort:** medium. **Risk:** scope creep toward wellbeing signals — the sanitizer
pattern (strip-before-prompt, test-asserted) is the part to port regardless.
**Decision needed: yes, owner.** (a) reduced version on permitted counts, (b) widen the
boundary knowingly (a product/legal call, portal copy changes too), or (c) skip.

## 4. Prompt-registry hardening — **PARTIALLY RELEVANT; two pieces worth taking**

**Theirs:** content-hash version doubling as the LLM prompt-cache key; save-blocking
validation (an *enabled* agent with no prompt cannot be saved); reload-failure keeps the
previous prompts and marks the registry degraded; audit with actor + before/after.

**Ours:** `prompt_templates` + versions + admin CRUD + revert already exist (and the
admin audit log now covers prompt saves). No content-hash, no save-blocking validation,
no degraded state — but our registry loads per-call from Postgres, so "reload failure"
has no equivalent failure mode.

**Worth taking:** (1) save-blocking validation in the admin route — an empty-body
activation currently *can* blank a live prompt (the `safety_classifier` acknowledgement
flow guards saving, not emptiness); (2) content-hash surfaced on `GET /admin/prompts` so
an operator can see at a glance whether prod matches the reviewed version.
**Not worth taking:** degraded-reload machinery (wrong shape for per-call Postgres reads).
**Effort:** small. **Decision needed:** none.

## 5. Control-envelope safety nets — **RELEVANT LATER; adopt the checklist, not the code**

**Theirs:** `_truthy` (json-repair can emit `"tru"` — ambiguity must never complete a
stage), prose-around-JSON salvage, empty-reply retry → neutral fallback, completion
floor/ceiling, stuck-stage watchdog. Plus one *measured* lesson (their own eval,
2026-07-18): forcing JSON mode on a routing gate made it silently skip-route three
eligible sessions — **JSON mode biases models toward decisive form-filling**, so
structured output belongs on extraction, never on routing decisions.

**Ours:** `/work` has no stages, no routing, no booleans — `complete_json` already does
fence-tolerant salvage, and the fallback plan is the empty-reply answer. Nothing to
adopt *today* without inventing the problem first.
**Verdict:** defer, but the JSON-mode lesson is already honoured by accident in our
design (extraction-only structured output) and should stay honoured on purpose — noted
in `workcoach.py` when stages arrive. **Decision needed:** none now.

## 6. Role-play / scenario rehearsal — **RELEVANT LATER, after usage proves out**

**Theirs:** dedicated stages with tuned turn budgets (role_play: minimum 4 turns so the
model can't bail after persona setup, ceiling 16 for two rounds + debrief) and a sticky
two-turn offer→route gate. "Difficult conversations" is literally one of `/work`'s five
focus values, so the fit is real. But it presupposes the stage machinery (#5), and
building a staged engine for a feature with zero usage data inverts the order this repo
builds things in.
**Verdict:** park until `/work` has usage; when built, copy their turn-budget numbers and
the offer-gate stickiness — those are the measured parts.

---

## Recommended order

1. **#1 crisis-floor folding + lexicon** — safety, small, no decision needed, fixes two
   demonstrable gaps.
2. **#2 work-plan check-in loop** — completes the corporate feature's core promise.
3. **#4 registry validation + hash** — small hardening alongside.
4. **#3 org recommendations** — after the owner picks (a)/(b)/(c).
5. **#5/#6** — when staged coaching earns its machinery.
