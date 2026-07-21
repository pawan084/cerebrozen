# CereBroZen Documentation

CereBroZen is an AI coaching platform sold **two ways**: to enterprises (every
employee gets an always-on performance coach; HR/leadership gets aggregated
behavioural analytics) and, since 2026-07-19, **direct to consumers as freemium**
(Free / Plus / enterprise, personal org-of-one on signup). The B2C tier is run as
a B2B2C funnel and evidence engine, not the P&L. The enterprise commercial model
follows sherlockperformance.com (subscription, demo-gated sales, CHRO/L&D buyers);
the engineering is adapted from two reference codebases in `ref/`.

The line that governs legal exposure is **functional, not contractual** — what
matters is that the product stays non-clinical, disclaims therapy, routes crises
deterministically, and hands off to humans. That posture is what makes B2C
defensible, and it is why safety is code (rule 4) rather than editable content.

## The documents

| Doc | Answers |
|---|---|
| [**DEVELOPING.md**](DEVELOPING.md) | **Start here to run it.** Ports, the seeded dev logins, the test gates — and the traps that cost days: `stack:down` wipes the engine's data, `JWT_SECRET` is base64 and a bad one silently disables auth, two unrelated databases, the mongomock-vs-Postgres blind spot that has produced six bugs, and why the eval harness lies without a real model. |
| [PRODUCT.md](PRODUCT.md) | What are we building, for whom, and what does each surface do? |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How do the services and apps fit together? What comes from which reference? |
| [COACHING_FLOW.md](COACHING_FLOW.md) | The agent graph: 18 nodes, 15 agents, routing rules, safety nets, the prompt workbook, and inherited traps. |
| [PROMPTS_SPEC.md](PROMPTS_SPEC.md) | The adaptation brief for the 15 agent prompts — contracts, placeholders, voice, length budgets, eval requirements. |
| [prompts/](prompts/README.md) | All 18 reference prompts extracted verbatim (~420K chars) + Catalog/extraction/variables tabs — the working content base. |
| [DESIGN.md](DESIGN.md) | Brand palette, typography, tokens, accessibility gates, per-surface design rules. |
| [ENGINEERING.md](ENGINEERING.md) | Standards: testing gates, CI, conventions, cross-stack contracts, repo layout. |
| [SECURITY.md](SECURITY.md) | Threat posture, tenancy, privacy modes, crisis safety, compliance mapping. |
| [LICENSING.md](LICENSING.md) | **What this codebase inherited and from whom** — the provenance inventory + the open questions for counsel. **Gates Phase 1 and below.** Read before promising anyone anything. |
| [REF_PARITY.md](REF_PARITY.md) | Feature-by-feature vs the `ref/Zen` clients: what to **take**, what is **correctly absent** (B2C→B2B), what we are **ahead** on, and the **traps** that would break rule 5 if ported. |
| [TODO.md](TODO.md) | The phase-by-phase build plan **and dated build log** — append-only, so read it chronologically; a 2026-07-14 entry can be superseded by a later one. |
| [IMPROVEMENT_BACKLOG.md](IMPROVEMENT_BACKLOG.md) | **The live tracker** — 240 numbered, autonomously-implementable items with a progress line. This is "what's being worked on now"; TODO.md is "the plan". |
| [CLAIMS_MAP.md](CLAIMS_MAP.md) | Rule 6 made checkable: every marketing claim → the mechanism that backs it → the test that proves it. Enforced in CI by `scripts/check-claims.mjs`. |
| [DATA_SAFETY.md](DATA_SAFETY.md) | Pre-filled answers for the Play Store Data Safety form and Apple's privacy questionnaire. |
| [SELF_HOSTING.md](SELF_HOSTING.md) | Running the whole thing on your own infrastructure — the sovereignty claim's actual instructions. |
| [ANDROID_QA.md](ANDROID_QA.md) | The line between what CI proves and what only a physical device can: coverage, the pending device checklist, hardware-verified log, and the deferred nav refactor (#174). |
| [SPLASH_SPEC.md](SPLASH_SPEC.md) · [HOME_SPEC.md](HOME_SPEC.md) | The two Android craft specs — each a numbered gap analysis with a shipped-status banner on top. Splash is device-verified; Home is not yet. |
| [legal/CONSUMER_TERMS_DRAFT.md](legal/CONSUMER_TERMS_DRAFT.md) | Consumer ToS draft. **Needs counsel review — not cleared.** |

Each backend service carries its own doc set for things that shouldn't leak into
the shared plan: `services/engine/docs/` (12 docs — `AGENT_FLOW`, `EVALS`,
`OPERATIONS`, `AIR_GAPPED`, `MODEL_CARD`, `FORK_NOTES`, …). A `docs/X.md`
reference inside an engine doc means *that* tree, not this one.

## The reference codebases (read-only)

| Path | Codename | What we take from it |
|---|---|---|
| `ref/Agent` | AgentMan | The coaching engine: LangGraph graph, prompt workbook, safety screen, commit gate, regulated mode, evals harness. |
| `ref/Zen` | CereBro | Android app (Compose, transport, offline, auth), admin dashboard, platform backend patterns (Postgres, roles, deletion ledger), design-token sync, e2e + Caddy deploy. |
| `apps/web` | — | Already built: the marketing site (Next.js 16, coral palette). |

Rules for `ref/`: never edit it, never run its `.env` files, never reuse its
secrets (at least one live key was found committed there — see TODO P0), and
never ship its media assets.

## Status

**2026-07-21.** All four surfaces and both services are built and gate-green;
the work now is backlog burn-down, not scaffolding.

| Surface | State |
|---|---|
| `services/engine` | Coaching graph, safety pipeline, evals + 22-case crisis red-team gate |
| `services/platform` | Auth/orgs/entitlements/billing (mock · Stripe · Play behind one seam) |
| `apps/web` | Marketing site + pricing, sovereignty, coaching-not-therapy, accessibility pages |
| `apps/admin` | HR portal + ops admin (personal B2C orgs excluded from the tenant list) |
| `apps/android` | 5 tabs, B2C purchase loop device-verified; Home/"Today" rebuild awaiting a device |

Live test/coverage numbers are kept in **one** place — the progress line of
[IMPROVEMENT_BACKLOG.md](IMPROVEMENT_BACKLOG.md) — deliberately not duplicated
here, because a second copy is a second thing to go stale.

**What needs a human, not code:** real Stripe + Google Play merchant keys; the
`/auth/google` backend; counsel review of the consumer ToS; app-store submission;
the final pricing call. These are listed unnumbered at the end of the backlog.
