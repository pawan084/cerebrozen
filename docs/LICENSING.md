# Provenance inventory — brief for counsel

> **Status: OPEN. This gates Phase 1 and everything below it in [TODO.md](TODO.md).**
> Purpose: state precisely what this codebase inherited, from where, and with what
> evidence, so counsel can answer the questions in §6 quickly. This document records
> **facts and open questions only** — it draws no legal conclusion, and nothing here
> should be read as one.
> Last updated: 2026-07-16.

## 1. The short version

CereBroZen was built by adopting an existing codebase (`ref/Agent`, internally
"AgentMan", previously "Sherlock") that was **built as a client program for a prior
client**. Two distinct things were inherited: the **engineering** (a deterministic
multi-agent graph and its platform) and the **coaching content + methodology** (a
~500K-character prompt workbook and the taxonomy it encodes).

The prior codebase's own documentation asserts that the methodology is the prior
client's IP. We need to know what the engagement agreement actually says before we
can rely on either half.

## 2. The parties

| | |
|---|---|
| **Us** | CereBroZen (this repo), a B2B workplace-coaching product intended for sale to multiple enterprise tenants. |
| **Prior client** | Sherlock Performance (`sherlockperformance.com`) — per the reference's docs, a live enterprise product with named customers (Citibank, HSBC, Northwestern Mutual, Guardian Life, SCOR, HCL). Described there as "a company with real customers and real counsel." |
| **Third parties** | Kobus Neethling (NBI / "Whole Brain", credited **by name** in the prompts); DISC (behavioural profiling); ICF (the "PCC" credential, asserted as the coach's claimed maturity). Their rights are **separate from** the prior client's and are not covered by any agreement with that client. |

## 3. What was inherited — inventory with evidence

### 3.1 Engineering (adopted into `services/engine`)
- `ref/Agent` → `services/engine`: app, tests, evals, scripts. A single-sweep rename of
  **938 identifiers** (prior brand names → CereBroZen). See
  [TODO.md](TODO.md) Phase 1 and `services/engine/docs/FORK_NOTES.md`.
- Verified 2026-07-16: the crown-jewel modules (`build_graph.py`, `nodes.py`, `state.py`,
  `crisis.py`, `guardrails.py`, `service.py`) are **byte-identical to the reference after
  brand-normalisation** — i.e. adopted, not reimplemented. Our additions on top:
  app-layer tenancy, a wellness surface, Postgres-first storage, regulated-mode default.

### 3.2 Coaching content (the copyright question)
| Artefact | State | Size |
|---|---|---|
| `docs/prompts/` (23 files) | Extracted **verbatim** from `ref/Agent/agent_prompts.xlsx` on 2026-07-14; tracked in git | **476,154 chars** |
| `services/engine/agent_prompts.xlsx` | Forked from the reference workbook; tracked in git; **live product behaviour** | 140 KB (15 agents) |

The reference's own `docs/WHITE_LABEL.md` describes this workbook as "~500K characters
of **their** literal expression."

### 3.3 Methodology encoded in code *and* content
Per the reference's `docs/WHITE_LABEL.md` (its table, verbatim), the following are the
prior client's IP and appear ~200 times across code and workbook:

- **Coachable Index** — 8 dimensions **with a weighted scoring formula** (`ci_*` variables, Catalog, `coaching_intake_agent`)
- **CIM / CBT / CH** three-path model + modules M1–M6 — `coaching_path`, `capability_coaching_node`, the whole graph
- **SSKB / CSKB** two-tier knowledge base — `rag/registry.py`, Extract1–8
- **NBI / Whole Brain** (credited to Kobus Neethling by name) and **DISC** — `user_context_builder_agent`, `role_play_agent`, `CH_coaching_agent`, a dedicated collection
- **PCC** (ICF credential) as the coach's claimed maturity — `environment_system_agent`
- Their **BRD** cited as the spec for the 7-day check-in — `app/checkin_scheduler.py`
  ("the rules … originate in the first client's internal spec; that document is theirs")

**The finding the reference flags as most material:** CIM / CBT / CH are not internal
codenames — they map 1:1 onto the prior client's *publicly marketed* product taxonomy
("Coaching in the Moment" / "Coaching Horizons" / "Coaching for Well-Being"). The routing
decision this entire graph exists to make is their marketed offering.

### 3.4 The reference's own assessment (quoted, `ref/Agent/docs/WHITE_LABEL.md`)
| Layer | Their words |
|---|---|
| **Copyright** | "**Strongest, and you are holding it.** … This is the part that makes the coaching good, and **it cannot ship to a second client under any framing**." |
| **Contract** | "**Decisive, and not visible from here.** Whether the architecture is reusable at all turns on the engagement agreement: work product, derived IP, non-compete. Standard work-for-hire → the code is theirs regardless of what any website claims. **Read it before promising anyone anything.**" |
| **Trademark** | "Weakest. Every mark … is `™`, not `®`… Stops you **naming** your product 'coaching guardrails'… Does **not** stop you *having* guardrails, or building a deterministic multi-agent graph. Techniques are not trademarkable." |
| **Patents** | "the remaining unknown. Nothing on the site asserts one; a search is cheap and worth doing." |

It also states what it considers **safely ours** (generic engineering encoding none of
their method): the deterministic graph machinery, the prompt registry (loader/validator/
content-versioning), the Postgres/pgvector/Ollama offline port, the output-contract
monitors, the stuck-stage watchdog, the crisis screen, the rate limiter, the eval harness.

## 4. Exposure to date

- The repo `pawan084/cerebrozen` was **public** at least until 2026-07-16 (confirmed via
  the unauthenticated GitHub API: `visibility: public`), with `docs/prompts/` (476K chars)
  and the forked workbook tracked. It was set private on discovery.
- **Making it private does not undo publication.** Forks, clones, and third-party caches
  may exist. Counsel should assume the content was publicly available for some period.
- `ref/` itself is **not** tracked (`.gitignore:52`) — the reference codebase was never pushed.
- The prior client's name still appears literally in **8 tracked files** (incl.
  `docs/prompts/orchestrator.md`, `_catalog.md`, `_extraction.md`, `docs/PRODUCT.md`,
  `docs/README.md`). The 938-identifier rename swept the *engine*, not the extracted docs.
- Separately flagged in [TODO.md](TODO.md) and CLAUDE.md: a live `OPENAI_API_KEY` was found
  in `ref/Agent/.env` and is to be treated as **compromised — rotate it**. Not read by us.

## 5. What was decided, and by whom

- **2026-07-14 (owner):** use the reference prompts as the *working base* for CereBroZen
  content — extracted verbatim to `docs/prompts/`, to be adapted per
  [PROMPTS_SPEC.md](PROMPTS_SPEC.md). Recorded there and in TODO.md. This was a **product**
  decision to proceed on the content; **it was not a legal clearance**, and it did not
  answer §6.
- **Still undecided:** everything in §6.

## 6. What we need counsel to answer

1. **The engagement agreement with the prior client** — what does it say about work
   product, derived IP, background IP, and non-compete? Is the *engine code* ours to
   reuse and resell, in whole or in part?
2. **The workbook.** Can any of the ~500K characters of prompt content be used, adapted,
   or must it be replaced outright? Does "adapted, not rewritten from zero" survive, or is
   a derivative work equally infringing?
3. **The taxonomy.** Can we route on CIM/CBT/CH (or an equivalent three-path model) at all,
   given it maps onto the prior client's marketed offering? Is the *idea* separable from
   their *expression* of it?
4. **The named third-party frameworks** — NBI/Neethling, DISC, ICF/PCC. What licence, if
   any, existed via the prior client, and does it travel to us? (Assume it does not.)
5. **Public exposure.** The content was in a public repo for some period. What, if any,
   notification/remediation obligation follows?
6. **Patents.** Worth a cheap search (the reference notes none are asserted publicly).

## 7. What is blocked, and what is not

**Blocked pending §6:** shipping the workbook to any tenant; marketing the three-path
model; the prompt-shrink work (adapting content we may not own); flipping `ref/` to
tracked; any customer commitment or dated promise.

**Not blocked** (generic engineering, per §3.4): the graph machinery, prompt registry,
offline port, contract monitors, watchdog, crisis screen, rate limiter, evals, tenancy,
the platform/admin/web apps, and the design system.

**Cheap and unconditional, do regardless:** rotate the exposed key; keep the `™` phrases
out of all marketing copy; keep `ref/` untracked.
